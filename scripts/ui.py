#!/usr/bin/env python3
"""Small ADB accessibility helper for repeatable emulator smoke checks."""
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

serial = os.environ.get('SALABOLGIL_ADB_SERIAL', 'emulator-5554')
root = Path(__file__).resolve().parents[1]
qa = root / 'artifacts' / 'qa'
qa.mkdir(parents=True, exist_ok=True)

def adb(*args, binary=False):
    return subprocess.check_output(['adb', '-s', serial, *args], text=not binary)

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/salabolgil-ui.xml')
    raw = adb('shell', 'cat', '/sdcard/salabolgil-ui.xml')
    (qa / 'ui.xml').write_text(raw)
    return list(ET.fromstring(raw).iter('node'))

action = sys.argv[1]
if action == 'dump':
    for node in nodes():
        if node.get('text'):
            print(node.get('text'), node.get('bounds'))
elif action == 'tap':
    label = sys.argv[2]
    matches = [n for n in nodes() if n.get('text') == label]
    if not matches:
        raise SystemExit(f'Visible label not found: {label}')
    n = matches[int(sys.argv[3]) if len(sys.argv) > 3 else 0]
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
    print(adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)))
elif action == 'capture':
    target = qa / (sys.argv[2] + '.png')
    target.write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    print(target)
elif action == 'assert':
    content = '\n'.join(n.get('text', '') for n in nodes())
    for phrase in sys.argv[2:]:
        if phrase not in content:
            raise SystemExit(f'Not present in current view: {phrase}')
    print('PASS', ', '.join(sys.argv[2:]))
else:
    raise SystemExit('Usage: ui.py dump|tap LABEL [INDEX]|capture NAME|assert PHRASE...')
