package tw.moneybook.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 記一筆的「欠款」：欠誰。欠款是別人先墊、我還沒付的支出，記成一筆支出但先不從任何帳戶扣，還款時才扣（見 Debt.kt）。
 * names：以前欠過的人（點一下帶入）；locked：已經有還款記錄，只能改欠誰、不能取消欠款；onClear 不是 null 時多一個「取消欠款」。
 */
@Composable
fun DebtWhoDialog(
    names: List<String>,
    initial: String,
    locked: Boolean,
    onConfirm: (String) -> Unit,
    onClear: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val cute = LocalCute.current
    var who by remember { mutableStateOf(initial) }
    val tries = rememberNeedTries()
    val need = if (who.isBlank()) Need("who", "請輸入欠誰") else null
    val nv = NeedView(need, tries.count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("欠款") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "別人先墊、我還沒付的錢：記成一筆支出（統計照這天算），但先不從任何帳戶扣，之後在「欠款」頁還款時才扣。",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                OutlinedTextField(
                    who, { who = it.take(16) },
                    label = { Text("欠誰") }, singleLine = true,
                    isError = nv.on("who"), supportingText = nv.supporting("who"),
                    modifier = Modifier.fillMaxWidth().needInView(nv, "who"),
                )
                if (names.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        names.take(10).forEach { n -> CuteChip(n, who.trim() == n, { who = n }) }
                    }
                }
                if (locked) {
                    Text("這筆已經有還款記錄：金額不能改，也不能取消欠款；要改請先到「欠款」頁刪掉還款。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (need != null) tries.count++ else onConfirm(who.trim()) }) { Text("確定") } },
        dismissButton = {
            Row {
                if (onClear != null && !locked) TextButton(onClick = onClear) { Text("取消欠款") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
