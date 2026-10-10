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


def edit_open(timeout=15):
    """等「編輯既有記錄／常用記帳」的畫面出現：只有編輯才有右上角的刪除鈕（沒有「正在編輯」提示條了）"""
    d.wait(lambda n: n.desc == "刪除", timeout, "編輯畫面（刪除鈕）")


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
    d.wait(lambda n: n.text in ("明細", "帳戶", "統計", "我的") and n.cy > 2000, 15, "回到主畫面")


def save_edit_to_account():
    """從帳戶明細進入的記一筆，按完成後會回到帳戶明細（不是主畫面）"""
    ns = d.nodes()
    c = [n for n in ns if n.text == "完成"]
    d.tap(max(c, key=lambda n: n.cy))
    d.wait_text("更新餘額", timeout=15)
    d.time.sleep(1)


def _tap_tab(name):
    ns = d.nodes()
    h = max([n.y2 for n in ns] + [1])          # 螢幕高度（縮小螢幕的用例也能用）
    c = [n for n in ns if n.text == name and n.cy > 0.83 * h]
    if not c:
        raise TimeoutError(f"找不到底部分頁 {name}")
    d.tap(c[0])


def _view(mode):
    """明細分頁標題列的切換鈕：目前不是想要的顯示方式就點一下"""
    d.time.sleep(0.8)
    ns = d.nodes()
    want = "切換成日曆" if mode == "calendar" else "切換成明細列表"
    sw = [n for n in ns if n.desc == want]
    if sw:
        d.tap(sw[0])
        d.time.sleep(1)


def tab(name):
    """底部分頁。日曆已併進「明細」分頁：tab("日曆") = 明細分頁 + 切成日曆，tab("明細") = 明細分頁 + 切成列表"""
    if name == "日曆":
        _tap_tab("明細")
        _view("calendar")
    elif name == "明細":
        _tap_tab("明細")
        _view("list")
    else:
        _tap_tab(name)


def is_disabled(ns, node):
    """停用的按鈕，在畫面結構裡是另一個 enabled=false 的方塊蓋在文字上（文字節點本身仍是 enabled）"""
    return any((not n.enabled) and n.x1 <= node.cx <= n.x2 and n.y1 <= node.cy <= n.y2 for n in ns)


def open_acc(name):
    """進帳戶分頁並點開某個帳戶的明細（分組標題可能跟帳戶同名，例如「現金」，帳戶卡片在標題下面）"""
    tab("帳戶")
    d.wait_text("總資產", timeout=15)
    d.time.sleep(0.8)
    c = sorted([n for n in d.nodes() if n.text == name and n.cy < 2000], key=lambda n: n.cy)
    if not c:
        raise TimeoutError(f"帳戶分頁找不到「{name}」")
    d.tap(c[-1])
    d.time.sleep(1)


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


def pick_from_list(target, anchor=None, via=None):
    """選擇鈕（帳戶、幣別）：點開跳出的清單，選 target。按鈕用「上面的小標籤 anchor」或「按鈕上目前的字 via」找；
    清單裡同名的（常用帳戶、類型標題、帳戶）選最下面那個"""
    ns = d.nodes()
    if via:
        btn = next(n for n in ns if n.text == via)
    else:
        lab = next(n for n in ns if n.text == anchor)
        below = [n for n in ns if n.text and 0 < n.cy - lab.cy < 180 and n.x1 < 400]
        btn = min(below, key=lambda n: n.cy)
    d.tap(btn)
    d.wait_text("關閉", timeout=10)
    d.time.sleep(0.6)
    hits = [n for n in d.nodes() if n.text == target]
    if not hits:
        raise TimeoutError(f"清單裡找不到「{target}」")
    d.tap(max(hits, key=lambda n: n.cy))
    d.time.sleep(0.8)


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
