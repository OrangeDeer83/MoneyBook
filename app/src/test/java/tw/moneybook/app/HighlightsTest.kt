package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本月重點：各種句子的門檻與寫法、預算固定第一句、挑句子 */
class HighlightsTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val oct = YearMonth.of(2026, 10)
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()
    private val base = Defaults.create()
    private val acc = base.accounts.first().id
    private val cats = base.topCategories(TxType.EXPENSE)
    private val food = cats[0]
    private val traffic = cats[1]
    private val salary = base.topCategories(TxType.INCOME).first()
    private var nid = 9000L

    private fun exp(day: Long, amount: Long, cat: Category = food, note: String = "", time: Int = -1) =
        Txn(nid++, base.currentBook.id, TxType.EXPENSE, amount, cat.id, acc, null, day, note, emptyList(), time = time)
    private fun inc(day: Long, amount: Long) = Txn(nid++, base.currentBook.id, TxType.INCOME, amount, salary.id, acc, null, day, "", emptyList())

    private fun data(vararg t: Txn, budget: Long = 0L): AppData =
        base.copy(txns = sortTxns(t.toList()), books = base.books.map { if (it.id == base.currentBook.id) it.copy(budget = budget) else it }, nextId = 99999)

    private fun AppData.hl(m: YearMonth = oct) = highlights(HlPeriod.month(m, today))
    private fun List<Highlight>.of(key: String) = firstOrNull { it.key == key }

    @Test
    fun emptyMonthHasOneReminder() {
        val h = data(exp(day(9, 3), 100)).hl()
        assertEquals(listOf("empty"), h.map { it.key })
        assertEquals("這個月還沒有記帳喔，記幾筆我就能幫你整理重點～", h[0].text)
    }

    @Test
    fun budgetIsFirstAndWorriesWhenOver() {
        val h = data(exp(day(10, 2), 6000), exp(day(10, 5), 500), budget = 6000).hl()
        assertEquals("budget", h[0].key)
        assertEquals(Feel.WORRIED, h[0].feel)
        assertEquals("預算已經超過 $500 了…還有 14 天，我們一起省一點好嗎？", h[0].text)
        // 挑句子時預算也固定第一句
        assertEquals("budget", pickHighlights(h, 42L)[0].key)
    }

    @Test
    fun budgetProgress() {
        // 18 號過了 58% 的日子，用了 30%：開心，剩下 14 天每天還能花 500
        val h = data(exp(day(10, 2), 3000), budget = 10000).hl()
        assertEquals(Feel.HAPPY, h.of("budget")!!.feel)
        assertEquals("預算用了 30%，還剩 $7,000，接下來每天還能花 $500", h.of("budget")!!.text)
    }

    @Test
    fun budgetOutNotCountedAndHasOwnSentence() {
        // 交通預設不算預算；餐飲那筆單筆標預算外
        val d0 = data(exp(day(10, 2), 3000), exp(day(10, 3), 2000, traffic), exp(day(10, 4), 500).copy(budgetOut = 1), budget = 10000)
        val d = d0.copy(categories = d0.categories.map { if (it.id == traffic.id) it.copy(budgetMode = 1) else it })
        val h = d.hl()
        assertEquals("預算用了 30%，還剩 $7,000，接下來每天還能花 $500", h.of("budget")!!.text)
        assertEquals("這個月預算外花了 $2,500，主要是「${traffic.name}」", h.of("outBudget")!!.text)
        // 沒有預算外就不講
        assertEquals(null, data(exp(day(10, 2), 3000)).hl().of("outBudget"))
    }

    @Test
    fun savedShareOfIncome() {
        val h = data(inc(day(10, 5), 50000), exp(day(10, 6), 10000)).hl()
        assertEquals("這個月到現在存下 $40,000，是收入的 80% 喔！", h.of("saved")!!.text)
        assertEquals(Feel.CHEER, h.of("saved")!!.feel)
        val h2 = data(exp(day(10, 6), 100), exp(day(10, 7), 200)).hl()
        assertEquals("這個月還沒有收入，目前花了 $300", h2.of("saved")!!.text)
    }

    @Test
    fun comparedWithSameDaysLastMonth() {
        // 這個月 1～18 號花 3,000（交通多 2,000）；上個月 1～18 號只花 1,000；上個月 25 號那筆不算
        val h = data(
            exp(day(10, 3), 1000), exp(day(10, 4), 2000, traffic),
            exp(day(9, 2), 1000), exp(day(9, 25), 5000),
        ).hl()
        assertEquals("比上個月同一段時間多花 $2,000，主要是「${traffic.name}」+$2,000", h.of("vsPrev")!!.text)
        // 看 9 月（已經過完）：比 8 月
        val h9 = data(exp(day(9, 3), 3000), exp(day(9, 4), 100), exp(day(8, 2), 1000)).hl(YearMonth.of(2026, 9))
        assertTrue(h9.of("vsPrev")!!.text.startsWith("比 8 月多花 $2,100"))
    }

    @Test
    fun smallDifferencesAreNotMentioned() {
        val h = data(exp(day(10, 3), 1100), exp(day(9, 3), 1000)).hl()
        assertFalse(h.any { it.key == "vsPrev" })   // 差 100，不到 $500
    }

    @Test
    fun firstTimeCategoryAndBiggest() {
        val h = data(exp(day(9, 1), 100), exp(day(10, 3), 1800, traffic), exp(day(10, 4), 8600, note = "機票")).hl()
        assertEquals("這個月第一次花在「${traffic.name}」，花了 $1,800", h.of("firstCat")!!.text)
        assertEquals("最大一筆是 10/4 的「機票」$8,600", h.of("biggest")!!.text)
    }

    @Test
    fun fixedMonthlyBillNotYetRecorded() {
        val rent = listOf(7, 8, 9).map { exp(day(it, 5), 12000, note = "房租") }
        val h = data(*(rent + exp(day(10, 6), 100)).toTypedArray()).hl()
        assertEquals("每個月固定的「房租」$12,000，這個月還沒記喔", h.of("fixed")!!.text)
        // 這個月記了：改講固定的帳一共多少
        val h2 = data(*(rent + exp(day(10, 5), 12000, note = "房租")).toTypedArray()).hl()
        assertTrue(h2.of("fixed")!!.text.startsWith("每個月固定的帳有 1 筆（「房租」），一共 $12,000"))
    }

    @Test
    fun habitAndTimeOfDay() {
        val list = (1..5).map { exp(day(10, it), 100, time = 20 * 60) }
        val h = data(*list.toTypedArray()).hl()
        assertEquals("已經 13 天沒記帳了，最近有花錢嗎？", h.of("habit")!!.text)
        assertEquals(Feel.SLEEPY, h.of("habit")!!.feel)
        assertEquals("最常在晚上花錢：一共 $500，佔有記時間的 100%", h.of("timeOfDay")!!.text)
    }

    @Test
    fun rangeComparesWithPreviousSameLength() {
        val p = HlPeriod.range(day(10, 11), day(10, 17), today)
        assertEquals(day(10, 4) to day(10, 10), p.prev)
        val h = data(exp(day(10, 12), 3000), exp(day(10, 5), 1000)).highlights(p)
        assertEquals("比前 7 天多花 $2,000，主要是「${food.name}」+$2,000", h.of("vsPrev")!!.text)
    }

    @Test
    fun pickIsStableForSameSeedAndAtMostFive() {
        val h = data(inc(day(10, 1), 50000), exp(day(9, 1), 100), exp(day(10, 3), 1800, traffic), exp(day(10, 4), 8600, note = "機票"), budget = 20000).hl()
        assertTrue(h.size > 5)
        val a = pickHighlights(h, 7L)
        assertEquals(5, a.size)
        assertEquals(a, pickHighlights(h, 7L))
        assertEquals(a.map { it.key }.distinct().size, a.size)
    }
}
