package com.taskflow.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardFoldsTest {

    @Test
    fun `a short section you never touched stays open`() {
        assertFalse(BoardFolds.isFolded(chosen = null, rowCount = BoardFolds.FOLD_AFTER))
    }

    @Test
    fun `a long section you never touched starts folded`() {
        assertTrue(BoardFolds.isFolded(chosen = null, rowCount = BoardFolds.FOLD_AFTER + 1))
    }

    @Test
    fun `opening a long section is remembered`() {
        assertFalse(BoardFolds.isFolded(chosen = false, rowCount = 20))
    }

    @Test
    fun `folding a short section is remembered`() {
        assertTrue(BoardFolds.isFolded(chosen = true, rowCount = 1))
    }
}
