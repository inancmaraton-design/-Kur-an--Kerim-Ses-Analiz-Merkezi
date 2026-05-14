@file:OptIn(ExperimentalMaterial3Api::class)

package com.kuran.android.screens

import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.*
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
import com.kuran.android.utils.FileDialogUtils
import java.io.File

@Composable
fun ComparisonScreen(
    secilenSureNo: Int,
    secilenAyetNo: Int,
    analizMetin: String,
    analizSonucu: MetinMetrikleri?,
    karsilastirmaMetin: String,
    karsilastirmaSonucu: MetinMetrikleri?,
    onKarsilastirmaMetniGuncelle: (String, MetinMetrikleri?) -> Unit,
    modifier: Modifier = Modifier
) {
    var kategori by remember { mutableStateOf("") }
    var kategoriler by remember { mutableStateOf(emptyList<String>()) }
    var dosyalar by remember { mutableStateOf(emptyList<String>()) }
    var secilenDosya by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        kategoriler = FileDialogUtils.getComparisonCategories()
    }
    
    LaunchedEffect(kategori) {
        if (kategori.isNotEmpty()) {
            dosyalar = FileDialogUtils.getComparisonFiles(kategori)
            secilenDosya = ""
        }
    }
    
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        // Başlık
        Text(
            "KARŞILAŞTIRMA",
            fontSize = 24.sp,
            color = Color.White,
            modifier = Modifier.padding(24.dp)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Sol Panel — KURAN
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text("KURAN", fontSize = 14.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                
                Surface(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        analizMetin.take(200),
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
            
            // Sağ Panel — KARŞILAŞTIRMA
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text("KARŞILAŞTIRMA", fontSize = 14.sp, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                
                // Kategori dropdown
                var expandedKategori by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedKategori,
                    onExpandedChange = { expandedKategori = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = kategori,
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Kategori seç...", color = Color.Gray) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedKategori) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7B1FA2),
                            unfocusedBorderColor = Color.White.copy(0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedKategori,
                        onDismissRequest = { expandedKategori = false }
                    ) {
                        kategoriler.forEach { kat ->
                            DropdownMenuItem(
                                text = { Text(kat) },
                                onClick = {
                                    kategori = kat
                                    expandedKategori = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Metin dropdown
                var expandedDosya by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expandedDosya,
                    onExpandedChange = { expandedDosya = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = secilenDosya,
                        onValueChange = {},
                        readOnly = true,
                        placeholder = { Text("Metin seç...", color = Color.Gray) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDosya) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF7B1FA2),
                            unfocusedBorderColor = Color.White.copy(0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    ExposedDropdownMenu(
                        expanded = expandedDosya,
                        onDismissRequest = { expandedDosya = false }
                    ) {
                        dosyalar.forEach { dosya ->
                            DropdownMenuItem(
                                text = { Text(dosya) },
                                onClick = {
                                    secilenDosya = dosya
                                    expandedDosya = false
                                    // Dosya içeriğini oku
                                    if (kategori.isNotEmpty()) {
                                        try {
                                            val dosyaYolu = "C:\\KP\\data\\karsilastirma\\$kategori\\$dosya.txt"
                                            val icerik = File(dosyaYolu).readText(Charsets.UTF_8)
                                            onKarsilastirmaMetniGuncelle(icerik, null)
                                        } catch (e: Exception) {
                                            // Dosya okunamadı
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                Surface(
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        karsilastirmaMetin.take(200),
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
        
        // KARŞILAŞTIRMA SONUÇLARI
        if (analizSonucu != null && karsilastirmaSonucu != null) {
            Text(
                "KARŞILAŞTIRMA SONUÇLARI",
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(Modifier.height(12.dp))
            
            ComparisonResultsTable(analizSonucu, karsilastirmaSonucu)
        }
    }
}

@Composable
private fun ComparisonResultsTable(
    kuranMetrikleri: MetinMetrikleri,
    karsilastirmaMetrikleri: MetinMetrikleri
) {
    val metrikler = listOf(
        Pair("f2_Entropi", Pair(kuranMetrikleri.f2_entropi?.toDouble() ?: 0.0, karsilastirmaMetrikleri.f2_entropi?.toDouble() ?: 0.0)),
        Pair("Nazal Oran", Pair(kuranMetrikleri.nazal_oran?.toDouble() ?: 0.0, karsilastirmaMetrikleri.nazal_oran?.toDouble() ?: 0.0)),
        Pair("Tefhim Oranı", Pair(kuranMetrikleri.tefhim_orani?.toDouble() ?: 0.0, karsilastirmaMetrikleri.tefhim_orani?.toDouble() ?: 0.0))
    )
    
    Surface(
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            metrikler.forEach { (metrikAdi, degerler) ->
                val farklı = degerler.first - degerler.second
                val sembul = when {
                    farklı > 0 -> "▲"
                    farklı < 0 -> "▼"
                    else -> "="
                }
                val farkRengi = when {
                    farklı > 0 -> Color(0xFF1D9E75)
                    farklı < 0 -> Color(0xFFA32D2D)
                    else -> Color.Gray
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(metrikAdi, color = Color.Gray, fontSize = 12.sp)
                    Text(String.format("%.2f", degerler.first), color = Color.White, fontSize = 12.sp)
                    Text(String.format("%.2f", degerler.second), color = Color.White, fontSize = 12.sp)
                    Text("$sembul ${String.format("%.2f", kotlin.math.abs(farklı))}", color = farkRengi, fontSize = 12.sp)
                }
            }
        }
    }
}
