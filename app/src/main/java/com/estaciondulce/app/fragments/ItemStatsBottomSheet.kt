package com.estaciondulce.app.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.estaciondulce.app.databinding.FragmentItemStatsBottomSheetBinding
import com.estaciondulce.app.models.enums.EMovementType
import com.estaciondulce.app.repository.FirestoreRepository
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar

class ItemStatsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentItemStatsBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentItemStatsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val itemId = arguments?.getString("ITEM_ID") ?: return
        val itemType = arguments?.getString("ITEM_TYPE") ?: return
        val year = arguments?.getInt("YEAR") ?: Calendar.getInstance().get(Calendar.YEAR)
        val name = arguments?.getString("ITEM_NAME") ?: "Detalle"
        
        if (itemType == "PROVIDER") {
            binding.titleText.text = "Compras: $name ($year)"
        } else {
            binding.titleText.text = "Ventas: $name ($year)"
        }
        
        setupChart(itemId, itemType, year)
    }

    private fun setupChart(itemId: String, itemType: String, year: Int) {
        val movements = FirestoreRepository.movementsLiveData.value ?: emptyList()
        val targetMovementType = if (itemType == "PROVIDER") EMovementType.PURCHASE else EMovementType.SALE
        val itemsData = movements.filter {
            it.type == targetMovementType && 
            isMovementInYear(it.movementDate, year) && 
            movementMatchesItem(it, itemId, itemType)
        }

        val monthTotals = FloatArray(12) { 0f }
        val calendar = Calendar.getInstance()
        var totalAmount = 0.0

        itemsData.forEach { mov ->
            calendar.time = mov.movementDate
            val month = calendar.get(Calendar.MONTH)
            monthTotals[month] += mov.totalAmount.toFloat()
            totalAmount += mov.totalAmount
        }

        val symbols = DecimalFormatSymbols().apply { groupingSeparator = '.'; decimalSeparator = ',' }
        val df = DecimalFormat("#,##0", symbols) // Fix: No decimals if not needed
        
        if (itemType == "PROVIDER") {
            binding.totalText.text = "Total gastado: $${df.format(totalAmount)}"
        } else {
            binding.totalText.text = "Total recaudado: $${df.format(totalAmount)}"
        }

        val entries = mutableListOf<Entry>()
        for (i in 0..11) {
            entries.add(Entry(i.toFloat(), monthTotals[i]))
        }

        val dataSet = LineDataSet(entries, if (itemType == "PROVIDER") "Compras" else "Ventas").apply {
            val lineColor = if (itemType == "PROVIDER") Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
            color = lineColor
            setCircleColor(lineColor)
            lineWidth = 3f
            circleRadius = 4f
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    if (value == 0f) return ""
                    return "$${df.format(value.toDouble())}"
                }
            }
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.description.isEnabled = false
        binding.lineChart.legend.isEnabled = false

        val monthNames = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
        binding.lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
                    val index = value.toInt()
                    return if (index in 0..11) monthNames[index] else ""
                }
            }
        }
        binding.lineChart.axisRight.isEnabled = false
        binding.lineChart.axisLeft.axisMinimum = 0f
        
        binding.lineChart.invalidate()
    }

    private fun isMovementInYear(date: java.util.Date, year: Int): Boolean {
        val cal = Calendar.getInstance()
        cal.time = date
        return cal.get(Calendar.YEAR) == year
    }

    private fun movementMatchesItem(movement: com.estaciondulce.app.models.parcelables.Movement, itemId: String, itemType: String): Boolean {
        if (itemType == "CLIENT") {
            return movement.personId == itemId
        } else if (itemType == "PROVIDER") {
            return movement.personId == itemId
        } else if (itemType == "RECIPE") {
            return movement.items.any { it.collection == "recipes" && it.collectionId == itemId }
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(itemId: String, itemType: String, itemName: String, year: Int): ItemStatsBottomSheet {
            val frag = ItemStatsBottomSheet()
            val args = Bundle()
            args.putString("ITEM_ID", itemId)
            args.putString("ITEM_TYPE", itemType)
            args.putString("ITEM_NAME", itemName)
            args.putInt("YEAR", year)
            frag.arguments = args
            return frag
        }
    }
}
