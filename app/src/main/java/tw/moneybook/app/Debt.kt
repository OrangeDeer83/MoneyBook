package tw.moneybook.app

/**
 * 欠款：別人先幫我付、我還沒還的錢。一筆支出可以一部分自己付（從帳戶扣 Txn.debtSelf）、一部分欠別人（Txn.debtItems，每個人一項），
 * 也可以同時有報銷。整筆在記的那天就算支出；欠的部分不動任何帳戶，之後還款（DebtItem.pays）時才從帳戶扣，不再算一次支出。
 *
 * 還的錢可以比欠的多或少（像報銷）：
 * - 剛好還清：支出不變。
 * - 還得比較少：選「還沒還完」剩下的繼續欠；選「這樣就算還清」被免掉的部分從支出扣掉。
 * - 還得比較多（台幣欠款）：算還清，多付的錢加在那一項上。
 * 結清的那一項算進支出的金額是實際還的總額（見 DebtItem.effective、Txn.paid）；刪掉還款就回到記的金額。
 *
 * 外幣：整筆的外幣金額在 Txn.fxSpendAmount，欠每個人的外幣在 DebtItem.units，剩下的是自己付的（刷台幣卡，等請款）。
 * 欠的部分記的時候用預估匯率換成台幣，還款時填實際扣的台幣，結清後那一項改算實際金額；自己付的部分照外幣消費請款（見 withFxSettled）。
 */

/** 還款讓帳戶 a 少掉多少：外幣帳戶是實際扣掉的外幣，其他是台幣（正數，餘額要減掉它） */
fun DebtPay.flowFor(a: Account): Long = if (a.isForeign) fxAmount else amount

/** 欠款的單位總數：外幣欠款是外幣最小單位，台幣欠款是台幣 */
val DebtItem.totalUnits: Long get() = if (units > 0L) units else amount

val DebtItem.paidUnits: Long get() = pays.sumOf { it.units }

/** 還欠多少（欠款的單位）；結清了是 0 */
val DebtItem.remainingUnits: Long get() = if (done) 0L else (totalUnits - paidUnits).coerceAtLeast(0L)

/** 還欠多少，換算成台幣（外幣欠款用記的時候的預估匯率） */
val DebtItem.remainingTwd: Long get() = if (units > 0L) scaleRound(remainingUnits, amount, units) else remainingUnits

/** 這一項算進支出的金額：還沒結清是記的金額；結清後是實際還的總額（多付、免掉都算進去） */
val DebtItem.effective: Long get() = if (done) pays.sumOf { it.amount } else amount

val Txn.isDebt: Boolean get() = debtItems.isNotEmpty()

/** 這筆的欠款幣別：外幣欠款是外幣代碼，台幣欠款是空白 */
val Txn.debtCurrency: String get() = if (isFxSpend) fxSpendCur else ""

/** 這筆還欠別人的錢（台幣，外幣用預估匯率） */
val Txn.debtRemainingTwd: Long get() = debtItems.sumOf { it.remainingTwd }

/** 欠款一開始記的合計（台幣） */
val Txn.debtTotal: Long get() = debtItems.sumOf { it.amount }

/** 某一筆支出裡的某一個欠款對象 */
class DebtRef(val t: Txn, val index: Int) {
    val item: DebtItem get() = t.debtItems[index]
    val who: String get() = item.who.trim()
    val currency: String get() = t.debtCurrency
    val key: Pair<Long, Int> get() = t.id to index
}

private fun List<Txn>.debtRefs(): List<DebtRef> =
    sortedWith(compareBy({ it.day }, { it.id })).flatMap { t -> t.debtItems.indices.map { DebtRef(t, it) } }

/** 還欠著的欠款（所有帳本，算總資產用），照消費日期由舊到新 */
fun AppData.openDebts(): List<DebtRef> = txns.filter { it.isDebt }.debtRefs().filter { it.item.remainingUnits > 0L }

/** 還欠著的錢合計（台幣；外幣欠款用預估匯率換算）：總資產要先扣掉 */
fun AppData.debtOutstanding(): Long = openDebts().sumOf { it.item.remainingTwd }

/** 以前欠過的人，最常用的排前面 */
fun AppData.debtNames(): List<String> =
    txns.flatMap { t -> t.debtItems.map { it.who.trim() } }.filter { it.isNotEmpty() }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

/** 還欠著的一組欠款：同一個人、同一種幣別（台幣欠款和外幣欠款分開還） */
class DebtGroup(val who: String, val currency: String, val refs: List<DebtRef>) {
    val remainingUnits: Long get() = refs.sumOf { it.item.remainingUnits }
    val remainingTwd: Long get() = refs.sumOf { it.item.remainingTwd }
}

/** 目前帳本還欠著的欠款，依人與幣別分組（欠最久的人排前面） */
fun AppData.debtGroups(): List<DebtGroup> =
    bookTxns.filter { it.isDebt }.debtRefs().filter { it.item.remainingUnits > 0L }
        .groupBy { it.who to it.currency }
        .map { (k, v) -> DebtGroup(k.first, k.second, v) }

/** 目前帳本已經結清的欠款（新到舊） */
fun AppData.settledDebts(): List<DebtRef> = bookTxns.filter { it.isDebt }.debtRefs().filter { it.item.done }.reversed()

/**
 * 還一個人（同一種幣別）的欠款。units 是這次還掉多少（台幣欠款是台幣；外幣欠款是外幣最小單位，不能超過還欠的）；
 * actualTwd 是用台幣帳戶還外幣欠款時實際扣掉的台幣（台幣欠款不用填）。only 不是 null 時只還勾選的那幾項（txn id 到第幾項）。
 * 分配：由舊到新依序還清；台幣欠款多還的錢算在最後一項。還得比較少時 settle＝true 代表這樣就算還清，沒還到的部分不用還。
 * 用外幣帳戶還只能還同幣別的外幣欠款；台幣帳戶可以還台幣欠款和外幣欠款。找不到欠款、金額或帳戶不對回傳 null。
 */
fun AppData.withDebtRepaid(
    who: String, currency: String, accountId: Long, day: Long, time: Int,
    units: Long, actualTwd: Long, settle: Boolean, bookId: Long = currentBook.id,
    only: Set<Pair<Long, Int>>? = null,
): AppData? {
    val acc = accMap[accountId] ?: return null
    val open = txns.filter { it.isDebt && it.bookId == bookId }.debtRefs()
        .filter { it.who == who.trim() && it.currency == currency && it.item.remainingUnits > 0L && (only == null || it.key in only) }
    if (open.isEmpty() || units <= 0L) return null
    val total = open.sumOf { it.item.remainingUnits }
    val fx = currency.isNotEmpty()
    if (fx && units > total) return null
    if (acc.isForeign && (!fx || acc.currency != currency)) return null
    if (fx && !acc.isForeign && actualTwd <= 0L) return null

    // 每一項分到多少（欠款單位）；台幣欠款多還的算在最後一項
    val alloc = LongArray(open.size)
    var left = units
    for (i in open.indices) {
        val a = minOf(open[i].item.remainingUnits, left)
        alloc[i] = a
        left -= a
    }
    val extra = if (left > 0L) left else 0L
    val lastPaid = alloc.indexOfLast { it > 0L }.coerceAtLeast(0)

    // 這次實際從帳戶扣掉的台幣，各項分到多少
    val twdPart = LongArray(open.size)
    var twdLeft = if (fx && !acc.isForeign) actualTwd else 0L
    for (i in open.indices) {
        val it = open[i].item
        twdPart[i] = when {
            !fx -> alloc[i] + if (i == lastPaid) extra else 0L
            acc.isForeign -> scaleRound(alloc[i], it.amount, it.units)
            i == lastPaid -> twdLeft
            else -> scaleRound(actualTwd, alloc[i], units).also { v -> twdLeft -= v }
        }
    }

    val newItems = HashMap<Long, MutableList<DebtItem>>()
    for (i in open.indices) {
        val r = open[i]
        val list = newItems.getOrPut(r.t.id) { r.t.debtItems.toMutableList() }
        val it = list[r.index]
        val pays = if (alloc[i] > 0L || twdPart[i] > 0L) it.pays + DebtPay(day, accountId, twdPart[i], time, if (acc.isForeign) alloc[i] else 0L, alloc[i]) else it.pays
        val done = alloc[i] >= it.remainingUnits || settle
        list[r.index] = it.copy(pays = pays, done = done)
    }
    return copy(txns = txns.map { t -> newItems[t.id]?.let { t.copy(debtItems = it) } ?: t })
}

/** 一次還款裡的一種幣別（還款頁每種幣別一區，各自的帳戶、金額；見 withDebtRepaid） */
class DebtRepayPart(
    val currency: String, val accountId: Long, val units: Long, val actualTwd: Long, val settle: Boolean,
    val only: Set<Pair<Long, Int>>? = null,
)

/** 一次還好幾種幣別（同一天同一時間）；任何一種還不了就整個不算，回傳 null */
fun AppData.withDebtsRepaid(who: String, parts: List<DebtRepayPart>, day: Long, time: Int, bookId: Long = currentBook.id): AppData? {
    if (parts.isEmpty()) return null
    var out = this
    for (p in parts) out = out.withDebtRepaid(who, p.currency, p.accountId, day, time, p.units, p.actualTwd, p.settle, bookId, p.only) ?: return null
    return out
}

/** 刪掉某一項欠款的某一筆還款：那一項重新變成還沒結清（回到記的金額）；找不到回傳 null */
fun AppData.withDebtPayRemoved(txnId: Long, itemIndex: Int, payIndex: Int): AppData? {
    val t = txns.firstOrNull { it.id == txnId && it.isDebt } ?: return null
    val item = t.debtItems.getOrNull(itemIndex) ?: return null
    if (payIndex !in item.pays.indices) return null
    val next = item.copy(pays = item.pays.filterIndexed { i, _ -> i != payIndex }, done = false)
    return copy(txns = txns.map { if (it.id == txnId) it.copy(debtItems = it.debtItems.mapIndexed { i, x -> if (i == itemIndex) next else x }) else it })
}

/**
 * 記一筆存檔時的欠款分配：外幣消費時，依每個人欠的台幣比例分外幣（最後一個人補零頭，欠款合計等於整筆時外幣也剛好分完）。
 * selfTwd 是自己付的那一份（整筆 − 欠款合計，不會是負的）。
 */
fun splitDebtUnits(items: List<DebtItem>, totalTwd: Long, fxTotal: Long): List<DebtItem> {
    if (fxTotal <= 0L || items.isEmpty()) return items.map { it.copy(units = 0L) }
    val debtTwd = items.sumOf { it.amount }
    val debtUnits = if (debtTwd >= totalTwd) fxTotal else scaleRound(fxTotal, debtTwd, totalTwd)
    var left = debtUnits
    return items.mapIndexed { i, it ->
        val u = if (i == items.lastIndex) left else scaleRound(debtUnits, it.amount, debtTwd).also { v -> left -= v }
        it.copy(units = u)
    }
}

/**
 * 互相抵銷時，抵的台幣 x 怎麼分到欠款上：先抵台幣欠款，剩下的由舊到新抵外幣欠款（外幣欠款用預估台幣算）。
 * 回傳每一項分到的台幣（和 debts 對齊；debts 要先照日期排好）。
 */
fun offsetDebtTwd(x: Long, debts: List<DebtRef>): LongArray {
    val out = LongArray(debts.size)
    var left = x
    for (fxPass in listOf(false, true)) {
        for (i in debts.indices) {
            val r = debts[i]
            if ((r.currency.isNotEmpty()) != fxPass || left <= 0L) continue
            val a = minOf(r.item.remainingTwd, left)
            out[i] = a
            left -= a
        }
    }
    return out
}

/** 互相抵銷時每種外幣分到的台幣（照第一次出現的順序），只列有分到的 */
fun offsetFxTwd(x: Long, debts: List<DebtRef>): Map<String, Long> {
    val twd = offsetDebtTwd(x, debts)
    val out = LinkedHashMap<String, Long>()
    debts.forEachIndexed { i, r -> if (r.currency.isNotEmpty() && twd[i] > 0L) out[r.currency] = (out[r.currency] ?: 0L) + twd[i] }
    return out
}

/** 外幣欠款分到 twd 台幣時，照記的時候的預估匯率要抵掉多少外幣（由舊到新，最後一項部分抵） */
fun offsetFxDefaultUnits(twd: Long, refs: List<DebtRef>): Long {
    var left = twd
    var units = 0L
    for (r in refs) {
        if (left <= 0L) break
        val rem = r.item.remainingTwd
        val a = minOf(rem, left)
        left -= a
        units += if (a >= rem) r.item.remainingUnits else scaleRound(r.item.remainingUnits, a, rem)
    }
    return units
}

/**
 * 互相抵銷：同一個人，他欠我的（報銷還沒收的）和我欠他的（欠款還沒還的）對沖，不動任何帳戶、不算收入或支出。
 * reimbKeys：要抵的報銷（txn id 到第幾個報銷對象）；debtKeys：要抵的欠款（txn id 到第幾項）。
 * 抵的金額是兩邊勾選的合計比較小的那一個（外幣欠款用預估台幣比）；報銷由舊到新分配，欠款先抵台幣、再由舊到新抵外幣（見 offsetDebtTwd）。
 * 外幣欠款：fxUnits 是每種外幣這次抵掉多少（最小單位；沒給就照預估匯率算），不能超過勾選的還欠的。
 * 那幾項記一筆台幣是抵掉的台幣、外幣是抵掉的外幣的紀錄，所以還清後支出＝抵掉的台幣＋之後實際還的台幣。
 * 抵完的那一項就結清（報銷是收齊、欠款是還清）。兩邊的紀錄用同一個編號（用掉一個 nextId），刪除時一起刪（見 withOffsetRemoved）。抵不了回傳 null。
 */
fun AppData.withOffset(
    who: String, reimbKeys: Set<Pair<Long, Int>>, debtKeys: Set<Pair<Long, Int>>, day: Long, time: Int,
    fxUnits: Map<String, Long> = emptyMap(),
): AppData? {
    val name = who.trim()
    val byDay = txns.sortedWith(compareBy({ it.day }, { it.id }))
    val rRefs = byDay.flatMap { t -> t.items.indices.map { t to it } }
        .filter { (t, i) -> t.type == TxType.EXPENSE && (t.id to i) in reimbKeys && t.items[i].who.trim() == name && t.items[i].remaining > 0L }
    val dRefs = byDay.filter { it.isDebt }.debtRefs()
        .filter { it.key in debtKeys && it.who == name && it.item.remainingUnits > 0L }
    val x = minOf(rRefs.sumOf { (t, i) -> t.items[i].remaining }, dRefs.sumOf { it.item.remainingTwd })
    if (x <= 0L) return null
    val twd = offsetDebtTwd(x, dRefs)

    // 每一項欠款這次抵掉多少（欠款單位）和多少台幣
    val units = LongArray(dRefs.size)
    val twdOf = LongArray(dRefs.size)
    dRefs.forEachIndexed { i, r -> if (r.currency.isEmpty()) { units[i] = twd[i]; twdOf[i] = twd[i] } }
    for ((c, tc) in offsetFxTwd(x, dRefs)) {
        val idx = dRefs.indices.filter { dRefs[it].currency == c }
        val refs = idx.map { dRefs[it] }
        val uc = fxUnits[c] ?: offsetFxDefaultUnits(tc, refs)
        if (uc <= 0L || uc > refs.sumOf { it.item.remainingUnits }) return null
        var leftU = uc
        val got = ArrayList<Int>()
        for (i in idx) {
            if (leftU <= 0L) break
            val a = minOf(dRefs[i].item.remainingUnits, leftU)
            units[i] = a
            leftU -= a
            got.add(i)
        }
        var leftT = tc
        got.forEachIndexed { n, i ->
            twdOf[i] = if (n == got.lastIndex) leftT else scaleRound(tc, units[i], uc).also { v -> leftT -= v }
        }
    }

    val oid = nextId
    val newReimb = HashMap<Long, MutableList<ReimbItem>>()
    var left = x
    for ((t, i) in rRefs) {
        if (left <= 0L) break
        val list = newReimb.getOrPut(t.id) { t.items.toMutableList() }
        val it = list[i]
        val a = minOf(it.remaining, left)
        left -= a
        val pays = it.pays + ReimbPay(day, null, a, time, 0L, oid)
        list[i] = it.copy(pays = pays, closed = it.closed || pays.sumOf { p -> p.amount } >= it.amount)
    }
    val newDebt = HashMap<Long, MutableList<DebtItem>>()
    dRefs.forEachIndexed { i, r ->
        if (units[i] <= 0L) return@forEachIndexed
        val list = newDebt.getOrPut(r.t.id) { r.t.debtItems.toMutableList() }
        val it = list[r.index]
        list[r.index] = it.copy(pays = it.pays + DebtPay(day, null, twdOf[i], time, 0L, units[i], oid), done = it.remainingUnits - units[i] <= 0L)
    }
    return copy(
        txns = txns.map { t ->
            var n = t
            newReimb[t.id]?.let { n = n.withItems(it) }
            newDebt[t.id]?.let { n = n.copy(debtItems = it) }
            n
        },
        nextId = nextId + 1,
    )
}

/** 刪掉一次互相抵銷：兩邊的紀錄一起刪，抵完而結清的報銷、欠款重新變成還沒結清 */
fun AppData.withOffsetRemoved(offsetId: Long): AppData? {
    if (offsetId == 0L) return null
    var found = false
    val out = txns.map { t ->
        var n = t
        if (t.items.any { i -> i.pays.any { it.offsetId == offsetId } }) {
            found = true
            n = n.withItems(t.items.map { i ->
                if (i.pays.none { it.offsetId == offsetId }) i
                else i.pays.filter { it.offsetId != offsetId }.let { ps -> i.copy(pays = ps, closed = i.closed && ps.sumOf { it.amount } >= i.amount) }
            })
        }
        if (t.debtItems.any { i -> i.pays.any { it.offsetId == offsetId } }) {
            found = true
            n = n.copy(debtItems = t.debtItems.map { i ->
                if (i.pays.none { it.offsetId == offsetId }) i else i.copy(pays = i.pays.filter { it.offsetId != offsetId }, done = false)
            })
        }
        n
    }
    return if (found) copy(txns = out) else null
}
