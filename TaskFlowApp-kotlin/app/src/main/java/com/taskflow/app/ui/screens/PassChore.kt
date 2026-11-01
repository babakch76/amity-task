package com.taskflow.app.ui.screens

import android.content.Context
import com.taskflow.app.data.model.Chore
import com.taskflow.app.data.model.MemberInfo
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.ScheduleType
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Who a busy pass would hand this occurrence to, so the confirm can name them
 * before the request is sent.
 *
 * The server's answer ([Occurrence.passTo]), not a prediction: who is next
 * depends on who is owed a skip for covering, which the phone is not told, and
 * a copy of the rule here is how the dialog comes to promise one person while
 * the pass goes to another.
 *
 * Null when there is nobody to pass to. The button is hidden in that case
 * rather than offering a pass that would come straight back.
 */
fun passReceiver(occurrence: Occurrence, members: List<MemberInfo>): MemberInfo? =
    occurrence.passTo?.let { id -> members.firstOrNull { it.id == id } }

/**
 * The second line of the pass confirmation: what the receiver will be told.
 *
 * Three cases, because promising the wrong date is worse than saying less:
 *
 *  - The chore was already due, so the pass refreshes it to tomorrow. The
 *    server does that, and this is the only case where "due tomorrow" is true.
 *  - It has a date still ahead of it, which the pass leaves alone.
 *  - It is an as-needed chore and has no date at all, so there is nothing to
 *    promise except that it is now their turn.
 */
fun passNoticeLine(occurrence: Occurrence, chore: Chore?, receiver: String): String {
    val due = occurrence.dueDate
    return when {
        chore?.scheduleType == ScheduleType.AS_NEEDED || due == null ->
            "$receiver'll be told it's their turn. Nobody else is notified."

        due.isBefore(OffsetDateTime.now()) ->
            "$receiver'll be told it's due tomorrow. Nobody else is notified."

        else ->
            "$receiver'll be told it's due ${formatDueDate(due)}. Nobody else is notified."
    }
}

/** True when the pass will move the date, which is what "due tomorrow" means. */
fun passRefreshesDate(occurrence: Occurrence): Boolean {
    val due = occurrence.dueDate ?: return false
    return due.isBefore(OffsetDateTime.now())
}

/** Tomorrow, as the receiver will see it. Only used when the pass re-dates. */
fun tomorrowLabel(): String =
    LocalDate.now(ZoneId.systemDefault()).plusDays(1).let { it.toString() }

/**
 * One-off local flags that are nobody's business but this device's.
 *
 * The swipe hint is shown once and then never again, which needs a memory that
 * survives the process but has no place on the server: whether this person has
 * discovered a gesture is not part of the household's data, and syncing it
 * would mean a new device re-teaching someone who already knows.
 */
object LocalHints {
    private const val FILE = "taskflow_hints"
    private const val SWIPE_SEEN = "swipe_hint_dismissed"

    fun swipeHintDismissed(context: Context): Boolean =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SWIPE_SEEN, false)

    fun dismissSwipeHint(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(SWIPE_SEEN, true)
            .apply()
    }
}
