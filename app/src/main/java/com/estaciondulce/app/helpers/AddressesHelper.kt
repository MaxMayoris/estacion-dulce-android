package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Address

class AddressesHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    fun addAddress(address: Address, onSuccess: (Address) -> Unit, onError: (Exception) -> Unit) {
        val addressData = mapOf(
            "personId" to address.personId,
            "rawAddress" to address.rawAddress
        )
        genericHelper.addDocument(
            collectionName = "addresses",
            data = addressData,
            onSuccess = { documentId ->
                onSuccess(address.copy(id = documentId))
            },
            onError = onError
        )
    }

    fun updateAddress(addressId: String, address: Address, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val addressData = mapOf(
            "personId" to address.personId,
            "rawAddress" to address.rawAddress
        )
        genericHelper.updateDocument(
            collectionName = "addresses",
            documentId = addressId,
            data = addressData,
            onSuccess = onSuccess,
            onError = onError
        )
    }
}
