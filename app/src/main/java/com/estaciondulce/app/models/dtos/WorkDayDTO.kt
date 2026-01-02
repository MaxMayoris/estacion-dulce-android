package com.estaciondulce.app.models.dtos

/**
 * DTO for WorkDay data in Firestore
 */
data class WorkDayDTO(
    val date: String = "",
    val totalMinutes: Long = 0,
    val blockCount: Long = 0,
    val totalsByCategory: Map<String, Long> = mapOf(),
    val totalsByWorker: Map<String, Long> = mapOf()
)

/**
 * DTO for WorkBlock data in Firestore
 */
data class WorkBlockDTO(
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
)

/**
 * DTO for WorkCategory data in Firestore
 */
data class WorkCategoryDTO(
    val name: String = "",
    val color: String = "",
    val icon: String = "",
    val isActive: Boolean = true,
    val order: Int = 0
)

/**
 * DTO for Worker data in Firestore
 */
data class WorkerDTO(
    val displayName: String = "",
    val isActive: Boolean = true,
    val role: String = ""
)

