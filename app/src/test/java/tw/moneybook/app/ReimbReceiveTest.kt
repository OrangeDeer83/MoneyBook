package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 報銷收款的分配：台幣、外幣、兩種一起收 */
class ReimbReceiveTest {
    private val day = LocalDate.of(2026, 10, 3).toEpochDay()
    // 帳 A：用美元帳戶花 US$20.50（約當 $646）；帳 B：用台幣花 $300
    private val usdSpend = Txn(1, 1L, TxType.EXPENSE, 646, null, 2L, null, day, "", emptyList(), fxAmount = 2_050)
    private val twdSpend = Txn(2, 1L, TxType.EXPENSE, 300, null, 1L, null, day, "", emptyList())
    private val claims = listOf(ClaimInput("1:0", usdSpend, 646), ClaimInput("2:0", twdSpend, 300))
    private val currencyOf = { t: Txn -> if (t.fxAmount > 0L) "USD" else "" }

    private fun twd(amount: Long) = ReceiptLine(1L, "", amount)
    private fun usd(units: Long) = ReceiptLine(2L, "USD", units)

    @Test
    fun twdOnlyFillsOldestFirstAndSaysWhoIsShort() {
        val r = allocateReceipt(claims, listOf(twd(700)), currencyOf)
        assertEquals(646L, r[0].credit)
        assertFalse(r[0].short)
        assertEquals(54L, r[1].credit)
        assertTrue(r[1].short)
    }

    @Test
    fun foreignOnlySettlesTheClaimsPaidInThatCurrency() {
        val r = allocateReceipt(claims, listOf(usd(2_050)), currencyOf)
        assertEquals(646L, r[0].credit)                 // 收到原本付的外幣，剛好收齊
        assertEquals(2_050L, r[0].parts.single().units)
        assertEquals(0L, r[1].credit)                   // 台幣付的帳不受外幣收款影響
        assertTrue(r[1].parts.isEmpty())
    }

    @Test
    fun partialForeignUsesTheSpendingRate() {
        val r = allocateReceipt(claims, listOf(usd(1_000)), currencyOf)
        assertEquals(315L, r[0].credit)                 // US$10.00 ≈ $315
        assertTrue(r[0].short)
    }

    @Test
    fun overpayingInForeignAddsTheExtraAtTheSameRate() {
        val r = allocateReceipt(claims, listOf(usd(2_100)), currencyOf)
        assertEquals(646L + 16L, r[0].credit)           // 多收的 US$0.50 ≈ $16，算回饋
    }

    @Test
    fun mixedCurrenciesInOneReceipt() {
        // 外幣先沖同幣別的帳，台幣再沖剩下的：US$10.00（≈$315）＋ 台幣 $300
        val r = allocateReceipt(claims, listOf(twd(300), usd(1_000)), currencyOf)
        assertEquals(2, r[0].parts.size)
        assertEquals(315L + 300L, r[0].credit)          // 帳 A 還欠 646，收了 615，還差 31
        assertTrue(r[0].short)
        assertEquals(0L, r[1].credit)
        // 外幣收齊帳 A，台幣就全給帳 B（多的算回饋）
        val r2 = allocateReceipt(claims, listOf(twd(600), usd(2_050)), currencyOf)
        assertEquals(646L, r2[0].credit)
        assertEquals(1, r2[0].parts.size)
        assertEquals(600L, r2[1].credit)                // 帳 B 只欠 300，多收的 300 算在這一筆
        assertFalse(r2[1].short)
    }

    @Test
    fun theOrderOfTheLinesDoesNotMatter() {
        val a = allocateReceipt(claims, listOf(twd(300), usd(1_000)), currencyOf)
        val b = allocateReceipt(claims, listOf(usd(1_000), twd(300)), currencyOf)
        assertEquals(a.map { it.credit }, b.map { it.credit })
    }

    @Test
    fun manualAmountsOnlyApplyToTheFirstCurrency() {
        val r = allocateReceipt(claims, listOf(twd(700)), currencyOf, overrides = mapOf("1:0" to 100L))
        assertEquals(100L, r[0].credit)
        assertEquals(54L, r[1].credit)
    }

    @Test
    fun receiptWithNothingEnteredAllocatesNothing() {
        val r = allocateReceipt(claims, listOf(twd(0), usd(0)), currencyOf)
        assertTrue(r.all { it.parts.isEmpty() && it.credit == 0L })
    }
}
