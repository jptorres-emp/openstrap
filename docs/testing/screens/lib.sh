#!/bin/bash
# Shared helpers for the screen sweep. Sourced by sweep.sh. Needs adb with one emulator, the .dev app installed.
P=${P:-org.openstrap.app.dev}
ACT=$P/org.openstrap.app.MainActivity
OUT=${OUT:?OUT must name the output folder}
WAIT=${WAIT:-30}   # a cold start late in a session needs about 30 s before Today shows the day

scale() { adb shell settings get system font_scale | tr -d '\r'; }

# Fails when anything other than unstrap is in front, so no tap lands in another app. One relaunch is tried first;
# the screen being captured is then skipped, not the whole run.
infront() { adb shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity | grep -q "$P"; }
front() {
  infront && return 0
  adb shell am start -n $ACT >/dev/null 2>&1; sleep 8
  infront || { echo "NOT IN FRONT"; return 1; }
}

# open [am extras...]: a cold start with the extras, so no scroll or page is left from the last screen.
open() { adb shell am start -S -n $ACT "$@" >/dev/null 2>&1; sleep $WAIT; front; }

# warm [am extras...]: the extras to the running app (workout commands).
warm() { adb shell am start -n $ACT "$@" >/dev/null 2>&1; sleep ${T:-6}; front; }

tap() { front || return 1; adb shell input tap $1 $2; sleep ${T:-2.5}; }

# tapText "Backup": taps the centre of the first element whose text or description is exactly that.
tapText() {
  front || return 1; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b; b=$(adb shell cat /sdcard/ui.xml | python3 -I -c '
import sys,re
t=sys.argv[1]; x=sys.stdin.read()
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0)
    if re.search(r"(text|content-desc)=\"%s\"" % re.escape(t), n):
        a=list(map(int,re.findall(r"\d+", re.search(r"bounds=\"([^\"]+)\"", n).group(1))))
        print((a[0]+a[2])//2, (a[1]+a[3])//2); break
' "$1")
  [ -n "$b" ] || { echo "no element \"$1\""; return 1; }
  adb shell input tap $b; sleep ${T:-2.5}
}

top() { for k in 1 2 3; do front || return 1; adb shell input swipe 540 900 540 2000 200; sleep 0.8; done; sleep 1.5; }

# shots name [pages]: the screen, then a scroll per page; stops early when a scroll changes nothing.
shots() {
  local n=$1 pages=${2:-1} d="$OUT/$(scale)"; mkdir -p "$d"
  front || { echo "skipped $n"; return 1; }
  adb exec-out screencap -p > "$d/$n-1.png"
  for i in $(seq 2 $pages); do
    front || return 1; adb shell input swipe 540 1900 540 700 400; sleep 1.5
    adb exec-out screencap -p > "$d/$n-$i.png"
    if cmp -s "$d/$n-$i.png" "$d/$n-$((i-1)).png"; then rm "$d/$n-$i.png"; break; fi
  done
  echo "ok $(scale) $n"
}

back() { adb shell input keyevent KEYCODE_BACK; sleep ${T:-2}; }
