# Missommunluuri

Missommunluuri is an Android application that serves as a BLE-triggered alarm. It listens for specific Bluetooth Low Energy (BLE) advertisements to trigger a wake-up alarm on the device.

## Features

- BLE Wake Trigger: The app listens for a specific Service UUID and Device Token in BLE advertisements.
- Customizable Alarm: Users can select custom alarm sounds using the system ringtone picker.
- Reliable Triggering: Uses exact alarms and foreground services to ensure the alarm sounds even when the device is idle.
- Simple Configuration: Provides a JSON snippet for easy integration with Linux-based BLE advertisement scripts.

## Requirements

- Android 8.0 (API level 26) or higher.
- Bluetooth and Location/Scan permissions.
- Bluetooth must be enabled for wake listening to function.

## Setup

1. Grant the necessary permissions and ensure Bluetooth is on.
2. Configure the Service UUID and Device Token in the Advanced Configuration section if needed.
3. Use the provided JSON snippet in your BLE advertisement source (e.g., a Linux machine with a BLE adapter).
4. Enable "Wake Listening" in the app.

## Development

The project is built using Kotlin and Jetpack Compose.

- Build: `./gradlew assembleDebug`
- Test: `./gradlew test`
