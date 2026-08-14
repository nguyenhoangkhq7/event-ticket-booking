# ================================================================
# Apache JMeter Auto-Downloader & Setup Utility
# ================================================================

param (
    [string]$Version = "5.6.3",
    [string]$InstallDir = "$PSScriptRoot\..\tools"
)

$ZipUrl = "https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-$Version.zip"
$ZipFile = "$InstallDir\apache-jmeter-$Version.zip"
$TargetFolder = "$InstallDir\apache-jmeter-$Version"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   Downloading & Setting Up Apache JMeter $Version" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

if (Test-Path "$TargetFolder\bin\jmeter.bat") {
    Write-Host "[OK] Apache JMeter is already installed at: $TargetFolder" -ForegroundColor Green
    exit 0
}

if (-not (Test-Path $InstallDir)) {
    New-Item -ItemType Directory -Path $InstallDir | Out-Null
}

Write-Host "[INFO] Downloading JMeter from $ZipUrl ..." -ForegroundColor Yellow
Invoke-WebRequest -Uri $ZipUrl -OutFile $ZipFile -UseBasicParsing

Write-Host "[INFO] Extracting to $InstallDir ..." -ForegroundColor Yellow
Expand-Archive -Path $ZipFile -DestinationPath $InstallDir -Force

if (Test-Path $ZipFile) {
    Remove-Item -Path $ZipFile -Force
}

if (Test-Path "$TargetFolder\bin\jmeter.bat") {
    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Green
    Write-Host "[SUCCESS] Apache JMeter $Version installed successfully!" -ForegroundColor Green
    Write-Host "Path: $TargetFolder\bin\jmeter.bat" -ForegroundColor Green
    Write-Host "================================================================" -ForegroundColor Green
} else {
    Write-Error "Failed to install JMeter."
    exit 1
}
