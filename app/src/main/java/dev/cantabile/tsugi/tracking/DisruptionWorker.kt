package dev.cantabile.tsugi.tracking

import android.app.Notification
import dev.cantabile.tsugi.data.TrainLine
import dev.cantabile.tsugi.data.TrainMessageDto
import dev.cantabile.tsugi.data.TrainAlertsDto
import dev.cantabile.tsugi.data.AffectedSegmentDto
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.cantabile.tsugi.MainActivity
import dev.cantabile.tsugi.R
import dev.cantabile.tsugi.TsugiApplication
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.allStopCodes
import dev.cantabile.tsugi.data.disruptionNotice
import dev.cantabile.tsugi.data.linesNear
import dev.cantabile.tsugi.data.toDomain
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Checks LTA's train alerts every 15 minutes (Android's minimum) while disruption alerts are on,
 * and notifies when a line near your saved stops is disrupted, and again when it's back to normal.
 * The same disruption is only announced once.
 */
class DisruptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as TsugiApplication).container
        if (!c.settings.disruptionAlerts.first() || !c.api.hasKey) return Result.success()
        val status = runCatching { c.api.trainServiceAlerts() }.getOrNull()?.value?.toDomain(Instant.now())
            ?: return Result.retry()
        runCatching { c.stops.ensureLoaded() }
        val stations = c.stations.ensureLoaded()
        val saved = c.favourites.favourites.first().flatMap(Favourite::allStopCodes).distinct()
        val myLines = linesNear(saved, { code -> c.stops[code]?.let { it.lat to it.lng } }, stations)
        val notice = disruptionNotice(status, myLines) { code -> stations[code]?.name }
        val last = c.settings.lastDisruptionKey.first()
        when {
            notice != null && notice.key != last -> {
                notify(applicationContext, notice.title, notice.text)
                c.settings.setLastDisruptionKey(notice.key)
            }
            // Only when the network itself has recovered: not just because you unsaved the stops near it.
            notice == null && last.isNotEmpty() && status.segments.isEmpty() && !status.disrupted -> {
                notify(applicationContext, "Trains back to normal", "Your lines are running normally again.")
                c.settings.setLastDisruptionKey("")
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "train_disruptions"
        private const val CHANNEL = "train_disruptions"
        private const val NOTIFICATION_ID = 3

        /** Starts (or keeps) the 15-minute check; call when alerts are turned on and at app start. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DisruptionWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Sends LTA's own sample disruption (API guide, Annex C: North East Line, Boon Keng to Dhoby
         * Ghaut towards HarbourFront) through the real message builder and notification, marked as a
         * test, so you can see what an alert looks like and that notifications get through.
         */
        suspend fun sendTest(context: Context) {
            val c = (context.applicationContext as TsugiApplication).container
            val stations = runCatching { c.stations.ensureLoaded() }.getOrNull()
            val sample = TrainAlertsDto(
                status = 2,
                segments = listOf(AffectedSegmentDto(line = "NEL", direction = "HarbourFront", stations = "NE9,NE8,NE7,NE6", freePublicBus = "NE9,NE8,NE7,NE6")),
                messages = listOf(TrainMessageDto(content = "NEL - Additional travelling time of 20 minutes between Boon Keng and Dhoby Ghaut stations towards HarbourFront station due to a signal fault.")),
            ).toDomain(Instant.now())
            val notice = disruptionNotice(sample, setOf(TrainLine.NEL)) { code -> stations?.get(code)?.name } ?: return
            notify(context, "Test · ${notice.title}", notice.text)
        }

        private fun notify(context: Context, title: String, text: String) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Train disruptions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When an MRT or LRT line near your saved stops is disrupted"
                },
            )
            val open = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_OPEN_TAB, "Saved")
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_train)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
