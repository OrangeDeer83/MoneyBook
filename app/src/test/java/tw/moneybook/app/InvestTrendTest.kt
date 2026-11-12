package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 投資成效折線：每天的現值、成本、這段期間賺賠，和要算哪幾天 */
class InvestTrendTest {
    private fun data(trades: List<Trade>, prices: List<PriceSnap> = emptyList(), rateHist: List<FxRate> = emptyList()): AppData =
        Defaults.create().copy(trades = trades, prices = prices, rateHist = rateHist)

    private fun t(id: Long, day: Long, buy: Boolean, qty: Double, price: Double, acc: Long = 1L, sym: String = "0050", fee: Long = 0L) =
        Trade(id, acc, sym, "", day, buy, qty, price, fee)

    @Test
    fun valueFollowsDailyClosesAndCostFollowsAverageCost() {
        val d = data(
            listOf(t(1, 100, true, 10.0, 100.0), t(2, 104, false, 5.0, 130.0)),
            listOf(PriceSnap("0050", 101, 110.0, close = true), PriceSnap("0050", 103, 120.0, close = true)),
        )
        val pts = d.investSeries(listOf(100L, 101L, 102L, 103L, 104L), today = 200L)
        assertEquals(listOf(1000L, 1100L, 1100L, 1200L, 650L), pts.map { it.value })   // 104 賣出那天：成交價比收盤價新
        assertEquals(listOf(1000L, 1000L, 1000L, 1000L, 500L), pts.map { it.cost })
        assertEquals(listOf(1000L, 1000L, 1000L, 1000L, 350L), pts.map { it.flow })
        // 這段期間：已實現 150 + 未實現 150
        assertEquals(300L, periodGain(pts))
        assertEquals(0.3, pts.last().pct!!, 1e-9)
    }

    @Test
    fun lastPointEqualsThePortfolioValue() {
        val d = data(
            listOf(t(1, 100, true, 10.0, 100.0), t(2, 150, true, 3.0, 90.0, sym = "2330")),
            listOf(PriceSnap("0050", 199, 105.0, close = true), PriceSnap("0050", 200, 107.5), PriceSnap("2330", 180, 95.0)),
        )
        val last = d.investSeries(listOf(150L, 200L), today = 200L).last()
        val pf = d.portfolio()
        assertEquals(pf.value, last.value)
        assertEquals(pf.cost, last.cost)
    }

    @Test
    fun foreignHoldingsUseTheRateOfThatDay() {
        val buy = Trade(1, 1L, "VOO", "", 100, true, 1.0, 500.0, 0L, null, "US", "USD", 30.0)
        val d = data(
            listOf(buy),
            listOf(PriceSnap("VOO", 101, 510.0, "USD", close = true), PriceSnap("VOO", 101, 999.0, "")),  // 台幣價格不能拿來用
            listOf(FxRate("USD", 32.0, 101)),
        )
        val pts = d.investSeries(listOf(100L, 101L), today = 500L)
        assertEquals(listOf(15_000L, 16_320L), pts.map { it.value })
        assertEquals(listOf(15_000L, 15_000L), pts.map { it.cost })
    }

    @Test
    fun accountAndSymbolFilters() {
        val d = data(listOf(t(1, 100, true, 1.0, 100.0), t(2, 100, true, 2.0, 50.0, acc = 2L), t(3, 100, true, 1.0, 10.0, sym = "2330")))
        assertEquals(210L, d.investSeries(listOf(100L), today = 200L).single().value)
        assertEquals(110L, d.investSeries(listOf(100L), setOf(1L), today = 200L).single().value)
        assertEquals(100L, d.investSeries(listOf(100L), setOf(1L), "0050", today = 200L).single().value)
        assertEquals(100L, d.firstTradeDay(setOf(2L)))
        assertEquals(null, d.firstTradeDay(setOf(9L)))
    }

    @Test
    fun daysPerRange() {
        val today = LocalDate.of(2026, 11, 12).toEpochDay()
        val longAgo = LocalDate.of(2024, 3, 5).toEpochDay()
        val (m1, s1) = investDays(1, longAgo, today)
        assertEquals(InvestStep.DAY, s1)
        assertEquals(LocalDate.of(2026, 10, 12).toEpochDay(), m1.first())
        assertEquals(today, m1.last())
        assertEquals(32, m1.size)
        // 不會早於第一次買進
        assertEquals(today - 5, investDays(3, today - 5, today).first.first())
        val (y, sy) = investDays(12, longAgo, today)
        assertEquals(InvestStep.WEEK, sy)
        assertEquals(today, y.last())
        assertTrue(y.zipWithNext().all { (a, b) -> b - a == 7L })
        val (all, sa) = investDays(0, longAgo, today)
        assertEquals(InvestStep.MONTH, sa)
        assertEquals(LocalDate.of(2024, 3, 31).toEpochDay(), all.first())
        assertEquals(LocalDate.of(2026, 10, 31).toEpochDay(), all[all.size - 2])
        assertEquals(today, all.last())
        assertEquals(InvestStep.DAY, investDays(0, today - 30, today).second)
        assertEquals(InvestStep.WEEK, investDays(0, today - 200, today).second)
    }

    @Test
    fun defaultRangeIsThreeMonthsUnlessTheHistoryIsShorter() {
        val today = LocalDate.of(2026, 11, 12).toEpochDay()
        assertEquals(3, defaultInvestRange(LocalDate.of(2026, 8, 12).toEpochDay(), today))
        assertEquals(1, defaultInvestRange(LocalDate.of(2026, 8, 13).toEpochDay(), today))
        assertEquals(1, defaultInvestRange(today, today))
    }
}
