package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** 記一筆預設帳戶＝常用帳戶第一個 */
class DefaultAccountTest {
    private val today = LocalDate.now().toEpochDay()
    private val card = Account(1, "玉山unicard", "", AccountType.CARD, 0, 0)
    private val bank = Account(2, "永豐大戶", "", AccountType.BANK, 0, 1)
    private val cash = Account(3, "錢包", "", AccountType.CASH, 0, 2)
    private val stock = Account(4, "國泰證券", "", AccountType.INVEST, 0, 3)

    private fun spend(id: Long, acc: Long) = Txn(id, 1L, TxType.EXPENSE, 100, 1L, acc, null, today - 1, "", emptyList())

    private fun data(vararg accs: Account, uses: List<Long> = emptyList()): AppData =
        Defaults.create().copy(accounts = accs.toList(), txns = sortTxns(uses.mapIndexed { i, a -> spend(100L + i, a) }), nextId = 999)

    @Test
    fun recordDefaultIsMostUsedNotFirstInOrder() {
        // 帳戶排序第一個是信用卡，但最常用的是錢包
        val d = data(card, bank, cash, uses = listOf(3, 3, 3, 2))
        assertEquals(3L, d.defaultAccount(d.visibleAccounts))
        // 打星號的優先
        val s = data(card, bank.copy(favorite = true), cash, uses = listOf(3, 3, 3))
        assertEquals(2L, s.defaultAccount(s.visibleAccounts))
        // 都沒用過：照排序第一個
        val e = data(card, bank, cash)
        assertEquals(1L, e.defaultAccount(e.visibleAccounts))
    }
}
