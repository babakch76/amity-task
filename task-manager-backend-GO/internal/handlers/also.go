package handlers

import (
	"database/sql"
	"log"
	"net/http"
	"time"

	"task-manager-backend/internal/middleware"
	"task-manager-backend/internal/models"
)

// "I worked on this too" — a second contributor on a completed cycle.
//
// The interviews' most repeated quiet grievance: somebody marks the bathroom
// done, somebody else redoes half of it rather than start an argument, and the
// second person's work leaves no trace. This records it, and is careful about
// everything it does *not* do:
//
//   - **Credit stays with the doer.** done_by still decides whose turn it
//     counted as. Rotation, debts and the per-person history count are all
//     untouched, so adding yourself cannot move a turn or pad a number.
//
//   - **No note, no reason.** "Also Sara" is a fact. A text box beside somebody
//     else's completion would be a comment thread and a quality verdict, which
//     constraint 1 rules out.
//
//   - **Only for a while.** alsoWindow, measured from the completion. Long
//     enough to cover "I finished it off the next morning"; short enough that
//     it cannot become a way to annotate the whole archive after an argument.

// alsoWindow is how long after a cycle is marked done somebody else may add
// themselves to it.
const alsoWindow = 48 * time.Hour

// AddAlso handles POST /groups/{group_id}/occurrences/{occurrence_id}/also.
func (h *ChoreHandler) AddAlso(w http.ResponseWriter, r *http.Request) {
	groupID := r.PathValue("group_id")
	occurrenceID := r.PathValue("occurrence_id")
	userID := middleware.GetUserID(r)

	current, err := h.loadOccurrence(occurrenceID)
	if err == sql.ErrNoRows || (current != nil && current.GroupID != groupID) {
		jsonError(w, "occurrence not found", http.StatusNotFound)
		return
	}
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}

	if current.Status != models.OccurrenceDone || current.DoneAt == nil {
		jsonError(w, "it isn't done yet, so mark it done instead", http.StatusConflict)
		return
	}
	// Away is not at home, for helping as much as for doing (see UpdateOccurrence).
	if away, err := h.awayMembers(groupID); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	} else if away[userID] {
		jsonError(w, "you're away, so you can't say you worked on this. Say you're back first.", http.StatusConflict)
		return
	}
	if current.DoneBy != nil && *current.DoneBy == userID {
		jsonError(w, "you are already down as the one who did it", http.StatusConflict)
		return
	}
	if time.Since(*current.DoneAt) > alsoWindow {
		jsonError(w, "too long ago to add yourself to", http.StatusConflict)
		return
	}
	for _, a := range current.AlsoBy {
		if a.UserID == userID {
			jsonError(w, "you are already on this one", http.StatusConflict)
			return
		}
	}

	tx, err := h.DB.Begin()
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback()

	if _, err := tx.Exec(
		`INSERT INTO occurrence_also_by (occurrence_id, user_id, added_at) VALUES (?, ?, ?)`,
		occurrenceID, userID, time.Now().UTC(),
	); err != nil {
		log.Printf("AddAlso: insert failed for %s: %v", occurrenceID, err)
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := recordActivity(tx, groupID, userID, EventOccurrenceAlso, &occurrenceID, current.ChoreName); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := tx.Commit(); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}

	h.respondWithOccurrence(w, occurrenceID)
}

// RemoveAlso handles DELETE /groups/{group_id}/occurrences/{occurrence_id}/also
// — taking back your own "I worked on this too".
//
// Same terms as undoing a completion: only your own, and only inside
// undoWindow. It is for the mis-tap, not for rewriting the record later.
func (h *ChoreHandler) RemoveAlso(w http.ResponseWriter, r *http.Request) {
	groupID := r.PathValue("group_id")
	occurrenceID := r.PathValue("occurrence_id")
	userID := middleware.GetUserID(r)

	current, err := h.loadOccurrence(occurrenceID)
	if err == sql.ErrNoRows || (current != nil && current.GroupID != groupID) {
		jsonError(w, "occurrence not found", http.StatusNotFound)
		return
	}
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}

	var mine *models.AlsoBy
	for i := range current.AlsoBy {
		if current.AlsoBy[i].UserID == userID {
			mine = &current.AlsoBy[i]
		}
	}
	if mine == nil {
		jsonError(w, "you are not on this one", http.StatusNotFound)
		return
	}
	if time.Since(mine.AddedAt) > undoWindow {
		jsonError(w, "the undo window has passed", http.StatusForbidden)
		return
	}

	tx, err := h.DB.Begin()
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback()

	if _, err := tx.Exec(
		`DELETE FROM occurrence_also_by WHERE occurrence_id = ? AND user_id = ?`,
		occurrenceID, userID,
	); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := recordActivity(tx, groupID, userID, EventOccurrenceAlsoWithdrawn, &occurrenceID, current.ChoreName); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := tx.Commit(); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}

	h.respondWithOccurrence(w, occurrenceID)
}

func (h *ChoreHandler) respondWithOccurrence(w http.ResponseWriter, occurrenceID string) {
	updated, err := h.loadOccurrence(occurrenceID)
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	jsonResponse(w, http.StatusOK, updated)
}

// alsoByFor loads the "also worked on it" entries for every occurrence that
// matches where, keyed by occurrence id and oldest first. where is a fixed
// fragment over the alias o; its arguments are passed through.
func alsoByFor(q interface {
	Query(string, ...any) (*sql.Rows, error)
}, where string, args ...any) (map[string][]models.AlsoBy, error) {
	rows, err := q.Query(`
		SELECT a.occurrence_id, a.user_id, u.username, a.added_at
		FROM occurrence_also_by a
		JOIN occurrences o ON o.id = a.occurrence_id
		JOIN users u ON u.id = a.user_id
		WHERE `+where+`
		ORDER BY a.added_at, a.rowid`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := map[string][]models.AlsoBy{}
	for rows.Next() {
		var occurrenceID string
		var a models.AlsoBy
		if err := rows.Scan(&occurrenceID, &a.UserID, &a.Username, &a.AddedAt); err != nil {
			return nil, err
		}
		out[occurrenceID] = append(out[occurrenceID], a)
	}
	return out, rows.Err()
}
