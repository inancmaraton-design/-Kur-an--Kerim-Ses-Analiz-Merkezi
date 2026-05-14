# ============================================================
#  KURAN-I KERİM UYGULAMASI — TAM KURULUM & ÇALIŞTIRMA
# ============================================================

$AppRoot    = "C:\Users\inanc\Desktop\Kuran ı Kerim Windows Uygulama"
$Backend    = "$AppRoot\backend"
$LogFile    = "$AppRoot\master_run.log"
$ErrorCount = 0

# ── Yardımcılar ─────────────────────────────────────────────
function Header($text) {
    Write-Host "`n╔══════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host   "║  $text" -ForegroundColor Cyan
    Write-Host   "╚══════════════════════════════════════════╝" -ForegroundColor Cyan
    "=== $text ===" | Out-File $LogFile -Append
}
function OK($text)   { Write-Host "  [OK] $text" -ForegroundColor Green;  "OK : $text" | Out-File $LogFile -Append }
function FAIL($text) { Write-Host "  [!!] $text" -ForegroundColor Red;    "ERR: $text" | Out-File $LogFile -Append; $script:ErrorCount++ }
function INFO($text) { Write-Host "  [->] $text" -ForegroundColor Yellow; "    $text"  | Out-File $LogFile -Append }
function SKIP($text) { Write-Host "  [--] $text" -ForegroundColor DarkGray }

function Pause-OnError($stepName) {
    Write-Host "`n  ─────────────────────────────────────────" -ForegroundColor Red
    Write-Host "  ADIM BASARISIZ: $stepName" -ForegroundColor Red
    Write-Host "  Log icin bak: $LogFile" -ForegroundColor Yellow
    Write-Host "  ─────────────────────────────────────────" -ForegroundColor Red
    $ans = Read-Host "  Devam edilsin mi? (e = evet, h = hayir/cikis)"
    if ($ans.ToLower() -ne "e") {
        Write-Host "`n  Cikiliyor." -ForegroundColor Yellow
        Read-Host "  Pencereyi kapatmak icin Enter"
        exit 1
    }
}

function Safe-Exit($code) {
    Write-Host "`n  Log: $LogFile" -ForegroundColor Gray
    Read-Host "`n  Pencereyi kapatmak icin Enter"
    exit $code
}

# Log sifirla
"Baslangic: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" | Out-File $LogFile
"AppRoot: $AppRoot" | Out-File $LogFile -Append


# ════════════════════════════════════════════════════════════
#  ADIM 1 — SİSTEM KONTROLÜ
# ════════════════════════════════════════════════════════════
Header "ADIM 1 — SISTEM KONTROLU"

$sysOK = $true
try   { $v = python --version 2>&1; OK "Python: $v" }
catch { FAIL "Python bulunamadi"; $sysOK = $false }

try   { $v = java -version 2>&1 | Select-Object -First 1; OK "Java: $v" }
catch { FAIL "Java bulunamadi (JDK 17+ gerekli)"; $sysOK = $false }

try   { $v = git --version 2>&1; OK "Git: $v" }
catch { SKIP "Git bulunamadi (zorunlu degil)" }

if (-not $sysOK) {
    FAIL "Sistem gereksinimleri eksik"
    Safe-Exit 1
}


# ════════════════════════════════════════════════════════════
#  ADIM 2 — KLASÖRLER
# ════════════════════════════════════════════════════════════
Header "ADIM 2 — KLASOR YAPISI"

@(
    "$AppRoot\logs",
    "$AppRoot\data",
    "$AppRoot\data\quran",
    "$AppRoot\data\audio",
    "$Backend\models",
    "$Backend\cache",
    "$AppRoot\composeApp\src\commonMain\resources"
) | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ -Force | Out-Null
        OK "Olusturuldu: $_"
    } else { SKIP "Var: $_" }
}


# ════════════════════════════════════════════════════════════
#  ADIM 3 — PYTHON PAKETLERİ
# ════════════════════════════════════════════════════════════
Header "ADIM 3 — PYTHON PAKETLERI"

Set-Location $Backend
$reqFile = "$Backend\requirements.txt"

if (Test-Path $reqFile) {
    INFO "requirements.txt bulundu, yukleniyor..."
    $out = pip install -r $reqFile 2>&1
    $out | Out-File $LogFile -Append
    if ($LASTEXITCODE -eq 0) { OK "Paketler yuklendi" }
    else {
        FAIL "pip install basarisiz"
        Write-Host ($out | Select-Object -Last 15 | Out-String) -ForegroundColor Red
        Pause-OnError "Python Paket Kurulumu"
    }
} else {
    INFO "requirements.txt yok, temel paketler kuruluyor..."
    foreach ($pkg in @("flask","flask-cors","requests","numpy","Pillow")) {
        $o = pip install $pkg --quiet 2>&1
        if ($LASTEXITCODE -eq 0) { OK "Kuruldu: $pkg" }
        else { FAIL "Kurulamadi: $pkg"; $o | Out-File $LogFile -Append }
    }
    "flask`nflask-cors`nrequests`nnumpy`nPillow" | Out-File $reqFile -Encoding UTF8
    OK "requirements.txt olusturuldu"
}


# ════════════════════════════════════════════════════════════
#  ADIM 4 — YAPILANDIRMA DOSYALARI
# ════════════════════════════════════════════════════════════
Header "ADIM 4 — YAPILANDIRMA"

if (-not (Test-Path "$Backend\config.json")) {
    @{ version="1.0.0"; bridge_port=5000; debug=$false; log_level="INFO" } |
        ConvertTo-Json | Out-File "$Backend\config.json" -Encoding UTF8
    OK "config.json olusturuldu"
} else { SKIP "config.json var" }

if (-not (Test-Path "$Backend\.env")) {
    "APP_ENV=production`nDEBUG=false`nBRIDGE_PORT=5000" |
        Out-File "$Backend\.env" -Encoding UTF8
    OK ".env olusturuldu"
} else { SKIP ".env var" }

if (-not (Test-Path "$AppRoot\local.properties")) {
    $sdk = "$env:LOCALAPPDATA\Android\Sdk" -replace "\\","/"
    "sdk.dir=$sdk" | Out-File "$AppRoot\local.properties" -Encoding UTF8
    OK "local.properties olusturuldu"
} else { SKIP "local.properties var" }


# ════════════════════════════════════════════════════════════
#  ADIM 5 — GRADLE KONTROL
# ════════════════════════════════════════════════════════════
Header "ADIM 5 — GRADLE"

Set-Location $AppRoot

if (-not (Test-Path ".\gradlew.bat")) {
    FAIL "gradlew.bat bulunamadi! Proje klasoru: $AppRoot"
    Safe-Exit 1
}

INFO "Gradle versiyon kontrolu..."
$gv = .\gradlew.bat --version 2>&1
$gv | Out-File $LogFile -Append
if ($LASTEXITCODE -eq 0) {
    $gl = ($gv | Select-String "Gradle").Line
    OK "Gradle hazir: $gl"
} else {
    FAIL "Gradle calistirilamadi"
    Write-Host ($gv | Out-String) -ForegroundColor Red
    Pause-OnError "Gradle Kontrol"
}


# ════════════════════════════════════════════════════════════
#  ADIM 6 — BACKEND TESTLERİ
# ════════════════════════════════════════════════════════════
Header "ADIM 6 — BACKEND TESTLERI"

Set-Location $Backend

if (Test-Path "test_full.py") {
    INFO "test_full.py calistiriliyor..."
    $testOut = python test_full.py 2>&1
    $testOut | Out-File $LogFile -Append
    $testOut | ForEach-Object { Write-Host "  $_" }   # ekranda goster
    if ($LASTEXITCODE -eq 0) {
        OK "Backend testleri BASARILI"
    } else {
        FAIL "Backend testleri BASARISIZ"
        Write-Host "`n  --- SON HATALAR ---" -ForegroundColor Red
        $testOut | Select-Object -Last 20 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
        Pause-OnError "Backend Testleri"
    }
} else { SKIP "test_full.py bulunamadi, atlanıyor" }


# ════════════════════════════════════════════════════════════
#  ADIM 7 — BRIDGE TESTİ
# ════════════════════════════════════════════════════════════
Header "ADIM 7 — BRIDGE PROTOKOL TESTI"

Set-Location $Backend

if (Test-Path "analyze_bridge.py") {
    INFO "Bridge ping gonderiliyor..."
    $bridgeOut = '{"cmd":"ping"}' | python analyze_bridge.py 2>&1
    $bridgeOut | Out-File $LogFile -Append
    $firstLine = ($bridgeOut -split "`n")[0].Trim()

    Write-Host "  Yanit: $firstLine" -ForegroundColor White

    if ($firstLine -eq '{"ok":true,"ready":true}') {
        OK "Bridge BASARILI"
    } else {
        FAIL "Bridge yaniti yanlis"
        Write-Host "  Beklenen : {`"ok`":true,`"ready`":true}" -ForegroundColor Yellow
        Write-Host "  Gelen    : $firstLine" -ForegroundColor Red
        Write-Host "`n  --- TUM CIKTI ---" -ForegroundColor DarkGray
        $bridgeOut | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
        Pause-OnError "Bridge Protokol"
    }
} else { SKIP "analyze_bridge.py bulunamadi, atlanıyor" }


# ════════════════════════════════════════════════════════════
#  ADIM 8 — TEMİZLE & DERLE
# ════════════════════════════════════════════════════════════
Header "ADIM 8 — TEMIZLE & DERLE"

Set-Location $AppRoot

INFO "Eski build temizleniyor..."
$cleanOut = .\gradlew.bat clean 2>&1
$cleanOut | Out-File $LogFile -Append
if ($LASTEXITCODE -eq 0) { OK "Temizlendi" }
else {
    FAIL "Clean basarisiz"
    $cleanOut | Select-Object -Last 10 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    Pause-OnError "Gradle Clean"
}

INFO "Proje derleniyor... (ilk seferde uzun surebilir)"
$buildOut = .\gradlew.bat :composeApp:compileKotlin 2>&1
$buildOut | Out-File $LogFile -Append

# Hataları filtrele
$errors   = $buildOut | Where-Object { $_ -match "error:|ERROR|FAILED|Exception" }
$warnings = $buildOut | Where-Object { $_ -match "warning:|Warning" }

if ($warnings.Count -gt 0) {
    Write-Host "`n  --- UYARILAR ($($warnings.Count) adet) ---" -ForegroundColor Yellow
    $warnings | Select-Object -Last 5 | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
}

if ($LASTEXITCODE -ne 0) {
    FAIL "Derleme BASARISIZ"
    Write-Host "`n  --- HATALAR ---" -ForegroundColor Red
    if ($errors) {
        $errors | Select-Object -Last 25 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    } else {
        $buildOut | Select-Object -Last 30 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    }
    Write-Host "`n  Tam log: $LogFile" -ForegroundColor Yellow
    Safe-Exit 1
} else {
    OK "Derleme BASARILI"
}


# ════════════════════════════════════════════════════════════
#  ADIM 9 — ÇALIŞTIR
# ════════════════════════════════════════════════════════════
Header "ADIM 9 — UYGULAMA BASLATILIYOR"

INFO "Uygulama aciliyor..."
.\gradlew.bat :composeApp:run

if ($LASTEXITCODE -ne 0) {
    FAIL "Uygulama baslatma hatasi"
    Safe-Exit 1
}


# ════════════════════════════════════════════════════════════
#  TAMAMLANDI
# ════════════════════════════════════════════════════════════
$renk = if ($ErrorCount -eq 0) {"Green"} else {"Yellow"}
Write-Host "`n╔══════════════════════════════════════════╗" -ForegroundColor $renk
Write-Host   "║  TAMAMLANDI  |  Uyari/Hata: $ErrorCount" -ForegroundColor $renk
Write-Host   "╚══════════════════════════════════════════╝" -ForegroundColor $renk
"Bitis: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') | Hata: $ErrorCount" | Out-File $LogFile -Append

Read-Host "`nPencereyi kapatmak icin Enter"
