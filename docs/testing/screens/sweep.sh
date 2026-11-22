#!/bin/bash
# Captures every screen in screens.md at the font sizes given, into a dated folder.
#   docs/testing/screens/sweep.sh [out-folder] [groups...]
# Groups: today edit details tabs data strap sheets you ready live summary widgets route. Default: all of them.
# FONTS="1.0 1.15 1.3" sets the font sizes (default all three). Before the first run: setup.sh.
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
export OUT=${1:-$PWD/screens-$(date +%Y%m%d-%H%M)}; shift || true
WHICH=${*:-today details tabs data strap sheets you ready live summary widgets route}
. "$HERE/lib.sh"
SPORTS=${SPORTS:-"1 2 4 23 49 52 60"}   # run, treadmill, cycling, rowing, HIIT, strength, yoga (data/SportTypes.kt)

today()   { for m in Waking Midday WorkoutWindow After Evening Night; do open --es moment $m; shots today-$m 4; done
            edit; }
# Edit Today is the button at the foot of Today, so scroll until it shows.
edit()    { open; for k in 1 2 3 4 5 6; do tapText "Edit Today" >/dev/null && { shots edit-today 2; return; }
              front; adb shell input swipe 540 1900 540 500 300; sleep 1; done; echo "no Edit Today"; }
details() { for m in HeartRate RestingHr Hrv Spo2 Stress SkinTemperature Breathing Steps SleepScore Recovery Sleep Energy Load; do
              open --es open_metric $m; shots detail-$m-day 3
              # Score pages (Sleep score, Recovery, Sleep, Energy, Load) offer fewer ranges; a missing one is skipped.
              for r in Week Month Year; do top; tapText $r >/dev/null && shots detail-$m-$r 1; done; done; true; }
tabs()    { for t in Recovery Sleep Activity; do open; tapText $t && shots tab-$t 4; done; }
data()    { for s in pair pair_search pair_connecting pair_connected pair_failed firstnights nosleep partial firstscore; do
              open --es data_state $s; shots data-$s 2; done; open --es data_state auto; }
strap()   { open --ez open_strap true; shots strap-page 3
            for s in stale stuck disconnected charging notpaired recoveryfail; do open --es strap_state $s --ez open_strap true; shots strap-$s 1; done
            open --es strap_state auto; }
sheets()  { open; tap 540 240; shots sheet-calendar 1
            open; front; adb shell input swipe 540 1150 540 1150 1200; sleep 2; shots sheet-hold 1
            open --es open_metric HeartRate; tap 445 218; shots sheet-switcher 2
            open --es open_metric HeartRate; tap 686 218; shots sheet-info 2; }
you()     { open; tap 100 222; shots you-page 4
            for r in "Profile" "Goals and bests" "Sleep goal" "Heart rate zones" "Workout settings" "Health Connect" "Backup" "Diagnostics" "Updates"; do
              open; tap 100 222; for k in 1 2 3; do tapText "$r" && break; front; adb shell input swipe 540 1900 540 900 300; sleep 1; done
              shots "you-$(echo $r | tr 'A-Z ' 'a-z-')" 3; done
            open --ez open_recap true; shots weekly-recap 2; open --ez open_updates true; shots updates 2; }
ready()   { open --ez ready_pick true; shots choose-sport 3; for id in $SPORTS; do open --es ready_sport $id; shots ready-$id 2; done; }
# Each sport: Live, a second view, Paused, then the summary right after. A workout under a minute isn't kept, so 75 s.
live()    { for id in $SPORTS; do
              open --es simulate_hr 120x60,150x600 --ei simulate_steps 1; warm --es workout start:$id; T=10 front; shots live-$id 1
              adb shell input swipe 900 1200 200 1200 300; sleep 2; shots live-$id-view2 1; adb shell input swipe 200 1200 900 1200 300
              sleep 60; warm --es workout pause; shots live-$id-paused 1
              [ $id = 52 ] && { warm --es workout resume; tap 540 1835; sleep 4; shots live-52-set 1; tap 540 1835; sleep 4; shots live-52-log 1
                                tapText "Exercise" && shots live-52-exercise 1 && back; }
              T=10 warm --es workout end; shots summary-$id 4; done; }
summary() { open --ez show_summary true; shots summary-latest 4; }
widgets() { adb shell appwidget grantbind --package $P --user 0
            for w in recovery steps sleep start moment; do for d in sample recording; do
              adb shell am start -f 0x00020000 -n $P/org.openstrap.app.widget.debug.WidgetGalleryActivity --es widget $w --es data $d >/dev/null
              sleep 35; adb exec-out screencap -p > "$OUT/$(scale)/widget-$w-$d.png"; echo "ok widget-$w-$d"; done; done; back; }
# The route map needs a phone workout with a GPS track. Demo data has one, a run at 07:00 the day before: the Activity
# tab, a swipe back a day, scroll to the run, then its map and the second split.
route()   { open; tap 884 2215; front && adb shell input swipe 300 240 900 240 300; sleep 3
            for k in 1 2 3 4 5 6 7 8; do tapHas "km, 07:00" && break; front; adb shell input swipe 540 1700 540 1000 300; sleep 1.5; done
            shots route-workout 3; top; T=6 tap 540 1100; shots route-map 1; T=4 tapText "Km 2"; shots route-split 1; }

adb root >/dev/null 2>&1; sleep 2
for f in ${FONTS:-1.0 1.15 1.3}; do
  adb shell settings put system font_scale $f; mkdir -p "$OUT/$f"
  for g in $WHICH; do $g || echo "FAILED $g at $f"; done
done
adb shell settings put system font_scale 1.0
echo "done: $OUT"
