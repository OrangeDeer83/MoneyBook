# -*- coding: utf-8 -*-
"""CI 模擬器上操作 App 的工具：uiautomator 找元件、點擊、塞資料、截圖、記錄每個測試用例的檢查結果。"""
import datetime
import json
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.environ.get("OUT", os.path.join(HERE, "out"))
PKG = os.environ.get("PKG", "tw.moneybook.app.test")
DATA_FILE = "moneybook_v2.json"
os.makedirs(OUT, exist_ok=True)

CURRENT = None   # 目前正在跑的用例紀錄


# ───────────────────────── 用例紀錄 ─────────────────────────
class Rec:
    def __init__(self, key):
        self.key = key
        self.id = ""
        self.checks = []   # (說明, 是否通過, 細節)
        self.shots = []    # (檔名, 標題)
        self.notes = []
        self.error = ""
        self.dir = ""
        self._n = 0

    def start(self, case_id):
        self.id = case_id
        self.dir = os.path.join(OUT, case_id)
        os.makedirs(self.dir, exist_ok=True)


def check(desc, ok, detail=""):
    CURRENT.checks.append((desc, bool(ok), str(detail)))
    print(("  PASS " if ok else "  FAIL ") + desc + (f"  ({detail})" if detail else ""), flush=True)
    return bool(ok)


def note(text):
    CURRENT.notes.append(text)


# ───────────────────────── adb ─────────────────────────
def adb(*args, binary=False, timeout=90):
    try:
        p = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return b"" if binary else ""
    return p.stdout if binary else p.stdout.decode("utf-8", "replace")


def sh(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


class Node:
    def __init__(self, e):
        self.text = e.get("text") or ""
        self.desc = e.get("content-desc") or ""
        self.cls = e.get("class") or ""
        self.checkable = e.get("checkable") == "true"
        self.checked = e.get("checked") == "true"
        self.clickable = e.get("clickable") == "true"
        self.enabled = e.get("enabled") != "false"
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
        x1, y1, x2, y2 = (int(v) for v in m.groups()) if m else (0, 0, 0, 0)
        self.x1, self.y1, self.x2, self.y2 = x1, y1, x2, y2
        self.cx, self.cy = (x1 + x2) // 2, (y1 + y2) // 2

    def __repr__(self):
        return f"<{self.cls.split('.')[-1]} {self.text!r} {self.desc!r} @({self.cx},{self.cy})>"


def dump():
    for _ in range(6):
        sh("uiautomator dump /sdcard/ui.xml")
        raw = sh("cat /sdcard/ui.xml")
        i = raw.find("<?xml")
        if i >= 0:
            try:
                root = ET.fromstring(raw[i:].strip())
                return [Node(e) for e in root.iter("node")], raw[i:]
            except ET.ParseError:
                pass
        time.sleep(1)
    return [], ""


def nodes():
    return dump()[0]


def texts(ns=None):
    return [n.text for n in (ns if ns is not None else nodes()) if n.text]


def has(ns, t, exact=False):
    return any((n.text == t or n.desc == t) if exact else (t in n.text or t in n.desc) for n in ns)


def find(ns, t, exact=True):
    return [n for n in ns if ((n.text == t or n.desc == t) if exact else (t in n.text or t in n.desc))]


def first(ns, t, exact=True):
    r = find(ns, t, exact)
    return r[0] if r else None


def wait(pred, timeout=20, what="元件"):
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes():
            if pred(n):
                return n
        time.sleep(1)
    raise TimeoutError(f"找不到{what}")


def wait_text(t, exact=True, timeout=20):
    return wait(lambda n: ((n.text == t or n.desc == t) if exact else (t in n.text or t in n.desc)), timeout, f"「{t}」")


def gone(t, exact=False, timeout=8):
    end = time.time() + timeout
    while time.time() < end:
        if not has(nodes(), t, exact):
            return True
        time.sleep(1)
    return False


def tap_xy(x, y, settle=0.8):
    sh(f"input tap {x} {y}")
    time.sleep(settle)


def tap(n, settle=0.8):
    tap_xy(n.cx, n.cy, settle)


def tap_text(t, exact=True, timeout=20, lowest=False, nth=0):
    end = time.time() + timeout
    while time.time() < end:
        r = find(nodes(), t, exact)
        if r:
            r = sorted(r, key=lambda n: (n.cy, n.cx))
            tap(r[-1] if lowest else r[min(nth, len(r) - 1)])
            return
        time.sleep(1)
    raise TimeoutError(f"找不到「{t}」")


def tap_any(labels, exact=True, lowest=False, timeout=10):
    """點第一個存在的標籤"""
    end = time.time() + timeout
    while time.time() < end:
        ns = nodes()
        for t in labels:
            r = find(ns, t, exact)
            if r:
                r = sorted(r, key=lambda n: (n.cy, n.cx))
                tap(r[-1] if lowest else r[0])
                return t
        time.sleep(1)
    raise TimeoutError(f"找不到任何一個：{labels}")


def swipe(x1, y1, x2, y2, ms=350):
    sh(f"input swipe {x1} {y1} {x2} {y2} {ms}")
    time.sleep(0.9)


def scroll_down(times=1):
    for _ in range(times):
        swipe(540, 1700, 540, 600)


def scroll_up(times=1):
    for _ in range(times):
        swipe(540, 600, 540, 1700)


def back():
    sh("input keyevent 4")
    time.sleep(0.9)


def tap_back():
    """畫面左上角的返回箭頭（無障礙名稱「返回」）"""
    try:
        tap(wait(lambda n: n.desc == "返回", 8, "返回"))
    except TimeoutError:
        back()


def type_text(s):
    sh("input text '" + s.replace("'", "") + "'")
    time.sleep(0.5)


def edits():
    return sorted([n for n in nodes() if n.cls.endswith("EditText")], key=lambda n: (n.cy, n.cx))


def fill(n, s):
    tap(n)
    sh("input keyevent 123 " + " ".join(["67"] * 14))
    type_text(s)


def ime_shown():
    return "mInputShown=true" in sh("dumpsys input_method")


def hide_ime():
    if ime_shown():
        back()


def set_ime_with_hw_keyboard(on):
    sh(f"settings put secure show_ime_with_hard_keyboard {1 if on else 0}")


# ───────────────────────── 裝置狀態 ─────────────────────────
def dev_now():
    s = sh("date +%Y-%m-%d_%H:%M").strip()
    return datetime.datetime.strptime(s, "%Y-%m-%d_%H:%M")


def dev_today():
    return dev_now().date()


def set_time(dt):
    """把模擬器時間設成 dt（需要 adb root）"""
    adb("root")
    adb("wait-for-device")
    time.sleep(2)
    sh("settings put global auto_time 0")
    sh("settings put global auto_time_zone 0")
    sh(dt.strftime("date %m%d%H%M%Y.00"))
    time.sleep(1)


def reset_time(real):
    try:
        set_time(real)
    except Exception:  # noqa: BLE001
        pass


def stop_app():
    sh(f"am force-stop {PKG}")
    time.sleep(0.5)


def launch():
    sh(f"monkey -p {PKG} -c android.intent.category.LAUNCHER 1")
    time.sleep(3)


def wait_main(timeout=25):
    wait(lambda n: n.text in ("明細", "日曆") or n.desc == "記一筆", timeout, "首頁")


def fresh(seed_json=None):
    """清空 App 資料；有 seed 就先把資料檔寫進去，再啟動"""
    stop_app()
    sh(f"pm clear {PKG}")
    if seed_json is not None:
        path = os.path.join(OUT, "_seed.json")
        with open(path, "w", encoding="utf-8") as f:
            f.write(seed_json)
        adb("push", path, "/data/local/tmp/seed.json")
        sh(f"run-as {PKG} mkdir -p files")
        sh(f"run-as {PKG} cp /data/local/tmp/seed.json files/{DATA_FILE}")
    launch()
    wait_main()


def restart():
    stop_app()
    launch()
    wait_main()


def reinstall_same_apk(apk):
    adb("install", "-r", apk, timeout=180)


def small_screen(on):
    if on:
        sh("wm size 720x1280")
        sh("wm density 280")
    else:
        sh("wm size reset")
        sh("wm density reset")
    time.sleep(2)


# ───────────────────────── 截圖與對比檢查 ─────────────────────────
def _lum(c):
    def f(v):
        v = v / 255
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2])


def contrast_issues(png_path, ns, threshold=1.8):
    try:
        from PIL import Image
    except ImportError:
        return None
    im = Image.open(png_path).convert("RGB")
    W, H = im.size
    bad = []
    for n in ns:
        t = n.text.strip()
        if not t:
            continue
        x1, y1, x2, y2 = max(n.x1, 0), max(n.y1, 0), min(n.x2, W), min(n.y2, H)
        if x2 - x1 < 6 or y2 - y1 < 6 or (x2 - x1) * (y2 - y1) > 0.3 * W * H:
            continue
        crop = im.crop((x1, y1, x2, y2))
        crop.thumbnail((80, 40))
        counts = Counter(crop.getdata())
        bg = counts.most_common(1)[0][0]
        lb = _lum(bg)
        fg = max(counts, key=lambda c: abs(_lum(c) - lb))
        lf = _lum(fg)
        ratio = (max(lb, lf) + 0.05) / (min(lb, lf) + 0.05)
        if ratio < threshold:
            bad.append(f"{t[:14]}({ratio:.1f})")
    return bad


def shot(label, contrast=False):
    """截圖＋存畫面結構；contrast=True 時順便檢查文字對比"""
    CURRENT._n += 1
    base = f"{CURRENT._n:02d}"
    png = adb("exec-out", "screencap", "-p", binary=True)
    path = os.path.join(CURRENT.dir, base + ".png")
    with open(path, "wb") as f:
        f.write(png)
    ns, raw = dump()
    with open(os.path.join(CURRENT.dir, base + ".xml"), "w", encoding="utf-8") as f:
        f.write(raw)
    CURRENT.shots.append((base + ".png", label))
    if contrast:
        bad = contrast_issues(path, ns)
        if bad is None:
            note("沒有安裝 Pillow，略過對比檢查")
        else:
            check(f"文字對比足夠：{label}", not bad, "、".join(bad[:8]))
    return ns
