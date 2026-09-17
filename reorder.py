import re

with open("app/src/main/res/layout/activity_home.xml", "r") as f:
    content = f.read()

# Extract cards by looking for <com.google.android.material.card.MaterialCardView and </com.google.android.material.card.MaterialCardView>
cards = {}
start_str = "<com.google.android.material.card.MaterialCardView"
end_str = "</com.google.android.material.card.MaterialCardView>"

curr_pos = 0
while True:
    start_idx = content.find(start_str, curr_pos)
    if start_idx == -1:
        break
    end_idx = content.find(end_str, start_idx) + len(end_str)
    card_xml = content[start_idx:end_idx]
    
    id_match = re.search(r'android:id="@\+id/(.*?Card)"', card_xml)
    if id_match:
        cards[id_match.group(1)] = card_xml
    curr_pos = end_idx

target_order = [
    "productsCard", "recipesCard",
    "personsCard", "movementsCard",
    "kitchenOrdersCard", "shipmentsCard",
    "discountsCard", "eventsCard",
    "timesheetCard", "statisticsCard"
]

if all(c in cards for c in target_order):
    grid_start = content.find("<GridLayout")
    grid_end = content.find("</GridLayout>")

    before_grid = content[:grid_start]
    grid_header = content[grid_start:content.find(">", grid_start)+1] + "\n            "
    
    new_cards = "\n            ".join(cards[cid] for cid in target_order)
    
    after_grid = "\n        " + content[grid_end:]
    
    with open("app/src/main/res/layout/activity_home.xml", "w") as f:
        f.write(before_grid + grid_header + new_cards + after_grid)
    print("Done")
else:
    print("Missing:", [c for c in target_order if c not in cards])
