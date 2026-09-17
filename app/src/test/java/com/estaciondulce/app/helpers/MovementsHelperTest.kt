package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.parcelables.Movement
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MovementsHelperTest {

    private lateinit var movementsHelper: MovementsHelper
    private lateinit var genericHelper: GenericHelper

    @Before
    fun setup() {
        io.mockk.mockkStatic(com.google.firebase.firestore.FirebaseFirestore::class)
        val mockFirestore = mockk<com.google.firebase.firestore.FirebaseFirestore>(relaxed = true)
        io.mockk.every { com.google.firebase.firestore.FirebaseFirestore.getInstance() } returns mockFirestore
        genericHelper = mockk(relaxed = true)
        movementsHelper = MovementsHelper(genericHelper)
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkStatic(com.google.firebase.firestore.FirebaseFirestore::class)
    }

    @Test
    fun `addMovement calls genericHelper with correct collection and data`() {
        // Arrange
        val movement = Movement(id = "", type = EMovementType.PURCHASE) // Not SALE, to avoid kitchen orders logic
        val onSuccess: (Movement) -> Unit = mockk(relaxed = true)
        val onError: (Exception) -> Unit = mockk(relaxed = true)

        // Act
        movementsHelper.addMovement(movement, onSuccess, onError)

        // Assert
        val collectionSlot = slot<String>()
        verify {
            genericHelper.addDocument(
                collectionName = capture(collectionSlot),
                data = any(),
                onSuccess = any(),
                onError = any()
            )
        }
        assertEquals("movements", collectionSlot.captured)
    }
}
