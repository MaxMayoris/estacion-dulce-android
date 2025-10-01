package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.parcelables.KitchenOrder
import com.estaciondulce.app.models.enums.EKitchenOrderStatus
import com.estaciondulce.app.models.enums.EKitchenOrderItemStatus
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.dtos.MovementDTO
import com.estaciondulce.app.models.mappers.*
import com.estaciondulce.app.repository.FirestoreRepository
import java.util.*

/**
 * Represents the comparison result between items in original and edited movements
 */
sealed class ItemComparison {
    object SAME_QUANTITY : ItemComparison()
    data class DIFFERENT_QUANTITY(val newQuantity: Double) : ItemComparison()
    object REMOVED : ItemComparison()
    object NEW : ItemComparison()
}

class MovementsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    private val kitchenOrdersHelper = KitchenOrdersHelper()
    

    fun addMovement(movement: Movement, onSuccess: (Movement) -> Unit, onError: (Exception) -> Unit) {
        addMovement(movement, createKitchenOrders = true, onSuccess, onError)
    }

    fun addMovement(movement: Movement, createKitchenOrders: Boolean, onSuccess: (Movement) -> Unit, onError: (Exception) -> Unit) {
        val movementDTO = movement.toDTO()
        val movementData = movementDTO.toMap()
        genericHelper.addDocument(
            collectionName = "movements",
            data = movementData,
            onSuccess = { documentId ->
                val newMovement = movement.copy(id = documentId)
                
                if (movement.type == EMovementType.SALE && createKitchenOrders) {
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
        genericHelper.getDocument(
            collectionName = "movements",
            documentId = movementId,
            onSuccess = { document ->
                val movementDTO = document.toObject(MovementDTO::class.java)
                val movement = movementDTO?.toParcelable()
                
        kitchenOrdersHelper.deleteKitchenOrdersForMovement(
            movementId = movementId,
            onSuccess = {
                        if (movement != null && movement.type == EMovementType.SALE && movement.referenceImages.isNotEmpty()) {
                            val storageHelper = StorageHelper()
                            storageHelper.deleteImagesFromStorage(
                                imageUrls = movement.referenceImages,
                                onSuccess = {
                genericHelper.deleteDocument(
                    collectionName = "movements",
                    documentId = movementId,
                    onSuccess = onSuccess,
                                        onError = onError
                                    )
                                },
                                onError = { _ ->
                                    genericHelper.deleteDocument(
                                        collectionName = "movements",
                                        documentId = movementId,
                                        onSuccess = onSuccess,
                                        onError = onError
                                    )
                                }
                            )
                        } else {
                            genericHelper.deleteDocument(
                                collectionName = "movements",
                                documentId = movementId,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        }
                    },
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
            if (item.collection == "custom" && item.customName == "discount") {
                return@mapNotNull null
            }
            
            when (item.collection) {
                "recipes" -> {
                    val recipe = FirestoreRepository.recipesLiveData.value?.find { it.id == item.collectionId }
                    recipe?.let {
                        KitchenOrder(
                            collection = item.collection,
                            collectionId = item.collectionId,
                            customName = null,
                            name = it.name,
                            quantity = item.quantity,
                            status = EKitchenOrderItemStatus.PENDING,
                            createdAt = Date(),
                            updatedAt = Date()
                        )
                    }
                }
                "products" -> {
                    val product = FirestoreRepository.productsLiveData.value?.find { it.id == item.collectionId }
                    product?.let {
                        KitchenOrder(
                            collection = item.collection,
                            collectionId = item.collectionId,
                            customName = null,
                            name = it.name,
                            quantity = item.quantity,
                            status = EKitchenOrderItemStatus.PENDING,
                            createdAt = Date(),
                            updatedAt = Date()
                        )
                    }
                }
                "custom" -> {
                    KitchenOrder(
                        collection = item.collection,
                        collectionId = item.collectionId,
                        customName = item.customName,
                        name = item.customName ?: "Custom Item",
                        quantity = item.quantity,
                        status = EKitchenOrderItemStatus.PENDING,
                        createdAt = Date(),
                        updatedAt = Date()
                    )
                }
                else -> null
            }
        }

        if (kitchenOrders.isNotEmpty()) {
            kitchenOrdersHelper.addKitchenOrdersForMovement(
                movementId = movement.id,
                kitchenOrders = kitchenOrders,
                onSuccess = { 
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
        kitchenOrdersHelper.deleteKitchenOrdersForMovement(
            movementId = movementId,
            onSuccess = {
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
     * Preserve and update kitchen orders when editing a movement
     */
    fun preserveKitchenOrdersForEditedMovement(
        originalMovement: Movement,
        newMovement: Movement,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        kitchenOrdersHelper.getKitchenOrdersForMovement(
            movementId = originalMovement.id,
            onSuccess = { originalKitchenOrders ->
                val itemComparison = compareMovementItems(originalMovement.items, newMovement.items)
                
                
                
                val finalKitchenOrders = mutableListOf<KitchenOrder>()
                
                newMovement.items.forEach { newItem ->
                    val itemKey = "${newItem.collection}_${newItem.collectionId}_${newItem.customName ?: ""}"
                    val comparison = itemComparison[itemKey]
                    
                    when (comparison) {
                        ItemComparison.SAME_QUANTITY -> {
                            val originalKitchenOrder = originalKitchenOrders.find { original ->
                                "${original.collection}_${original.collectionId}_${original.customName ?: ""}" == itemKey
                            }
                            originalKitchenOrder?.let { original ->
                                val preservedKitchenOrder = KitchenOrder(
                                    collection = newItem.collection,
                                    collectionId = newItem.collectionId,
                                    customName = newItem.customName,
                                    name = getItemName(newItem),
                                    quantity = newItem.quantity,
                                    status = original.status, // Preserve original status
                                    createdAt = original.createdAt, // Preserve original creation date
                                    updatedAt = Date() // Update modification date
                                )
                                finalKitchenOrders.add(preservedKitchenOrder)
                            }
                        }
                        is ItemComparison.DIFFERENT_QUANTITY -> {
                            val newKitchenOrder = KitchenOrder(
                                collection = newItem.collection,
                                collectionId = newItem.collectionId,
                                customName = newItem.customName,
                                name = getItemName(newItem),
                                quantity = comparison.newQuantity,
                                status = EKitchenOrderItemStatus.PENDING, // Reset to PENDING due to quantity change
                                createdAt = Date(), // New creation date for the modified item
                                updatedAt = Date()
                            )
                            finalKitchenOrders.add(newKitchenOrder)
                        }
                        ItemComparison.NEW -> {
                            val newKitchenOrder = KitchenOrder(
                                collection = newItem.collection,
                                collectionId = newItem.collectionId,
                                customName = newItem.customName,
                                name = getItemName(newItem),
                                quantity = newItem.quantity,
                                status = EKitchenOrderItemStatus.PENDING,
                                createdAt = Date(),
                                updatedAt = Date()
                            )
                            finalKitchenOrders.add(newKitchenOrder)
                        }
                        ItemComparison.REMOVED -> {
                        }
                        null -> {
                        }
                    }
                }


                if (finalKitchenOrders.isNotEmpty()) {
                    kitchenOrdersHelper.addKitchenOrdersForMovement(
                        movementId = newMovement.id,
                        kitchenOrders = finalKitchenOrders,
                        onSuccess = {
                            val newStatus = calculateMovementKitchenOrderStatus(finalKitchenOrders)
                            val movementData = mapOf(
                                "kitchenOrderStatus" to newStatus?.name
                            )
                            genericHelper.updateDocument(
                                collectionName = "movements",
                                documentId = newMovement.id,
                                data = movementData,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        },
                        onError = onError
                    )
                } else {
                    onSuccess()
                }
            },
            onError = onError
        )
    }

    /**
     * Update the status of specific kitchen orders to preserve their original status
     */
    private fun updateKitchenOrderStatuses(
        movementId: String,
        statusUpdates: List<KitchenOrder>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        kitchenOrdersHelper.getKitchenOrdersForMovement(
            movementId = movementId,
            onSuccess = { allKitchenOrders ->
                val batch = com.google.firebase.firestore.FirebaseFirestore.getInstance().batch()
                var updatesCount = 0
                
                statusUpdates.forEach { targetKitchenOrder ->
                    val kitchenOrderToUpdate = allKitchenOrders.find { existing ->
                        existing.collection == targetKitchenOrder.collection &&
                        existing.collectionId == targetKitchenOrder.collectionId &&
                        existing.customName == targetKitchenOrder.customName &&
                        existing.quantity == targetKitchenOrder.quantity
                    }
                    
                    if (kitchenOrderToUpdate != null) {
                        val kitchenOrderRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("movements")
                            .document(movementId)
                            .collection("kitchenOrders")
                            .document(kitchenOrderToUpdate.id)
                        
                        val updateData = mapOf(
                            "status" to targetKitchenOrder.status.name,
                            "updatedAt" to Date()
                        )
                        
                        batch.update(kitchenOrderRef, updateData)
                        updatesCount++
                    }
                }
                
                if (updatesCount > 0) {
                    batch.commit()
                        .addOnSuccessListener {
                            val newStatus = calculateMovementKitchenOrderStatus(allKitchenOrders.map { existing ->
                                val targetUpdate = statusUpdates.find { target ->
                                    existing.collection == target.collection &&
                                    existing.collectionId == target.collectionId &&
                                    existing.customName == target.customName &&
                                    existing.quantity == target.quantity
                                }
                                if (targetUpdate != null) {
                                    existing.copy(status = targetUpdate.status)
                                } else {
                                    existing
                                }
                            })
                            val movementData = mapOf(
                                "kitchenOrderStatus" to newStatus?.name
                            )
                            val genericHelper = com.estaciondulce.app.helpers.GenericHelper()
                            genericHelper.updateDocument(
                                collectionName = "movements",
                                documentId = movementId,
                                data = movementData,
                                onSuccess = onSuccess,
                                onError = onError
                            )
                        }
                        .addOnFailureListener { onError(it) }
                } else {
                    onSuccess()
                }
            },
            onError = onError
        )
    }

    /**
     * Compare items between original and edited movements
     */
    private fun compareMovementItems(
        originalItems: List<com.estaciondulce.app.models.parcelables.MovementItem>,
        newItems: List<com.estaciondulce.app.models.parcelables.MovementItem>
    ): Map<String, ItemComparison> {
        val comparison = mutableMapOf<String, ItemComparison>()
        
        val originalItemsMap = originalItems.associate { 
            "${it.collection}_${it.collectionId}_${it.customName ?: ""}" to it 
        }
        val newItemsMap = newItems.associate { 
            "${it.collection}_${it.collectionId}_${it.customName ?: ""}" to it 
        }
        
        
        originalItemsMap.forEach { (key, originalItem) ->
            val newItem = newItemsMap[key]
            when {
                newItem == null -> {
                    comparison[key] = ItemComparison.REMOVED
                }
                originalItem.quantity == newItem.quantity -> {
                    comparison[key] = ItemComparison.SAME_QUANTITY
                }
                else -> {
                    comparison[key] = ItemComparison.DIFFERENT_QUANTITY(newItem.quantity)
                }
            }
        }
        
        newItemsMap.forEach { (key, _) ->
            if (!originalItemsMap.containsKey(key)) {
                comparison[key] = ItemComparison.NEW
            }
        }
        
        return comparison
    }

    /**
     * Get item name for comparison
     */
    private fun getItemName(item: com.estaciondulce.app.models.parcelables.MovementItem): String {
        return when (item.collection) {
            "recipes" -> {
                FirestoreRepository.recipesLiveData.value?.find { it.id == item.collectionId }?.name 
                    ?: "Recipe ${item.collectionId}"
            }
            "products" -> {
                FirestoreRepository.productsLiveData.value?.find { it.id == item.collectionId }?.name 
                    ?: "Product ${item.collectionId}"
            }
            "custom" -> {
                item.customName ?: "Custom Item"
            }
            else -> "Unknown Item"
        }
    }

    /**
     * Update movement kitchen order status based on kitchen orders
     */
    fun updateMovementKitchenOrderStatus(
        movementId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val movements = FirestoreRepository.movementsLiveData.value ?: emptyList()
        val movement = movements.find { it.id == movementId }
        
        if (movement == null || movement.type != EMovementType.SALE) {
            onSuccess()
            return
        }

        kitchenOrdersHelper.getKitchenOrdersForMovement(
            movementId = movementId,
            onSuccess = { kitchenOrders ->
                val newStatus = calculateMovementKitchenOrderStatus(kitchenOrders)
                
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

        val allPending = kitchenOrders.all { it.status == EKitchenOrderItemStatus.PENDING }
        val allReady = kitchenOrders.all { it.status == EKitchenOrderItemStatus.READY }
        val allDone = kitchenOrders.all { it.status == EKitchenOrderItemStatus.DONE }
        val hasNonPending = kitchenOrders.any { it.status != EKitchenOrderItemStatus.PENDING }

        return when {
            allDone -> EKitchenOrderStatus.DONE
            
            allReady -> EKitchenOrderStatus.READY
            
            allPending -> EKitchenOrderStatus.PENDING
            
            hasNonPending -> EKitchenOrderStatus.PREPARING
            
            else -> EKitchenOrderStatus.PENDING
        }
    }
}
