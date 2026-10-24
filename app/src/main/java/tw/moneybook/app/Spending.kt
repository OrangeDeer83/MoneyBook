package tw.moneybook.app

import java.time.DayOfWeek
import java.time.LocalDate

/* 什麼時候花錢（統計的三張卡片）：平日／週末、一天中的時段、每天熱度 */

/** 平日、週末平均每天花多少：start～until 這幾天（每一天都算，沒花錢的天也算進天數） */
class WeekSplit(val weekday: Long, val weekend: Long, val weekdayDays: Int, val weekendDays: Int)

fun weekSplit(list: List<Txn>, start: Long, until: Long): WeekSplit {
    var wd = 0L
    var we = 0L
    var nWd = 0
    var nWe = 0
    for (day in start..until) {
        val w = LocalDate.ofEpochDay(day).dayOfWeek
        if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) nWe++ else nWd++
    }
    for (t in list) {
        if (t.day !in start..until) continue
        val w = t.date.dayOfWeek
        if (w == DayOfWeek.SATURDAY || w == DayOfWeek.SUNDAY) we += t.spent else wd += t.spent
    }
    return WeekSplit(if (nWd > 0) wd / nWd else 0L, if (nWe > 0) we / nWe else 0L, nWd, nWe)
}

/** 一天中的時段：早上 5～11、中午 11～14、下午 14～18、晚上 18～23、深夜（其他） */
val HourSlots: List<String> = listOf("早上", "中午", "下午", "晚上", "深夜")

fun hourSlot(minute: Int): Int = when (minute / 60) {
    in 5..10 -> 0
    in 11..13 -> 1
    in 14..17 -> 2
    in 18..22 -> 3
    else -> 4
}

/** 各時段的支出金額與筆數（只算有記時間的） */
class HourSplit(val amounts: List<Long>, val counts: List<Int>) {
    val timedCount: Int get() = counts.sum()
}

fun hourSplit(list: List<Txn>): HourSplit {
    val a = LongArray(5)
    val n = IntArray(5)
    for (t in list) {
        if (t.time < 0 || t.spent <= 0L) continue
        val k = hourSlot(t.time)
        a[k] += t.spent
        n[k]++
    }
    return HourSplit(a.toList(), n.toList())
}

/** 每一天花多少（每天熱度） */
fun dayTotals(list: List<Txn>): Map<Long, Long> = list.filter { it.spent > 0L }.groupBy { it.day }.mapValues { e -> e.value.sumOf { it.spent } }
