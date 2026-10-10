@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package tw.moneybook.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.AppData
import tw.moneybook.app.Calc
import tw.moneybook.app.FxMode
import tw.moneybook.app.cur
import tw.moneybook.app.formatFx
import tw.moneybook.app.fxExpr
import tw.moneybook.app.fxPlain
import tw.moneybook.app.fxPlan
import tw.moneybook.app.fxToTwd
import tw.moneybook.app.impliedRate
import tw.moneybook.app.isForeign
import tw.moneybook.app.parseFx
import tw.moneybook.app.rateOf
import tw.moneybook.app.rateText
import tw.moneybook.app.twdToFx
import tw.moneybook.app.MoneyViewModel
import tw.moneybook.app.TxType
import tw.moneybook.app.ReimbCodec
import tw.moneybook.app.ReimbItem
import tw.moneybook.app.TxnDraft
import tw.moneybook.app.formatMoney
import java.time.LocalDate

private fun defaultCat(d: AppData, type: TxType): Long? =
    if (type == TxType.TRANSFER) null else d.topCategories(type).firstOrNull()?.id

@Composable
fun EditScreen(
    vm: MoneyViewModel,
    editId: Long?,
    presetTo: Long? = null,
    presetAmount: Long? = null,
    tplMode: Boolean = false,
    tplId: Long? = null,
    presetDay: Long? = null,
    presetFrom: Long? = null,
    /** 預設記在哪個帳戶（從帳戶明細按記一筆） */
    presetAcc: Long? = null,
    onClose: () -> Unit,
) {
    val d = vm.data
    val cute = LocalCute.current
    val context = LocalContext.current
    val orig = remember(editId) { editId?.let { id -> d.txns.firstOrNull { it.id == id } } }
    // 常用記帳的金額是台幣整數，不能選外幣帳戶
    val accs = d.visibleAccounts.filter { !tplMode || !it.isForeign }
    val tpl = remember(tplId) { tplId?.let { id -> d.templates.firstOrNull { it.id == id } } }
    var tplName by rememberSaveable { mutableStateOf(tpl?.name ?: "") }
    var showTpl by remember { mutableStateOf(false) }

    var type by rememberSaveable {
        mutableStateOf(orig?.type ?: tpl?.type ?: if (presetTo != null || presetFrom != null) TxType.TRANSFER else TxType.EXPENSE)
    }
    // 外幣：編輯既有的外幣記錄時，算出當初是哪一種外幣模式，計算機和另一邊的金額才放得回去
    val origPlan = remember(editId) { orig?.let { d.fxPlan(it.type, it.accountId, it.toAccountId) } }
    val origDec = origPlan?.acc?.cur?.decimals ?: 0
    var expr by rememberSaveable {
        mutableStateOf(
            when {
                orig != null && origPlan != null && origPlan.mode == FxMode.BUY -> orig.amount.toString()
                orig != null && origPlan != null && origPlan.mode != FxMode.NONE -> fxExpr(orig.fxAmount, origDec)
                else -> orig?.amount?.toString() ?: tpl?.amount?.takeIf { it > 0 }?.toString() ?: presetAmount?.takeIf { it > 0 }?.toString() ?: ""
            }
        )
    }
    var catId by rememberSaveable { mutableStateOf(orig?.categoryId ?: tpl?.categoryId ?: defaultCat(d, type)) }
    var accId by rememberSaveable {
        mutableStateOf(
            orig?.accountId
                ?: tpl?.accountId
                ?: presetFrom
                ?: presetAcc?.takeIf { id -> accs.any { it.id == id } }
                ?: (if (presetTo != null) accs.firstOrNull { it.id != presetTo && it.type != tw.moneybook.app.AccountType.CARD && !it.isForeign }?.id else null)
                ?: accs.firstOrNull()?.id
        )
    }
    var toAccId by rememberSaveable {
        mutableStateOf(
            orig?.toAccountId ?: presetTo
                ?: (if (presetFrom != null) accs.firstOrNull { it.id != presetFrom && !it.isForeign && it.type != tw.moneybook.app.AccountType.CARD }?.id else null)
                ?: accs.getOrNull(1)?.id
        )
    }
    var day by rememberSaveable { mutableLongStateOf(orig?.day ?: presetDay ?: LocalDate.now().toEpochDay()) }
    // 時間：新增預設現在；編輯舊記錄沒有時間就是「未設定」（-1）
    var timeMin by rememberSaveable {
        mutableIntStateOf(
            when {
                orig != null -> orig.time
                tplMode -> -1
                else -> java.time.LocalTime.now().let { it.hour * 60 + it.minute }
            }
        )
    }
    var note by rememberSaveable { mutableStateOf(orig?.note ?: tpl?.note ?: "") }
    var tagsText by rememberSaveable { mutableStateOf((orig?.tags ?: tpl?.tags)?.joinToString("\n") ?: "") }
    var inst by rememberSaveable { mutableIntStateOf(1) }
    var fee by rememberSaveable { mutableLongStateOf(orig?.fee ?: 0L) }
    var discount by rememberSaveable { mutableLongStateOf(orig?.discount ?: 0L) }
    // 報銷：reimbFull = 一個人、全額（金額跟著實付走）；否則用 reimbJson 存多個對象
    val origItems = remember { orig?.items ?: emptyList() }
    var reimbOn by rememberSaveable { mutableStateOf(origItems.isNotEmpty()) }
    var reimbFull by rememberSaveable {
        mutableStateOf(origItems.isEmpty() || (origItems.size == 1 && origItems[0].amount >= (orig?.paid ?: 0L)))
    }
    var reimbWho by rememberSaveable { mutableStateOf(origItems.singleOrNull()?.who ?: "") }
    var reimbJson by rememberSaveable { mutableStateOf(ReimbCodec.encode(origItems)) }
    // 外幣：買賣外幣時「另一邊」的金額（買＝收到的外幣、賣＝收到的台幣）、有沒有手動改過；外幣消費用的匯率（這一筆自己的）
    var otherText by rememberSaveable {
        mutableStateOf(
            when {
                orig == null -> ""
                origPlan?.mode == FxMode.BUY -> fxExpr(orig.fxAmount, origDec)
                origPlan?.mode == FxMode.SELL -> orig.amount.toString()
                else -> ""
            }
        )
    }
    var otherTouched by rememberSaveable { mutableStateOf(origPlan?.mode == FxMode.BUY || origPlan?.mode == FxMode.SELL) }
    var rateUser by rememberSaveable {
        mutableStateOf(
            if (orig != null && (origPlan?.mode == FxMode.SPEND || origPlan?.mode == FxMode.SAME))
                impliedRate(orig.amount, orig.fxAmount, origDec)?.let { rateText(it) } ?: ""
            else ""
        )
    }
    var rateUserCode by rememberSaveable { mutableStateOf(origPlan?.acc?.currency ?: "") }
    // 台幣帳戶刷外幣：外幣金額、幣別、是否還沒請款（見 FxSpendDialog）；金額欄位放預估或實際請款的台幣
    var fxsCur by rememberSaveable { mutableStateOf(orig?.fxSpendCur ?: "") }
    var fxsMinor by rememberSaveable { mutableStateOf(orig?.fxSpendAmount ?: 0L) }
    var fxsPending by rememberSaveable { mutableStateOf(orig?.fxPending ?: false) }
    var noteFocused by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    // 系統鍵盤直接蓋在畫面上，不要把畫面往上推（備註在最上面，不會被蓋到）
    val hostContext = LocalContext.current
    DisposableEffect(Unit) {
        var c = hostContext
        while (c is ContextWrapper && c !is Activity) c = c.baseContext
        val window = (c as? Activity)?.window
        val old = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        onDispose { if (window != null && old != null) window.setSoftInputMode(old) }
    }


    val plan = d.fxPlan(type, accId, if (type == TxType.TRANSFER) toAccId else null)
    val fxAcc = plan.acc
    val dec = fxAcc?.cur?.decimals ?: 0
    // 計算機輸入的是外幣（消費、賣出、同幣別轉帳）還是台幣（一般記帳、買外幣付出的台幣）
    val keyIsFx = plan.mode == FxMode.SPEND || plan.mode == FxMode.SELL || plan.mode == FxMode.SAME
    val keyVal = if (keyIsFx) Calc.evalMinor(expr, dec) else Calc.eval(expr)
    val rate: Double? = fxAcc?.let { a ->
        (if (rateUserCode == a.currency) rateUser.toDoubleOrNull()?.takeIf { it > 0.0 } else null) ?: d.rateOf(a.currency)
    }
    val other: Long? = when (plan.mode) {
        FxMode.BUY -> parseFx(otherText, dec)
        FxMode.SELL -> otherText.filter { it.isDigit() }.toLongOrNull()
        else -> null
    }
    // amount 一律是台幣（統計用）；fxMinor 是外幣帳戶實際增減的外幣金額
    val amount: Long = when (plan.mode) {
        FxMode.SPEND, FxMode.SAME -> if (rate != null) fxToTwd(keyVal, dec, rate) else 0L
        FxMode.SELL -> other ?: 0L
        else -> keyVal
    }
    val fxMinor: Long = when (plan.mode) {
        FxMode.SPEND, FxMode.SAME, FxMode.SELL -> keyVal
        FxMode.BUY -> other ?: 0L
        else -> 0L
    }
    val hasValue = when (plan.mode) {
        FxMode.NONE -> amount > 0
        FxMode.SPEND, FxMode.SAME -> keyVal > 0 && rate != null
        FxMode.BUY -> keyVal > 0 && fxMinor > 0
        FxMode.SELL -> keyVal > 0 && amount > 0
        FxMode.UNSUPPORTED -> false
    }
    val implied: Double? = when (plan.mode) {
        FxMode.BUY -> impliedRate(keyVal, fxMinor, dec)
        FxMode.SELL -> impliedRate(amount, keyVal, dec)
        else -> null
    }
    val fromFx = accId?.let { d.accMap[it] }?.isForeign == true
    val pending = Calc.hasOp(expr)
    val tags = tagsText.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    val effDiscount = if (type == TxType.EXPENSE) discount else 0L
    val actual = when (type) {
        TxType.EXPENSE -> (amount - effDiscount + fee).coerceAtLeast(0L)
        TxType.INCOME -> (amount - fee).coerceAtLeast(0L)
        TxType.TRANSFER -> amount + fee
    }
    val reimbItems: List<ReimbItem> = when {
        type != TxType.EXPENSE || !reimbOn -> emptyList()
        reimbFull -> listOf((origItems.singleOrNull() ?: ReimbItem("", 0L)).copy(who = reimbWho.trim(), amount = actual))
        // 上限跟著目前的金額走：先填報銷、後填金額時，金額還是 0，不能先把報銷金額截成 0
        else -> ReimbCodec.decode(reimbJson).map { if (amount + fee > 0L && it.pays.isEmpty() && !it.closed) it.copy(amount = it.amount.coerceAtMost(amount + fee)) else it }
    }
    // 信用卡的入帳月份：選的是設了結帳日的信用卡、而且是支出／收入才有
    val cardAcc = accId?.let { d.accMap[it] }?.takeIf { it.type == tw.moneybook.app.AccountType.CARD && it.statementDay in 1..31 && type != TxType.TRANSFER && !tplMode }
    var billMonth by rememberSaveable { mutableIntStateOf(orig?.billMonth ?: 0) }
    val billAuto: java.time.YearMonth? = cardAcc?.let { tw.moneybook.app.autoBillMonth(it.statementDay, LocalDate.ofEpochDay(day)) }
    val billManual = tw.moneybook.app.billMonthFromCode(billMonth)
    val billEff: java.time.YearMonth? = billAuto?.let { billManual ?: it }
    // 和自動算的一樣就當作沒指定（存 0）
    val billSet = if (billAuto != null && billManual != null && billManual != billAuto) billMonth else 0
    val cat = catId?.let { d.catMap[it] }
    val parent = cat?.let { d.topOf(it) }
    // 外幣消費：一般（非外幣、非投資）帳戶的支出才有，分期不支援
    val fxsAcc = accId?.let { d.accMap[it] }
    val fxsEligible = !tplMode && type == TxType.EXPENSE && plan.mode == FxMode.NONE && fxsAcc != null && !fxsAcc.isForeign &&
        fxsAcc.type != tw.moneybook.app.AccountType.INVEST && inst <= 1 && (orig == null || orig.instTotal <= 1)
    val fxsOn = fxsEligible && fxsCur.isNotEmpty() && fxsMinor > 0L
    val targetOk = when (type) {
        TxType.TRANSFER -> accId != null && toAccId != null && accId != toAccId
        else -> catId != null
    }
    val canSave = if (tplMode) targetOk else hasValue && targetOk
    // 必填：按「完成」才檢查，照畫面由上到下只標第一個沒填好的（見 Need.kt）
    val tries = rememberNeedTries()
    val need = firstNeed(
        when {
            targetOk -> null
            type == TxType.TRANSFER -> Need("target", if (accs.size < 2) "轉帳需要至少兩個帳戶，可以到「我的 → 帳戶管理」新增" else "請選擇兩個不同的帳戶")
            else -> Need("target", "請選擇分類")
        },
        if (type == TxType.TRANSFER && plan.mode == FxMode.UNSUPPORTED) Need("target", "不同幣別的外幣帳戶之間不能直接轉帳，請先換回台幣，再買另一種外幣") else null,
        if (tplMode) null else when {
            (plan.mode == FxMode.BUY || plan.mode == FxMode.SELL) && keyVal > 0 && !hasValue ->
                Need("other", if (plan.mode == FxMode.BUY) "請輸入收到的外幣金額" else "請輸入收到的台幣金額")
            (plan.mode == FxMode.SPEND || plan.mode == FxMode.SAME) && keyVal > 0 && rate == null -> Need("rate", "請先設定匯率（點金額下面的字）")
            !hasValue -> Need("amount", if (plan.mode == FxMode.BUY || plan.mode == FxMode.SELL) "請輸入付出的金額" else "請先輸入金額")
            else -> null
        },
    )
    val nv = NeedView(need, tries.count)

    fun draft() = TxnDraft(
        type = type, amount = amount,
        categoryId = if (type == TxType.TRANSFER) null else catId,
        accountId = accId,
        toAccountId = if (type == TxType.TRANSFER) toAccId else null,
        day = day, note = note.trim(), tags = tags, time = if (tplMode) -1 else timeMin,
        installments = if (orig == null && type == TxType.EXPENSE) inst else 1,
        fee = fee,
        discount = effDiscount,
        reimbItems = reimbItems,
        fxAmount = fxMinor,
        billMonth = billSet,
        fxSpendAmount = if (fxsOn) fxsMinor else 0L,
        fxSpendCur = if (fxsOn) fxsCur else "",
        fxPending = fxsOn && fxsPending,
    )

    val initialDraft = remember { draft() }
    val initialName = remember { tplName }
    val dirty = draft() != initialDraft || (tplMode && tplName != initialName)

    fun applyTemplate(tp: tw.moneybook.app.Template) {
        if (tp.amount > 0) expr = tp.amount.toString()
        tp.categoryId?.let { c -> if (d.catMap[c] != null) catId = c }
        tp.accountId?.let { a -> if (d.accMap[a]?.hidden == false) accId = a }
        if (tp.note.isNotBlank()) note = tp.note
        if (tp.tags.isNotEmpty()) tagsText = tp.tags.joinToString("\n")
        showTpl = false
    }

    /** 儲存；成功回傳 true */
    fun doSave(): Boolean {
        // 快速連點時，換頁動畫還沒結束、畫面還能點，不能重複儲存
        if (saved) return true
        if (need != null) {
            tries.count++
            return false
        }
        if (tplMode) {
            if (tplName.isBlank()) {
                dialog = "tplname"
                return false
            }
            saved = true
            vm.saveTemplate(tplId, tplName.trim(), draft())
            vm.toast("常用記帳已儲存")
        } else {
            saved = true
            vm.saveTxn(editId, draft())
        }
        onClose()
        return true
    }

    fun tryClose() {
        when {
            noteFocused -> focus.clearFocus()
            dirty -> dialog = "unsaved"
            else -> onClose()
        }
    }

    BackHandler { tryClose() }

    // 買賣外幣：另一邊的金額沒有手動改過，就依匯率帶入（有輸入付出金額、也有匯率時）
    LaunchedEffect(plan.mode, keyVal, rate) {
        if (!otherTouched && (plan.mode == FxMode.BUY || plan.mode == FxMode.SELL)) {
            if (keyVal <= 0L) otherText = ""
            else if (rate != null) otherText = if (plan.mode == FxMode.BUY) fxPlain(twdToFx(keyVal, dec, rate), dec) else fxToTwd(keyVal, dec, rate).toString()
        }
    }
    // 從外幣模式換回台幣時，計算機裡的小數點拿掉（台幣只有整數）
    LaunchedEffect(keyIsFx) {
        if (!keyIsFx && '.' in expr) {
            val v = Calc.evalMinor(expr, 2) / 100
            expr = if (v > 0) v.toString() else ""
        }
    }
    // 外幣帳戶轉出／消費：沒有手續費、優惠、分期（報銷可以：金額用換算的台幣）
    LaunchedEffect(fromFx) { if (fromFx) { fee = 0L; discount = 0L } }
    LaunchedEffect(plan.mode) { if (plan.mode == FxMode.SPEND) inst = 1 }

    fun setType(t: TxType) {
        if (t == type) return
        type = t
        catId = defaultCat(d, t)
        if (t != TxType.EXPENSE) inst = 1
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp),
    ) {
        // 標題列
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { tryClose() }) { Icon(Icons.Filled.Close, contentDescription = "關閉") }
            Spacer(Modifier.weight(1f))
            PillSegment(
                listOf("支出", "收入", "轉帳"),
                when (type) { TxType.EXPENSE -> 0; TxType.INCOME -> 1; TxType.TRANSFER -> 2 },
                { i -> setType(listOf(TxType.EXPENSE, TxType.INCOME, TxType.TRANSFER)[i]) },
            )
            Spacer(Modifier.weight(1f))
            if (orig != null || (tplMode && tplId != null)) {
                IconButton(onClick = { dialog = "delete" }) { Icon(Icons.Filled.Delete, contentDescription = "刪除") }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }

        // 備註：放在最上面，鍵盤開關時不會跟著移動
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(300) },
            placeholder = { Text("備註（選填）") },
            leadingIcon = { IconGlyph("vec:pencil", 18.sp) },
            minLines = 1,
            maxLines = if (noteFocused) 3 else 1,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            trailingIcon = if (noteFocused) {
                { TextButton(onClick = { focus.clearFocus() }) { Text("完成") } }
            } else null,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).onFocusChanged { noteFocused = it.isFocused },
        )

        // 分類或轉帳帳戶
        Box(Modifier.weight(1f).fillMaxWidth().needFrame(nv, "target", RoundedCornerShape(16.dp))) {
            if (type == TxType.TRANSFER) {
                // 買賣外幣時多一列「收到」的輸入，各列縮小一點；螢幕還是放不下就可以往下捲
                val tight = (plan.mode == FxMode.BUY || plan.mode == FxMode.SELL) && fxAcc != null
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(if (tight) 6.dp else 10.dp)) {
                    AccountPick("從", accId?.let { d.accMap[it] }?.let { accLabel(it) } ?: "選擇帳戶", compact = tight) { dialog = "from" }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(LocalContentColor provides cute.sub) { IconGlyph("vec:down", if (tight) 18.sp else 22.sp) }
                    }
                    AccountPick("轉到", toAccId?.let { d.accMap[it] }?.let { accLabel(it) } ?: "選擇帳戶", compact = tight) { dialog = "to" }
                    if (tight && fxAcc != null) {
                        val buy = plan.mode == FxMode.BUY
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CompactField(
                            otherText,
                            { v ->
                                otherTouched = true
                                otherText = if (buy) {
                                    var dot = false
                                    val sb = StringBuilder()
                                    for (c in v) when {
                                        c.isDigit() -> sb.append(c)
                                        c == '.' && !dot && dec > 0 -> { dot = true; sb.append(c) }
                                    }
                                    val t = sb.toString()
                                    val at = t.indexOf('.')
                                    (if (at >= 0 && t.length - at - 1 > dec) t.take(at + 1 + dec) else t).take(14)
                                } else v.filter { c -> c.isDigit() }.take(10)
                            },
                            if (buy) "收到的外幣（${fxAcc.currency}）" else "收到的台幣",
                            Modifier.weight(1f),
                            number = true, decimal = buy && dec > 0,
                            prefix = if (buy) fxAcc.cur.symbol.trim() else tw.moneybook.app.Money.twd,
                            error = nv.on("other"),
                        )
                        if (rate != null) {
                            SoftButton("依匯率算", { otherTouched = false; focus.clearFocus() }, compact = true)
                        }
                        }
                    }
                    nv.Message("other")
                    if (plan.mode == FxMode.UNSUPPORTED && !nv.on("target")) {
                        Text("不同幣別的外幣帳戶之間不能直接轉帳，請先換回台幣，再買另一種外幣。", style = MaterialTheme.typography.bodySmall, color = cute.expense)
                    }
                    if (accs.size < 2 && !nv.on("target")) {
                        Text("轉帳需要至少兩個帳戶，可以到「我的 → 帳戶管理」新增。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                }
            } else {
                Column {
                    // 大分類：上方的小膠囊；「⭐ 常用」也是一個大分類
                    val tops = d.topCategories(type)
                    val tpls = if (!tplMode && orig == null) d.templates.filter { it.type == type } else emptyList()
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        if (tpls.isNotEmpty()) {
                            item { CuteChip("常用", showTpl, { showTpl = true }, icon = "vec:star") }
                        }
                        items(tops, key = { it.id }) { c ->
                            CuteChip(iconLabel(c.emoji, c.name), !showTpl && parent?.id == c.id, { catId = c.id; showTpl = false })
                        }
                    }
                    if (showTpl) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(tpls, key = { it.id }) { tp ->
                                val tc = tp.categoryId?.let { d.catMap[it] }
                                Column(
                                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { applyTemplate(tp) }.padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    CatBubble(tc?.emoji ?: "img:ui_favorite", tc?.color ?: 1, 46.dp)
                                    Text(tp.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                                    if (tp.amount > 0) Text(formatMoney(tp.amount), style = MaterialTheme.typography.labelSmall, color = cute.sub)
                                }
                            }
                        }
                    } else {
                    // 子分類：下方的大圖示（第一格代表大分類本身）
                    val kids = parent?.let { d.childrenOf(it.id) } ?: emptyList()
                    val tiles = if (parent != null) listOf(parent) + kids else emptyList()
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(tiles, key = { it.id }) { c ->
                            val on = catId == c.id
                            val isParent = c.id == parent?.id
                            Column(
                                Modifier.clip(RoundedCornerShape(16.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                    .clickable { catId = c.id }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CatBubble(c.emoji, c.color, 46.dp)
                                Text(
                                    if (isParent && kids.isNotEmpty()) "不細分" else c.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (on) cute.ink else cute.sub,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                        }
                    }
                    }
                }
            }
        }

        nv.Message("target")

        // 附加資訊
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (tplMode) CuteChip("名稱：" + tplName.ifBlank { "未命名" }, tplName.isNotBlank(), { dialog = "tplname" }, icon = "vec:pencil")
            if (!tplMode) CuteChip(dayLabel(day), false, { dialog = "date" }, icon = "vec:calendar")
            if (!tplMode) CuteChip(if (timeMin >= 0) tw.moneybook.app.formatTime(timeMin) else "未設定時間", false, { dialog = "time" }, icon = "vec:clock")
            if (type != TxType.TRANSFER && accs.isNotEmpty()) {
                val a = accId?.let { d.accMap[it] }
                CuteChip(a?.let { accLabel(it) } ?: "帳戶", false, { dialog = "from" }, icon = "vec:wallet")
            }
            if (billEff != null) {
                CuteChip(
                    "入帳 " + (if (billEff.year != LocalDate.now().year) "${billEff.year}/" else "") + "${billEff.monthValue}月",
                    billSet != 0, { dialog = "bill" }, icon = "vec:calendar",
                )
            }
            if (fxsEligible) {
                CuteChip(
                    if (fxsOn) "外幣 $fxsCur" + (if (fxsPending) "・待請款" else "") else "外幣",
                    fxsOn, { dialog = "fxs" }, icon = "vec:coin",
                )
            }
            CuteChip(if (tags.isEmpty()) "新增標籤" else tags.joinToString(" ") { "#$it" }.take(16), tags.isNotEmpty(), { dialog = "tags" }, icon = "vec:tag")
            val feeLabel = when {
                fee > 0 && effDiscount > 0 -> "手續費・優惠"
                fee > 0 -> "手續費 ${formatMoney(fee)}"
                effDiscount > 0 -> "優惠 ${formatMoney(effDiscount)}"
                type == TxType.EXPENSE -> "手續費／優惠"
                else -> "手續費"
            }
            if (!fromFx) CuteChip(feeLabel, fee > 0 || effDiscount > 0, { dialog = "fee" }, icon = if (fee == 0L && effDiscount > 0) "vec:ticket" else "vec:coin")
            if (type == TxType.EXPENSE && !tplMode) {
                val totalReimb = reimbItems.sumOf { it.effective }
                val part = if (reimbItems.isNotEmpty() && totalReimb != actual) " ${formatMoney(totalReimb)}" else ""
                val people = if (reimbItems.size > 1) "・${reimbItems.size} 人" else ""
                CuteChip(
                    when {
                        reimbItems.isEmpty() -> "報銷"
                        reimbItems.all { it.closed } -> "已報銷$part"
                        else -> "待報銷$part$people"
                    },
                    reimbItems.isNotEmpty(),
                    { dialog = "reimb" },
                    icon = if (reimbItems.isNotEmpty() && reimbItems.all { it.closed }) "vec:check" else "vec:receipt",
                )
            }
            if (orig == null && type == TxType.EXPENSE && !tplMode && plan.mode != FxMode.SPEND) {
                CuteChip(if (inst > 1) "分 $inst 期" else "分期", inst > 1, { dialog = "inst" }, icon = "vec:repeat")
            }
            if (orig == null && !tplMode && plan.mode == FxMode.NONE) CuteChip("存為常用", false, { dialog = "tpl" }, icon = "vec:star")
            if (orig != null && orig.instTotal > 1) CuteChip("分期 ${orig.instIndex}/${orig.instTotal}", false, {})
        }

        // 金額（貼在數字鍵盤正上方）
        Spacer(Modifier.height(8.dp))
        CuteCard(
            Modifier.fillMaxWidth().needFrame(nv, "amount", RoundedCornerShape(24.dp)).needFrame(nv, "rate", RoundedCornerShape(24.dp)),
            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (pending || fxsOn || (cat == null && type != TxType.TRANSFER)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (cat == null && type != TxType.TRANSFER) "請先選分類"
                        else if (fxsOn) formatFx(fxsMinor, fxsCur) +
                            (impliedRate(amount, fxsMinor, tw.moneybook.app.Currencies.of(fxsCur).decimals)?.let { " @ ${rateText(it)}" } ?: "") +
                            (if (fxsPending) "（預估，待請款）" else "（已請款）")
                        else "",
                        style = MaterialTheme.typography.labelLarge, color = cute.sub, modifier = Modifier.weight(1f),
                    )
                    if (pending) Text("= " + (if (keyIsFx && fxAcc != null) formatFx(keyVal, fxAcc.currency) else formatMoney(keyVal)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                (if (keyIsFx && fxAcc != null) fxAcc.cur.symbol.trim() else tw.moneybook.app.Money.twd) + (if (expr.isEmpty()) "0" else Calc.pretty(expr)),
                style = MaterialTheme.typography.displaySmall,
                color = when (type) {
                    TxType.EXPENSE -> cute.expense
                    TxType.INCOME -> cute.income
                    TxType.TRANSFER -> cute.ink
                },
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 外幣：換算成台幣（消費）、成交匯率與收到的金額（買賣）
            when (plan.mode) {
                FxMode.SPEND, FxMode.SAME -> Text(
                    if (rate != null) "≈ ${formatMoney(amount)}（匯率 ${rateText(rate)}，點這裡改）" else "還沒有匯率，點這裡設定",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().clickable { dialog = "fxrate" }.padding(vertical = 2.dp),
                )
                FxMode.BUY -> if (fxAcc != null) Text(
                    "付出 ${formatMoney(keyVal)}　收到 ${formatFx(fxMinor, fxAcc.currency)}" + (implied?.let { "　匯率 ${rateText(it)}" } ?: rate?.let { "　目前匯率 ${rateText(it)}" } ?: "　還沒有匯率，請輸入收到的金額"),
                    style = MaterialTheme.typography.labelMedium, color = cute.sub, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                )
                FxMode.SELL -> if (fxAcc != null) Text(
                    "賣出 ${formatFx(keyVal, fxAcc.currency)}　收到 ${formatMoney(amount)}" + (implied?.let { "　匯率 ${rateText(it)}" } ?: rate?.let { "　目前匯率 ${rateText(it)}" } ?: "　還沒有匯率，請輸入收到的金額"),
                    style = MaterialTheme.typography.labelMedium, color = cute.sub, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
                )
                else -> {}
            }
            if (fee > 0 || effDiscount > 0) {
                val parts = ArrayList<String>()
                parts.add(if (type == TxType.TRANSFER) "轉帳 ${formatMoney(amount)}" else "金額 ${formatMoney(amount)}")
                if (effDiscount > 0) parts.add("− 優惠 ${formatMoney(effDiscount)}")
                if (fee > 0) parts.add((if (type == TxType.INCOME) "− " else "+ ") + "手續費 ${formatMoney(fee)}")
                val label = when (type) {
                    TxType.EXPENSE -> "實付"
                    TxType.INCOME -> "實收"
                    TxType.TRANSFER -> "共扣"
                }
                Text(
                    parts.joinToString(" ") + " ＝ $label ${formatMoney(actual)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = cute.sub,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        nv.Message("amount")
        nv.Message("rate")
        Spacer(Modifier.height(8.dp))

        Keypad(
            onKey = { k -> expr = if (keyIsFx) Calc.pressFx(expr, k, dec) else Calc.press(expr, k) },
            doneLabel = if (pending) "=" else "完成",
            doneEnabled = true,
            onDone = {
                if (pending) {
                    expr = if (keyVal > 0) (if (keyIsFx) fxExpr(keyVal, dec) else keyVal.toString()) else ""
                } else {
                    doSave()
                }
            },
            modifier = Modifier.padding(bottom = 10.dp),
            dotKey = keyIsFx && dec > 0,
        )
    }

    // ───── 對話框 ─────
    when (dialog) {
        "reimb" -> ReimbEditPage(
            title = "${cat?.name ?: "未分類"}・${shortDate(day)}",
            actual = actual,
            cap = amount + fee,
            origItems = origItems,
            initOn = reimbOn,
            initFull = reimbFull,
            initJson = reimbJson,
            initWho = reimbWho,
            fxCur = if (plan.mode == FxMode.SPEND) fxAcc?.currency ?: "" else "",
            fxActual = if (plan.mode == FxMode.SPEND) fxMinor else 0L,
            names = d.reimbNames(),
            onDone = { on2, full2, json2, who2 ->
                reimbOn = on2
                reimbFull = full2
                reimbJson = json2
                reimbWho = who2
                dialog = ""
            },
            onClose = { dialog = "" },
        )
        "unsaved" -> AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text("要儲存修改嗎？") },
            text = { Text(if (canSave) "你有還沒儲存的修改。" else "你有還沒儲存的修改，但目前的內容還不能儲存（${if (!tplMode && !hasValue) "沒有金額" else "資料不完整"}）。") },
            confirmButton = {
                Row {
                    TextButton(onClick = { dialog = "" }) { Text("繼續編輯") }
                    if (canSave) TextButton(onClick = { dialog = ""; doSave() }) { Text("儲存") }
                }
            },
            dismissButton = { TextButton(onClick = { dialog = ""; onClose() }) { Text("不儲存", color = cute.expense) } },
        )
        "tplname" -> {
            var text by remember { mutableStateOf(tplName.ifBlank { cat?.name ?: "" }) }
            val nameTries = rememberNeedTries()
            val nameNeed = firstNeed(if (text.isBlank()) Need("name", "請輸入常用記帳的名稱") else null)
            val nameView = NeedView(nameNeed, nameTries.count)
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("常用記帳的名稱") },
                text = {
                    OutlinedTextField(
                        text, { text = it.take(12) }, label = { Text("名稱") }, singleLine = true,
                        isError = nameView.on("name"), supportingText = nameView.supporting("name"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = { TextButton(onClick = { if (nameNeed != null) nameTries.count++ else { tplName = text.trim(); dialog = "" } }) { Text("好") } },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "fxs" -> if (fxsEligible) {
            FxSpendDialog(
                vm = vm,
                init = FxSpendInit(
                    cur = fxsCur,
                    amount = if (fxsMinor > 0L && fxsCur.isNotEmpty()) fxPlain(fxsMinor, tw.moneybook.app.Currencies.of(fxsCur).decimals) else "",
                    pending = if (fxsOn) fxsPending else true,
                    twd = if (fxsOn) amount else 0L,
                ),
                hasValue = fxsOn,
                onConfirm = { cur, minor, twd, isPending ->
                    fxsCur = cur
                    fxsMinor = minor
                    fxsPending = isPending
                    expr = twd.toString()
                    dialog = ""
                },
                onClear = { fxsCur = ""; fxsMinor = 0L; fxsPending = false; dialog = "" },
                onDismiss = { dialog = "" },
            )
        }
        "fxrate" -> if (fxAcc != null) {
            var text by remember { mutableStateOf(rate?.let { rateText(it) } ?: "") }
            val rateTries = rememberNeedTries()
            val rateNeed = firstNeed(if (text.toDoubleOrNull()?.let { it > 0.0 } != true) Need("rate", "請輸入匯率（要大於 0）") else null)
            val rateView = NeedView(rateNeed, rateTries.count)
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("${fxAcc.currency} 匯率") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            text, { v -> text = v.filter { c -> c.isDigit() || c == '.' }.take(12) },
                            label = { Text("1 ${fxAcc.currency} = 幾元台幣") }, prefix = { Text(tw.moneybook.app.Money.twd) }, singleLine = true,
                            isError = rateView.on("rate"), supportingText = rateView.supporting("rate"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("只用在這一筆換算成台幣（統計用）。要改幣別的目前匯率，到帳戶明細的「設定匯率」。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { if (rateNeed != null) rateTries.count++ else { rateUser = text; rateUserCode = fxAcc.currency; dialog = "" } },
                    ) { Text("好") }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "time" -> TimePickerDialog(timeMin, { timeMin = it; dialog = "" }, { dialog = "" })
        "date" -> CuteDatePickerDialog(
            initial = day,
            onPick = { day = it; dialog = "" },
            onDismiss = { dialog = "" },
        )
        "tags" -> {
            var list by remember { mutableStateOf(tags) }
            var input by remember { mutableStateOf("") }
            val known = d.allTags().filter { it !in list }.take(15)
            fun addTag() {
                val t = input.trim().trimStart('#', '＃').trim()
                if (t.isNotEmpty() && t !in list && list.size < 10) list = list + t
                input = ""
            }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("標籤") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (list.isNotEmpty()) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { t -> CuteChip("#$t  ✕", true, { list = list - t }) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                input, { input = it.take(12) },
                                placeholder = { Text("輸入一個標籤") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { addTag() }),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            SoftButton("新增", { if (input.isNotBlank()) addTag() }, compact = true)
                        }
                        if (known.isNotEmpty()) {
                            Text("用過的標籤", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                known.forEach { k -> CuteChip("#$k", false, { if (list.size < 10) list = list + k }) }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val t = input.trim().trimStart('#', '＃').trim()
                        val final = if (t.isNotEmpty() && t !in list) list + t else list
                        tagsText = final.joinToString("\n")
                        dialog = ""
                    }) { Text("完成") }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "bill" -> if (billAuto != null) {
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("入帳月份") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "帳單用結帳日所在的月份稱呼（每月 ${cardAcc?.statementDay} 號結帳，這天以前算這個月的帳單，之後算下個月的）。" +
                                "刷卡和銀行實際入帳跨期時（例如結帳日前幾天刷、結帳後才入帳），可以往後挪到下一期。",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                        (0..3).forEach { k ->
                            val m = billAuto.plusMonths(k.toLong())
                            val on = (billEff ?: billAuto) == m
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer else cute.soft)
                                    .clickable { billMonth = if (k == 0) 0 else tw.moneybook.app.billCode(m); dialog = "" }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("${m.year} 年 ${m.monthValue} 月帳單", style = MaterialTheme.typography.bodyLarge)
                                    cardAcc?.let { ca ->
                                        val (rs, re) = tw.moneybook.app.billRange(ca.statementDay, m)
                                        Text("${rs.monthValue}/${rs.dayOfMonth}～${re.monthValue}/${re.dayOfMonth}", style = MaterialTheme.typography.labelSmall, color = cute.sub)
                                    }
                                }
                                if (k == 0) Text("依日期自動", style = MaterialTheme.typography.labelMedium, color = cute.sub)
                                if (on && k != 0) Text("已指定", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "fee" -> {
            var feeText by remember { mutableStateOf(if (fee > 0) fee.toString() else "") }
            var discText by remember { mutableStateOf(if (discount > 0) discount.toString() else "") }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(if (type == TxType.EXPENSE) "手續費與優惠" else "手續費") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            when (type) {
                                TxType.EXPENSE -> "上面輸入的是原價。優惠會從原價扣掉，手續費會加上去，帳戶扣的是實付金額。"
                                TxType.INCOME -> "手續費會從收到的金額扣掉，例如匯款手續費。"
                                TxType.TRANSFER -> "手續費從轉出帳戶多扣，例如跨行轉帳 $15，會算進支出統計。"
                            },
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                        OutlinedTextField(
                            feeText, { feeText = it.filter { c -> c.isDigit() }.take(8) },
                            label = { Text("手續費") }, prefix = { Text(tw.moneybook.app.Money.twd) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (type == TxType.EXPENSE) {
                            OutlinedTextField(
                                discText, { discText = it.filter { c -> c.isDigit() }.take(8) },
                                label = { Text("優惠／折扣") }, prefix = { Text(tw.moneybook.app.Money.twd) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        fee = feeText.toLongOrNull() ?: 0L
                        discount = discText.toLongOrNull() ?: 0L
                        dialog = ""
                    }) { Text("好") }
                },
                dismissButton = {
                    TextButton(onClick = { fee = 0L; discount = 0L; dialog = "" }) { Text("清除") }
                },
            )
        }
        "inst" -> {
            var text by remember { mutableStateOf(if (inst > 1) inst.toString() else "") }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("分期付款") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("金額會平均分成每月一筆，從選擇的日期開始。", style = MaterialTheme.typography.bodySmall, color = cute.sub)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3, 6, 12, 24).forEach { n -> CuteChip("$n 期", text == n.toString(), { text = n.toString() }) }
                        }
                        OutlinedTextField(
                            text, { text = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("期數") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val n = text.toIntOrNull() ?: 1
                        if (n > 1 && amount > 0) Text("每期約 ${formatMoney(amount / n)}", color = MaterialTheme.colorScheme.primary)
                    }
                },
                confirmButton = { TextButton(onClick = { inst = (text.toIntOrNull() ?: 1).coerceIn(1, 60); dialog = "" }) { Text("好") } },
                dismissButton = { TextButton(onClick = { inst = 1; dialog = "" }) { Text("不分期") } },
            )
        }
        "tpl" -> {
            var text by remember { mutableStateOf(cat?.name ?: "") }
            val saveTries = rememberNeedTries()
            val saveNeed = firstNeed(if (text.isBlank()) Need("name", "請輸入常用記帳的名稱") else null)
            val saveView = NeedView(saveNeed, saveTries.count)
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text("存為常用記帳") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "會記住目前的分類、帳戶、金額、備註和標籤，下次在上方一點就帶入。金額留 0 代表每次自己輸入。",
                            style = MaterialTheme.typography.bodySmall, color = cute.sub,
                        )
                        OutlinedTextField(
                            text, { text = it.take(12) }, label = { Text("名稱") }, singleLine = true,
                            isError = saveView.on("name"), supportingText = saveView.supporting("name"),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (saveNeed != null) { saveTries.count++; return@TextButton }
                        vm.addTemplate(text.trim(), draft())
                        dialog = ""
                    }) { Text("儲存") }
                },
                dismissButton = { TextButton(onClick = { dialog = "" }) { Text("取消") } },
            )
        }
        "from", "to" -> {
            val isTo = dialog == "to"
            val bal = remember(d) { d.balances() }
            AlertDialog(
                onDismissRequest = { dialog = "" },
                title = { Text(if (isTo) "轉入帳戶" else "選擇帳戶") },
                text = {
                    // 帳戶多的時候要能往下滑，不然只能選到畫面上看得到的
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        fun pick(a: tw.moneybook.app.Account) {
                            if (isTo) toAccId = a.id else accId = a.id
                            dialog = ""
                        }
                        val head: @Composable (String) -> Unit = { t ->
                            Text(t, style = MaterialTheme.typography.labelMedium, color = cute.sub, modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp))
                        }
                        // 常用帳戶放最上面：手動標星號的固定在前，其餘用最近的使用次數自動補；
                        // 帳戶太少（不到 3 個）又沒有標星號，就不用再分一區
                        val freq = if (accs.size >= 3 || accs.any { it.favorite }) d.frequentAccounts(3) else emptyList()
                        if (freq.isNotEmpty()) {
                            head("常用帳戶")
                            freq.forEach { a -> AccountLine(a, bal[a.id] ?: 0L, if (isTo) toAccId == a.id else accId == a.id, onFavorite = { vm.setFavorite(it.id, !it.favorite) }) { pick(a) } }
                        }
                        // 其餘依帳戶類型分組（現金、銀行、信用卡…）
                        tw.moneybook.app.AccountType.values().forEach { type ->
                            val g = accs.filter { it.type == type }
                            if (g.isNotEmpty()) {
                                head(type.label)
                                g.forEach { a -> AccountLine(a, bal[a.id] ?: 0L, if (isTo) toAccId == a.id else accId == a.id, onFavorite = { vm.setFavorite(it.id, !it.favorite) }) { pick(a) } }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = "" }) { Text("關閉") } },
            )
        }
        "delete" -> {
            if (tplMode && tplId != null) {
                ConfirmDialog(
                    title = "刪除這個常用記帳？",
                    text = "刪除後不會影響已經記好的帳。",
                    confirm = "刪除",
                    onConfirm = { vm.deleteTemplate(tplId); dialog = ""; onClose() },
                    onDismiss = { dialog = "" },
                )
            }
            val o = orig
            if (o != null && o.instGroup != null && o.instTotal > 1) {
                AlertDialog(
                    onDismissRequest = { dialog = "" },
                    title = { Text("刪除分期記錄") },
                    text = { Text("這筆是分期 ${o.instIndex}/${o.instTotal}，要只刪這一期，還是整組分期一起刪？") },
                    confirmButton = {
                        TextButton(onClick = { vm.deleteTxn(o.id, true); dialog = ""; onClose() }) { Text("全部刪除") }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.deleteTxn(o.id, false); dialog = ""; onClose() }) { Text("只刪這期") }
                    },
                )
            } else if (o != null) {
                ConfirmDialog(
                    title = "刪除這筆記錄？",
                    text = "刪除後無法復原。",
                    confirm = "刪除",
                    onConfirm = { vm.deleteTxn(o.id, false); dialog = ""; onClose() },
                    onDismiss = { dialog = "" },
                )
            }
        }
    }
}

@Composable
private fun AccountPick(label: String, value: String, compact: Boolean = false, onClick: () -> Unit) {
    val cute = LocalCute.current
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(cute.card)
            // 深色模式下卡片和背景幾乎同色，加一圈邊框才看得出是可以點的
            .then(if (cute.dark) Modifier.border(1.dp, cute.sub.copy(alpha = 0.5f), shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = cute.sub, modifier = Modifier.width(48.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
    }
}
