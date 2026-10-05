package app.notificationbridge
import android.app.Application
import app.notificationbridge.data.SettingsRepository
import app.notificationbridge.queue.BridgeRuntime
class BridgeApplication : Application() {
    lateinit var settings: SettingsRepository; private set
    override fun onCreate() { super.onCreate(); settings=SettingsRepository(this); BridgeRuntime.initialize(this,settings) }
}
