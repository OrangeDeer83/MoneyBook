package tw.moneybook.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalDate
import java.time.YearMonth

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
)

class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app.filesDir)

    var data by mutableStateOf(store.load())
        private set
    var month by mutableStateOf(YearMonth.now())
    var celebrateTick by mutableIntStateOf(0)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun toast(msg: String) {
        _messages.tryEmit(msg)
    }

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
            )
            commit(d.copy(txns = sortTxns(d.txns.map { if (it.id == editId) t else it })))
            return
        }
        var next = d.nextId
        val bookId = d.currentBook.id
        val n = if (dr.type == TxType.EXPENSE) dr.installments.coerceIn(1, 60) else 1
        val list = ArrayList<Txn>()
        if (n == 1) {
            list.add(
                Txn(next++, bookId, dr.type, dr.amount, dr.categoryId, dr.accountId, dr.toAccountId, dr.day, dr.note, dr.tags)
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
                    )
                )
            }
        }
        commit(d.copy(txns = sortTxns(d.txns + list), nextId = next))
        if (d.prefs.celebrate) celebrateTick++
        if (n > 1) toast("已建立 $n 期分期，每期約 ${formatMoney(dr.amount / n)}")
    }

    fun deleteTxn(id: Long, allInstallments: Boolean) {
        update { d ->
            val t = d.txns.firstOrNull { it.id == id } ?: return@update d
            val g = t.instGroup
            val keep = if (allInstallments && g != null) d.txns.filter { it.instGroup != g } else d.txns.filter { it.id != id }
            d.copy(txns = keep)
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

    fun setBudget(v: Long) {
        val bid = data.currentBook.id
        update { d -> d.copy(books = d.books.map { if (it.id == bid) it.copy(budget = v) else it }) }
    }

    // ───────── 帳戶 ─────────

    fun saveAccount(id: Long?, name: String, emoji: String, type: AccountType, initial: Long, hidden: Boolean) {
        update { d ->
            if (id == null) {
                val a = Account(d.nextId, name, emoji, type, initial, (d.accounts.maxOfOrNull { it.order } ?: 0) + 1)
                d.copy(accounts = d.accounts + a, nextId = d.nextId + 1)
            } else {
                d.copy(accounts = d.accounts.map {
                    if (it.id == id) it.copy(name = name, emoji = emoji, type = type, initial = initial, hidden = hidden) else it
                })
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

    fun renameTemplate(id: Long, name: String) =
        update { d -> d.copy(templates = d.templates.map { if (it.id == id) it.copy(name = name) else it }) }

    fun deleteTemplate(id: Long) = update { d -> d.copy(templates = d.templates.filter { it.id != id }) }

    // ───────── 外觀 ─────────

    fun setPalette(key: String) = update { it.copy(prefs = it.prefs.copy(palette = key)) }
    fun setMascot(key: String) = update { it.copy(prefs = it.prefs.copy(mascot = key)) }
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
