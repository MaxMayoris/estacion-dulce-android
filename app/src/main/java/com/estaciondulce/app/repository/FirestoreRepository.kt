package com.estaciondulce.app.repository

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import com.estaciondulce.app.models.Category
import com.estaciondulce.app.models.Measure
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.parcelables.Person
import com.estaciondulce.app.models.parcelables.Product
import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.models.parcelables.WorkCategory
import com.estaciondulce.app.models.parcelables.Worker
import com.estaciondulce.app.models.Section
import com.estaciondulce.app.models.parcelables.ShipmentSettings
import com.estaciondulce.app.models.dtos.MovementDTO
import com.estaciondulce.app.models.dtos.PersonDTO
import com.estaciondulce.app.models.dtos.ProductDTO
import com.estaciondulce.app.models.dtos.RecipeDTO
import com.estaciondulce.app.models.dtos.ShipmentSettingsDTO
import com.estaciondulce.app.models.dtos.WorkCategoryDTO
import com.estaciondulce.app.models.dtos.WorkerDTO
import com.estaciondulce.app.models.parcelables.Event
import com.estaciondulce.app.models.dtos.EventDTO
import com.estaciondulce.app.models.mappers.*
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SnapshotListenOptions

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
    val workCategoriesLiveData = MutableLiveData<List<WorkCategory>>()
    val workersLiveData = MutableLiveData<List<Worker>>()
    val eventsLiveData = MutableLiveData<List<Event>>()

    private var productsListener: ListenerRegistration? = null
    private var recipesListener: ListenerRegistration? = null
    private var measuresListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var sectionsListener: ListenerRegistration? = null
    private var personsListener: ListenerRegistration? = null
    private var movementsListener: ListenerRegistration? = null
    private var shipmentSettingsListener: ListenerRegistration? = null
    private var workCategoriesListener: ListenerRegistration? = null
    private var workersListener: ListenerRegistration? = null
    private var eventsListener: ListenerRegistration? = null

    private var movementsCacheListener: ListenerRegistration? = null
    private var latestCachedMovementDocs: List<MovementDocState> = emptyList()
    private var movementsServerSynced = false

    /**
     * Starts real-time snapshot listeners for all collections.
     * Fetches initial data and maintains live updates.
     * Idempotent: does nothing if listeners are already running, so repeated calls
     * (HomeActivity recreation, rotation) don't leak duplicate listeners.
     */
    @Synchronized
    fun startListeners() {
        if (productsListener != null) return

        productsListener = firestore.collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val products = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ProductDTO::class.java)?.toParcelable(doc.id)
                } ?: emptyList()
                productsLiveData.postValue(products)
            }

        recipesListener = firestore.collection("recipes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val recipes = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(RecipeDTO::class.java)?.toParcelable(doc.id)
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
                    doc.toObject(PersonDTO::class.java)?.toParcelable(doc.id)
                } ?: emptyList()
                personsLiveData.postValue(persons)
            }

        startMovementsSync()

        shipmentSettingsListener = firestore.collection("settings").document("shipment")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val settings = snapshot?.toObject(ShipmentSettingsDTO::class.java)?.toParcelable()
                shipmentSettingsLiveData.postValue(settings)
            }

        workCategoriesListener = firestore.collection("workCategories")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    workCategoriesLiveData.postValue(emptyList())
                    return@addSnapshotListener
                }
                val categories = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(WorkCategoryDTO::class.java)?.toParcelable(doc.id)
                } ?: emptyList()
                workCategoriesLiveData.postValue(categories.sortedBy { it.order })
            }

        workersListener = firestore.collection("workers")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    workersLiveData.postValue(emptyList())
                    return@addSnapshotListener
                }
                val workers = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(WorkerDTO::class.java)?.toParcelable(doc.id)
                } ?: emptyList()
                workersLiveData.postValue(workers)
            }

        eventsListener = firestore.collection("events")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    eventsLiveData.postValue(emptyList())
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(EventDTO::class.java)?.toParcelable(doc.id)
                } ?: emptyList()
                eventsLiveData.postValue(events)
            }
    }

    /**
     * Incremental sync for movements, whose full history is too large to re-read on every start.
     * - A cache-only listener on the whole collection feeds [movementsLiveData]. It costs no reads
     *   and also reflects local writes immediately (even offline, with pending server timestamps).
     * - A server listener only fetches documents whose `updatedAt` is newer than the newest cached
     *   one; the SDK stores them in the cache, which re-triggers the cache listener.
     * - An empty cache (fresh install) is not published until the server listener has synced
     *   (metadata changes included, so a sync with no new documents is noticed too); documents
     *   it receives reach the UI through the cache listener.
     * Deletions arrive as `deleted: true` (soft delete).
     */
    private fun startMovementsSync() {
        val movementsRef = firestore.collection("movements")
        val cacheOnly = SnapshotListenOptions.Builder().setSource(ListenSource.CACHE).build()

        movementsServerSynced = false
        movementsCacheListener = movementsRef.addSnapshotListener(cacheOnly) { snapshot, error ->
            if (error != null || snapshot == null) {
                return@addSnapshotListener
            }
            val docs = snapshot.documents.map { it.toMovementDocState() }
            latestCachedMovementDocs = docs
            if (docs.isNotEmpty() || movementsServerSynced) {
                movementsLiveData.postValue(MovementsSync.visibleMovements(docs))
            }
            startMovementsServerListener(movementsRef, docs)
        }
    }

    @Synchronized
    private fun startMovementsServerListener(movementsRef: CollectionReference, cachedDocs: List<MovementDocState>) {
        if (movementsListener != null || movementsCacheListener == null) return
        movementsListener = movementsRef
            .whereGreaterThanOrEqualTo(MovementsSync.UPDATED_AT, MovementsSync.syncStart(cachedDocs))
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
                if (snapshot != null && !snapshot.metadata.isFromCache && !movementsServerSynced) {
                    movementsServerSynced = true
                    if (snapshot.isEmpty) movementsLiveData.postValue(MovementsSync.visibleMovements(latestCachedMovementDocs))
                }
            }
    }

    private fun DocumentSnapshot.toMovementDocState(): MovementDocState {
        val movement = try {
            toObject(MovementDTO::class.java)
                ?.copy(isStock = getBoolean("isStock"))
                ?.toParcelable(id)
        } catch (e: Exception) {
            null
        }
        return MovementDocState(
            id = id,
            movement = movement,
            deleted = getBoolean(MovementsSync.DELETED) == true,
            updatedAt = getTimestamp(MovementsSync.UPDATED_AT)
        )
    }

    @Synchronized
    fun stopListeners() {
        productsListener?.remove()
        recipesListener?.remove()
        measuresListener?.remove()
        categoriesListener?.remove()
        sectionsListener?.remove()
        personsListener?.remove()
        movementsListener?.remove()
        movementsCacheListener?.remove()
        shipmentSettingsListener?.remove()
        workCategoriesListener?.remove()
        workersListener?.remove()
        eventsListener?.remove()

        productsListener = null
        recipesListener = null
        measuresListener = null
        categoriesListener = null
        sectionsListener = null
        personsListener = null
        movementsListener = null
        movementsCacheListener = null
        shipmentSettingsListener = null
        workCategoriesListener = null
        workersListener = null
        eventsListener = null
    }
}
