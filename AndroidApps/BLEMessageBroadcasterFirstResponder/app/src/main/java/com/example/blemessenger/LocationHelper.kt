package com.example.blemessenger

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

/**
 * Manages GPS location tracking
 */
class LocationHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "LocationHelper"
        private const val LOCATION_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes
    }
    
    private var lastKnownLocation: Location? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    
    var onLocationUpdate: ((Double, Double) -> Unit)? = null
    
    /**
     * Start location tracking
     */
    fun startTracking() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission, skipping location tracking")
            return
        }
        
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                LOCATION_UPDATE_INTERVAL
            ).build()
            
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { location ->
                        lastKnownLocation = location
                        onLocationUpdate?.invoke(location.latitude, location.longitude)
                        Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                    }
                }
            }
            
            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            
            // Get last known location immediately
            fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                location?.let {
                    lastKnownLocation = it
                    onLocationUpdate?.invoke(it.latitude, it.longitude)
                }
            }
            
            Log.d(TAG, "Location tracking started")
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting location tracking", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking (GPS may not be available)", e)
        }
    }
    
    /**
     * Stop location tracking
     */
    fun stopTracking() {
        try {
            locationCallback?.let {
                fusedLocationClient?.removeLocationUpdates(it)
            }
            Log.d(TAG, "Location tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location tracking", e)
        }
    }
    
    /**
     * Get current or last known location
     */
    fun getLastLocation(): Pair<Double, Double>? {
        return lastKnownLocation?.let {
            Pair(it.latitude, it.longitude)
        }
    }
    
    /**
     * Check if location permission is granted
     */
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}

