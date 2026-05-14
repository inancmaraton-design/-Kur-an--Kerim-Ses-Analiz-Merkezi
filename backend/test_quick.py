import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))

print('Import test...')
from analysis.text_parser import tokenize_arabic
from analysis.feature_vector import text_to_feature_vector, compute_summary_metrics
from data.harf_acoustics import HARF_ACOUSTICS
print('Import OK!')

print('\nAnaliz test...')
import json
text = 'بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ'
fv = text_to_feature_vector(text)
metrics = compute_summary_metrics(fv)
print('Analiz OK!')
print('\nMetrics:')
print(json.dumps(metrics, indent=2, ensure_ascii=False))
