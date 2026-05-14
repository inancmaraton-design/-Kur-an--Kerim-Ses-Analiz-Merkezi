# GÖREV: 3D Spektrogram Modülü — Plan & Tasarım

**Tarih:** 2026-05-08  
**Durum:** Plan Aşaması  
**Proje:** Kuran-ı Kerim Ses Analizi (Windows Desktop)

---

## 1. DURUM ANALİZİ

### Mevcut Sistem
- **Platform:** Windows Desktop (Kotlin Compose Multiplatform)
- **Backend:** Python 3.12 + librosa (STFT, MFCC, pitch zaten var)
- **3D Görselleştirme:** UMAP 3D projeksiyonu (timbre space nokta bulutu)
- **IPC:** JSON stdin/stdout pipe (PythonBridge.kt)
- **UI Renderer:** Canvas custom (Painter's algorithm)

### Fark: UMAP vs Spektrogram
| Özellik | UMAP 3D (Mevcut) | Spektrogram 3D (İstenen) |
|---------|-----------------|--------------------------|
| Eksen | Timbre boyutları (UMAP) | Time × Frequency × Magnitude |
| Veri | Nokta bulutu | Surface/heatmap grid |
| Hesaplama | Dimensionality reduction | STFT/FFT |
| İçgörü | Ses özellikleri benzerliği | Frekans zaman evrim |

---

## 2. TASARIM SEÇİMLERİ (KRITIK)

### Soru A: Spektrogram Hesapla Nerede?

**Seçenek A1 — Python'da (önerilen)**
- ✅ librosa.stft() zaten var
- ✅ Büyük matris hesaplama → IO thread'de güvenli
- ✅ Mevcut pipeline'ı genişletmek kolay
- ❌ JSON'da büyük matris → ağır (2048 frekanslık × 100+ frame = 200KB+)

**Seçenek A2 — Kotlin'de JVM FFT lib**
- ✅ FFT hesapla → Spektrogram → Canvas render all in Kotlin
- ❌ Yeni bağımlılık (JTransforms? apfloat?)
- ❌ Gradle 8.9 uyumluluğu riski
- ❌ Büyük dosya analizi Kotlin ile yavaş olabilir

### Soru B: Görsellendirme Nereye?

**Seçenek B1 — Kotlin Canvas (mevcut TimbreSpaceRenderer pattern)**
- ✅ Mevcut 3D renderer logic'i reuse et
- ✅ Döndürme, zoom etkileşim hazır
- ✅ Native performance
- ❌ Spektrogram surface renderlemesi karmaşık (painter's algo + grid)
- ❌ Custom shader/3D lib yok → surface fakesiası zor

**Seçenek B2 — WebView + HTML5/Plotly.js**
- ✅ Plotly 3D surface render hazır
- ✅ Interaktif (döndür, zoom, hover bilgi)
- ✅ JSON → HTML dönüşümü basit
- ❌ Desktop'ta **WebView yoktur!**
  - Swing'de JEditorPane yok
  - JCEF (Java Chromium Embedded) → ağır bağımlılık
  - javafx.scene.web.WebView → yeni modul, kompleks

**Seçenek B3 — Matplotlib/Plotly PNG export + Canvas göster**
- Python'da matplotlib/plotly PNG renderle
- Base64 encoded PNG → Kotlin'e gönder
- Canvas'te Image olarak göster
- ❌ Interaktiflık yok (static image)

---

## 3. ÖNERILEN ÇÖZÜM (A1 + B1)

### Kurulum
1. Python'da spektrogram hesapla (librosa.stft)
2. Büyük matris veri problemi → **çözüm:**
   - Frekanslı bölüm kırp (0–4000 Hz tilâvet için)
   - Frame decimation (her 2–4. frame al, 512 → 64 frame)
   - Magnitude dB dönüşümü (float32 → 20*log10)
   - JSON: `{magnitude: [[...], [...], ...], freq_bins: [...], time: [...]}`

3. Kotlin'de spektrogram grid oku → Canvas'te 3D surface render
   - Z eksen = magnitude (dB)
   - X eksen = zaman
   - Y eksen = frekans
   - Painter's algorithm ile surface çiz (arka-ön sorting)
   - Color mapping: dB → Inferno colorscale

### Neden Bu?
- Gradle bağımlılığı 0 (mevcut libs'te zaten numpy/librosa)
- Desktop WebView konfigürasyonu kopleks (JCEF vs JavaFX)
- Canvas renderer zaten optimize edilmiş
- Büyük JSON transfer → yönetilebilir boyutta (decimation ile)

---

## 4. ADIM ADIM PLAN

### FAZA 1: Python Backend
- [ ] `analyze_bridge.py` → `spectrogram` command ekle
- [ ] `audio_analyzer.py` → spectrogram hesaplama fonksiyonu
  - librosa.stft() → magnitude → dB dönüşümü
  - Frekans binlerini 0–4000 Hz kırp
  - Frame decimation (her 4. frame)
  - JSON format: `{ok, data: {spectrogram_db, freqs, times}}`

### FAZA 2: Kotlin Model & ViewModel
- [ ] SpectrogramData data class ekle
- [ ] AnalysisViewModel.loadSpectrogram() suspend fun
- [ ] PythonBridge spektrogram analiz desteği

### FAZA 3: Canvas 3D Renderer
- [ ] SpectrogramRenderer.kt (Painter's algorithm)
  - Surface vertex → projected 2D point
  - Magnitude → Z değeri
  - Sorting ve rasterization
  - Color interpolation

### FAZA 4: UI Entegrasyonu
- [ ] Spektrogram ekranını mevcut UMAP yanında aç
- [ ] Navigation: "Spektrogramı Göster" butonu
- [ ] Loading/Error states

### FAZA 5: Test & Optimizasyon
- [ ] Büyük dosya performans testi
- [ ] Frame decimation oranı tune
- [ ] Colorscale ve lighting optimize et

---

## 5. KODU YAZARKEN KURALLAR (CLAUDE.md)

- [ ] Gradle 8.9 ile build temiz geçer
- [ ] compileSdk = 34, targetSdk = 34
- [ ] Analiz Dispatchers.IO'da yapılır
- [ ] Kotlin naming: camelCase (var), PascalCase (class)
- [ ] Thread-safe: PythonBridge sync kurallarını takip et
- [ ] Sensitive data (dosya path) log'a yazılabilir ama içerik yazılmaz
- [ ] Commit: "feat: 3D spektrogram modülü eklendi"

---

## 6. DOĞRULAMA KRİTERLERİ

Tamamlanmış sayılır:
- [ ] Gerçek WAV dosyasında spektrogram hatasız hesaplanıyor
- [ ] 3D surface döndürülebiliyor (fare sürükleme)
- [ ] Zoom yapılabiliyor (scroll)
- [ ] Büyük dosya (>5 MB) UI donmuyur (IO thread control)
- [ ] Hata durumunda net mesaj (dosya yok, format hatalı)
- [ ] Gradle build clean geçiyor

---

## KARAR VERİLEN SORULAR ✅

1. **Spektrogram konumu:** ✅ Yeni buton ile yeni ekranda açılsın
   - Mevcut UMAP3D ekranını korut
   - Bottom panel'de "3D Spektrogram" butonu ekle
   - Navigation → Spectrogram ekranı aç

2. **Frekans aralığı:** ✅ 0–4000 Hz (tilâvet optimal)
   - librosa.stft() full, sonra 4000 Hz'e trim

3. **Frame decimation:** ✅ Her frame (tam çözünürlük)
   - ~100+ ms analiz, JSON ~200KB+
   - Desktop → network yok, local subprocess → tolerable
   - Daha detaylı spektrogram görseli

---

## IMPLEMENTATION ROADMAP

### FAZA 1: Python Backend [Sorumlu: analyze_bridge.py + audio_analyzer.py]
- analyze_bridge.py: "spectrogram" command handler ekle
- audio_analyzer.py: spectrogram_stft() fonksiyonu ekle (0–4000 Hz trim)
- JSON response: `{ok, data: {spec_magnitude_db, freq_hz, time_sec}}`

### FAZA 2: Kotlin Models [Sorumlu: AnalysisModels.kt + AnalysisViewModel.kt]
- SpectrogramData serializable data class
- AnalysisViewModel: loadSpectrogram() suspend fun
- PythonBridge spektrogram komutu desteği

### FAZA 3: 3D Renderer [Sorumlu: SpectrogramRenderer.kt + TimbreSpaceRenderer.kt paralel]
- Mevcut TimbreSpaceRenderer logic'ini adapt et
- Surface vertex generation (Z = magnitude dB)
- Painter's algorithm ile 3D project
- Colorscale mapping (Inferno palette)

### FAZA 4: UI Entegrasyonu [Sorumlu: App.kt + navigation]
- Bottom panel'de "3D Spektrogram" butonu
- Spectrogram ekranı (loading/success/error states)
- Navigation: main UMAP ↔ spektrogram

### FAZA 5: Test & Doğrulama
- Performans testi (5+ MB dosya)
- Hata handling (format, file not found)
- Gradle build clean

---

## BAŞLANMA ÖNCESİ KATKIDA BULUN

Tüm planı görüştük. EnterPlanMode ile implementasyon detaylarını tasarla, onay bekle, sonra kod yazmaya başla.
