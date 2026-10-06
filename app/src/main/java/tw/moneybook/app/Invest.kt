package tw.moneybook.app

import kotlin.math.roundToLong

/**
 * 一筆買進或賣出，歸在某個投資帳戶底下。
 * 單價用 currency 這個幣別記（空白＝台幣；美股用 USD，單價就是美金）；用外幣記時，rate 是這筆成交當時
 * 「1 單位外幣 = 幾元台幣」，成本與損益用它換成台幣算。手續費 fee 一律是台幣。
 */
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
    /** 市場（見 Markets）；空白是舊資料，抓價時自動判斷 */
    val market: String = "",
    /** 單價的幣別；空白是台幣（舊資料、台股都是） */
    val currency: String = "",
    /** 外幣單價換台幣的匯率（currency 不是空白才有意義） */
    val rate: Double = 0.0,
)

/** 某個代號在某一天的價格（抓到的或手動輸入的）。每個月抓幾次，當作那個月的代表。currency 是價格的幣別，空白是台幣 */
data class PriceSnap(val symbol: String, val day: Long, val price: Double, val currency: String = "")

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
    val market: String = "",
    /** 單價（price、avgPrice）的幣別；空白是台幣。cost、value、gain 一律是台幣 */
    val currency: String = "",
    /** 把單價換成台幣用的匯率（台幣是 1.0） */
    val rate: Double = 1.0,
    /** 每股平均成本（用單價的幣別；美股是美金） */
    val avgPrice: Double = 0.0,
) {
    val value: Long get() = (qty * price * rate).roundToLong()
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

/** 價格快照只留「這個月全部」加「之前每個月最後一筆」：每個月抓幾次，過了月就只留一筆當代表 */
fun pruneMonthly(list: List<PriceSnap>, today: java.time.LocalDate): List<PriceSnap> {
    val cur = java.time.YearMonth.from(today)
    val (now, old) = list.partition { java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(it.day)) >= cur }
    val kept = old.groupBy { it.symbol to java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(it.day)) }
        .map { (_, v) -> v.maxByOrNull { it.day }!! }
    return (kept + now).sortedBy { it.day }
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
        var market = ""
        var currency = ""
        var lastRate = 1.0
        var qty = 0.0
        var cost = 0.0           // 台幣
        var costNative = 0.0     // 單價幣別（美股是美金）
        var lastPrice = 0.0
        var lastDay = Long.MIN_VALUE
    }
    val m = LinkedHashMap<Pair<Long, String>, Acc>()
    var realized = 0.0
    for (t in trades.sortedWith(compareBy({ it.day }, { it.id }))) {
        if (accountId != null && t.accountId != accountId) continue
        val a = m.getOrPut(t.accountId to t.symbol) { Acc(t.name) }
        if (t.name.isNotBlank()) a.name = t.name
        if (t.market.isNotBlank()) a.market = t.market
        a.lastPrice = t.price
        a.lastDay = t.day
        a.currency = t.currency
        // 外幣單價：成交金額用「這筆的匯率」換成台幣；台幣就是 1
        val r = if (t.currency.isNotEmpty() && t.rate > 0.0) t.rate else 1.0
        a.lastRate = r
        if (t.buy) {
            a.cost += t.qty * t.price * r + t.fee
            a.costNative += t.qty * t.price
            a.qty += t.qty
        } else {
            val sold = minOf(t.qty, a.qty)
            val out = if (a.qty > EPS) a.cost / a.qty * sold else 0.0
            val outNative = if (a.qty > EPS) a.costNative / a.qty * sold else 0.0
            realized += t.qty * t.price * r - t.fee - out
            a.cost -= out
            a.costNative -= outNative
            a.qty -= sold
        }
    }
    val list = m.entries.filter { it.value.qty > EPS }.map { (k, a) ->
        val snap = latest[k.second]
        // 手動／抓到的價格比最後一筆成交價新（或一樣新）、而且幣別一樣才用它（舊的台幣價格不能當美金用）
        val useSnap = snap != null && snap.day >= a.lastDay && snap.currency == a.currency
        // 外幣持股用「目前匯率」算市值：手動設定的、最近一次買賣外幣的匯率，都沒有就用最後一筆成交的匯率
        val rate = if (a.currency.isEmpty()) 1.0 else rateOf(a.currency) ?: a.lastRate
        Position(
            accountId = k.first, symbol = k.second, name = a.name,
            qty = a.qty, cost = a.cost.roundToLong(), market = a.market,
            price = if (useSnap) snap!!.price else a.lastPrice,
            priceDay = if (useSnap) snap!!.day else a.lastDay,
            currency = a.currency, rate = rate,
            avgPrice = if (a.currency.isEmpty()) (if (a.qty > EPS) a.cost / a.qty else 0.0) else (if (a.qty > EPS) a.costNative / a.qty else 0.0),
        )
    }.sortedByDescending { it.value }
    return Portfolio(list, realized.roundToLong())
}

/** 買賣金額（不含手續費），四捨五入到元 */
fun tradeAmount(qty: Double, price: Double): Long = (qty * price).roundToLong()

/** 一檔的抓價結果（抓價完成後列給使用者看） */
data class FetchResult(val symbol: String, val name: String, val market: String, val price: Double?, val currency: String = "") {
    val ok: Boolean get() = price != null
}

/**
 * 市場：決定抓價時代號要怎麼補後綴（Yahoo Finance 的格式）。
 * 台股 0050 → 0050.TW（上櫃是 .TWO）、日股 7203 → 7203.T、韓股 005930 → 005930.KS（或 .KQ）、
 * 港股 700 → 0700.HK、加密貨幣 BTC → BTC-USD、美股照原樣。空白（舊資料）：4～6 位數字當台股，其他照原樣。
 */
object Markets {
    val all: List<Pair<String, String>> = listOf(
        "TW" to "台股", "US" to "美股", "JP" to "日股", "KR" to "韓股", "HK" to "港股", "CRYPTO" to "加密貨幣",
    )

    fun label(code: String): String = all.firstOrNull { it.first == code }?.second ?: ""

    /** 這個市場的單價用什麼幣別記：台股是台幣（空白），美股與加密貨幣是美金，其他是當地幣別 */
    fun currencyOf(code: String): String = when (code) {
        "US", "CRYPTO" -> "USD"
        "JP" -> "JPY"
        "KR" -> "KRW"
        "HK" -> "HKD"
        else -> ""
    }

    /** 代號欄的範例 */
    fun example(code: String): String = when (code) {
        "TW" -> "例如 0050、2330"
        "US" -> "例如 AAPL、VOO"
        "JP" -> "例如 7203"
        "KR" -> "例如 005930"
        "HK" -> "例如 0700"
        "CRYPTO" -> "例如 BTC、ETH"
        else -> "例如 0050、AAPL"
    }

    private val taiwan = Regex("^[0-9]{4,6}[A-Z]?$")

    /** 要依序嘗試的 Yahoo 代號；使用者自己已經帶了後綴（有「.」）就照用 */
    fun candidates(symbol: String, market: String): List<String> {
        val s = symbol.trim().uppercase()
        if (s.isEmpty()) return emptyList()
        return when (market) {
            "US" -> listOf(s)
            "JP" -> if ('.' in s) listOf(s) else listOf("$s.T")
            "KR" -> if ('.' in s) listOf(s) else listOf("$s.KS", "$s.KQ")
            "HK" -> if ('.' in s) listOf(s) else listOf(s.trimStart('0').padStart(4, '0') + ".HK")
            "CRYPTO" -> if ('-' in s) listOf(s) else listOf("$s-USD")
            else -> if ('.' !in s && taiwan.matches(s)) listOf("$s.TW", "$s.TWO") else listOf(s)   // 台股與舊資料
        }
    }
}

/** 數量：最多 4 位小數，去掉多餘的 0（100 → 100，0.5000 → 0.5） */
fun qtyText(q: Double): String =
    java.math.BigDecimal(q).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** 價格：1 元以上 2 位小數，不到 1 元（例如小幣種）4 位小數 */
fun priceText(p: Double): String = String.format(java.util.Locale.US, if (p >= 1.0) "%,.2f" else "%.4f", p)
