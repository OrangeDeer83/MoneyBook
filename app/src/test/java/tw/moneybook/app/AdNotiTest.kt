package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 像廣告的通知：不放進待確認、標「像廣告，不記」，「其實是刷卡」可以放回待確認 */
class AdNotiTest {
    private val d = Defaults.create()
    private val adText = "冬天來吃築間集團燒肉/鍋物/銅盤/港點品牌，TWQR條碼付最高拿500元！"
    private fun noti(id: Long, text: String, title: String = "玉山Wallet") = CapturedNoti(id, "esun", "玉山Wallet", title, text, 1_000L)
    private fun read(text: String, title: String = "") = parseCardNoti(title, text, 1_000L)

    @Test
    fun adsAreRecognised() {
        val r = read(adText, "玉山Wallet")
        assertEquals(NotiKind.AD, r.kind)
        assertEquals(500L, r.amount)
        assertEquals(listOf("最高"), r.adWords)
        assertEquals(NotiKind.AD, read("週末限定 回饋最高300元 立即登錄").kind)
    }

    @Test
    fun realSpendsAreNotAds() {
        // 有末四碼
        assertEquals(NotiKind.SPEND, read("金額NT\$45元 卡號末四碼1234於2026/11/13 08:42在OP錢包統一超商刷卡，享回饋").kind)
        // 有刷卡這類字
        assertEquals(NotiKind.SPEND, read("您於全聯福利中心消費NT\$500，最高享5%回饋").kind)
        assertEquals(NotiKind.SPEND, read("交易提醒 店家 UNIQLO 已扣款 NTD 990").kind)
        // Samsung Wallet：只有店名和金額、沒有廣告字
        assertEquals(NotiKind.SPEND, read("LI XIN EMPORIUM CO., L \$30.00", "玉山Unicard").kind)
        // 店名裡有拿、送這種字的 Samsung Wallet 通知不算廣告
        assertEquals(NotiKind.SPEND, read("拿坡里披薩 \$350", "玉山Unicard").kind)
        assertEquals(NotiKind.SPEND, read("UBER EATS 外送 \$280", "玉山Unicard").kind)
        // 讀不到金額的照舊
        assertEquals(NotiKind.NO_AMOUNT, read("限時活動，立即登錄抽好禮").kind)
    }

    @Test
    fun adsStayOutOfPendingButCanBeForced() {
        val n = noti(7, adText)
        assertNull(pendOf(n, d.readNoti(n)))
        assertEquals(NotiTagKind.OFF to "像廣告，不記", d.notiTag(n, emptyList()))
        val out = forceSpendPend(emptyList(), n, d, 2_000L)
        assertNotNull(out)
        val p = out!!.single()
        assertEquals(PendKind.SPEND, p.kind)
        assertEquals(500L, p.amount)
        assertEquals(PendState.WAIT, p.state)
        assertEquals(7L, p.mainSrc.notiId)
        // 放進去之後標籤看待確認那筆；再按一次不會重複
        assertTrue(d.notiTag(n, out).second != "像廣告，不記")
        assertNull(forceSpendPend(out, n, d, 2_000L))
    }
}
