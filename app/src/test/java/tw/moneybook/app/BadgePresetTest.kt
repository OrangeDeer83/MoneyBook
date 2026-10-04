package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 帳戶徽章：銀行／行動支付預設清單、雙色漸層 */
class BadgePresetTest {
    private fun acc(from: Long = 0L, to: Long = 0L) =
        Account(1, "帳戶", AccountType.BANK.emoji, AccountType.BANK, 0, 0, badge = "台新", badgeColor = 3, badgeFrom = from, badgeTo = to)

    @Test
    fun presetsCoverCommonBanksAndMobilePayments() {
        val names = BadgePresets.all.map { it.name }
        listOf("國泰世華", "中國信託", "玉山", "台新", "富邦", "永豐", "郵局").forEach { assertTrue(it, it in names) }
        listOf("LINE Pay", "街口支付", "悠遊付", "一卡通", "Apple Pay", "Google Pay").forEach { assertTrue(it, it in names) }
        assertEquals("名稱不能重複", names.size, names.toSet().size)
    }

    @Test
    fun eachPresetHasShortTextAndTwoOpaqueColors() {
        BadgePresets.all.forEach { p ->
            assertTrue("${p.name} 簡稱 1~4 字", p.badge.length in 1..4)
            assertEquals("${p.name} 起點色要不透明", 0xFFL, (p.from shr 24) and 0xFF)
            assertEquals("${p.name} 終點色要不透明", 0xFFL, (p.to shr 24) and 0xFF)
            assertNotEquals("${p.name} 兩個顏色要不同", p.from, p.to)
        }
        assertTrue(BadgePresets.banks.isNotEmpty() && BadgePresets.pays.isNotEmpty())
        assertEquals(BadgePresets.banks.size + BadgePresets.pays.size, BadgePresets.all.size)
    }

    @Test
    fun taishinIsGoldNotRed() {
        // 台新的識別色是金黃色：紅色分量高、綠色分量要夠高、藍色很低
        val p = BadgePresets.all.first { it.name == "台新" }
        val g = ((p.from shr 8) and 0xFF).toInt()
        val b = (p.from and 0xFF).toInt()
        assertTrue("綠色分量夠高才是金黃（不是紅）", g > 140)
        assertTrue(b < 80)
    }

    @Test
    fun autoGradientMakesALighterAndADarkerShade() {
        val (from, to) = BadgePresets.autoGradient(0xFF2E8B57L)
        fun lum(c: Long) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        assertTrue(lum(from) > lum(0xFF2E8B57L))
        assertTrue(lum(to) < lum(0xFF2E8B57L))
        assertEquals(0xFFL, (from shr 24) and 0xFF)
        assertEquals(0xFFL, (to shr 24) and 0xFF)
    }

    @Test
    fun gradientFieldsSurviveSaveAndOldBackupsStillLoad() {
        val d = Defaults.create().copy(accounts = listOf(acc(0xFFF5BC2BL, 0xFFD88A0CL)))
        val back = Codec.decode(Codec.encode(d)).accounts.first()
        assertEquals(0xFFF5BC2BL, back.badgeFrom)
        assertEquals(0xFFD88A0CL, back.badgeTo)
        assertEquals("台新", back.badge)

        // 舊版備份沒有這兩個欄位：讀進來是 0（沿用單色），其他資料不受影響
        val old = Codec.encode(Defaults.create().copy(accounts = listOf(acc()))).replace(Regex(",\"badge(From|To)\":\\d+|\"badge(From|To)\":\\d+,"), "")
        assertTrue(!old.contains("badgeFrom"))
        val oldBack = Codec.decode(old).accounts.first()
        assertEquals(0L, oldBack.badgeFrom)
        assertEquals(0L, oldBack.badgeTo)
        assertEquals(3, oldBack.badgeColor)
        assertNotNull(oldBack.name)
    }
}
