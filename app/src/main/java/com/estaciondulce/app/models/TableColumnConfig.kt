package com.estaciondulce.app.models

/**
 * Configuration for table columns to specify formatting options.
 */
data class TableColumnConfig(
    val header: String,
    val isCurrency: Boolean = false
)

/**
 * Extension function to create a list of column configs from headers with currency flags.
 */
fun List<String>.toColumnConfigs(currencyColumns: Set<Int> = emptySet()): List<TableColumnConfig> {
    return this.mapIndexed { index, header ->
        TableColumnConfig(
            header = header,
            isCurrency = currencyColumns.contains(index)
        )
    }
}

