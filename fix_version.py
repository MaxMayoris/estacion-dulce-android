with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace("versionCode = 53", "versionCode = 54")

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
