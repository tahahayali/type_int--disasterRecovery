# typFirstResponder - First Responder BLE App

## 🚀 What Was Created

A complete duplicate of the BLE emergency app, but customized for **First Responders** with the following modifications:

---

## 📱 Key Differences from Original App

### **1. Branding & Theme**
- **App Name**: `typFirstResponder`
- **Package**: `com.example.firstresponder`
- **Theme**: Blue color scheme (primary: `#1976D2`, accent: `#2196F3`)
- **Welcome Text**: Shows "typFirstResponder" at the top

### **2. Simplified Signup**
- **Only asks for name** (no age, height, weight, medical conditions)
- Sends `type: "first_responder"` to backend (hidden from user)
- Clean, minimal blue-themed signup screen

### **3. Auto-Location Broadcasting**
- **Automatically sends GPS location every 30 seconds** to backend
- Starts immediately when app launches (after permissions)
- Location broadcast happens in background continuously

### **4. Modified Features**
- ✅ **Removed**: Emergency button and questionnaire
- ✅ **Added**: BEEP button (sends Type 5 message)
- ✅ **Kept**: All BLE mesh networking functionality
- ✅ **Kept**: Manual location/battery sending
- ✅ **Kept**: Text messaging (7 chars max)
- ✅ **Kept**: Message relay and cloud sync

### **5. UI Changes**
- Blue-themed cards and buttons
- Status bar shows "Auto-sending location every 30s"
- Simplified header with first responder branding
- Blue accent colors throughout

---

## 🗂️ File Structure

```
appFirstResponder/
├── build.gradle.kts              # Build configuration
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml       # App manifest with permissions
    ├── java/com/example/firstresponder/
    │   ├── BLEMessage.kt         # Message encoding/decoding (20 bytes)
    │   ├── BLEManager.kt         # BLE advertising/scanning
    │   ├── MainActivity.kt       # Main UI (auto-location every 30s)
    │   ├── SignupActivity.kt     # Simplified signup (name only)
    │   ├── ApiService.kt         # Backend API (type='first_responder')
    │   ├── MessageStore.kt       # Local message storage
    │   ├── LocationHelper.kt     # GPS tracking
    │   ├── BatteryHelper.kt      # Battery monitoring
    │   └── UserPreferences.kt    # User data storage (simplified)
    └── res/
        ├── layout/
        │   ├── activity_main.xml       # Main screen layout
        │   └── activity_signup.xml     # Simplified signup layout
        ├── values/
        │   ├── colors.xml              # Blue theme colors
        │   ├── strings.xml             # App strings
        │   └── themes.xml              # Material theme (blue)
        ├── values-night/
        │   └── themes.xml              # Dark mode theme
        └── xml/
            ├── network_security_config.xml
            ├── backup_rules.xml
            └── data_extraction_rules.xml
```

---

## ⚙️ How It Works

### **On App Launch:**
1. Check if registered → if not, show signup (name only)
2. Request Bluetooth + Location permissions
3. Start BLE scanning and advertising
4. **Start auto-location timer (broadcasts GPS every 30s)**
5. Listen for BLE messages from nearby devices

### **Auto-Location Broadcasting:**
```kotlin
// Every 30 seconds:
1. Get current GPS coordinates
2. Create Type 1 (Location) BLE message
3. Broadcast over BLE mesh
4. Store locally
5. Upload to server if internet available
```

### **Manual Actions:**
- **📍 Send Location**: Manually send current GPS
- **🔋 Send Battery**: Send battery percentage + time remaining
- **🔔 Send BEEP**: Broadcast Type 5 alert message
- **💬 Send Message**: Send 7-character text message

### **BLE Mesh Network:**
- All messages auto-relay up to 3 hops
- Deduplication prevents duplicate displays
- Works completely offline
- Syncs to cloud when internet available

---

## 🎨 Blue Theme Colors

```xml
<color name="blue_primary">#1976D2</color>
<color name="blue_primary_dark">#0D47A1</color>
<color name="blue_primary_light">#42A5F5</color>
<color name="blue_accent">#2196F3</color>
<color name="light_blue">#E3F2FD</color>
```

---

## 🔗 Backend Integration

### **Signup API:**
```json
POST http://fennecs.duckdns.org:5000/api/signup
{
  "uuid": "8U4A",
  "type": "first_responder",  ← Hidden from user
  "name": "John Doe"
}
```

### **Location Data Upload:**
```json
POST http://fennecs.duckdns.org:5000/api/byte_string
{
  "messages": [
    "00000001000000016747a0000101f4ba5a07cd2e51"  ← 20-byte hex message
  ]
}
```

---

## 🚀 Building & Running

### **In Android Studio:**
1. Open project root in Android Studio
2. Sync Gradle files
3. Select `appFirstResponder` module
4. Run on device (requires API 23+, Bluetooth, GPS)

### **Gradle Build:**
```bash
./gradlew :appFirstResponder:assembleDebug
```

### **Install APK:**
```bash
adb install appFirstResponder/build/outputs/apk/debug/appFirstResponder-debug.apk
```

---

## 📊 Comparison: Original vs First Responder

| Feature | Original (Victim) | First Responder |
|---------|------------------|-----------------|
| **Theme** | Red/Orange | Blue |
| **Signup** | Name, age, height, weight, medical | Name only |
| **Auto-Location** | Every 5 mins (background) | **Every 30s + upload** |
| **Emergency Button** | ✅ Yes (4 questions) | ❌ No |
| **BEEP Button** | ❌ No | ✅ Yes |
| **User Type** | `victim` | `first_responder` |
| **BLE Mesh** | ✅ Yes | ✅ Yes |
| **Message Relay** | ✅ Yes | ✅ Yes |
| **Cloud Sync** | ✅ Yes | ✅ Yes |

---

## 🔧 Configuration

### **Change Auto-Location Interval:**
Edit `MainActivity.kt`:
```kotlin
// Line ~143: Change 30000 (30s) to desired milliseconds
locationHandler.postDelayed(locationRunnable!!, 30000)
```

### **Change Backend URL:**
Edit `ApiService.kt`:
```kotlin
private const val BASE_URL = "http://fennecs.duckdns.org:5000"
```

---

## 📝 Next Steps

1. **Test on physical device** (emulator GPS is unreliable)
2. **Grant all permissions** when prompted
3. **Verify location uploads** every 30 seconds
4. **Test BLE mesh** with multiple devices
5. **Check backend** receives `type: "first_responder"`

---

## ✅ What's Included

- ✅ Complete source code with new package name
- ✅ Blue Material Design theme
- ✅ Simplified signup (name only)
- ✅ Auto-location every 30s
- ✅ BEEP button (Type 5 message)
- ✅ All BLE mesh networking
- ✅ All helper classes (Location, Battery, etc.)
- ✅ Resource files (layouts, colors, strings)
- ✅ Build configuration
- ✅ AndroidManifest with permissions

---

## 🎯 Use Case

**First responders can use this app to:**
1. Automatically broadcast their location every 30 seconds
2. Relay emergency messages from victims via BLE mesh
3. Send manual alerts (BEEP) to nearby devices
4. Track their position in real-time on backend dashboard
5. Maintain mesh network in disaster zones

---

**Version:** 1.0  
**Created:** 2025  
**Package:** `com.example.firstresponder`  
**Min SDK:** 23 (Android 6.0)  
**Target SDK:** 34

