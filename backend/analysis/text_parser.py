"""
text_parser.py — Arapça Metin Tokenizer
Arapça metni harflere, harekelerine, şeddelerine ayrıştırır.
"""
from dataclasses import dataclass
from typing import List, Optional
import re


@dataclass
class HarfToken:
    """Arapça harf + diakritikal bilgileri"""
    temel_harf: str        # ب (harf kendisi)
    hareke: Optional[str]  # ِ (kesra), َ (fatha) vb.
    sedde: bool            # Şedde var mı? (ّ)
    tenvin: Optional[str]  # ٌ (fathatan), ٍ (kasratan), ٌ (dammatan)
    sukun: bool            # Sukun var mı? (ْ)
    maddah: bool           # Maddah var mı? (ء)
    pozisyon: int          # Metin içindeki pozisyon
    
    def __repr__(self) -> str:
        parts = [self.temel_harf]
        if self.hareke:
            parts.append(f"[{self.hareke}]")
        if self.sedde:
            parts.append("[ّ]")
        if self.sukun:
            parts.append("[ْ]")
        if self.maddah:
            parts.append("[ء]")
        if self.tenvin:
            parts.append(f"[{self.tenvin}]")
        return "".join(parts)


# Unicode Arapça Bölgeleri
ARABIC_LETTERS = {
    '\u0621': 'ء',  # Hemza
    '\u0622': 'آ',  # Elif with Maddah
    '\u0623': 'أ',  # Elif with Hamza
    '\u0624': 'ؤ',  # Vav with Hamza
    '\u0625': 'إ',  # Elif with Hamza below
    '\u0626': 'ئ',  # Ya with Hamza
    '\u0627': 'ا',  # Elif (Med)
    '\u0628': 'ب',  # Be
    '\u0629': 'ة',  # Ta Marbuta
    '\u062a': 'ت',  # Te
    '\u062b': 'ث',  # Se
    '\u062c': 'ج',  # Cim
    '\u062d': 'ح',  # Ha
    '\u062e': 'خ',  # Kha (Ha uvular)
    '\u062f': 'د',  # Dal
    '\u0630': 'ذ',  # Zal
    '\u0631': 'ر',  # Ra
    '\u0632': 'ز',  # Zay
    '\u0633': 'س',  # Sin
    '\u0634': 'ش',  # Shin
    '\u0635': 'ص',  # Sad
    '\u0636': 'ض',  # Dad
    '\u0637': 'ط',  # Ta (velaric)
    '\u0638': 'ظ',  # Za (velaric)
    '\u0639': 'ع',  # Ayn
    '\u063a': 'غ',  # Gayn
    '\u0641': 'ف',  # Fe
    '\u0642': 'ق',  # Qaf
    '\u0643': 'ك',  # Kef
    '\u0644': 'ل',  # Lam
    '\u0645': 'م',  # Mim
    '\u0646': 'ن',  # Nun
    '\u0647': 'ه',  # He
    '\u0648': 'و',  # Vav (Glide)
    '\u064a': 'ي',  # Ya (Glide)
}

# Diakritikal İşaretler (Harekeler)
HAREKE = {
    '\u064b': 'ٌ',  # Fathatan (dammatan)
    '\u064c': 'ٌ',  # Dammatan
    '\u064d': 'ٍ',  # Kasratan
    '\u064e': 'َ',  # Fatha (a)
    '\u064f': 'ُ',  # Damma (u)
    '\u0650': 'ِ',  # Kesra (i)
    '\u0651': 'ّ',  # Şedde (doubling)
    '\u0652': 'ْ',  # Sukun (silence)
    '\u0653': 'ٓ',  # Maddah
    '\u0654': 'ٔ',  # Hamza above
    '\u0655': 'ٕ',  # Hamza below
    '\u0656': 'ٖ',  # Subscript Alef
    '\u0657': 'ٗ',  # Inverted Damma
    '\u0658': '٘',  # Mark Noon
}

# Tenvinler (Nunation)
TENVIN = {
    '\u064b': 'ٌ',  # Fathatan
    '\u064c': 'ٌ',  # Dammatan  
    '\u064d': 'ٍ',  # Kasratan
}


def is_arabic_letter(char: str) -> bool:
    """Karakter Arapça harf mi?"""
    return char in ARABIC_LETTERS


def is_hareke(char: str) -> bool:
    """Karakter hareke (diakritikal) mi?"""
    return char in HAREKE


def is_tenvin(char: str) -> bool:
    """Karakter tenvin (nunation) mi?"""
    return char in TENVIN


def normalize_arabic_text(text: str) -> str:
    """
    Arapça metni normalize et.
    - Hamza varyantlarını temiz et
    - Boşlukları standartlaştır
    """
    # Hamza varyantlarını normalize et
    text = text.replace('أ', 'ا')  # Elif with Hamza → Elif
    text = text.replace('إ', 'ا')  # Elif with Hamza below → Elif
    text = text.replace('آ', 'ا')  # Elif with Maddah → Elif
    text = text.replace('ؤ', 'و')  # Vav with Hamza → Vav
    text = text.replace('ئ', 'ي')  # Ya with Hamza → Ya
    
    # Boşlukları standartlaştır
    text = re.sub(r'\s+', ' ', text).strip()
    
    return text


def tokenize_arabic(text: str) -> List[HarfToken]:
    """
    Arapça metni HarfToken'lara ayır.
    
    Her HarfToken bir harfi ve ona ait hareke/şedde/sukun vb. içerir.
    """
    text = normalize_arabic_text(text)
    tokens = []
    i = 0
    pozisyon = 0
    
    while i < len(text):
        char = text[i]
        
        # Boşluk ve latin karakterleri atla
        if char == ' ' or ord(char) < 1536:  # Unicode 1536 = Arapça başlangıcı
            i += 1
            pozisyon += 1
            continue
        
        # Arapça harf bulundu
        if is_arabic_letter(char):
            temel_harf = char
            hareke = None
            sedde = False
            tenvin = None
            sukun = False
            maddah = False
            
            # Sonraki karakterleri kontrol et (hareke, şedde, sukun vb.)
            i += 1
            while i < len(text):
                next_char = text[i]
                
                if next_char == '\u0651':  # Şedde
                    sedde = True
                    i += 1
                elif next_char == '\u0652':  # Sukun
                    sukun = True
                    i += 1
                elif next_char in HAREKE:
                    if next_char in TENVIN:
                        tenvin = next_char
                    else:
                        hareke = next_char
                    i += 1
                elif next_char == '\u0653':  # Maddah
                    maddah = True
                    i += 1
                else:
                    break
            
            token = HarfToken(
                temel_harf=temel_harf,
                hareke=hareke,
                sedde=sedde,
                tenvin=tenvin,
                sukun=sukun,
                maddah=maddah,
                pozisyon=pozisyon
            )
            tokens.append(token)
            pozisyon += 1
        else:
            i += 1
            pozisyon += 1
    
    return tokens


def text_to_plain_harfler(text: str) -> str:
    """Metni yalnızca harflere indir (hareke/şedde yok)"""
    tokens = tokenize_arabic(text)
    return "".join(t.temel_harf for t in tokens)


def tokenize_with_context(text: str) -> List[HarfToken]:
    """
    Tokenize et ve bağlam bilgisi ekle.
    (Gelecekte: Sure/Ayet numarası, sıra, vb.)
    """
    return tokenize_arabic(text)


# Test
if __name__ == "__main__":
    # Fatiha 1. ayet
    text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ"
    
    tokens = tokenize_arabic(text)
    print(f"Metin: {text}")
    print(f"Token sayısı: {len(tokens)}")
    print("\nTokenler:")
    for i, token in enumerate(tokens, 1):
        print(f"  {i:2d}. {token}")
    
    print(f"\nSade harfler: {text_to_plain_harfler(text)}")
