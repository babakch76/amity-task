package handlers

import "testing"

// The app's "How turns stay fair" page (Fairness.kt) walks two cases turn by
// turn, with a flat of Maya, you and Sam taking turns in that order. These
// play the same turns through the real API, so the page cannot quietly drift
// from what the server does. A is Maya, B is you, C is Sam.

// "Someone does your turn": Maya did hers, then did yours as well.
func TestFairnessPage_SomeoneDoesYourTurn(t *testing.T) {
	db, g, a, b, c, chore, name := threePersonChore(t, "interval")

	// whose: who the board puts it on; doer: who marks it done.
	steps := []struct{ whose, doer string }{
		{a, a}, // Maya's own turn.
		{b, a}, // Your turn, done by Maya.
		{b, b}, // The one you owe comes back to you.
		{c, c}, // As usual.
		{b, b}, // Maya's turn, but Maya uses the skip, so it moves on to you.
		{c, c}, // As usual.
	}
	for i, s := range steps {
		occ := theOpenOcc(t, db, g, a, chore.ID)
		if occ.AssignedTo != s.whose {
			t.Fatalf("turn %d went to %s, want %s", i+1, name(occ.AssignedTo), name(s.whose))
		}
		markDone(t, db, g, occ.ID, s.doer)
	}
}

// "You're too busy and pass it": your turn goes to Sam, who does it.
func TestFairnessPage_YouPassIt(t *testing.T) {
	db, g, a, b, c, chore, name := threePersonChore(t, "interval")

	markDone(t, db, g, theOpenOcc(t, db, g, a, chore.ID).ID, a) // Maya's own turn.

	yours := theOpenOcc(t, db, g, a, chore.ID)
	if yours.AssignedTo != b {
		t.Fatalf("turn 2 went to %s, want B", name(yours.AssignedTo))
	}
	passOK(t, db, g, yours.ID, b)
	passed := theOpenOcc(t, db, g, a, chore.ID)
	if passed.AssignedTo != c {
		t.Fatalf("your pass went to %s, want C", name(passed.AssignedTo))
	}
	markDone(t, db, g, passed.ID, c)

	for i, whose := range []string{
		b, // The one you owe comes back to you.
		a, // Sam's turn, but Sam uses the skip, so it moves on to Maya.
		b, // As usual.
		c, // As usual.
	} {
		occ := theOpenOcc(t, db, g, a, chore.ID)
		if occ.AssignedTo != whose {
			t.Fatalf("turn %d went to %s, want %s", i+3, name(occ.AssignedTo), name(whose))
		}
		markDone(t, db, g, occ.ID, whose)
	}
}
