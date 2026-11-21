#!/bin/bash
# 在模擬器上執行一個分流的 UI 測試。環境變數：OUT（輸出資料夾）、APK（要安裝的 apk）
SHARD="$1"
set -x
# 模擬器快取裡可能留著簽章不同的舊版（INSTALL_FAILED_UPDATE_INCOMPATIBLE）：先移除再裝；還是裝不上就停，不要拿舊版跑測試
if ! adb install -r "$APK"; then
  adb uninstall tw.moneybook.app.test || true
  adb install "$APK" || { echo "裝不上新版 APK"; exit 1; }
fi
adb shell settings put secure show_ime_with_hard_keyboard 0
adb shell settings put global stay_on_while_plugged_in 3
adb shell input keyevent 82
adb shell settings put global hide_error_dialogs 1
mkdir -p "$OUT"
cd "$(dirname "$0")"
python3 run_all.py "$SHARD"
exit 0
