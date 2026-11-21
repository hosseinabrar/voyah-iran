@echo off
cd /d "%~dp0"
dist\telemetry-install-windows-amd64.exe -root "%CD%"
set result=%errorlevel%
pause
exit /b %result%
