package tw.moneybook.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import tw.moneybook.app.AppData
import tw.moneybook.app.CapturedNoti
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.NotiKind
import tw.moneybook.app.NotiSrc
import tw.moneybook.app.NotiTpl
import tw.moneybook.app.TplField
import tw.moneybook.app.TplMark
import tw.moneybook.app.formatFx
import tw.moneybook.app.formatMoney
import tw.moneybook.app.markAt
import tw.moneybook.app.matchCard
import tw.moneybook.app.normalizeNoti
import tw.moneybook.app.notify.CardNotiLog
import tw.moneybook.app.read
import tw.moneybook.app.readNoti
import tw.moneybook.app.setMark
import tw.moneybook.app.tplFor
import tw.moneybook.app.tplName
import tw.moneybook.app.tplNeed
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 教範本要的東西：哪個 App、那則通知的內文（sample）、一開始標好的、改的是哪個範本（null＝新的）。
 * 從「收到的通知」點一則，或從範本清單點一個範本打開。
 */
data class TplTeach(
    val pkg: String,
    val label: String,
    val sample: String,
    val time: Long,
    val marks: List<TplMark>,
    val editId: Long?,
)

/** 從收到的一則通知打開：已經對得上範本的就改那個範本，沒有就照通用規則讀到的先標好 */
fun AppData.teachOf(n: CapturedNoti): TplTeach {
    val tpl = tplFor(n)
    val r = tpl?.read(n.title, n.text, n.time) ?: tw.moneybook.app.parseCardNoti(n.title, n.text, n.time)
    val marks = r.spans.map { (f, s) -> TplMark(f, s.first, s.last) }.sortedBy { it.start }
    return TplTeach(n.pkg, n.label, normalizeNoti(n.text), n.time, marks, tpl?.id)
}

/** 從範本清單打開：用當初那則通知 */
fun AppData.teachOf(t: NotiTpl): TplTeach =
    TplTeach(t.pkg, notiApps.firstOrNull { it.pkg == t.pkg }?.label ?: t.pkg, t.sample, System.currentTimeMillis(), t.marks, t.id)

/** 每個欄位的顏色（照配色變） */
@Composable
fun tplColor(f: TplField): Color {
    val cute = LocalCute.current
    return when (f) {
        // 金額用支出的顏色；末四碼用點綴色（tertiary 跟點綴色一樣，會跟金額分不出來）
        TplField.AMOUNT -> cute.expense
        TplField.MERCHANT -> cute.income
        TplField.LAST4 -> cute.accent2
        TplField.TIME -> cute.out
    }
}

private fun whenText(day: Long, minute: Int): String {
    val d = LocalDate.ofEpochDay(day)
    return "${d.year}/${d.monthValue}/${d.dayOfMonth}" + if (minute >= 0) " %02d:%02d".format(minute / 60, minute % 60) else ""
}

/**
 * 教範本（草圖 ci-out/notify_template.png ②③）：
 * ② 選欄位 → 點第一個字 → 點最後一個字；下面即時顯示讀出來的結果。按「存成 X 的範本」到 ③。
 * ③ 範本長怎樣、拿最近的通知試讀；「存好」才存。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TplTeachScreen(vm: MoneyViewModel, teach: TplTeach, onDone: () -> Unit) {
    val cute = LocalCute.current
    val d = vm.data
    var marks by remember(teach) { mutableStateOf(teach.marks) }
    var field by remember(teach) { mutableStateOf(if (teach.marks.none { it.field == TplField.MERCHANT }) TplField.MERCHANT else TplField.AMOUNT) }
    var first by remember(teach) { mutableStateOf<Int?>(null) }
    var stage by remember(teach) { mutableIntStateOf(1) }
    val tries = rememberNeedTries()
    val tpl = NotiTpl(teach.editId ?: 0L, teach.pkg, teach.sample, marks, LocalDate.now().toEpochDay())
    val read = remember(marks) { tpl.read("", teach.sample, teach.time) }
    val needField = tplNeed(marks)
    val need = firstNeed(
        when {
            needField != null -> Need("grid", "請標${needField.label}")
            read == null -> Need("grid", "金額讀不出來，請重新標金額")
            else -> null
        },
    )
    val nv = NeedView(need, tries.count)
    if (stage == 2) {
        TplConfirm(vm, tpl, teach, onBack = { stage = 1 }, onSaved = onDone)
        return
    }
    SubPage("教它讀這種通知", onDone) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("1. 選要標的欄位", style = MaterialTheme.typography.labelLarge, color = cute.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TplField.entries.forEach { f ->
                    val on = f == field
                    val c = tplColor(f)
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) c.copy(alpha = 0.45f) else cute.card)
                            .border(if (on) 2.dp else 1.dp, if (on) c else cute.soft, RoundedCornerShape(16.dp))
                            .clickable { field = f; first = null }.padding(horizontal = 14.dp, vertical = 7.dp)
                            .semantics { contentDescription = "標${f.label}" },
                    ) { Text(f.label, style = MaterialTheme.typography.labelLarge) }
                }
            }
            Text("2. 點這一格的第一個字和最後一個字", style = MaterialTheme.typography.labelLarge, color = cute.sub)
            CuteCard(Modifier.fillMaxWidth().needFrame(nv, "grid", RoundedCornerShape(24.dp)), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    teach.sample.forEachIndexed { i, ch ->
                        val m = markAt(marks, i)
                        val c = m?.let { tplColor(it.field) }
                        val edge = m != null && (i == m.start || i == m.end)
                        Box(
                            Modifier.size(width = 26.dp, height = 32.dp).clip(RoundedCornerShape(6.dp))
                                .background(c?.copy(alpha = 0.40f) ?: Color.Transparent)
                                .border(
                                    if (first == i) 2.dp else if (edge) 1.5.dp else 0.dp,
                                    if (first == i) MaterialTheme.colorScheme.primary else if (edge) (c ?: cute.soft) else Color.Transparent,
                                    RoundedCornerShape(6.dp),
                                )
                                .clickable {
                                    val f0 = first
                                    when {
                                        // 第一下：開始標（在已經標好的那段裡也一樣，才能縮小範圍重標）
                                        f0 == null -> first = i
                                        // 選那段的欄位、同一個字點兩下：取消那段
                                        f0 == i && m != null && m.field == field -> { marks = marks - m; first = null }
                                        else -> { marks = setMark(marks, field, f0, i); first = null }
                                    }
                                }
                                .semantics { contentDescription = "字$i" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (ch == ' ') Text("_", color = cute.sub, style = MaterialTheme.typography.bodyMedium)
                            else Text(ch.toString(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            nv.Message("grid")
            Text("3. 讀出來的結果", style = MaterialTheme.typography.labelLarge, color = cute.sub)
            CuteCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                TplField.entries.forEach { f ->
                    val v = when (f) {
                        TplField.AMOUNT -> read?.let { if (it.cur.isEmpty()) formatMoney(it.amount) else formatFx(it.amount, it.cur) }
                        TplField.MERCHANT -> read?.merchant?.takeIf { it.isNotEmpty() }?.let { s -> s + (read.wallet.takeIf { it.isNotEmpty() }?.let { "（$it 放備註）" } ?: "") }
                        TplField.LAST4 -> read?.last4?.takeIf { it.isNotEmpty() }?.let { l ->
                            l + (d.matchCard(NotiSrc(0, teach.pkg, teach.label, "", l))?.let { id -> d.accMap[id]?.name }?.let { " → $it" } ?: "")
                        }
                        TplField.TIME -> read?.let { whenText(it.day, it.minute) }
                    }
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.width(64.dp).clip(RoundedCornerShape(8.dp)).background(tplColor(f).copy(alpha = 0.40f)).padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(f.label, style = MaterialTheme.typography.labelMedium) }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            v ?: if (marks.any { it.field == f }) "讀不出來" else "沒標" + (if (f == TplField.LAST4 || f == TplField.TIME) "（照通用規則）" else ""),
                            style = MaterialTheme.typography.bodyMedium, color = if (v == null) cute.sub else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Button(
                onClick = { if (need != null) tries.count++ else stage = 2 },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("存成 ${teach.label} 的範本") }
            Text(
                "・一開始已經照通用規則標好顏色，只要改錯的那格\n" +
                    "・選欄位 → 點第一個字 → 點最後一個字，中間整段變成那個顏色\n" +
                    "・要取消一段：選那段的欄位，在那段裡同一個字點兩下\n" +
                    "・空白（底線）也是一格，可以包在裡面\n" +
                    "・金額、商店一定要標；末四碼、時間沒有可以不標",
                style = MaterialTheme.typography.labelSmall, color = cute.sub,
            )
        }
    }
}

/** ③ 存之前：範本長怎樣、拿這個 App 最近的通知試讀 */
@Composable
private fun TplConfirm(vm: MoneyViewModel, tpl: NotiTpl, teach: TplTeach, onBack: () -> Unit, onSaved: () -> Unit) {
    val cute = LocalCute.current
    val ctx = LocalContext.current
    val list = remember { CardNotiLog.load(ctx).filter { it.pkg == teach.pkg } }
    SubPage("確認範本", onBack) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("範本長這樣（灰字照抄，色塊每次會變）", style = MaterialTheme.typography.labelLarge, color = cute.sub)
            val colors = TplField.entries.associateWith { tplColor(it) }
            CuteCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                val text = buildAnnotatedString {
                    var pos = 0
                    tpl.marks.sortedBy { it.start }.forEach { m ->
                        withStyle(SpanStyle(color = cute.sub)) { append(tpl.sample.substring(pos, m.start)) }
                        withStyle(SpanStyle(background = colors.getValue(m.field).copy(alpha = 0.45f), color = MaterialTheme.colorScheme.onSurface)) { append(" ${m.field.label} ") }
                        pos = m.end + 1
                    }
                    withStyle(SpanStyle(color = cute.sub)) { append(tpl.sample.substring(pos)) }
                }
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
            Text("用這個範本重讀最近的通知", style = MaterialTheme.typography.labelLarge, color = cute.sub)
            val rows = list.map { n -> n to tpl.read(n.title, n.text, n.time) }
            CuteCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Text("${teach.label} 最近 7 天 ${list.size} 則，對得上 ${rows.count { it.second != null }} 則", style = MaterialTheme.typography.titleSmall)
                if (list.isEmpty()) Text("（收到的通知裡沒有這個 App 的）", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                rows.forEach { (n, r) ->
                    val day = Instant.ofEpochMilli(n.time).atZone(ZoneId.systemDefault()).toLocalDate()
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${day.monthValue}/${day.dayOfMonth}", style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.width(48.dp))
                        if (r != null) {
                            Text(r.merchant.ifEmpty { "（沒有商店）" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(if (r.cur.isEmpty()) formatMoney(r.amount) else formatFx(r.amount, r.cur), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            val g = vm.data.readNoti(n)
                            Text(
                                "（對不上，照通用規則：" + (if (g.kind == NotiKind.SPEND) "消費" else "不是消費") + "）",
                                style = MaterialTheme.typography.bodySmall, color = cute.sub, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(48.dp)) { Text("回去改") }
                Button(onClick = { vm.saveTpl(tpl); onSaved() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("存好") }
            }
            Text(
                "・範本用看得懂的樣子顯示，不給你看正規表示式\n" +
                    "・其他數字（例如沒標的日期）自動當「任何數字」，下次日期變了也對得上\n" +
                    "・存好後，之前讀錯的通知用新範本重讀，待確認自動補上",
                style = MaterialTheme.typography.labelSmall, color = cute.sub,
            )
        }
    }
}

/** 刷卡通知設定頁的範本清單（草圖 ④）：左滑刪除；點了看原本那則、重新標 */
@Composable
fun TplListCard(vm: MoneyViewModel, captured: List<CapturedNoti>, onOpen: (NotiTpl) -> Unit) {
    val cute = LocalCute.current
    val d = vm.data
    if (d.notiTpls.isEmpty()) return
    CuteCard(Modifier.fillMaxWidth()) {
        Text("範本", style = MaterialTheme.typography.titleSmall)
        d.notiTpls.sortedBy { it.id }.forEach { t ->
            Spacer(Modifier.height(6.dp))
            SwipeRow(onDelete = { vm.deleteTpl(t.id) }) {
                val n = captured.count { it.pkg == t.pkg && t.read(it.title, it.text, it.time) != null }
                val day = LocalDate.ofEpochDay(t.created)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cute.soft.copy(alpha = 0.45f))
                        .clickable { onOpen(t) }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(d.tplName(t), style = MaterialTheme.typography.bodyMedium)
                    Text("最近 7 天對上 $n 則・從 ${day.monthValue}/${day.dayOfMonth} 的通知學的", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("左滑刪除；點了看原本那則、重新標", style = MaterialTheme.typography.labelSmall, color = cute.sub)
    }
}
