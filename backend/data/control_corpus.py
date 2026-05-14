# -*- coding: utf-8 -*-
"""Kontrol korpusu yükleyici - control_corpus.json wrapper"""
import json
from pathlib import Path

_DATA_DIR = Path(__file__).parent
_CONTROL_CORPUS_PATH = _DATA_DIR / "control_corpus.json"


def load_control_corpus() -> dict:
    """Kontrol grubu metinlerini {id: {...}} dict olarak yükle"""
    if not _CONTROL_CORPUS_PATH.exists():
        raise FileNotFoundError(
            f"control_corpus.json bulunamadı: {_CONTROL_CORPUS_PATH}"
        )

    with open(_CONTROL_CORPUS_PATH, "r", encoding="utf-8") as f:
        raw = json.load(f)

    # {"metinler": [...]} formatını {id: {...}} dict'e çevir
    if isinstance(raw, dict) and "metinler" in raw:
        items = raw["metinler"]
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


CONTROL_CORPUS = load_control_corpus()

if len(CONTROL_CORPUS) == 0:
    raise ValueError("Kontrol korpusu boş — control_corpus.json kontrol et")
