package com.kuran.android.audio

/**
 * Helper to wrap PCM-16 data into a WAV container.
 * Manual byte manipulation for Multiplatform compatibility.
 */
object WavGenerator {
    fun createWavHeader(sampleRate: Int, dataSize: Int): ByteArray {
        val header = ByteArray(44)
        val fileSize = 44 + dataSize - 8
        
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        writeInt(header, 4, fileSize)
        
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        writeInt(header, 16, 16) // Subchunk1Size
        writeShort(header, 20, 1) // AudioFormat (PCM)
        writeShort(header, 22, 1) // NumChannels (Mono)
        writeInt(header, 24, sampleRate) // SampleRate
        writeInt(header, 28, sampleRate * 2) // ByteRate
        writeShort(header, 32, 2) // BlockAlign
        writeShort(header, 34, 16) // BitsPerSample
        
        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        writeInt(header, 40, dataSize)
        
        return header
    }
    
    fun pcm16ToByteArray(samples: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val sample = samples[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
        data[offset + 2] = ((value shr 16) and 0xFF).toByte()
        data[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShort(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
