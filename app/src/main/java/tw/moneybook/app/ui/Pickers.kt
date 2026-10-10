package tw.moneybook.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import tw.moneybook.app.Account
import tw.moneybook.app.AccountType
import tw.moneybook.app.Currencies
import tw.moneybook.app.MoneyViewModel

/** 欄位樣式的選擇鈕：現在選的是什麼寫在左邊，右邊有往下的小箭頭，點了跳出清單 */
@Composable
fun PickerButton(value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cute = LocalCute.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(AppIcons.ChevronDown, contentDescription = null, tint = cute.sub, modifier = Modifier.size(20.dp))
    }
}

/**
 * 選擇帳戶的清單（和記一筆選帳戶的清單一樣：常用帳戶在最上面、依帳戶類型分組、每個帳戶顯示餘額）。
 * accounts 是可以選的帳戶；noneLabel 不是 null 時，清單最上面多一列這個選項（例如「不連動」），選了呼叫 onNone。
 */
@Composable
fun AccountPickDialog(
    vm: MoneyViewModel,
    accounts: List<Account>,
    selectedId: Long?,
    onPick: (Account) -> Unit,
    onDismiss: () -> Unit,
    title: String = "選擇帳戶",
    noneLabel: String? = null,
    noneSelected: Boolean = false,
    onNone: () -> Unit = {},
) {
    val d = vm.data
    val cute = LocalCute.current
    val bal = remember(d) { d.balances() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // 帳戶多的時候要能往下滑
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val head: @Composable (String) -> Unit = { t ->
                    Text(t, style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp))
                }
                if (noneLabel != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (noneSelected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable(onClick = onNone).padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(noneLabel, modifier = Modifier.weight(1f)) }
                }
                // 常用帳戶放最上面：手動標星號的固定在前，其餘用最近的使用次數自動補；帳戶太少（不到 3 個）又沒有標星號就不分這一區
                val freq = if (accounts.size >= 3 || accounts.any { it.favorite }) d.frequentAccounts(3).filter { f -> accounts.any { it.id == f.id } } else emptyList()
                if (freq.isNotEmpty()) {
                    head("常用帳戶")
                    freq.forEach { a -> AccountLine(a, bal[a.id] ?: 0L, selectedId == a.id, onFavorite = { vm.setFavorite(it.id, !it.favorite) }) { onPick(a) } }
                }
                AccountType.values().forEach { type ->
                    val g = accounts.filter { it.type == type }
                    if (g.isNotEmpty()) {
                        head(type.label)
                        g.forEach { a -> AccountLine(a, bal[a.id] ?: 0L, selectedId == a.id, onFavorite = { vm.setFavorite(it.id, !it.favorite) }) { onPick(a) } }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}

/** 選擇幣別的清單：內建的幣別（代碼、名稱、符號）一列一個，最下面「其他幣別」可以輸入 3 個英文字母的代碼 */
@Composable
fun CurrencyPickDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    var custom by remember { mutableStateOf(current.isNotEmpty() && Currencies.builtin.none { it.code == current }) }
    var code by remember { mutableStateOf(if (custom) current else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇幣別") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Currencies.builtin.forEach { c ->
                    val on = !custom && current == c.code
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { onPick(c.code) }.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.code, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
                        Text(c.name, modifier = Modifier.weight(1f))
                        Text(c.symbol.trim(), color = cute.sub)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(if (custom) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { custom = true }.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) { Text("其他幣別…", modifier = Modifier.weight(1f)) }
                if (custom) {
                    OutlinedTextField(
                        code, { code = it.filter { ch -> ch in 'A'..'Z' || ch in 'a'..'z' }.take(3).uppercase() },
                        label = { Text("幣別代碼（3 個英文字母，例如 CAD）") }, singleLine = true,
                        isError = code.isNotEmpty() && !Currencies.validCode(code),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            if (custom) TextButton(onClick = { if (Currencies.validCode(code)) onPick(code.trim().uppercase()) }) { Text("確定") }
            else TextButton(onClick = onDismiss) { Text("關閉") }
        },
        dismissButton = if (custom) ({ TextButton(onClick = onDismiss) { Text("取消") } }) else null,
    )
}

/** 幣別的顯示文字：「JPY 日圓」；不是內建的幣別只有代碼；還沒選是「選擇幣別」 */
fun currencyLabel(code: String): String {
    if (code.isEmpty()) return "選擇幣別"
    val c = Currencies.builtin.firstOrNull { it.code == code } ?: return code
    return "${c.code} ${c.name}"
}
