#!/usr/bin/env python3
"""
Seed the demo household the screenshot deck is shot against.

    Flat 3B — demo (the phone), maya, sam

Run it against a server on an EMPTY database, never against one you use:

    rm -f /tmp/demo.db*
    DB_PATH=/tmp/demo.db PORT=8080 go run ./cmd/server &
    python3 scripts/seed_demo.py /tmp/demo.db          # --base defaults to :8080

Then sign in on the device as demo@flat3b.test / flat3b-demo.

Everything goes through the API, so the turn rule decides every assignee
exactly as it would for real people. Only *times* are rewritten afterwards,
straight in SQLite: the API stamps completions "now", and a month of chores all
done in the same second reads as a bug in a screenshot. The activity feed is
moved to match, so History and Activity agree about when things happened (the
2026-09-03 deck learned that one the hard way).

Standard library only.
"""

import argparse
import datetime as dt
import json
import sqlite3
import sys
import urllib.request

PASSWORD = "flat3b-demo"
STAMP = "%Y-%m-%d %H:%M:%f"


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("db", help="path of the server's (empty) database")
    ap.add_argument("--base", default="http://127.0.0.1:8080")
    args = ap.parse_args()

    db = sqlite3.connect(args.db)
    if db.execute("SELECT COUNT(*) FROM users").fetchone()[0]:
        sys.exit(f"refusing: {args.db} already has users — this seeds an empty database only")

    def call(method, path, token=None, body=None):
        req = urllib.request.Request(
            args.base + path,
            method=method,
            data=json.dumps(body).encode() if body is not None else None,
            headers={"Content-Type": "application/json"},
        )
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        with urllib.request.urlopen(req) as res:
            raw = res.read()
            return json.loads(raw) if raw else None

    def sql(query, *params):
        db.execute(query, params)
        db.commit()

    def user_id(name):
        return db.execute("SELECT id FROM users WHERE username = ?", (name,)).fetchone()[0]

    tokens = {}
    for name in ("demo", "maya", "sam"):
        tokens[name] = call("POST", "/auth/register", body={
            "username": name, "email": f"{name}@flat3b.test", "password": PASSWORD,
        })["token"]
    demo, maya, sam = tokens["demo"], tokens["maya"], tokens["sam"]
    D, M, S = user_id("demo"), user_id("maya"), user_id("sam")

    g = call("POST", "/groups", demo, {
        "name": "Flat 3B", "description": "Top floor, the one with the boiler",
    })["id"]
    invite = call("POST", f"/groups/{g}/invite-code", demo, {})
    code = invite.get("invite_code") or invite.get("code")
    for t in (maya, sam):
        call("POST", "/invites/redeem", t, {"code": code})

    def chore(**body):
        return call("POST", f"/groups/{g}/chores", demo, body)["id"]

    def open_of(chore_id):
        return db.execute(
            "SELECT id FROM occurrences WHERE chore_id = ? AND status = 'open'", (chore_id,),
        ).fetchone()[0]

    def done(token, chore_id, days_ago, hour):
        """Complete the chore's open turn as `token`, then move it into the past."""
        occ = open_of(chore_id)
        call("PATCH", f"/groups/{g}/occurrences/{occ}", token, {"status": "done"})
        sql(
            "UPDATE occurrences SET done_at = strftime(?, 'now', ?, 'start of day', ?) WHERE id = ?",
            STAMP, f"-{days_ago} days", f"+{hour} hours", occ,
        )
        return occ

    def utc_iso(days, hour):
        d = dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=days)
        return d.replace(hour=hour, minute=0, second=0, microsecond=0).isoformat()

    # Clean the bathroom — weekly. A full round, then a cover: demo, maya and
    # sam each do their own, then maya does demo's, so it comes back to demo
    # with "maya covered your last turn, so it's yours again".
    bath = chore(name="Clean the bathroom", schedule_type="interval", interval_days=7,
                 done_line="Sink, loo and shower screen wiped, and the bin emptied",
                 rotation=[D, M, S])
    done(demo, bath, 22, 10)
    done(maya, bath, 15, 18)
    done(sam, bath, 8, 11)
    done(maya, bath, 2, 19)

    # Put the bins out — Thursdays, before 7.
    bins = chore(name="Put the bins out", schedule_type="fixed_date", fixed_weekdays=[4],
                 needed_by_time="07:00", done_line="Both bins at the kerb, lids shut",
                 rotation=[S, M, D])
    # Done on the most recent Thursday before today, whatever today is.
    last_thursday = (dt.date.today().weekday() - 3) % 7 or 7
    bins_done = done(sam, bins, last_thursday, 6)

    # Water the plants — every 3 days. maya did it yesterday: a recent
    # completion by somebody else, which is what "I worked on this too" is for.
    plants = chore(name="Water the plants", schedule_type="interval", interval_days=3,
                   done_line="Every pot, the bedroom ones too", rotation=[M, D, S])
    done(maya, plants, 1, 9)

    # Kitchen floor — weekly. sam did it; maya says she worked on it too.
    floor = chore(name="Kitchen floor", schedule_type="interval", interval_days=7,
                  done_line="Swept and mopped, chairs back down", rotation=[S, D, M])
    floor_done = done(sam, floor, 1, 20)
    call("POST", f"/groups/{g}/occurrences/{floor_done}/also", maya, {})
    sql("UPDATE occurrence_also_by SET added_at = strftime(?, 'now', '-1 days', 'start of day', '+21 hours')"
        " WHERE occurrence_id = ?", STAMP, floor_done)

    chore(name="Take the recycling out", schedule_type="as_needed",
          done_line="Glass and paper both, boxes flattened", rotation=[M, S, D])

    # Two one-offs: one of demo's already past its day (amber, never red).
    chore(name="Call the landlord about the boiler", schedule_type="one_off",
          due_date=utc_iso(-2, 17), done_line="A date for the engineer, written on the fridge",
          rotation=[D])
    chore(name="Buy a bulb for the hall", schedule_type="one_off",
          due_date=utc_iso(4, 17), done_line="", rotation=[M])

    # Due dates are set to 20:00 UTC — the evening of that day in Europe. 23:59
    # UTC was tempting and wrong: it is already tomorrow in Rome, and every
    # date on the board came out a day late.
    #
    # Due dates were computed from the real "now" of each completion, so they
    # all land on the same day next week — and history would read "Done 10 Oct,
    # was due 8 Nov". Each completed turn is made due on the day it was done,
    # and each interval turn it spawned is re-dated from that.
    sql("UPDATE occurrences SET due_date = datetime(done_at, 'start of day', '+20 hours')"
        " WHERE status = 'done' AND due_date IS NOT NULL")
    sql("""UPDATE occurrences SET due_date = (
             SELECT datetime(p.done_at, 'start of day', '+' || c.interval_days || ' days',
                             '+20 hours')
             FROM occurrences p JOIN chores c ON c.id = p.chore_id
             WHERE p.id = occurrences.spawned_from AND c.schedule_type = 'interval')
           WHERE status = 'open' AND spawned_from IS NOT NULL
             AND chore_id IN (SELECT id FROM chores WHERE schedule_type = 'interval')""")

    # A fixed-date chore done on its day: the completed turn was due that
    # morning, and the next is the following Thursday — the API counted from
    # the real now and skipped one.
    sql("UPDATE occurrences SET due_date = datetime(done_at, 'start of day', '+7 hours') WHERE id = ?",
        bins_done)
    sql("""UPDATE occurrences SET due_date = datetime(
             (SELECT done_at FROM occurrences WHERE id = ?), '+1 day', 'start of day', 'weekday 4', '+7 hours')
           WHERE spawned_from = ?""", bins_done, bins_done)

    # The feed follows the completions it describes; setup happened a month ago.
    sql("""UPDATE activity_events SET created_at = (
             SELECT strftime(?, o.done_at) FROM occurrences o WHERE o.id = activity_events.task_id)
           WHERE event_type = 'occurrence_done'""", STAMP)
    sql("""UPDATE activity_events SET created_at = (
             SELECT strftime(?, a.added_at) FROM occurrence_also_by a
             WHERE a.occurrence_id = activity_events.task_id)
           WHERE event_type = 'occurrence_also'""", STAMP)
    sql("""UPDATE activity_events SET created_at = strftime(?, 'now', '-30 days', 'start of day', '+20 hours')
           WHERE event_type IN ('chore_created', 'member_joined')""", STAMP)
    sql("UPDATE groups SET created_at = datetime('now', '-30 days')")
    sql("UPDATE group_members SET joined_at = datetime('now', '-30 days')")

    # sam is away until the weekend. Last, so sam still has a place in the
    # turns above: being away lifts you out of rotations from now on.
    call("PUT", f"/groups/{g}/members/me/away", sam, {"away": True, "until": utc_iso(5, 12)})

    print(f"Seeded Flat 3B ({g}). Sign in as demo@flat3b.test / {PASSWORD}")


if __name__ == "__main__":
    main()
