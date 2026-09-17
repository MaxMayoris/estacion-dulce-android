with open("README.md", "r") as f:
    content = f.read()

import re
# Insert "- **v10.6** - Automatic test execution on build, improved event creation UI and sorting"
new_entry = "- **v10.6** - Automatic test execution on build, improved event creation UI and sorting\n"
content = re.sub(r'(## 🔄 Version History\n\n)', r'\1' + new_entry, content)

with open("README.md", "w") as f:
    f.write(content)
