package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 市場與抓價代號、持股帶著市場、存檔相容 */
class MarketTest {
    private fun c(sym: String, market: String) = Markets.candidates(sym, market)

    @Test
    fun taiwanTriesListedThenOtc() {
        assertEquals(listOf("0050.TW", "0050.TWO"), c("0050", "TW"))
        assertEquals(listOf("006208.TW", "006208.TWO"), c("006208", "TW"))
        assertEquals(listOf("00679B.TW", "00679B.TWO"), c("00679B", "TW"))
        assertEquals(listOf("2330.TW"), c("2330.TW", "TW").filter { it == "2330.TW" })   // 自己帶了後綴就照用
        assertEquals(listOf("2330.TW"), c("2330.TW", "TW"))
    }

    @Test
    fun legacyBlankMarketKeepsOldBehavior() {
        assertEquals(listOf("0050.TW", "0050.TWO"), c("0050", ""))
        assertEquals(listOf("AAPL"), c("AAPL", ""))
        assertEquals(listOf("BTC-USD"), c("BTC-USD", ""))
    }

    @Test
    fun otherMarkets() {
        assertEquals(listOf("AAPL"), c("aapl", "US"))
        assertEquals(listOf("7203.T"), c("7203", "JP"))
        assertEquals(listOf("7203.T"), c("7203.T", "JP"))
        assertEquals(listOf("005930.KS", "005930.KQ"), c("005930", "KR"))
        assertEquals(listOf("0700.HK"), c("700", "HK"))
        assertEquals(listOf("0700.HK"), c("0700", "HK"))
        assertEquals(listOf("9988.HK"), c("9988", "HK"))
        assertEquals(listOf("BTC-USD"), c("BTC", "CRYPTO"))
        assertEquals(listOf("ETH-USD"), c("ETH-USD", "CRYPTO"))
    }

    @Test
    fun koreanCodeIsNotTreatedAsTaiwan() {
        // 這就是需要選市場的原因：韓股 6 位數字代號，用台股規則會查成 005930.TW（查不到）
        assertEquals(listOf("005930.TW", "005930.TWO"), c("005930", ""))
        assertEquals(listOf("005930.KS", "005930.KQ"), c("005930", "KR"))
    }

    @Test
    fun emptyOrBlankSymbolHasNoCandidates() {
        assertTrue(c("", "TW").isEmpty())
        assertTrue(c("  ", "US").isEmpty())
    }

    @Test
    fun labelsAndExamples() {
        assertEquals(listOf("台股", "美股", "日股", "韓股", "港股", "加密貨幣"), Markets.all.map { it.second })
        assertEquals("日股", Markets.label("JP"))
        assertEquals("", Markets.label(""))
        assertTrue(Markets.example("JP").contains("7203"))
        assertTrue(Markets.example("KR").contains("005930"))
    }

    @Test
    fun portfolioCarriesTheLatestNonBlankMarket() {
        val d = Defaults.create().copy(
            trades = listOf(
                Trade(1, 1L, "7203", "TOYOTA", 100, true, 10.0, 3000.0, 0, null, "JP"),
                Trade(2, 1L, "7203", "TOYOTA", 101, true, 5.0, 3100.0, 0, null, ""),   // 舊資料沒寫市場，沿用前一筆
                Trade(3, 1L, "0050", "", 100, true, 1.0, 100.0),
            ),
        )
        val pos = d.portfolio(1L).positions.associateBy { it.symbol }
        assertEquals("JP", pos["7203"]!!.market)
        assertEquals("", pos["0050"]!!.market)
    }

    @Test
    fun marketSurvivesSaveAndOldTradesLoadWithBlankMarket() {
        val acc = Defaults.create().accounts.first().id
        val d = Defaults.create().copy(
            trades = listOf(Trade(9010, acc, "005930", "SAMSUNG", 100, true, 3.0, 8000.0, 0, null, "KR")),
        )
        val back = Codec.decode(Codec.encode(d))
        assertEquals("KR", back.trades.single().market)
        // 舊備份：trades 裡沒有 market 欄位
        val root = org.json.JSONObject(Codec.encode(d))
        root.getJSONArray("trades").getJSONObject(0).remove("market")
        assertEquals("", Codec.decode(root.toString()).trades.single().market)
    }

    @Test
    fun fetchResultOkMeansHasPrice() {
        assertTrue(FetchResult("0050", "ETF", "TW", 120.5).ok)
        assertFalse(FetchResult("ZZZ", "", "US", null).ok)
    }
}
