# Download MediaPipe HandLandmarker model into app/src/main/assets/
# Usage:  powershell -ExecutionPolicy Bypass -File download_model.ps1

$ErrorActionPreference = "Stop"

# Official MediaPipe Tasks Vision model URL
$ModelUrl = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task"

# Resolve target path relative to this script
$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$AssetsDir  = Join-Path $ScriptDir "app\src\main\assets"
$TargetFile = Join-Path $AssetsDir "hand_landmarker.task"

Write-Host "=========================================="
Write-Host " MediaPipe HandLandmarker Model Downloader"
Write-Host "=========================================="
Write-Host "Target: $TargetFile"
Write-Host ""

# Create assets dir if missing
if (-not (Test-Path $AssetsDir)) {
    New-Item -ItemType Directory -Path $AssetsDir -Force | Out-Null
    Write-Host "[OK] Created directory: $AssetsDir"
} else {
    Write-Host "[OK] Directory exists: $AssetsDir"
}

# If file already exists, ask before re-downloading
if (Test-Path $TargetFile) {
    $existingSize = (Get-Item $TargetFile).Length
    Write-Host "[INFO] File already exists, size: $([math]::Round($existingSize/1KB, 1)) KB"
    $answer = Read-Host "Re-download and overwrite? (y/N)"
    if ($answer -notmatch "^[yY]") {
        Write-Host "[SKIP] User cancelled, keeping existing file"
        exit 0
    }
    Remove-Item $TargetFile -Force
}

# Download
Write-Host ""
Write-Host "[Downloading] $ModelUrl"
try {
    $ProgressPreference = "Continue"
    Invoke-WebRequest -Uri $ModelUrl -OutFile $TargetFile -TimeoutSec 60
} catch {
    Write-Host ""
    Write-Host "[FAIL] Download failed: $($_.Exception.Message)"
    Write-Host ""
    Write-Host "Possible causes:"
    Write-Host "  1) Network cannot reach storage.googleapis.com (requires VPN in mainland China)"
    Write-Host "  2) Corporate/school firewall blocked the download"
    Write-Host ""
    Write-Host "Manual fallback:"
    Write-Host "  Open the URL above in a browser, then save the file to:"
    Write-Host "  $TargetFile"
    exit 1
}

# Validate
$size = (Get-Item $TargetFile).Length
if ($size -lt 1MB) {
    Write-Host "[WARN] File is only $([math]::Round($size/1KB, 1)) KB, may be incomplete"
    exit 2
}

Write-Host ""
Write-Host "[OK] Download complete! Size: $([math]::Round($size/1MB, 2)) MB"
Write-Host "[OK] Path: $TargetFile"
Write-Host ""
Write-Host "Next: open this project in Android Studio and press Run."
