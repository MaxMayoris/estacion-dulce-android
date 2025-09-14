package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Movement

class MovementsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun addMovement(movement: Movement, onSuccess: (Movement) -> Unit, onError: (Exception) -> Unit) {
        val movementData = mapOf(
            "type" to movement.type?.name,
            "personId" to movement.personId,
            "movementDate" to movement.movementDate,
            "totalAmount" to movement.totalAmount,
            "items" to movement.items,
            "shipment" to movement.shipment,
            "delta" to movement.delta,
            "appliedAt" to movement.appliedAt,
            "createdAt" to movement.createdAt
        )
        genericHelper.addDocument(
            collectionName = "movements",
            data = movementData,
            onSuccess = { documentId ->
                onSuccess(movement.copy(id = documentId))
            },
            onError = onError
        )
    }

    fun updateMovement(movementId: String, movement: Movement, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val movementData = mapOf(
            "type" to movement.type?.name,
            "personId" to movement.personId,
            "movementDate" to movement.movementDate,
            "totalAmount" to movement.totalAmount,
            "items" to movement.items,
            "shipment" to movement.shipment,
            "delta" to movement.delta,
            "appliedAt" to movement.appliedAt,
            "createdAt" to movement.createdAt
        )
        genericHelper.updateDocument(
            collectionName = "movements",
            documentId = movementId,
            data = movementData,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun deleteMovement(movementId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        genericHelper.deleteDocument(
            collectionName = "movements",
            documentId = movementId,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}
