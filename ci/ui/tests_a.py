# -*- coding: utf-8 -*-
"""分流 a：記一筆、明細列、編輯、導覽"""
import driver as d
import flows as f
import seed as S
from runner import case

A = "a"


def home_seed(**kw):
    return f.base_seed(**kw)


# ───────── 記一筆：版面與鍵盤
@case(A, "版面順序正確")
def t_layout():
    d.fresh(home_seed())
    f.open_add()
    ns = d.shot("記一筆初始畫面")
    note = d.first(ns, "備註（選填）")
    food = d.first(ns, "餐飲")
    date = next((n for n in ns if n.text.startswith("今天")), None)
    amount = d.first(ns, "$0")
    k7 = d.first(ns, "7")
    d.check("找到各區塊", all([note, food, date, amount, k7]))
    d.check("由上到下：備註 → 分類 → 按鈕列 → 金額 → 鍵盤", note.cy < food.cy < date.cy < amount.cy < k7.cy,
            f"{note.cy}, {food.cy}, {date.cy}, {amount.cy}, {k7.cy}")
    below = [n for n in ns if n.text == "餐飲" and date.cy < n.cy < k7.cy]
    d.check("金額卡旁邊沒有顯示類別名稱", not below)


@case(A, "點備註時鍵盤覆蓋而不是推動版面")
def t_kbd_overlay():
    d.set_ime_with_hw_keyboard(True)
    d.fresh(home_seed())
    f.open_add()
    ns = d.nodes()
    date0 = next(n for n in ns if n.text.startswith("今天"))
    amt0 = d.first(ns, "$0")
    d.tap(d.first(ns, "備註（選填）"))
    d.time.sleep(2)
    d.check("系統鍵盤有彈出", d.ime_shown())
    ns = d.shot("點備註後（鍵盤彈出）")
    date1 = next((n for n in ns if n.text.startswith("今天")), None)
    amt1 = d.first(ns, "$0")
    d.check("日期那一列位置沒有移動", date1 is not None and abs(date1.cy - date0.cy) <= 4, f"{date0.cy} → {date1.cy if date1 else None}")
    d.check("金額位置沒有移動", amt1 is not None and abs(amt1.cy - amt0.cy) <= 4, f"{amt0.cy} → {amt1.cy if amt1 else None}")


@case(A, "備註收鍵盤後數字鍵盤自然回來")
def t_kbd_back():
    d.set_ime_with_hw_keyboard(True)
    d.fresh(home_seed())
    f.open_add()
    k7 = d.first(d.nodes(), "7")
    d.tap(d.first(d.nodes(), "備註（選填）"))
    d.time.sleep(1.5)
    d.shot("鍵盤彈出")
    d.back()
    d.time.sleep(1.5)
    ns = d.shot("按返回收鍵盤後")
    d.check("鍵盤已收起", not d.ime_shown())
    d.check("仍在記一筆畫面（沒有被返回鍵關掉）", d.has(ns, "備註（選填）"))
    k7b = d.first(ns, "7")
    d.check("數字鍵盤在原位可用", k7b is not None and abs(k7b.cy - k7.cy) <= 4)


@case(A, "備註框顯示筆形線條與提示字", visual=True)
def t_note_hint():
    d.fresh(home_seed())
    f.open_add()
    # 金額是 0 時，數字鍵盤的「完成」本來就是淡色（還不能存），不算對比不足
    ns = d.shot("備註框", contrast=True, ignore=("完成",))
    d.check("提示字為「備註（選填）」", d.has(ns, "備註（選填）"))


@case(A, "按鈕列圖示為線條風格且不重複", visual=True)
def t_btn_icons():
    d.fresh(home_seed())
    f.open_add()
    d.shot("按鈕列左半")
    d.swipe(950, 1440, 120, 1440)
    d.shot("按鈕列右半")
    d.swipe(950, 1440, 120, 1440)
    ns = d.shot("按鈕列最右")
    d.check("按鈕列有報銷鈕", d.has(ns, "報銷", False))


# ───────── 新增帳目
@case(A, "新增一筆支出")
def t_add_expense():
    d.fresh(home_seed())
    f.open_add()
    d.tap_text("午餐", exact=True)
    f.keypad("250")
    ns = d.shot("輸入 250")
    d.check("金額卡顯示 250", d.has(ns, "$250"))
    f.save_edit()
    ns = d.shot("儲存後的首頁")
    d.check("明細出現 -$250", d.has(ns, "-$250", True))
    d.check("分類顯示「餐飲 › 午餐」", d.has(ns, "餐飲 › 午餐"))


@case(A, "新增一筆收入", visual=True)
def t_add_income():
    d.fresh(home_seed())
    f.open_add()
    d.tap_text("收入", exact=True, nth=0)
    d.time.sleep(1)
    f.keypad("1000")
    f.save_edit()
    ns = d.shot("儲存後的首頁")
    d.check("明細出現 +$1,000", d.has(ns, "+$1,000", True))
    d.check("吉祥物變歡呼（對話含「進來」）", d.has(ns, "進來"))


@case(A, "新增一筆轉帳含手續費", visual=True)
def t_add_transfer():
    d.fresh(home_seed())
    f.open_add()
    d.tap_text("轉帳", exact=True, nth=0)
    d.time.sleep(1)
    d.shot("轉帳畫面")
    f.keypad("5000")
    f.chip_scroll("手續費", "標籤") if d.has(d.nodes(), "手續費") else None
    d.time.sleep(1)
    ns = d.shot("手續費對話框")
    ed = d.edits()
    d.check("手續費對話框有輸入框", len(ed) >= 1)
    d.fill(ed[0], "15")
    d.hide_ime()
    f.confirm_dialog()
    d.time.sleep(1)
    ns = d.shot("設定手續費後")
    d.check("金額卡顯示實際金額 5,015", d.has(ns, "5,015"))
    f.save_edit()
    ns = d.shot("儲存後的首頁")
    # 明細的轉帳列顯示轉帳金額，手續費另外標在副標題
    d.check("明細出現轉帳金額 $5,000 與「手續費 $15」", d.has(ns, "$5,000", True) and d.has(ns, "手續費 $15"))


@case(A, "金額為 0 或空白不能儲存")
def t_zero():
    d.fresh(home_seed())
    f.open_add()
    ns = d.nodes()
    ok = [n for n in ns if n.text == "完成"]
    d.tap(max(ok, key=lambda n: n.cy))
    d.time.sleep(1.5)
    ns = d.shot("金額為 0 按完成後")
    d.check("仍停在記一筆（沒有儲存）", d.has(ns, "備註（選填）"))
    d.check("金額下方顯示提示「請先輸入金額」", d.has(ns, "請先輸入金額"), [n.text for n in ns if n.text][:20])


@case(A, "計算機運算")
def t_calc():
    d.fresh(home_seed())
    f.open_add()
    f.keypad("100")
    d.tap_text("+", exact=True, lowest=True)
    f.keypad("50")
    ns = d.shot("100+50")
    d.check("完成鍵變成「=」", d.has(ns, "=", True))
    d.tap_text("=", exact=True, lowest=True)
    ns = d.shot("按 = 之後")
    d.check("結果為 150", d.has(ns, "$150", True), [n.text for n in ns if n.text.startswith("$")])
    d.tap_text("−", exact=True, lowest=True)
    f.keypad("30")
    d.tap_text("=", exact=True, lowest=True)
    ns = d.shot("150−30")
    d.check("結果為 120", d.has(ns, "$120", True))


@case(A, "優惠／折扣計算", visual=True)
def t_discount():
    d.fresh(home_seed())
    f.open_add()
    f.keypad("3600")
    f.chip_scroll("手續費／優惠", "標籤")
    d.time.sleep(1)
    ns = d.shot("手續費與優惠對話框")
    ed = d.edits()
    d.check("對話框有兩個輸入框", len(ed) >= 2, len(ed))
    d.fill(ed[-1], "200")
    d.hide_ime()
    f.confirm_dialog()
    d.time.sleep(1)
    ns = d.shot("設定優惠後")
    d.check("實際金額為 3,400", d.has(ns, "3,400") or d.has(ns, "$3400"), [n.text for n in ns if "$" in n.text][:6])


@case(A, "標籤新增與重複使用", visual=True)
def t_tags():
    d.fresh(home_seed())
    f.open_add()
    f.keypad("10")
    f.chip_scroll("新增標籤", "今天")           # 多了「外幣」按鈕，標籤按鈕可能要往左捲才看得到
    d.time.sleep(1)
    d.shot("標籤對話框")
    ed = d.edits()
    d.check("標籤對話框有輸入框", len(ed) >= 1)
    d.fill(ed[0], "trip")
    d.hide_ime()
    d.tap_text("新增", exact=True)
    d.time.sleep(1)
    d.shot("新增標籤後")
    f.confirm_dialog()
    d.time.sleep(1)
    ns = d.shot("回到記一筆")
    d.check("按鈕顯示 #trip", d.has(ns, "#trip"))


@case(A, "編輯既有帳目並放棄修改")
def t_edit_discard():
    d.fresh(home_seed())
    d.tap(d.first(d.nodes(), "-$120", True))
    f.edit_open()
    d.shot("進入編輯")
    f.keypad("9")
    d.shot("改了金額")
    d.tap(d.first(d.nodes(), "關閉"))
    d.wait_text("要儲存修改嗎？", timeout=10)
    d.shot("詢問是否儲存")
    d.tap_text("不儲存", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("放棄後的首頁")
    d.check("原金額 -$120 仍在", d.has(ns, "-$120", True))
    d.check("沒有被改成 -$1,209", not d.has(ns, "1,209"))


@case(A, "連點儲存只產生一筆")
def t_double_tap():
    d.fresh(home_seed())
    f.open_add()
    f.keypad("777")
    ns = d.nodes()
    ok = max([n for n in ns if n.text == "完成"], key=lambda n: n.cy)
    d.sh(f"input tap {ok.cx} {ok.cy}; input tap {ok.cx} {ok.cy}; input tap {ok.cx} {ok.cy}")
    d.time.sleep(3)
    # 第一下儲存後就回到主畫面，後面兩下會點到底部分頁（例如「我的」），所以先切回明細再數
    f.tab("明細")
    d.time.sleep(1.5)
    ns = d.shot("連點後的明細")
    n777 = [n for n in ns if n.text == "-$777"]
    d.check("只出現 1 筆 -$777", len(n777) == 1, len(n777))


@case(A, "刪除帳目並復原")
def t_delete_undo():
    d.fresh(home_seed())
    row = d.first(d.nodes(), "-$120", True)
    f.swipe_row_left(row)
    d.shot("左滑露出刪除")
    d.tap_text("刪除", exact=True)
    d.time.sleep(1)
    ns = d.shot("刪除後")
    d.check("出現「復原」", d.has(ns, "復原", True))
    d.check("該筆已消失", not d.has(ns, "-$120", True))
    # 提示只停 4 秒，上面截圖已經花掉時間；再刪一筆，刪完馬上按復原
    row = d.first(d.nodes(), "-$85", True)
    f.swipe_row_left(row)
    d.tap_text("刪除", exact=True)
    d.tap_text("復原", exact=True, timeout=6)
    d.time.sleep(1.5)
    ns = d.shot("按復原後")
    d.check("帳目回來了", d.has(ns, "-$85", True))


@case(A, "已刪除提示 4 秒內消失且不擋最後一筆")
def t_toast_timeout():
    d.fresh(home_seed())
    row = d.first(d.nodes(), "-$120", True)
    f.swipe_row_left(row)
    d.tap_text("刪除", exact=True)
    d.time.sleep(1)
    d.check("提示出現", d.has(d.nodes(), "復原", True))
    d.time.sleep(5)
    ns = d.shot("5 秒後")
    d.check("提示已自動消失", not d.has(ns, "復原", True))


# ───────── 編輯畫面（不再有「正在編輯」提示條）
@case(A, "編輯既有帳目時直接進編輯畫面，沒有「正在編輯」提示條")
def t_banner():
    d.fresh(home_seed())
    for label, why in (("-$120", "有子分類的帳目"), ("-$85", "不細分的帳目")):
        d.tap(d.first(d.nodes(), label, True))
        f.edit_open()
        ns = d.shot(why)
        d.check(f"{why}：沒有「正在編輯」提示條", not d.has(ns, "正在編輯"))
        d.check(f"{why}：金額放回計算機（$" + label[2:] + "）", d.has(ns, "$" + label[2:], True), [n.text for n in ns if n.text.startswith("$")][:5])
        d.tap(d.first(d.nodes(), "關閉"))
        d.time.sleep(1.5)
    row = d.first(d.nodes(), "$500", True)   # 轉帳那一列的金額（不是頂端「收入 $50,000」）
    d.check("找得到轉帳那一列", row is not None)
    if row:
        d.tap(row)
        f.edit_open()
        ns = d.shot("轉帳")
        d.check("轉帳：沒有提示條，上方是支出／收入／轉帳切換", not d.has(ns, "正在編輯") and d.has(ns, "轉帳", True))


@case(A, "新增一筆時不顯示提示條")
def t_no_banner():
    d.fresh(home_seed())
    f.open_add()
    ns = d.shot("新增畫面")
    d.check("沒有「正在編輯」", not d.has(ns, "正在編輯"))


@case(A, "編輯常用記帳顯示名稱", visual=True)
def t_banner_tpl():
    d.fresh(home_seed())
    f.me_page("常用記帳")
    d.time.sleep(1)
    d.shot("常用記帳清單", contrast=True)
    d.tap_text("Tpl A", exact=False)
    f.edit_open()
    ns = d.shot("編輯常用記帳")
    d.check("常用記帳的名稱 Tpl A 顯示在「名稱：Tpl A」按鈕上", any("名稱：Tpl A" in n.text for n in ns), [n.text for n in ns if "名稱" in n.text])
    d.check("沒有「正在編輯」提示條", not d.has(ns, "正在編輯"))


@case(A, "編輯畫面在深色與淺色模式皆可讀", visual=True)
def t_banner_dark():
    for dark, name in ((1, "淺色"), (2, "深色")):
        d.fresh(f.base_seed(dark=dark))
        d.tap(d.first(d.nodes(), "-$120", True))
        f.edit_open()
        d.shot(f"{name}模式的編輯畫面", contrast=True)


# ───────── 明細列
@case(A, "分類標題一律從大分類開始")
def t_row_title():
    d.fresh(home_seed())
    ns = d.shot("首頁明細")
    d.check("有子分類的顯示「餐飲 › 午餐」", d.has(ns, "餐飲 › 午餐", True))
    plain = [n for n in ns if n.text == "餐飲"]
    d.check("不細分的只顯示「餐飲」", len(plain) >= 1, len(plain))
    tab_ok = [n for n in ns if n.text == "午餐"]
    d.check("沒有只顯示子分類「午餐」的列", not tab_ok)
    f.tab("日曆")
    d.time.sleep(1.5)
    ns = d.shot("日曆當天列表")
    d.check("日曆列表同樣格式", d.has(ns, "餐飲 › 午餐", True) or d.has(ns, "餐飲", True))


@case(A, "標題很長時仍顯示待報銷標籤", visual=True)
def t_long_title():
    s = S.Seed(f.today())
    s.extra_cats.append(dict(id=900, name="非常非常長的分類名稱測試用", emoji="img:cat_box", color=1, kind="EXPENSE", parentId=None, order=9))
    s.extra_cats.append(dict(id=901, name="同樣很長的子分類名稱", emoji="img:cat_box", color=1, kind="EXPENSE", parentId=900, order=0))
    s.expense(0, 1234, 901, reimb=1, reimb_amount=1234, items=[S.reimb_item("Ming", 1234)])
    d.fresh(s.json())
    ns = d.shot("長標題", contrast=True)
    # 標籤文字以「待報銷」開頭；頂端摘要條是「待報銷 N 筆…」，要排除
    d.check("待報銷標籤仍顯示", any(n.text.startswith("待報銷") and "筆" not in n.text for n in ns), [n.text for n in ns if n.text.startswith("待報銷")])
    d.check("金額 -$1,234 沒有被擠掉", d.has(ns, "-$1,234", True))


# ───────── 導覽
@case(A, "選取分頁時圖示與文字一起變色", visual=True)
def t_tabs_visual():
    d.fresh(home_seed())
    for name in ("明細", "日曆", "帳戶", "統計", "我的"):
        f.tab(name)
        d.time.sleep(1.2)
        d.shot(f"選取：{name}")


@case(A, "分頁切換保留各頁狀態")
def t_tab_state():
    d.fresh(home_seed())
    f.tab("日曆")
    d.time.sleep(2)
    ns = d.shot("日曆（預設選今天）")
    yday = f.today() - __import__("datetime").timedelta(days=1)
    d.check("測試資料的昨天與今天在同一個月", yday.month == f.today().month, f"昨天 {yday}")
    cells = [n for n in ns if n.text == str(yday.day) and n.cy < 1300 and n.cy > 400]
    d.check("找得到昨天的日期格", bool(cells), [n.text for n in ns if n.text.isdigit()][:40])
    if cells:
        d.tap(cells[0])
        d.time.sleep(1.5)
        ns = d.shot(f"選了 {yday.month}/{yday.day}")
        d.check("下方列出昨天的帳目（薪水 +$50,000）", d.has(ns, "+$50,000", True), [n.text for n in ns if n.text.startswith(("-$", "+$"))])
        d.check("不再顯示今天的午餐 -$120", not d.has(ns, "-$120", True))
        f.tab("統計")
        d.time.sleep(1.5)
        f.tab("日曆")
        d.time.sleep(2)
        ns = d.shot("切到統計再切回日曆")
        d.check("仍停在昨天（還是看到 +$50,000）", d.has(ns, "+$50,000", True))
        d.check("沒有跳回今天（沒有 -$120）", not d.has(ns, "-$120", True))


@case(A, "返回後保留捲動位置")
def t_scroll_keep():
    d.small_screen(True)
    # 我的：捲到最下面，進備份頁再返回
    d.fresh(S.many(f.today()).json())
    f.tab("我的")
    d.time.sleep(1.5)
    d.scroll_down(2)
    ns = d.shot("我的：往下捲")
    target = d.first(ns, "備份與匯入匯出")
    d.check("捲動後看得到「備份與匯入匯出」", target is not None)
    if target:
        y0 = target.cy
        d.tap(target)
        d.time.sleep(1.5)
        d.shot("備份頁")
        d.tap_back()
        d.time.sleep(1.5)
        ns = d.shot("返回後的我的")
        t2 = d.first(ns, "備份與匯入匯出")
        d.check("返回後仍停在原位（備份列位置不變）", t2 is not None and abs(t2.cy - y0) <= 6, f"{y0} → {t2.cy if t2 else None}")
    # 首頁：往下捲，點一筆再返回
    f.tab("明細")
    d.time.sleep(1.5)
    d.scroll_down(2)
    ns = d.shot("首頁：往下捲")
    rows = [n for n in ns if n.text.startswith("-$") and n.cy > 300]
    d.check("首頁捲動後仍有帳目列", bool(rows))
    if rows:
        pick = rows[0]
        y0, label = pick.cy, pick.text
        d.tap(pick)
        f.edit_open()
        d.tap(d.first(d.nodes(), "關閉"))
        d.time.sleep(1.5)
        ns = d.shot("返回後的首頁")
        again = d.first(ns, label)
        d.check("首頁返回後捲動位置保留", again is not None and abs(again.cy - y0) <= 8, f"{label}: {y0} → {again.cy if again else None}")


# ───────── 一般功能
@case(A, "首頁與明細清單", visual=True)
def t_home_list():
    d.fresh(home_seed())
    ns = d.shot("首頁", contrast=True)
    d.check("顯示本月支出", d.has(ns, "本月支出"))
    d.check("今天的三筆都在", d.has(ns, "-$120", True) and d.has(ns, "-$85", True))


@case(A, "日曆檢視", visual=True)
def t_calendar():
    d.fresh(home_seed())
    f.tab("日曆")
    d.time.sleep(2)
    d.shot("日曆", contrast=True)
    ns = d.nodes()
    d.check("日曆有月份標題", any("年" in n.text and "月" in n.text for n in ns))


@case(A, "搜尋", visual=True)
def t_search():
    d.fresh(home_seed())
    f.tab("統計")
    d.time.sleep(1.5)
    ns = d.shot("統計頁")
    btn = next((n for n in ns if n.text.startswith("搜尋記錄")), None)
    d.check("統計頁有搜尋入口", btn is not None, [n.text for n in ns][:12])
    if btn:
        d.tap(btn)
        d.time.sleep(1.5)
        d.shot("搜尋頁", contrast=True)
        ed = d.edits()
        if ed:
            d.fill(ed[0], "lunch")
            d.hide_ime()
            d.time.sleep(1.5)
            ns = d.shot("搜尋 lunch")
            d.check("找到 lunch 那筆", d.has(ns, "-$120", True) or d.has(ns, "lunch"))
        else:
            d.check("搜尋頁有輸入框", False)


@case(A, "空帳本與單筆資料")
def t_empty():
    d.fresh(None)
    ns = d.shot("全新安裝的首頁")
    d.check("首頁顯示空狀態文字", d.has(ns, "還沒有記錄"))
    f.tab("日曆")
    d.time.sleep(1.5)
    d.shot("日曆")
    f.tab("統計")
    d.time.sleep(1.5)
    ns = d.shot("統計")
    d.check("統計頁沒有閃退", d.has(ns, "統計") or d.has(ns, "支出"))
    f.tab("我的")
    d.time.sleep(1.2)
    d.shot("我的")
    f.open_reimb_home() if False else None


@case(A, "預設帳戶、分類、帳本為貼紙圖示", visual=True)
def t_default_icons():
    d.fresh(None)
    d.shot("首頁（預設帳戶與帳本圖示）")
    f.tab("帳戶")
    d.time.sleep(1)
    d.shot("帳戶分頁")
    f.me_page("分類管理")
    d.shot("分類管理")
    d.tap_back()
    f.me_page("帳本管理")
    d.shot("帳本管理")


@case(A, "我的選單與備份卡皆為線條圖示", visual=True)
def t_menu_icons():
    d.fresh(home_seed())
    f.tab("我的")
    d.time.sleep(1.2)
    d.shot("我的主頁（上半）", contrast=True)
    d.scroll_down(1)
    d.shot("我的主頁（下半）")
    ns = d.nodes()
    t = d.first(ns, "備份與匯入匯出")
    if t:
        d.tap(t)
        d.time.sleep(1.5)
        d.shot("備份與匯入匯出", contrast=True)
    d.check("進得去備份頁", t is not None)


@case(A, "左滑刪除、長按拖曳、拖曳排序", visual=True)
def t_gestures():
    d.fresh(home_seed())
    row = d.first(d.nodes(), "-$85", True)
    f.swipe_row_left(row)
    ns = d.shot("左滑露出刪除")
    d.check("左滑露出「刪除」", d.has(ns, "刪除", True))
    d.swipe(300, row.cy, 1000, row.cy)
    d.time.sleep(1)
    d.shot("滑回")
