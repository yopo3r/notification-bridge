/**
 * Posts a single user-visible alert when a transfer has failed after every retry. Used only in
 * dumbphone mode, where the app is meant to run unattended and the user would otherwise have no
 * idea that forwarding has silently stopped working.
 *
 * Deliberately conservative: at most one alert per [MIN_INTERVAL_MS] so a dead receiver doesn't
 * produce one alert per queued notification, and nothing is posted if the notification
 * permission (Android 13+) or the app's notifications are disabled. The alert contains no
 * notification content - only a generic "check the receiver" message.
 *
 * Note: this app's own notifications never loop back into the bridge, because
 * [NotificationBridgeService] ignores anything posted by its own package.
 */
package app.notificationbridge.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.notificationbridge.MainActivity
import app.notificationbridge.R

object ErrorNotifier {
    private const val MIN_INTERVAL_MS = 10 * 60 * 1000L

    @Volatile
    private var lastPostedAt = 0L

    // The permission is checked in canPostNotifications(); lint can't see through that helper.
    @SuppressLint("MissingPermission")
    fun notifyTransferFailed(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastPostedAt < MIN_INTERVAL_MS) return
        if (!canPostNotifications(context)) return

        NotificationChannels.ensureCreated(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.ERRORS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.error_notification_title))
            .setContentText(context.getString(R.string.error_notification_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.error_notification_text)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NotificationChannels.ERROR_NOTIFICATION_ID, notification)
        }
        lastPostedAt = now
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
