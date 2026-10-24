package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 依分帳結論結清：Amy 欠我 600、Bob 欠我 300、我欠 Carol 200，照理說我收到 700；
 * 結論是 Bob 給我（存進銀行），帳戶只動實際收到的錢，勾的帳一次結清。
 */
class SettleTest {
    private val day = LocalDate.of(2026, 10, 10).toEpochDay()
    private val cash = Account(1, "現金", "", AccountType.CASH, 0, 0)
    private val bank = Account(2, "銀行", "", AccountType.BANK, 0, 1)
    private val usd = Account(3, "美元", "", AccountType.FOREIGN, 0, 2, currency = "USD")

    private fun exp(id: Long, amount: Long, d: Long) = Txn(id, 1L, TxType.EXPENSE, amount, 1L, 1L, null, d, "", emptyList())

    private fun data(): AppData {
        val t1 = exp(11, 1000, day).withItems(listOf(ReimbItem("Amy", 600)))
        val t2 = exp(12, 500, day + 1).withItems(listOf(ReimbItem("Bob", 300)))
        val t3 = Txn(13, 1L, TxType.EXPENSE, 200, 1L, null, null, day + 2, "", emptyList(), debtItems = listOf(DebtItem("Carol", 200)))
        return Defaults.create().copy(accounts = listOf(cash, bank, usd), txns = sortTxns(listOf(t1, t2, t3)), nextId = 100)
    }

    private val rKeys = listOf(ItemKey(11, 0), ItemKey(12, 0))
    private val dKeys = listOf(ItemKey(13, 0))
    private fun bob(amount: Long) = listOf(SettleMove(true, "Bob", amount, 2L))
    private fun AppData.t(id: Long) = txns.first { it.id == id }

    @Test
    fun expectedNet() {
        assertEquals(700L, data().settleExpected(rKeys, dKeys))
    }

    @Test
    fun exactSettlesEverythingAndOnlyMovesActualMoney() {
        val before = data()
        val after = before.withSettle(rKeys, dKeys, bob(700), day + 5, 600)!!
        // 帳戶只多了 Bob 給的 700
        assertEquals(700L, after.balances()[2L])
        assertEquals(before.balances()[1L], after.balances()[1L])
        // 三項都結清，支出不變
        assertEquals(0L, after.t(11).items[0].remaining)
        assertEquals(0L, after.t(12).items[0].remaining)
        assertTrue(after.t(11).items[0].closed)
        assertEquals(0L, after.debtOutstanding())
        assertEquals(before.txns.expenseSum(), after.txns.expenseSum())
        assertEquals(1, after.settles.size)
        assertEquals(after.settles[0].id, after.t(11).items[0].pays.single().offsetId)
        assertNull(after.t(11).items[0].pays.single().accountId)
        // 刪掉這次結清：全部恢復
        val back = after.withSettleRemoved(after.settles[0].id)!!
        assertEquals(600L, back.t(11).items[0].remaining)
        assertFalse(back.t(11).items[0].closed)
        assertEquals(200L, back.debtOutstanding())
        assertEquals(0L, back.balances()[2L])
        assertTrue(back.settles.isEmpty())
    }

    @Test
    fun lessReceivedCountsAsMineFromNewestReimb() {
        val before = data()
        val after = before.withSettle(rKeys, dKeys, bob(650), day + 5, 600, SettleDiff.SELF)!!
        // 最新的報銷（Bob 那筆）少收 50、結案；少收的算我的支出
        assertTrue(after.t(12).items[0].closed)
        assertEquals(250L, after.t(12).items[0].received)
        assertEquals(before.txns.expenseSum() + 50, after.txns.expenseSum())
        assertEquals(0L, after.debtOutstanding())
        // 刪除後：結案恢復、支出回到原本
        val back = after.withSettleRemoved(after.settles[0].id)!!
        assertFalse(back.t(12).items[0].closed)
        assertEquals(before.txns.expenseSum(), back.txns.expenseSum())
    }

    @Test
    fun lessReceivedKeptOnChosenItem() {
        val after = data().withSettle(rKeys, dKeys, bob(650), day + 5, 600, SettleDiff.KEEP, listOf(ItemKey(11, 0)))!!
        assertEquals(50L, after.t(11).items[0].remaining)     // Amy 還欠 50
        assertEquals(0L, after.t(12).items[0].remaining)
        assertEquals(data().txns.expenseSum(), after.txns.expenseSum())
        // 留不下（選的項目不夠）就不行
        assertNull(data().withSettle(rKeys, dKeys, bob(100), day + 5, 600, SettleDiff.KEEP, listOf(ItemKey(12, 0))))
    }

    @Test
    fun moreReceivedForgivesNewestDebtOrKeepsIt() {
        val before = data()
        val self = before.withSettle(rKeys, dKeys, bob(750), day + 5, 600, SettleDiff.SELF)!!
        // 多收 50：Carol 那筆少還 50、算還清（免掉的從支出扣掉）
        assertTrue(self.t(13).debtItems[0].done)
        assertEquals(0L, self.debtOutstanding())
        assertEquals(before.txns.expenseSum() - 50, self.txns.expenseSum())
        val keep = before.withSettle(rKeys, dKeys, bob(750), day + 5, 600, SettleDiff.KEEP, listOf(ItemKey(13, 0)))!!
        assertEquals(50L, keep.debtOutstanding())               // 我還欠 Carol 50
        assertEquals(before.txns.expenseSum(), keep.txns.expenseSum())
    }

    @Test
    fun badInputs() {
        // 外幣帳戶、金額 0、沒勾帳都不行
        assertNull(data().withSettle(rKeys, dKeys, listOf(SettleMove(true, "Bob", 700, 3L)), day, -1))
        assertNull(data().withSettle(rKeys, dKeys, listOf(SettleMove(true, "Bob", 0, 2L)), day, -1))
        assertNull(data().withSettle(emptyList(), emptyList(), bob(700), day, -1))
    }

    @Test
    fun settlesSurviveSaveAndCountInNetWorth() {
        val after = data().withSettle(rKeys, dKeys, bob(700), day + 5, 600)!!
        val back = Codec.decode(Codec.encode(after))
        assertEquals(after.settles, back.settles)
        assertEquals(after.balances(), back.balances())
        assertTrue(back.nextId > after.settles[0].id)
        // 總資產：最新一點等於帳戶加總扣欠款
        val bal = back.balances()
        val page = back.visibleAccounts.sumOf { back.twdValue(it, bal[it.id] ?: 0L) ?: 0L } - back.debtOutstanding()
        assertEquals(page, back.netWorthAt(listOf(Long.MAX_VALUE)).single().net)
        // 帳戶明細：結清那一筆的餘額
        assertEquals(700L, back.runningBalances(2L)["s${after.settles[0].id}_0"])
        assertEquals(700L, back.balanceAt(2L, day + 5, 23 * 60))
    }
}
