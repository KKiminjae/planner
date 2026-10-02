#!/usr/bin/env python3
"""Package already-built frontend/backend artifacts without credentials."""
import hashlib
import json
from pathlib import Path
import tarfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'build/libs/photo_calendar-0.0.1-SNAPSHOT.jar'
FRONTEND = ROOT / 'frontend/dist'
OUTPUT = ROOT / 'build/release'


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    if not JAR.is_file() or not (FRONTEND / 'index.html').is_file():
        raise SystemExit('Build frontend and bootJar before packaging.')
    with zipfile.ZipFile(JAR) as jar:
        if jar.testzip() or 'META-INF/MANIFEST.MF' not in jar.namelist():
            raise SystemExit('Invalid backend JAR.')
    files = sorted(path for path in FRONTEND.rglob('*') if path.is_file())
    if not any(path.suffix == '.js' for path in files):
        raise SystemExit('Frontend JavaScript assets are missing.')
    if any(path.is_symlink() or path.name.startswith('.') for path in files):
        raise SystemExit('Unexpected frontend artifact.')
    OUTPUT.mkdir(parents=True, exist_ok=True)
    artifacts = [(JAR, 'backend.jar'),
                 (ROOT / 'deploy/lightsail/nginx-https.conf', 'nginx-https.conf')]
    artifacts += [(path, 'frontend/' + path.relative_to(FRONTEND).as_posix()) for path in files]
    manifest = {'files': {name: sha256(path) for path, name in artifacts}}
    manifest_path = OUTPUT / 'manifest.json'
    manifest_path.write_text(json.dumps(manifest, indent=2) + '\n')
    archive_path = OUTPUT / 'photo-calendar-release.tar.gz'
    with tarfile.open(archive_path, 'w:gz') as archive:
        for path, name in artifacts:
            archive.add(path, arcname=name, recursive=False)
        archive.add(manifest_path, arcname='manifest.json')
    with tarfile.open(archive_path, 'r:gz') as archive:
        for name, expected in manifest['files'].items():
            if hashlib.sha256(archive.extractfile(name).read()).hexdigest() != expected:
                raise SystemExit('Archive verification failed.')
    (OUTPUT / 'photo-calendar-release.tar.gz.sha256').write_text(
        sha256(archive_path) + '  photo-calendar-release.tar.gz\n')
    print(f'Verified {len(artifacts)} files: {archive_path.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
