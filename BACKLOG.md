# TaskFlow — backlog

Running list of known bugs and missing features across both projects. Append as
things turn up; keep the newest reports at the top of each section.

Status key: **OPEN** · **IN PROGRESS** · **DONE**

> **The project pivoted on 2026-08-21** to the household chore-rotation app in
> `taskflow_feature_spec_2.md`. Read **[V2-HANDOFF.md](V2-HANDOFF.md)** first —
> several items below were resolved by that spec rather than by being fixed,
> and some (bulk status, three-state status) are now non-goals.

## v2 progress

- **Household removal, Book it up and How TaskFlow works — DONE on
  `household-removal-and-tutorial` (2026-11-02), not merged or deployed.** The
  interviews run on `main` unchanged. The project owner merges this afterwards,
  and it needs the backend deployed with it: the current server answers the new
  routes with 404 or 405, not a sign-out.
  - **The month on Mine**, at the project owner's request after users complained
    they could only see a week. A small optional view, not a calendar coming
    back: the Calendar tab decision stands. It gives people some perspective on
    whether they are likely to have chores around exams or other commitments, so
    they can arrange something with housemates. Nothing can be passed from it:
    a later turn does not exist yet, so a pass or a cover happens when it arrives.
    - **A popup, not a section that expands in place**, which would push Up next
      off the screen. Opened from the whole Next 7 days card.
    - **Your own turns only.** All the household's chores would be a dot on most
      days and say nothing about whether you are free. It also keeps this within
      what the strip was allowed: nothing of anybody else's is drawn on it.
    - **Rings for likely turns.** The 2026-11-01 refusal of a forecast on the
      board stands: nothing on the board says who is next. This is a different
      case. It would still sometimes be wrong, but it names nobody else and says
      on its face that it is a guess, and without it the view would be empty
      past the next turn. It stops at six months.
    - **Driving it found two bugs the tests had not.** Comparing with the turn the
      server actually spawned showed the first version ignoring people who are
      away, so it now fetches members with the reminder data. And shorter months
      made the popup shorter, moving the arrows out from under a repeated tap, so
      every month is six rows.
    - **Known limits:** covers, passes and debts cannot be allowed for, since the
      phone is not sent them. Everything assumes each turn is done on its day.
      Away is taken as it stands today, so an absence not yet declared cannot show.
  - **Book it up narrows constraint 5** (no free/busy scheduling). The project
    owner decided this on 2026-11-02 after participants complained of running
    into each other over the same appliance. Kept deliberately small, and to
    stay within the other constraints it is a notice, not a lock: no refusal, no
    approval, no reminders, no availability view. Group-visible, so it is in the
    feed (constraint 7). Note that this was added after the interview version
    was fixed, so no interview tested it.
  - **Removal:** owner only, the one thing in the API gated on ownership, because
    it takes everybody's record with it. Housemates are told by their own phones
    (`KnownHouseholds`), since the feed goes too.
  - **A reminder bug found on the way and fixed:** a household you left kept its
    alarms, because rescheduling only touches households still on the list.
  - **Removed:** *Leave* for the owner in People, which could only fail while
    anybody else was in the household. *Remove* replaces it. The last member
    leaving still deletes the household through the API.
  - **Tried and dropped:** Material's `TimeInput` for booking times. Tapping the
    hour box puts the cursor after the two digits and the next digit is silently
    refused, so the keyboard appears broken. The clock face has no such trap.
  - **Known limits, left alone to keep this small:** a clash is matched by name,
    so "the washer" does not clash with "Washing machine". Finished bookings stay
    in the table, hidden from the list. A busy house's bookings push the chores
    down the board, as there is no cap on the section. No screenshots or deck
    slides show any of this yet.

- **Quiet House redesign — DONE on `mehrdad-design` (2026-11-01).** Built from
  the design canvas (claude.ai/artifact/5i9xzyDwwvD1LZQkGVgLPf), keeping the
  brand violet as the one accent. Every hard constraint still holds; the
  changes are of shape, not of rule:
  - **A floating dock: Mine · House · Feed, and Add.** Replaces the bottom bar
    (My groups · My chores · Settings). Settings became **You**, behind your
    own face; Members became **People**, behind the faces on House; Activity
    became **Feed**, with *What's been done* as its second view instead of a
    menu item. The overflow menu is gone.
  - **Mine replaces My chores and My week**, ending the interviews' A/B. It
    keeps a glance at the week (a rolling 7-day strip, dots only) and puts one
    turn forward as **Up next**, with the done-line and *I did this* on the
    card. The week strip, view toggle, `weekAgenda` and `WeekAgendaTest` are
    deleted rather than left unused.
  - **Rows are grouped cards.** Your turns carry a dot (amber when the day
    has passed — never red, never a count), others' carry the holder's face,
    done rows a sage tick.
  - **The chore sheet** draws the rotation as faces with the holder ringed
    (an order, not a forecast), the done-line as a quote on the button, and a
    sage card for a finished turn.
  - **Add a chore is one screen.** *How often* is still asked first and
    everything it implies appears beneath it — the two-step idea without the
    step.
  - **Theme:** stone ground, ink, violet, sage, amber, slate; Bricolage
    Grotesque + Instrument Sans bundled under OFL; every Material role set.
  - Not built from the canvas: the Up next card's pass button opens the
    chore's sheet rather than passing directly, so the receiver is named
    before anything is sent.

- **The screens deck re-shot — DONE (2026-11-01).** Every screen had changed
  since 2026-09-03: no checkbox on the board, no group list, new sign-in, a
  bottom bar. 36 screens now (was 31), eleven slides (was nine) — new ones for
  *Finishing a turn* (swipe confirm, "… is next · Undo", "I worked on this
  too") and *Your turns, everywhere* (My chores, My week). Files are named in
  deck order.

  The seed is **committed** this time: `task-manager-backend-GO/scripts/
  seed_demo.py` builds *Flat 3B* (demo, maya, sam) through the API, so the
  turn rule assigns every turn, then backdates completions, due dates and the
  feed to match. Two lessons kept in its comments: due times at 23:59 UTC are
  already tomorrow in Rome, and a fixed-date chore's next slot has to be
  re-dated from the backdated completion.

  Shooting notes for next time: run the seeded server on :8080 so the build's
  baked-in address works, and stop the dev server with `lsof -ti tcp:8080
  -sTCP:LISTEN` — without `-sTCP:LISTEN` it also kills the emulator, which
  holds a connection to that port. After a cold boot the emulator's demo-mode
  signal icon draws empty; the icons on those frames were repainted from a
  pre-reboot frame in each frame's own colours.

- **Members tab from the redesign concept's screen 05 — DONE (2026-11-01).**
  Rows are a name and, only where it applies, an *Owner* or *Away* badge
  (*Back Fri 6 Nov* under the name). Gone: the **email address** as each
  row's second line — housemates know each other by name, and an address is
  the one thing on that screen nobody needs — and the grey **Member** badge on
  every other row, a label that said nothing because it was on everybody.
  **Inviting and away moved out of the app bar's ⋮ menu** into the tab, under
  the list they change: *Share an invite code*, *Invite by username*, and
  *Your availability* (*At home* / *I'm away*). The menu keeps *What's been
  done* and *Leave household*. The Away badge is tinted from `secondary`,
  never amber — amber means a date has passed, and being away is not
  lateness. Not taken from the concept: a **share-invite link**, which needs
  a deep-link route the app does not have; the code dialog already offers
  Share.

- **Board extras from the redesign concept's screen 04 — DONE (2026-11-01).**
  Taken: a *"Trash done. bb is next."* snackbar with **Undo** after a
  completion, the rotation order in the sheet, and *RECENTLY DONE* (seven
  days, newest first) in place of *DONE THIS CYCLE*, which had been every
  completion ever made, oldest first. Changed from the concept, each on
  purpose:
  - **No "Next turn: Sara" on the row, no *You → Sara → Marco* forecast.**
    Who comes after the current turn depends on covers, passes and debts, and
    the client is deliberately not sent those (constraint 7). A forecast the
    phone worked out alone would sometimes be wrong. The snackbar names the
    next person from the turn the server actually spawned (`spawned_from`);
    the sheet shows the rotation as an *order*, labelled *Takes turns*.
  - **No *Mark done* button on the row.** Rows open the sheet; done is said
    under the done-line (2026-09-15).
  - **No "2 active · 1 completed" counter** — a count of the household's
    output on the screen everybody opens is a scoreboard in all but name
    (constraint 4).
  - **No "Live · Updated just now" badge.** The board already polls and has
    a refresh button; a liveness label is chrome.

- **"I worked on this too" — DONE (2026-11-01).** From the interviews: five of
  fifteen described redoing part of a chore somebody else had marked done —
  "if it's something small, I usually redo it" — to avoid an argument, and the
  extra work leaving no trace. A completed turn now takes a second (third…)
  name: table `occurrence_also_by`, `POST` / `DELETE
  …/occurrences/{id}/also`, an `occurrence_also` feed entry, *"Done by maya,
  also sara"* on the row, the sheet and the chore's history.

  What it deliberately does not do, each a decision:
  - **No credit.** `done_by` still decides whose turn it counted as. Rotation,
    debts and the per-person count are untouched — otherwise adding yourself
    to everything would pad a number (constraint 4) and could move a turn.
  - **No note.** A text box beside somebody else's completion is a comment
    thread and a quality verdict (constraint 1). The fact of the name is the
    whole message; the sheet's line under the button says the turn is still
    theirs.
  - **Two days, not forever.** Long enough for "I finished it off the next
    morning", short enough that it cannot become a way to annotate the archive
    after an argument.
  - **Not by the doer, not twice.** And taking your own name off is allowed
    for ten minutes, the same terms as undoing a completion.
  - **Undo takes it with it.** Reopening a completion deletes its "also"
    rows; otherwise an open turn would say somebody helped with a chore that
    is not done.
  - **In the feed, never pushed** — a group-visible change (constraint 7),
    and there is no push channel anyway.

- **WHAT DONE MEANS moved onto the button (2026-11-01).** Raised in review: the
  done-line headed the detail sheet, and the four-row list plus History / Edit
  chore stood between it and *I did this* — the agreement was something you had
  scrolled past by the time you decided. It now sits below the rule, directly
  on top of the action, with nothing in between. This is a fourth departure
  from the Stitch mock, which puts it under the header. The swipe path already
  quoted it (`MarkDoneSheet`), so both routes to done now end on the agreement.

  **Declined in the same review — a completion push to the rest of the
  household.** Completions already write `occurrence_done` to the Activity
  feed, and the board polls it while open. A notification to people who are
  not looking would close the "re-cleaned a room that was already done" gap
  from the interviews, but it needs either a background poller or FCM, and the
  feed is judged enough for now. The rules it would have to keep are on record
  here in case it returns: never to the doer, held until the ten-minute undo
  lapses, held through the receiver's quiet hours, and never a word about the
  due date (constraints 2, 3 and 7).

- **Single-tap completion — REPLACED BY A CONFIRM SHEET (2026-09-14).** F1 says
  completion is one tap, and it was. Two problems. A tap lands by accident in a
  scrolling list, and the only way back is the ten-minute undo, which the person
  who mis-tapped has to notice before it lapses. More importantly the
  **done-line** — F4's whole mechanism, the household's agreed definition of
  done — was readable only in the occurrence detail sheet, i.e. behind a
  deliberate second action nobody takes while holding a mop. The agreement was
  therefore present at the moment of *arguing* about a chore and absent at the
  moment of *finishing* one, which is backwards.

  The tick now opens `ui/components/MarkDoneSheet.kt` and completes nothing on
  its own: the chore name, the done-line quoted on its own surface under "What
  done means", one neutral sentence, a full-width *Mark it done* and *Not yet*
  under it. It is a restatement, not an "are you sure" — no lateness, no day
  count, nothing about the person (constraint 3). A blank done-line is a normal
  state and reads as one.

  **A bottom sheet, not a centred dialog** (revised the same day, after seeing
  it on screen). Everything else a row asks for — the occurrence detail, the
  pass, the day picker — arrives from the bottom edge, near the thumb that
  tapped it. A dialog in the middle of the screen reads as an interruption from
  the app; a sheet reads as the row opening up, and is the idiom the rest of the
  board already uses.

  Scope decisions worth recording:
  - **Unticking is still instant.** Undo is only offered to the person who
    ticked, within ten minutes; they have just read this sheet, and asking them
    twice about their own correction is friction with no information in it.
  - **The confirm lives in `TaskCard`**, not in either screen's wiring, so the
    board and My chores cannot drift — and the swipe-right shortcut routes
    through the same state rather than keeping a second, quieter path to done.
  - **Legacy one-off tasks** have no agreement, only a description, so the
    dialog labels theirs "What this is". Promoting a description to an
    agreement would invent one the household never made.
  - The checkbox does **not** tick optimistically while the sheet is open. The
    row keeps telling the truth until the request lands, so a dismissed sheet
    leaves the board exactly as it was.

  FEATURES.md §2 updated; F1's "one tap" is now one tap plus one confirm.

- **The checkbox — REMOVED (2026-09-15).** The confirm above was the right
  instinct in the wrong place: it kept a *control* on the row, and a control on
  a row invites the decision to be taken from the list, which is the one screen
  where the done-line is not visible. So the box is gone. The cell is a button,
  the chore opens, and done is said there.

  - **The row now only shows.** A completed row carries a green tick where the
    box was and a dimmed title. Green for done is safe under constraint 3 in a
    way red for late is not: it marks a thing that happened, not a person who
    failed. `doneColor()` picks `#0F7B5A` in light and the theme's `tertiary`
    in dark, the same shape of decision as `overdueColor()`.
  - **`OccurrenceDetailSheet` was rebuilt** from the Stitch mock *Chore detail —
    Clean the bathroom* (`1f57d1564abb495b9006f5cefe8e1b48`, project **Core
    Design System**): header with schedule line and household chip, violet
    callout, WHAT DONE MEANS card, a quiet four-row list, History / Edit chore,
    then **I did this** and **Busy — pass it on**. Departures from the mock: the
    footer is not pinned (the sheet scrolls as one column), the away banner in
    the mock's background board is not part of this screen, and *I did this*
    becomes *Mark as done* on somebody else's turn — F1 lets anyone tick
    anything, but not in the first person.
  - **Undo moved with it.** It is a button in the sheet, not an untickable box,
    and the ten-minute window is now waited out by `OccurrenceDetailHost`
    rather than by every visible row. That deletes one coroutine per undoable
    row, and the B-6 trap (reading the clock during composition) is guarded in
    one place instead of many.
  - **`MarkDoneSheet` survives for the swipe only.** Swipe-right completes a
    turn without opening it, so it is the one gesture that has shown you
    nothing — it asks, and quotes the done-line while asking.
  - **Fixed in passing: the swipe-right background.** It painted
    `tertiaryContainer`, which this theme never sets, so it fell through to
    Material's baseline **dark red** — a red wash behind the word "Done", which
    is exactly the signal constraint 3 rules out, chosen by nobody. Both swipe
    backgrounds are now our own tokens at 16% (`doneColor()` and `secondary`).

- **The overflow menu — REMOVED (2026-09-13).** Quiet hours, appearance, join
  with a code and sign out lived behind three dots on the group list. A menu is
  where you put things you have nowhere else for; all four are things a person
  goes looking for. They are now rows in a **Settings** tab. The dots are gone
  from that screen entirely.
- **The group list — REMOVED (2026-09-13).** DESIGN.md §4.2, done properly a few
  hours after the bottom bar shipped with the list still in it. The first tab
  opens the board of the household you last had open; the household name in its
  app bar is the switcher, and the menu behind it also carries *Create a
  household* and *Join with a code*, which is now the only place they live.

  The switcher **stays visible with one household**, where §3 says to hide it.
  §3's reasoning is that a chevron promising a choice with one option is a lie;
  it is not one here, because the menu is also the only route to a second
  household, and somebody in one flat is exactly the person most likely to want
  it.

  What this pulled with it, all recorded because it is the kind of thing that
  looks arbitrary a month later:
  - **The board is a root destination.** No back arrow, bottom bar underneath,
    and `group/{groupId}` deleted — three routes left in the whole graph.
    Tapping a turn in My chores switches the board's household rather than
    pushing a second copy of the board, so the current household is held by
    `MainShell` where both writers reach it.
  - **`GroupDetailViewModel` is keyed on the group id.** `viewModel()` keys by
    type within a store owner, so without the key, switching household kept the
    previous household's ViewModel and showed its chores under the new name.
  - **`LastHousehold` is on the device, not the account** — the same argument as
    `Appearance`. It is never trusted: the id may name a household you have left
    or one belonging to whoever was signed in before, so it is resolved against
    the group list and falls back to the first household. Cleared on sign-out.
  - **`DashboardViewModel` → `ShellViewModel`; `DashboardScreen.kt` →
    `AccountDialogs.kt`.** There is no dashboard to name.
- **My chores and My week ship together, on purpose, until the interviews
  (2026-09-13).** Two readings of the same set of turns, behind a toggle in the
  app bar: grouped **by household**, or DESIGN.md §5.11a's **by week** — a
  seven-day strip over dated buckets. The title follows the toggle and the
  choice is kept on the device.

  This is not indecision and it is not a permanent second destination. The two
  answer the same question differently — *whose flat is this on me for* versus
  *when is it on me* — and which one a household actually reaches for is a
  question about people. It is being put in front of people rather than argued
  about, and **one of the two is to be deleted after the interviews**. If that
  has not happened by the time the report is written, it is a defect, not a
  feature.

  The strip is neither the calendar constraint 5 forbids nor the tab
  `calendar_decision_record.md` deleted: no external calendar, no free/busy, and
  nothing of anybody else's drawn on it. A dot means *you* have a turn that day,
  never how many, and no day is coloured by how full it is.
- **My week's list is scoped to the strip (2026-09-13).** The window is the
  displayed week, or one day of it once a day is tapped. A strip that says which
  days carry something, over a list that ignores it and shows everything, makes
  the strip decorative — and the two then disagree about what "this week" means.

  This replaces DESIGN.md §5.11a's TODAY / TOMORROW / LATER THIS WEEK / NEXT
  WEEK AND BEYOND buckets with day headings inside the window. Those buckets
  describe an unbounded list; "next week and beyond" cannot appear in a view
  that stops on Sunday. What survives unchanged: no LATE heading anywhere (an
  overdue turn sits under its own day, date in amber), NO DATE for as-needed
  turns — current week only, and not once a day is picked, since they belong to
  no date — and DONE THIS CYCLE keyed on the day it was *done* rather than the
  day it was due.

  The empty state is deadpan rather than congratulatory. Nothing to do is the
  ordinary state of a flat that works, not an achievement, and a cheer here
  would be the gamification the never-list forbids wearing a joke as a disguise.
  No line mentions anybody else or what they might still have on.
- **The detail sheet is shared, not copied (2026-09-13).** Tapping a turn in My
  chores opens the board's own sheet, with its history, edit flow, delete
  confirm and busy pass behind it. Rather than copy that wiring, it came out of
  `GroupDetailScreen` into `OccurrenceDetailHost` with a hoisted
  `ChoreDetailController`; both screens mount the same composable. Same
  reasoning as `AuthScaffold` and the shared `TaskCard`.

  My chores spans households, so it builds a `GroupDetailViewModel` for
  whichever household the tapped turn belongs to. That is why **Busy? Pass it
  on** works from the sheet there while swipe-to-pass on the row does not: the
  swipe needs the rotation and the away states, which the tab does not fetch.
- **B-9: a turn due tomorrow crashed My week on a Sunday (2026-09-13, fixed).**
  "LATER THIS WEEK" ended on `today.with(SUNDAY)`, which on a Sunday is today —
  so tomorrow was already *after* the end of the week and fell into TOMORROW and
  NEXT WEEK AND BEYOND at once. A `LazyColumn` throws `IllegalArgumentException:
  Key … was already used` rather than drawing the row twice, so the app died on
  opening the tab. The boundary is now coerced to at least tomorrow, and a
  `seen` set makes any future boundary mistake cosmetic rather than fatal.
  `WeekAgendaTest` walks every weekday and asserts no turn is bucketed twice and
  none is lost.

  Worth generalising: **every bucketing of the same list into sections is a
  duplicate-key risk**, and in Compose that is a crash rather than a glitch.
- **Bottom navigation — PARTIALLY BUILT (2026-09-13).** DESIGN.md §4.1's shell,
  with one deliberate departure from §5 recorded here rather than silently:
  - **The List half of "My week", not the Calendar half.** §5's My week has List and Calendar
    modes over a day strip and a month grid. This is the List half only. The
    calendar half is not deferred work so much as a question — the month grid
    of your own projected turns is the nearest thing in the design to a
    schedule view, and `calendar_decision_record.md` is about exactly that
    argument. Build the rest of it after re-reading that record, not before.

  What the tab does have: your open turns across **every** household, grouped by
  household, oldest date first, with done-this-cycle rows kept last so the
  ten-minute undo is still reachable. It reuses the board's `TaskCard` rather
  than a second row implementation — the same reasoning that produced
  `AuthScaffold`. Passing is deliberately not offered there: it needs the
  rotation and the household's away states, so it stays on the board, one tap
  through the row.
- **Material's baseline container colours are a constraint-3 trap (2026-09-13).**
  `Theme.kt` sets `primary`, `secondary` and `tertiary` and nothing else, so
  every `…Container` role falls through to Material's baseline scheme — where
  `tertiaryContainer` is a **dark red**. The household switcher's monogram was
  tinted from it and shipped a red disc beside the chore rows until a screenshot
  caught it. Nobody chose that colour; the theme simply had no opinion and the
  library did. Tint from the theme's own three colours at low alpha instead, or
  define the container roles in `Theme.kt`.
- **`PATCH /me` now takes `username` and `email` (2026-09-13).** Settings can
  change either. Notes for anyone extending it:
  - **Stricter than register on email, on purpose.** Register accepts any
    non-empty string; this parses the address and rejects a display-name form
    (`Ann <ann@x>`), because email is the login identifier and a typo here locks
    the person out of their own account. Several dev accounts predate the check
    and hold addresses it would now refuse — they can still sign in, they simply
    cannot change to another bad one.
  - Username is trimmed and capped at `models.MaxUsernameLength` (32). Nothing
    in the data model needs the cap; the member rows and rotation pickers that
    render it do.
  - Both columns are `UNIQUE`, so a taken name or address is a **409** with
    register's own wording. Anything else from the UPDATE stays a 500 rather
    than being reported to the person as their fault.
  - **No password field, and no password-change route.** Changing a password
    needs the old one and a different failure mode. Same reason the sign-in
    screen has no *Forgot?* link.
- **Manager role + deadline permissions — REMOVED.** The spec makes chore
  editing open to every member, so the gate contradicted it.
- **F1 (Board) — DONE and device-verified** (2026-08-31). Driven on the emulator
  against a local backend: the three sections, single-group auto-open and its
  Back behaviour, one-tap completion by a non-assignee with `done_by` recorded,
  undo restricted to the doer (another member's completed row is greyed and
  sends no request), and amber — not red — overdue. See B-6 for the one defect
  this turned up.
- **F2 (chores, rotation, spawning) — DONE** on `v2-chores`, in three commits.
  Both open questions were settled first: chores/occurrences are **new tables
  alongside `tasks`** (existing tasks stay as the spec's one-off type). What
  landed: the chore/occurrence model with CRUD and the board reading it;
  completion spawning the next occurrence, interval dates counted from the
  completion, and a missed fixed date rolling forward with the same assignee;
  and the unified turn rule — a cover counts as the doer's turn, the chore goes
  back to whoever owed it, and the rotation resumes after the coverer.
  Device-verified at each step.
- **F3 (reminders + quiet hours) — DONE**, in three commits. Reminders are
  scheduled **on the device** with AlarmManager — no Firebase, no push service,
  no server-side scheduler, and nothing about anyone's chores leaves the phone.
  Quiet hours live on the user record so they survive a reinstall. Two reminders
  per occurrence to the assignee only, plus a flat 48-hourly nudge for anything
  still open. See B-8 for the one gap this delivery choice leaves.
- **F4 (done-line + edit diffs) — DONE**, in two commits. The occurrence detail
  sheet shows the done-line beside the chore's frequency (it had been stored
  since F2 and displayed nowhere), and "Edit chore" on that sheet lets any
  member change the name, done-line, schedule parameters and rotation. Only
  changed fields are sent, and the group sees the diff the backend already
  produced — the spec's own example, "weekly → every 3 days", now reachable
  from the UI. The schedule *type* stays immutable.
- **F5 (busy/away) — IN PROGRESS.** The busy pass is done server-side:
  `POST /occurrences/{id}/pass` hands the chore to the next person in the
  rotation, refreshes an already-overdue date to tomorrow, and keeps the debt
  with the passer via `passed_from`. It reuses `nextTurn` rather than growing a
  second rule — whoever ends up doing a passed chore is doing it for the passer,
  which is exactly a voluntary cover. No activity event is written: the spec
  gives a pass one private notification to the receiver and no group broadcast.
  **Away** is done server-side too: `PUT /groups/{id}/members/me/away`, per
  household, open-ended or dated. Rotation assignment steps over away members
  and they re-enter at the same position on return — no bookkeeping needed,
  since the order never changes. An expired period needs no cleanup. Away is
  *not* a debt: it never cancels a turn already owed. `ListMembers` reports it,
  because the spec makes away deliberately impossible to hide.
  The **client** has both now: "Busy — pass it on" on an open occurrence of
  yours, and an away toggle in the group's overflow menu whose dialog states the
  rule the app cannot enforce and points at busy as the alternative. Away shows
  on the member list and beside names in every rotation picker.
- **F6 (history) — IN PROGRESS**, the last feature in the build order. It began
  with a correction rather than a view: away was stored as a *current flag*, so
  a past absence was unrecoverable — and the per-person view counts completions
  over a window, where an unexplained dip reads as exactly the flaking the spec
  forbids. Away is now an `away_periods` record, closed rather than erased on
  return, with a one-time backfill so upgrading doesn't silently un-away anyone.
  Both **endpoints** are done: per chore, a timeline of completions with the
  due date and the done-at and deliberately no "late" flag; per person,
  completions in a window counted by `done_by`, in join order, with everyone
  listed and days-away alongside. The **client** has both: "History" on the
  occurrence detail, and "What's been done" in the group's overflow menu.
- **The spec's build order is complete** — F1 through F6. What remains is
  deployment (see the handoff) and whatever the report needs.
- **One create button, one form — DONE** (2026-09-01, UI cleanup phase 2). The
  board had two FABs, a primary one for chores and a small one for one-off
  tasks, which made the household choose a storage model before it could write
  anything down. There is now one "Add chore" button and one dialog, whose
  shared fields (name, done-line) are followed by a single question — **"Does
  this repeat?"**, *One time* / *Repeats* — and the answer decides the rest of
  the form. *One time* offers an assignee (defaulting to the creator) and an
  optional date and submits through the **task** path; *Repeats* offers the
  schedule and rotation and submits through the **chore** path. No endpoint
  changed, and `createTask` grew only a `dueDate` argument it already had a
  request field for.
  The one-off branch deliberately has no "Nobody" chip: the spec assigns
  everything, always (constraint 8). See B-12 for the leftover on the detail
  sheet.
  Device-verified: a one-off created through the merged form landed on the
  board with the chosen person and date (`POST /tasks`, 201); a fortnightly
  chore landed with its first occurrence (`POST /chores`, 201); with the
  backend killed the dialog stayed open with the name, interval and rotation
  intact and "Network error: Failed to connect to /10.0.2.2:8080" in its own
  error slot (B-7 not regressed), and pressing Create again once the server was
  back submitted the same form successfully.
- **v2 UI phase 1 (board grammar) — DONE** (2026-09-02). Implements section 1
  of the `Chore UI v2` deck.
  - **Status language is open / done, nothing else.** The `in_progress` pill is
    gone from the row, `StatusChip` deleted with it (it had no other caller),
    and the task detail sheet offers **Open / Done** rather than three states.
    `"todo"` stays as the wire value — only the word changed — so nothing needs
    migrating and legacy `in_progress` rows still read as open. `statusLabel`
    keeps its `in_progress` case for the activity feed, which is a record of
    what happened rather than of what the app would say today.
  - **The row is three lines now**, per the deck: name, then how often it comes
    round ("every 4 days", "Thursdays", "as needed"), then whose turn it is and
    when it is wanted ("Due Sun 6 Sep", "Your turn", "maya · due Sun 6 Sep",
    "Done by maya · Wed 26 Aug"). Putting the frequency on the row is F4's
    point: half an agreement on screen is not the treaty.
  - A one-off states its date on the schedule line ("one-off · Sat 29 Aug"),
    because a one-off's date *is* its schedule, and the cycle line below then
    carries only the name. **The amber overdue treatment follows the date**
    onto whichever line holds it — the first cut hard-wired it to the cycle
    line and an overdue one-off lost its amber entirely.
  - Section headers take the deck's wording: **YOURS / OTHERS / DONE THIS
    CYCLE**, and the per-section count comes off the header.
  - The done-line stays in the sheet only. The deck does not put it on the row
    and the prompt says sheet if in doubt.
  Device-verified at a true 360dp (density 480 on a 1080px screen) with a
  46-character chore name, an as-needed row, an overdue one-off and a done row:
  nothing truncates, the long name wraps to two lines, and nothing is red.

- **v2 UI phase 3 (stepped creation) — DONE** (2026-09-02). Section 3 of the
  deck. The merged dialog is replaced by a full-screen two-screen flow in
  `CreateChoreFlow.kt`; `CreateEntryDialog` is deleted.
  - **The schedule types are never named.** Screen 1 asks *how do you know it's
    time to do it?* and infers the type from the answer; screen 2 asks only what
    that answer needs. Reaching "as needed" is a matter of recognising your own
    situation rather than learning a taxonomy — which is the thing the housemate
    sessions are meant to test.
  - Deck copy verbatim throughout, including the four answers and their
    examples, every parameter explainer, and the discard sheet.
  - **Two things became sayable that were not.** `createChore` never passed
    `fixed_month_days` or `needed_by_time`, both of which the request already
    had — so "rent on the 1st" could not be expressed at all. The flow offers
    Day of week / Day of month and an optional needed-by time.
  - The X always asks, even with nothing typed. A conditional prompt is a trap
    that springs on exactly the occasion it matters.
  - Rotation reorder is offered as up/down controls beside the drag affordance.
    The deck shows a drag handle; the buttons do the same job for screen-reader
    and switch users, and are the only version a test harness can drive.
  - **One bug found only by driving it:** the default rotation (everyone, you
    first) was computed at render time and never written into the draft, so
    *Add chore* failed with "pick at least one person to take a turn" against a
    visible list of three names. Defaults are now applied to the draft on the
    way into screen 2, so the screen and the model agree.
  Device-verified end to end, all four types: interval (`interval_days: 14`),
  fixed-date by month day (`fixed_month_days: [1]`, 201), as-needed, and a
  one-off routed to `POST /tasks` with its assignee and date. Next stays
  disabled until a name and an answer exist; Back preserves both screens (the
  14-day choice survived a round trip); X shows the discard sheet on both
  screens and Keep editing returns to the form. The new row lands highlighted
  with a "{Chore} added." snackbar.

- **v2 UI phase 3, copy revision — DONE** (2026-09-02). Babak's call, and a
  deliberate departure from the deck, whose copy the prompt had made canonical.
  - Screen 1's chooser was a wall of prose: four sentence-length labels each
    with an example underneath. Two of them did not say what they meant —
    "a while since it was last done" never mentions the thing it is actually
    asking for, which is a number of days.
  - The heading is now **"How often?"** and the four labels are **Every so many
    days / A fixed day of the week or month / As needed / A one-time thing**.
    The schedule types are still never named; the labels just say what the
    answer *is* rather than describing the situation it comes from.
  - The explanations moved behind a **circled "?"** on each row, using
    Material's own tooltip so it behaves the way a "?" does elsewhere on the
    phone. **Persistent, not timed:** it stays until dismissed by tapping
    outside or tapping the "?" again. A timeout would be a race against the
    reader, and two sentences outlast any sensible one. Tapping the "?" does
    not select the option.
  - **Long dashes are out of every user-facing string in the app**, not just
    the new ones: the away banner, quiet hours, the pass confirmation and
    message, the occurrence sheet, the rotation note. Empty-value placeholders
    that were a bare dash now say "Nobody" and "Not recorded".
  **Note for the report:** the app's copy and the research deck now differ here.
  The deck remains the stimulus that was designed; if the housemate sessions run
  against the app, they test these labels instead.

- **v2 UI phase 4 (edit and delete) — DONE** (2026-09-03). Editing reuses the
  two-screen flow, prefilled; `EditChoreDialog` is deleted.
  - **Backend, with tests.** A chore's schedule *type* can now move among
    interval / fixed_date / as_needed, which it could not: `UpdateChoreRequest`
    had no `schedule_type` at all. one_off stays unreachable in both directions
    and returns 400 — a one-off is a row in `tasks`, so that switch is a delete
    and an add, not a column.
  - **The one new rule, and it lives in a comment beside the code:** the
    assignee never changes because of an edit; a changed schedule re-dates the
    open occurrence from the last completion, or from the chore's creation if
    it has never been done; moving to as-needed clears the date and moving away
    from it sets one; a rotation reorder applies from the next spawn only.
  - A fixed-date chore whose anchor is old can land on a date already past.
    Left alone rather than clamped: `rollForwardFixedDates` already walks a
    lapsed fixed date forward keeping the same assignee, and runs on the next
    board read. A clamp here would be a second rule doing the first one's job.
  - **A scan bug worth remembering:** the anchor query used
    `COALESCE(MAX(done_at), created_at)`, and go-sqlite3 parses a DATETIME from
    the column's *declared* type — which `COALESCE` and `MAX()` throw away. The
    value came back as a raw string and every edit 500'd. Two plain selects
    instead.
  - Screen 1 disables the kinds a chore cannot become and says why. X asks
    "Discard changes? / Your edits won't be saved." Delete sits at the foot of
    screen 2 in the edit flow only, reusing F-2's confirm.
  Four backend tests (re-date keeps the assignee, as-needed clears and restores
  the date, the one-off boundary is refused, a reorder leaves the current turn
  alone); full suite green twice. Device-verified: weekly → Fridays sent only
  `{"fixed_weekdays":[5],"schedule_type":"fixed_date"}`, kept the assignee,
  re-dated 9 Sep → Fri 4 Sep, and broadcast "Clean the bathroom: schedule:
  weekly → Fridays"; switching to as-needed cleared the date; X mid-edit
  discarded and left the name untouched; delete returned 204 and took the row.

- **v2 UI phase 2 (busy pass by swipe) — DONE** (2026-09-03), with one gap
  recorded below.
  - **Swipe your own open chore row**: left passes it, right marks it done. Not
    on somebody else's row (neither action is yours to take about their turn),
    not on a one-off (no rotation to pass along), and not when there is nobody
    to pass to — a rotation of one, or everyone else away. The detail sheet
    keeps its own button, so the gesture is a shortcut rather than the only way.
  - **A one-line hint** under the first row of YOURS, gone on the first swipe
    and never shown again. Stored in plain SharedPreferences: whether somebody
    has found a gesture is not household data and has no business on the server.
  - **The confirmation states the debt rule in plain language**, with the right
    date variant: "due tomorrow" only when the pass actually re-dates an
    already-due chore, the real date when it is still ahead, and "it's their
    turn" for an as-needed chore that has no date at all.
  - **Backend, `covered_by` (+ tests).** An occurrence handed back under the
    debt rule records who covered it, so the board can say
    *"Back to you after maya covered"*. The passer's board meanwhile shows
    *"Next turn comes back to you. maya is covering this one"*, from the
    `passed_from` it already had. The migration added the column to a populated
    database without disturbing it.
  - **The bug worth remembering:** the first condition compared the new
    assignee against `assigned_to` alone, which catches a voluntary cover and
    misses a *pass* — the very case the marker exists for. The unit test I
    wrote first covered only the cover case and passed. It now mirrors
    `nextTurn`'s own `owedBy` calculation, and there is a second test for the
    pass path.
  - **Gap: no Undo on the pass snackbar.** The deck shows one and the prompt
    asks for it, but undoing a pass means writing `assigned_to` back and
    clearing `passed_from`, and no endpoint does that. The phase authorises one
    backend change (`covered_by`), so the endpoint was not invented. The
    snackbar reads "Passed to maya. It's yours again next cycle." with no
    action. **Needs a decision**: add `DELETE /occurrences/{id}/pass`, or drop
    Undo from the design.
  Device-verified: hint shown then dismissed on first swipe; a one-person
  rotation correctly does not swipe; confirmation named maya and used the
  future-date variant; the pass created no group activity entry (constraint 7);
  both markers appear on the right rows.

- **v2 UI phase 5 (first run) — DONE** (2026-09-03). A new group lands on an
  offer instead of an empty board, closing the spec's onboarding loop: create or
  join, add three to five chores, land on the board.
  - The deck's five starters, each showing the wording it will have (Trash / as
    needed, Bathroom / every 4 days, Kitchen floor / weekly, Recycling /
    Tuesdays, Hallway vacuum / fortnightly). That teaches the schedule kinds by
    example before anyone meets the create flow, which is the second job the
    list does.
  - Rows are keep-or-remove **and renameable**: "Trash" is what one household
    calls it and "Bins" is what the next one does, and being able to say so is
    the difference between a list you accept and one you agree. "Something
    else" adds a blank row rather than opening the create flow, which would
    lose the rest of the choices on the way out and back.
  - **Skip** is always there. Each kept starter becomes an ordinary chore,
    editable through phase 4 like any other.
  - Creation is sequential so a partial failure is legible — five requests in
    flight can fail in five ways and leave the household guessing which chores
    exist. (An earlier comment claimed it preserved board order; it does not,
    since the board sorts by status and due date. Corrected rather than left.)
  Device-verified: new group "Lake House" → five offered → three removed, one
  renamed Trash to Bins → "Add 2 chores" → board shows exactly Bathroom (every
  4 days, dated) and Bins (as needed, "Your turn"), nothing else. Skip on a
  second new group added nothing and landed on the empty board.

- **v2 UI phase 6 (appearance) — DONE** (2026-09-03). **System / Light / Dark**
  in the Dashboard overflow menu, beside quiet hours, because they are the same
  kind of thing: settings about the person, not the household.
  - The app's existing dark scheme turned out to *be* the deck's dark palette
    already — background `#0F1117`, surface `#181A24`, violet `#A78BFA`, green
    `#34D399` all match. The one difference was the overdue amber, `#E3B341`
    picked by eye at F1 against the deck's `#E0A534`. Now the deck's.
  - **A real bug the toggle exposed:** `overdueColor()` asked
    `isSystemInDarkTheme()`. That was right while the app only followed the
    system and wrong the moment it could be overridden — a phone in light mode
    showing the app in dark would have drawn the *light* amber onto a dark
    card, which is the one colour in the app that has to stay legible. It now
    asks the theme, via the surface's luminance.
  - **SharedPreferences, not DataStore.** The prompt says DataStore; it is not a
    dependency, and one enum written on a radio tap and read once at startup
    does not need asynchronous storage or a new library. The swipe hint next
    door is kept the same way. Flagged rather than done silently.
  - Kept on the device and not on the server: it is a property of this phone in
    this person's hand, and syncing it would decide the tablet's appearance for
    them.
  - `dynamicColor` stays `false`.
  Device-verified: all three modes; Dark survived a force-stop and relaunch;
  the overdue row is amber in both appearances (dark amber on dark, even with
  the phone's own system theme set to light) and nothing is red on either.

- **The board is the screen — DONE** (2026-09-02, UI cleanup phase 3), in four
  commits:
  - **Calendar tab deleted.** It read `state.tasks` only, so it showed the
    one-off minority of the household's work and hid every rotation chore — on
    the test board it claimed "1 task due this month" while five chores sat on
    the board. Teaching it about occurrences would have meant building the
    scheduling view constraint 5 rules out, so it went instead. That also
    retires the generous reading of constraint 5 the handoff flagged: there is
    no calendar to defend in the report now. Tabs are **Board / Activity /
    Members**, and a plain `TabRow` again.
    Written up in full as
    [`calendar_decision_record.md`](calendar_decision_record.md), which adds the
    reason the code alone does not show: under completion-anchored rotation a
    chore has only **one live occurrence**, so a forward-looking month grid has
    almost nothing to draw. Re-adding one is a spec change, not a UI task.
  - **Quiet hours moved to the Dashboard menu.** It is stored on the user
    record and applies to every group, but it was reached from one group's
    overflow menu. Saving it there now also re-arms every group's alarms,
    because the Dashboard is what schedules them (see B-8).
  - **Away is visible on your own board.** A quiet banner above the sections,
    only while you are away in this group, with an inline "I'm back". Away
    already showed on the member list and in rotation pickers — everywhere
    except the screen the away person is actually looking at.
  - **The Board tab counts open rows** (B-11).
  Device-verified throughout; the details are in each commit message.
- **When the whole household is busy — DONE** (2026-09-03, Babak's rule, added
  mid-pass). Circulating passes were allowed (2.2), which left a chore able to
  lap the household forever. Babak's answer, and it is a better one than
  blocking the pass: when everybody available has passed it, **the chore
  returns to whoever asked first and they pick the day**, no later than the
  chore's own next date.

  How it lands:

  - `everyoneHasPassed` counts only *available* members, so a household of
    three with one away is a household of two for this purpose.
  - The closing pass still counts. Whoever passes last owes a turn like
    everybody else; they do not escape by being last.
  - The chore comes back dated to the chore's **next scheduled date**, which is
    both the sensible default and the bound. `PUT .../occurrences/{id}/due-date`
    brings it forward and refuses to push it back, because otherwise "we were
    all busy" becomes a way to postpone a chore a round at a time. If nobody
    picks, that date stands and the chore stands with it, which is what Babak
    chose.
  - Asking again once the round has closed is refused with what to do instead,
    rather than silently lapping: *"everyone has passed this one, so there is
    nobody to hand it to. Pick a day for it instead."*
  - An as-needed chore has no next date, so nothing to default to and nothing
    to bound. It comes back and waits.
  - `needs_date` is computed per request and sent **only on the holder's own
    rows**: that a whole household declined a chore is between them and the
    person now holding it.

  Three copy problems the device found, none of which the tests would have:

  - The board first said "Everyone's busy, so it's back with you. Pick a day."
    and kept saying it after a day was picked, because the flag derives from
    who has passed and that does not change when a date is set. The board now
    states the fact and the detail sheet carries the action.
  - The sheet still showed "You passed this on. It comes back to you next
    time" while the chore was already back in the passer's hands. Suppressed
    when the holder is the passer.
  - "Busy? Pass it on" was still offered after the round closed, where it could
    only earn a refusal. Hidden.

- **"Your turn" no longer appears on one-offs — DONE** (2026-09-03, Babak's
  correction). A one-off has no rotation, so it has no turns: it was given to
  one person once and stays with them. "Your turn" borrowed a word that means
  something precise everywhere else on the board and nothing here. Your own
  one-offs now show no third line at all, since the YOURS heading has already
  said it; somebody else's show the bare name. Rotation chores keep "your
  turn" and "sam's turn", which are true.

- **The turn rule, verified case by case — DONE** (2026-09-03), against
  `turn_rule_cases_prompt.md`. Thirteen cases now live in
  `internal/handlers/turn_rule_test.go`, each asserting a whole *sequence* of
  assignments rather than one step, because that sequence is the only part of
  the engine a household can actually see.

  **Four of the document's cases were wrong in code, and one of its own
  premises was wrong.**

  - **2.1, chained passes.** The document suspected that "B's pass overwrites
    A's, so only B is charged". The storage did the **opposite**: `passed_from`
    kept the *first* passer, so A was charged and B was never recorded at all.
    Neither the suspicion nor the requirement. Reported before touching it, as
    Part 5 asks.

    Fixed by replacing the single pointer with `occurrences.passed_chain`, an
    ordered list, and `occurrences.pending_debts`, the queue of turns still
    owed. Each passer owes one and they are repaid oldest first. This
    **reverses** the previous rule, which held that passing on "declines a
    favour, not a duty"; that reasoning is recorded in the renamed
    `TestAChainOfPassesRepaysTheFirstPasserFirst` so nobody restores it by
    accident.

  - **1.3, repeated skips.** Covering went `B, B, B`: the pass always targeted
    the person immediately after the *skipper*, so one unlucky neighbour
    absorbed every skip by a serial passer. It now counts on from the last
    coverer, which gives the document's `B, C, B`.

  - **1.5, a debt across an absence.** The turn was handed back to a member who
    was away and simply sat on their empty row, which contradicts constraint 8
    lifting an away member out of every rotation. The debt is now *held*: the
    ordinary rotation carries on without them and the turn returns when they
    do. `TestAwayDoesNotCancelADebtAlreadyOwed` asserted the old behaviour and
    has been rewritten rather than deleted, with the reason.

  - **2.2, an occurrence passed twice by the same person.** The document's
    stated decision was to block it; Babak's call was to **allow** circulation.
    Allowed, with one consequence handled: the chain records each person
    **once**, in first-pass order. Part 0 forbids counting anyone's skips, so a
    chain holding every pass event would be a skip counter in all but name, and
    3.5 wants one debt per person anyway. A repeat pass is therefore permitted
    but buys no second debt, and the chain stays bounded by the rotation.

  **Part 3's unnamed cases** are covered by tests and fall out of the same
  mechanism: a resume point that is not in the rotation falls back to the
  position of whoever owed the turn (3.1, 3.2), and a debt is filtered out when
  its owner leaves the rotation (3.3, 3.4). Undo needed no work (3.7) because
  the debts live on the *completed* occurrence and completion does not consume
  them; a test pins it.

  **The client had a copy of the pass rule and it drifted.** `nextInRotationAfter`
  in `PassChore.kt` predicts the receiver so the confirm dialog can name them
  before the request is sent, and it still counted from the holder. With sam
  brought back into the household the dialog said "maya" while the server would
  have assigned sam. Both now count from `resume_after`, which meant exposing
  that field to the client. **This is the second time the two have diverged**;
  the durable fix is for the server to return the prospective receiver instead
  of the client recomputing it, and that is worth doing before anything else
  touches the pass.

  Verified: 13 cases green, the whole backend suite green, `go vet` clean, the
  two-column migration run against the populated local database with every row
  count unchanged, a test for the read-time backfill of pre-chain rows (the
  live data had none, so nothing else would have caught a mistake there), and
  the pass driven on the device with the dialog and the board naming the same
  person. The seeded household was restored to exactly its previous state
  afterwards.

  **Not on the wire, deliberately:** `passed_chain` and `pending_debts` are
  `json:"-"`. Publishing who has passed what, or who owes turns, is what
  constraint 7 exists to prevent, and a client that could read it could count
  it. **Worth a separate look:** `passed_from` *is* already published to every
  member, which is arguably the same leak and predates this work.

- **Undo on the pass snackbar — DONE** (2026-09-03, Babak's call). The v2
  design showed an Undo on "Passed to maya. It's yours again next cycle." and
  there was none. `DELETE /groups/{id}/occurrences/{id}/pass` now exists.

  **Scoped to a mis-swipe, not a change of mind**, which is what Babak asked
  for once the two were separated. `passUndoWindow` is 2 minutes, against the
  10 of `undoWindow`: undoing a completion only rewrites your own history,
  while undoing a pass takes work off somebody else's board after they have
  been told it is theirs.

  Four guards, each with a test:

  - Only the person the debt belongs to (`passed_from`). **Not the receiver**:
    being handed a chore is not consent to hand it back, and if it were, "pass"
    and "refuse" would be the same button.
  - Only while it is still open, so an undo cannot take away work that has
    actually happened.
  - Only inside the window, measured on the server from `passed_at`. The
    snackbar's own timer is not evidence; a client can be slow or paused.
  - No activity event, matching the pass itself. Constraint 7 keeps a pass
    between the two people involved and an undo is the same exchange.

  **The part that needed a migration.** The pass rule moves an already-overdue
  chore's deadline to tomorrow for whoever receives it. A naive undo would
  leave that in place, which makes pass-then-undo **a way to launder
  lateness**: a chore four days late comes back due tomorrow. So the pass now
  records the date it overwrote in a new `occurrences.due_before_pass` column
  and the undo restores it exactly. Written with `COALESCE` so a chain of
  passes keeps the *first* original, since the debt and the right to undo
  belong to whoever passed it first. Read with a plain `SELECT`, never through
  a wrapping function, because go-sqlite3 decides whether to parse a DATETIME
  from the column's declared type and `COALESCE`/`MAX` throw that away. That
  has bitten this file twice.

  Verified past the unit tests: the migration ran against the populated local
  database with every row count unchanged, the four HTTP paths were exercised
  with curl (200 restore, 409 never-passed, 403 receiver, 200 passer), and the
  button itself was driven on the device with Logcat showing `POST .../pass`
  then `DELETE .../pass` two seconds apart, both 200. The seed was left
  canonical afterwards, and the activity table still held 14 rows, which is the
  privacy guard proving itself rather than being asserted.

  **One client-side trap worth keeping.** The snackbar message and the Undo
  offer are written in a *single* state update. Two updates would let the
  snackbar be composed from the first and read an offer that had not landed
  yet, which shows up as an Undo button that is intermittently missing.

- **The return marker said the wrong thing to the wrong person — FIXED**
  (2026-09-03, reported by Babak). The line under a chore that has come back to
  you after a cover read **"Back to you after maya covered"**. Two defects, one
  of which Babak spotted and one which fell out of looking:

  - **Tense.** The cover has already happened, and the turn is already sitting
    on your board. "Back to you after X covered" frames a finished event as a
    pending condition, so it reads as though the turn were still on its way.
    It now reads "maya covered your last turn, so it's yours again".
  - **No ownership test.** The branch checked only that the occurrence had a
    `covered_by` and was open, never that it was *yours*. So every member saw
    the second-person line on somebody else's row: signed in as maya, the board
    said "Back to you after maya covered" on **demo's** row, addressed to the
    wrong person and naming maya in the third person in a sentence aimed at
    her. Other people's rows now read "maya covered demo's last turn", which is
    the useful half for them: it says why the rotation did not move on to
    *them*. Whose row it is is already on the line above.

  Verified on the device from both sides, signed in as demo and as maya. The
  `passedByMe` branch beside it was left alone; its tenses are already right.

- **The screens deck re-shot — DONE** (2026-09-03). Nine slides, 31 screens,
  all captured at 360dp against a running backend after the six v2 UI phases;
  the source screenshots are committed under `screens/` so the deck is
  rebuildable without the seeded database. Re-seeded first, because the scratch
  database had drifted into three groups with duplicate occurrences. The
  seeded completions and the activity feed's timestamps are both **backdated**,
  which matters: seeding completes a weekly chore twice 0.13s apart, and
  unadjusted the history screen said "Done Thu 27 Aug" while the feed said "1h"
  for the same event.

- **The rotation picker can be dragged — DONE** (2026-09-03, Babak's call).
  The deck's copy had always read "Drag to change the order" and the picker had
  only ever had up and down arrows. Rather than delete the promise, the gesture
  now exists: a 48dp handle at the head of each row, and the line reads
  "Everyone takes a turn. Drag to change the order."

  **The arrows stay, deliberately.** Dragging is not something TalkBack can
  perform, so they are the accessible route to the same change rather than a
  leftover; for the same reason the handle's icon is unlabelled, so screen
  readers are not offered a control they cannot work.

  Two things in the implementation are load-bearing and should not be
  "simplified":

  - Each row is wrapped in `key(id)`. Without it a reorder re-keys the
    `pointerInput` under the finger, because a plain `Column` identifies its
    children by position, and the drag dies on the first swap.
  - The gesture reads the order through `rememberUpdatedState` instead of
    closing over the list it started with. Every swap rewrites that list, so a
    captured copy would go stale and the second swap would act on stale
    positions.

  Row heights are measured rather than assumed, since the first row carries a
  "first turn" caption and an away member carries "away", so a fixed step would
  drift. Device-verified: one swap down, two swaps in a single continuous
  gesture in both directions, and the arrows still working alongside.

- Multi-select and bulk status were removed with F1, and the backend endpoint
  behind them is **now gone too** — nothing had called it since, and the
  ViewModel wrapper was unreachable from any screen. The
  `tasks_bulk_updated` activity constant and the client's rendering of it stay,
  because rows written before the removal still carry it.

---

## Bugs

### B-21 · Dark appearance on a light phone hid the status bar — DONE (2026-11-01)

`enableEdgeToEdge()` picks the status bar's icon colour from the *system*
theme. With the app set to Dark on a phone in light mode, the clock and
battery were drawn dark on the dark background. `TaskFlowTheme` now sets the
bar icon appearance from the theme it is actually drawing. Found while
re-shooting the deck.

### B-22 · My chores said "Done by someone" for a covered turn — DONE (2026-11-01)

My chores named the doer of your covered turn from `coveredByName`, which is
set on the turn *after* a cover, never on the covered one. The backend now
joins `done_by_name` into every occurrence and the row uses it.

### B-23 · My chores does not refresh after changes made on the board — DONE (2026-11-01)

Completing or passing a turn on the board leaves My chores showing the old
state until its refresh button is tapped. The board's household ViewModel
and the shell's copy are separate; B-20's fix refreshes the shell only when
the change was made from My chores' own sheet.

**Fixed:** My chores reloads whenever the tab is opened, and the household
view behind a sheet opened from it reloads when it is reused for another turn
— otherwise it described the household as it was the first time. The same
change gave My chores the board's **Undo** on *"… done."*, both for its own
row action and for completions made in a sheet opened from it.

### B-24 · Edit chore asks "Discard changes?" when nothing was changed — DONE (2026-11-01)

Opening a chore's edit flow, moving to step 2 and closing it without editing
anything still asks to discard changes. Seen once, while re-shooting; not yet
narrowed down.

**Fixed:** the X still always asks (the reason is on `CreateChoreFlow`), but
when the draft equals what it opened with it asks *"Stop editing? Nothing has
been changed."* with **Close**. A real edit still gets *"Discard changes?"*.

### B-20 · Tapping a turn in My chores opened nothing — DONE (2026-11-01)

**Seen:** on the phone, a tap on any row in My chores did nothing. The touch
arrived; no sheet, no request.

**Cause:** My chores opens the board's sheet with a household ViewModel made
on the spot, whose list is empty for its first frame. `OccurrenceDetailHost`
closes the sheet when its turn is missing from the list — meant for an undo
that deletes the turn under it — and read "not loaded yet" as "gone". The
sheet closed before it drew; when the data arrived a moment later, My chores
saw nothing open and unmounted it.

**Fixed:** the host only closes for a turn it has already seen. Also, the
sheet's result (*"bathroom done. You're next."*) was set on that household
ViewModel, which nothing in My chores listened to; it is now passed to the
shell's snackbar, and My chores waits for the request to answer before it
unmounts the sheet. Checked on the phone: open, mark done, message, undo.

### B-19 · "Resume after the coverer" is only fair when the coverer sits just after — DONE (2026-11-01)

**Seen:** `home`'s bathroom, rotation dd → mm → aa → bb. mm covered aa's
turn, aa repaid it, and the next turn went straight back to aa (*"bathroom
done. You're next."*).

**Why:** after a repayment the rotation resumes *after the coverer*. Every
worked example (nextTurn's ann/bo/cass, the spec's 1.4) has the coverer
immediately after the person they covered, where that is exactly a one-cycle
swap. Anywhere else it is not. Counted against an unbroken rotation:

| Case | Today | Unbroken would give |
|---|---|---|
| dd/mm/aa/bb, mm covers aa | aa, **aa**, bb, dd, mm — aa +1, dd −1 | aa, bb, dd, *(mm's slot spent)*, aa |
| A/B/C/D, C covers A | A, **D**, A … — B loses a turn | A, B, *(C's slot spent)*, D |
| ann/bo/cass, bo covers ann | ann, cass — fair | same |
| A/B (spec 1.4) | A, A — fair | same |

**A rule that fits all four:** after a cover, the order carries on *after the
person who owed it*, and the coverer's own slot is skipped **once**, when it
comes round — the cover was their turn. When the coverer is next anyway (the
documented cases) this is today's behaviour; otherwise it stops one person
doubling and another being skipped.

**Not fixed, on purpose.** It changes what `resume_after` means ("skip once"
rather than "start after"), which the client also reads to predict a busy
pass's receiver (`nextInRotationAfter`), and the spec's tests are written in
the current wording. A first attempt — "whoever just did it is never next" —
was wrong: it breaks 1.4, where two in a row is the fair answer. This wants a
decision on the rule before code; the table above is the test list.

**Decided and fixed (same day, Mehrdad's call): "skip the coverer once".**
A cover now leaves a debt for whoever owed the turn *and* a **credit** for
whoever covered (`occurrences.skip_credits`, carried forward like
`pending_debts`). After the debt is repaid the order carries on after the
person who owed it, and the coverer's next slot is skipped, spending the
credit. Details that make it hold up:

- **No credit for settling your own debt** — doing a chore you passed, or one
  you were already owed on, pays that off instead.
- **No credit outside the rotation**, and credits for someone who has left it
  are dropped (3.1, 3.2).
- **Away does not spend a credit.** Being away is not a turn taken.
- **A pass goes to the next person not already owed a skip**, which is what
  keeps 1.3's B, C, B; asking does not spend the credit.
- **`pass_to` is computed by the server** and sent on the holder's own rows.
  The client's copy of the rule (`nextInRotationAfter`) is gone: it would
  have needed the credits, which are not sent to anyone.
- **Old rows** still holding `resume_after` are read as a credit for that
  coverer; the column is no longer written.

Every spec case (1.1–3.7, the all-busy cases) passes unchanged; new tests pin
the two cases above (`TestNextTurn`, `TestTurnRule_B19_…`) and `pass_to`. On
the real `home` data, aa repaying mm's cover now gives *"bathroom done. bb is
next."* The spec's wording ("continues after the doer") is left as the record
of what was asked; `FEATURES.md` and `CLAUDE.md` state the rule as built.

### B-17 · Two "done" at once spawned two next turns — DONE (2026-11-01)

**Seen:** the `home` household's *Trash* had three open turns at once (mm, mm,
bb). The chain shows `c9b8b03b` with two children: it was completed twice, and
each completion spawned a next turn. From then on two chains ran side by side,
and every completion in either one spawned another.

**Cause:** `UpdateOccurrence` checked `status` before opening its transaction
and then updated by id alone. Two requests a moment apart — a double tap, or
the swipe and the sheet together — both passed the check. A test firing eight
at once had five succeed and left five open turns.

**Fixed:** the update is `WHERE id = ? AND status = 'open'`, and anything but
one row affected is a 409. `TestCompletingTheSameTurnTwiceAtOnceSpawnsOneNextTurn`.

**Not fixed by this:** turns already duplicated stay duplicated — completing
them only continues both chains. They need deleting by hand. The very first
duplicate (`c9b8b03b`, created with no parent on 2026-09-14 20:08) is not
explained by this race and could not be reproduced: nothing in the current code
creates a parentless turn for an existing chore.

### B-18 · The detail sheet showed completion times in UTC — DONE (2026-11-01)

*Done* read *19:22* for something done at 20:22 in Rome. `formatStamp`
formatted the server's UTC value as-is; it now converts to the phone's zone,
as `formatDueDate` beside it already did.

### B-16 · The activity feed still prints raw event types for every v2 event — DONE (2026-09-02)

**Fixed:** `describeEvent` has the five missing cases, so the feed now reads
"maya did a chore / Clean the bathroom" and "demo added a chore" instead of
"maya: occurrence_done". `chore_updated` needed nothing extra — the backend
already writes the diff in words, and `humaniseDetail` passes it through, so
F4's broadcast now shows as "maya changed a chore / Water the plants: schedule:
every 3 days → every 4 days". That sentence is the spec's own example and this
is the first time it was legible in the app.

`eventColor` gained matching entries: chore creation shares the task-creation
primary, deletion the destructive red, and a completion takes the same tertiary
green a done row has. Not a reward — the feed is a record, and it is the one
entry that says something finished.

The fallback no longer prints the raw identifier: an unknown event now reads
"$user made a change". That trades a little debuggability for not leaking
constants into the household's feed; the comment above the function says to add
the case rather than lean on it.

Verified on the emulator: every row in the seeded feed reads as English, and
editing a chore through the API produced the diff line above.

Original report follows.

### B-16 · The activity feed still prints raw event types — was OPEN

**Found** 2026-09-02, while shooting the report screenshots — it was in the
frame, which is how it got noticed.

`describeEvent` (`GroupDetailScreen.kt` ~1457) humanises the v1 events and has
no case for any of the ones F2–F6 added, so they fall through to
`"${actorUsername}: ${eventType}"`. The feed reads:

```
maya: occurrence_done
demo: chore_created
demo: chore_updated
```

The v1 half of the same feed says "demo added a task". This is the defect the
backlog already closed once — "Activity feed leaks developer strings", round 1
— reopened by the pivot, because the chore events were never added to the map.

Missing cases, from `handlers/activity.go`: `chore_created`, `chore_updated`,
`chore_deleted`, `occurrence_done`, `occurrence_reopened`.

**Fix:** five lines in `describeEvent`, plus a look at `humaniseDetail` for the
second line — `chore_updated` carries the edit diff the backend already phrases
("weekly → every 3 days"), which is the good half of F4 and currently renders
unlabelled underneath a debug string.

Worth doing before the report: the activity feed is the app's most visible
shared surface and it is the one screen deliberately left out of the deck
because of this.

---

### B-13 · A fixed-date chore done early came back due the same day — DONE (2026-09-02)

**Reported by Babak**, 2026-09-02: a repeating chore completed before its due
date "stays on the same date" instead of following its pattern.

**Confirmed, and it was real on the fixed-date path.** A "Fridays" chore due
Fri 4 Sep, completed on Wed 2 Sep, spawned its next occurrence **also due Fri 4
Sep**. Put the bins out early and the chore reappears for the same collection,
so it has to be done twice — and a household that is habitually early never
advances the schedule at all.

**Cause:** `nextDueDate` passed the *completion time* to `nextFixedDate`, which
returns the soonest matching weekday strictly after it. Completing on Wednesday
made "the soonest Friday after Wednesday" the very Friday just dealt with.

**Fix:** the fixed-date scan now starts from the later of the completion and
the completed occurrence's own due date — the calendar slot just satisfied.
Both ends matter and neither anchor works alone: anchoring only to the due date
would put a chore finally done three weeks late onto a Friday that has already
gone. The due date is converted into the completion's location first, because
times come back from SQLite in UTC (the DSN sets no `_loc`) and a weekday read
in the wrong zone is a weekday off by one either side of midnight.

Two regression tests, `TestFixedDateDoneEarlyMovesToTheNextSlot` and
`TestFixedDateDoneLateLandsOnAFutureSlot`. The first was checked against the
unfixed code and fails there with exactly the reported symptom. Verified
end-to-end too: same chore, same early completion, now spawns Fri **11** Sep.

**The interval path was not changed** — see B-14, which is a question rather
than a defect.

---

### B-14 · Should an interval chore done early pull its schedule earlier? — DECIDED: no change

**Raised** alongside B-13, from the same report. Not a bug against the spec, so
it was left alone rather than changed quietly.

An interval chore's next date is counted from the **completion**:
`spec_2` line 43 — *"next occurrence is due N days after the last completion"* —
and the create dialog says so out loud ("Counted from the last time it was
done"). So an every-3-days chore done on Wednesday is next due Saturday, and
doing it early pulls the whole schedule earlier, exactly as doing it late
pushes it later.

What makes it *look* broken is the first cycle: a new chore's first occurrence
is due `created + N`, so creating an every-3-days chore and ticking it off the
same day produces a next date of `created + 3` — the same date the completed
one had. Nothing moved, apparently. It is a coincidence of completing exactly N
days early, not a stuck schedule.

**The question:** is completion-anchoring right for early completions, or
should the rhythm hold (done early → still due on the original date + N)? The
spec only ever justifies the rule with a *late* example. Completion-anchoring
is defensible — the bathroom is dirty three days after it was cleaned, not
three days after it was meant to be — but a household that tidies up two days
early every time will find "every 3 days" quietly drifting into every other
day. Changing it means changing the spec's stated rule and the copy in the
create dialog, so it needs Babak's call.

**Answered 2026-09-03 (Babak): keep the current behaviour.** The next date stays
`completion + N`, and nothing was changed.

Three reasons worth having in the report, because a reader may well ask:

- It is what `spec_2` line 43 says, and what the create screen tells the person
  out loud: "Counted from when it was last done, not from the calendar."
- The asymmetry with fixed-date chores is **meaningful rather than an
  inconsistency**. Since B-13 a fixed-date chore done early holds its pattern,
  because "Thursdays" is a promise about the calendar. "Every 3 days" is not:
  it is a measurement from the work.
- The known cost is a ratchet. Someone habitually a day early pulls the chore
  permanently earlier, so an every-3-days chore can drift towards every-2-days.
  Accepted, not overlooked: the household can edit the interval, and a chore
  being done sooner than required is not the failure mode this app is for.

---

### B-15 · Chore-history ordering has no tiebreaker, and the test is flaky — DONE (2026-09-02)

**Fixed:** `ORDER BY o.done_at DESC, o.rowid DESC`.

The first attempt used `created_at` as the second key and the test still failed
about one run in four — worth recording, because the obvious fix is the wrong
one. `created_at` defaults to `CURRENT_TIMESTAMP`, which is whole seconds, so
it ties in exactly the cases `done_at` does. And `done_at` ties more readily
than it looks: it comes from `time.Now()`, whose resolution on Windows is coarse
enough that two writes a few milliseconds apart get the identical value.

`rowid` is insertion order and always distinct, and for a chore's cycles that
is spawn order — the later cycle cannot have been inserted first. The table has
no `WITHOUT ROWID`, so it is there to use.

Verified by hammering: ten consecutive runs of the previously flaky test, zero
failures, then three full-suite runs, zero failures.

Original report follows.

### B-15 · Chore-history ordering has no tiebreaker — was OPEN

**Found** 2026-09-02 while running the full suite for B-13.
`TestChoreHistoryRecordsWhoActuallyDidIt` failed once in four runs and passes in
isolation, so it is timing, not the change under test.

`ChoreHistory` orders by `o.done_at DESC` alone (`history.go:70`). Two
completions of the same chore close enough together share a stored `done_at`,
and SQLite is then free to return them in either order — which is what the test
trips over, and what a real household would see as a timeline that shuffles
between refreshes.

**Fix:** add a deterministic tiebreaker, e.g. `ORDER BY o.done_at DESC,
o.created_at DESC`. One line, no change to what the history means — it ranks
nothing either way.

---

### B-12 · A task can still be un-assigned from the detail sheet — DONE (2026-11-01)

**Reported:** 2026-09-01, noticed while merging the create dialogs (phase 2).

The merged create form has no "Nobody" option, because constraint 8 makes
assignment unconditional — busy and away are the only exits from an assigned
chore, and an unassigned one is the arrangement the spec exists to replace.
`TaskDetailSheet` still offers a **Nobody** chip under "Assigned to", so a task
that cannot be created unowned can still be made unowned a moment later, and it
then sits in the board's *Others* section belonging to no one.

Small, and left alone deliberately: phase 2 was a merge of two dialogs and
changing the detail sheet is a different concern. **Fix:** drop the chip, and
decide what the sheet should do with the legacy tasks that already have a null
`assigned_to` (show them as unassigned, but don't offer it as a choice).

**Fixed:** the *Nobody* chip is gone. A legacy task that already has nobody
shows *Unassigned* on the board and no chip selected in its sheet, until
somebody takes it.

---

### B-10 · The board still wears a completion percentage — DONE (2026-09-02)

**Fixed:** `ProgressHeader` and its one call site are gone. The board now goes
straight from the app bar to the tabs.

Removed ahead of retaking the report screenshots, so the deck does not preserve
a constraint 4 violation in the images the report is graded on — it sat at the
top of nearly every board, dialog and away-banner shot.

`GroupWithProgress` still carries `total_tasks`/`done_tasks`/`progress` and the
API still returns them; nothing reads them now. The endpoint is shared and the
fields cost nothing, so they stay rather than becoming a backend change.

Original report follows.

### B-10 · The board still wears a completion percentage — was OPEN

**Reported:** 2026-09-01, while device-verifying phase 1 of the UI cleanup. Not
in that prompt's scope; raised rather than improvised on.

`ProgressHeader` (`GroupDetailScreen.kt` ~647, called at ~334) sits above the
tabs and renders **"0 of 3 done"**, **"0%"** and a filling bar, from
`GroupWithProgress.progress`. It is v1 furniture that survived the pivot.

The spec's constraint 4 forbids "points, streaks, leaderboards, or
gamification", and the Dynamics note under history is explicit: *"No points,
ranks, streaks, percentages, or comparisons drawn by the app."* A household
completion percentage is a conclusion the app is drawing — and because it
counts the whole group's rows, a low number is a statement about the
household's week, which is the shame signal constraints 3 and 4 exist to keep
out. It also only counts `tasks`, not occurrences, so the number it shows is
wrong as well as unwanted.

**Fix:** delete `ProgressHeader` and its call. Nothing else reads
`progress`/`doneTasks`; the API can keep returning them. Sits naturally with
the phase-3 information-architecture commits, including B-11 — needs Babak's
go-ahead first.

---

### B-11 · Board tab count includes done rows — DONE (2026-09-02)

**Reported:** 2026-09-01. **Fixed** in UI cleanup phase 3d.

`Board (${tasks.size + occurrences.size})` counted every row including the done
ones, so finishing a chore never moved the number — the one moment the count
should visibly change was the one moment it didn't. It now counts open rows of
both shapes. Verified on the emulator: six open rows read "Board (6)"; ticking
one off left "Board (5)" with one row under Done.

---

### B-9 · Red overdue survived on the legacy task path — DONE (2026-09-01)

**Reported and fixed** during the UI cleanup pass, phase 1.

F1 made overdue amber on the board and left a comment beside `overdueColor()`
saying `colorScheme.error` must not appear anywhere overdue is rendered. Two
places on the legacy `Task` path still contradicted it — the board had been
fixed, the screens either side of it had not:

- `GroupDetailScreen.kt` ~1315, `TaskDetailSheet`: the deadline line rendered
  `MaterialTheme.colorScheme.error` when the task was past due, so opening an
  overdue task turned an amber row into a red one. Now `overdueColor()`, the
  same amber the board and the calendar use — there is one amber, not two.
- `GroupDetailScreen.kt` ~1416, `CalendarTab`: the *doc comment* still said the
  dots are "overdue in error". The dot code below it had already been moved to
  `overdueColor()`, so this was a stale description rather than a live
  violation — but it is the comment a later change would have followed.

Verified on the emulator against a local backend: a one-off task due three days
ago shows amber on the board, amber on its detail sheet, and an amber dot on
the calendar's 29 August. `Delete task` and "Title is required" are still red —
the constraint is about lateness, not failures.

The rest of `ui/` was grepped for `colorScheme.error`: every other use is an
error message, a destructive control, or the Dashboard's avatar palette. None
key off a due date.

---

### B-8 · Reminders only cover the group whose board you last opened — DONE (2026-09-01)

**Fixed:** the Dashboard now loads occurrences and chores for every group and
arms reminders for all of them. It is the one screen that sees them all.

The part that needed care: `reschedule` replaced the *whole* stored set, which
was fine while only the board called it. With two callers, whichever ran last
would have cancelled the other's alarms — a worse bug than the one being fixed,
and a silent one. Reminders are now stored and replaced **per group**, so the
board (one group) and the Dashboard (all of them) can both call in.

Failures while loading are deliberately silent: this drives reminders, not the
screen, and a group whose board could not be fetched keeps whatever alarms it
already had rather than raising a banner over a group list that loaded fine.

Verified with demo in two households: the Dashboard armed 2 reminders for one
and 6 for the other, "Your turn: Water the plants" fired for the group whose
board had never been opened, and opening one board left the other group's six
untouched.

Original report follows.

**Reported:** 2026-09-01, while building F3.

Reminders are scheduled on the device from the board's data, and the board is
per-group. `GroupDetailScreen` is the only thing that calls
`ReminderScheduler.reschedule`, so a user in two households gets reminders for
whichever board they opened most recently, and the other group's turns pass
unmentioned.

**Not a problem for the common case.** The spec's household is one group, and a
user in exactly one group lands straight on its board at login — so their
reminders are always current. This only bites the multi-group user.

**Fix:** schedule from somewhere that sees every group. The Dashboard already
lists them, so it could fetch occurrences per group and hand the union to the
scheduler — N requests where N is the number of households, which is 1 or 2 in
practice. The scheduler itself needs no change: it is keyed by occurrence and
replaces its whole set each time, so feeding it more occurrences is enough.

---

### B-7 · A rejected create closes the dialog and loses what you typed — DONE (2026-09-01)

**Fixed:** both create dialogs now stay open until the write actually lands.
`runAction` takes an optional result callback; the screen closes the dialog on
success and, on failure, shows the message in the dialog's own error slot with
every field still filled. Create reads "Creating…" and is disabled while the
request is in flight, and Cancel is disabled with it so the form cannot be
dismissed out from under a pending write. A failure routed to a form no longer
also raises a snackbar — one message, where the user is looking.

Verified by filling the chore form, killing the backend, and pressing Create:
the dialog stayed up with "Network error: Failed to connect to /10.0.2.2:8080"
and the name, schedule and rotation all intact. Restarting the server and
pressing Create again submitted the same form successfully.

Original report follows.

**Reported:** 2026-08-31, hit while testing chore creation against a stale
server binary.

Both `CreateTaskDialog` and `CreateChoreDialog` set `showCreate… = false` and
*then* fire the request, so a 400 from the backend surfaces as a snackbar over
an empty board — the dialog is already gone, and every field the user filled in
goes with it. On the chore form that is a name, a done-line, a schedule and a
rotation order.

Client-side validation now catches the common cases before the request (so this
is rarer than it was), but anything only the server can know — a name that
collides, a member who left the group mid-edit, a dropped connection — still
loses the whole form.

**Fix:** keep the dialog open until the call succeeds. That means the dialog
needs to see `isWorking`/`message` rather than being dismissed optimistically:
disable Create while in flight, close on success, show the server's message
in-dialog on failure. The in-dialog error slot already exists.

---

### B-6 · The undo window doesn't close on its own — DONE (2026-09-01)

**Fixed:** each undoable row now waits out its own window in a `LaunchedEffect`
that sleeps exactly until the moment it lapses, then closes it. No clock read
during composition, so nothing can go stale, and no polling either — one
coroutine per undoable row, and only while there is one.

Verified by temporarily shortening the window to 20 seconds, ticking a row, and
watching the checkbox go from enabled to disabled with no taps, no scrolling and
no refresh. The original 10 minutes was restored afterwards.

Original report follows.

**Reported:** 2026-08-31, while device-verifying F1.

`canUndo` in `GroupDetailScreen.kt` (~line 600) calls `OffsetDateTime.now()`
during composition, with nothing to trigger a recomposition when the ten
minutes elapse. The checkbox therefore stays enabled past the window until
something else recomposes the row — a poll, a refresh, or leaving and coming
back.

**Not a permissions hole.** The `doneBy == myUserId` half is correct, and since
F2 the *server* enforces both halves (403 "the undo window has passed"), so a
stale screen produces a clear error rather than a silent rewrite. What's wrong
is only that the control looks available when it isn't.

**Fix:** drive it from a ticker rather than from composition — a `produceState`
that re-evaluates once a minute while a done row is on screen, or recompute
against a clock the ViewModel already updates. Cheap either way; it was left
alone so F1's verification pass didn't turn into a refactor.

---

### B-1 · Invite list can't scroll past ~6 entries — DONE (round 1)

**Reported:** 2026-08-01

With more than about six pending invites, the older ones are unreachable — the
dialog clips them and there's no way to scroll.

**Cause:** `PendingInvitesDialog` in
`TaskFlowApp-kotlin/…/ui/screens/DashboardScreen.kt` (~line 466) lays the
invites out in a plain `Column` with `invites.forEach`. `AlertDialog` constrains
its content height, so anything past the visible area is simply cut off. Nothing
in that Column scrolls.

**Fixed:** content now has `heightIn(max = 400.dp)` + `verticalScroll`.
Original plan: give the content `Modifier.verticalScroll(rememberScrollState())`, or
swap the `Column` for a `LazyColumn` with a bounded `heightIn(max = …)`. Lazy is
the better choice if the list can get long, since it also avoids composing every
row. Worth capping the dialog height explicitly either way so it doesn't grow to
fill the screen on a phone.

**Note:** the same shape appears elsewhere — check the group detail dialogs for
the same clipping before calling this closed.

---

### B-2 · New invites only appear after a manual refresh — DONE (round 3)

**Reported:** 2026-08-01

If someone invites you while you're sat on the Dashboard, nothing happens. You
have to hit refresh to discover it.

**Cause:** `DashboardViewModel` only calls `fetchInvites()` from `init`,
`refresh()`, and after responding to an invite. There's no polling. Compare
`GroupDetailViewModel`, which polls the activity feed every 5s — the Dashboard
never got the same treatment.

**Fixed:** `PollWhileResumed` ticks `fetchInvites()` every 20s while the
Dashboard is resumed, and immediately on resume. Built on `repeatOnLifecycle`,
so it also stops polling while the app is backgrounded — which fixes the
separate complaint about the activity feed below. The FCM point still stands
if a third poller ever appears.

Original plan: poll `fetchInvites()` on an interval from a `LaunchedEffect` in
`DashboardScreen`, the same way `GroupDetailScreen` polls activity. Points to
settle:

- A slower interval than the activity feed is fine — invites are rare. 15–30s.
- Also refresh on resume, not just on a timer, so coming back to the app shows
  the truth immediately.
- Polling on every screen is the wrong end state. If this list grows, the real
  answer is one push channel (FCM) rather than N pollers — the backend already
  records the events that would drive it. Worth raising before adding a third
  poller.

---

## Missing features

### F-2 · No way to delete a chore from the app — DONE (2026-09-02)

**Fixed:** "Delete chore" on `EditChoreDialog`, behind a confirm.

It went there rather than on the occurrence sheet because that dialog is
already the "this chore is wrong" screen; on a single occurrence the same
button reads as deleting *that cycle* rather than the whole rotation.

The confirm says what is lost instead of asking "are you sure?": *"This removes
the chore, whoever's turn it currently is, and the history of every time it has
been done. It cannot be undone."* There is no undelete endpoint, and a snackbar
pretending otherwise would be a lie — the same reasoning that governs task
delete.

**Left open to every member**, consistent with everything else about chores
since the manager role went (F2/F4). It is the most destructive thing in the
app, so it is worth naming in the report as a deliberate choice rather than an
oversight.

Nothing behind it needed writing: the endpoint, `deleteChore` on the API
service and `deleteChore` on the ViewModel had all existed since F2 and had
simply never been reachable.

Verified on the emulator: `DELETE /groups/{id}/chores/{id}` → 204, and the
chore and its open occurrence both left the board.

Original report follows.

### F-2 · No way to delete a chore from the app — was OPEN

**Asked by Babak**, 2026-09-02: "why is there no option to delete a chore?"

Because the UI was never wired up. Everything else exists:

- `DELETE /groups/{group_id}/chores/{chore_id}` is routed
  (`cmd/server/main.go:93`) and works — verified against the local backend,
  returns 204 and takes the chore's occurrences with it.
- `GroupApiService.deleteChore` exists (`GroupApiService.kt:154`).
- `GroupDetailViewModel.deleteChore` exists (`GroupDetailViewModel.kt:553`),
  with a "Chore deleted" message ready.

Nothing calls the ViewModel method. It has been unreachable since F2. Tasks got
a delete button on `TaskDetailSheet` back in v1 and chores never got the
equivalent, so the one-off shape is deletable and the chore shape is not.

**Fix:** a "Delete chore" action on `EditChoreDialog` — that is already the
"this chore is wrong" screen and is open to every member, so delete belongs
beside edit rather than on the occurrence sheet, where it would read as
deleting *this cycle*. Behind a confirm, like task delete: deleting a chore
removes its whole history, which is not something to do on one tap.

Worth settling at the same time: whether a member may delete a chore anyone
created. Everything else about chores is open to every member by design
(F2/F4), so the consistent answer is yes — but it is the most destructive thing
in the app, and it is the first place that question really bites since the
manager role was removed.

---

### F-1 · No group settings; description can't be edited after creation — OPEN

**Reported:** 2026-08-01

If you skip the description when creating a group, there's no way to add one
later. No group settings screen exists at all.

**Needs backend work first** — there is currently *no* update route for a group.
`cmd/server/main.go` has `GET /groups/{group_id}` and the members/tasks/invite
routes, but nothing to modify the group itself.

**Backend:** add `PATCH /groups/{group_id}` accepting `{"name"?, "description"?}`,
membership-guarded like its neighbours. Decisions to make:

- Reuse the `NullableField` tri-state so a description can be *cleared* as well
  as set, consistent with how task fields behave.
- Should any member be able to rename the group, or only the owner? Role-based
  permissions were explicitly deferred pending the need-finding interviews, so
  the simplest consistent choice for now is any member — but flag it, because
  it's the first place that question really bites.
- Record a `group_updated` activity event, with the changed fields in `detail`,
  so it shows in the feed like every other change.

**Android:** a settings entry in the group detail overflow menu → dialog with
name and description, calling the new endpoint. `Group`/`GroupWithProgress`
models already carry `description`, so nothing to add there.

---

## UX review findings (2026-08-01, from driving every screen)

### Functional bugs found during review

- **B-3 · DONE (round 1) · Task could only be assigned to the first 3 members.** `CreateTaskDialog`
  in `GroupDetailScreen.kt` (~line 745) does `members.take(3)` for the assignee
  chips. Group of 5 → two people can never be assigned. Needs a scrollable/flow
  layout or a dropdown instead of a capped chip row.
- **B-4 · DONE (round 1) · System back didn't exit selection mode.** No `BackHandler` in
  `GroupDetailScreen`; in multi-select, back exits the whole screen instead of
  clearing the selection. Add `BackHandler(enabled = inSelectionMode)`.
- **B-5 · DONE (round 1) · Keyboard covered the primary button on auth screens.** Observed while
  driving Register: the IME hid Create Account with no way to scroll to it.
  Auth columns need `verticalScroll` + `imePadding()`.

### UX debt (no crash, but hurts flow)

- **DONE (round 2)** ~~Tap-to-cycle status is undiscoverable and error-prone.~~
  A tap now opens the task detail sheet; status is an explicit chip row there.
- **DONE (round 1)** ~~Destructive actions are one tap, no confirm, no undo.~~
  Task delete now confirms. (Undo would need an undelete endpoint — deliberately
  not faked with a snackbar.)  Original note: Task delete
  especially — the trash icon sits beside every card. Snackbar-with-Undo is the
  Material pattern; confirmation dialog is the cheap stopgap.
- **DONE (round 2)** ~~No task detail/edit screen.~~ `TaskDetailSheet` gives
  editable title/description (staged, saved with a button), explicit status and
  assignee, created/updated stamps, and delete. It is also where the due-date
  picker belongs when that work starts.
- **DONE (round 1)** ~~Activity feed leaks developer strings.~~ Original note: `detail` renders raw
  `assigned_to=cleared`, `status=in_progress`, `2 task(s) → done`. Humanise
  client-side ("cleared the assignee", "moved 2 tasks to Done").
- **DONE (round 1)** ~~Silent success.~~ Mutations now confirm via snackbar. Original note: Most mutations show nothing on success (only failures
  surface). Bulk moves, status changes, task creation should confirm via
  snackbar — which is also where Undo lives.
- **DONE (round 3)** ~~Session expiry is unexplained.~~ Login now shows
  "Your session expired. Please sign in again." Was: On a 401 the app bounces to Login with no
  message; user can't tell logout from crash. Show "session expired, sign in
  again."
- **DONE (round 1)** ~~Invite code can't be copied or shared.~~ Original note: `InviteCodeDialog` shows the code
  as plain text; user must transcribe it. Add copy-to-clipboard + system share
  sheet.
- **DONE (round 3)** ~~No pull-to-refresh anywhere.~~ `PullToRefreshBox` on the
  Dashboard and the group tabs; toolbar icons kept for discoverability.
  Required bumping the Compose BOM to 2024.09.00 (Material 3 1.3.0), since
  the pull-to-refresh API did not exist in 1.2.1.
- **DONE (round 1)** ~~FAB overlaps the last list item~~ — task/group lists need bottom content
  padding (~88dp).
- **DONE (round 1)** ~~Members tab reuses StatusChip for roles~~ — now a RoleChip.
  Was: so "owner" renders raw and
  lowercase.
- **DONE (round 3)** ~~No sorting/grouping of tasks.~~ The task list is grouped
  under To do / In progress / Done headers with counts, so finished work sinks
  to the bottom. Filtering is still not implemented.
- **Login lacks show-password toggle; Login "success" card is dead weight**
  (navigation happens immediately, the message never gets read).
- **Logout is instant** from an icon that sits next to Refresh — one mis-tap
  signs you out. Confirm, or move it behind the overflow menu (Dashboard now
  has one).

## Deadlines, roles and calendar (2026-08-02) — DONE

Built on request, ahead of the need-finding interviews that were originally
meant to inform role design. Flagged at the time; the decision was to proceed.

- `group_members.role` (owner/admin/member) is now actually enforced. It was in
  the schema from the start but `GetMemberRole` had never been called once.
- Only owner/manager may set or clear a task's `due_date`; everything else stays
  open to all members. The check keys off whether `due_date` is *present* in the
  patch, so a member editing a title is unaffected.
- `PATCH /groups/{id}/members/{user_id}/role`, owner only. The owner's own role
  cannot be changed, by anyone.
- Calendar is a per-group tab: month grid, a dot per task (error = overdue,
  tertiary = done), tap a day for its tasks. Tasks with no deadline are counted
  at the bottom rather than silently omitted.
- "admin" on the wire is shown as **Manager** throughout the UI.

Open questions deliberately left:

- Should the *assignee* be able to set their own deadline? Currently no.
- Nothing else is role-gated (delete, invite, group settings). Revisit after the
  interviews rather than guessing.
- The calendar is per-group. A cross-group "my deadlines" view was considered
  and deferred — it needs an aggregate endpoint.

## Also known (raised in passing, not yet triaged)

- **DONE** ~~No due-date UI.~~ Deadlines are set from the task detail sheet by
  the owner or a manager, shown on a month calendar tab, and cleared with
  `Patchable.SetNull`.
- **Reinstalling the APK signs you out.** `EncryptedSharedPreferences` loses its
  Keystore-backed key across reinstalls, so the JWT is unreadable and the app
  starts at Login. Expected behaviour rather than a defect, but it surprises
  testers — worth a line in whatever instructions go out with the APK.
- **DONE (round 3)** ~~Activity polling keeps running when backgrounded.~~ Both
  pollers now go through `PollWhileResumed`, which uses `repeatOnLifecycle` to
  pause on background and tick on resume.
- **Debug builds log request bodies.** `HttpLoggingInterceptor` is at `BODY` in
  debug, which includes the plaintext password on login/register. `Authorization`
  is redacted and release builds log nothing, but don't share a debug logcat.
