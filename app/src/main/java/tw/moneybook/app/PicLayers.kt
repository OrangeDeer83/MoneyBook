package tw.moneybook.app

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 自己的圖片上的一個貼紙或文字。位置、大小都是「佔圖片邊長的比例」，圖片放大縮小都對得上。
 * - kind：LAYER_ICON（value 是圖示字串：img:…、表情符號、mascot:動物:表情、pic:…）或 LAYER_TEXT（value 是文字）
 * - x、y：中心點（0～1，左上角是 0,0）
 * - size：貼紙是邊長、文字是字的高度
 * - rot：角度（順時針，-180～180）
 * - color、outline：文字的顏色（LAYER_TEXT_COLORS 的第幾個）、要不要白色外框
 */
data class PicLayer(
    val kind: Int,
    val value: String,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = LAYER_ICON_SIZE,
    val rot: Float = 0f,
    val color: Int = 0,
    val outline: Boolean = true,
)

const val LAYER_ICON = 0
const val LAYER_TEXT = 1
/** 新放上去的大小 */
const val LAYER_ICON_SIZE = 0.4f
const val LAYER_TEXT_SIZE = 0.26f
/** 可以縮放的範圍 */
const val LAYER_MIN = 0.06f
const val LAYER_MAX = 1.6f

/** 文字可以選的顏色（畫進圖片裡的固定顏色，不跟著配色變） */
val LAYER_TEXT_COLORS: List<Long> = listOf(
    0xFFC0392B, 0xFFE67E22, 0xFFF1C40F, 0xFF27AE60, 0xFF2E86C1, 0xFF8E44AD, 0xFF4D3B31, 0xFFFFFFFF,
)

/** 吉祥物貼紙的圖示字串：mascot:動物:表情（例如 mascot:cat:HAPPY） */
const val MASCOT_STICKER = "mascot:"

/** 拖動：中心點不會拖出圖片外 */
fun PicLayer.moved(dx: Float, dy: Float): PicLayer = copy(x = (x + dx).coerceIn(0f, 1f), y = (y + dy).coerceIn(0f, 1f))

/** 兩指縮放 */
fun PicLayer.zoomed(z: Float): PicLayer = if (z.isNaN() || z <= 0f) this else copy(size = (size * z).coerceIn(LAYER_MIN, LAYER_MAX))

/** 兩指旋轉（度）：角度一直保持在 -180～180 */
fun PicLayer.rotated(deg: Float): PicLayer = if (deg.isNaN()) this else copy(rot = normDeg(rot + deg))

fun normDeg(d: Float): Float {
    var r = d % 360f
    if (r > 180f) r -= 360f
    if (r <= -180f) r += 360f
    return r
}

/** 放開手指時：角度很接近 0、90、180、-90 度就對齊（差 4 度以內），比較好擺正 */
fun PicLayer.snapped(): PicLayer {
    val k = (rot / 90f).roundToInt() * 90f
    return if (abs(rot - k) <= 4f) copy(rot = normDeg(k)) else this
}

/**
 * 點 (px, py)（比例）有沒有落在這個貼紙、文字上；w、h 是它的寬高（比例）。
 * 先把點轉回貼紙沒旋轉時的座標再比；pad 讓很小的貼紙也點得到。
 */
fun PicLayer.contains(px: Float, py: Float, w: Float, h: Float, pad: Float = 0.03f): Boolean {
    val (lx, ly) = toLocal(px, py)
    return abs(lx) <= w / 2f + pad && abs(ly) <= h / 2f + pad
}

/** 圖片上的點換成貼紙自己的座標（以中心為原點、沒旋轉時的方向） */
fun PicLayer.toLocal(px: Float, py: Float): Pair<Float, Float> {
    val a = Math.toRadians(-rot.toDouble())
    val dx = px - x
    val dy = py - y
    return Pair((dx * cos(a) - dy * sin(a)).toFloat(), (dx * sin(a) + dy * cos(a)).toFloat())
}

/** 貼紙自己的座標換回圖片上的點（給「×」放在旋轉後的左上角） */
fun PicLayer.toImage(lx: Float, ly: Float): Pair<Float, Float> {
    val a = Math.toRadians(rot.toDouble())
    return Pair((x + lx * cos(a) - ly * sin(a)).toFloat(), (y + lx * sin(a) + ly * cos(a)).toFloat())
}

/** 點到的是哪一個（後放的在上面，先找上面的）；都沒點到回傳 -1。extent 給每一個的寬高（比例） */
fun hitLayer(layers: List<PicLayer>, px: Float, py: Float, extent: (PicLayer) -> Pair<Float, Float>): Int {
    for (i in layers.indices.reversed()) {
        val (w, h) = extent(layers[i])
        if (layers[i].contains(px, py, w, h)) return i
    }
    return -1
}

/** 畫面測試、無障礙用的說明：「文字「AB」（位置 50,50，大小 26%，角度 0）」 */
fun PicLayer.describe(): String {
    val what = if (kind == LAYER_TEXT) "文字「$value」" else "貼紙 $value"
    return "$what（位置 ${(x * 100).roundToInt()},${(y * 100).roundToInt()}，大小 ${(size * 100).roundToInt()}%，角度 ${rot.roundToInt()}）"
}

object PicLayerCodec {
    fun toJson(layers: List<PicLayer>): JSONArray = JSONArray().apply {
        layers.forEach { l ->
            put(
                JSONObject().put("k", l.kind).put("v", l.value).put("x", l.x.toDouble()).put("y", l.y.toDouble())
                    .put("s", l.size.toDouble()).put("r", l.rot.toDouble()).put("c", l.color).put("o", l.outline)
            )
        }
    }

    fun fromJson(a: JSONArray?): List<PicLayer> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val v = o.optString("v", "")
            if (v.isEmpty()) return@mapNotNull null
            PicLayer(
                kind = if (o.optInt("k", LAYER_ICON) == LAYER_TEXT) LAYER_TEXT else LAYER_ICON,
                value = v,
                x = o.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                y = o.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                size = o.optDouble("s", LAYER_ICON_SIZE.toDouble()).toFloat().coerceIn(LAYER_MIN, LAYER_MAX),
                rot = normDeg(o.optDouble("r", 0.0).toFloat()),
                color = o.optInt("c", 0).coerceIn(0, LAYER_TEXT_COLORS.size - 1),
                outline = o.optBoolean("o", true),
            )
        }
    }
}

private const val PIC_PREFIX = "pic:"

/**
 * 有用到的自己的圖片：帳本、帳戶、分類的圖示，還有這些圖片上當貼紙用的其他自己的圖片（一層一層找下去）。
 * 沒用到的打開 App 時清掉。
 */
fun AppData.usedPicIds(): Set<String> {
    val used = HashSet<String>()
    val queue = ArrayDeque<String>()
    (accounts.map { it.emoji } + categories.map { it.emoji } + books.map { it.emoji })
        .filter { it.startsWith(PIC_PREFIX) }.forEach { queue.add(it.removePrefix(PIC_PREFIX)) }
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        if (!used.add(id)) continue
        pics[id]?.layers?.forEach { l -> if (l.kind == LAYER_ICON && l.value.startsWith(PIC_PREFIX)) queue.add(l.value.removePrefix(PIC_PREFIX)) }
    }
    return used
}
