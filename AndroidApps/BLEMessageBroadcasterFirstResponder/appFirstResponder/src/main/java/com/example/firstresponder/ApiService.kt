package com.example.firstresponder

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles API calls to backend server
 */
object ApiService {
    
    private const val TAG = "ApiService"
    private const val BASE_URL = "http://fennecs.duckdns.org:5000"
    
    /**
     * Register first responder with backend
     */
    suspend fun signupUser(uuid: String, name: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/signup")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Build JSON payload
            val jsonPayload = JSONObject().apply {
                put("uuid", uuid)
                put("type", "first_responder")
                put("name", name)
            }
            
            Log.d(TAG, "Sending signup request: $jsonPayload")
            
            // Write request body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }
            
            // Read response
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                Log.d(TAG, "Signup successful: $response")
                Result.success(response)
            } else {
                val errorResponse = try {
                    BufferedReader(InputStreamReader(connection.errorStream)).use {
                        it.readText()
                    }
                } catch (e: Exception) {
                    "HTTP $responseCode"
                }
                Log.e(TAG, "Signup failed: $errorResponse")
                Result.failure(Exception("Signup failed: $errorResponse"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Network error during signup", e)
            Result.failure(e)
        }
    }
    
    /**
     * Send hex byte strings to server
     */
    suspend fun sendByteStrings(hexMessages: List<String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/api/byte_string")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Build JSON payload with messages array
            val messagesArray = org.json.JSONArray(hexMessages)
            val jsonPayload = JSONObject().apply {
                put("messages", messagesArray)
            }
            
            Log.d(TAG, "Sending ${hexMessages.size} messages to server")
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonPayload.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use {
                    it.readText()
                }
                Log.d(TAG, "Messages sent successfully: $response")
                Result.success(response)
            } else {
                val errorResponse = try {
                    BufferedReader(InputStreamReader(connection.errorStream)).use {
                        it.readText()
                    }
                } catch (e: Exception) {
                    "HTTP $responseCode"
                }
                Log.e(TAG, "Send messages failed: $errorResponse")
                Result.failure(Exception("Send failed: $errorResponse"))
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Network error sending messages", e)
            Result.failure(e)
        }
    }
}


