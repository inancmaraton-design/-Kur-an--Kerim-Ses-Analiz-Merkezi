package com.kuran.android

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.models.RecordingEntry
import kotlinx.coroutines.launch

@Composable
fun RecordingsPanel(
    recordings: List<RecordingEntry>,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (RecordingEntry) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0D0D22), Color(0xFF080815))
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF9C27B0).copy(0.4f), Color(0xFF3B1A60).copy(0.2f))
                ),
                shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Başlık ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF9C27B0).copy(0.15f))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LibraryMusic,
                    null,
                    tint = Color(0xFF9C27B0),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "KAYITLARIM",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "${recordings.size} kayıt",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                // Yenile
                IconButton(onClick = onRefresh, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = Color(0xFF9C27B0), modifier = Modifier.size(18.dp))
                }
                // Kapat
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            HorizontalDivider(color = Color(0xFF9C27B0).copy(0.2f))

            // ── Kayıt Listesi ──
            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FolderOpen,
                            null,
                            tint = Color.Gray.copy(0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Kayıt bulunamadı", color = Color.Gray, fontSize = 14.sp)
                        Text(
                            "Belgeler/KuranAnaliz/",
                            color = Color.DarkGray,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    RecordingListWithScroll(
                        recordings = recordings,
                        onSelect = onSelect
                    )
                }
            }
        }
    }
}

@Composable
fun RecordingItem(entry: RecordingEntry, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        color = if (hovered) Color(0xFF9C27B0).copy(0.2f) else Color.White.copy(0.04f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (hovered) Color(0xFF9C27B0).copy(0.5f) else Color.White.copy(0.06f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onHover { hovered = it }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // İkon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF9C27B0).copy(0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    null,
                    tint = Color(0xFF9C27B0),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // Bilgi
            Column(Modifier.weight(1f)) {
                Text(
                    entry.displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(entry.dateStr, color = Color.Gray, fontSize = 10.sp)
                    Text("•", color = Color.DarkGray, fontSize = 10.sp)
                    Text("${entry.fileSizeKb} KB", color = Color.DarkGray, fontSize = 10.sp)
                }
            }

            // Ok
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Color(0xFF9C27B0).copy(if (hovered) 1f else 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RecordingListWithScroll(
    recordings: List<RecordingEntry>,
    onSelect: (RecordingEntry) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = 12.dp),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(recordings) { entry ->
                RecordingItem(entry = entry, onClick = { onSelect(entry) })
            }
        }

        // Native Desktop Scrollbar
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(scrollState = listState)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (canScrollUp) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF9C27B0).copy(0.4f), RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Yukarıya kaydır",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (canScrollDown) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                        }
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF9C27B0).copy(0.4f), RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Aşağıya kaydır",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// Hover extension (Compose Desktop için)
fun Modifier.onHover(onHover: (Boolean) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                when (event.type) {
                    PointerEventType.Enter -> onHover(true)
                    PointerEventType.Exit -> onHover(false)
                    else -> {}
                }
            }
        }
    }
