package tw.moneybook.app

/**
 * 千分位：把字串裡每段數字的整數部分每三位加一個逗號（小數點後面、運算符號、負號都不動），
 * 例如 "-1354869.5+1200" → "-1,354,869.5+1,200"。
 * 第二個值是原本每個位置（0～長度）在新字串裡的位置，輸入框游標對位置用。
 */
fun groupDigits(s: String): Pair<String, IntArray> {
    val out = StringBuilder()
    val map = IntArray(s.length + 1)
    var i = 0
    while (i < s.length) {
        if (!s[i].isDigit()) {
            map[i] = out.length
            out.append(s[i])
            i++
            continue
        }
        var j = i
        while (j < s.length && s[j].isDigit()) j++
        // 小數點後面的數字不加逗號
        val fraction = i > 0 && s[i - 1] == '.'
        for (k in i until j) {
            map[k] = out.length
            out.append(s[k])
            val left = j - k - 1
            if (!fraction && left > 0 && left % 3 == 0) out.append(',')
        }
        i = j
    }
    map[s.length] = out.length
    return out.toString() to map
}
