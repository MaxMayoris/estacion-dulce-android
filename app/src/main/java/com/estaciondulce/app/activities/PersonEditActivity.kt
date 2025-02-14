package com.estaciondulce.app.activities

import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityPersonEditBinding
import com.estaciondulce.app.helpers.PersonsHelper
import com.estaciondulce.app.models.Person
import com.estaciondulce.app.repository.FirestoreRepository
import com.google.android.material.snackbar.Snackbar

/**
 * Activity para agregar o editar una persona (Cliente o Proveedor).
 */
class PersonEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonEditBinding
    private val personsHelper = PersonsHelper() // Usamos el helper para personas
    private var currentPerson: Person? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Recibir la persona (si se está editando) a través del Intent
        currentPerson = intent.getParcelableExtra("PERSON")
        supportActionBar?.title = if (currentPerson != null) "Editar Persona" else "Agregar Persona"

        // Configurar el Spinner para el tipo de persona (Cliente o Proveedor)
        val personTypes = listOf("Cliente", "Proveedor")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, personTypes)
        binding.personTypeSpinner.adapter = spinnerAdapter

        // Lista de prefijos con nombres de provincias
        val phonePrefixOptions = listOf(
            "Córdoba (351)",
            "Santa Fe (341)",
            "Mendoza (261)",
            "Tucumán (381)",
            "Salta (387)",
            "Jujuy (388)",
            "Santiago del Estero (386)",
            "Corrientes (379)",
            "Entre Ríos (343)",
            "Misiones (375)",
            "Chaco (373)",
            "Formosa (370)",
            "San Juan (264)",
            "San Luis (266)",
            "La Rioja (382)",
            "Neuquén (299)",
            "Río Negro (294)",
            "Chubut (297)",
            "Santa Cruz (296)",
            "Tierra del Fuego (291)"
        )
        val phonePrefixAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, phonePrefixOptions)
        binding.phonePrefixSpinner.adapter = phonePrefixAdapter

        // Establecer el valor por defecto a "San Juan (264)"
        val defaultIndex = phonePrefixOptions.indexOfFirst { it.contains("San Juan") }
        if (defaultIndex != -1) {
            binding.phonePrefixSpinner.setSelection(defaultIndex)
        }

        // Si se está editando, pre-cargar los datos en los campos
        currentPerson?.let { person ->
            binding.personNameInput.setText(person.name)
            binding.personLastNameInput.setText(person.lastName)
            // Seleccionar en el spinner el prefijo que coincida con el valor almacenado
            val selectedIndex = phonePrefixOptions.indexOfFirst { it.contains(person.phoneNumberPrefix) }
            if (selectedIndex != -1) {
                binding.phonePrefixSpinner.setSelection(selectedIndex)
            }
            binding.phoneSuffixInput.setText(person.phoneNumberSuffix)
            val typePosition = personTypes.indexOf(person.type)
            if (typePosition >= 0) binding.personTypeSpinner.setSelection(typePosition)
        }

        binding.savePersonButton.setOnClickListener { savePerson() }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Valida que los campos obligatorios estén completos y cumplan las restricciones.
     */
    private fun validateInputs(): Boolean {
        val name = binding.personNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Snackbar.make(binding.root, "El nombre es obligatorio.", Snackbar.LENGTH_LONG).show()
            return false
        }

        val lastName = binding.personLastNameInput.text.toString().trim()
        if (lastName.isEmpty()) {
            Snackbar.make(binding.root, "El apellido es obligatorio.", Snackbar.LENGTH_LONG).show()
            return false
        }

        val selectedPrefixOption = binding.phonePrefixSpinner.selectedItem?.toString() ?: ""
        if (selectedPrefixOption.isEmpty()) {
            Snackbar.make(binding.root, "El prefijo telefónico es obligatorio.", Snackbar.LENGTH_LONG).show()
            return false
        }

        val phoneSuffix = binding.phoneSuffixInput.text.toString().trim()
        if (phoneSuffix.isEmpty()) {
            Snackbar.make(binding.root, "El sufijo telefónico es obligatorio.", Snackbar.LENGTH_LONG).show()
            return false
        }
        if (phoneSuffix.length > 8) {
            Snackbar.make(binding.root, "El sufijo debe tener máximo 8 dígitos.", Snackbar.LENGTH_LONG).show()
            return false
        }

        // Validación de unicidad: se verifica que no exista otra persona con el mismo nombre y apellido.
        if (!isUniquePerson(name, lastName, currentPerson?.id)) {
            Snackbar.make(binding.root, "La persona ya existe.", Snackbar.LENGTH_LONG).show()
            return false
        }
        return true
    }

    /**
     * Comprueba que no exista otra persona con el mismo nombre y apellido (ignorando mayúsculas/minúsculas).
     * Se utiliza la LiveData global de personas del repositorio.
     */
    private fun isUniquePerson(name: String, lastName: String, excludingId: String? = null): Boolean {
        return FirestoreRepository.personsLiveData.value?.none { person ->
            person.name.equals(name, ignoreCase = true) &&
                    person.lastName.equals(lastName, ignoreCase = true) &&
                    person.id != excludingId
        } ?: true
    }

    /**
     * Extrae un objeto Person de los valores ingresados en los campos.
     */
    private fun getPersonFromInputs(): Person {
        val name = binding.personNameInput.text.toString().trim()
        val lastName = binding.personLastNameInput.text.toString().trim()

        // Obtener el prefijo seleccionado y extraer el número
        val selectedPrefixOption = binding.phonePrefixSpinner.selectedItem.toString()
        val phonePrefix = selectedPrefixOption.substringAfter("(").substringBefore(")")

        val phoneSuffix = binding.phoneSuffixInput.text.toString().trim()
        val type = binding.personTypeSpinner.selectedItem.toString()

        return Person(
            id = currentPerson?.id ?: "",
            name = name,
            lastName = lastName,
            phoneNumberPrefix = phonePrefix,
            phoneNumberSuffix = phoneSuffix,
            type = type,
            addresses = listOf()
        )
    }


    /**
     * Guarda la persona (agrega una nueva o actualiza la existente) usando PersonsHelper.
     */
    private fun savePerson() {
        if (!validateInputs()) return

        val personToSave = getPersonFromInputs()

        if (currentPerson == null) {
            // Agregar nueva persona
            personsHelper.addPerson(
                person = personToSave,
                onSuccess = { newPerson ->
                    Snackbar.make(binding.root, "Persona añadida correctamente.", Snackbar.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    Snackbar.make(binding.root, "Error al añadir la persona: ${exception.message}", Snackbar.LENGTH_LONG).show()
                }
            )
        } else {
            // Actualizar persona existente
            personsHelper.updatePerson(
                personId = currentPerson!!.id,
                person = personToSave,
                onSuccess = {
                    Snackbar.make(binding.root, "Persona actualizada correctamente.", Snackbar.LENGTH_LONG).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    Snackbar.make(binding.root, "Error al actualizar la persona: ${exception.message}", Snackbar.LENGTH_LONG).show()
                }
            )
        }
    }
}
