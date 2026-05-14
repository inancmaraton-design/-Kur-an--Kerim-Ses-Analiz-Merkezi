# -*- coding: utf-8 -*-
"""
tajweed_rules.py — Metin uzerinden tecvid kural tespiti
"""

NUN_TENVIN_KURALLARI = {
    "izhar": {
        "harfler": ["\u0621","\u0647","\u0639","\u062d","\u063a","\u062e"],
        "aciklama": "Izhar — nun acik okunur",
        "beklenen_nazal_oran": (0.0, 0.15),
    },
    "idgam_gunna": {
        "harfler": ["\u064a","\u0646","\u0645","\u0648"],
        "aciklama": "Idgam (Gunnali) — harf birlesir gunna kalir",
        "beklenen_nazal_oran": (0.3, 0.7),
    },
    "idgam_bilagunna": {
        "harfler": ["\u0644","\u0631"],
        "aciklama": "Idgam (Gunnasiz) — harf birlesir",
        "beklenen_nazal_oran": (0.0, 0.15),
    },
    "iklab": {
        "harfler": ["\u0628"],
        "aciklama": "Iklab — Nun sesi Mim olur",
        "beklenen_nazal_oran": (0.25, 0.6),
    },
    "ihfa": {
        "harfler": list("\u062a\u062b\u062c\u062f\u0630\u0632\u0633\u0634\u0635\u0636\u0637\u0638\u0641\u0642\u0643"),
        "aciklama": "Ihfa — gizleme, yari nazal",
        "beklenen_nazal_oran": (0.15, 0.40),
    },
}

QALQALA_HARFLERI = ["\u0642","\u0637","\u0628","\u062c","\u062f"]
TEFHIM_HARFLERI  = ["\u0635","\u0636","\u0637","\u0638","\u063a","\u062e","\u0642"]


def ayet_kural_analiz(ayet_metni: str) -> list:
    """Ayet metnini tarayip tecvid kurallarini cikartir."""
    kurallar = []
    harfler  = [h for h in ayet_metni if h.strip()]

    for i, harf in enumerate(harfler):
        sonraki = harfler[i + 1] if i + 1 < len(harfler) else None

        # Nun/tenvin kurallari
        if harf in ["\u0646", "\u064b", "\u064d", "\u064c"] and sonraki:
            for kadi, kural in NUN_TENVIN_KURALLARI.items():
                if sonraki in kural["harfler"]:
                    kurallar.append({
                        "pozisyon": i,
                        "harf":     harf,
                        "kural":    kadi,
                        "sonraki":  sonraki,
                        "aciklama": kural["aciklama"],
                        "beklenen": {"nazal_oran": kural.get("beklenen_nazal_oran")},
                        "agirlik":  1.0
                    })

        # Qalqala (sekte sesi)
        if harf in QALQALA_HARFLERI and (not sonraki or sonraki in ["\u0652", " "]):
            kurallar.append({
                "pozisyon": i,
                "harf":     harf,
                "kural":    "qalqala",
                "aciklama": f"{harf} - qalqala gerekli",
                "beklenen": {},
                "agirlik":  0.9
            })

        # Tefhim (kalin okuma)
        if harf in TEFHIM_HARFLERI:
            kurallar.append({
                "pozisyon": i,
                "harf":     harf,
                "kural":    "tefhim",
                "aciklama": f"{harf} - kalin okunmali",
                "beklenen": {"F2_max": 1200},
                "agirlik":  0.8
            })

        # Med (uzatma) — elif, vav, ya harfleri
        if harf in ["\u0627", "\u0648", "\u064a"]:
            kurallar.append({
                "pozisyon": i,
                "harf":     harf,
                "kural":    "med_tabii",
                "aciklama": f"{harf} - 2 elif uzatma",
                "beklenen": {"sure_ms": (180, 380)},
                "agirlik":  0.7
            })

    return kurallar
