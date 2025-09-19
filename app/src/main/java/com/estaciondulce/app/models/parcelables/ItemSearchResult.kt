package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import com.estaciondulce.app.models.enums.EItemType

/**
 * Unified model for search results that can represent either products or recipes.
 */
@Parcelize
data class ItemSearchResult(
    val id: String = "",
    val name: String = "",
    val type: EItemType = EItemType.PRODUCT,
    val price: Double = 0.0,
    val collection: String = "",
    val collectionId: String = ""
) : Parcelable
