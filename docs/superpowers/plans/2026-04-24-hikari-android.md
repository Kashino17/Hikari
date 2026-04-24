# Hikari Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the native Android Kotlin/Compose client that talks to the Hikari backend over Tailscale, renders a vertical-swipe Reels feed via ExoPlayer playing MP4s served from the laptop, and gives Kadir a minimal no-dark-patterns UI (no likes, no counts, no suggestions — just curated content and the four spec-defined gestures).

**Architecture:** Single-module Android app in Kotlin 2.1. Clean layering: API (Retrofit) → Repository → ViewModel (Hilt) → Compose UI. Local cache in Room. Backend base URL stored in DataStore. ExoPlayer (Media3) plays MP4s directly from `${baseUrl}/videos/:id.mp4`. SponsorBlock segments skipped via Player listener.

**Tech Stack:**
- Kotlin 2.1 · Jetpack Compose (BOM 2025.12.00) · Android Gradle Plugin 8.7+
- Min SDK 26 · Target SDK 36
- Retrofit 2.11 + OkHttp 5 + kotlinx-serialization
- Media3 ExoPlayer 1.5
- Room 2.6 · DataStore-Preferences 1.1
- Hilt 2.52 · Navigation-Compose 2.8
- Coil 2.7 (thumbnails)
- Tests: JUnit4 + MockK + coroutines-test + Turbine

**Spec:** `docs/superpowers/specs/2026-04-24-hikari-mvp-design.md` (Section 5.7)
**Backend:** live at `http://kadir-laptop.tailxxxx.ts.net:3000` (Tailscale MagicDNS; Kadir fills actual host in Settings).
**Package name:** `com.hikari.app`

---

## File Structure (Target Layout)

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/
│   ├── wrapper/
│   │   └── gradle-wrapper.properties
│   └── libs.versions.toml
├── gradlew
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/
        │   │   ├── values/strings.xml
        │   │   ├── values/themes.xml
        │   │   ├── values/colors.xml
        │   │   └── xml/network_security_config.xml
        │   └── java/com/hikari/app/
        │       ├── HikariApp.kt                    # @HiltAndroidApp entry
        │       ├── MainActivity.kt                 # Compose host + Nav
        │       ├── data/
        │       │   ├── api/
        │       │   │   ├── HikariApi.kt            # Retrofit interface
        │       │   │   └── dto/                    # @Serializable payloads
        │       │   │       ├── FeedItemDto.kt
        │       │   │       └── ChannelDto.kt
        │       │   ├── db/
        │       │   │   ├── HikariDatabase.kt
        │       │   │   ├── FeedItemEntity.kt
        │       │   │   └── FeedDao.kt
        │       │   ├── prefs/
        │       │   │   └── SettingsStore.kt        # DataStore wrapper
        │       │   └── sponsor/
        │       │       ├── SponsorBlockApi.kt      # direct to sponsor.ajay.pw
        │       │       └── SponsorSegment.kt
        │       ├── domain/
        │       │   ├── model/                      # plain Kotlin, no Android deps
        │       │   │   ├── FeedItem.kt
        │       │   │   └── Channel.kt
        │       │   └── repo/
        │       │       ├── FeedRepository.kt
        │       │       └── ChannelsRepository.kt
        │       ├── di/
        │       │   ├── NetworkModule.kt            # Retrofit, OkHttp
        │       │   ├── DatabaseModule.kt
        │       │   └── AppModule.kt
        │       ├── player/
        │       │   ├── HikariPlayerFactory.kt
        │       │   ├── PreloadCoordinator.kt
        │       │   └── SponsorSkipListener.kt
        │       └── ui/
        │           ├── theme/
        │           │   ├── Theme.kt
        │           │   └── Type.kt
        │           ├── navigation/
        │           │   └── HikariNavHost.kt
        │           ├── feed/
        │           │   ├── FeedScreen.kt
        │           │   ├── FeedViewModel.kt
        │           │   └── ReelPlayer.kt
        │           ├── channels/
        │           │   ├── ChannelsScreen.kt
        │           │   └── ChannelsViewModel.kt
        │           ├── saved/
        │           │   ├── SavedScreen.kt
        │           │   └── SavedViewModel.kt
        │           └── settings/
        │               ├── SettingsScreen.kt
        │               └── SettingsViewModel.kt
        └── test/
            └── java/com/hikari/app/
                ├── data/api/HikariApiTest.kt
                ├── domain/repo/FeedRepositoryTest.kt
                ├── ui/feed/FeedViewModelTest.kt
                └── player/SponsorSkipListenerTest.kt
```

---

## Task 0: Gradle Scaffolding + Manifest + Network Security

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/gradle/wrapper/gradle-wrapper.properties`
- Create: `android/gradle/libs.versions.toml`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/proguard-rules.pro`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/themes.xml`
- Create: `android/app/src/main/res/xml/network_security_config.xml`
- Create: `android/app/src/main/java/com/hikari/app/HikariApp.kt`
- Create: `android/app/src/main/java/com/hikari/app/MainActivity.kt`
- Copy: `gradlew`, `gradle/wrapper/gradle-wrapper.jar` (use `gradle wrapper --gradle-version 8.11`)

- [ ] **Step 1: Create `android/settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "Hikari"
include(":app")
```

- [ ] **Step 2: Create `android/gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 3: Create `android/gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.11-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Generate `gradlew` and `gradle-wrapper.jar` via: `cd android && gradle wrapper --gradle-version 8.11` (requires global gradle; alternatively copy from an existing Android Studio project).

- [ ] **Step 4: Create `android/gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.2"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigationCompose = "2.8.4"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
retrofit = "2.11.0"
okhttp = "5.0.0-alpha.14"
kotlinxSerialization = "1.7.3"
kotlinxCoroutines = "1.9.0"
room = "2.6.1"
datastore = "1.1.1"
media3 = "1.5.0"
coil = "2.7.0"
junit = "4.13.2"
mockk = "1.13.13"
turbine = "1.2.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-kotlinx-serialization = { module = "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter", version = "1.0.0" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
media3-datasource-okhttp = { module = "androidx.media3:media3-datasource-okhttp", version.ref = "media3" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
junit = { module = "junit:junit", version.ref = "junit" }
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 5: Create `android/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 6: Create `android/app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.hikari.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hikari.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
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

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

- [ ] **Step 7: Create `android/app/proguard-rules.pro`** (empty is fine for MVP)

```
# Reserved for future rules.
```

- [ ] **Step 8: Create `android/app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".HikariApp"
        android:allowBackup="false"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:usesCleartextTraffic="true"
        android:supportsRtl="true"
        android:theme="@style/Theme.Hikari">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Hikari">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 9: Create `android/app/src/main/res/xml/network_security_config.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
</network-security-config>
```

Rationale: the Hikari backend serves plain HTTP on Tailscale. All traffic goes through the WireGuard tunnel, so TLS on the app layer is redundant. Cleartext is explicitly permitted.

- [ ] **Step 10: Create `android/app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Hikari</string>
</resources>
```

- [ ] **Step 11: Create `android/app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Hikari" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

The actual theme lives in Compose (`ui/theme/Theme.kt`). This XML style is just a splash/host placeholder.

- [ ] **Step 12: Create `android/app/src/main/java/com/hikari/app/HikariApp.kt`**

```kotlin
package com.hikari.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class HikariApp : Application()
```

- [ ] **Step 13: Create `android/app/src/main/java/com/hikari/app/MainActivity.kt` (stub)**

```kotlin
package com.hikari.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Hikari scaffold") }
    }
}
```

- [ ] **Step 14: Generate Gradle wrapper**

Run: `cd /Users/ayysir/Desktop/Hikari/android && gradle wrapper --gradle-version 8.11`
Expected: creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`.

If you don't have a global gradle installed: `brew install gradle` first.

- [ ] **Step 15: Verify build succeeds**

Run: `cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. An APK appears at `app/build/outputs/apk/debug/app-debug.apk`.

Expect 1–5 minutes for first run (Gradle downloads dependencies).

- [ ] **Step 16: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): project scaffolding + gradle + manifest"
```

---

## Task 1: Retrofit API Contract + DTOs

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/data/api/HikariApi.kt`
- Create: `android/app/src/main/java/com/hikari/app/data/api/dto/FeedItemDto.kt`
- Create: `android/app/src/main/java/com/hikari/app/data/api/dto/ChannelDto.kt`
- Create: `android/app/src/test/java/com/hikari/app/data/api/HikariApiTest.kt`

- [ ] **Step 1: Write DTO — `FeedItemDto.kt`**

```kotlin
package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedItemDto(
    val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String,
    val thumbnailUrl: String,
    val channelId: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val addedAt: Long,
    val saved: Int,
)
```

Matches the backend's `GET /feed` response shape 1:1 (verified from live curl output).

- [ ] **Step 2: Write DTO — `ChannelDto.kt`**

```kotlin
package com.hikari.app.data.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChannelDto(
    val id: String,
    val url: String,
    val title: String,
    val added_at: Long,
    val is_active: Int,
)

@Serializable
data class AddChannelRequest(val channelUrl: String)

@Serializable
data class AddChannelResponse(
    val id: String,
    val title: String,
    val url: String,
)
```

- [ ] **Step 3: Write API interface — `HikariApi.kt`**

```kotlin
package com.hikari.app.data.api

import com.hikari.app.data.api.dto.AddChannelRequest
import com.hikari.app.data.api.dto.AddChannelResponse
import com.hikari.app.data.api.dto.ChannelDto
import com.hikari.app.data.api.dto.FeedItemDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface HikariApi {
    @GET("feed")
    suspend fun getFeed(): List<FeedItemDto>

    @POST("feed/{id}/seen")
    suspend fun markSeen(@Path("id") videoId: String)

    @POST("feed/{id}/save")
    suspend fun save(@Path("id") videoId: String)

    @DELETE("feed/{id}/save")
    suspend fun unsave(@Path("id") videoId: String)

    @POST("feed/{id}/unplayable")
    suspend fun markUnplayable(@Path("id") videoId: String)

    @POST("feed/{id}/less-like-this")
    suspend fun lessLikeThis(@Path("id") videoId: String)

    @GET("channels")
    suspend fun getChannels(): List<ChannelDto>

    @POST("channels")
    suspend fun addChannel(@Body req: AddChannelRequest): AddChannelResponse

    @DELETE("channels/{id}")
    suspend fun deleteChannel(@Path("id") channelId: String)
}
```

- [ ] **Step 4: Write failing test — `HikariApiTest.kt`**

This test uses OkHttp MockWebServer to verify the Retrofit interface serializes/deserializes correctly against the real backend contract.

```kotlin
package com.hikari.app.data.api

import com.hikari.app.data.api.dto.AddChannelRequest
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals

class HikariApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HikariApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HikariApi::class.java)
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun getFeed_parsesLiveBackendShape() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                [{
                  "videoId":"Y7ImxZ_YhJk","title":"Escher","durationSeconds":102,
                  "aspectRatio":"9:16","thumbnailUrl":"https://thumb",
                  "channelId":"UC1","channelTitle":"3B1B","category":"art",
                  "reasoning":"good","addedAt":1777048562119,"saved":0
                }]
                """.trimIndent()
            )
        )
        val feed = api.getFeed()
        assertEquals(1, feed.size)
        assertEquals("Y7ImxZ_YhJk", feed[0].videoId)
        assertEquals("9:16", feed[0].aspectRatio)
    }

    @Test fun addChannel_sendsJsonBody() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"UC1","title":"Test","url":"https://yt.com/@test"}"""
            )
        )
        val res = api.addChannel(AddChannelRequest("https://yt.com/@test"))
        assertEquals("UC1", res.id)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/channels", recorded.path)
    }
}
```

Add dependency to `app/build.gradle.kts`:
```kotlin
testImplementation("com.squareup.okhttp3:mockwebserver:5.0.0-alpha.14")
```

- [ ] **Step 5: Run test — verify pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.hikari.app.data.api.HikariApiTest"`
Expected: 2 tests passing.

- [ ] **Step 6: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/data/api android/app/src/test android/app/build.gradle.kts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): Retrofit API + DTOs matching backend contract"
```

---

## Task 2: Settings DataStore (backend URL)

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/data/prefs/SettingsStore.kt`
- Create: `android/app/src/test/java/com/hikari/app/data/prefs/SettingsStoreTest.kt`

**Purpose:** Store Tailscale backend URL + daily budget + LLM provider (display-only). Reactive via Flow.

- [ ] **Step 1: Implement `SettingsStore.kt`**

```kotlin
package com.hikari.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hikari_settings")

private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
private val DAILY_BUDGET_KEY = intPreferencesKey("daily_budget")

const val DEFAULT_BACKEND_URL = "http://kadir-laptop.tail1234.ts.net:3000"
const val DEFAULT_DAILY_BUDGET = 15

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    val backendUrl: Flow<String> = ctx.dataStore.data.map {
        it[BACKEND_URL_KEY] ?: DEFAULT_BACKEND_URL
    }

    val dailyBudget: Flow<Int> = ctx.dataStore.data.map {
        it[DAILY_BUDGET_KEY] ?: DEFAULT_DAILY_BUDGET
    }

    suspend fun setBackendUrl(url: String) {
        ctx.dataStore.edit { it[BACKEND_URL_KEY] = url.trimEnd('/') }
    }

    suspend fun setDailyBudget(value: Int) {
        ctx.dataStore.edit { it[DAILY_BUDGET_KEY] = value.coerceIn(1, 100) }
    }
}
```

- [ ] **Step 2: Tests for SettingsStore are out of scope for MVP** — DataStore is Android-instrumented and the code is a thin adapter. Skip unit test.

- [ ] **Step 3: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/data/prefs
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): SettingsStore DataStore wrapper"
```

---

## Task 3: Hilt DI — Network Module (reactive base URL)

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/di/NetworkModule.kt`

The tricky bit: the Retrofit instance needs to use the latest URL from DataStore, but DataStore is reactive (Flow-based). Solution: use a `@Singleton OkHttpClient` and a custom `BaseUrlInterceptor` that pulls the URL reactively.

- [ ] **Step 1: Implement `NetworkModule.kt`**

```kotlin
package com.hikari.app.di

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.prefs.SettingsStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideOkHttpClient(store: SettingsStore): OkHttpClient {
        val baseUrlInterceptor = Interceptor { chain ->
            val currentBase = runBlocking { store.backendUrl.first() }
            val base = currentBase.toHttpUrlOrNull() ?: return@Interceptor chain.proceed(chain.request())
            val orig = chain.request()
            val newUrl = orig.url.newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .build()
            chain.proceed(orig.newBuilder().url(newUrl).build())
        }
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, store: SettingsStore): Retrofit {
        val initialBase = runBlocking { store.backendUrl.first() }
            .let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(initialBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides @Singleton
    fun provideHikariApi(retrofit: Retrofit): HikariApi = retrofit.create(HikariApi::class.java)
}
```

**NOTE on `runBlocking`:** Acceptable at DI initialization time for Singleton scope (app start). If we ever need to change URL mid-session, user must restart the app — acceptable for MVP. A cleaner alternative (dynamic Retrofit) is out of scope.

- [ ] **Step 2: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/di/NetworkModule.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): Hilt network module"
```

---

## Task 4: Room Local Cache for Feed Items

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/data/db/FeedItemEntity.kt`
- Create: `android/app/src/main/java/com/hikari/app/data/db/FeedDao.kt`
- Create: `android/app/src/main/java/com/hikari/app/data/db/HikariDatabase.kt`
- Create: `android/app/src/main/java/com/hikari/app/di/DatabaseModule.kt`

- [ ] **Step 1: Implement `FeedItemEntity.kt`**

```kotlin
package com.hikari.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_items")
data class FeedItemEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String,
    val thumbnailUrl: String,
    val channelId: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val addedAt: Long,
    val saved: Boolean,
    val seen: Boolean,
)
```

- [ ] **Step 2: Implement `FeedDao.kt`**

```kotlin
package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feed_items WHERE seen = 0 ORDER BY addedAt DESC")
    fun unseenItems(): Flow<List<FeedItemEntity>>

    @Query("SELECT * FROM feed_items WHERE saved = 1 ORDER BY addedAt DESC")
    fun savedItems(): Flow<List<FeedItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<FeedItemEntity>)

    @Query("UPDATE feed_items SET seen = 1 WHERE videoId = :videoId")
    suspend fun markSeen(videoId: String)

    @Query("UPDATE feed_items SET saved = :saved WHERE videoId = :videoId")
    suspend fun setSaved(videoId: String, saved: Boolean)

    @Query("DELETE FROM feed_items WHERE videoId NOT IN (:keepIds)")
    suspend fun pruneNotIn(keepIds: List<String>)
}
```

- [ ] **Step 3: Implement `HikariDatabase.kt`**

```kotlin
package com.hikari.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FeedItemEntity::class], version = 1, exportSchema = false)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
}
```

- [ ] **Step 4: Implement `DatabaseModule.kt`**

```kotlin
package com.hikari.app.di

import android.content.Context
import androidx.room.Room
import com.hikari.app.data.db.FeedDao
import com.hikari.app.data.db.HikariDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): HikariDatabase =
        Room.databaseBuilder(ctx, HikariDatabase::class.java, "hikari.db").build()

    @Provides @Singleton
    fun provideFeedDao(db: HikariDatabase): FeedDao = db.feedDao()
}
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/data/db android/app/src/main/java/com/hikari/app/di/DatabaseModule.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): Room cache + DAO"
```

---

## Task 5: Domain Model + FeedRepository

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/domain/model/FeedItem.kt`
- Create: `android/app/src/main/java/com/hikari/app/domain/model/Channel.kt`
- Create: `android/app/src/main/java/com/hikari/app/domain/repo/FeedRepository.kt`
- Create: `android/app/src/test/java/com/hikari/app/domain/repo/FeedRepositoryTest.kt`

- [ ] **Step 1: `FeedItem.kt` and `Channel.kt` — pure Kotlin models**

```kotlin
package com.hikari.app.domain.model

data class FeedItem(
    val videoId: String,
    val title: String,
    val durationSeconds: Int,
    val aspectRatio: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val category: String,
    val reasoning: String,
    val saved: Boolean,
)

data class Channel(
    val id: String,
    val url: String,
    val title: String,
)
```

- [ ] **Step 2: Write failing test `FeedRepositoryTest.kt`**

```kotlin
package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.FeedItemDto
import com.hikari.app.data.db.FeedDao
import com.hikari.app.data.db.FeedItemEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class FeedRepositoryTest {
    private val api = mockk<HikariApi>(relaxUnitFun = true)
    private val dao = mockk<FeedDao>(relaxUnitFun = true)
    private val repo = FeedRepository(api, dao)

    @Test fun refresh_upsertsItemsFromApi() = runTest {
        coEvery { api.getFeed() } returns listOf(
            FeedItemDto(
                videoId = "v1", title = "t", durationSeconds = 60,
                aspectRatio = "9:16", thumbnailUrl = "thumb",
                channelId = "c1", channelTitle = "chan",
                category = "science", reasoning = "r",
                addedAt = 100L, saved = 0,
            )
        )
        repo.refresh()
        coVerify { dao.upsertAll(match { it.size == 1 && it[0].videoId == "v1" }) }
        coVerify { dao.pruneNotIn(listOf("v1")) }
    }

    @Test fun unseenItems_mapsEntitiesToModels() = runTest {
        coEvery { dao.unseenItems() } returns flowOf(
            listOf(
                FeedItemEntity(
                    videoId = "v1", title = "t", durationSeconds = 60,
                    aspectRatio = "9:16", thumbnailUrl = "thumb",
                    channelId = "c1", channelTitle = "chan",
                    category = "art", reasoning = "r",
                    addedAt = 100L, saved = false, seen = false,
                )
            )
        )
        val emitted = mutableListOf<List<com.hikari.app.domain.model.FeedItem>>()
        repo.unseenItems().collect { emitted += it }
        assertEquals(1, emitted[0].size)
        assertEquals("v1", emitted[0][0].videoId)
    }
}
```

- [ ] **Step 3: Run test — verify fail**

Run: `./gradlew :app:testDebugUnitTest --tests "*FeedRepositoryTest"`

- [ ] **Step 4: Implement `FeedRepository.kt`**

```kotlin
package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.FeedItemDto
import com.hikari.app.data.db.FeedDao
import com.hikari.app.data.db.FeedItemEntity
import com.hikari.app.domain.model.FeedItem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class FeedRepository @Inject constructor(
    private val api: HikariApi,
    private val dao: FeedDao,
) {
    fun unseenItems(): Flow<List<FeedItem>> =
        dao.unseenItems().map { rows -> rows.map { it.toDomain() } }

    fun savedItems(): Flow<List<FeedItem>> =
        dao.savedItems().map { rows -> rows.map { it.toDomain() } }

    suspend fun refresh() {
        val remote = api.getFeed()
        dao.upsertAll(remote.map { it.toEntity() })
        dao.pruneNotIn(remote.map { it.videoId })
    }

    suspend fun markSeen(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.markSeen(videoId) }
    }

    suspend fun toggleSave(videoId: String, currentlySaved: Boolean) {
        val new = !currentlySaved
        dao.setSaved(videoId, new)
        runCatching { if (new) api.save(videoId) else api.unsave(videoId) }
    }

    suspend fun markUnplayable(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.markUnplayable(videoId) }
    }

    suspend fun lessLikeThis(videoId: String) {
        dao.markSeen(videoId)
        runCatching { api.lessLikeThis(videoId) }
    }
}

private fun FeedItemDto.toEntity() = FeedItemEntity(
    videoId = videoId, title = title, durationSeconds = durationSeconds,
    aspectRatio = aspectRatio, thumbnailUrl = thumbnailUrl,
    channelId = channelId, channelTitle = channelTitle,
    category = category, reasoning = reasoning,
    addedAt = addedAt, saved = saved == 1, seen = false,
)

private fun FeedItemEntity.toDomain() = FeedItem(
    videoId = videoId, title = title, durationSeconds = durationSeconds,
    aspectRatio = aspectRatio, thumbnailUrl = thumbnailUrl,
    channelTitle = channelTitle, category = category,
    reasoning = reasoning, saved = saved,
)
```

**Design choice:** API calls are wrapped in `runCatching` AFTER the local DAO update. This means the UI updates instantly (optimistic), and if the network is down the local state diverges temporarily. When refresh() runs next, the backend's truth wins. Acceptable for single-user local-first.

- [ ] **Step 5: Run tests — verify pass**

Run: `./gradlew :app:testDebugUnitTest --tests "*FeedRepositoryTest"`

- [ ] **Step 6: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/domain android/app/src/test/java/com/hikari/app/domain
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): domain model + FeedRepository with tests"
```

---

## Task 6: ChannelsRepository

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/domain/repo/ChannelsRepository.kt`

This is a thin pass-through — no local cache needed for channel list (it's small, infrequently updated, and Kadir only interacts when adding/removing).

- [ ] **Step 1: Implement**

```kotlin
package com.hikari.app.domain.repo

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.AddChannelRequest
import com.hikari.app.domain.model.Channel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelsRepository @Inject constructor(
    private val api: HikariApi,
) {
    suspend fun list(): List<Channel> = api.getChannels().map {
        Channel(id = it.id, url = it.url, title = it.title)
    }

    suspend fun add(url: String): Channel {
        val res = api.addChannel(AddChannelRequest(channelUrl = url))
        return Channel(id = res.id, url = res.url, title = res.title)
    }

    suspend fun remove(channelId: String) {
        api.deleteChannel(channelId)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/domain/repo/ChannelsRepository.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): ChannelsRepository"
```

---

## Task 7: SponsorBlock Client

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/data/sponsor/SponsorSegment.kt`
- Create: `android/app/src/main/java/com/hikari/app/data/sponsor/SponsorBlockApi.kt`
- Create: `android/app/src/main/java/com/hikari/app/data/sponsor/SponsorBlockClient.kt`

The Android app hits `sponsor.ajay.pw` DIRECTLY (not through the Hikari backend) — the backend caches segments but the community API is public and fine to call client-side. This avoids another API endpoint on the backend.

- [ ] **Step 1: Implement `SponsorSegment.kt`**

```kotlin
package com.hikari.app.data.sponsor

data class SponsorSegment(
    val startSeconds: Double,
    val endSeconds: Double,
    val category: String,
)
```

- [ ] **Step 2: Implement `SponsorBlockApi.kt` + `SponsorBlockClient.kt`**

```kotlin
package com.hikari.app.data.sponsor

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class SkipSegmentDto(val category: String, val segment: List<Double>)

interface SponsorBlockApi {
    @GET("api/skipSegments")
    suspend fun skipSegments(@Query("videoID") videoId: String): List<SkipSegmentDto>
}
```

```kotlin
package com.hikari.app.data.sponsor

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit

@Singleton
class SponsorBlockClient @Inject constructor(client: OkHttpClient, json: Json) {
    private val api = Retrofit.Builder()
        .baseUrl("https://sponsor.ajay.pw/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SponsorBlockApi::class.java)

    suspend fun fetchSegments(videoId: String): List<SponsorSegment> =
        runCatching {
            api.skipSegments(videoId).map {
                SponsorSegment(
                    startSeconds = it.segment[0],
                    endSeconds = it.segment[1],
                    category = it.category,
                )
            }
        }.getOrElse { e ->
            if (e is HttpException && e.code() == 404) emptyList()
            else emptyList() // network failures: best-effort
        }
}
```

- [ ] **Step 3: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/data/sponsor
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): SponsorBlock client"
```

---

## Task 8: ExoPlayer Factory + Preload Coordinator

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/player/HikariPlayerFactory.kt`
- Create: `android/app/src/main/java/com/hikari/app/player/PreloadCoordinator.kt`

- [ ] **Step 1: Implement `HikariPlayerFactory.kt`**

```kotlin
package com.hikari.app.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class HikariPlayerFactory @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: OkHttpClient,
) {
    fun create(): ExoPlayer {
        val okhttpSource = OkHttpDataSource.Factory(client)
        val dsFactory = DefaultDataSource.Factory(ctx, okhttpSource)
        return ExoPlayer.Builder(ctx)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
            .apply { playWhenReady = true }
    }

    fun mediaItemFor(baseUrl: String, videoId: String): MediaItem =
        MediaItem.fromUri("$baseUrl/videos/$videoId.mp4")
}
```

- [ ] **Step 2: Implement `PreloadCoordinator.kt`**

```kotlin
package com.hikari.app.player

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Given the current player and a list of upcoming MediaItems, makes sure
 * ExoPlayer has the next 2 items queued so swipes are instant. Invokes
 * `prepare()` once when the queue becomes non-empty.
 */
object PreloadCoordinator {
    fun setQueue(player: ExoPlayer, upcoming: List<MediaItem>) {
        if (upcoming.isEmpty()) {
            player.clearMediaItems()
            return
        }
        player.setMediaItems(upcoming, /* resetPosition = */ true)
        player.prepare()
    }
}
```

The real preload logic (buffering next items silently) is handled automatically by ExoPlayer when multiple items are queued — it builds buffers in priority order. Our code just ensures the queue is always populated.

- [ ] **Step 3: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/player
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): ExoPlayer factory + preload coordinator"
```

---

## Task 9: SponsorBlock Skip Listener

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/player/SponsorSkipListener.kt`
- Create: `android/app/src/test/java/com/hikari/app/player/SponsorSkipListenerTest.kt`

**Purpose:** Given current playback position (polled from player), find the earliest sponsor segment that contains `currentMs` and return the skip-to timestamp. Pure function — no Android deps.

- [ ] **Step 1: Write failing test**

```kotlin
package com.hikari.app.player

import com.hikari.app.data.sponsor.SponsorSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SponsorSkipListenerTest {
    @Test fun noSegments_returnsNull() {
        assertNull(SponsorSkipListener.skipTargetMs(currentMs = 5000, segments = emptyList()))
    }

    @Test fun currentInsideSegment_returnsEndMs() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"))
        assertEquals(20_000L, SponsorSkipListener.skipTargetMs(currentMs = 12_000, segments = segs))
    }

    @Test fun currentBeforeAllSegments_returnsNull() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"))
        assertNull(SponsorSkipListener.skipTargetMs(currentMs = 5000, segments = segs))
    }

    @Test fun currentBetweenSegments_returnsNull() {
        val segs = listOf(
            SponsorSegment(5.0, 10.0, "sponsor"),
            SponsorSegment(30.0, 40.0, "selfpromo"),
        )
        assertNull(SponsorSkipListener.skipTargetMs(currentMs = 20_000, segments = segs))
    }
}
```

- [ ] **Step 2: Run — verify fail**

- [ ] **Step 3: Implement `SponsorSkipListener.kt`**

```kotlin
package com.hikari.app.player

import com.hikari.app.data.sponsor.SponsorSegment

object SponsorSkipListener {
    /**
     * If the current playback position falls inside any sponsor segment,
     * returns the skip-to position (end of segment) in milliseconds.
     * Returns null if no skip is warranted.
     */
    fun skipTargetMs(currentMs: Long, segments: List<SponsorSegment>): Long? {
        val current = currentMs / 1000.0
        val inside = segments.firstOrNull { current >= it.startSeconds && current < it.endSeconds }
        return inside?.let { (it.endSeconds * 1000).toLong() }
    }
}
```

- [ ] **Step 4: Run — pass**

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/player/SponsorSkipListener.kt android/app/src/test/java/com/hikari/app/player
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): SponsorBlock skip logic"
```

---

## Task 10: FeedViewModel

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/feed/FeedViewModel.kt`
- Create: `android/app/src/test/java/com/hikari/app/ui/feed/FeedViewModelTest.kt`

- [ ] **Step 1: Write failing test**

```kotlin
package com.hikari.app.ui.feed

import app.cash.turbine.test
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.FeedRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class FeedViewModelTest {
    private val repo = mockk<FeedRepository>(relaxUnitFun = true)
    private val settings = mockk<SettingsStore>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.backendUrl } returns flowOf("http://laptop.local:3000")
        every { settings.dailyBudget } returns flowOf(15)
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun unseenItems_flowsFromRepo_cappedAtDailyBudget() = runTest {
        val items = (0..19).map { FeedItem("v$it", "t$it", 60, "9:16", "", "c", "sci", "r", false) }
        every { repo.unseenItems() } returns flowOf(items)

        val vm = FeedViewModel(repo, settings)
        vm.items.test {
            val first = awaitItem()
            assertEquals(15, first.size)
            assertEquals("v0", first[0].videoId)
        }
    }
}
```

- [ ] **Step 2: Implement `FeedViewModel.kt`**

```kotlin
package com.hikari.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repo: FeedRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    val backendUrl: StateFlow<String> = settings.backendUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val items: StateFlow<List<FeedItem>> =
        combine(repo.unseenItems(), settings.dailyBudget) { list, budget ->
            list.take(budget)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        runCatching { repo.refresh() }
    }

    fun onSeen(id: String) = viewModelScope.launch { repo.markSeen(id) }
    fun onToggleSave(id: String, currentlySaved: Boolean) = viewModelScope.launch {
        repo.toggleSave(id, currentlySaved)
    }
    fun onUnplayable(id: String) = viewModelScope.launch { repo.markUnplayable(id) }
    fun onLessLikeThis(id: String) = viewModelScope.launch { repo.lessLikeThis(id) }
}
```

- [ ] **Step 3: Run tests — pass**

- [ ] **Step 4: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/feed/FeedViewModel.kt android/app/src/test/java/com/hikari/app/ui/feed
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): FeedViewModel with daily budget"
```

---

## Task 11: ReelPlayer Composable

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/feed/ReelPlayer.kt`

Renders one video full-screen 9:16 with a dim overlay showing channel + title + category. Accepts gesture callbacks.

- [ ] **Step 1: Implement**

```kotlin
package com.hikari.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hikari.app.domain.model.FeedItem

@Composable
fun ReelPlayer(
    item: FeedItem,
    player: ExoPlayer,
    mediaItem: MediaItem,
    onSeen: () -> Unit,
    onToggleSave: () -> Unit,
    onLessLikeThis: () -> Unit,
) {
    var playing by remember { mutableStateOf(true) }

    DisposableEffect(item.videoId) {
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        playing = true
        onSeen()
        onDispose { player.clearMediaItems() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.videoId) {
                detectTapGestures(
                    onTap = {
                        playing = !playing
                        player.playWhenReady = playing
                    },
                    onDoubleTap = { onLessLikeThis() },
                    onLongPress = { onToggleSave() },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setPlayer(player)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        // Bottom overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        ) {
            Text(
                item.channelTitle,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                item.title,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${item.category} · ${item.durationSeconds}s",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/feed/ReelPlayer.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): ReelPlayer composable with gesture map"
```

---

## Task 12: FeedScreen (VerticalPager + swipe wiring)

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/feed/FeedScreen.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.hikari.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.player.HikariPlayerFactory
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlayerFactoryEntryPoint {
    fun playerFactory(): HikariPlayerFactory
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(vm: FeedViewModel = hiltViewModel()) {
    val items by vm.items.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()
    val ctx = LocalContext.current
    val factory = remember {
        EntryPointAccessors.fromApplication(ctx, PlayerFactoryEntryPoint::class.java).playerFactory()
    }
    val player = remember { factory.create() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    LaunchedEffect(Unit) { vm.refresh() }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No new reels today. Come back tomorrow.", Modifier.padding(24.dp))
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })
    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        val item = items[page]
        val mediaItem = factory.mediaItemFor(baseUrl, item.videoId)
        ReelPlayer(
            item = item,
            player = player,
            mediaItem = mediaItem,
            onSeen = { vm.onSeen(item.videoId) },
            onToggleSave = { vm.onToggleSave(item.videoId, item.saved) },
            onLessLikeThis = { vm.onLessLikeThis(item.videoId) },
        )
    }
}
```

**Note:** For MVP, `onUnplayable` is wired but not invoked from UI — it's only triggered if ExoPlayer fires `onPlayerError`. A follow-up task can add `Player.Listener` and call `vm.onUnplayable(currentItem.videoId)` on error. Keep simple for now.

- [ ] **Step 2: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/feed/FeedScreen.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): FeedScreen with VerticalPager"
```

---

## Task 13: ChannelsScreen + ViewModel

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/channels/ChannelsViewModel.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/channels/ChannelsScreen.kt`

- [ ] **Step 1: Implement ViewModel**

```kotlin
package com.hikari.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.model.Channel
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val repo: ChannelsRepository,
) : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch {
        _busy.value = true
        runCatching { repo.list() }
            .onSuccess { _channels.value = it; _error.value = null }
            .onFailure { _error.value = it.message ?: "Unknown error" }
        _busy.value = false
    }

    fun add(url: String) = viewModelScope.launch {
        _busy.value = true
        runCatching { repo.add(url) }
            .onSuccess { _error.value = null }
            .onFailure { _error.value = it.message ?: "Couldn't add channel" }
        _busy.value = false
        load()
    }

    fun remove(channelId: String) = viewModelScope.launch {
        runCatching { repo.remove(channelId) }
        load()
    }
}
```

- [ ] **Step 2: Implement `ChannelsScreen.kt`**

```kotlin
package com.hikari.app.ui.channels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(vm: ChannelsViewModel = hiltViewModel()) {
    val channels by vm.channels.collectAsState()
    val busy by vm.busy.collectAsState()
    val error by vm.error.collectAsState()
    var newUrl by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Channels") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                label = { Text("Channel URL") },
                placeholder = { Text("https://www.youtube.com/@...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.add(newUrl.trim()); newUrl = "" },
                enabled = !busy && newUrl.isNotBlank(),
            ) { Text("Add") }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            LazyColumn {
                items(channels, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = { Text(c.title) },
                        supportingContent = {
                            Text(c.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            TextButton(onClick = { vm.remove(c.id) }) { Text("Remove") }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/channels
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): ChannelsScreen"
```

---

## Task 14: SavedScreen + ViewModel

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/saved/SavedViewModel.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/saved/SavedScreen.kt`

- [ ] **Step 1: Implement ViewModel**

```kotlin
package com.hikari.app.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.model.FeedItem
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SavedViewModel @Inject constructor(
    repo: FeedRepository,
) : ViewModel() {
    val saved: StateFlow<List<FeedItem>> =
        repo.savedItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

- [ ] **Step 2: Implement `SavedScreen.kt`** — a simple grid of thumbnails

```kotlin
package com.hikari.app.ui.saved

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(vm: SavedViewModel = hiltViewModel()) {
    val saved by vm.saved.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Saved") }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(padding).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(saved, key = { it.videoId }) { item ->
                Card {
                    Column {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                        Text(
                            item.title,
                            maxLines = 2,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/saved
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): SavedScreen"
```

---

## Task 15: SettingsScreen + ViewModel

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/settings/SettingsViewModel.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Implement ViewModel**

```kotlin
package com.hikari.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
) : ViewModel() {
    val backendUrl: StateFlow<String> =
        store.backendUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val dailyBudget: StateFlow<Int> =
        store.dailyBudget.stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    fun setBackendUrl(url: String) = viewModelScope.launch { store.setBackendUrl(url) }
    fun setDailyBudget(value: Int) = viewModelScope.launch { store.setDailyBudget(value) }
}
```

- [ ] **Step 2: Implement `SettingsScreen.kt`**

```kotlin
package com.hikari.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val backend by vm.backendUrl.collectAsState()
    val budget by vm.dailyBudget.collectAsState()
    var draftUrl by remember(backend) { mutableStateOf(backend) }
    var draftBudget by remember(budget) { mutableStateOf(budget.toString()) }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text("Hikari Backend URL (Tailscale)")
            OutlinedTextField(
                value = draftUrl,
                onValueChange = { draftUrl = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.setBackendUrl(draftUrl.trim()) }) { Text("Save URL") }

            Spacer(Modifier.height(24.dp))
            Text("Daily Budget (max reels per day)")
            OutlinedTextField(
                value = draftBudget,
                onValueChange = { draftBudget = it.filter(Char::isDigit) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                draftBudget.toIntOrNull()?.let { vm.setDailyBudget(it) }
            }) { Text("Save Budget") }

            Spacer(Modifier.height(24.dp))
            Text("Changes to the backend URL require an app restart.",
                style = MaterialTheme.typography.labelSmall)
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/settings
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): SettingsScreen"
```

---

## Task 16: Navigation Shell + Final MainActivity

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/theme/Theme.kt`
- Create: `android/app/src/main/java/com/hikari/app/ui/navigation/HikariNavHost.kt`
- Modify: `android/app/src/main/java/com/hikari/app/MainActivity.kt`

- [ ] **Step 1: Implement `Theme.kt`** — minimal dark theme (Hikari is a night-mode "lights in the darkness" app)

```kotlin
package com.hikari.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun HikariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colors, content = content)
}
```

- [ ] **Step 2: Implement `HikariNavHost.kt`**

```kotlin
package com.hikari.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hikari.app.ui.channels.ChannelsScreen
import com.hikari.app.ui.feed.FeedScreen
import com.hikari.app.ui.saved.SavedScreen
import com.hikari.app.ui.settings.SettingsScreen

private data class Dest(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val destinations = listOf(
    Dest("feed", "Feed", Icons.Default.PlayArrow),
    Dest("saved", "Saved", Icons.Default.Favorite),
    Dest("channels", "Channels", Icons.Default.List),
    Dest("settings", "Settings", Icons.Default.Settings),
)

@Composable
fun HikariNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { d ->
                    NavigationBarItem(
                        selected = currentRoute == d.route || currentRoute?.startsWith(d.route) == true,
                        onClick = {
                            nav.navigate(d.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(d.icon, d.label) },
                        label = { Text(d.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "feed", modifier = Modifier.padding(padding)) {
            composable("feed") { FeedScreen() }
            composable("saved") { SavedScreen() }
            composable("channels") { ChannelsScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
```

- [ ] **Step 3: Rewrite `MainActivity.kt`**

```kotlin
package com.hikari.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.hikari.app.ui.navigation.HikariNavHost
import com.hikari.app.ui.theme.HikariTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HikariTheme {
                HikariNavHost()
            }
        }
    }
}
```

- [ ] **Step 4: Verify debug build**

Run: `cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Path printed: `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 5: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: tests from Tasks 1, 5, 9, 10 pass (roughly 8-10 tests).

- [ ] **Step 6: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/theme android/app/src/main/java/com/hikari/app/ui/navigation android/app/src/main/java/com/hikari/app/MainActivity.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android): navigation shell + final MainActivity"
```

---

## Task 17: Smoke Test (Manual — requires phone)

**Files:** none

- [ ] **Step 1: Enable USB debugging on Android phone**

Settings → About Phone → tap "Build Number" 7× to enable Developer Options.
Settings → Developer Options → enable "USB debugging".

- [ ] **Step 2: Connect phone via USB and authorize**

Run: `adb devices`
Expected: device listed as `device` (not `unauthorized`). If unauthorized, accept the fingerprint dialog on the phone.

- [ ] **Step 3: Install Tailscale on phone**

From Play Store. Sign in with same account as the laptop. Verify both show as online in the Tailscale admin panel.

- [ ] **Step 4: Determine laptop's Tailscale hostname**

On laptop: `tailscale ip -4` gives Tailscale IPv4 (e.g., `100.125.192.44`). Or use MagicDNS hostname (e.g., `kadir-laptop`).

The URL to use in app: `http://<host>:3000`.

- [ ] **Step 5: Install APK**

Run: `cd /Users/ayysir/Desktop/Hikari/android && ./gradlew :app:installDebug`
Expected: "Installed on 1 device". Launcher shows Hikari icon.

- [ ] **Step 6: Start the Hikari backend on laptop**

Run: `cd /Users/ayysir/Desktop/Hikari/backend && npm run dev`

Verify LM Studio is running with `qwen/qwen3.6-27b` loaded.

- [ ] **Step 7: First-run flow in the app**

1. Open Hikari. FeedScreen shows "No new reels today" (fresh install, empty local cache).
2. Tap Settings (bottom nav). Enter backend URL: `http://<tailscale-host>:3000`. Save.
3. Restart app (kill and reopen — the NetworkModule reads URL at DI init).
4. Tap Channels. Paste `https://www.youtube.com/@3blue1brown`. Tap Add.
5. Back to Feed. Pull-to-refresh isn't implemented yet — but on navigating back, `vm.refresh()` runs.
6. Verify videos appear. Swipe up/down to cycle.

- [ ] **Step 8: Validate each gesture**

- Tap: video pauses/plays
- Long-press: save toggles (verify in Saved screen)
- Double-tap: "less like this" removes from feed
- Swipe vertical: next/prev video
- Verify video plays smoothly over Tailscale

- [ ] **Step 9: Tag MVP**

```bash
git -C /Users/ayysir/Desktop/Hikari tag android-mvp-v0.1
```

---

## Done Criteria

- [ ] All unit tests pass: `./gradlew :app:testDebugUnitTest` → 0 failures
- [ ] Debug APK builds: `./gradlew :app:assembleDebug` → 0 errors
- [ ] APK installs on real phone and reaches main screens
- [ ] Add-channel flow works end-to-end (via Tailscale to backend)
- [ ] Feed screen plays at least 2 videos via ExoPlayer streaming from laptop
- [ ] All four gestures respond correctly
- [ ] `android-mvp-v0.1` tag created

---

## Known Follow-Ups (Out of Scope for MVP)

- Pull-to-refresh on FeedScreen
- Player error handler → auto-invoke `onUnplayable`
- SponsorBlock skip wired into player listener (Task 8+9 are built but not yet plumbed into ReelPlayer — a focused follow-up task)
- Preload coordinator actually invoked from FeedScreen (currently each ReelPlayer sets its own single item)
- Dynamic URL swap without app restart
- Splash screen / app icon (currently using system default)
- Haptics on gestures
- Support-creator link ON each reel → explicitly dropped per spec decision #10

These are deliberate scope cuts: the MVP proves the architecture end-to-end; polish comes after Kadir uses it for a few days and decides what matters.
