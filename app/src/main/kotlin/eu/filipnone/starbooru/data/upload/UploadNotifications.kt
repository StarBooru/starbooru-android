package eu.filipnone.starbooru.data.upload

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import eu.filipnone.starbooru.MainActivity
import eu.filipnone.starbooru.R

/** The notifications of uploads: one while they go, one when they've finished with the app closed. */
object UploadNotifications {
    private const val CHANNEL = "uploads"

    /** The one shown while files go up, which keeps the work running. */
    const val SENDING_ID = 4100

    private const val FINISHED_ID = 4101

    /**
     * What a tap on an upload notification opens the app with. MainActivity
     * shows the Upload page for it.
     */
    const val ACTION_SHOW = "eu.filipnone.starbooru.action.SHOW_UPLOADS"

    /** Whether [intent] came from an upload notification. */
    fun opensUploads(intent: Intent?): Boolean = intent?.action == ACTION_SHOW

    fun channel(context: Context) {
        val res = context.resources
        val channel =
            NotificationChannelCompat
                .Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(res.getString(R.string.upload_channel))
                .setDescription(res.getString(R.string.upload_channel_hint))
                .setShowBadge(false)
                .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    private fun showUploads(context: Context): PendingIntent {
        val open =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_SHOW)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** `Uploading 3 of 7`, how far and how fast, and Stop. */
    fun sending(
        context: Context,
        overall: Overall?,
        stop: PendingIntent,
    ): Notification {
        val res = context.resources
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_upload)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(showUploads(context))
                .addAction(0, res.getString(R.string.upload_stop_all), stop)
        if (overall == null) {
            return builder
                .setContentTitle(res.getString(R.string.upload_uploading))
                .setProgress(0, 0, true)
                .build()
        }
        val at = (overall.finished + 1).coerceAtMost(overall.files)
        val percent = overall.percent
        return builder
            .setContentTitle(res.getString(R.string.upload_sending_count, at, overall.files))
            .setContentText(transferLabel(overall.sent, overall.total, overall.speed) { Words.size(res, it) })
            .setProgress(100, percent ?: 0, percent == null)
            .build()
    }

    /** What a run that finished out of sight did. Needs the permission to notify from Android 13. */
    fun finished(
        context: Context,
        tally: Tally,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val res = context.resources
        val note =
            NotificationCompat
                .Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_upload)
                .setContentTitle(res.getString(R.string.upload_finished))
                .setContentText(Words.summary(res, tally))
                .setContentIntent(showUploads(context))
                .setAutoCancel(true)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(FINISHED_ID, note)
        } catch (_: SecurityException) {
        }
    }

    /** The finished note goes once the page is looked at. */
    fun seen(context: Context) = NotificationManagerCompat.from(context).cancel(FINISHED_ID)
}
