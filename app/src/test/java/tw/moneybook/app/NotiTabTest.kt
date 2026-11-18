package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** 收到的通知：分頁（刷卡紀錄／廣告／其他）、同一筆的好幾則併成一組 */
class NotiTabTest {
    private val d = Defaults.create()
    private fun noti(id: Long, text: String, pkg: String = "x", time: Long = id * 1_000L) = CapturedNoti(id, pkg, "X 銀行", "", text, time)
    private fun pend(id: Long, vararg ids: Long, state: Int = PendState.WAIT) =
        CardPend(id, ids.map { NotiSrc(it, "x", "X 銀行", "", "") }, 30, "", "全聯", true, "", 0L, -1, 1_000L, state = state)

    @Test
    fun tabs() {
        val pends = listOf(pend(1, 1), pend(4, 4, state = PendState.NOT))
        assertEquals(NotiTab.CARD, d.notiTab(noti(1, "隨便"), pends))
        // 按了「不是消費」的留在刷卡紀錄
        assertEquals(NotiTab.CARD, d.notiTab(noti(4, "您已獲得 2 點"), pends))
        // 沒放進待確認的看讀出來的結果
        assertEquals(NotiTab.CARD, d.notiTab(noti(5, "在全聯消費NT\$350"), pends))
        assertEquals(NotiTab.CARD, d.notiTab(noti(5, "在全聯福利中心之消費退款NT\$350"), pends))
        assertEquals(NotiTab.AD, d.notiTab(noti(5, "週末限定 回饋最高300元 立即登錄"), pends))
        assertEquals(NotiTab.OTHER, d.notiTab(noti(5, "您已獲得 2 點"), pends))
        assertEquals(NotiTab.OTHER, d.notiTab(noti(5, "交易取消 NT\$85"), pends))
        assertEquals(NotiTab.OTHER, d.notiTab(noti(5, "您的分期付款第3期NT\$1,000"), pends))
    }

    @Test
    fun sameSpendGrouped() {
        // 新的在前：Samsung Wallet(3)、點數(2)、玉山(1)；1 和 3 是同一筆
        val list = listOf(noti(3, "LI XIN \$30.00", "samsung"), noti(2, "您已獲得 2 點"), noti(1, "信用卡消費30元通知", "esun"))
        val pends = listOf(pend(1, 1, 3))
        val g = groupNotis(list, pends)
        assertEquals(2, g.size)
        // 併成一組、放在最新那則的位置，組裡照收到的順序
        assertEquals(listOf(1L, 3L), g[0].notis.map { it.id })
        assertEquals(1L, g[0].pend?.id)
        assertEquals(listOf(2L), g[1].notis.map { it.id })
        // 同一筆的另一則已經過了 7 天被刪掉：單獨一則
        val g2 = groupNotis(listOf(noti(3, "LI XIN \$30.00", "samsung")), pends)
        assertEquals(listOf(listOf(3L)), g2.map { x -> x.notis.map { it.id } })
    }
}
