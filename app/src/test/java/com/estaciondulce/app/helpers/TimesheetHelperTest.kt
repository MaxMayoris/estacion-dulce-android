package com.estaciondulce.app.helpers

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertNotNull

class TimesheetHelperTest {

    private lateinit var timesheetHelper: TimesheetHelper
    private lateinit var genericHelper: GenericHelper

    @Before
    fun setup() {
        mockkStatic(FirebaseFirestore::class)
        mockkStatic(FirebaseAuth::class)
        
        val mockFirestore = mockk<FirebaseFirestore>(relaxed = true)
        val mockAuth = mockk<FirebaseAuth>(relaxed = true)
        
        every { FirebaseFirestore.getInstance() } returns mockFirestore
        every { FirebaseAuth.getInstance() } returns mockAuth
        
        genericHelper = mockk(relaxed = true)
        timesheetHelper = TimesheetHelper(genericHelper)
    }

    @After
    fun tearDown() {
        unmockkStatic(FirebaseFirestore::class)
        unmockkStatic(FirebaseAuth::class)
    }

    @Test
    fun `TimesheetHelper initializes successfully with mocked Firebase`() {
        assertNotNull(timesheetHelper)
    }
}
