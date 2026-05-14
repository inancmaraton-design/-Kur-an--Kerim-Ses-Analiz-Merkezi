# -*- coding: utf-8 -*-
"""
comparison_engine.py — Ses olcumleri vs Tecvid kurallari karsilastirmasi
"""
import numpy as np


def kural_kontrol(kural: dict, ses_noktalari: list, zaman: float) -> dict:
    """Verilen zamana yakin noktalari bulup kural skori hesaplar."""
    yakin = [n for n in ses_noktalari
             if isinstance(n, dict) and abs(float(n.get("t", n.get("time_sec", 0))) - zaman) < 0.25]
    if not yakin:
        return {"skor": 60.0, "durum": "tespit_edilemedi"}

    beklenen = kural.get("beklenen", {})
    puanlar  = []

    # Nazal oran kontrolu
    if beklenen.get("nazal_oran"):
        nazal_degerler = [n.get("nazal", None) for n in yakin if n.get("nazal") is not None]
        if nazal_degerler:
            ort_nazal = float(np.nanmean(nazal_degerler))
            mn, mx    = beklenen["nazal_oran"]
            if mn <= ort_nazal <= mx:
                puanlar.append(100.0)
            else:
                fark = min(abs(ort_nazal - mn), abs(ort_nazal - mx))
                puanlar.append(max(0.0, 100.0 - fark * 200.0))

    # F2 kontrolu (tefhim icin)
    if beklenen.get("F2_max"):
        f2_degerler = [n.get("f2", None) for n in yakin if n.get("f2") is not None]
        if f2_degerler:
            ort_f2 = float(np.nanmean(f2_degerler))
            if ort_f2 <= beklenen["F2_max"]:
                puanlar.append(100.0)
            else:
                puanlar.append(max(0.0, 100.0 - (ort_f2 - beklenen["F2_max"]) / 500.0 * 100.0))

    # Skor ve durum
    skor = float(np.mean(puanlar)) if puanlar else 70.0
    durum = "dogru" if skor >= 80 else "kismi" if skor >= 50 else "yanlis"
    return {"skor": round(skor, 1), "durum": durum}


def karsilastir(sure_no: int, ayet_no, ses_sonucu: dict, whisper_segmentler: list) -> dict:
    """
    Ses analizi sonucunu tecvid kurallariyla karsilastirir.
    Returns: KarsilastirmaResult sozlugu
    """
    from quran_data import SURELER, sure_metin_al
    from tajweed_rules import ayet_kural_analiz

    sure = SURELER.get(sure_no)
    if not sure:
        return {
            "sure_isim": f"Sure {sure_no}", "sure_isim_ar": "",
            "ayet_no": ayet_no, "ayet_metni": "",
            "genel_skor": 0.0, "dogru_sayisi": 0, "yanlis_sayisi": 0, "kismi_sayisi": 0,
            "kurallar": [], "iyi_yapilan": [], "dikkat_gereken": []
        }

    metin     = sure_metin_al(sure_no, ayet_no)
    kurallar  = ayet_kural_analiz(metin) if metin else []
    noktalari = ses_sonucu.get("points", ses_sonucu.get("umap_3d", []))
    sonuclar  = []

    for kural in kurallar:
        # Kelimedeki haref zamanini bul
        zaman = 0.0
        for seg in whisper_segmentler:
            for wrd in seg.get("words", []):
                if kural.get("harf", "") in wrd.get("word", ""):
                    zaman = float(wrd.get("start", 0.0))
                    break

        eslesme = kural_kontrol(kural, noktalari, zaman)
        sonuclar.append({
            "harf":     kural["harf"],
            "kural":    kural["kural"],
            "aciklama": kural["aciklama"],
            "sonuc":    eslesme,
            "agirlik":  kural.get("agirlik", 1.0)
        })

    # Genel skor
    if sonuclar:
        toplam_agirlik = sum(k["agirlik"] for k in sonuclar)
        genel = sum(k["sonuc"]["skor"] * k["agirlik"] for k in sonuclar) / max(toplam_agirlik, 1.0)
    else:
        genel = 75.0  # Kural bulunamadiysa ortalama puan

    dogru  = [k for k in sonuclar if k["sonuc"]["durum"] == "dogru"]
    yanlis = [k for k in sonuclar if k["sonuc"]["durum"] == "yanlis"]
    kismi  = [k for k in sonuclar if k["sonuc"]["durum"] == "kismi"]

    return {
        "sure_isim":      sure.get("isim", ""),
        "sure_isim_ar":   sure.get("isim_ar", ""),
        "ayet_no":        ayet_no,
        "ayet_metni":     metin,
        "genel_skor":     round(genel, 1),
        "dogru_sayisi":   len(dogru),
        "yanlis_sayisi":  len(yanlis),
        "kismi_sayisi":   len(kismi),
        "kurallar":       sonuclar,
        "iyi_yapilan":    [k["aciklama"] for k in dogru[:3]],
        "dikkat_gereken": [k["aciklama"] for k in yanlis[:3]],
    }
