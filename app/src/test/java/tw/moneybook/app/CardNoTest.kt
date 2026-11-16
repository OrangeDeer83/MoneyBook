package tw.moneybook.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 信用卡的卡號末四碼：存檔讀檔、舊資料、同一組只能在一張卡 */
class CardNoTest {
    private val base = Defaults.create()
    private fun card(id: Long, name: String, vararg nos: CardNo) =
        Account(id, name, "img:acc_card", AccountType.CARD, 0L, id.toInt(), cardNos = nos.toList())

    @Test
    fun cardNosSurviveSaveAndLoad() {
        val d = base.copy(accounts = base.accounts + card(9001, "CUBE 卡", CardNo("1234"), CardNo("5566", "手機", 20406L)) + card(9002, "世界卡"))
        val back = Codec.decode(Codec.encode(d))
        assertEquals(listOf(CardNo("1234"), CardNo("5566", "手機", 20406L)), back.accounts.first { it.id == 9001L }.cardNos)
        assertTrue(back.accounts.first { it.id == 9002L }.cardNos.isEmpty())
        // 沒有末四碼的帳戶不寫這個欄位
        val root = org.json.JSONObject(Codec.encode(d))
        val accs = root.getJSONArray("accounts")
        val w = (0 until accs.length()).map { accs.getJSONObject(it) }.first { it.getLong("id") == 9002L }
        assertFalse(w.has("cardNos"))
    }

    @Test
    fun oldDataAndBadNumbers() {
        val root = org.json.JSONObject(Codec.encode(base.copy(accounts = base.accounts + card(9001, "CUBE 卡", CardNo("1234")))))
        val accs = root.getJSONArray("accounts")
        val o = (0 until accs.length()).map { accs.getJSONObject(it) }.first { it.getLong("id") == 9001L }
        o.remove("cardNos")
        assertTrue(Codec.decode(root.toString()).accounts.first { it.id == 9001L }.cardNos.isEmpty())
        // 壞掉的、重複的丟掉
        val arr = org.json.JSONArray("""[{"d":"12a4"},{"d":"123"},{"d":"7788","note":"舊卡"},{"d":"7788"}]""")
        assertEquals(listOf(CardNo("7788", "舊卡")), CardNoCodec.fromJson(arr))
        assertTrue(validCardNo("0012"))
        assertFalse(validCardNo("12345"))
    }

    @Test
    fun numberMovesToTheCardSavedLast() {
        val d = base.copy(accounts = base.accounts + card(9001, "CUBE 卡", CardNo("1234"), CardNo("5566")) + card(9002, "世界卡"), nextId = 9100)
        assertEquals(9001L, d.cardWithNo("1234")?.id)
        assertNull(d.cardWithNo("1234", exceptId = 9001))
        // 世界卡加了 1234：從 CUBE 卡移過來，5566 留著
        val w = d.accounts.first { it.id == 9002L }
        val after = saveAccountTo(d, 9002, w.copy(cardNos = listOf(CardNo("1234", "實體卡"))))
        assertEquals(listOf("5566"), after.accounts.first { it.id == 9001L }.cardNos.map { it.digits })
        assertEquals(listOf(CardNo("1234", "實體卡")), after.accounts.first { it.id == 9002L }.cardNos)
        // 新增一張卡也一樣
        val added = saveAccountTo(after, null, card(0, "Pi 卡", CardNo("5566")))
        assertTrue(added.accounts.first { it.id == 9001L }.cardNos.isEmpty())
        assertEquals(9100L, added.cardWithNo("5566")?.id)
    }
}
