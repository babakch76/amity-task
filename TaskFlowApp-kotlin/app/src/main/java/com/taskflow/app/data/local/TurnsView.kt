package com.taskflow.app.data.local

import android.content.Context

/**
 * Two ways of reading the same set of turns — yours, across every household.
 *
 * [BY_HOUSEHOLD] groups them under the flat they belong to; [BY_WEEK] puts a
 * week strip on top and buckets them by date, which is DESIGN.md §5.11's
 * **My week**. The tab's title follows the mode, so the toggle in the app bar
 * really does switch between *My chores* and *My week*.
 *
 * **Both exist on purpose, for now.** The design has one destination, not two,
 * and the two readings answer the same question differently: *whose flat is
 * this on me for* versus *when is it on me*. Which one a household actually
 * reaches for is a question about people, so it is being put in front of
 * people rather than argued about — and one of the two will be deleted after
 * the interviews rather than both being kept.
 */
enum class TurnsView {
    BY_HOUSEHOLD,
    BY_WEEK;

    /** The tab's title in this mode. */
    val title: String
        get() = when (this) {
            BY_HOUSEHOLD -> "My chores"
            BY_WEEK -> "My week"
        }

    val other: TurnsView
        get() = if (this == BY_HOUSEHOLD) BY_WEEK else BY_HOUSEHOLD
}

/**
 * Which of the two the person last chose, kept on the device beside
 * `Appearance`, `LocalHints` and `LastHousehold` and for the same reason: it is
 * a way of reading a screen on this phone, not a fact about the account or a
 * thing anybody else can see.
 */
object TurnsViewPreference {
    private const val FILE = "taskflow_turns_view"
    private const val KEY = "mode"

    fun get(context: Context): TurnsView {
        val stored = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
        return TurnsView.entries.firstOrNull { it.name == stored } ?: TurnsView.BY_HOUSEHOLD
    }

    fun set(context: Context, view: TurnsView) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, view.name)
            .apply()
    }
}
