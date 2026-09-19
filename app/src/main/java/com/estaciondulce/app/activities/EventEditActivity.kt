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
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView

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
        setupEventNameAutocomplete()
        
        if (eventId != null) {
            loadEvent(eventId!!)
        }
        
        binding.saveButton.setOnClickListener {
            saveEvent()
        }
    }

        private fun setupEventNameAutocomplete() {
        FirestoreRepository.categoriesLiveData.observe(this) { categories ->
            // In case old data has 'event: true' we'd have to migrate it manually or assume the new ones use 'isEvent'
            // We just filter by isEvent == true (or we can just show all names from eventsLiveData)
            val events = FirestoreRepository.eventsLiveData.value ?: emptyList()
            val namesFromEvents = events.map { it.name }
            val namesFromCategories = categories.filter { it.isEvent }.map { it.name }
            
            val eventNames = (namesFromEvents + namesFromCategories).distinct().sorted()
            
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, eventNames)
            val actv = binding.nameInput as AutoCompleteTextView
            actv.setAdapter(adapter)
            
            actv.setOnClickListener {
                actv.showDropDown()
            }
            
            actv.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) actv.showDropDown()
            }
        }
    }

    private fun setupHeader() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (eventId != null) "Editar Evento" else "Nuevo Evento"
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
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
            

        }
    }

    private fun saveEvent() {
        val name = binding.nameInput.text.toString().trim()
        val description = binding.descriptionInput.text.toString().trim()
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
                showInCategories = true,
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
