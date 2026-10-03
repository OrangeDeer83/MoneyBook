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
    """對話框按鈕被停用時，畫面結構裡是一個 enabled=false 的方塊蓋在文字上"""
    t = d.first(ns, label, True)
    return bool(t) and any((not n.enabled) and n.x1 <= t.cx <= n.x2 and n.y1 <= t.cy <= n.y2 for n in ns)


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
    for _ in range(4):
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
    d.check("信用卡有「欠款請輸入負數」的提示", d.has(ns, "信用卡欠款請輸入負數"))
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
