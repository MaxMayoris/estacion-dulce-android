package com.estaciondulce.app.models

/**
 * Enum for person types with both English database values and Spanish display values.
 */
enum class EPersonType(val dbValue: String, val displayValue: String) {
    CLIENT("CLIENT", "Cliente"),
    PROVIDER("PROVIDER", "Proveedor");
    
    companion object {
        /**
         * Get the display value from database value.
         */
        fun getDisplayValue(dbValue: String): String {
            return values().find { it.dbValue == dbValue }?.displayValue ?: dbValue
        }
        
        /**
         * Get the database value from display value.
         */
        fun getDbValue(displayValue: String): String {
            return values().find { it.displayValue == displayValue }?.dbValue ?: displayValue
        }
        
        /**
         * Get enum from database value.
         */
        fun fromDbValue(dbValue: String): EPersonType? {
            return values().find { it.dbValue == dbValue }
        }
        
        /**
         * Get enum from display value.
         */
        fun fromDisplayValue(displayValue: String): EPersonType? {
            return values().find { it.displayValue == displayValue }
        }
    }
}




