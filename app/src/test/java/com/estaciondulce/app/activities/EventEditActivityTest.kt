package com.estaciondulce.app.activities

import android.os.Build
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.estaciondulce.app.models.Category
import com.estaciondulce.app.models.parcelables.Event
import com.estaciondulce.app.repository.FirestoreRepository
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE]) // API 34
class EventEditActivityTest {

    @Before
    fun setup() {
        mockkObject(FirestoreRepository)
        every { FirestoreRepository.categoriesLiveData } returns MutableLiveData(emptyList())
        every { FirestoreRepository.eventsLiveData } returns MutableLiveData(emptyList())
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `activity should launch and inflate layout without crashing`() {
        ActivityScenario.launch(EventEditActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                // If we reach here, the layout inflated successfully without crashes (like the MaterialSwitch one)
                assert(activity != null)
            }
        }
    }
}
