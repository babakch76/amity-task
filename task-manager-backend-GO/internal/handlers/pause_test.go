package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"task-manager-backend/internal/database"
)

// Going away, and the household pausing when everyone has. See pause.go.

func goAway(t *testing.T, db *database.DB, groupID, userID string, away bool) {
	t.Helper()
	rec := httptest.NewRecorder()
	(&GroupHandler{DB: db}).SetAway(rec, request(http.MethodPut, "/groups/x/members/me/away",
		fmt.Sprintf(`{"away":%v}`, away), userID, map[string]string{"group_id": groupID}))
	if rec.Code != http.StatusOK {
		t.Fatalf("set away=%v: %d %s", away, rec.Code, rec.Body.String())
	}
}

func isPaused(t *testing.T, db *database.DB, groupID, userID string) bool {
	t.Helper()
	rec := httptest.NewRecorder()
	(&GroupHandler{DB: db}).GetGroup(rec, request(http.MethodGet, "/groups/x", "", userID,
		map[string]string{"group_id": groupID}))
	var g struct {
		Paused bool `json:"paused"`
	}
	if err := json.Unmarshal(rec.Body.Bytes(), &g); err != nil {
		t.Fatalf("group: %s", rec.Body.String())
	}
	return g.Paused
}

// backdate pretends the household has been empty for the given time: the
// pause, everyone's absence and the open turns all began that long ago.
func backdate(t *testing.T, db *database.DB, groupID string, ago time.Duration) {
	t.Helper()
	then := time.Now().UTC().Add(-ago)
	for _, q := range []string{
		`UPDATE groups SET paused_since = ? WHERE id = ? AND paused_since IS NOT NULL`,
		`UPDATE away_periods SET started_at = ? WHERE group_id = ?`,
		`UPDATE occurrences SET created_at = ? WHERE group_id = ?`,
	} {
		if _, err := db.Exec(q, then, groupID); err != nil {
			t.Fatalf("backdate: %v", err)
		}
	}
}

func TestGoingAwayHandsYourTurnOnAndItComesBackToYou(t *testing.T) {
	db, g, a, b, c, chore, name := threePersonChore(t, "interval")

	goAway(t, db, g, a, true)
	occ := theOpenOcc(t, db, g, b, chore.ID)
	if occ.AssignedTo != b {
		t.Fatalf("A went away holding the turn; it is with %s, want B", name(occ.AssignedTo))
	}
	markDone(t, db, g, occ.ID, b)

	// A owes it back, but is away: the rotation carries on without them.
	occ = theOpenOcc(t, db, g, b, chore.ID)
	if occ.AssignedTo != c {
		t.Fatalf("next went to %s, want C", name(occ.AssignedTo))
	}
	markDone(t, db, g, occ.ID, c)

	goAway(t, db, g, a, false)
	for i, want := range []string{
		b, // Already on the board, given out while A was away.
		a, // Then the turn A owes.
	} {
		occ = theOpenOcc(t, db, g, b, chore.ID)
		if occ.AssignedTo != want {
			t.Fatalf("after A is back, turn %d went to %s, want %s", i+1, name(occ.AssignedTo), name(want))
		}
		markDone(t, db, g, occ.ID, occ.AssignedTo)
	}
}

func TestTheLastOneToGoAwayKeepsTheirTurn(t *testing.T) {
	db, g, a, b, c, chore, name := threePersonChore(t, "interval")
	goAway(t, db, g, b, true)
	goAway(t, db, g, c, true)
	if isPaused(t, db, g, a) {
		t.Fatal("paused while A is still home")
	}
	goAway(t, db, g, a, true)

	if !isPaused(t, db, g, a) {
		t.Fatal("everyone is away but the household is not paused")
	}
	if occ := theOpenOcc(t, db, g, a, chore.ID); occ.AssignedTo != a {
		t.Fatalf("with nobody home the turn moved to %s; it should stay with A", name(occ.AssignedTo))
	}
}

func TestNothingCanBePassedWhileEveryoneIsAway(t *testing.T) {
	db, g, a, b, c, chore, _ := threePersonChore(t, "interval")
	for _, id := range []string{b, c, a} {
		goAway(t, db, g, id, true)
	}
	occ := theOpenOcc(t, db, g, a, chore.ID)
	if rec := passOccurrence(t, db, g, occ.ID, a); rec.Code != http.StatusConflict {
		t.Fatalf("passing while paused: expected 409, got %d %s", rec.Code, rec.Body.String())
	}
	// Doing it still works, for whoever is home early.
	markDone(t, db, g, occ.ID, a)
}

func TestDatesMoveOnByTheDaysEveryoneWasAway(t *testing.T) {
	db, g, a, b, c, chore, _ := threePersonChore(t, "interval")
	before := *theOpenOcc(t, db, g, a, chore.ID).DueDate

	for _, id := range []string{b, c, a} {
		goAway(t, db, g, id, true)
	}
	backdate(t, db, g, 5*24*time.Hour)

	// While it lasts, the board shows the dates people will come back to.
	shown := *theOpenOcc(t, db, g, a, chore.ID).DueDate
	if want := before.AddDate(0, 0, 5); !shown.Equal(want) {
		t.Fatalf("during the pause the board shows %v, want %v", shown, want)
	}

	goAway(t, db, g, b, false)
	if isPaused(t, db, g, a) {
		t.Fatal("still paused after B came back")
	}
	after := *theOpenOcc(t, db, g, a, chore.ID).DueDate
	if want := before.AddDate(0, 0, 5); !after.Equal(want) {
		t.Fatalf("after the pause the date is %v, want five days on, %v", after, want)
	}
	// Once only: reading again moves nothing.
	if again := *theOpenOcc(t, db, g, a, chore.ID).DueDate; !again.Equal(after) {
		t.Fatalf("the date moved again on a second read: %v", again)
	}
}

func TestAnEveningWithEveryoneOutMovesNothing(t *testing.T) {
	db, g, a, b, c, chore, _ := threePersonChore(t, "interval")
	before := *theOpenOcc(t, db, g, a, chore.ID).DueDate
	for _, id := range []string{b, c, a} {
		goAway(t, db, g, id, true)
	}
	backdate(t, db, g, 3*time.Hour)
	goAway(t, db, g, a, false)
	if after := *theOpenOcc(t, db, g, a, chore.ID).DueDate; !after.Equal(before) {
		t.Fatalf("three hours away moved the date from %v to %v", before, after)
	}
}

// Somebody's planned return date passing ends the pause, with nobody having to
// open the app at that moment.
func TestAPlannedReturnEndsThePauseOnTime(t *testing.T) {
	db, g, a, b, c, chore, _ := threePersonChore(t, "interval")
	before := *theOpenOcc(t, db, g, a, chore.ID).DueDate
	for _, id := range []string{b, c, a} {
		goAway(t, db, g, id, true)
	}
	backdate(t, db, g, 6*24*time.Hour)
	// B was due back two days ago.
	if _, err := db.Exec(`UPDATE away_periods SET ends_at = ? WHERE group_id = ? AND user_id = ?`,
		time.Now().UTC().Add(-2*24*time.Hour), g, b); err != nil {
		t.Fatal(err)
	}
	if after := *theOpenOcc(t, db, g, a, chore.ID).DueDate; !after.Equal(before.AddDate(0, 0, 4)) {
		t.Fatalf("date is %v, want four days on (six away, back two days ago)", after)
	}
}

// Changing your return date while away closes one absence and opens another at
// the same moment. That is not coming home.
func TestChangingYourReturnDateIsNotComingHome(t *testing.T) {
	db, g, a, b, c, chore, _ := threePersonChore(t, "interval")
	before := *theOpenOcc(t, db, g, a, chore.ID).DueDate
	for _, id := range []string{b, c, a} {
		goAway(t, db, g, id, true)
	}
	backdate(t, db, g, 5*24*time.Hour)
	// Three days ago C said away again, with a return date.
	threeDaysAgo := time.Now().UTC().Add(-3 * 24 * time.Hour)
	if _, err := db.Exec(`UPDATE away_periods SET ended_at = ? WHERE group_id = ? AND user_id = ?`, threeDaysAgo, g, c); err != nil {
		t.Fatal(err)
	}
	setAway(t, db, g, c, nil)
	if _, err := db.Exec(`UPDATE away_periods SET started_at = ? WHERE group_id = ? AND user_id = ? AND ended_at IS NULL`,
		threeDaysAgo, g, c); err != nil {
		t.Fatal(err)
	}

	goAway(t, db, g, a, false)
	if after := *theOpenOcc(t, db, g, a, chore.ID).DueDate; !after.Equal(before.AddDate(0, 0, 5)) {
		t.Fatalf("date is %v, want the full five days on", after)
	}
}

func TestFixedDayChoresKeepTheirDayThroughAPause(t *testing.T) {
	db := newTestDB(t)
	a := newUser(t, db, "ann")
	b := newUser(t, db, "bo")
	g := newGroup(t, db, a, "Flat")
	addMember(t, db, g, b, "member")
	chore := createChore(t, db, g, a, fmt.Sprintf(`{
		"name": "Bins", "schedule_type": "fixed_date", "fixed_weekdays": [2],
		"rotation": [%q, %q]}`, a, b))

	goAway(t, db, g, b, true)
	goAway(t, db, g, a, true)
	backdate(t, db, g, 9*24*time.Hour)
	goAway(t, db, g, a, false)

	due := theOpenOcc(t, db, g, a, chore.ID).DueDate
	if due == nil || due.Weekday() != time.Tuesday {
		t.Fatalf("the bins are due %v, want a Tuesday", due)
	}
}

// The household is not paused (somebody else is home), but everyone in this
// chore's rotation is away. There is nobody to pass it to, and nobody is
// offered as a taker.
func TestNobodyAwayIsHandedAChore(t *testing.T) {
	db, g, a, b, c, chore, name := threePersonChore(t, "interval")
	d := newUser(t, db, "dee")
	addMember(t, db, g, d, "member")
	goAway(t, db, g, b, true)
	goAway(t, db, g, c, true)
	goAway(t, db, g, a, true)
	if isPaused(t, db, g, d) {
		t.Fatal("dee is home, so the household is not paused")
	}

	occ := theOpenOcc(t, db, g, a, chore.ID)
	if occ.AssignedTo != a {
		t.Fatalf("with its whole rotation away the turn moved to %s", name(occ.AssignedTo))
	}
	if occ.PassTo != nil {
		t.Fatalf("offered %s as a taker, who is away", name(*occ.PassTo))
	}
	if rec := passOccurrence(t, db, g, occ.ID, a); rec.Code != http.StatusConflict {
		t.Fatalf("passing to nobody: expected 409, got %d %s", rec.Code, rec.Body.String())
	}
}
