package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 常用帳戶（記一筆選帳戶時放最上面） */
class FrequentAccountsTest {
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
    fun frequentAccountsAreOrderedByUsage() {
        val t = listOf(txn(1, 3), txn(2, 3), txn(3, 3), txn(4, 2), txn(5, 2), txn(6, 1))
        assertEquals(listOf(3L, 2L, 1L), data(accs, t).frequentAccounts(3).map { it.id })
    }

    @Test
    fun transferCountsBothSides() {
        val t = listOf(txn(1, 2, 3), txn(2, 2, 3), txn(3, 1))
        // 帳戶 2、3 各用了 2 次（轉帳兩邊都算），現金 1 次
        assertEquals(listOf(2L, 3L, 1L), data(accs, t).frequentAccounts(3).map { it.id })
    }

    @Test
    fun neverUsedAndHiddenAccountsAreNotListed() {
        val t = listOf(txn(1, 5), txn(2, 5), txn(3, 2))
        val f = data(accs, t).frequentAccounts(3)
        assertEquals(listOf(2L), f.map { it.id })     // 隱藏的 5 不列、沒用過的不列
    }

    @Test
    fun limitAndTieBreakByDisplayOrder() {
        val t = listOf(txn(1, 1), txn(2, 2), txn(3, 3), txn(4, 4))
        // 次數一樣時，照帳戶頁的順序
        assertEquals(listOf(1L, 2L), data(accs, t).frequentAccounts(2).map { it.id })
    }

    @Test
    fun noRecordsMeansNoFrequentAccounts() {
        assertTrue(data(accs, emptyList()).frequentAccounts().isEmpty())
    }
}
