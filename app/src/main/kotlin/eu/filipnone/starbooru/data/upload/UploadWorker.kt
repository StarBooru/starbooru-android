package eu.filipnone.starbooru.data.upload

import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.filipnone.starbooru.R
import eu.filipnone.starbooru.StarBooruApp
import eu.filipnone.starbooru.core.Post
import eu.filipnone.starbooru.core.UploadProgress
import eu.filipnone.starbooru.data.Account
import eu.filipnone.starbooru.data.AccountState
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends what waits on the upload list, [AT_ONCE] at a time, as a foreground
 * service with a notification, so it goes on after the app closes. One run
 * goes at a time. A new batch joins the run going, or starts the next one.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    /** What one run has done so far. */
    private class Run {
        val tally = AtomicReference(Tally())

        /** Items the server couldn't be reached for: sent again in a later run. */
        val again: MutableSet<String> = Collections.synchronizedSet(HashSet())
    }

    private class Flight(val attempt: Int, val job: Job)

    override suspend fun doWork(): Result {
        val app = applicationContext
        val queue = UploadQueue.get(app)
        val account = (app as? StarBooruApp)?.graph?.account ?: return Result.success()
        // The session is read at the app's start; a run started by the system may come first.
        withTimeoutOrNull(START_WAIT_MS) { account.state.first { it != AccountState.Starting } }
        // Only one runs at a time, so anything still marked as sent was left by one that died.
        queue.requeue()
        if (queue.state.value.items.none { it.status == UploadStatus.Waiting }) return Result.success()

        UploadNotifications.channel(app)
        val stop = WorkManager.getInstance(app).createCancelPendingIntent(id)
        val run = Run()
        try {
            coroutineScope {
                val shown = launch { show(queue, stop) }
                pump(queue, account, run)
                shown.cancel()
            }
        } catch (e: CancellationException) {
            // Stop in the notification ends them all. When the system stops
            // the work (no network, a time limit) they wait for the next run.
            if (stoppedByUser()) queue.stopPending() else queue.requeue()
            throw e
        }
        val tally = run.tally.get()
        queue.finished(tally)
        if (!tally.empty && !inFront()) UploadNotifications.finished(app, tally)
        val later = queue.state.value.items.any { it.status == UploadStatus.Waiting && it.id in run.again }
        return if (later) Result.retry() else Result.success()
    }

    /**
     * Whether Stop in the notification cancelled the run. The system stops
     * runs too, and starts them again later. Before Android 12 only the
     * work's state tells the two apart.
     */
    private fun stoppedByUser(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP
        } else {
            try {
                WorkManager.getInstance(applicationContext).getWorkInfoById(id).get()?.state == WorkInfo.State.CANCELLED
            } catch (_: Exception) {
                false
            }
        }

    /** Keeps the notification current while the run lasts. */
    private suspend fun show(
        queue: UploadQueue,
        stop: PendingIntent,
    ) {
        var last: Overall? = null
        var first = true
        while (true) {
            val overall = queue.state.value.overall()
            // A change of a whole percent or of the file being sent; the speed alone waits.
            val moved = first || overall?.finished != last?.finished || overall?.files != last?.files || overall?.percent != last?.percent
            if (moved) {
                foreground(UploadNotifications.sending(applicationContext, overall, stop))
                last = overall
                first = false
            }
            delay(NOTIFY_EVERY_MS)
        }
    }

    private suspend fun foreground(notification: android.app.Notification) {
        val info =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(UploadNotifications.SENDING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                ForegroundInfo(UploadNotifications.SENDING_ID, notification)
            }
        try {
            setForeground(info)
        } catch (_: IllegalStateException) {
            // Not allowed from the background on Android 12 and later: the
            // run goes on as ordinary work, without its notification.
        }
    }

    /** Sends items until none waits, starting one as a place frees up. Stops those cancelled on their way. */
    private suspend fun pump(
        queue: UploadQueue,
        account: Account,
        run: Run,
    ) = coroutineScope {
        val flying = HashMap<String, Flight>()
        while (true) {
            val now = queue.state.value
            val each = flying.entries.iterator()
            while (each.hasNext()) {
                val (key, flight) = each.next()
                val item = now.get(key)
                val still = item != null && item.status is UploadStatus.Sending && item.attempt == flight.attempt
                if (!flight.job.isActive) {
                    each.remove()
                } else if (!still) {
                    flight.job.cancel()
                    each.remove()
                }
            }
            for (item in queue.claim(AT_ONCE, run.again)) {
                val job =
                    launch {
                        val outcome = send(queue, account, item)
                        if (queue.finish(item.id, item.attempt, outcome)) {
                            run.tally.updateAndGet { it.plus(outcome) }
                            if (outcome == Outcome.Again) run.again += item.id
                        }
                    }
                flying[item.id] = Flight(item.attempt, job)
            }
            if (flying.isEmpty()) break
            select {
                flying.values.forEach { f -> f.job.onJoin {} }
                queue.wake.onReceive {}
            }
        }
    }

    /** One item, from its first byte to the server's answer. */
    private suspend fun send(
        queue: UploadQueue,
        account: Account,
        item: UploadItem,
    ): Outcome {
        val res = applicationContext.resources
        val server = account.api.value?.server().orEmpty()
        val limit = account.info.value?.maxUploadBytes?.let { UploadQueue.limitOf(it) } ?: 0L
        val plan = item.plan ?: return Outcome.Failed(res.getString(R.string.upload_gone))
        return try {
            when (val source = item.source) {
                is UploadSource.Local -> {
                    val file = queue.fileOf(item.id)
                    when {
                        !file.isFile -> Outcome.Failed(res.getString(R.string.upload_gone))

                        Accept.tooBig(file.length(), limit) -> Outcome.Failed(Words.tooBig(res, limit))

                        else -> {
                            val progress = Progress(queue, item)
                            val post =
                                account.call { api ->
                                    api.uploadFile(
                                        file.absolutePath,
                                        source.name,
                                        plan.tags,
                                        plan.rating,
                                        plan.source,
                                        plan.autotag,
                                        progress,
                                    )
                                }
                            Outcome.Posted(post.id, thumbOf(post))
                        }
                    }
                }

                is UploadSource.Link -> {
                    val post = account.call { api -> api.importUrl(source.url, plan.tags, plan.rating, null, plan.autotag) }
                    Outcome.Posted(post.id, thumbOf(post))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (refusal(e) == Refusal.Unreachable && item.tries + 1 < TRIES) return Outcome.Again
            when (val outcome = Words.outcome(res, e, server, limit)) {
                is Outcome.Duplicate -> outcome.copy(thumb = outcome.postId?.let { thumbOf(account, it) })
                else -> outcome
            }
        }
    }

    /** The picture of the post a file already is, for its tile. */
    private suspend fun thumbOf(
        account: Account,
        id: Long,
    ): Thumb? =
        try {
            thumbOf(account.call { it.post(id) })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun thumbOf(post: Post): Thumb? = post.thumbUrl?.let { Thumb(it, post.sha256) }

    private suspend fun inFront(): Boolean =
        withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }

    /** Hears the core's progress, on its own thread, and tells the list. Never throws. */
    private class Progress(private val queue: UploadQueue, private val item: UploadItem) : UploadProgress {
        private val meter = Meter(SystemClock.elapsedRealtime())

        override fun sent(
            bytes: ULong,
            total: ULong,
        ) {
            try {
                val done = UploadQueue.limitOf(bytes)
                val whole = UploadQueue.limitOf(total)
                val speed = synchronized(meter) { meter.seen(done, SystemClock.elapsedRealtime()) }
                queue.sent(item.id, item.attempt, done, whole, speed)
            } catch (_: RuntimeException) {
            }
        }
    }

    companion object {
        private const val WORK = "uploads"
        private const val START_WAIT_MS = 30_000L
        private const val NOTIFY_EVERY_MS = 1_000L

        /** Has a worker go through the list once the network is there: after the one running, if any. */
        fun schedule(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_S, TimeUnit.SECONDS)
                    .build()
            try {
                WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            } catch (_: IllegalStateException) {
                // WorkManager not set up, as in a test: nothing runs.
            }
        }

        private const val BACKOFF_S = 30L
    }
}
