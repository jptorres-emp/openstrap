#!/bin/bash
# Shared helpers for the screen sweep. Sourced by sweep.sh. Needs adb with one emulator, the .dev app installed.
P=${P:-org.openstrap.app.dev}
ACT=$P/org.openstrap.app.MainActivity
OUT=${OUT:?OUT must name the output folder}
WAIT=${WAIT:-30}   # the longest a cold start may take; the sweep moves on as soon as the screen has settled
STILL=${STILL:-2.5} # seconds a cold-started screen must stay unchanged to count as settled

now() { perl -MTime::HiRes=time -e 'printf "%.2f", time'; }

# settle [max] [still]: waits until the screen has not changed for `still` seconds, or `max` seconds have gone. The
# frame is hashed on the emulator, so each look costs about 0.15 s. A screen with a ticking timer (Live) never settles
# and waits the whole `max`.
settle() {
  local max=${1:-$WAIT} still=${2:-$STILL} t0 t h last="" since
  t0=$(now); since=$t0
  while :; do
    h=$(perl -e 'alarm 10; exec @ARGV' adb shell 'screencap | md5sum' 2>/dev/null | cut -c1-16)
    t=$(now)
    [ -n "$h" ] && [ "$h" != "$last" ] && { last=$h; since=$t; }
    awk "BEGIN{exit !($t-$since >= $still || $t-$t0 >= $max)}" && return 0
    sleep 0.2
  done
}

scale() { adb shell settings get system font_scale | tr -d '\r'; }

# Fails when anything other than unstrap is in front, so no tap lands in another app. One relaunch is tried first;
# the screen being captured is then skipped, not the whole run.
infront() { adb shell dumpsys activity activities 2>/dev/null | grep -m1 topResumedActivity | grep -q "$P"; }
front() {
  infront && return 0
  adb shell am start -n $ACT >/dev/null 2>&1; settle 8
  infront || { echo "NOT IN FRONT"; return 1; }
}

# open [am extras...]: a cold start with the extras, so no scroll or page is left from the last screen.
open() { adb shell am start -S -n $ACT "$@" >/dev/null 2>&1; sleep 1; settle; front; }

# warm [am extras...]: the extras to the running app (workout commands).
warm() { adb shell am start -n $ACT "$@" >/dev/null 2>&1; settle ${T:-6} 1.5; front; }

tap() { front || return 1; adb shell input tap $1 $2; settle ${T:-2.5} 1; }

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
  adb shell input tap $b; settle ${T:-2.5} 1
}

# tapHas "07:00": taps the first element whose text holds that, above the tab bar; fails when none is on screen.
tapHas() {
  front || return 1; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  local b; b=$(adb shell cat /sdcard/ui.xml | python3 -I -c '
import sys,re
t=sys.argv[1]; x=sys.stdin.read()
for m in re.finditer(r"<node [^>]*>", x):
    n=m.group(0)
    if re.search(r"(text|content-desc)=\"[^\"]*%s" % re.escape(t), n):
        a=list(map(int,re.findall(r"\d+", re.search(r"bounds=\"([^\"]+)\"", n).group(1))))
        if a[3] < 2100: print((a[0]+a[2])//2, (a[1]+a[3])//2); break
' "$1")
  [ -n "$b" ] || return 1
  adb shell input tap $b; settle ${T:-2.5} 1
}

# cap file: a screenshot that gives up after 20 s and tries once more, since the emulator's capture can wedge and
# would otherwise hang the sweep. A failed capture leaves no file and says so.
cap() {
  local k; for k in 1 2; do
    perl -e 'alarm 20; exec @ARGV' adb exec-out screencap -p > "$1.tmp" 2>/dev/null && [ -s "$1.tmp" ] && { mv "$1.tmp" "$1"; return 0; }
    sleep 2; done
  rm -f "$1.tmp"; echo "CAPTURE FAILED $(basename "$1")"; return 1
}

top() { for k in 1 2 3; do front || return 1; adb shell input swipe 540 900 540 2000 200; done; settle 3 0.8; }

# shots name [pages]: the screen, then a scroll per page; stops early when a scroll changes nothing.
shots() {
  local n=$1 pages=${2:-1} d="$OUT/$(scale)"; mkdir -p "$d"
  front || { echo "skipped $n"; return 1; }
  cap "$d/$n-1.png"
  for i in $(seq 2 $pages); do
    front || return 1; adb shell input swipe 540 1900 540 700 400; settle 3 0.8
    cap "$d/$n-$i.png"
    if cmp -s "$d/$n-$i.png" "$d/$n-$((i-1)).png"; then rm "$d/$n-$i.png"; break; fi
  done
  echo "ok $(scale) $n"
}

back() { adb shell input keyevent KEYCODE_BACK; settle ${T:-2} 1; }
