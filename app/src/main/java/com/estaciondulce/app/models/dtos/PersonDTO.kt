package com.estaciondulce.app.models.dtos

/**
 * DTO for Person data in Firestore (without Parcelable to avoid stability field)
 */
data class PersonDTO(
    val name: String = "",
    val lastName: String = "",
    val type: String = "",
    val phones: List<PhoneDTO> = listOf(),
    val addresses: List<String> = listOf()
)

/**
 * DTO for Phone data in Firestore (without Parcelable to avoid stability field)
 */
data class PhoneDTO(
    val phoneNumberPrefix: String = "",
    val phoneNumberSuffix: String = ""
)

/**
 * DTO for Address data in Firestore (without Parcelable to avoid stability field)
 */
data class AddressDTO(
    val label: String = "",
    val formattedAddress: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null
)
