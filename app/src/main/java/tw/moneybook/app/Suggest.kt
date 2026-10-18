package tw.moneybook.app

/**
 * 記一筆備註的建議：所有帳本裡用過 minCount 次以上的備註（只取第一行），
 * 同一個分類用過的排前面，再來照用過的次數、最近用的日期。
 */
fun AppData.noteSuggestions(categoryId: Long?, minCount: Int = 2): List<String> {
    class Stat(var n: Int = 0, var inCat: Int = 0, var last: Long = Long.MIN_VALUE)
    val m = LinkedHashMap<String, Stat>()
    for (t in txns) {
        if (t.adjust) continue
        val note = t.note.trim().lines().firstOrNull()?.trim().orEmpty()
        if (note.isEmpty() || note.length > 30) continue
        val s = m.getOrPut(note) { Stat() }
        s.n++
        if (categoryId != null && t.categoryId == categoryId) s.inCat++
        if (t.day > s.last) s.last = t.day
    }
    return m.entries.filter { it.value.n >= minCount }
        .sortedWith(
            compareByDescending<Map.Entry<String, Stat>> { if (it.value.inCat > 0) 1 else 0 }
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
