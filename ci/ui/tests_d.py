# -*- coding: utf-8 -*-
"""0.2.0 之後新增的用例：帳戶分頁、明細／日曆切換、更新餘額、投資帳戶與持股、帳本切換、記一筆選帳戶……
每個用例都用自己的小資料（seed.py），結果寫進 cases.json 對應的編號。"""
import driver as d
import flows as f
import seed as S
from runner import case

D = "d"


# ───────────────────────── 共用小工具 ─────────────────────────
def empty_seed(**kw):
    """沒有任何記錄：現金 $1,000、測試銀行 $50,000、測試信用卡 $0"""
    return S.Seed(f.today(), **kw)


def value_below(ns, label):
    """某個標籤正下方最近的金額文字（例如「本月支出」下面的 $835）"""
    labs = sorted([n for n in ns if n.text == label], key=lambda n: n.cy)
    if not labs:
        return None
    lab = labs[0]
    cand = [n for n in ns if (n.text.startswith("$") or n.text.startswith("-$")) and n.cy > lab.cy and abs(n.x1 - lab.x1) < 60]
    return min(cand, key=lambda n: n.cy).text if cand else None


def to_main():
    """在帳戶明細等子頁時先返回，直到看得到底部分頁"""
    for _ in range(3):
        if [n for n in d.nodes() if n.text == "明細" and n.cy > 2000]:
            return
        d.tap_back()


def disabled(ns, label):
    t = d.first(ns, label, True)
    return bool(t) and f.is_disabled(ns, t)


def acc_tab():
    to_main()
    f.tab("帳戶")
    d.wait_text("總資產", timeout=15)
    d.time.sleep(0.8)


def open_account(name):
    acc_tab()
    d.tap(d.wait(lambda n: n.text == name and n.cy < 2000, 10, f"帳戶「{name}」"))
    d.wait_text("更新餘額", timeout=10)
    d.time.sleep(0.8)


def fill_dialog(values):
    """依序填對話框裡的輸入框（每填一格就收鍵盤，位置不會被推走）；None 代表跳過"""
    for i, v in enumerate(values):
        if v is None:
            continue
        ed = d.edits()
        d.fill(ed[i], v)
        d.hide_ime()


def home_values():
    """回到明細分頁（列表），回傳 (本月支出, 收入)"""
    to_main()
    f.tab("明細")
    d.time.sleep(1)
    ns = d.nodes()
    return value_below(ns, "本月支出"), value_below(ns, "收入")


# ───────────────────────── 記一筆與帳本 ─────────────────────────
@case(D, "記一筆選帳戶可以往下滑")
def t_pick_account_scroll():
    d.fresh(S.base(f.today(), extra_accounts=14).json())
    f.open_add()
    # 帳戶按鈕顯示目前的帳戶名稱（預設第一個：現金）
    d.tap(d.wait(lambda n: n.text == "現金", 10, "帳戶按鈕"))
    d.wait_text("選擇帳戶", timeout=10)
    ns = d.shot("選擇帳戶清單（一開始）")
    d.check("一開始看得到最上面的帳戶「測試銀行」", d.has(ns, "測試銀行", True))
    d.check("一開始看不到最後一個帳戶「帳戶14」（超出畫面）", not d.has(ns, "帳戶14", True))
    for _ in range(4):
        d.swipe(540, 1500, 540, 800)
        if d.has(d.nodes(), "帳戶14", True):
            break
    ns = d.shot("清單往下滑之後")
    d.check("往下滑之後看得到最後一個帳戶「帳戶14」", d.has(ns, "帳戶14", True))
    c = [n for n in ns if n.text == "帳戶14"]
    if c:
        d.tap(c[0])
        d.time.sleep(1)
        ns = d.shot("選了帳戶14")
        d.check("對話框關閉，帳戶按鈕顯示「帳戶14」", d.has(ns, "帳戶14", True) and not d.has(ns, "選擇帳戶", True))


@case(D, "切換帳本的清單可以往下滑")
def t_pick_book_scroll():
    d.fresh(empty_seed(extra_books=30).json())
    d.tap(d.wait(lambda n: n.text == "我的帳本", 10, "帳本按鈕"))
    d.wait_text("切換帳本", timeout=10)
    ns = d.shot("切換帳本（一開始）")
    d.check("一開始看得到「帳本02」", d.has(ns, "帳本02", True))
    d.check("一開始看不到最後一個帳本「帳本31」（超出畫面）", not d.has(ns, "帳本31", True))
    for _ in range(12):   # 31 個帳本，一次約滑過 6 個
        d.swipe(540, 1500, 540, 800)
        if d.has(d.nodes(), "帳本31", True):
            break
    ns = d.shot("往下滑之後")
    d.check("往下滑之後看得到「帳本31」", d.has(ns, "帳本31", True))
    c = [n for n in ns if n.text == "帳本31"]
    if c:
        d.tap(c[0])
        d.time.sleep(1.5)
        ns = d.shot("切換到帳本31")
        d.check("對話框關閉，標題列顯示「帳本31」", d.has(ns, "帳本31", True) and not d.has(ns, "切換帳本", True))


# ───────────────────────── 帳戶分頁 ─────────────────────────
@case(D, "帳戶分頁依類型分組並顯示小計")
def t_acc_groups():
    d.fresh(empty_seed().json())
    acc_tab()
    ns = d.shot("帳戶分頁")
    d.check("顯示總資產 $51,000", d.has(ns, "$51,000", True), [n.text for n in ns if n.text.startswith("$")][:8])
    heads = {k: [n for n in ns if n.text == k] for k in ("現金", "銀行", "信用卡")}
    d.check("有「現金」「銀行」「信用卡」三個分組標題", all(len(v) >= 1 for v in heads.values()))
    if all(heads.values()):
        hc = min(heads["現金"], key=lambda n: n.cy)
        hb, hk = heads["銀行"][0], heads["信用卡"][0]
        d.check("分組由上到下：現金 → 銀行 → 信用卡", hc.cy < hb.cy < hk.cy, (hc.cy, hb.cy, hk.cy))
        bank = d.first(ns, "測試銀行", True)
        card = d.first(ns, "測試信用卡", True)
        d.check("測試銀行在「銀行」組底下、測試信用卡在「信用卡」組底下", bool(bank and card) and hb.cy < bank.cy < hk.cy < card.cy)
    d.check("沒有帳戶的類型不顯示（電子票證、電子支付、投資、其他）", not any(d.has(ns, t, True) for t in ("電子票證", "電子支付", "投資", "其他")))
    d.check("每組小計：現金 $1,000、銀行 $50,000", d.has(ns, "$1,000", True) and d.has(ns, "$50,000", True))
    d.check("有「編輯排序」按鈕與「新增帳戶」按鈕", d.has(ns, "編輯排序", True) and d.has(ns, "＋ 新增帳戶", True))


@case(D, "帳戶分頁編輯排序")
def t_acc_reorder():
    d.fresh(empty_seed(extra_accounts=3).json())
    acc_tab()
    ns = d.shot("帳戶分頁（一般模式）")
    d.check("一般模式沒有拖曳把手", not d.has(ns, "按住拖曳排序", True))
    d.tap_text("編輯排序", exact=True)
    d.time.sleep(1)
    ns = d.shot("編輯排序模式")
    handles = d.find(ns, "按住拖曳排序", True)
    d.check("按下「編輯排序」後每個帳戶右邊出現拖曳把手（6 個）", len(handles) == 6, len(handles))
    d.check("按鈕變成「完成」", d.has(ns, "完成", True))
    bank, a1 = d.first(ns, "測試銀行", True), d.first(ns, "帳戶01", True)
    d.check("一開始測試銀行在帳戶01上面", bool(bank and a1) and bank.cy < a1.cy)
    # 編輯模式點帳戶不會進明細
    if a1:
        d.tap(a1)
        d.time.sleep(1)
        d.check("編輯排序時點帳戶不會跳進明細", not d.has(d.nodes(), "更新餘額", True))
    # 把「測試銀行」的把手拖到帳戶02的位置
    ns = d.nodes()
    bank, a2 = d.first(ns, "測試銀行", True), d.first(ns, "帳戶02", True)
    hs = [h for h in d.find(ns, "按住拖曳排序", True) if bank and abs(h.cy - bank.cy) < 80]
    if hs and a2:
        h = hs[0]
        d.swipe(h.cx, h.cy, h.cx, a2.cy + 40, 1200)
        d.time.sleep(1)
    ns = d.shot("拖曳之後")
    bank, a1 = d.first(ns, "測試銀行", True), d.first(ns, "帳戶01", True)
    d.check("拖曳後測試銀行排到帳戶01下面", bool(bank and a1) and bank.cy > a1.cy, (bank and bank.cy, a1 and a1.cy))
    d.tap_text("完成", exact=True)
    d.time.sleep(1)
    ns = d.shot("按完成之後")
    d.check("按「完成」後把手消失、按鈕回到「編輯排序」", not d.has(ns, "按住拖曳排序", True) and d.has(ns, "編輯排序", True))
    f.tab("統計")
    d.time.sleep(1)
    acc_tab()
    ns = d.shot("切到統計再回來")
    bank, a1 = d.first(ns, "測試銀行", True), d.first(ns, "帳戶01", True)
    d.check("排序有保留（切分頁再回來，測試銀行仍在帳戶01下面）", bool(bank and a1) and bank.cy > a1.cy)


@case(D, "點帳戶進入明細再返回帳戶分頁")
def t_acc_open_back():
    d.fresh(empty_seed().json())
    open_account("測試銀行")
    ns = d.shot("測試銀行的明細")
    d.check("顯示餘額 $50,000 與「編輯」「更新餘額」", d.has(ns, "$50,000") and d.has(ns, "編輯", True) and d.has(ns, "更新餘額", True))
    d.tap_back()
    d.wait_text("總資產", timeout=10)
    ns = d.shot("返回之後")
    d.check("返回後停在帳戶分頁（有總資產與編輯排序）", d.has(ns, "總資產", True) and d.has(ns, "編輯排序", True))


# ───────────────────────── 明細與日曆 ─────────────────────────
@case(D, "明細與日曆用切換鈕切換")
def t_home_cal_toggle():
    d.fresh(f.base_seed())
    ns = d.shot("明細（列表）")
    d.check("底部分頁沒有「日曆」", not [n for n in ns if n.text == "日曆" and n.cy > 2000])
    d.check("明細頁有「切換成日曆」按鈕與「本月支出」", d.has(ns, "切換成日曆", True) and d.has(ns, "本月支出", True))
    d.tap(d.first(ns, "切換成日曆", True))
    d.wait_text("切換成明細列表", timeout=10)
    ns = d.shot("日曆")
    d.check("切成日曆：標題「日曆」、本月收入／本月支出", d.has(ns, "日曆", True) and d.has(ns, "本月收入") and d.has(ns, "本月支出"))
    f._tap_tab("統計")
    d.time.sleep(1)
    f._tap_tab("明細")
    d.time.sleep(1.2)
    ns = d.shot("統計→再回明細分頁")
    d.check("切到別的分頁再回來，仍是日曆", d.has(ns, "切換成明細列表", True))
    d.tap(d.first(ns, "切換成明細列表", True))
    d.wait_text("切換成日曆", timeout=10)
    ns = d.shot("切回明細")
    d.check("切回明細列表：有「本月支出」大字", d.has(ns, "本月支出", True) and d.has(ns, "切換成日曆", True))


# ───────────────────────── 更新餘額 ─────────────────────────
@case(D, "更新餘額補平差額且不算收支")
def t_adjust_balance():
    d.fresh(empty_seed().json())
    open_account("測試銀行")
    d.tap_text("更新餘額", exact=True)
    d.wait_text("實際的餘額", exact=False, timeout=10)
    ns = d.shot("更新餘額對話框")
    d.check("顯示目前記錄的餘額 $50,000", d.has(ns, "目前記錄的餘額：$50,000"))
    fill_dialog(["52000"])
    ns = d.shot("輸入 52000")
    d.check("預覽：會補記 +$2,000，不算收入", d.has(ns, "會補記 +$2,000，不算收入"))
    d.tap_text("更新", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("更新之後")
    d.check("帳戶餘額變成 $52,000", d.has(ns, "$52,000", True))
    d.check("明細出現「餘額調整」+$2,000", d.has(ns, "餘額調整", True) and d.has(ns, "+$2,000", True))
    d.check("出現提示「已更新餘額…」", d.has(ns, "已更新餘額"))
    exp, inc = home_values()
    d.shot("明細分頁的本月收支")
    d.check("本月支出仍是 $0、收入仍是 $0（餘額調整不算收支）", exp == "$0" and inc == "$0", (exp, inc))


@case(D, "更新餘額：數字相同與信用卡欠款")
def t_adjust_edge():
    d.fresh(empty_seed().json())
    open_account("測試信用卡")
    d.tap_text("更新餘額", exact=True)
    d.wait_text("實際的餘額", exact=False, timeout=10)
    ns = d.shot("信用卡的更新餘額")
    d.check("信用卡有「欠款請輸入負數」的提示", d.has(ns, "欠款請輸入負數"))
    fill_dialog(["0"])
    ns = d.shot("輸入和目前一樣的 0")
    d.check("數字相同：提示「不用調整」，更新鈕不能按", d.has(ns, "和記錄的一樣，不用調整") and disabled(ns, "更新"))
    fill_dialog(["-3000"])
    ns = d.shot("輸入 -3000")
    d.check("預覽：會補記 −$3,000，不算支出", d.has(ns, "會補記 −$3,000，不算支出"))
    d.tap_text("更新", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("更新之後")
    d.check("信用卡餘額變成 -$3,000", d.has(ns, "-$3,000", True))


@case(D, "更新餘額可以復原")
def t_adjust_undo():
    d.fresh(empty_seed().json())
    open_account("測試銀行")
    d.tap_text("更新餘額", exact=True)
    d.wait_text("實際的餘額", exact=False, timeout=10)
    fill_dialog(["52000"])
    d.tap_text("更新", exact=True)
    d.tap_text("復原", exact=True, timeout=6)   # 提示只停 4 秒，不能先截圖再點
    d.time.sleep(1.5)
    ns = d.shot("按復原之後")
    d.check("餘額回到 $50,000", d.has(ns, "$50,000", True) and not d.has(ns, "$52,000", True))
    d.check("「餘額調整」那一筆消失", not d.has(ns, "餘額調整", True))


# ───────────────────────── 投資帳戶與持股 ─────────────────────────
def invest_seed(with_price=None):
    """測試證券已經買了 0050：100 股、單價 100、手續費 20（現金轉進去 10,000，手續費 20 另外扣）"""
    s = empty_seed(invest=True)
    t = s.transfer(0, 10000, S.CASH, S.INVEST, fee=20, note="買進 0050 100 @ 100.00")
    s.trade(0, "0050", True, 100, 100, 20, "元大50", txn=t)
    s.price("0050", 0, with_price if with_price else 100)
    return s


@case(D, "投資帳戶顯示持股區")
def t_invest_empty():
    d.fresh(empty_seed(invest=True).json())
    acc_tab()
    ns = d.shot("帳戶分頁（有投資帳戶）")
    d.check("有「投資」分組與「測試證券」", d.has(ns, "投資", True) and d.has(ns, "測試證券", True))
    open_account("測試證券")
    ns = d.shot("測試證券的明細")
    d.check("顯示持股市值、成本、未實現損益、已實現損益", all(d.has(ns, t, True) for t in ("持股市值", "成本", "未實現損益", "已實現損益")))
    d.check("有「買進」「賣出」按鈕與還沒有持股的提示", d.has(ns, "買進", True) and d.has(ns, "賣出", True) and d.has(ns, "還沒有持股"))
    d.check("沒有持股時不顯示「同步市值到餘額」「抓最新價格」", not d.has(ns, "同步市值到餘額") and not d.has(ns, "抓最新價格"))


@case(D, "買進一檔：持股、成本、損益與轉帳")
def t_invest_buy():
    d.fresh(empty_seed(invest=True).json())
    open_account("測試證券")
    d.tap_text("買進", exact=True)
    d.wait_text("買進", timeout=10)
    d.time.sleep(1)
    d.shot("買進對話框")
    fill_dialog(["0050", "ETF50", "100", "100", "20"])
    ns = d.shot("填好之後")
    d.check("預覽：金額 $10,000＋手續費 $20，會同時記一筆轉帳", d.has(ns, "金額 $10,000＋手續費 $20"))
    d.tap_text("記錄", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("買進之後")
    d.check("持股列出「ETF50 0050」", d.has(ns, "ETF50 0050", True))
    d.check("持股市值 $10,000、成本 $10,020", d.has(ns, "$10,000", True) and d.has(ns, "$10,020", True))
    d.check("數量 100 股", d.has(ns, "100 股"))
    d.check("未實現損益 −$20（因為手續費）", d.has(ns, "−$20"), [n.text for n in ns if "$" in n.text][:12])
    d.check("買賣記錄出現這一筆", d.has(ns, "買賣記錄", True))
    acc_tab()
    ns = d.shot("帳戶分頁")
    d.check("現金 -$9,020（轉出 10,000＋手續費 20）、測試證券 $10,000", d.has(ns, "-$9,020", True) and d.has(ns, "$10,000", True))
    exp, inc = home_values()
    d.check("本月支出只有手續費 $20，收入 $0", exp == "$20" and inc == "$0", (exp, inc))


@case(D, "手動更新現價並重算損益")
def t_invest_price():
    d.fresh(invest_seed().json())
    open_account("測試證券")
    ns = d.shot("持股")
    d.tap(d.first(ns, "元大50 0050", True))
    d.wait_text("更新現價", timeout=10)
    fill_dialog(["120"])
    d.shot("輸入現價 120")
    d.tap_text("更新", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("更新之後")
    d.check("持股市值 $12,000", d.has(ns, "$12,000", True))
    d.check("未實現損益 +$1,980（+19.8%）", d.has(ns, "+$1,980（+19.8%）"), [n.text for n in ns if "%" in n.text])


@case(D, "賣出一部分：已實現損益與資金轉回")
def t_invest_sell():
    d.fresh(invest_seed().json())
    open_account("測試證券")
    d.tap_text("賣出", exact=True)
    d.wait_text("賣出", timeout=10)
    d.time.sleep(1)
    fill_dialog([None, None, "200", "120", None])
    ns = d.shot("賣出 200 股（超過持有）")
    d.check("賣出超過持有的股數：提示並不能記錄", d.has(ns, "賣出的股數比持有的多") and disabled(ns, "記錄"))
    fill_dialog([None, None, "40", "120", "10"])
    ns = d.shot("賣出 40 股")
    d.check("預覽：金額 $4,800＋手續費 $10", d.has(ns, "金額 $4,800＋手續費 $10"))
    d.tap_text("記錄", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("賣出之後")
    d.check("剩下 60 股", d.has(ns, "60 股"))
    d.check("已實現損益 +$782（賣出 4,800−手續費 10−成本 4,008）", d.has(ns, "+$782"), [n.text for n in ns if "$" in n.text][:14])
    acc_tab()
    ns = d.shot("帳戶分頁")
    d.check("現金 -$4,230（轉出 10,020 後收回 4,790）", d.has(ns, "-$4,230", True))


@case(D, "同步市值到餘額不算收入")
def t_invest_sync():
    d.fresh(invest_seed(with_price=120).json())
    open_account("測試證券")
    d.tap_text("同步市值到餘額", exact=True)
    d.wait_text("同步市值到餘額", timeout=10)
    ns = d.shot("同步市值對話框")
    d.check("說明持股市值 $12,000、目前餘額 $10,000", d.has(ns, "持股市值 $12,000，帳戶目前餘額 $10,000"))
    d.tap_text("更新", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("同步之後")
    d.scroll_down(1)
    ns = d.shot("往下滑看明細")
    d.check("帳戶餘額變成 $12,000，明細出現「餘額調整」", d.has(ns, "$12,000", True) and d.has(ns, "餘額調整", True))
    exp, inc = home_values()
    d.check("本月收入仍是 $0（市值變動不算收入）", inc == "$0", (exp, inc))


@case(D, "刪除買賣記錄連動轉帳一起刪")
def t_invest_delete_trade():
    d.fresh(invest_seed().json())
    open_account("測試證券")
    ns = d.shot("買賣記錄")
    rows = sorted([n for n in ns if n.text == "元大50 0050"], key=lambda n: n.cy)
    d.check("持股與買賣記錄各有一列", len(rows) == 2, len(rows))
    f.swipe_row_left(rows[-1])
    d.tap_text("刪除", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("刪除之後")
    d.check("持股清空（還沒有持股）", d.has(ns, "還沒有持股"))
    d.check("出現「復原」", d.has(ns, "復原", True))
    acc_tab()
    ns = d.shot("帳戶分頁")
    d.check("現金回到 $1,000（連動的轉帳一起刪了）", d.has(ns, "$1,000", True) and not d.has(ns, "-$9,020", True))


@case(D, "上網抓價預設關閉：先說明再詢問")
def t_invest_fetch_ask():
    d.fresh(invest_seed().json())
    open_account("測試證券")
    d.tap_text("抓最新價格", exact=True)
    d.wait_text("上網抓最新價格？", timeout=10)
    ns = d.shot("詢問是否上網")
    d.check("說明只送出代號、不傳記帳資料", d.has(ns, "不會傳送任何記帳資料"))
    d.tap_text("不要", exact=True)
    d.time.sleep(1)
    ns = d.shot("按不要之後")
    d.check("對話框關閉，沒有開啟抓價（仍可看到「抓最新價格」，沒有關閉抓價的提示）",
            not d.has(ns, "上網抓最新價格？", True) and d.has(ns, "抓最新價格", True) and not d.has(ns, "點這裡關閉網路抓價"))


@case(D, "買進時要選市場並顯示在持股上")
def t_invest_market():
    d.fresh(empty_seed(invest=True).json())
    open_account("測試證券")
    d.tap_text("買進", exact=True)
    d.wait_text("買進", timeout=10)
    d.time.sleep(1)
    ns = d.shot("買進對話框")
    d.check("有市場選項：台股、美股、日股、韓股（一排可以橫向滑）", all(d.has(ns, m, True) for m in ("台股", "美股", "日股", "韓股")))
    row = d.first(ns, "台股", True)
    if row:
        d.swipe(800, row.cy, 250, row.cy, 500)   # 在那一排裡往左撥，看後面的選項
    ns2 = d.shot("市場選項往右滑")
    d.check("往右滑可以看到港股、加密貨幣", d.has(ns2, "港股", True) and d.has(ns2, "加密貨幣", True))
    row = d.first(ns2, "港股", True)
    if row:
        d.swipe(250, row.cy, 800, row.cy, 500)   # 撥回來，後面要選日股
    d.check("說明名稱只是方便自己辨認", d.has(ns, "名稱（選填，自己看得懂就好"))
    d.tap_text("日股", exact=True)
    d.time.sleep(0.8)
    ns = d.shot("選了日股")
    d.check("代號提示跟著市場變：日股填 7203", d.has(ns, "7203"))
    fill_dialog(["7203", "TOYOTA", "10", "3000", None])
    d.tap_text("記錄", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("買進之後")
    d.check("持股列出「TOYOTA 7203」並標示「日股」", d.has(ns, "TOYOTA 7203", True) and d.has(ns, "日股", True))


@case(D, "抓最新價格要列出沒抓到的是哪一檔")
def t_invest_fetch_fail():
    s = empty_seed(invest=True)
    t = s.transfer(0, 5000, S.CASH, S.INVEST)
    s.trade(0, "ZZZZ9999", True, 10, 500, 0, "FAKE", txn=t, market="US")
    d.fresh(s.json())
    open_account("測試證券")
    d.tap_text("抓最新價格", exact=True)
    d.wait_text("上網抓最新價格？", timeout=10)
    d.tap_text("開啟並抓價", exact=True)
    d.wait_text("抓價結果", timeout=60)
    ns = d.shot("抓價結果")
    d.check("結果裡列出沒抓到的代號 ZZZZ9999", d.has(ns, "ZZZZ9999"))
    d.check("有「沒抓到」的分類與處理建議（確認市場、代號，或手動輸入）", d.has(ns, "沒抓到") and d.has(ns, "手動輸入"))
    d.tap_text("關閉", exact=True)
    d.time.sleep(1)
    ns = d.shot("關閉之後")
    d.check("持股那一列標示「抓不到價格」", d.has(ns, "抓不到價格"))


@case(D, "抓最新價格會更新持股現價")
def t_invest_fetch_real():
    # 持股是今天記的、成交價故意設成 1 元；抓到的真實價格（0050 約一百多元）一定不是 1。
    # 這個用例會真的連 Yahoo Finance，雲端沒有網路時會失敗（失敗訊息會寫是沒抓到）
    s = empty_seed(invest=True)
    s.trade(0, "0050", True, 10, 1, 0, "ETF", market="TW")
    d.fresh(s.json())
    open_account("測試證券")
    ns = d.shot("抓價前")
    d.check("抓價前現價是成交價 1.00", d.has(ns, "現價 1.00"))
    d.tap_text("抓最新價格", exact=True)
    d.wait_text("上網抓最新價格？", timeout=10)
    d.tap_text("開啟並抓價", exact=True)
    d.wait_text("抓價結果", timeout=60)
    ns = d.shot("抓價結果")
    d.check("結果顯示已更新 1 檔（沒有的話代表網路不通或抓不到）", d.has(ns, "已更新（1 檔）"), [n.text for n in ns if "檔" in n.text])
    d.tap_text("關閉", exact=True)
    d.time.sleep(1.5)
    ns = d.shot("抓價後")
    import re
    nums = [float(m.group(1).replace(",", "")) for n in ns for m in [re.search(r"現價 ([\d,\.]+)", n.text)] if m]
    d.check("持股現價已經不是 1.00（抓到的價格有套用）", bool(nums) and nums[0] > 5, nums)
    d.check("沒有出現「抓不到價格」", not d.has(ns, "抓不到價格"))


# ───────────────────────── 帳戶分頁：收折、隱藏帳戶、貸款 ─────────────────────────
@case(D, "帳戶分頁的分組可以收折")
def t_acc_collapse():
    d.fresh(empty_seed(extra_accounts=2).json())
    acc_tab()
    ns = d.shot("全部展開")
    d.check("展開時看得到銀行組的帳戶", d.has(ns, "測試銀行", True) and d.has(ns, "帳戶01", True))
    head = d.first(ns, "銀行", True)
    d.check("有「銀行」分組標題", head is not None)
    if head:
        d.tap(head)
        d.time.sleep(1)
        ns = d.shot("收折銀行組")
        d.check("收折後銀行組的帳戶不顯示，標題與其他組還在", not d.has(ns, "測試銀行", True) and not d.has(ns, "帳戶01", True) and d.has(ns, "銀行", True) and d.has(ns, "信用卡", True))
        d.check("總資產不受收折影響", d.has(ns, "$51,000", True))
        d.tap(d.first(ns, "銀行", True))
        d.time.sleep(1)
        ns = d.shot("再展開")
        d.check("再點一次就展開", d.has(ns, "測試銀行", True) and d.has(ns, "帳戶01", True))


@case(D, "隱藏的帳戶不在帳戶分頁顯示但可以打開")
def t_acc_hidden():
    d.fresh(empty_seed(hidden_account=True).json())
    acc_tab()
    ns = d.shot("帳戶分頁")
    d.check("隱藏的帳戶不顯示", not d.has(ns, "隱藏帳戶"))
    d.check("總資產不含隱藏帳戶（$51,000）", d.has(ns, "$51,000", True))
    d.check("有「顯示已隱藏的帳戶（1）」", d.has(ns, "顯示已隱藏的帳戶（1）", True))
    sw, first = d.first(ns, "顯示已隱藏的帳戶（1）", True), d.first(ns, "現金", True)
    d.check("開關在最上方（在第一個分組「現金」之前，比較好按）", bool(sw and first) and sw.cy < first.cy, (sw and sw.cy, first and first.cy))
    d.tap_text("顯示已隱藏的帳戶（1）", exact=True)
    d.time.sleep(1)
    ns = d.shot("打開隱藏的帳戶")
    d.check("打開後看得到「隱藏帳戶（已隱藏）」", d.has(ns, "隱藏帳戶（已隱藏）", True))
    d.check("按鈕變成「收起已隱藏的帳戶」，總資產仍是 $51,000", d.has(ns, "收起已隱藏的帳戶", True) and d.has(ns, "$51,000", True))
    d.tap_text("收起已隱藏的帳戶", exact=True)
    d.time.sleep(1)
    ns = d.shot("再收起")
    d.check("再收起後又看不到", not d.has(ns, "隱藏帳戶"))


@case(D, "貸款帳戶：和信用卡同屬負債，還款是轉帳不算支出")
def t_acc_loan():
    s = empty_seed(loan=True)
    s.transfer(0, 11291, S.BANK, S.LOAN, note="信貸第1期")
    d.fresh(s.json())
    acc_tab()
    ns = d.shot("帳戶分頁")
    d.check("有「貸款」分組與「信貸」帳戶", d.has(ns, "貸款", True) and d.has(ns, "信貸", True))
    d.check("信貸餘額 -$188,709（初始 -200,000 加上還款 11,291）", d.has(ns, "-$188,709", True), [n.text for n in ns if "$" in n.text][:10])
    d.check("總資產 -$149,000（把欠款算進去）", d.has(ns, "-$149,000", True))
    exp, inc = home_values()
    d.check("還款是轉帳：本月支出 $0", exp == "$0", (exp, inc))
    open_account("信貸")
    d.tap_text("更新餘額", exact=True)
    d.wait_text("實際的餘額", exact=False, timeout=10)
    ns = d.shot("貸款的更新餘額")
    d.check("貸款的更新餘額也提示欠款請輸入負數", d.has(ns, "欠款請輸入負數"))


# ───────────────────────── 記一筆：選帳戶分類與常用帳戶 ─────────────────────────
@case(D, "記一筆選帳戶：常用帳戶在最上面，其餘依類型分組")
def t_pick_account_groups():
    d.fresh(f.base_seed())
    f.open_add()
    d.tap(d.wait(lambda n: n.text == "現金", 10, "帳戶按鈕"))
    d.wait_text("選擇帳戶", timeout=10)
    ns = d.shot("選擇帳戶")
    d.check("有「常用帳戶」分組", d.has(ns, "常用帳戶", True))
    fav = d.first(ns, "常用帳戶", True)
    bank_h, card_h = d.first(ns, "銀行", True), d.first(ns, "信用卡", True)
    d.check("有「銀行」「信用卡」分組標題（依類型分組）", bank_h is not None and card_h is not None)
    if fav and bank_h and card_h:
        d.check("由上到下：常用帳戶 → 銀行 → 信用卡", fav.cy < bank_h.cy < card_h.cy, (fav.cy, bank_h.cy, card_h.cy))
    names = [n for n in ns if n.text == "測試銀行"]
    d.check("常用的測試銀行會在常用帳戶與原本的分組各出現一次", len(names) == 2, len(names))
    d.check("沒用過的信用卡不會出現在常用帳戶裡", len([n for n in ns if n.text == "測試信用卡"]) == 1)


@case(D, "帳戶分組的收折狀態重開 App 後保留")
def t_acc_collapse_persist():
    d.fresh(empty_seed(extra_accounts=2).json())
    acc_tab()
    d.tap(d.first(d.nodes(), "銀行", True))
    d.time.sleep(1)
    ns = d.shot("收折銀行組")
    d.check("收折後看不到銀行組的帳戶", not d.has(ns, "測試銀行", True) and not d.has(ns, "帳戶01", True))
    d.restart()
    acc_tab()
    ns = d.shot("重開 App 後")
    d.check("重開後銀行組仍然是收折的（帳戶沒顯示，標題還在）", not d.has(ns, "測試銀行", True) and not d.has(ns, "帳戶01", True) and d.has(ns, "銀行", True))
    d.tap(d.first(ns, "銀行", True))
    d.time.sleep(1)
    d.restart()
    acc_tab()
    ns = d.shot("展開後再重開")
    d.check("展開之後再重開，仍然是展開的", d.has(ns, "測試銀行", True) and d.has(ns, "帳戶01", True))


@case(D, "常用帳戶：標了星號的固定在最上面，其餘自動補")
def t_favorite_first():
    d.fresh(S.base(f.today(), fav_card=True).json())    # 測試信用卡從沒用過，但標了星號
    f.open_add()
    d.tap(d.wait(lambda n: n.text == "現金", 10, "帳戶按鈕"))
    d.wait_text("選擇帳戶", timeout=10)
    ns = d.shot("選擇帳戶")
    fav = d.first(ns, "常用帳戶", True)
    star = sorted([n for n in ns if n.desc == "取消常用帳戶"], key=lambda n: n.cy)      # 實心星號 = 已設為常用
    d.check("標了星號的測試信用卡（沒用過）也出現在常用帳戶，右邊是實心星號", bool(star))
    cash_lines = [n for n in ns if n.text == "現金"]
    if fav and star and cash_lines:
        d.check("星號帳戶排在最前面：常用帳戶標題 → 實心星號的測試信用卡 → 其他自動補的帳戶（現金…）",
                fav.cy < star[0].cy < min(n.cy for n in cash_lines), (fav.cy, star[0].cy, min(n.cy for n in cash_lines)))
    d.check("自動補的常用帳戶還在（用過的現金、測試銀行）", len(cash_lines) >= 2 and len([n for n in ns if n.text == "測試銀行"]) >= 2)


@case(D, "帳戶編輯可以設為常用帳戶")
def t_favorite_switch():
    d.fresh(f.base_seed())
    open_account("測試銀行")
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    ns = d.shot("編輯帳戶")
    label = d.first(ns, "設為常用帳戶（記一筆選帳戶時固定放最上面）", True)
    d.check("編輯帳戶有「設為常用帳戶」開關", label is not None)
    if label:
        sw = [n for n in ns if n.checkable and abs(n.cy - label.cy) < 100]   # 開關在畫面結構裡是 checkable 的 View
        d.check("找得到那個開關", bool(sw))
        if sw:
            d.tap(sw[0])
            d.time.sleep(0.8)
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    ns = d.shot("儲存之後")
    d.tap_back()
    to_main()
    f.tab("明細")
    f.open_add()
    d.tap(d.wait(lambda n: n.text == "現金", 10, "帳戶按鈕"))
    d.wait_text("選擇帳戶", timeout=10)
    ns = d.shot("選擇帳戶")
    d.check("測試銀行標了星號：選擇帳戶時出現實心星號", d.has(ns, "取消常用帳戶", True))


def star_near(ns, y, desc=None):
    """某個高度附近的星號按鈕（desc 可指定「設為常用帳戶」或「取消常用帳戶」）"""
    c = [n for n in ns if n.desc in ("設為常用帳戶", "取消常用帳戶") and abs(n.cy - y) < 90 and (desc is None or n.desc == desc)]
    return min(c, key=lambda n: abs(n.cy - y)) if c else None


@case(D, "帳戶分頁：點星號直接設為常用帳戶")
def t_star_in_account_tab():
    d.fresh(f.base_seed())
    acc_tab()
    ns = d.shot("帳戶分頁")
    bank = d.first(ns, "測試銀行", True)
    d.check("找得到測試銀行", bank is not None)
    stars = [n for n in ns if n.desc in ("設為常用帳戶", "取消常用帳戶")]
    d.check("每個帳戶列都有星號按鈕", len(stars) >= 3, len(stars))
    st = star_near(ns, bank.cy) if bank else None
    d.check("測試銀行的星號一開始是空心（設為常用帳戶）", st is not None and st.desc == "設為常用帳戶", st.desc if st else None)
    bal = [n for n in ns if bank and (n.text.startswith("$") or n.text.startswith("-$")) and abs(n.cy - bank.cy) < 70 and n.cx > bank.cx]
    d.check("星號在餘額的右邊（餘額在前、星號在後）", bool(bal) and st is not None and st.cx > bal[0].cx, (st.cx if st else None, bal[0].cx if bal else None))
    if st:
        d.tap(st)
        d.time.sleep(1)
        ns = d.shot("點星號之後")
        bank = d.first(ns, "測試銀行", True)
        st = star_near(ns, bank.cy) if bank else None
        d.check("點了之後變成實心（取消常用帳戶）", st is not None and st.desc == "取消常用帳戶", st.desc if st else None)
    f.tab("明細")
    f.open_add()
    d.tap(d.wait(lambda n: n.text == "現金", 10, "帳戶按鈕"))
    d.wait_text("選擇帳戶", timeout=10)
    ns = d.shot("記一筆選帳戶")
    fav = d.first(ns, "常用帳戶", True)
    solid = sorted([n for n in ns if n.desc == "取消常用帳戶"], key=lambda n: n.cy)
    d.check("記一筆選帳戶：常用帳戶區有實心星號的測試銀行", bool(fav) and bool(solid) and solid[0].cy > fav.cy)


@case(D, "記一筆選帳戶：在清單裡直接點星號設為常用")
def t_star_in_picker():
    d.fresh(f.base_seed())
    f.open_add()
    d.tap(d.wait(lambda n: n.text == "現金", 10, "帳戶按鈕"))
    d.wait_text("選擇帳戶", timeout=10)
    ns = d.shot("選擇帳戶")
    before = len([n for n in ns if n.desc == "取消常用帳戶"])
    cards = sorted([n for n in ns if n.text == "測試信用卡"], key=lambda n: n.cy)
    d.check("找得到測試信用卡那一列", bool(cards))
    st = star_near(ns, cards[-1].cy, "設為常用帳戶") if cards else None
    d.check("測試信用卡的星號是空心", st is not None)
    if st:
        d.tap(st)
        d.time.sleep(1)
        ns = d.shot("點星號之後")
        after = len([n for n in ns if n.desc == "取消常用帳戶"])
        d.check("點了之後多出實心星號（常用帳戶區和類型分組各一個）", after >= before + 2, (before, after))
        d.check("點星號不會選走帳戶、對話框還在", d.has(ns, "選擇帳戶", True))
    d.tap_text("關閉", exact=True)
    d.time.sleep(0.8)
    acc_tab()
    ns = d.shot("帳戶分頁")
    row = d.first(ns, "測試信用卡", True)
    st = star_near(ns, row.cy) if row else None
    d.check("帳戶分頁的測試信用卡也是實心星號", st is not None and st.desc == "取消常用帳戶", st.desc if st else None)


# ───────────────────────── 記錄時間 ─────────────────────────
def wheel_hours(ns):
    """時間滾輪：小時那一列（左半邊）的數字，由上到下"""
    import re
    return sorted([n for n in ns if re.fullmatch(r"\d\d", n.text) and n.cx < 540 and 900 < n.cy < 1700], key=lambda n: n.cy)


def wheel_minutes(ns):
    import re
    return sorted([n for n in ns if re.fullmatch(r"\d\d", n.text) and n.cx >= 540 and 900 < n.cy < 1700], key=lambda n: n.cy)


def tap_wheel_to_type():
    """點一下滾輪上的小時（正中間那個）→ 直接變成鍵盤輸入，不用按任何切換按鈕"""
    hs = wheel_hours(d.nodes())
    d.tap(hs[len(hs) // 2])
    d.time.sleep(1)


def type_time(hh, mm):
    """鍵盤輸入的時間：一出現小時就已經選取，直接打；小時輸入滿 2 位數會自動跳到分鐘（也是選取狀態）。
    輸入太快會吃掉字，所以分兩段、中間等一下。畫面上同時只有一格是輸入框，不能用重試（重試會打到分鐘那格）"""
    d.type_text(hh)
    d.time.sleep(1.2)
    d.type_text(mm)
    d.time.sleep(1.0)


@case(D, "記一筆可以設定時間並在編輯時看得到")
def t_txn_time():
    import re
    d.fresh(empty_seed().json())
    f.open_add()
    ns = d.shot("記一筆")
    chip = next((n for n in ns if re.fullmatch(r"\d\d:\d\d", n.text)), None)
    d.check("日期旁邊有時間按鈕，預設是現在的時間（HH:mm）", chip is not None, [n.text for n in ns if ":" in n.text][:5])
    if chip:
        d.tap(chip)
        d.wait_text("選擇時間", timeout=10)
        d.shot("選擇時間對話框")
        # 預設是滾輪；點一下滾輪就直接變鍵盤輸入
        tap_wheel_to_type()
        type_time("09", "30")
        d.shot("輸入 09:30")
        d.tap_text("確定", exact=True)
        d.time.sleep(1)
    ns = d.shot("設定後")
    d.check("時間按鈕變成 09:30", d.has(ns, "09:30", True))
    f.keypad("100")
    f.save_edit()
    d.time.sleep(1)
    d.wait(lambda n: n.text == "-$100", 10, "剛記的這一筆")
    # 畫面上有兩個 -$100：上面「結餘」卡片，和下面的明細列；要點明細列（比較下面那個）
    row = max([n for n in d.nodes() if n.text == "-$100"], key=lambda n: n.cy)
    d.tap(row)
    d.wait_text("正在編輯", exact=False, timeout=15)
    ns = d.shot("編輯這一筆")
    d.check("編輯時時間按鈕顯示 09:30（有存下來）", d.has(ns, "09:30", True))
    d.tap(d.first(d.nodes(), "關閉"))
    d.time.sleep(1.2)
    d.check("返回列表後，明細列沒有出現 09:30", not d.has(d.nodes(), "09:30"))


@case(D, "舊記錄沒有時間：編輯時顯示未設定")
def t_txn_time_unset():
    s = empty_seed()
    s.expense(0, 85, S.C_FOOD, note="old")      # 沒有 time 欄位，就是舊資料
    d.fresh(s.json())
    d.wait(lambda n: n.text == "-$85", 10, "舊記錄")
    row = max([n for n in d.nodes() if n.text == "-$85"], key=lambda n: n.cy)   # 明細列，不是上面的結餘卡片
    d.tap(row)
    d.wait_text("正在編輯", exact=False, timeout=15)
    ns = d.shot("編輯舊記錄")
    d.check("時間按鈕顯示「未設定時間」", d.has(ns, "未設定時間", True))


@case(D, "帳戶明細每一筆都顯示做完之後的餘額")
def t_running_balance():
    # 測試銀行初始 $50,000。昨天（沒有時間）支出 300；今天 10:00 支出 100、11:40 收入 1,000、15:00 支出 200
    s = empty_seed()
    s.expense(-1, 300, S.C_FOOD, acc=S.BANK, note="y")
    s.expense(0, 100, S.C_FOOD, acc=S.BANK, note="a", time=600)
    s.income(0, 1000, S.C_SALARY, acc=S.BANK, note="b", time=700)
    s.expense(0, 200, S.C_FOOD, acc=S.BANK, note="c", time=900)
    d.fresh(s.json())
    open_account("測試銀行")
    ns = d.shot("測試銀行的明細")
    want = ["餘額 $50,400", "餘額 $50,600", "餘額 $49,600", "餘額 $49,700"]   # 由上到下（新到舊）
    nodes = [d.first(ns, w, True) for w in want]
    d.check("每一筆下面都有「餘額」：15:00 之後 $50,400、11:40 之後 $50,600、10:00 之後 $49,600、昨天之後 $49,700",
            all(nodes), [n.text for n in ns if n.text.startswith("餘額")])
    if all(nodes):
        ys = [n.cy for n in nodes]
        d.check("同一天依時間排：15:00 在最上面，再來 11:40、10:00，最後是昨天", ys == sorted(ys), ys)
    d.check("最新一筆的餘額等於帳戶目前的餘額（標題 $50,400）", d.has(ns, "$50,400", True))
    amt = d.first(ns, "-$200", True)
    bal = d.first(ns, "餘額 $50,400", True)
    d.check("餘額就在金額的正下方（一大一小，列不會變高）", bool(amt and bal) and bal.cy > amt.cy and abs(bal.x2 - amt.x2) < 60 and bal.cy - amt.cy < 80,
            (amt and (amt.cx, amt.cy), bal and (bal.cx, bal.cy)))


@case(D, "選時間：預設滾輪，可以滑動，點一下滾輪就變鍵盤輸入")
def t_time_wheel():
    import re
    d.fresh(empty_seed().json())
    f.open_add()
    ns = d.nodes()
    chip = next(n for n in ns if re.fullmatch(r"\d\d:\d\d", n.text))
    h0 = int(chip.text[:2])
    d.tap(chip)
    d.wait_text("選擇時間", timeout=10)
    ns = d.shot("選擇時間（滾輪）")
    d.check("預設是滾輪（沒有輸入框），而且沒有「改用鍵盤輸入」按鈕", not d.edits() and not d.has(ns, "改用鍵盤輸入"))
    # 小時那一列的各個數字（左半邊、兩位數），中間那個是目前選的
    hours = wheel_hours(ns)
    d.check("看得到小時的滾輪（多個數字上下排列）", len(hours) >= 3, [n.text for n in hours])
    if len(hours) >= 3:
        mid = hours[len(hours) // 2]
        step = hours[1].cy - hours[0].cy
        # 手指由下往上滑 2 格 → 小時 +2；已經太晚（12 點以後）就反方向（-2）。慢慢滑，滑完會自動對齊
        up = h0 < 12
        if up:
            d.swipe(mid.cx, mid.cy + step, mid.cx, mid.cy - step, 1200)
        else:
            d.swipe(mid.cx, mid.cy - step, mid.cx, mid.cy + step, 1200)
        d.time.sleep(1.2)
        d.shot("滑動之後")
    d.tap_text("確定", exact=True)
    d.time.sleep(1)
    ns = d.shot("確定之後")
    chip2 = next((n for n in ns if re.fullmatch(r"\d\d:\d\d", n.text)), None)
    h1 = int(chip2.text[:2]) if chip2 else -1
    d.check("滑動滾輪後小時改變了（約 ±2）", chip2 is not None and 1 <= abs(h1 - h0) <= 3, (h0, h1))
    # 點一下滾輪 → 直接變鍵盤輸入，打字設成 09:30
    d.tap(chip2)
    d.wait_text("選擇時間", timeout=10)
    tap_wheel_to_type()
    ns = d.shot("點滾輪之後")
    d.check("點一下滾輪就變成鍵盤輸入（出現輸入框）", bool(d.edits()))
    d.check("沒有任何切換按鈕（改用滾輪／改用鍵盤輸入）", not d.has(ns, "改用滾輪") and not d.has(ns, "改用鍵盤輸入"))
    type_time("09", "30")
    d.tap_text("確定", exact=True)
    d.time.sleep(1)
    ns = d.shot("鍵盤輸入之後")
    d.check("用鍵盤輸入 09:30 後時間按鈕是 09:30", d.has(ns, "09:30", True))


@case(D, "選時間滾輪可以循環：23 之後接 00、59 之後接 00")
def t_time_wheel_loop():
    import re
    d.fresh(empty_seed().json())
    f.open_add()
    chip = next(n for n in d.nodes() if re.fullmatch(r"\d\d:\d\d", n.text))
    d.tap(chip)
    d.wait_text("選擇時間", timeout=10)
    tap_wheel_to_type()
    type_time("23", "59")
    d.tap_text("確定", exact=True)
    d.time.sleep(1)
    d.check("先把時間設成 23:59", d.has(d.nodes(), "23:59", True))
    d.tap(next(n for n in d.nodes() if n.text == "23:59"))
    d.wait_text("選擇時間", timeout=10)
    d.time.sleep(1)
    ns = d.shot("23:59 的滾輪")
    hs = [n.text for n in wheel_hours(ns)]
    ms = [n.text for n in wheel_minutes(ns)]
    d.check("小時滾輪：23 的下面接 00、01（循環）", hs == ["21", "22", "23", "00", "01"], hs)
    d.check("分鐘滾輪：59 的下面接 00、01（循環）", ms == ["57", "58", "59", "00", "01"], ms)


@case(D, "日曆選了日期，記一筆預設是那一天")
def t_add_uses_calendar_day():
    import datetime
    d.fresh(f.base_seed())
    f.tab("日曆")
    d.time.sleep(1.5)
    yday = f.today() - datetime.timedelta(days=1)
    ns = d.nodes()
    cells = [n for n in ns if n.text == str(yday.day) and 400 < n.cy < 1300]
    d.check("日曆裡找得到昨天的日期格", bool(cells))
    if cells:
        d.tap(cells[0])
        d.time.sleep(1.2)
    f.open_add()
    ns = d.shot("日曆選了昨天後按記一筆")
    want = f"昨天・{yday.month}/{yday.day}"
    d.check(f"記一筆的日期預設是日曆選的那天（{want}）", d.has(ns, want, True), [n.text for n in ns if "・" in n.text][:4])
    d.tap(d.first(d.nodes(), "關閉"))
    d.time.sleep(1)
    f.tab("明細")                      # 切回列表（不是日曆）
    f.open_add()
    ns = d.shot("列表模式按記一筆")
    d.check("在明細列表按記一筆，日期仍是今天", d.has(ns, "今天・", False), [n.text for n in ns if "・" in n.text][:4])


# ───────────────────────── 帳戶徽章：銀行／行動支付預設 ─────────────────────────
@case(D, "帳戶徽章可以直接選銀行或行動支付")
def t_badge_presets():
    d.fresh(f.base_seed())
    open_account("測試銀行")
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    d.tap_text("文字徽章", exact=True)
    d.time.sleep(0.8)
    ns = d.shot("切到文字徽章")
    d.check("有「常見銀行」與「常見行動支付」兩排預設可以選", d.has(ns, "常見銀行", True) and d.has(ns, "常見行動支付", True), [n.text for n in ns][:30])
    d.check("銀行那排有台新，行動支付那排有街口支付", d.has(ns, "台新", True) and d.has(ns, "街口支付", True))
    d.tap_text("台新", exact=True)
    d.time.sleep(0.8)
    ns = d.shot("選了台新")
    texts = [e.text for e in d.edits()]
    d.check("選了台新：徽章文字變成「台新」，帳戶名稱不變", "台新" in texts and "測試銀行" in texts, texts)
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    ns = d.shot("儲存之後")
    d.check("帳戶頁的圖示是「台新」徽章，名稱仍是測試銀行", d.has(ns, "台新", True) and d.has(ns, "測試銀行", True))
    # 再改成行動支付
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    d.tap_text("街口支付", exact=True)
    d.time.sleep(0.8)
    texts = [e.text for e in d.edits()]
    d.check("改選街口支付：徽章文字變成「街口」", "街口" in texts, texts)
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    ns = d.shot("改成街口之後")
    d.check("帳戶頁的圖示變成「街口」，不再是「台新」", d.has(ns, "街口", True) and not d.has(ns, "台新", True))


# ───────────────────────── 信用卡共用額度 ─────────────────────────
@case(D, "信用卡共用額度：兩張卡共用一份額度，已用金額加總")
def t_shared_limit():
    # 測試信用卡額度 100,000，花了 30,000；第二張卡沒有自己的額度，花了 20,000
    s = empty_seed(card2=True)
    s.expense(-1, 30000, S.C_SHOP, acc=S.CARD, note="a")
    s.expense(-1, 20000, S.C_SHOP, acc=S.CARD2, note="b")
    d.fresh(s.json())
    open_account("第二張卡")
    ns = d.shot("第二張卡（還沒共用）")
    d.check("還沒設定共用時，第二張卡沒有額度資訊", not d.has(ns, "額度 $"))
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    for _ in range(4):                 # 對話框內容比較長，往下滑到「共用額度」
        if d.has(d.nodes(), "可以多選", False):
            break
        d.swipe(540, 1400, 540, 800, 500)
        d.time.sleep(0.6)
    ns = d.shot("編輯第二張卡")
    d.check("信用卡編輯有「共用額度」選項，列出其他信用卡可以點選（可以多選）", d.has(ns, "共用額度", False) and d.has(ns, "可以多選", False) and d.has(ns, "測試信用卡", True),
            [n.text for n in ns if n.text][:40])
    d.tap_text("測試信用卡", exact=True)
    d.time.sleep(0.8)
    d.shot("選了測試信用卡")
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    ns = d.shot("儲存之後")
    d.check("第二張卡顯示共用的額度：已用 $50,000（兩張加總）・可用 $50,000・額度 $100,000",
            d.has(ns, "已用 $50,000・可用 $50,000・額度 $100,000", True), [n.text for n in ns if "已用" in n.text])
    d.check("有說明和哪些卡共用額度", d.has(ns, "共用額度", False) and d.has(ns, "測試信用卡", False))
    # 主卡（測試信用卡）也看得到同樣的共用資訊
    d.tap_back()
    to_main()
    open_account("測試信用卡")
    ns = d.shot("測試信用卡")
    d.check("主卡也顯示加總後的已用 $50,000、可用 $50,000", d.has(ns, "已用 $50,000・可用 $50,000・額度 $100,000", True), [n.text for n in ns if "已用" in n.text])
    # 帳戶分頁：第二張卡顯示共用後的可用額度
    d.tap_back()
    to_main()
    acc_tab()
    ns = d.shot("帳戶分頁")
    d.check("帳戶分頁兩張卡都顯示「可用 $50,000」", len([n for n in ns if n.text == "可用 $50,000"]) == 2, [n.text for n in ns if n.text.startswith("可用")])



def scroll_to(text, tries=6):
    """帳戶編輯對話框比較長，往下滑到某段文字出現"""
    for _ in range(tries):
        if d.has(d.nodes(), text, False):
            return True
        d.swipe(540, 1400, 540, 800, 500)
        d.time.sleep(0.6)
    return d.has(d.nodes(), text, False)


@case(D, "信用卡共用額度可以多選：一次選好所有一起共用的卡")
def t_shared_limit_multi():
    # 主卡（測試信用卡）額度 100,000，花 30,000；第二張卡花 20,000、第三張卡花 10,000，這兩張沒有自己的額度
    s = empty_seed(card2=True, card3=True)
    s.expense(-1, 30000, S.C_SHOP, acc=S.CARD, note="a")
    s.expense(-1, 20000, S.C_SHOP, acc=S.CARD2, note="b")
    s.expense(-1, 10000, S.C_SHOP, acc=S.CARD3, note="c")
    d.fresh(s.json())
    open_account("測試信用卡")
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    found = scroll_to("可以多選")
    ns = d.shot("主卡的編輯畫面")
    d.check("主卡的編輯畫面有「和這張卡共用額度的信用卡（可以多選）」", found)
    d.check("清單同時列出第二張卡和第三張卡", d.has(ns, "第二張卡", True) and d.has(ns, "第三張卡", True), [n.text for n in ns if n.text][:40])
    d.tap_text("第二張卡", exact=True)
    d.tap_text("第三張卡", exact=True)
    d.shot("兩張都選了")
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    ns = d.shot("儲存之後")
    d.check("主卡顯示三張卡加總：已用 $60,000・可用 $40,000・額度 $100,000",
            d.has(ns, "已用 $60,000・可用 $40,000・額度 $100,000", True), [n.text for n in ns if "已用" in n.text])
    d.tap_back()
    to_main()
    acc_tab()
    ns = d.shot("帳戶分頁")
    d.check("帳戶分頁三張卡都顯示「可用 $40,000」", len([n for n in ns if n.text == "可用 $40,000"]) == 3, [n.text for n in ns if n.text.startswith("可用")])
    # 再編輯一次，取消第三張卡
    open_account("測試信用卡")
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    scroll_to("可以多選")
    ns = d.shot("再次編輯主卡")
    d.check("已選的卡會是選取狀態，仍然列出第二張卡和第三張卡", d.has(ns, "第二張卡", True) and d.has(ns, "第三張卡", True))
    d.tap_text("第三張卡", exact=True)
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    ns = d.shot("取消第三張卡之後")
    d.check("只剩第二張卡共用：已用 $50,000・可用 $50,000・額度 $100,000",
            d.has(ns, "已用 $50,000・可用 $50,000・額度 $100,000", True), [n.text for n in ns if "已用" in n.text])
    d.tap_back()
    to_main()
    open_account("第三張卡")
    ns = d.shot("第三張卡")
    d.check("第三張卡不再共用：沒有額度資訊（它自己沒設額度）", not d.has(ns, "額度 $"), [n.text for n in ns if "額度" in n.text])


def ab_seed(**kw):
    """測試信用卡（額度 100,000、花 30,000）和第二張卡（花 20,000）已經共用額度"""
    s = empty_seed(card2=True, card2_shared=True, **kw)
    s.expense(-1, 30000, S.C_SHOP, acc=S.CARD, note="a")
    s.expense(-1, 20000, S.C_SHOP, acc=S.CARD2, note="b")
    return s


def chip_in_row(ns, label, anchor):
    """和 anchor 同一排的 label（例如對話框裡的帳戶類型按鈕）"""
    a = d.first(ns, anchor, True)
    c = [n for n in ns if n.text == label and a is not None and abs(n.cy - a.cy) < 25]
    return c[0] if c else None


@case(D, "新增信用卡時可以選已經共用的組")
def t_new_card_join_group():
    d.fresh(ab_seed().json())
    acc_tab()
    d.tap_text("新增帳戶", exact=False)
    d.wait_text("新增帳戶", timeout=10)
    d.fill(d.edits()[0], "NewC")
    d.hide_ime()
    ns = d.nodes()
    t = chip_in_row(ns, "信用卡", "電子票證")
    d.check("找得到帳戶類型的「信用卡」", t is not None)
    if t:
        d.tap(t)
        d.time.sleep(0.8)
    scroll_to("和其他信用卡共用額度")
    ns = d.shot("新增信用卡的共用額度選項")
    grp = [n for n in ns if "測試信用卡" in n.text and "第二張卡" in n.text]
    d.check("可以直接選「測試信用卡＋第二張卡」這個共用組", bool(grp), [n.text for n in ns if n.text][:40])
    if grp:
        d.tap(grp[0])
        d.time.sleep(0.8)
        d.tap_text("儲存", exact=True)
        d.time.sleep(1.2)
        open_account("NewC")
        ns = d.shot("新卡的明細")
        d.check("新卡加入後顯示整組共用：已用 $50,000・可用 $50,000・額度 $100,000",
                d.has(ns, "已用 $50,000・可用 $50,000・額度 $100,000", True), [n.text for n in ns if "已用" in n.text])


@case(D, "離開共用之後可以馬上重新選回原本的卡")
def t_leave_then_reselect():
    d.fresh(ab_seed().json())
    # (1) 副卡按「離開共用」，還沒儲存就應該能再選回原本的主卡
    open_account("第二張卡")
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    found = scroll_to("離開共用")
    d.check("副卡的編輯畫面有「離開共用」", found)
    d.tap_text("離開共用", exact=True)
    d.time.sleep(0.8)
    scroll_to("可以多選")
    ns = d.shot("按離開共用之後（還沒儲存）")
    d.check("按了離開共用之後，原本的主卡「測試信用卡」馬上可以再選", d.has(ns, "測試信用卡", True), [n.text for n in ns if n.text][:40])
    d.tap_text("測試信用卡", exact=True)
    d.time.sleep(0.8)
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    ns = d.shot("重新選回測試信用卡並儲存")
    d.check("仍然是兩張共用：已用 $50,000・可用 $50,000・額度 $100,000",
            d.has(ns, "已用 $50,000・可用 $50,000・額度 $100,000", True), [n.text for n in ns if "已用" in n.text])
    # (2) 主卡取消第二張卡、儲存後立刻再編輯，第二張卡要可以再選
    d.tap_back()
    to_main()
    open_account("測試信用卡")
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    scroll_to("可以多選")
    d.tap_text("第二張卡", exact=True)
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    d.tap_text("編輯", exact=True)
    d.wait_text("編輯帳戶", timeout=10)
    scroll_to("可以多選")
    ns = d.shot("取消共用、儲存後再編輯主卡")
    d.check("取消之後馬上再編輯，第二張卡仍然在清單裡可以選", d.has(ns, "第二張卡", True))
    d.tap_text("第二張卡", exact=True)
    d.tap_text("儲存", exact=True)
    d.time.sleep(1.2)
    ns = d.shot("再選回第二張卡")
    d.check("再次共用：已用 $50,000・可用 $50,000・額度 $100,000",
            d.has(ns, "已用 $50,000・可用 $50,000・額度 $100,000", True), [n.text for n in ns if "已用" in n.text])


@case(D, "帳戶分頁最底下的說明不會被記一筆按鈕擋住")
def t_acc_bottom_space():
    d.fresh(S.base(f.today(), extra_accounts=12).json())
    acc_tab()
    for _ in range(8):
        if d.has(d.nodes(), "點帳戶可以看它的明細", False):
            break
        d.swipe(540, 1700, 540, 700, 500)
        d.time.sleep(0.6)
    d.swipe(540, 1700, 540, 700, 500)       # 再多滑一次，確保已經到最底
    d.time.sleep(0.8)
    ns = d.shot("帳戶分頁捲到最底")
    hint = d.first(ns, "點帳戶可以看它的明細", False)
    fab = d.first(ns, "記一筆", True)
    d.check("找得到最底下的說明和記一筆按鈕", hint is not None and fab is not None)
    if hint and fab:
        gap = fab.y1 - hint.y2
        d.check("說明文字在記一筆按鈕上方，至少留 50 像素的空隙（小螢幕、說明折成兩行時才不會被擋住）", gap >= 50, gap)
        d.check("空隙也不能太大（最多 160 像素），不然底下一大塊空白", gap <= 160, gap)


# ───────────────────────── 鍵盤輸入時間：自動回滾輪、報銷收款時間 ─────────────────────────
def open_time_typing():
    """記一筆 → 時間按鈕 → 點一下滾輪變鍵盤輸入"""
    import re
    f.open_add()
    chip = next(n for n in d.nodes() if re.fullmatch(r"\d\d:\d\d", n.text))
    d.tap(chip)
    d.wait_text("選擇時間", timeout=10)
    tap_wheel_to_type()


@case(D, "選時間：鍵盤輸入時收起鍵盤，自動回到滾輪")
def t_time_collapse_keyboard():
    d.fresh(empty_seed().json())
    open_time_typing()
    ns = d.shot("鍵盤輸入")
    d.check("先確認變成鍵盤輸入：有輸入框、螢幕鍵盤有出現", bool(d.edits()) and d.ime_shown(), (len(d.edits()), d.ime_shown()))
    d.hide_ime()
    d.time.sleep(1.5)
    ns = d.shot("收起鍵盤之後")
    d.check("收起鍵盤後自動回到滾輪：輸入框不見了", not d.edits(), [n.text for n in d.edits()])
    d.check("看得到小時的滾輪", len(wheel_hours(ns)) >= 3, [n.text for n in wheel_hours(ns)])
    d.check("對話框還在（沒有被關掉）", d.has(ns, "選擇時間", True) and d.has(ns, "確定", True))
    d.check("提示文字回到「點一下數字可以直接輸入」", d.has(ns, "點一下數字可以直接輸入"))


@case(D, "選時間：鍵盤輸入時點旁邊，回到滾輪並保留已輸入的時間")
def t_time_tap_outside():
    d.fresh(empty_seed().json())
    open_time_typing()
    type_time("09", "30")
    ns = d.shot("輸入 09:30")
    hint = d.first(ns, "收起鍵盤或點旁邊空白處", False)
    d.check("鍵盤輸入時有提示「收起鍵盤或點旁邊空白處，回到滾輪」", hint is not None, [n.text for n in ns if n.text][:20])
    if hint:
        d.tap(hint)
        d.time.sleep(1.5)
    ns = d.shot("點旁邊之後")
    d.check("回到滾輪：輸入框不見了、螢幕鍵盤收起來", not d.edits() and not d.ime_shown(), (len(d.edits()), d.ime_shown()))
    d.check("滾輪正中間是剛輸入的 09:30", [n.text for n in wheel_hours(ns)][2:3] == ["09"] and [n.text for n in wheel_minutes(ns)][2:3] == ["30"],
            ([n.text for n in wheel_hours(ns)], [n.text for n in wheel_minutes(ns)]))
    d.tap_text("確定", exact=True)
    d.time.sleep(1)
    d.check("按確定後時間按鈕是 09:30", d.has(d.nodes(), "09:30", True))
