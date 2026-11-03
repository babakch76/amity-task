package com.taskflow.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.data.model.InviteActionRequest
import com.taskflow.app.data.model.InviteInfo
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.CreateChoreRequest
import com.taskflow.app.data.model.RedeemInviteRequest
import com.taskflow.app.data.model.UpdateMeRequest
import com.taskflow.app.data.model.UpdateOccurrenceRequest
import com.taskflow.app.data.model.User
import com.taskflow.app.data.remote.ApiErrors
import com.taskflow.app.data.remote.RetrofitClient
import com.taskflow.app.reminders.QuietHours
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents the three possible states of the Dashboard UI.
 */
sealed class ShellUiState {
    /** Initial state while the group list is being fetched. */
    object Loading : ShellUiState()

    /** Groups loaded successfully. [groups] may be empty. */
    data class Success(val groups: List<Group>) : ShellUiState()

    /** Network or server error. [message] is user-friendly text. */
    data class Error(val message: String) : ShellUiState()
}

/**
 * ViewModel for the Dashboard screen (group listing + inbound invites).
 *
 * Invites live here rather than in their own screen because both ways of
 * joining a group land on this list: accepting a direct invite, and redeeming a
 * shared code. Keeping them together means one refresh after either path.
 */
class ShellViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ShellUiState>(ShellUiState.Loading)
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Pending invites addressed to this user, for the top-bar badge. */
    private val _invites = MutableStateFlow<List<InviteInfo>>(emptyList())
    val invites: StateFlow<List<InviteInfo>> = _invites.asStateFlow()

    /**
     * Emitted once when the user turns out to be in exactly one group, so the
     * app can go straight to its board — F1: "board is the app's default
     * screen after login", and the onboarding loop ends "land on the board".
     *
     * One-shot on purpose. Forwarding every time the group list appears would
     * make Back from the board bounce straight forward again, trapping the
     * user. [consumeAutoOpen] latches it, and the flag lives as long as this
     * ViewModel — i.e. as long as the Dashboard's place on the back stack — so
     * navigating back leaves you on the list, which is what you asked for by
     * pressing Back.
     */
    private val _autoOpenGroupId = MutableStateFlow<String?>(null)
    val autoOpenGroupId: StateFlow<String?> = _autoOpenGroupId.asStateFlow()
    private var autoOpenLatched = false

    fun consumeAutoOpen() {
        autoOpenLatched = true
        _autoOpenGroupId.value = null
    }

    /** One-shot feedback for an invite action; the screen shows it and clears it. */
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    /**
     * A completion the message on screen can still take back, as (group,
     * occurrence) — the same Undo the board's snackbar offers. Set before the
     * message it belongs to, and cleared with it, so a stale Undo never rides
     * on the next one. The server's ten-minute window is the permission.
     */
    private val _undoableDone = MutableStateFlow<Pair<String, String>?>(null)
    val undoableDone: StateFlow<Pair<String, String>?> = _undoableDone.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val api get() = RetrofitClient.getInstance().groupApi

    init {
        fetchGroups()
        fetchInvites()
    }

    /**
     * Fetch the user's groups from the Go backend.
     * Called on init and when the user taps "Retry" or pulls to refresh.
     */
    fun fetchGroups() {
        viewModelScope.launch {
            // Only show full-screen loading on initial load, not on refresh
            if (_uiState.value !is ShellUiState.Success) {
                _uiState.value = ShellUiState.Loading
            }
            _isRefreshing.value = true

            try {
                val response = api.listMyGroups()

                if (response.isSuccessful) {
                    val groups = response.body() ?: emptyList()
                    _uiState.value = ShellUiState.Success(groups)
                    if (!autoOpenLatched && groups.size == 1) {
                        _autoOpenGroupId.value = groups.first().id
                    }
                    loadReminderData(groups)
                } else {
                    _uiState.value = ShellUiState.Error(ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                _uiState.value = ShellUiState.Error(ApiErrors.forFailure(e))
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Quietly checks the list of households, so one removed by its owner leaves
     * this phone without anybody having to restart the app.
     *
     * Separate from [fetchGroups], which turns a failed request into an error
     * screen. A dropped poll must not do that, so anything but a clean answer
     * is ignored and the next tick tries again.
     */
    fun pollHouseholds() {
        viewModelScope.launch {
            val current = (_uiState.value as? ShellUiState.Success)?.groups ?: return@launch
            val groups = runCatching { api.listMyGroups() }.getOrNull()
                ?.takeIf { it.isSuccessful }?.body() ?: return@launch
            if (groups.map { it.id }.toSet() != current.map { it.id }.toSet()) {
                _uiState.value = ShellUiState.Success(groups)
                loadReminderData(groups)
            }
        }
    }

    private var choresPoll: Job? = null

    /**
     * Quietly checks every household's turns and chores, so Mine shows a
     * housemate's change while it is open, not only when the tab is reopened.
     *
     * Reads the turns themselves rather than the feed, because a turn passed to
     * you makes no feed entry. Only reloads when something actually differs,
     * and a household still being loaded is left to that load. A check still
     * waiting on a slow network is not stacked on.
     */
    fun pollChores() {
        if (choresPoll?.isActive == true) return
        choresPoll = viewModelScope.launch {
            val groups = (_uiState.value as? ShellUiState.Success)?.groups ?: return@launch
            val held = _reminderData.value.associateBy { it.groupId }
            val changed = groups.map { group ->
                async {
                    val mine = held[group.id] ?: return@async false
                    val occurrences = runCatching { api.listOccurrences(group.id) }
                        .getOrNull()?.takeIf { it.isSuccessful }?.body() ?: return@async false
                    val chores = runCatching { api.listChores(group.id) }
                        .getOrNull()?.takeIf { it.isSuccessful }?.body() ?: return@async false
                    occurrences.toSet() != mine.occurrences.toSet() || chores.toSet() != mine.chores.toSet()
                }
            }.awaitAll().any { it }
            if (changed) loadReminderData(groups)
        }
    }

    /**
     * Everything one group needs for its reminders to be scheduled (F3, B-8).
     */
    data class GroupReminderData(
        val groupId: String,
        val occurrences: List<Occurrence>,
        val chores: List<Chore>,
        /** Who is away, so the month on Mine can step over them as the server does. */
        val members: List<MemberInfo> = emptyList(),
    )

    /**
     * Board data for **every** group the user belongs to, plus their quiet
     * hours — enough for the screen to arm reminders across all of them.
     *
     * The board can only ever schedule for the group you happen to have open,
     * so a second household's turns went unmentioned until you visited it
     * (B-8). The dashboard is the one place that sees them all.
     */
    private val _reminderData = MutableStateFlow<List<GroupReminderData>>(emptyList())
    val reminderData: StateFlow<List<GroupReminderData>> = _reminderData.asStateFlow()

    private val _quietHours = MutableStateFlow(QuietHours.DEFAULT)
    val quietHours: StateFlow<QuietHours> = _quietHours.asStateFlow()

    /**
     * The signed-in person's own account, for the Settings tab's header and
     * its two identity dialogs.
     *
     * Fetched alongside the reminder data rather than on its own, because the
     * quiet-hours window comes from the same row and one request answers both.
     * Null until the first fetch lands; Settings shows what it has.
     */
    private val _me = MutableStateFlow<User?>(null)
    val me: StateFlow<User?> = _me.asStateFlow()

    /**
     * Change the name housemates see, or the address used to sign in.
     *
     * One call rather than two, because the dialogs send one field each and
     * the endpoint takes either. The server does the real validation — empty
     * or over-long names, malformed addresses, and names already taken — and
     * [ApiErrors] passes its wording through, so the two sides cannot drift
     * into disagreeing about what is allowed.
     *
     * @param onResult null on success, otherwise the message to show in the
     *   dialog that is still open. A failure must not close it: the typed
     *   value is the only copy of what the person meant.
     */
    fun updateProfile(
        username: String? = null,
        email: String? = null,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val response = api.updateMe(UpdateMeRequest(username = username, email = email))
                val updated = response.body()
                if (response.isSuccessful && updated != null) {
                    _me.value = updated
                    _actionMessage.value =
                        if (username != null) "Name updated" else "Email updated"
                    onResult(null)
                } else {
                    onResult(ApiErrors.messageFor(response))
                }
            } catch (e: Exception) {
                onResult(networkMessage(e))
            } finally {
                _isWorking.value = false
            }
        }
    }

    /**
     * Tick or untick one of your own turns from the My chores tab.
     *
     * The board's own version of this lives in [GroupDetailViewModel] and
     * cannot be reached from here: this tab spans every household, so it has
     * no single group's ViewModel to ask. Both end up at the same endpoint,
     * and the server is what decides whether an undo is still allowed.
     *
     * Reloads the whole cross-household set afterwards rather than patching
     * one row in place. Marking a chore done creates its next occurrence, so
     * the list genuinely changes shape — and this is the same fetch that arms
     * the reminders, which the new occurrence needs too.
     */
    fun setOccurrenceDone(groupId: String, occurrenceId: String, done: Boolean, name: String? = null) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val response = api.updateOccurrence(
                    groupId,
                    occurrenceId,
                    UpdateOccurrenceRequest(status = if (done) "done" else "open"),
                )
                if (response.isSuccessful) {
                    _undoableDone.value = if (done) groupId to occurrenceId else null
                    _actionMessage.value = when {
                        !done -> "Marked as not done"
                        name != null -> "$name done."
                        else -> "Marked done"
                    }
                    (uiState.value as? ShellUiState.Success)?.let { loadReminderData(it.groups) }
                } else {
                    _actionMessage.value = ApiErrors.messageFor(response)
                }
            } catch (e: Exception) {
                _actionMessage.value = networkMessage(e)
            } finally {
                _isWorking.value = false
            }
        }
    }

    /**
     * Move the quiet-hours window (F3).
     *
     * It lives here, on the screen that lists every household, because it is a
     * property of the *person*: one window, stored on the user record, applying
     * to reminders from all of their groups. It used to sit in a single group's
     * overflow menu, which said the opposite.
     *
     * Writing it here also re-arms every group's alarms in one step, since the
     * screen reschedules whenever this flow changes.
     */
    fun setQuietHours(from: String, to: String) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val response = api.updateMe(UpdateMeRequest(quietFrom = from, quietTo = to))
                val updated = response.body()
                if (response.isSuccessful && updated != null) {
                    _quietHours.value = QuietHours.parse(updated.quietFrom, updated.quietTo)
                    _actionMessage.value = "Quiet hours updated"
                } else {
                    _actionMessage.value = ApiErrors.messageFor(response)
                }
            } catch (e: Exception) {
                _actionMessage.value = networkMessage(e)
            } finally {
                _isWorking.value = false
            }
        }
    }

    /**
     * Fetches occurrences and chores for each group, in parallel.
     *
     * That is two requests per group — fine for a household app, where the
     * number of groups is one or two, and the alternative is an aggregate
     * endpoint that does not exist yet.
     *
     * Failures are silent by design. This drives reminders, not the screen: a
     * group whose board could not be fetched simply keeps whatever alarms it
     * already had, which is better than an error banner over a group list that
     * loaded perfectly well.
     */
    private suspend fun loadReminderData(groups: List<Group>) = coroutineScope {
        val me = runCatching { api.getMe() }.getOrNull()?.takeIf { it.isSuccessful }?.body()
        if (me != null) _me.value = me
        _quietHours.value = QuietHours.parse(me?.quietFrom, me?.quietTo)

        _reminderData.value = groups
            .map { group ->
                async {
                    val occurrences = runCatching { api.listOccurrences(group.id) }
                        .getOrNull()?.takeIf { it.isSuccessful }?.body()
                    val chores = runCatching { api.listChores(group.id) }
                        .getOrNull()?.takeIf { it.isSuccessful }?.body()
                    val members = runCatching { api.listMembers(group.id) }
                        .getOrNull()?.takeIf { it.isSuccessful }?.body()
                    if (occurrences == null) null
                    else GroupReminderData(group.id, occurrences, chores.orEmpty(), members.orEmpty())
                }
            }
            .awaitAll()
            .filterNotNull()
    }

    /**
     * Creates the starter chores a new household picked, in the order shown.
     *
     * Sequential rather than parallel, and deliberately. The board sorts by
     * status and due date rather than by creation, so this is not about the
     * order rows appear in; it is about a partial failure being legible.
     * Five requests in flight at once can fail in the middle in five ways,
     * and the household is left guessing which of its chores exist.
     *
     * Stops at the first failure and reports it, rather than leaving a
     * half-built board with no explanation.
     */
    fun addStarterChores(
        groupId: String,
        myUserId: String?,
        starters: List<StarterChore>,
        onResult: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val rotation = listOfNotNull(myUserId)
                for (starter in starters) {
                    val response = api.createChore(
                        groupId,
                        CreateChoreRequest(
                            name = starter.name.trim(),
                            scheduleType = starter.kind.scheduleType,
                            intervalDays = starter.intervalDays
                                .takeIf { starter.kind == ChoreKind.INTERVAL },
                            fixedWeekdays = listOf(starter.weekday)
                                .takeIf { starter.kind == ChoreKind.FIXED_DATE },
                            rotation = rotation,
                        ),
                    )
                    if (!response.isSuccessful) {
                        onResult(ApiErrors.messageFor(response))
                        return@launch
                    }
                }
                onResult(null)
            } catch (e: Exception) {
                onResult(networkMessage(e))
            } finally {
                _isWorking.value = false
            }
        }
    }

    /**
     * Load pending invites. Failures are silent: an unreachable invites endpoint
     * should not blank out a group list that loaded fine.
     */
    fun fetchInvites() {
        viewModelScope.launch {
            val response = runCatching { api.listMyInvites() }.getOrNull() ?: return@launch
            if (response.isSuccessful) {
                _invites.value = response.body().orEmpty()
            }
        }
    }

    /**
     * Accept or decline a direct invite.
     *
     * @param action "accept" or "decline" — the values the backend validates.
     */
    fun respondToInvite(inviteId: String, action: String, groupName: String) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val response = api.respondToInvite(inviteId, InviteActionRequest(action))
                if (response.isSuccessful) {
                    _actionMessage.value =
                        if (action == "accept") "You joined $groupName" else "Invite declined"
                    // Accepting adds a membership, so both lists changed.
                    fetchInvites()
                    fetchGroups()
                } else {
                    _actionMessage.value = ApiErrors.messageFor(response)
                    // A 409/410 means our copy of the invite is stale — reload it.
                    fetchInvites()
                }
            } catch (e: Exception) {
                _actionMessage.value = networkMessage(e)
            } finally {
                _isWorking.value = false
            }
        }
    }

    /**
     * Join a group with a shared invite code.
     *
     * The backend distinguishes the failure modes for us — 404 unknown code,
     * 410 expired or used up, 409 already a member — and [ApiErrors] surfaces
     * whichever message came back rather than a generic "failed".
     */
    fun redeemCode(code: String) {
        viewModelScope.launch {
            _isWorking.value = true
            try {
                val response = api.redeemInviteCode(RedeemInviteRequest(code.trim()))
                if (response.isSuccessful) {
                    _actionMessage.value = response.body()?.message ?: "Joined the household"
                    fetchGroups()
                    fetchInvites()
                } else {
                    _actionMessage.value = ApiErrors.messageFor(response)
                }
            } catch (e: Exception) {
                _actionMessage.value = networkMessage(e)
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
        _undoableDone.value = null
        _undoablePass.value = null
    }

    /**
     * Show a message that came from somewhere else — a household's own
     * ViewModel, behind a sheet opened from My chores — on the shell's
     * snackbar, which is the one My chores can see. [undo] is the completion
     * it can take back, if it was one.
     */
    fun showMessage(text: String, undo: Pair<String, String>? = null, undoPass: Pair<String, String>? = null) {
        _undoableDone.value = undo
        _undoablePass.value = undoPass
        _actionMessage.value = text
    }

    /** A pass the message on screen can take back, as (group, occurrence). */
    private val _undoablePass = MutableStateFlow<Pair<String, String>?>(null)
    val undoablePass: StateFlow<Pair<String, String>?> = _undoablePass.asStateFlow()

    /** Take back a pass made from a sheet opened on Mine. */
    fun undoPass(groupId: String, occurrenceId: String) {
        viewModelScope.launch {
            try {
                val response = api.undoPass(groupId, occurrenceId)
                _actionMessage.value = if (response.isSuccessful) "Back with you." else ApiErrors.messageFor(response)
                (uiState.value as? ShellUiState.Success)?.let { loadReminderData(it.groups) }
            } catch (e: Exception) {
                _actionMessage.value = networkMessage(e)
            }
        }
    }

    /**
     * Alias for [fetchGroups] — semantic name for pull-to-refresh / retry.
     * Also re-checks invites, since a teammate may have sent one meanwhile.
     */
    fun refresh() {
        fetchGroups()
        fetchInvites()
    }

    private fun networkMessage(e: Throwable) = ApiErrors.forFailure(e)
}
