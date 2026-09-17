with open("app/src/main/res/layout/activity_event_edit.xml", "r") as f:
    content = f.read()

import re
content = re.sub(r'<com\.google\.android\.material\.switchmaterial\.SwitchMaterial.*?/>', '', content, flags=re.DOTALL)

with open("app/src/main/res/layout/activity_event_edit.xml", "w") as f:
    f.write(content)
