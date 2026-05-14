"""
test_negative_control.py — Negatif Kontrol Testi
Rastgele Arapça harf dizisi vs Kur'an = FARK OLMALı
(Sistem bozuksa fark çıkmaz)
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent / "analysis"))

import random
import numpy as np
from feature_vector import text_to_feature_vector, compute_summary_metrics
from statistical_engine import compare_groups
import pandas as pd


def generate_random_arabic(n_texts=30, min_len=50, max_len=150):
    """Rastgele Arapça harf dizileri üret"""
    harfler = [
        'ا', 'ب', 'ت', 'ث', 'ج', 'ح', 'خ', 'د', 'ذ', 'ر',
        'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ع', 'غ', 'ف',
        'ق', 'ك', 'ل', 'م', 'ن', 'ه', 'و', 'ي'
    ]
    
    texts = []
    for _ in range(n_texts):
        length = random.randint(min_len, max_len)
        text = ''.join(random.choices(harfler, k=length))
        texts.append(text)
    
    return texts


def test_negative_control():
    """
    Negatif Kontrol: Rastgele metinler vs Kur'an
    BEKLENEN: İstatistiksel olarak anlamlı FARK olmalı
    (Sistem normal çalışıyorsa)
    """
    print("🎲 Negatif Kontrol Testi: Rastgele Arapça vs Kur'an")
    
    # Kur'an surelerinden örnek
    from pathlib import Path
    import json
    
    project_dir = Path(__file__).parent.parent
    with open(project_dir / "data" / "quran_corpus.json", "r", encoding="utf-8") as f:
        quran_data = json.load(f)
    
    quran_texts = [s["metin"][:200] for s in quran_data["sureler"][:20]]  # İlk 20 sure
    random_texts = generate_random_arabic(n_texts=20)
    
    print(f"  Kur'an: {len(quran_texts)} metin")
    print(f"  Rastgele: {len(random_texts)} metin")
    
    # Metriklere dönüştür
    quran_metrics = []
    random_metrics = []
    
    print("  Metriklendiriliyor...")
    for text in quran_texts:
        fv = text_to_feature_vector(text)
        metrics = compute_summary_metrics(fv)
        quran_metrics.append(metrics)
    
    for text in random_texts:
        fv = text_to_feature_vector(text)
        metrics = compute_summary_metrics(fv)
        random_metrics.append(metrics)
    
    quran_df = pd.DataFrame(quran_metrics)
    random_df = pd.DataFrame(random_metrics)
    
    # İstatistiksel test
    result = compare_groups(quran_df, random_df, "f2_ortalama")
    
    print(f"\n  F2 Ortalaması Karşılaştırması:")
    print(f"    Kur'an: {result['quran_mean']:.2f} ± {result['quran_std']:.2f}")
    print(f"    Rastgele: {result['control_mean']:.2f} ± {result['control_std']:.2f}")
    print(f"    p-value: {result['p_value']:.6f}")
    print(f"    Cohen's d: {result['cohen_d']:.3f}")
    
    if result['anlamli_mi']:
        print("  ✅ BEKLENEN: İstatistiksel olarak anlamlı FARK var (p<0.05)")
        return True
    else:
        print("  ❌ PROBLEM: Fark anlamsız! Sistem hatalı olabilir.")
        return False


if __name__ == "__main__":
    print("="*60)
    print("NEGATİF KONTROL DOĞRULAMA")
    print("="*60)
    
    result = test_negative_control()
    
    print("\n" + "="*60)
    if result:
        print("✅ NEGATİF KONTROL TESTİ BAŞARILI")
    else:
        print("❌ NEGATİF KONTROL TESTİ BAŞARISIZ")
    print("="*60)
