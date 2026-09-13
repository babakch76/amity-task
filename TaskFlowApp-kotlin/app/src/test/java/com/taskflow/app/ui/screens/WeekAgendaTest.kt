package com.taskflow.app.ui.screens

import com.taskflow.app.data.model.Group
import com.taskflow.app.data.model.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The date buckets behind **My week**.
 *
 * These exist because of a real crash: a turn due tomorrow fell into TOMORROW
 * *and* into NEXT WEEK AND BEYOND when today was a Sunday, and a LazyColumn
 * throws on a duplicate key rather than merely drawing the row twice. The
 * boundary is a one-line expression whose failure mode is fatal, which is the
 * combination worth a test.
 */
class WeekAgendaTest {

    private val group = Group(id = "g1", name = "Flat 3B", description = "", createdBy = "u1")

    private fun turn(id: String, due: LocalDate?, open: Boolean = true) = MyTurn(
        occurrence = Occurrence(
            id = id,
            choreId = "c-$id",
            groupId = group.id,
            assignedTo = "u1",
            status = if (open) "open" else "done",
            dueDate = due?.atStartOfDay(ZoneId.systemDefault())?.toOffsetDateTime(),
            doneAt = if (open) null else OffsetDateTime.now(ZoneOffset.UTC),
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

    /** The crash, from the day it happened on. */
    @Test
    fun `a turn due tomorrow appears once when today is a Sunday`() {
        val sunday = LocalDate.of(2026, 9, 13)
        val items = weekAgenda(listOf(turn("t", sunday.plusDays(1))), sunday, showAllLater = false)

        assertEquals(listOf("t"), items.rowIds())
        assertEquals(listOf("TOMORROW"), items.headers())
    }

    /** And on every other weekday, for the same reason. */
    @Test
    fun `no turn is ever bucketed twice, whatever day it is`() {
        val monday = LocalDate.of(2026, 9, 7)
        for (offset in 0L..6L) {
            val today = monday.plusDays(offset)
            val turns = (-3L..14L).map { turn("d$it", today.plusDays(it)) } + turn("none", null)
            val ids = weekAgenda(turns, today, showAllLater = true).rowIds()
            assertEquals("duplicate row on ${today.dayOfWeek}", ids.distinct().size, ids.size)
            assertEquals("lost a row on ${today.dayOfWeek}", turns.size, ids.size)
        }
    }

    @Test
    fun `overdue rides at the top of today rather than in a bucket of its own`() {
        val today = LocalDate.of(2026, 9, 9)
        val items = weekAgenda(
            listOf(turn("today", today), turn("late", today.minusDays(2))),
            today,
            showAllLater = false,
        )

        assertTrue("LATE" !in items.headers())
        assertEquals(listOf("late", "today"), items.idsUnder("TODAY"))
    }

    @Test
    fun `an as-needed turn goes under NO DATE, and done rows come last`() {
        val today = LocalDate.of(2026, 9, 9)
        val items = weekAgenda(
            listOf(turn("done", today, open = false), turn("whenever", null), turn("today", today)),
            today,
            showAllLater = false,
        )

        assertEquals(listOf("TODAY", "NO DATE", "DONE THIS CYCLE"), items.headers())
        assertEquals(listOf("whenever"), items.idsUnder("NO DATE"))
        assertEquals(listOf("done"), items.idsUnder("DONE THIS CYCLE"))
    }

    @Test
    fun `beyond this week collapses to five rows with the rest behind a button`() {
        val today = LocalDate.of(2026, 9, 7)
        val far = (1L..8L).map { turn("f$it", today.plusWeeks(1).plusDays(it)) }

        val collapsed = weekAgenda(far, today, showAllLater = false)
        assertEquals(5, collapsed.idsUnder("NEXT WEEK AND BEYOND").size)
        assertEquals(3, collapsed.filterIsInstance<AgendaItem.ShowAll>().single().hidden)

        val expanded = weekAgenda(far, today, showAllLater = true)
        assertEquals(8, expanded.idsUnder("NEXT WEEK AND BEYOND").size)
        assertTrue(expanded.none { it is AgendaItem.ShowAll })
    }

    @Test
    fun `an empty bucket is absent rather than an empty heading`() {
        val today = LocalDate.of(2026, 9, 9)
        val items = weekAgenda(listOf(turn("only", today)), today, showAllLater = false)

        assertEquals(listOf("TODAY"), items.headers())
    }
}
