package com.example.firstresponder

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE Manager using pure BLE advertising and scanning
 * No connections needed - just broadcast and listen!
 */
class BLEManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BLEManager"
        private const val MAX_REBROADCAST_COUNT = 3
        
        // Custom Service UUID to identify our app's broadcasts
        private val SERVICE_UUID = UUID.fromString("0000FE9A-0000-1000-8000-00805F9B34FB")
        
        // Max BLE advertisement payload size (reduced for reliability)
        private const val MAX_PAYLOAD_SIZE = 20
    }
    
    private val bluetoothManager: BluetoothManager = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    
    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var bleScanner: BluetoothLeScanner? = null
    
    // Track message UUIDs (first 4 bytes) and their rebroadcast counts
    private val messageRebroadcastCounts = ConcurrentHashMap<Int, Int>()
    
    // Queue of messages to broadcast (rotating through them)
    private val messageBroadcastQueue = ConcurrentHashMap<Int, BLEMessage>()
    
    // Currently advertising message index
    private var currentAdvertisingMessageIndex = 0
    
    // This device's sender UUID will be set from UserPreferences
    private var senderUuid: Int = 0
    
    // Handler for rotating advertisements
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var advertisingRotationRunnable: Runnable? = null
    
    // Callbacks
    var onMessageReceived: ((BLEMessage) -> Unit)? = null
    var onScanningStatusChanged: ((Boolean) -> Unit)? = null
    
    private var isScanning = false
    private var isAdvertising = false
    
    /**
     * Scan callback - receives BLE advertisements
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            
            val scanRecord = result.scanRecord ?: return
            val serviceData = scanRecord.serviceData
            
            // Check if this is our app's message
            val data = serviceData[ParcelUuid(SERVICE_UUID)]
            if (data != null && data.size == 20) {
                try {
                    // Quick check: extract message UUID (first 4 bytes) for deduplication
                    val messageUuid = ByteBuffer.wrap(data.copyOfRange(0, 4)).order(ByteOrder.BIG_ENDIAN).int
                    
                    // Check if we've already processed this message
                    val rebroadcastCount = messageRebroadcastCounts.getOrDefault(messageUuid, 0)
                    if (rebroadcastCount >= MAX_REBROADCAST_COUNT) {
                        // Already processed max times, skip parsing
                        return
                    }
                    
                    // Parse full message
                    val message = BLEMessage.fromBytes(data)
                    if (message != null) {
                        Log.d(TAG, "✅ Received: ${message.payload}")
                        handleReceivedMessage(message)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse broadcast data", e)
                }
            }
        }
        
        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "❌ Scan failed with error: $errorCode")
            isScanning = false
            onScanningStatusChanged?.invoke(false)
        }
    }
    
    /**
     * Advertisement callback
     */
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.d(TAG, "Advertising started successfully")
            isAdvertising = true
        }
        
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Advertising failed with error: $errorCode")
            isAdvertising = false
        }
    }
    
    /**
     * Start scanning for BLE advertisements
     */
    fun startScanning(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return false
        }
        
        try {
            bleScanner = bluetoothAdapter.bluetoothLeScanner
            if (bleScanner == null) {
                Log.e(TAG, "BLE Scanner not available")
                return false
            }
            
            // Scan filter to only get our service UUID
            val scanFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(SERVICE_UUID))
                .build()
            
            // Scan settings - low latency for faster detection
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build()
            
            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            isScanning = true
            onScanningStatusChanged?.invoke(true)
            
            // Start advertising rotation to ensure continuous broadcasting
            startAdvertisingRotation()
            
            Log.d(TAG, "Scanning started")
            return true
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting scan", e)
            return false
        }
    }
    
    /**
     * Stop scanning
     */
    fun stopScanning() {
        try {
            bleScanner?.stopScan(scanCallback)
            isScanning = false
            onScanningStatusChanged?.invoke(false)
            Log.d(TAG, "Scanning stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }
    
    /**
     * Broadcast a complete message via BLE advertising
     */
    fun broadcastMessage(message: BLEMessage) {
        // Add to broadcast queue
        messageBroadcastQueue[message.messageUuid] = message
        
        // Mark as our own message (max rebroadcast count so we don't rebroadcast it)
        messageRebroadcastCounts[message.messageUuid] = MAX_REBROADCAST_COUNT
        
        Log.d(TAG, "📡 Broadcasting: ${message.payload}")
        
        // If not already rotating, start rotation
        if (advertisingRotationRunnable == null) {
            startAdvertisingRotation()
        }
    }
    
    /**
     * Set this device's sender UUID (from UserPreferences)
     */
    fun setSenderUuid(senderId: Int) {
        senderUuid = senderId
    }
    
    /**
     * Get this device's sender UUID
     */
    fun getSenderUuid(): Int {
        return senderUuid
    }
    
    /**
     * Start BLE advertising with current messages
     */
    private fun startAdvertising(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth not available or not enabled")
            return false
        }
        
        try {
            bleAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
            if (bleAdvertiser == null) {
                Log.e(TAG, "BLE Advertiser not available")
                return false
            }
            
            // Get the next message to advertise
            val messageToAdvertise = getNextMessageToAdvertise()
            if (messageToAdvertise == null) {
                Log.d(TAG, "No messages to advertise")
                return false
            }
            
            // Encode to 20 bytes
            val data = messageToAdvertise.toBytes()
            
            Log.d(TAG, "📤 Advertising: ${messageToAdvertise.payload} (20 bytes)")
            
            return startAdvertisingWithData(data)
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting advertising", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising", e)
            return false
        }
    }
    
    /**
     * Helper to start advertising with specific data
     */
    private fun startAdvertisingWithData(data: ByteArray): Boolean {
        try {
            // Advertisement settings
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(false)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build()
            
            // Advertisement data
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(SERVICE_UUID))
                .addServiceData(ParcelUuid(SERVICE_UUID), data)
                .build()
            
            bleAdvertiser?.startAdvertising(settings, advertiseData, advertiseCallback)
            Log.d(TAG, "Started advertising with ${data.size} bytes")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting advertising", e)
            return false
        }
    }
    
    /**
     * Stop BLE advertising
     */
    fun stopAdvertising() {
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            isAdvertising = false
            Log.d(TAG, "Advertising stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advertising", e)
        }
    }
    
    /**
     * Start continuous advertising rotation
     * Rotates through all messages in queue, broadcasting each one multiple times
     */
    private fun startAdvertisingRotation() {
        // Cancel any existing rotation
        stopAdvertisingRotation()
        
        advertisingRotationRunnable = object : Runnable {
            override fun run() {
                if (messageBroadcastQueue.isNotEmpty()) {
                    // Stop current advertising
                    stopAdvertising()
                    
                    // Start advertising next message
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startAdvertising()
                    }, 50)
                    
                    // Rotate every 1 second to broadcast each message multiple times
                    handler.postDelayed(this, 1000)
                } else {
                    // No messages to broadcast, check again in 2 seconds
                    handler.postDelayed(this, 2000)
                }
            }
        }
        
        handler.post(advertisingRotationRunnable!!)
        Log.d(TAG, "🔄 Started advertising rotation")
    }
    
    /**
     * Stop advertising rotation
     */
    private fun stopAdvertisingRotation() {
        advertisingRotationRunnable?.let {
            handler.removeCallbacks(it)
            advertisingRotationRunnable = null
        }
    }
    
    /**
     * Get the next message to advertise (rotating through queue)
     */
    private fun getNextMessageToAdvertise(): BLEMessage? {
        if (messageBroadcastQueue.isEmpty()) return null
        
        val messages = messageBroadcastQueue.values.toList()
        if (messages.isEmpty()) return null
        
        currentAdvertisingMessageIndex = currentAdvertisingMessageIndex % messages.size
        val message = messages[currentAdvertisingMessageIndex]
        currentAdvertisingMessageIndex++
        
        return message
    }
    
    /**
     * Handle a received message
     */
    private fun handleReceivedMessage(message: BLEMessage) {
        val uuid = message.messageUuid
        val currentCount = messageRebroadcastCounts.getOrDefault(uuid, 0)
        
        Log.d(TAG, "Handling message UUID: ${String.format("%08X", uuid)}, count: $currentCount")
        
        // If this is the first time we see this message
        if (currentCount == 0) {
            // Display it
            onMessageReceived?.invoke(message)
            
            // Add to our broadcast queue for rebroadcasting
            messageBroadcastQueue[uuid] = message
            messageRebroadcastCounts[uuid] = 1
            
            // If not already rotating, start rotation
            if (advertisingRotationRunnable == null) {
                startAdvertisingRotation()
            }
            
            Log.d(TAG, "✨ New message queued for rebroadcast")
            
        } else if (currentCount < MAX_REBROADCAST_COUNT) {
            // We've seen this before, but can still rebroadcast
            // Update count but don't display again
            messageRebroadcastCounts[uuid] = currentCount + 1
            Log.d(TAG, "🔁 Rebroadcast count: ${currentCount + 1}")
        } else {
            // Already rebroadcast max times, stop broadcasting it
            messageBroadcastQueue.remove(uuid)
            Log.d(TAG, "🛑 Max rebroadcasts reached, removed from queue")
        }
    }
    
    /**
     * Stop all BLE operations
     */
    fun stop() {
        stopAdvertisingRotation()
        stopScanning()
        stopAdvertising()
        messageBroadcastQueue.clear()
        messageRebroadcastCounts.clear()
        Log.d(TAG, "BLE Manager stopped")
    }
    
    /**
     * Check if BLE is available and enabled
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }
    
    /**
     * Get status string
     */
    fun getStatusString(): String {
        val scanStatus = if (isScanning) "Scanning" else "Not scanning"
        val advStatus = if (isAdvertising) "Broadcasting" else "Not broadcasting"
        val queueSize = messageBroadcastQueue.size
        return "$scanStatus, $advStatus, Queue: $queueSize msg(s)"
    }
    
}



