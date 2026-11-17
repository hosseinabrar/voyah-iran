@echo off
cd /d "%~dp0"
dist\install-windows-amd64.exe -root "%~dp0." -uninstall
set result=%errorlevel%
pause
exit /b %result%
