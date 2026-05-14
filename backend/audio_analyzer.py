"""
GuardianAAM — Audio Feature Extraction Module
Kuran-ı Kerim ses analizi için öznitelik çıkarma fonksiyonları.
"""

import base64
import math
import numpy as np
import librosa
import umap as umap_lib
from loguru import logger
from typing import Tuple, List, Dict
from scipy.ndimage import zoom


# ─── YARDIMCI ────────────────────────────────────────────────────────────────

def _normalize(arr: np.ndarray) -> np.ndarray:
    """0-1 normalizasyon; NaN değerleri korur, sabit dizilerde 0 döner."""
    arr = np.array(arr, dtype=float)
    valid = arr[~np.isnan(arr)]
    if len(valid) == 0:
        return arr
    mn, mx = valid.min(), valid.max()
    if mx == mn:
        return np.where(np.isnan(arr), np.nan, 0.0)
    return (arr - mn) / (mx - mn)


def _trim_or_pad(arr: np.ndarray, n: int) -> np.ndarray:
    """Diziyi n uzunluğuna kırp veya NaN ile genişlet."""
    arr = np.array(arr, dtype=float)
    if len(arr) >= n:
        return arr[:n]
    return np.concatenate([arr, np.full(n - len(arr), np.nan)])


# ─── TEMEL FONKSİYONLAR ───────────────────────────────────────────────────────

def load_audio(path: str, sr: int = 22050) -> Tuple[np.ndarray, int]:
    """
    Ses dosyasını yükle ve pre-emphasis filtresi uygula.

    Pre-emphasis: y[t] = y[t] - 0.97 * y[t-1]
    Yüksek frekanslı bileşenleri güçlendirir, formant ve pitch
    tespitinin doğruluğunu artırır.
    """
    y, loaded_sr = librosa.load(path, sr=sr, mono=True)
    # Pre-emphasis filtresi (coef=0.97)
    y_emph = np.append(y[0], y[1:] - 0.97 * y[:-1])
    logger.debug(f"Yüklendi: {path} | sr={loaded_sr} | n_samples={len(y_emph)}")
    return y_emph, loaded_sr


def extract_pitch(
    y: np.ndarray,
    sr: int,
    hop: int = 512
) -> Tuple[np.ndarray, np.ndarray]:
    """
    pyin algoritmasıyla temel frekans (F0) çıkar.

    pyin, YIN'in olasılıksal versiyonudur:
    - Sessiz bölgelerde NaN döner (yanlış pozitif az)
    - Voicing kararı Viterbi HMM ile verilir
    - Asla yin() kullanma: yin(), pyin()'den düşük doğrulukludur

    Returns:
        f0: Hz cinsinden F0 dizisi (NaN = sessiz)
        voiced_flag: boolean array (True = sesli frame)
    """
    f0, voiced_flag, voiced_prob = librosa.pyin(
        y,
        fmin=librosa.note_to_hz('C2'),   # ~65 Hz
        fmax=librosa.note_to_hz('G4'),   # ~392 Hz
        sr=sr,
        hop_length=hop,
        fill_na=float('nan')
    )
    n_voiced = int(voiced_flag.sum())
    logger.debug(f"Pitch: {n_voiced} sesli frame / {len(f0)} toplam")
    return f0, voiced_flag


def extract_mfcc(
    y: np.ndarray,
    sr: int,
    n_fft: int = 2048,
    hop: int = 512,
    n_mfcc: int = 13
) -> np.ndarray:
    """
    13 MFCC + delta + delta-delta = 39 boyutlu öznitelik matrisi.

    - MFCC: tımbre'ı temsil eden mel-frekans kepstrum katsayıları
    - Delta: zaman ekseni boyunca birinci türev (dinamik öznitelik)
    - Delta2: ikinci türev (ivme bilgisi)

    Returns:
        shape (39, n_frames)
    """
    mfcc = librosa.feature.mfcc(
        y=y, sr=sr, n_mfcc=n_mfcc, n_fft=n_fft, hop_length=hop
    )
    delta = librosa.feature.delta(mfcc)
    delta2 = librosa.feature.delta(mfcc, order=2)
    result = np.vstack([mfcc, delta, delta2])  # (39, n_frames)
    logger.debug(f"MFCC shape: {result.shape}")
    return result


def extract_spectral(
    y: np.ndarray,
    sr: int,
    n_fft: int = 2048,
    hop: int = 512
) -> Dict[str, np.ndarray]:
    """
    Spektral öznitelikler: centroid, flux, zcr, rolloff, rms.

    - Centroid: spektrumun ağırlık merkezi (parlaklık göstergesi)
    - Flux: ardışık frame'ler arası spektral değişim
    - ZCR: işaret geçiş oranı (fısıltı/sessizlik tespiti için)
    - Rolloff: toplam enerjinin %85'ini içeren frekans eşiği
    - RMS: çerçeve bazlı ses şiddeti (enerji)
    """
    S = np.abs(librosa.stft(y, n_fft=n_fft, hop_length=hop))
    S_power_db = librosa.power_to_db(S ** 2)

    return {
        "centroid": librosa.feature.spectral_centroid(S=S, sr=sr)[0],
        "flux": librosa.onset.onset_strength(
            S=S_power_db, sr=sr, hop_length=hop
        ),
        "zcr": librosa.feature.zero_crossing_rate(y, hop_length=hop)[0],
        "rolloff": librosa.feature.spectral_rolloff(
            S=S, sr=sr, roll_percent=0.85
        )[0],
        "rms": librosa.feature.rms(y=y, hop_length=hop)[0],
    }


def extract_spectrogram(
    y: np.ndarray,
    sr: int,
    n_fft: int = 2048,
    hop: int = 512,
    max_freq_hz: float = 4000.0,
    out_width: int = 1200,
    out_height: int = 400,
) -> Dict:
    """
    Render-ready spektrogram — base64 uint8 pixel buffer.

    Python'da spektrogramı (n_frames, freq_bins) → (out_height, out_width) pixel
    matrisine downsample eder ve base64 encode eder. Kotlin'de doğrudan çizilebilir.

    Args:
        y: Audio sample array
        sr: Sample rate (22050)
        n_fft: FFT window size (2048)
        hop: Hop length (512)
        max_freq_hz: Maksimum frekans sınırı (4000 for Quran)
        out_width: Çıktı pixel genişliği (1200)
        out_height: Çıktı pixel yüksekliği (400)

    Returns:
        Dictionary with base64 pixels + metadata
    """
    S = np.abs(librosa.stft(y, n_fft=n_fft, hop_length=hop))  # (n_bins, n_frames)
    S_db = librosa.amplitude_to_db(S, ref=np.max)

    # Frekans trimi: 0 – max_freq_hz
    freqs = librosa.fft_frequencies(sr=sr, n_fft=n_fft)  # (n_bins,)
    freq_mask = freqs <= max_freq_hz
    S_trimmed = S_db[freq_mask, :]  # (freq_bins, n_frames)
    freq_bins_actual = S_trimmed.shape[0]
    n_frames_actual = S_trimmed.shape[1]

    # dB normalize: -80 dB → 0, 0 dB → 255
    db_floor = -80.0
    db_ceil = 0.0
    S_norm = np.clip((S_trimmed - db_floor) / (db_ceil - db_floor), 0.0, 1.0)

    # Resize to (out_height, out_width) using bilinear interpolation
    zoom_y = out_height / freq_bins_actual if freq_bins_actual > 0 else 1.0
    zoom_x = out_width / n_frames_actual if n_frames_actual > 0 else 1.0
    S_resized = zoom(S_norm, (zoom_y, zoom_x), order=1, prefilter=False)
    S_resized = np.clip(S_resized, 0.0, 1.0)

    # Flip vertical so row 0 = highest freq (visual convention)
    S_flipped = np.flipud(S_resized)

    # Convert to uint8 and base64-encode
    pixels_u8 = (S_flipped * 255).astype(np.uint8)
    pixels_b64 = base64.b64encode(pixels_u8.tobytes()).decode('ascii')

    duration_sec = float(librosa.get_duration(y=y, sr=sr))

    logger.debug(f"Spectrogram: {out_height}x{out_width} pixels, {len(pixels_b64)} chars base64")

    return {
        "pixels": pixels_b64,
        "width": out_width,
        "height": out_height,
        "time_bins": n_frames_actual,
        "freq_bins": freq_bins_actual,
        "freq_max_hz": float(freqs[freq_mask][-1] if freq_mask.any() else freqs[-1]),
        "freq_min_hz": 0.0,
        "duration_sec": round(duration_sec, 3),
        "db_min": db_floor,
        "db_max": db_ceil,
    }


def extract_formants(
    y: np.ndarray,
    sr: int,
    n_fft: int = 2048,
    hop: int = 512
) -> Tuple[List[float], List[float]]:
    """
    LPC analizi ile vokal formantları (F1, F2) çıkar.

    LPC, vokal kanat filtresi olarak modeller: ses üretim sistemi
    AR(p) süreciyle temsil edilir. Kökler → rezonans frekansları.

    Referans formant değerleri:
        Elif (a): F1~750Hz, F2~1200Hz
        Vav  (u): F1~300Hz, F2~600Hz
        Ye   (i): F1~300Hz, F2~2200Hz

    Returns:
        f1_list, f2_list: her frame için Hz cinsinden formant değerleri
    """
    order = int(2 * (sr / 1000)) + 2  # sr=22050 → order=46
    f1_list: List[float] = []
    f2_list: List[float] = []

    for i in range(0, len(y) - n_fft, hop):
        frame = y[i: i + n_fft] * np.hamming(n_fft)
        try:
            a = librosa.lpc(frame, order=order)
            roots = np.roots(a)
            # Sadece üst yarı düzlem (pozitif frekanslar)
            roots = roots[np.imag(roots) >= 0]
            angles = np.arctan2(np.imag(roots), np.real(roots))
            freqs = sorted(angles * (sr / (2 * np.pi)))
            freqs = [f for f in freqs if 50 < f < 5000]
            f1_list.append(freqs[0] if len(freqs) > 0 else float('nan'))
            f2_list.append(freqs[1] if len(freqs) > 1 else float('nan'))
        except Exception:
            f1_list.append(float('nan'))
            f2_list.append(float('nan'))

    return f1_list, f2_list


def to_3d_umap(features_matrix: np.ndarray) -> np.ndarray:
    """
    Yüksek boyutlu öznitelik matrisini 3D uzaya indirge.

    UMAP (Uniform Manifold Approximation and Projection):
    - Cosine metriği: tımbre benzerliği için uygun
    - n_neighbors=15: yerel yapı dengesi
    - min_dist=0.1: kümelerin sıkışıklık derecesi

    Args:
        features_matrix: shape (n_frames, n_features)

    Returns:
        shape (n_frames, 3)
    """
    # NaN → 0, UMAP NaN kabul etmez
    clean = np.nan_to_num(features_matrix, nan=0.0, posinf=0.0, neginf=0.0)
    n_samples = len(clean)
    # n_neighbors, n_samples'tan küçük olmalı
    n_neighbors = min(15, max(2, n_samples - 1))

    reducer = umap_lib.UMAP(
        n_components=3,
        n_neighbors=n_neighbors,
        min_dist=0.1,
        metric='cosine',
        random_state=42,
        low_memory=False,
    )
    coords = reducer.fit_transform(clean)
    logger.debug(f"UMAP output shape: {coords.shape}")
    return coords


def assign_color(f0: float, energy_norm: float) -> str:
    """
    F0 ve normalize edilmiş enerji değerine göre hex renk döner.

    Renk paleti:
        #E85D24 — yüksek enerjili sesli (Kuran vurgusu)
        #EF9F27 — orta enerjili sesli
        #3B8BD4 — düşük enerjili sesli
        #1D9E75 — çok düşük enerjili / derin sesli
        #4A4A4A — sessizlik / NaN pitch
    """
    if f0 is None or math.isnan(f0):
        return "#4A4A4A"  # sessizlik
    if energy_norm > 0.7:
        return "#E85D24"  # high_energy_voiced
    elif energy_norm > 0.4:
        return "#EF9F27"  # mid_energy_voiced
    elif energy_norm > 0.2:
        return "#3B8BD4"  # low_energy_voiced
    else:
        return "#1D9E75"  # deep_voiced


def segment_by_silence(
    y: np.ndarray,
    sr: int,
    threshold_db: float = -40
) -> List[Dict]:
    """
    RMS enerjisine göre sessizlik aralıklarını tespit et ve
    ses segmentlerini (ayetleri) döndür.

    Fatiha Suresi için beklenen: >= 6 segment (7 ayet).
    Minimum segment süresi: 0.2 saniye (çok kısa bölümleri eler).

    Returns:
        [{"start": float, "end": float}, ...]
    """
    rms = librosa.feature.rms(y=y)[0]
    rms_db = librosa.power_to_db(rms ** 2, ref=np.max)
    silence_frames = rms_db < threshold_db

    segments: List[Dict] = []
    in_segment = False
    start = 0.0

    for i, is_silent in enumerate(silence_frames):
        t = float(librosa.frames_to_time(i, sr=sr))
        if not is_silent and not in_segment:
            start = t
            in_segment = True
        elif is_silent and in_segment:
            if t - start > 0.2:
                segments.append({"start": start, "end": t})
            in_segment = False

    # Dosya sonunda açık segment varsa kapat
    if in_segment:
        t_end = float(librosa.frames_to_time(len(silence_frames), sr=sr))
        if t_end - start > 0.2:
            segments.append({"start": start, "end": t_end})

    logger.debug(f"Tespit edilen segment sayısı: {len(segments)}")
    return segments


# ─── DIŞA AKTAR ───────────────────────────────────────────────────────────────

__all__ = [
    "load_audio",
    "extract_pitch",
    "extract_mfcc",
    "extract_spectral",
    "extract_spectrogram",
    "extract_formants",
    "to_3d_umap",
    "assign_color",
    "segment_by_silence",
    "_normalize",
    "_trim_or_pad",
]
