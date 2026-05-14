"""Import ve temel fonksiyon smoke testi."""
import sys
sys.path.insert(0, r'C:\Users\inanc\Desktop\Kuran_i_Kerim_Android\backend')

import librosa
import numpy as np
import scipy
import umap as umap_lib
import fastapi
import uvicorn
import loguru
import soundfile

from audio_analyzer import (
    load_audio, extract_pitch, extract_mfcc, extract_spectral,
    extract_formants, to_3d_umap, assign_color, segment_by_silence,
    _normalize, _trim_or_pad,
)

print(f"PASS librosa    {librosa.__version__}")
print(f"PASS numpy      {np.__version__}")
print(f"PASS scipy      {scipy.__version__}")
print(f"PASS umap       {umap_lib.__version__}")
print(f"PASS fastapi    {fastapi.__version__}")
print(f"PASS uvicorn    {uvicorn.__version__}")
print(f"PASS soundfile  {soundfile.__version__}")
print(f"PASS audio_analyzer — 8 fonksiyon import edildi")

# ── _normalize ──────────────────────────────────────────────────────────────
arr = np.array([0.0, 5.0, 10.0])
out = _normalize(arr)
assert out[0] == 0.0 and out[-1] == 1.0, f"normalize hata: {out}"
# NaN korunmalı
arr_nan = np.array([np.nan, 2.0, 4.0])
out_nan = _normalize(arr_nan)
assert np.isnan(out_nan[0]), "NaN korunmadı"
print("PASS _normalize")

# ── _trim_or_pad ─────────────────────────────────────────────────────────────
trimmed = _trim_or_pad(np.arange(10), 5)
assert len(trimmed) == 5
padded = _trim_or_pad(np.arange(3), 6)
assert len(padded) == 6 and np.isnan(padded[-1])
print("PASS _trim_or_pad")

# ── assign_color ─────────────────────────────────────────────────────────────
assert assign_color(float('nan'), 0.5) == "#4A4A4A"
assert assign_color(200.0, 0.8) == "#E85D24"
assert assign_color(200.0, 0.5) == "#EF9F27"
assert assign_color(200.0, 0.3) == "#3B8BD4"
assert assign_color(200.0, 0.1) == "#1D9E75"
print("PASS assign_color")

# ── extract_pitch (sentetik sinyal) ──────────────────────────────────────────
sr = 22050
t  = np.linspace(0, 1.0, sr, endpoint=False)
sine = (0.5 * np.sin(2 * np.pi * 220 * t)).astype(np.float32)
f0, voiced = extract_pitch(sine, sr, hop=512)
assert len(f0) > 0, "f0 boş"
assert len(f0) == len(voiced), "f0/voiced uzunluk uyumsuz"
n_voiced = int(voiced.sum())
print(f"PASS extract_pitch — {n_voiced}/{len(f0)} sesli frame, "
      f"ort F0={float(np.nanmean(f0)):.1f} Hz")

# ── extract_mfcc ─────────────────────────────────────────────────────────────
mfcc = extract_mfcc(sine, sr, n_fft=2048, hop=512, n_mfcc=13)
assert mfcc.shape[0] == 39, f"MFCC satır sayısı yanlış: {mfcc.shape[0]}"
print(f"PASS extract_mfcc — shape={mfcc.shape}")

# ── extract_spectral ─────────────────────────────────────────────────────────
spec = extract_spectral(sine, sr, n_fft=2048, hop=512)
for key in ("centroid", "flux", "zcr", "rolloff", "rms"):
    assert key in spec, f"{key} eksik"
print(f"PASS extract_spectral — {list(spec.keys())}")

# ── segment_by_silence ───────────────────────────────────────────────────────
# 0.5s ses + 0.5s sessizlik
signal = np.concatenate([
    0.3 * np.sin(2 * np.pi * 200 * np.linspace(0, 0.5, sr // 2)),
    np.zeros(sr // 2)
])
segs = segment_by_silence(signal, sr, threshold_db=-40)
assert len(segs) >= 1, f"Segment bulunamadı: {segs}"
print(f"PASS segment_by_silence — {len(segs)} segment bulundu")

# ── extract_formants (kısa sinyal) ───────────────────────────────────────────
short = np.random.randn(4096).astype(np.float32) * 0.01
f1_list, f2_list = extract_formants(short, sr, n_fft=2048, hop=512)
print(f"PASS extract_formants — {len(f1_list)} frame")

# ── to_3d_umap (küçük matris) ────────────────────────────────────────────────
X = np.random.randn(30, 20).astype(np.float32)
coords = to_3d_umap(X)
assert coords.shape == (30, 3), f"UMAP shape yanlış: {coords.shape}"
print(f"PASS to_3d_umap — shape={coords.shape}")

print("\n" + "="*50)
print("ALL TESTS PASSED -- backend ready")
print("="*50)
