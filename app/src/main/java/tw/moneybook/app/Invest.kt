package tw.moneybook.app

import kotlin.math.roundToLong

/** 一筆買進或賣出，歸在某個投資帳戶底下。價格用帳戶的幣別（目前一律 NT$） */
data class Trade(
    val id: Long,
    val accountId: Long,
    /** 代號，例如 0050、2330、AAPL、BTC-USD；抓價時照這個代號查 */
    val symbol: String,
    val name: String,
    val day: Long,
    val buy: Boolean,
    val qty: Double,
    val price: Double,
    /** 手續費與證交稅：買進加進成本，賣出從收入扣掉 */
    val fee: Long = 0L,
    /** 買賣時一起記的資金移動（銀行 ⇄ 投資帳戶的轉帳）；沒有連動就是 null */
    val txnId: Long? = null,
)

/** 某個代號在某一天的價格（抓到的或手動輸入的）。每個月抓幾次，當作那個月的代表 */
data class PriceSnap(val symbol: String, val day: Long, val price: Double)

/** 目前持有的部位 */
data class Position(
    val accountId: Long,
    val symbol: String,
    val name: String,
    val qty: Double,
    /** 這些持股的成本（含買進手續費；賣掉一部分時按比例扣掉） */
    val cost: Long,
    val price: Double,
    /** 這個價格是哪一天的 */
    val priceDay: Long,
) {
    val value: Long get() = (qty * price).roundToLong()
    val gain: Long get() = value - cost
    val gainPct: Double get() = if (cost > 0L) gain.toDouble() / cost else 0.0
}

/** 一個投資帳戶（或全部帳戶）的持股總覽 */
data class Portfolio(
    val positions: List<Position>,
    /** 已經賣掉的部分實現的損益（賣出收入扣手續費，減掉當初的成本） */
    val realized: Long,
) {
    val value: Long get() = positions.sumOf { it.value }
    val cost: Long get() = positions.sumOf { it.cost }
    val gain: Long get() = value - cost
}

/** 單位數量小於這個值就當作已經賣光（避免小數誤差留下 0.0000001 股） */
private const val EPS = 1e-9

/**
 * 依買賣記錄算出持股，成本用平均成本法：
 * 買進：成本 += 數量 × 價格 + 手續費；賣出：照比例扣掉成本，賣出收入扣手續費後減掉那部分成本就是已實現損益。
 * 價格用最新的一筆：手動／抓到的價格，或最後一筆成交價，看哪個日期比較新。
 */
fun AppData.portfolio(accountId: Long? = null): Portfolio {
    val latest = HashMap<String, PriceSnap>()
    for (p in prices) {
        val o = latest[p.symbol]
        if (o == null || p.day >= o.day) latest[p.symbol] = p
    }
    class Acc(var name: String) {
        var qty = 0.0
        var cost = 0.0
        var lastPrice = 0.0
        var lastDay = Long.MIN_VALUE
    }
    val m = LinkedHashMap<Pair<Long, String>, Acc>()
    var realized = 0.0
    for (t in trades.sortedWith(compareBy({ it.day }, { it.id }))) {
        if (accountId != null && t.accountId != accountId) continue
        val a = m.getOrPut(t.accountId to t.symbol) { Acc(t.name) }
        if (t.name.isNotBlank()) a.name = t.name
        a.lastPrice = t.price
        a.lastDay = t.day
        if (t.buy) {
            a.cost += t.qty * t.price + t.fee
            a.qty += t.qty
        } else {
            val sold = minOf(t.qty, a.qty)
            val out = if (a.qty > EPS) a.cost / a.qty * sold else 0.0
            realized += t.qty * t.price - t.fee - out
            a.cost -= out
            a.qty -= sold
        }
    }
    val list = m.entries.filter { it.value.qty > EPS }.map { (k, a) ->
        val snap = latest[k.second]
        // 手動／抓到的價格比最後一筆成交價新（或一樣新）才用它
        val useSnap = snap != null && snap.day >= a.lastDay
        Position(
            accountId = k.first, symbol = k.second, name = a.name,
            qty = a.qty, cost = a.cost.roundToLong(),
            price = if (useSnap) snap!!.price else a.lastPrice,
            priceDay = if (useSnap) snap!!.day else a.lastDay,
        )
    }.sortedByDescending { it.value }
    return Portfolio(list, realized.roundToLong())
}

/** 買賣金額（不含手續費），四捨五入到元 */
fun tradeAmount(qty: Double, price: Double): Long = (qty * price).roundToLong()
