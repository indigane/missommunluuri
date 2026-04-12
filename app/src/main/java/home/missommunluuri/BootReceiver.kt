package home.missommunluuri

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Re-arming BLE scan after boot")
            val prefs = PrefsManager(context)
            val wakeManager = WakeScanManager(context)

            CoroutineScope(Dispatchers.IO).launch {
                val enabled = prefs.wakeEnabled.first()
                val token = prefs.deviceToken.first()
                val serviceUuid = prefs.serviceUuid.first()

                if (enabled && token != null) {
                    wakeManager.arm(serviceUuid, token)
                }
            }
        }
    }
}
