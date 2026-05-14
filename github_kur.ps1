# ================================================================
#  Kur'an-i Kerim Akustik Analiz - GitHub Kurulum Scripti
#  Calistirmak icin: Sag tik -> "PowerShell ile Calistir"
#  Veya PowerShell'i Yonetici olarak ac, scripti surukle birak
# ================================================================

$ErrorActionPreference = "Stop"
$ProjeKlasoru = "C:\Users\inanc\Desktop\Kuran i Kerim Windows Uygulama"

# --- Renk fonksiyonlari ---
function Yaz($mesaj, $renk = "White") { Write-Host $mesaj -ForegroundColor $renk }
function Baslik($mesaj) {
    Yaz ""
    Yaz "================================================================" Cyan
    Yaz "  $mesaj" Cyan
    Yaz "================================================================" Cyan
}
function Tamam($mesaj)  { Yaz "  [OK] $mesaj" Green }
function Bilgi($mesaj)  { Yaz "  [>>] $mesaj" Yellow }
function Hata($mesaj)   { Yaz "  [!!] $mesaj" Red }

# ================================================================
Baslik "ADIM 1/6 — Klasor Kontrolu"
# ================================================================

if (-not (Test-Path $ProjeKlasoru)) {
    Hata "Klasor bulunamadi: $ProjeKlasoru"
    Hata "Lutfen klasor yolunu kontrol edin."
    pause; exit 1
}

Set-Location $ProjeKlasoru
Tamam "Klasore girildi: $ProjeKlasoru"

# Alt klasor boyutlarini goster
Bilgi "Klasor boyutlari hesaplaniyor..."
Yaz ""
Get-ChildItem -Directory | ForEach-Object {
    $boyut = (Get-ChildItem $_.FullName -Recurse -File -ErrorAction SilentlyContinue |
              Measure-Object -Property Length -Sum).Sum
    $boyutMB = [math]::Round($boyut / 1MB, 1)
    $boyutGB = [math]::Round($boyut / 1GB, 2)
    if ($boyutGB -ge 1) {
        Yaz ("    {0,-35} {1,8} GB  <-- BUYUK" -f $_.Name, $boyutGB) Red
    } elseif ($boyutMB -ge 100) {
        Yaz ("    {0,-35} {1,8} MB  <-- orta" -f $_.Name, $boyutMB) Yellow
    } else {
        Yaz ("    {0,-35} {1,8} MB" -f $_.Name, $boyutMB) Gray
    }
}
Yaz ""

# ================================================================
Baslik "ADIM 2/6 — .gitignore Olusturuluyor"
# ================================================================

$gitignore = @"
# Python sanal ortami (cok buyuk, GitHub'a gitmemeli)
.venv/
venv/
env/
ENV/

# Python onbellek
__pycache__/
*.py[cod]
*.pyo
*.pyd
*.egg-info/
dist/
build/

# Uretilen ses dosyalari
*.wav
*.mp3
*.flac
reports/output/

# Buyuk veri dosyalari (istege bagli, kucukse silebilirsin bu satiri)
# backend/data/

# IDE dosyalari
.idea/
.vscode/
*.iml
*.suo
*.user

# Windows sistem dosyalari
Thumbs.db
desktop.ini
ehthumbs.db
$RECYCLE.BIN/

# Gradle/Kotlin build
.gradle/
build/
*.class
out/

# Log dosyalari
*.log
logs/

# Gecici dosyalar
*.tmp
*.temp
.DS_Store
"@

$gitignore | Out-File -FilePath ".gitignore" -Encoding UTF8 -Force
Tamam ".gitignore olusturuldu"

# ================================================================
Baslik "ADIM 3/6 — Git Kontrolu"
# ================================================================

try {
    $gitVersiyon = git --version 2>&1
    Tamam "Git bulundu: $gitVersiyon"
} catch {
    Hata "Git yuklu degil!"
    Bilgi "https://git-scm.com/download/win adresinden indirip kurun."
    pause; exit 1
}

# Git repo var mi kontrol et
if (Test-Path ".git") {
    Tamam "Mevcut Git deposu bulundu, devam ediliyor."
} else {
    Bilgi "Git deposu baslatiliyor..."
    git init
    git branch -M main
    Tamam "Git deposu olusturuldu."
}

# Git kimlik kontrolu
$gitIsim = git config user.name 2>&1
$gitEmail = git config user.email 2>&1

if (-not $gitIsim -or $gitIsim -match "error") {
    Yaz ""
    Bilgi "Git icin ad ve e-posta gerekli (GitHub'da gozukur):"
    $isim = Read-Host "  Adiniz Soyadiniz"
    $email = Read-Host "  E-posta adresiniz"
    git config --global user.name  "$isim"
    git config --global user.email "$email"
    Tamam "Git kimlik bilgileri kaydedildi."
} else {
    Tamam "Git kimlik: $gitIsim / $gitEmail"
}

# ================================================================
Baslik "ADIM 4/6 — Dosyalar Ekleniyor ve Commit Yapiliyor"
# ================================================================

Bilgi "Tum dosyalar ekleniyor (.gitignore kurallari uygulanarak)..."
git add .

# Neyin eklendigini goster
$eklenen = git status --short | Measure-Object -Line
Tamam ("$($eklenen.Lines) dosya/degisiklik eklendi")

Bilgi "Ilk commit yapiliyor..."
$tarih = Get-Date -Format "yyyy-MM-dd"
git commit -m "feat: Kuran Akustik Analiz Merkezi v7.0 - ilk yuklem ($tarih)"
Tamam "Commit tamamlandi."

# ================================================================
Baslik "ADIM 5/6 — GitHub'a Baglanma"
# ================================================================

Yaz ""
Bilgi "GitHub'da once bos bir repo olusturun:"
Yaz "    1. github.com adresine gidin" White
Yaz "    2. Sag ust kose + -> New repository" White
Yaz "    3. Name: kuran-akustik-analiz" White
Yaz "    4. Public secin" White
Yaz "    5. HICBIR SEYI isaretlemeyin (README, gitignore, license YOK)" White
Yaz "    6. 'Create repository' tiklayin" White
Yaz ""

$githubKullanici = Read-Host "  GitHub kullanici adinizi girin"
$repoAdi = Read-Host "  Repo adi (Enter = kuran-akustik-analiz)"
if (-not $repoAdi) { $repoAdi = "kuran-akustik-analiz" }

$repoURL = "https://github.com/$githubKullanici/$repoAdi.git"
Bilgi "Baglanilacak URL: $repoURL"

# Mevcut remote varsa kaldir
$mevcutRemote = git remote 2>&1
if ($mevcutRemote -match "origin") {
    git remote remove origin
}

git remote add origin $repoURL
Tamam "GitHub reposu baglandi."

# ================================================================
Baslik "ADIM 6/6 — GitHub'a Yukleniyor"
# ================================================================

Bilgi "Kodlar GitHub'a yukleniyor..."
Bilgi "(Ilk yuklemede tarayici acilip giris yapmaniz istenebilir)"
Yaz ""

try {
    git push -u origin main
    Yaz ""
    Yaz "================================================================" Green
    Yaz "  BASARILI! Proje GitHub'a yuklendi." Green
    Yaz "  Adres: https://github.com/$githubKullanici/$repoAdi" Green
    Yaz "================================================================" Green
} catch {
    Hata "Yukleme basarisiz oldu."
    Bilgi "Asagidaki komutu elle calistirin:"
    Yaz "    git push -u origin main" White
}

Yaz ""
Bilgi "Gunluk kullanim (kod degistirince):"
Yaz "    git add ."                                     White
Yaz "    git commit -m 'degisiklik aciklamasi'"         White
Yaz "    git push"                                      White
Yaz ""
pause
