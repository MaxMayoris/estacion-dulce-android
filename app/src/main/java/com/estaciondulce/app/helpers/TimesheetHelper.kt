package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.WorkDay
import com.estaciondulce.app.models.parcelables.WorkBlock
import com.estaciondulce.app.models.dtos.WorkDayDTO
import com.estaciondulce.app.models.dtos.WorkBlockDTO
import com.estaciondulce.app.models.mappers.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

/**
 * Helper for timesheet operations
 */
class TimesheetHelper(private val genericHelper: GenericHelper = GenericHelper()) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Gets or creates a WorkDay document for a specific date
     */
    fun getOrCreateWorkDay(
        date: String,
        onSuccess: (WorkDay) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("workDays")
            .document(date)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val workDayDTO = document.toObject(WorkDayDTO::class.java)
                    val workDay = workDayDTO?.toParcelable(date) ?: WorkDay(id = date, date = date)
                    onSuccess(workDay)
                } else {
                    val now = Timestamp.now()
                    val workDayData = mapOf(
                        "date" to date,
                        "totalMinutes" to 0L,
                        "blockCount" to 0L,
                        "totalsByCategory" to mapOf<String, Long>(),
                        "totalsByWorker" to mapOf<String, Long>(),
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                    
                    firestore.collection("workDays")
                        .document(date)
                        .set(workDayData)
                        .addOnSuccessListener {
                            val newWorkDay = WorkDay(
                                id = date,
                                date = date,
                                totalMinutes = 0,
                                blockCount = 0,
                                totalsByCategory = mapOf(),
                                totalsByWorker = mapOf()
                            )
                            onSuccess(newWorkDay)
                        }
                        .addOnFailureListener { e ->
                            onError(e)
                        }
                }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    /**
     * Gets all blocks for a specific day
     */
    fun getBlocksForDay(
        date: String,
        onSuccess: (List<WorkBlock>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("workDays")
            .document(date)
            .collection("blocks")
            .orderBy("startAt")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val blocks = querySnapshot.documents.mapNotNull { doc ->
                    val blockDTO = doc.toObject(WorkBlockDTO::class.java)
                    blockDTO?.toParcelable(doc.id)
                }
                onSuccess(blocks)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    /**
     * Adds a work block to a day
     */
    fun addWorkBlock(
        date: String,
        block: WorkBlock,
        onSuccess: (WorkBlock) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        val userId = currentUser?.uid ?: currentUser?.email ?: "unknown"
        val now = Timestamp.now()

        val blockWithAudit = block.copy(
            createdBy = userId,
            updatedBy = userId,
            createdAt = now,
            updatedAt = now
        )

        val blockDTO = blockWithAudit.toDTO()
        val blockData = blockDTO.toMap()

        firestore.collection("workDays")
            .document(date)
            .collection("blocks")
            .add(blockData)
            .addOnSuccessListener { documentReference ->
                val newBlock = blockWithAudit.copy(id = documentReference.id)
                updateWorkDayCacheWithDelta(
                    date = date,
                    deltaMinutes = block.durationMinutes,
                    deltaBlockCount = 1,
                    categoryId = block.categoryId,
                    workerId = block.workerId,
                    isAdd = true
                ) {
                    onSuccess(newBlock)
                }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    /**
     * Updates a work block
     */
    fun updateWorkBlock(
        date: String,
        blockId: String,
        block: WorkBlock,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val currentUser = auth.currentUser
        val userId = currentUser?.uid ?: currentUser?.email ?: "unknown"
        val now = Timestamp.now()

        firestore.collection("workDays")
            .document(date)
            .collection("blocks")
            .document(blockId)
            .get()
            .addOnSuccessListener { oldBlockDoc ->
                val oldBlockDTO = oldBlockDoc.toObject(WorkBlockDTO::class.java)
                val oldBlock = oldBlockDTO?.toParcelable(blockId)
                
                val blockWithAudit = block.copy(
                    updatedBy = userId,
                    updatedAt = now
                )

                val blockDTO = blockWithAudit.toDTO()
                val blockData = blockDTO.toMap()

                firestore.collection("workDays")
                    .document(date)
                    .collection("blocks")
                    .document(blockId)
                    .update(blockData)
                    .addOnSuccessListener {
                        val oldDuration = oldBlock?.durationMinutes ?: 0L
                        val newDuration = block.durationMinutes
                        val deltaMinutes = newDuration - oldDuration
                        
                        val oldCategoryId = oldBlock?.categoryId ?: ""
                        val oldWorkerId = oldBlock?.workerId ?: ""
                        
                        updateWorkDayCacheWithDelta(
                            date = date,
                            deltaMinutes = deltaMinutes,
                            deltaBlockCount = 0,
                            categoryId = block.categoryId,
                            workerId = block.workerId,
                            oldCategoryId = oldCategoryId,
                            oldWorkerId = oldWorkerId,
                            oldDurationMinutes = oldDuration,
                            isAdd = false
                        ) {
                            onSuccess()
                        }
                    }
                    .addOnFailureListener { e ->
                        onError(e)
                    }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    /**
     * Deletes a work block
     */
    fun deleteWorkBlock(
        date: String,
        blockId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("workDays")
            .document(date)
            .collection("blocks")
            .document(blockId)
            .get()
            .addOnSuccessListener { blockDoc ->
                val blockDTO = blockDoc.toObject(WorkBlockDTO::class.java)
                val block = blockDTO?.toParcelable(blockId)
                
                firestore.collection("workDays")
                    .document(date)
                    .collection("blocks")
                    .document(blockId)
                    .delete()
                    .addOnSuccessListener {
                        val blockDuration = block?.durationMinutes ?: 0L
                        updateWorkDayCacheWithDelta(
                            date = date,
                            deltaMinutes = -blockDuration,
                            deltaBlockCount = -1,
                            categoryId = block?.categoryId ?: "",
                            workerId = block?.workerId ?: "",
                            oldDurationMinutes = blockDuration,
                            isAdd = false
                        ) {
                            onSuccess()
                        }
                    }
                    .addOnFailureListener { e ->
                        onError(e)
                    }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    /**
     * Updates the cache using deltas (more efficient than recalculating)
     */
    private fun updateWorkDayCacheWithDelta(
        date: String,
        deltaMinutes: Long,
        deltaBlockCount: Long,
        categoryId: String,
        workerId: String,
        oldCategoryId: String = "",
        oldWorkerId: String = "",
        oldDurationMinutes: Long = 0L,
        isAdd: Boolean,
        onComplete: () -> Unit
    ) {
        firestore.collection("workDays")
            .document(date)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    val newWorkDay = WorkDay(
                        id = date,
                        date = date,
                        totalMinutes = if (isAdd) deltaMinutes else 0L,
                        blockCount = if (isAdd) deltaBlockCount else 0L,
                        totalsByCategory = if (isAdd && categoryId.isNotEmpty()) {
                            mapOf(categoryId to deltaMinutes)
                        } else mapOf(),
                        totalsByWorker = if (isAdd && workerId.isNotEmpty()) {
                            mapOf(workerId to deltaMinutes)
                        } else mapOf()
                    )
                    val workDayDTO = newWorkDay.toDTO()
                    val workDayData = workDayDTO.toMap()
                    
                    firestore.collection("workDays")
                        .document(date)
                        .set(workDayData)
                        .addOnSuccessListener { onComplete() }
                        .addOnFailureListener { _ -> onComplete() }
                    return@addOnSuccessListener
                }

                val currentTotalMinutes = (doc.getLong("totalMinutes") ?: 0L)
                val currentBlockCount = (doc.getLong("blockCount") ?: 0L)
                @Suppress("UNCHECKED_CAST")
                val currentTotalsByCategory = (doc.get("totalsByCategory") as? Map<String, Long>) ?: mapOf()
                @Suppress("UNCHECKED_CAST")
                val currentTotalsByWorker = (doc.get("totalsByWorker") as? Map<String, Long>) ?: mapOf()

                val newTotalMinutes = currentTotalMinutes + deltaMinutes
                val newBlockCount = (currentBlockCount + deltaBlockCount).coerceAtLeast(0L)

                val newTotalsByCategory = currentTotalsByCategory.toMutableMap()
                if (oldCategoryId.isNotEmpty() && !isAdd) {
                    val oldCategoryTotal = newTotalsByCategory[oldCategoryId] ?: 0L
                    newTotalsByCategory[oldCategoryId] = (oldCategoryTotal - oldDurationMinutes).coerceAtLeast(0L)
                    if (newTotalsByCategory[oldCategoryId] == 0L) {
                        newTotalsByCategory.remove(oldCategoryId)
                    }
                }
                if (categoryId.isNotEmpty()) {
                    val categoryTotal = newTotalsByCategory[categoryId] ?: 0L
                    newTotalsByCategory[categoryId] = categoryTotal + deltaMinutes
                    if (newTotalsByCategory[categoryId] == 0L) {
                        newTotalsByCategory.remove(categoryId)
                    }
                }

                val newTotalsByWorker = currentTotalsByWorker.toMutableMap()
                if (oldWorkerId.isNotEmpty() && !isAdd) {
                    val oldWorkerTotal = newTotalsByWorker[oldWorkerId] ?: 0L
                    newTotalsByWorker[oldWorkerId] = (oldWorkerTotal - oldDurationMinutes).coerceAtLeast(0L)
                    if (newTotalsByWorker[oldWorkerId] == 0L) {
                        newTotalsByWorker.remove(oldWorkerId)
                    }
                }
                if (workerId.isNotEmpty()) {
                    val workerTotal = newTotalsByWorker[workerId] ?: 0L
                    newTotalsByWorker[workerId] = workerTotal + deltaMinutes
                    if (newTotalsByWorker[workerId] == 0L) {
                        newTotalsByWorker.remove(workerId)
                    }
                }

                val cacheData = mapOf(
                    "totalMinutes" to newTotalMinutes,
                    "blockCount" to newBlockCount,
                    "totalsByCategory" to newTotalsByCategory,
                    "totalsByWorker" to newTotalsByWorker,
                    "updatedAt" to Timestamp.now()
                )

                firestore.collection("workDays")
                    .document(date)
                    .update(cacheData)
                    .addOnSuccessListener {
                        onComplete()
                    }
                    .addOnFailureListener { _ ->
                        onComplete()
                    }
            }
            .addOnFailureListener { _ ->
                onComplete()
            }
    }

    /**
     * Checks if a block overlaps with existing blocks for the same day
     */
    fun checkOverlap(
        date: String,
        startAt: Timestamp,
        endAt: Timestamp,
        excludeBlockId: String? = null,
        onResult: (Boolean, List<WorkBlock>) -> Unit
    ) {
        getBlocksForDay(
            date = date,
            onSuccess = { blocks ->
                val filteredBlocks = if (excludeBlockId != null) {
                    blocks.filter { it.id != excludeBlockId }
                } else {
                    blocks
                }

                val overlappingBlocks = filteredBlocks.filter { existingBlock ->
                    val existingStart = existingBlock.startAt ?: return@filter false
                    val existingEnd = existingBlock.endAt ?: return@filter false

                    startAt.toDate().time < existingEnd.toDate().time &&
                    endAt.toDate().time > existingStart.toDate().time
                }

                onResult(overlappingBlocks.isNotEmpty(), overlappingBlocks)
            },
            onError = { _ ->
                onResult(false, emptyList())
            }
        )
    }

    /**
     * Gets work days for a date range
     */
    fun getWorkDaysForRange(
        startDate: String,
        endDate: String,
        onSuccess: (List<WorkDay>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firestore.collection("workDays")
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val workDays = querySnapshot.documents.mapNotNull { doc ->
                    val workDayDTO = doc.toObject(WorkDayDTO::class.java)
                    workDayDTO?.toParcelable(doc.id)
                }
                onSuccess(workDays)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }
}

