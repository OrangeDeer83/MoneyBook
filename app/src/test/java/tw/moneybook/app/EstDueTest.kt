package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 預估放太久（草圖 ci-out/noti_test_charge3.png）：試刷 $1 隔天起、預先授權 14 天起提醒；通知每筆只發一次；填金額 */
class EstDueTest {
    private val base = Defaults.create()
    private val book = base.currentBook.id
    private val cat = base.categories.first { it.kind == TxType.EXPENSE && it.parentId != null }
    private val acc = base.accounts.first().id
    private val today = LocalDate.of(2026, 11, 28).toEpochDay()

    private fun txn(id: Long, amount: Long, daysAgo: Long, shop: String, est: Boolean = true) =
        Txn(id, book, TxType.EXPENSE, amount, cat.id, acc, null, today - daysAgo, "", emptyList(), est = est, shop = shop)

    private fun data(vararg t: Txn) = base.copy(txns = t.toList())

    @Test
    fun testChargeFromNextDayAndPreauthAfter14Days() {
        val d = data(
            txn(1, 1, 0, "台灣中油"),           // 今天記的試刷：當天還不提醒
            txn(2, 1, 1, "全國加油站"),         // 昨天的試刷：提醒
            txn(3, 3000, 13, "飯店"),           // 預先授權 13 天：還在等請款
            txn(4, 5000, 14, "租車"),           // 預先授權 14 天：提醒
            txn(5, 1, 5, "不是預估", est = false), // 已經不是預估
        )
        assertEquals(listOf(4L, 2L), d.estDue(today).map { it.id })
        assertTrue(d.txnMap[2]!!.isTestEst)
        assertFalse(d.txnMap[4]!!.isTestEst)
        assertFalse(d.txnMap[5]!!.isTestEst)
    }

    @Test
    fun noticeOnlyOncePerTxn() {
        val d = data(txn(2, 1, 1, "台灣中油"))
        val n = d.estNotice(today, emptySet(), "小咪")!!
        assertEquals("小咪：台灣中油 的試刷還沒填實際金額", n.title)
        assertEquals(listOf(2L), n.ids)
        // 通知過就不再發，但首頁還是看得到
        assertNull(d.estNotice(today, setOf(2L), "小咪"))
        assertEquals(1, d.estDue(today).size)
        // 多一筆新的：只通知新的那筆
        val d2 = data(txn(2, 1, 1, "台灣中油"), txn(4, 5000, 20, "租車"))
        val n2 = d2.estNotice(today, setOf(2L), "小咪")!!
        assertEquals(listOf(4L), n2.ids)
        assertEquals("小咪：租車 預估 \$5,000 還沒請款", n2.title)
        // 兩筆都沒通知過：合在一則
        val n3 = d2.estNotice(today, emptySet(), "小咪")!!
        assertEquals("小咪：有 2 筆預估的刷卡還沒填實際金額", n3.title)
        assertEquals(setOf(2L, 4L), n3.ids.toSet())
    }

    @Test
    fun fillAmountKeepsDayAndIsNoLongerEstimate() {
        val d = data(txn(2, 1, 1, "台灣中油"))
        val t = d.txnMap[2]!!
        val filled = d.settlePreauth(2, 1280, t.day)
        val f = filled.txnMap[2]!!
        assertEquals(1280L, f.amount)
        assertFalse(f.est)
        assertEquals(t.day, f.day)
        assertEquals(t.billMonth, f.billMonth)
        assertTrue(filled.estDue(today).isEmpty())
    }

    @Test
    fun nameFallsBackToNoteThenCategory() {
        val d = data(txn(2, 1, 1, ""))
        assertEquals(cat.name, d.estName(d.txnMap[2]!!))
        assertEquals("加油", d.estName(d.txnMap[2]!!.copy(note = "加油")))
    }
}
