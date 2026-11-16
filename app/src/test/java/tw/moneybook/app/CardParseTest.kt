package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 刷卡通知第二步：把通知讀成金額、商店、末四碼、時間（用使用者給的真實樣本＋各種寫法） */
class CardParseTest {
    private val tz = ZoneId.of("Asia/Taipei")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, mi: Int = 0) = LocalDate.of(y, m, d).atTime(h, mi).atZone(tz).toInstant().toEpochMilli()
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun read(title: String, text: String, post: Long = at(2026, 11, 14)) = parseCardNoti(title, text, post, tz)

    @Test
    fun cathayThreeLines() {
        // 國泰：三行、全形ＯＰ和「錢包」中間斷行
        val r = read("國泰世華銀行", "【刷卡通知】\n金額NT\$2元 卡號末四碼1234於2026/11/13 09:11在ＯＰ\n錢包統一超商刷卡・點擊查看消費明細")
        assertEquals(NotiKind.SPEND, r.kind)
        assertEquals(2L, r.amount)
        assertEquals("", r.cur)
        assertEquals("統一超商", r.merchant)
        assertEquals("OP錢包", r.wallet)
        assertEquals("1234", r.last4)
        assertEquals(day(2026, 11, 13), r.day)
        assertEquals(9 * 60 + 11, r.minute)
        assertTrue(r.merchantSure)
        // 商店中間被空白隔開（「OP錢 包」）也一樣
        assertEquals("統一超商", read("國泰世華銀行", "金額NT\$85元 卡號末四碼1234於2026/11/13 09:11在OP錢 包統一超商刷卡").merchant)
    }

    @Test
    fun samsungWalletAndEsun() {
        // Samsung Wallet：標題是卡片名稱、單獨的 $ 是台幣、商店被截斷
        val s = read("玉山Unicard", "LI XIN EMPORIUM CO., L \$30.00", at(2026, 11, 14, 15, 38))
        assertEquals(NotiKind.SPEND, s.kind)
        assertEquals(30L, s.amount)
        assertEquals("", s.cur)
        assertEquals("LI XIN EMPORIUM CO., L", s.merchant)
        assertEquals("", s.last4)
        assertEquals(day(2026, 11, 14), s.day)
        assertEquals(15 * 60 + 38, s.minute)
        // 玉山 e.Fingo：只有金額，沒有商店
        val e = read("玉山銀行", "信用卡消費30元通知", at(2026, 11, 14, 15, 38))
        assertEquals(NotiKind.SPEND, e.kind)
        assertEquals(30L, e.amount)
        assertEquals("", e.merchant)
        // Samsung Wallet 的點數通知：沒有金額
        assertEquals(NotiKind.NO_AMOUNT, read("玉山Unicard", "您已獲得 2 點").kind)
    }

    @Test
    fun commonWordings() {
        val a = read("X 銀行", "您的信用卡末四碼5678於11/14 18:40在7-ELEVEN消費NT\$65")
        assertEquals(65L, a.amount)
        assertEquals("7-ELEVEN", a.merchant)
        assertEquals("5678", a.last4)
        assertEquals(day(2026, 11, 14), a.day)
        assertEquals(18 * 60 + 40, a.minute)
        // 千分位、遮起來的卡號
        val b = read("Y 銀行", "卡號 ****-****-****-4321 消費金額 NT\$1,288 元【全聯福利中心】")
        assertEquals(1288L, b.amount)
        assertEquals("4321", b.last4)
        assertEquals("全聯福利中心", b.merchant)
        // 黏在英文代碼裡的數字不是金額
        val c = read("Y 銀行", "在SPOTIFY P1F2消費\$356")
        assertEquals(356L, c.amount)
        assertEquals("SPOTIFY P1F2", c.merchant)
        // 只有一個沒有幣別、沒有「元」的數字：不算金額
        assertEquals(NotiKind.NO_AMOUNT, read("Y 銀行", "您有 1 則新訊息").kind)
    }

    @Test
    fun foreignCurrency() {
        val r = read("Z 銀行", "末四碼9012 於11/13 在NETFLIX.COM 消費 US\$15.49")
        assertEquals(1549L, r.amount)
        assertEquals("USD", r.cur)
        assertEquals("NETFLIX.COM", r.merchant)
        assertEquals("9012", r.last4)
        assertEquals(day(2026, 11, 13), r.day)
        // 不是收到的那天、又沒寫時間：不知道幾點
        assertEquals(-1, r.minute)
        val j = read("Z 銀行", "消費 JPY 1,500 在 LAWSON")
        assertEquals(1500L, j.amount)
        assertEquals("JPY", j.cur)
    }

    @Test
    fun datesWithoutYearOrInRocYear() {
        // 1 月收到 12/31 的通知：是去年的
        assertEquals(day(2026, 12, 31), read("X", "12/31 23:50 刷卡 NT\$100", at(2027, 1, 1, 0, 5)).day)
        // 民國年
        assertEquals(day(2026, 11, 15), read("X", "115/11/15 消費NT\$100", at(2026, 11, 15)).day)
        // 寫的日期離收到的時間太遠（例如有效期限）：用收到的那天
        assertEquals(day(2026, 11, 14), read("X", "消費NT\$100 有效期限 2030/12/31", at(2026, 11, 14)).day)
    }

    @Test
    fun refundsAndCancelsAreLeftForLater() {
        assertEquals(NotiKind.OTHER, read("X", "您的信用卡退款NT\$300").kind)
        assertEquals(NotiKind.OTHER, read("X", "預先授權交易 NT\$1,000").kind)
        assertEquals(NotiKind.OTHER, read("X", "交易取消 NT\$85").kind)
    }

    @Test
    fun normalizeJoinsChineseLines() {
        assertEquals("OP錢包", normalizeNoti("ＯＰ\n錢包"))
        assertEquals("LI XIN EMPORIUM", normalizeNoti("LI  XIN\nEMPORIUM"))
        assertEquals("金額NT$2元卡號", normalizeNoti("金額ＮＴ＄２元 卡號"))
    }
}
