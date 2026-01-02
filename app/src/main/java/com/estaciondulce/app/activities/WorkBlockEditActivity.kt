package com.estaciondulce.app.activities

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ActivityWorkBlockEditBinding
import com.estaciondulce.app.helpers.TimesheetHelper
import com.estaciondulce.app.models.parcelables.WorkBlock
import com.estaciondulce.app.models.parcelables.WorkCategory
import com.estaciondulce.app.models.parcelables.Worker
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity to add or edit a work block
 */
class WorkBlockEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkBlockEditBinding
    private val timesheetHelper = TimesheetHelper()
    private lateinit var customLoader: CustomLoader
    private var date: String = ""
    private var currentBlock: WorkBlock? = null
    private var selectedStartHour: Int = 6
    private var selectedStartMinute: Int = 0
    private var selectedEndHour: Int = 13
    private var selectedEndMinute: Int = 0
    private var selectedCategoryId: String = ""
    private var selectedWorkerId: String = ""
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkBlockEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        customLoader = CustomLoader(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        date = intent.getStringExtra("DATE") ?: ""
        @Suppress("DEPRECATION")
        currentBlock = intent.getParcelableExtra<WorkBlock>("BLOCK")

        if (date.isEmpty()) {
            date = dateFormat.format(Date())
        }

        supportActionBar?.title = if (currentBlock != null) "Editar Bloque" else "Agregar Bloque"

        setupUI()
        setupSpinners()
        setupTimePickers()
        setupButtons()
        loadBlockData()
    }

    /**
     * Sets up UI elements
     */
    private fun setupUI() {
        updateDateDisplay()

        binding.dateInput.isEnabled = currentBlock == null
        binding.dateInput.setOnClickListener {
            if (currentBlock == null) {
                showDatePicker()
            }
        }
    }

    /**
     * Updates the date display
     */
    private fun updateDateDisplay() {
        try {
            val dateObj = dateFormat.parse(date)
            if (dateObj != null) {
                val formattedDate = displayDateFormat.format(dateObj)
                val capitalizedDate = formattedDate.split(" ").joinToString(" ") { word ->
                    if (word == "de") {
                        word
                    } else {
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                }
                binding.dateInput.setText(capitalizedDate)
            }
        } catch (e: Exception) {
            binding.dateInput.setText(date)
        }
    }

    /**
     * Shows date picker dialog
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        try {
            val dateObj = dateFormat.parse(date)
            if (dateObj != null) {
                calendar.time = dateObj
            }
        } catch (e: Exception) {
            // Use current date if parsing fails
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay)
                }
                date = dateFormat.format(selectedCalendar.time)
                updateDateDisplay()
            },
            year,
            month,
            day
        ).show()
    }

    /**
     * Sets up category and worker spinners
     */
    private fun setupSpinners() {
        val categories = FirestoreRepository.workCategoriesLiveData.value ?: emptyList()
        val categoryOptions = categories.map { "${it.name}" }
        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categoryOptions)
        binding.categorySpinner.setAdapter(categoryAdapter)

        binding.categorySpinner.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryId = categories[position].id
        }

        val workers = FirestoreRepository.workersLiveData.value ?: emptyList()
        val workerOptions = workers.map { "${it.displayName}" }
        val workerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, workerOptions)
        binding.workerSpinner.setAdapter(workerAdapter)

        binding.workerSpinner.setOnItemClickListener { _, _, position, _ ->
            selectedWorkerId = workers[position].id
        }

        val currentUser = FirebaseAuth.getInstance().currentUser
        val currentUserId = currentUser?.uid ?: currentUser?.email ?: ""
        val currentWorker = workers.find { it.id == currentUserId }
        if (currentWorker != null && currentBlock == null) {
            val workerIndex = workers.indexOf(currentWorker)
            if (workerIndex >= 0) {
                binding.workerSpinner.setText(workerOptions[workerIndex], false)
                selectedWorkerId = currentWorker.id
            }
        }
    }

    /**
     * Sets up time pickers
     */
    private fun setupTimePickers() {
        binding.startTimeInput.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedStartHour = hourOfDay
                    selectedStartMinute = minute
                    updateStartTimeDisplay()
                    validateTimes()
                },
                selectedStartHour,
                selectedStartMinute,
                true
            ).show()
        }

        binding.endTimeInput.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    selectedEndHour = hourOfDay
                    selectedEndMinute = minute
                    updateEndTimeDisplay()
                    validateTimes()
                },
                selectedEndHour,
                selectedEndMinute,
                true
            ).show()
        }
    }

    /**
     * Sets up buttons
     */
    private fun setupButtons() {
        binding.saveButton.setOnClickListener {
            validateAndSave()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
            }
    }

    /**
     * Loads existing block data if editing
     */
    private fun loadBlockData() {
        currentBlock?.let { block ->
            block.startAt?.toDate()?.let { startDate ->
                val calendar = Calendar.getInstance().apply {
                    time = startDate
                }
                selectedStartHour = calendar.get(Calendar.HOUR_OF_DAY)
                selectedStartMinute = calendar.get(Calendar.MINUTE)
                updateStartTimeDisplay()
            }

            block.endAt?.toDate()?.let { endDate ->
                val calendar = Calendar.getInstance().apply {
                    time = endDate
                }
                selectedEndHour = calendar.get(Calendar.HOUR_OF_DAY)
                selectedEndMinute = calendar.get(Calendar.MINUTE)
                updateEndTimeDisplay()
            }

            selectedCategoryId = block.categoryId
            val categories = FirestoreRepository.workCategoriesLiveData.value
            categories?.find { it.id == block.categoryId }?.let { category ->
                binding.categorySpinner.setText(category.name, false)
            }

            selectedWorkerId = block.workerId
            val workers = FirestoreRepository.workersLiveData.value
            workers?.find { it.id == block.workerId }?.let { worker ->
                binding.workerSpinner.setText(worker.displayName, false)
            }

            binding.noteInput.setText(block.note)
        }
    }

    /**
     * Updates start time display
     */
    private fun updateStartTimeDisplay() {
        val timeStr = String.format("%02d:%02d", selectedStartHour, selectedStartMinute)
        binding.startTimeInput.setText(timeStr)
    }

    /**
     * Updates end time display
     */
    private fun updateEndTimeDisplay() {
        val timeStr = String.format("%02d:%02d", selectedEndHour, selectedEndMinute)
        binding.endTimeInput.setText(timeStr)
    }

    /**
     * Validates times and shows errors
     */
    private fun validateTimes(): Boolean {
        val startMinutes = selectedStartHour * 60 + selectedStartMinute
        val endMinutes = selectedEndHour * 60 + selectedEndMinute

        if (endMinutes <= startMinutes) {
            binding.errorText.text = "La hora de fin debe ser mayor que la hora de inicio"
            binding.errorText.visibility = View.VISIBLE
            return false
        }

        binding.errorText.visibility = View.GONE
        return true
    }

    /**
     * Validates all fields and checks for overlaps
     */
    private fun validateAndSave(): Boolean {
        if (!validateTimes()) {
            return false
        }

        if (selectedCategoryId.isEmpty()) {
            CustomToast.showError(this, "Seleccione una categoría")
            return false
        }

        if (selectedWorkerId.isEmpty()) {
            CustomToast.showError(this, "Seleccione un trabajador")
            return false
        }

        val startCalendar = Calendar.getInstance().apply {
            time = dateFormat.parse(date) ?: Date()
            set(Calendar.HOUR_OF_DAY, selectedStartHour)
            set(Calendar.MINUTE, selectedStartMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val endCalendar = Calendar.getInstance().apply {
            time = dateFormat.parse(date) ?: Date()
            set(Calendar.HOUR_OF_DAY, selectedEndHour)
            set(Calendar.MINUTE, selectedEndMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startTimestamp = Timestamp(startCalendar.time)
        val endTimestamp = Timestamp(endCalendar.time)

        customLoader.show()

        timesheetHelper.checkOverlap(
            date = date,
            startAt = startTimestamp,
            endAt = endTimestamp,
            excludeBlockId = currentBlock?.id,
            onResult = { hasOverlap, _ ->
                customLoader.hide()

                if (hasOverlap) {
                    binding.errorText.text = "Este bloque se superpone con otro bloque del mismo día"
                    binding.errorText.visibility = View.VISIBLE
                    CustomToast.showError(this, "Se superpone con otro bloque")
                } else {
                    saveBlockInternal(startTimestamp, endTimestamp)
                }
            }
        )

        return false
    }

    /**
     * Internal method to save the block
     */
    private fun saveBlockInternal(startTimestamp: Timestamp, endTimestamp: Timestamp) {
        val durationMinutes = ((endTimestamp.toDate().time - startTimestamp.toDate().time) / (1000 * 60)).toLong()

        val block = WorkBlock(
            id = currentBlock?.id ?: "",
            startAt = startTimestamp,
            endAt = endTimestamp,
            durationMinutes = durationMinutes,
            categoryId = selectedCategoryId,
            workerId = selectedWorkerId,
            note = binding.noteInput.text.toString().trim(),
            createdBy = currentBlock?.createdBy ?: "",
            updatedBy = "",
            createdAt = currentBlock?.createdAt,
            updatedAt = null
        )

        customLoader.show()

        if (currentBlock != null) {
            timesheetHelper.updateWorkBlock(
                date = date,
                blockId = currentBlock!!.id,
                block = block,
                onSuccess = {
                    customLoader.hide()
                    CustomToast.showSuccess(this, "Bloque actualizado correctamente")
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al actualizar bloque: ${exception.message}")
                }
            )
        } else {
            timesheetHelper.addWorkBlock(
                date = date,
                block = block,
                onSuccess = {
                    customLoader.hide()
                    CustomToast.showSuccess(this, "Bloque agregado correctamente")
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    customLoader.hide()
                    CustomToast.showError(this, "Error al agregar bloque: ${exception.message}")
                }
            )
        }
    }
}

