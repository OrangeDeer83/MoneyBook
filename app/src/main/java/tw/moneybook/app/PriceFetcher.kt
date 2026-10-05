package tw.moneybook.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId

/**
 * 從 Yahoo Finance 的公開行情介面抓最新價格（台股、美股、加密貨幣都用同一個來源）。
 * 只會把「代號」送出去，不會送任何記帳資料。抓不到就回傳 null，不影響手動輸入的價格。
 * 這不是官方保證穩定的介面，哪天改了或擋了，只會變成抓不到。
 */
object PriceFetcher {
    /** 換成 NT$ 的價格，和這個價格是哪一天的 */
    data class Quote(val price: Double, val day: Long)

    /** 回傳 meta 區塊（價格、幣別、時間都在裡面）；任何錯誤都回 null */
    private fun meta(symbol: String): JSONObject? {
        var c: HttpURLConnection? = null
        return try {
            c = URL("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?range=5d&interval=1d").openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (c.responseCode != 200) return null
            val text = c.inputStream.bufferedReader().use { it.readText() }
            val res = JSONObject(text).getJSONObject("chart").optJSONArray("result")
            if (res == null || res.length() == 0) null else res.getJSONObject(0).getJSONObject("meta")
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    /** 抓外幣換台幣的目前匯率（1 單位外幣 = 幾元台幣，例如 USD → 31.5）；抓不到回傳 null。只送出幣別代碼 */
    suspend fun fetchRate(code: String): Double? = withContext(Dispatchers.IO) {
        val p = meta("${code.trim().uppercase()}TWD=X")?.optDouble("regularMarketPrice", Double.NaN) ?: return@withContext null
        if (p.isNaN() || p <= 0.0) null else p
    }

    /** 抓某個代號的最新價格，外幣用當下匯率換成 NT$；抓不到（或匯率抓不到）回傳 null */
    suspend fun fetch(symbol: String, market: String = ""): Quote? = withContext(Dispatchers.IO) {
        for (s in Markets.candidates(symbol, market)) {
            val m = meta(s) ?: continue
            var price = m.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN() || price <= 0.0) continue
            val cur = m.optString("currency", "TWD")
            if (cur != "TWD") {
                // 外幣：用「幣別 → TWD」的匯率換算，換不到就當作抓價失敗，不要存成錯的幣別
                val rate = meta("${cur}TWD=X")?.optDouble("regularMarketPrice", Double.NaN)
                if (rate == null || rate.isNaN() || rate <= 0.0) return@withContext null
                price *= rate
            }
            val t = m.optLong("regularMarketTime", 0L)
            val day = if (t > 0L) Instant.ofEpochSecond(t).atZone(ZoneId.of("Asia/Taipei")).toLocalDate().toEpochDay()
            else java.time.LocalDate.now().toEpochDay()
            return@withContext Quote(price, day)
        }
        null
    }
}
