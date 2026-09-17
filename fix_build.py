with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace('dependsOn("testDebugUnitTest")', 'dependsOn("testDevDebugUnitTest", "testProdDebugUnitTest")')

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
