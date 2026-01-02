package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.estaciondulce.app.R
import com.estaciondulce.app.activities.DayDetailActivity
import com.estaciondulce.app.activities.WorkBlockEditActivity
import com.estaciondulce.app.adapters.CalendarDayAdapter
import com.estaciondulce.app.databinding.FragmentTimesheetBinding
import com.estaciondulce.app.helpers.TimesheetHelper
import com.estaciondulce.app.models.parcelables.WorkDay
import com.estaciondulce.app.models.parcelables.WorkBlock
import com.estaciondulce.app.models.parcelables.WorkCategory
import com.estaciondulce.app.models.parcelables.Worker
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomLoader
import java.text.SimpleDateFormat
import java.util.*

class TimesheetFragment : Fragment() {

    private var _binding: FragmentTimesheetBinding? = null
    private val binding get() = _binding!!
    private val timesheetHelper = TimesheetHelper()
    private lateinit var customLoader: CustomLoader
    private var currentMonth: Calendar = Calendar.getInstance()
    private var selectedDate: String = ""
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale("es"))
    private val dayFormat = SimpleDateFormat("d", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        setMonthToFirstDay(currentMonth)
        val today = Calendar.getInstance()
        selectedDate = dateFormat.format(today.time)
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTimesheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        customLoader = CustomLoader(requireContext())
        
        setupCalendarGrid()
        setupMonthNavigation()
        setupAddButton()
        setupStatisticsButton()
        setupDaySummaryCard()
        
        val today = Calendar.getInstance()
        selectedDate = dateFormat.format(today.time)
        
        customLoader.show()
        loadMonthData()
        loadSelectedDayData()
    }

    /**
     * Sets up the calendar grid RecyclerView
     */
    private fun setupCalendarGrid() {
        binding.calendarGridRecyclerView.layoutManager = GridLayoutManager(requireContext(), 7)
    }

    /**
     * Sets up month navigation buttons
     */
    private fun setupMonthNavigation() {
        binding.prevWeekButton.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            setMonthToFirstDay(currentMonth)
            selectedDate = dateFormat.format(currentMonth.time)
            if (::customLoader.isInitialized) {
                customLoader.show()
            }
            loadMonthData()
            loadSelectedDayData()
        }

        binding.nextWeekButton.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            setMonthToFirstDay(currentMonth)
            selectedDate = dateFormat.format(currentMonth.time)
            if (::customLoader.isInitialized) {
                customLoader.show()
            }
            loadMonthData()
            loadSelectedDayData()
        }
    }

    /**
     * Sets up the add block button
     */
    private fun setupAddButton() {
        binding.addBlockButton.setOnClickListener {
            val intent = Intent(requireContext(), WorkBlockEditActivity::class.java)
            if (selectedDate.isNotEmpty()) {
                intent.putExtra("DATE", selectedDate)
            }
            workBlockEditLauncher.launch(intent)
        }
    }

    /**
     * Sets up the statistics button
     */
    private fun setupStatisticsButton() {
        binding.statisticsButton.setOnClickListener {
            val intent = Intent(requireContext(), com.estaciondulce.app.activities.TimesheetStatisticsActivity::class.java)
            startActivity(intent)
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
                val workDaysMap = workDays.associateBy { it.date }
                val daysList = generateMonthDaysList(workDaysMap)
                updateCalendarGrid(daysList)
            },
            onError = { exception ->
                if (::customLoader.isInitialized) {
                    customLoader.hide()
                }
                com.estaciondulce.app.utils.CustomToast.showError(
                    requireContext(),
                    "Error al cargar datos: ${exception.message}"
                )
            }
        )
    }

    /**
     * Generates list of day items for the entire month (including empty days at the start)
     */
    private fun generateMonthDaysList(workDaysMap: Map<String, WorkDay>): List<CalendarDayAdapter.CalendarDayItem> {
        val daysList = mutableListOf<CalendarDayAdapter.CalendarDayItem>()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = currentMonth.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = when (firstDayOfWeek) {
            Calendar.SUNDAY -> -6
            Calendar.MONDAY -> 0
            else -> Calendar.MONDAY - firstDayOfWeek
        }
        
        val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        val emptyDaysAtStart = if (daysFromMonday < 0) -daysFromMonday else daysFromMonday
        val totalDaysNeeded = emptyDaysAtStart + daysInMonth
        val weeksToShow = kotlin.math.ceil(totalDaysNeeded / 7.0).toInt()
        val totalCells = weeksToShow * 7

        calendar.add(Calendar.DAY_OF_YEAR, daysFromMonday)

        val targetMonth = currentMonth.get(Calendar.MONTH)
        val targetYear = currentMonth.get(Calendar.YEAR)

        for (i in 0 until totalCells) {
            val date = dateFormat.format(calendar.time)
            val dayNumber = dayFormat.format(calendar.time).toInt()
            val workDay = workDaysMap[date]
            val isCurrentMonth = calendar.get(Calendar.MONTH) == targetMonth && 
                                 calendar.get(Calendar.YEAR) == targetYear

            daysList.add(
                CalendarDayAdapter.CalendarDayItem(
                    date = if (isCurrentMonth) date else "",
                    dayNumber = if (isCurrentMonth) dayNumber else 0,
                    workDay = workDay
                )
            )

            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return daysList
    }

    /**
     * Updates the calendar grid
     */
    private fun updateCalendarGrid(daysList: List<CalendarDayAdapter.CalendarDayItem>) {
        val categories = FirestoreRepository.workCategoriesLiveData.value?.associateBy { it.id } ?: mapOf()
        val today = Calendar.getInstance()
        val todayDate = dateFormat.format(today.time)
        
        if (selectedDate.isEmpty()) {
            selectedDate = todayDate
        }
        
        val adapter = CalendarDayAdapter(
            days = daysList,
            categories = categories,
            selectedDate = selectedDate,
            todayDate = todayDate,
            onDayClick = { date ->
                selectedDate = date
                (binding.calendarGridRecyclerView.adapter as? CalendarDayAdapter)?.updateSelectedDate(date)
                loadSelectedDayData()
            }
        )

        binding.calendarGridRecyclerView.adapter = adapter
        binding.calendarGridRecyclerView.requestLayout()
    }

    /**
     * Sets up the day summary card click listener
     */
    private fun setupDaySummaryCard() {
        binding.daySummaryCard.setOnClickListener {
            if (selectedDate.isNotEmpty()) {
                val intent = Intent(requireContext(), DayDetailActivity::class.java)
                intent.putExtra("DATE", selectedDate)
                dayDetailLauncher.launch(intent)
            }
        }
    }

    /**
     * Loads and displays blocks for the selected day
     */
    private fun loadSelectedDayData() {
        if (selectedDate.isEmpty()) return

        try {
            val dateObj = dateFormat.parse(selectedDate)
            if (dateObj != null) {
                val formattedDate = displayDateFormat.format(dateObj)
                val capitalizedDate = formattedDate.split(" ").joinToString(" ") { word ->
                    if (word == "de") {
                        word
                    } else {
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                    }
                }
                binding.selectedDayTitleText.text = capitalizedDate
            } else {
                binding.selectedDayTitleText.text = selectedDate
            }
        } catch (e: Exception) {
            binding.selectedDayTitleText.text = selectedDate
        }

        timesheetHelper.getBlocksForDay(
            date = selectedDate,
            onSuccess = { blocks ->
                updateDaySummary(blocks)
                if (::customLoader.isInitialized) {
                    customLoader.hide()
                }
            },
            onError = { _ ->
                binding.selectedDayTotalText.text = "Total: 0h"
                binding.selectedDayBlocksText.text = ""
                binding.selectedDayBlocksText.visibility = View.GONE
                binding.selectedDayEmptyText.visibility = View.VISIBLE
                if (::customLoader.isInitialized) {
                    customLoader.hide()
                }
            }
        )
    }

    /**
     * Updates the day summary card with blocks information
     */
    private fun updateDaySummary(blocks: List<WorkBlock>) {
        val totalMinutes = blocks.sumOf { it.durationMinutes }
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

        binding.selectedDayTotalText.text = "Total: $totalStr"

        if (blocks.isEmpty()) {
            binding.selectedDayBlocksText.text = ""
            binding.selectedDayBlocksText.visibility = View.GONE
            binding.selectedDayEmptyText.visibility = View.VISIBLE
        } else {
            binding.selectedDayEmptyText.visibility = View.GONE
            val categories = FirestoreRepository.workCategoriesLiveData.value?.associateBy { it.id } ?: mapOf()
            val workers = FirestoreRepository.workersLiveData.value?.associateBy { it.id } ?: mapOf()

            val sortedBlocks = blocks.sortedBy { it.startAt?.toDate()?.time ?: 0L }
            val blocksText = sortedBlocks.joinToString("\n") { block ->
                val startTime = block.startAt?.toDate()?.let { timeFormat.format(it) } ?: ""
                val endTime = block.endAt?.toDate()?.let { timeFormat.format(it) } ?: ""
                val blockHours = block.durationMinutes / 60
                val blockMinutes = (block.durationMinutes % 60).toInt()
                val durationStr = if (blockHours > 0 && blockMinutes > 0) {
                    "${blockHours}h ${blockMinutes}m"
                } else if (blockHours > 0) {
                    "${blockHours}h"
                } else {
                    "${blockMinutes}m"
                }
                val category = categories[block.categoryId]?.name ?: "Desconocida"
                val worker = workers[block.workerId]?.displayName ?: "Desconocido"
                "$startTime-$endTime ($durationStr) - $category - $worker"
            }

            binding.selectedDayBlocksText.text = blocksText
            binding.selectedDayBlocksText.visibility = View.VISIBLE
        }
    }

    /**
     * Updates the month and year text
     */
    private fun updateMonthYearText() {
        val monthYearText = monthYearFormat.format(currentMonth.time)
        val capitalizedText = monthYearText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        binding.weekRangeText.text = capitalizedText
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

    /**
     * Launcher for WorkBlockEditActivity
     */
    private val workBlockEditLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadMonthData()
        }
    }

    /**
     * Launcher for DayDetailActivity
     */
    private val dayDetailLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadMonthData()
        }
    }

    override fun onResume() {
        super.onResume()
        loadMonthData()
        loadSelectedDayData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
