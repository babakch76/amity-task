# CLAUDE.md — TaskFlow

Guidance for Claude Code working in this repository. Written 2026-09-10 against
`82fa394` on branch `mehrdad`.

---

## 1. What this is

**TaskFlow v2 is a household chore-rotation app.** Three or four people sharing
a flat, a handful of recurring chores, and one question: *whose turn is it, and
has it been done?*

It is **not** a task manager with a household skin. v1 was; the pivot is
finished. If something reads like project management (priorities, subtasks,
comment threads, kanban statuses, a calendar), it is either legacy or wrong.

Academic context: HCI course project, Sapienza. Design decisions are defended in
documents, not just in code — which is why the doc set below is unusually large
and why "why not" is recorded as carefully as "what".

## 2. Repository layout

```
taskflow/
├── TaskFlowApp-kotlin/       Android client — Kotlin, Jetpack Compose, Material 3
├── task-manager-backend-GO/  Go backend — one binary, SQLite (WAL), JWT
├── screens/                  31 PNG screenshots of the real build (device-captured)
├── interviews/               User-interview packs (participant PDFs + scripts)
├── index.html / app.js / index.css   ⚠️ PRE-PIVOT WEB PROTOTYPE — NOT the app.
│                             Different product ("Joint Task Management",
│                             projects, deadline calendar). Nothing builds it.
│                             Never read it as documentation.
└── *.md                      See doc map below
```

### Doc map — read in this order

| File | What it is |
|---|---|
| `FEATURES.md` | **What the app actually does**, verified against the build. Start here. |
| `taskflow_feature_spec_2.md` | The spec — what was asked for, plus 8 hard constraints. |
| `V2-HANDOFF.md` | Working state: deployed, half-done, and what will cost hours. |
| `BACKLOG.md` | Why things are the way they are (67 KB of decision records). |
| `PROPOSALS.md` | Six changes raised at handover; two are defects, not improvements. |
| `calendar_decision_record.md` | Why there is no calendar, and why not to add one. |
| `DESIGN.md` | **Target** design: design system + screen-by-screen UI/UX spec. Ahead of the code, but less so as of 2026-09-13: §5.1–5.2 (Sign in, Create account) are built, and so are the bottom bar and a real Settings tab — with two departures from §4.2/§5 that `BACKLOG.md` records. My week's calendar and day-strip modes are still unbuilt. |
| `INTERVIEWS.md` | User-interview scripts and timing. |
| `journal.md` | Running session log. Append, don't rewrite. |

Where `FEATURES.md` and the spec disagree, **the app follows `FEATURES.md`** and
`BACKLOG.md` records why.

## 3. The eight hard constraints (non-negotiable)

Violating one of these is a bug, no matter how good the feature is.

1. No peer ratings, likes, comments, or quality feedback.
2. No user-to-user nag actions; no notification ever names another member as late.
3. **No red overdue states, late counters, or shame badges** — amber or dim at
   most. `colorScheme.error` must never appear where overdue is rendered.
4. No points, streaks, leaderboards, percentages, or gamification.
5. No calendar integration or free/busy scheduling.
6. Household scope only — no priorities, no subtasks, no comment threads.
7. Every group-visible change is broadcast; **a busy pass notifies only the
   receiver** and writes nothing to the activity feed; nothing about individual
   lateness is ever pushed.
8. Busy pass and away are the **only** exits from an assigned chore besides
   doing it. No unassigned chores, no claim pool, no directed swaps.

## 4. Domain model — the parts that trip people up

- **Chore** = a definition (name, schedule, rotation list, done-line).
  **Occurrence** = one cycle of it, assigned to exactly one person.
  **Task** = a legacy one-off item, predating chores. The board shows both;
  rotation rules apply only to occurrences.
- Occurrence states are **`open → done` only**. There is no "missed" state
  anywhere in the system. (`in_progress` is v1 vocabulary, on the way out.)
- **Four schedule kinds:** `interval` (N days from the last completion),
  `fixed_date` (weekday or day-of-month; the pattern holds regardless of when it
  was done), `as_needed` (no date at all), `one_off` (assigned once, never
  recurs).
- **The turn rule:** a chore done by anybody other than the person whose turn it
  was counts as **the doer's** turn. The next one goes back to the person who
  owed it, and rotation resumes **after the doer**. A cover is a one-cycle swap,
  not a favour and not a compounding debt.
- **Every passer owes one**, repaid oldest first; a person is owed at most one
  turn per chore however many times they passed.
- **Away holds a debt**, does not cancel it. **A debt belongs to a person, not a
  position** — reordering a rotation does not move it; leaving voids it.
- **Everyone busy** → the chore returns to whoever asked first, dated no later
  than its natural next date, and that person may pull the date earlier only.

## 5. Stack and commands

### Android — `TaskFlowApp-kotlin/`

Kotlin 2.0, AGP 8.4, **JDK 17** (not 21+), minSdk 26, compileSdk 34.
Retrofit + OkHttp + Gson; JWT in EncryptedSharedPreferences. **No DI framework** —
`TokenManager` and `RetrofitClient` are created once in `TaskFlowApp.onCreate()`.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # plain JVM tests, no emulator
```

Base URL comes from `taskflow.baseUrl` in `local.properties` (emulator default
`http://10.0.2.2:8080/`). It is a `buildConfigField`, so the compiler **inlines**
it: after changing it you must `./gradlew clean assembleDebug`, and a physical
device also needs its IP in `res/xml/network_security_config.xml`. Reading
`BuildConfig.java` proves nothing — verify from Logcat's `okhttp` output.

### Backend — `task-manager-backend-GO/`

Go 1.22+, CGO required (SQLite). Migrations run on every start.

```bash
go run ./cmd/server            # :8080, SQLite at ./taskmanager.db
go vet ./... && go test ./...
```

Deployed on one EC2 box: Caddy terminates TLS on 443 and proxies to
`127.0.0.1:8080`. JWT middleware wraps every route, so even an unknown path
answers `401`.

## 6. Screen map (Android)

| Screen | Role |
|---|---|
| `LoginScreen` / `RegisterScreen` | Auth, text-button toggle between them. Register signs you straight in. Both are a stateful screen plus a stateless `LoginContent` / `RegisterContent` — edit the layout there, and the `@Preview`s at the foot of each file are the design surface. |
| `MainShell` | The bottom bar and the three tabs below it. Owns the shared `ShellViewModel`, which household is open, the reminder alarms and the invite poll. |
| `HouseholdTab` — **My groups** | Decides *which* household is open, handles having none, carries create + first-run starters. The board itself is still `GroupDetailScreen`. |
| `GroupDetailScreen` (2.6k lines) | One household's board: Board / Activity / Members tabs, plus every sheet and dialog. A root destination — no back arrow, bar underneath. |
| `MyChoresScreen` — **My chores** | Your own turns across every household, grouped by household. Reuses the board's `TaskCard`. |
| `SettingsScreen` — **Settings** | Name, email, invitations, join with a code, quiet hours, appearance, sign out. |
| `ui/components/HouseholdSwitcher` | The board's title: household name + chevron, dropping a menu of households plus *Create a household* / *Join with a code*. |
| `AccountDialogs.kt` | The four dialogs that outlived the group list — invites, join by code, create household, quiet hours. Was `DashboardScreen.kt`. |
| `CreateChoreFlow` | Add or edit a chore as two full-screen steps; delete lives at the end of edit. |
| `FirstRunStarters` | Five starter chores offered to a brand-new household. |

Navigation is **three outer routes**: `login`, `register`, `shell`, plus a
nested graph of three inside the shell (`household`, `mychores`, `settings`).
Everything else is a sheet or dialog.

**There is no group list and no `group/{groupId}` route.** The app opens on the
board of the household you last had open (`data/local/LastHousehold`, on the
device), and the switcher in its app bar changes it. Three things follow:

- `ShellViewModel` is created by `MainShell` and passed down to all three tabs
  by hand. A tab that calls `viewModel()` for its own copy gets a second
  instance and a second set of requests.
- Which household is open is held by `MainShell` too, because **My chores**
  writes it as well as the switcher — tapping a turn there switches the board
  rather than opening a second one.
- `GroupDetailViewModel` is obtained with `viewModel(key = groupId, …)`.
  Without the key, switching household keeps the previous household's ViewModel
  and leaves its chores on screen under the new name.

**My chores** is not DESIGN.md's My week: it is the List half, without the
calendar and day-strip modes. `BACKLOG.md` records why, and the calendar half is
a question rather than a to-do — read `calendar_decision_record.md` first.

The two auth screens share their top half — emblem, wordmark, subtitle, field
shape, pill button, amber error line — through `ui/components/AuthScaffold.kt`.
Change the shape of an auth field there, not in one screen, or the two drift.
The emblem is `R.drawable.ic_launcher_foreground`, drawn at 72dp and clipped to
a 56dp tile because it is an adaptive-icon foreground whose mark only occupies
the middle. That drawable still calls itself a placeholder; it is now on the
first screen of the app, not just the launcher.

Both screens were ported from Google Stitch mocks (MCP server `stitch`, project
**Core Design System** `4515006561005969934`, screens *Sign in* and *Create
account*), which draw `DESIGN.md` §5.1–5.2. Two deliberate departures: the
mock's **Forgot?** link is not built — there is no password-reset route on the
backend — and the register password placeholder is *"Create a password"* rather
than the mock's bullets, which TalkBack reads aloud as bullets.

## 7. Theme

Defined in `ui/theme/Theme.kt` as a Material 3 `ColorScheme`, **not** in
`colors.xml` (that holds only the cold-start window background).

| Token | Light | Dark |
|---|---|---|
| primary | `#7C3AED` violet-600 | `#A78BFA` violet-400 |
| secondary | `#60A5FA` | `#60A5FA` |
| tertiary | `#34D399` | `#34D399` |
| background | `#F8F9FC` | `#0F1117` |
| surface | `#FFFFFF` | `#181A24` |
| onSurface | `#1A1B2E` | `#EEF0F6` |

**Material You dynamic colour is deliberately off.** A wallpaper-derived scheme
can put a red primary next to the amber overdue colour, which reads as exactly
the shame signal constraint 3 rules out. Do not turn it back on.

**The `…Container` roles are not ours.** The table above is the whole of what
`Theme.kt` sets, so `primaryContainer`, `tertiaryContainer`, `errorContainer`
and the rest fall through to Material's baseline scheme — in which
`tertiaryContainer` is a **dark red**. Reaching for one to tint something is how
a red disc ended up beside the chore rows in the household switcher
(2026-09-13), with nobody having chosen it. Tint from `primary` / `secondary` /
`tertiary` at low alpha, or define the container roles properly before using
them.

Appearance (System/Light/Dark) is stored in SharedPreferences **on the device**,
never synced — it is a property of this phone, not of the account or household.

## 8. Landmines

- **PATCH tri-state.** `UpdateTaskRequest.assignedTo` / `.dueDate` are
  `Patchable<String>`: `Absent` (key omitted), `SetNull` (`null`), `Value`.
  Serialised by a hand-written `UpdateTaskRequestTypeAdapter`, never reflection —
  a reflective adapter turned absent fields into explicit nulls and silently
  cleared assignees and deadlines. An all-absent request serialises to `{}` and
  the backend answers `400 no fields to update`; skip the call instead.
- **`PATCH /me` is stricter than register.** It parses the email address and
  rejects a display-name form; register accepts any non-empty string. Dev
  accounts created before 2026-09-13 can hold addresses the endpoint would now
  refuse. Username is trimmed and capped at 32. A taken name or address is a
  **409**, not a 400.
- **Debug `BODY` logging** includes the plaintext password on login/register.
  Drop to `BASIC` before sharing a Logcat.
- **Release signing** needs a keystore configured in `local.properties`;
  `assembleRelease` without it silently emits an uninstallable unsigned APK.
- **Git identity:** this repo is `babakch76/taskflow`. The work account has no
  push access — the remote is the `github-personal` SSH host alias and commits
  are authored as the personal account.

## 9. Working style here

- Prefer the existing document set over inventing new ones; append to
  `journal.md` rather than starting parallel logs.
- When removing something, record *why* in `BACKLOG.md` — non-goals are treated
  as evidence of maturity in the report.
- Screenshots in `screens/` are captured from the real build; if the UI changes,
  they are stale and should be retaken (V2-HANDOFF phase 4b).
