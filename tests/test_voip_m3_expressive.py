import os
import re
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
if not os.path.isdir(os.path.join(REPO_ROOT, "TMessagesProj")):
    REPO_ROOT = "/root/ExteraMS"

VOIP_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui/Components/voip")
UI_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/java/org/telegram/ui")
APPEARANCE_DIR = os.path.join(REPO_ROOT, "TMessagesProj/src/main/kotlin/app/exteraless/appearance")


def test_md3_avatar_shapes_definitions():
    """Verify Md3AvatarShapes.kt exposes MD3 Expressive shapes: pill, scallop, squircle."""
    shapes_path = os.path.join(APPEARANCE_DIR, "Md3AvatarShapes.kt")
    assert os.path.isfile(shapes_path), f"Md3AvatarShapes.kt must exist at {shapes_path}"
    with open(shapes_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "const val SHAPE_PILL = 6" in content, "Md3AvatarShapes must define SHAPE_PILL"
    assert "const val SHAPE_SCALLOP = 39" in content, "Md3AvatarShapes must define SHAPE_SCALLOP"
    assert "const val SHAPE_SQUARE = 1" in content, "Md3AvatarShapes must define SHAPE_SQUARE"
    assert "fun getShapePath(" in content, "Md3AvatarShapes must expose getShapePath"
    assert "fun getMorphPath(" in content, "Md3AvatarShapes must expose getMorphPath"


def test_voip_floating_layout_outline_corner_radius_24dp():
    """Feature 6: Verify VoIPFloatingLayout uses 24dp squircle/capsule corner radius in floating mode."""
    floating_path = os.path.join(VOIP_DIR, "VoIPFloatingLayout.java")
    assert os.path.isfile(floating_path), f"VoIPFloatingLayout.java must exist at {floating_path}"
    with open(floating_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify outline provider uses 24dp for floatingMode
    assert re.search(r'floatingMode\s*\?\s*AndroidUtilities\.dp\(24\)\s*:\s*0', content) or \
           re.search(r'floatingMode\s*\?\s*dp\(24\)\s*:\s*0', content) or \
           "AndroidUtilities.dp(24)" in content, (
        "VoIPFloatingLayout must use 24dp corner radius in floatingMode outline"
    )

    # Verify legacy 4dp radius is removed from outline provider
    assert not re.search(r'floatingMode\s*\?\s*AndroidUtilities\.dp\(4\)\s*:\s*0', content), (
        "VoIPFloatingLayout must not retain legacy 4dp corner radius in outline"
    )

    # Verify fallback path addRoundRect also uses 24dp
    assert re.search(r'addRoundRect\(\s*rectF\s*,\s*AndroidUtilities\.dp\(24\)\s*,\s*AndroidUtilities\.dp\(24\)', content) or \
           re.search(r'addRoundRect\(\s*rectF\s*,\s*dp\(24\)\s*,\s*dp\(24\)', content), (
        "VoIPFloatingLayout path clipping fallback must use 24dp"
    )


def test_voip_floating_layout_override_radius_contract():
    """Verify VoIPFloatingLayout allows dynamic corner radius override and PiP restoration."""
    floating_path = os.path.join(VOIP_DIR, "VoIPFloatingLayout.java")
    with open(floating_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "public void setCornerRadius(float cornerRadius)" in content, (
        "VoIPFloatingLayout must provide setCornerRadius(float)"
    )
    assert "overrideCornerRadius = cornerRadius;" in content, (
        "VoIPFloatingLayout must update overrideCornerRadius"
    )
    assert "invalidateOutline();" in content, (
        "VoIPFloatingLayout must invalidate outline on corner radius change"
    )


def test_voip_fragment_pip_transition_24dp_harmony():
    """Verify VoIPFragment PiP transitions harmonize with the 24dp floating window corner radius."""
    fragment_path = os.path.join(UI_DIR, "VoIPFragment.java")
    assert os.path.isfile(fragment_path), f"VoIPFragment.java must exist at {fragment_path}"
    with open(fragment_path, "r", encoding="utf-8") as f:
        content = f.read()

    # cameraCornerRadiusFrom must use 24dp when callingUserIsVideo is true
    assert re.search(r'cameraCornerRadiusFrom\s*=\s*callingUserIsVideo\s*\?\s*AndroidUtilities\.dp\(24\)\s*:\s*0', content) or \
           re.search(r'cameraCornerRadiusFrom\s*=\s*callingUserIsVideo\s*\?\s*dp\(24\)\s*:\s*0', content), (
        "VoIPFragment createPiPTransition must use 24dp for cameraCornerRadiusFrom"
    )

    # setCornerRadius(-1f) must be called upon transition completion
    assert "currentUserCameraFloatingLayout.setCornerRadius(-1f);" in content, (
        "VoIPFragment must restore floating corner radius with setCornerRadius(-1f)"
    )


def test_voip_buttons_layout_md3_pill_dock_structure():
    """Feature 1: Verify VoIPButtonsLayout defines an MD3 Expressive pill container dock."""
    buttons_path = os.path.join(VOIP_DIR, "VoIPButtonsLayout.java")
    assert os.path.isfile(buttons_path), f"VoIPButtonsLayout.java must exist at {buttons_path}"
    with open(buttons_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify container drawing fields
    assert "containerPaint" in content, "VoIPButtonsLayout must define containerPaint"
    assert "containerRect" in content, "VoIPButtonsLayout must define containerRect"
    assert "containerRadius" in content, "VoIPButtonsLayout must define containerRadius"

    # Verify setWillNotDraw(false) and setClipChildren(false)
    assert "setWillNotDraw(false);" in content, (
        "VoIPButtonsLayout must enable drawing via setWillNotDraw(false)"
    )
    assert "setClipChildren(false);" in content, (
        "VoIPButtonsLayout must disable child clipping for smooth button slide animations"
    )


def test_voip_buttons_layout_dock_drawing_and_empty_guard():
    """Feature 1: Verify VoIPButtonsLayout draws the pill dock and guards when visibleChildCount == 0."""
    buttons_path = os.path.join(VOIP_DIR, "VoIPButtonsLayout.java")
    with open(buttons_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify onDraw draws the round rect pill
    assert "drawRoundRect(" in content, "VoIPButtonsLayout must draw round rect container"
    assert "canvas.drawRoundRect(containerRect, containerRadius, containerRadius, containerPaint);" in content or \
           re.search(r'drawRoundRect\(\s*containerRect\s*,\s*containerRadius', content), (
        "VoIPButtonsLayout must render containerRect with containerRadius in onDraw"
    )

    # Verify dock is guarded when visibleChildCount == 0 (no ghost pill when buttons are hidden)
    assert re.search(r'visibleChildCount\s*>\s*0', content), (
        "VoIPButtonsLayout must check visibleChildCount > 0 before drawing container"
    )


def test_voip_toggle_button_md3_pill_squircle_shapes():
    """Feature 2 & 3: Verify VoIPToggleButton uses MD3 pill/squircle container shapes."""
    toggle_path = os.path.join(VOIP_DIR, "VoIPToggleButton.java")
    assert os.path.isfile(toggle_path), f"VoIPToggleButton.java must exist at {toggle_path}"
    with open(toggle_path, "r", encoding="utf-8") as f:
        content = f.read()

    # VoIPToggleButton must draw rounded rect / squircle rather than raw drawCircle
    assert "drawRoundRect(" in content or "getShapePath(" in content, (
        "VoIPToggleButton must render MD3 pill/squircle shape using drawRoundRect or getShapePath"
    )
    assert "pressedScale" in content, "VoIPToggleButton must support pressedScale animation"


def test_voip_switch_layout_md3_squircle_shapes_and_states():
    """Feature 2: Verify VoIpSwitchLayout uses MD3 pill/squircle shapes and tonal colors."""
    switch_path = os.path.join(VOIP_DIR, "VoIpSwitchLayout.java")
    assert os.path.isfile(switch_path), f"VoIpSwitchLayout.java must exist at {switch_path}"
    with open(switch_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Must draw rounded rect or shape path in VoIpButtonView
    assert "drawRoundRect(" in content or "addRoundRect(" in content or "getShapePath(" in content, (
        "VoIpSwitchLayout VoIpButtonView must render MD3 squircle/pill shapes"
    )


def test_voip_fragment_end_call_prominent_red_pill():
    """Feature 3: Verify VoIPFragment styles bottomEndCallBtn with semantic red color."""
    fragment_path = os.path.join(UI_DIR, "VoIPFragment.java")
    with open(fragment_path, "r", encoding="utf-8") as f:
        content = f.read()

    # End call button must use semantic red 0xFFF01D2C
    assert "0xFFF01D2C" in content, (
        "VoIPFragment must style bottomEndCallBtn with prominent red 0xFFF01D2C"
    )
    assert "R.drawable.calls_decline" in content, (
        "VoIPFragment must use calls_decline icon for end call"
    )
    assert "bottomEndCallBtn.setOnClickListener" in content, (
        "VoIPFragment must wire hangUp click listener to bottomEndCallBtn"
    )


def test_accept_decline_view_and_fab_md3_expressive():
    """Feature 4: Verify AcceptDeclineView and FabBackgroundDrawable use MD3 Expressive containers."""
    fab_path = os.path.join(VOIP_DIR, "FabBackgroundDrawable.java")
    accept_path = os.path.join(VOIP_DIR, "AcceptDeclineView.java")
    assert os.path.isfile(fab_path), f"FabBackgroundDrawable.java must exist at {fab_path}"
    assert os.path.isfile(accept_path), f"AcceptDeclineView.java must exist at {accept_path}"

    with open(fab_path, "r", encoding="utf-8") as f:
        fab_content = f.read()
    with open(accept_path, "r", encoding="utf-8") as f:
        accept_content = f.read()

    # FabBackgroundDrawable must render rounded squircle/pill shape
    assert "drawRoundRect(" in fab_content or "getShapePath(" in fab_content, (
        "FabBackgroundDrawable must draw MD3 rounded/scalloped container shape"
    )

    # AcceptDeclineView must have semantic colors: Green for accept, Red for decline
    assert "0xFF40C749" in accept_content, "AcceptDeclineView must use affirmative green 0xFF40C749"
    assert "0xFFF01D2C" in accept_content, "AcceptDeclineView must use destructive red 0xFFF01D2C"


def test_image_with_waves_md3_avatar_shaping():
    """Feature 5: Verify ImageWithWavesView clips caller avatar using Md3AvatarShapes."""
    avatar_path = os.path.join(VOIP_DIR, "ImageWithWavesView.java")
    assert os.path.isfile(avatar_path), f"ImageWithWavesView.java must exist at {avatar_path}"
    with open(avatar_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "Md3AvatarShapes" in content or "clipPath" in content, (
        "ImageWithWavesView must clip avatar using Md3AvatarShapes and clipPath"
    )
    assert "dispatchDraw(" in content, (
        "ImageWithWavesView must override dispatchDraw for avatar shape clipping"
    )


def test_voip_fragment_transitions_and_animation_harmony():
    """Verify VoIPFragment maintains transition safety and animation harmony with the pill dock."""
    fragment_path = os.path.join(UI_DIR, "VoIPFragment.java")
    with open(fragment_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify updateButtons animation transitions
    assert "TransitionSet" in content, "VoIPFragment must use TransitionSet"
    assert "ChangeBounds" in content, "VoIPFragment must use ChangeBounds transition"
    assert "TransitionManager.beginDelayedTransition(buttonsLayout" in content, (
        "VoIPFragment must beginDelayedTransition on buttonsLayout"
    )

    # Verify staggered animation delay
    assert "animationDelay += 16;" in content or "animationDelay" in content, (
        "VoIPFragment must maintain staggered button animation delays for expressive entrance"
    )

    # Verify UI visibility toggle animates buttonsLayout as a single surface
    assert "buttonsLayout.animate().alpha(" in content, (
        "VoIPFragment must animate buttonsLayout alpha and translation on UI visibility toggle"
    )


def test_voip_gradient_layout_md3_theme_colors():
    """Feature 7: Verify VoIpGradientLayout renders MD3 Expressive themed background."""
    gradient_path = os.path.join(VOIP_DIR, "VoIpGradientLayout.java")
    assert os.path.isfile(gradient_path), f"VoIpGradientLayout.java must exist at {gradient_path}"
    with open(gradient_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Must use Theme colors instead of hardcoded 2018 blue/violet mesh
    assert "Theme.key_windowBackgroundWhite" in content, (
        "VoIpGradientLayout must use Theme.key_windowBackgroundWhite for base surface"
    )
    assert "Theme.key_chats_actionBackground" in content, (
        "VoIpGradientLayout must use Theme.key_chats_actionBackground for atmospheric primary bloom"
    )
    assert "LinearGradient" in content, (
        "VoIpGradientLayout must render smooth MD3 atmospheric LinearGradient"
    )
    assert "backgroundProvider.getLightCanvas()" in content, (
        "VoIpGradientLayout must supply theme-colored surface to VoIPBackgroundProvider"
    )
    assert "buttonTonalColor" in content, (
        "VoIpGradientLayout must compute theme-aware buttonTonalColor"
    )


def test_voip_typography_and_top_icons_themed():
    """Feature 8: Verify VoIP title, status, timer, and top icons use theme colors."""
    fragment_path = os.path.join(UI_DIR, "VoIPFragment.java")
    status_path = os.path.join(VOIP_DIR, "VoIPStatusTextView.java")
    timer_path = os.path.join(VOIP_DIR, "VoIPTimerView.java")

    with open(fragment_path, "r", encoding="utf-8") as f:
        frag_content = f.read()
    with open(status_path, "r", encoding="utf-8") as f:
        status_content = f.read()
    with open(timer_path, "r", encoding="utf-8") as f:
        timer_content = f.read()

    # Title must use Theme.key_windowBackgroundWhiteBlackText
    assert "callingUserTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));" in frag_content, (
        "VoIPFragment callingUserTitle must use Theme text color"
    )
    # Top icons must have theme tint
    assert "backIcon.setColorFilter(" in frag_content, "VoIPFragment backIcon must be theme tinted"
    assert "addIcon.setColorFilter(" in frag_content, "VoIPFragment addIcon must be theme tinted"

    # Status must use Theme secondary text
    assert "Theme.key_windowBackgroundWhiteGrayText" in status_content, (
        "VoIPStatusTextView must use Theme.key_windowBackgroundWhiteGrayText"
    )
    # Timer must use Theme text
    assert "Theme.key_windowBackgroundWhiteBlackText" in timer_content, (
        "VoIPTimerView must use Theme.key_windowBackgroundWhiteBlackText"
    )


def test_voip_dock_capsule_and_labels_removed():
    """Verify VoIP dock capsule frame is disabled by default and button labels are hidden."""
    buttons_path = os.path.join(VOIP_DIR, "VoIPButtonsLayout.java")
    switch_path = os.path.join(VOIP_DIR, "VoIpSwitchLayout.java")
    toggle_path = os.path.join(VOIP_DIR, "VoIPToggleButton.java")

    with open(buttons_path, "r", encoding="utf-8") as f:
        buttons_content = f.read()
    with open(switch_path, "r", encoding="utf-8") as f:
        switch_content = f.read()
    with open(toggle_path, "r", encoding="utf-8") as f:
        toggle_content = f.read()

    # Capsule frame must be disabled by default
    assert "drawPillDock = false;" in buttons_content, (
        "VoIPButtonsLayout must have drawPillDock = false by default"
    )

    # Button labels must be hidden by default
    assert "showText = false;" in switch_content, (
        "VoIpSwitchLayout must hide labels by default"
    )
    assert "showText = false;" in toggle_content, (
        "VoIPToggleButton must hide labels by default"
    )


def test_voip_end_call_button_touch_and_measure_safety():
    """Verify VoIPToggleButton overrides onMeasure, onTouchEvent and VoIPButtonsLayout ensures non-zero height."""
    buttons_path = os.path.join(VOIP_DIR, "VoIPButtonsLayout.java")
    toggle_path = os.path.join(VOIP_DIR, "VoIPToggleButton.java")
    fragment_path = os.path.join(UI_DIR, "VoIPFragment.java")

    with open(buttons_path, "r", encoding="utf-8") as f:
        buttons_content = f.read()
    with open(toggle_path, "r", encoding="utf-8") as f:
        toggle_content = f.read()
    with open(fragment_path, "r", encoding="utf-8") as f:
        fragment_content = f.read()

    # VoIPToggleButton must have onMeasure setting at least diameter height
    assert "protected void onMeasure" in toggle_content, (
        "VoIPToggleButton must override onMeasure"
    )
    assert "setMeasuredDimension" in toggle_content, (
        "VoIPToggleButton must set measured dimension with non-zero button height"
    )

    # VoIPToggleButton must have onTouchEvent handling clicks
    assert "public boolean onTouchEvent(MotionEvent" in toggle_content, (
        "VoIPToggleButton must handle onTouchEvent"
    )
    assert "performClick();" in toggle_content, (
        "VoIPToggleButton must call performClick on click"
    )

    # VoIPButtonsLayout must layout children with at least getMeasuredHeight()
    assert "Math.max(child.getMeasuredHeight(), getMeasuredHeight())" in buttons_content, (
        "VoIPButtonsLayout must layout children with at least getMeasuredHeight()"
    )

    # VoIPFragment must set bottomEndCallBtn click listener upon creation
    assert "bottomEndCallBtn.setOnClickListener" in fragment_content, (
        "VoIPFragment must wire click listener to bottomEndCallBtn"
    )


def test_accept_decline_view_dynamic_monet_styling_no_moss_green():
    """Verify AcceptDeclineView has no hardcoded moss green colors and uses dynamic translucent Monet-friendly styling."""
    accept_path = os.path.join(VOIP_DIR, "AcceptDeclineView.java")
    with open(accept_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Verify hardcoded moss green colors are eliminated
    moss_green_colors = [
        "0xFF0A2619",
        "0xFFC1DEC9",
        "0xFF0E3020",
        "0xFF3ED88E",
        "0xFF1D8348",
        "0xFF14422D",
        "0xFF0D2F1F",
    ]
    for color in moss_green_colors:
        assert color not in content, f"AcceptDeclineView must not contain hardcoded moss green {color}"

    # Verify translucent white track background (32 alpha)
    assert "ColorUtils.setAlphaComponent(0xFFFFFFFF, 32)" in content, (
        "AcceptDeclineView track background must use translucent white"
    )

    # Verify message button container uses translucent white (30 resting, 45 touching)
    assert "touchingMessage ? 45 : 30" in content, (
        "AcceptDeclineView message button background must adapt to touch state with translucent white"
    )

    # Verify labels use 0xD9FFFFFF / 85% alpha
    assert "0xD9FFFFFF" in content, "AcceptDeclineView labels must use 85% white (0xD9FFFFFF)"

    # Verify knob phone icon resting color is dark neutral 0xFF1C1B1F and transitions smoothly to COLOR_ACCEPT & COLOR_DECLINE
    assert "0xFF1C1B1F" in content, "AcceptDeclineView knob icon resting color must be dark neutral 0xFF1C1B1F"
    assert "ColorUtils.blendARGB(0xFF1C1B1F, COLOR_DECLINE, p)" in content, (
        "AcceptDeclineView knob icon must transition to COLOR_DECLINE when dragged left"
    )
    assert "ColorUtils.blendARGB(0xFF1C1B1F, COLOR_ACCEPT, p)" in content, (
        "AcceptDeclineView knob icon must transition to COLOR_ACCEPT when dragged right"
    )




