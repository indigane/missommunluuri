package home.missommunluuri

import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

class WakeScanManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter
    private val scanner = adapter?.bluetoothLeScanner

    private val sharedUuid = UUID.fromString("7d8f6a4e-1d3b-4a6b-9e5d-c8d72d10b4a1")

    fun arm(tokenHex: String) {
        if (scanner == null) {
            Log.e("WakeScanManager", "BLE Scanner not available")
            return
        }

        val tokenBytes = hexToBytes(tokenHex)
        if (tokenBytes.size != 8) {
            Log.e("WakeScanManager", "Invalid token length")
            return
        }

        val payloadPrefix = byteArrayOf(0x01) + tokenBytes
        val payloadMask = ByteArray(9) { 0xFF.toByte() }

        val filter = ScanFilter.Builder()
            .setServiceData(ParcelUuid(sharedUuid), payloadPrefix, payloadMask)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val pendingIntent = getPendingIntent()

        try {
            scanner.startScan(listOf(filter), settings, pendingIntent)
            Log.i("WakeScanManager", "BLE scan armed with token $tokenHex")
        } catch (e: SecurityException) {
            Log.e("WakeScanManager", "Missing permissions for startScan", e)
        }
    }

    fun disarm() {
        if (scanner == null) return
        val pendingIntent = getPendingIntent()
        try {
            scanner.stopScan(pendingIntent)
            Log.i("WakeScanManager", "BLE scan disarmed")
        } catch (e: SecurityException) {
            Log.e("WakeScanManager", "Missing permissions for stopScan", e)
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, BleWakeReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        val result = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            result[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }
}
