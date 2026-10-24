#!/usr/bin/env python3
"""Create a history-free public-source preview from explicitly allowed files."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parent.parent
PUBLIC_FILES = {
    ".gitignore", "README.md", "CHANGELOG.md", "LICENSE", "PRIVACY.md",
    "SECURITY.md", "THIRD_PARTY_NOTICES.md", "build.gradle",
    "settings.gradle", "gradle.properties", "app/build.gradle", "app/lint.xml",
    ".github/dependabot.yml", ".github/workflows/public-ci.yml",
    "docs/BOARD_MENU_SNAPSHOT.md", "docs/LICENSING_SCOPE.md",
    "docs/RELEASE_NOTES_v1.0.0.md", "tools/generate_5ch_snapshot.py",
}
PUBLIC_DIRS = ("app/src/main/", "app/src/test/")
PRIVATE_FILES = {
    "docs/PUBLIC_RELEASE_CHECKLIST.md", "docs/PUBLIC_EXPORT_PLAN.md",
    "docs/RELEASE_CANDIDATE_ACCEPTANCE.md", "docs/REAL_DEVICE_ACCEPTANCE.md",
    "docs/GITHUB_PUBLIC_SECURITY.md", "docs/SIGNING.md", "docs/UI_POLICY.md",
    ".github/workflows/build-debug-apk.yml",
    ".github/workflows/release-candidate.yml",
    ".github/workflows/public-security-audit.yml",
    ".github/workflows/generate-5ch-snapshot.yml",
    "tools/public_security_audit.py", "tools/emulator_smoke.sh",
    "tools/adb_real_device_check.ps1",
}
DOCUMENTS = {
    "README.md", "CHANGELOG.md", "LICENSE", "PRIVACY.md", "SECURITY.md",
    "THIRD_PARTY_NOTICES.md", "docs/BOARD_MENU_SNAPSHOT.md",
    "docs/LICENSING_SCOPE.md", "docs/RELEASE_NOTES_v1.0.0.md",
}

def reject(msg):
    raise SystemExit("Public export rejected: " + msg)

def validate_documents(data):
    names = set(data)
    if not DOCUMENTS.issubset(names):
        reject("missing public docs: " + str(sorted(DOCUMENTS - names)))
    for path in sorted(DOCUMENTS):
        content = data[path].decode("utf-8")
        if chr(0xff0f) in content:
            reject("fullwidth slash: " + path)
        if any(x in content for x in ("release-candidate/", "Privateブランチ",
                                      "Privateリポジトリ", "公開前チェックリスト")):
            reject("private QA wording: " + path)
        for link in re.findall(r"\]\(([^)]+)\)", content):
            if link.startswith(("https://", "http://", "mailto:", "#")):
                continue
            parts = []
            for part in (Path(path).parent / link.split("#")[0]).as_posix().split("/"):
                if part == "..":
                    if parts:
                        parts.pop()
                elif part not in ("", "."):
                    parts.append(part)
            if "/".join(parts) not in names:
                reject("broken doc link: " + path + " -> " + link)

def main():
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    tracked = sorted(b.decode("utf-8") for b in raw.split(b"\0") if b)
    selected = [p for p in tracked if p in PUBLIC_FILES or p.startswith(PUBLIC_DIRS)]
    if set(selected) & PRIVATE_FILES:
        reject("private file included")
    if any("original_boards.tsv" in p for p in selected):
        reject("legacy board asset included")
    content = {p: (ROOT / p).read_bytes() for p in selected}
    validate_documents(content)
    output = ROOT / "export"
    output.mkdir(exist_ok=True)
    archive = output / "v1.0.0-public-source-preview.zip"
    with ZipFile(archive, "w", compression=ZIP_DEFLATED, compresslevel=9) as z:
        for path in selected:
            info = ZipInfo(path, date_time=(2026, 1, 1, 0, 0, 0))
            info.compress_type = ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, content[path])
    with ZipFile(archive) as z:
        if z.namelist() != selected:
            reject("archive contains unexpected entries")
    manifest = {
        "archive": archive.name,
        "sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
        "files": [{"path": p, "sha256": hashlib.sha256(content[p]).hexdigest()}
                  for p in selected],
        "excluded_tracked_count": len(tracked) - len(selected),
        "excluded_private_files": sorted(PRIVATE_FILES),
    }
    (output / "public-source-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Public source: {len(selected)} included, "
          f"{manifest['excluded_tracked_count']} excluded; sha256 {manifest['sha256']}")

if __name__ == "__main__":
    main()
