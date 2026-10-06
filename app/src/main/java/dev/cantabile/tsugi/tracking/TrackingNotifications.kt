package dev.cantabile.tsugi.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import dev.cantabile.tsugi.MainActivity
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.data.stopsAwayLabel

/** What the ongoing notification shows on each poll. */
data class LiveState(
    val minutes: Long,
    /** 0–100: how far the bus has come since tracking started. */
    val progress: Int,
    val destination: String?,
    val stopName: String,
    val loadLabel: String,
    val nextMinutes: Long?,
    val stopsAway: Int? = null,
)

object TrackingNotifications {
    private const val CHANNEL_LIVE = "live_tracking"
    private const val CHANNEL_ALERTS = "bus_alerts"
    const val ONGOING_ID = 1
    private const val ALERT_ID = 2

    fun ensureChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_LIVE, "Live bus tracking", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "The countdown for a bus you're tracking"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_ALERTS, "Bus alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Heads-up when a tracked bus is close or arriving"
                    enableVibration(true)
                },
            ),
        )
    }

    /** The persistent countdown. On Android 16+ it's promoted to a Live Update with a progress bar. */
    fun ongoing(context: Context, target: Tracked, state: LiveState?): Notification {
        val short = state?.let { if (it.minutes < 1) "Arr" else "${it.minutes} min" }
        val title = when {
            state == null -> "${target.serviceNo} · finding your bus…"
            state.minutes < 1 -> "${target.serviceNo} is arriving"
            else -> "${target.serviceNo} in ${state.minutes} min"
        }
        val text = listOfNotNull(
            state?.destination?.let { "to $it" },
            "at ${state?.stopName ?: target.stopCode}",
        ).joinToString(" · ")
        val sub = listOfNotNull(
            state?.stopsAway?.let(::stopsAwayLabel),
            state?.loadLabel?.ifEmpty { null },
            state?.nextMinutes?.let { "next in $it min" },
        ).joinToString(" · ").ifEmpty { null }

        val builder = Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(sub)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setContentIntent(openStop(context, target.stopCode))
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_close), "Stop", stop(context)).build(),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            // Promotion to a Live Update (status-bar chip, lock screen) arrived in 16 QPR2 (36.1).
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                builder.setRequestPromotedOngoing(true)
                short?.let(builder::setShortCriticalText)
            }
            builder.setStyle(
                Notification.ProgressStyle()
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(100)))
                    .setStyledByProgress(true)
                    .setProgressIndeterminate(state == null)
                    .setProgress(state?.progress ?: 0)
                    .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_bus)),
            )
        }
        return builder.build()
    }

    /** A heads-up alert; replaces the previous alert rather than stacking. */
    fun alert(context: Context, target: Tracked, title: String, text: String) {
        val notification = Notification.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_bus)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openStop(context, target.stopCode))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ALERT_ID, notification)
    }

    private fun openStop(context: Context, stopCode: String): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_STOP, stopCode)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun stop(context: Context): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, BusTrackingService::class.java).setAction(BusTrackingService.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
