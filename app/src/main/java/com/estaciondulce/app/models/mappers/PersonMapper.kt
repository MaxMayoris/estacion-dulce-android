package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.Person
import com.estaciondulce.app.models.parcelables.Phone
import com.estaciondulce.app.models.parcelables.Address
import com.estaciondulce.app.models.dtos.PersonDTO
import com.estaciondulce.app.models.dtos.PhoneDTO
import com.estaciondulce.app.models.dtos.AddressDTO

/**
 * Extension functions to convert between Person (Parcelable) and PersonDTO (Firestore)
 */

fun Person.toDTO(): PersonDTO {
    return PersonDTO(
        name = name,
        lastName = lastName,
        type = type,
        phones = phones.map { it.toDTO() },
        addresses = addresses
    )
}

fun PersonDTO.toParcelable(id: String = ""): Person {
    return Person(
        id = id,
        name = name,
        lastName = lastName,
        type = type,
        phones = phones.map { it.toParcelable() },
        addresses = addresses
    )
}

/**
 * Extension functions to convert between Phone (Parcelable) and PhoneDTO (Firestore)
 */

fun Phone.toDTO(): PhoneDTO {
    return PhoneDTO(
        phoneNumberPrefix = phoneNumberPrefix,
        phoneNumberSuffix = phoneNumberSuffix
    )
}

fun PhoneDTO.toParcelable(): Phone {
    return Phone(
        phoneNumberPrefix = phoneNumberPrefix,
        phoneNumberSuffix = phoneNumberSuffix
    )
}

/**
 * Extension functions to convert between Address (Parcelable) and AddressDTO (Firestore)
 */

fun Address.toDTO(): AddressDTO {
    return AddressDTO(
        label = label,
        formattedAddress = formattedAddress,
        latitude = latitude,
        longitude = longitude
    )
}

fun AddressDTO.toParcelable(): Address {
    return Address(
        label = label,
        formattedAddress = formattedAddress,
        latitude = latitude,
        longitude = longitude
    )
}

/**
 * Convert PersonDTO to Map for Firestore
 */
fun PersonDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "lastName" to lastName,
        "type" to type,
        "phones" to phones,
        "addresses" to addresses
    )
}
