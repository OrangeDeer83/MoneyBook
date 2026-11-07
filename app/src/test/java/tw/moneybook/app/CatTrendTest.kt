package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分類走勢：每個月的金額、平均與紅點、下鑽圖畫到哪個月、趨勢頁預設挑的分類、存檔 */
class CatTrendTest {
    private val base = Defaults.create()
    private val food = base.topCategories(TxType.EXPENSE).first { base.childrenOf(it.id).size >= 2 }
    private val kid = base.childrenOf(food.id)[0]
    private val tops = base.topCategories(TxType.EXPENSE).filter { it.id != food.id }
    private val acc = base.accounts.first().id

    private fun day(y: Int, m: Int, d: Int = 10) = LocalDate.of(y, m, d).toEpochDay()
    private fun t(id: Long, cat: Long, amount: Long, d: Long, type: TxType = TxType.EXPENSE) =
        Txn(id, 1L, type, amount, cat, acc, null, d, "", emptyList())
    private fun data(vararg txns: Txn): AppData = base.copy(txns = sortTxns(txns.toList()), nextId = 999)

    @Test
    fun monthlyIncludesSubCategoriesOnlyForTop() {
        val d = data(
            t(1, food.id, 100, day(2026, 9)), t(2, kid.id, 50, day(2026, 9)), t(3, kid.id, 30, day(2026, 10)),
            t(4, food.id, 999, day(2026, 6)),   // 不在這三個月
        )
        val months = monthsEnding(YearMonth.of(2026, 10), 3)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), months)
        assertEquals(listOf(0L, 150L, 30L), d.catMonthly(food.id, months))   // 大分類含子分類
        assertEquals(listOf(0L, 50L, 30L), d.catMonthly(kid.id, months))     // 子分類只有自己
    }

    @Test
    fun averageStartsFromFirstRecordAndSkipsThisMonth() {
        val months = monthsEnding(YearMonth.of(2026, 11), 6)   // 6～11 月
        // 8 月才開始記；11 月是這個月（還沒過完）不算進平均：平均 = (100+100+100) / 3
        val values = listOf(0L, 0L, 100L, 100L, 100L, 130L)
        val st = trendStat(values, months, YearMonth.of(2026, 8), YearMonth.of(2026, 11))
        assertEquals(100L, st.avg)
        assertEquals(setOf(5), st.warn)   // 130 剛好多 30% 算紅點
        val st2 = trendStat(listOf(0L, 0L, 100L, 100L, 100L, 129L), months, YearMonth.of(2026, 8), YearMonth.of(2026, 11))
        assertTrue(st2.warn.isEmpty())
        // 這個月才第一次記：沒有平均、沒有紅點
        val st3 = trendStat(listOf(0L, 0L, 0L, 0L, 0L, 500L), months, YearMonth.of(2026, 11), YearMonth.of(2026, 11))
        assertEquals(0L, st3.avg)
        assertTrue(st3.warn.isEmpty())
    }

    @Test
    fun drillEndFollowsPeriodButNotFuture() {
        val now = YearMonth.of(2026, 11)
        assertEquals(YearMonth.of(2026, 9), drillTrendEnd(0, YearMonth.of(2026, 9), 2026, 0L, now))
        assertEquals(YearMonth.of(2025, 12), drillTrendEnd(1, now, 2025, 0L, now))       // 去年：到 12 月
        assertEquals(now, drillTrendEnd(1, now, 2026, 0L, now))                           // 今年：到這個月
        assertEquals(YearMonth.of(2026, 3), drillTrendEnd(2, now, 2026, day(2026, 3, 31), now))
        assertEquals(now, drillTrendEnd(0, YearMonth.of(2027, 1), 2026, 0L, now))
    }

    @Test
    fun defaultPicksTopThreeAndSavedPickWins() {
        val end = YearMonth.of(2026, 11)
        val d = data(
            t(1, kid.id, 500, day(2026, 10)), t(2, tops[0].id, 300, day(2026, 9)), t(3, tops[1].id, 200, day(2026, 11)),
            t(4, tops[2].id, 100, day(2026, 1)), t(5, tops[3].id, 9999, day(2025, 5)),   // 超過 12 個月不算
        )
        assertEquals(listOf(food.id, tops[0].id, tops[1].id), d.trendCats(end))   // 子分類算進大分類
        val saved = d.copy(prefs = d.prefs.copy(trendCats = listOf(kid.id, 424242L)))   // 已經刪掉的分類不算
        assertEquals(listOf(kid.id), saved.trendCats(end))
        val gone = d.copy(prefs = d.prefs.copy(trendCats = listOf(424242L)))
        assertEquals(listOf(food.id, tops[0].id, tops[1].id), gone.trendCats(end))   // 挑的都刪光了回到預設
    }

    @Test
    fun pickSurvivesSaveAndOldDataHasNone() {
        val d = data()
        assertEquals(emptyList<Long>(), Codec.decode(Codec.encode(d)).prefs.trendCats)
        val saved = d.copy(prefs = d.prefs.copy(trendCats = listOf(kid.id, food.id)))
        assertEquals(listOf(kid.id, food.id), Codec.decode(Codec.encode(saved)).prefs.trendCats)
    }

    @Test
    fun newTrendCardGoesBelowBarsInOldLayout() {
        // 舊版存的趨勢排版（使用者的備份）：分類走勢放在近幾個月收支下面，不是最下面
        val old = CardLayout(listOf(CardRow("asset", "saved", true), CardRow("savings"), CardRow("bars"), CardRow("months")))
        assertEquals(
            CardLayout(listOf(CardRow("asset", "saved", true), CardRow("savings"), CardRow("bars"), CardRow("catTrend"), CardRow("months"))),
            CardPages.TREND.normalize(old),
        )
        // 把近幾個月收支隱藏了：照舊加到最下面
        val noBars = CardLayout(listOf(CardRow("asset"), CardRow("months")), hidden = listOf("bars", "saved", "savings"))
        assertEquals(CardRow("catTrend"), CardPages.TREND.normalize(noBars).rows.last())
        // 已經有分類走勢（自己搬過）就不動
        val moved = CardLayout(listOf(CardRow("catTrend"), CardRow("asset"), CardRow("bars")), hidden = listOf("saved", "savings", "months"))
        assertEquals(moved, CardPages.TREND.normalize(moved))
    }
}
