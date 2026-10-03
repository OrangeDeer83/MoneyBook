# 記帳本 MoneyBook — 給 Claude Code 的專案說明

溫暖可愛風格的 Android 記帳 App。Kotlin + Jetpack Compose（Material 3），沒有網路權限，資料只存在手機裡。
使用者是繁體中文使用者：**App 內文字、程式註解、commit 訊息、更新說明都用繁體中文**。

## 專案結構

- `app/src/main/java/tw/moneybook/app/`
  - `Data.kt`：資料模型（Book、Account、Category、Txn、Template、Prefs、AppData）與計算（餘額、實付、統計金額、信用卡週期）
  - `Store.kt`：JSON 存檔（org.json）、舊版資料搬移、CSV 匯出匯入、計算機運算 `Calc`
  - `MoneyViewModel.kt`：所有修改資料的動作，存檔後透過 `messages` 送提示（可帶「復原」）
  - `MainActivity.kt`
  - `ui/`：`MoneyApp.kt`（導覽與路由）、`Screens.kt`（首頁、日曆、記錄列）、`EditScreen.kt`（記一筆／常用記帳編輯）、`Stats.kt`（統計）、`Search.kt`（搜尋、統計下鑽）、`Manage.kt`（我的、各管理頁、預算、備份）、`Accounts.kt`（帳戶明細、信用卡、帳戶圖示）、`Theme.kt`（配色、字型）、`Mascot.kt`（吉祥物）、`Widgets.kt`（圖表、鍵盤、撒花）、`Gestures.kt`（左滑刪除、長按拖曳、拖曳排序）、`Common.kt`（共用元件、日期選擇器）
- `app/src/main/res/font/huninn.ttf`：jf 粉圓字型（OFL）
- `version.properties`：版本號（唯一的版本來源）
- `RELEASE_NOTES.md`：**這一版**的更新說明，會自動放到 GitHub Release
- `CHANGELOG.md`：所有版本的更新紀錄

## 版本與發布流程（每次改完都要做）

1. 依照變更大小更新 `version.properties` 的 `versionName`（主版號.次版號.修訂號）：
   - 主版號：大改版，或需要使用者重新安裝的變更（**1.0.0 之前是初版測試期**，版號是 0.次版號.修訂號）
   - 次版號：新增功能
   - 修訂號：修 bug、小調整
2. 把 `RELEASE_NOTES.md` **整個換成這一版**的說明（格式：`## emoji 標題`，再分「新功能／改進／修正」條列）
3. 在 `CHANGELOG.md` 最上方（標題說明下面）加上這一版的簡短紀錄
4. commit 並 push 到 `main`，GitHub Actions 會自動：讀版本號 → 用 Secrets 裡的金鑰編譯正式版 → 發布 `記帳本 vX.Y.Z` 的 Release（tag `vX.Y.Z`）
5. 同一個版本號重新 push 會更新同一個 Release（例如修正編譯錯誤）
6. 只改 `*.md` 或 `docs/` 不會觸發編譯

`versionCode` 自動使用 GitHub Actions 的編譯次數，不用手動改。

## 編譯與檢查

- 主要靠 GitHub Actions 編譯。編譯失敗時，Kotlin 錯誤會出現在 Actions 頁面的註記（`gh run view --log-failed` 也能看）
- 本機有 Android SDK 的話：`./gradlew assembleDebug`（debug 版用系統預設 debug key，不能覆蓋安裝正式版）
- 本機正式版需要 `keystore.properties`，見 `docs/signing.md`

## 重要規則

- **絕對不要 commit 簽章金鑰或密碼**（*.jks、*.keystore、keystore.properties 已在 .gitignore）
- **資料相容性**：`Codec` 讀取時所有新欄位都要有預設值（`optXxx`），舊的備份檔和舊版資料必須還能讀。不要改現有欄位的意義
- 刪除類操作盡量提供「復原」（`deleteWithUndo`、`UiMsg` 帶 action）
- 金額都是 `Long`（新台幣整數）；支出統計用 `Txn.spent`／`statAmount`，不要直接用 `amount`
- CSV 匯出的文字欄位要經過 `txt()` 防止公式注入
- 顏色一律從 `MaterialTheme.colorScheme` 或 `LocalCute.current` 取，不要寫死，才能跟著配色與深色模式變化
- Kotlin 小陷阱：`if`／`when` 分支要回傳 lambda 時要加括號，例如 `if (x) ({ t -> ... }) else ({ ... })`

## 相依版本

AGP 8.7.3、Kotlin 2.1.0、Compose BOM 2024.12.01、Gradle 8.11.1（wrapper）、minSdk 26、targetSdk 35、JDK 17
