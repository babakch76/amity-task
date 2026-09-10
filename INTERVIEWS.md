# TaskFlow user interviews (prototype feedback, about 3 minutes each)

Written 2026-09-07 against the app as documented in [`FEATURES.md`](FEATURES.md)
and the 31 screenshots in [`screens/`](screens/). Participants do **not** have
an Android phone, so each interview is run over the screenshot deck
([`TaskFlow_Screens.pptx`](TaskFlow_Screens.pptx)) or the PNGs in `screens/`,
and the participant gets a **cue card** (section 3) so they have something
concrete to react to.

---

## 1. How to run it

- **Length:** five questions, about three minutes of recorded talk. Hard stop at 3:30.
- **People:** one interviewer (**I**), one participant (**P**).
- **Participant packs:** `interviews/TaskFlow-Interview-A-Participant-Pack.pdf` and `-B-`, sent before the call. Same five questions in both; only the example answers differ. Regenerate with `python3 interviews/build-participant-packs.py`.
- **Setup:** laptop or tablet showing the screenshots. Open them in this order
  and pause on each for a few seconds:
  1. `01-login.png` (sign in)
  2. `03-dashboard.png` (My Groups)
  3. `29-starters.png` (first run, five suggested chores)
  4. `04-board.png` (the board: YOURS / OTHERS / DONE)
  5. `06-occurrence-detail.png` (chore sheet with "Busy? Pass it on")
  6. `08-create-step1.png` and `10-create-step2.png` (adding a chore)
  7. `17-activity.png` (activity feed) and `19-group-history.png` ("What's been done")
- **Before recording:** hand the participant the cue card and give them ~30
  seconds to read it. That time is not part of the 2–3 minutes.
- **Consent:** read the consent lines (section 2) **on the recording**, get a
  verbal yes, then start the questions.
- **Rule for the interviewer:** ask, then stay quiet. Do not defend the app.
  If P says nothing, point at a cue card item and ask "what about this one?"

---

## 2. Consent script (read aloud, on the recording)

> Hi, thanks for doing this. I am going to ask you a few questions about a
> prototype of a household chore app called TaskFlow. It takes about two to
> three minutes.
>
> I would like to record the audio so I can write up what you said accurately.
> The recording is only used for this university project, it is not shared
> outside the team, and you will not be named in the report. You can skip any
> question, and you can stop at any time, and if you stop I will delete the
> recording.
>
> Is it okay if I record? **[wait for a clear "yes"]**
>
> Great. For the record, today is [date], and you have agreed to be recorded.

---

## 3. Cue card for the participant

Give this to P before recording. It is a prompt list, not a script: they should
pick the ones that ring true and ignore the rest.

### What the app is (one paragraph)

A chore board for people who share a home: three or four housemates, a handful
of chores that repeat. It answers one question: **whose turn is it, and has it
been done?** You sign in, open your household, and see three lists: **YOURS**,
**OTHERS**, and **DONE THIS CYCLE**. Tick a chore when it is done. If you are
busy you can **pass** your turn to the next person, and it comes back to you
next time round.

### Things people tend to like (good points)

- **Board is split by person, not by status.** You see *your* chores first,
  then everyone else's, then what is already done.
- **Anyone can tick anything.** If you did the bins yourself, you tick it, and
  the app records who really did it.
- **"Busy? Pass it on."** One button hands your turn to the next person and
  explains, in one sentence, that it comes back to you next time.
- **Undo for ten minutes** after ticking something by mistake.
- **New household starts with five suggested chores** (Trash, Bathroom,
  Kitchen floor, Recycling, Hallway vacuum) instead of an empty screen. You
  rename or remove any of them.
- **Adding a chore is two short screens**, and each option has a **?** that
  explains it. Repeat intervals are big tap buttons (1, 2, 3 … 7, 14, 30 days).
- **"What done means"**: each chore can carry one sentence about what counts
  as finished, so it is not argued every time.
- **Overdue is amber, never red.** No points, no streaks, no leaderboard, no
  shaming.
- **Quiet hours** for reminders, and **Light / Dark / System** appearance.
- **Activity feed in plain words**: "maya did a chore", "sam joined the group".

### Things people tend to find missing or awkward (bad points)

- **Straight to Sign In.** First screen is email + password. No way to peek
  inside or try a demo before making an account.
- **No Google / Apple sign-in.** Register asks for username, email, password.
  No "forgot password" either.
- **"Sign in to your workspace"** sounds like an office tool, not a home.
- **Everything is the same purple.** No colour per chore or per person, so the
  board reads as a wall of text. Icons for chores (bin, shower, plant) could
  help scanning.
- **Adding a chore means typing the name every time.** The five suggestions
  only appear when a household is *first* created, not later when you tap
  "Add chore". A pick-list of common chores would be faster.
- **No calendar.** You cannot see the whole week or month in one glance.
- **No timeline of who is up next.** The board shows *today's* holder only,
  not "after me it is Sam, then Maya".
- **Swipe on a row feels odd.** Swiping looks like it will delete the row, but
  the row springs back and passes the chore instead.
- **Settings are hidden** in the three-dot menu on the groups list (Quiet
  hours, Appearance, Join with a code). Not obvious they exist.
- **History is two taps deep.** You have to open a chore, then tap History.
- **No push notifications.** Reminders come only from the phone's own alarms.
- **No photos or notes** on a chore (for example "the hoover bag is in the
  cupboard").
- **Household admin is thin.** You cannot remove a housemate who moved out,
  the creator cannot leave while others remain, and the household name and
  description cannot be edited after creation.
- **"What's been done" shows counts only**, no chart, no "scheduled vs done".
  Some people want the comparison, others find that fairer.

---

## 4. Interview A — student in a shared flat (target: 3:00)

Persona: **P** shares a flat with two others, uses group chats and a fridge whiteboard today. Example answers are written from that home.

Same five questions as the other interview. P's answers are illustrative; real answers will differ, and that is the point.

---

**[0:00] I:** [consent script from section 2, get the "yes"]

**[0:30] I:** Look at the board (screen 3) as if you had never seen the app. Do you understand what it is telling you and what to do next, or would someone have to show you? What exactly would need explaining?

**P:** Mostly yes. YOURS at the top, “Clean the bathroom, weekly, due Thu 10 Sep”, tells me what I owe and when. What I would not get alone: is the checkbox “I did it” or “I claim it”? And “maya covered your last turn, so it’s yours again” took two reads. Nobody finds swiping without the hint. I could use it alone, but the passing rules I would want a flatmate to explain once.

**[1:04] I:** The app wants an account before it shows anything, then offers five suggested chores. Setting it up in your home for real: who creates it, how do the others join, and where does it stall?

**P:** The organised flatmate creates it. The other two must install it, invent a username, email and password, and type a code. That is the stall: my flatmates ignore anything needing a new password for a week. A Google button or a link straight into the household would fix it. The five suggestions are good: keep Bathroom and Trash, rename Kitchen floor to Kitchen, drop Hallway vacuum, we have no hallway.

**[1:38] I:** Think of your actual week. Which chores would you really put in, and where would the app cost more effort than a whiteboard or a group chat?

**P:** Bathroom, kitchen, bins, recycling, plus toilet paper as “as needed”. Adding them once is fine. The one-offs annoy me: “call the landlord” I would just say in the group chat, typing it into an app is slower. Where it beats the whiteboard is memory: the whiteboard never remembers I did it early last week. Where it loses: the whiteboard is on the fridge, I never have to open it.

**[2:12] I:** The board only shows who holds each chore right now, no calendar, no upcoming turns. When would you need to see further ahead, and what should that view look like?

**P:** Sunday evening, when I plan the week around a deadline. Right now I would open each chore one by one. A week strip at the top, Monday to Sunday with a coloured dot per person, is enough, I do not need a month view for chores. And on the chore, show “next: sam, then maya”, so when I pass I see who I am handing it to before I tap.

**[2:46] I:** Deliberate choices: anyone can tick anything, passing gives the other person the credit, no scores, no red, no ranking. In your home, is that fairer, or does it let people hide?

**P:** Fairer, mostly. Anyone can tick means when I empty the overflowing bin I get the credit and nobody argues whose turn it was. No red and no ranking is right for a flat, a leaderboard would cause a fight by week two. Where people hide: passing is silent, so one person can pass every time and it looks normal. Not a counter, but the feed should say “sam passed the bathroom to maya”.

**[3:20] I:** That's everything. Thank you very much, I'm stopping the recording now.

---

## 5. Interview B — partner or family household (target: 3:00)

Persona: **P** lives with a partner and one other adult, not very "app-y", cares about not typing much and seeing the whole picture. Example answers are written from that home.

Same five questions as the other interview. P's answers are illustrative; real answers will differ, and that is the point.

---

**[0:00] I:** [consent script from section 2, get the "yes"]

**[0:30] I:** Look at the board (screen 3) as if you had never seen the app. Do you understand what it is telling you and what to do next, or would someone have to show you? What exactly would need explaining?

**P:** The board I understand: my things, their things, done. Plain words, “Thursdays”, “every 3 days”, no jargon. What I would need showing: how a chore gets into OTHERS at all, who decided it is maya’s turn, and what “Add chore” will ask. My partner would not read the small grey lines, they would tap the box and hope. The board explains itself, the setup does not.

**[1:04] I:** The app wants an account before it shows anything, then offers five suggested chores. Setting it up in your home for real: who creates it, how do the others join, and where does it stall?

**P:** I would set it up on a Sunday with the other two there, so we agree the list. The five suggestions turn a blank page into a conversation: “does recycling count as a chore?” Keep four, add Cooking. The stall is accounts: three emails, three passwords, one of us forgets theirs within a month, and there is no “forgot password”. A shared code that just works decides whether we use it at all.

**[1:38] I:** Think of your actual week. Which chores would you really put in, and where would the app cost more effort than a whiteboard or a group chat?

**P:** Kitchen daily, bathroom weekly, bins Thursday, laundry, the cat. Adding them once is fine, the day buttons are quick, and writing what “done” means stops the argument over whether wiping the counter counts. The cost is typing every new chore, and that suggestions appear only on day one. The app wins on memory, the whiteboard wins on being visible all day without opening anything.

**[2:12] I:** The board only shows who holds each chore right now, no calendar, no upcoming turns. When would you need to see further ahead, and what should that view look like?

**P:** When someone is away. If my partner travels next week I need to see what lands on me, and today I would find out when it arrives. A month view with the fixed-day chores is enough, interval ones can say “around the 12th”. And show the order: me now, who next, who after. With three people it is short, but it kills the “I thought it was your turn” conversation.

**[2:46] I:** Deliberate choices: anyone can tick anything, passing gives the other person the credit, no scores, no red, no ranking. In your home, is that fairer, or does it let people hide?

**P:** No scores is right for us, we are not children, and a red badge would feel like being told off by a phone. Anyone can tick is good, real life is messy. Passing is where I hesitate: the coverer gets the credit, fine, but then “What’s been done” shows my partner did more and not how much was covering for me. Honest numbers, not the whole story. Add one line: “of which 2 were covering”.

**[3:20] I:** That's everything. Thank you very much, I'm stopping the recording now.

---

## 6. After the interview (interviewer only, off the recording)

Write these down within five minutes, while it is fresh:

| | |
|---|---|
| Participant code | A1 / A2 / B1 / B2 … (never the name) |
| Household type | shared flat / partner / family / other |
| Good points they raised **unprompted** | |
| Good points they only agreed with **from the cue card** | |
| Bad points they raised **unprompted** | |
| Bad points they only agreed with **from the cue card** | |
| One thing they would add | |
| One thing they would remove | |
| Anything that contradicts a design rule (see FEATURES.md section 8)? | e.g. asked for leaderboard, red overdue, calendar |

The last row matters most for the report: when a participant asks for
something the design deliberately refuses (rankings, red, calendar), that is a
finding in itself, whether or not it gets built.
