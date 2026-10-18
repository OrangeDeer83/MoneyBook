package tw.moneybook.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import tw.moneybook.app.groupDigits

/**
 * 金額輸入框的千分位：打字時自動顯示逗號（12000 → 12,000），存的還是沒有逗號的數字；
 * 小數點後面不加（1025.5 不會變），游標位置會跟著對齊。
 */
object ThousandsVisual : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val src = text.text
        val (out, map) = groupDigits(src)
        // 新字串的每個位置對回原本的位置：落在逗號上的算在它前面那個數字後面
        val back = IntArray(out.length + 1)
        var k = 0
        for (t in 0..out.length) {
            while (k < src.length && map[k + 1] <= t) k++
            back[t] = k
        }
        return TransformedText(
            AnnotatedString(out),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = map[offset.coerceIn(0, src.length)]
                override fun transformedToOriginal(offset: Int): Int = back[offset.coerceIn(0, out.length)]
            },
        )
    }
}
