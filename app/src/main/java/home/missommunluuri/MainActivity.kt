package home.missommunluuri

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
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
import androidx.compose.ui.platform.LocalClipboard
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
            val serviceUuid = prefs.serviceUuid.first()
            if (enabled && token != null) {
                wakeScanManager.arm(serviceUuid, token)
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
    val ringtoneUri by prefs.ringtoneUri.collectAsStateWithLifecycle(initialValue = null)
    val serviceUuidStored by prefs.serviceUuid.collectAsStateWithLifecycle(initialValue = "7d8f6a4e-1d3b-4a6b-9e5d-c8d72d10b4a1")
    val clipboard = LocalClipboard.current

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

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let { intent ->
                IntentCompat.getParcelableExtra(intent, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            if (uri != null) {
                scope.launch {
                    prefs.setRingtoneUri(uri.toString())
                }
            }
        }
    }

    var uuidInput by remember(serviceUuidStored) { mutableStateOf(serviceUuidStored) }
    var tokenInput by remember(deviceToken) { mutableStateOf(deviceToken ?: "") }

    LaunchedEffect(Unit) {
        val currentToken = prefs.deviceToken.first()
        if (currentToken == null) {
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

        // 1. Wake Listening Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (wakeEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wake Listening", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = if (wakeEnabled) "App is armed and listening for BLE triggers" else "App is idle",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = wakeEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !wakeScanManager.isBluetoothEnabled()) {
                            Toast.makeText(context, "Bluetooth is disabled", Toast.LENGTH_SHORT).show()
                            return@Switch
                        }
                        scope.launch {
                            prefs.setWakeEnabled(enabled)
                            if (enabled) {
                                val currentToken = prefs.deviceToken.first()
                                val currentUuid = prefs.serviceUuid.first()
                                if (currentToken != null) {
                                    wakeScanManager.arm(currentUuid, currentToken)
                                }
                            } else {
                                wakeScanManager.disarm()
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Common Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("General Settings", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))

                // Permissions
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Permissions", style = MaterialTheme.typography.bodyLarge)
                        Text("Bluetooth & Notifications", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Button(onClick = { launcher.launch(permissions.toTypedArray()) }) {
                        Text("Grant")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Alarm Sound
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alarm Sound", style = MaterialTheme.typography.bodyLarge)
                        val ringtoneName = remember(ringtoneUri) {
                            if (ringtoneUri == null) "Default"
                            else try {
                                RingtoneManager.getRingtone(context, Uri.parse(ringtoneUri)).getTitle(context)
                            } catch (e: Exception) { "Custom" }
                        }
                        Text(text = ringtoneName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Button(onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let { Uri.parse(it) })
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        }
                        ringtonePickerLauncher.launch(intent)
                    }) {
                        Text("Change")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Test Button
        if (isRinging) {
            Button(
                onClick = {
                    val intent = Intent(context, AlarmRingingService::class.java)
                    intent.action = "STOP_ALARM"
                    context.startService(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("STOP ALARM", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            OutlinedButton(
                onClick = {
                    val permissionStatus = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(context, "Warning: Notification permission recommended for stopping the alarm.", Toast.LENGTH_LONG).show()
                    }
                    val intent = Intent(context, AlarmTriggerReceiver::class.java)
                    context.sendBroadcast(intent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Test Ring Alarm", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Integration Snippet
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Integration", style = MaterialTheme.typography.titleMedium)
                Text("Use this snippet in your Linux trigger script:", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))

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

                TextButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("linux-config", jsonSnippet)))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Copy Snippet")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. Advanced Config
        var showAdvanced by remember { mutableStateOf(false) }

        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "Hide Advanced Configuration" else "Show Advanced Configuration")
        }

        if (showAdvanced) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Advanced", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = uuidInput,
                        onValueChange = { uuidInput = it },
                        label = { Text("Service UUID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Device Token (16 hex chars)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            try {
                                UUID.fromString(uuidInput)
                                if (tokenInput.length != 16 || tokenInput.any { it.digitToIntOrNull(16) == null }) {
                                    throw IllegalArgumentException("Invalid token")
                                }

                                scope.launch {
                                    prefs.setServiceUuid(uuidInput)
                                    prefs.setDeviceToken(tokenInput)
                                    Toast.makeText(context, "Configuration saved", Toast.LENGTH_SHORT).show()

                                    if (wakeEnabled) {
                                        wakeScanManager.disarm()
                                        wakeScanManager.arm(uuidInput, tokenInput)
                                    }
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Invalid UUID or Token format", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save and Apply")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Reliability depends on Bluetooth being enabled and battery optimization settings.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
    }
}
