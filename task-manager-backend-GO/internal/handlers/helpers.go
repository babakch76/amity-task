package handlers

import (
	"encoding/json"
	"net/http"
	"time"
)

// clock is the handlers' idea of now. A variable so a test can move time on
// between two completions of the same chore: a turn cannot be marked done
// before halfway to its day (see canDoFrom), and a test that completes a
// chore three times in a row would otherwise be refused at the second.
var clock = time.Now

func jsonResponse(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func jsonError(w http.ResponseWriter, message string, status int) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(map[string]string{"error": message})
}

// Membership roles. The set is fixed by a CHECK constraint on
// group_members.role, so these are the only legal values.
//
// RoleAdmin is no longer assigned to anyone: the manager role and the
// permission it carried were removed when the chore spec made chore editing
// explicitly open to every member ("Editing a chore is open to any member",
// F2). The constant stays because historical rows may still hold the value and
// the CHECK constraint still accepts it — SQLite cannot alter a CHECK in place.
const (
	RoleOwner  = "owner"
	RoleAdmin  = "admin"
	RoleMember = "member"
)
