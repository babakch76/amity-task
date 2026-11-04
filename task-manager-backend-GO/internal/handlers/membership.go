package handlers

// Joining and leaving, as far as the turns go.
//
// The rule, in one place:
//
//   - **Leaving** takes you out of every chore in that household. Each turn you
//     were holding goes to the next person at home, as an ordinary turn: nobody
//     owes anything for it. Every turn you owe (one you were holding, one you
//     passed as busy, one that was coming back to you) is written down in
//     left_debts, apart from everyone else's turns, and your name is cleared
//     out of theirs. Skips you had earned go: there is nobody left to skip.
//     One-off tasks given to you become nobody's, for anyone to pick up.
//
//   - **Joining** puts you at the end of every repeating chore that already
//     includes everyone, when the household has add_new_members on (it is by
//     default). A chore kept to a few people on purpose stays as it is. At the
//     end, so a new person does not get the very next turn on day one.
//
//   - **Coming back** is joining. You go to the end of the line like anyone
//     new; your old place is not kept. What you owed when you left comes back
//     on each chore you are in again: its next turn is yours first. That is the
//     point of keeping it, so leaving and rejoining is never a way to clear
//     your turns.
//
// Debts already work this way when someone is edited out of a chore (see
// debtsAfter and turn rule 3.3): the rotation is what makes a debt count. What
// was missing was leaving ever taking anyone out of a rotation, so a person
// who left went on being handed turns nobody would do.

import (
	"database/sql"
	"fmt"
	"log"
	"time"

	"task-manager-backend/internal/database"
	"task-manager-backend/internal/models"
)

// queryer is satisfied by both *database.DB and *sql.Tx.
type queryer interface {
	Query(query string, args ...any) (*sql.Rows, error)
	QueryRow(query string, args ...any) *sql.Row
}

// openTurn is the part of an open occurrence that leaving has to rewrite.
type openTurn struct {
	id, choreID, assignedTo string
	neededBy                *string
	dueDate                 *time.Time
	chain, debts, credits   []string
	passedFrom, resumeAfter sql.NullString
}

// departRotations takes userID out of every chore in groupID, inside tx, and
// returns how many open turns went to someone else.
func departRotations(tx *sql.Tx, groupID, userID string, now time.Time) (int, error) {
	rotations, err := groupRotations(tx, groupID)
	if err != nil {
		return 0, err
	}
	turns, err := openTurns(tx, groupID)
	if err != nil {
		return 0, err
	}
	away, err := awayIn(tx, groupID, now)
	if err != nil {
		return 0, err
	}
	members, err := membersInOrder(tx, groupID)
	if err != nil {
		return 0, err
	}
	stayers := without(members, userID)

	// Each chore's order without them. If they were the only one on it, it
	// becomes everyone's who is left: a chore always has a name on it.
	newOrder := map[string][]string{}
	for choreID, rotation := range rotations {
		if !contains(rotation, userID) {
			continue
		}
		next := without(rotation, userID)
		if len(next) == 0 {
			next = stayers
		}
		newOrder[choreID] = next
	}

	handed := 0
	owes := map[string]bool{}
	for _, t := range turns {
		rotation := rotations[t.choreID]
		if t.assignedTo == userID || contains(t.debts, userID) || contains(t.chain, userID) {
			owes[t.choreID] = true
		}

		left, changed := newOrder[t.choreID]
		if !changed {
			left = rotation
			if t.assignedTo == userID && len(left) == 0 {
				left = stayers
				newOrder[t.choreID] = left
			}
		}

		assignee := t.assignedTo
		due := t.dueDate
		var passedAt any
		if t.assignedTo == userID {
			if len(left) == 0 {
				// Nobody is left at all. Only reachable if the household is
				// being emptied, and then it is deleted with everything in it.
				continue
			}
			assignee = nextAfter(rotation, left, userID, away)
			// Landing on somebody already late would make a favour feel like a
			// penalty, the same as for a pass.
			due = passDueDate(&models.Chore{NeededByTime: t.neededBy}, &models.Occurrence{DueDate: t.dueDate}, now)
			passedAt = now.UTC()
			handed++
		}

		chain := without(t.chain, userID)
		passedFrom := t.passedFrom
		if passedFrom.Valid && passedFrom.String == userID {
			passedFrom = sql.NullString{}
			if len(chain) > 0 {
				passedFrom = sql.NullString{String: chain[0], Valid: true}
			}
		}
		resumeAfter := t.resumeAfter
		if resumeAfter.Valid && resumeAfter.String == userID {
			resumeAfter = sql.NullString{}
		}

		query := `UPDATE occurrences
			SET assigned_to = ?, due_date = ?, passed_chain = ?, pending_debts = ?,
			    skip_credits = ?, passed_from = ?, resume_after = ?`
		args := []any{assignee, due, encodeIDs(chain), encodeIDs(without(t.debts, userID)),
			encodeIDs(without(t.credits, userID)), passedFrom, resumeAfter}
		if passedAt != nil {
			// It is theirs from now: this is the moment it changed hands.
			query += `, passed_at = ?`
			args = append(args, passedAt)
		}
		query += ` WHERE id = ?`
		args = append(args, t.id)
		if _, err := tx.Exec(query, args...); err != nil {
			return handed, fmt.Errorf("rewrite turn %s: %w", t.id, err)
		}
	}

	// Kept apart from everyone else's turns, for if they come back. One per
	// chore at most, the same as anyone's debt (turn rule 3.5).
	for choreID := range owes {
		if _, err := tx.Exec(
			`INSERT OR IGNORE INTO left_debts (group_id, user_id, chore_id, left_at) VALUES (?,?,?,?)`,
			groupID, userID, choreID, now.UTC(),
		); err != nil {
			return handed, fmt.Errorf("keep debt on %s: %w", choreID, err)
		}
	}

	for choreID, next := range newOrder {
		if len(next) == 0 {
			// Nobody left in the household at all; it goes with the household.
			continue
		}
		if err := replaceRotation(tx, choreID, next); err != nil {
			return handed, fmt.Errorf("rotation of %s: %w", choreID, err)
		}
	}

	if _, err := tx.Exec(
		`UPDATE tasks SET assigned_to = NULL WHERE group_id = ? AND assigned_to = ? AND status != 'done'`,
		groupID, userID,
	); err != nil {
		return handed, fmt.Errorf("unassign tasks: %w", err)
	}
	return handed, nil
}

// joinRotations is the other half, for someone who has just joined groupID
// inside tx. It returns the names of the chores they are now in.
func joinRotations(tx *sql.Tx, groupID, userID string) ([]string, error) {
	var addNew bool
	if err := tx.QueryRow(`SELECT add_new_members FROM groups WHERE id = ?`, groupID).Scan(&addNew); err != nil {
		return nil, err
	}

	joined := []string{}
	if addNew {
		members, err := membersInOrder(tx, groupID)
		if err != nil {
			return nil, err
		}
		others := without(members, userID)
		rotations, err := groupRotations(tx, groupID)
		if err != nil {
			return nil, err
		}
		names, err := repeatingChoreNames(tx, groupID)
		if err != nil {
			return nil, err
		}
		for choreID, rotation := range rotations {
			name, repeating := names[choreID]
			if !repeating || contains(rotation, userID) || !sameSet(rotation, others) {
				continue
			}
			if err := replaceRotation(tx, choreID, append(append([]string(nil), rotation...), userID)); err != nil {
				return nil, err
			}
			joined = append(joined, name)
		}
	}
	if err := restoreLeftDebts(tx, groupID, userID); err != nil {
		return nil, err
	}
	return joined, nil
}

// restoreLeftDebts gives userID back what they owed when they left, on every
// chore they are in the rotation of now. A debt on a chore they are not in
// stays written down, and comes back if they are added to it later.
func restoreLeftDebts(tx *sql.Tx, groupID, userID string) error {
	rows, err := tx.Query(
		`SELECT d.chore_id FROM left_debts d
		 JOIN chore_rotation r ON r.chore_id = d.chore_id AND r.user_id = d.user_id
		 WHERE d.group_id = ? AND d.user_id = ?`, groupID, userID,
	)
	if err != nil {
		return err
	}
	var chores []string
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			rows.Close()
			return err
		}
		chores = append(chores, id)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return err
	}

	for _, choreID := range chores {
		var id, assignee string
		var debts sql.NullString
		err := tx.QueryRow(
			`SELECT id, assigned_to, pending_debts FROM occurrences
			 WHERE chore_id = ? AND status = 'open' ORDER BY created_at DESC LIMIT 1`, choreID,
		).Scan(&id, &assignee, &debts)
		if err == sql.ErrNoRows {
			continue
		}
		if err != nil {
			return err
		}
		// Already holding it pays the debt by doing it, the same as anyone.
		if assignee != userID {
			if _, err := tx.Exec(`UPDATE occurrences SET pending_debts = ? WHERE id = ?`,
				encodeIDs(appendOnce(decodeIDs(debts), userID)), id); err != nil {
				return err
			}
		}
		if _, err := tx.Exec(
			`DELETE FROM left_debts WHERE group_id = ? AND user_id = ? AND chore_id = ?`,
			groupID, userID, choreID,
		); err != nil {
			return err
		}
	}
	return nil
}

// SettleDeparted applies the leaving rule to anyone who left before it
// existed and is still in a rotation or holding a turn. Safe to run on every
// start: once it has run there is nobody left for it to find.
func SettleDeparted(db *database.DB) error {
	rows, err := db.Query(`
		SELECT DISTINCT c.group_id, r.user_id FROM chore_rotation r
		JOIN chores c ON c.id = r.chore_id
		WHERE NOT EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = c.group_id AND m.user_id = r.user_id)
		UNION
		SELECT DISTINCT o.group_id, o.assigned_to FROM occurrences o
		WHERE o.status = 'open'
		  AND NOT EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = o.group_id AND m.user_id = o.assigned_to)
		UNION
		SELECT DISTINCT t.group_id, t.assigned_to FROM tasks t
		WHERE t.status != 'done' AND t.assigned_to IS NOT NULL
		  AND NOT EXISTS (SELECT 1 FROM group_members m WHERE m.group_id = t.group_id AND m.user_id = t.assigned_to)`)
	if err != nil {
		return err
	}
	type pair struct{ group, user string }
	var gone []pair
	for rows.Next() {
		var p pair
		if err := rows.Scan(&p.group, &p.user); err != nil {
			rows.Close()
			return err
		}
		gone = append(gone, p)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return err
	}

	for _, p := range gone {
		tx, err := db.Begin()
		if err != nil {
			return err
		}
		n, err := departRotations(tx, p.group, p.user, time.Now())
		if err != nil {
			tx.Rollback()
			return fmt.Errorf("settle %s in %s: %w", p.user, p.group, err)
		}
		if err := tx.Commit(); err != nil {
			return err
		}
		log.Printf("settled someone who had left household %s: out of its turns, %d open turn(s) handed on", p.group, n)
	}
	return nil
}

// ── Reading, inside the transaction ─────────────────────────────────────────

func groupRotations(q queryer, groupID string) (map[string][]string, error) {
	rows, err := q.Query(
		`SELECT r.chore_id, r.user_id FROM chore_rotation r JOIN chores c ON c.id = r.chore_id
		 WHERE c.group_id = ? ORDER BY r.chore_id, r.position`, groupID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string][]string{}
	for rows.Next() {
		var choreID, userID string
		if err := rows.Scan(&choreID, &userID); err != nil {
			return nil, err
		}
		out[choreID] = append(out[choreID], userID)
	}
	return out, rows.Err()
}

func repeatingChoreNames(q queryer, groupID string) (map[string]string, error) {
	rows, err := q.Query(
		`SELECT id, name FROM chores WHERE group_id = ? AND schedule_type != 'one_off'`, groupID,
	)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]string{}
	for rows.Next() {
		var id, name string
		if err := rows.Scan(&id, &name); err != nil {
			return nil, err
		}
		out[id] = name
	}
	return out, rows.Err()
}

func openTurns(q queryer, groupID string) ([]openTurn, error) {
	rows, err := q.Query(`
		SELECT o.id, o.chore_id, o.assigned_to, c.needed_by_time, o.due_date,
		       o.passed_chain, o.pending_debts, o.skip_credits, o.passed_from, o.resume_after
		FROM occurrences o JOIN chores c ON c.id = o.chore_id
		WHERE o.group_id = ? AND o.status = 'open'`, groupID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []openTurn
	for rows.Next() {
		var t openTurn
		var neededBy, chain, debts, credits sql.NullString
		var due sql.NullTime
		if err := rows.Scan(&t.id, &t.choreID, &t.assignedTo, &neededBy, &due,
			&chain, &debts, &credits, &t.passedFrom, &t.resumeAfter); err != nil {
			return nil, err
		}
		if neededBy.Valid {
			t.neededBy = &neededBy.String
		}
		if due.Valid {
			t.dueDate = &due.Time
		}
		t.chain, t.debts, t.credits = decodeIDs(chain), decodeIDs(debts), decodeIDs(credits)
		// A row from before passed_chain existed keeps its passer here.
		if len(t.chain) == 0 && t.passedFrom.Valid {
			t.chain = []string{t.passedFrom.String}
		}
		out = append(out, t)
	}
	return out, rows.Err()
}

func membersInOrder(q queryer, groupID string) ([]string, error) {
	// rowid breaks ties: joined_at has one-second resolution.
	rows, err := q.Query(`SELECT user_id FROM group_members WHERE group_id = ? ORDER BY joined_at, rowid`, groupID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var out []string
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		out = append(out, id)
	}
	return out, rows.Err()
}

// awayIn is awayMembers, readable inside a transaction.
func awayIn(q queryer, groupID string, now time.Time) (map[string]bool, error) {
	rows, err := q.Query(`
		SELECT DISTINCT user_id FROM away_periods
		WHERE group_id = ? AND ended_at IS NULL AND (ends_at IS NULL OR ends_at > ?)`,
		groupID, now.UTC())
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	away := map[string]bool{}
	for rows.Next() {
		var id string
		if err := rows.Scan(&id); err != nil {
			return nil, err
		}
		away[id] = true
	}
	return away, rows.Err()
}

// ── Small list helpers ──────────────────────────────────────────────────────

// nextAfter picks who takes a turn from someone leaving: the first person
// after them in the old order who is still on the chore and at home. If they
// were not in the order, the top of what is left. If everyone is away, the
// first in line anyway, since a turn always has a name on it.
func nextAfter(old, left []string, leaving string, away map[string]bool) string {
	start := 0
	for i, id := range old {
		if id == leaving {
			start = i + 1
			break
		}
	}
	var inLine []string
	for step := 0; step < len(old); step++ {
		id := old[(start+step)%len(old)]
		if contains(left, id) {
			inLine = append(inLine, id)
		}
	}
	for _, id := range left {
		inLine = appendOnce(inLine, id)
	}
	for _, id := range inLine {
		if !away[id] {
			return id
		}
	}
	return inLine[0]
}

func without(ids []string, id string) []string {
	out := []string{}
	for _, existing := range ids {
		if existing != id {
			out = append(out, existing)
		}
	}
	return out
}

func sameSet(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for _, id := range b {
		if !contains(a, id) {
			return false
		}
	}
	return true
}
