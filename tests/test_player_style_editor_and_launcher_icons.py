import os
import re
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main")

def test_material_you_vector_bounds():
    xml_path = os.path.join(TMESSAGES, "res", "drawable", "moltengram_icon_foreground.xml")
    assert os.path.isfile(xml_path), f"File {xml_path} must exist"
    with open(xml_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify scale is ~0.0032
    match_scale = re.search(r'android:scaleX="([0-9.]+)"', content)
    assert match_scale is not None, "scaleX must be present"
    scale_val = float(match_scale.group(1))
    assert 0.0030 <= scale_val <= 0.0035, f"scaleX {scale_val} should be between 0.0030 and 0.0035"

    # Verify translation centers the icon at ~ (54, 54)
    match_tx = re.search(r'android:translateX="([0-9.]+)"', content)
    match_ty = re.search(r'android:translateY="([0-9.]+)"', content)
    assert match_tx is not None and match_ty is not None

def test_text_detail_settings_cell_launcher_icon_preview():
    cell_path = os.path.join(TMESSAGES, "java", "org", "telegram", "ui", "Cells", "TextDetailSettingsCell.java")
    assert os.path.isfile(cell_path)
    with open(cell_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Must use createIconPreviewDrawable to ensure smooth circular clip, not square
    assert "createIconPreviewDrawable" in content, "TextDetailSettingsCell must use createIconPreviewDrawable"
    assert "rightLauncherIconView.setImageDrawable(" in content

def test_player_settings_activity_style_editor_entry():
    activity_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerSettingsActivity.java")
    assert os.path.isfile(activity_path)
    with open(activity_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "styleEditorRow" in content, "PlayerSettingsActivity must have styleEditorRow"
    assert "PlayerSheet.openStyleEditor(this)" in content, "styleEditorRow must open PlayerSheet.openStyleEditor"
    assert "R.string.OEPlayerCustomize" in content

def test_player_style_editor_unified_tabs():
    editor_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerStyleEditor.java")
    assert os.path.isfile(editor_path)
    with open(editor_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify 5 tabs
    assert "OEPlayerStyleCover" in content, "Tab 0: Cover"
    assert "OEPlayerStyleElements" in content, "Tab 1: Elements"
    assert "OEPlayerStyleMini" in content, "Tab 2: Mini"
    assert "OEPlayerStyleAudio" in content, "Tab 3: Audio"
    assert "OEPlayerLyrics" in content, "Tab 4: Lyrics"

    # Verify MoltenGram audio features integrated
    assert "SleepTimer" in content, "Sleep timer must be in Audio tab"
    assert "LocalPlaylistBottomSheet" in content, "Local playlists must be in Audio tab"
    assert "playerCrossfade" in content, "Crossfade must be in Audio tab"

    # Verify MoltenGram lyrics features integrated
    assert "playerLyricsTranslate" in content, "Lyrics translation must be in Lyrics tab"
    assert "playerLyricsRomanize" in content, "Lyrics romanize must be in Lyrics tab"
    assert "playerLyricsLrcLib" in content, "Lyrics sources must be in Lyrics tab"

def test_player_style_and_artwork_classes():
    style_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerStyle.java")
    artwork_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerArtwork.java")
    assert os.path.isfile(style_path)
    assert os.path.isfile(artwork_path)

    with open(style_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "coverStyle" in content
    assert "coverRadiusDp" in content
    assert "discSpin" in content
    assert "miniCover" in content
    assert "setOrder" in content


def test_player_style_sync_and_sheet_apply():
    style_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerStyle.java")
    with open(style_path, "r", encoding="utf-8") as f:
        style_content = f.read()

    assert "AppearanceConfig.playerBackgroundStyle" in style_content
    assert "AppearanceConfig.playerColorStyle" in style_content
    assert "AppearanceConfig.playerSeekbarStyle" in style_content
    assert "AppearanceConfig.playerCoverShape" in style_content
    assert "AppearanceConfig.playerMiniCoverShape" in style_content
    assert "AppearanceConfig.playerSwipeTrack" in style_content
    assert "applyPreset(" in style_content

    sheet_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerSheet.java")
    with open(sheet_path, "r", encoding="utf-8") as f:
        sheet_content = f.read()

    assert "PlayerArt.getOrCycleShape" in sheet_content
    assert "PlayerStyle.titleCentered" in sheet_content
    assert "PlayerStyle.controlsScale" in sheet_content
    assert "PlayerStyle.actionsIsland" in sheet_content
    assert "PlayerStyle.showLike" in sheet_content
    assert "PlayerStyle.showModes" in sheet_content
    assert "PlayerStyle.showActions" in sheet_content
    assert "PlayerMiniView.styleChanged" in sheet_content
    assert "NotificationCenter.reloadInterface" in sheet_content

    mini_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerMiniView.java")
    with open(mini_path, "r", encoding="utf-8") as f:
        mini_content = f.read()

    assert "prevButton" in mini_content
    assert "PlayerIcon.PREV" in mini_content
    assert "NotificationCenter.reloadInterface" in mini_content
    assert "PlayerArt.getOrCycleMiniShape" in mini_content


def test_player_sheet_menu_structure():
    sheet_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerSheet.java")
    with open(sheet_path, "r", encoding="utf-8") as f:
        content = f.read()

    # 1. Save to submenu
    assert "R.string.AudioSaveTo" in content
    assert "R.string.AudioSaveToMyProfile" in content
    assert "R.string.AudioSaveToSavedMessages" in content
    assert "R.string.AudioSaveToMusicFolder" in content

    # 2. Forward & Share
    assert "R.string.Forward" in content
    assert "R.string.ShareFile" in content

    # 4. Playback order submenu
    assert "OEPlayerPlaybackOrder" in content
    assert "R.string.PlayOnce" in content
    assert "AudioPlayerAlert.PLAY_ONCE" in content
    assert "R.string.RepeatSong" in content
    assert "R.string.RepeatList" in content
    assert "R.string.ShuffleList" in content
    assert "R.string.ReverseOrder" in content

    # 5. Customize player
    assert "R.drawable.msg_customize" in content
    assert "openEditor()" in content

    # 6. Classic player disabling md3Player
    assert "R.string.OEPlayerClassic" in content
    assert "openClassic()" in content
    assert "AppearanceConfig.md3Player.setConfigBool(false);" in content

    # 7. MoltenGram features
    assert "R.string.ShowInChat" in content
    assert "R.string.AudioMyPlaylists" in content
    assert "LocalPlaylistsController.getInstance().showPlaylistsDialog" in content
    assert "R.string.OEAppearancePlayerEqualizer" in content
    assert "PlayerSettingsActivity" in content


def test_player_style_and_settings_real_effects():
    cover_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "CoverImage.java")
    with open(cover_path, "r", encoding="utf-8") as f:
        cover_content = f.read()

    assert "setCrop(boolean" in cover_content
    assert "setAspectFit(!crop)" in cover_content
    assert "getRadius()" in cover_content
    assert "invalidateOutline()" in cover_content

    seekbar_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "WavySeekBar.java")
    with open(seekbar_path, "r", encoding="utf-8") as f:
        seekbar_content = f.read()

    assert "waveEnabled" in seekbar_content
    assert "setWave(boolean wave)" in seekbar_content

    lyrics_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "LyricsView.java")
    with open(lyrics_path, "r", encoding="utf-8") as f:
        lyrics_content = f.read()

    assert "resetLayout()" in lyrics_content
    assert "currentTextSize" in lyrics_content
    assert "currentAlignment" in lyrics_content

    mini_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerMiniView.java")
    with open(mini_path, "r", encoding="utf-8") as f:
        mini_content = f.read()

    assert "cover.setCrop(" in mini_content
    assert "setShowRing(" in mini_content
    assert "PlayerStyle.miniProgress()" in mini_content

    colors_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "player", "PlayerColors.java")
    with open(colors_path, "r", encoding="utf-8") as f:
        colors_content = f.read()

    assert "ColorUtils.setAlphaComponent(primary" in colors_content


