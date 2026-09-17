with open("app/src/main/res/layout/activity_event_edit.xml", "r") as f:
    content = f.read()

content = content.replace(
    '<AutoCompleteTextView\n                    android:id="@+id/nameInput"',
    '<AutoCompleteTextView\n                    android:id="@+id/nameInput"\n                    android:completionThreshold="1"'
)

with open("app/src/main/res/layout/activity_event_edit.xml", "w") as f:
    f.write(content)
