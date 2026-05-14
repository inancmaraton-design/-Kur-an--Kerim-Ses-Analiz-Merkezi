"""
GuardianAAM — FastAPI Ses Analiz Servisi
POST /analyze/quran  →  metadata + features + umap_3d + segments
"""

import os
import tempfile
from typing import Optional

import librosa
import numpy as np
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from loguru import logger

from audio_analyzer import (
    _normalize,
    _trim_or_pad,
    assign_color,
    extract_formants,
    extract_mfcc,
    extract_pitch,
    extract_spectral,
    load_audio,
    segment_by_silence,
    to_3d_umap,
)

# ─── UYGULAMA ─────────────────────────────────────────────────────────────────

app = FastAPI(
    title="GuardianAAM API",
    version="1.0",
    description="Kuran-ı Kerim ses frekans analizi — 3D UMAP görselleştirme",
)

# ─── YARDIMCI ────────────────────────────────────────────────────────────────

_N_FFT = 2048
_HOP   = 512
_SR    = 22050


def _to_list(arr: np.ndarray) -> list:
    """Numpy array → JSON-uyumlu liste; NaN → None."""
    return [
        None if np.isnan(v) else round(float(v), 6)
        for v in arr
    ]


def _get_label(t: float, segments: list) -> str:
    """Zaman damgasına göre ayet etiketini döndür."""
    for idx, seg in enumerate(segments):
        if seg["start"] <= t < seg["end"]:
            return f"Ayet {idx + 1}"
    return "silence"


# ─── ÇEKIRDEK PIPELINE ───────────────────────────────────────────────────────

def build_response_json(y: np.ndarray, sr: int, surah_name: str) -> dict:
    """
    Tüm öznitelik çıkarma adımlarını sırayla çalıştırır ve
    API şemasına uygun JSON dict döndürür.

    Pipeline:
        1. pyin  → F0 eğrisi
        2. STFT  → Spektrogram
        3. MFCC  → 13 + Δ + ΔΔ = 39 öznitelik
        4. LPC   → Formantlar (F1, F2)
        5. Spektral öznitelikler
        6. Normalize (0-1)
        7. UMAP  → 3D koordinatlar
        8. Renk ata
        9. Segment tespiti
    """
    logger.info(f"[{surah_name}] Pipeline başlatıldı")

    # 1. Pitch (pyin)
    f0_raw, voiced_flag = extract_pitch(y, sr, _HOP)

    # 2. MFCC×39
    mfcc_mat = extract_mfcc(y, sr, _N_FFT, _HOP)          # (39, n_frames)
    n_frames = mfcc_mat.shape[1]
    duration_sec = float(librosa.get_duration(y=y, sr=sr))

    # 3. Spektral öznitelikler
    spectral = extract_spectral(y, sr, _N_FFT, _HOP)

    # 4. Formantlar
    f1_raw, f2_raw = extract_formants(y, sr, _N_FFT, _HOP)

    # 5. Tüm dizileri n_frames uzunluğuna hizala
    f0          = _trim_or_pad(f0_raw, n_frames)
    voiced      = _trim_or_pad(voiced_flag.astype(float), n_frames)
    centroid    = _trim_or_pad(spectral["centroid"], n_frames)
    flux        = _trim_or_pad(spectral["flux"],     n_frames)
    zcr         = _trim_or_pad(spectral["zcr"],      n_frames)
    rolloff     = _trim_or_pad(spectral["rolloff"],  n_frames)
    rms         = _trim_or_pad(spectral["rms"],      n_frames)
    f1          = _trim_or_pad(f1_raw, n_frames)
    f2          = _trim_or_pad(f2_raw, n_frames)

    # 6. Normalize (0-1)
    f0_norm       = _normalize(f0)
    rms_norm      = _normalize(rms)
    centroid_norm = _normalize(centroid)
    flux_norm     = _normalize(flux)
    zcr_norm      = _normalize(zcr)
    rolloff_norm  = _normalize(rolloff)
    f1_norm       = _normalize(f1)
    f2_norm       = _normalize(f2)

    # 7. UMAP öznitelik matrisi: MFCC.T (n_frames,39) + 7 spektral = 46 boyut
    mfcc_T = mfcc_mat.T  # (n_frames, 39)
    extra = np.column_stack([
        np.nan_to_num(f0_norm,       nan=0.0),
        np.nan_to_num(rms_norm,      nan=0.0),
        np.nan_to_num(centroid_norm, nan=0.0),
        np.nan_to_num(flux_norm,     nan=0.0),
        np.nan_to_num(zcr_norm,      nan=0.0),
        np.nan_to_num(f1_norm,       nan=0.0),
        np.nan_to_num(f2_norm,       nan=0.0),
    ])  # (n_frames, 7)
    features_matrix = np.hstack([mfcc_T, extra])  # (n_frames, 46)

    logger.info(f"UMAP girişi: {features_matrix.shape}")
    coords_3d = to_3d_umap(features_matrix)       # (n_frames, 3)

    # 8. Segment tespiti
    segments = segment_by_silence(y, sr)

    # 9. umap_3d listesi
    umap_3d = []
    for i in range(n_frames):
        t_sec = float(librosa.frames_to_time(i, sr=sr, hop_length=_HOP))
        f0_val = float(f0[i]) if not np.isnan(f0[i]) else None
        energy = float(rms_norm[i]) if not np.isnan(rms_norm[i]) else 0.0
        color  = assign_color(f0_val if f0_val is not None else float('nan'), energy)

        umap_3d.append({
            "x":        round(float(coords_3d[i, 0]), 6),
            "y":        round(float(coords_3d[i, 1]), 6),
            "z":        round(float(coords_3d[i, 2]), 6),
            "frame":    i,
            "time_sec": round(t_sec, 4),
            "f0":       round(f0_val, 2) if f0_val is not None else None,
            "color":    color,
            "label":    _get_label(t_sec, segments),
        })

    # 10. Segment çıktısı
    seg_out = []
    for idx, seg in enumerate(segments):
        seg_out.append({
            "label":       f"Ayet {idx + 1}",
            "start":       round(seg["start"], 4),
            "end":         round(seg["end"], 4),
            "frame_start": int(librosa.time_to_frames(
                seg["start"], sr=sr, hop_length=_HOP
            )),
            "frame_end":   int(librosa.time_to_frames(
                seg["end"], sr=sr, hop_length=_HOP
            )),
        })

    logger.success(f"[{surah_name}] Analiz tamamlandı: {n_frames} frame, "
                   f"{len(seg_out)} segment")

    return {
        "metadata": {
            "surah":            surah_name,
            "duration_sec":     round(duration_sec, 3),
            "sample_rate":      sr,
            "n_frames":         n_frames,
            "n_fft":            _N_FFT,
            "hop_length":       _HOP,
            "analysis_version": "1.0",
        },
        "features": {
            "f0":                 _to_list(f0),
            "voiced":             [bool(v) for v in voiced.astype(bool)],
            "mfcc":               mfcc_mat.tolist(),
            "spectral_centroid":  _to_list(centroid),
            "spectral_flux":      _to_list(flux),
            "zero_crossing_rate": _to_list(zcr),
            "formant_f1":         _to_list(f1),
            "formant_f2":         _to_list(f2),
            "rms_energy":         _to_list(rms),
        },
        "umap_3d":  umap_3d,
        "segments": seg_out,
    }


# ─── ENDPOINT ────────────────────────────────────────────────────────────────

@app.post("/analyze/quran")
async def analyze_quran(
    file: UploadFile = File(..., description="WAV / MP3 ses dosyası"),
    surah_name: Optional[str] = Form(default="Bilinmiyor"),
) -> dict:
    """
    Ses dosyasını analiz et, 3D UMAP koordinatlarıyla birlikte
    tam öznitelik JSON'u döndür.
    """
    suffix = os.path.splitext(file.filename or ".wav")[1] or ".wav"
    tmp_path: Optional[str] = None

    try:
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = tmp.name

        logger.info(f"Gelen dosya: {file.filename} ({len(content)} byte)")
        y, sr = load_audio(tmp_path)
        return build_response_json(y, sr, surah_name or "Bilinmiyor")

    except Exception as exc:
        logger.error(f"Analiz hatası: {exc}")
        raise HTTPException(status_code=500, detail=str(exc)) from exc

    finally:
        if tmp_path and os.path.exists(tmp_path):
            os.unlink(tmp_path)


@app.get("/health")
async def health() -> dict:
    """Servis sağlık kontrolü."""
    return {"status": "ok", "version": "1.0"}


# ─── BAŞLAT ──────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="127.0.0.1",
        port=8000,
        reload=True,
        timeout_keep_alive=1800,   # 30 dakika bağlantı timeout
    )
