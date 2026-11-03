package handlers

import (
	"database/sql"
	"log"
	"math"
	"time"

	"task-manager-backend/internal/database"
	"task-manager-backend/internal/models"
)

// A household where everyone is away is paused.
//
// Nobody is home to do anything, so nothing should move or go late: no chore
// can be passed (there is nobody to pass it to), nothing counts as overdue, and
// when the first person is back every open chore's date has moved on by the
// days the household was empty. Something due two days into the holiday is due
// two days after the return, so nobody comes back to a pile of late chores.
//
// Nothing can be marked done either, because nobody away can mark anything done
// (UpdateOccurrence). Whoever comes home early says they are back first.
//
// There is no scheduler, so like rollForwardFixedDates this runs on the board's
// reads and writes. groups.paused_since records when the pause began; it is set
// the first time a pause is seen and cleared, with the dates moved, the first
// time it is seen to be over. The dates move by whole days, so a chore needed
// by 20:00 is still needed by 20:00.
//
// Fixed-day chores are left out: they already roll on to their next day by
// themselves (rollForwardFixedDates), and the bins still go out on a Tuesday.

// pauseDays is how many days a pause of length d moves a date: to the nearest
// day, so an evening with everyone out moves nothing.
func pauseDays(d time.Duration) int {
	if d <= 0 {
		return 0
	}
	return int(math.Round(d.Hours() / 24))
}

// pausedDue is where a date lands after a pause from since to until. A turn
// that only began during the pause is moved by the part of it that it was
// waiting through.
func pausedDue(due, created, since, until time.Time) time.Time {
	from := since
	if created.After(from) {
		from = created
	}
	return due.AddDate(0, 0, pauseDays(until.Sub(from)))
}

// awayPeriod is one row of away_periods, as the pause needs it.
type awayPeriod struct {
	userID  string
	started time.Time
	ended   *time.Time
	endsAt  *time.Time
}

// over is when this absence stopped, or nil while it still holds at now.
func (p awayPeriod) over(now time.Time) *time.Time {
	if p.ended != nil {
		return p.ended
	}
	if p.endsAt != nil && !p.endsAt.After(now) {
		return p.endsAt
	}
	return nil
}

func loadAwayPeriods(db *database.DB, groupID string) ([]awayPeriod, error) {
	rows, err := db.Query(`
		SELECT ap.user_id, ap.started_at, ap.ended_at, ap.ends_at
		FROM away_periods ap
		JOIN group_members gm ON gm.group_id = ap.group_id AND gm.user_id = ap.user_id
		WHERE ap.group_id = ?`, groupID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []awayPeriod
	for rows.Next() {
		var p awayPeriod
		if err := rows.Scan(&p.userID, &p.started, &p.ended, &p.endsAt); err != nil {
			return nil, err
		}
		out = append(out, p)
	}
	return out, rows.Err()
}

// everyoneAway reports whether nobody in the household is home, and if so
// since when: the moment the last of them went.
func everyoneAway(db *database.DB, groupID string, now time.Time) (bool, time.Time, error) {
	var members int
	if err := db.QueryRow(`SELECT COUNT(*) FROM group_members WHERE group_id = ?`, groupID).Scan(&members); err != nil {
		return false, time.Time{}, err
	}
	periods, err := loadAwayPeriods(db, groupID)
	if err != nil {
		return false, time.Time{}, err
	}
	away := map[string]bool{}
	var since time.Time
	for _, p := range periods {
		if p.over(now) != nil {
			continue
		}
		away[p.userID] = true
		if p.started.After(since) {
			since = p.started
		}
	}
	return members > 0 && len(away) == members, since, nil
}

// pauseEnd is the first moment after since that somebody was home again: a
// return, a planned return date passing, or a new member joining.
//
// Changing your return date closes one away period and opens the next at the
// same moment. That is not coming home, so an end that another of the same
// person's periods covers does not count.
func pauseEnd(db *database.DB, groupID string, since, now time.Time) (time.Time, error) {
	periods, err := loadAwayPeriods(db, groupID)
	if err != nil {
		return now, err
	}
	end := now
	stillAway := func(userID string, at time.Time) bool {
		for _, p := range periods {
			if p.userID != userID || p.started.After(at.Add(2*time.Second)) {
				continue
			}
			if o := p.over(now); o == nil || o.After(at) {
				return true
			}
		}
		return false
	}
	for _, p := range periods {
		o := p.over(now)
		if o == nil || !o.After(since) || !o.Before(end) || stillAway(p.userID, *o) {
			continue
		}
		end = *o
	}

	rows, err := db.Query(`SELECT joined_at FROM group_members WHERE group_id = ?`, groupID)
	if err != nil {
		return end, err
	}
	defer rows.Close()
	for rows.Next() {
		var joined time.Time
		if err := rows.Scan(&joined); err != nil {
			return end, err
		}
		if joined.After(since) && joined.Before(end) {
			end = joined
		}
	}
	return end, rows.Err()
}

// settlePause keeps a household's pause in step with who is away, and returns
// when the current pause began, or nil when somebody is home.
func settlePause(db *database.DB, groupID string) (*time.Time, error) {
	now := time.Now().UTC()
	var recorded *time.Time
	if err := db.QueryRow(`SELECT paused_since FROM groups WHERE id = ?`, groupID).Scan(&recorded); err != nil {
		return nil, err
	}
	all, since, err := everyoneAway(db, groupID, now)
	if err != nil {
		return nil, err
	}

	switch {
	case all && recorded == nil:
		if _, err := db.Exec(
			`UPDATE groups SET paused_since = ? WHERE id = ? AND paused_since IS NULL`, since, groupID,
		); err != nil {
			return nil, err
		}
		return &since, nil
	case all:
		return recorded, nil
	case recorded == nil:
		return nil, nil
	}

	// Somebody is home again: move the dates on, once.
	end, err := pauseEnd(db, groupID, *recorded, now)
	if err != nil {
		return nil, err
	}
	tx, err := db.Begin()
	if err != nil {
		return nil, err
	}
	defer tx.Rollback()

	// Claimed first, so two requests arriving together cannot both move the
	// dates. The second finds nothing to clear and leaves.
	res, err := tx.Exec(`UPDATE groups SET paused_since = NULL WHERE id = ? AND paused_since IS NOT NULL`, groupID)
	if err != nil {
		return nil, err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return nil, nil
	}

	rows, err := tx.Query(`
		SELECT o.id, o.due_date, o.created_at
		FROM occurrences o JOIN chores c ON c.id = o.chore_id
		WHERE o.group_id = ? AND o.status = 'open' AND o.due_date IS NOT NULL
		  AND c.schedule_type <> 'fixed_date'`, groupID)
	if err != nil {
		return nil, err
	}
	type move struct {
		id  string
		due time.Time
	}
	var moves []move
	for rows.Next() {
		var (
			id           string
			due, created time.Time
		)
		if err := rows.Scan(&id, &due, &created); err != nil {
			rows.Close()
			return nil, err
		}
		if moved := pausedDue(due, created, *recorded, end); !moved.Equal(due) {
			moves = append(moves, move{id, moved})
		}
	}
	if err := rows.Err(); err != nil {
		rows.Close()
		return nil, err
	}
	rows.Close()

	for _, m := range moves {
		if _, err := tx.Exec(`UPDATE occurrences SET due_date = ? WHERE id = ?`, m.due, m.id); err != nil {
			return nil, err
		}
	}
	return nil, tx.Commit()
}

// showPaused moves the board's open dates on by the pause so far, without
// writing them, so that nothing reads as late while everyone is away and the
// dates people see are the ones they will come back to.
func showPaused(db *database.DB, groupID string, since time.Time, occurrences []models.Occurrence) {
	fixed := map[string]bool{}
	rows, err := db.Query(`SELECT id FROM chores WHERE group_id = ? AND schedule_type = 'fixed_date'`, groupID)
	if err != nil {
		log.Printf("showPaused: chore lookup failed for group %s: %v", groupID, err)
		return
	}
	for rows.Next() {
		var id string
		if rows.Scan(&id) == nil {
			fixed[id] = true
		}
	}
	rows.Close()

	now := time.Now().UTC()
	for i := range occurrences {
		o := &occurrences[i]
		if o.Status != models.OccurrenceOpen || o.DueDate == nil || fixed[o.ChoreID] {
			continue
		}
		moved := pausedDue(*o.DueDate, o.CreatedAt, since, now)
		o.DueDate = &moved
	}
}

// handOnWhileAway moves every open turn of userID's in this household to the
// next person at home, as if they had passed it: it comes back to them when
// they return, because it was already theirs before they left. Called when
// somebody goes away, unless that leaves the household paused.
//
// A turn with nobody to take it (everyone else in its rotation away, or
// everyone at home has already passed it) stays where it is. Returns how many
// moved, so the app can say what happened rather than guess.
func handOnWhileAway(db *database.DB, groupID, userID string) (int, error) {
	h := &ChoreHandler{DB: db}
	rows, err := db.Query(
		`SELECT id FROM occurrences WHERE group_id = ? AND assigned_to = ? AND status = 'open'`,
		groupID, userID,
	)
	if err != nil {
		return 0, err
	}
	var ids []string
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			rows.Close()
			return 0, err
		}
		ids = append(ids, id)
	}
	rows.Close()
	if len(ids) == 0 {
		return 0, nil
	}

	away, err := h.awayMembers(groupID)
	if err != nil {
		return 0, err
	}
	now := time.Now()
	moved := 0
	for _, id := range ids {
		current, err := h.loadOccurrence(id)
		if err != nil {
			return moved, err
		}
		chore, err := h.loadChore(current.ChoreID)
		if err != nil {
			return moved, err
		}
		chain := appendOnce(current.PassedChain, userID)
		if everyoneHasPassed(chore.Rotation, chain, away) {
			continue
		}
		receiver := nextCoverer(chore.Rotation, current, userID, away)
		if receiver == userID || away[receiver] {
			continue
		}
		if err := writePass(db, current, receiver, passDueDate(chore, current, now), chain, now); err != nil {
			return moved, err
		}
		moved++
	}
	return moved, nil
}

// settlePauseQuietly is settlePause for callers that should carry on if it
// fails: a board that renders is better than one that refuses to.
func settlePauseQuietly(db *database.DB, groupID string) *time.Time {
	since, err := settlePause(db, groupID)
	if err != nil && err != sql.ErrNoRows {
		log.Printf("pause: settling failed for group %s: %v", groupID, err)
	}
	return since
}
