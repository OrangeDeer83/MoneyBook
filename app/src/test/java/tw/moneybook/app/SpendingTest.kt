package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 什麼時候花錢：平日／週末平均每天、時段、每天熱度 */
class SpendingTest {
    private val base = Defaults.create()
    private val cat = base.topCategories(TxType.EXPENSE).first()
    private val day = LocalDate.of(2026, 10, 16).toEpochDay()   // 週五
    private fun t(id: Long, amount: Long, d: Long, time: Int = -1) =
        Txn(id, 1L, TxType.EXPENSE, amount, cat.id, base.accounts.first().id, null, d, "", emptyList(), time = time)

    @Test
    fun whenSpending() {
        // 10/16 週五 300（晚上 20:00）、10/17 週六 900（中午 12:00）、10/18 週日 300（沒有時間）
        val list = listOf(t(1, 300, day, 20 * 60), t(2, 900, day + 1, 12 * 60), t(3, 300, day + 2))
        val w = weekSplit(list, day - 4, day + 2)       // 10/12（一）～10/18（日）：平日 5 天、週末 2 天
        assertEquals(5, w.weekdayDays)
        assertEquals(2, w.weekendDays)
        assertEquals(60L, w.weekday)
        assertEquals(600L, w.weekend)
        val h = hourSplit(list)
        assertEquals(listOf(0L, 900L, 0L, 300L, 0L), h.amounts)
        assertEquals(2, h.timedCount)
        assertEquals(3, hourSlot(22 * 60 + 59))
        assertEquals(4, hourSlot(23 * 60))
        assertEquals(4, hourSlot(3 * 60))
        assertEquals(mapOf(day to 300L, day + 1 to 900L, day + 2 to 300L), dayTotals(list))
    }
}
