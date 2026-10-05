package tw.moneybook.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** AppData <-> JSON。使用 Android 內建的 org.json，不需要額外套件。 */
object Codec {

    private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun strings(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.getString(i))
        return out
    }

    private fun <T> objects(arr: JSONArray?, f: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        val out = ArrayList<T>()
        for (i in 0 until arr.length()) out.add(f(arr.getJSONObject(i)))
        return out
    }

    private fun nullable(v: Long?): Any = v ?: JSONObject.NULL

    fun encode(d: AppData): String {
        val root = JSONObject()
        root.put("version", 2)
        root.put("nextId", d.nextId)

        root.put("books", JSONArray().apply {
            d.books.forEach { b ->
                val mb = JSONObject()
                b.monthBudgets.forEach { (k, v) -> mb.put(k, v) }
                put(JSONObject().put("id", b.id).put("name", b.name).put("emoji", b.emoji).put("budget", b.budget).put("monthBudgets", mb))
            }
        })
        root.put("accounts", JSONArray().apply {
            d.accounts.forEach { a ->
                put(
                    JSONObject().put("id", a.id).put("name", a.name).put("emoji", a.emoji)
                        .put("type", a.type.name).put("initial", a.initial).put("order", a.order)
                        .put("hidden", a.hidden)
                        .put("badge", a.badge).put("badgeColor", a.badgeColor)
                        .put("badgeFrom", a.badgeFrom).put("badgeTo", a.badgeTo)
                        .put("creditLimit", a.creditLimit).put("statementDay", a.statementDay).put("dueDay", a.dueDay)
                        .put("sharedLimitOf", a.sharedLimitOf)
                        .put("favorite", a.favorite).put("currency", a.currency)
                )
            }
        })
        root.put("categories", JSONArray().apply {
            d.categories.forEach { c ->
                put(
                    JSONObject().put("id", c.id).put("name", c.name).put("emoji", c.emoji)
                        .put("color", c.color).put("kind", c.kind.name)
                        .put("parentId", nullable(c.parentId)).put("order", c.order)
                )
            }
        })
        root.put("txns", JSONArray().apply {
            d.txns.forEach { t ->
                put(
                    JSONObject().put("id", t.id).put("bookId", t.bookId).put("type", t.type.name)
                        .put("amount", t.amount).put("categoryId", nullable(t.categoryId))
                        .put("accountId", nullable(t.accountId)).put("toAccountId", nullable(t.toAccountId))
                        .put("day", t.day).put("note", t.note).put("tags", JSONArray(t.tags))
                        .put("instGroup", nullable(t.instGroup)).put("instIndex", t.instIndex)
                        .put("instTotal", t.instTotal)
                        .put("fee", t.fee).put("discount", t.discount).put("reimb", t.reimb)
                        .put("reimbAccountId", nullable(t.reimbAccountId)).put("reimbDay", nullable(t.reimbDay))
                        .put("reimbAmount", t.reimbAmount)
                        .put("reimbItems", ReimbCodec.toJson(t.reimbItems))
                        .put("adjust", t.adjust).put("time", t.time).put("fxAmount", t.fxAmount)
                )
            }
        })
        root.put("templates", JSONArray().apply {
            d.templates.forEach { t ->
                put(
                    JSONObject().put("id", t.id).put("name", t.name).put("type", t.type.name)
                        .put("amount", t.amount).put("categoryId", nullable(t.categoryId))
                        .put("accountId", nullable(t.accountId)).put("note", t.note)
                        .put("tags", JSONArray(t.tags))
                )
            }
        })
        root.put("trades", JSONArray().apply {
            d.trades.forEach { t ->
                put(
                    JSONObject().put("id", t.id).put("accountId", t.accountId).put("symbol", t.symbol)
                        .put("name", t.name).put("day", t.day).put("buy", t.buy).put("qty", t.qty)
                        .put("price", t.price).put("fee", t.fee).put("txnId", nullable(t.txnId)).put("market", t.market)
                )
            }
        })
        root.put("prices", JSONArray().apply {
            d.prices.forEach { p -> put(JSONObject().put("symbol", p.symbol).put("day", p.day).put("price", p.price)) }
        })
        root.put("rates", JSONArray().apply {
            d.rates.forEach { r -> put(JSONObject().put("code", r.code).put("rate", r.rate).put("day", r.day)) }
        })
        val p = d.prefs
        root.put(
            "prefs",
            JSONObject().put("bookId", p.bookId).put("palette", p.palette).put("mascot", p.mascot)
                .put("mascotName", p.mascotName).put("mascotLast", p.mascotLast).put("dark", p.dark).put("celebrate", p.celebrate)
                .put("priceFetch", p.priceFetch).put("priceFetchDay", p.priceFetchDay)
                .put("collapsedAccTypes", JSONArray(p.collapsedAccTypes))
        )
        return root.toString()
    }

    fun decode(text: String): AppData {
        val root = JSONObject(text)
        val books = objects(root.optJSONArray("books")) { o ->
            val mbo = o.optJSONObject("monthBudgets")
            val mb = HashMap<String, Long>()
            if (mbo != null) {
                val it = mbo.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    mb[k] = mbo.optLong(k, 0L)
                }
            }
            Book(o.getLong("id"), o.getString("name"), o.optString("emoji", "img:ui_ledger"), o.optLong("budget", 0L), mb)
        }
        val accounts = objects(root.optJSONArray("accounts")) { o ->
            Account(
                id = o.getLong("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "img:acc_purse"),
                type = runCatching { AccountType.valueOf(o.getString("type")) }.getOrDefault(AccountType.OTHER),
                initial = o.optLong("initial", 0L),
                order = o.optInt("order", 0),
                hidden = o.optBoolean("hidden", false),
                favorite = o.optBoolean("favorite", false),
                currency = o.optString("currency", ""),
                badge = o.optString("badge", ""),
                badgeColor = o.optInt("badgeColor", 0),
                badgeFrom = o.optLong("badgeFrom", 0L),
                badgeTo = o.optLong("badgeTo", 0L),
                creditLimit = o.optLong("creditLimit", 0L),
                statementDay = o.optInt("statementDay", 0),
                dueDay = o.optInt("dueDay", 0),
                sharedLimitOf = o.optLong("sharedLimitOf", 0L),
            )
        }
        val categories = objects(root.optJSONArray("categories")) { o ->
            Category(
                id = o.getLong("id"),
                name = o.getString("name"),
                emoji = o.optString("emoji", "img:cat_box"),
                color = o.optInt("color", 0),
                kind = runCatching { TxType.valueOf(o.getString("kind")) }.getOrDefault(TxType.EXPENSE),
                parentId = o.optLongOrNull("parentId"),
                order = o.optInt("order", 0),
            )
        }
        val txns = objects(root.optJSONArray("txns")) { o ->
            val t = Txn(
                id = o.getLong("id"),
                bookId = o.getLong("bookId"),
                type = runCatching { TxType.valueOf(o.getString("type")) }.getOrDefault(TxType.EXPENSE),
                amount = o.getLong("amount"),
                categoryId = o.optLongOrNull("categoryId"),
                accountId = o.optLongOrNull("accountId"),
                toAccountId = o.optLongOrNull("toAccountId"),
                day = o.getLong("day"),
                note = o.optString("note", ""),
                tags = strings(o.optJSONArray("tags")),
                instGroup = o.optLongOrNull("instGroup"),
                instIndex = o.optInt("instIndex", 0),
                instTotal = o.optInt("instTotal", 0),
                fee = o.optLong("fee", 0L),
                discount = o.optLong("discount", 0L),
                reimb = o.optInt("reimb", 0),
                reimbAccountId = o.optLongOrNull("reimbAccountId"),
                reimbDay = o.optLongOrNull("reimbDay"),
                reimbAmount = o.optLong("reimbAmount", -1L),
                reimbItems = ReimbCodec.fromJson(o.optJSONArray("reimbItems")),
                adjust = o.optBoolean("adjust", false),
                time = o.optInt("time", -1),
                fxAmount = o.optLong("fxAmount", 0L),
            )
            // 舊資料沒有報銷金額時視為全額
            if (t.reimbAmount < 0) t.copy(reimbAmount = if (t.reimb != 0) t.paid else 0L) else t
        }
        val templates = objects(root.optJSONArray("templates")) { o ->
            Template(
                id = o.getLong("id"),
                name = o.getString("name"),
                type = runCatching { TxType.valueOf(o.getString("type")) }.getOrDefault(TxType.EXPENSE),
                amount = o.optLong("amount", 0L),
                categoryId = o.optLongOrNull("categoryId"),
                accountId = o.optLongOrNull("accountId"),
                note = o.optString("note", ""),
                tags = strings(o.optJSONArray("tags")),
            )
        }
        val trades = objects(root.optJSONArray("trades")) { o ->
            Trade(
                id = o.getLong("id"),
                accountId = o.getLong("accountId"),
                symbol = o.getString("symbol"),
                name = o.optString("name", ""),
                day = o.getLong("day"),
                buy = o.optBoolean("buy", true),
                qty = o.optDouble("qty", 0.0),
                price = o.optDouble("price", 0.0),
                fee = o.optLong("fee", 0L),
                txnId = o.optLongOrNull("txnId"),
                market = o.optString("market", ""),
            )
        }
        val prices = objects(root.optJSONArray("prices")) { o ->
            PriceSnap(o.getString("symbol"), o.getLong("day"), o.optDouble("price", 0.0))
        }
        val rates = objects(root.optJSONArray("rates")) { o ->
            FxRate(o.getString("code"), o.optDouble("rate", 0.0), o.optLong("day", 0L))
        }.filter { it.rate > 0.0 }
        require(books.isNotEmpty()) { "沒有帳本資料" }
        val po = root.optJSONObject("prefs") ?: JSONObject()
        val prefs = Prefs(
            bookId = po.optLong("bookId", books.first().id),
            palette = po.optString("palette", "milktea"),
            mascot = po.optString("mascot", "deer"),
            mascotName = po.optString("mascotName", ""),
            mascotLast = po.optString("mascotLast", "deer"),
            dark = po.optInt("dark", 0),
            celebrate = po.optBoolean("celebrate", true),
            priceFetch = po.optBoolean("priceFetch", false),
            priceFetchDay = po.optLong("priceFetchDay", 0L),
            collapsedAccTypes = strings(po.optJSONArray("collapsedAccTypes")),
        )
        val maxId = (books.map { it.id } + accounts.map { it.id } + categories.map { it.id } +
            txns.map { it.id } + templates.map { it.id } + trades.map { it.id }).maxOrNull() ?: 0L
        return AppData(
            books = books,
            accounts = accounts,
            categories = categories,
            txns = sortTxns(txns),
            templates = templates,
            prefs = prefs,
            nextId = maxOf(root.optLong("nextId", 1L), maxId + 1),
            trades = trades,
            prices = prices,
            rates = rates,
        )
    }

    /** 第一版（v1）的資料格式轉換成新格式 */
    fun migrateV1(text: String, base: AppData): AppData {
        val root = JSONObject(text)
        val arr = root.optJSONArray("txns") ?: JSONArray()
        val cashId = base.accounts.first().id
        val bookId = base.books.first().id
        fun catFor(name: String, expense: Boolean): Long? {
            val kind = if (expense) TxType.EXPENSE else TxType.INCOME
            val tops = base.topCategories(kind)
            return (tops.firstOrNull { it.name == name } ?: tops.lastOrNull())?.id
        }
        val list = ArrayList<Txn>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val expense = o.optBoolean("isExpense", true)
            list.add(
                Txn(
                    id = o.getLong("id"),
                    bookId = bookId,
                    type = if (expense) TxType.EXPENSE else TxType.INCOME,
                    amount = o.getLong("amount"),
                    categoryId = catFor(o.optString("category", ""), expense),
                    accountId = cashId,
                    toAccountId = null,
                    day = LocalDate.parse(o.getString("date")).toEpochDay(),
                    note = o.optString("note", ""),
                    tags = emptyList(),
                )
            )
        }
        val budget = root.optLong("budget", 0L)
        return base.copy(
            books = base.books.map { if (it.id == bookId) it.copy(budget = budget) else it },
            txns = sortTxns(list),
        )
    }
}

/** 資料檔放在 App 私有目錄，不需要任何權限 */
class Store(private val dir: File) {
    private val file = File(dir, "moneybook_v2.json")
    private val legacy = File(dir, "moneybook.json")

    fun load(): AppData {
        if (file.exists()) {
            try {
                return Codec.decode(file.readText())
            } catch (e: Exception) {
                // 檔案壞掉時先留一份副本，避免資料被覆蓋後找不回來
                try {
                    file.copyTo(File(dir, "moneybook_v2.broken.json"), overwrite = true)
                } catch (_: Exception) {
                }
            }
        }
        val base = Defaults.create()
        if (legacy.exists()) {
            try {
                return Codec.migrateV1(legacy.readText(), base)
            } catch (_: Exception) {
            }
        }
        return base
    }

    fun save(d: AppData) {
        val text = Codec.encode(d)
        val tmp = File(dir, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}

/** 計算機鍵盤：只支援加減，金額為整數 */
object Calc {
    private const val MAX = 9_999_999_999L

    fun eval(expr: String): Long {
        var total = 0L
        var cur = 0L
        var sign = 1L
        for (ch in expr) {
            when {
                ch.isDigit() -> cur = minOf(MAX, cur * 10 + (ch - '0'))
                ch == '+' -> { total += sign * cur; cur = 0; sign = 1 }
                ch == '-' -> { total += sign * cur; cur = 0; sign = -1 }
            }
        }
        total += sign * cur
        return total.coerceIn(0L, MAX)
    }

    fun hasOp(expr: String): Boolean = expr.drop(1).any { it == '+' || it == '-' }

    fun press(expr: String, key: String): String {
        return when (key) {
            "⌫" -> expr.dropLast(1)
            "C" -> ""
            "+", "-" -> when {
                expr.isEmpty() -> expr
                expr.last() == '+' || expr.last() == '-' -> expr.dropLast(1) + key
                else -> expr + key
            }
            else -> {
                if (expr.length >= 30) return expr
                val lastNum = expr.takeLastWhile { it.isDigit() }
                if (lastNum.length >= 10) return expr
                if (lastNum == "0") expr.dropLast(1) + key.trimStart('0').ifEmpty { "0" }
                else if (lastNum.isEmpty() && key == "00") expr + "0"
                else expr + key
            }
        }
    }

    fun pretty(expr: String): String = expr.replace("-", " − ").replace("+", " + ")

    private const val MAX_FX = 99_999_999_999L

    /** 外幣模式：金額可以有小數（dec 位），結果是最小單位（美元的分） */
    fun evalMinor(expr: String, dec: Int): Long {
        var total = 0L
        var sign = 1L
        val cur = StringBuilder()
        fun flush() {
            total += sign * (parseFx(cur.toString(), dec) ?: 0L)
            cur.clear()
        }
        for (ch in expr) {
            when {
                ch.isDigit() || ch == '.' -> cur.append(ch)
                ch == '+' -> { flush(); sign = 1L }
                ch == '-' -> { flush(); sign = -1L }
            }
        }
        flush()
        return total.coerceIn(0L, MAX_FX)
    }

    /** 外幣模式的按鍵：多了小數點，小數位數不超過幣別的位數（日圓這類 0 位的不能打小數點） */
    fun pressFx(expr: String, key: String, dec: Int): String {
        val last = expr.takeLastWhile { it.isDigit() || it == '.' }
        return when {
            key == "." -> when {
                dec == 0 || '.' in last -> expr
                last.isEmpty() -> expr + "0."
                else -> expr + "."
            }
            key.all { it.isDigit() } && '.' in last -> {
                val room = dec - (last.length - last.indexOf('.') - 1)
                if (room <= 0) expr else expr + key.take(room)
            }
            else -> press(expr, key)
        }
    }
}

/** CSV 匯出與匯入 */
object CsvIO {
    private val header = listOf("日期", "類型", "金額", "手續費", "優惠", "實際金額", "分類", "子分類", "帳戶", "轉入帳戶", "帳本", "報銷", "報銷金額", "備註", "標籤", "時間", "外幣金額", "外幣幣別")

    private fun esc(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /**
     * 文字欄位：開頭是 = + - @ 的內容，Excel 會當成公式執行（CSV 公式注入）。
     * 前面加一個 ' 讓它只被當成文字。
     */
    private fun txt(s: String): String =
        if (s.isNotEmpty() && s[0] in "=+-@\t\r") "'$s" else s

    /** 匯入時把我們加的 ' 拿掉 */
    private fun untxt(s: String): String =
        if (s.length >= 2 && s[0] == '\'' && s[1] in "=+-@\t\r") s.substring(1) else s

    private fun typeLabel(t: Txn) = when {
        t.adjust -> if (t.type == TxType.EXPENSE) "餘額調整（減少）" else "餘額調整（增加）"
        t.type == TxType.EXPENSE -> "支出"
        t.type == TxType.INCOME -> "收入"
        else -> "轉帳"
    }

    private fun fxCells(d: AppData, t: Txn): Array<String> {
        if (t.fxAmount <= 0L) return arrayOf("", "")
        val fa = listOfNotNull(t.accountId, t.toAccountId).mapNotNull { d.accMap[it] }.firstOrNull { it.isForeign } ?: return arrayOf("", "")
        return arrayOf(fxPlain(t.fxAmount, fa.cur.decimals), fa.currency)
    }

    fun export(d: AppData): ByteArray {
        val sb = StringBuilder()
        sb.append('﻿')
        sb.append(header.joinToString(",")).append('\n')
        val bookName = d.books.associate { it.id to it.name }
        for (t in d.txns.sortedBy { it.day }) {
            val c = t.categoryId?.let { d.catMap[it] }
            val top = c?.let { d.topOf(it) }
            val sub = if (c != null && c.parentId != null) c.name else ""
            val row = listOf(
                t.date.toString(),
                typeLabel(t),
                t.amount.toString(),
                t.fee.toString(),
                t.discount.toString(),
                t.paid.toString(),
                txt(top?.name ?: ""),
                txt(sub),
                txt(t.accountId?.let { d.accMap[it]?.name } ?: ""),
                txt(t.toAccountId?.let { d.accMap[it]?.name } ?: ""),
                txt(bookName[t.bookId] ?: ""),
                when (t.reimb) { 1 -> "待報銷"; 2 -> "已報銷"; else -> "" },
                if (t.reimb != 0) t.reimbAmount.toString() else "",
                txt(t.note),
                txt(t.tags.joinToString(" ")),
                formatTime(t.time),
                // 動到外幣帳戶的記錄：外幣金額（純數字、小數位照幣別）和幣別；金額欄位仍是約當台幣
                *fxCells(d, t),
            )
            sb.append(row.joinToString(",") { esc(it) }).append('\n')
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var inQuote = false
        var i = 0
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val ch = s[i]
            if (inQuote) {
                if (ch == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { cell.append('"'); i++ } else inQuote = false
                } else cell.append(ch)
            } else {
                when (ch) {
                    '"' -> inQuote = true
                    ',' -> { row.add(cell.toString()); cell.setLength(0) }
                    '\r' -> {}
                    '\n' -> {
                        row.add(cell.toString()); cell.setLength(0)
                        if (row.any { it.isNotBlank() }) rows.add(row)
                        row = ArrayList()
                    }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        row.add(cell.toString())
        if (row.any { it.isNotBlank() }) rows.add(row)
        return rows
    }

    /** 匯入 CSV（附加到目前帳本）。回傳新資料與匯入筆數。 */
    fun import(d0: AppData, text: String): Pair<AppData, Int> {
        val rows = parse(text)
        if (rows.size < 2) return Pair(d0, 0)
        val h = rows[0].map { it.trim() }
        fun col(vararg names: String): Int = names.map { h.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
        val cDate = col("日期", "Date", "date")
        val cType = col("類型", "收支", "Type")
        val cAmt = col("金額", "Amount", "amount")
        val cCat = col("分類", "類別", "Category")
        val cSub = col("子分類", "子類別")
        val cAcc = col("帳戶", "Account")
        val cTo = col("轉入帳戶")
        val cNote = col("備註", "Note", "note", "描述")
        val cTags = col("標籤", "Tags")
        val cTime = col("時間", "Time")
        val cFee = col("手續費")
        val cDisc = col("優惠", "折扣")
        val cReimb = col("報銷")
        val cReimbAmt = col("報銷金額")
        val cFx = col("外幣金額")
        val cFxCur = col("外幣幣別")
        if (cDate < 0 || cAmt < 0) return Pair(d0, 0)

        var d = d0
        var nextId = d.nextId
        val cats = d.categories.toMutableList()
        val accs = d.accounts.toMutableList()
        val bookId = d.currentBook.id
        val defaultAcc = d.visibleAccounts.firstOrNull()?.id ?: accs.firstOrNull()?.id

        fun findAcc(name: String, currency: String = ""): Long? {
            if (name.isBlank()) return defaultAcc
            accs.firstOrNull { it.name == name }?.let { return it.id }
            val a = if (currency.isEmpty()) Account(nextId++, name, "img:acc_purse", AccountType.OTHER, 0L, accs.size)
            else Account(nextId++, name, AccountType.FOREIGN.emoji, AccountType.FOREIGN, 0L, accs.size, currency = currency)
            accs.add(a)
            return a.id
        }

        fun findCat(kind: TxType, top: String, sub: String): Long? {
            val topName = top.ifBlank { "其他" }
            var p = cats.firstOrNull { it.kind == kind && it.parentId == null && it.name == topName }
            if (p == null) {
                p = Category(nextId++, topName, "img:cat_box", 8, kind, null, cats.count { it.kind == kind && it.parentId == null })
                cats.add(p)
            }
            if (sub.isBlank()) return p.id
            val pid = p.id
            val s = cats.firstOrNull { it.parentId == pid && it.name == sub }
                ?: Category(nextId++, sub, p.emoji, p.color, kind, pid, cats.count { it.parentId == pid }).also { cats.add(it) }
            return s.id
        }

        val added = ArrayList<Txn>()
        for (r in rows.drop(1)) {
            fun get(c: Int): String = if (c >= 0 && c < r.size) untxt(r[c].trim()) else ""
            val date = try {
                LocalDate.parse(get(cDate).replace('/', '-').let { s ->
                    val parts = s.split('-')
                    if (parts.size == 3) "%04d-%02d-%02d".format(parts[0].toInt(), parts[1].toInt(), parts[2].toInt()) else s
                })
            } catch (_: Exception) {
                continue
            }
            val raw = get(cAmt).replace(",", "").replace("$", "").trim()
            val amtD = raw.toDoubleOrNull() ?: continue
            val typeText = get(cType)
            // 匯出的「餘額調整（增加／減少）」要還原成不算收支的調整
            val isAdjust = typeText.contains("餘額調整")
            val type = when {
                isAdjust -> if (typeText.contains("減")) TxType.EXPENSE else TxType.INCOME
                typeText.contains("轉") -> TxType.TRANSFER
                typeText.contains("收") || typeText.equals("income", true) -> TxType.INCOME
                typeText.isEmpty() && amtD > 0 && cType < 0 -> TxType.EXPENSE
                else -> TxType.EXPENSE
            }
            val amount = kotlin.math.abs(Math.round(amtD))
            val fxCur = get(cFxCur).trim().uppercase().takeIf { Currencies.validCode(it) } ?: ""
            val fxRaw = if (fxCur.isNotEmpty()) parseFx(get(cFx), Currencies.of(fxCur).decimals) ?: 0L else 0L
            if (amount == 0L && fxRaw <= 0L) continue
            // 外幣在哪一邊：消費／收入是這個帳戶；轉帳看轉出帳戶本來就是外幣帳戶，不是的話就是轉入那邊
            val fromName = get(cAcc)
            val fxOnFrom = fxRaw > 0L && (type != TxType.TRANSFER || accs.firstOrNull { it.name == fromName }?.isForeign == true)
            val accId = findAcc(fromName, if (fxOnFrom) fxCur else "")
            val toId = if (type == TxType.TRANSFER) findAcc(get(cTo), if (fxRaw > 0L && !fxOnFrom) fxCur else "") else null
            val fxAcc = accs.firstOrNull { it.id == (if (fxOnFrom) accId else toId) }
            val fxVal = if (fxRaw > 0L && fxAcc != null && fxAcc.isForeign && fxAcc.currency == fxCur) fxRaw else 0L
            val catId = if (type == TxType.TRANSFER || isAdjust) null else findCat(type, get(cCat), get(cSub))
            added.add(
                Txn(
                    id = nextId++,
                    bookId = bookId,
                    type = type,
                    amount = amount,
                    categoryId = catId,
                    accountId = accId,
                    toAccountId = toId,
                    day = date.toEpochDay(),
                    note = get(cNote),
                    tags = parseTags(get(cTags)),
                    time = parseTime(get(cTime)),
                    fee = get(cFee).replace(",", "").toDoubleOrNull()?.let { kotlin.math.abs(Math.round(it)) } ?: 0L,
                    discount = if (type == TxType.EXPENSE) get(cDisc).replace(",", "").toDoubleOrNull()?.let { kotlin.math.abs(Math.round(it)) } ?: 0L else 0L,
                    reimb = if (type == TxType.EXPENSE && !isAdjust) when (get(cReimb)) { "待報銷" -> 1; "已報銷" -> 2; else -> 0 } else 0,
                    adjust = isAdjust,
                    fxAmount = fxVal,
                ).let { t ->
                    if (t.reimb == 0) t
                    else t.copy(reimbAmount = get(cReimbAmt).replace(",", "").toDoubleOrNull()?.let { Math.round(it) }?.coerceIn(0L, t.paid) ?: t.paid)
                }
            )
        }
        d = d.copy(
            categories = cats,
            accounts = accs,
            txns = sortTxns(d.txns + added),
            nextId = nextId,
        )
        return Pair(d, added.size)
    }
}

/** 報銷明細的 JSON 轉換（存檔用；記一筆畫面也用它把明細暫存成文字） */
object ReimbCodec {
    fun toJson(list: List<ReimbItem>): JSONArray = JSONArray().apply {
        list.forEach { i ->
            put(
                JSONObject().put("who", i.who).put("amount", i.amount).put("closed", i.closed)
                    .put(
                        "pays",
                        JSONArray().apply {
                            i.pays.forEach { p ->
                                put(JSONObject().put("day", p.day).put("accountId", p.accountId ?: JSONObject.NULL).put("amount", p.amount).put("time", p.time))
                            }
                        },
                    )
            )
        }
    }

    fun fromJson(arr: JSONArray?): List<ReimbItem> {
        if (arr == null) return emptyList()
        val out = ArrayList<ReimbItem>()
        for (n in 0 until arr.length()) {
            val o = arr.getJSONObject(n)
            val pa = o.optJSONArray("pays")
            val pays = ArrayList<ReimbPay>()
            if (pa != null) for (k in 0 until pa.length()) {
                val po = pa.getJSONObject(k)
                pays.add(ReimbPay(po.optLong("day", 0L), if (po.isNull("accountId")) null else po.getLong("accountId"), po.optLong("amount", 0L), po.optInt("time", -1)))
            }
            out.add(ReimbItem(o.optString("who", ""), o.optLong("amount", 0L), pays, o.optBoolean("closed", false)))
        }
        return out
    }

    fun encode(list: List<ReimbItem>): String = if (list.isEmpty()) "" else toJson(list).toString()

    fun decode(s: String): List<ReimbItem> = if (s.isBlank()) emptyList() else try { fromJson(JSONArray(s)) } catch (e: Exception) { emptyList() }
}
