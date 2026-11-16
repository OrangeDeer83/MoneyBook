package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 事後在修改頁改卡：要不要問「以後也記到這張嗎」、記住後以後的通知記到哪張 */
class RelearnTest {
    private val base = Defaults.create()
    private fun card(id: Long, name: String, vararg nos: String) =
        Account(id, name, "img:acc_card", AccountType.CARD, 0L, id.toInt(), cardNos = nos.map { CardNo(it) })
    private val cube = card(9001, "CUBE 卡", "1234")
    private val world = card(9002, "世界卡")
    private val uni = card(9003, "玉山 Unicard")
    private val d = base.copy(
        accounts = base.accounts + cube + world + uni,
        notiApps = listOf(
            NotiApp("cathay", "國泰世華", listOf(9001L)),
            NotiApp("samsung", "Samsung Wallet", listOf(9001L, 9003L)),
            NotiApp("line", "LINE Pay", listOf(9001L)),
        ),
    )
    private val cash = base.accounts.first().id
    private fun txn(acc: Long, s: NotiSrc?) =
        Txn(1, base.books.first().id, TxType.EXPENSE, 85, null, acc, null, 20400L, "統一超商", listOf(CARD_NOTI_TAG), noti = s)

    @Test
    fun askOnlyWhenRuleWouldNotPickNewCard() {
        val s = NotiSrc(1, "cathay", "國泰世華", "國泰世華", "1234")
        val t = txn(9001, s)
        assertEquals(s, d.relearnAsk(t, 9002L))
        // 沒換卡、換成現金、不是刷卡通知記的：不問
        assertNull(d.relearnAsk(t, 9001L))
        assertNull(d.relearnAsk(t, cash))
        assertNull(d.relearnAsk(txn(9001, null), 9002L))
        // 照規則本來就會記到新的這張：不用問
        val moved = d.relearnCard(s, 9002L, 20410L)
        assertNull(moved.relearnAsk(t, 9002L))
    }

    @Test
    fun last4MovesToNewCard() {
        val s = NotiSrc(1, "cathay", "國泰世華", "國泰世華", "1234")
        val out = d.relearnCard(s, 9002L, 20410L)
        assertTrue(out.accMap[9001L]!!.cardNos.isEmpty())
        assertEquals(listOf(CardNo("1234", learned = 20410L)), out.accMap[9002L]!!.cardNos)
        assertEquals(9002L, out.matchCard(s))
        assertEquals(" 1234 ", relearnWhat(s))
    }

    @Test
    fun titleAndAppDefault() {
        // Samsung Wallet 沒有末四碼、標題是卡片名稱：記住標題
        val st = NotiSrc(2, "samsung", "Samsung Wallet", "國泰CUBE", "")
        assertEquals(9002L, d.relearnCard(st, 9002L, 20410L).matchCard(st))
        assertEquals("「國泰CUBE」的通知", relearnWhat(st))
        // LINE Pay 連標題都沒有：當這個 App 的預設卡，比「App 只設了一張卡」優先
        val sl = NotiSrc(3, "line", "LINE Pay", "", "")
        assertEquals(9001L, d.matchCard(sl))
        assertEquals(9002L, d.relearnCard(sl, 9002L, 20410L).matchCard(sl))
        assertEquals(" LINE Pay 沒寫末四碼的通知", relearnWhat(sl))
        // App 已經不讀了：不問
        val gone = d.copy(notiApps = d.notiApps.filter { it.pkg != "line" })
        assertNull(gone.relearnAsk(txn(9001, sl), 9002L))
    }

    @Test
    fun recordedTxnKeepsSourceAndBackup() {
        val p = CardPend(
            5, listOf(NotiSrc(5, "esun", "玉山", "玉山銀行", ""), NotiSrc(6, "samsung", "Samsung Wallet", "玉山Unicard", "7788")),
            30, "", "LI XIN", false, "", 20400L, 600, 0L,
        )
        val t = d.pendTxn(p, 99, base.books.first().id, 9003L, null)!!
        // 有末四碼的那則
        assertEquals("7788", t.noti!!.last4)
        val back = Codec.decode(Codec.encode(d.copy(txns = listOf(t)))).txns.single()
        assertEquals(t.noti, back.noti)
        // 舊資料沒有這個欄位
        assertNull(Codec.decode(Codec.encode(d.copy(txns = listOf(t.copy(noti = null))))).txns.single().noti)
    }
}
