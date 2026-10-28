package tw.moneybook.app

/**
 * 分帳結清：照分帳軟體（Splitwise 之類）的結論，把勾選的報銷（別人欠我）和欠款（我欠別人）一次結清。
 * 收到的錢不一定是欠我的人給的（例如 Amy 欠我、但結論是 Bob 給我），所以實際轉的錢記在 [Settle.moves]，
 * 帳戶只照它動；每一項報銷／欠款加一筆不動帳戶的收款／還款（offsetId＝這次結清的 id），刪掉這次結清就一起拿掉。
 */
data class SettleMove(
    /** true＝我收到，false＝我付出 */
    val receive: Boolean,
    /** 對方是誰（只是紀錄用） */
    val who: String,
    /** 台幣 */
    val amount: Long,
    /** 錢進出哪個帳戶（台幣帳戶） */
    val accountId: Long?,
)

data class Settle(
    val id: Long,
    val bookId: Long,
    val day: Long,
    val time: Int,
    val moves: List<SettleMove>,
    /** 這次結清時才結案的報銷項目（"r<記錄 id>_<第幾項>"）、才結清的欠款項目（"d<記錄 id>_<第幾項>"）；刪除時恢復成沒結清 */
    val closed: List<String> = emptyList(),
) {
    val received: Long get() = moves.filter { it.receive }.sumOf { it.amount }
    val paidOut: Long get() = moves.filter { !it.receive }.sumOf { it.amount }
    /** 實際收到－付出 */
    val net: Long get() = received - paidOut
}

/** 實際和照理說的不一樣時：SELF＝差的算我的（全部結清，差額算進我的支出／收入）；KEEP＝繼續欠（差的留在指定的項目上） */
enum class SettleDiff { SELF, KEEP }

/** 一筆報銷或欠款項目：記錄 id 與第幾項 */
data class ItemKey(val txnId: Long, val index: Int) {
    fun reimbKey(): String = "r${txnId}_$index"
    fun debtKey(): String = "d${txnId}_$index"
}

/** 這次結清在帳戶 a 動了多少（收到是正、付出是負） */
fun Settle.flowFor(accountId: Long): Long = moves.filter { it.accountId == accountId }.sumOf { if (it.receive) it.amount else -it.amount }

/**
 * 「繼續欠」時差的留在某個人的哪幾筆帳、各留多少：金額大的先放滿，放到夠為止（一筆就夠時就是最大那一筆，用最少筆）。
 * items 是（帳, 還沒結清的金額），只回傳有留到的；全部放滿還不夠時就是全部。
 */
fun <K> keepSpread(items: List<Pair<K, Long>>, amount: Long): List<Pair<K, Long>> {
    var left = amount
    return items.sortedByDescending { it.second }.mapNotNull { (k, v) ->
        if (left <= 0L || v <= 0L) null else minOf(v, left).also { left -= it }.let { k to it }
    }
}

/** 某個 offsetId 是不是分帳結清（不是互相抵銷） */
fun AppData.settleOf(offsetId: Long): Settle? = if (offsetId == 0L) null else settles.firstOrNull { it.id == offsetId }

/** 照理說的淨額：勾選的報銷還沒收的（台幣）－勾選的欠款還欠的（台幣，外幣用預估），正的是我會收到 */
fun AppData.settleExpected(reimbKeys: List<ItemKey>, debtKeys: List<ItemKey>): Long {
    val tm = txns.associateBy { it.id }
    val r = reimbKeys.sumOf { k -> tm[k.txnId]?.items?.getOrNull(k.index)?.remaining ?: 0L }
    val dd = debtKeys.sumOf { k -> tm[k.txnId]?.debtItems?.getOrNull(k.index)?.remainingTwd ?: 0L }
    return r - dd
}

/**
 * 依分帳結論結清。reimbKeys／debtKeys 是勾選的項目（要還有沒結清的），moves 是實際轉的錢。
 * 實際淨額和照理說的一樣：全部結清。不一樣時照 mode：
 * - SELF：全部結清。少收：從最新的報銷開始少收、那幾項結案（少收的算我的支出），還不夠就算我多還了最新的欠款；
 *         多收：從最新的欠款開始少還、那幾項算還清（免掉的從支出扣掉），還多就算最新那項報銷多收（算收入）。
 * - KEEP：差的留在 keep 指定的項目、各留指定的金額（少收留在報銷、多收留在欠款），加起來要剛好是差額，其他全部結清。
 * 項目不對、帳戶不對、KEEP 留不下時回傳 null。
 */
fun AppData.withSettle(
    reimbKeys: List<ItemKey>, debtKeys: List<ItemKey>, moves: List<SettleMove>, day: Long, time: Int,
    mode: SettleDiff = SettleDiff.SELF, keep: Map<ItemKey, Long> = emptyMap(), bookId: Long = currentBook.id,
): AppData? {
    if (reimbKeys.isEmpty() && debtKeys.isEmpty()) return null
    if (moves.any { m -> m.amount <= 0L || m.accountId?.let { id -> accMap[id]?.takeIf { !it.isForeign } } == null }) return null
    val tm = txns.associateBy { it.id }
    class R(val k: ItemKey, val t: Txn, val item: ReimbItem) { var pay = item.remaining; var close = false }
    class D(val k: ItemKey, val t: Txn, val item: DebtItem) { var pay = item.remainingTwd; var done = false }
    val rs = reimbKeys.distinct().map { k ->
        val t = tm[k.txnId]?.takeIf { it.bookId == bookId } ?: return null
        val it = t.items.getOrNull(k.index)?.takeIf { it.remaining > 0L } ?: return null
        R(k, t, it)
    }
    val ds = debtKeys.distinct().map { k ->
        val t = tm[k.txnId]?.takeIf { it.bookId == bookId } ?: return null
        val it = t.debtItems.getOrNull(k.index)?.takeIf { it.remainingUnits > 0L } ?: return null
        D(k, t, it)
    }
    // 最新的在前（從最新的帳開始扣）
    val rNew = rs.sortedWith(compareByDescending<R> { it.t.day }.thenByDescending { it.t.id }.thenByDescending { it.k.index })
    val dNew = ds.sortedWith(compareByDescending<D> { it.t.day }.thenByDescending { it.t.id }.thenByDescending { it.k.index })
    val expected = rs.sumOf { it.pay } - ds.sumOf { it.pay }
    val diff = moves.sumOf { if (it.receive) it.amount else -it.amount } - expected

    fun reduceR(list: List<R>, amount: Long, close: Boolean): Long {
        var left = amount
        for (r in list) {
            if (left <= 0L) break
            val cut = minOf(r.pay, left)
            r.pay -= cut
            left -= cut
            if (close && cut > 0L) r.close = true
        }
        return left
    }
    fun reduceD(list: List<D>, amount: Long, done: Boolean): Long {
        var left = amount
        for (x in list) {
            if (left <= 0L) break
            val cut = minOf(x.pay, left)
            x.pay -= cut
            left -= cut
            if (done && cut > 0L) x.done = true
        }
        return left
    }
    when {
        diff < 0L && mode == SettleDiff.SELF -> {
            // 少收：最新的報銷少收、結案；還不夠就算多還了最新的欠款
            val left = reduceR(rNew, -diff, true)
            if (left > 0L) {
                val last = dNew.firstOrNull() ?: return null
                last.pay += left
                last.done = true
            }
        }
        diff > 0L && mode == SettleDiff.SELF -> {
            // 多收：最新的欠款少還、算還清（免掉）；還多就算最新那項報銷多收
            val left = reduceD(dNew, diff, true)
            if (left > 0L) {
                val last = rNew.firstOrNull() ?: return null
                last.pay += left
                last.close = true
            }
        }
        diff != 0L -> {
            // 每一項留多少就少收／少還多少；項目要在對的那一邊，金額不能超過它還沒結清的，加起來要剛好
            if (keep.values.sum() != kotlin.math.abs(diff) || keep.values.any { it <= 0L }) return null
            for ((k, amt) in keep) {
                if (diff < 0L) {
                    val r = rs.firstOrNull { it.k == k }?.takeIf { it.pay >= amt } ?: return null
                    r.pay -= amt
                } else {
                    val x = ds.firstOrNull { it.k == k }?.takeIf { it.pay >= amt } ?: return null
                    x.pay -= amt
                }
            }
        }
    }

    val id = nextId
    val rBy = rs.associateBy { it.k }
    val dBy = ds.associateBy { it.k }
    val closed = rs.filter { it.close && !it.item.closed }.map { it.k.reimbKey() } + ds.filter { it.done && !it.item.done }.map { it.k.debtKey() }
    val newTxns = txns.map { t ->
        val touchR = t.items.indices.any { ItemKey(t.id, it) in rBy }
        val touchD = t.debtItems.indices.any { ItemKey(t.id, it) in dBy }
        if (!touchR && !touchD) return@map t
        var nt = t
        if (touchR) {
            nt = nt.withItems(t.items.mapIndexed { i, item ->
                val r = rBy[ItemKey(t.id, i)] ?: return@mapIndexed item
                val pays = if (r.pay > 0L) item.pays + ReimbPay(day, null, r.pay, time, 0L, id) else item.pays
                // 收齊了也算結案（和一般收款一樣）
                item.copy(pays = pays, closed = item.closed || r.close || pays.sumOf { it.amount } >= item.amount)
            })
        }
        if (touchD) {
            nt = nt.copy(debtItems = t.debtItems.mapIndexed { i, item ->
                val x = dBy[ItemKey(t.id, i)] ?: return@mapIndexed item
                if (x.pay <= 0L) return@mapIndexed if (x.done) item.copy(done = true) else item
                val remTwd = item.remainingTwd
                // 沖掉的欠款單位：還清（或多還）就是剩下的全部，部分就照比例（外幣用記的時候的預估匯率）
                val units = when {
                    x.pay >= remTwd -> item.remainingUnits
                    item.units > 0L -> scaleRound(x.pay, item.units, item.amount).coerceAtMost(item.remainingUnits)
                    else -> x.pay
                }
                item.copy(pays = item.pays + DebtPay(day, null, x.pay, time, 0L, units, id), done = item.done || x.done)
            })
        }
        nt
    }
    return copy(txns = newTxns, settles = settles + Settle(id, bookId, day, time, moves, closed), nextId = id + 1)
}

/** 刪掉一次分帳結清：拿掉它的收款／還款，當時才結案／結清的項目恢復成沒結清，帳戶跟著回去 */
fun AppData.withSettleRemoved(id: Long): AppData? {
    val s = settles.firstOrNull { it.id == id } ?: return null
    val closed = s.closed.toSet()
    val newTxns = txns.map { t ->
        val hasR = t.items.any { i -> i.pays.any { it.offsetId == id } } || t.items.indices.any { "r${t.id}_$it" in closed }
        val hasD = t.debtItems.any { i -> i.pays.any { it.offsetId == id } } || t.debtItems.indices.any { "d${t.id}_$it" in closed }
        var nt = t
        if (hasR) nt = nt.withItems(t.items.mapIndexed { i, item ->
            val ps = item.pays.filter { it.offsetId != id }
            // 這次結清前還沒結案（才會被勾來結清）：拿掉之後沒收齊就恢復成沒結案
            item.copy(pays = ps, closed = item.closed && "r${t.id}_$i" !in closed && ps.sumOf { it.amount } >= item.amount)
        })
        if (hasD) nt = nt.copy(debtItems = t.debtItems.mapIndexed { i, item ->
            item.copy(pays = item.pays.filter { it.offsetId != id }, done = item.done && "d${t.id}_$i" !in closed)
        })
        nt
    }
    return copy(txns = newTxns, settles = settles.filter { it.id != id })
}
