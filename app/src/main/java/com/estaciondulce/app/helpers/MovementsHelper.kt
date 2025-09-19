package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.parcelables.KitchenOrder
import com.estaciondulce.app.models.enums.EKitchenOrderStatus
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.dtos.MovementDTO
import com.estaciondulce.app.models.mappers.*
import com.estaciondulce.app.repository.FirestoreRepository
import java.util.*

class MovementsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    private val kitchenOrdersHelper = KitchenOrdersHelper()
    

    fun addMovement(movement: Movement, onSuccess: (Movement) -> Unit, onError: (Exception) -> Unit) {
        val movementDTO = movement.toDTO()
        val movementData = movementDTO.toMap()
        genericHelper.addDocument(
            collectionName = "movements",
            data = movementData,
            onSuccess = { documentId ->
                val newMovement = movement.copy(id = documentId)
                
                // Create kitchen orders for sales
                if (movement.type == EMovementType.SALE) {
                    val movementWithStatus = newMovement.copy(kitchenOrderStatus = EKitchenOrderStatus.PENDING)
                    createKitchenOrdersForMovement(movementWithStatus, onSuccess, onError)
                } else {
                    onSuccess(newMovement)
                }
            },
            onError = onError
        )
    }

    fun updateMovement(movementId: String, movement: Movement, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        updateMovement(movementId, movement, updateKitchenOrders = true, onSuccess, onError)
    }

    fun updateMovement(movementId: String, movement: Movement, updateKitchenOrders: Boolean, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val movementDTO = movement.toDTO()
        val movementData = movementDTO.toMap()
        genericHelper.updateDocument(
            collectionName = "movements",
            documentId = movementId,
            data = movementData,
            onSuccess = {
                // Update kitchen orders for sales only if requested and items changed
                if (movement.type == EMovementType.SALE && updateKitchenOrders) {
                    updateKitchenOrdersForMovement(movementId, movement, onSuccess, onError)
                } else {
                    onSuccess()
                }
            },
            onError = onError
        )
    }

    fun updateMovementKitchenOrderStatusOnly(
        movementId: String, 
        kitchenOrderStatus: EKitchenOrderStatus?, 
        onSuccess: () -> Unit, 
        onError: (Exception) -> Unit
    ) {
        val movementData = mapOf(
            "kitchenOrderStatus" to kitchenOrderStatus?.name
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
        // Delete kitchen orders first
        kitchenOrdersHelper.deleteKitchenOrdersForMovement(
            movementId = movementId,
            onSuccess = {
                // Then delete the movement
                genericHelper.deleteDocument(
                    collectionName = "movements",
                    documentId = movementId,
                    onSuccess = onSuccess,
                    onError = onError
                )
            },
            onError = onError
        )
    }

    /**
     * Create kitchen orders for a movement
     */
    private fun createKitchenOrdersForMovement(
        movement: Movement,
        onSuccess: (Movement) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val kitchenOrders = movement.items.mapNotNull { item ->
            when (item.collection) {
                "recipes" -> {
                    val recipe = FirestoreRepository.recipesLiveData.value?.find { it.id == item.collectionId }
                    recipe?.let {
                        KitchenOrder(
                            productId = item.collectionId,
                            name = it.name,
                            quantity = item.quantity,
                            status = EKitchenOrderStatus.PENDING,
                            createdAt = Date(),
                            updatedAt = Date()
                        )
                    }
                }
                "products" -> {
                    val product = FirestoreRepository.productsLiveData.value?.find { it.id == item.collectionId }
                    product?.let {
                        KitchenOrder(
                            productId = item.collectionId,
                            name = it.name,
                            quantity = item.quantity,
                            status = EKitchenOrderStatus.PENDING,
                            createdAt = Date(),
                            updatedAt = Date()
                        )
                    }
                }
                else -> null
            }
        }

        if (kitchenOrders.isNotEmpty()) {
            kitchenOrdersHelper.addKitchenOrdersForMovement(
                movementId = movement.id,
                kitchenOrders = kitchenOrders,
                onSuccess = { 
                    // Update movement with PENDING status after creating kitchen orders
                    val movementData = mapOf(
                        "kitchenOrderStatus" to EKitchenOrderStatus.PENDING.name
                    )
                    genericHelper.updateDocument(
                        collectionName = "movements",
                        documentId = movement.id,
                        data = movementData,
                        onSuccess = { onSuccess(movement) },
                        onError = onError
                    )
                },
                onError = onError
            )
        } else {
            onSuccess(movement)
        }
    }

    /**
     * Update kitchen orders for a movement
     */
    private fun updateKitchenOrdersForMovement(
        movementId: String,
        movement: Movement,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Delete existing kitchen orders
        kitchenOrdersHelper.deleteKitchenOrdersForMovement(
            movementId = movementId,
            onSuccess = {
                // Create new kitchen orders
                createKitchenOrdersForMovement(
                    movement = movement,
                    onSuccess = { onSuccess() },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    /**
     * Update movement kitchen order status based on kitchen orders
     */
    fun updateMovementKitchenOrderStatus(
        movementId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Get current movement
        val movements = FirestoreRepository.movementsLiveData.value ?: emptyList()
        val movement = movements.find { it.id == movementId }
        
        if (movement == null || movement.type != EMovementType.SALE) {
            onSuccess()
            return
        }

        // Get kitchen orders for this movement
        kitchenOrdersHelper.getKitchenOrdersForMovement(
            movementId = movementId,
            onSuccess = { kitchenOrders ->
                val newStatus = calculateMovementKitchenOrderStatus(kitchenOrders)
                
                // Update movement with new status
                val movementData = mapOf(
                    "kitchenOrderStatus" to newStatus?.name
                )
                
                genericHelper.updateDocument(
                    collectionName = "movements",
                    documentId = movementId,
                    data = movementData,
                    onSuccess = onSuccess,
                    onError = onError
                )
            },
            onError = onError
        )
    }

    /**
     * Calculate movement kitchen order status based on kitchen orders
     */
    private fun calculateMovementKitchenOrderStatus(kitchenOrders: List<KitchenOrder>): EKitchenOrderStatus? {
        if (kitchenOrders.isEmpty()) return null

        val allDone = kitchenOrders.all { it.status == EKitchenOrderStatus.DONE }
        val allReady = kitchenOrders.all { it.status == EKitchenOrderStatus.READY }
        val allCanceled = kitchenOrders.all { it.status == EKitchenOrderStatus.CANCELED }
        val hasReady = kitchenOrders.any { it.status == EKitchenOrderStatus.READY }
        val hasPreparing = kitchenOrders.any { it.status == EKitchenOrderStatus.PREPARING }

        return when {
            allDone -> EKitchenOrderStatus.DONE
            allCanceled -> EKitchenOrderStatus.CANCELED
            allReady -> EKitchenOrderStatus.READY
            hasReady || hasPreparing -> EKitchenOrderStatus.PREPARING
            else -> EKitchenOrderStatus.PENDING
        }
    }
}
