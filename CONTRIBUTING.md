# 🤝 Katkıda Bulunma Rehberi

Kur'an-ı Kerim Analiz Merkezi projesine katkıda bulunmak istediğiniz için teşekkür ederiz! Bu belge, katkı sürecini kolaylaştırmak için hazırlanmıştır.

---

## 📋 Başlamadan Önce

- [Davranış Kurallarını](CODE_OF_CONDUCT.md) okuyun
- Mevcut [Issue'ları](../../issues) inceleyin — belki birisi zaten aynı fikri önermiştir
- Büyük değişiklikler için önce bir Issue açarak tartışın

---

## 🚀 Katkı Süreci

### 1. Fork & Clone

```bash
# Repoyu fork'layın (GitHub arayüzünden "Fork" butonuna tıklayın)

# Fork'unuzu klonlayın
git clone https://github.com/SIZIN_KULLANICI_ADINIZ/kuran-analiz-merkezi.git
cd kuran-analiz-merkezi

# Orijinal repoyu upstream olarak ekleyin
git remote add upstream https://github.com/PROJE_SAHIBI/kuran-analiz-merkezi.git
```

### 2. Branch Oluşturun

```bash
# Her özellik/düzeltme için ayrı branch açın
git checkout -b ozellik/ayet-arama-modulu
# veya
git checkout -b duzeltme/arama-hatasi
```

**Branch İsimlendirme Kuralları:**
- `ozellik/` — Yeni özellikler
- `duzeltme/` — Hata düzeltmeleri
- `dokumantasyon/` — Dokümantasyon güncellemeleri
- `refactor/` — Kod iyileştirmeleri

### 3. Değişikliklerinizi Yapın

- Küçük, odaklı commit'ler yapın
- Her commit tek bir amaca hizmet etsin

### 4. Commit Mesajları

[Conventional Commits](https://www.conventionalcommits.org/) formatını kullanın:

```
feat: Ayet arama özelliği eklendi
fix: Arapça karakter kodlama hatası düzeltildi
docs: README Türkçe'ye çevrildi
refactor: Veritabanı sorguları optimize edildi
test: Arama modülü testleri eklendi
```

### 5. Push & Pull Request

```bash
git push origin ozellik/ayet-arama-modulu
```

Ardından GitHub'da **Pull Request** açın ve şunları belirtin:
- Ne değiştirdiniz?
- Neden değiştirdiniz?
- Nasıl test ettiniz?
- İlgili Issue varsa numarasını yazın (`Closes #42`)

---

## 🐛 Hata Bildirimi

Issue açarken şunları dahil edin:
- **Ortam:** İşletim sistemi, tarayıcı, versiyon
- **Adımlar:** Hatayı nasıl tekrarlatırsınız?
- **Beklenen:** Ne olmasını bekliyordunuz?
- **Gerçekleşen:** Ne oldu?
- **Ekran görüntüsü** (mümkünse)

---

## 💡 Özellik Talebi

- Önce Issue açın ve `[ÖNERİ]` etiketi kullanın
- Özelliğin amacını açıklayın
- Kullanım senaryoları verin
- Teknik öneri varsa ekleyin

---

## ✅ Kod Standartları

- Kodunuzu çalıştırmadan önce test edin
- Karmaşık mantığa yorum satırları ekleyin
- Değişkenler ve fonksiyonlar için açıklayıcı isimler kullanın
- Türkçe ve İngilizce yorum satırları her ikisi de kabul edilir

---

## 🌍 Çeviri Katkıları

Arayüzü başka dillere çevirmek isteyenler için:
- `/locales` klasörüne gidin (oluşturulduğunda)
- Kendi dilinizde yeni bir dosya oluşturun
- PR açın

---

## 🏅 Katkıda Bulunanlar

Tüm katkılar README'deki Contributors bölümünde listelenecektir. Katkınız için şimdiden teşekkürler! 🎉
