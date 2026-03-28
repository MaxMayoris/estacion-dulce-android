package com.estaciondulce.app.activities

import android.annotation.SuppressLint
import android.app.Activity
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

class DiscountEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiscountEditBinding
    private lateinit var loader: CustomLoader
    private var recipe: Recipe? = null
    private val recipesHelper = RecipesHelper()

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

            updateDiscountVisibility(r.onDiscount)
            updateProfitPercentages()
        }
    }

    private fun setupListeners() {
        binding.recipeOnDiscountCheckbox.setOnCheckedChangeListener { _, isChecked ->
            updateDiscountVisibility(isChecked)
            updateProfitPercentages()
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

    private fun updateDiscountVisibility(isDiscounted: Boolean) {
        if (isDiscounted) {
            binding.discountPriceLayout.visibility = View.VISIBLE
            binding.recipeDiscountPriceInput.isEnabled = true
        } else {
            binding.discountPriceLayout.visibility = View.GONE
            binding.recipeDiscountPriceInput.isEnabled = false
        }
    }

    @SuppressLint("DefaultLocale")
    private fun updateProfitPercentages() {
        recipe?.let { r ->
            // Original profit percentage
            val originalProfit = if (r.cost > 0) ((r.salePrice - r.cost) / r.cost) * 100 else 0.0
            binding.profitPercentageValue.text = String.format("%.1f%%", originalProfit)

            // Discount profit percentage
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

            // Create updated recipe
            val updatedRecipe = r.copy(
                onDiscount = isDiscounted,
                discountPrice = discountPrice
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
