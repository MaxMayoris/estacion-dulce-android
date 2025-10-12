package com.estaciondulce.app.helpers

import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.estaciondulce.app.BuildConfig
import com.estaciondulce.app.models.MCPCacheNamespace
import com.estaciondulce.app.models.MCPCacheTTL
import com.estaciondulce.app.models.MCPCachedResource
import com.estaciondulce.app.models.MCPResourceCacheMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.mcpCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "mcp_cache")

/**
 * Manager for MCP resource cache with ETag support
 * Implements conditional caching with ETags and Last-Modified headers
 */
class MCPCacheManager(private val context: Context) {
    
    private val TAG = "MCPCacheManager"
    
    private val namespace = MCPCacheNamespace(
        baseUrl = "mcp://estacion-dulce",
        environment = if (BuildConfig.DEBUG) "dev" else "prod",
        appVersion = BuildConfig.VERSION_NAME
    )
    
    private val memoryCache = LruCache<String, MCPCachedResource>(50)
    
    /**
     * Get cached resource metadata if it exists and is not expired
     * @param uri Resource URI
     * @return Metadata if cache is valid, null otherwise
     */
    suspend fun getCachedMetadata(uri: String): MCPResourceCacheMetadata? {
        val key = namespace.resourceKey(uri)
        
        val cached = memoryCache.get(key)
        if (cached != null) {
            Log.d(TAG, "Memory cache HIT for: $uri")
            return cached.metadata
        }
        
        val metadata = loadMetadataFromDataStore(uri)
        if (metadata != null) {
            Log.d(TAG, "DataStore cache HIT for: $uri")
            return metadata
        }
        
        Log.d(TAG, "Cache MISS for: $uri")
        return null
    }
    
    /**
     * Check if cache is still valid (not expired)
     * @param metadata Cached metadata
     * @return true if cache is valid, false if expired
     */
    fun isCacheValid(metadata: MCPResourceCacheMetadata): Boolean {
        val now = System.currentTimeMillis()
        val isValid = now < metadata.ttlExpiresAt
        
        if (isValid) {
            Log.d(TAG, "Cache VALID for: ${metadata.uri} (expires in ${(metadata.ttlExpiresAt - now) / 1000}s)")
        } else {
            Log.d(TAG, "Cache EXPIRED for: ${metadata.uri}")
        }
        
        return isValid
    }
    
    /**
     * Save resource metadata to cache
     * @param uri Resource URI
     * @param etag ETag from server
     * @param lastModified Last-Modified from server
     * @param dataVersion Version from server
     * @param sizeBytes Payload size
     * @param payload Optional payload data
     */
    suspend fun saveToCache(
        uri: String,
        etag: String?,
        lastModified: String?,
        dataVersion: String?,
        sizeBytes: Int,
        payload: String? = null
    ) {
        val now = System.currentTimeMillis()
        val ttl = MCPCacheTTL.getTTL(uri)
        
        val metadata = MCPResourceCacheMetadata(
            uri = uri,
            etag = etag,
            lastModified = lastModified,
            ttlExpiresAt = now + ttl,
            dataVersion = dataVersion,
            sizeBytes = sizeBytes,
            lastAccess = now
        )
        
        val key = namespace.resourceKey(uri)
        val cachedResource = MCPCachedResource(metadata, payload)
        
        memoryCache.put(key, cachedResource)
        saveMetadataToDataStore(metadata)
        
        Log.d(TAG, "Saved to cache: $uri (ETag: $etag, TTL: ${ttl/1000}s)")
    }
    
    /**
     * Update cache TTL without changing content
     * Used when server responds with 304 Not Modified
     * @param uri Resource URI
     */
    suspend fun refreshTTL(uri: String) {
        val key = namespace.resourceKey(uri)
        val cached = memoryCache.get(key)
        
        if (cached != null) {
            val now = System.currentTimeMillis()
            val ttl = MCPCacheTTL.getTTL(uri)
            
            val updatedMetadata = cached.metadata.copy(
                ttlExpiresAt = now + ttl,
                lastAccess = now
            )
            
            val updatedResource = cached.copy(metadata = updatedMetadata)
            memoryCache.put(key, updatedResource)
            saveMetadataToDataStore(updatedMetadata)
            
            Log.d(TAG, "Refreshed TTL for: $uri (new TTL: ${ttl/1000}s)")
        }
    }
    
    /**
     * Invalidate cache for specific URI
     * @param uri Resource URI to invalidate
     */
    suspend fun invalidate(uri: String) {
        val key = namespace.resourceKey(uri)
        memoryCache.remove(key)
        deleteMetadataFromDataStore(uri)
        Log.d(TAG, "Invalidated cache for: $uri")
    }
    
    /**
     * Invalidate all cache entries
     */
    suspend fun invalidateAll() {
        memoryCache.evictAll()
        clearDataStore()
        Log.d(TAG, "Invalidated ALL cache")
    }
    
    /**
     * Get cached payload if available
     * @param uri Resource URI
     * @return Payload string or null
     */
    fun getCachedPayload(uri: String): String? {
        val key = namespace.resourceKey(uri)
        return memoryCache.get(key)?.payload
    }
    
    private suspend fun loadMetadataFromDataStore(uri: String): MCPResourceCacheMetadata? {
        val key = namespace.resourceKey(uri)
        
        return context.mcpCacheDataStore.data.map { prefs ->
            val etag = prefs[stringPreferencesKey("${key}_etag")]
            val lastModified = prefs[stringPreferencesKey("${key}_lastModified")]
            val ttlExpiresAt = prefs[longPreferencesKey("${key}_ttlExpiresAt")]
            val dataVersion = prefs[stringPreferencesKey("${key}_dataVersion")]
            val sizeBytes = prefs[stringPreferencesKey("${key}_sizeBytes")]?.toIntOrNull()
            val lastAccess = prefs[longPreferencesKey("${key}_lastAccess")]
            
            if (ttlExpiresAt != null) {
                MCPResourceCacheMetadata(
                    uri = uri,
                    etag = etag,
                    lastModified = lastModified,
                    ttlExpiresAt = ttlExpiresAt,
                    dataVersion = dataVersion,
                    sizeBytes = sizeBytes ?: 0,
                    lastAccess = lastAccess ?: System.currentTimeMillis()
                )
            } else {
                null
            }
        }.first()
    }
    
    private suspend fun saveMetadataToDataStore(metadata: MCPResourceCacheMetadata) {
        val key = namespace.resourceKey(metadata.uri)
        
        context.mcpCacheDataStore.edit { prefs ->
            metadata.etag?.let { prefs[stringPreferencesKey("${key}_etag")] = it }
            metadata.lastModified?.let { prefs[stringPreferencesKey("${key}_lastModified")] = it }
            prefs[longPreferencesKey("${key}_ttlExpiresAt")] = metadata.ttlExpiresAt
            metadata.dataVersion?.let { prefs[stringPreferencesKey("${key}_dataVersion")] = it }
            prefs[stringPreferencesKey("${key}_sizeBytes")] = metadata.sizeBytes.toString()
            prefs[longPreferencesKey("${key}_lastAccess")] = metadata.lastAccess
        }
    }
    
    private suspend fun deleteMetadataFromDataStore(uri: String) {
        val key = namespace.resourceKey(uri)
        
        context.mcpCacheDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey("${key}_etag"))
            prefs.remove(stringPreferencesKey("${key}_lastModified"))
            prefs.remove(longPreferencesKey("${key}_ttlExpiresAt"))
            prefs.remove(stringPreferencesKey("${key}_dataVersion"))
            prefs.remove(stringPreferencesKey("${key}_sizeBytes"))
            prefs.remove(longPreferencesKey("${key}_lastAccess"))
        }
    }
    
    private suspend fun clearDataStore() {
        context.mcpCacheDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}


