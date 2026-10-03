package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 貸款帳戶類型 */
class LoanAccountTest {
    private val day = LocalDate.of(2026, 10, 3).toEpochDay()

    private fun acc(id: Long, name: String, type: AccountType, order: Int, hidden: Boolean = false, initial: Long = 0L) =
        Account(id, name, type.emoji, type, initial, order, hidden)

    private fun txn(id: Long, from: Long?, to: Long? = null) =
        Txn(id, 1L, if (to != null) TxType.TRANSFER else TxType.EXPENSE, 10, null, from, to, day, "", emptyList())

    private fun data(accs: List<Account>, txns: List<Txn>): AppData = Defaults.create().copy(accounts = accs, txns = txns)

    private val accs = listOf(
        acc(1, "現金", AccountType.CASH, 0),
        acc(2, "銀行A", AccountType.BANK, 1),
        acc(3, "銀行B", AccountType.BANK, 2),
        acc(4, "信用卡", AccountType.CARD, 3),
        acc(5, "舊帳戶", AccountType.BANK, 4, hidden = true),
    )

    @Test
    fun loanTypeIsALiabilityLikeCreditCardAndSurvivesSave() {
        assertEquals("貸款", AccountType.LOAN.label)
        // 貸款排在信用卡後面（帳戶分頁依這個順序分組）
        val order = AccountType.values().toList()
        assertTrue(order.indexOf(AccountType.LOAN) == order.indexOf(AccountType.CARD) + 1)
        val loan = acc(9, "信貸", AccountType.LOAN, 5, initial = -200_000L)
        val d = data(accs + loan, listOf(Txn(1, 1L, TxType.TRANSFER, 11_291, null, 2, 9, day, "", emptyList())))
        // 初始欠款 -200,000，還款（轉帳）11,291 後餘額 -188,709；還款不算支出
        assertEquals(-188_709L, d.balances()[9L])
        assertEquals(0L, d.txns.expenseSum())
        val back = Codec.decode(Codec.encode(d))
        assertEquals(AccountType.LOAN, back.accMap[9L]!!.type)
        assertEquals(-200_000L, back.accMap[9L]!!.initial)
    }

    @Test
    fun legacyImportGuessesLoanFromName() {
        assertEquals(AccountType.LOAN, LegacyImport.guessType("信貸"))
        assertEquals(AccountType.LOAN, LegacyImport.guessType("永豐信貸"))
        assertEquals(AccountType.LOAN, LegacyImport.guessType("房貸"))
        assertEquals(AccountType.CARD, LegacyImport.guessType("cube卡"))   // 信用卡不受影響
    }
}
