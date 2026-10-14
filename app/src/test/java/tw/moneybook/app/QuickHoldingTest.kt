package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 快速加入庫存：一次加入現有持股（見 withQuickHoldings、Markets.guess） */
class QuickHoldingTest {
    private fun data(): AppData {
        val base = Defaults.create()
        val inv = Account(1, "證券", AccountType.INVEST.emoji, AccountType.INVEST, 0L, 1)
        return base.copy(accounts = base.accounts + inv, nextId = 100)
    }

    @Test
    fun guessMarketBySymbol() {
        assertEquals("TW", Markets.guess("2330"))
        assertEquals("TW", Markets.guess("0050"))
        assertEquals("TW", Markets.guess("00878"))
        assertEquals("TW", Markets.guess("00679B"))   // 債券 ETF 代號結尾有字母，還是台股
        assertEquals("US", Markets.guess("AAPL"))
        assertEquals("US", Markets.guess("brk-b"))
        assertEquals("CRYPTO", Markets.guess("BTC-USD"))
        assertEquals("TW", Markets.guess(""))
    }

    @Test
    fun addsOneBuyPerHoldingWithoutTouchingAccounts() {
        val d = data()
        val after = d.withQuickHoldings(
            1L, 500L,
            listOf(QuickHolding("2330", 1000.0, 580.0, "TW"), QuickHolding("0050", 2000.0, 152.3, "TW")),
            emptyMap(),
        )!!
        assertEquals(2, after.trades.size)
        assertTrue(after.trades.all { it.buy && it.fee == 0L && it.txnId == null && it.day == 500L && it.accountId == 1L })
        assertEquals(d.txns, after.txns)                       // 沒有轉帳、不動任何餘額
        assertEquals(d.balances(), after.balances())
        assertEquals(102L, after.nextId)
        val p = after.portfolio(1L)
        assertEquals(580_000L, p.positions.single { it.symbol == "2330" }.cost)
        assertEquals(304_600L, p.positions.single { it.symbol == "0050" }.cost)
        assertEquals(580.0, p.positions.single { it.symbol == "2330" }.avgPrice, 1e-9)
    }

    @Test
    fun foreignMarketUsesRate() {
        val after = data().withQuickHoldings(1L, 500L, listOf(QuickHolding("AAPL", 10.0, 185.2, "US")), mapOf("USD" to 31.5))!!
        val t = after.trades.single()
        assertEquals("USD", t.currency)
        assertEquals(31.5, t.rate, 1e-9)
        val pos = after.portfolio(1L).positions.single()
        assertEquals(58_338L, pos.cost)                        // 10 × 185.2 × 31.5
        assertEquals(185.2, pos.avgPrice, 1e-9)
    }

    @Test
    fun missingRateOrBadValuesReturnNull() {
        val d = data()
        assertNull(d.withQuickHoldings(1L, 500L, listOf(QuickHolding("AAPL", 10.0, 185.2, "US")), emptyMap()))
        assertNull(d.withQuickHoldings(1L, 500L, listOf(QuickHolding("AAPL", 10.0, 185.2, "US")), mapOf("USD" to 0.0)))
        assertNull(d.withQuickHoldings(1L, 500L, listOf(QuickHolding("2330", 0.0, 580.0, "TW")), emptyMap()))
        assertNull(d.withQuickHoldings(1L, 500L, listOf(QuickHolding("2330", 10.0, 0.0, "TW")), emptyMap()))
        assertNull(d.withQuickHoldings(1L, 500L, listOf(QuickHolding(" ", 10.0, 5.0, "TW")), emptyMap()))
        assertNull(d.withQuickHoldings(1L, 500L, emptyList(), emptyMap()))
        assertNull(d.withQuickHoldings(999L, 500L, listOf(QuickHolding("2330", 10.0, 5.0, "TW")), emptyMap()))
    }

    @Test
    fun totalCostDividedByQtyKeepsCostExact() {
        // 填總成本 12,345、股數 7：每股成本 = 12345 / 7，成本合計仍然是 12,345
        val avg = 12_345.0 / 7.0
        val after = data().withQuickHoldings(1L, 500L, listOf(QuickHolding("2330", 7.0, avg, "TW")), emptyMap())!!
        assertEquals(12_345L, after.portfolio(1L).positions.single().cost)
    }

    @Test
    fun laterBuyContinuesAverageCost() {
        val d = data().withQuickHoldings(1L, 500L, listOf(QuickHolding("2330", 1000.0, 500.0, "TW")), emptyMap())!!
        val more = d.copy(trades = d.trades + Trade(200, 1L, "2330", "", 501L, true, 1000.0, 600.0, 0L))
        assertEquals(550.0, more.portfolio(1L).positions.single().avgPrice, 1e-9)
    }
}
