"""
analyze_bridge.py — Kotlin subprocess bridge
Kotlin uygulaması bu scripti başlatır ve stdin/stdout üzerinden haberleşir.
FastAPI/HTTP/uvicorn gerektirmez.

Protokol (satır bazlı):
  Kotlin  →  stdin : {"cmd": "analyze", "path": "/tmp/xxx.wav", "name": "Sure"}
  Python  →  stdout: {"ok": true, "data": {...}}   veya  {"ok": false, "error": "..."}

  Kotlin  →  stdin : {"cmd": "ping"}
  Python  →  stdout: {"ok": true, "pong": true}

  Kotlin  →  stdin : {"cmd": "quit"}
  Python  →  stdout: (yok, process kapanır)
"""

import sys
import json
import traceback
import os

# Scriptin bulunduğu dizini path'e ekle (audio_analyzer.py'yi bul)
_DIR = os.path.dirname(os.path.abspath(__file__))
if _DIR not in sys.path:
    sys.path.insert(0, _DIR)

# ─── Analiz modülünü yükle ───────────────────────────────────────────────────
try:
    import librosa
    import numpy as np
    from audio_analyzer import (
        _normalize,
        _trim_or_pad,
        assign_color,
        extract_formants,
        extract_mfcc,
        extract_pitch,
        extract_spectral,
        extract_spectrogram,
        load_audio,
        segment_by_silence,
        to_3d_umap,
    )
    _READY = True
except Exception as _import_err:
    _READY = False
    _IMPORT_ERROR = str(_import_err)


# ─── Pipeline ────────────────────────────────────────────────────────────────

_N_FFT = 2048
_HOP   = 512


def _to_list(arr):
    return [None if np.isnan(v) else round(float(v), 6) for v in arr]


def _get_label(t, segments):
    for idx, seg in enumerate(segments):
        if seg["start"] <= t < seg["end"]:
            return f"Ayet {idx + 1}"
    return "silence"


def build_response(y, sr, surah_name):
    """Tüm pipeline: audio array → JSON dict."""
    # 1. Pitch
    f0_raw, voiced_flag = extract_pitch(y, sr, _HOP)

    # 2. MFCC
    mfcc_mat = extract_mfcc(y, sr, _N_FFT, _HOP)
    n_frames = mfcc_mat.shape[1]

    # 3. Spektral
    spectral = extract_spectral(y, sr, _N_FFT, _HOP)

    # 4. Formantlar
    f1_raw, f2_raw = extract_formants(y, sr, _N_FFT, _HOP)

    # 5. Hizala
    f0       = _trim_or_pad(f0_raw, n_frames)
    voiced   = _trim_or_pad(voiced_flag.astype(float), n_frames)
    centroid = _trim_or_pad(spectral["centroid"], n_frames)
    flux     = _trim_or_pad(spectral["flux"],     n_frames)
    zcr      = _trim_or_pad(spectral["zcr"],      n_frames)
    rolloff  = _trim_or_pad(spectral["rolloff"],  n_frames)
    rms      = _trim_or_pad(spectral["rms"],      n_frames)
    f1       = _trim_or_pad(f1_raw, n_frames)
    f2       = _trim_or_pad(f2_raw, n_frames)

    # 6. Normalize
    f0_norm       = _normalize(f0)
    rms_norm      = _normalize(rms)
    centroid_norm = _normalize(centroid)
    flux_norm     = _normalize(flux)
    zcr_norm      = _normalize(zcr)
    rolloff_norm  = _normalize(rolloff)
    f1_norm       = _normalize(f1)
    f2_norm       = _normalize(f2)

    # 7. UMAP öznitelik matrisi (n_frames, 46)
    mfcc_T = mfcc_mat.T
    extra = np.column_stack([
        np.nan_to_num(f0_norm,       nan=0.0),
        np.nan_to_num(rms_norm,      nan=0.0),
        np.nan_to_num(centroid_norm, nan=0.0),
        np.nan_to_num(flux_norm,     nan=0.0),
        np.nan_to_num(zcr_norm,      nan=0.0),
        np.nan_to_num(f1_norm,       nan=0.0),
        np.nan_to_num(f2_norm,       nan=0.0),
    ])
    features_matrix = np.hstack([mfcc_T, extra])
    coords_3d = to_3d_umap(features_matrix)

    # 8. Segment tespiti
    segments = segment_by_silence(y, sr)

    # 9. umap_3d noktaları
    duration_sec = float(librosa.get_duration(y=y, sr=sr))
    umap_3d = []
    for i in range(n_frames):
        t_sec  = float(librosa.frames_to_time(i, sr=sr, hop_length=_HOP))
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

    # 10. Segmentler
    seg_out = []
    for idx, seg in enumerate(segments):
        seg_out.append({
            "label":       f"Ayet {idx + 1}",
            "start":       round(seg["start"], 4),
            "end":         round(seg["end"], 4),
            "frame_start": int(librosa.time_to_frames(seg["start"], sr=sr, hop_length=_HOP)),
            "frame_end":   int(librosa.time_to_frames(seg["end"],   sr=sr, hop_length=_HOP)),
        })

    return {
        "metadata": {
            "surah":            surah_name,
            "duration_sec":     round(duration_sec, 3),
            "sample_rate":      sr,
            "n_frames":         n_frames,
            "n_fft":            _N_FFT,
            "hop_length":       _HOP,
            "analysis_version": "2.0",
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


# ─── Ana döngü ────────────────────────────────────────────────────────────────

def _write(obj):
    """stdout'a tek satır JSON yaz ve flush yap."""
    line = json.dumps(obj, ensure_ascii=False)
    sys.stdout.write(line + "\n")
    sys.stdout.flush()


def main():
    # stderr'i tamamen sessizleştir (loguru/librosa uyarıları Kotlin'i karıştırmasın)
    # Önemli hatalar {"ok":false,...} ile iletilir.
    sys.stderr = open(os.devnull, "w")

    if not _READY:
        _write({"ok": False, "error": f"Import hatasi: {_IMPORT_ERROR}"})
        sys.exit(1)

    # Hazır sinyali gönder — Kotlin bu satırı bekler
    _write({"ok": True, "ready": True})

    for raw_line in sys.stdin:
        raw_line = raw_line.strip()
        if not raw_line:
            continue
        try:
            req = json.loads(raw_line)
        except json.JSONDecodeError as e:
            _write({"ok": False, "error": f"JSON parse hatasi: {e}"})
            continue

        cmd = req.get("cmd", "")

        if cmd == "ping":
            _write({"ok": True, "pong": True})

        elif cmd == "analyze":
            path = req.get("path", "")
            name = req.get("name", "Bilinmiyor")
            if not os.path.isfile(path):
                _write({"ok": False, "error": f"Dosya bulunamadi: {path}"})
                continue
            try:
                y, sr = load_audio(path)
                data  = build_response(y, sr, name)
                _write({"ok": True, "data": data})
            except Exception:
                _write({"ok": False, "error": traceback.format_exc()})

        elif cmd == "spectrogram":
            path = req.get("path", "")
            if not os.path.isfile(path):
                _write({"ok": False, "error": f"Dosya bulunamadi: {path}"})
                continue
            try:
                y, sr = load_audio(path)
                data = extract_spectrogram(y, sr)
                _write({"ok": True, "data": data})
            except Exception:
                _write({"ok": False, "error": traceback.format_exc()})

        elif cmd == "sureler_tara":
            # sureler/ klasoründeki MP3/WAV dosyalarini listele
            try:
                from quran_data import dosya_adi_parse, SURELER
                sureler_klasor = req.get("klasor", "")
                if not sureler_klasor:
                    # Varsayilan: script yanindaki sureler/ klasoru
                    proje_kok = os.path.dirname(os.path.dirname(_DIR))
                    sureler_klasor = os.path.join(proje_kok, "sureler")
                    if not os.path.exists(sureler_klasor):
                        sureler_klasor = os.path.join(_DIR, "..", "sureler")

                dosyalar = []
                if os.path.exists(sureler_klasor):
                    for kok, _, dosya_listesi in os.walk(sureler_klasor):
                        for f in sorted(dosya_listesi):
                            if f.lower().endswith((".mp3", ".wav")):
                                tam_yol = os.path.join(kok, f)
                                bilgi   = dosya_adi_parse(f)
                                dosyalar.append({
                                    "dosya_adi":    f,
                                    "tam_yol":      tam_yol.replace("\\", "/"),
                                    "klasor":       os.path.basename(kok),
                                    "sure_no":      bilgi["sure_no"],
                                    "sure_isim":    bilgi["sure_isim"],
                                    "sure_isim_ar": bilgi["sure_isim_ar"],
                                    "ayet_no":      bilgi["ayet_no"],
                                    "kari":         bilgi["kari"],
                                    "format":       f.rsplit(".", 1)[-1].upper(),
                                    "boyut_mb":     round(os.path.getsize(tam_yol) / 1024 / 1024, 2)
                                })

                _write({"ok": True, "dosyalar": dosyalar})
            except Exception:
                _write({"ok": False, "error": traceback.format_exc()})

        elif cmd == "analiz_ve_karsilastir":
            path     = req.get("wav_path", req.get("path", ""))
            sure_no  = req.get("sure_no")
            ayet_no  = req.get("ayet_no")
            if not os.path.isfile(path):
                _write({"ok": False, "error": f"Dosya bulunamadi: {path}"})
                continue
            try:
                y, sr     = load_audio(path)
                ses_data  = build_response(y, sr, req.get("name", os.path.basename(path)))

                karsilastirma = None
                if sure_no is not None:
                    try:
                        from comparison_engine import karsilastir
                        karsilastirma = karsilastir(
                            sure_no=int(sure_no),
                            ayet_no=int(ayet_no) if ayet_no is not None else None,
                            ses_sonucu=ses_data,
                            whisper_segmentler=ses_data.get("segments", [])
                        )
                    except Exception as ce:
                        karsilastirma = {"hata": str(ce)}

                ses_data["karsilastirma"] = karsilastirma
                _write({"ok": True, "data": ses_data})
            except Exception:
                _write({"ok": False, "error": traceback.format_exc()})

        elif cmd == "quit":
            break

        else:
            _write({"ok": False, "error": f"Bilinmeyen komut: {cmd}"})


if __name__ == "__main__":
    main()
