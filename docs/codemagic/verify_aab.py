"""Check AAB JAR signing, compare signer to Codemagic's key, and check four ABI."""
import base64
import hashlib
import os
import re
import subprocess
import sys
import zipfile
from pathlib import Path


def run(args):
    return subprocess.run(args, check=True, capture_output=True, text=True).stdout


def certificate_digest(pem):
    match = re.search(r'-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----', pem, re.S)
    if not match:
        raise SystemExit('No signing certificate was found')
    return hashlib.sha256(base64.b64decode(match.group(1))).hexdigest()


def main():
    aab = Path(sys.argv[1] if len(sys.argv) > 1 else 'app/build/outputs/bundle/release/app-release.aab')
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin'
    verified = run([str(java_bin / 'jarsigner'), '-J-Duser.language=en', '-verify', str(aab)])
    if 'jar verified.' not in verified:
        raise SystemExit('AAB is not a verified signed JAR')
    signed_pem = run([str(java_bin / 'keytool'), '-printcert', '-rfc', '-jarfile', str(aab)])
    expected_pem = run([str(java_bin / 'keytool'), '-exportcert', '-rfc',
                        '-keystore', os.environ['CM_KEYSTORE_PATH'],
                        '-alias', os.environ['CM_KEY_ALIAS'],
                        '-storepass:env', 'CM_KEYSTORE_PASSWORD'])
    signer = certificate_digest(signed_pem)
    if signer != certificate_digest(expected_pem):
        raise SystemExit('AAB signer differs from the configured Codemagic keystore')
    with zipfile.ZipFile(aab) as archive:
        abis = {name.split('/')[2] for name in archive.namelist()
                if name.startswith('base/lib/') and name.endswith('.so')}
        if abis != {'armeabi-v7a', 'arm64-v8a', 'x86', 'x86_64'}:
            raise SystemExit('Unexpected native ABI set: ' + repr(sorted(abis)))
        if any(name.endswith(('.jks', '.keystore')) or 'upload-key.properties' in name
               for name in archive.namelist()):
            raise SystemExit('Private signing files found inside the AAB')
    print('Verified signed AAB; uploaded key matches; four ABI present; no keystore embedded.')
    print('Signer SHA-256 (public): ' + signer)
    print('AAB SHA-256: ' + hashlib.sha256(aab.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
