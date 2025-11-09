package com.example.firstresponder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
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

/**
 * Main Activity for First Responders - auto-sends location every 30s
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var bleManager: BLEManager
    private lateinit var userPrefs: UserPreferences
    private lateinit var messageStore: MessageStore
    private lateinit var locationHelper: LocationHelper
    private lateinit var batteryHelper: BatteryHelper
    
    // UI Components
    private lateinit var welcomeText: TextView
    private lateinit var sendLocationButton: MaterialButton
    private lateinit var sendBatteryButton: MaterialButton
    private lateinit var sendBeepButton: MaterialButton
    private lateinit var deviceNameInput: TextInputEditText
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
    
    // Auto-location handler
    private val locationHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var locationRunnable: Runnable? = null
    
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
            bleManager.setSenderUuid(userPrefs.uuidToSenderId())
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
        sendLocationButton = findViewById(R.id.sendLocationButton)
        sendBatteryButton = findViewById(R.id.sendBatteryButton)
        sendBeepButton = findViewById(R.id.sendBeepButton)
        deviceNameInput = findViewById(R.id.deviceNameInput)
        statusText = findViewById(R.id.statusText)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        messageHistory = findViewById(R.id.messageHistory)
    }
    
    /**
     * Set up profile UI with user name
     */
    private fun setupProfileUI() {
        try {
            val name = userPrefs.getName()
            welcomeText.text = "typFirstResponder\nHello, $name"
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
     * Start continuous auto-location broadcasting (every 30 seconds)
     */
    private fun startAutoLocationBroadcasting() {
        locationRunnable = object : Runnable {
            override fun run() {
                // Send current location
                val location = locationHelper.getLastLocation()
                if (location != null) {
                    sendLocationPayload(location.first, location.second)
                    Log.d("MainActivity", "📍 Auto-sent location")
                }
                
                // Schedule next broadcast in 30 seconds
                locationHandler.postDelayed(this, 30000)
            }
        }
        
        // Start immediately
        val location = locationHelper.getLastLocation()
        if (location != null) {
            sendLocationPayload(location.first, location.second)
        }
        
        // Schedule next one in 30 seconds
        locationHandler.postDelayed(locationRunnable!!, 30000)
        
        Log.d("MainActivity", "🌍 Auto-location broadcasting started (every 30s)")
    }
    
    /**
     * Stop auto-location broadcasting
     */
    private fun stopAutoLocationBroadcasting() {
        locationRunnable?.let {
            locationHandler.removeCallbacks(it)
        }
        locationRunnable = null
    }
    
    /**
     * Setup location tracking
     */
    private fun setupLocationTracking() {
        try {
            locationHelper.onLocationUpdate = { lat, lon ->
                Log.d("MainActivity", "Location updated: $lat, $lon")
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
        val message = BLEMessage(
            messageUuid = BLEMessage.generateMessageUuid(),
            senderUuid = userPrefs.uuidToSenderId(),
            timestamp = BLEMessage.getCurrentTimestamp(),
            payload = payload
        )
        
        // Debug: log the hex string
        val hexString = bytesToHex(message.toBytes())
        Log.d("MainActivity", "📤 Generated message hex (${hexString.length/2} bytes): $hexString")
        
        // Broadcast over BLE
        bleManager.broadcastMessage(message)
        
        // Store locally
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
     * Send beep payload
     */
    private fun sendBeepPayload() {
        val payload = BLEMessage.Payload.Beep
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
            sendLocationButton.setOnClickListener {
                sendCurrentLocation()
            }
            
            sendBatteryButton.setOnClickListener {
                sendCurrentBattery()
            }
            
            sendBeepButton.setOnClickListener {
                sendBeep()
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
     * Send beep alert
     */
    private fun sendBeep() {
        sendBeepPayload()
        Toast.makeText(this, "🔔 BEEP sent", Toast.LENGTH_SHORT).show()
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
                
                // Start auto-location broadcasting
                startAutoLocationBroadcasting()
                
                Toast.makeText(this, "🔍 Listening & broadcasting location...", Toast.LENGTH_SHORT).show()
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
            statusText.text = "Status: Broadcasting & Listening 📡\nAuto-sending location every 30s"
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
        statusText.text = "Status: Broadcasting & Listening 📡\nAuto-sending location every 30s"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // Stop auto-location broadcasting
            stopAutoLocationBroadcasting()
            
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


