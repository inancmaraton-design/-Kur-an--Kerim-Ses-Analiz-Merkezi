$AppRoot = "C:\Users\inanc\Desktop\Kuran i Kerim Windows Uygulama"
$Backend = "$AppRoot\backend"
$LogFile = "$AppRoot\master_run.log"
$EC = 0

function H($t) { Write-Host "`n=== $t ===" -ForegroundColor Cyan; "=== $t ===" | Out-File $LogFile -Append }
function G($t) { Write-Host "  [OK] $t" -ForegroundColor Green; "OK: $t" | Out-File $LogFile -Append }
function R($t) { Write-Host "  [!!] $t" -ForegroundColor Red; "ERR: $t" | Out-File $LogFile -Append; $script:EC++ }
function Y($t) { Write-Host "  [->] $t" -ForegroundColor Yellow; "   $t" | Out-File $LogFile -Append }
function S($t) { Write-Host "  [--] $t" -ForegroundColor DarkGray }

function Dur($n) {
    Write-Host "`n  BASARISIZ: $n" -ForegroundColor Red
    Write-Host "  Log: $LogFile" -ForegroundColor Yellow
    $a = Read-Host "  Devam edilsin mi (e/h)"
    if ($a -ne "e") { Read-Host "Cikmak icin Enter"; exit 1 }
}

"Baslangic: $(Get-Date)" | Out-File $LogFile

H "ADIM 1 - SISTEM KONTROLU"
try { $v = python --version 2>&1; G "Python: $v" } catch { R "Python bulunamadi"; Read-Host "Enter"; exit 1 }
try { $v = java -version 2>&1 | Select-Object -First 1; G "Java: $v" } catch { R "Java bulunamadi"; Read-Host "Enter"; exit 1 }

H "ADIM 2 - KLASORLER"
$dirs = @("$AppRoot\logs","$AppRoot\data","$AppRoot\data\quran","$Backend\cache","$Backend\models")
foreach ($d in $dirs) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null; G "Olusturuldu: $d" }
    else { S "Var: $d" }
}

H "ADIM 3 - PYTHON PAKETLERI"
Set-Location $Backend
$req = "$Backend\requirements.txt"
if (Test-Path $req) {
    Y "requirements.txt yukleniyor..."
    $o = pip install -r $req 2>&1
    $o | Out-File $LogFile -Append
    if ($LASTEXITCODE -eq 0) { G "Paketler yuklendi" } else { R "pip install basarisiz"; $o | Select-Object -Last 10 | Write-Host; Dur "Paket kurulum" }
} else {
    foreach ($p in @("flask","flask-cors","requests","numpy")) {
        pip install $p --quiet 2>&1 | Out-File $LogFile -Append
        G "Kuruldu: $p"
    }
}

H "ADIM 4 - YAPILANDIRMA"
if (-not (Test-Path "$Backend\config.json")) {
    '{"version":"1.0","bridge_port":5000,"debug":false}' | Out-File "$Backend\config.json" -Encoding UTF8
    G "config.json olusturuldu"
} else { S "config.json var" }

H "ADIM 5 - GRADLE KONTROL"
Set-Location $AppRoot
if (-not (Test-Path ".\gradlew.bat")) { R "gradlew.bat bulunamadi"; Read-Host "Enter"; exit 1 }
$gv = .\gradlew.bat --version 2>&1
$gv | Out-File $LogFile -Append
if ($LASTEXITCODE -eq 0) { G "Gradle hazir" } else { R "Gradle hatasi"; Dur "Gradle" }

H "ADIM 6 - BACKEND TESTLERI"
Set-Location $Backend
if (Test-Path "test_full.py") {
    Y "test_full.py calistiriliyor..."
    $to = python test_full.py 2>&1
    $to | Out-File $LogFile -Append
    $to | ForEach-Object { Write-Host "  $_" }
    if ($LASTEXITCODE -eq 0) { G "Backend testleri BASARILI" }
    else { R "Backend testleri BASARISIZ"; Dur "Backend Test" }
} else { S "test_full.py yok, atlandi" }

H "ADIM 7 - BRIDGE TESTI"
Set-Location $Backend
if (Test-Path "analyze_bridge.py") {
    Y "Bridge ping testi..."
    $pingJson = '{"cmd":"ping"}'
    $bo = ($pingJson | python analyze_bridge.py 2>&1)
    $bo | Out-File $LogFile -Append
    $fl = ($bo -split "`n")[0].Trim()
    Write-Host "  Yanit: $fl"
    $expected = '{"ok":true,"ready":true}'
    if ($fl -eq $expected) { G "Bridge BASARILI" }
    else {
        R "Bridge yaniti yanlis"
        Write-Host "  Beklenen: $expected" -ForegroundColor Yellow
        Write-Host "  Gelen   : $fl" -ForegroundColor Red
        $bo | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
        Dur "Bridge Test"
    }
} else { S "analyze_bridge.py yok, atlandi" }

H "ADIM 8 - TEMIZLE VE DERLE"
Set-Location $AppRoot
Y "Temizleniyor..."
.\gradlew.bat clean 2>&1 | Out-File $LogFile -Append
if ($LASTEXITCODE -eq 0) { G "Temizlendi" } else { R "Clean basarisiz"; Dur "Clean" }

Y "Derleniyor... (bekliyiniz)"
$bo = .\gradlew.bat ":composeApp:compileKotlin" 2>&1
$bo | Out-File $LogFile -Append
if ($LASTEXITCODE -ne 0) {
    R "Derleme BASARISIZ"
    Write-Host "`n  --- HATALAR ---" -ForegroundColor Red
    $bo | Where-Object { $_ -match "error|FAILED|Exception" } | Select-Object -Last 25 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    if (-not ($bo | Where-Object { $_ -match "error|FAILED|Exception" })) {
        $bo | Select-Object -Last 30 | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
    }
    Write-Host "`n  Log dosyasi: $LogFile" -ForegroundColor Yellow
    Read-Host "`nEnter ile kapat"
    exit 1
}
G "Derleme BASARILI"

H "ADIM 9 - UYGULAMA BASLATILIYOR"
Y "Uygulama aciliyor..."
.\gradlew.bat ":composeApp:run"
if ($LASTEXITCODE -ne 0) { R "Uygulama baslanamadi"; Read-Host "`nEnter ile kapat"; exit 1 }

Write-Host "`n============================================" -ForegroundColor Green
Write-Host "  TAMAMLANDI - Uyari/Hata sayisi: $EC" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
"Bitis: $(Get-Date) | Hata: $EC" | Out-File $LogFile -Append
Read-Host "`nPencereyi kapatmak icin Enter"
