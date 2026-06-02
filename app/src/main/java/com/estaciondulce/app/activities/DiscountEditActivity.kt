package com.estaciondulce.app.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityDiscountEditBinding
import com.estaciondulce.app.helpers.RecipesHelper
import com.estaciondulce.app.models.parcelables.Recipe
import com.estaciondulce.app.utils.CustomLoader
import com.estaciondulce.app.utils.CustomToast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DiscountEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiscountEditBinding
    private lateinit var loader: CustomLoader
    private var recipe: Recipe? = null
    private val recipesHelper = RecipesHelper()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private var selectedEndDate: Date? = null

    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscountEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loader = CustomLoader(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Configurar Descuento"

        @Suppress("DEPRECATION")
        recipe = intent.getParcelableExtra<Recipe>("recipe")

        if (recipe == null) {
            CustomToast.showError(this, "Error al cargar la receta")
            finish()
            return
        }

        setupUI()
        setupListeners()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @SuppressLint("DefaultLocale")
    private fun setupUI() {
        recipe?.let { r ->
            binding.recipeNameInput.setText(r.name)
            binding.recipeCostInput.setText(String.format("%.2f", r.cost))
            binding.recipeSuggestedPriceInput.setText(String.format("%.2f", r.suggestedPrice))
            binding.recipeSalePriceInput.setText(String.format("%.2f", r.salePrice))
            binding.recipeUnitInput.setText(r.unit.toString())
            binding.recipeOnSaleCheckbox.isChecked = r.onSale

            binding.recipeOnDiscountCheckbox.isChecked = r.onDiscount
            if (r.discountPrice > 0.0) {
                binding.recipeDiscountPriceInput.setText(String.format("%.2f", r.discountPrice))
            } else {
                binding.recipeDiscountPriceInput.setText(String.format("%.2f", r.salePrice))
            }

            selectedEndDate = r.discountEndDate
            val dateLimitActive = selectedEndDate != null
            binding.recipeDiscountEndDateCheckbox.isChecked = dateLimitActive
            if (selectedEndDate != null) {
                binding.recipeDiscountEndDateInput.setText(dateFormat.format(selectedEndDate!!))
            } else {
                binding.recipeDiscountEndDateInput.setText("")
            }

            updateDiscountVisibility(r.onDiscount)
            updateProfitPercentages()
        }
    }

    private fun setupListeners() {
        binding.recipeOnDiscountCheckbox.setOnCheckedChangeListener { _, isChecked ->
            updateDiscountVisibility(isChecked)
            updateProfitPercentages()
        }

        binding.recipeDiscountEndDateCheckbox.setOnCheckedChangeListener { _, isChecked ->
            binding.discountEndDateLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked && selectedEndDate == null) {
                showDatePickerDialog()
            }
        }

        binding.recipeDiscountEndDateInput.setOnClickListener {
            showDatePickerDialog()
        }

        binding.recipeDiscountPriceInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateProfitPercentages()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.saveDiscountButton.setOnClickListener {
            saveDiscount()
        }
    }

    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        selectedEndDate?.let {
            calendar.time = it
        }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val newCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, selectedYear)
                    set(Calendar.MONTH, selectedMonth)
                    set(Calendar.DAY_OF_MONTH, selectedDay)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                val newDate = newCalendar.time
                selectedEndDate = newDate
                binding.recipeDiscountEndDateInput.setText(dateFormat.format(newDate))
            },
            year,
            month,
            day
        )
        datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000
        datePickerDialog.show()
    }

    private fun updateDiscountVisibility(isDiscounted: Boolean) {
        if (isDiscounted) {
            binding.discountPriceLayout.visibility = View.VISIBLE
            binding.recipeDiscountPriceInput.isEnabled = true
            binding.recipeDiscountEndDateCheckbox.visibility = View.VISIBLE
            val limitChecked = binding.recipeDiscountEndDateCheckbox.isChecked
            binding.discountEndDateLayout.visibility = if (limitChecked) View.VISIBLE else View.GONE
        } else {
            binding.discountPriceLayout.visibility = View.GONE
            binding.recipeDiscountPriceInput.isEnabled = false
            binding.recipeDiscountEndDateCheckbox.visibility = View.GONE
            binding.discountEndDateLayout.visibility = View.GONE
        }
    }

    @SuppressLint("DefaultLocale")
    private fun updateProfitPercentages() {
        recipe?.let { r ->
            val originalProfit = if (r.cost > 0) ((r.salePrice - r.cost) / r.cost) * 100 else 0.0
            binding.profitPercentageValue.text = String.format("%.1f%%", originalProfit)

            val discountPriceInputText = binding.recipeDiscountPriceInput.text.toString()
            val discountPrice = discountPriceInputText.toDoubleOrNull() ?: 0.0
            
            if (binding.recipeOnDiscountCheckbox.isChecked) {
                val discountProfit = if (r.cost > 0) ((discountPrice - r.cost) / r.cost) * 100 else 0.0
                binding.discountProfitPercentageValue.text = String.format("%.1f%%", discountProfit)
                
                if (discountProfit < 0) {
                    binding.discountProfitPercentageValue.setTextColor(resources.getColor(android.R.color.holo_red_dark, theme))
                } else {
                    binding.discountProfitPercentageValue.setTextColor(resources.getColor(com.estaciondulce.app.R.color.success_green, theme))
                }
            } else {
                binding.discountProfitPercentageValue.text = "-"
                binding.discountProfitPercentageValue.setTextColor(resources.getColor(com.estaciondulce.app.R.color.text_secondary, theme))
            }
        }
    }

    private fun saveDiscount() {
        recipe?.let { r ->
            val isDiscounted = binding.recipeOnDiscountCheckbox.isChecked
            val discountPriceInputText = binding.recipeDiscountPriceInput.text.toString()
            val discountPrice = if (isDiscounted) (discountPriceInputText.toDoubleOrNull() ?: 0.0) else 0.0

            if (isDiscounted && discountPrice <= 0.0) {
                CustomToast.showError(this, "El precio de descuento debe ser mayor a 0")
                return
            }

            val finalEndDate = if (isDiscounted && binding.recipeDiscountEndDateCheckbox.isChecked) {
                if (selectedEndDate == null) {
                    CustomToast.showError(this, "Por favor seleccione una fecha límite")
                    return
                }
                selectedEndDate
            } else {
                null
            }

            val updatedRecipe = r.copy(
                onDiscount = isDiscounted,
                discountPrice = discountPrice,
                discountEndDate = finalEndDate
            )

            loader.show()
            recipesHelper.updateRecipe(
                recipeId = r.id,
                recipe = updatedRecipe,
                onSuccess = {
                    loader.hide()
                    CustomToast.showSuccess(this, "Descuento configurado exitosamente")
                    setResult(Activity.RESULT_OK)
                    finish()
                },
                onError = { exception ->
                    loader.hide()
                    CustomToast.showError(this, "Error al guardar: ${exception.message}")
                }
            )
        }
    }
}
