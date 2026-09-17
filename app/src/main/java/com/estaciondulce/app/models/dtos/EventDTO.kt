package com.estaciondulce.app.models.dtos

import java.util.Date

data class EventDTO(
    val name: String = "",
    val description: String = "",
    val startDate: Date? = null,
    val endDate: Date? = null,
    val categoryId: String = ""
)
