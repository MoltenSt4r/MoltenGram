import os
import pytest

SITE_PATH = "/root/moltenstar_site/index.html"


def test_moltenstar_site_exists():
    assert os.path.exists(SITE_PATH), "moltenstar_site/index.html must exist"


def test_archive_modal_sheet_has_stable_height_and_transition():
    with open(SITE_PATH, "r", encoding="utf-8") as f:
        html = f.read()

    # Must contain stable height for #archiveSheet to prevent jerky jumps on tab change
    assert "#archiveSheet {" in html
    assert "height: 82vh;" in html
    assert "min-height: 520px;" in html
    assert "transition: transform 0.45s var(--spring-smooth), height 0.35s" in html
    # Inline style on HTML tag should not lock max-height without height
    assert '<div class="modal-sheet" id="archiveSheet">' in html


def test_settings_smooth_accordion_styles_and_logic():
    with open(SITE_PATH, "r", encoding="utf-8") as f:
        html = f.read()

    # CSS accordion for smooth expanding
    assert ".settings-accordion {" in html
    assert "grid-template-rows: 0fr;" in html
    assert ".settings-accordion.is-expanded {" in html
    assert "grid-template-rows: 1fr;" in html
    assert "transition: grid-template-rows" in html

    # Accordion inner container
    assert ".settings-accordion-inner {" in html
    assert "transform: translateY(-8px);" in html

    # Icon rotation transition
    assert "#settingsExpandIcon {" in html
    assert "transform: rotate(180deg);" in html

    # JavaScript integration
    assert "settingsAccordion" in html
    assert "classList.toggle('is-expanded'" in html


def test_catalog_has_new_ios_fab_player_voice_settings():
    with open(SITE_PATH, "r", encoding="utf-8") as f:
        html = f.read()

    # iOS Liquid Glass
    assert "iosLiquidGlassNavBar" in html
    assert "iosLiquidGlassFab" in html
    assert "iosStyles" in html

    # FAB & Speed Dial
    assert "fabAppearance" in html
    assert "fabColor" in html
    assert "fabAlignment" in html

    # Player settings
    assert "playerCoverStyle" in html
    assert "playerDiscSpin" in html
    assert "playerControlsSize" in html
    assert "playerWavySeekbar" in html
    assert "playerMiniProgress" in html

    # Voice changer
    assert "voiceChangerQuickTest" in html
