package com.estaciondulce.app.helpers

import android.content.Context
import android.util.Log
import com.estaciondulce.app.BuildConfig
import com.estaciondulce.app.models.parcelables.Movement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Helper class for Google Routes API operations.
 * Uses the official Google Routes API for route optimization.
 */
class GoogleRoutesHelper(private val context: Context) {
    
    private val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
    private val routesApiUrl = "https://routes.googleapis.com/directions/v2:computeRoutes"
    
    /**
     * Calculates an optimized route for multiple destinations using Google Routes API.
     * @param origin Origin coordinates (lat, lng)
     * @param destinations List of destination coordinates
     * @param onSuccess Callback with the optimized route result
     * @param onError Callback with error information
     */
    suspend fun calculateOptimizedRoute(
        origin: Pair<Double, Double>,
        destinations: List<Pair<Double, Double>>,
        onSuccess: (RoutesApiResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            
            withContext(Dispatchers.IO) {
                val requestBody = buildRoutesRequest(origin, destinations)
                val response = makeHttpRequest(requestBody)
                
                withContext(Dispatchers.Main) {
                    onSuccess(response)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e)
            }
        }
    }
    
    /**
     * Calculates route for multiple shipments with optimization.
     * @param baseAddress Base address coordinates
     * @param movements List of movements to optimize
     * @param onSuccess Callback with the optimized route result
     * @param onError Callback with error information
     */
    suspend fun calculateShipmentRoute(
        baseAddress: Pair<Double, Double>,
        movements: List<Movement>,
        onSuccess: (RoutesApiResponse) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val destinations = movements.mapNotNull { movement ->
            val delivery = movement.delivery
            val shipment = delivery?.shipment
            if (shipment?.lat != null) {
                Pair(shipment.lat, shipment.lng)
            } else null
        }
        
        if (destinations.isEmpty()) {
            onError(Exception("No valid destinations found"))
            return
        }
        
        calculateOptimizedRoute(baseAddress, destinations, onSuccess, onError)
    }
    
    /**
     * Builds the request body for Google Routes API.
     */
    private fun buildRoutesRequest(
        origin: Pair<Double, Double>,
        destinations: List<Pair<Double, Double>>
    ): JSONObject {
        val request = JSONObject()
        
        val originLocation = JSONObject()
        originLocation.put("location", JSONObject().apply {
            put("latLng", JSONObject().apply {
                put("latitude", origin.first)
                put("longitude", origin.second)
            })
        })
        request.put("origin", originLocation)
        
        val destinationLocation = JSONObject()
        destinationLocation.put("location", JSONObject().apply {
            put("latLng", JSONObject().apply {
                put("latitude", origin.first)
                put("longitude", origin.second)
            })
        })
        request.put("destination", destinationLocation)
        
        val intermediatesArray = org.json.JSONArray()
        destinations.forEach { dest ->
            val waypoint = JSONObject()
            waypoint.put("location", JSONObject().apply {
                put("latLng", JSONObject().apply {
                    put("latitude", dest.first)
                    put("longitude", dest.second)
                })
            })
            intermediatesArray.put(waypoint)
        }
        request.put("intermediates", intermediatesArray)
        
        request.put("travelMode", "DRIVE")
        
        request.put("routingPreference", "TRAFFIC_AWARE")
        
        request.put("optimizeWaypointOrder", true)
        
        return request
    }
    
    /**
     * Makes HTTP request to Google Routes API.
     */
    private fun makeHttpRequest(requestBody: JSONObject): RoutesApiResponse {
        val url = URL("$routesApiUrl?key=$apiKey")
        val connection = url.openConnection() as HttpURLConnection
        
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.legs,routes.optimizedIntermediateWaypointIndex,routes.polyline")
            connection.doOutput = true
            
            connection.outputStream.use { outputStream ->
                outputStream.write(requestBody.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            
            val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                BufferedReader(InputStreamReader(connection.errorStream)).use { reader ->
                    reader.readText()
                }
            }
            
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return parseRoutesResponse(response)
            } else {
                throw Exception("API Error $responseCode: $response")
            }
        } catch (e: Exception) {
            throw e
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Parses the Google Routes API response.
     */
    private fun parseRoutesResponse(response: String): RoutesApiResponse {
        try {
            
            val jsonResponse = JSONObject(response)
            
            val routes = jsonResponse.getJSONArray("routes")
            
            if (routes.length() > 0) {
                val route = routes.getJSONObject(0)
                
                val duration = try {
                    val durationObj = route.getJSONObject("duration")
                    durationObj.getString("seconds").toLong()
                } catch (e: Exception) {
                    val durationStr = route.getString("duration")
                    if (durationStr.endsWith("s")) {
                        durationStr.substring(0, durationStr.length - 1).toLong()
                    } else {
                        durationStr.toLong()
                    }
                }
                
                val distanceMeters = route.getInt("distanceMeters")
                
                val routeCoordinates = try {
                    val polyline = route.getJSONObject("polyline")
                    val encodedPolyline = polyline.getString("encodedPolyline")
                    decodePolyline(encodedPolyline)
                } catch (e: Exception) {
                    emptyList()
                }
                
        val optimizedWaypointOrder = try {
            if (route.has("optimizedIntermediateWaypointIndex")) {
                val waypointOrder = route.getJSONArray("optimizedIntermediateWaypointIndex")
                val order = mutableListOf<Int>()
                for (i in 0 until waypointOrder.length()) {
                    order.add(waypointOrder.getInt(i))
                }
                order
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
                
                
                return RoutesApiResponse(
                    durationSeconds = duration,
                    distanceMeters = distanceMeters,
                    success = true,
                    routeCoordinates = routeCoordinates,
                    optimizedWaypointOrder = optimizedWaypointOrder
                )
            } else {
                throw Exception("No routes found in response")
            }
        } catch (e: Exception) {
            throw Exception("Error parsing response: ${e.message}")
        }
    }
    
    /**
     * Decodes Google's encoded polyline to get route coordinates.
     */
    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val poly = mutableListOf<Pair<Double, Double>>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            
            poly.add(Pair(lat / 1E5, lng / 1E5))
        }
        
        return poly
    }
}

/**
 * Data class for Google Routes API response.
 */
data class RoutesApiResponse(
    val durationSeconds: Long,
    val distanceMeters: Int,
    val success: Boolean,
    val routeCoordinates: List<Pair<Double, Double>> = emptyList(),
    val optimizedWaypointOrder: List<Int> = emptyList()
)
