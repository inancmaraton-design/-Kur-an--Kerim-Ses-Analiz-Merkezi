# -*- coding: utf-8 -*-
"""
quran_data.py — Sure/Ayet metin veritabani
sureler/ klasoru: NNN_SureName.mp3 formatinda dosyalar iceriyor
"""
import re, os

# ─── Sure veritabani (kisaltilmis — tefsir amacli sure'ler) ───────────────────
SURELER = {
    1:   {"isim": "Fatiha",   "isim_ar": "\u0627\u0644\u0641\u0627\u062a\u062d\u0629",   "ayet_sayisi": 7,
          "ayetler": {
              1: "\u0628\u0650\u0633\u0652\u0645\u0650 \u0671\u0644\u0644\u0651\u064e\u0647\u0650 \u0671\u0644\u0631\u0651\u064e\u062d\u0652\u0645\u064e\u0670\u0646\u0650 \u0671\u0644\u0631\u0651\u064e\u062d\u0650\u064a\u0645\u0650",
              2: "\u0671\u0644\u0652\u062d\u064e\u0645\u0652\u062f\u064f \u0644\u0650\u0644\u0651\u064e\u0647\u0650 \u0631\u064e\u0628\u0651\u0650 \u0671\u0644\u0652\u0639\u064e\u0670\u0644\u064e\u0645\u0650\u064a\u0646\u064e",
              3: "\u0671\u0644\u0631\u0651\u064e\u062d\u0652\u0645\u064e\u0670\u0646\u0650 \u0671\u0644\u0631\u0651\u064e\u062d\u0650\u064a\u0645\u0650",
              4: "\u0645\u064e\u0670\u0644\u0650\u0643\u0650 \u064a\u064e\u0648\u0652\u0645\u0650 \u0671\u0644\u062f\u0651\u0650\u064a\u0646\u0650",
              5: "\u0625\u0650\u064a\u0651\u064e\u0627\u0643\u064e \u0646\u064e\u0639\u0652\u0628\u064f\u062f\u064f \u0648\u064e\u0625\u0650\u064a\u0651\u064e\u0627\u0643\u064e \u0646\u064e\u0633\u0652\u062a\u064e\u0639\u0650\u064a\u0646\u064f",
              6: "\u0671\u0647\u0652\u062f\u0650\u0646\u064e\u0627 \u0671\u0644\u0635\u0651\u0650\u0631\u064e\u0670\u0637\u064e \u0671\u0644\u0652\u0645\u064f\u0633\u0652\u062a\u064e\u0642\u0650\u064a\u0645\u064e",
              7: "\u0635\u0650\u0631\u064e\u0670\u0637\u064e \u0671\u0644\u0651\u064e\u0630\u0650\u064a\u0646\u064e \u0623\u064e\u0646\u0652\u0639\u064e\u0645\u0652\u062a\u064e \u0639\u064e\u0644\u064e\u064a\u0652\u0647\u0650\u0645\u0652",
          }},
    112: {"isim": "Ihlas",    "isim_ar": "\u0627\u0644\u0625\u062e\u0644\u0627\u0635",    "ayet_sayisi": 4,
          "ayetler": {
              1: "\u0642\u064f\u0644\u0652 \u0647\u064f\u0648\u064e \u0671\u0644\u0644\u0651\u064e\u0647\u064f \u0623\u064e\u062d\u064e\u062f\u064c",
              2: "\u0671\u0644\u0644\u0651\u064e\u0647\u064f \u0671\u0644\u0635\u0651\u064e\u0645\u064e\u062f\u064f",
              3: "\u0644\u064e\u0645\u0652 \u064a\u064e\u0644\u0650\u062f\u0652 \u0648\u064e\u0644\u064e\u0645\u0652 \u064a\u064f\u0648\u0644\u064e\u062f\u0652",
              4: "\u0648\u064e\u0644\u064e\u0645\u0652 \u064a\u064e\u0643\u064f\u0646 \u0644\u0651\u064e\u0647\u064f\u00a5 \u0643\u064f\u0641\u064f\u0648\u064b\u0627 \u0623\u064e\u062d\u064e\u062f\u064c",
          }},
    113: {"isim": "Felak",    "isim_ar": "\u0627\u0644\u0641\u0644\u0642",    "ayet_sayisi": 5,
          "ayetler": {
              1: "\u0642\u064f\u0644\u0652 \u0623\u064e\u0639\u064f\u0648\u0630\u064f \u0628\u0650\u0631\u064e\u0628\u0651\u0650 \u0671\u0644\u0652\u0641\u064e\u0644\u064e\u0642\u0650",
              2: "\u0645\u0650\u0646 \u0634\u064e\u0631\u0651\u0650 \u0645\u064e\u0627 \u062e\u064e\u0644\u064e\u0642\u064e",
              3: "\u0648\u064e\u0645\u0650\u0646 \u0634\u064e\u0631\u0651\u0650 \u063a\u064e\u0627\u0633\u0650\u0642\u064d \u0625\u0650\u0630\u064e\u0627 \u0648\u064e\u0642\u064e\u0628\u064e",
              4: "\u0648\u064e\u0645\u0650\u0646 \u0634\u064e\u0631\u0651\u0650 \u0671\u0644\u0646\u0651\u064e\u0641\u0651\u064e\u0670\u062b\u064e\u0670\u062a\u0650 \u0641\u0650\u064a \u0671\u0644\u0652\u0639\u064f\u0642\u064e\u062f\u0650",
              5: "\u0648\u064e\u0645\u0650\u0646 \u0634\u064e\u0631\u0651\u0650 \u062d\u064e\u0627\u0633\u0650\u062f\u064d \u0625\u0650\u0630\u064e\u0627 \u062d\u064e\u0633\u064e\u062f\u064e",
          }},
    114: {"isim": "Nas",      "isim_ar": "\u0627\u0644\u0646\u0627\u0633",      "ayet_sayisi": 6,
          "ayetler": {
              1: "\u0642\u064f\u0644\u0652 \u0623\u064e\u0639\u064f\u0648\u0630\u064f \u0628\u0650\u0631\u064e\u0628\u0651\u0650 \u0671\u0644\u0646\u0651\u064e\u0627\u0633\u0650",
              2: "\u0645\u064e\u0644\u0650\u0643\u0650 \u0671\u0644\u0646\u0651\u064e\u0627\u0633\u0650",
              3: "\u0625\u0650\u0644\u064e\u0670\u0647\u0650 \u0671\u0644\u0646\u0651\u064e\u0627\u0633\u0650",
              4: "\u0645\u0650\u0646 \u0634\u064e\u0631\u0651\u0650 \u0671\u0644\u0652\u0648\u064e\u0633\u0652\u0648\u064e\u0627\u0633\u0650 \u0671\u0644\u0652\u062e\u064e\u0646\u0651\u064e\u0627\u0633\u0650",
              5: "\u0671\u0644\u0651\u064e\u0630\u0650\u064a \u064a\u064f\u0648\u064e\u0633\u0652\u0648\u0650\u0633\u064f \u0641\u0650\u064a \u0635\u064f\u062f\u064f\u0648\u0631\u0650 \u0671\u0644\u0646\u0651\u064e\u0627\u0633\u0650",
              6: "\u0645\u0650\u0646\u064e \u0671\u0644\u0652\u062c\u0650\u0646\u0651\u064e\u0629\u0650 \u0648\u064e\u0671\u0644\u0646\u0651\u064e\u0627\u0633\u0650",
          }},
    36:  {"isim": "Yasin",    "isim_ar": "\u064a\u0633", "ayet_sayisi": 83, "ayetler": {}},
    67:  {"isim": "Mulk",     "isim_ar": "\u0627\u0644\u0645\u0644\u0643",  "ayet_sayisi": 30, "ayetler": {}},
    55:  {"isim": "Rahman",   "isim_ar": "\u0627\u0644\u0631\u062d\u0645\u0646", "ayet_sayisi": 78, "ayetler": {}},
    78:  {"isim": "Nebe",     "isim_ar": "\u0627\u0644\u0646\u0628\u0623",   "ayet_sayisi": 40, "ayetler": {}},
    56:  {"isim": "Vakia",    "isim_ar": "\u0627\u0644\u0648\u0627\u0642\u0639\u0629", "ayet_sayisi": 96, "ayetler": {}},
}

# Dosya adi -> sure no mapping (NNN_SureName.mp3 format)
_DOSYA_SURE_MAP = {}

def _build_dosya_map():
    """NNN_ prefix ile sure numarasini eslestirir."""
    for no in SURELER:
        _DOSYA_SURE_MAP[no] = no

_build_dosya_map()


def dosya_adi_parse(dosya_adi: str) -> dict:
    """
    NNN_SureName.mp3 veya NNN_SureName.wav -> {sure_no, sure_isim, ayet_no}
    Ornek: "001_Fatiha.mp3" -> {sure_no: 1, sure_isim: "Fatiha", ayet_no: None}
    """
    ad = re.sub(r'\.(mp3|wav)$', '', dosya_adi, flags=re.IGNORECASE)
    
    # NNN_ prefix
    m = re.match(r'^(\d+)[_\-](.+)$', ad)
    if m:
        sure_no_str = m.group(1)
        sure_isim   = m.group(2).replace('_', ' ').replace('-', ' ')
        sure_no     = int(sure_no_str)
        sure_obj    = SURELER.get(sure_no, {})
        
        # Ayet no: orn "001_Fatiha_ayet3"
        ayet_no = None
        ayet_m = re.search(r'[Aa]yet(\d+)', sure_isim)
        if ayet_m:
            ayet_no = int(ayet_m.group(1))
        
        return {
            "sure_no":    sure_no,
            "sure_isim":  sure_obj.get("isim", sure_isim),
            "sure_isim_ar": sure_obj.get("isim_ar", ""),
            "ayet_no":    ayet_no,
            "kari":       None
        }
    
    return {"sure_no": None, "sure_isim": dosya_adi, "sure_isim_ar": "", "ayet_no": None, "kari": None}


def sure_metin_al(sure_no: int, ayet_no=None) -> str:
    """Sure/ayet metnini dondur."""
    sure = SURELER.get(sure_no)
    if not sure:
        return ""
    if ayet_no is not None:
        return sure["ayetler"].get(ayet_no, "")
    return " ".join(v for v in sure["ayetler"].values()) if sure.get("ayetler") else ""
