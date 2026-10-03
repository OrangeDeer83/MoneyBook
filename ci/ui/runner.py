# -*- coding: utf-8 -*-
"""用例登錄與執行。用法：python3 run_all.py <分流名稱>"""
import json
import os
import sys
import time
import traceback

import driver as d

HERE = os.path.dirname(os.path.abspath(__file__))
CASES = json.load(open(os.path.join(HERE, "cases.json"), encoding="utf-8"))
REG = []


def _match(key):
    hits = [c for c in CASES if key in c["n"]]
    exact = [c for c in hits if c["n"] == key]
    if exact:
        hits = exact
    if len(hits) != 1:
        raise KeyError(f"用例名稱「{key}」對到 {len(hits)} 個")
    return hits[0]


def case(shard, key, visual=False, time_at=None):
    """登錄一個自動測試。visual=True 代表流程與數值會自動檢查，但外觀仍需要人看截圖。
    time_at：需要把模擬器時間設成指定的 (月, 日, 時, 分)"""
    def deco(fn):
        REG.append(dict(shard=shard, key=key, visual=visual, fn=fn, time_at=time_at))
        return fn
    return deco


def _cleanup(real_now):
    try:
        d.small_screen(False)
        d.set_ime_with_hw_keyboard(False)
        d.sh("settings put system font_scale 1.0")
        d.sh("settings put system accelerometer_rotation 0")
        d.sh("settings put system user_rotation 0")
        d.sh("cmd uimode night no")
    except Exception:  # noqa: BLE001
        pass


def _selected(r, keys):
    """only 模式：環境變數 ONLY（逗號分隔）裡的任何一項，等於用例編號，或包含在用例名稱裡"""
    try:
        cid = _match(r["key"])["id"]
    except KeyError:
        cid = ""
    return any(k == cid or k in r["key"] for k in keys)


def run(shard):
    real_now = d.dev_now()
    results = []
    if shard == "only":
        keys = [k.strip() for k in os.environ.get("ONLY", "").split(",") if k.strip()]
        todo = [r for r in REG if _selected(r, keys)]
    else:
        todo = [r for r in REG if r["shard"] == shard]
    print(f"分流 {shard}：{len(todo)} 個用例", flush=True)
    for r in todo:
        try:
            c = _match(r["key"])
        except KeyError as e:
            print("略過：", e)
            continue
        rec = d.Rec(r["key"])
        rec.start(c["id"])
        d.CURRENT = rec
        print(f"== {c['id']} {c['n']}", flush=True)
        t0 = time.time()
        try:
            if r["time_at"]:
                mo, dy, hh, mm = r["time_at"]
                d.set_time(real_now.replace(month=mo, day=dy, hour=hh, minute=mm))
            for attempt in (1, 2):
                rec.checks.clear()
                rec.shots.clear()
                rec.error = ""
                rec._n = 0
                try:
                    r["fn"]()
                    break
                except Exception as e:  # noqa: BLE001
                    rec.error = f"{type(e).__name__}: {e}"
                    traceback.print_exc()
                    if attempt == 1 and "找不到首頁" in rec.error:
                        print("   找不到首頁，清掉系統對話框後重試一次", flush=True)
                        d.recover()
                        continue
                    try:
                        d.shot("失敗當下的畫面")
                    except Exception:  # noqa: BLE001
                        pass
                    break
        finally:
            if r["time_at"]:
                d.reset_time(real_now)
            _cleanup(real_now)
        if r["visual"] and not rec.checks and not rec.error and rec.shots:
            d.check("已擷取截圖供檢視（沒有可自動判斷的數值）", True)
        failed = bool(rec.error) or any(not ok for _, ok, _ in rec.checks)
        if not rec.checks and not rec.error:
            rec.error = "沒有任何檢查項目"
            failed = True
        status = "fail" if failed else ("review" if r["visual"] else "pass")
        results.append(dict(
            id=c["id"], key=r["key"], status=status, error=rec.error, seconds=round(time.time() - t0),
            checks=[dict(desc=a, ok=ok, detail=det) for a, ok, det in rec.checks],
            shots=[dict(file=f"{c['id']}/{f}", label=lb) for f, lb in rec.shots], notes=rec.notes,
        ))
        with open(os.path.join(d.OUT, f"results_{shard}.json"), "w", encoding="utf-8") as f:
            json.dump(results, f, ensure_ascii=False, indent=1)
        print(f"   → {status} ({results[-1]['seconds']}s)", flush=True)
    return results
