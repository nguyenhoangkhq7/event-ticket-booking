# ================================================================
# Event Ticket Booking Service - JMeter PowerShell Runner
# ================================================================

param (
    [string]$HostName = "localhost",
    [int]$Port = 8080,
    [string]$Protocol = "http",
    [double]$BookingRpm = 500.0,
    [int]$BrowseUsers = 100,
    [int]$BookingUsers = 50,
    [int]$Duration = 300,
    [int]$RampUp = 30,
    [switch]$OpenReport = $true
)

$BaseDir = Resolve-Path "$PSScriptRoot\.."
$PlanFile = "$BaseDir\plans\event_ticket_booking_load_test.jmx"
$CsvFile = "$BaseDir\data\users_tokens.csv"

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "   Event Ticket Booking Service - JMeter Load Test Runner" -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 1. Ensure test tokens CSV exists
if (-not (Test-Path $CsvFile)) {
    Write-Host "[INFO] users_tokens.csv not found. Generating 50,000 tokens..." -ForegroundColor Yellow
    python "$BaseDir\data\generate_test_data.py"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to generate test tokens. Make sure Python is installed."
        exit 1
    }
}

# 2. Locate JMeter
$JMeterBin = $null
if (Get-Command "jmeter" -ErrorAction SilentlyContinue) {
    $JMeterBin = "jmeter"
} elseif ($env:JMETER_HOME -and (Test-Path "$env:JMETER_HOME\bin\jmeter.bat")) {
    $JMeterBin = "$env:JMETER_HOME\bin\jmeter.bat"
} else {
    # Check local tools folder
    $LocalJMeter = Get-ChildItem -Path "$BaseDir\tools\apache-jmeter-*\bin\jmeter.bat" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($LocalJMeter) {
        $JMeterBin = $LocalJMeter.FullName
    } else {
        # Check library folder
        $LibJMeter = Get-ChildItem -Path "D:\projects\library\apache-jmeter-*\bin\jmeter.bat" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($LibJMeter) {
            $JMeterBin = $LibJMeter.FullName
        }
    }
}

if (-not $JMeterBin) {
    Write-Host "[WARNING] Apache JMeter not found. Automatically downloading portable JMeter 5.6.3..." -ForegroundColor Yellow
    & "$PSScriptRoot\setup_jmeter.ps1"
    $LocalJMeter = Get-ChildItem -Path "$BaseDir\tools\apache-jmeter-*\bin\jmeter.bat" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($LocalJMeter) {
        $JMeterBin = $LocalJMeter.FullName
    } else {
        Write-Error "Failed to locate JMeter after download."
        exit 1
    }
}

# 3. Create Report Directory
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ReportsDir = "$BaseDir\reports"
if (-not (Test-Path $ReportsDir)) {
    New-Item -ItemType Directory -Path $ReportsDir | Out-Null
}

$ReportDir = "$ReportsDir\report_$Timestamp"
$JtlFile = "$ReportsDir\results_$Timestamp.jtl"

Write-Host ""
Write-Host "Test Configuration:" -ForegroundColor Green
Write-Host "----------------------------------------------------"
Write-Host "Target Host       : $Protocol://$HostName`:$Port"
Write-Host "Booking Rate      : $BookingRpm requests/minute (~$([math]::Round($BookingRpm/60, 2)) RPS)"
Write-Host "Active Users Pool : 50,000 users ($CsvFile)"
Write-Host "Catalog Users     : $BrowseUsers concurrent threads"
Write-Host "Booking Users     : $BookingUsers concurrent threads"
Write-Host "Duration          : $Duration seconds (Ramp-up: ${RampUp}s)"
Write-Host "JMX Plan          : $PlanFile"
Write-Host "Report Directory  : $ReportDir"
Write-Host "----------------------------------------------------"
Write-Host ""

Write-Host "[INFO] Executing JMeter non-GUI test..." -ForegroundColor Cyan

$JMeterArgs = @(
    "-n",
    "-t", $PlanFile,
    "-l", $JtlFile,
    "-e",
    "-o", $ReportDir,
    "-Jhost=$HostName",
    "-Jport=$Port",
    "-Jprotocol=$Protocol",
    "-Jbooking_rpm=$BookingRpm",
    "-Jbrowse_users=$BrowseUsers",
    "-Jbooking_users=$BookingUsers",
    "-Jduration=$Duration",
    "-Jrampup=$RampUp",
    "-Jcsv_file=$CsvFile"
)

& $JMeterBin @JMeterArgs

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "================================================================" -ForegroundColor Green
    Write-Host "[SUCCESS] Load test completed successfully!" -ForegroundColor Green
    Write-Host "HTML Report generated at: $ReportDir\index.html" -ForegroundColor Green
    Write-Host "================================================================" -ForegroundColor Green

    if ($OpenReport) {
        Start-Process "$ReportDir\index.html"
    }
} else {
    Write-Host ""
    Write-Host "[ERROR] JMeter execution failed or JMeter executable was not found." -ForegroundColor Red
    Write-Host "Ensure Apache JMeter is installed and added to PATH or set JMETER_HOME." -ForegroundColor Yellow
}
