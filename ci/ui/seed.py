# -*- coding: utf-8 -*-
"""產生測試用的資料檔（格式同 Codec.encode），日期以「今天」為基準，避免跨日後測試失效。"""
import datetime
import json

EPOCH = datetime.date(1970, 1, 1)

BOOK, CASH, BANK, CARD = 1, 2, 3, 4
INVEST = 600   # 投資帳戶（invest=True 時才有）
LOAN = 601     # 貸款帳戶「信貸」，初始 -200,000（loan=True 時才有）
CARD2 = 602    # 第二張信用卡「第二張卡」，沒有自己的額度（card2=True 時才有）
# 支出分類：頂層與子分類
C_FOOD, C_BREAKFAST, C_LUNCH, C_DINNER, C_DRINK = 10, 11, 12, 13, 14
C_TRAFFIC, C_METRO, C_HSR = 20, 21, 22
C_SHOP, C_3C = 30, 31
C_HOME, C_RENT = 40, 41
C_PLAY = 50
C_MED = 60
# 收入分類
C_SALARY, C_BONUS, C_OTHER_IN = 70, 71, 72


def eday(d):
    return (d - EPOCH).days


class Seed:
    def __init__(self, today, dark=0, mascot="deer", budget=0, month_budgets=None, many_categories=False, extra_accounts=0, extra_books=0, invest=False, hidden_account=False, loan=False, fav_card=False, card2=False):
        self.today = today
        self.dark = dark
        self.mascot = mascot
        self.budget = budget
        self.month_budgets = month_budgets or {}
        self.txns = []
        self.templates = []
        self.extra_cats = []
        self._id = 1000
        self.many_categories = many_categories
        self.extra_accounts = extra_accounts
        self.extra_books = extra_books
        self.invest = invest
        self.hidden_account = hidden_account
        self.loan = loan
        self.fav_card = fav_card
        self.card2 = card2
        self.trades = []
        self.prices = []

    def nid(self):
        self._id += 1
        return self._id

    def day(self, offset):
        return eday(self.today + datetime.timedelta(days=offset))

    def add(self, offset, type_, amount, cat=None, acc=CASH, to=None, note="", tags=(), fee=0, disc=0,
            reimb=0, reimb_amount=-1, items=None, time=-1):
        t = dict(
            id=self.nid(), bookId=BOOK, type=type_, amount=amount, categoryId=cat, accountId=acc, toAccountId=to,
            day=self.day(offset), note=note, tags=list(tags), instGroup=None, instIndex=0, instTotal=0,
            fee=fee, discount=disc, reimb=reimb, reimbAccountId=None, reimbDay=None,
            reimbAmount=0 if reimb == 0 else (amount if reimb_amount < 0 else reimb_amount),
            reimbItems=items or [], time=time,
        )
        self.txns.append(t)
        return t

    def expense(self, offset, amount, cat, **kw):
        return self.add(offset, "EXPENSE", amount, cat, **kw)

    def income(self, offset, amount, cat=C_SALARY, **kw):
        return self.add(offset, "INCOME", amount, cat, **kw)

    def transfer(self, offset, amount, frm=CASH, to=BANK, **kw):
        return self.add(offset, "TRANSFER", amount, None, acc=frm, to=to, **kw)

    def trade(self, offset, symbol, buy, qty, price, fee=0, name="", acc=INVEST, txn=None, market=""):
        """投資帳戶的買賣記錄；txn 是連動的轉帳（self.transfer 回傳的那筆）；market 空白＝舊資料（自動判斷）"""
        t = dict(id=self.nid(), accountId=acc, symbol=symbol, name=name, day=self.day(offset), buy=buy,
                 qty=qty, price=price, fee=fee, txnId=(txn["id"] if txn else None), market=market)
        self.trades.append(t)
        return t

    def price(self, symbol, offset, price):
        self.prices.append(dict(symbol=symbol, day=self.day(offset), price=price))

    def template(self, name, amount, cat, acc=CASH, note=""):
        self.templates.append(dict(id=self.nid(), name=name, type="EXPENSE", amount=amount, categoryId=cat, accountId=acc, note=note, tags=[]))

    def json(self):
        cats = []

        def cat(i, name, kind, parent=None, order=0, color=1, emoji="img:cat_box"):
            cats.append(dict(id=i, name=name, emoji=emoji, color=color, kind=kind, parentId=parent, order=order))

        cat(C_FOOD, "餐飲", "EXPENSE", None, 0, 1)
        for i, (cid, n) in enumerate([(C_BREAKFAST, "早餐"), (C_LUNCH, "午餐"), (C_DINNER, "晚餐"), (C_DRINK, "飲料")]):
            cat(cid, n, "EXPENSE", C_FOOD, i, 1)
        cat(C_TRAFFIC, "交通", "EXPENSE", None, 1, 2)
        cat(C_METRO, "捷運", "EXPENSE", C_TRAFFIC, 0, 2)
        cat(C_HSR, "高鐵", "EXPENSE", C_TRAFFIC, 1, 2)
        cat(C_SHOP, "購物", "EXPENSE", None, 2, 3)
        cat(C_3C, "3C", "EXPENSE", C_SHOP, 0, 3)
        cat(C_HOME, "居住", "EXPENSE", None, 3, 4)
        cat(C_RENT, "房租", "EXPENSE", C_HOME, 0, 4)
        cat(C_PLAY, "娛樂", "EXPENSE", None, 4, 5)
        cat(C_MED, "醫療", "EXPENSE", None, 5, 6)
        cat(C_SALARY, "薪資", "INCOME", None, 0, 7)
        cat(C_BONUS, "獎金", "INCOME", None, 1, 7)
        cat(C_OTHER_IN, "其他收入", "INCOME", None, 2, 7)
        cats.extend(self.extra_cats)

        accounts = [
            dict(id=CASH, name="現金", emoji="img:acc_cash", type="CASH", initial=1000, order=0, hidden=False, badge="", badgeColor=0, creditLimit=0, statementDay=0, dueDay=0),
            dict(id=BANK, name="測試銀行", emoji="img:acc_bank", type="BANK", initial=50000, order=1, hidden=False, badge="", badgeColor=0, creditLimit=0, statementDay=0, dueDay=0),
            dict(id=CARD, name="測試信用卡", emoji="img:acc_card", type="CARD", initial=0, order=2, hidden=False, badge="", badgeColor=0, creditLimit=100000, statementDay=25, dueDay=10),
        ]
        for i in range(self.extra_accounts):
            accounts.append(dict(id=500 + i, name=f"帳戶{i + 1:02d}", emoji="img:acc_bank", type="BANK", initial=0, order=10 + i, hidden=False, badge="", badgeColor=0, creditLimit=0, statementDay=0, dueDay=0))
        if self.invest:
            accounts.append(dict(id=INVEST, name="測試證券", emoji="img:extra_gold", type="INVEST", initial=0, order=5, hidden=False, badge="", badgeColor=0, creditLimit=0, statementDay=0, dueDay=0))
        if self.loan:
            accounts.append(dict(id=LOAN, name="信貸", emoji="img:acc_receipt", type="LOAN", initial=-200000, order=6, hidden=False, badge="", badgeColor=0, creditLimit=0, statementDay=0, dueDay=0))
        if self.hidden_account:
            accounts.append(dict(id=610, name="隱藏帳戶", emoji="img:acc_bank", type="BANK", initial=123, order=7, hidden=True, badge="", badgeColor=0, creditLimit=0, statementDay=0, dueDay=0))
        if self.card2:
            accounts.append(dict(id=CARD2, name="第二張卡", emoji="img:acc_card", type="CARD", initial=0, order=8, hidden=False, badge="", badgeColor=0, creditLimit=0, statementDay=0, dueDay=0))
        if self.fav_card:
            accounts[2]["favorite"] = True   # 測試信用卡：從沒用過，但標了星號
        mb = dict(self.month_budgets)
        root = dict(
            version=2, nextId=self._id + 100,
            books=[dict(id=BOOK, name="我的帳本", emoji="img:ui_ledger", budget=self.budget, monthBudgets=mb)]
            + [dict(id=700 + i, name=f"帳本{i + 2:02d}", emoji="img:ui_ledger", budget=0, monthBudgets={}) for i in range(self.extra_books)],
            accounts=accounts, categories=cats, txns=self.txns, templates=self.templates,
            trades=self.trades, prices=self.prices,
            prefs=dict(bookId=BOOK, palette="milktea", mascot=self.mascot, mascotName="", mascotLast=self.mascot, dark=self.dark, celebrate=False),
        )
        return json.dumps(root, ensure_ascii=False)


def reimb_item(who, amount, closed=False, pays=None):
    return dict(who=who, amount=amount, closed=closed, pays=pays or [])


def pay(day_offset_date, account, amount):
    return dict(day=eday(day_offset_date), accountId=account, amount=amount)


# ───────────────────────── 常用資料組合 ─────────────────────────
def base(today, **kw):
    """一般測試資料：今天的午餐、不細分、轉帳；昨天起有報銷；上個月有幾筆"""
    s = Seed(today, **kw)
    s.expense(0, 120, C_LUNCH, note="lunch")
    s.expense(0, 85, C_FOOD, note="plain")
    s.transfer(0, 500, CASH, BANK, fee=15)
    s.expense(-1, 1800, C_HSR, acc=BANK, note="trip", reimb=1, reimb_amount=1800)                 # 舊格式：單一報銷
    s.expense(-1, 1500, C_MED, note="doctor", reimb=1, reimb_amount=900)                          # 舊格式：只能報 900
    s.expense(-1, 2400, C_FOOD, note="party", reimb=1, reimb_amount=2400,
              items=[reimb_item("Amy", 1000), reimb_item("Bob", 1400)])
    s.income(-1, 50000, C_SALARY, acc=BANK, note="salary")
    last = today.replace(day=1) - datetime.timedelta(days=1)
    off = (last - today).days
    s.expense(off, 640, C_PLAY, note="movie")
    s.expense(off - 2, 3600, C_3C, acc=BANK, disc=200, note="gadget")
    s.income(off - 3, 800, C_OTHER_IN)
    s.template("Tpl A", 100, C_LUNCH)
    return s


def with_reimb(today, **kw):
    """報銷專用：同一個人有兩筆（舊的先收），另一個人一筆，已收款一筆"""
    s = Seed(today, **kw)
    s.expense(-3, 1000, C_FOOD, note="r1", reimb=1, reimb_amount=1000, items=[reimb_item("Ming", 1000)])
    s.expense(-1, 500, C_TRAFFIC, note="r2", reimb=1, reimb_amount=500, items=[reimb_item("Ming", 500)])
    s.expense(-2, 800, C_SHOP, note="r3", reimb=1, reimb_amount=800, items=[reimb_item("Hua", 800)])
    s.expense(-4, 600, C_PLAY, note="r4", reimb=1, reimb_amount=600,
              items=[reimb_item("Lin", 600, False, [pay(today - datetime.timedelta(days=1), CASH, 200)])])
    return s


def many(today, n=40, **kw):
    s = Seed(today, **kw)
    for i in range(n):
        s.expense(0 if i % 2 == 0 else -1, 10 + i, C_FOOD if i % 3 else C_TRAFFIC, note=f"row{i}")
    return s


def budget_case(today, budget, spent=0, income_today=0, reimb_full=False, dark=0, mascot="deer"):
    s = Seed(today, budget=budget, dark=dark, mascot=mascot)
    if spent:
        if reimb_full:
            s.expense(0, spent, C_FOOD, reimb=1, reimb_amount=spent)
        else:
            s.expense(0, spent, C_FOOD)
    if income_today:
        s.income(0, income_today, C_BONUS)
    return s


def old_format(today):
    """3.0.0 以前的資料：沒有報銷明細、沒有 prefs 的吉祥物欄位、沒有 reimbItems"""
    s = base(today)
    root = json.loads(s.json())
    for t in root["txns"]:
        t.pop("reimbItems", None)
        t.pop("reimbAmount", None)
    root["prefs"] = dict(bookId=BOOK, palette="milktea")
    return json.dumps(root, ensure_ascii=False)
