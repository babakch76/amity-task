package com.taskflow.app.ui.screens

import com.taskflow.app.data.model.Booking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class BookItTest {

    private val rome = ZoneId.of("Europe/Rome")
    private val day = LocalDate.of(2026, 11, 7)

    private fun time(hour: Int, minute: Int = 0, date: LocalDate = day): OffsetDateTime =
        date.atTime(hour, minute).atZone(rome).toOffsetDateTime()

    private fun booking(what: String, from: OffsetDateTime, to: OffsetDateTime) = Booking(
        id = "$what-$from",
        groupId = "g",
        userId = "u",
        username = "maya",
        what = what,
        startsAt = from,
        endsAt = to,
    )

    @Test
    fun `a window within one day stays on that day`() {
        val (start, end) = bookingWindow(day, LocalTime.of(14, 0), LocalTime.of(16, 0), rome)
        assertEquals(time(14), start)
        assertEquals(time(16), end)
    }

    @Test
    fun `an until before the from runs past midnight`() {
        val (start, end) = bookingWindow(day, LocalTime.of(22, 0), LocalTime.of(1, 0), rome)
        assertEquals(time(22), start)
        assertEquals(time(1, date = day.plusDays(1)), end)
    }

    @Test
    fun `the same from and until is a whole day rather than nothing`() {
        val (start, end) = bookingWindow(day, LocalTime.of(9, 0), LocalTime.of(9, 0), rome)
        assertEquals(time(9), start)
        assertEquals(time(9, date = day.plusDays(1)), end)
    }

    @Test
    fun `the default start is the next full hour`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 7, 15, 0),
            defaultBookingStart(LocalDateTime.of(2026, 11, 7, 14, 20, 33)),
        )
    }

    @Test
    fun `the default start rolls over midnight`() {
        assertEquals(
            LocalDateTime.of(2026, 11, 8, 0, 0),
            defaultBookingStart(LocalDateTime.of(2026, 11, 7, 23, 50)),
        )
    }

    @Test
    fun `an overlap of the same thing is found`() {
        val taken = booking("Kitchen", time(14), time(16))
        assertEquals(listOf(taken), overlapping(listOf(taken), "Kitchen", time(15), time(17)))
    }

    @Test
    fun `names are compared without case or stray spaces`() {
        val taken = booking("Kitchen", time(14), time(16))
        assertEquals(listOf(taken), overlapping(listOf(taken), " kitchen ", time(15), time(17)))
    }

    @Test
    fun `a different thing at the same time is no clash`() {
        val taken = booking("Kitchen", time(14), time(16))
        assertTrue(overlapping(listOf(taken), "Dryer", time(14), time(16)).isEmpty())
    }

    @Test
    fun `one booking starting as another ends is no clash`() {
        val taken = booking("Washing machine", time(14), time(16))
        assertTrue(overlapping(listOf(taken), "Washing machine", time(16), time(18)).isEmpty())
        assertTrue(overlapping(listOf(taken), "Washing machine", time(12), time(14)).isEmpty())
    }

    @Test
    fun `a window inside or around a booking is a clash`() {
        val taken = booking("Kitchen", time(14), time(18))
        assertEquals(1, overlapping(listOf(taken), "Kitchen", time(15), time(16)).size)
        assertEquals(1, overlapping(listOf(taken), "Kitchen", time(10), time(20)).size)
    }

    @Test
    fun `times from another offset are compared as instants`() {
        val taken = booking(
            "Kitchen",
            time(14).withOffsetSameInstant(ZoneOffset.UTC),
            time(16).withOffsetSameInstant(ZoneOffset.UTC),
        )
        assertEquals(1, overlapping(listOf(taken), "Kitchen", time(15), time(17)).size)
    }

    @Test
    fun `today and tomorrow are said in words`() {
        val now = time(9)
        assertEquals("Today, 14:00 to 16:00", bookingTimeLine(time(14), time(16), now, rome))
        assertEquals(
            "Tomorrow, 14:00 to 16:00",
            bookingTimeLine(time(14, date = day.plusDays(1)), time(16, date = day.plusDays(1)), now, rome),
        )
    }

    @Test
    fun `one already under way says until when`() {
        assertEquals("Now, until 16:00", bookingTimeLine(time(14), time(16), time(15), rome))
    }

    @Test
    fun `the suggestions all fit what the server accepts`() {
        assertTrue(BOOKING_SUGGESTIONS.all { it.length <= BOOKING_WHAT_MAX })
    }

    @Test
    fun `only the soonest three show until the list is opened`() {
        val start = OffsetDateTime.of(2026, 11, 5, 9, 0, 0, 0, java.time.ZoneOffset.UTC)
        val five = (0L..4L).map { booking("Washer $it", start.plusHours(it * 2), start.plusHours(it * 2 + 1)) }

        assertEquals(five.take(3), shownBookings(five, expanded = false))
        assertEquals(five, shownBookings(five, expanded = true))
        // Three or fewer have nothing to fold.
        assertEquals(five.take(3), shownBookings(five.take(3), expanded = false))
        assertEquals(emptyList<Any>(), shownBookings(emptyList(), expanded = false))
    }
}
