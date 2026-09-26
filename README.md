# 記帳本 MoneyBook

用 Kotlin + Jetpack Compose（Material 3）寫的簡單 Android 記帳 App。

## 功能
- 記錄收入和支出，可以選分類、日期、寫備註；點一筆記錄就能編輯或刪除
- 每月統計：各分類的環狀圖和百分比
- 每月預算：首頁有進度條；記帳後用掉 80% 以上或超支會跳出提醒
- 匯出 CSV 備份，可以用 Excel 開啟，中文不會變亂碼
- 深色模式；Android 12 以上會自動套用手機的主題色

## 用 GitHub 自動編譯
每次推送到 `main` 分支，GitHub Actions 就會自動編出 APK，
並發布到 repo 的 **Releases** 頁面，用手機打開就能直接下載安裝。

## 注意
- `app/debug.keystore` 是固定的簽章金鑰，所以新版可以直接覆蓋安裝，資料不會消失。它只適合自己用，不要拿去上架。
- 資料只存在手機裡（App 私有目錄）。解除安裝 App 資料就會消失，換手機前請先匯出 CSV。
