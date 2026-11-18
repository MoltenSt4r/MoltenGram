import os
import re
import pytest

REPO_ROOT = "/root/ExteraMS_repo"
JAVA_PLAYER_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/app/exteraless/player")
KOTLIN_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/kotlin/app/exteraless")
MESSENGER_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/messenger")
RES_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/res")


def test_appearance_config_songrec_settings():
    """Verify AppearanceConfig contains songrecFetchCover, lyricsSongrecCover, and songrecBackgroundRecognition."""
    path = os.path.join(KOTLIN_DIR, "appearance/AppearanceConfig.kt")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # songrecFetchCover ConfigItem and getter
    assert 'val songrecFetchCover =' in content or 'val songrecFetchCover=' in content, (
        "AppearanceConfig must define 'songrecFetchCover' ConfigItem"
    )
    assert '"OEAppearanceSongrecFetchCover"' in content, (
        "songrecFetchCover must use key OEAppearanceSongrecFetchCover"
    )
    assert re.search(r'fun\s+songrecFetchCover\s*\(\s*\)\s*:\s*Boolean', content), (
        "AppearanceConfig must provide 'fun songrecFetchCover(): Boolean'"
    )

    # lyricsSongrecCover alias
    assert 'val lyricsSongrecCover' in content, (
        "AppearanceConfig must define lyricsSongrecCover alias"
    )
    assert re.search(r'fun\s+lyricsSongrecCover\s*\(\s*\)\s*:\s*Boolean', content), (
        "AppearanceConfig must provide 'fun lyricsSongrecCover(): Boolean'"
    )

    # songrecBackgroundRecognition ConfigItem and getter
    assert 'val songrecBackgroundRecognition =' in content or 'val songrecBackgroundRecognition=' in content, (
        "AppearanceConfig must define 'songrecBackgroundRecognition' ConfigItem"
    )
    assert '"OEAppearanceSongrecBackgroundRecognition"' in content, (
        "songrecBackgroundRecognition must use key OEAppearanceSongrecBackgroundRecognition"
    )
    assert re.search(r'fun\s+songrecBackgroundRecognition\s*\(\s*\)\s*:\s*Boolean', content), (
        "AppearanceConfig must provide 'fun songrecBackgroundRecognition(): Boolean'"
    )


def test_player_settings_activity_songrec_toggles():
    """Verify PlayerSettingsActivity includes toggles for SongRec cover art and background recognition."""
    path = os.path.join(JAVA_PLAYER_DIR, "PlayerSettingsActivity.java")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Row declarations
    assert "songrecFetchCoverRow" in content, (
        "PlayerSettingsActivity must define songrecFetchCoverRow"
    )
    assert "songrecBackgroundRecognitionRow" in content, (
        "PlayerSettingsActivity must define songrecBackgroundRecognitionRow"
    )

    # Row initialization
    assert 'songrecFetchCoverRow = addRow("songrecFetchCover");' in content, (
        "PlayerSettingsActivity must add songrecFetchCover row"
    )
    assert 'songrecBackgroundRecognitionRow = addRow("songrecBackgroundRecognition");' in content, (
        "PlayerSettingsActivity must add songrecBackgroundRecognition row"
    )

    # Toggle handling in onClick / position matching
    assert "AppearanceConfig.songrecFetchCover()" in content, (
        "PlayerSettingsActivity must check AppearanceConfig.songrecFetchCover() on toggle"
    )
    assert "AppearanceConfig.songrecFetchCover.setConfigBool" in content, (
        "PlayerSettingsActivity must update AppearanceConfig.songrecFetchCover config"
    )
    assert "AppearanceConfig.songrecBackgroundRecognition()" in content, (
        "PlayerSettingsActivity must check AppearanceConfig.songrecBackgroundRecognition() on toggle"
    )
    assert "AppearanceConfig.songrecBackgroundRecognition.setConfigBool" in content, (
        "PlayerSettingsActivity must update AppearanceConfig.songrecBackgroundRecognition config"
    )

    # View binding with strings
    assert "R.string.OEAppearanceSongrecFetchCover" in content, (
        "PlayerSettingsActivity must bind R.string.OEAppearanceSongrecFetchCover"
    )
    assert "R.string.OEAppearanceSongrecFetchCoverDesc" in content, (
        "PlayerSettingsActivity must bind R.string.OEAppearanceSongrecFetchCoverDesc"
    )
    assert "R.string.OEAppearanceSongrecBackgroundRecognition" in content, (
        "PlayerSettingsActivity must bind R.string.OEAppearanceSongrecBackgroundRecognition"
    )
    assert "R.string.OEAppearanceSongrecBackgroundRecognitionDesc" in content, (
        "PlayerSettingsActivity must bind R.string.OEAppearanceSongrecBackgroundRecognitionDesc"
    )


def test_localization_strings_songrec():
    """Verify localization resources in English and Russian for SongRec settings."""
    files_to_check = [
        (os.path.join(RES_DIR, "values/strings_nax.xml"), "Fetch album art", "Background song recognition"),
        (os.path.join(RES_DIR, "values-ru-rRU/strings_nax.xml"), "Подставлять обложки", "Фоновое распознавание треков"),
        (os.path.join(RES_DIR, "values/strings_oe_player.xml"), "Fetch album art", "Background song recognition"),
        (os.path.join(RES_DIR, "values-ru-rRU/strings_oe_player.xml"), "Подставлять обложки", "Фоновое распознавание треков"),
    ]

    for path, cover_text, bg_text in files_to_check:
        assert os.path.isfile(path), f"File not found: {path}"
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()

        assert 'name="OEAppearanceSongrecFetchCover"' in content, (
            f"Missing OEAppearanceSongrecFetchCover in {path}"
        )
        assert cover_text in content, (
            f"Expected '{cover_text}' in {path}"
        )
        assert 'name="OEAppearanceSongrecBackgroundRecognition"' in content, (
            f"Missing OEAppearanceSongrecBackgroundRecognition in {path}"
        )
        assert bg_text in content, (
            f"Expected '{bg_text}' in {path}"
        )


def test_song_recognizer_speed_optimizations_and_sample_window():
    """Verify SongRecognizer decodes 5-8s window, breaks early, pools generator, and uses optimized HTTP timeouts."""
    path = os.path.join(JAVA_PLAYER_DIR, "SongRecognizer.java")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Sample window duration check: 5-8 seconds
    match = re.search(r'SEGMENT_SECONDS\s*=\s*(\d+)', content)
    assert match, "SEGMENT_SECONDS constant must be defined in SongRecognizer"
    segment_seconds = int(match.group(1))
    assert 5 <= segment_seconds <= 8, f"SEGMENT_SECONDS must be between 5 and 8, found {segment_seconds}"

    # Early break in decoding loop
    assert "mono.size >= needSrc" in content, (
        "SongRecognizer must break early once required audio samples (needSrc) are decoded"
    )

    # ThreadLocal generator cache / pooling to avoid GC pressure
    assert "GENERATOR_CACHE" in content or "ThreadLocal" in content, (
        "SongRecognizer must reuse Generator instances via ThreadLocal or pooling"
    )

    # Optimized network connection timeouts: <= 5000ms connect, <= 6000ms read
    conn_timeout_match = re.search(r'setConnectTimeout\s*\(\s*([\d_]+)\s*\)', content)
    assert conn_timeout_match, "setConnectTimeout must be set on HttpURLConnection"
    conn_timeout = int(conn_timeout_match.group(1).replace('_', ''))
    assert conn_timeout <= 5000, f"Connect timeout should be <= 5000ms, got {conn_timeout}"

    # GZIP and Keep-Alive headers
    assert "Accept-Encoding" in content and "gzip" in content, (
        "SongRecognizer must accept gzip encoding to speed up network transfer"
    )
    assert "Keep-Alive" in content, (
        "SongRecognizer must specify Keep-Alive for HTTP connection reuse"
    )


def test_song_recognizer_cover_extraction_and_caching():
    """Verify SongRecognizer extracts cover URLs from images and supports in-memory & disk caching."""
    path = os.path.join(JAVA_PLAYER_DIR, "SongRecognizer.java")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Result structure contains coverUrl and localCoverPath
    assert "public final String coverUrl;" in content or "String coverUrl" in content, (
        "Result must have coverUrl field"
    )
    assert "localCoverPath" in content, (
        "Result must have localCoverPath field"
    )
    assert "getEffectiveCover()" in content, (
        "Result must expose getEffectiveCover() method"
    )

    # Extraction from images JSON object (coverarthq, coverart, background)
    assert "coverarthq" in content, "SongRecognizer must extract coverarthq if available"
    assert "coverart" in content, "SongRecognizer must fallback to coverart"
    assert "background" in content, "SongRecognizer must fallback to background image"

    # In-memory and disk caching
    assert "MEM_CACHE" in content or "LruCache" in content, (
        "SongRecognizer must have an in-memory cache"
    )
    assert "readDiskCache" in content and "writeDiskCache" in content, (
        "SongRecognizer must support reading and writing disk cache"
    )
    assert "songrec" in content, (
        "SongRecognizer must store disk cache under cacheDir/songrec"
    )
    assert "downloadAndCacheCover" in content, (
        "SongRecognizer must implement downloadAndCacheCover to persist album artwork locally"
    )


def test_online_lyrics_songrec_integration():
    """Verify OnlineLyrics integrates SongRec cover retrieval and prefetching."""
    path = os.path.join(JAVA_PLAYER_DIR, "OnlineLyrics.java")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "public static void prefetch(Query query)" in content, (
        "OnlineLyrics must provide public static void prefetch(Query query)"
    )
    assert "SongRecognizer.downloadAndCacheCover" in content, (
        "OnlineLyrics must invoke SongRecognizer.downloadAndCacheCover when cover art is found"
    )
    assert "AppearanceConfig.songrecFetchCover()" in content, (
        "OnlineLyrics must check AppearanceConfig.songrecFetchCover() before downloading cover"
    )


def test_player_sheet_background_recognition_and_lyrics_flow():
    """Verify PlayerSheet triggers background recognition on playback and wires covers and lyrics."""
    path = os.path.join(JAVA_PLAYER_DIR, "PlayerSheet.java")
    assert os.path.isfile(path), f"File not found: {path}"
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Method definition
    assert "triggerBackgroundRecognition(MessageObject mo)" in content or "triggerBackgroundRecognition(" in content, (
        "PlayerSheet must implement triggerBackgroundRecognition method"
    )
    # Background recognition trigger points
    assert "NotificationCenter.messagePlayingPlayStateChanged" in content, (
        "PlayerSheet must handle messagePlayingPlayStateChanged"
    )
    assert "triggerBackgroundRecognition" in content, (
        "PlayerSheet must invoke triggerBackgroundRecognition"
    )
    # Check config condition
    assert "AppearanceConfig.songrecBackgroundRecognition()" in content, (
        "triggerBackgroundRecognition must respect AppearanceConfig.songrecBackgroundRecognition()"
    )
    # Recognized query caching for instant lyrics
    assert "recognizedQueries" in content, (
        "PlayerSheet must store recognized queries in recognizedQueries map"
    )
    # Cover application
    assert "PlayerArt.setSongrecCover" in content, (
        "PlayerSheet must set recognized cover via PlayerArt.setSongrecCover"
    )
    # Prefetch lyrics
    assert "OnlineLyrics.fetch" in content, (
        "PlayerSheet must fetch lyrics upon successful background recognition"
    )
    # Instant query lookup
    assert "query(MessageObject mo)" in content, (
        "PlayerSheet must implement query(MessageObject mo)"
    )
    assert "recognizedQueries.get(key)" in content, (
        "query(mo) must check recognizedQueries map first for instant cached lookup"
    )


def test_player_art_and_message_object_cover_propagation():
    """Verify PlayerArt and MessageObject store and resolve SongRec album artwork."""
    art_path = os.path.join(JAVA_PLAYER_DIR, "PlayerArt.java")
    mo_path = os.path.join(MESSENGER_DIR, "MessageObject.java")
    cover_path = os.path.join(JAVA_PLAYER_DIR, "CoverImage.java")

    assert os.path.isfile(art_path), f"File not found: {art_path}"
    assert os.path.isfile(mo_path), f"File not found: {mo_path}"
    assert os.path.isfile(cover_path), f"File not found: {cover_path}"

    with open(art_path, "r", encoding="utf-8") as f:
        art_content = f.read()
    with open(mo_path, "r", encoding="utf-8") as f:
        mo_content = f.read()
    with open(cover_path, "r", encoding="utf-8") as f:
        cover_content = f.read()

    # PlayerArt methods
    assert "public static void setSongrecCover(MessageObject messageObject, String coverUrl)" in art_content, (
        "PlayerArt must have setSongrecCover method"
    )
    assert "public static String getSongrecCover(MessageObject messageObject)" in art_content, (
        "PlayerArt must have getSongrecCover method"
    )
    assert "thumbLocation" in art_content and "getSongrecCover" in art_content, (
        "PlayerArt.thumbLocation must check SongRec cover"
    )
    assert "fullLocation" in art_content and "getSongrecCover" in art_content, (
        "PlayerArt.fullLocation must check SongRec cover"
    )

    # MessageObject customArtworkUrl
    assert "public String customArtworkUrl;" in mo_content, (
        "MessageObject must declare public String customArtworkUrl"
    )
    assert "public void setCustomArtworkUrl(String url)" in mo_content, (
        "MessageObject must provide setCustomArtworkUrl"
    )
    assert "getArtworkUrl" in mo_content and "customArtworkUrl" in mo_content, (
        "MessageObject.getArtworkUrl must prioritize customArtworkUrl if set"
    )

    # CoverImage uses SongRec cover
    assert "PlayerArt.getSongrecCover(messageObject)" in cover_content, (
        "CoverImage must retrieve SongRec cover via PlayerArt.getSongrecCover"
    )
