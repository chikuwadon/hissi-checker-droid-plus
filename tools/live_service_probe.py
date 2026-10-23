#!/usr/bin/env python3
"""Non-invasive public endpoint diagnostics. No IDs, sessions, cookies or search requests."""
import json, ssl, urllib.error, urllib.request
from datetime import datetime, timezone
from pathlib import Path
from time import monotonic

targets = {
    "official_board_menu": "https://menu.5ch.io/bbsmenu.json",
    "hissi_https_root": "https://hissi.org/",
    "hissi_http_root": "http://hissi.org/",
    "edge_https_root": "https://www.kyodemo.net/",
}
report = {"timestamp_utc": datetime.now(timezone.utc).isoformat(),
          "purpose": "endpoint-only check; NOT a live ID-search or real-device test",
          "results": {}}
for name, url in targets.items():
    started = monotonic()
    result = {"protocol": url.split(":", 1)[0], "url": url}
    try:
        request = urllib.request.Request(url, headers={
            "User-Agent": "HissiCheckerDroidPlus-QA/1.0",
            "Accept": "application/json,text/html;q=0.9,*/*;q=0.1"})
        # Limit responses; this is an availability-only probe.
        with urllib.request.urlopen(request, timeout=12,
                                    context=ssl.create_default_context()) as res:
            data = res.read(3_000_001)
            result["status"] = res.status
            result["redirect_url_protocol"] = res.url.split(":", 1)[0]
            result["bytes_sampled"] = len(data)
            if name == "official_board_menu":
                try:
                    parsed = json.loads(data)
                    groups = parsed.get("menu_list")
                    result["valid_json_menu"] = isinstance(groups, list) and len(groups) >= 10
                except Exception:
                    result["valid_json_menu"] = False
    except urllib.error.HTTPError as ex:
        result["status"] = ex.code
        result["result"] = "HTTP response received"
    except Exception as ex:
        result["result"] = "transport_error"
        result["error_type"] = type(ex).__name__
    result["elapsed_ms"] = int(1000 * (monotonic() - started))
    report["results"][name] = result
Path("connectivity-report.json").write_text(
    json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(report, ensure_ascii=False))
