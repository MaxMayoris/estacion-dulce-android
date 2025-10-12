package com.estaciondulce.app.models

/**
 * Response from AI Gateway Firebase Function
 */
data class AIGatewayResponse(
    val reply: String,
    val usage: AIUsage,
    val requestId: String,
    val mcpMetadata: MCPResponseMetadata? = null
)

/**
 * Token usage information from OpenAI
 */
data class AIUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

/**
 * MCP resource metadata from server response
 */
data class MCPResponseMetadata(
    val etag: String?,
    val lastModified: String?,
    val dataVersion: String?,
    val resourcesUsed: List<String>?
)


