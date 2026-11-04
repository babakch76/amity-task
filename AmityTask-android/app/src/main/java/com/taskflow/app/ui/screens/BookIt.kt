package com.taskflow.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taskflow.app.data.model.Booking
import com.taskflow.app.ui.components.Avatar
import com.taskflow.app.ui.components.GroupedCard
import com.taskflow.app.ui.components.QuietAlertDialog
import com.taskflow.app.ui.components.RowDivider
import com.taskflow.app.ui.components.SectionLabel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Book a space: saying when you want the washing machine or the kitchen, so the
// rest of the house can see it coming. Nobody approves it, but the same thing
// cannot be booked twice at once: a clash is shown here and Book it waits for a
// free time, and the server refuses it too. There are no reminders, because a
// booking is not a chore and nothing is owed.
// See bookings.go in the backend.

/** The things people book most, so the usual case is one tap. Anything else can be typed. */
internal val BOOKING_SUGGESTIONS = listOf("Washing machine", "Dryer", "Kitchen", "Bathroom", "Living room")

internal const val BOOKING_WHAT_MAX = 40
internal const val BOOKING_NOTE_MAX = 140

/**
 * The window a person picked. Until at or before From means it runs past
 * midnight, which is how somebody booking the kitchen until one in the morning
 * would naturally enter it, and is the only way to get there with one day.
 */
internal fun bookingWindow(
    day: LocalDate,
    from: LocalTime,
    until: LocalTime,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<OffsetDateTime, OffsetDateTime> {
    val endDay = if (until.isAfter(from)) day else day.plusDays(1)
    return day.atTime(from).atZone(zone).toOffsetDateTime() to
        endDay.atTime(until).atZone(zone).toOffsetDateTime()
}

/** The next full hour, which is when somebody opening this is likely to mean. */
internal fun defaultBookingStart(now: LocalDateTime): LocalDateTime =
    now.withMinute(0).withSecond(0).withNano(0).plusHours(1)

/**
 * Bookings of the same thing that share any time with this window. Same thing
 * means the same name: "Kitchen" and "kitchen" are one room, but nothing here
 * can know that "the washer" is the washing machine, which is why the suggested
 * names come first. Touching ends do not count, since one wash can start the
 * minute another stops.
 */
internal fun overlapping(
    bookings: List<Booking>,
    what: String,
    start: OffsetDateTime,
    end: OffsetDateTime,
): List<Booking> = bookings.filter {
    it.what.trim().equals(what.trim(), ignoreCase = true) &&
        it.startsAt.isBefore(end) && it.endsAt.isAfter(start)
}

/** "Today, 14:00 to 16:00", or "Now, until 16:00" once it has started. */
internal fun bookingTimeLine(
    start: OffsetDateTime,
    end: OffsetDateTime,
    now: OffsetDateTime = OffsetDateTime.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val clock = DateTimeFormatter.ofPattern("HH:mm")
    val from = start.atZoneSameInstant(zone)
    val until = end.atZoneSameInstant(zone)
    if (!start.isAfter(now)) return "Now, until ${until.format(clock)}"

    val today = now.atZoneSameInstant(zone).toLocalDate()
    val day = when (from.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> formatDueDate(start)
    }
    return "$day, ${from.format(clock)} to ${until.format(clock)}"
}

/** At the top of the board, so what is coming up is seen without going looking. */
@Composable
internal fun BookItSection(
    bookings: List<Booking>,
    myUserId: String?,
    onBook: () -> Unit,
    onCancel: (Booking) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SectionLabel("Book a space", Modifier.padding(top = 14.dp, bottom = 8.dp))
        GroupedCard {
            bookings.forEachIndexed { index, booking ->
                BookingRow(
                    booking = booking,
                    mine = booking.userId == myUserId,
                    onCancel = { onCancel(booking) },
                )
                if (index != bookings.lastIndex) RowDivider()
            }
            if (bookings.isNotEmpty()) RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onBook)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (bookings.isEmpty()) "Need the washer or the kitchen? Book it" else "Book something",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun BookingRow(booking: Booking, mine: Boolean, onCancel: () -> Unit) {
    val who = if (mine) "You" else booking.username
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = if (mine) 4.dp else 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(booking.username, booking.userId)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = booking.what,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$who · ${bookingTimeLine(booking.startsAt, booking.endsAt)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (booking.note.isNotBlank()) {
                Text(
                    text = booking.note,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (mine) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel booking",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun CancelBookingDialog(booking: Booking, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this booking?") },
        text = { Text("${booking.what}, ${bookingTimeLine(booking.startsAt, booking.endsAt)}.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Cancel booking") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } },
    )
}

/**
 * The form. [onBook] reports back like every other form here: null closes the
 * sheet, anything else is shown in it, so a failed write does not cost what was
 * typed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BookItSheet(
    bookings: List<Booking>,
    myUserId: String?,
    onDismiss: () -> Unit,
    onBook: (
        what: String,
        note: String,
        start: OffsetDateTime,
        end: OffsetDateTime,
        onResult: (String?) -> Unit,
    ) -> Unit,
) {
    val first = remember { defaultBookingStart(LocalDateTime.now()) }
    var what by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(first.toLocalDate()) }
    var from by remember { mutableStateOf(first.toLocalTime()) }
    var until by remember { mutableStateOf(first.toLocalTime().plusHours(2)) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf<BookingPicker?>(null) }

    val (start, end) = bookingWindow(day, from, until)
    val passed = !end.isAfter(OffsetDateTime.now())
    val clashes = overlapping(bookings, what, start, end)
    val clock = DateTimeFormatter.ofPattern("HH:mm")

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(
                    text = "Book a space",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = "Let the house know what you need and when.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BOOKING_SUGGESTIONS.forEach { suggestion ->
                    FilterChip(
                        selected = what.equals(suggestion, ignoreCase = true),
                        onClick = { what = suggestion },
                        label = { Text(suggestion) },
                    )
                }
            }
            OutlinedTextField(
                value = what,
                onValueChange = { what = it.take(BOOKING_WHAT_MAX) },
                label = { Text("What are you booking?") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(BOOKING_NOTE_MAX) },
                label = { Text("What's it for?") },
                placeholder = { Text("Two loads, or friends over for dinner") },
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    WhenRow("Day", bookingDayLabel(day)) { picking = BookingPicker.DAY }
                    RowDivider()
                    WhenRow("From", from.format(clock)) { picking = BookingPicker.FROM }
                    RowDivider()
                    WhenRow("Until", until.format(clock)) { picking = BookingPicker.UNTIL }
                }
            }

            // The same thing cannot be booked twice at once (the server refuses
            // too), so a clash is said before Book it, and Book it waits for a
            // time that is free. Plain text still: it is a fact about the
            // calendar, not something the person did wrong.
            if (clashes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Already booked. Pick a time that doesn't overlap.",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    clashes.forEach { clash ->
                        val who = if (clash.userId == myUserId) "You" else clash.username
                        Text(
                            text = "$who · ${bookingTimeLine(clash.startsAt, clash.endsAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (passed) {
                Text(
                    text = "That time has already passed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    saving = true
                    error = null
                    onBook(what, note, start, end) { failure ->
                        saving = false
                        error = failure
                        if (failure == null) onDismiss()
                    }
                },
                enabled = what.isNotBlank() && !passed && !saving && clashes.isEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Book it") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }

    when (val picker = picking) {
        BookingPicker.DAY -> DueDatePickerDialog(
            initial = start,
            confirmLabel = "Pick this day",
            onDismiss = { picking = null },
            onPicked = { picked ->
                day = OffsetDateTime.parse(picked)
                    .atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                picking = null
            },
        )
        BookingPicker.FROM, BookingPicker.UNTIL -> BookingTimeDialog(
            title = if (picker == BookingPicker.FROM) "From" else "Until",
            initial = if (picker == BookingPicker.FROM) from else until,
            onDismiss = { picking = null },
            onPicked = { picked ->
                if (picker == BookingPicker.FROM) {
                    // The stretch stays the same length, so moving the start
                    // does not quietly turn two hours into ten.
                    val length = java.time.Duration.between(from, until)
                    from = picked
                    until = picked.plus(length)
                } else {
                    until = picked
                }
                picking = null
            },
        )
        null -> Unit
    }
}

private enum class BookingPicker { DAY, FROM, UNTIL }

private fun bookingDayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> formatDueDate(day.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime())
    }
}

@Composable
private fun WhenRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The clock face rather than Material's TimeInput. That one silently refuses a
 * digit if you tap the box first, because the tap puts the cursor after the two
 * digits already there, and the keyboard then appears not to work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookingTimeDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    QuietAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
