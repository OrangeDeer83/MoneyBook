import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { stream -> this.load(stream) }
}

android {
    namespace = "tw.moneybook.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "tw.moneybook.app"
        minSdk = 26
        targetSdk = 35
        // 版本名稱來自 version.properties；版本代碼用編譯次數，確保每次都比上次大
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = versionProps.getProperty("versionName", "0.0.0").trim()
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    // 正式版簽章：金鑰不放在 repo 裡。
    // GitHub Actions 從 Secrets 取得（環境變數）；自己電腦上則讀 keystore.properties（已加入 .gitignore）。
    val localSigning = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { stream -> this.load(stream) }
    }
    val ksPath = System.getenv("KEYSTORE_PATH") ?: localSigning.getProperty("storeFile")
    val hasSigning = !ksPath.isNullOrBlank() && file(ksPath).exists()

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(ksPath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: localSigning.getProperty("storePassword")
                keyAlias = System.getenv("KEY_ALIAS") ?: localSigning.getProperty("keyAlias")
                keyPassword = System.getenv("KEY_PASSWORD") ?: localSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // 私下測試版：套件名稱不同，可以跟正式版同時安裝，資料互不影響
        getByName("debug") {
            applicationIdSuffix = ".test"
            // 測試版名稱帶上 commit 的前 7 碼（例如 3.1.0-dev.be2f7fc），不用靠不斷變大的編號分辨
            versionNameSuffix = "-dev" + (System.getenv("GITHUB_SHA")?.take(7)?.let { ".$it" } ?: "")
            manifestPlaceholders["appLabel"] = "記帳本 測試"
            // 用固定金鑰簽章，這樣每次測試版才能互相覆蓋安裝（否則 CI 每次會臨時產生新的 debug 金鑰）
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        getByName("release") {
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
