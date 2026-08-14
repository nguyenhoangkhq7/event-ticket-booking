@echo off
setlocal enabledelayedexpansion

echo ================================================================
echo    Event Ticket Booking Service - JMeter Load Test Runner
echo ================================================================

:: 1. Configuration Defaults
set "HOST=localhost"
set "PORT=8080"
set "PROTOCOL=http"
set "BOOKING_RPM=500"
set "BROWSE_USERS=100"
set "BOOKING_USERS=50"
set "DURATION=300"
set "RAMPUP=30"

:: Custom arguments override
if not "%~1"=="" set "HOST=%~1"
if not "%~2"=="" set "PORT=%~2"
if not "%~3"=="" set "BOOKING_RPM=%~3"
if not "%~4"=="" set "DURATION=%~4"

:: Paths
set "BASE_DIR=%~dp0.."
set "PLAN_FILE=%BASE_DIR%\plans\event_ticket_booking_load_test.jmx"
set "CSV_FILE=%BASE_DIR%\data\users_tokens.csv"

:: 2. Check CSV file
if not exist "%CSV_FILE%" (
    echo [INFO] users_tokens.csv not found. Generating 50,000 tokens...
    python "%BASE_DIR%\data\generate_test_data.py"
    if errorlevel 1 (
        echo [ERROR] Failed to generate test tokens with Python. Trying Java...
        java "%BASE_DIR%\data\GenerateTestData.java"
    )
)

:: 3. Locate JMeter Executable
set "JMETER_BIN="

:: Check PATH
where jmeter >nul 2>&1
if %ERRORLEVEL% equ 0 set "JMETER_BIN=jmeter"

:: Check JMETER_HOME
if not defined JMETER_BIN (
    if defined JMETER_HOME (
        if exist "%JMETER_HOME%\bin\jmeter.bat" set "JMETER_BIN=%JMETER_HOME%\bin\jmeter.bat"
    )
)

:: Check jmeter\tools folder
if not defined JMETER_BIN (
    for /d %%D in ("%BASE_DIR%\tools\apache-jmeter-*") do (
        if exist "%%D\bin\jmeter.bat" set "JMETER_BIN=%%D\bin\jmeter.bat"
    )
)

:: Check D:\projects\library folder
if not defined JMETER_BIN (
    for /d %%D in ("D:\projects\library\apache-jmeter-*") do (
        if exist "%%D\bin\jmeter.bat" set "JMETER_BIN=%%D\bin\jmeter.bat"
    )
)

:: If still not found, offer options
if not defined JMETER_BIN (
    echo.
    echo [WARNING] Apache JMeter was not found on your system!
    echo.
    echo Choose an option to proceed:
    echo   1. Automatically download and setup portable Apache JMeter 5.6.3 (Recommended)
    echo   2. Run via Maven wrapper (./mvnw jmeter:jmeter)
    echo   3. Run via Docker Compose
    echo   4. Exit
    echo.
    set /p "CHOICE=Enter choice (1-4) [default: 1]: "
    if "!CHOICE!"=="" set "CHOICE=1"
    
    if "!CHOICE!"=="1" (
        echo [INFO] Downloading and installing Apache JMeter...
        powershell -ExecutionPolicy Bypass -File "%~dp0setup_jmeter.ps1"
        for /d %%D in ("%BASE_DIR%\tools\apache-jmeter-*") do (
            if exist "%%D\bin\jmeter.bat" set "JMETER_BIN=%%D\bin\jmeter.bat"
        )
    ) else if "!CHOICE!"=="2" (
        echo [INFO] Executing JMeter via Maven Plugin...
        cd /d "%BASE_DIR%\.."
        call mvnw.cmd verify "-Dsurefire.skip=true" "-Djmeter.host=%HOST%" "-Djmeter.port=%PORT%" "-Djmeter.booking_rpm=%BOOKING_RPM%" "-Djmeter.duration=%DURATION%" "-Djmeter.browse_users=%BROWSE_USERS%" "-Djmeter.booking_users=%BOOKING_USERS%"
        exit /b %ERRORLEVEL%
    ) else if "!CHOICE!"=="3" (
        echo [INFO] Executing JMeter via Docker Compose...
        cd /d "%BASE_DIR%\.."
        docker-compose -f docker-compose.jmeter.yml up
        exit /b %ERRORLEVEL%
    ) else (
        echo Exiting...
        exit /b 1
    )
)

if not defined JMETER_BIN (
    echo [ERROR] JMeter executable is still not available.
    exit /b 1
)

:: 4. Generate timestamp using PowerShell
for /f %%I in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TIMESTAMP=%%I"
if not defined TIMESTAMP set "TIMESTAMP=latest"

set "REPORT_DIR=%BASE_DIR%\reports\report_%TIMESTAMP%"
set "JTL_FILE=%BASE_DIR%\reports\results_%TIMESTAMP%.jtl"

if not exist "%BASE_DIR%\reports" mkdir "%BASE_DIR%\reports"

echo.
echo Test Configuration:
echo ----------------------------------------------------
echo Target Host       : %PROTOCOL%://%HOST%:%PORT%
echo Booking Rate      : %BOOKING_RPM% requests/minute
echo Active Users Pool : 50,000 users
echo Catalog Users     : %BROWSE_USERS% concurrent threads
echo Booking Users     : %BOOKING_USERS% concurrent threads
echo Duration          : %DURATION% seconds (Ramp-up: %RAMPUP%s)
echo JMX Plan          : %PLAN_FILE%
echo JMeter Executable : %JMETER_BIN%
echo Report Directory  : %REPORT_DIR%
echo ----------------------------------------------------
echo.

echo [INFO] Starting JMeter execution in non-GUI mode...
call "%JMETER_BIN%" -n -t "%PLAN_FILE%" ^
  -l "%JTL_FILE%" ^
  -e -o "%REPORT_DIR%" ^
  -Jhost="%HOST%" ^
  -Jport="%PORT%" ^
  -Jprotocol="%PROTOCOL%" ^
  -Jbooking_rpm="%BOOKING_RPM%" ^
  -Jbrowse_users="%BROWSE_USERS%" ^
  -Jbooking_users="%BOOKING_USERS%" ^
  -Jduration="%DURATION%" ^
  -Jrampup="%RAMPUP%" ^
  -Jcsv_file="%CSV_FILE%"

if %ERRORLEVEL% equ 0 (
    echo.
    echo ================================================================
    echo [SUCCESS] Load test completed successfully!
    echo HTML Dashboard Report generated at:
    echo %REPORT_DIR%\index.html
    echo ================================================================
    
    set /p "OPEN_REPORT=Open HTML report in browser now? (Y/N) [Y]: "
    if "!OPEN_REPORT!"=="" set "OPEN_REPORT=Y"
    if /i "!OPEN_REPORT!"=="Y" (
        start "" "%REPORT_DIR%\index.html"
    )
) else (
    echo.
    echo [ERROR] JMeter execution encountered an error (exit code: %ERRORLEVEL%).
)

endlocal
