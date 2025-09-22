package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.KitchenOrder
import com.estaciondulce.app.models.enums.EKitchenOrderStatus
import com.estaciondulce.app.models.enums.EKitchenOrderItemStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.*

/**
 * Helper class for Firestore operations on kitchen orders
 */
class KitchenOrdersHelper {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Add kitchen orders for a movement
     */
    fun addKitchenOrdersForMovement(
        movementId: String,
        kitchenOrders: List<KitchenOrder>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val batch = db.batch()
            
            kitchenOrders.forEach { kitchenOrder ->
                
                val kitchenOrderRef = db.collection("movements")
                    .document(movementId)
                    .collection("kitchenOrders")
                    .document()
                
                val kitchenOrderData = mapOf(
                    "collection" to kitchenOrder.collection,
                    "collectionId" to kitchenOrder.collectionId,
                    "customName" to kitchenOrder.customName,
                    "name" to kitchenOrder.name,
                    "quantity" to kitchenOrder.quantity,
                    "status" to kitchenOrder.status.name,
                    "createdAt" to kitchenOrder.createdAt,
                    "updatedAt" to kitchenOrder.updatedAt
                )
                
                batch.set(kitchenOrderRef, kitchenOrderData)
            }
            
            batch.commit()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Get kitchen orders for a movement
     */
    fun getKitchenOrdersForMovement(
        movementId: String,
        onSuccess: (List<KitchenOrder>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("movements")
            .document(movementId)
            .collection("kitchenOrders")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val kitchenOrders = snapshot.documents.mapNotNull { document ->
                    try {
                        KitchenOrder(
                            id = document.id,
                            collection = document.getString("collection") ?: "",
                            collectionId = document.getString("collectionId") ?: "",
                            customName = document.getString("customName"),
                            name = document.getString("name") ?: "",
                            quantity = document.getDouble("quantity") ?: 0.0,
                            status = EKitchenOrderItemStatus.valueOf(document.getString("status") ?: "PENDING"),
                            createdAt = (document.getDate("createdAt") ?: Date()),
                            updatedAt = (document.getDate("updatedAt") ?: Date())
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onSuccess(kitchenOrders)
            }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Update kitchen order status
     */
    fun updateKitchenOrderStatus(
        movementId: String,
        kitchenOrderId: String,
        newStatus: EKitchenOrderItemStatus,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val kitchenOrderRef = db.collection("movements")
            .document(movementId)
            .collection("kitchenOrders")
            .document(kitchenOrderId)
        
        val updateData = mapOf(
            "status" to newStatus.name,
            "updatedAt" to Date()
        )
        
        kitchenOrderRef.update(updateData)
            .addOnSuccessListener { 
                val movementsHelper = MovementsHelper()
                movementsHelper.updateMovementKitchenOrderStatus(
                    movementId = movementId,
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Delete kitchen orders for a movement
     */
    fun deleteKitchenOrdersForMovement(
        movementId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("movements")
            .document(movementId)
            .collection("kitchenOrders")
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Get all kitchen orders across all movements
     */
    fun getAllKitchenOrders(
        onSuccess: (List<KitchenOrder>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collectionGroup("kitchenOrders")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val kitchenOrders = snapshot.documents.mapNotNull { document ->
                    try {
                        KitchenOrder(
                            id = document.id,
                            collection = document.getString("collection") ?: "",
                            collectionId = document.getString("collectionId") ?: "",
                            customName = document.getString("customName"),
                            name = document.getString("name") ?: "",
                            quantity = document.getDouble("quantity") ?: 0.0,
                            status = EKitchenOrderItemStatus.valueOf(document.getString("status") ?: "PENDING"),
                            createdAt = (document.getDate("createdAt") ?: Date()),
                            updatedAt = (document.getDate("updatedAt") ?: Date())
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onSuccess(kitchenOrders)
            }
            .addOnFailureListener { onError(it) }
    }
}
