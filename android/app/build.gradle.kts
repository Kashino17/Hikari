import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Stabile Release-Signierung für In-Place-Updates. Lokal liegen Keystore und
// Passwörter unter keystore/ (gitignored), in der CI kommen sie aus GitHub-
// Secrets per Env-Variablen. Ohne vollständige Konfiguration fällt der
// Release-Build auf den Debug-Key zurück (nur für lokale Builds — niemals
// für veröffentlichte APKs, sonst brechen Updates wieder).
// Hinweis: kein `java.util.Properties`/`java.io.File` vollqualifiziert — das
// `java`-Extension-Objekt des angewendeten Java-Plugins überdeckt hier das
// Package, deshalb Import + kurze Namen.
val releaseSigningProps = Properties().apply {
    val propsFile = rootProject.file("keystore/signing.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}
val releaseStoreFile = (System.getenv("HIKARI_RELEASE_KEYSTORE")?.let { File(it) }
    ?: rootProject.file("keystore/hikari-release.jks")).takeIf { it.exists() }
val releaseStorePassword = System.getenv("HIKARI_RELEASE_STORE_PASSWORD")
    ?: releaseSigningProps.getProperty("storePassword")
val releaseKeyPassword = System.getenv("HIKARI_RELEASE_KEY_PASSWORD")
    ?: releaseSigningProps.getProperty("keyPassword")
val useReleaseSigning = releaseStoreFile != null &&
    !releaseStorePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.hikari.app"
    compileSdk = 34   // was 36 in plan — deviation due to only android-34 installed

    defaultConfig {
        applicationId = "com.hikari.app"
        minSdk = 26
        targetSdk = 34   // was 36 in plan — same deviation
        versionCode = 142
        versionName = "0.82.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (useReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = "hikari"
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (useReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.work.runtime)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.session)

    implementation(libs.coil.compose)
    implementation(libs.telephoto.zoomable.image.coil)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0-alpha.14")
}
