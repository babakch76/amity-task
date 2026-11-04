package com.taskflow.app.ui.screens

import com.taskflow.app.data.model.Occurrence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * The board's last section. It used to be every completion ever made, oldest
 * first, so today's sat at the bottom under months of others.
 */
class RecentlyDoneTest {

    private val now = OffsetDateTime.of(2026, 11, 1, 20, 0, 0, 0, ZoneOffset.UTC)

    private fun row(id: String, doneAt: OffsetDateTime?, status: String = "done") =
        BoardRow.OccurrenceRow(
            Occurrence(
                id = id, choreId = "c", groupId = "g", assignedTo = "u",
                status = status, doneBy = doneAt?.let { "u" }, doneAt = doneAt,
            ),
        )

    @Test
    fun `keeps the last week, newest first`() {
        val rows = listOf(
            row("sept", now.minusDays(48)),
            row("yesterday", now.minusDays(1)),
            row("today", now.minusHours(1)),
            row("six-days", now.minusDays(6)),
        )
        assertEquals(
            listOf("today", "yesterday", "six-days"),
            recentlyDone(rows, now).map { (it as BoardRow.OccurrenceRow).occurrence.id },
        )
    }

    @Test
    fun `leaves out open turns and completions with no time`() {
        val rows = listOf(
            row("open", null, status = "open"),
            row("undated", null),
            row("recent", now.minusHours(2)),
        )
        assertEquals(
            listOf("recent"),
            recentlyDone(rows, now).map { (it as BoardRow.OccurrenceRow).occurrence.id },
        )
    }
}
