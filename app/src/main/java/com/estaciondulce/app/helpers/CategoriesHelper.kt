package com.estaciondulce.app.helpers

class CategoriesHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun fetchCategories(onSuccess: (Map<String, String>) -> Unit, onError: (Exception) -> Unit) {
        genericHelper.fetchCollection(
            collectionName = "categories",
            mapToEntity = { id, data ->
                id to (data["name"] as? String ?: "Sin nombre")
            },
            onSuccess = { categories ->
                onSuccess(categories.toMap()) // Convert List<Pair> to Map
            },
            onError = onError
        )
    }
}
