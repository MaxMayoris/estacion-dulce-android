package com.estaciondulce.app.models

/**
 * Metadata for cached MCP resources
 */
data class MCPResourceCacheMetadata(
    val uri: String,
    val etag: String?,
    val lastModified: String?,
    val ttlExpiresAt: Long,
    val dataVersion: String?,
    val sizeBytes: Int,
    val lastAccess: Long = System.currentTimeMillis()
)

/**
 * Full cached resource with payload
 */
data class MCPCachedResource(
    val metadata: MCPResourceCacheMetadata,
    val payload: String?
)

/**
 * Cache namespace configuration
 */
data class MCPCacheNamespace(
    val baseUrl: String,
    val environment: String,
    val appVersion: String
) {
    fun toKey(): String = "$baseUrl|$environment|$appVersion"
    
    fun resourceKey(uri: String): String = "${toKey()}|$uri"
}

/**
 * TTL configuration for different resource types
 */
object MCPCacheTTL {
    const val PRODUCTS_INDEX = 90_000L // 90 seconds (60-120s)
    const val RECIPES_INDEX = 600_000L // 10 minutes (5-15 min)
    const val PERSONS_INDEX = 2_700_000L // 45 minutes (15-60 min)
    const val MOVEMENTS_LAST_30D = 180_000L // 3 minutes (1-5 min)
    const val DEFAULT = 120_000L // 2 minutes default
    
    /**
     * Get TTL based on resource URI
     */
    fun getTTL(uri: String): Long {
        return when {
            uri.contains("products#index") -> PRODUCTS_INDEX
            uri.contains("recipes#index") -> RECIPES_INDEX
            uri.contains("persons#index") -> PERSONS_INDEX
            uri.contains("movements#last-30d") -> MOVEMENTS_LAST_30D
            else -> DEFAULT
        }
    }
}


