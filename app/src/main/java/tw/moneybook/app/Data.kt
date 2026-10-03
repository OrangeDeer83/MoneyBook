package tw.moneybook.app

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth

enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class AccountType(val label: String, val emoji: String) {
    CASH("現金", "img:acc_cash"),
    BANK("銀行", "img:acc_bank"),
    CARD("信用卡", "img:acc_card"),
    LOAN("貸款", "img:acc_receipt"),
    ECARD("電子票證", "img:acc_transit"),
    EPAY("電子支付", "img:acc_epay"),
    INVEST("投資", "img:extra_gold"),
    OTHER("其他", "img:acc_purse"),
}

data class Book(
    val id: Long,
    val name: String,
    val emoji: String,
    /** 每個月的預設預算 */
    val budget: Long = 0L,
    /** 個別月份的預算（key 是 "2026-09"），會蓋過預設值 */
    val monthBudgets: Map<String, Long> = emptyMap(),
) {
    fun budgetFor(m: YearMonth): Long = monthBudgets[m.toString()] ?: budget
}

data class Account(
    val id: Long,
    val name: String,
    val emoji: String,
    val type: AccountType,
    val initial: Long,
    val order: Int,
    val hidden: Boolean = false,
    /** 文字徽章（例如「國泰」），空白代表用表情符號 */
    val badge: String = "",
    val badgeColor: Int = 0,
    /** 信用卡：額度、結帳日、繳款日（0 代表未設定） */
    val creditLimit: Long = 0L,
    val statementDay: Int = 0,
    val dueDay: Int = 0,
    /** 手動標了星號的常用帳戶：記一筆選帳戶時固定放在最上面 */
    val favorite: Boolean = false,
)

data class Category(
    val id: Long,
    val name: String,
    val emoji: String,
    val color: Int,
    val kind: TxType,
    val parentId: Long?,
    val order: Int,
)

/** 一筆實際收到的報銷款 */
data class ReimbPay(val day: Long, val accountId: Long?, val amount: Long)

/**
 * 一個報銷對象（例如幫 5 個人付款，每個人一項）：應收金額與實際收款紀錄。
 * 可以分次收款；收到的比應收少時，可以選擇繼續追，或結案不追了（不追的部分算自己的支出）。
 */
data class ReimbItem(
    val who: String = "",
    val amount: Long,
    val pays: List<ReimbPay> = emptyList(),
    val closed: Boolean = false,
) {
    val received: Long get() = pays.sumOf { it.amount }

    /** 還沒收到的金額（結案後就沒有了） */
    val remaining: Long get() = if (closed) 0L else (amount - received).coerceAtLeast(0L)

    /** 算進「報銷、不算支出」的金額：還在追就用應收，結案後只算實際收到的 */
    val effective: Long get() = if (closed) received else amount
}

data class Txn(
    val id: Long,
    val bookId: Long,
    val type: TxType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val toAccountId: Long?,
    val day: Long,
    val note: String,
    val tags: List<String>,
    val instGroup: Long? = null,
    val instIndex: Int = 0,
    val instTotal: Int = 0,
    val fee: Long = 0L,
    val discount: Long = 0L,
    /** 0 一般, 1 待報銷, 2 已報銷 */
    val reimb: Int = 0,
    val reimbAccountId: Long? = null,
    val reimbDay: Long? = null,
    /** 可報銷／已報銷的金額（可以少於實付，代表部分報銷） */
    val reimbAmount: Long = 0L,
    /** 各報銷對象的明細；舊資料沒有這個欄位，由上面的舊欄位換算（見 items） */
    val reimbItems: List<ReimbItem> = emptyList(),
    /** 更新餘額產生的「餘額調整」：只改帳戶餘額，不算收入或支出（type 只用來表示增加或減少） */
    val adjust: Boolean = false,
) {
    val date: LocalDate get() = LocalDate.ofEpochDay(day)
    val month: YearMonth get() = YearMonth.from(LocalDate.ofEpochDay(day))

    /** 實際金額：支出 = 原價 − 優惠 + 手續費；收入 = 金額 − 手續費；轉帳 = 轉帳金額 */
    val paid: Long
        get() = when (type) {
            TxType.EXPENSE -> (amount - discount + fee).coerceAtLeast(0L)
            TxType.INCOME -> (amount - fee).coerceAtLeast(0L)
            TxType.TRANSFER -> amount
        }

    /** 報銷明細：新資料用 reimbItems，舊資料（只有單一報銷）換算成一個對象 */
    val items: List<ReimbItem>
        get() = when {
            reimbItems.isNotEmpty() -> reimbItems
            reimb == 1 -> listOf(ReimbItem("", reimbAmount))
            reimb == 2 -> listOf(ReimbItem("", reimbAmount, listOf(ReimbPay(reimbDay ?: day, reimbAccountId, reimbAmount)), true))
            else -> emptyList()
        }

    /** 還沒收到的報銷款 */
    val reimbOutstanding: Long get() = if (type == TxType.EXPENSE) items.sumOf { it.remaining } else 0L

    /** 換上新的報銷明細，並更新 reimb（0 無、1 有沒收完的、2 全部結案）與 reimbAmount（不算支出的金額） */
    fun withItems(list: List<ReimbItem>): Txn =
        if (list.isEmpty()) copy(reimb = 0, reimbAmount = 0L, reimbAccountId = null, reimbDay = null, reimbItems = emptyList())
        else copy(
            reimb = if (list.all { it.closed }) 2 else 1,
            reimbAmount = list.sumOf { it.effective },
            reimbAccountId = null, reimbDay = null,
            reimbItems = list,
        )

    /** 報銷上限：原價 + 手續費（刷卡有優惠時，對方可能還是給你原價） */
    val reimbCap: Long get() = if (type == TxType.EXPENSE) amount + fee else 0L

    /** 報銷收到的錢比實付多（例如刷卡有優惠），多出來的算收入 */
    val reimbGain: Long get() = if (type == TxType.EXPENSE && reimb == 2 && reimbAmount > paid) reimbAmount - paid else 0L

    /** 算進支出統計的金額（報銷的不算，轉帳只算手續費） */
    val spent: Long
        get() = if (adjust) 0L else when (type) {
            TxType.EXPENSE -> if (reimb == 0) paid else (paid - reimbAmount).coerceAtLeast(0L)
            TxType.TRANSFER -> fee
            TxType.INCOME -> 0L
        }
}

data class Template(
    val id: Long,
    val name: String,
    val type: TxType,
    val amount: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val note: String,
    val tags: List<String>,
)

data class Prefs(
    val bookId: Long,
    val palette: String = "milktea",
    val mascot: String = "deer",
    val mascotName: String = "",
    /** 關掉吉祥物前選的是哪一種，重新打開時還原 */
    val mascotLast: String = "deer",
    val dark: Int = 0, // 0 跟隨系統, 1 淺色, 2 深色
    val celebrate: Boolean = true,
    /** 是否允許上網抓股價（預設關閉，在持股頁按「抓最新價格」時才會問） */
    val priceFetch: Boolean = false,
    /** 上次抓價是哪一天（epoch day），用來決定要不要自動再抓 */
    val priceFetchDay: Long = 0L,
    /** 帳戶分頁收折起來的類型（AccountType 的名稱），重開 App 後仍保留 */
    val collapsedAccTypes: List<String> = emptyList(),
)

data class AppData(
    val books: List<Book>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val txns: List<Txn>,
    val templates: List<Template>,
    val prefs: Prefs,
    val nextId: Long,
    /** 投資帳戶的買賣記錄與價格（舊資料沒有這兩個欄位，就是空的） */
    val trades: List<Trade> = emptyList(),
    val prices: List<PriceSnap> = emptyList(),
) {
    val catMap: Map<Long, Category> by lazy { categories.associateBy { it.id } }
    val accMap: Map<Long, Account> by lazy { accounts.associateBy { it.id } }

    val currentBook: Book
        get() = books.firstOrNull { it.id == prefs.bookId } ?: books.first()

    /** 目前帳本的記錄（已依日期新到舊排序） */
    val bookTxns: List<Txn> by lazy {
        val bid = currentBook.id
        txns.filter { it.bookId == bid }
    }

    val visibleAccounts: List<Account>
        get() = accounts.filter { !it.hidden }.sortedBy { it.order }

    /**
     * 記一筆選帳戶時放在最上面的「常用帳戶」，手動與自動並用：
     * 1. 手動標了星號的帳戶（顯示中的）固定排最前面，照帳戶頁的順序；
     * 2. 名額（limit）還沒滿，就用自動統計的補滿：最近 [days] 天、目前帳本的使用次數多的優先，
     *    次數一樣看全部記錄的次數，再一樣照帳戶頁順序。
     * 統計時：餘額調整不算（不是真的在用），轉帳兩邊都算，分期付款同一組只算一次；沒用過的帳戶不列。
     * 星號帳戶超過 limit 時全部都列。
     */
    fun frequentAccounts(limit: Int = 3, today: LocalDate = LocalDate.now(), days: Int = 90): List<Account> {
        val since = today.toEpochDay() - days
        val bookId = currentBook.id
        val recent = HashMap<Long, Int>()
        val all = HashMap<Long, Int>()
        val seenInstallments = HashSet<Pair<Long, Long>>()
        for (t in txns) {
            if (t.adjust) continue
            val g = t.instGroup
            for (id in listOfNotNull(t.accountId, t.toAccountId)) {
                if (g != null && !seenInstallments.add(g to id)) continue
                all[id] = (all[id] ?: 0) + 1
                if (t.bookId == bookId && t.day >= since) recent[id] = (recent[id] ?: 0) + 1
            }
        }
        val starred = visibleAccounts.filter { it.favorite }
        val auto = visibleAccounts.filter { !it.favorite && (all[it.id] ?: 0) > 0 }
            .sortedWith(
                compareByDescending<Account> { recent[it.id] ?: 0 }
                    .thenByDescending { all[it.id] ?: 0 }
                    .thenBy { it.order }
            )
        return starred + auto.take((limit - starred.size).coerceAtLeast(0))
    }

    fun topCategories(kind: TxType): List<Category> =
        categories.filter { it.kind == kind && it.parentId == null }.sortedBy { it.order }

    fun childrenOf(parentId: Long): List<Category> =
        categories.filter { it.parentId == parentId }.sortedBy { it.order }

    fun topOf(c: Category): Category = c.parentId?.let { catMap[it] } ?: c

    /** 各帳戶目前餘額（所有帳本合計） */
    fun balances(): Map<Long, Long> {
        val m = HashMap<Long, Long>()
        for (a in accounts) m[a.id] = a.initial
        for (t in txns) {
            when (t.type) {
                TxType.EXPENSE -> t.accountId?.let { m[it] = (m[it] ?: 0L) - t.paid }
                TxType.INCOME -> t.accountId?.let { m[it] = (m[it] ?: 0L) + t.paid }
                TxType.TRANSFER -> {
                    t.accountId?.let { m[it] = (m[it] ?: 0L) - t.amount - t.fee }
                    t.toAccountId?.let { m[it] = (m[it] ?: 0L) + t.amount }
                }
            }
            for (p in t.items.flatMap { it.pays }) p.accountId?.let { m[it] = (m[it] ?: 0L) + p.amount }
        }
        return m
    }

    /** 過去報銷過的對象名字，最常用的排前面 */
    fun reimbNames(): List<String> =
        txns.flatMap { t -> t.items.map { it.who.trim() } }.filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun allTags(): List<String> =
        txns.flatMap { it.tags }.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.value }.map { it.key }
}

fun List<Txn>.inMonth(m: YearMonth): List<Txn> = filter { it.month == m }
fun List<Txn>.inYear(y: Int): List<Txn> = filter { it.date.year == y }
fun List<Txn>.expenseSum(): Long = sumOf { it.spent }
fun List<Txn>.incomeSum(): Long = filter { it.type == TxType.INCOME && !it.adjust }.sumOf { it.paid } + sumOf { it.reimbGain }
/** 統計用金額：支出扣掉報銷的部分，收入用實收 */
val Txn.statAmount: Long get() = if (adjust) 0L else if (type == TxType.EXPENSE) spent else paid

/** 信用卡帳單週期 */
data class CardCycle(
    val lastStatement: LocalDate, // 上次結帳日
    val nextStatement: LocalDate, // 下次結帳日
    val lastDue: LocalDate?,      // 上期帳單的繳款日
)

private fun dayIn(m: YearMonth, d: Int): LocalDate = m.atDay(d.coerceIn(1, m.lengthOfMonth()))

fun cardCycle(a: Account, today: LocalDate): CardCycle? {
    if (a.statementDay !in 1..31) return null
    val ym = YearMonth.from(today)
    val thisMonth = dayIn(ym, a.statementDay)
    val last = if (today.isAfter(thisMonth)) thisMonth else dayIn(ym.minusMonths(1), a.statementDay)
    val next = dayIn(YearMonth.from(last).plusMonths(1), a.statementDay)
    val due = if (a.dueDay in 1..31) {
        val lm = YearMonth.from(last)
        if (a.dueDay > a.statementDay) dayIn(lm, a.dueDay) else dayIn(lm.plusMonths(1), a.dueDay)
    } else null
    return CardCycle(last, next, due)
}

/** 某帳戶在一段期間的淨流出（刷卡消費 − 退款／繳款以外的流入不算） */
fun AppData.cardSpending(accId: Long, from: LocalDate, to: LocalDate): Long {
    val a = from.toEpochDay()
    val b = to.toEpochDay()
    var sum = 0L
    for (t in txns) {
        if (t.day < a || t.day > b) continue
        when (t.type) {
            TxType.EXPENSE -> if (t.accountId == accId) sum += t.paid
            TxType.INCOME -> if (t.accountId == accId) sum -= t.paid
            TxType.TRANSFER -> if (t.accountId == accId) sum += t.amount + t.fee
        }
    }
    return sum
}

/** 某帳戶在一段期間收到的轉入（例如繳卡費） */
fun AppData.transfersIn(accId: Long, from: LocalDate, to: LocalDate): Long {
    val a = from.toEpochDay()
    val b = to.toEpochDay()
    return txns.filter { it.type == TxType.TRANSFER && it.toAccountId == accId && it.day in a..b }.sumOf { it.amount }
}

fun List<Txn>.pendingReimb(): List<Txn> = filter { it.type == TxType.EXPENSE && it.reimb == 1 }

fun sortTxns(list: List<Txn>): List<Txn> =
    list.sortedWith(compareByDescending<Txn> { it.day }.thenByDescending { it.id })

private val moneyFmt: NumberFormat = NumberFormat.getIntegerInstance()

fun formatMoney(v: Long): String =
    (if (v < 0) "-$" else "$") + moneyFmt.format(kotlin.math.abs(v))

/** 日曆格子用的短格式 */
fun formatShort(v: Long): String = when {
    v >= 100_000_000L -> String.format("%.1f億", v / 100_000_000.0)
    v >= 10_000L -> String.format("%.1f萬", v / 10_000.0)
    else -> moneyFmt.format(v)
}

fun parseTags(s: String): List<String> =
    s.split(' ', ',', '，', '#', '＃', '\n', '\t', ';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(10)

object Defaults {
    val emojis: List<String> = listOf(
        "🍜", "🍱", "🥪", "🍛", "🍰", "🧋", "☕", "🍺", "🍎", "🍿",
        "🚌", "🚇", "🚕", "⛽", "🅿️", "🚲", "✈️", "🏍️", "🚗", "🎫",
        "🛍️", "👕", "👟", "💄", "📱", "💻", "🎧", "📷", "🧻", "🧴",
        "🏠", "🔑", "💡", "📶", "🛋️", "🧺", "🎮", "🎬", "🎤", "🎨",
        "💊", "🏥", "🦷", "💪", "📚", "✏️", "🎓", "🎁", "💐", "🍻",
        "🐶", "🐱", "🐰", "👶", "💼", "🏆", "📈", "🧧", "💰", "🪙",
        "💳", "🏦", "💵", "👛", "📦", "🔧", "🧾", "❤️", "⭐", "🌈",
    )

    fun create(): AppData {
        var id = 1L
        fun nid(): Long = id++

        val book = Book(nid(), "我的帳本", "img:ui_ledger", 0L)
        val cash = Account(nid(), "現金", "img:acc_cash", AccountType.CASH, 0L, 0)
        val cats = ArrayList<Category>()

        fun parent(name: String, emoji: String, color: Int, kind: TxType, subs: List<Pair<String, String>>) {
            val order = cats.count { it.parentId == null && it.kind == kind }
            val p = Category(nid(), name, emoji, color, kind, null, order)
            cats.add(p)
            subs.forEachIndexed { i, s -> cats.add(Category(nid(), s.first, s.second, color, kind, p.id, i)) }
        }

        val e = TxType.EXPENSE
        parent("餐飲", "img:cat_food", 0, e, listOf("早餐" to "img:cat_breakfast", "午餐" to "img:cat_lunch", "晚餐" to "img:cat_dinner", "飲料" to "img:cat_drink", "點心" to "img:cat_snack"))
        parent("交通", "img:cat_transport", 2, e, listOf("大眾運輸" to "img:cat_metro", "計程車" to "img:cat_taxi", "加油" to "img:cat_fuel", "停車" to "img:cat_parking"))
        parent("購物", "img:cat_shopping", 3, e, listOf("衣物" to "img:cat_clothes", "3C" to "img:cat_3c", "美妝" to "img:cat_beauty"))
        parent("日用", "img:cat_daily", 1, e, emptyList())
        parent("居住", "img:cat_home", 5, e, listOf("房租" to "img:cat_rent", "水電" to "img:cat_utility", "網路" to "img:cat_internet"))
        parent("娛樂", "img:cat_game", 4, e, listOf("電影" to "img:cat_movie", "遊戲" to "img:cat_joystick", "旅遊" to "img:cat_travel"))
        parent("醫療", "img:cat_medical", 6, e, emptyList())
        parent("教育", "img:cat_education", 7, e, emptyList())
        parent("社交", "img:cat_social", 9, e, emptyList())
        parent("寵物", "img:cat_pet", 8, e, emptyList())
        parent("其他", "img:cat_other", 8, e, emptyList())

        val i = TxType.INCOME
        parent("薪水", "img:cat_salary", 5, i, emptyList())
        parent("獎金", "img:cat_bonus", 1, i, emptyList())
        parent("投資", "img:extra_gold", 2, i, emptyList())
        parent("兼職", "img:cat_parttime", 4, i, emptyList())
        parent("零用錢", "img:cat_redpacket", 6, i, emptyList())
        parent("其他", "img:cat_moneybag", 8, i, emptyList())

        return AppData(
            books = listOf(book),
            accounts = listOf(cash),
            categories = cats,
            txns = emptyList(),
            templates = emptyList(),
            prefs = Prefs(bookId = book.id),
            nextId = id + 100,
        )
    }
}
