package handlers

import (
	"testing"
	"time"
)

// The board sends every open turn but only the last few days of finished
// ones: the app shows a week of "Recently done", and the whole history made
// every poll grow for as long as a household existed. Older completions are
// still on the history endpoint.
func TestTheBoardSendsOnlyRecentlyFinishedTurns(t *testing.T) {
	db, g, a, b, c, chore, _ := threePersonChore(t, "interval")

	old := theOpenOcc(t, db, g, a, chore.ID)
	markDone(t, db, g, old.ID, a)
	recent := theOpenOcc(t, db, g, a, chore.ID)
	markDone(t, db, g, recent.ID, b)
	open := theOpenOcc(t, db, g, a, chore.ID)

	// Finished ten days and three days before the handlers' "now". That is
	// clock(), not time.Now(): markDone moves the clock on a month each time,
	// so the halfway rule (B-25) never stops a test from marking turns.
	now := clock()
	if _, err := db.Exec(`UPDATE occurrences SET done_at = ? WHERE id = ?`,
		now.Add(-10*24*time.Hour).UTC(), old.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := db.Exec(`UPDATE occurrences SET done_at = ? WHERE id = ?`,
		now.Add(-3*24*time.Hour).UTC(), recent.ID); err != nil {
		t.Fatal(err)
	}

	listed := map[string]bool{}
	for _, o := range listOccurrences(t, db, g, c) {
		listed[o.ID] = true
	}
	if !listed[open.ID] {
		t.Error("the open turn is missing from the board")
	}
	if !listed[recent.ID] {
		t.Error("a turn finished three days ago is missing: the app shows a week of Recently done")
	}
	if listed[old.ID] {
		t.Error("a turn finished ten days ago was still sent with the board")
	}

	// Nothing is lost: the chore's history still has both.
	inHistory := map[string]bool{}
	for _, e := range choreHistory(t, db, g, chore.ID, c).Entries {
		inHistory[e.OccurrenceID] = true
	}
	if !inHistory[old.ID] || !inHistory[recent.ID] {
		t.Errorf("history lost a completion: old %v, recent %v", inHistory[old.ID], inHistory[recent.ID])
	}
}

// An open turn is sent however old it is: a chore left for weeks is exactly
// the one the board must not drop.
func TestAnOldOpenTurnIsStillOnTheBoard(t *testing.T) {
	db, g, a, _, _, chore, _ := threePersonChore(t, "interval")
	open := theOpenOcc(t, db, g, a, chore.ID)
	now := clock()
	if _, err := db.Exec(`UPDATE occurrences SET created_at = ?, due_date = ? WHERE id = ?`,
		now.Add(-60*24*time.Hour).UTC(), now.Add(-50*24*time.Hour).UTC(), open.ID); err != nil {
		t.Fatal(err)
	}
	found := false
	for _, o := range listOccurrences(t, db, g, a) {
		if o.ID == open.ID {
			found = true
		}
	}
	if !found {
		t.Error("an open turn two months old was dropped from the board")
	}
}
