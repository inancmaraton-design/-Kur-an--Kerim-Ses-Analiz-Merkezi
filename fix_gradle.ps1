# Gradle JNI / Native Services Fix Script
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "  GRADLE NATIVE SERVICES FIX" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

# 1. Gradle Daemon'larini Durdur
Write-Host "[1/3] Calisan Gradle daemon'lari durduruluyor..." -ForegroundColor Yellow
try {
    .\gradlew.bat --stop
} catch {
    Write-Host "Gradle stop komutu hata verdi (onemli degil)." -ForegroundColor Gray
}

# 2. Yerel .gradle ve build klasorlerini temizle
Write-Host "[2/3] Yerel .gradle ve build klasorleri temizleniyor..." -ForegroundColor Yellow
$folders = @(".gradle", "build", "composeApp/build")
foreach ($f in $folders) {
    if (Test-Path $f) {
        Remove-Item -Recurse -Force $f
        Write-Host "  Silindi: $f" -ForegroundColor Gray
    }
}

# 3. Global Native Cache Temizligi (En Kritik Adim)
Write-Host "[3/3] Global native JNI cache temizleniyor..." -ForegroundColor Yellow
$globalNativeCache = Join-Path $HOME ".gradle\native"
if (Test-Path $globalNativeCache) {
    Remove-Item -Recurse -Force $globalNativeCache
    Write-Host "  Silindi: $globalNativeCache" -ForegroundColor Gray
}

Write-Host "===============================================" -ForegroundColor Green
Write-Host "  TEMIZLIK TAMAMLANDI!" -ForegroundColor Green
Write-Host "  Simdi run_all.ps1 scriptini tekrar calistirin." -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green
