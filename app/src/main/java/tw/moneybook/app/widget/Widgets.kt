package tw.moneybook.app.widget

import tw.moneybook.app.budgetSpent
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.layout.fillMaxHeight
import tw.moneybook.app.R
import tw.moneybook.app.GRID_CELL_PAD
import tw.moneybook.app.GRID_OUTER_PAD
import tw.moneybook.app.GRID_MIN_W
import tw.moneybook.app.GRID_TALL
import tw.moneybook.app.TPL_ICON
import tw.moneybook.app.TPL_ICON_TALL
import tw.moneybook.app.TPL_NAME_SP
import tw.moneybook.app.TPL_PLUS_SP
import tw.moneybook.app.splitName
import tw.moneybook.app.Template
import tw.moneybook.app.tplGrid
import tw.moneybook.app.ui.AppImages
import tw.moneybook.app.ui.AppLines
import tw.moneybook.app.ui.catColor
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import tw.moneybook.app.AppData
import tw.moneybook.app.MainActivity
import tw.moneybook.app.Money
import tw.moneybook.app.Store
import tw.moneybook.app.TxType
import tw.moneybook.app.expenseSum
import tw.moneybook.app.formatMoney
import tw.moneybook.app.inMonth
import tw.moneybook.app.pendingReimb
import tw.moneybook.app.ui.Mood
import tw.moneybook.app.ui.Pal
import tw.moneybook.app.ui.mascotRes
import tw.moneybook.app.ui.moodOf
import tw.moneybook.app.ui.palOf
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// ───────────────────────── 共用 ─────────────────────────

/** 小工具直接讀手機上的存檔（跟 App 同一份，沒有網路） */
private fun loadData(context: Context): AppData = Store(context.filesDir).load().also { Money.sync(it) }

/** 小工具用的配色：跟著 App 選的配色，深色模式跟著系統 */
private class WTheme(p: Pal) {
    val bg = ColorProvider(Color(p.card), Color(p.dCard))
    val ink = ColorProvider(Color(p.ink), Color(p.dInk))
    val sub = ColorProvider(Color(p.sub), Color(p.dSub))
    val primary = ColorProvider(Color(p.primary), Color(p.dPrimary))
    val soft = ColorProvider(Color(p.soft), Color(p.dSoft))
    val exp = ColorProvider(Color(p.exp), Color(p.dExp))
    val inc = ColorProvider(Color(p.inc), Color(p.dInc))
    val onPrimary = ColorProvider(Color.White, Color(p.dBg))
}

/** 點小工具開啟 App：open = add（記一筆）、reimb（報銷）、templates（常用記帳頁）、tpl:<id>（用那個常用記帳記一筆）、空白（首頁） */
private fun openApp(context: Context, open: String?): androidx.glance.action.Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (open != null) putExtra("open", open)
    }
    return actionStartActivity(intent)
}

/** total＝本月全部支出；spent＝算預算的支出（預算外的不算，見 Budget.kt） */
private class MonthInfo(val total: Long, val spent: Long, val budget: Long, val daysLeft: Int) {
    val left: Long get() = budget - spent
}

private fun monthInfo(d: AppData): MonthInfo {
    val m = YearMonth.now()
    val today = LocalDate.now()
    val list = d.bookTxns.inMonth(m)
    return MonthInfo(list.expenseSum(), d.budgetSpent(list), d.currentBook.budgetFor(m), m.lengthOfMonth() - today.dayOfMonth + 1)
}

/** 記帳後呼叫，讓桌面上的小工具跟著更新 */
suspend fun updateAllWidgets(context: Context) {
    MonthWidget().updateAll(context)
    BudgetWidget().updateAll(context)
    ReimbWidget().updateAll(context)
    TemplateWidget().updateAll(context)
}

// ───────────────────────── 快速記一筆 ─────────────────────────

class QuickAddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val t = WTheme(palOf(loadData(context).prefs.palette))
        provideContent {
            Box(
                modifier = GlanceModifier.fillMaxSize().background(t.primary).cornerRadius(24.dp)
                    .clickable(openApp(context, "add")),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("＋", style = TextStyle(color = t.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.width(8.dp))
                    Text("記一筆", style = TextStyle(color = t.onPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

// ───────────────────────── 本月支出（含吉祥物） ─────────────────────────

class MonthWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadData(context)
        val t = WTheme(palOf(d.prefs.palette))
        val info = monthInfo(d)
        val mascot = d.prefs.mascot
        val mood = moodOf(d)
        provideContent {
            Row(
                modifier = GlanceModifier.fillMaxSize().background(t.bg).cornerRadius(24.dp).padding(14.dp)
                    .clickable(openApp(context, null)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (mascot != "none") {
                    Image(
                        provider = ImageProvider(mascotRes(mascot, mood)),
                        contentDescription = null,
                        modifier = GlanceModifier.size(64.dp),
                    )
                    Spacer(GlanceModifier.width(12.dp))
                }
                Column(modifier = GlanceModifier.fillMaxWidth()) {
                    Text("本月支出", style = TextStyle(color = t.sub, fontSize = 12.sp))
                    Text(formatMoney(info.total), style = TextStyle(color = t.ink, fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.height(6.dp))
                    if (info.budget > 0L) {
                        val over = info.spent > info.budget
                        Text(
                            if (over) "已超出預算 ${formatMoney(info.spent - info.budget)}" else "剩餘預算 ${formatMoney(info.left)}",
                            style = TextStyle(color = if (over) t.exp else t.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = (info.spent.toFloat() / info.budget.toFloat()).coerceIn(0f, 1f),
                            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                            color = if (over) t.exp else t.primary,
                            backgroundColor = t.soft,
                        )
                    } else {
                        Text("還沒設定預算", style = TextStyle(color = t.sub, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}

// ───────────────────────── 當月剩餘預算 ─────────────────────────

class BudgetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadData(context)
        val t = WTheme(palOf(d.prefs.palette))
        val info = monthInfo(d)
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(t.bg).cornerRadius(24.dp).padding(14.dp)
                    .clickable(openApp(context, null)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (info.budget <= 0L) {
                    Text("當月預算", style = TextStyle(color = t.sub, fontSize = 12.sp))
                    Text("還沒設定", style = TextStyle(color = t.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                    Text("到「我的」設定每月預算", style = TextStyle(color = t.sub, fontSize = 12.sp), maxLines = 1)
                } else {
                    val over = info.spent > info.budget
                    Text(if (over) "已超出預算" else "當月剩餘預算", style = TextStyle(color = t.sub, fontSize = 12.sp))
                    Text(
                        formatMoney(if (over) info.spent - info.budget else info.left),
                        style = TextStyle(color = if (over) t.exp else t.ink, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = (info.spent.toFloat() / info.budget.toFloat()).coerceIn(0f, 1f),
                        modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                        color = if (over) t.exp else t.primary,
                        backgroundColor = t.soft,
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    val perDay = if (!over && info.daysLeft > 0) info.left / info.daysLeft else 0L
                    Text(
                        if (over) "預算 ${formatMoney(info.budget)}" else "還有 ${info.daysLeft} 天，平均每天 ${formatMoney(perDay)}",
                        style = TextStyle(color = t.sub, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

class BudgetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BudgetWidget()
}

// ───────────────────────── 待報銷提醒 ─────────────────────────

class ReimbWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadData(context)
        val t = WTheme(palOf(d.prefs.palette))
        val pending = d.bookTxns.pendingReimb()
        val outstanding = pending.sumOf { it.reimbOutstanding }
        // 還沒收齊的項目：（對象, 帳單日期）
        val open = pending.flatMap { tx -> tx.items.filter { !it.closed && it.remaining > 0L }.map { it.who.trim() to tx.day } }
        val people = open.map { it.first }.distinct().size
        val oldest = open.minByOrNull { it.second }
        val days = oldest?.let { ChronoUnit.DAYS.between(LocalDate.ofEpochDay(it.second), LocalDate.now()).coerceAtLeast(0L) }
        provideContent {
            Column(
                modifier = GlanceModifier.fillMaxSize().background(t.bg).cornerRadius(24.dp).padding(14.dp)
                    .clickable(openApp(context, "reimb")),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("還沒收到的報銷款", style = TextStyle(color = t.sub, fontSize = 12.sp))
                if (open.isEmpty()) {
                    Text("都收齊了", style = TextStyle(color = t.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold))
                } else {
                    Text(formatMoney(outstanding), style = TextStyle(color = t.exp, fontSize = 26.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.height(4.dp))
                    val who = oldest?.first?.ifBlank { "沒填對象" } ?: ""
                    Text(
                        "$people 位・欠最久：$who ${days ?: 0} 天",
                        style = TextStyle(color = t.sub, fontSize = 12.sp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

class ReimbWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ReimbWidget()
}

// ───────────────────────── 常用記帳 ─────────────────────────

/**
 * 常用記帳：照「我的 → 常用記帳」的順序放得下幾個就幾個，最後一格是 ＋（空白的記一筆）。
 * 怎麼排見 tplGrid（常用記帳不多時排成比較扁的格子）；點一格打開記一筆、內容帶好，按完成才記。
 * 格子只有分類圖示和名稱（不放金額）；外框圓角跟著手機的小工具圓角，格子圓角用系統的「內層圓角」（＝外框－8dp，和外框同心）
 */
class TemplateWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadData(context)
        val t = WTheme(palOf(d.prefs.palette))
        provideContent {
            val size = LocalSize.current
            Column(
                GlanceModifier.fillMaxSize().background(t.bg).cornerRadius(android.R.dimen.system_app_widget_background_radius)
                    .padding(GRID_OUTER_PAD.dp),
            ) {
                if (d.templates.isEmpty()) {
                    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            GlanceModifier.defaultWeight().fillMaxHeight().clickable(openApp(context, "templates")).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("還沒有常用記帳", style = TextStyle(color = t.ink, fontSize = 15.sp), maxLines = 1)
                            Text("點這裡新增（我的 → 常用記帳）", style = TextStyle(color = t.sub, fontSize = 12.sp), maxLines = 2)
                        }
                        Box(GlanceModifier.width((GRID_MIN_W + 2 * GRID_CELL_PAD).dp).fillMaxHeight().padding(GRID_CELL_PAD.dp)) { TplPlus(context, t) }
                    }
                } else {
                    val g = tplGrid(d.templates.size, size.width.value, size.height.value)
                    val cells: List<Template?> = d.templates.take(g.shown) + null
                    val tall = g.cellH >= GRID_TALL
                    // 名稱一行放得下幾個字：格子寬扣掉左右留白（橫的格子再扣圖示）
                    val textW = if (tall) g.cellW - 12f else g.cellW - 10f - TPL_ICON - 5f
                    val maxEm = textW / TPL_NAME_SP
                    var k = 0
                    g.rows.forEach { count ->
                        val row = cells.subList(k, k + count)
                        k += count
                        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            row.forEach { tp ->
                                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(GRID_CELL_PAD.dp)) {
                                    if (tp == null) TplPlus(context, t) else TplCell(context, d, tp, t, tall, maxEm)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** ＋：底色和其他格子一樣，只有 ＋ 是主色 */
@Composable
private fun TplPlus(context: Context, t: WTheme) {
    Box(
        GlanceModifier.fillMaxSize().background(t.soft).cornerRadius(android.R.dimen.system_app_widget_inner_radius).clickable(openApp(context, "add")),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋", style = TextStyle(color = t.primary, fontSize = TPL_PLUS_SP.sp, fontWeight = FontWeight.Bold))
    }
}

/** 分類圖示：圖片圖示用 App 裡的圖，表情符號用文字，線條圖示（小工具畫不出來）用箱子 */
@Composable
private fun TplIcon(emoji: String, color: Int, box: Int) {
    val img = box * 3 / 4
    Box(
        GlanceModifier.size(box.dp).background(androidx.glance.unit.ColorProvider(catColor(color).copy(alpha = 0.35f))).cornerRadius((box / 3).dp),
        contentAlignment = Alignment.Center,
    ) {
        if (AppLines.isLine(emoji) || AppImages.isImg(emoji)) {
            Image(ImageProvider(AppImages.all[emoji] ?: R.drawable.img_cat_box), contentDescription = null, modifier = GlanceModifier.size(img.dp))
        } else {
            Text(emoji, style = TextStyle(fontSize = (img * 0.75f).sp))
        }
    }
}

/**
 * 一格常用記帳：分類圖示、名稱（一行放不下就平均分成兩行，見 splitName）。
 * tall：格子高，圖示在上、名稱在下置中；不然圖示在左、名稱在右
 */
@Composable
private fun TplCell(context: Context, d: AppData, tp: Template, t: WTheme, tall: Boolean, maxEm: Float) {
    val cat = tp.categoryId?.let { d.catMap[it] }
    val emoji = cat?.emoji ?: if (tp.type == TxType.TRANSFER) "img:ui_transfer" else "img:cat_box"
    val name = splitName(tp.name, maxEm)
    val base = GlanceModifier.fillMaxSize().background(t.soft).cornerRadius(android.R.dimen.system_app_widget_inner_radius)
        .clickable(openApp(context, "tpl:${tp.id}"))
    if (tall) {
        Column(base.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            TplIcon(emoji, cat?.color ?: 5, TPL_ICON_TALL)
            Spacer(GlanceModifier.height(5.dp))
            Text(name, style = TextStyle(color = t.ink, fontSize = TPL_NAME_SP.sp, textAlign = androidx.glance.text.TextAlign.Center), maxLines = 2)
        }
    } else {
        Row(base.padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            TplIcon(emoji, cat?.color ?: 5, TPL_ICON)
            Spacer(GlanceModifier.width(5.dp))
            Text(name, style = TextStyle(color = t.ink, fontSize = TPL_NAME_SP.sp), maxLines = 2)
        }
    }
}

class TemplateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TemplateWidget()
}
