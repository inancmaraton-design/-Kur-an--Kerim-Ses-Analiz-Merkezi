# -*- coding: utf-8 -*-
"""
Kuran Analiz v7.1 - Tam Sistem Testi
Çalıştır: python test_full.py
"""
import sys
import json
import subprocess
from pathlib import Path

# Backend'i path'e ekle
BACKEND = Path(__file__).parent
sys.path.insert(0, str(BACKEND))


def test_imports():
    print("=" * 60)
    print("1. IMPORT TESTLERİ")
    print("=" * 60)

    from data.harf_acoustics import HARF_ACOUSTICS
    print(f"  ✓ harf_acoustics: {len(HARF_ACOUSTICS)} harf")
    assert len(HARF_ACOUSTICS) == 28, f"28 harf bekleniyor, {len(HARF_ACOUSTICS)} bulundu"

    from data.quran_corpus import QURAN_CORPUS
    print(f"  ✓ quran_corpus: {len(QURAN_CORPUS)} sure")
    assert len(QURAN_CORPUS) > 0, "Kur'an korpusu boş"

    from data.control_corpus import CONTROL_CORPUS
    print(f"  ✓ control_corpus: {len(CONTROL_CORPUS)} metin")
    assert len(CONTROL_CORPUS) > 0, "Kontrol korpusu boş"

    from analysis.text_parser import tokenize_arabic
    print(f"  ✓ text_parser")

    from analysis.tajweed_detector import detect_all_tajweed
    print(f"  ✓ tajweed_detector")

    from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics
    print(f"  ✓ feature_vector")

    from analysis.statistical_engine import compare_groups, bonferroni_correction
    print(f"  ✓ statistical_engine")

    print()
    return True


def test_corpus_format():
    print("=" * 60)
    print("2. CORPUS FORMAT TESTİ")
    print("=" * 60)

    from data.quran_corpus import QURAN_CORPUS
    from data.control_corpus import CONTROL_CORPUS

    # İlk Kur'an öğesini kontrol et
    first_k = next(iter(QURAN_CORPUS.values()))
    required_fields = ["id", "metin"]
    for field in required_fields:
        assert field in first_k, f"Kur'an öğesinde '{field}' alanı eksik"
    print(f"  ✓ Kur'an formatı OK — örnek: id={first_k['id']}, ayet_sayisi={first_k.get('ayet_sayisi', '?')}")

    # İlk kontrol öğesini kontrol et
    first_c = next(iter(CONTROL_CORPUS.values()))
    assert "metin" in first_c, "Kontrol öğesinde 'metin' alanı eksik"
    print(f"  ✓ Kontrol formatı OK — örnek: id={first_c['id']}, kategori={first_c.get('kategori', '?')}")

    print()
    return True


def test_analiz():
    print("=" * 60)
    print("3. ANALİZ TESTİ")
    print("=" * 60)

    from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics

    text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
    print(f"  Metin: {text}")

    fv = text_to_feature_vector(text)
    print(f"  ✓ Feature vector: {len(fv.f2_array)} harf")

    metrics = compute_summary_metrics(fv)
    print(f"  ✓ Metrikler: {len(metrics)} adet")

    assert "f2_ortalama" in metrics, "f2_ortalama metriği eksik"
    assert "nazal_oran" in metrics, "nazal_oran metriği eksik"
    print(f"  ✓ Örnek metrik — f2_ortalama: {metrics['f2_ortalama']:.2f}")

    print()
    return True


def test_bridge():
    print("=" * 60)
    print("4. PYTHON BRIDGE TESTİ")
    print("=" * 60)

    bridge_py = BACKEND / "analyze_bridge.py"
    assert bridge_py.exists(), f"analyze_bridge.py bulunamadı: {bridge_py}"

    # Test 1: korpus_listele
    print("  Test: korpus_listele...")
    proc = subprocess.Popen(
        [sys.executable, str(bridge_py)],
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        cwd=str(BACKEND),
        text=True,
        encoding="utf-8",
    )
    cmd = json.dumps({"cmd": "korpus_listele"}) + "\n"
    stdout, stderr = proc.communicate(input=cmd, timeout=15)

    if stderr:
        print(f"  stderr: {stderr[:500]}")

    lines = stdout.strip().splitlines()
    # Eğer ilk satır "ready" sinyali ise onu atla ve asıl yanıtı al
    response_json = lines[-1] if lines else ""

    try:
        response = json.loads(response_json)
    except json.JSONDecodeError as e:
        print(f"  ✗ JSON parse hatası: {e}")
        print(f"  stdout: {stdout[:500]}")
        return False

    if "error" in response:
        print(f"  ✗ Bridge hatası: {response['error']}")
        if "traceback" in response:
            print(f"  traceback:\n{response['traceback']}")
        return False

    kuran_n = len(response.get("kuran", []))
    kontrol_n = len(response.get("kontrol", []))
    print(f"  ✓ Bridge çalışıyor — Kur'an: {kuran_n} sure, Kontrol: {kontrol_n} metin")

    # Yanıt formatını kontrol et
    if kuran_n > 0:
        k_item = response["kuran"][0]
        assert "id" in k_item, "Kur'an öğesinde 'id' eksik"
        assert "isim" in k_item, "Kur'an öğesinde 'isim' eksik"
        assert "ayet_sayisi" in k_item, "Kur'an öğesinde 'ayet_sayisi' eksik"
        print(f"  ✓ Yanıt formatı OK — örnek: {k_item}")

    print()
    return True


def main():
    print()
    print("╔══════════════════════════════════════════════════════════╗")
    print("║      Kuran Analiz v7.1 — Tam Sistem Testi               ║")
    print("╚══════════════════════════════════════════════════════════╝")
    print()

    tests = [
        ("Import testleri", test_imports),
        ("Corpus format", test_corpus_format),
        ("Analiz motoru", test_analiz),
        ("Python bridge", test_bridge),
    ]

    passed = 0
    failed = 0

    for name, fn in tests:
        try:
            ok = fn()
            if ok:
                passed += 1
            else:
                failed += 1
                print(f"  ✗ {name} BAŞARISIZ\n")
        except Exception as e:
            failed += 1
            print(f"  ✗ {name} HATA: {e}")
            import traceback
            traceback.print_exc()
            print()

    print("═" * 60)
    if failed == 0:
        print(f"  ✅ TÜM TESTLER BAŞARILI ({passed}/{passed})")
    else:
        print(f"  ❌ {failed} TEST BAŞARISIZ ({passed}/{passed + failed} geçti)")
    print("═" * 60)
    print()

    return failed == 0


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
