package com.kuran.android.viewmodel

import com.kuran.android.audio.AudioRecorder
import com.kuran.android.audio.AudioPlayer
import com.kuran.android.models.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import javax.sound.sampled.AudioSystem
import kotlin.math.abs


// ─────────────────────────────────────────────────────────────────────────────
// UI State — Analiz başarısı artık fonetik bilgiyi de taşıyor
// ─────────────────────────────────────────────────────────────────────────────

sealed class AnalysisUIState {
    object Idle : AnalysisUIState()
    data class Recording(val seconds: Int) : AnalysisUIState()
    object Loading : AnalysisUIState()
    data class Success(
        val points: List<AudioPoint3D>,
        val segments: List<Segment>,
        val totalDuration: Float,
        val surahName: String = "",
        val wavPath: String = "",
        // ── Fonetik bilgi ──
        val genelSkor: Double = 0.0,
        val harfDagilimi: Map<String, HarfIstatistik> = emptyMap(),
        val harfVeritabani: Map<String, HarfBilgi> = emptyMap(),
        // Gerçek AnalysisResult (SegmentWord içeren segment'lar için)
        val analysisResult: AnalysisResult? = null,
        val metin_metrikleri: MetinMetrikleri? = null
    ) : AnalysisUIState()
    data class Error(val message: String) : AnalysisUIState()
}

// Şu an okunan harfe ait anlık state
data class AktifHarfState(
    val harf: String? = null,
    val harf_isim: String? = null,
    val kelime: String? = null,
    val makhrej: String? = null,
    val skor: Double = 0.0
)

sealed class SpectrogramUIState {
    object Idle : SpectrogramUIState()
    object Loading : SpectrogramUIState()
    data class Success(val data: SpectrogramData) : SpectrogramUIState()
    data class Error(val message: String) : SpectrogramUIState()
}

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class AnalysisViewModel(
    private val recorder: AudioRecorder,
    private val player: AudioPlayer,
    private val analyzeAudio: suspend (ByteArray, String) -> AnalysisResponse,
    private val analyzeSpectrogram: suspend (ByteArray) -> SpectrogramData,
    private val getSurahs: (() -> List<SurahInfo>)? = null,
    private val getAyahFile: ((SurahInfo, Int) -> File?)? = null
) {
    // Metin analiz callback — Main.kt tarafından set edilir
    var metinAnaliz: (suspend (String) -> MetinMetrikleri)? = null

    private val _state = MutableStateFlow<AnalysisUIState>(AnalysisUIState.Idle)
    val state: StateFlow<AnalysisUIState> = _state

    private val _playbackTime = MutableStateFlow(0f)
    val playbackTime: StateFlow<Float> = _playbackTime

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _availableRecordings = MutableStateFlow<List<RecordingEntry>>(emptyList())
    val availableRecordings: StateFlow<List<RecordingEntry>> = _availableRecordings

    private val _spectrogramState = MutableStateFlow<SpectrogramUIState>(SpectrogramUIState.Idle)
    val spectrogramState: StateFlow<SpectrogramUIState> = _spectrogramState

    /** Şu an okunan harfe ait bilgi — oynatma döngüsünde güncellenir */
    private val _aktifHarf = MutableStateFlow(AktifHarfState())
    val aktifHarf: StateFlow<AktifHarfState> = _aktifHarf

    // ── Sure/Dosya Secimi ──
    private val _dosyaListesi = MutableStateFlow<List<DosyaBilgi>>(emptyList())
    val dosyaListesi: StateFlow<List<DosyaBilgi>> = _dosyaListesi

    private val _seciliDosya = MutableStateFlow<DosyaBilgi?>(null)
    val seciliDosya: StateFlow<DosyaBilgi?> = _seciliDosya

    private val _karsilastirma = MutableStateFlow<KarsilastirmaResult?>(null)
    val karsilastirma: StateFlow<KarsilastirmaResult?> = _karsilastirma

    private val _surelerYukleniyor = MutableStateFlow(false)
    val surelerYukleniyor: StateFlow<Boolean> = _surelerYukleniyor

    val saveDir: File = File(
        System.getProperty("user.home") + "/Documents/KuranAnaliz"
    ).also { it.mkdirs() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timerJob: Job? = null
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    @Volatile
    private var isPlayerPaused = false

    init {
        refreshRecordings()
    }

    // ──────────────────────────── WAV Süre ────────────────────────────

    private fun getWavDuration(bytes: ByteArray, fallbackMs: Float): Float {
        return try {
            val bis = ByteArrayInputStream(bytes)
            val ais = AudioSystem.getAudioInputStream(bis)
            try {
                (ais.frameLength.toFloat() / ais.format.sampleRate) * 1000f
            } finally {
                ais.close()
            }
        } catch (e: Exception) {
            println("WAV duration okunamadi: ${e.message}")
            fallbackMs
        }
    }

    // ──────────────────────────── Aktif Harf Güncelleme ────────────────────────────

    /**
     * Oynatma zamanına (timeSec) en yakın noktayı bul → _aktifHarf güncelle.
     * Bu fonksiyon her frame'de çağrılır.
     */
    private fun updateAktifHarf(timeSec: Float, points: List<AudioPoint3D>, harfVeritabani: Map<String, HarfBilgi>) {
        if (points.isEmpty()) return
        val closest = points.minByOrNull { abs(it.timeSec - timeSec) } ?: return
        val bilgi = closest.harf?.let { harfVeritabani[it] }
        _aktifHarf.value = AktifHarfState(
            harf       = closest.harf,
            harf_isim  = closest.harf_isim,
            kelime     = closest.kelime,
            makhrej    = bilgi?.makhrej,
            skor       = closest.skor.toDouble()
        )
    }

    // ──────────────────────────── Kayıt Yöneticisi ────────────────────────────

    fun refreshRecordings() {
        scope.launch(Dispatchers.IO) {
            val wavFiles = saveDir.listFiles { f -> f.extension.equals("wav", true) }
                ?: return@launch
            val entries = wavFiles.mapNotNull { wav ->
                val jsonFile = File(saveDir, wav.nameWithoutExtension + ".json")
                if (jsonFile.exists()) {
                    val date = SimpleDateFormat("dd.MM.yyyy HH:mm").format(Date(wav.lastModified()))
                    val displayName = wav.nameWithoutExtension
                        .replace("_", " ")
                        .split(" ")
                        .dropLast(2)
                        .joinToString(" ")
                        .ifBlank { wav.nameWithoutExtension }
                    RecordingEntry(
                        name = wav.nameWithoutExtension,
                        displayName = displayName,
                        wavFile = wav,
                        jsonFile = jsonFile,
                        dateStr = date,
                        fileSizeKb = wav.length() / 1024
                    )
                } else null
            }.sortedByDescending { it.wavFile.lastModified() }

            withContext(Dispatchers.Main) {
                _availableRecordings.value = entries
            }
        }
    }

    fun loadRecording(entry: RecordingEntry) {
        player.stop()
        isPlayerPaused = false
        _isPlaying.value = false
        _playbackTime.value = 0f
        _state.value = AnalysisUIState.Loading
        scope.launch(Dispatchers.IO) {
            try {
                val jsonText = entry.jsonFile.readText()

                // Önce yeni formatı dene (AnalysisResult), başarısız olursa eski (AnalysisResponse)
                val (points, segments, genelSkor, harfDagilimi, harfVeritabani, analysisResult) =
                    tryParseNewFormat(jsonText) ?: parseOldFormat(jsonText)

                val wavBytes = entry.wavFile.readBytes()
                val wavDurationSec = getWavDuration(wavBytes, 0f) / 1000f
                val pointsMaxTime = points.maxOfOrNull { it.timeSec } ?: 0f
                val effectiveDuration = if (wavDurationSec > 0.1f) wavDurationSec
                                        else pointsMaxTime.coerceAtLeast(1f)

                withContext(Dispatchers.Main) {
                    _playbackTime.value = 0f
                    _isPlaying.value = false
                    _state.value = AnalysisUIState.Success(
                        points          = points,
                        segments        = segments,
                        totalDuration   = effectiveDuration,
                        surahName       = entry.displayName.ifBlank { "Kayıt" },
                        wavPath         = entry.wavFile.absolutePath,
                        genelSkor       = genelSkor,
                        harfDagilimi    = harfDagilimi,
                        harfVeritabani  = harfVeritabani,
                        analysisResult  = analysisResult,
                        metin_metrikleri = analysisResult?.metin_metrikleri
                    )
                }
            } catch (e: Exception) {
                println("[loadRecording] HATA: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _state.value = AnalysisUIState.Error("Kayit yuklenemedi: ${e.message}")
                }
            }
        }
    }

    /** Python v6.0 AnalysisResult formatını dene */
    private fun tryParseNewFormat(jsonText: String): ParsedData? {
        return try {
            val result = json.decodeFromString<AnalysisResult>(jsonText)
            val points = result.points.map { it.toAudioPoint3D() }
            ParsedData(
                points         = points,
                segments       = emptyList(),  // eski Segment tipi — UI segmentleri için analysisResult kullan
                genelSkor      = result.genel_skor,
                harfDagilimi   = result.harf_dagilimi,
                harfVeritabani = result.harf_veritabani,
                analysisResult = result
            )
        } catch (e: Exception) {
            null  // eski formata düş
        }
    }

    /** Eski UmapPoint formatını parse et */
    private fun parseOldFormat(jsonText: String): ParsedData {
        val response = json.decodeFromString<AnalysisResponse>(jsonText)
        val points = response.umap3d.map { it.toAudioPoint3D() }
        return ParsedData(
            points         = points,
            segments       = response.segments,
            genelSkor      = 0.0,
            harfDagilimi   = emptyMap(),
            harfVeritabani = emptyMap(),
            analysisResult = null
        )
    }

    private data class ParsedData(
        val points: List<AudioPoint3D>,
        val segments: List<Segment>,
        val genelSkor: Double,
        val harfDagilimi: Map<String, HarfIstatistik>,
        val harfVeritabani: Map<String, HarfBilgi>,
        val analysisResult: AnalysisResult?
    )

    // ──────────────────────────── Kayıt ve Analiz ────────────────────────────

    fun startRecording() {
        if (recorder.isRecording) return
        recorder.startRecording()
        _state.value = AnalysisUIState.Recording(0)
        timerJob = scope.launch {
            var elapsed = 0
            while (isActive && elapsed < 60) {
                delay(1000)
                elapsed++
                _state.value = AnalysisUIState.Recording(elapsed)
            }
            if (isActive) stopAndAnalyze()
        }
    }

    fun stopAndAnalyze() {
        timerJob?.cancel()
        _state.value = AnalysisUIState.Loading
        recorder.stopRecording { bytes ->
            if (bytes != null) {
                val ts = SimpleDateFormat("dd.MM.yyyy_HH.mm.ss").format(Date())
                analyze(bytes, "Canli_Kayit_$ts")
            } else {
                _state.value = AnalysisUIState.Error("Kayit alinamadi")
            }
        }
    }

    fun analyzeSelectedFile(bytes: ByteArray, fileName: String) {
        _state.value = AnalysisUIState.Loading
        val name = fileName.substringBeforeLast(".").ifBlank { "Bilinmeyen" }
        analyze(bytes, name)
    }

    fun analyzeMultipleFiles(files: List<Pair<ByteArray, String>>) {
        if (files.isEmpty()) return
        scope.launch {
            for ((bytes, fileName) in files) {
                _state.value = AnalysisUIState.Loading
                val name = fileName.substringBeforeLast(".").ifBlank { "Bilinmeyen" }

                try {
                    val response = analyzeAudio(bytes, name)
                    val points = response.umap3d.map { it.toAudioPoint3D() }
                    val pointsMaxTimeMs = points.maxOfOrNull { it.timeSec }?.let { it * 1000f } ?: 0f
                    val totalDurationMs = getWavDuration(bytes, pointsMaxTimeMs)

                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                    val baseName = "${name}_${ts}"
                    val wavFile = File(saveDir, "$baseName.wav")
                    wavFile.writeBytes(bytes)

                    val jsonFile = File(saveDir, "$baseName.json")
                    jsonFile.writeText(json.encodeToString(response))

                    println("Kaydedildi: ${wavFile.absolutePath}")
                    refreshRecordings()

                    withContext(Dispatchers.Main) {
                        _playbackTime.value = 0f
                        _isPlaying.value = false
                        _state.value = AnalysisUIState.Success(
                            points        = points,
                            segments      = response.segments,
                            totalDuration = totalDurationMs / 1000f,
                            surahName     = name,
                            wavPath       = wavFile.absolutePath,
                            analysisResult = null,
                            genelSkor      = response.karsilastirma?.genel_skor ?: 0.0,
                            metin_metrikleri = response.metin_metrikleri
                        )
                    }

                    delay(500)
                } catch (e: Exception) {
                    println("Analiz hatasi: ${e.message}")
                    withContext(Dispatchers.Main) {
                        _state.value = AnalysisUIState.Error(e.message ?: "Bilinmeyen hata")
                    }
                    delay(500)
                }
            }
        }
    }

    fun analyzeSurahAyah(surah: SurahInfo, ayahNumber: Int) {
        if (getAyahFile == null) return
        _state.value = AnalysisUIState.Loading
        scope.launch(Dispatchers.IO) {
            try {
                val file = getAyahFile.invoke(surah, ayahNumber)
                if (file == null) {
                    withContext(Dispatchers.Main) {
                        _state.value = AnalysisUIState.Error("Dosya bulunamadı")
                    }
                    return@launch
                }
                val bytes = file.readBytes()
                val name = "${surah.displayName} - ${ayahNumber}. Ayet"
                withContext(Dispatchers.Main) {
                    analyze(bytes, name)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = AnalysisUIState.Error("Dosya okunamadı: ${e.message}")
                }
            }
        }
    }

    fun getAvailableSurahs(): List<SurahInfo> {
        return getSurahs?.invoke() ?: emptyList()
    }

    private fun analyze(bytes: ByteArray, surahName: String) {
        scope.launch {
            try {
                val response = analyzeAudio(bytes, surahName)
                val points = response.umap3d.map { it.toAudioPoint3D() }
                val pointsMaxTimeMs = points.maxOfOrNull { it.timeSec }?.let { it * 1000f } ?: 0f
                val totalDurationMs = getWavDuration(bytes, pointsMaxTimeMs)

                val ts = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                val baseName = "${surahName}_${ts}"
                val wavFile = File(saveDir, "$baseName.wav")
                wavFile.writeBytes(bytes)

                val jsonFile = File(saveDir, "$baseName.json")
                jsonFile.writeText(json.encodeToString(response))

                println("Kaydedildi: ${wavFile.absolutePath}")
                refreshRecordings()

                withContext(Dispatchers.Main) {
                    _playbackTime.value = 0f
                    _isPlaying.value = false
                    _state.value = AnalysisUIState.Success(
                        points        = points,
                        segments      = response.segments,
                        totalDuration = totalDurationMs / 1000f,
                        surahName     = surahName,
                        wavPath       = wavFile.absolutePath,
                        genelSkor     = response.karsilastirma?.genel_skor ?: 0.0,
                        metin_metrikleri = response.metin_metrikleri
                    )
                }
            } catch (e: Exception) {
                println("Analiz hatasi: ${e.message}")
                withContext(Dispatchers.Main) {
                    _state.value = AnalysisUIState.Error(e.message ?: "Bilinmeyen hata")
                }
            }
        }
    }

    // ──────────────────────────── Oynatma ────────────────────────────

    fun playAudio() {
        val success = _state.value as? AnalysisUIState.Success ?: run {
            println("[playAudio] Success state yok")
            return
        }
        val wavFile = File(success.wavPath)
        if (!wavFile.exists()) {
            println("[playAudio] WAV bulunamadi: ${success.wavPath}")
            scope.launch(Dispatchers.Main) {
                _state.value = AnalysisUIState.Error("Ses dosyasi bulunamadi: ${wavFile.name}")
            }
            return
        }
        println("[playAudio] Baslatiliyor: ${wavFile.name} (${wavFile.length()} byte)")

        if (isPlayerPaused) {
            isPlayerPaused = false
            player.resume()
            scope.launch(Dispatchers.Main) { _isPlaying.value = true }
            return
        }

        scope.launch(Dispatchers.Main) {
            _isPlaying.value = false
            _playbackTime.value = 0f
        }

        scope.launch(Dispatchers.IO) {
            val bytes = try {
                wavFile.readBytes()
            } catch (e: Exception) {
                println("[playAudio] Dosya okuma HATA: ${e.message}")
                withContext(Dispatchers.Main) {
                    _state.value = AnalysisUIState.Error("Dosya okunamadi: ${e.message}")
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                _isPlaying.value = true
            }

            try {
                player.play(bytes) { timeSec ->
                    _playbackTime.value = timeSec

                    // Her frame'de aktif harfi güncelle
                    val currentSuccess = _state.value as? AnalysisUIState.Success
                    if (currentSuccess != null) {
                        updateAktifHarf(timeSec, currentSuccess.points, currentSuccess.harfVeritabani)
                    }

                    if (timeSec >= success.totalDuration - 0.05f && success.totalDuration > 0f) {
                        _isPlaying.value = false
                        isPlayerPaused = false
                        _aktifHarf.value = AktifHarfState()
                        println("[playAudio] Oynatma tamamlandi (${timeSec}s / ${success.totalDuration}s)")
                    }
                }
            } catch (e: Exception) {
                println("[playAudio] Player HATA: ${e.javaClass.simpleName}: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _isPlaying.value = false
                    _state.value = AnalysisUIState.Error("Oynatma hatasi: ${e.message}")
                }
            }
        }
    }

    fun pauseAudio() {
        if (_isPlaying.value) {
            player.pause()
            isPlayerPaused = true
            _isPlaying.value = false
        }
    }

    fun resetPlayback() {
        player.stop()
        isPlayerPaused = false
        _isPlaying.value = false
        _aktifHarf.value = AktifHarfState()
        scope.launch(Dispatchers.Main) {
            _playbackTime.value = 0f
        }
    }

    fun resetToIdle() {
        player.stop()
        isPlayerPaused = false
        _isPlaying.value = false
        _aktifHarf.value = AktifHarfState()
        scope.launch(Dispatchers.Main) {
            _playbackTime.value = 0f
            _state.value = AnalysisUIState.Idle
        }
    }

    fun openSaveDir() {
        try {
            java.awt.Desktop.getDesktop().open(saveDir)
        } catch (e: Exception) {
            println("Klasor acilamadi: ${e.message}")
        }
    }

    fun deleteAllRecordings() {
        scope.launch(Dispatchers.IO) {
            try {
                saveDir.listFiles()?.forEach { file ->
                    if (file.extension in listOf("wav", "json")) {
                        file.delete()
                        println("Silindi: ${file.name}")
                    }
                }
                refreshRecordings()
            } catch (e: Exception) {
                println("Kayitlar silinirken hata: ${e.message}")
            }
        }
    }

    fun loadSpectrogram() {
        val success = _state.value as? AnalysisUIState.Success ?: run {
            _spectrogramState.value = SpectrogramUIState.Error("Önce bir kayıt analiz edin.")
            return
        }
        _spectrogramState.value = SpectrogramUIState.Loading
        scope.launch(Dispatchers.IO) {
            try {
                val wavFile = File(success.wavPath)
                if (!wavFile.exists()) {
                    withContext(Dispatchers.Main) {
                        _spectrogramState.value = SpectrogramUIState.Error(
                            "WAV dosyası bulunamadı: ${wavFile.name}"
                        )
                    }
                    return@launch
                }
                val bytes = wavFile.readBytes()
                val spectroData = analyzeSpectrogram(bytes)
                withContext(Dispatchers.Main) {
                    _spectrogramState.value = SpectrogramUIState.Success(spectroData)
                }
            } catch (e: Exception) {
                println("[loadSpectrogram] HATA: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _spectrogramState.value = SpectrogramUIState.Error(e.message ?: "Bilinmeyen hata")
                }
            }
        }
    }

    fun clearSpectrogram() {
        _spectrogramState.value = SpectrogramUIState.Idle
    }

    // ─────────────────────── Sure Dosya Tarama ────────────────────────────

    /**
     * sureler/ klasorundeki MP3/WAV dosyalarini PythonBridge uzerinden listele.
     * surelerKlasor bos birakilirsa Python kendi konumuna gore bulur.
     */
    fun surelerTara(
        analyzeWithBridge: suspend (String) -> List<DosyaBilgi>,
        surelerKlasor: String = ""
    ) {
        scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _surelerYukleniyor.value = true }
            try {
                val liste = analyzeWithBridge(surelerKlasor)
                withContext(Dispatchers.Main) {
                    _dosyaListesi.value = liste
                    _surelerYukleniyor.value = false
                }
            } catch (e: Exception) {
                println("[surelerTara] HATA: ${e.message}")
                withContext(Dispatchers.Main) { _surelerYukleniyor.value = false }
            }
        }
    }

    fun dosyaSec(dosya: DosyaBilgi) {
        _seciliDosya.value = dosya
        _karsilastirma.value = null
    }

    /**
     * Secili dosyayi analiz edip tecvid karsilastirmasi yapar.
     * analyzeAndCompare: (dosyaYolu, isim, sureNo, ayetNo) -> AnalysisResponse
     */
    fun analizVeKarsilastir(
        analyzeAndCompare: suspend (String, String, Int?, Int?) -> AnalysisResponse,
        karsilastirmaJson: (AnalysisResponse) -> KarsilastirmaResult?
    ) {
        val dosya = _seciliDosya.value ?: return
        _state.value = AnalysisUIState.Loading
        _karsilastirma.value = null
        scope.launch {
            try {
                val response = analyzeAndCompare(
                    dosya.tam_yol,
                    dosya.sure_isim ?: dosya.dosya_adi,
                    dosya.sure_no,
                    dosya.ayet_no
                )
                val points        = response.umap3d.map { it.toAudioPoint3D() }
                val pointsMaxMs   = points.maxOfOrNull { it.timeSec }?.let { it * 1000f } ?: 0f

                // Karsilastirma sonucunu parse et
                val karsilastirma = karsilastirmaJson(response)

                // WAV path: gecici dosya olusturmak yerine orijinal yolu kullan
                val ts      = java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(java.util.Date())
                val baseName = "${dosya.sure_isim ?: "analiz"}_${ts}"
                val wavFile = File(saveDir, "$baseName.wav")
                val jsonFile = File(saveDir, "$baseName.json")
                jsonFile.writeText(json.encodeToString(response))

                withContext(Dispatchers.Main) {
                    _karsilastirma.value = karsilastirma
                    _playbackTime.value  = 0f
                    _isPlaying.value     = false
                    _state.value = AnalysisUIState.Success(
                        points        = points,
                        segments      = response.segments,
                        totalDuration = (pointsMaxMs / 1000f).coerceAtLeast(1f),
                        surahName     = dosya.sure_isim ?: dosya.dosya_adi,
                        wavPath       = dosya.tam_yol,
                        genelSkor     = response.karsilastirma?.genel_skor ?: 0.0,
                        metin_metrikleri = response.metin_metrikleri
                    )
                }
                refreshRecordings()
            } catch (e: Exception) {
                println("[analizVeKarsilastir] HATA: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _state.value = AnalysisUIState.Error(e.message ?: "Bilinmeyen hata")
                }
            }
        }
    }

    // ──────────────────────────── Yardımcı ────────────────────────────

    /** UmapPoint → AudioPoint3D (eski format için) */
    private fun UmapPoint.toAudioPoint3D(): AudioPoint3D {
        val (r, g, b) = hexToRgb(color)
        return AudioPoint3D(x, y, z, r, g, b, timeSec, f0 ?: 0f, label)
    }

    /** AnalysisPoint → AudioPoint3D (yeni format için) */
    private fun AnalysisPoint.toAudioPoint3D(): AudioPoint3D {
        val (r, g, b) = hexToRgb(color)
        return AudioPoint3D(
            x         = x.toFloat(),
            y         = y.toFloat(),
            z         = z.toFloat(),
            r         = r,
            g         = g,
            b         = b,
            timeSec   = t.toFloat(),
            f0        = null,
            label     = harf ?: "",
            skor      = skor.toFloat(),
            harf      = harf,
            harf_isim = harf_isim,
            kelime    = kelime
        )
    }

    /**
     * Dosya yolundan metin oku ve analiz et
     */
    fun analyzeTextFile(filePath: String) {
        _state.value = AnalysisUIState.Loading
        scope.launch(Dispatchers.IO) {
            try {
                val analyzer = metinAnaliz
                if (analyzer == null) {
                    withContext(Dispatchers.Main) {
                        _state.value = AnalysisUIState.Error("Metin analiz callback'i ayarlanmamış")
                    }
                    return@launch
                }

                val file = File(filePath)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        _state.value = AnalysisUIState.Error("Dosya bulunamadı: $filePath")
                    }
                    return@launch
                }

                val metinIcerigi = file.readText()
                if (metinIcerigi.isBlank()) {
                    withContext(Dispatchers.Main) {
                        _state.value = AnalysisUIState.Error("Metin boş")
                    }
                    return@launch
                }

                // Backend'e gönder
                val metrikleri = analyzer(metinIcerigi)

                withContext(Dispatchers.Main) {
                    _playbackTime.value = 0f
                    _isPlaying.value = false
                    _state.value = AnalysisUIState.Success(
                        points = emptyList(),
                        segments = emptyList(),
                        totalDuration = 0f,
                        surahName = file.nameWithoutExtension,
                        wavPath = "",
                        metin_metrikleri = metrikleri
                    )
                }
            } catch (e: Exception) {
                println("[analyzeTextFile] HATA: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _state.value = AnalysisUIState.Error(e.message ?: "Metin analiz hatası")
                }
            }
        }
    }

    /**
     * Ses dosyaları listesini analiz et (çoklu seçim)
     */
    fun analyzeAudioFiles(filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        scope.launch {
            for (filePath in filePaths) {
                try {
                    _state.value = AnalysisUIState.Loading
                    val file = File(filePath)
                    if (!file.exists()) {
                        withContext(Dispatchers.Main) {
                            _state.value = AnalysisUIState.Error("Dosya bulunamadı: ${file.name}")
                        }
                        delay(500)
                        continue
                    }

                    val bytes = file.readBytes()
                    val name = file.nameWithoutExtension

                    // Backend'e gönder (analyzeAudio callback kullabılıyor)
                    val response = analyzeAudio(bytes, name)

                    val points = response.umap3d.map { it.toAudioPoint3D() }
                    val pointsMaxTimeMs = points.maxOfOrNull { it.timeSec }?.let { it * 1000f } ?: 0f
                    val totalDurationMs = getWavDuration(bytes, pointsMaxTimeMs)

                    // Sonuçları kaydet
                    val ts = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                    val baseName = "${name}_${ts}"
                    val wavFile = File(saveDir, "$baseName.wav")
                    wavFile.writeBytes(bytes)

                    val jsonFile = File(saveDir, "$baseName.json")
                    jsonFile.writeText(json.encodeToString(response))

                    println("Kaydedildi: ${wavFile.absolutePath}")
                    refreshRecordings()

                    withContext(Dispatchers.Main) {
                        _playbackTime.value = 0f
                        _isPlaying.value = false
                        _state.value = AnalysisUIState.Success(
                            points = points,
                            segments = response.segments,
                            totalDuration = totalDurationMs / 1000f,
                            surahName = name,
                            wavPath = wavFile.absolutePath,
                            metin_metrikleri = response.metin_metrikleri
                        )
                    }

                    delay(500)
                } catch (e: Exception) {
                    println("[analyzeAudioFiles] HATA: ${e.message}")
                    withContext(Dispatchers.Main) {
                        _state.value = AnalysisUIState.Error(e.message ?: "Ses analiz hatası")
                    }
                    delay(500)
                }
            }
        }
    }

    fun clear() {
        scope.cancel()
        timerJob?.cancel()
        player.stop()
        recorder.stopRecording {}
    }

    private fun hexToRgb(hex: String): Triple<Float, Float, Float> {
        val c = hex.trimStart('#').padEnd(6, '0')
        return try {
            Triple(
                c.substring(0, 2).toInt(16) / 255f,
                c.substring(2, 4).toInt(16) / 255f,
                c.substring(4, 6).toInt(16) / 255f
            )
        } catch (e: Exception) {
            Triple(0.5f, 0.5f, 0.5f)
        }
    }
}
