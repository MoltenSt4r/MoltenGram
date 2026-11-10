import os
import re
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main")
TMESSAGES_JAVA = os.path.join(TMESSAGES, "java")
TMESSAGES_KOTLIN = os.path.join(TMESSAGES, "kotlin")

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

def test_player_settings_activity_seekbar_constructor():
    activity_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSettingsActivity.java")
    assert os.path.isfile(activity_path)
    with open(activity_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "seekbarWavyRow" in content
    assert "seekbarThicknessRow" in content
    assert "seekbarIndicatorRow" in content
    assert "actionButtonsRow" in content
    assert "pauseOnMuteRow" in content
    assert "resumeOnBluetoothRow" in content
    assert "SlideIntChooseView" in content
    assert "AppearanceConfig.playerSeekbarThickness" in content

def test_wavy_seekbar_dynamic_constructor():
    seekbar_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "WavySeekBar.java")
    with open(seekbar_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "AppearanceConfig.playerSeekbarIndicator" in content
    assert "AppearanceConfig.playerSeekbarThickness" in content
    assert "AppearanceConfig.playerWavy.Bool()" in content
    assert "indicator == 2" in content or "indicator == 0" in content

    cfg_path = os.path.join(TMESSAGES_KOTLIN, "app", "exteraless", "appearance", "AppearanceConfig.kt")
    with open(cfg_path, "r", encoding="utf-8") as f:
        cfg_content = f.read()
    assert "playerSeekbarThickness" in cfg_content
    assert "2..16" in cfg_content or "v in 2..16" in cfg_content

def test_queue_drag_and_drop_reordering():
    queue_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerQueueSheet.java")
    with open(queue_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "ItemTouchHelper" in content
    assert "MediaController.getInstance().getPlaylist()" in content
    assert "playlist.remove" in content
    assert "playlist.add" in content
    assert "adapter.notifyItemMoved" in content

def test_lyrics_shazam_fallback():
    lyrics_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "OnlineLyrics.java")
    with open(lyrics_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "requestShazam" in content
    assert "amp.shazam.com" in content
    assert "playerLyricsAudioRecognition" in content

def test_player_sheet_menu_structure():
    sheet_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSheet.java")
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

    # 3. Playback order submenu
    assert "OEPlayerPlaybackOrder" in content
    assert "R.string.PlayOnce" in content
    assert "R.string.RepeatSong" in content
    assert "R.string.RepeatList" in content
    assert "R.string.ShuffleList" in content
    assert "R.string.ReverseOrder" in content

    # 4. MoltenGram features
    assert "R.string.ShowInChat" in content
    assert "R.string.AudioMyPlaylists" in content
    assert "LocalPlaylistsController.getInstance().showPlaylistsDialog" in content
    assert "R.string.OEAppearancePlayerEqualizer" in content
    assert "PlayerSettingsActivity" in content

def test_rage_launcher_icon_no_bleed():
    launcher_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "LauncherIconController.java")
    with open(launcher_path, "r", encoding="utf-8") as f:
        content = f.read()

    # MOLTENGRAM_RAGE must have hasBleed = false to prevent 1.5x zoom and cut off
    assert re.search(r'MOLTENGRAM_RAGE\(.*?,\s*false\)', content, re.DOTALL) is not None

def test_avatar_shapes_by_id_priority():
    cfg_path = os.path.join(TMESSAGES_KOTLIN, "app", "exteraless", "appearance", "AppearanceConfig.kt")
    with open(cfg_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "if (typeShape != 0)" in content
    assert "avatarShapesById.Bool()" in content
    assert "Md3AvatarShapes.SHAPES_COUNT" in content

    img_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "messenger", "ImageReceiver.java")
    with open(img_path, "r", encoding="utf-8") as f:
        img_content = f.read()
    assert "oePeerId = ad.peerId" in img_content
