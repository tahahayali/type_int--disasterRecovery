# BLE Message Broadcaster

A pure BLE mesh network app for Android that broadcasts and relays messages without requiring device pairing or connections.

## Features

✨ **No Connections** - Uses BLE advertising/scanning only  
🔄 **Auto Mesh Network** - Messages automatically relay up to 3 hops  
📡 **Always Listening** - Auto-starts on launch  
🎯 **UUID Deduplication** - Each message shown only once  
⚡ **5 Message Types** - Location, Survey, Battery, Text, Beep

## Message Types

Send messages by typing in the input field:

### 1. Text Message
```
Hello!!
```
Just type any text (max 7 bytes)

### 2. Location (GPS Coordinates)
```
loc:40.7128,-74.0060
```
Format: `loc:latitude,longitude`  
Resolution: ~7.5cm accuracy

### 3. Battery Status
```
bat:85,3600
```
Format: `bat:percentage,seconds`  
Example: 85% battery, 3600 seconds (1 hour) remaining

### 4. Survey/Questionnaire
```
survey:1,0,1,1,0
```
Format: `survey:answer1,answer2,...`  
Values: `1`=Yes, `0`=No, `-1`=Unknown  
Max 7 answers

### 5. BEEP Alert
```
beep
```
Sends a simple alert notification

## How It Works

1. **Launch app** → Automatically starts broadcasting & listening
2. **Grant permissions** → Bluetooth, Location required
3. **Send messages** → Type and broadcast
4. **Mesh relay** → Messages automatically propagate through network
5. **Deduplication** → Each unique message displayed once

## Technical Details

- **Message Size:** Exactly 20 bytes
- **BLE Strategy:** Pure advertising/scanning (no connections)
- **Rebroadcast:** Each message relayed max 3 times
- **Rotation:** Messages cycle every 1 second for continuous broadcast

## Message Format

See [`MESSAGE_FORMAT.md`](MESSAGE_FORMAT.md) for complete technical specification.

**Header (12 bytes):**
- 4 bytes: message_id (for deduplication)
- 4 bytes: sender_id (device identifier)
- 4 bytes: timestamp (Unix seconds)

**Payload (8 bytes):**
- 1 byte: type (1-5)
- 7 bytes: type-specific data

## Permissions Required

- `BLUETOOTH` / `BLUETOOTH_ADVERTISE` / `BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `NEARBY_WIFI_DEVICES` (Android 13+)

## Build Requirements

- Android Studio
- Kotlin
- Minimum SDK: 23 (Android 6.0)
- Target SDK: 34

## Dependencies

```gradle
implementation 'com.google.code.gson:gson:2.10.1'
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
```

## Usage

1. Install on 2+ Android devices
2. Grant all permissions when prompted
3. App auto-starts broadcasting
4. Send messages - they propagate automatically!

## Architecture

- **BLEMessage.kt** - Message structure & encoding/decoding
- **BLEManager.kt** - BLE advertising/scanning, mesh relay logic
- **MainActivity.kt** - UI, permissions, user input

## License

MIT License - Feel free to use and modify!

---

**Note:** This app uses pure BLE broadcasting (no Google Nearby Connections API). Perfect for mesh networks where devices don't need to "pair" or "connect".

