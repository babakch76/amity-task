package com.taskflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.isOpen
import com.taskflow.app.data.model.scheduleWording
import com.taskflow.app.ui.components.Avatar
import com.taskflow.app.ui.components.CapsLabel
import com.taskflow.app.ui.components.DOCK_CLEARANCE
import com.taskflow.app.ui.components.GroupedCard
import com.taskflow.app.ui.components.RowDivider
import com.taskflow.app.ui.components.SectionLabel
import com.taskflow.app.ui.theme.Quiet
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * **Mine** — your turns, across every household. The first place in the dock,
 * and the screen the app opens on.
 *
 * It replaces the two readings the interviews compared (My chores, grouped by
 * household, and My week, a paged week strip over day buckets). This keeps
 * what each did well: a glance at the coming week, and one obvious thing to do
 * next.
 *
 * - **Next 7 days** — a rolling strip from today, a dot on each day carrying a
 *   turn of yours. Never a count, never a colour by how full a day is.
 * - **Up next** — the soonest turn that is still ahead of you, with the
 *   household's definition of done on the card and *I did this* under it.
 * - **Also yours** — everything else on you; a turn whose day has passed is
 *   amber and says the date, never how late (constraint 3).
 * - **Done lately** — what you have already got through this week.
 *
 * Only your own turns. Never another member's name, date, or workload
 * (constraint 4). Tapping any turn opens the board's own detail sheet.
 */
@Composable
fun MyChoresScreen(
    myUserId: String?,
    viewModel: ShellViewModel = viewModel(),
    hasInvites: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
) {
    val context = LocalContext.current
    var showGuideCard by remember {
        mutableStateOf(!LocalHints.seen(context, LocalHints.Hint.GUIDE_CARD))
    }
    fun guideCardDone() {
        showGuideCard = false
        LocalHints.markSeen(context, LocalHints.Hint.GUIDE_CARD)
    }

    val uiState by viewModel.uiState.collectAsState()
    val reminderData by viewModel.reminderData.collectAsState()
    val me by viewModel.me.collectAsState()
    val isWorking by viewModel.isWorking.collectAsState()

    val groups = (uiState as? ShellUiState.Success)?.groups.orEmpty()
    val households = remember(groups, reminderData, myUserId) {
        myChoresByHousehold(groups, reminderData, myUserId)
    }
    val turns = remember(households) { households.flatMap { it.turns() } }
    val today = LocalDate.now()
    val manyHouseholds = households.size > 1

    val open = turns.filter { it.occurrence.isOpen }
    val upNext = open.filter { it.date != null && !it.date.isBefore(today) }.minByOrNull { it.date!! }
        ?: open.firstOrNull { it.date == null }
    val others = open.filter { it !== upNext }
        .sortedWith(compareBy<MyTurn> { it.date == null }.thenBy { it.date })
    val doneLately = turns
        .filter { !it.occurrence.isOpen && it.doneDate != null && !it.doneDate.isBefore(today.minusDays(6)) }
        .sortedByDescending { it.occurrence.doneAt }

    var detailFor by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Fresh every time the tab is opened (B-23). The board does its work
    // through its own household ViewModel, so a turn completed or passed
    // there left this list showing the old state until refresh was tapped.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isWorking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = DOCK_CLEARANCE),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        today.format(TODAY_LINE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Your turns", style = MaterialTheme.typography.headlineLarge)
                }
                // You live behind your own face. The dot is the one badge in
                // the app: invitations are addressed to you.
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onOpenSettings)
                        .semantics { contentDescription = if (hasInvites) "You and settings, invitation waiting" else "You and settings" },
                ) {
                    Avatar(me?.username ?: "?", myUserId ?: "", 44.dp)
                    if (hasInvites) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }

            if (showGuideCard) {
                GuideCard(
                    onOpen = {
                        guideCardDone()
                        onOpenGuide()
                    },
                    onDismiss = { guideCardDone() },
                )
            }

            NextSevenDays(today = today, days = open.mapNotNull { it.date }.toSet())

            if (open.isEmpty() && doneLately.isEmpty()) {
                EmptyBlock(
                    title = "Nothing on you",
                    subtitle = "Turns with your name on them show up here, from every household you're in.",
                )
            }

            upNext?.let { turn ->
                UpNextCard(
                    turn = turn,
                    showHousehold = true,
                    onOpen = { detailFor = turn.group.id to turn.occurrence.id },
                    onDone = {
                        viewModel.setOccurrenceDone(turn.group.id, turn.occurrence.id, true, turn.occurrence.choreName)
                    },
                )
            }

            if (others.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(if (upNext != null) "Also yours" else "Yours")
                    GroupedCard {
                        others.forEachIndexed { i, turn ->
                            if (i > 0) RowDivider()
                            TurnRow(turn, today, manyHouseholds) { detailFor = turn.group.id to turn.occurrence.id }
                        }
                    }
                }
            }

            if (doneLately.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Done lately")
                    GroupedCard {
                        doneLately.forEachIndexed { i, turn ->
                            if (i > 0) RowDivider()
                            TurnRow(turn, today, manyHouseholds) { detailFor = turn.group.id to turn.occurrence.id }
                        }
                    }
                }
            }
        }
    }

    detailFor?.let { (groupId, occurrenceId) ->
        OpenedFromMyChores(
            groupId = groupId,
            occurrenceId = occurrenceId,
            myUserId = myUserId,
            onMessage = { text, undoOccurrence, undoPassOccurrence ->
                viewModel.showMessage(
                    text,
                    undoOccurrence?.let { groupId to it },
                    undoPassOccurrence?.let { groupId to it },
                )
            },
            onClosed = {
                detailFor = null
                // Whatever the sheet did happened in another ViewModel. This
                // list is built from the shell's copy, so it has to be told.
                viewModel.refresh()
            },
        )
    }
}

private val TODAY_LINE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** Seven days from today; today filled, a dot under any day with a turn of yours. */
@Composable
private fun NextSevenDays(today: LocalDate, days: Set<LocalDate>) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val inWeek = (0L..6L).count { today.plusDays(it) in days }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text("Next 7 days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    when (inWeek) {
                        0 -> "Nothing dated"
                        1 -> "A turn on 1 day"
                        else -> "Turns on $inWeek days"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                (0L..6L).forEach { offset ->
                    val day = today.plusDays(offset)
                    val isToday = offset == 0L
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            day.format(WEEKDAY_LETTER),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isToday) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isToday) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (day in days) MaterialTheme.colorScheme.primary
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                ),
                        )
                    }
                }
            }
        }
    }
}

private val WEEKDAY_LETTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEEE")

/** The one turn to do next, with the agreement on the card and the button under it. */
@Composable
private fun UpNextCard(turn: MyTurn, showHousehold: Boolean, onOpen: () -> Unit, onDone: () -> Unit) {
    val occ = turn.occurrence
    val chore = turn.chores.firstOrNull { it.id == occ.choreId }
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CapsLabel("Up next")
                Spacer(Modifier.weight(1f))
                if (showHousehold) HouseholdChip(turn.group.name)
            }
            Column(
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(occ.choreName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(
                        chore?.scheduleWording()?.replaceFirstChar { it.uppercase() },
                        occ.dueDate?.let { "due ${formatDueDate(it)}" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            occ.coveredByName?.let { coverer ->
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("$coverer covered your last turn, so this one is yours.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (occ.doneLine.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CapsLabel("Done means")
                    Text(occ.doneLine, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onDone, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("I did this", style = MaterialTheme.typography.titleMedium)
                }
                // Passing is confirmed in the chore's own sheet, which names who
                // it goes to — so this opens that, rather than passing blind.
                // Hidden when there is nobody to pass to (the server's pass_to).
                if (occ.passTo != null) {
                    OutlinedIconButton(onClick = onOpen, modifier = Modifier.size(50.dp)) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Busy? Pass it on")
                    }
                }
            }
        }
    }
}

@Composable
private fun HouseholdChip(name: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(999.dp)) {
        Text(
            name,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** One of your turns in a list: a dot, the chore, and when. */
@Composable
private fun TurnRow(turn: MyTurn, today: LocalDate, showHousehold: Boolean, onClick: () -> Unit) {
    val occ = turn.occurrence
    val chore = turn.chores.firstOrNull { it.id == occ.choreId }
    val q = Quiet.colors
    val done = !occ.isOpen
    val past = !done && turn.date != null && turn.date.isBefore(today)
    val meta = when {
        done -> "Done ${occ.doneAt?.let { formatDueDate(it) }.orEmpty()}"
        past -> "Past its day · was ${formatDueDate(occ.dueDate!!)}"
        occ.dueDate != null -> listOfNotNull(
            chore?.scheduleWording()?.replaceFirstChar { it.uppercase() },
            formatDueDate(occ.dueDate),
        ).joinToString(" · ")
        else -> "When it's needed"
    } + if (showHousehold) " · ${turn.group.name}" else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (done) {
            Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(q.doneSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, null, tint = q.done, modifier = Modifier.size(14.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(horizontal = 6.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (past) q.pastDot else MaterialTheme.colorScheme.outline),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                occ.choreName,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                meta,
                style = MaterialTheme.typography.bodySmall,
                color = if (past) q.pastText else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

    /** The day it was actually done, which is routinely not [date]. */
    val doneDate: LocalDate? =
        occurrence.doneAt?.atZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()
}

private fun MyChoresHousehold.turns(): List<MyTurn> =
    occurrences.map { MyTurn(it, group, chores) }

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
    onMessage: (String, String?, String?) -> Unit,
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
            onMessage(it, state.undoableDone, state.undoablePass)
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
