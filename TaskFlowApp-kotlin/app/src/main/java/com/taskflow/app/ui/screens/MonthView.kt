package com.taskflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.data.model.ScheduleType
import com.taskflow.app.data.model.isOpen
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

// The month behind the week on Mine, for "am I free on the 14th?". Your turns
// only, never a housemate's. calendar_decision_record.md says why this is not a
// household calendar: a chore has one live turn, and every turn after it is
// created when that one is done. So only that turn has a date the server has
// promised. Everything after it here is worked out on this phone and drawn as
// likely, because covers, passes and debts can move it and the client is
// deliberately not sent those. A guess must never look like a commitment.

/** How far ahead you can browse. Further out, a likely turn is barely a guess. */
internal const val MONTHS_AHEAD = 6L

/** One turn of yours on one day. [likely] means the phone worked it out. */
internal data class TurnDay(
    val date: LocalDate,
    val chore: String,
    val household: String,
    val likely: Boolean,
)

/**
 * The dates a chore comes round on after the open turn, through [until].
 *
 * The server's own rule, from nextDueDate in chores.go. An interval counts from
 * completion, so this assumes each turn is done on its day, and a turn whose day
 * has passed as done today. A fixed schedule scans forward from the same point,
 * and a month day a month does not have is skipped, as it is on the server.
 *
 * Empty for as-needed and one-off chores, which have no next date to work out.
 */
internal fun comingDates(
    chore: Chore,
    openDue: LocalDate,
    today: LocalDate,
    until: LocalDate,
): List<LocalDate> {
    val from = if (openDue.isAfter(today)) openDue else today
    return when (chore.scheduleType) {
        ScheduleType.INTERVAL -> {
            val days = chore.intervalDays?.takeIf { it > 0 } ?: return emptyList()
            generateSequence(from.plusDays(days.toLong())) { it.plusDays(days.toLong()) }
                .takeWhile { !it.isAfter(until) }
                .toList()
        }
        ScheduleType.FIXED_DATE -> {
            val weekdays = chore.fixedWeekdays.orEmpty()
            val monthDays = chore.fixedMonthDays.orEmpty()
            generateSequence(from.plusDays(1)) { it.plusDays(1) }
                .takeWhile { !it.isAfter(until) }
                // Sunday is 0 on the server and 7 in java.time.
                .filter { (it.dayOfWeek.value % 7) in weekdays || it.dayOfMonth in monthDays }
                .toList()
        }
        else -> emptyList()
    }
}

/**
 * Who the turn goes to after [current], from nextInRotation in chores.go: the
 * next person in order who is not away. Somebody no longer in the rotation hands
 * to the top of it, and if everybody is away it goes to whoever it would have.
 */
internal fun nextHolder(rotation: List<String>, current: String, away: (String) -> Boolean): String? {
    if (rotation.isEmpty()) return null
    val start = rotation.indexOf(current) + 1
    return rotation.indices
        .map { rotation[(start + it) % rotation.size] }
        .firstOrNull { !away(it) }
        ?: rotation[start % rotation.size]
}

/** Whether this person is away on [day]. No end date means until they say otherwise. */
internal fun MemberInfo.awayOn(day: LocalDate, zone: ZoneId): Boolean {
    if (!away) return false
    val back = awayUntil?.atZoneSameInstant(zone)?.toLocalDate() ?: return true
    return !day.isAfter(back)
}

/** Your turns from [today] through [until], across every household. */
internal fun myTurnDays(
    groups: List<Group>,
    reminderData: List<ShellViewModel.GroupReminderData>,
    myUserId: String?,
    today: LocalDate,
    until: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TurnDay> {
    if (myUserId == null) return emptyList()
    return groups.flatMap { group ->
        val data = reminderData.firstOrNull { it.groupId == group.id } ?: return@flatMap emptyList()
        data.occurrences.filter { it.isOpen }.flatMap { open ->
            val due = open.dueDate?.atZoneSameInstant(zone)?.toLocalDate()
                ?: return@flatMap emptyList<TurnDay>()
            val chore = data.chores.firstOrNull { it.id == open.choreId }
            val planned = if (open.assignedTo == myUserId && !due.isBefore(today)) {
                listOf(TurnDay(due, open.choreName, group.name, likely = false))
            } else {
                emptyList()
            }
            // A turn is handed on when the one before it is done, so that is the
            // day whoever is away gets stepped over, not the day of the turn itself.
            var holder: String? = open.assignedTo
            var handedOn = if (due.isAfter(today)) due else today
            val likely = chore?.let { comingDates(it, due, today, until) }.orEmpty().mapNotNull { date ->
                val on = handedOn
                holder = holder?.let { current ->
                    nextHolder(chore!!.rotation, current) { id ->
                        data.members.any { it.id == id && it.awayOn(on, zone) }
                    }
                }
                handedOn = date
                if (holder == myUserId) TurnDay(date, open.choreName, group.name, likely = true) else null
            }
            planned + likely
        }
    }
}

/**
 * A month as six weeks of squares, null for the blank ones. Always six, because a
 * shorter month would make the popup shorter and move the arrows out from under
 * a thumb going through the months.
 */
internal fun monthCells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
    val blanks = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDayOfWeek.value, 7)
    val cells = List<LocalDate?>(blanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    return cells + List(MONTH_SQUARES - cells.size) { null }
}

private const val MONTH_SQUARES = 42

/**
 * A popup rather than a month opening in place, which would push Up next off the
 * screen, and that is the one thing Mine exists to keep in view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthSheet(turns: List<TurnDay>, today: LocalDate, onDismiss: () -> Unit) {
    val first = remember(today) { YearMonth.from(today) }
    val last = remember(first) { first.plusMonths(MONTHS_AHEAD) }
    var month by remember { mutableStateOf(first) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val byDay = remember(turns) { turns.groupBy { it.date } }
    val weekStart = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { month = month.minusMonths(1); picked = null },
                    enabled = month > first,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(
                    text = month.format(MONTH_TITLE),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { month = month.plusMonths(1); picked = null },
                    enabled = month < last,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                (0L..6L).forEach { offset ->
                    Text(
                        text = weekStart.plus(offset).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            monthCells(month, weekStart).chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        Box(
                            modifier = Modifier.weight(1f).heightIn(min = MONTH_ROW_HEIGHT),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day != null) {
                                MonthDay(
                                    day = day,
                                    today = today,
                                    turns = byDay[day].orEmpty(),
                                    picked = day == picked,
                                    onPick = { picked = day },
                                )
                            }
                        }
                    }
                }
            }

            picked?.let { DayTurns(it, byDay[it].orEmpty()) } ?: MonthKey()
        }
    }
}

@Composable
private fun MonthDay(
    day: LocalDate,
    today: LocalDate,
    turns: List<TurnDay>,
    picked: Boolean,
    onPick: () -> Unit,
) {
    val past = day.isBefore(today)
    val isToday = day == today
    val onlyLikely = turns.isNotEmpty() && turns.all { it.likely }
    val heard = day.format(DAY_HEARD) + when {
        turns.isEmpty() -> ""
        onlyLikely -> ", likely your turn"
        else -> ", your turn"
    }
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !past, onClick = onPick)
            .semantics(mergeDescendants = true) { contentDescription = heard }
            .padding(vertical = 3.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.secondary else Color.Transparent)
                .then(
                    if (picked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleSmall,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onSecondary
                    past -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        TurnDot(filled = turns.any { !it.likely }, ringed = onlyLikely)
    }
}

/** Filled is a turn you have. A ring is one you probably will. Nothing is a count. */
@Composable
private fun TurnDot(filled: Boolean, ringed: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(if (filled) accent else Color.Transparent)
            .then(if (ringed) Modifier.border(1.5.dp, accent, CircleShape) else Modifier),
    )
}

@Composable
private fun DayTurns(day: LocalDate, turns: List<TurnDay>) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(formatMonthDay(day), style = MaterialTheme.typography.titleSmall)
        if (turns.isEmpty()) {
            Text(
                text = "No chores this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        turns.forEach { turn ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TurnDot(filled = !turn.likely, ringed = turn.likely)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(turn.chore, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (turn.likely) "${turn.household} · Likely" else turn.household,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthKey() {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TurnDot(filled = true, ringed = false)
            Spacer(Modifier.width(8.dp))
            Text("Your turn", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.width(18.dp))
            TurnDot(filled = false, ringed = true)
            Spacer(Modifier.width(8.dp))
            Text("Likely your turn", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            text = "Likely turns can move if someone covers, passes or goes away.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatMonthDay(day: LocalDate): String =
    formatDueDate(day.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime())

private val MONTH_ROW_HEIGHT = 52.dp
private val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val DAY_HEARD: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
