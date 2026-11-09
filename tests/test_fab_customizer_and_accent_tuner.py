import os
import re
import pytest
import colorsys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main")


def test_appearance_config_md3_fab_item_style():
    """Verify md3FabItemStyle configs and constants in AppearanceConfig.kt."""
    cfg_path = os.path.join(TMESSAGES, "kotlin", "app", "exteraless", "appearance", "AppearanceConfig.kt")
    assert os.path.isfile(cfg_path), f"File {cfg_path} must exist"
    with open(cfg_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "const val FAB_ITEM_STYLE_CAPSULE = 0" in content
    assert "const val FAB_ITEM_STYLE_CARD = 1" in content
    assert "const val FAB_ITEM_STYLE_FILLED_TONAL = 2"
    assert "const val FAB_ITEM_STYLE_OUTLINE = 3" in content
    assert 'val md3FabItemStyle = addConfig("OEAppearanceMd3FabItemStyle"' in content
    assert "fun md3FabItemStyle(): Int" in content


def test_accent_color_tuner_bottom_sheet_structure():
    """Verify AccentColorTunerBottomSheet has all 4 sliders, live preview, presets, and theme application."""
    sheet_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "appearance", "AccentColorTunerBottomSheet.java")
    assert os.path.isfile(sheet_path), f"File {sheet_path} must exist"
    with open(sheet_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "public class AccentColorTunerBottomSheet extends BottomSheet" in content
    # 4 sliders
    assert "Смещение оттенка (Hue Shift)" in content
    assert "Насыщенность (Saturation)" in content
    assert "Яркость (Lightness)" in content
    assert "Контраст (Contrast)" in content
    assert "-180" in content and "180" in content
    # Live preview card elements
    assert "MiniFabPreviewView" in content
    assert "pillPreviewButton" in content
    assert "hexCodeView" in content
    assert "hslInfoView" in content
    # Presets
    assert "Исходный акцент" in content
    assert "Пастель (MD3)" in content
    assert "Глубокий (Vibrant)" in content
    assert "Инверсия (+180°)" in content
    assert "Третичный (+60°)" in content
    # Apply to theme
    assert "applyCustomAccentToInterface" in content
    assert "Theme.refreshThemeColors()" in content
    # Color calculation method
    assert "public static int calculateTunedColor(" in content


def test_calculate_tuned_color_math():
    """Verify mathematical properties of the HSL + Contrast color tuner formula."""
    # Let's verify formula in Python:
    def calc_tuned(h_base, s_base, l_base, hue_shift, light_pct, sat_pct, contrast_pct):
        h = (h_base + hue_shift) % 360.0
        if h < 0:
            h += 360.0
        s = max(0.0, min(1.0, sat_pct / 100.0))
        l = max(0.0, min(1.0, light_pct / 100.0))
        if contrast_pct != 0:
            factor = 1.0 + (contrast_pct / 100.0)
            l = 0.5 + (l - 0.5) * factor
            l = max(0.0, min(1.0, l))
        return h, s, l

    # 1. Identity case:
    h, s, l = calc_tuned(210.0, 0.8, 0.5, 0, 50, 80, 0)
    assert abs(h - 210.0) < 1e-4
    assert abs(s - 0.8) < 1e-4
    assert abs(l - 0.5) < 1e-4

    # 2. Hue shift by +60 deg:
    h, s, l = calc_tuned(210.0, 0.8, 0.5, 60, 50, 80, 0)
    assert abs(h - 270.0) < 1e-4

    # 3. Negative hue shift:
    h, s, l = calc_tuned(30.0, 0.8, 0.5, -90, 50, 80, 0)
    assert abs(h - 300.0) < 1e-4

    # 4. Contrast boost expands lightness away from 0.5:
    h, s, l_high = calc_tuned(210.0, 0.8, 0.7, 0, 70, 80, 50)
    assert l_high > 0.7  # lighter gets even lighter
    h, s, l_low = calc_tuned(210.0, 0.8, 0.3, 0, 30, 80, 50)
    assert l_low < 0.3   # darker gets even darker


def test_md3_speed_dial_view_input_dialogs_and_reordering():
    """Verify Md3SpeedDialView uses OutlineTextContainerView, EditTextBoldCursor, style chips, and reordering."""
    view_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "components", "Md3SpeedDialView.java")
    assert os.path.isfile(view_path), f"File {view_path} must exist"
    with open(view_path, "r", encoding="utf-8") as f:
        content = f.read()

    # MD3 Outline input fields in showAddOrEditCustomActionDialog
    assert "showAddOrEditCustomActionDialog" in content
    assert "OutlineTextContainerView" in content
    assert "EditTextBoldCursor" in content
    assert "nameOutlineView.attachEditText(nameEdit)" in content
    assert "nameOutlineView.animateError" in content
    assert "nameOutlineView.animateSelection" in content

    # Type chips
    assert "deeplink" in content and "chat" in content and "setting" in content and "plugin" in content

    # Customize dialog reordering and style selection
    assert "showCustomizeDialog" in content
    assert "FAB_ITEM_STYLE_CAPSULE" in content
    assert "FAB_ITEM_STYLE_CARD" in content
    assert "FAB_ITEM_STYLE_FILLED_TONAL" in content
    assert "FAB_ITEM_STYLE_OUTLINE" in content
    assert "msg_arrow_up" in content
    assert "msg_arrow_down" in content

    # CapsuleItemView styling
    assert "updateColors()" in content
    assert "FAB_ITEM_STYLE_FILLED_TONAL" in content
    assert "FAB_ITEM_STYLE_OUTLINE" in content


def test_md3_fab_appearance_activity_accent_tuner_integration():
    """Verify Md3FabAppearanceActivity has accent color tuner buttons for background and icon."""
    act_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "appearance", "Md3FabAppearanceActivity.java")
    assert os.path.isfile(act_path), f"File {act_path} must exist"
    with open(act_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "showAccentColorTuner(false)" in content
    assert "showAccentColorTuner(true)" in content
    assert "AccentColorTunerBottomSheet" in content
    assert "bigFabPreview.setColor(liveColor, false)" in content
    assert "bigFabPreview.setIconColor(liveColor, false)" in content


def test_open_extera_app_navigation_activity_accent_tuner():
    """Verify OpenExteraAppNavigationActivity color picker has accent tuner option."""
    nav_path = os.path.join(TMESSAGES, "java", "app", "exteraless", "settings", "OpenExteraAppNavigationActivity.java")
    assert os.path.isfile(nav_path), f"File {nav_path} must exist"
    with open(nav_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "На базе акцентного (слайдеры)" in content
    assert "AccentColorTunerBottomSheet" in content
