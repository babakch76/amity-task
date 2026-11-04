package com.taskflow.app.data.local

import android.content.Context
import com.taskflow.app.data.model.Group
import org.json.JSONObject

/**
 * The households this phone last saw, so it can notice one disappearing.
 *
 * When an owner removes a household its activity feed goes with it, so there is
 * nothing left for the other members to read. Their phones have to work it out
 * for themselves: a household that was on the list last time and is not now, and
 * that this person did not leave here, has been taken away. Without this, it
 * would simply be gone the next time they opened the app, with no explanation.
 *
 * Kept per account, because the phone may be signed in as somebody else next
 * time and their households are not this person's to lose.
 *
 * Only ever used to say something once. Nothing here is trusted as a list of
 * memberships; the server's answer is.
 */
object KnownHouseholds {
    private const val FILE = "taskflow_known_households"

    /** The names of households that were [known] and are no longer current. */
    internal fun missing(known: Map<String, String>, currentIds: Set<String>): List<String> =
        known.filterKeys { it !in currentIds }.values.toList()

    /**
     * Compares [current] with what was stored, stores [current], and returns
     * the names of any households that have gone.
     */
    fun reconcile(context: Context, userId: String, current: List<Group>): List<String> {
        val gone = missing(read(context, userId), current.map { it.id }.toSet())
        write(context, userId, current.associate { it.id to it.name })
        return gone
    }

    /** Called when this person left or removed it, so nobody tells them what they just did. */
    fun forget(context: Context, userId: String, groupId: String) {
        write(context, userId, read(context, userId) - groupId)
    }

    /** Forgotten on sign-out, along with [LastHousehold]. */
    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun read(context: Context, userId: String): Map<String, String> {
        val raw = prefs(context).getString(userId, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun write(context: Context, userId: String, households: Map<String, String>) {
        prefs(context).edit().putString(userId, JSONObject(households).toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
