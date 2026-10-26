package tw.moneybook.app

import kotlin.math.abs
import kotlin.math.ln

/*
 * 常用記帳小工具的格子怎麼排（單位都是 dp）：
 * 小工具外框留 5.5、每格四周留 2.5 → 格子離邊緣 8、格子之間隔 5（按鈕圓角＝外框圓角－8 才會同心）。
 */
const val GRID_OUTER_PAD = 5.5f
const val GRID_CELL_PAD = 2.5f
/**
 * 字的大小（sp）與跟著變的格子大小（dp）：name 名稱、amount 金額、icon／iconTall 橫的／直的格子的圖示框、
 * minW、minH 一格最小寬高、tall 格子高到這樣就改成圖示在上、名稱在下、plus 是 ＋ 的大小
 */
class TplStyle(
    val name: Int, val amount: Int, val icon: Int, val iconTall: Int,
    val minW: Float, val minH: Float, val tall: Float, val plus: Int,
) {
    companion object {
        val SMALL = TplStyle(14, 11, 22, 30, 110f, 52f, 84f, 26)
        val MEDIUM = TplStyle(17, 13, 26, 36, 135f, 62f, 100f, 30)
        val LARGE = TplStyle(20, 15, 30, 42, 160f, 72f, 116f, 34)
    }
}
/** 想要的格子形狀（寬÷高）：比較扁的長方形 */
private const val GRID_RATIO = 1.4f

/** rows：每一排有幾格（最後一格是 ＋）；shown：放了幾個常用記帳；cellW、cellH：格子看得到的大小 */
class TplGrid(val rows: List<Int>, val shown: Int, val cellW: Float, val cellH: Float)

/**
 * n 個常用記帳＋1 個 ＋，放進 w×h 的小工具：
 * 先求放得下最多個（放不下的照常用記帳的順序放前面幾個）；格子比放得下的少時，挑格子最接近 GRID_RATIO 的排數。
 * 每排平均分，最後一排可能少一格（格子變寬，不留空格）
 */
fun tplGrid(n: Int, w: Float, h: Float, st: TplStyle = TplStyle.SMALL): TplGrid {
    val availW = w - 2 * GRID_OUTER_PAD
    val availH = h - 2 * GRID_OUTER_PAD
    val slotW = st.minW + 2 * GRID_CELL_PAD
    val slotH = st.minH + 2 * GRID_CELL_PAD
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
