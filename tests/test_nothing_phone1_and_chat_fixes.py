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

    # Yougram MD3 layout: attach '+' on the left, emoji button on the right
    assert "attachButton.setImageResource(R.drawable.baseline_add_24);" in content
    assert "messageEditTextContainer.addView(emojiButton, LayoutHelper.createFrame(DEFAULT_HEIGHT, DEFAULT_HEIGHT, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 2, 0));" in content
    assert "messageEditTextContainer.addView(attachButton, LayoutHelper.createFrame(DEFAULT_HEIGHT, DEFAULT_HEIGHT, Gravity.BOTTOM | Gravity.LEFT, 2, 0, 0, 0));" in content


def test_chat_input_views_container_md3_draw_background():
    container_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Components", "chat", "ChatInputViewsContainer.java"
    )
    with open(container_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "if (drawInputBackground && !app.exteraless.chats.ChatsConfig.md3ChatInput())" in content
    assert "blurredBackgroundDrawable.draw(canvas);" in content


def test_chat_message_cell_share_button_selection_guard():
    cell_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Cells", "ChatMessageCell.java"
    )
    with open(cell_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "if (delegate != null && (delegate.hasSelectedMessages() || !delegate.canPerformActions())) return false;" in content
    assert "delegate == null || (!delegate.hasSelectedMessages() && delegate.canPerformActions())" in content


def test_metrolist_url_matcher_youtube_music_and_youtu_be():
    matcher_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "metrolist", "MetrolistUrlMatcher.java"
    )
    with open(matcher_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "youtu.be" in content
    assert "YOUTU_BE_PATTERN" in content
    assert "music.youtube.com" in content
    assert "metrolist.cc" in content


def test_media_controller_http_audio_streaming():
    mc_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "messenger", "MediaController.java"
    )
    with open(mc_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert 'streamAttachPath.startsWith("http://") || streamAttachPath.startsWith("https://")' in content
    assert 'audioPlayer.preparePlayer(Uri.parse(streamAttachPath), "other");' in content


def test_profile_music_saving_for_streaming_tracks():
    controller_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "metrolist", "MetrolistController.java"
    )
    with open(controller_path, "r", encoding="utf-8") as f:
        c_content = f.read()

    assert "public void saveStreamTrackToProfile(MessageObject mo, PlayerActions.ResultCallback callback)" in c_content
    assert "TL_account_saveMusic req = new TLRPC.TL_account_saveMusic();" in c_content
    assert "NotificationCenter.profileMusicUpdated" in c_content

    actions_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "player", "PlayerActions.java"
    )
    with open(actions_path, "r", encoding="utf-8") as f:
        a_content = f.read()

    assert "app.exteraless.metrolist.MetrolistController.getInstance().saveStreamTrackToProfile(messageObject, callback);" in a_content


def test_chat_list_item_animator_enter_transition_and_added_items_height():
    animator_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "recyclerview", "ChatListItemAnimator.java"
    )
    with open(animator_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Enter transition cleanup must execute for all animations
    assert "activity.animatingMessageObjects.remove(chatMessageCell.getMessageObject());" in content
    assert "activity.getChatActivityEnterView().startMessageTransition();" in content
    assert "if (addedItemsHeight != 0)" in content
    assert "view.setTranslationY(addedItemsHeight);" in content
    assert "holder != greetingsSticker" in content
    assert "cell.getTransitionParams().resetAnimation();" in content or "((ChatMessageCell) view).getTransitionParams().resetAnimation();" in content


def test_metrolist_url_matcher_youtube_domains_and_endpoints():
    matcher_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "metrolist", "MetrolistUrlMatcher.java"
    )
    with open(matcher_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert 'host.equals("music.youtube.com") || host.endsWith(".music.youtube.com")' in content
    assert "YT_PATH_PATTERN" in content


def test_metrolist_client_innertube_clients_and_cipher_fallback():
    client_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "metrolist", "MetrolistClient.java"
    )
    with open(client_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "ANDROID_TESTSUITE" in content
    assert "TVHTML5_SIMPLY_EMBEDDED_PLAYER" in content
    assert "signatureCipher" in content
    assert "hlsManifestUrl" in content
    assert 'fetchPlayerWithClient(videoId, "IOS", "20.03.02"' in content
    assert 'MetrolistAuth.getInstance().getAuthorizationHeader(origin)' in content


def test_video_player_modern_user_agent_and_hls_fallback():
    vp_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Components", "VideoPlayer.java"
    )
    with open(vp_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "Chrome/130.0.0.0" in content
    assert "Firefox/47.0" not in content
    assert '.contains(".m3u8")' in content


def test_metrolist_controller_audio_url_resolution_and_profile_saving():
    controller_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "metrolist", "MetrolistController.java"
    )
    with open(controller_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "MetrolistClient.Track resolved = MetrolistClient.getInstance().fetchPlayer(videoId);" in content
    assert "conn.setRequestProperty(\"Referer\", \"https://music.youtube.com/\");" in content


