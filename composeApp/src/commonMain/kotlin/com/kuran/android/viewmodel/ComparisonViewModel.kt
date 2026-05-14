package com.kuran.android.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.kuran.android.models.AnalysisGroup
import com.kuran.android.models.AnalysisResponse
import com.kuran.android.models.AudioPoint3D
import com.kuran.android.models.ComparisonState
import com.kuran.android.models.GROUP_COLORS
import com.kuran.android.models.SurahInfo
import com.kuran.android.models.RecordingEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

class ComparisonViewModel(
    private val analyzeAudio: suspend (ByteArray, String) -> AnalysisResponse,
    private val getSurahs: () -> List<SurahInfo>,
    private val getAyahFile: (SurahInfo, Int) -> File?
) {
    private val _comparisonState = MutableStateFlow<ComparisonState>(ComparisonState.Idle)
    val comparisonState: StateFlow<ComparisonState> = _comparisonState

    private val _selectedSurah = MutableStateFlow<SurahInfo?>(null)
    val selectedSurah: StateFlow<SurahInfo?> = _selectedSurah

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val analyzeMutex = Mutex()
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private data class AnalysisRequest(val surah: SurahInfo, val ayahNumber: Int)

    private val _queue = mutableListOf<AnalysisRequest>()
    private var analyzing = false

    fun selectSurah(surah: SurahInfo) {
        _selectedSurah.value = surah
    }

    fun getAvailableSurahs(): List<SurahInfo> = getSurahs()

    fun addAyahRange(surah: SurahInfo, startAyah: Int, endAyah: Int) {
        val current = _comparisonState.value
        val currentGroupCount = when (current) {
            is ComparisonState.Ready -> current.groups.size
            is ComparisonState.Analyzing -> current.groups.size
            else -> 0
        }
        val availableSlots = 10 - currentGroupCount - _queue.size
        if (availableSlots <= 0) return

        val clampedEnd = minOf(endAyah, startAyah + availableSlots - 1, surah.ayahCount)
        for (ayahNum in startAyah..clampedEnd) {
            _queue.add(AnalysisRequest(surah, ayahNum))
        }
        if (!analyzing) {
            processNextInQueue()
        }
    }

    fun addAyahToAnalysis(surah: SurahInfo, ayahNumber: Int) {
        val id = "%03d%03d".format(surah.number, ayahNumber)

        val current = _comparisonState.value
        if (current is ComparisonState.Ready || current is ComparisonState.Analyzing) {
            val currentGroups = when (current) {
                is ComparisonState.Ready -> current.groups
                is ComparisonState.Analyzing -> current.groups
                else -> emptyList()
            }
            val groupExists = currentGroups.any { it.id == id }
            if (groupExists) {
                removeGroup(id)
                return
            }
        }

        val groupCount = when (current) {
            is ComparisonState.Ready -> current.groups.size
            is ComparisonState.Analyzing -> current.groups.size
            else -> 0
        }
        if (groupCount >= 10) return

        _queue.add(AnalysisRequest(surah, ayahNumber))
        if (!analyzing) {
            processNextInQueue()
        }
    }

    fun removeGroup(id: String) {
        val current = _comparisonState.value
        val newGroups = when (current) {
            is ComparisonState.Ready -> current.groups.filter { it.id != id }
            is ComparisonState.Analyzing -> current.groups.filter { it.id != id }
            else -> return
        }
        when (current) {
            is ComparisonState.Ready -> _comparisonState.value = ComparisonState.Ready(newGroups, _queue.size)
            is ComparisonState.Analyzing -> _comparisonState.value = ComparisonState.Analyzing(current.currentLabel, newGroups, _queue.size)
            else -> {}
        }
    }

    fun resetGroupOffset(id: String) {
        updateGroupOffset(id, Offset.Zero)  // set to zero
    }

    fun updateGroupOffset(id: String, newOffset: Offset) {
        val current = _comparisonState.value
        val updatedGroups = when (current) {
            is ComparisonState.Ready -> current.groups.map {
                if (it.id == id) it.copy(offset = newOffset) else it
            }
            is ComparisonState.Analyzing -> current.groups.map {
                if (it.id == id) it.copy(offset = newOffset) else it
            }
            else -> return
        }
        when (current) {
            is ComparisonState.Ready -> _comparisonState.value = ComparisonState.Ready(updatedGroups, _queue.size)
            is ComparisonState.Analyzing -> _comparisonState.value = ComparisonState.Analyzing(current.currentLabel, updatedGroups, _queue.size)
            else -> {}
        }
    }

    fun clearAll() {
        _queue.clear()
        _comparisonState.value = ComparisonState.Idle
        analyzing = false
    }

    fun addSavedRecordingToComparison(recordingEntry: RecordingEntry) {
        val current = _comparisonState.value
        val currentGroups = when (current) {
            is ComparisonState.Ready -> current.groups
            is ComparisonState.Analyzing -> current.groups
            else -> emptyList()
        }

        val groupCount = currentGroups.size
        if (groupCount >= 10) return

        scope.launch {
            try {
                val jsonText = recordingEntry.jsonFile.readText()
                val response = json.decodeFromString<AnalysisResponse>(jsonText)

                val audioPoints = response.umap3d.map { point ->
                    AudioPoint3D(
                        x = point.x,
                        y = point.y,
                        z = point.z,
                        timeSec = point.timeSec,
                        f0 = point.f0,
                        label = point.label,
                        r = parseColorR(point.color),
                        g = parseColorG(point.color),
                        b = parseColorB(point.color)
                    )
                }

                val color = GROUP_COLORS[currentGroups.size % GROUP_COLORS.size]
                val id = "recording_${recordingEntry.name}"
                val group = AnalysisGroup(
                    id = id,
                    surahName = recordingEntry.displayName,
                    ayahNumber = 0,
                    displayLabel = recordingEntry.displayName,
                    points = audioPoints,
                    color = color
                )

                val updatedGroups = currentGroups + group
                when (current) {
                    is ComparisonState.Ready -> _comparisonState.value = ComparisonState.Ready(updatedGroups, _queue.size)
                    is ComparisonState.Analyzing -> _comparisonState.value = ComparisonState.Analyzing(current.currentLabel, updatedGroups, _queue.size)
                    else -> _comparisonState.value = ComparisonState.Ready(updatedGroups, _queue.size)
                }
            } catch (e: Exception) {
                val groups = ((_comparisonState.value as? ComparisonState.Ready)?.groups
                    ?: (_comparisonState.value as? ComparisonState.Analyzing)?.groups
                    ?: emptyList())
                _comparisonState.value = ComparisonState.Error("Kayit yuklenemedi: ${e.message}", groups)
            }
        }
    }

    private fun processNextInQueue() {
        scope.launch {
            while (_queue.isNotEmpty()) {
                val request = _queue.removeAt(0)
                analyzing = true

                try {
                    val currentLabel = "${request.surah.displayName} - ${request.ayahNumber}. Ayet"
                    val groups = ((_comparisonState.value as? ComparisonState.Ready)?.groups
                        ?: (_comparisonState.value as? ComparisonState.Analyzing)?.groups
                        ?: emptyList())
                    _comparisonState.value = ComparisonState.Analyzing(currentLabel, groups, _queue.size)

                    val file = getAyahFile(request.surah, request.ayahNumber)
                    if (file == null) {
                        _comparisonState.value = ComparisonState.Error("Dosya bulunamadı", groups)
                        analyzing = false
                        return@launch
                    }

                    val bytes = file.readBytes()
                    val response = analyzeMutex.withLock {
                        analyzeAudio(bytes, request.surah.displayName)
                    }

                    val audioPoints = response.umap3d.map { point ->
                        AudioPoint3D(
                            x = point.x,
                            y = point.y,
                            z = point.z,
                            timeSec = point.timeSec,
                            f0 = point.f0,
                            label = point.label,
                            r = parseColorR(point.color),
                            g = parseColorG(point.color),
                            b = parseColorB(point.color)
                        )
                    }

                    val color = GROUP_COLORS[groups.size % GROUP_COLORS.size]
                    val id = "%03d%03d".format(request.surah.number, request.ayahNumber)
                    val group = AnalysisGroup(
                        id = id,
                        surahName = request.surah.displayName,
                        ayahNumber = request.ayahNumber,
                        displayLabel = currentLabel,
                        points = audioPoints,
                        color = color
                    )

                    val updatedGroups = groups + group
                    _comparisonState.value = ComparisonState.Ready(updatedGroups, _queue.size)
                } catch (e: Exception) {
                    val groups = ((_comparisonState.value as? ComparisonState.Ready)?.groups
                        ?: (_comparisonState.value as? ComparisonState.Analyzing)?.groups
                        ?: emptyList())
                    _comparisonState.value = ComparisonState.Error("Hata: ${e.message}", groups)
                }

                analyzing = false
            }
        }
    }

    private fun parseColorR(hexColor: String): Float {
        return try {
            val hex = hexColor.removePrefix("#")
            Integer.parseInt(hex.substring(0, 2), 16) / 255f
        } catch (e: Exception) {
            1f
        }
    }

    private fun parseColorG(hexColor: String): Float {
        return try {
            val hex = hexColor.removePrefix("#")
            Integer.parseInt(hex.substring(2, 4), 16) / 255f
        } catch (e: Exception) {
            1f
        }
    }

    private fun parseColorB(hexColor: String): Float {
        return try {
            val hex = hexColor.removePrefix("#")
            Integer.parseInt(hex.substring(4, 6), 16) / 255f
        } catch (e: Exception) {
            1f
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
