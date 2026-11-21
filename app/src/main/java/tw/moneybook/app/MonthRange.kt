package tw.moneybook.app

import java.time.YearMonth

/*
 * 統計自訂期間（草圖 ci-out/stats_custom_range.png）：趨勢分頁、總資產頁的「自訂」，跳視窗點開始、結束月份。
 */

/** a 到 b（含）的每個月，舊到新；a 比 b 晚時對調 */
fun monthsBetween(a: YearMonth, b: YearMonth): List<YearMonth> {
    val (from, to) = if (a <= b) a to b else b to a
    val out = ArrayList<YearMonth>()
    var m = from
    while (m <= to) { out.add(m); m = m.plusMonths(1) }
    return out
}

/** 自訂期間視窗的狀態：editEnd＝現在點的是結束月份（false＝開始月份） */
data class MonthPick(val from: YearMonth, val to: YearMonth, val editEnd: Boolean = false) {
    /** 共幾個月 */
    val count: Int get() = (to.year * 12 + to.monthValue) - (from.year * 12 + from.monthValue) + 1

    /** 點一個月：點了開始自動換成選結束（結束比新的開始早就先跟著開始）；結束點得比開始早就對調 */
    fun tap(m: YearMonth): MonthPick = when {
        !editEnd -> MonthPick(m, if (to < m) m else to, true)
        m < from -> MonthPick(m, from, true)
        else -> copy(to = m)
    }
}

/**
 * 統計趨勢分頁的月份。n＝6、12：到 end 為止近 n 個月；n＝0：全部（這本帳第一筆的月份到這個月，最少 2 個月）；
 * n＝-1：自訂 from～to（沒選過就當全部）。
 */
fun trendMonthsOf(n: Int, end: YearMonth, first: YearMonth, now: YearMonth, from: YearMonth? = null, to: YearMonth? = null): List<YearMonth> = when {
    n > 0 -> monthsEnding(end, n)
    n < 0 && from != null && to != null -> monthsBetween(from, to)
    else -> monthsBetween(minOf(first, now.minusMonths(1)), now)
}
