import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
P = "TMessagesProj/src/main/java/app/exteraless/player/"


def _r(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
        return f.read()


def test_player_art_concurrent_preload():
    s = _r(P + "PlayerArt.java")
    assert "newFixedThreadPool(3)" in s
    assert "preloadPlaylistAhead(MessageObject current)" in s
    assert "preloadTrackInBackground(MessageObject mo)" in s
    assert "idx + 1" in s and "idx + 2" in s and "idx - 1" in s
    assert "ConcurrentHashMap" in s and "recognizedQueries" in s
    assert "new LruCache<>(24)" in s
    assert "preloading.add(key)" in s


def test_song_recognizer_metadata_cover():
    s = _r(P + "SongRecognizer.java")
    assert "fetchCoverByMetadata(String artist, String title)" in s
    assert "itunes.apple.com/search" in s and "600x600bb" in s
    assert "api.deezer.com/search" in s and "cover_xl" in s


def test_songrec_fetch_cover_gating():
    s = _r(P + "PlayerArt.java")
    assert "!AppearanceConfig.songrecFetchCover()) {\n            return null" in s.replace("\r\n", "\n") or \
        "|| !AppearanceConfig.songrecFetchCover()" in s
    assert "embeddedCovers" in s and "songrecBitmapCovers" in s


def test_crop_cover_aspect_fit():
    s = _r(P + "CoverImage.java")
    assert s.count("setAspectFit(!AppearanceConfig.playerCropCover())") >= 2


def test_lyrics_experimental():
    s = _r(P + "OnlineLyrics.java")
    assert "playerLyricsExperimental()" in s


def test_toggle_notifications_and_hooks():
    s = _r(P + "PlayerSettingsActivity.java")
    assert "audioInfoLoaded" in s and "updateInterfaces" in s
    assert "preloadPlaylistAhead" in s
    assert "preloadPlaylistAhead" in _r(P + "PlayerSheet.java")
    assert "preloadPlaylistAhead" in _r("TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java")
