with open("app/src/main/java/com/estaciondulce/app/activities/EventEditActivity.kt", "r") as f:
    content = f.read()

content = content.replace("private fun setupDatePickers()\n        setupEventNameAutocomplete() {", "private fun setupDatePickers() {")

with open("app/src/main/java/com/estaciondulce/app/activities/EventEditActivity.kt", "w") as f:
    f.write(content)
