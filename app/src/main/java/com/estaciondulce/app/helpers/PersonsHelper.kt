package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.Person
import com.estaciondulce.app.models.dtos.PersonDTO
import com.estaciondulce.app.models.mappers.toDTO
import com.estaciondulce.app.models.mappers.toMap

class PersonsHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun addPerson(person: Person, onSuccess: (Person) -> Unit, onError: (Exception) -> Unit) {
        val personDTO = person.toDTO()
        val personData = personDTO.toMap()
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
        val personDTO = person.toDTO()
        val personData = personDTO.toMap()
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
