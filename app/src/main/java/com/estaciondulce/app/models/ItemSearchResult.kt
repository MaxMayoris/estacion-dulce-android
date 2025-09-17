package com.estaciondulce.app.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Unified model for search results that can represent either products or recipes.
 */
@Parcelize
data class ItemSearchResult(
    val id: String = "",
    val name: String = "",
    val type: ItemType = ItemType.PRODUCT,
    val price: Double = 0.0,
    val collection: String = "",
    val collectionId: String = ""
) : Parcelable

enum class ItemType {
    PRODUCT,
    RECIPE
}
