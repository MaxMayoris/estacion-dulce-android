package com.estaciondulce.app.helpers

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

/**
 * Generic Firestore operations for CRUD operations across collections.
 */
class GenericHelper(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    companion object {
        /** Collections synced incrementally by `updatedAt` (see FirestoreRepository). */
        private val INCREMENTAL_SYNC_COLLECTIONS = setOf("movements")
    }

    /** Stamps `updatedAt` on writes to incrementally synced collections so other devices pick them up. */
    private fun withUpdatedAt(collectionName: String, data: Map<String, Any?>): Map<String, Any?> =
        if (collectionName in INCREMENTAL_SYNC_COLLECTIONS) {
            data + ("updatedAt" to FieldValue.serverTimestamp())
        } else {
            data
        }

    fun addDocument(
        collectionName: String,
        data: Map<String, Any?>,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .add(withUpdatedAt(collectionName, data))
            .addOnSuccessListener { documentReference ->
                onSuccess(documentReference.id)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    fun updateDocument(
        collectionName: String,
        documentId: String,
        data: Map<String, Any?>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .document(documentId)
            .update(withUpdatedAt(collectionName, data))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    fun deleteDocument(
        collectionName: String,
        documentId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .document(documentId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    fun getDocument(
        collectionName: String,
        documentId: String,
        onSuccess: (DocumentSnapshot) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .document(documentId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    onSuccess(document)
                } else {
                    onError(Exception("Document not found"))
                }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}
