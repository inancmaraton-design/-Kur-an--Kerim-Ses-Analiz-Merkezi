"""
feature_vector.py — Metin → Sayısal Öznitelik Vektörü
Bu projenin KALBİ. Ses dosyası kullanmaz, sadece matematik.
"""
import json
from dataclasses import dataclass
from pathlib import Path
from typing import List, Dict, Optional
import numpy as np
from scipy.stats import entropy as scipy_entropy

from analysis.text_parser import tokenize_arabic, HarfToken
from analysis.tajweed_detector import detect_all_tajweed, KuralEslesme


# HARF_AKUSTIK veritabanını data modülünden yükle
from data.harf_acoustics import HARF_ACOUSTICS


@dataclass
class FeatureVector:
    """Metin için çıkarılan akustik öznitelik vektörü"""
    f1_array: np.ndarray         # Her harf için F1 (Hz)
    f2_array: np.ndarray         # Her harf için F2 (Hz)
    f3_array: np.ndarray         # Her harf için F3 (Hz)
    sure_array: np.ndarray       # Her harf için ses süresi (ms)
    spektral_merkez_array: np.ndarray  # Her harf için spektral merkez (Hz)
    nazal_array: np.ndarray      # Her harf için nazal rezonans (0-1)
    tefhim_array: np.ndarray     # Her harf için tefhim (0-1)
    text: str                    # Orijinal metin
    
    def to_dict(self) -> dict:
        """Dict'e dönüştür (JSON serializable)"""
        return {
            "f1_array": self.f1_array.tolist(),
            "f2_array": self.f2_array.tolist(),
            "f3_array": self.f3_array.tolist(),
            "sure_array": self.sure_array.tolist(),
            "spektral_merkez_array": self.spektral_merkez_array.tolist(),
            "nazal_array": self.nazal_array.tolist(),
            "tefhim_array": self.tefhim_array.tolist(),
            "text": self.text
        }


def _has_rule(token_pos: int, rules: List[KuralEslesme], rule_name: str) -> bool:
    """Token'a belirli bir tecvid kuralı uygulanıyor mu?"""
    for rule in rules:
        if rule.baslama_pozisyonu <= token_pos <= rule.bitis_pozisyonu:
            if rule.kural_adi == rule_name:
                return True
    return False


def text_to_feature_vector(text: str) -> FeatureVector:
    """
    Arapça metin → Sayısal öznitelik vektörü
    
    Her harf için:
    - F1, F2, F3 (formantlar) — ses spektrumu
    - Süre (ms) — timing
    - Spektral merkez — parlaklık
    - Nazal rezonans — nazal oran
    - Tefhim — ses derinleştirilme
    
    Tecvid kuralları uygulanır (med, gunna, tefhim vb.)
    """
    tokens = tokenize_arabic(text)
    rules = detect_all_tajweed(tokens)
    
    # Çıktı dizileri
    f1_list = []
    f2_list = []
    f3_list = []
    sure_list = []
    spektral_merkez_list = []
    nazal_list = []
    tefhim_list = []
    
    for i, token in enumerate(tokens):
        harf = token.temel_harf
        
        # Harf veritabanından temel değerleri al
        if harf not in HARF_ACOUSTICS:
            # Bilinmeyen harf → atla
            continue
        
        harf_data = HARF_ACOUSTICS[harf]
        
        # Temel akustik değerler
        f1 = float(harf_data["F1_ortalama"])
        f2 = float(harf_data["F2_ortalama"])
        f3 = float(harf_data["F3_ortalama"])
        sure = float(harf_data["sure_ms_tipik"])
        spektral_merkez = float(harf_data["spektral_merkez"])
        nazal = float(harf_data["nazal_rezonans"])
        tefhim = 1.0 if harf_data.get("tefhim", False) else 0.0
        
        # ─────────────────────────────────────────────────────────
        # TECVID KURALLARINI UYGULA
        # ─────────────────────────────────────────────────────────
        
        # 1. Med (مد): Sesin uzatılması
        if _has_rule(i, rules, "med_tabii"):
            sure *= 2.0  # 2 zaman
        elif _has_rule(i, rules, "med_wacib"):
            sure *= 4.0  # 4 zaman
        elif _has_rule(i, rules, "med_lazim"):
            sure *= 6.0  # 6 zaman
        
        # 2. Gunna (غنة): Nazallikleştirilmiş ses
        if _has_rule(i, rules, "gunna"):
            nazal = 0.6
            # Nasallar çok yüksek nazal rezonansa sahip
            if harf in {'م', 'ن'}:
                nazal = 0.95
        
        # 3. Şedde (ّ): Ses iki katı kadar
        if token.sedde:
            sure *= 1.5
        
        # 4. Tefhim (تفخيم): F2 düşür (back vowel orientation)
        if _has_rule(i, rules, "tefhim"):
            f2 *= 0.7  # Back vowel
            tefhim = 1.0
        
        # 5. Qalqala (قلقلة): Titreme (F2 varyasyonu artır)
        if _has_rule(i, rules, "qalqala"):
            sure *= 1.3
            # Stops'da daha fazla varyasyon
            spektral_merkez *= 1.2
        
        # ─────────────────────────────────────────────────────────
        
        f1_list.append(f1)
        f2_list.append(f2)
        f3_list.append(f3)
        sure_list.append(sure)
        spektral_merkez_list.append(spektral_merkez)
        nazal_list.append(nazal)
        tefhim_list.append(tefhim)
    
    # numpy array'lere dönüştür
    return FeatureVector(
        f1_array=np.array(f1_list, dtype=np.float64),
        f2_array=np.array(f2_list, dtype=np.float64),
        f3_array=np.array(f3_list, dtype=np.float64),
        sure_array=np.array(sure_list, dtype=np.float64),
        spektral_merkez_array=np.array(spektral_merkez_list, dtype=np.float64),
        nazal_array=np.array(nazal_list, dtype=np.float64),
        tefhim_array=np.array(tefhim_list, dtype=np.float64),
        text=text
    )


# ─────────────────────────────────────────────────────────
# ÖZET METRİKLER
# ─────────────────────────────────────────────────────────

def shannon_entropy(arr: np.ndarray) -> float:
    """Shannon Entropy — belirsizlik ölçüsü"""
    if len(arr) == 0:
        return 0.0
    
    # Array'i normalize et (0-1)
    arr_norm = (arr - np.min(arr)) / (np.max(arr) - np.min(arr) + 1e-10)
    
    # Histogramı hesapla (10 bin)
    hist, _ = np.histogram(arr_norm, bins=10, range=(0, 1))
    hist = hist[hist > 0]  # Sıfır olmayan bölmeleri al
    
    # Entropy hesapla
    return scipy_entropy(hist)


def npvi(arr: np.ndarray) -> float:
    """
    Normalized Pairwise Variability Index
    Ardışık değerlerin varyasyonu (ritmik düzgünlük)
    """
    if len(arr) < 2:
        return 0.0
    
    diffs = np.abs(np.diff(arr))
    mean_val = np.mean(arr)
    
    if mean_val == 0:
        return 0.0
    
    # NPVI = (2 * sum(|Xi - Xi+1|)) / (sum(Xi + Xi+1)) * 100
    numerator = 2 * np.sum(diffs)
    denominator = np.sum(arr[:-1] + arr[1:])
    
    if denominator == 0:
        return 0.0
    
    return (numerator / denominator) * 100.0


def compute_summary_metrics(fv: FeatureVector) -> Dict:
    """
    FeatureVector'den özet metrikler hesapla
    """
    return {
        # ─── Frekans Dağılımı ───
        "f1_ortalama": float(np.mean(fv.f1_array)),
        "f1_std": float(np.std(fv.f1_array)),
        "f1_range": float(np.ptp(fv.f1_array)),
        
        "f2_ortalama": float(np.mean(fv.f2_array)),
        "f2_std": float(np.std(fv.f2_array)),
        "f2_range": float(np.ptp(fv.f2_array)),
        "f2_entropi": float(shannon_entropy(fv.f2_array)),
        
        "f3_ortalama": float(np.mean(fv.f3_array)),
        "f3_std": float(np.std(fv.f3_array)),
        
        # ─── Geçiş Yumuşaklığı (Süreklilik) ───
        "f1_gecis_yumusakligi": float(np.mean(np.abs(np.diff(fv.f1_array)))) if len(fv.f1_array) > 1 else 0.0,
        "f2_gecis_yumusakligi": float(np.mean(np.abs(np.diff(fv.f2_array)))) if len(fv.f2_array) > 1 else 0.0,
        "f3_gecis_yumusakligi": float(np.mean(np.abs(np.diff(fv.f3_array)))) if len(fv.f3_array) > 1 else 0.0,
        
        # ─── Spektral Denge ───
        "spektral_merkez_ortalama": float(np.mean(fv.spektral_merkez_array)),
        "spektral_merkez_std": float(np.std(fv.spektral_merkez_array)),
        
        # ─── Ritmik Düzgünlük ───
        "ritmik_duzgunluk_npvi": float(npvi(fv.sure_array)),
        "ortalama_harf_sure_ms": float(np.mean(fv.sure_array)),
        "toplam_sure_ms": float(np.sum(fv.sure_array)),
        
        # ─── Nazal ve Tefhim ───
        "nazal_oran": float(np.mean(fv.nazal_array > 0.3)),
        "nazal_ortalama": float(np.mean(fv.nazal_array)),
        "tefhim_orani": float(np.mean(fv.tefhim_array > 0.5)),
        "tefhim_ortalama": float(np.mean(fv.tefhim_array)),
        
        # ─── Genel ───
        "harf_sayisi": len(fv.f2_array),
    }


# Test
if __name__ == "__main__":
    # Fatiha 1. ayet
    text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
    
    print(f"Metin: {text}")
    print(f"Harf sayısı: {len([c for c in text if ord(c) > 1536])}")
    
    # Feature vector oluştur
    fv = text_to_feature_vector(text)
    print(f"\nFeature Vector oluşturuldu ({len(fv.f2_array)} harf):")
    print(f"  F1: {fv.f1_array.tolist()}")
    print(f"  F2: {fv.f2_array.tolist()}")
    print(f"  Sure (ms): {fv.sure_array.tolist()}")
    
    # Metrikler
    metrics = compute_summary_metrics(fv)
    print(f"\nÖzet Metrikler:")
    for key, val in sorted(metrics.items()):
        print(f"  {key}: {val:.2f}")
    
    # Determinizm testi
    print(f"\n⟶ Determinizm testi: aynı metin 3 kere analiz et")
    for run in range(1, 4):
        fv_test = text_to_feature_vector(text)
        if np.allclose(fv_test.f2_array, fv.f2_array, rtol=0):
            print(f"  Run {run}: ✅ Bit-bit aynı")
        else:
            print(f"  Run {run}: ❌ Farklı!")
