package tw.moneybook.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.res.ResourcesCompat
import tw.moneybook.app.Defaults
import tw.moneybook.app.LAYER_ICON
import tw.moneybook.app.LAYER_TEXT
import tw.moneybook.app.LAYER_TEXT_COLORS
import tw.moneybook.app.LAYER_TEXT_SIZE
import tw.moneybook.app.MASCOT_STICKER
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.PicLayer
import tw.moneybook.app.R
import tw.moneybook.app.describe
import tw.moneybook.app.hitLayer
import tw.moneybook.app.moved
import tw.moneybook.app.rotated
import tw.moneybook.app.snapped
import tw.moneybook.app.toImage
import tw.moneybook.app.zoomed
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ───────────────────────── 畫貼紙、文字 ─────────────────────────

/** 貼紙要用的圖：內建圖片、吉祥物、自己的圖片（表情符號用字畫，這裡回傳 null） */
object LayerArt {
    private val cache = HashMap<String, Bitmap>()
    private var font: Typeface? = null

    /** 文字用 App 的粉圓體 */
    fun typeface(ctx: Context): Typeface =
        font ?: (try { ResourcesCompat.getFont(ctx, R.font.huninn) } catch (_: Exception) { null } ?: Typeface.DEFAULT_BOLD).also { font = it }

    @Synchronized
    fun bitmap(ctx: Context, v: String): Bitmap? {
        if (UserPics.isPic(v)) return UserPics.bitmap(UserPics.idOf(v))
        cache[v]?.let { return it }
        val res = when {
            v.startsWith(MASCOT_STICKER) -> {
                val p = v.removePrefix(MASCOT_STICKER).split(':')
                mascotRes(p.getOrElse(0) { "deer" }, runCatching { Mood.valueOf(p.getOrElse(1) { "HAPPY" }) }.getOrDefault(Mood.HAPPY))
            }
            AppImages.isImg(v) -> AppImages.all[v]
            else -> null
        } ?: return null
        val b = BitmapFactory.decodeResource(ctx.resources, res) ?: return null
        cache[v] = b
        return b
    }
}

private fun textPaint(ctx: Context, l: PicLayer, side: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = LayerArt.typeface(ctx)
    textSize = l.size * side
    textAlign = Paint.Align.CENTER
}

/** 貼紙、文字的寬高（佔圖片邊長的比例）：點選、虛線框、「×」的位置用 */
fun layerExtent(ctx: Context, l: PicLayer): Pair<Float, Float> = if (l.kind == LAYER_TEXT) {
    val p = textPaint(ctx, l, 100f)
    Pair((p.measureText(l.value) + if (l.outline) l.size * 100f * 0.3f else 0f) / 100f, l.size * 1.25f)
} else {
    Pair(l.size, l.size)
}

/** 把貼紙、文字畫在 side×side 的圖上（畫面上看的、存起來的都用這個，才會一模一樣） */
fun drawLayers(ctx: Context, c: android.graphics.Canvas, side: Float, layers: List<PicLayer>) {
    val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    layers.forEach { l ->
        c.save()
        c.translate(l.x * side, l.y * side)
        c.rotate(l.rot)
        val s = l.size * side
        if (l.kind == LAYER_TEXT) {
            val p = textPaint(ctx, l, side)
            val fm = p.fontMetrics
            val by = -(fm.ascent + fm.descent) / 2f
            if (l.outline) {
                val st = Paint(p).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = s * 0.18f
                    strokeJoin = Paint.Join.ROUND
                    color = android.graphics.Color.WHITE
                }
                c.drawText(l.value, 0f, by, st)
            }
            p.color = LAYER_TEXT_COLORS.getOrElse(l.color) { LAYER_TEXT_COLORS[0] }.toInt()
            c.drawText(l.value, 0f, by, p)
        } else {
            val b = LayerArt.bitmap(ctx, l.value)
            if (b != null) {
                val k = s / max(b.width, b.height)
                val w = b.width * k
                val h = b.height * k
                c.drawBitmap(b, null, RectF(-w / 2f, -h / 2f, w / 2f, h / 2f), bp)
            } else {
                // 表情符號
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = s * 0.82f; textAlign = Paint.Align.CENTER }
                val fm = p.fontMetrics
                c.drawText(l.value, 0f, -(fm.ascent + fm.descent) / 2f, p)
            }
        }
        c.restore()
    }
}

/** 底圖加上貼紙、文字，合成存檔用的圖（PIC_SIZE×PIC_SIZE） */
fun composePic(ctx: Context, base: Bitmap, layers: List<PicLayer>, out: Int = PIC_SIZE): Bitmap {
    val b = Bitmap.createBitmap(out, out, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    c.drawBitmap(base, null, android.graphics.Rect(0, 0, out, out), Paint(Paint.FILTER_BITMAP_FLAG))
    drawLayers(ctx, c, out.toFloat(), layers)
    return b
}

// ───────────────────────── 加貼紙、文字的畫面 ─────────────────────────

/**
 * 加貼紙、文字：點一下選取，拖動移動，兩指縮放、旋轉；選取的左上角「×」刪掉；點選取中的文字可以改內容。
 * onDone(貼紙和文字, 合成好的圖)：沒有貼紙、文字時合成的圖是 null（直接用底圖）。
 */
@Composable
fun PicDecorateDialog(
    base: Bitmap,
    initial: List<PicLayer>,
    corner: Float,
    ownPics: List<String>,
    onDone: (List<PicLayer>, Bitmap?) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "略過",
) {
    val ctx = LocalContext.current
    val cute = LocalCute.current
    val img = remember(base) { base.asImageBitmap() }
    var layers by remember { mutableStateOf(initial) }
    var sel by remember { mutableIntStateOf(-1) }
    val undo = remember { mutableStateListOf<List<PicLayer>>() }
    var picking by remember { mutableStateOf(false) }
    // 文字對話框：-1＝新增，其他＝改第幾個
    var editing by remember { mutableStateOf<Int?>(null) }
    fun change(next: List<PicLayer>) {
        undo.add(layers)
        layers = next
    }
    fun add(l: PicLayer) {
        // 新放的往右下錯開一點，不會整個疊在前一個上面
        val n = layers.size % 5
        change(layers + l.copy(x = 0.5f + 0.05f * n - 0.1f, y = 0.5f + 0.05f * n - 0.1f))
        sel = layers.size - 1
    }
    val density = LocalDensity.current
    val marginDp = 24.dp
    val marginPx = with(density) { marginDp.toPx() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF2B2421)).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp)) {
            Text("加貼紙、文字", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val boxDp = min(maxWidth.value, maxHeight.value).dp
                Box(Modifier.size(boxDp)) {
                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val side = size.width - marginPx * 2
                                    if (side <= 0f) return@awaitEachGesture
                                    val fx = (down.position.x - marginPx) / side
                                    val fy = (down.position.y - marginPx) / side
                                    val hit = hitLayer(layers, fx, fy) { layerExtent(ctx, it) }
                                    val before = layers
                                    val wasSel = sel
                                    if (hit >= 0) sel = hit
                                    var moved = false
                                    var multi = false
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        val pressed = ev.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        val i = sel
                                        if (pressed.size >= 2) {
                                            multi = true
                                            if (i in layers.indices) {
                                                val z = ev.calculateZoom()
                                                val r = ev.calculateRotation()
                                                val p = ev.calculatePan()
                                                layers = layers.toMutableList().also { it[i] = it[i].zoomed(z).rotated(r).moved(p.x / side, p.y / side) }
                                            }
                                        } else if (!multi && hit >= 0 && i == hit) {
                                            val ch = pressed[0]
                                            val dlt = ch.position - ch.previousPosition
                                            if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                            if (dlt != Offset.Zero) layers = layers.toMutableList().also { it[i] = it[i].moved(dlt.x / side, dlt.y / side) }
                                        }
                                        ev.changes.forEach { it.consume() }
                                    }
                                    if (layers !== before) {
                                        val i = sel
                                        if (i in layers.indices) layers = layers.toMutableList().also { it[i] = it[i].snapped() }
                                        undo.add(before)
                                    }
                                    if (!moved && !multi) {
                                        if (hit < 0) sel = -1
                                        else if (hit == wasSel && layers[hit].kind == LAYER_TEXT) editing = hit
                                    }
                                }
                            }
                            .semantics {
                                contentDescription = "加貼紙的圖：${layers.size} 個" +
                                    (layers.getOrNull(sel)?.let { "，選取 " + it.describe() } ?: "")
                            },
                    ) {
                        val m = marginPx
                        val side = size.width - m * 2
                        translate(m, m) {
                            val frame = Path().apply { addRoundRect(RoundRect(0f, 0f, side, side, CornerRadius(side * corner, side * corner))) }
                            clipPath(frame) {
                                checker(12.dp.toPx())
                                drawImage(img, dstSize = IntSize(side.roundToInt(), side.roundToInt()))
                                drawIntoCanvas { drawLayers(ctx, it.nativeCanvas, side, layers) }
                            }
                            drawPath(frame, Color.White.copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
                            // 選取中的：虛線框（跟著旋轉）
                            layers.getOrNull(sel)?.let { l ->
                                val (w, h) = layerExtent(ctx, l)
                                drawIntoCanvas { cv ->
                                    val nc = cv.nativeCanvas
                                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                        style = Paint.Style.STROKE
                                        strokeWidth = 2.dp.toPx()
                                        color = android.graphics.Color.WHITE
                                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()), 0f)
                                    }
                                    nc.save()
                                    nc.translate(l.x * side, l.y * side)
                                    nc.rotate(l.rot)
                                    nc.drawRect(-w * side / 2f, -h * side / 2f, w * side / 2f, h * side / 2f, p)
                                    nc.restore()
                                }
                            }
                        }
                    }
                    // 「×」放在選取中那個的左上角（跟著旋轉）
                    layers.getOrNull(sel)?.let { l ->
                        val (w, h) = layerExtent(ctx, l)
                        val (cx, cy) = l.toImage(-w / 2f, -h / 2f)
                        val sidePx = with(density) { boxDp.toPx() } - marginPx * 2
                        val r = with(density) { 14.dp.toPx() }
                        val px = (marginPx + cx * sidePx).coerceIn(r, marginPx * 2 + sidePx - r)
                        val py = (marginPx + cy * sidePx).coerceIn(r, marginPx * 2 + sidePx - r)
                        Box(
                            Modifier.offset { IntOffset((px - r).roundToInt(), (py - r).roundToInt()) }.size(28.dp).clip(CircleShape)
                                .background(cute.expense)
                                .clickable { change(layers.filterIndexed { i, _ -> i != sel }); sel = -1 }
                                .semantics { contentDescription = "刪掉這個" },
                            contentAlignment = Alignment.Center,
                        ) { Text("×", color = Color.White, fontSize = 18.sp) }
                    }
                }
            }
            Text(
                if (sel >= 0 && layers.getOrNull(sel)?.kind == LAYER_TEXT) "拖動、兩指縮放和旋轉；再點一下文字可以修改"
                else "點一下選取；拖動、兩指縮放和旋轉",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8CFC8), modifier = Modifier.padding(vertical = 6.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("實際大小", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8CFC8))
                LayerPreview(img, layers, 40.dp, corner)
                LayerPreview(img, layers, 26.dp, corner)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dark = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A403C))
                Button(onClick = { picking = true }, modifier = Modifier.weight(1f), colors = dark) { Text("＋ 貼紙", color = Color.White) }
                Button(onClick = { editing = -1 }, modifier = Modifier.weight(1f), colors = dark) { Text("＋ 文字", color = Color.White) }
                Button(
                    onClick = { if (undo.isNotEmpty()) { layers = undo.removeAt(undo.size - 1); sel = -1 } },
                    modifier = Modifier.weight(1f), colors = dark,
                ) { Text("復原", color = if (undo.isNotEmpty()) Color.White else Color(0xFF9A8F88)) }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A403C))) {
                    Text(dismissLabel, color = Color.White)
                }
                Button(onClick = { onDone(layers, if (layers.isEmpty()) null else composePic(ctx, base, layers)) }, modifier = Modifier.weight(1f)) { Text("完成") }
            }
        }
    }
    if (picking) StickerPickerDialog(ownPics, { v -> picking = false; add(PicLayer(LAYER_ICON, v)) }, { picking = false })
    editing?.let { i ->
        val cur = layers.getOrNull(i)
        TextLayerDialog(
            cur,
            { text, color, outline ->
                if (cur == null) add(PicLayer(LAYER_TEXT, text, size = LAYER_TEXT_SIZE, color = color, outline = outline))
                else change(layers.toMutableList().also { it[i] = cur.copy(value = text, color = color, outline = outline) })
                editing = null
            },
            if (cur != null) ({ change(layers.filterIndexed { k, _ -> k != i }); sel = -1; editing = null }) else null,
            { editing = null },
        )
    }
}

/** 實際大小：記錄列、帳戶列上看起來的樣子 */
@Composable
private fun LayerPreview(img: ImageBitmap, layers: List<PicLayer>, size: Dp, corner: Float) {
    val ctx = LocalContext.current
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size * corner))) {
        drawImage(img, dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()))
        drawIntoCanvas { drawLayers(ctx, it.nativeCanvas, this.size.width, layers) }
    }
}

/** 選一個貼紙：內建圖案、表情符號、吉祥物、自己的圖片 */
@Composable
fun StickerPickerDialog(ownPics: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val mascots = remember { MascotKinds.flatMap { (k, _) -> Mood.values().map { "$MASCOT_STICKER$k:${it.name}" } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選一個貼紙") },
        text = {
            Column {
                PillSegment(listOf("圖案", "表情", "吉祥物", "自己的"), tab, { tab = it }, Modifier.fillMaxWidth(), equal = true)
                Spacer(Modifier.height(10.dp))
                val list = when (tab) {
                    0 -> AppImages.all.keys.toList()
                    1 -> Defaults.emojis
                    2 -> mascots
                    else -> ownPics
                }
                if (list.isEmpty()) {
                    Text("還沒有自己的圖片。帳本、帳戶、分類用過的圖片會出現在這裡。", style = MaterialTheme.typography.bodySmall, color = LocalCute.current.sub)
                    return@Column
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(list) { e ->
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable { onPick(e) }.semantics { contentDescription = "貼紙 $e" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (e.startsWith(MASCOT_STICKER)) {
                                val p = e.removePrefix(MASCOT_STICKER).split(':')
                                Image(painterResource(mascotRes(p[0], Mood.valueOf(p[1]))), null, Modifier.size(40.dp))
                            } else {
                                IconGlyph(e, 26.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}

/** 加文字／改文字：內容、顏色、白色外框 */
@Composable
private fun TextLayerDialog(init: PicLayer?, onDone: (String, Int, Boolean) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(init?.value ?: "") }
    var color by remember { mutableIntStateOf(init?.color ?: 0) }
    var outline by remember { mutableStateOf(init?.outline ?: true) }
    val tries = rememberNeedTries()
    val need = firstNeed(if (text.isBlank()) Need("text", "請輸入文字") else null)
    val nv = NeedView(need, tries.count)
    val cute = LocalCute.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (init == null) "加文字" else "改文字") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    text, { text = it.take(8) }, placeholder = { Text("例如 玉山、早餐") }, singleLine = true,
                    isError = nv.on("text"), supportingText = nv.supporting("text"), modifier = Modifier.fillMaxWidth(),
                )
                Text("顏色", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LAYER_TEXT_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .border(if (i == color) 3.dp else 1.dp, if (i == color) MaterialTheme.colorScheme.onSurface else cute.sub.copy(alpha = 0.4f), CircleShape)
                                .clickable { color = i }
                                .semantics { contentDescription = "文字顏色 ${i + 1}" + if (i == color) "（選了）" else "" }
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("白色外框", modifier = Modifier.weight(1f))
                    Switch(outline, { outline = it })
                }
                Text("圖示很小，建議 1～2 個字；放上去後可以拖動、兩指縮放和旋轉。", style = MaterialTheme.typography.labelSmall, color = cute.sub)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (need != null) tries.count++ else onDone(text.trim(), color, outline) }) { Text(if (init == null) "放上去" else "好") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("刪掉", color = cute.expense) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

// ───────────────────────── 自己的圖片：選圖、調整位置、去背、加貼紙的流程 ─────────────────────────

/**
 * 帳本、帳戶、分類共用的「自己的圖片」流程：
 * 選相片 → 調整位置 →（cutout 才有）去背 → 加貼紙、文字（可以略過）→ 存成 pic:<id> 交給 onResult。
 * 已經存好的圖片可以再加貼紙（redecorate）、再去背（recut）；這次剛選的圖可以再調整位置（recrop）。
 */
class OwnPicFlow internal constructor(internal val corner: Float, internal val cutout: Boolean) {
    internal var stage by mutableStateOf("")
    /** 這次剛選的原圖（調整位置用；之前存的圖片沒有留原圖） */
    internal var cropSrc by mutableStateOf<Bitmap?>(null)
    internal var cutSrc by mutableStateOf<Bitmap?>(null)
    internal var decoBase by mutableStateOf<Bitmap?>(null)
    internal var decoSrc by mutableStateOf<Bitmap?>(null)
    /** 重新調整位置時，原本的貼紙、文字帶過去 */
    internal var carry by mutableStateOf<List<PicLayer>>(emptyList())
    internal var launch: () -> Unit = {}
    val canRecrop: Boolean get() = cropSrc != null

    fun pick() = launch()
    fun recrop(current: String) {
        if (cropSrc == null) return
        carry = if (UserPics.isPic(current)) UserPics.layers(UserPics.idOf(current)) else emptyList()
        stage = "crop"
    }
    fun redecorate() { stage = "redeco" }
    fun recut() { stage = "recut" }
}

@Composable
fun rememberOwnPicFlow(corner: Float, cutout: Boolean): OwnPicFlow {
    val flow = remember { OwnPicFlow(corner, cutout) }
    val ctx = LocalContext.current
    flow.launch = rememberPicPicker { b ->
        if (b != null) {
            flow.cropSrc = b
            flow.carry = emptyList()
            flow.stage = "crop"
        } else {
            android.widget.Toast.makeText(ctx, "讀不到這張圖片", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    return flow
}

/** 流程中的各個畫面；放在對話框後面（current＝現在的圖示字串） */
@Composable
fun OwnPicFlowDialogs(flow: OwnPicFlow, vm: MoneyViewModel, current: String, onResult: (String) -> Unit) {
    val ctx = LocalContext.current
    fun choices(): List<String> = vm.ownPicChoices().filter { it != current }
    when (flow.stage) {
        "crop" -> flow.cropSrc?.let { src ->
            PicCropDialog(src, flow.corner, { out ->
                if (flow.cutout) {
                    flow.cutSrc = out
                    flow.stage = "cut"
                } else {
                    flow.decoBase = out
                    flow.decoSrc = null
                    flow.stage = "deco"
                }
            }, { flow.stage = "" })
        }
        "cut" -> flow.cutSrc?.let { src ->
            PicCutoutDialog(
                src, null,
                { fin -> flow.decoBase = fin; flow.decoSrc = src; flow.stage = "deco" },
                { flow.decoBase = src; flow.decoSrc = null; flow.stage = "deco" },
                dismissLabel = "不去背",
            )
        }
        "deco" -> flow.decoBase?.let { base ->
            PicDecorateDialog(
                base, flow.carry, flow.corner, choices(),
                { layers, out -> onResult(vm.addPic(out ?: base, flow.decoSrc, base, layers)); flow.stage = "" },
                { onResult(vm.addPic(base, flow.decoSrc)); flow.stage = "" },
            )
        }
        "redeco" -> {
            val id = UserPics.idOf(current)
            val base = remember(id) { UserPics.base(id) }
            if (base == null || !UserPics.isPic(current)) {
                LaunchedEffect(Unit) { flow.stage = "" }
            } else {
                PicDecorateDialog(
                    base, UserPics.layers(id), flow.corner, choices(),
                    { layers, out -> vm.redecoratePic(id, out, layers)?.let(onResult); flow.stage = "" },
                    { flow.stage = "" },
                    dismissLabel = "取消",
                )
            }
        }
        "recut" -> {
            val id = UserPics.idOf(current)
            val src = remember(id) { UserPics.source(id) }
            val keep = remember(id) {
                val cur = UserPics.base(id)
                if (src == null || cur == null || cur.width != src.width || cur.height != src.height) null
                else {
                    val pxs = IntArray(cur.width * cur.height)
                    cur.getPixels(pxs, 0, cur.width, 0, 0, cur.width, cur.height)
                    BooleanArray(pxs.size) { (pxs[it] ushr 24) > 16 }
                }
            }
            if (src == null || !UserPics.isPic(current)) {
                LaunchedEffect(Unit) { flow.stage = "" }
            } else {
                PicCutoutDialog(
                    src, keep ?: BooleanArray(src.width * src.height) { true },
                    { fin ->
                        // 去背改的是底圖，原本的貼紙、文字照樣貼回去
                        val layers = UserPics.layers(id)
                        val out = if (layers.isEmpty()) null else composePic(ctx, fin, layers)
                        onResult(vm.addPic(out ?: fin, src, fin, layers))
                        flow.stage = ""
                    },
                    { flow.stage = "" },
                    dismissLabel = "取消",
                )
            }
        }
    }
}
