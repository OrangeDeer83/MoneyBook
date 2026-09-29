# 簽章金鑰設定（只要做一次）

App 的簽章金鑰就像「印章」：Android 只允許同一顆印章蓋出來的新版覆蓋舊版。
這顆印章**只能存在你自己的電腦和 GitHub Secrets 裡，絕對不要 commit 進 repo**（.gitignore 已經擋掉 *.jks、*.keystore、keystore.properties）。

## 1. 產生金鑰

需要 `keytool`（裝了 Android Studio 就有，路徑通常是
`C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe`）。

在 repo 外面的資料夾執行（例如 `%USERPROFILE%\keys`），密碼請自己取一個長一點的：

```
keytool -genkeypair -v -keystore moneybook-release.jks -alias moneybook -keyalg RSA -keysize 2048 -validity 36500
```

依提示輸入密碼（keystore 密碼和 key 密碼可以設成一樣）與名字等資料。

**請把 `moneybook-release.jks` 和密碼另外備份（例如放到私人雲端硬碟）。弄丟了就再也無法發布能覆蓋安裝的更新。**

## 2. 放進 GitHub Secrets

到 repo 的 Settings → Secrets and variables → Actions → New repository secret，新增 4 個：

| 名稱 | 內容 |
|---|---|
| `KEYSTORE_BASE64` | 金鑰檔轉成 base64 的文字（見下方） |
| `KEYSTORE_PASSWORD` | keystore 密碼 |
| `KEY_ALIAS` | `moneybook` |
| `KEY_PASSWORD` | key 密碼 |

把金鑰轉成 base64（PowerShell）：

```
[Convert]::ToBase64String([IO.File]::ReadAllBytes("moneybook-release.jks")) | Set-Clipboard
```

執行後內容已經在剪貼簿，直接貼到 `KEYSTORE_BASE64` 即可。

如果有安裝 GitHub CLI（`gh`），也可以用指令設定：

```
gh secret set KEYSTORE_BASE64 < moneybook-release.b64
gh secret set KEYSTORE_PASSWORD
gh secret set KEY_ALIAS --body moneybook
gh secret set KEY_PASSWORD
```

## 3.（選用）在自己電腦上編譯正式版

在 repo 根目錄建立 `keystore.properties`（不會被 commit）：

```
storeFile=C:/Users/你的帳號/keys/moneybook-release.jks
storePassword=你的密碼
keyAlias=moneybook
keyPassword=你的密碼
```

然後執行 `gradlew assembleRelease`（需要安裝 Android SDK）。
