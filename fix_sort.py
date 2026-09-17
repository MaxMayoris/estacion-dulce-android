with open("app/src/main/java/com/estaciondulce/app/fragments/EventFragment.kt", "r") as f:
    content = f.read()

content = content.replace(
    "val sortedList = events.sortedBy { it.name }",
    "val sortedList = events.sortedByDescending { it.startDate?.time ?: 0L }"
)

with open("app/src/main/java/com/estaciondulce/app/fragments/EventFragment.kt", "w") as f:
    f.write(content)
