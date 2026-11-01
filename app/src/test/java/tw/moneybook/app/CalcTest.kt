package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 計算機鍵盤的乘除：先乘除後加減、四捨五入、台幣可以打小數點、除以 0 */
class CalcTest {
    private fun typed(vararg keys: String, dec: Int = 0): String {
        var e = ""
        for (k in keys) e = Calc.press(e, k, dec)
        return e
    }

    @Test
    fun multiplyAndDivideBeforeAddAndSubtract() {
        assertEquals(3650L, Calc.eval("1200*3+50"))
        assertEquals(3650L, Calc.eval("50+1200*3"))
        assertEquals(400L, Calc.eval("1000-1200/2"))
        assertEquals(60L, Calc.eval("100/5*3"))              // 同一級照順序：100 ÷ 5 × 3
        assertEquals(1200L, Calc.eval("1200*"))              // 結尾多按的符號不算
    }

    @Test
    fun roundsToWholeDollarsAndCurrencyDecimals() {
        assertEquals(333L, Calc.eval("1000/3"))
        assertEquals(667L, Calc.eval("2000/3"))
        assertEquals(1320L, Calc.eval("1200*1.1"))
        assertEquals(1L, Calc.eval("1/3+1/3+1/3"))           // 最後才四捨五入
        assertEquals(2L, Calc.eval("1/3*4.5"))               // 剛好 1.5：用分數算，不會因為 1/3 除不盡變成 1
        assertEquals(10L, Calc.eval("10/3*3"))
        assertEquals(333L, Calc.evalMinor("10/3", 2))        // 美元 3.33
        assertEquals(-333L, Calc.evalSigned("-1000/3"))
        assertEquals(0L, Calc.eval("5-10*2"))                // 不會變負的
        assertEquals(9_999_999_999L, Calc.eval("9999999999*9999999999"))   // 很大也不會溢位
    }

    @Test
    fun divideByZeroIsFlagged() {
        assertTrue(Calc.divZero("100/0"))
        assertTrue(Calc.divZero("100/0.0+5"))
        assertFalse(Calc.divZero("100/0.5"))
        assertFalse(Calc.divZero("100/"))
        assertFalse(Calc.divZero(""))
        assertEquals(0L, Calc.eval("100/0"))
        assertNull(Calc.evalSigned("100/0"))
        assertEquals(200L, Calc.eval("100/0.5"))
    }

    @Test
    fun tooBigUsesTheUnclampedValue() {
        assertTrue(Calc.tooBig("9999999999*5", 9))           // 外幣會被夾成上限，還是要抓得到
        assertTrue(Calc.tooBig("1000000000", 9))
        assertFalse(Calc.tooBig("999999999.99", 9, 2))
        assertTrue(Calc.tooBig("999999999.6", 9))            // 台幣四捨五入進位成 10 位
        assertTrue(Calc.tooBig("999999999.995", 9, 2))       // 外幣進位成 1,000,000,000.00
        assertFalse(Calc.tooBig("0-99999999999", 9))         // 負的會變成 0
        assertFalse(Calc.tooBig("100/0", 9))
    }

    @Test
    fun keysForMultiplyDivideAndDecimals() {
        assertEquals("1200*1.1", typed("1", "2", "00", "*", "1", ".", "1"))
        assertEquals("12/", typed("1", "2", "*", "/"))       // 連按符號換成後按的
        assertEquals("", typed("*"))                          // 一開頭不能按乘除
        assertEquals("1.05", typed("1", ".", "0", "5", "9"))  // 台幣小數最多 2 位
        assertEquals("0.5", typed(".", "5"))
        assertEquals("1.234", typed("1", ".", "2", "3", "4", dec = 3))
        assertEquals("-", Calc.pressSigned("-", "*"))         // 負號後面不能接乘除
        assertTrue(Calc.hasOp("12*3"))
        assertTrue(Calc.pending("1200.5"))                    // 台幣打小數：要先按 = 看到 $1,201
        assertFalse(Calc.pending("1200.5", 2))                // 美元 2 位小數不用
        assertFalse(Calc.pending("1200."))
        assertTrue(Calc.pending("12.345", 2))
        assertEquals("1,200 × 3 ÷ 2 − 50", Calc.pretty("1200*3/2-50"))
    }

    @Test
    fun pasteFromClipboard() {
        assertEquals("1500", Calc.clipNumber("NT$1,500"))
        assertEquals("1500", Calc.clipNumber("1500 元"))
        assertEquals("12.35", Calc.clipNumber("US$12.345", 2))
        assertEquals("13", Calc.clipNumber("12.5"))          // 台幣四捨五入到元
        assertEquals("12.5", Calc.clipNumber("12.50", 2))
        assertNull(Calc.clipNumber("午餐"))
        assertNull(Calc.clipNumber(""))
        assertNull(Calc.clipNumber("12345678901"))           // 太大
        assertNull(Calc.clipNumber("0.001"))                 // 四捨五入是 0：不顯示貼上
        assertEquals("1500", Calc.paste("", "1500"))
        assertEquals("200+1500", Calc.paste("200+", "1500"))
        assertEquals("200+1500", Calc.paste("200+3", "1500"))
        assertEquals("1500", Calc.paste("12.5", "1500"))
    }
}
