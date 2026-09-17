package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.Person
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PersonsHelperTest {

    private lateinit var personsHelper: PersonsHelper
    private lateinit var genericHelper: GenericHelper

    @Before
    fun setup() {
        genericHelper = mockk(relaxed = true)
        personsHelper = PersonsHelper(genericHelper)
    }

    @Test
    fun `addPerson calls genericHelper with correct collection and data`() {
        // Arrange
        val person = Person(id = "", name = "John Doe", type = "CLIENT")
        val onSuccess: (Person) -> Unit = mockk(relaxed = true)
        val onError: (Exception) -> Unit = mockk(relaxed = true)

        // Act
        personsHelper.addPerson(person, onSuccess, onError)

        // Assert
        val collectionSlot = slot<String>()
        val dataSlot = slot<Map<String, Any?>>()
        verify {
            genericHelper.addDocument(
                collectionName = capture(collectionSlot),
                data = capture(dataSlot),
                onSuccess = any(),
                onError = any()
            )
        }
        assertEquals("persons", collectionSlot.captured)
        assertEquals("John Doe", dataSlot.captured["name"])
    }
}
