---
description: Release instructions and Zero Warnings Policy for EstacionDulceApp
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

## Quick Release Command
When the user says "haz el release" or similar, follow these steps:

### Release Steps

#### 1. Execute Automated Release Script
Run the automated release script with version parameters:
```powershell
.\scripts\release.ps1 -VersionName "X.X" -VersionCode "XX" -Description "Brief description of changes"
```

The script automatically handles:
- ✅ **Update version numbers** in `app/build.gradle.kts`
- ✅ **Update README.md** with new version entry
- ✅ **Remove single-line comments** from modified .kt files
- ✅ **Remove debug logs** from modified files
- ✅ **Verify debug settings** (skipLoginForDebug = false)
- ✅ **Compile and check** for errors
- ✅ **Verify warnings** in release bundle
- ✅ **Create final release bundle**
- ✅ **Show git status** for review

#### 2. Review Changes and Commit
- **CRITICAL**: This step requires explicit user authorization.
- Review the changes with: `git diff`.
- List all files that would be committed using `git status`.
- Wait for user confirmation before proceeding.
- Once authorized, execute:
  ```bash
  git add .
  git commit -m "DESCRIPTION_FROM_SCRIPT"
  git push
  ```
- **Commit message format**: Use the description provided to the script (without version prefix).
