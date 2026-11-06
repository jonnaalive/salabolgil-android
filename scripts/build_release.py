#!/usr/bin/env python3
"""Build a signed release without putting signing credentials into the source tree."""
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
private = Path.home() / '.local' / 'share' / 'salabolgil' / 'signing'
private.mkdir(parents=True, exist_ok=True, mode=0o700)
config_file = private / 'release.json'
keystore = private / 'release.jks'

if not config_file.exists():
    if keystore.exists():
        raise SystemExit('Signing key exists but credentials are missing. Restore release.json; do not replace the key.')
    config = {'password': secrets.token_urlsafe(32), 'alias': 'salabolgil'}
    fd = os.open(config_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(config, stream)
else:
    config = json.loads(config_file.read_text())

env = os.environ.copy()
env.update({
    'SALABOLGIL_KEYSTORE': str(keystore),
    'SALABOLGIL_KEY_ALIAS': config['alias'],
    'SALABOLGIL_STORE_PASSWORD': config['password'],
    'SALABOLGIL_KEY_PASSWORD': config['password'],
})
if not keystore.exists():
    subprocess.run([
        'keytool', '-genkeypair', '-keystore', str(keystore), '-storetype', 'JKS',
        '-alias', config['alias'], '-keyalg', 'RSA', '-keysize', '3072',
        '-validity', '10000', '-dname', 'CN=Salabolgil Development',
        '-storepass:env', 'SALABOLGIL_STORE_PASSWORD',
        '-keypass:env', 'SALABOLGIL_KEY_PASSWORD', '-noprompt',
    ], check=True, env=env)
    keystore.chmod(0o600)

subprocess.run([str(root / 'gradlew'), ':app:assembleRelease', ':app:lintRelease',
                '--console', 'plain'], cwd=root, env=env, check=True)
output = root / 'artifacts'
output.mkdir(exist_ok=True)
apk = output / 'salabolgil_0.1.0_release.apk'
shutil.copy2(root / 'app/build/outputs/apk/release/app-release.apk', apk)
print(f'Signed release: {apk}')
print(f'Private signing key stays outside the project: {private}')
