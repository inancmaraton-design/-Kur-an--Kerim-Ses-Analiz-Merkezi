package com.kuran.android.audio

import kotlinx.coroutines.*
import javax.sound.sampled.*
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter
import java.io.File

// ─────────────────────────────────────────────────────────────────────────────
// Recorder
// ─────────────────────────────────────────────────────────────────────────────
actual class PlatformAudioRecorder actual constructor() : AudioRecorder {
    private var _isRecording = false
    override val isRecording: Boolean get() = _isRecording
    private var job: Job? = null
    private var scope: CoroutineScope? = null
    @Volatile
    private var capturedBytes: ByteArray? = null

    override fun startRecording() {
        if (_isRecording) return
        _isRecording = true
        val s = CoroutineScope(Dispatchers.IO + Job())
        scope = s
        job = s.launch {
            val format = AudioFormat(22050f, 16, 1, true, false)
            val info = DataLine.Info(TargetDataLine::class.java, format)
            if (!AudioSystem.isLineSupported(info)) { _isRecording = false; return@launch }
            val line = AudioSystem.getLine(info) as TargetDataLine
            line.open(format); line.start()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            try {
                while (isActive && _isRecording) {
                    val read = line.read(buffer, 0, buffer.size)
                    if (read > 0) out.write(buffer, 0, read)
                }
            } finally { line.stop(); line.close() }
            val pcmData = out.toByteArray()
            val header = WavGenerator.createWavHeader(22050, pcmData.size)
            capturedBytes = header + pcmData
        }
    }

    override fun stopRecording(onComplete: (ByteArray?) -> Unit) {
        _isRecording = false
        val s = scope
        val j = job
        GlobalScope.launch(Dispatchers.IO) { // stopRecording should survive the local scope cancel
            j?.join()
            withContext(Dispatchers.Main) {
                onComplete(capturedBytes)
                capturedBytes = null
            }
            s?.cancel()
        }
        scope = null
        job = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Player — event-driven, ontimeupdate benzeri LineListener yaklaşımı
// ─────────────────────────────────────────────────────────────────────────────
actual class PlatformAudioPlayer actual constructor() : AudioPlayer {
    private val _playing = AtomicBoolean(false)
    private val _paused = AtomicBoolean(false)
    override val isPlaying: Boolean get() = _playing.get() && !_paused.get()

    private var line: SourceDataLine? = null
    private var playJob: Job? = null
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun play(bytes: ByteArray, onProgress: (Float) -> Unit) {
        stop()

        playJob = ioScope.launch {
            var rawAis: javax.sound.sampled.AudioInputStream? = null
            var ais: javax.sound.sampled.AudioInputStream? = null
            try {
                _playing.set(true)
                _paused.set(false)
                println("[AudioPlayer] ${bytes.size} byte yukleniyor...")

                val bis = ByteArrayInputStream(bytes)
                rawAis = AudioSystem.getAudioInputStream(bis)

                val baseFormat = rawAis.format
                val targetFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.sampleRate,
                    16,
                    baseFormat.channels,
                    baseFormat.channels * 2,
                    baseFormat.sampleRate,
                    false
                )

                ais = if (AudioSystem.isConversionSupported(targetFormat, baseFormat)) {
                    AudioSystem.getAudioInputStream(targetFormat, rawAis)
                } else {
                    rawAis
                }

                val info = DataLine.Info(SourceDataLine::class.java, targetFormat)
                val newLine = AudioSystem.getLine(info) as SourceDataLine
                newLine.open(targetFormat)
                newLine.start()
                line = newLine

                val buffer = ByteArray(2048)
                var bytesRead = 0
                val frameSize = targetFormat.frameSize
                val sampleRate = targetFormat.sampleRate
                val totalFrames = ais.frameLength

                var totalBytesWritten: Long = 0

                while (isActive && _playing.get() && bytesRead != -1) {
                    if (_paused.get()) {
                        delay(50)
                        continue
                    }

                    bytesRead = ais.read(buffer, 0, buffer.size)
                    if (bytesRead > 0) {
                        newLine.write(buffer, 0, bytesRead)
                        totalBytesWritten += bytesRead

                        // Tam donanımsal akustik senkronizasyon için buffer'a yazılanı değil,
                        // ses kartından çıkan anlık (playhead) pozisyonunu kullanıyoruz.
                        val timeSec = newLine.microsecondPosition / 1_000_000f

                        withContext(Dispatchers.Main) {
                            onProgress(timeSec)
                        }
                    }
                }

                if (isActive && _playing.get() && bytesRead == -1) {
                    newLine.drain()
                    val durationSec = if (totalFrames != AudioSystem.NOT_SPECIFIED.toLong()) {
                        totalFrames.toFloat() / sampleRate
                    } else {
                        (totalBytesWritten / frameSize).toFloat() / sampleRate
                    }
                    withContext(Dispatchers.Main) {
                        onProgress(durationSec)
                    }
                }

                _playing.set(false)
                _paused.set(false)
                newLine.stop()
                newLine.close()

            } catch (e: Exception) {
                println("[AudioPlayer] HATA: ${e.message}")
                e.printStackTrace()
                _playing.set(false)
                _paused.set(false)
            } finally {
                rawAis?.close()
                if (ais != rawAis) ais?.close()
                line?.close()
            }
        }
    }

    override fun pause() {
        if (_playing.get() && !_paused.get()) {
            _paused.set(true)
            line?.stop()
        }
    }

    override fun resume() {
        if (_playing.get() && _paused.get()) {
            _paused.set(false)
            line?.start()
        }
    }

    override fun stop() {
        _playing.set(false)
        _paused.set(false)
        playJob?.cancel()
        playJob = null
        try {
            line?.stop()
            line?.close()
        } catch (_: Exception) {}
        line = null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// File Picker
// ─────────────────────────────────────────────────────────────────────────────
actual fun openFilePicker(onFileSelected: (ByteArray?, String?) -> Unit) {
    val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Ses Dosyasi Sec", java.awt.FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".wav", true) || name.endsWith(".mp3", true) ||
        name.endsWith(".ogg", true) || name.endsWith(".flac", true)
    }
    val userDir = System.getProperty("user.dir")
    var defaultDir = File(userDir, "sureler")
    if (!defaultDir.exists()) {
        val parent = File(userDir).parentFile
        if (parent != null && File(parent, "sureler").exists()) {
            defaultDir = File(parent, "sureler")
        }
    }
    
    if (defaultDir.exists()) {
        println("[openFilePicker] Varsayılan klasör: ${defaultDir.absolutePath}")
        dialog.directory = defaultDir.absolutePath
    }
    dialog.isVisible = true
    if (dialog.file != null) {
        val file = java.io.File(dialog.directory, dialog.file)
        try {
            println("Dosya secildi: ${file.name} (${file.length()} byte)")
            onFileSelected(file.readBytes(), file.name)
        } catch (e: Exception) {
            println("Dosya okuma hatasi: ${e.message}")
            onFileSelected(null, null)
        }
    } else {
        onFileSelected(null, null)
    }
}

actual fun openMultipleFilePicker(maxFiles: Int, onFilesSelected: (List<Pair<ByteArray, String>>) -> Unit) {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.FILES_ONLY
    chooser.isMultiSelectionEnabled = true
    chooser.fileFilter = object : FileFilter() {
        override fun accept(f: File): Boolean {
            return f.isDirectory || f.name.endsWith(".wav", true) ||
                   f.name.endsWith(".mp3", true) || f.name.endsWith(".ogg", true) ||
                   f.name.endsWith(".flac", true)
        }
        override fun getDescription() = "Ses Dosyaları (WAV, MP3, OGG, FLAC)"
    }
    val userDir = System.getProperty("user.dir")
    var defaultDir = File(userDir, "sureler")
    if (!defaultDir.exists()) {
        val parent = File(userDir).parentFile
        if (parent != null && File(parent, "sureler").exists()) {
            defaultDir = File(parent, "sureler")
        }
    }

    if (defaultDir.exists()) {
        println("[openMultipleFilePicker] Varsayılan klasör: ${defaultDir.absolutePath}")
        chooser.currentDirectory = defaultDir
    }

    val result = chooser.showOpenDialog(null)
    if (result == JFileChooser.APPROVE_OPTION) {
        val selectedFiles = chooser.selectedFiles.take(maxFiles)
        val filesData = mutableListOf<Pair<ByteArray, String>>()

        for (file in selectedFiles) {
            try {
                println("Dosya okutuldu: ${file.name} (${file.length()} byte)")
                filesData.add(file.readBytes() to file.name)
            } catch (e: Exception) {
                println("Dosya okuma hatasi (${file.name}): ${e.message}")
            }
        }

        if (filesData.isNotEmpty()) {
            onFilesSelected(filesData)
        } else {
            onFilesSelected(emptyList())
        }
    } else {
        onFilesSelected(emptyList())
    }
}
