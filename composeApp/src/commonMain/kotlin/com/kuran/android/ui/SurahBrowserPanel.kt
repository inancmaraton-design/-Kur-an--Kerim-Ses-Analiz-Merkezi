package com.kuran.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.models.AnalysisGroup
import com.kuran.android.models.ComparisonState
import com.kuran.android.models.SurahInfo
import com.kuran.android.viewmodel.ComparisonViewModel
import com.kuran.android.models.RecordingEntry
import kotlinx.coroutines.launch

@Composable
fun SurahBrowserPanel(
    viewModel: ComparisonViewModel,
    comparisonState: ComparisonState,
    availableRecordings: List<RecordingEntry>,
    onAddSavedRecording: (RecordingEntry) -> Unit,
    onDeleteAllRecordings: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(comparisonState) {
        when (comparisonState) {
            is ComparisonState.Ready -> comparisonState.groups
            is ComparisonState.Analyzing -> comparisonState.groups
            else -> emptyList()
        }
    }

    val groupCount = groups.size

    Column(
        modifier = modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(Color(0xFFF5F5F5))
            .padding(8.dp)
    ) {
        AnalyzedGroupsList(
            groups = groups,
            viewModel = viewModel,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onClearAll,
            enabled = groupCount > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Analizleri Temizle")
        }
    }
}

@Composable
private fun AnalyzedGroupsList(
    groups: List<AnalysisGroup>,
    viewModel: ComparisonViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.Black.copy(0.1f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Text(
            "ANALİZ EDİLDİ (${groups.size})",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black.copy(0.7f),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Kayıttan seçin",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    items(groups) { group ->
                        AnalyzedGroupItem(
                            group = group,
                            onRemove = { viewModel.removeGroup(group.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyzedGroupItem(
    group: AnalysisGroup,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(group.color.copy(0.15f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(group.color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    group.surahName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Ayet ${group.ayahNumber}",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Kaldır",
                tint = Color.Red.copy(0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SavedRecordingsList(
    recordings: List<RecordingEntry>,
    onSelectRecording: (RecordingEntry) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, Color.Black.copy(0.1f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "KAYITLI DOSYALAR (${recordings.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(0.7f)
            )
            if (recordings.isNotEmpty()) {
                IconButton(
                    onClick = onDeleteAll,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Tümünü sil",
                        tint = Color.Red.copy(0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Kayıtlı dosya yok",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    items(recordings) { recording ->
                        SavedRecordingItem(
                            recording = recording,
                            onSelect = { onSelectRecording(recording) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedRecordingItem(
    recording: RecordingEntry,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .background(Color(0xFF9C27B0).copy(0.15f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                recording.displayName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "${recording.dateStr} • ${recording.fileSizeKb}KB",
                fontSize = 9.sp,
                color = Color.Gray
            )
        }

        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "Karşılaştırmaya ekle",
            tint = Color(0xFF9C27B0).copy(0.7f),
            modifier = Modifier.size(16.dp)
        )
    }
}
