package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 持股、成本、損益（平均成本法）與價格快照的計算 */
class InvestTest {
    private fun data(trades: List<Trade>, prices: List<PriceSnap> = emptyList()): AppData =
        Defaults.create().copy(trades = trades, prices = prices)

    private fun buy(id: Long, day: Long, qty: Double, price: Double, fee: Long = 0L, sym: String = "0050") =
        Trade(id, 1L, sym, "元大50", day, true, qty, price, fee)

    private fun sell(id: Long, day: Long, qty: Double, price: Double, fee: Long = 0L, sym: String = "0050") =
        Trade(id, 1L, sym, "元大50", day, false, qty, price, fee)

    @Test
    fun buyAddsFeeToCost() {
        val p = data(listOf(buy(1, 100, 100.0, 100.0, 20))).portfolio(1L)
        val pos = p.positions.single()
        assertEquals(100.0, pos.qty, 1e-9)
        assertEquals(10_020L, pos.cost)
        assertEquals(10_000L, pos.value)          // 沒有別的價格，用最後一筆成交價
        assertEquals(-20L, pos.gain)
        assertEquals(0L, p.realized)
    }

    @Test
    fun sellPartRealizesGainByAverageCost() {
        // 買 100 股成本 10,020（均價 100.2）；賣 40 股 @120、手續費 10：
        // 收入 4,800−10 = 4,790，成本 4,008，已實現 782；剩 60 股、成本 6,012
        val p = data(listOf(buy(1, 100, 100.0, 100.0, 20), sell(2, 101, 40.0, 120.0, 10))).portfolio(1L)
        val pos = p.positions.single()
        assertEquals(60.0, pos.qty, 1e-9)
        assertEquals(6_012L, pos.cost)
        assertEquals(782L, p.realized)
        assertEquals(120.0, pos.price, 1e-9)       // 最後一筆成交價是賣出的 120
    }

    @Test
    fun sellAllRemovesPosition() {
        val p = data(listOf(buy(1, 100, 10.0, 50.0), sell(2, 101, 10.0, 60.0))).portfolio(1L)
        assertTrue(p.positions.isEmpty())
        assertEquals(100L, p.realized)
    }

    @Test
    fun oversellNeverGoesNegative() {
        val p = data(listOf(buy(1, 100, 10.0, 50.0), sell(2, 101, 15.0, 60.0))).portfolio(1L)
        assertTrue(p.positions.isEmpty())
    }

    @Test
    fun newerPriceSnapshotWinsOverLastTrade() {
        val d = data(listOf(buy(1, 100, 100.0, 100.0)), listOf(PriceSnap("0050", 105, 120.0)))
        val pos = d.portfolio(1L).positions.single()
        assertEquals(120.0, pos.price, 1e-9)
        assertEquals(12_000L, pos.value)
        assertEquals(2_000L, pos.gain)
        assertEquals(105L, pos.priceDay)
    }

    @Test
    fun snapshotOnTheSameDayAsTheTradeWins() {
        // 今天記的持股，今天抓到的價格要生效（這是「抓了價格卻沒更新」那個 bug 的規則）
        val d = data(listOf(buy(1, 100, 10.0, 1.0)), listOf(PriceSnap("0050", 100, 112.8)))
        val pos = d.portfolio(1L).positions.single()
        assertEquals(112.8, pos.price, 1e-9)
        assertEquals(1_128L, pos.value)
    }

    @Test
    fun olderPriceSnapshotIsIgnored() {
        // 價格是買進之前抓的，買進價比較新
        val d = data(listOf(buy(1, 100, 100.0, 100.0)), listOf(PriceSnap("0050", 90, 80.0)))
        assertEquals(100.0, d.portfolio(1L).positions.single().price, 1e-9)
    }

    @Test
    fun latestSnapshotOfSeveralIsUsed() {
        val d = data(
            listOf(buy(1, 100, 10.0, 100.0)),
            listOf(PriceSnap("0050", 110, 130.0), PriceSnap("0050", 105, 120.0), PriceSnap("0050", 120, 90.0)),
        )
        assertEquals(90.0, d.portfolio(1L).positions.single().price, 1e-9)
    }

    @Test
    fun accountFilterAndTotals() {
        val t = listOf(
            buy(1, 100, 10.0, 100.0),
            Trade(2, 2L, "AAPL", "蘋果", 100, true, 2.0, 5000.0, 0),
        )
        val d = data(t)
        assertEquals(1, d.portfolio(1L).positions.size)
        assertEquals(1, d.portfolio(2L).positions.size)
        val all = d.portfolio()
        assertEquals(2, all.positions.size)
        assertEquals(11_000L, all.value)
        assertEquals(all.value - all.cost, all.gain)
    }

    @Test
    fun fractionalQuantity() {
        val pos = data(listOf(buy(1, 100, 0.5, 80_000.0, 0, "BTC-USD"))).portfolio(1L).positions.single()
        assertEquals(40_000L, pos.value)
        assertEquals("0.5", qtyText(pos.qty))
    }

    @Test
    fun pruneMonthlyKeepsCurrentMonthAndOneOfEachOldMonth() {
        val today = LocalDate.of(2026, 10, 15)
        fun snap(sym: String, m: Int, d: Int, p: Double) = PriceSnap(sym, LocalDate.of(2026, m, d).toEpochDay(), p)
        val list = listOf(
            snap("A", 8, 3, 1.0), snap("A", 8, 20, 2.0), snap("A", 8, 28, 3.0),   // 8 月只留最後一筆 28 日
            snap("A", 9, 5, 4.0),                                                    // 9 月只有一筆
            snap("A", 10, 1, 5.0), snap("A", 10, 8, 6.0), snap("A", 10, 14, 7.0),  // 這個月全留
            snap("B", 8, 10, 9.0),
        )
        val out = pruneMonthly(list, today)
        assertEquals(setOf(3.0, 4.0, 5.0, 6.0, 7.0, 9.0), out.map { it.price }.toSet())
        assertEquals(out.sortedBy { it.day }, out)
    }

    @Test
    fun formatting() {
        assertEquals("100", qtyText(100.0))
        assertEquals("0.1235", qtyText(0.12345678))
        assertEquals("1,234.50", priceText(1234.5))
        assertEquals("0.0123", priceText(0.01234))
        assertEquals(10_000L, tradeAmount(100.0, 100.0))
        assertEquals(333L, tradeAmount(3.0, 111.1))
    }
}
