package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 子分類的顏色一律跟大分類一樣（11/21 使用者回報「宵夜」跟吃喝其他子分類不同色） */
class CategoryColorTest {
    @Test
    fun subcategoryColorFollowsItsParentWhenLoaded() {
        val base = Defaults.create()
        val top = base.categories.first { it.kind == TxType.EXPENSE && it.parentId == null && base.categories.any { k -> k.parentId == it.id } }
        val kid = base.categories.first { it.parentId == top.id }
        val wrong = (top.color + 1) % 10
        // 資料裡宵夜這種子分類存到別的顏色
        val bad = base.copy(categories = base.categories.map { if (it.id == kid.id) it.copy(color = wrong) else it })
        val back = Codec.decode(Codec.encode(bad))
        assertEquals(top.color, back.catMap[kid.id]!!.color)
        // 大分類自己的顏色、其他分類不動
        assertEquals(top.color, back.catMap[top.id]!!.color)
        assertTrue(back.categories.filter { it.parentId == null }.all { c -> c.color == bad.catMap[c.id]!!.color })
        // 本來就一致的資料讀進來完全一樣
        assertEquals(base.categories, Codec.decode(Codec.encode(base)).categories)
    }
}
