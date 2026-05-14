$ErrorActionPreference = "Stop"

function Baslik($m) {
    Write-Host ""
    Write-Host "===============================================" -ForegroundColor Cyan
    Write-Host "  $m" -ForegroundColor Cyan
    Write-Host "===============================================" -ForegroundColor Cyan
}
function Tamam($m) { Write-Host "  [OK] $m" -ForegroundColor Green }
function Bilgi($m) { Write-Host "  [>>] $m" -ForegroundColor Yellow }
function Hata($m)  { Write-Host "  [!!] $m" -ForegroundColor Red }

Baslik "ADIM 1 - Yonetici Yetkisi Kontrolu"

$yonetici = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]"Administrator")
if (-not $yonetici) {
    Hata "Yonetici yetkisi gerekli!"
    Hata "PowerShell'i kapat, Baslat -> PowerShell -> Sag tik -> Yonetici olarak calistir"
    pause
    exit 1
}
Tamam "Yonetici yetkisi tamam."

Baslik "ADIM 2 - Git Kontrolu ve Kurulumu"

$gitYuklu = $false
try {
    $gitVer = & git --version 2>&1
    if ("$gitVer" -match "git version") {
        Tamam "Git zaten yuklu: $gitVer"
        $gitYuklu = $true
    }
} catch {}

if (-not $gitYuklu) {
    Bilgi "Git kuruluyor..."
    winget install --id Git.Git -e --source winget --silent --accept-package-agreements --accept-source-agreements
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    Start-Sleep -Seconds 3
    try {
        $gitVer = & git --version 2>&1
        Tamam "Git kuruldu: $gitVer"
    } catch {
        Hata "Git kuruldu fakat bilgisayari yeniden baslatmaniz gerekiyor."
        pause
        exit 1
    }
}

Baslik "ADIM 3 - Python Kontrolu ve Kurulumu"

$pyYuklu = $false
try {
    $pyVer = & python --version 2>&1
    if ("$pyVer" -match "Python 3") {
        Tamam "Python zaten yuklu: $pyVer"
        $pyYuklu = $true
    }
} catch {}

if (-not $pyYuklu) {
    Bilgi "Python 3.12 kuruluyor..."
    winget install --id Python.Python.3.12 -e --source winget --silent --accept-package-agreements --accept-source-agreements
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    Start-Sleep -Seconds 3
    try {
        $pyVer = & python --version 2>&1
        Tamam "Python kuruldu: $pyVer"
    } catch {
        Hata "Python kuruldu fakat bilgisayari yeniden baslatmaniz gerekiyor."
        pause
        exit 1
    }
}

Baslik "ADIM 4 - Python Kutuphaneleri"

$proje = Get-Location
$venv = Join-Path $proje ".venv"

if (-not (Test-Path $venv)) {
    Bilgi "Sanal ortam olusturuluyor..."
    & python -m venv "$venv"
    Tamam "Sanal ortam olusturuldu."
} else {
    Tamam "Sanal ortam zaten var."
}

$pip = Join-Path $venv "Scripts\pip.exe"
Bilgi "Kutuphaneler yukleniyor (3-5 dakika surebilir)..."
& $pip install numpy scipy librosa soundfile scikit-learn statsmodels matplotlib seaborn arabic-reshaper python-bidi --quiet
Tamam "Python kutuphaneleri yuklendi."

Baslik "ADIM 5 - gitignore ve Git Deposu"

$gitignore = ".venv/`n__pycache__/`n*.pyc`n*.wav`n*.mp3`nreports/output/`n.idea/`n.vscode/`nThumbs.db`ndesktop.ini`n.gradle/`nbuild/"
$gitignore | Out-File -FilePath ".gitignore" -Encoding UTF8 -Force
Tamam ".gitignore olusturuldu."

if (-not (Test-Path ".git")) {
    & git init
    & git branch -M main
    Tamam "Git deposu olusturuldu."
} else {
    Tamam "Git deposu zaten var."
}

$kimlik = & git config user.name 2>&1
if (-not $kimlik -or "$kimlik" -eq "") {
    Write-Host ""
    $isim  = Read-Host "  Adiniz Soyadiniz"
    $email = Read-Host "  E-posta adresiniz"
    & git config --global user.name "$isim"
    & git config --global user.email "$email"
    Tamam "Kimlik kaydedildi."
} else {
    Tamam "Git kimlik: $kimlik"
}

& git add .
$degisiklik = (& git status --short 2>&1 | Measure-Object -Line).Lines
if ($degisiklik -gt 0) {
    $tarih = Get-Date -Format "yyyy-MM-dd"
    & git commit -m "feat: Kuran Akustik Analiz v7.0 ilk yuklem ($tarih)"
    Tamam "Commit yapildi."
} else {
    Tamam "Yeni degisiklik yok."
}

Baslik "ADIM 6 - GitHub'a Yukleme"

Write-Host ""
Write-Host "  Tarayicinizda su adimlari yapin:" -ForegroundColor White
Write-Host "  1. github.com -> giris yapin" -ForegroundColor White
Write-Host "  2. Sag ust + -> New repository" -ForegroundColor White
Write-Host "  3. Name: kuran-akustik-analiz" -ForegroundColor White
Write-Host "  4. Public secin, HICBIR SEYI isaretlemeyin" -ForegroundColor White
Write-Host "  5. Create repository tiklayin" -ForegroundColor White
Write-Host ""

$kullanici = Read-Host "  GitHub kullanici adinizi girin"
$repoURL = "https://github.com/$kullanici/kuran-akustik-analiz.git"

$mevcutRemote = & git remote 2>&1
if ("$mevcutRemote" -match "origin") {
    & git remote remove origin
}
& git remote add origin $repoURL

Bilgi "GitHub'a yukleniyor..."
try {
    & git push -u origin main
    Write-Host ""
    Write-Host "  BASARILI!" -ForegroundColor Green
    Write-Host "  https://github.com/$kullanici/kuran-akustik-analiz" -ForegroundColor Green
} catch {
    Hata "Yukleme basarisiz. Asagidaki komutu elle calistirin:"
    Write-Host "  git push -u origin main" -ForegroundColor White
}

Write-Host ""
pause
