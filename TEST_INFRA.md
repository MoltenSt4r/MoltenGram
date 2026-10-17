# E2E Test Infra: MoltenGram Audio Modernization

## Test Philosophy
- Requirement-driven and opaque-box verification.
- Direct alignment with verification requirements in `ORIGINAL_REQUEST.md`:
  1. Automated test verifying playlist persistence across simulated account switch / account reset.
  2. Automated test verifying URL interceptor filter matching `music.youtube.com` and `metrolist.cc` while strictly ignoring `youtube.com` / `youtu.be`.
  3. Compilation verification with `./gradlew compileStagingJavaWithJavac compileStagingKotlin --no-daemon`.

---

## Feature Inventory & Test Matrix
| # | Feature | Requirement | Tier 1 (Unit / Direct) | Tier 2 (Boundary & Corner) | Tier 3 (Cross-Feature) | Tier 4 (Workload Scenario) |
|---|---------|-------------|:----------------------:|:--------------------------:|:----------------------:|:--------------------------:|
| F1 | Player UI Header | R1 | Title / subtitle update | Empty / null playlist name | Account switch mid-play | Playback from local list |
| F2 | GlobalPlaylistsStore | R1 | CRUD & persistence | Corrupted JSON / empty file | Account switch/reset | Multi-account playlist use |
| F3 | Settings Menu Entry | R1 | Row present in settings | Rapid navigation | Add/delete from settings | Open playlist from menu |
| F4 | Metrolist InnerTube (Guest) | R2 | Search & stream parse | Malformed response / 404 | Feed to ExoPlayer | Search & stream playback |
| F5 | Cookie & SAPISID Auth | R2 | Netscape parsing & hash | Expired cookie / invalid format | Switch Guest -> Cookie | Private music stream |
| F6 | Google OAuth WebView | R2 | Cookie capture callback | Interrupted webview / redirect | Authenticated query | Complete login flow |
| F7 | ExoPlayer Streaming & Cache | R2 | HTTP source & 256MB cache | Network drop / cache evict | Enqueue remote tracks | Stream album offline |
| F8 | Strict URL Interceptor | R2 | Filter whitelist vs blacklist | Malicious subdomains / paths | Intercept & play in player | Chat link tap to play |
| F9 | 1-Click Audio Actions | R2 | Saved Msgs / Profile / Playlist | External track without MTProto doc | Save external to playlist | 1-click add & verify in list |
| F10 | Multi-Segment SongRec | R3 | 3-window cascade (1, 2, 3) | Silent intro / low SNR (*Afterglow*) | Recognition -> Cover query | Ambiguous track recognition |
| F11 | YT Catalog Metadata Match | R3 | Title + duration ±15s filter | Multiple covers / live versions | Stream track -> fetch canvas | Match official music video |
| F12 | MD3 Shaped Canvas Playback | R3 | TextureView loop & shape clip | Null video / format error | Change MD3 shape live | Smooth video cover display |
| F13 | Build & Unit Compilation | Guardrails | `./gradlew :TMessagesProj:testDebugUnitTest` | Dry run vs Full build | Canary commit check | Full CI staging compile |

---

## Test Architecture
- **Unit Test Runner**:
  - Gradle Task: `./gradlew :TMessagesProj:testDebugUnitTest --no-daemon`
  - Location: `TMessagesProj/src/test/java/app/exteraless/player/`
  - Key Test Suites:
    1. `GlobalPlaylistsStoreTest.java`:
       - `testSaveAndReloadPlaylists`: Verifies serialization, file I/O, reload integrity.
       - `testAccountSwitchPersistence`: Verifies playlists persist when switching accounts.
       - `testAccountResetAndLogoutPersistence`: Verifies data survives account cleanup and logout.
       - `testExternalTracksStorage`: Verifies YouTube Music & Metrolist tracks with remote stream URLs and artwork URLs persist correctly.
    2. `MusicUrlInterceptorTest.java`:
       - `testAcceptsMusicYouTube`: Verifies `https://music.youtube.com/watch?v=...` is intercepted.
       - `testAcceptsMetrolist`: Verifies `https://metrolist.cc/track/...` and `https://metrolist.cc/watch?v=...` are intercepted.
       - `testRejectsYouTubeCom`: Verifies `https://www.youtube.com/...` and `https://youtube.com/...` are rejected.
       - `testRejectsYouTuBe`: Verifies `https://youtu.be/...` is rejected.
       - `testRejectsSubdomainAttacks`: Verifies `https://music.youtube.com.attacker.com/...` is rejected.
    3. `YtAuthHelperTest.java`:
       - Netscape `cookies.txt` parser tests.
       - SAPISIDHASH computation tests against RFC vectors.
- **Compilation Verification**:
  - Command: `./gradlew compileStagingJavaWithJavac compileStagingKotlin --no-daemon`
  - Verifies zero compilation errors in Java and Kotlin code under `staging` configuration.

---

## Real-World Application Scenarios (Tier 4)
1. **Scenario 1 (Local Playlist across Account Logout)**:
   User creates a local playlist "Gym Beats" while logged into Account A. User logs out of Account A completely (`performLogout`). User opens player settings or logs into Account B: "Gym Beats" remains intact, fully editable, and playable.
2. **Scenario 2 (Metrolist / YouTube Music Chat Link Interception)**:
   A friend sends `https://music.youtube.com/watch?v=abc123xyz` and `https://youtube.com/watch?v=def456uvw` in a chat. User taps the YouTube Music link: it does NOT open an external browser; it resolves the audio stream, launches the native player, and displays "Из плейлиста" / metadata. User taps the standard YouTube link: it opens the system browser / YouTube app as normal.
3. **Scenario 3 (Difficult Track Video Cover Discovery)**:
   User plays Ed Sheeran's *Afterglow* (quiet acoustic guitar intro). The multi-segment acoustic fingerprinting passes through Segment 1 (low confidence) into Segment 2 (Chorus), recognizes the track, matches the official YouTube video catalog (duration match ±15s), and displays the official looping Canvas inside the active MD3 cover shape.
