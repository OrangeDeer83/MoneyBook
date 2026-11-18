package tw.moneybook.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 店家欄位：存檔、CSV、舊資料搬移、照上次的記法帶 */
class ShopTest {
    private val base = Defaults.create()
    private val cats = base.categories.filter { it.kind == TxType.EXPENSE && it.parentId == null }
    private val food = cats[0].id
    private val shopCat = cats[2].id
    private val cube = Account(9001, "CUBE 卡", "img:acc_card", AccountType.CARD, 0L, 1)
    private val book = base.books.first().id
    private var nid = 100L
    private fun t(cat: Long?, note: String, amount: Long = 45, day: Long = 20400, shop: String = "", bank: String = "", tags: List<String> = emptyList()) =
        Txn(nid++, book, TxType.EXPENSE, amount, cat, 9001, null, day, note, tags, shop = shop, bankShop = bank)
    private fun data(vararg txns: Txn) = base.copy(accounts = base.accounts + cube, txns = sortTxns(txns.toList()))
    private fun pend(amount: Long = 45, merchant: String = "統一超商", day: Long = 20406) =
        CardPend(1, listOf(NotiSrc(1, "cathay", "國泰", "", "8526")), amount, "", merchant, true, "OP錢包", day, 551, 0L)

    @Test
    fun savedAndExported() {
        val d = data(t(food, "雙蔬鮪魚飯糰", shop = "7-11", bank = "統一超商")).copy(
            templates = listOf(Template(5, "早餐", TxType.EXPENSE, 45, food, 9001, "雙蔬鮪魚飯糰", emptyList(), shop = "7-11")),
            prefs = base.prefs.copy(shopOpen = false),
        )
        val back = Codec.decode(Codec.encode(d))
        assertEquals("7-11", back.txns[0].shop)
        assertEquals("統一超商", back.txns[0].bankShop)
        assertEquals("雙蔬鮪魚飯糰", back.txns[0].note)
        assertEquals("7-11", back.templates[0].shop)
        assertFalse(back.prefs.shopOpen)
        assertTrue(Codec.decode(Codec.encode(base)).prefs.shopOpen)
        // CSV：店家一欄，匯入讀得回來
        val csv = String(CsvIO.export(d), Charsets.UTF_8)
        assertTrue(csv.lines().first().contains("報銷金額,店家,備註"))
        val (imp, n) = CsvIO.import(Defaults.create(), csv)
        assertEquals(1, n)
        assertEquals("7-11", imp.txns[0].shop)
        assertEquals("雙蔬鮪魚飯糰", imp.txns[0].note)
    }

    @Test
    fun oldCardRecordsMoveToShop() {
        val tag = listOf(CARD_NOTI_TAG)
        val d = data(
            t(food, "統一超商（OP錢包）", tags = tag),
            t(food, "全聯福利中心", tags = tag),
            t(food, "OP錢包", tags = tag),
            t(food, "全家\n買了咖啡", tags = tag),
            t(food, "早餐店"),
        )
        // 舊版存的檔沒有 shopV
        val old = JSONObject(Codec.encode(d)).apply { remove("shopV") }.toString()
        val back = Codec.decode(old).txns.sortedBy { it.id }
        assertEquals(listOf("統一超商", "全聯福利中心", "", "", ""), back.map { it.shop })
        assertEquals(listOf("統一超商", "全聯福利中心", "", "", ""), back.map { it.bankShop })
        assertEquals(listOf("", "", "OP錢包", "全家\n買了咖啡", "早餐店"), back.map { it.note })
        // 新版存的檔不再搬（店家清掉了就是清掉了）
        val cleared = data(t(food, "統一超商", tags = tag))
        assertEquals("", Codec.decode(Codec.encode(cleared)).txns[0].shop)
    }

    @Test
    fun followsLastWay() {
        // 11/17 自己記：店家 7-11、備註飯糰，確認「已經有一筆」後補上銀行寫的
        val mine = t(food, "雙蔬鮪魚飯糰", day = 20405, shop = "7-11").linkBankShop("統一超商")
        assertEquals("統一超商", mine.bankShop)
        assertEquals("7-11", mine.shop)
        // 店家空著時也填上
        assertEquals("統一超商", t(food, "飯糰").linkBankShop("統一超商").shop)
        val d = data(mine, t(shopCat, "衛生紙", amount = 120, day = 20300, shop = "全聯", bank = "全聯福利中心"))
        // 同一家店、金額一樣：店家、備註、分類照上次
        val ps = d.pendShop(pend())
        assertEquals("7-11", ps.shop)
        assertEquals("統一超商", ps.bankShop)
        assertEquals("雙蔬鮪魚飯糰", ps.note)
        assertEquals(mine.id, ps.memo?.id)
        assertEquals("7-11・雙蔬鮪魚飯糰", d.pendName(pend()))
        assertEquals(food, d.pendCat(pend(), 9001))
        val tx = d.pendTxn(pend(), 77, book, 9001, food)!!
        assertEquals("7-11", tx.shop)
        assertEquals("統一超商", tx.bankShop)
        assertEquals("雙蔬鮪魚飯糰", tx.note)
        // 別的金額：只帶店家的叫法
        val other = d.pendShop(pend(35))
        assertEquals("7-11", other.shop)
        assertEquals("", other.note)
        assertNull(other.memo)
        // 門市名稱不一樣也算同一家
        assertEquals("雙蔬鮪魚飯糰", d.pendShop(pend(merchant = "統一超商信義門市")).note)
        // 退款、分期只帶店家
        assertEquals("", d.pendShop(pend(), false).note)
        // 沒連過的店：照銀行寫的
        val none = d.pendShop(pend(merchant = "鼎泰豐"))
        assertEquals("鼎泰豐", none.shop)
        assertEquals("", none.note)
        // 通知沒寫商店：店家寫錢包，沒有銀行寫的
        val w = d.pendShop(pend(merchant = ""))
        assertEquals("OP錢包", w.shop)
        assertEquals("", w.bankShop)
        // 按了「商店對錯了」（銀行寫的拿掉）：不再照它帶
        val off = data(mine.copy(bankShop = ""))
        assertEquals("統一超商", off.pendShop(pend()).shop)
        assertEquals("", off.pendShop(pend()).note)
    }

    @Test
    fun shopFieldHelpsMatching() {
        // 猜分類：店家是全聯、備註寫買了什麼
        val buy = t(shopCat, "衛生紙", amount = 120, shop = "全聯")
        val d = data(buy)
        assertEquals(shopCat, d.guessCat("全聯福利中心", 9001))
        // 7-11 也算超商
        assertEquals("超商", shopKindOf("7-11")?.name)
        // 退款連到原本那筆：比店家
        assertEquals(buy.id, d.refundGuess(9001, 20403, 120, "全聯福利中心")?.id)
        // 列表、退款的寫法：店家・備註
        assertEquals("全聯・衛生紙", buy.shopNote())
        assertEquals("全聯", buy.copy(note = "").shopNote())
        assertTrue(d.refundLabel(buy).contains("全聯・衛生紙"))
        // 店家的建議：用過越多次越前面
        val s = data(t(food, "", shop = "7-11"), t(food, "", shop = "全聯"), t(food, "", shop = "7-11")).shopSuggestions()
        assertEquals(listOf("7-11", "全聯"), s)
    }
}
