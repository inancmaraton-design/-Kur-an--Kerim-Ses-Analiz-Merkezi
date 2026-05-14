package com.kuran.android.models

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

data class SurahInfo(
    val number: Int,
    val displayName: String,
    val folderName: String,
    val ayahCount: Int
)

data class AnalysisGroup(
    val id: String,
    val surahName: String,
    val ayahNumber: Int,
    val displayLabel: String,
    val points: List<AudioPoint3D>,
    val color: Color,
    val offset: Offset = Offset.Zero
)

sealed class ComparisonState {
    object Idle : ComparisonState()
    data class Ready(val groups: List<AnalysisGroup>, val queueSize: Int) : ComparisonState()
    data class Analyzing(val currentLabel: String, val groups: List<AnalysisGroup>, val queueSize: Int) : ComparisonState()
    data class Error(val message: String, val groups: List<AnalysisGroup>) : ComparisonState()
}

val GROUP_COLORS = listOf(
    Color(0xFFE85D24),  // Turuncu
    Color(0xFF1D9E75),  // Yeşil
    Color(0xFF4A90D9),  // Mavi
    Color(0xFFD4AC0D),  // Altın
    Color(0xFF9C27B0),  // Mor
    Color(0xFFE91E63),  // Pembe
    Color(0xFF00BCD4),  // Turkuaz
    Color(0xFF8BC34A),  // Açık yeşil
    Color(0xFFFF5722),  // Derin turuncu
    Color(0xFF607D8B)   // Mavi-gri
)
