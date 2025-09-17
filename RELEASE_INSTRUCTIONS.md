# Release Instructions for EstacionDulceApp

## Quick Release Command
When the user says "haz el release" or similar, follow these steps:

### Release Steps
### 1. Update Version Numbers
- Increment `versionCode` by 1 in `app/build.gradle.kts`
- Update `versionName` to next version (e.g., 3.4 → 3.5)

### 2. Update README.md
- Add new entry to "Version History" section
- Write a concise one-line summary of the most important changes
- Follow the format: `- **vX.X** - Brief description of key changes`

### 3. Code Cleanup
- Remove unnecessary comments (keep only function comments)
- Remove unused imports and variables
- Clean up any leftover `//` comments in modified files

### 4. Check Debug Settings
- Verify `private val skipLoginForDebug = false` in `LoginActivity.kt`
- If it's `true`, change it to `false` (prevents accidental debug mode in release)
- Search all `Log.d` and delete them
- Fix ALL warnings found (deprecation, unused variables, etc.)
- Repeat compilation until NO warnings remain
- Only proceed when build is completely clean (0 warnings, 0 errors)

### 5. Compile and Verify Warnings
- Run `.\gradlew assembleDevDebug --warning-mode all` to check for warnings

## Example Release Process

```bash
# 1. Update version in build.gradle.kts
versionCode = 11
versionName = "3.5"

# 2. Add to README.md
- **v3.5** - Added new feature X, fixed bug Y, improved performance Z

# 3. Clean up code
# Remove unnecessary comments, unused imports

# 4. Check debug settings
# Verify skipLoginForDebug = false in LoginActivity.kt

# 5. Compile and check warnings
.\gradlew assembleDevDebug --warning-mode all
```

## Files to Check for Cleanup
- All modified `.kt` files
- Layout files (`.xml`)
- Any new files created during development

## Critical Debug Settings to Verify
- `LoginActivity.kt`: `private val skipLoginForDebug = false`

## Notes
- Keep function comments that explain complex logic
- Remove TODO comments and temporary notes
- Ensure all changes are properly documented in README
- Always test compilation before considering release complete
