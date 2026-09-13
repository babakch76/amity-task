# DESIGN.md — TaskFlow

The design specification for the TaskFlow Android app: the design system, the
information architecture, and every screen described well enough to be redrawn
from scratch. Written 2026-09-10, revised 2026-09-11 with the new bottom
navigation, the personal **My week** view, and a real **Settings** section.

This is the **target** design, not a description of the current build. Where it
differs from the shipped app, this document wins and `journal.md` records the
gap. `CLAUDE.md` describes the code as it is today.

Hand this to a designer, or paste one screen section into a design tool
(Stitch, Figma AI, v0) at a time — each section stands on its own.

**The primary violet does not change.** `#7C3AED` on light, `#A78BFA` on dark.

---

## 1. What we are designing

A chore board for **one shared household** — three to five flatmates, a handful
of recurring chores. The app answers one question: **whose turn is it, and has
it been done?**

The governing metaphor is **a hallway whiteboard whose entries don't wash off**.
Accountability comes from a chore staying visibly next to your name — never
from badges, counters, or notifications about other people.

Not a task manager, not a productivity dashboard, not a family gamification
app. Every visual decision should read as *calm, factual, private*.

### The five design principles

1. **Quiet by default.** State facts and stop. No cheering, no nagging, no
   urgency theatre.
2. **One question per screen.** The board answers "whose turn"; My week answers
   "what's on me"; Settings answers "how do I change this".
3. **Nothing that can be read as a scoreboard.** Two housemates comparing
   screens must find nothing to compete over.
4. **Lateness is visible, never accused.** Amber, once, on the line carrying
   the date.
5. **Shared and personal are visibly different places.** The Board is the
   household's. My week and Settings are yours alone, and the interface says so
   rather than leaving people to guess.

### The never list (hard constraints — a violation is a bug)

- **No red for lateness.** Amber `#B45309` / `#F59E0B` only, on the line
  carrying the date. Red exists at exactly one place: the Delete confirm button.
- **No progress bars, percentages, rings, "5 of 8 done" counters** — anywhere.
- **No points, streaks, leaderboards, rankings, medals, confetti.**
- **No counter of anybody's skips, passes, or late turns.** No "3×".
- **No sorting people by how much they did.** Person rows are visually
  identical to one another.
- **No warning icons, no exclamation marks, no shouting typography.**
- **No avatar status rings, presence dots, or badges on a person.** A profile
  photo is a photo, nothing more.
- **A busy pass is invisible to the household.** Never in the activity feed; no
  screen may list who passed what.
- **My week shows only your own turns.** It never renders another member's
  name, date, or workload, and it has no shared or comparison mode.

---

## 2. Foundations

### 2.1 Colour

Material You dynamic colour stays **off** — a wallpaper-derived scheme can put
a red primary beside the amber overdue colour, producing exactly the shame
signal the constraints forbid.

| Role | Light | Dark |
|---|---|---|
| primary | `#7C3AED` | `#A78BFA` |
| onPrimary | `#FFFFFF` | `#FFFFFF` |
| primary container (tint) | `#EDE4FE` | `#2A2140` |
| secondary (accent, sparing) | `#60A5FA` | `#60A5FA` |
| tertiary (completion) | `#34D399` | `#34D399` |
| background | `#F8F9FC` | `#0F1117` |
| surface (cards, sheets, bottom bar) | `#FFFFFF` | `#181A24` |
| surfaceVariant | `#F1F3F9` | `#1E2130` |
| onSurface | `#1A1B2E` | `#EEF0F6` |
| onSurfaceVariant (muted) | `#5B5D72` | `#9AA0B4` |
| outline (hairline) | `#D4D6E0` | `#2A2E3F` |
| **attention / overdue** | `#B45309` | `#F59E0B` |
| attention container | `#FEF3C7` | `#3A2A0A` |
| error (delete only) | `#DC2626` | `#F87171` |

**Rules.** Violet marks *the primary action and the thing that explains
itself* — the FAB, the filled button, the selected option, the active
navigation item, the "why is this yours" line. Two violet things competing on
one screen is a bug. Green appears only on a completed row's checkbox. Blue
appears only on membership events in the feed. Amber appears only where a date
has passed, at most once per row.

### 2.2 Type

Inter, Roboto as platform fallback. Six sizes, no more.

| Token | Size / weight | Used for |
|---|---|---|
| Display | 28sp ExtraBold, tracking −1 | Screen titles, wordmark |
| Title | 22sp Bold | App bar titles, sheet and dialog titles |
| Section | 20sp SemiBold | Question headings ("How often?") |
| Row title | 17sp SemiBold | Chore names, member names, settings rows |
| Body | 15sp Regular | Sentences, detail values |
| Support | 13–14sp Regular, muted | Schedule lines, timestamps, helpers |
| Label | 12sp Medium, uppercase, +0.8 tracking, muted | Section headers |

Never more than three sizes inside one card. Never centre body text except in
an empty state.

### 2.3 Space, shape, elevation

- 8dp base grid. Screen side padding **16dp** (32dp on the two auth screens).
- 12dp between cards; **24dp between sections**; **96dp bottom padding** on any
  scrolling list so the bottom bar and the FAB never cover the last row.
- Corners: cards and sheets **16dp** (sheet top corners 28dp), fields and
  outlined buttons **12dp**, primary buttons a **28dp pill**, chips full round.
- Cards are flat: `surface`, **1dp `outline` hairline**, soft shadow (2dp blur,
  4% black). No heavy elevation, no gradients, no glass.
- Minimum tap target 48dp. The board checkbox is 28dp inside a 48dp target — it
  is the single most-tapped element in the product.

### 2.4 Motion

150ms for state changes, 250ms for sheets and screen transitions, standard
Material easing.

- **Tick a row** → checkbox fills, row fades to 60%, slides to DONE THIS CYCLE.
  No burst, no celebration haptic.
- **Swipe to pass** → row translates to reveal a violet panel; release animates
  out and raises the Undo snackbar.
- **Bottom-bar switch** → cross-fade the content, 150ms. No horizontal paging
  between destinations, so a mis-swipe never lands you in Settings.
- **Sheets** slide up with a drag handle; dialogs fade and scale from 96%.

Nothing loops. Nothing pulses for attention.

### 2.5 Voice

British English, sentences over labels, lower case for names as people typed
them. Buttons are verbs in sentence case: *Add chore*, *I did this*, *I'm away*.

Three copy tests:

1. Would a flatmate say this out loud?
2. Does it name someone as failing? Rewrite it.
3. Does it promise privacy? Then the data layer must keep that promise, not
   just the wording.

---

## 3. Component library

| Component | Spec |
|---|---|
| **Bottom navigation bar** | 80dp, `surface`, 1dp top hairline. Three items, labels always visible. Active: violet pill indicator `#EDE4FE` behind a filled icon, violet label. Inactive: outlined icon, muted label. Optional 8dp violet dot badge (Settings only). |
| **Top app bar** | 64dp on `background`, no fill. Large title on root destinations, Title size on detail. Up to two icon actions plus overflow. |
| **Household switcher chip** | In the Board app bar: household name + small chevron; tap opens the switcher sheet. Hidden when the person belongs to one household. |
| **Tab row** | Three tabs max, 2dp violet indicator, label + count. |
| **Board row card** | `surface`, 16dp corners, hairline, 16dp padding. Leading 28dp square checkbox in a 48dp target; three text lines; trailing chevron; optional fourth line in violet 13sp. |
| **Agenda row** (My week) | Same anatomy, plus a small muted household chip on the title line when the person has more than one household. |
| **Section header** | Label token, 24dp above, 8dp below, 16dp inset. |
| **Segmented control** | Two or three segments, full-round, `surfaceVariant` track, violet selected segment with white label. Used for List/Calendar and the history ranges. |
| **Day strip** | Seven 44dp columns, weekday letter above the date. Today: violet ring. A day with turns: 4dp violet dot beneath. Selected: filled violet circle. |
| **Month grid cell** | 44dp square, date numeral centred. Filled violet dot = a live turn of yours. Hollow violet ring = a projected turn. Amber dot = a date already passed. Empty = nothing. Maximum two marks per cell, then "+". |
| **Avatar** | Circle, 4 sizes: 28 (rotation picker), 44 (member row), 64 (settings header), 96 (profile screen). Photo, or a monogram on a soft violet tint. **Never** a ring, badge, or presence dot. |
| **Settings row** | 56dp, leading 24dp outlined icon in a muted tint, label at Row title, optional value right in muted Support, trailing chevron. Toggle rows use a Material switch instead of a chevron. |
| **Settings group** | Card of rows with 1dp dividers inset to the text, under a Label header. |
| **Filled button** | 56dp pill, violet, white 16sp SemiBold. One per screen. |
| **Outlined button** | 52dp, 12dp corners, violet text. |
| **Text button** | Violet, or muted grey for a quiet destructive action. |
| **Extended FAB** | Violet pill, plus icon + label, bottom right, 16dp from the side, **16dp above the bottom bar**. |
| **Text field** | Outlined, 12dp corners, floating label, optional leading icon, helper beneath. Errors are amber, never red. |
| **Option card** (create flow) | Full width, 64dp, label left, circled `?` right. Selected: `#EDE4FE` fill, 2dp violet border. |
| **Info callout** | Violet-tinted `#F1EBFE`, 12dp corners, small leading icon. Explains, never warns. |
| **Privacy note** | Grey `surfaceVariant` box, small eye or lock icon, one muted sentence. Used wherever a screen is private or a change is visible to others. |
| **Attention banner** | Amber-tinted `#FEF3C7`, leading icon, one sentence, optional trailing text button. |
| **Bottom sheet** | 28dp top corners, drag handle, up to ~85% height, internally scrollable. |
| **Dialog** | 28dp corners, title, body sentence, at most two actions. |
| **Snackbar** | `#1E2130`, white text, one violet action. 10s where an undo window exists. |
| **Role chip** | Small tinted, no icon: `Owner` violet, `Member` grey. |
| **Empty state** | 72dp tinted circle with an outlined icon, 20sp SemiBold heading, one muted sentence, one action. |

---

## 4. Information architecture

### 4.1 The shell

A persistent **bottom navigation bar** with three destinations. Two of them are
private to the person; one is the household's. That split is the backbone of
the whole IA.

```
┌───────────────────────────────────────────────┐
│                                               │
│                content                        │
│                                               │
├───────────────────────────────────────────────┤
│   ▣ Board        ▤ My week        ⚙ Settings  │
│   (household)    (only you)       (only you)  │
└───────────────────────────────────────────────┘
```

| Destination | Icon | Scope | Answers |
|---|---|---|---|
| **Board** | clipboard / checklist | The household you are looking at | Whose turn is it, and has it been done? |
| **My week** | calendar | **You, across every household** | What is on me, and when? |
| **Settings** | gear | You | Everything else |

Rules:

- The bar is **visible on all three roots and their tabs**, and **hidden** on:
  the two auth screens, the first-run starters, the two-step create/edit chore
  flow, and anything presented as a full-screen dialog.
- Sheets (chore detail, history, what's been done, household switcher) are
  drawn **over** the bar; the bar stays visible and disabled behind the scrim.
- **Back** from My week or Settings returns to Board. Back on Board exits.
  Re-tapping the active item scrolls its list to the top.
- Each destination keeps its own scroll position and its own back stack.
- The only badge in the bar is a violet dot on **Settings** when invitations are
  pending. No counts anywhere in the bar.

### 4.2 Where the old screens went

The group list is gone as a destination. It was a transit screen most people
passed through with one card on it.

| Was | Is now |
|---|---|
| Dashboard / My Groups | **Household switcher sheet** from the Board app bar, plus the **Households** section of Settings |
| Quiet hours (overflow) | Settings → Reminders |
| Appearance (overflow) | Settings → Appearance |
| Invitations (overflow) | Settings → Households → Invitations (with the bar badge) |
| Join with a code (overflow) | Settings → Households → Join with a code |
| Sign out (overflow) | Settings → bottom |
| Invite by username / Create invite code (group overflow) | Settings → Households → *household* → Manage, **and** kept on the Members tab where they are used in context |
| What's been done (group overflow) | Kept in the Board overflow — it is about this household, not about you |
| I'm away (group overflow) | Kept in the Board overflow, **and** mirrored in Settings → Privacy & sharing |

### 4.3 Route map

```
login ⇄ register
  └─ shell
      ├─ board/{groupId}            tabs: Board · Activity · Members
      │    ├─ household switcher sheet
      │    ├─ chore detail sheet → chore history sheet
      │    ├─ what's been done sheet
      │    ├─ away / invite / leave dialogs
      │    └─ create-or-edit chore   (full screen, 2 steps, bar hidden)
      ├─ myweek                     modes: List · Calendar
      │    └─ chore detail sheet (the same sheet)
      └─ settings
           ├─ profile               → change photo · change password
           ├─ households            → invitations · join with a code ·
           │                          new household · manage {household}
           ├─ reminders             → quiet hours
           ├─ appearance
           ├─ privacy & sharing     → away
           └─ about                 → sign out
```

---

## 5. The screens

Sample data is fixed throughout: household **Flat 3B**, members **you**,
**maya**, **sam**; second household **Mum's house**.

### 5.1 Sign in

**Purpose.** Get an existing person in, in two fields. No bottom bar.

Single centred column, 32dp side padding, keyboard-aware — the button must
never sit behind the IME.

- Wordmark **TaskFlow**, 28sp ExtraBold, violet.
- Muted subtitle: *"Your household's chore board."*
- 40dp gap.
- Field **Email**, envelope icon.
- Field **Password**, lock icon, trailing reveal eye.
- Filled pill **Sign in**.
- Text button *"New here? Create an account."*

**States.** *Expired session* → violet info callout above the fields: *"Your
session expired. Please sign in again."*, shown once. *Error* → amber inline
line: *"Email or password is wrong."* *Loading* → spinner in the button, fields
locked.

**Redesign notes.** Drop *"Sign in to your workspace"* — workspace is v1
vocabulary. Add the reveal-eye; it does not exist today.

### 5.2 Create account

Same skeleton. Fields **Username** (helper *"This is the name your housemates
will see."*), **Email**, **Password**. Filled **Create account**. Text button
*"Already have an account? Sign in."*

No strength meter, no step indicator, no social buttons. Registering returns a
token and signs you straight in — never show a "now sign in" screen.

After the very first registration the app goes to **first-run starters** (5.22),
then lands on Board.

### 5.3 The shell

Nothing is drawn by the shell except the bottom bar, but three things are its
responsibility:

- **Start destination** is Board, on the household you last opened.
- **Cold start with no households** → Board shows its no-household empty state
  (5.4), which offers *Create a household* and *Join with a code* rather than
  bouncing the person into Settings.
- **Bar badge** — a violet dot on Settings while `GET /invites` is non-empty.
  Polled with the same 5s cycle as the activity feed while the app is resumed.

### 5.4 Board — the household's screen

**Purpose.** Whose turn is it, and has it been done. Everything else here is
subordinate.

**App bar.** Back arrow only when arrived from a sheet. Title = the household
switcher chip — **Flat 3B ⌄** — at Title size. Right: refresh, overflow
(*What's been done · I'm away · Invite by username · Create invite code ·
Leave household*).

**Tabs.** **Board (6)** · **Activity** · **Members (3)**.

**Sections**, fixed order, by *whose it is*, never by status: **YOURS**,
**OTHERS**, **DONE THIS CYCLE**.

```
☐  Clean the bathroom                                    ›
   weekly
   Due Thu 10 Sep
   maya covered your last turn, so it's yours again     ← violet, only when
                                                          there is something
                                                          to explain
```

Line 2 is how it recurs and when it is wanted; line 3 is whose turn it is
(*"Your turn"*, *"maya · due Thu 3 Sep"*, *"sam's turn"*). A done row reads
*"done by sam · 2h ago"*, dims to 60%, fills its checkbox in green, and gets
**no strikethrough**.

Overdue: the date line turns amber with a small amber dot before it. No count,
no icon, no badge.

Under the first YOURS row, a one-time muted hint: *"Swipe a row for actions."*

**Extended FAB** *Add chore*, 16dp above the bottom bar.

**States.** *Loading* → skeleton rows under real headers. *Empty* → clipboard
empty state, *"Nothing on the board"*, *"Add the first chore and it'll show up
for everyone in the house."* *No households at all* → *"You're not in a
household yet"*, *"Create one for your flat, or join with a code a housemate
sent you."*, filled **Create a household**, text **Join with a code**. *Away* →
amber banner under the tabs: *"You're away. Chores are passing over you."* with
**I'm back**, and YOURS reads *"Nothing with your name on it while you're
away."* *Error* → inline block with **Retry**, tabs still usable.

**Interactions.** **Tick** — one tap, by anybody, on any row; snackbar offers
**UNDO** for ten minutes, only to whoever ticked. **Swipe** — your own rows
only, revealing a violet *"Busy — pass it"* panel; on release the snackbar says
*"Passed to maya. Only maya is told."* with **UNDO** (two minutes). **Tap** →
chore detail sheet. Activity polls every 5s while resumed.

**Redesign notes.** Raise the contrast between the row title and its two
support lines; give sections 24dp of air; 96dp bottom padding; make the
checkbox unmistakably *the* affordance.

### 5.5 Household switcher sheet

Opened from the app-bar chip; only exists when the person has two or more
households.

- Drag handle, title **Your households**.
- One row per household: 44dp violet-tinted circle with a two-people icon,
  name at Row title, muted second line *"3 members · 2 chores need doing"*, and
  a violet check on the current one.
- Divider, then two quiet rows: **+ New household** and **Join with a code**.
- Footer text button **Manage households** → Settings → Households.

No creation dates, no member avatar stacks, no counts of who is behind.

### 5.6 Chore detail sheet

Shared by Board and My week — one sheet, one implementation.

- Chore name (Title); muted line *"weekly · Due Thu 10 Sep"*; when opened from
  My week, a muted household chip beside it.
- Violet info callout when the turn needs explaining: *"maya covered your last
  turn, so it's yours again."*
- **WHAT DONE MEANS** label, then the agreed sentence: *"Sink, loo and shower
  screen, and the bin out."* Otherwise *"No agreed definition of done for this
  chore yet."*
- Quiet detail list, label left / value right: *Whose turn* → **You**;
  *How often* → **weekly**; *Due* → **Thu 10 Sep**; *Needed by* → **before
  7am**.
- Pinned at the bottom: filled **I did this**; outlined **Busy — pass it on**
  with the muted line *"Goes to the next person in the rotation. It comes back
  to you next time, and only they are told."*
- Text buttons **History** and **Edit chore**.

A one-off (legacy *task*) shows *Assigned to*, *Deadline*, *Description* and
offers **Set / Change / Remove deadline** and **Delete task**, and no pass —
one-offs do not rotate.

**Redesign notes.** Today the detail list, the done-line and the actions share
one visual weight. Order them: explanation, reference, actions pinned.

### 5.7 Chore history sheet

Title **History**, subtitle = the chore name, muted *"Every time this has been
done."*

Timeline with a hairline connector:

- **sam did it** — *Thu 4 Sep · was due Thu 4 Sep*
- **maya did it for you** — *Thu 28 Aug · was due Wed 27 Aug*
- *you were away · 20–24 Aug* — grey dot, italic, no bold line
- **you did it** — *Wed 20 Aug · was due Wed 20 Aug*

Empty: *"Nothing done yet. It'll show up here once someone ticks it off."*

**No totals, no on-time/late labels, no colour coding.** Lateness is derivable
from the two dates and is never called out.

### 5.8 Activity tab

The household's shared record, in words — the awareness loop.

Rows: small leading dot (violet = chore change, blue = membership, green =
completion), the sentence at Body, muted relative time right-aligned. Grouped
under **TODAY** / **YESTERDAY** / **EARLIER**.

- *maya ticked off Put the bins out* — 20m ago
- *you added Hoover the stairs* — 2h ago
- *sam changed when Kitchen floor is due* — yesterday
- *maya joined the household* — Tue

Empty: *"Nothing has happened yet"* / *"Chore changes, joins and invites show
up here as they happen."*

**A pass never appears here** — not even redacted.

**Redesign notes.** Raw event vocabulary still reaches the screen in places
(`occurrence_done`, `assigned_to=cleared`). Every event type needs a human
sentence; anything without one is dropped, not shown raw.

### 5.9 Members tab

Member cards: 44dp avatar (photo or monogram), name at Row title, muted second
line, role chip right.

- **you** — *joined 3 Sep* — **Owner**
- **maya** — *joined 3 Sep* — **Member**
- **sam** — *away until Fri 12 Sep* in amber with a suitcase icon — **Member**

Below: outlined **Invite by username** and **Create invite code**. At the very
bottom a muted text button **Leave household**.

**No per-person counts here.** Away is the only status a person carries.

### 5.10 What's been done (household record)

Sheet. Segmented control **This week · This month · 3 months**. Then one
identical row per person, in a **fixed neutral order that never reorders by
amount**:

- **you** — *4 chores*
- **maya** — *5 chores*
- **sam** — *0 chores* — muted *"away 3 days of this"*

Footer: *"Counted by who actually did it, so covering for someone counts for
you."*

Zeroes are ordinary: same type, same colour, no dimming. **It counts and does
not rank** — no bars, no percentages, no ordering, no highlighting.

---

### 5.11 My week — the personal view

**This is a new destination.** Read this preamble before designing it.

**Why it is allowed, and what it must not become.** Two recorded decisions bear
on it. Constraint 5 bans *calendar integration and free/busy scheduling* — this
is neither: no external calendar, no availability, no coordinating times with
other people. `calendar_decision_record.md` deleted the old Calendar tab for
three reasons: it read only one-off tasks and so hid every rotation chore; the
data model puts **one live occurrence** on each chore, so a month grid is mostly
empty; and it was a shared view of the household's dates, which is the thing
constraint 5 is pointed at.

My week answers a different question — **"what is on me?"** — and it is
answered three ways the old tab could not:

1. **It is personal.** Only your own turns, and it never renders another
   member's name, date or workload. No shared mode exists.
2. **It reads occurrences and one-offs together**, across **every** household
   you belong to, which nothing in the app does today.
3. **The list is the primary mode**, because the list is what the data can
   actually fill. The grid is a toggle, and it is honest about being sparse.

**Purpose.** Your own turns, in date order, calm enough to check in bed.

**App bar.** Display title **My week**, and a right-hand icon toggling
List / Calendar (the choice is remembered). No refresh icon — it polls.

**Privacy note**, once, directly under the bar, dismissible and then never
shown again: grey box with a lock icon — *"Only you can see this. Your
housemates have their own."*

**Day strip**, always visible under the bar in both modes: seven columns,
Mon–Sun, weekday letter over date, today ringed in violet, a 4dp violet dot
under any day carrying a turn of yours, an amber dot for a day already passed.
Tapping a day scrolls the list to it (list mode) or selects it (calendar mode).
Swiping the strip moves a week; a **This week** text button appears once you
have moved away from the current one.

#### 5.11a List mode (default)

Buckets, in this order, each under a Label header, and a bucket with nothing in
it is simply absent:

| Bucket | Contents |
|---|---|
| **TODAY** | Your turns due today, **and** anything whose date has passed, at the top of the bucket with its date line in amber |
| **TOMORROW** | Due tomorrow |
| **LATER THIS WEEK** | Through Sunday |
| **NEXT WEEK AND BEYOND** | Collapsed to five rows with a *Show all* text button |
| **NO DATE** | *as-needed* chores that are currently your turn |

Rows use the agenda row: checkbox, chore name, schedule line, and a third line
carrying the date or *"whenever it's needed"*. When you belong to more than one
household, a small muted chip sits beside the chore name — *Flat 3B*,
*Mum's house*. One-off tasks appear here too, marked *one-off* on the schedule
line.

Ticking works exactly as on the board, with the same ten-minute UNDO. Tapping
opens the same chore detail sheet. **Swipe to pass works here too** — this is
the screen where someone realises they cannot do a thing this week.

**No FAB.** Adding a chore is a household act and belongs to the Board.

**Empty state.** 72dp tinted circle with a calendar icon, *"Nothing on you this
week"*, one muted line *"Your turns will show up here, from every household
you're in."*

**What must never appear here.** Another person's turn. A count of how many
turns you have versus anyone else. A "workload" summary. A percentage of the
week complete. A red anything.

#### 5.11b Calendar mode

The same data in a month grid, for people who think in months. It is a toggle,
never the default, and it must be honest about what the data can fill.

- Month header with **‹ September 2026 ›** and a **Today** text button.
- Weekday initials row, then the grid. Cells are 44dp:
  - **filled violet dot** — a live turn of yours;
  - **hollow violet ring** — a *projected* next turn (derived, and it moves when
    reality does);
  - **amber dot** — a date that has passed;
  - at most two marks, then a small "+".
- The selected day is a filled violet circle; today is ringed.
- **Below the grid**, the selected day's rows in the same agenda row as list
  mode, with the day written out: *"Thursday 10 September"*. An empty day says
  *"Nothing on you."*
- A permanent footer chip above the bottom bar: **3 with no date ›** — opens
  the NO DATE bucket in list mode. This is how the grid stops lying about
  as-needed chores, which have no date and can never be drawn on it.
- One muted line under the grid, always: *"Hollow rings are the next turn as
  things stand. They move when a chore is done early or late."*

**Never** draw other members' turns on the grid, never colour a day by how full
it is, never show a monthly total.

---

### 5.12 Settings — home

**Purpose.** Everything that is about *you*, plus the management of households
that does not belong in the flow of the board.

**Layout.** Display title **Settings**. No FAB, no overflow.

**Profile card** at the top — its own full-width card, not a row: 64dp avatar
on the left, display name at Title, `@username` muted beneath, email muted
below that, chevron right. Tapping opens Profile (5.13).

Then grouped rows:

```
HOUSEHOLDS
  👥  Households                          Flat 3B, Mum's house  ›
  ✉️  Invitations                                        2  ›   ← violet dot
  🔗  Join with a code                                       ›
  ＋  New household                                          ›

REMINDERS
  🔔  Reminders                                    On  ›
  🌙  Quiet hours                          21:00 – 07:30  ›

APPEARANCE
  🎨  Appearance                                  System  ›

PRIVACY & SHARING
  🔒  What your household can see                        ›
  🧳  Away                                       Not away  ›

ABOUT
  ℹ️  About TaskFlow                            v2.1.0  ›
  ⎋   Sign out
```

Row spec: 56dp, 24dp outlined icon in a muted tint, label at Row title, current
value right in muted Support, chevron. **Sign out** is a muted row with no
chevron and no icon tint, and it asks for confirmation: *"Sign out?"* /
*"You'll need your email and password to get back in."*

The **Invitations** row shows a count and a violet dot only while invitations
are pending; otherwise it is absent, not shown as zero.

### 5.13 Profile

**Purpose.** Who you are to your housemates, and the credentials behind it.

- 96dp avatar centred at the top, with a small violet camera badge on its
  corner. Tapping opens a sheet: **Take a photo · Choose from gallery ·
  Remove photo**. A chosen image is cropped square in a circular mask with
  pinch-to-zoom, then uploaded; while it uploads the avatar dims and shows a
  small spinner, and a failure is an amber inline line under it with **Retry**.
  Without a photo the avatar is the monogram on a violet tint — that is a
  complete, unapologetic state, not a prompt to fix something.
- **YOU** group:
  - *Display name* — inline-editable field, helper *"What your housemates see
    on the board."*
  - *Username* — inline-editable, helper *"Used for direct invites. Letters,
    numbers and underscores."* Validation is amber inline: *"That username is
    taken."*
  - *Email* — row with the address, chevron to a change-email screen that
    requires the current password.
- **SECURITY** group:
  - *Change password* → its own screen: **Current password**, **New password**,
    **Confirm new password**, filled **Change password**. Rules are stated up
    front as a muted line (*"At least 8 characters."*), never as a strength
    meter, and never as a red error — mismatch is amber inline.
  - *Signed-in devices* — omit unless the backend grows sessions; do not fake it.
- Bottom, muted text button **Delete account**, with a dialog naming exactly
  what goes: *"Your account, your turns, and your place in every household.
  Households you own with other people in them are not deleted."*

**Save behaviour.** Each field saves on blur with a quiet snackbar *"Saved."*
No global Save button, no dirty-state dialog.

### 5.14 Households

**Purpose.** Join, create, leave, and manage — the administrative half of what
the old group list did.

- Display title **Households**.
- One card per household: 44dp tinted circle, name at Row title, muted
  *"3 members · you're the owner"*, chevron.
- Then two rows: **Join with a code** and **New household**.
- **Invitations** appears as a violet-tinted banner at the top when pending:
  *"2 invitations waiting"* with **View**.

**Invitations sheet.** One row per invite — household name, muted *"invited by
maya"*, **Decline** (text) and **Accept** (filled). Closes itself when the last
one is handled.

**Join with a code dialog.** One sentence *"Paste the invite code a housemate
shared with you."*, one field, monospace code, **Cancel** / **Join**.

**New household dialog.** *Household name* (helper *"What you'd all call it.
'Flat 3B'."*), *Description (optional)*, **Cancel** / **Create**. On success it
goes straight to first-run starters for that household.

**Manage household** (tapping a card):

- Header: the household name, editable inline by the owner; description
  beneath, also editable.
- **MEMBERS** group — the same member rows as 5.9, read-only here, with a
  *"See all"* row when there are more than five.
- **INVITE** group — *Invite by username*, *Create invite code*.
- **YOU IN THIS HOUSEHOLD** group — *Away in this household* (value: *Not
  away*), *Reminders for this household* (switch).
- Bottom: muted **Leave household**. For the owner while others remain, the row
  is disabled with the reason stated underneath rather than failing on tap:
  *"You created this household, so you can't leave while others are in it."*
  (That is a known defect — see §7.)

### 5.15 Reminders

- Switch row **Remind me about my turns**, master for the rest. Off dims the
  group and adds a muted line *"You'll still see everything on the board."*
- Switch **When a turn starts**.
- Switch **On the day it's due**, with a time field *at 08:00*.
- **Quiet hours** → its own row: *"Reminders due inside this window wait until
  it's over. Nothing is dropped."* Two 24-hour fields **From** / **Until**,
  helper *"Use 24-hour times, like 21:00."* Validation (start = end) is amber
  inline. Footnote: *"Kept on your account, across all your households."*
- Per-household switches at the bottom under **BY HOUSEHOLD**, one row each.

Reminders are scheduled **on the device**, so add one muted line at the foot:
*"Reminders are set on this phone. A new phone needs them set again."*

### 5.16 Appearance

Three radios **System · Light · Dark**, a live preview card above them showing
a board row in the chosen scheme, and the footnote *"Kept on this phone only —
your housemates don't see it."*

### 5.17 Privacy & sharing

An explainer screen, not a settings screen — it exists because the product's
privacy promises are unusual and people should be able to check them.

Three plain blocks, each a Body sentence under a Label header:

- **WHAT THEY SEE** — *"Chores you add or change, when you tick something off,
  when you join or leave, and whether you're away."*
- **WHAT THEY DON'T** — *"When you pass a chore on. Only the person it goes to
  is told, and it's never in the activity feed. Nobody can see how often you
  pass."*
- **WHAT'S COUNTED** — *"How many chores each person did, over a week, a month
  or three. It counts and it doesn't rank, and there's no score anywhere in the
  app."*

Then the **Away** row, which opens the same dialog as the board (5.18).

### 5.18 Away

Dialog **I'm away**. Body: *"You'll be taken out of every rotation here until
you're back, and you'll come back in the same place. No turns are owed."*
Radios **I don't know when I'm back** / **Until a date** (revealing a compact
date field). A privacy note with an eye icon: *"Everyone in the household can
see that you're away."* — the deliberate difference from a pass, and the dialog
must say it. **Cancel** / **I'm away**.

While away: the board banner (5.4), the amber line on the Members row, and My
week shows the same banner with *"Nothing is coming to you while you're away."*
Coming back is one tap, no confirmation.

### 5.19 Add a chore — step 1 of 2

Full screen, **bottom bar hidden**.

- App bar: **×**, title **New chore**, subtitle *Step 1 of 2*, two-segment step
  indicator (wayfinding, not a progress metric).
- Section **What's the chore?** → field **Name**, helper *"What a housemate
  would call it on a whiteboard."*
- Section **How often?** → four option cards, each with a circled **?** opening
  a violet popover:
  - **Every so many days** — *"You pick the number of days. It is counted from
    the last time it was done, so doing it late moves the next turn along with
    it."*
  - **A fixed day of the week or month** — *"The calendar decides it. Bin
    collection on Tuesday, rent on the 1st."*
  - **As needed** — *"No schedule at all. It sits on the board as someone's
    turn until it's done."*
  - **A one-time thing** — *"A repair, a delivery, a bill to pay once. It is
    assigned to one person and does not come round again."*
- Pinned filled **Next**, disabled until there is a name.

### 5.20 Add a chore — step 2 of 2

Four variants. All end with the rotation picker and the done-line, except the
one-off.

**Common tail.** Section **Whose turn does it take?**, muted *"Everyone takes a
turn. Drag to change the order."* → reorderable list in one card: drag handle,
28dp avatar, name, trailing tag (*first turn* in violet on the first, muted
*away* on an absent member). Then **What counts as done?**, muted *"One line,
agreed once, so it isn't argued every time. You can skip it."* → multiline
field, placeholder *"Bin emptied and a fresh bag in."* Pinned filled **Add
chore**.

- **A — every so many days.** **When does it need doing?** → stepper *"How many
  days between turns?"* with − / value / +, helper *"Empty or 0 means every
  day."* Optional **Needed by** (*20:00*), helper *"A time of day, if it
  matters."*
- **B — fixed day.** **Which day decides it?** → toggle *Day of week / Day of
  month*; seven day pills, or a 1–31 picker. Muted: *"The date comes from the
  calendar. Miss it and it rolls to the next one, with the same person still
  holding it."*
- **C — as needed.** No date controls. A violet callout: *"No date at all. It
  sits on the board as someone's turn until it's done, and there are no date
  reminders — just a nudge when a turn starts."* Add one muted line, because
  this is the case the new calendar has to explain: *"These sit in 'No date' on
  My week."*
- **D — a one-time thing.** **Who's doing it?** → single-select member list.
  Then **Date (optional)** with **Clear**, and *"A one-off is assigned once and
  doesn't rotate. Leave the date empty if there isn't one."* No rotation list,
  no done-line.

**Validation** is amber, inline, on the block at fault: *"Give the chore a
name."* · *"Pick at least one person to take a turn."* · *"Pick who's doing
it."*

### 5.21 Edit a chore, and deleting it

Identical to the two steps, prefilled, titled **Edit chore**, primary **Save
changes**.

- **A one-time thing** is shown **disabled with the reason stated**, never
  hidden: *"To change this into a one-time thing, delete it and add it again."*
  (And the reverse on a one-off.)
- Changing a schedule re-dates the open turn rather than stranding it; say so
  in a muted line once the schedule is touched.
- **Delete chore** is a muted text button at the very bottom of step 2, below a
  divider. Never on the row.

**Delete dialog.** *"Delete this chore?"* / *"This removes the chore, whoever's
turn it currently is, and the history of every time it was done. This can't be
undone."* / **Keep it** / **Delete** — the one red button in the app.

**Discard dialog.** *"Discard changes?"* / *"Your edits won't be saved."* /
**Keep editing** / **Discard**.

### 5.22 First-run starters

Full screen, bottom bar hidden, shown once per new household.

Title *"What needs doing round here?"*; muted *"Pick a few to start with. You
can change the schedule, the order and what counts as done at any point, and
anyone in the house can."*; five rows, each a card with a violet checkbox, the
name, its schedule in muted Support, a pencil to rename inline and an × to
remove:

*Trash* (as needed) · *Bathroom* (every 4 days) · *Kitchen floor* (weekly) ·
*Recycling* (Tuesdays) · *Hallway vacuum* (fortnightly)

Then a dashed row **+ Something else**. Pinned: filled **Add 4 chores** (count
follows the selection) and a **Skip** text button. Nothing selected → the
primary button is disabled and Skip carries the weight.

### 5.23 Dark theme

Same layout everywhere. Cards `#181A24` with a `#2A2E3F` hairline and no
shadow; background `#0F1117`; primary `#A78BFA` — the same hue lifted for
contrast, never a different colour; overdue `#F59E0B`. The bottom bar is
`#181A24` with a `#2A2E3F` top hairline and a `#2A2140` active indicator.
Re-check every amber and violet pairing for 4.5:1; the amber banner needs the
`#3A2A0A` container behind it.

---

## 6. Accessibility

- Contrast 4.5:1 for body text, 3:1 for large text and for the outline of any
  meaningful control. A new grey is measured, not guessed.
- **Colour is never the only signal.** Overdue is amber *and* the word "Due"
  with a date. Done is a filled checkbox *and* a "done by" line *and* a section
  header. A calendar dot is never the only carrier of meaning — the day's rows
  below the grid say it in words, and every cell's content description reads
  *"10 September, 2 turns"*.
- Bottom-bar items announce their selected state and never rely on the
  indicator alone.
- Every icon-only control has a description: refresh, overflow, the circled
  `?`, copy, the drag handle, the avatar camera badge, the month arrows.
- Drag reordering keeps its **Move up** / **Move down** accessibility actions.
- Text scales to 200% without clipping; the three-line row wraps rather than
  truncating. The day strip scrolls horizontally at large text sizes rather
  than compressing below 44dp.
- Touch targets ≥48dp, including the × on a starter row and the month-grid
  cells.

## 7. What this needs from the backend

The design is ahead of the API in five places. None blocks drawing the screens;
all block shipping them.

| Screen | Needs | Today |
|---|---|---|
| Profile photo | Upload, store, serve an avatar; a URL on the user and on member rows | Nothing exists |
| Profile | `PATCH /me` accepting `username`, `display_name`, `email` | `PATCH /me` takes quiet hours only |
| Change password | `POST /auth/password` with the current password | Nothing exists |
| Reminders | Per-user and per-household reminder preferences | Quiet hours only; reminders are device-side |
| My week | `GET /me/turns` — my open occurrences and one-offs across every household, in date order | Would need one request per household, then a client-side merge |

Until `GET /me/turns` exists, My week can be built on the existing per-household
calls with a client-side merge; it is correct, just chatty. Do not let that
shape the design.

Also outstanding, from `PROPOSALS.md`, and both visible in this design:
**the owner cannot leave a household they created** (5.14 states the reason
rather than failing), and **a rotation edit silently voids a turn somebody
owed** (5.21 should eventually warn).

## 8. What to build first

1. **The shell and the bottom bar** — it changes every screen's padding and the
   back stack, so nothing else should be drawn on the old IA.
2. **My week, list mode.** The whole value of the new destination is here; the
   grid is optional polish.
3. **Settings home, Appearance, Reminders.** They already have working
   backends; moving them out of two overflow menus is pure gain.
4. **Board hierarchy** — title/support contrast, 24dp sections, 96dp bottom
   padding.
5. **Households, invitations, manage** — the old group list's real job.
6. **Profile** — blocked on the API above; design it, ship it when the
   endpoints land.
7. **My week, calendar mode**, with the projected rings and the no-date chip.
8. **Activity feed vocabulary** — no raw event names ever reaching the screen.

## 9. Review checklist

- [ ] Nothing red except the Delete confirm button.
- [ ] No bar, ring, percentage, count of lateness, or ordering by amount.
- [ ] My week renders no other person, anywhere, in any mode.
- [ ] The privacy note appears wherever a screen is private or a change is
      visible to others.
- [ ] Primary violet used exactly once as *the* action on the screen.
- [ ] At most one amber element per row, on the line carrying the date.
- [ ] Section headers, row titles and support lines are three distinct weights.
- [ ] 96dp bottom padding under every scrolling list; the FAB clears the bar.
- [ ] Every empty state is one sentence plus one action.
- [ ] Every destructive confirmation names exactly what is lost.
- [ ] Every privacy promise in the copy is true in the data.
- [ ] Tap targets ≥48dp; text at 200% does not clip.
- [ ] The screen still reads calmly with a full board and long names.
