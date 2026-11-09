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

    # Verify scale is ~0.0021 and not oversized (> 0.003)
    match_scale = re.search(r'android:scaleX="([0-9.]+)"', content)
    assert match_scale is not None, "scaleX must be present"
    scale_val = float(match_scale.group(1))
    assert 0.0020 <= scale_val <= 0.0025, f"scaleX {scale_val} should be between 0.0020 and 0.0025 to avoid oversized icon"

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
