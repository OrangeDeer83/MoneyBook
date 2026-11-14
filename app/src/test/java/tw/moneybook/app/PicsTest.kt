package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.moneybook.app.ui.brush
import tw.moneybook.app.ui.floodClear

/** 自己的圖片：存檔、去背（點背景去掉相近顏色、擦掉、補回） */
class PicsTest {
    private val white = 0xFFFFFFFF.toInt()
    private val offWhite = 0xFFF4F4F2.toInt()
    private val red = 0xFFE04030.toInt()

    /** 7×7：外圈白（有一點點不同的白），中間 3×3 紅，紅的裡面中心又是白（被紅包住，點外面的白不會去掉它） */
    private fun img(): IntArray = IntArray(49) { i ->
        val x = i % 7
        val y = i / 7
        when {
            x == 3 && y == 3 -> white
            x in 2..4 && y in 2..4 -> red
            (x + y) % 2 == 0 -> white
            else -> offWhite
        }
    }

    @Test
    fun clickBackgroundClearsConnectedSimilarColorsOnly() {
        val px = img()
        val keep = BooleanArray(49) { true }
        assertTrue(floodClear(px, keep, 7, 7, 0, 0, 30))
        assertEquals(9, keep.count { it })                     // 剩中間 3×3（紅＋被包住的白）
        assertTrue(keep[3 * 7 + 3])
        assertFalse(keep[0])
        // 已經去掉的地方再點一次不會變
        assertFalse(floodClear(px, keep, 7, 7, 0, 0, 30))
        // 範圍外的點不理
        assertFalse(floodClear(px, keep, 7, 7, -1, 9, 30))
        // 相近程度很低：只去掉跟點的那一格一模一樣的（白），隔壁的「有一點點不同的白」留著
        val strict = BooleanArray(49) { true }
        floodClear(px, strict, 7, 7, 0, 0, 0)
        assertEquals(1, strict.count { !it })                   // 四周都是不同的白，連不出去
    }

    @Test
    fun brushErasesAndRestoresACircle() {
        val keep = BooleanArray(100) { true }
        brush(keep, 10, 10, 5f, 5f, 2f, false)
        assertFalse(keep[5 * 10 + 5])
        assertFalse(keep[5 * 10 + 7])
        assertTrue(keep[5 * 10 + 8])
        assertTrue(keep[0])
        brush(keep, 10, 10, 5f, 5f, 1f, true)
        assertTrue(keep[5 * 10 + 5])
        assertFalse(keep[5 * 10 + 7])
        // 碰到邊緣不會出錯
        brush(keep, 10, 10, 0f, 0f, 3f, false)
        assertFalse(keep[0])
    }

    @Test
    fun picsSurviveSaveAndOldDataHasNone() {
        val base = Defaults.create()
        val d = base.copy(pics = mapOf("p1" to UserPic("iVBORw0KGgo=", "/9j/4AAQ"), "p2" to UserPic("iVBORw0KGgo=")))
        val back = Codec.decode(Codec.encode(d))
        assertEquals(d.pics, back.pics)
        val root = org.json.JSONObject(Codec.encode(d))
        root.remove("pics")
        assertTrue(Codec.decode(root.toString()).pics.isEmpty())
    }
}
