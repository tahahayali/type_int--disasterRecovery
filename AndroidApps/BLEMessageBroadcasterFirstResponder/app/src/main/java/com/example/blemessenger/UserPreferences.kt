package com.example.blemessenger

import android.content.Context
import android.content.SharedPreferences
import kotlin.random.Random

/**
 * Manages user data and UUID in SharedPreferences
 */
class UserPreferences(context: Context) {
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_UUID = "uuid"
        private const val KEY_NAME = "name"
        private const val KEY_AGE = "age"
        private const val KEY_HEIGHT = "height"
        private const val KEY_WEIGHT = "weight"
        private const val KEY_MEDICAL = "medical"
        private const val KEY_IS_REGISTERED = "is_registered"
    }
    
    /**
     * Get or generate 4-character UUID
     */
    fun getOrGenerateUUID(): String {
        var uuid = prefs.getString(KEY_UUID, null)
        if (uuid == null) {
            // Generate 4-character alphanumeric UUID
            uuid = generateUUID()
            prefs.edit().putString(KEY_UUID, uuid).apply()
        }
        return uuid
    }
    
    /**
     * Generate random 4-character alphanumeric UUID
     */
    private fun generateUUID(): String {
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return (1..4)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
    
    /**
     * Save user profile data
     */
    fun saveUserProfile(
        name: String,
        age: Int,
        height: String,
        weight: String,
        medical: String
    ) {
        prefs.edit()
            .putString(KEY_NAME, name)
            .putInt(KEY_AGE, age)
            .putString(KEY_HEIGHT, height)
            .putString(KEY_WEIGHT, weight)
            .putString(KEY_MEDICAL, medical)
            .putBoolean(KEY_IS_REGISTERED, true)
            .apply()
    }
    
    /**
     * Check if user is registered
     */
    fun isRegistered(): Boolean {
        return prefs.getBoolean(KEY_IS_REGISTERED, false)
    }
    
    /**
     * Get user name
     */
    fun getName(): String {
        return prefs.getString(KEY_NAME, "") ?: ""
    }
    
    /**
     * Get user age
     */
    fun getAge(): Int {
        return prefs.getInt(KEY_AGE, 0)
    }
    
    /**
     * Get height
     */
    fun getHeight(): String {
        return prefs.getString(KEY_HEIGHT, "") ?: ""
    }
    
    /**
     * Get weight
     */
    fun getWeight(): String {
        return prefs.getString(KEY_WEIGHT, "") ?: ""
    }
    
    /**
     * Get medical conditions
     */
    fun getMedical(): String {
        return prefs.getString(KEY_MEDICAL, "") ?: ""
    }
    
    /**
     * Clear all user data (for testing/reset)
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Convert 4-character UUID to sender_id (4-byte big-endian integer)
     * Example: "8U4A" → bytes [0x38, 0x55, 0x34, 0x41] → int 946193473
     */
    fun uuidToSenderId(): Int {
        val uuid = getOrGenerateUUID()
        require(uuid.length == 4) { "UUID must be exactly 4 characters" }
        
        val bytes = uuid.toByteArray(Charsets.US_ASCII)
        return java.nio.ByteBuffer.wrap(bytes)
            .order(java.nio.ByteOrder.BIG_ENDIAN)
            .getInt()
    }
}

