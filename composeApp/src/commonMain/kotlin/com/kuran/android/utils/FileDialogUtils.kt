package com.kuran.android.utils

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object FileDialogUtils {
    
    fun openTextFileDialog(
        defaultDir: String = "C:\\KP\\data\\quran",
        onFileSelected: (String) -> Unit
    ) {
        val dialog = FileDialog(Frame(), "Metin Dosyası Seç", FileDialog.LOAD)
        dialog.directory = defaultDir
        dialog.file = "*.txt;*.json"
        dialog.isMultipleMode = false
        dialog.isVisible = true

        if (dialog.files.isNotEmpty()) {
            val filePath = dialog.files[0].absolutePath
            onFileSelected(filePath)
        }
    }
    
    fun openAudioFileDialog(
        defaultDir: String = "C:\\KP\\sureler",
        onFilesSelected: (List<File>) -> Unit
    ) {
        val dialog = FileDialog(Frame(), "Ses Dosyaları Seç", FileDialog.LOAD)
        dialog.directory = defaultDir
        dialog.file = "*.mp3;*.wav;*.m4a"
        dialog.isMultipleMode = true
        dialog.isVisible = true
        
        val selectedFiles = dialog.files.toList()
        if (selectedFiles.isNotEmpty()) {
            onFilesSelected(selectedFiles.take(15)) // En fazla 15 dosya
        }
    }
    
    fun openComparisonFileDialog(
        category: String,
        defaultBaseDir: String = "C:\\KP\\data\\karsilastirma",
        onFileSelected: (String) -> Unit
    ) {
        val categoryDir = "$defaultBaseDir\\$category"
        val dialog = FileDialog(Frame(), "Karşılaştırma Dosyası Seç", FileDialog.LOAD)
        dialog.directory = categoryDir
        dialog.file = "*.txt"
        dialog.isMultipleMode = false
        dialog.isVisible = true

        if (dialog.files.isNotEmpty()) {
            val filePath = dialog.files[0].absolutePath
            onFileSelected(filePath)
        }
    }
    
    fun getComparisonCategories(baseDir: String = "C:\\KP\\data\\karsilastirma"): List<String> {
        val dir = File(baseDir)
        return if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
        } else {
            emptyList()
        }
    }
    
    fun getComparisonFiles(category: String, baseDir: String = "C:\\KP\\data\\karsilastirma"): List<String> {
        val categoryDir = File("$baseDir\\$category")
        return if (categoryDir.exists() && categoryDir.isDirectory) {
            categoryDir.listFiles()?.filter { it.extension == "txt" }?.map { it.nameWithoutExtension } ?: emptyList()
        } else {
            emptyList()
        }
    }
}
