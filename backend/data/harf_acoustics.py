# -*- coding: utf-8 -*-
"""Harf akustik veritabanı yükleyici - harf_acoustics.json wrapper"""
import json
from pathlib import Path

_DATA_DIR = Path(__file__).parent

# Önce backend/data/harf_acoustics.json, sonra backend/harf_acoustics.json'a bak
_PATHS_TO_TRY = [
    _DATA_DIR / "harf_acoustics.json",
    _DATA_DIR.parent / "harf_acoustics.json",
]


def load_harf_acoustics() -> dict:
    """28 Arap harfinin akustik özelliklerini yükle"""
    for path in _PATHS_TO_TRY:
        if path.exists():
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)

    raise FileNotFoundError(
        f"harf_acoustics.json bulunamadı. Aranan yollar:\n"
        + "\n".join(f"  {p}" for p in _PATHS_TO_TRY)
    )


HARF_ACOUSTICS = load_harf_acoustics()

if len(HARF_ACOUSTICS) != 28:
    raise ValueError(
        f"28 harf bekleniyor, {len(HARF_ACOUSTICS)} bulundu — harf_acoustics.json kontrol et"
    )
