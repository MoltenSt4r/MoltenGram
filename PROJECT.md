# Project: MoltenGram Audio Modernization

## Architecture
MoltenGram's audio modernization introduces a clean, modular layer across the UI, storage, streaming engine, and recognition services:
1. **Application-Level Storage (`app.exteraless.player.GlobalPlaylistsStore`)**:
   - Resides in `ApplicationLoader.applicationContext.getFilesDir()/extera_local_playlists.json`.
   - Independent of `currentAccount` and survives account switch, logout, and deletion.
   - Provides thread-safe CRUD, atomic disk writes (`.tmp` write and rename), and tracks active local playlist.
2. **Audio UI Layer (`app.exteraless.player.PlayerSheet`, `AudioPlayerAlert`, `PlayerSettingsActivity`)**:
   - Header awareness: Checks `GlobalPlaylistsStore.getInstance().getPlayingPlaylist()`. If set, displays "Из плейлиста" (`R.string.OEPlayerFromPlaylist`) and playlist name.
   - Settings integration: "Локальные плейлисты" in `PlayerSettingsActivity` routes to playlist management dialog.
3. **Metrolist / InnerTube Audio Client (`app.exteraless.player.ytmusic`)**:
   - Pure Kotlin/JVM modular client communicating with `https://music.youtube.com/youtubei/v1/`.
   - 3 Auth Modes: Guest mode (visitor token), Cookie auth (Netscape `cookies.txt` / raw cookies with SAPISIDHASH computation), and Google OAuth via `WebView`.
   - ExoPlayer streaming integration via Media3 `CacheDataSource` + `SimpleCache` (256MB LRU) and queue management via synthetic `MessageObject` instances.
4. **Strict URL Interceptor (`app.exteraless.player.ytmusic.MetrolistUrlInterceptor`)**:
   - Hooked into `Browser.openUrl` and `ChatActivity`.
   - Strict filter whitelist: `music.youtube.com`, `metrolist.cc`, `www.metrolist.cc`.
   - Strict filter blacklist: Rejects `youtube.com`, `www.youtube.com`, `m.youtube.com`, `youtu.be`.
   - Resolves stream asynchronously and starts playback in native player.
5. **1-Click Actions (`app.exteraless.player.PlayerActions`)**:
   - 1-click Save to Saved Messages (`UserConfig.getClientUserId()`).
   - 1-click Pin to Profile (`TLRPC.TL_account_saveMusic`).
   - 1-click Save to Local Playlist (`LocalPlaylistsController.showAddToPlaylistDialog`).
6. **Acoustic Fingerprinting & Canvas Video Covers (`app.exteraless.player.videocover`)**:
   - Multi-segment acoustic fingerprinting fallback cascade: Intro (10-22s) -> Chorus (~35-45% duration) -> Bridge (~60-70% duration) for complex tracks like *Afterglow*.
   - YouTube video catalog metadata matching (duration ±15s, title similarity score).
   - Looping Canvas video cover playback in `PlayerSheet` masked by active Material 3 avatar shape.

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| F1 | Player UI Header "Из плейлиста" | Header label "Из плейлиста" and playlist title in PlayerSheet and AudioPlayerAlert | M1 | ORIGINAL_REQUEST §R1 |
| F2 | GlobalPlaylistsStore Account Decoupling | App-level storage in filesDir surviving account logout/switch/reset | M1 | ORIGINAL_REQUEST §R1 |
| F3 | Settings Menu Entry "Локальные плейлисты" | Entry in PlayerSettingsActivity under Audio section opening playlist manager | M1 | ORIGINAL_REQUEST §R1 |
| F4 | Metrolist InnerTube Client (Guest Mode) | Modular client for music.youtube.com querying search & playback without auth | M2 | ORIGINAL_REQUEST §R2 |
| F5 | Cookie & SAPISID Authentication | Netscape cookies.txt & raw string parsing + SAPISIDHASH header generation | M2 | ORIGINAL_REQUEST §R2 |
| F6 | Google OAuth via WebView | Interactive login form capturing music.youtube.com session cookies | M2 | ORIGINAL_REQUEST §R2 |
| F7 | ExoPlayer Streaming, Caching & Queues | Remote audio playback with 256MB SimpleCache and synthetic MessageObject queues | M2 | ORIGINAL_REQUEST §R2 |
| F8 | Strict URL Interceptor | Strictly intercept music.youtube.com and metrolist.cc; strictly ignore youtube.com & youtu.be | M2 | ORIGINAL_REQUEST §R2 |
| F9 | 1-Click Audio Actions | 1-click save to Saved Messages, pin to profile, save to local playlist | M2 | ORIGINAL_REQUEST §R2 |
| F10 | Multi-Segment SongRec Fingerprinting | Intro -> Chorus -> Bridge acoustic fingerprinting cascade for difficult tracks | M3 | ORIGINAL_REQUEST §R3 |
| F11 | YouTube Video Catalog Metadata Matching | Duration ±15s and metadata similarity scoring to find official clips / Canvases | M3 | ORIGINAL_REQUEST §R3 |
| F12 | MD3 Shaped Canvas Playback | Looping video cover in PlayerSheet masked to current MD3 avatar shape | M3 | ORIGINAL_REQUEST §R3 |
| F13 | Automated Unit Testing & Build Verification | Persistence tests across account switch/reset, URL filtering tests, compileStaging verification | Final | ORIGINAL_REQUEST §Verification |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | UI & GlobalPlaylistsStore Decoupling | F1, F2, F3 | None | IN_PROGRESS |
| M2 | Metrolist / YT Music Integration & URL Interceptor | F4, F5, F6, F7, F8, F9 | M1 | PLANNED |
| M3 | Video Cover Accuracy & Multi-Segment SongRec | F10, F11, F12 | M1 | PLANNED |
| Final | E2E Testing, Hardening & Final Gate | F13, full regression tests, audit, compilation | M1, M2, M3 | PLANNED |

---

## Interface Contracts

### 1. `GlobalPlaylistsStore` ↔ `Player UI` (`PlayerSheet` / `AudioPlayerAlert`)
```java
public class GlobalPlaylistsStore {
    public static GlobalPlaylistsStore getInstance();
    public LocalPlaylistsController.LocalPlaylist getPlayingPlaylist();
    public void setPlayingPlaylist(LocalPlaylistsController.LocalPlaylist playlist);
    public List<LocalPlaylistsController.LocalPlaylist> getPlaylists();
    public LocalPlaylistsController.LocalPlaylist createPlaylist(String name);
    public boolean addTrack(String playlistId, LocalPlaylistsController.PlaylistItem track);
    public void removeTrack(String playlistId, int index);
    public void deletePlaylist(String playlistId);
}
```

### 2. `MetrolistUrlInterceptor` ↔ `Browser.openUrl` & `ChatActivity`
```java
public class MetrolistUrlInterceptor {
    public static boolean shouldIntercept(Uri uri);
    public static boolean handleUrl(Context context, Uri uri);
}
```
Contract:
- `shouldIntercept`: Returns `true` if and only if host is `music.youtube.com`, `metrolist.cc`, or `www.metrolist.cc`. Returns `false` for `youtube.com`, `youtu.be`, and any other domains.
- `handleUrl`: Returns `true` if successfully scheduled for native playback, preventing browser launch.

### 3. `InnerTubeClient` ↔ `MediaController`
```kotlin
data class YtTrack(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Int,
    val streamUrl: String?,
    val artworkUrl: String?
)

class InnerTubeClient {
    suspend fun search(query: String): List<YtTrack>
    suspend fun getStreamUrl(videoId: String): String
    fun toMessageObject(track: YtTrack, account: Int): MessageObject
}
```

### 4. `AcousticFingerprintEngine` ↔ `VideoCoverManager`
```kotlin
object AcousticFingerprintEngine {
    fun recognizeMultiSegment(audioFile: File, totalDurationSeconds: Int): RecognizedTrack?
}

object YouTubeVideoCatalogMatcher {
    fun findOfficialVideoOrCanvas(artist: String, title: String, durationSeconds: Int): VideoCoverResult?
}
```

---

## Code Layout
- `TMessagesProj/src/main/java/app/exteraless/player/GlobalPlaylistsStore.java`: Global app-level playlist repository.
- `TMessagesProj/src/main/java/app/exteraless/player/LocalPlaylistsController.java`: High-level controller & UI dialogs.
- `TMessagesProj/src/main/java/app/exteraless/player/PlayerSheet.java`: Bottom sheet audio player UI.
- `TMessagesProj/src/main/java/org/telegram/ui/Components/AudioPlayerAlert.java`: Native audio player alert UI.
- `TMessagesProj/src/main/java/app/exteraless/player/PlayerSettingsActivity.java`: Player settings activity.
- `TMessagesProj/src/main/java/app/exteraless/player/ytmusic/`: Metrolist / InnerTube client, auth, URL interceptor.
- `TMessagesProj/src/main/java/app/exteraless/player/videocover/`: Multi-segment acoustic fingerprinting, YT catalog matching, Canvas playback.
- `TMessagesProj/src/test/java/app/exteraless/player/`: Unit test suite (persistence, URL interception, auth).
- `TMessagesProj/src/main/res/values/strings_oe_player.xml`: Default English string resources.
- `TMessagesProj/src/main/res/values-ru-rRU/strings_oe_player.xml`: Russian string resources.
