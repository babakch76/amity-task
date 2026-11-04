package handlers

import (
	"fmt"
	"testing"
	"time"
)

// B-25: tapping Mark as done over and over.
//
// Two things used to happen. Each tap completed the newly spawned turn and
// spawned another, so dates ran ahead and the feed filled up; and each tap on
// a flatmate's turn earned the tapper one more skip of their own, invisibly.
// The first is closed by a halfway gate, the second by capping credits at one
// per person per chore.

// advanceClock moves the handlers' clock forward by d for the rest of the test.
// Offsets add up across calls, and the clock is real time again afterwards.
func advanceClock(t *testing.T, d time.Duration) {
	t.Helper()
	previous := clock
	clock = func() time.Time { return previous().Add(d) }
	t.Cleanup(func() { clock = previous })
}

func TestAChoresFirstTurnCanBeDoneAtOnce(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	g := newGroup(t, db, owner, "Flat")
	chore := createChore(t, db, g, owner, fmt.Sprintf(`{
		"name": "Fridge", "schedule_type": "interval", "interval_days": 10, "rotation": [%q]
	}`, owner))

	first := theOpenOcc(t, db, g, owner, chore.ID)
	if first.CanDoFrom != nil {
		t.Fatalf("a chore's first turn has can_do_from %s; there is no previous turn to be early for", first.CanDoFrom)
	}
	if rec := patchOccurrence(t, db, g, first.ID, owner, "done"); rec.Code != 200 {
		t.Fatalf("first turn: %d %s", rec.Code, rec.Body.String())
	}
}

func TestATurnCannotBeMarkedDoneBeforeHalfwayToItsDay(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	g := newGroup(t, db, owner, "Flat")
	chore := createChore(t, db, g, owner, fmt.Sprintf(`{
		"name": "Fridge", "schedule_type": "interval", "interval_days": 10, "rotation": [%q]
	}`, owner))

	first := theOpenOcc(t, db, g, owner, chore.ID)
	if rec := patchOccurrence(t, db, g, first.ID, owner, "done"); rec.Code != 200 {
		t.Fatalf("first turn: %d %s", rec.Code, rec.Body.String())
	}
	doneAt := clock()

	next := theOpenOcc(t, db, g, owner, chore.ID)
	if next.CanDoFrom == nil {
		t.Fatal("the spawned turn has no can_do_from")
	}
	// Every 10 days, done today: next due in 10, tickable from halfway —
	// about day 5, give or take the clock time the due date lands on.
	want := doneAt.Add(next.DueDate.Sub(doneAt) / 2)
	if diff := next.CanDoFrom.Sub(want); diff < -time.Minute || diff > time.Minute {
		t.Errorf("can_do_from = %s, want %s (halfway from the completion to the due date)", next.CanDoFrom, want)
	}
	if days := next.CanDoFrom.Sub(doneAt).Hours() / 24; days < 4.5 || days > 5.5 {
		t.Errorf("can_do_from is %.1f days after the completion, want about 5", days)
	}

	rec := patchOccurrence(t, db, g, next.ID, owner, "done")
	if rec.Code != 409 {
		t.Fatalf("a second tap straight away was %d %s, want 409", rec.Code, rec.Body.String())
	}
	if got := theOpenOcc(t, db, g, owner, chore.ID); got.ID != next.ID {
		t.Fatalf("the refused tap still moved the turn on")
	}

	// A minute before halfway: still too soon. A minute after: fine.
	advanceClock(t, next.CanDoFrom.Sub(clock())-time.Minute)
	if rec := patchOccurrence(t, db, g, next.ID, owner, "done"); rec.Code != 409 {
		t.Fatalf("a minute before halfway was %d, want 409", rec.Code)
	}
	advanceClock(t, 2*time.Minute)
	if rec := patchOccurrence(t, db, g, next.ID, owner, "done"); rec.Code != 200 {
		t.Fatalf("a minute after halfway was %d %s, want 200", rec.Code, rec.Body.String())
	}
}

func TestAnyoneIsGatedNotJustTheHolder(t *testing.T) {
	db, g, a, b, _, chore, _ := threePersonChore(t, "interval")
	first := theOpenOcc(t, db, g, a, chore.ID)
	if rec := patchOccurrence(t, db, g, first.ID, a, "done"); rec.Code != 200 {
		t.Fatalf("first turn: %d", rec.Code)
	}
	next := theOpenOcc(t, db, g, a, chore.ID)
	if rec := patchOccurrence(t, db, g, next.ID, b, "done"); rec.Code != 409 {
		t.Fatalf("a flatmate covering straight away was %d, want 409: the gate is on the turn, not the person", rec.Code)
	}
}

func TestAnAsNeededTurnIsNeverGated(t *testing.T) {
	db, g, a, _, _, chore, _ := threePersonChore(t, "as_needed")
	for i := 0; i < 3; i++ {
		occ := theOpenOcc(t, db, g, a, chore.ID)
		if occ.CanDoFrom != nil {
			t.Fatalf("round %d: an as-needed turn has can_do_from set", i)
		}
		if rec := patchOccurrence(t, db, g, occ.ID, a, "done"); rec.Code != 200 {
			t.Fatalf("round %d: %d %s", i, rec.Code, rec.Body.String())
		}
	}
}

func TestAFixedDayTurnOpensHalfwayToItsSlot(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	g := newGroup(t, db, owner, "Flat")
	// A weekday two days out, so the first completion is early and the next
	// turn is counted from the slot, not from the completion (B-13).
	target := time.Now().AddDate(0, 0, 2)
	chore := createChore(t, db, g, owner, fmt.Sprintf(`{
		"name": "Bins", "schedule_type": "fixed_date", "fixed_weekdays": [%d], "rotation": [%q]
	}`, int(target.Weekday()), owner))

	first := theOpenOcc(t, db, g, owner, chore.ID)
	firstDue := *first.DueDate
	if rec := patchOccurrence(t, db, g, first.ID, owner, "done"); rec.Code != 200 {
		t.Fatalf("first turn: %d", rec.Code)
	}

	next := theOpenOcc(t, db, g, owner, chore.ID)
	if next.CanDoFrom == nil {
		t.Fatal("no can_do_from on the next slot")
	}
	// Halfway between the slot just done and the next one: 3.5 days after
	// the first, 3.5 before the second.
	before := next.CanDoFrom.Sub(firstDue)
	after := next.DueDate.Sub(*next.CanDoFrom)
	if diff := before - after; diff < -time.Minute || diff > time.Minute {
		t.Errorf("can_do_from %s is %s after the last slot and %s before the next; want halfway",
			next.CanDoFrom, before, after)
	}
	if rec := patchOccurrence(t, db, g, next.ID, owner, "done"); rec.Code != 409 {
		t.Fatalf("tapping again at once was %d, want 409", rec.Code)
	}
}

func TestUndoIsNotGated(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	g := newGroup(t, db, owner, "Flat")
	chore := createChore(t, db, g, owner, fmt.Sprintf(`{
		"name": "Fridge", "schedule_type": "interval", "interval_days": 10, "rotation": [%q]
	}`, owner))
	first := theOpenOcc(t, db, g, owner, chore.ID)
	patchOccurrence(t, db, g, first.ID, owner, "done")
	if rec := patchOccurrence(t, db, g, first.ID, owner, "open"); rec.Code != 200 {
		t.Fatalf("undo: %d %s", rec.Code, rec.Body.String())
	}
	if got := theOpenOcc(t, db, g, owner, chore.ID); got.ID != first.ID {
		t.Fatal("undo did not bring the first turn back")
	}
}

// Covering a flatmate's turn twice earns one skip, not two. Rotation A, B, C;
// B does A's turn twice in a row (the debt keeps returning it to A). Once A
// repays, B is skipped once — C, then A — and then B's turn comes round as
// normal. Uncapped, B would be skipped a second time: C, A, C.
func TestCoveringTheSameChoreAgainEarnsNoSecondSkip(t *testing.T) {
	db, g, a, b, _, chore, name := threePersonChore(t, "interval")

	occ1 := theOpenOcc(t, db, g, a, chore.ID)
	markDone(t, db, g, occ1.ID, b)
	occ2 := theOpenOcc(t, db, g, a, chore.ID)
	if occ2.AssignedTo != a {
		t.Fatalf("occ2 went to %s, want A (the debt)", name(occ2.AssignedTo))
	}
	markDone(t, db, g, occ2.ID, b)
	occ3 := theOpenOcc(t, db, g, a, chore.ID)
	if occ3.AssignedTo != a {
		t.Fatalf("occ3 went to %s, want A (still the one debt)", name(occ3.AssignedTo))
	}
	markDone(t, db, g, occ3.ID, a)

	got := []string{}
	for i := 0; i < 3; i++ {
		occ := theOpenOcc(t, db, g, a, chore.ID)
		got = append(got, name(occ.AssignedTo))
		markDone(t, db, g, occ.ID, occ.AssignedTo)
	}
	want := []string{"C", "A", "B"}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("after A repaid, turns went %v, want %v: B covered twice but is skipped once", got, want)
		}
	}
}
