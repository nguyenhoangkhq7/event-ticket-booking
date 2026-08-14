@echo off
setlocal

echo [INFO] Running PowerShell JMeter auto-installer...
powershell -ExecutionPolicy Bypass -File "%~dp0setup_jmeter.ps1"

endlocal
