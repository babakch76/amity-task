# TaskFlow: what the app does

A reference for the app **as built and deployed**, not as planned. Written
2026-09-04 against commit `496005a`, revised 2026-09-13 for the bottom bar and
the Settings tab, with every claim checked against the code or driven on a
device rather than taken from an earlier document.

Two companion documents, deliberately separate:

- [`taskflow_feature_spec_2.md`](taskflow_feature_spec_2.md) is the *spec*: what
  was asked for. Where this document and the spec disagree, the app follows this
  one and [`BACKLOG.md`](BACKLOG.md) records why.
- [`V2-HANDOFF.md`](V2-HANDOFF.md) is the *working state*: what is deployed,
  what is half-done, what will cost the next person hours.

---

## 1. What it is

A chore board for a household that shares one. Three or four people, a handful
of recurring chores, and the question the app exists to answer: **whose turn is
it, and has it been done?**

It is not a task manager with a household theme. The difference shows in what it
refuses to do, which is set out in section 8.

---

## 2. The board

The app's home screen after signing in. If you belong to exactly one household
it opens straight onto that household's board; the group list is one Back away.

Rows are grouped by **whose it is**, not by status:

| Section | What is in it |
|---|---|
| **YOURS** | Open rows assigned to you |
| **OTHERS** | Open rows assigned to anybody else |
| **RECENTLY DONE** | Completions from the last seven days, newest first, with who did it (and who else worked on it) and when. Older ones are in each chore's History and in Activity. |

Each row is three lines: **what it is**, **how it recurs and when it is wanted**,
and **whose turn it is**. For example:

```
Clean the bathroom
weekly
Due Thu 10 Sep
maya covered your last turn, so it's yours again
```

That fourth line appears only when there is something to explain. See section 4.

**Overdue is amber, never red**, and never a day count, a badge or a percentage.
The amber follows whichever line carries the date, and is picked from the theme
so it stays legible in light and dark.

**Anyone can tick anything.** "I just did it myself" is the common reality, so
the board records who actually did it rather than fighting over who was meant
to.

**The row has no checkbox.** The whole cell is a button: tap it anywhere and
the chore opens. A completed row is marked, not offered — a green tick where the
box used to be, and the title dimmed. Nothing on the board decides anything.

**Done is said in the chore's own sheet**, under the household's **done-line**.
The sheet reads: the chore and when it is wanted, a violet callout when there is
something to explain (a cover, a pass, an everybody-busy return), then whose
turn, how often, due, needed by, and *History* / *Edit chore*. Below a rule,
**WHAT DONE MEANS** — *"Sink, loo and shower screen, and the bin out"* — sits
directly on top of the action: **I did this** (*Mark as done* on somebody else's
turn — anyone may tick anything), with **Busy — pass it on** under it on your
own turn.

Putting the two together is the point. The done-line is the household's single
setup conversation about standards (F4); it was readable only by opening a sheet
nobody opened, while the decision it governs was one accidental tap away in a
scrolling list. Now the standard and the button that applies it are the same
screen, and reaching the button takes a deliberate tap.

**Undo is in the sheet too** — *Undo — I didn't do this* — still only for the
person who marked it and still only for **ten minutes**. After that the sheet
shows who did it and when, and nothing more.

**Swipe right is the one shortcut**, and the only thing that still asks: it
completes a turn without opening it, so it puts up a short sheet quoting the
done-line first. Swipe left still passes.

**After a completion the board says who is next** — *"Trash done. bb is next."*,
or *"You're next."* — with **Undo** on the same snackbar. The name is read off
the turn the server just created, not predicted on the phone, so it is always
the one the turn rule actually chose. A one-off just says it is done.

**The sheet shows the rotation** as the chore defines it — *Takes turns: dd →
mm → You → bb*. It is the order, not a forecast: who is next after the current
turn depends on covers and passes the app deliberately does not show anyone.

**Tabs are Board, Activity and Members.** There is no calendar; see
[`calendar_decision_record.md`](calendar_decision_record.md) for why it was
removed rather than fixed.

---

## 3. Chores, and the four ways they come round

A chore is added in **two questions**. The first is *how often*, and the answer
decides what the second screen even asks:

| Answer | What it means | What the second screen asks |
|---|---|---|
| **Every so many days** | Counted from the last time it was done | The interval, and the order of turns |
| **A fixed day of the week or month** | A calendar commitment: "Thursdays" | Which day, and the order of turns |
| **As needed** | No date at all; you can see it needs doing | Just the order of turns |
| **A one-time thing** | Given to one person once | Who, and an optional date |

Each option has a circled **?** that explains it on tap, so the choice itself
stays short.

Every chore may carry a **done-line**: one sentence agreed once, saying what
counts as finished, so it is not argued every time. *"Sink, loo and shower
screen, and the bin out."*

**Editing is the same two screens, prefilled.** Changing a schedule re-dates the
open turn rather than stranding it on a date the new schedule would never
produce. The one change a chore cannot make is becoming a one-time thing, and
the screen says so instead of silently disabling the option.

**Deleting** lives at the end of editing, not on the row, and its confirmation
names what goes with it: the chore, whoever's turn it currently is, and the
history of every time it was done.

### Dates

- **Interval**: the next turn is due N days after the **last completion**, so
  doing it late moves the next turn with it. Doing it *early* also pulls the
  schedule earlier; that is deliberate and BACKLOG's B-14 records the three
  reasons, including the cost.
- **Fixed date**: the pattern holds. A chore done early still comes round on its
  own day, and a missed day rolls forward with the same person still holding it.
- A chore may also have a **needed-by time** ("before 7am").

---

## 4. Whose turn: the turn rule

One rule covers three things the app treats as identical: a housemate quietly
doing someone else's chore, a **busy pass**, and the overflowing bin somebody
else finally emptied. All three are a **cover**.

> A chore done by anybody other than the person whose turn it was counts as
> **the doer's** turn. The next one goes back to the person who owed it, the
> rotation carries on after them, and **the doer's own next slot is skipped
> once**.

So a cover is not a favour that vanishes and not a debt that compounds. It is a
swap: two people exchange places for one cycle, and everybody still does one
turn a round. In *dd → mm → aa → bb*, with mm covering aa: aa repays, then bb,
then dd, then mm's slot is skipped, and the plain order resumes.

(Until 2026-11-01 the order resumed *after the doer*. That is the same thing
when the doer happens to come next, and unfair otherwise — someone went twice
and someone else lost a turn. `BACKLOG.md` B-19 has the cases.)

**A pass goes to the next person not already owed a skip**, so a serial
passer's skips spread round the household instead of landing on the same
neighbour. The confirm names them before you send it.

**Every passer owes one.** If A passes to B and B passes on to C, both A and B
owe a turn, repaid oldest first. A person is only ever owed **one** turn per
chore, however many times they passed it.

**Away holds a debt, it does not cancel or park it.** Someone away is out of
every rotation, so the turn passes to the next available person and the debt
waits for their return.

**When everyone is busy**, the chore has nowhere to go. It returns to whoever
asked first, dated no later than the day it would next have come round on, and
that person picks the day. If nobody picks, that date stands and the chore
stands with it. Asking again is refused with what to do instead, rather than
lapping the household forever.

**A debt belongs to a person, not a position.** Reordering a rotation does not
move it; leaving the rotation voids it, and nothing is said about the turn that
was not taken.

---

## 5. The two ways out of a turn, besides doing it

Neither cancels what you owe. These are the **only** two.

### Busy pass

Swipe your own row, or use the button in the row's detail. It goes to the next
person in the rotation, and the confirmation says who will be told and that
**nobody else is**.

**A pass is private.** It writes no entry in the household's activity feed. That
is enforced in the data, not just in the wording: the pass and its undo both
leave the feed untouched, and the list of who has passed what is never sent to
anybody's phone.

The snackbar offers **Undo** for a mis-swipe. The server allows it for two
minutes, only to the person whose pass it was, and only while the chore is still
open. It also puts back a date the pass had moved, so passing an overdue chore
and taking it straight back cannot launder its deadline.

There is no cap on passing, no cooldown, no tally, and no notification to
anybody about somebody else's passing. That is a decision, not an omission; the
reasoning is in BACKLOG's Part 0 notes.

### Away

Declare yourself away, open-ended or until a date. You come out of every
rotation here and back in at the same place, and no turns are owed for the time
you were gone. **The household can see you are away** — unlike a pass, which is
the point of having both.

You say it under **Your availability** at the foot of the Members tab — *At
home* / *I'm away*, or *Away until Fri 6 Nov* / *I'm back*. Everyone else sees
an **Away** badge on your row, with *Back Fri 6 Nov* under your name. Your own
board shows a quiet banner while you are away, with a way back.

---

## 6. Reminders

Scheduled **on the device**, not on the server, and covering every household you
belong to rather than only the one you have open.

**Quiet hours** are a property of the person, not the group: one window on your
account, applying to reminders from all of your households, and set from
**Settings**. Anything due inside the window waits until it is over rather
than being dropped.

---

## 7. The record

**Activity** is the household's shared feed, in words: *"maya did a chore"*,
*"demo added a task"*, *"Moved it to Done"*. Every group-visible change appears;
a pass does not, by design.

**One chore's history** shows every time it has been done, with the date, the
date it was due, and who did it for whom. Away periods appear as *away*.

**"I worked on this too."** On a turn somebody else has marked done, the sheet
offers anyone else a button to add their name beside the doer's, for two days
after it was done. The row then reads *"Done by maya, also sara"*, and so do
the sheet and the chore's history; the feed says *"sara also worked on a
chore"*. It adds a name and nothing else — no note, no reason — and the turn
still counts as the doer's: rotation, debts and the per-person count below are
untouched. Your own name can be taken off again within ten minutes, and undoing
the completion takes every "also" with it.

**What's been done** is the per-person record, over this week, this month, or
three months. It **counts, and does not rank**: no percentages, no averages, no
streaks, no ordering by who did most. Zeroes are shown as zeroes, days away are
noted, and the line under it says what the number means: *"Counted by who
actually did it, so covering for someone counts for you."*

---

## 8. What it deliberately does not do

Each of these was considered and refused. A reader who finds one missing should
know it was a decision.

- **No scores, percentages, points, streaks, leaderboards or rankings.** Not on
  the board, not in history, not anywhere.
- **No red, and no shaming of lateness.** Overdue is amber and stated once.
- **No counting of anybody's skips.** No "3×", no tally, no sort by it.
- **No escalating repayment.** Three skips owe three turns, one at a time, in the
  ordinary way.
- **No penalty, cap or cooldown on the busy pass**, and no telling the household
  about somebody else's.
- **No roles or permissions beyond ownership.** Chore editing is open to every
  member, so the manager role and its deadline gate were removed rather than
  kept.
- **No calendar view.** Under completion-anchored rotation a chore has one live
  occurrence, so a month grid has almost nothing to draw.
- **No multi-select or bulk actions.**

---

## 9. Joining, leaving, and a new household

A household is created by name, and joined either by **direct invite** to a
username or by a **shared code** that expires. Both are buttons under the list
on the **Members** tab — *Share an invite code* and *Invite by username* — and
the rows above them are just names, with an **Owner** or **Away** badge where
one applies. Invites appear on the group list
with a badge.

**A new household is offered five starter chores** rather than an empty board:
*Trash* (as needed), *Bathroom* (every 4 days), *Kitchen floor* (weekly),
*Recycling* (Tuesdays) and *Hallway vacuum* (fortnightly). Each shows its
schedule underneath, so "as needed" and "Tuesdays" are met as facts about real
chores before the create flow ever asks about them. Each can be renamed, because
*Trash* and *Bins* are the same chore in different houses, and any can be
removed. "Something else" adds a blank row, and Skip leaves the board empty
with a line saying what to do next.

Leaving is available to any member; the owner cannot leave while others remain,
and the last member leaving deletes the household.

---

## 10. Appearance

**System, Light or Dark**, chosen from **Settings** and stored **on the
device**: it is a property of this phone in this person's hand, not of the
account or the household, so it is not synced and not visible to anybody else.

---

## 11. Getting around

Three places, on a bottom bar that is always there once you are signed in.

**My groups** — a household's board. Not a list of them: the app opens on the
household you last had open, and its name at the top of the board is a chip
with a chevron. Tap it and your households drop down, with a tick beside the
one you are in, and underneath them the two ways to end up with another —
*Create a household* and *Join with a code*. Somebody with no household at all
is offered both on the screen itself rather than being sent elsewhere to find
them.

Which household you were last in is remembered on the phone, not on the
account: it says nothing about how the same account should open on anybody
else's device.

**My chores / My week** — your own turns, from **every** household at once.
A button in the bar switches between two ways of reading them, and the title
says which you are in:

- **My chores** groups them under the household they belong to.
- **My week** puts the week on top — seven days, today ringed, a dot under any
  day something of yours falls on — and the list underneath answers that week,
  day by day. Move the week with the arrows either side or by swiping it; a
  **This week** button brings you back. Tap a day and the list narrows to that
  day; tap it again and you get the week back. Anything whose day has passed
  sits under its own day with the date in amber, never under a heading that
  names you as late. As-needed chores, which have no date to sit on, get their
  own section on the current week.

  A week or a day with nothing on it says so, with a straight face.

Tick a turn and it is done; the ones you have already done this cycle stay at
the bottom so a mis-tap can still be undone. Tapping one opens the same detail
it opens on the board — what counts as done, how often it comes round, its
history, and *Busy? Pass it on*.

It shows only your own turns. Never another member's name, date or workload,
and there is no shared or comparison mode. The week never says how many turns a
day holds and never colours a day by how full it is: a day with four looks
exactly like a day with one.

**Settings** — you. The name your housemates see and the address you sign in
with, both changeable; invitations waiting for you, and joining with a code;
quiet hours, appearance, and signing out. A violet dot on the tab means an
invitation is waiting; it is the only badge in the bar, and there are no counts
in it at all.

---

## 12. Shape of the system

| Piece | What it is |
|---|---|
| **App** | Android, Kotlin, Jetpack Compose with Material 3. `minSdk` 26. |
| **Backend** | Go, one binary, SQLite in WAL mode. Migrations run on every start. |
| **Hosting** | One EC2 instance. Caddy terminates TLS on 443 and proxies to the app on `127.0.0.1:8080`, so the API is reachable only over HTTPS. |
| **Auth** | JWT bearer tokens; the middleware wraps every route, so even an unknown path answers `401`. |

Two data shapes sit side by side on the board and this is intentional:
**occurrences** are the turns of a recurring chore, and **tasks** are one-off
items, which predate the chore model and are the spec's one-off type. The board
shows both; the rotation rules apply only to the first.

The endpoint list lives in
[`task-manager-backend-GO/README.md`](task-manager-backend-GO/README.md), and the
screen-by-screen map in
[`TaskFlowApp-kotlin/README.md`](TaskFlowApp-kotlin/README.md).

### One thing in this repository is not the app

`index.html`, `app.js` and `index.css` at the root are a **pre-pivot web
prototype**, and they describe a different product: "Joint Task Management",
projects called *Website Redesign* and *API Integration*, a deadline calendar.
Nothing in the Android app or the Go backend uses them, and no build refers to
them. They are kept only because deleting somebody's earlier work is their call,
not the toolchain's. **Do not read them as documentation of TaskFlow.**
