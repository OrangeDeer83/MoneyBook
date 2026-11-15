package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自己的圖片加貼紙、文字：拖動、縮放、旋轉、點選、存檔、清掉沒用到的圖片 */
class PicLayersTest {
    private val eps = 1e-4f
    private val heart = PicLayer(LAYER_ICON, "img:pick_heart", 0.5f, 0.5f, 0.4f)

    @Test
    fun moveStaysInsidePicture() {
        val m = heart.moved(0.2f, -0.1f)
        assertEquals(0.7f, m.x, eps)
        assertEquals(0.4f, m.y, eps)
        val far = heart.moved(5f, -5f)
        assertEquals(1f, far.x, eps)
        assertEquals(0f, far.y, eps)
    }

    @Test
    fun zoomIsLimited() {
        assertEquals(0.8f, heart.zoomed(2f).size, eps)
        assertEquals(LAYER_MAX, heart.zoomed(100f).size, eps)
        assertEquals(LAYER_MIN, heart.zoomed(0.001f).size, eps)
        // 手指資料怪怪的（0、NaN）時不變
        assertEquals(heart, heart.zoomed(0f))
        assertEquals(heart, heart.zoomed(Float.NaN))
        // 連續縮放：放大再縮回來是原本的大小
        assertEquals(0.4f, heart.zoomed(1.5f).zoomed(1f / 1.5f).size, eps)
    }

    @Test
    fun rotationWrapsAndSnaps() {
        assertEquals(30f, heart.rotated(30f).rot, eps)
        assertEquals(-170f, heart.rotated(190f).rot, eps)
        assertEquals(180f, heart.rotated(180f).rot, eps)
        assertEquals(180f, heart.rotated(-180f).rot, eps)
        assertEquals(10f, heart.rotated(370f).rot, eps)
        assertEquals(heart, heart.rotated(Float.NaN))
        // 放開時差 4 度以內對齊 0、90、180、-90
        assertEquals(0f, heart.rotated(3f).snapped().rot, eps)
        assertEquals(90f, heart.rotated(87f).snapped().rot, eps)
        assertEquals(-90f, heart.rotated(-93.5f).snapped().rot, eps)
        assertEquals(180f, heart.rotated(-177f).snapped().rot, eps)
        assertEquals(20f, heart.rotated(20f).snapped().rot, eps)
    }

    @Test
    fun hitTestFollowsRotation() {
        // 一個橫長的文字：寬 0.6、高 0.2，放在中間
        val text = PicLayer(LAYER_TEXT, "AB", 0.5f, 0.5f, 0.2f)
        val ext = { _: PicLayer -> Pair(0.6f, 0.2f) }
        assertTrue(text.contains(0.75f, 0.5f, 0.6f, 0.2f, pad = 0f))
        assertFalse(text.contains(0.5f, 0.75f, 0.6f, 0.2f, pad = 0f))
        // 轉 90 度後變成直的：上下點得到、左右點不到
        val up = text.rotated(90f)
        assertTrue(up.contains(0.5f, 0.75f, 0.6f, 0.2f, pad = 0f))
        assertFalse(up.contains(0.75f, 0.5f, 0.6f, 0.2f, pad = 0f))
        // 疊在一起時點到上面那個（後放的）
        val layers = listOf(heart, text)
        assertEquals(1, hitLayer(layers, 0.5f, 0.5f, ext))
        assertEquals(0, hitLayer(layers, 0.5f, 0.65f) { l -> if (l.kind == LAYER_TEXT) Pair(0.6f, 0.2f) else Pair(0.4f, 0.4f) })
        assertEquals(-1, hitLayer(layers, 0.02f, 0.02f) { l -> if (l.kind == LAYER_TEXT) Pair(0.6f, 0.2f) else Pair(0.4f, 0.4f) })
    }

    @Test
    fun cornerFollowsRotation() {
        val l = PicLayer(LAYER_ICON, "x", 0.5f, 0.5f, 0.4f)
        val (ax, ay) = l.toImage(-0.2f, -0.2f)
        assertEquals(0.3f, ax, eps)
        assertEquals(0.3f, ay, eps)
        // 轉 90 度（順時針）：左上角跑到右上
        val (bx, by) = l.rotated(90f).toImage(-0.2f, -0.2f)
        assertEquals(0.7f, bx, eps)
        assertEquals(0.3f, by, eps)
        // 換過去再換回來是同一點
        val r = l.copy(x = 0.3f, y = 0.6f).rotated(37f)
        val (lx, ly) = r.toLocal(0.42f, 0.55f)
        val (px, py) = r.toImage(lx, ly)
        assertEquals(0.42f, px, eps)
        assertEquals(0.55f, py, eps)
    }

    @Test
    fun layersSurviveSaveAndOldDataStillLoads() {
        val layers = listOf(
            heart.rotated(-35f).zoomed(1.3f),
            PicLayer(LAYER_TEXT, "玉山", 0.62f, 0.7f, 0.26f, 12f, 3, false),
            PicLayer(LAYER_ICON, "mascot:cat:HAPPY", 0.2f, 0.8f, 0.3f),
        )
        val base = Defaults.create()
        val d = base.copy(pics = mapOf("p1" to UserPic("iVBOR", "/9j/src", "/9j/base", layers), "p2" to UserPic("iVBOR")))
        val back = Codec.decode(Codec.encode(d))
        assertEquals(d.pics["p2"], back.pics["p2"])
        val p1 = back.pics["p1"]!!
        assertEquals("/9j/base", p1.base)
        assertEquals(3, p1.layers.size)
        layers.zip(p1.layers).forEach { (a, b) ->
            assertEquals(a.kind, b.kind); assertEquals(a.value, b.value)
            assertEquals(a.x, b.x, eps); assertEquals(a.y, b.y, eps); assertEquals(a.size, b.size, eps); assertEquals(a.rot, b.rot, eps)
            assertEquals(a.color, b.color); assertEquals(a.outline, b.outline)
        }
        // 舊版的圖片（沒有 base、layers）照樣讀得到
        val root = org.json.JSONObject(Codec.encode(d))
        root.getJSONObject("pics").getJSONObject("p1").apply { remove("base"); remove("layers") }
        val old = Codec.decode(root.toString()).pics["p1"]!!
        assertEquals("", old.base)
        assertTrue(old.layers.isEmpty())
        // 怪資料：超出範圍的值拉回來、沒有內容的略過
        val bad = PicLayerCodec.fromJson(org.json.JSONArray("""[{"k":1,"v":"A","x":3,"y":-1,"s":99,"r":540,"c":42},{"k":0,"v":""},{}]"""))
        assertEquals(1, bad.size)
        assertEquals(1f, bad[0].x, eps); assertEquals(0f, bad[0].y, eps); assertEquals(LAYER_MAX, bad[0].size, eps)
        assertEquals(180f, bad[0].rot, eps); assertEquals(LAYER_TEXT_COLORS.size - 1, bad[0].color)
    }

    @Test
    fun picsUsedAsStickersAreKept() {
        val base = Defaults.create()
        val acc = base.accounts.first().copy(emoji = "pic:a")
        val d = base.copy(
            accounts = listOf(acc) + base.accounts.drop(1),
            pics = mapOf(
                "a" to UserPic("x", base = "b", layers = listOf(PicLayer(LAYER_ICON, "pic:b"))),
                "b" to UserPic("x", base = "b", layers = listOf(PicLayer(LAYER_ICON, "pic:c"), PicLayer(LAYER_TEXT, "pic:z"))),
                "c" to UserPic("x"),
                "z" to UserPic("x"),
                "old" to UserPic("x"),
            ),
        )
        // a 用在帳戶、b 是 a 的貼紙、c 是 b 的貼紙；z 只是文字內容剛好長得像，不算；old 沒人用
        assertEquals(setOf("a", "b", "c"), d.usedPicIds())
    }
}
