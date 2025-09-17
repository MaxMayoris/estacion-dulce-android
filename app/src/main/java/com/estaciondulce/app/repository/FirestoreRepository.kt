package com.estaciondulce.app.repository

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import com.estaciondulce.app.models.Address
import com.estaciondulce.app.models.Category
import com.estaciondulce.app.models.Measure
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.models.Person
import com.estaciondulce.app.models.Product
import com.estaciondulce.app.models.Recipe
import com.estaciondulce.app.models.Section
import com.estaciondulce.app.models.ShipmentSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Central repository managing real-time Firestore listeners for all collections.
 */
object FirestoreRepository {
    @SuppressLint("StaticFieldLeak")
    private val firestore = FirebaseFirestore.getInstance()

    val productsLiveData = MutableLiveData<List<Product>>()
    val recipesLiveData = MutableLiveData<List<Recipe>>()
    val measuresLiveData = MutableLiveData<List<Measure>>()
    val categoriesLiveData = MutableLiveData<List<Category>>()
    val sectionsLiveData = MutableLiveData<List<Section>>()
    val personsLiveData = MutableLiveData<List<Person>>()
    val movementsLiveData = MutableLiveData<List<Movement>>()
    val shipmentSettingsLiveData = MutableLiveData<ShipmentSettings?>()

    private var productsListener: ListenerRegistration? = null
    private var recipesListener: ListenerRegistration? = null
    private var measuresListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var sectionsListener: ListenerRegistration? = null
    private var personsListener: ListenerRegistration? = null
    private var movementsListener: ListenerRegistration? = null
    private var shipmentSettingsListener: ListenerRegistration? = null

    /**
     * Starts real-time snapshot listeners for all collections.
     * Fetches initial data and maintains live updates.
     */
    fun startListeners() {
        productsListener = firestore.collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val products = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Product::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                productsLiveData.postValue(products)
            }

        recipesListener = firestore.collection("recipes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val recipes = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Recipe::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                recipesLiveData.postValue(recipes)
            }

        measuresListener = firestore.collection("measures")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val measures = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Measure::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                measuresLiveData.postValue(measures)
            }

        categoriesListener = firestore.collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Category::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                categoriesLiveData.postValue(categories)
            }

        sectionsListener = firestore.collection("sections")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val sections = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Section::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                sectionsLiveData.postValue(sections)
            }

        personsListener = firestore.collection("persons")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val persons = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Person::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                personsLiveData.postValue(persons)
            }

        movementsListener = firestore.collection("movements")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val movements = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Movement::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                movementsLiveData.postValue(movements)
            }

        shipmentSettingsListener = firestore.collection("settings").document("shipment")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val settings = snapshot?.toObject(ShipmentSettings::class.java)
                shipmentSettingsLiveData.postValue(settings)
            }

    }

    fun stopListeners() {
        productsListener?.remove()
        recipesListener?.remove()
        measuresListener?.remove()
        categoriesListener?.remove()
        sectionsListener?.remove()
        personsListener?.remove()
        movementsListener?.remove()
        shipmentSettingsListener?.remove()
    }
}
