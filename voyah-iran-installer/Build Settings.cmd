@echo off
powershell.exe -STA -NoProfile -ExecutionPolicy Bypass -File "%~dp0build\Edit-BuildSettings.ps1"
if errorlevel 1 pause
