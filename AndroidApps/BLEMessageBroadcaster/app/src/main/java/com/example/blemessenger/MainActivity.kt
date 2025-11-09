package com.example.blemessenger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

/**
 * Main Activity that manages the UI and coordinates with NearbyManager
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var bleManager: BLEManager
    private lateinit var userPrefs: UserPreferences
    private lateinit var messageStore: MessageStore
    private lateinit var locationHelper: LocationHelper
    private lateinit var batteryHelper: BatteryHelper
    
    // UI Components
    private lateinit var welcomeText: TextView
    private lateinit var updateProfileButton: MaterialButton
    private lateinit var emergencyButton: MaterialButton
    private lateinit var sendLocationButton: MaterialButton
    private lateinit var sendBatteryButton: MaterialButton
    private lateinit var stopBeepButton: MaterialButton
    private lateinit var deviceNameInput: TextInputEditText
    private lateinit var startButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var statusText: TextView
    private lateinit var messageInput: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var messageHistory: TextView
    
    // State
    private var isRunning = false
    private var deviceName = ""
    private val messageList = ConcurrentLinkedQueue<String>()
    
    // Track seen message UUIDs to prevent duplicates in display
    private val seenMessageUUIDs = mutableSetOf<Int>()
    
    // Emergency state
    private var isInEmergency = false
    private val emergencyHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var emergencyRunnable: Runnable? = null
    private var emergencyAnswers = BooleanArray(4) // 4 yes/no answers
    
    // Beep handling
    private val beepHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var beepRunnable: Runnable? = null
    private val processedBeepUUIDs = mutableSetOf<Int>() // Track which beep messages already triggered alert
    private var currentRingtone: android.media.Ringtone? = null
    
    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBroadcasting()
        } else {
            showPermissionDeniedDialog()
        }
    }
    
    // Emergency questionnaire launcher
    private val emergencyQuestionnaireLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val answers = result.data?.getBooleanArrayExtra("answers")
            if (answers != null && answers.size == 4) {
                emergencyAnswers = answers
                startEmergencyBroadcasting()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Initialize user preferences
            userPrefs = UserPreferences(this)
            
            // Check if user is registered
            if (!userPrefs.isRegistered()) {
                // Redirect to signup
                val intent = Intent(this, SignupActivity::class.java)
                startActivity(intent)
                finish()
                return
            }
            
            setContentView(R.layout.activity_main)
            
            // Initialize managers
            bleManager = BLEManager(this)
            bleManager.setSenderUuid(userPrefs.uuidToSenderId()) // Set correct sender ID
            messageStore = MessageStore(this)
            locationHelper = LocationHelper(this)
            batteryHelper = BatteryHelper(this)
            
            // Initialize UI components
            initializeViews()
            
            // Set up callbacks
            setupBLECallbacks()
            setupLocationTracking()
            
            // Set up click listeners
            setupClickListeners()
            
            // Set up profile UI
            setupProfileUI()
            
            // Generate default device name (use UUID)
            deviceName = userPrefs.getOrGenerateUUID()
            deviceNameInput.setText(deviceName)
            
            // Auto-start broadcasting/listening
            checkPermissionsAndStart()
            
            // Upload stored messages if internet available
            uploadStoredMessagesIfOnline()
            
            // Test encoding (debug)
            TestEncoding.testUuidEncoding()
            TestEncoding.testBuffaloLocation()
            TestEncoding.testBattery()
            
            // Log this device's sender_id
            Log.d("MainActivity", "This device UUID: ${userPrefs.getOrGenerateUUID()}, sender_id: ${userPrefs.uuidToSenderId()}")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Fatal error in onCreate", e)
            Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            // Restart if not running and views are initialized
            if (!isRunning && ::bleManager.isInitialized) {
                checkPermissionsAndStart()
            }
            
            // Refresh profile UI
            if (::userPrefs.isInitialized) {
                setupProfileUI()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onResume", e)
        }
    }
    
    /**
     * Initialize all UI components
     */
    private fun initializeViews() {
        welcomeText = findViewById(R.id.welcomeText)
        updateProfileButton = findViewById(R.id.updateProfileButton)
        emergencyButton = findViewById(R.id.emergencyButton)
        sendLocationButton = findViewById(R.id.sendLocationButton)
        sendBatteryButton = findViewById(R.id.sendBatteryButton)
        stopBeepButton = findViewById(R.id.stopBeepButton)
        deviceNameInput = findViewById(R.id.deviceNameInput)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        messageHistory = findViewById(R.id.messageHistory)
    }
    
    /**
     * Set up profile UI with user name and internet-dependent features
     */
    private fun setupProfileUI() {
        try {
            val name = userPrefs.getName()
            welcomeText.text = "Hello, $name"
            
            // Show/hide update profile button based on internet
            val hasInternet = isInternetAvailable()
            updateProfileButton.visibility = if (hasInternet) View.VISIBLE else View.GONE
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupProfileUI", e)
        }
    }
    
    /**
     * Check if internet is available
     */
    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }
    
    /**
     * Handle emergency button click - launch questionnaire activity
     */
    private fun handleEmergency() {
        val intent = Intent(this, EmergencyQuestionnaireActivity::class.java)
        emergencyQuestionnaireLauncher.launch(intent)
    }
    
    /**
     * Start continuous emergency broadcasting
     */
    private fun startEmergencyBroadcasting() {
        isInEmergency = true
        
        // Update emergency button
        emergencyButton.text = "🚨 EMERGENCY ACTIVE\n(Broadcasting every 30s)"
        emergencyButton.isEnabled = false
        emergencyButton.setBackgroundColor(android.graphics.Color.RED)
        
        // Create the recurring broadcast runnable
        emergencyRunnable = object : Runnable {
            override fun run() {
                if (isInEmergency) {
                    broadcastEmergencyData()
                    // Schedule next broadcast in 30 seconds
                    emergencyHandler.postDelayed(this, 30000)
                }
            }
        }
        
        // Start immediately
        broadcastEmergencyData()
        
        // Schedule next one in 30 seconds
        emergencyHandler.postDelayed(emergencyRunnable!!, 30000)
        
        Toast.makeText(this, "🚨 EMERGENCY MODE: Broadcasting every 30 seconds", Toast.LENGTH_LONG).show()
    }
    
    /**
     * Broadcast all emergency data (questionnaire, location, battery)
     */
    private fun broadcastEmergencyData() {
        Log.d("MainActivity", "📡 Broadcasting emergency data...")
        
        // 1. Send questionnaire answers (Type 2)
        val answers = emergencyAnswers.map { if (it) 0x01 else 0x00 }
        val questionnairePayload = BLEMessage.Payload.Questionnaire(answers)
        sendAndStoreMessage(questionnairePayload)
        
        // 2. Send location (Type 1)
        val location = locationHelper.getLastLocation()
        if (location != null) {
            sendLocationPayload(location.first, location.second)
        } else {
            Log.w("MainActivity", "No location available for emergency broadcast")
        }
        
        // 3. Send battery (Type 3)
        val (percentage, seconds) = batteryHelper.getBatteryInfo()
        sendBatteryPayload(percentage, seconds)
        
        // Upload to server if online
        uploadStoredMessagesIfOnline()
        
        Log.d("MainActivity", "✅ Emergency data broadcasted")
    }
    
    /**
     * Stop emergency broadcasting
     */
    private fun stopEmergencyBroadcasting() {
        isInEmergency = false
        emergencyRunnable?.let {
            emergencyHandler.removeCallbacks(it)
        }
        emergencyButton.text = "🚨 DO YOU HAVE AN EMERGENCY?\n(Tap if YES)"
        emergencyButton.isEnabled = true
    }
    
    /**
     * Setup location tracking
     */
    private fun setupLocationTracking() {
        try {
            locationHelper.onLocationUpdate = { lat, lon ->
                Log.d("MainActivity", "Location updated: $lat, $lon")
                // Auto-send location every 5 mins
                sendLocationPayload(lat, lon)
            }
            locationHelper.startTracking()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up location tracking", e)
        }
    }
    
    /**
     * Send and store a message
     */
    private fun sendAndStoreMessage(payload: BLEMessage.Payload) {
        // Create message ONCE with consistent IDs
        // IMPORTANT: sender_id must be UUID encoded as ASCII bytes → big-endian int
        val message = BLEMessage(
            messageUuid = BLEMessage.generateMessageUuid(),
            senderUuid = userPrefs.uuidToSenderId(),  // ✅ Correct encoding!
            timestamp = BLEMessage.getCurrentTimestamp(),
            payload = payload
        )
        
        // Debug: log the hex string
        val hexString = bytesToHex(message.toBytes())
        Log.d("MainActivity", "📤 Generated message hex (${hexString.length/2} bytes): $hexString")
        Log.d("MainActivity", "   UUID: 0x${String.format("%08X", message.messageUuid)}, Sender: 0x${String.format("%08X", message.senderUuid)}, Time: 0x${String.format("%08X", message.timestamp)}, Type: ${payload.getType()}")
        
        // Broadcast over BLE (pass the complete message)
        bleManager.broadcastMessage(message)
        
        // Store locally (same message)
        messageStore.storeMessage(message)
        
        // Add to display
        addMessageToDisplay(message)
        
        // Upload if online
        uploadStoredMessagesIfOnline()
    }
    
    /**
     * Convert byte array to hex string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Send location payload
     */
    private fun sendLocationPayload(lat: Double, lon: Double) {
        val payload = BLEMessage.Payload.Location(lat, lon)
        sendAndStoreMessage(payload)
    }
    
    /**
     * Send battery payload
     */
    private fun sendBatteryPayload(percentage: Int, seconds: Int) {
        val payload = BLEMessage.Payload.Battery(percentage, seconds)
        sendAndStoreMessage(payload)
    }
    
    /**
     * Upload stored messages to server if online
     */
    private fun uploadStoredMessagesIfOnline() {
        try {
            if (!isInternetAvailable()) {
                Log.d("MainActivity", "No internet, skipping upload")
                return
            }
            
            val messages = messageStore.getAllMessages()
            if (messages.isEmpty()) {
                Log.d("MainActivity", "No messages to upload")
                return
            }
            
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    Log.d("MainActivity", "Uploading messages to server:")
                    messages.forEachIndexed { index, msg ->
                        Log.d("MainActivity", "Message $index: $msg")
                    }
                    
                    val result = ApiService.sendByteStrings(messages)
                    result.onSuccess { response ->
                        Log.d("MainActivity", "✅ Uploaded ${messages.size} messages. Response: $response")
                        messageStore.clearMessages()
                        Toast.makeText(
                            this@MainActivity,
                            "✅ Synced ${messages.size} messages to server",
                            Toast.LENGTH_SHORT
                        ).show()
                    }.onFailure { error ->
                        Log.e("MainActivity", "❌ Failed to upload messages: ${error.message}", error)
                        Toast.makeText(
                            this@MainActivity,
                            "Upload failed: ${error.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Exception during upload", e)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in uploadStoredMessagesIfOnline", e)
        }
    }
    
    /**
     * Set up BLEManager callbacks
     */
    private fun setupBLECallbacks() {
        bleManager.onMessageReceived = { message ->
            runOnUiThread {
                // Store received message
                messageStore.storeMessage(message)
                
                // Add to display
                addMessageToDisplay(message)
                
                // Check if it's a BEEP message
                if (message.payload is BLEMessage.Payload.Beep) {
                    // Only beep once per unique BEEP message UUID
                    if (!processedBeepUUIDs.contains(message.messageUuid)) {
                        processedBeepUUIDs.add(message.messageUuid)
                        startBeepAlert()
                        Log.d("MainActivity", "🔔 New BEEP message UUID: ${String.format("%08X", message.messageUuid)}, starting alert")
                    } else {
                        Log.d("MainActivity", "🔔 BEEP message UUID already processed, skipping alert")
                    }
                }
                
                Toast.makeText(this, "📩 ${getMessageTypeIcon(message.payload)}", Toast.LENGTH_SHORT).show()
                
                // Upload if online
                uploadStoredMessagesIfOnline()
            }
        }
        
        bleManager.onScanningStatusChanged = { isScanning ->
            runOnUiThread {
                updateStatusText(isScanning)
            }
        }
    }
    
    private fun getMessageTypeIcon(payload: BLEMessage.Payload): String {
        return when (payload) {
            is BLEMessage.Payload.Location -> "Location received!"
            is BLEMessage.Payload.Questionnaire -> "Survey received!"
            is BLEMessage.Payload.Battery -> "Battery info received!"
            is BLEMessage.Payload.Message -> "Message received!"
            is BLEMessage.Payload.Beep -> "BEEP received!"
        }
    }
    
    /**
     * Set up click listeners for all buttons
     */
    private fun setupClickListeners() {
        try {
            // Remove START/STOP functionality since it's always on
            startButton.visibility = android.view.View.GONE
            stopButton.visibility = android.view.View.GONE
            
            updateProfileButton.setOnClickListener {
                // Navigate to signup activity for profile update
                val intent = Intent(this, SignupActivity::class.java)
                startActivity(intent)
            }
            
            emergencyButton.setOnClickListener {
                handleEmergency()
            }
            
            sendLocationButton.setOnClickListener {
                sendCurrentLocation()
            }
            
            sendBatteryButton.setOnClickListener {
                sendCurrentBattery()
            }
            
            stopBeepButton.setOnClickListener {
                stopBeepAlert()
                Toast.makeText(this, "🔕 Beep alert stopped", Toast.LENGTH_SHORT).show()
            }
            
            sendButton.setOnClickListener {
                sendMessage()
            }
            
            clearButton.setOnClickListener {
                clearMessageHistory()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up click listeners", e)
        }
    }
    
    /**
     * Send current GPS location
     */
    private fun sendCurrentLocation() {
        val location = locationHelper.getLastLocation()
        if (location != null) {
            sendLocationPayload(location.first, location.second)
            Toast.makeText(this, "📍 Location sent", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "❌ Location not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Send current battery status
     */
    private fun sendCurrentBattery() {
        val (percentage, seconds) = batteryHelper.getBatteryInfo()
        sendBatteryPayload(percentage, seconds)
        Toast.makeText(this, "🔋 Battery status sent: $percentage%", Toast.LENGTH_SHORT).show()
    }
    
    
    /**
     * Check and request necessary permissions
     */
    private fun checkPermissionsAndStart() {
        val requiredPermissions = getRequiredPermissions()
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isEmpty()) {
            startBroadcasting()
        } else {
            // Show explanation dialog before requesting permissions
            showPermissionRationaleDialog(missingPermissions)
        }
    }
    
    /**
     * Get the list of required permissions based on Android version
     */
    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Older versions
            permissions.add(Manifest.permission.BLUETOOTH)
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        // Location permissions (required for BLE on Android < 12)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        // Nearby WiFi devices for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        
        return permissions
    }
    
    /**
     * Show dialog explaining why permissions are needed
     */
    private fun showPermissionRationaleDialog(permissions: List<String>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.permission_message)
            .setPositiveButton("OK") { _, _ ->
                permissionLauncher.launch(permissions.toTypedArray())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Show dialog when permissions are denied
     */
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.permission_denied)
            .setPositiveButton("Retry") { _, _ ->
                checkPermissionsAndStart()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    /**
     * Start BLE broadcasting and scanning
     */
    private fun startBroadcasting() {
        try {
            // Check Bluetooth is enabled
            if (!bleManager.isBluetoothEnabled()) {
                Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show()
                return
            }
            
            // Start scanning for broadcasts
            val scanStarted = bleManager.startScanning()
            
            if (scanStarted) {
                isRunning = true
                updateUIForRunningState()
                Toast.makeText(this, "🔍 Listening for broadcasts...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to start. Check Bluetooth & Location are ON", Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Permission error: Enable Bluetooth & Location", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Stop BLE broadcasting and scanning
     */
    private fun stopBroadcasting() {
        bleManager.stop()
        isRunning = false
        updateUIForStoppedState()
        Toast.makeText(this, "📡 Stopped broadcasting", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Send a text message via BLE
     */
    private fun sendMessage() {
        val content = messageInput.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Text message (max 7 bytes)
        val bytes = content.toByteArray(Charsets.UTF_8).take(7).toByteArray()
        val payload = BLEMessage.Payload.Message(bytes)
        
        sendAndStoreMessage(payload)
        
        // Clear input
        messageInput.text?.clear()
        
        Toast.makeText(this, "💬 Message sent: $content", Toast.LENGTH_SHORT).show()
    }
    
    private fun getPayloadTypeName(payload: BLEMessage.Payload): String {
        return when (payload) {
            is BLEMessage.Payload.Location -> "location"
            is BLEMessage.Payload.Questionnaire -> "survey"
            is BLEMessage.Payload.Battery -> "battery"
            is BLEMessage.Payload.Message -> "message"
            is BLEMessage.Payload.Beep -> "beep"
        }
    }
    
    /**
     * Add a message to the display (with deduplication)
     */
    private fun addMessageToDisplay(message: BLEMessage) {
        // Check if we've already displayed this message
        if (seenMessageUUIDs.contains(message.messageUuid)) {
            return // Already displayed, skip
        }
        
        // Mark as seen
        seenMessageUUIDs.add(message.messageUuid)
        
        val displayText = message.getDisplayString()
        
        // Add to list (newest first)
        messageList.add(displayText)
        
        // Update UI
        updateMessageHistoryDisplay()
    }
    
    /**
     * Update the message history TextView
     */
    private fun updateMessageHistoryDisplay() {
        if (messageList.isEmpty()) {
            messageHistory.text = getString(R.string.no_messages)
        } else {
            // Display messages in reverse chronological order (newest first)
            val displayText = messageList.reversed().joinToString("\n\n")
            messageHistory.text = displayText
        }
    }
    
    /**
     * Clear the message history
     */
    private fun clearMessageHistory() {
        messageList.clear()
        updateMessageHistoryDisplay()
        Toast.makeText(this, "Message history cleared", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Update the status text
     */
    private fun updateStatusText(isScanning: Boolean) {
        if (isScanning) {
            statusText.text = "Status: Broadcasting & Listening 📡"
        } else {
            statusText.text = getString(R.string.status_stopped)
        }
    }
    
    /**
     * Update UI for running state
     */
    private fun updateUIForRunningState() {
        sendButton.isEnabled = true
        deviceNameInput.isEnabled = false
        statusText.text = "Status: Broadcasting & Listening 📡"
    }
    
    /**
     * Update UI for stopped state (permissions denied)
     */
    private fun updateUIForStoppedState() {
        sendButton.isEnabled = false
        deviceNameInput.isEnabled = true
        statusText.text = "Status: Waiting for permissions..."
    }
    
    /**
     * Start beep alert for 30 seconds
     */
    private fun startBeepAlert() {
        Log.d("MainActivity", "🔔 BEEP alert received! Starting 30-second alarm...")
        
        // Stop any existing beep
        stopBeepAlert()
        
        try {
            // Get notification ringtone
            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            currentRingtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            
            // Create repeating beep/vibration pattern
            beepRunnable = object : Runnable {
                private var beepCount = 0
                private val maxBeeps = 30 // 30 beeps over 30 seconds
                
                override fun run() {
                    if (beepCount < maxBeeps) {
                        try {
                            // Vibrate (short burst)
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                            vibrator?.let {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    it.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    it.vibrate(300)
                                }
                            }
                            
                            // Play sound (if not already playing)
                            currentRingtone?.let { ringtone ->
                                if (!ringtone.isPlaying) {
                                    ringtone.play()
                                }
                            }
                            
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error in beep alert", e)
                        }
                        
                        beepCount++
                        beepHandler.postDelayed(this, 1000) // Every 1 second
                    } else {
                        // Finished
                        Log.d("MainActivity", "🔔 Beep alert finished (30 seconds)")
                        stopBeepAlert()
                    }
                }
            }
            
            beepHandler.post(beepRunnable!!)
            
            // Show stop button
            stopBeepButton.visibility = View.VISIBLE
            
            Toast.makeText(this, "🔔 BEEP ALERT! Alarming for 30 seconds", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting beep alert", e)
            Toast.makeText(this, "⚠️ Could not start alarm", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Stop beep alert
     */
    private fun stopBeepAlert() {
        beepRunnable?.let {
            beepHandler.removeCallbacks(it)
            beepRunnable = null
        }
        
        currentRingtone?.let {
            if (it.isPlaying) {
                it.stop()
            }
            currentRingtone = null
        }
        
        // Hide stop button
        if (::stopBeepButton.isInitialized) {
            stopBeepButton.visibility = View.GONE
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // Stop emergency broadcasting
            stopEmergencyBroadcasting()
            
            // Stop beep alert
            stopBeepAlert()
            
            if (::bleManager.isInitialized && isRunning) {
                bleManager.stop()
            }
            if (::locationHelper.isInitialized) {
                locationHelper.stopTracking()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onDestroy", e)
        }
    }
}


