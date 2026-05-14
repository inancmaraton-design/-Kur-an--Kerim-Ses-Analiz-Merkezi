# Kuran Analiz v7.1 - Master Execution Script
$ErrorActionPreference = "Stop"

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "  KURAN ANALIZ v7.1 - MASTER RUN SCRIPT" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

# 1. Dizin Ayari
$projeDizini = "C:\KP"
if (-not (Test-Path $projeDizini)) {
    Write-Error "HATA: $projeDizini dizini bulunamadi!"
    exit
}
Set-Location $projeDizini
Write-Host "[1/4] Dizin: $projeDizini" -ForegroundColor Green

# Ortam Ayarlari
$env:PYTHONIOENCODING = "utf-8"
$gradleCache = "C:\.gradle_cache"
if (-not (Test-Path $gradleCache)) { New-Item -Path $gradleCache -ItemType Directory -Force | Out-Null }
$env:GRADLE_USER_HOME = $gradleCache
Write-Host "  -> Gradle Cache: $gradleCache" -ForegroundColor Gray

# 2. Python Kutuphaneleri
Write-Host "[2/4] Python kutuphaneleri kontrol ediliyor..." -ForegroundColor Yellow
Set-Location "$projeDizini\backend"
python -m pip install -r requirements.txt
python -m pip install librosa umap-learn
Set-Location $projeDizini

# 3. Backend Testi
Write-Host "[3/4] Backend testleri baslatiliyor..." -ForegroundColor Yellow
Set-Location "$projeDizini\backend"
python test_full.py
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAILED: Backend testleri basarisiz!" -ForegroundColor Red
    Set-Location $projeDizini
    exit
}
Write-Host "  -> Backend OK" -ForegroundColor Green
Set-Location $projeDizini

# 4. Uygulamayi Baslat
Write-Host "[4/4] Uygulama baslatiliyor (Gradle)..." -ForegroundColor Cyan
& ".\gradlew.bat" :composeApp:run
