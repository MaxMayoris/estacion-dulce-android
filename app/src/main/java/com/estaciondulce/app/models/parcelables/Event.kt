package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

@Parcelize
data class Event(
    override var id: String = "",
    val name: String = "",
    val description: String = "",
    val startDate: Date? = null,
    val endDate: Date? = null,
    val categoryId: String = ""
) : Parcelable, com.estaciondulce.app.models.Identifiable
