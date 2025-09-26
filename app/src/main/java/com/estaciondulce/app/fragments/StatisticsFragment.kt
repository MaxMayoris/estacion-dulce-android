package com.estaciondulce.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.estaciondulce.app.databinding.FragmentStatisticsBinding
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight
import java.text.SimpleDateFormat
import java.util.*

/**
 * Custom value formatter to hide zero values
 */
class HideZeroValueFormatter : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        return if (value == 0f) "" else value.toInt().toString()
    }
}

/**
 * Custom axis label formatter for balance chart
 */
class BalanceAxisLabelFormatter(private val labels: List<String>) : ValueFormatter() {
    override fun getAxisLabel(value: Float, axis: AxisBase?): String {
        val index = value.toInt()
        return if (index >= 0 && index < labels.size) labels[index] else ""
    }
}

/**
 * Custom value formatter to show only unique values (no repetition).
 */
class UniqueValueFormatter : ValueFormatter() {
    private val shownValues = mutableSetOf<Float>()
    
    override fun getFormattedValue(value: Float): String {
        return if (value == 0f) {
            "" // Don't show zero values
        } else if (shownValues.contains(value)) {
            "" // Don't show repeated values
        } else {
            shownValues.add(value)
            value.toInt().toString()
        }
    }
    
    /**
     * Resets the shown values to start fresh for each chart rendering.
     */
    fun reset() {
        shownValues.clear()
    }
}

/**
 * Fragment for displaying financial statistics with charts using MPAndroidChart.
 * Shows balance over configurable periods and monthly sales data.
 */
class StatisticsFragment : Fragment() {

    private var _binding: FragmentStatisticsBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository
    private lateinit var customLoader: CustomLoader
    
    private var selectedTab = "monthlySales"
    private var selectedPeriod = "7d"
    private var selectedMonth = getCurrentMonthName().lowercase()
    
    private val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
    private val monthDateFormat = SimpleDateFormat("dd", Locale.getDefault())

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatisticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        customLoader = CustomLoader(requireActivity())
        
        setupTabListeners()
        setupPeriodSpinner()
        setupMonthSpinner()
        observeMovementsData()
        
        selectTab(selectedTab)
        selectPeriod("7d")
        selectMonth(getCurrentMonthName().lowercase())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Sets up tab click listeners for navigation between Balance and Monthly Sales.
     */
    private fun setupTabListeners() {
        binding.balanceTab.setOnClickListener { selectTab("balance") }
        binding.monthlySalesTab.setOnClickListener { selectTab("monthlySales") }
    }

    /**
     * Sets up period selection spinner for balance chart.
     */
    private fun setupPeriodSpinner() {
        val periods = listOf("7 días", "2 semanas", "1 mes", "3 meses")
        val periodValues = listOf("7d", "2w", "1m", "3m")
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, periods)
        binding.periodSpinner.setAdapter(adapter)
        
        binding.periodSpinner.setOnItemClickListener { _, _, position, _ ->
            selectPeriod(periodValues[position])
        }
        
        binding.periodSpinner.setText("7 días", false)
    }

    /**
     * Sets up month selection spinner for monthly sales chart.
     */
    private fun setupMonthSpinner() {
        val months = listOf("Septiembre", "Octubre", "Noviembre", "Diciembre")
        val monthValues = listOf("septiembre", "octubre", "noviembre", "diciembre")
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, months)
        binding.monthSpinner.setAdapter(adapter)
        
        binding.monthSpinner.setOnItemClickListener { _, _, position, _ ->
            selectMonth(monthValues[position])
        }
        
        binding.monthSpinner.setText(getCurrentMonthName(), false)
    }


    /**
     * Observes movements data changes and updates charts accordingly.
     */
    private fun observeMovementsData() {
        repository.movementsLiveData.observe(viewLifecycleOwner, Observer { movements ->
            updateCharts(movements)
        })
    }

    /**
     * Handles tab selection and updates UI accordingly.
     */
    private fun selectTab(tab: String) {
        selectedTab = tab
        
        when (tab) {
            "balance" -> {
                binding.balanceTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.balanceTab.setTextColor(Color.WHITE)
                binding.monthlySalesTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.monthlySalesTab.setTextColor(ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_secondary))
                
                binding.balanceTabContent.visibility = View.VISIBLE
                binding.monthlySalesTabContent.visibility = View.GONE
            }
            "monthlySales" -> {
                binding.monthlySalesTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.monthlySalesTab.setTextColor(Color.WHITE)
                binding.balanceTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.balanceTab.setTextColor(ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_secondary))
                
                binding.balanceTabContent.visibility = View.GONE
                binding.monthlySalesTabContent.visibility = View.VISIBLE
            }
        }
        
        updateCharts(repository.movementsLiveData.value ?: emptyList())
    }

    /**
     * Handles period selection for balance chart.
     */
    private fun selectPeriod(period: String) {
        selectedPeriod = period
        
        if (selectedTab == "balance") {
            updateCharts(repository.movementsLiveData.value ?: emptyList())
        }
    }

    /**
     * Handles month selection for monthly sales chart.
     */
    private fun selectMonth(month: String) {
        selectedMonth = month
        
        if (selectedTab == "monthlySales") {
            updateCharts(repository.movementsLiveData.value ?: emptyList())
        }
    }


    /**
     * Updates charts based on current tab selection and movements data.
     */
    private fun updateCharts(movements: List<Movement>) {
        when (selectedTab) {
            "balance" -> updateBalanceChart(movements)
            "monthlySales" -> {
                updateMonthlySalesChart(movements)
                updateRecipeSalesChart(movements)
            }
        }
    }

    /**
     * Updates the balance chart with sales and purchases data for the selected period.
     */
    private fun updateBalanceChart(movements: List<Movement>) {
        customLoader.show()
        
        val _endDate = Date()
        val startDate = getStartDateForPeriod(selectedPeriod, _endDate)
        
        val filteredMovements = movements.filter { movement ->
            movement.movementDate >= startDate && movement.movementDate <= _endDate
        }
        
        val salesData = filteredMovements.filter { it.type == EMovementType.SALE }
        val purchaseData = filteredMovements.filter { it.type == EMovementType.PURCHASE }
        
        if (salesData.isEmpty() && purchaseData.isEmpty()) {
            showEmptyMessage(binding.balanceChart, binding.balanceEmptyMessage)
            updateBalanceTotal(emptyList(), emptyList())
            customLoader.hide()
            return
        }
        
        hideEmptyMessage(binding.balanceChart, binding.balanceEmptyMessage)
        
        val chartData = createBalanceChartData(salesData, purchaseData, startDate, _endDate)
        setupBalanceChart(binding.balanceChart, chartData, salesData, purchaseData, startDate, _endDate)
        updateBalanceTotal(salesData, purchaseData)
        
        customLoader.hide()
    }

    /**
     * Updates the monthly sales chart with daily sales data for the selected month.
     */
    private fun updateMonthlySalesChart(movements: List<Movement>) {
        customLoader.show()
        
        val (startOfMonth, _endOfMonth) = getMonthDateRange(selectedMonth)
        
        val monthlySales = movements.filter { movement ->
            movement.type == EMovementType.SALE && 
            movement.movementDate >= startOfMonth && 
            movement.movementDate <= _endOfMonth
        }
        
        if (monthlySales.isEmpty()) {
            showEmptyMessage(binding.monthlySalesChart, binding.monthlySalesEmptyMessage)
            customLoader.hide()
            return
        }
        
        hideEmptyMessage(binding.monthlySalesChart, binding.monthlySalesEmptyMessage)
        
        val chartData = createMonthlySalesChartData(monthlySales, startOfMonth, _endOfMonth)
        setupMonthlySalesChart(binding.monthlySalesChart, chartData, monthlySales, startOfMonth, _endOfMonth)
        
        updateMonthlyBalance(movements, startOfMonth, _endOfMonth)
        
        customLoader.hide()
    }

    /**
     * Creates chart data for balance chart with sales and purchases grouped by period.
     */
    private fun createBalanceChartData(
        sales: List<Movement>, 
        purchases: List<Movement>, 
        startDate: Date, 
        _endDate: Date
    ): BarData {
        val periodGroups = groupMovementsByPeriod(sales, purchases, startDate, _endDate)
        
        val salesEntries = mutableListOf<BarEntry>()
        val purchaseEntries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        
        periodGroups.forEachIndexed { index, (period, data) ->
            salesEntries.add(BarEntry(index.toFloat(), data.first.toFloat()))
            purchaseEntries.add(BarEntry(index.toFloat(), data.second.toFloat()))
            labels.add(period)
        }
        
        val salesDataSet = BarDataSet(salesEntries, "Ventas").apply {
            color = Color.parseColor("#4CAF50")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = HideZeroValueFormatter()
        }
        
        val purchaseDataSet = BarDataSet(purchaseEntries, "Compras").apply {
            color = Color.parseColor("#F44336")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = HideZeroValueFormatter()
        }
        
        return BarData(salesDataSet, purchaseDataSet).apply {
            barWidth = 0.3f
            groupBars(0f, 0.1f, 0.1f)
        }
    }

    /**
     * Creates chart data for monthly sales chart with cumulative daily sales count.
     */
    private fun createMonthlySalesChartData(
        sales: List<Movement>, 
        startOfMonth: Date, 
        _endOfMonth: Date
    ): LineData {
        val dailySales = groupSalesByDay(sales, startOfMonth, _endOfMonth)
        
        val entries = mutableListOf<Entry>()
        val labels = mutableListOf<String>()
        var cumulativeCount = 0.0
        
        dailySales.forEachIndexed { index, (day, count) ->
            cumulativeCount += count
            entries.add(Entry(index.toFloat(), cumulativeCount.toFloat()))
            labels.add(day.toString())
        }
        
        val dataSet = LineDataSet(entries, "Ventas").apply {
            color = Color.parseColor("#4CAF50")
            setCircleColor(Color.parseColor("#4CAF50"))
            lineWidth = 3f
            circleRadius = 4f
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawFilled(true)
            fillColor = Color.parseColor("#4CAF50")
            fillAlpha = 50
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                private var lastValue = -1f
                
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) {
                        ""
                    } else if (value == lastValue) {
                        "" // Don't show repeated values
                    } else {
                        lastValue = value
                        value.toInt().toString()
                    }
                }
            }
        }
        
        return LineData(dataSet)
    }

    /**
     * Groups movements by period based on selected timeframe.
     */
    private fun groupMovementsByPeriod(
        sales: List<Movement>, 
        purchases: List<Movement>, 
        startDate: Date, 
        @Suppress("UNUSED_PARAMETER") unusedEndDate: Date
    ): List<Pair<String, Pair<Double, Double>>> {
        val calendar = Calendar.getInstance()
        val groups = mutableMapOf<String, Pair<Double, Double>>()
        
        when (selectedPeriod) {
            "7d" -> {
                for (i in 0..6) {
                    calendar.time = startDate
                    calendar.add(Calendar.DAY_OF_MONTH, i)
                    val dayKey = dateFormat.format(calendar.time)
                    groups[dayKey] = Pair(0.0, 0.0)
                }
            }
            "2w" -> {
                for (i in 0..13) {
                    calendar.time = startDate
                    calendar.add(Calendar.DAY_OF_MONTH, i)
                    val dayKey = dateFormat.format(calendar.time)
                    groups[dayKey] = Pair(0.0, 0.0)
                }
            }
            "1m" -> {
                for (i in 0..29) {
                    calendar.time = startDate
                    calendar.add(Calendar.DAY_OF_MONTH, i)
                    val dayKey = dateFormat.format(calendar.time)
                    groups[dayKey] = Pair(0.0, 0.0)
                }
            }
            "3m" -> {
                for (i in 0..12) {
                    calendar.time = startDate
                    calendar.add(Calendar.WEEK_OF_YEAR, i)
                    val weekKey = "Sem ${i + 1}"
                    groups[weekKey] = Pair(0.0, 0.0)
                }
            }
        }
        
        sales.forEach { movement ->
            val key = getPeriodKey(movement.movementDate, startDate)
            groups[key]?.let { current ->
                groups[key] = Pair(current.first + movement.totalAmount, current.second)
            }
        }
        
        purchases.forEach { movement ->
            val key = getPeriodKey(movement.movementDate, startDate)
            groups[key]?.let { current ->
                groups[key] = Pair(current.first, current.second + movement.totalAmount)
            }
        }
        
        return groups.toList().sortedBy { it.first }
    }

    /**
     * Groups sales by day for the current month (count of sales, not amount).
     */
    private fun groupSalesByDay(sales: List<Movement>, startOfMonth: Date, @Suppress("UNUSED_PARAMETER") unusedEndOfMonth: Date): List<Pair<Int, Double>> {
        val dailyCounts = mutableMapOf<Int, Double>()
        
        val calendar = Calendar.getInstance()
        calendar.time = startOfMonth
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        for (day in 1..daysInMonth) {
            dailyCounts[day] = 0.0
        }
        
        sales.forEach { movement ->
            calendar.time = movement.movementDate
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            dailyCounts[day] = dailyCounts[day]!! + 1.0 // Count sales, not amount
        }
        
        return dailyCounts.toList().sortedBy { it.first }
    }

    /**
     * Gets the period key for a movement date based on selected period.
     */
    private fun getPeriodKey(movementDate: Date, startDate: Date): String {
        val calendar = Calendar.getInstance()
        
        return when (selectedPeriod) {
            "7d", "2w", "1m" -> {
                dateFormat.format(movementDate)
            }
            "3m" -> {
                calendar.time = startDate
                val startWeek = calendar.get(Calendar.WEEK_OF_YEAR)
                calendar.time = movementDate
                val movementWeek = calendar.get(Calendar.WEEK_OF_YEAR)
                val weekDiff = movementWeek - startWeek + 1
                "Sem $weekDiff"
            }
            else -> dateFormat.format(movementDate)
        }
    }

    /**
     * Gets the start date for the selected period.
     */
    private fun getStartDateForPeriod(period: String, _endDate: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = _endDate
        
        return when (period) {
            "7d" -> {
                calendar.add(Calendar.DAY_OF_MONTH, -6)
                calendar.time
            }
            "2w" -> {
                calendar.add(Calendar.DAY_OF_MONTH, -13)
                calendar.time
            }
            "1m" -> {
                calendar.add(Calendar.DAY_OF_MONTH, -29)
                calendar.time
            }
            "3m" -> {
                calendar.add(Calendar.MONTH, -3)
                calendar.time
            }
            else -> _endDate
        }
    }

    /**
     * Gets the date range for the selected month.
     */
    private fun getMonthDateRange(month: String): Pair<Date, Date> {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        
        val monthNumber = when (month) {
            "septiembre" -> Calendar.SEPTEMBER
            "octubre" -> Calendar.OCTOBER
            "noviembre" -> Calendar.NOVEMBER
            "diciembre" -> Calendar.DECEMBER
            else -> Calendar.DECEMBER
        }
        
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, monthNumber)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val _endOfMonth = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, monthNumber)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time
        
        return Pair(startOfMonth, _endOfMonth)
    }

    /**
     * Gets the current month name in Spanish.
     */
    private fun getCurrentMonthName(): String {
        val calendar = Calendar.getInstance()
        return when (calendar.get(Calendar.MONTH)) {
            Calendar.SEPTEMBER -> "Septiembre"
            Calendar.OCTOBER -> "Octubre"
            Calendar.NOVEMBER -> "Noviembre"
            Calendar.DECEMBER -> "Diciembre"
            else -> "Diciembre" // Default to december if current month is not in range
        }
    }

    /**
     * Updates the recipe sales pie chart with data for the selected month.
     */
    private fun updateRecipeSalesChart(movements: List<Movement>) {
        customLoader.show()
        
        val (startOfMonth, _endOfMonth) = getMonthDateRange(selectedMonth)
        
        val sales = movements.filter { movement ->
            movement.type == EMovementType.SALE &&
            movement.movementDate >= startOfMonth &&
            movement.movementDate <= _endOfMonth
        }
        
        if (sales.isEmpty()) {
            binding.recipeSalesChart.visibility = View.GONE
            binding.recipeSalesEmptyMessage.visibility = View.VISIBLE
            updateMonthlyBalance(movements, startOfMonth, _endOfMonth)
            customLoader.hide()
            return
        }
        
        binding.recipeSalesChart.visibility = View.VISIBLE
        binding.recipeSalesEmptyMessage.visibility = View.GONE
        
        val recipeData = createRecipeSalesChartData(sales)
        setupRecipeSalesChart(binding.recipeSalesChart, recipeData)
        
        updateMonthlyBalance(movements, startOfMonth, _endOfMonth)
        
        customLoader.hide()
    }

    /**
     * Creates pie chart data for recipe sales distribution.
     * Counts recipe presence in movements (1 per movement, regardless of quantity).
     */
    private fun createRecipeSalesChartData(sales: List<Movement>): PieData {
        val recipeCounts = mutableMapOf<String, Int>()
        val recipes = repository.recipesLiveData.value ?: emptyList()
        
        sales.forEach { movement ->
            val recipesInMovement = mutableSetOf<String>()
            
            movement.items.forEach { item ->
                if (item.collection == "recipes") {
                    val recipe = recipes.find { it.id == item.collectionId }
                    val recipeName = recipe?.name ?: "Receta desconocida"
                    recipesInMovement.add(recipeName)
                }
            }
            
            recipesInMovement.forEach { recipeName ->
                recipeCounts[recipeName] = (recipeCounts[recipeName] ?: 0) + 1
            }
        }
        
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()
        
        recipeCounts.forEach { (recipeName, count) ->
            entries.add(PieEntry(count.toFloat(), recipeName))
            colors.add(getRecipeColor(recipeName))
        }
        
        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            valueFormatter = HideZeroValueFormatter()
        }
        
        return PieData(dataSet)
    }

    /**
     * Gets color for recipe name.
     */
    private fun getRecipeColor(recipeName: String): Int {
        val colors = listOf(
            Color.parseColor("#4CAF50"), // Green
            Color.parseColor("#FF9800"), // Orange
            Color.parseColor("#2196F3"), // Blue
            Color.parseColor("#9C27B0"), // Purple
            Color.parseColor("#795548"), // Brown
            Color.parseColor("#607D8B"), // Blue Grey
            Color.parseColor("#E91E63"), // Pink
            Color.parseColor("#00BCD4"), // Cyan
            Color.parseColor("#8BC34A"), // Light Green
            Color.parseColor("#FF5722")  // Deep Orange
        )
        return colors[recipeName.hashCode().mod(colors.size)]
    }

    /**
     * Sets up the recipe sales pie chart with styling and configuration.
     */
    private fun setupRecipeSalesChart(chart: PieChart, data: PieData) {
        chart.data = data
        chart.setUsePercentValues(false) // Show actual values instead of percentages
        chart.description.isEnabled = false
        chart.setExtraOffsets(5f, 10f, 5f, 5f)
        chart.dragDecelerationFrictionCoef = 0.95f
        chart.isRotationEnabled = true
        chart.setDrawEntryLabels(false) // Remove labels on chart segments
        chart.setEntryLabelTextSize(12f)
        chart.setEntryLabelColor(Color.BLACK)
        
        val legend = chart.legend
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.xEntrySpace = 7f
        legend.yEntrySpace = 5f // Add vertical spacing between rows
        legend.yOffset = 0f
        legend.textSize = 12f
        legend.textColor = ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_primary)
        
        legend.setWordWrapEnabled(true)
        legend.setMaxSizePercent(0.95f) // Use 95% of available width
        
        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e != null && h != null) {
                    chart.setDrawEntryLabels(true)
                    chart.setEntryLabelTextSize(14f)
                    chart.setEntryLabelColor(Color.BLACK)
                    chart.setEntryLabelTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                    chart.invalidate()
                }
            }
            
            override fun onNothingSelected() {
                chart.setDrawEntryLabels(false)
                chart.invalidate()
            }
        })
        
        chart.animateY(1400)
        chart.invalidate()
    }

    /**
     * Updates the balance total display with calculated balance for the period.
     */
    private fun updateBalanceTotal(sales: List<Movement>, purchases: List<Movement>) {
        val totalSales = sales.sumOf { it.totalAmount }
        val totalPurchases = purchases.sumOf { it.totalAmount }
        val balance = totalSales - totalPurchases
        
        val formattedBalance = String.format("$%.2f", balance)
        binding.balanceTotalText.text = formattedBalance
        
        val color = if (balance >= 0) {
            ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_primary)
        } else {
            Color.parseColor("#F44336") // Red for negative balance
        }
        binding.balanceTotalText.setTextColor(color)
    }

    /**
     * Updates the monthly balance total display with calculated balance for the selected month.
     */
    private fun updateMonthlyBalance(movements: List<Movement>, startOfMonth: Date, _endOfMonth: Date) {
        val monthlyMovements = movements.filter { movement ->
            movement.movementDate >= startOfMonth && movement.movementDate <= _endOfMonth
        }
        
        val sales = monthlyMovements.filter { it.type == EMovementType.SALE }
        val purchases = monthlyMovements.filter { it.type == EMovementType.PURCHASE }
        
        val totalSales = sales.sumOf { it.totalAmount }
        val totalPurchases = purchases.sumOf { it.totalAmount }
        val balance = totalSales - totalPurchases
        val formattedBalance = String.format("$%.2f", balance)
        
        binding.monthlyBalanceTotalText.text = "Balance mensual: $formattedBalance"
        val color = if (balance >= 0) {
            ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_primary)
        } else {
            Color.parseColor("#F44336") // Red for negative balance
        }
        binding.monthlyBalanceTotalText.setTextColor(color)
    }

    /**
     * Sets up the balance chart with data and styling.
     */
    private fun setupBalanceChart(
        chart: BarChart, 
        data: BarData, 
        sales: List<Movement>, 
        purchases: List<Movement>, 
        startDate: Date, 
        _endDate: Date
    ) {
        chart.data = data
        chart.description.isEnabled = false
        chart.setFitBars(true)
        chart.animateY(1000)
        
        val periodGroups = groupMovementsByPeriod(sales, purchases, startDate, _endDate)
        val labels = periodGroups.map { it.first }
        
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.textColor = Color.BLACK
        xAxis.textSize = 10f
        xAxis.valueFormatter = BalanceAxisLabelFormatter(labels)
        
        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true
        chart.legend.textColor = Color.BLACK
        chart.legend.textSize = 12f
        
        chart.invalidate()
    }

    /**
     * Sets up the monthly sales chart with data and styling.
     */
    private fun setupMonthlySalesChart(
        chart: LineChart, 
        data: LineData, 
        sales: List<Movement>, 
        startOfMonth: Date, 
        _endOfMonth: Date
    ) {
        chart.data = data
        chart.description.isEnabled = false
        chart.animateY(1000)
        
        val dailySales = groupSalesByDay(sales, startOfMonth, _endOfMonth)
        val labels = dailySales.map { it.first.toString() }
        
        val totalSalesCount = sales.size
        val maxAxisValue = if (totalSalesCount > 0) {
            (totalSalesCount * 1.2).toInt() + 1
        } else {
            10 // Default maximum if no sales
        }
        
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.textColor = Color.BLACK
        xAxis.textSize = 10f
        xAxis.valueFormatter = BalanceAxisLabelFormatter(labels)
        
        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.granularity = 1f
        leftAxis.isGranularityEnabled = true
        leftAxis.setAxisMinimum(0f) // Force minimum to 0
        leftAxis.setAxisMaximum(maxAxisValue.toFloat()) // Dynamic maximum based on total sales
        leftAxis.setLabelCount(maxAxisValue + 1, true) // Show all integer values from 0 to maxAxisValue
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString() // Force integer values
            }
        }
        
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = true
        chart.legend.textColor = Color.BLACK
        chart.legend.textSize = 12f
        
        chart.invalidate()
    }

    /**
     * Shows empty message and hides chart.
     */
    private fun showEmptyMessage(chart: View, emptyMessage: View) {
        chart.visibility = View.GONE
        emptyMessage.visibility = View.VISIBLE
    }

    /**
     * Hides empty message and shows chart.
     */
    private fun hideEmptyMessage(chart: View, emptyMessage: View) {
        chart.visibility = View.VISIBLE
        emptyMessage.visibility = View.GONE
    }
}

