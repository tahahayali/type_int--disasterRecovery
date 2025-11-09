package com.example.blemessenger

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * BLE Message structure (20 bytes total):
 * [4 bytes message_id] [4 bytes sender_id] [4 bytes timestamp] [8 bytes payload]
 * 
 * Payload = [1 byte type] [7 bytes body]
 */
data class BLEMessage(
    val messageUuid: Int,           // 4 bytes - message_id for deduplication
    val senderUuid: Int,            // 4 bytes - sender_id
    val timestamp: Int,             // 4 bytes - unix timestamp (seconds)
    val payload: Payload            // 8 bytes - [1 byte type][7 bytes data]
) {
    
    /**
     * Payload types
     */
    sealed class Payload {
        abstract fun getType(): Byte
        abstract fun toBytes(): ByteArray
        
        /**
         * Type 1: Location (7 bytes = 56 bits)
         * Format: 28-bit latitude + 28-bit longitude
         * Resolution: ~7.5cm (lat), ~15cm (lon @ equator)
         */
        data class Location(
            val latitude: Double,   // -90 to +90
            val longitude: Double   // -180 to +180
        ) : Payload() {
            override fun getType(): Byte = 0x01
            
            override fun toBytes(): ByteArray {
                // Map lat [-90,+90] → [0, 2^28-1]
                val latU = (((latitude + 90.0) / 180.0) * ((1L shl 28) - 1)).roundToLong()
                    .coerceIn(0, (1L shl 28) - 1)
                
                // Map lon [-180,+180] → [0, 2^28-1]
                val lonU = (((longitude + 180.0) / 360.0) * ((1L shl 28) - 1)).roundToLong()
                    .coerceIn(0, (1L shl 28) - 1)
                
                // Pack into 56 bits: lat28 | lon28
                val val56 = (latU shl 28) or lonU
                
                // Write as 7 bytes big-endian
                val result = put56BE(val56)
                require(result.size == 7) { "Location payload must be 7 bytes" }
                return result
            }
            
            override fun toString(): String {
                return "📍 Location: (${"%.4f".format(latitude)}, ${"%.4f".format(longitude)})"
            }
        }
        
        /**
         * Type 2: Questionnaire (7 bytes)
         * Each byte = one answer: 0x00=No, 0x01=Yes, 0xFF=Unknown
         */
        data class Questionnaire(
            val answers: List<Int>  // Up to 7 answers (0x00, 0x01, or 0xFF)
        ) : Payload() {
            override fun getType(): Byte = 0x02
            
            override fun toBytes(): ByteArray {
                val bytes = ByteArray(7)
                for (i in 0 until 7) {
                    bytes[i] = (answers.getOrNull(i) ?: 0xFF).toByte()
                }
                require(bytes.size == 7) { "Questionnaire payload must be 7 bytes" }
                return bytes
            }
            
            override fun toString(): String {
                val yesCount = answers.count { it == 0x01 }
                val noCount = answers.count { it == 0x00 }
                return "📋 Survey: $yesCount YES, $noCount NO"
            }
        }
        
        /**
         * Type 3: Battery Status (7 bytes)
         * Format: [2B ASCII percentage "00"-"99"][5B ASCII seconds "00000"-"99999"]
         */
        data class Battery(
            val percentage: Int,     // 0-99
            val secondsRemaining: Int  // 0-99999
        ) : Payload() {
            override fun getType(): Byte = 0x03
            
            override fun toBytes(): ByteArray {
                val pct = percentage.coerceIn(0, 99)
                val sec = secondsRemaining.coerceIn(0, 99999)
                
                val pctStr = "%02d".format(pct)
                val secStr = "%05d".format(sec)
                
                val bytes = ByteArray(7)
                bytes[0] = pctStr[0].code.toByte()
                bytes[1] = pctStr[1].code.toByte()
                bytes[2] = secStr[0].code.toByte()
                bytes[3] = secStr[1].code.toByte()
                bytes[4] = secStr[2].code.toByte()
                bytes[5] = secStr[3].code.toByte()
                bytes[6] = secStr[4].code.toByte()
                
                require(bytes.size == 7) { "Battery payload must be 7 bytes" }
                return bytes
            }
            
            override fun toString(): String {
                val hours = secondsRemaining / 3600
                val minutes = (secondsRemaining % 3600) / 60
                return "🔋 Battery: $percentage% (${hours}h ${minutes}m left)"
            }
        }
        
        /**
         * Type 4: Message (7 bytes raw data)
         * For longer messages, send multiple Type-4 payloads
         */
        data class Message(
            val chunk: ByteArray
        ) : Payload() {
            override fun getType(): Byte = 0x04
            
            override fun toBytes(): ByteArray {
                val bytes = ByteArray(7)
                System.arraycopy(chunk, 0, bytes, 0, minOf(chunk.size, 7))
                require(bytes.size == 7) { "Message payload must be 7 bytes" }
                return bytes
            }
            
            override fun toString(): String {
                val text = String(chunk, Charsets.UTF_8).trim('\u0000')
                return "💬 Message: $text"
            }
            
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
                other as Message
                if (!chunk.contentEquals(other.chunk)) return false
                return true
            }
            
            override fun hashCode(): Int {
                return chunk.contentHashCode()
            }
        }
        
        /**
         * Type 5: BEEP (7 bytes, all zeros)
         * Simple alert/notification
         */
        object Beep : Payload() {
            override fun getType(): Byte = 0x05
            
            override fun toBytes(): ByteArray {
                val bytes = ByteArray(7) // All zeros
                require(bytes.size == 7) { "Beep payload must be 7 bytes" }
                return bytes
            }
            
            override fun toString(): String {
                return "🔔 BEEP"
            }
        }
    }
    
    /**
     * Encode message to 20-byte array for BLE advertising
     */
    fun toBytes(): ByteArray {
        val buffer = ByteBuffer.allocate(20)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        // Header (12 bytes)
        buffer.putInt(messageUuid)    // 4 bytes: message_id
        buffer.putInt(senderUuid)     // 4 bytes: sender_id
        buffer.putInt(timestamp)      // 4 bytes: timestamp
        
        // Payload (8 bytes)
        val payloadBytes = payload.toBytes()
        buffer.put(payload.getType()) // 1 byte: type
        buffer.put(payloadBytes)      // 7 bytes: body
        
        val result = buffer.array()
        
        // Debug verification
        if (result.size != 20) {
            android.util.Log.e("BLEMessage", "ERROR: Message is ${result.size} bytes, expected 20!")
        }
        
        return result
    }
    
    fun getDisplayString(): String {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(timestamp * 1000L))
        val msgId = String.format("%08X", messageUuid).substring(0, 4)
        val senderId = String.format("%08X", senderUuid).substring(0, 4)
        return "[$time] Msg:$msgId From:$senderId\n${payload}"
    }
    
    companion object {
        /**
         * Decode 20-byte array back to BLEMessage
         */
        fun fromBytes(data: ByteArray): BLEMessage? {
            if (data.size != 20) return null
            
            try {
                val buffer = ByteBuffer.wrap(data)
                buffer.order(ByteOrder.BIG_ENDIAN)
                
                // Header (12 bytes)
                val messageUuid = buffer.int
                val senderUuid = buffer.int
                val timestamp = buffer.int
                
                // Payload (8 bytes)
                val type = buffer.get()
                val body = ByteArray(7)
                buffer.get(body)
                
                val payload = when (type.toInt()) {
                    0x01 -> parseLocationPayload(body)
                    0x02 -> parseQuestionnairePayload(body)
                    0x03 -> parseBatteryPayload(body)
                    0x04 -> parseMessagePayload(body)
                    0x05 -> Payload.Beep
                    else -> return null
                }
                
                return BLEMessage(messageUuid, senderUuid, timestamp, payload)
                
            } catch (e: Exception) {
                return null
            }
        }
        
        /**
         * Parse Type 1: Location (56-bit packed lat/lon)
         */
        private fun parseLocationPayload(body: ByteArray): Payload.Location {
            // Read 56 bits (7 bytes) big-endian
            val val56 = get56BE(body, 0)
            
            // Extract 28-bit lat and lon
            val latU = (val56 ushr 28) and 0xFFFFFFF
            val lonU = val56 and 0xFFFFFFF
            
            // Map back to degrees
            val latitude = (latU.toDouble() / ((1L shl 28) - 1)) * 180.0 - 90.0
            val longitude = (lonU.toDouble() / ((1L shl 28) - 1)) * 360.0 - 180.0
            
            return Payload.Location(latitude, longitude)
        }
        
        /**
         * Parse Type 2: Questionnaire (7 bytes, 1 per answer)
         */
        private fun parseQuestionnairePayload(body: ByteArray): Payload.Questionnaire {
            val answers = body.map { it.toInt() and 0xFF }
            return Payload.Questionnaire(answers)
        }
        
        /**
         * Parse Type 3: Battery (ASCII percentage + seconds)
         */
        private fun parseBatteryPayload(body: ByteArray): Payload.Battery {
            try {
                val pctStr = String(body, 0, 2, Charsets.US_ASCII)
                val secStr = String(body, 2, 5, Charsets.US_ASCII)
                
                val percentage = pctStr.toIntOrNull() ?: 0
                val seconds = secStr.toIntOrNull() ?: 0
                
                return Payload.Battery(percentage, seconds)
            } catch (e: Exception) {
                return Payload.Battery(0, 0)
            }
        }
        
        /**
         * Parse Type 4: Message (7 raw bytes)
         */
        private fun parseMessagePayload(body: ByteArray): Payload.Message {
            return Payload.Message(body.copyOf())
        }
        
        /**
         * Write 56-bit value as 7 bytes big-endian
         */
        private fun put56BE(value: Long): ByteArray {
            require(value ushr 56 == 0L) { "Value must fit in 56 bits" }
            val bytes = ByteArray(7)
            for (i in 0 until 7) {
                bytes[i] = ((value ushr (8 * (6 - i))) and 0xFF).toByte()
            }
            return bytes
        }
        
        /**
         * Read 56-bit value from 7 bytes big-endian
         */
        private fun get56BE(bytes: ByteArray, offset: Int): Long {
            var value = 0L
            for (i in 0 until 7) {
                value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
            }
            return value
        }
        
        /**
         * Generate a random message UUID (message_id)
         */
        fun generateMessageUuid(): Int {
            return Random.nextInt()
        }
        
        /**
         * Generate a sender UUID (sender_id) - should be stored per device
         */
        fun generateSenderUuid(): Int {
            return Random.nextInt()
        }
        
        /**
         * Get current timestamp in seconds
         */
        fun getCurrentTimestamp(): Int {
            return (System.currentTimeMillis() / 1000).toInt()
        }
    }
}
