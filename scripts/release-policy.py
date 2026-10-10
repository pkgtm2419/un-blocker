"""Fail-closed release policy. Never prints secret values.

Modes:
  preflight  validate tag format, tag == app version, and (for production tags) that the tag commit is on main.
             Writes outputs to $GITHUB_OUTPUT. Needs no secrets.
  secrets    verify that the signing secrets required for the build type are present (names only are printed).
"""
import argparse
import os
import pathlib
import re
import subprocess
import sys

TAG_RE = re.compile(r'^(?P<prefix>test-v|v)(?P<version>\d+\.\d+\.\d+)$')
TEST_FIELDS = ('UB_BLOCKER_KEYSTORE_BASE64', 'UB_BLOCKER_KEYSTORE_PASSWORD',
               'UB_BLOCKER_KEY_ALIAS', 'UB_BLOCKER_KEY_PASSWORD')
RELEASE_FIELDS = ('UB_RELEASE_KEYSTORE_BASE64', 'UB_RELEASE_KEYSTORE_PASSWORD',
                  'UB_RELEASE_KEY_ALIAS', 'UB_RELEASE_KEY_PASSWORD')


def parse_tag(tag):
    match = TAG_RE.fullmatch(tag or '')
    if not match:
        raise ValueError(f"Invalid tag '{tag}': must match (v|test-v)MAJOR.MINOR.PATCH")
    return match.group('version'), match.group('prefix') == 'test-v'


def read_version_name(gradle_file='app/build.gradle.kts'):
    text = pathlib.Path(gradle_file).read_text(encoding='utf-8')
    match = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', text, re.MULTILINE)
    if not match:
        raise ValueError(f'versionName not found in {gradle_file}')
    return match.group(1)


def validate_release(version_name, tag):
    version, prerelease = parse_tag(tag)
    if not re.fullmatch(r'\d+\.\d+\.\d+', version_name):
        raise ValueError('Invalid application versionName')
    if version != version_name:
        raise ValueError(f'Tag {tag} does not match versionName {version_name}; bump the version first')
    build_type = 'debug' if prerelease else 'release'
    return {
        'tag': tag, 'version': version, 'prerelease': prerelease, 'build_type': build_type,
        'environment': 'test-release' if prerelease else 'production',
        'apk_path': f'app/build/outputs/apk/{build_type}/ub-blocker-{version}.apk',
    }


def tag_is_on_main(tag, main_ref='origin/main'):
    result = subprocess.run(['git', 'merge-base', '--is-ancestor', f'refs/tags/{tag}^{{commit}}', main_ref],
                            capture_output=True, check=False)
    return result.returncode == 0


def commit_of(tag):
    result = subprocess.run(['git', 'rev-parse', '--verify', '--quiet', f'refs/tags/{tag}^{{commit}}'],
                            capture_output=True, text=True, check=False)
    return result.stdout.strip() if result.returncode == 0 else None


def sibling_tag_conflict(tag):
    """One version must map to exactly one commit: v1.2.3 and test-v1.2.3 may both exist, but only on the same commit."""
    version, prerelease = parse_tag(tag)
    sibling = f'v{version}' if prerelease else f'test-v{version}'
    mine, other = commit_of(tag), commit_of(sibling)
    if mine and other and mine != other:
        return f'{sibling} points to a different commit than {tag}; one version must map to one commit (bump the version instead of moving tags)'
    return None


def missing_secrets(environ, prerelease):
    fields = TEST_FIELDS if prerelease else RELEASE_FIELDS
    return [name for name in fields if not environ.get(name, '').strip()]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest='mode', required=True)
    pre = sub.add_parser('preflight')
    pre.add_argument('--tag', required=True)
    pre.add_argument('--github-output', default=os.environ.get('GITHUB_OUTPUT'))
    pre.add_argument('--gradle-file', default='app/build.gradle.kts')
    pre.add_argument('--skip-main-check', action='store_true')
    sec = sub.add_parser('secrets')
    sec.add_argument('--build-type', choices=('debug', 'release'), required=True)
    args = parser.parse_args(argv)

    try:
        if args.mode == 'preflight':
            release = validate_release(read_version_name(args.gradle_file), args.tag)
            if not release['prerelease'] and not args.skip_main_check and not tag_is_on_main(args.tag):
                raise ValueError(f"Production tag {args.tag} must point to a commit on main")
            conflict = sibling_tag_conflict(args.tag)
            if conflict:
                raise ValueError(conflict)
            lines = [f'{key}={str(value).lower() if isinstance(value, bool) else value}' for key, value in release.items()]
            if args.github_output:
                with open(args.github_output, 'a', encoding='utf-8') as out:
                    out.write('\n'.join(lines) + '\n')
            print(f"Preflight OK: {release['tag']} -> {release['build_type']} build, environment '{release['environment']}'")
        else:
            missing = missing_secrets(os.environ, args.build_type == 'debug')
            if missing:
                print('Missing signing secrets (names only): ' + ', '.join(missing), file=sys.stderr)
                print('Create them in the matching GitHub Environment (see docs).', file=sys.stderr)
                return 1
            print('Signing secrets present.')
    except ValueError as error:
        print(f'Error: {error}', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
