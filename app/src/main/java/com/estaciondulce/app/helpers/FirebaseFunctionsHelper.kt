package com.estaciondulce.app.helpers

import android.content.Context
import android.util.Log
import com.estaciondulce.app.models.AIGatewayResponse
import com.estaciondulce.app.models.AIUsage
import com.estaciondulce.app.models.MCPResponseMetadata
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Helper class for calling Firebase Cloud Functions
 */
object FirebaseFunctionsHelper {
    
    private const val TAG = "FirebaseFunctionsHelper"
    private val functions: FirebaseFunctions by lazy {
        Firebase.functions("southamerica-east1")
    }
    
    private var cacheManager: MCPCacheManager? = null
    private val chatSessionsStarted = mutableSetOf<String>()
    
    /**
     * Initialize cache manager (call once from Application.onCreate)
     */
    fun initialize(context: Context) {
        cacheManager = MCPCacheManager(context)
    }
    
    /**
     * Calls the AI Gateway MCP function to get a response from the AI assistant with MCP server integration
     * Implements ETag-based caching for MCP resources
     * @param chatId Unique identifier for the chat conversation
     * @param userMessage User's message/question
     * @param context Optional context data (formatted string from ContextSelector)
     * @return AI response or null if error
     */
    suspend fun callAIGatewayMCP(
        chatId: String,
        userMessage: String,
        context: String? = null
    ): AIGatewayResponse? {
        return try {
            val requestId = UUID.randomUUID().toString()
            
            val isFirstMessage = !chatSessionsStarted.contains(chatId)
            if (isFirstMessage) {
                chatSessionsStarted.add(chatId)
                Log.d(TAG, "First message in chat: $chatId - sending full MCP instructions")
            }
            
            val data = hashMapOf(
                "chatId" to chatId,
                "userMessage" to userMessage,
                "requestId" to requestId,
                "isFirstMessage" to isFirstMessage
            )
            
            if (context != null) {
                data["context"] = context
            }
            
            Log.d(TAG, "Calling aiGatewayMCP function for chatId: $chatId")
            Log.d(TAG, "Request ID: $requestId")
            
            val result = functions
                .getHttpsCallable("aiGatewayMCP")
                .call(data)
                .await()
            
            val responseData = result.getData() as? Map<*, *>
            
            if (responseData == null) {
                Log.e(TAG, "Invalid response format from aiGatewayMCP")
                return null
            }
            
            val reply = responseData["reply"] as? String
            val usageData = responseData["usage"] as? Map<*, *>
            val responseRequestId = responseData["requestId"] as? String
            val mcpMetadataData = responseData["mcpMetadata"] as? Map<*, *>
            
            if (reply == null || usageData == null) {
                Log.e(TAG, "Missing required fields in response")
                return null
            }
            
            val usage = AIUsage(
                inputTokens = (usageData["inputTokens"] as? Number)?.toInt() ?: 0,
                outputTokens = (usageData["outputTokens"] as? Number)?.toInt() ?: 0,
                totalTokens = (usageData["totalTokens"] as? Number)?.toInt() ?: 0
            )
            
            var mcpMetadata: MCPResponseMetadata? = null
            if (mcpMetadataData != null) {
                mcpMetadata = MCPResponseMetadata(
                    etag = mcpMetadataData["etag"] as? String,
                    lastModified = mcpMetadataData["lastModified"] as? String,
                    dataVersion = mcpMetadataData["dataVersion"] as? String,
                    resourcesUsed = (mcpMetadataData["resourcesUsed"] as? List<*>)?.mapNotNull { it as? String }
                )
            }
            
            Log.d(TAG, "AI Gateway MCP response received successfully")
            Log.d(TAG, "Reply length: ${reply.length} chars")
            Log.d(TAG, "Token usage: ${usage.totalTokens} tokens (input: ${usage.inputTokens}, output: ${usage.outputTokens})")
            
            AIGatewayResponse(
                reply = reply,
                usage = usage,
                requestId = responseRequestId ?: requestId,
                mcpMetadata = mcpMetadata
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error calling aiGatewayMCP", e)
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            null
        }
    }
}

