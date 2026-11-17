@echo off
cd /d "%~dp0"
start http://127.0.0.1:18781/
dist\client-windows-amd64.exe
pause
