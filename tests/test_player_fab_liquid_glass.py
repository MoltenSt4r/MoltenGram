import os
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES_JAVA = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "java")
TMESSAGES_KOTLIN = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "kotlin")
TMESSAGES_RES = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "res")


def test_liquid_glass_in_appearance_config():
    config_path = os.path.join(TMESSAGES_KOTLIN, "app", "exteraless", "appearance", "AppearanceConfig.kt")
    assert os.path.exists(config_path), "AppearanceConfig.kt must exist"
    with open(config_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "liquidGlassNavBar" in content, "liquidGlassNavBar config missing"
    assert "liquidGlassFab" in content, "liquidGlassFab config missing"
    assert "fun liquidGlassNavBar(): Boolean" in content, "liquidGlassNavBar() accessor missing"
    assert "fun liquidGlassFab(): Boolean" in content, "liquidGlassFab() accessor missing"


def test_liquid_glass_in_ios_appearance_section():
    activity_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "settings", "OpenExteraAppearanceActivity.java")
    assert os.path.exists(activity_path), "OpenExteraAppearanceActivity.java must exist"
    with open(activity_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "iosLiquidGlassNavBarRow" in content, "iosLiquidGlassNavBarRow missing"
    assert "iosLiquidGlassFabRow" in content, "iosLiquidGlassFabRow missing"
    assert "IOS_STYLE_COUNT = 4" in content, "IOS_STYLE_COUNT should be 4 with liquid glass items"
    assert "OEAppearanceLiquidGlassNavBar" in content, "OEAppearanceLiquidGlassNavBar string reference missing"
    assert "OEAppearanceLiquidGlassFab" in content, "OEAppearanceLiquidGlassFab string reference missing"


def test_liquid_glass_strings_in_resources():
    res_en = os.path.join(TMESSAGES_RES, "values", "strings_oe_appearance.xml")
    res_ru = os.path.join(TMESSAGES_RES, "values-ru-rRU", "strings_oe_appearance.xml")

    with open(res_en, "r", encoding="utf-8") as f:
        en_content = f.read()
    assert "OEAppearanceLiquidGlassNavBar" in en_content
    assert "OEAppearanceLiquidGlassFab" in en_content

    with open(res_ru, "r", encoding="utf-8") as f:
        ru_content = f.read()
    assert "OEAppearanceLiquidGlassNavBar" in ru_content
    assert "OEAppearanceLiquidGlassFab" in ru_content


def test_voice_changer_preview_replay():
    vc_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "settings", "OpenExteraVoiceChangerActivity.java")
    assert os.path.exists(vc_path), "OpenExteraVoiceChangerActivity.java must exist"
    with open(vc_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "TEST_STATE_RECORDING" in content, "TEST_STATE_RECORDING missing"
    assert "TEST_STATE_PLAYING" in content, "TEST_STATE_PLAYING missing"
    assert "finishRecordingEarly" in content, "finishRecordingEarly missing"
    assert "stopRecordingEarly" in content, "stopRecordingEarly flag missing"


def test_fab_search_discoverability():
    sh_path = os.path.join(TMESSAGES_JAVA, "tw", "nekomimi", "nekogram", "helpers", "SettingsHelper.java")
    assert os.path.exists(sh_path), "SettingsHelper.java must exist"
    with open(sh_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "Md3FabAppearanceActivity" in content, "Md3FabAppearanceActivity must be indexed in settings search"
    assert "FAB (Плавающая кнопка)" in content, "FAB keyword missing from search results"


def test_player_customize_opens_native_settings():
    ps_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSheet.java")
    assert os.path.exists(ps_path), "PlayerSheet.java must exist"
    with open(ps_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "PlayerSettingsActivity" in content, "PlayerSettingsActivity reference missing in PlayerSheet"
    assert "OEPlayerCustomize" in content, "OEPlayerCustomize missing in PlayerSheet"


def test_fab_no_plugin_options():
    fab_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "components", "Md3SpeedDialView.java")
    assert os.path.exists(fab_path), "Md3SpeedDialView.java must exist"
    with open(fab_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert 'new String[]{"deeplink", "chat", "setting"}' in content, "Custom action types should only include deeplink, chat, setting"
    assert '"Команда или функция плагина"' not in content, "Plugin placeholder should be removed"
