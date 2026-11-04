package handlers

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"testing"

	"task-manager-backend/internal/database"
	"task-manager-backend/internal/models"
)

// ── helpers ─────────────────────────────────────────────────────────────────

func leave(t *testing.T, db *database.DB, groupID, userID string) {
	t.Helper()
	rec := httptest.NewRecorder()
	(&GroupHandler{DB: db}).LeaveGroup(rec, request(http.MethodDelete, "/groups/x/members/me", "", userID,
		map[string]string{"group_id": groupID}))
	if rec.Code != http.StatusOK {
		t.Fatalf("leaving: %d %s", rec.Code, rec.Body.String())
	}
}

// joinWithCode joins the way the app does, through a shareable code, and
// returns the chores the server says they are now in.
func joinWithCode(t *testing.T, db *database.DB, groupID, inviter, joiner string) []string {
	t.Helper()
	h := &GroupHandler{DB: db}
	rec := httptest.NewRecorder()
	h.GenerateInviteCode(rec, request(http.MethodPost, "/groups/x/invite-code", "", inviter,
		map[string]string{"group_id": groupID}))
	var code struct{ Code string }
	json.NewDecoder(rec.Body).Decode(&code)

	rec = httptest.NewRecorder()
	h.RedeemInviteCode(rec, request(http.MethodPost, "/invites/redeem", `{"code":"`+code.Code+`"}`, joiner, nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("joining: %d %s", rec.Code, rec.Body.String())
	}
	var body struct{ Chores []string }
	json.NewDecoder(rec.Body).Decode(&body)
	return body.Chores
}

func rotationOf(t *testing.T, db *database.DB, choreID string) []string {
	t.Helper()
	rot, err := (&ChoreHandler{DB: db}).loadRotations([]string{choreID})
	if err != nil {
		t.Fatalf("rotation: %v", err)
	}
	return rot[choreID]
}

func leftDebts(t *testing.T, db *database.DB, groupID, userID string) int {
	t.Helper()
	var n int
	db.QueryRow(`SELECT COUNT(*) FROM left_debts WHERE group_id = ? AND user_id = ?`, groupID, userID).Scan(&n)
	return n
}

func names(name func(string) string, ids []string) []string {
	out := []string{}
	for _, id := range ids {
		out = append(out, name(id))
	}
	return out
}

func choreFor(t *testing.T, db *database.DB, groupID, actor, choreName string, order ...string) models.Chore {
	t.Helper()
	quoted := ""
	for i, id := range order {
		if i > 0 {
			quoted += ","
		}
		quoted += fmt.Sprintf("%q", id)
	}
	return createChore(t, db, groupID, actor, fmt.Sprintf(
		`{"name": %q, "schedule_type": "interval", "interval_days": 2, "rotation": [%s]}`, choreName, quoted))
}

// fourOfUs is the case that started this: A, B, C and D share three chores,
// and before D leaves they are tangled up in all of them.
//
//   - Kitchen [A B C D]: D covers A's turn, so the next one comes back to A
//     and D has a skip coming.
//   - Bins [D A B C]: D passes their turn as busy, so A holds it and D owes.
//   - Bathroom [B C D A]: B and C do theirs, so D is holding it.
func fourOfUs(t *testing.T) (db *database.DB, g, a, b, c, d string, kitchen, bins, bath models.Chore, name func(string) string) {
	t.Helper()
	db = newTestDB(t)
	a, b, c, d = newUser(t, db, "A"), newUser(t, db, "B"), newUser(t, db, "C"), newUser(t, db, "D")
	g = newGroup(t, db, a, "Flat")
	for _, id := range []string{b, c, d} {
		addMember(t, db, g, id, "member")
	}
	name = nameFor(map[string]string{"A": a, "B": b, "C": c, "D": d})

	kitchen = choreFor(t, db, g, a, "Kitchen", a, b, c, d)
	bins = choreFor(t, db, g, a, "Bins", d, a, b, c)
	bath = choreFor(t, db, g, a, "Bathroom", b, c, d, a)

	markDone(t, db, g, theOpenOcc(t, db, g, a, kitchen.ID).ID, d)
	passOK(t, db, g, theOpenOcc(t, db, g, a, bins.ID).ID, d)
	markDone(t, db, g, theOpenOcc(t, db, g, a, bath.ID).ID, b)
	markDone(t, db, g, theOpenOcc(t, db, g, a, bath.ID).ID, c)
	if got := theOpenOcc(t, db, g, a, bath.ID).AssignedTo; got != d {
		t.Fatalf("set-up: Bathroom is with %s, want D", name(got))
	}
	return
}

// ── leaving ─────────────────────────────────────────────────────────────────

func TestLeavingTakesThemOutOfEveryTurn(t *testing.T) {
	db, g, a, b, c, d, kitchen, bins, bath, name := fourOfUs(t)
	leave(t, db, g, d)

	for _, ch := range []models.Chore{kitchen, bins, bath} {
		if rot := rotationOf(t, db, ch.ID); contains(rot, d) {
			t.Errorf("%s still has D in its order: %v", ch.Name, names(name, rot))
		}
	}
	// The order closes up and otherwise stays as it was.
	if got := names(name, rotationOf(t, db, bath.ID)); fmt.Sprint(got) != "[B C A]" {
		t.Errorf("Bathroom order is %v, want [B C A]", got)
	}

	// The turn D was holding goes to whoever was after D: A.
	held := theOpenOcc(t, db, g, a, bath.ID)
	if held.AssignedTo != a {
		t.Errorf("D's Bathroom turn went to %s, want A, who was next", name(held.AssignedTo))
	}
	// Nothing of D is left in anyone else's turns.
	for _, ch := range []models.Chore{kitchen, bins, bath} {
		o := theOpenOcc(t, db, g, a, ch.ID)
		for what, list := range map[string]string{"chain": chainOf(t, db, o.ID), "debts": debtsOf(t, db, o.ID), "skips": creditsOf(t, db, o.ID)} {
			if list != "" && contains(decodeIDsString(list), d) {
				t.Errorf("%s still has D in its %s", ch.Name, what)
			}
		}
		if o.PassedFrom != nil && *o.PassedFrom == d {
			t.Errorf("%s still says D passed it", ch.Name)
		}
	}

	// And D is never handed a turn again, however the others carry on.
	for round := 0; round < 4; round++ {
		for _, ch := range []models.Chore{kitchen, bins, bath} {
			o := theOpenOcc(t, db, g, a, ch.ID)
			if o.AssignedTo == d {
				t.Fatalf("round %d: %s went to D, who has left", round, ch.Name)
			}
			markDone(t, db, g, o.ID, o.AssignedTo)
		}
	}
	_, _ = b, c
}

// The rule the existing turn tests already set (3.2): a turn somebody covered
// for you is still yours to do, even if they have since left.
func TestATurnCoveredBySomeoneWhoLeftStillComesBack(t *testing.T) {
	db, g, a, _, _, d, kitchen, _, _, name := fourOfUs(t)
	leave(t, db, g, d)
	if got := theOpenOcc(t, db, g, a, kitchen.ID).AssignedTo; got != a {
		t.Errorf("Kitchen is with %s, want A, whose turn D covered", name(got))
	}
}

func TestLeavingKeepsWhatTheyOweAndNotWhatTheyAreOwed(t *testing.T) {
	db, g, _, _, _, d, kitchen, bins, bath, _ := fourOfUs(t)
	leave(t, db, g, d)

	owed := map[string]bool{}
	rows, _ := db.Query(`SELECT chore_id FROM left_debts WHERE group_id = ? AND user_id = ?`, g, d)
	for rows.Next() {
		var id string
		rows.Scan(&id)
		owed[id] = true
	}
	rows.Close()
	if !owed[bins.ID] {
		t.Error("D passed Bins as busy and should still owe it")
	}
	if !owed[bath.ID] {
		t.Error("D was holding Bathroom and should owe it")
	}
	if owed[kitchen.ID] {
		t.Error("D owes nothing on Kitchen: they covered it, and that skip is simply dropped")
	}
}

func TestComingBackPutsThemLastAndBringsBackWhatTheyOwed(t *testing.T) {
	db, g, a, b, c, d, kitchen, bins, bath, name := fourOfUs(t)
	leave(t, db, g, d)

	joined := joinWithCode(t, db, g, a, d)
	if len(joined) != 3 {
		t.Errorf("D rejoined %v, want all three chores, which are everyone's", joined)
	}
	// At the bottom of every order, not back where they were.
	for _, ch := range []models.Chore{kitchen, bins, bath} {
		rot := rotationOf(t, db, ch.ID)
		if rot[len(rot)-1] != d {
			t.Errorf("%s order is %v, want D last", ch.Name, names(name, rot))
		}
	}
	if fmt.Sprint(names(name, rotationOf(t, db, bins.ID))) != "[A B C D]" {
		t.Errorf("Bins was [D A B C] before; coming back puts D at the end, not the front")
	}
	if n := leftDebts(t, db, g, d); n != 0 {
		t.Errorf("%d debts still written down after coming back, want 0", n)
	}

	// What D owed is theirs first: the next Bins and the next Bathroom.
	for _, ch := range []models.Chore{bins, bath} {
		o := theOpenOcc(t, db, g, a, ch.ID)
		markDone(t, db, g, o.ID, o.AssignedTo)
		if got := theOpenOcc(t, db, g, a, ch.ID).AssignedTo; got != d {
			t.Errorf("after D came back the next %s went to %s, want D, who owed it", ch.Name, name(got))
		}
	}
	// Kitchen they owed nothing on, so it carries on as normal.
	o := theOpenOcc(t, db, g, a, kitchen.ID)
	markDone(t, db, g, o.ID, a)
	if got := theOpenOcc(t, db, g, a, kitchen.ID).AssignedTo; got != b {
		t.Errorf("next Kitchen went to %s, want B", name(got))
	}
	_ = c
}

func TestTheOnlyPersonOnAChoreLeavingGivesItToEveryone(t *testing.T) {
	db := newTestDB(t)
	a, b, d := newUser(t, db, "A"), newUser(t, db, "B"), newUser(t, db, "D")
	g := newGroup(t, db, a, "Flat")
	addMember(t, db, g, b, "member")
	addMember(t, db, g, d, "member")
	name := nameFor(map[string]string{"A": a, "B": b, "D": d})
	plants := choreFor(t, db, g, a, "Plants", d)

	leave(t, db, g, d)
	if got := names(name, rotationOf(t, db, plants.ID)); fmt.Sprint(got) != "[A B]" {
		t.Errorf("Plants order is %v, want everyone who is left, [A B]", got)
	}
	if got := theOpenOcc(t, db, g, a, plants.ID).AssignedTo; got != a {
		t.Errorf("Plants went to %s, want A", name(got))
	}
}

func TestATurnFromSomeoneLeavingSkipsWhoeverIsAway(t *testing.T) {
	db := newTestDB(t)
	a, b, c := newUser(t, db, "A"), newUser(t, db, "B"), newUser(t, db, "C")
	g := newGroup(t, db, a, "Flat")
	addMember(t, db, g, b, "member")
	addMember(t, db, g, c, "member")
	name := nameFor(map[string]string{"A": a, "B": b, "C": c})
	ch := choreFor(t, db, g, a, "Kitchen", c, b, a)

	goAway(t, db, g, b, true)
	leave(t, db, g, c)
	if got := theOpenOcc(t, db, g, a, ch.ID).AssignedTo; got != a {
		t.Errorf("C's turn went to %s; B is away, so it should go to A", name(got))
	}
}

func TestLeavingLeavesTheirTasksForAnyone(t *testing.T) {
	db := newTestDB(t)
	a, d := newUser(t, db, "A"), newUser(t, db, "D")
	g := newGroup(t, db, a, "Flat")
	addMember(t, db, g, d, "member")
	task := newTask(t, db, g, "Pay the internet")
	db.Exec(`UPDATE tasks SET assigned_to = ? WHERE id = ?`, d, task)

	leave(t, db, g, d)
	var assigned *string
	db.QueryRow(`SELECT assigned_to FROM tasks WHERE id = ?`, task).Scan(&assigned)
	if assigned != nil {
		t.Errorf("the task is still given to D, who has left")
	}
}

// ── joining ─────────────────────────────────────────────────────────────────

func TestSomeoneNewJoinsTheChoresForEveryoneAtTheEnd(t *testing.T) {
	db := newTestDB(t)
	a, b, e := newUser(t, db, "A"), newUser(t, db, "B"), newUser(t, db, "E")
	g := newGroup(t, db, a, "Flat")
	addMember(t, db, g, b, "member")
	name := nameFor(map[string]string{"A": a, "B": b, "E": e})
	kitchen := choreFor(t, db, g, a, "Kitchen", b, a)
	cat := choreFor(t, db, g, a, "Feed the cat", a)
	oneOff := createChore(t, db, g, a, fmt.Sprintf(
		`{"name": "Fix the shelf", "schedule_type": "one_off", "rotation": [%q, %q]}`, a, b))

	joined := joinWithCode(t, db, g, a, e)
	if fmt.Sprint(joined) != "[Kitchen]" {
		t.Errorf("E was told %v, want [Kitchen]", joined)
	}
	if got := names(name, rotationOf(t, db, kitchen.ID)); fmt.Sprint(got) != "[B A E]" {
		t.Errorf("Kitchen order is %v, want E added at the end: [B A E]", got)
	}
	if got := rotationOf(t, db, cat.ID); contains(got, e) {
		t.Error("Feed the cat is kept to A on purpose, and should stay that way")
	}
	if got := rotationOf(t, db, oneOff.ID); contains(got, e) {
		t.Error("a one-off is not a turn anyone joins")
	}
	// Joining changes nothing about whose turn it is now.
	if got := theOpenOcc(t, db, g, a, kitchen.ID).AssignedTo; got != b {
		t.Errorf("Kitchen moved to %s when E joined, want it still with B", name(got))
	}
}

func TestTheHouseholdCanTurnOffAddingNewPeople(t *testing.T) {
	db := newTestDB(t)
	a, e := newUser(t, db, "A"), newUser(t, db, "E")
	g := newGroup(t, db, a, "Flat")
	h := &GroupHandler{DB: db}
	kitchen := choreFor(t, db, g, a, "Kitchen", a)

	if rec := rename(h, g, a, `{"add_new_members": false}`); rec.Code != 200 {
		t.Fatalf("turning it off: %d %s", rec.Code, rec.Body.String())
	}
	if kind, actor, detail := lastEvent(t, h, g); kind != EventGroupSettingChanged || actor != a || detail != "add new members: off" {
		t.Errorf("feed says %s %s %q, want A turning it off", kind, actor, detail)
	}
	rec := httptest.NewRecorder()
	h.GetGroup(rec, request(http.MethodGet, "/groups/x", "", a, map[string]string{"group_id": g}))
	var got models.GroupWithProgress
	json.NewDecoder(rec.Body).Decode(&got)
	if got.AddNewMembers {
		t.Error("the household still says it adds new people")
	}

	if joined := joinWithCode(t, db, g, a, e); len(joined) != 0 {
		t.Errorf("E was added to %v with the setting off", joined)
	}
	if contains(rotationOf(t, db, kitchen.ID), e) {
		t.Error("E is on Kitchen with the setting off")
	}
}

func TestTheSettingAndTheNameAreEachOptional(t *testing.T) {
	db := newTestDB(t)
	a := newUser(t, db, "A")
	g := newGroup(t, db, a, "Flat")
	h := &GroupHandler{DB: db}
	if rec := rename(h, g, a, `{}`); rec.Code != http.StatusBadRequest {
		t.Errorf("an empty change: %d, want 400", rec.Code)
	}
	if rec := rename(h, g, a, `{"name": "Flat 2"}`); rec.Code != 200 {
		t.Errorf("a rename alone: %d %s", rec.Code, rec.Body.String())
	}
	// Saving what it already is writes nothing to the feed.
	rename(h, g, a, `{"add_new_members": true}`)
	if kind, _, _ := lastEvent(t, h, g); kind == EventGroupSettingChanged {
		t.Error("saving the setting unchanged wrote to the feed")
	}
}

func TestADebtOnAChoreTheyAreNotBackOnWaitsUntilTheyAre(t *testing.T) {
	db := newTestDB(t)
	a, b, c, d := newUser(t, db, "A"), newUser(t, db, "B"), newUser(t, db, "C"), newUser(t, db, "D")
	g := newGroup(t, db, a, "Flat")
	for _, id := range []string{b, c, d} {
		addMember(t, db, g, id, "member")
	}
	name := nameFor(map[string]string{"A": a, "B": b, "C": c, "D": d})
	// Only C and D do the car; D is holding it.
	car := choreFor(t, db, g, a, "Wash the car", d, c)

	leave(t, db, g, d)
	joinWithCode(t, db, g, a, d)
	// [C] is not everyone, so D is not put back on it automatically.
	if contains(rotationOf(t, db, car.ID), d) {
		t.Fatal("D was put back on a chore that is not everyone's")
	}
	if n := leftDebts(t, db, g, d); n != 1 {
		t.Fatalf("%d debts written down, want the car's still waiting", n)
	}

	// Added back by hand: what D owed on it comes back with them.
	setRotation(t, db, g, car.ID, a, []string{c, d})
	o := theOpenOcc(t, db, g, a, car.ID)
	markDone(t, db, g, o.ID, o.AssignedTo)
	if got := theOpenOcc(t, db, g, a, car.ID).AssignedTo; got != d {
		t.Errorf("the next car wash went to %s, want D, who owed one", name(got))
	}
	if n := leftDebts(t, db, g, d); n != 0 {
		t.Errorf("%d debts still written down, want 0", n)
	}
}

// ── people who left before this rule ────────────────────────────────────────

func TestSettleDepartedTidiesUpSomeoneWhoLeftTheOldWay(t *testing.T) {
	db, g, a, _, _, d, kitchen, bins, bath, name := fourOfUs(t)
	// The old leave: the membership goes and nothing else does.
	if _, err := db.Exec(`DELETE FROM group_members WHERE group_id = ? AND user_id = ?`, g, d); err != nil {
		t.Fatal(err)
	}

	if err := SettleDeparted(db); err != nil {
		t.Fatalf("settling: %v", err)
	}
	for _, ch := range []models.Chore{kitchen, bins, bath} {
		if contains(rotationOf(t, db, ch.ID), d) {
			t.Errorf("%s still has D in it", ch.Name)
		}
	}
	if got := theOpenOcc(t, db, g, a, bath.ID).AssignedTo; got != a {
		t.Errorf("D's Bathroom turn went to %s, want A", name(got))
	}
	// Running it again finds nobody and changes nothing.
	before := rotationOf(t, db, bath.ID)
	if err := SettleDeparted(db); err != nil {
		t.Fatalf("settling again: %v", err)
	}
	if fmt.Sprint(rotationOf(t, db, bath.ID)) != fmt.Sprint(before) {
		t.Error("a second run changed something")
	}
}

func decodeIDsString(s string) []string {
	return decodeIDs(sql.NullString{String: s, Valid: s != ""})
}
