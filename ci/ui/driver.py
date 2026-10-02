# -*- coding: utf-8 -*-
"""在 CI 的模擬器上操作 App 的小工具：用 uiautomator 的畫面結構找元件、截圖、記錄檢查結果。"""
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT = os.environ.get("OUT", "out")
PKG = os.environ.get("PKG", "tw.moneybook.app.test")
os.makedirs(OUT, exist_ok=True)

results = []   # (名稱, 是否通過, 說明)
_shot_no = [0]


def adb(*args, check=False, binary=False):
    p = subprocess.run(["adb", *args], capture_output=True, check=check)
    return p.stdout if binary else p.stdout.decode("utf-8", "replace")


class Node:
    def __init__(self, e):
        self.text = e.get("text") or ""
        self.desc = e.get("content-desc") or ""
        self.cls = e.get("class") or ""
        self.checkable = e.get("checkable") == "true"
        self.checked = e.get("checked") == "true"
        self.clickable = e.get("clickable") == "true"
        m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
        x1, y1, x2, y2 = (int(v) for v in m.groups()) if m else (0, 0, 0, 0)
        self.x1, self.y1, self.x2, self.y2 = x1, y1, x2, y2
        self.cx, self.cy = (x1 + x2) // 2, (y1 + y2) // 2

    def __repr__(self):
        return f"<{self.cls.split('.')[-1]} text={self.text!r} desc={self.desc!r} @({self.cx},{self.cy})>"


def dump():
    """目前畫面所有元件；失敗時重試幾次"""
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        raw = adb("shell", "cat", "/sdcard/ui.xml")
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


def wait(pred, timeout=20, what="元件"):
    end = time.time() + timeout
    while time.time() < end:
        for n in nodes():
            if pred(n):
                return n
        time.sleep(1)
    raise TimeoutError(f"找不到{what}")


def by_text(t, exact=True):
    return lambda n: (n.text == t) if exact else (t in n.text)


def by_desc(t):
    return lambda n: n.desc == t


def tap(n):
    adb("shell", "input", "tap", str(n.cx), str(n.cy))
    time.sleep(0.8)


def tap_text(t, exact=True, timeout=20):
    tap(wait(by_text(t, exact), timeout, f"文字「{t}」"))


def tap_desc(t, timeout=20):
    tap(wait(by_desc(t), timeout, f"圖示「{t}」"))


def ime_shown():
    return "mInputShown=true" in adb("shell", "dumpsys", "input_method")


def hide_ime():
    if ime_shown():
        adb("shell", "input", "keyevent", "4")
        time.sleep(0.8)


def fill(n, s):
    """點進輸入框，清掉原本內容再輸入"""
    tap(n)
    adb("shell", "input", "keyevent", "123", *(["67"] * 12))
    adb("shell", "input", "text", s)
    time.sleep(0.5)


def shot(name):
    _shot_no[0] += 1
    base = f"{_shot_no[0]:02d}_{name}"
    png = adb("exec-out", "screencap", "-p", binary=True)
    with open(os.path.join(OUT, base + ".png"), "wb") as f:
        f.write(png)
    ns, raw = dump()
    with open(os.path.join(OUT, base + ".xml"), "w", encoding="utf-8") as f:
        f.write(raw)
    return ns


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(("PASS " if ok else "FAIL ") + name + (f"  ({detail})" if detail else ""), flush=True)


def has_text(ns, t):
    return any(t in n.text or t in n.desc for n in ns)


def launch():
    adb("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(4)


def write_report(title):
    lines = [f"# {title}", ""]
    for name, ok, detail in results:
        lines.append(f"- {'✅' if ok else '❌'} {name}" + (f" — {detail}" if detail else ""))
    lines.append("")
    lines.append("截圖依序編號，同名 .xml 是當時的畫面結構。")
    with open(os.path.join(OUT, "report.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    return all(ok for _, ok, _ in results)
