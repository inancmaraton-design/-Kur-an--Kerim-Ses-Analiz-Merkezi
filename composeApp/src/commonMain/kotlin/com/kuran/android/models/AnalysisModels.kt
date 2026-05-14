package com.kuran.android.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// YENİ MODELLER — Python v6.0 Akustik Fonetik motoru çıktısı
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bir 3D nokta: harf doğruluk skoru + fonetik bilgi içeriyor.
 * X = zaman normalize, Y = F2 formant normalize, Z = spektral merkez normalize
 * color = harfin doğruluk skoru rengi (Python tarafından hesaplanır)
 */
@Serializable
data class AnalysisPoint(
    val x: Double,
    val y: Double,
    val z: Double,
    val t: Double,
    val color: String,
    val skor: Double,
    val harf: String? = null,
    val harf_isim: String? = null,
    val kelime: String? = null
)

/** Segment içindeki tek kelime */
@Serializable
data class SegmentWord(
    val word: String,
    val start: Double,
    val end: Double
)

/** Zaman aralığı segment (transkript + kelime listesi) */
@Serializable
data class AnalysisSegment(
    val start: Double,
    val end: Double,
    val text: String,
    val words: List<SegmentWord> = emptyList()
)

/** Bir harf için istatistik özeti */
@Serializable
data class HarfIstatistik(
    val sayi: Int,
    val ortalama_skor: Double
)

/** Bir harfin fonetik kimliği */
@Serializable
data class HarfBilgi(
    val isim: String,
    val makhrej: String,
    val renk: String,
    val sifat: List<String> = emptyList()
)

/** 
 * Metin tabanlı istatistiksel metrikler (Pipeline B).
 * Sadece metnin matematiksel yapısından çıkarılır (ses bağımsız).
 */
@Serializable
data class MetinMetrikleri(
    val f1_ortalama: Double = 0.0,
    val f2_ortalama: Double = 0.0,
    val f2_entropi: Double = 0.0,
    val f1_gecis_yumusakligi: Double = 0.0,
    val f2_gecis_yumusakligi: Double = 0.0,
    val ritmik_duzgunluk_npvi: Double = 0.0,
    val spektral_merkez_ortalama: Double = 0.0,
    val nazal_oran: Double = 0.0,
    val tefhim_orani: Double = 0.0,
    val harf_sayisi: Int = 0,
    val harf_istatistikleri: Map<String, Int> = emptyMap()
)

/**
 * Python backend'in döndürdüğü ana analiz sonucu.
 * PythonBridge'den gelen "data" objesi bu tipe deserialize edilir.
 */
@Serializable
data class AnalysisResult(
    val sure: Double,
    val points: List<AnalysisPoint>,
    val segments: List<AnalysisSegment>,
    val genel_skor: Double,
    val harf_dagilimi: Map<String, HarfIstatistik> = emptyMap(),
    val harf_veritabani: Map<String, HarfBilgi> = emptyMap(),
    val karsilastirma: KarsilastirmaResult? = null,
    val metin_metrikleri: MetinMetrikleri? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// ESKİ MODELLER — Geriye dönük uyumluluk (loadRecording / ComparisonViewModel)
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class AnalysisResponse(
    val metadata: Metadata,
    @SerialName("umap_3d") val umap3d: List<UmapPoint>,
    val segments: List<Segment>,
    val karsilastirma: KarsilastirmaResult? = null,
    val metin_metrikleri: MetinMetrikleri? = null
)

@Serializable
data class Metadata(
    val surah: String,
    @SerialName("duration_sec") val durationSec: Double,
    @SerialName("sample_rate") val sampleRate: Int,
    @SerialName("n_frames") val nFrames: Int,
    @SerialName("n_fft") val nFft: Int,
    @SerialName("hop_length") val hopLength: Int,
    @SerialName("analysis_version") val analysisVersion: String
)

@Serializable
data class UmapPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val frame: Int,
    @SerialName("time_sec") val timeSec: Float,
    val f0: Float? = null,
    val color: String,
    val label: String
)

@Serializable
data class Segment(
    val label: String,
    val start: Double,
    val end: Double,
    @SerialName("frame_start") val frameStart: Int,
    @SerialName("frame_end") val frameEnd: Int
)

// ─────────────────────────────────────────────────────────────────────────────
// 3D RENDER MODELİ — UI tarafı için normalize edilmiş nokta
// ─────────────────────────────────────────────────────────────────────────────

data class AudioPoint3D(
    val x: Float,
    val y: Float,
    val z: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val timeSec: Float,
    val f0: Float? = null,
    val label: String,
    // Yeni alanlar — fonetik bilgi
    val skor: Float = 0f,
    val harf: String? = null,
    val harf_isim: String? = null,
    val kelime: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// SPECTROGRAM
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class SpectrogramData(
    val pixels: String,
    val width: Int,
    val height: Int,
    @SerialName("time_bins") val timeBins: Int,
    @SerialName("freq_bins") val freqBins: Int,
    @SerialName("freq_max_hz") val freqMaxHz: Float,
    @SerialName("freq_min_hz") val freqMinHz: Float,
    @SerialName("duration_sec") val durationSec: Float,
    @SerialName("db_min") val dbMin: Float,
    @SerialName("db_max") val dbMax: Float
)

@Serializable
data class SpectrogramResponse(
    val ok: Boolean,
    val data: SpectrogramData? = null,
    val error: String? = null
)

// ─────────────────────────────────────────────────────────────────────────────
// KAYIT GİRİŞİ
// ─────────────────────────────────────────────────────────────────────────────

data class RecordingEntry(
    val name: String,
    val displayName: String,
    val wavFile: File,
    val jsonFile: File,
    val dateStr: String,
    val fileSizeKb: Long
)

// ─────────────────────────────────────────────────────────────────────────────
// SURE SEÇİMİ — Dosya tarama modelleri
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class DosyaBilgi(
    val dosya_adi: String,
    val tam_yol: String,
    val klasor: String,
    val sure_no: Int? = null,
    val sure_isim: String? = null,
    val sure_isim_ar: String? = null,
    val ayet_no: Int? = null,
    val kari: String? = null,
    val format: String = "MP3",
    val boyut_mb: Double = 0.0
)

@Serializable
data class SurelerTaraResponse(
    val dosyalar: List<DosyaBilgi> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// TECVİD KARŞILAŞTIRMA — Sonuç modelleri
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class KuralSonucDetay(
    val skor: Double,
    val durum: String  // "dogru" | "kismi" | "yanlis" | "tespit_edilemedi"
)

@Serializable
data class KuralSonuc(
    val harf: String,
    val kural: String,
    val aciklama: String,
    val sonuc: KuralSonucDetay,
    val agirlik: Double
)

@Serializable
data class KarsilastirmaResult(
    val sure_isim: String = "",
    val sure_isim_ar: String = "",
    val ayet_no: Int? = null,
    val ayet_metni: String = "",
    val genel_skor: Double = 0.0,
    val dogru_sayisi: Int = 0,
    val yanlis_sayisi: Int = 0,
    val kismi_sayisi: Int = 0,
    val kurallar: List<KuralSonuc> = emptyList(),
    val iyi_yapilan: List<String> = emptyList(),
    val dikkat_gereken: List<String> = emptyList()
)
