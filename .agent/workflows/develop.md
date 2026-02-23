---
description: Development rules for EstacionDulceApp - Coding standards, compilation, and logging
---

Follow these rules during development of EstacionDulceApp:

## Code Comments
- **NO add single-line comments** (`//`) during development - they create visual noise.
- **Add function descriptions in English** using block comments (`/* */`) for important functions.
- **Use descriptive variable/function names** instead of explanatory comments.

## Function Documentation
- **Important functions must have English descriptions** using block comments:
```kotlin
/**
 * Cleans WhatsApp phone numbers by removing spaces, country codes, and mobile prefixes
 * @param phoneText Raw phone number text from clipboard
 * @return Cleaned phone number with only digits
 */
private fun cleanWhatsAppPhoneNumber(phoneText: String): String
```

## Compilation Verification
// turbo
1. **ALWAYS run compilation check** after completing instructions that modify code.
2. **Use command**: `cmd /c run_compilation.bat`
3. **DO NOT finish until ALL compilation errors are resolved**.
4. **Fix ALL warnings and errors** before considering task complete.

## Development Workflow
1. Make code changes.
2. Run `cmd /c run_compilation.bat`.
3. Check `build_logs/build_result.txt` (must be 0) and `build_logs/build_output.log` for errors.
4. Fix any compilation errors/warnings.
5. Repeat until compilation is clean.
6. Only then consider the task complete.

## Debug Logging
- **ALWAYS use Log.d() instead of println()** for debug logging.
- **When adding debug logs** to troubleshoot functionality issues, **ALWAYS provide the filter command** for the user to see the logs.
- **Use format**: `adb logcat | grep "TAG_NAME"` or `adb logcat | findstr "TAG_NAME"`.
- **Example**:
```kotlin
// Add debug log
Log.d("PhoneCleaner", "Input: $phoneText, Output: $cleaned")

// Tell user the filter command:
// "Use this filter to see debug logs: adb logcat | findstr PhoneCleaner"
```
