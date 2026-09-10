#!/usr/bin/env python3
"""Build the two participant packs (HTML + PDF) for the TaskFlow interviews.

Run from anywhere:  python3 interviews/build-participant-packs.py
Needs Google Chrome for the PDF step; the HTML is written regardless.
"""
import base64, html, os, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCREENS = ROOT / "screens"
OUT = ROOT / "interviews"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"

def img(name):
    data = base64.b64encode((SCREENS / name).read_bytes()).decode()
    return f"data:image/png;base64,{data}"

SCREENS_ORDER = [
    ("01-login.png", "1. Sign in", "The first screen. Email and password, or a link to create an account."),
    ("29-starters.png", "2. New household", "A brand-new household is offered five suggested chores instead of an empty page."),
    ("04-board.png", "3. The board", "Split into YOURS, OTHERS and DONE THIS CYCLE. Each row: what, how often, when, whose turn."),
    ("06-occurrence-detail.png", "4. One chore", "Tap a row: what done means, how often, Edit, History, and “Busy? Pass it on”."),
    ("08-create-step1.png", "5. Add a chore, step 1", "Name it, then say how often. Each option has a ? that explains it."),
    ("10-create-step2.png", "6. Add a chore, step 2", "Pick the number of days with one tap, then drag people into turn order."),
    ("17-activity.png", "7. Activity", "The household’s feed, in plain words."),
    ("19-group-history.png", "8. What’s been done", "A count per person for the week, month or three months. No ranking."),
]

GOOD = [
    ("Board is split by person, not by status.", "You see your chores first, then everyone else’s, then what is already done."),
    ("Anyone can tick anything.", "If you did the bins yourself, you tick it, and the app records who really did it."),
    ("“Busy? Pass it on.”", "One button hands your turn to the next person and says, in one sentence, that it comes back to you next time."),
    ("Undo for ten minutes", "after ticking something by mistake."),
    ("Five suggested chores for a new household", "(Trash, Bathroom, Kitchen floor, Recycling, Hallway vacuum). Rename or remove any."),
    ("Adding a chore is two short screens.", "Repeat intervals are big tap buttons (1, 2, 3 … 7, 14, 30 days). Each option has a ? that explains it."),
    ("“What done means”", "one agreed sentence per chore about what counts as finished, so it is not argued every time."),
    ("Overdue is amber, never red.", "No points, no streaks, no leaderboard, no shaming."),
    ("Quiet hours for reminders,", "and Light / Dark / System appearance."),
    ("Activity feed in plain words:", "“maya did a chore”, “sam joined the group”."),
]

BAD = [
    ("Straight to Sign In.", "First screen is email + password. No way to peek inside or try a demo before making an account."),
    ("No Google or Apple sign-in.", "Register asks for username, email and password. No “forgot password” either."),
    ("“Sign in to your workspace”", "sounds like an office tool, not a home."),
    ("Everything is the same purple.", "No colour per chore or per person, so the board reads as a wall of text. Icons (bin, shower, plant) could help."),
    ("Adding a chore means typing the name every time.", "The five suggestions only appear when a household is first created, not later under “Add chore”."),
    ("No calendar.", "You cannot see the whole week or month in one glance."),
    ("No timeline of who is up next.", "The board shows today’s holder only, not “after me it is Sam, then Maya”."),
    ("Swipe on a row feels odd.", "It looks like it will delete the row, but the row springs back and passes the chore instead."),
    ("Settings are hidden", "in the three-dot menu (Quiet hours, Appearance, Join with a code)."),
    ("History is two taps deep.", "Open a chore, then tap History."),
    ("No push notifications.", "Reminders come only from the phone’s own alarms."),
    ("No photos or notes on a chore", "(for example “the hoover bag is in the cupboard”)."),
    ("Household admin is thin.", "You cannot remove a housemate who moved out, the creator cannot leave while others remain, and the household name cannot be edited."),
    ("“What’s been done” shows counts only,", "no chart, no “planned vs done”. Some want the comparison, others find counts fairer."),
]

QUESTIONS = [
    "Look at the board (screen 3) as if you had never seen the app. Do you understand what it is telling you and what to do next, or would someone have to show you? What exactly would need explaining?",
    "The app wants an account before it shows anything, then offers five suggested chores. Setting it up in your home for real: who creates it, how do the others join, and where does it stall?",
    "Think of your actual week. Which chores would you really put in, and where would the app cost more effort than a whiteboard or a group chat?",
    "The board only shows who holds each chore right now, no calendar, no upcoming turns. When would you need to see further ahead, and what should that view look like?",
    "Deliberate choices: anyone can tick anything, passing gives the other person the credit, no scores, no red, no ranking. In your home, is that fairer, or does it let people hide?",
]

PACKS = {
    "A": dict(
        code="A",
        who="someone who shares a flat with housemates",
        answers=[
            "Mostly yes. YOURS at the top, “Clean the bathroom, weekly, due Thu 10 Sep”, tells me what I owe and when. What I would not get alone: is the checkbox “I did it” or “I claim it”? And “maya covered your last turn, so it’s yours again” took two reads. Nobody finds swiping without the hint. I could use it alone, but the passing rules I would want a flatmate to explain once.",
            "The organised flatmate creates it. The other two must install it, invent a username, email and password, and type a code. That is the stall: my flatmates ignore anything needing a new password for a week. A Google button or a link straight into the household would fix it. The five suggestions are good: keep Bathroom and Trash, rename Kitchen floor to Kitchen, drop Hallway vacuum, we have no hallway.",
            "Bathroom, kitchen, bins, recycling, plus toilet paper as “as needed”. Adding them once is fine. The one-offs annoy me: “call the landlord” I would just say in the group chat, typing it into an app is slower. Where it beats the whiteboard is memory: the whiteboard never remembers I did it early last week. Where it loses: the whiteboard is on the fridge, I never have to open it.",
            "Sunday evening, when I plan the week around a deadline. Right now I would open each chore one by one. A week strip at the top, Monday to Sunday with a coloured dot per person, is enough, I do not need a month view for chores. And on the chore, show “next: sam, then maya”, so when I pass I see who I am handing it to before I tap.",
            "Fairer, mostly. Anyone can tick means when I empty the overflowing bin I get the credit and nobody argues whose turn it was. No red and no ranking is right for a flat, a leaderboard would cause a fight by week two. Where people hide: passing is silent, so one person can pass every time and it looks normal. Not a counter, but the feed should say “sam passed the bathroom to maya”.",
        ],
    ),
    "B": dict(
        code="B",
        who="someone who lives with a partner or family",
        answers=[
            "The board I understand: my things, their things, done. Plain words, “Thursdays”, “every 3 days”, no jargon. What I would need showing: how a chore gets into OTHERS at all, who decided it is maya’s turn, and what “Add chore” will ask. My partner would not read the small grey lines, they would tap the box and hope. The board explains itself, the setup does not.",
            "I would set it up on a Sunday with the other two there, so we agree the list. The five suggestions turn a blank page into a conversation: “does recycling count as a chore?” Keep four, add Cooking. The stall is accounts: three emails, three passwords, one of us forgets theirs within a month, and there is no “forgot password”. A shared code that just works decides whether we use it at all.",
            "Kitchen daily, bathroom weekly, bins Thursday, laundry, the cat. Adding them once is fine, the day buttons are quick, and writing what “done” means stops the argument over whether wiping the counter counts. The cost is typing every new chore, and that suggestions appear only on day one. The app wins on memory, the whiteboard wins on being visible all day without opening anything.",
            "When someone is away. If my partner travels next week I need to see what lands on me, and today I would find out when it arrives. A month view with the fixed-day chores is enough, interval ones can say “around the 12th”. And show the order: me now, who next, who after. With three people it is short, but it kills the “I thought it was your turn” conversation.",
            "No scores is right for us, we are not children, and a red badge would feel like being told off by a phone. Anyone can tick is good, real life is messy. Passing is where I hesitate: the coverer gets the credit, fine, but then “What’s been done” shows my partner did more and not how much was covering for me. Honest numbers, not the whole story. Add one line: “of which 2 were covering”.",
        ],
    ),
}

CSS = """
@page { size: A4; margin: 16mm 16mm 18mm 16mm; }
* { box-sizing: border-box; }
body { font-family: -apple-system, "Helvetica Neue", Helvetica, Arial, sans-serif; color: #1c1b2e; font-size: 10.5pt; line-height: 1.45; margin: 0; }
h1 { font-size: 24pt; margin: 0 0 2mm; color: #6b3af0; letter-spacing: -0.5px; }
h2 { font-size: 14pt; margin: 8mm 0 3mm; padding-bottom: 1.5mm; border-bottom: 2px solid #e6e1f7; color: #2b2450; break-after: avoid; }
h3 { font-size: 11pt; margin: 4mm 0 1.5mm; color: #2b2450; break-after: avoid; }
p { margin: 0 0 2.5mm; }
.sub { color: #5a5670; font-size: 11pt; margin-bottom: 6mm; }
.meta { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 3mm; margin: 4mm 0 6mm; }
.meta div { background: #f4f2fb; border-radius: 3mm; padding: 3mm 4mm; }
.meta b { display: block; font-size: 8.5pt; color: #6b3af0; text-transform: uppercase; letter-spacing: .5px; margin-bottom: 1mm; }
.box { border: 1px solid #d9d3f2; border-radius: 3mm; padding: 4mm 5mm; margin: 3mm 0; background: #fbfaff; break-inside: avoid; }
.consent li { margin-bottom: 1.5mm; }
.sig { margin-top: 5mm; display: grid; grid-template-columns: 1fr 1fr; gap: 6mm; }
.sig div { border-top: 1px solid #555; padding-top: 1.5mm; font-size: 9pt; color: #5a5670; }
.check { display: block; margin: 1.5mm 0; }
.check span { display: inline-block; width: 4mm; height: 4mm; border: 1.5px solid #6b3af0; border-radius: 1mm; vertical-align: -0.8mm; margin-right: 2mm; }
.shots { display: grid; grid-template-columns: repeat(4, 1fr); gap: 4mm 4mm; }
.shot { break-inside: avoid; }
.shot img { width: 100%; border: 1px solid #ddd; border-radius: 2.5mm; display: block; }
.shot b { display: block; margin-top: 1.5mm; font-size: 9pt; }
.shot small { color: #5a5670; font-size: 8pt; line-height: 1.35; display: block; }
ul.cue { padding-left: 4.5mm; margin: 0; }
ul.cue { font-size: 9.6pt; line-height: 1.38; } ul.cue li { margin-bottom: 1.4mm; }
ul.cue b { color: #2b2450; }
.two { display: grid; grid-template-columns: 1fr 1fr; gap: 5mm; }
.good h3 { color: #1b7f4b; } .bad h3 { color: #a05a00; }
ol.q li { margin-bottom: 2.2mm; break-inside: avoid; } .qq { font-weight: 600; color: #2b2450; } .ex { margin-top: .8mm; padding: 1.6mm 3mm; background: #f4f2fb; border-left: 2.5px solid #c9bdf5; border-radius: 0 2mm 2mm 0; font-size: 9.3pt; color: #3d395a; font-style: italic; } .ex span { display: block; font-style: normal; font-size: 7.5pt; text-transform: uppercase; letter-spacing: .5px; color: #6b3af0; margin-bottom: .5mm; }
.note { font-size: 9pt; color: #5a5670; }
.pb { break-before: page; }
footer { margin-top: 3mm; font-size: 8.5pt; color: #8a86a0; border-top: 1px solid #eee; padding-top: 2mm; }
"""

def li_pairs(items):
    return "\n".join(f"<li><b>{html.escape(a)}</b> {html.escape(b)}</li>" for a, b in items)

def build(pack):
    q = "\n".join(
        f'<li><div class="qq">{html.escape(qx)}</div><div class="ex"><span>Example answer</span>{html.escape(ax)}</div></li>'
        for qx, ax in zip(QUESTIONS, pack["answers"])
    )
    shots = "\n".join(
        f'<div class="shot"><img src="{img(f)}" alt="{html.escape(t)}"><b>{html.escape(t)}</b><small>{html.escape(c)}</small></div>'
        for f, t, c in SCREENS_ORDER
    )
    return f"""<!doctype html><html><head><meta charset="utf-8"><title>TaskFlow interview, participant pack {pack['code']}</title><style>{CSS}</style></head><body>

<h1>TaskFlow</h1>
<p class="sub">Participant pack {pack['code']} &middot; a three-minute conversation about a chore-app prototype</p>

<p>Thank you for agreeing to talk to us. TaskFlow is a student prototype of an Android app for people who share a home. You do not need an Android phone: this pack shows you the screens, and on the call we will look at them together. We are interested in your honest opinion, and there are no wrong answers. Anything you find confusing or missing is exactly what we want to hear.</p>

<div class="meta">
  <div><b>Where</b>Google Meet video call. The link is in the invitation.</div>
  <div><b>How long</b>Five questions, about three minutes, plus a minute for consent.</div>
  <div><b>Before the call</b>Please read page 2 (consent), then pages 3 and 4 (the screens and the cue card). Two minutes is enough.</div>
</div>

<h2>What the app is</h2>
<p>A chore board for people who share a home: three or four people and a handful of chores that repeat. It answers one question: <b>whose turn is it, and has it been done?</b> You sign in, open your household, and see three lists: <b>YOURS</b>, <b>OTHERS</b> and <b>DONE THIS CYCLE</b>. Tick a chore when it is done. If you are busy you can <b>pass</b> your turn to the next person, and it comes back to you next time round. There is a feed of what happened, and a per-person count of what has been done. On purpose there are no points, no rankings and no red warnings.</p>

<p class="note">The example answers in this pack are written from the point of view of {html.escape(pack['who'])}. The questions are the same for everyone.</p>

<h2 class="pb">Recording and consent</h2>
<div class="box consent">
<p>The call will be <b>recorded (video and audio) in Google Meet</b> so that we can write up what you said accurately. Please read the points below. At the start of the call the interviewer will ask you to confirm out loud, on the recording, that you agree.</p>
<ul>
  <li>The recording is used <b>only for this university project</b> at Sapienza University of Rome. It is not published and not shared outside the project team.</li>
  <li>In the written report you appear as a <b>participant code</b> (for example &ldquo;Participant {pack['code']}1&rdquo;), never by name. Short quotes may be used, without your name.</li>
  <li>You may <b>skip any question</b>, and you may <b>stop at any time</b> without giving a reason. If you stop, the recording is deleted.</li>
  <li>You may keep your <b>camera off</b> if you prefer. Audio is enough for us.</li>
  <li>You can ask for your recording to be <b>deleted</b> at any point after the call by messaging the interviewer.</li>
  <li>The recording is stored on the interviewer&rsquo;s account and deleted when the project is graded. Interviewer: Mehrdad Jafarzadeh, Sapienza University of Rome.</li>
</ul>
<p><b>On the call you will be asked:</b> &ldquo;Is it okay if I record this conversation?&rdquo; Saying <b>yes</b> on the recording is your consent. If you would also like to sign, you can fill in the lines below and send a photo or scan back, but this is optional.</p>
<label class="check"><span></span> I have read this page and I agree to take part and to be recorded.</label>
<label class="check"><span></span> I agree that short anonymous quotes may be used in the report.</label>
<div class="sig"><div>Name (optional)</div><div>Date and signature (optional)</div></div>
</div>

<h2 class="pb">The screens we will look at</h2>
<div class="shots">
{shots}
</div>

<h2 class="pb">Cue card: things other people have said</h2>
<p>Use this as a prompt, not a script. Pick the ones that ring true for <i>your</i> home, ignore the rest, and add your own.</p>
<div class="two">
<div class="good"><h3>Things people tend to like</h3><ul class="cue">{li_pairs(GOOD)}</ul></div>
<div class="bad"><h3>Things people find missing or awkward</h3><ul class="cue">{li_pairs(BAD)}</ul></div>
</div>

<h2 class="pb">The questions you will be asked</h2>
<p>Five questions, in this order, about half a minute each. Under each one is an <b>example answer</b> from another person, to show the depth we are after: specific, about your own home, and honest about what would not work. It is only an example. Agree, disagree, or say something completely different.</p>
<ol class="q">{q}</ol>

<footer>TaskFlow user interview &middot; participant pack {pack['code']} &middot; prepared 7 September 2026 &middot; prototype documented in FEATURES.md, screens from the screens/ folder</footer>
</body></html>"""

def main():
    OUT.mkdir(exist_ok=True)
    for code, pack in PACKS.items():
        html_path = OUT / f"TaskFlow-Interview-{code}-Participant-Pack.html"
        pdf_path = OUT / f"TaskFlow-Interview-{code}-Participant-Pack.pdf"
        html_path.write_text(build(pack), encoding="utf-8")
        if os.path.exists(CHROME):
            subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                            f"--print-to-pdf={pdf_path}", html_path.as_uri()],
                           check=True, capture_output=True)
            print("wrote", pdf_path.name)
        else:
            print("Chrome not found; wrote HTML only:", html_path.name, file=sys.stderr)

if __name__ == "__main__":
    main()
