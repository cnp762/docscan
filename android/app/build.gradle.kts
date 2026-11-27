import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

fun loadDotEnv(rootDir: File): Map<String, String> {
    val envFile = listOf(
        File(rootDir, "../.env"),
        File(rootDir, "../.env.example"),
    ).firstOrNull { it.exists() } ?: return emptyMap()

    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .associate { line ->
            val idx = line.indexOf('=')
            val key = line.substring(0, idx).trim()
            val rawVal = line.substring(idx + 1).trim()
            val value = rawVal.removeSurrounding("\"").removeSurrounding("'")
            key to value
        }
}

val dotEnv = loadDotEnv(rootDir)

fun envOrDefault(key: String, fallback: String): String =
    dotEnv[key]?.takeIf { it.isNotBlank() } ?: fallback

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "cam.docscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "cam.docscan"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "PUBLIC_SITE_URL", "\"${envOrDefault("PUBLIC_SITE_URL", "https://docscan.cam")}\"")
        buildConfigField("String", "PRODUCT_PRO_MONTHLY", "\"${envOrDefault("PRODUCT_PRO_MONTHLY", "pro_monthly")}\"")
        buildConfigField("String", "PRODUCT_PRO_QUARTERLY", "\"${envOrDefault("PRODUCT_PRO_QUARTERLY", "pro_quarterly")}\"")
        buildConfigField("String", "PRODUCT_PRO_YEARLY", "\"${envOrDefault("PRODUCT_PRO_YEARLY", "pro_yearly")}\"")
        buildConfigField("String", "ADMOB_BANNER_UNIT_ID", "\"${envOrDefault("ADMOB_BANNER_UNIT_ID_ANDROID", "ca-app-pub-3940256099942544/6300978111")}\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID_WEB", "\"${envOrDefault("GOOGLE_OAUTH_CLIENT_ID_WEB", "")}\"")

        manifestPlaceholders["admobAppId"] = envOrDefault("ADMOB_APP_ID_ANDROID", "ca-app-pub-3940256099942544~3347511713")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${envOrDefault("API_BASE_URL_DEV", "https://desktop-d5brjmr.tail79d3ce.ts.net:3005/v1")}\"",
            )
        }
        release {
            isMinifyEnabled = true
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${envOrDefault("API_BASE_URL_PROD", envOrDefault("API_BASE_URL", "https://api.docscan.cam/v1"))}\"",
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    // Persistence (Room + Security)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Networking & Serialization
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Billing, Auth & Ads
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.android.gms:play-services-ads:23.6.0")

    // Firebase (Analytics + Crashlytics)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")

    // On-device ML Kit & ONNX
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("com.google.mlkit:text-recognition-devanagari:16.0.1")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.22.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
