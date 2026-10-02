# -*- coding: utf-8 -*-
"""跨用例共用的操作步驟"""
import driver as d
import seed as S


def today():
    return d.dev_today()


def base_seed(**kw):
    return S.base(today(), **kw).json()


def open_add():
    d.tap(d.wait(lambda n: n.desc == "記一筆", 15, "記一筆按鈕"))
    d.wait_text("備註（選填）", timeout=15)


def keypad(digits):
    for ch in digits:
        ns = d.nodes()
        c = [n for n in ns if n.text == ch]
        if not c:
            raise TimeoutError(f"鍵盤上找不到 {ch}")
        d.tap(max(c, key=lambda n: n.cy), settle=0.4)


def save_edit():
    """記一筆畫面右下角的「完成」（有運算時是「=」，要按兩次）"""
    ns = d.nodes()
    c = [n for n in ns if n.text == "完成"]
    d.tap(max(c, key=lambda n: n.cy))
    d.wait(lambda n: n.text in ("明細", "日曆", "統計", "我的") and n.cy > 2000, 15, "回到主畫面")


def tab(name):
    ns = d.nodes()
    c = [n for n in ns if n.text == name and n.cy > 2000]
    if not c:
        raise TimeoutError(f"找不到底部分頁 {name}")
    d.tap(c[0])


def me_page(label):
    tab("我的")
    d.tap(d.wait(lambda n: n.text == label, 10, label))
    d.time.sleep(1)


def chip_scroll(label, anchor, tries=6):
    """可橫向捲動的一排按鈕：找不到就往左捲"""
    for _ in range(tries):
        ns = d.nodes()
        r = d.find(ns, label, exact=True) or [n for n in ns if n.text.startswith(label)]
        if r:
            d.tap(r[0])
            return r[0]
        row = next((n for n in ns if anchor in n.text), None)
        y = row.cy if row else 1440
        d.swipe(950, y, 120, y)
    raise TimeoutError(f"找不到按鈕「{label}」")


def confirm_dialog():
    return d.tap_any(["好", "完成", "儲存", "確定", "新增", "OK"], exact=True)


def reimb_row_texts():
    return [n.text for n in d.nodes() if n.text]


def open_reimb_home():
    me_page("報銷")
    d.wait_text("還沒收到的報銷款", timeout=15)


def swipe_row_left(node):
    y = node.cy
    d.swipe(1000, y, 250, y, 400)
