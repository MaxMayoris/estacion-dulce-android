package com.estaciondulce.app.models.parcelables

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * WorkDay data model representing a day with work blocks
 */
@Parcelize
data class WorkDay(
    override var id: String = "",
    val date: String = "",
    val totalMinutes: Long = 0,
    val blockCount: Long = 0,
    val totalsByCategory: Map<String, Long> = mapOf(),
    val totalsByWorker: Map<String, Long> = mapOf()
) : Parcelable, com.estaciondulce.app.models.Identifiable

/**
 * WorkBlock data model representing a time block of work
 */
@Parcelize
data class WorkBlock(
    override var id: String = "",
    val startAt: com.google.firebase.Timestamp? = null,
    val endAt: com.google.firebase.Timestamp? = null,
    val durationMinutes: Long = 0,
    val categoryId: String = "",
    val workerId: String = "",
    val note: String = "",
    val createdBy: String = "",
    val updatedBy: String = "",
    val createdAt: com.google.firebase.Timestamp? = null,
    val updatedAt: com.google.firebase.Timestamp? = null
) : Parcelable, com.estaciondulce.app.models.Identifiable

/**
 * WorkCategory data model for categorizing work blocks
 */
@Parcelize
data class WorkCategory(
    override var id: String = "",
    val name: String = "",
    val color: String = "",
    val icon: String = "",
    val isActive: Boolean = true,
    val order: Int = 0
) : Parcelable, com.estaciondulce.app.models.Identifiable

/**
 * Worker data model representing a person who can work
 */
@Parcelize
data class Worker(
    override var id: String = "",
    val displayName: String = "",
    val isActive: Boolean = true,
    val role: String = ""
) : Parcelable, com.estaciondulce.app.models.Identifiable

