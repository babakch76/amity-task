package com.taskflow.app.reminders

import android.content.Context
import com.taskflow.app.data.model.ActivityEvent
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.Task
import com.taskflow.app.data.model.isOpen
import com.taskflow.app.ui.screens.UNDO_WINDOW
import com.taskflow.app.ui.screens.formatDueDate
import java.time.Duration
import java.time.OffsetDateTime

/**
 * What other people did that this phone should hear about while the app is closed.
 *
 * [HouseNewsWorker] fetches, and this decides. Two kinds of news:
 *
 *  - **A turn passed to you.** Always on, because the confirm promises the other
 *    person that you will be told.
 *  - **A housemate did a chore.** Off unless you switch it on in You, and all or
 *    nothing: choosing whose chores to hear about would be a way of watching one
 *    person.
 *
 * Both wait out their undo window, so nobody hears about a mis-tap taken back, and
 * both follow the rules in constraints 2, 3 and 7: never to the person who did it,
 * nothing about lateness, and never whose turn it really was. "Maya did the bins"
 * and not "Maya did Sam's bins".
 */

/** Mirrors `passUndoWindow` in chores.go. */
internal val PASS_HOLD: Duration = Duration.ofMinutes(2)

internal data class DoneNews(val who: String, val what: String)

/** Open turns of yours that reached you after [since] and can no longer be undone. */
internal fun passedToMe(
    occurrences: List<Occurrence>,
    me: String,
    since: OffsetDateTime,
    now: OffsetDateTime,
): List<Occurrence> = occurrences.filter {
    val at = it.passedAt
    it.isOpen && it.assignedTo == me && at != null && at.isAfter(since) && !at.isAfter(now.minus(PASS_HOLD))
}

/** Who gets told what about a pass. No name: the first passer may not be the last. */
internal fun passWords(occurrence: Occurrence): Pair<String, String> {
    val name = occurrence.choreName.ifBlank { "A chore" }
    val due = occurrence.dueDate
    return when {
        occurrence.needsDate -> "$name is back with you" to "Everyone's busy, so you pick the day."
        due == null -> "$name was passed to you" to "It's your turn."
        else -> "$name was passed to you" to "Due ${formatDueDate(due)}."
    }
}

/** The events old enough to count: past [since], and past the ten minutes to undo. */
internal fun settled(events: List<ActivityEvent>, since: OffsetDateTime, now: OffsetDateTime): List<ActivityEvent> =
    events.filter {
        val at = it.createdAt
        at != null && at.isAfter(since) && !at.isAfter(now.minus(UNDO_WINDOW))
    }

/**
 * Chores somebody else finished, oldest first. [events] is everything since the
 * last look, newer ones included, because an undo can land after what it undoes.
 *
 * A one-off is a task, and its event carries only `status=done`, so its name comes
 * from [tasks]. A task can be reopened with no window at all, so it has to still
 * be done now.
 */
internal fun doneByOthers(
    events: List<ActivityEvent>,
    tasks: List<Task>,
    me: String,
    since: OffsetDateTime,
    now: OffsetDateTime,
): List<DoneNews> = settled(events, since, now)
    .filter { it.actorId != me }
    .sortedBy { it.createdAt }
    .mapNotNull { event ->
        when (event.eventType) {
            OCCURRENCE_DONE -> DoneNews(event.actorUsername, event.detail)
                .takeUnless { event.undoneIn(events) || event.detail.isBlank() }

            TASK_UPDATED -> tasks
                .firstOrNull { it.id == event.taskId && it.status == "done" }
                ?.takeIf { "status=done" in event.detail }
                ?.let { DoneNews(event.actorUsername, it.title) }

            else -> null
        }
    }

private fun ActivityEvent.undoneIn(events: List<ActivityEvent>): Boolean {
    val done = createdAt ?: return false
    return events.any { later ->
        later.eventType == OCCURRENCE_REOPENED && later.taskId == taskId &&
            later.createdAt?.isAfter(done) == true
    }
}

/** One notification however many there are. It is news, not an alarm. */
internal fun doneWords(news: List<DoneNews>): Pair<String, String> {
    val lines = news.map { "${it.who} did ${it.what}" }
    return when (lines.size) {
        1 -> lines.single() to "It's off the board."
        else -> "${lines.size} chores done" to lines.joinToString(". ", postfix = ".")
    }
}

private const val OCCURRENCE_DONE = "occurrence_done"
private const val OCCURRENCE_REOPENED = "occurrence_reopened"
private const val TASK_UPDATED = "task_updated"

/**
 * How far this phone has read, and whether it wants to hear about chores.
 *
 * Both "since" marks are the server's own times, moved on only once the news has
 * gone out, so a check that fails or falls in quiet hours loses nothing. They start
 * at the moment of signing in or switching on, so nobody gets a backlog.
 *
 * Plain SharedPreferences, like [ReminderStore]: nothing secret, and it is the
 * phone's choice rather than the account's. The switch is kept per person, so it
 * is there again after signing back in.
 */
internal class HouseNewsStore(context: Context) {

    private val prefs = context.getSharedPreferences("house_news", Context.MODE_PRIVATE)

    fun passSince(): OffsetDateTime? = read(KEY_PASS)

    fun setPassSince(at: OffsetDateTime) = write(KEY_PASS, at)

    fun doneSince(): OffsetDateTime? = read(KEY_DONE)

    fun setDoneSince(at: OffsetDateTime) = write(KEY_DONE, at)

    fun doneOn(userId: String): Boolean = prefs.getBoolean(onKey(userId), false)

    fun setDoneOn(userId: String, on: Boolean, now: OffsetDateTime = OffsetDateTime.now()) {
        prefs.edit().putBoolean(onKey(userId), on).apply()
        if (on) setDoneSince(now)
    }

    /** At signing in. Leaves the marks alone if this person is already reading. */
    fun start(userId: String, now: OffsetDateTime = OffsetDateTime.now()) {
        if (passSince() == null) setPassSince(now)
        if (doneOn(userId) && doneSince() == null) setDoneSince(now)
    }

    /** At signing out, so the next person starts from their own sign-in. */
    fun clearMarks() {
        prefs.edit().remove(KEY_PASS).remove(KEY_DONE).apply()
    }

    private fun read(key: String): OffsetDateTime? =
        prefs.getString(key, null)?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() }

    private fun write(key: String, at: OffsetDateTime) {
        prefs.edit().putString(key, at.toString()).apply()
    }

    private fun onKey(userId: String) = "done_on:$userId"

    private companion object {
        const val KEY_PASS = "pass_since"
        const val KEY_DONE = "done_since"
    }
}
