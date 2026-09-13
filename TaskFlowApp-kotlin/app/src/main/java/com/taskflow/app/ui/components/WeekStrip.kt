package com.taskflow.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
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
    val thisWeek = today.with(DayOfWeek.MONDAY)

    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = 16.dp, end = 8.dp),
            ) {
                Text(
                    text = monthLabel(weekStart, weekStart.plusDays(6)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // The slot is always here and always this size; only its
                // contents come and go. Letting the button appear and
                // disappear moved the whole strip down and back up again,
                // which reads as the screen twitching every time you page a
                // week.
                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .width(THIS_WEEK_SLOT)
                        .fillMaxHeight(),
                ) {
                    // Offered only once it would do something. A "This week"
                    // button on this week is a control that cannot be pressed
                    // wrong, which is a control that should not be there.
                    if (weekStart != thisWeek) {
                        TextButton(
                            onClick = { onWeekChange(thisWeek) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Text("This week", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    // The swipe stays: the chevrons are for finding the
                    // gesture, not for replacing it. Threshold is generous —
                    // this sits above a vertically scrolling list, and a
                    // hesitant diagonal drag should do nothing rather than
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
                WeekArrow(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    description = "Previous week",
                    onClick = { onWeekChange(weekStart.minusWeeks(1)) },
                )

                // The week slides the way you sent it, so paging reads as
                // movement along a line rather than as the numbers being
                // replaced where they stand.
                AnimatedContent(
                    targetState = weekStart,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val width = { w: Int -> if (forward) w else -w }
                        (slideInHorizontally(animationSpec = tween(WEEK_MS)) { width(it) } +
                            fadeIn(tween(WEEK_MS))) togetherWith
                            (slideOutHorizontally(animationSpec = tween(WEEK_MS)) { -width(it) } +
                                fadeOut(tween(WEEK_MS)))
                    },
                    label = "week",
                    modifier = Modifier.weight(1f),
                ) { start ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        (0L..6L).map { start.plusDays(it) }.forEach { day ->
                            DayColumn(
                                day = day,
                                isToday = day == today,
                                isSelected = day == selected,
                                hasTurn = day in datesWithTurns,
                                isOverdue = day in overdueDates,
                                onClick = { onSelect(day) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                WeekArrow(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    description = "Next week",
                    onClick = { onWeekChange(weekStart.plusWeeks(1)) },
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

/** How long a week takes to slide out of the way of the next one. */
private const val WEEK_MS = 220

/**
 * The width kept for **This week**, whether or not the button is in it.
 *
 * Wide enough for the label at a large font scale. Reserving it means the month
 * label never reflows and the strip never changes height.
 */
private val THIS_WEEK_SLOT = 104.dp

@Composable
private fun WeekArrow(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
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
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
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
