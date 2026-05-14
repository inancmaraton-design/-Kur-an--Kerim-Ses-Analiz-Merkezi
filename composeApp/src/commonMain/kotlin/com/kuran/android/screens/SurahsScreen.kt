package com.kuran.android.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.Sure
import com.kuran.android.surahlar

@Composable
fun SurahsScreen(
    onSurahSelected: (Sure) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchText by remember { mutableStateOf("") }
    
    val filteredSurahs = if (searchText.isEmpty()) {
        surahlar
    } else {
        surahlar.filter {
            it.adiLatince.contains(searchText, ignoreCase = true) ||
            it.no.toString().contains(searchText)
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
    ) {
        // Başlık
        Text(
            "SURELER",
            fontSize = 24.sp,
            color = Color.White,
            modifier = Modifier.padding(24.dp)
        )
        
        // Arama kutusu
        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            placeholder = { Text("Sure ara...", color = Color.Gray) },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFE65100),
                unfocusedBorderColor = Color.White.copy(0.1f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Sureler listesi
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(filteredSurahs) { sure ->
                SurahCard(sure) {
                    onSurahSelected(sure)
                }
            }
        }
    }
}

@Composable
private fun SurahCard(
    sure: Sure,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sure numarası
            Text(
                sure.no.toString(),
                fontSize = 18.sp,
                color = Color(0xFFE65100),
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
            
            // Sure isimleri
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    sure.adiArapca,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    sure.adiLatince,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            // Ayet sayısı
            Text(
                "${sure.ayetSayisi} ayet",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}
