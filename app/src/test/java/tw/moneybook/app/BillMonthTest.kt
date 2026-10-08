package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 信用卡帳單：用結帳日所在的月份稱呼（x 月帳單），明細一期一期看；手動指定入帳月份只能往後挪 */
class BillMonthTest {
    private val card = Account(1, "卡", AccountType.CARD.emoji, AccountType.CARD, 0L, 0, creditLimit = 100_000L, statementDay = 25, dueDay = 10)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private fun spend(id: Long, y: Int, m: Int, d: Int, amount: Long, bill: Int = 0) =
        Txn(id, 1L, TxType.EXPENSE, amount, null, 1L, null, day(y, m, d), "", emptyList(), billMonth = bill)

    private fun data(vararg t: Txn) = Defaults.create().copy(accounts = listOf(card), txns = sortTxns(t.toList()))

    @Test
    fun autoBillMonthFollowsTheStatementDay() {
        assertEquals(YearMonth.of(2026, 10), autoBillMonth(25, LocalDate.of(2026, 10, 25)))   // 結帳當天算這一期
        assertEquals(YearMonth.of(2026, 11), autoBillMonth(25, LocalDate.of(2026, 10, 26)))
        assertEquals(YearMonth.of(2027, 1), autoBillMonth(25, LocalDate.of(2026, 12, 31)))    // 跨年
        assertEquals(YearMonth.of(2026, 2), autoBillMonth(31, LocalDate.of(2026, 2, 28)))     // 結帳日 31 號的月份只到 28 號
    }

    @Test
    fun sportAndCubeExamplesFromTheUser() {
        // sport 每月 12 號結帳：9/12 是 9 月帳單，9/13～9/30 是 10 月帳單
        assertEquals(YearMonth.of(2026, 9), autoBillMonth(12, LocalDate.of(2026, 9, 12)))
        assertEquals(YearMonth.of(2026, 10), autoBillMonth(12, LocalDate.of(2026, 9, 13)))
        assertEquals(YearMonth.of(2026, 10), autoBillMonth(12, LocalDate.of(2026, 9, 30)))
        // cube 每月 9 號結帳：9/9 結帳的 8/10～9/9 是 9 月帳單
        assertEquals(YearMonth.of(2026, 9), autoBillMonth(9, LocalDate.of(2026, 8, 10)))
        assertEquals(YearMonth.of(2026, 9), autoBillMonth(9, LocalDate.of(2026, 9, 9)))
        assertEquals(YearMonth.of(2026, 10), autoBillMonth(9, LocalDate.of(2026, 9, 10)))
        assertEquals(YearMonth.of(2026, 8), autoBillMonth(9, LocalDate.of(2026, 8, 9)))
    }

    @Test
    fun billRangeIsTheDayAfterLastClosingToThisClosing() {
        assertEquals(LocalDate.of(2026, 9, 26) to LocalDate.of(2026, 10, 25), billRange(25, YearMonth.of(2026, 10)))
        assertEquals(LocalDate.of(2026, 8, 10) to LocalDate.of(2026, 9, 9), billRange(9, YearMonth.of(2026, 9)))
        assertEquals(LocalDate.of(2025, 12, 13) to LocalDate.of(2026, 1, 12), billRange(12, YearMonth.of(2026, 1)))
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28), billRange(31, YearMonth.of(2026, 2)))   // 31 號結帳：2 月用月底
    }

    @Test
    fun manualBillMonthMovesLaterButNeverEarlier() {
        val t = spend(1, 2026, 10, 24, 500, bill = 202611)     // 10/24 刷的，銀行 11 月才入帳
        assertEquals(YearMonth.of(2026, 11), t.billMonthFor(card))
        assertEquals(YearMonth.of(2026, 10), spend(2, 2026, 10, 24, 500).billMonthFor(card))
        // 指定的帳單比自動的還早（結帳日比消費日早）：不可能，忽略
        assertEquals(YearMonth.of(2026, 10), spend(3, 2026, 10, 24, 500, bill = 202609).billMonthFor(card))
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
    fun accountDetailListsTheCardOneStatementAtATime() {
        val moved = spend(1, 2026, 10, 24, 500, bill = 202611)
        assertEquals(YearMonth.of(2026, 11), moved.accountMonthFor(card))                                        // 手動挪到 11 月帳單
        assertEquals(YearMonth.of(2026, 10), spend(2, 2026, 10, 24, 500).accountMonthFor(card))                 // 10/24 ≤ 25：10 月帳單
        assertEquals(YearMonth.of(2026, 11), spend(3, 2026, 10, 26, 500).accountMonthFor(card))                 // 10/26：11 月帳單（不看日曆月份）
        assertEquals(YearMonth.of(2026, 10), moved.accountMonthFor(card.copy(type = AccountType.BANK)))         // 不是信用卡：看日期
        assertEquals(YearMonth.of(2026, 10), moved.accountMonthFor(card.copy(id = 9)))                            // 不是這張卡：看日期
        // 繳卡費（轉進這張卡）放在繳款那天所在的那一期
        val pay = Txn(6, 1L, TxType.TRANSFER, 1000, null, 2L, 1L, day(2026, 10, 30), "", emptyList())
        assertEquals(YearMonth.of(2026, 11), pay.accountMonthFor(card))
        // 沒設結帳日的信用卡：維持舊的月份列表（有手動指定的看手動的）
        assertEquals(YearMonth.of(2026, 11), moved.accountMonthFor(card.copy(statementDay = 0)))
        assertEquals(YearMonth.of(2026, 10), spend(7, 2026, 10, 26, 500).accountMonthFor(card.copy(statementDay = 0)))
    }

    @Test
    fun lastBillAndCurrentFollowTheStatements() {
        // 照使用者的 cube卡：9 號結帳。9/9 結帳的 8/10～9/9 是 9 月帳單
        val c9 = card.copy(statementDay = 9)
        val d = Defaults.create().copy(
            accounts = listOf(c9),
            txns = sortTxns(
                listOf(
                    spend(1, 2026, 8, 3, 2), spend(2, 2026, 8, 6, 1000, bill = 202609),     // 8/6 銀行晚幾天才入帳：手動挪到 9 月帳單
                    spend(3, 2026, 8, 20, 500), spend(4, 2026, 9, 1, 168), spend(5, 2026, 9, 12, 300), spend(6, 2026, 10, 2, 90),
                )
            ),
        )
        val last = YearMonth.of(2026, 9)
        assertEquals(1000L + 500L + 168L, d.cardBillSpending(c9, last))                       // 9 月帳單：8/6（手動）、8/20、9/1
        assertEquals(2L, d.cardBillSpending(c9, YearMonth.of(2026, 8)))                       // 8 月帳單：只有 8/3
        assertEquals(300L + 90L, d.cardBillSpendingAfter(c9, last))                           // 本期累積：9 月帳單之後所有期
    }
}
