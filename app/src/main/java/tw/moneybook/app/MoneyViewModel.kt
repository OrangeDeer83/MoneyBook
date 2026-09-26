package tw.moneybook.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.time.YearMonth

class MoneyViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(File(app.filesDir, "moneybook.json"))

    var txns by mutableStateOf<List<Txn>>(emptyList())
        private set
    var budget by mutableStateOf(0L)
        private set
    var month by mutableStateOf(YearMonth.now())

    init {
        val (list, b) = store.load()
        txns = sortTxns(list)
        budget = b
    }

    private fun sortTxns(list: List<Txn>): List<Txn> =
        list.sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.id })

    private fun persist() {
        try {
            store.save(txns, budget)
        } catch (_: Exception) {
        }
    }

    fun upsert(t: Txn) {
        txns = sortTxns(txns.filter { it.id != t.id } + t)
        persist()
    }

    fun delete(id: Long) {
        txns = txns.filter { it.id != id }
        persist()
    }

    fun updateBudget(v: Long) {
        budget = v
        persist()
    }

    fun txnsOf(m: YearMonth): List<Txn> = txns.filter { YearMonth.from(it.date) == m }

    fun expenseOf(m: YearMonth): Long = txnsOf(m).filter { it.isExpense }.sumOf { it.amount }

    fun incomeOf(m: YearMonth): Long = txnsOf(m).filter { !it.isExpense }.sumOf { it.amount }

    /** 存檔後呼叫：若該月支出接近或超過預算，回傳提醒文字 */
    fun budgetAlert(m: YearMonth): String? {
        if (budget <= 0) return null
        val spent = expenseOf(m)
        return when {
            spent > budget -> "⚠️ ${m.monthValue} 月已超出預算 ${formatMoney(spent - budget)}"
            spent * 100 >= budget * 80 -> "提醒：${m.monthValue} 月預算只剩 ${formatMoney(budget - spent)}"
            else -> null
        }
    }
}
