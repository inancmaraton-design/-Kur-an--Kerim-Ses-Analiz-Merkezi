package com.kuran.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.models.DosyaBilgi
import com.kuran.android.models.KarsilastirmaResult
import com.kuran.android.ui.ComparisonScreen
import com.kuran.android.ui.SpectrogramScreen
import com.kuran.android.viewmodel.ComparisonViewModel
import com.kuran.android.viewmodel.AnalysisViewModel

enum class AppScreen {
    ANALYSIS, COMPARISON, SPECTROGRAM, SURELER
}

@Composable
fun AppShell(
    analysisViewModel: AnalysisViewModel,
    comparisonViewModel: ComparisonViewModel,
    onAnalizEt: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf(AppScreen.ANALYSIS) }

    Column(modifier = modifier.fillMaxSize()) {
        // Tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color(0xFF0F0F24), Color(0xFF080815))
                    )
                )
                .padding(12.dp)
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TabButton(
                label = "Analiz",
                isSelected = currentScreen == AppScreen.ANALYSIS,
                onClick = { currentScreen = AppScreen.ANALYSIS }
            )
            TabButton(
                label = "Karşılaştırma",
                isSelected = currentScreen == AppScreen.COMPARISON,
                onClick = { currentScreen = AppScreen.COMPARISON }
            )
            TabButton(
                label = "Sureler",
                isSelected = currentScreen == AppScreen.SURELER,
                onClick = { currentScreen = AppScreen.SURELER }
            )
        }

        // Content
        when (currentScreen) {
            AppScreen.ANALYSIS -> App(analysisViewModel)
            AppScreen.COMPARISON -> ComparisonScreen(
                comparisonViewModel = comparisonViewModel,
                availableRecordings = analysisViewModel.availableRecordings,
                onDeleteAllRecordings = { analysisViewModel.deleteAllRecordings() }
            )
            AppScreen.SPECTROGRAM -> SpectrogramScreen(analysisViewModel)
            AppScreen.SURELER     -> SureListesiEkrani(
                viewModel   = analysisViewModel,
                onAnalizEt  = onAnalizEt ?: {}
            )
        }
    }
}

@Composable
private fun TabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .background(
                color = if (isSelected) Color(0xFFE85D24).copy(0.2f) else Color.White.copy(0.05f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFFE85D24) else Color.White.copy(0.1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            color = if (isSelected) Color(0xFFE85D24) else Color.White.copy(0.7f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sureler Ekrani — Sol liste + Sag sonuc paneli
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SureListesiEkrani(
    viewModel: AnalysisViewModel,
    onAnalizEt: () -> Unit
) {
    val dosyaListesi     by viewModel.dosyaListesi.collectAsState()
    val seciliDosya      by viewModel.seciliDosya.collectAsState()
    val yukleniyor       by viewModel.surelerYukleniyor.collectAsState()
    val analiz           by viewModel.state.collectAsState()
    val karsilastirma    by viewModel.karsilastirma.collectAsState()

    val bg = Color(0xFF080815)

    Row(
        modifier = Modifier.fillMaxSize().background(bg)
    ) {
        // Sol panel: Dosya listesi
        Surface(
            color = Color(0xFF0D0D1E),
            modifier = Modifier.width(280.dp).fillMaxHeight()
        ) {
            Column(Modifier.fillMaxSize()) {
                // Baslik + yenile
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sureler (${dosyaListesi.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (yukleniyor) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF1D9E75))
                    }
                }
                HorizontalDivider(color = Color.White.copy(0.07f))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(dosyaListesi) { dosya ->
                        val secili = dosya.tam_yol == seciliDosya?.tam_yol
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (secili) Color(0xFF1D9E75).copy(0.15f) else Color.Transparent)
                                .clickable { viewModel.dosyaSec(dosya) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dosya.sure_isim ?: dosya.dosya_adi,
                                    color = if (secili) Color(0xFF1D9E75) else Color.White,
                                    fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${dosya.format}  ${dosya.boyut_mb} MB",
                                    color = Color.Gray, fontSize = 10.sp
                                )
                            }
                            if (!dosya.sure_isim_ar.isNullOrBlank()) {
                                Text(
                                    dosya.sure_isim_ar,
                                    color = if (secili) Color(0xFF1D9E75) else Color.White.copy(0.6f),
                                    fontSize = 15.sp, textAlign = TextAlign.End,
                                    modifier = Modifier.width(50.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color.White.copy(0.04f))
                    }
                }
            }
        }

        // Sag panel: Secili dosya detayi + analiz sonucu
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (seciliDosya == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\u0627\u0644\u0642\u0631\u0622\u0646", fontSize = 80.sp, color = Color(0xFF1D9E75).copy(0.3f))
                        Spacer(Modifier.height(12.dp))
                        Text("Sol listeden bir sure secin", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                val dosya = seciliDosya!!
                // Dosya bilgi karti
                Surface(color = Color(0xFF0D0D1E), shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    dosya.sure_isim ?: dosya.dosya_adi,
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp
                                )
                                Text(
                                    "${dosya.dosya_adi}  |  ${dosya.format}  |  ${dosya.boyut_mb} MB",
                                    color = Color.Gray, fontSize = 11.sp
                                )
                            }
                            if (!dosya.sure_isim_ar.isNullOrBlank()) {
                                Text(dosya.sure_isim_ar, fontSize = 28.sp, color = Color(0xFF1D9E75))
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val analizYukleniyor = analiz is com.kuran.android.viewmodel.AnalysisUIState.Loading
                        Button(
                            onClick = { onAnalizEt() },
                            enabled = !analizYukleniyor,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D9E75)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (analizYukleniyor) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Text("Analiz yapiliyor...", color = Color.White)
                            } else {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Analiz Et", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Hata goster
                if (analiz is com.kuran.android.viewmodel.AnalysisUIState.Error) {
                    val hata = (analiz as com.kuran.android.viewmodel.AnalysisUIState.Error).message
                    Surface(color = Color(0xFFA32D2D).copy(0.2f), shape = RoundedCornerShape(10.dp)) {
                        Text(hata, color = Color(0xFFE57373), modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                    }
                }

                // Tecvid karsilastirma sonucu
                if (karsilastirma != null) {
                    TecvidSonucPanel(karsilastirma!!)
                } else if (analiz is com.kuran.android.viewmodel.AnalysisUIState.Success) {
                    Surface(color = Color(0xFF0D0D1E), shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            val success = analiz as com.kuran.android.viewmodel.AnalysisUIState.Success
                            Text("Analiz tamamlandi", color = Color(0xFF1D9E75), fontWeight = FontWeight.Bold)
                            Text("${success.points.size} nokta  |  ${success.totalDuration.toInt()}s",
                                color = Color.Gray, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            Text("Tecvid karsilastirmasi bu sure icin veritabaninda mevcut degil.",
                                color = Color.Gray.copy(0.7f), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TecvidSonucPanel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TecvidSonucPanel(karsilastirma: KarsilastirmaResult) {
    Surface(color = Color(0xFF0D0D1E), shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()) {
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (karsilastirma.ayet_metni.isNotBlank()) {
                    Text(karsilastirma.ayet_metni, color = Color.White, fontSize = 18.sp,
                        textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkorKutu("Genel",  karsilastirma.genel_skor,           Color(0xFF1D9E75))
                    SkorKutu("Dogru",  karsilastirma.dogru_sayisi.toDouble(),  Color(0xFF4A90D9))
                    SkorKutu("Yanlis", karsilastirma.yanlis_sayisi.toDouble(), Color(0xFFA32D2D))
                    SkorKutu("Kismi",  karsilastirma.kismi_sayisi.toDouble(),  Color(0xFFD4AC0D))
                }
            }
            if (karsilastirma.iyi_yapilan.isNotEmpty()) {
                item {
                    Text("Iyi Yapilanlar", color = Color(0xFF1D9E75), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    karsilastirma.iyi_yapilan.forEach { Text("  - $it", color = Color.White.copy(0.8f), fontSize = 11.sp) }
                }
            }
            if (karsilastirma.dikkat_gereken.isNotEmpty()) {
                item {
                    Text("Dikkat Gerekenler", color = Color(0xFFA32D2D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    karsilastirma.dikkat_gereken.forEach { Text("  - $it", color = Color.White.copy(0.8f), fontSize = 11.sp) }
                }
            }
            if (karsilastirma.kurallar.isNotEmpty()) {
                item { Text("Kural Detaylari", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items(karsilastirma.kurallar) { kural ->
                    val renk = when (kural.sonuc.durum) {
                        "dogru"  -> Color(0xFF1D9E75)
                        "kismi"  -> Color(0xFFD4AC0D)
                        "yanlis" -> Color(0xFFA32D2D)
                        else     -> Color.Gray
                    }
                    val ikon = when (kural.sonuc.durum) {
                        "dogru" -> "✓"; "kismi" -> "◐"; "yanlis" -> "✗"; else -> "?"
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(ikon, color = renk, fontSize = 14.sp, modifier = Modifier.width(20.dp))
                        Text(kural.aciklama, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("${kural.sonuc.skor.toInt()}/100", color = renk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkorKutu(baslik: String, deger: Double, renk: Color) {
    Surface(color = renk.copy(0.15f), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Text(deger.toInt().toString(), color = renk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(baslik, color = Color.Gray, fontSize = 10.sp)
        }
    }
}
