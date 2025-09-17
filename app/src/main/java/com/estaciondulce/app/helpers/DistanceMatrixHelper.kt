package com.estaciondulce.app.helpers

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Helper class for calculating shipping costs using Google Distance Matrix API.
 */
class DistanceMatrixHelper {
    
    companion object {
        private const val TAG = "DistanceMatrixHelper"
        private const val API_KEY = "YOUR_GOOGLE_MAPS_API_KEY" // TODO: Replace with actual API key
        private const val BASE_URL = "https://maps.googleapis.com/maps/api/distancematrix/json"
    }
    
    /**
     * Calculates the distance between two points using Google Distance Matrix API.
     * @param origin Origin coordinates as "lat,lng"
     * @param destination Destination coordinates as "lat,lng"
     * @param callback Callback with distance in kilometers or error
     */
    fun calculateDistance(
        origin: String,
        destination: String,
        callback: (Double?, String?) -> Unit
    ) {
        // For now, we'll use a mock calculation since we don't have the API key
        // In a real implementation, you would make an HTTP request to Google Distance Matrix API
        
        Log.d(TAG, "Calculating distance from $origin to $destination")
        
        // Mock calculation - replace with actual API call
        val mockDistance = calculateMockDistance(origin, destination)
        callback(mockDistance, null)
    }
    
    /**
     * Mock distance calculation for testing purposes.
     * Replace this with actual Google Distance Matrix API call.
     */
    private fun calculateMockDistance(origin: String, destination: String): Double {
        try {
            val originParts = origin.split(",")
            val destParts = destination.split(",")
            
            if (originParts.size != 2 || destParts.size != 2) {
                return 0.0
            }
            
            val originLat = originParts[0].toDouble()
            val originLng = originParts[1].toDouble()
            val destLat = destParts[0].toDouble()
            val destLng = destParts[1].toDouble()
            
            // Calculate distance using Haversine formula
            val distance = calculateHaversineDistance(
                LatLng(originLat, originLng),
                LatLng(destLat, destLng)
            )
            
            Log.d(TAG, "Calculated distance: ${distance}km")
            return distance
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating mock distance: ${e.message}")
            return 0.0
        }
    }
    
    /**
     * Calculates distance between two points using Haversine formula.
     */
    private fun calculateHaversineDistance(point1: LatLng, point2: LatLng): Double {
        val earthRadius = 6371.0 // Earth's radius in kilometers
        
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLngRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLngRad / 2) * Math.sin(deltaLngRad / 2)
        
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        
        return earthRadius * c
    }
    
    /**
     * Calculates shipping cost based on distance and settings.
     */
    fun calculateShippingCost(
        distanceKm: Double,
        fuelPrice: Double,
        litersPerKm: Double
    ): Double {
        val cost = (distanceKm / litersPerKm) * fuelPrice // Formula: (distancia_en_km / litersPerKm) × fuelPrice
        Log.d(TAG, "Shipping cost calculation: (${distanceKm}km / ${litersPerKm}) * ${fuelPrice}ARS = ${cost}ARS")
        return cost
    }
}
