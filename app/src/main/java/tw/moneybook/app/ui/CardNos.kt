package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.Account
import tw.moneybook.app.CardNo
import java.time.LocalDate

/** 末四碼的備註常用選項 */
private val CARD_NO_NOTES = listOf("實體卡", "手機", "舊卡")

/**
 * 信用卡編輯頁的「卡號末四碼」：一組一個小卡片（數字＋備註，點 × 刪掉），「＋ 新增」打開輸入視窗。
 * others：其他帳戶（新增時檢查這組是不是已經在別張卡）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardNoSection(nos: List<CardNo>, selfId: Long, others: List<Account>, onChange: (List<CardNo>) -> Unit) {
    val cute = LocalCute.current
    var adding by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft.copy(alpha = 0.45f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("卡號末四碼", style = MaterialTheme.typography.titleSmall)
        Text("刷卡通知用來分辨是哪張卡，可以不填", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nos.forEach { n ->
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(cute.card).border(1.dp, cute.soft, RoundedCornerShape(16.dp))
                        .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(n.digits, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (n.note.isNotEmpty()) Text("  " + n.note, style = MaterialTheme.typography.labelSmall, color = cute.sub)
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).clickable { onChange(nos - n) }
                            .semantics { contentDescription = "刪掉末四碼 ${n.digits}" },
                        contentAlignment = Alignment.Center,
                    ) { Text("×", color = cute.sub, fontSize = 16.sp) }
                }
            }
            SoftButton("＋ 新增", { adding = true }, compact = true)
        }
        nos.filter { it.learned > 0L }.forEach { n ->
            val day = LocalDate.ofEpochDay(n.learned)
            Text("${n.digits} 是 ${day.monthValue}/${day.dayOfMonth} 從通知學的", style = MaterialTheme.typography.labelSmall, color = cute.sub)
        }
    }
    if (adding) {
        CardNoAddDialog(nos, selfId, others, onAdd = { onChange(nos + it); adding = false }, onDismiss = { adding = false })
    }
}

/** 新增一組末四碼：4 格數字、備註（常用的點一下，或自己打）；這組在別張卡時先問要不要移過來 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardNoAddDialog(nos: List<CardNo>, selfId: Long, others: List<Account>, onAdd: (CardNo) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    var digits by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var moveFrom by remember { mutableStateOf<Account?>(null) }
    val tries = rememberNeedTries()
    val need = firstNeed(
        when {
            digits.length < 4 -> Need("digits", "請輸入 4 碼")
            nos.any { it.digits == digits } -> Need("digits", "這張卡已經有 $digits 了")
            else -> null
        },
    )
    val nv = NeedView(need, tries.count)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun add() {
        if (need != null) { tries.count++; return }
        val other = others.firstOrNull { it.id != selfId && it.cardNos.any { n -> n.digits == digits } }
        if (other != null) moveFrom = other else onAdd(CardNo(digits, note.trim().take(8)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增末四碼") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("卡片背面或手機錢包裡卡號的最後 4 碼", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                BasicTextField(
                    digits, { digits = it.filter { c -> c in '0'..'9' }.take(4) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.focusRequester(focus).semantics { contentDescription = "末四碼" },
                    decorationBox = { _ ->
                        Row(
                            Modifier.needFrame(nv, "digits", RoundedCornerShape(14.dp)).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for (i in 0 until 4) {
                                val ch = digits.getOrNull(i)?.toString() ?: ""
                                val cur = i == digits.length.coerceAtMost(3) && digits.length < 4
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(cute.soft.copy(alpha = 0.5f))
                                        .border(if (cur) 2.dp else 1.dp, if (cur) MaterialTheme.colorScheme.primary else cute.soft, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) { Text(ch, style = MaterialTheme.typography.headlineSmall) }
                            }
                        }
                    },
                )
                nv.Message("digits")
                Text("備註（選填）", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CARD_NO_NOTES.forEach { s ->
                        val on = note == s
                        Box(
                            Modifier.clip(RoundedCornerShape(16.dp))
                                .background(if (on) MaterialTheme.colorScheme.primary else cute.card)
                                .border(1.dp, if (on) MaterialTheme.colorScheme.primary else cute.soft, RoundedCornerShape(16.dp))
                                .clickable { note = if (on) "" else s }.padding(horizontal = 14.dp, vertical = 7.dp),
                        ) { Text(s, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge) }
                    }
                }
                OutlinedTextField(
                    if (note in CARD_NO_NOTES) "" else note, { note = it.take(8) },
                    placeholder = { Text("或自己打…") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { add() }) { Text("加入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
    moveFrom?.let { o ->
        ConfirmDialog(
            "移到這張卡？", "$digits 現在是「${o.name}」的末四碼。移過來之後，按儲存時「${o.name}」就不會有這組了。", "移過來",
            onConfirm = { moveFrom = null; onAdd(CardNo(digits, note.trim().take(8))) }, onDismiss = { moveFrom = null },
        )
    }
}
