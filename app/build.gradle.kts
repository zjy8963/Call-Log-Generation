import java.text.SimpleDateFormat
import java.util.*
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.uselesswater.multicallloggeneration"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.uselesswater.multicallloggeneration"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "3.1.2"
        // 添加多dex支持（如果方法数过多）
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ========== 新增：从 local.properties 读取 API 密钥并生成 BuildConfig ==========
        val localProperties = Properties().apply {
            file("../local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }

        // 豆包配置
        val doubaoApiKey = localProperties.getProperty("DOUBAO_API_KEY", "")
        val doubaoEndpointId = localProperties.getProperty("DOUBAO_ENDPOINT_ID", "")

        // 百度配置
        val baiduApiKey = localProperties.getProperty("BAIDU_API_KEY", "")
        val baiduSecretKey = localProperties.getProperty("BAIDU_SECRET_KEY", "")

        // 生成 BuildConfig 字段
        buildConfigField("String", "DOUBAO_API_KEY", "\"$doubaoApiKey\"")
        buildConfigField("String", "DOUBAO_ENDPOINT_ID", "\"$doubaoEndpointId\"")
        buildConfigField("String", "BAIDU_API_KEY", "\"$baiduApiKey\"")
        buildConfigField("String", "BAIDU_SECRET_KEY", "\"$baiduSecretKey\"")
        // =================================================================================
    }

    // 读取local.properties（用于签名配置）
    val localProperties = Properties().apply {
        file("../local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }

    signingConfigs {
        create("release") {
            storePassword = localProperties.getProperty("keystore.password") ?: ""
            keyPassword = localProperties.getProperty("key.password") ?: ""
            storeFile = file("app/keystore")
            keyAlias = "calllog"
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        // ========== 新增：启用 BuildConfig 功能 ==========
        buildConfig = true
        // ================================================
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    // 使用最简单且兼容的方法
    applicationVariants.all {
        val variant = this
        val buildType = variant.buildType.name
        val versionName = variant.versionName
        val appName = "CallLogGeneration"

        // 获取当前时间
        val currentTime = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())

        // 设置输出文件名 - 使用正确的方法
        variant.outputs.all {
            //如果为正式发行版，则命名为appName-v版本号.apk
            if (buildType == "release") {
                val fileName = "${appName}-v${versionName}.apk"
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = fileName
            }else{
                //如果为测试版，则命名为appName_buildType_v版本号_时间.apk
                val fileName = "${appName}_${buildType}_v${versionName}_${currentTime}.apk"
                (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = fileName
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.threetenabp)

    // 网络请求依赖
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.androidx.junit.ktx)
    // 图片加载（用于OCR图片预览）
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.exifinterface)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

}