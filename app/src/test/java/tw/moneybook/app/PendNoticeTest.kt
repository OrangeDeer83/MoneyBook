package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 刷卡待確認的吉祥物通知內容 */
class PendNoticeTest {
    private val base = Defaults.create()
    private val cube = Account(9001, "CUBE 卡", "img:acc_card", AccountType.CARD, 0L, 1)
    private val food = base.categories.first { it.kind == TxType.EXPENSE && it.parentId == null }
    private var nid = 100L
    private fun data(vararg txns: Txn) = base.copy(
        accounts = base.accounts + cube, notiApps = listOf(NotiApp("cathay", "國泰", listOf(9001L)), NotiApp("x", "X 銀行")),
        txns = sortTxns(txns.toList()),
    )
    private fun pend(id: Long, merchant: String, pkg: String = "cathay", amount: Long = 85, last4: String = "") =
        CardPend(id, listOf(NotiSrc(id, pkg, if (pkg == "x") "X 銀行" else "國泰", "", last4)), amount, "", merchant, true, "", 20406L, 551, id)

    @Test
    fun singleWithCardAndCategoryCanRecordOnTheNotification() {
        val d = data(Txn(nid++, base.books.first().id, TxType.EXPENSE, 50, food.id, 9001, null, 20400, "統一超商", emptyList()))
        val n = d.pendNotice(listOf(pend(1, "統一超商")), "小鹿")!!
        assertEquals("小鹿：刷了一筆 ${formatMoney(85)}", n.title)
        assertEquals("統一超商・CUBE 卡・${food.name}", n.body)
        assertEquals(1L, n.single)
    }

    @Test
    fun noCategoryOrNoCardOpensTheApp() {
        val d = data()
        val a = d.pendNotice(listOf(pend(1, "鼎泰豐")), "小鹿")!!
        assertEquals("鼎泰豐・CUBE 卡・還沒有分類", a.body)
        assertNull(a.single)
        val b = d.pendNotice(listOf(pend(1, "7-ELEVEN", pkg = "x", last4 = "9012")), "小鹿")!!
        assertEquals("7-ELEVEN・X 銀行 末四碼 9012・點開來選是哪張卡", b.body)
        assertNull(b.single)
    }

    @Test
    fun severalItemsAndNone() {
        val d = data()
        val list = listOf(pend(1, "統一超商"), pend(2, "全聯", amount = 350), pend(3, ""), pend(4, "鼎泰豐", amount = 120))
        val n = d.pendNotice(list, "小鹿")!!
        assertEquals("小鹿：有 4 筆刷卡等你確認", n.title)
        assertEquals("統一超商 ${formatMoney(85)}、全聯 ${formatMoney(350)}、刷卡 ${formatMoney(85)}、…", n.body)
        assertEquals(4, n.lines.size)
        assertNull(n.single)
        // 處理完了：不發
        assertNull(d.pendNotice(list.map { it.copy(state = PendState.DONE) }, "小鹿"))
    }

    @Test
    fun exactlyOneDollarMayBeATestCharge() {
        val d = data(Txn(nid++, base.books.first().id, TxType.EXPENSE, 50, food.id, 9001, null, 20400, "台灣中油", emptyList()))
        // 剛好 $1：可能是試刷，通知上不能直接記
        val one = pend(1, "台灣中油", amount = 1)
        assertEquals(true, one.maybeTest)
        val n = d.pendNotice(listOf(one), "小鹿")!!
        assertEquals("小鹿：刷了一筆 ${formatMoney(1)}，可能是試刷", n.title)
        assertEquals("台灣中油・CUBE 卡・點開來選要不要先記", n.body)
        assertNull(n.single)
        // $2、外幣 1.00、分期、退款都不算
        assertEquals(false, pend(2, "7-11", amount = 2).maybeTest)
        assertEquals(false, one.copy(cur = "USD", amount = 100).maybeTest)
        assertEquals(false, one.copy(cur = "JPY").maybeTest)
        assertEquals(false, one.copy(kind = PendKind.REFUND).maybeTest)
        // 收到的通知：選了不記標「試刷，不記」
        val cap = CapturedNoti(1, "cathay", "國泰", "", "台灣中油 NT$1", 0L)
        assertEquals("試刷，不記", d.notiTag(cap, listOf(one.copy(state = PendState.NOT))).second)
        assertEquals("不是消費", d.notiTag(cap.copy(id = 2), listOf(pend(2, "7-11", amount = 2).copy(state = PendState.NOT))).second)
    }
}
