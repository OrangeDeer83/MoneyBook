package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 訂閱／固定扣款：扣款日怎麼算、到期提醒、記了／略過、每月合計、記成的那一筆、存檔 */
class SubsTest {
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun dates(vararg ds: Triple<Int, Int, Int>) = ds.map { day(it.first, it.second, it.third) }
    private fun sub(
        freq: Int = Sub.FREQ_MONTH, days: List<Int>, start: Long, every: Int = 1,
        endMode: Int = Sub.END_NONE, endCount: Int = 12, endDay: Long = 0L, amount: Long = 100L, cur: String = "", acc: Long? = 1L,
    ) = Sub(1, 1, "Netflix", amount, cur, 10L, acc, freq = freq, every = every, days = days, start = start,
        endMode = endMode, endCount = endCount, endDay = endDay, doneUntil = start - 1)

    @Test
    fun monthlyShortMonthAndMonthEnd() {
        val s = sub(days = listOf(31), start = day(2026, 1, 1))
        assertEquals(dates(Triple(2026, 1, 31), Triple(2026, 2, 28), Triple(2026, 3, 31), Triple(2026, 4, 30)), s.dates(day(2026, 4, 30)))
        // 30 號和月底都選：2 月只算一次
        val both = sub(days = listOf(30, Sub.MONTH_END), start = day(2026, 2, 1))
        assertEquals(dates(Triple(2026, 2, 28), Triple(2026, 3, 30), Triple(2026, 3, 31)), both.dates(day(2026, 3, 31)))
        // 每 3 個月，開始日之前的不算
        val q = sub(days = listOf(5), start = day(2026, 1, 10), every = 3)
        assertEquals(dates(Triple(2026, 4, 5), Triple(2026, 7, 5)), q.dates(day(2026, 9, 30)))
    }

    @Test
    fun weeklyEveryTwoWeeks() {
        // 2026/11/11 是星期三；每 2 週的星期日、二、四（從 11/8 那一週開始數）
        val s = sub(Sub.FREQ_WEEK, listOf(4, 0, 2), day(2026, 11, 11), every = 2)
        assertEquals(dates(Triple(2026, 11, 12), Triple(2026, 11, 22), Triple(2026, 11, 24), Triple(2026, 11, 26)), s.dates(day(2026, 11, 30)))
        assertEquals("每 2 週的星期日、二、四", s.ruleText())
    }

    @Test
    fun yearlyLeapDayAndEnds() {
        val s = sub(Sub.FREQ_YEAR, listOf(229), day(2024, 1, 1))
        assertEquals(dates(Triple(2024, 2, 29), Triple(2025, 2, 28), Triple(2026, 2, 28)), s.dates(day(2026, 12, 31)))
        assertEquals("每年 2/29", s.ruleText())
        val count = sub(days = listOf(1), start = day(2026, 1, 1), endMode = Sub.END_COUNT, endCount = 3)
        assertEquals(3, count.dates(day(2030, 1, 1)).size)
        val until = sub(days = listOf(1), start = day(2026, 1, 1), endMode = Sub.END_UNTIL, endDay = day(2026, 3, 1))
        assertEquals(3, until.dates(day(2030, 1, 1)).size)
        assertNull(until.copy(doneUntil = day(2026, 3, 1)).next(day(2026, 6, 1)))   // 結束了
    }

    @Test
    fun dueAndMarkDoneOutOfOrder() {
        val s = sub(days = listOf(5), start = day(2026, 8, 1))
        val today = day(2026, 11, 9)
        assertEquals(dates(Triple(2026, 8, 5), Triple(2026, 9, 5), Triple(2026, 10, 5), Triple(2026, 11, 5)), s.due(today))
        // 先處理 10/5：8、9 月還在
        val a = s.markDone(day(2026, 10, 5))
        assertEquals(dates(Triple(2026, 8, 5), Triple(2026, 9, 5), Triple(2026, 11, 5)), a.due(today))
        assertEquals(s.doneUntil, a.doneUntil)
        // 再處理 8/5、9/5：連起來併進 doneUntil
        val b = a.markDone(day(2026, 8, 5)).markDone(day(2026, 9, 5))
        assertEquals(day(2026, 10, 5), b.doneUntil)
        assertTrue(b.doneDays.isEmpty())
        assertEquals(dates(Triple(2026, 11, 5)), b.due(today))
        assertEquals(day(2026, 11, 5), b.next(today))
        assertTrue(b.copy(paused = true).due(today).isEmpty())
        // 新增的訂閱：開始日在以前的，今天以前那幾次不提醒
        val fresh = s.copy(doneUntil = newDoneUntil(s.start, today))
        assertTrue(fresh.due(today).isEmpty())
        assertEquals(day(2026, 12, 5), fresh.next(today))
        assertEquals(dates(Triple(2026, 11, 9)), sub(days = listOf(9), start = today).copy(doneUntil = newDoneUntil(today, today)).due(today))   // 今天就扣：馬上提醒
    }

    @Test
    fun monthlyTotalAndTxn() {
        val base = Defaults.create()
        val twdAcc = base.accounts.first()
        val usdAcc = Account(77, "美元帳戶", AccountType.FOREIGN.emoji, AccountType.FOREIGN, 0L, 5, currency = "USD")
        val bid = base.currentBook.id
        val start = day(2026, 1, 1)
        val today = day(2026, 11, 9)
        fun mk(id: Long, s: Sub) = s.copy(id = id, bookId = bid, doneUntil = start - 1)
        val d = base.copy(
            accounts = base.accounts + usdAcc,
            rates = listOf(FxRate("USD", 32.0, today)),
            subs = listOf(
                mk(1, sub(days = listOf(5), start = start, amount = 149, acc = twdAcc.id)),
                mk(2, sub(Sub.FREQ_YEAR, listOf(301), start, amount = 1200, acc = twdAcc.id)),
                mk(3, sub(days = listOf(5), start = start, amount = 1000, cur = "USD", acc = twdAcc.id)),      // US$10.00
                mk(4, sub(days = listOf(5), start = start, amount = 999, acc = twdAcc.id)).copy(paused = true),
                mk(5, sub(days = listOf(5), start = start, amount = 500, cur = "USD", acc = usdAcc.id)),       // US$5.00
                mk(6, sub(days = listOf(5), start = start, amount = 777, acc = twdAcc.id)).copy(bookId = 999), // 別的帳本
            ),
        )
        // 149 + 1200/12 + 10×32 + 5×32；暫停的、別的帳本不算
        assertEquals(149L + 100L + 320L + 160L, d.subsMonthly(today))
        val t1 = d.subTxn(d.subs[0], day(2026, 11, 5), 500, 149, true)
        assertEquals(149L, t1.amount)
        assertEquals(listOf(Sub.TAG), t1.tags)
        assertEquals(TxType.EXPENSE, t1.type)
        // 外幣扣台幣帳戶：外幣消費、待請款
        val t3 = d.subTxn(d.subs[2], day(2026, 11, 5), 501, 321, true)
        assertEquals(321L, t3.amount)
        assertEquals(1000L, t3.fxSpendAmount)
        assertEquals("USD", t3.fxSpendCur)
        assertTrue(t3.fxPending)
        // 外幣扣美元帳戶：直接扣 US$
        val t5 = d.subTxn(d.subs[4], day(2026, 11, 5), 502, 160, true)
        assertEquals(500L, t5.fxAmount)
        assertEquals("", t5.fxSpendCur)
        assertEquals(-500L, t5.flowFor(usdAcc))
        // 到期：只列目前帳本、沒暫停的；最早的在前面
        val due = d.subsDue(today)
        assertTrue(due.none { it.first.id == 4L || it.first.id == 6L })
        assertEquals(day(2026, 1, 5), due.first().second)
    }

    @Test
    fun codecRoundTripAndOldData() {
        val base = Defaults.create()
        assertTrue(Codec.decode(Codec.encode(base)).subs.isEmpty())
        val s = sub(Sub.FREQ_WEEK, listOf(2, 4), day(2026, 11, 1), every = 2, endMode = Sub.END_UNTIL, endDay = day(2027, 2, 2), cur = "USD")
            .copy(id = 900, bookId = base.currentBook.id, tags = listOf("影音"), note = "家庭方案", paused = true, doneDays = listOf(day(2026, 11, 10)))
        val back = Codec.decode(Codec.encode(base.copy(subs = listOf(s))))
        assertEquals(listOf(s), back.subs)
        assertTrue(back.nextId > 900)
    }
}
