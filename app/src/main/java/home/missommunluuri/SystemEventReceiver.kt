package home.missommunluuri

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        val reason = when (action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> "boot"
            Intent.ACTION_MY_PACKAGE_REPLACED -> "package update"
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) "bluetooth on" else return
            }
            else -> return
        }

        Log.i("SystemEventReceiver", "Received action: $action, triggering re-arm (reason: $reason)")

        val pendingResult = goAsync()
        val wakeManager = WakeScanManager(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                wakeManager.rearmIfEnabled(reason)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
