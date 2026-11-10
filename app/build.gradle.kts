plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wordlens.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wordlens.app"
        minSdk = 26          // Android 8.0
        targetSdk = 36
        versionCode = 22
        versionName = "2.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    /**
     * 固定签名。
     *
     * GitHub Actions 每跑一次都是全新机器，默认的 ~/.android/debug.keystore 是临时生成的，
     * 两次构建的签名指纹不同 —— 手机上覆盖安装就会报「签名不一致 / 应用未安装」。
     * 所以这里把仓库里的 keystore/wordlens.p12 当作唯一签名证书，debug 与 release 共用。
     *
     * 这是个人自用 App 的自签名证书，口令公开是有意为之（方便任何人复现构建）。
     * 要发给别人装或上架，请换成你自己的证书，并把口令挪到 GitHub Secrets。
     */
    signingConfigs {
        create("wordlens") {
            storeFile = rootProject.file("keystore/wordlens.p12")
            storeType = "PKCS12"
            storePassword = "wordlens"
            keyAlias = "wordlens"
            keyPassword = "wordlens"
        }
    }

    buildTypes {
        debug {
            // CI 直接产 app-debug.apk，手机装了就能用
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("wordlens")
        }
        release {
            // 默认不混淆：免去签名/规则调试成本。要瘦身就自己打开并补 proguard 规则
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("wordlens")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*"
        )
    }

    testOptions {
        // 单测只跑纯 Kotlin 逻辑（词形还原、坐标序列化），不碰 Android framework
        unitTests.isReturnDefaultValues = true
    }

    // CI 上不希望因为 lint 警告中断构建
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose：版本由 BOM 统一管理，单个库不要再写版本号
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 相机
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // 端侧 OCR（模型已打进 APK，无需联网、无需 Google Play 服务）
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
