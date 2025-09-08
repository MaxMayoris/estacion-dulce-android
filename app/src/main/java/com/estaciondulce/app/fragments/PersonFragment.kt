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
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.utils.CustomToast

class PersonFragment : Fragment() {

    private var _binding: FragmentPersonBinding? = null
    private val binding get() = _binding!!
    private val personsHelper = PersonsHelper()
    private val repository = FirestoreRepository

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
        repository.personsLiveData.observe(viewLifecycleOwner) { persons ->
            setupTableView(persons)
        }
        repository.categoriesLiveData.observe(viewLifecycleOwner) {
            repository.personsLiveData.value?.let { persons ->
                setupTableView(persons)
            }
        }

        binding.addPersonButton.setOnClickListener {
            openPersonEditActivity(null)
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
            // La LiveData global se actualiza automáticamente.
        }
    }

    /**
     * Configura la tabla con la lista de personas.
     */
    private fun setupTableView(persons: List<Person>) {
        val sortedList = persons.sortedBy { it.name }

        binding.personTable.setupTable(
            columnHeaders = listOf("Nombre", "Tipo"),
            data = sortedList,
            adapter = PersonAdapter(
                personList = sortedList,
                onRowClick = { person -> editPerson(person) },
                onDeleteClick = { person -> deletePerson(person) }
            ) { person ->
                listOf(
                    "${person.name} ${person.lastName}",
                    person.type
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val person = item as Person
                when (columnIndex) {
                    0 -> "${person.name} ${person.lastName}"
                    1 -> person.type
                    else -> null
                }
            }
        )
    }

    /**
     * Filtra la lista de personas según la búsqueda y actualiza la tabla.
     */
    private fun filterPersons(query: String) {
        val persons = repository.personsLiveData.value ?: emptyList()
        val filteredList = persons.filter {
            it.name.contains(query, ignoreCase = true)
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
