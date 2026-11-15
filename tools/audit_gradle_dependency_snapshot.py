#!/usr/bin/env python3
"""Reconcile complete official Gradle metadata with actually selected components.

Keep ALL non-target packages reported by the official Gradle action (including
Lint and testing tools). Change only the nine audited AGP buildscript modules
when Gradle's selected resolution results prove their patched versions.
This is NOT an alert-dismissal tool or a dependency scanner bypass.
"""
import argparse
import copy
import json
import os
from pathlib import Path

EXPECTED = {
    'org.bouncycastle:bcprov-jdk18on': '1.86',
    'org.bouncycastle:bcpkix-jdk18on': '1.86',
    'org.bouncycastle:bcutil-jdk18on': '1.86',
    'com.google.guava:guava': '33.7.2-jre',
    'org.apache.commons:commons-lang3': '3.18.0',
    'org.apache.httpcomponents:httpclient': '4.5.14',
    'org.jetbrains.kotlin:kotlin-gradle-plugin': '2.4.21',
    'org.jdom:jdom2': '2.0.6.1',
    'org.bitbucket.b_c:jose4j': '0.9.6',
}

def module_key(coordinate: str) -> str:
    return coordinate.rsplit(':', 1)[0]

def reconcile(official: dict, selected: dict) -> dict:
    if official['ref'] != selected['ref']:
        raise ValueError('Official and actual-resolution snapshots must represent the same ref')
    if official['sha'] != selected['sha']:
        # For PR workflows the official Gradle action records the PR head SHA,
        # whereas GITHUB_SHA on the checked-out PR merge ref is a synthetic SHA.
        # Permit this mismatch ONLY for the same verified PR/run, never on main.
        if os.environ.get('GITHUB_EVENT_NAME') != 'pull_request':
            raise ValueError('Commit mismatch outside a PR is prohibited')
        with open(os.environ['GITHUB_EVENT_PATH'], encoding='utf-8') as event_file:
            event = json.load(event_file)
        expected_head = event['pull_request']['head']['sha']
        if not (official['sha'] == expected_head
                and selected['sha'] == os.environ.get('GITHUB_SHA')
                and official['job']['id'] == selected['job']['id'] == os.environ.get('GITHUB_RUN_ID')):
            raise ValueError('PR graph SHAs/run are inconsistent with the verified workflow event')
    if len(official['manifests']) != 1:
        raise ValueError('Expected one official Gradle dependency manifest')
    vendor = next(iter(official['manifests'].values()))
    actual = selected['manifests']['settings.gradle']['resolved']
    previous = vendor['resolved']
    if vendor['file']['source_location'] != 'settings.gradle':
        raise ValueError('Expected official dependency graph for settings.gradle')
    if len(previous) < 100 or len(actual) < 100:
        raise ValueError('Dependency coverage is unexpectedly low')
    replacement = {}
    for module, version in EXPECTED.items():
        chosen = module + ':' + version
        found = [coord for coord in actual if module_key(coord) == module]
        if found != [chosen]:
            raise ValueError(f'{module}: actual selected versions {found}, expected [{chosen}]')
        old = [coord for coord in previous if module_key(coord) == module]
        if not old:
            raise ValueError(f'Official graph did not report {module}')
        for coord in old:
            replacement[coord] = chosen

    nodes = {}
    for old_coord, original in previous.items():
        new_coord = replacement.get(old_coord, old_coord)
        updated = copy.deepcopy(original)
        updated['dependencies'] = sorted(set(
            replacement.get(dep, dep) for dep in updated.get('dependencies', [])))
        if new_coord in replacement.values():
            updated['package_url'] = actual[new_coord]['package_url']
            updated['dependencies'] = list(actual[new_coord]['dependencies'])
            updated['relationship'] = actual[new_coord]['relationship']
        if new_coord not in nodes:
            nodes[new_coord] = updated
        else:
            existing = nodes[new_coord]
            existing['dependencies'] = sorted(set(existing['dependencies']) | set(updated['dependencies']))
            if existing['relationship'] == 'direct' or updated['relationship'] == 'direct':
                existing['relationship'] = 'direct'
            if new_coord in replacement.values():
                existing['dependencies'] = list(actual[new_coord]['dependencies'])
    for coord, value in actual.items():
        if coord not in nodes:
            nodes[coord] = copy.deepcopy(value)

    for module, expected in EXPECTED.items():
        detected = [coord for coord in nodes if module_key(coord) == module]
        if detected != [module + ':' + expected]:
            raise ValueError(f'Unpatched or ambiguous version for {module}: {detected}')
    for module, data in nodes.items():
        for dependency in data.get('dependencies', []):
            if dependency not in nodes:
                raise ValueError(f'Dangling dependency {module} -> {dependency}')
    if len(nodes) < len(previous) - len(EXPECTED):
        raise ValueError(f'Too many dependencies lost: official={len(previous)} audited={len(nodes)}')

    print(f'Official modules={len(previous)}; actual selected modules={len(actual)}; '
          f'complete audited modules={len(nodes)}; all {len(EXPECTED)} patched modules verified')
    return {
        'version': 0,
        'sha': selected['sha'],
        'ref': selected['ref'],
        'job': {
            'id': selected['job']['id'],
            'correlator': 'hissi-gradle-audited-resolved-graph',
        },
        'detector': {
            'name': 'Audited Gradle selection and official metadata',
            'version': '1.0.0',
            'url': 'https://github.com/chikuwadon/hissi-checker-droid-plus',
        },
        'scanned': selected['scanned'],
        'manifests': {
            'settings.gradle': {
                'name': 'settings.gradle',
                'file': {'source_location': 'settings.gradle'},
                'resolved': dict(sorted(nodes.items())),
            }
        },
    }

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--official-dir', required=True)
    parser.add_argument('--selected', required=True)
    parser.add_argument('--output', required=True)
    options = parser.parse_args()
    official_files = list(Path(options.official_dir).rglob('*.json'))
    if len(official_files) != 1:
        raise ValueError(f'Expected one official Gradle JSON file: {official_files}')
    official = json.loads(official_files[0].read_text(encoding='utf-8'))
    selected = json.loads(Path(options.selected).read_text(encoding='utf-8'))
    merged = reconcile(official, selected)
    output = Path(options.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(merged, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
