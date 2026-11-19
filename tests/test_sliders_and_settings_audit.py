import glob
import os
import re
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
TMESSAGES_SRC = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main")
JAVA_ROOT = os.path.join(TMESSAGES_SRC, "java")
KOTLIN_ROOT = os.path.join(TMESSAGES_SRC, "kotlin")
RES_ROOT = os.path.join(TMESSAGES_SRC, "res")


def _read(rel_path):
    path = os.path.join(REPO_ROOT, rel_path)
    assert os.path.isfile(path), f"Missing file: {rel_path}"
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def test_md3_end_stop_dot_size_wavy_and_seekbar():
    wavy = _read("TMessagesProj/src/main/java/app/exteraless/player/WavySeekBar.java")
    assert "float stopX = right - capHalf;" in wavy
    assert "dpf2(2f)" in wavy
    assert "strokeW * 0.4f" not in wavy

    seekbar = _read("TMessagesProj/src/main/java/org/telegram/ui/Components/SeekBarView.java")
    assert "float endDotX = right - trackStroke * 0.5f;" in seekbar
    assert "AndroidUtilities.dpf2(2f)" in seekbar
    assert "trackStroke * 0.38f" not in seekbar


def test_player_style_editor_uses_clean_md3_slider():
    editor = _read("TMessagesProj/src/main/java/app/exteraless/player/PlayerStyleEditor.java")
    assert "Md3SliderView" in editor
    assert "WavySeekBar" not in editor
    assert "dpf2(2f)" in editor


def test_switch_size_config_scaling_and_appearance_row():
    config = _read("TMessagesProj/src/main/kotlin/app/exteraless/appearance/AppearanceConfig.kt")
    assert 'addConfig("OEAppearanceSwitchSize", ConfigItem.configTypeInt, 100)' in config
    assert "fun switchSize(): Int" in config
    assert "coerceIn(70, 130)" in config

    switch_java = _read("TMessagesProj/src/main/java/org/telegram/ui/Components/Switch.java")
    assert "AppearanceConfig.switchSize()" in switch_java
    assert "drawScaledContent" in switch_java

    appearance = _read("TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraAppearanceActivity.java")
    assert 'md3SwitchSizeRow = addRow("md3SwitchSize");' in appearance
    assert "TYPE_SWITCH_SIZE" in appearance
    assert "R.string.OEAppearanceSwitchSize" in appearance
    assert "AppearanceConfig.switchSize.setConfigInt" in appearance


def test_settings_search_indexing_deeplinks_and_subgrouping():
    helper = _read("TMessagesProj/src/main/java/tw/nekomimi/nekogram/helpers/SettingsHelper.java")
    assert 'HOST_EXTERASETTINGS = "exterasettings"' in helper
    assert "SEARCH_TITLE_ALIASES" in helper
    for cls in (
        "OpenExteraCloudActivity",
        "OpenClipSettingsActivity",
        "VoskSettingsActivity",
        "WeatherSettingsActivity",
        "IconPacksActivity",
        "GhostModeActivity",
        "LiquidGlassSettingsActivity",
    ):
        assert cls in helper, f"{cls} must be indexed/handled in SettingsHelper.java"

    registry = _read("TMessagesProj/src/main/java/app/exteraless/settings/utils/SettingsRegistry.java")
    assert "categoriesIcons" in registry
    for cls in (
        "OpenExteraToolsActivity",
        "OpenExteraCloudActivity",
        "LiquidGlassSettingsActivity",
        "PluginStoreSettingsActivity",
        "PillStackSettingsActivity",
    ):
        assert cls in registry, f"{cls} must be registered in SettingsRegistry.java"

    general = _read("TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraGeneralActivity.java")
    assert "archiveMoreRow" in general and "notificationsMoreRow" in general and "collapsibleGroups" in general

    chats = _read("TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraChatsActivity.java")
    assert "chatsMoreRow" in chats and "messagesMoreRow" in chats and "collapsibleGroups" in chats

    other = _read("TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraOtherActivity.java")
    assert "experimentalMoreRow" in other and "collapsibleGroups" in other

    player = _read("TMessagesProj/src/main/java/app/exteraless/player/PlayerSettingsActivity.java")
    assert "appearanceMoreRow" in player and "lyricsMoreRow" in player and "collapsibleGroups" in player
    assert "getSearchGuid()" in player

    glass = _read("TMessagesProj/src/main/java/app/exteraless/settings/LiquidGlassSettingsActivity.java")
    assert "ID_OPTICAL_MORE" in glass and "ID_ELSEWHERE_MORE" in glass and "asShadowCollapseButton" in glass


def test_liquid_glass_shader_fp32_and_central_diff():
    agsl = _read("TMessagesProj/src/main/res/raw/solegram_glass_shader.agsl")
    assert "float sdfRect(float2 p, float4 r)" in agsl
    assert "float2 grad = float2(sdX, sdY);" in agsl
    assert "sdfRect(p + float2(0.5, 0.0), radius) - sdfRect(p - float2(0.5, 0.0), radius)" in agsl
    assert "half sdfRect" not in agsl
    assert "half3 normal" not in agsl


def test_no_duplicate_xml_resources_in_values():
    for folder in ("values", "values-ru-rRU"):
        seen = {}
        dups = []
        for path in sorted(glob.glob(os.path.join(RES_ROOT, folder, "*.xml"))):
            root = ET.parse(path).getroot()
            for child in root:
                name = child.attrib.get("name")
                if name and child.tag in ("string", "plurals", "string-array", "color", "dimen", "bool", "integer"):
                    key = (child.tag, name)
                    if key in seen:
                        dups.append((key, seen[key], path))
                    else:
                        seen[key] = path
        assert not dups, f"Duplicate XML resources in {folder}: {dups}"
