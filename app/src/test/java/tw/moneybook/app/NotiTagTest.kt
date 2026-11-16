package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Test

/** 「收到的通知」旁邊的標籤 */
class NotiTagTest {
    private val d = Defaults.create()
    private fun noti(id: Long, text: String) = CapturedNoti(id, "x", "X 銀行", "", text, 1_000L)
    private fun pend(id: Long, merchant: String, state: Int) =
        CardPend(id, listOf(NotiSrc(id, "x", "X 銀行", "", "")), 85, "", merchant, true, "", 0L, -1, 1_000L, state = state)

    @Test
    fun tags() {
        val pends = listOf(pend(1, "全家", PendState.WAIT), pend(2, "", PendState.WAIT), pend(3, "全家", PendState.DONE), pend(4, "全家", PendState.NOT), pend(5, "全家", PendState.HAD))
        assertEquals(NotiTagKind.GOOD to "讀對了", d.notiTag(noti(1, ""), pends))
        assertEquals(NotiTagKind.PART to "少了商店", d.notiTag(noti(2, ""), pends))
        assertEquals(NotiTagKind.GOOD to "已記", d.notiTag(noti(3, ""), pends))
        assertEquals(NotiTagKind.OFF to "不是消費", d.notiTag(noti(4, ""), pends))
        assertEquals(NotiTagKind.OFF to "已經記過", d.notiTag(noti(5, ""), pends))
        // 沒放進待確認的：看讀出來的結果
        assertEquals(NotiTagKind.OFF to "不是消費", d.notiTag(noti(9, "您已獲得 2 點"), pends))
        assertEquals(NotiTagKind.OFF to "還不會讀", d.notiTag(noti(9, "您的信用卡退款NT\$300"), pends))
        assertEquals(NotiTagKind.PART to "少了商店", d.notiTag(noti(9, "信用卡消費30元通知"), pends))
        assertEquals(NotiTagKind.GOOD to "讀對了", d.notiTag(noti(9, "在全聯消費NT\$350"), pends))
    }
}
