package tw.moneybook.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** 幣別：decimals 是最小單位的小數位數（美元 2 位：金額存「分」；日圓 0 位：金額存「圓」） */
data class Currency(val code: String, val name: String, val symbol: String, val decimals: Int)

/** 匯率：1 單位外幣可以換多少台幣（例如 USD 31.5），day 是設定（或更新）的日期 */
data class FxRate(val code: String, val rate: Double, val day: Long)

object Currencies {
    val builtin: List<Currency> = listOf(
        Currency("USD", "美元", "US$", 2),
        Currency("JPY", "日圓", "¥", 0),
        Currency("EUR", "歐元", "€", 2),
        Currency("GBP", "英鎊", "£", 2),
        Currency("CNY", "人民幣", "CN¥", 2),
        Currency("HKD", "港幣", "HK$", 2),
        Currency("KRW", "韓元", "₩", 0),
        Currency("AUD", "澳幣", "A$", 2),
        Currency("SGD", "新加坡幣", "S$", 2),
        Currency("THB", "泰銖", "฿", 2),
    )

    /** 自訂的幣別代碼：這些常見的沒有小數位，其他一律 2 位 */
    private val noDecimals = setOf("VND", "IDR", "CLP", "ISK", "PYG", "UGX")

    fun of(code: String): Currency {
        val c = code.trim().uppercase()
        return builtin.firstOrNull { it.code == c } ?: Currency(c, c, "$c ", if (c in noDecimals) 0 else 2)
    }

    /** 自訂代碼只收 3 個英文字母 */
    fun validCode(code: String): Boolean = code.trim().length == 3 && code.trim().all { it in 'A'..'Z' || it in 'a'..'z' }
}

private fun nf(decimals: Int): NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = decimals
    maximumFractionDigits = decimals
}

/** 最小單位 → 數字文字（不含符號、不含負號）：123456（美元）→ "1,234.56" */
fun fxNumber(minor: Long, decimals: Int): String = nf(decimals).format(BigDecimal(kotlin.math.abs(minor)).movePointLeft(decimals))

/** 外幣金額：符號 + 數字，負的在最前面 */
fun formatFx(minor: Long, code: String): String {
    val c = Currencies.of(code)
    return (if (minor < 0) "-" else "") + c.symbol + fxNumber(minor, c.decimals)
}

/** 輸入框用的純數字（沒有千分位）：1250（美元）→ "12.50"；0 位小數就是整數 */
fun fxPlain(minor: Long, decimals: Int): String = BigDecimal(minor).movePointLeft(decimals).setScale(decimals).toPlainString()

/** 讀使用者輸入的外幣金額（可以有小數點）成最小單位；讀不懂回傳 null，小數位超過就四捨五入 */
fun parseFx(text: String, decimals: Int): Long? {
    val t = text.trim().replace(",", "")
    if (t.isEmpty() || t == ".") return null
    return try {
        BigDecimal(t).setScale(decimals, RoundingMode.HALF_UP).movePointRight(decimals).longValueExact()
    } catch (e: Exception) {
        null
    }
}

/** 外幣（最小單位）換成台幣，四捨五入到元 */
fun fxToTwd(minor: Long, decimals: Int, rate: Double): Long =
    BigDecimal(minor).movePointLeft(decimals).multiply(BigDecimal.valueOf(rate)).setScale(0, RoundingMode.HALF_UP).toLong()

/** 台幣換成外幣（最小單位） */
fun twdToFx(twd: Long, decimals: Int, rate: Double): Long =
    if (rate <= 0.0) 0L
    else BigDecimal(twd).divide(BigDecimal.valueOf(rate), decimals + 4, RoundingMode.HALF_UP).setScale(decimals, RoundingMode.HALF_UP).movePointRight(decimals).toLong()

/** 這次買賣的匯率：台幣 ÷ 外幣；任一邊是 0 回傳 null */
fun impliedRate(twd: Long, minor: Long, decimals: Int): Double? {
    if (twd <= 0L || minor <= 0L) return null
    return BigDecimal(twd).divide(BigDecimal(minor).movePointLeft(decimals), 8, RoundingMode.HALF_UP).toDouble()
}

/** 匯率文字：31.5 → "31.5"；0.2134 → "0.2134"（最多 4 位小數，太小的匯率多留幾位） */
fun rateText(rate: Double): String {
    val scale = if (rate < 0.01) 6 else 4
    return BigDecimal.valueOf(rate).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

val Account.isForeign: Boolean get() = currency.isNotEmpty()

val Account.cur: Currency get() = Currencies.of(currency)

/** 這個帳戶的金額文字：外幣帳戶是外幣格式，其他是台幣 */
fun Account.fmt(v: Long): String = if (isForeign) formatFx(v, currency) else formatMoney(v)

/** 目前匯率：手動設定的優先，沒有就用最近一次買賣外幣的成交匯率；都沒有回傳 null */
fun AppData.rateOf(code: String): Double? {
    val c = code.trim().uppercase()
    rates.firstOrNull { it.code == c }?.let { return it.rate }
    val dec = Currencies.of(c).decimals
    for (t in txns) {      // txns 已經是新到舊
        if (t.type != TxType.TRANSFER || t.fxAmount <= 0L) continue
        val from = accMap[t.accountId ?: continue] ?: continue
        val to = accMap[t.toAccountId ?: continue] ?: continue
        if (from.isForeign == to.isForeign) continue
        if ((if (from.isForeign) from else to).currency != c) continue
        impliedRate(t.amount, t.fxAmount, dec)?.let { return it }
    }
    return null
}

/** 外幣帳戶的餘額換成約當台幣；沒有匯率回傳 null。台幣帳戶原樣回傳 */
fun AppData.twdValue(a: Account, bal: Long): Long? {
    if (!a.isForeign) return bal
    val r = rateOf(a.currency) ?: return null
    return fxToTwd(bal, a.cur.decimals, r)
}

/** 平均買進成本（台幣 / 1 單位外幣）：這個帳戶所有「台幣帳戶轉進來」的買進，台幣總額 ÷ 外幣總額；沒有買進記錄回傳 null */
fun AppData.avgCost(a: Account): Double? {
    if (!a.isForeign) return null
    var twd = 0L
    var fx = 0L
    for (t in txns) {
        if (t.type != TxType.TRANSFER || t.toAccountId != a.id || t.fxAmount <= 0L) continue
        val from = accMap[t.accountId ?: continue] ?: continue
        if (from.isForeign) continue
        twd += t.amount
        fx += t.fxAmount
    }
    return impliedRate(twd, fx, a.cur.decimals)
}

/** 台幣帳戶刷外幣（待請款或已請款） */
val Txn.isFxSpend: Boolean get() = fxSpendAmount > 0L && fxSpendCur.isNotEmpty()

/** 這筆外幣消費的匯率：台幣 ÷ 外幣（待請款是預估的，已請款是實際的）；金額是 0 回傳 null */
fun Txn.fxSpendRate(): Double? = if (!isFxSpend) null else impliedRate(amount, fxSpendAmount, Currencies.of(fxSpendCur).decimals)

/** 外幣消費的備註：「¥12,000 @ 0.2198（預估）」「¥12,000 @ 0.2241（實際）」 */
fun Txn.fxSpendNote(): String? {
    if (!isFxSpend) return null
    val rate = fxSpendRate()?.let { " @ ${rateText(it)}" } ?: ""
    return formatFx(fxSpendAmount, fxSpendCur) + rate + if (fxPending) "（預估）" else "（實際）"
}

/** 還沒請款的外幣消費（目前帳本），最久的排前面 */
fun AppData.fxPendingList(): List<Txn> = bookTxns.filter { it.fxPending && it.isFxSpend }.sortedWith(compareBy({ it.day }, { it.id }))

/** 請款：把預估的台幣改成銀行實際請款的台幣，標記消失；找不到、不是待請款、金額不對回傳 null */
fun AppData.withFxSettled(id: Long, actualTwd: Long): AppData? {
    val t = txns.firstOrNull { it.id == id } ?: return null
    if (!t.fxPending || !t.isFxSpend || actualTwd <= 0L) return null
    return copy(txns = txns.map { if (it.id == id) it.copy(amount = actualTwd, fxPending = false) else it })
}

/**
 * 明細列的外幣備註：inForeign（在外幣帳戶的明細裡，主金額已經是外幣）→ 顯示台幣那一邊；否則顯示外幣金額。
 * 台幣 ⇄ 外幣的買賣會附上成交匯率（台幣 ÷ 外幣）。
 */
fun AppData.fxNote(t: Txn, inForeign: Boolean): String? {
    t.fxSpendNote()?.let { return it }
    if (t.fxAmount <= 0L) return null
    val fa = listOfNotNull(t.accountId, t.toAccountId).mapNotNull { accMap[it] }.firstOrNull { it.isForeign } ?: return null
    val from = t.accountId?.let { accMap[it] }
    val to = t.toAccountId?.let { accMap[it] }
    val trade = t.type == TxType.TRANSFER && from != null && to != null && from.isForeign != to.isForeign
    val rate = if (trade) impliedRate(t.amount, t.fxAmount, fa.cur.decimals)?.let { " @ ${rateText(it)}" } ?: "" else ""
    return if (inForeign) {
        if (t.amount <= 0L) null else (if (trade) "" else "約 ") + formatMoney(t.amount) + rate
    } else formatFx(t.fxAmount, fa.currency) + rate
}

/**
 * 記一筆的外幣模式（看轉出／轉入帳戶的幣別）：
 * SPEND＝外幣帳戶的消費或收入；BUY＝台幣帳戶轉進外幣帳戶（買外幣）；SELL＝外幣帳戶轉回台幣帳戶（賣外幣）；
 * SAME＝同幣別外幣帳戶之間轉帳；UNSUPPORTED＝不同幣別的外幣帳戶互轉（要先換回台幣）。
 */
enum class FxMode { NONE, SPEND, BUY, SELL, SAME, UNSUPPORTED }

class FxPlan(val mode: FxMode, val acc: Account?)

fun AppData.fxPlan(type: TxType, accId: Long?, toId: Long?): FxPlan {
    val from = accId?.let { accMap[it] }
    if (type != TxType.TRANSFER) return if (from?.isForeign == true) FxPlan(FxMode.SPEND, from) else FxPlan(FxMode.NONE, null)
    val to = toId?.let { accMap[it] }
    return when {
        from?.isForeign == true && to?.isForeign == true ->
            if (from.currency == to.currency) FxPlan(FxMode.SAME, from) else FxPlan(FxMode.UNSUPPORTED, from)
        from?.isForeign == true -> FxPlan(FxMode.SELL, from)
        to?.isForeign == true -> FxPlan(FxMode.BUY, to)
        else -> FxPlan(FxMode.NONE, null)
    }
}

/** 放進計算機的外幣文字：1250（美元）→ "12.5"；去掉多餘的 0 */
fun fxExpr(minor: Long, decimals: Int): String =
    fxPlain(minor, decimals).let { if ('.' in it) it.trimEnd('0').trimEnd('.') else it }

/** 報銷收款讓帳戶 a 的餘額增加多少：外幣帳戶是實際入帳的外幣，其他是台幣 */
fun ReimbPay.flowFor(a: Account): Long = if (a.isForeign) fxAmount else amount

/** 這筆消費是用哪個外幣帳戶付的（不是外幣消費回傳 null） */
fun AppData.fxAccountOf(t: Txn): Account? = t.accountId?.let { accMap[it] }?.takeIf { it.isForeign && t.fxAmount > 0L }

/** v × num ÷ den，四捨五入（用 BigDecimal，大金額也不會溢位）；den 是 0 回傳 0 */
fun scaleRound(v: Long, num: Long, den: Long): Long =
    if (den == 0L) 0L else BigDecimal(v).multiply(BigDecimal(num)).divide(BigDecimal(den), 0, RoundingMode.HALF_UP).toLong()

/** 用這筆消費當時的匯率（台幣金額 ÷ 外幣金額），把台幣換成外幣（最小單位）；不是外幣消費回傳 0 */
fun Txn.twdToFxAt(twd: Long): Long = if (fxAmount <= 0L || amount <= 0L) 0L else scaleRound(twd, fxAmount, amount)

/** 用這筆消費當時的匯率，把外幣（最小單位）換成台幣；不是外幣消費回傳 0 */
fun Txn.fxToTwdAt(fx: Long): Long = if (fxAmount <= 0L || amount <= 0L) 0L else scaleRound(fx, amount, fxAmount)
