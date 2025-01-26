package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Measure

class MeasuresHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun fetchMeasures(onSuccess: (List<Measure>) -> Unit, onError: (Exception) -> Unit) {
        genericHelper.fetchCollectionWithToObject(
            collectionName = "measures",
            clazz = Measure::class.java,
            onSuccess = { recipes ->
                // Assign IDs manually if required, since Firestore's toObject() ignores them
                val measuresWithId = recipes.map { it.copy(id = it.id) }
                onSuccess(measuresWithId)
            },
            onError = onError
        )
    }
}
