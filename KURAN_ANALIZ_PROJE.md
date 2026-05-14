# Kuran-ı Kerim Ses Analiz Merkezi
## Proje Mimari ve Teknik Analiz Dokümantasyonu

---

## İçindekiler
1. [Proje Genel Bakış](#1-proje-genel-bakış)
2. [Sistem Mimarisi (v3.0 Sunucusuz)](#2-sistem-mimarisi-v30-sunucusuz)
3. [Kullanılan Teknolojiler ve Bağımlılıklar](#3-kullanılan-teknolojiler-ve-bağımlılıklar)
4. [Bileşenler ve Klasör Yapısı](#4-bileşenler-ve-klasör-yapısı)
5. [Ses Analiz Pipeline (Python)](#5-ses-analiz-pipeline-python)
6. [3D Görselleştirme ve Oynatma (Kotlin)](#6-3d-görselleştirme-ve-oynatma-kotlin)
7. [Kayıt ve Yönetim Sistemi](#7-kayıt-ve-yönetim-sistemi)
8. [Karşılaşılan Sorunlar ve Çözümleri](#8-karşılaşılan-sorunlar-ve-çözümleri)
9. [Kurulum ve Derleme](#9-kurulum-ve-derleme)

---

## 1. Proje Genel Bakış

**Kuran-ı Kerim Ses Analiz Merkezi**, Kur'an tilavetini (veya herhangi bir ses kaydını) yapay zeka ve sinyal işleme algoritmaları kullanarak analiz eden ve bu veriyi eşzamanlı bir 3D uzayda (nokta bulutu) görselleştiren gelişmiş bir masaüstü uygulamasıdır.

Uygulamanın temel amacı, sesi sadece duymakla kalmayıp; frekans dağılımı, perde (pitch), enerji, tını (timbre) ve formant karakteristikleri gibi spektral öznitelikleri derinlemesine analiz ederek interaktif bir inceleme ortamı sunmaktır.

---

## 2. Sistem Mimarisi (v3.0 Sunucusuz)

Projenin ilk sürümlerinde kullanılan "FastAPI üzerinden HTTP/REST haberleşmesi" mimarisi tamamen terk edilmiş ve yerine yüksek performanslı, düşük gecikmeli **"Subprocess Pipe IPC" (Süreçler Arası İletişim)** mimarisine geçilmiştir.

**Neden Mimari Değişti?**
- **Kullanıcı Deneyimi:** Kullanıcının arka planda ayrı bir sunucu başlatma zorunluluğunun ortadan kaldırılması.
- **Performans:** HTTP overhead (TCP handshake, multipart encode/decode) yükünün tamamen silinmesi.
- **Kararlılık:** Port çakışmaları ve ağ güvenlik duvarı sorunlarının (firewall) engellenmesi.

### Çalışma Modeli (IPC Protokolü)
1. Kotlin (Compose Desktop) uygulaması çalışmaya başladığında arka planda görünmez bir Python süreci (`analyze_bridge.py`) başlatır.
2. Kotlin, analiz isteğini bir JSON formatında Python'un **stdin** (standart girdi) kanalına yazar.
3. Python analiz işlemini gerçekleştirir ve devasa analiz verisini tek bir satır JSON olarak **stdout** (standart çıktı) üzerinden Kotlin'e geri yollar.

```mermaid
graph TD
    UI[Compose Desktop UI (JVM)]
    UI --> |start/stop/play| VM[AnalysisViewModel]
    
    subgraph KOTLIN [Kotlin Multiplatform]
        VM --> Audio[AudioImplementations (Clip, TargetDataLine)]
        VM --> Bridge[PythonBridge IPC]
    end

    subgraph PYTHON [Python Subprocess]
        Bridge -->|stdin: JSON Cmd| PyBridge[analyze_bridge.py]
        PyBridge --> |librosa, sklearn| Analyzer[audio_analyzer.py]
        Analyzer --> |UMAP, MFCC, Pitch| PyBridge
        PyBridge -->|stdout: JSON Response| Bridge
    end
```

---

## 3. Kullanılan Teknolojiler ve Bağımlılıklar

| Katman | Teknoloji / Kütüphane | Kullanım Amacı |
| :--- | :--- | :--- |
| **Arayüz (UI)** | Kotlin + Compose Multiplatform | Modern, reaktif masaüstü grafik arayüzü |
| **Eşzamanlılık** | Kotlinx Coroutines + Swing | Asenkron işlemler ve UI güncellemeleri |
| **Ses (Desktop)** | `javax.sound.sampled` | Mikrofon kaydı ve WAV oynatma |
| **Analiz Motoru** | Python 3.12 | Veri işleme ve yapay zeka backend'i |
| **Sinyal İşleme** | `librosa`, `soundfile` | MFCC, Spektral Analiz, F0, Sessizlik Bulma |
| **Boyut İndirgeme**| `umap-learn`, `scikit-learn` | 46 boyutlu veriyi 3 boyutlu uzaya (3D) indirme |
| **Derleme (Build)**| Gradle 8.9 | Proje bağımlılık ve artifact yönetimi |

---

## 4. Bileşenler ve Klasör Yapısı

```text
C:\Users\inanc\Desktop\Kuran ı Kerim Windows Uygulama\
├── backend/
│   ├── analyze_bridge.py         # Kotlin'in konuştuğu süreç köprüsü (IPC)
│   ├── audio_analyzer.py         # Asıl sinyal işleme mantığı
│   └── requirements.txt          # Python bağımlılıkları
│
├── composeApp/src/
│   ├── commonMain/
│   │   ├── viewmodel/AnalysisViewModel.kt  # İş mantığı, dosya yükleme, state yönetimi
│   │   ├── render/TimbreSpaceRenderer.kt   # Canvas tabanlı 3D görselleştirme motoru
│   │   └── models/AnalysisModels.kt        # JSON serileştirme modelleri
│   │
│   └── desktopMain/
│       ├── Main.kt                         # Windows giriş noktası
│       └── audio/AudioImplementations.kt   # Native ses kayıt (TargetDataLine) ve Clip oynatıcı
│
└── build.gradle.kts                        # Root Gradle yapılandırması
```

---

## 5. Ses Analiz Pipeline (Python)

Ses verisi (WAV) Kotlin üzerinden diske yazılır ve yolu Python bridge'e iletilir. `audio_analyzer.py` aşağıdaki sıralı işlemleri gerçekleştirir:

1. **Yükleme:** `librosa.load()` ile ses 22050 Hz, mono olarak belleğe alınır.
2. **Özellik Çıkarımı (Feature Extraction):**
   - **Perde (Pitch):** `librosa.pyin` ile temel frekans (F0) ve sesli/sessiz (voiced/unvoiced) tespiti.
   - **MFCC:** 13 katsayı + delta + delta² = Toplam 39 boyut.
   - **Spektral:** Centroid (parlaklık), Flux (değişim), Zero Crossing Rate (ZCR), Rolloff, RMS Enerji.
   - **Formantlar:** LPC analizi kullanılarak F1 ve F2 formant frekansları.
3. **Boyut İndirgeme (UMAP):** Normalize edilen 46 boyutlu (39 MFCC + 7 Spektral/Pitch) özellik matrisi, `umap-learn` kullanılarak 3 boyutlu koordinatlara (x, y, z) çevrilir.
4. **Renk Ataması:** Perdeye (F0) ve RMS Enerjisine bakılarak hex renkleri atanır. (Örn: Derin sesli `#1D9E75`, Yüksek enerjili `#E85D24`).
5. **Segmentasyon:** Sessizlik bölgeleri (`-40 dB` eşiği) tespit edilip "Ayet" aralıklarına bölünür.

---

## 6. 3D Görselleştirme ve Oynatma (Kotlin)

### Oynatma ve Senkronizasyon (Audio-Visual Sync)
Ses dosyalarının oynatılması `javax.sound.sampled.Clip` üzerinden yapılır. 
**Önemli Bir Mühendislik Çözümü:** Sadece bir zamanlayıcı (Timer) kullanmak zamanla kaymalara (drift) neden oluyordu. Bu sorun, `clip.microsecondPosition` kullanılarak donanım saatine tam senkronize bir okuma yapılarak çözülmüştür. Bu sayede 3D noktaların aydınlanması ile duyulan ses arasında milisaniyelik bir mükemmel uyum sağlanmıştır.

### TimbreSpaceRenderer (3D Motoru)
- Veriler `(x,y,z)` uzayına yerleştirilir.
- **İnteraktivite:** Kullanıcı mouse sol kliği ile basılı tutup kaydırarak 3D uzayı döndürebilir (rotationX, rotationY). Mouse tekerleği (Scroll) ile kamerayı yakınlaştırıp uzaklaştırabilir (Pinch/Zoom).
- O an çalan zamana (timeSec) denk gelen 3D noktalar daha parlak, diğer noktalar soluk gösterilir. Aktif noktanın üzerinde ayet etiketi belirir.

---

## 7. Kayıt ve Yönetim Sistemi

Uygulama her analiz sonucunu otomatik olarak arşivler. 
- **Konum:** `Belgeler/KuranAnaliz/`
- **İsimlendirme:** `SureAdı_YYYYMMDD_HHMMSS.wav` ve `.json`
- **Arayüz Yönetimi:** Uygulamanın sol veya sağ panelinde önceki kayıtlar listelenir. Kullanıcı bir kayda tıkladığında Python'u hiç meşgul etmeden (anında) direkt `JSON` dosyasından okuma yapılarak 3D sahne saniyeler içinde yüklenir ve oynatmaya hazır hale gelir.

---

## 8. Karşılaşılan Sorunlar ve Çözümleri

| Sorun / Bug | Sebebi | Çözüm Yaklaşımı |
| :--- | :--- | :--- |
| **Desktop Dosya Seçici Crash'i** | `JFileChooser` asenkron çağrıldığında Coroutine dispatcher eksikliği yaşanıyordu. | Gradle'a `kotlinx-coroutines-swing` bağımlılığı eklendi ve file dialog `Dispatchers.Main` bloklarına alındı. |
| **Senkronizasyon Kayması (Drift)** | Kotlin Flow veya döngülerinin seste gecikmeye sebep olması. | Oynatma pozisyonu donanımdan (Native Clip Position) okunarak render döngüsüne aktarıldı. |
| **Gradle Derleme Sorunları (ı harfi)**| Windows path'lerinde veya Türkçe karakterlerde JVM encoding çökmesi. | PowerShell boot scripti ve Gradle daemon JVM args (`-Dfile.encoding=UTF-8`) revizyonu yapıldı. |
| **Gereksiz CPU ve Port Kullanımı** | FastAPI sunucusu gereksiz yere TCP portlarını tutuyordu. | Python HTTP sunucusu silindi, süreç içi standart I/O köprüsü (`analyze_bridge.py`) yazıldı. |

---

## 9. Kurulum ve Derleme

### 1. Python Gereksinimleri
Python 3.12 kurulu olmalıdır.
```powershell
cd "backend"
python -m pip install -r requirements.txt
```

### 2. Uygulamayı Derleme
Kotlin Multiplatform kullanılarak çalıştırılabilir (.exe) dosya oluşturulur.
```powershell
.\gradlew.bat :composeApp:createReleaseDistributable
```
Derlenmiş uygulama şu adreste bulunabilir:
`composeApp\build\compose\binaries\main-release\app\KuranAnalizWindows\KuranAnalizWindows.exe`

*Uygulama çalıştırıldığında Python ortamını otomatik tanır ve arka plan analiz servisini kendisi başlatır.*

---
*Bu dokümantasyon, projenin en son hali (v3.0) baz alınarak oluşturulmuştur. Tüm asenkron iletişim, görselleştirme rotasyonları ve audio-clip senkronizasyon düzeltmeleri bu analize dahildir.*
