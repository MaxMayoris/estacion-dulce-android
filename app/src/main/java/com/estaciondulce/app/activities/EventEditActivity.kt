package com.estaciondulce.app.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ActivityEventEditBinding
import com.estaciondulce.app.helpers.EventsHelper
import com.estaciondulce.app.models.parcelables.Event
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.CustomToast
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EventEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventEditBinding
    private lateinit var loader: CustomLoader
    private var eventId: String? = null
    private var currentEvent: Event = Event()
    
    private var startDate: Date? = null
    private var endDate: Date? = null
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEventEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loader = CustomLoader(this)
        
        eventId = intent.getStringExtra("EVENT_ID")
        
        setupHeader()
        setupDatePickers()
        
        if (eventId != null) {
            loadEvent(eventId!!)
        }
        
        binding.saveButton.setOnClickListener {
            saveEvent()
        }
    }

    private fun setupHeader() {
        findViewById<TextView>(R.id.fragmentTitle).text = if (eventId != null) "Editar Evento" else "Nuevo Evento"
        findViewById<MaterialButton>(R.id.backButton).setOnClickListener { finish() }
    }

    private fun setupDatePickers() {
        binding.btnStartDate.setOnClickListener {
            showDatePicker(startDate) { date ->
                startDate = date
                binding.btnStartDate.text = dateFormat.format(date)
            }
        }
        
        binding.btnEndDate.setOnClickListener {
            showDatePicker(endDate) { date ->
                endDate = date
                binding.btnEndDate.text = dateFormat.format(date)
            }
        }
    }
    
    private fun showDatePicker(currentDate: Date?, onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()
        currentDate?.let { calendar.time = it }
        
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance()
                selectedCal.set(year, month, dayOfMonth)
                onDateSelected(selectedCal.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadEvent(id: String) {
        val events = FirestoreRepository.eventsLiveData.value ?: emptyList()
        val event = events.find { it.id == id }
        if (event != null) {
            currentEvent = event
            binding.nameInput.setText(event.name)
            binding.descriptionInput.setText(event.description)
            
            startDate = event.startDate
            endDate = event.endDate
            
            startDate?.let { binding.btnStartDate.text = dateFormat.format(it) }
            endDate?.let { binding.btnEndDate.text = dateFormat.format(it) }
            
            // Check show status of the associated category
            val cat = FirestoreRepository.categoriesLiveData.value?.find { it.id == event.categoryId }
            if (cat != null) {
                binding.showTagSwitch.isChecked = cat.show
            }
        }
    }

    private fun saveEvent() {
        val name = binding.nameInput.text.toString().trim()
        val description = binding.descriptionInput.text.toString().trim()
        val showTag = binding.showTagSwitch.isChecked

        if (name.isEmpty()) {
            binding.nameInput.error = "Requerido"
            return
        }
        
        if (startDate != null && endDate != null && startDate!!.after(endDate)) {
            CustomToast.showError(this, "Fecha inicio no puede ser posterior a fecha fin.")
            return
        }

        currentEvent = currentEvent.copy(
            name = name,
            description = description,
            startDate = startDate,
            endDate = endDate
        )

        loader.show()
        lifecycleScope.launch {
            EventsHelper().saveEvent(
                event = currentEvent,
                showInCategories = showTag,
                onSuccess = {
                    loader.hide()
                    CustomToast.showSuccess(this@EventEditActivity, "Evento guardado")
                    finish()
                },
                onError = { e ->
                    loader.hide()
                    CustomToast.showError(this@EventEditActivity, "Error: ${e.message}")
                }
            )
        }
    }
}
