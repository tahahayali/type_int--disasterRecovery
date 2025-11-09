package com.example.blemessenger

import android.util.Log

/**
 * Test encoding to match Python reference implementation
 */
object TestEncoding {
    
    private const val TAG = "TestEncoding"
    
    /**
     * Test UUID to sender_id conversion
     */
    fun testUuidEncoding() {
        // Test case 1: "8U4A" should = 946193473
        val uuid1 = "8U4A"
        val expected1 = 946193473
        val actual1 = uuidToSenderId(uuid1)
        Log.d(TAG, "UUID '$uuid1' → sender_id $actual1 (expected $expected1) ${if (actual1 == expected1) "✅" else "❌"}")
        
        // Test case 2: "MPHH" should = 1297297480
        val uuid2 = "MPHH"
        val expected2 = 1297297480
        val actual2 = uuidToSenderId(uuid2)
        Log.d(TAG, "UUID '$uuid2' → sender_id $actual2 (expected $expected2) ${if (actual2 == expected2) "✅" else "❌"}")
        
        // Test case 3: "0001" 
        val uuid3 = "0001"
        val actual3 = uuidToSenderId(uuid3)
        Log.d(TAG, "UUID '$uuid3' → sender_id $actual3")
    }
    
    /**
     * Convert 4-character UUID to sender_id integer
     */
    private fun uuidToSenderId(uuid: String): Int {
        require(uuid.length == 4) { "UUID must be 4 characters" }
        val bytes = uuid.toByteArray(java.nio.charset.StandardCharsets.US_ASCII)
        return java.nio.ByteBuffer.wrap(bytes)
            .order(java.nio.ByteOrder.BIG_ENDIAN)
            .getInt()
    }
    
    /**
     * Test Buffalo location encoding
     */
    fun testBuffaloLocation() {
        // Buffalo, NY coordinates from Python script
        val lat = 42.8864
        val lon = -78.8784
        
        // Expected from Python: message 1
        val expectedHex = "00000001000000016747a0000101f4ba5a07cd2e51"
        
        // Create our message
        val message = BLEMessage(
            messageUuid = 1,
            senderUuid = 1,
            timestamp = 0x6747a000,
            payload = BLEMessage.Payload.Location(lat, lon)
        )
        
        val actualHex = bytesToHex(message.toBytes())
        
        Log.d(TAG, "Testing Buffalo location encoding:")
        Log.d(TAG, "Expected: $expectedHex")
        Log.d(TAG, "Actual:   $actualHex")
        Log.d(TAG, "Match: ${expectedHex == actualHex}")
        
        if (expectedHex != actualHex) {
            Log.e(TAG, "❌ ENCODING MISMATCH!")
            // Compare byte by byte
            val expectedBytes = hexToBytes(expectedHex)
            val actualBytes = message.toBytes()
            for (i in 0 until 20) {
                if (expectedBytes[i] != actualBytes[i]) {
                    Log.e(TAG, "  Byte $i differs: expected 0x${"%02x".format(expectedBytes[i])} got 0x${"%02x".format(actualBytes[i])}")
                }
            }
        } else {
            Log.d(TAG, "✅ Encoding matches Python!")
        }
    }
    
    /**
     * Test battery encoding
     */
    fun testBattery() {
        // Expected from Python: battery 25%, 3600 seconds
        val message = BLEMessage(
            messageUuid = 4,
            senderUuid = 1,
            timestamp = 0x6747a006,
            payload = BLEMessage.Payload.Battery(25, 3600)
        )
        
        val actualHex = bytesToHex(message.toBytes())
        Log.d(TAG, "Battery encoding test:")
        Log.d(TAG, "Hex: $actualHex")
        Log.d(TAG, "Should be: 00000004000000016747a00603323503363030")
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

