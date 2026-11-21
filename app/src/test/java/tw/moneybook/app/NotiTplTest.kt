package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 自己教的刷卡通知範本：照標的位置讀、沒標的數字當任何數字、對不上用通用規則、重讀待確認 */
class NotiTplTest {
    private val zone = ZoneId.of("Asia/Taipei")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = LocalDate.of(y, m, d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val cathay = "【刷卡通知】金額NT$2元 卡號末四碼1234於2026/11/13 09:11在ＯＰ\n錢包統一超商刷卡，點擊查看消費明細"

    /** 照通用規則讀到的位置標好 */
    private fun tplOf(text: String, post: Long, pkg: String = "cathay"): NotiTpl {
        val r = parseCardNoti("", text, post, zone)
        return NotiTpl(1, pkg, normalizeNoti(text), r.spans.map { (f, s) -> TplMark(f, s.first, s.last) }.sortedBy { it.start }, 20405L)
    }

    @Test
    fun readsSameFormatWithOtherValues() {
        val t = tplOf(cathay, ms(2026, 11, 13))
        assertEquals(setOf(TplField.AMOUNT, TplField.MERCHANT, TplField.LAST4, TplField.TIME), t.marks.map { it.field }.toSet())
        val r = t.read("", "【刷卡通知】金額NT$1,280元 卡號末四碼5678於2026/11/14 20:05在全聯福利中心刷卡，點擊查看消費明細", ms(2026, 11, 14, 21), zone)!!
        assertEquals(1280L, r.amount)
        assertEquals("", r.cur)
        assertEquals("全聯福利中心", r.merchant)
        assertEquals("5678", r.last4)
        assertEquals(LocalDate.of(2026, 11, 14).toEpochDay(), r.day)
        assertEquals(20 * 60 + 5, r.minute)
        assertTrue(r.merchantSure)
        // 錢包照樣拆出來
        val op = t.read("", cathay, ms(2026, 11, 13), zone)!!
        assertEquals("統一超商", op.merchant)
        assertEquals("OP錢包", op.wallet)
        // 格式不一樣（帳單通知）：對不上
        assertNull(t.read("", "您本月帳單已出，應繳金額NT$12,000元", ms(2026, 11, 14), zone))
    }

    @Test
    fun handMarkedMerchantAndForeignAmount() {
        // 通用規則讀不到商店的格式：自己標「店家」後面那段
        val text = "交易提醒 卡片1234 店家 COSTCO TAICHUNG 已扣款 NTD 2,350"
        val s = normalizeNoti(text)
        var marks = parseCardNoti("", text, ms(2026, 11, 13), zone).spans.map { (f, r) -> TplMark(f, r.first, r.last) }
        val a = s.indexOf("COSTCO")
        marks = setMark(marks, TplField.MERCHANT, a + "COSTCO TAICHUNG".length - 1, a)
        val t = NotiTpl(2, "x", s, marks, 0L)
        val r = t.read("", "交易提醒 卡片1234 店家 UNIQLO 台中店 已扣款 NTD 990", ms(2026, 11, 15), zone)!!
        // 英文和中文中間的空白會接起來（normalizeNoti）
        assertEquals("UNIQLO台中店", r.merchant)
        assertEquals(990L, r.amount)
        // 外幣：金額前面是 US$ → 美金，最小單位
        val sw = "LI XIN EMPORIUM CO., L US$30.00"
        val ss = normalizeNoti(sw)
        val st = NotiTpl(
            3, "samsung", ss,
            listOf(TplMark(TplField.MERCHANT, 0, ss.indexOf(" US$") - 1), TplMark(TplField.AMOUNT, ss.indexOf("30"), ss.length - 1)), 0L,
        )
        val rr = st.read("玉山Unicard", "AMAZON MKTPL US$15.49", ms(2026, 11, 15), zone)!!
        assertEquals("USD", rr.cur)
        assertEquals(1549L, rr.amount)
        assertEquals("AMAZON MKTPL", rr.merchant)
    }

    @Test
    fun marksEditing() {
        val m = setMark(listOf(TplMark(TplField.AMOUNT, 3, 5), TplMark(TplField.MERCHANT, 10, 14)), TplField.MERCHANT, 4, 8)
        // 新的商店蓋到金額那段：金額拿掉，商店只剩新的
        assertEquals(listOf(TplMark(TplField.MERCHANT, 4, 8)), m)
        assertEquals(TplField.AMOUNT, tplNeed(m))
        assertEquals(TplField.MERCHANT, tplNeed(listOf(TplMark(TplField.AMOUNT, 0, 1))))
        assertNull(tplNeed(listOf(TplMark(TplField.AMOUNT, 0, 1), TplMark(TplField.MERCHANT, 3, 4))))
        assertEquals(TplField.MERCHANT, markAt(m, 6)?.field)
        assertNull(markAt(m, 9))
    }

    @Test
    fun appTemplatesFirstThenGeneralAndBackup() {
        val base = Defaults.create()
        val t = tplOf(cathay, ms(2026, 11, 13))
        val d = base.copy(notiApps = listOf(NotiApp("cathay", "國泰世華")), notiTpls = listOf(t))
        val n = CapturedNoti(9, "cathay", "國泰世華", "", "【刷卡通知】金額NT$350元 卡號末四碼1234於2026/11/14 12:31在全聯刷卡，點擊查看消費明細", ms(2026, 11, 14))
        assertNotNull(d.tplFor(n))
        assertEquals("全聯", d.readNoti(n).merchant)
        // 別的 App 的通知不用這個範本
        assertNull(d.tplFor(n.copy(pkg = "other")))
        assertEquals("國泰世華・刷卡", d.tplName(t))
        // 跟著備份；舊資料沒有這個欄位
        val back = Codec.decode(Codec.encode(d))
        assertEquals(d.notiTpls, back.notiTpls)
        assertTrue(Codec.decode(Codec.encode(base)).notiTpls.isEmpty())
    }

    @Test
    fun retplUpdatesWaitingAndAddsMissing() {
        val base = Defaults.create()
        val d0 = base.copy(notiApps = listOf(NotiApp("x", "X 銀行")))
        val text1 = "交易提醒 卡片1234 店家 COSTCO 已扣款 NTD 2,350"
        val text2 = "交易提醒 卡片1234 店家 UNIQLO 已扣款 NTD 990"
        val now = ms(2026, 11, 15)
        val c1 = CapturedNoti(1, "x", "X 銀行", "", text1, now - 3_600_000L)
        val c2 = CapturedNoti(2, "x", "X 銀行", "", text2, now - 1_800_000L)
        // c1 已經在待確認、商店讀錯；c2 還沒放進去
        val p1 = pendOf(c1, parseCardNoti("", text1, c1.time, zone))!!.copy(merchant = "", merchantSure = false)
        val s = normalizeNoti(text1)
        var marks = parseCardNoti("", text1, c1.time, zone).spans.map { (f, r) -> TplMark(f, r.first, r.last) }
        marks = setMark(marks, TplField.MERCHANT, s.indexOf("COSTCO"), s.indexOf("COSTCO") + 5)
        val t = NotiTpl(5, "x", s, marks, 0L)
        val d = d0.copy(notiTpls = listOf(t))
        val (out, n) = retplPends(listOf(p1), listOf(c1, c2), d, t, now)
        assertEquals(2, n)
        assertEquals("COSTCO", out.first { it.id == 1L }.merchant)
        assertEquals("UNIQLO", out.first { it.id == 2L }.merchant)
        // 再重讀一次沒有變
        assertEquals(0, retplPends(out, listOf(c1, c2), d, t, now).second)
    }

    @Test
    fun moreDetailedTemplateGoesFirst() {
        val now = ms(2026, 11, 15)
        val text = "交易提醒 店家 COSTCO 已扣款 NTD 2,350"
        val s = normalizeNoti(text)   // 交易提醒店家COSTCO已扣款NTD 2,350
        val amt = TplMark(TplField.AMOUNT, s.indexOf("2,350"), s.lastIndex)
        // 仔細：「交易提醒店家【商店】已扣款NTD【金額】」
        val fine = NotiTpl(1, "x", s, listOf(TplMark(TplField.MERCHANT, s.indexOf("COSTCO"), s.indexOf("COSTCO") + 5), amt), 0L)
        // 寬鬆（後來才存）：「交易提醒【商店】已扣款NTD【金額】」，商店會讀成「店家COSTCO」
        val loose = NotiTpl(2, "x", s, listOf(TplMark(TplField.MERCHANT, s.indexOf("店家"), s.indexOf("COSTCO") + 5), amt), 0L)
        assertTrue(fine.fixedLen > loose.fixedLen)
        val d = Defaults.create().copy(notiApps = listOf(NotiApp("x", "X 銀行")), notiTpls = listOf(fine, loose))
        assertEquals(listOf(1L, 2L), d.tplsOf("x").map { it.id })
        val c = CapturedNoti(9, "x", "X 銀行", "", "交易提醒 店家 UNIQLO 已扣款 NTD 990", now)
        assertEquals(1L, d.tplFor(c)?.id)
        assertEquals("UNIQLO", d.readNoti(c).merchant)
        // 一樣仔細：新存的先；還沒存的（id 0）當最新
        val same = loose.copy(id = 3)
        assertEquals(listOf(3L, 2L), orderTpls(listOf(loose, same)).map { it.id })
        assertEquals(listOf(0L, 3L), orderTpls(listOf(same, same.copy(id = 0))).map { it.id })
        // 存寬鬆的那個後重讀：比較仔細的會先讀的不動
        val p = pendOf(c, d.readNoti(c))!!
        assertEquals(0, retplPends(listOf(p), listOf(c), d, loose, now).second)
    }
}
