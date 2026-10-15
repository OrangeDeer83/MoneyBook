package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 欠款：別人先墊、我還沒付的支出（見 Debt.kt） */
class DebtTest {
    private val bank = Account(10, "銀行", AccountType.BANK.emoji, AccountType.BANK, 50_000L, 1)
    private val jpy = Account(12, "日圓帳戶", AccountType.BANK.emoji, AccountType.BANK, 100_000L, 2, currency = "JPY")

    private fun base(vararg debts: Txn): AppData {
        val b = Defaults.create()
        return b.copy(accounts = listOf(bank, jpy), txns = debts.toList().map { it.copy(bookId = b.currentBook.id) }.sortedByDescending { it.id }, nextId = 1000)
    }

    private fun debt(id: Long, amount: Long, day: Long, who: String = "Amy", note: String = "") =
        Txn(id, 1L, TxType.EXPENSE, amount, null, null, null, day, note, emptyList(), debtTo = who, debtOrig = amount)

    private fun fxDebt(id: Long, twd: Long, units: Long, day: Long, who: String = "Ken") =
        Txn(id, 1L, TxType.EXPENSE, twd, null, null, null, day, "", emptyList(), debtTo = who, debtOrig = twd, fxSpendAmount = units, fxSpendCur = "JPY", fxPending = true)

    private fun AppData.t(id: Long) = txns.first { it.id == id }

    @Test
    fun openDebtDoesNotTouchAnyAccountButCountsAsExpense() {
        val d = base(debt(1, 800, 100))
        assertEquals(50_000L, d.balances()[10])
        assertEquals(800L, d.txns.expenseSum())                      // 記的那天就算支出
        assertEquals(800L, d.debtOutstanding())
    }

    @Test
    fun exactRepayClearsDebtAndMovesBalanceOnce() {
        val d = base(debt(1, 800, 100)).withDebtRepaid("Amy", "", 10, 110, -1, 800, 0, false)!!
        assertEquals(49_200L, d.balances()[10])
        assertEquals(800L, d.t(1).amount)
        assertTrue(d.t(1).debtDone)
        assertEquals(0L, d.debtOutstanding())
        assertEquals(800L, d.txns.expenseSum())                      // 還款不再算一次支出
    }

    @Test
    fun shortRepayCanContinueOwing() {
        // 機票 2000（10/10）、晚餐 800（10/15）共欠 2800，還 2500：先還舊的，機票還清、晚餐還 500
        val d0 = base(debt(1, 2000, 10, note = "機票"), debt(2, 800, 15, note = "晚餐"))
        val d = d0.withDebtRepaid("Amy", "", 10, 20, -1, 2500, 0, false)!!
        assertTrue(d.t(1).debtDone)
        assertFalse(d.t(2).debtDone)
        assertEquals(300L, d.t(2).debtRemainingUnits)
        assertEquals(800L, d.t(2).amount)                            // 還沒結清，支出不變
        assertEquals(300L, d.debtOutstanding())
        assertEquals(47_500L, d.balances()[10])
    }

    @Test
    fun shortRepayCanSettleAndReduceExpense() {
        val d0 = base(debt(1, 2000, 10), debt(2, 800, 15))
        val d = d0.withDebtRepaid("Amy", "", 10, 20, -1, 2500, 0, true)!!
        assertTrue(d.t(1).debtDone && d.t(2).debtDone)
        assertEquals(2000L, d.t(1).amount)
        assertEquals(500L, d.t(2).amount)                            // 被免掉的 300 從支出扣掉
        assertEquals(0L, d.debtOutstanding())
        assertEquals(2500L, d.txns.expenseSum())
    }

    @Test
    fun overRepayAddsExtraToLastDebtExpense() {
        val d0 = base(debt(1, 2000, 10), debt(2, 800, 15))
        val d = d0.withDebtRepaid("Amy", "", 10, 20, -1, 2900, 0, false)!!
        assertEquals(2000L, d.t(1).amount)
        assertEquals(900L, d.t(2).amount)                            // 多付的 100 算在最後一筆
        assertTrue(d.t(1).debtDone && d.t(2).debtDone)
        assertEquals(47_100L, d.balances()[10])
        assertEquals(2900L, d.txns.expenseSum())
    }

    @Test
    fun removingAPayRestoresTheDebt() {
        val d0 = base(debt(1, 800, 10))
        val paid = d0.withDebtRepaid("Amy", "", 10, 20, -1, 900, 0, false)!!
        assertEquals(900L, paid.t(1).amount)
        val back = paid.withDebtPayRemoved(1, 0)!!
        assertEquals(800L, back.t(1).amount)
        assertFalse(back.t(1).debtDone)
        assertEquals(800L, back.debtOutstanding())
        assertEquals(50_000L, back.balances()[10])
        assertNull(paid.withDebtPayRemoved(1, 5))
    }

    @Test
    fun otherPeopleAndBadInputAreRejected() {
        val d = base(debt(1, 800, 10))
        assertNull(d.withDebtRepaid("Ken", "", 10, 20, -1, 100, 0, false))        // 沒有欠 Ken
        assertNull(d.withDebtRepaid("Amy", "", 10, 20, -1, 0, 0, false))
        assertNull(d.withDebtRepaid("Amy", "", 99, 20, -1, 100, 0, false))        // 沒有這個帳戶
        assertNull(d.withDebtRepaid("Amy", "", 12, 20, -1, 100, 0, false))        // 台幣欠款不能用外幣帳戶還
    }

    @Test
    fun foreignDebtRepaidInTwdUsesActualAmount() {
        // 朋友先墊 ¥12,000，預估 $2,640；實際用台幣還 $2,700
        val d0 = base(fxDebt(1, 2640, 12_000, 10))
        assertEquals(2640L, d0.debtOutstanding())
        val d = d0.withDebtRepaid("Ken", "JPY", 10, 20, -1, 12_000, 2700, false)!!
        assertEquals(2700L, d.t(1).amount)                           // 支出改成實際台幣
        assertFalse(d.t(1).fxPending)
        assertTrue(d.t(1).debtDone)
        assertEquals(47_300L, d.balances()[10])
    }

    @Test
    fun foreignDebtPartialRepayKeepsEstimateUntilDone() {
        val d0 = base(fxDebt(1, 2640, 12_000, 10))
        val d1 = d0.withDebtRepaid("Ken", "JPY", 10, 20, -1, 6_000, 1_350, false)!!
        assertEquals(6_000L, d1.t(1).debtRemainingUnits)
        assertEquals(2640L, d1.t(1).amount)
        assertTrue(d1.t(1).fxPending)
        assertEquals(1320L, d1.debtOutstanding())                    // 剩 ¥6,000 × 預估匯率
        val d2 = d1.withDebtRepaid("Ken", "JPY", 10, 25, -1, 6_000, 1_400, false)!!
        assertEquals(2750L, d2.t(1).amount)                          // 1,350 + 1,400
        assertTrue(d2.t(1).debtDone)
        assertNull(d1.withDebtRepaid("Ken", "JPY", 10, 25, -1, 7_000, 1_400, false))   // 外幣不能多還
        assertNull(d1.withDebtRepaid("Ken", "JPY", 10, 25, -1, 6_000, 0, false))       // 台幣帳戶要填實際台幣
    }

    @Test
    fun foreignDebtRepaidFromSameCurrencyAccountDeductsForeign() {
        val d = base(fxDebt(1, 2640, 12_000, 10)).withDebtRepaid("Ken", "JPY", 12, 20, -1, 12_000, 0, false)!!
        assertEquals(88_000L, d.balances()[12])                      // 日圓帳戶 100,000 − 12,000
        assertEquals(2640L, d.t(1).amount)                           // 用預估匯率，支出不變
        assertTrue(d.t(1).debtDone)
    }

    @Test
    fun runningBalancesIncludeRepayments() {
        val d = base(debt(1, 800, 10)).withDebtRepaid("Amy", "", 10, 20, -1, 300, 0, false)!!
        val run = d.runningBalances(10)
        assertEquals(49_700L, run["d1_0"])
        assertEquals(d.balances()[10], run["d1_0"])
    }

    @Test
    fun separatesPeopleAndCurrencies() {
        val d = base(debt(1, 800, 10), debt(2, 500, 11, who = "Ken"), fxDebt(3, 2640, 12_000, 12))
        val g = d.debtGroups()
        assertEquals(3, g.size)
        assertEquals(setOf("Amy" to "", "Ken" to "", "Ken" to "JPY"), g.map { it.who to it.currency }.toSet())
        assertEquals(listOf("Ken", "Amy"), d.copy(txns = d.txns + debt(4, 100, 13, who = "Ken")).debtNames())
    }

    @Test
    fun fxDebtIsNotListedAsPendingCardCharge() {
        assertTrue(base(fxDebt(1, 2640, 12_000, 10)).fxPendingList().isEmpty())
    }

    @Test
    fun csvKeepsDebtWithoutAccount() {
        val d = base(debt(1, 800, 10, note = "晚餐"), debt(2, 500, 11, who = "Ken").copy(debtDone = true))
        val csv = String(CsvIO.export(d), Charsets.UTF_8)
        val (back, n) = CsvIO.import(Defaults.create(), csv)
        assertEquals(2, n)
        val a = back.txns.first { it.debtTo == "Amy" }
        assertNull(a.accountId)                                      // 不能補成預設帳戶
        assertEquals(800L, a.debtOrig)
        assertFalse(a.debtDone)
        assertTrue(back.txns.first { it.debtTo == "Ken" }.debtDone)
    }

    @Test
    fun debtFieldsSurviveSaveAndLoad() {
        val d = base(debt(1, 800, 10, note = "晚餐")).withDebtRepaid("Amy", "", 10, 20, 600, 300, 0, false)!!
        val back = Codec.decode(Codec.encode(d))
        val t = back.t(1)
        assertEquals("Amy", t.debtTo)
        assertEquals(800L, t.debtOrig)
        assertEquals(listOf(DebtPay(20, 10, 300, 600, 0, 300)), t.debtPays)
        assertEquals(d.balances()[10], back.balances()[10])
    }
}
