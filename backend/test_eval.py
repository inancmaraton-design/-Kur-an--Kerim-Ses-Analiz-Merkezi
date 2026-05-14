"""
GuardianAAM — Eval & Regression Test Suite
Çalıştır: python test_eval.py [optional: audio.mp3]
Bağımlılık: requirements.txt (scikit-learn dahil)
"""

import json
import sys
import time
import traceback
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

import numpy as np

# backend/ dizini path'e ekle
sys.path.insert(0, str(Path(__file__).parent))

from audio_analyzer import (
    _normalize,
    extract_mfcc,
    extract_pitch,
    load_audio,
    to_3d_umap,
)

# ─── EŞİK DEĞERLERİ ──────────────────────────────────────────────────────────

THRESHOLDS: Dict[str, float] = {
    "rca_min":             92.0,   # % — Raw Chroma Accuracy
    "voicing_recall_min":  85.0,   # % — sesli frame geri çağırma
    "vfa_max":             15.0,   # % — yanlış pozitif seslilik oranı
    "umap_trust_min":       0.90,  # trustworthiness skoru
    "avg_f0_min":          65.0,   # Hz
    "avg_f0_max":         400.0,   # Hz
    "mfcc_time_ms_max":   500.0,   # ms
    "umap_time_ms_max":  3000.0,   # ms
    "n_voiced_min_ratio":   0.40,  # sesli frame oranı (toplam frame'e göre)
}

# ─── RENK SABITLERI ───────────────────────────────────────────────────────────

_G = "\033[92m"   # yeşil
_R = "\033[91m"   # kırmızı
_Y = "\033[93m"   # sarı
_B = "\033[94m"   # mavi
_W = "\033[97m"   # beyaz
_0 = "\033[0m"    # sıfırla

PASS_BADGE = f"{_G}[PASS]{_0}"
FAIL_BADGE = f"{_R}[FAIL]{_0}"
INFO_BADGE = f"{_B}[INFO]{_0}"
WARN_BADGE = f"{_Y}[WARN]{_0}"


# ─── PRINT YARDIMCİLARI ───────────────────────────────────────────────────────

def _hr(char: str = "─", n: int = 60) -> None:
    print(char * n)

def print_result(name: str, passed: bool, details: Dict[str, Any]) -> None:
    badge = PASS_BADGE if passed else FAIL_BADGE
    print(f"\n{badge}  {_W}{name}{_0}")
    for key, val in details.items():
        if isinstance(val, float):
            val_str = f"{val:.4f}"
        elif isinstance(val, list):
            val_str = str(val)
        else:
            val_str = str(val)
        marker = " "
        # Eşik geçmişse yeşil, geçmemişse kırmızı vurgula
        if key == "RCA":
            marker = _G + "✓" + _0 if val >= THRESHOLDS["rca_min"] else _R + "✗" + _0
        elif key == "VoicingRecall":
            marker = _G + "✓" + _0 if val >= THRESHOLDS["voicing_recall_min"] else _R + "✗" + _0
        elif key == "VoicingFalseAlarm":
            marker = _G + "✓" + _0 if val <= THRESHOLDS["vfa_max"] else _R + "✗" + _0
        elif key == "trustworthiness":
            marker = _G + "✓" + _0 if val >= THRESHOLDS["umap_trust_min"] else _R + "✗" + _0
        print(f"    {marker}  {key:<28} {val_str}")


# ─── TEST SİNYALİ ────────────────────────────────────────────────────────────

def generate_test_signal(
    freq_hz: float = 220.0,
    duration_sec: float = 5.0,
    sr: int = 22050,
    pitch_drift_hz: float = 10.0,
) -> Tuple[np.ndarray, int, np.ndarray]:
    """
    Sentetik sesli sinyal + zemin gerçekliği F0.

    - İlk (duration - 0.5) saniye: voiced, frekans [freq_hz, freq_hz+pitch_drift_hz]
    - Son 0.5 saniye: sessizlik (F0 = NaN)
    - Harmonik yapı: fundamental + 3 harmonik (vokal benzeri)

    Returns:
        y         : float32 ses sinyali
        sr        : örnekleme hızı
        gt_f0     : frame başına zemin gerçekliği F0 (sessiz → NaN)
    """
    n_voiced  = int(sr * (duration_sec - 0.5))
    n_silence = int(sr * 0.5)
    t         = np.linspace(0, n_voiced / sr, n_voiced)

    # Doğrusal frekans kayması (pyin için daha gerçekçi sinyal)
    freq_curve = freq_hz + pitch_drift_hz * (t / t[-1])
    phase      = 2.0 * np.pi * np.cumsum(freq_curve) / sr

    y_voiced = (
        np.sin(phase) * 0.50 +
        np.sin(2 * phase) * 0.30 +
        np.sin(3 * phase) * 0.15 +
        np.sin(4 * phase) * 0.05
    ).astype(np.float32)

    y = np.concatenate([y_voiced, np.zeros(n_silence, dtype=np.float32)])

    # Zemin gerçekliği: frame başına F0
    hop      = 512
    n_frames = 1 + (len(y) - 1) // hop
    gt_f0    = np.full(n_frames, np.nan, dtype=float)
    # Voiced bölge için frekansı kademeli olarak ata
    n_voiced_frames = n_voiced // hop
    gt_f0[:n_voiced_frames] = np.linspace(freq_hz, freq_hz + pitch_drift_hz, n_voiced_frames)

    return y, sr, gt_f0


# ─── EVAL FONKSİYONLARI ──────────────────────────────────────────────────────

def evaluate_pitch_accuracy(
    predicted_f0: np.ndarray,
    ground_truth_f0: np.ndarray,
    tolerance_cents: float = 50.0,
) -> Dict[str, Any]:
    """
    Pitch doğruluğunu hesapla.

    RCA  : Tolerance içindeki sesli frame oranı (Raw Chroma Accuracy)
    VR   : Gerçek sesli frame'lerin ne kadarı tespit edildi
    VFA  : Gerçek sessiz frame'lerin ne kadarı yanlışlıkla sesli sayıldı
    """
    n    = min(len(predicted_f0), len(ground_truth_f0))
    pred = predicted_f0[:n]
    gt   = ground_truth_f0[:n]

    # Her iki sinyal de sesli olan frame'ler
    valid = ~(np.isnan(pred) | np.isnan(gt)) & (gt > 0) & (pred > 0)
    if valid.sum() == 0:
        return {"error": "Geçerli sesli frame bulunamadı", "RCA": 0.0,
                "VoicingRecall": 0.0, "VoicingFalseAlarm": 100.0}

    cents_diff = np.abs(1200.0 * np.log2(pred[valid] / gt[valid]))
    rca = float(np.mean(cents_diff < tolerance_cents) * 100)

    pred_voiced  = ~np.isnan(pred)
    true_voiced  = ~np.isnan(gt)
    true_silence = ~true_voiced

    vr  = (float(np.mean(pred_voiced[true_voiced])  * 100)
           if true_voiced.any()  else 0.0)
    vfa = (float(np.mean(pred_voiced[true_silence]) * 100)
           if true_silence.any() else 0.0)

    return {
        "RCA":               rca,
        "VoicingRecall":     vr,
        "VoicingFalseAlarm": vfa,
        "MeanCentError":     float(np.mean(cents_diff)),
        "ValidFrames":       int(valid.sum()),
        "TotalFrames":       int(n),
    }


def evaluate_umap_quality(
    original: np.ndarray,
    embedded: np.ndarray,
    k: int = 10,
) -> Dict[str, Any]:
    """
    UMAP gömme kalitesini trustworthiness metriğiyle ölç.

    Trustworthiness ∈ [0, 1]: k en-yakın komşunun korunma oranı.
    Hedef: > 0.9
    """
    from sklearn.manifold import trustworthiness

    n     = min(len(original), len(embedded))
    k_use = min(k, n - 2)
    if k_use < 1:
        return {"trustworthiness": 0.0, "quality": "Yetersiz veri"}

    score = float(trustworthiness(
        np.nan_to_num(original[:n], nan=0.0),
        embedded[:n],
        n_neighbors=k_use
    ))
    return {
        "trustworthiness": score,
        "n_neighbors":     k_use,
        "n_samples":       n,
        "quality":         "İyi" if score >= THRESHOLDS["umap_trust_min"] else "Zayıf",
    }


def run_full_regression(audio_path: Optional[str] = None) -> Dict[str, Any]:
    """
    Uçtan uca pipeline regression testi.

    Gerçek dosya verilmezse sentetik sinyal kullanılır.
    Ölçülen metrikler: pitch, MFCC, UMAP süreleri + F0 istatistikleri.
    """
    results: Dict[str, Any] = {}

    # 1. Yükle / Üret
    t0 = time.perf_counter()
    if audio_path and Path(audio_path).exists():
        y, sr = load_audio(audio_path)
        results["source"] = str(Path(audio_path).name)
    else:
        y, sr, _ = generate_test_signal(220.0, duration_sec=5.0)
        results["source"] = "synthetic_220hz"
    results["load_time_ms"] = round((time.perf_counter() - t0) * 1000, 2)
    results["n_samples"]    = len(y)
    results["duration_sec"] = round(len(y) / sr, 3)

    # 2. Pitch (pyin)
    t0 = time.perf_counter()
    f0, voiced = extract_pitch(y, sr)
    results["pitch_time_ms"]    = round((time.perf_counter() - t0) * 1000, 2)
    results["n_frames"]         = len(f0)
    results["n_voiced_frames"]  = int(voiced.sum())
    results["voiced_ratio"]     = round(float(voiced.mean()), 4)
    results["avg_f0_hz"]        = round(float(np.nanmean(f0)) if not np.all(np.isnan(f0)) else 0.0, 2)
    results["f0_std_hz"]        = round(float(np.nanstd(f0)),  2)

    # 3. MFCC (39 boyut)
    t0 = time.perf_counter()
    mfcc = extract_mfcc(y, sr)
    results["mfcc_time_ms"]  = round((time.perf_counter() - t0) * 1000, 2)
    results["mfcc_shape"]    = list(mfcc.shape)
    results["mfcc_variance"] = round(float(np.var(mfcc)), 4)

    # 4. UMAP (3B)
    n_frames  = mfcc.shape[1]
    f0_padded = np.nan_to_num(f0[:n_frames], nan=0.0)
    feat_mat  = np.column_stack([f0_padded, mfcc.T])   # (n_frames, 40)

    t0 = time.perf_counter()
    coords = to_3d_umap(feat_mat)
    results["umap_time_ms"] = round((time.perf_counter() - t0) * 1000, 2)
    results["umap_shape"]   = list(coords.shape)

    # 5. Eşik kontrolleri
    checks = {
        "avg_f0_in_range": (THRESHOLDS["avg_f0_min"] <=
                            results["avg_f0_hz"] <=
                            THRESHOLDS["avg_f0_max"]),
        "voiced_ratio_ok": results["voiced_ratio"] >= THRESHOLDS["n_voiced_min_ratio"],
        "mfcc_time_ok":    results["mfcc_time_ms"]  < THRESHOLDS["mfcc_time_ms_max"],
        "umap_time_ok":    results["umap_time_ms"]  < THRESHOLDS["umap_time_ms_max"],
        "mfcc_shape_ok":   results["mfcc_shape"][0] == 39,
    }
    results["checks"] = checks
    results["status"] = "PASS" if all(checks.values()) else "FAIL"
    return results


# ─── ANA TEST AKIŞI ───────────────────────────────────────────────────────────

def main() -> None:
    audio_path  = sys.argv[1] if len(sys.argv) > 1 else None
    all_results: Dict[str, Any] = {}
    passed_list: Dict[str, bool] = {}

    print(f"\n{'═' * 60}")
    print(f"  {_W}GuardianAAM — Eval & Regression Suite{_0}")
    print(f"  Tarih : {time.strftime('%Y-%m-%d %H:%M:%S')}")
    if audio_path:
        print(f"  Dosya : {audio_path}")
    else:
        print(f"  {WARN_BADGE}  Ses dosyası belirtilmedi → sentetik sinyal kullanılacak")
    print(f"{'═' * 60}")

    # ── Test 1: Pitch Accuracy ────────────────────────────────────────────
    print(f"\n{INFO_BADGE}  Test 1: Pitch Accuracy  (pyin @ 220–230 Hz)")
    _hr()
    try:
        y_t, sr_t, gt_f0 = generate_test_signal(220.0)
        pred_f0, _        = extract_pitch(y_t, sr_t)
        pitch_res         = evaluate_pitch_accuracy(pred_f0, gt_f0)
        t1_pass = (
            pitch_res.get("RCA", 0)           >= THRESHOLDS["rca_min"] and
            pitch_res.get("VoicingRecall", 0) >= THRESHOLDS["voicing_recall_min"] and
            pitch_res.get("VoicingFalseAlarm", 100) <= THRESHOLDS["vfa_max"]
        )
    except Exception as exc:
        pitch_res = {"error": str(exc)}
        t1_pass   = False
        traceback.print_exc()

    print_result("Pitch Accuracy", t1_pass, pitch_res)
    all_results["pitch_accuracy"] = pitch_res
    passed_list["pitch_accuracy"] = t1_pass

    # ── Test 2: UMAP Quality ──────────────────────────────────────────────
    print(f"\n{INFO_BADGE}  Test 2: UMAP Trustworthiness  (k=10, hedef ≥ 0.90)")
    _hr()
    try:
        y_u, sr_u, _ = generate_test_signal(220.0, duration_sec=4.0)
        f0_u, _      = extract_pitch(y_u, sr_u)
        mfcc_u       = extract_mfcc(y_u, sr_u)
        nu           = mfcc_u.shape[1]
        feat_u       = np.column_stack([
            np.nan_to_num(f0_u[:nu], nan=0.0), mfcc_u.T
        ])
        coords_u = to_3d_umap(feat_u)
        umap_res = evaluate_umap_quality(feat_u, coords_u)
        t2_pass  = umap_res.get("trustworthiness", 0) >= THRESHOLDS["umap_trust_min"]
    except Exception as exc:
        umap_res = {"error": str(exc)}
        t2_pass  = False
        traceback.print_exc()

    print_result("UMAP Quality", t2_pass, umap_res)
    all_results["umap_quality"] = umap_res
    passed_list["umap_quality"] = t2_pass

    # ── Test 3: Full Regression ───────────────────────────────────────────
    print(f"\n{INFO_BADGE}  Test 3: Full Pipeline Regression")
    _hr()
    try:
        reg_res = run_full_regression(audio_path)
        t3_pass = reg_res.get("status") == "PASS"
        display = {k: v for k, v in reg_res.items() if k != "checks"}
        print_result("Full Regression", t3_pass, display)
        if not t3_pass:
            print(f"\n  {WARN_BADGE}  Başarısız kontroller:")
            for check, ok in reg_res.get("checks", {}).items():
                if not ok:
                    print(f"         {_R}✗{_0}  {check}")
    except Exception as exc:
        reg_res = {"error": str(exc), "status": "FAIL"}
        t3_pass = False
        traceback.print_exc()

    all_results["full_regression"] = reg_res
    passed_list["full_regression"] = t3_pass

    # ── Rapor ─────────────────────────────────────────────────────────────
    all_passed = all(passed_list.values())

    report = {
        "timestamp":   time.strftime("%Y-%m-%dT%H:%M:%S"),
        "all_passed":  all_passed,
        "summary":     passed_list,
        "thresholds":  THRESHOLDS,
        "results":     all_results,
    }
    report_path = Path(__file__).parent / "eval_report.json"
    with open(report_path, "w", encoding="utf-8") as fh:
        json.dump(report, fh, indent=2, ensure_ascii=False, default=str)

    # ── Son özet ──────────────────────────────────────────────────────────
    print(f"\n{'═' * 60}")
    print(f"  {'Test Özeti':}")
    _hr()
    for name, passed in passed_list.items():
        badge = PASS_BADGE if passed else FAIL_BADGE
        print(f"  {badge}  {name}")
    _hr()
    print(f"  Rapor: {report_path}")
    print(f"{'═' * 60}")

    if all_passed:
        print(f"\n{_G}  ✓ Tüm testler geçti.")
        print(f"  GuardianAAM hazır.{_0}\n")
        sys.exit(0)
    else:
        print(f"\n{_R}  ✗ Bazı testler başarısız.{_0}\n")
        sys.exit(1)


if __name__ == "__main__":
    main()
