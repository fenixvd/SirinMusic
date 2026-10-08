plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "ru.rainedev.sirinmusic"
    buildToolsVersion = "37.0.0"
    compileSdk {
        // 37 требуют зависимости Compose BOM 2026.09.00; targetSdk остаётся 36 —
        // compileSdk только даёт доступ к новым API, поведение не меняет.
        version = release(37)
    }

    defaultConfig {
        applicationId = "ru.rainedev.sirinmusic"
        minSdk = 27
        targetSdk = 36
        versionCode = providers.environmentVariable("SIRIN_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = providers.environmentVariable("SIRIN_VERSION_NAME").orNull ?: "2.0rc0.2-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val keystorePath = providers.environmentVariable("SIRIN_KEYSTORE_PATH").orNull
    val releaseSigning = if (keystorePath != null) signingConfigs.create("release") {
        storeFile = file(keystorePath)
        storePassword = providers.environmentVariable("SIRIN_KEYSTORE_PASSWORD").get()
        keyAlias = providers.environmentVariable("SIRIN_KEY_ALIAS").get()
        keyPassword = providers.environmentVariable("SIRIN_KEY_PASSWORD").get()
    } else null

    buildTypes {
        release {
            signingConfig = releaseSigning
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.work.runtime)
    implementation(libs.material.color.utilities)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.security.crypto)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}