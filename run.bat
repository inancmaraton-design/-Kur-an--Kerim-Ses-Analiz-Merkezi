@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo.
echo ========================================
echo  Kuran Analiz Merkezi Baslatiliyor
echo ========================================
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
echo.
echo Uygulama kapandi.
pause
