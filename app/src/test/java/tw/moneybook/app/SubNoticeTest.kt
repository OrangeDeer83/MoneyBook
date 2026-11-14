package tw.moneybook.app

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 訂閱到期通知：哪幾天要提醒、通知寫什麼、通知上的按鈕、下一次檢查的時間 */
class SubNoticeTest {
    private val base = Defaults.create()
    private val acc = base.accounts.first { !it.isForeign }
    private val book = base.currentBook.id
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()

    /** 每月 day 號扣款，從 10/1 開始，之前的都處理過了 */
    private fun sub(id: Long, name: String, dom: Int, amount: Long = 390L, cur: String = "", accId: Long? = acc.id) =
        Sub(id, book, name, amount, cur, null, accId, days = listOf(dom), start = day(10, 1), doneUntil = day(10, 31))

    private fun data(vararg s: Sub) = base.copy(subs = s.toList(), nextId = 1000L)

    @Test
    fun dueDayThenRepeatForNDaysThenStop() {
        val d = data(sub(1, "Netflix", 14))
        val n0 = d.subNotice(day(11, 14), 2, 0, "小鹿")!!
        assertEquals("小鹿：Netflix 今天扣款喔", n0.title)
        assertEquals("\$390・${acc.name}・記一下吧", n0.body)
        assertEquals(1L to day(11, 14), n0.single)
        assertTrue(!n0.worried)
        val n1 = d.subNotice(day(11, 15), 2, 0, "小鹿")!!
        assertEquals("小鹿：Netflix 還沒記喔", n1.title)
        assertEquals("昨天扣了 \$390・${acc.name}", n1.body)
        assertTrue(n1.worried)
        val n2 = d.subNotice(day(11, 16), 2, 0, "小鹿")!!
        assertEquals("小鹿：Netflix 已經 2 天沒記了", n2.title)
        assertTrue(n2.body.endsWith("最後提醒一次"))
        assertNull(d.subNotice(day(11, 17), 2, 0, "小鹿"))
        // 不再提醒：只有當天；每天：一直提醒
        assertNull(d.subNotice(day(11, 15), 0, 0, "小鹿"))
        assertEquals("小鹿：Netflix 還沒記喔", d.subNotice(day(11, 30), SUB_REPEAT_DAILY, 0, "小鹿")!!.title)
        // 記了或略過就不提醒
        val done = d.withSubRecorded(1, day(11, 14))!!
        assertNull(done.subNotice(day(11, 15), 2, 0, "小鹿"))
        assertEquals(1, done.txns.size - d.txns.size)
        assertNull(done.withSubRecorded(1, day(11, 14)))      // 按兩次不會記兩筆
        assertNull(d.withSubSkipped(1, day(11, 14))!!.subNotice(day(11, 14), 2, 0, "小鹿"))
    }

    @Test
    fun severalAreMergedIntoOneWithRecordAll() {
        val d = data(sub(1, "Netflix", 14), sub(2, "Spotify", 14, 149L), sub(3, "iCloud", 13, 90L))
        val n = d.subNotice(day(11, 14), 2, 0, "小貓")!!
        assertEquals("小貓：有 3 筆固定扣款還沒記", n.title)
        assertEquals("合計 \$629・點開來記", n.body)
        assertEquals(listOf("iCloud \$90（昨天）", "Netflix \$390", "Spotify \$149"), n.lines)
        assertNull(n.single)
        assertEquals(listOf(3L to day(11, 13), 1L to day(11, 14), 2L to day(11, 14)), n.all)
        val today = data(sub(1, "Netflix", 14), sub(2, "Spotify", 14, 149L)).subNotice(day(11, 14), 2, 0, "小貓")!!
        assertEquals("小貓：有 2 筆固定扣款今天到期", today.title)
    }

    @Test
    fun aheadRemindsOnceBeforeTheDay() {
        val d = data(sub(1, "Netflix", 14))
        val n = d.subNotice(day(11, 13), 2, 1, "小鹿")!!
        assertEquals("小鹿：Netflix 明天要扣款", n.title)
        assertNull(n.single)
        assertEquals("小鹿：Netflix 3 天後要扣款", d.subNotice(day(11, 11), 2, 3, "小鹿")!!.title)
        assertNull(d.subNotice(day(11, 12), 2, 3, "小鹿"))
    }

    @Test
    fun foreignOnTwdAccountMustOpenTheApp() {
        val d = data(sub(1, "ChatGPT", 14, 2000L, "USD"))
        val n = d.subNotice(day(11, 14), 2, 0, "小鹿")!!
        assertNull(n.single)
        assertTrue(n.body.endsWith("點開來確認匯率再記"))
    }

    @Test
    fun nextCheckIsTodayIfNotYetElseTomorrow() {
        val z = ZoneId.of("Asia/Taipei")
        fun at(h: Int, m: Int) = LocalDate.of(2026, 11, 14).atTime(h, m).atZone(z).toInstant().toEpochMilli()
        assertEquals(at(9, 0), nextNotifyAt(at(8, 30), 9 * 60, z))
        assertEquals(LocalDate.of(2026, 11, 15).atTime(9, 0).atZone(z).toInstant().toEpochMilli(), nextNotifyAt(at(9, 0), 9 * 60, z))
    }

    @Test
    fun settingsSurviveSaveAndOldDataUsesDefaults() {
        val d = base.copy(prefs = base.prefs.copy(subNotify = true, subNotifyTime = 21 * 60 + 30, subNotifyRepeat = SUB_REPEAT_DAILY, subNotifyAhead = 3))
        val back = Codec.decode(Codec.encode(d)).prefs
        assertEquals(true, back.subNotify)
        assertEquals(21 * 60 + 30, back.subNotifyTime)
        assertEquals(SUB_REPEAT_DAILY, back.subNotifyRepeat)
        assertEquals(3, back.subNotifyAhead)
        val root = org.json.JSONObject(Codec.encode(base))
        val po = root.getJSONObject("prefs")
        listOf("subNotify", "subNotifyTime", "subNotifyRepeat", "subNotifyAhead").forEach { po.remove(it) }
        val old = Codec.decode(root.toString()).prefs
        assertEquals(false, old.subNotify)
        assertEquals(9 * 60, old.subNotifyTime)
        assertEquals(2, old.subNotifyRepeat)
        assertEquals(0, old.subNotifyAhead)
    }
}
