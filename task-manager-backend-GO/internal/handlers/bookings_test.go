package handlers

import (
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"task-manager-backend/internal/database"
	"task-manager-backend/internal/models"
)

func at(d time.Duration) string {
	return time.Now().Add(d).UTC().Format(time.RFC3339)
}

func createBooking(t *testing.T, db *database.DB, groupID, userID, body string) *httptest.ResponseRecorder {
	t.Helper()
	h := &BookingHandler{DB: db}
	rec := httptest.NewRecorder()
	h.CreateBooking(rec, request(
		http.MethodPost, "/groups/x/bookings", body, userID,
		map[string]string{"group_id": groupID},
	))
	return rec
}

func book(t *testing.T, db *database.DB, groupID, userID, what string, from, to time.Duration) models.Booking {
	t.Helper()
	rec := createBooking(t, db, groupID, userID, fmt.Sprintf(
		`{"what": %q, "note": "guests", "starts_at": %q, "ends_at": %q}`, what, at(from), at(to),
	))
	if rec.Code != http.StatusCreated {
		t.Fatalf("booking %s: %d %s", what, rec.Code, rec.Body.String())
	}
	var b models.Booking
	if err := json.Unmarshal(rec.Body.Bytes(), &b); err != nil {
		t.Fatalf("decoding booking: %v", err)
	}
	return b
}

func listBookings(t *testing.T, db *database.DB, groupID, userID string) []models.Booking {
	t.Helper()
	h := &BookingHandler{DB: db}
	rec := httptest.NewRecorder()
	h.ListBookings(rec, request(
		http.MethodGet, "/groups/x/bookings", "", userID,
		map[string]string{"group_id": groupID},
	))
	if rec.Code != http.StatusOK {
		t.Fatalf("listing bookings: %d %s", rec.Code, rec.Body.String())
	}
	var out []models.Booking
	if err := json.Unmarshal(rec.Body.Bytes(), &out); err != nil {
		t.Fatalf("decoding list: %v", err)
	}
	return out
}

func cancelBooking(t *testing.T, db *database.DB, groupID, bookingID, userID string) *httptest.ResponseRecorder {
	t.Helper()
	h := &BookingHandler{DB: db}
	rec := httptest.NewRecorder()
	h.CancelBooking(rec, request(
		http.MethodDelete, "/groups/x/bookings/y", "", userID,
		map[string]string{"group_id": groupID, "booking_id": bookingID},
	))
	return rec
}

// The whole point: what one person books, the others can read, with their name
// on it, soonest first.
func TestABookingIsVisibleToEveryoneInTheHousehold(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	sara := newUser(t, db, "sara")
	groupID := newGroup(t, db, owner, "Flat")
	addMember(t, db, groupID, sara, "member")

	book(t, db, groupID, sara, "Kitchen", 26*time.Hour, 28*time.Hour)
	book(t, db, groupID, owner, "Washing machine", 2*time.Hour, 4*time.Hour)

	got := listBookings(t, db, groupID, sara)
	if len(got) != 2 {
		t.Fatalf("got %d bookings, want 2", len(got))
	}
	if got[0].What != "Washing machine" || got[0].Username != "owner" {
		t.Errorf("first is %s by %s, want the soonest, by owner", got[0].What, got[0].Username)
	}
	if got[1].What != "Kitchen" || got[1].Username != "sara" || got[1].Note != "guests" {
		t.Errorf("second is %+v", got[1])
	}
	if n := tableRows(t, db,
		`SELECT COUNT(*) FROM activity_events WHERE group_id = ? AND event_type = 'booking_added'`, groupID,
	); n != 2 {
		t.Errorf("%d booking_added events, want 2: every group-visible change is broadcast", n)
	}
}

// Two people may want the same thing. It is a notice, not a lock, so the server
// takes both and the app is the one that points the overlap out.
func TestOverlappingBookingsAreBothAccepted(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	sara := newUser(t, db, "sara")
	groupID := newGroup(t, db, owner, "Flat")
	addMember(t, db, groupID, sara, "member")

	book(t, db, groupID, owner, "Kitchen", 2*time.Hour, 4*time.Hour)
	book(t, db, groupID, sara, "Kitchen", 3*time.Hour, 5*time.Hour)

	if n := len(listBookings(t, db, groupID, owner)); n != 2 {
		t.Fatalf("got %d bookings, want both", n)
	}
}

// One already under way is still on the board; one that has finished is not.
func TestAFinishedBookingLeavesTheList(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	groupID := newGroup(t, db, owner, "Flat")

	book(t, db, groupID, owner, "Under way", -time.Hour, time.Hour)
	if _, err := db.Exec(
		`INSERT INTO bookings (id, group_id, user_id, what, note, starts_at, ends_at, created_at)
		 VALUES ('old', ?, ?, 'Finished', '', ?, ?, ?)`,
		groupID, owner, time.Now().Add(-3*time.Hour).UTC(), time.Now().Add(-time.Hour).UTC(), time.Now().UTC(),
	); err != nil {
		t.Fatalf("seeding a finished booking: %v", err)
	}

	got := listBookings(t, db, groupID, owner)
	if len(got) != 1 || got[0].What != "Under way" {
		t.Fatalf("got %+v, want only the one under way", got)
	}
}

func TestCreateBookingRejectsWhatCannotBeShown(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	groupID := newGroup(t, db, owner, "Flat")

	long := func(n int) string {
		s := ""
		for i := 0; i < n; i++ {
			s += "x"
		}
		return s
	}

	for name, body := range map[string]string{
		"no name":       fmt.Sprintf(`{"what": "   ", "starts_at": %q, "ends_at": %q}`, at(time.Hour), at(2*time.Hour)),
		"name too long": fmt.Sprintf(`{"what": %q, "starts_at": %q, "ends_at": %q}`, long(bookingWhatMax+1), at(time.Hour), at(2*time.Hour)),
		"note too long": fmt.Sprintf(`{"what": "Kitchen", "note": %q, "starts_at": %q, "ends_at": %q}`, long(bookingNoteMax+1), at(time.Hour), at(2*time.Hour)),
		"no times":      `{"what": "Kitchen"}`,
		"ends first":    fmt.Sprintf(`{"what": "Kitchen", "starts_at": %q, "ends_at": %q}`, at(2*time.Hour), at(time.Hour)),
		"zero length":   fmt.Sprintf(`{"what": "Kitchen", "starts_at": %q, "ends_at": %q}`, at(time.Hour), at(time.Hour)),
		"already over":  fmt.Sprintf(`{"what": "Kitchen", "starts_at": %q, "ends_at": %q}`, at(-2*time.Hour), at(-time.Hour)),
		"not a time":    `{"what": "Kitchen", "starts_at": "Saturday", "ends_at": "Sunday"}`,
	} {
		if rec := createBooking(t, db, groupID, owner, body); rec.Code != http.StatusBadRequest {
			t.Errorf("%s: got %d, want 400 (%s)", name, rec.Code, rec.Body.String())
		}
	}
	if n := len(listBookings(t, db, groupID, owner)); n != 0 {
		t.Fatalf("a rejected booking was saved: %d", n)
	}
}

// A name in another alphabet is counted in letters, not bytes.
func TestBookingLimitsCountCharacters(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	groupID := newGroup(t, db, owner, "Flat")

	what := ""
	for i := 0; i < bookingWhatMax; i++ {
		what += "ش"
	}
	book(t, db, groupID, owner, what, time.Hour, 2*time.Hour)
}

func TestOnlyTheBookerCanCancel(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	sara := newUser(t, db, "sara")
	groupID := newGroup(t, db, owner, "Flat")
	otherGroup := newGroup(t, db, owner, "Other flat")
	addMember(t, db, groupID, sara, "member")

	b := book(t, db, groupID, sara, "Kitchen", time.Hour, 2*time.Hour)

	// Not even the owner: it is somebody else's plan.
	if rec := cancelBooking(t, db, groupID, b.ID, owner); rec.Code != http.StatusForbidden {
		t.Errorf("owner cancelling: got %d, want 403", rec.Code)
	}
	if rec := cancelBooking(t, db, otherGroup, b.ID, sara); rec.Code != http.StatusNotFound {
		t.Errorf("through another household: got %d, want 404", rec.Code)
	}
	if rec := cancelBooking(t, db, groupID, "no-such-booking", sara); rec.Code != http.StatusNotFound {
		t.Errorf("unknown booking: got %d, want 404", rec.Code)
	}
	if n := len(listBookings(t, db, groupID, sara)); n != 1 {
		t.Fatalf("a refused cancel removed the booking: %d left", n)
	}

	if rec := cancelBooking(t, db, groupID, b.ID, sara); rec.Code != http.StatusOK {
		t.Fatalf("booker cancelling: got %d: %s", rec.Code, rec.Body.String())
	}
	if n := len(listBookings(t, db, groupID, sara)); n != 0 {
		t.Fatalf("still %d booking(s) after cancelling", n)
	}
	if n := tableRows(t, db,
		`SELECT COUNT(*) FROM activity_events WHERE group_id = ? AND event_type = 'booking_cancelled'`, groupID,
	); n != 1 {
		t.Errorf("%d booking_cancelled events, want 1", n)
	}
}

// Somebody who has moved out is not using the washing machine on Saturday.
func TestLeavingAHouseholdClearsYourBookingsThere(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	sara := newUser(t, db, "sara")
	groupID := newGroup(t, db, owner, "Flat")
	addMember(t, db, groupID, sara, "member")

	book(t, db, groupID, sara, "Kitchen", time.Hour, 2*time.Hour)
	book(t, db, groupID, owner, "Washing machine", time.Hour, 2*time.Hour)

	h := &GroupHandler{DB: db}
	rec := httptest.NewRecorder()
	h.LeaveGroup(rec, request(
		http.MethodDelete, "/groups/x/members/me", "", sara,
		map[string]string{"group_id": groupID},
	))
	if rec.Code != http.StatusOK {
		t.Fatalf("leaving: %d %s", rec.Code, rec.Body.String())
	}

	got := listBookings(t, db, groupID, owner)
	if len(got) != 1 || got[0].What != "Washing machine" {
		t.Fatalf("got %+v, want only the owner's booking", got)
	}
}

func TestRemovingAHouseholdRemovesItsBookings(t *testing.T) {
	db := newTestDB(t)
	owner := newUser(t, db, "owner")
	groupID := newGroup(t, db, owner, "Flat")

	book(t, db, groupID, owner, "Kitchen", time.Hour, 2*time.Hour)

	h := &GroupHandler{DB: db}
	rec := httptest.NewRecorder()
	h.DeleteGroup(rec, request(
		http.MethodDelete, "/groups/x", "", owner,
		map[string]string{"group_id": groupID},
	))
	if rec.Code != http.StatusOK {
		t.Fatalf("removing: %d %s", rec.Code, rec.Body.String())
	}
	if n := tableRows(t, db, `SELECT COUNT(*) FROM bookings WHERE group_id = ?`, groupID); n != 0 {
		t.Fatalf("bookings still holds %d row(s)", n)
	}
}
