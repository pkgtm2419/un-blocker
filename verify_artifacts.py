#!/usr/bin/env python3
"""Read-only validation of pre-existing, downloaded Un-Blocker CI artifacts."""
import collections
import datetime
import io
import json
import os
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

base = Path('/mnt/data')
apks = base / 'unblocker-ci-apks.zip'
reports = base / 'unblocker-ci-reports.zip'
assert apks.exists() and reports.exists(), 'Missing downloaded CI files'
with zipfile.ZipFile(apks) as b, zipfile.ZipFile(reports) as rep:
    apkbytes = b.read('releaseTest/ub-blocker-2.2.0.apk')
    with zipfile.ZipFile(io.BytesIO(apkbytes)) as apk:
        native = [info for info in apk.infolist() if info.filename.endswith('.so')]
        native_size = sum(info.file_size for info in native)
        models = [info.filename for info in apk.infolist() if info.filename.endswith(('.tflite', '.onnx'))]
        arm64 = next(i for i in native if i.filename == 'lib/arm64-v8a/libtensorflowlite_jni.so')
        with tempfile.NamedTemporaryFile(suffix='.so') as temp:
            temp.write(apk.read(arm64));temp.flush()
            result = subprocess.run(['readelf','-lW',temp.name],capture_output=True,text=True,check=True)
        load_alignments = [int(line.split()[-1],16) for line in result.stdout.splitlines() if line.strip().startswith('LOAD ')]
        entries = len(apk.namelist())
    suites = []
    for path in rep.namelist():
        if path.endswith('.xml') and '/TEST-' in path:
            el = ET.fromstring(rep.read(path))
            suites.append({key:int(el.get(key,'0')) for key in ('tests','failures','errors','skipped')})
    assert suites and sum(s['tests'] for s in suites)==260
    lint = {}
    for path in rep.namelist():
        if path.endswith('/lint-results-release.html'):
            html=rep.read(path).decode('utf-8','replace')
            mt=re.search(r'Lint Report:\s*(\d+) warnings',re.sub('<[^>]+>',' ',html))
            lint[path] = {'warning_count':int(mt.group(1)) if mt else None,
                         'has_16kb_warning':'not 16 KB aligned' in html,
                         'has_idle_charging_warning':'IdleBatteryChargingConstraints' in html}
    summary={
        'evidence':'GitHub Actions CI artifacts from run 41342081066, commit 6fd537e34773e3f799e3685f68a0fbef761eb71c',
        'timestamp_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds'),
        'release_test_apk_bytes':len(apkbytes),
        'native_so_count':len(native),
        'native_so_uncompressed_bytes':native_size,
        'native_share_percent':round(100*native_size/len(apkbytes),2),
        'model_files':models,
        'arm64_tflite_jni_load_alignment_bytes':load_alignments,
        'total_junit_test_suites':len(suites),
        'junit':{k:sum(s[k] for s in suites) for k in ('tests','failures','errors','skipped')},
        'lint_release':lint,
        'interpretation':'Static artifact verification; not a physical device performance benchmark or an installed-APK runtime test.'
    }
    assert 0<len(load_alignments)<5 and all(x==4096 for x in load_alignments)
    assert len(native)==8
    assert not models
    out=base/'unblocker-improvement-2026-11-02'/'artifact-evidence.json'
    out.write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(summary,indent=2))
