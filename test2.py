import re
with open("app/src/main/res/layout/activity_home.xml", "r") as f:
    content = f.read()

start_str = "<com.google.android.material.card.MaterialCardView"
end_str = "</com.google.android.material.card.MaterialCardView>"
start_idx = content.find(start_str)
end_idx = content.find(end_str, start_idx) + len(end_str)
card_xml = content[start_idx:end_idx]

print(repr(card_xml))
id_match = re.search(r'android:id="@+id/(.*?Card)"', card_xml)
print(id_match)
