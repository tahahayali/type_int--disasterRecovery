package com.example.firstresponder

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log

/**
 * Manages battery status information
 */
class BatteryHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "BatteryHelper"
    }
    
    /**
     * Get current battery percentage (0-99)
     */
    fun getBatteryPercentage(): Int {
        val batteryStatus = getBatteryIntent()
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        
        return if (level >= 0 && scale > 0) {
            ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 99)
        } else {
            0
        }
    }
    
    /**
     * Estimate seconds remaining based on battery percentage
     * Simple estimation: assume linear drain
     */
    fun getEstimatedSecondsRemaining(): Int {
        val percentage = getBatteryPercentage()
        val batteryStatus = getBatteryIntent()
        
        // Get charging status
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
        
        return if (isCharging) {
            // If charging, estimate time to full (rough estimate)
            val remainingPercent = 100 - percentage
            (remainingPercent * 60).coerceIn(0, 99999) // ~1 min per percent
        } else {
            // If discharging, estimate time remaining
            // Rough estimate: assume 4 hours at 100%
            val totalSeconds = 4 * 3600 // 4 hours
            ((percentage / 100.0) * totalSeconds).toInt().coerceIn(0, 99999)
        }
    }
    
    /**
     * Get battery info as formatted pair (percentage, seconds)
     */
    fun getBatteryInfo(): Pair<Int, Int> {
        return Pair(getBatteryPercentage(), getEstimatedSecondsRemaining())
    }
    
    /**
     * Get battery intent
     */
    private fun getBatteryIntent(): Intent? {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return context.registerReceiver(null, intentFilter)
    }
}


