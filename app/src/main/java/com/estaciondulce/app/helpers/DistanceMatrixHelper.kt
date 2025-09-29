package com.estaciondulce.app.helpers

import android.util.Log
import com.estaciondulce.app.BuildConfig
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
        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
        
        if (apiKey.isNullOrEmpty()) {
            val mockDistance = calculateMockDistance(origin, destination)
            callback(mockDistance, null)
            return
        }
        
        makeDistanceMatrixRequest(origin, destination, apiKey, callback)
    }
    
    /**
     * Makes HTTP request to Google Distance Matrix API.
     */
    private fun makeDistanceMatrixRequest(
        origin: String,
        destination: String,
        apiKey: String,
        callback: (Double?, String?) -> Unit
    ) {
        Thread {
            try {
                val url = "$BASE_URL?origins=$origin&destinations=$destination&key=$apiKey&units=metric"
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                
                val responseCode = connection.responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream.bufferedReader().use { it.readText() }
                }
                
                connection.disconnect()
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val distance = parseDistanceMatrixResponse(response)
                    callback(distance, null)
                } else {
                    callback(null, "API Error: $responseCode")
                }
            } catch (e: Exception) {
                callback(null, e.message)
            }
        }.start()
    }
    
    /**
     * Parses Google Distance Matrix API response to extract distance.
     */
    private fun parseDistanceMatrixResponse(response: String): Double? {
        return try {
            val jsonResponse = JSONObject(response)
            val rows = jsonResponse.getJSONArray("rows")
            
            if (rows.length() > 0) {
                val row = rows.getJSONObject(0)
                val elements = row.getJSONArray("elements")
                
                if (elements.length() > 0) {
                    val element = elements.getJSONObject(0)
                    val status = element.getString("status")
                    
                    if (status == "OK") {
                        val distance = element.getJSONObject("distance")
                        val distanceValue = distance.getInt("value") // Distance in meters
                        return distanceValue / 1000.0 // Convert to kilometers
                    } else {
                        return null
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Mock distance calculation for testing purposes.
     * Used as fallback when API key is not available.
     */
    private fun calculateMockDistance(origin: String, destination: String): Double {
        return try {
            val originParts = origin.split(",")
            val destParts = destination.split(",")
            
            if (originParts.size != 2 || destParts.size != 2) {
                return 0.0
            }
            
            val originLat = originParts[0].toDouble()
            val originLng = originParts[1].toDouble()
            val destLat = destParts[0].toDouble()
            val destLng = destParts[1].toDouble()
            
            calculateHaversineDistance(
                LatLng(originLat, originLng),
                LatLng(destLat, destLng)
            )
        } catch (e: Exception) {
            0.0
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
     * Includes round trip (ida y vuelta) so distance is multiplied by 2.
     */
    fun calculateShippingCost(
        distanceKm: Double,
        fuelPrice: Double,
        litersPerKm: Double
    ): Double {
        val roundTripDistance = distanceKm * 2 // Ida y vuelta
        val cost = (roundTripDistance / litersPerKm) * fuelPrice // Formula: (distancia_ida_y_vuelta / litersPerKm) × fuelPrice
        return cost
    }
}
