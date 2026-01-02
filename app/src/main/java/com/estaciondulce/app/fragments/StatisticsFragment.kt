package com.estaciondulce.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.estaciondulce.app.R
import com.estaciondulce.app.databinding.FragmentStatisticsBinding
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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
    private var selectedMonth = getCurrentMonthName().lowercase()
    private var selectedYear = Calendar.getInstance().get(Calendar.YEAR)
    
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
        setupMonthSpinner()
        observeMovementsData()
        
        selectTab(selectedTab)
        selectYear(selectedYear)
        selectMonth(getCurrentMonthName().lowercase())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Sets up tab click listeners for navigation between Balance, Monthly Sales, and Annual.
     */
    private fun setupTabListeners() {
        binding.balanceTab.setOnClickListener { selectTab("balance") }
        binding.monthlySalesTab.setOnClickListener { selectTab("monthlySales") }
        binding.annualTab.setOnClickListener { selectTab("annual") }
    }

    /**
     * Sets up month and year selection spinners for all tabs (shared selection).
     */
    private fun setupMonthSpinner() {
        val months = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        val monthValues = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
        val years = listOf("2025", "2026")
        val yearValues = listOf(2025, 2026)
        
        val monthAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, months)
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, years)
        
        binding.monthSpinner.setAdapter(monthAdapter)
        binding.monthSpinner.setOnItemClickListener { _, _, position, _ ->
            selectMonth(monthValues[position])
        }
        binding.monthSpinner.setText(getCurrentMonthName(), false)
        
        binding.balanceMonthSpinner.setAdapter(monthAdapter)
        binding.balanceMonthSpinner.setOnItemClickListener { _, _, position, _ ->
            selectMonth(monthValues[position])
        }
        binding.balanceMonthSpinner.setText(getCurrentMonthName(), false)
        
        binding.yearSpinner.setAdapter(yearAdapter)
        binding.yearSpinner.setOnItemClickListener { _, _, position, _ ->
            selectYear(yearValues[position])
        }
        binding.yearSpinner.setText(selectedYear.toString(), false)
        
        binding.balanceYearSpinner.setAdapter(yearAdapter)
        binding.balanceYearSpinner.setOnItemClickListener { _, _, position, _ ->
            selectYear(yearValues[position])
        }
        binding.balanceYearSpinner.setText(selectedYear.toString(), false)
        
        binding.annualYearSpinner.setAdapter(yearAdapter)
        binding.annualYearSpinner.setOnItemClickListener { _, _, position, _ ->
            selectYear(yearValues[position])
        }
        binding.annualYearSpinner.setText(selectedYear.toString(), false)
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
                binding.annualTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.annualTab.setTextColor(ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_secondary))
                
                binding.balanceTabContent.visibility = View.VISIBLE
                binding.monthlySalesTabContent.visibility = View.GONE
                binding.annualTabContent.visibility = View.GONE
            }
            "monthlySales" -> {
                binding.monthlySalesTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.monthlySalesTab.setTextColor(Color.WHITE)
                binding.balanceTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.balanceTab.setTextColor(ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_secondary))
                binding.annualTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.annualTab.setTextColor(ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_secondary))
                
                binding.balanceTabContent.visibility = View.GONE
                binding.monthlySalesTabContent.visibility = View.VISIBLE
                binding.annualTabContent.visibility = View.GONE
            }
            "annual" -> {
                binding.annualTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.annualTab.setTextColor(Color.WHITE)
                binding.monthlySalesTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.monthlySalesTab.setTextColor(ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_secondary))
                binding.balanceTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.balanceTab.setTextColor(ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_secondary))
                
                binding.balanceTabContent.visibility = View.GONE
                binding.monthlySalesTabContent.visibility = View.GONE
                binding.annualTabContent.visibility = View.VISIBLE
            }
        }
        
        updateCharts(repository.movementsLiveData.value ?: emptyList())
    }

    /**
     * Handles month selection for both tabs (shared month selection).
     */
    private fun selectMonth(month: String) {
        selectedMonth = month
        
        val capitalizedMonth = month.replaceFirstChar { it.uppercaseChar() }
        binding.monthSpinner.setText(capitalizedMonth, false)
        binding.balanceMonthSpinner.setText(capitalizedMonth, false)
        
        updateCharts(repository.movementsLiveData.value ?: emptyList())
    }

    /**
     * Handles year selection for all tabs (shared year selection).
     */
    private fun selectYear(year: Int) {
        selectedYear = year
        
        binding.yearSpinner.setText(year.toString(), false)
        binding.balanceYearSpinner.setText(year.toString(), false)
        binding.annualYearSpinner.setText(year.toString(), false)
        
        updateCharts(repository.movementsLiveData.value ?: emptyList())
    }


    /**
     * Updates charts based on current tab selection and movements data.
     */
    private fun updateCharts(movements: List<Movement>) {
        when (selectedTab) {
            "balance" -> {
                updateBalanceChart(movements)
                updateBalanceMonthlyBalance(movements)
                updateShipmentCostsChart(movements)
            }
            "monthlySales" -> {
                updateMonthlySalesChart(movements)
                updateRecipeSalesChart(movements)
                updateClientSalesChart(movements)
            }
            "annual" -> {
                updateAnnualSalesByMonthChart(movements)
                updateAnnualClientSalesChart(movements)
                updateAnnualShipmentProfitChart(movements)
            }
        }
    }

    /**
     * Updates the balance chart with PURCHASE movements grouped by provider for the selected month.
     */
    private fun updateBalanceChart(movements: List<Movement>) {
        customLoader.show()
        
        val (startOfMonth, _endOfMonth) = getMonthDateRange(selectedMonth)
        
        val purchases = movements.filter { movement ->
            movement.type == EMovementType.PURCHASE &&
            movement.movementDate >= startOfMonth &&
            movement.movementDate <= _endOfMonth
        }
        
        if (purchases.isEmpty()) {
            showEmptyMessage(binding.balanceChart, binding.balanceEmptyMessage)
            showEmptyMessage(binding.balanceProductsChart, binding.balanceProductsEmptyMessage)
            customLoader.hide()
            return
        }
        
        hideEmptyMessage(binding.balanceChart, binding.balanceEmptyMessage)
        
        val (chartData, labels) = createProviderSalesChartData(purchases)
        setupProviderSalesChart(binding.balanceChart, chartData, labels)
        
        updateProductPurchasesChart(purchases)
        
        customLoader.hide()
    }

    private fun updateMonthlySalesTotal(sales: List<Movement>) {
        val totalSales = sales.size
        android.util.Log.d("StatisticsFragment", "updateMonthlySalesTotal: $totalSales")
        
        if (binding.monthlySalesTabContent.childCount > 0) {
            val materialCardView = binding.monthlySalesTabContent.getChildAt(0) as android.view.ViewGroup
            android.util.Log.d("StatisticsFragment", "MaterialCardView children count: ${materialCardView.childCount}")
            findAndUpdateTitleText(materialCardView, totalSales)
        }
    }

    private fun findAndUpdateTitleText(parent: android.view.ViewGroup, totalSales: Int) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is android.widget.TextView) {
                val currentText = child.text.toString()
                android.util.Log.d("StatisticsFragment", "Found TextView with text: '$currentText'")
                if (currentText.contains("Ventas acumuladas")) {
                    child.text = "Ventas acumuladas: $totalSales"
                    android.util.Log.d("StatisticsFragment", "Updating TextView '${child.javaClass.simpleName}' with total: $totalSales")
                    return
                }
            } else if (child is android.view.ViewGroup) {
                findAndUpdateTitleText(child, totalSales)
            }
        }
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
        
        updateMonthlySalesTotal(monthlySales)
        updateMonthlyBalance(movements, startOfMonth, _endOfMonth)
        
        customLoader.hide()
    }

    /**
     * Creates chart data for monthly sales chart with cumulative daily sales count.
     */
    private fun createMonthlySalesChartData(
        sales: List<Movement>, 
        startOfMonth: Date, 
        _endOfMonth: Date
    ): LineData {
        val (dailyPedidos, dailyStock) = groupSalesByDayAndType(sales, startOfMonth, _endOfMonth)
        
        val pedidosEntries = mutableListOf<Entry>()
        val stockEntries = mutableListOf<Entry>()
        var cumulativePedidos = 0.0
        var cumulativeStock = 0.0
        
        dailyPedidos.forEachIndexed { index, (_, count) ->
            cumulativePedidos += count
            pedidosEntries.add(Entry(index.toFloat(), cumulativePedidos.toFloat()))
        }
        
        dailyStock.forEachIndexed { index, (_, count) ->
            cumulativeStock += count
            stockEntries.add(Entry(index.toFloat(), cumulativeStock.toFloat()))
        }
        
        val pedidosDataSet = LineDataSet(pedidosEntries, "Pedidos").apply {
            color = androidx.core.content.ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.button_gradient_end)
            setCircleColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.button_gradient_end))
            lineWidth = 3f
            circleRadius = 4f
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawFilled(false)
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                private var lastValue = -1f
                
                override fun getFormattedValue(value: Float): String {
                    return if (value == 0f) {
                        ""
                    } else if (value == lastValue) {
                        ""
                    } else {
                        lastValue = value
                        value.toInt().toString()
                    }
                }
            }
        }
        
        val stockDataSet = LineDataSet(stockEntries, "Stock").apply {
            color = androidx.core.content.ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.button_gradient_start)
            setCircleColor(androidx.core.content.ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.button_gradient_start))
            lineWidth = 3f
            circleRadius = 4f
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawFilled(false)
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
        
        return LineData(pedidosDataSet, stockDataSet)
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

    private fun groupSalesByDayAndType(
        sales: List<Movement>, 
        startOfMonth: Date, 
        @Suppress("UNUSED_PARAMETER") unusedEndOfMonth: Date
    ): Pair<List<Pair<Int, Double>>, List<Pair<Int, Double>>> {
        val dailyPedidosCounts = mutableMapOf<Int, Double>()
        val dailyStockCounts = mutableMapOf<Int, Double>()
        
        val calendar = Calendar.getInstance()
        calendar.time = startOfMonth
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        
        for (day in 1..daysInMonth) {
            dailyPedidosCounts[day] = 0.0
            dailyStockCounts[day] = 0.0
        }
        
        sales.forEach { movement ->
            calendar.time = movement.movementDate
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            if (movement.isStock) {
                dailyStockCounts[day] = dailyStockCounts[day]!! + 1.0
            } else {
                dailyPedidosCounts[day] = dailyPedidosCounts[day]!! + 1.0
            }
        }
        
        val pedidosList = dailyPedidosCounts.toSortedMap().map { it.key to it.value }
        val stockList = dailyStockCounts.toSortedMap().map { it.key to it.value }
        
        return Pair(pedidosList, stockList)
    }

    /**
     * Gets the date range for the selected month and year.
     */
    private fun getMonthDateRange(month: String, year: Int = selectedYear): Pair<Date, Date> {
        val monthNumber = when (month) {
            "enero" -> Calendar.JANUARY
            "febrero" -> Calendar.FEBRUARY
            "marzo" -> Calendar.MARCH
            "abril" -> Calendar.APRIL
            "mayo" -> Calendar.MAY
            "junio" -> Calendar.JUNE
            "julio" -> Calendar.JULY
            "agosto" -> Calendar.AUGUST
            "septiembre" -> Calendar.SEPTEMBER
            "octubre" -> Calendar.OCTOBER
            "noviembre" -> Calendar.NOVEMBER
            "diciembre" -> Calendar.DECEMBER
            else -> Calendar.getInstance().get(Calendar.MONTH)
        }
        
        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthNumber)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        
        val _endOfMonth = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
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
            Calendar.JANUARY -> "Enero"
            Calendar.FEBRUARY -> "Febrero"
            Calendar.MARCH -> "Marzo"
            Calendar.APRIL -> "Abril"
            Calendar.MAY -> "Mayo"
            Calendar.JUNE -> "Junio"
            Calendar.JULY -> "Julio"
            Calendar.AUGUST -> "Agosto"
            Calendar.SEPTEMBER -> "Septiembre"
            Calendar.OCTOBER -> "Octubre"
            Calendar.NOVEMBER -> "Noviembre"
            Calendar.DECEMBER -> "Diciembre"
            else -> "Enero"
        }
    }

    /**
     * Updates the recipe sales bar chart with data for the selected month.
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
        
        val (recipeData, labels) = createRecipeSalesChartData(sales)
        setupRecipeSalesChart(binding.recipeSalesChart, recipeData, labels)
        
        updateMonthlyBalance(movements, startOfMonth, _endOfMonth)
        
        customLoader.hide()
    }

    /**
     * Creates bar chart data for recipe sales distribution.
     * Counts recipe presence in movements (1 per movement, regardless of quantity).
     * Returns a Pair of BarData and labels list.
     */
    private fun createRecipeSalesChartData(sales: List<Movement>): Pair<BarData, List<String>> {
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
        
        val sortedRecipes = recipeCounts.toList().sortedByDescending { it.second }.take(10)
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        
        sortedRecipes.forEachIndexed { index, (recipeName, count) ->
            entries.add(BarEntry(index.toFloat(), count.toFloat()))
            labels.add(recipeName)
        }
        
        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#4CAF50")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = HideZeroValueFormatter()
        }
        
        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }
        
        return Pair(barData, labels)
    }

    /**
     * Creates bar chart data for provider movements distribution.
     * Groups movements by provider (personId).
     */
    private fun createProviderSalesChartData(movements: List<Movement>): Pair<BarData, List<String>> {
        val providerCounts = mutableMapOf<String, Double>()
        val persons = repository.personsLiveData.value ?: emptyList()
        
        movements.forEach { movement ->
            val person = persons.find { it.id == movement.personId }
            val providerName = if (person != null) {
                "${person.name} ${person.lastName}".trim()
            } else {
                "Proveedor desconocido"
            }
            providerCounts[providerName] = (providerCounts[providerName] ?: 0.0) + movement.totalAmount
        }
        
        val sortedProviders = providerCounts.toList().sortedByDescending { it.second }
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        
        sortedProviders.forEachIndexed { index, (providerName, amount) ->
            entries.add(BarEntry(index.toFloat(), amount.toFloat()))
            labels.add(providerName)
        }
        
        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#FF5722")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val symbols = DecimalFormatSymbols().apply {
                        groupingSeparator = '.'
                        decimalSeparator = ','
                    }
                    val decimalFormat = DecimalFormat("#,##0", symbols)
                    return decimalFormat.format(value.toDouble())
                }
            }
        }
        
        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }
        
        return Pair(barData, labels)
    }

    /**
     * Sets up the provider sales bar chart with styling and configuration.
     */
    private fun setupProviderSalesChart(chart: BarChart, data: BarData, labels: List<String>) {
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
        
        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val symbols = DecimalFormatSymbols().apply {
                    groupingSeparator = '.'
                    decimalSeparator = ','
                }
                val decimalFormat = DecimalFormat("#,##0", symbols)
                return decimalFormat.format(value.toInt().toDouble())
            }
        }
        
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        
        chart.invalidate()
    }

    /**
     * Updates the product purchases chart with data for the selected month.
     */
    private fun updateProductPurchasesChart(purchases: List<Movement>) {
        val productData = createProductPurchasesChartData(purchases)
        
        if (productData == null) {
            showEmptyMessage(binding.balanceProductsChart, binding.balanceProductsEmptyMessage)
            return
        }
        
        binding.balanceProductsChart.visibility = View.VISIBLE
        binding.balanceProductsEmptyMessage.visibility = View.GONE
        
        val (chartData, labels) = productData
        setupProductPurchasesChart(binding.balanceProductsChart, chartData, labels)
    }

    /**
     * Creates bar chart data for product purchases distribution.
     * Groups purchase movements by product and sums total cost (cost * quantity).
     */
    private fun createProductPurchasesChartData(purchases: List<Movement>): Pair<BarData, List<String>>? {
        val productCosts = mutableMapOf<String, Double>()
        val products = repository.productsLiveData.value ?: emptyList()
        
        purchases.forEach { movement ->
            movement.items.forEach { item ->
                if (item.collection == "products") {
                    val product = products.find { it.id == item.collectionId }
                    val productName = product?.name ?: item.customName ?: "Producto desconocido"
                    val totalCost = item.cost * item.quantity
                    productCosts[productName] = (productCosts[productName] ?: 0.0) + totalCost
                }
            }
        }
        
        if (productCosts.isEmpty()) {
            return null
        }
        
        val sortedProducts = productCosts.toList().sortedByDescending { it.second }.take(10)
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        
        sortedProducts.forEachIndexed { index, (productName, totalCost) ->
            entries.add(BarEntry(index.toFloat(), totalCost.toFloat()))
            labels.add(productName)
        }
        
        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#FF5722")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val symbols = DecimalFormatSymbols().apply {
                        groupingSeparator = '.'
                        decimalSeparator = ','
                    }
                    val decimalFormat = DecimalFormat("#,##0", symbols)
                    return decimalFormat.format(value.toDouble())
                }
            }
        }
        
        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }
        
        return Pair(barData, labels)
    }

    /**
     * Sets up the product purchases bar chart with styling and configuration.
     */
    private fun setupProductPurchasesChart(chart: BarChart, data: BarData, labels: List<String>) {
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
        
        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val symbols = DecimalFormatSymbols().apply {
                    groupingSeparator = '.'
                    decimalSeparator = ','
                }
                val decimalFormat = DecimalFormat("#,##0", symbols)
                return decimalFormat.format(value.toInt().toDouble())
            }
        }
        
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        
        chart.invalidate()
    }

    /**
     * Updates the client sales chart with data for the selected month.
     */
    private fun updateClientSalesChart(movements: List<Movement>) {
        val (startOfMonth, _endOfMonth) = getMonthDateRange(selectedMonth)
        
        val sales = movements.filter { movement ->
            movement.type == EMovementType.SALE &&
            movement.movementDate >= startOfMonth &&
            movement.movementDate <= _endOfMonth
        }
        
        val clientData = createClientSalesChartData(sales)
        
        if (clientData == null) {
            showEmptyMessage(binding.clientSalesChart, binding.clientSalesEmptyMessage)
            return
        }
        
        binding.clientSalesChart.visibility = View.VISIBLE
        binding.clientSalesEmptyMessage.visibility = View.GONE
        
        val (chartData, labels) = clientData
        setupClientSalesChart(binding.clientSalesChart, chartData, labels)
    }

    /**
     * Creates bar chart data for client sales distribution.
     * Groups SALE movements by client (personId) and shows top 10 clients by total spent.
     */
    private fun createClientSalesChartData(sales: List<Movement>): Pair<BarData, List<String>>? {
        val clientTotals = mutableMapOf<String, Double>()
        val persons = repository.personsLiveData.value ?: emptyList()
        
        sales.forEach { movement ->
            val person = persons.find { it.id == movement.personId }
            val clientName = if (person != null) {
                "${person.name} ${person.lastName}".trim()
            } else {
                "Cliente desconocido"
            }
            clientTotals[clientName] = (clientTotals[clientName] ?: 0.0) + movement.totalAmount
        }
        
        if (clientTotals.isEmpty()) {
            return null
        }
        
        val topClients = clientTotals.toList()
            .sortedByDescending { it.second }
            .take(10)
        
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        
        topClients.forEachIndexed { index, (clientName, totalSpent) ->
            entries.add(BarEntry(index.toFloat(), totalSpent.toFloat()))
            labels.add(clientName)
        }
        
        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#4CAF50")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val symbols = DecimalFormatSymbols().apply {
                        groupingSeparator = '.'
                        decimalSeparator = ','
                    }
                    val decimalFormat = DecimalFormat("#,##0", symbols)
                    return decimalFormat.format(value.toDouble())
                }
            }
        }
        
        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }
        
        return Pair(barData, labels)
    }

    /**
     * Sets up the client sales bar chart with styling and configuration.
     */
    private fun setupClientSalesChart(chart: BarChart, data: BarData, labels: List<String>) {
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
        
        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val symbols = DecimalFormatSymbols().apply {
                    groupingSeparator = '.'
                    decimalSeparator = ','
                }
                val decimalFormat = DecimalFormat("#,##0", symbols)
                return decimalFormat.format(value.toInt().toDouble())
            }
        }
        
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        
        chart.invalidate()
    }

    /**
     * Sets up the recipe sales bar chart with styling and configuration.
     */
    private fun setupRecipeSalesChart(chart: BarChart, data: BarData, labels: List<String>) {
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
        
        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.granularity = 1f
        leftAxis.isGranularityEnabled = true
        
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        
        chart.invalidate()
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
        
        val symbols = DecimalFormatSymbols().apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        val decimalFormat = DecimalFormat("#,##0.00", symbols)
        val formattedBalance = String.format("$%s", decimalFormat.format(balance))
        
        binding.monthlyBalanceTotalText.text = "Balance mensual: $formattedBalance"
        val color = if (balance >= 0) {
            ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_primary)
        } else {
            Color.parseColor("#F44336") // Red for negative balance
        }
        binding.monthlyBalanceTotalText.setTextColor(color)
    }

    /**
     * Updates the balance monthly balance total display with calculated balance for the selected month.
     */
    private fun updateBalanceMonthlyBalance(movements: List<Movement>) {
        val (startOfMonth, _endOfMonth) = getMonthDateRange(selectedMonth)
        val monthlyMovements = movements.filter { movement ->
            movement.movementDate >= startOfMonth && movement.movementDate <= _endOfMonth
        }
        
        val sales = monthlyMovements.filter { it.type == EMovementType.SALE }
        val purchases = monthlyMovements.filter { it.type == EMovementType.PURCHASE }
        
        val totalSales = sales.sumOf { it.totalAmount }
        val totalPurchases = purchases.sumOf { it.totalAmount }
        val balance = totalSales - totalPurchases
        
        val symbols = DecimalFormatSymbols().apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        val decimalFormat = DecimalFormat("#,##0.00", symbols)
        val formattedBalance = String.format("$%s", decimalFormat.format(balance))
        
        binding.balanceMonthlyBalanceTotalText.text = "Balance mensual: $formattedBalance"
        val color = if (balance >= 0) {
            ContextCompat.getColor(requireContext(), com.estaciondulce.app.R.color.text_primary)
        } else {
            Color.parseColor("#F44336") // Red for negative balance
        }
        binding.balanceMonthlyBalanceTotalText.setTextColor(color)
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

    /**
     * Updates the shipment costs chart comparing calculatedCost vs cost for the selected month for SALE movements with SHIPMENT delivery.
     */
    private fun updateShipmentCostsChart(movements: List<Movement>) {
        val (startOfMonth, _endOfMonth) = getMonthDateRange(selectedMonth)
        
        val shipmentSales = movements.filter { movement ->
            if (movement.type != EMovementType.SALE) return@filter false
            if (movement.movementDate < startOfMonth || movement.movementDate > _endOfMonth) return@filter false
            val delivery = movement.delivery
            if (delivery == null) return@filter false
            delivery.type == com.estaciondulce.app.models.enums.EDeliveryType.SHIPMENT.name &&
            delivery.shipment != null
        }

        if (shipmentSales.isEmpty()) {
            showEmptyMessage(binding.shipmentCostsChart, binding.shipmentCostsEmptyMessage)
            binding.shipmentCostsTitle.text = "Ganancias de envíos: $0"
            return
        }

        hideEmptyMessage(binding.shipmentCostsChart, binding.shipmentCostsEmptyMessage)

        var totalCalculatedCost = 0.0
        var totalCost = 0.0

        shipmentSales.forEach { movement ->
            val shipment = movement.delivery?.shipment ?: return@forEach
            totalCalculatedCost += shipment.calculatedCost
            totalCost += shipment.cost
        }

        val profit = totalCost - totalCalculatedCost
        val symbols = DecimalFormatSymbols().apply {
            groupingSeparator = '.'
            decimalSeparator = ','
        }
        val decimalFormat = DecimalFormat("#,##0", symbols)
        val formattedProfit = decimalFormat.format(profit.toInt().toDouble())
        binding.shipmentCostsTitle.text = "Ganancias de envíos: $$formattedProfit"

        val chartData = createShipmentCostsChartData(shipmentSales)
        setupShipmentCostsChart(binding.shipmentCostsChart, chartData)
    }

    /**
     * Creates HorizontalBarChart data comparing calculatedCost vs cost for the selected month.
     */
    private fun createShipmentCostsChartData(shipmentSales: List<Movement>): BarData {
        var totalCalculatedCost = 0.0
        var totalCost = 0.0

        shipmentSales.forEach { movement ->
            val shipment = movement.delivery?.shipment ?: return@forEach
            totalCalculatedCost += shipment.calculatedCost
            totalCost += shipment.cost
        }

        val calculatedCostEntry = BarEntry(0f, totalCalculatedCost.toFloat())
        val costEntry = BarEntry(1f, totalCost.toFloat())

        val calculatedCostDataSet = BarDataSet(listOf(calculatedCostEntry), "Costo Calculado").apply {
            color = Color.parseColor("#F44336")
            valueTextColor = Color.BLACK
            valueTextSize = 12f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val symbols = DecimalFormatSymbols().apply {
                        groupingSeparator = '.'
                        decimalSeparator = ','
                    }
                    val decimalFormat = DecimalFormat("#,##0", symbols)
                    return decimalFormat.format(value.toInt().toDouble())
                }
            }
        }

        val costDataSet = BarDataSet(listOf(costEntry), "Costo Cobrado").apply {
            color = Color.parseColor("#4CAF50")
            valueTextColor = Color.BLACK
            valueTextSize = 12f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val symbols = DecimalFormatSymbols().apply {
                        groupingSeparator = '.'
                        decimalSeparator = ','
                    }
                    val decimalFormat = DecimalFormat("#,##0", symbols)
                    return decimalFormat.format(value.toInt().toDouble())
                }
            }
        }

        return BarData(calculatedCostDataSet, costDataSet).apply {
            barWidth = 0.4f
        }
    }

    /**
     * Sets up the shipment costs comparison BarChart with styling and configuration.
     * Eje X (abajo): Etiquetas (Costo Calculado, Costo Cobrado)
     * Eje Y (izquierda): Valores (montos en pesos)
     */
    private fun setupShipmentCostsChart(chart: BarChart, data: BarData) {
        chart.data = data
        chart.description.isEnabled = false
        chart.setFitBars(true)
        chart.animateY(1000)

        val labels = listOf("Costo Calculado", "Costo Cobrado")

        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.textColor = Color.BLACK
        xAxis.textSize = 12f
        xAxis.setLabelCount(labels.size, false)
        xAxis.valueFormatter = BalanceAxisLabelFormatter(labels)

        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val symbols = DecimalFormatSymbols().apply {
                    groupingSeparator = '.'
                    decimalSeparator = ','
                }
                val decimalFormat = DecimalFormat("#,##0", symbols)
                return decimalFormat.format(value.toInt().toDouble())
            }
        }

        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        chart.invalidate()
    }

    /**
     * Updates the annual monthly balance chart showing balance (sales - purchases) per month for the selected year.
     */
    private fun updateAnnualSalesByMonthChart(movements: List<Movement>) {
        val startOfYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val endOfYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        val annualMovements = movements.filter { movement ->
            movement.movementDate >= startOfYear &&
            movement.movementDate <= endOfYear
        }

        if (annualMovements.isEmpty()) {
            showEmptyMessage(binding.annualSalesByMonthChart, binding.annualSalesByMonthEmptyMessage)
            return
        }

        hideEmptyMessage(binding.annualSalesByMonthChart, binding.annualSalesByMonthEmptyMessage)

        val (chartData, labels) = createAnnualMonthlyBalanceChartData(annualMovements)
        setupAnnualSalesByMonthChart(binding.annualSalesByMonthChart, chartData, labels)
    }

    /**
     * Creates bar chart data for annual monthly balance (sales - purchases) by month.
     */
    private fun createAnnualMonthlyBalanceChartData(movements: List<Movement>): Pair<BarData, List<String>> {
        val monthBalances = mutableMapOf<Int, Double>()
        val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

        for (month in Calendar.JANUARY..Calendar.DECEMBER) {
            monthBalances[month] = 0.0
        }

        val calendar = Calendar.getInstance()
        movements.forEach { movement ->
            calendar.time = movement.movementDate
            val month = calendar.get(Calendar.MONTH)
            val currentBalance = monthBalances[month] ?: 0.0
            val amount = if (movement.type == EMovementType.SALE) {
                movement.totalAmount
            } else {
                -movement.totalAmount
            }
            monthBalances[month] = currentBalance + amount
        }

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        monthNames.forEachIndexed { index, monthName ->
            val monthNumber = index
            val balance = monthBalances[monthNumber] ?: 0.0
            entries.add(BarEntry(index.toFloat(), balance.toFloat()))
            labels.add(monthName)
        }

        val dataSet = BarDataSet(entries, "").apply {
            colors = entries.map { entry ->
                if (entry.y >= 0) Color.parseColor("#4CAF50") else Color.parseColor("#F44336")
            }
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val symbols = DecimalFormatSymbols().apply {
                        groupingSeparator = '.'
                        decimalSeparator = ','
                    }
                    val decimalFormat = DecimalFormat("#,##0", symbols)
                    return decimalFormat.format(value.toInt().toDouble())
                }
            }
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }

        return Pair(barData, labels)
    }

    /**
     * Sets up the annual sales by month bar chart with styling and configuration.
     */
    private fun setupAnnualSalesByMonthChart(chart: BarChart, data: BarData, labels: List<String>) {
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
        xAxis.valueFormatter = BalanceAxisLabelFormatter(labels)

        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val symbols = DecimalFormatSymbols().apply {
                    groupingSeparator = '.'
                    decimalSeparator = ','
                }
                val decimalFormat = DecimalFormat("#,##0", symbols)
                return decimalFormat.format(value.toInt().toDouble())
            }
        }

        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        chart.invalidate()
    }

    /**
     * Updates the annual client sales chart with top 10 clients for the selected year.
     */
    private fun updateAnnualClientSalesChart(movements: List<Movement>) {
        val startOfYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val endOfYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        val annualSales = movements.filter { movement ->
            movement.type == EMovementType.SALE &&
            movement.movementDate >= startOfYear &&
            movement.movementDate <= endOfYear
        }

        val clientData = createAnnualClientSalesChartData(annualSales)

        if (clientData == null) {
            showEmptyMessage(binding.annualClientSalesChart, binding.annualClientSalesEmptyMessage)
            return
        }

        binding.annualClientSalesChart.visibility = View.VISIBLE
        binding.annualClientSalesEmptyMessage.visibility = View.GONE

        val (chartData, labels) = clientData
        setupAnnualClientSalesChart(binding.annualClientSalesChart, chartData, labels)
    }

    /**
     * Creates bar chart data for annual client sales distribution (top 10 clients).
     */
    private fun createAnnualClientSalesChartData(sales: List<Movement>): Pair<BarData, List<String>>? {
        val clientTotals = mutableMapOf<String, Double>()
        val persons = repository.personsLiveData.value ?: emptyList()

        sales.forEach { movement ->
            val person = persons.find { it.id == movement.personId }
            val clientName = if (person != null) {
                "${person.name} ${person.lastName}".trim()
            } else {
                "Cliente desconocido"
            }
            clientTotals[clientName] = (clientTotals[clientName] ?: 0.0) + movement.totalAmount
        }

        if (clientTotals.isEmpty()) {
            return null
        }

        val topClients = clientTotals.toList()
            .sortedByDescending { it.second }
            .take(10)

        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()

        topClients.forEachIndexed { index, (clientName, totalSpent) ->
            entries.add(BarEntry(index.toFloat(), totalSpent.toFloat()))
            labels.add(clientName)
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#4CAF50")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val symbols = DecimalFormatSymbols().apply {
                        groupingSeparator = '.'
                        decimalSeparator = ','
                    }
                    val decimalFormat = DecimalFormat("#,##0", symbols)
                    return decimalFormat.format(value.toDouble())
                }
            }
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }

        return Pair(barData, labels)
    }

    /**
     * Sets up the annual client sales bar chart with styling and configuration.
     */
    private fun setupAnnualClientSalesChart(chart: BarChart, data: BarData, labels: List<String>) {
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

        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val symbols = DecimalFormatSymbols().apply {
                    groupingSeparator = '.'
                    decimalSeparator = ','
                }
                val decimalFormat = DecimalFormat("#,##0", symbols)
                return decimalFormat.format(value.toInt().toDouble())
            }
        }

        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        chart.invalidate()
    }

    /**
     * Updates the annual recipe sales chart showing top 10 recipes sold in the selected year.
     */
    private fun updateAnnualShipmentProfitChart(movements: List<Movement>) {
        val startOfYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time

        val endOfYear = Calendar.getInstance().apply {
            set(Calendar.YEAR, selectedYear)
            set(Calendar.MONTH, Calendar.DECEMBER)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        val annualSales = movements.filter { movement ->
            movement.type == EMovementType.SALE &&
            movement.movementDate >= startOfYear &&
            movement.movementDate <= endOfYear
        }

        if (annualSales.isEmpty()) {
            showEmptyMessage(binding.annualShipmentProfitChart, binding.annualShipmentProfitEmptyMessage)
            binding.annualShipmentProfitTitle.text = "Ventas por receta"
            return
        }

        hideEmptyMessage(binding.annualShipmentProfitChart, binding.annualShipmentProfitEmptyMessage)
        binding.annualShipmentProfitTitle.text = "Ventas por receta"

        val (chartData, labels) = createAnnualShipmentProfitChartData(annualSales)
        setupAnnualShipmentProfitChart(binding.annualShipmentProfitChart, chartData, labels)
    }

    /**
     * Creates bar chart data for annual recipe sales distribution (top 10 recipes).
     */
    private fun createAnnualShipmentProfitChartData(sales: List<Movement>): Pair<BarData, List<String>> {
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
        
        val sortedRecipes = recipeCounts.toList().sortedByDescending { it.second }.take(10)
        val entries = mutableListOf<BarEntry>()
        val labels = mutableListOf<String>()
        
        sortedRecipes.forEachIndexed { index, (recipeName, count) ->
            entries.add(BarEntry(index.toFloat(), count.toFloat()))
            labels.add(recipeName)
        }
        
        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#4CAF50")
            valueTextColor = Color.BLACK
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val symbols = DecimalFormatSymbols().apply {
                        groupingSeparator = '.'
                        decimalSeparator = ','
                    }
                    val decimalFormat = DecimalFormat("#,##0", symbols)
                    return decimalFormat.format(value.toInt().toDouble())
                }
            }
        }
        
        val barData = BarData(dataSet).apply {
            barWidth = 0.5f
        }
        
        return Pair(barData, labels)
    }

    /**
     * Sets up the annual recipe sales BarChart with styling and configuration.
     */
    private fun setupAnnualShipmentProfitChart(chart: BarChart, data: BarData, labels: List<String>) {
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

        val leftAxis = chart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.axisMinimum = 0f
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = 10f
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                val symbols = DecimalFormatSymbols().apply {
                    groupingSeparator = '.'
                    decimalSeparator = ','
                }
                val decimalFormat = DecimalFormat("#,##0", symbols)
                return decimalFormat.format(value.toInt().toDouble())
            }
        }

        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false

        chart.invalidate()
    }
}

