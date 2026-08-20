@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo.
echo === dreamreel 一键启动 ===
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-all.ps1"
if errorlevel 1 (
  echo.
  echo 启动失败，请查看上方错误信息。
  pause
  exit /b 1
)

echo.
echo 窗口将保持打开；关闭各服务请运行 stop.bat 或关掉弹出的终端窗口。
pause
