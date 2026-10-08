package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 信用卡入帳月份：消費日期和入帳日期不同時，手動指定算進哪一期帳單 */
class BillMonthTest {
    private val card = Account(1, "卡", AccountType.CARD.emoji, AccountType.CARD, 0L, 0, creditLimit = 100_000L, statementDay = 25, dueDay = 10)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun spend(id: Long, y: Int, m: Int, d: Int, amount: Long, bill: Int = 0) =
        Txn(id, 1L, TxType.EXPENSE, amount, null, 1L, null, day(y, m, d), "", emptyList(), billMonth = bill)

    private fun data(vararg t: Txn) = Defaults.create().copy(accounts = listOf(card), txns = sortTxns(t.toList()))

    @Test
    fun autoBillMonthIsTheMonthOfTheDate() {
        assertEquals(YearMonth.of(2026, 10), autoBillMonth(LocalDate.of(2026, 10, 25)))
        assertEquals(YearMonth.of(2026, 10), autoBillMonth(LocalDate.of(2026, 10, 26)))       // 不看結帳日
        assertEquals(YearMonth.of(2026, 12), autoBillMonth(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun billMonthIsNamedByTheMonthMostOfThePeriodFallsIn() {
        // 每月 9 號結帳：9/9 結帳的是 8/10～9/9，大部分在 8 月
        assertEquals(YearMonth.of(2026, 8), cardBillMonthOf(LocalDate.of(2026, 9, 9)))
        assertEquals(YearMonth.of(2026, 9), cardBillMonthOf(LocalDate.of(2026, 10, 9)))
        // 每月 25 號結帳：9/25 結帳的是 8/26～9/25，大部分在 9 月
        assertEquals(YearMonth.of(2026, 9), cardBillMonthOf(LocalDate.of(2026, 9, 25)))
        // 跨年、月底結帳
        assertEquals(YearMonth.of(2025, 12), cardBillMonthOf(LocalDate.of(2026, 1, 5)))
        assertEquals(YearMonth.of(2026, 3), cardBillMonthOf(LocalDate.of(2026, 3, 31)))
        assertEquals(YearMonth.of(2026, 2), cardBillMonthOf(LocalDate.of(2026, 2, 28)))
        // 15 號結帳（一半一半）算前一個月
        assertEquals(YearMonth.of(2026, 8), cardBillMonthOf(LocalDate.of(2026, 9, 15)))
    }

    @Test
    fun lastBillAndCurrentFollowTheListMonths() {
        // 照使用者的 cube卡：9 號結帳、手動把 8/3 改到 7 月、9/1 改到 8 月；8 月帳單 = 8 月列表
        val c9 = card.copy(statementDay = 9)
        val d = Defaults.create().copy(
            accounts = listOf(c9),
            txns = sortTxns(
                listOf(
                    spend(1, 2026, 8, 3, 2, bill = 202607), spend(2, 2026, 8, 6, 1000), spend(3, 2026, 8, 20, 500),
                    spend(4, 2026, 9, 1, 168, bill = 202608), spend(5, 2026, 9, 12, 300), spend(6, 2026, 10, 2, 90),
                )
            ),
        )
        val lastMonth = cardBillMonthOf(LocalDate.of(2026, 9, 9))
        assertEquals(YearMonth.of(2026, 8), lastMonth)
        assertEquals(1000L + 500L + 168L, d.cardBillSpending(c9, lastMonth))                  // 8 月帳單：8/6、8/20、手動的 9/1
        assertEquals(300L + 90L, d.cardBillSpendingAfter(c9, lastMonth))                      // 本期累積：8 月之後所有月份
    }

    @Test
    fun manualBillMonthWinsOverTheDate() {
        val t = spend(1, 2026, 10, 24, 500, bill = 202611)     // 10/24 刷的，但是 11 月才入帳
        assertEquals(YearMonth.of(2026, 11), t.billMonthFor(card))
        assertEquals(YearMonth.of(2026, 10), spend(2, 2026, 10, 24, 500).billMonthFor(card))
    }

    @Test
    fun cardWithoutStatementDayHasNoBillMonth() {
        assertNull(spend(1, 2026, 10, 24, 500).billMonthFor(card.copy(statementDay = 0)))
    }

    @Test
    fun billSpendingMovesWithTheManualMonth() {
        val d = data(spend(1, 2026, 10, 10, 300), spend(2, 2026, 10, 24, 500, bill = 202611), spend(3, 2026, 10, 30, 200))
        assertEquals(300L, d.cardBillSpending(card, YearMonth.of(2026, 10)))          // 10 月帳單：只有 10/10 那筆
        assertEquals(700L, d.cardBillSpending(card, YearMonth.of(2026, 11)))          // 11 月帳單：10/30（自動）＋ 10/24（手動）
    }

    @Test
    fun refundsAndTransfersOutCount() {
        val refund = Txn(4, 1L, TxType.INCOME, 100, null, 1L, null, day(2026, 10, 12), "", emptyList())
        val cash = Txn(5, 1L, TxType.TRANSFER, 1000, null, 1L, 2L, day(2026, 10, 13), "", emptyList(), fee = 30)
        val d = data(spend(1, 2026, 10, 10, 300), refund, cash)
        assertEquals(300L - 100L + 1030L, d.cardBillSpending(card, YearMonth.of(2026, 10)))
    }

    @Test
    fun codeRoundTripAndCodecKeepsTheField() {
        assertEquals(202611, billCode(YearMonth.of(2026, 11)))
        assertEquals(YearMonth.of(2026, 11), billMonthFromCode(202611))
        assertNull(billMonthFromCode(0))
        assertNull(billMonthFromCode(202613))
        val d = data(spend(1, 2026, 10, 24, 500, bill = 202611))
        assertEquals(202611, Codec.decode(Codec.encode(d)).txns.single().billMonth)
        // 舊資料沒有這個欄位：讀出來是 0（自動）
        val root = org.json.JSONObject(Codec.encode(d))
        root.getJSONArray("txns").getJSONObject(0).remove("billMonth")
        assertEquals(0, Codec.decode(root.toString()).txns.single().billMonth)
    }

    @Test
    fun csvRoundTripKeepsTheBillMonth() {
        val d = data(spend(1, 2026, 10, 24, 500, bill = 202611), spend(2, 2026, 10, 10, 300))
        val csv = String(CsvIO.export(d), Charsets.UTF_8)
        assertEquals(true, csv.contains("2026-11"))
        val (back, n) = CsvIO.import(Defaults.create(), csv)
        assertEquals(2, n)
        assertEquals(listOf(202611, 0), back.txns.sortedByDescending { it.day }.map { it.billMonth })
    }

    @Test
    fun laterBillsAreSummedSeparately() {
        val d = data(spend(1, 2026, 10, 10, 300), spend(2, 2026, 10, 24, 500, bill = 202612), spend(3, 2026, 10, 25, 200, bill = 202611))
        assertEquals(700L, d.cardBillSpendingAfter(card, YearMonth.of(2026, 10)))
        assertEquals(500L, d.cardBillSpendingAfter(card, YearMonth.of(2026, 11)))
        assertEquals(0L, d.cardBillSpendingAfter(card, YearMonth.of(2026, 12)))
    }

    @Test
    fun accountDetailShowsAManuallyBilledTxnInItsBillMonth() {
        val moved = spend(1, 2026, 10, 24, 500, bill = 202611)
        assertEquals(YearMonth.of(2026, 11), moved.accountMonthFor(card))
        assertEquals(YearMonth.of(2026, 10), spend(2, 2026, 10, 24, 500).accountMonthFor(card))                 // 沒指定：看消費日期
        assertEquals(YearMonth.of(2026, 10), moved.accountMonthFor(card.copy(type = AccountType.BANK)))         // 不是信用卡：不受影響
        assertEquals(YearMonth.of(2026, 10), moved.accountMonthFor(card.copy(id = 9)))                            // 不是這張卡的記錄
    }
}

