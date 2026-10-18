package tw.moneybook.app

import java.time.LocalDate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 千分位、更新餘額可以輸入負數的計算機、某個時間點的帳戶餘額 */
class SignedInputTest {
    @Test
    fun groupDigitsAddsCommasToIntegerPartsOnly() {
        assertEquals("1,354,869", groupDigits("1354869").first)
        assertEquals("-1,354,869", groupDigits("-1354869").first)
        assertEquals("1,200+350", groupDigits("1200+350").first)
        assertEquals("1,025.5", groupDigits("1025.5").first)
        assertEquals("0.12345", groupDigits("0.12345").first)   // 小數點後面不加
        assertEquals("999", groupDigits("999").first)
        assertEquals("", groupDigits("").first)
        // 原本每個位置在新字串的位置："1234" → "1,234"
        assertArrayEquals(intArrayOf(0, 2, 3, 4, 5), groupDigits("1234").second)
    }

    @Test
    fun prettyExpressionHasCommas() {
        assertEquals("1,200 + 350", Calc.pretty("1200+350"))
        assertEquals("12,000 − 3,000", Calc.pretty("12000-3000"))
    }

    @Test
    fun firstMinusMakesNegative() {
        var e = ""
        for (k in listOf("-", "3", "0", "0", "0")) e = Calc.pressSigned(e, k)
        assertEquals("-3000", e)
        assertEquals(-3000L, Calc.evalSigned(e))
        assertEquals(-2000L, Calc.evalSigned(Calc.pressSigned(Calc.pressSigned(Calc.pressSigned(e, "+"), "1"), "000")))
        // 只有負號再按 + 就清掉；還沒輸入數字是 null
        assertEquals("", Calc.pressSigned("-", "+"))
        assertNull(Calc.evalSigned("-"))
        assertNull(Calc.evalSigned(""))
        assertEquals(0L, Calc.evalSigned("0"))
        // 外幣可以有小數：-12.5 美元＝-1250 分
        var f = ""
        for (k in listOf("-", "1", "2", ".", "5")) f = Calc.pressSigned(f, k, 2)
        assertEquals(-1250L, Calc.evalSigned(f, 2))
    }

    @Test
    fun balanceAtACertainTime() {
        val d0 = Defaults.create()
        val acc = d0.accounts.first()
        val day = LocalDate.of(2026, 4, 23).toEpochDay()
        fun t(id: Long, type: TxType, amount: Long, day: Long, time: Int) =
            Txn(id, 1L, type, amount, if (type == TxType.EXPENSE) 1L else null, acc.id, null, day, "", emptyList(), time = time)
        val d = d0.copy(txns = sortTxns(listOf(
            t(1, TxType.INCOME, 1000, day - 1, -1),
            t(2, TxType.EXPENSE, 100, day, -1),      // 沒有時間：算在當天最前面
            t(3, TxType.EXPENSE, 200, day, 9 * 60),
            t(4, TxType.EXPENSE, 300, day, 18 * 60),
        )), nextId = 100)
        val start = acc.initial
        assertEquals(start + 1000 - 100, d.balanceAt(acc.id, day, 8 * 60))
        assertEquals(start + 1000 - 100 - 200, d.balanceAt(acc.id, day, 12 * 60))
        assertEquals(d.balances()[acc.id], d.balanceAt(acc.id, day, 23 * 60 + 59))
    }
}
