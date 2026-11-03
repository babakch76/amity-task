package handlers

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// Household settings: renaming, handing over, and the owner leaving.

func roleOf(t *testing.T, h *GroupHandler, groupID, userID string) string {
	t.Helper()
	role, err := h.DB.GetMemberRole(groupID, userID)
	if err != nil {
		t.Fatalf("role lookup: %v", err)
	}
	return role
}

// lastEvent returns the newest feed entry's type, actor and detail.
func lastEvent(t *testing.T, h *GroupHandler, groupID string) (string, string, string) {
	t.Helper()
	var kind, actor, detail string
	if err := h.DB.QueryRow(
		`SELECT event_type, actor_id, detail FROM activity_events
		 WHERE group_id = ? ORDER BY created_at DESC, rowid DESC LIMIT 1`, groupID,
	).Scan(&kind, &actor, &detail); err != nil {
		t.Fatalf("last event: %v", err)
	}
	return kind, actor, detail
}

func rename(h *GroupHandler, groupID, userID, body string) *httptest.ResponseRecorder {
	rec := httptest.NewRecorder()
	h.RenameGroup(rec, request(http.MethodPatch, "/groups/x", body, userID,
		map[string]string{"group_id": groupID}))
	return rec
}

func handOverTo(h *GroupHandler, groupID, userID, to string) *httptest.ResponseRecorder {
	rec := httptest.NewRecorder()
	h.HandOverGroup(rec, request(http.MethodPost, "/groups/x/owner", `{"user_id":"`+to+`"}`, userID,
		map[string]string{"group_id": groupID}))
	return rec
}

func TestAnyoneInTheHouseholdCanRenameIt(t *testing.T) {
	db := newTestDB(t)
	h := &GroupHandler{DB: db}
	owner := newUser(t, db, "owner")
	maya := newUser(t, db, "maya")
	groupID := newGroup(t, db, owner, "Flat")
	addMember(t, db, groupID, maya, "member")

	rec := rename(h, groupID, maya, `{"name":"  Flat 3B  "}`)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	var name string
	db.QueryRow(`SELECT name FROM groups WHERE id = ?`, groupID).Scan(&name)
	if name != "Flat 3B" {
		t.Fatalf("name is %q, want the trimmed %q", name, "Flat 3B")
	}
	kind, actor, detail := lastEvent(t, h, groupID)
	if kind != EventGroupRenamed || actor != maya || detail != "Flat 3B" {
		t.Fatalf("feed says %s by %s (%q)", kind, actor, detail)
	}
}

func TestRenamingToTheSameNameWritesNothingToTheFeed(t *testing.T) {
	db := newTestDB(t)
	h := &GroupHandler{DB: db}
	owner := newUser(t, db, "owner")
	groupID := newGroup(t, db, owner, "Flat 3B")

	if rec := rename(h, groupID, owner, `{"name":"Flat 3B"}`); rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}
	var n int
	db.QueryRow(`SELECT COUNT(*) FROM activity_events WHERE group_id = ?`, groupID).Scan(&n)
	if n != 0 {
		t.Fatalf("%d feed entries for a rename that changed nothing", n)
	}
}

func TestHouseholdNamesAreCappedAtFortyCharacters(t *testing.T) {
	db := newTestDB(t)
	h := &GroupHandler{DB: db}
	owner := newUser(t, db, "owner")
	groupID := newGroup(t, db, owner, "Flat")

	// Characters, not bytes: forty accented letters are fine.
	if rec := rename(h, groupID, owner, `{"name":"`+strings.Repeat("é", 40)+`"}`); rec.Code != http.StatusOK {
		t.Fatalf("40 characters refused: %d %s", rec.Code, rec.Body.String())
	}
	for _, body := range []string{
		`{"name":"` + strings.Repeat("a", 41) + `"}`,
		`{"name":"   "}`,
	} {
		if rec := rename(h, groupID, owner, body); rec.Code != http.StatusBadRequest {
			t.Fatalf("%s: expected 400, got %d", body, rec.Code)
		}
	}

	rec := httptest.NewRecorder()
	h.CreateGroup(rec, request(http.MethodPost, "/groups", `{"name":"`+strings.Repeat("a", 41)+`"}`, owner, nil))
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("creating with 41 characters: expected 400, got %d", rec.Code)
	}
}

func TestTheOwnerCanHandTheHouseholdOver(t *testing.T) {
	db := newTestDB(t)
	h := &GroupHandler{DB: db}
	owner := newUser(t, db, "owner")
	sam := newUser(t, db, "sam")
	groupID := newGroup(t, db, owner, "Flat")
	addMember(t, db, groupID, sam, "member")

	if rec := handOverTo(h, groupID, owner, sam); rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	if r := roleOf(t, h, groupID, sam); r != "owner" {
		t.Fatalf("sam is %q, want owner", r)
	}
	if r := roleOf(t, h, groupID, owner); r != "member" {
		t.Fatalf("old owner is %q, want member", r)
	}
	kind, actor, detail := lastEvent(t, h, groupID)
	if kind != EventOwnerChanged || actor != owner || detail != "sam" {
		t.Fatalf("feed says %s by %s (%q)", kind, actor, detail)
	}

	// Owning means being able to remove it: now Sam can and the old owner can't.
	rec := httptest.NewRecorder()
	h.DeleteGroup(rec, request(http.MethodDelete, "/groups/x", "", owner, map[string]string{"group_id": groupID}))
	if rec.Code != http.StatusForbidden {
		t.Fatalf("old owner removing it: expected 403, got %d", rec.Code)
	}
	rec = httptest.NewRecorder()
	h.DeleteGroup(rec, request(http.MethodDelete, "/groups/x", "", sam, map[string]string{"group_id": groupID}))
	if rec.Code != http.StatusOK {
		t.Fatalf("new owner removing it: expected 200, got %d", rec.Code)
	}
}

func TestHandingOverIsRefusedWhenItMakesNoSense(t *testing.T) {
	db := newTestDB(t)
	h := &GroupHandler{DB: db}
	owner := newUser(t, db, "owner")
	sam := newUser(t, db, "sam")
	stranger := newUser(t, db, "stranger")
	groupID := newGroup(t, db, owner, "Flat")
	addMember(t, db, groupID, sam, "member")

	cases := []struct {
		name     string
		caller   string
		to       string
		wantCode int
	}{
		{"a member giving away what they don't own", sam, owner, http.StatusForbidden},
		{"the owner handing it to themselves", owner, owner, http.StatusBadRequest},
		{"the owner handing it to someone outside", owner, stranger, http.StatusNotFound},
		{"nobody named", owner, "", http.StatusBadRequest},
	}
	for _, c := range cases {
		if rec := handOverTo(h, groupID, c.caller, c.to); rec.Code != c.wantCode {
			t.Errorf("%s: expected %d, got %d", c.name, c.wantCode, rec.Code)
		}
	}
	if r := roleOf(t, h, groupID, owner); r != "owner" {
		t.Fatalf("after refusals the owner is %q", r)
	}
}

func TestTheOwnerCanHandOverAndLeaveInOneStep(t *testing.T) {
	db := newTestDB(t)
	h := &GroupHandler{DB: db}
	owner := newUser(t, db, "owner")
	sam := newUser(t, db, "sam")
	groupID := newGroup(t, db, owner, "Flat")
	addMember(t, db, groupID, sam, "member")

	rec := httptest.NewRecorder()
	h.LeaveGroup(rec, request(http.MethodDelete, "/groups/x/members/me?hand_to="+sam, "", owner,
		map[string]string{"group_id": groupID}))
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", rec.Code, rec.Body.String())
	}
	if isMember, _ := db.IsMember(groupID, owner); isMember {
		t.Fatal("the owner is still in the household")
	}
	if r := roleOf(t, h, groupID, sam); r != "owner" {
		t.Fatalf("sam is %q, want owner", r)
	}
}

func TestAFailedHandOverLeavesTheOwnerWhereTheyWere(t *testing.T) {
	db := newTestDB(t)
	h := &GroupHandler{DB: db}
	owner := newUser(t, db, "owner")
	sam := newUser(t, db, "sam")
	stranger := newUser(t, db, "stranger")
	groupID := newGroup(t, db, owner, "Flat")
	addMember(t, db, groupID, sam, "member")

	rec := httptest.NewRecorder()
	h.LeaveGroup(rec, request(http.MethodDelete, "/groups/x/members/me?hand_to="+stranger, "", owner,
		map[string]string{"group_id": groupID}))
	if rec.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d: %s", rec.Code, rec.Body.String())
	}
	if r := roleOf(t, h, groupID, owner); r != "owner" {
		t.Fatalf("after a refused leave the owner is %q", r)
	}
	var n int
	db.QueryRow(`SELECT COUNT(*) FROM activity_events WHERE group_id = ?`, groupID).Scan(&n)
	if n != 0 {
		t.Fatalf("%d feed entries for a leave that didn't happen", n)
	}
}
