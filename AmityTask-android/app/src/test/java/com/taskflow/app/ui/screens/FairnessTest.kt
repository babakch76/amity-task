package com.taskflow.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FairnessTest {

    private val people = setOf(FAIR_MAYA, FAIR_YOU, FAIR_SAM)

    @Test
    fun `every worked example comes out even`() {
        FAIR_CASES.filter { it.turns.isNotEmpty() }.forEach { case ->
            val counts = case.turns.groupingBy { it.who }.eachCount()
            assertEquals(case.title, people, counts.keys)
            assertEquals(case.title, 1, counts.values.toSet().size)
        }
    }

    @Test
    fun `the examples only use the three people the page introduces`() {
        FAIR_CASES.flatMap { it.turns }.forEach { assertTrue(it.who, it.who in people) }
    }

    @Test
    fun `the two worked cases are the ones the server test plays`() {
        // fairness_page_test.go asserts these same sequences against the API.
        val worked = FAIR_CASES.filter { it.turns.isNotEmpty() }
        assertEquals(
            listOf(FAIR_MAYA, FAIR_MAYA, FAIR_YOU, FAIR_SAM, FAIR_YOU, FAIR_SAM),
            worked[0].turns.map { it.who },
        )
        assertEquals(
            listOf(FAIR_MAYA, FAIR_SAM, FAIR_YOU, FAIR_MAYA, FAIR_YOU, FAIR_SAM),
            worked[1].turns.map { it.who },
        )
    }
}
