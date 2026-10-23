#!/usr/bin/env python3
"""Generate a reproducible compact offline snapshot from the official 5ch BBSMENU JSON.

Usage: python3 tools/generate_5ch_snapshot.py [--input local.json] [--output filename]
No original APK or decompiled assets are used.
"""
import argparse
import datetime as dt
import hashlib
import json
import pathlib
import re
import urllib.parse
import urllib.request

SOURCE = "https://menu.5ch.io/bbsmenu.json"
VALID_CODE = re.compile(r"[A-Za-z0-9_]{1,60}\Z")

def compact(value):
    return " ".join(str(value).replace("\t", " ").replace("\r", " ").replace("\n", " ").split())

def generate(raw):
    data = json.loads(raw)
    categories = data.get("menu_list")
    if not isinstance(categories, list):
        raise ValueError("Missing official menu_list")
    rows = []
    seen = set()
    category_names = set()
    for group in categories:
        if not isinstance(group, dict):
            continue
        category = compact(group.get("category_name") or "")
        boards = group.get("category_content")
        if not category or not isinstance(boards, list):
            continue
        for b in boards:
            if not isinstance(b, dict):
                continue
            code = compact(b.get("directory_name") or "")
            name = compact(b.get("board_name") or "")
            url = b.get("url")
            if not name or not VALID_CODE.fullmatch(code) or not isinstance(url, str):
                continue
            parsed = urllib.parse.urlparse(url)
            host = (parsed.hostname or "").lower()
            if (parsed.scheme not in ("https", "http") or
                not (host == "5ch.io" or host.endswith(".5ch.io") or host.endswith(".5ch.net")) or
                parsed.path not in ("/" + code, "/" + code + "/")):
                continue
            if code in seen:
                continue
            seen.add(code)
            category_names.add(category)
            rows.append((category, code, name))
    # A generated snapshot must be large enough to be usable and to expose
    # upstream format changes rather than silently shipping an empty file.
    if len(rows) < 100 or len(category_names) < 10:
        raise ValueError(f"Implausible official board menu: {len(rows)} boards / {len(category_names)} categories")
    rows.sort(key=lambda r: (r[0], r[1]))
    body = "\n".join("\t".join(row) for row in rows) + "\n"
    metadata = {
        "source": SOURCE,
        "source_sha256": hashlib.sha256(raw).hexdigest(),
        "format": "category<TAB>board_id<TAB>board_name",
        "board_count": len(rows),
        "category_count": len(category_names),
        "last_modify": data.get("last_modify"),
        "snapshot_sha256": hashlib.sha256(body.encode("utf-8")).hexdigest(),
    }
    return body, metadata

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", type=pathlib.Path)
    ap.add_argument("--output", type=pathlib.Path, default=pathlib.Path("offline_boards.tsv"))
    ap.add_argument("--metadata", type=pathlib.Path, default=pathlib.Path("offline_boards.meta.json"))
    args = ap.parse_args()
    if args.input:
        raw = args.input.read_bytes()
    else:
        req = urllib.request.Request(SOURCE, headers={"User-Agent":"HissiCheckerDroidPlus-BoardSnapshot/1.0"})
        with urllib.request.urlopen(req, timeout=25) as response:
            raw = response.read(3_000_001)
        if len(raw) > 3_000_000:
            raise ValueError("Official menu too large")
    body, metadata = generate(raw)
    metadata["generated_at_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.metadata.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(body, encoding="utf-8")
    args.metadata.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(metadata, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
