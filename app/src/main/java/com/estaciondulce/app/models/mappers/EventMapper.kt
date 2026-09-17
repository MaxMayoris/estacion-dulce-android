package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.Event
import com.estaciondulce.app.models.dtos.EventDTO

fun Event.toDTO(): EventDTO {
    return EventDTO(
        name = name,
        description = description,
        startDate = startDate,
        endDate = endDate,
        categoryId = categoryId
    )
}

fun EventDTO.toParcelable(id: String = ""): Event {
    return Event(
        id = id,
        name = name,
        description = description,
        startDate = startDate,
        endDate = endDate,
        categoryId = categoryId
    )
}

fun EventDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "description" to description,
        "startDate" to startDate,
        "endDate" to endDate,
        "categoryId" to categoryId
    )
}
