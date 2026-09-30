package com.estaciondulce.app.activities

import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.estaciondulce.app.repository.FirestoreRepository
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.mockk

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE]) // API 34
class ActivitiesCrashTest {

    @Before
    fun setup() {
        mockkObject(FirestoreRepository)
        // Mock common live data that might be accessed
        every { FirestoreRepository.categoriesLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.eventsLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.productsLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.recipesLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.movementsLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.personsLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.sectionsLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.measuresLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.workersLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.workCategoriesLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.shipmentSettingsLiveData } returns MutableLiveData(null)
        
        // Mock FirebaseAuth
        io.mockk.mockkStatic(FirebaseAuth::class)
        val mockAuth = mockk<FirebaseAuth>(relaxed = true)
        val mockUser = mockk<FirebaseUser>(relaxed = true)
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `HomeActivity should launch without crashing`() {
        ActivityScenario.launch(HomeActivity::class.java).use { scenario ->
            scenario.onActivity { assert(it != null) }
        }
    }
}
