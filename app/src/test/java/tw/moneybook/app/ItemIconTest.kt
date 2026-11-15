package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 常用記帳、訂閱自己換的圖示：存檔讀檔、舊資料、用到的圖片不會被清掉 */
class ItemIconTest {
    private val base = Defaults.create()
    private fun tpl(id: Long, icon: String = "") = Template(id, "早餐", TxType.EXPENSE, 65, base.categories.first().id, null, "", emptyList(), icon)
    private fun sub(id: Long, icon: String = "") = Sub(
        id = id, bookId = base.books.first().id, name = "影音平台", amount = 390, categoryId = base.categories.first().id,
        accountId = base.accounts.first().id, days = listOf(5), start = 20000L, doneUntil = 19999L, icon = icon,
    )

    @Test
    fun iconsSurviveSaveAndLoad() {
        val d = base.copy(
            templates = listOf(tpl(9001, "pic:n"), tpl(9002, "🥪"), tpl(9003)),
            subs = listOf(sub(9101, "img:cat_education"), sub(9102)),
        )
        val back = Codec.decode(Codec.encode(d))
        assertEquals(listOf("pic:n", "🥪", ""), back.templates.map { it.icon })
        assertEquals(listOf("img:cat_education", ""), back.subs.map { it.icon })
    }

    @Test
    fun emptyIconIsNotWritten() {
        val root = org.json.JSONObject(Codec.encode(base.copy(templates = listOf(tpl(9001)), subs = listOf(sub(9101)))))
        assertFalse(root.getJSONArray("templates").getJSONObject(0).has("icon"))
        assertFalse(root.getJSONArray("subs").getJSONObject(0).has("icon"))
    }

    @Test
    fun oldDataWithoutIconUsesCategory() {
        val root = org.json.JSONObject(Codec.encode(base.copy(templates = listOf(tpl(9001, "pic:n")), subs = listOf(sub(9101, "pic:n")))))
        root.getJSONArray("templates").getJSONObject(0).remove("icon")
        root.getJSONArray("subs").getJSONObject(0).remove("icon")
        val back = Codec.decode(root.toString())
        assertEquals("", back.templates.single().icon)
        assertEquals("", back.subs.single().icon)
    }

    @Test
    fun picsUsedByTemplatesAndSubsAreKept() {
        val d = base.copy(
            templates = listOf(tpl(9001, "pic:t"), tpl(9002, "🥪")),
            subs = listOf(sub(9101, "pic:s")),
            pics = mapOf(
                "t" to UserPic("x"),
                "s" to UserPic("x", base = "b", layers = listOf(PicLayer(LAYER_ICON, "pic:k"))),
                "k" to UserPic("x"),
                "old" to UserPic("x"),
            ),
        )
        val used = d.usedPicIds()
        assertTrue(used.containsAll(listOf("t", "s", "k")))
        assertFalse("old" in used)
    }
}
