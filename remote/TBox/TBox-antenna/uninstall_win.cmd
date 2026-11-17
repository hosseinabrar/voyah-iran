@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0host_win.ps1" -Action uninstall
set result=%errorlevel%
pause
exit /b %result%
