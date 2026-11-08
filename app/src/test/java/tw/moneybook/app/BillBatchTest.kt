package tw.moneybook.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 信用卡多選：一次改好幾筆的入帳月份（只能往後挪；選依消費日期自動就回到原本那一期）；從卡轉出的轉帳也能改，繳卡費不能 */
class BillBatchTest {
    private val card = Account(5, "信用卡", "", AccountType.CARD, 0, 0, statementDay = 25)
    private val bank = Account(6, "銀行", "", AccountType.BANK, 0, 1)
    private val ecard = Account(8, "悠遊卡", "", AccountType.ECARD, 0, 2)
    private fun day(m: Int, d: Int) = LocalDate.of(2026, m, d).toEpochDay()
    private fun spend(id: Long, d: Long, acc: Long = 5) = Txn(id, 1L, TxType.EXPENSE, 100, 1L, acc, null, d, "", emptyList())

    private fun data(): AppData = Defaults.create().copy(
        accounts = listOf(card, bank, ecard),
        txns = sortTxns(listOf(
            spend(1, day(10, 24)), spend(2, day(10, 23)), spend(3, day(10, 20)),
            spend(4, day(10, 22), acc = 6),   // 不是這張卡
            Txn(7, 1L, TxType.TRANSFER, 500, null, 6, 5, day(10, 21), "", emptyList()),   // 繳卡費（轉帳）
            Txn(9, 1L, TxType.TRANSFER, 300, null, 5, 8, day(10, 24), "", emptyList()),   // 信用卡儲值悠遊卡
        )),
        nextId = 100,
    )
    private fun AppData.bill(id: Long) = txns.first { it.id == id }.billMonthFor(card)

    @Test
    fun moveSeveralToNextBill() {
        val d = data().withBillMonths(card, setOf(1, 2, 4, 7), YearMonth.of(2026, 11))
        assertEquals(YearMonth.of(2026, 11), d.bill(1))
        assertEquals(YearMonth.of(2026, 11), d.bill(2))
        assertEquals(YearMonth.of(2026, 10), d.bill(3))          // 沒勾的不動
        assertEquals(0, d.txns.first { it.id == 4L }.billMonth)  // 別的帳戶不動
        assertEquals(0, d.txns.first { it.id == 7L }.billMonth)  // 繳卡費不動
        assertFalse(d.txns.first { it.id == 7L }.billMovable(card))
    }

    @Test
    fun transferOutOfCardCanMove() {
        val t9 = data().txns.first { it.id == 9L }
        assertTrue(t9.billMovable(card))
        assertEquals(YearMonth.of(2026, 10), t9.accountMonthFor(card))
        assertEquals(600L, data().cardBillSpending(card, YearMonth.of(2026, 10)))   // 10 月帳單：三筆消費 300 + 儲值 300
        val d = data().withBillMonths(card, setOf(9), YearMonth.of(2026, 11))
        assertEquals(YearMonth.of(2026, 11), d.bill(9))
        assertEquals(YearMonth.of(2026, 11), d.txns.first { it.id == 9L }.accountMonthFor(card))   // 明細也跟著到 11 月那一期
        assertEquals(300L, d.cardBillSpending(card, YearMonth.of(2026, 11)))
        assertEquals(300L, d.cardBillSpending(card, YearMonth.of(2026, 10)))
        // 悠遊卡那邊還是看日期
        assertEquals(YearMonth.of(2026, 10), d.txns.first { it.id == 9L }.accountMonthFor(ecard))
        // 存檔再讀回來還在
        assertEquals(YearMonth.of(2026, 11), Codec.decode(Codec.encode(d)).txns.first { it.id == 9L }.billMonthFor(card))
    }

    @Test
    fun backToAutoAndCannotMoveEarlier() {
        val moved = data().withBillMonths(card, setOf(1, 2), YearMonth.of(2026, 12))
        val back = moved.withBillMonths(card, setOf(1), null)
        assertEquals(YearMonth.of(2026, 10), back.bill(1))
        assertEquals(0, back.txns.first { it.id == 1L }.billMonth)
        assertEquals(YearMonth.of(2026, 12), back.bill(2))
        // 選的月份比自動的早或一樣：回到自動
        val same = data().withBillMonths(card, setOf(3), YearMonth.of(2026, 10))
        assertEquals(0, same.txns.first { it.id == 3L }.billMonth)
    }
}
