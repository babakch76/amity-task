package handlers

import (
	"encoding/json"
	"fmt"
	"net/http/httptest"
	"testing"
	"time"

	"task-manager-backend/internal/database"
	"task-manager-backend/internal/models"
)

func postAlso(t *testing.T, db *database.DB, groupID, occurrenceID, userID string) *httptest.ResponseRecorder {
	t.Helper()
	h := &ChoreHandler{DB: db}
	rec := httptest.NewRecorder()
	h.AddAlso(rec, request("POST",
		"/groups/"+groupID+"/occurrences/"+occurrenceID+"/also", "", userID,
		map[string]string{"group_id": groupID, "occurrence_id": occurrenceID}))
	return rec
}

func deleteAlso(t *testing.T, db *database.DB, groupID, occurrenceID, userID string) *httptest.ResponseRecorder {
	t.Helper()
	h := &ChoreHandler{DB: db}
	rec := httptest.NewRecorder()
	h.RemoveAlso(rec, request("DELETE",
		"/groups/"+groupID+"/occurrences/"+occurrenceID+"/also", "", userID,
		map[string]string{"group_id": groupID, "occurrence_id": occurrenceID}))
	return rec
}

// alsoFixture is a two-person flat with one as-needed chore whose first turn
// ann has just completed. It returns the group, both users, the chore and the
// completed occurrence.
func alsoFixture(t *testing.T) (db *database.DB, groupID, ann, bo string, chore models.Chore, doneID string) {
	t.Helper()
	db = newTestDB(t)
	ann = newUser(t, db, "ann")
	bo = newUser(t, db, "bo")
	groupID = newGroup(t, db, ann, "Flat")
	addMember(t, db, groupID, bo, "member")

	chore = createChore(t, db, groupID, ann, fmt.Sprintf(`{
		"name": "Bathroom", "schedule_type": "as_needed", "rotation": [%q, %q]
	}`, ann, bo))
	doneID = openOccurrencesFor(t, db, groupID, ann, chore.ID)[0].ID
	if rec := patchOccurrence(t, db, groupID, doneID, ann, "done"); rec.Code != 200 {
		t.Fatalf("complete: got %d (%s)", rec.Code, rec.Body.String())
	}
	return db, groupID, ann, bo, chore, doneID
}

func occurrenceByID(t *testing.T, db *database.DB, groupID, userID, id string) models.Occurrence {
	t.Helper()
	for _, o := range listOccurrences(t, db, groupID, userID) {
		if o.ID == id {
			return o
		}
	}
	t.Fatalf("occurrence %s not on the board", id)
	return models.Occurrence{}
}

// "Done by ann, also bo" — and nothing else moves. Credit, rotation and the
// next turn are exactly what they were before bo said anything.
func TestAlsoRecordsASecondContributorWithoutMovingTheTurn(t *testing.T) {
	db, groupID, ann, bo, chore, doneID := alsoFixture(t)
	nextBefore := openOccurrencesFor(t, db, groupID, ann, chore.ID)

	rec := postAlso(t, db, groupID, doneID, bo)
	if rec.Code != 200 {
		t.Fatalf("got %d, want 200 (%s)", rec.Code, rec.Body.String())
	}
	var got models.Occurrence
	json.Unmarshal(rec.Body.Bytes(), &got)
	if len(got.AlsoBy) != 1 || got.AlsoBy[0].UserID != bo || got.AlsoBy[0].Username != "bo" {
		t.Fatalf("also_by: got %+v, want just bo", got.AlsoBy)
	}
	if got.DoneBy == nil || *got.DoneBy != ann {
		t.Errorf("done_by moved: got %v, want ann", got.DoneBy)
	}

	// The board carries it too, not just the response.
	if board := occurrenceByID(t, db, groupID, ann, doneID); len(board.AlsoBy) != 1 {
		t.Errorf("board also_by: got %+v", board.AlsoBy)
	}

	nextAfter := openOccurrencesFor(t, db, groupID, ann, chore.ID)
	if len(nextAfter) != 1 || nextAfter[0].ID != nextBefore[0].ID ||
		nextAfter[0].AssignedTo != nextBefore[0].AssignedTo {
		t.Errorf("next turn changed: before %+v, after %+v", nextBefore, nextAfter)
	}

	var events int
	db.QueryRow(`SELECT COUNT(*) FROM activity_events
		WHERE group_id = ? AND actor_id = ? AND event_type = ? AND task_id = ? AND detail = ?`,
		groupID, bo, EventOccurrenceAlso, doneID, "Bathroom").Scan(&events)
	if events != 1 {
		t.Errorf("activity: got %d %s events, want 1", events, EventOccurrenceAlso)
	}
}

func TestAlsoIsRefusedWhenItWouldMakeNoSense(t *testing.T) {
	t.Run("on a turn that is still open", func(t *testing.T) {
		db, groupID, ann, _, chore, _ := alsoFixture(t)
		open := openOccurrencesFor(t, db, groupID, ann, chore.ID)[0]
		rec := postAlso(t, db, groupID, open.ID, ann)
		if rec.Code != 409 {
			t.Fatalf("got %d, want 409 (%s)", rec.Code, rec.Body.String())
		}
	})

	t.Run("by the person who did it", func(t *testing.T) {
		db, groupID, ann, _, _, doneID := alsoFixture(t)
		rec := postAlso(t, db, groupID, doneID, ann)
		if rec.Code != 409 {
			t.Fatalf("got %d, want 409 (%s)", rec.Code, rec.Body.String())
		}
	})

	t.Run("twice by the same person", func(t *testing.T) {
		db, groupID, _, bo, _, doneID := alsoFixture(t)
		postAlso(t, db, groupID, doneID, bo)
		rec := postAlso(t, db, groupID, doneID, bo)
		if rec.Code != 409 {
			t.Fatalf("got %d, want 409 (%s)", rec.Code, rec.Body.String())
		}
	})

	t.Run("once the window has passed", func(t *testing.T) {
		db, groupID, _, bo, _, doneID := alsoFixture(t)
		stale := time.Now().Add(-alsoWindow - time.Hour).UTC()
		if _, err := db.Exec(`UPDATE occurrences SET done_at = ? WHERE id = ?`, stale, doneID); err != nil {
			t.Fatalf("backdate: %v", err)
		}
		rec := postAlso(t, db, groupID, doneID, bo)
		if rec.Code != 409 {
			t.Fatalf("got %d, want 409 (%s)", rec.Code, rec.Body.String())
		}
	})

	t.Run("through another group's path", func(t *testing.T) {
		db, _, _, bo, _, doneID := alsoFixture(t)
		other := newGroup(t, db, bo, "Elsewhere")
		rec := postAlso(t, db, other, doneID, bo)
		if rec.Code != 404 {
			t.Fatalf("got %d, want 404 (%s)", rec.Code, rec.Body.String())
		}
	})
}

func TestAlsoCanBeTakenBackOnlyByItsOwnerAndOnlyBriefly(t *testing.T) {
	t.Run("by its owner, inside the window", func(t *testing.T) {
		db, groupID, ann, bo, _, doneID := alsoFixture(t)
		postAlso(t, db, groupID, doneID, bo)

		rec := deleteAlso(t, db, groupID, doneID, bo)
		if rec.Code != 200 {
			t.Fatalf("got %d, want 200 (%s)", rec.Code, rec.Body.String())
		}
		if got := occurrenceByID(t, db, groupID, ann, doneID); len(got.AlsoBy) != 0 {
			t.Errorf("also_by after withdraw: got %+v", got.AlsoBy)
		}
	})

	t.Run("not by anybody else", func(t *testing.T) {
		db, groupID, ann, bo, _, doneID := alsoFixture(t)
		postAlso(t, db, groupID, doneID, bo)

		rec := deleteAlso(t, db, groupID, doneID, ann)
		if rec.Code != 404 {
			t.Fatalf("got %d, want 404 (%s)", rec.Code, rec.Body.String())
		}
		if got := occurrenceByID(t, db, groupID, ann, doneID); len(got.AlsoBy) != 1 {
			t.Errorf("someone else's withdraw removed it: %+v", got.AlsoBy)
		}
	})

	t.Run("not after the window", func(t *testing.T) {
		db, groupID, _, bo, _, doneID := alsoFixture(t)
		postAlso(t, db, groupID, doneID, bo)
		stale := time.Now().Add(-undoWindow - time.Minute).UTC()
		db.Exec(`UPDATE occurrence_also_by SET added_at = ? WHERE occurrence_id = ?`, stale, doneID)

		rec := deleteAlso(t, db, groupID, doneID, bo)
		if rec.Code != 403 {
			t.Fatalf("got %d, want 403 (%s)", rec.Code, rec.Body.String())
		}
	})
}

// Undoing a completion takes "also" with it: otherwise an open turn would say
// somebody helped with a chore that is not done.
func TestUndoingTheCompletionClearsAlso(t *testing.T) {
	db, groupID, ann, bo, _, doneID := alsoFixture(t)
	postAlso(t, db, groupID, doneID, bo)

	if rec := patchOccurrence(t, db, groupID, doneID, ann, "open"); rec.Code != 200 {
		t.Fatalf("undo: got %d (%s)", rec.Code, rec.Body.String())
	}
	var left int
	db.QueryRow(`SELECT COUNT(*) FROM occurrence_also_by WHERE occurrence_id = ?`, doneID).Scan(&left)
	if left != 0 {
		t.Errorf("%d also row(s) survived the undo", left)
	}
}

// The chore's history says "done by ann, also bo"; the per-person count still
// credits only the doer, so adding yourself can never pad a number.
func TestAlsoShowsInHistoryButIsNotCounted(t *testing.T) {
	db, groupID, ann, bo, chore, doneID := alsoFixture(t)
	postAlso(t, db, groupID, doneID, bo)
	h := &ChoreHandler{DB: db}

	rec := httptest.NewRecorder()
	h.ChoreHistory(rec, request("GET", "/", "", ann,
		map[string]string{"group_id": groupID, "chore_id": chore.ID}))
	var hist models.ChoreHistory
	json.Unmarshal(rec.Body.Bytes(), &hist)
	if len(hist.Entries) != 1 || len(hist.Entries[0].AlsoBy) != 1 ||
		hist.Entries[0].AlsoBy[0].Username != "bo" {
		t.Fatalf("history: got %+v", hist.Entries)
	}

	rec = httptest.NewRecorder()
	h.GroupHistory(rec, request("GET", "/?window=week", "", ann,
		map[string]string{"group_id": groupID}))
	var people models.GroupHistory
	json.Unmarshal(rec.Body.Bytes(), &people)
	for _, p := range people.People {
		want := 0
		if p.UserID == ann {
			want = 1
		}
		if p.Completed != want {
			t.Errorf("%s completed: got %d, want %d", p.Username, p.Completed, want)
		}
	}
}
