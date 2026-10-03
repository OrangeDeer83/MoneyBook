package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 餘額調整不算收支、存檔讀寫（含舊資料相容）、CSV 往返 */
class AdjustAndCodecTest {
    private val day = LocalDate.of(2026, 10, 3).toEpochDay()

    private fun txn(id: Long, type: TxType, amount: Long, acc: Long, adjust: Boolean = false, cat: Long? = 1L) =
        Txn(id, 1L, type, amount, if (type == TxType.TRANSFER || adjust) null else cat, acc, null, day, "", emptyList(), adjust = adjust)

    private fun base(): AppData {
        val d = Defaults.create()
        val acc = d.accounts.first().id
        val cat = d.topCategories(TxType.EXPENSE).first().id
        val inc = d.topCategories(TxType.INCOME).first().id
        val list = listOf(
            txn(9001, TxType.EXPENSE, 100, acc, cat = cat),
            txn(9002, TxType.INCOME, 1000, acc, cat = inc),
            txn(9003, TxType.INCOME, 500, acc, adjust = true),    // 餘額調整：增加
            txn(9004, TxType.EXPENSE, 200, acc, adjust = true),   // 餘額調整：減少
        )
        return d.copy(txns = sortTxns(list), nextId = 9100)
    }

    @Test
    fun adjustChangesBalanceButNotIncomeOrExpense() {
        val d = base()
        val acc = d.accounts.first().id
        assertEquals(0L - 100 + 1000 + 500 - 200, d.balances()[acc])
        assertEquals(100L, d.txns.expenseSum())
        assertEquals(1000L, d.txns.incomeSum())
        val adj = d.txns.filter { it.adjust }
        assertTrue(adj.all { it.spent == 0L && it.statAmount == 0L })
        assertEquals(700L, adj.sumOf { it.paid })   // paid 仍是金額，所以餘額會動
    }

    private fun roundTrip(d: AppData): AppData = Codec.decode(Codec.encode(d))

    @Test
    fun codecRoundTripKeepsAdjustTradesPricesAndPrefs() {
        val acc = Defaults.create().accounts.first().id
        val d = base().copy(
            trades = listOf(Trade(9010, acc, "0050", "元大50", day, true, 100.0, 100.5, 20, 9001L)),
            prices = listOf(PriceSnap("0050", day, 120.25)),
        ).let { it.copy(prefs = it.prefs.copy(priceFetch = true, priceFetchDay = day)) }
        val back = roundTrip(d)
        assertEquals(d.txns.map { it.adjust }, back.txns.map { it.adjust })
        assertEquals(d.trades, back.trades)
        assertEquals(d.prices, back.prices)
        assertTrue(back.prefs.priceFetch)
        assertEquals(day, back.prefs.priceFetchDay)
        assertEquals(d.balances(), back.balances())
        assertTrue(back.nextId > 9010)
    }

    @Test
    fun oldBackupWithoutNewFieldsStillLoads() {
        // 沒有 adjust、trades、prices、priceFetch 的舊備份
        val json = Codec.encode(Defaults.create().copy(txns = listOf(txn(9001, TxType.EXPENSE, 100, Defaults.create().accounts.first().id))))
        val root = org.json.JSONObject(json)
        root.remove("trades"); root.remove("prices")
        val txns = root.getJSONArray("txns")
        for (i in 0 until txns.length()) txns.getJSONObject(i).remove("adjust")
        root.getJSONObject("prefs").remove("priceFetch").also { }
        root.getJSONObject("prefs").remove("priceFetchDay")
        val back = Codec.decode(root.toString())
        assertTrue(back.trades.isEmpty())
        assertTrue(back.prices.isEmpty())
        assertFalse(back.txns.single().adjust)
        assertFalse(back.prefs.priceFetch)
        assertEquals(0L, back.prefs.priceFetchDay)
    }

    @Test
    fun investAccountTypeSurvivesAndUnknownTypeFallsBack() {
        val d = Defaults.create()
        val inv = Account(9500, "證券", "img:extra_gold", AccountType.INVEST, 0L, 5)
        val back = roundTrip(d.copy(accounts = d.accounts + inv))
        assertEquals(AccountType.INVEST, back.accMap[9500L]!!.type)
        val root = org.json.JSONObject(Codec.encode(d.copy(accounts = d.accounts + inv)))
        root.getJSONArray("accounts").getJSONObject(root.getJSONArray("accounts").length() - 1).put("type", "FUTURE_TYPE")
        assertEquals(AccountType.OTHER, Codec.decode(root.toString()).accMap[9500L]!!.type)
    }

    @Test
    fun csvKeepsAdjustThroughExportAndImport() {
        val d = base()
        val csv = String(CsvIO.export(d), Charsets.UTF_8)
        assertTrue(csv.contains("餘額調整（增加）"))
        assertTrue(csv.contains("餘額調整（減少）"))
        val (imported, n) = CsvIO.import(Defaults.create(), csv)
        assertEquals(4, n)
        val adj = imported.txns.filter { it.adjust }
        assertEquals(2, adj.size)
        assertEquals(setOf(TxType.INCOME, TxType.EXPENSE), adj.map { it.type }.toSet())
        assertEquals(setOf(500L, 200L), adj.map { it.amount }.toSet())
        assertEquals(100L, imported.txns.expenseSum())
        assertEquals(1000L, imported.txns.incomeSum())
    }
}
