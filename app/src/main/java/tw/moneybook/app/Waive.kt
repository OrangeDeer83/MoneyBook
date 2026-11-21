package tw.moneybook.app

/**
 * 免除：別人不用還我（報銷不追了），或我不用還別人（對方說不用還）。不動任何帳戶，沒有錢進出。
 * - 別人不用還我：那幾項報銷結案（closed），沒收到的錢算回自己的支出（跟收款時選「不追了」一樣）。
 * - 我不用還別人：那幾項欠款結清（done），不用還的錢從那筆支出扣掉（跟還款時選「這樣就算還清了」一樣）。
 * 免除留一筆紀錄（AppData.waives），刪掉時那幾項恢復成沒結清。
 */
data class Waive(
    val id: Long,
    val bookId: Long,
    val day: Long,
    val time: Int,
    val who: String,
    /** true＝別人不用還我（報銷）；false＝我不用還別人（欠款） */
    val owedToMe: Boolean,
    /** 免除了哪幾項（"r<記錄 id>_<第幾項>" 或 "d<記錄 id>_<第幾項>"，跟 Settle.closed 一樣）→ 免除的金額（報銷、台幣欠款是台幣，外幣欠款是外幣最小單位） */
    val items: Map<String, Long>,
    /** 原因（選填） */
    val note: String = "",
)

fun reimbKey(txnId: Long, index: Int) = "r${txnId}_$index"
fun debtKey(txnId: Long, index: Int) = "d${txnId}_$index"

/** 免除的金額換成台幣（外幣欠款用記的時候的預估匯率） */
fun AppData.waiveTwd(w: Waive): Long = w.items.entries.sumOf { (k, v) ->
    if (!k.startsWith("d")) v else {
        val (tid, i) = k.drop(1).split('_').let { it[0].toLong() to it[1].toInt() }
        val item = txnMap[tid]?.debtItems?.getOrNull(i)
        if (item != null && item.units > 0L) scaleRound(v, item.amount, item.units) else v
    }
}

/**
 * 免除 keys 這幾項（都要是 who 的、還沒結清的）。owedToMe＝true 只收報銷的 key，false 只收欠款的 key。
 * 不成立（沒有勾、勾到已結清的、別人的）回傳 null。
 */
fun AppData.withWaive(
    who: String, owedToMe: Boolean, keys: List<String>, day: Long, time: Int, note: String = "", bookId: Long = currentBook.id,
): AppData? {
    if (keys.isEmpty()) return null
    val want = keys.toSet()
    val amounts = LinkedHashMap<String, Long>()
    val newTxns = txns.map { t ->
        if (owedToMe) {
            if (t.items.indices.none { reimbKey(t.id, it) in want }) return@map t
            t.withItems(t.items.mapIndexed { i, item ->
                if (reimbKey(t.id, i) !in want) item
                else {
                    if (item.closed || item.remaining <= 0L || item.who.trim() != who) return null
                    amounts[reimbKey(t.id, i)] = item.remaining
                    item.copy(closed = true)
                }
            })
        } else {
            if (t.debtItems.indices.none { debtKey(t.id, it) in want }) return@map t
            t.copy(debtItems = t.debtItems.mapIndexed { i, item ->
                if (debtKey(t.id, i) !in want) item
                else {
                    if (item.done || item.remainingUnits <= 0L || item.who.trim() != who) return null
                    amounts[debtKey(t.id, i)] = item.remainingUnits
                    item.copy(done = true)
                }
            })
        }
    }
    if (amounts.size != want.size) return null
    val w = Waive(nextId, bookId, day, time, who, owedToMe, amounts, note.trim())
    return copy(txns = newTxns, waives = waives + w, nextId = nextId + 1)
}

/** 刪掉一次免除：那幾項恢復成沒結清（報銷已經收齊的照樣結案） */
fun AppData.withWaiveRemoved(id: Long): AppData? {
    val w = waives.firstOrNull { it.id == id } ?: return null
    val keys = w.items.keys
    val newTxns = txns.map { t ->
        val hasR = t.items.indices.any { reimbKey(t.id, it) in keys }
        val hasD = t.debtItems.indices.any { debtKey(t.id, it) in keys }
        var nt = t
        if (hasR) nt = nt.withItems(t.items.mapIndexed { i, item ->
            if (reimbKey(t.id, i) in keys) item.copy(closed = item.received >= item.amount) else item
        })
        if (hasD) nt = nt.copy(debtItems = t.debtItems.mapIndexed { i, item ->
            if (debtKey(t.id, i) in keys) item.copy(done = false) else item
        })
        nt
    }
    return copy(txns = newTxns, waives = waives.filter { it.id != id })
}
