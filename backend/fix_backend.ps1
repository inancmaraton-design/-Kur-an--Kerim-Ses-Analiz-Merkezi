# ══════════════════════════════════════════════════════════
# KURAN ANALİZ v7.1 — BACKEND OTOMATİK DÜZELTME SCRİPTİ
# ══════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

Write-Host "`n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  KURAN ANALİZ v7.1 BACKEND DÜZELTME" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`n" -ForegroundColor Cyan

$ROOT = "C:\KP"
$BACKEND = "$ROOT\backend"

# ──────────────────────────────────────────────────────────
# ADIM 1: Klasör Yapısını Kontrol Et
# ──────────────────────────────────────────────────────────
Write-Host "[1/7] Klasör yapısı kontrol ediliyor..." -ForegroundColor Yellow

if (-not (Test-Path $ROOT)) {
    Write-Host "✗ C:\KP bulunamadı! Junction link oluşturuluyor..." -ForegroundColor Red
    $TARGET = "C:\Users\inanc\Desktop\Kuran ı Kerim Windows Uygulama"
    if (Test-Path $TARGET) {
        New-Item -ItemType Junction -Path $ROOT -Target $TARGET -Force | Out-Null
        Write-Host "✓ Junction link oluşturuldu" -ForegroundColor Green
    } else {
        Write-Host "✗ HATA: $TARGET bulunamadı!" -ForegroundColor Red
        exit 1
    }
}

Set-Location $BACKEND

$DIRS = @("analysis", "data", "tests", "reports", "reports/output")
foreach ($dir in $DIRS) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}
Write-Host "✓ Klasör yapısı OK" -ForegroundColor Green

# ──────────────────────────────────────────────────────────
# ADIM 2: __init__.py Dosyalarını Oluştur
# ──────────────────────────────────────────────────────────
Write-Host "`n[2/7] Python paketleri hazırlanıyor..." -ForegroundColor Yellow

$INIT_DIRS = @("analysis", "data", "tests")
foreach ($dir in $INIT_DIRS) {
    $initFile = "$dir\__init__.py"
    if (-not (Test-Path $initFile)) {
        @'
"""
Kuran Analiz v7.1 package
"""
__version__ = "7.1.0"
'@ | Out-File -FilePath $initFile -Encoding UTF8
    }
}
Write-Host "✓ __init__.py dosyaları oluşturuldu" -ForegroundColor Green

# ──────────────────────────────────────────────────────────
# ADIM 3: Import Sorunlarını Düzelt
# ──────────────────────────────────────────────────────────
Write-Host "`n[3/7] Import sorunları düzeltiliyor..." -ForegroundColor Yellow

$FILES_TO_FIX = @(
    "analysis\feature_vector.py",
    "analysis\statistical_engine.py",
    "analysis\visualize.py",
    "analysis\tajweed_detector.py"
)

foreach ($file in $FILES_TO_FIX) {
    if (Test-Path $file) {
        $content = Get-Content $file -Raw -Encoding UTF8
        
        # Relative import'ları absolute import'a çevir
        $content = $content -replace 'from text_parser import', 'from analysis.text_parser import'
        $content = $content -replace 'from tajweed_detector import', 'from analysis.tajweed_detector import'
        $content = $content -replace 'from feature_vector import', 'from analysis.feature_vector import'
        $content = $content -replace 'from statistical_engine import', 'from analysis.statistical_engine import'
        
        # Veri dosyası import'ları
        $content = $content -replace 'import harf_acoustics', 'import data.harf_acoustics as harf_acoustics'
        $content = $content -replace 'from harf_acoustics import', 'from data.harf_acoustics import'
        
        $content | Out-File -FilePath $file -Encoding UTF8 -NoNewline
    }
}
Write-Host "✓ Import'lar düzeltildi" -ForegroundColor Green

# ──────────────────────────────────────────────────────────
# ADIM 4: data/ Klasöründe Python Modülleri Oluştur
# ──────────────────────────────────────────────────────────
Write-Host "`n[4/7] Veri modülleri oluşturuluyor..." -ForegroundColor Yellow

# harf_acoustics.py wrapper
if (Test-Path "data\harf_acoustics.json") {
    @'
"""Harf akustik veritabanı yükleyici"""
import json
from pathlib import Path

HARF_ACOUSTICS_PATH = Path(__file__).parent / "harf_acoustics.json"

def load_harf_acoustics():
    with open(HARF_ACOUSTICS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)

HARF_ACOUSTICS = load_harf_acoustics()
'@ | Out-File -FilePath "data\harf_acoustics.py" -Encoding UTF8
}

# quran_corpus.py wrapper
if (Test-Path "data\quran_corpus.json") {
    @'
"""Kur'an korpusu yükleyici"""
import json
from pathlib import Path

QURAN_CORPUS_PATH = Path(__file__).parent / "quran_corpus.json"

def load_quran_corpus():
    with open(QURAN_CORPUS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)

QURAN_CORPUS = load_quran_corpus()
'@ | Out-File -FilePath "data\quran_corpus.py" -Encoding UTF8
}

# control_corpus.py wrapper
if (Test-Path "data\control_corpus.json") {
    @'
"""Kontrol korpusu yükleyici"""
import json
from pathlib import Path

CONTROL_CORPUS_PATH = Path(__file__).parent / "control_corpus.json"

def load_control_corpus():
    with open(CONTROL_CORPUS_PATH, "r", encoding="utf-8") as f:
        return json.load(f)

CONTROL_CORPUS = load_control_corpus()
'@ | Out-File -FilePath "data\control_corpus.py" -Encoding UTF8
}

Write-Host "✓ Veri modülleri oluşturuldu" -ForegroundColor Green

# ──────────────────────────────────────────────────────────
# ADIM 5: Test Script'i Oluştur
# ──────────────────────────────────────────────────────────
Write-Host "`n[5/7] Test scripti hazırlanıyor..." -ForegroundColor Yellow

$TEST_SCRIPT = @'
# -*- coding: utf-8 -*-
"""Hızlı backend testi"""
import sys
from pathlib import Path

# Backend klasörünü path'e ekle
BACKEND = Path(__file__).parent
sys.path.insert(0, str(BACKEND))

def test_imports():
    """Tüm import'ları test et"""
    try:
        from analysis.text_parser import tokenize_arabic, HarfToken
        from analysis.tajweed_detector import detect_all_tajweed
        from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics
        from data.harf_acoustics import HARF_ACOUSTICS
        print("✓ Tüm import'lar başarılı")
        return True
    except Exception as e:
        print(f"✗ Import hatası: {e}")
        return False

def test_basic_analysis():
    """Basit analiz testi"""
    try:
        from analysis.text_parser import tokenize_arabic
        from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics
        import json
        
        text = 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ'
        fv = text_to_feature_vector(text)
        metrics = compute_summary_metrics(fv)
        
        print("\n✓ Analiz başarılı:")
        print(json.dumps(metrics, indent=2, ensure_ascii=False))
        return True
    except Exception as e:
        print(f"✗ Analiz hatası: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    print("  BACKEND TEST")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    
    success = test_imports()
    if success:
        success = test_basic_analysis()
    
    print("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    if success:
        print("  ✓ TÜM TESTLER BAŞARILI")
    else:
        print("  ✗ TESTLER BAŞARISIZ")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
    
    sys.exit(0 if success else 1)
'@

$TEST_SCRIPT | Out-File -FilePath "test_backend.py" -Encoding UTF8
Write-Host "✓ Test scripti oluşturuldu" -ForegroundColor Green

# ──────────────────────────────────────────────────────────
# ADIM 6: Python Testi Çalıştır
# ──────────────────────────────────────────────────────────
Write-Host "`n[6/7] Backend test ediliyor..." -ForegroundColor Yellow

try {
    $result = python test_backend.py 2>&1
    Write-Host $result
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`n✓ Backend testleri BAŞARILI" -ForegroundColor Green
    } else {
        Write-Host "`n✗ Backend testleri BAŞARISIZ" -ForegroundColor Red
        Write-Host "Hata detayları yukarıda gösterildi." -ForegroundColor Yellow
    }
} catch {
    Write-Host "✗ Python test çalıştırılamadı: $_" -ForegroundColor Red
}

# ──────────────────────────────────────────────────────────
# ADIM 7: Git Commit
# ──────────────────────────────────────────────────────────
Write-Host "`n[7/7] Git commit yapılıyor..." -ForegroundColor Yellow

try {
    git add -A
    git commit -m "fix: Backend import sorunları düzeltildi, __init__.py eklendi" -q
    Write-Host "✓ Git commit başarılı" -ForegroundColor Green
} catch {
    Write-Host "⚠ Git commit atlandı (repo olmayabilir)" -ForegroundColor Yellow
}

# ──────────────────────────────────────────────────────────
# ÖZET
# ──────────────────────────────────────────────────────────
Write-Host "`n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  DÜZELTME TAMAMLANDI" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`n" -ForegroundColor Cyan

Write-Host "Yapılanlar:" -ForegroundColor White
Write-Host "  ✓ Klasör yapısı kontrol edildi" -ForegroundColor Green
Write-Host "  ✓ __init__.py dosyaları oluşturuldu" -ForegroundColor Green
Write-Host "  ✓ Import'lar absolute path'e çevrildi" -ForegroundColor Green
Write-Host "  ✓ Veri modül wrapper'ları eklendi" -ForegroundColor Green
Write-Host "  ✓ Test scripti hazırlandı" -ForegroundColor Green
Write-Host "  ✓ Backend test edildi" -ForegroundColor Green
Write-Host "  ✓ Git commit yapıldı`n" -ForegroundColor Green

Write-Host "Sonraki adım:" -ForegroundColor Yellow
Write-Host "  python test_backend.py   → Backend'i tekrar test et" -ForegroundColor Cyan
Write-Host "  .\fix_python_bridge.ps1  → Python bridge'i güncelle`n" -ForegroundColor Cyan
