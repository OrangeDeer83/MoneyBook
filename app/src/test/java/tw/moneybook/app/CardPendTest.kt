package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 刷卡通知第二步：對卡片的規則、同一筆兩則通知合併、待確認存檔 */
class CardPendTest {
    private val base = Defaults.create()
    private fun card(id: Long, name: String, vararg nos: String, hidden: Boolean = false) =
        Account(id, name, "img:acc_card", AccountType.CARD, 0L, id.toInt(), hidden = hidden, cardNos = nos.map { CardNo(it) })
    private val cube = card(9001, "CUBE 卡", "1234", "5566")
    private val world = card(9002, "世界卡")
    private val uni = card(9003, "玉山 Unicard", "7788")
    private val pi = card(9004, "Pi 卡", "1234")
    private fun data(vararg apps: NotiApp) = base.copy(accounts = base.accounts + cube + world + uni + pi, notiApps = apps.toList())
    private fun src(pkg: String, last4: String = "", title: String = "", id: Long = 1L) = NotiSrc(id, pkg, pkg, title, last4)

    @Test
    fun matchByLast4() {
        val d = data(NotiApp("cathay", "國泰", listOf(9001L, 9002L)))
        assertEquals(9001L, d.matchCard(src("cathay", "5566")))
        // 1234 在 CUBE 卡和 Pi 卡都有：國泰 App 設定的卡優先
        assertEquals(9001L, d.matchCard(src("cathay", "1234")))
        // 沒設定這個 App 的卡：所有信用卡裡剛好一張
        assertEquals(9003L, d.matchCard(src("other", "7788")))
        // 兩張都有、App 分不出來：問
        assertNull(d.matchCard(src("other", "1234")))
        // 末四碼沒存在任何一張卡、App 有兩張卡：問
        assertNull(d.matchCard(src("cathay", "9012")))
    }

    @Test
    fun matchByTitleSingleCardAndDefault() {
        val d = data(
            NotiApp("samsung", "Samsung Wallet", listOf(9001L, 9003L), titles = mapOf(titleKey("國泰CUBE") to 9001L)),
            NotiApp("esun", "玉山", listOf(9003L)),
            NotiApp("line", "LINE Pay", listOf(9001L, 9002L), defaultCard = 9002L),
        )
        // 標題和卡名一樣（去掉空白比）
        assertEquals(9003L, d.matchCard(src("samsung", title = "玉山Unicard")))
        // 記住的標題
        assertEquals(9001L, d.matchCard(src("samsung", title = "國泰 CUBE")))
        // App 只有一張卡：就是它（有沒有末四碼都一樣）
        assertEquals(9003L, d.matchCard(src("esun", title = "玉山銀行")))
        assertEquals(9003L, d.matchCard(src("esun", "9999")))
        // 沒寫末四碼：用預設卡；寫了對不到的末四碼：問
        assertEquals(9002L, d.matchCard(src("line")))
        assertNull(d.matchCard(src("line", "9999")))
        // 標題對不到、也沒有預設卡：問
        assertNull(d.matchCard(src("samsung", title = "台新@GoGo")))
        // 隱藏的卡不算（Samsung Wallet 剩下 CUBE 卡一張）
        val h = d.copy(accounts = d.accounts.map { if (it.id == 9003L) it.copy(hidden = true) else it })
        assertEquals(9001L, h.matchCard(src("samsung", title = "玉山Unicard")))
    }

    private fun pend(id: Long, pkg: String, amount: Long, time: Long, merchant: String = "", sure: Boolean = false, last4: String = "", title: String = "", minute: Int = 600) =
        CardPend(id, listOf(src(pkg, last4, title, id)), amount, "", merchant, sure, "", 20406L, minute, time)

    @Test
    fun twoNotisOfOnePurchaseMerge() {
        val d = data(NotiApp("samsung", "Samsung Wallet", listOf(9003L)), NotiApp("esun", "玉山", listOf(9003L)))
        val now = 1_000_000_000L
        // 玉山先到（沒有商店），Samsung Wallet 3 分鐘後到（有商店）
        var list = addPend(emptyList(), pend(1, "esun", 30, now), d, now)
        list = addPend(list, pend(2, "samsung", 30, now + 3 * 60_000L, "LI XIN EMPORIUM CO., L", title = "玉山Unicard"), d, now)
        assertEquals(1, list.size)
        assertEquals("LI XIN EMPORIUM CO., L", list[0].merchant)
        assertEquals(listOf(1L, 2L), list[0].src.map { it.notiId })
        assertEquals(9003L, d.resolveCard(list[0]))
        // 金額不一樣、超過 10 分鐘、同一個 App：都不合併
        list = addPend(list, pend(3, "samsung", 31, now + 4 * 60_000L), d, now)
        list = addPend(list, pend(4, "esun", 30, now + 20 * 60_000L), d, now)
        list = addPend(list, pend(5, "esun", 30, now + 21 * 60_000L), d, now)
        assertEquals(4, list.size)
        // 不同卡的同金額不合併
        val d2 = data(NotiApp("cathay", "國泰", listOf(9001L)), NotiApp("esun", "玉山", listOf(9003L)))
        val l2 = addPend(addPend(emptyList(), pend(1, "cathay", 85, now), d2, now), pend(2, "esun", 85, now), d2, now)
        assertEquals(2, l2.size)
    }

    @Test
    fun mergedIntoHandledStaysHandledAndOldOnesDrop() {
        val d = data(NotiApp("samsung", "Samsung Wallet", listOf(9003L)), NotiApp("esun", "玉山", listOf(9003L)))
        val now = 1_000_000_000_000L
        val done = pend(1, "esun", 30, now).copy(state = PendState.DONE, txnId = 77)
        val list = addPend(listOf(done), pend(2, "samsung", 30, now + 60_000L, "全聯", sure = true), d, now)
        assertEquals(1, list.size)
        assertEquals(PendState.DONE, list[0].state)
        assertEquals("全聯", list[0].merchant)
        // 14 天前的丟掉
        val old = pend(9, "esun", 50, now - 15 * 86_400_000L)
        assertTrue(prunePend(list + old, now).none { it.id == 9L })
    }

    @Test
    fun codecAndLearnedTitles() {
        val p = pend(1, "esun", 30, 123L, "全聯", true, "7788", "玉山Unicard").copy(cardId = 9003L, state = PendState.HAD, txnId = 5, wallet = "LINE Pay", cur = "USD")
        assertEquals(listOf(p), CardPendCodec.decode(CardPendCodec.encode(listOf(p))))
        assertTrue(CardPendCodec.decode("壞掉的檔案").isEmpty())
        val d = data(NotiApp("samsung", "Samsung Wallet", listOf(9003L), titles = mapOf("玉山unicard" to 9003L)))
        assertEquals(d.notiApps, Codec.decode(Codec.encode(d)).notiApps)
        // 舊資料沒有 titles
        assertTrue(NotiAppCodec.fromJson(org.json.JSONArray("""[{"pkg":"a","label":"A","cardIds":[],"defaultCard":null}]""")).single().titles.isEmpty())
    }

    @Test
    fun parsedNotiBecomesPend() {
        val n = CapturedNoti(42, "cathay", "國泰世華", "國泰世華銀行", "金額NT\$85元 卡號末四碼5566於2026/11/13 09:11在全家刷卡", 1_000L)
        val r = parseCardNoti(n.title, n.text, n.time)
        val p = pendOf(n, r)!!
        assertEquals(85L, p.amount)
        assertEquals("全家", p.merchant)
        assertEquals("5566", p.last4)
        assertEquals(9001L, data(NotiApp("cathay", "國泰", listOf(9001L, 9002L))).resolveCard(p))
        assertNull(pendOf(n, parseCardNoti("", "您已獲得 2 點", 1_000L)))
    }
}
