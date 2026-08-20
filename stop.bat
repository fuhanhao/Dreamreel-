@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo === 停止本地服务 ===
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\stop-all.ps1"
pause
