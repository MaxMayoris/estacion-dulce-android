package com.estaciondulce.app.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.estaciondulce.app.R
import com.estaciondulce.app.activities.KitchenOrderEditActivity
import com.estaciondulce.app.adapters.CalendarDay
import com.estaciondulce.app.adapters.DayOrdersAdapter
import com.estaciondulce.app.adapters.KitchenCalendarAdapter
import com.estaciondulce.app.adapters.KitchenOrderAdapter
import com.estaciondulce.app.databinding.FragmentKitchenOrderBinding
import com.estaciondulce.app.helpers.KitchenOrdersHelper
import com.estaciondulce.app.models.enums.EDeliveryType
import com.estaciondulce.app.models.parcelables.Movement
import com.estaciondulce.app.models.enums.EKitchenOrderStatus
import com.estaciondulce.app.models.toColumnConfigs
import com.estaciondulce.app.repository.FirestoreRepository
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fragment to display kitchen orders in a table format and calendar format
 */
class KitchenOrderFragment : Fragment() {

    private var _binding: FragmentKitchenOrderBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository
    private val kitchenOrdersHelper = KitchenOrdersHelper()
    private var movementsWithKitchenOrders: List<Movement> = emptyList()

    private var currentMonth: Calendar = Calendar.getInstance()
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("es"))
    private lateinit var calendarAdapter: KitchenCalendarAdapter
    private var selectedDate: Date = Calendar.getInstance().time

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentKitchenOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupTabs()
        setupSearchBar()
        setupCalendarView()
        loadKitchenOrders()
    }

    private fun setupTabs() {
        binding.listTab.setOnClickListener { selectTab(isListTab = true) }
        binding.calendarTab.setOnClickListener { selectTab(isListTab = false) }
    }

    private fun selectTab(isListTab: Boolean) {
        if (isListTab) {
            binding.listContainer.visibility = View.VISIBLE
            binding.calendarContainer.visibility = View.GONE
            binding.listTab.background = ContextCompat.getDrawable(requireContext(), R.drawable.tab_selected_background)
            binding.listTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.calendarTab.background = ContextCompat.getDrawable(requireContext(), R.drawable.tab_unselected_background)
            binding.calendarTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        } else {
            binding.listContainer.visibility = View.GONE
            binding.calendarContainer.visibility = View.VISIBLE
            binding.calendarTab.background = ContextCompat.getDrawable(requireContext(), R.drawable.tab_selected_background)
            binding.calendarTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            binding.listTab.background = ContextCompat.getDrawable(requireContext(), R.drawable.tab_unselected_background)
            binding.listTab.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }
    }

    private fun setupCalendarView() {
        calendarAdapter = KitchenCalendarAdapter(emptyList()) { day ->
            selectedDate = day.date
            calendarAdapter.setSelectedDate(day.date)
            showDayOrders(day)
        }
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(requireContext(), 7)
        binding.calendarRecyclerView.adapter = calendarAdapter

        binding.prevMonthButton.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            updateCalendar()
        }
        binding.nextMonthButton.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            updateCalendar()
        }
    }

    private fun updateCalendar() {
        // Only update if fragment is attached
        if (!isAdded) return

        val capitalizedText = monthYearFormat.format(currentMonth.time).replaceFirstChar { it.titlecase(Locale("es")) }
        binding.monthYearText.text = capitalizedText

        val calendar = Calendar.getInstance()
        calendar.time = currentMonth.time
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Ensure Monday is first day of week
        val firstDayOfWeek = Calendar.MONDAY
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        // Adjust to our week starting on Monday
        val offset = if (dayOfWeek >= firstDayOfWeek) dayOfWeek - firstDayOfWeek else 7 - (firstDayOfWeek - dayOfWeek)
        
        calendar.add(Calendar.DAY_OF_MONTH, -offset)

        val days = mutableListOf<CalendarDay>()
        val currentMonthVal = currentMonth.get(Calendar.MONTH)

        // Load up to 42 days (6 weeks)
        for (i in 0 until 42) {
            val date = calendar.time
            val isCurrentMonth = calendar.get(Calendar.MONTH) == currentMonthVal
            val dayNumber = calendar.get(Calendar.DAY_OF_MONTH)
            
            // Find orders for this day
            val dayStart = calendar.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
            
            val dayOrders = if (isCurrentMonth) {
                movementsWithKitchenOrders.filter { movement ->
                    val movTime = (movement.delivery?.date ?: movement.movementDate).time
                    movTime in dayStart..dayEnd
                }
            } else {
                emptyList()
            }

            days.add(CalendarDay(date, dayNumber, isCurrentMonth, dayOrders))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        calendarAdapter.updateDays(days)
        calendarAdapter.setSelectedDate(selectedDate)
        
        // Show selected date's details
        val startCal = Calendar.getInstance()
        startCal.time = selectedDate
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)
        val dayStart = startCal.timeInMillis
        val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1
        
        val selectedDayOrders = movementsWithKitchenOrders.filter { movement ->
            val movTime = (movement.delivery?.date ?: movement.movementDate).time
            movTime in dayStart..dayEnd
        }
        val calDayForDetail = CalendarDay(selectedDate, 0, true, selectedDayOrders)
        showDayOrders(calDayForDetail)
    }

    private fun showDayOrders(day: CalendarDay) {
        if (!isAdded) return
        val dateFormat = SimpleDateFormat("d 'de' MMMM", Locale("es"))
        binding.dayDetailTitleText.text = "Pedidos del ${dateFormat.format(day.date)}"

        if (day.orders.isEmpty()) {
            binding.dayDetailEmptyText.visibility = View.VISIBLE
            binding.dayOrdersRecyclerView.visibility = View.GONE
        } else {
            binding.dayDetailEmptyText.visibility = View.GONE
            binding.dayOrdersRecyclerView.visibility = View.VISIBLE
            binding.dayOrdersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            binding.dayOrdersRecyclerView.adapter = DayOrdersAdapter(day.orders) { movement ->
                openKitchenOrderDetails(movement.id)
            }
        }
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
            }.sortedByDescending { it.delivery?.date ?: it.movementDate }

            setupTableView(movementsWithKitchenOrders)
            updateCalendar()
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
                formatDateToSpanish(movement.delivery?.date ?: movement.movementDate).contains(query, ignoreCase = true)
            }
        }
        val sortedFilteredList = filteredList.sortedByDescending { it.delivery?.date ?: it.movementDate }
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
                val date = formatDateToSpanish(movement.delivery?.date ?: movement.movementDate)
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
                    0 -> formatDateToSpanish(movement.delivery?.date ?: movement.movementDate)
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
                if (movement.delivery?.type == EDeliveryType.SHIPMENT.name) {
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
