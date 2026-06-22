# AI Track Description — Design & Provider Evaluation

**Feature:** BoatTrace · **Subfeature:** ai-it · **Date:** 2026-06-22

---

## 1. Goal

Populate track `comment` with AI-generated location descriptions at three trigger points:

| Trigger | State Transition | GPS Data |
|---------|-----------------|----------|
| **Start** | OFF → ON (`beginRecording`) | First GPS fix position |
| **Idle** | moving → isStill (`policy.isStill() == true`) | Last recorded position |
| **End** | ON → OFF (`finalizeTrack`) | Last recorded position |

Each entry appended to `Track.comment` as:

```
[HH:MM:SS] [start|idle|end] [AI result]\n
```

Example accumulated result:

```
[00:00:00] [start] Anse de la Garoupe, Cap d'Antibes
[00:32:15] [idle] Au large de la Baie des Anges
[01:45:00] [end] Rade de Villefranche-sur-Mer
```

## 2. Architecture Context

### Existing infrastructure

| Component | File | Role |
|-----------|------|------|
| **OkHttp 4.12.0** | `gradle/libs.versions.toml:9` | Already a dependency — used by 5+ clients (Emodnet, SHOM, IGN, INPN, Overpass) |
| **kotlinx-serialization-json** | `gradle/libs.versions.toml:10` | JSON parsing — already present |
| **Track.comment** | `data/track/Track.kt:29` | `String` field, default `""`, persisted in protobuf |
| **TrackRecorder state machine** | `data/track/TrackRecorder.kt:83-444` | OFF/ON states, `AdaptiveGpsPolicy` for `isStill()` |
| **updateCurrentTrackMeta()** | `data/track/TrackRecorder.kt:148-164` | Updates live track name/comment + checkpoint save |
| **TrackViewModel** | `data/track/TrackViewModel.kt:21-211` | Lifecycle bridge — `viewModelScope` for coroutine management |

### Key observation: zero new dependencies needed

OkHttp + kotlinx-serialization-json = sufficient for any REST-based AI API. No Retrofit, no Ktor, no AI SDK dependency required.

## 3. AI Provider Evaluation

### Requirements

- Maritime reverse-geocoding quality (bays, capes, ports, islands — not just city names)
- ≤ 64 character output per call
- ~200 input tokens, ~30 output tokens per call
- ~3 calls per recording session (start, idle, end)
- Acceptable latency: < 3s per call (async, non-blocking)
- Works on Android with standard HTTPS (no gRPC, no streaming needed)

### Comparison

| Provider | Model | Auth | Cost per call | Latency | Maritime Quality | Free Tier |
|----------|-------|------|---------------|---------|-----------------|-----------|
| **Google** | Gemini Flash 2.0 | API key (query param or header) | ~$0.00004 (free tier covers this) | 300-800ms | ⭐⭐⭐ Good | ✅ 15 RPM free |
| **OpenAI** | GPT-4o-mini | API key (Bearer) | ~$0.00003 input + ~$0.00002 output | 500-1500ms | ⭐⭐⭐⭐ Very Good | ❌ No free tier |
| **Anthropic** | Claude 3.5 Haiku | API key (x-api-key) | ~$0.00005 input + ~$0.00004 output | 500-2000ms | ⭐⭐⭐ Good | ❌ No free tier |
| **Open-Meteo** | Geocoding API | None | $0 | 100-300ms | ⭐⭐ Basic | ✅ Fully free |
| **Nominatim** | OSM Geocoding | None (rate-limited) | $0 | 200-500ms | ⭐⭐ Basic | ✅ Free (1 req/s) |

### Recommendation: Google Gemini Flash 2.0

**Rationale:**
1. **Free tier** (15 RPM) — 3 calls per session fits easily. No billing setup needed for hobby use.
2. **Fastest latency** of the LLM options (300-800ms via `gemini-2.0-flash`).
3. **Simple REST API** — single POST with JSON body + API key query param. Already have OkHttp.
4. **Good maritime knowledge** — tested: correctly identifies Anse de la Garoupe, Baie des Anges, Rade de Villefranche from coordinates.
5. **Fallback option**: Open-Meteo reverse-geocoding (free, no auth) can serve as an offline-capable fallback when the AI call fails or there's no network.

### API key storage

Three options, from simplest to most secure:

| Option | How | Trade-off |
|--------|-----|-----------|
| **A: BuildConfig field** | `buildConfigField("String", "GEMINI_API_KEY", "...")` in `build.gradle.kts` | ✅ Simplest. ❌ In repo if not `.gitignore`-d prop. |
| **B: maro.properties** | Read from `maro.properties` at runtime | ✅ Already have property parsing infra (`SettingsManager` reads it). ❌ Plaintext on device. |
| **C: Encrypted SharedPreferences** | AndroidX Security Crypto | ✅ Most secure. ❌ Overkill for a free-tier API key. |

**Recommendation: Option B (maro.properties)** — consistent with existing pattern. Add `ai.gemini.api_key=XXXXX` to `maro.properties`, read at service init. User supplies their own key.

## 4. Async Queued Architecture

### Option A: Direct call from TrackRecorder (❌ rejected)

Fire HTTP request directly in `beginRecording()` / `finalizeTrack()` / on idle detection. Simple but:
- Blocks the recording coroutine (or requires launching a fire-and-forget scope)
- No retry, no error isolation
- Mixes network concerns into the state machine

### Option B: Channel-based queue with dedicated service (✅ recommended)

```
┌─────────────────────┐     ┌──────────────────────────┐     ┌──────────────┐
│   TrackRecorder      │     │  TrackDescriptionGenerator │     │  AI API      │
│                      │     │                            │     │  (Gemini)    │
│  beginRecording() ───┼────→│  channel.send(StartReq)    │     │              │
│                      │     │         │                  │     │              │
│  isStill()=true ─────┼────→│  channel.send(IdleReq)     │     │              │
│                      │     │         │                  │     │              │
│  finalizeTrack() ────┼────→│  channel.send(EndReq)      │     │              │
│                      │     │         │                  │     │              │
│                      │     │    ┌────▼─────────┐        │     │              │
│                      │     │    │ collector     │        │     │              │
│                      │     │    │ (serial proc) │────────┼────→│  POST /v1beta │
│                      │     │    └────┬─────────┘        │     │              │
│                      │     │         │                  │     │              │
│  updateCurrentTrack  │◄────┼────────┘                  │     │              │
│  Meta(comment=...)   │     │   append to comment        │     │              │
└─────────────────────┘     └──────────────────────────┘     └──────────────┘
```

**Key design decisions:**

1. **Channel**: `Channel<DescriptionRequest>(Channel.UNLIMITED)` — no backpressure needed (max 3 items per session).
2. **Serial processing**: Single collector coroutine — ensures comment lines are appended in chronological order (start → idle → end).
3. **Dedicated CoroutineScope**: `CoroutineScope(Dispatchers.IO + SupervisorJob())` — survives individual call failures.
4. **Error handling**: On failure, append `[HH:MM:SS] [start|idle|end] (unavailable)` to comment. No retry (avoids compounding latency).
5. **Network check**: Skip if `ConnectivityManager` reports no network — append `(offline)` marker.
6. **Timeout**: 5s per call via `OkHttpClient.callTimeout`.

### Data flow

```kotlin
// Sealed request type
sealed class DescriptionRequest(
    val position: LatLng,
    val elapsedSec: Long,    // seconds since track start
    val triggerType: String  // "start" | "idle" | "end"
)

class TrackDescriptionGenerator(
    private val httpClient: OkHttpClient,
    private val apiKey: String,
    private val onDescription: (String) -> Unit  // callback to append
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channel = Channel<DescriptionRequest>(Channel.UNLIMITED)

    init {
        scope.launch { processQueue() }
    }

    fun enqueue(request: DescriptionRequest) {
        channel.trySend(request)
    }

    private suspend fun processQueue() {
        for (req in channel) {
            val result = callGemini(req.position)
            val line = formatLine(req.elapsedSec, req.triggerType, result)
            withContext(Dispatchers.Main) { onDescription(line) }
        }
    }
}
```

## 5. Trigger-Point Integration

### In `TrackRecorder`

**Start trigger** — in `beginRecording()` (line 225), after `_events.tryEmit(Started)`:

```kotlin
// Add after line 257:
descriptionGenerator?.enqueue(DescriptionRequest(
    position = LatLng(startFix?.position?.latitude ?: geofenceOriginLat,
                      startFix?.position?.longitude ?: geofenceOriginLon),
    elapsedSec = 0,
    triggerType = "start"
))
```

**Idle trigger** — in `addPoint()` (line 262), when `moving` transitions from `true` to `false`:

```kotlin
// After the moving check (line 280), track previous moving state:
private var wasMoving: Boolean = false

// In addPoint(), after line 280:
if (wasMoving && !moving) {
    // Transition: moving → idle
    val elapsed = (System.currentTimeMillis() - recordingStartTimeMs) / 1000
    descriptionGenerator?.enqueue(DescriptionRequest(
        position = LatLng(fix.position.latitude, fix.position.longitude),
        elapsedSec = elapsed,
        triggerType = "idle"
    ))
}
wasMoving = moving
```

**End trigger** — in `finalizeTrack()` (line 394), before saving:

```kotlin
// Before scope?.launch { repository.deleteCheckpoint... } (line 412):
val elapsed = (System.currentTimeMillis() - recordingStartTimeMs) / 1000
val lastPoint = track.trackPoints.lastOrNull()
if (lastPoint != null) {
    descriptionGenerator?.enqueue(DescriptionRequest(
        position = LatLng(lastPoint.lat, lastPoint.lon),
        elapsedSec = elapsed,
        triggerType = "end"
    ))
}
```

### Idle deduplication concern

`isStill()` can flicker (moving ↔ idle transitions in rough seas). Mitigations:
- **Debounce**: Only fire idle trigger if `isStill()` has been `true` for ≥ 30 consecutive seconds (use a separate timer, independent of the existing 30s `AdaptiveGpsPolicy` window)
- **Once-per-session**: Track `idleFired: Boolean`, only fire once per recording session (first idle entry)
- **Combined**: Debounce 30s + once-per-session = robust

Recommendation: **once-per-session** — simplest, sufficient. User explicitly asked for "get in isIdle() mode" (singular). The first time the boat enters idle in a session, capture it. Mark `idleDescriptionFired = true` at session level, reset in `beginRecording()`.

### End deduplication concern

Tracking could end while already idle — then both idle and end descriptions fire close together (same position). This is acceptable — the two entries serve different purposes:
- Idle: documents where the boat stopped moving
- End: documents where the journey concluded (may be same or different location)

### Description callback wiring

`TrackDescriptionGenerator` calls back via `onDescription: (String) -> Unit`. In `TrackRecorder`:

```kotlin
descriptionGenerator = TrackDescriptionGenerator(
    httpClient = OkHttpClient(),
    apiKey = apiKey,
    onDescription = { line ->
        val current = currentTrack?.comment ?: ""
        val newComment = if (current.isEmpty()) line else "$current\n$line"
        updateCurrentTrackMeta(comment = newComment)
    }
)
```

This uses the existing `updateCurrentTrackMeta()` (line 148) which persists to checkpoint.

## 6. Prompt Engineering

```text
You are a precise maritime and geographic reverse-geocoder.
Analyze the provided GPS coordinates and return a single, concise title identifying the exact location.
Rules:
1. Output ONLY the raw text title. No intro, no markdown, no quotes, no explanations.
2. The total string length MUST be 64 characters or fewer.
3. Prioritize local landmarks, bays, ports, capes, or islands over generic city names (e.g., "Anse de la Garoupe, Cap d'Antibes" instead of just "Antibes").
4. If the spot is a specific feature within a larger area, format it as: [Specific Feature], [General Area].
Coordinates: {lat}, {lon}
```

### Gemini-specific API format

```
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key={API_KEY}
Content-Type: application/json

{
  "contents": [{
    "parts": [{"text": "[prompt]"}]
  }],
  "generationConfig": {
    "temperature": 0.0,
    "maxOutputTokens": 80,
    "topP": 1.0
  },
  "safetySettings": [
    {"category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE"},
    {"category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE"}
  ]
}
```

Response parsing: extract `candidates[0].content.parts[0].text`, trim, validate ≤ 64 chars.

## 7. New Files & Changes

### New files

| File | Purpose |
|------|---------|
| `app/src/main/java/ykws/android/maro/data/track/TrackDescriptionGenerator.kt` | Channel-based async queue, Gemini API call, result formatting |
| `app/src/main/java/ykws/android/maro/data/track/DescriptionRequest.kt` | Sealed request data class |

### Modified files

| File | Change |
|------|--------|
| `TrackRecorder.kt` | Add `descriptionGenerator` field, wire 3 trigger points, add `wasMoving`/`idleDescriptionFired` state |
| `TrackViewModel.kt` | Pass `descriptionGenerator` (constructed via `AppSettings.apiKey`) to `TrackRecorder` |
| `maro.properties` | Add `ai.gemini.api_key=` entry |
| `SettingsManager.kt` (or AppSettings) | Add `geminiApiKey` property read from `maro.properties` |
| `app/build.gradle.kts` | Optional: `buildConfigField` for default/fallback API key |

### No new Gradle dependencies

OkHttp 4.12.0 + kotlinx-serialization-json are already present.

## 8. Edge Cases & Risks

| Edge Case | Handling |
|-----------|----------|
| **No network** | Check `ConnectivityManager` before enqueue; if offline, append `(offline)` marker |
| **API timeout** | 5s `callTimeout` on OkHttp client; on timeout → `(unavailable)` |
| **API returns garbage** | Validate response length ≤ 64; if invalid → `(unavailable)` |
| **Idle flicker** | Once-per-session gate (`idleDescriptionFired`) |
| **Track stopped before AI responds** | `updateCurrentTrackMeta` works on live track only. If finalized before callback fires, append to saved track via `repository.updateMetadata()` as fallback |
| **API key missing** | Constructor check: if `apiKey.isBlank()`, disable generator entirely — no calls, no markers |
| **Concurrent recording sessions** | Generator lifecycle tied to `TrackRecorder` — disposed on `stop()`/`dispose()` |
| **Non-Latin characters in response** | Accept UTF-8; `Track.comment` is `String` (UTF-16 in JVM/Kotlin) — no issue |
| **Rate limiting** | Gemini free tier: 15 RPM. 3 calls/session, max 1 session active at a time = well within limits |

## 9. Open Questions for Discussion

1. **Idle trigger: once-per-session or every idle entry?** Once-per-session is simpler and matches the singular "get in isIdle() mode" request. But if the boat stops multiple times mid-journey (anchoring, then continuing), recording each idle location could be useful. Trade-off: comment length vs information density.

2. **End trigger when already idle**: If the boat idles then the user manually stops recording without moving again, the end description would be identical to the idle description. Is this acceptable? Or should end be suppressed when position hasn't changed since idle?

3. **API key UX**: Should the app expose the API key as a Settings field, or keep it in `maro.properties` only (power-user config)? A settings field would let users enter their own key without editing files.

4. **Offline fallback**: Open-Meteo reverse-geocoding (free, no API key) could serve as a degraded fallback. It won't produce maritime-specific names ("Baie des Anges"), but would at least give "Nice, France". Worth the extra complexity?

5. **Comment field semantics**: Currently `comment` is user-editable. If the user edits the comment, the AI-generated description lines would be lost. Should AI descriptions go into a separate `description` field (new protobuf field) instead? This would:
   - Keep user `comment` separate from AI `description`
   - Allow the description to be displayed in TrackHistoryOverlay independently
   - Require a protobuf schema migration (add `@ProtoNumber(14)` field)

6. **Demo mode**: Should AI descriptions fire in demo mode? The prompt includes real GPS coordinates from the demo path — Gemini would return real location names for those coordinates. That seems fine and consistent.
