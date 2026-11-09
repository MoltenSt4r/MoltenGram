import os
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES_JAVA = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "java")
TMESSAGES_KOTLIN = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "kotlin")
TMESSAGES_RES = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "res")


def test_profile_invisible_metadata_structure():
    meta_path = os.path.join(TMESSAGES_KOTLIN, "app", "exteraless", "chats", "ProfileInvisibleMetadata.kt")
    assert os.path.isfile(meta_path), "ProfileInvisibleMetadata.kt must exist"
    with open(meta_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "object ProfileInvisibleMetadata" in content
    assert "fun cleanLastName(" in content
    assert "fun formatLastName(" in content
    assert "fun updateSelfColors(" in content
    assert "fun updateSelfEmojiStatus(" in content
    assert "fun getMetadata(" in content
    assert "fun getEmojiStatusDocumentId(" in content
    assert "fun getNameColorId(" in content
    assert "fun getNameEmojiId(" in content
    assert "fun getProfileColorId(" in content
    assert "fun getProfileEmojiId(" in content


def test_user_object_invisible_metadata_integration():
    user_obj_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "messenger", "UserObject.java")
    with open(user_obj_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "ProfileInvisibleMetadata.getEmojiStatusDocumentId" in content
    assert "ProfileInvisibleMetadata.getNameColorId" in content
    assert "ProfileInvisibleMetadata.getNameEmojiId" in content
    assert "ProfileInvisibleMetadata.getProfileColorId" in content
    assert "ProfileInvisibleMetadata.getProfileEmojiId" in content


def test_peer_color_and_name_activity_hooks():
    color_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "PeerColorActivity.java")
    with open(color_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "ProfileInvisibleMetadata.updateSelfColors" in content

    name_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "ChangeNameActivity.java")
    with open(name_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "ProfileInvisibleMetadata.cleanLastName" in content
    assert "ProfileInvisibleMetadata.formatLastName" in content


def test_feed_hide_channel_avatars():
    msg_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "messenger", "MessageObject.java")
    with open(msg_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "AppearanceConfig.hideFeedChannelAvatars()" in content

    feed_act_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "feed", "ui", "FeedChannelsActivity.java")
    with open(feed_act_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "ID_HIDE_CHANNEL_AVATARS" in content


def test_hide_settings_accounts():
    settings_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "SettingsActivity.java")
    with open(settings_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "AppearanceConfig.hideSettingsAccounts()" in content

    app_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "settings", "OpenExteraAppearanceActivity.java")
    with open(app_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "hideAccountsSectionRow" in content


def test_player_controls_overflow_and_actions():
    controls_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "ControlsRow.java")
    with open(controls_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "Math.min(userScale, available / baseNeeded)" in content
    assert "startX" in content

    sheet_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSheet.java")
    with open(sheet_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "titleLeftSpacer" in content
    assert "shareButton" in content
    assert "bottomLikeButton" in content
    assert "updateGroupActions()" in content


def test_player_settings_activity_modernization():
    act_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSettingsActivity.java")
    with open(act_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "actionButtonsRow" in content
    assert "showActionButtonsDialog()" in content
    assert "seekbarIndicatorRow" in content
    assert "seekbarThicknessRow" in content
    assert "miniPreviewRow" in content
    assert "lyricsPreviewRow" in content
    assert "systemEqualizerRow" not in content
    assert "actionsStyleRow" not in content


def test_wavy_seekbar_indicator_and_thickness():
    sb_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "WavySeekBar.java")
    with open(sb_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "playerSeekbarIndicator" in content
    assert "playerSeekbarThickness" in content


def test_local_playlist_rename_bottom_sheet():
    pl_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "LocalPlaylistBottomSheet.java")
    with open(pl_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "BottomSheet.Builder" in content
    assert "OutlineTextContainerView" in content


def test_music_player_pause_on_mute_and_bluetooth_resume():
    srv_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "messenger", "MusicPlayerService.java")
    with open(srv_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "playerPauseOnMute" in content
    assert "VOLUME_CHANGED_ACTION" in content
    assert "playerResumeOnBluetooth" in content
    assert "ACTION_ACL_CONNECTED" in content
    # Crucial safety check: Bluetooth disconnect MUST NOT trigger resume
    assert "STATE_CONNECTED" in content, "Bluetooth reconnection must verify STATE_CONNECTED to prevent loudspeaker blasts on disconnect"


def test_invisible_metadata_exact_codec_simulation():
    START_TAG = "\u200D\u2060"
    END_TAG = "\u2060\u200D"
    KEY_STATUS = "\u206A"
    KEY_NAME_COLOR = "\u206B"
    KEY_NAME_EMOJI = "\u206C"
    KEY_PROFILE_COLOR = "\u206D"
    KEY_PROFILE_EMOJI = "\u206E"
    DELIMITER = "\u200B"

    import re

    TAG_REGEX = re.compile(re.escape(START_TAG) + r"([\uFE00-\uFE0F\u200B\u206A-\u206E]+?)" + re.escape(END_TAG))

    def encode_number(num: int) -> str:
        if num <= 0:
            return ""
        h = hex(num)[2:]
        return "".join(chr(0xFE00 + int(c, 16)) for c in h)

    def decode_number(s: str) -> int:
        if not s:
            return 0
        h = "".join(hex(ord(c) - 0xFE00)[2:] for c in s)
        return int(h, 16)

    def encode(meta: dict) -> str:
        parts = []
        if meta.get("status", 0) > 0:
            parts.append(KEY_STATUS + encode_number(meta["status"]))
        if meta.get("nc", -1) >= 0:
            parts.append(KEY_NAME_COLOR + encode_number(meta["nc"] + 1))
        if meta.get("ne", 0) > 0:
            parts.append(KEY_NAME_EMOJI + encode_number(meta["ne"]))
        if meta.get("pc", -1) >= 0:
            parts.append(KEY_PROFILE_COLOR + encode_number(meta["pc"] + 1))
        if meta.get("pe", 0) > 0:
            parts.append(KEY_PROFILE_EMOJI + encode_number(meta["pe"]))
        if not parts:
            return ""
        return START_TAG + DELIMITER.join(parts) + END_TAG

    def decode(text: str) -> dict:
        m = TAG_REGEX.search(text)
        if not m:
            return {}
        payload = m.group(1)
        res = {"status": 0, "nc": -1, "ne": 0, "pc": -1, "pe": 0}
        for tok in payload.split(DELIMITER):
            if len(tok) < 2:
                continue
            k = tok[0]
            v = decode_number(tok[1:])
            if k == KEY_STATUS:
                res["status"] = v
            elif k == KEY_NAME_COLOR:
                res["nc"] = v - 1
            elif k == KEY_NAME_EMOJI:
                res["ne"] = v
            elif k == KEY_PROFILE_COLOR:
                res["pc"] = v - 1
            elif k == KEY_PROFILE_EMOJI:
                res["pe"] = v
        return res

    test_cases = [
        {"status": 537482910492817203, "nc": 4, "ne": 7788990011, "pc": 9, "pe": 2233445566},
        {"status": 123456789, "nc": -1, "ne": 0, "pc": -1, "pe": 0},
        {"status": 0, "nc": 0, "ne": 0, "pc": 5, "pe": 998877},
        {"status": 0, "nc": 14, "ne": 5544332211, "pc": -1, "pe": 0},
    ]

    for tc in test_cases:
        tag = encode(tc)
        # All characters must be strictly invisible Unicode
        for ch in tag:
            code = ord(ch)
            assert (0xFE00 <= code <= 0xFE0F) or (0x200B <= code <= 0x200D) or (0x2060 <= code <= 0x206E), (
                f"Leaked visible character: {repr(ch)} (U+{code:04X})"
            )
        full_last_name = "Smith" + tag
        cleaned = TAG_REGEX.sub("", full_last_name)
        assert cleaned == "Smith"
        decoded = decode(full_last_name)
        for key in ["status", "nc", "ne", "pc", "pe"]:
            assert decoded.get(key) == tc.get(key), f"Mismatch for {key}: expected {tc.get(key)}, got {decoded.get(key)}"


def test_liquid_glass_completely_purged():
    dial_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "components", "Md3SpeedDialView.java")
    with open(dial_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "liquidGlassFab" not in content, "Liquid glass blur must be purged from Md3SpeedDialView"

    nav_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "settings", "OpenExteraAppNavigationActivity.java")
    with open(nav_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "ID_LIQUID_GLASS_NAV_BAR" not in content
    assert "ID_LIQUID_GLASS_FAB" not in content


def test_crossfade_dual_sided_adjustment():
    act_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSettingsActivity.java")
    with open(act_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "OEAppearancePlayerCrossfadeFadeIn" in content
    assert "OEAppearancePlayerCrossfadeFadeOut" in content
    assert "Md3RangeSlider" in content


def test_mini_player_preview_visible_and_no_border():
    mini_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerMiniView.java")
    with open(mini_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "view.setVisibility(VISIBLE)" in content, "preview must be VISIBLE"
    assert "MINI_PROGRESS_BORDER" not in content, "MINI_PROGRESS_BORDER must be removed"

    style_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerStyle.java")
    with open(style_path, "r", encoding="utf-8") as f:
        content = f.read()
    assert "MINI_PROGRESS_RING" in content

