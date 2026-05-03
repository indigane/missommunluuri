package home.missommunluuri

import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID

sealed class ArmResult {
    object Armed : ArmResult()
    data class Failed(val message: String, val errorCode: Int? = null) : ArmResult()
}

class WakeScanManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    fun isBluetoothEnabled(): Boolean {
        return adapter?.isEnabled == true
    }

    fun arm(serviceUuidStr: String, tokenHex: String): ArmResult {
        val currentAdapter = bluetoothManager.adapter
        if (currentAdapter == null) {
            Log.e("WakeScanManager", "Bluetooth adapter not available")
            return ArmResult.Failed("Bluetooth adapter not available")
        }
        if (!currentAdapter.isEnabled) {
            Log.e("WakeScanManager", "Bluetooth is disabled")
            return ArmResult.Failed("Bluetooth is disabled")
        }

        val scanner = currentAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e("WakeScanManager", "BLE Scanner not available (is Bluetooth on?)")
            return ArmResult.Failed("BLE Scanner not available")
        }

        val tokenBytes = Utils.hexToBytes(tokenHex)
        if (tokenBytes.size != 8) {
            Log.e("WakeScanManager", "Invalid token length")
            return ArmResult.Failed("Invalid token length")
        }

        val serviceUuid = UUID.fromString(serviceUuidStr)
        val parcelUuid = ParcelUuid(serviceUuid)

        // RESTORED: Filter by Service Data (version 0x01 + token) instead of Service UUID.
        // This restores compatibility with Linux advertisers that omit Service UUIDs.
        val payloadPrefix = byteArrayOf(0x01) + tokenBytes
        val payloadMask = ByteArray(9) { 0xFF.toByte() }

        val filter = ScanFilter.Builder()
            .setServiceData(parcelUuid, payloadPrefix, payloadMask)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val pendingIntent = getPendingIntent()

        return try {
            val resultCode = scanner.startScan(listOf(filter), settings, pendingIntent)
            if (resultCode == 0 || resultCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                if (resultCode == ScanCallback.SCAN_FAILED_ALREADY_STARTED) {
                    Log.i("WakeScanManager", "BLE scan was already started; treating as success")
                } else {
                    Log.i("WakeScanManager", "BLE scan armed with Service Data for $serviceUuidStr")
                }
                ArmResult.Armed
            } else {
                val message = mapScanError(resultCode)
                Log.e("WakeScanManager", "startScan failed with code $resultCode: $message")
                ArmResult.Failed(message, resultCode)
            }
        } catch (e: SecurityException) {
            Log.e("WakeScanManager", "Missing permissions for startScan", e)
            ArmResult.Failed("Missing Bluetooth permissions")
        } catch (e: Exception) {
            Log.e("WakeScanManager", "Unexpected error during startScan", e)
            ArmResult.Failed("Error: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    fun disarm() {
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.w("WakeScanManager", "Scanner not available for disarm")
            return
        }
        val pendingIntent = getPendingIntent()
        try {
            scanner.stopScan(pendingIntent)
            Log.i("WakeScanManager", "BLE scan disarmed")
        } catch (e: SecurityException) {
            Log.e("WakeScanManager", "Missing permissions for stopScan", e)
        } catch (e: Exception) {
            Log.e("WakeScanManager", "Error during stopScan", e)
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

    private fun mapScanError(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth error"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning unsupported"
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of hardware resources"
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> "Scanning too frequently"
            else -> "Unknown error ($errorCode)"
        }
    }
}
