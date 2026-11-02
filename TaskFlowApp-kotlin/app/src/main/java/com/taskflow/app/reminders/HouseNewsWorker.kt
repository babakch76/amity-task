package com.taskflow.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.taskflow.app.MainActivity
import com.taskflow.app.R
import com.taskflow.app.TaskFlowApp
import com.taskflow.app.data.model.ActivityEvent
import com.taskflow.app.data.model.Occurrence
import com.taskflow.app.data.model.Task
import com.taskflow.app.data.remote.RetrofitClient
import retrofit2.Response
import java.io.IOException
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/**
 * Looks for news every so often, with the app open or closed. See [passedToMe] and
 * [doneByOthers] for what counts.
 *
 * This is a poll and not a push: there is no push service, and adding one would
 * mean an outside account and a server change. Android runs it about every fifteen
 * minutes at best, and later on a sleeping phone. That delay suits it. Nothing is
 * told until its undo window is over anyway, and nothing here is urgent.
 *
 * It only ever reads. A phone the owner has force-stopped runs nothing until it is
 * next opened, which is Android's rule and not ours.
 */
class HouseNewsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val me = (applicationContext as TaskFlowApp).tokenManager.getUserId() ?: return Result.success()
        val store = HouseNewsStore(applicationContext)
        return try {
            look(me, store)
            Result.success()
        } catch (e: IOException) {
            // Nothing has been moved on, so the next try picks up the same news.
            Log.d(TAG, "no news this time: ${e.message}")
            Result.retry()
        }
    }

    private suspend fun look(me: String, store: HouseNewsStore) {
        val api = RetrofitClient.getInstance().groupApi
        val now = OffsetDateTime.now()

        // Held, not dropped: the marks stay put, so this goes out once the window ends.
        val quiet = api.getMe().bodyOrThrow().let { QuietHours.parse(it.quietFrom, it.quietTo) }
        if (quiet.covers(now.toLocalTime())) return

        val groups = api.listMyGroups().bodyOrThrow()

        // All fetched before anything is told. A household that failed halfway
        // must not have its news skipped by the marks moving past it.
        val occurrences = groups.flatMap { api.listOccurrences(it.id).bodyOrThrow() }
        val passSince = store.passSince() ?: now
        val passes = passedToMe(occurrences, me, passSince, now)

        val doneSince = store.doneSince()
        val watchingDone = store.doneOn(me) && doneSince != null
        val events = mutableListOf<ActivityEvent>()
        val tasks = mutableListOf<Task>()
        if (watchingDone) {
            for (group in groups) {
                events += api.listActivity(group.id, doneSince!!.toInstant().toString()).bodyOrThrow()
                tasks += api.listTasks(group.id).bodyOrThrow()
            }
        }

        passes.forEach { tell(applicationContext, it) }
        store.setPassSince(passes.mapNotNull { it.passedAt }.maxOrNull()?.takeIf { it > passSince } ?: passSince)

        if (watchingDone) {
            val news = doneByOthers(events, tasks, me, doneSince!!, now)
            if (news.isNotEmpty()) tell(applicationContext, news, doneSince)
            settled(events, doneSince, now).mapNotNull { it.createdAt }.maxOrNull()?.let(store::setDoneSince)
        }
    }

    /** A 401 has already signed this phone out, so there is nothing to retry for. */
    private fun <T> Response<T>.bodyOrThrow(): T =
        body().takeIf { isSuccessful } ?: throw IOException("HTTP ${code()}")

    private fun tell(context: Context, occurrence: Occurrence) {
        val (title, body) = passWords(occurrence)
        post(context, CHANNEL_PASSED, "${occurrence.id}:${occurrence.passedAt}".hashCode(), title, body)
    }

    private fun tell(context: Context, news: List<DoneNews>, since: OffsetDateTime) {
        val (title, body) = doneWords(news)
        post(context, CHANNEL_DONE, "done:$since".hashCode(), title, body)
    }

    private fun post(context: Context, channel: String, id: Int, title: String, body: String) {
        // Same as a reminder: posting without the permission throws.
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        ensureChannels(context)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    companion object {
        private const val TAG = "HouseNewsWorker"
        private const val WORK_NAME = "house_news"
        private const val CHANNEL_PASSED = "turns_passed"
        private const val CHANNEL_DONE = "housemates_chores"
        private const val EVERY_MINUTES = 15L

        /** At signing in. Safe to call every time: the running schedule is kept. */
        fun start(context: Context, userId: String) {
            HouseNewsStore(context).start(userId)
            val request = PeriodicWorkRequestBuilder<HouseNewsWorker>(EVERY_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** At signing out. */
        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            HouseNewsStore(context).clearMarks()
        }

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService<NotificationManager>() ?: return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PASSED,
                    "Turns passed to you",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "When a housemate is busy and a turn becomes yours." },
            )
            // LOW makes no sound. It is in the shade when you look, and it is
            // not worth interrupting anybody for someone else's chore.
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DONE,
                    "Housemates' chores",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "When someone does a chore, if you turned this on in You." },
            )
        }
    }
}
