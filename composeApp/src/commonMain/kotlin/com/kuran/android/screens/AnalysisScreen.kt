package com.kuran.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.models.MetinMetrikleri
import kotlin.math.roundToInt

@Composable
fun AnalysisScreen(
    analizMetin: String,
    analizSonucu: MetinMetrikleri?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(top = 32.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
            .verticalScroll(scrollState)
    ) {
        if (analizSonucu != null) {
            // KART 1 — ANALİZ EDİLEN METİN
            AnalysisTextCard(analizMetin)
            
            Spacer(Modifier.height(16.dp))
            
            // KART 2 — METRİK KARTLARI
            MetrikKartlari(analizSonucu)
            
            Spacer(Modifier.height(16.dp))
            
            // KART 3 — HARF FREKANSI
            if (analizSonucu.harf_istatistikleri.isNotEmpty()) {
                HarfFrekansiyasiCart(analizSonucu.harf_istatistikleri)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.Center)
            ) {
                Text(
                    "Analiz yapmak için dosya seçin",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun AnalysisTextCard(metinArapca: String) {
    Surface(
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "ANALİZ EDİLEN METİN",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Text(
                metinArapca,
                fontSize = 22.sp,
                textAlign = TextAlign.End,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MetrikKartlari(metrikleri: MetinMetrikleri) {
    val metrikler = listOf(
        Metrik("f2_Entropi", metrikleri.f2_entropi, 1000.0),
        Metrik("Ritmik Düzgünlük\n(nPVI)", metrikleri.ritmik_duzgunluk_npvi, 1.0),
        Metrik("Nazal Oran", metrikleri.nazal_oran, 1.0),
        Metrik("Tefhim Oranı", metrikleri.tefhim_orani, 1.0)
    )
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(metrikler) { metrik ->
            MetrikKart(metrik)
        }
    }
}

@Composable
private fun MetrikKart(metrik: Metrik) {
    val normalizedValue = (metrik.deger / metrik.maksimum).coerceIn(0.0, 1.0).toFloat()
    
    Surface(
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                metrik.adi,
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(8.dp))
            Text(
                String.format("%.2f", metrik.deger),
                fontSize = 28.sp,
                color = Color(0xFFE65100),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { normalizedValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = Color(0xFFE65100),
                trackColor = Color.White.copy(0.1f)
            )
        }
    }
}

@Composable
private fun HarfFrekansiyasiCart(harfIstatistikleri: Map<String, Int>) {
    val sortedHarfs = harfIstatistikleri.entries
        .sortedByDescending { it.value }
        .take(20)
    
    val maxSayi = sortedHarfs.maxOfOrNull { it.value }?.toFloat() ?: 1f
    
    Surface(
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "HARF FREKANSI (İlk 20)",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(12.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedHarfs.forEach { (harf, sayi) ->
                    HarfFrekansiyaRec(harf, sayi, maxSayi)
                }
            }
        }
    }
}

@Composable
private fun HarfFrekansiyaRec(harf: String, sayi: Int, max: Float) {
    val barGenislik = (sayi / max * 200).toInt().dp
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            harf,
            fontSize = 18.sp,
            color = Color.White,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
        
        Box(
            modifier = Modifier
                .width(barGenislik)
                .height(20.dp)
                .background(Color(0xFFE65100), RoundedCornerShape(4.dp))
        )
        
        Text(
            sayi.toString(),
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

data class Metrik(
    val adi: String,
    val deger: Double,
    val maksimum: Double
)
