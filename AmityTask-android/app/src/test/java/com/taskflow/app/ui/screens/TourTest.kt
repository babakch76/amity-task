package com.taskflow.app.ui.screens

import com.taskflow.app.ui.components.DockTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TourTest {

    private val today = LocalDate.of(2026, 11, 2)
    private val flat = SampleFlat(today, ZoneOffset.UTC)

    @Test
    fun `no long dashes anywhere in it`() {
        val words = TOUR.flatMap { listOf(it.title, it.body) } +
            listOf(PRACTICE_PROMPT, PRACTICE_PASSED, PRACTICE_DONE)
        words.forEach { assertFalse(it, it.contains('—') || it.contains('–')) }
    }

    @Test
    fun `every stop can be read at a glance`() {
        TOUR.forEach { assertTrue(it.title, it.body.length <= 140) }
    }

    @Test
    fun `no two stops share a title`() {
        assertEquals(TOUR.size, TOUR.map { it.title }.toSet().size)
    }

    @Test
    fun `it opens in the middle of the screen and ends lit on your face, on Mine`() {
        assertNull(TOUR.first().target)
        assertEquals(TourTarget.FACE, TOUR.last().target)
        assertEquals(DockTab.MINE, TOUR.last().tab)
    }

    @Test
    fun `it says it is a sample and that nothing real is touched`() {
        assertTrue(TOUR.first().body.contains("sample flat"))
        assertTrue(TOUR.first().body.contains("nothing here touches your real chores"))
    }

    @Test
    fun `it goes through the pages in the dock's order and never comes back to one`() {
        val pages = TOUR.map { it.tab }.dropLast(1).distinct()
        assertEquals(listOf(DockTab.MINE, DockTab.HOUSE, DockTab.FEED), pages)
    }

    @Test
    fun `every part of the app has a stop, and only one`() {
        val lit = TOUR.mapNotNull { it.target }
        assertEquals(lit.size, lit.toSet().size)
        listOf(
            TourTarget.DOCK_MINE, TourTarget.WEEK, TourTarget.UP_NEXT, TourTarget.ROW,
            TourTarget.SWIPE_ROW, TourTarget.SWITCHER, TourTarget.FACES, TourTarget.BOOK,
            TourTarget.FEED_VIEWS, TourTarget.ADD, TourTarget.FACE,
        ).forEach { assertTrue(it.name, it in lit) }
    }

    @Test
    fun `the swipe can be tried, and says both ways`() {
        val swipe = TOUR.single { it.practice }
        assertEquals(TourTarget.SWIPE_ROW, swipe.target)
        assertTrue(swipe.body.contains("left to pass it on"))
        assertTrue(swipe.body.contains("right to mark"))
    }

    @Test
    fun `the sample's turns fall inside the coming week, so the dots are there to see`() {
        listOf(flat.upNext, flat.alsoYours, flat.maya).forEach { turn ->
            val date = turn.date!!
            assertTrue(date.isAfter(today) && date.isBefore(today.plusDays(7)))
        }
    }

    @Test
    fun `up next is the soonest of your own turns`() {
        assertTrue(flat.upNext.date!!.isBefore(flat.alsoYours.date))
        assertEquals(SAMPLE_TURN, flat.upNext.occurrence.choreName)
    }

    @Test
    fun `the sample shows the arrows the tour talks about`() {
        assertTrue(flat.upNext.occurrence.passTo != null)
    }

    @Test
    fun `there is a turn of someone else's, for whose turn it is to mean something`() {
        assertFalse(flat.maya.occurrence.assignedTo == flat.upNext.occurrence.assignedTo)
    }

    @Test
    fun `every chore on show belongs to the sample`() {
        val ids = flat.chores.map { it.id }
        listOf(flat.upNext, flat.alsoYours, flat.maya).forEach {
            assertTrue(it.occurrence.choreId in ids)
        }
    }

    @Test
    fun `the booking is still to come`() {
        assertTrue(flat.booking.endsAt.toLocalDate().isAfter(today))
    }
}
