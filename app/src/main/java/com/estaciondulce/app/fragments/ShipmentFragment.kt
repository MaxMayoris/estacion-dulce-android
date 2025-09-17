package com.estaciondulce.app.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.estaciondulce.app.R
import com.estaciondulce.app.activities.ShipmentEditActivity
import com.estaciondulce.app.adapters.ShipmentTableAdapter
import com.estaciondulce.app.databinding.FragmentShipmentBinding
import com.estaciondulce.app.models.EShipmentStatus
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.estaciondulce.app.models.toColumnConfigs
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment to display and manage shipments.
 */
class ShipmentFragment : Fragment() {

    private var _binding: FragmentShipmentBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository
    private lateinit var customLoader: CustomLoader
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentShipmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        customLoader = CustomLoader(requireContext())
        
        repository.movementsLiveData.observe(viewLifecycleOwner) { movements ->
            setupTableView(movements.filter { it.shipment != null })
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterShipments(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    
    private fun setupTableView(shipments: List<Movement>) {
        val sortedList = shipments.sortedByDescending { it.shipment?.date ?: Date() }
        val columnConfigs = listOf("Fecha", "Cliente", "Estado").toColumnConfigs()
        binding.shipmentTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = sortedList,
            adapter = ShipmentTableAdapter(
                dataList = sortedList,
                onRowClick = { movement ->
                    val intent = Intent(requireContext(), ShipmentEditActivity::class.java)
                    intent.putExtra("movementId", movement.id)
                    startActivity(intent)
                },
                onActionClick = { _ -> }, // No longer used
                onMapsClick = { movement ->
                    openGoogleMaps(movement)
                }
            ),
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val movement = item as Movement
                when (columnIndex) {
                    0 -> formatDateToSpanish(movement.shipment?.date ?: Date())
                    1 -> {
                        val person = repository.personsLiveData.value?.find { it.id == movement.personId }
                        if (person != null) "${person.name} ${person.lastName}" else "Cliente no encontrado"
                    }
                    2 -> getStatusText(movement.shipment?.status)
                    else -> null
                }
            }
        )
    }

    private fun filterShipments(query: String) {
        val movements = repository.movementsLiveData.value ?: emptyList()
        val filteredList = movements.filter { movement ->
            val shipment = movement.shipment
            shipment != null && (
                shipment.formattedAddress.contains(query, ignoreCase = true) ||
                getStatusText(shipment.status).contains(query, ignoreCase = true)
            )
        }
        setupTableView(filteredList)
    }
    
    private fun showStatusChangeDialog(movement: Movement) {
        val currentStatus = movement.shipment?.status
        
        when (currentStatus) {
            EShipmentStatus.PENDING -> {
                showPendingToInProgressDialog(movement)
            }
            EShipmentStatus.IN_PROGRESS -> {
                showInProgressOptionsDialog(movement)
            }
            EShipmentStatus.DELIVERED, EShipmentStatus.CANCELED -> {
                CustomToast.showInfo(requireContext(), "Este envío ya está finalizado")
                return
            }
            null -> {
                CustomToast.showError(requireContext(), "Estado de envío no válido")
                return
            }
        }
    }
    
    private fun showPendingToInProgressDialog(movement: Movement) {
        if (movement.kitchenOrderStatus != com.estaciondulce.app.models.EKitchenOrderStatus.READY) {
            CustomToast.showError(requireContext(), "No se puede iniciar el envío. El pedido debe estar listo en cocina.")
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.custom_status_dialog, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = "El envío pasará a: EN PROGRESO"
        dialogView.findViewById<ImageView>(R.id.dialogIcon).setImageResource(R.drawable.ic_clock_blue_small)
        dialogView.findViewById<Button>(R.id.positiveButton).setOnClickListener {
            updateShipmentStatus(movement, EShipmentStatus.IN_PROGRESS)
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.negativeButton).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun showInProgressOptionsDialog(movement: Movement) {
        val dialogView = layoutInflater.inflate(R.layout.custom_status_dialog_two_options, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        
        // Configure dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Set button listeners
        dialogView.findViewById<Button>(R.id.positiveButton).setOnClickListener {
            updateShipmentStatus(movement, EShipmentStatus.DELIVERED)
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.neutralButton).setOnClickListener {
            updateShipmentStatus(movement, EShipmentStatus.CANCELED)
            dialog.dismiss()
        }
        
        dialogView.findViewById<Button>(R.id.negativeButton).setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun updateShipmentStatus(movement: Movement, newStatus: EShipmentStatus) {
        val updatedShipment = movement.shipment?.copy(status = newStatus)
        
        val updatedKitchenOrderStatus = if (newStatus == EShipmentStatus.DELIVERED) {
            com.estaciondulce.app.models.EKitchenOrderStatus.DONE
        } else {
            movement.kitchenOrderStatus
        }
        
        val updatedMovement = movement.copy(
            shipment = updatedShipment,
            kitchenOrderStatus = updatedKitchenOrderStatus
        )
        
        val movementsHelper = com.estaciondulce.app.helpers.MovementsHelper()
        
        customLoader.show()
        movementsHelper.updateMovement(
            movementId = movement.id,
            movement = updatedMovement,
            updateKitchenOrders = false,
            onSuccess = {
                customLoader.hide()
                val message = if (newStatus == EShipmentStatus.DELIVERED) {
                    "Envío marcado como entregado y pedido completado"
                } else {
                    "Estado actualizado correctamente"
                }
                CustomToast.showSuccess(requireContext(), message)
            },
            onError = { exception ->
                customLoader.hide()
                CustomToast.showError(requireContext(), "Error al actualizar estado: ${exception.message}")
            }
        )
    }
    
    private fun openGoogleMaps(movement: Movement) {
        val shipment = movement.shipment ?: return
        try {
            val gmmIntentUri = android.net.Uri.parse("google.navigation:q=${shipment.lat},${shipment.lng}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        } catch (e: Exception) {
            // Fallback to web version
            val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/maps?q=${shipment.lat},${shipment.lng}"))
            startActivity(webIntent)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
