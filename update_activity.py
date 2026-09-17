with open("app/src/main/java/com/estaciondulce/app/activities/EventEditActivity.kt", "r") as f:
    content = f.read()

# I will observe categoriesLiveData so that if it changes, the dropdown updates.
# Also I'll check if they had "event" instead of "isEvent" in Firestore. Wait, we can't check that without adding a Map property.
old_autocomplete = """    private fun setupEventNameAutocomplete() {
        val categories = FirestoreRepository.categoriesLiveData.value ?: emptyList()
        val eventNames = categories.filter { it.isEvent }.map { it.name }.distinct().sorted()
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, eventNames)
        val actv = binding.nameInput as AutoCompleteTextView
        actv.setAdapter(adapter)
        
        actv.setOnClickListener {
            actv.showDropDown()
        }
    }"""

new_autocomplete = """    private fun setupEventNameAutocomplete() {
        FirestoreRepository.categoriesLiveData.observe(this) { categories ->
            // In case old data has 'event: true' we'd have to migrate it manually or assume the new ones use 'isEvent'
            // We just filter by isEvent == true (or we can just show all names from eventsLiveData)
            val events = FirestoreRepository.eventsLiveData.value ?: emptyList()
            val namesFromEvents = events.map { it.name }
            val namesFromCategories = categories.filter { it.isEvent }.map { it.name }
            
            val eventNames = (namesFromEvents + namesFromCategories).distinct().sorted()
            
            val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, eventNames)
            val actv = binding.nameInput as AutoCompleteTextView
            actv.setAdapter(adapter)
            
            actv.setOnClickListener {
                actv.showDropDown()
            }
            
            actv.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) actv.showDropDown()
            }
        }
    }"""

content = content.replace(old_autocomplete, new_autocomplete)

with open("app/src/main/java/com/estaciondulce/app/activities/EventEditActivity.kt", "w") as f:
    f.write(content)
