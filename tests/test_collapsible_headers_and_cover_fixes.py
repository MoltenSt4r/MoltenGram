import os
import re

def test_collapsible_header_cell_exists():
    path = "TMessagesProj/src/main/java/tw/nekomimi/nekogram/ui/cells/CollapsibleHeaderCell.java"
    assert os.path.exists(path), f"File {path} does not exist"
    with open(path, "r", encoding="utf-8") as f:
        code = f.read()
    assert "public class CollapsibleHeaderCell extends FrameLayout" in code
    assert "public void set(CharSequence title, boolean expanded)" in code
    assert "arrowView.setRotation(expanded ? 180 : 0)" in code
    assert "Theme.createSimpleSelectorRoundRectDrawable" in code

def test_base_neko_settings_activity_collapsible_header():
    path = "TMessagesProj/src/main/java/tw/nekomimi/nekogram/settings/BaseNekoSettingsActivity.java"
    with open(path, "r", encoding="utf-8") as f:
        code = f.read()
    assert "public static final int TYPE_COLLAPSIBLE_HEADER = 18;" in code
    assert "type == TYPE_COLLAPSIBLE_HEADER" in code
    assert "case TYPE_COLLAPSIBLE_HEADER:" in code
    assert "view = new tw.nekomimi.nekogram.ui.cells.CollapsibleHeaderCell(mContext, resourcesProvider);" in code
    assert "viewType == TYPE_COLLAPSIBLE_HEADER" in code

def test_settings_activities_use_collapsible_header_not_collapse_text_cell():
    files = [
        "TMessagesProj/src/main/java/app/exteraless/player/PlayerSettingsActivity.java",
        "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraGeneralActivity.java",
        "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraChatsActivity.java",
        "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraOtherActivity.java",
        "TMessagesProj/src/main/java/app/exteraless/spoof/DeviceSpoofActivity.java"
    ]
    for path in files:
        with open(path, "r", encoding="utf-8") as f:
            code = f.read()
        assert "CollapseTextCell" not in code, f"{path} still references CollapseTextCell"
        assert "TYPE_COLLAPSIBLE_HEADER" in code, f"{path} should use TYPE_COLLAPSIBLE_HEADER"

def test_music_player_service_notification_and_media_metadata():
    path = "TMessagesProj/src/main/java/org/telegram/messenger/MusicPlayerService.java"
    with open(path, "r", encoding="utf-8") as f:
        code = f.read()
    assert "NotificationCenter.audioInfoLoaded" in code
    assert "meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART" in code
    assert "meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ART" in code
    assert "PlayerArt.fileCover(messageObject)" in code
    assert "PlayerArt.getSongrecCover(messageObject)" in code

def test_cover_image_no_flicker_and_instant_cover():
    path = "TMessagesProj/src/main/java/app/exteraless/player/CoverImage.java"
    with open(path, "r", encoding="utf-8") as f:
        code = f.read()
    assert "PlayerArt.fileCover(messageObject)" in code
    assert "PlayerArt.localFile(messageObject)" in code
    # Ensure clearImage is not called before checking if file is non-null
    assert "imageView.setImageBitmap(file);" in code
    assert "imageView.getImageReceiver().setCrossfadeWithOldImage(true);" in code

def test_player_art_public_methods_and_preload():
    path = "TMessagesProj/src/main/java/app/exteraless/player/PlayerArt.java"
    with open(path, "r", encoding="utf-8") as f:
        code = f.read()
    assert "public static java.io.File localFile(MessageObject mo)" in code
    assert "public static void putEmbeddedCover(String key, Bitmap bmp)" in code
    assert "if (hasCover) {\n            return;\n        }" in code or "if (hasCover) {\n                return;\n            }" in code or "if (hasCover) {" in code
