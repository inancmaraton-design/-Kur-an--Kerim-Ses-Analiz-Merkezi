# Kuran-ı Kerim Ses Analiz Uygulaması — Kapsamlı Teknik Analiz Raporu

**Tarih:** Mayıs 2026  
**Durum:** Aktif Geliştirme  
**Platform:** Windows Masaüstü (JVM/Kotlin)

---

## 1. Proje Özeti

**Kuran-ı Kerim Ses Analiz Merkezi** — Kur'an tilâveti ses kayıtlarının yapay zekâ (ML) ile analiz edilerek, ses spektrumunun 3D görselleştirildiği Windows masaüstü uygulaması.

**Ana Özellikler:**
- Canlı ses kaydı (22050 Hz, 16-bit mono) veya dosya yükleme (WAV, MP3, OGG, FLAC)
- Python backend tarafından ses analizi (librosa, UMAP)
- 3D timbre space görselleştirmesi (döndürülebilir, zoomlanabilir)
- Ses oynatma senkronizasyonu (frame-accurate)
- Kayıt arşivi (Belgeler/KuranAnaliz altında WAV + JSON çiftleri)

**Teknik Stack:**
| Katman | Teknoloji |
|--------|-----------|
| UI | Kotlin Multiplatform (Compose Multiplatform 1.6.10) |
| Desktop Runtime | JVM 17 + Swing (javax.sound.sampled) |
| Backend | Python 3.12 + librosa + UMAP |
| IPC | stdin/stdout JSON pipe (subprocess) |
| Build | Gradle 8.9 + jpackage (Windows MSI) |

---

## 2. Mimari Analizi

### 2.1 Genel Mimari Diyagramı

```
┌─────────────────────────────────────────────────────────────┐
│                   Kotlin Compose Desktop UI                 │
│  ┌─────────────┬────────────────┬──────────────────────┐    │
│  │   App.kt    │ RecordingsPanel │ TimbreSpaceRenderer  │    │
│  │ (MAIN UI)   │ (RECORDINGS)    │ (3D CANVAS RENDER)   │    │
│  └─────────────┴────────────────┴──────────────────────┘    │
│                           ↕                                   │
│  ┌────────────────────────────────────────────────────────┐  │
│  │           AnalysisViewModel (MVVM State)               │  │
│  │  - StateFlow<AnalysisUIState>                          │  │
│  │  - Kayıt/Analiz/Oynatma Yönetimi                       │  │
│  │  - Koroutine koordinasyonu (CoroutineScope)            │  │
│  └────────────────────────────────────────────────────────┘  │
│                           ↕                                   │
│  ┌────────────────────────────────────────────────────────┐  │
│  │    Platform Abstraction (expect/actual)                │  │
│  │  - PlatformAudioRecorder (javax.sound.sampled)         │  │
│  │  - PlatformAudioPlayer (javax.sound.sampled + pause)   │  │
│  │  - openFilePicker (java.awt.FileDialog)                │  │
│  │  - WavGenerator (PCM → WAV container)                  │  │
│  └────────────────────────────────────────────────────────┘  │
│                           ↕                                   │
│  ┌────────────────────────────────────────────────────────┐  │
│  │        PythonBridge (Singleton + IPC Controller)       │  │
│  │  - ProcessBuilder (subprocess yönetimi)                │  │
│  │  - JSON/String parser (stdin/stdout)                   │  │
│  │  - Thread-safe: synchronized(this) + @Synchronized     │  │
│  └────────────────────────────────────────────────────────┘  │
│                           ↕ (IPC pipe)                       │
└─────────────────────────────────────────────────────────────┘
                             │
                  [analyze_bridge.py subprocess]
                             │
        ┌────────────────────┴────────────────────┐
        ↓                                          ↓
[audio_analyzer.py]                     [~/ Belgeler/KuranAnaliz/]
- librosa (MFCC, pitch)                 (WAV + JSON arşiv)
- UMAP (3D görselleştirme)
- LPC (formant analizi)
```

### 2.2 Katman Analizi

#### **UI Katmanı** (commonMain)
- **App.kt** — Ana Composable root, state management, view mode tabları
  - `ViewMode` enum: STATIC_FULL | ANIMATED | SYNC
  - `StatusOverlay` — Kayıt, yükleme, hata durumları
  - `BottomPanel` — Kontrol paneli (kayıt, dosya seç, oynat, ilerleme)
  - `AmbientBackground` — Boş durumdayken arapça "قرآن" animasyonu
- **RecordingsPanel.kt** — Yan panel, kayıt listesi, slide-in animasyonu
- **TimbreSpaceRenderer.kt** — 3D nokta bulutu Canvas render'ı
  - Fare sürükleme → Y/X rotasyonu
  - Scroll → Zoom (10x–2000x)
  - Painter's algorithm (depth sorting)

#### **ViewModel Katmanı** (commonMain)
- **AnalysisViewModel** — Tek merkez ViewModel
  - State machine: Idle → Recording → Loading → Success/Error
  - StateFlow'lar: `state`, `playbackTime`, `isPlaying`, `availableRecordings`
  - Metodlar: startRecording(), stopAndAnalyze(), playAudio(), loadRecording() vb.
  - **Sorun:** Scope asla cancel() edilmiyor → leak

#### **Model Katmanı** (commonMain)
- **AnalysisModels.kt** — Data class'lar (Kotlin serialization uyumlu)
  - `AnalysisResponse` — Python'dan gelen kök JSON yanıtı
  - `UmapPoint` — 3D koordinat (x,y,z), renk, F0, zaman
  - `Segment` — Ses segmenti (baş/bitiş frame)
  - `AudioPoint3D` — Render-hazır form (r,g,b float)
  - `RecordingEntry` — Kayıt dosya metadata'sı

#### **Servis Katmanı** (desktopMain)
- **PythonBridge** — Singleton object, Python IPC controller
  - ProcessBuilder ile analyze_bridge.py subprocess başlatır
  - JSON seri/deserialization (kotlinx.serialization)
  - **IPC Protokolü:**
    ```json
    // Request: Kotlin → Python
    {"cmd":"analyze", "path":"/tmp/xxx.wav", "name":"Sure_Adi"}
    
    // Response: Python → Kotlin
    {"ok":true, "data":{...AnalysisResponse...}}
    ```
  - **Sorunlar:** Çok sayıda `!!`, synchronized + blocking I/O

#### **Platform Soyutlama** (expect/actual)
- **expect** — commonMain/audio/AudioExpectations.kt
  - `expect class PlatformAudioRecorder`, `expect class PlatformAudioPlayer`
  - `expect fun openFilePicker()`
- **actual** — desktopMain/audio/AudioImplementations.kt
  - `actual class PlatformAudioRecorder` — javax.sound.sampled.TargetDataLine
  - `actual class PlatformAudioPlayer` — javax.sound.sampled.SourceDataLine + pause/resume
  - `actual fun openFilePicker` — java.awt.FileDialog

### 2.3 Ses Analiz Pipeline'i (Python Backend)

```python
1. librosa.load(path) + pre-emphasis filtresi (0.97)
2. pyin() → F0 pitch (HMM Viterbi)
3. MFCC (13) + delta + delta-delta → 39 boyutlu feature
4. Spektral: centroid, flux, ZCR, rolloff, RMS
5. LPC analizi → F1/F2 formant cıkartma
6. Tüm features → 46 boyutlu feature matrix
7. UMAP (46D → 3D, cosine metric, n_neighbors=15)
8. Sessizlik tespiti (RMS -40dB eşiği)
9. Renk atama: enerji seviyelerine göre (turuncu/sarı/mavi/yeşil + gri=sessizlik)
```

---

## 3. Kullanılan Diller ve Teknolojiler

### 3.1 Programlama Dilleri
| Dil | Versiyon | Kullanım |
|-----|----------|----------|
| **Kotlin** | 2.1.0 | UI (Compose), ViewModel, Platform abstraction, IPC |
| **Python** | 3.12 | Ses analiz (librosa, UMAP, scipy, scikit-learn) |
| **XML/Gradle** | - | Build konfigürasyonu |
| **JSON** | - | Veri seriyalizasyonu (Python ↔ Kotlin) |

### 3.2 Framework'ler ve Kütüphaneler

#### **Kotlin/JVM Taraf**
| Kütüphane | Versiyon | Amaç |
|-----------|----------|------|
| `Compose Multiplatform` | 1.6.10 | UI framework (declarative) |
| `Kotlin Serialization` | 1.6.3 | JSON seri/deserialization |
| `Coroutines Core` | 1.8.1 | Async/await, CoroutineScope |
| `Coroutines Swing` | 1.8.1 | Swing event loop entegrasyonu |
| `javax.sound.sampled` | (stdlib) | Ses kaydı/oynatma |
| `java.awt.FileDialog` | (stdlib) | Dosya seçici dialog |

#### **Python Taraf**
| Kütüphane | Versiyon | Amaç |
|-----------|----------|------|
| `librosa` | 0.10.2 | Ses analiz (MFCC, pitch, spektral) |
| `numpy` | 1.26.4 | Sayısal hesaplama |
| `scipy` | 1.13.0 | Bilimsel fonksiyonlar |
| `scikit-learn` | 1.5.2 | ML yardımcı |
| `umap-learn` | 0.5.6 | Boyut indirgeme (3D) |
| `soundfile` | 0.12.1 | Ses dosyası okuma |
| `loguru` | 0.7.2 | Logging |

**Deprected (kullannılmıyor ama version catalog'da):**
- Ktor Client (core, okhttp, serialization) — v2.3.11
- AndroidX (lifecycle, activity, compose) — Hedef Android değil, Desktop sadece

### 3.3 Build Sistemi
- **Gradle:** 8.9-bin (ZORUNLU — 9.x ile uyumsuz)
- **JVM Toolchain:** JDK 17 (Eclipse Adoptium)
- **Compose Desktop:** jlink + jpackage (Windows .exe, .msi)
- **Tur Karakteri Workaround:** build çıktıları `C:/KuranBuild/` → ASCII yol

---

## 4. İş Yöntemi ve Akışı

### 4.1 Uygulama Başlatması
1. `MainKt.main()` → JVM entry point
2. Compose Desktop runtime başlatılır
3. `App()` composable root render edilir
4. `PythonBridge.getInstance()` → lazy initialization (Python subprocess başlatılır)

### 4.2 Kayıt Akışı
```
[Kayıt Başla Butonu]
    ↓
startRecording() → PlatformAudioRecorder.startRecording()
    ↓
[javax.sound.sampled.TargetDataLine opens, 22050Hz mono]
    ↓
Koroutine döngüsü PCM verisi toplar (ByteArray'e)
    ↓
[Kayıt Durdur Butonu]
    ↓
stopAndAnalyze()
    ↓
ByteArray → WAV file (WavGenerator) → /Belgeler/KuranAnaliz/SureAdi_YYYYMMDD_HHMMSS.wav
    ↓
PythonBridge.analyze(path, name) → JSON request
    ↓
Python subprocess → AnalysisResponse (UMAP 3D + segments)
    ↓
ViewModel.state = Success(analysisData)
    ↓
UI render (TimbreSpaceRenderer 3D noktalar gösterir)
```

### 4.3 Oynatma Akışı
```
[Oynat Butonu]
    ↓
playAudio() → loadRecording() → ByteArray load
    ↓
PlatformAudioPlayer.play(bytes, onProgress)
    ↓
javax.sound.sampled.SourceDataLine açılır
    ↓
onProgress callback → _playbackTime.value = timeSec
    ↓
UI: İlerleme çubuğu güncellenir
UI: (SYNC modu) 3D noktalar ses konumuna senkron hareket eder
    ↓
Oynatma bittiyse _isPlaying.value = false
```

### 4.4 3D Görselleştirme Modları
| Mod | Davranış |
|-----|----------|
| **STATIC_FULL** | Tüm noktalar sabit, renklendirilmiş enerji seviyelerine göre |
| **ANIMATED** | Zaman tabanlı loop, noktalar animasyon hızında hareket eder |
| **SYNC** | Ses oynatmasıyla senkron, noktalar gerçek zamanlı takip eder |

---

## 5. Tespit Edilen Hatalar

### 🔴 KRİTİK HATALAR

#### **5.1 CoroutineScope Lifecycle Leak**

**Konum:** `composeApp/src/commonMain/kotlin/com/kuran/android/viewmodel/AnalysisViewModel.kt:62`

**Kod:**
```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

**Hata Açıklaması:**
- ViewModel manuel olarak CoroutineScope oluşturuyor
- `scope.cancel()` asla çağrılmıyor
- Window kapandığında scope iptal edilmez
- Aktif coroutine'ler JVM heap'inde asılı kalır
- Uzun süreli uygulamalar veya çok sayıda analiz durumunda memory leak

**Root Cause:**
- Jetpack Compose'da `rememberCoroutineScope()` yerine manuel scope
- Lifecycle binding eksikliği — MVVM implementasyonu eksik
- `Main.kt`'de `onCloseRequest` sadece `PythonBridge.stop()` çağırıyor

**Etki:** Memory leak, özellikle batch analiz senaryolarında ciddi

**Alternatif Çözümler:**

1. **Önerilen — DisposableEffect + rememberCoroutineScope():**
```kotlin
@Composable
fun App(viewModel: AnalysisViewModel = remember { AnalysisViewModel(...) }) {
    val scope = rememberCoroutineScope()
    
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelAllTasks()  // cleanup method
        }
    }
}
```

2. **AndroidViewModel Pattern (Lifecycle-aware):**
```kotlin
class AnalysisViewModel(...) : ViewModel() {
    // Bu pattern JVM'de doğrudan geçerli değil, ancak similiar:
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )
    
    override fun onCleared() {
        scope.cancel()
    }
}
```

3. **Explicit cleanup callback:**
```kotlin
fun shutdown() {
    scope.cancel()
    player.stop()
    PythonBridge.stop()
}
// Main.kt'de:
window.onCloseRequest = { 
    viewModel.shutdown()
    exitApplication()
}
```

---

#### **5.2 Thread-Safety Sorunu — isPlayerPaused İçin @Volatile Eksikliği**

**Konum:** `AnalysisViewModel.kt:66`

**Kod:**
```kotlin
private var isPlayerPaused = false
```

**Hata Açıklaması:**
- `isPlayerPaused` Boolean değişkeni @Volatile veya AtomicBoolean değil
- `playAudio()` method'unda (IO dispatcher): `isPlayerPaused = false` yazılıyor
- `pauseAudio()` method'unda (Default dispatcher): `isPlayerPaused = true` yazılıyor
- `onProgress` callback'i (IO thread): `isPlayerPaused` okuyup kontrol ediyor
- **Memory visibility sorunu:** JVM thread'leri cache'lerini senkronize etmeyebilir

**Root Cause:**
- Fine-grained mutable state, StateFlow ile wrap edilmemiş
- Threading model doğru tasarlanmamış

**Etki:** 
- Nadir deadlock veya race condition (düşük ihtimal ama potansiyel)
- Oynatma durumu tutarsız olabilir

**Alternatif Çözümler:**

1. **Önerilen — @Volatile kullanımı:**
```kotlin
@Volatile
private var isPlayerPaused = false
```

2. **AtomicBoolean:**
```kotlin
private val isPlayerPaused = AtomicBoolean(false)
// Kullanımı: isPlayerPaused.get(), isPlayerPaused.set(true)
```

3. **StateFlow ile (en elegant):**
```kotlin
private val _isPlayerPausedPrivate = MutableStateFlow(false)
val isPlayerPaused: StateFlow<Boolean> = _isPlayerPausedPrivate.asStateFlow()

// Yazma:
_isPlayerPausedPrivate.value = true
// Okuma:
if (_isPlayerPausedPrivate.value) { ... }
```

---

#### **5.3 NullPointerException Riski — !! Operatörü Aşırılaması**

**Konum:** `composeApp/src/desktopMain/kotlin/com/kuran/android/analysis/PythonBridge.kt:110-115`

**Kod:**
```kotlin
writer  = BufferedWriter(OutputStreamWriter(process!!.outputStream, Charsets.UTF_8))
reader  = BufferedReader(InputStreamReader(process!!.inputStream, Charsets.UTF_8))
val firstLine = reader!!.readLine()
```

**Hata Açıklaması:**
- `process!!`, `reader!!`, `writer!!` çok sayıda yer kullanılıyor
- Subprocess başlatması başarısız olursa (örn., Python yok) `process` null
- `!!` operatörü `NullPointerException` fırlatır, aygın hata mesajı yok
- `started` flag set edilmeden exception olursa inconsistent state

**Root Cause:**
- Null-safe design eksikliği
- Error handling yetersiz
- Process validation yapılmıyor

**Etki:** 
- Kullanıcı-dostu olmayan çöküş (stack trace, hiçbir uyarı mesajı yok)
- Debug zor

**Alternatif Çözümler:**

1. **Önerilen — Elvis operator + explicit error:**
```kotlin
val process = process ?: run {
    state = BridgeState.ERROR
    error("Python process başlatılamadı. Lütfen Python 3.12+ ve librosa'nın yüklü olduğunu kontrol edin.")
}
writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
    ?: error("Writer açılamadı")
```

2. **Result<T> pattern:**
```kotlin
fun start(): Result<Unit> = runCatching {
    val pb = ProcessBuilder("python", "-m", "analyze_bridge")
    process = pb.start() ?: return Result.failure(Exception("Process null"))
    writer = BufferedWriter(...)
    reader = BufferedReader(...)
    started = true
}
// Çağrıda: start().onFailure { e -> showErrorDialog(e.message) }
```

3. **Validation + descriptive error:**
```kotlin
private fun validateProcessHandles() {
    check(process != null) { "Process null (başlatılmamış)" }
    check(writer != null) { "Writer null (I/O başarısız)" }
    check(reader != null) { "Reader null (I/O başarısız)" }
}
```

---

#### **5.4 Deadlock Riski — Synchronized Blok İçinde Blocking I/O**

**Konum:** `PythonBridge.kt:156-165`

**Kod:**
```kotlin
synchronized(this@PythonBridge) {
    writer!!.write(cmd)
    writer!!.newLine()
    writer!!.flush()
}
val responseLine = synchronized(this@PythonBridge) {
    reader!!.readLine()  // ← Blocking I/O inside synchronized!
}
```

**Hata Açıklaması:**
- `reader!!.readLine()` blocking (çağrı Python yanıt verene kadar bekler)
- Bu bekleyiş `synchronized(this)` blok içinde yapılıyor
- Python subprocess'i yavaş yanıt verirse (network, I/O) → JVM thread bloke
- Ayrı @Synchronized metotlar varsa lock ordering → deadlock riski
- Thread pool'a bağlı coroutine'ler starve edebilir

**Root Cause:**
- Synchronization primitivi yanlış yerde (lock too broad)
- Blocking I/O + mutex kombinasyonu anti-pattern

**Etki:**
- UI freeze (responsive değil)
- Timeout yok, sonsuz bekleyiş
- Deadlock (nadir ama mümkün)

**Alternatif Çözümler:**

1. **Önerilen — Coroutine'ler + withContext(IO):**
```kotlin
suspend fun sendCommand(cmd: String): String = withContext(Dispatchers.IO) {
    synchronized(this@PythonBridge) {
        writer!!.write(cmd)
        writer!!.newLine()
        writer!!.flush()
    }
    
    synchronized(this@PythonBridge) {
        reader!!.readLine() ?: error("EOF")
    }
}
```

2. **Timeout + non-blocking:**
```kotlin
val responseLine = try {
    withTimeoutOrNull(Duration.ofSeconds(30)) {
        synchronized(this@PythonBridge) {
            reader!!.readLine()
        }
    } ?: error("Python timeout (30s)")
} catch (e: TimeoutCancellationException) {
    // Graceful error
    error("Analiz çok uzun sürdü")
}
```

3. **Lock-free channels (en elegant):**
```kotlin
private val requestChannel = Channel<String>()
private val responseChannel = Channel<String>()

// Writer thread:
for (cmd in requestChannel) {
    writer!!.write(cmd); writer!!.flush()
}

// Reader thread:
while (true) {
    val line = reader!!.readLine()
    responseChannel.send(line)
}

// Kullanımda:
suspend fun analyze(...): AnalysisResponse {
    requestChannel.send(jsonCmd)
    val response = responseChannel.receive()
    return Json.decodeFromString(response)
}
```

---

### 🟠 ORTA SEVİYE HATALAR

#### **5.5 Thread-Safe Olmayan capturedBytes (AudioImplementations.kt:18)**

**Konum:** `composeApp/src/desktopMain/kotlin/com/kuran/android/audio/AudioImplementations.kt:18`

**Kod:**
```kotlin
private var capturedBytes: ByteArray? = null
```

**Sorun:**
- IO coroutine'de yazılıp (recording loop), `stopRecording` callback'inde okunuyor
- Race condition riski
- `@Volatile` veya lock yok

**Çözüm:**
```kotlin
@Volatile
private var capturedBytes: ByteArray? = null
// veya
private val capturedBytes = AtomicReference<ByteArray?>(null)
```

---

#### **5.6 PlatformAudioRecorder Scope Leak**

**Konum:** `AudioImplementations.kt:16`

**Kod:**
```kotlin
private val scope = CoroutineScope(Dispatchers.IO)
```

**Sorun:**
- SupervisorJob yok, scope asla cancel() edilmiyor
- Recording durursa scope iptal edilmemeli

**Çözüm:**
```kotlin
private var scope: CoroutineScope? = null

fun startRecording() {
    scope = CoroutineScope(Dispatchers.IO + Job())
    scope!!.launch { ... }
}

fun stopRecording() {
    scope?.cancel()
    scope = null
}
```

---

#### **5.7 Gecici Dosya Silme Başarısızlığı Yok Sayılır**

**Konum:** `PythonBridge.kt:181`

**Kod:**
```kotlin
} finally {
    tmp.delete()  // Return value ignored!
}
```

**Sorun:**
- Python prosesi dosyayı kilitledi ise `delete()` false döndürür
- Sessizce atlanır, temp dosya birikir
- Disk alanı tükenebilir

**Çözüm:**
```kotlin
} finally {
    if (!tmp.delete()) {
        // Retry veya schedule for deletion
        tmp.deleteOnExit()
        System.err.println("⚠ Temp file delete failed: ${tmp.path}")
    }
}
```

---

#### **5.8 RecordingEntry ViewModel Dosyasında Tanımlandı**

**Konum:** `AnalysisViewModel.kt:18`

**Kod:**
```kotlin
data class RecordingEntry(...)  // ← ViewModel'de tanımlanmış
```

**Sorun:**
- Model katmanında olması gerekir (AnalysisModels.kt)
- Separation of concerns ihlali
- Reusability düşük

**Çözüm:**
```kotlin
// Taşı: models/AnalysisModels.kt
data class RecordingEntry(
    val wavFile: File,
    val jsonFile: File,
    val name: String,
    val timestamp: Long
)
```

---

#### **5.9 SimpleDateFormat Thread-Safe Değil**

**Konum:** `AnalysisViewModel.kt:97`

**Kod:**
```kotlin
SimpleDateFormat("dd.MM.yyyy HH:mm").format(Date(...))
```

**Sorun:**
- JVM'de SimpleDateFormat thread-safe değil
- Her seferinde yeni instance oluşturuluyor (çalışıyor ama inefficient)
- Java 8+ DateTimeFormatter kullanılmalı

**Çözüm:**
```kotlin
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

fun formatTime(instant: Instant): String {
    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        .format(dateFormatter)
}
```

---

### 🟡 DÜŞÜK SEVİYE / KALİTE SORUNLARI

#### **5.10 stderr DISCARD Ediliyor**

**Konum:** `PythonBridge.kt:107`

**Kod:**
```kotlin
pb.redirectError(ProcessBuilder.Redirect.DISCARD)
```

**Sorun:**
- Python hata mesajları tamamen kaybolur
- Debug çok zordur
- Crash'e neden olan stack trace görülmez

**Çözüm:**
```kotlin
// Seçenek 1: Dosyaya yönlendir
pb.redirectError(ProcessBuilder.Redirect.to(File("python-error.log")))

// Seçenek 2: stdout'a karıştur
pb.redirectErrorStream(true)

// Seçenek 3: Uygulamanın logger'ına aktar
val errorReader = process.errorStream.bufferedReader()
scope.launch(Dispatchers.IO) {
    errorReader.useLines { lines ->
        lines.forEach { line -> 
            Timber.e("Python: $line")
        }
    }
}
```

---

#### **5.11 Hard-coded Kayıt Dizini**

**Konum:** `AnalysisViewModel.kt:58-60`

**Kod:**
```kotlin
val saveDir: File = File(System.getProperty("user.home") + "/Documents/KuranAnaliz")
```

**Sorun:**
- Kullanıcı değiştiremez
- Harici sürücüye kaydetmek imkânsız
- Cross-platform sorunlar (Windows'ta Documents yolu değişebilir)

**Çözüm:**
```kotlin
// Settings ile yapılabilir:
var recordingDirectory: File = defaultRecordingDir()
    private set

fun setRecordingDirectory(dir: File) {
    require(dir.exists() && dir.isDirectory) { "Geçersiz dizin" }
    recordingDirectory = dir
    // Preference'e kaydet
}

private fun defaultRecordingDir(): File {
    val docs = File(System.getProperty("user.home"), "Documents")
    return File(docs, "KuranAnaliz").apply { mkdirs() }
}
```

---

#### **5.12 Kullanılmayan Dependency'ler (Version Catalog Kirli)**

**Konum:** `gradle/libs.versions.toml`

**Sorun:**
- Ktor Client (core, okhttp, serialization) — kullanılmıyor (eski HTTP mimarisi)
- AndroidX (lifecycle, activity, compose) — Android target yok
- Network aydınlatma azaltır

**Çözüm:**
```toml
# Deprecated section'a taşı veya sil
[deprecated]
ktor-client-core = "2.3.11"
ktor-client-okhttp = "2.3.11"
androidx-lifecycle-runtime = "2.6.1"

# composeApp/build.gradle.kts'de uncomment et
// implementation(libs.deprecated.ktor-client-core)
```

---

#### **5.13 main.py (Eski FastAPI) Temizlenmemiş**

**Konum:** `backend/main.py`

**Sorun:**
- FastAPI v1 HTTP sunucusu (v3.0 ile terk edildi)
- Repository'de duruyor, yaniltıcı
- Birisi yanliş olarak `python main.py` calistirsa 8000 port'ta server açılır
- `__pycache__/main.cpython-312.pyc` proof of concept

**Çözüm:**
```bash
# Seçenek 1: Sil
rm backend/main.py

# Seçenek 2: Legacy klasörüne taşı
mkdir -p backend/legacy
mv backend/main.py backend/legacy/
# README ekle: "Bu dosyalar deprecated v1 mimarisi, v3.0 analyze_bridge.py kullanır"

# Seçenek 3: Uyarı ekle (kalıcı yapacaksan)
# main.py ilk satırı:
"""
⚠️  DEPRECATED - Bu dosya FastAPI v1 mimarisi (2024)
v3.0+ analyze_bridge.py subprocess IPC kullanır.
Bu dosya RUN ETMEYIN!
"""
```

---

#### **5.14 test_endpoint.py Artık Çalışmıyor**

**Konum:** `backend/test_endpoint.py`

**Sorun:**
- FastAPI sunucusu yok (v3.0'da kaldırıldı)
- Test başarısız olur
- Yaniltıcı

**Çözüm:**
```bash
# Seçenek 1: Sil
rm backend/test_endpoint.py

# Seçenek 2: Güncelle (analyze_bridge test'ine çevir)
# Yeni test dosyası: backend/test_analyze_bridge.py
```

---

## 6. Geliştirme Önerileri (Öncelik Sırasına Göre)

### **P0 — AÇIK YARAYLA BAŞLA (bu ayda çözülmeli)**

1. **ViewModel CoroutineScope Lifecycle Bağlama**
   - Hata #5.1
   - Çözüm: DisposableEffect + rememberCoroutineScope()
   - Etkiniz: 2-4 saat
   - Risk: Düşük (geçmiş versiyonlarla uyumlu)

2. **Thread-Safety: @Volatile Ekle**
   - Hata #5.2, #5.6
   - Çözüm: `@Volatile` ekle veya AtomicBoolean
   - Etkiniz: 30 dakika
   - Risk: Çok düşük

3. **NullPointerException Exception Handling Iyileştir**
   - Hata #5.3
   - Çözüm: Elvis operator + explicit error messages
   - Etkiniz: 1-2 saat
   - Risk: Düşük

---

### **P1 — İKİ HAFTA İÇİNDE YAPılMALI**

4. **Repository Katmanı Oluştur**
   - Geliştirme önerisi
   - ViewModel'den dosya sistemi erişim ayır
   - Yeni sınıf: `AudioRepository`, `AnalysisRepository`
   - Etkiniz: 4-6 saat
   - Risk: Orta (refactor)

5. **Python stderr Logging Ekle**
   - Hata #5.10
   - Çözüm: Dosyaya yönlendir veya stdout'a karıştur
   - Etkiniz: 1 saat
   - Risk: Çok düşük

6. **Main.py (Eski FastAPI) Temizle**
   - Hata #5.13
   - Çözüm: Legacy klasörüne taşı veya sil
   - Etkiniz: 15 dakika
   - Risk: Çok düşük

---

### **P2 — BU AYDA TESPİT ET VE PLANLA**

7. **Deadlock Riski: Synchronized → Coroutine Channels**
   - Hata #5.4
   - Çözüm: Lock-free channels pattern
   - Etkiniz: 6-8 saat (derinlemesine refactor)
   - Risk: Yüksek (extensive testing gerekir)
   - **Not:** Mevcut kod de facto çalışıyor, ancak potential deadlock riski vardır

8. **SimpleDateFormat → DateTimeFormatter**
   - Hata #5.9
   - Çözüm: Java 8+ DateTimeFormatter
   - Etkiniz: 1 saat
   - Risk: Düşük

9. **Kayıt Dizini User-Configurable Yapma**
   - Hata #5.11
   - Çözüm: Settings/Preferences menüsü ekle
   - Etkiniz: 2-3 saat (UI + persistence)
   - Risk: Orta

---

### **P3 — TEKNIK BORÇ (Sonraki Release'e)**

10. **Version Catalog Temizle (Unused Dependencies)**
    - Hata #5.12
    - Çözüm: Ktor, AndroidX eklemelerini sil veya deprecated seção taşı
    - Etkiniz: 1 saat
    - Risk: Çok düşük

11. **RecordingEntry Taşı (models/ klasörüne)**
    - Hata #5.8
    - Çözüm: models/AnalysisModels.kt'ye move
    - Etkiniz: 30 dakika
    - Risk: Çok düşük

12. **test_endpoint.py Sil/Güncelle**
    - Hata #5.14
    - Çözüm: Yeni test dosyası (analyze_bridge için) veya sil
    - Etkiniz: 1 saat
    - Risk: Çok düşük

---

## 7. Güvenlik Değerlendirmesi

### ✅ GÜVENLİ BULUNDU
- **API Key exposure:** BULUNAMADI (local.properties temiz)
- **Hardcoded secrets:** Tüm Python/Kotlin dosyaları tarandı — BULUNAMADI
- **SQL injection:** Veritabanı kullanılmıyor
- **XSS:** Web API yok (masaüstü uygulaması)

### ⚠️ DÜŞÜK RİSK (İyileştirme Önerisi)

| Risk | Detay | Şiddet |
|------|-------|--------|
| **Path injection (Python)** | `surahName` JSON'a doğrudan ekleniyor, sadece `"` → `'` sanitize | DÜŞÜK |
| **stderr disclosure** | Python hataları DISCARD, debug zor | DÜŞÜK |
| **Temp dosya silinemiyor** | `tmp.delete()` başarısızlığı yok sayılır | DÜŞÜK |
| **Process injection** | `ProcessBuilder` user input almıyor, hard-coded path | GÜVENLİ |

**Sonuç:** Proje güvenlik açısından temiz, standart masaüstü uygulaması riskleri mevcut.

---

## 8. Mimari Öneriler (Long-term)

### 8.1 State Management Iyileştirmesi

**Mevcut:** AnalysisViewModel tek merkez ViewModel
**Sorun:** Çok büyüyor (recording, playback, file manager hepsi aynı yerde)

**Önerilen:** Multi-ViewModel + ViewModel Factory
```kotlin
// ViewModels.kt
val recordingViewModel: RecordingViewModel by lazy { ... }
val playbackViewModel: PlaybackViewModel by lazy { ... }
val fileManagerViewModel: FileManagerViewModel by lazy { ... }

// App.kt
App(
    recordingVM = recordingViewModel,
    playbackVM = playbackViewModel,
    fileManagerVM = fileManagerViewModel
)
```

### 8.2 Navigation Framework Ekle (İleride)

**Mevcut:** Manuel state yönetimi (showRecordings: Boolean)
**Sorun:** Karmaşık multi-screen senaryolara ölçeklenemiyor

**Önerilen:** Compose Navigation
```kotlin
// Navigation.kt
sealed class Screen {
    object Main : Screen()
    object Settings : Screen()
    data class RecordingDetail(val id: String) : Screen()
}

NavHost(navController, startDestination = Screen.Main) {
    composable<Screen.Main> { MainScreen() }
    ...
}
```

### 8.3 Dependency Injection Ekle (Opsiyonel)

**Mevcut:** Manual constructor passing
**Önerilen:** Koin (DI framework)
```kotlin
// DI.kt
val appModule = module {
    single { AnalysisViewModel(...) }
    single { PythonBridge() }
    single { AudioRepository(get()) }
}

// Main.kt
startKoin { modules(appModule) }
```

### 8.4 Test Suite Oluştur

**Mevcut:** Test yok
**Önerilen:**
- Unit tests: AnalysisViewModel state machine
- Integration tests: PythonBridge ↔ analyze_bridge
- E2E tests: Recorder → Analyzer → Player flow

```bash
# Test koşu
./gradlew test
```

---

## 9. Derlenebilirlik ve Build Durumu

**Son Build:** ✅ Başarılı (Gradle 8.9)

**Bilinen Build Sorunları:**
- **Gradle 9.x:** Uyumsuz, downgrade to 8.9 (CLAUDE.md'de belirtili)
- **Türkçe karakter:** Path'de "ı" sorunu → KuranBuild/ workaround
- **ProGuard:** Devre dış (isEnabled=false)

**Test Suite:** Mevcut değil

---

## 10. Özet Tablo

| Metrik | Değer | Durum |
|--------|-------|-------|
| **Kod Satırı** | ~1500 (Kotlin) + ~800 (Python) | ✅ Orta |
| **Mimari Kalitesi** | MVVM + IPC | ✅ İyi (eksik: Repository) |
| **Teknik Borç** | 5 kritik + 9 orta hata | ⚠️ Orta |
| **Test Kapsamı** | %0 | 🔴 Kritik |
| **Güvenlik** | Temiz, standart masaüstü riskleri | ✅ İyi |
| **Performance** | Hızlı (Python backend async) | ✅ İyi |
| **Ölçeklenebilirlik** | Şimdilik iyi, uzun vadede refactor gerekir | ⚠️ Uyarı |

---

## 11. Sonuçlar ve Tavsiyeler

### ✅ Güçlü Yönler
1. **Temiz mimari:** expect/actual pattern, MVVM doğru uygulanmış
2. **Özgün IPC çözümü:** HTTP'den daha hafif, saf subprocess
3. **Türkçe dokümantasyon:** KURAN_ANALIZ_PROJE.md iyi belgelenmiş
4. **Python backend:** librosa + UMAP kombinasyonu etkin

### ⚠️ İyileştirme Alanları
1. **Lifecycle management:** ViewModel scope lifecycle bağlanmalı
2. **Thread safety:** @Volatile eklemeler
3. **Error handling:** NullPointerException riskleri
4. **Repository katmanı:** Separation of concerns eksik
5. **Testing:** Test suite yoktur

### 🎯 Immediate Action Items (Öncelik Sırasına Göre)

| # | Görev | Saatler | Öncelik |
|---|-------|---------|---------|
| 1 | ViewModel CoroutineScope lifecycle | 2-4 | P0 |
| 2 | @Volatile eklemeler | 0.5 | P0 |
| 3 | NullPointerException handling | 1-2 | P0 |
| 4 | Repository katmanı oluştur | 4-6 | P1 |
| 5 | stderr logging | 1 | P1 |
| 6 | main.py temizle | 0.25 | P1 |

**Tahmini Total:** ~13-14 saat çalışma (kritik + önemli görevler)

---

## 12. Referanslar

- CLAUDE.md — Proje Claude Code talimatları
- KURAN_ANALIZ_PROJE.md — Detaylı teknik dokümantasyon
- `backend/requirements.txt` — Python bağımlılıkları
- `gradle/libs.versions.toml` — Kotlin bağımlılıkları

---

**Rapor tarihi:** 2026-05-03  
**Hazırlayan:** Claude Code (Comprehensive Codebase Analysis)
