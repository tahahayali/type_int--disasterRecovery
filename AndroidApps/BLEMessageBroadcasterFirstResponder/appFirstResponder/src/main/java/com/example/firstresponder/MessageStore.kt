package com.example.firstresponder

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File

/**
 * Stores BLE messages locally and manages sync to server
 */
class MessageStore(private val context: Context) {
    
    companion object {
        private const val TAG = "MessageStore"
        private const val MESSAGES_FILE = "stored_messages.json"
    }
    
    private val messagesFile = File(context.filesDir, MESSAGES_FILE)
    
    /**
     * Store a message (sent or received)
     */
    fun storeMessage(message: BLEMessage) {
        val hexString = bytesToHex(message.toBytes())
        
        synchronized(this) {
            val messages = getAllMessages().toMutableSet()
            messages.add(hexString)
            saveMessages(messages)
        }
        
        Log.d(TAG, "Stored message: $hexString")
    }
    
    /**
     * Get all stored messages as hex strings
     */
    fun getAllMessages(): List<String> {
        synchronized(this) {
            if (!messagesFile.exists()) {
                return emptyList()
            }
            
            return try {
                val json = messagesFile.readText()
                val jsonArray = JSONArray(json)
                List(jsonArray.length()) { i ->
                    jsonArray.getString(i)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading messages", e)
                emptyList()
            }
        }
    }
    
    /**
     * Save messages to file
     */
    private fun saveMessages(messages: Set<String>) {
        try {
            val jsonArray = JSONArray(messages.toList())
            messagesFile.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages", e)
        }
    }
    
    /**
     * Clear all stored messages (after successful upload)
     */
    fun clearMessages() {
        synchronized(this) {
            messagesFile.delete()
            Log.d(TAG, "Cleared all stored messages")
        }
    }
    
    /**
     * Get message count
     */
    fun getMessageCount(): Int {
        return getAllMessages().size
    }
    
    /**
     * Convert byte array to hex string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}


