# -*- coding: utf-8 -*-
"""0.2.0 之後新增的用例（帳戶分頁、更新餘額、持股、記一筆選帳戶……）"""
import driver as d
import flows as f
import seed as S
from runner import case

D = "d"


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
