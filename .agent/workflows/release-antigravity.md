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
- `@Suppress("UNUSED_PARAMETER")` for unused parameters
- `@Suppress("DEPRECATION")` for deprecated methods
- Parameter renaming (e.g., `exception` → `_`)
- Code refactoring to eliminate the warning
- Safe call removal when not needed

**The release bundle MUST compile with 0 warnings using `--warning-mode all`**

This is a **NON-NEGOTIABLE REQUIREMENT** for all releases.

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

### Step 3: Clean up comments and debug logs
1. Run `git diff --name-only` to identify Kotlin (`.kt`) files that were modified.
2. For each modified `.kt` file:
   - Identify and remove any remaining `// TODO` or single-line descriptive comments, and pure debug logging (e.g., `Log.d`, `Log.i`) using `multi_replace_file_content`.

### Step 4: Verify debug settings
1. Use `view_file` on `app/src/main/java/com/estaciondulce/app/activities/LoginActivity.kt`.
2. Check if the variable `skipLoginForDebug` is set to `true`. If so, use `multi_replace_file_content` to change it to `false`.

### Step 5: Verify Compilation
1. Check that the build compiles successfully by redirecting the output to a text file so you can read it:
// turbo
2. Run `cd /d c:\Users\Maxi\AndroidStudioProjects\EstacionDulceAppCursor && call gradlew.bat compileDevDebugKotlin > compile_debug_log.txt 2>&1`
3. Wait for the command to finish. Then use `view_file` on `compile_debug_log.txt` to ensure compilation passed. Fix any warnings/errors that appear.

### Step 6: Create Final Release Bundle and Verify Zero Warnings
1. Create the release bundle while redirecting output to capture warnings:
// turbo
2. Run `cd /d c:\Users\Maxi\AndroidStudioProjects\EstacionDulceAppCursor && call gradlew.bat bundleProdRelease --warning-mode all > bundle_release_log.txt 2>&1`
3. Wait for the command to finish. Then use `view_file` on `bundle_release_log.txt`. 
4. Inspect the file carefully for any warnings or errors. Ensure it complies with the **ZERO WARNINGS POLICY**.
(Note: Building the release bundle can take several minutes. Be patient and use `WaitMsBeforeAsync` con `command_status`).

### Step 7: Commit and Push Changes
1. After all verifications pass and the bundle is generated without warnings, list the changed files and request the user's explicit permission to commit.
2. Upon user approval, execute:
// turbo
3. Run `cd /d c:\Users\Maxi\AndroidStudioProjects\EstacionDulceAppCursor && git add . && git commit -m "[DESCRIPTION_IN_ENGLISH]" && git push`
(Ensure the commit message strictly uses English).
