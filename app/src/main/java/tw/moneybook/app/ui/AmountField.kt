package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import tw.moneybook.app.Calc
import tw.moneybook.app.Money
import tw.moneybook.app.fxExpr
import tw.moneybook.app.groupDigits

/**
 * 金額欄位：外觀和 CompactField 一樣，點了跳出小算盤視窗（不用手機鍵盤），可以加減乘除。
 * value 是純數字（台幣整數、外幣可以有 dec 位小數），按「完成」填回算好的結果。
 */
@Composable
internal fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    /** 小算盤視窗的標題，例如「這次還了多少」 */
    title: String,
    modifier: Modifier = Modifier,
    prefix: String = Money.twd,
    /** 幣別的小數位數（台幣 0） */
    dec: Int = 0,
    /** 必填沒填好：亮紅框 */
    error: Boolean = false,
    /** 整數最多幾位（和原本輸入框的上限一樣），算出來超過就不能按完成 */
    maxDigits: Int = 9,
) {
    val cute = LocalCute.current
    var open by remember { mutableStateOf(false) }
    // 別的輸入框正在打字時先收掉手機鍵盤，小算盤關掉後才不會又跳出來
    val focus = LocalFocusManager.current
    val shown = groupDigits(value).first
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp)).background(cute.soft)
            .then(if (error) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(14.dp)) else Modifier)
            .clickable { focus.clearFocus(); open = true }
            // 畫面測試用描述找到金額欄位；文字只放數字（和輸入框一樣，提示字不算）
            .clearAndSetSemantics {
                contentDescription = "金額欄位：$title"
                text = AnnotatedString(shown)
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix.isNotEmpty()) {
            Text(prefix, color = cute.sub, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(4.dp))
        }
        Text(
            shown.ifEmpty { placeholder }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (value.isEmpty()) cute.sub.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
    if (open) {
        AmountKeypadDialog(title, value, prefix, dec, maxDigits, { v ->
            // 沒改就不通知（onChange 可能會關掉自動分攤、清掉手動改的金額）；空的和 0 算一樣
            fun num(s: String) = s.filter { it.isDigit() || it == '.' }.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
            if (num(v).compareTo(num(value)) != 0) onChange(v)
            open = false
        }, { open = false })
    }
}

/** 金額欄位的小算盤視窗：標題、算式、小算盤；左下角「貼上」（剪貼簿有數字才有），右下角「取消」 */
@Composable
private fun AmountKeypadDialog(title: String, initial: String, prefix: String, dec: Int, maxDigits: Int, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    var expr by remember { mutableStateOf(initial.filter { it.isDigit() || it == '.' }) }
    val clipboard = LocalClipboardManager.current
    // 只在打開時讀一次剪貼簿
    val clip = remember { clipboard.getText()?.text?.let { Calc.clipNumber(it, dec) } }
    val units = Calc.evalMinor(expr, dec)
    val pending = Calc.pending(expr, dec)
    val divZero = Calc.divZero(expr)
    fun result(): String = if (expr.isEmpty()) "" else if (dec == 0) units.toString() else fxExpr(units, dec)
    // 四捨五入後進位（999,999,999.6 → 1,000,000,000）也算太大
    val tooBig = !divZero && Calc.tooBig(expr, maxDigits, dec)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cute.card)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalAlignment = Alignment.End) {
                Text(
                    prefix + (if (expr.isEmpty()) "0" else Calc.pretty(expr)), style = MaterialTheme.typography.headlineMedium, maxLines = 1,
                    color = if (expr.isEmpty()) cute.sub else cute.ink,
                )
                when {
                    divZero -> Text("不能除以 0", style = MaterialTheme.typography.bodyMedium, color = cute.expense)
                    tooBig -> Text("金額太大", style = MaterialTheme.typography.bodyMedium, color = cute.expense)
                    pending -> Text("= $prefix${groupDigits(result()).first}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(2.dp))
            Keypad(
                onKey = { k -> expr = Calc.press(expr, k, dec) },
                doneLabel = if (pending) "=" else "完成",
                doneEnabled = !divZero && !tooBig,
                onDone = {
                    when {
                        divZero || tooBig -> {}                        // 上面已經顯示「不能除以 0」／「金額太大」
                        pending -> expr = result().let { if (it == "0") "" else it }
                        else -> onDone(result())
                    }
                },
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (clip != null) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(cute.soft)
                            .clickable { expr = Calc.paste(expr, clip) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("貼上 ${groupDigits(clip).first}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    }
}
