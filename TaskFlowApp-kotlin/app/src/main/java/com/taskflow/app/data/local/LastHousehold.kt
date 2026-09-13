package com.taskflow.app.data.local

import android.content.Context

/**
 * Which household this phone opens on.
 *
 * The app no longer lands on a list of households and asks which one you
 * meant; it opens the board of the one you were last looking at, and the
 * switcher in its app bar is how you change your mind. That needs one fact to
 * survive a restart, and this is it.
 *
 * **On the device, not on the account.** Which flat you last had open is a
 * property of this phone in this person's hand — the same reasoning that keeps
 * `Appearance` local. Syncing it would also mean a write to the server every
 * time somebody glanced at their other household.
 *
 * Nothing here is trusted: the stored id may name a household you have since
 * left, or one belonging to the account that was signed in before yours. The
 * caller resolves it against the group list it actually has and falls back to
 * the first household — see `HouseholdTab`.
 *
 * A plain SharedPreferences file, like `Appearance` and `LocalHints` next door.
 * One string written on a tap does not need DataStore.
 */
object LastHousehold {
    private const val FILE = "taskflow_last_household"
    private const val KEY = "group_id"

    fun get(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)

    fun set(context: Context, groupId: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, groupId)
            .apply()
    }

    /**
     * Forgotten on sign-out, so the next person to use the phone does not open
     * on a household that was never theirs — even for the moment it takes to
     * be resolved away.
     */
    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }
}
