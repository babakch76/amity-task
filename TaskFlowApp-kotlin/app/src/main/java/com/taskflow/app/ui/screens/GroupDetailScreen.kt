package com.taskflow.app.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.taskflow.app.TaskFlowApp
import com.taskflow.app.reminders.QuietHours
import com.taskflow.app.reminders.ReminderScheduler
import com.taskflow.app.ui.components.HouseholdSwitcher
import com.taskflow.app.ui.components.MarkDoneSheet
import com.taskflow.app.data.local.BoardFolds
import com.taskflow.app.data.model.ActivityEvent
import com.taskflow.app.data.model.Absence
import com.taskflow.app.data.model.Booking
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.ChoreHistory
import com.taskflow.app.data.model.ChoreHistoryEntry
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.GroupHistory
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.neededByWording
import com.taskflow.app.data.model.scheduleWording
import com.taskflow.app.data.model.ScheduleType
import com.taskflow.app.data.model.Task
import com.taskflow.app.data.model.isOpen
import com.taskflow.app.data.model.joinedNames
import com.taskflow.app.ui.theme.Quiet
import com.taskflow.app.ui.components.CapsLabel
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Luggage
import androidx.compose.material3.Switch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.taskflow.app.ui.components.Avatar
import com.taskflow.app.ui.components.AvatarStack
import com.taskflow.app.ui.components.DOCK_CLEARANCE
import com.taskflow.app.ui.components.DoneAvatar
import com.taskflow.app.ui.components.GroupedCard
import com.taskflow.app.ui.components.QuietAlertDialog
import com.taskflow.app.ui.components.QuietSnackbar
import com.taskflow.app.ui.components.showToast
import com.taskflow.app.ui.components.RowDivider
import com.taskflow.app.ui.components.ScreenTitle
import com.taskflow.app.ui.components.SectionLabel
import com.taskflow.app.ui.components.FoldLabel
import com.taskflow.app.ui.components.SegmentedPair
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How often the activity feed is polled while this screen is on screen. */
private const val ACTIVITY_POLL_MILLIS = 5_000L

// open / done, and nothing else — the v2 status language.
//
// "todo" stays as the wire value because that is what the tasks table holds and
// what every existing row already says; only the word shown changed. Legacy
// rows still carrying "in_progress" read as open (isOpen is status != "done"),
// so nothing needs migrating and no history is rewritten.
private val TASK_STATUSES = listOf("todo", "done")

/**
 * One household's board: chores, activity feed, members and invites.
 *
 * Three tabs rather than one long scroll: the activity feed is append-only and
 * would otherwise push the board off screen.
 *
 * There was a fourth, a month view of due dates. It read `state.tasks` only, so
 * it showed the one-off minority of the household's work and hid every rotation
 * chore — and teaching it about occurrences would have meant building the
 * scheduling view constraint 5 rules out. The board is the screen.
 *
 * **This is a root destination now**, the first tab of [MainShell] rather than
 * a screen pushed on top of a group list. So it has no back arrow: its title is
 * the household switcher, and changing household happens in place. The bottom
 * bar sits underneath; the create-chore flow is a full-screen `Dialog` and
 * covers it, which is what DESIGN.md §4.1 asks for.
 *
 * @param groupId Which household. [HouseholdTab] resolves it, and the
 *   ViewModel is keyed on it — switching household builds a new one rather than
 *   leaving the last household's chores on screen under a new name.
 * @param groups Every household the person is in, for the switcher menu.
 * @param onSelectGroup Open a different household, in place.
 * @param onLeft Called once leaving has gone through, since this household may
 *   no longer exist. The caller re-resolves which household is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    groups: List<Group>,
    onSelectGroup: (String) -> Unit,
    onCreateHousehold: () -> Unit,
    onJoinHousehold: () -> Unit,
    onLeft: (removed: Boolean) -> Unit,
    /** Which part of the household this destination shows (House, Feed or People). */
    section: HouseSection = HouseSection.BOARD,
    /** Add on the dock was pressed: open the create flow once, then say so. */
    createRequested: Boolean = false,
    onCreateHandled: () -> Unit = {},
    onOpenPeople: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: GroupDetailViewModel = viewModel(
        key = groupId,
        factory = GroupDetailViewModel.Factory(groupId),
    ),
) {
    val state by viewModel.uiState.collectAsState()

    // Feed: the stream of what happened, or the per-person record.
    var feedView by rememberSaveable { mutableIntStateOf(0) }
    // One create flow, not two. Whether the thing repeats is a question inside
    // the form, not a choice of button — see CreateChoreFlow.
    var showCreate by remember { mutableStateOf(false) }
    // The name of the thing just added, so its row can be picked out of the
    // board for a moment. Cleared on a timer: a highlight that stays is just a
    // second kind of status.
    var justAdded by remember { mutableStateOf<String?>(null) }
    // What the detail sheet and everything behind it currently has open. Shared
    // with My chores through [OccurrenceDetailHost], so a chore opened from
    // either place behaves identically — including the busy pass, which both a
    // swipe and the sheet route through, so the rule is stated once.
    val detail = rememberChoreDetailController()
    var showAway by remember { mutableStateOf(false) }

    // Per-form submit state, so a create dialog can stay open until the write
    // lands and show a rejection where the user is already looking (B-7).
    var createSubmitting by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    var showInvite by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    // Up here rather than in the section: the section moves when the board goes
    // from empty to not, and a housemate's new chore must not close the sheet.
    var showBookIt by remember { mutableStateOf(false) }
    var cancellingBooking by remember { mutableStateOf<Booking?>(null) }
    // Task pending deletion — deleting is irreversible, so it goes via a confirm.
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    // Detail sheet holds an *id*, not a Task. The Task is looked up from state
    // on each recomposition, so an edit (ours or a teammate's) is reflected
    // instead of the sheet showing the snapshot it opened with.
    var detailTaskId by remember { mutableStateOf<String?>(null) }
    val detailTask = detailTaskId?.let { id -> state.tasks.firstOrNull { it.id == id } }

    // Which member row is "me" — needed so the owner isn't offered a demote
    // button on their own row.
    val context = LocalContext.current
    val myUserId = remember {
        (context.applicationContext as? TaskFlowApp)?.tokenManager?.getUserId()
    }

    val iAmAway = state.members.firstOrNull { it.id == myUserId }?.away == true

    // Shown once, under the first row you can actually swipe, and never again
    // on this device. Whether somebody has found a gesture is not household
    // data, so it stays local.
    var showSwipeHint by remember { mutableStateOf(!LocalHints.seen(context, LocalHints.Hint.SWIPE)) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Activity feed poll — the awareness loop. Lifecycle-aware, so it pauses
    // when the app is backgrounded and ticks immediately on return rather than
    // showing stale data for up to a full interval.
    PollWhileResumed(intervalMillis = ACTIVITY_POLL_MILLIS, key = groupId) {
        viewModel.pollActivity()
    }

    // Leaving or removing it, the household is over for this person.
    LaunchedEffect(state.hasLeft) {
        if (state.hasLeft) onLeft(state.householdRemoved)
    }

    // F3 — ask once for permission to notify. Refusing costs only reminders:
    // the board has never depended on them, so there is nothing to explain and
    // nothing to re-ask for.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* granted or not, the app works the same */ }

    // Asked once per install. This screen now backs three destinations
    // (House, Feed, People), so "on entry" meant the prompt reappeared every
    // time someone who had said no changed tab.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !LocalHints.seen(context, LocalHints.Hint.NOTIFICATIONS_ASKED)) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            LocalHints.markSeen(context, LocalHints.Hint.NOTIFICATIONS_ASKED)
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Re-arm reminders whenever the board or the quiet window changes.
    //
    // Rescheduling is a full replacement and skips anything already delivered,
    // so running it on every board change is safe and keeps the alarms in step
    // with a rotation that may have moved while we were away.
    LaunchedEffect(state.occurrences, state.chores, state.quietFrom, state.quietTo, myUserId) {
        ReminderScheduler.reschedule(
            context = context.applicationContext,
            groupId = groupId,
            occurrences = state.occurrences,
            chores = state.chores,
            myUserId = myUserId,
            quiet = QuietHours.parse(state.quietFrom, state.quietTo),
        )
    }

    // The new row is marked for a moment so it can be found without hunting,
    // then stops being marked. A highlight that stays is a second status.
    LaunchedEffect(justAdded) {
        if (justAdded != null) {
            delay(2_500)
            justAdded = null
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { text ->
            // Read once, before showing. The state is cleared as soon as the
            // snackbar closes, so reading it afterwards would always find null.
            val undoablePass = state.undoablePass
            val undoableDone = state.undoableDone
            val result = snackbarHostState.showToast(
                message = text,
                actionLabel = (undoablePass ?: undoableDone)?.let { "Undo" },
            )
            when {
                result != SnackbarResult.ActionPerformed -> viewModel.dismissMessage()
                undoablePass != null -> viewModel.undoPass(undoablePass)
                undoableDone != null -> viewModel.undoDone(undoableDone)
                else -> viewModel.dismissMessage()
            }
        }
    }

    LaunchedEffect(createRequested, state.group) {
        if (createRequested && state.group != null) {
            showCreate = true
            onCreateHandled()
        }
    }

    LaunchedEffect(section, feedView) {
        if (section == HouseSection.FEED && feedView == 1) viewModel.loadGroupHistory(state.historyWindow)
    }

    val othersAway = state.members.filter { it.away && it.id != myUserId }
    val currentGroup = state.group?.let { loaded -> groups.firstOrNull { it.id == loaded.id } }
        ?: groups.firstOrNull { it.id == groupId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.padding(bottom = if (section == HouseSection.PEOPLE) 16.dp else 96.dp),
            ) { QuietSnackbar(it) }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (section) {
                HouseSection.BOARD -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The title *is* the switcher: the household you are
                    // looking at is changed in place, not by going back.
                    HouseholdSwitcher(
                        current = currentGroup,
                        groups = groups,
                        onSelect = { onSelectGroup(it.id) },
                        onCreate = onCreateHousehold,
                        onJoin = onJoinHousehold,
                        modifier = Modifier.weight(1f),
                    )
                    // Who lives here, as faces. Tapping them opens People —
                    // which is where inviting and saying you're away now live.
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(onClick = onOpenPeople)
                            .semantics { contentDescription = "People in this household" }
                            .padding(start = 6.dp, top = 5.dp, bottom = 5.dp, end = 2.dp),
                    ) {
                        AvatarStack(
                            people = state.members.take(5).map { Triple(it.username, it.id, it.away) },
                        )
                    }
                }

                HouseSection.FEED -> Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ScreenTitle(title = "What happened", over = currentGroup?.name)
                    SegmentedPair(
                        first = "Everything",
                        second = "Who did what",
                        selected = feedView,
                        onSelect = { feedView = it },
                    )
                }

                HouseSection.PEOPLE -> Column(
                    modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the board")
                        }
                        Text(
                            currentGroup?.name.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "People",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 8.dp),
                    )
                }
            }

            if (state.isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                state.isLoading && state.group == null -> LoadingBlock("Loading…")

                state.error != null -> GroupErrorBlock(
                    message = state.error ?: "",
                    onRetry = { viewModel.refresh() },
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (section) {
                        HouseSection.BOARD -> Column(modifier = Modifier.fillMaxSize()) {
                            // Away is meant to be visible — it is what explains
                            // why the rotation is stepping over someone.
                            if (iAmAway) {
                                AwayBanner(onBack = { viewModel.setAway(false, null) })
                            } else if (othersAway.isNotEmpty()) {
                                OthersAwayNote(othersAway)
                            }
                            TaskList(
                                groupId = groupId,
                                // Chore occurrences first: they are the model the
                                // app is moving to, and tasks are the legacy
                                // one-off shape still carried alongside them.
                                rows = state.occurrences.map { BoardRow.OccurrenceRow(it) } +
                                    state.tasks.map { BoardRow.TaskRow(it) },
                                chores = state.chores,
                                justAdded = justAdded,
                                members = state.members,
                                myUserId = myUserId,
                                showSwipeHint = showSwipeHint,
                                onOpenDetail = { row ->
                                    when (row) {
                                        is BoardRow.TaskRow -> detailTaskId = row.task.id
                                        is BoardRow.OccurrenceRow ->
                                            detail.open(row.occurrence.id)
                                    }
                                },
                                onToggleDone = { row ->
                                    when (row) {
                                        is BoardRow.TaskRow -> viewModel.setTaskStatus(
                                            row.task.id,
                                            if (row.isOpen) "done" else "todo",
                                        )
                                        is BoardRow.OccurrenceRow ->
                                            viewModel.toggleOccurrenceDone(row.occurrence, myUserId)
                                    }
                                },
                                onPass = { row ->
                                    (row as? BoardRow.OccurrenceRow)?.let {
                                        detail.confirmPass(it.occurrence)
                                    }
                                },
                                onSwiped = {
                                    if (showSwipeHint) {
                                        showSwipeHint = false
                                        LocalHints.markSeen(context, LocalHints.Hint.SWIPE)
                                    }
                                },
                                onCreate = { showCreate = true },
                                header = { modifier ->
                                    BookItSection(
                                        // Sent by the server as unfinished, but it
                                        // can finish while the screen is open.
                                        bookings = state.bookings.filter {
                                            it.endsAt.isAfter(OffsetDateTime.now())
                                        },
                                        myUserId = myUserId,
                                        onBook = { showBookIt = true },
                                        onCancel = { cancellingBooking = it },
                                        modifier = modifier,
                                    )
                                },
                            )
                        }

                        HouseSection.FEED -> if (feedView == 0) {
                            ActivityList(state.activity, state.members, myUserId)
                        } else {
                            GroupHistoryBody(
                                history = state.groupHistory,
                                window = state.historyWindow,
                                loading = state.historyLoading,
                                myUserId = myUserId,
                                onWindowChange = { viewModel.loadGroupHistory(it) },
                            )
                        }

                        HouseSection.PEOPLE -> MemberList(
                            members = state.members,
                            myUserId = myUserId,
                            onShareCode = { viewModel.generateInviteCode() },
                            onInviteByName = { showInvite = true },
                            // Coming back needs no explaining; going away
                            // does, so only that direction gets a dialog.
                            onAway = {
                                if (iAmAway) viewModel.setAway(false, null) else showAway = true
                            },
                            onLeave = { confirmLeave = true },
                            onRemove = { confirmRemove = true },
                            householdName = currentGroup?.name.orEmpty(),
                        )
                    }
                }
            }
        }
    }

    // Detail sheet. Closes itself if the task disappears — deleted here, or by
    // a teammate while it was open.
    detailTask?.let { task ->
        TaskDetailSheet(
            task = task,
            members = state.members,
            onDismiss = { detailTaskId = null },
            onSaveText = { title, description ->
                viewModel.updateTaskText(task.id, title, description)
            },
            onSetStatus = { viewModel.setTaskStatus(task.id, it) },
            onSetAssignee = { viewModel.setTaskAssignee(task.id, it) },
            onSetDueDate = { viewModel.setTaskDueDate(task.id, it) },
            onDelete = { taskToDelete = task },
        )
    }
    LaunchedEffect(detailTaskId, state.tasks) {
        if (detailTaskId != null && state.tasks.none { it.id == detailTaskId }) {
            detailTaskId = null
        }
    }

    // The detail sheet and everything behind it — history, the edit flow, the
    // delete confirm, the pass confirm. Shared with My chores.
    OccurrenceDetailHost(
        controller = detail,
        viewModel = viewModel,
        myUserId = myUserId,
    )

    if (showCreate) {
        CreateChoreFlow(
            members = state.members,
            myUserId = myUserId,
            submitting = createSubmitting,
            serverError = createError,
            onDismiss = { showCreate = false; createError = null },
            // Two write paths behind one flow, and the flow does not know which
            // it is using: a one-off is still a task, the three rotating kinds
            // are still chores, and the split is decided here from the answer
            // given on screen 1.
            //
            // It stays open until the write lands. Closing first and reporting
            // afterwards threw away everything typed into it (B-7).
            onSubmit = { draft ->
                createSubmitting = true
                createError = null
                val settle = { failure: String? ->
                    createSubmitting = false
                    if (failure == null) {
                        showCreate = false
                        justAdded = draft.name.trim()
                    } else {
                        createError = failure
                    }
                }
                if (draft.kind == ChoreKind.ONE_OFF) {
                    viewModel.createTask(
                        draft.name,
                        draft.doneLine,
                        draft.assignee ?: myUserId,
                        draft.dueDate?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        settle,
                    )
                } else {
                    viewModel.createChore(
                        name = draft.name,
                        doneLine = draft.doneLine,
                        scheduleType = draft.kind?.scheduleType ?: ScheduleType.INTERVAL,
                        intervalDays = draft.intervalDays.takeIf { draft.kind == ChoreKind.INTERVAL },
                        fixedWeekdays = listOf(draft.weekday)
                            .takeIf { draft.kind == ChoreKind.FIXED_DATE && draft.byWeekday },
                        rotation = draft.rotation,
                        fixedMonthDays = listOf(draft.monthDay)
                            .takeIf { draft.kind == ChoreKind.FIXED_DATE && !draft.byWeekday },
                        neededByTime = draft.neededBy?.takeIf { draft.kind != ChoreKind.AS_NEEDED },
                        onResult = settle,
                    )
                }
            },
        )
    }


    if (showAway) {
        AwayDialog(
            onDismiss = { showAway = false },
            onConfirm = { until ->
                showAway = false
                viewModel.setAway(true, until)
            },
        )
    }

    if (showInvite) {
        InviteByUsernameDialog(
            onDismiss = { showInvite = false },
            onInvite = { showInvite = false; viewModel.inviteByUsername(it) },
        )
    }

    state.inviteCode?.let { code ->
        InviteCodeDialog(code = code, onDismiss = { viewModel.dismissInviteCode() })
    }

    // Deleting a task is irreversible — there is no undelete endpoint, so a
    // snackbar-with-Undo would be a lie. Confirm up front instead.
    taskToDelete?.let { task ->
        QuietAlertDialog(
            onDismissRequest = { taskToDelete = null },
            title = { Text("Delete this task?", fontWeight = FontWeight.Bold) },
            text = {
                Text("\"${task.title}\" will be removed for everyone in the group. This can't be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(task.id)
                    taskToDelete = null
                    // If the confirm came from inside the detail sheet, that
                    // sheet is now showing a task that no longer exists.
                    detailTaskId = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { taskToDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmLeave) {
        QuietAlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave household?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You'll lose access to its board.")
            },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; viewModel.leaveGroup() }) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Cancel") }
            },
        )
    }

    if (showBookIt) {
        BookItSheet(
            bookings = state.bookings,
            myUserId = myUserId,
            onDismiss = { showBookIt = false },
            onBook = { what, note, start, end, onResult ->
                viewModel.createBooking(what, note, start, end, onResult)
            },
        )
    }

    cancellingBooking?.let { booking ->
        CancelBookingDialog(
            booking = booking,
            onDismiss = { cancellingBooking = null },
            onConfirm = {
                cancellingBooking = null
                viewModel.cancelBooking(booking.id)
            },
        )
    }

    // It takes other people's record with it, and there is no undelete, so the
    // confirm says exactly that. It uses the same red button as deleting a chore.
    if (confirmRemove) {
        val others = (state.members.size - 1).coerceAtLeast(0)
        QuietAlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${currentGroup?.name.orEmpty()}?") },
            text = {
                Text(
                    when (others) {
                        0 -> "Its board, chores and history will be deleted. This can't be undone."
                        1 -> "You and 1 housemate will lose its board, chores and history. " +
                            "This can't be undone."
                        else -> "You and $others housemates will lose its board, chores and " +
                            "history. This can't be undone."
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmRemove = false; viewModel.removeHousehold() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Remove household") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Keep it") }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Header / states
// ═══════════════════════════════════════════════════════════════

// There was a ProgressHeader here: "5 of 8 done", a percentage, and a filling
// bar above the tabs. It was v1 furniture that outlived the pivot.
//
// Constraint 4 forbids "points, streaks, leaderboards, or gamification", and
// the spec's note under history is flatter still: "No points, ranks, streaks,
// percentages, or comparisons drawn by the app. The data is presented;
// conclusions are the household's business." A completion percentage is a
// conclusion, and because it counted the whole group's rows it was a verdict on
// the household's week — the shame signal constraints 3 and 4 exist to keep
// out. It was also wrong: it counted `tasks` only and never saw an occurrence,
// so it reported a number for a fraction of the board.
//
// GroupWithProgress still carries total_tasks/done_tasks/progress and the API
// still returns them. Nothing reads them now, and that is fine — the endpoint
// is shared and the fields cost nothing.

@Composable
internal fun LoadingBlock(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun GroupErrorBlock(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Couldn't load this group",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
internal fun EmptyBlock(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.let { Spacer(Modifier.height(24.dp)); it() }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Tasks
// ═══════════════════════════════════════════════════════════════

/**
 * One row on the board, from either of the two shapes it can hold.
 *
 * F2 introduces chore occurrences, but tasks predate them and are already the
 * spec's "one-off" type, so both live on the board together rather than one
 * being hidden or migrated away. The board's question — "what still needs
 * doing and whose is it" — is the same for both, so the sections and the row
 * are shared and only the source differs.
 */
internal sealed interface BoardRow {
    val key: String
    val title: String
    val assignedTo: String?
    val dueDate: OffsetDateTime?
    val isOpen: Boolean
    val doneBy: String?
    val doneAt: OffsetDateTime?

    data class TaskRow(val task: Task) : BoardRow {
        override val key get() = "task-${task.id}"
        override val title get() = task.title
        override val assignedTo get() = task.assignedTo
        override val dueDate get() = task.dueDate
        override val isOpen get() = task.isOpen
        override val doneBy get() = task.doneBy
        override val doneAt get() = task.doneAt
    }

    data class OccurrenceRow(val occurrence: Occurrence) : BoardRow {
        override val key get() = "occ-${occurrence.id}"
        override val title get() = occurrence.choreName
        // Never null: every occurrence has exactly one name on it.
        override val assignedTo get() = occurrence.assignedTo
        override val dueDate get() = occurrence.dueDate
        override val isOpen get() = occurrence.isOpen
        override val doneBy get() = occurrence.doneBy
        override val doneAt get() = occurrence.doneAt
    }
}

/**
 * "You're away" — shown on your own board, and only while you are.
 *
 * The spec makes away deliberately impossible to hide, but until now the only
 * places it showed were the member list and the rotation pickers — that is,
 * everywhere *except* the screen the away person is looking at. So your own
 * absence was the one thing you could not see.
 *
 * Deliberately quiet: a surface tint, no icon, no colour that reads as a
 * warning. Being away is not a failure state, and this is a reminder of a
 * setting rather than a nag about one.
 *
 * "I'm back" is here because this is where you notice you are still marked
 * away. Going away keeps its dialog in the overflow menu — that direction has
 * a rule worth reading first; coming back has nothing to explain.
 */
@Composable
private fun AwayBanner(onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "You're away. Chores are passing over you",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("I'm back") }
        }
    }
}

/**
 * The Board — F1, now reading the chore model as well.
 *
 * Three sections, in this order: **Yours**, **Others**, **Done**. Grouping by
 * ownership rather than by status is the whole point: the question the board
 * answers is "what still needs doing and whose is it", so the first thing you
 * should see is your own name's worth of work.
 *
 * Unassigned tasks sit under Others. The chore model has no such thing —
 * "everything on the board always has exactly one name on it" — but tasks
 * created before that rule exists still can, and hiding them would lose them.
 *
 * Everyone else and Recently done fold under their heading; see [BoardFolds].
 * A section holding the chore you just added stays open whatever you chose,
 * or the highlight would land on nothing.
 */
@Composable
private fun TaskList(
    groupId: String,
    rows: List<BoardRow>,
    chores: List<Chore>,
    justAdded: String?,
    members: List<MemberInfo>,
    myUserId: String?,
    showSwipeHint: Boolean,
    onOpenDetail: (BoardRow) -> Unit,
    onToggleDone: (BoardRow) -> Unit,
    onPass: (BoardRow) -> Unit,
    onSwiped: () -> Unit,
    onCreate: () -> Unit,
    header: @Composable (Modifier) -> Unit,
) {
    if (rows.isEmpty()) {
        // A household with no chores yet can still want the kitchen on Saturday.
        Column(modifier = Modifier.fillMaxSize()) {
            header(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
            EmptyBlock(
                title = "Nothing on the board",
                subtitle = "Add the first chore and it'll show up for everyone in the household.",
            ) {
                FilledTonalButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New chore")
                }
            }
        }
        return
    }

    val sections = remember(rows, myUserId) {
        val open = rows.filter { it.isOpen }
        listOf(
            BoardSection("Yours", open.filter { it.assignedTo != null && it.assignedTo == myUserId }),
            BoardSection("Everyone else", open.filter { it.assignedTo == null || it.assignedTo != myUserId }, fold = "others"),
            BoardSection("Recently done", recentlyDone(rows, OffsetDateTime.now()), fold = "done"),
        ).filter { it.rows.isNotEmpty() }
    }
    val context = LocalContext.current
    var chosen by remember(groupId) {
        mutableStateOf(listOf("others", "done").associateWith { BoardFolds.chosen(context, groupId, it) })
    }

    LazyColumn(
        // Clears the floating dock so the last row is reachable.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = DOCK_CLEARANCE),
    ) {
        item(key = "book-it") { header(Modifier) }
        sections.forEach { (label, rows, fold) ->
            val holdsNew = justAdded != null && rows.any { it.title.equals(justAdded, ignoreCase = true) }
            val folded = fold != null && !holdsNew && BoardFolds.isFolded(chosen[fold], rows.size)
            item(key = "header-$label") {
                if (fold == null) {
                    SectionLabel(label, Modifier.padding(top = 14.dp, bottom = 8.dp))
                } else {
                    FoldLabel(
                        text = label,
                        count = rows.size,
                        folded = folded,
                        onToggle = {
                            BoardFolds.choose(context, groupId, fold, !folded)
                            chosen = chosen + (fold to !folded)
                        },
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            if (folded) return@forEach
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                TaskCard(
                    row = row,
                    first = index == 0,
                    last = index == rows.lastIndex,
                    scheduleText = scheduleLineFor(row, chores),
                    highlighted = justAdded != null && row.title.equals(justAdded, ignoreCase = true),
                    assigneeName = members.firstOrNull { it.id == row.assignedTo }?.username,
                    doneByName = members.firstOrNull { it.id == row.doneBy }?.username,
                    coveredByName = (row as? BoardRow.OccurrenceRow)?.occurrence?.coveredByName,
                    needsDate = (row as? BoardRow.OccurrenceRow)?.occurrence?.needsDate == true,
                    // Whether there is anybody to pass to at all. A rotation of
                    // one, or a household where everyone else is away, has
                    // nowhere to send it, and offering the gesture there would
                    // promise a hand-off that came straight back.
                    canPass = (row as? BoardRow.OccurrenceRow)?.let { occRow ->
                        passReceiver(occRow.occurrence, members) != null
                    } == true,
                    passedByMe = (row as? BoardRow.OccurrenceRow)
                        ?.occurrence?.passedFrom == myUserId && myUserId != null,
                    myUserId = myUserId,
                    onOpenDetail = { onOpenDetail(row) },
                    onToggleDone = { onToggleDone(row) },
                    onPass = { onPass(row) },
                    onSwiped = onSwiped,
                )
                // The hint sits under the first row of the first section, which
                // is a row you can actually swipe. Under a heading it would be
                // advice about nothing in particular.
                if (showSwipeHint && label == "Yours" && index == rows.lastIndex) {
                    Text(
                        text = "Swipe a row for actions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
            }
        }
    }
}

/** One heading on the board and its rows. [fold] names it in [BoardFolds]; null cannot fold. */
private data class BoardSection(val label: String, val rows: List<BoardRow>, val fold: String? = null)

/**
 * What the board's last section shows: completions from the past
 * [RECENTLY_DONE] days, newest first.
 *
 * It was every completion the household had ever made, oldest first — a
 * section that only grew, with today's at the bottom under months of others.
 * Everything older is still in each chore's History and in Activity. A done
 * row with no time on it (legacy tasks from before done_at existed) has no
 * claim to "recent" and is left out.
 *
 * `now` is a parameter so this can be tested; the board reads the clock when
 * its rows change, which is as fresh as a list of past events needs to be.
 */
internal fun recentlyDone(rows: List<BoardRow>, now: OffsetDateTime): List<BoardRow> {
    val since = now.minus(RECENTLY_DONE)
    return rows
        .filter { !it.isOpen && it.doneAt?.isAfter(since) == true }
        .sortedByDescending { it.doneAt }
}

internal val RECENTLY_DONE: Duration = Duration.ofDays(7)

/**
 * How long after marking something done you can still take it back. Spec F1:
 * "Undo within 10 minutes by the person who marked it."
 */
internal val UNDO_WINDOW: Duration = Duration.ofMinutes(10)

/**
 * How long after a turn is done somebody else may add "I worked on this too".
 * Mirrors `alsoWindow` in the backend's also.go, which is what actually decides.
 */
internal val ALSO_WINDOW: Duration = Duration.ofHours(48)

/**
 * Line 2 of a row: how often this comes round, in the household's words.
 *
 * "every 4 days", "Tuesdays", "as needed" - the same forms the chore's own
 * schedule wording produces, so the row and the sheet cannot drift apart. A
 * needed-by time rides along, because it is what the second reminder is
 * measured against.
 *
 * A one-off has no frequency to state, so it says what it is and when:
 * "one-off, Fri 11 Sep", or just "one-off" when it has no date. Returns null
 * only while an occurrence's chore has yet to arrive, which is a moment rather
 * than a state.
 */
internal fun scheduleLineFor(row: BoardRow, chores: List<Chore>): String? = when (row) {
    is BoardRow.OccurrenceRow ->
        chores.firstOrNull { it.id == row.occurrence.choreId }?.let { chore ->
            buildString {
                append(chore.scheduleWording())
                chore.neededByWording()?.let { append(" · $it") }
            }
        }

    is BoardRow.TaskRow -> buildString {
        append("one-off")
        row.dueDate?.let { append(" · ${formatDueDate(it)}") }
    }
}

/**
 * Line 3 of a row: whose turn this is, and when it is wanted.
 *
 * Your own rows do not repeat your name back at you - "Due 30 Aug" rather than
 * "you, due 30 Aug". A row with no date says whose turn it is instead of
 * leaving the line empty, which is exactly what an as-needed chore needs: it is
 * somebody's turn indefinitely, and that is the whole of its status.
 *
 * [dateAlreadyShown] suppresses the date when the schedule line above has
 * already given it, which is the one-off case.
 */
private fun cycleLineFor(
    row: BoardRow,
    assigneeName: String?,
    doneByName: String?,
    myUserId: String?,
    dateAlreadyShown: Boolean,
): String {
    if (!row.isOpen) {
        val also = (row as? BoardRow.OccurrenceRow)?.occurrence?.alsoBy.joinedNames(myUserId)
        val doerName = if (row.doneBy != null && row.doneBy == myUserId) "you" else doneByName ?: "someone"
        val doer = doerName + (also?.let { ", also $it" } ?: "")
        val doneOn = row.doneAt?.let { formatDueDate(it) }
        return if (doneOn != null) "Done by $doer · $doneOn" else "Done by $doer"
    }

    val due = row.dueDate?.takeUnless { dateAlreadyShown }?.let { formatDueDate(it) }
    val mine = row.assignedTo != null && row.assignedTo == myUserId
    val who = assigneeName ?: "Unassigned"

    // A one-off has no rotation, so it has no *turns*: it was given to one
    // person once and stays with them. Saying "your turn" about it borrows a
    // word that means something precise everywhere else on this board, and
    // means nothing here. The name alone does the job, and for your own rows
    // the section heading has already said it.
    val oneOff = row is BoardRow.TaskRow

    return when {
        mine && due != null -> "Due $due"
        mine -> if (oneOff) "" else "Your turn"
        due != null -> "$who · due $due"
        // Somebody else's standing turn. Possessive rather than "Maya - turn",
        // which reads as a label instead of a sentence.
        assigneeName != null -> if (oneOff) who else "$who's turn"
        else -> who
    }
}

/**
 * One row on the board.
 *
 * The checkbox marks done in a single tap, per F1 — and **anyone** may tick
 * **anything**, because "I just did it myself" is the common reality and the
 * board records it rather than fighting it. The assignee's name stays on the
 * row; who actually did it is kept separately.
 *
 * Tapping the row opens the detail sheet. Past-due-and-open gets a small amber
 * dot and the date as it is — never red, never a day count, never a badge
 * (hard constraint 3). The date is information enough.
 */
@Composable
internal fun TaskCard(
    row: BoardRow,
    /** Where it sits in its group, so the group draws as one rounded card. */
    first: Boolean = true,
    last: Boolean = true,
    scheduleText: String?,
    highlighted: Boolean,
    assigneeName: String?,
    doneByName: String?,
    coveredByName: String?,
    needsDate: Boolean,
    passedByMe: Boolean,
    canPass: Boolean,
    myUserId: String?,
    /**
     * Which household this row belongs to, shown as a small muted chip beside
     * the name. Null on the board, where every row is this household's and the
     * chip would be noise; set on My week, where the rows are ordered by date
     * and come from more than one place (DESIGN.md §3, agenda row).
     */
    householdName: String? = null,
    onOpenDetail: () -> Unit,
    onToggleDone: () -> Unit,
    onPass: () -> Unit,
    onSwiped: () -> Unit,
) {
    val overdue = isOverdue(row)

    // The row no longer completes anything, so it no longer undoes anything
    // either: both live in the detail sheet, next to the done-line they are
    // decisions about. What used to be here — a checkbox, a ten-minute window
    // and a coroutine per row to expire it (B-6) — went with them.

    // Swipe-right is the one way to complete a turn without opening it, so it
    // is the one place left that asks. The confirm quotes the done-line,
    // because a gesture that fires from a list has shown you nothing.
    //
    // The row itself is now a button and nothing else: tapping it anywhere
    // opens the detail sheet, and the sheet owns both done and undo.
    var confirming by remember(row.key, row.isOpen) { mutableStateOf(false) }
    val requestDone = { if (row.isOpen) confirming = true else onToggleDone() }

    // An occurrence carries the household's agreement. A legacy one-off carries
    // only its own description, so it is labelled as one rather than promoted
    // into an agreement nobody made.
    val doneLine = when (row) {
        is BoardRow.OccurrenceRow -> row.occurrence.doneLine
        is BoardRow.TaskRow -> row.task.description
    }
    val doneLineLabel = when (row) {
        is BoardRow.OccurrenceRow -> "What done means"
        is BoardRow.TaskRow -> "What this is"
    }

    // A one-off's date *is* its schedule, so it is stated once, on the schedule
    // line, and the cycle line below carries only the name. Everything else puts
    // its date on the cycle line, where it belongs to this turn rather than to
    // the arrangement.
    val dateIsOnScheduleLine = row is BoardRow.TaskRow && row.dueDate != null
    val cycleText = cycleLineFor(row, assigneeName, doneByName, myUserId, dateIsOnScheduleLine)

    // Swipe is offered on your own open chore rows and nowhere else.
    //
    // Not on somebody else's row: the two actions behind it are "I did this"
    // and "I can't do this", and neither is yours to say about their turn. Not
    // on a one-off either, which has no rotation to pass along. The detail
    // sheet keeps its own button, so the gesture is a shortcut rather than the
    // only way through — some people will never find it, and that is a finding
    // rather than a dead end.
    val swipeable = row is BoardRow.OccurrenceRow && row.isOpen &&
        myUserId != null && row.assignedTo == myUserId && canPass

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> onPass()
                SwipeToDismissBoxValue.StartToEnd -> requestDone()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            if (value != SwipeToDismissBoxValue.Settled) onSwiped()
            // Never actually dismiss: the row is revealing an action, not
            // leaving the board, so it snaps back once the action has fired.
            false
        },
    )

    val mine = row.assignedTo != null && row.assignedTo == myUserId
    val corner = 22.dp
    val shape = RoundedCornerShape(
        topStart = if (first) corner else 0.dp,
        topEnd = if (first) corner else 0.dp,
        bottomStart = if (last) corner else 0.dp,
        bottomEnd = if (last) corner else 0.dp,
    )
    val schedule = scheduleText?.replaceFirstChar { it.uppercase() }
    val due = row.dueDate?.let { formatDueDate(it) }
    val who = assigneeName ?: "Unassigned"
    val meta = when {
        !row.isOpen -> cycleText
        mine && overdue && due != null -> "Past its day · was $due"
        mine -> listOfNotNull(schedule.takeIf { row is BoardRow.OccurrenceRow }, due ?: "Your turn".takeIf { schedule == null })
            .joinToString(" · ").ifEmpty { schedule.orEmpty() }
        row is BoardRow.TaskRow -> listOfNotNull(who, due?.let { "once, by $it" } ?: "once").joinToString(" · ")
        else -> listOfNotNull("$who's turn", due ?: schedule).joinToString(" · ")
    }

    val card: @Composable () -> Unit = {
    Surface(
        onClick = onOpenDetail,
        shape = shape,
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            if (!first) RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when {
                    !row.isOpen -> Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Quiet.colors.doneSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = doneColor(), modifier = Modifier.size(16.dp))
                    }
                    mine || row.assignedTo == null -> Box(
                        modifier = Modifier
                            .padding(horizontal = 9.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (overdue) Quiet.colors.pastDot else MaterialTheme.colorScheme.outline),
                    )
                    else -> Avatar(who, row.assignedTo!!, 34.dp)
                }
                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = row.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (row.isOpen) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        householdName?.let { household ->
                            Spacer(Modifier.width(8.dp))
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(999.dp)) {
                                Text(
                                    household,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (mine && overdue && row.isOpen) overdueColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val returned = coveredByName != null && row.isOpen
                    val marker = when {
                        needsDate && mine -> "Everyone's busy, so it's back with you."
                        passedByMe -> "Comes back to you next time. ${assigneeName ?: "Someone"} is covering this one."
                        returned && mine -> "$coveredByName covered your last turn, so it's yours again"
                        returned -> assigneeName?.let { "$coveredByName covered $it's last turn" }
                            ?: "$coveredByName covered the last turn"
                        else -> null
                    }
                    marker?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                    }
                }

                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    }

    if (confirming) {
        MarkDoneSheet(
            title = row.title,
            doneLine = doneLine,
            doneLineLabel = doneLineLabel,
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                onToggleDone()
            },
        )
    }

    if (!swipeable) {
        card()
        return
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val toEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            // Tinted from our own tokens, not from `tertiaryContainer` — which
            // this theme never sets, so it falls through to Material's baseline
            // *dark red*. A red wash behind the word "Done" is the shame signal
            // constraint 3 rules out, and nobody had chosen it (CLAUDE.md §7).
            Surface(
                shape = shape,
                color = if (toEnd) doneColor().copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
                ) {
                    Text(
                        text = if (toEnd) "Done" else "Pass it",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (toEnd) doneColor()
                        else MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
        content = { card() },
    )
}

/**
 * Detail view for a single task, as a modal bottom sheet.
 *
 * This is where a task is actually read and edited: the full description
 * instead of two truncated lines, an explicit status control instead of a
 * hidden tap-to-cycle, assignment, and delete.
 *
 * Text edits are staged and saved with a button, because typing a title should
 * not fire a request per keystroke. Status and assignee apply immediately —
 * they're single, cheap, obviously-reversible choices.
 *
 * [task] is re-derived from the ViewModel's list by the caller, so the sheet
 * reflects a teammate's concurrent change rather than a stale snapshot taken
 * when it opened.
 */
/**
 * Detail for one chore occurrence — where F4's "what done means" is finally
 * read.
 *
 * The line is agreed once, at the chore, and shown on every occurrence of it.
 * That is the whole mechanism: it moves the standards argument (what counts as
 * "clean"? how often is often enough?) out of each individual turn and into a
 * single setup conversation the household has once. So the done-line and the
 * frequency sit together at the top, before anything about this particular
 * cycle — they describe the agreement; the rest describes today.
 *
 * Read-only. Editing a chore is open to every member and broadcasts a diff to
 * the group, which is a different affordance in a different place — this sheet
 * is for the person about to do the chore, wondering what "done" means.
 *
 * Deliberately absent: anything about lateness. A past-due occurrence shows its
 * date exactly as any other does. There is no "overdue" line, no day count and
 * no colour beyond the same amber the board uses — constraint 3, and the detail
 * view is precisely where a shame badge would feel most justified and do most
 * damage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OccurrenceDetailSheet(
    occurrence: Occurrence,
    chore: Chore?,
    members: List<MemberInfo>,
    myUserId: String?,
    /**
     * Which household this turn belongs to, as a chip beside the schedule line.
     * Null drops the chip — on the board every row is this household's and the
     * app bar already says which, so it would be noise.
     */
    householdName: String? = null,
    /**
     * Whether *this* person may still take the completion back: they are the
     * one who marked it, and the ten-minute window has not closed. Computed by
     * the caller, which owns the clock — reading the time during composition
     * freezes the answer at whatever it was when the sheet was drawn (B-6).
     */
    canUndo: Boolean = false,
    /**
     * Whether this person may add "I worked on this too": the turn is done,
     * somebody else did it, they are not already on it, and it is recent.
     * Computed by the caller for the same reason as [canUndo].
     */
    canAddAlso: Boolean = false,
    /** Whether this person may take their own "also" back — ten minutes, like undo. */
    canUndoAlso: Boolean = false,
    onDismiss: () -> Unit,
    onEditChore: () -> Unit,
    onShowHistory: () -> Unit,
    onMarkDone: () -> Unit,
    onUndoDone: () -> Unit,
    onAddAlso: () -> Unit = {},
    onUndoAlso: () -> Unit = {},
    onPass: () -> Unit,
    onPickDay: (String) -> Unit,
) {
    var pickingDay by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val assignee = members.firstOrNull { it.id == occurrence.assignedTo }?.username
    val doer = members.firstOrNull { it.id == occurrence.doneBy }?.username
    val passer = occurrence.passedFrom?.let { id -> members.firstOrNull { it.id == id }?.username }
    val mine = myUserId != null && occurrence.assignedTo == myUserId
    val overdue = occurrence.isOpen && occurrence.dueDate?.isBefore(OffsetDateTime.now()) == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // ── Header: what this is, when it is wanted, and where ──
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = occurrence.choreName,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = buildString {
                                chore?.let { append(it.scheduleWording()) }
                                occurrence.dueDate?.let {
                                    if (isNotEmpty()) append(" · ")
                                    append("Due ${formatDueDate(it)}")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            // The amber follows the date here exactly as it does
                            // on the row: never red, never a day count.
                            color = if (overdue) overdueColor()
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        householdName?.let { name ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                // The sheet already closes by drag or scrim. The X is here
                // because a sheet this tall can be read to the bottom with the
                // handle off screen, and then there is nothing to drag.
                FilledTonalIconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // ── Why this turn is here, when that needs saying ──
            //
            // The debt rule, the pass and the everybody-busy return are the
            // three things about this app that look like bugs unless somebody
            // says them out loud. One callout, tinted from `primary` at low
            // alpha — not `primaryContainer`, which is Material's baseline here
            // (CLAUDE.md §7).
            val stillAway = occurrence.assignedTo != occurrence.passedFrom
            val callout = when {
                occurrence.needsDate && mine ->
                    "Everyone was busy, so it's back with you. It's set for the latest " +
                        "the schedule allows, and you can bring it forward."

                occurrence.isOpen && occurrence.coveredByName != null && mine ->
                    "${occurrence.coveredByName} covered your last turn, so it's yours again."

                occurrence.isOpen && passer != null && stillAway ->
                    if (occurrence.passedFrom == myUserId) {
                        "You passed this on. It comes back to you next time."
                    } else {
                        "$passer passed this on, so it's yours this cycle."
                    }

                else -> null
            }
            callout?.let { line ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(text = line, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // ── This cycle, as a quiet list ──
            if (!occurrence.isOpen) {
                val q = Quiet.colors
                Surface(color = q.doneSoft, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            occurrence.doneBy?.let { DoneAvatar(doer ?: "?", it, 44.dp) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    if (occurrence.doneBy == myUserId) "You did this" else "${doer ?: "Someone"} did this",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(formatStamp(occurrence.doneAt), style = MaterialTheme.typography.bodySmall, color = q.onDoneSoft)
                            }
                        }
                        occurrence.alsoBy.joinedNames(myUserId)?.let { also ->
                            Text("Also worked on it: $also", style = MaterialTheme.typography.bodyMedium, color = q.onDoneSoft)
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    SheetInfoRow(
                        label = "Whose turn",
                        value = if (mine) "You" else assignee ?: "Nobody",
                        emphasised = mine,
                        dot = mine,
                    )
                    chore?.let {
                        SheetInfoDivider()
                        SheetInfoRow("How often", it.scheduleWording())
                    }
                    // The order turns go round in, as the chore defines it. Not
                    // "who is next": that depends on covers and passes the
                    // client is deliberately not told, and a prediction that is
                    // sometimes wrong is worse than none. The snackbar after a
                    // completion names the real next person instead.
                    SheetInfoDivider()
                    SheetInfoRow(
                        label = "Due",
                        // As-needed chores genuinely have no date. Saying so
                        // beats an empty field, which reads like missing data.
                        value = occurrence.dueDate?.let { formatDueDate(it) }
                            ?: "No date. It waits until it's needed",
                        valueColor = if (overdue) overdueColor() else null,
                    )
                    chore?.neededByTime?.let { time ->
                        SheetInfoDivider()
                        SheetInfoRow("Needed by", "Before $time")
                    }
                }
            }

            // The order turns go round in, as faces, the current holder ringed.
            // An order, not a forecast: who is next after this depends on covers
            // and passes the phone is deliberately not told.
            chore?.rotation?.takeIf { it.size > 1 }?.let { rotation ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CapsLabel("Takes turns")
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rotation.forEachIndexed { i, id ->
                            val member = members.firstOrNull { it.id == id }
                            val name = if (id == myUserId) "You" else member?.username ?: "someone"
                            val current = occurrence.isOpen && id == occurrence.assignedTo
                            if (i > 0) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 13.dp).size(18.dp),
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = if (current) Modifier
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(3.dp) else Modifier.padding(3.dp),
                                ) {
                                    Avatar(member?.username ?: name, id, 38.dp, dimmed = member?.away == true)
                                }
                                Text(
                                    name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                                    color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Who the turn actually belonged to, when somebody else did it.
            // Stated plainly, without praise or blame either way: it is simply
            // how the next turn was decided.
            if (!occurrence.isOpen) {
                val owed = passer ?: assignee
                if (doer != null && owed != null && doer != owed) {
                    Text(
                        "$doer covered this one, so the next turn goes back to $owed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── The two quiet routes out of the sheet ──
            //
            // Any member may change the agreement — that is the point of it
            // being an agreement — but reading this sheet is the common case
            // and editing the household's standards is not, so neither of these
            // competes with the action below.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton(onClick = onShowHistory) {
                    Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("History")
                }
                chore?.let {
                    TextButton(
                        onClick = onEditChore,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit chore")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── What done means (F4) ──
            //
            // Directly on top of the button that applies it, as a quote: the
            // household's own words, the last thing read before "I did this".
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val doneLine = occurrence.doneLine.trim()
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CapsLabel("What done means")
                        Text(
                            text = if (doneLine.isNotEmpty()) "“$doneLine”" else doneLine.ifEmpty {
                                // Not a defect to fix before you can tick it: a
                                // household that never wrote one down is not
                                // doing it wrong.
                                "No agreed definition of done yet. It's your call on " +
                                    "whether it's finished."
                            },
                            style = if (doneLine.isEmpty()) MaterialTheme.typography.bodyLarge
                            else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 27.sp),
                            color = if (doneLine.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // ── The action ──
            //
            // This is where a turn is completed, and since the board's row lost
            // its checkbox it is the only place. The board can only *open* a
            // chore now; saying it is done is a decision taken with the
            // household's definition of done on the same screen.
            //
            // Anyone may tick anything (F1) — "I just did it myself" is the
            // common reality — so the button is offered on somebody else's row
            // too. It only changes what it calls itself, because "I did this"
            // on a turn that is not yours is a sentence about the wrong person.
            if (occurrence.isOpen) {
                Button(
                    onClick = onMarkDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (mine) "I did this" else "Mark as done",
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            } else if (canUndo) {
                // The undo that used to live on the row's checkbox. Only for
                // the person who marked it, and only for ten minutes; anything
                // older is history, and history is not editable from here.
                OutlinedButton(
                    onClick = onUndoDone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) { Text("Undo, I didn't do this") }
            }

            // ── "I worked on this too" ──
            //
            // For whoever finished it off, or redid half of it, after somebody
            // else marked it done — work that otherwise leaves no trace. It adds
            // a name and nothing else: no note, no reason, and the turn is
            // still the doer's. The line under it says so, because the obvious
            // worry is that this is a way to correct somebody (constraint 1).
            if (canAddAlso) {
                Text("Did you help finish it?", style = MaterialTheme.typography.titleSmall)
                Button(
                    onClick = onAddAlso,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Quiet.colors.dock,
                        contentColor = Quiet.colors.onDock,
                    ),
                ) {
                    Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("I worked on this too")
                }
                Text(
                    doer?.let { "Adds your name beside $it's. It still counts as $it's turn." }
                        ?: "Adds your name. It still counts as their turn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (canUndoAlso) {
                OutlinedButton(
                    onClick = onUndoAlso,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) { Text("Undo, take my name off") }
            }

            if (occurrence.needsDate && mine) {
                OutlinedButton(
                    onClick = { pickingDay = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) { Text("Pick a day") }
            }

            // Passing is offered on your own open turn, but not once the round
            // has closed: there is nobody left to hand it to, and the button
            // would only earn a refusal.
            // Only when there is somebody to hand it to — the server says who
            // (pass_to), and says nobody in a household of one or when every
            // one else is away.
            if (occurrence.isOpen && mine && !occurrence.needsDate && passReceiver(occurrence, members) != null) {
                OutlinedButton(
                    onClick = onPass,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(passReceiver(occurrence, members)?.let { "Busy? Pass to ${it.username}" } ?: "Busy? Pass it on")
                }
                Text(
                    (passReceiver(occurrence, members)?.let { "Only ${it.username} is told." } ?: "Only they are told.") +
                        " It comes back to you next time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (pickingDay) {
        DueDatePickerDialog(
            initial = occurrence.dueDate,
            onDismiss = { pickingDay = false },
            onPicked = { picked ->
                pickingDay = false
                onPickDay(picked)
            },
        )
    }
}

/**
 * One line of the detail sheet's quiet list: label left, value right.
 *
 * [dot] is the small violet disc the mock puts beside "You" — a marker for
 * *your* turn, and the only place on the sheet where a colour means anything.
 * [valueColor] exists for the overdue amber and nothing else.
 */
@Composable
private fun SheetInfoRow(
    label: String,
    value: String,
    emphasised: Boolean = false,
    dot: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        // Half the row each, so a long value (a rotation of four names) wraps
        // on the right rather than squeezing the label down to one letter a
        // line. The dot rides inside the value's half, beside the name it
        // marks, instead of being left at the halfway line.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
                ),
                color = valueColor
                    ?: if (emphasised) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun SheetInfoDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TaskDetailSheet(
    task: Task,
    members: List<MemberInfo>,
    onDismiss: () -> Unit,
    onSaveText: (title: String?, description: String?) -> Unit,
    onSetStatus: (String) -> Unit,
    onSetAssignee: (String?) -> Unit,
    onSetDueDate: (String?) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        DueDatePickerDialog(
            initial = task.dueDate,
            onDismiss = { showDatePicker = false },
            onPicked = { iso ->
                showDatePicker = false
                onSetDueDate(iso)
            },
        )
    }

    // Re-seed the draft when a different task is opened, or when this one is
    // changed underneath us by someone else.
    var draftTitle by remember(task.id, task.title) { mutableStateOf(task.title) }
    var draftDescription by remember(task.id, task.description) { mutableStateOf(task.description) }

    val titleChanged = draftTitle.trim() != task.title && draftTitle.isNotBlank()
    val descriptionChanged = draftDescription.trim() != task.description
    val dirty = titleChanged || descriptionChanged

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = draftTitle,
                onValueChange = { draftTitle = it },
                label = { Text("Title") },
                isError = draftTitle.isBlank(),
                supportingText = if (draftTitle.isBlank()) {
                    { Text("Title can't be empty") }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draftDescription,
                onValueChange = { draftDescription = it },
                label = { Text("Description") },
                placeholder = { Text("Add more detail…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(visible = dirty) {
                Button(
                    onClick = {
                        onSaveText(
                            draftTitle.trim().takeIf { titleChanged },
                            draftDescription.trim().takeIf { descriptionChanged },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save changes") }
            }

            HorizontalDivider()

            Text("Status", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TASK_STATUSES.forEach { status ->
                    FilterChip(
                        selected = task.status == status,
                        onClick = { if (task.status != status) onSetStatus(status) },
                        label = { Text(statusLabel(status)) },
                    )
                }
            }

            Text("Assigned to", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // No "Nobody" chip (B-12). A task cannot be created unowned —
                // constraint 8: busy and away are the only ways out of a turn —
                // so it should not be made unowned a moment later either. A
                // legacy task that already has nobody simply has no chip
                // selected until somebody takes it.
                members.forEach { member ->
                    FilterChip(
                        selected = task.assignedTo == member.id,
                        onClick = { if (task.assignedTo != member.id) onSetAssignee(member.id) },
                        label = { Text(member.username) },
                    )
                }
            }

            HorizontalDivider()

            // ─── Deadline ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Deadline", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.dueDate?.let { formatDueDate(it) } ?: "No deadline",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            task.dueDate == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            isOverdue(task) -> overdueColor()
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                Row {
                    if (task.dueDate != null) {
                        IconButton(onClick = { onSetDueDate(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "Remove deadline")
                        }
                    }
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = if (task.dueDate == null) "Set deadline"
                            else "Change deadline",
                        )
                    }
                }
            }
            HorizontalDivider()

            // updated_at is null for rows written before that column existed,
            // hence the fallback rather than an empty line.
            Text(
                text = buildString {
                    append("Created ${formatStamp(task.createdAt)}")
                    task.updatedAt?.let { append("  ·  updated ${formatStamp(it)}") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete task")
            }
        }
    }
}

/**
 * Amber for past its day, in both themes — from the theme's Quiet palette.
 *
 * Hard constraint 3: "No red overdue states, late counters, or shame badges —
 * dim/amber at most." `colorScheme.error` is deliberately not used anywhere a
 * date has passed: red reads as failure, and lateness here is not a failure.
 */
@Composable
internal fun overdueColor(): androidx.compose.ui.graphics.Color = Quiet.colors.pastText

/**
 * Sage for done — a thing that happened, not a reward and not a verdict.
 */
@Composable
internal fun doneColor(): androidx.compose.ui.graphics.Color = Quiet.colors.done

// StatusChip lived here. It rendered a task's status as a coloured pill on the
// board row, which is the "status pill" v2 does away with: a row is open or it
// is done, and the checkbox already says which.

private fun statusLabel(status: String) = when (status) {
    "todo" -> "Open"
    // Kept for the activity feed only. A row written before v2 can carry this,
    // and the feed is a record of what happened rather than of what the app
    // would say today.
    "in_progress" -> "In progress"
    "done" -> "Done"
    else -> status
}

// ═══════════════════════════════════════════════════════════════
// Activity feed
// ═══════════════════════════════════════════════════════════════

@Composable
internal fun ActivityList(events: List<ActivityEvent>, members: List<MemberInfo>, myUserId: String?) {
    if (events.isEmpty()) {
        EmptyBlock(
            title = "Nothing has happened yet",
            subtitle = "Chores done, changes and new people show up here as they happen.",
        )
        return
    }

    // Grouped by the day it happened, in this phone's zone, newest first.
    val today = java.time.LocalDate.now()
    val groups = remember(events) {
        events.groupBy { e ->
            val day = e.createdAt?.atZoneSameInstant(java.time.ZoneId.systemDefault())?.toLocalDate()
            when {
                day == null -> "Earlier"
                day == today -> "Today"
                day == today.minusDays(1) -> "Yesterday"
                day.isAfter(today.minusDays(7)) -> "Earlier this week"
                else -> "Before that"
            }
        }.toList()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = DOCK_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (label, dayEvents) ->
            item(key = "h-$label") { SectionLabel(label, Modifier.padding(top = 6.dp)) }
            item(key = "g-$label") {
                GroupedCard {
                    dayEvents.forEachIndexed { i, event ->
                        if (i > 0) RowDivider()
                        val name = if (event.actorId == myUserId) "You" else event.actorUsername
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (event.eventType == "occurrence_done") {
                                DoneAvatar(event.actorUsername, event.actorId)
                            } else {
                                Avatar(event.actorUsername, event.actorId)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    describeEvent(event).replaceFirst(event.actorUsername, name),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                humaniseDetail(event)?.let { detail ->
                                    Text(
                                        detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                relativeTime(event.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp, start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Event types come from handlers/activity.go — **keep these in sync**.
 *
 * A missing case used to fall through to "maya: occurrence_done", which is how
 * every event F2 to F6 added read for the whole of v2: the v1 half of the feed
 * spoke English and the chore half printed its own constants. The fallback no
 * longer leaks the identifier, so if you add an event to the backend and forget
 * it here, the feed stays readable — but it also stays vague, and the fix is to
 * add the case rather than to leave it generic.
 */
private fun describeEvent(e: ActivityEvent): String = when (e.eventType) {
    "task_created" -> "${e.actorUsername} added a task"
    "task_updated" -> "${e.actorUsername} updated a task"
    "task_deleted" -> "${e.actorUsername} deleted a task"
    "tasks_bulk_updated" -> "${e.actorUsername} moved several tasks"

    // The chore model (F2–F6). "did" rather than "completed" — the household
    // says the former, and the detail line underneath names the chore.
    "chore_created" -> "${e.actorUsername} added a chore"
    "chore_updated" -> "${e.actorUsername} changed a chore"
    "chore_deleted" -> "${e.actorUsername} deleted a chore"
    "occurrence_done" -> "${e.actorUsername} did a chore"
    "occurrence_reopened" -> "${e.actorUsername} undid a completion"
    "occurrence_also" -> "${e.actorUsername} also worked on a chore"
    "occurrence_also_withdrawn" -> "${e.actorUsername} took their name off a chore"

    // The detail line underneath names what was booked.
    "booking_added" -> "${e.actorUsername} booked something"
    "booking_cancelled" -> "${e.actorUsername} cancelled a booking"

    "member_joined" -> "${e.actorUsername} joined the group"
    "member_left" -> "${e.actorUsername} left the group"
    "invite_accepted" -> "${e.actorUsername} accepted an invite"
    "member_role_changed" -> "${e.actorUsername} changed a member's role"
    else -> "${e.actorUsername} made a change"
}

/**
 * Turns the backend's `detail` string into something a person would say.
 *
 * The backend writes `detail` for machines as much as humans — `task_updated`
 * carries a comma-separated list of changed fields like
 * `status=done, assigned_to=cleared`, and the bulk event carries
 * `2 task(s) → done`. Rendering that raw made the feed read like a debug log.
 *
 * For created/deleted events `detail` is just the task title, which already
 * reads fine and is passed through untouched.
 *
 * Returns null when there is nothing worth showing on a second line.
 */
private fun humaniseDetail(e: ActivityEvent): String? {
    val detail = e.detail.trim()
    if (detail.isEmpty()) return null

    return when (e.eventType) {
        "task_created", "task_deleted" -> detail

        // "username → admin" from the backend; "admin" is the stored value for
        // what users are shown as "Manager".
        "member_role_changed" -> detail
            .replace(" → admin", " is now a manager")
            .replace(" → member", " is now a member")

        "task_updated" -> detail
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { field ->
                when {
                    field == "title" -> "renamed it"
                    field == "description" -> "edited the description"
                    field == "assigned_to" -> "changed who it's assigned to"
                    field == "assigned_to=cleared" -> "removed the assignee"
                    field == "due_date" -> "changed the deadline"
                    field == "due_date=cleared" -> "removed the deadline"
                    field.startsWith("status=") ->
                        "moved it to ${statusLabel(field.removePrefix("status="))}"
                    else -> null
                }
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.replaceFirstChar { it.uppercase() }

        "tasks_bulk_updated" -> {
            // "2 task(s) → done"
            val count = detail.substringBefore(" ").toIntOrNull()
            val status = detail.substringAfterLast("→").trim()
            if (count != null && status.isNotEmpty()) {
                "${if (count == 1) "1 task" else "$count tasks"} → ${statusLabel(status)}"
            } else {
                detail
            }
        }

        else -> detail
    }
}

@Composable
private fun eventColor(type: String) = when (type) {
    "task_created", "chore_created", "booking_added" -> MaterialTheme.colorScheme.primary
    // Completions get the same green a done row does. Not a reward — the feed
    // is a record, and this is the one entry that says something finished.
    "occurrence_done", "occurrence_also" -> MaterialTheme.colorScheme.tertiary
    "task_deleted", "chore_deleted", "member_left" -> MaterialTheme.colorScheme.error
    "member_joined", "invite_accepted" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}

// ═══════════════════════════════════════════════════════════════
// Members
// ═══════════════════════════════════════════════════════════════

/**
 * People: who lives here, bringing somebody in, and saying you are away.
 *
 * Each row is a face and a name, with an *Owner* or *Away* badge only where one
 * applies — never the email, never a "Member" label on everyone. No role
 * controls: no role grants anything, every member can edit every chore.
 */
@Composable
private fun MemberList(
    members: List<MemberInfo>,
    myUserId: String?,
    onShareCode: () -> Unit,
    onInviteByName: () -> Unit,
    onAway: () -> Unit,
    onLeave: () -> Unit,
    onRemove: () -> Unit,
    householdName: String,
) {
    val me = members.firstOrNull { it.id == myUserId }
    val q = Quiet.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GroupedCard {
            members.forEachIndexed { i, member ->
                if (i > 0) RowDivider()
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(member.username, member.id, 40.dp, dimmed = member.away)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (member.id == myUserId) "You" else member.username,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (member.away) {
                            Text(
                                member.awayUntil?.let { "Back ${formatDueDate(it)}" } ?: "Away until they say they're back",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when {
                        member.away -> Badge("Away", q.awaySoft, q.onAwaySoft)
                        member.role == ROLE_OWNER -> Badge(
                            "Owner",
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        // Bringing somebody in: the dark card, so it is the one thing on the
        // page that reads as an action.
        Surface(color = q.dock, contentColor = q.onDock, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Invite someone to $householdName", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Share a code that works for a few days, or invite someone who already has an account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = q.onDockMuted,
                )
                val shareCode: @Composable (Modifier) -> Unit = { modifier ->
                    Button(
                        onClick = onShareCode,
                        modifier = modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = q.onDock, contentColor = q.dock),
                    ) { Text("Share code") }
                }
                val byUsername: @Composable (Modifier) -> Unit = { modifier ->
                    OutlinedButton(
                        onClick = onInviteByName,
                        modifier = modifier.heightIn(min = 48.dp),
                        border = BorderStroke(1.5.dp, q.dockActive),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = q.onDock),
                    ) { Text("By username") }
                }
                // Half a card is not enough for these labels once the system
                // font is enlarged: split evenly they broke mid-word ("By
                // userna / me"). So each takes the full width then, and the
                // labels are free to wrap so neither can ever be clipped.
                if (LocalDensity.current.fontScale > 1.1f) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        shareCode(Modifier.fillMaxWidth())
                        byUsername(Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shareCode(Modifier.weight(1f))
                        byUsername(Modifier.weight(1f))
                    }
                }
            }
        }

        if (me != null) {
            GroupedCard {
                // The whole row is the switch, so a screen reader reads its
                // title as the switch's label rather than an unnamed toggle.
                Row(
                    modifier = Modifier
                        .toggleable(
                            value = me.away,
                            role = androidx.compose.ui.semantics.Role.Switch,
                            onValueChange = { onAway() },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            if (me.away) me.awayUntil?.let { "Away until ${formatDueDate(it)}" } ?: "I'm away"
                            else "I'm away",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "Your turns pass over you and come back when you are. Everyone can see it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = me.away, onCheckedChange = null)
                }
            }
        }

        // One or the other. An owner cannot leave a household with people in it
        // (the server refuses), so offering Leave there would only earn an error.
        val owner = me?.role == "owner"
        TextButton(
            onClick = if (owner) onRemove else onLeave,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (owner) "Remove $householdName" else "Leave $householdName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Badge(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Surface(color = bg, contentColor = fg, shape = RoundedCornerShape(999.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Someone else is away: a quiet slate note above the board. */
@Composable
private fun OthersAwayNote(away: List<MemberInfo>) {
    val q = Quiet.colors
    val text = if (away.size == 1) {
        val m = away.first()
        m.awayUntil?.let { "${m.username} is away until ${formatDueDate(it)}. Turns pass over them." }
            ?: "${m.username} is away. Turns pass over them."
    } else {
        away.joinToString(" and ") { it.username } + " are away. Turns pass over them."
    }
    Surface(
        color = q.awaySoft,
        contentColor = q.onAwaySoft,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Luggage, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * The away badge, shaped like [RoleChip]. Tinted from `secondary` — not amber,
 * which on this app means a date has passed, and being away is not lateness.
 * The label itself is `onSurfaceVariant`: `#60A5FA` as text at this size is
 * too faint on white to read.
 */
@Composable
private fun AwayChip() {
    Surface(
        color = MaterialTheme.colorScheme.secondary.copy(alpha = .18f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = "Away",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * Human label for a role.
 *
 * "Manager" is kept only for groups that still hold an `admin` row from before
 * the role was removed — nothing assigns it any more.
 */
private fun roleLabel(role: String): String = when (role) {
    ROLE_OWNER -> "Owner"
    ROLE_ADMIN -> "Manager"
    ROLE_MEMBER -> "Member"
    else -> role.replaceFirstChar { it.uppercase() }
}

/**
 * Role badge. Separate from [StatusChip] because roles and task statuses are
 * different vocabularies — sharing the chip meant "owner" rendered raw and
 * lowercase, styled as if it were a task state.
 */
@Composable
private fun RoleChip(role: String) {
    val color = when (role) {
        ROLE_OWNER -> MaterialTheme.colorScheme.primary
        ROLE_ADMIN -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = .15f), shape = MaterialTheme.shapes.small) {
        Text(
            text = roleLabel(role),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Dialogs
// ═══════════════════════════════════════════════════════════════

/**
 * One chore's history (F6) — who did each cycle, when it was due, when it was
 * done.
 *
 * Both dates are shown side by side and nothing is computed from them. A
 * completion three days after its date simply reads "done Tue 8 Sep · was due
 * Sat 5 Sep", and whether that matters is the reader's judgement. The moment
 * the app renders "3 days late" it has taken a position, which is what the spec
 * means by lateness being visible only as arithmetic.
 *
 * Absences are interleaved by date rather than listed separately, so a gap in
 * someone's completions is visibly a gap in their being there — otherwise a
 * quiet stretch reads as flaking, which is the misreading this view exists to
 * prevent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChoreHistorySheet(
    choreName: String,
    history: ChoreHistory?,
    loading: Boolean,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // One list, newest first, of two kinds of thing.
    val rows: List<Pair<OffsetDateTime, Any>> = remember(history) {
        if (history == null) emptyList()
        else (
            history.entries.map { it.doneAt to (it as Any) } +
                history.absences.map { it.startedAt to (it as Any) }
            ).sortedByDescending { it.first }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = choreName,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                "Every time this has been done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            when {
                loading -> Text("Loading…", style = MaterialTheme.typography.bodyMedium)

                rows.isEmpty() -> Text(
                    "Nothing done yet. It'll show up here once someone ticks it off.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> rows.forEach { (_, row) ->
                    when (row) {
                        is ChoreHistoryEntry -> Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = buildString {
                                    append("Done ")
                                    append(formatDueDate(row.doneAt))
                                    row.dueDate?.let { append(" · was due ${formatDueDate(it)}") }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = (if (row.doneBy == row.assignedTo) {
                                    "${row.doneByName}'s turn"
                                } else {
                                    // Named without comment. Who did it is the
                                    // fact; what it says about anyone is not.
                                    "${row.doneByName} did ${row.assigneeName}'s turn"
                                }) + (row.alsoBy.joinedNames()?.let { ", also $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        is Absence -> Text(
                            text = buildString {
                                append("${row.username} away from ${formatDueDate(row.startedAt)}")
                                row.finishedAt?.let { append(" to ${formatDueDate(it)}") }
                                    ?: append(", still away")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The per-person view (F6): completions over a window.
 *
 * A count each, in the order people joined, with everyone listed — including
 * anyone who has done nothing. Re-sorting by count, or hiding the zeroes, would
 * build the leaderboard the whole feature is designed around not being. There
 * are no totals, shares or averages for the same reason: the data is presented
 * and the conclusions belong to the household.
 *
 * Days away sit beside the count so a quiet spell has its explanation attached
 * rather than being left to speak for itself.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GroupHistoryBody(
    history: GroupHistory?,
    window: String,
    loading: Boolean,
    myUserId: String?,
    onWindowChange: (String) -> Unit,
) {
    // A count each, in the order people joined, everyone listed — zeroes too.
    // Re-sorting by count or hiding zeroes would make it a leaderboard, which
    // is the one thing this view must never be (constraint 4).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = DOCK_CLEARANCE),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Wraps rather than squeezes: in a Row the last chip got whatever width
        // was left, and at a large font that was a tall blank sliver.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("week" to "This week", "month" to "This month", "quarter" to "3 months")
                .forEach { (value, label) ->
                    val selected = window == value
                    Surface(
                        onClick = { if (!selected) onWindowChange(value) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                }
        }

        if (loading || history == null) {
            Text("Loading…", style = MaterialTheme.typography.bodyMedium)
        } else {
            GroupedCard {
                history.people.forEachIndexed { i, person ->
                    if (i > 0) RowDivider()
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(person.username, person.userId, 38.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (person.userId == myUserId) "You" else person.username,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            if (person.awayDays > 0) {
                                Text(
                                    if (person.awayDays == 1) "Away 1 day of this" else "Away ${person.awayDays} days of this",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            when (person.completed) {
                                0 -> "Nothing yet"
                                1 -> "1 done"
                                else -> "${person.completed} done"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
            Text(
                "Counted by who actually did it, so covering for someone counts for you. In the order people joined.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/**
 * Away (F5) — "I'm not at the house".
 *
 * The dialog states the rule it cannot enforce: away is for being physically
 * gone, not for a busy week. The app has no way to check, so the spec's answer
 * is to make the claim impossible to hide rather than to police it — hence the
 * line about everyone seeing it. Social enforcement, zero mechanics.
 *
 * It also names the alternative, because someone reaching for "away" when they
 * mean "not this week" needs somewhere else to go, and busy is right there on
 * the chore itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AwayDialog(
    onDismiss: () -> Unit,
    onConfirm: (until: String?) -> Unit,
) {
    // Open-ended by default: "I don't know when I'm back" is the honest common
    // case, and guessing a return date you then have to correct is worse.
    var days by remember { mutableStateOf<Int?>(null) }

    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Away from the house", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You'll be taken out of every rotation here until you're back, and you'll " +
                        "come back in the same place. No turns are owed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "This is for actually being away. If you're sleeping at home but this week " +
                        "is bad, use \"Busy? Pass it on\" on the chore instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text("Until", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterChip(
                        selected = days == null,
                        onClick = { days = null },
                        label = { Text("Until I say") },
                    )
                    listOf(3, 7, 14, 30).forEach { option ->
                        FilterChip(
                            selected = days == option,
                            onClick = { days = option },
                            label = { Text("$option days") },
                        )
                    }
                }

                HorizontalDivider()
                Text(
                    "Everyone in the household can see that you're away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val until = days?.let {
                    OffsetDateTime.now().plusDays(it.toLong())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                }
                onConfirm(until)
            }) { Text("I'm away") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// EditChoreDialog lived here too: a second, differently shaped form for the
// same facts the create dialog collected. Editing now reuses CreateChoreFlow
// prefilled, so a household changing "weekly" to "every 3 days" answers the
// question in the screen it first answered it in, and Delete sits at the foot
// of that flow rather than in a dialog of its own.

// CreateEntryDialog lived here: one AlertDialog with a "Does this repeat?"
// segmented control branching into two field sets. It is replaced by
// CreateChoreFlow, which asks how you know it is time rather than whether it
// repeats, and gives each answer a screen of its own instead of one dialog
// that scrolled past the fold on a 360dp phone.

@Composable
private fun InviteByUsernameDialog(onDismiss: () -> Unit, onInvite: (String) -> Unit) {
    var username by remember { mutableStateOf("") }

    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite someone") },
        text = {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = { onInvite(username) },
                enabled = username.isNotBlank(),
            ) { Text("Send invite") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Shows a generated invite code with copy and share actions.
 *
 * The code is 12 hex characters. Displaying it as plain text meant the user had
 * to read it off the screen and retype it into a chat app — error-prone, and
 * the whole point of the code is that it travels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteCodeDialog(code: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite code", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Anyone with this code can join the group. It expires in 48 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(onClick = {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (copied) "Copied" else "Copy")
                    }
                    FilledTonalButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join my TaskFlow group with this code: $code",
                            )
                        }
                        context.startActivity(Intent.createChooser(share, "Share invite code"))
                    }) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

// ═══════════════════════════════════════════════════════════════
// Helpers
// ═══════════════════════════════════════════════════════════════

/**
 * Date picker for a task deadline.
 *
 * A deadline is a *day*, not an instant — "due Friday" — so this picks a date
 * and pins it to the end of that day in the device's own zone before
 * converting to the RFC 3339 instant the API stores. Using start-of-day would
 * make a task due on Friday show as overdue for the whole of Friday.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DueDatePickerDialog(
    initial: OffsetDateTime?,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
    confirmLabel: String = "Set deadline",
) {
    // DatePicker works in UTC-midnight millis, so seed it from the deadline's
    // date as the user sees it locally, not from the raw instant.
    val initialMillis = initial
        ?.atZoneSameInstant(ZoneId.systemDefault())
        ?.toLocalDate()
        ?.atStartOfDay(ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()

    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        val endOfDay = date.atTime(23, 59)
                            .atZone(ZoneId.systemDefault())
                            .toOffsetDateTime()
                        onPicked(endOfDay.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

/** Deadline as a person would read it. Includes the year only when it isn't this one. */
internal fun formatDueDate(due: OffsetDateTime): String {
    val local = due.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    val pattern = if (local.year == LocalDate.now().year) "EEE d MMM" else "EEE d MMM yyyy"
    return local.format(DateTimeFormatter.ofPattern(pattern))
}

/** A deadline in the past on a task that isn't finished. */
private fun isOverdue(task: Task): Boolean {
    val due = task.dueDate ?: return false
    return task.status != "done" && due.isBefore(OffsetDateTime.now())
}

/**
 * The same question for a board row of either shape.
 *
 * Note what this deliberately is not: a state. An open occurrence past its date
 * is still just open — there is no "missed" anywhere in the system, and this
 * only decides whether the date is drawn in amber.
 */
private fun isOverdue(row: BoardRow): Boolean {
    val due = row.dueDate ?: return false
    return row.isOpen && due.isBefore(OffsetDateTime.now())
}

/** Absolute date for the detail sheet, where "23h ago" is less useful than a date. */
private fun formatStamp(time: OffsetDateTime?): String {
    if (time == null) return "unknown"
    return try {
        // The server sends UTC. Formatted as-is, a completion at 20:22 in Rome
        // read "19:22" — the phone's own zone, as formatDueDate already does.
        time.atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
    } catch (_: Exception) {
        "unknown"
    }
}

/** Coarse "how long ago" for the activity feed. */
private fun relativeTime(time: OffsetDateTime?): String {
    if (time == null) return ""
    return try {
        val seconds = Duration.between(time, OffsetDateTime.now()).seconds
        when {
            seconds < 60 -> "now"
            seconds < 3600 -> "${seconds / 60}m"
            seconds < 86_400 -> "${seconds / 3600}h"
            seconds < 604_800 -> "${seconds / 86_400}d"
            else -> time.format(DateTimeFormatter.ofPattern("MMM d"))
        }
    } catch (_: Exception) {
        ""
    }
}
