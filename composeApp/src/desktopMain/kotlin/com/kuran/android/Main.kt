package com.kuran.android

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kuran.android.analysis.PythonBridge
import com.kuran.android.audio.PlatformAudioRecorder
import com.kuran.android.audio.PlatformAudioPlayer
import com.kuran.android.data.SurahRepository
import com.kuran.android.viewmodel.AnalysisViewModel
import com.kuran.android.viewmodel.ComparisonViewModel

fun main() = application {
    // GÖREV 7: Türkçe karakter / encoding sorunu
    System.setProperty("file.encoding", "UTF-8")
    System.setProperty("sun.stdout.encoding", "UTF-8")
    System.setProperty("sun.stderr.encoding", "UTF-8")

    val recorder  = PlatformAudioRecorder()
    val player    = PlatformAudioPlayer()

    SurahRepository.initialize()
    val surahs = SurahRepository.getSurahs()

    // Sureler klasoru — uygulama yaninda
    val surelerDir = run {
        val candidates = listOf(
            java.io.File("sureler"),
            java.io.File("../sureler"),
            java.io.File(System.getProperty("user.dir"), "sureler")
        )
        candidates.firstOrNull { it.exists() }?.absolutePath ?: "sureler"
    }

    val viewModel = AnalysisViewModel(
        recorder     = recorder,
        player       = player,
        analyzeAudio = { bytes, name ->
            if (!PythonBridge.isStarted) PythonBridge.start()
            PythonBridge.analyze(bytes, name)
        },
        analyzeSpectrogram = { bytes ->
            if (!PythonBridge.isStarted) PythonBridge.start()
            PythonBridge.analyzeSpectrogram(bytes)
        },
        getSurahs    = { surahs },
        getAyahFile  = { surah, ayahNum -> SurahRepository.getAyahFile(surah, ayahNum) }
    )

    // Metin analiz callback'i set et
    viewModel.metinAnaliz = { metin ->
        if (!PythonBridge.isStarted) PythonBridge.start()
        PythonBridge.metinAnaliz(metin)
    }

    // Uygulama acilinca sureler/ klasorunu tara
    viewModel.surelerTara(
        analyzeWithBridge = { klasor ->
            if (!PythonBridge.isStarted) PythonBridge.start()
            PythonBridge.surelerTara(klasor)
        },
        surelerKlasor = surelerDir
    )

    val comparisonViewModel = ComparisonViewModel(
        analyzeAudio = { bytes, name ->
            if (!PythonBridge.isStarted) PythonBridge.start()
            PythonBridge.analyze(bytes, name)
        },
        getSurahs   = { surahs },
        getAyahFile = { surah, ayahNum -> SurahRepository.getAyahFile(surah, ayahNum) }
    )

    Window(
        onCloseRequest = {
            viewModel.clear()
            comparisonViewModel.dispose()
            PythonBridge.stop()
            exitApplication()
        },
        title = "Kuran-ı Kerim Analiz Merkezi"
    ) {
        App(viewModel)
    }
}
