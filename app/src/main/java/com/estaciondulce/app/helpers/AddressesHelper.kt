package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.Address
import com.google.firebase.firestore.FirebaseFirestore

class AddressesHelper(private val genericHelper: GenericHelper = GenericHelper()) {

    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Adds an address to a person's addresses subcollection.
     */
    fun addAddressToPerson(personId: String, address: Address, onSuccess: (Address) -> Unit, onError: (Exception) -> Unit) {
        android.util.Log.d("AddressesHelper", "addAddressToPerson called for personId: $personId")
        android.util.Log.d("AddressesHelper", "Address data: label=${address.label}, formattedAddress=${address.formattedAddress}, lat=${address.latitude}, lng=${address.longitude}")
        
        val addressData = mapOf(
            "label" to address.label,
            "rawAddress" to address.rawAddress,
            "formattedAddress" to address.formattedAddress,
            "placeId" to address.placeId,
            "latitude" to address.latitude,
            "longitude" to address.longitude,
            "street" to address.street,
            "city" to address.city,
            "state" to address.state,
            "postalCode" to address.postalCode,
            "country" to address.country,
            "detail" to address.detail
        )

        android.util.Log.d("AddressesHelper", "Saving to Firestore: persons/$personId/addresses")
        firestore.collection("persons")
            .document(personId)
            .collection("addresses")
            .add(addressData)
            .addOnSuccessListener { documentReference ->
                android.util.Log.d("AddressesHelper", "Address saved successfully with ID: ${documentReference.id}")
                onSuccess(address.copy(id = documentReference.id))
            }
            .addOnFailureListener { exception ->
                android.util.Log.e("AddressesHelper", "Error saving address: ${exception.message}", exception)
                onError(exception)
            }
    }

    /**
     * Updates an address in a person's addresses subcollection.
     */
    fun updateAddressInPerson(personId: String, address: Address, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val addressData = mapOf(
            "label" to address.label,
            "rawAddress" to address.rawAddress,
            "formattedAddress" to address.formattedAddress,
            "placeId" to address.placeId,
            "latitude" to address.latitude,
            "longitude" to address.longitude,
            "street" to address.street,
            "city" to address.city,
            "state" to address.state,
            "postalCode" to address.postalCode,
            "country" to address.country,
            "detail" to address.detail
        )

        firestore.collection("persons")
            .document(personId)
            .collection("addresses")
            .document(address.id)
            .set(addressData)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    /**
     * Deletes an address from a person's addresses subcollection.
     */
    fun deleteAddressFromPerson(personId: String, addressId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("persons")
            .document(personId)
            .collection("addresses")
            .document(addressId)
            .delete()
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    /**
     * Gets all addresses for a specific person.
     */
    fun getAddressesForPerson(personId: String, onSuccess: (List<Address>) -> Unit, onError: (Exception) -> Unit) {
        firestore.collection("persons")
            .document(personId)
            .collection("addresses")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val addresses = querySnapshot.documents.mapNotNull { document ->
                    try {
                        Address(
                            id = document.id,
                            label = document.getString("label") ?: "",
                            rawAddress = document.getString("rawAddress") ?: "",
                            formattedAddress = document.getString("formattedAddress") ?: "",
                            placeId = document.getString("placeId") ?: "",
                            latitude = document.getDouble("latitude"),
                            longitude = document.getDouble("longitude"),
                            street = document.getString("street"),
                            city = document.getString("city"),
                            state = document.getString("state"),
                            postalCode = document.getString("postalCode"),
                            country = document.getString("country"),
                            detail = document.getString("detail") ?: ""
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onSuccess(addresses)
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

}
