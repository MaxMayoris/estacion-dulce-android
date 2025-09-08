package com.estaciondulce.app.activities

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityPersonEditBinding
import com.estaciondulce.app.helpers.PersonsHelper
import com.estaciondulce.app.models.Person
import com.estaciondulce.app.repository.FirestoreRepository
import com.estaciondulce.app.utils.CustomToast

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
        @Suppress("DEPRECATION")
        currentPerson = intent.getParcelableExtra<Person>("PERSON")
        supportActionBar?.title = if (currentPerson != null) "Editar Persona" else "Agregar Persona"

        // Configurar el Spinner para el tipo de persona (Cliente o Proveedor)
        val personTypes = listOf("Cliente", "Proveedor")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, personTypes)
        binding.personTypeSpinner.setAdapter(spinnerAdapter)

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
        val phonePrefixAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, phonePrefixOptions)
        binding.phonePrefixSpinner.setAdapter(phonePrefixAdapter)

        // Establecer el valor por defecto a "San Juan (264)"
        val defaultPrefix = phonePrefixOptions.find { it.contains("San Juan") }
        if (defaultPrefix != null) {
            binding.phonePrefixSpinner.setText(defaultPrefix, false)
        }

        // Si se está editando, pre-cargar los datos en los campos
        currentPerson?.let { person ->
            binding.personNameInput.setText(person.name)
            binding.personLastNameInput.setText(person.lastName)
            // Seleccionar en el spinner el prefijo que coincida con el valor almacenado
            val selectedPrefix = phonePrefixOptions.find { it.contains(person.phoneNumberPrefix) }
            if (selectedPrefix != null) {
                binding.phonePrefixSpinner.setText(selectedPrefix, false)
            }
            binding.phoneSuffixInput.setText(person.phoneNumberSuffix)
            binding.personTypeSpinner.setText(person.type, false)
        }

        binding.savePersonButton.setOnClickListener { savePerson() }
        
        binding.copyPhoneButton.setOnClickListener { copyPhoneNumber() }
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
            CustomToast.showError(this, "El nombre es obligatorio.")
            return false
        }

        val lastName = binding.personLastNameInput.text.toString().trim()
        if (lastName.isEmpty()) {
            CustomToast.showError(this, "El apellido es obligatorio.")
            return false
        }

        val selectedPrefixOption = binding.phonePrefixSpinner.text.toString()
        if (selectedPrefixOption.isEmpty()) {
            CustomToast.showError(this, "El prefijo telefónico es obligatorio.")
            return false
        }

        val phoneSuffix = binding.phoneSuffixInput.text.toString().trim()
        if (phoneSuffix.isEmpty()) {
            CustomToast.showError(this, "El sufijo telefónico es obligatorio.")
            return false
        }
        if (phoneSuffix.length > 8) {
            CustomToast.showError(this, "El sufijo debe tener máximo 8 dígitos.")
            return false
        }

        // Validación de unicidad: se verifica que no exista otra persona con el mismo nombre y apellido.
        if (!isUniquePerson(name, lastName, currentPerson?.id)) {
            CustomToast.showError(this, "La persona ya existe.")
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
        val selectedPrefixOption = binding.phonePrefixSpinner.text.toString()
        val phonePrefix = selectedPrefixOption.substringAfter("(").substringBefore(")")

        val phoneSuffix = binding.phoneSuffixInput.text.toString().trim()
        val type = binding.personTypeSpinner.text.toString()

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
     * Copia el número de teléfono completo al portapapeles.
     */
    private fun copyPhoneNumber() {
        val prefixText = binding.phonePrefixSpinner.text.toString()
        val suffixText = binding.phoneSuffixInput.text.toString().trim()
        
        if (prefixText.isEmpty() || suffixText.isEmpty()) {
            CustomToast.showError(this, "Complete el número de teléfono antes de copiar.")
            return
        }
        
        // Extraer el prefijo sin paréntesis
        val prefix = prefixText.substringAfter("(").substringBefore(")")
        val fullNumber = "$prefix$suffixText"
        
        // Copiar al portapapeles
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Número de teléfono", fullNumber)
        clipboard.setPrimaryClip(clip)
        
        CustomToast.showSuccess(this, "Número copiado: $fullNumber")
    }

    /**
     * Guarda la persona (agrega una nueva o actualiza la existente) usando PersonsHelper.
     */
    private fun savePerson() {
        if (!validateInputs()) return

        val personToSave = getPersonFromInputs()

        if (currentPerson == null) {
            personsHelper.addPerson(
                person = personToSave,
                onSuccess = { _ ->
                    CustomToast.showSuccess(this, "Persona añadida correctamente.")
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    CustomToast.showError(this, "Error al añadir la persona: ${exception.message}")
                }
            )
        } else {
            personsHelper.updatePerson(
                personId = currentPerson!!.id,
                person = personToSave,
                onSuccess = {
                    CustomToast.showSuccess(this, "Persona actualizada correctamente.")
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    CustomToast.showError(this, "Error al actualizar la persona: ${exception.message}")
                }
            )
        }
    }
}
