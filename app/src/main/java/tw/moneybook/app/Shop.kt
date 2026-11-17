package tw.moneybook.app

/*
 * 店家欄位：每一筆可以填店家（選填），備註放買了什麼。
 * 跟刷卡通知連起來過的記錄另外存「銀行寫的」商店名稱（bankShop，看得到、不能改，只能拿掉），
 * 下次同一家店的通知用它對到這筆：店家照你的叫法；金額也一樣時，備註和分類也照這筆帶。
 */

/** 比對商店用的名稱：銀行寫的、店家、備註第一行（有寫的才算） */
fun Txn.shopNames(): List<String> =
    listOf(bankShop, shop, note.lineSequence().firstOrNull() ?: "").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** 這筆是不是 key（shopKey 整理過的）這家店 */
fun Txn.isShop(key: String): Boolean = key.length >= 2 && shopNames().any { sameShop(key, shopKey(it)) }

/** 列表上的寫法：店家・備註第一行（都沒有是空白） */
fun Txn.shopNote(): String =
    listOf(shop.trim(), note.lineSequence().firstOrNull()?.trim().orEmpty()).filter { it.isNotEmpty() }.joinToString("・")

/**
 * 沒有店家欄位的舊資料：刷卡通知記的那些（標了 #刷卡通知），備註就是銀行寫的商店（有錢包時是「商店（OP錢包）」），
 * 搬到店家和銀行寫的，備註清空。備註有好幾行（自己加過字）、或只有錢包名稱的不動。
 */
fun migrateCardShops(txns: List<Txn>): List<Txn> {
    val wallets = WALLETS.map { it.second }.distinct()
    return txns.map { t ->
        val n = t.note.trim()
        if (CARD_NOTI_TAG !in t.tags || t.type == TxType.TRANSFER || t.adjust || t.shop.isNotEmpty() || n.isEmpty() || '\n' in n) return@map t
        val w = wallets.firstOrNull { n.endsWith("（$it）") }
        val name = (if (w != null) n.removeSuffix("（$w）") else n).trim()
        if (name.isEmpty() || name in wallets) t else t.copy(shop = name, bankShop = t.bankShop.ifEmpty { name }, note = "")
    }
}

/** 店家的建議：用過的店家，照用過的次數、最近用的日期排 */
fun AppData.shopSuggestions(): List<String> {
    class Stat(var n: Int = 0, var last: Long = Long.MIN_VALUE)
    val m = LinkedHashMap<String, Stat>()
    for (t in txns) {
        val s = t.shop.trim()
        if (s.isEmpty() || t.adjust) continue
        val st = m.getOrPut(s) { Stat() }
        st.n++
        if (t.day > st.last) st.last = t.day
    }
    return m.entries.sortedWith(compareByDescending<Map.Entry<String, Stat>> { it.value.n }.thenByDescending { it.value.last }).map { it.key }
}

/** 照上次的記法：src＝銀行寫的是同一家店的最近一筆；exact＝金額也一樣（備註、分類也照它帶） */
class ShopMemo(val src: Txn, val exact: Boolean)

/**
 * 刷卡通知的商店對到以前的哪一筆：銀行寫的是同一家店的支出（不算餘額調整、分期），從新到舊。
 * 台幣、記的總額和通知金額一樣的最近一筆是 exact；沒有就取同一家店最近一筆（只帶店家的叫法）。
 */
fun AppData.shopMemo(merchant: String, amount: Long, cur: String): ShopMemo? {
    val key = shopKey(merchant)
    if (key.length < 2) return null
    var any: Txn? = null
    for (t in txns) {
        if (t.type != TxType.EXPENSE || t.adjust || t.bankShop.isEmpty() || t.instTotal > 1) continue
        if (!sameShop(key, shopKey(t.bankShop))) continue
        if (cur.isEmpty() && !t.isFxSpend && t.fxAmount == 0L && t.recorded == amount) return ShopMemo(t, true)
        if (any == null) any = t
    }
    return any?.let { ShopMemo(it, false) }
}

/** 刷卡通知記成的那一筆的店家、銀行寫的、備註；memo＝照哪一筆的記法帶了備註（金額一樣的才有） */
class PendShop(val shop: String, val bankShop: String, val note: String, val memo: Txn?) {
    /** 待確認上的名稱：店家・備註 */
    val name: String get() = listOf(shop, note.lineSequence().firstOrNull()?.trim().orEmpty()).filter { it.isNotEmpty() }.joinToString("・")
}

/**
 * 待確認記成的店家：同一家店以前有記過就用你的叫法，金額一樣再帶備註；沒有就用銀行寫的（沒寫商店時用錢包）。
 * withNote = false：只帶店家（退款、分期）。
 */
fun AppData.pendShop(p: CardPend, withNote: Boolean = true): PendShop {
    val memo = if (p.merchant.isEmpty()) null else shopMemo(p.merchant, p.amount, p.cur)
    val exact = memo?.takeIf { it.exact && withNote }?.src
    val shop = memo?.src?.shop?.trim()?.ifEmpty { null } ?: p.merchant.ifEmpty { p.wallet }
    return PendShop(shop, p.merchant, exact?.note.orEmpty(), exact)
}

/** 待確認的名稱（沒有店家也沒有備註寫「刷卡」） */
fun AppData.pendName(p: CardPend): String = pendShop(p, p.kind == PendKind.SPEND && !p.isInst).name.ifEmpty { "刷卡" }

/** 待確認自動帶的分類：照上次記法（金額一樣）那筆的分類，沒有就照 guessCat */
fun AppData.pendCat(p: CardPend, cardId: Long?): Long? =
    pendShop(p).memo?.categoryId?.let { catMap[it] }?.takeIf { it.kind == TxType.EXPENSE }?.id ?: guessCat(p.merchant, cardId)

/** 確認「已經有一筆」時：那筆補上銀行寫的（店家空著就也填上） */
fun Txn.linkBankShop(merchant: String): Txn =
    if (merchant.isBlank()) this else copy(bankShop = merchant, shop = shop.ifBlank { merchant })
