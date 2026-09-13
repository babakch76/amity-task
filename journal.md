# journal.md — TaskFlow working log

Append-only. Newest entry at the top. One entry per working session: what was
done, what was learned, what is open. Decisions that outlive a session graduate
to `BACKLOG.md`; state that outlives a session graduates to `V2-HANDOFF.md`.

---

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
