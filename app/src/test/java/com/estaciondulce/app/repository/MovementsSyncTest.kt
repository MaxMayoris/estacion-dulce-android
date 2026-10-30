package com.estaciondulce.app.repository

import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.parcelables.Movement
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementsSyncTest {

    private fun doc(id: String, deleted: Boolean = false, updatedAtSeconds: Long? = null, mappable: Boolean = true) =
        MovementDocState(
            id = id,
            movement = if (mappable) Movement(id = id, type = EMovementType.SALE) else null,
            deleted = deleted,
            updatedAt = updatedAtSeconds?.let { Timestamp(it, 0) }
        )

    @Test
    fun `visibleMovements leaves out soft-deleted and unmappable documents`() {
        val docs = listOf(doc("a"), doc("b", deleted = true), doc("c", mappable = false), doc("d"))

        assertEquals(listOf("a", "d"), MovementsSync.visibleMovements(docs).map { it.id })
    }

    @Test
    fun `syncStart is the epoch when no cached document has updatedAt`() {
        assertEquals(Timestamp(0, 0), MovementsSync.syncStart(emptyList()))
        assertEquals(Timestamp(0, 0), MovementsSync.syncStart(listOf(doc("a"), doc("b"))))
    }

    @Test
    fun `syncStart is the newest cached updatedAt minus a margin`() {
        val docs = listOf(doc("a", updatedAtSeconds = 1_000), doc("b"), doc("c", updatedAtSeconds = 5_000))

        assertEquals(Timestamp(4_940, 0), MovementsSync.syncStart(docs))
    }

    @Test
    fun `syncStart counts soft-deleted documents`() {
        val docs = listOf(doc("a", updatedAtSeconds = 1_000), doc("b", deleted = true, updatedAtSeconds = 9_000))

        assertEquals(Timestamp(8_940, 0), MovementsSync.syncStart(docs))
    }

    @Test
    fun `syncStart never goes below the epoch`() {
        assertEquals(Timestamp(0, 0), MovementsSync.syncStart(listOf(doc("a", updatedAtSeconds = 10))))
    }
}
