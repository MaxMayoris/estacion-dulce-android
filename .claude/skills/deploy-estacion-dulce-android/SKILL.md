---
name: deploy-estacion-dulce-android
description: Full release of the Estación Dulce Android app to the Play Store internal track (version bump, README, release notes, cleanup, compile, tests, publish, Firestore minVersionCode, commit and push). Use whenever the user says deploy, release, publicar, sacar versión or similar for this project.
---

# Deploy Estación Dulce

Run every step below in order. Don't skip any.

## Zero warnings policy (non-negotiable)

The release must not have **any** compiler warnings, however small they look: unused parameters/variables/imports, deprecated methods, unnecessary safe calls, type mismatches, etc.

How to fix them:
- Delete unused parameters entirely (never `@Suppress("UNUSED_PARAMETER")`).
- `@Suppress("DEPRECATION")` is allowed for deprecated methods.
- Rename unused lambda parameters to `_`.
- Remove unnecessary safe calls, or refactor the code so the warning goes away.

## Step 0: Define version and description

1. Read `versionCode` and `versionName` from `app/build.gradle.kts`.
2. If the user didn't give a version or description, work them out from the changes since the last release (`git log` since the last `feat: release` commit, plus `git diff` / `git status`). Use a patch bump for fixes and a minor bump for features.
3. Tell the user the new version and the English description in one line, then go on without waiting, unless something is ambiguous.

## Step 1: Update version numbers

In `app/build.gradle.kts`, add 1 to `versionCode` (or what the user asked for) and set `versionName` to the new version.

## Step 2: Update README.md

Under the `## 🔄 Version History` header, prepend:
`- **v[VERSION_NAME]** - [DESCRIPTION_IN_ENGLISH]`
The description must be **in English only**.

## Step 3: Update Play Store release notes

1. `app/src/main/play/release-notes/en-US/internal.txt`: the same English description (max 500 characters).
2. `app/src/main/play/release-notes/es-419/internal.txt`: the Spanish translation (max 500 characters).

These texts are what users see as "What's new" in the Play Store.

## Step 4: Clean up comments and debug logs

For each modified `.kt` file (`git diff --name-only` plus untracked files), remove leftover `// TODO` comments, single-line descriptive comments that were added, and pure debug logging (`Log.d`, `Log.i`). Leave KDoc that matches the surrounding code's style.

## Step 5: Verify debug settings

In `app/src/main/java/com/estaciondulce/app/activities/LoginActivity.kt`, `skipLoginForDebug` **must be `false`**. If it is `true`, change it.

## Step 6: Compile

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew compileDevDebugKotlin --warning-mode all > compile_debug_log.txt 2>&1
```

Read `compile_debug_log.txt`. Fix every error **and every warning** (lines starting with `w:`), then compile again until it is clean.

## Step 6.5: Unit and UI tests (Robolectric)

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew testDevDebugUnitTest > test_log.txt 2>&1
```

Read `test_log.txt`. All tests must pass. Fix any failure before you go on.

## Step 7: Publish to the Play Store (build + sign + upload)

Compiles the release bundle, signs it and uploads it to the **internal** track. It takes 2-3 minutes, so run it in the background or with a long timeout.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew publishProdReleaseBundle > publish_log.txt 2>&1
```

Read `publish_log.txt` and check that it shows:
- `App Bundle upload complete`
- `BUILD SUCCESSFUL`

If there are warnings or errors, fix them and run it again.

## Step 7.5: Update minVersionCode in Firestore

```bash
node scripts/update_firestore_version.js
```

The output must show `✅ Firestore actualizado con éxito.`

## Step 8: Commit and push

Asking for a deploy already authorizes this commit and push, so don't ask again.

1. Delete the temporary logs:
   ```bash
   rm -f bundle_release_log.txt compile_debug_log.txt publish_log.txt test_log.txt
   ```
2. Check `git status` and make sure nothing that shouldn't be committed gets in (stray logs, scratch scripts).
3. Commit (message in English) and push:
   ```bash
   git add . && git commit -m "feat: release v[VERSION] - [DESCRIPTION_IN_ENGLISH]" && git push
   ```
4. In your final reply, give a summary of the changed files and a short changelog for the release.

## Infrastructure

- **Keystore**: `../../estacion-dulce-keys/my-release-key.jks` (relative to `app/`). Its credentials are in `local.properties` (gitignored).
- **Play Store credentials**: `../../estacion-dulce-keys/play-store-credentials.json` (gitignored), used by Gradle Play Publisher.
- **Java**: always use Android Studio's JDK: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- **Track**: releases go to `internal`. Promotion to production is done by hand in Play Console.
