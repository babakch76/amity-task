package handlers

import (
	"database/sql"
	"encoding/json"
	"log"
	"net/http"
	"sort"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/google/uuid"

	"task-manager-backend/internal/database"
	"task-manager-backend/internal/middleware"
	"task-manager-backend/internal/models"
)

// Booking: "I'll have the washing machine on Saturday from two."
//
// People in a house run into each other over the same appliance or room. This
// lets somebody say when they want it, so the others can see it coming. It is a
// notice on the board and nothing more, and it is careful about what it does
// not do:
//
//   - **It does not refuse.** Two people may book the same thing at the same
//     time. The app points the overlap out before they confirm, and after that
//     it is theirs to sort out in the kitchen. The server has no list of
//     appliances to clash on anyway: "what" is whatever they typed.
//
//   - **Nobody approves it.** Any member can book, and only the person who
//     booked can cancel. An owner gets no say over a housemate's Saturday.
//
//   - **No reminders.** It is not a chore. Nothing is owed and nothing is late.
//
// Finished bookings are left out of the list by comparing in Go, not in SQL:
// a string comparison on a DATETIME column only works if every row happens to
// be in the same format.

const (
	// bookingWhatMax fits on one line of the board.
	bookingWhatMax = 40
	// bookingNoteMax is a sentence, not a place for a conversation.
	bookingNoteMax = 140
)

// BookingHandler holds the database connection for the booking routes.
type BookingHandler struct {
	DB *database.DB
}

// ListBookings handles GET /groups/{group_id}/bookings: everything still to
// come or happening now, soonest first.
func (h *BookingHandler) ListBookings(w http.ResponseWriter, r *http.Request) {
	groupID := r.PathValue("group_id")

	rows, err := h.DB.Query(
		`SELECT b.id, b.group_id, b.user_id, u.username, b.what, b.note,
		        b.starts_at, b.ends_at, b.created_at
		 FROM bookings b JOIN users u ON u.id = b.user_id
		 WHERE b.group_id = ?`,
		groupID,
	)
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	now := time.Now()
	bookings := []models.Booking{}
	for rows.Next() {
		var b models.Booking
		if err := rows.Scan(
			&b.ID, &b.GroupID, &b.UserID, &b.Username, &b.What, &b.Note,
			&b.StartsAt, &b.EndsAt, &b.CreatedAt,
		); err != nil {
			jsonError(w, "internal error", http.StatusInternalServerError)
			return
		}
		if b.EndsAt.After(now) {
			bookings = append(bookings, b)
		}
	}
	if err := rows.Err(); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}

	sort.Slice(bookings, func(i, j int) bool {
		return bookings[i].StartsAt.Before(bookings[j].StartsAt)
	})
	jsonResponse(w, http.StatusOK, bookings)
}

// CreateBooking handles POST /groups/{group_id}/bookings.
func (h *BookingHandler) CreateBooking(w http.ResponseWriter, r *http.Request) {
	groupID := r.PathValue("group_id")
	userID := middleware.GetUserID(r)

	var req models.CreateBookingRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		jsonError(w, "invalid request body", http.StatusBadRequest)
		return
	}

	what := strings.TrimSpace(req.What)
	note := strings.TrimSpace(req.Note)
	switch {
	case what == "":
		jsonError(w, "say what you're booking", http.StatusBadRequest)
		return
	case utf8.RuneCountInString(what) > bookingWhatMax:
		jsonError(w, "that name is too long", http.StatusBadRequest)
		return
	case utf8.RuneCountInString(note) > bookingNoteMax:
		jsonError(w, "that note is too long", http.StatusBadRequest)
		return
	case req.StartsAt.IsZero() || req.EndsAt.IsZero():
		jsonError(w, "say when it starts and ends", http.StatusBadRequest)
		return
	case !req.EndsAt.After(req.StartsAt):
		jsonError(w, "it has to end after it starts", http.StatusBadRequest)
		return
	case !req.EndsAt.After(time.Now()):
		// Starting in the past is fine: "I've just put a wash on". Being over
		// already is not, because it would never appear on the board.
		jsonError(w, "that time has already passed", http.StatusBadRequest)
		return
	}

	booking := models.Booking{
		ID:        uuid.New().String(),
		GroupID:   groupID,
		UserID:    userID,
		What:      what,
		Note:      note,
		StartsAt:  req.StartsAt.UTC(),
		EndsAt:    req.EndsAt.UTC(),
		CreatedAt: time.Now().UTC(),
	}

	tx, err := h.DB.Begin()
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback()

	if _, err := tx.Exec(
		`INSERT INTO bookings (id, group_id, user_id, what, note, starts_at, ends_at, created_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		booking.ID, booking.GroupID, booking.UserID, booking.What, booking.Note,
		booking.StartsAt, booking.EndsAt, booking.CreatedAt,
	); err != nil {
		log.Printf("CreateBooking: insert failed for group %s: %v", groupID, err)
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := recordActivity(tx, groupID, userID, EventBookingAdded, nil, what); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := tx.QueryRow(
		`SELECT username FROM users WHERE id = ?`, userID,
	).Scan(&booking.Username); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := tx.Commit(); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}

	jsonResponse(w, http.StatusCreated, booking)
}

// CancelBooking handles DELETE /groups/{group_id}/bookings/{booking_id}. It is
// the booker's own plan, so it is theirs alone to call off.
func (h *BookingHandler) CancelBooking(w http.ResponseWriter, r *http.Request) {
	groupID := r.PathValue("group_id")
	bookingID := r.PathValue("booking_id")
	userID := middleware.GetUserID(r)

	tx, err := h.DB.Begin()
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback()

	var owner, what string
	err = tx.QueryRow(
		`SELECT user_id, what FROM bookings WHERE id = ? AND group_id = ?`,
		bookingID, groupID,
	).Scan(&owner, &what)
	if err == sql.ErrNoRows {
		jsonError(w, "booking not found", http.StatusNotFound)
		return
	}
	if err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if owner != userID {
		jsonError(w, "only the person who booked it can cancel it", http.StatusForbidden)
		return
	}

	if _, err := tx.Exec(`DELETE FROM bookings WHERE id = ?`, bookingID); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := recordActivity(tx, groupID, userID, EventBookingCancelled, nil, what); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}
	if err := tx.Commit(); err != nil {
		jsonError(w, "internal error", http.StatusInternalServerError)
		return
	}

	jsonResponse(w, http.StatusOK, map[string]string{"message": "booking cancelled"})
}
