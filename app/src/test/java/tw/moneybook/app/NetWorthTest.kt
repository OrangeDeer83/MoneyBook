package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** 總資產變化：每個月底的組成、最後一點等於帳戶頁的總資產、變化的原因加起來等於變化 */
class NetWorthTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()
    private val cash = Account(1, "現金", "", AccountType.CASH, 1000, 0)
    private val card = Account(2, "信用卡", "", AccountType.CARD, 0, 1)
    private val stock = Account(3, "證券", "", AccountType.INVEST, 0, 2)
    private val hidden = Account(4, "藏起來", "", AccountType.BANK, 9999, 3, hidden = true)

    private fun txn(id: Long, type: TxType, amount: Long, acc: Long?, day: Long, to: Long? = null, adjust: Boolean = false) =
        Txn(id, 1L, type, amount, if (type == TxType.TRANSFER || adjust) null else 1L, acc, to, day, "", emptyList(), adjust = adjust)

    private fun data(): AppData {
        val d = Defaults.create()
        val list = listOf(
            txn(11, TxType.INCOME, 5000, 1, day(9, 10)),
            txn(12, TxType.EXPENSE, 300, 2, day(9, 15)),
            txn(13, TxType.TRANSFER, 2000, 1, day(10, 1), to = 3),
            txn(14, TxType.INCOME, 500, 3, day(10, 5), adjust = true),          // 同步市值：漲了 500
            txn(15, TxType.EXPENSE, 1000, 1, day(10, 8)).withItems(listOf(ReimbItem("Amy", 600))),   // 幫 Amy 墊 600
            txn(16, TxType.EXPENSE, 200, null, day(10, 9)).copy(debtItems = listOf(DebtItem("Bob", 200))),   // Bob 先幫我付
        )
        return d.copy(accounts = listOf(cash, card, stock, hidden), txns = sortTxns(list), nextId = 100)
    }

    @Test
    fun monthEndComposition() {
        val d = data()
        val (sep, oct) = d.netWorthMonths(listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10)), today)
        // 9 月底：現金 1000＋5000，信用卡欠 300；隱藏的帳戶不算
        assertEquals(NetWorthPoint(6000, 0, 300), sep)
        // 現在：現金 6000－2000－1000，證券 2000＋500，負債＝信用卡 300＋欠 Bob 200
        assertEquals(NetWorthPoint(3000, 2500, 500), oct)
        assertEquals(5000L, oct.net)
    }

    @Test
    fun latestPointEqualsAccountsPageTotal() {
        val d = data()
        val bal = d.balances()
        val page = d.visibleAccounts.sumOf { d.twdValue(it, bal[it.id] ?: 0L) ?: 0L } - d.debtOutstanding()
        assertEquals(page, d.netWorthAt(listOf(Long.MAX_VALUE)).single().net)
    }

    @Test
    fun reasonsAddUpToChange() {
        val r = data().netWorthReasons(YearMonth.of(2026, 10), today)
        assertEquals(-700L, r.change)
        assertEquals(-600L, r.saved)     // 自己負擔 400＋欠 Bob 的 200
        assertEquals(500L, r.invest)
        assertEquals(-600L, r.reimb)     // 先墊 Amy 的 600 還沒收回
        assertEquals(0L, r.other)
    }

    @Test
    fun debtAndReimbAsOfADay() {
        val d = data()
        assertEquals(0L, d.debtOutstandingAt(day(10, 8)))
        assertEquals(200L, d.debtOutstandingAt(day(10, 9)))
        assertEquals(0L, d.reimbOutstandingAt(day(9, 30)))
        assertEquals(600L, d.reimbOutstandingAt(day(10, 8)))
        assertEquals(d.debtOutstanding(), d.debtOutstandingAt(Long.MAX_VALUE))
    }

    @Test
    fun firstMonthIsEarliestRecord() {
        assertEquals(YearMonth.of(2026, 9), data().firstMonth(today))
    }
}
