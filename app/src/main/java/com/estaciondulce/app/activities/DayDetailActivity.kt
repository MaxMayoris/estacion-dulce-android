package com.estaciondulce.app.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.estaciondulce.app.R
import com.estaciondulce.app.adapters.WorkBlockAdapter
import com.estaciondulce.app.databinding.ActivityDayDetailBinding
import com.estaciondulce.app.helpers.TimesheetHelper
import com.estaciondulce.app.models.parcelables.WorkBlock
import com.estaciondulce.app.models.parcelables.WorkCategory
import com.estaciondulce.app.models.parcelables.Worker
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity to display and manage work blocks for a specific day
 */
class DayDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDayDetailBinding
    private val timesheetHelper = TimesheetHelper()
    private lateinit var customLoader: CustomLoader
    private var date: String = ""
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDayDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        customLoader = CustomLoader(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        date = intent.getStringExtra("DATE") ?: ""
        if (date.isEmpty()) {
            CustomToast.showError(this, "Fecha no válida")
            finish()
            return
        }

        setupUI()
        setupRecyclerView()
        setupAddButton()
        loadDayData()
    }

    /**
     * Sets up UI elements
     */
    private fun setupUI() {
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
                supportActionBar?.title = capitalizedDate
            } else {
                supportActionBar?.title = date
            }
        } catch (e: Exception) {
            supportActionBar?.title = date
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
     * Sets up the RecyclerView
     */
    private fun setupRecyclerView() {
        binding.blocksRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    /**
     * Sets up the add block button
     */
    private fun setupAddButton() {
        binding.addBlockButton.setOnClickListener {
            val intent = Intent(this, WorkBlockEditActivity::class.java)
            intent.putExtra("DATE", date)
            workBlockEditLauncher.launch(intent)
        }
    }

    /**
     * Loads blocks for the day
     */
    private fun loadDayData() {
        customLoader.show()

        timesheetHelper.getBlocksForDay(
            date = date,
            onSuccess = { blocks ->
                customLoader.hide()
                updateBlocksList(blocks)
                updateTotalHours(blocks)
            },
            onError = { exception ->
                customLoader.hide()
                CustomToast.showError(this, "Error al cargar bloques: ${exception.message}")
            }
        )
    }

    /**
     * Updates the blocks list in RecyclerView
     */
    private fun updateBlocksList(blocks: List<WorkBlock>) {
        val categories = FirestoreRepository.workCategoriesLiveData.value?.associateBy { it.id } ?: mapOf()
        val workers = FirestoreRepository.workersLiveData.value?.associateBy { it.id } ?: mapOf()

        val sortedBlocks = blocks.sortedBy { it.startAt?.toDate()?.time ?: 0L }

        val adapter = WorkBlockAdapter(
            blocks = sortedBlocks,
            categories = categories,
            workers = workers,
            onEditClick = { block ->
                val intent = Intent(this, WorkBlockEditActivity::class.java)
                intent.putExtra("DATE", date)
                intent.putExtra("BLOCK", block)
                workBlockEditLauncher.launch(intent)
            },
            onDeleteClick = { block ->
                DeleteConfirmationDialog.show(
                    context = this,
                    itemName = "bloque de trabajo",
                    itemType = "bloque",
                    onConfirm = {
                        deleteBlock(block)
                    }
                )
            }
        )

        binding.blocksRecyclerView.adapter = adapter

        binding.emptyStateText.visibility = if (blocks.isEmpty()) View.VISIBLE else View.GONE
    }

    /**
     * Updates the total hours display
     */
    private fun updateTotalHours(blocks: List<WorkBlock>) {
        val totalMinutes = blocks.sumOf { it.durationMinutes }
        val hours = totalMinutes / 60
        val minutes = (totalMinutes % 60).toInt()

        val totalStr = if (hours > 0 && minutes > 0) {
            "${hours}h ${minutes}m"
        } else if (hours > 0) {
            "${hours}h"
        } else if (minutes > 0) {
            "${minutes}m"
        } else {
            "0h"
        }

        binding.totalHoursText.text = totalStr
    }

    /**
     * Deletes a work block
     */
    private fun deleteBlock(block: WorkBlock) {
        customLoader.show()

        timesheetHelper.deleteWorkBlock(
            date = date,
            blockId = block.id,
            onSuccess = {
                customLoader.hide()
                CustomToast.showSuccess(this, "Bloque eliminado correctamente")
                loadDayData()
            },
            onError = { exception ->
                customLoader.hide()
                CustomToast.showError(this, "Error al eliminar bloque: ${exception.message}")
            }
        )
    }

    /**
     * Launcher for WorkBlockEditActivity
     */
    private val workBlockEditLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadDayData()
        }
    }
}

