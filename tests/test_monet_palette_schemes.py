import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

def test_appearance_config_monet_palette_schemes():
    config_file = REPO_ROOT / "TMessagesProj/src/main/kotlin/app/exteraless/appearance/AppearanceConfig.kt"
    assert config_file.exists(), "AppearanceConfig.kt must exist"
    content = config_file.read_text(encoding="utf-8")

    # Verify 9 palette constants exist
    palettes = [
        "MONET_PALETTE_TONAL_SPOT",
        "MONET_PALETTE_NEUTRAL",
        "MONET_PALETTE_VIBRANT",
        "MONET_PALETTE_EXPRESSIVE",
        "MONET_PALETTE_RAINBOW",
        "MONET_PALETTE_FRUIT_SALAD",
        "MONET_PALETTE_MONOCHROME",
        "MONET_PALETTE_FIDELITY",
        "MONET_PALETTE_CONTENT",
    ]
    for pal in palettes:
        assert pal in content, f"AppearanceConfig.kt must declare {pal}"

    # Verify config declaration
    assert "monetPaletteStyle = addConfig" in content or "val monetPaletteStyle" in content
    assert "fun monetPaletteStyle(): Int" in content
    assert "fun createDynamicScheme(" in content
    assert "resolveThemeToken" in content
    assert "createDynamicScheme(monetPaletteStyle()" in content

def test_theme_activity_monet_palette_row():
    theme_activity = REPO_ROOT / "TMessagesProj/src/main/java/org/telegram/ui/ThemeActivity.java"
    assert theme_activity.exists(), "ThemeActivity.java must exist"
    content = theme_activity.read_text(encoding="utf-8")

    assert "monetPaletteRow" in content
    assert "showMonetPaletteOptions" in content
    assert "applyMonetPalette" in content
    assert "monetPaletteName()" in content
    assert "R.string.MonetPaletteHeader" in content

def test_player_colors_uses_dynamic_scheme_factory():
    player_colors = REPO_ROOT / "TMessagesProj/src/main/java/app/exteraless/player/PlayerColors.java"
    assert player_colors.exists(), "PlayerColors.java must exist"
    content = player_colors.read_text(encoding="utf-8")

    assert "createDynamicScheme" in content
    assert "monetPaletteStyle()" in content
    assert "new SchemeTonalSpot(" not in content

def test_all_locales_have_monet_palette_strings():
    locales = ["values", "values-ru-rRU", "values-ar", "values-es", "values-pl-rPL", "values-zh-rCN"]
    keys = [
        "MonetPaletteHeader",
        "MonetPaletteSubHeader",
        "MonetPaletteTonalSpot",
        "MonetPaletteNeutral",
        "MonetPaletteVibrant",
        "MonetPaletteExpressive",
        "MonetPaletteRainbow",
        "MonetPaletteFruitSalad",
        "MonetPaletteMonochrome",
        "MonetPaletteFidelity",
        "MonetPaletteContent",
    ]
    for loc in locales:
        file_path = REPO_ROOT / f"TMessagesProj/src/main/res/{loc}/strings_oe_appearance.xml"
        assert file_path.exists(), f"{file_path} must exist"
        txt = file_path.read_text(encoding="utf-8")
        for key in keys:
            assert f'name="{key}"' in txt, f"Missing string key {key} in {loc}"
