---
description: Antigravity Release Instructions and Zero Warnings Policy for EstacionDulceApp
---

## 🚨 CRITICAL RELEASE RULE - ZERO WARNINGS POLICY

**UNDER NO CIRCUMSTANCES SHOULD ANY WARNINGS BE LEFT IN THE RELEASE, NO MATTER HOW MINIMAL THEY MAY SEEM.**

This includes but is not limited to:
- Unused parameters
- Deprecated methods  
- Unnecessary safe calls
- Unused variables
- Unused imports
- Type mismatches
- Any other compiler warnings

**ALL WARNINGS MUST BE FIXED** using appropriate solutions:
- Delete unused parameters completely (do not use `@Suppress("UNUSED_PARAMETER")`)
- `@Suppress("DEPRECATION")` for deprecated methods
- Parameter renaming (e.g., `exception` → `_`)
- Code refactoring to eliminate the warning
- Safe call removal when not needed

**The release bundle MUST compile with 0 warnings using `--warning-mode all`**

This is a **NON-NEGOTIABLE REQUIREMENT** for all releases.

---

## Antigravity Release Process

When the user asks you to perform a release using this workflow, execute the following specific steps sequentially through your tools:

### Step 1: Update version numbers
1. Use `view_file` on `app/build.gradle.kts` to check the current `versionCode` and `versionName`.
2. Increment the `versionCode` by 1 (or as requested) and update the `versionName` to the new version.
3. Use `multi_replace_file_content` to accurately update the file.

### Step 2: Update README.md
1. Use `view_file` on `README.md` and find the `## 🔄 Version History` section.
2. Underneath the version history header, prepend a new line with the format: `- **v[VERSION_NAME]** - [DESCRIPTION_IN_ENGLISH]`.
3. Use `multi_replace_file_content` to insert the new entry. Make sure the description is **strictly in English**.

### Step 3: Update Play Store release notes
1. Update `app/src/main/play/release-notes/en-US/internal.txt` with the same English description used in README.md (max 500 chars).
2. Update `app/src/main/play/release-notes/es-419/internal.txt` with a Spanish translation of the same description.
3. These files are what users see as "What's new" in the Play Store listing.

### Step 4: Clean up comments and debug logs
1. Run `git diff --name-only` to identify Kotlin (`.kt`) files that were modified.
2. For each modified `.kt` file:
   - Identify and remove any remaining `// TODO` or single-line descriptive comments, and pure debug logging (e.g., `Log.d`, `Log.i`) using `multi_replace_file_content`.

### Step 5: Verify debug settings
1. Use `view_file` on `app/src/main/java/com/estaciondulce/app/activities/LoginActivity.kt`.
2. Check if the variable `skipLoginForDebug` is set to `true`. If so, use `multi_replace_file_content` to change it to `false`.

### Step 6: Verify Compilation
1. Check that the build compiles successfully by redirecting the output to a text file so you can read it:
// turbo
2. Run `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew compileDevDebugKotlin > compile_debug_log.txt 2>&1`
3. Wait for the command to finish. Then use `view_file` on `compile_debug_log.txt` to ensure compilation passed. Fix any warnings/errors that appear.

### Step 7: Publish to Play Store (Build + Upload in one step)
This single command compiles the release bundle, signs it, and uploads it directly to the **internal testing** track on Google Play:
// turbo
1. Run `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew publishProdReleaseBundle > publish_log.txt 2>&1`
2. Wait for the command to finish. Then use `view_file` on `publish_log.txt`.
3. Verify it shows:
   - `App Bundle upload complete`
   - `BUILD SUCCESSFUL`
4. If there are warnings or errors, fix them and re-run.
(Note: This step can take 2-3 minutes. Be patient.)

### Step 7.5: Update Firestore config (Auto)
Run the script to automatically update `minVersionCode` in Firestore to match the newly released `versionCode`:
1. Run `node scripts/update_firestore_version.js`
2. Check the output to ensure it prints `✅ Firestore actualizado con éxito.`

### Step 8: Commit and Push Changes (Auto)
Do NOT ask the user for permission. Execute the commands directly:
1. Delete temporary log files: `rm -f bundle_release_log.txt compile_debug_log.txt publish_log.txt`
2. Commit and push:
// turbo
3. Run `git add . && git commit -m "feat: release v[VERSION] - [DESCRIPTION_IN_ENGLISH]" && git push`
   (Ensure the commit message strictly uses English).
4. After pushing, output a summary of the changed files and a concise changelog of the release in your final response.

---

## Infrastructure Notes

- **Keystore**: Located at `../../estacion-dulce-keys/my-release-key.jks` (relative to `app/`). Credentials read from `local.properties` (gitignored).
- **Play Store credentials**: Located at `../../estacion-dulce-keys/play-store-credentials.json` (gitignored). Used by Gradle Play Publisher to authenticate with the Google Play Developer API.
- **Java**: Always set `JAVA_HOME` to Android Studio's bundled JDK: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`
- **Track**: Releases go to `internal` testing track. To promote to production, do it manually in Play Console.
