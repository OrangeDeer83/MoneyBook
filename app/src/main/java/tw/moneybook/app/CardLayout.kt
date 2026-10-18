package tw.moneybook.app

/**
 * 統計頁（月・年・區間、趨勢）與總資產頁的卡片排版。
 * 一頁由上到下是一排一排：整排放一張；半排分左右兩格，其中一格可以是空位（統計頁上就是空白）。
 * 只有小卡片（canHalf）能半排；隱藏的卡片另外記在 hidden。存檔時每一排寫成一個字串（見 encode）。
 */
data class CardRow(val left: String?, val right: String? = null, val half: Boolean = false) {
    val ids: List<String> get() = listOfNotNull(left, right)
}

data class CardLayout(val rows: List<CardRow>, val hidden: List<String> = emptyList()) {
    /** 存檔用：整排 "f:id"、半排 "h:左|右"（空位寫空字串）、隱藏 "x:id" */
    fun encode(): List<String> =
        rows.map { if (it.half) "h:${it.left ?: ""}|${it.right ?: ""}" else "f:${it.left ?: ""}" } + hidden.map { "x:$it" }

    /** 這張卡片在第幾排（隱藏或找不到是 -1） */
    fun rowOf(id: String): Int = rows.indexOfFirst { id in it.ids }

    companion object {
        fun decode(list: List<String>): CardLayout {
            val rows = ArrayList<CardRow>()
            val hidden = ArrayList<String>()
            for (s in list) {
                val body = s.drop(2)
                when (s.take(2)) {
                    "f:" -> rows.add(CardRow(body.ifEmpty { null }))
                    "h:" -> {
                        val p = body.split('|')
                        rows.add(CardRow(p.getOrNull(0)?.ifEmpty { null }, p.getOrNull(1)?.ifEmpty { null }, true))
                    }
                    "x:" -> if (body.isNotEmpty()) hidden.add(body)
                }
            }
            return CardLayout(rows, hidden)
        }
    }
}

/** 一種卡片：canHalf＝可以半排（數字、短條這種小卡片；大圖表只能整排） */
class CardSpec(val id: String, val name: String, val canHalf: Boolean = false)

/** 一頁可以排的卡片與預設排版 */
class CardPage(val key: String, val title: String, val specs: List<CardSpec>, val default: CardLayout) {
    fun spec(id: String): CardSpec? = specs.firstOrNull { it.id == id }
    fun name(id: String): String = spec(id)?.name ?: id
    fun canHalf(id: String): Boolean = spec(id)?.canHalf == true

    /**
     * 整理存起來的排版：拿掉不認識或重複的卡片、不能半排的卡片改成自己一整排、空的排拿掉；
     * 舊排版沒有的新卡片：預設隱藏的放進隱藏區，其他加到最下面。沒有存過就用預設排版。
     */
    fun normalize(saved: CardLayout?): CardLayout {
        if (saved == null) return default
        val seen = HashSet<String>()
        fun take(id: String?): String? = id?.takeIf { spec(it) != null && seen.add(it) }
        val rows = ArrayList<CardRow>()
        for (r in saved.rows) {
            if (!r.half) {
                take(r.left)?.let { rows.add(CardRow(it)) }
                continue
            }
            var a = take(r.left)
            var b = take(r.right)
            if (a != null && !canHalf(a)) { rows.add(CardRow(a)); a = null }
            val fullB = b?.takeIf { !canHalf(it) }
            if (fullB != null) b = null
            if (a != null || b != null) rows.add(CardRow(a, b, true))
            fullB?.let { rows.add(CardRow(it)) }
        }
        val hidden = saved.hidden.mapNotNull { take(it) }.toMutableList()
        for (s in specs) {
            if (!seen.add(s.id)) continue
            if (s.id in default.hidden) hidden.add(s.id) else rows.add(CardRow(s.id))
        }
        return CardLayout(rows, hidden)
    }

    /** 隱藏一張卡片：從排版拿掉（半排的另一張留在原本那一邊），放進隱藏區最後面 */
    fun hide(l: CardLayout, id: String): CardLayout {
        if (id in l.hidden) return l
        val d = detach(l, id)
        return CardLayout(compact(d.rows), d.hidden + id)
    }

    /** 把隱藏的卡片加回最下面（自己一整排） */
    fun show(l: CardLayout, id: String): CardLayout =
        if (id !in l.hidden) l else CardLayout(l.rows + CardRow(id), l.hidden - id)

    /**
     * 改寬度：半排 → 原地變成落單的半排（靠左）；整排 → 從半排拆出來自己一整排，
     * 另一張留在原本那一邊（原本在左邊的整排放在前面，在右邊的放在後面）。
     */
    fun setHalf(l: CardLayout, id: String, half: Boolean): CardLayout {
        val i = l.rowOf(id)
        if (i < 0) return l
        val r = l.rows[i]
        if (half == r.half || (half && !canHalf(id))) return l
        val rows = l.rows.toMutableList()
        if (half) {
            rows[i] = CardRow(id, null, true)
        } else {
            rows.removeAt(i)
            val rest = if (r.left == id) CardRow(null, r.right, true) else CardRow(r.left, null, true)
            val add = if (r.left == id) listOf(CardRow(id), rest) else listOf(rest, CardRow(id))
            rows.addAll(i, add.filter { it.ids.isNotEmpty() })
        }
        return l.copy(rows = rows)
    }

    /** 半排靠左或靠右：落單的直接換邊；和另一張並排的就兩張對調 */
    fun setSide(l: CardLayout, id: String, right: Boolean): CardLayout {
        val i = l.rowOf(id)
        if (i < 0) return l
        val r = l.rows[i]
        if (!r.half || (r.right == id) == right) return l
        val rows = l.rows.toMutableList()
        rows[i] = CardRow(r.right, r.left, true)
        return l.copy(rows = rows)
    }

    /**
     * 拖到兩排之間：在原本排版的第 index 排前面插入新的一排（index＝排數就是最下面）。
     * 原本是半排的維持半排、同一邊；整排或從隱藏區拖出來的是整排。
     */
    fun moveToRow(l: CardLayout, id: String, index: Int): CardLayout {
        val from = l.rowOf(id)
        val wasHalf = from >= 0 && l.rows[from].half
        val onRight = from >= 0 && l.rows[from].right == id
        val d = detach(l, id)
        val rows = d.rows.toMutableList()
        val row = if (wasHalf && canHalf(id)) CardRow(if (onRight) null else id, if (onRight) id else null, true) else CardRow(id)
        rows.add(index.coerceIn(0, rows.size), row)
        return CardLayout(compact(rows), d.hidden)
    }

    /** 拖進半排的空位（只有能半排的卡片可以）：row 是原本排版的第幾排，right＝右邊那一格 */
    fun moveToSlot(l: CardLayout, id: String, row: Int, right: Boolean): CardLayout {
        if (!canHalf(id) || row !in l.rows.indices) return l
        val d = detach(l, id)
        val r = d.rows[row]
        if (!r.half || (if (right) r.right else r.left) != null) return l
        val rows = d.rows.toMutableList()
        rows[row] = if (right) r.copy(right = id) else r.copy(left = id)
        return CardLayout(compact(rows), d.hidden)
    }

    /** 把卡片從原本的位置拿起來：那一格變空的（排數不變，之後再 compact），也從隱藏區拿掉 */
    private fun detach(l: CardLayout, id: String): CardLayout = CardLayout(
        l.rows.map { r ->
            when {
                r.left == id -> r.copy(left = null)
                r.right == id -> r.copy(right = null)
                else -> r
            }
        },
        l.hidden - id,
    )

    private fun compact(rows: List<CardRow>): List<CardRow> = rows.filter { it.ids.isNotEmpty() }
}

object CardPages {
    /** 統計 → 月、年、區間（共用一份） */
    val PERIOD = CardPage(
        "period", "月・年・區間",
        listOf(
            CardSpec("sum", "收支", canHalf = true),
            CardSpec("cat", "分類（圓環圖＋清單）"),
            CardSpec("yearBars", "每月收支（只有年）"),
        ),
        CardLayout(listOf(CardRow("sum"), CardRow("cat"), CardRow("yearBars"))),
    )

    /** 統計 → 趨勢 */
    val TREND = CardPage(
        "trend", "趨勢",
        listOf(
            CardSpec("saved", "這個月存下", canHalf = true),
            CardSpec("savings", "存錢趨勢"),
            CardSpec("bars", "近幾個月收支"),
            CardSpec("months", "每月明細"),
        ),
        CardLayout(listOf(CardRow("savings"), CardRow("bars"), CardRow("months")), hidden = listOf("saved")),
    )

    /** 總資產頁 */
    val ASSET = CardPage(
        "asset", "總資產",
        listOf(
            CardSpec("compose", "現在的組成"),
            CardSpec("change", "總資產變化"),
            CardSpec("reasons", "變化的原因"),
            CardSpec("share", "投資佔資產的比例"),
        ),
        CardLayout(listOf(CardRow("compose"), CardRow("change"), CardRow("reasons")), hidden = listOf("share")),
    )

    val all: List<CardPage> = listOf(PERIOD, TREND, ASSET)
    fun of(key: String): CardPage? = all.firstOrNull { it.key == key }
}
