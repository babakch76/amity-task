package com.taskflow.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {

    @Test
    fun `no long dashes anywhere in it`() {
        GUIDE.forEach {
            val text = it.title + it.body
            assertFalse(it.title, text.contains('—') || text.contains('–'))
        }
    }

    @Test
    fun `every section stays short enough to read in passing`() {
        GUIDE.forEach { assertTrue(it.title, it.body.length <= 180) }
    }

    @Test
    fun `every section has a heading of its own`() {
        assertTrue(GUIDE.map { it.title }.toSet().size == GUIDE.size)
    }
}
