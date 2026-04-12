package home.missommunluuri

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var prefs: PrefsManager
    private lateinit var wakeScanManager: WakeScanManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PrefsManager(this)
        wakeScanManager = WakeScanManager(this)

        setContent {
            val isDark = isSystemInDarkTheme()
            val colorScheme = if (isDark) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(prefs, wakeScanManager)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-arm if enabled, as recommended by spec
        val scope = lifecycleScope
        scope.launch {
            val enabled = prefs.wakeEnabled.first()
            val token = prefs.deviceToken.first()
            if (enabled && token != null) {
                wakeScanManager.arm(token)
            }
        }
    }
}

@Composable
fun MainScreen(prefs: PrefsManager, wakeScanManager: WakeScanManager) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceToken by prefs.deviceToken.collectAsStateWithLifecycle(initialValue = null)
    val deviceSlug by prefs.deviceSlug.collectAsStateWithLifecycle(initialValue = Build.MODEL)
    val wakeEnabled by prefs.wakeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val isRinging by prefs.isRinging.collectAsStateWithLifecycle(initialValue = false)
    val clipboardManager = LocalClipboardManager.current

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION) // Legacy BLE requires location
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            Toast.makeText(context, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permissions required for BLE scanning", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (deviceToken == null) {
            val random = SecureRandom()
            val bytes = ByteArray(8)
            random.nextBytes(bytes)
            val token = bytes.joinToString("") { "%02x".format(it) }
            prefs.setDeviceToken(token)
            prefs.setDeviceSlug(Build.MODEL)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Missommunluuri",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Setup", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Grant permissions and ensure Bluetooth is on.")
                Button(
                    onClick = { launcher.launch(permissions.toTypedArray()) },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text("Request Permissions")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Exact alarm permission (USE_EXACT_ALARM) is included in the manifest. " +
                           "This app is categorized as an alarm app, so this permission is granted automatically at installation. " +
                           "It will not appear in the 'Alarms & Reminders' system settings list.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("2. Copy this snippet to your Linux config:")

                val jsonSnippet = """
                {
                  "devices": {
                    "${deviceSlug ?: "my-phone"}": {
                      "token": "$deviceToken"
                    }
                  }
                }
                """.trimIndent()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = jsonSnippet,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(jsonSnippet))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text("Copy Snippet")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Wake Listening", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = wakeEnabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        prefs.setWakeEnabled(enabled)
                        if (enabled) {
                            deviceToken?.let { wakeScanManager.arm(it) }
                        } else {
                            wakeScanManager.disarm()
                        }
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isRinging) {
            Button(
                onClick = {
                    val intent = Intent(context, AlarmRingingService::class.java)
                    intent.action = "STOP_ALARM"
                    context.startService(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("STOP ALARM")
            }
        } else {
            Button(
                onClick = {
                    val permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Warning: Notification permission is recommended for stopping the alarm via notification.", Toast.LENGTH_LONG).show()
                    }
                    val intent = Intent(context, AlarmTriggerReceiver::class.java)
                    context.sendBroadcast(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Test Ring Button")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Reliability depends on Bluetooth being enabled and battery optimization settings.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
}
