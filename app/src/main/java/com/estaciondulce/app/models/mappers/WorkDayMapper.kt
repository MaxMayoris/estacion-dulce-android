package com.estaciondulce.app.models.mappers

import com.estaciondulce.app.models.parcelables.WorkDay
import com.estaciondulce.app.models.parcelables.WorkBlock
import com.estaciondulce.app.models.parcelables.WorkCategory
import com.estaciondulce.app.models.parcelables.Worker
import com.estaciondulce.app.models.dtos.WorkDayDTO
import com.estaciondulce.app.models.dtos.WorkBlockDTO
import com.estaciondulce.app.models.dtos.WorkCategoryDTO
import com.estaciondulce.app.models.dtos.WorkerDTO

/**
 * Extension functions to convert between WorkDay (Parcelable) and WorkDayDTO (Firestore)
 */
fun WorkDay.toDTO(): WorkDayDTO {
    return WorkDayDTO(
        date = date,
        totalMinutes = totalMinutes,
        blockCount = blockCount,
        totalsByCategory = totalsByCategory,
        totalsByWorker = totalsByWorker
    )
}

fun WorkDayDTO.toParcelable(id: String = ""): WorkDay {
    return WorkDay(
        id = id,
        date = date,
        totalMinutes = totalMinutes,
        blockCount = blockCount,
        totalsByCategory = totalsByCategory,
        totalsByWorker = totalsByWorker
    )
}

/**
 * Extension functions to convert between WorkBlock (Parcelable) and WorkBlockDTO (Firestore)
 */
fun WorkBlock.toDTO(): WorkBlockDTO {
    return WorkBlockDTO(
        startAt = startAt,
        endAt = endAt,
        durationMinutes = durationMinutes,
        categoryId = categoryId,
        workerId = workerId,
        note = note,
        createdBy = createdBy,
        updatedBy = updatedBy,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun WorkBlockDTO.toParcelable(id: String = ""): WorkBlock {
    return WorkBlock(
        id = id,
        startAt = startAt,
        endAt = endAt,
        durationMinutes = durationMinutes,
        categoryId = categoryId,
        workerId = workerId,
        note = note,
        createdBy = createdBy,
        updatedBy = updatedBy,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

/**
 * Extension functions to convert between WorkCategory (Parcelable) and WorkCategoryDTO (Firestore)
 */
fun WorkCategory.toDTO(): WorkCategoryDTO {
    return WorkCategoryDTO(
        name = name,
        color = color,
        icon = icon,
        isActive = isActive,
        order = order
    )
}

fun WorkCategoryDTO.toParcelable(id: String = ""): WorkCategory {
    return WorkCategory(
        id = id,
        name = name,
        color = color,
        icon = icon,
        isActive = isActive,
        order = order
    )
}

/**
 * Extension functions to convert between Worker (Parcelable) and WorkerDTO (Firestore)
 */
fun Worker.toDTO(): WorkerDTO {
    return WorkerDTO(
        displayName = displayName,
        isActive = isActive,
        role = role
    )
}

fun WorkerDTO.toParcelable(id: String = ""): Worker {
    return Worker(
        id = id,
        displayName = displayName,
        isActive = isActive,
        role = role
    )
}

/**
 * Convert WorkDayDTO to Map for Firestore
 */
fun WorkDayDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "date" to date,
        "totalMinutes" to totalMinutes,
        "blockCount" to blockCount,
        "totalsByCategory" to totalsByCategory,
        "totalsByWorker" to totalsByWorker
    )
}

/**
 * Convert WorkBlockDTO to Map for Firestore
 */
fun WorkBlockDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "startAt" to startAt,
        "endAt" to endAt,
        "durationMinutes" to durationMinutes,
        "categoryId" to categoryId,
        "workerId" to workerId,
        "note" to note,
        "createdBy" to createdBy,
        "updatedBy" to updatedBy,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}

/**
 * Convert WorkCategoryDTO to Map for Firestore
 */
fun WorkCategoryDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "name" to name,
        "color" to color,
        "icon" to icon,
        "isActive" to isActive,
        "order" to order
    )
}

/**
 * Convert WorkerDTO to Map for Firestore
 */
fun WorkerDTO.toMap(): Map<String, Any?> {
    return mapOf(
        "displayName" to displayName,
        "isActive" to isActive,
        "role" to role
    )
}

