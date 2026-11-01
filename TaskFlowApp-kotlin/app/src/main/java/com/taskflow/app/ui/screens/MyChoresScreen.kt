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
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    // Fresh every time the tab is opened (B-23). The board does its work
    // through its own household ViewModel, so a turn completed or passed
    // there left this list showing the old state until refresh was tapped.
    LaunchedEffect(Unit) { viewModel.refresh() }

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
            onMessage = { text, undoOccurrence ->
                viewModel.showMessage(text, undoOccurrence?.let { groupId to it })
            },
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
    /**
     * A result to show on the shell's snackbar — this tab has no other — with
     * the occurrence it can undo, when it was a completion.
     */
    onMessage: (String, String?) -> Unit,
    onClosed: () -> Unit,
) {
    val boardViewModel: GroupDetailViewModel = viewModel(
        key = "mychores-$groupId",
        factory = GroupDetailViewModel.Factory(groupId),
    )
    val state by boardViewModel.uiState.collectAsState()

    // The ViewModel outlives the sheet — it is keyed to the household, so the
    // second turn opened from here reuses the first one's. Without a reload it
    // shows whatever that household looked like then: a turn completed on the
    // board since still read as open, and one undone still read as done. A
    // brand-new ViewModel has just loaded, so only a reused one is refreshed.
    LaunchedEffect(occurrenceId) {
        if (state.occurrences.isNotEmpty()) boardViewModel.refresh()
    }
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
        // This tab spans households, so the sheet says which one the turn came
        // from. The board's own mount leaves it out: its app bar already has.
        householdName = state.group?.name,
    )

    val nothingOpen = controller.occurrenceId == null &&
        controller.editChoreId == null &&
        controller.choreToDelete == null &&
        controller.passCandidate == null &&
        controller.historyChoreName == null

    // The household ViewModel says how a completion, pass or edit went —
    // "Trash done. bb is next." — but only the board ever listened. Passed to
    // the shell, whose snackbar this tab shows.
    LaunchedEffect(state.message) {
        state.message?.let {
            onMessage(it, state.undoableDone)
            boardViewModel.dismissMessage()
        }
    }

    // Closing unmounts this, and with it the listener above. So it waits for
    // whatever the sheet just started to answer: "I did this" closes the sheet
    // in the same tap that sends the request, and closing on that alone threw
    // the reply away. isWorking is already true by then — the ViewModel sets
    // it before its first suspension, on the main thread.
    LaunchedEffect(arrived, nothingOpen, state.isWorking, state.message) {
        if (arrived && nothingOpen && !state.isWorking && state.message == null) onClosed()
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

    val turns = remember(households) { households.flatMap { it.turns() } }
    val items = remember(turns, weekStart, selected, today) {
        weekAgenda(turns, weekStart, today, selected)
    }

    // The dots are about the whole of your turns, not about what the list is
    // currently narrowed to: a day you have selected away from should still
    // show that it has something on it.
    val datesWithTurns = remember(turns) {
        turns.filter { it.occurrence.isOpen }.mapNotNull { it.date }.toSet()
    }
    val overdueDates = remember(datesWithTurns, today) {
        datesWithTurns.filter { it.isBefore(today) }.toSet()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekStrip(
            weekStart = weekStart,
            today = today,
            selected = selected,
            datesWithTurns = datesWithTurns,
            overdueDates = overdueDates,
            // Tapping the selected day again lets go of it and gives the week
            // back. Without that, narrowing to a day is a one-way door you can
            // only leave by paging away and back.
            onSelect = { day -> selected = if (selected == day) null else day },
            // Changing week drops the day. You asked to look at a week; showing
            // one day of it because of a tap two weeks ago would be answering a
            // question nobody asked.
            onWeekChange = { weekStart = it; selected = null },
        )

        if (items.isEmpty()) {
            NothingOn(weekStart = weekStart, today = today, selected = selected)
            return@Column
        }

        LazyColumn(
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
                }
            }
        }
    }
}

/**
 * A week, or a day, with nothing of yours in it.
 *
 * Light rather than congratulatory. Nothing to do is the ordinary state of a
 * flat that is working, not an achievement to be marked — a "well done!" here
 * would be the same gamification the never-list rules out, wearing a joke as a
 * disguise. So these are deadpan, and none of them mentions anybody else or
 * what they might still have on.
 *
 * The line is picked from the week being shown rather than at random, so it
 * stays put while you look at it and changes when you page somewhere else.
 */
@Composable
private fun NothingOn(weekStart: LocalDate, today: LocalDate, selected: LocalDate?) {
    val past = (selected ?: weekStart.plusDays(6)).isBefore(today)

    val lines = when {
        selected != null && past -> DAY_GONE_LINES
        selected != null -> DAY_CLEAR_LINES
        past -> WEEK_GONE_LINES
        else -> WEEK_CLEAR_LINES
    }
    val seed = (selected ?: weekStart).toEpochDay().toInt()
    val line = lines[((seed % lines.size) + lines.size) % lines.size]

    val heading = when {
        selected == null -> "Nothing on you this week"
        selected == today -> "Nothing on you today"
        // "Nothing on you Tuesday" reads like a sentence missing a word, and
        // "on you on Tuesday" reads like one with an extra.
        else -> "Nothing on ${selected.format(DAY_NAME)}"
    }

    EmptyBlock(title = heading, subtitle = line)
}

private val DAY_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE")

private val WEEK_CLEAR_LINES = listOf(
    "The flat appears to be running itself. Don't look directly at it.",
    "A whole week and not one bin. Enjoy it while it lasts.",
    "Suspiciously empty. Somewhere, a kettle is descaling itself.",
)

private val WEEK_GONE_LINES = listOf(
    "That week came and went without needing you.",
    "Nothing here. Whatever happened that week, it happened without a rota.",
)

private val DAY_CLEAR_LINES = listOf(
    "A free day. Sit down before something notices.",
    "Nothing due. The kitchen will cope.",
    "Not your day. In the good sense.",
)

private val DAY_GONE_LINES = listOf(
    "That day asked nothing of you.",
    "Nothing was on you then either.",
)

/** One of your turns, with everything a row needs to draw itself. */
internal data class MyTurn(
    val occurrence: Occurrence,
    val group: Group,
    val chores: List<Chore>,
) {
    /** The day it is wanted, in the phone's zone, or null for an as-needed turn. */
    val date: LocalDate? =
        occurrence.dueDate?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()

    /** The day it was actually done, which is routinely not [date]. */
    val doneDate: LocalDate? =
        occurrence.doneAt?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()
}

private fun MyChoresHousehold.turns(): List<MyTurn> =
    occurrences.map { MyTurn(it, group, chores) }

/** A row in the agenda: a day heading, or one turn. */
internal sealed interface AgendaItem {
    val key: String

    data class Header(val label: String) : AgendaItem {
        override val key get() = "header-$label"
    }

    data class Row(val turn: MyTurn) : AgendaItem {
        override val key get() = "occ-${turn.occurrence.id}"
    }

}

/**
 * Your turns inside the window the strip is showing — DESIGN.md §5.11a, scoped.
 *
 * The window is **the displayed week**, or **one day** of it once a day is
 * selected. That is the whole of what the strip is for: it says which days
 * carry something, and the list underneath answers the day or the week you
 * pointed at. A list that ignored the strip and always showed everything would
 * make the strip decorative.
 *
 * Grouped by day, in date order, under a heading that says which day it is.
 * Anything whose day has passed sits under that day with its date line in
 * amber, and there is no "LATE" heading anywhere: a heading that names you as
 * having failed is what constraint 3 is about, and the amber on the row is
 * signal enough.
 *
 * **NO DATE** — as-needed chores that are currently your turn — appears only on
 * the current week and only when no single day is selected. They belong to no
 * date, so pinning them under a day would be a lie, and repeating them on every
 * week you page to would be noise.
 *
 * **DONE THIS CYCLE** is keyed on the day it was *done*, not the day it was
 * due, because the question this section answers is "what have I already got
 * through", and those two dates are routinely different.
 */
internal fun weekAgenda(
    turns: List<MyTurn>,
    weekStart: LocalDate,
    today: LocalDate,
    selected: LocalDate?,
): List<AgendaItem> {
    val window: ClosedRange<LocalDate> =
        if (selected != null) selected..selected else weekStart..weekStart.plusDays(6)

    val open = turns.filter { it.occurrence.isOpen }
    val items = mutableListOf<AgendaItem>()
    // Belt as well as braces. Drawing one row twice is a duplicate key, and a
    // LazyColumn throws on those rather than merely looking wrong (B-9).
    val emitted = mutableSetOf<String>()

    open.filter { it.date != null && it.date in window }
        .sortedBy { it.date }
        .groupBy { it.date!! }
        .toSortedMap()
        .forEach { (day, rows) ->
            val fresh = rows.filter { emitted.add(it.occurrence.id) }
            if (fresh.isEmpty()) return@forEach
            items += AgendaItem.Header(dayLabel(day, today))
            fresh.forEach { items += AgendaItem.Row(it) }
        }

    if (selected == null && today in window) {
        val undated = open.filter { it.date == null && emitted.add(it.occurrence.id) }
        if (undated.isNotEmpty()) {
            items += AgendaItem.Header("NO DATE")
            undated.forEach { items += AgendaItem.Row(it) }
        }
    }

    val done = turns
        .filterNot { it.occurrence.isOpen }
        .filter { it.doneDate != null && it.doneDate in window }
        .sortedByDescending { it.occurrence.doneAt }
        .filter { emitted.add(it.occurrence.id) }
    if (done.isNotEmpty()) {
        items += AgendaItem.Header("DONE THIS CYCLE")
        done.forEach { items += AgendaItem.Row(it) }
    }

    return items
}

/** "TODAY", "TOMORROW", "YESTERDAY", or "MONDAY 14 SEPT". */
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "TODAY"
    today.plusDays(1) -> "TOMORROW"
    today.minusDays(1) -> "YESTERDAY"
    else -> day.format(DAY_HEADING).uppercase(Locale.getDefault())
}

private val DAY_HEADING: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMM")

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
        // coveredByName is set on the turn *after* a cover, never on the one
        // that was covered, so it printed "Done by someone" here.
        doneByName = if (occurrence.doneBy == myUserId) myName else occurrence.doneByName,
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
