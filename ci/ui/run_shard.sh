#!/bin/bash
# 在模擬器上執行一個分流的 UI 測試。環境變數：OUT（輸出資料夾）、APK（要安裝的 apk）
SHARD="$1"
set -x
adb install -r "$APK"
adb shell settings put secure show_ime_with_hard_keyboard 0
adb shell settings put global stay_on_while_plugged_in 3
adb shell input keyevent 82
adb shell settings put global hide_error_dialogs 1
mkdir -p "$OUT"
cd "$(dirname "$0")"
python3 run_all.py "$SHARD"
exit 0
