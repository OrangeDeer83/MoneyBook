package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 刷卡通知第一步：勾選的 App 與卡片存檔、收到的通知去重與保留天數、猜哪些是銀行支付 App */
class CardNotiTest {
    @Test
    fun notiAppsSurviveSaveAndOldDataHasNone() {
        val base = Defaults.create()
        val d = base.copy(notiApps = listOf(NotiApp("com.cathay", "國泰世華", listOf(5L, 6L), 5L), NotiApp("com.samsung.wallet", "Samsung Wallet")))
        val back = Codec.decode(Codec.encode(d))
        assertEquals(d.notiApps, back.notiApps)
        val root = org.json.JSONObject(Codec.encode(d))
        root.remove("notiApps")
        assertTrue(Codec.decode(root.toString()).notiApps.isEmpty())
    }

    @Test
    fun capturedDedupAndKeepSevenDays() {
        val day = 86_400_000L
        val now = 100 * day
        val a = CapturedNoti(1, "p", "國泰", "國泰世華銀行", "【刷卡通知】金額NT\$2元", now - 60_000L)
        var list = addCaptured(emptyList(), a, now)
        // 系統重送同一則（5 分鐘內、一樣的內容）不重複
        list = addCaptured(list, a.copy(id = 2, time = now), now)
        assertEquals(1, list.size)
        // 不同內容、或隔了很久的一樣內容都算新的，新的在前
        list = addCaptured(list, a.copy(id = 3, text = "【刷卡通知】金額NT\$85元", time = now), now)
        assertEquals(listOf(3L, 1L), list.map { it.id })
        // 超過 7 天的丟掉
        val old = a.copy(id = 4, text = "很久以前", time = now - 8 * day)
        assertEquals(listOf(3L, 1L), pruneCaptured(list + old, now).map { it.id })
        val back = CapturedCodec.decode(CapturedCodec.encode(list))
        assertEquals(list, back)
        assertTrue(CapturedCodec.decode("壞掉的檔案").isEmpty())
    }

    @Test
    fun payAppsAreGuessedByName() {
        listOf("國泰世華 CUBE", "玉山銀行", "e.Fingo", "LINE Pay", "Samsung Wallet", "大戶 DAWHO", "街口支付").forEach { assertTrue(it, looksLikePayApp(it)) }
        listOf("LINE", "Gmail", "YouTube", "記帳本").forEach { assertFalse(it, looksLikePayApp(it)) }
    }
}
