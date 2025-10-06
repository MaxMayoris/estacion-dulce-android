# Complete Release Script for EstacionDulceApp
# Usage: powershell -ExecutionPolicy Bypass -File scripts/release.ps1

param(
    [string]$VersionName = "",
    [string]$VersionCode = "",
    [string]$Description = ""
)

Write-Host "=== EstacionDulceApp Release Script ===" -ForegroundColor Magenta
Write-Host ""

# Check if version parameters are provided
if ([string]::IsNullOrEmpty($VersionName) -or [string]::IsNullOrEmpty($VersionCode) -or [string]::IsNullOrEmpty($Description)) {
    Write-Host "Usage: .\scripts\release.ps1 -VersionName '5.7' -VersionCode '25' -Description 'Brief description of changes'" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Example:" -ForegroundColor Cyan
    Write-Host ".\scripts\release.ps1 -VersionName '5.7' -VersionCode '25' -Description 'Added new feature X and fixed bug Y'" -ForegroundColor Gray
    exit 1
}

Write-Host "Release Parameters:" -ForegroundColor Green
Write-Host "  Version Name: $VersionName" -ForegroundColor Cyan
Write-Host "  Version Code: $VersionCode" -ForegroundColor Cyan
Write-Host "  Description: $Description" -ForegroundColor Cyan
Write-Host ""

# Step 1: Update version numbers
Write-Host "=== Step 1: Updating version numbers ===" -ForegroundColor Green
$buildGradlePath = "app/build.gradle.kts"
$buildGradleContent = Get-Content $buildGradlePath

# Update versionCode and versionName
$updatedContent = $buildGradleContent | ForEach-Object {
    if ($_ -match "versionCode = \d+") {
        "        versionCode = $VersionCode"
    } elseif ($_ -match 'versionName = "[\d.]+"') {
        "        versionName = `"$VersionName`""
    } else {
        $_
    }
}

Set-Content -Path $buildGradlePath -Value $updatedContent
Write-Host "Updated build.gradle.kts" -ForegroundColor Green

# Step 2: Update README.md
Write-Host ""
Write-Host "=== Step 2: Updating README.md ===" -ForegroundColor Green
$readmePath = "README.md"
$readmeContent = Get-Content $readmePath

# Find the version history section and add new entry
$updatedReadmeContent = @()
$foundVersionHistory = $false
$addedNewEntry = $false

foreach ($line in $readmeContent) {
    if ($line -match "## .*Version History") {
        $foundVersionHistory = $true
        $updatedReadmeContent += $line
        Write-Host "  Found version history section" -ForegroundColor Cyan
    } elseif ($foundVersionHistory -and $line -match "^- \*\*v\d+\.\d+\*\*" -and !$addedNewEntry) {
        # Add new version entry before the first existing entry
        $newEntry = "- **v$VersionName** - $Description"
        $updatedReadmeContent += $newEntry
        $updatedReadmeContent += $line
        $addedNewEntry = $true
        Write-Host "  Added new version entry: $newEntry" -ForegroundColor Cyan
    } else {
        $updatedReadmeContent += $line
    }
}

# If we didn't find a version history section, add it
if (-not $foundVersionHistory) {
    Write-Host "  Warning: Version history section not found, adding at end of file" -ForegroundColor Yellow
    $updatedReadmeContent += ""
    $updatedReadmeContent += "## 🔄 Version History"
    $updatedReadmeContent += ""
    $updatedReadmeContent += "- **v$VersionName** - $Description"
}

if (-not $addedNewEntry) {
    Write-Host "  Warning: Could not find existing version entry to add before" -ForegroundColor Yellow
}

Set-Content -Path $readmePath -Value $updatedReadmeContent
Write-Host "Updated README.md with new version entry" -ForegroundColor Green

# Step 3: Remove single-line comments from modified files
Write-Host ""
Write-Host "=== Step 3: Removing comments from modified files ===" -ForegroundColor Green
& ".\scripts\remove_comments.ps1"

# Step 4: Remove debug logs
Write-Host ""
Write-Host "=== Step 4: Removing debug logs ===" -ForegroundColor Green
$files = git diff --name-only | Where-Object { $_ -match '\.kt$' }
$removedLogs = 0

foreach ($file in $files) {
    if (Test-Path $file) {
        $content = Get-Content $file
        $filtered = @()
        
        foreach ($line in $content) {
            # Only remove lines that are purely debug logs (start with whitespace + Log.d/Log.i/etc)
            if ($line -match '^\s*Log\.(d|i|w|e|v)\(' -and $line -match '^\s*Log\.(d|i|w|e|v)\(.*\);?\s*$') {
                $removedLogs++
                Write-Host "  Removed debug log: $($line.Trim())" -ForegroundColor Yellow
            } else {
                $filtered += $line
            }
        }
        
        if ($content.Count -ne $filtered.Count) {
            Set-Content -Path $file -Value $filtered
            Write-Host "  Processed debug logs in: $file" -ForegroundColor Yellow
        }
    }
}

Write-Host "Removed $removedLogs debug log lines" -ForegroundColor Green

# Step 5: Check debug settings
Write-Host ""
Write-Host "=== Step 5: Verifying debug settings ===" -ForegroundColor Green
$loginActivityPath = "app/src/main/java/com/estaciondulce/app/activities/LoginActivity.kt"
if (Test-Path $loginActivityPath) {
    $loginContent = Get-Content $loginActivityPath
    $skipLoginLine = $loginContent | Where-Object { $_ -match "skipLoginForDebug" }
    
    if ($skipLoginLine -match "= true") {
        Write-Host "WARNING: skipLoginForDebug is set to true!" -ForegroundColor Red
        Write-Host "  Please set it to false before release." -ForegroundColor Yellow
    } else {
        Write-Host "Debug settings verified (skipLoginForDebug = false)" -ForegroundColor Green
    }
} else {
    Write-Host "LoginActivity.kt not found" -ForegroundColor Yellow
}

# Step 6: Compile and check for warnings
Write-Host ""
Write-Host "=== Step 6: Compiling and checking for warnings ===" -ForegroundColor Green
Write-Host "Running gradlew compileDevDebugKotlin..." -ForegroundColor Cyan
$compileResult = & ".\gradlew" "compileDevDebugKotlin" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful" -ForegroundColor Green
} else {
    Write-Host "Compilation failed!" -ForegroundColor Red
    Write-Host $compileResult -ForegroundColor Red
    exit 1
}

# Step 7: Check release bundle warnings
Write-Host ""
Write-Host "=== Step 7: Checking release bundle for warnings ===" -ForegroundColor Green
Write-Host "Running gradlew bundleProdRelease --warning-mode all..." -ForegroundColor Cyan
$bundleResult = & ".\gradlew" "bundleProdRelease" "--warning-mode" "all" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Release bundle build successful (0 warnings)" -ForegroundColor Green
} else {
    Write-Host "Release bundle build failed!" -ForegroundColor Red
    Write-Host $bundleResult -ForegroundColor Red
    exit 1
}

# Step 8: Create final release bundle
Write-Host ""
Write-Host "=== Step 8: Creating final release bundle ===" -ForegroundColor Green
Write-Host "Running gradlew bundleProdRelease..." -ForegroundColor Cyan
$finalBundleResult = & ".\gradlew" "bundleProdRelease" 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Final release bundle created successfully" -ForegroundColor Green
} else {
    Write-Host "Final release bundle creation failed!" -ForegroundColor Red
    Write-Host $finalBundleResult -ForegroundColor Red
    exit 1
}

# Step 9: Show git status
Write-Host ""
Write-Host "=== Step 9: Git Status ===" -ForegroundColor Green
Write-Host "Modified files ready for commit:" -ForegroundColor Cyan
git status --porcelain | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }

Write-Host ""
Write-Host "=== Release Preparation Complete! ===" -ForegroundColor Magenta
Write-Host "Version: $VersionName ($VersionCode)" -ForegroundColor Green
Write-Host "Description: $Description" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "1. Review the changes with: git diff" -ForegroundColor Cyan
Write-Host "2. Commit with: git add ." -ForegroundColor Cyan
Write-Host "3. Then: git commit -m `"$Description`"" -ForegroundColor Cyan
Write-Host "4. Push with: git push" -ForegroundColor Cyan
Write-Host ""
Write-Host "Release script completed successfully!" -ForegroundColor Green