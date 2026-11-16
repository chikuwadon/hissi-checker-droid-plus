#!/usr/bin/env python3
"""Validate an actual Gradle resolution snapshot before any GitHub submission.

A snapshot must contain *selected* module versions for buildscript and Android
compile/runtime/test classpaths, and keep all transitive links intact.
"""
import json
import sys
from pathlib import Path

EXPECTED = {
    'org.bouncycastle:bcprov-jdk18on': '1.87',
    'org.bouncycastle:bcpkix-jdk18on': '1.87',
    'org.bouncycastle:bcutil-jdk18on': '1.87',
    'com.google.guava:guava': '33.8.0-jre',
    'org.apache.commons:commons-lang3': '3.22.0',
    'org.apache.httpcomponents:httpclient': '4.5.14',
    'org.jetbrains.kotlin:kotlin-gradle-plugin': '2.4.21',
    'org.jdom:jdom2': '2.0.6.1',
    'org.bitbucket.b_c:jose4j': '0.9.7',
}

def validate(snapshot: dict) -> list[str]:
    failures = []
    if snapshot.get('version') != 0:
        failures.append('Snapshot must use GitHub dependency-submission schema version 0')
    manifest = snapshot.get('manifests', {}).get('settings.gradle', {})
    if manifest.get('file', {}).get('source_location') != 'settings.gradle':
        failures.append('Manifest must be linked to settings.gradle')
    resolved = manifest.get('resolved', {})
    if len(resolved) < 20:
        failures.append(f'Unexpectedly small graph: {len(resolved)} nodes')
    for key in ('com.android.tools.build:gradle:9.5.1', 'org.jsoup:jsoup:1.24.1'):
        if key not in resolved:
            failures.append(f'Core dependency missing: {key}')
    for module, expected in EXPECTED.items():
        selected = sorted({key.removeprefix(module + ':')
                           for key in resolved if key.startswith(module + ':')})
        if selected != [expected]:
            failures.append(f'{module}: detected {selected}, expected [{expected}]')
    for module, node in resolved.items():
        parts = module.split(':')
        if len(parts) != 3 or not all(parts):
            failures.append(f'Invalid Maven coordinate: {module}')
        if node.get('relationship') not in ('direct', 'indirect'):
            failures.append(f'Invalid relationship: {module}')
        for dependency in node.get('dependencies', []):
            if dependency not in resolved:
                failures.append(f'Dangling edge: {module} -> {dependency}')
    return failures

if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit('Usage: validate_resolved_dependency_snapshot.py <snapshot.json>')
    snapshot = json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))
    problems = validate(snapshot)
    if problems:
        for issue in problems:
            print('ERROR: ' + issue, file=sys.stderr)
        sys.exit(1)
    count = len(snapshot['manifests']['settings.gradle']['resolved'])
    print(f'Validated {count} actually selected Gradle dependencies; all nine patched '
          'buildscript modules present, no stale versions, all dependency edges valid.')
