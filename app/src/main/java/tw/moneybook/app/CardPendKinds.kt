package tw.moneybook.app

import java.time.LocalDate

/*
 * 刷卡通知第三步：退款、預先授權、分期（使用者 11/16～11/17 確認草圖 ci-out/card_step3b.png、card_step3c.png）。
 *  - 退款：記成那張卡的收入、分類「退款」（不算收入），自動連到同一張卡 60 天內同一家店的那筆，統計從那筆的分類扣回（見 Refund.kt）
 *  - 預先授權：先記成「預估」的支出，日期是授權那天（入住那天）；之後同一張卡、同一家店的刷卡通知來了，首頁問一次要不要改成實際金額，
 *    請款落在下一期帳單就改入帳月份。銀行沒發請款通知就不問，帳戶頁看得到還沒請款的
 *  - 分期：第一次刷寫「分 12 期」的，按「記」記成 12 期（用現有的分期）；每個月「第 1/12 期」的請款通知不記
 */

// ───────── 退款 ─────────

/** 這筆退款會自動連到哪一筆（台幣才連；見 refundGuess） */
fun AppData.pendRefundOf(p: CardPend, cardId: Long?): Txn? =
    if (p.kind != PendKind.REFUND || p.cur.isNotEmpty()) null else refundGuess(cardId, p.day, p.amount, p.merchant)

/**
 * 退款記成的一筆收入（不算收入，退的是 link 那筆；0＝沒連）。外幣：記在同幣別的外幣卡是外幣金額；
 * 記在台幣卡要台幣金額 twd，沒給就用目前匯率，沒有匯率回傳 null。
 */
fun AppData.pendRefundTxn(p: CardPend, id: Long, bookId: Long, cardId: Long, catId: Long, link: Long, twd: Long? = null): Txn? {
    val acc = accMap[cardId] ?: return null
    val base = Txn(
        id = id, bookId = bookId, type = TxType.INCOME, amount = p.amount, categoryId = catId, accountId = cardId, toAccountId = null,
        day = p.day, note = "", tags = listOf(CARD_NOTI_TAG), time = p.minute, noti = p.mainSrc, noIncome = true, refundOf = link,
        shop = pendShop(p, false).shop, bankShop = p.merchant,
    )
    if (p.cur.isEmpty()) return base
    val est = twd ?: rateOf(p.cur)?.let { fxToTwd(p.amount, Currencies.of(p.cur).decimals, it) } ?: return null
    return if (acc.isForeign && acc.currency == p.cur) base.copy(amount = est, fxAmount = p.amount) else base.copy(amount = est)
}

// ───────── 預先授權 ─────────

/** 預先授權之後幾天內的刷卡會當成請款來問 */
const val PREAUTH_DAYS = 60

/**
 * 這筆刷卡是不是之前預先授權的請款：同一張卡、同一家店、在預估那筆當天或之後 60 天內、還是預估的支出（台幣才比）；
 * 好幾筆時取金額最接近的。使用者按過「是另一筆」的不算。
 */
fun AppData.preauthFor(p: CardPend, cardId: Long?): Txn? {
    if (p.kind != PendKind.SPEND || p.notPre || cardId == null || p.cur.isNotEmpty()) return null
    val key = shopKey(p.merchant)
    if (key.length < 2) return null
    return txns.filter { t ->
        t.est && t.type == TxType.EXPENSE && t.accountId == cardId && p.day >= t.day && p.day - t.day <= PREAUTH_DAYS &&
            t.isShop(key)
    }.minByOrNull { kotlin.math.abs(it.amount - p.amount) }
}

/** 請款那天算在哪一期帳單，比預估那筆現在算的晚就改成那一期（回傳要存的入帳月份；不用改回傳原本的） */
fun AppData.preauthBillMonth(t: Txn, chargeDay: Long): Int {
    val acc = t.accountId?.let { accMap[it] } ?: return t.billMonth
    val now = t.billMonthFor(acc) ?: return t.billMonth
    val charge = autoBillMonth(acc.statementDay, LocalDate.ofEpochDay(chargeDay))
    return if (charge > now) billCode(charge) else t.billMonth
}

/** 請款：金額改成實際請款的（優惠、手續費不動）、不再是預估；請款落在下一期就改入帳月份 */
fun AppData.settlePreauth(txnId: Long, amount: Long, chargeDay: Long): AppData =
    copy(txns = txns.map { t -> if (t.id == txnId) t.copy(amount = amount, est = false, billMonth = preauthBillMonth(t, chargeDay)) else t })

/** 還沒請款的預先授權（帳戶頁提醒用；舊的在前） */
fun AppData.preauthWaiting(accId: Long): List<Txn> =
    bookTxns.filter { it.est && it.accountId == accId && it.type == TxType.EXPENSE }.sortedWith(compareBy({ it.day }, { it.id }))

// ───────── 分期 ─────────

/**
 * 寫「分 N 期」的刷卡記成 N 期（跟記一筆的分期一樣：金額平均分、除不盡的放第 1 期，每期往後一個月，入帳月份照日期自動）。
 * 只有台幣；ids 從 startId 開始連號，第 1 期的 id 是分期的組別。
 */
fun AppData.pendInstTxns(p: CardPend, startId: Long, bookId: Long, cardId: Long, catId: Long?): List<Txn> {
    val n = p.inst.coerceIn(2, 60)
    val base = p.amount / n
    val rest = p.amount - base * n
    val start = LocalDate.ofEpochDay(p.day)
    val ps = pendShop(p, false)
    return (0 until n).map { i ->
        Txn(
            id = startId + i, bookId = bookId, type = TxType.EXPENSE, amount = base + if (i == 0) rest else 0L,
            categoryId = catId, accountId = cardId, toAccountId = null, day = start.plusMonths(i.toLong()).toEpochDay(),
            note = "", tags = listOf(CARD_NOTI_TAG), time = p.minute, instGroup = startId, instIndex = i + 1, instTotal = n,
            noti = p.mainSrc, shop = ps.shop, bankShop = ps.bankShop,
        )
    }
}

/** 這筆待確認按「記」會記成分期（台幣、有寫分幾期） */
val CardPend.isInst: Boolean get() = kind == PendKind.SPEND && inst > 1 && cur.isEmpty()
