package com.estaciondulce.app.activities

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.R
import com.estaciondulce.app.helpers.TimesheetHelper
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.repository.FirestoreRepository

import com.estaciondulce.app.adapters.MovementAdapter
import com.estaciondulce.app.customviews.TableView
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.toColumnConfigs
import com.estaciondulce.app.utils.FormatUtils
import java.util.Locale

class EventDetailActivity : AppCompatActivity() {

    private lateinit var tvTotalSales: TextView
    private lateinit var tvTotalPurchases: TextView
    private lateinit var tvBalance: TextView
    private lateinit var tvTotalHours: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var summaryTab: TextView
    private lateinit var movementsTab: TextView
    private lateinit var summaryContent: View
    private lateinit var movementsContent: View
    private lateinit var movementsTable: TableView<Movement>
    private lateinit var emptyMovementsText: TextView

    private val timesheetHelper = TimesheetHelper()

    private var eventId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        eventId = intent.getStringExtra("EVENT_ID")
        if (eventId == null) {
            finish()
            return
        }

        val event = FirestoreRepository.eventsLiveData.value?.find { it.id == eventId }
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val titleText = if (!event?.description.isNullOrEmpty()) {
            "${event?.name} - ${event?.description}"
        } else {
            event?.name ?: "Detalle del Evento"
        }
        supportActionBar?.title = titleText

        tvTotalSales = findViewById(R.id.tvTotalSales)
        tvTotalPurchases = findViewById(R.id.tvTotalPurchases)
        tvBalance = findViewById(R.id.tvBalance)
        tvTotalHours = findViewById(R.id.tvTotalHours)
        progressBar = findViewById(R.id.progressBar)
        summaryTab = findViewById(R.id.summaryTab)
        movementsTab = findViewById(R.id.movementsTab)
        summaryContent = findViewById(R.id.summaryContent)
        movementsContent = findViewById(R.id.movementsContent)
        movementsTable = findViewById(R.id.eventMovementsTable)
        emptyMovementsText = findViewById(R.id.emptyMovementsText)

        summaryTab.setOnClickListener { selectTab(showMovements = false) }
        movementsTab.setOnClickListener { selectTab(showMovements = true) }

        FirestoreRepository.movementsLiveData.observe(this) { allMovements ->
            val eventMovements = allMovements.filter { it.eventId == eventId }
            showMovementStats(eventMovements)
            setupMovementsTable(eventMovements)
        }

        eventId?.let { loadEventStats(it) }
    }

    /**
     * Switches between the summary tab and the related movements tab.
     */
    private fun selectTab(showMovements: Boolean) {
        val selectedTab = if (showMovements) movementsTab else summaryTab
        val unselectedTab = if (showMovements) summaryTab else movementsTab
        selectedTab.setBackgroundResource(R.drawable.tab_selected_background)
        selectedTab.setTextColor(getColor(R.color.white))
        unselectedTab.setBackgroundResource(R.drawable.tab_unselected_background)
        unselectedTab.setTextColor(getColor(R.color.text_secondary))
        summaryContent.visibility = if (showMovements) View.GONE else View.VISIBLE
        movementsContent.visibility = if (showMovements) View.VISIBLE else View.GONE
    }

    /**
     * Shows the movements linked to the event, newest first. Amounts are green for sales and red for purchases,
     * and tapping a row opens the movement for editing.
     */
    private fun setupMovementsTable(movements: List<Movement>) {
        movementsTab.text = if (movements.isEmpty()) "Movimientos" else "Movimientos (${movements.size})"
        emptyMovementsText.visibility = if (movements.isEmpty()) View.VISIBLE else View.GONE
        movementsTable.visibility = if (movements.isEmpty()) View.GONE else View.VISIBLE
        if (movements.isEmpty()) return

        val sortedList = movements.sortedByDescending { it.movementDate }
        val columnConfigs = listOf("Fecha", "Nombre", "Monto").toColumnConfigs(currencyColumns = setOf(2))
        movementsTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = sortedList,
            adapter = MovementAdapter(
                movementList = sortedList,
                onRowClick = { movement -> openMovement(movement) },
                onDeleteClick = {},
                showDeleteIcon = false,
                highlightAmountByType = true
            ) { movement ->
                listOf(
                    FormatUtils.formatShortDateTime(movement.movementDate),
                    personName(movement),
                    movement.totalAmount
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val movement = item as Movement
                when (columnIndex) {
                    0 -> FormatUtils.formatShortDateTime(movement.movementDate)
                    1 -> personName(movement)
                    2 -> movement.totalAmount
                    else -> null
                }
            },
            enableColumnSorting = false
        )
    }

    private fun personName(movement: Movement): String =
        FirestoreRepository.personsLiveData.value?.find { it.id == movement.personId }
            ?.let { "${it.name} ${it.lastName}" } ?: "Desconocido"

    private fun openMovement(movement: Movement) {
        val intent = android.content.Intent(this, MovementEditActivity::class.java)
        intent.putExtra("MOVEMENT", movement)
        startActivity(intent)
    }

    /**
     * Shows total sales, total purchases and the resulting balance of the event's movements.
     */
    private fun showMovementStats(movements: List<Movement>) {
        var totalSales = 0.0
        var totalPurchases = 0.0

        for (mov in movements) {
            if (mov.type == EMovementType.SALE) {
                totalSales += mov.totalAmount
            } else if (mov.type == EMovementType.PURCHASE) {
                totalPurchases += mov.totalAmount
            }
        }
        val balance = totalSales - totalPurchases

        tvTotalSales.text = FormatUtils.formatCurrency(totalSales)
        tvTotalPurchases.text = FormatUtils.formatCurrency(totalPurchases)
        tvBalance.text = FormatUtils.formatCurrency(balance)

        val balanceColor = if (balance >= 0) R.color.success_green else R.color.error_red
        tvBalance.setTextColor(resources.getColor(balanceColor, theme))
    }

    private fun loadEventStats(eventId: String) {
        progressBar.visibility = View.VISIBLE

        timesheetHelper.getWorkBlocksForEvent(eventId,
            onSuccess = { blocks ->
                progressBar.visibility = View.GONE
                var totalMinutes = 0L
                for (block in blocks) {
                    totalMinutes += block.durationMinutes
                }
                val hours = totalMinutes / 60.0
                tvTotalHours.text = String.format(Locale.getDefault(), "%.1f hrs", hours)

                val breakdownContainer = findViewById<android.widget.LinearLayout>(R.id.hoursBreakdownContainer)
                breakdownContainer.removeAllViews()

                val categories = FirestoreRepository.workCategoriesLiveData.value ?: emptyList()
                val blocksByCategory = blocks.groupBy { it.categoryId }

                for ((categoryId, categoryBlocks) in blocksByCategory) {
                    val categoryMinutes = categoryBlocks.sumOf { it.durationMinutes.toLong() }
                    val categoryHours = categoryMinutes / 60.0

                    val categoryName = categories.find { it.id == categoryId }?.name ?: "Desconocido"

                    val row = android.widget.LinearLayout(this).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { setMargins(0, 0, 0, 16) }
                        gravity = android.view.Gravity.CENTER_VERTICAL
                    }

                    val nameView = TextView(this).apply {
                        text = categoryName
                        textSize = 16f
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }

                    val hoursView = TextView(this).apply {
                        text = String.format(Locale.getDefault(), "%.1f hrs", categoryHours)
                        textSize = 18f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(androidx.core.content.ContextCompat.getColor(this@EventDetailActivity, R.color.text_primary))
                    }

                    row.addView(nameView)
                    row.addView(hoursView)
                    breakdownContainer.addView(row)
                }
            },
            onError = {
                progressBar.visibility = View.GONE
                tvTotalHours.text = "0.0 hrs"
            }
        )
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menu.add(0, 1, 0, "Editar").apply {
            setIcon(R.drawable.ic_edit)
            icon?.setTint(androidx.core.content.ContextCompat.getColor(this@EventDetailActivity, android.R.color.white))
            setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            1 -> {
                val intent = android.content.Intent(this, EventEditActivity::class.java)
                intent.putExtra("EVENT_ID", eventId)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
