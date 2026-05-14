"""
Test: Proje setupı (harf_acoustics.json, klasör yapısı)
"""
import json
import os
from pathlib import Path


def test_harf_acoustics_exists():
    """harf_acoustics.json dosyası var mı?"""
    path = Path(__file__).parent.parent / "harf_acoustics.json"
    assert path.exists(), f"harf_acoustics.json bulunamadı: {path}"


def test_harf_acoustics_valid_json():
    """harf_acoustics.json geçerli JSON mi?"""
    path = Path(__file__).parent.parent / "harf_acoustics.json"
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    assert isinstance(data, dict), "JSON bir dict olmalı"


def test_harf_acoustics_28_harfler():
    """28 Arapça harf var mı?"""
    path = Path(__file__).parent.parent / "harf_acoustics.json"
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    
    expected_harfler = [
        "ا", "ب", "ت", "ث", "ج", "ح", "خ", "د", "ذ", "ر",
        "ز", "س", "ش", "ص", "ض", "ط", "ظ", "ع", "غ", "ف",
        "ق", "ك", "ل", "م", "ن", "ه", "و", "ي", "ء", "ة"
    ]
    
    for harf in expected_harfler:
        assert harf in data, f"Harf '{harf}' eksik"
    
    assert len(data) >= 28, f"En az 28 harf olmalı, {len(data)} var"


def test_harf_acoustics_fields():
    """Her harfin gerekli alanları var mı?"""
    path = Path(__file__).parent.parent / "harf_acoustics.json"
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    
    required_fields = [
        "isim", "F1_ortalama", "F1_std", "F2_ortalama", "F2_std",
        "F3_ortalama", "F3_std", "spektral_merkez", "sure_ms_tipik",
        "sesli", "nazal_rezonans", "tefhim", "sifat"
    ]
    
    for harf, harf_data in data.items():
        for field in required_fields:
            assert field in harf_data, f"'{harf}' harf: '{field}' alanı eksik"


def test_folder_structure():
    """Gerekli klasörler var mı?"""
    backend_path = Path(__file__).parent.parent
    
    required_dirs = [
        backend_path / "data",
        backend_path / "analysis",
        backend_path / "tests",
        backend_path / "reports",
        backend_path / "reports" / "output"
    ]
    
    for dir_path in required_dirs:
        assert dir_path.exists(), f"Klasör eksik: {dir_path}"
        assert dir_path.is_dir(), f"Bir dosya, klasör değil: {dir_path}"


if __name__ == "__main__":
    import sys
    
    tests = [
        test_harf_acoustics_exists,
        test_harf_acoustics_valid_json,
        test_harf_acoustics_28_harfler,
        test_harf_acoustics_fields,
        test_folder_structure
    ]
    
    failed = 0
    for test in tests:
        try:
            test()
            print(f"✅ {test.__name__}")
        except AssertionError as e:
            print(f"❌ {test.__name__}: {e}")
            failed += 1
    
    if failed > 0:
        print(f"\n{failed} test başarısız")
        sys.exit(1)
    else:
        print(f"\n✅ Tüm {len(tests)} test başarılı!")
