with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace("compileSdk = 35", "compileSdk = 36")
content = content.replace("targetSdk = 35", "targetSdk = 36")

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
