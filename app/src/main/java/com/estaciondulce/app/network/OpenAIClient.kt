package com.estaciondulce.app.network

import android.util.Log
import com.estaciondulce.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for making HTTP requests to OpenAI API
 */
object OpenAIClient {
    
    private const val TAG = "OpenAIClient"
    private const val OPENAI_API_URL = "https://api.openai.com/v1/chat/completions"
    
    /**
     * Sends a request to OpenAI API and returns the response
     * @param prompt The prompt to send to the AI
     * @return The AI response or null if error
     */
    suspend fun getAIResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.OPENAI_API_KEY
            if (apiKey.isBlank() || apiKey == "tu_api_key_aqui") {
                Log.e(TAG, "OpenAI API key not configured")
                return@withContext null
            }
            
            val url = URL(OPENAI_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            
            val messagesArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            
            val requestBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", messagesArray as Any)
                put("max_tokens", 500)
                put("temperature", 0.7)
            }
            
            val outputStream = connection.outputStream
            outputStream.write(requestBody.toString().toByteArray())
            outputStream.flush()
            outputStream.close()
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                val content = message.getString("content")
                
                Log.d(TAG, "OpenAI response received successfully")
                Log.d(TAG, "Response content: $content")
                return@withContext content.trim()
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "OpenAI API HTTP Error: $responseCode")
                Log.e(TAG, "Error Response: $errorResponse")
                Log.e(TAG, "Request URL: $OPENAI_API_URL")
                Log.e(TAG, "Request Body: $requestBody")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling OpenAI API", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            return@withContext null
        }
    }
}

