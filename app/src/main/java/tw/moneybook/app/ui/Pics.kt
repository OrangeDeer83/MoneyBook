package tw.moneybook.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tw.moneybook.app.UserPic
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 自己的圖片存成多大（正方形，像素） */
const val PIC_SIZE = 256

/** 圖示字串「pic:<id>」：使用者自己的圖片（帳戶、分類圖示用） */
object UserPics {
    const val PREFIX = "pic:"
    fun isPic(s: String) = s.startsWith(PREFIX)
    fun idOf(s: String) = s.removePrefix(PREFIX)

    @Volatile private var data: Map<String, UserPic> = emptyMap()
    private val bitmaps = HashMap<String, Bitmap>()
    private val images = HashMap<String, ImageBitmap>()

    /** 資料變了（加了新圖片、還原備份）就換成新的；畫面上用的時候才解碼 */
    @Synchronized
    fun sync(pics: Map<String, UserPic>) {
        if (pics === data) return
        val gone = bitmaps.keys.filter { pics[it]?.png != data[it]?.png }
        gone.forEach { bitmaps.remove(it); images.remove(it) }
        data = pics
    }

    @Synchronized
    fun bitmap(id: String): Bitmap? {
        bitmaps[id]?.let { return it }
        val b = data[id]?.png?.let { decodeB64(it) } ?: return null
        bitmaps[id] = b
        return b
    }

    @Synchronized
    fun image(id: String): ImageBitmap? {
        images[id]?.let { return it }
        val img = bitmap(id)?.asImageBitmap() ?: return null
        images[id] = img
        return img
    }

    /** 去背前的原圖（再去背、補回用）；沒有就用存好的那張 */
    fun source(id: String): Bitmap? = data[id]?.src?.takeIf { it.isNotEmpty() }?.let { decodeB64(it) } ?: bitmap(id)
}

private fun decodeB64(s: String): Bitmap? = try {
    val bytes = Base64.decode(s, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (_: Exception) {
    null
}

/** 圖片轉成存檔用的文字：有透明的存 PNG，沒有透明的原圖存 JPEG（小一點） */
fun encodePic(b: Bitmap, allowJpeg: Boolean = false): String {
    val out = ByteArrayOutputStream()
    val opaque = allowJpeg && !hasTransparent(b)
    b.compress(if (opaque) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, if (opaque) 88 else 100, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun hasTransparent(b: Bitmap): Boolean {
    if (!b.hasAlpha()) return false
    val px = IntArray(b.width * b.height)
    b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
    return px.any { (it ushr 24) < 255 }
}

/** 讀使用者選的圖片：太大的先縮小（長邊最多 1600），照片的旋轉方向照手機記的；透明的部分保留 */
fun loadPicked(ctx: Context, uri: Uri): Bitmap? = try {
    if (Build.VERSION.SDK_INT >= 28) {
        val src = android.graphics.ImageDecoder.createSource(ctx.contentResolver, uri)
        android.graphics.ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            val s = info.size
            val k = max(s.width, s.height) / 1600f
            if (k > 1f) dec.setTargetSize((s.width / k).roundToInt(), (s.height / k).roundToInt())
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }
} catch (_: Exception) {
    null
}

/** 打開手機內建的選相片畫面（不用讀取相片的權限，只拿得到選的那一張） */
@Composable
fun rememberPicPicker(onPicked: (Bitmap?) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(loadPicked(ctx, uri))
    }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** 顯示自己的圖片（填滿 size 的圓角方形）；找不到圖片就留空 */
@Composable
fun PicIcon(e: String, size: Dp, corner: Float = 0.32f) {
    val img = UserPics.image(UserPics.idOf(e))
    if (img != null) {
        Image(img, null, Modifier.size(size).clip(RoundedCornerShape(size * corner)), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(size * corner)).background(LocalCute.current.soft))
    }
}

/** 灰白格子：已經變透明的地方 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.checker(cell: Float) {
    val a = Color(0xFFFFFFFF)
    val b = Color(0xFFE2E2E2)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var col = 0
        while (x < size.width) {
            drawRect(if ((row + col) % 2 == 0) a else b, Offset(x, y), Size(min(cell, size.width - x), min(cell, size.height - y)))
            x += cell
            col++
        }
        y += cell
        row++
    }
}

/**
 * 調整位置：圖片可以拖動、兩指縮放，中間圓角方框裡的部分變成圖示（corner＝圓角佔邊長的比例，帳戶 0.32、分類 0.36）。
 * 按「完成」切出框裡的範圍，縮成 PIC_SIZE×PIC_SIZE 交給 onDone；透明的部分保留。
 */
@Composable
fun PicCropDialog(source: Bitmap, corner: Float, onDone: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    val img = remember(source) { source.asImageBitmap() }
    var scale by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val margin = with(density) { 28.dp.toPx() }
    fun frame(): Float = min(box.width, box.height) - margin * 2
    fun minScale(): Float = frame() / min(source.width, source.height)
    fun reset() {
        scale = minScale()
        offset = Offset((box.width - source.width * scale) / 2f, (box.height - source.height * scale) / 2f)
    }
    // 圖片一定要蓋滿框（不能拖到框裡有空白）
    fun clamp() {
        val f = frame()
        val fl = (box.width - f) / 2f
        val ft = (box.height - f) / 2f
        val w = source.width * scale
        val h = source.height * scale
        // 圖片剛好跟框一樣大時，小數誤差可能讓下限比上限大一點點：取兩者的大小關係再限制，不然會閃退
        fun fit(v: Float, a: Float, b: Float) = v.coerceIn(min(a, b), max(a, b))
        offset = Offset(fit(offset.x, fl + f - w, fl), fit(offset.y, ft + f - h, ft))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF2B2421)).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("調整圖示範圍", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFFD8CFC8)) }
            }
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .onSizeChanged { box = it; if (scale == 0f && it.width > 0) reset() }
                    .pointerInput(source) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val ev = awaitPointerEvent()
                                val zoom = ev.calculateZoom()
                                val pan = ev.calculatePan()
                                val c = ev.calculateCentroid()
                                if (scale > 0f && frame() > 0f) {
                                    val lo = minScale()
                                    val ns = (scale * zoom).coerceIn(lo, lo * 8f)
                                    val k = ns / scale
                                    offset = Offset(c.x - (c.x - offset.x) * k, c.y - (c.y - offset.y) * k) + pan
                                    scale = ns
                                    clamp()
                                }
                                ev.changes.forEach { it.consume() }
                            } while (ev.changes.any { it.pressed })
                        }
                    }
                    .semantics { contentDescription = "調整圖示範圍" },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    if (scale <= 0f) return@Canvas
                    drawImage(
                        img, dstOffset = IntOffset(offset.x.roundToInt(), offset.y.roundToInt()),
                        dstSize = IntSize((source.width * scale).roundToInt(), (source.height * scale).roundToInt()),
                    )
                    val f = frame()
                    val l = (size.width - f) / 2f
                    val t = (size.height - f) / 2f
                    val rr = RoundRect(l, t, l + f, t + f, CornerRadius(f * corner, f * corner))
                    val p = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                        addRoundRect(rr)
                    }
                    drawPath(p, Color.Black.copy(alpha = 0.55f))
                    drawPath(Path().apply { addRoundRect(rr) }, Color.White, style = Stroke(width = 2.dp.toPx()))
                }
            }
            Text("拖動、兩指縮放，亮的框裡就是圖示", style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8CFC8), modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { reset() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A403C))) { Text("重來", color = Color.White) }
                Button(onClick = {
                    val f = frame()
                    val l = (box.width - f) / 2f
                    val t = (box.height - f) / 2f
                    val sx = ((l - offset.x) / scale).roundToInt().coerceIn(0, source.width - 1)
                    val sy = ((t - offset.y) / scale).roundToInt().coerceIn(0, source.height - 1)
                    val sw = (f / scale).roundToInt().coerceIn(1, source.width - sx)
                    val sh = (f / scale).roundToInt().coerceIn(1, source.height - sy)
                    val out = Bitmap.createBitmap(PIC_SIZE, PIC_SIZE, Bitmap.Config.ARGB_8888)
                    android.graphics.Canvas(out).drawBitmap(
                        source, android.graphics.Rect(sx, sy, sx + sw, sy + sh), android.graphics.Rect(0, 0, PIC_SIZE, PIC_SIZE),
                        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
                    )
                    onDone(out)
                }, modifier = Modifier.weight(1f)) { Text("完成") }
            }
        }
    }
}

/**
 * 點背景：從 (x, y) 開始，把顏色跟那一點相近、而且連在一起的範圍去掉（keep 改成 false）。
 * tol 0～100：越大去掉的顏色範圍越寬。回傳有沒有改到。
 */
fun floodClear(px: IntArray, keep: BooleanArray, w: Int, h: Int, x: Int, y: Int, tol: Int): Boolean {
    if (x !in 0 until w || y !in 0 until h) return false
    val seed = y * w + x
    if (!keep[seed]) return false
    val c = px[seed]
    val r0 = (c shr 16) and 0xff
    val g0 = (c shr 8) and 0xff
    val b0 = c and 0xff
    val limit = tol * 3 * 255 / 100 / 2      // 三個顏色差加起來的上限
    val stack = IntArray(w * h)
    var top = 0
    stack[top++] = seed
    keep[seed] = false
    var changed = false
    while (top > 0) {
        val i = stack[--top]
        changed = true
        val ix = i % w
        val iy = i / w
        for (k in 0 until 4) {
            val nx = ix + if (k == 0) 1 else if (k == 1) -1 else 0
            val ny = iy + if (k == 2) 1 else if (k == 3) -1 else 0
            if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
            val j = ny * w + nx
            if (!keep[j]) continue
            val p = px[j]
            val d = kotlin.math.abs(((p shr 16) and 0xff) - r0) + kotlin.math.abs(((p shr 8) and 0xff) - g0) + kotlin.math.abs((p and 0xff) - b0)
            if (d <= limit) {
                keep[j] = false
                stack[top++] = j
            }
        }
    }
    return changed
}

/** 用圓形筆刷塗：on＝true 補回、false 擦掉 */
fun brush(keep: BooleanArray, w: Int, h: Int, cx: Float, cy: Float, r: Float, on: Boolean) {
    val x0 = (cx - r).toInt().coerceAtLeast(0)
    val x1 = (cx + r).toInt().coerceAtMost(w - 1)
    val y0 = (cy - r).toInt().coerceAtLeast(0)
    val y1 = (cy + r).toInt().coerceAtMost(h - 1)
    val r2 = r * r
    for (y in y0..y1) for (x in x0..x1) {
        val dx = x - cx
        val dy = y - cy
        if (dx * dx + dy * dy <= r2) keep[y * w + x] = on
    }
}

/**
 * 去背：點背景（顏色相近、連在一起的整片去掉）、擦掉、補回，可以復原；一指操作、兩指縮放移動。
 * source 是沒去背的圖（PIC_SIZE 見方），initialKeep 是目前留下來的部分（第一次就是不透明的地方）。
 */
@Composable
fun PicCutoutDialog(source: Bitmap, initialKeep: BooleanArray?, onDone: (Bitmap) -> Unit, onDismiss: () -> Unit, dismissLabel: String = "取消") {
    val w = source.width
    val h = source.height
    val px = remember(source) { IntArray(w * h).also { source.getPixels(it, 0, w, 0, 0, w, h) } }
    val keep = remember(source) { initialKeep?.copyOf() ?: BooleanArray(w * h) { (px[it] ushr 24) > 16 } }
    val undo = remember(source) { ArrayDeque<BooleanArray>() }
    var ver by remember { mutableIntStateOf(0) }
    var mode by remember { mutableIntStateOf(0) }       // 0 點背景、1 擦掉、2 補回
    var tol by remember { mutableFloatStateOf(30f) }
    var brushPx by remember { mutableFloatStateOf(12f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    fun render(): Bitmap {
        val out = IntArray(w * h) { if (keep[it]) px[it] else 0 }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
    val shown = remember(ver) { render().asImageBitmap() }
    fun push(snapshot: BooleanArray) {
        undo.addLast(snapshot)
        if (undo.size > 30) undo.removeFirst()
    }
    val modes = listOf("點背景", "擦掉", "補回")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color(0xFF2B2421)).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("去背", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    undo.removeLastOrNull()?.let { s -> s.copyInto(keep); ver++ }
                }, enabled = undo.isNotEmpty() || ver < 0) { Text("復原", color = Color(0xFFD8CFC8)) }
                TextButton(onClick = onDismiss) { Text(dismissLabel, color = Color(0xFFD8CFC8)) }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val side = min(constraints.maxWidth, constraints.maxHeight).toFloat()
                val sideDp = with(LocalDensity.current) { side.toDp() }
                Box(
                    Modifier.size(sideDp).clip(RoundedCornerShape(6.dp))
                        .pointerInput(mode, side) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                val snap = keep.copyOf()
                                var multi = false
                                var moved = false
                                var last = down.position
                                var touched = false
                                fun toImg(p: Offset): Offset {
                                    val k = side / w * zoom
                                    return Offset((p.x - pan.x) / k, (p.y - pan.y) / k)
                                }
                                fun paint(a: Offset, b: Offset) {
                                    val ia = toImg(a)
                                    val ib = toImg(b)
                                    val dist = (ib - ia).getDistance()
                                    val steps = max(1, (dist / (brushPx / 2f)).toInt())
                                    for (s in 0..steps) {
                                        val t = s / steps.toFloat()
                                        brush(keep, w, h, ia.x + (ib.x - ia.x) * t, ia.y + (ib.y - ia.y) * t, brushPx, mode == 2)
                                    }
                                    touched = true
                                    ver++
                                }
                                if (mode != 0) paint(last, last)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val pressed = ev.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        if (!multi) {
                                            multi = true
                                            if (touched) { snap.copyInto(keep); ver++ }
                                        }
                                        val z = ev.calculateZoom()
                                        val p = ev.calculatePan()
                                        val c = ev.calculateCentroid()
                                        val nz = (zoom * z).coerceIn(1f, 6f)
                                        val k = nz / zoom
                                        pan = Offset(c.x - (c.x - pan.x) * k, c.y - (c.y - pan.y) * k) + p
                                        zoom = nz
                                        if (zoom == 1f) pan = Offset.Zero
                                    } else if (!multi) {
                                        val p = pressed[0].position
                                        if ((p - down.position).getDistance() > 10f) moved = true
                                        if (mode != 0) paint(last, p)
                                        last = p
                                    }
                                    ev.changes.forEach { it.consume() }
                                }
                                if (!multi) {
                                    if (mode == 0 && !moved) {
                                        val ip = toImg(down.position)
                                        if (floodClear(px, keep, w, h, ip.x.toInt(), ip.y.toInt(), tol.roundToInt())) {
                                            push(snap)
                                            ver++
                                        }
                                    } else if (touched) push(snap)
                                }
                            }
                        }
                        .semantics { contentDescription = "去背的圖" },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        checker(12.dp.toPx())
                        val k = size.width / w * zoom
                        clipRect {
                            drawImage(
                                shown, dstOffset = IntOffset(pan.x.roundToInt(), pan.y.roundToInt()),
                                dstSize = IntSize((w * k).roundToInt(), (h * k).roundToInt()),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEachIndexed { i, l ->
                    Button(
                        onClick = { mode = i },
                        colors = ButtonDefaults.buttonColors(containerColor = if (mode == i) MaterialTheme.colorScheme.primary else Color(0xFF4A403C)),
                        modifier = Modifier.weight(1f),
                    ) { Text(l, color = Color.White) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (mode == 0) "相近程度" else "筆刷大小", color = Color(0xFFD8CFC8), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                if (mode == 0) Slider(tol, { tol = it }, valueRange = 2f..80f, modifier = Modifier.weight(1f))
                else Slider(brushPx, { brushPx = it }, valueRange = 3f..40f, modifier = Modifier.weight(1f))
            }
            Text(
                when (mode) {
                    0 -> "點一下背景：顏色相近、連在一起的整片去掉"
                    1 -> "用手指塗：塗到的地方去掉"
                    else -> "用手指塗：不小心去掉的地方塗回來"
                } + "；兩指可以放大",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFD8CFC8),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onDone(render()) }, modifier = Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}
