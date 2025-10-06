# Script to remove single-line comments from modified .kt files for release
# Usage: powershell -ExecutionPolicy Bypass -File scripts/remove_comments.ps1

Write-Host "=== Removing single-line comments from modified .kt files ===" -ForegroundColor Green

# Get all modified .kt files
$files = git diff --name-only | Where-Object { $_ -match '\.kt$' }

if ($files.Count -eq 0) {
    Write-Host "No modified .kt files found." -ForegroundColor Yellow
    exit 0
}

Write-Host "Found $($files.Count) modified .kt files:" -ForegroundColor Cyan
$files | ForEach-Object { Write-Host "  - $_" -ForegroundColor Gray }

$processedCount = 0
$removedCommentsCount = 0

foreach ($file in $files) {
    Write-Host "Processing: $file" -ForegroundColor White
    
    if (Test-Path $file) {
        $originalContent = Get-Content $file
        $filteredContent = $originalContent | Where-Object { $_ -notmatch '^\s*//' }
        
        # Count removed comments
        $originalComments = ($originalContent | Where-Object { $_ -match '^\s*//' }).Count
        if ($originalComments -gt 0) {
            Write-Host "  Removed $originalComments comment(s)" -ForegroundColor Yellow
            $removedCommentsCount += $originalComments
        }
        
        Set-Content -Path $file -Value $filteredContent
        $processedCount++
        Write-Host "  Processed successfully" -ForegroundColor Green
    } else {
        Write-Host "  File not found: $file" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "=== Summary ===" -ForegroundColor Green
Write-Host "Files processed: $processedCount" -ForegroundColor Cyan
Write-Host "Total comments removed: $removedCommentsCount" -ForegroundColor Cyan
Write-Host "Comment removal completed successfully!" -ForegroundColor Green