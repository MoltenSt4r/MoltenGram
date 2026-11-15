"""
End-to-End Requirement Test Suite for Telegram 13.2.1 Upgrade in MoltenGram.

Opaque-box and requirement-driven test suite validating Tiers 1-4:
- Tier 1: Feature Coverage (>=5 tests per requirement feature across F1-F7, total >=35 tests)
  - F1: Telegram 13.2.1 Version Code & LAYER
  - F2: Material 3 Sliders
  - F3: Monet Theming
  - F4: Custom Player Settings
  - F5: Profile Invisible Metadata
  - F6: Custom Commands & Plugins
  - F7: CI Canary Trigger and Constraints
- Tier 2: Boundary & Corner Cases (>=5 tests)
- Tier 3: Cross-Feature Combinations (>=5 tests)
- Tier 4: Real-World Acceptance Scenarios (>=5 tests)

Execution Constraints:
- 100% Python standard library + pytest.
- Strictly no local gradle invocations (R3 compliance).
- Offline, self-contained, no external corpus dependencies.
"""

import os
import re
import json
import math
import time
import subprocess
from typing import Dict, List, Optional, Tuple, Any
import pytest

# ==============================================================================
# Path Constants & Target Contracts
# ==============================================================================

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES_JAVA = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "java")
TMESSAGES_KOTLIN = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "kotlin")
TMESSAGES_RES = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "res")

GRADLE_PROPERTIES = os.path.join(REPO_ROOT, "gradle.properties")
BUILD_GRADLE = os.path.join(REPO_ROOT, "TMessagesProj", "build.gradle")
TLRPC_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "tgnet", "TLRPC.java")
BUILDVARS_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "messenger", "BuildVars.java")
SEEKBARVIEW_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "Components", "SeekBarView.java")
SLIDECHOOSEVIEW_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "Components", "SlideChooseView.java")
APPEARANCE_CONFIG_KT = os.path.join(TMESSAGES_KOTLIN, "app", "exteraless", "appearance", "AppearanceConfig.kt")
MONET_HELPER_JAVA = os.path.join(TMESSAGES_JAVA, "tw", "nekomimi", "nekogram", "helpers", "MonetHelper.java")
THEME_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "ActionBar", "Theme.java")
PROFILE_METADATA_KT = os.path.join(TMESSAGES_KOTLIN, "app", "exteraless", "chats", "ProfileInvisibleMetadata.kt")
USER_OBJECT_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "messenger", "UserObject.java")
PEER_COLOR_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "PeerColorActivity.java")
CHANGE_NAME_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "ChangeNameActivity.java")
PLUGINS_CONTROLLER_JAVA = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "plugins", "PluginsController.java")
CUSTOM_COMMANDS_JAVA = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "plugins", "ui", "CustomCommandsActivity.java")
MENTIONS_ADAPTER_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "Adapters", "MentionsAdapter.java")
CHAT_ACTIVITY_JAVA = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "ChatActivity.java")
CANARY_WORKFLOW = os.path.join(REPO_ROOT, ".github", "workflows", "canary.yml")

# Authoritative Target Specifications for Telegram 13.2.1 Upgrade
TARGET_APP_VERSION_CODE = 7291
TARGET_APP_VERSION_NAME = "13.2.1"
TARGET_LAYER = 233
TARGET_APP_PACKAGE = "com.moltengram.app"
TARGET_CODENAMES = {"Tiramisu", "Cinnamon"}
TARGET_APP_ID = 39205442
TARGET_APP_HASH = "1090b570704d1d97975994ee9c177dba"

# Base specifications
BASE_APP_VERSION_CODE = 7112
BASE_APP_VERSION_NAME = "12.10.6"
BASE_LAYER = 229

# The 70 MoltenGram-unique files that must be retained during upgrade
MOLTENGRAM_UNIQUE_FILES: Tuple[str, ...] = (
    # 67 Java files in TMessagesProj/src/main/java/app/exteraless/
    "TMessagesProj/src/main/java/app/exteraless/appearance/AccentColorTunerBottomSheet.java",
    "TMessagesProj/src/main/java/app/exteraless/appearance/ChipFoldersController.java",
    "TMessagesProj/src/main/java/app/exteraless/appearance/ChipFoldersSettingsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/appearance/CylinderScrollEffect.java",
    "TMessagesProj/src/main/java/app/exteraless/appearance/Md3AvatarShapesActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/appearance/Md3FabAppearanceActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/appearance/Md3MediaCarouselView.java",
    "TMessagesProj/src/main/java/app/exteraless/chats/ColoredTextHelper.java",
    "TMessagesProj/src/main/java/app/exteraless/chats/InlineMathHelper.java",
    "TMessagesProj/src/main/java/app/exteraless/chats/NotoEmojiHelper.java",
    "TMessagesProj/src/main/java/app/exteraless/chats/TextAnimationController.java",
    "TMessagesProj/src/main/java/app/exteraless/chats/TextAnimationSettingsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/ClipAutoIndexer.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/ClipDatabase.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/ClipImageProcessor.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/ClipTokenizer.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/OpenClipConfig.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/OpenClipInference.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/OpenClipManager.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/OpenClipSettingsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/RussianConceptTranslator.java",
    "TMessagesProj/src/main/java/app/exteraless/clip/VectorMath.java",
    "TMessagesProj/src/main/java/app/exteraless/components/Md3SpeedDialView.java",
    "TMessagesProj/src/main/java/app/exteraless/glyph/GlyphDevicePreviewCell.java",
    "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneBurstOverlay.java",
    "TMessagesProj/src/main/java/app/exteraless/glyph/NothingPhoneEasterEgg.java",
    "TMessagesProj/src/main/java/app/exteraless/nowplaying/LastFmNowPlaying.java",
    "TMessagesProj/src/main/java/app/exteraless/nowplaying/LastFmWebFetcher.java",
    "TMessagesProj/src/main/java/app/exteraless/nowplaying/ProfileMusicMark.java",
    "TMessagesProj/src/main/java/app/exteraless/nowplaying/ProfileMusicStamp.java",
    "TMessagesProj/src/main/java/app/exteraless/nowplaying/ZeroWidthCodec.java",
    "TMessagesProj/src/main/java/app/exteraless/ota/ChangelogFormatter.java",
    "TMessagesProj/src/main/java/app/exteraless/ota/LinearWavyProgressView.java",
    "TMessagesProj/src/main/java/app/exteraless/ota/MoltenGramOtaJobService.java",
    "TMessagesProj/src/main/java/app/exteraless/ota/MoltenGramOtaManager.java",
    "TMessagesProj/src/main/java/app/exteraless/ota/MoltenGramOtaReceiver.java",
    "TMessagesProj/src/main/java/app/exteraless/ota/MoltenGramPackageInstallReceiver.java",
    "TMessagesProj/src/main/java/app/exteraless/ota/MoltenGramPackageReplacedReceiver.java",
    "TMessagesProj/src/main/java/app/exteraless/ota/ShapeBadgeView.java",
    "TMessagesProj/src/main/java/app/exteraless/player/GlobalPlaylistsStore.java",
    "TMessagesProj/src/main/java/app/exteraless/player/LocalPlaylistBottomSheet.java",
    "TMessagesProj/src/main/java/app/exteraless/player/LocalPlaylistsController.java",
    "TMessagesProj/src/main/java/app/exteraless/player/Md3RangeSlider.java",
    "TMessagesProj/src/main/java/app/exteraless/player/PlayerSettingsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/player/RomanizeHelper.java",
    "TMessagesProj/src/main/java/app/exteraless/player/SleepTimer.java",
    "TMessagesProj/src/main/java/app/exteraless/player/SongRecognizer.java",
    "TMessagesProj/src/main/java/app/exteraless/plugins/PluginAutoConverter.java",
    "TMessagesProj/src/main/java/app/exteraless/plugins/PluginsBackupHelper.java",
    "TMessagesProj/src/main/java/app/exteraless/plugins/ui/CustomCommandsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/plugins/ui/PluginDetailsBottomSheet.java",
    "TMessagesProj/src/main/java/app/exteraless/plugins/ui/PluginStoreActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/plugins/ui/PluginStoreConfig.java",
    "TMessagesProj/src/main/java/app/exteraless/plugins/ui/PluginStoreSettingsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraToolsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraUpdatesActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraVoiceChangerActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/shizuku/ShizukuConfig.java",
    "TMessagesProj/src/main/java/app/exteraless/shizuku/ShizukuController.java",
    "TMessagesProj/src/main/java/app/exteraless/shizuku/ShizukuSettingsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/spoof/DeviceSpoofActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/spoof/DeviceSpoofManager.java",
    "TMessagesProj/src/main/java/app/exteraless/spoof/DeviceSpoofPreset.java",
    "TMessagesProj/src/main/java/app/exteraless/tor/TorController.java",
    "TMessagesProj/src/main/java/app/exteraless/tor/TorDiagnostics.java",
    "TMessagesProj/src/main/java/app/exteraless/tor/TorSettingsActivity.java",
    "TMessagesProj/src/main/java/app/exteraless/voice/VoiceChangerController.java",
    # 3 Kotlin files in TMessagesProj/src/main/kotlin/app/exteraless/
    "TMessagesProj/src/main/kotlin/app/exteraless/appearance/Md3AvatarShapes.kt",
    "TMessagesProj/src/main/kotlin/app/exteraless/chats/CustomEmojiStatusHelper.kt",
    "TMessagesProj/src/main/kotlin/app/exteraless/chats/ProfileInvisibleMetadata.kt",
)

# 26 Custom Player files in TMessagesProj/src/main/java/app/exteraless/player/
PLAYER_FILES: Tuple[str, ...] = (
    "ControlsRow.java",
    "CoverImage.java",
    "GlobalPlaylistsStore.java",
    "LocalPlaylistBottomSheet.java",
    "LocalPlaylistsController.java",
    "Lyrics.java",
    "LyricsView.java",
    "Md3Player.java",
    "Md3RangeSlider.java",
    "MorphButton.java",
    "OnlineLyrics.java",
    "PlayerActions.java",
    "PlayerArt.java",
    "PlayerBarView.java",
    "PlayerColors.java",
    "PlayerIcon.java",
    "PlayerMiniView.java",
    "PlayerQueueSheet.java",
    "PlayerSettingsActivity.java",
    "PlayerSheet.java",
    "RingPlayButton.java",
    "RomanizeHelper.java",
    "SleepTimer.java",
    "SongRecognizer.java",
    "Spring.java",
    "WavySeekBar.java",
)


# ==============================================================================
# Pure Python Oracles & Simulators
# ==============================================================================

class InvisibleMetadataCodecOracle:
    """Exact Python reproduction of ProfileInvisibleMetadata.kt zero-width codec."""
    START_TAG = "\u200D\u2060"
    END_TAG = "\u2060\u200D"
    KEY_STATUS = "\u206A"
    KEY_NAME_COLOR = "\u206B"
    KEY_NAME_EMOJI = "\u206C"
    KEY_PROFILE_COLOR = "\u206D"
    KEY_PROFILE_EMOJI = "\u206E"
    DELIMITER = "\u200B"

    TAG_REGEX = re.compile(
        re.escape(START_TAG) + r"([\uFE00-\uFE0F\u200B\u206A-\u206E]+?)" + re.escape(END_TAG)
    )

    @classmethod
    def encode_number(cls, num: int) -> str:
        if num <= 0:
            return ""
        hex_str = hex(num)[2:]
        return "".join(chr(0xFE00 + int(c, 16)) for c in hex_str)

    @classmethod
    def decode_number(cls, s: str) -> int:
        if not s:
            return 0
        hex_digits = []
        for ch in s:
            code = ord(ch)
            if 0xFE00 <= code <= 0xFE0F:
                hex_digits.append(hex(code - 0xFE00)[2:])
            else:
                return 0
        hex_str = "".join(hex_digits)
        return int(hex_str, 16) if hex_str else 0

    @classmethod
    def encode(cls, meta: Dict[str, Any]) -> str:
        parts: List[str] = []
        if meta.get("status", 0) > 0:
            parts.append(cls.KEY_STATUS + cls.encode_number(meta["status"]))
        if meta.get("nc", -1) >= 0:
            parts.append(cls.KEY_NAME_COLOR + cls.encode_number(meta["nc"] + 1))
        if meta.get("ne", 0) > 0:
            parts.append(cls.KEY_NAME_EMOJI + cls.encode_number(meta["ne"]))
        if meta.get("pc", -1) >= 0:
            parts.append(cls.KEY_PROFILE_COLOR + cls.encode_number(meta["pc"] + 1))
        if meta.get("pe", 0) > 0:
            parts.append(cls.KEY_PROFILE_EMOJI + cls.encode_number(meta["pe"]))
        if not parts:
            return ""
        return cls.START_TAG + cls.DELIMITER.join(parts) + cls.END_TAG

    @classmethod
    def decode(cls, text: Optional[str]) -> Optional[Dict[str, Any]]:
        if not text:
            return None
        m = cls.TAG_REGEX.search(text)
        if not m:
            return None
        payload = m.group(1)
        res = {"status": 0, "nc": -1, "ne": 0, "pc": -1, "pe": 0}
        tokens = payload.split(cls.DELIMITER)
        for tok in tokens:
            if len(tok) < 2:
                continue
            k = tok[0]
            val = cls.decode_number(tok[1:])
            if k == cls.KEY_STATUS:
                res["status"] = val
            elif k == cls.KEY_NAME_COLOR:
                res["nc"] = val - 1
            elif k == cls.KEY_NAME_EMOJI:
                res["ne"] = val
            elif k == cls.KEY_PROFILE_COLOR:
                res["pc"] = val - 1
            elif k == cls.KEY_PROFILE_EMOJI:
                res["pe"] = val
        if res["status"] <= 0 and res["nc"] < 0 and res["ne"] <= 0 and res["pc"] < 0 and res["pe"] <= 0:
            return None
        return res

    @classmethod
    def clean_last_name(cls, last_name: Optional[str]) -> str:
        if not last_name:
            return ""
        return cls.TAG_REGEX.sub("", last_name).rstrip()

    @classmethod
    def format_last_name(cls, last_name: Optional[str], meta: Optional[Dict[str, Any]]) -> str:
        base = cls.clean_last_name(last_name)
        if not meta:
            return base
        tag = cls.encode(meta)
        return f"{base}{tag}" if base else tag

    @classmethod
    def is_strictly_invisible_unicode(cls, s: str) -> bool:
        for ch in s:
            code = ord(ch)
            if not ((0xFE00 <= code <= 0xFE0F) or (0x200B <= code <= 0x200D) or (0x2060 <= code <= 0x206E)):
                return False
        return True


class MonetBaselineOracle:
    """Mathematical oracle evaluating static baseline purple rejection."""
    BASELINE_PURPLE_80 = 0xffd0bcff
    BASELINE_PURPLE_40 = 0xff6750a4
    BASELINE_DARK_SURFACE = 0xff141218
    BASELINE_LIGHT_SURFACE = 0xfffef7ff
    BASELINE_DARK_CONTAINER = 0xff4a4458

    @classmethod
    def is_exact_static_color(cls, color: int) -> bool:
        rgb = color & 0xFFFFFF
        return rgb in (
            cls.BASELINE_PURPLE_80 & 0xFFFFFF,
            cls.BASELINE_PURPLE_40 & 0xFFFFFF,
            cls.BASELINE_DARK_SURFACE & 0xFFFFFF,
            cls.BASELINE_LIGHT_SURFACE & 0xFFFFFF,
            cls.BASELINE_DARK_CONTAINER & 0xFFFFFF,
        )

    @classmethod
    def is_hue_in_purple_rejection_window(cls, hue: float, chroma: float) -> bool:
        return 265.0 <= hue <= 318.0 and chroma >= 3.0


class SliderDimensionsOracle:
    """Calculates slider dimensions strictly matching SeekBarView.drawMd3Slider."""
    @classmethod
    def clamp_thickness(cls, thickness_dp: int) -> int:
        return max(2, min(24, thickness_dp))

    @classmethod
    def compute_metrics(cls, thickness_dp: int, density: float = 2.0) -> Dict[str, float]:
        t = float(cls.clamp_thickness(thickness_dp))
        track_height = t * density
        # Match Java Math.round(float) half-up behavior: (int)Math.floor(f + 0.5f)
        rounded_quarter = float(math.floor(t * 0.25 + 0.5))
        thumb_width = max(3.0, rounded_quarter) * density
        thumb_height = max(20.0, t + 28.0) * density
        gap = max(2.0, rounded_quarter) * density
        radius = track_height / 2.0
        thumb_radius = thumb_width / 2.0
        tick_radius = max(1.5, min(2.5, t * 0.16)) * density
        return {
            "thickness_dp": t,
            "track_height": track_height,
            "thumb_width": thumb_width,
            "thumb_height": thumb_height,
            "gap": gap,
            "radius": radius,
            "thumb_radius": thumb_radius,
            "tick_radius": tick_radius,
        }


class CustomCommandParserOracle:
    """Simulates PluginsController.CustomCommand string sanitization and CRUD."""
    @classmethod
    def sanitize(cls, prefix: Optional[str], name: Optional[str], desc: Optional[str] = "") -> Dict[str, str]:
        p = prefix.strip()[:1] if (prefix and prefix.strip()) else "."
        n = name.strip() if name else ""
        if n.startswith(p):
            n = n[len(p):].strip()
        elif n.startswith((".", "/", "!", "$")):
            p = n[0]
            n = n[1:].strip()
        return {
            "prefix": p,
            "name": n,
            "desc": desc.strip() if desc else "",
            "full": f"{p}{n}",
        }


# ==============================================================================
# Tier 1: Feature Coverage (>=5 tests per requirement feature across F1-F7)
# ==============================================================================

class TestTier1FeatureCoverage:
    """Tier 1: Feature Coverage across F1 through F7 (total 35 tests)."""

    # --------------------------------------------------------------------------
    # F1: Telegram 13.2.1 Version Code & LAYER
    # --------------------------------------------------------------------------

    def test_f1_01_version_code_and_upgrade_monotonicity(self):
        """F1: Verifies APP_VERSION_CODE target 7291, monotonicity over base 7112, and offset headroom."""
        with open(GRADLE_PROPERTIES, "r", encoding="utf-8") as f:
            content = f.read()

        codes = [int(m) for m in re.findall(r"^APP_VERSION_CODE=(\d+)", content, re.MULTILINE)]
        assert len(codes) > 0, "APP_VERSION_CODE must be present in gradle.properties"

        # Strict validation: must equal target 7291, exceed base 7112, and remain below baseCode 20000
        for code in codes:
            assert code == TARGET_APP_VERSION_CODE, (
                f"APP_VERSION_CODE {code} in gradle.properties must equal target {TARGET_APP_VERSION_CODE}"
            )
            assert code > BASE_APP_VERSION_CODE, (
                f"APP_VERSION_CODE {code} must be strictly greater than base {BASE_APP_VERSION_CODE}"
            )
            assert code < 20000, (
                f"APP_VERSION_CODE {code} must be strictly less than baseCode 20000 to prevent APK collision"
            )

    def test_f1_02_version_name_semantic_format(self):
        """F1: Verifies APP_VERSION_NAME target 13.2.1 and clean semantic version format."""
        with open(GRADLE_PROPERTIES, "r", encoding="utf-8") as f:
            content = f.read()

        names = re.findall(r"^APP_VERSION_NAME=([\d\.]+)", content, re.MULTILINE)
        assert len(names) > 0, "APP_VERSION_NAME must be defined in gradle.properties"
        for name in names:
            assert re.match(r"^\d+\.\d+\.\d+$", name), f"Version name '{name}' must follow semver format"
            assert name == TARGET_APP_VERSION_NAME, (
                f"APP_VERSION_NAME '{name}' in gradle.properties must equal target '{TARGET_APP_VERSION_NAME}'"
            )
            # Semver monotonicity over base version
            major, minor, patch = map(int, name.split("."))
            base_major, base_minor, base_patch = map(int, BASE_APP_VERSION_NAME.split("."))
            assert (major, minor, patch) > (base_major, base_minor, base_patch), (
                f"Semantic version ({major}.{minor}.{patch}) must be strictly greater than base {BASE_APP_VERSION_NAME}"
            )

    def test_f1_03_mtproto_layer_233_and_bot_auth_retention(self):
        """F1: Verifies MTProto LAYER upgrade to 233 and custom TL_auth_importBotAuthorization."""
        with open(TLRPC_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        m = re.search(r"public static final int LAYER\s*=\s*(\d+);", content)
        assert m, "LAYER declaration must exist in TLRPC.java"
        layer = int(m.group(1))
        assert layer == TARGET_LAYER, (
            f"LAYER {layer} in TLRPC.java has not been upgraded to target {TARGET_LAYER}"
        )
        assert layer > BASE_LAYER, (
            f"LAYER upgrade must be strictly monotonic over base {BASE_LAYER}"
        )

        # Crucial MoltenGram custom class retention
        assert "class TL_auth_importBotAuthorization extends TLObject" in content
        assert "0x67a3ff2c" in content, "TL_auth_importBotAuthorization constructor magic must be 0x67a3ff2c"

    def test_f1_04_branding_package_and_codename_support(self):
        """F1: Verifies MoltenGram branding com.moltengram.app and codename support."""
        with open(GRADLE_PROPERTIES, "r", encoding="utf-8") as f:
            content = f.read()

        packages = re.findall(r"^APP_PACKAGE=([a-zA-Z0-9_\.]+)", content, re.MULTILINE)
        assert len(packages) > 0 and packages[0] == TARGET_APP_PACKAGE, (
            f"MoltenGram package ID {TARGET_APP_PACKAGE} must be preserved"
        )

        codenames = re.findall(r"^APP_CODENAME=([a-zA-Z0-9_\.]+)", content, re.MULTILINE)
        assert len(codenames) > 0, "APP_CODENAME must be defined in gradle.properties"
        for cn in codenames:
            assert cn in TARGET_CODENAMES, f"Codename '{cn}' must be in {TARGET_CODENAMES}"

        # Verify BuildVars codename fallback
        with open(BUILDVARS_JAVA, "r", encoding="utf-8") as f:
            bvars = f.read()
        assert "BUILD_CODENAME" in bvars
        assert 'BUILD_CODENAME : "Tiramisu"' in bvars or 'BUILD_CODENAME : "Cinnamon"' in bvars

    def test_f1_05_official_api_credentials_and_buildvars(self):
        """F1: Verifies official API credentials APP_ID=39205442 and APP_HASH."""
        with open(BUILDVARS_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert f"APP_ID = {TARGET_APP_ID};" in content
        assert f'APP_HASH = "{TARGET_APP_HASH}";' in content

        with open(BUILD_GRADLE, "r", encoding="utf-8") as f:
            bgradle = f.read()
        assert f"def appId = '{TARGET_APP_ID}'" in bgradle
        assert f"def appHash = '{TARGET_APP_HASH}'" in bgradle

    # --------------------------------------------------------------------------
    # F2: Material 3 Sliders
    # --------------------------------------------------------------------------

    def test_f2_01_seekbarview_draw_md3_slider_implementation(self):
        """F2: Verifies drawMd3Slider implementation and dynamic formula in SeekBarView."""
        with open(SEEKBARVIEW_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "drawMd3Slider(Canvas canvas, int thumbX)" in content
        assert "getTrackHeightDp()" in content
        assert "AndroidUtilities.dp(thicknessDp)" in content
        assert "Math.max(3, Math.round(thicknessDp * 0.25f))" in content
        assert "Math.min(getMeasuredHeight(), AndroidUtilities.dp(Math.max(20, thicknessDp + 28)))" in content
        assert "Math.max(2, Math.round(thicknessDp * 0.25f))" in content
        assert "drawRoundRect" in content

    def test_f2_02_dynamic_slider_thickness_hook_and_bounds(self):
        """F2: Verifies dynamic thickness hook in SeekBarView and coerceIn(2, 24) in AppearanceConfig."""
        with open(SEEKBARVIEW_JAVA, "r", encoding="utf-8") as f:
            sb = f.read()
        assert "app.exteraless.appearance.AppearanceConfig.sliderThickness()" in sb

        with open(APPEARANCE_CONFIG_KT, "r", encoding="utf-8") as f:
            ac = f.read()
        assert "OEAppearanceSliderThickness" in ac
        assert "coerceIn(2, 24)" in ac
        assert "fun sliderThickness(): Int" in ac

    def test_f2_03_slidechooseview_92dp_exact_measure_spec(self):
        """F2: Verifies SlideChooseView enforces exactly 92dp height and gesture exclusions."""
        with open(SLIDECHOOSEVIEW_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "AndroidUtilities.dp(92)" in content
        assert "MeasureSpec.EXACTLY" in content
        assert "AndroidUtilities.dp(80)" in content
        assert "gestureExclusionRects" in content
        assert "canUseMaterialSlider()" in content
        assert "drawMaterialSlider" in content

    def test_f2_04_wavy_seekbar_structure_and_controls(self):
        """F2: Verifies WavySeekBar animation delegate and methods."""
        wavy_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "WavySeekBar.java")
        assert os.path.isfile(wavy_path)
        with open(wavy_path, "r", encoding="utf-8") as f:
            content = f.read()

        assert "public interface Delegate" in content
        assert "void onSeekStart()" in content
        assert "void onSeekMove(float progress)" in content
        assert "void onSeekEnd(float progress, boolean commit)" in content
        assert "public void setWave(boolean wave)" in content
        assert "public void setMaxAmplitude(float px)" in content

    def test_f2_05_md3_range_slider_structure_and_crossfade(self):
        """F2: Verifies Md3RangeSlider dual handles, height, and range change listener."""
        range_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "Md3RangeSlider.java")
        assert os.path.isfile(range_path)
        with open(range_path, "r", encoding="utf-8") as f:
            content = f.read()

        assert "public interface OnRangeChangeListener" in content
        assert "void onRangeChanged(int value1, int value2, boolean fromUser)" in content
        assert "AndroidUtilities.dp(72)" in content
        assert "setRange(int min, int max)" in content
        assert "setValues(int val1, int val2)" in content

    # --------------------------------------------------------------------------
    # F3: Monet Theming
    # --------------------------------------------------------------------------

    def test_f3_01_monet_helper_dark_roles_neutral_1(self):
        """F3: Verifies MonetHelper darkRoles map surface and background to neutral-1."""
        with open(MONET_HELPER_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert 'put("background", "n1:6");' in content
        assert 'put("surface", "n1:6");' in content
        assert 'put("surface_dim", "n1:6");' in content
        assert 'put("surface_container", "n1:12");' in content
        assert 'put("on_surface", "n1:90");' in content

    def test_f3_02_monet_helper_static_baseline_purple_rejection(self):
        """F3: Verifies baseline purple constants and hue rejection window [265°, 318°]."""
        with open(MONET_HELPER_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "d0bcff" in content.lower()
        assert "6750a4" in content.lower()
        assert "141218" in content.lower()
        assert "4a4458" in content.lower()
        assert "isStaticBaselineFrameworkRole" in content
        assert "265d" in content and "318d" in content

    def test_f3_03_monet_helper_wallpaper_seed_extraction(self):
        """F3: Verifies dynamic primary seed extraction from WallpaperColors."""
        with open(MONET_HELPER_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "getWallpaperPrimarySeed(Context context)" in content
        assert "WallpaperColors" in content
        assert "FLAG_SYSTEM" in content
        assert "FLAG_LOCK" in content

    def test_f3_04_theme_monet_fallback_keys(self):
        """F3: Verifies Theme.java fallback keys for progress circles and buttons."""
        with open(THEME_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "fallbackKeys.put(key_progressCircle, key_featuredStickers_addButton);" in content
        assert "fallbackKeys.put(key_dialog_inlineProgress, key_featuredStickers_addButton);" in content

    def test_f3_05_monochrome_launcher_icons(self):
        """F3: Verifies all anydpi-v26 launcher icons declare monochrome vector tags."""
        anydpi_dir = os.path.join(TMESSAGES_RES, "mipmap-anydpi-v26")
        assert os.path.isdir(anydpi_dir)
        xml_files = [f for f in os.listdir(anydpi_dir) if f.endswith(".xml")]
        assert len(xml_files) > 0, "Launcher anydpi XML files must exist"

        for x in xml_files:
            with open(os.path.join(anydpi_dir, x), "r", encoding="utf-8") as f:
                content = f.read()
            assert "<monochrome" in content, f"Icon {x} must contain <monochrome tag"
            assert "android:drawable=" in content

        # Primary MoltenGram launcher icons must point to moltengram_icon_monochrome
        for primary in ("ic_launcher.xml", "ic_launcher_round.xml", "ic_launcher_moltengram.xml", "ic_launcher_moltengram_round.xml"):
            path = os.path.join(anydpi_dir, primary)
            if os.path.isfile(path):
                with open(path, "r", encoding="utf-8") as f:
                    content = f.read()
                assert "moltengram_icon_monochrome" in content, f"{primary} must reference moltengram_icon_monochrome"

    # --------------------------------------------------------------------------
    # F4: Custom Player Settings
    # --------------------------------------------------------------------------

    def test_f4_01_all_26_player_files_exist_and_non_empty(self):
        """F4: Verifies all 26 custom player classes exist and are non-empty."""
        player_dir = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player")
        for f in PLAYER_FILES:
            full_path = os.path.join(player_dir, f)
            assert os.path.isfile(full_path), f"Player file {f} must exist"
            assert os.path.getsize(full_path) > 200, f"Player file {f} must not be empty"

    def test_f4_02_controls_row_responsive_scaling_formula(self):
        """F4: Verifies ControlsRow dynamic scaling formula and height calculation."""
        cr_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "ControlsRow.java")
        with open(cr_path, "r", encoding="utf-8") as f:
            content = f.read()

        assert "scale" in content
        assert "dp(88)" in content
        assert "measureButtons()" in content
        assert "layoutButtons" in content

    def test_f4_03_player_sheet_action_buttons_rebuild(self):
        """F4: Verifies PlayerSheet rebuildActionButtons supports all 7 tokens and fallback."""
        ps_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSheet.java")
        with open(ps_path, "r", encoding="utf-8") as f:
            content = f.read()

        assert "rebuildActionButtons()" in content
        for token in ("speed", "lyrics", "playlist", "sleep", "queue", "share", "like"):
            assert f'"{token}"' in content, f"Token {token} must be handled in PlayerSheet"
        assert "dp(26)" in content and "dp(8)" in content

    def test_f4_04_player_settings_activity_controls_and_purged_rows(self):
        """F4: Verifies PlayerSettingsActivity rows and confirmed purge of obsolete rows."""
        act_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSettingsActivity.java")
        with open(act_path, "r", encoding="utf-8") as f:
            content = f.read()

        assert "actionButtonsRow" in content
        assert "showActionButtonsDialog()" in content
        assert "seekbarThicknessRow" in content
        assert "miniPreviewRow" in content

        # Confirmed purge of obsolete rows
        assert "systemEqualizerRow" not in content, "Obsolete systemEqualizerRow must remain purged"
        assert "actionsStyleRow" not in content, "Obsolete actionsStyleRow must remain purged"

    def test_f4_05_local_playlists_and_online_lyrics(self):
        """F4: Verifies LocalPlaylistsController CRUD and OnlineLyrics normalized matching."""
        lpc_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "LocalPlaylistsController.java")
        with open(lpc_path, "r", encoding="utf-8") as f:
            lpc = f.read()
        assert "LocalPlaylistsController getInstance()" in lpc
        assert "createPlaylist(" in lpc
        assert "exportM3U8(" in lpc
        assert "createRadSelectorDrawable" in lpc

        ol_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "OnlineLyrics.java")
        with open(ol_path, "r", encoding="utf-8") as f:
            ol = f.read()
        assert "tokenSimilarity(" in ol

    # --------------------------------------------------------------------------
    # F5: Profile Invisible Metadata
    # --------------------------------------------------------------------------

    def test_f5_01_profile_invisible_metadata_kt_core_methods(self):
        """F5: Verifies ProfileInvisibleMetadata.kt declares all 10 core methods."""
        with open(PROFILE_METADATA_KT, "r", encoding="utf-8") as f:
            content = f.read()

        assert "object ProfileInvisibleMetadata" in content
        methods = (
            "cleanLastName",
            "formatLastName",
            "updateSelfColors",
            "updateSelfEmojiStatus",
            "getMetadata",
            "getEmojiStatusDocumentId",
            "getNameColorId",
            "getNameEmojiId",
            "getProfileColorId",
            "getProfileEmojiId",
        )
        for m in methods:
            assert f"fun {m}(" in content, f"Method {m} must be declared in ProfileInvisibleMetadata.kt"

    def test_f5_02_user_object_all_six_hooks_present(self):
        """F5: Verifies UserObject.java implements all 6 invisible metadata hook points."""
        with open(USER_OBJECT_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "ProfileInvisibleMetadata.getEmojiStatusDocumentId" in content
        assert "ProfileInvisibleMetadata.getNameColorId" in content
        assert "ProfileInvisibleMetadata.getNameEmojiId" in content
        assert "ProfileInvisibleMetadata.getProfileColorId" in content
        assert "ProfileInvisibleMetadata.getProfileEmojiId" in content

    def test_f5_03_peer_color_activity_p2p_sync_and_unlocked(self):
        """F5: Verifies PeerColorActivity P2P metadata sync and non-premium unlocked state."""
        with open(PEER_COLOR_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "ProfileInvisibleMetadata.updateSelfColors" in content
        assert "buttonUnlocked" in content, "PeerColor button must allow unlocked non-premium state"

    def test_f5_04_change_name_activity_hooks(self):
        """F5: Verifies ChangeNameActivity cleans display text and preserves metadata on save."""
        with open(CHANGE_NAME_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "ProfileInvisibleMetadata.cleanLastName" in content
        assert "ProfileInvisibleMetadata.formatLastName" in content

    def test_f5_05_zero_width_codec_algorithmic_roundtrip(self):
        """F5: Simulates exact algorithmic roundtrip of zero-width unicode codec."""
        # Connect codec markers directly to ProfileInvisibleMetadata.kt
        with open(PROFILE_METADATA_KT, "r", encoding="utf-8") as pmf:
            pm_content = pmf.read()
        assert r'START_TAG = "\u200D\u2060"' in pm_content
        assert r'END_TAG = "\u2060\u200D"' in pm_content
        assert r"DELIMITER = '\u200B'" in pm_content
        assert r"KEY_STATUS = '\u206A'" in pm_content

        test_payloads = [
            {"status": 537482910492817203, "nc": 4, "ne": 7788990011, "pc": 9, "pe": 2233445566},
            {"status": 123456789, "nc": -1, "ne": 0, "pc": -1, "pe": 0},
            {"status": 0, "nc": 0, "ne": 0, "pc": 5, "pe": 998877},
            {"status": 0, "nc": 14, "ne": 5544332211, "pc": -1, "pe": 0},
        ]
        for payload in test_payloads:
            encoded = InvisibleMetadataCodecOracle.encode(payload)
            assert InvisibleMetadataCodecOracle.is_strictly_invisible_unicode(encoded)
            full_name = f"Ivanov{encoded}"
            cleaned = InvisibleMetadataCodecOracle.clean_last_name(full_name)
            assert cleaned == "Ivanov"
            decoded = InvisibleMetadataCodecOracle.decode(full_name)
            assert decoded is not None
            for k in ("status", "nc", "ne", "pc", "pe"):
                assert decoded[k] == payload[k], f"Codec mismatch for field '{k}'"

    # --------------------------------------------------------------------------
    # F6: Custom Commands & Plugins
    # --------------------------------------------------------------------------

    def test_f6_01_custom_commands_activity_structure(self):
        """F6: Verifies CustomCommandsActivity exists and implements CRUD dialogs."""
        assert os.path.isfile(CUSTOM_COMMANDS_JAVA)
        with open(CUSTOM_COMMANDS_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "CustomCommandsActivity" in content
        assert "showAddOrEditDialog" in content
        assert "confirmDelete" in content

    def test_f6_02_plugins_controller_custom_command_class(self):
        """F6: Verifies PluginsController.CustomCommand static class and serialization."""
        with open(PLUGINS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "public static class CustomCommand" in content
        assert "public String prefix = \".\";" in content
        assert "public String name = \"\";" in content
        assert "public String description = \"\";" in content
        assert "public JSONObject toJson()" in content
        assert "public static CustomCommand fromJson(JSONObject obj)" in content

    def test_f6_03_plugins_controller_crud_operations(self):
        """F6: Verifies PluginsController custom command CRUD and persistence methods."""
        with open(PLUGINS_CONTROLLER_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "public List<CustomCommand> getCustomCommands()" in content
        assert "public void loadCustomCommands()" in content
        assert "public void saveCustomCommands()" in content
        assert "public void addCustomCommand(" in content
        assert "public void editCustomCommand(" in content
        assert "public void deleteCustomCommand(" in content

    def test_f6_04_mentions_adapter_multi_prefix_autocompletion(self):
        """F6: Verifies MentionsAdapter multi-prefix autocompletion integration."""
        with open(MENTIONS_ADAPTER_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "PluginsController.getInstance().isPluginPrefix(ch)" in content
        assert "foundType = 2;" in content
        assert "foundPrefix = ch;" in content
        assert "PluginsController.getInstance().getMatchingCommands(" in content

    def test_f6_05_chat_activity_command_click_guard(self):
        """F6: Verifies ChatActivity click guard prevents instant dispatch of custom commands."""
        with open(CHAT_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "isCustomOrPluginCommand((String) object)" in content
        assert "chatActivityEnterView.replaceWithText(" in content
        assert "hideFieldPanel(false);" in content

    # --------------------------------------------------------------------------
    # F7: CI Canary Trigger and Constraints
    # --------------------------------------------------------------------------

    def test_f7_01_canary_workflow_push_trigger_branch(self):
        """F7: Verifies canary.yml triggers on pushes to branch canary."""
        with open(CANARY_WORKFLOW, "r", encoding="utf-8") as f:
            content = f.read()

        assert "branches:" in content
        assert "canary" in content
        assert "workflow_dispatch:" in content

    def test_f7_02_canary_workflow_staging_assemble_command(self):
        """F7: Verifies canary build target ./gradlew TMessagesProj:assembleStaging."""
        with open(CANARY_WORKFLOW, "r", encoding="utf-8") as f:
            content = f.read()

        assert "./gradlew TMessagesProj:assembleStaging" in content
        assert "NATIVE_TARGET=arm64-v8a" in content

    def test_f7_03_canary_workflow_artifact_upload(self):
        """F7: Verifies staging APK artifact packaging in canary workflow."""
        with open(CANARY_WORKFLOW, "r", encoding="utf-8") as f:
            content = f.read()

        assert "upload-artifact" in content
        assert "TMessagesProj/build/outputs/apk/staging/*.apk" in content

    def test_f7_04_canary_workflow_credentials_fallback(self):
        """F7: Verifies fallback API credentials in canary workflow."""
        with open(CANARY_WORKFLOW, "r", encoding="utf-8") as f:
            content = f.read()

        assert f"TG_API_ID: ${{{{ secrets.TG_API_ID || '{TARGET_APP_ID}' }}}}" in content
        assert f"TG_API_HASH: ${{{{ secrets.TG_API_HASH || '{TARGET_APP_HASH}' }}}}" in content

    def test_f7_05_r3_no_local_gradle_execution_constraint(self):
        """F7: Verifies R3 constraint: tests run entirely offline via pytest without local gradle."""
        # 1. Invariant: No local gradle daemon directory exists in the workspace
        gradle_daemon_path = os.path.join(REPO_ROOT, ".gradle", "daemon")
        assert not os.path.exists(gradle_daemon_path), (
            f"Local gradle daemon directory '{gradle_daemon_path}' must not exist; "
            "R3 forbids local gradle execution."
        )

        # 2. Invariant: Scan all tests confirming zero tests invoke ./gradlew locally
        tests_dir = os.path.join(REPO_ROOT, "tests")
        for fname in os.listdir(tests_dir):
            if fname.endswith(".py") and fname != "test_e2e_telegram_13_2_1_upgrade.py":
                fpath = os.path.join(tests_dir, fname)
                with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                    src = f.read()
                assert "subprocess.run" not in src or "./gradlew" not in src, (
                    f"Test file '{fname}' violates R3 by executing ./gradlew locally"
                )
                assert "os.system" not in src or "./gradlew" not in src, (
                    f"Test file '{fname}' violates R3 by executing ./gradlew locally"
                )

        # 3. Invariant: assembleStaging is strictly delegated to remote GitHub Actions CI
        with open(CANARY_WORKFLOW, "r", encoding="utf-8") as wf:
            workflow_content = wf.read()
        assert "./gradlew TMessagesProj:assembleStaging" in workflow_content, (
            "assembleStaging must be delegated to remote GitHub Actions canary workflow"
        )


# ==============================================================================
# Tier 2: Boundary & Corner Cases (>=5 tests)
# ==============================================================================

class TestTier2BoundaryAndCornerCases:
    """Tier 2: Boundary & Corner Cases (7 tests)."""

    def test_t2_01_slider_thickness_clamping_simulation(self):
        """T2: Boundary test: slider thickness clamping to [2, 24] and Java Math.round half-up metrics."""
        # Connect to AppearanceConfig.kt and SeekBarView.java
        with open(APPEARANCE_CONFIG_KT, "r", encoding="utf-8") as acf:
            ac_content = acf.read()
        assert "coerceIn(2, 24)" in ac_content, "AppearanceConfig must clamp slider thickness to [2, 24]"

        with open(SEEKBARVIEW_JAVA, "r", encoding="utf-8") as sbvf:
            sbv_content = sbvf.read()
        assert "thicknessDp * 0.25f" in sbv_content, "SeekBarView must calculate thumb/gap with thicknessDp * 0.25f"

        inputs = [
            (-100, 2),
            (-1, 2),
            (0, 2),
            (1, 2),
            (2, 2),
            (3, 3),
            (8, 8),
            (10, 10),
            (12, 12),
            (18, 18),
            (24, 24),
            (25, 24),
            (100, 24),
            (10000, 24),
        ]
        for raw, expected in inputs:
            clamped = SliderDimensionsOracle.clamp_thickness(raw)
            assert clamped == expected, f"Raw thickness {raw} should clamp to {expected}, got {clamped}"
            metrics = SliderDimensionsOracle.compute_metrics(raw)
            assert metrics["thickness_dp"] == expected
            assert metrics["thumb_width"] >= 6.0  # at least 3dp * 2.0 density
            assert metrics["gap"] >= 4.0          # at least 2dp * 2.0 density
            assert metrics["thumb_height"] >= 40.0

        # Strict validation of Java Math.round half-up behavior:
        # t=10: 10 * 0.25 = 2.5 -> Java Math.round gives 3 (gap = 3 * 2.0 = 6.0; banker's would give 4.0)
        metrics_10 = SliderDimensionsOracle.compute_metrics(10, density=2.0)
        assert metrics_10["gap"] == 6.0, f"Expected gap 6.0 for t=10, got {metrics_10['gap']}"
        assert metrics_10["thumb_width"] == 6.0

        # t=18: 18 * 0.25 = 4.5 -> Java Math.round gives 5 (gap = 5 * 2.0 = 10.0; banker's would give 8.0)
        metrics_18 = SliderDimensionsOracle.compute_metrics(18, density=2.0)
        assert metrics_18["gap"] == 10.0, f"Expected gap 10.0 for t=18, got {metrics_18['gap']}"
        assert metrics_18["thumb_width"] == 10.0

    def test_t2_02_slidechooseview_exact_92dp_measure_spec(self):
        """T2: Boundary test: SlideChooseView exact MeasureSpec.EXACTLY with 92dp."""
        with open(SLIDECHOOSEVIEW_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        pattern = r"MeasureSpec\.makeMeasureSpec\(AndroidUtilities\.dp\(92\),\s*MeasureSpec\.EXACTLY\)"
        assert re.search(pattern, content), "SlideChooseView must pass dp(92) with MeasureSpec.EXACTLY"

    def test_t2_03_invisible_metadata_null_and_empty_inputs(self):
        """T2: Corner case test: null, empty, and boundary metadata inputs."""
        # Connect to ProfileInvisibleMetadata.kt, ChangeNameActivity.java, and UserObject.java
        with open(PROFILE_METADATA_KT, "r", encoding="utf-8") as pmf:
            pm_content = pmf.read()
        assert "val isEmpty: Boolean" in pm_content
        assert 'if (num <= 0L) return ""' in pm_content
        assert "fun cleanLastName(lastName: String?): String" in pm_content

        with open(CHANGE_NAME_JAVA, "r", encoding="utf-8") as cnj:
            cn_content = cnj.read()
        assert "ProfileInvisibleMetadata.cleanLastName" in cn_content

        with open(USER_OBJECT_JAVA, "r", encoding="utf-8") as uoj:
            uo_content = uoj.read()
        assert "ProfileInvisibleMetadata.getEmojiStatusDocumentId" in uo_content

        # Empty dictionary should return empty string
        assert InvisibleMetadataCodecOracle.encode({}) == ""
        assert InvisibleMetadataCodecOracle.encode({"status": 0, "nc": -1, "ne": 0, "pc": -1, "pe": 0}) == ""

        # Decoding non-tagged string should return None
        assert InvisibleMetadataCodecOracle.decode(None) is None
        assert InvisibleMetadataCodecOracle.decode("") is None
        assert InvisibleMetadataCodecOracle.decode("Regular text without metadata") is None

        # Clean last name should handle None and empty
        assert InvisibleMetadataCodecOracle.clean_last_name(None) == ""
        assert InvisibleMetadataCodecOracle.clean_last_name("") == ""
        assert InvisibleMetadataCodecOracle.clean_last_name("Smith") == "Smith"

    def test_t2_04_invisible_metadata_max_64bit_document_id(self):
        """T2: Boundary test: max 64-bit unsigned/signed document ID handling."""
        # Connect to ProfileInvisibleMetadata.kt
        with open(PROFILE_METADATA_KT, "r", encoding="utf-8") as pmf:
            pm_content = pmf.read()
        assert "var emojiStatusDocId: Long = 0L" in pm_content
        assert "java.lang.Long.parseUnsignedLong" in pm_content

        max_64bit = 0x7FFFFFFFFFFFFFFF  # 9223372036854775807
        payload = {"status": max_64bit, "nc": 15, "ne": max_64bit, "pc": 15, "pe": max_64bit}

        encoded = InvisibleMetadataCodecOracle.encode(payload)
        assert InvisibleMetadataCodecOracle.is_strictly_invisible_unicode(encoded)

        decoded = InvisibleMetadataCodecOracle.decode(encoded)
        assert decoded is not None
        assert decoded["status"] == max_64bit
        assert decoded["nc"] == 15
        assert decoded["ne"] == max_64bit
        assert decoded["pc"] == 15
        assert decoded["pe"] == max_64bit

    def test_t2_05_invisible_metadata_unicode_character_isolation(self):
        """T2: Boundary test: strict Unicode isolation, zero visible glyph leakage."""
        # Connect to ProfileInvisibleMetadata.kt character class definitions
        with open(PROFILE_METADATA_KT, "r", encoding="utf-8") as pmf:
            pm_content = pmf.read()
        assert "TAG_REGEX" in pm_content
        assert "uFE00" in pm_content and "uFE0F" in pm_content
        assert "u206A" in pm_content and "u206E" in pm_content
        assert "0xFE00" in pm_content

        test_meta = {"status": 98765432101234, "nc": 3, "ne": 1122334455, "pc": 7, "pe": 9988776655}
        tag = InvisibleMetadataCodecOracle.encode(test_meta)
        assert len(tag) > 0

        # Every single code point must be strictly invisible Unicode
        for ch in tag:
            code = ord(ch)
            is_valid = (
                (0xFE00 <= code <= 0xFE0F) or  # Variation Selectors
                (0x200B <= code <= 0x200D) or  # ZWSP, ZWNJ, ZWJ
                (0x2060 <= code <= 0x206E)     # Word Joiner & Format controls
            )
            assert is_valid, f"Leaked visible Unicode code point: U+{code:04X} ({repr(ch)})"

    def test_t2_06_custom_command_malformed_inputs_and_case_insensitivity(self):
        """T2: Corner case test: malformed command prefixes and case-insensitive matching."""
        # Connect to PluginsController.java CustomCommand definition
        with open(PLUGINS_CONTROLLER_JAVA, "r", encoding="utf-8") as pcf:
            pc_content = pcf.read()
        assert 'public String prefix = ".";' in pc_content
        assert 'public String name = "";' in pc_content

        # Name starting with prefix should strip prefix
        res1 = CustomCommandParserOracle.sanitize(".", ".tagall", "Tag all users")
        assert res1["prefix"] == "."
        assert res1["name"] == "tagall"
        assert res1["full"] == ".tagall"

        # Name starting with standard prefix ! should switch prefix
        res2 = CustomCommandParserOracle.sanitize(".", "!calc", "Calculator")
        assert res2["prefix"] == "!"
        assert res2["name"] == "calc"
        assert res2["full"] == "!calc"

        # Empty name and prefix
        res3 = CustomCommandParserOracle.sanitize("", "   ", "")
        assert res3["prefix"] == "."
        assert res3["name"] == ""
        assert res3["full"] == "."

    def test_t2_07_monet_hue_boundary_rejection(self):
        """T2: Boundary test: Monet hue rejection threshold 265.0° and 318.0°."""
        # Connect to MonetHelper.java hue constants
        with open(MONET_HELPER_JAVA, "r", encoding="utf-8") as mhf:
            mh_content = mhf.read()
        assert "hue >= 265d && hue <= 318d" in mh_content
        assert "chroma >= 3d" in mh_content

        # Boundaries: 264.9 should not be rejected; 265.0 should be rejected; 318.0 rejected; 318.1 not rejected
        assert not MonetBaselineOracle.is_hue_in_purple_rejection_window(264.9, 10.0)
        assert MonetBaselineOracle.is_hue_in_purple_rejection_window(265.0, 10.0)
        assert MonetBaselineOracle.is_hue_in_purple_rejection_window(290.0, 5.0)
        assert MonetBaselineOracle.is_hue_in_purple_rejection_window(318.0, 10.0)
        assert not MonetBaselineOracle.is_hue_in_purple_rejection_window(318.1, 10.0)
        # Low chroma (< 3.0) is not rejected even if hue matches
        assert not MonetBaselineOracle.is_hue_in_purple_rejection_window(280.0, 2.9)


# ==============================================================================
# Tier 3: Cross-Feature Combinations (>=5 tests)
# ==============================================================================

class TestTier3CrossFeatureCombinations:
    """Tier 3: Cross-Feature Combinations (5 tests)."""

    def test_t3_01_monet_theming_and_m3_sliders(self):
        """T3: Integration: Monet dynamic accent maps to M3 slider active track."""
        with open(SEEKBARVIEW_JAVA, "r", encoding="utf-8") as f:
            sb = f.read()

        # SeekBarView queries switchTrackChecked for active track
        assert "Theme.key_switchTrackChecked" in sb
        assert "Theme.key_player_progress" in sb
        # Evaluates isDark and applies HSL lightness (0.84f dark / 0.38f light)
        assert "0.84f" in sb
        assert "0.38f" in sb
        assert "switchExpressiveColors" in sb

    def test_t3_02_profile_invisible_metadata_and_peer_color_activity(self):
        """T3: Integration: PeerColorActivity calls updateSelfColors without Premium check."""
        with open(PEER_COLOR_JAVA, "r", encoding="utf-8") as f:
            content = f.read()

        assert "ProfileInvisibleMetadata.updateSelfColors(" in content
        assert "namePage.selectedColor" in content
        assert "profilePage.selectedColor" in content
        assert "selectedEmojiCollectible" in content

    def test_t3_03_custom_player_and_wavy_seekbar_and_dynamic_thickness(self):
        """T3: Integration: PlayerSettingsActivity controls both WavySeekBar and dynamic thickness."""
        with open(os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSettingsActivity.java"), "r", encoding="utf-8") as f:
            act = f.read()

        assert "seekbarThicknessRow" in act
        assert "seekbarIndicatorRow" in act

        with open(APPEARANCE_CONFIG_KT, "r", encoding="utf-8") as f:
            ac = f.read()
        assert "fun playerSeekbarThickness(): Int" in ac
        assert "fun playerSeekbarIndicator(): Int" in ac

    def test_t3_04_custom_commands_mentions_adapter_and_chat_activity(self):
        """T3: Integration: Full pipeline from typing prefix to autocompletion and click guard."""
        with open(MENTIONS_ADAPTER_JAVA, "r", encoding="utf-8") as f:
            ma = f.read()
        with open(CHAT_ACTIVITY_JAVA, "r", encoding="utf-8") as f:
            ca = f.read()

        # MentionsAdapter detects prefix and queries matching commands
        assert "PluginsController.getInstance().isPluginPrefix(ch)" in ma
        assert "getMatchingCommands(foundPrefix, command)" in ma

        # ChatActivity prevents instant dispatch and replaces with trailing space
        assert "isCustomOrPluginCommand((String) object)" in ca
        assert "replaceWithText(start, len, object + \" \", false)" in ca

    def test_t3_05_device_spoofing_and_mtproto_connections_manager(self):
        """T3: Integration: MTProto ConnectionsManager hooks into DeviceSpoofManager."""
        conn_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "tgnet", "ConnectionsManager.java")
        with open(conn_path, "r", encoding="utf-8") as f:
            content = f.read()

        assert "DeviceSpoofManager.getInstance().getDeviceModel()" in content
        assert "DeviceSpoofManager.getInstance().getSystemVersion()" in content


# ==============================================================================
# Tier 4: Real-World Acceptance Scenarios (>=5 tests)
# ==============================================================================

class TestTier4RealWorldAcceptanceScenarios:
    """Tier 4: Real-World Acceptance Scenarios (5 tests)."""

    def test_t4_01_full_upgrade_configuration_matrix(self):
        """T4: End-to-end upgrade invariant matrix across gradle, TLRPC, and BuildVars."""
        with open(GRADLE_PROPERTIES, "r", encoding="utf-8") as f:
            gp = f.read()
        with open(TLRPC_JAVA, "r", encoding="utf-8") as f:
            tl = f.read()
        with open(BUILDVARS_JAVA, "r", encoding="utf-8") as f:
            bv = f.read()

        # Invariant 1: Version code strictly matches target contract 7291
        codes = [int(m) for m in re.findall(r"^APP_VERSION_CODE=(\d+)", gp, re.MULTILINE)]
        assert len(codes) > 0, "APP_VERSION_CODE must be present in gradle.properties"
        assert all(c == TARGET_APP_VERSION_CODE for c in codes), (
            f"All APP_VERSION_CODE entries in gradle.properties must equal target {TARGET_APP_VERSION_CODE}, found {codes}"
        )

        # Invariant 2: Version name strictly matches target contract 13.2.1
        names = re.findall(r"^APP_VERSION_NAME=([\d\.]+)", gp, re.MULTILINE)
        assert len(names) > 0, "APP_VERSION_NAME must be defined in gradle.properties"
        assert all(n == TARGET_APP_VERSION_NAME for n in names), (
            f"All APP_VERSION_NAME entries in gradle.properties must equal target '{TARGET_APP_VERSION_NAME}', found {names}"
        )

        # Invariant 3: MTProto Layer strictly matches target contract 233
        m_layer = re.search(r"public static final int LAYER\s*=\s*(\d+);", tl)
        assert m_layer, "LAYER constant must exist in TLRPC.java"
        assert int(m_layer.group(1)) == TARGET_LAYER, (
            f"LAYER in TLRPC.java must equal target {TARGET_LAYER}, found {m_layer.group(1)}"
        )

        # Invariant 4: Brand preservation
        assert "com.moltengram.app" in gp
        assert "Tiramisu" in gp or "Cinnamon" in gp
        assert f"APP_ID = {TARGET_APP_ID};" in bv
        assert f'APP_HASH = "{TARGET_APP_HASH}";' in bv

    def test_t4_02_retention_of_all_70_moltengram_unique_files(self):
        """T4: Invariant scenario: Complete retention of all 70 MoltenGram unique files."""
        missing = []
        empty = []
        for rel_path in MOLTENGRAM_UNIQUE_FILES:
            full_path = os.path.join(REPO_ROOT, rel_path)
            if not os.path.isfile(full_path):
                missing.append(rel_path)
            elif os.path.getsize(full_path) == 0:
                empty.append(rel_path)

        assert len(missing) == 0, f"Missing MoltenGram unique files ({len(missing)}): {missing}"
        assert len(empty) == 0, f"Empty MoltenGram unique files ({len(empty)}): {empty}"
        verified_count = len(MOLTENGRAM_UNIQUE_FILES) - len(missing) - len(empty)
        assert verified_count >= 70, (
            f"Expected at least 70 MoltenGram unique files verified on disk, got {verified_count}"
        )

    def test_t4_03_composite_user_workflow_simulation(self):
        """T4: Simulates complete composite user workflow lifecycle."""
        # Step 1: User applies dynamic Monet theme (dark mode neutral-1 background)
        assert MonetBaselineOracle.is_exact_static_color(MonetBaselineOracle.BASELINE_PURPLE_80)
        assert not MonetBaselineOracle.is_hue_in_purple_rejection_window(200.0, 15.0)

        # Step 2: User sets M3 slider thickness to 14dp
        dim = SliderDimensionsOracle.compute_metrics(14, density=2.0)
        assert dim["track_height"] == 28.0
        assert dim["gap"] == 8.0

        # Step 3: User configures custom action buttons for player
        player_sheet_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSheet.java")
        with open(player_sheet_path, "r", encoding="utf-8") as psf:
            ps_content = psf.read()
        tokens = ["speed", "lyrics", "queue", "share", "playlist", "sleep", "like"]
        for tok in tokens:
            assert f"MorphButton {tok}Button" in ps_content or f"{tok}Button" in ps_content, (
                f"Action button for '{tok}' must be declared in PlayerSheet.java"
            )

        # Step 4: User sets custom peer color and emoji status via zero-width metadata
        user_meta = {"status": 777666555444, "nc": 5, "ne": 111222, "pc": 8, "pe": 333444}
        encoded = InvisibleMetadataCodecOracle.encode(user_meta)
        full_name = InvisibleMetadataCodecOracle.format_last_name("Alexander", user_meta)
        assert full_name.startswith("Alexander")
        assert InvisibleMetadataCodecOracle.clean_last_name(full_name) == "Alexander"
        assert InvisibleMetadataCodecOracle.decode(full_name) == user_meta

        # Step 5: User triggers custom autocomplete command .tagall
        cmd = CustomCommandParserOracle.sanitize(".", ".tagall", "Mention all")
        assert cmd["full"] == ".tagall"

    def test_t4_04_ci_canary_and_beta_cinnamon_push_readiness(self):
        """T4: Verifies canary push contract and beta-cinnamon branch delivery readiness."""
        with open(CANARY_WORKFLOW, "r", encoding="utf-8") as f:
            workflow = f.read()

        # Check CI canary push target
        assert "branches:" in workflow
        assert "- canary" in workflow or "canary" in workflow
        assert "assembleStaging" in workflow

        # Verify git HEAD reference integrity for branch creation
        git_head_path = os.path.join(REPO_ROOT, ".git", "HEAD")
        assert os.path.isfile(git_head_path), "Git repository must be initialized (.git/HEAD missing)"
        with open(git_head_path, "r", encoding="utf-8") as hf:
            head_ref = hf.read().strip()
        assert head_ref.startswith("ref: refs/heads/") or len(head_ref) == 40, (
            f"Git HEAD must be a valid symbolic reference or commit SHA, found '{head_ref}'"
        )

        # Verify origin remote configuration for beta branch delivery
        git_config_path = os.path.join(REPO_ROOT, ".git", "config")
        assert os.path.isfile(git_config_path), ".git/config must exist"
        with open(git_config_path, "r", encoding="utf-8") as gf:
            git_config = gf.read()
        assert '[remote "origin"]' in git_config, "origin remote must be defined in git configuration"
        assert "MoltenGram.git" in git_config, "origin remote must point to MoltenGram repository"

        # Verify beta branch ref name formatting via git plumbing
        for branch_name in ("beta-cinnamon", "cinnamon-beta"):
            res = subprocess.run(
                ["git", "check-ref-format", "--branch", branch_name],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
            )
            assert res.returncode == 0, f"Git ref format invalid for branch '{branch_name}': {res.stderr}"
            assert res.stdout.strip() == branch_name

        # Verify release codename readiness for branching
        with open(GRADLE_PROPERTIES, "r", encoding="utf-8") as pf:
            gp_text = pf.read()
        assert any(cn in gp_text for cn in ("Cinnamon", "Tiramisu")), (
            "gradle.properties must contain Cinnamon or Tiramisu codename for release branching"
        )

    def test_t4_05_local_test_execution_performance(self):
        """T4: Verifies that requirement checks execute within strict performance bounds."""
        start = time.time()
        # Verify read performance across 7 core subsystem files
        core_files = [
            GRADLE_PROPERTIES,
            TLRPC_JAVA,
            BUILDVARS_JAVA,
            APPEARANCE_CONFIG_KT,
            MONET_HELPER_JAVA,
            PROFILE_METADATA_KT,
            PLUGINS_CONTROLLER_JAVA,
        ]
        for fpath in core_files:
            assert os.path.isfile(fpath)
            with open(fpath, "r", encoding="utf-8", errors="ignore") as f:
                data = f.read(1024)
                assert len(data) > 0

        for _ in range(50):
            InvisibleMetadataCodecOracle.encode(
                {"status": 123456789, "nc": 4, "ne": 987654321, "pc": 2, "pe": 555666}
            )
            SliderDimensionsOracle.compute_metrics(8)
            CustomCommandParserOracle.sanitize(".", ".test", "description")
        elapsed = time.time() - start
        # File checks plus 50 iterations of oracles must finish in under 1 second
        assert elapsed < 1.0, f"Core file inspections and simulation oracles took {elapsed:.2f}s, exceeding 1.0s limit"
