package com.taskflow.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class HouseholdNameTest {

    @Test
    fun `the limit matches the server's`() {
        // householdNameMax in groups.go.
        assertEquals(40, HOUSEHOLD_NAME_MAX)
    }

    @Test
    fun `length is counted the way the server counts it`() {
        // The server trims, then counts characters, not UTF-16 units.
        assertEquals(7, nameLength("  Flat 3B "))
        assertEquals(6, nameLength("Casa 🏠"))
        assertEquals(40, nameLength("é".repeat(40)))
    }
}
