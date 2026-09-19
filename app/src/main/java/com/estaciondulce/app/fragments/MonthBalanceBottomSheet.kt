package com.estaciondulce.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.estaciondulce.app.databinding.FragmentMonthBalanceBottomSheetBinding
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.repository.FirestoreRepository
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar

class MonthBalanceBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentMonthBalanceBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMonthBalanceBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val month = arguments?.getInt("MONTH") ?: 0
        val year = arguments?.getInt("YEAR") ?: Calendar.getInstance().get(Calendar.YEAR)
        
        val monthNames = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
        val monthName = if (month in 0..11) monthNames[month] else ""
        
        binding.titleText.text = "Balance: $monthName $year"
        setupChart(month, year)
    }

    private fun setupChart(month: Int, year: Int) {
        val movements = FirestoreRepository.movementsLiveData.value ?: emptyList()
        val targetMovements = movements.filter {
            val cal = Calendar.getInstance()
            cal.time = it.movementDate
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
        }

        var totalIncome = 0.0
        var totalPurchases = 0.0

        targetMovements.forEach { mov ->
            if (mov.type == EMovementType.SALE) {
                totalIncome += mov.totalAmount
            } else if (mov.type == EMovementType.PURCHASE) {
                totalPurchases += mov.totalAmount
            }
        }
        
        val balance = totalIncome - totalPurchases

        val symbols = DecimalFormatSymbols().apply { groupingSeparator = '.'; decimalSeparator = ',' }
        val df = DecimalFormat("#,##0.00", symbols)
        
        binding.incomeText.text = "Ingresos:\n$${df.format(totalIncome)}"
        binding.purchasesText.text = "Compras:\n$${df.format(totalPurchases)}"
        binding.balanceText.text = "Balance Neto: $${df.format(balance)}"
        
        val entries = mutableListOf<BarEntry>()
        entries.add(BarEntry(0f, totalIncome.toFloat()))
        entries.add(BarEntry(1f, totalPurchases.toFloat()))

        val dataSet = BarDataSet(entries, "").apply {
            colors = listOf(Color.parseColor("#4CAF50"), Color.parseColor("#F44336"))
            valueTextColor = Color.BLACK
            valueTextSize = 12f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    if (value == 0f) return ""
                    val noDecDf = DecimalFormat("#,##0", symbols)
                    return "$${noDecDf.format(value.toDouble())}"
                }
            }
        }

        binding.barChart.data = BarData(dataSet).apply { barWidth = 0.6f }
        binding.barChart.description.isEnabled = false
        binding.barChart.legend.isEnabled = false

        binding.barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                    return when (value.toInt()) {
                        0 -> "Ingresos"
                        1 -> "Compras"
                        else -> ""
                    }
                }
            }
        }
        
        binding.barChart.axisRight.isEnabled = false
        binding.barChart.axisLeft.apply {
            axisMinimum = 0f
            setDrawGridLines(true)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val noDecDf = DecimalFormat("#,##0", symbols)
                    return "$${noDecDf.format(value.toDouble())}"
                }
            }
        }
        
        binding.barChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(month: Int, year: Int): MonthBalanceBottomSheet {
            val frag = MonthBalanceBottomSheet()
            val args = Bundle()
            args.putInt("MONTH", month)
            args.putInt("YEAR", year)
            frag.arguments = args
            return frag
        }
    }
}
