package tw.moneybook.app

import java.time.LocalDate
import kotlin.math.abs

/**
 * 把別的記帳 App 匯出的 CSV 轉成記帳本的資料。
 * 目前認得這種表頭：類型、記帳時間、交易帳戶、交易帳本、貨幣符號、交易金額、一級分類、二級分類、
 * 交易標籤、備註、報銷、手續費、匯率……
 *
 * 轉換規則（原則：匯入後每個帳戶的餘額變化，和舊 App 完全一樣）：
 * - 同一個時間、分類是「轉帳」或「還款」的一收一支配成一筆轉帳（兩邊金額差額記成手續費）
 * - 「投資 → 股票基金」的支出、「收入 → 股票基金」的收入，改成轉到／轉自「○○證券」投資帳戶；
 *   舊資料的持股市值已經記在「台股／美股／幣安」這類帳戶裡，所以新建的證券帳戶最後用一筆餘額調整沖回 0，避免資產重複計算
 * - 更新餘額、更新欠款、收益、虧損、借出、收回、報銷入帳：只改餘額，不算收入或支出（餘額調整）
 * - 外幣用檔案裡的匯率換成 NT$，原幣金額寫進備註
 * - 報銷欄的金額換成已報銷（對象留白）；手續費為負數代表優惠
 */
object LegacyImport {
    data class Summary(
        val rows: Int,
        val skipped: Int,
        val expense: Int,
        val income: Int,
        val transfers: Int,
        /** 股票基金的買賣，轉成轉帳到證券帳戶（已含在 transfers 裡） */
        val stockTransfers: Int,
        val adjusts: Int,
        /** 找不到另一半的轉帳／還款，當成一般收支 */
        val unpaired: Int,
        val foreign: Int,
        /** 新建的帳戶與匯入後的餘額 */
        val newAccounts: List<Pair<String, Long>>,
        /** 用餘額調整沖回 0 的證券帳戶 */
        val offsetAccounts: List<String>,
        val newBooks: List<String>,
        val newCategories: Int,
        val from: Long,
        val to: Long,
    )

    class Result(val data: AppData, val summary: Summary)

    private class Row(
        val income: Boolean,
        val time: String,
        val day: Long,
        val acc: String,
        val book: String,
        val cur: String,
        val orig: Double,
        val ntd: Long,
        val top: String,
        val sub: String,
        val tags: List<String>,
        val note: String,
        val reimb: Long,
        val fee: Long,
    ) {
        val foreign: Boolean get() = cur != "NT$"
    }

    /** 是不是這種格式 */
    fun detect(rows: List<List<String>>): Boolean {
        if (rows.isEmpty()) return false
        val h = rows[0].map { it.trim() }
        return listOf("類型", "記帳時間", "交易帳戶", "一級分類", "交易金額").all { it in h }
    }

    private val adjustTops = setOf("更新餘額", "更新欠款", "收益", "虧損", "借出", "收回")

    private val icons = mapOf(
        "吃喝" to "img:cat_food", "早餐" to "img:cat_breakfast", "午餐" to "img:cat_lunch", "晚餐" to "img:cat_dinner",
        "飲料" to "img:cat_drink", "點心" to "img:cat_snack", "交通" to "img:cat_transport", "旅行" to "img:cat_travel",
        "生活" to "img:cat_daily", "購物" to "img:cat_shopping", "醫療" to "img:cat_medical", "娛樂" to "img:cat_game",
        "學習" to "img:cat_education", "薪資" to "img:cat_salary", "獎金" to "img:cat_bonus", "紅包" to "img:cat_redpacket",
    )

    /** 從帳戶名稱猜帳戶類型（猜錯了可以到帳戶管理改） */
    fun guessType(name: String): AccountType {
        val n = name.lowercase()
        return when {
            n == "台股" || n == "美股" || n == "幣安" || n.endsWith("證券") -> AccountType.INVEST
            "悠遊卡" in n || "ipass" in n || "suica" in n || "icash" in n -> AccountType.ECARD
            "pay" in n -> AccountType.EPAY
            "卡" in n || "card" in n -> AccountType.CARD
            "錢包" in n || "零錢" in n || "現金" in n -> AccountType.CASH
            "bank" in n || "銀行" in n || "國泰" in n || "富邦" in n || "永豐" in n || "將來" in n || "玉山" in n || "中信" in n || "台新" in n -> AccountType.BANK
            else -> AccountType.OTHER
        }
    }

    private fun brokerName(acc: String): String = when {
        "國泰" in acc -> "國泰證券"
        "永豐" in acc -> "永豐證券"
        "富邦" in acc -> "富邦證券"
        else -> "證券戶"
    }

    private fun plain(v: Double): String = java.math.BigDecimal.valueOf(abs(v)).stripTrailingZeros().toPlainString()

    /** 轉換並合併進目前的資料；格式不認得或沒有任何可匯入的記錄回傳 null */
    fun convert(d0: AppData, text: String): Result? {
        val rows = CsvIO.parse(text)
        if (rows.size < 2 || !detect(rows)) return null
        val h = rows[0].map { it.trim() }
        fun col(name: String) = h.indexOf(name)
        val cType = col("類型"); val cTime = col("記帳時間"); val cAcc = col("交易帳戶"); val cBook = col("交易帳本")
        val cCur = col("貨幣符號"); val cAmt = col("交易金額"); val cTop = col("一級分類"); val cSub = col("二級分類")
        val cTags = col("交易標籤"); val cNote = col("備註"); val cReimb = col("報銷"); val cFee = col("手續費"); val cRate = col("匯率")

        // ── 讀成中間格式 ──
        val list = ArrayList<Row>()
        var skipped = 0
        for (r in rows.drop(1)) {
            fun get(c: Int): String = if (c >= 0 && c < r.size) r[c].trim() else ""
            fun num(c: Int): Double? = get(c).replace(",", "").toDoubleOrNull()
            val time = get(cTime)
            val day = try { LocalDate.parse(time.take(10)).toEpochDay() } catch (_: Exception) { skipped++; continue }
            val raw = num(cAmt)
            if (raw == null) { skipped++; continue }
            val typeText = get(cType)
            val income = typeText.contains("收")
            if (!income && !typeText.contains("支")) { skipped++; continue }
            val cur = get(cCur).ifBlank { "NT$" }
            val rate = if (cur != "NT$") (num(cRate)?.takeIf { it > 0.0 } ?: 1.0) else 1.0
            val tagText = get(cTags)
            list.add(
                Row(
                    income = income, time = time, day = day, acc = get(cAcc), book = get(cBook),
                    cur = cur, orig = raw, ntd = Math.round(abs(raw) * rate),
                    top = get(cTop), sub = get(cSub),
                    tags = if (tagText == "/") emptyList() else parseTags(tagText),
                    note = get(cNote),
                    reimb = Math.round(abs(num(cReimb) ?: 0.0) * rate),
                    fee = Math.round((num(cFee) ?: 0.0) * rate),
                )
            )
        }
        if (list.isEmpty()) return null

        // ── 準備新資料 ──
        var nextId = d0.nextId
        val accs = d0.accounts.toMutableList()
        val cats = d0.categories.toMutableList()
        val books = d0.books.toMutableList()
        val newAccNames = ArrayList<String>()
        val brokerIds = LinkedHashSet<Long>()
        val newBookNames = ArrayList<String>()
        val catsBefore = cats.size
        val currentBookId = d0.currentBook.id

        fun findAcc(name: String, forceType: AccountType? = null): Long {
            val n = name.ifBlank { "未指定帳戶" }
            accs.firstOrNull { it.name == n }?.let { return it.id }
            val type = forceType ?: guessType(n)
            val a = Account(nextId++, n, type.emoji, type, 0L, accs.size)
            accs.add(a)
            newAccNames.add(n)
            return a.id
        }

        fun findBook(name: String): Long {
            if (name.isBlank()) return currentBookId
            books.firstOrNull { it.name == name }?.let { return it.id }
            val b = Book(nextId++, name, "img:ui_ledger", 0L)
            books.add(b)
            newBookNames.add(name)
            return b.id
        }

        fun findCat(kind: TxType, top: String, sub: String): Long {
            val topName = top.ifBlank { "其他" }
            val p = cats.firstOrNull { it.kind == kind && it.parentId == null && it.name == topName }
                ?: Category(nextId++, topName, icons[topName] ?: "img:cat_box", 8, kind, null, cats.count { it.kind == kind && it.parentId == null })
                    .also { cats.add(it) }
            if (sub.isBlank() || sub == topName) return p.id
            val s = cats.firstOrNull { it.parentId == p.id && it.name == sub }
                ?: Category(nextId++, sub, icons[sub] ?: p.emoji, p.color, kind, p.id, cats.count { it.parentId == p.id })
                    .also { cats.add(it) }
            return s.id
        }

        fun noteOf(r: Row, extra: String = ""): String {
            val parts = ArrayList<String>()
            if (extra.isNotBlank()) parts.add(extra)
            if (r.note.isNotBlank()) parts.add(r.note)
            if (r.foreign) parts.add("（原幣 ${plain(r.orig)}${r.cur}）")
            return parts.joinToString(" ")
        }

        // 轉帳、還款：同一個時間、同一種分類剛好一收一支，才能配成一筆
        val groups = LinkedHashMap<String, MutableList<Row>>()
        for (r in list) if (r.top == "轉帳" || r.top == "還款") groups.getOrPut(r.time + "|" + r.top) { ArrayList() }.add(r)
        val pairs = groups.filterValues { g -> g.size == 2 && g.count { it.income } == 1 && g[0].acc != g[1].acc }

        val added = ArrayList<Txn>()
        val doneGroups = HashSet<String>()
        var nExpense = 0; var nIncome = 0; var nTransfer = 0; var nStock = 0; var nAdjust = 0; var nUnpaired = 0; var nForeign = 0

        for (r in list) {
            if (r.foreign) nForeign++
            val bookId = findBook(r.book)
            val key = r.time + "|" + r.top

            if (key in pairs) {
                if (!doneGroups.add(key)) continue
                val g = pairs.getValue(key)
                val out = g.first { !it.income }
                val into = g.first { it.income }
                val amount = if (out.ntd >= into.ntd) into.ntd else out.ntd
                val fee = (out.ntd - amount).coerceAtLeast(0L)
                added.add(
                    Txn(
                        id = nextId++, bookId = bookId, type = TxType.TRANSFER, amount = amount, categoryId = null,
                        accountId = findAcc(out.acc), toAccountId = findAcc(into.acc),
                        day = out.day, note = noteOf(if (out.note.isBlank() && into.note.isNotBlank()) into else out),
                        tags = out.tags, fee = fee,
                    )
                )
                nTransfer++
                continue
            }
            if (r.top == "轉帳" || r.top == "還款") nUnpaired++

            // 股票基金的買賣：轉到／轉自證券帳戶，不算支出或收入
            if ((!r.income && r.top == "投資" && r.sub == "股票基金") || (r.income && r.top == "收入" && r.sub == "股票基金")) {
                if (r.ntd > 0L) {
                    val broker = findAcc(brokerName(r.acc), AccountType.INVEST)
                    if (accs.first { it.id == broker }.name in newAccNames) brokerIds.add(broker)
                    val bank = findAcc(r.acc)
                    added.add(
                        Txn(
                            id = nextId++, bookId = bookId, type = TxType.TRANSFER, amount = r.ntd, categoryId = null,
                            accountId = if (r.income) broker else bank, toAccountId = if (r.income) bank else broker,
                            day = r.day, note = noteOf(r, if (r.income) "賣出" else "買進"), tags = r.tags,
                        )
                    )
                    nTransfer++; nStock++
                }
                continue
            }

            // 只改餘額、不算收支的項目
            if (r.top in adjustTops || (r.income && r.top == "報銷")) {
                if (r.ntd > 0L) {
                    val extra = if (r.income && r.top == "報銷") "報銷入帳" else r.top
                    added.add(
                        Txn(
                            id = nextId++, bookId = bookId, type = if (r.income) TxType.INCOME else TxType.EXPENSE, amount = r.ntd,
                            categoryId = null, accountId = findAcc(r.acc), toAccountId = null,
                            day = r.day, note = noteOf(r, extra), tags = emptyList(), adjust = true,
                        )
                    )
                    nAdjust++
                }
                continue
            }

            // 一般收支：實際金額（扣掉優惠、加上手續費後）要等於舊 App 的金額
            val kind = if (r.income) TxType.INCOME else TxType.EXPENSE
            var amount = r.ntd
            var fee = 0L
            var discount = 0L
            if (r.fee > 0L) {
                fee = r.fee.coerceAtMost(r.ntd)
                amount = if (r.income) r.ntd + fee else r.ntd - fee
            } else if (r.fee < 0L && !r.income) {
                discount = -r.fee
                amount = r.ntd + discount
            }
            if (amount <= 0L) continue
            var t = Txn(
                id = nextId++, bookId = bookId, type = kind, amount = amount,
                categoryId = findCat(kind, r.top, r.sub), accountId = findAcc(r.acc), toAccountId = null,
                day = r.day, note = noteOf(r), tags = r.tags, fee = fee, discount = discount,
            )
            // 報銷欄：已經報銷完了（錢是另外一筆「報銷入帳」進來的），這筆只算自己負擔的部分
            val reimb = r.reimb.coerceAtMost(t.paid)
            if (!r.income && reimb > 0L) {
                t = t.withItems(listOf(ReimbItem("", reimb, listOf(ReimbPay(r.day, null, reimb)), true)))
            }
            added.add(t)
            if (r.income) nIncome++ else nExpense++
        }

        // 新建的證券帳戶沖回 0：舊資料的持股市值已經記在「台股／美股／幣安」，不然資產會重複算
        val lastDay = list.maxOf { it.day }
        val offsetNames = ArrayList<String>()
        val before = d0.copy(accounts = accs, txns = d0.txns + added).balances()
        for (id in brokerIds) {
            val b = before[id] ?: 0L
            if (b == 0L) continue
            added.add(
                Txn(
                    id = nextId++, bookId = currentBookId, type = if (b > 0L) TxType.EXPENSE else TxType.INCOME, amount = abs(b),
                    categoryId = null, accountId = id, toAccountId = null, day = lastDay,
                    note = "沖回 0：舊資料的持股市值已記在「台股／美股／幣安」，避免重複計算", tags = emptyList(), adjust = true,
                )
            )
            offsetNames.add(accs.first { it.id == id }.name)
        }

        val nd = d0.copy(
            books = books,
            accounts = accs,
            categories = cats,
            txns = sortTxns(d0.txns + added),
            nextId = nextId,
        )
        val bal = nd.balances()
        val summary = Summary(
            rows = list.size + skipped, skipped = skipped,
            expense = nExpense, income = nIncome, transfers = nTransfer, stockTransfers = nStock,
            adjusts = nAdjust, unpaired = nUnpaired, foreign = nForeign,
            offsetAccounts = offsetNames,
            newAccounts = newAccNames.map { n -> n to (bal[accs.first { it.name == n }.id] ?: 0L) },
            newBooks = newBookNames, newCategories = cats.size - catsBefore,
            from = list.minOf { it.day }, to = list.maxOf { it.day },
        )
        return Result(nd, summary)
    }
}
