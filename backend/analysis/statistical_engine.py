"""
statistical_engine.py — İstatistiksel Karşılaştırma
Kur'an vs Kontrol Grupları için hypothesis testing
"""
import json
from pathlib import Path
from typing import Dict, List
import numpy as np
import pandas as pd
from scipy.stats import shapiro, ttest_ind, mannwhitneyu
from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics


def load_corpus(file_path: str) -> List[Dict]:
    """Corpus JSON dosyasını yükle — list veya dict formatı desteklenir"""
    with open(file_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    # {"sureler": [...]} veya {"metinler": [...]} formatı
    if isinstance(data, dict):
        if "sureler" in data:
            return data["sureler"]
        elif "metinler" in data:
            return data["metinler"]
        else:
            # Zaten {id: {...}} dict formatında — listeye çevir
            return list(data.values())
    elif isinstance(data, list):
        return data
    else:
        raise ValueError(f"Bilinmeyen corpus formatı: {file_path}")


def corpus_to_metrics_df(corpus: List[Dict], corpus_name: str) -> pd.DataFrame:
    """Corpus'taki metinleri özniteliklere dönüştür ve DataFrame'e koy"""
    rows = []
    
    for item in corpus:
        text = item.get("metin", "")
        if not text:
            continue
        
        # Feature vector oluştur
        try:
            fv = text_to_feature_vector(text)
            metrics = compute_summary_metrics(fv)
            metrics["metin_id"] = item.get("id", "unknown")
            metrics["corpus"] = corpus_name
            
            # Corpus-specific metadata
            if "sure_no" in item:  # Qur'an
                metrics["sure_no"] = item["sure_no"]
                metrics["sure_adi"] = item.get("sure_adi_tr", "")
            if "kategori" in item:  # Control
                metrics["kategori"] = item["kategori"]
                metrics["yazar"] = item.get("yazar", "")
            
            rows.append(metrics)
        except Exception as e:
            print(f"⚠️  {item.get('id')}: {e}")
            continue
    
    return pd.DataFrame(rows)


def pooled_std(arr1: np.ndarray, arr2: np.ndarray) -> float:
    """Pooled standard deviation (Cohen's d için)"""
    n1, n2 = len(arr1), len(arr2)
    if n1 + n2 <= 2:
        return 1.0
    
    var1 = np.var(arr1, ddof=1)
    var2 = np.var(arr2, ddof=1)
    
    pooled_var = ((n1 - 1) * var1 + (n2 - 1) * var2) / (n1 + n2 - 2)
    return np.sqrt(pooled_var)


def compare_groups(quran_df: pd.DataFrame, control_df: pd.DataFrame, metric: str) -> Dict:
    """
    İki grup için istatistiksel test yap
    """
    quran_values = quran_df[metric].values
    control_values = control_df[metric].values
    
    # NaN değerleri kaldır
    quran_values = quran_values[~np.isnan(quran_values)]
    control_values = control_values[~np.isnan(control_values)]
    
    if len(quran_values) < 2 or len(control_values) < 2:
        return {
            "metric": metric,
            "test": "SKIP",
            "statistic": np.nan,
            "p_value": np.nan,
            "cohen_d": np.nan,
            "quran_mean": np.mean(quran_values),
            "control_mean": np.mean(control_values),
            "anlamli_mi": False,
            "neden_skip": "Yetersiz veri"
        }
    
    # Normallik testi
    _, p_normal_quran = shapiro(quran_values)
    _, p_normal_control = shapiro(control_values)
    
    # Test seçimi
    if p_normal_quran > 0.05 and p_normal_control > 0.05:
        test_name = "Welch's t-test"
        stat, p_value = ttest_ind(quran_values, control_values, equal_var=False)
    else:
        test_name = "Mann-Whitney U"
        stat, p_value = mannwhitneyu(quran_values, control_values, alternative='two-sided')
    
    # Effect size (Cohen's d)
    mean_diff = np.mean(quran_values) - np.mean(control_values)
    pooled_s = pooled_std(quran_values, control_values)
    cohen_d = mean_diff / pooled_s if pooled_s > 0 else 0.0
    
    return {
        "metric": metric,
        "test": test_name,
        "statistic": float(stat),
        "p_value": float(p_value),
        "cohen_d": float(cohen_d),
        "quran_mean": float(np.mean(quran_values)),
        "quran_std": float(np.std(quran_values)),
        "control_mean": float(np.mean(control_values)),
        "control_std": float(np.std(control_values)),
        "anlamli_mi": p_value < 0.05
    }


def bonferroni_correction(results: List[Dict]) -> List[Dict]:
    """Multiple comparison correction (Bonferroni)"""
    n_tests = len(results)
    
    for r in results:
        if np.isnan(r.get("p_value", np.nan)):
            r["p_corrected"] = np.nan
            r["anlamli_bonferroni"] = False
        else:
            r["p_corrected"] = min(r["p_value"] * n_tests, 1.0)
            r["anlamli_bonferroni"] = r["p_corrected"] < 0.05
    
    return results


def run_full_analysis(quran_corpus_path: str, control_corpus_path: str, 
                     output_dir: str = "reports/output") -> Dict:
    """
    Tam analiz pipeline'ı
    """
    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    
    print("📚 Corpus'lar yükleniyor...")
    quran_corpus = load_corpus(quran_corpus_path)
    control_corpus = load_corpus(control_corpus_path)
    
    print("🔄 Metriklere dönüştürülüyor...")
    quran_df = corpus_to_metrics_df(quran_corpus, "Qur'an")
    control_df = corpus_to_metrics_df(control_corpus, "Kontrol")
    
    print(f"  Kur'an: {len(quran_df)} metin")
    print(f"  Kontrol: {len(control_df)} metin")
    
    # Metrik listesi (önemli olanlar)
    metrics_to_test = [
        "f1_ortalama", "f1_std", "f2_ortalama", "f2_std", "f3_ortalama",
        "f2_gecis_yumusakligi", "f3_gecis_yumusakligi",
        "spektral_merkez_ortalama", "spektral_merkez_std",
        "ritmik_duzgunluk_npvi", "ortalama_harf_sure_ms",
        "nazal_oran", "tefhim_orani", "harf_sayisi"
    ]
    
    # Test sonuçları
    results = []
    print("\n📊 İstatistiksel Testler çalışıyor...")
    for metric in metrics_to_test:
        if metric in quran_df.columns and metric in control_df.columns:
            result = compare_groups(quran_df, control_df, metric)
            results.append(result)
            
            symbol = "✓" if result["anlamli_mi"] else "✗"
            print(f"  {symbol} {metric}: p={result['p_value']:.4f}")
    
    # Bonferroni correction
    results = bonferroni_correction(results)
    
    # Convert numpy types to Python native types
    for r in results:
        for key, val in r.items():
            if isinstance(val, (np.bool_)):
                r[key] = bool(val)
            elif isinstance(val, (np.integer)):
                r[key] = int(val)
            elif isinstance(val, (np.floating)):
                r[key] = float(val)
    
    # Summary
    summary = {
        "timestamp": str(pd.Timestamp.now()),
        "quran_n": len(quran_df),
        "control_n": len(control_df),
        "metrics_tested": len(results),
        "significant_uncorrected": sum(1 for r in results if r["anlamli_mi"]),
        "significant_bonferroni": sum(1 for r in results if r["anlamli_bonferroni"]),
        "results": results
    }
    
    # JSON'a kaydet
    output_file = output_path / "sonuc_ozet.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    
    print(f"\n✅ Sonuçlar kaydedildi: {output_file}")
    
    # DataFrame'leri de kaydet (CSV)
    quran_df.to_csv(output_path / "quran_metrics.csv", index=False, encoding="utf-8")
    control_df.to_csv(output_path / "control_metrics.csv", index=False, encoding="utf-8")
    
    return summary


if __name__ == "__main__":
    # Korpus yolları
    project_dir = Path(__file__).parent.parent
    quran_path = project_dir / "data" / "quran_corpus.json"
    control_path = project_dir / "data" / "control_corpus.json"
    output_dir = project_dir / "reports" / "output"
    
    # Analiz çalıştır
    summary = run_full_analysis(str(quran_path), str(control_path), str(output_dir))
    
    # Summary göster
    print(f"\n{'='*60}")
    print(f"İSTATİSTİKSEL SONUÇ ÖZETİ")
    print(f"{'='*60}")
    print(f"Kur'an metinleri: {summary['quran_n']}")
    print(f"Kontrol metinleri: {summary['control_n']}")
    print(f"Test edilen metrikler: {summary['metrics_tested']}")
    print(f"Anlamlı farklar (p<0.05, uncorrected): {summary['significant_uncorrected']}")
    print(f"Anlamlı farklar (p<0.05, Bonferroni): {summary['significant_bonferroni']}")
    print(f"{'='*60}")
    
    # Anlamlı bulguları göster
    significant = [r for r in summary["results"] if r["anlamli_bonferroni"]]
    if significant:
        print(f"\n🎯 Bonferroni-corrected anlamlı farklar:")
        for r in significant:
            print(f"  - {r['metric']}: Kur'an {r['quran_mean']:.2f} vs Kontrol {r['control_mean']:.2f}")
            print(f"    (Cohen's d={r['cohen_d']:.3f}, p_corrected={r['p_corrected']:.4f})")
    else:
        print(f"\n⚠️  Bonferroni-corrected anlamlı fark YOK")
