# Hikari v0.6 — Full SponsorBlock Integration (Plan)

> **For agentic workers:** Execute with superpowers:subagent-driven-development or executing-plans.
> Steps use `- [ ]` checklist syntax.

**Goal:** Implement full SponsorBlock category management in Hikari — all 10 segment categories, per-category behavior (auto/manual/ignore), manual-skip overlay pill, and lifetime stats ("X segments skipped, Y hours saved").

**Motivation:** Kadir's feedback after v0.5.4: NewPipe/ReVanced have a mature SponsorBlock UX with granular user control. Hikari already uses the same API (`sponsor.ajay.pw`) but blindly auto-skips only two categories (`sponsor`, `selfpromo`) without asking. We extend to all 10, give Kadir behavior toggles, and surface stats.

**Tech context:** This is Android-only. No backend changes required — the Android app talks directly to `sponsor.ajay.pw`. Room schema unchanged. DataStore gets new keys.

**Spec reference:** `docs/superpowers/specs/2026-04-24-hikari-mvp-design.md` Section 5.3 (SponsorBlock) + 5.7 (Android).

---

## Data Model

### `SegmentBehavior` enum

```kotlin
enum class SegmentBehavior {
    SKIP_AUTO,    // silently seek past the segment
    SKIP_MANUAL,  // show "Segment überspringen" pill, user decides
    IGNORE,       // play the segment normally
}
```

### 10 categories with metadata

```kotlin
data class SegmentCategory(
    val apiKey: String,           // SponsorBlock API identifier
    val germanLabel: String,      // UI label
    val germanDescription: String,// short description for Settings
    val color: Color,             // dot in Settings + pill accent
    val defaultBehavior: SegmentBehavior,
)
```

Full catalog (defaults chosen to match ReVanced conventions):

| API key | Label | Default | Rationale |
|---------|-------|---------|-----------|
| `sponsor` | Sponsor | SKIP_AUTO | Objektiv Werbung |
| `selfpromo` | Eigenwerbung | SKIP_AUTO | Merch/Patreon/Shoutouts |
| `interaction` | Like/Abo-Aufruf | SKIP_AUTO | Reine Interaktions-Call-to-Action |
| `intro` | Intro | SKIP_MANUAL | User-Präferenz |
| `outro` | Outro/Endkarten | SKIP_MANUAL | User-Präferenz |
| `preview` | Vorschau/Recap | IGNORE | Oft substanziell |
| `hook` | Hook/Begrüßung | IGNORE | Oft Kontext |
| `filler` | Filler/Witze | SKIP_MANUAL | Abschweifungen |
| `music_offtopic` | Non-Music | IGNORE | Sehr Musikvideo-spezifisch |
| `poi_highlight` | Highlight | IGNORE | Ist kein Skip, nur Marker |

### Stats

Stored in DataStore as two longs plus an optional per-category breakdown:

- `skip_total_count: Long` — Anzahl übersprungener Segmente (lifetime)
- `skip_total_ms: Long` — Summe der gesparten Millisekunden
- Optional: `skip_count_<category>: Long` für Stats-Details (V1: skip)

---

## SponsorBlock API Contract (Reference)

Endpoint:
```
GET https://sponsor.ajay.pw/api/skipSegments
  ?videoID=<id>
  &categories=["sponsor","selfpromo","interaction","intro","outro","preview","hook","filler","music_offtopic","poi_highlight"]
```

Response:
```json
[
  {
    "category": "sponsor",
    "actionType": "skip",
    "segment": [12.5, 38.2],
    "UUID": "abc...",
    "videoDuration": 612
  }
]
```

**actionType** values: `skip | mute | full | poi | chapter`.
Hikari v0.6 handles only `skip` and `poi` (the latter is ignored for skip purposes). `mute` requires audio API — skip for now.

---

## File Structure (Target)

```
android/app/src/main/java/com/hikari/app/
├── data/
│   ├── prefs/
│   │   ├── SettingsStore.kt                       # (existing, keep)
│   │   └── SponsorBlockPrefs.kt                   # NEW — behavior + stats store
│   └── sponsor/
│       ├── SponsorBlockClient.kt                  # MODIFY — all categories query
│       ├── SponsorSegment.kt                      # (existing)
│       ├── SegmentCategory.kt                     # NEW — catalog + labels
│       └── SegmentBehavior.kt                     # NEW — enum + defaults
├── player/
│   └── SponsorSkipListener.kt                     # MODIFY — behavior-aware evaluate
├── ui/
│   ├── feed/
│   │   ├── ReelPlayer.kt                          # MODIFY — manual-skip pill + record stat
│   │   └── ManualSkipPill.kt                      # NEW — overlay composable
│   └── settings/
│       ├── SettingsScreen.kt                      # MODIFY — SponsorBlock section
│       ├── SettingsViewModel.kt                   # MODIFY — expose behaviors + stats
│       └── SponsorBlockSection.kt                 # NEW — the UI block
└── di/
    └── AppModule.kt                               # (check) provide SponsorBlockPrefs if not already via @Inject
```

---

## Task 1: SegmentCategory catalog + Behavior enum

**Files:**
- `android/app/src/main/java/com/hikari/app/data/sponsor/SegmentBehavior.kt`
- `android/app/src/main/java/com/hikari/app/data/sponsor/SegmentCategory.kt`

- [ ] **Step 1: Create `SegmentBehavior.kt`**

```kotlin
package com.hikari.app.data.sponsor

enum class SegmentBehavior { SKIP_AUTO, SKIP_MANUAL, IGNORE }
```

- [ ] **Step 2: Create `SegmentCategory.kt`**

```kotlin
package com.hikari.app.data.sponsor

import androidx.compose.ui.graphics.Color

data class SegmentCategory(
    val apiKey: String,
    val label: String,
    val description: String,
    val color: Color,
    val defaultBehavior: SegmentBehavior,
)

object SegmentCategories {
    val all: List<SegmentCategory> = listOf(
        SegmentCategory(
            apiKey = "sponsor",
            label = "Sponsor",
            description = "Bezahlte Werbung",
            color = Color(0xFF00D400),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
        ),
        SegmentCategory(
            apiKey = "selfpromo",
            label = "Eigenwerbung",
            description = "Merch, Patreon, Shoutouts",
            color = Color(0xFFFFFF00),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
        ),
        SegmentCategory(
            apiKey = "interaction",
            label = "Like/Abo-Aufruf",
            description = "Aufruf zu liken, abonnieren, folgen",
            color = Color(0xFFCC00FF),
            defaultBehavior = SegmentBehavior.SKIP_AUTO,
        ),
        SegmentCategory(
            apiKey = "intro",
            label = "Intro",
            description = "Intro-Animation ohne Inhalt",
            color = Color(0xFF00FFFF),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
        ),
        SegmentCategory(
            apiKey = "outro",
            label = "Outro / Endkarten",
            description = "Credits, Endkarten, Abspann",
            color = Color(0xFF0202ED),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
        ),
        SegmentCategory(
            apiKey = "preview",
            label = "Vorschau / Recap",
            description = "Clips die zeigen was kommt oder was war",
            color = Color(0xFF008FD6),
            defaultBehavior = SegmentBehavior.IGNORE,
        ),
        SegmentCategory(
            apiKey = "hook",
            label = "Hook / Begrüßung",
            description = "Teaser für das Video, Begrüßung",
            color = Color(0xFF6B6B9A),
            defaultBehavior = SegmentBehavior.IGNORE,
        ),
        SegmentCategory(
            apiKey = "filler",
            label = "Abschweifung / Filler",
            description = "Witze, Tangenten, nicht benötigt für Hauptinhalt",
            color = Color(0xFF7300FF),
            defaultBehavior = SegmentBehavior.SKIP_MANUAL,
        ),
        SegmentCategory(
            apiKey = "music_offtopic",
            label = "Non-Music",
            description = "Nicht-Musik-Passagen in Musikvideos",
            color = Color(0xFFFF9900),
            defaultBehavior = SegmentBehavior.IGNORE,
        ),
        SegmentCategory(
            apiKey = "poi_highlight",
            label = "Highlight",
            description = "Der Hauptteil des Videos (kein Skip)",
            color = Color(0xFFFF1684),
            defaultBehavior = SegmentBehavior.IGNORE,
        ),
    )

    fun byKey(apiKey: String): SegmentCategory? = all.firstOrNull { it.apiKey == apiKey }
}
```

- [ ] **Step 3: Compile check + commit**

```bash
cd /Users/ayysir/Desktop/Hikari/android
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:compileDebugKotlin --no-daemon
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/data/sponsor/SegmentBehavior.kt android/app/src/main/java/com/hikari/app/data/sponsor/SegmentCategory.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android/sponsorblock): SegmentBehavior enum + 10-category catalog"
```

---

## Task 2: SponsorBlockPrefs — per-category behavior + lifetime stats

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/data/prefs/SponsorBlockPrefs.kt`

- [ ] **Step 1: Implement**

```kotlin
package com.hikari.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategories
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sponsorBlockDataStore by preferencesDataStore(name = "hikari_sponsorblock")

private val TOTAL_SKIPPED_COUNT = longPreferencesKey("skip_total_count")
private val TOTAL_SKIPPED_MS = longPreferencesKey("skip_total_ms")
private fun behaviorKey(apiKey: String) =
    stringPreferencesKey("skip_behavior_$apiKey")

@Singleton
class SponsorBlockPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    /** Reactive map of category apiKey → current user behavior. */
    val behaviors: Flow<Map<String, SegmentBehavior>> = ctx.sponsorBlockDataStore.data.map { prefs ->
        SegmentCategories.all.associate { cat ->
            cat.apiKey to (prefs[behaviorKey(cat.apiKey)]
                ?.let { runCatching { SegmentBehavior.valueOf(it) }.getOrNull() }
                ?: cat.defaultBehavior)
        }
    }

    val totalSkippedCount: Flow<Long> = ctx.sponsorBlockDataStore.data.map {
        it[TOTAL_SKIPPED_COUNT] ?: 0L
    }
    val totalSkippedMs: Flow<Long> = ctx.sponsorBlockDataStore.data.map {
        it[TOTAL_SKIPPED_MS] ?: 0L
    }

    suspend fun setBehavior(apiKey: String, behavior: SegmentBehavior) {
        ctx.sponsorBlockDataStore.edit { it[behaviorKey(apiKey)] = behavior.name }
    }

    suspend fun recordSkip(segmentDurationMs: Long) {
        ctx.sponsorBlockDataStore.edit {
            it[TOTAL_SKIPPED_COUNT] = (it[TOTAL_SKIPPED_COUNT] ?: 0L) + 1
            it[TOTAL_SKIPPED_MS] = (it[TOTAL_SKIPPED_MS] ?: 0L) + segmentDurationMs.coerceAtLeast(0L)
        }
    }

    suspend fun resetStats() {
        ctx.sponsorBlockDataStore.edit {
            it[TOTAL_SKIPPED_COUNT] = 0L
            it[TOTAL_SKIPPED_MS] = 0L
        }
    }
}
```

- [ ] **Step 2: Compile check + commit**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/data/prefs/SponsorBlockPrefs.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android/sponsorblock): DataStore prefs for behaviors + stats"
```

---

## Task 3: Update SponsorBlockClient to fetch all 10 categories

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/data/sponsor/SponsorBlockClient.kt`

- [ ] **Step 1: Extend `SponsorBlockApi` + client**

Change the query shape to include a `categories` parameter:

```kotlin
// SponsorBlockApi.kt
interface SponsorBlockApi {
    @GET("api/skipSegments")
    suspend fun skipSegments(
        @Query("videoID") videoId: String,
        @Query("categories") categoriesJson: String,
    ): List<SkipSegmentDto>
}

// SponsorBlockClient.kt
private val CATEGORIES_JSON = SegmentCategories.all
    .joinToString(prefix = "[", postfix = "]", separator = ",") { "\"${it.apiKey}\"" }

suspend fun fetchSegments(videoId: String): List<SponsorSegment> =
    runCatching {
        api.skipSegments(videoId, CATEGORIES_JSON).map {
            SponsorSegment(
                startSeconds = it.segment[0],
                endSeconds = it.segment[1],
                category = it.category,
            )
        }
    }.getOrElse { e ->
        if (e is HttpException && e.code() == 404) emptyList()
        else emptyList()
    }
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/data/sponsor/SponsorBlockClient.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android/sponsorblock): fetch all 10 categories"
```

---

## Task 4: Behavior-aware SponsorSkipListener

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/player/SponsorSkipListener.kt`
- Modify: `android/app/src/test/java/com/hikari/app/player/SponsorSkipListenerTest.kt`

New API: evaluate returns a sealed `Decision` expressing what the player should do.

- [ ] **Step 1: Rewrite `SponsorSkipListener.kt`**

```kotlin
package com.hikari.app.player

import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SponsorSegment

object SponsorSkipListener {
    sealed class Decision {
        data object None : Decision()
        data class Auto(val segment: SponsorSegment, val targetMs: Long) : Decision()
        data class Manual(val segment: SponsorSegment, val targetMs: Long) : Decision()
    }

    /**
     * Given the current playback position, segments for this video, and the user's
     * per-category behavior, decide what to do:
     *   - Auto → player should seekTo(targetMs)
     *   - Manual → UI should show a "skip" pill with onClick seekTo(targetMs)
     *   - None → nothing to do
     *
     * If multiple segments contain the current position, prefer the one ending latest
     * (largest endMs). This avoids bouncing between nested segments.
     */
    fun evaluate(
        currentMs: Long,
        segments: List<SponsorSegment>,
        behaviors: Map<String, SegmentBehavior>,
    ): Decision {
        if (segments.isEmpty()) return Decision.None
        val currentSeconds = currentMs / 1000.0
        val containing = segments.filter {
            currentSeconds >= it.startSeconds && currentSeconds < it.endSeconds
        }.maxByOrNull { it.endSeconds } ?: return Decision.None

        val behavior = behaviors[containing.category] ?: SegmentBehavior.IGNORE
        val targetMs = (containing.endSeconds * 1000).toLong()
        return when (behavior) {
            SegmentBehavior.SKIP_AUTO -> Decision.Auto(containing, targetMs)
            SegmentBehavior.SKIP_MANUAL -> Decision.Manual(containing, targetMs)
            SegmentBehavior.IGNORE -> Decision.None
        }
    }

    /** Legacy helper kept for backward compat while migrating callers. */
    @Deprecated("Use evaluate with behaviors map", ReplaceWith("evaluate(...)"))
    fun skipTargetMs(currentMs: Long, segments: List<SponsorSegment>): Long? =
        evaluate(
            currentMs,
            segments,
            behaviors = SponsorSegment::class.let {
                com.hikari.app.data.sponsor.SegmentCategories.all
                    .associate { cat -> cat.apiKey to SegmentBehavior.SKIP_AUTO }
            },
        ).let { d -> (d as? Decision.Auto)?.targetMs }
}
```

(The legacy helper is only needed if anything still calls it; prefer to migrate fully.)

- [ ] **Step 2: Rewrite tests to cover all three branches**

```kotlin
package com.hikari.app.player

import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SponsorSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SponsorSkipListenerTest {
    private val allAuto = mapOf(
        "sponsor" to SegmentBehavior.SKIP_AUTO,
        "intro" to SegmentBehavior.SKIP_AUTO,
    )
    private val introManual = mapOf(
        "sponsor" to SegmentBehavior.SKIP_AUTO,
        "intro" to SegmentBehavior.SKIP_MANUAL,
    )
    private val introIgnore = mapOf(
        "sponsor" to SegmentBehavior.SKIP_AUTO,
        "intro" to SegmentBehavior.IGNORE,
    )

    @Test fun none_when_outside_any_segment() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"))
        assertTrue(SponsorSkipListener.evaluate(5_000, segs, allAuto) is SponsorSkipListener.Decision.None)
    }

    @Test fun auto_returns_end_of_containing_segment() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "sponsor"))
        val d = SponsorSkipListener.evaluate(12_000, segs, allAuto)
        assertTrue(d is SponsorSkipListener.Decision.Auto)
        assertEquals(20_000L, (d as SponsorSkipListener.Decision.Auto).targetMs)
    }

    @Test fun manual_when_behavior_is_manual() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "intro"))
        val d = SponsorSkipListener.evaluate(12_000, segs, introManual)
        assertTrue(d is SponsorSkipListener.Decision.Manual)
    }

    @Test fun none_when_behavior_is_ignore() {
        val segs = listOf(SponsorSegment(10.0, 20.0, "intro"))
        assertTrue(SponsorSkipListener.evaluate(12_000, segs, introIgnore) is SponsorSkipListener.Decision.None)
    }

    @Test fun overlapping_segments_prefers_longer_ending() {
        val segs = listOf(
            SponsorSegment(10.0, 15.0, "sponsor"),
            SponsorSegment(10.0, 25.0, "sponsor"),
        )
        val d = SponsorSkipListener.evaluate(12_000, segs, allAuto)
        assertTrue(d is SponsorSkipListener.Decision.Auto)
        assertEquals(25_000L, (d as SponsorSkipListener.Decision.Auto).targetMs)
    }
}
```

- [ ] **Step 3: Run + commit**

```bash
./gradlew :app:testDebugUnitTest --tests "*SponsorSkipListenerTest" --no-daemon
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/player/SponsorSkipListener.kt android/app/src/test/java/com/hikari/app/player/SponsorSkipListenerTest.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android/sponsorblock): behavior-aware skip decision"
```

---

## Task 5: ManualSkipPill composable

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/feed/ManualSkipPill.kt`

A pill that fades in, offers "<Label> überspringen →" text, auto-dismisses after 5s or on click.

- [ ] **Step 1: Implement**

```kotlin
package com.hikari.app.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hikari.app.data.sponsor.SegmentCategory

/**
 * Overlay pill shown when the current playback position enters a SKIP_MANUAL segment.
 * Fades in, shows "{category label} überspringen →", dismisses after 5s or on click.
 */
@Composable
fun ManualSkipPill(
    category: SegmentCategory?,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember(category) { mutableStateOf(category != null) }
    LaunchedEffect(category) {
        if (category != null) {
            visible = true
            kotlinx.coroutines.delay(5_000)
            visible = false
        }
    }
    AnimatedVisibility(
        visible = visible && category != null,
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 3 },
        exit = fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { it / 3 },
        modifier = modifier,
    ) {
        category?.let { cat ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier
                    .clickable { onSkip() }
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(cat.color, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${cat.label} überspringen  →",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/feed/ManualSkipPill.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android/sponsorblock): ManualSkipPill overlay composable"
```

---

## Task 6: ReelPlayer integration (pill + auto-skip + stats)

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/ui/feed/ReelPlayer.kt`
- Modify: `android/app/src/main/java/com/hikari/app/ui/feed/FeedScreen.kt` (FeedEntryPoint: add SponsorBlockPrefs)

- [ ] **Step 1: Add `SponsorBlockPrefs` to `FeedEntryPoint`**

In `FeedScreen.kt`:

```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FeedEntryPoint {
    fun playerFactory(): HikariPlayerFactory
    fun sponsorBlockClient(): SponsorBlockClient
    fun playbackRepository(): PlaybackRepository
    fun sponsorBlockPrefs(): com.hikari.app.data.prefs.SponsorBlockPrefs  // NEW
}
```

And in FeedScreen pass it down:

```kotlin
val sponsorBlockPrefs = remember { entryPoint.sponsorBlockPrefs() }
// ...
ReelPlayer(
    // ... existing args ...
    sponsorBlockPrefs = sponsorBlockPrefs,  // NEW param
    ...
)
```

- [ ] **Step 2: Update `ReelPlayer` signature + skip logic**

Add parameter `sponsorBlockPrefs: SponsorBlockPrefs`.

Replace the current SponsorBlock skip loop:

```kotlin
// OLD:
LaunchedEffect(item.videoId, segments, isCurrent) {
    if (!isCurrent) return@LaunchedEffect
    while (true) {
        kotlinx.coroutines.delay(200)
        val pos = player.currentPosition
        val skip = SponsorSkipListener.skipTargetMs(pos, segments)
        if (skip != null && skip > pos) player.seekTo(skip)
    }
}
```

with a behavior-aware version:

```kotlin
val behaviors by sponsorBlockPrefs.behaviors.collectAsState(initial = SegmentCategories.all
    .associate { it.apiKey to it.defaultBehavior })

var manualSegmentCategory by remember(item.videoId) {
    mutableStateOf<SegmentCategory?>(null)
}
var manualTargetMs by remember { mutableLongStateOf(0L) }
val scope = rememberCoroutineScope()

LaunchedEffect(item.videoId, segments, isCurrent, behaviors) {
    if (!isCurrent) return@LaunchedEffect
    while (true) {
        kotlinx.coroutines.delay(200)
        val pos = player.currentPosition
        when (val decision = SponsorSkipListener.evaluate(pos, segments, behaviors)) {
            is SponsorSkipListener.Decision.Auto -> {
                if (decision.targetMs > pos) {
                    val saved = decision.targetMs - pos
                    player.seekTo(decision.targetMs)
                    scope.launch { sponsorBlockPrefs.recordSkip(saved) }
                    // Clear any manual pill that may still be up
                    if (manualSegmentCategory != null) manualSegmentCategory = null
                }
            }
            is SponsorSkipListener.Decision.Manual -> {
                val category = SegmentCategories.byKey(decision.segment.category)
                if (manualSegmentCategory?.apiKey != category?.apiKey) {
                    manualSegmentCategory = category
                    manualTargetMs = decision.targetMs
                }
            }
            SponsorSkipListener.Decision.None -> {
                if (manualSegmentCategory != null) manualSegmentCategory = null
            }
        }
    }
}
```

- [ ] **Step 3: Render the pill in ReelPlayer's Box**

Near the top-right, below the top bar safe area:

```kotlin
// Inside the main Box(fillMaxSize)...
ManualSkipPill(
    category = manualSegmentCategory,
    onSkip = {
        val target = manualTargetMs
        if (target > 0L && isCurrent) {
            val pos = player.currentPosition
            val saved = (target - pos).coerceAtLeast(0L)
            player.seekTo(target)
            scope.launch { sponsorBlockPrefs.recordSkip(saved) }
            manualSegmentCategory = null
        }
    },
    modifier = Modifier
        .align(Alignment.TopEnd)
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(top = 48.dp, end = 12.dp),  // sits below FeedScreen's top bar
)
```

- [ ] **Step 4: Build + commit**

```bash
./gradlew :app:assembleDebug --no-daemon
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/feed/ReelPlayer.kt android/app/src/main/java/com/hikari/app/ui/feed/FeedScreen.kt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android/sponsorblock): ReelPlayer integration — auto + manual + stats"
```

---

## Task 7: Settings UI — per-category toggle + stats

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/settings/SponsorBlockSection.kt`
- Modify: `android/app/src/main/java/com/hikari/app/ui/settings/SettingsViewModel.kt`
- Modify: `android/app/src/main/java/com/hikari/app/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Extend `SettingsViewModel`**

```kotlin
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SettingsStore,
    private val sponsorPrefs: SponsorBlockPrefs,  // NEW inject
) : ViewModel() {
    // ... existing backendUrl, dailyBudget ...

    val segmentBehaviors: StateFlow<Map<String, SegmentBehavior>> =
        sponsorPrefs.behaviors.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            SegmentCategories.all.associate { it.apiKey to it.defaultBehavior }
        )

    val totalSkippedCount: StateFlow<Long> =
        sponsorPrefs.totalSkippedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val totalSkippedMs: StateFlow<Long> =
        sponsorPrefs.totalSkippedMs.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    fun setSegmentBehavior(apiKey: String, behavior: SegmentBehavior) =
        viewModelScope.launch { sponsorPrefs.setBehavior(apiKey, behavior) }

    fun resetSponsorStats() = viewModelScope.launch { sponsorPrefs.resetStats() }
}
```

- [ ] **Step 2: Implement `SponsorBlockSection.kt`**

```kotlin
package com.hikari.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategory

@Composable
fun SponsorBlockSection(
    categories: List<SegmentCategory>,
    behaviors: Map<String, SegmentBehavior>,
    totalSkippedCount: Long,
    totalSkippedMs: Long,
    onBehaviorChange: (apiKey: String, SegmentBehavior) -> Unit,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("SponsorBlock", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        // Stats card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Statistik",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "$totalSkippedCount Segmente übersprungen",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Zeit gespart: ${formatHms(totalSkippedMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onResetStats) { Text("Statistik zurücksetzen") }
            }
        }

        Spacer(Modifier.height(20.dp))

        Text("Kategorien", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))

        categories.forEachIndexed { i, cat ->
            CategoryRow(
                cat = cat,
                current = behaviors[cat.apiKey] ?: cat.defaultBehavior,
                onChange = { onBehaviorChange(cat.apiKey, it) },
            )
            if (i < categories.lastIndex) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun CategoryRow(
    cat: SegmentCategory,
    current: SegmentBehavior,
    onChange: (SegmentBehavior) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(cat.color, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(cat.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    cat.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        val options = listOf(
            SegmentBehavior.SKIP_AUTO to "Auto",
            SegmentBehavior.SKIP_MANUAL to "Manuell",
            SegmentBehavior.IGNORE to "Aus",
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { idx, (value, label) ->
                SegmentedButton(
                    selected = current == value,
                    onClick = { onChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(idx, options.size),
                ) { Text(label) }
            }
        }
    }
}

private fun formatHms(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (hours > 0 || minutes > 0) append("${minutes}m ")
        append("${seconds}s")
    }
}
```

- [ ] **Step 3: Plug into `SettingsScreen`**

Inside SettingsScreen's Column, after the existing fields, add:

```kotlin
Spacer(Modifier.height(32.dp))
val behaviors by vm.segmentBehaviors.collectAsState()
val skippedCount by vm.totalSkippedCount.collectAsState()
val skippedMs by vm.totalSkippedMs.collectAsState()
SponsorBlockSection(
    categories = com.hikari.app.data.sponsor.SegmentCategories.all,
    behaviors = behaviors,
    totalSkippedCount = skippedCount,
    totalSkippedMs = skippedMs,
    onBehaviorChange = vm::setSegmentBehavior,
    onResetStats = vm::resetSponsorStats,
)
```

- [ ] **Step 4: Build + commit**

```bash
./gradlew :app:assembleDebug --no-daemon
git -C /Users/ayysir/Desktop/Hikari add android/app/src/main/java/com/hikari/app/ui/settings
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(android/sponsorblock): Settings section — per-category behavior + stats"
```

---

## Task 8: End-to-end verification + release

- [ ] **Step 1: Full test run**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

All existing tests still pass + the 5 new SponsorSkipListener tests.

- [ ] **Step 2: Manual smoke test on device**

1. Install APK over v0.5.4
2. Open a video known to have sponsor segments (3Blue1Brown often doesn't; SpongeLore often does)
3. Watch — sponsor/selfpromo/interaction segments skip silently
4. Open Settings → SponsorBlock section — see Statistik incrementing
5. Change "Intro" from Manuell to Auto. Play a video with intro segment. It should now auto-skip.
6. Change "Sponsor" from Auto to Manuell. Play a video with sponsor. A pill should appear top-right; tap → skips. Pill auto-dismisses after 5s.
7. Toggle "Sponsor" to Aus. Sponsor segment plays normally.

- [ ] **Step 3: Tag + release**

```bash
git -C /Users/ayysir/Desktop/Hikari tag android-mvp-v0.6
git -C /Users/ayysir/Desktop/Hikari push --tags origin
gh release create android-mvp-v0.6 \
  /Users/ayysir/Desktop/Hikari/android/app/build/outputs/apk/debug/app-debug.apk#Hikari-v0.6-debug.apk \
  --title "Hikari v0.6 — Full SponsorBlock" \
  --notes "(release notes)"
```

---

## Testing Strategy

### Unit tests (JVM, already part of baseline)
- `SponsorSkipListenerTest` — 5 branches: none, auto, manual, ignore, overlapping
- Do NOT unit-test `SponsorBlockPrefs` (DataStore is Android-instrumented; thin adapter)
- Do NOT unit-test Compose UI (out of MVP scope)

### Manual smoke (Task 8 Step 2)
- Coverage for auto-skip
- Coverage for manual pill behavior + tap
- Coverage for ignore
- Stats increment

### Known risks / edge cases
- **API down:** SponsorBlockClient swallows errors → empty list → no skip behavior changes. Stats not affected. ✓
- **Overlapping segments of different categories:** current implementation picks the segment ending latest. If a sponsor sits inside an intro, the sponsor's endMs is smaller so intro wins. In practice overlapping happens rarely; acceptable behavior.
- **User switches behavior mid-playback:** `LaunchedEffect(..., behaviors, ...)` restarts the skip loop on behavior change. No glitches.
- **Manual pill dismisses then user re-enters same segment:** `manualSegmentCategory` tracks by apiKey; re-entering the same segment with a NULL→non-NULL transition re-shows the pill. User could theoretically oscillate, but SponsorBlock segments are linear (non-repeating) in videos, so not an issue.
- **Race: user taps pill as the skip loop is about to auto-dismiss `manualSegmentCategory = null`:** pill's onSkip reads `manualTargetMs` captured by LaunchedEffect scope, not the state var, so it's safe.

---

## Out of Scope (v0.6)

- `mute` actionType segments (separate audio track; needs ExoPlayer volume-schedule hooks)
- User-contributed new segments (needs auth with private user ID)
- "Like-Button"-on-segment (voting on segment accuracy)
- Segment min-duration filter ("skip only segments ≥ N seconds")
- Import/Export config as JSON
- Per-video override

These are on the roadmap for v0.7 if Kadir wants them.

---

## Estimated Effort

- Task 1 (enum + catalog): 10 min
- Task 2 (prefs): 15 min
- Task 3 (client update): 10 min
- Task 4 (listener + tests): 25 min
- Task 5 (pill composable): 20 min
- Task 6 (player integration): 35 min
- Task 7 (settings UI): 45 min
- Task 8 (verify + release): 20 min

**Total: ~3 hours** for one focused implementer (subagent).

---

## Next Step

After approval: dispatch via superpowers:subagent-driven-development. All 8 tasks as one big batch is OK — they're tightly related and a single subagent can keep coherence better than 8 handoffs.
