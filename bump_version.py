with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace('versionCode = 52', 'versionCode = 53')
content = content.replace('versionName = "10.5"', 'versionName = "10.6"')

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
