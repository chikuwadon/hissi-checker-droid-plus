#!/usr/bin/env bash
# Strict on-emulator smoke test. Does not claim real-device or live server validation.
set -Eeuo pipefail
package=com.chikuwadon.hissidroidplus
mkdir -p emulator-evidence
old=baseline/app-release.apk
new=candidate/app-release.apk
test -s "$old"
test -s "$new"
adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb shell pm clear "$package" >/dev/null 2>&1 || true
adb install -r "$old"
adb shell am force-stop "$package" || true
adb shell am start -W -n "$package/.HomeActivity" > emulator-evidence/baseline-start.txt
adb shell dumpsys package "$package" > emulator-evidence/baseline-package.txt
grep -q 'versionCode=22' emulator-evidence/baseline-package.txt
sleep 2
adb shell dumpsys meminfo "$package" > emulator-evidence/baseline-memory.txt

dump_ui() {
  for attempt in 1 2 3; do
    if adb shell uiautomator dump /sdcard/hissi-window.xml > /dev/null 2>&1 &&
      adb exec-out cat /sdcard/hissi-window.xml > emulator-evidence/current-ui.xml &&
      grep -q '<hierarchy' emulator-evidence/current-ui.xml; then
      return 0
    fi
    sleep 2
  done
  echo "Unable to capture accessibility UI" >&2
  return 1
}
tap_id() {
  local id="$1" coords
  dump_ui
  coords="$(python3 - "$id" <<'PY'
import re,sys,xml.etree.ElementTree as ET
target='com.chikuwadon.hissidroidplus:id/'+sys.argv[1]
root=ET.parse('emulator-evidence/current-ui.xml').getroot()
for el in root.iter('node'):
    if el.attrib.get('resource-id')==target:
        v=list(map(int,re.findall(r'\d+',el.attrib['bounds'])))
        print((v[0]+v[2])//2,(v[1]+v[3])//2)
        break
else:
    sys.exit('Missing Android UI id: '+target)
PY
)"
  adb shell input tap $coords
}
tap_text() {
  local target="$1" coords
  dump_ui
  coords="$(python3 - "$target" <<'PY'
import re,sys,xml.etree.ElementTree as ET
target=sys.argv[1]
root=ET.parse('emulator-evidence/current-ui.xml').getroot()
for el in root.iter('node'):
    if el.attrib.get('text')==target:
        v=list(map(int,re.findall(r'\d+',el.attrib['bounds'])))
        print((v[0]+v[2])//2,(v[1]+v[3])//2)
        break
else:
    sys.exit('Missing Android UI text: '+target)
PY
)"
  adb shell input tap $coords
}
# Create persisted board favorite in the old release, then upgrade without clearing data.
tap_id board
adb shell input text news4vip
tap_id add_favorite
sleep 1
adb shell am force-stop "$package"
adb install -r "$new"
adb shell dumpsys package "$package" > emulator-evidence/release-package.txt
grep -q 'versionCode=23' emulator-evidence/release-package.txt
adb shell am start -W -n "$package/.HomeActivity" > emulator-evidence/release-start.txt
sleep 2
tap_id show_favorites
sleep 1
dump_ui
grep -q 'news4vip' emulator-evidence/current-ui.xml
adb shell input keyevent KEYCODE_BACK
sleep 1
tap_id home_more
sleep 1
tap_text '検索履歴'
sleep 1
dump_ui
grep -q '検索履歴' emulator-evidence/current-ui.xml
adb shell input keyevent KEYCODE_BACK
adb shell dumpsys meminfo "$package" > emulator-evidence/release-memory.txt
python3 - <<'PY'
import json,pathlib,re
root=pathlib.Path('emulator-evidence')
metric={'description':'emulator-only samples; not a real-device performance benchmark'}
for label in ('baseline','release'):
    launch=(root/f'{label}-start.txt').read_text(errors='replace')
    mem=(root/f'{label}-memory.txt').read_text(errors='replace')
    m=re.search(r'TotalTime:\s*(\d+)',launch)
    metric[label+'_startup_ms']=int(m.group(1)) if m else None
    p=re.search(r'TOTAL\s+([\d,]+)',mem)
    metric[label+'_total_pss_kb']=int(p.group(1).replace(',','')) if p else None
(root/'performance-sample.json').write_text(json.dumps(metric,indent=2)+'\n')
PY
adb shell getprop ro.build.version.sdk > emulator-evidence/api.txt
printf "PASS api=%s upgrade=22-to-23 favorite=persisted home=ok history=ok\n" \
  "$(cat emulator-evidence/api.txt)" | tee emulator-evidence/summary.txt
