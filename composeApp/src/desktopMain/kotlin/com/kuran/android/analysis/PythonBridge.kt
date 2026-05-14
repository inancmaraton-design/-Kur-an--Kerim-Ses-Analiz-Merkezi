package com.kuran.android.analysis

import com.kuran.android.models.AnalysisResponse
import com.kuran.android.models.SpectrogramData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * PythonBridge — FastAPI/HTTP yerine stdin/stdout pipe üzerinden
 * analyze_bridge.py ile haberleşir.
 *
 * Tek process mantığı:
 *  - Python subprocess Kotlin tarafından başlatılır
 *  - Kullanıcı hiçbir şey yapmaz, backend kurulumu gerekmez
 *  - HTTP, TCP, uvicorn yoktur; saf pipe IPC
 *
 * Protokol (satır bazlı JSON):
 *  Kotlin → stdin : {"cmd":"analyze","path":"/tmp/xx.wav","name":"Sure"}
 *  Python → stdout: {"ok":true,"data":{...AnalysisResponse...}}
 */
object PythonBridge {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val started = AtomicBoolean(false)
    private val mutex = Mutex()
    val isStarted: Boolean get() = started.get() && process?.isAlive == true

    private fun findBackendDir(): File {
        // 1. Çalışma dizini ve üst dizinlerinde "backend/" ara
        var currentDir: File? = File(System.getProperty("user.dir"))
        while (currentDir != null) {
            val backendDir = File(currentDir, "backend")
            val scriptFile = File(backendDir, "analyze_bridge.py")
            if (backendDir.exists() && scriptFile.exists()) {
                return backendDir
            }
            currentDir = currentDir.parentFile
        }

        // 2. JAR dizini ve üst dizinlerinde "backend/" ara
        try {
            val location = PythonBridge::class.java.protectionDomain?.codeSource?.location
            if (location != null) {
                currentDir = File(location.toURI())
                while (currentDir != null) {
                    val backendDir = File(currentDir, "backend")
                    val scriptFile = File(backendDir, "analyze_bridge.py")
                    if (backendDir.exists() && scriptFile.exists()) {
                        return backendDir
                    }
                    currentDir = currentDir.parentFile
                }
            }
        } catch (_: Exception) {}

        // 3. Son çare: Kullanıcının home altında
        return File(System.getProperty("user.home"), "KuranBridge/backend")
    }

    /**
     * Python process'i başlat ve hazır sinyalini bekle.
     * Zaten başlatılmışsa hiçbir şey yapmaz.
     * @throws RuntimeException python bulunamazsa veya bridge başlamazsa
     */
    @Synchronized
    fun start() {
        if (started.get() && process?.isAlive == true) return

        val backendDir = findBackendDir()
        val bridgeScript = File(backendDir, "analyze_bridge.py")
        if (!bridgeScript.exists()) {
            throw RuntimeException(
                "analyze_bridge.py bulunamadi: ${bridgeScript.absolutePath}\n" +
                "Lutfen backend/ klasorunu uygulama yanina kopyalayin."
            )
        }

        // Sistemdeki python3 / python komutunu bul
        val pythonCmd = listOf("python3", "python").firstOrNull { cmd ->
            runCatching {
                ProcessBuilder(cmd, "--version").start().waitFor() == 0
            }.getOrDefault(false)
        } ?: throw RuntimeException(
            "Python bulunamadi. Lutfen Python 3.x kurun ve PATH'e ekleyin."
        )

        println("[PythonBridge] Baslatiliyor: $pythonCmd ${bridgeScript.absolutePath}")

        val pb = ProcessBuilder(pythonCmd, bridgeScript.absolutePath)
            .directory(backendDir)
        pb.environment()["PYTHONIOENCODING"] = "utf-8"
        pb.environment()["PYTHONUTF8"] = "1"
        
        // Error 5.10: stderr'i dosyaya yönlendir (debug için)
        val errorLog = File(backendDir, "python_error.log")
        pb.redirectError(ProcessBuilder.Redirect.to(errorLog))

        val p = pb.start()
        process = p
        writer  = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
        reader  = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))

        // İlk satır: {"ok":true,"ready":true}
        val r = reader ?: throw RuntimeException("Reader baslatilamadi")
        val firstLine = r.readLine()
            ?: throw RuntimeException("Python bridge hic cikti vermedi. Loglari kontrol edin: ${errorLog.absolutePath}")
        val firstObj = json.parseToJsonElement(firstLine) as? JsonObject
            ?: throw RuntimeException("Python bridge geçersiz JSON döndürdü: $firstLine")
        if (firstObj["ok"]?.jsonPrimitive?.boolean != true) {
            val err = firstObj["error"]?.jsonPrimitive?.content ?: "Bilinmeyen hata"
            throw RuntimeException("Python bridge baslama hatasi: $err")
        }

        started.set(true)
        println("[PythonBridge] Hazir.")

        // JVM kapanınca temizle
        Runtime.getRuntime().addShutdownHook(Thread { stop() })
    }

    /**
     * Ses dosyasını analiz et.
     * @param audioBytes WAV/MP3/OGG/FLAC byte dizisi
     * @param surahName  Sure ismi (JSON'a gömülür)
     */
    suspend fun analyze(
        audioBytes: ByteArray,
        surahName: String
    ): AnalysisResponse = withContext(Dispatchers.IO) {

        // Process canlı değilse yeniden başlat
        if (!started.get() || process?.isAlive != true) {
            started.set(false)
            start()
        }

        // Geçici WAV dosyası: librosa.load path ister
        val tmp = File.createTempFile("kuran_bridge_", ".wav")
        try {
            tmp.writeBytes(audioBytes)

            // Komutu JSON olarak gönder (backslash'ları kaçır)
            val safePath = tmp.absolutePath.replace("\\", "/")
            val safeName = surahName.replace("\"", "'")
            val cmd = """{"cmd":"analyze","path":"$safePath","name":"$safeName"}"""

            mutex.withLock {
                val w = writer ?: throw RuntimeException("Writer kapali")
                val r = reader ?: throw RuntimeException("Reader kapali")

                w.write(cmd)
                w.newLine()
                w.flush()

                // Yanıt satırını oku (tek satır JSON, analiz birkaç dakika sürebilir)
                val responseLine = r.readLine() 
                    ?: throw RuntimeException("Python bridge beklenmedik sekilde kapandi.")
                
                // Parse
                val responseObj = json.parseToJsonElement(responseLine) as? JsonObject
                    ?: throw RuntimeException("Python bridge geçersiz JSON: $responseLine")

                if (responseObj["ok"]?.jsonPrimitive?.boolean != true) {
                    val err = responseObj["error"]?.jsonPrimitive?.content ?: "Bilinmeyen analiz hatasi"
                    throw RuntimeException("Analiz hatasi: $err")
                }

                val dataElement = responseObj["data"]
                    ?: throw RuntimeException("Python bridge 'data' alani eksik")

                return@withContext json.decodeFromJsonElement(AnalysisResponse.serializer(), dataElement)
            }

        } finally {
            if (!tmp.delete()) tmp.deleteOnExit()
        }
    }

    /**
     * Ses dosyasının 3D spektrogramını çıkar (base64 pixel buffer).
     * @param audioBytes WAV byte dizisi
     * @return SpectrogramData — base64 piksel verisi ve metadata
     */
    suspend fun analyzeSpectrogram(
        audioBytes: ByteArray
    ): SpectrogramData = withContext(Dispatchers.IO) {

        if (!started.get() || process?.isAlive != true) {
            started.set(false)
            start()
        }

        val tmp = File.createTempFile("kuran_spec_", ".wav")
        try {
            tmp.writeBytes(audioBytes)

            val safePath = tmp.absolutePath.replace("\\", "/")
            val cmd = """{"cmd":"spectrogram","path":"$safePath"}"""

            mutex.withLock {
                val w = writer ?: throw RuntimeException("Writer kapali")
                val r = reader ?: throw RuntimeException("Reader kapali")

                w.write(cmd)
                w.newLine()
                w.flush()

                val responseLine = r.readLine()
                    ?: throw RuntimeException("Python bridge beklenmedik sekilde kapandi.")

                val responseObj = json.parseToJsonElement(responseLine) as? JsonObject
                    ?: throw RuntimeException("Spectrogram bridge gecersiz JSON: ${responseLine.take(200)}")

                if (responseObj["ok"]?.jsonPrimitive?.boolean != true) {
                    val err = responseObj["error"]?.jsonPrimitive?.content ?: "Bilinmeyen spectrogram hatasi"
                    throw RuntimeException("Spectrogram hatasi: $err")
                }

                val dataElement = responseObj["data"]
                    ?: throw RuntimeException("Spectrogram 'data' alani eksik")

                return@withContext json.decodeFromJsonElement(SpectrogramData.serializer(), dataElement)
            }

        } finally {
            if (!tmp.delete()) tmp.deleteOnExit()
        }
    }

    /** sureler/ klasorundeki dosyalari tara */
    suspend fun surelerTara(surelerKlasor: String = ""): List<com.kuran.android.models.DosyaBilgi> =
        withContext(Dispatchers.IO) {
            if (!started.get() || process?.isAlive != true) { started.set(false); start() }
            val safePath = surelerKlasor.replace("\\", "/")
            val cmdStr = if (safePath.isBlank())
                """{"cmd":"sureler_tara"}"""
            else
                """{"cmd":"sureler_tara","klasor":"$safePath"}"""
            mutex.withLock {
                val w = writer ?: throw RuntimeException("Writer kapali")
                val r = reader ?: throw RuntimeException("Reader kapali")
                w.write(cmdStr); w.newLine(); w.flush()
                val line = r.readLine() ?: throw RuntimeException("Bridge kapandi")
                val obj  = json.parseToJsonElement(line) as? kotlinx.serialization.json.JsonObject
                    ?: throw RuntimeException("Gecersiz JSON")
                if (obj["ok"]?.jsonPrimitive?.boolean != true) {
                    val err = obj["error"]?.jsonPrimitive?.content ?: "Hata"
                    throw RuntimeException("sureler_tara hatasi: $err")
                }
                val dataEl = obj["dosyalar"]
                    ?: return@withLock emptyList<com.kuran.android.models.DosyaBilgi>()
                return@withLock json.decodeFromJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(
                        com.kuran.android.models.DosyaBilgi.serializer()
                    ), dataEl
                )
            }
        }

    /** Ses dosyasini analiz edip tecvid karsilastirmasi yapar */
    suspend fun analizVeKarsilastir(
        dosyaYolu: String,
        name: String,
        sureNo: Int?,
        ayetNo: Int?
    ): AnalysisResponse = withContext(Dispatchers.IO) {
        if (!started.get() || process?.isAlive != true) { started.set(false); start() }
        val safePath = dosyaYolu.replace("\\", "/")
        val safeName = name.replace("\"", "'")
        val extras = buildString {
            if (sureNo != null) append(""","sure_no":$sureNo""")
            if (ayetNo != null) append(""","ayet_no":$ayetNo""")
        }
        val cmdStr = """{"cmd":"analiz_ve_karsilastir","wav_path":"$safePath","name":"$safeName"$extras}"""
        mutex.withLock {
            val w = writer ?: throw RuntimeException("Writer kapali")
            val r = reader ?: throw RuntimeException("Reader kapali")
            w.write(cmdStr); w.newLine(); w.flush()
            val line = withTimeoutOrNull(300.seconds) { r.readLine() }
                ?: throw RuntimeException("Analiz zaman asimi (300s)")
            val obj = json.parseToJsonElement(line) as? kotlinx.serialization.json.JsonObject
                ?: throw RuntimeException("Gecersiz JSON")
            if (obj["ok"]?.jsonPrimitive?.boolean != true) {
                val err = obj["error"]?.jsonPrimitive?.content ?: "Bilinmeyen hata"
                throw RuntimeException("Analiz hatasi: $err")
            }
            val dataEl = obj["data"] ?: throw RuntimeException("data alani eksik")
            return@withLock json.decodeFromJsonElement(AnalysisResponse.serializer(), dataEl)
        }
    }

    /**
     * Metin analizi — dosyadaki metni oku ve metrikleri hesapla
     * @param text Arapça metin
     * @return Analiz sonucu (metrikleri içeriyor)
     */
    suspend fun metinAnaliz(
        text: String
    ): com.kuran.android.models.MetinMetrikleri = withContext(Dispatchers.IO) {
        if (!started.get() || process?.isAlive != true) {
            started.set(false)
            start()
        }

        val safeText = text.replace("\"", "\\\"").replace("\n", "\\n")
        val cmd = """{"cmd":"metin_analiz","text":"$safeText"}"""

        mutex.withLock {
            val w = writer ?: throw RuntimeException("Writer kapali")
            val r = reader ?: throw RuntimeException("Reader kapali")

            w.write(cmd)
            w.newLine()
            w.flush()

            val responseLine = r.readLine()
                ?: throw RuntimeException("Python bridge beklenmedik sekilde kapandi.")

            val responseObj = json.parseToJsonElement(responseLine) as? JsonObject
                ?: throw RuntimeException("Metin analiz gecersiz JSON: ${responseLine.take(200)}")

            if (responseObj["ok"]?.jsonPrimitive?.boolean != true) {
                val err = responseObj["error"]?.jsonPrimitive?.content ?: "Bilinmeyen metin analiz hatasi"
                throw RuntimeException("Metin analiz hatasi: $err")
            }

            val dataElement = responseObj["data"]
                ?: throw RuntimeException("Metin analiz 'data' alani eksik")

            val meticsObj = dataElement as? JsonObject
                ?: throw RuntimeException("Metin analiz data JsonObject değil")

            val metricsEl = meticsObj["metrics"]
                ?: throw RuntimeException("Metin analiz metrics alani eksik")

            return@withLock json.decodeFromJsonElement(
                com.kuran.android.models.MetinMetrikleri.serializer(),
                metricsEl
            )
        }
    }

    /**
     * Python process'e quit komutu gönder ve kapat.
     */
    @Synchronized
    fun stop() {
        if (!started.get()) return
        runCatching {
            writer?.write("""{"cmd":"quit"}""")
            writer?.newLine()
            writer?.flush()
        }
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { process?.destroyForcibly() }
        started.set(false)
        println("[PythonBridge] Kapatildi.")
    }

    /** Ping ile bağlantıyı test et (UI için) */
    @Synchronized
    fun ping(): Boolean {
        if (!started.get() || process?.isAlive != true) return false
        return runCatching {
            val w = writer ?: return false
            val r = reader ?: return false
            w.write("""{"cmd":"ping"}""")
            w.newLine()
            w.flush()
            val line = r.readLine() ?: return false
            val obj = json.parseToJsonElement(line) as? JsonObject ?: return false
            obj["ok"]?.jsonPrimitive?.boolean == true
        }.getOrDefault(false)
    }
}
