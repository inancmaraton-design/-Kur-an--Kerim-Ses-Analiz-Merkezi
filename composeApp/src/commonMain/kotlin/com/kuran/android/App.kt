@file:OptIn(ExperimentalMaterial3Api::class)

package com.kuran.android

import androidx.compose.foundation.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.viewmodel.AnalysisViewModel
import com.kuran.android.screens.AnalysisScreen
import com.kuran.android.screens.ComparisonScreen
import com.kuran.android.screens.SurahsScreen
import com.kuran.android.utils.FileDialogUtils
import java.io.File

@Composable
fun App(viewModel: AnalysisViewModel) {
    var appState by remember { mutableStateOf(AppState()) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFE65100),
            secondary = Color(0xFF7B1FA2),
            background = Color(0xFF0A0A0F)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
        ) {
            // SEKME BARI
            SecmeBari(
                aktifSekme = appState.aktifSekme,
                onSekmeSecimi = { yeniSekme ->
                    appState = appState.copy(aktifSekme = yeniSekme)
                }
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // SOL PANEL
                SolPanel(
                    secilenSureNo = appState.secilenSureNo,
                    onSureSecimi = { sureNo ->
                        appState = appState.copy(secilenSureNo = sureNo)
                    },
                    secilenAyetNo = appState.secilenAyetNo,
                    onAyetSecimi = { ayetNo ->
                        appState = appState.copy(secilenAyetNo = ayetNo)
                    },
                    modifier = Modifier.width(300.dp)
                )

                // ANA IÇERIK (Sekmeler)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF0A0A0F))
                ) {
                    when (appState.aktifSekme) {
                        Sekme.ANALIZ -> AnalysisScreen(
                            analizMetin = appState.analizMetin,
                            analizSonucu = appState.analizSonucu,
                            modifier = Modifier.fillMaxSize()
                        )
                        Sekme.KARSILASTIRMA -> ComparisonScreen(
                            secilenSureNo = appState.secilenSureNo,
                            secilenAyetNo = appState.secilenAyetNo,
                            analizMetin = appState.analizMetin,
                            analizSonucu = appState.analizSonucu,
                            karsilastirmaMetin = appState.karsilastirmaMetin,
                            karsilastirmaSonucu = appState.karsilastirmaSonucu,
                            onKarsilastirmaMetniGuncelle = { metin, sonuc ->
                                appState = appState.copy(
                                    karsilastirmaMetin = metin,
                                    karsilastirmaSonucu = sonuc
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Sekme.SURELER -> SurahsScreen(
                            onSurahSelected = { sure ->
                                appState = appState.copy(
                                    aktifSekme = Sekme.ANALIZ,
                                    secilenSureNo = sure.no,
                                    secilenAyetNo = 1
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // ALT BUTON BARI
            AltButonBari(
                onMetinAnaliziTiklandi = { dosyaYolu ->
                    viewModel.analyzeTextFile(dosyaYolu)
                },
                onSesAnaliziTiklandi = { dosyaYollari ->
                    viewModel.analyzeAudioFiles(dosyaYollari)
                }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SEKME BARI
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SecmeBari(
    aktifSekme: Sekme,
    onSekmeSecimi: (Sekme) -> Unit
) {
    Surface(
        color = Color(0xFF1A1A2E),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecmeButonu("ANALİZ", Sekme.ANALIZ == aktifSekme) {
                onSekmeSecimi(Sekme.ANALIZ)
            }
            SecmeButonu("KARŞILAŞTIRMA", Sekme.KARSILASTIRMA == aktifSekme) {
                onSekmeSecimi(Sekme.KARSILASTIRMA)
            }
            SecmeButonu("SURELER", Sekme.SURELER == aktifSekme) {
                onSekmeSecimi(Sekme.SURELER)
            }
        }
    }
}

@Composable
fun SecmeButonu(
    metni: String,
    secili: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (secili) Color(0xFFE65100) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Text(
            metni,
            color = if (secili) Color.White else Color.Gray,
            fontSize = 14.sp,
            fontWeight = if (secili) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SOL PANEL
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun SolPanel(
    secilenSureNo: Int,
    onSureSecimi: (Int) -> Unit,
    secilenAyetNo: Int,
    onAyetSecimi: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF1A1A2E),
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, Color.White.copy(0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "SÜRELERİ SEÇİN",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            // Sure dropdown
            var expandedSure by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedSure,
                onExpandedChange = { expandedSure = it }
            ) {
                OutlinedTextField(
                    value = surahlar.find { it.no == secilenSureNo }?.adiLatince ?: "Sure seç",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSure) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE65100),
                        unfocusedBorderColor = Color.White.copy(0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 11.sp)
                )
                ExposedDropdownMenu(
                    expanded = expandedSure,
                    onDismissRequest = { expandedSure = false }
                ) {
                    surahlar.forEach { sure ->
                        DropdownMenuItem(
                            text = { Text("${sure.no} - ${sure.adiLatince}") },
                            onClick = {
                                onSureSecimi(sure.no)
                                expandedSure = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("AYETİ SEÇİN", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            // Ayet dropdown
            val secilenSure = surahlar.find { it.no == secilenSureNo }
            val ayetSayisi = secilenSure?.ayetSayisi ?: 1

            var expandedAyet by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedAyet,
                onExpandedChange = { expandedAyet = it }
            ) {
                OutlinedTextField(
                    value = secilenAyetNo.toString(),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAyet) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE65100),
                        unfocusedBorderColor = Color.White.copy(0.1f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 11.sp)
                )
                ExposedDropdownMenu(
                    expanded = expandedAyet,
                    onDismissRequest = { expandedAyet = false }
                ) {
                    (1..ayetSayisi).forEach { ayet ->
                        DropdownMenuItem(
                            text = { Text("Ayet $ayet") },
                            onClick = {
                                onAyetSecimi(ayet)
                                expandedAyet = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("ÖNİZLEME", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Surface(
                color = Color.White.copy(0.05f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    "Ayet ${secilenAyetNo} - Sure $secilenSureNo",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ALT BUTON BARI
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun AltButonBari(
    onMetinAnaliziTiklandi: (String) -> Unit,
    onSesAnaliziTiklandi: (List<String>) -> Unit
) {
    Surface(
        color = Color(0xFF1A1A2E),
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(1.dp, Color.White.copy(0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // METİN ANALİZİ BUTONU
            Button(
                onClick = {
                    FileDialogUtils.openTextFileDialog(
                        defaultDir = "C:\\KP\\data\\quran"
                    ) { dosyaYolu ->
                        onMetinAnaliziTiklandi(dosyaYolu)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(56.dp)
                    .weight(1f)
            ) {
                Icon(Icons.Default.Description, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "METİN ANALİZİ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // SES ANALİZİ BUTONU
            Button(
                onClick = {
                    FileDialogUtils.openAudioFileDialog(
                        defaultDir = "C:\\KP\\sureler"
                    ) { dosyalar ->
                        onSesAnaliziTiklandi(dosyalar.map { it.absolutePath })
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B1FA2)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(56.dp)
                    .weight(1f)
            ) {
                Icon(Icons.Default.AudioFile, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "SES ANALİZİ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
