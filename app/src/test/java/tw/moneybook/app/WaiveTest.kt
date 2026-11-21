package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 免除：別人不用還我（報銷不追了）、我不用還別人（欠款算還清），不動任何帳戶（見 Waive.kt） */
class WaiveTest {
    private val bank = Account(10, "銀行", AccountType.BANK.emoji, AccountType.BANK, 50_000L, 1)

    private fun base(vararg txns: Txn): AppData {
        val b = Defaults.create()
        return b.copy(accounts = listOf(bank), txns = txns.toList().map { it.copy(bookId = b.currentBook.id) }.sortedByDescending { it.id }, nextId = 1000)
    }

    /** 我從銀行付了 amount，其中 owed 是 Amy 要還我的（報銷） */
    private fun claim(id: Long, amount: Long, owed: Long, who: String = "Amy", received: Long = 0L) =
        Txn(id, 1L, TxType.EXPENSE, amount, null, 10L, null, 100, "", emptyList())
            .withItems(listOf(ReimbItem(who, owed, if (received > 0L) listOf(ReimbPay(101, 10L, received)) else emptyList())))

    /** Amy 幫我付了 amount（我欠她），已經還了 paid */
    private fun debt(id: Long, amount: Long, who: String = "Amy", paid: Long = 0L) =
        Txn(id, 1L, TxType.EXPENSE, amount, null, null, null, 100, "", emptyList(),
            debtItems = listOf(DebtItem(who, amount, pays = if (paid > 0L) listOf(DebtPay(101, 10L, paid, units = paid)) else emptyList())))

    private fun AppData.t(id: Long) = txns.first { it.id == id }

    @Test
    fun owedToMeBecomesMyOwnSpendingWithoutMovingMoney() {
        val d = base(claim(1, 1_000, 1_000), claim(2, 450, 450))
        val bal = d.balances()[10]
        val w = d.withWaive("Amy", true, listOf(reimbKey(1, 0)), 200, 600, "生日請客")!!
        assertTrue(w.t(1).items[0].closed)
        assertEquals(0L, w.t(1).items[0].remaining)
        assertEquals(1_000L, w.t(1).spent)                          // 那 $1,000 算回我自己的支出
        assertFalse(w.t(2).items[0].closed)                          // 沒勾的不動
        assertEquals(bal, w.balances()[10])                          // 沒有錢進出
        val rec = w.waives.single()
        assertEquals(mapOf(reimbKey(1, 0) to 1_000L), rec.items)
        assertEquals("生日請客", rec.note)
        assertEquals(1_000L, w.waiveTwd(rec))
        // 刪掉：恢復成待收
        val back = w.withWaiveRemoved(rec.id)!!
        assertFalse(back.t(1).items[0].closed)
        assertEquals(1_000L, back.t(1).items[0].remaining)
        assertTrue(back.waives.isEmpty())
    }

    @Test
    fun partlyReceivedWaivesOnlyTheRest() {
        val d = base(claim(1, 1_000, 1_000, received = 300))
        val w = d.withWaive("Amy", true, listOf(reimbKey(1, 0)), 200, 600)!!
        assertEquals(700L, w.waives.single().items.values.single())
        assertEquals(700L, w.t(1).spent)
    }

    @Test
    fun iDoNotNeedToPayBackIsDeductedFromSpending() {
        val d = base(debt(1, 800, paid = 200))
        val bal = d.balances()[10]
        val w = d.withWaive("Amy", false, listOf(debtKey(1, 0)), 200, 600)!!
        val item = w.t(1).debtItems[0]
        assertTrue(item.done)
        assertEquals(0L, item.remainingUnits)
        assertEquals(200L, w.t(1).spent)                             // 只算真的還的 $200
        assertEquals(0L, w.debtOutstanding())
        assertEquals(bal, w.balances()[10])
        assertEquals(600L, w.waives.single().items.values.single())
        val back = w.withWaiveRemoved(w.waives.single().id)!!
        assertFalse(back.t(1).debtItems[0].done)
        assertEquals(600L, back.debtOutstanding())
    }

    @Test
    fun rejectsSettledOrOtherPeoplesItems() {
        val d = base(claim(1, 1_000, 1_000), claim(2, 500, 500, who = "Bob"), debt(3, 800))
        assertNull(d.withWaive("Amy", true, emptyList(), 200, 600))
        assertNull(d.withWaive("Amy", true, listOf(reimbKey(2, 0)), 200, 600))   // Bob 的
        assertNull(d.withWaive("Amy", true, listOf(debtKey(3, 0)), 200, 600))    // 方向不對
        val once = d.withWaive("Amy", true, listOf(reimbKey(1, 0)), 200, 600)!!
        assertNull(once.withWaive("Amy", true, listOf(reimbKey(1, 0)), 200, 600)) // 已經結清
    }

    @Test
    fun savedAndLoaded() {
        val d = base(claim(1, 1_000, 1_000), debt(2, 800)).let {
            it.withWaive("Amy", true, listOf(reimbKey(1, 0)), 200, 600, "請客")!!.withWaive("Amy", false, listOf(debtKey(2, 0)), 201, -1)!!
        }
        val back = Codec.decode(Codec.encode(d))
        assertEquals(d.waives, back.waives)
        assertTrue(back.nextId > d.waives.maxOf { it.id })
        // 舊資料沒有 waives
        assertTrue(Codec.decode(Codec.encode(base(claim(1, 1_000, 1_000)))).waives.isEmpty())
    }
}
