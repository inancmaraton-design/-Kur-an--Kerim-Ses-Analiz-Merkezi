package com.kuran.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.models.ComparisonState
import com.kuran.android.render.ComparisonRenderer
import com.kuran.android.viewmodel.ComparisonViewModel
import com.kuran.android.models.RecordingEntry
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ComparisonScreen(
    comparisonViewModel: ComparisonViewModel,
    availableRecordings: StateFlow<List<RecordingEntry>>,
    onDeleteAllRecordings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val comparisonState by comparisonViewModel.comparisonState.collectAsState()
    val recordings by availableRecordings.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // Üst banner: Loading, Error, veya başlık
        when (comparisonState) {
            is ComparisonState.Analyzing -> {
                val state = comparisonState as ComparisonState.Analyzing
                Surface(
                    color = Color(0xFFE3F2FD),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Analiz ediliyor: ${state.currentLabel}",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }
            is ComparisonState.Error -> {
                val state = comparisonState as ComparisonState.Error
                Surface(
                    color = Color(0xFFFFEBEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        state.message,
                        fontSize = 12.sp,
                        color = Color(0xFFB71C1C),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            else -> {
                Surface(
                    color = Color(0xFFF5F5F5),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Karşılaştırmalı 3D Analiz",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Ana içerik: Row(Panel + Renderer)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            SurahBrowserPanel(
                viewModel = comparisonViewModel,
                comparisonState = comparisonState,
                availableRecordings = recordings,
                onAddSavedRecording = { recording ->
                    comparisonViewModel.addSavedRecordingToComparison(recording)
                },
                onDeleteAllRecordings = onDeleteAllRecordings,
                onClearAll = {
                    comparisonViewModel.clearAll()
                }
            )

            // Renderer
            when (comparisonState) {
                is ComparisonState.Idle -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Kayıttan seçin",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
                is ComparisonState.Ready -> {
                    val state = comparisonState as ComparisonState.Ready
                    ComparisonRenderer(
                        groups = state.groups,
                        onUpdateGroupOffset = { id, delta ->
                            val current = (state.groups.find { it.id == id }?.offset ?: return@ComparisonRenderer)
                            comparisonViewModel.updateGroupOffset(id, current + delta)
                        },
                        onResetGroupOffset = { id ->
                            comparisonViewModel.resetGroupOffset(id)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                is ComparisonState.Analyzing -> {
                    val state = comparisonState as ComparisonState.Analyzing
                    ComparisonRenderer(
                        groups = state.groups,
                        onUpdateGroupOffset = { id, delta ->
                            val current = (state.groups.find { it.id == id }?.offset ?: return@ComparisonRenderer)
                            comparisonViewModel.updateGroupOffset(id, current + delta)
                        },
                        onResetGroupOffset = { id ->
                            comparisonViewModel.resetGroupOffset(id)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                is ComparisonState.Error -> {
                    val state = comparisonState as ComparisonState.Error
                    ComparisonRenderer(
                        groups = state.groups,
                        onUpdateGroupOffset = { id, delta ->
                            val current = (state.groups.find { it.id == id }?.offset ?: return@ComparisonRenderer)
                            comparisonViewModel.updateGroupOffset(id, current + delta)
                        },
                        onResetGroupOffset = { id ->
                            comparisonViewModel.resetGroupOffset(id)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
