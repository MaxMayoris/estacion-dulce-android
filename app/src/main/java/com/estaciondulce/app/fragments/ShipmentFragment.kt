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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.estaciondulce.app.R
import com.estaciondulce.app.activities.ShipmentEditActivity
import com.estaciondulce.app.adapters.ShipmentTableAdapter
import com.estaciondulce.app.databinding.FragmentShipmentBinding
import com.estaciondulce.app.models.enums.EDeliveryType
import com.estaciondulce.app.models.enums.EShipmentStatus
import com.estaciondulce.app.models.enums.EKitchenOrderStatus
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.models.toColumnConfigs
import com.estaciondulce.app.helpers.GoogleRoutesHelper
import com.estaciondulce.app.helpers.RoutesApiResponse
import android.net.Uri
import android.util.Log
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
    private lateinit var googleRoutesHelper: GoogleRoutesHelper
    
    private var isSelectionMode = false
    private val selectedShipments = mutableSetOf<String>()
    
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
        googleRoutesHelper = GoogleRoutesHelper(requireContext())
        
        repository.movementsLiveData.observe(viewLifecycleOwner) { movements ->
            val shipmentMovements = movements.filter { it.delivery?.type == EDeliveryType.SHIPMENT.name }
            println("DEBUG: Total movements: ${movements.size}, Shipment movements: ${shipmentMovements.size}")
            shipmentMovements.forEach { movement ->
                println("DEBUG: Movement ${movement.id} - delivery type: ${movement.delivery?.type}")
            }
            setupTableView(shipmentMovements)
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterShipments(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        
        setupSelectionModeControls()
    }
    
    private fun setupTableView(shipments: List<Movement>) {
        println("DEBUG: setupTableView called with ${shipments.size} shipments")
        val sortedList = shipments.sortedByDescending { it.delivery?.date ?: Date() }
        val columnConfigs = listOf("Fecha", "Cliente", "Estado").toColumnConfigs()
        binding.shipmentTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = sortedList,
            adapter = ShipmentTableAdapter(
                dataList = sortedList,
                onRowClick = { movement ->
                    if (!isSelectionMode) {
                        val intent = Intent(requireContext(), ShipmentEditActivity::class.java)
                        intent.putExtra("movementId", movement.id)
                        startActivity(intent)
                    }
                },
                onActionClick = { _ -> }, // No longer used
                onMapsClick = { movement ->
                    if (!isSelectionMode) {
                        openGoogleMaps(movement)
                    }
                },
                onSelectionChanged = { shipmentId, isSelected ->
                    handleSelectionChange(shipmentId, isSelected)
                },
                getSelectionMode = { isSelectionMode },
                isItemSelected = { shipmentId -> selectedShipments.contains(shipmentId) }
            ),
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val movement = item as Movement
                when (columnIndex) {
                    0 -> formatDateToSpanish(movement.delivery?.date ?: Date())
                    1 -> {
                        val person = repository.personsLiveData.value?.find { it.id == movement.personId }
                        if (person != null) "${person.name} ${person.lastName}" else "Cliente no encontrado"
                    }
                    2 -> getStatusText(movement.delivery?.status)
                    else -> null
                }
            }
        )
    }

    private fun filterShipments(query: String) {
        val movements = repository.movementsLiveData.value ?: emptyList()
        val filteredList = movements.filter { movement ->
            val delivery = movement.delivery
            delivery?.type == EDeliveryType.SHIPMENT.name && (
                delivery.shipment?.formattedAddress?.contains(query, ignoreCase = true) == true ||
                getStatusText(delivery.status).contains(query, ignoreCase = true)
            )
        }
        setupTableView(filteredList)
    }
    
    private fun showStatusChangeDialog(movement: Movement) {
        val currentStatus = movement.delivery?.status
        
        when (currentStatus) {
            "PENDING" -> {
                showPendingToInProgressDialog(movement)
            }
            "IN_PROGRESS" -> {
                showInProgressOptionsDialog(movement)
            }
            "DELIVERED", "CANCELED" -> {
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
        if (movement.kitchenOrderStatus != EKitchenOrderStatus.READY) {
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
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
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
        val updatedDelivery = movement.delivery?.copy(status = newStatus.name)
        
        val updatedKitchenOrderStatus = if (newStatus == EShipmentStatus.DELIVERED) {
            EKitchenOrderStatus.DONE
        } else {
            movement.kitchenOrderStatus
        }
        
        val updatedMovement = movement.copy(
            delivery = updatedDelivery,
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
        val delivery = movement.delivery ?: return
        val shipment = delivery.shipment
        if (shipment?.lat != null) {
            val person = repository.personsLiveData.value?.find { it.id == movement.personId }
            val destinationName = if (person != null) "${person.name} ${person.lastName}" else "Destino"
            openGoogleMapsSingleDestination(shipment.lat, shipment.lng, destinationName)
        } else {
            CustomToast.showWarning(requireContext(), "No se encontraron coordenadas para este envío")
        }
    }
    
    private fun getStatusText(status: String?): String {
        return when (status) {
            "PENDING" -> "Pendiente"
            "IN_PROGRESS" -> "En Progreso"
            "DELIVERED" -> "Entregado"
            "CANCELED" -> "Cancelado"
            null -> "Pendiente"
            else -> "Pendiente"
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

    /**
     * Sets up the selection mode controls and their click listeners.
     */
    private fun setupSelectionModeControls() {
        binding.selectDestinationsButton.setOnClickListener {
            enterSelectionMode()
        }
        
        binding.optimizeRouteButton.setOnClickListener {
            optimizeRoute()
        }
        
        binding.cancelSelectionButton.setOnClickListener {
            exitSelectionMode()
        }
    }
    
    /**
     * Enters selection mode for multiple shipment selection.
     */
    private fun enterSelectionMode() {
        isSelectionMode = true
        binding.shipmentTable.selectionMode = true
        binding.selectDestinationsButton.visibility = View.GONE
        binding.optimizeRouteButton.visibility = View.VISIBLE
        binding.cancelSelectionButton.visibility = View.VISIBLE
        updateOptimizeButtonState()
        binding.shipmentTable.refreshTable()
    }
    
    /**
     * Exits selection mode and clears all selections.
     */
    private fun exitSelectionMode() {
        isSelectionMode = false
        binding.shipmentTable.selectionMode = false
        selectedShipments.clear()
        binding.selectDestinationsButton.visibility = View.VISIBLE
        binding.optimizeRouteButton.visibility = View.GONE
        binding.cancelSelectionButton.visibility = View.GONE
        binding.shipmentTable.refreshTable()
    }
    
    /**
     * Handles selection changes for individual shipments.
     */
    private fun handleSelectionChange(shipmentId: String, isSelected: Boolean) {
        if (isSelected) {
            selectedShipments.add(shipmentId)
        } else {
            selectedShipments.remove(shipmentId)
        }
        updateOptimizeButtonState()
    }
    
    /**
     * Updates the state of the optimize route button based on selection count.
     */
    private fun updateOptimizeButtonState() {
        binding.optimizeRouteButton.isEnabled = selectedShipments.size >= 2
        binding.optimizeRouteButton.contentDescription = if (selectedShipments.size >= 2) {
            "Optimizar ruta (${selectedShipments.size} destinos)"
        } else {
            "Optimizar ruta (selecciona al menos 2 destinos)"
        }
    }
    
    /**
     * Optimizes the route for selected shipments using Google Routes API.
     */
    private fun optimizeRoute() {
        if (selectedShipments.size < 2) {
            CustomToast.showError(requireContext(), "Selecciona al menos 2 destinos para optimizar la ruta")
            return
        }
        
        customLoader.show()
        
        val movements = repository.movementsLiveData.value ?: emptyList()
        val selectedMovements = movements.filter { selectedShipments.contains(it.id) }
        
        if (selectedMovements.isEmpty()) {
            customLoader.hide()
            CustomToast.showError(requireContext(), "No se encontraron los envíos seleccionados")
            return
        }
        
        val baseAddress = getBaseAddress()
        if (baseAddress == null) {
            customLoader.hide()
            CustomToast.showError(requireContext(), "No se encontró la dirección base en configuración")
            return
        }
        
        calculateOptimizedRoute(baseAddress, selectedMovements)
    }
    
    /**
     * Gets the base address from shipment settings.
     */
    private fun getBaseAddress(): Pair<Double, Double>? {
        val settings = repository.shipmentSettingsLiveData.value
        if (settings?.baseAddress?.isNotEmpty() == true) {
            try {
                val coordinates = settings.baseAddress.split(",")
                if (coordinates.size == 2) {
                    val lat = coordinates[0].trim().toDouble()
                    val lng = coordinates[1].trim().toDouble()
                    return Pair(lat, lng)
                } else {
                    val defaultLat = -34.6037
                    val defaultLng = -58.3816
                    return Pair(defaultLat, defaultLng)
                }
            } catch (e: Exception) {
                val defaultLat = -34.6037
                val defaultLng = -58.3816
                return Pair(defaultLat, defaultLng)
            }
        }
        return null
    }
    
    /**
     * Calculates the optimized route using Google Routes API.
     */
    private fun calculateOptimizedRoute(baseAddress: Pair<Double, Double>, movements: List<Movement>) {
        customLoader.show()
        
        lifecycleScope.launch {
            try {
                googleRoutesHelper.calculateShipmentRoute(
                    baseAddress = baseAddress,
                    movements = movements,
                    onSuccess = { result ->
                        customLoader.hide()
                        handleRouteResult(result, movements)
                    },
                    onError = { exception ->
                        customLoader.hide()
                        CustomToast.showError(requireContext(), "Error al calcular la ruta: ${exception.message}")
                    }
                )
            } catch (e: Exception) {
                customLoader.hide()
                CustomToast.showError(requireContext(), "Error inesperado: ${e.message}")
            }
        }
    }
    
    /**
     * Handles the successful route calculation result.
     */
    private fun handleRouteResult(result: RoutesApiResponse, movements: List<Movement>) {
        val distanceKm = result.distanceMeters / 1000.0
        val durationMinutes = result.durationSeconds / 60.0
        
        val message = "Ruta optimizada calculada:\n" +
                "Distancia: ${String.format("%.1f", distanceKm)} km\n" +
                "Tiempo estimado: ${String.format("%.0f", durationMinutes)} minutos\n" +
                "Destinos: ${movements.size}"
        
        CustomToast.showSuccess(requireContext(), message)
        
        openGoogleMapsWithRoute(result.routeCoordinates, movements, result.optimizedWaypointOrder)
    }
    
    /**
     * Opens Google Maps with a single destination (from current location).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun openGoogleMapsSingleDestination(lat: Double, lng: Double, _destinationName: String) {
        try {
            val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=driving"
            
            
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(mapsUrl))
            intent.setPackage("com.google.android.apps.maps")
            
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
            } else {
                val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                startActivity(webIntent)
            }
        } catch (e: Exception) {
            CustomToast.showError(requireContext(), "Error al abrir Google Maps: ${e.message}")
        }
    }
    
    /**
     * Opens Google Maps with the optimized route (from base address to multiple destinations).
     */
    private fun openGoogleMapsWithRoute(routeCoordinates: List<Pair<Double, Double>>, movements: List<Movement>, optimizedOrder: List<Int>) {
        try {
            if (routeCoordinates.isNotEmpty()) {
                val allWaypoints = movements.mapNotNull { movement ->
                    val delivery = movement.delivery
                    val shipment = delivery?.shipment
                    if (shipment?.lat != null) {
                        "${shipment.lat},${shipment.lng}"
                    } else null
                }
                
                if (allWaypoints.isNotEmpty()) {
                    val orderedWaypoints = if (optimizedOrder.isNotEmpty() && optimizedOrder.size == allWaypoints.size) {
                        optimizedOrder.map { index -> allWaypoints[index] }
                    } else {
                        allWaypoints
                    }
                    
                    
                    
                    val currentLocation = getCurrentLocation()
                    val baseAddress = getBaseAddress()
                    
                    if (currentLocation != null && baseAddress != null) {
                        val origin = "${currentLocation.first},${currentLocation.second}"
                        val destination = "${baseAddress.first},${baseAddress.second}"
                        val waypoints = orderedWaypoints.joinToString("|")
                        
                        
                        val mapsUrl = "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination&waypoints=$waypoints&travelmode=driving&dir_action=navigate"
                        
                        
                        val navigationIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                        navigationIntent.setPackage("com.google.android.apps.maps")
                        navigationIntent.putExtra("navigate", true)
                        
                        if (navigationIntent.resolveActivity(requireContext().packageManager) != null) {
                            startActivity(navigationIntent)
                        } else {
                            val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(mapsUrl))
                            startActivity(webIntent)
                        }
                    } else {
                        CustomToast.showError(requireContext(), "No se pudo obtener la dirección base para la ruta optimizada")
                    }
                } else {
                    CustomToast.showWarning(requireContext(), "No se encontraron coordenadas válidas para abrir en Google Maps")
                }
            } else {
                CustomToast.showWarning(requireContext(), "No se pudo obtener la ruta para abrir en Google Maps")
            }
        } catch (e: Exception) {
            CustomToast.showError(requireContext(), "Error al abrir Google Maps: ${e.message}")
        }
    }

    private fun getCurrentLocation(): Pair<Double, Double>? {
        return Pair(-34.6037, -58.3816) // Buenos Aires coordinates
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
