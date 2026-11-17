package tw.moneybook.app

/*
 * 常見商店的種類（使用者選甲：內建在 App 裡、使用者看不到的表）。
 * 自動帶分類時，同一家店沒記過，就看同種類的別家店記成什麼、或有沒有跟種類同名的分類（見 guessCat）。
 */

/** 一種商店：cats＝跟這個種類同名的分類（照順序找）；shops＝商店名稱的關鍵字（跟 shopKey 一樣：小寫、去掉空白和標點） */
data class ShopKind(val name: String, val cats: List<String>, val shops: List<String>)

val SHOP_KINDS = listOf(
    ShopKind("超商", listOf("超商", "便利商店"), listOf("7eleven", "711", "統一超商", "全家", "familymart", "萊爾富", "hilife", "ok超商", "okmart")),
    ShopKind(
        "超市", listOf("超市", "日用", "日用品", "生活用品"),
        listOf("全聯", "pxmart", "家樂福", "carrefour", "美廉社", "頂好", "wellcome", "楓康", "愛買", "大潤發", "好市多", "costco", "喜互惠"),
    ),
    ShopKind("加油", listOf("加油", "油錢", "汽油"), listOf("中油", "cpc", "台塑石油", "台塑石化", "全國加油", "台亞", "福懋", "山隆")),
    ShopKind("外送", listOf("外送"), listOf("ubereats", "foodpanda", "熊貓外送")),
    ShopKind(
        "大眾運輸", listOf("大眾運輸", "交通"),
        listOf("台鐵", "臺鐵", "台灣鐵路", "高鐵", "台灣高鐵", "捷運", "悠遊卡", "easycard", "一卡通", "ipass", "客運"),
    ),
    ShopKind("計程車", listOf("計程車", "交通"), listOf("uber", "台灣大車隊", "55688", "yoxi", "linego")),
    ShopKind("停車", listOf("停車", "停車費"), listOf("停車場", "parking", "嘟嘟房", "俥亭")),
    ShopKind("速食", listOf("速食"), listOf("麥當勞", "mcdonald", "肯德基", "kfc", "摩斯漢堡", "mosburger", "漢堡王", "burgerking", "subway")),
    ShopKind(
        "飲料", listOf("飲料", "咖啡"),
        listOf("星巴克", "starbucks", "路易莎", "louisa", "cama", "85度c", "50嵐", "清心福全", "可不可", "迷客夏", "coco都可", "茶湯會", "麻古"),
    ),
    ShopKind(
        "影音", listOf("影音", "訂閱"),
        listOf("netflix", "spotify", "youtube", "disney", "kkbox", "kktv", "applecombill", "googleplay", "friday影音"),
    ),
    ShopKind("藥妝", listOf("藥妝", "美妝"), listOf("屈臣氏", "watsons", "康是美", "cosmed", "寶雅", "poya", "大樹藥局")),
    ShopKind("網購", listOf("網購", "購物"), listOf("蝦皮", "shopee", "momo", "pchome", "amazon", "酷澎", "coupang", "yahoo購物")),
    ShopKind("電信", listOf("電信", "電話費", "手機費", "網路"), listOf("中華電信", "台灣大哥大", "遠傳", "亞太電信", "台灣之星")),
    ShopKind("電影", listOf("電影"), listOf("威秀", "vieshow", "秀泰", "國賓影城", "美麗華影城")),
    ShopKind("書店", listOf("書籍", "書"), listOf("誠品", "博客來", "金石堂")),
    ShopKind("3C", listOf("3C", "3c", "電子產品"), listOf("燦坤", "全國電子", "順發")),
)

/** 這家店是哪一種（看不出來回傳 null）：名稱裡有關鍵字，好幾個都有時用最長的那個（Uber Eats 是外送、不是計程車） */
fun shopKindOf(merchant: String): ShopKind? {
    val key = shopKey(merchant)
    if (key.length < 2) return null
    var best: ShopKind? = null
    var len = 0
    for (k in SHOP_KINDS) for (s in k.shops) if (s.length > len && key.contains(s)) { best = k; len = s.length }
    return best
}
