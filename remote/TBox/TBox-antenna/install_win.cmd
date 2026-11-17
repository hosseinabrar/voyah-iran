@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0host_win.ps1" -Action install
set result=%errorlevel%
pause
exit /b %result%
