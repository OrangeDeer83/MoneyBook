package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 訂閱提醒的「可能已經記過」：同帳本、同帳戶、前後 3 天、金額一樣、沒有 #訂閱 */
class SubHadTest {
    private val base = Defaults.create()
    private val acc = base.accounts.first().id
    private val book = base.books.first().id
    private val cat = base.categories.first { it.kind == TxType.EXPENSE }.id
    private val d15 = LocalDate.of(2026, 11, 15).toEpochDay()
    private val gym = Sub(
        id = 9001, bookId = book, name = "健身房", amount = 1288, categoryId = cat, accountId = acc,
        days = listOf(15), start = LocalDate.of(2026, 1, 1).toEpochDay(), doneUntil = d15 - 1,
    )
    private var nid = 100L
    private fun t(day: Long, amount: Long = 1288, a: Long = acc, tags: List<String> = emptyList(), b: Long = book) =
        Txn(nid++, b, TxType.EXPENSE, amount, cat, a, null, day, "運動", tags)
    private fun data(vararg txns: Txn) = base.copy(txns = sortTxns(txns.toList()), subs = listOf(gym))

    @Test
    fun findsNearestOwnRecord() {
        val near = t(d15 - 1)
        val d = data(t(d15 - 3), near, t(d15 + 4))
        assertEquals(near.id, d.subHad(gym, d15)?.id)
    }

    @Test
    fun ignoresOthers() {
        // 金額不一樣、別的帳戶、太遠、已經有 #訂閱（訂閱記的、已經認走的）、別本帳本
        val other = base.accounts.getOrNull(1)?.id ?: 999L
        val d = data(t(d15, 1289), t(d15, a = other), t(d15 - 4), t(d15, tags = listOf(Sub.TAG)), t(d15, b = book + 77))
        assertNull(d.subHad(gym, d15))
        // 有優惠：比記的時候的總額
        val disc = t(d15).copy(amount = 1388, discount = 100)
        assertEquals(disc.id, data(disc).subHad(gym, d15)?.id)
    }
}
