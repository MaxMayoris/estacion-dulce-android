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

import java.text.NumberFormat
import java.util.Locale

class EventDetailActivity : AppCompatActivity() {

    private lateinit var tvTotalSales: TextView
    private lateinit var tvTotalPurchases: TextView
    private lateinit var tvBalance: TextView
    private lateinit var tvTotalHours: TextView
    private lateinit var progressBar: ProgressBar

    private val timesheetHelper = TimesheetHelper()
    @Suppress("DEPRECATION")
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("es", "AR"))

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

        eventId?.let { loadEventStats(it) }
    }

    private fun loadEventStats(eventId: String) {
        progressBar.visibility = View.VISIBLE

        // 1. Calculate Movements Stats
        val movements = FirestoreRepository.movementsLiveData.value?.filter { it.eventId == eventId } ?: emptyList()
        
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

        tvTotalSales.text = currencyFormat.format(totalSales)
        tvTotalPurchases.text = currencyFormat.format(totalPurchases)
        tvBalance.text = currencyFormat.format(balance)

        val balanceColor = if (balance >= 0) R.color.success_green else R.color.error_red
        tvBalance.setTextColor(resources.getColor(balanceColor, theme))

        // 2. Calculate Work Hours
        timesheetHelper.getWorkBlocksForEvent(eventId,
            onSuccess = { blocks ->
                progressBar.visibility = View.GONE
                var totalMinutes = 0L
                for (block in blocks) {
                    totalMinutes += block.durationMinutes
                }
                val hours = totalMinutes / 60.0
                tvTotalHours.text = String.format(Locale.getDefault(), "%.1f hrs", hours)

                // Populate Breakdown
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
