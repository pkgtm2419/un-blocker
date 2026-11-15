#!/usr/bin/env python3
"""Check or format project Kotlin with a pinned, checksum-verified ktlint CLI.

Requires JDK 17+. Use --download-tool to fetch the formatter into ignored tooling.
Android SDK configuration is not required for formatting.
"""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import urllib.request

KTLINT_VERSION = '1.8.0'
KTLINT_SHA256 = '369ad2b789f95a011f807e1fcb690ccef80bd7cd014fd139e73ae82dcc0baeab'
KTLINT_URL = (
    'https://repo.maven.apache.org/maven2/com/pinterest/ktlint/ktlint-cli/'
    f'{KTLINT_VERSION}/ktlint-cli-{KTLINT_VERSION}-all.jar'
)
MODULES = ('app', 'common', 'data-store', 'ml-engine', 'vpn-engine')


def kotlin_files(root: Path) -> list[Path]:
    """Select maintained source and build scripts, excluding generated outputs."""
    files = list(root.glob('*.gradle.kts'))
    for module in MODULES:
        files.extend((root / module / 'src').rglob('*.kt'))
        build = root / module / 'build.gradle.kts'
        if build.is_file():
            files.append(build)
    return sorted(set(files))


def verify_jar(path: Path) -> None:
    """Reject a formatter artifact that differs from the reviewed release hash."""
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != KTLINT_SHA256:
        raise ValueError(f'SHA-256 mismatch for {path}; expected {KTLINT_SHA256}, got {digest}')


def obtain_jar(path: Path, download: bool) -> Path:
    """Fetch only when explicitly requested and verify before Java executes it."""
    if not path.is_file():
        if not download:
            raise FileNotFoundError(f'{path} missing; use --download-tool for ktlint {KTLINT_VERSION}')
        path.parent.mkdir(parents=True, exist_ok=True)
        partial = path.with_suffix('.download')
        try:
            with urllib.request.urlopen(KTLINT_URL, timeout=90) as response, partial.open('wb') as out:
                shutil.copyfileobj(response, out)
            verify_jar(partial)
            partial.replace(path)
        finally:
            partial.unlink(missing_ok=True)
    verify_jar(path)
    return path


def main(argv: list[str] | None = None) -> int:
    """Run the pinned formatter and preserve its failing exit status."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--format', action='store_true', help='Apply supported formatting corrections')
    parser.add_argument('--download-tool', action='store_true', help='Fetch the pinned formatter if absent')
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--java', help='Java executable; otherwise JAVA_HOME, PATH or portable project JDK')
    parser.add_argument('--report', type=Path, help='Optional ktlint JSON report destination')
    args = parser.parse_args(argv)
    root = args.root.resolve()
    try:
        jar = obtain_jar(root / '.tooling/ktlint.jar', args.download_tool)
        java = args.java
        if not java and os.environ.get('JAVA_HOME'):
            java = str(Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
        java = java or shutil.which('java')
        if not java:
            portable = sorted((root / '.tooling/jdk').glob('*/bin/java.exe'))
            java = str(portable[0]) if portable else None
        if not java:
            raise FileNotFoundError('JDK 17+ required; set JAVA_HOME or pass --java')
        command = [java, '-jar', str(jar), '--relative']
        if args.format:
            command.append('--format')
        if args.report:
            report = args.report.resolve()
            report.parent.mkdir(parents=True, exist_ok=True)
            command.append(f'--reporter=json,output={report}')
        files = kotlin_files(root)
        if not files:
            raise ValueError('No Kotlin source/build scripts found')
        command.append('--patterns-from-stdin=;')
        patterns = [path.relative_to(root).as_posix() for path in files]
        patterns.extend(('!**/.tooling/**', '!**/.style-artifacts/**', '!**/build/**', '!**/.gradle/**'))
        result = subprocess.run(
            command, cwd=root, input=';'.join(patterns), text=True, encoding='utf-8',
            capture_output=True, check=False
        )
        print(result.stdout, end='')
        print(result.stderr, end='', file=sys.stderr)
        if 'No files matched' in result.stdout + result.stderr:
            raise ValueError('Formatter matched no files; style was not checked')
        return result.returncode
    except (OSError, ValueError) as error:
        print(f'kotlin_style: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
