package com.taskflow.app.data.local

import android.content.Context

/**
 * Which board sections you folded away, per household, on this phone.
 *
 * "Everyone else" and "Recently done" can fold; "Yours" cannot, because it is
 * what the board is for. A section you never touched starts folded only on a
long board, so a small household never meets the feature (see [isFolded]);
 * once you tap it, your choice is kept here and wins.
 *
 * **On the device, not on the account**, for the same reason as
 * [LastHousehold] and `Appearance`: how much of the board you like to see is
 * about this phone in this hand. It is kept across sign-out because it holds
 * nothing about anyone, only a household id and a yes or no.
 */
object BoardFolds {
    private const val FILE = "taskflow_board_folds"

    /** Above this many rows, a section you never touched starts folded. */
    const val FOLD_AFTER = 4

    /** Whether a section of [rowCount] rows is folded, given what you last chose. */
    fun isFolded(chosen: Boolean?, rowCount: Int): Boolean = chosen ?: (rowCount > FOLD_AFTER)

    /** Your choice for [section] in [groupId], or null if you never made one. */
    fun chosen(context: Context, groupId: String, section: String): Boolean? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val key = key(groupId, section)
        return if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    }

    fun choose(context: Context, groupId: String, section: String, folded: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(groupId, section), folded)
            .apply()
    }

    private fun key(groupId: String, section: String) = "$groupId:$section"
}
