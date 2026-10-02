# -*- coding: utf-8 -*-
"""流程：記一筆時先填報銷對象與金額，後輸入帳目金額，儲存後報銷資料要還在。"""
import sys
import traceback

import driver as d


def keypad(digits):
    for ch in digits:
        cands = [n for n in d.nodes() if n.text == ch]
        d.tap(max(cands, key=lambda n: n.cy))   # 數字鍵盤在畫面最下方


def main():
    try:
        d.launch()
        ns = d.shot("home")
        d.check("App 啟動並看到首頁", d.has_text(ns, "明細") or d.has_text(ns, "記一筆"))

        d.tap_desc("記一筆")
        ns = d.shot("edit_empty")
        d.check("進入記一筆", d.has_text(ns, "備註（選填）"))

        d.tap_text("報銷")
        d.wait(d.by_text("這筆的報銷"))
        ns = d.shot("reimb_page_open")
        sw = [n for n in ns if n.checkable]
        d.check("報銷整頁有開關", bool(sw))
        if sw and not sw[0].checked:
            d.tap(sw[0])
        d.tap_text("分給多人")
        ns = d.shot("reimb_multi")

        edits = sorted([n for n in d.nodes() if n.cls.endswith("EditText")], key=lambda n: (n.cy, n.cx))
        d.check("找到對象與金額輸入框", len(edits) >= 2, f"{len(edits)} 個")
        d.fill(edits[0], "Ming")
        edits = sorted([n for n in d.nodes() if n.cls.endswith("EditText")], key=lambda n: (n.cy, n.cx))
        d.fill(edits[1], "500")
        d.hide_ime()
        d.tap_text("新增對象", exact=False)
        edits = sorted([n for n in d.nodes() if n.cls.endswith("EditText")], key=lambda n: (n.cy, n.cx))
        d.check("新增第二位對象", len(edits) >= 4, f"{len(edits)} 個輸入框")
        d.fill(edits[2], "Hua")
        edits = sorted([n for n in d.nodes() if n.cls.endswith("EditText")], key=lambda n: (n.cy, n.cx))
        d.fill(edits[3], "700")
        d.hide_ime()
        d.shot("reimb_filled")

        d.tap_text("完成")
        d.wait(d.by_text("備註（選填）"))
        ns = d.shot("back_in_edit_no_amount")
        d.check("回到記一筆後報銷鈕仍顯示已設定", d.has_text(ns, "待報銷"), "這是被修好的 bug 的第一個關鍵點")

        keypad("1500")
        ns = d.shot("amount_entered")
        d.check("輸入金額後報銷鈕仍有 2 人", d.has_text(ns, "2 人"))

        cands = [n for n in d.nodes() if n.text == "完成"]
        d.tap(max(cands, key=lambda n: n.cy))
        d.wait(lambda n: n.text in ("明細", "日曆", "統計", "我的"), timeout=15)
        d.shot("saved_home")

        d.tap_text("我的")
        d.shot("me")
        rows = [n for n in d.nodes() if n.text.startswith("報銷")]
        d.check("我的頁面有報銷選項", bool(rows))
        d.tap(rows[0])
        d.wait(d.by_text("依對象", exact=False), timeout=15)
        d.tap_text("依對象")
        ns = d.shot("reimb_overview")
        d.check("報銷總覽有 Ming", d.has_text(ns, "Ming"))
        d.check("報銷總覽有 Hua", d.has_text(ns, "Hua"))
    except Exception as e:  # noqa: BLE001
        traceback.print_exc()
        d.check("流程執行中沒有中斷", False, f"{type(e).__name__}: {e}")
        try:
            d.shot("failure")
        except Exception:  # noqa: BLE001
            pass
    ok = d.write_report("先填報銷、後填金額")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
