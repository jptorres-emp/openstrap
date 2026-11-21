#!/bin/bash
# Sets the emulator up the way every capture expects: the sample week's clock (Fri 13 Nov 2026 18:05), the dev app
# with demo data. Build and install first: ./gradlew :app:assembleDebug -PdevApp && adb install -r app/build/outputs/apk/debug/app-debug.apk
P=${P:-org.openstrap.app.dev}
adb root >/dev/null; sleep 2
adb shell settings put global auto_time 0
adb shell date 111318052026.00
adb shell am start -S -n $P/org.openstrap.app.MainActivity --ez demo_data true >/dev/null
sleep 40
adb shell dumpsys package $P | grep -m1 versionName
