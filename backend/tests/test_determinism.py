"""
test_determinism.py — Determinizm Testi
Aynı metin 100 kere analiz edildiğinde bit-bit aynı sonuç veriyor mu?
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent / "analysis"))

import numpy as np
from feature_vector import text_to_feature_vector, compute_summary_metrics


def test_determinism():
    """Determinizm test: 100 run aynı sonuç?"""
    test_text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"
    
    print("🔄 Determinizm Testi: 100 run")
    print(f"Metin: {test_text[:50]}...")
    
    results = []
    for i in range(100):
        fv = text_to_feature_vector(test_text)
        metrics = compute_summary_metrics(fv)
        results.append(metrics)
    
    # İlk sonuçla karşılaştır
    first_metrics = results[0]
    all_same = True
    
    for i, metrics in enumerate(results[1:], 1):
        for key in first_metrics.keys():
            if abs(first_metrics[key] - metrics[key]) > 1e-15:
                print(f"❌ Run {i+1}: {key} değişti!")
                print(f"   İlk: {first_metrics[key]}")
                print(f"   Son: {metrics[key]}")
                all_same = False
                break
        if not all_same:
            break
    
    if all_same:
        print("✅ Tüm 100 run bit-bit AYNI sonuç verdi!")
        return True
    else:
        print("❌ Determinizm başarısız!")
        return False


def test_array_determinism():
    """Feature vector array'leri de bit-bit aynı?"""
    test_text = "قُلْ هُوَ اللَّهُ أَحَدٌ اللَّهُ الصَّمَدُ"
    
    print("\n🔢 Array Determinizm Testi")
    
    fv_first = text_to_feature_vector(test_text)
    
    for run in range(20):
        fv_test = text_to_feature_vector(test_text)
        
        if not (np.array_equal(fv_test.f1_array, fv_first.f1_array) and
                np.array_equal(fv_test.f2_array, fv_first.f2_array) and
                np.array_equal(fv_test.f3_array, fv_first.f3_array) and
                np.array_equal(fv_test.sure_array, fv_first.sure_array)):
            print(f"❌ Run {run+1}: Array farklı!")
            return False
    
    print("✅ Tüm 20 run array'leri bit-bit AYNI!")
    return True


if __name__ == "__main__":
    print("="*60)
    print("DETERMİNİZM DOĞRULAMA")
    print("="*60)
    
    test1 = test_determinism()
    test2 = test_array_determinism()
    
    print("\n" + "="*60)
    if test1 and test2:
        print("✅ DETERMİNİZM TESTİ BAŞARILI")
    else:
        print("❌ DETERMİNİZM TESTİ BAŞARISIZ")
    print("="*60)
