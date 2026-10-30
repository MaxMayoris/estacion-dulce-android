package com.estaciondulce.app.repository

import com.estaciondulce.app.models.parcelables.Movement
import com.google.firebase.Timestamp

/**
 * Snapshot of a movement document as needed by the incremental sync.
 * [movement] is null when the document could not be mapped.
 */
internal data class MovementDocState(
    val id: String,
    val movement: Movement?,
    val deleted: Boolean,
    val updatedAt: Timestamp?
)

/**
 * Pure helpers for the incremental movements sync: the full history lives in the local
 * Firestore cache and only documents with a newer `updatedAt` are fetched from the server.
 */
internal object MovementsSync {

    const val UPDATED_AT = "updatedAt"
    const val DELETED = "deleted"

    /** Margin subtracted from the last seen `updatedAt` to tolerate near-simultaneous commits. */
    private const val SYNC_MARGIN_SECONDS = 60L

    /** Movements to show: soft-deleted and unmappable documents are left out. */
    fun visibleMovements(docs: List<MovementDocState>): List<Movement> =
        docs.filter { !it.deleted }.mapNotNull { it.movement }

    /**
     * Returns the `updatedAt` lower bound for the server listener, based on the newest
     * cached document. Without any cached `updatedAt` it returns the epoch (full download).
     */
    fun syncStart(cachedDocs: List<MovementDocState>): Timestamp {
        val newest = cachedDocs.mapNotNull { it.updatedAt }.maxOrNull() ?: return Timestamp(0, 0)
        return Timestamp((newest.seconds - SYNC_MARGIN_SECONDS).coerceAtLeast(0), 0)
    }
}
