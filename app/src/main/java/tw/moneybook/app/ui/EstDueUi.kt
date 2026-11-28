package tw.moneybook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import tw.moneybook.app.Money
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.Txn
import tw.moneybook.app.estName
import tw.moneybook.app.formatMoney
import tw.moneybook.app.isTestEst

/**
 * 首頁「試刷」膠囊展開的那張（草圖 ci-out/noti_test_charge3.png ②③）：預估放太久還沒填實際金額的（見 EstDue.kt），
 * 每一筆「填金額」（小算盤，改成實際金額、不再是預估）、「刪掉」（可以復原）。
 */
@Composable
fun EstDueCard(vm: MoneyViewModel, due: List<Txn>) {
    val d = vm.data
    val cute = LocalCute.current
    val today = LocalDate.now().toEpochDay()
    var fill by remember { mutableStateOf<Txn?>(null) }
    CuteCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Text(
            if (due.all { it.isTestEst }) "${due.size} 筆試刷還沒填實際金額" else "${due.size} 筆預估的刷卡還沒填實際金額",
            style = MaterialTheme.typography.titleSmall,
        )
        due.forEach { t ->
            val c = t.categoryId?.let { d.catMap[it] }
            val name = d.estName(t)
            val date = LocalDate.ofEpochDay(t.day)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBubble(c?.emoji ?: "img:cat_box", c?.color ?: 8, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                t.accountId?.let { d.accMap[it]?.name }, "${date.monthValue}/${date.dayOfMonth}", c?.name,
                                if (t.isTestEst) null else "記了 ${today - t.day} 天還沒請款",
                            ).joinToString("・"),
                            style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        (if (t.isTestEst) "" else "預估 ") + formatMoney(t.amount),
                        color = cute.expense, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Row(Modifier.padding(start = 44.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { fill = t },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).semantics { contentDescription = "填金額 $name" },
                    ) { Text("填金額", style = MaterialTheme.typography.labelLarge) }
                    SoftButton("刪掉", { vm.deleteWithUndo(t.id) }, compact = true)
                }
            }
        }
    }
    fill?.let { t ->
        AmountKeypadDialog(
            "${d.estName(t)} 實際金額", "", Money.twd, 0, 8,
            onDone = { v ->
                fill = null
                v.filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }?.let { vm.fillEst(t.id, it) }
            },
            onDismiss = { fill = null },
        )
    }
}
