package com.taskflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskflow.app.data.local.TurnsView
import com.taskflow.app.data.local.TurnsViewPreference
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.isOpen
import com.taskflow.app.ui.components.WeekStrip
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * **My chores / My week** — the second shell tab, and the only screen in the
 * app that crosses household lines.
 *
 * The board answers "whose turn is it"; this answers "what is on *me*", for
 * every household at once. A person in two flats had to open each board in
 * turn to assemble that list in their head, which is exactly the sort of thing
 * software is for.
 *
 * ### Two readings of the same turns
 *
 * The toggle beside the refresh icon switches between them, and the title
 * follows:
 *
 * - **My chores** — grouped by household, which is how the tab started.
 * - **My week** — a week strip on top and date buckets beneath, DESIGN.md
 *   §5.11a. The strip is not a calendar in the sense constraint 5 forbids:
 *   nothing of anybody else's is drawn on it, and there is no free/busy
 *   anywhere near it.
 *
 * Both exist **for the interviews**, not forever: the design has one
 * destination, and which reading people actually reach for is a question about
 * people rather than one to settle by argument. The loser gets deleted.
 * [TurnsViewPreference] remembers the choice on the device.
 *
 * ### What it deliberately does not show
 *
 * Only your own turns. Never another member's name, date, or workload, and no
 * shared or comparison mode — DESIGN.md's never-list, and hard constraint 4
 * behind it. The week strip carries that through: a dot means you have a turn
 * that day, never how many, and no day is coloured by how full it is.
 *
 * ### Why it reuses the board's row and the board's sheet
 *
 * [TaskCard] is the board's own row, and tapping it opens
 * [OccurrenceDetailHost] — the board's own detail sheet, with its history, its
 * edit flow and its busy pass behind it. Neither is a copy. The alternative was
 * a second implementation of each that starts identical and drifts, which is
 * the mistake the two auth screens made before `AuthScaffold` pulled them back
 * together.
 *
 * @param myUserId Whose turns these are. Null only if the token has no user id
 *   on it, in which case the list is honestly empty rather than everyone's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyChoresScreen(
    myUserId: String?,
    viewModel: ShellViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val reminderData by viewModel.reminderData.collectAsState()
    val me by viewModel.me.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()

    val context = LocalContext.current
    var view by remember { mutableStateOf(TurnsViewPreference.get(context)) }

    val groups = (uiState as? ShellUiState.Success)?.groups.orEmpty()
    val households = remember(groups, reminderData, myUserId) {
        myChoresByHousehold(groups, reminderData, myUserId)
    }

    // The occurrence whose detail sheet is open, and the household it belongs
    // to — the sheet's actions are performed by that household's ViewModel.
    var detailFor by remember { mutableStateOf<Pair<String, String>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(view.title, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            view = view.other
                            TurnsViewPreference.set(context, view)
                        },
                    ) {
                        Icon(
                            if (view == TurnsView.BY_HOUSEHOLD) Icons.Default.CalendarMonth
                            else Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = "Show ${view.other.title}",
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (isWorking) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (households.isEmpty()) {
                // Not a failure, and worded so it does not read as one: an
                // empty list here is the good outcome.
                EmptyBlock(
                    title = if (view == TurnsView.BY_WEEK) "Nothing on you this week"
                    else "Nothing on you",
                    subtitle = "Turns with your name on them show up here, from every " +
                        "household you're in.",
                )
                return@Column
            }

            when (view) {
                TurnsView.BY_HOUSEHOLD -> ByHousehold(
                    households = households,
                    myUserId = myUserId,
                    myName = me?.username,
                    onOpen = { groupId, occurrenceId -> detailFor = groupId to occurrenceId },
                    onToggleDone = viewModel::setOccurrenceDone,
                )

                TurnsView.BY_WEEK -> ByWeek(
                    households = households,
                    myUserId = myUserId,
                    myName = me?.username,
                    showHouseholdChip = households.size > 1,
                    onOpen = { groupId, occurrenceId -> detailFor = groupId to occurrenceId },
                    onToggleDone = viewModel::setOccurrenceDone,
                )
            }
        }
    }

    detailFor?.let { (groupId, occurrenceId) ->
        OpenedFromMyChores(
            groupId = groupId,
            occurrenceId = occurrenceId,
            myUserId = myUserId,
            onClosed = {
                detailFor = null
                // Whatever the sheet did — passed it, edited the chore, moved
                // its day — happened in another ViewModel. This list is built
                // from the shell's copy, so it has to be told.
                viewModel.refresh()
            },
        )
    }
}

/**
 * The board's detail sheet, opened over My chores.
 *
 * The sheet's actions — pass, edit, delete, move the day — are all performed by
 * [GroupDetailViewModel], which is built for one household. This tab spans
 * several, so the ViewModel is created here for whichever household the tapped
 * turn belongs to, and keyed separately from the board's own so the two tabs do
 * not share one instance across two back stacks.
 *
 * It waits for that ViewModel's first fetch before deciding the sheet has been
 * closed. Without the wait, the moment between "opened" and "the occurrence has
 * arrived" looks exactly like "the user dismissed it", and the sheet would shut
 * itself the instant it opened.
 */
@Composable
private fun OpenedFromMyChores(
    groupId: String,
    occurrenceId: String,
    myUserId: String?,
    onClosed: () -> Unit,
) {
    val boardViewModel: GroupDetailViewModel = viewModel(
        key = "mychores-$groupId",
        factory = GroupDetailViewModel.Factory(groupId),
    )
    val state by boardViewModel.uiState.collectAsState()
    val controller = remember(occurrenceId) {
        ChoreDetailController().apply { open(occurrenceId) }
    }

    var arrived by remember(occurrenceId) { mutableStateOf(false) }
    LaunchedEffect(state.occurrences, occurrenceId) {
        if (state.occurrences.any { it.id == occurrenceId }) arrived = true
    }

    OccurrenceDetailHost(
        controller = controller,
        viewModel = boardViewModel,
        myUserId = myUserId,
    )

    val nothingOpen = controller.occurrenceId == null &&
        controller.editChoreId == null &&
        controller.choreToDelete == null &&
        controller.passCandidate == null &&
        controller.historyChoreName == null

    LaunchedEffect(arrived, nothingOpen) {
        if (arrived && nothingOpen) onClosed()
    }
}

// ═══════════════════════════════════════════════════════════════
// By household — the original reading
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ByHousehold(
    households: List<MyChoresHousehold>,
    myUserId: String?,
    myName: String?,
    onOpen: (String, String) -> Unit,
    onToggleDone: (String, String, Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        households.forEach { household ->
            item(key = "header-${household.group.id}") {
                SectionLabel(household.group.name.uppercase())
            }
            items(household.occurrences, key = { "occ-${it.id}" }) { occurrence ->
                TurnCard(
                    occurrence = occurrence,
                    chores = household.chores,
                    householdName = null,
                    myUserId = myUserId,
                    myName = myName,
                    onOpen = { onOpen(household.group.id, occurrence.id) },
                    onToggleDone = {
                        onToggleDone(household.group.id, occurrence.id, occurrence.isOpen)
                    },
                )
            }
            item(key = "gap-${household.group.id}") { Spacer(Modifier.height(6.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// By week — DESIGN.md §5.11a
// ═══════════════════════════════════════════════════════════════

@Composable
private fun ByWeek(
    households: List<MyChoresHousehold>,
    myUserId: String?,
    myName: String?,
    showHouseholdChip: Boolean,
    onOpen: (String, String) -> Unit,
    onToggleDone: (String, String, Boolean) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var weekStart by remember { mutableStateOf(today.with(DayOfWeek.MONDAY)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var showAllLater by remember { mutableStateOf(false) }

    val turns = remember(households) { households.flatMap { it.turns() } }
    val items = remember(turns, today, showAllLater) { weekAgenda(turns, today, showAllLater) }

    val datesWithTurns = remember(turns) {
        turns.filter { it.occurrence.isOpen }.mapNotNull { it.date }.toSet()
    }
    val overdueDates = remember(datesWithTurns, today) {
        datesWithTurns.filter { it.isBefore(today) }.toSet()
    }

    val listState = rememberLazyListState()

    // Tapping a day scrolls the list to it rather than filtering to it: the
    // question the screen answers is "what is on me", and a day with nothing on
    // it should show you the days around it, not an empty screen.
    LaunchedEffect(selected, items) {
        val day = selected ?: return@LaunchedEffect
        val index = items.indexOfFirst { it is AgendaItem.Row && it.turn.date == day }
            .takeIf { it >= 0 }
            ?: items.indexOfFirst { it is AgendaItem.Row && it.turn.date?.isAfter(day) == true }
                .takeIf { it >= 0 }
            ?: return@LaunchedEffect
        // Land on the heading above the row where there is one, so the row
        // arrives with its bucket rather than alone at the top.
        listState.animateScrollToItem((index - 1).coerceAtLeast(0))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekStrip(
            weekStart = weekStart,
            today = today,
            selected = selected,
            datesWithTurns = datesWithTurns,
            overdueDates = overdueDates,
            onSelect = { day ->
                selected = day
                weekStart = day.with(DayOfWeek.MONDAY)
            },
            onWeekChange = { weekStart = it },
        )

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.key }) { item ->
                when (item) {
                    is AgendaItem.Header -> SectionLabel(item.label)

                    is AgendaItem.Row -> TurnCard(
                        occurrence = item.turn.occurrence,
                        chores = item.turn.chores,
                        householdName = item.turn.group.name.takeIf { showHouseholdChip },
                        myUserId = myUserId,
                        myName = myName,
                        onOpen = { onOpen(item.turn.group.id, item.turn.occurrence.id) },
                        onToggleDone = {
                            onToggleDone(
                                item.turn.group.id,
                                item.turn.occurrence.id,
                                item.turn.occurrence.isOpen,
                            )
                        },
                    )

                    is AgendaItem.ShowAll -> TextButton(onClick = { showAllLater = true }) {
                        Text("Show all ${item.hidden} more")
                    }
                }
            }
        }
    }
}

/** One of your turns, with everything a row needs to draw itself. */
internal data class MyTurn(
    val occurrence: Occurrence,
    val group: Group,
    val chores: List<Chore>,
) {
    /** The day it is wanted, in the phone's zone, or null for an as-needed turn. */
    val date: LocalDate? =
        occurrence.dueDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()
}

private fun MyChoresHousehold.turns(): List<MyTurn> =
    occurrences.map { MyTurn(it, group, chores) }

/** A row in the agenda: a bucket heading, one turn, or the *Show all* button. */
internal sealed interface AgendaItem {
    val key: String

    data class Header(val label: String) : AgendaItem {
        override val key get() = "header-$label"
    }

    data class Row(val turn: MyTurn) : AgendaItem {
        override val key get() = "occ-${turn.occurrence.id}"
    }

    data class ShowAll(val hidden: Int) : AgendaItem {
        override val key get() = "show-all"
    }
}

/**
 * Your turns as dated buckets — DESIGN.md §5.11a, in its order.
 *
 * Anything whose date has passed goes at the **top of TODAY** rather than into
 * a bucket of its own. A "LATE" heading is a heading that names you as having
 * failed at something, which is the whole of what constraint 3 is about; the
 * amber on the row's own date line is the signal, and it is enough.
 *
 * Done-this-cycle rows are kept, last, under their own heading. They are what
 * makes the ten-minute undo reachable from this tab at all — a list that drops
 * a chore the instant you tick it gives a mis-tap nowhere to go.
 */
internal fun weekAgenda(
    turns: List<MyTurn>,
    today: LocalDate,
    showAllLater: Boolean,
): List<AgendaItem> {
    val open = turns.filter { it.occurrence.isOpen }

    // Where LATER THIS WEEK stops. `with(SUNDAY)` on a Sunday returns the same
    // day, so on a Sunday the week has already ended — and tomorrow is then
    // *after* it. Without the coerce, a turn due tomorrow fell into TOMORROW
    // and into NEXT WEEK AND BEYOND at once, and the LazyColumn crashed on the
    // duplicate key rather than merely looking wrong.
    val lastDayOfWeek = maxOf(today.with(DayOfWeek.SUNDAY), today.plusDays(1))

    val overdue = open.filter { it.date != null && it.date.isBefore(today) }.sortedBy { it.date }
    val todayTurns = open.filter { it.date == today }
    val tomorrow = open.filter { it.date == today.plusDays(1) }
    val later = open.filter {
        it.date != null && it.date.isAfter(today.plusDays(1)) && !it.date.isAfter(lastDayOfWeek)
    }.sortedBy { it.date }
    val beyond = open.filter { it.date != null && it.date.isAfter(lastDayOfWeek) }
        .sortedBy { it.date }
    val undated = open.filter { it.date == null }
    val done = turns.filterNot { it.occurrence.isOpen }
        .sortedByDescending { it.occurrence.doneAt }

    val items = mutableListOf<AgendaItem>()
    // Belt as well as braces. The buckets above are meant to be disjoint, and
    // a mistake in one boundary is a crash rather than a cosmetic fault — a
    // row drawn twice is a duplicate key, and a LazyColumn throws on those.
    val emitted = mutableSetOf<String>()

    fun bucket(label: String, rows: List<MyTurn>) {
        val fresh = rows.filter { emitted.add(it.occurrence.id) }
        if (fresh.isEmpty()) return
        items += AgendaItem.Header(label)
        fresh.forEach { items += AgendaItem.Row(it) }
    }

    // Overdue rides at the top of TODAY: it is still a thing to do today.
    bucket("TODAY", overdue + todayTurns)
    bucket("TOMORROW", tomorrow)
    bucket("LATER THIS WEEK", later)

    val beyondFresh = beyond.filter { emitted.add(it.occurrence.id) }
    if (beyondFresh.isNotEmpty()) {
        items += AgendaItem.Header("NEXT WEEK AND BEYOND")
        val shown = if (showAllLater) beyondFresh else beyondFresh.take(5)
        shown.forEach { items += AgendaItem.Row(it) }
        if (beyondFresh.size > shown.size) {
            items += AgendaItem.ShowAll(beyondFresh.size - shown.size)
        }
    }

    bucket("NO DATE", undated)
    bucket("DONE THIS CYCLE", done)
    return items
}

// ═══════════════════════════════════════════════════════════════
// Shared pieces
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

/** The board's row, told only what is true on this screen. */
@Composable
private fun TurnCard(
    occurrence: Occurrence,
    chores: List<Chore>,
    householdName: String?,
    myUserId: String?,
    myName: String?,
    onOpen: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val row = BoardRow.OccurrenceRow(occurrence)
    TaskCard(
        row = row,
        scheduleText = scheduleLineFor(row, chores),
        highlighted = false,
        // Always yours, so the row never says so — and it certainly never names
        // anybody else.
        assigneeName = null,
        // The only name this screen will print, and only on your own row: who
        // actually ticked it. Your own turn covered by a flatmate is an event
        // on your row, not a report on their workload — which is what the
        // never-list is guarding against.
        doneByName = if (occurrence.doneBy == myUserId) myName else occurrence.coveredByName,
        coveredByName = occurrence.coveredByName,
        needsDate = occurrence.needsDate,
        passedByMe = false,
        // Swipe-to-pass needs the rotation and the household's away states,
        // which this tab does not fetch. Passing is still one tap away: the
        // sheet the row opens is the board's own, and its Pass button works,
        // because the sheet brings that household's ViewModel with it.
        canPass = false,
        myUserId = myUserId,
        householdName = householdName,
        onOpenDetail = onOpen,
        onToggleDone = onToggleDone,
        onPass = {},
        onSwiped = {},
    )
}

/** One household's worth of your turns, with the chores they came from. */
private data class MyChoresHousehold(
    val group: Group,
    val occurrences: List<Occurrence>,
    val chores: List<Chore>,
)

/**
 * Your own turns, grouped by household and ordered the way the board orders
 * them: open first, oldest date first, then the ones already done this cycle.
 *
 * A household you have no turns in is left out entirely: an empty heading is a
 * row that says nothing.
 */
private fun myChoresByHousehold(
    groups: List<Group>,
    reminderData: List<ShellViewModel.GroupReminderData>,
    myUserId: String?,
): List<MyChoresHousehold> {
    if (myUserId == null) return emptyList()
    return groups.mapNotNull { group ->
        val data = reminderData.firstOrNull { it.groupId == group.id } ?: return@mapNotNull null
        val mine = data.occurrences
            .filter { it.assignedTo == myUserId }
            .sortedWith(
                compareBy<Occurrence> { !it.isOpen }
                    // An as-needed turn has no date; it waits behind the dated
                    // ones rather than jumping the queue on a null.
                    .thenBy { it.dueDate == null }
                    .thenBy { it.dueDate },
            )
        if (mine.isEmpty()) null else MyChoresHousehold(group, mine, data.chores)
    }
}
