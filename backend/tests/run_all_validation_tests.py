"""
run_all_validation_tests.py — Tüm QA Testleri (GÜN 6 Özet)
"""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

import subprocess
import os
import json
from datetime import datetime


def run_test_file(test_name, file_path):
    """Test dosyası çalıştır ve sonuç döndür"""
    print(f"\n{'='*60}")
    print(f"⏱️  Çalıştırılıyor: {test_name}")
    print(f"{'='*60}")
    
    # UTF-8 encoding ile çalıştır
    env = os.environ.copy()
    env['PYTHONIOENCODING'] = 'utf-8'
    result = subprocess.run([sys.executable, '-X', 'utf8', str(file_path)], 
                          capture_output=True, 
                          text=True,
                          timeout=300,
                          env=env)
    
    print(result.stdout)
    if result.stderr and "warning" not in result.stderr.lower():
        print("STDERR:", result.stderr)
    
    return {
        'test_name': test_name,
        'exit_code': result.returncode,
        'passed': result.returncode == 0,
        'output': result.stdout
    }


def main():
    print("\n" + "🔬 QA DOĞRULAMA SETİ — GÜN 6 ÖZET".center(70))
    print("=" * 70)
    
    tests = [
        ("Determinizm Testi", Path(__file__).parent / "test_determinism.py"),
        ("Negatif Kontrol Testi", Path(__file__).parent / "test_negative_control.py"),
        ("Pozitif Kontrol Testi", Path(__file__).parent / "test_positive_control.py"),
        ("Sensitivite Analizi", Path(__file__).parent / "test_sensitivity.py"),
    ]
    
    results = []
    for test_name, test_file in tests:
        result = run_test_file(test_name, test_file)
        results.append(result)
    
    # Özet
    print("\n" + "="*70)
    print("📋 ÖZET RAPORLU")
    print("="*70)
    
    summary = {
        'timestamp': datetime.now().isoformat(),
        'total_tests': len(results),
        'passed': sum(1 for r in results if r['passed']),
        'failed': sum(1 for r in results if not r['passed']),
        'results': [
            {
                'test': r['test_name'],
                'status': '✅ PASS' if r['passed'] else '❌ FAIL',
                'exit_code': r['exit_code']
            }
            for r in results
        ]
    }
    
    for r in summary['results']:
        print(f"  {r['status']} — {r['test']}")
    
    print(f"\nToplam: {summary['passed']}/{summary['total_tests']} TEST BAŞARILI")
    
    # JSON raporu kaydet
    report_path = Path(__file__).parent.parent / "reports" / "output" / "qa_dogrulama_raporu.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    
    with open(report_path, 'w', encoding='utf-8') as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)
    
    print(f"✅ Rapor kaydedildi: {report_path}")
    
    print("\n" + "="*70)
    if summary['failed'] == 0:
        print("🎉 TÜM DOĞRULAMA TESTs BAŞARILI — SISTEM HAZIR!")
    else:
        print(f"❌ {summary['failed']} test başarısız")
    print("="*70)
    
    return summary['failed'] == 0


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
