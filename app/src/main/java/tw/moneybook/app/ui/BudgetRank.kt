package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import tw.moneybook.app.AppData
import tw.moneybook.app.Drill
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Txn
import tw.moneybook.app.budgetRankCats
import tw.moneybook.app.budgetTopTxns
import tw.moneybook.app.catAndKids
import tw.moneybook.app.countsInBudget
import tw.moneybook.app.refundInBudget
import tw.moneybook.app.statCat
import tw.moneybook.app.firstMonthOf
import tw.moneybook.app.formatMoney
import tw.moneybook.app.monthlySums
import tw.moneybook.app.monthsEnding
import tw.moneybook.app.trendStat

/**
 * 首頁點預算卡：這個月算預算的支出花在哪。最上面是預算（點了換成設定預算的視窗，存好回到這裡），
 * 「依分類」大分類由多到少（點一列直接打開那個分類的下鑽頁），「單筆最大」前 10 筆（點了修改）。
 */
@Composable
fun BudgetRankDialog(
    vm: MoneyViewModel,
    month: YearMonth,
    list: List<Txn>,
    tab: Int,
    onTab: (Int) -> Unit,
    onBudget: () -> Unit,
    onEdit: (Long) -> Unit,
    onDrill: () -> Unit,
    onDismiss: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    val budget = d.currentBook.budgetFor(month)
    val spent = remember(d, list) { list.filter { d.countsInBudget(it) || d.refundInBudget(it) }.sumOf { it.spent } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${month.monthValue} 月錢花在哪") },
        text = {
            ChartPickArea {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "算預算的 ${formatMoney(spent)}" + when {
                            budget <= 0L -> ""
                            spent > budget -> "・超出 ${formatMoney(spent - budget)}"
                            else -> "・還剩 ${formatMoney(budget - spent)}"
                        },
                        style = MaterialTheme.typography.bodySmall, color = if (budget in 1 until spent) cute.expense else cute.sub,
                    )
                    // 預算：點了換成設定預算的視窗（不疊兩層），存好回到這裡
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cute.soft).clickable(onClick = onBudget)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (budget > 0L) "預算 ${formatMoney(budget)}" else "還沒設定預算", modifier = Modifier.weight(1f))
                        Text(if (budget > 0L) "點這裡修改  ›" else "點這裡設定  ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    PillSegment(listOf("依分類", "單筆最大"), tab, onTab)
                    if (tab == 0) {
                        val rank = remember(d, list) { d.budgetRankCats(list) }
                        if (rank.isEmpty()) Text("這個月還沒有算預算的支出", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        val top = rank.firstOrNull()?.second ?: 1L
                        rank.forEachIndexed { i, (cid, v) ->
                            val c = cid?.let { d.catMap[it] }
                            // 點一列：直接打開那個分類這個月的明細（下鑽頁）
                            Column(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = c != null) {
                                        if (c != null) {
                                            vm.drill = Drill(iconLabel(c.emoji, c.name), 0, rankPred(d, c.id), { t -> t.spent }, c.id)
                                            onDrill()
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.width(18.dp))
                                    CatBubble(c?.emoji ?: "img:cat_box", c?.color ?: 8, 28.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(c?.name ?: "未分類", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(formatMoney(v), fontWeight = FontWeight.SemiBold)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("${if (spent > 0) Math.round(v * 100.0 / spent) else 0}%", style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.width(36.dp))
                                            RatioBar(v.toFloat() / top.toFloat(), catColor(c?.color ?: 8), Modifier.weight(1f))
                                        }
                                    }
                                    if (c != null) {
                                        Icon(
                                            AppIcons.ChevronRight, contentDescription = "看${c.name}的明細",
                                            tint = cute.sub, modifier = Modifier.padding(start = 4.dp).size(20.dp),
                                        )
                                    } else Spacer(Modifier.width(24.dp))
                                }
                            }
                        }
                    } else {
                        val top = remember(d, list) { d.budgetTopTxns(list) }
                        if (top.isEmpty()) Text("這個月還沒有算預算的支出", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        top.forEachIndexed { i, t ->
                            val c = t.categoryId?.let { d.catMap[it] }
                            val sub = listOf("${t.date.monthValue}/${t.date.dayOfMonth}", t.note.lineSequence().firstOrNull()?.trim().orEmpty()).filter { it.isNotEmpty() }.joinToString("・")
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onEdit(t.id) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.width(22.dp))
                                CatBubble(c?.emoji ?: "img:cat_box", c?.color ?: 8, 28.dp)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(catPath(d, t), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(sub, style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(formatMoney(t.spent), color = cute.expense, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (top.isNotEmpty()) Text("前 10 筆；點一筆可以修改", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}

/** 這個分類（含子分類）算預算的支出 */
private fun rankPred(d: AppData, id: Long): (Txn) -> Boolean {
    val ids = d.catAndKids(id)
    return { t -> d.statCat(t)?.let { it in ids } == true && (d.countsInBudget(t) || d.refundInBudget(t)) }
}
