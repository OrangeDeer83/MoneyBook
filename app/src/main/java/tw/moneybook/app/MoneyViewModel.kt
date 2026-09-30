package tw.moneybook.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalDate
import java.time.YearMonth

/** 統計頁點進某個分類/標籤時要列出的記錄 */
class Drill(
    val title: String,
    val mode: Int,
    val pred: (Txn) -> Boolean,
    val amt: (Txn) -> Long,
)

/** 畫面下方的提示訊息，可以附一個動作按鈕（例如復原） */
class UiMsg(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

/** 記一筆畫面送出的內容 */
data class TxnDraft(
    val type: TxType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val toAccountId: Long?,
    val day: Long,
    val note: String,
    val tags: List<String>,
    val installments: Int,
    val fee: Long = 0L,
    val discount: Long = 0L,
    /** 報銷明細（每個對象一項），空的代表不報銷 */
    val reimbItems: List<ReimbItem> = emptyList(),
)

/** 一次收款：第 index 個報銷對象收到 amount；chase = 收得比剩下的少時，是否繼續追 */
class ReimbReceipt(val txnId: Long, val index: Int, val amount: Long, val chase: Boolean)

/** 報銷總額不能超過原價 + 手續費；沒有收款紀錄又是 0 元的對象直接拿掉 */
private fun capItems(t: Txn, items: List<ReimbItem>): List<ReimbItem> {
    var left = t.reimbCap
    val out = ArrayList<ReimbItem>()
    for (i in items) {
        if (i.closed || i.pays.isNotEmpty()) {
            left -= i.effective
            out.add(i)
        } else {
            val a = i.amount.coerceIn(0L, left.coerceAtLeast(0L))
            if (a > 0L) {
                left -= a
                out.add(i.copy(amount = a))
            }
        }
    }
    return out
}

class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app.filesDir)

    var data by mutableStateOf(store.load())
        private set
    var month by mutableStateOf(YearMonth.now())

    /** 統計頁：0 月, 1 年, 2 區間, 3 趨勢 */
    var statMode by mutableIntStateOf(0)
    var statYear by mutableIntStateOf(LocalDate.now().year)
    var rangeStart by mutableLongStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay())
    var rangeEnd by mutableLongStateOf(LocalDate.now().toEpochDay())

    var drill by mutableStateOf<Drill?>(null)

    fun setRange(a: Long, b: Long) {
        rangeStart = minOf(a, b)
        rangeEnd = maxOf(a, b)
    }
    var celebrateTick by mutableIntStateOf(0)
        private set

    private val _messages = MutableSharedFlow<UiMsg>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMsg> = _messages

    fun toast(msg: String) {
        _messages.tryEmit(UiMsg(msg))
    }

    /** 撒花：只在新增記錄時觸發，播過就不再重播 */
    var celebratePlayed = 0

    private fun commit(d: AppData) {
        data = d
        try {
            store.save(d)
        } catch (_: Exception) {
            toast("存檔失敗，請確認手機空間")
        }
    }

    private inline fun update(f: (AppData) -> AppData) = commit(f(data))

    // ───────── 記錄 ─────────

    fun saveTxn(editId: Long?, dr: TxnDraft) {
        val d = data
        if (editId != null) {
            val old = d.txns.firstOrNull { it.id == editId } ?: return
            val t = old.copy(
                type = dr.type, amount = dr.amount, categoryId = dr.categoryId,
                accountId = dr.accountId, toAccountId = dr.toAccountId, day = dr.day,
                note = dr.note, tags = dr.tags,
                fee = dr.fee, discount = if (dr.type == TxType.EXPENSE) dr.discount else 0L,
            ).let { it.withItems(if (dr.type == TxType.EXPENSE) capItems(it, dr.reimbItems) else emptyList()) }
            commit(d.copy(txns = sortTxns(d.txns.map { if (it.id == editId) t else it })))
            return
        }
        var next = d.nextId
        val bookId = d.currentBook.id
        val n = if (dr.type == TxType.EXPENSE) dr.installments.coerceIn(1, 60) else 1
        val list = ArrayList<Txn>()
        if (n == 1) {
            list.add(
                Txn(
                    id = next++, bookId = bookId, type = dr.type, amount = dr.amount,
                    categoryId = dr.categoryId, accountId = dr.accountId, toAccountId = dr.toAccountId,
                    day = dr.day, note = dr.note, tags = dr.tags,
                    fee = dr.fee, discount = if (dr.type == TxType.EXPENSE) dr.discount else 0L,
                ).let {
                    it.withItems(
                        if (dr.type == TxType.EXPENSE) capItems(it, dr.reimbItems.map { i -> i.copy(pays = emptyList(), closed = false) })
                        else emptyList()
                    )
                }
            )
        } else {
            val group = next
            val base = dr.amount / n
            val rest = dr.amount - base * n
            val start = LocalDate.ofEpochDay(dr.day)
            for (i in 0 until n) {
                list.add(
                    Txn(
                        id = next++, bookId = bookId, type = dr.type,
                        amount = base + if (i == 0) rest else 0L,
                        categoryId = dr.categoryId, accountId = dr.accountId, toAccountId = null,
                        day = start.plusMonths(i.toLong()).toEpochDay(),
                        note = dr.note, tags = dr.tags,
                        instGroup = group, instIndex = i + 1, instTotal = n,
                        fee = if (i == 0) dr.fee else 0L,
                        discount = if (i == 0) dr.discount else 0L,
                    ).let {
                        // 每個報銷對象的金額也平均分到每一期
                        val shares = dr.reimbItems.map { r ->
                            r.copy(amount = r.amount / n + if (i == 0) r.amount % n else 0L, pays = emptyList(), closed = false)
                        }
                        it.withItems(capItems(it, shares))
                    }
                )
            }
        }
        commit(d.copy(txns = sortTxns(d.txns + list), nextId = next))
        if (d.prefs.celebrate) celebrateTick++
        if (n > 1) toast("已建立 $n 期分期，每期約 ${formatMoney(dr.amount / n)}")
    }

    /** 刪除一筆，並提供「復原」 */
    fun deleteWithUndo(id: Long) {
        val d = data
        val t = d.txns.firstOrNull { it.id == id } ?: return
        commit(d.copy(txns = d.txns.filter { it.id != id }))
        _messages.tryEmit(UiMsg("已刪除「${t.categoryId?.let { d.catMap[it]?.name } ?: "轉帳"}」", "復原") {
            val now = data
            if (now.txns.none { it.id == t.id }) commit(now.copy(txns = sortTxns(now.txns + t)))
        })
    }

    fun moveTxnDay(id: Long, day: Long) {
        val t = data.txns.firstOrNull { it.id == id } ?: return
        if (t.day == day) return
        val old = t.day
        update { d -> d.copy(txns = sortTxns(d.txns.map { if (it.id == id) it.copy(day = day) else it })) }
        val nd = LocalDate.ofEpochDay(day)
        _messages.tryEmit(UiMsg("已移到 ${nd.monthValue}/${nd.dayOfMonth}", "復原") {
            update { d -> d.copy(txns = sortTxns(d.txns.map { if (it.id == id) it.copy(day = old) else it })) }
        })
    }

    fun deleteTxn(id: Long, allInstallments: Boolean) {
        update { d ->
            val t = d.txns.firstOrNull { it.id == id } ?: return@update d
            val g = t.instGroup
            val keep = if (allInstallments && g != null) d.txns.filter { it.instGroup != g } else d.txns.filter { it.id != id }
            d.copy(txns = keep)
        }
    }

    // ───────── 報銷 ─────────

    /** 收到報銷款：每一筆收款對應一個報銷對象；收齊自動結案，沒收齊時看 chase 決定繼續追或結案不追 */
    fun receiveReimb(list: List<ReimbReceipt>, accountId: Long?, day: Long) {
        update { d ->
            d.copy(txns = d.txns.map { t ->
                val mine = list.filter { it.txnId == t.id }
                if (mine.isEmpty()) t
                else {
                    val items = t.items.toMutableList()
                    for (r in mine) {
                        val cur = items.getOrNull(r.index) ?: continue
                        if (cur.closed) continue
                        val pays = if (r.amount > 0L) cur.pays + ReimbPay(day, accountId, r.amount) else cur.pays
                        items[r.index] = cur.copy(pays = pays, closed = pays.sumOf { it.amount } >= cur.amount || !r.chase)
                    }
                    t.withItems(items)
                }
            })
        }
        toast("已收到 ${list.count { it.amount > 0L }} 筆報銷，共 ${formatMoney(list.sumOf { it.amount })}")
    }

    /** 改動某個對象的收款紀錄後，重新判斷這個對象是否收齊 */
    private fun changePays(txnId: Long, itemIndex: Int, f: (List<ReimbPay>) -> List<ReimbPay>) {
        update { d ->
            d.copy(txns = d.txns.map { t ->
                if (t.id != txnId) t
                else {
                    val items = t.items.toMutableList()
                    val cur = items.getOrNull(itemIndex)
                    if (cur == null) t
                    else {
                        val pays = f(cur.pays)
                        val received = pays.sumOf { it.amount }
                        val closed = when {
                            received >= cur.amount -> true
                            cur.received >= cur.amount -> false
                            else -> cur.closed
                        }
                        items[itemIndex] = cur.copy(pays = pays, closed = closed)
                        t.withItems(items)
                    }
                }
            })
        }
    }

    /** 修改一筆收款（金額、帳戶、日期） */
    fun editReimbPay(txnId: Long, itemIndex: Int, payIndex: Int, pay: ReimbPay) {
        changePays(txnId, itemIndex) { list -> list.toMutableList().also { if (payIndex in it.indices) it[payIndex] = pay } }
        toast("已修改這筆收款")
    }

    /** 刪除一筆收款，並提供「復原」 */
    fun deleteReimbPay(txnId: Long, itemIndex: Int, payIndex: Int) {
        val old = data.txns.firstOrNull { it.id == txnId } ?: return
        changePays(txnId, itemIndex) { list -> list.filterIndexed { i, _ -> i != payIndex } }
        _messages.tryEmit(UiMsg("已刪除這筆收款", "復原") {
            val now = data
            if (now.txns.any { it.id == txnId }) commit(now.copy(txns = now.txns.map { if (it.id == txnId) old else it }))
        })
    }

    /** 清掉這筆的收款紀錄，全部改回待報銷 */
    fun resetReimb(id: Long) {
        update { d ->
            d.copy(txns = d.txns.map {
                if (it.id == id && it.reimb != 0) it.withItems(it.items.map { i -> i.copy(pays = emptyList(), closed = false) }) else it
            })
        }
    }

    // ───────── 帳本 ─────────

    fun switchBook(id: Long) = update { it.copy(prefs = it.prefs.copy(bookId = id)) }

    fun saveBook(id: Long?, name: String, emoji: String, budget: Long) {
        update { d ->
            if (id == null) {
                val b = Book(d.nextId, name, emoji, budget)
                d.copy(books = d.books + b, nextId = d.nextId + 1, prefs = d.prefs.copy(bookId = b.id))
            } else {
                d.copy(books = d.books.map { if (it.id == id) it.copy(name = name, emoji = emoji, budget = budget) else it })
            }
        }
    }

    fun deleteBook(id: Long) {
        if (data.books.size <= 1) {
            toast("至少要保留一本帳本")
            return
        }
        update { d ->
            val books = d.books.filter { it.id != id }
            d.copy(
                books = books,
                txns = d.txns.filter { it.bookId != id },
                prefs = if (d.prefs.bookId == id) d.prefs.copy(bookId = books.first().id) else d.prefs,
            )
        }
    }

    /** month = null 代表設定每個月的預設預算；v < 0 代表清除該月份的個別設定 */
    fun setBudget(v: Long, month: YearMonth?) {
        val bid = data.currentBook.id
        update { d ->
            d.copy(books = d.books.map { b ->
                if (b.id != bid) b
                else if (month == null) b.copy(budget = v.coerceAtLeast(0L))
                else if (v < 0) b.copy(monthBudgets = b.monthBudgets - month.toString())
                else b.copy(monthBudgets = b.monthBudgets + (month.toString() to v))
            })
        }
    }

    // ───────── 帳戶 ─────────

    /** a 的 id 與 order 會被忽略（新增時自動產生） */
    fun saveAccount(id: Long?, a: Account) {
        update { d ->
            if (id == null) {
                val n = a.copy(id = d.nextId, order = (d.accounts.maxOfOrNull { it.order } ?: 0) + 1)
                d.copy(accounts = d.accounts + n, nextId = d.nextId + 1)
            } else {
                d.copy(accounts = d.accounts.map { if (it.id == id) a.copy(id = id, order = it.order) else it })
            }
        }
    }

    /** 有記錄在用的帳戶不能刪，改成隱藏 */
    fun deleteAccount(id: Long) {
        val used = data.txns.any { it.accountId == id || it.toAccountId == id }
        if (used) {
            update { d -> d.copy(accounts = d.accounts.map { if (it.id == id) it.copy(hidden = true) else it }) }
            toast("這個帳戶還有記錄，已改為隱藏")
            return
        }
        if (data.accounts.size <= 1) {
            toast("至少要保留一個帳戶")
            return
        }
        update { d -> d.copy(accounts = d.accounts.filter { it.id != id }) }
    }

    fun reorderAccounts(ids: List<Long>) {
        val pos = ids.withIndex().associate { it.value to it.index }
        update { d -> d.copy(accounts = d.accounts.map { a -> pos[a.id]?.let { a.copy(order = it) } ?: a }) }
    }

    fun reorderCategories(ids: List<Long>) {
        val pos = ids.withIndex().associate { it.value to it.index }
        update { d -> d.copy(categories = d.categories.map { c -> pos[c.id]?.let { c.copy(order = it) } ?: c }) }
    }

    fun moveAccount(id: Long, up: Boolean) {
        update { d ->
            val list = d.accounts.sortedBy { it.order }.toMutableList()
            val i = list.indexOfFirst { it.id == id }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j < 0 || j >= list.size) return@update d
            val a = list[i]; list[i] = list[j]; list[j] = a
            d.copy(accounts = list.mapIndexed { idx, acc -> acc.copy(order = idx) })
        }
    }

    // ───────── 分類 ─────────

    fun saveCategory(id: Long?, name: String, emoji: String, color: Int, kind: TxType, parentId: Long?) {
        update { d ->
            if (id == null) {
                val order = d.categories.count { it.kind == kind && it.parentId == parentId }
                val c = Category(d.nextId, name, emoji, color, kind, parentId, order)
                d.copy(categories = d.categories + c, nextId = d.nextId + 1)
            } else {
                d.copy(categories = d.categories.map {
                    if (it.id == id) it.copy(name = name, emoji = emoji, color = color)
                    else if (it.parentId == id) it.copy(color = color) else it
                })
            }
        }
    }

    /** 子分類刪除後，記錄移回上層分類；上層分類有記錄時不能刪 */
    fun deleteCategory(id: Long): Boolean {
        val d = data
        val c = d.catMap[id] ?: return false
        if (c.parentId != null) {
            val pid = c.parentId
            commit(
                d.copy(
                    categories = d.categories.filter { it.id != id },
                    txns = d.txns.map { if (it.categoryId == id) it.copy(categoryId = pid) else it },
                    templates = d.templates.map { if (it.categoryId == id) it.copy(categoryId = pid) else it },
                )
            )
            return true
        }
        val ids = setOf(id) + d.childrenOf(id).map { it.id }
        val used = d.txns.count { it.categoryId in ids }
        if (used > 0) {
            toast("還有 $used 筆記錄使用這個分類，請先把它們改到別的分類")
            return false
        }
        if (d.topCategories(c.kind).size <= 1) {
            toast("至少要保留一個分類")
            return false
        }
        commit(
            d.copy(
                categories = d.categories.filter { it.id !in ids },
                templates = d.templates.filter { it.categoryId !in ids },
            )
        )
        return true
    }

    fun moveCategory(id: Long, up: Boolean) {
        update { d ->
            val c = d.catMap[id] ?: return@update d
            val siblings = d.categories.filter { it.kind == c.kind && it.parentId == c.parentId }.sortedBy { it.order }.toMutableList()
            val i = siblings.indexOfFirst { it.id == id }
            val j = if (up) i - 1 else i + 1
            if (i < 0 || j < 0 || j >= siblings.size) return@update d
            val a = siblings[i]; siblings[i] = siblings[j]; siblings[j] = a
            val newOrder = siblings.mapIndexed { idx, s -> s.id to idx }.toMap()
            d.copy(categories = d.categories.map { cat -> newOrder[cat.id]?.let { cat.copy(order = it) } ?: cat })
        }
    }

    // ───────── 常用範本 ─────────

    fun addTemplate(name: String, dr: TxnDraft) {
        update { d ->
            val t = Template(d.nextId, name, dr.type, dr.amount, dr.categoryId, dr.accountId, dr.note, dr.tags)
            d.copy(templates = d.templates + t, nextId = d.nextId + 1)
        }
        toast("已存成常用：$name")
    }

    /** id = null 代表新增 */
    fun saveTemplate(id: Long?, name: String, dr: TxnDraft) {
        update { d ->
            if (id == null) {
                val t = Template(d.nextId, name, dr.type, dr.amount, dr.categoryId, dr.accountId, dr.note, dr.tags)
                d.copy(templates = d.templates + t, nextId = d.nextId + 1)
            } else {
                d.copy(templates = d.templates.map {
                    if (it.id == id) Template(id, name, dr.type, dr.amount, dr.categoryId, dr.accountId, dr.note, dr.tags) else it
                })
            }
        }
    }

    fun renameTemplate(id: Long, name: String) =
        update { d -> d.copy(templates = d.templates.map { if (it.id == id) it.copy(name = name) else it }) }

    fun deleteTemplate(id: Long) = update { d -> d.copy(templates = d.templates.filter { it.id != id }) }

    // ───────── 外觀 ─────────

    fun setPalette(key: String) = update { it.copy(prefs = it.prefs.copy(palette = key)) }
    fun setMascot(key: String) = update {
        it.copy(prefs = it.prefs.copy(mascot = key, mascotLast = if (key != "none") key else it.prefs.mascotLast))
    }

    /** 開關吉祥物：關掉時記住目前選的，打開時還原 */
    fun setMascotOn(on: Boolean) = update {
        val p = it.prefs
        it.copy(
            prefs = if (on) p.copy(mascot = p.mascotLast.ifBlank { "deer" }.takeIf { k -> k != "none" } ?: "deer")
            else p.copy(mascot = "none", mascotLast = if (p.mascot != "none") p.mascot else p.mascotLast)
        )
    }
    fun setMascotName(name: String) = update { it.copy(prefs = it.prefs.copy(mascotName = name.take(12))) }
    fun setDark(mode: Int) = update { it.copy(prefs = it.prefs.copy(dark = mode)) }
    fun setCelebrate(on: Boolean) = update { it.copy(prefs = it.prefs.copy(celebrate = on)) }

    // ───────── 資料 ─────────

    fun exportCsv(): ByteArray = CsvIO.export(data)

    fun exportBackup(): ByteArray = Codec.encode(data).toByteArray(Charsets.UTF_8)

    fun restoreBackup(text: String): Boolean {
        return try {
            val d = Codec.decode(text)
            commit(d)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun importCsv(text: String): Int {
        return try {
            val (d, n) = CsvIO.import(data, text)
            if (n > 0) commit(d)
            n
        } catch (_: Exception) {
            -1
        }
    }
}
