package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 刷卡待確認：自動帶分類、可能已經記過、記成的那一筆 */
class CardGuessTest {
    private val base = Defaults.create()
    private val cats = base.categories.filter { it.kind == TxType.EXPENSE && it.parentId == null }
    private val food = cats[0].id
    private val traffic = cats[1].id
    private val shop = cats[2].id
    private val cube = Account(9001, "CUBE 卡", "img:acc_card", AccountType.CARD, 0L, 1)
    private val gas = Account(9002, "加油卡", "img:acc_card", AccountType.CARD, 0L, 2)
    private val book = base.books.first().id
    private var nid = 100L
    private fun t(cat: Long?, note: String, acc: Long = 9001, day: Long = 20400, amount: Long = 100, tags: List<String> = emptyList()) =
        Txn(nid++, book, TxType.EXPENSE, amount, cat, acc, null, day, note, tags)
    private fun data(vararg txns: Txn) = base.copy(accounts = base.accounts + cube + gas, txns = sortTxns(txns.toList()))
    private fun pend(amount: Long = 85, merchant: String = "統一超商", day: Long = 20406, cur: String = "") =
        CardPend(1, listOf(NotiSrc(1, "cathay", "國泰", "", "")), amount, cur, merchant, true, "", day, 551, 0L)

    @Test
    fun shopNamesCompare() {
        assertEquals("統一超商", shopKey("統一超商（OP錢包）"))
        assertEquals("spotify", shopKey("SPOTIFY P1F2"))
        assertEquals("lixinemporiumcol", shopKey("LI XIN EMPORIUM CO., L"))
        assertEquals("7eleven", shopKey("7-ELEVEN"))
        assertTrue(sameShop(shopKey("統一超商"), shopKey("統一超商 信義門市")))
        assertTrue(sameShop(shopKey("統一超商 信義門市"), shopKey("統一超商 松仁門市")))
        assertTrue(sameShop(shopKey("LI XIN EMPORIUM CO., L"), shopKey("LI XIN EMPORIUM CO., LTD")))
        assertTrue(sameShop(shopKey("統一超商"), shopKey("統一超商 買咖啡")))
        // 甲：2 個字也比（「全家」對到「全家出遊晚餐」）
        assertTrue(sameShop(shopKey("全家"), shopKey("全家出遊晚餐")))
        assertFalse(sameShop(shopKey("Uber Eats"), shopKey("Uber Trip")))
        assertFalse(sameShop(shopKey("全"), shopKey("全家")))
    }

    @Test
    fun sameShopLastCategoryWins() {
        // 新到舊：最新那筆統一超商記成購物
        val d = data(t(food, "統一超商 信義門市", day = 20400), t(shop, "統一超商（OP錢包）", day = 20402), t(traffic, "捷運", day = 20403))
        assertEquals(shop, d.guessCat("統一超商 松仁門市", 9001))
        // 沒有同一家、這張卡也看不出習慣：不帶
        assertNull(d.guessCat("鼎泰豐", 9001))
        assertNull(d.guessCat("", null))
    }

    @Test
    fun cardHabitNeedsMoreThanHalfAndFiveRecords() {
        val six = (1..6).map { t(traffic, "加油$it", acc = 9002, day = 20390L + it) }
        assertEquals(traffic, data(*six.toTypedArray()).guessCat("台塑石化", 9002))
        // 剛好一半不算
        val half = (1..3).map { t(traffic, "a$it", acc = 9002) } + (1..3).map { t(food, "b$it", acc = 9002) }
        assertNull(data(*half.toTypedArray()).guessCat("某商店", 9002))
        // 少於 5 筆不算
        assertNull(data(*(1..4).map { t(traffic, "c$it", acc = 9002) }.toTypedArray()).guessCat("某商店", 9002))
        // 訂閱記的不算
        val subs = (1..6).map { t(food, "s$it", acc = 9002, tags = listOf(Sub.TAG)) }
        assertNull(data(*subs.toTypedArray()).guessCat("某商店", 9002))
    }

    @Test
    fun possiblyRecordedAlready() {
        val mine = t(food, "午餐", day = 20406, amount = 85)
        val d = data(mine, t(food, "別天", day = 20405, amount = 85), t(food, "別張卡", acc = 9002, day = 20406, amount = 85))
        assertEquals(mine.id, d.possibleHad(pend(), 9001, emptySet())?.id)
        // 已經被別筆待確認認走的不算
        assertNull(d.possibleHad(pend(), 9001, setOf(mine.id)))
        // 金額不一樣、卡不知道
        assertNull(d.possibleHad(pend(amount = 86), 9001, emptySet()))
        assertNull(d.possibleHad(pend(), null, emptySet()))
        // 有優惠：比記的時候的總額（原價 − 優惠）
        val disc = mine.copy(amount = 100, discount = 15)
        assertEquals(disc.id, data(disc).possibleHad(pend(), 9001, emptySet())?.id)
        // 待確認裡已經記過的那些
        val list = listOf(pend().copy(id = 1, state = PendState.DONE, txnId = 5), pend().copy(id = 2, state = PendState.HAD, txnId = 6), pend().copy(id = 3, state = PendState.NOT, txnId = 7))
        assertEquals(setOf(5L, 6L), claimedTxns(list))
    }

    @Test
    fun recordedTxn() {
        val d = data()
        val p = pend().copy(wallet = "OP錢包")
        val tx = d.pendTxn(p, 77, book, 9001, food)!!
        assertEquals(85L, tx.amount)
        assertEquals(9001L, tx.accountId)
        assertEquals("統一超商（OP錢包）", tx.note)
        assertEquals(listOf(CARD_NOTI_TAG), tx.tags)
        assertEquals(20406L, tx.day)
        assertEquals(551, tx.time)
        assertEquals("OP錢包", pendNote(p.copy(merchant = "")))
        // 外幣刷台幣卡：沒有匯率要問；給了台幣就記成外幣消費（待請款）
        val f = pend(1549, "NETFLIX.COM", cur = "USD")
        assertNull(d.pendTxn(f, 78, book, 9001, food))
        val fx = d.pendTxn(f, 78, book, 9001, food, twd = 500)!!
        assertEquals(500L, fx.amount)
        assertEquals(1549L, fx.fxSpendAmount)
        assertEquals("USD", fx.fxSpendCur)
        assertTrue(fx.fxPending)
    }
}
