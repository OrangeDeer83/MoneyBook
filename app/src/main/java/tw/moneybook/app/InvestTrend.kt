package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * 投資成效折線的一個點（某一天結束時）：value＝現值、cost＝還持有部分的成本（平均成本法，和持股總覽的「成本」一樣），
 * flow＝到這天為止投入的錢（買進含手續費、減掉賣出拿回來的），用來算一段期間賺賠多少。全部是台幣。
 */
data class InvestPoint(val day: Long, val value: Long, val cost: Long, val flow: Long) {
    val gain: Long get() = value - cost
    /** 報酬率（未實現損益 ÷ 成本）；沒有持股是 null */
    val pct: Double? get() = if (cost > 0L) gain.toDouble() / cost else null
}

/** 這段期間賺賠多少：現值的變化扣掉這段期間新投入的錢（賣掉的已實現損益、手續費都算在裡面） */
fun periodGain(points: List<InvestPoint>): Long =
    if (points.size < 2) 0L else (points.last().value - points.first().value) - (points.last().flow - points.first().flow)

/** 投資成效的範圍：1＝1 個月、3＝3 個月、12＝1 年、0＝全部 */
object InvestRanges {
    val all: List<Pair<Int, String>> = listOf(1 to "1 個月", 3 to "3 個月", 12 to "1 年", 0 to "全部")
    fun label(r: Int): String = all.firstOrNull { it.first == r }?.second ?: ""
}

/** 預設範圍：第一次買進已經超過 3 個月就看 3 個月，不到就看 1 個月 */
fun defaultInvestRange(first: Long, today: Long): Int =
    if (first <= LocalDate.ofEpochDay(today).minusMonths(3).toEpochDay()) 3 else 1

/** 一個點是一天、一週還是一個月（標籤和資訊框的寫法不同） */
enum class InvestStep { DAY, WEEK, MONTH }

/**
 * 折線要算哪幾天（由舊到新，最後一點一定是今天）：1 個月、3 個月每天一點；1 年每週一點（從今天往回每 7 天）；
 * 全部看第一次買進到今天多久：3 個月內每天、一年多內每週、再長每個月底一點。不會早於第一次買進那天。
 */
fun investDays(range: Int, first: Long, today: Long): Pair<List<Long>, InvestStep> {
    val t = LocalDate.ofEpochDay(today)
    fun daily(from: Long) = (maxOf(from, first)..today).toList() to InvestStep.DAY
    fun weekly(from: Long): Pair<List<Long>, InvestStep> {
        val lo = maxOf(from, first)
        return generateSequence(today) { it - 7 }.takeWhile { it >= lo }.toList().reversed() to InvestStep.WEEK
    }
    return when (range) {
        1 -> daily(t.minusMonths(1).toEpochDay())
        3 -> daily(t.minusMonths(3).toEpochDay())
        12 -> weekly(t.minusYears(1).toEpochDay())
        else -> {
            val span = today - first
            when {
                span <= 92 -> daily(first)
                span <= 400 -> weekly(first)
                else -> {
                    // 第一次買進那個月到上個月，每個月底一點，最後加今天
                    val ends = generateSequence(YearMonth.from(LocalDate.ofEpochDay(first))) { it.plusMonths(1) }
                        .takeWhile { it < YearMonth.from(t) }.map { it.atEndOfMonth().toEpochDay() }.toList()
                    (ends + today) to InvestStep.MONTH
                }
            }
        }
    }
}

/** list 依日期排好；回傳日期 <= day 的最後一筆 */
private fun <T> lastAtOrBefore(list: List<T>?, day: Long, dayOf: (T) -> Long): T? {
    if (list.isNullOrEmpty()) return null
    var lo = 0
    var hi = list.size - 1
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (dayOf(list[mid]) <= day) { ans = mid; lo = mid + 1 } else hi = mid - 1
    }
    return if (ans < 0) null else list[ans]
}

/**
 * 投資成效：days 每一天結束時的現值與成本（accountIds 是 null＝所有帳戶；symbol 有填就只算那一檔）。
 * 股數與成本照買賣記錄、平均成本法（和 portfolio 一樣）；價格用那天（含）以前最近的一筆價格記錄或成交價，看哪個比較新；
 * 外幣用那天以前最近的歷史匯率或成交匯率。今天（含）以後的點和持股總覽一樣用最新價格、目前匯率，最後一點會等於持股市值。
 */
fun AppData.investSeries(
    days: List<Long>, accountIds: Set<Long>? = null, symbol: String? = null,
    today: Long = LocalDate.now().toEpochDay(),
): List<InvestPoint> {
    val ts = trades.filter { (accountIds == null || it.accountId in accountIds) && (symbol == null || it.symbol == symbol) }
        .sortedWith(compareBy({ it.day }, { it.id }))
    val snaps = prices.groupBy { it.symbol to it.currency }.mapValues { (_, v) -> v.sortedBy { it.day } }
    val rateList = (rateHist + rates).groupBy { it.code }.mapValues { (_, v) -> v.sortedBy { it.day } }
    class St(val symbol: String) {
        var currency = ""
        var qty = 0.0
        var cost = 0.0
        var lastPrice = 0.0
        var lastDay = Long.MIN_VALUE
        var lastRate = 1.0
    }
    val st = LinkedHashMap<Pair<Long, String>, St>()
    var flow = 0.0
    var k = 0
    val now = HashMap<String, Double?>()
    return days.sorted().map { day ->
        while (k < ts.size && ts[k].day <= day) {
            val t = ts[k++]
            val s = st.getOrPut(t.accountId to t.symbol) { St(t.symbol) }
            val r = if (t.currency.isNotEmpty() && t.rate > 0.0) t.rate else 1.0
            s.currency = t.currency
            s.lastPrice = t.price
            s.lastDay = t.day
            s.lastRate = r
            if (t.buy) {
                s.cost += t.qty * t.price * r + t.fee
                s.qty += t.qty
                flow += t.qty * t.price * r + t.fee
            } else {
                val sold = minOf(t.qty, s.qty)
                s.cost -= if (s.qty > 1e-9) s.cost / s.qty * sold else 0.0
                s.qty -= sold
                flow -= t.qty * t.price * r - t.fee
            }
        }
        val isNow = day >= today
        var value = 0L
        var cost = 0L
        for (s in st.values) {
            if (s.qty <= 1e-9) continue
            val snap = lastAtOrBefore(snaps[s.symbol to s.currency], if (isNow) Long.MAX_VALUE else day) { it.day }
            val price = if (snap != null && snap.day >= s.lastDay) snap.price else s.lastPrice
            val rate = when {
                s.currency.isEmpty() -> 1.0
                isNow -> now.getOrPut(s.currency) { rateOf(s.currency) } ?: s.lastRate
                else -> lastAtOrBefore(rateList[s.currency], day) { it.day }?.takeIf { it.day >= s.lastDay }?.rate ?: s.lastRate
            }
            value += (s.qty * price * rate).roundToLong()
            cost += s.cost.roundToLong()
        }
        InvestPoint(day, value, cost, flow.roundToLong())
    }
}

/** 第一次買進那天（accountIds、symbol 的意思同 investSeries）；沒有買賣記錄回傳 null */
fun AppData.firstTradeDay(accountIds: Set<Long>? = null, symbol: String? = null): Long? =
    trades.filter { (accountIds == null || it.accountId in accountIds) && (symbol == null || it.symbol == symbol) }.minOfOrNull { it.day }
