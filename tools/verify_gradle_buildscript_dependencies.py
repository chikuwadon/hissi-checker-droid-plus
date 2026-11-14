#!/usr/bin/env python3
"""Fail CI if Gradle's *selected* AGP buildscript dependencies are vulnerable.

Input is Gradle's standard `buildEnvironment` report, where each dependency
line prints `requested -> selected` if the versions differ. Checks both the
direct patched modules and every selected occurrence of their module names.
This verifies the actual loaded classpath; it does not suppress Dependabot.
"""
import re
import sys
from pathlib import Path

EXPECTED = {
    "org.bouncycastle:bcprov-jdk18on": "1.86",
    "org.bouncycastle:bcpkix-jdk18on": "1.86",
    "org.bouncycastle:bcutil-jdk18on": "1.86",
    "com.google.guava:guava": "33.7.2-jre",
    "org.apache.commons:commons-lang3": "3.18.0",
    "org.apache.httpcomponents:httpclient": "4.5.14",
    "org.jetbrains.kotlin:kotlin-gradle-plugin": "2.4.21",
    "org.jdom:jdom2": "2.0.6.1",
    "org.bitbucket.b_c:jose4j": "0.9.6",
}

def verify(report: str) -> list[str]:
    issues = []
    for module, expected in EXPECTED.items():
        regex = re.compile(
            re.escape(module) +
            r":(?P<requested>[^\s()]+)(?:\s*->\s*(?P<selected>[^\s()]+))?"
        )
        matches = [regex.search(line) for line in report.splitlines()]
        matches = [m for m in matches if m is not None]
        if not matches:
            issues.append(f"{module}: not present in buildscript dependency report")
            continue
        for match in matches:
            selected = match.group("selected") or match.group("requested")
            if selected != expected:
                issues.append(f"{module}: selected {selected}, expected {expected}")
    return issues

if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: verify_gradle_buildscript_dependencies.py <buildEnvironment-output>")
    report = Path(sys.argv[1]).read_text(encoding="utf-8")
    issues = verify(report)
    if issues:
        print("AGP security version verification failed:", file=sys.stderr)
        print("\n".join(issues), file=sys.stderr)
        sys.exit(1)
    print(f"Validated patched versions for {len(EXPECTED)} AGP buildscript modules.")
