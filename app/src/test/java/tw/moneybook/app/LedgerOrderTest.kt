package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Test
import tw.moneybook.app.ui.ledgerEntries
import tw.moneybook.app.ui.reimbRecvsOf

/** 帳戶明細同一天的順序：由下往上讀，餘額一筆一筆加上去，最上面是最多的（和算餘額的順序剛好相反） */
class LedgerOrderTest {
    private val bank = Account(10, "銀行", AccountType.BANK.emoji, AccountType.BANK, 50_000L, 1)
    private val cash = Account(11, "現金", AccountType.CASH.emoji, AccountType.CASH, 0L, 0)
    private fun day(d: Int) = 20_000L + d

    /** 一筆待報銷的支出（記在現金），報銷款當天收進銀行 */
    private fun reimbExpense(id: Long, expDay: Int, amount: Long, payDay: Int, payTime: Int = -1): Txn {
        val t = Txn(id, 1L, TxType.EXPENSE, amount, null, 11L, null, day(expDay), "", emptyList(), reimb = 1, reimbAmount = amount)
        return t.withItems(listOf(ReimbItem("公司", amount, pays = listOf(ReimbPay(day(payDay), 10L, amount, payTime)))))
    }

    private fun data(vararg t: Txn) = Defaults.create().copy(accounts = listOf(cash, bank), txns = sortTxns(t.toList()))

    @Test
    fun sameDayReimbReceiptsReadBottomToTopWithGrowingBalance() {
        // 三筆待報銷的支出日期亂排（編號小的日期反而晚），報銷款都在同一天收進銀行
        val d = data(reimbExpense(1, 5, 100, 20), reimbExpense(2, 9, 200, 20), reimbExpense(3, 7, 300, 20))
        val running = d.runningBalances(10)
        val rows = ledgerEntries(emptyList(), reimbRecvsOf(d.txns))
        val balances = rows.map { running["p${it.recv!!.t.id}_${it.recv!!.idx}"]!! }
        // 算餘額是依記錄編號：編號 1 → 50,100、2 → 50,300、3 → 50,600；明細由上到下要是 3、2、1（餘額由大到小）
        assertEquals(listOf(50_600L, 50_300L, 50_100L), balances)
        // 每一筆的餘額 − 它自己的金額 = 下面那一筆的餘額
        rows.zipWithNext().forEach { (up, down) ->
            val upBal = running["p${up.recv!!.t.id}_${up.recv!!.idx}"]!!
            val downBal = running["p${down.recv!!.t.id}_${down.recv!!.idx}"]!!
            assertEquals(downBal, upBal - up.recv!!.pay.amount)
        }
    }

    @Test
    fun receiptsWithATimeFollowTheirTimeAndNoTimeComesLast() {
        // 1 號有收款時間 09:00（540 分）、2 號沒有時間：同一天，沒有時間的當成當天最後 → 在最上面
        val d = data(reimbExpense(1, 5, 100, 20, payTime = 540), reimbExpense(2, 5, 200, 20))
        val running = d.runningBalances(10)
        val rows = ledgerEntries(emptyList(), reimbRecvsOf(d.txns))
        assertEquals(listOf(2L, 1L), rows.map { it.recv!!.t.id })
        assertEquals(listOf(50_300L, 50_100L), rows.map { running["p${it.recv!!.t.id}_${it.recv!!.idx}"]!! })
    }

    @Test
    fun receiptComesAfterTheExpenseOfTheSameRecordOnTheSameDay() {
        // 同一天記的支出，銀行收到它的報銷款：收款排在比較上面（算餘額時收款在後）
        val t = reimbExpense(1, 20, 100, 20).copy(accountId = 10L)
        val d = data(t)
        val rows = ledgerEntries(listOf(t), reimbRecvsOf(d.txns))
        assertEquals(listOf(null, 1L), rows.map { it.txn?.id })
    }
}
