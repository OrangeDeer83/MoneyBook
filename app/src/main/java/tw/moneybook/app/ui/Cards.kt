package tw.moneybook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.CardLayout

/** 很寬的螢幕（平板、橫放）卡片置中，最寬約手機的兩倍 */
val CardMaxWidth = 840.dp

/** 半排每張不到 160dp（分割畫面、字放大）就上下排：扣掉左右邊距 16dp 和中間 8dp */
fun isNarrow(width: Dp): Boolean = width < 160.dp * 2 + 8.dp + 32.dp

/** 卡片右上角 ••• 的動作 */
class CardMenu(val onHide: (String) -> Unit, val onLayout: () -> Unit)

/** 卡片的寬度：半排要撐滿那一格的高度，兩張才會一樣高 */
fun cardMod(half: Boolean): Modifier = if (half) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth()

/**
 * 依排版把卡片放進列表：整排一張一個項目；半排兩張並排（落單的那一邊空白），窄的螢幕改成上下排。
 * present 是這張卡片在目前的頁面有沒有東西（例如「每月收支」只有年才有）；全部卡片都隱藏時顯示一行提示。
 */
fun LazyListScope.cardRows(
    layout: CardLayout,
    narrow: Boolean,
    present: (String) -> Boolean,
    onLayout: () -> Unit,
    card: @Composable (id: String, half: Boolean) -> Unit,
) {
    if (layout.rows.isEmpty()) {
        item(key = "card-none") { AllCardsHidden(onLayout) }
        return
    }
    for (r in layout.rows) {
        val l = r.left?.takeIf(present)
        val rt = r.right?.takeIf(present)
        if (l == null && rt == null) continue
        if (!r.half || narrow) {
            listOfNotNull(l, rt).forEach { id -> item(key = "card-$id") { card(id, false) } }
            continue
        }
        item(key = "card-${l ?: ""}-${rt ?: ""}") {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight()) { if (l != null) card(l, true) }
                Box(Modifier.weight(1f).fillMaxHeight()) { if (rt != null) card(rt, true) }
            }
        }
    }
}

@Composable
private fun AllCardsHidden(onLayout: () -> Unit) {
    val cute = LocalCute.current
    CuteCard(Modifier.fillMaxWidth(), onClick = onLayout) {
        Text("所有卡片都隱藏了・調整排版 ›", style = MaterialTheme.typography.bodyMedium, color = cute.sub)
    }
}

/** 卡片右上角的 •••：隱藏這張、調整排版 */
@Composable
fun CardMore(id: String, menu: CardMenu) {
    val cute = LocalCute.current
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.offset(x = 6.dp).size(30.dp).clip(CircleShape).clickable { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(AppIcons.More, contentDescription = "卡片選單", tint = cute.sub, modifier = Modifier.size(18.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("隱藏這張") }, onClick = { open = false; menu.onHide(id) })
            DropdownMenuItem(text = { Text("調整排版…") }, onClick = { open = false; menu.onLayout() })
        }
    }
}

/** 卡片標題列：（圖示）標題，右邊是 ••• */
@Composable
fun CardTitle(title: String, id: String, menu: CardMenu, icon: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            IconGlyph(icon, 22.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
        CardMore(id, menu)
    }
}
