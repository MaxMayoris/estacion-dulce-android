package com.estaciondulce.app.activities

import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.ActivityTimesheetStatisticsBinding
import com.estaciondulce.app.helpers.TimesheetHelper
import com.estaciondulce.app.models.parcelables.WorkDay
import com.estaciondulce.app.models.parcelables.WorkBlock
import com.estaciondulce.app.repository.FirestoreRepository
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for displaying timesheet statistics with monthly charts
 */
class TimesheetStatisticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimesheetStatisticsBinding
    private val timesheetHelper = TimesheetHelper()
    private var currentMonth: Calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("es"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimesheetStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Jornadas"

        setMonthToFirstDay(currentMonth)

        setupNavigation()
        loadMonthData()
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
     * Sets up navigation buttons
     */
    private fun setupNavigation() {
        binding.prevMonthButton.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            setMonthToFirstDay(currentMonth)
            loadMonthData()
        }

        binding.nextMonthButton.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            setMonthToFirstDay(currentMonth)
            loadMonthData()
        }
    }

    /**
     * Loads work days for the current month
     */
    private fun loadMonthData() {
        updateMonthYearText()

        val monthEnd = Calendar.getInstance().apply {
            timeInMillis = currentMonth.timeInMillis
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
        }

        val startDate = dateFormat.format(currentMonth.time)
        val endDate = dateFormat.format(monthEnd.time)

        timesheetHelper.getWorkDaysForRange(
            startDate = startDate,
            endDate = endDate,
            onSuccess = { workDays ->
                loadBlocksForMonth(workDays, startDate, endDate)
            },
            onError = { exception ->
                com.estaciondulce.app.utils.CustomToast.showError(
                    this,
                    "Error al cargar datos: ${exception.message}"
                )
            }
        )
    }

    /**
     * Loads all blocks for the month to calculate statistics
     */
    @Suppress("UNUSED_PARAMETER")
    private fun loadBlocksForMonth(workDays: List<WorkDay>, _startDate: String, _endDate: String) {
        val allBlocks = mutableListOf<WorkBlock>()
        var loadedDays = 0
        val totalDays = workDays.size

        if (totalDays == 0) {
            updateCharts(emptyList(), emptyList())
            return
        }

        workDays.forEach { workDay ->
            timesheetHelper.getBlocksForDay(
                date = workDay.date,
                onSuccess = { blocks ->
                    allBlocks.addAll(blocks)
                    loadedDays++
                    if (loadedDays >= totalDays) {
                        updateCharts(workDays, allBlocks)
                    }
                },
                onError = { _ ->
                    loadedDays++
                    if (loadedDays >= totalDays) {
                        updateCharts(workDays, allBlocks)
                    }
                }
            )
        }
    }

    /**
     * Updates all charts with the loaded data
     */
    private fun updateCharts(workDays: List<WorkDay>, blocks: List<WorkBlock>) {
        updateTotalHours(workDays)
        updateDailyHoursChart(workDays)
        updateCategoriesChart(blocks)
    }

    /**
     * Updates the total hours display
     */
    private fun updateTotalHours(workDays: List<WorkDay>) {
        val totalMinutes = workDays.sumOf { it.totalMinutes }
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

        binding.totalHoursText.text = "Horas trabajadas: $totalStr"
    }

    /**
     * Updates the daily hours line chart
     */
    private fun updateDailyHoursChart(workDays: List<WorkDay>) {
        val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()

        for (day in 1..daysInMonth) {
            val dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", 
                currentMonth.get(Calendar.YEAR),
                currentMonth.get(Calendar.MONTH) + 1,
                day)
            val workDay = workDays.find { it.date == dateStr }
            val hours = (workDay?.totalMinutes ?: 0L) / 60.0
            entries.add(Entry((day - 1).toFloat(), hours.toFloat()))
            labels.add(day.toString())
        }

        if (entries.all { it.y == 0f }) {
            binding.dailyHoursChart.visibility = View.GONE
            binding.dailyHoursEmptyMessage.visibility = View.VISIBLE
            return
        }

        binding.dailyHoursChart.visibility = View.VISIBLE
        binding.dailyHoursEmptyMessage.visibility = View.GONE

        val dataSet = LineDataSet(entries, "Horas").apply {
            color = ContextCompat.getColor(this@TimesheetStatisticsActivity, R.color.button_gradient_start)
            setCircleColor(ContextCompat.getColor(this@TimesheetStatisticsActivity, R.color.button_gradient_start))
            lineWidth = 3f
            circleRadius = 4f
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawFilled(false)
            setDrawValues(false)
        }

        val lineData = LineData(dataSet)
        setupDailyHoursChart(binding.dailyHoursChart, lineData, labels)
    }

    /**
     * Sets up the daily hours line chart
     */
    private fun setupDailyHoursChart(chart: LineChart, data: LineData, labels: List<String>) {
        chart.data = data
        chart.description.isEnabled = false
        chart.animateY(1000)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.textColor = Color.BLACK
        xAxis.textSize = 10f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val index = value.toInt()
                return if (index >= 0 && index < labels.size) labels[index] else ""
            }
        }

        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f

        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        chart.invalidate()
    }

    /**
     * Updates the categories bar chart
     */
    private fun updateCategoriesChart(blocks: List<WorkBlock>) {
        val categories = FirestoreRepository.workCategoriesLiveData.value?.associateBy { it.id } ?: mapOf()
        val categoryMinutes = mutableMapOf<String, Long>()

        blocks.forEach { block ->
            categoryMinutes[block.categoryId] = (categoryMinutes[block.categoryId] ?: 0L) + block.durationMinutes
        }

        if (categoryMinutes.isEmpty()) {
            binding.categoriesChart.visibility = View.GONE
            binding.categoriesEmptyMessage.visibility = View.VISIBLE
            return
        }

        binding.categoriesChart.visibility = View.VISIBLE
        binding.categoriesEmptyMessage.visibility = View.GONE

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        categoryMinutes.toList().sortedByDescending { it.second }.forEachIndexed { index, (categoryId, totalMinutes) ->
            val category = categories[categoryId]
            val hours = totalMinutes / 60.0
            entries.add(BarEntry(index.toFloat(), hours.toFloat()))
            labels.add(category?.name ?: "Desconocida")
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = ContextCompat.getColor(this@TimesheetStatisticsActivity, R.color.button_gradient_start)
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val totalMinutes = Math.round(value * 60).toLong()
                    val h = totalMinutes / 60
                    val m = totalMinutes % 60
                    return when {
                        h > 0 && m > 0 -> "${h}h ${m}m"
                        h > 0 -> "${h}h"
                        m > 0 -> "${m}m"
                        else -> "0h"
                    }
                }
            }
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }

        setupCategoriesChart(binding.categoriesChart, barData, labels)
    }

    /**
     * Sets up the categories bar chart
     */
    private fun setupCategoriesChart(chart: BarChart, data: BarData, labels: List<String>) {
        chart.data = data
        chart.description.isEnabled = false
        chart.setFitBars(true)
        chart.animateY(1000)

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.textColor = Color.BLACK
        xAxis.textSize = 10f
        xAxis.setLabelCount(labels.size, false)
        xAxis.setDrawLabels(true)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val index = value.toInt()
                return if (index >= 0 && index < labels.size) {
                    val label = labels[index]
                    if (label.length > 15) label.substring(0, 15) + "..." else label
                } else ""
            }
        }
        xAxis.setLabelRotationAngle(-45f)
        xAxis.yOffset = 10f

        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f

        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        chart.setExtraOffsets(0f, 0f, 0f, 40f)

        chart.invalidate()
    }

    /**
     * Updates the month and year text
     */
    private fun updateMonthYearText() {
        val monthYearText = monthYearFormat.format(currentMonth.time)
        val capitalizedText = monthYearText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        binding.monthYearText.text = capitalizedText
    }

    /**
     * Sets calendar to first day of the month
     */
    private fun setMonthToFirstDay(calendar: Calendar) {
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
}

