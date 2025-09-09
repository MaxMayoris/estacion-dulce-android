package com.estaciondulce.app.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.Fragment
import com.estaciondulce.app.activities.MovementEditActivity
import com.estaciondulce.app.adapters.MovementAdapter
import com.estaciondulce.app.databinding.FragmentMovementBinding
import com.estaciondulce.app.helpers.MovementsHelper
import com.estaciondulce.app.models.EMovementType
import com.estaciondulce.app.models.Movement
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.DeleteConfirmationDialog
import com.estaciondulce.app.utils.CustomToast
import com.estaciondulce.app.models.toColumnConfigs

class MovementFragment : Fragment() {

    private var _binding: FragmentMovementBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMovementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository.movementsLiveData.observe(viewLifecycleOwner) { movements ->
            setupTableView(movements)
        }
        repository.personsLiveData.observe(viewLifecycleOwner) {
            repository.movementsLiveData.value?.let { movements ->
                setupTableView(movements)
            }
        }

        binding.addMovementButton.setOnClickListener {
            openMovementEditActivity(null)
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterMovements(s.toString())
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    /**
     * Launches MovementEditActivity. If a movement is passed, it is used for editing.
     */
    private fun openMovementEditActivity(movement: Movement? = null) {
        val intent = Intent(requireContext(), MovementEditActivity::class.java)
        if (movement != null) {
            intent.putExtra("MOVEMENT", movement)
        }
        movementEditActivityLauncher.launch(intent)
    }

    /**
     * Launcher for MovementEditActivity.
     */
    private val movementEditActivityLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Global LiveData is updated automatically.
        }
    }

    /**
     * Configures the table with the list of movements.
     * The table displays Fecha, Nombre (obtained via personId), Monto, and Tipo (Compra o Venta).
     */
    private fun setupTableView(movements: List<Movement>) {
        val sortedList = movements.sortedByDescending { it.date }
        val columnConfigs = listOf("Fecha", "Nombre", "Monto", "Tipo").toColumnConfigs(currencyColumns = setOf(2))
        binding.movementTable.setupTableWithConfigs(
            columnConfigs = columnConfigs,
            data = sortedList,
            adapter = MovementAdapter(
                movementList = sortedList,
                onRowClick = { movement -> editMovement(movement) },
                onDeleteClick = { movement -> deleteMovement(movement) }
            ) { movement ->
                val dateString =
                    android.text.format.DateFormat.format("yyyy-MM-dd", movement.date).toString()
                val personName =
                    repository.personsLiveData.value?.find { it.id == movement.personId }?.let {
                        "${it.name} ${it.lastName}"
                    } ?: "Desconocido"
                val movementTypeDisplay = when (movement.type) {
                    EMovementType.PURCHASE -> "Compra"
                    EMovementType.SALE -> "Venta"
                    else -> "No disponible"
                }
                listOf(
                    dateString,
                    personName,
                    movement.totalAmount,
                    movementTypeDisplay
                )
            },
            pageSize = 10,
            columnValueGetter = { item, columnIndex ->
                val movement = item as Movement
                when (columnIndex) {
                    0 -> android.text.format.DateFormat.format("yyyy-MM-dd", movement.date)
                        .toString()

                    1 -> repository.personsLiveData.value?.find { it.id == movement.personId }
                        ?.let {
                            "${it.name} ${it.lastName}"
                        } ?: "Desconocido"

                    2 -> movement.totalAmount
                    3 -> when (movement.type) {
                        EMovementType.PURCHASE -> "Compra"
                        EMovementType.SALE -> "Venta"
                        else -> "No disponible"
                    }

                    else -> null
                }
            }
        )
    }

    /**
     * Filters the list of movements based on the search query and updates the table.
     * The filter is applied on the person's name retrieved via personId.
     */
    private fun filterMovements(query: String) {
        val movements = repository.movementsLiveData.value ?: emptyList()
        val filteredList = movements.filter { movement ->
            val personName =
                repository.personsLiveData.value?.find { it.id == movement.personId }?.let {
                    "${it.name} ${it.lastName}"
                } ?: ""
            personName.contains(query, ignoreCase = true)
        }
        setupTableView(filteredList)
    }

    private fun editMovement(movement: Movement) {
        openMovementEditActivity(movement)
    }

    private fun deleteMovement(movement: Movement) {
        val movementType = if (movement.type == EMovementType.PURCHASE) "Compra" else "Venta"
        val person = repository.personsLiveData.value?.find { it.id == movement.personId }
        val personName = person?.let { "${it.name} ${it.lastName}" } ?: "Persona desconocida"
        val formattedAmount = String.format("%.2f", movement.totalAmount)
        
        DeleteConfirmationDialog.show(
            context = requireContext(),
            itemName = "$movementType de $personName por $${formattedAmount}",
            itemType = "movimiento",
            onConfirm = {
                MovementsHelper().deleteMovement(
                    movementId = movement.id,
                    onSuccess = {
                        CustomToast.showSuccess(requireContext(), "$movementType de $personName eliminada correctamente.")
                    },
                    onError = {
                        CustomToast.showError(requireContext(), "Error al eliminar el movimiento.")
                    }
                )
            }
        )
    }
}
