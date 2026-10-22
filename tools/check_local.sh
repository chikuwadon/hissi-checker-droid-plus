#!/usr/bin/env bash
set -euo pipefail

test -f app/src/main/AndroidManifest.xml
test -f app/src/main/java/com/chikuwadon/hissidroidplus/HomeActivity.java
test -f app/src/main/java/com/chikuwadon/hissidroidplus/CheckerQuery.java
test -f app/src/main/java/com/chikuwadon/hissidroidplus/EdgeAdapter.java
test -f app/src/test/java/com/chikuwadon/hissidroidplus/EdgeMhtStructureTest.java
grep -q "versionName = '0.1.4'" app/build.gradle
if test -e app/src/main/java/com/chikuwadon/hissidroidplus/CheckerActivity.java; then
  echo "Refuse old decompiled mod source" >&2
  exit 1
fi
printf 'Independent Android project structure and version verified.\n'
