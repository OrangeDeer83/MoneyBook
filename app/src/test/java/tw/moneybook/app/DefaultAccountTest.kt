package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 記一筆預設帳戶＝常用帳戶第一個；投資帳戶買賣預設的扣款帳戶 */
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

    @Test
    fun payAccountForTrades() {
        val choices = listOf(card, bank, cash)
        // 沒設定：常用的帳戶裡第一個不是信用卡的（信用卡用最多次也不當預設）
        val d = data(card, bank, cash, stock, uses = listOf(1, 1, 1, 2))
        assertEquals(2L, d.defaultPayAccount(stock, choices))
        // 設定了就用設定的；-1 是不連動
        assertEquals(3L, d.defaultPayAccount(stock.copy(payAccountId = 3), choices))
        assertNull(d.defaultPayAccount(stock.copy(payAccountId = -1), choices))
        // 設定的帳戶不能選（例如被隱藏）：照沒設定
        assertEquals(2L, d.defaultPayAccount(stock.copy(payAccountId = 77), choices))
    }

    @Test
    fun payAccountSurvivesSave() {
        val d = data(card, bank, stock.copy(payAccountId = 2))
        assertEquals(2L, Codec.decode(Codec.encode(d)).accMap[4L]!!.payAccountId)
    }
}
