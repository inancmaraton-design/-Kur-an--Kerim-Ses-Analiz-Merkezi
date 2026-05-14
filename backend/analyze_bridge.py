# -*- coding: utf-8 -*-
"""
Kuran Analiz v7.1 - Birleşik Python Bridge
Kotlin UI <-> Python Backend IPC (stdin/stdout, satır bazlı JSON)

Protokol:
  Başlangıç : Python → {"ok": true, "ready": true}
  Komutlar  : Kotlin → {"cmd": "...", ...}
  Yanıtlar  : Python → {"ok": true, "data": {...}}
                    veya {"ok": false, "error": "..."}

Desteklenen komutlar:
  analyze             → Ses dosyası analiz (UMAP 3D)
  spectrogram         → Spektrogram üret
  sureler_tara        → Klasördeki ses dosyalarını tara
  analiz_ve_karsilastir → Ses + Tecvid karşılaştırması
  korpus_listele      → Metin korpusunu listele (Pipeline B)
  metin_analiz        → Tek metin analizi (Pipeline B)
  karsilastir         → Grup istatistiksel karşılaştırması (Pipeline B)
  ping                → {"ok": true}
  quit                → çıkış
"""

import sys
import json
import traceback
import os
from pathlib import Path

# Backend klasörünü sys.path'e ekle
BACKEND = Path(__file__).parent
sys.path.insert(0, str(BACKEND))

# Windows UTF-8 zorlaması
if sys.platform == "win32":
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

# ─────────────────────────────────────────────────────────
# JSON iletişim yardımcıları
# ─────────────────────────────────────────────────────────

def send_ok(data: dict):
    """Başarılı yanıt gönder: {"ok": true, "data": {...}}"""
    print(json.dumps({"ok": True, "data": data}, ensure_ascii=False), flush=True)

def send_ok_raw(payload: dict):
    """ok:true içeren özel payload gönder (data wrapper olmadan)"""
    payload["ok"] = True
    print(json.dumps(payload, ensure_ascii=False), flush=True)

def send_error(msg: str, tb: str = None):
    """Hata yanıtı gönder: {"ok": false, "error": "..."}"""
    err = {"ok": False, "error": msg}
    if tb:
        err["traceback"] = tb
    print(json.dumps(err, ensure_ascii=False), flush=True)

def safe_float(v):
    """NaN/Inf → None (JSON serializable)"""
    if v is None:
        return None
    try:
        f = float(v)
        import math
        if math.isnan(f) or math.isinf(f):
            return None
        return round(f, 6)
    except (TypeError, ValueError):
        return None

# ─────────────────────────────────────────────────────────
# Pipeline A — Ses Analizi
# ─────────────────────────────────────────────────────────

def build_umap_response(wav_path: str, surah_name: str) -> dict:
    """WAV dosyasından tam UMAP 3D analiz yanıtı üret"""
    from main import build_response_json
    from audio_analyzer import load_audio

    y, sr = load_audio(wav_path)
    result = build_response_json(y, sr, surah_name)
    return result


def handle_analyze(req: dict):
    """
    {"cmd":"analyze","path":"...","name":"..."}
    → {"ok":true,"data":{metadata,umap_3d,segments}}
    """
    wav_path = req.get("path", "")
    name = req.get("name", "Bilinmiyor")

    if not wav_path or not Path(wav_path).exists():
        send_error(f"Dosya bulunamadı: {wav_path}")
        return

    try:
        result = build_umap_response(wav_path, name)
        send_ok(result)
    except Exception as e:
        send_error(f"Analiz hatası: {e}", traceback.format_exc())


def handle_spectrogram(req: dict):
    """
    {"cmd":"spectrogram","path":"..."}
    → {"ok":true,"data":{pixels,width,height,...}}
    """
    wav_path = req.get("path", "")

    if not wav_path or not Path(wav_path).exists():
        send_error(f"Dosya bulunamadı: {wav_path}")
        return

    try:
        from audio_analyzer import load_audio, extract_spectrogram
        y, sr = load_audio(wav_path)
        spec = extract_spectrogram(y, sr)
        send_ok(spec)
    except Exception as e:
        send_error(f"Spektrogram hatası: {e}", traceback.format_exc())


def handle_sureler_tara(req: dict):
    """
    {"cmd":"sureler_tara","klasor":"..."}
    → {"ok":true,"dosyalar":[{dosya_adi,tam_yol,...}]}
    """
    AUDIO_EXTS = {".wav", ".mp3", ".ogg", ".flac", ".m4a", ".aac"}

    try:
        from quran_data import dosya_adi_parse
    except ImportError:
        dosya_adi_parse = None

    klasor = req.get("klasor", "")
    if not klasor:
        candidates = [
            BACKEND.parent / "sureler",
            BACKEND / "sureler",
            Path(os.getcwd()) / "sureler",
        ]
        klasor_path = next((p for p in candidates if p.exists()), None)
        if not klasor_path:
            send_ok_raw({"dosyalar": []})
            return
    else:
        klasor_path = Path(klasor)

    if not klasor_path.exists():
        send_ok_raw({"dosyalar": []})
        return

    dosyalar = []
    for root, dirs, files in os.walk(str(klasor_path)):
        for f in sorted(files):
            p = Path(root) / f
            if p.suffix.lower() not in AUDIO_EXTS:
                continue

            # sure no / isim tespiti
            if dosya_adi_parse:
                parsed = dosya_adi_parse(f)
            else:
                parsed = {"sure_no": None, "sure_isim": p.stem,
                          "sure_isim_ar": "", "ayet_no": None, "kari": None}

            boyut_mb = round(p.stat().st_size / (1024 * 1024), 2)

            dosyalar.append({
                "dosya_adi": f,
                "tam_yol": str(p).replace("\\", "/"),
                "klasor": str(klasor_path).replace("\\", "/"),
                "sure_no": parsed.get("sure_no"),
                "sure_isim": parsed.get("sure_isim"),
                "sure_isim_ar": parsed.get("sure_isim_ar", ""),
                "ayet_no": parsed.get("ayet_no"),
                "kari": parsed.get("kari"),
                "format": p.suffix.upper().lstrip("."),
                "boyut_mb": boyut_mb,
            })

    send_ok_raw({"dosyalar": dosyalar})


def handle_analiz_ve_karsilastir(req: dict):
    """
    {"cmd":"analiz_ve_karsilastir","wav_path":"...","name":"...","sure_no":1,"ayet_no":1}
    → {"ok":true,"data":{metadata,umap_3d,segments,...karsilastirma}}
    """
    wav_path = req.get("wav_path", "")
    name = req.get("name", "Bilinmiyor")
    sure_no = req.get("sure_no")
    ayet_no = req.get("ayet_no")

    if not wav_path or not Path(wav_path).exists():
        send_error(f"Dosya bulunamadı: {wav_path}")
        return

    try:
        # Ses analizi (Pipeline A)
        result = build_umap_response(wav_path, name)

        # Tecvid karşılaştırması (varsa)
        metin = ""
        if sure_no is not None and ayet_no is not None:
            try:
                from comparison_engine import karsilastir
                whisper_segs = result.get("segments", [])
                karsilastirma = karsilastir(sure_no, ayet_no, result, whisper_segs)
                result["karsilastirma"] = karsilastirma
                metin = karsilastirma.get("ayet_metni", "")
            except Exception as e:
                result["karsilastirma"] = None
                print(f"[WARN] Karşılaştırma atlandı: {e}", file=sys.stderr)

        # Metin Analizi Metrikleri (Dilbilimsel/İstatistiksel)
        if metin:
            try:
                from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics
                fv = text_to_feature_vector(metin)
                result["metin_metrikleri"] = compute_summary_metrics(fv)
            except Exception as e:
                result["metin_metrikleri"] = None
                print(f"[WARN] Metin metrikleri atlandı: {e}", file=sys.stderr)

        send_ok(result)
    except Exception as e:
        send_error(f"Analiz + karşılaştırma hatası: {e}", traceback.format_exc())


# ─────────────────────────────────────────────────────────
# Pipeline B — Metin Tabanlı İstatistiksel Analiz
# ─────────────────────────────────────────────────────────

def handle_korpus_listele(req: dict):
    """
    {"cmd":"korpus_listele"}
    → {"ok":true,"data":{"kuran":[...],"kontrol":[...]}}
    """
    from data.quran_corpus import QURAN_CORPUS
    from data.control_corpus import CONTROL_CORPUS

    kuran_list = [
        {
            "id": k,
            "isim": v.get("sure_adi_tr", v.get("sure_adi", k)),
            "isim_ar": v.get("sure_adi", ""),
            "ayet_sayisi": v.get("ayet_sayisi", 0),
        }
        for k, v in QURAN_CORPUS.items()
    ]

    kontrol_list = [
        {
            "id": k,
            "kategori": v.get("kategori", ""),
            "yazar": v.get("yazar", v.get("ravi", "")),
        }
        for k, v in CONTROL_CORPUS.items()
    ]

    send_ok({"kuran": kuran_list, "kontrol": kontrol_list})


def handle_metin_analiz(req: dict):
    """
    {"cmd":"metin_analiz","text":"..."}
    → {"ok":true,"data":{"text":"...","metrics":{...}}}
    """
    from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics

    text = req.get("text", "").strip()
    if not text:
        send_error("Metin boş olamaz")
        return

    try:
        fv = text_to_feature_vector(text)
        metrics = compute_summary_metrics(fv)
        send_ok({"text": text, "metrics": metrics})
    except Exception as e:
        send_error(f"Metin analiz hatası: {e}", traceback.format_exc())


def handle_karsilastir(req: dict):
    """
    {"cmd":"karsilastir","kuran_ids":[...],"kontrol_ids":[...]}
    → {"ok":true,"data":{"sonuclar":[...],"kuran_sayisi":N,...}}
    """
    try:
        import pandas as pd
    except ImportError:
        send_error("pandas kurulu değil. 'pip install pandas' çalıştırın.")
        return

    import numpy as np
    from data.quran_corpus import QURAN_CORPUS
    from data.control_corpus import CONTROL_CORPUS
    from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics
    from analysis.statistical_engine import compare_groups, bonferroni_correction

    kuran_ids = req.get("kuran_ids", [])
    kontrol_ids = req.get("kontrol_ids", [])

    kuran_rows, kontrol_rows = [], []

    for qid in kuran_ids:
        if qid in QURAN_CORPUS:
            text = QURAN_CORPUS[qid].get("metin", "")
            if text:
                try:
                    m = compute_summary_metrics(text_to_feature_vector(text))
                    m["id"] = qid
                    kuran_rows.append(m)
                except Exception as e:
                    print(f"[WARN] {qid}: {e}", file=sys.stderr)

    for cid in kontrol_ids:
        if cid in CONTROL_CORPUS:
            text = CONTROL_CORPUS[cid].get("metin", "")
            if text:
                try:
                    m = compute_summary_metrics(text_to_feature_vector(text))
                    m["id"] = cid
                    kontrol_rows.append(m)
                except Exception as e:
                    print(f"[WARN] {cid}: {e}", file=sys.stderr)

    if not kuran_rows:
        send_error("Kur'an grubu boş — geçerli ID seçin")
        return
    if not kontrol_rows:
        send_error("Kontrol grubu boş — geçerli ID seçin")
        return

    kuran_df = pd.DataFrame(kuran_rows)
    kontrol_df = pd.DataFrame(kontrol_rows)

    metric_names = [
        "f2_ortalama", "f2_std", "f2_entropi",
        "f2_gecis_yumusakligi", "spektral_merkez_ortalama",
        "ritmik_duzgunluk_npvi", "nazal_oran",
    ]

    results = []
    for metric in metric_names:
        if metric in kuran_df.columns and metric in kontrol_df.columns:
            try:
                results.append(compare_groups(kuran_df, kontrol_df, metric))
            except Exception as e:
                print(f"[WARN] {metric}: {e}", file=sys.stderr)

    if not results:
        send_error("Hiçbir metrik karşılaştırılamadı")
        return

    results = bonferroni_correction(results)

    # numpy tiplerini Python native'e çevir
    clean = []
    for r in results:
        row = {}
        for k, v in r.items():
            if isinstance(v, np.bool_):
                row[k] = bool(v)
            elif isinstance(v, np.integer):
                row[k] = int(v)
            elif isinstance(v, np.floating):
                row[k] = safe_float(float(v))
            else:
                row[k] = v
        clean.append(row)

    send_ok({
        "sonuclar": clean,
        "kuran_sayisi": len(kuran_rows),
        "kontrol_sayisi": len(kontrol_rows),
    })


# ─────────────────────────────────────────────────────────
# Komut yönlendiricisi
# ─────────────────────────────────────────────────────────

HANDLERS = {
    # Pipeline A (ses)
    "analyze":               handle_analyze,
    "spectrogram":           handle_spectrogram,
    "sureler_tara":          handle_sureler_tara,
    "analiz_ve_karsilastir": handle_analiz_ve_karsilastir,
    # Pipeline B (metin)
    "korpus_listele":        handle_korpus_listele,
    "metin_analiz":          handle_metin_analiz,
    "karsilastir":           handle_karsilastir,
}


def main():
    # Kotlin'in beklediği hazır sinyali — MUTLAKA ilk satır olmalı
    print(json.dumps({"ok": True, "ready": True}, ensure_ascii=False), flush=True)

    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue

        try:
            req = json.loads(raw)
        except json.JSONDecodeError as e:
            send_error(f"JSON parse hatası: {e}")
            continue

        cmd = req.get("cmd", "")

        if cmd == "ping":
            print(json.dumps({"ok": True}, ensure_ascii=False), flush=True)
        elif cmd == "quit":
            break
        else:
            handler = HANDLERS.get(cmd)
            if handler:
                try:
                    handler(req)
                except Exception as e:
                    send_error(f"Handler hatası [{cmd}]: {e}", traceback.format_exc())
            else:
                send_error(
                    f"Bilinmeyen komut: {cmd!r}. "
                    f"Geçerliler: {list(HANDLERS)} + ping, quit"
                )


if __name__ == "__main__":
    main()
