"""
tajweed_detector.py — Tecvid Kuralı Tespiti
Arapça harflerin bağlamsal tecvid kurallarını tanır.
"""
from dataclasses import dataclass
from typing import List, Optional
from analysis.text_parser import HarfToken, tokenize_arabic


@dataclass
class KuralEslesme:
    """Tespit edilen tecvid kuralı"""
    kural_adi: str          # "gunna", "ihfa", "qalqala" vb.
    baslama_pozisyonu: int  # Token indeksi
    bitis_pozisyonu: int    # Token indeksi
    aciklama: str
    mim_nun_kodu: Optional[str] = None  # "gunna_nun", "gunna_mim" vb.


# Tespiti Kurallar
IHFA_HARFLERI = {'س', 'ز', 'ط', 'ش', 'ص', 'د', 'ت', 'ض', 'ل', 'ن', 
                 'ك', 'ب', 'ج', 'ف', 'ق'}  # 15 harften 14'ü (nun hariç)

QALQALA_HARFLERI = {'ق', 'ط', 'ب', 'ج', 'د'}

MED_HARFLERI = {'ا', 'و', 'ي'}  # Elif, Vav, Ya

TEFHIM_HARFLERI = {'ق', 'خ', 'ص', 'ض', 'ط', 'ظ'}

LIQUID_HARFLERI = {'ل', 'ر'}  # Liquid sonoranter

NASAL_HARFLERI = {'م', 'ن'}  # Nasals


def detect_gunna(tokens: List[HarfToken]) -> List[KuralEslesme]:
    """
    Gunna (غنة): Şedde'li Nun (نّ) ve Mim (مّ)
    Nasallikleştirilmiş ses (~ng gibi)
    """
    kurallar = []
    
    for i, token in enumerate(tokens):
        # Şedde'li Nun
        if token.temel_harf == 'ن' and token.sedde:
            kurallar.append(KuralEslesme(
                kural_adi="gunna",
                baslama_pozisyonu=i,
                bitis_pozisyonu=i,
                aciklama="Şedde'li Nun (غنة)",
                mim_nun_kodu="gunna_nun"
            ))
        # Şedde'li Mim
        elif token.temel_harf == 'م' and token.sedde:
            kurallar.append(KuralEslesme(
                kural_adi="gunna",
                baslama_pozisyonu=i,
                bitis_pozisyonu=i,
                aciklama="Şedde'li Mim (غنة)",
                mim_nun_kodu="gunna_mim"
            ))
    
    return kurallar


def detect_ihfa(tokens: List[HarfToken]) -> List[KuralEslesme]:
    """
    İhfa: Nun Sukun (نْ) + 15 ihfa harfinden biri
    Nun kaybolmuş (hidden) görünür ama burun sesiyle çıkar.
    """
    kurallar = []
    
    for i in range(len(tokens) - 1):
        token = tokens[i]
        next_token = tokens[i + 1]
        
        # Nun Sukun bulundu
        if token.temel_harf == 'ن' and token.sukun:
            # Sonraki harf ihfa harfi mi?
            if next_token.temel_harf in IHFA_HARFLERI:
                kurallar.append(KuralEslesme(
                    kural_adi="ihfa",
                    baslama_pozisyonu=i,
                    bitis_pozisyonu=i + 1,
                    aciklama=f"İhfa: نْ + {next_token.temel_harf}"
                ))
    
    return kurallar


def detect_qalqala(tokens: List[HarfToken]) -> List[KuralEslesme]:
    """
    Qalqala (قلقلة): Sukun'lu Qaf, Ta, Ba, Cim, Dal
    "Titreme" sesi — kelimeyi şiddetle çıkartılır.
    """
    kurallar = []
    
    for i, token in enumerate(tokens):
        if token.temel_harf in QALQALA_HARFLERI and token.sukun:
            kurallar.append(KuralEslesme(
                kural_adi="qalqala",
                baslama_pozisyonu=i,
                bitis_pozisyonu=i,
                aciklama=f"Qalqala: {token.temel_harf} + Sukun"
            ))
    
    return kurallar


def detect_med(tokens: List[HarfToken]) -> List[KuralEslesme]:
    """
    Med (مد): Sesin uzatılması
    - Med Tabii: Elif, Vav, Ya + normal hareke (2 zaman)
    - Med Wacib: Hemza + Med harfi (4 zaman)
    - Med Lazim: Hemza + Med harfi + Sukun (6 zaman)
    """
    kurallar = []
    
    for i in range(len(tokens) - 1):
        token = tokens[i]
        next_token = tokens[i + 1]
        
        # Med Tabii: Elif, Vav, Ya (şedde yok, normal hale)
        if token.temel_harf in MED_HARFLERI and not token.sedde:
            kurallar.append(KuralEslesme(
                kural_adi="med_tabii",
                baslama_pozisyonu=i,
                bitis_pozisyonu=i,
                aciklama=f"Med Tabii: {token.temel_harf} (2 zaman)"
            ))
        
        # Med Wacib: Hemza + Med harfi
        if token.temel_harf == 'ء':  # Hemza
            if next_token.temel_harf in MED_HARFLERI:
                kurallar.append(KuralEslesme(
                    kural_adi="med_wacib",
                    baslama_pozisyonu=i,
                    bitis_pozisyonu=i + 1,
                    aciklama=f"Med Wacib: ء + {next_token.temel_harf} (4 zaman)"
                ))
        
        # Med Lazim: Med harfi + Sukun (Hemza şart değil, sadece bağlam)
        if token.temel_harf in MED_HARFLERI and next_token.sukun:
            kurallar.append(KuralEslesme(
                kural_adi="med_lazim",
                baslama_pozisyonu=i,
                bitis_pozisyonu=i + 1,
                aciklama=f"Med Lazim: {token.temel_harf} + Sukun (6 zaman)"
            ))
    
    return kurallar


def detect_tefhim(tokens: List[HarfToken]) -> List[KuralEslesme]:
    """
    Tefhim (تفخيم): Ses derinleştirilir (back vowel orientation)
    Tefhim Harfleri: Qaf, Kha, Sad, Dad, Ta, Za
    """
    kurallar = []
    
    for i, token in enumerate(tokens):
        if token.temel_harf in TEFHIM_HARFLERI:
            kurallar.append(KuralEslesme(
                kural_adi="tefhim",
                baslama_pozisyonu=i,
                bitis_pozisyonu=i,
                aciklama=f"Tefhim: {token.temel_harf}"
            ))
    
    return kurallar


def detect_lam_allah(tokens: List[HarfToken]) -> List[KuralEslesme]:
    """
    Lam-Allah (لالله): "Allah" lafzında Lam'ın tefhimi
    "Allah"'ta Lam tefhim (derinleştirilir).
    """
    kurallar = []
    
    # "Allah" = ا + ل + ل + ه
    for i in range(len(tokens) - 3):
        if (tokens[i].temel_harf == 'ا' and
            tokens[i+1].temel_harf == 'ل' and
            tokens[i+2].temel_harf == 'ل' and
            tokens[i+3].temel_harf == 'ه'):
            
            kurallar.append(KuralEslesme(
                kural_adi="lam_allah",
                baslama_pozisyonu=i + 1,
                bitis_pozisyonu=i + 2,
                aciklama="Lam-Allah: Allah lafzında Lam tefhimi"
            ))
    
    return kurallar


def detect_all_tajweed(tokens: List[HarfToken]) -> List[KuralEslesme]:
    """Tüm tecvid kurallarını tespit et"""
    all_rules = []
    
    all_rules.extend(detect_gunna(tokens))
    all_rules.extend(detect_ihfa(tokens))
    all_rules.extend(detect_qalqala(tokens))
    all_rules.extend(detect_med(tokens))
    all_rules.extend(detect_tefhim(tokens))
    all_rules.extend(detect_lam_allah(tokens))
    
    # Pozisyona göre sırala
    all_rules.sort(key=lambda r: (r.baslama_pozisyonu, r.bitis_pozisyonu))
    
    return all_rules


def test_with_text(text: str):
    """Metni test et"""
    tokens = tokenize_arabic(text)
    rules = detect_all_tajweed(tokens)
    
    print(f"Metin: {text}")
    print(f"Token sayısı: {len(tokens)}")
    print(f"\nTokenler:")
    for i, token in enumerate(tokens):
        print(f"  {i:2d}. {token}")
    
    if rules:
        print(f"\nTespit edilen kurallar ({len(rules)}):")
        for rule in rules:
            print(f"  - {rule.aciklama} (pozisyon {rule.baslama_pozisyonu}-{rule.bitis_pozisyonu})")
    else:
        print("\nHiçbir tecvid kuralı tespit edilmedi.")


if __name__ == "__main__":
    # Test 1: Fatiha 1. ayet
    print("=" * 60)
    print("TEST 1: Fatiha 1. ayet")
    print("=" * 60)
    test_with_text("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ")
    
    # Test 2: Ihlas Sure'si
    print("\n" + "=" * 60)
    print("TEST 2: İhlas Suresi (Sure 112)")
    print("=" * 60)
    test_with_text("قُلْ هُوَ اللَّهُ أَحَدٌ")
