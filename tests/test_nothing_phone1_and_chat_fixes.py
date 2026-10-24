import os
import re
import pytest

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_glyph_controller_phone1_detection_and_fallback():
    glyph_controller_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "glyph", "GlyphController.java"
    )
    with open(glyph_controller_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "public static boolean isPhone1()" in content
    assert 'model.contains("A063")' in content or 'model.contains("a063")' in content.lower()
    assert "SPACEWAR" in content
    assert "PHONE (1)" in content or "PHONE 1" in content
    assert "public static String resolveStripDevice()" in content
    assert "stripManager.register(targetDevice)" in content
    assert "IGlyphService" in content


def test_glyph_controller_null_safe_builder():
    glyph_controller_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "glyph", "GlyphController.java"
    )
    with open(glyph_controller_path, "r", encoding="utf-8") as f:
        content = f.read()

    # All uses of getGlyphFrameBuilder must be null-checked
    occurrences = [m.start() for m in re.finditer(r"getGlyphFrameBuilder\(\)", content)]
    assert len(occurrences) >= 4, "Expected getGlyphFrameBuilder() to be used in multiple animations"
    assert "if (builder == null)" in content or "if (b == null)" in content


def test_android_manifest_nothing_queries_and_metadata():
    manifest_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "AndroidManifest.xml"
    )
    with open(manifest_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert '<package android:name="com.nothing.thirdparty"/>' in content
    assert '<action android:name="com.nothing.thirdparty.bind_glyphservice"/>' in content
    assert '<meta-data android:name="targetNothingSDKVersion" android:value="1" />' in content


def test_chat_activity_action_mode_hides_fragment_context_view():
    chat_activity_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "ChatActivity.java"
    )
    with open(chat_activity_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "public void updateFragmentContextView(boolean animated)" in content
    assert "boolean isActionMode = actionBar != null && actionBar.isActionModeShowed();" in content
    assert "topPanelLayout.setViewVisible(fragmentContextViewWrapper, shouldShow, animated);" in content
    assert "updateFragmentContextView(true);" in content


def test_md3_chat_input_styling_and_animations():
    enter_view_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Components", "ChatActivityEnterView.java"
    )
    with open(enter_view_path, "r", encoding="utf-8") as f:
        content = f.read()

    # MD3 background bypass in drawBackground
    assert "boolean md3Input = app.exteraless.chats.ChatsConfig.md3ChatInput();" in content
    assert "topView != null && topView.getVisibility() == View.VISIBLE" in content

    # Circular send/mic button in MD3 mode
    assert "Theme.createInsetRoundRectDrawable(sendColor, dp(19), dp(5), dp(5))" in content
    assert "audioVideoButtonContainer.setBackground(" in content

    # Audio video container scale and alpha animations in checkSendButton
    assert "ObjectAnimator.ofFloat(audioVideoButtonContainer, View.SCALE_X" in content
    assert "ObjectAnimator.ofFloat(audioVideoButtonContainer, View.ALPHA" in content
