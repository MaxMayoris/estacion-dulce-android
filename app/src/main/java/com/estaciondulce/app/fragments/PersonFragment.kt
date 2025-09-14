package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.Fragment
import com.estaciondulce.app.activities.PersonEditActivity
import com.estaciondulce.app.adapters.PersonAdapter
import com.estaciondulce.app.databinding.FragmentPersonBinding
import com.estaciondulce.app.helpers.PersonsHelper
import com.estaciondulce.app.models.Person
import com.estaciondulce.app.models.EPersonType
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.utils.CustomToast

class PersonFragment : Fragment() {

    private var _binding: FragmentPersonBinding? = null
    private val binding get() = _binding!!
    private val personsHelper = PersonsHelper()
    private val repository = FirestoreRepository
    
    // Tab state
    private var selectedTab: String = "client" // Default to client tab

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository.personsLiveData.observe(viewLifecycleOwner) { _ ->
            // Apply current filter (empty search + selected tab)
            filterPersons("")
        }
        repository.categoriesLiveData.observe(viewLifecycleOwner) {
            // Apply current filter (empty search + selected tab)
            filterPersons("")
        }

        binding.addPersonButton.setOnClickListener {
            openPersonEditActivity(null)
        }

        // Setup tab click listeners
        binding.clientTab.setOnClickListener {
            selectTab("client")
        }
        
        binding.providerTab.setOnClickListener {
            selectTab("provider")
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterPersons(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    /**
     * Lanza PersonEditActivity. Si se pasa una persona, se usará para editarla.
     */
    private fun openPersonEditActivity(person: Person? = null) {
        val intent = Intent(requireContext(), PersonEditActivity::class.java)
        if (person != null) {
            intent.putExtra("PERSON", person)
        }
        personEditActivityLauncher.launch(intent)
    }

    /**
     * Launcher para PersonEditActivity.
     */
    private val personEditActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
        }
    }

    /**
     * Handles tab selection and updates UI accordingly.
     */
    private fun selectTab(tabType: String) {
        selectedTab = tabType
        
        // Update tab visual states
        when (tabType) {
            "client" -> {
                binding.clientTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.clientTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
                binding.providerTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.providerTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            }
            "provider" -> {
                binding.providerTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_selected_background)
                binding.providerTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.white, null))
                binding.clientTab.setBackgroundResource(com.estaciondulce.app.R.drawable.tab_unselected_background)
                binding.clientTab.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, null))
            }
        }
        
        // Refresh table with current filter
        val currentFilter = binding.searchBar.text.toString()
        filterPersons(currentFilter)
    }

    /**
     * Configura la tabla con la lista de personas según el tab seleccionado.
     */
    private fun setupTableView(persons: List<Person>) {
        // Persons are already filtered by tab type and search query in filterPersons
        val sortedList = persons.sortedBy { it.name }

        // Setup column headers and data based on selected tab
        val (columnHeaders, columnValueGetter) = when (selectedTab) {
            "client" -> {
                val headers = listOf("Nombre", "Teléfono")
                val getter: (Any, Int) -> String? = { item, columnIndex ->
                    val person = item as Person
                    when (columnIndex) {
                        0 -> "${person.name} ${person.lastName}"
                        1 -> {
                            if (person.phones.isNotEmpty()) {
                                val firstPhone = person.phones.first()
                                if (firstPhone.phoneNumberPrefix.isNotEmpty() && firstPhone.phoneNumberSuffix.isNotEmpty()) {
                                    "${firstPhone.phoneNumberPrefix} ${firstPhone.phoneNumberSuffix}"
                                } else {
                                    "Sin teléfono"
                                }
                            } else {
                                "Sin teléfono"
                            }
                        }
                        else -> null
                    }
                }
                Pair(headers, getter)
            }
            "provider" -> {
                val headers = listOf("Nombre", "Teléfono")
                val getter: (Any, Int) -> String? = { item, columnIndex ->
                    val person = item as Person
                    when (columnIndex) {
                        0 -> "${person.name} ${person.lastName}"
                        1 -> {
                            if (person.phones.isNotEmpty()) {
                                val firstPhone = person.phones.first()
                                if (firstPhone.phoneNumberPrefix.isNotEmpty() && firstPhone.phoneNumberSuffix.isNotEmpty()) {
                                    "${firstPhone.phoneNumberPrefix} ${firstPhone.phoneNumberSuffix}"
                                } else {
                                    "Sin teléfono"
                                }
                            } else {
                                "Sin teléfono"
                            }
                        }
                        else -> null
                    }
                }
                Pair(headers, getter)
            }
            else -> {
                val headers = listOf("Nombre", "Tipo")
                val getter: (Any, Int) -> String? = { item, columnIndex ->
                    val person = item as Person
                    when (columnIndex) {
                        0 -> "${person.name} ${person.lastName}"
                        1 -> EPersonType.getDisplayValue(person.type)
                        else -> null
                    }
                }
                Pair(headers, getter)
            }
        }

        binding.personTable.setupTable(
            columnHeaders = columnHeaders,
            data = sortedList,
            adapter = PersonAdapter(
                personList = sortedList,
                onRowClick = { person -> editPerson(person) },
                onDeleteClick = { person -> deletePerson(person) }
            ) { person ->
                // This function should return the same number of elements as columnHeaders
                when (selectedTab) {
                    "client" -> listOf(
                        "${person.name} ${person.lastName}",
                        if (person.phones.isNotEmpty()) {
                            val firstPhone = person.phones.first()
                            if (firstPhone.phoneNumberPrefix.isNotEmpty() && firstPhone.phoneNumberSuffix.isNotEmpty()) {
                                "${firstPhone.phoneNumberPrefix} ${firstPhone.phoneNumberSuffix}"
                            } else {
                                "Sin teléfono"
                            }
                        } else {
                            "Sin teléfono"
                        }
                    )
                    "provider" -> listOf(
                        "${person.name} ${person.lastName}",
                        if (person.phones.isNotEmpty()) {
                            val firstPhone = person.phones.first()
                            if (firstPhone.phoneNumberPrefix.isNotEmpty() && firstPhone.phoneNumberSuffix.isNotEmpty()) {
                                "${firstPhone.phoneNumberPrefix} ${firstPhone.phoneNumberSuffix}"
                            } else {
                                "Sin teléfono"
                            }
                        } else {
                            "Sin teléfono"
                        }
                    )
                    else -> listOf(
                        "${person.name} ${person.lastName}",
                        EPersonType.getDisplayValue(person.type)
                    )
                }
            },
            pageSize = 10,
            columnValueGetter = columnValueGetter
        )
    }

    /**
     * Filtra la lista de personas según la búsqueda y actualiza la tabla.
     */
    private fun filterPersons(query: String) {
        val persons = repository.personsLiveData.value ?: emptyList()
        
        // First filter by selected tab type
        val tabFilteredPersons = when (selectedTab) {
            "client" -> persons.filter { it.type == "CLIENT" }
            "provider" -> persons.filter { it.type == "PROVIDER" }
            else -> persons
        }
        
        // Then filter by search query
        val filteredList = if (query.isEmpty()) {
            tabFilteredPersons
        } else {
            tabFilteredPersons.filter {
                it.name.contains(query, ignoreCase = true) || 
                it.lastName.contains(query, ignoreCase = true)
            }
        }
        
        setupTableView(filteredList)
    }

    /**
     * Llama a PersonEditActivity para editar la persona seleccionada.
     */
    private fun editPerson(person: Person) {
        openPersonEditActivity(person)
    }

    private fun deletePerson(person: Person) {
        // Check if person has associated movements
        val associatedMovements = repository.movementsLiveData.value?.filter { it.personId == person.id } ?: emptyList()
        
        if (associatedMovements.isNotEmpty()) {
            // Show error message if person has movements
            val movementCount = associatedMovements.size
            val movementText = if (movementCount == 1) "movimiento" else "movimientos"
            CustomToast.showError(
                requireContext(), 
                "No se puede eliminar esta persona porque tiene $movementCount $movementText asociado${if (movementCount > 1) "s" else ""}. " +
                "Elimine primero los movimientos para poder eliminar la persona."
            )
            return
        }
        
        // Proceed with deletion if no movements are associated
        DeleteConfirmationDialog.show(
            context = requireContext(),
            itemName = "${person.name} ${person.lastName}",
            itemType = "persona",
            onConfirm = {
                PersonsHelper().deletePerson(
                    personId = person.id,
                    onSuccess = {
                        CustomToast.showSuccess(requireContext(), "Persona eliminada correctamente.")
                    },
                    onError = { exception ->
                        CustomToast.showError(requireContext(), "Error al eliminar la persona: ${exception.message}")
                    }
                )
            }
        )
    }
}
