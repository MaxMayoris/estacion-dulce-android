package com.estaciondulce.app.adapters

import android.graphics.Color
import android.view.View
import com.estaciondulce.app.databinding.TableRowDynamicBinding
import com.estaciondulce.app.models.EShipmentStatus
import com.estaciondulce.app.models.Movement
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying shipments in a table format.
 */
class ShipmentTableAdapter(
    dataList: List<Movement>,
    private val onRowClick: (Movement) -> Unit,
    private val onActionClick: (Movement) -> Unit,
    private val onMapsClick: (Movement) -> Unit
) : TableAdapter<Movement>(dataList, onRowClick, { /* No delete for shipments */ }) {

    override fun getCellValues(item: Movement, position: Int): List<Any> {
        val shipment = item.shipment ?: return listOf("", "", "")
        val person = com.estaciondulce.app.repository.FirestoreRepository.personsLiveData.value?.find { it.id == item.personId }
        val personName = if (person != null) "${person.name} ${person.lastName}" else "Cliente no encontrado"
        
        return listOf(
            formatDateToSpanish(shipment.date ?: Date()),
            personName,
            getStatusText(shipment.status)
        )
    }

    override fun bindRow(binding: TableRowDynamicBinding, item: Movement, position: Int) {
        val cellValues = getCellValues(item, position)
        bindRowContentWithColors(binding, cellValues, item)
        
        // Show only maps button, hide action and delete buttons
        binding.deleteIcon.visibility = View.GONE
        binding.actionIcon.visibility = View.GONE
        binding.mapsIcon.visibility = View.VISIBLE
        
        // Set up maps icon
        binding.mapsIcon.setImageResource(android.R.drawable.ic_menu_mylocation)
        binding.mapsIcon.setOnClickListener { onMapsClick(item) }
        
        // Configure dynamic icon spacing
        configureIconSpacing(binding)
    }

    private fun bindRowContentWithColors(binding: TableRowDynamicBinding, cellValues: List<Any>, item: Movement) {
        binding.rowContainer.removeAllViews()
        for ((index, value) in cellValues.withIndex()) {
            val textView = android.widget.TextView(binding.root.context).apply {
                text = when (value) {
                    is Double -> {
                        val formattedValue = String.format("%.2f", value)
                        "$$formattedValue"
                    }
                    is Boolean -> if (value) "Sí" else "No"
                    else -> value.toString()
                }
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                setPadding(8, 6, 8, 6)
                textAlignment = android.widget.TextView.TEXT_ALIGNMENT_CENTER
                gravity = android.view.Gravity.CENTER
                setTextColor(binding.root.context.getColor(com.estaciondulce.app.R.color.table_cell_text))
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.NORMAL)
                
                // Apply color to status column (index 2)
                if (index == 2) {
                    val status = item.shipment?.status
                    setTextColor(getStatusColor(status))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }
            binding.rowContainer.addView(textView)
        }
    }

    private fun getStatusText(status: EShipmentStatus?): String {
        return when (status) {
            EShipmentStatus.PENDING -> "Pendiente"
            EShipmentStatus.IN_PROGRESS -> "En Progreso"
            EShipmentStatus.DELIVERED -> "Entregado"
            EShipmentStatus.CANCELED -> "Cancelado"
            null -> "Pendiente"
        }
    }

    private fun getStatusColor(status: EShipmentStatus?): Int {
        return when (status) {
            EShipmentStatus.PENDING -> Color.GRAY
            EShipmentStatus.IN_PROGRESS -> Color.BLUE
            EShipmentStatus.DELIVERED -> Color.GREEN
            EShipmentStatus.CANCELED -> Color.RED
            null -> Color.GRAY
        }
    }

    /**
     * Formats a date to Spanish format: "dd mes hh:mm"
     */
    private fun formatDateToSpanish(date: java.util.Date): String {
        val sdf = SimpleDateFormat("dd MMM HH:mm", Locale("es"))
        val formatted = sdf.format(date)
        return formatted.replace("sept.", "sep")
            .replace("enero", "ene")
            .replace("febrero", "feb")
            .replace("marzo", "mar")
            .replace("abril", "abr")
            .replace("mayo", "may")
            .replace("junio", "jun")
            .replace("julio", "jul")
            .replace("agosto", "ago")
            .replace("octubre", "oct")
            .replace("noviembre", "nov")
            .replace("diciembre", "dic")
    }
}
