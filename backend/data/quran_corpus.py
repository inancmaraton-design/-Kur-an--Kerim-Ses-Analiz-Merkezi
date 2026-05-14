# -*- coding: utf-8 -*-
"""Kur'an korpusu yükleyici - quran_corpus.json wrapper"""
import json
from pathlib import Path

_DATA_DIR = Path(__file__).parent
_QURAN_CORPUS_PATH = _DATA_DIR / "quran_corpus.json"


def load_quran_corpus() -> dict:
    """Kur'an metinlerini {id: {...}} dict olarak yükle"""
    if not _QURAN_CORPUS_PATH.exists():
        raise FileNotFoundError(
            f"quran_corpus.json bulunamadı: {_QURAN_CORPUS_PATH}"
        )

    with open(_QURAN_CORPUS_PATH, "r", encoding="utf-8") as f:
        raw = json.load(f)

    # {"sureler": [...]} formatını {id: {...}} dict'e çevir
    if isinstance(raw, dict) and "sureler" in raw:
        items = raw["sureler"]
    elif isinstance(raw, list):
        items = raw
    else:
        # Zaten dict formatında olabilir
        return raw

    corpus = {}
    for item in items:
        item_id = item.get("id", "")
        if item_id:
            corpus[item_id] = item

    return corpus


QURAN_CORPUS = load_quran_corpus()

if len(QURAN_CORPUS) == 0:
    raise ValueError("Kur'an korpusu boş — quran_corpus.json kontrol et")
