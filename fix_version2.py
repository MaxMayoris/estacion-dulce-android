with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace('versionCode = 54', 'versionCode = 55')
content = content.replace('versionName = "10.6"', 'versionName = "10.6.1"')

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
