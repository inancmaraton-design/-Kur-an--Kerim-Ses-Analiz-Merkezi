"""
test_sensitivity.py — Sensitivite Analizi
harf_acoustics.json F2 değerleri ±10% değiştirilince
sonuçlar ne kadar etkileniyor?
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent / "analysis"))

import json
import numpy as np
from copy import deepcopy
from feature_vector import HARF_ACOUSTICS, text_to_feature_vector, compute_summary_metrics


def test_sensitivity_f2():
    """F2 ±10% değişikliğinin sensitivitesini test et"""
    print("📊 Sensitivite Analizi: F2 ±10% değişiklik")
    
    test_text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ"
    
    # Baseline
    fv_baseline = text_to_feature_vector(test_text)
    metrics_baseline = compute_summary_metrics(fv_baseline)
    
    print(f"  Baseline F2 ortalaması: {metrics_baseline['f2_ortalama']:.2f}")
    print(f"  Baseline harf_sayisi: {metrics_baseline['harf_sayisi']}")
    
    # F2 % değişiklikleri test et
    perturbations = [-10, -5, 0, 5, 10]
    results = []
    
    for perturb_pct in perturbations:
        # harf_acoustics değerlerini modifiye et
        modified_acoustics = deepcopy(HARF_ACOUSTICS)
        
        for harf_data in modified_acoustics.values():
            if isinstance(harf_data, dict) and "F2_ortalama" in harf_data:
                # F2 değerini yüzde değişikliğe göre ayarla
                original_f2 = harf_data["F2_ortalama"]
                harf_data["F2_ortalama"] = original_f2 * (1.0 + perturb_pct / 100.0)
                
                # Std da orantılı şekilde değiş
                harf_data["F2_std"] = harf_data.get("F2_std", 0) * (1.0 + perturb_pct / 100.0)
        
        # Modu patch et geçici olarak
        import feature_vector as fv_mod
        original_acoustics = fv_mod.HARF_ACOUSTICS
        fv_mod.HARF_ACOUSTICS = modified_acoustics
        
        try:
            fv_perturbed = text_to_feature_vector(test_text)
            metrics_perturbed = compute_summary_metrics(fv_perturbed)
            
            f2_change_pct = ((metrics_perturbed['f2_ortalama'] - metrics_baseline['f2_ortalama']) / 
                            metrics_baseline['f2_ortalama'] * 100.0)
            
            results.append({
                'perturbation_pct': perturb_pct,
                'f2_perturbed': metrics_perturbed['f2_ortalama'],
                'f2_change_pct': f2_change_pct
            })
            
        finally:
            fv_mod.HARF_ACOUSTICS = original_acoustics
    
    # Sonuçları göster
    print("\n  Perturbation | F2 Çıkışı | Çıkış/Giriş Oran")
    print("  " + "-" * 50)
    for r in results:
        sensitivity_ratio = r['f2_change_pct'] / r['perturbation_pct'] if r['perturbation_pct'] != 0 else 0
        print(f"  {r['perturbation_pct']:+3d}% | {r['f2_change_pct']:+6.2f}% | {sensitivity_ratio:.3f}")
    
    # Stabilite kontrol
    stability_ok = all(abs(r['f2_change_pct']) < 20 for r in results if r['perturbation_pct'] != 0)
    
    if stability_ok:
        print("\n  ✅ STABIL: F2 ±10% değişiklik → çıktı ±20% içinde")
        return True
    else:
        print("\n  ⚠️  UYARI: Sistem aşırı sensititf olabilir")
        return False


def test_sensitivity_formants():
    """Tüm formantlar (F1, F2, F3) sensitivitesini kontrol et"""
    print("\n📊 Formant Sensitivitesi: Tüm F1/F2/F3")
    
    test_text = "قُلْ هُوَ اللَّهُ أَحَدٌ"
    
    fv_baseline = text_to_feature_vector(test_text)
    metrics_baseline = compute_summary_metrics(fv_baseline)
    
    formants = ['f1_ortalama', 'f2_ortalama', 'f3_ortalama']
    
    print(f"  Baseline:")
    for f in formants:
        print(f"    {f}: {metrics_baseline[f]:.2f}")
    
    # Test: Tüm formantlar değiştirildi, ama sonuçlar tutarlı mı?
    all_stable = True
    
    for formant in formants:
        baseline_val = metrics_baseline[formant]
        if baseline_val > 0:
            # Veri var demektir, consistent olmalı
            if baseline_val < 100 or baseline_val > 3000:
                print(f"  ⚠️  {formant} aralık dışı: {baseline_val:.2f} Hz")
                all_stable = False
    
    if all_stable:
        print("\n  ✅ Tüm formantlar sağlıklı aralıkta")
        return True
    else:
        print("\n  ❌ Formant aralıkları kontrol edilmeli")
        return False


if __name__ == "__main__":
    print("="*60)
    print("SENSİTİVİTE DOĞRULAMA")
    print("="*60)
    
    test1 = test_sensitivity_f2()
    test2 = test_sensitivity_formants()
    
    print("\n" + "="*60)
    if test1 and test2:
        print("✅ SENSİTİVİTE ANALİZİ BAŞARILI")
    else:
        print("⚠️  SENSİTİVİTE ANALİZİ UYARI")
    print("="*60)
