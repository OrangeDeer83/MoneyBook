package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth

/*
 * 分類走勢：統計下鑽頁上面的近 12 個月折線、趨勢頁的「分類走勢」卡片共用的計算。
 * 金額都是自己負擔的支出（Txn.statAmount，調整不算），只看目前帳本。
 */

/** 分類本身＋它的子分類（子分類就只有自己） */
fun AppData.catAndKids(id: Long): Set<Long> = setOf(id) + childrenOf(id).map { it.id }

/** 從 end 往前算 n 個月（舊到新，最後一個是 end） */
fun monthsEnding(end: YearMonth, n: Int): List<YearMonth> = (n - 1 downTo 0).map { end.minusMonths(it.toLong()) }

/** 符合 pred 的帳每個月加起來（amt 是每一筆算多少） */
fun monthlySums(txns: List<Txn>, months: List<YearMonth>, pred: (Txn) -> Boolean, amt: (Txn) -> Long): List<Long> {
    if (months.isEmpty()) return emptyList()
    val idx = months.withIndex().associate { (i, m) -> m to i }
    val out = LongArray(months.size)
    for (t in txns) {
        if (!pred(t)) continue
        val i = idx[t.month] ?: continue
        out[i] += amt(t)
    }
    return out.toList()
}

/** 某個支出分類（大分類含子分類）每個月花多少 */
fun AppData.catMonthly(id: Long, months: List<YearMonth>): List<Long> {
    val ids = catAndKids(id)
    return monthlySums(bookTxns, months, { t -> t.type == TxType.EXPENSE && t.categoryId?.let { it in ids } == true }, { it.statAmount })
}

/** 趨勢頁一開始挑的分類：到 end 為止 12 個月支出最多的 3 個大分類（沒花過的不挑） */
fun AppData.topTrendCats(end: YearMonth, n: Int = 3): List<Long> {
    val months = monthsEnding(end, 12).toSet()
    val sums = HashMap<Long, Long>()
    for (t in bookTxns) {
        if (t.type != TxType.EXPENSE || t.month !in months) continue
        val c = t.categoryId?.let { catMap[it] } ?: continue
        val top = topOf(c).id
        sums[top] = (sums[top] ?: 0L) + t.statAmount
    }
    return sums.filter { it.value > 0L }.entries.sortedByDescending { it.value }.take(n).map { it.key }
}

/** 趨勢頁要畫的分類：自己挑過的（拿掉已經刪掉的、不是支出的），沒挑過或都刪光了就用預設 */
fun AppData.trendCats(end: YearMonth): List<Long> {
    val picked = prefs.trendCats.filter { catMap[it]?.kind == TxType.EXPENSE }.distinct().take(3)
    return picked.ifEmpty { topTrendCats(end) }
}

/**
 * 下鑽頁的走勢圖畫到哪個月：正在看的那段期間的最後一個月，還沒到的月份不畫（最多到這個月）。
 * mode：0 月、1 年、2 區間（和統計頁一樣）；rangeEndDay 是區間最後一天（epoch day）。
 */
fun drillTrendEnd(mode: Int, month: YearMonth, year: Int, rangeEndDay: Long, now: YearMonth): YearMonth {
    val end = when (mode) {
        0 -> month
        1 -> YearMonth.of(year, 12)
        else -> YearMonth.from(LocalDate.ofEpochDay(rangeEndDay))
    }
    return if (end.isAfter(now)) now else end
}

/**
 * 走勢的平均和「比平均多 30% 以上」的月份。
 * 平均從第一次記這個分類的月份（first）開始算，之前還沒記帳的月份不算；這個月還沒過完（now）也不算進平均。
 * 沒有可以算平均的月份（例如這個月才第一次記）回傳 avg 0、沒有紅點。
 */
class TrendStat(val avg: Long, val warn: Set<Int>)

fun trendStat(values: List<Long>, months: List<YearMonth>, first: YearMonth?, now: YearMonth): TrendStat {
    if (first == null) return TrendStat(0L, emptySet())
    val use = months.indices.filter { i -> !months[i].isBefore(first) && months[i] != now && !months[i].isAfter(now) }
    if (use.isEmpty()) return TrendStat(0L, emptySet())
    val avg = Math.round(use.sumOf { values[it] }.toDouble() / use.size)
    if (avg <= 0L) return TrendStat(0L, emptySet())
    // 比平均多 30% 以上（整數比較：v × 10 ≥ avg × 13）
    val warn = months.indices.filter { i -> !months[i].isBefore(first) && values[i] * 10 >= avg * 13 }.toSet()
    return TrendStat(avg, warn)
}

/** 符合 pred 的帳最早是哪個月（平均從這個月開始算），都沒有回傳 null */
fun firstMonthOf(txns: List<Txn>, pred: (Txn) -> Boolean): YearMonth? =
    txns.filter(pred).minOfOrNull { it.day }?.let { YearMonth.from(LocalDate.ofEpochDay(it)) }
