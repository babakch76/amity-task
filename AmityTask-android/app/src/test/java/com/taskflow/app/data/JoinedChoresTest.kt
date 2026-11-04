package com.taskflow.app.data

import com.taskflow.app.data.model.GroupWithProgress
import com.taskflow.app.data.model.joinedChoresLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinedChoresTest {

    @Test
    fun `joining says which turns you are in, in plain words`() {
        assertEquals("You're in the turns for Kitchen.", joinedChoresLine(listOf("Kitchen")))
        assertEquals(
            "You're in the turns for Kitchen, Bins and Bathroom.",
            joinedChoresLine(listOf("Kitchen", "Bins", "Bathroom")),
        )
    }

    @Test
    fun `joining nothing, or an older server, says nothing extra`() {
        assertNull(joinedChoresLine(emptyList()))
        assertNull(joinedChoresLine(null))
    }

    @Test
    fun `the new-people setting reads as off only when the server says so`() {
        val group = GroupWithProgress(id = "g", name = "Flat", description = "", createdBy = "u")
        // An older server sends nothing: the app shows what it actually does, which is not adding.
        assertFalse(group.addsNewMembers)
        assertTrue(group.copy(addNewMembers = true).addsNewMembers)
        assertFalse(group.copy(addNewMembers = false).addsNewMembers)
    }
}
