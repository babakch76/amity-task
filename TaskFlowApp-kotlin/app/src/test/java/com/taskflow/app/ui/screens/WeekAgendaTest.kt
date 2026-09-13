package com.taskflow.app.ui.screens

import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * What **My week** puts under the strip.
 *
 * The list answers the window the strip is pointing at — the displayed week, or
 * one day of it — so these assert the window, the day headings, and that no
 * turn is ever drawn twice. That last one is not fussiness: a duplicate key
 * crashes a `LazyColumn` rather than merely looking wrong, and it has already
 * happened once (B-9), when a turn due tomorrow fell into two buckets on a
 * Sunday.
 */
class WeekAgendaTest {

    private val group = Group(id = "g1", name = "Flat 3B", description = "", createdBy = "u1")
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Monday of the week these tests mostly live in. */
    private val monday = LocalDate.of(2026, 9, 7)

    private fun turn(id: String, due: LocalDate?, doneOn: LocalDate? = null) = MyTurn(
        occurrence = Occurrence(
            id = id,
            choreId = "c-$id",
            groupId = group.id,
            assignedTo = "u1",
            status = if (doneOn == null) "open" else "done",
            dueDate = due?.atStartOfDay(zone)?.toOffsetDateTime(),
            doneAt = doneOn?.atStartOfDay(zone)?.toOffsetDateTime(),
        ),
        group = group,
        chores = emptyList(),
    )

    private fun List<AgendaItem>.headers() =
        filterIsInstance<AgendaItem.Header>().map { it.label }

    private fun List<AgendaItem>.rowIds() =
        filterIsInstance<AgendaItem.Row>().map { it.turn.occurrence.id }

    private fun List<AgendaItem>.idsUnder(label: String): List<String> {
        val start = indexOfFirst { it is AgendaItem.Header && it.label == label }
        if (start < 0) return emptyList()
        return drop(start + 1)
            .takeWhile { it !is AgendaItem.Header }
            .filterIsInstance<AgendaItem.Row>()
            .map { it.turn.occurrence.id }
    }

    // ─── The window ───────────────────────────────────────────

    @Test
    fun `the week shows its own seven days and nothing either side`() {
        val turns = listOf(
            turn("before", monday.minusDays(1)),
            turn("mon", monday),
            turn("sun", monday.plusDays(6)),
            turn("after", monday.plusDays(7)),
        )

        val ids = weekAgenda(turns, monday, today = monday, selected = null).rowIds()

        assertEquals(listOf("mon", "sun"), ids)
    }

    @Test
    fun `selecting a day narrows the list to that day`() {
        val wednesday = monday.plusDays(2)
        val turns = listOf(turn("mon", monday), turn("wed", wednesday), turn("thu", monday.plusDays(3)))

        val ids = weekAgenda(turns, monday, today = monday, selected = wednesday).rowIds()

        assertEquals(listOf("wed"), ids)
        // The month's short name is the platform's — "SEP" on a bare JVM,
        // "SEPT" on the phone — so the assertion stops before it.
        val heading = weekAgenda(turns, monday, today = monday, selected = wednesday)
            .headers()
            .single()
        assertTrue(heading, heading.startsWith("WEDNESDAY 9 "))
    }

    @Test
    fun `paging to another week shows that week, not this one`() {
        val nextWeek = monday.plusWeeks(1)
        val turns = listOf(turn("thisweek", monday), turn("nextweek", nextWeek.plusDays(2)))

        val ids = weekAgenda(turns, nextWeek, today = monday, selected = null).rowIds()

        assertEquals(listOf("nextweek"), ids)
    }

    // ─── Headings ─────────────────────────────────────────────

    @Test
    fun `today, tomorrow and yesterday are named rather than dated`() {
        val today = monday.plusDays(2)
        val turns = listOf(
            turn("yest", today.minusDays(1)),
            turn("today", today),
            turn("tom", today.plusDays(1)),
            turn("later", today.plusDays(2)),
        )

        val headers = weekAgenda(turns, monday, today = today, selected = null).headers()

        assertEquals(listOf("YESTERDAY", "TODAY", "TOMORROW"), headers.take(3))
        assertTrue(headers.last(), headers.last().startsWith("FRIDAY 11 "))
    }

    /** Overdue is a row with an amber date, never a heading that names you late. */
    @Test
    fun `nothing is ever headed LATE or OVERDUE`() {
        val today = monday.plusDays(3)
        val items = weekAgenda(listOf(turn("late", monday)), monday, today, selected = null)

        assertTrue(items.headers().none { it.contains("LATE") || it.contains("OVERDUE") })
        assertEquals(listOf("late"), items.rowIds())
        assertTrue(items.headers().single().startsWith("MONDAY 7 "))
    }

    // ─── The sections that are not days ───────────────────────

    @Test
    fun `as-needed turns show on the current week only, and not once a day is picked`() {
        val turns = listOf(turn("whenever", null))

        val thisWeek = weekAgenda(turns, monday, today = monday, selected = null)
        assertEquals(listOf("whenever"), thisWeek.idsUnder("NO DATE"))

        val otherWeek = weekAgenda(turns, monday.plusWeeks(1), today = monday, selected = null)
        assertTrue(otherWeek.isEmpty())

        val oneDay = weekAgenda(turns, monday, today = monday, selected = monday)
        assertTrue(oneDay.isEmpty())
    }

    /** Done is keyed on the day you did it, which is routinely not the day it was due. */
    @Test
    fun `a done turn lands on the day it was done, not the day it was due`() {
        val doneOn = monday.plusDays(1)
        val turns = listOf(turn("done", due = monday.plusWeeks(2), doneOn = doneOn))

        val thisWeek = weekAgenda(turns, monday, today = monday, selected = null)
        assertEquals(listOf("done"), thisWeek.idsUnder("DONE THIS CYCLE"))

        val weekItWasDue = weekAgenda(turns, monday.plusWeeks(2), today = monday, selected = null)
        assertTrue(weekItWasDue.isEmpty())
    }

    @Test
    fun `done rows come last`() {
        val turns = listOf(
            turn("done", due = monday, doneOn = monday),
            turn("open", due = monday),
        )

        val headers = weekAgenda(turns, monday, today = monday, selected = null).headers()

        assertEquals(listOf("TODAY", "DONE THIS CYCLE"), headers)
    }

    // ─── Invariants ───────────────────────────────────────────

    @Test
    fun `no turn is ever drawn twice, whatever day of the week it is`() {
        for (offset in 0L..6L) {
            val today = monday.plusDays(offset)
            val weekStart = today.with(DayOfWeek.MONDAY)
            val turns = (-3L..10L).map { turn("d$it", today.plusDays(it)) } +
                turn("none", null) +
                turn("done", due = today, doneOn = today)

            val ids = weekAgenda(turns, weekStart, today, selected = null).rowIds()

            assertEquals("duplicate row on ${today.dayOfWeek}", ids.distinct().size, ids.size)
        }
    }

    @Test
    fun `an empty window produces nothing at all, not an empty heading`() {
        val items = weekAgenda(
            listOf(turn("elsewhere", monday.plusWeeks(3))),
            monday,
            today = monday,
            selected = null,
        )

        assertTrue(items.isEmpty())
    }
}
