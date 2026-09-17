package com.estaciondulce.app.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.estaciondulce.app.activities.EventEditActivity
import com.estaciondulce.app.adapters.EventAdapter
import com.estaciondulce.app.databinding.FragmentEventBinding
import com.estaciondulce.app.helpers.EventsHelper
import com.estaciondulce.app.models.parcelables.Event
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import java.text.SimpleDateFormat
import java.util.Locale

class EventFragment : Fragment() {

    private var _binding: FragmentEventBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.addEventButton.setOnClickListener {
            openEventEditActivity(null)
        }

        repository.eventsLiveData.observe(viewLifecycleOwner) { events ->
            val currentFilter = binding.searchBar.text.toString()
            filterEvents(currentFilter)
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterEvents(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun openEventEditActivity(event: Event?) {
        val intent = Intent(requireContext(), EventEditActivity::class.java)
        if (event != null) {
            intent.putExtra("EVENT_ID", event.id)
        }
        startActivity(intent)
    }

    private fun setupTableView(events: List<Event>) {
        val sortedList = events.sortedByDescending { it.startDate?.time ?: 0L }
        
        val headers = listOf("Nombre", "Fechas")
        val getter: (Any, Int) -> String? = { item, columnIndex ->
            val event = item as Event
            when (columnIndex) {
                0 -> if (event.description.isNotEmpty()) "${event.name} - ${event.description}" else event.name
                1 -> {
                    val start = event.startDate?.let { dateFormat.format(it) } ?: ""
                    val end = event.endDate?.let { dateFormat.format(it) } ?: ""
                    if (start.isNotEmpty() && end.isNotEmpty()) "$start al $end"
                    else if (start.isNotEmpty()) "Desde $start"
                    else if (end.isNotEmpty()) "Hasta $end"
                    else "-"
                }
                else -> null
            }
        }
        
        binding.eventTable.setupTable(
            columnHeaders = headers,
            data = sortedList,
            adapter = EventAdapter(
                eventList = sortedList,
                onRowClick = { editEvent(it) },
                onDeleteClick = { deleteEvent(it) }
            ) { event ->
                listOf(
                    if (event.description.isNotEmpty()) "${event.name} - ${event.description}" else event.name,
                    getter(event, 1) ?: "-"
                )
            },
            pageSize = 10,
            columnValueGetter = getter
        )
    }

    private fun filterEvents(query: String) {
        val events = repository.eventsLiveData.value ?: emptyList()
        val filteredList = if (query.isEmpty()) {
            events
        } else {
            events.filter {
                it.name.contains(query, ignoreCase = true) || 
                it.description.contains(query, ignoreCase = true)
            }
        }
        setupTableView(filteredList)
    }

    private fun editEvent(event: Event) {
        openEventEditActivity(event)
    }

    private fun deleteEvent(event: Event) {
        val associatedMovements = repository.movementsLiveData.value?.filter { it.eventId == event.id } ?: emptyList()
        // Here we could also check workBlocks by finding all WorkDays and iterating, but for simplicity let's rely on EventsHelper to do the backend check or just basic checks.
        
        if (associatedMovements.isNotEmpty()) {
            CustomToast.showError(requireContext(), "No se puede eliminar. Tiene movimientos asociados.")
            return
        }
        
        DeleteConfirmationDialog.show(
            context = requireContext(),
            itemName = event.name,
            itemType = "evento",
            onConfirm = {
                EventsHelper().deleteEvent(
                    eventId = event.id,
                    onSuccess = {
                        CustomToast.showSuccess(requireContext(), "Evento eliminado.")
                    },
                    onError = { exception ->
                        CustomToast.showError(requireContext(), "Error: ${exception.message}")
                    }
                )
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
