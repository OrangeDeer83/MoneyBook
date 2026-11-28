package tw.moneybook.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import tw.moneybook.app.AppData
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.WBlocks
import tw.moneybook.app.WLayout
import tw.moneybook.app.forWidget
import tw.moneybook.app.pendWaiting
import tw.moneybook.app.widget.WidgetBinds
import tw.moneybook.app.widgetBar
import tw.moneybook.app.widgetNum
import tw.moneybook.app.widgetTop3
import tw.moneybook.app.widgetWeek

/*
 * 自組桌面小工具在 App 裡的頁面（草圖 ci-out/widget_compose2.png）：
 *  ① 我的 → 桌面小工具：自己組的排版清單（左滑刪除、新增）
 *  ③ 排版頁：上面即時預覽（可以切大小），下面跟統計卡片一樣長按拖曳排、••• 改寬度／設定／拿掉，最下面外觀
 */

// ───────── 小工具的顏色（App 的預覽、桌面上的小工具共用） ─────────

/** 小工具的顏色（ARGB）：照 App 的配色，tone 0 跟著 App 的深淺、1 淺色、2 深色；bg 是背景透明度 */
class WColors(val bg: Long, val bgAlpha: Float, val ink: Long, val sub: Long, val cell: Long, val primary: Long, val inc: Long, val exp: Long, val onPrimary: Long)

fun wColors(p: Pal, l: WLayout, appDark: Boolean): WColors {
    val dark = when (l.tone) {
        1 -> false
        2 -> true
        else -> appDark
    }
    val alpha = when (l.bg) {
        1 -> 0.72f
        2 -> 0f
        else -> 1f
    }
    return if (dark) WColors(p.dCard, alpha, p.dInk, p.dSub, p.dSoft, p.dPrimary, p.dInc, p.dExp, p.dBg)
    else WColors(p.card, alpha, p.ink, p.sub, p.soft, p.primary, p.inc, p.exp, 0xFFFFFFFF)
}

/** 預設可以選的大小（格數）；寬、高 dp 是一般手機上大約的大小 */
val WIDGET_SIZES = listOf(2 to 2, 4 to 2, 4 to 3, 4 to 4, 5 to 5)
fun cellsDp(cols: Int, rows: Int): Pair<Float, Float> = (75f * cols - 8f) to (88f * rows - 12f)

/** 排版裡常用記帳區塊放幾個（不含＋） */
fun AppData.tplCount(l: WLayout): Int = WBlocks.tplIds(this, l.opts).size

/** 常用記帳區塊上寫的名字 */
private fun AppData.tplNames(l: WLayout): String =
    WBlocks.tplIds(this, l.opts).mapNotNull { id -> templates.firstOrNull { it.id == id }?.name }.joinToString("、")

// ───────── ① 排版清單 ─────────

@Composable
fun WidgetLayoutsScreen(vm: MoneyViewModel, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler { onBack() }
    val d = vm.data
    val cute = LocalCute.current
    val ctx = LocalContext.current
    // 桌面上幾個小工具用這份：綁定存在手機上（不在 App 資料裡），回到這一頁（例如長按 → 設定換了排版）就重算
    var tick by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        SubTopBar("桌面小工具", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("自己組的排版", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            Text(
                "在這裡排好，桌面上的「自己組的」小工具照著畫；可以有好幾份，每個小工具選一份。排版跟著備份",
                style = MaterialTheme.typography.bodySmall, color = cute.sub,
            )
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                d.widgetLayouts.forEach { l ->
                    androidx.compose.runtime.key(l.id) {
                        SwipeRow(onDelete = { vm.deleteWidgetLayout(l.id) }) {
                            val n = l.layout.rows.sumOf { it.ids.size }
                            val book = d.books.firstOrNull { it.id == l.bookId }?.name ?: "跟著 App 的帳本"
                            val used = remember(l.id, d, tick) { WidgetBinds.countFor(ctx, l.id) }
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cute.card).clickable { onOpen(l.id) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(l.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "$n 塊・$book・" + if (used > 0) "桌面上有 $used 個" else "桌面上沒有",
                                        style = MaterialTheme.typography.labelSmall, color = cute.sub,
                                    )
                                }
                                Icon(AppIcons.ChevronRight, contentDescription = null, tint = cute.sub)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { onOpen(vm.newWidgetLayout()) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text("＋ 新增一份排版", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(16.dp))
            Text(
                "放到桌面：長按桌面 → 小工具 → 記帳本 →「自己組的」，再選要用哪一份。左滑可以刪除（可以復原）；" +
                    "桌面上在用的那個會寫「排版被刪了」，長按它 → 設定可以換一份",
                style = MaterialTheme.typography.bodySmall, color = cute.sub,
            )
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = cute.soft, thickness = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text("原本的五種", style = MaterialTheme.typography.titleSmall)
            Text(
                "快速記帳、本月收支、預算、報銷、常用記帳照舊，一樣在桌面的小工具清單裡",
                style = MaterialTheme.typography.bodySmall, color = cute.sub,
            )
        }
    }
}

// ───────── ③ 排版頁 ─────────

/**
 * 一份排版的排版頁。initW/initH：從桌面上的小工具打開時，那個小工具現在的大小（dp），預覽預設用它
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WidgetEditScreen(vm: MoneyViewModel, id: Long, initW: Int?, initH: Int?, onBack: () -> Unit) {
    val d = vm.data
    val cute = LocalCute.current
    val ctx = LocalContext.current
    val l = d.widgetLayouts.firstOrNull { it.id == id }
    if (l == null) {
        androidx.activity.compose.BackHandler { onBack() }
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            SubTopBar("排版", onBack)
            Text("這份排版已經刪掉了", color = cute.sub, modifier = Modifier.padding(16.dp))
        }
        return
    }
    fun save(n: WLayout) = vm.saveWidgetLayout(n)
    val pendN = remember(d) { d.pendWaiting(tw.moneybook.app.notify.CardPendLog.load(ctx)).size }
    // 預覽的大小：從小工具打開的話先用那個小工具的大小（「這個小工具」），不然 4×3
    val real = if (initW != null && initH != null) initW.toFloat() to initH.toFloat() else null
    var sizeKey by remember { mutableStateOf(if (real != null) "real" else "4x3") }
    val (wDp, hDp) = if (sizeKey == "real" && real != null) real else {
        val (c, r) = sizeKey.split('x').map { it.toInt() }
        cellsDp(c, r)
    }
    val tplN = d.tplCount(l)
    val fit = WBlocks.fitRows(l, tplN, hDp)
    val cutIds = l.layout.rows.drop(fit).flatMap { it.ids }
    var rename by remember { mutableStateOf(false) }
    var pickTpl by remember { mutableStateOf(false) }
    var pickShortcut by remember { mutableStateOf<String?>(null) }

    CardLayoutEditor(
        page = WBlocks.PAGE,
        layout = l.layout,
        save = { n -> save(l.copy(layout = n)) },
        title = l.name,
        onBack = onBack,
        onReset = { vm.resetWidgetLayout(l.id) },
        texts = LayoutTexts(
            hint = "長按區塊拖到想要的位置；••• 改寬度、設定或拿掉",
            reset = "恢復範例",
            empty = "還沒有區塊，從下面加",
            hiddenTitle = { "可以加的區塊（$it）" },
            hiddenHint = { none -> if (none) "全部都加上去了" else "點「＋ 加」加到最下面；也可以長按拖到上面想要的位置" },
            show = "＋ 加",
            hide = "拿掉",
            menuDesc = "區塊選單",
        ),
        name = { bid ->
            if (bid == WBlocks.TPLS) "常用記帳" + d.tplNames(l).let { if (it.isEmpty()) "（只有＋）" else "：$it" }
            else WBlocks.name(bid, l.opts)
        },
        badge = { bid -> if (bid in cutIds) "放不下" else null },
        hiddenIds = WBlocks.addable(l.layout),
        extraMenu = { bid, close ->
            when {
                bid == WBlocks.TPLS -> DropdownMenuItem(text = { Text("挑常用記帳…") }, onClick = { close(); pickTpl = true })
                bid == WBlocks.ADD -> {
                    val cur = WBlocks.addType(l.opts)
                    listOf(TxType.EXPENSE to "打開：支出", TxType.INCOME to "打開：收入", TxType.TRANSFER to "打開：轉帳").forEach { (t, s) ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            trailingIcon = if (cur == t) ({ Icon(AppIcons.LCheck, contentDescription = "目前", modifier = Modifier.size(18.dp)) }) else null,
                            onClick = { close(); save(l.copy(opts = l.opts + (WBlocks.ADD to t.name))) },
                        )
                    }
                }
                WBlocks.isShortcut(bid) -> DropdownMenuItem(text = { Text("打開哪一頁…") }, onClick = { close(); pickShortcut = bid })
            }
        },
        header = {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { rename = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("名字：${l.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("改名", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            WidgetPreview(d, l, wDp, hDp, pendN)
            if (cutIds.isNotEmpty()) {
                Text(
                    "放不下：" + cutIds.joinToString("、") { WBlocks.name(it, l.opts) } + "（把小工具拉大，或把它們往上排）",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub, modifier = Modifier.padding(top = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (real != null) CuteChip("這個小工具", sizeKey == "real", { sizeKey = "real" })
                WIDGET_SIZES.forEach { (c, r) -> CuteChip("$c×$r", sizeKey == "${c}x$r", { sizeKey = "${c}x$r" }) }
            }
            Text(
                "預覽的大小只是看看；桌面上的大小是長按小工具、拉邊框改的",
                style = MaterialTheme.typography.labelSmall, color = cute.sub, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        },
        footer = {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = cute.soft, thickness = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text("外觀", style = MaterialTheme.typography.titleSmall)
            @Composable
            fun line(label: String, opts: List<Pair<String, Boolean>>, pick: (Int) -> Unit) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    opts.forEachIndexed { i, (s, on) -> CuteChip(s, on, { pick(i) }) }
                }
            }
            val books = listOf(0L to "跟著 App") + d.books.map { it.id to it.name }
            line("看哪一本帳本", books.map { (bid, n) -> n to (l.bookId == bid || (bid == 0L && d.books.none { it.id == l.bookId })) }) { i ->
                save(l.copy(bookId = books[i].first))
            }
            line("背景", listOf("不透明", "半透明", "透明").mapIndexed { i, s -> s to (l.bg == i) }) { save(l.copy(bg = it)) }
            line("顏色", listOf("跟 App 一樣", "淺色", "深色").mapIndexed { i, s -> s to (l.tone == i) }) { save(l.copy(tone = it)) }
            line("字的大小", listOf("小", "中", "大").mapIndexed { i, s -> s to (l.font == i) }) { save(l.copy(font = it)) }
            Spacer(Modifier.height(8.dp))
        },
    )

    if (rename) {
        var text by remember { mutableStateOf(l.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("排版的名字") },
            text = { CompactField(text, { text = it.replace("\n", "").take(20) }, "例如 每天看", Modifier.fillMaxWidth()) },
            confirmButton = {
                Button(onClick = { rename = false; if (text.isNotBlank()) save(l.copy(name = text.trim())) }) { Text("好") }
            },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("取消") } },
        )
    }
    if (pickTpl) WidgetTplDialog(d, WBlocks.tplIds(d, l.opts), { ids -> save(l.copy(opts = l.opts + (WBlocks.TPLS to ids.joinToString(",")))) }) { pickTpl = false }
    pickShortcut?.let { sid ->
        val cur = WBlocks.shortcutOpen(l.opts, sid)
        AlertDialog(
            onDismissRequest = { pickShortcut = null },
            title = { Text("捷徑打開哪一頁") },
            text = {
                Column {
                    WBlocks.SHORTCUTS.forEach { (k, s) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { pickShortcut = null; save(l.copy(opts = l.opts + (sid to k))) }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = cur == k, onClick = { pickShortcut = null; save(l.copy(opts = l.opts + (sid to k))) })
                            Text(s)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { pickShortcut = null }) { Text("取消") } },
        )
    }
}

/** 挑常用記帳：勾的順序就是小工具上的順序，最多 8 個 */
@Composable
private fun WidgetTplDialog(d: AppData, init: List<Long>, onDone: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    val cute = LocalCute.current
    var picked by remember { mutableStateOf(init) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("小工具上放哪幾個常用記帳") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "照勾的順序排，最多 ${WBlocks.TPL_MAX} 個；最後一格固定是「＋」（打開記一筆）",
                    style = MaterialTheme.typography.bodySmall, color = cute.sub,
                )
                if (d.templates.isEmpty()) {
                    Text("還沒有常用記帳（我的 → 常用記帳）", color = cute.sub, modifier = Modifier.padding(top = 12.dp))
                }
                d.templates.forEach { t ->
                    val i = picked.indexOf(t.id)
                    val full = i < 0 && picked.size >= WBlocks.TPL_MAX
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !full) { picked = if (i >= 0) picked - t.id else picked + t.id }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = i >= 0, enabled = !full, onCheckedChange = { picked = if (i >= 0) picked - t.id else picked + t.id })
                        Text(t.name, modifier = Modifier.weight(1f), color = if (full) cute.sub else cute.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (i >= 0) Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onDone(picked); onDismiss() }) { Text("好") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

// ───────── 預覽（跟桌面上畫的一樣） ─────────

/** 手機桌面上的樣子：太寬就整個縮小；放不下的排不畫 */
@Composable
fun WidgetPreview(d0: AppData, l: WLayout, wDp: Float, hDp: Float, pendN: Int) {
    val cute = LocalCute.current
    val d = d0.forWidget(l)
    val c = wColors(palOf(d0.prefs.palette), l, cute.dark)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cute.sub.copy(alpha = 0.45f)).padding(12.dp)
            .semantics { contentDescription = "小工具預覽" },
        contentAlignment = Alignment.Center,
    ) {
        val scale = (maxWidth.value / wDp).coerceAtMost(1f)
        val dens = LocalDensity.current
        Box(Modifier.size((wDp * scale).dp, (hDp * scale).dp)) {
            CompositionLocalProvider(LocalDensity provides Density(dens.density * scale, dens.fontScale)) {
                // 跟桌面上一樣用手機的字型、自然的行高（App 的字型行高比較高，數字會被切掉）
                CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Default)) {
                    WidgetBody(d, l, c, wDp, hDp, pendN)
                }
            }
        }
    }
}

@Composable
private fun WidgetBody(d: AppData, l: WLayout, c: WColors, wDp: Float, hDp: Float, pendN: Int) {
    val f = WBlocks.fontScale(l.font)
    val tplN = WBlocks.tplIds(d, l.opts).size
    val heights = WBlocks.rowHeights(l, tplN)
    val fit = WBlocks.fitRows(l, tplN, hDp)
    Column(
        Modifier.size(wDp.dp, hDp.dp).clip(RoundedCornerShape(22.dp)).background(Color(c.bg).copy(alpha = c.bgAlpha)).padding(WBlocks.PAD.dp),
        verticalArrangement = Arrangement.spacedBy(WBlocks.GAP.dp),
    ) {
        l.layout.rows.take(fit).forEachIndexed { i, r ->
            Row(Modifier.fillMaxWidth().height(heights[i].dp), horizontalArrangement = Arrangement.spacedBy(WBlocks.GAP.dp)) {
                val inner = wDp - WBlocks.PAD * 2
                if (!r.half) PreviewBlock(d, l, c, r.left!!, false, f, pendN, inner, Modifier.weight(1f).fillMaxHeight())
                else listOf(r.left, r.right).forEach { bid ->
                    if (bid == null) Spacer(Modifier.weight(1f))
                    else PreviewBlock(d, l, c, bid, true, f, pendN, (inner - WBlocks.GAP) / 2, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun PreviewBlock(d: AppData, l: WLayout, c: WColors, id: String, half: Boolean, f: Float, pendN: Int, wDp: Float, modifier: Modifier) {
    val today = LocalDate.now()
    val cellBg = Color(c.cell).copy(alpha = if (c.bgAlpha == 0f) 0.9f else 1f)
    val cellMod = modifier.clip(RoundedCornerShape(14.dp)).background(cellBg).padding(horizontal = 10.dp, vertical = 6.dp)
    fun tone(t: Int) = Color(when (t) { 1 -> c.inc; 2 -> c.exp; else -> c.ink })
    when {
        id in WBlocks.NUMBERS -> {
            val n = d.widgetNum(id, today, pendN) ?: return
            Column(cellMod, verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(n.label, color = Color(c.sub), fontSize = (11 * f).sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (n.sub.isNotEmpty()) Text("・" + n.sub, color = Color(c.sub), fontSize = (11 * f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(n.value, color = tone(n.tone), fontSize = (19 * f).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        id == WBlocks.BAR -> {
            val b = d.widgetBar(today)
            Column(cellMod, verticalArrangement = Arrangement.Center) {
                Text(b?.label ?: "還沒設定預算", color = Color(c.sub), fontSize = (11 * f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(c.sub).copy(alpha = 0.18f))) {
                    if (b != null) {
                        val main = Color(if (b.over) c.exp else c.primary)
                        Row(Modifier.fillMaxSize()) {
                            val spent = (b.used - b.reserved).coerceAtLeast(0f)
                            if (spent > 0f) Box(Modifier.weight(spent).fillMaxHeight().background(main))
                            if (b.reserved > 0f) Box(Modifier.weight(b.reserved.coerceAtMost(b.used)).fillMaxHeight().background(main.copy(alpha = 0.4f)))
                            if (b.used < 1f) Spacer(Modifier.weight(1f - b.used))
                        }
                    }
                }
            }
        }
        id == WBlocks.ADD -> {
            val label = when (WBlocks.addType(l.opts)) {
                TxType.INCOME -> "＋ 記收入"
                TxType.TRANSFER -> "＋ 記轉帳"
                else -> "＋ 記一筆"
            }
            Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(c.primary)), contentAlignment = Alignment.Center) {
                Text(label, color = Color(c.onPrimary), fontSize = (14 * f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        WBlocks.isShortcut(id) -> Box(cellMod, contentAlignment = Alignment.Center) {
            Text(WBlocks.shortcutName(WBlocks.shortcutOpen(l.opts, id)) + " ›", color = Color(c.ink), fontSize = (13 * f).sp, maxLines = 1)
        }
        id == WBlocks.TPLS -> {
            val ids = WBlocks.tplIds(d, l.opts)
            val cells: List<Long?> = ids + null
            val per = WBlocks.tplPerRow(half)
            val icon = WBlocks.tplShowIcon(wDp, per, f)
            Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                cells.chunked(per).forEach { row ->
                    Row(Modifier.fillMaxWidth().height((WBlocks.TPL_CELL * f).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { tid ->
                            val t = tid?.let { x -> d.templates.firstOrNull { it.id == x } }
                            Row(
                                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(cellBg).padding(horizontal = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = if (t == null || !icon) Arrangement.Center else Arrangement.Start,
                            ) {
                                if (t == null) {
                                    Text("＋", color = Color(c.primary), fontSize = (18 * f).sp, fontWeight = FontWeight.Bold)
                                } else {
                                    val cat = t.categoryId?.let { d.catMap[it] }
                                    if (icon) {
                                        CatBubble(t.icon.ifEmpty { cat?.emoji ?: if (t.type == TxType.TRANSFER) "img:ui_transfer" else "img:cat_box" }, cat?.color ?: 5, (22 * f).dp)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(t.name, color = Color(c.ink), fontSize = (12 * f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (13 * f).sp)
                                }
                            }
                        }
                        repeat(per - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        id == WBlocks.WEEK -> {
            val week = d.widgetWeek(today)
            val max = week.maxOf { it.second }.coerceAtLeast(1L)
            Column(cellMod) {
                Text("近 7 天支出", color = Color(c.sub), fontSize = (11 * f).sp, maxLines = 1)
                Row(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    week.forEach { (day, v) ->
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                            val h = (36f * f * v / max).coerceAtLeast(2f)
                            Box(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Color(c.primary).copy(alpha = if (v > 0) 1f else 0.25f)))
                            Text("$day", color = Color(c.sub), fontSize = (9 * f).sp, maxLines = 1)
                        }
                    }
                }
            }
        }
        id == WBlocks.TOP3 -> {
            val top = d.widgetTop3(today)
            Column(cellMod, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("本月花最多", color = Color(c.sub), fontSize = (11 * f).sp, maxLines = 1)
                if (top.isEmpty()) Text("這個月還沒有支出", color = Color(c.ink), fontSize = (12 * f).sp)
                top.forEach { (name, _, pct) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = Color(c.ink), fontSize = (11 * f).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width((48 * f).dp))
                        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Color(c.sub).copy(alpha = 0.18f))) {
                            Box(Modifier.fillMaxWidth(pct / 100f).fillMaxHeight().background(Color(c.primary)))
                        }
                        Text("$pct%", color = Color(c.ink), fontSize = (11 * f).sp, textAlign = TextAlign.End, modifier = Modifier.width((34 * f).dp))
                    }
                }
            }
        }
        id == WBlocks.MASCOT -> {
            val mood = moodOf(d)
            Row(cellMod, verticalAlignment = Alignment.CenterVertically) {
                if (d.prefs.mascot != "none") {
                    Image(painterResource(mascotRes(d.prefs.mascot, mood)), contentDescription = null, modifier = Modifier.size((40 * f).dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(mascotLine(mood, d), color = Color(c.ink), fontSize = (12.5f * f).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (15 * f).sp)
            }
        }
    }
}
