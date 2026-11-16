package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** 更新後補一次：「收到的通知」裡還沒放進待確認的刷卡也放進去 */
class BackfillTest {
    private val base = Defaults.create()
    private val d = base.copy(
        accounts = base.accounts + Account(9003, "玉山 Unicard", "img:acc_card", AccountType.CARD, 0L, 1),
        notiApps = listOf(NotiApp("esun", "玉山", listOf(9003L)), NotiApp("samsung", "Samsung Wallet", listOf(9003L))),
    )
    private val now = 1_800_000_000_000L
    private fun noti(id: Long, pkg: String, title: String, text: String, ago: Long) = CapturedNoti(id, pkg, pkg, title, text, now - ago)

    @Test
    fun addsOnlyNewSpendsAndMergesPairs() {
        val captured = listOf(
            noti(1, "esun", "玉山銀行", "信用卡消費30元通知", 86_400_000L),
            noti(2, "samsung", "玉山Unicard", "LI XIN EMPORIUM CO., L \$30.00", 86_400_000L - 60_000L),
            noti(3, "samsung", "玉山Unicard", "您已獲得 2 點", 86_000_000L),
            noti(4, "esun", "玉山銀行", "信用卡消費120元通知", 3_600_000L),
        )
        // 4 已經在待確認裡（更新後才收到的）
        val existing = pendOf(captured[3], parseCardNoti(captured[3].title, captured[3].text, captured[3].time))!!
        val (out, n) = backfillPends(listOf(existing), captured, d, now)
        // 玉山＋Samsung Wallet 那筆合成一筆、點數不算、4 不重複
        assertEquals(1, n)
        assertEquals(2, out.size)
        val merged = out.first { it.amount == 30L }
        assertEquals("LI XIN EMPORIUM CO., L", merged.merchant)
        assertEquals(listOf(1L, 2L), merged.src.map { it.notiId })
        // 再補一次不會多
        assertEquals(0, backfillPends(out, captured, d, now).second)
    }
}
