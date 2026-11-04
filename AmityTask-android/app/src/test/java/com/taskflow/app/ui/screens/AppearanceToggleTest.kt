package com.taskflow.app.ui.screens

import com.taskflow.app.ui.theme.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceToggleTest {

    @Test
    fun `the toggle reads light, system, dark from left to right`() {
        assertEquals(
            listOf("Light", "System", "Dark"),
            APPEARANCE_ORDER.map { it.label },
        )
    }

    @Test
    fun `every appearance has exactly one place on the toggle`() {
        assertEquals(AppearanceMode.entries.toSet(), APPEARANCE_ORDER.toSet())
        assertEquals(AppearanceMode.entries.size, APPEARANCE_ORDER.size)
    }
}
