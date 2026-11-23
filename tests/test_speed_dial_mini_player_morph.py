import os
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES_JAVA = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "java")


def test_speed_dial_view_bottom_navigation_bar_safety():
    """Verify Md3SpeedDialView accounts for navigation bar and tabs offset so items never clash."""
    dial_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "components", "Md3SpeedDialView.java")
    assert os.path.exists(dial_path), "Md3SpeedDialView.java must exist"
    with open(dial_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "activity.getFloatingButtonBottomOffset()" in content
    assert "minBottomMargin" in content
    assert "activity.onSpeedDialOpenChanged(true, true)" in content
    assert "activity.onSpeedDialOpenChanged(false, animated)" in content


def test_player_mini_view_morph_methods():
    """Verify PlayerMiniView has morph methods to shrink into a compact square tile showing only the play button."""
    mini_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "player", "PlayerMiniView.java")
    assert os.path.exists(mini_path), "PlayerMiniView.java must exist"
    with open(mini_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "setSpeedDialMorphProgress" in content
    assert "setSpeedDialOpen" in content
    assert "notifySpeedDialMorph" in content
    assert "applySpeedDialMorph" in content
    assert "compactSize = dp(HEIGHT_DP)" in content
    # In compact mode, cover, texts, next/close buttons fade out
    assert "cover.setVisibility(progress >= 0.99f ? GONE : VISIBLE)" in content
    assert "texts.setVisibility(progress >= 0.99f ? GONE : VISIBLE)" in content
    assert "nextButton.setVisibility(progress >= 0.99f ? GONE : VISIBLE)" in content
    assert "closeButton.setVisibility(progress >= 0.99f ? GONE : VISIBLE)" in content
    # Play button remains visible and centered
    assert "playButton.setVisibility(VISIBLE)" in content


def test_dialogs_activity_speed_dial_open_changed():
    """Verify DialogsActivity notifies PlayerMiniView and updates floating button offset on speed dial toggle."""
    act_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "DialogsActivity.java")
    assert os.path.exists(act_path), "DialogsActivity.java must exist"
    with open(act_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "PlayerMiniView.notifySpeedDialMorph(open, animated)" in content
    assert "getFloatingButtonBottomOffset()" in content
    assert "speedDialView != null && speedDialView.isOpen()" in content
    assert "effectiveMiniPlayerInset" in content


def test_main_tabs_activity_controller_drives_mini_player_morph():
    """Verify MainTabsActivity propagates speedDialDimProgress to miniPlayer."""
    main_tabs_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "MainTabsActivity.java")
    assert os.path.exists(main_tabs_path), "MainTabsActivity.java must exist"
    with open(main_tabs_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "miniPlayer.setSpeedDialMorphProgress(speedDialDimProgress)" in content


def test_fragment_floating_button_bottom_nav_offset():
    """Verify FragmentFloatingButton provides ensureBottomNavOffset."""
    fab_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "Components", "FragmentFloatingButton.java")
    assert os.path.exists(fab_path), "FragmentFloatingButton.java must exist"
    with open(fab_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "ensureBottomNavOffset" in content
