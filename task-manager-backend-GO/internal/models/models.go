package models

import (
	"encoding/json"
	"fmt"
	"time"
)

type User struct {
	ID           string    `json:"id"`
	Username     string    `json:"username"`
	Email        string    `json:"email"`
	PasswordHash string    `json:"-"`
	CreatedAt    time.Time `json:"created_at"`
	// Quiet hours, "HH:MM" (F3). A reminder that would land inside the window
	// waits for QuietTo. The window normally wraps midnight.
	QuietFrom string `json:"quiet_from"`
	QuietTo   string `json:"quiet_to"`
}

// Default quiet hours: the 11pm finding — a reminder you cannot act on is a
// reminder you will forget.
const (
	DefaultQuietFrom = "21:00"
	DefaultQuietTo   = "09:00"
)

// UpdateMeRequest changes the caller's own settings: the quiet-hours window,
// and the two pieces of identity a person can correct themselves.
//
// Every field is optional, so one can be set without restating the others, and
// a patch naming none of them is refused rather than silently doing nothing.
//
// Username and email are here rather than on a separate profile endpoint
// because they are the same kind of thing as quiet hours — settings that
// belong to a person rather than to a household. There is deliberately no
// password field: changing a password needs the old one, which is a different
// conversation with a different failure mode.
type UpdateMeRequest struct {
	Username  *string `json:"username,omitempty"`
	Email     *string `json:"email,omitempty"`
	QuietFrom *string `json:"quiet_from,omitempty"`
	QuietTo   *string `json:"quiet_to,omitempty"`
}

// MaxUsernameLength bounds the name housemates see. Nothing in the data model
// needs a limit; the member rows and rotation pickers that render it do.
const MaxUsernameLength = 32

// SetAwayRequest declares the caller away from one household, or back (F5).
//
// Until is optional: an away period with no end is open-ended, which is the
// honest default for "I don't know when I'm back". It is ignored when Away is
// false.
type SetAwayRequest struct {
	Away  bool    `json:"away"`
	Until *string `json:"until,omitempty"` // RFC3339
}

type Group struct {
	ID          string    `json:"id"`
	Name        string    `json:"name"`
	Description string    `json:"description"`
	CreatedBy   string    `json:"created_by"`
	CreatedAt   time.Time `json:"created_at"`
}

type GroupMember struct {
	ID       string    `json:"id"`
	GroupID  string    `json:"group_id"`
	UserID   string    `json:"user_id"`
	Role     string    `json:"role"` // "owner", "admin", "member"
	JoinedAt time.Time `json:"joined_at"`
}

type Invite struct {
	ID          string     `json:"id"`
	GroupID     string     `json:"group_id"`
	InvitedBy   string     `json:"invited_by"`
	InvitedUser *string    `json:"invited_user,omitempty"` // nil for code-based invites
	Status      string     `json:"status"`                 // "pending", "accepted", "declined", "expired", "active"
	InviteCode  string     `json:"invite_code,omitempty"`
	MaxUses     int        `json:"max_uses"`
	UseCount    int        `json:"use_count"`
	CreatedAt   time.Time  `json:"created_at"`
	ExpiresAt   *time.Time `json:"expires_at,omitempty"`
}

type Task struct {
	ID          string     `json:"id"`
	GroupID     string     `json:"group_id"`
	AssignedTo  *string    `json:"assigned_to,omitempty"`
	Title       string     `json:"title"`
	Description string     `json:"description"`
	Status      string     `json:"status"` // "todo", "in_progress", "done"
	DueDate     *time.Time `json:"due_date,omitempty"`
	CreatedAt   time.Time  `json:"created_at"`
	// UpdatedAt is a pointer because the column was added after the fact:
	// rows written before that migration have NULL until their next update.
	UpdatedAt *time.Time `json:"updated_at,omitempty"`
	// DoneBy is who marked it done, which is not necessarily who it was
	// assigned to — anyone may complete anything, and the board records the
	// reality rather than the intention. Both names are kept.
	DoneBy *string    `json:"done_by,omitempty"`
	DoneAt *time.Time `json:"done_at,omitempty"`
}

// Schedule types. A chore picks exactly one at creation and cannot change
// category afterwards — only its parameters.
const (
	// ScheduleInterval: every N days, counted from the last *completion*, so a
	// chore done late shifts the whole schedule with reality.
	ScheduleInterval = "interval"
	// ScheduleFixedDate: specific weekdays or month days, because the world sets
	// the deadline (bin collection). A missed date rolls to the next one with
	// the same assignee.
	ScheduleFixedDate = "fixed_date"
	// ScheduleAsNeeded: rotates with no date at all. One standing occurrence
	// always exists; completing it advances the turn. No due date, so no due
	// reminder — and deliberately no "it's needed now" button, which would be a
	// nag with a disguise on.
	ScheduleAsNeeded = "as_needed"
	// ScheduleOneOff: no recurrence. A bill, a repair, a delivery.
	ScheduleOneOff = "one_off"
)

// OccurrenceStatus values. The whole set — there is no "missed" state anywhere
// in the system, by design: a chore can only be not yet done.
const (
	OccurrenceOpen = "open"
	OccurrenceDone = "done"
)

// DoneLineMaxLen caps F4's "what done means" line. The limit is deliberate:
// this is a treaty between housemates, not a manual.
const DoneLineMaxLen = 140

// Chore is a definition — what the chore is, how often it comes round, and the
// turn order it follows. It is never itself on the board; its occurrences are.
type Chore struct {
	ID       string `json:"id"`
	GroupID  string `json:"group_id"`
	Name     string `json:"name"`
	DoneLine string `json:"done_line"`

	ScheduleType string `json:"schedule_type"`
	// IntervalDays is set only for ScheduleInterval.
	IntervalDays *int `json:"interval_days,omitempty"`
	// FixedWeekdays (0=Sunday) and FixedMonthDays (1..31) are set only for
	// ScheduleFixedDate, and exactly one of the two is non-empty.
	FixedWeekdays  []int `json:"fixed_weekdays,omitempty"`
	FixedMonthDays []int `json:"fixed_month_days,omitempty"`
	// NeededByTime is an optional "HH:MM" clock time.
	NeededByTime *string `json:"needed_by_time,omitempty"`

	// Rotation is the ordered turn list, by user id, position 0 first.
	Rotation []string `json:"rotation"`

	CreatedBy string     `json:"created_by"`
	CreatedAt time.Time  `json:"created_at"`
	UpdatedAt *time.Time `json:"updated_at,omitempty"`
}

// Occurrence is one cycle of a chore, assigned to exactly one member.
//
// AssignedTo is not a pointer: every occurrence always has exactly one name on
// it. DoneBy may differ from AssignedTo — anyone may complete anything, and
// recording who actually did it is what makes covering visible without anyone
// having to say so.
type Occurrence struct {
	ID         string     `json:"id"`
	ChoreID    string     `json:"chore_id"`
	GroupID    string     `json:"group_id"`
	AssignedTo string     `json:"assigned_to"`
	Status     string     `json:"status"`
	DueDate    *time.Time `json:"due_date,omitempty"`
	DoneBy     *string    `json:"done_by,omitempty"`
	DoneAt     *time.Time `json:"done_at,omitempty"`
	CreatedAt  time.Time  `json:"created_at"`
	// SpawnedFrom is the occurrence whose completion created this one. Undoing
	// that completion removes this occurrence again.
	SpawnedFrom *string `json:"spawned_from,omitempty"`
	// ResumeAfter is legacy and no longer written. Rows from before
	// SkipCredits hold the coverer here, meaning "the rotation resumes after
	// them"; scanOccurrence reads it as a credit for that coverer instead,
	// which is the rule that replaced it (BACKLOG B-19).
	ResumeAfter *string `json:"-"`
	// PassedFrom is whoever passed this occurrence away (F5). The debt stays
	// with them through a chain of passes: passing on is declining a favour,
	// not a duty, so it does not move the turn onto the person who declined.
	PassedFrom *string `json:"passed_from,omitempty"`
	// PassedAt is when it last changed hands, which is when it became the
	// current holder's turn.
	PassedAt *time.Time `json:"passed_at,omitempty"`

	// CoveredBy is who did the previous cycle when this one came back under the
	// debt rule, and CoveredByName is their display name, joined in.
	//
	// Null on a normal advance. It is the difference between "your turn" and
	// "your turn again, because Maya did the last one for you" — the second of
	// which the board should say rather than leave to be worked out.
	// PassedChain and PendingDebts are the turn rule's bookkeeping, and are
	// deliberately **not** on the wire.
	//
	// Constraint 7 keeps a pass between the two people it concerns. Sending
	// every member a list of who has passed what, or of who owes turns, would
	// publish exactly what that constraint protects — and a client that could
	// read it could count it, which Part 0 forbids. The board already shows
	// the only part anyone else needs: whose name is on the row.
	PassedChain  []string `json:"-"`
	PendingDebts []string `json:"-"`
	// SkipCredits is everyone who covered a turn they did not owe and has
	// not yet had their own next slot skipped for it — the other half of a
	// debt (see nextTurn). Not on the wire either, for the same reason.
	SkipCredits []string `json:"-"`

	// PassTo is who a busy pass would hand this to, sent only on the
	// holder's own open rows so the confirm can name them. Computed by the
	// server because it depends on the credits, which the client is not sent.
	PassTo *string `json:"pass_to,omitempty"`

	// NeedsDate is set when everybody who could take this chore has passed it,
	// so it has come back to whoever asked first and they are the one to say
	// when it will happen.
	//
	// Sent **only on the holder's own rows**, and computed per request rather
	// than stored, because it is a fact about the chain and the chain can
	// change. Everyone else sees an ordinary standing chore: that a whole
	// household declined it is between them and the person now holding it.
	NeedsDate bool `json:"needs_date,omitempty"`

	CoveredBy     *string `json:"covered_by,omitempty"`
	CoveredByName *string `json:"covered_by_name,omitempty"`

	// DoneByName is the doer's display name, joined in. A screen that shows
	// your turns across households (My chores) has no member list to look the
	// doer up in, and printed "Done by someone" for a turn a flatmate covered.
	DoneByName *string `json:"done_by_name,omitempty"`

	// ChoreName and DoneLine are joined in so the board can render a row
	// without a second round trip per occurrence.
	ChoreName string `json:"chore_name"`
	DoneLine  string `json:"done_line"`

	// AlsoBy is everyone besides the doer who said they worked on this cycle
	// too, oldest first. Only ever set on a done occurrence.
	AlsoBy []AlsoBy `json:"also_by,omitempty"`
}

// AlsoBy is one "I worked on this too" on a completed cycle.
//
// A fact, not a rating: it says who put work in, and nothing about whether the
// doer's part was good enough. There is no note field, on purpose — a free-text
// box beside somebody else's completion is a comment thread (constraint 1).
type AlsoBy struct {
	UserID   string    `json:"user_id"`
	Username string    `json:"username"`
	AddedAt  time.Time `json:"added_at"`
}

// ── History (F6) ────────────────────────────────────────────────────────────

// ChoreHistoryEntry is one completed cycle of a chore.
//
// Note what is absent: there is no "late" field. The due date and the
// completion time are both here, and whether one followed the other is
// arithmetic the reader can do. A boolean would be the app reaching a verdict,
// which is what the spec means by "visible only as date arithmetic, never as a
// flag".
type ChoreHistoryEntry struct {
	OccurrenceID string `json:"occurrence_id"`
	AssignedTo   string `json:"assigned_to"`
	AssigneeName string `json:"assignee_name"`
	// DoneBy may differ from AssignedTo — that is a cover, and the record
	// credits whoever actually did it.
	DoneBy     string  `json:"done_by"`
	DoneByName string  `json:"done_by_name"`
	PassedFrom *string `json:"passed_from,omitempty"`

	DueDate *time.Time `json:"due_date,omitempty"`
	DoneAt  time.Time  `json:"done_at"`

	// AlsoBy is everyone else who worked on this cycle — "done by Maya, also
	// Sara". It does not change who the cycle is credited to.
	AlsoBy []AlsoBy `json:"also_by,omitempty"`
}

// Absence is one away period, for showing beside a timeline or explaining a
// quiet stretch in someone's count.
type Absence struct {
	UserID    string     `json:"user_id"`
	Username  string     `json:"username"`
	StartedAt time.Time  `json:"started_at"`
	EndsAt    *time.Time `json:"ends_at,omitempty"`
	EndedAt   *time.Time `json:"ended_at,omitempty"`
}

type ChoreHistory struct {
	ChoreID  string              `json:"chore_id"`
	Entries  []ChoreHistoryEntry `json:"entries"`
	Absences []Absence           `json:"absences"`
}

// PersonHistory is one member's completions in a window.
//
// A count and a number of days away, and nothing else — no percentage, no
// share, no rank. AwayDays is here so a low count has its explanation next to
// it rather than being left to look like flaking.
type PersonHistory struct {
	UserID    string `json:"user_id"`
	Username  string `json:"username"`
	Completed int    `json:"completed"`
	AwayDays  int    `json:"away_days"`
}

// GroupHistory is the per-person view.
//
// People are returned in the order they joined, deliberately. Sorting by
// completions would be a leaderboard whatever it was called, and constraint 4
// rules those out.
type GroupHistory struct {
	Window string          `json:"window"`
	From   time.Time       `json:"from"`
	To     time.Time       `json:"to"`
	People []PersonHistory `json:"people"`
}

// ActivityEvent is one entry in a group's audit trail. It is what the Android
// client polls for to keep members aware of each other's changes.
type ActivityEvent struct {
	ID string `json:"id"`
	// ActorUsername is joined in from users so clients don't need a second
	// round trip to render "Sam moved 3 tasks to done".
	ActorUsername string    `json:"actor_username"`
	GroupID       string    `json:"group_id"`
	ActorID       string    `json:"actor_id"`
	EventType     string    `json:"event_type"`
	TaskID        *string   `json:"task_id,omitempty"`
	Detail        string    `json:"detail"`
	CreatedAt     time.Time `json:"created_at"`
}

// --- Request / Response DTOs ---

type RegisterRequest struct {
	Username string `json:"username"`
	Email    string `json:"email"`
	Password string `json:"password"`
}

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type AuthResponse struct {
	Token string `json:"token"`
	User  User   `json:"user"`
}

type CreateGroupRequest struct {
	Name        string `json:"name"`
	Description string `json:"description"`
}

type InviteByUsernameRequest struct {
	Username string `json:"username"`
}

type RedeemInviteRequest struct {
	Code string `json:"code"`
}

type CreateTaskRequest struct {
	Title       string  `json:"title"`
	Description string  `json:"description"`
	AssignedTo  *string `json:"assigned_to,omitempty"`
	DueDate     *string `json:"due_date,omitempty"` // RFC3339
}

// NullableField distinguishes three JSON states:
//   - field omitted  → Present=false
//   - field: null    → Present=true, Value=""  (clear the value)
//   - field: "abc"   → Present=true, Value="abc"
type NullableField struct {
	Value   string
	Present bool // true if the key appeared in the JSON at all
	IsNull  bool // true if the value was explicitly null
}

func (n *NullableField) UnmarshalJSON(data []byte) error {
	n.Present = true
	if string(data) == "null" {
		n.IsNull = true
		n.Value = ""
		return nil
	}
	// Delegate to encoding/json for proper unescaping of \", \n, \uXXXX, etc.
	// This also rejects non-string JSON types (numbers, booleans, objects)
	// which would panic with the old manual quote-stripping approach.
	var s string
	if err := json.Unmarshal(data, &s); err != nil {
		return fmt.Errorf("NullableField must be a string or null, got: %s", string(data))
	}
	n.Value = s
	return nil
}

type UpdateTaskRequest struct {
	Title       *string       `json:"title,omitempty"`
	Description *string       `json:"description,omitempty"`
	Status      *string       `json:"status,omitempty"`
	AssignedTo  NullableField `json:"assigned_to"`
	DueDate     NullableField `json:"due_date"`
}

// CreateChoreRequest defines a chore and, implicitly, its first occurrence.
//
// Rotation must contain at least one member: an occurrence always has exactly
// one name on it, so a chore with nobody in its rotation could never spawn one.
type CreateChoreRequest struct {
	Name           string   `json:"name"`
	DoneLine       string   `json:"done_line"`
	ScheduleType   string   `json:"schedule_type"`
	IntervalDays   *int     `json:"interval_days,omitempty"`
	FixedWeekdays  []int    `json:"fixed_weekdays,omitempty"`
	FixedMonthDays []int    `json:"fixed_month_days,omitempty"`
	NeededByTime   *string  `json:"needed_by_time,omitempty"`
	Rotation       []string `json:"rotation"`
	// DueDate applies to one-off chores only, which are assigned at creation by
	// the creator and have no recurrence to derive a date from. RFC3339.
	DueDate *string `json:"due_date,omitempty"`
}

// UpdateChoreRequest edits a chore. Open to any member by design — the spec
// replaces an approval flow with a diff broadcast to the whole group.
//
// ScheduleType is absent on purpose: a chore cannot change category, only its
// parameters. Changing category would make the existing open occurrence's due
// date meaningless, and re-deriving it silently is worse than requiring a new
// chore.
type UpdateChoreRequest struct {
	Name *string `json:"name,omitempty"`
	// ScheduleType moves a chore between interval, fixed_date and as_needed.
	//
	// It was immutable until v2's edit flow, on the reasoning that a chore
	// which has changed kind is a new chore. Households disagreed: "the bins
	// moved to Thursdays" and "let's just do the bathroom when it looks bad"
	// are edits to an existing arrangement, not new arrangements, and the
	// history belongs to the same chore either way.
	//
	// one_off is not reachable from here in either direction. A one-off lives
	// in the tasks table, so the switch is a different row, not a different
	// column.
	ScheduleType   *string       `json:"schedule_type,omitempty"`
	DoneLine       *string       `json:"done_line,omitempty"`
	IntervalDays   *int          `json:"interval_days,omitempty"`
	FixedWeekdays  []int         `json:"fixed_weekdays,omitempty"`
	FixedMonthDays []int         `json:"fixed_month_days,omitempty"`
	NeededByTime   NullableField `json:"needed_by_time"`
	Rotation       []string      `json:"rotation,omitempty"`
}

// UpdateOccurrenceRequest is the board's one-tap completion, and its undo.
//
// Status is the only field: an occurrence's assignee is decided by rotation,
// never by hand, and its due date by the schedule. Reassignment happens through
// the busy pass (F5), not through this endpoint.
type UpdateOccurrenceRequest struct {
	Status string `json:"status"`
}

type GroupWithProgress struct {
	Group
	TotalTasks int     `json:"total_tasks"`
	DoneTasks  int     `json:"done_tasks"`
	Progress   float64 `json:"progress"` // 0.0 to 1.0
	// MyRole is the requesting user's role in this group: "owner", "admin" or
	// "member". Returned so a client can gate its UI without having to fetch
	// the member list and work out which row is itself.
	MyRole string `json:"my_role"`
}
