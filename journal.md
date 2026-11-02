# journal.md — TaskFlow working log

Append-only. Newest entry at the top. One entry per working session: what was
done, what was learned, what is open. Decisions that outlive a session graduate
to `BACKLOG.md`; state that outlives a session graduates to `V2-HANDOFF.md`.

---

## 2026-11-02 — Removal, Book it up and the guide, on `household-removal-and-tutorial`

For after the interviews, which run on `main` unchanged. Popups and buttons
audited at 360dp and 150% font, and every popup now scrolls. Owners can remove a
household, and housemates are told by their own phones because the feed goes
with it. Book it up added at the project owner's request after participants
complained of clashing over appliances, as a notice rather than a lock, which
narrows constraint 5. How TaskFlow works added to You, with a one-time card on
Mine.

All driven on the emulator against a throwaway local server with two accounts.
That caught what the tests could not: `TimeInput` refusing digits after a tap,
so booking uses the clock face. Checked at 360dp and 150% font and in dark mode.
Nothing here is merged or deployed. `BACKLOG.md` has the detail and the limits.

---

## 2026-11-01 (evening) — Quiet House, on `mehrdad-design`

Designed the app from scratch on a canvas, then built it on a new branch with
the brand violet kept as the accent. Six commits: theme and fonts; the dock
with Mine, House, Feed, People and You; the chore sheet; Add a chore on one
screen; the auth screens. Checked on the phone (light and dark) and the
emulator (auth). `BACKLOG.md` lists what changed and what the canvas asked
for that was not built.

## 2026-11-01 — Review round: done-line onto the button, and a plan

Two pieces of outside feedback came in: three interview-driven points from a
colleague, and a six-screen redesign concept from someone else.

- **Done-line above the button — done.** WHAT DONE MEANS moved from under the
  sheet's header to directly on top of *I did this*. Swipe-right already quoted
  it. `BACKLOG.md` records it as the fourth departure from the Stitch mock.
- **Completion push — declined.** The Activity feed already carries
  `occurrence_done`; the rules a push would need are recorded in `BACKLOG.md`.
- **"I worked on this too" — done.** Backend table + two endpoints + five
  tests (credit and next turn unchanged, refusals, withdraw window, undo
  clears it, history shows it but the count does not). Android: button and
  *Also worked on it* row in the sheet, *"Done by maya, also sara"* on the row
  and in history, two feed phrasings. The three time windows in
  `OccurrenceDetailHost` now share one `openUntil` helper instead of one
  hand-written coroutine each. Checked end to end against a throwaway database.
- **Fixed on the way:** two "done" at once spawned two next turns (B-17), which
  is how Trash had three open turns; detail-sheet times were UTC (B-18). The
  test household's stale Trash turns were deleted and a pasted login removed
  from its done-line, with the owner's go-ahead.
- **Screen 04 — done.** Snackbar *"… done. bb is next."* + Undo, the rotation
  in the sheet, *RECENTLY DONE* (seven days, newest first). All checked on
  the phone, including Undo from the snackbar. Not taken: a forecast of who
  is next on the row, a mark-done button on the row, the counter, the live
  badge — reasons in `BACKLOG.md`.
- **Screen 05 — done.** Members rows are names with an Owner / Away badge
  only where one applies; the invite buttons and *Your availability* moved
  from the ⋮ menu into the tab. Checked on the phone, including the Away
  badge (a five-minute test absence for bb, removed after).
- **Round complete.** All four planned steps are in. Open from this session:
  the turn rule gives someone two turns in a row when the person who covered
  them sits just before them in the rotation (seen as *"You're next."* right
  after repaying a cover) — designed behaviour or not, it wants a decision
  before the interviews; and My chores never shows the board's messages, so a
  completion from there gets no snackbar.
- **Follow-up, same evening.** Pushed. The turn-rule gap is written up as
  B-19 (OPEN, with the counting table and a proposed "skip the coverer once"
  rule) — a first fix, "never twice in a row", broke the spec's own 1.4 and was
  reverted. Looking at My chores turned up a worse bug than the missing
  snackbar: tapping a turn there opened nothing at all (B-20). Fixed, with the
  snackbar.
- **Deck re-shot.** 36 screens, eleven slides, from a committed seed
  (`scripts/seed_demo.py`). Shooting found two more bugs, both fixed — Dark
  appearance on a light phone hid the status bar (B-21), My chores said "Done
  by someone" (B-22) — and two left open (B-23, B-24). `INTERVIEWS.md` keeps
  the old file names on purpose: it records what participants were shown, and
  now says how to restore that set from git.
- **Turn rule changed (B-19).** Mehrdad chose "skip the coverer once" over
  "resume after the coverer". A cover now leaves a debt and a credit
  (`skip_credits`); the order carries on after whoever owed it, and the
  coverer's own next slot is skipped. All spec cases still pass; the server
  now sends `pass_to` so the phone no longer carries a copy of the rule.
  Verified on the real `home` bathroom: aa repaid mm's cover, bb is next.
- **Small fixes, all checked on the phone:** My chores refreshes on opening
  and after the board changes something (B-23); it has the board's Undo on
  "… done."; Edit chore no longer claims edits that were never made (B-24);
  the *Nobody* assignee is gone from legacy tasks (B-12). Pushed for a
  colleague to test; redeploying the backend is theirs to decide.
- **Not taken from the concept:** Google / emailed-link sign-in (01), the group
  list as home (03, contradicts 2026-09-13), group settings with archive /
  delete / transfer (06), the setup checklist (02).

## 2026-09-15 — The row stops deciding; the sheet decides

Yesterday's confirm was a control bolted to a row. Today the control is gone.

- **No checkbox on `TaskCard`.** The cell is a button and nothing else. A done
  row shows a green tick in a 26dp disc where the box was, plus the dimmed title
  it already had. New `doneColor()` — `#0F7B5A` light, theme `tertiary` dark —
  built like `overdueColor()`, and for the same reason: a 16dp glyph in
  `#34D399` on white is not legible.
- **`OccurrenceDetailSheet` rebuilt from the Stitch mock** *Chore detail — Clean
  the bathroom* (screen `1f57d1564abb495b9006f5cefe8e1b48`, project **Core
  Design System** `4515006561005969934`). Header + household chip, violet
  callout for the one thing that needs explaining, WHAT DONE MEANS card, a quiet
  list card (Whose turn · How often · Due · Needed by, plus Done by / Done once
  closed), History / Edit chore, then the pill **I did this** and outlined
  **Busy — pass it on** with its note. The callout is `primary` at 10% and the
  cards are `surfaceVariant`: no `…Container` role is touched.
- **Undo is a button now**, in the same footer, and the ten-minute window is
  waited out once in `OccurrenceDetailHost` instead of once per visible row.
  `UNDO_WINDOW` went `internal` so both files agree on the number.
- **`MarkDoneSheet` is swipe-only.** The gesture completes without opening
  anything, so it stays the one place that asks.
- **The swipe-right background was Material's baseline dark red** —
  `tertiaryContainer`, never set by `Theme.kt` — behind the word "Done". Flagged
  yesterday, fixed today: both backgrounds are our own tokens at 16% alpha.

What this trades: F1's "completion is one tap" is now two, and the board can no
longer be cleared while walking past it. That is the intended cost — the tap it
removes is the accidental one, and the screen it adds is the only one that says
what the household agreed "done" means.

Verified: `assembleDebug` and `testDebugUnitTest` clean. Not yet looked at on a
device — the sheet's scroll behaviour with a long done-line is worth a look.

---

## 2026-09-14 — The tick opens a sheet and asks what done means

The board's checkbox completed a turn on one tap. It now opens a confirm that
quotes the chore's done-line and asks.

- **New `ui/components/MarkDoneSheet.kt`** — a `ModalBottomSheet`: tinted check
  disc beside the chore name and "Mark this turn as done?", the done-line on a
  `surfaceVariant` card under "What done means", one sentence saying a tick
  records the cycle for the whole household, then a full-width *Mark it done*
  with *Not yet* beneath it. Two `@Preview`s (with and without a done-line) are
  the design surface. The disc is tinted from `primary` at 12%, not
  `primaryContainer` — the container roles here are Material's baseline, and one
  of them is a dark red (CLAUDE.md §7).
- **Started as a centred `AlertDialog`; changed to a sheet within the hour.**
  Every other thing a row asks for arrives from the bottom edge, by the thumb
  that tapped. The dialog read as the app interrupting; the sheet reads as the
  row opening up. Same content, same state, one composable swapped.
- **The tick completes nothing by itself.** It only opens the sheet, and the box
  stays unticked — no optimistic state — so backing out leaves the board
  untouched.
- **The confirm is inside `TaskCard`**, so the board, My chores and the
  swipe-right shortcut all get it from the same state. Nothing changed in either
  screen's `onToggleDone` wiring; `requestDone()` intercepts on the way in.
- **Ticking asks, unticking does not.** Undo belongs to the person who ticked,
  inside ten minutes, and they have just read the sheet.
- **One-offs get "What this is"** instead of "What done means": they carry a
  description, not an agreement.

Why it matters more than it looks: F4's done-line is the mechanism that moves
the standards argument out of each turn and into one household conversation. It
was only visible in the detail sheet — present when you argue about a chore,
absent when you finish one. Now it is on screen at the moment it is the only
question being asked.

Open: the swipe-right "Done" background still paints `tertiaryContainer`, which
is Material's baseline **dark red** in this scheme — the same trap CLAUDE.md §7
records, and a red wash behind the word "Done" is close to the signal
constraint 3 rules out. Not touched here; worth its own pass.

Verified: `./gradlew assembleDebug` and `testDebugUnitTest` both clean. Not yet
looked at on a device.

---

## 2026-09-13 (late) — The strip drives the list

My week's strip was decorative: it showed which days carried something, and the
list underneath ignored it and showed everything. Now the list answers the
strip.

- **The window is the displayed week**, or **one day** of it once a day is
  tapped. Tapping the selected day again lets go of it and gives the week back —
  without that, narrowing to a day is a one-way door. Changing week drops the
  selection: you asked to look at a week.
- **Headings are days** — TODAY, TOMORROW, YESTERDAY, or "FRIDAY 11 SEPT" —
  rather than DESIGN.md §5.11a's TODAY / TOMORROW / LATER THIS WEEK / NEXT WEEK
  AND BEYOND. Those buckets describe an unbounded list, and this one is bounded
  by the week on screen; "next week and beyond" cannot appear in a view that
  stops at Sunday. Still no LATE heading anywhere: an overdue turn sits under
  its own day with the date in amber.
- **NO DATE** (as-needed) shows on the current week only, and not once a day is
  picked: they belong to no date, so pinning them under a day would be a lie and
  repeating them on every week you page to would be noise.
- **DONE THIS CYCLE** is keyed on the day it was *done*, not the day it was due.
  Those two are routinely different, and the question the section answers is
  "what have I already got through".

Strip changes: **chevrons either side** — the swipe stays, the chevrons are for
finding it — and the week now **slides the way you sent it** (`AnimatedContent`,
220ms), so paging reads as movement along a line rather than the numbers being
replaced where they stand.

The **This week** button had been appearing into the header row and pushing the
whole strip down, which made the screen twitch every time you paged. It now
lives in a slot that is always there and always 104dp wide; only its contents
come and go.

An empty week or day gets a line instead of a blank. Deadpan rather than
congratulatory — nothing to do is the ordinary state of a flat that is working,
not an achievement, and a "well done!" here would be the gamification the
never-list rules out wearing a joke as a disguise. None of the lines mentions
anybody else or what they might still have on. The line is picked from the date
being shown rather than at random, so it stays put while you read it.

`WeekAgendaTest` rewritten for the window: ten tests covering the week bounds,
day selection, the named headings, as-needed and done placement, and the
no-duplicate-rows invariant that B-9 was about.

## 2026-09-13 (later still) — My week, and one sheet for both screens

Three things, one of which turned into a crash worth writing down.

**A week strip, and two readings of the same turns.** The toggle beside the
refresh icon switches My chores between grouping by household — how the tab
started — and DESIGN.md §5.11a's **My week**: a seven-day strip on top, then
TODAY / TOMORROW / LATER THIS WEEK / NEXT WEEK AND BEYOND / NO DATE, with the
done-this-cycle rows kept last. The title follows the toggle, so it really is
*My chores* or *My week*, and `TurnsViewPreference` remembers which on the
device.

Both exist **for the interviews**, not forever. The design has one destination;
which reading people reach for is a question about people rather than one to
settle by argument, so it is being put in front of people. The loser gets
deleted, and `BACKLOG.md` says so in advance, so the pair is not mistaken later
for indecision.

The strip is not the calendar constraint 5 forbids and not the tab
`calendar_decision_record.md` deleted: no external calendar, no free/busy,
nothing of anybody else's on it. A dot means *you* have a turn that day — never
how many, and no day is coloured by how full it is.

**Tapping a turn opens the board's own sheet.** It used to switch the board's
household and leave you there. Rather than copying two hundred lines of wiring
into a second screen, the detail sheet and everything behind it — history, the
edit flow, the delete confirm, the busy pass — came out of `GroupDetailScreen`
into `OccurrenceDetailHost`, driven by a hoisted `ChoreDetailController`. Both
screens now mount the same thing. My chores spans households, so it builds a
`GroupDetailViewModel` for whichever household the tapped turn belongs to; that
is also why **Busy? Pass it on** works there, while swipe-to-pass on the row
does not — the swipe needs the rotation and the away states, which the tab does
not fetch.

**The crash.** A turn due tomorrow landed in TOMORROW *and* in NEXT WEEK AND
BEYOND, because "later this week" ends on Sunday and today *was* Sunday, so
tomorrow was already after it. Two rows, one key, and a `LazyColumn` throws on
that rather than drawing it twice — the app died on opening the tab. Fixed by
coercing the boundary to at least tomorrow, plus a `seen` set so a future
boundary mistake degrades instead of crashing. `WeekAgendaTest` now walks every
weekday and asserts no turn is bucketed twice and none is lost; six tests,
green.

Also: `TaskCard` gained an optional household chip, shown on My week where the
rows are ordered by date and come from more than one place, and absent on the
board where it would be noise.

Device-verified: both readings, the toggle and its title, the strip's today
ring and violet dot, swiping to next week and the **This week** button, the
detail sheet opening from a row with history, edit and pass, and the crash gone.
A throwaway chore was created for the test and deleted through the sheet's own
edit flow, which verified that path too.

## 2026-09-13 (later) — The group list is gone; the name at the top is the switcher

The first tab no longer lists households. It opens the board of the one you
last had open, and the household name in its app bar is a chip with a chevron:
tapping it drops a menu of your households, with a check on the one you are in,
and *Create a household* / *Join with a code* beneath a divider. That is
DESIGN.md §4.2, finally — the list was a transit screen most people passed
through with one card on it.

`LastHousehold` keeps the choice on the device, next to `Appearance` and
`LocalHints` and for the same reason: which flat you last had open is a
property of this phone, not of the account. Nothing trusts it — the stored id
may name a household you have left, or one belonging to whoever was signed in
before — so `HouseholdTab` resolves it against the group list it actually has
and falls back to the first household. Sign-out clears it.

Consequences worth recording:

- **The board moved inside the shell.** It is the first tab rather than a route
  pushed on top of a list, so it has no back arrow, and the bottom bar sits
  under it. The create-chore flow is a full-screen `Dialog`, so it still covers
  the bar — which is what §4.1 asks for and what it already did.
- **`group/{groupId}` is deleted.** Three routes left: `login`, `register`,
  `shell`. Tapping one of your turns in **My chores** now switches the board's
  household and shows it, rather than pushing a second board on top; the
  current household is therefore hoisted into `MainShell`, where both writers
  can reach it.
- **`GroupDetailViewModel` is keyed on the group.** `viewModel()` keys by type
  within a store owner, so switching household would otherwise have kept the
  old household's ViewModel and left its chores on screen under a new name.
- **`DashboardViewModel` → `ShellViewModel`, `DashboardScreen.kt` →
  `AccountDialogs.kt`.** With no dashboard left, the names were describing a
  screen that no longer exists. The file keeps the four dialogs that outlived
  the list; the group list, its cards and its three states are deleted.

One thing the screenshots caught: the switcher's household monogram was tinted
from `tertiaryContainer`, and it came out **dark red**. `Theme.kt` defines
`primary`, `secondary` and `tertiary` and stops, so every `…Container` role
falls back to Material's baseline — and the baseline's tertiary container is
red. A red disc beside a chore row is exactly the shame signal constraint 3
rules out, and it arrived without anybody choosing it. The monogram now tints
from the theme's own three colours at 18% alpha. Noted in `CLAUDE.md` §7,
because the next person reaching for a container role will hit the same trap.

Copy pulled into line while the dialogs were open: *Create a household*,
*Household name*, *Join a household*, *Leave household?*, housemate rather than
teammate.

Device-verified end to end on the physical phone: opening straight onto the
board, the menu, creating a second household and the starters offer over the
bar, switching between the two, leaving one and landing on the other, the
choice surviving a cold start, and a My chores row switching the board rather
than stacking one.

## 2026-09-13 — The bottom bar, and a settings screen that exists

The group list had quietly become the app's settings screen: quiet hours,
appearance, joining with a code and signing out all lived behind three dots on
a screen about households. Replaced with the shell DESIGN.md §4.1 asks for — a
persistent bottom bar over three destinations — and deleted the overflow menu.

**My groups** kept only what is about households: the list, New Group, the
first-run starters, and Join with a code on the empty state (a person with no
households is the one person who must not be sent to another tab to find half
the answer).

**My chores** is new, and the only screen that crosses household lines: your
own open turns from every household you're in, grouped by household, oldest
date first, with the done-this-cycle rows kept last so the ten-minute undo is
reachable. It reuses the board's own `TaskCard` rather than a second row
implementation — the `AuthScaffold` lesson. The checkbox works; passing does
not, because passing needs the rotation and the away states, which belong to
one household's board. Tapping a row opens it.

**Settings** is three groups — You / Households / This app — over rows rather
than menu items, with a monogram header. Invitations moved here and the bar's
only badge is a violet dot on this tab while any are waiting.

Two departures from DESIGN.md §4, both deliberate, both recorded in
`BACKLOG.md`: the first tab is **My groups**, not a single household's Board —
the group list is not deleted as §4.2 plans, because the household switcher
sheet that was meant to replace it does not exist yet. And **My chores** is the
list form of §5's My week without the calendar and day-strip modes.

Backend: `PATCH /me` grew `username` and `email`, so the two identity rows in
Settings have something to call. Stricter than register on email — it is the
login identifier and a typo locks you out of your own account — and a taken
name or address comes back 409 with register's own wording. Eight new handler
tests; `go vet` and `go test ./...` clean.

Device-verified on the physical phone against the local backend: all three
tabs, the tab state surviving a switch, a rename round-trip (`PATCH /me -> 200`,
snackbar, header updated, renamed back), and the board still opening
full-screen with no bar under it.

Open: the no-household empty state and the multi-household case have not been
driven on a device, only reasoned about; the `screens/` captures are now stale
for three screens (V2-HANDOFF phase 4b).

## 2026-09-13 — Sign in and Create account built from the Stitch mocks

**Done**

- Set up the Google **Stitch** MCP server and pulled the two auth screens from
  project **Core Design System** (`4515006561005969934`): *Sign in* and *Create
  account*. Both are `DESIGN.md` §5.1–5.2 drawn, and both are now built.
- `LoginScreen` keeps its `LoginUiState` / `LoginContent` split; `RegisterScreen`
  was a 264-line stateful blob and got the same treatment — `RegisterUiState`,
  a stateless `RegisterContent`, and seven previews including font scale 2.0 and
  a 320dp screen.
- The shared half went to **`ui/components/AuthScaffold.kt`**: `AuthHeader`,
  `BrandMark`, `AuthField` (label above the box, leading icon, optional helper
  line), `AuthSubmitButton`, `NoticeCallout`, `AuthErrorLine`, `QuietFooter`,
  and the `mutedInk` / `subtleInk` / `authAmber` colours. One file, so a change
  to a field cannot land on one screen and miss the other.
- What actually changed on screen: emblem tile above the wordmark, subtitle now
  *"Your household's chore board."*, labels above the fields instead of floating
  in them, envelope / person / lock leading icons, a reveal eye that did not
  exist before, 12dp field radius on `surface` with the `#D4D6E0` outline the
  theme already had, and a full-round 56dp pill for the primary action.

**Decisions**

- The mock's **"Forgot?"** link is not built. There is no password-reset route
  on the backend — it would have been a dead link on the first screen.
- Errors are the board's **amber** (`#9A6700` / `#E0A534`), not
  `colorScheme.error`. `DESIGN.md` §5.1 asks for an amber inline line, and it
  keeps "wrong password" in the same register as "this was wanted yesterday"
  rather than making one of them an alarm. The constants are duplicated from
  `GroupDetailScreen`, where they are file-private; if a third screen needs
  them they should go to `Theme.kt`.
- The register password placeholder is *"Create a password"*, not the mock's
  bullets — TalkBack reads bullets aloud as bullets.
- The emblem is the app's own `ic_launcher_foreground` rather than the mock's
  drawn bars, at the request of the person whose app it is.

**Open**

- `ic_launcher_foreground.xml` still describes itself as a placeholder and is
  now on the first screen of the app. It wants a real mark.
- Nothing has been seen on a device: no emulator or phone was attached this
  session. Previews 6 (font scale 2.0) and 7 (320dp) are the two to check first.
- `screens/01-login.png` and `02-register.png` are now stale (V2-HANDOFF
  phase 4b).

## 2026-09-11 — DESIGN.md v2: bottom bar, My week, real Settings

**Done**

- Rewrote `DESIGN.md` around a new information architecture: a persistent
  **bottom navigation bar** with three destinations — **Board** (the
  household's), **My week** (personal, private), **Settings** (personal).
- The group list is gone as a destination. It became a **household switcher
  sheet** on the Board app bar plus a Households section in Settings, and the
  two overflow menus that held quiet hours, appearance, invitations, join and
  sign out were emptied into Settings.
- **My week** — your own turns only, across every household, list mode by
  default (Today / Tomorrow / Later this week / Next week / No date) with a
  month grid as a toggle. Ticking, passing and the chore detail sheet all work
  the same as on the board.
- **Settings** — profile card with a photo, display name, username, email,
  change password, delete account; Households with invitations, join, create
  and per-household management; Reminders and quiet hours; Appearance; a
  Privacy & sharing explainer; About and sign out.

**Two collisions, both reconciled in the document rather than waved through**

- Constraint 5 bans calendar *integration* and free/busy scheduling, and
  `calendar_decision_record.md` deleted the old Calendar tab for three reasons.
  My week is admitted on the grounds that it is personal (never renders another
  member), reads occurrences and one-offs together across households (the old
  tab read only one-off tasks), and makes the list primary because the list is
  what a single-live-occurrence data model can fill. The grid is a toggle and
  is honest about being sparse: hollow rings for projected turns, and a
  permanent "3 with no date" chip for as-needed chores that can never be drawn
  on a grid. §5.11 carries the reasoning; `BACKLOG.md` should get the same
  entry if this ships.
- Profile photos, username/email changes and password changes have **no
  backend**. `PATCH /me` takes quiet hours and nothing else. §7 lists the five
  API gaps, including a proposed `GET /me/turns` so My week is not N+1 across
  households.

**Open**

- Nothing is implemented yet — this is a target design. The build order is §8.
- `screens/` is now stale against the target as well as against the build.

---

## 2026-09-10 — Repo docs initialised; UI/UX redesign started

**Done**

- Wrote `CLAUDE.md` at the repo root: what the app is, the doc map, the eight
  hard constraints, the domain model, stack commands, screen map, theme tokens,
  and the landmines (PATCH tri-state, inlined `BASE_URL`, debug password
  logging, release signing, git identity).
- Wrote this journal and seeded it with the history below.
- Wrote `DESIGN.md`: the design system (colour, type, space, motion, voice,
  component library) plus a screen-by-screen UI/UX spec covering all 18
  surfaces — purpose, layout, content, states, interactions, and redesign
  notes per screen. **Primary violet `#7C3AED` is fixed and unchanged.** Ends
  with a fix-first list and a review checklist.
  (Started as a pack of Stitch prompts; switched to a tool-independent spec
  mid-session at Mehrdad's request — sections are still written to stand alone
  so any one can be pasted into a design tool.)

**State of the app at this point**

- Backend and Android client both run locally; app installed and exercised on
  both a physical device (LAN `192.168.1.120:8080`) and the emulator.
- 31 screenshots in `screens/` from the real build, captured pre-redesign.

**Open**

- V2-HANDOFF phase 4b — retake every screenshot in `TaskFlow_Screens.pptx` from
  the real build — still outstanding, and the redesign will invalidate the
  current set again.
- Two defects named in `PROPOSALS.md` are still unfixed: the owner cannot leave
  a household they created, and a rotation edit silently voids a turn somebody
  owed.

---

## 2026-09-10 (earlier) — Git identity untangled

Push to `mehrdad` failed: the repo is `babakch76/taskflow` and the work account
`mehrdad-spaarkly` is not a collaborator. Resolved by routing the remote through
a `github-personal` SSH host alias with its own key, setting the local
`user.name` / `user.email` to the personal identity, and re-authoring the
current commit. Read access confirmed; write access still to be proven by a
successful push.

Also ran the app on the emulator: APK built and installed, login flow exercised
end to end, backend `/auth/register` confirmed to require `username`.

## 2026-09-08 — `intervalDays` accepts 0 or empty

The backend enforces `intervalDays` in 1–365 and deliberately excludes 0 to
prevent immediate re-due. The UI now accepts empty or `0` as a friendly way to
say "every day, no gap" and maps it to `1` before sending. Caption on the field:
*"Empty or 0 means every day."*

## 2026-09-07 — Local device bring-up, and the missing entrypoint

Setting the app up to run on a physical phone surfaced that
`task-manager-backend-GO/cmd/server` **did not exist in any branch** — the
deployed binary had no source in the repo. Wrote `main.go` with full routing,
graceful shutdown and request logging, and fixed `.gitignore`, whose binary
patterns were exact-matching the `cmd/server` directory out of existence.

Configured the phone path: LAN IP in `local.properties` and in
`network_security_config.xml`, clean rebuild, APK installed, requests confirmed
flowing from the phone to `192.168.1.120:8080`.

## 2026-09-07 — Interview packs

Interview restructured to five shared deep questions with perspective-specific
example answers, trimmed to a ~3-minute spoken target, PDF layout tightened from
six pages to five, `INTERVIEWS.md` regenerated.

## 2026-09-03 → 09-05 — Deployment and documentation

v2 deployed to the EC2 box (Caddy on 443 → `127.0.0.1:8080`), deployment turned
into a script, and the previous day's wrong diagnosis of the box corrected.
`FEATURES.md` written against the built app rather than the plan, and two
READMEs that still described v1 fixed.

## 2026-09-02 — UI cleanup merged

`ui-cleanup` phases 1–4a merged into `main`: constraint 3 cleaned up on the
legacy task path, dynamic colour switched off, the two create dialogs merged
into one, the Calendar tab deleted, quiet hours moved to the Dashboard, an away
banner added to the board, the tab count fixed. Phase 4b (retaking every
screenshot from the real build) left open.

## 2026-08-21 — v2 handoff written

End of the chat that removed the manager role and built F1 (the Board).
`V2-HANDOFF.md` written. The pivot from generic task manager to household chore
rotation is treated as a pivot, not an increment: `Task` becomes **Chore** plus
**Occurrence**, occurrence states collapse to `open → done`, and the governing
metaphor becomes a hallway whiteboard whose entries do not wash off.

## Earlier — F1 to F6

Board (F1), chore definitions + rotation + occurrence spawning (F2),
notifications and quiet hours (F3), done-line and edit-diff broadcasts (F4),
busy pass and away (F5), history (F6). Then: the turn rule case by case
("every passer owes one"), undo on the pass snackbar scoped to the mis-swipe it
belongs to, drag ordering in the rotation picker, and the everyone-busy path
that returns a chore to whoever asked first.
