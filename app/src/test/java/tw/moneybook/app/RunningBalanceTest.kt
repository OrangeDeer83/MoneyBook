package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 帳戶明細每一筆做完之後的餘額 */
class RunningBalanceTest {
    private val day = LocalDate.of(2026, 10, 3).toEpochDay()
    private val bank = Account(2, "銀行", AccountType.BANK.emoji, AccountType.BANK, 50_000L, 1)
    private val cash = Account(1, "現金", AccountType.CASH.emoji, AccountType.CASH, 1_000L, 0)

    private fun txn(id: Long, type: TxType, amount: Long, from: Long?, to: Long? = null, d: Long = day, time: Int = -1, fee: Long = 0L, adjust: Boolean = false, items: List<ReimbItem> = emptyList()) =
        Txn(id, 1L, type, amount, null, from, to, d, "", emptyList(), fee = fee, adjust = adjust, time = time, reimbItems = items)
            .let { if (items.isEmpty()) it else it.withItems(items) }

    private fun data(vararg t: Txn) = Defaults.create().copy(accounts = listOf(cash, bank), txns = sortTxns(t.toList()))

    private fun AppData.rb(acc: Long = 2L) = runningBalances(acc)

    @Test
    fun followsDayThenTimeThenEntryOrder() {
        val d = data(
            txn(1, TxType.EXPENSE, 300, 2, d = day - 1),                      // 昨天，沒有時間
            txn(2, TxType.EXPENSE, 100, 2, time = 600),
            txn(3, TxType.INCOME, 1000, 2, time = 700),
            txn(4, TxType.EXPENSE, 200, 2, time = 900),
        )
        val r = d.rb()
        assertEquals(49_700L, r["t1"])
        assertEquals(49_600L, r["t2"])
        assertEquals(50_600L, r["t3"])
        assertEquals(50_400L, r["t4"])
    }

    @Test
    fun lastBalanceEqualsCurrentBalance() {
        val d = data(
            txn(1, TxType.EXPENSE, 300, 2, d = day - 2),
            txn(2, TxType.TRANSFER, 5_000, 2, 1, d = day - 1, fee = 15),
            txn(3, TxType.INCOME, 800, 2, time = 500),
            txn(4, TxType.INCOME, 70, 2, adjust = true, time = 800),
            txn(5, TxType.EXPENSE, 20, 2, time = 800),
        )
        val bal = d.balances()
        for (acc in listOf(1L, 2L)) {
            // 這個帳戶最新的一筆（日期 → 時間 → 輸入順序最大的），做完之後的餘額就是目前餘額
            val newest = sortTxns(d.txns.filter { it.accountId == acc || it.toAccountId == acc }).first()
            assertEquals("帳戶 $acc", bal[acc], d.runningBalances(acc)["t${newest.id}"])
        }
    }

    @Test
    fun transferAffectsBothAccountsAndFeeComesFromTheSource() {
        val d = data(txn(1, TxType.TRANSFER, 5_000, 2, 1, fee = 15))
        assertEquals(50_000L - 5_015L, d.rb(2)["t1"])
        assertEquals(1_000L + 5_000L, d.rb(1)["t1"])
    }

    @Test
    fun balanceAdjustmentIsPartOfTheSequence() {
        val d = data(txn(1, TxType.EXPENSE, 100, 2, time = 600), txn(2, TxType.INCOME, 2_000, 2, adjust = true, time = 700))
        assertEquals(49_900L, d.rb()["t1"])
        assertEquals(51_900L, d.rb()["t2"])
    }

    @Test
    fun unrelatedTransactionsAreNotListed() {
        val d = data(txn(1, TxType.EXPENSE, 100, 1))
        assertNull(d.rb(2)["t1"])
        assertEquals(900L, d.rb(1)["t1"])
    }

    @Test
    fun reimbursementReceiptsAreAfterTheDaysTransactions() {
        // 8 元的支出 + 當天收到 3 元的報銷款（沒有時間，排當天最後）
        val item = ReimbItem("A", 3L, listOf(ReimbPay(day, 2L, 3L)), true)
        val d = data(txn(1, TxType.EXPENSE, 8, 2, time = 600, items = listOf(item)), txn(2, TxType.EXPENSE, 100, 2, time = 1000))
        val r = d.rb()
        assertEquals(49_992L, r["t1"])
        assertEquals(49_892L, r["t2"])
        assertEquals(49_895L, r["p1_0"])        // 報銷款收在當天最後
        assertEquals(d.balances()[2L], r["p1_0"])
    }

    @Test
    fun unknownAccountHasNothing() {
        assertEquals(emptyMap<String, Long>(), data(txn(1, TxType.EXPENSE, 1, 2)).runningBalances(999L))
    }
}
