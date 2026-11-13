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
    private fun meta(symbol: String): JSONObject? = result(symbol, "range=5d&interval=1d")?.optJSONObject("meta")

    /**
     * 最近一次抓不到的原因（給抓價結果顯示，例如「Yahoo 暫時拒絕」「連不上網路」）。
     * 抓價是一檔一檔依序抓的，每次失敗都會蓋掉；呼叫的人在開始抓一檔前先清掉。
     */
    @Volatile
    var lastError: String? = null

    /** 回傳 chart.result[0]（meta、每天的時間與收盤價都在裡面）；任何錯誤都回 null（原因記在 lastError）。
     * Yahoo 回「請求太頻繁」（429）時等一下、換另一台主機再試一次 */
    private fun result(symbol: String, query: String): JSONObject? {
        for ((i, host) in listOf("query1", "query2").withIndex()) {
            if (i > 0) Thread.sleep(1_500L)
            var c: HttpURLConnection? = null
            try {
                c = URL("https://$host.finance.yahoo.com/v8/finance/chart/$symbol?$query").openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0")
                val code = c.responseCode
                if (code == 429 || code >= 500) {
                    lastError = if (code == 429) "Yahoo 暫時拒絕（請求太頻繁，過幾分鐘再試）" else "Yahoo 伺服器出錯（$code）"
                    continue
                }
                if (code != 200) {
                    lastError = if (code == 404) "Yahoo 找不到這個代號" else "Yahoo 回應錯誤（$code）"
                    return null
                }
                val text = c.inputStream.bufferedReader().use { it.readText() }
                val res = JSONObject(text).getJSONObject("chart").optJSONArray("result")
                if (res == null || res.length() == 0) {
                    lastError = "Yahoo 沒有這個代號的資料"
                    return null
                }
                return res.getJSONObject(0)
            } catch (e: java.net.UnknownHostException) {
                lastError = "連不上網路（找不到 Yahoo 的伺服器）"
                return null
            } catch (e: java.io.IOException) {
                lastError = "連不上網路（${e.javaClass.simpleName}）"
                return null
            } catch (e: Exception) {
                lastError = "看不懂 Yahoo 回傳的資料（${e.javaClass.simpleName}）"
                return null
            } finally {
                c?.disconnect()
            }
        }
        return null
    }

    /** 原幣價格（不換算成台幣）：價格、日期，和 Yahoo 回報的幣別 */
    data class NativeQuote(val price: Double, val day: Long, val currency: String)

    /** 抓某個代號的最新價格，保留原幣（美股就是美金）；抓不到回傳 null */
    suspend fun fetchNative(symbol: String, market: String = ""): NativeQuote? = withContext(Dispatchers.IO) {
        for (s in Markets.candidates(symbol, market)) {
            val m = meta(s) ?: continue
            val price = m.optDouble("regularMarketPrice", Double.NaN)
            if (price.isNaN() || price <= 0.0) continue
            val t = m.optLong("regularMarketTime", 0L)
            val day = if (t > 0L) Instant.ofEpochSecond(t).atZone(ZoneId.of("Asia/Taipei")).toLocalDate().toEpochDay()
            else java.time.LocalDate.now().toEpochDay()
            return@withContext NativeQuote(price, day, m.optString("currency", "").uppercase())
        }
        null
    }

    /**
     * 最新價格和歷史收盤價：price／priceDay 是最新成交價（抓不到是 null），closes 是每個交易日（交易所當地日期）的收盤價。
     * currency 是 Yahoo 回報的幣別。
     */
    data class History(val currency: String, val price: Double?, val priceDay: Long, val closes: List<Pair<Long, Double>>)

    /** 從 chart.result[0] 讀出每天的收盤價（日期用交易所的時區；沒成交的 null 跳過，同一天只留最後一筆） */
    internal fun closesOf(r: JSONObject): List<Pair<Long, Double>> {
        val ts = r.optJSONArray("timestamp") ?: return emptyList()
        val close = r.optJSONObject("indicators")?.optJSONArray("quote")?.optJSONObject(0)?.optJSONArray("close") ?: return emptyList()
        val tz = try { ZoneId.of(r.optJSONObject("meta")?.optString("exchangeTimezoneName", "").orEmpty()) } catch (_: Exception) { ZoneId.of("Asia/Taipei") }
        val out = java.util.TreeMap<Long, Double>()
        for (i in 0 until minOf(ts.length(), close.length())) {
            if (close.isNull(i)) continue
            val c = close.optDouble(i, Double.NaN)
            if (c.isNaN() || c <= 0.0) continue
            out[Instant.ofEpochSecond(ts.getLong(i)).atZone(tz).toLocalDate().toEpochDay()] = c
        }
        return out.entries.map { it.key to it.value }
    }

    /** 用 Yahoo 的代號抓 fromDay 到今天的收盤價和最新價格 */
    private fun historyOf(yahoo: String, fromDay: Long): History? {
        val p1 = java.time.LocalDate.ofEpochDay(fromDay).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        val p2 = Instant.now().epochSecond + 86_400L
        val r = result(yahoo, "period1=$p1&period2=$p2&interval=1d") ?: return null
        val m = r.optJSONObject("meta") ?: return null
        val price = m.optDouble("regularMarketPrice", Double.NaN)
        val closes = closesOf(r)
        val ok = !price.isNaN() && price > 0.0
        if (!ok && closes.isEmpty()) return null
        val t = m.optLong("regularMarketTime", 0L)
        val day = if (t > 0L) Instant.ofEpochSecond(t).atZone(ZoneId.of("Asia/Taipei")).toLocalDate().toEpochDay()
        else java.time.LocalDate.now().toEpochDay()
        return History(m.optString("currency", "").uppercase(), if (ok) price else null, day, closes)
    }

    /** 抓某個代號 fromDay 到今天的收盤價和最新價格（依市場試各種代號寫法）；抓不到回傳 null */
    suspend fun history(symbol: String, market: String, fromDay: Long): History? = withContext(Dispatchers.IO) {
        for (s in Markets.candidates(symbol, market)) {
            historyOf(s, fromDay)?.let { return@withContext it }
        }
        null
    }

    /** 抓外幣換台幣 fromDay 到今天的匯率（1 單位外幣 = 幾元台幣）；只送出幣別代碼 */
    suspend fun rateHistory(code: String, fromDay: Long): History? = withContext(Dispatchers.IO) {
        historyOf("${code.trim().uppercase()}TWD=X", fromDay)
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
