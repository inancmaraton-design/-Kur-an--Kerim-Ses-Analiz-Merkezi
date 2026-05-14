"""
test_positive_control.py — Pozitif Kontrol Testi
Aynı surenin iki kopyası = FARK OLMAMALI
(Sistem çok duyarlı değilse başarılı)
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent / "analysis"))

import pandas as pd
from feature_vector import text_to_feature_vector, compute_summary_metrics
from statistical_engine import compare_groups


def test_positive_control():
    """
    Pozitif Kontrol: Aynı metin 2 kopyası
    BEKLENEN: İstatistiksel olarak FARK OLMAMALI (p>0.05)
    """
    print("✔️  Pozitif Kontrol Testi: Aynı metin 2 kopyası")
    
    # Kur'an Sure 112 (İhlas) - 4 ayet
    test_text = "قُلْ هُوَ اللَّهُ أَحَدٌ اللَّهُ الصَّمَدُ لَمْ يَلِدْ وَلَمْ يُولَدْ وَلَمْ يَكُنْ لَهُ كُفُوًا أَحَدٌ"
    
    # Aynı metni 2 kategoriye böl
    group1_texts = [test_text] * 10  # 10 kere
    group2_texts = [test_text] * 10  # 10 kere
    
    print(f"  Grup 1: {len(group1_texts)} x '{test_text[:40]}...'")
    print(f"  Grup 2: {len(group2_texts)} x (aynı metin)")
    
    # Metriklere dönüştür
    group1_metrics = []
    group2_metrics = []
    
    print("  Metriklendiriliyor...")
    for text in group1_texts:
        fv = text_to_feature_vector(text)
        metrics = compute_summary_metrics(fv)
        group1_metrics.append(metrics)
    
    for text in group2_texts:
        fv = text_to_feature_vector(text)
        metrics = compute_summary_metrics(fv)
        group2_metrics.append(metrics)
    
    group1_df = pd.DataFrame(group1_metrics)
    group2_df = pd.DataFrame(group2_metrics)
    
    # İstatistiksel test (F2 ortalaması)
    result = compare_groups(group1_df, group2_df, "f2_ortalama")
    
    print(f"\n  F2 Ortalaması Karşılaştırması:")
    print(f"    Grup 1: {result['quran_mean']:.2f} ± {result['quran_std']:.2f}")
    print(f"    Grup 2: {result['control_mean']:.2f} ± {result['control_std']:.2f}")
    print(f"    p-value: {result['p_value']:.6f}")
    print(f"    Cohen's d: {result['cohen_d']:.3f}")
    
    if not result['anlamli_mi']:
        print("  ✅ BEKLENEN: Fark anlamsız (p>0.05) — Sistem uygun duyarlılıkta")
        return True
    else:
        print("  ⚠️  UYARI: Fark anlamlı (p<0.05) — Sistem aşırı duyarlı olabilir")
        return False


if __name__ == "__main__":
    print("="*60)
    print("POZİTİF KONTROL DOĞRULAMA")
    print("="*60)
    
    result = test_positive_control()
    
    print("\n" + "="*60)
    if result:
        print("✅ POZİTİF KONTROL TESTİ BAŞARILI")
    else:
        print("⚠️  POZİTİF KONTROL TESTİ UYARI (aşırı duyarlılık)")
    print("="*60)
