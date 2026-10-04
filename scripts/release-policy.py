"""Fail-closed release preflight; never prints signing secrets."""
import os
import pathlib
import re

TEST_SIGNING_FIELDS = ('TEST_KEYSTORE_BASE64', 'UB_BLOCKER_KEYSTORE_PASSWORD',
                      'UB_BLOCKER_KEY_ALIAS', 'UB_BLOCKER_KEY_PASSWORD')
RELEASE_SIGNING_FIELDS = ('RELEASE_KEYSTORE_BASE64', 'UB_RELEASE_KEYSTORE_PASSWORD',
                         'UB_RELEASE_KEY_ALIAS', 'UB_RELEASE_KEY_PASSWORD')
SIGNING_FIELDS = RELEASE_SIGNING_FIELDS


def validate_release(version, tag, signing):
    if not re.fullmatch(r'\d+\.\d+\.\d+', version):
        raise ValueError('Invalid application version')
    if tag not in ('v' + version, 'test-v' + version):
        raise ValueError('Release tag must exactly match application version')
    prerelease = tag.startswith('test-v')
    required_fields = TEST_SIGNING_FIELDS if prerelease else RELEASE_SIGNING_FIELDS
    if not all(signing.get(name, '').strip() for name in required_fields):
        mode = 'test' if prerelease else 'production release'
        raise ValueError(f'{mode} signing secrets required; cannot publish without configured keys')
    return dict(tag=tag, version=version, apk_name=f'ub-blocker-{version}.apk',
                prerelease=prerelease, build_type='debug' if prerelease else 'release')


if __name__ == '__main__':
    gradle = pathlib.Path('app/build.gradle.kts').read_text()
    version = re.search(r'^\s*versionName\s*=\s*"([^"]+)"', gradle, re.MULTILINE).group(1)
    tag = os.environ.get('RELEASE_INPUT_TAG') or os.environ.get('GITHUB_REF_NAME', '')
    release = validate_release(version, tag, os.environ)
    with open(os.environ['GITHUB_ENV'], 'a', encoding='utf-8') as output:
        for key, value in dict(RELEASE_TAG=release['tag'], VERSION_NAME=version,
                              BUILD_TYPE=release['build_type'],
                              PRERELEASE_FLAG='--prerelease' if release['prerelease'] else '',
                              APK_PATH=f"app/build/outputs/apk/{release['build_type']}/{release['apk_name']}").items():
            output.write(f'{key}={value}\n')
    print(f"Validated {tag}: stable-signed {release['build_type']} APK")
