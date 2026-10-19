package tw.moneybook.app

/**
 * 記一筆備註的建議：只列這個分類用過 minCount 次以上的備註（只取第一行，所有帳本）。
 * 選的是大分類時，連它底下的小分類一起算，記在大分類本身的排前面；沒有分類（轉帳）就列同一種記錄沒有分類的。
 * 排序：這個分類本身用過的優先，再來照用過的次數、最近用的日期。
 */
fun AppData.noteSuggestions(categoryId: Long?, type: TxType, minCount: Int = 2): List<String> {
    class Stat(var n: Int = 0, var exact: Int = 0, var last: Long = Long.MIN_VALUE)
    val family = if (categoryId == null) emptySet() else setOf(categoryId) + childrenOf(categoryId).map { it.id }
    val m = LinkedHashMap<String, Stat>()
    for (t in txns) {
        if (t.adjust) continue
        val fits = if (categoryId == null) t.categoryId == null && t.type == type else t.categoryId in family
        if (!fits) continue
        val note = t.note.trim().lines().firstOrNull()?.trim().orEmpty()
        if (note.isEmpty() || note.length > 30) continue
        val s = m.getOrPut(note) { Stat() }
        s.n++
        if (t.categoryId == categoryId) s.exact++
        if (t.day > s.last) s.last = t.day
    }
    return m.entries.filter { it.value.n >= minCount }
        .sortedWith(
            compareByDescending<Map.Entry<String, Stat>> { if (it.value.exact > 0) 1 else 0 }
                .thenByDescending { it.value.n }
                .thenByDescending { it.value.last }
        )
        .map { it.key }
}

/** 照打的字篩選建議（不分大小寫、包含就算）；還沒打字就全部，和打的一模一樣的不列 */
fun filterSuggestions(list: List<String>, typed: String): List<String> {
    val q = typed.trim()
    if (q.isEmpty()) return list
    return list.filter { it != q && it.contains(q, ignoreCase = true) }
}
