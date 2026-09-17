package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Category
import com.estaciondulce.app.models.mappers.toMap
import com.estaciondulce.app.models.parcelables.Event
import com.estaciondulce.app.models.mappers.toDTO

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class EventsHelper {
    private val db = FirebaseFirestore.getInstance()

    suspend fun saveEvent(
        event: Event,
        showInCategories: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            var categoryId = event.categoryId
            if (categoryId.isEmpty()) {
                // Check if a category with this name already exists
                val snapshot = db.collection("categories").whereEqualTo("name", event.name).get().await()
                if (snapshot.documents.isNotEmpty()) {
                    val doc = snapshot.documents.first()
                    categoryId = doc.id
                    // Update it to be an event tag
                    db.collection("categories").document(categoryId).update(
                        mapOf("isEvent" to true, "show" to showInCategories)
                    ).await()
                } else {
                    // Create new
                    val newCatRef = db.collection("categories").document()
                    categoryId = newCatRef.id
                    val cat = Category(id = categoryId, name = event.name, isEvent = true, show = showInCategories)
                    newCatRef.set(cat).await()
                }
            } else {
                // Update existing category's show flag and name (if event name changed)
                db.collection("categories").document(categoryId).update(
                    mapOf("name" to event.name, "show" to showInCategories)
                ).await()
            }

            // Save Event
            val eventRef = if (event.id.isEmpty()) db.collection("events").document() else db.collection("events").document(event.id)
            val eventToSave = event.copy(id = eventRef.id, categoryId = categoryId)
            val dto = eventToSave.toDTO()
            eventRef.set(dto.toMap()).await()
            
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun deleteEvent(
        eventId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Assume validations are done before calling this
        db.collection("events").document(eventId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
}
