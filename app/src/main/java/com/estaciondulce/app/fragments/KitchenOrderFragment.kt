package com.estaciondulce.app.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.Fragment
import com.estaciondulce.app.activities.KitchenOrderEditActivity
import com.estaciondulce.app.adapters.KitchenOrderAdapter
import com.estaciondulce.app.databinding.FragmentKitchenOrderBinding
import com.estaciondulce.app.helpers.KitchenOrdersHelper
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.models.EKitchenOrderStatus
import com.estaciondulce.app.models.toColumnConfigs
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import android.graphics.Color
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment to display kitchen orders in a table format
 */
class KitchenOrderFragment : Fragment() {

    private var _binding: FragmentKitchenOrderBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository
    private val kitchenOrdersHelper = KitchenOrdersHelper()
    private var movementsWithKitchenOrders: List<Movement> = emptyList()

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View? {
        _binding = FragmentKitchenOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupSearchBar()
        loadKitchenOrders()
    }

    private fun setupSearchBar() {
        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterKitchenOrders(s.toString())
            }
        })
    }

    private fun loadKitchenOrders() {
        repository.movementsLiveData.observe(viewLifecycleOwner) { movements ->
            val sales = movements.filter { it.type?.name == "SALE" }
            
            movementsWithKitchenOrders = sales.filter { movement ->
                movement.kitchenOrderStatus != null
            }.sortedByDescending { it.movementDate }

            setupTableView(movementsWithKitchenOrders)
        }
    }

    private fun filterKitchenOrders(query: String) {
        val filteredList = if (query.isBlank()) {
            movementsWithKitchenOrders
        } else {
            movementsWithKitchenOrders.filter { movement ->
                val person = repository.personsLiveData.value?.find { it.id == movement.personId }
                val clientName = person?.let { "${it.name} ${it.lastName}" } ?: ""
                clientName.contains(query, ignoreCase = true) ||
                formatDateToSpanish(movement.movementDate).contains(query, ignoreCase = true)
            }
        }
        val sortedFilteredList = filteredList.sortedByDescending { it.movementDate }
        setupTableView(sortedFilteredList)
    }

    private fun setupTableView(movements: List<Movement>) {
        val columnConfigs = listOf("Fecha", "Cliente", "Estado").toColumnConfigs()
        
        binding.kitchenOrdersTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = movements,
            adapter = KitchenOrderAdapter(
                movementList = movements,
                onRowClick = { movement -> openKitchenOrderDetails(movement.id) }
            ) { movement ->
                val person = repository.personsLiveData.value?.find { it.id == movement.personId }
                val clientName = person?.let { "${it.name} ${it.lastName}" } ?: "Cliente desconocido"
                val date = formatDateToSpanish(movement.movementDate)
                val statusInfo = getMovementKitchenOrderStatusInfo(movement)
                
                listOf(
                    date,
                    clientName,
                    statusInfo
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val movement = item as Movement
                when (columnIndex) {
                    0 -> formatDateToSpanish(movement.movementDate)
                    1 -> {
                        val person = repository.personsLiveData.value?.find { it.id == movement.personId }
                        person?.let { "${it.name} ${it.lastName}" } ?: "Cliente desconocido"
                    }
                    2 -> getMovementKitchenOrderStatusInfo(movement)
                    else -> null
                }
            },
            enableColumnSorting = false
        )
    }

    private fun getMovementKitchenOrderStatus(movement: Movement): String {
        return when (movement.kitchenOrderStatus) {
            EKitchenOrderStatus.PENDING -> "Pendiente de preparación"
            EKitchenOrderStatus.PREPARING -> "En preparación"
            EKitchenOrderStatus.READY -> {
                if (movement.shipment != null) {
                    "Listo para envío"
                } else {
                    "Listo para entrega"
                }
            }
            EKitchenOrderStatus.CANCELED -> "Cancelado"
            EKitchenOrderStatus.DONE -> "Entregado"
            null -> "Sin estado"
        }
    }

    private fun getMovementKitchenOrderStatusInfo(movement: Movement): String {
        return getMovementKitchenOrderStatus(movement)
    }

    private fun getMovementKitchenOrderStatusColor(movement: Movement): String {
        return when (movement.kitchenOrderStatus) {
            EKitchenOrderStatus.PENDING -> "#FF9800" // Orange
            EKitchenOrderStatus.PREPARING -> "#2196F3" // Blue
            EKitchenOrderStatus.READY -> "#4CAF50" // Green
            EKitchenOrderStatus.CANCELED -> "#F44336" // Red
            EKitchenOrderStatus.DONE -> "#9E9E9E" // Gray
            null -> "#757575" // Dark Gray
        }
    }

    private fun openKitchenOrderDetails(movementId: String) {
        val intent = Intent(requireContext(), KitchenOrderEditActivity::class.java).apply {
            putExtra("movementId", movementId)
        }
        startActivity(intent)
    }

    private fun formatDateToSpanish(date: Date): String {
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
