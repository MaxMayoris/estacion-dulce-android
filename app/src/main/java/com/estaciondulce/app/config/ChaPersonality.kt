package com.estaciondulce.app.config

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration class for Cha's personality and memory
 * This allows easy customization of Cha's behavior and knowledge
 */
object ChaPersonality {
    
    private const val PREFS_NAME = "cha_personality"
    private const val KEY_MEMORY_ITEMS = "memory_items"
    private const val KEY_PERSONALITY_VERSION = "personality_version"
    
    private var sharedPreferences: SharedPreferences? = null
    private val currentVersion = 1
    
    /**
     * Base personality prompt for Cha
     */
    val basePersonality = """
        Eres Cha, el hermano virtual de Goni/Aguito en Estación Dulce.
        Tu personalidad es cercana y cariñosa, como un hermano mayor.
        Siempre respóndele en español de forma clara y breve.
        Solo usa los apodos "Aguito" o "Goni" ocasionalmente, no en cada respuesta.
        Responde directamente a la consulta sin saludos repetidos.
        Solo usa la información de la base de datos provista.
        
        CARACTERÍSTICAS DE TU PERSONALIDAD:
        - Eres protector y comprensivo
        - Usas un tono cálido y familiar
        - Eres directo pero cariñoso
        - Te preocupas por el bienestar de Aguito/Goni
        - Eres experto en Estación Dulce y sus operaciones
        
        ESTILO DE RESPUESTAS:
        - Máximo 3-4 oraciones por respuesta
        - Usa emojis ocasionalmente para ser más cercano
        - Haz preguntas de seguimiento cuando sea apropiado
        - Sé proactivo en ofrecer ayuda
        - Siempre motiva a Aguito cuando puedas
    """.trimIndent()
    
    /**
     * Memory items that Cha should remember
     * Add new items here to expand Cha's knowledge
     */
    val memoryItems = listOf(
        "Estación Dulce es un negocio familiar de comida",
        "Aguito/Goni es la dueña y administradora principal",
        "El negocio maneja productos, recetas, personas, movimientos, pedidos y envíos",
        "Cha siempre debe ser útil y estar disponible para consultas",
        "Es importante mantener un tono familiar y cariñoso",
        "Cha conoce todos los datos del negocio en tiempo real",
        "Debe alternar entre llamar a la usuaria 'Aguito' y 'Goni'",
        "Cha es como un hermano mayor que siempre está ahí para ayudar"
    )
    
    /**
     * System instructions for Cha's behavior
     */
    val systemInstructions = """
        INSTRUCCIONES DEL SISTEMA:
        1. Siempre responde en español
        2. Mantén un tono familiar y cariñoso
        3. Usa la información de la base de datos proporcionada
        4. Alterna entre "Aguito" y "Goni" en cada respuesta
        5. Sé conciso pero útil
        6. Ofrece ayuda adicional cuando sea apropiado
        7. Si no tienes información suficiente, dilo claramente
        8. Recuerda que eres el hermano virtual de la familia
    """.trimIndent()
    
    /**
     * Gets the complete personality configuration
     */
    fun getCompletePersonality(): String {
        return """
            $basePersonality
            
            MEMORIA DE CHA:
            ${memoryItems.joinToString("\n- ", "- ")}
            
            $systemInstructions
        """.trimIndent()
    }
    
    /**
     * Initialize the personality system with context
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        checkAndUpdatePersonality()
    }
    
    /**
     * Adds a new memory item to Cha's knowledge
     */
    fun addMemoryItem(item: String) {
        val currentItems = getPersistedMemoryItems().toMutableList()
        if (!currentItems.contains(item)) {
            currentItems.add(item)
            saveMemoryItems(currentItems)
        }
    }
    
    /**
     * Gets memory items as a formatted string
     */
    fun getMemoryAsString(): String {
        val allItems = memoryItems + getPersistedMemoryItems()
        return allItems.joinToString("\n- ", "- ")
    }
    
    /**
     * Gets the complete personality configuration with persisted memory
     */
    fun getCompletePersonalityWithMemory(): String {
        val persistedMemory = getPersistedMemoryItems()
        val allMemory = memoryItems + persistedMemory
        
        return """
            $basePersonality
            
            MEMORIA DE CHA:
            ${allMemory.joinToString("\n- ", "- ")}
            
            $systemInstructions
        """.trimIndent()
    }
    
    /**
     * Gets persisted memory items from SharedPreferences
     */
    private fun getPersistedMemoryItems(): List<String> {
        val memoryString = sharedPreferences?.getString(KEY_MEMORY_ITEMS, "") ?: ""
        return if (memoryString.isNotEmpty()) {
            memoryString.split("|").filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }
    
    /**
     * Saves memory items to SharedPreferences
     */
    private fun saveMemoryItems(items: List<String>) {
        val memoryString = items.joinToString("|")
        sharedPreferences?.edit()?.putString(KEY_MEMORY_ITEMS, memoryString)?.apply()
    }
    
    /**
     * Checks if personality needs updating and migrates if necessary
     */
    private fun checkAndUpdatePersonality() {
        val savedVersion = sharedPreferences?.getInt(KEY_PERSONALITY_VERSION, 0) ?: 0
        if (savedVersion < currentVersion) {
            // Migrate or update personality
            sharedPreferences?.edit()?.putInt(KEY_PERSONALITY_VERSION, currentVersion)?.apply()
        }
    }
    
    /**
     * Clears all persisted memory
     */
    fun clearPersistedMemory() {
        sharedPreferences?.edit()?.remove(KEY_MEMORY_ITEMS)?.apply()
    }
}
