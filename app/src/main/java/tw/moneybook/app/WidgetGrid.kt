package tw.moneybook.app

import kotlin.math.abs
import kotlin.math.ln

/*
 * 常用記帳小工具的格子怎麼排（單位都是 dp）：
 * 小工具外框留 5.5、每格四周留 2.5 → 格子離邊緣 8、格子之間隔 5（按鈕圓角＝外框圓角－8 才會同心）。
 * 字的大小是使用者試過三種後選的「中」（10/26）；格子只放圖示和名稱，不放金額。
 */
const val GRID_OUTER_PAD = 5.5f
const val GRID_CELL_PAD = 2.5f
/** 一格至少要這麼寬、這麼高 */
const val GRID_MIN_W = 135f
const val GRID_MIN_H = 62f
/** 格子高到這樣就改成圖示在上、名稱在下 */
const val GRID_TALL = 100f
/** 名稱的字（sp）、圖示框（橫的格子、直的格子）、＋ 的大小 */
const val TPL_NAME_SP = 17
const val TPL_ICON = 26
const val TPL_ICON_TALL = 36
const val TPL_PLUS_SP = 30
/** 想要的格子形狀（寬÷高）：比較扁的長方形 */
private const val GRID_RATIO = 1.4f

/** rows：每一排有幾格（最後一格是 ＋）；shown：放了幾個常用記帳；cellW、cellH：格子看得到的大小 */
class TplGrid(val rows: List<Int>, val shown: Int, val cellW: Float, val cellH: Float)

/**
 * n 個常用記帳＋1 個 ＋，放進 w×h 的小工具：
 * 先求放得下最多個（放不下的照常用記帳的順序放前面幾個）；格子比放得下的少時，挑格子最接近 GRID_RATIO 的排數。
 * 每排平均分，最後一排可能少一格（格子變寬，不留空格）
 */
fun tplGrid(n: Int, w: Float, h: Float): TplGrid {
    val availW = w - 2 * GRID_OUTER_PAD
    val availH = h - 2 * GRID_OUTER_PAD
    val slotW = GRID_MIN_W + 2 * GRID_CELL_PAD
    val slotH = GRID_MIN_H + 2 * GRID_CELL_PAD
    val maxCols = (availW / slotW).toInt().coerceAtLeast(1)
    val maxRows = (availH / slotH).toInt().coerceAtLeast(1)
    val total = minOf(n, maxCols * maxRows - 1).coerceAtLeast(0) + 1
    var best: TplGrid? = null
    var bestScore = Float.MAX_VALUE
    for (r in 1..maxRows) {
        val per = (total + r - 1) / r
        if (per > maxCols) continue
        val used = (total + per - 1) / per
        val cw = availW / per - 2 * GRID_CELL_PAD
        val ch = availH / used - 2 * GRID_CELL_PAD
        val score = abs(ln((cw / ch) / GRID_RATIO))
        if (score < bestScore - 1e-4f) {
            bestScore = score
            best = TplGrid(List(used) { i -> minOf(per, total - i * per) }, total - 1, cw, ch)
        }
    }
    return best ?: TplGrid(listOf(total), total - 1, availW / total - 2 * GRID_CELL_PAD, availH - 2 * GRID_CELL_PAD)
}

/** 估一個字有多寬（以字的大小為 1）：中文、全形 1，英數半形約 0.55 */
private fun charEm(c: Char): Float = if (c.code < 0x2E80) 0.55f else 1f

private fun widthEm(s: String): Float = s.sumOf { charEm(it).toDouble() }.toFloat()

/**
 * 名稱一行放不下、要兩行時，兩行平均分（例如「禮拜三羽毛球」→「禮拜三／羽毛球」）：
 * 挑兩行裡比較長的那行最短的斷法，有空白就優先斷在空白（空白拿掉），不在英文字或數字中間斷。
 * maxEm＝一行放得下幾個字寬（以字的大小為 1）；放得下就不斷，回傳原本的名稱
 */
fun splitName(name: String, maxEm: Float): String {
    val s = name.trim()
    if (s.length < 2 || widthEm(s) <= maxEm) return s
    var best = s
    var bestScore = Float.MAX_VALUE
    for (i in 1 until s.length) {
        val a = s.substring(0, i).trimEnd()
        val b = s.substring(i).trimStart()
        if (a.isEmpty() || b.isEmpty()) continue
        val atSpace = s[i - 1] == ' ' || s[i] == ' '
        val inWord = s[i - 1].isLetterOrDigit() && s[i].isLetterOrDigit() && s[i - 1].code < 0x2E80 && s[i].code < 0x2E80
        val score = maxOf(widthEm(a), widthEm(b)) - (if (atSpace) 0.6f else 0f) + (if (inWord) 3f else 0f)
        if (score < bestScore) {
            bestScore = score
            best = "$a\n$b"
        }
    }
    return best
}
