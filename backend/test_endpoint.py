"""POST /analyze/quran endpoint entegrasyon testi."""
import struct
import sys
import urllib.request
import urllib.parse
import json
import io
import numpy as np

# ── Sentetik WAV yaz ────────────────────────────────────────────────────────
sr = 22050
duration = 3.0  # saniye — hızlı test
t = np.linspace(0, duration, int(sr * duration), endpoint=False)
signal = (0.4 * np.sin(2 * np.pi * 220 * t) +
          0.2 * np.sin(2 * np.pi * 440 * t)).astype(np.float32)
pcm = (signal * 32767).astype(np.int16)

def make_wav(samples, sr):
    buf = io.BytesIO()
    n_channels, sampwidth, n_samples = 1, 2, len(samples)
    data_size = n_samples * sampwidth
    buf.write(b'RIFF')
    buf.write(struct.pack('<I', 36 + data_size))
    buf.write(b'WAVE')
    buf.write(b'fmt ')
    buf.write(struct.pack('<IHHIIHH', 16, 1, n_channels, sr,
                          sr * sampwidth, sampwidth, 16))
    buf.write(b'data')
    buf.write(struct.pack('<I', data_size))
    buf.write(samples.tobytes())
    return buf.getvalue()

wav_bytes = make_wav(pcm, sr)

# ── Multipart form-data gönder ───────────────────────────────────────────────
boundary = b'----GuardianTestBoundary1234'

def make_multipart(boundary, wav_data, surah_name):
    body = b''
    body += b'--' + boundary + b'\r\n'
    body += b'Content-Disposition: form-data; name="file"; filename="test.wav"\r\n'
    body += b'Content-Type: audio/wav\r\n\r\n'
    body += wav_data + b'\r\n'
    body += b'--' + boundary + b'\r\n'
    body += b'Content-Disposition: form-data; name="surah_name"\r\n\r\n'
    body += surah_name.encode() + b'\r\n'
    body += b'--' + boundary + b'--\r\n'
    return body

body = make_multipart(boundary, wav_bytes, 'TestSuresi')
req = urllib.request.Request(
    'http://127.0.0.1:8000/analyze/quran',
    data=body,
    method='POST',
    headers={'Content-Type': f'multipart/form-data; boundary={boundary.decode()}'}
)

try:
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = json.loads(resp.read())
except Exception as e:
    print(f"FAIL endpoint hatasi: {e}")
    sys.exit(1)

# ── Sonucu dogrula ───────────────────────────────────────────────────────────
meta = data.get('metadata', {})
feats = data.get('features', {})
umap3d = data.get('umap_3d', [])
segs = data.get('segments', [])

errors = []

if meta.get('surah') != 'TestSuresi':
    errors.append(f"metadata.surah yanlis: {meta.get('surah')}")

if meta.get('sample_rate') != 22050:
    errors.append(f"sample_rate yanlis: {meta.get('sample_rate')}")

for key in ('f0', 'voiced', 'mfcc', 'spectral_centroid',
            'spectral_flux', 'zero_crossing_rate',
            'formant_f1', 'formant_f2', 'rms_energy'):
    if key not in feats:
        errors.append(f"features.{key} eksik")

if len(umap3d) == 0:
    errors.append("umap_3d bos")
else:
    pt = umap3d[0]
    for k in ('x', 'y', 'z', 'frame', 'time_sec', 'color', 'label'):
        if k not in pt:
            errors.append(f"umap_3d[0].{k} eksik")

if errors:
    for e in errors:
        print(f"FAIL {e}")
    sys.exit(1)

n_frames = meta.get('n_frames', 0)
print(f"PASS metadata  — surah={meta['surah']}, sr={meta['sample_rate']}, "
      f"frames={n_frames}, duration={meta['duration_sec']}s")
print(f"PASS features  — {len(feats)} alan mevcut")
print(f"PASS umap_3d   — {len(umap3d)} nokta, "
      f"ilk=({umap3d[0]['x']:.3f}, {umap3d[0]['y']:.3f}, {umap3d[0]['z']:.3f})")
print(f"PASS segments  — {len(segs)} segment tespit edildi")
print(f"PASS color ornek: {umap3d[0]['color']}")
print("ALL ENDPOINT TESTS PASSED")
