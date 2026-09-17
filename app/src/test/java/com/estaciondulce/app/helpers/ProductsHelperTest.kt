package com.estaciondulce.app.helpers

import com.estaciondulce.app.models.parcelables.Product
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.Exception

class ProductsHelperTest {

    private lateinit var productsHelper: ProductsHelper
    private lateinit var genericHelper: GenericHelper

    @Before
    fun setup() {
        genericHelper = mockk(relaxed = true)
        productsHelper = ProductsHelper(genericHelper)
    }

    @Test
    fun `addProduct calls onError if name is blank`() {
        val product = Product(name = "", measure = "KG")
        var errorCalled = false

        productsHelper.addProduct(
            product = product,
            onSuccess = {},
            onError = { exception ->
                errorCalled = true
                assertTrue(exception.message?.contains("nombre y medida") == true)
            }
        )

        assertTrue(errorCalled)
        verify(exactly = 0) { genericHelper.addDocument(any(), any(), any(), any()) }
    }

    @Test
    fun `addProduct calls onError if measure is blank`() {
        val product = Product(name = "Flour", measure = "")
        var errorCalled = false

        productsHelper.addProduct(
            product = product,
            onSuccess = {},
            onError = { exception ->
                errorCalled = true
                assertTrue(exception.message?.contains("nombre y medida") == true)
            }
        )

        assertTrue(errorCalled)
        verify(exactly = 0) { genericHelper.addDocument(any(), any(), any(), any()) }
    }

    @Test
    fun `addProduct saves product when valid`() {
        val product = Product(name = "Flour", measure = "KG")
        
        productsHelper.addProduct(
            product = product,
            onSuccess = {},
            onError = {}
        )

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
        assertEquals("products", collectionSlot.captured)
        assertEquals("Flour", dataSlot.captured["name"])
    }
}
