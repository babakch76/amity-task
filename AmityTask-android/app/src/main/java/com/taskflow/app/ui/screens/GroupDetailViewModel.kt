package com.taskflow.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.taskflow.app.data.model.ActivityEvent
import com.taskflow.app.data.model.AwayResponse
import com.taskflow.app.data.model.Booking
import com.taskflow.app.data.model.CreateBookingRequest
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.ChoreHistory
import com.taskflow.app.data.model.CreateChoreRequest
import com.taskflow.app.data.model.GroupHistory
import com.taskflow.app.data.model.CreateTaskRequest
import com.taskflow.app.data.model.GroupWithProgress
import com.taskflow.app.data.model.HandOverRequest
import com.taskflow.app.data.model.RenameGroupRequest
import com.taskflow.app.data.model.InviteByUsernameRequest
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.Patchable
import com.taskflow.app.data.model.SetAwayRequest
import com.taskflow.app.data.model.Task
import com.taskflow.app.data.model.UpdateChoreRequest
import com.taskflow.app.data.model.UpdateOccurrenceRequest
import com.taskflow.app.data.model.UpdateTaskRequest
import com.taskflow.app.data.model.isOpen
import com.taskflow.app.data.remote.ApiErrors
import com.taskflow.app.data.remote.RetrofitClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Membership roles, matching the backend's `group_members.role`.
 *
 * [ROLE_ADMIN] is no longer assignable. The manager role and the deadline
 * permission it carried were removed when the chore spec made editing
 * explicitly open to every member; the constant remains only so that groups
 * created before that still render their existing rows correctly.
 */
const val ROLE_OWNER = "owner"
const val ROLE_ADMIN = "admin"
const val ROLE_MEMBER = "member"

/** Everything the Group Detail screen renders. */
data class GroupDetailUiState(
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val group: GroupWithProgress? = null,
    val tasks: List<Task> = emptyList(),
    /**
     * The chore model's half of the board (F2). Occurrences and tasks are shown
     * together: tasks predate the chore model and are the spec's "one-off"
     * shape, so they stay on the board rather than being hidden or migrated.
     */
    val occurrences: List<Occurrence> = emptyList(),
    /** Chore definitions, for rendering a schedule and for editing. */
    val chores: List<Chore> = emptyList(),
    val members: List<MemberInfo> = emptyList(),
    /** What people have booked, soonest first. Shown above the chores on the board. */
    val bookings: List<Booking> = emptyList(),
    /** The signed-in user's quiet-hours window (F3), for the settings dialog. */
    val quietFrom: String = "21:00",
    val quietTo: String = "09:00",
    /**
     * History (F6), fetched only when a history view is opened.
     *
     * Not part of the board load: it is read rarely, it is the feature the
     * spec ranked last, and pulling it on every refresh would cost every user
     * for the few who look.
     */
    val choreHistory: ChoreHistory? = null,
    val groupHistory: GroupHistory? = null,
    val historyWindow: String = "month",
    val historyLoading: Boolean = false,
    val activity: List<ActivityEvent> = emptyList(),
    /** Fatal error for the whole screen — shows the retry state. */
    val error: String? = null,
    /** Transient message for a single failed/successful action. */
    val message: String? = null,
    /**
     * The occurrence a just-shown message can still undo, if any.
     *
     * Only ever set alongside [message], and only for a pass. It is what turns
     * the snackbar into an offer rather than a notification: the server keeps
     * its own window, so this is the offer and not the permission.
     */
    val undoablePass: String? = null,
    /**
     * The occurrence a just-shown "done" message can still undo — the same
     * offer as [undoablePass], for a completion. The server's ten-minute
     * window is the permission; this is only the button.
     */
    val undoableDone: String? = null,
    /** Set after generating a shareable invite code, so the UI can display it. */
    val inviteCode: String? = null,
    /** Flipped once the user has left the group, so the screen can navigate away. */
    val hasLeft: Boolean = false,
    /** Set with [hasLeft] when the household was removed rather than left. */
    val householdRemoved: Boolean = false,
)

/**
 * ViewModel for one group's detail screen.
 *
 * Owns every call in the group-scoped half of [com.taskflow.app.data.remote.GroupApiService]:
 * tasks CRUD, the bulk status update behind multi-select, members and invites,
 * leaving, and the activity feed that drives the awareness/feedback loop.
 *
 * Takes [groupId] as a constructor argument, hence [Factory] — Compose's
 * `viewModel()` cannot supply it otherwise, and there is no DI framework here.
 */
class GroupDetailViewModel(private val groupId: String) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupDetailUiState())
    val uiState: StateFlow<GroupDetailUiState> = _uiState.asStateFlow()

    private val api get() = RetrofitClient.getInstance().groupApi

    /**
     * `created_at` of the newest activity event already held, sent back as
     * `?since=` so each poll transfers only what's new. Null until the first
     * load, which fetches the whole (capped) feed.
     */
    private var activityCursor: String? = null

    init {
        refresh()
    }

    // ─── Loading ────────────────────────────────────────────────

    /** Full reload of every section. Used on first open and on pull-to-retry. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // Six independent endpoints — fire them together rather than
            // serially, so opening the screen costs one round trip, not six.
            val groupDeferred = async { runCatching { api.getGroup(groupId) } }
            val tasksDeferred = async { runCatching { api.listTasks(groupId) } }
            val occurrencesDeferred = async { runCatching { api.listOccurrences(groupId) } }
            val choresDeferred = async { runCatching { api.listChores(groupId) } }
            val membersDeferred = async { runCatching { api.listMembers(groupId) } }
            val activityDeferred = async { runCatching { api.listActivity(groupId) } }
            val bookingsDeferred = async { runCatching { api.listBookings(groupId) } }
            // Quiet hours, for the on-device reminder scheduler (F3).
            val meDeferred = async { runCatching { api.getMe() } }

            val groupRes = groupDeferred.await()
            val tasksRes = tasksDeferred.await()
            val occurrencesRes = occurrencesDeferred.await()
            val choresRes = choresDeferred.await()
            val membersRes = membersDeferred.await()
            val activityRes = activityDeferred.await()
            val bookingsRes = bookingsDeferred.await()
            val meRes = meDeferred.await()

            // The group call is the one that must succeed — without it there is
            // no screen to show. A 404 here also means the membership guard
            // rejected us (the backend returns 404, never 403, to non-members).
            val group = groupRes.getOrNull()
            if (group == null || !group.isSuccessful || group.body() == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = groupRes.exceptionOrNull()?.let { networkMessage(it) }
                        ?: group?.let { ApiErrors.messageFor(it) }
                        ?: "Couldn't load this household",
                )
                return@launch
            }

            val me = meRes.getOrNull()?.takeIf { it.isSuccessful }?.body()
            val activity = activityRes.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty()
            activityCursor = activity.firstOrNull()?.createdAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = null,
                group = group.body(),
                tasks = tasksRes.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty(),
                occurrences = occurrencesRes.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty(),
                chores = choresRes.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty(),
                members = membersRes.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty(),
                activity = activity,
                bookings = bookingsRes.getOrNull()?.takeIf { it.isSuccessful }?.body().orEmpty(),
                // Falls back to the current state, which starts at the spec's
                // default — a failed /me must not silently widen the window.
                quietFrom = me?.quietFrom ?: _uiState.value.quietFrom,
                quietTo = me?.quietTo ?: _uiState.value.quietTo,
            )
        }
    }

    /**
     * Incremental activity poll — the CSCW feedback loop.
     *
     * Sends the newest timestamp already held as `?since=`, so a teammate's
     * change shows up here within one poll interval without re-fetching the
     * whole feed. Failures are swallowed: a dropped poll is not worth an error
     * banner, and the next tick retries.
     *
     * The turns are read on every tick as well, because not every change makes
     * a feed entry. A turn passed to you writes none, by design, and neither do
     * turns moved round someone who went away. Waiting for the feed left a pass
     * sitting on the receiver's board as somebody else's until they refreshed.
     */
    fun pollActivity() {
        viewModelScope.launch {
            val activityRes = async { runCatching { api.listActivity(groupId, activityCursor) }.getOrNull() }
            val occurrencesRes = async { runCatching { api.listOccurrences(groupId) }.getOrNull() }
            // Going away or coming back writes nothing to the feed and moves no
            // turn when nobody can take it, so who is away is checked as well:
            // the away banners and the paused household follow it.
            val membersRes = async { runCatching { api.listMembers(groupId) }.getOrNull() }
            val fresh = activityRes.await()?.takeIf { it.isSuccessful }?.body().orEmpty()
            val occurrences = occurrencesRes.await()?.takeIf { it.isSuccessful }?.body()
            val members = membersRes.await()?.takeIf { it.isSuccessful }?.body()

            if (fresh.isNotEmpty()) {
                activityCursor = fresh.first().createdAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    ?: activityCursor
                _uiState.value = _uiState.value.copy(activity = fresh + _uiState.value.activity)
            }

            // Someone changed something — the board is now stale, so pull it
            // again. Compared as sets, so rows sent in another order are not news.
            val turnsMoved = occurrences != null && occurrences.toSet() != _uiState.value.occurrences.toSet()
            val peopleMoved = members != null && members.toSet() != _uiState.value.members.toSet()
            // The turns and the people were fetched just now, so they are passed
            // on rather than asked for a second time.
            if (fresh.isNotEmpty() || turnsMoved || peopleMoved) refreshTasksAndProgress(occurrences, members)
        }
    }

    /**
     * Reloads the board and everything around it.
     *
     * All at once rather than one after another: none depends on another, and
     * one by one it was six round trips, about a second and a half on the live
     * server, after every tap. Anything the caller already has ([knownTurns],
     * [knownMembers]) is used instead of being fetched again.
     *
     * One state update at the end, not six: each update redraws the board and,
     * for turns and chores, re-arms the reminders.
     */
    private suspend fun refreshTasksAndProgress(
        knownTurns: List<Occurrence>? = null,
        knownMembers: List<MemberInfo>? = null,
    ) = coroutineScope {
        fun <T> fetch(call: suspend () -> retrofit2.Response<T>) =
            async { runCatching { call() }.getOrNull()?.takeIf { it.isSuccessful }?.body() }

        val tasks = fetch { api.listTasks(groupId) }
        // The board is both halves, so a poll that only refreshed tasks would
        // leave a housemate's completed chore sitting on the screen as open.
        val turns = if (knownTurns == null) fetch { api.listOccurrences(groupId) } else null
        val chores = fetch { api.listChores(groupId) }
        val bookings = fetch { api.listBookings(groupId) }
        val group = fetch { api.getGroup(groupId) }
        // Joins, leaves and going away change the people as well as the turns.
        val members = if (knownMembers == null) fetch { api.listMembers(groupId) } else null

        val newTasks = tasks.await()
        val newTurns = knownTurns ?: turns?.await()
        val newChores = chores.await()
        val newBookings = bookings.await()
        val newGroup = group.await()
        val newMembers = knownMembers ?: members?.await()

        // Read and written with no suspension in between, so nothing set
        // meanwhile (a message, the working flag) is lost. A failed fetch
        // keeps what was there.
        val now = _uiState.value
        _uiState.value = now.copy(
            tasks = newTasks ?: now.tasks,
            occurrences = newTurns ?: now.occurrences,
            chores = newChores ?: now.chores,
            bookings = newBookings ?: now.bookings,
            group = newGroup ?: now.group,
            members = newMembers ?: now.members,
        )
    }

    // ─── Bookings ───────────────────────────────────────────────

    fun createBooking(
        what: String,
        note: String,
        start: OffsetDateTime,
        end: OffsetDateTime,
        onResult: (String?) -> Unit,
    ) {
        runAction(successMessage = "${what.trim()} booked.", onResult = onResult) {
            api.createBooking(
                groupId,
                CreateBookingRequest(
                    what = what.trim(),
                    note = note.trim(),
                    startsAt = start.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    endsAt = end.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                ),
            )
        }
    }

    fun cancelBooking(bookingId: String) {
        runAction(successMessage = "Booking cancelled.") {
            api.cancelBooking(groupId, bookingId)
        }
    }

    // ─── Tasks ──────────────────────────────────────────────────

    /**
     * [onResult] lets the dialog stay open until the write lands: null on
     * success (close it), otherwise the message to show inside it.
     */
    fun createTask(
        title: String,
        description: String,
        assignedTo: String?,
        dueDate: String? = null,
        onResult: (String?) -> Unit = {},
    ) {
        runAction(successMessage = "${title.trim()} added.", onResult = onResult) {
            api.createTask(
                groupId,
                CreateTaskRequest(
                    title = title.trim(),
                    description = description.trim(),
                    assignedTo = assignedTo,
                    dueDate = dueDate,
                ),
            )
        }
    }

    /**
     * Edit a task's title and/or description.
     *
     * Pass null for a field that didn't change — it stays out of the JSON, so
     * this can't clobber a concurrent edit to the other field. If nothing
     * changed, no request is made at all: an empty patch would serialise to
     * `{}` and the backend rejects that with "no fields to update".
     */
    fun updateTaskText(taskId: String, title: String?, description: String?) {
        if (title == null && description == null) return
        runAction(successMessage = "Task updated") {
            api.updateTask(
                groupId,
                taskId,
                UpdateTaskRequest(title = title, description = description),
            )
        }
    }

    fun setTaskStatus(taskId: String, status: String) {
        // Only `status` is set; assignedTo/dueDate stay Patchable.Absent and are
        // therefore omitted from the JSON entirely, so this cannot disturb the
        // assignee or the deadline.
        runAction(successMessage = "Moved to ${statusLabelFor(status)}") {
            api.updateTask(groupId, taskId, UpdateTaskRequest(status = status))
        }
    }

    /**
     * Assign or unassign a task.
     *
     * [userId] null means unassign, which must reach the backend as an explicit
     * `"assigned_to": null` — [Patchable.SetNull] — because an omitted key means
     * "leave it alone". This is the distinction the whole Patchable type exists
     * for.
     */
    fun setTaskAssignee(taskId: String, userId: String?) {
        val assignee = if (userId == null) Patchable.SetNull else Patchable.Value(userId)
        val note = if (userId == null) "Assignee removed" else "Task assigned"
        runAction(successMessage = note) {
            api.updateTask(groupId, taskId, UpdateTaskRequest(assignedTo = assignee))
        }
    }

    fun deleteTask(taskId: String) {
        runAction(successMessage = "Task deleted") { api.deleteTask(groupId, taskId) }
    }

    /**
     * Set or clear a task's deadline.
     *
     * Owner/manager only — the backend answers 403 otherwise, and [ApiErrors]
     * surfaces its explanation. The UI hides the control for plain members, but
     * this is not relying on that: the server is the authority, and a stale
     * `my_role` after a demotion would otherwise let a request through.
     *
     * @param dueDateIso RFC 3339 instant, or null to clear. Null becomes
     *   [Patchable.SetNull] so the key is sent as an explicit `null` — an
     *   omitted key would mean "leave the deadline alone", which is the exact
     *   opposite of clearing it.
     */
    fun setTaskDueDate(taskId: String, dueDateIso: String?) {
        val due = if (dueDateIso == null) Patchable.SetNull else Patchable.Value(dueDateIso)
        val note = if (dueDateIso == null) "Deadline removed" else "Deadline set"
        runAction(successMessage = note) {
            api.updateTask(groupId, taskId, UpdateTaskRequest(dueDate = due))
        }
    }

    // ─── Invites & membership ───────────────────────────────────

    fun generateInviteCode() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null)
            try {
                val response = api.generateInviteCode(groupId)
                val body = response.body()
                _uiState.value = if (response.isSuccessful && body != null) {
                    _uiState.value.copy(isWorking = false, inviteCode = body.code)
                } else {
                    _uiState.value.copy(isWorking = false, message = ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isWorking = false, message = networkMessage(e))
            }
        }
    }

    fun inviteByUsername(username: String) {
        runAction(
            successMessage = "Invite sent to $username",
        ) { api.inviteByUsername(groupId, InviteByUsernameRequest(username.trim())) }
    }

    /**
     * Leave the group.
     *
     * Three outcomes from the backend, all worth surfacing differently: 200 for
     * a normal leave, 200 with a different message when the caller was the last
     * member (the group is deleted), and 409 when the owner tries to leave while
     * others remain — that one is not a failure to retry, it's a rule.
     *
     * @param handTo An owner leaving a household with people in it names who
     *   takes over. The server hands it over and lets them go in one step.
     */
    fun leaveGroup(handTo: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null)
            try {
                val response = api.leaveGroup(groupId, handTo)
                _uiState.value = if (response.isSuccessful) {
                    _uiState.value.copy(isWorking = false, hasLeft = true)
                } else {
                    _uiState.value.copy(isWorking = false, message = ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isWorking = false, message = networkMessage(e))
            }
        }
    }

    /**
     * Remove the household for everyone. Only the owner is offered this, and
     * the server checks again; a refusal is shown rather than swallowed.
     */
    fun removeHousehold() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null)
            try {
                val response = api.deleteGroup(groupId)
                _uiState.value = if (response.isSuccessful) {
                    _uiState.value.copy(isWorking = false, hasLeft = true, householdRemoved = true)
                } else {
                    _uiState.value.copy(isWorking = false, message = ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isWorking = false, message = networkMessage(e))
            }
        }
    }

    /**
     * Rename the household. Anyone in it can.
     *
     * [onResult] gets null once it went through, or what to say under the name
     * field, so a refusal is shown where the person is typing rather than in a
     * snackbar behind the settings card.
     */
    fun renameHousehold(name: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.renameGroup(groupId, RenameGroupRequest(name.trim()))
                if (response.isSuccessful) {
                    refreshTasksAndProgress()
                    onResult(null)
                } else {
                    onResult(ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                onResult(networkMessage(e))
            }
        }
    }

    /**
     * Whether someone joining goes at the end of every chore everyone shares.
     * Anyone in the household can change it; the feed says who did.
     */
    fun setAddNewMembers(on: Boolean, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.renameGroup(groupId, RenameGroupRequest(addNewMembers = on))
                if (response.isSuccessful) {
                    refreshTasksAndProgress()
                    onResult(null)
                } else {
                    onResult(ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                onResult(networkMessage(e))
            }
        }
    }

    /** Make [member] the owner. Only the owner is offered this; the server checks again. */
    fun handOver(member: MemberInfo) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null)
            try {
                val response = api.handOver(groupId, HandOverRequest(member.id))
                if (response.isSuccessful) {
                    refreshTasksAndProgress()
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        message = "${member.username} owns the household now.",
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isWorking = false, message = ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isWorking = false, message = networkMessage(e))
            }
        }
    }

    fun dismissMessage() {
        // The offer goes with the message it belonged to. A stale Undo sitting
        // on the next snackbar would be a button that reassigns a chore
        // nobody was talking about.
        _uiState.value = _uiState.value.copy(
            message = null, undoablePass = null, undoableDone = null,
        )
    }

    fun dismissInviteCode() {
        _uiState.value = _uiState.value.copy(inviteCode = null)
    }

    // ─── Plumbing ───────────────────────────────────────────────

    /**
     * Runs a mutating call, then reloads.
     *
     * Everything is re-read from the server afterwards rather than patched
     * locally: this screen is shared by several people, so the authoritative
     * state is the backend's, and a local edit would paper over a teammate's
     * concurrent change.
     */
    // ─── Chores and occurrences (F2) ────────────────────────────

    /**
     * Define a chore. The backend creates its first occurrence too, so it
     * appears on the board immediately, on the row of whoever is first in
     * [rotation].
     */
    fun createChore(
        name: String,
        doneLine: String,
        scheduleType: String,
        intervalDays: Int?,
        fixedWeekdays: List<Int>?,
        rotation: List<String>,
        // Both of these were already in the request and neither had a way in
        // from the UI: the old form offered no needed-by time and no
        // day-of-month schedule, so "rent on the 1st" could not be said at all.
        fixedMonthDays: List<Int>? = null,
        neededByTime: String? = null,
        onResult: (String?) -> Unit = {},
    ) {
        runAction(successMessage = "${name.trim()} added.", onResult = onResult) {
            api.createChore(
                groupId,
                CreateChoreRequest(
                    name = name.trim(),
                    doneLine = doneLine.trim(),
                    scheduleType = scheduleType,
                    intervalDays = intervalDays,
                    fixedWeekdays = fixedWeekdays,
                    fixedMonthDays = fixedMonthDays,
                    neededByTime = neededByTime,
                    rotation = rotation,
                ),
            )
        }
    }

    /**
     * One-tap completion, and its undo.
     *
     * Anyone may tick anything — done_by records who actually did it. Undo goes
     * back to the same endpoint with "open"; the server refuses it with 403
     * unless the caller marked it and is inside the ten-minute window, so a
     * stale screen produces a clear message rather than a silent no-op.
     */
    fun toggleOccurrenceDone(occurrence: Occurrence, myUserId: String? = null) {
        if (!occurrence.isOpen) {
            runAction(successMessage = "Marked as not done") {
                api.updateOccurrence(groupId, occurrence.id, UpdateOccurrenceRequest(status = "open"))
            }
            return
        }
        runAction(
            // One state write for the message and its Undo, for the reason
            // given in passOccurrence.
            onResult = { failure ->
                _uiState.value = _uiState.value.copy(
                    message = failure ?: doneMessage(occurrence, myUserId),
                    undoableDone = if (failure == null) occurrence.id else null,
                )
            },
        ) {
            api.updateOccurrence(groupId, occurrence.id, UpdateOccurrenceRequest(status = "done"))
        }
    }

    /**
     * "Trash done. bb is next." — read off the turn the server just spawned
     * (it has refreshed by the time this runs), never predicted here: the
     * client does not hold the debts and passes that decide it, deliberately.
     * A one-off spawns nothing, so it has no "next".
     */
    private fun doneMessage(occurrence: Occurrence, myUserId: String?): String {
        val done = "${occurrence.choreName} done."
        val next = _uiState.value.occurrences.firstOrNull { it.spawnedFrom == occurrence.id }
            ?: return done
        if (next.assignedTo == myUserId) return "$done You're next."
        val name = _uiState.value.members.firstOrNull { it.id == next.assignedTo }?.username
            ?: return done
        return "$done $name is next."
    }

    /** Take back the completion the snackbar is offering to undo. */
    fun undoDone(occurrenceId: String) {
        _uiState.value = _uiState.value.copy(undoableDone = null)
        runAction(successMessage = "Marked as not done") {
            api.updateOccurrence(groupId, occurrenceId, UpdateOccurrenceRequest(status = "open"))
        }
    }

    /**
     * "I worked on this too", or taking that back.
     *
     * The snackbar repeats what it does not do, because the obvious worry is
     * that it is a way to take credit for — or comment on — somebody else's
     * turn. It is neither.
     */
    fun addAlso(occurrence: Occurrence) {
        runAction(successMessage = "Added. It's still counted as their turn.") {
            api.addAlso(groupId, occurrence.id)
        }
    }

    fun removeAlso(occurrence: Occurrence) {
        runAction(successMessage = "Taken off.") {
            api.removeAlso(groupId, occurrence.id)
        }
    }

    // ─── History (F6) ───────────────────────────────────────────

    /** One chore's timeline. Cleared first, so the sheet never shows another chore's. */
    fun loadChoreHistory(choreId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(choreHistory = null, historyLoading = true)
            val history = runCatching { api.choreHistory(groupId, choreId) }
                .getOrNull()?.takeIf { it.isSuccessful }?.body()
            _uiState.value = _uiState.value.copy(
                choreHistory = history,
                historyLoading = false,
                message = if (history == null) "Couldn't load this chore's history" else null,
            )
        }
    }

    /** The per-person view for a window: "week", "month" or "quarter". */
    fun loadGroupHistory(window: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(historyWindow = window, historyLoading = true)
            val history = runCatching { api.groupHistory(groupId, window) }
                .getOrNull()?.takeIf { it.isSuccessful }?.body()
            _uiState.value = _uiState.value.copy(
                groupHistory = history,
                historyLoading = false,
                message = if (history == null) "Couldn't load history" else null,
            )
        }
    }

    fun clearHistory() {
        _uiState.value = _uiState.value.copy(choreHistory = null, groupHistory = null)
    }

    /**
     * Busy — pass this one (F5).
     *
     * Hands an open occurrence of yours to the next person in the rotation. The
     * turn comes back to you next cycle, so the snackbar says so: someone
     * expecting to have got rid of it should find that out from the app, not
     * from the board a week later.
     */
    fun passOccurrence(occurrence: Occurrence, receiverName: String? = null) {
        val passed = receiverName?.let { "Passed to $it. It's yours again next time." }
            ?: "Passed on. It comes back to you next time."
        runAction(
            // The message is written here rather than through successMessage so
            // that it and the Undo offer land in a *single* state write. Two
            // writes would let the snackbar be composed from the first one and
            // read an offer that had not arrived yet, which is a race that
            // shows up as an Undo button that is sometimes missing.
            onResult = { failure ->
                _uiState.value = _uiState.value.copy(
                    message = failure ?: passed,
                    undoablePass = if (failure == null) occurrence.id else null,
                )
            },
        ) {
            api.passOccurrence(groupId, occurrence.id)
        }
    }

    /**
     * Pick the day a chore nobody could take will happen.
     *
     * Only ever earlier than the date it arrived with; the server enforces
     * that, and its refusal is what the person sees rather than a generic
     * failure.
     */
    fun setOccurrenceDueDate(occurrenceId: String, dueDate: String) {
        runAction(successMessage = "Day set.") {
            api.setOccurrenceDueDate(groupId, occurrenceId, mapOf("due_date" to dueDate))
        }
    }

    /**
     * Take back the pass the snackbar is offering to undo.
     *
     * For the swipe that caught the wrong row. The server decides whether it is
     * still allowed, and says why when it is not: the window is measured there
     * from `passed_at`, because a client's own timer is not evidence. Whatever
     * it says is what the person sees, rather than a generic failure.
     */
    fun undoPass(occurrenceId: String) {
        _uiState.value = _uiState.value.copy(undoablePass = null)
        runAction(successMessage = "Back with you.") {
            api.undoPass(groupId, occurrenceId)
        }
    }

    /**
     * Away, or back (F5).
     *
     * [until] is an RFC 3339 instant, or null for open-ended. Members are
     * reloaded on success because away is shown throughout the group's screens,
     * and runAction refreshes the board but not the member list.
     */
    fun setAway(away: Boolean, until: String?) {
        // Going away hands on whatever is already yours, if somebody in its
        // rotation is home to take it, and pauses the household if you were
        // the last one home (pause.go). The server says which happened.
        var result: AwayResponse? = null
        runAction(
            // Reloaded *after* the write lands, not alongside it — firing both
            // at once would race, and the loser would paint the old state back.
            onResult = { failure ->
                if (failure == null) {
                    _uiState.value = _uiState.value.copy(message = awayMessage(away, result))
                    viewModelScope.launch { refreshMembers() }
                } else {
                    _uiState.value = _uiState.value.copy(message = failure)
                }
            },
        ) {
            api.setAway(groupId, SetAwayRequest(away = away, until = until)).also { result = it.body() }
        }
    }

    private fun awayMessage(away: Boolean, result: AwayResponse?): String = when {
        !away -> "Welcome back"
        result?.paused == true -> "You're marked away. Everyone's away now, so the household is paused."
        (result?.handedOn ?: 0) > 0 ->
            "You're marked away. Your turns went to the next person and come back to you when you're back."
        else -> "You're marked away"
    }

    /**
     * Re-reads the member list. Away is shown throughout the group's screens,
     * and [runAction] refreshes the board but not the roster.
     */
    private suspend fun refreshMembers() {
        runCatching { api.listMembers(groupId) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body()
            ?.let { _uiState.value = _uiState.value.copy(members = it) }
    }

    // Quiet hours are *read* here — they gate this group's reminders — but no
    // longer written here. They are one window per person, not per household,
    // so the setting moved to the Dashboard (ShellViewModel.setQuietHours)
    // where every group is in view.

    /**
     * Edit a chore — open to every member, by design.
     *
     * Only changed fields are sent: the backend rejects an empty patch, and
     * restating a field you didn't touch would clobber a concurrent edit by
     * someone else. It also phrases the group diff from what actually changed,
     * so sending everything would announce changes nobody made.
     *
     * The schedule *type* is absent on purpose and cannot be changed here — a
     * chore that switched category would leave its open occurrence holding a
     * due date derived from rules that no longer apply.
     */
    fun updateChore(
        choreId: String,
        name: String?,
        doneLine: String?,
        intervalDays: Int?,
        fixedWeekdays: List<Int>?,
        rotation: List<String>?,
        scheduleType: String? = null,
        fixedMonthDays: List<Int>? = null,
        neededByTime: String? = null,
        onResult: (String?) -> Unit = {},
    ) {
        if (name == null && doneLine == null && intervalDays == null &&
            fixedWeekdays == null && rotation == null &&
            scheduleType == null && fixedMonthDays == null && neededByTime == null
        ) {
            onResult(null)
            return
        }
        runAction(successMessage = "Chore updated", onResult = onResult) {
            api.updateChore(
                groupId,
                choreId,
                UpdateChoreRequest(
                    name = name?.trim(),
                    doneLine = doneLine?.trim(),
                    scheduleType = scheduleType,
                    intervalDays = intervalDays,
                    fixedWeekdays = fixedWeekdays,
                    fixedMonthDays = fixedMonthDays,
                    neededByTime = neededByTime,
                    rotation = rotation,
                ),
            )
        }
    }

    fun deleteChore(choreId: String) {
        runAction(successMessage = "Chore deleted") { api.deleteChore(groupId, choreId) }
    }

    /**
     * Runs one mutation, then refreshes what it may have changed.
     *
     * [onResult] reports the outcome back to a caller that owns a form: null on
     * success, otherwise the message to show. A form needs this because it must
     * stay open until the write actually lands — closing first and reporting
     * afterwards throws away everything the user typed (B-7).
     *
     * When [onResult] is given, a failure is *not* also put in [message]: the
     * caller is showing it in the form, and a snackbar saying the same thing
     * from behind the dialog is just noise.
     */
    private fun runAction(
        successMessage: String? = null,
        onResult: ((String?) -> Unit)? = null,
        block: suspend () -> Response<*>,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isWorking = true, message = null)
            try {
                val response = block()
                if (response.isSuccessful) {
                    // The board and the feed at the same time; neither waits on the other.
                    coroutineScope {
                        launch { refreshTasksAndProgress() }
                        launch { reloadActivityFromScratch() }
                    }
                    _uiState.value = _uiState.value.copy(isWorking = false, message = successMessage)
                    onResult?.invoke(null)
                } else {
                    val failure = ApiErrors.messageFor(response)
                    _uiState.value = _uiState.value.copy(
                        isWorking = false,
                        message = if (onResult == null) failure else null,
                    )
                    onResult?.invoke(failure)
                }
            } catch (e: Exception) {
                val failure = networkMessage(e)
                _uiState.value = _uiState.value.copy(
                    isWorking = false,
                    message = if (onResult == null) failure else null,
                )
                onResult?.invoke(failure)
            }
        }
    }

    /**
     * Re-reads the feed from the top after our own write.
     *
     * The cursor is reset because our new event may share a timestamp with ones
     * already held, and a `since` poll is strictly-greater-than.
     */
    private suspend fun reloadActivityFromScratch() {
        val fresh = runCatching { api.listActivity(groupId) }.getOrNull()
            ?.takeIf { it.isSuccessful }?.body() ?: return
        activityCursor = fresh.firstOrNull()?.createdAt?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        _uiState.value = _uiState.value.copy(activity = fresh)
    }

    /** Mirrors the UI's status labels so snackbars read the same as the chips. */
    private fun statusLabelFor(status: String) = when (status) {
        "todo" -> "To do"
        "in_progress" -> "In progress"
        "done" -> "Done"
        else -> status
    }

    private fun networkMessage(e: Throwable) = ApiErrors.forFailure(e)

    /** Supplies [groupId] to the ViewModel; no DI framework in this project. */
    class Factory(private val groupId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GroupDetailViewModel(groupId) as T
    }
}
