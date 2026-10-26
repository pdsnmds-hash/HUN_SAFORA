"""Bridge Codemagic Code Signing Identities to Safora's existing Gradle properties."""
import os
import shutil
from pathlib import Path


def property_value(value):
    escaped = []
    special = {'\\': '\\\\', '\n': '\\n', '\r': '\\r', '\t': '\\t', '\f': '\\f'}
    for char in value:
        if char in special:
            escaped.append(special[char])
        elif char in ' =:#!':
            escaped.append('\\' + char)
        elif ord(char) < 32 or ord(char) > 126:
            data = char.encode('utf-16-be')
            escaped.extend('\\u' + data[i:i + 2].hex() for i in range(0, len(data), 2))
        else:
            escaped.append(char)
    return ''.join(escaped)


def main():
    required = ['CM_BUILD_DIR', 'CM_KEYSTORE_PATH', 'CM_KEYSTORE_PASSWORD', 'CM_KEY_ALIAS', 'CM_KEY_PASSWORD']
    missing = [name for name in required if not os.environ.get(name)]
    if missing:
        raise SystemExit('Missing Codemagic signing variables: ' + ', '.join(missing))
    source = Path(os.environ['CM_KEYSTORE_PATH'])
    if not source.is_file():
        raise SystemExit('Codemagic keystore file is unavailable')
    target = Path(os.environ['CM_BUILD_DIR']) / 'release/signing'
    target.mkdir(parents=True, exist_ok=True, mode=0o700)
    key = target / 'upload-keystore.jks'
    properties = target / 'upload-key.properties'
    # This helper is for a clean CI checkout; never overwrite local private material.
    if key.exists() or properties.exists():
        raise SystemExit('Signing destination already contains files; refusing to overwrite them')
    pairs = {
        'storeFile': key.name,
        'storePassword': os.environ['CM_KEYSTORE_PASSWORD'],
        'keyAlias': os.environ['CM_KEY_ALIAS'],
        'keyPassword': os.environ['CM_KEY_PASSWORD'],
    }
    with key.open('xb') as stream:
        key.chmod(0o600)
        with source.open('rb') as original:
            shutil.copyfileobj(original, stream)
    with properties.open('x', encoding='ascii') as stream:
        properties.chmod(0o600)
        stream.write(''.join(name + '=' + property_value(value) + '\n' for name, value in pairs.items()))
    print('Signing files prepared for Gradle; credential values are not logged.')


if __name__ == '__main__':
    main()
