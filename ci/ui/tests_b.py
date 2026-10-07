# -*- coding: utf-8 -*-
"""分流 b：報銷、統計"""
import datetime

import driver as d
import flows as f
import seed as S
from runner import case

B = "b"


def reimb_seed(**kw):
    s = S.with_reimb(f.today(), **kw)
    s.expense(0, 700, S.C_FOOD, note="today", reimb=1, reimb_amount=700, items=[S.reimb_item("Ming", 700)])
    return s.json()


def clear(n):
    d.clear(n)


def money_after(ns, label):
    """某個標籤文字下面緊接著的金額文字"""
    lb = d.first(ns, label, exact=False)
    if not lb:
        return None
    below = [n for n in ns if n.text.startswith("$") and n.cy > lb.cy and n.cy - lb.cy < 160]
    return min(below, key=lambda n: n.cy).text if below else None


def reimb_total():
    ns = d.nodes()
    return money_after(ns, "還沒收到的報銷款")


def open_edit_reimb_switch_on(multi=False):
    """記一筆 → 報銷整頁，把開關打開"""
    f.chip_scroll("報銷", "新增標籤")
    d.wait_text("這筆的報銷", timeout=15)
    ns = d.nodes()
    sw = [n for n in ns if n.checkable]
    if sw and not sw[0].checked:
        d.tap(sw[0])
    # 報銷頁已經沒有「一人・全額／分給多人」分頁：一律是對象列表，預設一列、全額


def fill_rows(rows):
    """依序填多人報銷列：[(對象, 金額), ...]；第一列已存在，之後用「新增對象」"""
    for i, (who, amt) in enumerate(rows):
        if i >= 1:
            d.hide_ime()
            d.tap_text("新增對象", exact=False)
        ed = d.edits()
        d.fill(ed[2 * i], who)
        ed = d.edits()
        d.fill(ed[2 * i + 1], str(amt))
    d.hide_ime()


def done_reimb():
    d.tap_text("完成", exact=True)
    d.wait_text("備註（選填）", timeout=15)


def to_reimb_home():
    f.me_page("報銷")
    d.wait_text("還沒收到的報銷款", timeout=15)


def month_spent(ns):
    return money_after(ns, "本月支出")


# ───────── 建立報銷
@case(B, "一人全額報銷並填對象")
def t_full():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    open_edit_reimb_switch_on(multi=False)
    d.shot("一人全額")
    ed = d.edits()
    d.check("有對象輸入框", len(ed) >= 1)
    d.fill(ed[0], "Zed")
    d.hide_ime()
    d.shot("填好對象")
    done_reimb()
    f.keypad("400")
    ns = d.shot("輸入金額後")
    d.check("報銷鈕顯示「待報銷」（金額與實付相同時不另外顯示）", d.has(ns, "待報銷"))
    f.save_edit()
    ns = d.shot("儲存後的首頁")
    d.check("本月支出變成 $0（全額報銷）", month_spent(ns) == "$0", month_spent(ns))
    to_reimb_home()
    ns = d.shot("報銷總覽")
    d.check("總覽有對象 Zed 與 $400", d.has(ns, "Zed", True) and d.has(ns, "$400"))


@case(B, "多人分攤報銷")
def t_multi():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    f.keypad("1500")
    open_edit_reimb_switch_on(multi=True)
    fill_rows([("Ming", 500), ("Hua", 700)])
    ns = d.shot("填好兩位")
    d.check("整頁顯示可報銷 $1,200、自己負擔 $300", d.has(ns, "$1,200") and d.has(ns, "$300"))
    done_reimb()
    f.save_edit()
    ns = d.shot("儲存後的首頁")
    d.check("本月支出為 $300", month_spent(ns) == "$300", month_spent(ns))
    to_reimb_home()
    ns = d.shot("報銷總覽")
    d.check("看到 Ming 與 Hua", d.has(ns, "Ming", True) and d.has(ns, "Hua", True))


@case(B, "報銷金額少於實付：自己負擔算進支出")
def t_partial():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    f.keypad("1500")
    open_edit_reimb_switch_on(multi=True)
    fill_rows([("Ming", 900)])
    ns = d.shot("報銷 900")
    d.check("自己負擔顯示 $600", d.has(ns, "$600"))
    done_reimb()
    f.save_edit()
    ns = d.shot("首頁")
    d.check("本月支出為 $600", month_spent(ns) == "$600", month_spent(ns))


@case(B, "報銷整頁編輯返回後資料保留")
def t_page_keep():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    f.keypad("1500")
    open_edit_reimb_switch_on(multi=True)
    fill_rows([("Ming", 500), ("Hua", 700)])
    done_reimb()
    f.chip_scroll("待報銷", "新增標籤")
    d.wait_text("這筆的報銷", timeout=15)
    ns = d.shot("再次開啟報銷整頁")
    t = [n.text for n in d.edits()]
    d.check("對象與金額都還在", "Ming" in t and "Hua" in t and "500" in t and "700" in t, t)


@case(B, "報銷金額超過實付", visual=True)
def t_over():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    f.keypad("1000")
    open_edit_reimb_switch_on(multi=True)
    fill_rows([("Ming", 1500)])
    ns = d.shot("報銷 1500 > 實付 1000")
    d.check("整頁出現超過上限提示", d.has(ns, "超過上限") or d.has(ns, "比實付多"))
    done_reimb()
    f.save_edit()
    to_reimb_home()
    ns = d.shot("報銷總覽")
    d.check("總覽的待收被截到 $1,000", reimb_total() == "$1,000", reimb_total())


@case(B, "先填報銷對象與金額，後填帳目金額")
def t_late_amount():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    open_edit_reimb_switch_on(multi=True)
    fill_rows([("Ming", 500), ("Hua", 700)])
    ns = d.shot("實付 $0 時填好兩位", contrast=True)
    d.check("此時不應顯示「超過上限」誤導提示", not d.has(ns, "超過上限"), "實付為 $0 時尚未輸入金額")
    done_reimb()
    ns = d.shot("回到記一筆（尚未輸入金額）")
    d.check("報銷鈕仍顯示已設定", d.has(ns, "待報銷"))
    f.keypad("1500")
    ns = d.shot("輸入金額 1500 後")
    d.check("報銷鈕仍有 2 人", d.has(ns, "2 人"))
    f.save_edit()
    to_reimb_home()
    ns = d.shot("報銷總覽")
    d.check("總覽有 Ming 與 Hua", d.has(ns, "Ming", True) and d.has(ns, "Hua", True))
    d.check("待收合計 $1,200", reimb_total() == "$1,200", reimb_total())


@case(B, "先設報銷後把帳目金額改小")
def t_shrink():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    f.keypad("1500")
    open_edit_reimb_switch_on(multi=True)
    fill_rows([("Ming", 500), ("Hua", 700)])
    done_reimb()
    for _ in range(4):
        d.tap_text("⌫", exact=True, lowest=True)
    f.keypad("1000")
    ns = d.shot("金額改成 1000")
    f.save_edit()
    ns = d.shot("首頁")
    d.check("本月支出為 $0（報銷被截在實付內）", month_spent(ns) == "$0", month_spent(ns))
    to_reimb_home()
    d.check("待收合計不超過 $1,000", reimb_total() == "$1,000", reimb_total())


@case(B, "全額報銷後改金額")
def t_full_then_change():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    f.keypad("800")
    open_edit_reimb_switch_on(multi=False)
    d.fill(d.edits()[0], "Zed")
    d.hide_ime()
    done_reimb()
    for _ in range(3):
        d.tap_text("⌫", exact=True, lowest=True)
    f.keypad("1200")
    f.save_edit()
    to_reimb_home()
    d.shot("報銷總覽")
    d.check("報銷金額跟著變成 $1,200", reimb_total() == "$1,200", reimb_total())


@case(B, "匯入的待報銷（舊單一報銷格式）")
def t_legacy():
    d.fresh(f.base_seed())
    to_reimb_home()
    ns = d.shot("報銷總覽", contrast=True)
    d.check("舊單一報銷顯示為「沒填對象」", d.has(ns, "沒填對象", True))
    d.check("多人報銷的 Amy、Bob 都在", d.has(ns, "Amy", True) and d.has(ns, "Bob", True))
    d.check("待收合計 $5,100（1800 + 900 + 1000 + 1400）", reimb_total() == "$5,100", reimb_total())


# ───────── 總覽與收款
@case(B, "總覽三個分頁等寬")
def t_tabs_equal():
    d.fresh(reimb_seed())
    to_reimb_home()
    ns = d.shot("報銷總覽")
    a, b, c = d.first(ns, "依對象"), d.first(ns, "依帳單"), d.first(ns, "已收款")
    d.check("找到三個分頁", all([a, b, c]))
    d1, d2 = b.cx - a.cx, c.cx - b.cx
    d.check("三個分頁中心等距（等寬）", abs(d1 - d2) <= 10, f"{d1} / {d2}")
    d.tap(b)
    d.shot("依帳單")
    d.tap(c)
    d.shot("已收款")


@case(B, "依對象：每人只列欠最久的一筆")
def t_oldest_only():
    d.fresh(reimb_seed())
    to_reimb_home()
    ns = d.shot("依對象")
    ming = [n for n in ns if n.text == "Ming"]
    d.check("Ming 只有一列", len(ming) == 1, len(ming))
    d.check("該列標示「另有 N 筆」", any("另有" in n.text for n in ns))
    d.check("顯示「欠最久」", d.has(ns, "欠最久"))


def receive_page(who):
    to_reimb_home()
    d.tap(d.wait(lambda n: n.text == who, 10, who))
    d.wait_text("這次收到多少", timeout=15)


def confirm_receive():
    d.tap_text("確認收款", exact=False)
    d.time.sleep(1.5)


@case(B, "收款：全額收齊")
def t_receive_full():
    d.fresh(reimb_seed())
    receive_page("Hua")
    ns = d.shot("收款頁", contrast=True)
    d.check("預設金額為還欠的 $800", "800" in [n.text for n in d.edits()], [n.text for n in d.edits()])
    confirm_receive()
    ns = d.shot("確認後")
    d.check("回到總覽，Hua 已不在待收清單", not d.has(ns, "Hua", True))
    d.tap_text("已收款", exact=True)
    d.time.sleep(1)
    ns = d.shot("已收款分頁")
    d.check("已收款出現 +$800", d.has(ns, "+$800", True))


@case(B, "收款：分批收款")
def t_receive_partial_fifo():
    d.fresh(reimb_seed())
    receive_page("Ming")
    d.shot("Ming 的收款頁（共欠 $2,200，三筆）")
    ed = d.edits()
    d.fill(ed[0], "1200")
    d.hide_ime()
    ns = d.shot("輸入 1200 後的分配")
    d.check("由舊到新自動分配：最舊的一筆收 1000", "1000" in [n.text for n in d.edits()], [n.text for n in d.edits()])
    confirm_receive()
    if d.has(d.nodes(), "還有沒收到的款項"):
        d.shot("追問對話框")
        d.tap_text("繼續追", exact=True)
        d.time.sleep(1.5)
    ns = d.shot("確認後")
    d.check("Ming 還欠 $1,000（2200 − 1200）", d.has(ns, "$1,000", True), [n.text for n in ns if n.text.startswith("$")])


@case(B, "少收時：繼續追剩下的")
def t_short_chase():
    d.fresh(reimb_seed())
    receive_page("Hua")
    d.fill(d.edits()[0], "500")
    d.hide_ime()
    ns = d.shot("少收 500")
    d.check("出現「還差 $300，要繼續追嗎？」", d.has(ns, "還差 $300"))
    confirm_receive()
    ns = d.shot("追問對話框")
    d.check("彈出「還有沒收到的款項」", d.has(ns, "還有沒收到的款項"))
    d.tap_text("繼續追", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("選繼續追後")
    d.check("Hua 仍在待收清單，剩 $300", d.has(ns, "Hua", True) and d.has(ns, "$300", True))


@case(B, "少收時：不追了（自己負擔）")
def t_short_giveup():
    d.fresh(reimb_seed())
    receive_page("Hua")
    d.fill(d.edits()[0], "500")
    d.hide_ime()
    confirm_receive()
    d.tap_text("不追了", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("選不追了後")
    d.check("Hua 已不在待收清單", not d.has(ns, "Hua", True))
    d.tap_text("已收款", exact=True)
    d.time.sleep(1)
    ns = d.shot("已收款分頁")
    row = next((n for n in ns if "Hua" in n.text), None)
    d.check("已收款分頁有 Hua 的收款", row is not None)
    if row:
        d.tap(row)
        d.time.sleep(1.5)
        ns = d.shot("Hua 的明細")
        d.check("帳單顯示「不追了，少收 $300」", d.has(ns, "不追了，少收 $300"))


@case(B, "少收對話框取消")
def t_short_cancel():
    d.fresh(reimb_seed())
    receive_page("Hua")
    d.fill(d.edits()[0], "500")
    d.hide_ime()
    confirm_receive()
    d.wait_text("還有沒收到的款項", timeout=8)
    d.tap_text("取消", exact=True)
    d.time.sleep(1)
    ns = d.shot("按取消後")
    d.check("仍在收款頁", d.has(ns, "這次收到多少"))
    d.check("沒有寫入收款（按鈕仍在）", d.has(ns, "確認收款", False))


@case(B, "多收的處理")
def t_overpay():
    d.fresh(reimb_seed())
    receive_page("Hua")
    d.fill(d.edits()[0], "1000")
    d.hide_ime()
    ns = d.shot("收 1000（欠 800）")
    d.check("提示「多收 $200，多的部分會算成報銷回饋收入」", d.has(ns, "多收 $200"))
    confirm_receive()
    d.shot("確認後")
    f.tab("統計") if False else None
    d.check("流程完成沒有閃退", d.has(d.nodes(), "還沒收到的報銷款"))


@case(B, "收款存到不同帳戶", visual=True)
def t_other_account():
    d.fresh(reimb_seed())
    receive_page("Hua")
    d.tap_text("測試銀行", exact=False)
    d.shot("選測試銀行")
    confirm_receive()
    d.tap_text("已收款", exact=True)
    d.time.sleep(1)
    ns = d.shot("已收款分頁")
    d.check("收款存入「測試銀行」", d.has(ns, "存入 測試銀行"))


@case(B, "收款金額為空或 0")
def t_zero_receive():
    d.fresh(reimb_seed())
    receive_page("Hua")
    clear(d.edits()[0])
    d.hide_ime()
    ns = d.shot("金額清空")
    btn = next((n for n in ns if n.text.startswith("確認收款")), None)
    d.check("確認收款按鈕存在（不灰掉）", btn is not None and not f.is_disabled(ns, btn), btn and btn.text)
    d.tap(btn)
    d.time.sleep(1)
    ns = d.shot("按確認收款後")
    d.check("金額欄下方提示「請輸入這次收到的金額」，沒有寫入收款", d.has(ns, "請輸入這次收到的金額") and d.has(ns, "這次收到多少"), [n.text for n in ns if n.text][:20])


def go_person(who):
    to_reimb_home()
    d.tap_text("已收款", exact=True)
    d.time.sleep(1)
    row = d.wait(lambda n: who in n.text, 8, who)
    d.tap(row)
    d.wait_text("收款紀錄", timeout=10)


@case(B, "修改收款紀錄")
def t_edit_pay():
    d.fresh(reimb_seed())
    go_person("Lin")
    d.shot("Lin 的明細")
    d.tap_text("⋯", exact=True)
    d.tap_text("修改", exact=True)
    d.wait_text("修改收款", timeout=8)
    d.shot("修改收款對話框")
    d.fill(d.edits()[0], "300")
    d.hide_ime()
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("儲存後")
    d.check("收款紀錄變成 $300", d.has(ns, "收到 $300"))
    d.check("還欠 $300（600 − 300）", d.has(ns, "$300", True))


@case(B, "刪除收款紀錄")
def t_delete_pay():
    d.fresh(reimb_seed())
    go_person("Lin")
    d.tap_text("⋯", exact=True)
    d.tap_text("刪除", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("刪除後")
    d.check("顯示「還沒有收款紀錄」", d.has(ns, "還沒有收款紀錄"))
    d.check("還欠金額恢復為 $600", d.has(ns, "$600", True))


@case(B, "刪除含報銷的帳目後復原")
def t_delete_reimb_txn():
    d.fresh(reimb_seed())
    row = d.first(d.nodes(), "-$700", True)
    d.check("首頁找得到今天的報銷帳目", row is not None, [n.text for n in d.nodes() if n.text.startswith("-$")])
    if row:
        f.swipe_row_left(row)
        d.tap_text("刪除", exact=True)
        d.time.sleep(1)
        d.tap_text("復原", exact=True)
        d.time.sleep(1.5)
        to_reimb_home()
        d.shot("復原後的報銷總覽")
        d.check("待收合計恢復為 $3,400", reimb_total() == "$3,400", reimb_total())


@case(B, "首頁待報銷條、帳戶頁報銷待收", visual=True)
def t_home_bar():
    d.fresh(f.base_seed())
    ns = d.shot("首頁", contrast=True)
    bar = next((n for n in ns if n.text.startswith("待報銷") and "筆" in n.text), None)
    d.check("首頁有待報銷提示條", bar is not None, [n.text for n in ns if n.text.startswith("待報銷")])
    if bar:
        d.tap(bar)
        d.time.sleep(1.5)
        ns = d.shot("點提示條後")
        d.check("進入報銷頁", d.has(ns, "還沒收到的報銷款"))


@case(B, "沒有報銷時的空狀態")
def t_reimb_empty():
    d.fresh(None)
    to_reimb_home()
    ns = d.shot("報銷空狀態")
    d.check("顯示「沒有待報銷的項目」", d.has(ns, "沒有待報銷的項目"))


# ───────── 統計
def stats_seed(**kw):
    s = S.Seed(f.today(), **kw)
    s.expense(0, 120, S.C_LUNCH)
    s.expense(0, 300, S.C_METRO)
    s.expense(0, 80, S.C_FOOD)
    s.income(0, 5000, S.C_SALARY)
    return s.json()


def pill(ns, a, b):
    na = [n for n in ns if n.text == a]
    nb = [n for n in ns if n.text == b]
    best = None
    for x in na:
        for y in nb:
            if abs(x.cy - y.cy) <= 6 and x.cx < y.cx and (best is None or y.cx - x.cx < best[1]):
                best = (y, y.cx - x.cx)
    return best[0] if best else None


@case(B, "支出與收入切換", visual=True)
def t_stats_kind():
    d.fresh(stats_seed())
    f.tab("統計")
    d.time.sleep(2)
    ns = d.shot("統計：支出", contrast=True)
    d.check("支出模式有餐飲、交通", d.has(ns, "餐飲") and d.has(ns, "交通"))
    inc = pill(ns, "支出", "收入")
    d.check("找到支出／收入切換", inc is not None)
    if inc:
        d.tap(inc)
        d.time.sleep(1.5)
        ns = d.shot("統計：收入")
        d.check("收入模式有薪資，沒有餐飲", d.has(ns, "薪資") and not d.has(ns, "餐飲", True))


@case(B, "下鑽後返回仍停在原類型")
def t_drill_back():
    d.fresh(stats_seed())
    f.tab("統計")
    d.time.sleep(2)
    inc = pill(d.nodes(), "支出", "收入")
    d.check("找到切換", inc is not None)
    d.tap(inc)
    d.time.sleep(1.5)
    rows = sorted([n for n in d.nodes() if n.text == "薪資"], key=lambda n: n.cy)
    d.tap(rows[-1])   # 最下面的是清單那一列，上面的是圓餅圖旁的標籤
    d.time.sleep(2)
    ns = d.shot("下鑽頁")
    d.check("真的進入下鑽頁（有返回箭頭）", d.has(ns, "返回", True))
    d.tap_back()
    d.time.sleep(2)
    ns = d.shot("返回後")
    d.check("仍是收入（有薪資、沒有餐飲）", d.has(ns, "薪資") and not d.has(ns, "餐飲", True))


@case(B, "月份切換與跨月邊界", visual=True)
def t_month_switch():
    d.fresh(f.base_seed())
    f.tab("統計")
    d.time.sleep(2)
    ns = d.shot("本月")
    prev = next((n for n in ns if n.desc == "上個月"), None)
    d.check("有上個月按鈕", prev is not None)
    if prev:
        d.tap(prev)
        d.time.sleep(1.5)
        d.shot("上個月")
        d.tap(next(n for n in d.nodes() if n.desc == "上個月"))
        d.time.sleep(1.5)
        d.shot("再上一個月（可能沒資料）")


@case(B, "子項目縮排與圖示", visual=True)
def t_sub_indent():
    d.fresh(stats_seed())
    f.tab("統計")
    d.time.sleep(2)
    d.tap_text("餐飲", exact=True)
    d.time.sleep(1.5)
    d.shot("展開餐飲的子項目")


@case(B, "圓環圖標籤不被遮住", visual=True)
def t_donut():
    s = S.Seed(f.today())
    for i, (c, a) in enumerate([(S.C_FOOD, 1200), (S.C_TRAFFIC, 700), (S.C_SHOP, 300), (S.C_HOME, 40), (S.C_PLAY, 30), (S.C_MED, 20)]):
        s.expense(0, a, c)
    d.fresh(s.json())
    f.tab("統計")
    d.time.sleep(2.5)
    d.shot("圓環圖（含多個小比例）")


@case(B, "存錢趨勢與趨勢期數", visual=True)
def t_trend():
    d.fresh(f.base_seed())
    f.tab("統計")
    d.time.sleep(2)
    d.tap_text("趨勢", exact=True)
    d.time.sleep(2)
    d.shot("趨勢")


@case(B, "收款頁看得出實際項目：有備註顯示備註，分類顯示完整路徑", visual=True)
def t_receive_shows_item():
    s = S.with_reimb(f.today())
    s.expense(0, 300, S.C_LUNCH, note="team lunch", reimb=1, reimb_amount=300, items=[S.reimb_item("Amy", 300)])
    s.expense(-1, 150, S.C_LUNCH, reimb=1, reimb_amount=150, items=[S.reimb_item("Amy", 150)])
    d.fresh(s.json())
    receive_page("Amy")
    ns = d.shot("Amy 的收款頁")
    d.check("有備註的那筆顯示備註「team lunch」", any(n.text.startswith("team lunch") for n in ns), [n.text for n in ns if n.text][:30])
    d.check("備註下面另一行是分類路徑「餐飲 › 午餐」", d.has(ns, "餐飲 › 午餐", True), [n.text for n in ns if n.text][:30])
    d.check("沒有備註的那筆直接顯示「餐飲 › 午餐 日期」", any(n.text.startswith("餐飲 › 午餐 ") for n in ns), [n.text for n in ns if n.text][:30])


def split_page_with_three():
    """實付 600，分給多人：Amy、Bob、Cat 三個人，金額都空白"""
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    f.keypad("600")
    open_edit_reimb_switch_on(multi=True)
    d.time.sleep(0.8)
    d.clear(d.edits()[1])
    d.hide_ime()
    d.fill(d.edits()[0], "Amy")
    d.hide_ime()
    for who in ("Bob", "Cat"):
        d.tap_text("新增對象", exact=False)
        d.time.sleep(0.6)
        ed = d.edits()
        d.fill(ed[-2], who)
        d.hide_ime()


def amounts():
    return [e.text for i, e in enumerate(d.edits()) if i % 2 == 1]


@case(B, "報銷平分：平分給幾人、含自己，兩種分法", visual=True)
def t_split_modes():
    split_page_with_three()
    d.tap_text("平分", exact=False)
    d.time.sleep(0.8)
    ns = d.shot("平分選單")
    d.check("選項一：平分給 3 人（不含我），每人 $200", d.has(ns, "平分給 3 人") and d.has(ns, "每人 $200", True), [n.text for n in ns if n.text][:30])
    d.check("選項二：平分給 4 人（含我），每人 $150・我 $150", d.has(ns, "平分給 4 人（含我）") and d.has(ns, "每人 $150・我 $150", True))
    d.check("選項三還不能選：先填至少一人的金額", d.has(ns, "先填至少一人的金額"))
    d.tap_text("平分給 3 人", exact=False)
    d.time.sleep(1)
    ns = d.shot("平分給 3 人之後")
    d.check("三個人各 200", amounts() == ["200", "200", "200"], amounts())
    d.check("可報銷 $600、自己負擔 $0", d.has(ns, "$600", True) and d.has(ns, "$0", True))
    d.tap_text("平分", exact=False)
    d.time.sleep(0.8)
    d.tap_text("平分給 4 人（含我）", exact=False)
    d.time.sleep(1)
    ns = d.shot("平分給 4 人（含我）之後")
    d.check("三個人各 150", amounts() == ["150", "150", "150"], amounts())
    d.check("可報銷 $450、自己負擔 $150", d.has(ns, "$450", True) and d.has(ns, "$150", True), [n.text for n in ns if n.text.startswith("$")])


@case(B, "報銷平分：平分剩下的（含自己），固定金額的人不動", visual=True)
def t_split_rest():
    split_page_with_three()
    d.fill(d.edits()[1], "300")
    d.hide_ime()
    d.tap_text("平分", exact=False)
    d.time.sleep(0.8)
    ns = d.shot("Amy 填 300 後的平分選單")
    d.check("選項三可以選：剩 $300 ÷ 3 人", d.has(ns, "剩 $300 ÷ 3 人"), [n.text for n in ns if n.text][:30])
    d.tap_text("平分剩下的", exact=False)
    d.time.sleep(1.2)
    ns = d.shot("平分剩下的之後")
    d.check("Amy 固定 300，Bob、Cat 各 100", amounts() == ["300", "100", "100"], amounts())
    d.check("自動那兩列有提示：自動：剩下 $300 ÷ 3 人（含我）", sum(1 for n in ns if "自動：剩下 $300 ÷ 3 人（含我）" in n.text) == 2, [n.text for n in ns if "自動" in n.text])
    d.fill(d.edits()[1], "400")
    d.hide_ime()
    d.time.sleep(1.2)
    ns = d.shot("Amy 改成 400")
    d.check("Amy 改成 400 後，Bob、Cat 跟著重算成各 66", amounts() == ["400", "66", "66"], amounts())
    d.fill(d.edits()[3], "50")
    d.hide_ime()
    d.time.sleep(1.2)
    ns = d.shot("手動把 Bob 改成 50")
    d.check("手動改過的 Bob 固定 50，剩下的 150 由 Cat 和我平分，Cat 變 75", amounts() == ["400", "50", "75"], amounts())


@case(B, "報銷頁預設一人全額：沒有分頁，一列、金額是實付", visual=True)
def t_reimb_default_single():
    d.fresh(S.Seed(f.today()).json())
    f.open_add()
    f.keypad("400")
    open_edit_reimb_switch_on(multi=False)
    d.time.sleep(0.8)
    ns = d.shot("打開報銷")
    d.check("不再有「一人・全額」「分給多人」分頁", not d.has(ns, "一人・全額") and not d.has(ns, "分給多人"), [n.text for n in ns if n.text][:30])
    d.check("預設一列：對象空白、金額是實付 400", len(d.edits()) == 2 and d.edits()[1].text == "400", [e.text for e in d.edits()])
    d.check("有「＋ 新增對象」與「平分」", d.has(ns, "＋ 新增對象") and d.has(ns, "平分", False))
    d.fill(d.edits()[0], "Zed")
    d.hide_ime()
    done_reimb()
    f.save_edit()
    to_reimb_home()
    ns = d.shot("報銷總覽")
    d.check("總覽有對象 Zed 與 $400（全額）", d.has(ns, "Zed", True) and d.has(ns, "$400"))


@case(B, "點首頁的「明細」分頁可以切換列表和日曆", visual=True)
def t_tab_toggle_view():
    d.fresh(f.base_seed())
    ns = d.nodes()
    d.check("一開始是列表：有「切換成日曆」鈕", d.has(ns, "切換成日曆", True))
    f._tap_tab("明細")
    d.time.sleep(1)
    ns = d.shot("再點一次「明細」分頁")
    d.check("變成日曆：鈕變成「切換成明細列表」", d.has(ns, "切換成明細列表", True))
    f._tap_tab("明細")
    d.time.sleep(1)
    ns = d.shot("又點一次")
    d.check("變回列表", d.has(ns, "切換成日曆", True))


@case(B, "收款時勾選對方這次已經給的帳，最上面統計合計", visual=True)
def t_receive_select():
    d.fresh(S.with_reimb(f.today()).json())
    receive_page("Ming")
    ns = d.shot("Ming 的收款頁（預設全選）")
    d.check("最上面：這次勾選 2 筆，合計 $1,500", d.has(ns, "這次勾選 2 筆，合計") and d.has(ns, "$1,500", True), [n.text for n in ns if n.text][:30])
    d.check("收到金額預設是勾選的合計 1500", "1500" in [e.text for e in d.edits()], [e.text for e in d.edits()])
    boxes = sorted([n for n in ns if n.checkable], key=lambda n: n.cy)
    d.check("每一筆帳前面有勾選框（兩筆，預設都勾）", len(boxes) == 2 and all(b.checked for b in boxes), [(b.cy, b.checked) for b in boxes])
    d.tap(boxes[0])          # 取消勾選最舊的那一筆（r1，$1,000）
    d.time.sleep(1)
    ns = d.shot("取消勾選最舊的一筆")
    d.check("合計變成 1 筆 $500", d.has(ns, "這次勾選 1 筆，合計") and d.has(ns, "$500", True), [n.text for n in ns if n.text][:30])
    d.check("收到金額跟著變成 500", "500" in [e.text for e in d.edits()], [e.text for e in d.edits()])
    d.tap_text("確認收款", exact=False)
    d.time.sleep(1.5)
    to_reimb_home()
    ns = d.shot("收完之後的報銷總覽")
    d.check("Ming 還欠 $1,000（沒勾選的那一筆）", d.has(ns, "Ming", True) and d.has(ns, "$1,000", True), [n.text for n in ns if n.text.startswith("$") or n.text == "Ming"])


@case(B, "收款勾選：全不選不能確認，全選回復", visual=True)
def t_receive_select_none():
    d.fresh(S.with_reimb(f.today()).json())
    receive_page("Ming")
    d.tap_text("全不選", exact=True)
    d.time.sleep(1)
    ns = d.shot("全不選")
    d.check("合計 0 筆", d.has(ns, "這次勾選 0 筆，合計"), [n.text for n in ns if "勾選" in n.text])
    d.tap_text("確認收款", exact=False)
    d.time.sleep(1)
    ns = d.shot("全不選按確認收款")
    d.check("提示「請先勾選對方已經給的帳」，還在收款頁", d.has(ns, "請先勾選對方已經給的帳") and d.has(ns, "這次收到多少"), [n.text for n in ns if n.text][:20])
    d.tap_text("全選", exact=True)
    d.time.sleep(1)
    ns = d.shot("全選")
    d.check("全選回來：2 筆合計 $1,500", d.has(ns, "這次勾選 2 筆，合計") and d.has(ns, "$1,500", True))

