/**
 * The three notification channels this app posts to, and the two notification ids it uses.
 * Kept in one place because both [NotificationBridgeService] (the persistent status
 * notification) and [ErrorNotifier] (failure alerts) need them, and channels must exist before
 * anything is posted to them.
 *
 * - [STATUS]: the foreground-service notification, low importance (no sound, no heads-up).
 * - [STATUS_QUIET]: same notification for dumbphone mode, minimum importance - collapsed in the
 *   shade with no status-bar icon on most Android versions. A channel's importance cannot be
 *   raised or lowered by the app after creation, hence a second channel instead of switching.
 * - [ERRORS]: the only channel that can make a sound; used solely when a transfer fails.
 *
 * Channel names are user-visible in system settings, so they come from string resources.
 * Limitation: on Android below 13 a Service/Application context does not follow the in-app
 * per-app language, so these names and the notification texts can appear in the system language.
 */
package app.notificationbridge.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import app.notificationbridge.R

internal object NotificationChannels {
    const val STATUS = "bridge_service_status"
    const val STATUS_QUIET = "bridge_service_status_quiet"
    const val ERRORS = "bridge_errors"

    const val FOREGROUND_NOTIFICATION_ID = 1001
    const val ERROR_NOTIFICATION_ID = 1002

    /** Idempotent; re-creating an existing channel only refreshes its (localized) name. */
    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                STATUS,
                context.getString(R.string.channel_status_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                STATUS_QUIET,
                context.getString(R.string.channel_status_quiet_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                ERRORS,
                context.getString(R.string.channel_error_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
}
