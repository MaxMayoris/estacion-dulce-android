package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Person

class PersonsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun addPerson(person: Person, onSuccess: (Person) -> Unit, onError: (Exception) -> Unit) {
        val personData = mapOf(
            "name" to person.name,
            "lastName" to person.lastName,
            "phones" to person.phones,
            "type" to person.type
        )
        genericHelper.addDocument(
            collectionName = "persons",
            data = personData,
            onSuccess = { documentId ->
                onSuccess(person.copy(id = documentId))
            },
            onError = onError
        )
    }

    fun updatePerson(personId: String, person: Person, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val personData = mapOf(
            "name" to person.name,
            "lastName" to person.lastName,
            "phones" to person.phones,
            "type" to person.type
        )
        genericHelper.updateDocument(
            collectionName = "persons",
            documentId = personId,
            data = personData,
            onSuccess = onSuccess,
            onError = onError
        )
    }

    fun deletePerson(personId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        genericHelper.deleteDocument(
            collectionName = "persons",
            documentId = personId,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}
