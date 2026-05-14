@echo off
chcp 65001 >nul
echo Starting Kuran Analiz...
cd /d "C:\Users\inanc\Desktop\Kuran ı Kerim Windows Uygulama"
powershell.exe -ExecutionPolicy Bypass -File ".\run.ps1" :composeApp:run
echo.
echo Process finished with exit code: %ERRORLEVEL%
pause
