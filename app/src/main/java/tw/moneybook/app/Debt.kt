package tw.moneybook.app

/**
 * 欠款：別人先墊、我還沒轉帳的支出。這筆本體是一筆支出（記的那天就算進統計），只是 accountId 是 null、不動任何帳戶；
 * 之後還款（Txn.debtPays）時才從帳戶扣錢，不再算一次支出。
 *
 * 還的錢可以比記的多或少（像報銷）：
 * - 剛好還清：支出不變。
 * - 還得比較少：選「還沒還完」剩下的繼續欠；選「這樣就算還清」被免掉的部分從支出扣掉。
 * - 還得比較多（台幣欠款）：算還清，多付的錢加在最後一筆的支出上。
 * 結清時支出（amount）改成實際還的總額；刪掉還款會還原成記欠款時的金額（debtOrig）。
 * 外幣欠款：記的時候用預估匯率換成台幣（同外幣消費），還款時填這次還的外幣和實際扣的台幣，結清後支出改成實際台幣。
 */

/** 還款讓帳戶 a 少掉多少：外幣帳戶是實際扣掉的外幣，其他是台幣（正數，餘額要減掉它） */
fun DebtPay.flowFor(a: Account): Long = if (a.isForeign) fxAmount else amount

val Txn.isDebt: Boolean get() = debtTo.isNotBlank()

/** 這筆欠款的幣別：台幣欠款是空白，外幣欠款是外幣代碼 */
val Txn.debtCurrency: String get() = if (isFxSpend) fxSpendCur else ""

/** 欠款的總額，用欠款的單位：台幣欠款是台幣，外幣欠款是外幣最小單位 */
val Txn.debtTotalUnits: Long get() = if (isFxSpend) fxSpendAmount else debtOrig

val Txn.debtPaidUnits: Long get() = debtPays.sumOf { it.units }

/** 還欠多少（欠款的單位）；結清、或不是欠款是 0 */
val Txn.debtRemainingUnits: Long get() = if (!isDebt || debtDone) 0L else (debtTotalUnits - debtPaidUnits).coerceAtLeast(0L)

/** 還欠多少，換算成台幣（外幣欠款用記欠款時的預估匯率） */
val Txn.debtRemainingTwd: Long
    get() = if (isFxSpend) scaleRound(debtRemainingUnits, debtOrig, fxSpendAmount) else debtRemainingUnits

/** 目前還欠著的欠款（所有帳本，算總資產用），照消費日期由舊到新 */
fun AppData.openDebts(): List<Txn> = txns.filter { it.isDebt && it.debtRemainingUnits > 0L }.sortedWith(compareBy({ it.day }, { it.id }))

/** 還欠著的錢合計（台幣；外幣欠款用預估匯率換算）：總資產要先扣掉 */
fun AppData.debtOutstanding(): Long = openDebts().sumOf { it.debtRemainingTwd }

/** 以前欠過的人，最常用的排前面 */
fun AppData.debtNames(): List<String> =
    txns.filter { it.isDebt }.map { it.debtTo.trim() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

/** 還欠著的一組欠款：同一個人、同一種幣別（台幣欠款和外幣欠款分開還） */
class DebtGroup(val who: String, val currency: String, val txns: List<Txn>) {
    val remainingUnits: Long get() = txns.sumOf { it.debtRemainingUnits }
    val remainingTwd: Long get() = txns.sumOf { it.debtRemainingTwd }
}

/** 目前帳本還欠著的欠款，依人與幣別分組（欠最久的人排前面） */
fun AppData.debtGroups(): List<DebtGroup> =
    bookTxns.filter { it.isDebt && it.debtRemainingUnits > 0L }.sortedWith(compareBy({ it.day }, { it.id }))
        .groupBy { it.debtTo.trim() to it.debtCurrency }
        .map { (k, v) -> DebtGroup(k.first, k.second, v) }

/** 目前帳本已經結清的欠款（新到舊） */
fun AppData.settledDebts(): List<Txn> =
    bookTxns.filter { it.isDebt && it.debtDone }.sortedWith(compareByDescending<Txn> { it.day }.thenByDescending { it.id })

/**
 * 還一個人（同一種幣別）的欠款。units 是這次還掉多少（台幣欠款是台幣；外幣欠款是外幣最小單位，不能超過還欠的）；
 * actualTwd 是用台幣帳戶還外幣欠款時實際扣掉的台幣（台幣欠款不用填）。
 * 分配：由舊到新依序還清；台幣欠款多還的錢算在最後一筆。還得比較少時 settle＝true 代表這樣就算還清，沒還到的部分不用還。
 * 用外幣帳戶還只能還同幣別的外幣欠款；台幣帳戶可以還台幣欠款和外幣欠款。找不到欠款、金額或帳戶不對回傳 null。
 */
fun AppData.withDebtRepaid(
    who: String, currency: String, accountId: Long, day: Long, time: Int,
    units: Long, actualTwd: Long, settle: Boolean, bookId: Long = currentBook.id,
): AppData? {
    val acc = accMap[accountId] ?: return null
    val open = txns.filter { it.isDebt && it.debtTo.trim() == who.trim() && it.debtCurrency == currency && it.debtRemainingUnits > 0L && it.bookId == bookId }
        .sortedWith(compareBy({ it.day }, { it.id }))
    if (open.isEmpty() || units <= 0L) return null
    val total = open.sumOf { it.debtRemainingUnits }
    val fx = currency.isNotEmpty()
    if (fx && units > total) return null
    if (acc.isForeign && (!fx || acc.currency != currency)) return null
    if (fx && !acc.isForeign && actualTwd <= 0L) return null

    // 每一筆分到多少（欠款單位）；台幣欠款多還的算在最後一筆
    val alloc = LongArray(open.size)
    var left = units
    for (i in open.indices) {
        val a = minOf(open[i].debtRemainingUnits, left)
        alloc[i] = a
        left -= a
    }
    val extra = if (left > 0L) left else 0L
    val lastPaid = alloc.indexOfLast { it > 0L }.coerceAtLeast(0)

    // 這次實際從帳戶扣掉的台幣，各筆分到多少
    val twdPart = LongArray(open.size)
    var twdLeft = if (fx && !acc.isForeign) actualTwd else 0L
    for (i in open.indices) {
        val t = open[i]
        twdPart[i] = when {
            !fx -> alloc[i] + if (i == lastPaid) extra else 0L
            acc.isForeign -> scaleRound(alloc[i], t.debtOrig, t.fxSpendAmount)
            i == lastPaid -> twdLeft
            else -> scaleRound(actualTwd, alloc[i], units).also { twdLeft -= it }
        }
    }

    val changed = HashMap<Long, Txn>()
    for (i in open.indices) {
        val t = open[i]
        val pays = if (alloc[i] > 0L || twdPart[i] > 0L) {
            t.debtPays + DebtPay(day, accountId, twdPart[i], time, if (acc.isForeign) alloc[i] else 0L, alloc[i])
        } else t.debtPays
        val paidAll = alloc[i] >= t.debtRemainingUnits
        val done = paidAll || settle
        val next = t.copy(debtPays = pays)
        changed[t.id] = if (done) next.copy(debtDone = true, amount = pays.sumOf { it.amount }, fxPending = false) else next
    }
    return copy(txns = txns.map { changed[it.id] ?: it })
}

/** 刪掉某一筆還款：這筆欠款重新變成還沒結清，支出還原成記欠款時的金額；找不到回傳 null */
fun AppData.withDebtPayRemoved(txnId: Long, index: Int): AppData? {
    val t = txns.firstOrNull { it.id == txnId && it.isDebt } ?: return null
    if (index !in t.debtPays.indices) return null
    val next = t.copy(debtPays = t.debtPays.filterIndexed { i, _ -> i != index }, debtDone = false, amount = t.debtOrig, fxPending = t.isFxSpend)
    return copy(txns = txns.map { if (it.id == txnId) next else it })
}
