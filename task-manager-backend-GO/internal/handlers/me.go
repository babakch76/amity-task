package handlers

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"net/http"
	"net/mail"
	"strings"

	"task-manager-backend/internal/database"
	"task-manager-backend/internal/middleware"
	"task-manager-backend/internal/models"
)

// MeHandler serves the caller's own account: the settings that belong to a
// person rather than to a group.
//
// It exists for F3's quiet hours. Reminders are scheduled on the device, but
// the window itself lives on the server — reinstalling the app loses its
// Keystore-backed token and everything stored beside it, and a quiet-hours
// setting that vanishes when you reinstall is one people will not set twice.
type MeHandler struct {
	DB *database.DB
}

// GetMe handles GET /me.
func (h *MeHandler) GetMe(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r)

	user, err := h.load(userID)
	if err == sql.ErrNoRows {
		jsonError(w, "user not found", http.StatusNotFound)
		return
	}
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	jsonResponse(w, http.StatusOK, user)
}

// UpdateMe handles PATCH /me — the quiet-hours window, the username, and the
// email address.
//
// Every field is optional, so one can be moved without restating the others,
// and an empty patch is refused rather than silently doing nothing.
func (h *MeHandler) UpdateMe(w http.ResponseWriter, r *http.Request) {
	userID := middleware.GetUserID(r)

	var req models.UpdateMeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, "invalid request body", http.StatusBadRequest)
		return
	}

	setClauses := []string{}
	args := []any{}

	// Trimmed before anything looks at it: a trailing space is invisible in
	// the field it was typed into and would make two housemates' names look
	// identical in the member list while comparing unequal everywhere else.
	if req.Username != nil {
		name := strings.TrimSpace(*req.Username)
		if name == "" {
			jsonError(w, "username cannot be empty", http.StatusBadRequest)
			return
		}
		if len([]rune(name)) > models.MaxUsernameLength {
			jsonError(
				w,
				fmt.Sprintf("username must be %d characters or fewer", models.MaxUsernameLength),
				http.StatusBadRequest,
			)
			return
		}
		setClauses = append(setClauses, "username = ?")
		args = append(args, name)
	}
	if req.Email != nil {
		address := strings.TrimSpace(*req.Email)
		// Email is the login identifier, so a typo here locks the person out
		// of their own account. Register accepts anything non-empty; this
		// endpoint is stricter on purpose, because the failure is worse.
		parsed, err := mail.ParseAddress(address)
		if err != nil || parsed.Address != address {
			jsonError(w, "that does not look like an email address", http.StatusBadRequest)
			return
		}
		setClauses = append(setClauses, "email = ?")
		args = append(args, address)
	}

	if req.QuietFrom != nil {
		if err := validateClockTime(*req.QuietFrom); err != nil {
			jsonError(w, "quiet_from must be HH:MM", http.StatusBadRequest)
			return
		}
		setClauses = append(setClauses, "quiet_from = ?")
		args = append(args, *req.QuietFrom)
	}
	if req.QuietTo != nil {
		if err := validateClockTime(*req.QuietTo); err != nil {
			jsonError(w, "quiet_to must be HH:MM", http.StatusBadRequest)
			return
		}
		setClauses = append(setClauses, "quiet_to = ?")
		args = append(args, *req.QuietTo)
	}

	if len(setClauses) == 0 {
		jsonError(w, "no fields to update", http.StatusBadRequest)
		return
	}

	// Equal ends would mean a window of zero length or of a whole day —
	// unanswerable, and the honest reading differs by which you assume. Refuse
	// it rather than pick one. Turning reminders off entirely is a separate
	// setting, not a degenerate window.
	//
	// Only worth checking when the patch actually moves the window: a rename
	// has no business failing because of the clock.
	if req.QuietFrom != nil || req.QuietTo != nil {
		current, err := h.load(userID)
		if err != nil {
			jsonError(w, "internal error", http.StatusInternalServerError)
			return
		}
		from, to := current.QuietFrom, current.QuietTo
		if req.QuietFrom != nil {
			from = *req.QuietFrom
		}
		if req.QuietTo != nil {
			to = *req.QuietTo
		}
		if from == to {
			jsonError(w, "quiet hours cannot start and end at the same time", http.StatusBadRequest)
			return
		}
	}

	args = append(args, userID)
	if _, err := h.DB.Exec(
		"UPDATE users SET "+strings.Join(setClauses, ", ")+" WHERE id = ?", args...,
	); err != nil {
		// username and email are both UNIQUE. A collision is the caller's
		// problem and gets register's wording, because it is register's
		// situation; anything else is ours and stays a 500.
		if strings.Contains(err.Error(), "UNIQUE constraint") {
			jsonError(w, "username or email already taken", http.StatusConflict)
			return
		}
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}

	updated, err := h.load(userID)
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	jsonResponse(w, http.StatusOK, updated)
}

// load reads the caller's row. The error is returned unwrapped so callers can
// still compare it against sql.ErrNoRows.
func (h *MeHandler) load(userID string) (*models.User, error) {
	var u models.User
	err := h.DB.QueryRow(
		`SELECT id, username, email, created_at, quiet_from, quiet_to FROM users WHERE id = ?`,
		userID,
	).Scan(&u.ID, &u.Username, &u.Email, &u.CreatedAt, &u.QuietFrom, &u.QuietTo)
	if err != nil {
		return nil, err
	}
	return &u, nil
}
