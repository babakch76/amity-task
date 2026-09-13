package com.taskflow.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The week strip on top of **My week**: seven days, and which of them have
 * something of yours on them.
 *
 * DESIGN.md §5.11 — weekday letter over the date, today ringed in violet, a dot
 * under any day carrying a turn of yours, the selected day a filled violet
 * circle. Swiping moves a week, and a **This week** button appears once you
 * have moved off the current one.
 *
 * **It is not a calendar in the sense constraint 5 forbids.** No external
 * calendar, no free/busy, no coordinating times with other people, and nothing
 * of anybody else's is drawn on it — a dot here means *you* have a turn that
 * day. `calendar_decision_record.md` deleted a month grid that was a shared
 * view of the household's dates; this is the opposite of that, which is the
 * whole reason it is allowed.
 *
 * Deliberately absent: any colouring by how full a day is, any count on a day,
 * any total for the week. A day with four turns looks exactly like a day with
 * one.
 *
 * @param datesWithTurns Days you have an open turn on. Only membership matters;
 *   how many is not drawn, and is not passed in.
 * @param overdueDates Days already past that still carry an open turn of yours.
 *   These get the amber dot — the same amber the board uses on the date line,
 *   never red, never a count (constraint 3).
 */
@Composable
fun WeekStrip(
    weekStart: LocalDate,
    today: LocalDate,
    selected: LocalDate?,
    datesWithTurns: Set<LocalDate>,
    overdueDates: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
    onWeekChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = (0L..6L).map { weekStart.plusDays(it) }
    val thisWeek = today.with(java.time.DayOfWeek.MONDAY)

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 6.dp),
            ) {
                Text(
                    text = monthLabel(days.first(), days.last()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Only offered once it would do something. A "This week" button
                // on this week is a control that cannot be pressed wrong, which
                // is a control that should not be there.
                if (weekStart != thisWeek) {
                    TextButton(onClick = { onWeekChange(thisWeek); onSelect(today) }) {
                        Text("This week")
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    // A week at a time, in whichever direction. Threshold is
                    // generous: this sits above a vertically scrolling list and
                    // a hesitant diagonal drag should do nothing rather than
                    // jump a week.
                    .pointerInput(weekStart) {
                        var dragged = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragged <= -60f) onWeekChange(weekStart.plusWeeks(1))
                                if (dragged >= 60f) onWeekChange(weekStart.minusWeeks(1))
                                dragged = 0f
                            },
                        ) { _, amount -> dragged += amount }
                    },
            ) {
                days.forEach { day ->
                    DayColumn(
                        day = day,
                        isToday = day == today,
                        isSelected = day == selected,
                        hasTurn = day in datesWithTurns,
                        isOverdue = day in overdueDates,
                        onClick = { onSelect(day) },
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    hasTurn: Boolean,
    isOverdue: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .then(if (isSelected) Modifier.background(primary) else Modifier)
                // Today is ringed rather than filled, so selecting another day
                // never takes today's marker away.
                .then(
                    if (isToday && !isSelected) {
                        Modifier.border(1.5.dp, primary, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Text(
                text = day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        // One dot, never a number. How many turns a day holds is exactly the
        // sort of thing two housemates would compare.
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    when {
                        !hasTurn -> Color.Transparent
                        isOverdue -> stripAmber()
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> primary
                    },
                ),
        )
    }
}

/** "September 2026", or "Aug – Sep 2026" for a week that straddles two. */
private fun monthLabel(first: LocalDate, last: LocalDate): String {
    val locale = Locale.getDefault()
    val firstMonth = first.month.getDisplayName(TextStyle.FULL, locale)
    if (first.month == last.month && first.year == last.year) {
        return "$firstMonth ${first.year}"
    }
    val lastMonth = last.month.getDisplayName(TextStyle.SHORT, locale)
    val shortFirst = first.month.getDisplayName(TextStyle.SHORT, locale)
    return if (first.year == last.year) {
        "$shortFirst – $lastMonth ${last.year}"
    } else {
        "$shortFirst ${first.year} – $lastMonth ${last.year}"
    }
}

/**
 * The board's overdue amber, not `colorScheme.error`.
 *
 * Same two values `GroupDetailScreen` and `AuthScaffold` use, so a date that
 * has passed looks the same on the strip as on the row beneath it —
 * constraint 3: amber or dim, never red.
 */
@Composable
private fun stripAmber(): Color =
    if (isDarkish()) Color(0xFFE0A534) else Color(0xFF9A6700)

@Composable
private fun isDarkish(): Boolean {
    val surface = MaterialTheme.colorScheme.surface
    return (0.299f * surface.red + 0.587f * surface.green + 0.114f * surface.blue) < 0.5f
}
