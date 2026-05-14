package com.kuran.android.audio

interface AudioRecorder {
    fun startRecording()
    fun stopRecording(onComplete: (ByteArray?) -> Unit)
    val isRecording: Boolean
}

interface AudioPlayer {
    fun play(bytes: ByteArray, onProgress: (Float) -> Unit)
    fun pause()
    fun resume()
    fun stop()
    val isPlaying: Boolean
}

expect class PlatformAudioRecorder() : AudioRecorder
expect class PlatformAudioPlayer() : AudioPlayer

expect fun openFilePicker(onFileSelected: (ByteArray?, String?) -> Unit)
expect fun openMultipleFilePicker(maxFiles: Int = 5, onFilesSelected: (List<Pair<ByteArray, String>>) -> Unit)
