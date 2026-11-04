package com.taskflow.app.data.local

import android.content.Context

/**
 * Whether the month view on Mine shows likely turns: the rings for turns that
 * will probably be yours, worked out from the rotation.
 *
 * On unless someone turns it off, from the switch in the month popup. Some
 * people find a month full of rings more worrying than useful, since a likely
 * turn can move whenever someone covers, passes or goes away.
 *
 * **On the device, not the account**, like [LastHousehold] and the folds: it is
 * how this person likes to look at their own calendar, and nobody else sees it.
 */
object LikelyTurns {
    private const val FILE = "taskflow_month_view"
    private const val KEY = "show_likely"

    fun shown(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setShown(context: Context, shown: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY, shown)
            .apply()
    }
}
