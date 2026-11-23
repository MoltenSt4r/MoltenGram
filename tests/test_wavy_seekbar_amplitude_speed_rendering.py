import os
import re
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main")
TMESSAGES_JAVA = os.path.join(TMESSAGES, "java")
TMESSAGES_KOTLIN = os.path.join(TMESSAGES, "kotlin")
TMESSAGES_RES = os.path.join(TMESSAGES, "res")

def test_wavy_seekbar_amplitude_and_speed_config():
    cfg_path = os.path.join(TMESSAGES_KOTLIN, "app", "exteraless", "appearance", "AppearanceConfig.kt")
    with open(cfg_path, "r", encoding="utf-8") as f:
        cfg = f.read()

    assert "val playerWaveAmplitude = addConfig(\"OEPlayerWaveAmplitude\"" in cfg
    assert "fun playerWaveAmplitude(): Int" in cfg
    assert "val playerWaveSpeed = addConfig(\"OEPlayerWaveSpeed\"" in cfg
    assert "fun playerWaveSpeed(): Int" in cfg
    assert "playerWaveAmplitude, playerWaveSpeed" in cfg

def test_player_style_wave_getters_and_reset():
    style_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerStyle.java")
    with open(style_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "waveAmplitude()" in content
    assert "waveSpeed()" in content
    assert "AppearanceConfig.playerWaveAmplitude" in content
    assert "AppearanceConfig.playerWaveSpeed" in content

def test_wavy_seekbar_rendering_no_underlying_track_and_clean_caps():
    seekbar_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "WavySeekBar.java")
    with open(seekbar_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify wave speed and amplitude configurable in WavySeekBar
    assert "setWaveSpeed" in content
    assert "playerWaveAmplitude" in content
    assert "playerWaveSpeed" in content

    # Verify no redundant gray track underneath the wavy played portion
    # The inactive background track must only be drawn from unplayedStart onwards, not from left!
    assert "canvas.drawLine(left, cy, stopX, cy, trackPaint)" not in content
    assert "canvas.drawLine(unplayedStart, cy, stopX, cy, trackPaint)" in content

    # Verify smooth taper/fade at wave start and wave end for perfect round caps aligned with baseline Y center
    assert "smoothFade" in content
    assert "waveStart = left + capHalf" in content
    assert "TAU * (cx - waveStart) / lambda - phase" in content or "smoothFade" in content

def test_player_style_editor_wave_controls():
    editor_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerStyleEditor.java")
    with open(editor_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "R.string.OEPlayerWaveAmplitude" in content
    assert "R.string.OEPlayerWaveSpeed" in content
    assert "AppearanceConfig::playerWaveAmplitude" in content
    assert "AppearanceConfig::playerWaveSpeed" in content
    assert "waveAmpSlider" in content
    assert "waveSpeedSlider" in content

def test_player_sheet_apply_style_updates_wave_amplitude_and_speed():
    sheet_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSheet.java")
    with open(sheet_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "seekBar.setMaxAmplitude(PlayerStyle.wavy() ? dp(PlayerStyle.waveAmplitude()) : 0);" in content
    assert "seekBar.setWaveSpeed(PlayerStyle.waveSpeed());" in content

def test_player_settings_activity_wave_rows():
    act_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerSettingsActivity.java")
    with open(act_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "seekbarWaveAmplitudeRow" in content
    assert "seekbarWaveSpeedRow" in content
    assert "AppearanceConfig.playerWaveAmplitude" in content
    assert "AppearanceConfig.playerWaveSpeed" in content

def test_player_resources_strings():
    values_path = os.path.join(TMESSAGES_RES, "values", "strings_oe_player.xml")
    with open(values_path, "r", encoding="utf-8") as f:
        val_content = f.read()
    assert "OEPlayerWaveAmplitude" in val_content
    assert "OEPlayerWaveSpeed" in val_content

    values_ru_path = os.path.join(TMESSAGES_RES, "values-ru-rRU", "strings_oe_player.xml")
    with open(values_ru_path, "r", encoding="utf-8") as f:
        val_ru_content = f.read()
    assert "Высота волны" in val_ru_content
    assert "Скорость анимации" in val_ru_content
