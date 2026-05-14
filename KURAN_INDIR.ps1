# ============================================================
#  KURAN-I KERİM — TAM METİN İNDİRİCİ
#  Kaynak: api.alquran.cloud (ücretsiz, resmi)
#  Çıktı:  data\quran\ klasörüne ayet ayet JSON
# ============================================================

$AppRoot  = "C:\Users\inanc\Desktop\Kuran i Kerim Windows Uygulama"
$HedefDir = "$AppRoot\data\quran"
$LogFile  = "$AppRoot\kuran_indir.log"

# Klasörü oluştur
if (-not (Test-Path $HedefDir)) {
    New-Item -ItemType Directory -Path $HedefDir -Force | Out-Null
}

"Baslangic: $(Get-Date)" | Out-File $LogFile
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  KURAN-I KERIM METIN INDIRICI" -ForegroundColor Cyan
Write-Host "  Kaynak: api.alquran.cloud" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ─── TÜM KURAN'I TEK SEFERDE İNDİR (Ana dosya) ──────────────
Write-Host "  [1/4] Tam Kuran metni indiriliyor (Arapca)..." -ForegroundColor Yellow

try {
    $url = "https://api.alquran.cloud/v1/quran/quran-uthmani"
    $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 120
    $resp | ConvertTo-Json -Depth 20 | Out-File "$HedefDir\kuran_tam.json" -Encoding UTF8
    Write-Host "  [OK] kuran_tam.json indirildi" -ForegroundColor Green
} catch {
    Write-Host "  [!!] Tam Kuran indirilemedi: $_" -ForegroundColor Red
    "HATA tam kuran: $_" | Out-File $LogFile -Append
}

# ─── TÜRKÇE MEAL (Diyanet) ───────────────────────────────────
Write-Host "  [2/4] Turkce meal indiriliyor (Diyanet)..." -ForegroundColor Yellow

try {
    $url = "https://api.alquran.cloud/v1/quran/tr.diyanet"
    $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 120
    $resp | ConvertTo-Json -Depth 20 | Out-File "$HedefDir\kuran_turkce_diyanet.json" -Encoding UTF8
    Write-Host "  [OK] kuran_turkce_diyanet.json indirildi" -ForegroundColor Green
} catch {
    Write-Host "  [!!] Turkce meal indirilemedi: $_" -ForegroundColor Red
}

# ─── SURE SURE AYRI DOSYALAR ─────────────────────────────────
Write-Host "  [3/4] 114 sure ayri ayri indiriliyor..." -ForegroundColor Yellow

$SureDir = "$HedefDir\sureler"
if (-not (Test-Path $SureDir)) {
    New-Item -ItemType Directory -Path $SureDir -Force | Out-Null
}

# Sure isimleri
$SureIsimleri = @(
    "Al-Fatiha","Al-Baqara","Al-Imran","An-Nisa","Al-Maeda",
    "Al-Anam","Al-Araf","Al-Anfal","At-Tawba","Yunus",
    "Hud","Yusuf","Ar-Rad","Ibrahim","Al-Hijr",
    "An-Nahl","Al-Isra","Al-Kahf","Maryam","Ta-Ha",
    "Al-Anbiya","Al-Hajj","Al-Muminun","An-Nur","Al-Furqan",
    "Ash-Shuara","An-Naml","Al-Qasas","Al-Ankabut","Ar-Rum",
    "Luqman","As-Sajda","Al-Ahzab","Saba","Fatir",
    "Ya-Sin","As-Saffat","Sad","Az-Zumar","Ghafir",
    "Fussilat","Ash-Shura","Az-Zukhruf","Ad-Dukhan","Al-Jathiya",
    "Al-Ahqaf","Muhammad","Al-Fath","Al-Hujurat","Qaf",
    "Adh-Dhariyat","At-Tur","An-Najm","Al-Qamar","Ar-Rahman",
    "Al-Waqia","Al-Hadid","Al-Mujadila","Al-Hashr","Al-Mumtahana",
    "As-Saf","Al-Jumua","Al-Munafiqun","At-Taghabun","At-Talaq",
    "At-Tahrim","Al-Mulk","Al-Qalam","Al-Haqqa","Al-Maarij",
    "Nuh","Al-Jinn","Al-Muzzammil","Al-Muddaththir","Al-Qiyama",
    "Al-Insan","Al-Mursalat","An-Naba","An-Naziat","Abasa",
    "At-Takwir","Al-Infitar","Al-Mutaffifin","Al-Inshiqaq","Al-Buruj",
    "At-Tariq","Al-Ala","Al-Ghashiya","Al-Fajr","Al-Balad",
    "Ash-Shams","Al-Layl","Ad-Duha","Ash-Sharh","At-Tin",
    "Al-Alaq","Al-Qadr","Al-Bayyina","Az-Zalzala","Al-Adiyat",
    "Al-Qaria","At-Takathur","Al-Asr","Al-Humaza","Al-Fil",
    "Quraysh","Al-Maun","Al-Kawthar","Al-Kafirun","An-Nasr",
    "Al-Masad","Al-Ikhlas","Al-Falaq","An-Nas"
)

$basarili = 0
$basarisiz = 0

for ($i = 1; $i -le 114; $i++) {
    $sureNo   = $i.ToString("000")
    $sureAdi  = $SureIsimleri[$i - 1]
    $dosyaAdi = "$SureDir\sure_$sureNo`_$sureAdi.json"

    if (Test-Path $dosyaAdi) {
        Write-Host "  [--] $sureNo/$sureAdi zaten var, atlandi" -ForegroundColor DarkGray
        $basarili++
        continue
    }

    try {
        $url  = "https://api.alquran.cloud/v1/surah/$i/quran-uthmani"
        $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 30

        # Ayet listesini düzenli kaydet
        $sureData = @{
            sure_no    = $i
            sure_adi   = $sureAdi
            ayet_sayisi = $resp.data.numberOfAyahs
            ayetler    = @()
        }

        foreach ($ayet in $resp.data.ayahs) {
            $sureData.ayetler += @{
                ayet_no = $ayet.numberInSurah
                ayet_no_genel = $ayet.number
                metin   = $ayet.text
                sayfa   = $ayet.page
                cuz     = $ayet.juz
            }
        }

        $sureData | ConvertTo-Json -Depth 10 | Out-File $dosyaAdi -Encoding UTF8
        Write-Host "  [OK] Sure $i/$sureAdi ($($resp.data.numberOfAyahs) ayet)" -ForegroundColor Green
        $basarili++

        # API'ye yüklenmemek için kısa bekleme
        Start-Sleep -Milliseconds 200

    } catch {
        Write-Host "  [!!] Sure $i HATALI: $_" -ForegroundColor Red
        "HATA Sure $i`: $_" | Out-File $LogFile -Append
        $basarisiz++
        Start-Sleep -Milliseconds 500
    }
}

# ─── AYET AYET TEK DOSYA (düz liste) ─────────────────────────
Write-Host ""
Write-Host "  [4/4] Tum ayetler tek dosyada birlestiriliyor..." -ForegroundColor Yellow

try {
    $url  = "https://api.alquran.cloud/v1/quran/quran-uthmani"
    $resp = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 120

    $tumAyetler = @()
    foreach ($sure in $resp.data.surahs) {
        foreach ($ayet in $sure.ayahs) {
            $tumAyetler += @{
                sure_no         = $sure.number
                sure_adi        = $sure.englishName
                sure_adi_tr     = $sure.name
                ayet_no         = $ayet.numberInSurah
                ayet_no_genel   = $ayet.number
                metin           = $ayet.text
                sayfa           = $ayet.page
                cuz             = $ayet.juz
            }
        }
    }

    $tumAyetler | ConvertTo-Json -Depth 5 | Out-File "$HedefDir\tum_ayetler.json" -Encoding UTF8
    Write-Host "  [OK] tum_ayetler.json — $($tumAyetler.Count) ayet" -ForegroundColor Green

    # CSV olarak da kaydet (kolay okunur)
    $tumAyetler | ForEach-Object {
        [PSCustomObject]@{
            sure_no       = $_.sure_no
            sure_adi      = $_.sure_adi
            ayet_no       = $_.ayet_no
            ayet_no_genel = $_.ayet_no_genel
            metin         = $_.metin
            sayfa         = $_.sayfa
            cuz           = $_.cuz
        }
    } | Export-Csv "$HedefDir\tum_ayetler.csv" -NoTypeInformation -Encoding UTF8
    Write-Host "  [OK] tum_ayetler.csv de olusturuldu" -ForegroundColor Green

} catch {
    Write-Host "  [!!] Birlesik dosya olusturulamadi: $_" -ForegroundColor Red
}

# ─── ÖZET ────────────────────────────────────────────────────
Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  TAMAMLANDI" -ForegroundColor Cyan
Write-Host "  Basarili sure: $basarili / 114" -ForegroundColor Green
if ($basarisiz -gt 0) {
    Write-Host "  Basarisiz sure: $basarisiz" -ForegroundColor Red
}
Write-Host ""
Write-Host "  Dosyalar:" -ForegroundColor White
Write-Host "  $HedefDir\kuran_tam.json         (tam Kuran Arapca)" -ForegroundColor Gray
Write-Host "  $HedefDir\kuran_turkce_diyanet.json (Turkce meal)" -ForegroundColor Gray
Write-Host "  $HedefDir\tum_ayetler.json        (6236 ayet duz liste)" -ForegroundColor Gray
Write-Host "  $HedefDir\tum_ayetler.csv         (Excel ile acilir)" -ForegroundColor Gray
Write-Host "  $HedefDir\sureler\               (114 ayri sure dosyasi)" -ForegroundColor Gray
Write-Host "============================================" -ForegroundColor Cyan
"Bitis: $(Get-Date) | Basarili: $basarili | Basarisiz: $basarisiz" | Out-File $LogFile -Append

Read-Host "`nPencereyi kapatmak icin Enter"
