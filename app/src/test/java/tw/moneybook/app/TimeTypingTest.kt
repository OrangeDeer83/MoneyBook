package tw.moneybook.app

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test
import tw.moneybook.app.ui.typedDigits

/** 鍵盤輸入時間：剛選取的格子，第一個數字要取代原本的數字，即使輸入法沒有套用選取範圍 */
class TimeTypingTest {
    private val old = TextFieldValue("06", TextRange(0, 2))

    @Test
    fun selectionHonoredReplacesOldDigits() {
        assertEquals("0", typedDigits(old, TextFieldValue("0", TextRange(1)), true))
    }

    @Test
    fun appendedDigitReplacesWhenSelectionWasNotApplied() {
        // 輸入法把 0 接在 06 後面：只取新打的那個數字
        assertEquals("0", typedDigits(old, TextFieldValue("060", TextRange(3)), true))
        // 接在前面
        assertEquals("0", typedDigits(old, TextFieldValue("006", TextRange(1)), true))
    }

    @Test
    fun afterTheFirstDigitTypingJustContinues() {
        assertEquals("09", typedDigits(TextFieldValue("0", TextRange(1)), TextFieldValue("09", TextRange(2)), false))
        assertEquals("12", typedDigits(TextFieldValue("12", TextRange(2)), TextFieldValue("123", TextRange(3)), false))
    }

    @Test
    fun nonDigitsAreDropped() {
        assertEquals("1", typedDigits(old, TextFieldValue("06a1", TextRange(4)), true))
    }
}
