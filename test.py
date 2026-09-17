with open("app/src/main/res/layout/activity_home.xml", "r") as f:
    content = f.read()
print(content.count("<com.google.android.material.card.MaterialCardView"))
