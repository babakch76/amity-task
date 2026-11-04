package com.taskflow.app.ui.screens

import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class MonthViewTest {

    // Monday.
    private val today = LocalDate.of(2026, 11, 2)
    private val until = LocalDate.of(2026, 12, 31)

    private fun chore(
        schedule: String,
        interval: Int? = null,
        weekdays: List<Int>? = null,
        monthDays: List<Int>? = null,
        rotation: List<String> = listOf("me", "maya"),
    ) = Chore(
        id = "c",
        groupId = "g",
        name = "Bins",
        scheduleType = schedule,
        intervalDays = interval,
        fixedWeekdays = weekdays,
        fixedMonthDays = monthDays,
        rotation = rotation,
        createdBy = "me",
    )

    private fun open(assignedTo: String, due: LocalDate?, name: String = "Bins") = Occurrence(
        id = "o",
        choreId = "c",
        groupId = "g",
        assignedTo = assignedTo,
        status = "open",
        dueDate = due?.atTime(23, 59)?.atOffset(ZoneOffset.UTC),
        choreName = name,
    )

    private fun member(id: String, away: Boolean = false, until: OffsetDateTime? = null) =
        MemberInfo(id = id, username = id, email = "$id@x", role = "member", away = away, awayUntil = until)

    private fun data(chore: Chore, occurrence: Occurrence) =
        listOf(ShellViewModel.GroupReminderData("g", listOf(occurrence), listOf(chore)))

    private val flat = listOf(Group(id = "g", name = "Flat 3B", description = "", createdBy = "me"))

    private fun days(chore: Chore, occurrence: Occurrence, end: LocalDate = until) =
        myTurnDays(flat, data(chore, occurrence), "me", today, end, ZoneOffset.UTC)

    @Test
    fun `an interval counts on from the open turn's day`() {
        val dates = comingDates(chore(ScheduleType.INTERVAL, interval = 3), today.plusDays(1), today, today.plusDays(10))
        assertEquals(listOf(today.plusDays(4), today.plusDays(7), today.plusDays(10)), dates)
    }

    @Test
    fun `a turn whose day has passed counts as done today`() {
        val dates = comingDates(chore(ScheduleType.INTERVAL, interval = 3), today.minusDays(5), today, today.plusDays(6))
        assertEquals(listOf(today.plusDays(3), today.plusDays(6)), dates)
    }

    @Test
    fun `a fixed weekday lands on that weekday, with Sunday as zero`() {
        val dates = comingDates(chore(ScheduleType.FIXED_DATE, weekdays = listOf(0)), today, today, today.plusDays(14))
        assertEquals(listOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15)), dates)
    }

    @Test
    fun `the day of the open turn is not counted twice`() {
        val thursday = LocalDate.of(2026, 11, 5)
        val dates = comingDates(chore(ScheduleType.FIXED_DATE, weekdays = listOf(4)), thursday, today, thursday.plusDays(7))
        assertEquals(listOf(thursday.plusDays(7)), dates)
    }

    @Test
    fun `a month day a month does not have is skipped, as on the server`() {
        val dates = comingDates(
            chore(ScheduleType.FIXED_DATE, monthDays = listOf(31)),
            today, today, LocalDate.of(2027, 1, 31),
        )
        assertEquals(listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 31)), dates)
    }

    @Test
    fun `as needed and one-off chores have no next date`() {
        assertTrue(comingDates(chore(ScheduleType.AS_NEEDED), today, today, until).isEmpty())
        assertTrue(comingDates(chore(ScheduleType.ONE_OFF), today, today, until).isEmpty())
    }

    @Test
    fun `the rotation goes round in order from whoever holds it`() {
        val rotation = listOf("a", "b", "c")
        assertEquals("c", nextHolder(rotation, "b") { false })
        assertEquals("a", nextHolder(rotation, "c") { false })
    }

    @Test
    fun `someone away is stepped over, as on the server`() {
        assertEquals("a", nextHolder(listOf("a", "b", "c"), "b") { it == "c" })
    }

    @Test
    fun `with everyone away it goes to whoever it would have`() {
        assertEquals("c", nextHolder(listOf("a", "b", "c"), "b") { true })
    }

    @Test
    fun `someone no longer in the rotation hands to the top, as on the server`() {
        assertEquals("a", nextHolder(listOf("a", "b"), "gone") { false })
        assertNull(nextHolder(emptyList(), "a") { false })
    }

    @Test
    fun `being away ends the day after the date you gave`() {
        val back = today.plusDays(5).atTime(12, 0).atOffset(ZoneOffset.UTC)
        val sam = member("sam", away = true, until = back)
        assertTrue(sam.awayOn(today.plusDays(5), ZoneOffset.UTC))
        assertFalse(sam.awayOn(today.plusDays(6), ZoneOffset.UTC))
    }

    @Test
    fun `away with no end date lasts`() {
        assertTrue(member("sam", away = true).awayOn(today.plusYears(1), ZoneOffset.UTC))
        assertFalse(member("sam").awayOn(today, ZoneOffset.UTC))
    }

    // The case the seeded household showed on the emulator. Rotation maya, me,
    // sam, with sam away: the server gave the turn after mine to maya.
    @Test
    fun `a housemate away now is stepped over, then counted once they are back`() {
        val rotation = listOf("maya", "me", "sam")
        val c = chore(ScheduleType.INTERVAL, interval = 3, rotation = rotation)
        val back = today.plusDays(5).atTime(12, 0).atOffset(ZoneOffset.UTC)
        val turns = myTurnDays(
            flat,
            listOf(
                ShellViewModel.GroupReminderData(
                    "g",
                    listOf(open("me", today.plusDays(2))),
                    listOf(c),
                    listOf(member("me"), member("maya"), member("sam", away = true, until = back)),
                ),
            ),
            "me", today, today.plusDays(20), ZoneOffset.UTC,
        ).filter { it.likely }.map { it.date }
        // +2 me (open). Handed on at +2, sam away, so +5 maya. Handed on at +5,
        // still away, so +8 me. Then sam at +11, maya at +14, me at +17.
        assertEquals(listOf(today.plusDays(8), today.plusDays(17)), turns)
    }

    @Test
    fun `if you are away the likely turns step over you too`() {
        val c = chore(ScheduleType.INTERVAL, interval = 7, rotation = listOf("me", "maya"))
        val turns = myTurnDays(
            flat,
            listOf(
                ShellViewModel.GroupReminderData(
                    "g",
                    listOf(open("maya", today.plusDays(1))),
                    listOf(c),
                    listOf(member("me", away = true), member("maya")),
                ),
            ),
            "me", today, today.plusDays(40), ZoneOffset.UTC,
        )
        assertTrue(turns.isEmpty())
    }

    @Test
    fun `your open turn is a promise and the ones after it are likely`() {
        val due = today.plusDays(1)
        val turns = days(chore(ScheduleType.INTERVAL, interval = 7), open("me", due), today.plusDays(16))
        assertEquals(
            listOf(TurnDay(due, "Bins", "Flat 3B", likely = false), TurnDay(today.plusDays(15), "Bins", "Flat 3B", likely = true)),
            turns,
        )
    }

    @Test
    fun `a housemate's open turn is never drawn, but your turn after it is`() {
        val due = today.plusDays(1)
        val turns = days(chore(ScheduleType.INTERVAL, interval = 7), open("maya", due), today.plusDays(16))
        assertEquals(listOf(TurnDay(today.plusDays(8), "Bins", "Flat 3B", likely = true)), turns)
    }

    @Test
    fun `a turn of yours whose day has passed stays on Mine and not on the month`() {
        val turns = days(chore(ScheduleType.INTERVAL, interval = 7), open("me", today.minusDays(3)), today.plusDays(6))
        assertTrue(turns.isEmpty())
    }

    @Test
    fun `an as-needed turn has no day to put it on`() {
        assertTrue(days(chore(ScheduleType.AS_NEEDED), open("me", null)).isEmpty())
    }

    @Test
    fun `nobody signed in has no turns`() {
        val c = chore(ScheduleType.INTERVAL, interval = 7)
        assertTrue(myTurnDays(flat, data(c, open("me", today)), null, today, until, ZoneOffset.UTC).isEmpty())
    }

    @Test
    fun `a month starts on the right weekday`() {
        // November 2026 starts on a Sunday.
        val monday = monthCells(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(6, monday.takeWhile { it == null }.size)
        val sunday = monthCells(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(0, sunday.takeWhile { it == null }.size)
    }

    @Test
    fun `every month is the same height, so the arrows stay put`() {
        (1..12).forEach { assertEquals(42, monthCells(YearMonth.of(2027, it), DayOfWeek.MONDAY).size) }
    }

    @Test
    fun `every day of the month is there once`() {
        val cells = monthCells(YearMonth.of(2028, 2), DayOfWeek.MONDAY).filterNotNull()
        assertEquals(29, cells.size)
        assertEquals(LocalDate.of(2028, 2, 29), cells.last())
    }
}
