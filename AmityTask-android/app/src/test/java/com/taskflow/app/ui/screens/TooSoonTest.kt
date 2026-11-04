package com.taskflow.app.ui.screens

import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.tooSoon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A turn cannot be marked done before halfway to its day (B-25). The server
 * says when; the phone only compares and explains.
 */
class TooSoonTest {

    private val now = OffsetDateTime.of(2026, 11, 4, 18, 0, 0, 0, ZoneOffset.UTC)

    private fun turn(canDoFrom: OffsetDateTime?, status: String = "open") = Occurrence(
        id = "o", choreId = "c", groupId = "g", assignedTo = "u", status = status,
        dueDate = now.plusDays(10), canDoFrom = canDoFrom,
    )

    @Test
    fun `a first turn, or an as-needed one, is never too soon`() {
        assertFalse(turn(canDoFrom = null).tooSoon(now))
    }

    @Test
    fun `too soon until the date the server gave, and not after`() {
        assertTrue(turn(now.plusDays(5)).tooSoon(now))
        assertTrue(turn(now.plusMinutes(1)).tooSoon(now))
        assertFalse(turn(now.minusMinutes(1)).tooSoon(now))
    }

    @Test
    fun `a done turn is not too soon, whatever its date`() {
        assertFalse(turn(now.plusDays(5), status = "done").tooSoon(now))
    }

    @Test
    fun `the line says when, as a day`() {
        // The phone's zone is whatever the test JVM has; keep the dates well
        // inside a day so the wording does not depend on it.
        val noon = OffsetDateTime.of(2026, 11, 4, 12, 0, 0, 0, ZoneOffset.UTC)
        assertEquals(
            "It was done not long ago. You can mark it done again later today.",
            tooSoonLine(noon.plusHours(3), noon),
        )
        assertEquals(
            "It was done not long ago. You can mark it done again tomorrow.",
            tooSoonLine(noon.plusDays(1), noon),
        )
        assertEquals(
            "It was done not long ago. You can mark it done again from Mon 9 Nov.",
            tooSoonLine(noon.plusDays(5), noon),
        )
    }
}
