package com.estaciondulce.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.estaciondulce.app.databinding.ActivityRecipeEditBinding
import com.estaciondulce.app.models.Recipe
import com.google.firebase.firestore.FirebaseFirestore

class RecipeEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecipeEditBinding
    private var recipe: Recipe? = null
    private val allCategories = mutableMapOf<String, String>() // Map of category ID to name
    private val selectedCategories = mutableSetOf<String>() // Use Set to prevent duplicates

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecipeEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable back button in the action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit Recipe"

        // Get the recipe from the intent
        recipe = intent.getParcelableExtra("recipe")

        // Prefill fields with recipe data
        recipe?.let { prefillFields(it) }

        // Fetch categories from Firestore
        fetchCategories()

        // Save button logic
        binding.saveRecipeButton.setOnClickListener {
            saveRecipe()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun prefillFields(recipe: Recipe?) {
        binding.recipeNameInput.setText(recipe?.name.orEmpty())
        binding.recipeCostInput.setText(recipe?.cost?.toString().orEmpty())
        binding.recipeSuggestedPriceInput.setText(recipe?.suggestedPrice?.toString().orEmpty())
        binding.recipeSalePriceInput.setText(recipe?.salePrice?.toString().orEmpty())
        binding.recipeOnSaleCheckbox.isChecked = recipe?.onSale ?: false

        selectedCategories.clear() // Clear any existing categories

        recipe?.categories?.let {
            Log.d("RecipeEditActivity", "Categories in Recipe: $it") // Log categories in the recipe
            selectedCategories.addAll(it)
        }

        Log.d("RecipeEditActivity", "Selected Categories after prefill: $selectedCategories")
        updateCategoryTags() // Update the tags UI
    }

    private fun fetchCategories() {
        FirebaseFirestore.getInstance().collection("categories")
            .get()
            .addOnSuccessListener { documents ->
                val availableCategories = mutableListOf<String>() // List of category names for dropdown
                for (document in documents) {
                    val id = document.id
                    val name = document.getString("name") ?: "Unknown"
                    allCategories[id] = name

                    // Log fetched categories
                    Log.d("RecipeEditActivity", "Fetched Category: ID=$id, Name=$name")

                    // Only add categories not already in selectedCategories to the dropdown
                    if (!selectedCategories.contains(id)) {
                        availableCategories.add(name)
                    }
                }

                Log.d("RecipeEditActivity", "Available Categories: $availableCategories")
                setupCategorySelector(availableCategories)

                // Update tags after allCategories is populated
                updateCategoryTags()
            }
    }

    private fun setupCategorySelector(categoryNames: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categoryNames)
        binding.categorySelector.adapter = adapter

        // Use a flag to ignore the initial selection
        var isSpinnerInitialized = false

        binding.categorySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!isSpinnerInitialized) {
                    isSpinnerInitialized = true // Ignore the first call
                    return
                }

                val selectedName = adapter.getItem(position) ?: return

                // Ignore placeholder option
                if (selectedName == "No categories available") return

                val selectedId = allCategories.entries.find { it.value == selectedName }?.key ?: return

                // Add to selected categories if not already added
                if (selectedCategories.add(selectedId)) {
                    adapter.remove(selectedName) // Remove from dropdown
                    updateCategoryTags() // Update tags
                }

                // Update dropdown placeholder visibility
                updateDropdownAndPlaceholder(adapter)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        updateDropdownAndPlaceholder(adapter)
    }

    private fun updateCategoryTags() {
        binding.categoryTagsContainer.removeAllViews() // Clear existing tags

        Log.d("RecipeEditActivity", "Updating Tags: $selectedCategories")

        for (categoryId in selectedCategories) {
            val categoryName = allCategories[categoryId] ?: continue

            // Log each tag being added
            Log.d("RecipeEditActivity", "Adding Tag: ID=$categoryId, Name=$categoryName")

            // Create a tag view
            val tagView = LayoutInflater.from(this).inflate(R.layout.item_category_tag, binding.categoryTagsContainer, false)
            val textView = tagView.findViewById<TextView>(R.id.categoryTagName)
            val deleteButton = tagView.findViewById<TextView>(R.id.categoryTagDelete)

            textView.text = categoryName
            deleteButton.setOnClickListener {
                // Remove category and update UI
                Log.d("RecipeEditActivity", "Removing Tag: ID=$categoryId, Name=$categoryName")
                selectedCategories.remove(categoryId)
                allCategories[categoryId]?.let { newCategoryName ->
                    (binding.categorySelector.adapter as? ArrayAdapter<String>)?.add(newCategoryName)
                }
                updateCategoryTags() // Refresh tags
                updateDropdownAndPlaceholder(binding.categorySelector.adapter as ArrayAdapter<String>)
            }

            binding.categoryTagsContainer.addView(tagView)
        }

        // Show placeholder if no categories are selected
        toggleCategoryTagsPlaceholder(selectedCategories.isEmpty())
    }

    private fun updateDropdownAndPlaceholder(adapter: ArrayAdapter<String>) {
        if (adapter.count == 0) {
            if (adapter.getPosition("No categories available") == -1) {
                adapter.add("No categories available")
            }
            binding.categorySelector.isEnabled = false
        } else {
            adapter.remove("No categories available")
            binding.categorySelector.isEnabled = true
        }
    }


    private fun toggleCategoryTagsPlaceholder(isEmpty: Boolean) {
        binding.categoryTagsPlaceholder.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun saveRecipe() {
        val updatedRecipe = Recipe(
            id = recipe?.id ?: "", // Use existing ID or generate a new one
            name = binding.recipeNameInput.text.toString(),
            cost = binding.recipeCostInput.text.toString().toDoubleOrNull() ?: 0.0,
            suggestedPrice = binding.recipeSuggestedPriceInput.text.toString().toDoubleOrNull() ?: 0.0,
            salePrice = binding.recipeSalePriceInput.text.toString().toDoubleOrNull() ?: 0.0,
            onSale = binding.recipeOnSaleCheckbox.isChecked,
            categories = selectedCategories.toList(),
            sections = listOf() // TODO: Handle sections in the future
        )

        val db = FirebaseFirestore.getInstance()
        val recipeRef = db.collection("recipes")

        if (updatedRecipe.id.isEmpty()) {
            // New recipe: Add to Firestore
            recipeRef.add(updatedRecipe)
                .addOnSuccessListener { documentReference ->
                    updatedRecipe.id = documentReference.id // Assign Firestore ID
                    sendResultAndFinish(updatedRecipe)
                }
                .addOnFailureListener { e ->
                    Log.e("RecipeEditActivity", "Error adding recipe: ${e.message}")
                    Toast.makeText(this, "Error saving recipe.", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Existing recipe: Update Firestore document
            recipeRef.document(updatedRecipe.id)
                .set(updatedRecipe)
                .addOnSuccessListener {
                    sendResultAndFinish(updatedRecipe)
                }
                .addOnFailureListener { e ->
                    Log.e("RecipeEditActivity", "Error updating recipe: ${e.message}")
                    Toast.makeText(this, "Error saving recipe.", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun sendResultAndFinish(updatedRecipe: Recipe) {
        val resultIntent = Intent().apply {
            putExtra("updatedRecipe", updatedRecipe)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }


}
