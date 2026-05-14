"""
visualize.py — Istatistik Sonuçlarının Görselleştirilmesi
GÜN 7: Box plots, heatmaps, forest plots, histograms
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

import json
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats


def load_results():
    """Istatistik sonuçlarını yükle"""
    project_dir = Path(__file__).parent.parent
    
    # JSON sonuçları
    with open(project_dir / "reports" / "output" / "sonuc_ozet.json", "r", encoding="utf-8") as f:
        summary = json.load(f)
    
    # CSV metrikleri
    quran_df = pd.read_csv(project_dir / "reports" / "output" / "quran_metrics.csv")
    control_df = pd.read_csv(project_dir / "reports" / "output" / "control_metrics.csv")
    
    return summary, quran_df, control_df


def plot_boxplots(summary, quran_df, control_df):
    """Box plot: 6 ana metric (F1, F2, F3, spektral, ritmik, nazal)"""
    print("📊 Box Plot Oluşturuluyor...")
    
    metrics_to_plot = [
        ('f1_ortalama', 'F1 Ortalaması (Hz)'),
        ('f2_ortalama', 'F2 Ortalaması (Hz)'),
        ('f3_ortalama', 'F3 Ortalaması (Hz)'),
        ('spektral_merkez_ortalama', 'Spektral Merkez (Hz)'),
        ('ritmik_duzgunluk_npvi', 'Ritmik Düzgünlük (NPVI)'),
        ('nazal_ortalama', 'Nazal Rezonans Ortalaması'),
    ]
    
    fig, axes = plt.subplots(2, 3, figsize=(16, 10))
    fig.suptitle('Kur\'an vs Kontrol: 6 Ana Metrik', fontsize=16, fontweight='bold')
    
    for idx, (metric, label) in enumerate(metrics_to_plot):
        ax = axes[idx // 3, idx % 3]
        
        data_to_plot = [
            quran_df[metric].dropna(),
            control_df[metric].dropna()
        ]
        
        bp = ax.boxplot(data_to_plot, labels=['Kur\'an', 'Kontrol'], patch_artist=True)
        
        # Renk
        colors = ['#3498db', '#e74c3c']
        for patch, color in zip(bp['boxes'], colors):
            patch.set_facecolor(color)
            patch.set_alpha(0.6)
        
        # Anlamlılık notu
        # Find corresponding test result
        anlamli = False
        for result in summary.get('results', []):
            if result.get('metric') == metric:
                p_val = result.get('p_value', 1.0)
                if p_val < 0.001:
                    stars = '***'
                elif p_val < 0.01:
                    stars = '**'
                elif p_val < 0.05:
                    stars = '*'
                else:
                    stars = 'n.s.'
                
                ax.text(0.98, 0.98, f'p={p_val:.4f} {stars}', 
                       transform=ax.transAxes, 
                       verticalalignment='top', 
                       horizontalalignment='right',
                       fontsize=9, 
                       bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
                break
        
        ax.set_ylabel(label, fontsize=10)
        ax.grid(True, alpha=0.3)
    
    plt.tight_layout()
    output_path = Path(__file__).parent.parent / "reports" / "output" / "boxplots.png"
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"✅ {output_path}")
    plt.close()


def plot_heatmap(summary):
    """P-values heatmap: Metrikler × Anlamlilik"""
    print("🔥 Heatmap Oluşturuluyor...")
    
    results = summary.get('results', [])
    
    # Dataframe hazırla
    data_for_heatmap = {
        'Metrik': [r['metric'] for r in results],
        'p-değeri': [r['p_value'] for r in results],
        '-log10(p)': [-np.log10(max(r['p_value'], 1e-10)) for r in results],
    }
    
    df_heatmap = pd.DataFrame(data_for_heatmap)
    
    fig, ax = plt.subplots(figsize=(12, 8))
    
    # -log10(p) değerlerini visualize et
    heatmap_data = df_heatmap[['Metrik', '-log10(p)']].set_index('Metrik').T
    
    sns.heatmap(heatmap_data, annot=True, fmt='.2f', cmap='RdYlGn', 
                cbar_kws={'label': '-log10(p-değeri)'}, ax=ax, linewidths=0.5)
    
    ax.set_title('Istatistiksel Anlamlılık Haritası\n(Kırmızı=Anlamlı, Yeşil=Anlamsız)', 
                fontsize=12, fontweight='bold')
    ax.set_xlabel('')
    
    plt.xticks(rotation=45, ha='right')
    plt.tight_layout()
    
    output_path = Path(__file__).parent.parent / "reports" / "output" / "heatmap_pvalues.png"
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"✅ {output_path}")
    plt.close()


def plot_forest_plot(summary):
    """Forest plot: Cohen's d değerleri"""
    print("🌲 Forest Plot Oluşturuluyor...")
    
    results = summary.get('results', [])
    
    # Cohen's d'si olanları filtrele
    d_values = []
    metrics = []
    
    for r in results:
        if 'cohen_d' in r and pd.notna(r['cohen_d']):
            d_values.append(r['cohen_d'])
            metrics.append(r['metric'][:30])  # Truncate long names
    
    if not d_values:
        print("⚠️  Cohen's d değeri bulunamadı")
        return
    
    fig, ax = plt.subplots(figsize=(10, max(6, len(metrics) * 0.4)))
    
    colors = ['#27ae60' if d > 0.8 else '#f39c12' if d > 0.5 else '#3498db' 
              for d in d_values]
    
    y_pos = np.arange(len(metrics))
    ax.barh(y_pos, d_values, color=colors, alpha=0.7, edgecolor='black')
    
    ax.set_yticks(y_pos)
    ax.set_yticklabels(metrics)
    ax.set_xlabel("Cohen's d (Effect Size)", fontsize=11, fontweight='bold')
    ax.set_title("Effect Size Forest Plot\n(Yeşil=Büyük, Sarı=Orta, Mavi=Küçük)", 
                fontsize=12, fontweight='bold')
    
    # Referans çizgiler
    ax.axvline(x=0.2, color='gray', linestyle='--', alpha=0.5, label='Small (0.2)')
    ax.axvline(x=0.5, color='gray', linestyle='--', alpha=0.5, label='Medium (0.5)')
    ax.axvline(x=0.8, color='gray', linestyle='--', alpha=0.5, label='Large (0.8)')
    
    ax.legend(loc='lower right')
    ax.grid(True, alpha=0.3, axis='x')
    
    plt.tight_layout()
    output_path = Path(__file__).parent.parent / "reports" / "output" / "forest_plot.png"
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"✅ {output_path}")
    plt.close()


def plot_distributions(quran_df, control_df):
    """Histogramlar: Kur'an vs Kontrol dağılımları"""
    print("📈 Histogramlar Oluşturuluyor...")
    
    metrics_to_plot = ['f1_ortalama', 'f2_ortalama', 'f3_ortalama', 
                       'spektral_merkez_ortalama', 'harf_sayisi']
    
    fig, axes = plt.subplots(2, 3, figsize=(16, 10))
    fig.suptitle('Kur\'an vs Kontrol: Dağılım Karşılaştırması', 
                fontsize=14, fontweight='bold')
    
    for idx, metric in enumerate(metrics_to_plot):
        ax = axes[idx // 3, idx % 3]
        
        if metric in quran_df.columns and metric in control_df.columns:
            quran_data = quran_df[metric].dropna()
            control_data = control_df[metric].dropna()
            
            ax.hist(quran_data, bins=15, alpha=0.6, label='Kur\'an', color='#3498db', edgecolor='black')
            ax.hist(control_data, bins=12, alpha=0.6, label='Kontrol', color='#e74c3c', edgecolor='black')
            
            ax.set_xlabel(metric)
            ax.set_ylabel('Frekans')
            ax.legend()
            ax.grid(True, alpha=0.3)
    
    # Son subplot boş ise gizle
    if len(metrics_to_plot) < 6:
        axes[1, 2].axis('off')
    
    plt.tight_layout()
    output_path = Path(__file__).parent.parent / "reports" / "output" / "distributions.png"
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"✅ {output_path}")
    plt.close()


def plot_summary_stats(summary):
    """Özet İstatistikler Tablosu"""
    print("📋 Özet Tablo Oluşturuluyor...")
    
    results = summary.get('results', [])
    
    fig, ax = plt.subplots(figsize=(14, 8))
    ax.axis('off')
    
    # Tablo verisi hazırla
    table_data = []
    table_data.append(['Metrik', 'Test', 'p-değeri', "Cohen's d", 'Anlamlı?'])
    
    for r in results[:10]:  # İlk 10
        metric = r['metric'][:20]
        test = r.get('test', 'N/A')[:15]
        p_val = f"{r['p_value']:.6f}"
        cohen_d = f"{r.get('cohen_d', 'N/A'):.3f}" if r.get('cohen_d') else 'N/A'
        sig = '✓' if r['p_value'] < 0.05 else ''
        
        table_data.append([metric, test, p_val, cohen_d, sig])
    
    table = ax.table(cellText=table_data, cellLoc='left', loc='center',
                    colWidths=[0.25, 0.2, 0.15, 0.15, 0.1])
    table.auto_set_font_size(False)
    table.set_fontsize(9)
    table.scale(1, 1.8)
    
    # Header formatlama
    for i in range(5):
        table[(0, i)].set_facecolor('#3498db')
        table[(0, i)].set_text_props(weight='bold', color='white')
    
    plt.title('İstatistiksel Test Sonuçları Özeti', fontsize=12, fontweight='bold', pad=20)
    
    output_path = Path(__file__).parent.parent / "reports" / "output" / "summary_table.png"
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"✅ {output_path}")
    plt.close()


def main():
    print("="*60)
    print("📊 GÖRSELLEŞTIRME BAŞLIYOR (GÜN 7)")
    print("="*60)
    
    # Sonuçları yükle
    summary, quran_df, control_df = load_results()
    
    print(f"\n✅ Veriler yüklendi:")
    print(f"   Kur'an: {len(quran_df)} satır")
    print(f"   Kontrol: {len(control_df)} satır")
    print(f"   Test sayısı: {len(summary.get('results', []))}")
    
    # Görselleştirmeler
    print("\n📈 Görselleştirmeler oluşturuluyor...")
    
    plot_boxplots(summary, quran_df, control_df)
    plot_heatmap(summary)
    plot_forest_plot(summary)
    plot_distributions(quran_df, control_df)
    plot_summary_stats(summary)
    
    print("\n" + "="*60)
    print("✅ TÜM GÖRSELLEŞTİRMELER TAMAMLANDI")
    print("="*60)
    print("\nÇıktı Dosyaları:")
    print("  - boxplots.png")
    print("  - heatmap_pvalues.png")
    print("  - forest_plot.png")
    print("  - distributions.png")
    print("  - summary_table.png")


if __name__ == "__main__":
    main()
