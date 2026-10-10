package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 外幣：金額格式與換算、外幣帳戶餘額、匯率與平均成本、存檔 */
class FxTest {
    private val day = LocalDate.of(2026, 10, 3).toEpochDay()
    private val twd = Account(1, "銀行", AccountType.BANK.emoji, AccountType.BANK, 100_000L, 0)
    private val usd = Account(2, "美元帳戶", AccountType.FOREIGN.emoji, AccountType.FOREIGN, 0L, 1, currency = "USD")
    private val jpy = Account(3, "日圓帳戶", AccountType.FOREIGN.emoji, AccountType.FOREIGN, 0L, 2, currency = "JPY")

    private fun txn(id: Long, type: TxType, amount: Long, from: Long?, to: Long? = null, fx: Long = 0L, fee: Long = 0L, d: Long = day) =
        Txn(id, 1L, type, amount, null, from, to, d, "", emptyList(), fee = fee, fxAmount = fx)

    private fun data(vararg t: Txn, rates: List<FxRate> = emptyList()) =
        Defaults.create().copy(accounts = listOf(twd, usd, jpy), txns = sortTxns(t.toList()), rates = rates)

    @Test
    fun formatsForeignAmounts() {
        assertEquals("US$1,234.56", formatFx(123456, "USD"))
        assertEquals("-US$0.05", formatFx(-5, "USD"))
        assertEquals("¥12,000", formatFx(12000, "JPY"))
        assertEquals("₩1,000", formatFx(1000, "KRW"))
        assertEquals("ABC 3.50", formatFx(350, "abc"))      // 自訂幣別：代碼當符號、2 位小數
        assertEquals("12.50", fxPlain(1250, 2))
        assertEquals("120", fxPlain(120, 0))
    }

    @Test
    fun parsesTypedAmounts() {
        assertEquals(1250L, parseFx("12.5", 2))
        assertEquals(1234L, parseFx("12.344", 2))          // 超過的小數位四捨五入
        assertEquals(1235L, parseFx("12.345", 2))
        assertEquals(1_200_000L, parseFx("12,000", 2))
        assertEquals(500L, parseFx("500", 0))
        assertEquals(501L, parseFx("500.6", 0))
        assertNull(parseFx("", 2))
        assertNull(parseFx(".", 2))
        assertNull(parseFx("abc", 2))
    }

    @Test
    fun convertsBetweenTwdAndForeign() {
        assertEquals(31_500L, fxToTwd(100_000L, 2, 31.5))   // US$1,000.00 × 31.5
        assertEquals(2_134L, fxToTwd(10_000L, 0, 0.2134))   // ¥10,000 × 0.2134
        assertEquals(100_000L, twdToFx(31_500L, 2, 31.5))
        assertEquals(0L, twdToFx(100L, 2, 0.0))
        assertEquals(31.5, impliedRate(31_500L, 100_000L, 2)!!, 1e-9)
        assertNull(impliedRate(0L, 100L, 2))
        assertEquals("31.5", rateText(31.5))
        assertEquals("0.2134", rateText(0.2134))
        assertEquals("0.00685", rateText(0.00685))
    }

    @Test
    fun buyingAndSellingForeignCurrencyMovesBothBalances() {
        // 用 31,500 台幣買 US$1,000，再賣出 US$400 換回 12,800 台幣
        val d = data(
            txn(1, TxType.TRANSFER, 31_500, 1, 2, fx = 100_000),
            txn(2, TxType.TRANSFER, 12_800, 2, 1, fx = 40_000),
        )
        val bal = d.balances()
        assertEquals(100_000L - 31_500L + 12_800L, bal[1])
        assertEquals(60_000L, bal[2])                         // US$600.00
    }

    @Test
    fun foreignSpendingUsesTheForeignAmountNotTheTwdEquivalent() {
        // 美元帳戶先買 US$100，花 US$12.50（約當台幣 394）、收入 US$5
        val d = data(
            txn(1, TxType.TRANSFER, 3_150, 1, 2, fx = 10_000),
            txn(2, TxType.EXPENSE, 394, 2, fx = 1_250),
            txn(3, TxType.INCOME, 158, 2, fx = 500),
        )
        assertEquals(10_000L - 1_250L + 500L, d.balances()[2])
        // 台幣帳戶只被買外幣扣掉，外幣消費不動台幣帳戶
        assertEquals(100_000L - 3_150L, d.balances()[1])
    }

    @Test
    fun transferFeeIsOnlyChargedToTheTwdSide() {
        val d = data(txn(1, TxType.TRANSFER, 31_500, 1, 2, fx = 100_000, fee = 30))
        assertEquals(100_000L - 31_500L - 30L, d.balances()[1])
        assertEquals(100_000L, d.balances()[2])
    }

    @Test
    fun statisticsStayInTwd() {
        // 外幣帳戶的消費：amount 是約當台幣，支出統計用它
        val t = txn(1, TxType.EXPENSE, 394, 2, fx = 1_250)
        assertEquals(394L, t.spent)
        assertEquals(394L, listOf(t).expenseSum())
    }

    @Test
    fun runningBalancesFollowTheForeignCurrency() {
        val d = data(
            txn(1, TxType.TRANSFER, 3_150, 1, 2, fx = 10_000),
            txn(2, TxType.EXPENSE, 394, 2, fx = 1_250),
        )
        val r = d.runningBalances(2)
        assertEquals(10_000L, r["t1"])
        assertEquals(8_750L, r["t2"])
        assertEquals(d.balances()[2], r["t2"])
    }

    @Test
    fun rateComesFromTheManualRateOtherwiseTheLatestTrade() {
        val buys = arrayOf(
            txn(1, TxType.TRANSFER, 31_000, 1, 2, fx = 100_000, d = day - 5),
            txn(2, TxType.TRANSFER, 32_000, 1, 2, fx = 100_000, d = day),
        )
        assertEquals(32.0, data(*buys).rateOf("USD")!!, 1e-9)                       // 最近一次買賣
        assertNull(data(*buys).rateOf("JPY"))
        val manual = data(*buys, rates = listOf(FxRate("USD", 30.0, day)))
        assertEquals(30.0, manual.rateOf("USD")!!, 1e-9)                          // 手動設定優先
        assertEquals(30.0, manual.rateOf("usd")!!, 1e-9)
    }

    @Test
    fun twdValueAndAverageCost() {
        val d = data(
            txn(1, TxType.TRANSFER, 31_000, 1, 2, fx = 100_000),     // 31.0
            txn(2, TxType.TRANSFER, 63_000, 1, 2, fx = 200_000),     // 31.5 → 平均 (31000+63000)/300 = 31.333…
            txn(3, TxType.TRANSFER, 10_000, 2, 1, fx = 30_000),      // 賣出不影響平均成本
            rates = listOf(FxRate("USD", 33.0, day)),
        )
        val usdNow = d.accMap[2L]!!
        assertEquals(270_000L, d.balances()[2])
        assertEquals(89_100L, d.twdValue(usdNow, 270_000L))        // US$2,700 × 33
        assertEquals(94_000.0 / 3_000.0, d.avgCost(usdNow)!!, 1e-6)
        assertEquals(5_000L, d.twdValue(d.accMap[1L]!!, 5_000L))   // 台幣帳戶原樣
        assertNull(d.twdValue(d.accMap[3L]!!, 100L))                // 日圓沒有匯率
        assertNull(d.avgCost(d.accMap[1L]!!))
    }

    private fun typed(dec: Int, vararg keys: String): String {
        var e = ""
        for (k in keys) e = Calc.pressFx(e, k, dec)
        return e
    }

    @Test
    fun calculatorHandlesForeignDecimals() {
        assertEquals(1250L, Calc.evalMinor("12.5", 2))
        assertEquals(1649L, Calc.evalMinor("12.5+3.99", 2))
        assertEquals(900L, Calc.evalMinor("10-1.00", 2))
        assertEquals(1000L, Calc.evalMinor("1000", 0))
        assertEquals(0L, Calc.evalMinor("", 2))
        assertEquals(0L, Calc.evalMinor("1-5", 2))                   // 不會變負的
        assertEquals("12.5", typed(2, "1", "2", ".", "5"))
        assertEquals("1.23", typed(2, "1", ".", "2", "3", "4"))      // 超過 2 位小數的第 3 位不收
        assertEquals("1.2", typed(2, "1", ".", ".", "2"))            // 第二個小數點不收
        assertEquals("0.5", typed(2, ".", "5"))                      // 一開頭按小數點補 0
        assertEquals("5+0.5", typed(2, "5", "+", ".", "5"))
        assertEquals("120", typed(0, "1", ".", "2", "0"))            // 日圓沒有小數
        assertEquals("1.0", typed(2, "1", ".", "0"))
        assertEquals("1.05", typed(2, "1", ".", "0", "5"))           // 小數的 0 不會被吃掉
        assertEquals("1.2", Calc.pressFx("1.25", "⌫", 2))
        assertEquals("", Calc.pressFx("1.25", "C", 2))
    }

    @Test
    fun foreignModeFollowsTheAccounts() {
        val d = data()
        assertEquals(FxMode.NONE, d.fxPlan(TxType.EXPENSE, 1, null).mode)
        assertEquals(FxMode.SPEND, d.fxPlan(TxType.EXPENSE, 2, null).mode)
        assertEquals(FxMode.SPEND, d.fxPlan(TxType.INCOME, 3, null).mode)
        assertEquals(FxMode.NONE, d.fxPlan(TxType.TRANSFER, 1, 1).mode)
        val buy = d.fxPlan(TxType.TRANSFER, 1, 2)
        assertEquals(FxMode.BUY, buy.mode)
        assertEquals("USD", buy.acc!!.currency)
        val sell = d.fxPlan(TxType.TRANSFER, 3, 1)
        assertEquals(FxMode.SELL, sell.mode)
        assertEquals("JPY", sell.acc!!.currency)
        assertEquals(FxMode.UNSUPPORTED, d.fxPlan(TxType.TRANSFER, 2, 3).mode)
        assertEquals(FxMode.SAME, d.fxPlan(TxType.TRANSFER, 2, 2).mode)
        assertEquals("12.5", fxExpr(1250, 2))
        assertEquals("12", fxExpr(1200, 2))
        assertEquals("1000", fxExpr(1000, 0))
    }

    @Test
    fun csvCarriesTheForeignAmountAndCurrency() {
        val d = data(
            txn(1, TxType.TRANSFER, 31_500, 1, 2, fx = 100_000, d = day - 3),
            txn(2, TxType.EXPENSE, 394, 2, fx = 1_250, d = day - 1),
            txn(3, TxType.EXPENSE, 50, 1, d = day),
        )
        val csv = String(CsvIO.export(d), Charsets.UTF_8)
        assertTrue(csv.lines().first().endsWith("外幣金額,外幣幣別,入帳月份,刷外幣金額,刷外幣幣別,待請款"))
        assertTrue(csv.contains("1000.00,USD"))
        assertTrue(csv.contains("12.50,USD"))
        val (back, n) = CsvIO.import(Defaults.create(), csv)
        assertEquals(3, n)
        val usdAcc = back.accounts.single { it.name == "美元帳戶" }      // 匯入時自動建立成外幣帳戶
        assertEquals(AccountType.FOREIGN, usdAcc.type)
        assertEquals("USD", usdAcc.currency)
        assertEquals(listOf(100_000L, 1_250L), back.txns.filter { it.fxAmount > 0 }.sortedBy { it.day }.map { it.fxAmount })
        assertEquals(98_750L, back.balances()[usdAcc.id])                 // US$987.50
        assertEquals(0L, back.txns.single { it.amount == 50L }.fxAmount)
        // 沒有外幣欄位的舊 CSV 照常匯入
        val old = "日期,類型,金額,分類,帳戶\n2026-10-01,支出,100,餐飲,現金\n"
        assertEquals(1, CsvIO.import(Defaults.create(), old).second)
    }

    private val spend = txn(1, TxType.EXPENSE, 646, 2, fx = 2_050)        // 用美元帳戶花 US$20.50，約當 $646

    @Test
    fun reimbursementReceivedInForeignCurrencyCreditsTheForeignAccount() {
        // 對方還了 US$20.50，存回美元帳戶：美元餘額回到 0，台幣帳戶不動
        val full = spend.withItems(listOf(ReimbItem("A", 646, listOf(ReimbPay(day, 2L, 646, -1, 2_050)), true)))
        val d = data(full)
        assertEquals(0L, d.balances()[2L])
        assertEquals(100_000L, d.balances()[1L])
        assertEquals(0L, full.reimbOutstanding)
        assertEquals(0L, full.spent)                                    // 報銷收齊，這筆不算自己的支出
        assertEquals(2_050L, d.runningBalances(2L)["p1_0"]?.minus(-2_050L))    // 花 -2,050 之後，收款加回 2,050
        // 對方還台幣 $646，存進台幣帳戶：台幣帳戶增加，美元帳戶只扣花掉的
        val inTwd = spend.withItems(listOf(ReimbItem("A", 646, listOf(ReimbPay(day, 1L, 646)), true)))
        val d2 = data(inTwd)
        assertEquals(100_646L, d2.balances()[1L])
        assertEquals(-2_050L, d2.balances()[2L])
    }

    @Test
    fun convertsBetweenTwdAndForeignAtTheSpendingRate() {
        assertEquals(2_050L, spend.twdToFxAt(646))
        assertEquals(646L, spend.fxToTwdAt(2_050))
        assertEquals(315L, spend.fxToTwdAt(1_000))                       // US$10.00 約 $315
        assertEquals(1_050L, spend.twdToFxAt(331))
        assertEquals(0L, txn(2, TxType.EXPENSE, 100, 1).twdToFxAt(100))   // 台幣消費沒有外幣
        assertEquals(0L, txn(2, TxType.EXPENSE, 100, 1).fxToTwdAt(100))
        assertEquals("USD", data(spend).fxAccountOf(spend)!!.currency)
        assertNull(data(txn(2, TxType.EXPENSE, 100, 1)).fxAccountOf(txn(2, TxType.EXPENSE, 100, 1)))
    }

    @Test
    fun foreignReimbursementReceiptSurvivesSave() {
        val t = spend.withItems(listOf(ReimbItem("A", 646, listOf(ReimbPay(day, 2L, 315, 600, 1_000)), false)))
        val back = Codec.decode(Codec.encode(data(t))).txns.single().items.single().pays.single()
        assertEquals(1_000L, back.fxAmount)
        assertEquals(315L, back.amount)
        // 舊備份的收款沒有 fxAmount
        val root = org.json.JSONObject(Codec.encode(data(t)))
        val arr = root.getJSONArray("txns").getJSONObject(0).getJSONArray("reimbItems").getJSONObject(0).getJSONArray("pays")
        for (i in 0 until arr.length()) arr.getJSONObject(i).remove("fxAmount")
        assertEquals(0L, Codec.decode(root.toString()).txns.single().items.single().pays.single().fxAmount)
    }

    @Test
    fun foreignDataSurvivesSaveAndOldBackupsStillLoad() {
        val d = data(txn(1, TxType.TRANSFER, 31_500, 1, 2, fx = 100_000), rates = listOf(FxRate("USD", 31.8, day)))
        val back = Codec.decode(Codec.encode(d))
        assertEquals(listOf("", "USD", "JPY"), back.accounts.map { it.currency })
        assertEquals(100_000L, back.txns.single().fxAmount)
        assertEquals(31.8, back.rates.single().rate, 1e-9)
        assertEquals(AccountType.FOREIGN, back.accounts[1].type)
        // 舊備份沒有這些欄位
        val root = org.json.JSONObject(Codec.encode(d))
        root.remove("rates")
        val acc = root.getJSONArray("accounts")
        for (i in 0 until acc.length()) acc.getJSONObject(i).remove("currency")
        val tx = root.getJSONArray("txns")
        for (i in 0 until tx.length()) tx.getJSONObject(i).remove("fxAmount")
        val old = Codec.decode(root.toString())
        assertTrue(old.rates.isEmpty())
        assertTrue(old.accounts.all { !it.isForeign })
        assertEquals(0L, old.txns.single().fxAmount)
    }

    @Test
    fun twdAmountsGetNtPrefixOnlyWhenForeignDataExists() {
        try {
            val plain = Defaults.create()
            Money.sync(plain)
            assertEquals("$1,234", formatMoney(1234))
            assertEquals("-$5", formatMoney(-5))
            // 有外幣帳戶
            Money.sync(plain.copy(accounts = plain.accounts + usd))
            assertEquals("NT$1,234", formatMoney(1234))
            assertEquals("-NT$5", formatMoney(-5))
            // 沒有外幣帳戶、但有外幣計價的買賣
            val trade = Trade(1, 1L, "VOO", "", day, true, 1.0, 500.0, 0L, null, "US", "USD", 31.5)
            Money.sync(plain.copy(trades = listOf(trade)))
            assertEquals("NT$99", formatMoney(99))
            // 只有台幣的買賣不算
            Money.sync(plain.copy(trades = listOf(trade.copy(currency = "", rate = 0.0))))
            assertEquals("$99", formatMoney(99))
        } finally {
            Money.twd = "$"
        }
    }
}
