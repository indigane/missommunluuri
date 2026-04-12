package home.missommunluuri

import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.IntentCompat
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class BleWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, -1)
        if (errorCode != -1) {
            Log.e("BleWakeReceiver", "Scan failed with error code: $errorCode")
            return
        }

        val results = IntentCompat.getParcelableArrayListExtra(intent, BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT, ScanResult::class.java)
        if (results.isNullOrEmpty()) {
            return
        }

        val pendingResult = goAsync()
        val prefs = PrefsManager(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tokenHex = prefs.deviceToken.first() ?: return@launch
                val serviceUuidStr = prefs.serviceUuid.first()
                val lastAccepted = prefs.lastAcceptedTrigger.first()
                val now = SystemClock.elapsedRealtime()

                // Deduplicate: ignore if triggered within last 60 seconds
                if (now - lastAccepted < 60_000) {
                    Log.d("BleWakeReceiver", "Deduplicated trigger")
                    return@launch
                }

                val serviceUuid = UUID.fromString(serviceUuidStr)

                for (result in results) {
                    val serviceData = result.scanRecord?.serviceData?.get(android.os.ParcelUuid(serviceUuid))
                    if (serviceData != null && validatePayload(serviceData, tokenHex)) {
                        Log.i("BleWakeReceiver", "Valid trigger received!")
                        prefs.setLastAcceptedTrigger(now)
                        triggerAlarm(context)
                        break
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun validatePayload(data: ByteArray, tokenHex: String): Boolean {
        if (data.size != 9) return false
        if (data[0] != 0x01.toByte()) return false
        val receivedToken = data.sliceArray(1..8)
        val expectedToken = Utils.hexToBytes(tokenHex)
        return receivedToken.contentEquals(expectedToken)
    }

    private fun triggerAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmTriggerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + 1000

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                // Fallback to inexact or guide user to settings in real app
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
