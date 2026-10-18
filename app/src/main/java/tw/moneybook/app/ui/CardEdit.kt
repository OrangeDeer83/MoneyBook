package tw.moneybook.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import tw.moneybook.app.CardLayout
import tw.moneybook.app.CardPage
import tw.moneybook.app.MoneyViewModel

/** 拖曳放下的位置：插在第 index 排前面、放進半排的空位、或丟進隱藏區 */
private sealed interface Drop {
    data class Row(val index: Int) : Drop
    data class Slot(val row: Int, val right: Boolean) : Drop
    data object Hide : Drop
}

/**
 * 調整排版（從統計頁卡片的 •••「調整排版…」進來）：照實際寬度排的簡化卡片（只寫名字），所見即所得。
 * 長按卡片拖到兩排之間、半排的空位或隱藏區；••• 選整排／半排、靠左／靠右、隱藏。改了馬上存，右上角「完成」回去。
 */
@Composable
fun CardLayoutScreen(vm: MoneyViewModel, page: CardPage, onBack: () -> Unit) {
    BackHandler { onBack() }
    val cute = LocalCute.current
    val primary = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val layout = vm.cardLayout(page)
    fun save(l: CardLayout) { if (l != layout) vm.setCardLayout(page, l) }

    // 拖曳中的卡片、手指位置（螢幕座標）、按住的地方在卡片裡的位置、卡片大小
    var dragId by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var dragW by remember { mutableStateOf(0) }
    var rootPos by remember { mutableStateOf(Offset.Zero) }
    // 每一排、每一個半排格子、隱藏區在螢幕上的位置（找放下的位置用）
    val rowBounds = remember { mutableStateMapOf<Int, Rect>() }
    var hiddenArea by remember { mutableStateOf(Rect.Zero) }

    fun targetOf(id: String, p: Offset): Drop? {
        if (hiddenArea.contains(p)) return if (id in layout.hidden) null else Drop.Hide
        val n = layout.rows.size
        for (i in 0 until n) {
            val b = rowBounds[i] ?: continue
            if (p.y < b.top) return Drop.Row(i)
            if (p.y <= b.bottom) {
                val r = layout.rows[i]
                if (r.half && page.canHalf(id)) {
                    val right = p.x > b.center.x
                    val who = if (right) r.right else r.left
                    if (who == null) return Drop.Slot(i, right)
                    if (who == id) return null
                }
                return Drop.Row(if (p.y < b.center.y) i else i + 1)
            }
        }
        return Drop.Row(n)
    }

    fun apply(id: String, t: Drop): CardLayout = when (t) {
        is Drop.Row -> page.moveToRow(layout, id, t.index)
        is Drop.Slot -> page.moveToSlot(layout, id, t.row, t.right)
        Drop.Hide -> page.hide(layout, id)
    }

    // 目前手指下的放置位置；放下去不會改變排版的就不顯示
    val target = dragId?.let { id -> targetOf(id, finger)?.takeIf { apply(id, it) != layout } }

    /** 掛在卡片上的長按拖曳 */
    @Composable
    fun Modifier.draggable(id: String): Modifier {
        val bounds = remember { arrayOf(Rect.Zero) }
        return onGloballyPositioned { bounds[0] = it.boundsInWindow() }
            .pointerInput(id, layout) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { off ->
                        dragId = id
                        grab = off
                        finger = bounds[0].topLeft + off
                        dragW = size.width
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { ch, amount ->
                        ch.consume()
                        finger += amount
                    },
                    onDragEnd = {
                        targetOf(id, finger)?.let { save(apply(id, it)) }
                        dragId = null
                    },
                    onDragCancel = { dragId = null },
                )
            }
    }

    @Composable
    fun EditCard(id: String, modifier: Modifier) {
        val i = layout.rowOf(id)
        val half = i >= 0 && layout.rows[i].half
        val canHalf = page.canHalf(id)
        var menu by remember { mutableStateOf(false) }
        Row(
            modifier.alpha(if (dragId == id) 0.35f else 1f).draggable(id)
                .clip(RoundedCornerShape(14.dp)).background(cute.card).border(1.dp, cute.soft, RoundedCornerShape(14.dp))
                .heightIn(min = 60.dp).padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(page.name(id), style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(
                    when {
                        !canHalf -> "只能整排"
                        half -> "半排"
                        else -> "整排・可以改半排"
                    },
                    style = MaterialTheme.typography.labelSmall, color = cute.sub, maxLines = 1,
                )
            }
            Box {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.More, contentDescription = "卡片選單", tint = cute.sub, modifier = Modifier.size(20.dp)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val check: @Composable () -> Unit = { Icon(AppIcons.LCheck, contentDescription = "目前", modifier = Modifier.size(18.dp)) }
                    if (canHalf) {
                        DropdownMenuItem(text = { Text("整排") }, trailingIcon = if (!half) check else null, onClick = { menu = false; save(page.setHalf(layout, id, false)) })
                        DropdownMenuItem(text = { Text("半排") }, trailingIcon = if (half) check else null, onClick = { menu = false; save(page.setHalf(layout, id, true)) })
                    }
                    if (half) {
                        val right = layout.rows[i].right == id
                        DropdownMenuItem(text = { Text("靠左") }, trailingIcon = if (!right) check else null, onClick = { menu = false; save(page.setSide(layout, id, false)) })
                        DropdownMenuItem(text = { Text("靠右") }, trailingIcon = if (right) check else null, onClick = { menu = false; save(page.setSide(layout, id, true)) })
                    }
                    DropdownMenuItem(text = { Text("隱藏", color = cute.expense) }, onClick = { menu = false; save(page.hide(layout, id)) })
                }
            }
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { rootPos = it.positionInWindow() }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            SubTopBar("調整排版・${page.title}", onBack) {
                TextButton(onClick = onBack) { Text("完成", style = MaterialTheme.typography.titleSmall) }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState(), enabled = dragId == null)
                    .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "長按卡片拖到想要的位置；••• 改寬度或隱藏",
                        style = MaterialTheme.typography.bodySmall, color = cute.sub, modifier = Modifier.weight(1f),
                    )
                    SoftButton("恢復預設", { vm.resetCardLayout(page) }, compact = true)
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.widthIn(max = CardMaxWidth).fillMaxWidth().align(Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (layout.rows.isEmpty()) {
                        Text("所有卡片都隱藏了，從下面加回來", style = MaterialTheme.typography.bodyMedium, color = cute.sub,
                            modifier = Modifier.fillMaxWidth().onGloballyPositioned { rowBounds.clear() }.padding(vertical = 16.dp))
                    }
                    layout.rows.forEachIndexed { i, r ->
                        Row(
                            Modifier.fillMaxWidth().height(IntrinsicSize.Min).onGloballyPositioned { rowBounds[i] = it.boundsInWindow() },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (!r.half) {
                                key(r.left) { EditCard(r.left!!, Modifier.weight(1f)) }
                            } else {
                                listOf(r.left to false, r.right to true).forEach { (id, right) ->
                                    if (id != null) {
                                        key(id) { EditCard(id, Modifier.weight(1f).fillMaxHeight()) }
                                    } else {
                                        val lit = target == Drop.Slot(i, right)
                                        Box(
                                            Modifier.weight(1f).fillMaxHeight().heightIn(min = 60.dp).drawBehind {
                                                drawRoundRect(
                                                    if (lit) primary else cute.sub.copy(alpha = 0.6f),
                                                    style = Stroke(width = (if (lit) 2.5f else 1.5f).dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                                                    cornerRadius = CornerRadius(14.dp.toPx()),
                                                )
                                            },
                                            contentAlignment = Alignment.Center,
                                        ) { Text("空位", style = MaterialTheme.typography.labelMedium, color = if (lit) primary else cute.sub) }
                                    }
                                }
                            }
                        }
                    }
                    // 隱藏的卡片：點「＋ 顯示」加到最下面，或長按拖到上面
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = cute.soft, thickness = 2.dp)
                    Column(
                        Modifier.fillMaxWidth().onGloballyPositioned { hiddenArea = it.boundsInWindow() }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (target == Drop.Hide) cute.soft else androidx.compose.ui.graphics.Color.Transparent),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("隱藏的卡片（${layout.hidden.size}）", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (layout.hidden.isEmpty()) "把卡片拖到這裡，或按卡片的 ••• 選「隱藏」"
                            else "點「＋ 顯示」加到最下面；也可以長按拖到上面想要的位置",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                        layout.hidden.forEach { id ->
                            key(id) {
                                Row(
                                    Modifier.fillMaxWidth().alpha(if (dragId == id) 0.35f else 1f).draggable(id)
                                        .clip(RoundedCornerShape(14.dp)).background(cute.soft.copy(alpha = 0.5f))
                                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(page.name(id), style = MaterialTheme.typography.titleSmall, color = cute.sub, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { save(page.show(layout, id)) }) { Text("＋ 顯示") }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        // 插入位置的線（畫在上面，不推動其他卡片，拖起來才不會跳）
        val t = target
        if (t is Drop.Row) {
            val y = when {
                layout.rows.isEmpty() -> null
                t.index < layout.rows.size -> rowBounds[t.index]?.top?.minus(with(density) { 4.dp.toPx() })
                else -> rowBounds[layout.rows.size - 1]?.bottom?.plus(with(density) { 4.dp.toPx() })
            }
            val x = rowBounds[0]
            if (y != null && x != null) {
                Box(
                    Modifier.offset { IntOffset((x.left - rootPos.x).toInt(), (y - rootPos.y).toInt() - 2.dp.roundToPx()) }
                        .width(with(density) { x.width.toDp() }).height(4.dp).clip(CircleShape).background(primary)
                )
            }
        }
        // 拖曳中的卡片跟著手指
        dragId?.let { id ->
            Row(
                Modifier.offset { IntOffset((finger.x - grab.x - rootPos.x).toInt(), (finger.y - grab.y - rootPos.y).toInt()) }
                    .width(with(density) { dragW.toDp() }).shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)).background(cute.card).border(2.dp, primary, RoundedCornerShape(14.dp))
                    .heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(page.name(id), style = MaterialTheme.typography.titleSmall) }
        }
    }
}
