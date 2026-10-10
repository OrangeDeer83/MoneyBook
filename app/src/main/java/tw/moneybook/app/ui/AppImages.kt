package tw.moneybook.app.ui

import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.moneybook.app.R

/**
 * 內建的貼紙圖示。分類、帳戶、帳本的「emoji」欄位如果是 `img:名稱`，就顯示這裡的圖片，
 * 其他照舊當表情符號顯示，所以舊資料、舊備份都不受影響。
 */
object AppImages {
    const val PREFIX = "img:"

    /** 挑選器裡的順序 */
    val all: LinkedHashMap<String, Int> = linkedMapOf(
        "img:cat_education" to R.drawable.img_cat_education,
        "img:cat_food" to R.drawable.img_cat_food,
        "img:cat_fuel" to R.drawable.img_cat_fuel,
        "img:cat_game" to R.drawable.img_cat_game,
        "img:cat_home" to R.drawable.img_cat_home,
        "img:cat_internet" to R.drawable.img_cat_internet,
        "img:cat_medical" to R.drawable.img_cat_medical,
        "img:cat_metro" to R.drawable.img_cat_metro,
        "img:cat_movie" to R.drawable.img_cat_movie,
        "img:cat_other" to R.drawable.img_cat_other,
        "img:cat_parking" to R.drawable.img_cat_parking,
        "img:cat_pet" to R.drawable.img_cat_pet,
        "img:cat_rent" to R.drawable.img_cat_rent,
        "img:cat_shopping" to R.drawable.img_cat_shopping,
        "img:cat_snack" to R.drawable.img_cat_snack,
        "img:cat_social" to R.drawable.img_cat_social,
        "img:cat_transport" to R.drawable.img_cat_transport,
        "img:cat_travel" to R.drawable.img_cat_travel,
        "img:cat_utility" to R.drawable.img_cat_utility,
        "img:cat_breakfast" to R.drawable.img_cat_breakfast,
        "img:cat_lunch" to R.drawable.img_cat_lunch,
        "img:cat_dinner" to R.drawable.img_cat_dinner,
        "img:cat_taxi" to R.drawable.img_cat_taxi,
        "img:cat_clothes" to R.drawable.img_cat_clothes,
        "img:cat_3c" to R.drawable.img_cat_3c,
        "img:cat_beauty" to R.drawable.img_cat_beauty,
        "img:cat_daily" to R.drawable.img_cat_daily,
        "img:cat_joystick" to R.drawable.img_cat_joystick,
        "img:cat_drink" to R.drawable.img_cat_drink,
        "img:cat_tote" to R.drawable.img_cat_tote,
        "img:cat_box" to R.drawable.img_cat_box,
        "img:cat_salary" to R.drawable.img_cat_salary,
        "img:cat_bonus" to R.drawable.img_cat_bonus,
        "img:cat_parttime" to R.drawable.img_cat_parttime,
        "img:cat_redpacket" to R.drawable.img_cat_redpacket,
        "img:cat_moneybag" to R.drawable.img_cat_moneybag,
        "img:pick_cat" to R.drawable.img_pick_cat,
        "img:pick_coffee" to R.drawable.img_pick_coffee,
        "img:pick_coin" to R.drawable.img_pick_coin,
        "img:pick_fruit" to R.drawable.img_pick_fruit,
        "img:pick_heart" to R.drawable.img_pick_heart,
        "img:acc_card" to R.drawable.img_acc_card,
        "img:acc_cash" to R.drawable.img_acc_cash,
        "img:acc_wallet" to R.drawable.img_acc_wallet,
        "img:acc_bank" to R.drawable.img_acc_bank,
        "img:acc_transit" to R.drawable.img_acc_transit,
        "img:acc_epay" to R.drawable.img_acc_epay,
        "img:acc_purse" to R.drawable.img_acc_purse,
        "img:acc_receipt" to R.drawable.img_acc_receipt,
        "img:ui_favorite" to R.drawable.img_ui_favorite,
        "img:ui_savings" to R.drawable.img_ui_savings,
        "img:stat_tag" to R.drawable.img_stat_tag,
        "img:stat_atm" to R.drawable.img_stat_atm,
        "img:stat_cashback" to R.drawable.img_stat_cashback,
        "img:ui_ledger" to R.drawable.img_ui_ledger,
        "img:ui_transfer" to R.drawable.img_ui_transfer,
        "img:ui_deer" to R.drawable.img_ui_deer,
        "img:extra_cart" to R.drawable.img_extra_cart,
        "img:extra_clover" to R.drawable.img_extra_clover,
        "img:extra_diamond" to R.drawable.img_extra_diamond,
        "img:extra_diploma" to R.drawable.img_extra_diploma,
        "img:extra_envelope" to R.drawable.img_extra_envelope,
        "img:extra_ghost" to R.drawable.img_extra_ghost,
        "img:extra_glasses" to R.drawable.img_extra_glasses,
        "img:extra_gold" to R.drawable.img_extra_gold,
        "img:extra_house_brown" to R.drawable.img_extra_house_brown,
        "img:extra_moon" to R.drawable.img_extra_moon,
        "img:extra_passport" to R.drawable.img_extra_passport,
        "img:extra_ricecooker" to R.drawable.img_extra_ricecooker,
        "img:extra_pouch" to R.drawable.img_extra_pouch,
        "img:extra_cashstack" to R.drawable.img_extra_cashstack,
        "img:cat_bus" to R.drawable.img_cat_bus,
        "img:cat_cutlery" to R.drawable.img_cat_cutlery,
        "img:cat_eatdrink" to R.drawable.img_cat_eatdrink,
        "img:cat_fastfood" to R.drawable.img_cat_fastfood,
        "img:cat_hsr" to R.drawable.img_cat_hsr,
        "img:cat_rentcar" to R.drawable.img_cat_rentcar,
        "img:cat_ridehail" to R.drawable.img_cat_ridehail,
        "img:cat_roadsign" to R.drawable.img_cat_roadsign,
        "img:cat_scooter" to R.drawable.img_cat_scooter,
        "img:cat_study" to R.drawable.img_cat_study,
        "img:cat_subway" to R.drawable.img_cat_subway,
        "img:cat_train" to R.drawable.img_cat_train,
        "img:cat_electronics" to R.drawable.img_cat_electronics,
        "img:cat_furniture" to R.drawable.img_cat_furniture,
        "img:cat_gamedisc" to R.drawable.img_cat_gamedisc,
        "img:cat_giftbag" to R.drawable.img_cat_giftbag,
        "img:cat_goods" to R.drawable.img_cat_goods,
        "img:cat_grocery" to R.drawable.img_cat_grocery,
        "img:cat_jewelry" to R.drawable.img_cat_jewelry,
        "img:cat_life" to R.drawable.img_cat_life,
        "img:cat_lipstick" to R.drawable.img_cat_lipstick,
        "img:cat_shoes" to R.drawable.img_cat_shoes,
        "img:cat_stationery" to R.drawable.img_cat_stationery,
        "img:cat_toy" to R.drawable.img_cat_toy,
        "img:cat_clinic" to R.drawable.img_cat_clinic,
        "img:cat_firstaid" to R.drawable.img_cat_firstaid,
        "img:cat_fitness" to R.drawable.img_cat_fitness,
        "img:cat_karaoke" to R.drawable.img_cat_karaoke,
        "img:cat_member" to R.drawable.img_cat_member,
        "img:cat_movieticket" to R.drawable.img_cat_movieticket,
        "img:cat_party" to R.drawable.img_cat_party,
        "img:cat_phoneplan" to R.drawable.img_cat_phoneplan,
        "img:cat_pills" to R.drawable.img_cat_pills,
        "img:cat_rehab" to R.drawable.img_cat_rehab,
        "img:cat_show" to R.drawable.img_cat_show,
        "img:cat_syringe" to R.drawable.img_cat_syringe,
        "img:cat_crypto" to R.drawable.img_cat_crypto,
        "img:cat_hotel" to R.drawable.img_cat_hotel,
        "img:cat_insurance" to R.drawable.img_cat_insurance,
        "img:cat_interest" to R.drawable.img_cat_interest,
        "img:cat_invest" to R.drawable.img_cat_invest,
        "img:cat_misc_life" to R.drawable.img_cat_misc_life,
        "img:cat_points" to R.drawable.img_cat_points,
        "img:cat_salon" to R.drawable.img_cat_salon,
        "img:cat_software" to R.drawable.img_cat_software,
        "img:cat_admission" to R.drawable.img_cat_admission,
        "img:cat_souvenir" to R.drawable.img_cat_souvenir,
        "img:cat_suitcase" to R.drawable.img_cat_suitcase,
        "img:acc_chipcard" to R.drawable.img_acc_chipcard,
        "img:acc_foreign" to R.drawable.img_acc_foreign,
        "img:acc_loan" to R.drawable.img_acc_loan,
        "img:acc_mobile_transit" to R.drawable.img_acc_mobile_transit,
        "img:acc_loan_icon" to R.drawable.img_acc_loan_icon,
        "img:acc_pocket" to R.drawable.img_acc_pocket,
        "img:acc_prepaid" to R.drawable.img_acc_prepaid,
        "img:acc_stock_tw" to R.drawable.img_acc_stock_tw,
        "img:acc_stock_us" to R.drawable.img_acc_stock_us,
        "img:acc_voucher" to R.drawable.img_acc_voucher,
    )

    fun isImg(s: String): Boolean = s.startsWith(PREFIX)
}

/** 單色線條圖示：key 形如 `vec:calendar`，顏色跟著文字顏色（LocalContentColor） */
object AppLines {
    const val PREFIX = "vec:"

    val all: Map<String, androidx.compose.ui.graphics.vector.ImageVector> = mapOf(
        "vec:calendar" to AppIcons.LCalendar,
        "vec:clock" to AppIcons.LClock,
        "vec:ticket" to AppIcons.LTicket,
        "vec:check" to AppIcons.LCheck,
        "vec:repeat" to AppIcons.LRepeat,
        "vec:pencil" to AppIcons.LPencil,
        "vec:down" to AppIcons.LArrowDown,
        "vec:folder" to AppIcons.LFolder,
        "vec:target" to AppIcons.LTarget,
        "vec:palette" to AppIcons.LPalette,
        "vec:save" to AppIcons.LSave,
        "vec:restore" to AppIcons.LRestore,
        "vec:export" to AppIcons.LExport,
        "vec:import" to AppIcons.LImport,
        "vec:siren" to AppIcons.LSiren,
        "vec:warning" to AppIcons.LWarning,
        "vec:search" to AppIcons.LSearch,
        "vec:wallet" to AppIcons.LWallet,
        "vec:tag" to AppIcons.LTag,
        "vec:receipt" to AppIcons.LReceipt,
        "vec:coin" to AppIcons.LCoin,
        "vec:coin_yen" to AppIcons.LCoinYen,
        "vec:coin_dollar" to AppIcons.LCoinDollar,
        "vec:coin_euro" to AppIcons.LCoinEuro,
        "vec:coin_pound" to AppIcons.LCoinPound,
        "vec:coin_won" to AppIcons.LCoinWon,
        "vec:coin_baht" to AppIcons.LCoinBaht,
        "vec:coin_other" to AppIcons.LCoinOther,
        "vec:coin_two" to AppIcons.LCoinTwo,
        "vec:fee" to AppIcons.LFee,
        "vec:star" to AppIcons.LStar,
        "vec:book" to AppIcons.LBook,
    )

    fun isLine(s: String): Boolean = s.startsWith(PREFIX)
}

/** 外幣按鈕的圖示：硬幣裡放這個幣別的符號；還沒選幣別是 ¥，列表裡沒有的幣別是通用的硬幣 */
fun fxCoinIcon(code: String): String = when (code.uppercase()) {
    "", "JPY", "CNY" -> "vec:coin_yen"
    "USD", "HKD", "AUD", "SGD" -> "vec:coin_dollar"
    "EUR" -> "vec:coin_euro"
    "GBP" -> "vec:coin_pound"
    "KRW" -> "vec:coin_won"
    "THB" -> "vec:coin_baht"
    else -> "vec:coin_other"
}

/** 有圖片的圖示回傳 null（要另外畫圖），其他照原樣 */
private fun imgRes(e: String): Int? = if (AppImages.isImg(e)) AppImages.all[e] else null

/** 文字裡的圖示前綴：圖片圖示沒辦法塞進文字，就只留名稱 */
fun iconLabel(emoji: String, name: String): String =
    if (AppImages.isImg(emoji) || AppLines.isLine(emoji)) name else "$emoji $name"

/** 表情符號或圖片，依 fontSize 決定大小 */
@Composable
fun IconGlyph(e: String, fontSize: TextUnit) {
    if (AppLines.isLine(e)) {
        AppLines.all[e]?.let { Icon(it, null, Modifier.size((fontSize.value * 1.1f).dp)) }
        return
    }
    if (AppImages.isImg(e)) {
        val res = imgRes(e)
        if (res != null) {
            Image(painterResource(res), null, Modifier.size((fontSize.value * 1.15f).dp), contentScale = ContentScale.Fit)
        } else {
            Image(painterResource(R.drawable.img_cat_box), null, Modifier.size((fontSize.value * 1.15f).dp), contentScale = ContentScale.Fit)
        }
    } else {
        Text(e, fontSize = fontSize)
    }
}

/** 圖片圖示，給 CatBubble 這類已知外框大小的地方用 */
@Composable
fun IconImage(e: String, size: Dp) {
    val res = imgRes(e)
    if (res != null) Image(painterResource(res), null, Modifier.size(size), contentScale = ContentScale.Fit)
    else Image(painterResource(R.drawable.img_cat_box), null, Modifier.size(size), contentScale = ContentScale.Fit)
}
