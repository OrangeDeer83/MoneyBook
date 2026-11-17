package tw.moneybook.app

/*
 * 退款（使用者 11/16 選甲、11/17 確認草圖 ci-out/card_step3b.png、card_step3c.png）：
 * 記成那張卡的收入，分類是「不算收入」的（例如自動建的「退款」），收入統計看不到。
 * 選了退的是哪一筆（Txn.refundOf）：統計、預算把退的金額從那筆的分類扣回，扣在退款那天的月份（跨月也是，使用者選甲）；
 * 原本那筆是預算外，就從預算外扣。兩筆都留在明細，帳戶餘額照銀行。沒選：只是不算收入，不扣。
 * 一筆支出可以被退好幾次（部分退）。
 */

/** 統計上算哪個分類：退款算原本那筆的分類 */
fun AppData.statCat(t: Txn): Long? = if (t.isRefund) txnMap[t.refundOf]?.categoryId else t.categoryId

/** 算進支出統計（支出，或退款） */
val Txn.countsAsExpense: Boolean get() = (type == TxType.EXPENSE && !adjust) || isRefund

/** 這個收入分類不算收入（小分類沒打開就看大分類） */
fun AppData.catNoIncome(id: Long?): Boolean {
    val c = id?.let { catMap[it] } ?: return false
    if (c.kind != TxType.INCOME) return false
    return c.noIncome || (c.parentId?.let { catMap[it] }?.noIncome == true)
}

/** 自動建的退款分類 */
const val REFUND_CAT = "退款"

/**
 * 記刷卡退款用的分類：已經有叫「退款」的收入分類就用它（沒打開「不算收入」的會打開，那個分類的記錄跟著改），
 * 再來是任何一個不算收入的分類，都沒有就建一個「退款」。回傳新的資料和分類 id。
 */
fun AppData.ensureRefundCat(): Pair<AppData, Long> {
    val named = categories.firstOrNull { it.kind == TxType.INCOME && it.name == REFUND_CAT }
    if (named != null) return (if (named.noIncome) this else withCatNoIncome(named.id, true)) to named.id
    categories.firstOrNull { it.kind == TxType.INCOME && it.noIncome }?.let { return this to it.id }
    val order = categories.count { it.kind == TxType.INCOME && it.parentId == null }
    val c = Category(nextId, REFUND_CAT, "img:stat_cashback", 3, TxType.INCOME, null, order, noIncome = true)
    return copy(categories = categories + c, nextId = nextId + 1) to c.id
}

/** 改一個收入分類的「不算收入」，記在這個分類（和底下小分類）的收入跟著改；改成算收入時，退款的連結也拿掉 */
fun AppData.withCatNoIncome(catId: Long, on: Boolean): AppData {
    val cats = categories.map { if (it.id == catId) it.copy(noIncome = on) else it }
    val d = copy(categories = cats)
    return d.copy(txns = txns.map { t ->
        if (t.type != TxType.INCOME || t.categoryId == null) t
        else {
            val ni = d.catNoIncome(t.categoryId)
            if (ni == t.noIncome) t else t.copy(noIncome = ni, refundOf = if (ni) t.refundOf else 0L)
        }
    })
}

/** 這筆支出已經被退了多少 */
fun AppData.refundedOf(id: Long): Long = txns.filter { it.refundOf == id && it.type == TxType.INCOME }.sumOf { it.paid }

/** 退款往前找幾天內的支出 */
const val REFUND_DAYS = 60

/**
 * 「退的是哪一筆」可以選的：這個帳戶、退款那天往前 60 天內（含當天）的支出（不算餘額調整），
 * 金額一樣的排最前面、再來是同一家店的，其他照日期新到舊。
 */
fun AppData.refundCands(accId: Long?, day: Long, amount: Long, shop: String): List<Txn> {
    if (accId == null) return emptyList()
    val key = shopKey(shop)
    fun same(t: Txn) = key.length >= 2 && sameShop(key, shopKey(t.note.lineSequence().firstOrNull() ?: ""))
    return txns.filter { t ->
        t.accountId == accId && t.type == TxType.EXPENSE && !t.adjust && t.day <= day && t.day >= day - REFUND_DAYS
    }.sortedWith(
        compareByDescending<Txn> { amount > 0L && it.recorded == amount }.thenByDescending { same(it) }
            .thenByDescending { it.day }.thenByDescending { it.time },
    )
}

/**
 * 刷卡退款自動連到的那筆：同一張卡 60 天內、同一家店、還沒退完的支出（金額一樣的優先）；
 * 通知沒寫商店時，只認金額一樣而且只有一筆的。找不到回傳 null（只記成不算收入）。
 */
fun AppData.refundGuess(accId: Long?, day: Long, amount: Long, shop: String): Txn? {
    val cands = refundCands(accId, day, amount, shop).filter { refundedOf(it.id) + amount <= it.recorded }
    val key = shopKey(shop)
    if (key.length >= 2) return cands.firstOrNull { sameShop(key, shopKey(it.note.lineSequence().firstOrNull() ?: "")) }
    return cands.filter { it.recorded == amount }.singleOrNull()
}

/** 「11/14 全聯福利中心 $350」：退款連到的那筆的簡短說明 */
fun AppData.refundLabel(t: Txn): String {
    val d = t.date
    val name = t.note.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: t.categoryId?.let { catMap[it]?.name } ?: "支出"
    return "${d.monthValue}/${d.dayOfMonth} $name ${formatMoney(t.recorded)}"
}

/** 刪掉的支出：退款的連結拿掉（變成只是不算收入）。commit 時順便做 */
fun AppData.dropDanglingRefunds(): AppData =
    if (txns.none { it.refundOf != 0L && it.refundOf !in txnMap }) this
    else copy(txns = txns.map { if (it.refundOf != 0L && it.refundOf !in txnMap) it.copy(refundOf = 0L) else it })

/** 存一筆時：記在「不算收入」分類的收入標成不算收入；退的是哪一筆只留給這種收入（連到的要是支出） */
fun Txn.withRefund(d: AppData, link: Long): Txn {
    val ni = type == TxType.INCOME && !adjust && d.catNoIncome(categoryId)
    val of = if (ni && link != id && d.txnMap[link]?.type == TxType.EXPENSE) link else 0L
    return copy(noIncome = ni, refundOf = of)
}
