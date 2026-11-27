#!/usr/bin/env bash
# Retry only HTTP 429 throttling; never suppress a failed Gradle build.
set -uo pipefail
log=$(mktemp) || exit 1
trap 'rm -f "$log"' EXIT
for delay in 15 45 90 0; do
    if gradle "$@" 2>&1 | tee "$log"; then
        exit 0
    else
        status=$?
    fi
    if [[ "$delay" == 0 ]] || ! grep -Eqi 'Received status code 429|HTTP (status )?429|429 Too Many Requests' "$log"; then
        exit "$status"
    fi
    echo "Gradle dependency download was rate-limited; retrying in ${delay}s." >&2
    sleep "$delay" || exit 1
done
