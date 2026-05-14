"""
e2e_test.py — End-to-End Test (GÜN 7)
Corpus → Analiz → Rapor: Tam pipeline testi
"""
import sys
from pathlib import Path

# Path ayarı
backend_dir = Path(__file__).parent.parent
analysis_dir = backend_dir / "analysis"
sys.path.insert(0, str(analysis_dir))

import json
import time
from feature_vector import text_to_feature_vector
from statistical_engine import run_full_analysis


def test_e2e():
    """End-to-End: Full pipeline in one test"""
    print("="*60)
    print("🔄 END-TO-END TEST (GÜN 7)")
    print("="*60)
    
    start = time.time()
    
    # Test 1: Single text analysis
    print("\n1️⃣  Tek Metin Analizi...")
    test_text = "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"
    
    try:
        fv = text_to_feature_vector(test_text)
        print(f"   ✅ Metin analiz edildi: {len(fv.f1_array)} harf")
    except Exception as e:
        print(f"   ❌ Hata: {e}")
        return False
    
    # Test 2: Full corpus analysis
    print("\n2️⃣  Tam Korpus Analizi...")
    
    try:
        backend_dir = Path(__file__).parent.parent
        quran_path = backend_dir / "data" / "quran_corpus.json"
        control_path = backend_dir / "data" / "control_corpus.json"
        output_dir = backend_dir / "reports" / "output"
        
        results = run_full_analysis(str(quran_path), str(control_path), str(output_dir))
        print(f"   ✅ Analiz tamamlandı")
    except Exception as e:
        print(f"   ❌ Hata: {e}")
        return False
    
    # Test 3: Output files exist
    print("\n3️⃣  Çıktı Dosyaları Kontrol...")
    
    output_dir = Path(__file__).parent.parent / "reports" / "output"
    required_files = [
        "sonuc_ozet.json",
        "quran_metrics.csv",
        "control_metrics.csv",
        "boxplots.png",
        "heatmap_pvalues.png",
        "forest_plot.png",
        "distributions.png",
        "summary_table.png",
    ]
    
    missing = []
    for fname in required_files:
        fpath = output_dir / fname
        if fpath.exists():
            size_mb = fpath.stat().st_size / (1024*1024)
            print(f"   ✅ {fname} ({size_mb:.2f} MB)")
        else:
            print(f"   ❌ {fname} BULUNAMADI")
            missing.append(fname)
    
    # Test 4: Results validity
    print("\n4️⃣  Sonuç Doğruluğu...")
    
    try:
        with open(output_dir / "sonuc_ozet.json", "r", encoding="utf-8") as f:
            summary = json.load(f)
        
        assert 'timestamp' in summary, "Timestamp yok"
        assert 'results' in summary, "Results yok"
        assert len(summary['results']) > 0, "Sonuç sayısı 0"
        
        print(f"   ✅ {len(summary['results'])} metrik test edildi")
        print(f"   ✅ {summary['significant_bonferroni']} Bonferroni-sig sonuç")
        
    except Exception as e:
        print(f"   ❌ JSON Hata: {e}")
        return False
    
    elapsed = time.time() - start
    print(f"\n⏱️  Toplam Süre: {elapsed:.2f} saniye")
    
    if not missing:
        print("\n" + "="*60)
        print("✅ E2E TEST BAŞARILI - TÜM ADIMLAR GEÇTİ")
        print("="*60)
        return True
    else:
        print("\n" + "="*60)
        print(f"❌ E2E TEST BAŞARISIZ - {len(missing)} dosya eksik")
        print("="*60)
        return False


if __name__ == "__main__":
    success = test_e2e()
    sys.exit(0 if success else 1)
