/**
 * Entry point of the whole pipeline: an [android.service.notification.NotificationListenerService]
 * that Android calls for every notification posted on the device (once the user has granted
 * notification access to this app).
 *
 * Responsibilities:
 * - Filter out notifications that must never be forwarded as-is: group summaries (which
 *   duplicate the content of the individual notifications they bundle) are dropped here.
 * - Extract a normalized [app.notificationbridge.model.NotificationData] from the raw
 *   [android.service.notification.StatusBarNotification] (title, body text - including
 *   `MessagingStyle`/big-text/text-lines variants -, app label, category, ongoing/silent flags).
 * - Hand that data to [app.notificationbridge.queue.BridgeRuntime.enqueue], which owns all
 *   filtering policy (allowed apps, calls, duplicates, etc.) from that point on.
 * - Promote itself to a foreground service with a low-priority status notification, so the
 *   OS is less likely to kill the listener process while the app is in the background - this
 *   matters most on OEM skins (e.g. MIUI) with aggressive background-process management. In
 *   dumbphone mode that notification moves to a minimum-importance channel
 *   ([NotificationChannels.STATUS_QUIET]) and is swapped live when the setting changes.
 * - Ignore notifications posted by this app itself, so the status/failure notifications can never
 *   feed back into the bridge (the app is also hidden from the allowed-apps list).
 * - Tell [app.notificationbridge.queue.BridgeRuntime] when Android actually binds/unbinds the
 *   listener. That is different from the permission being listed as granted, and is the state to
 *   look at when notifications silently stop after a reinstall.
 *
 * Assumptions:
 * - The system only calls [onNotificationPosted] after the user has explicitly granted
 *   notification access; there is no in-app fallback if that permission is missing.
 * - A caller notification (native or VoIP) is expected to be posted with
 *   `Notification.CATEGORY_CALL`, which is the standard Android convention. Apps that don't
 *   follow it won't be recognized as calls by [app.notificationbridge.queue.BridgeRuntime].
 *
 * Limitations:
 * - `startForeground()` can silently fail without `POST_NOTIFICATIONS` (Android 13+) or under
 *   OEM restrictions; the listener keeps working, it just loses the foreground-priority boost.
 */
package app.notificationbridge.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import app.notificationbridge.BridgeApplication
import app.notificationbridge.MainActivity
import app.notificationbridge.R
import app.notificationbridge.model.NotificationData
import app.notificationbridge.queue.BridgeRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NotificationBridgeService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )
    private var settingsJob: Job? = null
    private val appNameCache = ConcurrentHashMap<String, String>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        BridgeRuntime.refreshAccess()
        BridgeRuntime.setListenerConnected(true)
        BridgeRuntime.log("Notification listener connected")

        // Re-post the foreground notification whenever dumbphone mode is toggled, so the switch
        // between the normal and the minimal status notification takes effect immediately.
        val repo = (application as BridgeApplication).settings
        settingsJob?.cancel()
        settingsJob = serviceScope.launch(Dispatchers.Main) {
            repo.settings
                .map { it.dumbphoneMode }
                .distinctUntilChanged()
                .collect { quiet -> startForegroundStatus(quiet) }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        settingsJob?.cancel()
        BridgeRuntime.refreshAccess()
        BridgeRuntime.setListenerConnected(false)
        BridgeRuntime.log("Notification listener disconnected")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    // Promotes the listener to a foreground service with a persistent, low-priority
    // status notification. Without this, MIUI (and stock Android's background limits)
    // can kill this process while the app is in the background, silently dropping
    // any notifications that arrive until the process is relaunched. In dumbphone mode the
    // notification moves to a minimum-importance channel so it stays out of the way.
    private fun startForegroundStatus(quiet: Boolean) {
        NotificationChannels.ensureCreated(this)

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // NotificationCompat (not Notification.Builder(context, channelId), which needs API 26)
        // because minSdk is 25.
        val notification = NotificationCompat.Builder(
            this,
            if (quiet) NotificationChannels.STATUS_QUIET else NotificationChannels.STATUS
        )
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_text))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_MIN else NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationChannels.FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NotificationChannels.FOREGROUND_NOTIFICATION_ID, notification)
            }
        }.onFailure {
            // Missing POST_NOTIFICATIONS (API 33+) or a MIUI restriction can make this
            // throw; the listener keeps working, it just won't get the foreground boost.
            BridgeRuntime.log("startForeground failed: ${it.message}")
        }
    }

    override fun onNotificationPosted(
        statusBarNotification: StatusBarNotification
    ) {
        // Never forward this app's own notifications (status + failure alert). Without this,
        // ticking this app in the allowed list would create a feedback loop.
        if (statusBarNotification.packageName == packageName) return

        val notification = statusBarNotification.notification

        // Group-summary notifications (e.g. WhatsApp's "N new messages" bundling
        // entry) duplicate the content of the individual notifications they
        // summarize. Forwarding them too is what causes the same message to
        // arrive twice on the receiver.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) {
            BridgeRuntime.log(
                "Skipped ${statusBarNotification.packageName}: group summary notification"
            )
            return
        }

        val extras = notification.extras

        val title = extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            ?.takeIf { it.isNotBlank() }

        val text = extractNotificationText(extras)

        val appName = appNameCache.computeIfAbsent(statusBarNotification.packageName) { packageName ->
            runCatching {
                val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(applicationInfo).toString()
            }.getOrDefault(packageName)
        }

        val notificationData = NotificationData(
            packageName = statusBarNotification.packageName,
            appName = appName,
            title = title,
            text = text,
            timestamp = statusBarNotification.postTime,
            category = notification.category,
            notificationKey = statusBarNotification.key,
            isOngoing = statusBarNotification.isOngoing,
            isSilent = isSilentSafely(statusBarNotification.key)
        )

        serviceScope.launch {
            BridgeRuntime.enqueue(notificationData)
        }
    }

    private fun extractNotificationText(
        extras: Bundle
    ): String? {
        val bigText = extras
            .getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }

        if (bigText != null) {
            return bigText
        }

        val textLines = extras
            .getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(separator = "\n")
            ?.takeIf { it.isNotBlank() }

        if (textLines != null) {
            return textLines
        }

        return extras
            .getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    private fun isSilentSafely(
        notificationKey: String
    ): Boolean {
        return runCatching {
            val ranking = Ranking()
            val rankingAvailable = currentRanking.getRanking(
                notificationKey,
                ranking
            )

            rankingAvailable &&
                ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
        }.getOrDefault(false)
    }
}
