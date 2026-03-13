# Unified Diagnostics Plan (v2)

## Goal

Align diagnostic emissions between Android and iOS so that JS-side logs alone can diagnose playback issues (songs repeating, stuck states, Bluetooth misbehavior, etc.) on either platform.

## Principles

- Same tag names, same payload shape on both platforms
- Piggyback on existing callbacks — no polling, no new permissions
- Include `audioRoute` in every diagnostic so we always know the output device context
- Don't break existing events (STATE_CHANGED, TRACK_ENDED, PROGRESS, etc.) — diagnostics are additive
- DIAGNOSTIC events are for developer observability, not for driving app logic
- Always emit diagnostics (no gating behind debug flag) — ~6-10 events per track lifecycle is negligible

---

## Common Envelope

Every diagnostic payload includes these correlation fields:

```ts
{
  tag: string,
  data: {
    ts: number,          // Date.now() / System.currentTimeMillis() — wall clock ms
    seq: number,         // Monotonic counter per native module lifecycle, starts at 0
    trackId?: string,    // Current track's mediaId/URL hash — correlates events to a track
    route: { type: string, name: string },
    // ... event-specific fields
  }
}
```

`seq` is a simple integer incremented on every `emitDiagnostic` call. Lets you detect dropped/reordered events in logs.

---

## Shared Helper: `getAudioRoute()`

Both platforms implement a synchronous helper that returns a snapshot of the current active audio output.

**Return shape:**
```ts
{
  type: string   // "BLUETOOTH_A2DP" | "BLUETOOTH_HFP" | "BLUETOOTH_LE" | "WIRED_HEADSET" | "WIRED_HEADPHONES" | "SPEAKER" | "EARPIECE" | "AIRPLAY" | "USB" | "LINE_OUT" | "UNKNOWN"
  name: string   // Human-readable device name, e.g. "AirPods Pro", "Built-In Speaker"
}
```

**Platform notes:**
- `CAR_AUDIO` omitted from shared enum. iOS `.carAudio` maps to `"BLUETOOTH_A2DP"` or `"UNKNOWN"` since Android car audio routes through BT/USB.
- `EARPIECE` added for iOS `.builtInReceiver` (phone earpiece during calls).

### Android

```kotlin
private fun getAudioRoute(): Map<String, String> {
    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    // API 31+: getAudioDevicesForAttributes returns the ACTIVE output device
    // Pre-31: fall back to boolean queries
    val type: String
    val name: String
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val attrs = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA).setContentType(AudioProController.settingAudioContentType).build()
        val devices = am.getAudioDevicesForAttributes(attrs.audioAttributesV21.audioAttributes)
        val device = devices.firstOrNull()
        type = device?.let { mapDeviceType(it.type) } ?: "UNKNOWN"
        name = device?.productName?.toString() ?: "Unknown"
    } else {
        @Suppress("DEPRECATION")
        type = when {
            am.isBluetoothA2dpOn -> "BLUETOOTH_A2DP"
            am.isBluetoothScoOn -> "BLUETOOTH_HFP"
            am.isWiredHeadsetOn -> "WIRED_HEADSET"
            am.isSpeakerphoneOn -> "SPEAKER"
            else -> "SPEAKER" // default output
        }
        name = type // no device name available pre-31
    }
    return mapOf("type" to type, "name" to name)
}

private fun mapDeviceType(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_HFP"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "SPEAKER"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "EARPIECE"
    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
    AudioDeviceInfo.TYPE_DOCK -> "LINE_OUT"
    else -> {
        // TYPE_BLE_HEADSET is API 31+ — handle dynamically
        if (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET) "BLUETOOTH_LE"
        else "UNKNOWN"
    }
}
```

### iOS
```swift
private func getAudioRoute() -> [String: String] {
    guard let output = AVAudioSession.sharedInstance().currentRoute.outputs.first else {
        return ["type": "UNKNOWN", "name": "Unknown"]
    }
    let type: String
    switch output.portType {
    case .bluetoothA2DP:    type = "BLUETOOTH_A2DP"
    case .bluetoothHFP:     type = "BLUETOOTH_HFP"
    case .bluetoothLE:      type = "BLUETOOTH_LE"
    case .headphones:       type = "WIRED_HEADPHONES"
    case .builtInSpeaker:   type = "SPEAKER"
    case .builtInReceiver:  type = "EARPIECE"
    case .carAudio:         type = "BLUETOOTH_A2DP"  // normalize to shared enum
    case .airPlay:          type = "AIRPLAY"
    case .usbAudio:         type = "USB"
    case .lineOut:          type = "LINE_OUT"
    default:                type = "UNKNOWN"
    }
    return ["type": type, "name": output.portName]
}
```

---

## Diagnostic Events

All emitted via the existing `DIAGNOSTIC` event type with `{ tag, data }` payload.

### 1. `AUDIO_ROUTE_CHANGED`

**Trigger:** Audio output route changes (headphones plugged/unplugged, Bluetooth connect/disconnect, etc.)

**Payload:**
```ts
{
  route: { type: string, name: string },
  reason: string,       // "NEW_DEVICE" | "DEVICE_REMOVED" | "CATEGORY_CHANGE" | "OVERRIDE" | "UNKNOWN"
  previousRoute?: { type: string, name: string }  // iOS only — Android omits this field
}
```

**Android implementation:**
- Register `AudioDeviceCallback` on `AudioManager` in `onCreate()`, unregister in `onDestroy()`
- Filter: only emit when output device types change (ignore input-only device changes like USB mic)
- Emit current route snapshot via `getAudioRoute()`

**iOS implementation:**
- Add observer for `AVAudioSession.routeChangeNotification` — register ONCE in `init()` or first `play()` with a guard flag (`isRouteObserverRegistered`), not per-track
- Read `AVAudioSessionRouteChangeReasonKey` from userInfo, map to reason string
- Read `AVAudioSessionRouteChangePreviousRouteKey`, extract `.outputs.first` and run through `getAudioRoute()` mapping for `previousRoute`

**Cost:** Cheap — one listener, fires ~1-10x per session.

---

### 2. `PLAY_INTENT`

**Trigger:** Any play/resume command arrives at the native player, regardless of source. Emitted for BOTH allowed and blocked plays.

Replaces/unifies: Android `SESSION_COMMAND` + iOS `PLAY_COMMAND` / `TOGGLE_PLAY_PAUSE`

**Payload:**
```ts
{
  source: string,       // "APP" | "REMOTE" | "UNKNOWN"
  action: string,       // "ALLOWED" | "BLOCKED"
  blockReason?: string, // "TRACK_ENDED" (only when blocked)
  playerState: string,  // "IDLE" | "BUFFERING" | "READY" | "ENDED"
  route: { type: string, name: string },
  callerPackage?: string  // Android only, when controllerForCurrentRequest is valid
}
```

**Android implementation:**
- Emit from `GuardedPlayer.handleSetPlayWhenReady()` for ALL play commands (`playWhenReady=true`)
- BLOCKED path: when `player.playbackState == STATE_ENDED` — action "BLOCKED", blockReason "TRACK_ENDED"
- ALLOWED path: add emission BEFORE `return super.handleSetPlayWhenReady(playWhenReady)` — action "ALLOWED"
- Source: `controllerForCurrentRequest?.packageName` → if null → "UNKNOWN", if matches own package → "APP", else → "REMOTE"

**iOS implementation:**
- Emit from `playCommand` handler and `togglePlayPauseCommand` handler — source always "REMOTE"
- Action: "BLOCKED" if `hasTrackEnded`, "ALLOWED" otherwise
- Also emit from `play()` method (JS-initiated) with source "APP", action "ALLOWED" — so all play commands are visible in logs

**Cost:** Free — piggyback on existing command handlers.

---

### 3. `PLAYBACK_STATE_CHANGE`

**Trigger:** Native player state transitions OR playWhenReady changes.

Replaces/unifies: Android `SERVICE_STATE` + `PLAY_WHEN_READY` (merged into one event). iOS has no equivalent currently.

**Payload:**
```ts
{
  state: string,           // "IDLE" | "BUFFERING" | "READY" | "ENDED"
  playWhenReady: boolean,
  reason?: string,         // When playWhenReady changes: "USER_REQUEST" | "AUDIO_FOCUS_LOSS" | "BECOMING_NOISY" | "REMOTE" | "END_OF_MEDIA_ITEM" | "INTERRUPTION" | "UNKNOWN"
  positionMs: number,      // sanitized: if < 0 then 0
  durationMs: number,      // sanitized: if < 0 (C.TIME_UNSET) then 0
  route: { type: string, name: string }
}
```

**Android implementation:**
- Emit from `playbackListener.onPlaybackStateChanged()` — replaces `SERVICE_STATE` tag
- Emit from `playbackListener.onPlayWhenReadyChanged()` — replaces `PLAY_WHEN_READY` tag. Include `reason` field mapped from the reason int.
- Sanitize: `if (duration == C.TIME_UNSET || duration < 0) 0L else duration`

**iOS implementation:**
- Emit from `playerItemDidPlayToEndTime` with state "ENDED"
- Emit from `play()` / `pause()` / `resume()` / `stop()` / `clear()` methods — since the `timeControlStatus` KVO observer is gated by `shouldBePlaying` and misses pause/stop transitions
- Emit from interruption handlers (began → state with playWhenReady=false, ended+resume → playWhenReady=true)
- Best-effort `reason` mapping:
  - `play()` from JS → "USER_REQUEST"
  - `pause()` from JS → "USER_REQUEST"
  - `playCommand`/`pauseCommand` handlers → "REMOTE"
  - Interruption began → "INTERRUPTION"
  - Interruption ended + resume → "INTERRUPTION"
  - `playerItemDidPlayToEndTime` → "END_OF_MEDIA_ITEM"
  - Route change `.oldDeviceUnavailable` → "BECOMING_NOISY"

**Note on iOS `BECOMING_NOISY`:** AVPlayer does NOT auto-pause on headphone removal (unlike ExoPlayer). If we want parity, we should add explicit pause behavior in the route change handler when reason is `.oldDeviceUnavailable` and audio is currently playing. Gate this behind an `isInterrupted` flag to avoid double-emission when a phone call also triggers route change.

**Cost:** Free — piggyback on existing handlers.

---

### 4. `INTERRUPTION`

**Trigger:** Audio interruption (phone call, Siri, other app, alarm, etc.)

iOS already emits partial version as `INTERRUPTION_ENDED`. Android gets this via focus reasons.

**Payload:**
```ts
{
  type: string,           // "BEGAN" | "ENDED"
  wasPlaying: boolean,
  shouldResume?: boolean,  // only on ENDED
  willResume?: boolean,    // only on ENDED — did we actually resume?
  hasTrackEnded: boolean,
  route: { type: string, name: string }
}
```

**Android implementation:**
- Emit `INTERRUPTION` BEGAN from `onPlayWhenReadyChanged` when reason is `AUDIO_FOCUS_LOSS`. Set `wasPlayingBeforeFocusLoss = previousPlayWhenReady`.
- For ENDED: Only emit when `wasPlayingBeforeFocusLoss` is true AND playWhenReady transitions back to true with reason `USER_REQUEST` or `REMOTE`. Reset `wasPlayingBeforeFocusLoss` after emitting.
- Do NOT attempt to emit ENDED for permanent focus loss (no reliable signal). An orphaned BEGAN is acceptable — it means focus was never regained.

**iOS implementation:**
- Already listening to `AVAudioSession.interruptionNotification`
- On `.began`: emit with type "BEGAN" (NEW — currently only ENDED is emitted)
- On `.ended`: rename tag from `INTERRUPTION_ENDED` → `INTERRUPTION`, add type "ENDED"
- Add route to both

**Cost:** Android: low (add boolean + emit in existing callback). iOS: free (extend existing handler).

---

### 5. `TRACK_DID_END`

**Trigger:** Track reaches natural end — before dedup, before JS handling.

iOS already emits this. Android doesn't.

**Payload:**
```ts
{
  positionMs: number,    // sanitized
  durationMs: number,    // sanitized
  route: { type: string, name: string }
}
```

**Android implementation:**
- Emit from `playbackListener.onPlaybackStateChanged()` when `STATE_ENDED`, BEFORE the `emitTrackEndedOnce` call
- Not deduped — fires every time state changes to ENDED (intentional for diagnostics)

**iOS implementation:**
- Already emitted in `playerItemDidPlayToEndTime` — extend payload with positionMs, durationMs, route

**Cost:** Free on both.

---

### 6. `TRACK_LOADED`

**Trigger:** New media item set on player. Confirms what track native is actually loading.

Neither platform emits this currently.

**Payload:**
```ts
{
  mediaId?: string,      // Android: MediaItem.mediaId, iOS: URL string
  url?: string,          // The URL being loaded (truncated for privacy if needed)
  autoPlay: boolean,
  route: { type: string, name: string }
}
```

**Android implementation:**
- Emit from the `runOnUiThread` block in `AudioProController.play()`, right after `setMediaItem()` call
- `mediaId` from `mediaItem.mediaId`, `url` from the URI

**iOS implementation:**
- Emit from `play()` method after `player?.replaceCurrentItem(with: item)` or `AVPlayer(playerItem: item)`
- `url` from the track URL

**Cost:** Free — one emit per track load.

---

## Events NOT being added (and why)

| Idea | Why not |
|------|---------|
| Periodic audio route polling | Route change listener covers this; polling wastes cycles |
| Bluetooth device MAC/address | Requires BLUETOOTH permission on Android; not available on iOS |
| Lock screen vs notification vs Control Center distinction | Neither OS exposes this |
| CarPlay-specific detection | Requires app-level scene wiring, not audio player concern |
| Audio ducking events | ExoPlayer pauses on focus loss (doesn't duck); iOS ducking is category-level |
| `mediaServicesWereLost/Reset` (iOS) | Extremely rare (~0 per session); not worth the code |
| `silenceSecondaryAudioHint` (iOS) | Redundant with INTERRUPTION for our purposes |
| Per-seek diagnostics | Seeks already emit SEEK_COMPLETE; diagnostics would be noise |
| PROGRESS-level route info | Too frequent (every 1s); route changes are rare, use the listener |
| Separate `PLAY_WHEN_READY_CHANGE` event | Merged into `PLAYBACK_STATE_CHANGE` with `reason` field — avoids redundancy |

---

## Summary: What changes where

### Android (`AudioProPlaybackService.kt`)
- Add `getAudioRoute()` and `mapDeviceType()` helpers
- Add `AudioDeviceCallback` registration (filtered to output devices) for `AUDIO_ROUTE_CHANGED`
- Add `wasPlayingBeforeFocusLoss` boolean for `INTERRUPTION` tracking
- Add `diagnosticSeq` counter for monotonic sequence numbers
- Rename `SERVICE_STATE` → `PLAYBACK_STATE_CHANGE`, `PLAY_WHEN_READY` → merged into `PLAYBACK_STATE_CHANGE`
- Add route + envelope fields to all diagnostic payloads
- Add `TRACK_DID_END` emission in `onPlaybackStateChanged(STATE_ENDED)`
- Emit `PLAY_INTENT` for ALL play commands in `GuardedPlayer.handleSetPlayWhenReady()` (both ALLOWED and BLOCKED)
- Sanitize duration values (`C.TIME_UNSET` → 0)

### Android (`AudioProController.kt`)
- Add `TRACK_LOADED` emission in `play()` after `setMediaItem()`
- Add `trackId` field (mediaId or URL hash) to `emitDiagnostic()` envelope

### iOS (`AudioPro.swift`)
- Add `getAudioRoute()` helper
- Add `AVAudioSession.routeChangeNotification` observer — register ONCE with `isRouteObserverRegistered` guard
- Add `diagnosticSeq` counter
- Add `PLAYBACK_STATE_CHANGE` emissions from play/pause/stop/clear/interruption/end handlers
- Rename `INTERRUPTION_ENDED` → `INTERRUPTION` with type field, add `INTERRUPTION` BEGAN emission
- Extend `TRACK_DID_END` payload with position, duration, route
- Add `PLAY_INTENT` emission in playCommand, togglePlayPauseCommand, and play() handlers
- Add `TRACK_LOADED` emission in play() after replaceCurrentItem
- Add explicit pause on headphone removal (route change `.oldDeviceUnavailable` while playing), gated by `isInterrupted` flag
- Add route + envelope fields to all diagnostic payloads

### TypeScript (`src/types.ts`)
- No changes needed — diagnostic payload is already `{ tag?: string; data?: Record<string, unknown> }`

### JS (`AudioPlayer.ts`)
- No changes needed — already logs `[native] ${tag}: ${data}` for all DIAGNOSTIC events

---

## Expected log output (illustrative — exact ordering may vary between platforms)

### Normal track play → end → next track (Bluetooth)
```
[native] AUDIO_ROUTE_CHANGED: { route: { type: "BLUETOOTH_A2DP", name: "AirPods Pro" }, reason: "NEW_DEVICE", seq: 1 }
[native] TRACK_LOADED: { url: "https://cdn.example.com/track-123.m4a", autoPlay: true, seq: 2 }
[native] PLAY_INTENT: { source: "APP", action: "ALLOWED", playerState: "BUFFERING", seq: 3 }
[native] PLAYBACK_STATE_CHANGE: { state: "BUFFERING", playWhenReady: true, positionMs: 0, durationMs: 0, seq: 4 }
[native] PLAYBACK_STATE_CHANGE: { state: "READY", playWhenReady: true, positionMs: 0, durationMs: 180000, seq: 5 }
... (playback, PROGRESS events) ...
[native] PLAYBACK_STATE_CHANGE: { state: "ENDED", playWhenReady: false, reason: "END_OF_MEDIA_ITEM", positionMs: 180000, durationMs: 180000, seq: 42 }
[native] TRACK_DID_END: { positionMs: 180000, durationMs: 180000, seq: 43 }
AudioPro event: TRACK_ENDED
Track ended, advancing to next
[native] TRACK_LOADED: { url: "https://cdn.example.com/track-456.m4a", autoPlay: true, seq: 44 }
```

### Songs repeating bug (if it recurred — BLOCKED by GuardedPlayer)
```
[native] TRACK_DID_END: { positionMs: 180000, durationMs: 180000, route: { type: "BLUETOOTH_A2DP" }, seq: 43 }
AudioPro event: TRACK_ENDED
Track ended, advancing to next
[native] PLAY_INTENT: { source: "REMOTE", action: "BLOCKED", blockReason: "TRACK_ENDED", playerState: "ENDED", route: { type: "BLUETOOTH_A2DP" }, callerPackage: "com.android.systemui", seq: 44 }
```

### Phone call interruption
```
[native] INTERRUPTION: { type: "BEGAN", wasPlaying: true, hasTrackEnded: false, route: { type: "SPEAKER" }, seq: 20 }
[native] PLAYBACK_STATE_CHANGE: { state: "READY", playWhenReady: false, reason: "INTERRUPTION", seq: 21 }
... (call ends) ...
[native] INTERRUPTION: { type: "ENDED", wasPlaying: true, shouldResume: true, willResume: true, hasTrackEnded: false, route: { type: "SPEAKER" }, seq: 22 }
[native] PLAYBACK_STATE_CHANGE: { state: "READY", playWhenReady: true, reason: "INTERRUPTION", seq: 23 }
```

### Headphones unplugged
```
[native] AUDIO_ROUTE_CHANGED: { route: { type: "SPEAKER", name: "Built-In Speaker" }, reason: "DEVICE_REMOVED", previousRoute: { type: "WIRED_HEADPHONES", name: "Headphones" }, seq: 15 }
[native] PLAYBACK_STATE_CHANGE: { state: "READY", playWhenReady: false, reason: "BECOMING_NOISY", seq: 16 }
```
