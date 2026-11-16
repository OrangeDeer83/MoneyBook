package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 刷卡通知對到訂閱：同一張卡、金額一樣、扣款日前後 3 天；外幣用匯率差 10% 以內要問 */
class CardSubMatchTest {
    private val base = Defaults.create()
    private val cube = Account(9001, "CUBE 卡", "img:acc_card", AccountType.CARD, 0L, 1)
    private val cat = base.categories.first { it.kind == TxType.EXPENSE }.id
    private val book = base.books.first().id
    private val d15 = LocalDate.of(2026, 11, 15).toEpochDay()
    private fun sub(id: Long, amount: Long, day: Int = 15, cur: String = "", acc: Long = 9001, doneUntil: Long = d15 - 1) = Sub(
        id = id, bookId = book, name = "影音平台$id", amount = amount, cur = cur, categoryId = cat, accountId = acc,
        days = listOf(day), start = LocalDate.of(2026, 1, 1).toEpochDay(), doneUntil = doneUntil, tags = listOf("娛樂"),
    )
    private fun data(vararg subs: Sub, rates: List<FxRate> = emptyList()) =
        base.copy(accounts = base.accounts + cube, subs = subs.toList(), rates = rates)
    private fun pend(amount: Long, day: Long = d15, cur: String = "", minute: Int = 192) =
        CardPend(1, listOf(NotiSrc(1, "cathay", "國泰", "", "")), amount, cur, "NETFLIX", true, "", day, minute, 0L)

    @Test
    fun sameCardSameAmountWithinThreeDays() {
        val d = data(sub(1, 390), sub(2, 149))
        val m = d.pendSub(pend(390, d15 + 2), 9001)!!
        assertEquals(1L, m.sub.id)
        assertEquals(d15, m.day)
        assertTrue(m.exact)
        assertFalse(m.done)
        // 早 3 天也算（通知比提醒早到）
        assertEquals(d15, d.pendSub(pend(390, d15 - 3), 9001)?.day)
        // 晚 4 天、金額不一樣、別張卡、不知道卡：不算
        assertNull(d.pendSub(pend(390, d15 + 4), 9001))
        assertNull(d.pendSub(pend(391), 9001))
        assertNull(d.pendSub(pend(390), 9002))
        assertNull(d.pendSub(pend(390), null))
        // 按過「不是這個訂閱」
        assertNull(d.pendSub(pend(390).copy(notSub = true), 9001))
        // 暫停的不算
        assertNull(data(sub(1, 390).copy(paused = true)).pendSub(pend(390), 9001))
    }

    @Test
    fun alreadyHandledOccurrenceIsDone() {
        val d = data(sub(1, 390, doneUntil = d15))
        val m = d.pendSub(pend(390), 9001)!!
        assertTrue(m.done)
        val all = listOf(pend(390))
        assertEquals(1L, d.copy(notiApps = listOf(NotiApp("cathay", "國泰", listOf(9001L)))).pendSubDone(all[0])?.sub?.id)
        // 還沒處理的：訂閱提醒不再列這一次
        val d2 = data(sub(1, 390)).copy(notiApps = listOf(NotiApp("cathay", "國泰", listOf(9001L))))
        assertEquals(setOf(1L to d15), d2.subsShownInPend(all))
        assertTrue(d2.subsShownInPend(all.map { it.copy(state = PendState.NOT) }).isEmpty())
    }

    @Test
    fun foreignSubscription() {
        val usd = listOf(FxRate("USD", 32.0, d15))
        val d = data(sub(1, 1099, cur = "USD"), rates = usd)
        // 通知也寫美金：一樣就對到
        assertTrue(d.pendSub(pend(1099, cur = "USD"), 9001)!!.exact)
        // 通知寫台幣：約 $352，差 10% 以內要問
        val near = d.pendSub(pend(356), 9001)!!
        assertFalse(near.exact)
        assertNull(d.pendSub(pend(400), 9001))
        // 台幣訂閱不會對到外幣通知
        assertNull(data(sub(1, 390)).pendSub(pend(390, cur = "USD"), 9001))
        // 記成已請款的外幣消費：台幣照通知
        val t = d.pendSubTxn(pend(356), near, 50)!!
        assertEquals(356L, t.amount)
        assertEquals(1099L, t.fxSpendAmount)
        assertFalse(t.fxPending)
        assertTrue(Sub.TAG in t.tags && CARD_NOTI_TAG in t.tags && "娛樂" in t.tags)
        assertEquals(192, t.time)
    }

    @Test
    fun recordedAsSubscription() {
        val d = data(sub(1, 390))
        val m = d.pendSub(pend(390, d15 + 1), 9001)!!
        val t = d.pendSubTxn(pend(390, d15 + 1), m, 50)!!
        assertEquals(390L, t.amount)
        assertEquals(d15 + 1, t.day)
        assertEquals(cat, t.categoryId)
        assertEquals(listOf("娛樂", Sub.TAG, CARD_NOTI_TAG), t.tags)
        // 存檔讀檔：不是這個訂閱
        val p = pend(390).copy(notSub = true)
        assertTrue(CardPendCodec.decode(CardPendCodec.encode(listOf(p))).single().notSub)
    }
}
