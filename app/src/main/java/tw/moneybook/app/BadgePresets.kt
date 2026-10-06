package tw.moneybook.app

/** 帳戶徽章的預設清單項目：簡稱 + 雙色漸層（ARGB） */
data class BadgePreset(val name: String, val badge: String, val from: Long, val to: Long)

/**
 * 銀行與行動支付的預設徽章。
 * 沒有用官方 Logo（有商標，也要附圖檔），只用簡稱加品牌識別色的雙色漸層。
 * 顏色依各家識別色換算，是近似值，不是官方標準色：
 *  - 有查到識別色描述的：國泰（綠）、中信（紅＋綠）、玉山（藍綠）、台新（金黃）、富邦（藍＋綠，#0095B8）、
 *    永豐（正紅）、臺銀（酒紅，#AB005F）、街口（紅，#C9191D）、LINE（綠）、將來（黃＋灰）
 *  - 其他是憑印象配的，之後可以隨時改這張表（只影響新選的徽章，已存的帳戶不受影響）
 */
object BadgePresets {
    val banks: List<BadgePreset> = listOf(
        BadgePreset("國泰世華", "國泰", 0xFF7CC63AL, 0xFF2E8B4AL),
        BadgePreset("中國信託", "中信", 0xFFE0262FL, 0xFF2E9E4FL),
        BadgePreset("玉山", "玉山", 0xFF17B38FL, 0xFF0F86B5L),
        BadgePreset("台新", "台新", 0xFFF5BC2BL, 0xFFD88A0CL),
        BadgePreset("富邦", "富邦", 0xFF0095B8L, 0xFF3AAE74L),
        BadgePreset("永豐", "永豐", 0xFFF0343AL, 0xFFC3101CL),
        BadgePreset("臺灣銀行", "台銀", 0xFFD0357FL, 0xFFAB005FL),
        BadgePreset("兆豐", "兆豐", 0xFF4C97E0L, 0xFF1F5FA8L),
        BadgePreset("第一", "一銀", 0xFFE0605FL, 0xFFA82B33L),
        BadgePreset("華南", "華南", 0xFFD9565CL, 0xFF9E232CL),
        BadgePreset("郵局", "郵局", 0xFF4DB07AL, 0xFF1F7446L),
        BadgePreset("星展", "星展", 0xFFF0525AL, 0xFFC01B26L),
        BadgePreset("合作金庫", "合庫", 0xFF58AB78L, 0xFF2A7A49L),
        BadgePreset("彰化", "彰銀", 0xFFE49A5BL, 0xFFB25A28L),
        BadgePreset("土地", "土銀", 0xFF5BB06CL, 0xFF2D7A3FL),
        BadgePreset("凱基", "凱基", 0xFF4F89D6L, 0xFF224F9AL),
        BadgePreset("LINE Bank", "LB", 0xFF4FD07CL, 0xFF1E9A4AL),
        BadgePreset("將來銀行", "將來", 0xFFF2B600L, 0xFF666666L),
        BadgePreset("樂天", "樂天", 0xFFD95D66L, 0xFF98262FL),
    )

    val pays: List<BadgePreset> = listOf(
        BadgePreset("LINE Pay", "LINE", 0xFF3DD66EL, 0xFF06A843L),
        BadgePreset("街口支付", "街口", 0xFFD8282CL, 0xFFA81216L),
        BadgePreset("悠遊付", "悠遊付", 0xFF2BB8A6L, 0xFF1E8FD0L),
        BadgePreset("悠遊卡", "悠遊卡", 0xFF2E8FE0L, 0xFF3DB35AL),
        BadgePreset("一卡通", "一卡通", 0xFFF58A1FL, 0xFFE2551BL),
        BadgePreset("icash Pay", "ic", 0xFFE8412CL, 0xFFC2301FL),
        BadgePreset("全支付", "全支", 0xFF2F6FC7L, 0xFFF08A2AL),
        BadgePreset("全盈+PAY", "全盈", 0xFF1D9BD8L, 0xFFF5A623L),
        BadgePreset("Pi 拍錢包", "Pi", 0xFF7B61D9L, 0xFF4A3AA8L),
        BadgePreset("台灣Pay", "台灣", 0xFF1C6FB5L, 0xFF0E9A7BL),
        BadgePreset("FamiPay", "Fami", 0xFF00A84FL, 0xFF0072BCL),
        BadgePreset("Apple Pay", "Pay", 0xFF555555L, 0xFF111111L),
        BadgePreset("Google Pay", "GPay", 0xFF4285F4L, 0xFF1A5FD0L),
        BadgePreset("Samsung Pay", "SPay", 0xFF2D52D9L, 0xFF1428A0L),
        BadgePreset("PayPal", "PP", 0xFF0A8ADCL, 0xFF003087L),
        BadgePreset("支付寶", "支付寶", 0xFF3D94FFL, 0xFF0A5FE0L),
        BadgePreset("微信支付", "微信", 0xFF2DD47EL, 0xFF05A050L),
    )

    val all: List<BadgePreset> = banks + pays

    /** 只選一個主色時，自動配出較亮的起點和較深的終點 */
    fun autoGradient(argb: Long): Pair<Long, Long> = mix(argb, 0xFFFFFFFFL, 0.22f) to mix(argb, 0xFF000000L, 0.22f)

    private fun mix(c: Long, other: Long, t: Float): Long {
        fun ch(shift: Int): Long {
            val a = (c shr shift) and 0xFF
            val b = (other shr shift) and 0xFF
            return (a + (b - a) * t).toLong().coerceIn(0L, 255L)
        }
        return 0xFF000000L or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
