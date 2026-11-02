package com.taskflow.app.reminders

import com.taskflow.app.data.model.ActivityEvent
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

class HouseNewsTest {

    private val now = OffsetDateTime.of(2026, 11, 2, 12, 0, 0, 0, ZoneOffset.UTC)
    private val since = now.minusHours(1)

    private fun turn(
        assignedTo: String = "me",
        status: String = "open",
        passedAt: OffsetDateTime? = now.minusMinutes(5),
        passedFrom: String? = "maya",
        needsDate: Boolean = false,
        due: OffsetDateTime? = null,
    ) = Occurrence(
        id = "o",
        choreId = "c",
        groupId = "g",
        assignedTo = assignedTo,
        status = status,
        dueDate = due,
        passedAt = passedAt,
        passedFrom = passedFrom,
        needsDate = needsDate,
        choreName = "Bins",
    )

    private fun event(
        type: String = "occurrence_done",
        actor: String = "maya",
        name: String = "Maya",
        detail: String = "Bins",
        id: String? = "o",
        at: OffsetDateTime? = now.minusMinutes(20),
    ) = ActivityEvent(
        id = "e",
        groupId = "g",
        actorId = actor,
        actorUsername = name,
        eventType = type,
        taskId = id,
        detail = detail,
        createdAt = at,
    )

    private fun task(status: String = "done") =
        Task(id = "o", groupId = "g", title = "Call the landlord", description = "", status = status)

    private fun done(vararg events: ActivityEvent, tasks: List<Task> = emptyList()) =
        doneByOthers(events.toList(), tasks, "me", since, now)

    @Test
    fun `a turn passed to you is news`() {
        assertEquals(1, passedToMe(listOf(turn()), "me", since, now).size)
    }

    @Test
    fun `a pass is held until it can no longer be undone`() {
        val fresh = turn(passedAt = now.minusMinutes(1))
        assertTrue(passedToMe(listOf(fresh), "me", since, now).isEmpty())
        val later = now.plusMinutes(2)
        assertEquals(1, passedToMe(listOf(fresh), "me", since, later).size)
    }

    @Test
    fun `a pass already told is not told twice`() {
        assertTrue(passedToMe(listOf(turn()), "me", now.minusMinutes(5), now).isEmpty())
    }

    @Test
    fun `a pass from before you signed in is not news`() {
        assertTrue(passedToMe(listOf(turn(passedAt = since.minusDays(1))), "me", since, now).isEmpty())
    }

    @Test
    fun `someone else's pass is nobody else's business`() {
        assertTrue(passedToMe(listOf(turn(assignedTo = "sam")), "me", since, now).isEmpty())
    }

    @Test
    fun `a passed turn already done is not news`() {
        assertTrue(passedToMe(listOf(turn(status = "done")), "me", since, now).isEmpty())
    }

    @Test
    fun `a turn that was never passed is not news`() {
        assertTrue(passedToMe(listOf(turn(passedAt = null, passedFrom = null)), "me", since, now).isEmpty())
    }

    @Test
    fun `the words say the date, as the confirm promised`() {
        val (title, body) = passWords(turn(due = now.plusDays(1)))
        assertEquals("Bins was passed to you", title)
        assertTrue(body.startsWith("Due "))
    }

    @Test
    fun `an as-needed turn has no date to say`() {
        assertEquals("It's your turn.", passWords(turn()).second)
    }

    @Test
    fun `when everyone is busy it says you pick the day`() {
        val (title, body) = passWords(turn(needsDate = true, passedFrom = "me"))
        assertEquals("Bins is back with you", title)
        assertEquals("Everyone's busy, so you pick the day.", body)
    }

    @Test
    fun `a pass never names anyone, since the first passer may not be the last`() {
        val (title, body) = passWords(turn(due = now.plusDays(1)))
        assertFalse((title + body).contains("maya", ignoreCase = true))
    }

    @Test
    fun `a housemate's chore is news`() {
        assertEquals(listOf(DoneNews("Maya", "Bins")), done(event()))
    }

    @Test
    fun `your own chore is not news to you`() {
        assertTrue(done(event(actor = "me", name = "You")).isEmpty())
    }

    @Test
    fun `a chore waits out the ten minutes to undo`() {
        assertTrue(done(event(at = now.minusMinutes(9))).isEmpty())
    }

    @Test
    fun `a chore taken back is not news, even if the undo is too fresh to count itself`() {
        val undo = event(type = "occurrence_reopened", at = now.minusMinutes(2))
        assertTrue(done(event(), undo).isEmpty())
    }

    @Test
    fun `done again after being taken back is news`() {
        val undo = event(type = "occurrence_reopened", at = now.minusMinutes(30))
        val again = event(at = now.minusMinutes(20))
        assertEquals(1, done(event(at = now.minusMinutes(40)), undo, again).size)
    }

    @Test
    fun `an undo of one chore does not hide another`() {
        val undo = event(type = "occurrence_reopened", id = "other", at = now.minusMinutes(2))
        assertEquals(1, done(event(), undo).size)
    }

    @Test
    fun `a one-off gets its name from the task`() {
        val event = event(type = "task_updated", detail = "status=done")
        assertEquals(listOf(DoneNews("Maya", "Call the landlord")), done(event, tasks = listOf(task())))
    }

    @Test
    fun `a one-off that was edited and not finished is not news`() {
        val event = event(type = "task_updated", detail = "title")
        assertTrue(done(event, tasks = listOf(task())).isEmpty())
    }

    @Test
    fun `a one-off reopened since is not news`() {
        val event = event(type = "task_updated", detail = "status=done")
        assertTrue(done(event, tasks = listOf(task(status = "todo"))).isEmpty())
    }

    @Test
    fun `nothing else in the feed is news`() {
        assertTrue(done(event(type = "booking_added"), event(type = "chore_created")).isEmpty())
    }

    @Test
    fun `chores come oldest first`() {
        val earlier = event(detail = "Bins", at = now.minusMinutes(30))
        val later = event(detail = "Floor", at = now.minusMinutes(20))
        assertEquals(listOf("Bins", "Floor"), done(later, earlier).map { it.what })
    }

    @Test
    fun `one chore reads as a sentence`() {
        assertEquals("Maya did Bins", doneWords(listOf(DoneNews("Maya", "Bins"))).first)
    }

    @Test
    fun `several go in one notification`() {
        val (title, body) = doneWords(listOf(DoneNews("Maya", "Bins"), DoneNews("Sam", "Floor")))
        assertEquals("2 chores done", title)
        assertEquals("Maya did Bins. Sam did Floor.", body)
    }

    @Test
    fun `no long dashes in anything it says`() {
        val words = passWords(turn()).toList() + passWords(turn(needsDate = true)).toList() +
            doneWords(listOf(DoneNews("Maya", "Bins"))).toList()
        words.forEach { assertFalse(it, it.contains('—') || it.contains('–')) }
    }
}
