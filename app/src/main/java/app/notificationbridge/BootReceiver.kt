package app.notificationbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.notificationbridge.queue.BridgeRuntime

/**
 * Fires on device boot. The NotificationListenerService itself is rebound
 * automatically by the system as long as notification access is still
 * granted - that part needs no code here. This receiver just exists so the
 * app's process (and BridgeRuntime) is guaranteed to start right away on
 * boot rather than waiting for the first notification or a manual app open,
 * and so we get a log line confirming it came back up cleanly.
 *
 * Note: per Android's "stopped app" rules, this only fires once the app has
 * been opened at least once after install; a freshly installed, never-opened
 * app won't receive BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // BridgeApplication.onCreate() already ran when the system created
        // this process to deliver the broadcast, so BridgeRuntime is ready.
        BridgeRuntime.refreshAccess()
        BridgeRuntime.log(
            "Device booted; notification access = ${BridgeRuntime.state.value.notificationAccess}"
        )
    }
}
