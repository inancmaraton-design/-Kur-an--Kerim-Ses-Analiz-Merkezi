@echo off
chcp 65001 >nul
title Kuran-i Kerim - Tam Kurulum ve Calistirma
color 0B

set "APP=C:\Users\inanc\Desktop\Kuran i Kerim Windows Uygulama"
set "BACKEND=%APP%\backend"
set "LOG=%APP%\master_run.log"

echo. > "%LOG%"
echo Baslangic: %DATE% %TIME% >> "%LOG%"

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 1 - SISTEM KONTROLU
echo ========================================
echo.

python --version >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo   [!!] Python bulunamadi! python.org'dan kur.
    echo   [!!] Python bulunamadi >> "%LOG%"
    pause
    exit /b 1
)
for /f "delims=" %%v in ('python --version 2^>^&1') do echo   [OK] %%v

java -version >nul 2>&1
if %errorlevel% neq 0 (
    color 0C
    echo   [!!] Java bulunamadi! JDK 17+ kur.
    pause
    exit /b 1
)
echo   [OK] Java bulundu

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 2 - KLASORLER
echo ========================================
echo.

if not exist "%APP%\logs\"      mkdir "%APP%\logs"       && echo   [OK] logs klasoru olusturuldu
if not exist "%APP%\data\"      mkdir "%APP%\data"       && echo   [OK] data klasoru olusturuldu
if not exist "%APP%\data\quran\" mkdir "%APP%\data\quran" && echo   [OK] data\quran olusturuldu
if not exist "%BACKEND%\cache\" mkdir "%BACKEND%\cache"  && echo   [OK] backend\cache olusturuldu
if not exist "%BACKEND%\models\" mkdir "%BACKEND%\models" && echo   [OK] backend\models olusturuldu

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 3 - PYTHON PAKETLERI
echo ========================================
echo.

cd /d "%BACKEND%"

if exist "requirements.txt" (
    echo   [-^>] requirements.txt bulundu, yukleniyor...
    pip install -r requirements.txt >> "%LOG%" 2>&1
    if %errorlevel% neq 0 (
        echo   [!!] pip install basarisiz! Log: %LOG%
        echo   Devam edilsin mi?
        pause
    ) else (
        echo   [OK] Python paketleri yuklendi
    )
) else (
    echo   [-^>] requirements.txt yok, temel paketler kuruluyor...
    pip install flask flask-cors requests numpy --quiet >> "%LOG%" 2>&1
    echo   [OK] Temel paketler yuklendi
    echo flask> requirements.txt
    echo flask-cors>> requirements.txt
    echo requests>> requirements.txt
    echo numpy>> requirements.txt
)

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 4 - YAPILANDIRMA DOSYALARI
echo ========================================
echo.

if not exist "%BACKEND%\config.json" (
    echo {"version":"1.0","bridge_port":5000,"debug":false}> "%BACKEND%\config.json"
    echo   [OK] config.json olusturuldu
) else (
    echo   [--] config.json zaten var
)

if not exist "%BACKEND%\.env" (
    echo APP_ENV=production> "%BACKEND%\.env"
    echo DEBUG=false>> "%BACKEND%\.env"
    echo BRIDGE_PORT=5000>> "%BACKEND%\.env"
    echo   [OK] .env olusturuldu
) else (
    echo   [--] .env zaten var
)

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 5 - GRADLE KONTROL
echo ========================================
echo.

cd /d "%APP%"

if not exist "gradlew.bat" (
    color 0C
    echo   [!!] gradlew.bat bulunamadi!
    echo   Proje klasoru: %APP%
    pause
    exit /b 1
)

echo   [-^>] Gradle kontrol ediliyor...
call gradlew.bat --version >> "%LOG%" 2>&1
if %errorlevel% neq 0 (
    echo   [!!] Gradle calistirilamadi. Log: %LOG%
    set /p devam="  Devam edilsin mi? (e/h): "
    if /i "%devam%" neq "e" pause & exit /b 1
) else (
    echo   [OK] Gradle hazir
)

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 6 - BACKEND TESTLERI
echo ========================================
echo.

cd /d "%BACKEND%"

if exist "test_full.py" (
    echo   [-^>] test_full.py calistiriliyor...
    python test_full.py
    echo. >> "%LOG%"
    if %errorlevel% neq 0 (
        echo.
        echo   [!!] Backend testleri BASARISIZ
        set /p devam="  Devam edilsin mi? (e/h): "
        if /i "%devam%" neq "e" pause & exit /b 1
    ) else (
        echo   [OK] Backend testleri BASARILI
    )
) else (
    echo   [--] test_full.py bulunamadi, atlandi
)

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 7 - BRIDGE TESTI
echo ========================================
echo.

if exist "analyze_bridge.py" (
    echo   [-^>] Bridge ping testi yapiliyor...
    echo {"cmd":"ping"} | python analyze_bridge.py > "%TEMP%\bridge_out.txt" 2>&1
    set /p BRIDGE_YANIT=<"%TEMP%\bridge_out.txt"
    echo   Bridge yaniti: %BRIDGE_YANIT%
    echo   [!!] Bridge sonucunu manuel kontrol edin
    echo   Beklenen: {"ok":true,"ready":true}
    set /p devam="  Yanit dogru mu? (e/h): "
    if /i "%devam%" neq "e" (
        echo   [!!] Bridge basarisiz, durduruluyor
        pause
        exit /b 1
    )
    echo   [OK] Bridge onaylandi
) else (
    echo   [--] analyze_bridge.py bulunamadi, atlandi
)

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 8 - TEMIZLE VE DERLE
echo ========================================
echo.

cd /d "%APP%"

echo   [-^>] Eski build temizleniyor...
call gradlew.bat clean >> "%LOG%" 2>&1
if %errorlevel% equ 0 (
    echo   [OK] Temizlendi
) else (
    echo   [!!] Clean hatasi, devam ediliyor...
)

echo   [-^>] Derleniyor... (lutfen bekleyin, uzun surebilir)
call gradlew.bat :composeApp:compileKotlin 2>&1 | tee "%TEMP%\build_out.txt" >> "%LOG%"
if %errorlevel% neq 0 (
    echo.
    color 0C
    echo   [!!] DERLEME BASARISIZ!
    echo.
    echo   --- Son hatalar ---
    powershell -Command "Get-Content '%TEMP%\build_out.txt' | Select-String 'error|FAILED|Exception' | Select-Object -Last 20 | ForEach-Object { Write-Host '  ' $_ -ForegroundColor Red }"
    echo.
    echo   Tam log icin bak: %LOG%
    pause
    exit /b 1
)
echo   [OK] Derleme BASARILI

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   ADIM 9 - UYGULAMA BASLATILIYOR
echo ========================================
echo.

echo   [-^>] Uygulama aciliyor...
call gradlew.bat :composeApp:run
if %errorlevel% neq 0 (
    echo   [!!] Uygulama baslanamadi. Log: %LOG%
    pause
    exit /b 1
)

:: ─────────────────────────────────────────
echo.
echo ========================================
echo   TAMAMLANDI!
echo ========================================
echo   Log dosyasi: %LOG%
echo ========================================
echo.
pause
