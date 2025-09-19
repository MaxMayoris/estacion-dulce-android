package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.models.dtos.ProductDTO

/**
 * Extension functions to convert between Product (Parcelable) and ProductDTO (Firestore)
 */

fun Product.toDTO(): ProductDTO {
    return ProductDTO(
        name = name,
        quantity = quantity,
        minimumQuantity = minimumQuantity,
        cost = cost,
        salePrice = salePrice,
        measure = measure
    )
}

fun ProductDTO.toParcelable(id: String = ""): Product {
    return Product(
        id = id,
        name = name,
        quantity = quantity,
        minimumQuantity = minimumQuantity,
        cost = cost,
        salePrice = salePrice,
        measure = measure
    )
}

/**
 * Convert ProductDTO to Map for Firestore
 */
fun ProductDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "quantity" to quantity,
        "minimumQuantity" to minimumQuantity,
        "cost" to cost,
        "salePrice" to salePrice,
        "measure" to measure
    )
}
