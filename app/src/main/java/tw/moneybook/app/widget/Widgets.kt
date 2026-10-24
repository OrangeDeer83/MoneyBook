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
import tw.moneybook.app.Template
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

/** 一格至少要多寬、多高（dp）：放得下圖示＋四個字的名稱＋金額 */
private const val TPL_CELL_W = 92f
private const val TPL_CELL_H = 56f

/**
 * 常用記帳：照「我的 → 常用記帳」的順序放得下幾個就幾個，最後一格是 ＋（空白的記一筆）。
 * 寬度決定一排幾格、高度決定幾排；常用記帳比格子少時格子變寬，不留空格。點一格打開記一筆、內容帶好，按完成才記
 */
class TemplateWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val d = loadData(context)
        val t = WTheme(palOf(d.prefs.palette))
        provideContent {
            val size = LocalSize.current
            val cols = ((size.width.value - 12f) / TPL_CELL_W).toInt().coerceAtLeast(1)
            val rows = ((size.height.value - 12f) / TPL_CELL_H).toInt().coerceAtLeast(1)
            Column(GlanceModifier.fillMaxSize().background(t.bg).cornerRadius(24.dp).padding(5.dp)) {
                if (d.templates.isEmpty()) {
                    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(18.dp).clickable(openApp(context, "templates")).padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("還沒有常用記帳", style = TextStyle(color = t.ink, fontSize = 15.sp), maxLines = 1)
                            Text("點這裡新增（我的 → 常用記帳）", style = TextStyle(color = t.sub, fontSize = 12.sp), maxLines = 1)
                        }
                        Box(GlanceModifier.width(TPL_CELL_W.dp).fillMaxHeight().padding(3.dp)) { TplPlus(context, t) }
                    }
                } else {
                    // 最後一格留給 ＋；排數用到幾排就幾排，每排平均分
                    val cells: List<Template?> = d.templates.take(cols * rows - 1) + null
                    val used = (cells.size + cols - 1) / cols
                    val per = (cells.size + used - 1) / used
                    cells.chunked(per).forEach { row ->
                        Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            row.forEach { tp ->
                                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(3.dp)) {
                                    if (tp == null) TplPlus(context, t) else TplCell(context, d, tp, t)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TplPlus(context: Context, t: WTheme) {
    Box(
        GlanceModifier.fillMaxSize().background(t.primary).cornerRadius(14.dp).clickable(openApp(context, "add")),
        contentAlignment = Alignment.Center,
    ) {
        Text("＋", style = TextStyle(color = t.onPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold))
    }
}

/** 一格常用記帳：分類圖示、名稱、金額（沒有固定金額寫「自訂」，收入寫 +） */
@Composable
private fun TplCell(context: Context, d: AppData, tp: Template, t: WTheme) {
    val cat = tp.categoryId?.let { d.catMap[it] }
    val emoji = cat?.emoji ?: if (tp.type == TxType.TRANSFER) "img:ui_transfer" else "img:cat_box"
    Row(
        GlanceModifier.fillMaxSize().background(t.soft).cornerRadius(14.dp).clickable(openApp(context, "tpl:${tp.id}")).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier.size(28.dp).background(androidx.glance.unit.ColorProvider(catColor(cat?.color ?: 5).copy(alpha = 0.35f))).cornerRadius(9.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (AppLines.isLine(emoji) || AppImages.isImg(emoji)) {
                // 線條圖示小工具畫不出來，用箱子代替
                Image(ImageProvider(AppImages.all[emoji] ?: R.drawable.img_cat_box), contentDescription = null, modifier = GlanceModifier.size(20.dp))
            } else {
                Text(emoji, style = TextStyle(fontSize = 15.sp))
            }
        }
        Spacer(GlanceModifier.width(5.dp))
        Column {
            Text(tp.name, style = TextStyle(color = t.ink, fontSize = 14.sp), maxLines = 1)
            Text(
                when {
                    tp.amount <= 0L -> "自訂"
                    tp.type == TxType.INCOME -> "+" + formatMoney(tp.amount)
                    else -> formatMoney(tp.amount)
                },
                style = TextStyle(color = if (tp.type == TxType.INCOME && tp.amount > 0L) t.inc else t.sub, fontSize = 11.sp),
                maxLines = 1,
            )
        }
    }
}

class TemplateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TemplateWidget()
}
