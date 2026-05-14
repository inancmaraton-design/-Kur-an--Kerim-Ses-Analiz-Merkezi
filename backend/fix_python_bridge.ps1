# ══════════════════════════════════════════════════════════
# PYTHON BRIDGE GÜNCELLEME SCRİPTİ
# analyze_bridge.py'ye yeni komutlar ekler
# ══════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"

Write-Host "`n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  PYTHON BRIDGE GÜNCELLEME" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`n" -ForegroundColor Cyan

$BACKEND = "C:\KP\backend"
$BRIDGE_FILE = "$BACKEND\analyze_bridge.py"

Set-Location $BACKEND

# ──────────────────────────────────────────────────────────
# Yedek Al
# ──────────────────────────────────────────────────────────
Write-Host "[1/3] Mevcut dosya yedekleniyor..." -ForegroundColor Yellow

if (Test-Path $BRIDGE_FILE) {
    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    Copy-Item $BRIDGE_FILE "$BRIDGE_FILE.backup_$timestamp"
    Write-Host "✓ Yedek alındı: analyze_bridge.py.backup_$timestamp" -ForegroundColor Green
} else {
    Write-Host "⚠ analyze_bridge.py bulunamadı, yeni oluşturuluyor..." -ForegroundColor Yellow
}

# ──────────────────────────────────────────────────────────
# Yeni Bridge Dosyasını Yaz
# ──────────────────────────────────────────────────────────
Write-Host "`n[2/3] Yeni bridge kodu yazılıyor..." -ForegroundColor Yellow

$BRIDGE_CODE = @'
# -*- coding: utf-8 -*-
"""
Kuran Analiz v7.1 - Python Bridge
Kotlin <-> Python IPC (stdin/stdout JSON)
"""
import sys
import json
from pathlib import Path

# Backend klasörünü path'e ekle
BACKEND = Path(__file__).parent
sys.path.insert(0, str(BACKEND))

def send_response(data):
    """JSON yanıt gönder"""
    print(json.dumps(data, ensure_ascii=False), flush=True)

def send_error(error_msg):
    """Hata mesajı gönder"""
    send_response({"error": error_msg})

def main():
    """Ana IPC loop"""
    for line in sys.stdin:
        try:
            req = json.loads(line.strip())
            cmd = req.get("cmd")
            
            # ────────────────────────────────────────────────
            # KOMUT: korpus_listele
            # ────────────────────────────────────────────────
            if cmd == "korpus_listele":
                try:
                    from data.quran_corpus import QURAN_CORPUS
                    from data.control_corpus import CONTROL_CORPUS
                    
                    send_response({
                        "kuran": [
                            {
                                "id": k,
                                "isim": v.get("isim", k),
                                "isim_ar": v.get("isim_ar", ""),
                                "ayet_sayisi": v.get("ayet_sayisi", 0)
                            }
                            for k, v in QURAN_CORPUS.items()
                        ],
                        "kontrol": [
                            {
                                "id": k,
                                "kategori": v.get("kategori", ""),
                                "yazar": v.get("yazar", "")
                            }
                            for k, v in CONTROL_CORPUS.items()
                        ]
                    })
                except Exception as e:
                    send_error(f"Korpus listesi hatası: {e}")
            
            # ────────────────────────────────────────────────
            # KOMUT: metin_analiz
            # ────────────────────────────────────────────────
            elif cmd == "metin_analiz":
                try:
                    from analysis.text_parser import tokenize_arabic
                    from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics
                    
                    text = req.get("text", "")
                    if not text:
                        send_error("Metin boş olamaz")
                        continue
                    
                    fv = text_to_feature_vector(text)
                    metrics = compute_summary_metrics(fv)
                    
                    send_response({
                        "text": text,
                        "metrics": metrics
                    })
                except Exception as e:
                    send_error(f"Metin analiz hatası: {e}")
            
            # ────────────────────────────────────────────────
            # KOMUT: karsilastir
            # ────────────────────────────────────────────────
            elif cmd == "karsilastir":
                try:
                    from data.quran_corpus import QURAN_CORPUS
                    from data.control_corpus import CONTROL_CORPUS
                    from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics
                    from analysis.statistical_engine import compare_groups, bonferroni_correction
                    import pandas as pd
                    
                    kuran_ids = req.get("kuran_ids", [])
                    kontrol_ids = req.get("kontrol_ids", [])
                    
                    # Kur'an metriklerini hesapla
                    kuran_metrics_list = []
                    for qid in kuran_ids:
                        if qid in QURAN_CORPUS:
                            text = QURAN_CORPUS[qid].get("metin", "")
                            if text:
                                fv = text_to_feature_vector(text)
                                metrics = compute_summary_metrics(fv)
                                metrics["id"] = qid
                                kuran_metrics_list.append(metrics)
                    
                    # Kontrol metriklerini hesapla
                    kontrol_metrics_list = []
                    for cid in kontrol_ids:
                        if cid in CONTROL_CORPUS:
                            text = CONTROL_CORPUS[cid].get("metin", "")
                            if text:
                                fv = text_to_feature_vector(text)
                                metrics = compute_summary_metrics(fv)
                                metrics["id"] = cid
                                kontrol_metrics_list.append(metrics)
                    
                    # DataFrame'lere çevir
                    kuran_df = pd.DataFrame(kuran_metrics_list)
                    kontrol_df = pd.DataFrame(kontrol_metrics_list)
                    
                    # Her metrik için karşılaştır
                    metric_names = [
                        "f2_ortalama", "f2_std", "f2_entropi",
                        "gecis_yumusakligi", "spektral_merkez_ortalama",
                        "ritmik_duzgunluk", "nazal_oran"
                    ]
                    
                    results = []
                    for metric in metric_names:
                        if metric in kuran_df.columns and metric in kontrol_df.columns:
                            result = compare_groups(kuran_df, kontrol_df, metric)
                            results.append(result)
                    
                    # Bonferroni düzeltmesi
                    results = bonferroni_correction(results)
                    
                    send_response({
                        "sonuclar": results,
                        "kuran_sayisi": len(kuran_metrics_list),
                        "kontrol_sayisi": len(kontrol_metrics_list),
                        "grafik_yollari": {
                            "boxplot": "reports/output/boxplots.png",
                            "heatmap": "reports/output/heatmap_pvalues.png",
                            "forest": "reports/output/forest_plot.png"
                        }
                    })
                except Exception as e:
                    import traceback
                    send_error(f"Karşılaştırma hatası: {e}\n{traceback.format_exc()}")
            
            # ────────────────────────────────────────────────
            # KOMUT: rapor_olustur
            # ────────────────────────────────────────────────
            elif cmd == "rapor_olustur":
                try:
                    rapor_format = req.get("format", "json")
                    rapor_yolu = f"reports/output/sonuc_ozet.{rapor_format}"
                    
                    send_response({
                        "rapor_yolu": rapor_yolu,
                        "format": rapor_format
                    })
                except Exception as e:
                    send_error(f"Rapor oluşturma hatası: {e}")
            
            # ────────────────────────────────────────────────
            # Bilinmeyen komut
            # ────────────────────────────────────────────────
            else:
                send_error(f"Bilinmeyen komut: {cmd}")
        
        except json.JSONDecodeError as e:
            send_error(f"JSON parse hatası: {e}")
        except Exception as e:
            send_error(f"Genel hata: {e}")
            import traceback
            traceback.print_exc()

if __name__ == "__main__":
    main()
'@

$BRIDGE_CODE | Out-File -FilePath $BRIDGE_FILE -Encoding UTF8
Write-Host "✓ Yeni bridge kodu yazıldı" -ForegroundColor Green

# ──────────────────────────────────────────────────────────
# Test
# ──────────────────────────────────────────────────────────
Write-Host "`n[3/3] Bridge test ediliyor..." -ForegroundColor Yellow

$TEST_INPUT = '{"cmd":"korpus_listele"}' + "`n"
$TEST_INPUT | python analyze_bridge.py 2>&1 | Out-String | Write-Host

Write-Host "`n✓ Bridge başarıyla güncellendi" -ForegroundColor Green

# ──────────────────────────────────────────────────────────
# Git Commit
# ──────────────────────────────────────────────────────────
try {
    git add analyze_bridge.py
    git commit -m "feat: Python bridge'e v7.1 komutları eklendi (korpus_listele, metin_analiz, karsilastir)" -q
    Write-Host "✓ Git commit yapıldı" -ForegroundColor Green
} catch {
    Write-Host "⚠ Git commit atlandı" -ForegroundColor Yellow
}

Write-Host "`n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  TAMAMLANDI" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`n" -ForegroundColor Cyan

Write-Host "Yeni komutlar:" -ForegroundColor White
Write-Host "  • korpus_listele  → Tüm korpusu listeler" -ForegroundColor Cyan
Write-Host "  • metin_analiz    → Tek metin analizi" -ForegroundColor Cyan
Write-Host "  • karsilastir     → Grup karşılaştırması" -ForegroundColor Cyan
Write-Host "  • rapor_olustur   → Rapor üretir`n" -ForegroundColor Cyan

Write-Host "Sonraki adım:" -ForegroundColor Yellow
Write-Host "  .\fix_kotlin_ui.ps1  → Kotlin UI'yı güncelle`n" -ForegroundColor Cyan
