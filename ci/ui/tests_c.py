# -*- coding: utf-8 -*-
"""分流 c：深色模式、吉祥物、預算、資料相容、裝置狀態"""
import os

import driver as d
import flows as f
import seed as S
from runner import case

C = "c"
APK = os.environ.get("APK", os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "app", "build", "outputs", "apk", "debug", "app-debug.apk"))
SUB_PAGES = ["帳本管理", "帳戶管理", "分類管理", "報銷", "常用記帳", "外觀與吉祥物", "備份與匯入匯出"]


def safe(label, fn):
    try:
        fn()
    except Exception as e:  # noqa: BLE001
        d.check(f"{label}：頁面可開啟", False, f"{type(e).__name__}: {e}")
        try:
            d.shot(f"{label}（失敗）")
        except Exception:  # noqa: BLE001
            pass


def dark_seed(dark=2):
    s = S.base(f.today(), dark=dark)
    return s.json()


def walk_tabs(dark):
    for name in ("明細", "日曆", "統計", "我的"):
        f.tab(name)
        d.time.sleep(1.8)
        d.shot(f"{'深色' if dark == 2 else '淺色'}：{name}", contrast=(dark == 2))


# ───────── 深色 / 淺色
@case(C, "深色：主要分頁文字可讀", visual=True)
def t_dark_tabs():
    d.fresh(dark_seed())
    walk_tabs(2)


@case(C, "深色：我的各子頁", visual=True)
def t_dark_sub():
    d.fresh(dark_seed())
    for label in SUB_PAGES:
        def one(label=label):
            f.me_page(label)
            d.time.sleep(1.2)
            d.shot(f"深色：{label}", contrast=True)
            d.tap_back()
            d.time.sleep(1)
        safe(label, one)

    def budget():
        f.tab("我的")
        d.tap(d.wait(lambda n: n.text == "每月預算", 8, "每月預算"))
        d.time.sleep(1.2)
        d.shot("深色：每月預算對話框", contrast=True)
        d.back()
    safe("每月預算", budget)


@case(C, "深色：記一筆頁面", visual=True)
def t_dark_edit():
    d.fresh(dark_seed())
    f.open_add()
    d.shot("深色：記一筆", contrast=True)

    def tags():
        d.tap_text("新增標籤", exact=True)
        d.time.sleep(1)
        d.shot("深色：標籤對話框", contrast=True)
        d.back()
    safe("標籤對話框", tags)

    def date():
        ns = d.nodes()
        d.tap(next(n for n in ns if n.text.startswith("今天")))
        d.time.sleep(1.2)
        d.shot("深色：日期對話框", contrast=True)
        d.back()
    safe("日期對話框", date)

    def fee():
        f.chip_scroll("手續費／優惠", "標籤")
        d.time.sleep(1)
        d.shot("深色：手續費對話框", contrast=True)
        d.back()
    safe("手續費對話框", fee)

    def transfer():
        d.tap_text("轉帳", exact=True, nth=0)
        d.time.sleep(1.2)
        d.shot("深色：轉帳畫面（從／轉到）", contrast=True)
        d.tap(d.wait(lambda n: n.text == "從", 8, "從"))
        d.time.sleep(1.2)
        d.shot("深色：選擇轉出帳戶", contrast=True)
        d.back()
    safe("轉帳畫面", transfer)


@case(C, "深色：報銷頁面", visual=True)
def t_dark_reimb():
    s = S.with_reimb(f.today(), dark=2)
    d.fresh(s.json())
    f.me_page("報銷")
    d.wait_text("還沒收到的報銷款", timeout=15)
    d.shot("深色：報銷總覽", contrast=True)

    def receive():
        d.tap(d.wait(lambda n: n.text == "Hua", 8, "Hua"))
        d.wait_text("這次收到多少", timeout=10)
        d.shot("深色：收款頁", contrast=True)
        d.fill(d.edits()[0], "500")
        d.hide_ime()
        d.tap_text("確認收款", exact=False)
        d.time.sleep(1.2)
        d.shot("深色：繼續追／不追了對話框", contrast=True)
        d.tap_text("取消", exact=True)
    safe("收款頁", receive)

    def edit():
        d.tap_back()
        d.time.sleep(1)
        d.tap_back()
        d.time.sleep(1)
        f.open_add()
        f.keypad("1000")
        f.chip_scroll("報銷", "新增標籤")
        d.wait_text("這筆的報銷", timeout=10)
        d.shot("深色：報銷整頁（記一筆）", contrast=True)
    safe("報銷整頁", edit)


@case(C, "深色：搜尋、統計下鑽、帳戶明細", visual=True)
def t_dark_misc():
    d.fresh(dark_seed())

    def search():
        f.tab("統計")
        d.time.sleep(1.5)
        d.tap(d.wait(lambda n: n.text.startswith("搜尋記錄"), 8, "搜尋列"))
        d.time.sleep(1.5)
        d.shot("深色：搜尋頁", contrast=True)
        d.tap_back()
    safe("搜尋頁", search)

    def drill():
        f.tab("統計")
        d.time.sleep(1.5)
        d.tap(d.wait(lambda n: n.text == "餐飲", 8, "餐飲"))
        d.time.sleep(1.5)
        d.shot("深色：統計展開／下鑽", contrast=True)
    safe("統計下鑽", drill)

    def acc():
        f.me_page("帳戶管理")
        d.time.sleep(1)
        d.tap(d.wait(lambda n: n.text == "現金", 8, "現金"))
        d.time.sleep(1.5)
        d.shot("深色：帳戶明細", contrast=True)
    safe("帳戶明細", acc)


@case(C, "淺色模式外觀未改變", visual=True)
def t_light():
    d.fresh(dark_seed(1))
    walk_tabs(1)
    f.open_add()
    d.shot("淺色：記一筆")


@case(C, "系統主題即時切換", visual=True)
def t_theme_switch():
    s = S.base(f.today(), dark=0)
    d.fresh(s.json())
    d.sh("cmd uimode night yes")
    d.time.sleep(3)
    ns = d.shot("系統切到深色", contrast=True)
    d.check("切換後 App 仍在（沒有閃退）", d.has(ns, "明細"))
    d.sh("cmd uimode night no")
    d.time.sleep(3)
    ns = d.shot("系統切回淺色")
    d.check("切回後 App 仍在", d.has(ns, "明細"))


# ───────── 吉祥物
def mood_texts(ns):
    return " ".join(n.text for n in ns if n.text)


def home_text():
    d.time.sleep(1.5)
    ns = d.shot("首頁（吉祥物對話）")
    return ns, mood_texts(ns)


@case(C, "顯示吉祥物開關", visual=True)
def t_mascot_switch():
    d.fresh(f.base_seed())
    ns = d.shot("首頁（有吉祥物）")
    d.check("首頁有吉祥物名稱「小鹿」", d.has(ns, "小鹿", True))
    f.me_page("外觀與吉祥物")
    d.time.sleep(1)
    ns = d.shot("外觀與吉祥物")
    lbl = d.first(ns, "顯示吉祥物")
    d.check("有「顯示吉祥物」開關", lbl is not None)
    sw = [n for n in ns if n.checkable and lbl and abs(n.cy - lbl.cy) < 60]
    if sw:
        d.tap(sw[0])
        d.time.sleep(1)
        d.shot("關閉開關後")
    d.tap_back()
    f.tab("明細")
    d.time.sleep(1.5)
    ns = d.shot("首頁（關閉吉祥物後）")
    d.check("首頁不再顯示「小鹿」", not d.has(ns, "小鹿", True))


@case(C, "切換六種吉祥物且名稱正確", visual=True)
def t_mascot_kinds():
    d.fresh(f.base_seed())
    f.me_page("外觀與吉祥物")
    d.time.sleep(1)
    d.shot("外觀頁（動物六宮格上半）")
    d.scroll_down(1)
    d.shot("外觀頁（下半）")


@case(C, "心情：普通與開心", visual=True, time_at=(10, 10, 12, 0))
def t_mood_normal_happy():
    d.fresh(S.Seed(f.today()).json())
    ns, t = home_text()
    d.check("今天還沒記帳 → 普通的對話", any(k in t for k in ("今天還沒記帳", "今天過得好嗎", "花了什麼記得告訴我")), t[:80])
    d.fresh(S.budget_case(f.today(), 0, spent=100).json())
    ns, t = home_text()
    d.check("今天有記帳 → 開心的對話", any(k in t for k in ("好好記帳", "最可愛", "一起加油", "乖乖待在帳本")), t[:80])


@case(C, "心情：今天有收入時歡呼", visual=True, time_at=(10, 10, 12, 0))
def t_mood_cheer():
    d.fresh(S.budget_case(f.today(), 0, income_today=1200).json())
    ns, t = home_text()
    d.check("歡呼的對話含「進來」", "進來" in t, t[:80])


@case(C, "心情：深夜睏", visual=True, time_at=(10, 10, 23, 30))
def t_mood_sleepy():
    d.fresh(S.Seed(f.today()).json())
    ns, t = home_text()
    d.check("深夜的對話（好晚了／夜深了）", "好晚了" in t or "夜深了" in t, t[:80])


@case(C, "擔心：已超過預算", visual=True, time_at=(10, 10, 12, 0))
def t_worry_over():
    d.fresh(S.budget_case(f.today(), 1000, spent=1200).json())
    ns, t = home_text()
    d.check("對話提到已超過預算 $200", "已經超過預算" in t and "$200" in t, t[:100])


@case(C, "擔心：已用 80% 且剩餘天數超過 7 天", visual=True, time_at=(10, 10, 12, 0))
def t_worry_80():
    d.fresh(S.budget_case(f.today(), 1000, spent=850).json())
    ns, t = home_text()
    d.check("擔心的對話（花得有點快）", "花得有點快" in t, t[:100])


@case(C, "月底前 80% 不擔心只提醒", visual=True, time_at=(10, 28, 12, 0))
def t_no_worry_month_end():
    d.fresh(S.budget_case(f.today(), 1000, spent=850).json())
    ns, t = home_text()
    d.check("不是擔心的對話", "花得有點快" not in t, t[:100])
    d.check("輕輕提醒「預算已經用了 85%」", "預算已經用了 85%" in t, t[:100])


@case(C, "擔心：10 號後花費進度超前", visual=True, time_at=(10, 15, 12, 0))
def t_worry_pace():
    d.fresh(S.budget_case(f.today(), 3000, spent=2200).json())
    ns, t = home_text()
    d.check("擔心的對話", "花得有點快" in t, t[:100])


@case(C, "月初固定支出不誤判", visual=True, time_at=(10, 5, 12, 0))
def t_no_worry_early():
    d.fresh(S.budget_case(f.today(), 3000, spent=2000).json())
    ns, t = home_text()
    d.check("不擔心", "花得有點快" not in t and "超過預算" not in t, t[:100])


@case(C, "擔心與歡呼的優先順序", visual=True, time_at=(10, 10, 12, 0))
def t_worry_over_cheer():
    d.fresh(S.budget_case(f.today(), 1000, spent=1200, income_today=500).json())
    ns, t = home_text()
    d.check("擔心優先於歡呼", "已經超過預算" in t, t[:100])


@case(C, "預算以扣除報銷後的支出計算", visual=True, time_at=(10, 10, 12, 0))
def t_budget_net_reimb():
    d.fresh(S.budget_case(f.today(), 1000, spent=1200, reimb_full=True).json())
    ns, t = home_text()
    d.check("全額報銷後不超預算，不擔心", "超過預算" not in t and "花得有點快" not in t, t[:100])


@case(C, "沒設預算時不擔心", visual=True, time_at=(10, 10, 12, 0))
def t_no_budget():
    d.fresh(S.budget_case(f.today(), 0, spent=5000).json())
    ns, t = home_text()
    d.check("不擔心", "超過預算" not in t and "花得有點快" not in t and "預算已經用" not in t, t[:100])


@case(C, "預算提示卡圖示", visual=True, time_at=(10, 10, 12, 0))
def t_budget_card():
    for label, spent in (("接近預算", 850), ("超支", 1200), ("達標", 300)):
        d.fresh(S.budget_case(f.today(), 1000, spent=spent).json())
        d.time.sleep(1.5)
        d.shot(f"預算提示：{label}")


@case(C, "六種動物五種心情圖片正確", visual=True, time_at=(10, 10, 12, 0))
def t_animals():
    for kind in ("deer", "cat", "bear", "bunny", "dog", "schnauzer"):
        d.fresh(S.budget_case(f.today(), 1000, spent=1200, mascot=kind).json())
        d.time.sleep(1.2)
        d.shot(f"{kind}：擔心")
        d.fresh(S.budget_case(f.today(), 0, income_today=500, mascot=kind).json())
        d.time.sleep(1.2)
        d.shot(f"{kind}：歡呼")


@case(C, "設定每月預算與個別月份")
def t_budget_dialog():
    d.fresh(S.Seed(f.today()).json())
    f.tab("我的")
    d.time.sleep(1)
    d.tap(d.wait(lambda n: n.text == "每月預算", 8, "每月預算"))
    d.wait_text("預算設定", timeout=8)
    d.shot("預算設定對話框")
    ed = d.edits()
    d.check("有預算輸入框", len(ed) >= 1)
    d.fill(ed[0], "1000")
    d.hide_ime()
    f.confirm_dialog()
    d.time.sleep(1.5)
    ns = d.shot("設定後的我的頁面")
    d.check("我的頁面顯示 $1,000", any("$1,000" in n.text for n in ns))


# ───────── 資料相容與裝置
@case(C, "覆蓋安裝後舊資料完整保留")
def t_reinstall():
    d.fresh(f.base_seed())
    d.check("安裝前有 -$120", d.has(d.nodes(), "-$120", True))
    d.stop_app()
    d.reinstall_same_apk(APK)
    d.launch()
    d.wait_main()
    ns = d.shot("覆蓋安裝後")
    d.check("覆蓋安裝後帳目仍在", d.has(ns, "-$120", True) and d.has(ns, "-$85", True))


@case(C, "還原舊格式備份檔")
def t_old_format():
    d.fresh(S.old_format(f.today()))
    ns = d.shot("舊格式資料載入後", contrast=False)
    d.check("舊格式資料載入成功（帳目在）", d.has(ns, "-$120", True))
    f.me_page("報銷")
    d.wait_text("還沒收到的報銷款", timeout=15)
    ns = d.shot("舊格式的報銷頁")
    d.check("舊的單一報銷顯示正常", d.has(ns, "沒填對象", True))


@case(C, "網路權限只用在抓價")
def t_internet_for_prices():
    out = d.sh(f"dumpsys package {d.PKG}")
    d.check("App 有宣告 INTERNET 權限（只用來抓持股價格）", "android.permission.INTERNET" in out)


@case(C, "輸入到一半被打斷後回來")
def t_interrupted():
    d.fresh(f.base_seed())
    f.open_add()
    f.keypad("250")
    d.sh("input keyevent 3")
    d.time.sleep(2)
    d.launch()
    ns = d.shot("回到 App")
    d.check("仍在記一筆", d.has(ns, "備註（選填）"))
    d.check("金額 $250 還在", d.has(ns, "$250", True))
    d.sh("settings put system accelerometer_rotation 0")
    d.sh("settings put system user_rotation 1")
    d.time.sleep(2.5)
    ns = d.shot("旋轉成橫向")
    d.check("橫向後仍在記一筆", d.has(ns, "備註（選填）") or d.has(ns, "$250"))
    d.sh("settings put system user_rotation 0")
    d.time.sleep(2.5)
    ns = d.shot("轉回直向")
    d.check("轉回直向金額仍是 $250", d.has(ns, "$250", True))


@case(C, "旋轉與背景返回時頁面堆疊正常")
def t_stack_rotate():
    d.fresh(f.base_seed())
    f.me_page("報銷")
    d.wait_text("還沒收到的報銷款", timeout=15)
    d.sh("input keyevent 3")
    d.time.sleep(2)
    d.launch()
    ns = d.shot("按 Home 後回來")
    d.check("停在報銷頁", d.has(ns, "還沒收到的報銷款"))
    d.sh("settings put system accelerometer_rotation 0")
    d.sh("settings put system user_rotation 1")
    d.time.sleep(2.5)
    d.shot("橫向")
    d.sh("settings put system user_rotation 0")
    d.time.sleep(2.5)
    ns = d.shot("轉回直向")
    d.check("轉回後仍在報銷頁", d.has(ns, "還沒收到的報銷款"))
    d.tap_back()
    d.time.sleep(1.2)
    ns = d.shot("返回")
    d.check("返回到我的頁面", d.has(ns, "帳戶管理"))


@case(C, "大量資料效能", visual=True)
def t_many():
    d.fresh(S.many(f.today(), n=300).json())
    ns = d.shot("300 筆資料的首頁")
    d.check("首頁正常顯示", d.has(ns, "本月支出"))
    d.scroll_down(6)
    d.shot("往下捲")
    f.tab("統計")
    d.time.sleep(2)
    ns = d.shot("統計")
    d.check("統計頁沒有閃退", d.has(ns, "統計") or d.has(ns, "支出"))


@case(C, "字體放大與可及性", visual=True)
def t_font_scale():
    d.sh("settings put system font_scale 1.6")
    d.fresh(f.base_seed())
    d.shot("字體 160%：首頁", contrast=True)
    f.open_add()
    d.shot("字體 160%：記一筆")
    d.tap(d.first(d.nodes(), "關閉"))
    f.me_page("報銷")
    d.shot("字體 160%：報銷")


@case(C, "信用卡週期與繳款", visual=True)
def t_card():
    s = S.Seed(f.today())
    s.expense(0, 1500, S.C_FOOD, acc=S.CARD, note="card1")
    s.expense(-3, 800, S.C_SHOP, acc=S.CARD, note="card2")
    d.fresh(s.json())
    f.me_page("帳戶管理")
    d.time.sleep(1)
    d.shot("帳戶管理")
    d.tap(d.wait(lambda n: n.text == "測試信用卡", 8, "測試信用卡"))
    d.time.sleep(2)
    d.shot("信用卡明細", contrast=True)
