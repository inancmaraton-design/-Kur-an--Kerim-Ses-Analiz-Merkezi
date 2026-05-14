# build_and_update.ps1
# Kuran Analiz Merkezi — Derleme + Masaustu Kisayolu Guncelleme
# Calistirma: powershell -ExecutionPolicy Bypass -File .\build_and_update.ps1

$projectDir = "C:\KP"
$outputDir  = "C:\KuranBuild\composeApp\compose\binaries\main-release\app\KuranAnalizWindows"
$desktopLnk = "$env:USERPROFILE\Desktop\Kuran Analiz Merkezi.lnk"

# --- Gradle bul ---
$gradleBin = (Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists" -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue | Select-Object -First 1).FullName

if (-not $gradleBin) {
    Write-Error "Gradle bulunamadi! Projeyi IDE'den en az bir kere derleyin."
    Read-Host "Cikis"
    exit 1
}

Write-Host "=== Kuran Analiz Merkezi — Build ===" -ForegroundColor Cyan
Write-Host "Gradle: $gradleBin"
Write-Host ""

# --- Derle ---
Write-Host "[1/3] Derleniyor..." -ForegroundColor Yellow
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8"
& $gradleBin ":composeApp:createReleaseDistributable" "--project-dir" $projectDir
if ($LASTEXITCODE -ne 0) {
    Write-Error "Derleme basarisiz!"
    Read-Host "Cikis"
    exit 1
}
Write-Host "Derleme basarili!" -ForegroundColor Green

# --- sureler/ kopyala ---
Write-Host "[2/3] Kaynaklar kopyalaniyor..." -ForegroundColor Yellow
$surelerSrc = Join-Path $projectDir "sureler"
$surelerDst = Join-Path $outputDir  "sureler"
if (Test-Path $surelerSrc) {
    if (Test-Path $surelerDst) { Remove-Item $surelerDst -Recurse -Force }
    Copy-Item $surelerSrc $surelerDst -Recurse
    $mp3Count = (Get-ChildItem $surelerDst -Filter "*.mp3" -Recurse).Count
    Write-Host "  sureler/: $mp3Count MP3 dosyasi kopyalandi"
}

# backend/ kopyala
$backendSrc = Join-Path $projectDir "backend"
$backendDst = Join-Path $outputDir  "backend"
if (Test-Path $backendSrc) {
    if (Test-Path $backendDst) { Remove-Item $backendDst -Recurse -Force }
    Copy-Item $backendSrc $backendDst -Recurse
    Write-Host "  backend/: Python dosyalari kopyalandi"
}

# --- Kisayolu guncelle ---
Write-Host "[3/3] Masaustu kisayolu guncelleniyor..." -ForegroundColor Yellow
$exePath = Join-Path $outputDir "KuranAnalizWindows.exe"
if (Test-Path $exePath) {
    $shell = New-Object -ComObject WScript.Shell
    $lnk   = $shell.CreateShortcut($desktopLnk)
    $lnk.TargetPath      = $exePath
    $lnk.WorkingDirectory = $outputDir
    $lnk.Description     = "Kuran-i Kerim Ses Analiz Merkezi"
    $lnk.Save()
    Write-Host "  Kisayol guncellendi: $exePath" -ForegroundColor Green
} else {
    Write-Warning "EXE bulunamadi: $exePath"
}

Write-Host ""
Write-Host "=== Tamamlandi! Masaustu ikonuna tiklayin ===" -ForegroundColor Green
