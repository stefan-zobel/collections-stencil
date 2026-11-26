#!/usr/bin/env python3
"""Builds the Maven Central upload bundle of a release.

1. Runs the Maven build with the ``release`` profile (``clean verify``, all modules and tests),
   which attaches the sources and Javadoc jars.
2. Collects the published artifacts in the Maven repository layout: the parent POM, and for
   generator, annotations, and processor the POM, the jar, the sources jar, and the Javadoc jar.
3. Signs every file with gpg (detached ASCII signature, ``.asc``) and writes MD5 and SHA-1
   checksums.
4. Packs everything into ``target/central-bundle/<name>-<version>-bundle.zip`` for the upload
   in the Central Portal (Publish Component, upload the zip).

Usage::

    python scripts/release_bundle.py [--key KEYID] [--gpg PATH] [--skip-tests] [--no-build]
                                     [--no-sign]

The JDK for the build comes from JAVA_HOME (JDK 25 or later). gpg asks for the passphrase of
the key through its agent. Without --key, gpg signs with its default key.
"""

import argparse
import hashlib
import os
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
POM_NS = {'m': 'http://maven.apache.org/POM/4.0.0'}

# Published modules (directory relative to ROOT); "." is the parent POM. core and bench are
# never published.
PUBLISHED = ['.', 'generator', 'annotations', 'processor']


def fail(message):
    print('error: ' + message, file=sys.stderr)
    sys.exit(1)


def pom_info(module_dir):
    """Returns (groupId, artifactId, version, packaging) of a module POM (inherited from the parent)."""
    root = ET.parse(module_dir / 'pom.xml').getroot()

    def text(path):
        node = root.find(path, POM_NS)
        return node.text.strip() if node is not None and node.text else None

    group = text('m:groupId') or text('m:parent/m:groupId')
    version = text('m:version') or text('m:parent/m:version')
    return group, text('m:artifactId'), version, text('m:packaging') or 'jar'


def run_maven(skip_tests):
    mvnw = ROOT / ('mvnw.cmd' if os.name == 'nt' else 'mvnw')
    cmd = [str(mvnw), '-B', 'clean', 'verify', '-Prelease']
    if skip_tests:
        cmd.append('-DskipTests')
    print('>', ' '.join(cmd), flush=True)
    if subprocess.run(cmd, cwd=ROOT).returncode != 0:
        fail('the Maven build failed')


def check_jar(path, kind):
    """Checks that a jar holds what its kind promises (classes, sources, or Javadoc)."""
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
    want = {'classes': '.class', 'sources': '.java', 'javadoc': 'index.html'}[kind]
    if not any(n.endswith(want) for n in names):
        fail(f'{path.name} contains no {want} entry')


def collect(staging):
    """Copies the artifacts into staging in the repository layout; returns the copied files."""
    files = []
    expected_version = None
    for module in PUBLISHED:
        module_dir = ROOT / module
        group, artifact, version, packaging = pom_info(module_dir)
        if expected_version is None:
            expected_version = version
        if version != expected_version:
            fail(f'{artifact} has version {version}, the parent has {expected_version}')
        if version.endswith('-SNAPSHOT'):
            fail(f'{version} is a snapshot version; Central accepts releases only')
        target_dir = staging.joinpath(*group.split('.'), artifact, version)
        target_dir.mkdir(parents=True)
        base = f'{artifact}-{version}'
        pom = target_dir / f'{base}.pom'
        shutil.copyfile(module_dir / 'pom.xml', pom)
        files.append(pom)
        if packaging == 'pom':
            continue
        for suffix, kind in (('', 'classes'), ('-sources', 'sources'), ('-javadoc', 'javadoc')):
            source = module_dir / 'target' / f'{base}{suffix}.jar'
            if not source.is_file():
                fail(f'{source} is missing (was the release profile active?)')
            check_jar(source, kind)
            copy = target_dir / source.name
            shutil.copyfile(source, copy)
            files.append(copy)
    return files, expected_version


def sign(files, gpg, key):
    """Writes a detached ASCII signature next to every file and verifies it."""
    for f in files:
        cmd = [gpg, '--armor', '--detach-sign', '--yes', '--output', f'{f}.asc']
        if key:
            cmd += ['--local-user', key]
        if subprocess.run(cmd + [str(f)]).returncode != 0:
            fail(f'gpg could not sign {f.name}')
    signers = set()
    for f in files:
        result = subprocess.run([gpg, '--status-fd', '1', '--verify', f'{f}.asc', str(f)],
                                capture_output=True, text=True)
        valid = [line.split()[2] for line in result.stdout.splitlines()
                 if line.startswith('[GNUPG:] VALIDSIG')]
        if result.returncode != 0 or not valid:
            fail(f'the signature of {f.name} does not verify:\n{result.stderr}')
        signers.update(valid)
    return signers


def write_checksums(files):
    for f in files:
        data = f.read_bytes()
        for algorithm in ('md5', 'sha1'):
            Path(f'{f}.{algorithm}').write_text(hashlib.new(algorithm, data).hexdigest(), encoding='ascii')


def main():
    parser = argparse.ArgumentParser(description='Builds the Maven Central upload bundle of a release.')
    parser.add_argument('--key', help='gpg key (id or fingerprint) to sign with; default: the gpg default key')
    parser.add_argument('--gpg', default='gpg', help='gpg executable (default: gpg on the PATH)')
    parser.add_argument('--skip-tests', action='store_true', help='build without running the tests')
    parser.add_argument('--no-build', action='store_true',
                        help='use the artifacts of a previous release build instead of building')
    parser.add_argument('--no-sign', action='store_true', help='unsigned bundle, for checking the content only')
    args = parser.parse_args()

    gpg = None
    if not args.no_sign:
        gpg = shutil.which(args.gpg)
        if gpg is None:
            fail(f'gpg not found: {args.gpg} (use --gpg)')

    if not args.no_build:
        run_maven(args.skip_tests)

    out_dir = ROOT / 'target' / 'central-bundle'
    staging = out_dir / 'staging'
    if out_dir.exists():
        try:
            shutil.rmtree(out_dir)
        except OSError as e:
            fail(f'cannot delete the previous bundle in {out_dir} (still open somewhere?): {e}')
    staging.mkdir(parents=True)

    files, version = collect(staging)
    if gpg:
        signers = sign(files, gpg, args.key)
        print('signed by key ' + ', '.join(sorted(signers)))
    write_checksums(files)

    name = pom_info(ROOT)[1].removesuffix('-parent')
    bundle = out_dir / f'{name}-{version}-bundle{"-unsigned" if args.no_sign else ""}.zip'
    entries = sorted(p for p in staging.rglob('*') if p.is_file())
    with zipfile.ZipFile(bundle, 'w', zipfile.ZIP_DEFLATED) as zf:
        for p in entries:
            zf.write(p, p.relative_to(staging).as_posix())
    print(f'{len(entries)} files in {bundle}')
    for p in entries:
        print('  ' + p.relative_to(staging).as_posix())


if __name__ == '__main__':
    main()
