package com.kuran.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.render.SpectrogramRenderer
import com.kuran.android.viewmodel.AnalysisUIState
import com.kuran.android.viewmodel.AnalysisViewModel
import com.kuran.android.viewmodel.SpectrogramUIState

/**
 * 3D Spektrogram ekranı — UMAP 3D tarafından bağımsız
 *
 * Akış:
 *  1. Analiz tab'ında kayıt analiz et (Success state)
 *  2. "3D Spektrogram" tab'ına geç
 *  3. "HESAPLA" butonu tıkla
 *  4. Python hesaplama → base64 bitmap
 *  5. Canvas'te göster (interactive pan/zoom + playback cursor)
 */
@Composable
fun SpectrogramScreen(
    viewModel: AnalysisViewModel,
    modifier: Modifier = Modifier
) {
    val analysisState by viewModel.state.collectAsState()
    val spectroState by viewModel.spectrogramState.collectAsState()
    val playbackTime by viewModel.playbackTime.collectAsState()

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF020205))) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Analytics,
                contentDescription = null,
                tint = Color(0xFFE85D24),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "3D Spektrogram — 0 Hz - 4000 Hz",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))

            val canAnalyze = analysisState is AnalysisUIState.Success
            Button(
                onClick = { viewModel.loadSpectrogram() },
                enabled = canAnalyze && spectroState !is SpectrogramUIState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85D24))
            ) {
                Text("HESAPLA")
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (spectroState) {
                is SpectrogramUIState.Idle -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Analiz sonrası 'HESAPLA'",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        if (analysisState !is AnalysisUIState.Success) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Önce Analiz sekmesinde bir kayıt açın.",
                                color = Color.DarkGray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                is SpectrogramUIState.Loading -> {
                    Column(
                        Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFFE85D24))
                        Spacer(Modifier.height(12.dp))
                        Text("Spektrogram hesaplanıyor...", color = Color.White)
                    }
                }

                is SpectrogramUIState.Success -> {
                    val data = (spectroState as SpectrogramUIState.Success).data
                    SpectrogramRenderer(
                        data = data,
                        currentTimeSec = if (playbackTime >= 0f) playbackTime else -1f,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is SpectrogramUIState.Error -> {
                    Text(
                        "Hata: ${(spectroState as SpectrogramUIState.Error).message}",
                        color = Color(0xFFE85D24),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp)
                    )
                }
            }
        }
    }
}
