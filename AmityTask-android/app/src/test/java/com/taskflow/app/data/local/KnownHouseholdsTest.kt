package com.taskflow.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class KnownHouseholdsTest {

    private val known = mapOf("a" to "Flat 3B", "b" to "Lake House")

    @Test
    fun `a household still on the list is not reported`() {
        assertEquals(emptyList<String>(), KnownHouseholds.missing(known, setOf("a", "b")))
    }

    @Test
    fun `a household that has gone is reported by name`() {
        assertEquals(listOf("Lake House"), KnownHouseholds.missing(known, setOf("a")))
    }

    @Test
    fun `a household joined since last time is not a loss`() {
        assertEquals(emptyList<String>(), KnownHouseholds.missing(known, setOf("a", "b", "c")))
    }

    @Test
    fun `a first run has nothing to compare with`() {
        assertEquals(emptyList<String>(), KnownHouseholds.missing(emptyMap(), setOf("a")))
    }
}
