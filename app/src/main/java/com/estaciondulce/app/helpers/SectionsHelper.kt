package com.estaciondulce.app.helpers

class SectionsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun fetchSections(onSuccess: (Map<String, String>) -> Unit, onError: (Exception) -> Unit) {
        genericHelper.fetchCollection(
            collectionName = "sections",
            mapToEntity = { id, data ->
                id to (data["name"] as? String ?: "Sin nombre")
            },
            onSuccess = { sections ->
                onSuccess(sections.toMap()) // Convert List<Pair> to Map
            },
            onError = onError
        )
    }
}
