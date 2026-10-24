import os
import re
import pytest

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def test_chats_config_has_md3_and_animation_items():
    chats_config_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "kotlin", "app", "exteraless", "chats", "ChatsConfig.kt"
    )
    with open(chats_config_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "val md3ChatInput = addConfig(\"OEChatsMd3ChatInput\"" in content
    assert "fun md3ChatInput(): Boolean" in content
    assert "const val MESSAGE_ANIM_DEFAULT = 0" in content
    assert "const val MESSAGE_ANIM_NONE = 1" in content
    assert "const val MESSAGE_ANIM_FADE = 2" in content
    assert "const val MESSAGE_ANIM_SLIDE = 3" in content
    assert "const val MESSAGE_ANIM_SCALE = 4" in content
    assert "const val MESSAGE_ANIM_POP = 5" in content
    assert "val incomingMessageAnimation = addConfig(\"OEChatsIncomingMessageAnimation\"" in content
    assert "val outgoingMessageAnimation = addConfig(\"OEChatsOutgoingMessageAnimation\"" in content
    assert "fun incomingMessageAnimation(): Int" in content
    assert "fun outgoingMessageAnimation(): Int" in content


def test_appearance_config_has_md3_chat_input_alias():
    appearance_config_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "kotlin", "app", "exteraless", "appearance", "AppearanceConfig.kt"
    )
    with open(appearance_config_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "val md3ChatInput = app.exteraless.chats.ChatsConfig.md3ChatInput" in content
    assert "fun md3ChatInput(): Boolean = app.exteraless.chats.ChatsConfig.md3ChatInput()" in content


def test_string_resources_defined():
    chats_en = os.path.join(REPO, "TMessagesProj", "src", "main", "res", "values", "strings_oe_chats.xml")
    chats_ru = os.path.join(REPO, "TMessagesProj", "src", "main", "res", "values-ru-rRU", "strings_oe_chats.xml")
    appearance_en = os.path.join(REPO, "TMessagesProj", "src", "main", "res", "values", "strings_oe_appearance.xml")
    appearance_ru = os.path.join(REPO, "TMessagesProj", "src", "main", "res", "values-ru-rRU", "strings_oe_appearance.xml")

    for path in (chats_en, chats_ru, appearance_en, appearance_ru):
        assert os.path.isfile(path), f"File {path} must exist"

    with open(chats_en, "r", encoding="utf-8") as f:
        c_en = f.read()
    with open(chats_ru, "r", encoding="utf-8") as f:
        c_ru = f.read()
    with open(appearance_en, "r", encoding="utf-8") as f:
        a_en = f.read()
    with open(appearance_ru, "r", encoding="utf-8") as f:
        a_ru = f.read()

    for string_id in [
        "OEChatsMd3ChatInput",
        "OEChatsMd3ChatInputInfo",
        "OEChatsIncomingMessageAnimation",
        "OEChatsOutgoingMessageAnimation",
        "OEChatsMessageAnimDefault",
        "OEChatsMessageAnimNone",
        "OEChatsMessageAnimFade",
        "OEChatsMessageAnimSlide",
        "OEChatsMessageAnimScale",
        "OEChatsMessageAnimPop",
    ]:
        assert f'name="{string_id}"' in c_en, f"{string_id} missing in English chats strings"
        assert f'name="{string_id}"' in c_ru, f"{string_id} missing in Russian chats strings"

    assert 'name="OEAppearanceMd3ChatInput"' in a_en
    assert 'name="OEAppearanceMd3ChatInput"' in a_ru


def test_enter_view_md3_wiring():
    enter_view_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Components", "ChatActivityEnterView.java"
    )
    with open(enter_view_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "app.exteraless.chats.ChatsConfig.md3ChatInput()" in content
    assert "frameLayout.setBackground(Theme.createRoundRectDrawable(dp(24), pillBg));" in content
    assert "messageEditTextContainer.setBackground(Theme.createRoundRectDrawable(dp(24), pillBg));" in content


def test_chat_list_item_animator_message_animations():
    animator_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "recyclerview", "ChatListItemAnimator.java"
    )
    with open(animator_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "getMessageAnimationType(RecyclerView.ViewHolder holder)" in content
    assert "app.exteraless.chats.ChatsConfig.MESSAGE_ANIM_NONE" in content
    assert "app.exteraless.chats.ChatsConfig.MESSAGE_ANIM_FADE" in content
    assert "app.exteraless.chats.ChatsConfig.MESSAGE_ANIM_SLIDE" in content
    assert "app.exteraless.chats.ChatsConfig.MESSAGE_ANIM_SCALE" in content
    assert "app.exteraless.chats.ChatsConfig.MESSAGE_ANIM_POP" in content
    assert "outgoingMessageAnimation()" in content
    assert "incomingMessageAnimation()" in content


def test_noto_animated_emoji_playback_support():
    image_receiver_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "messenger", "ImageReceiver.java"
    )
    with open(image_receiver_path, "r", encoding="utf-8") as f:
        ir_content = f.read()

    assert "import android.os.SystemClock;" in ir_content
    assert "android.graphics.drawable.Animatable" in ir_content
    assert "setRepeatCount(android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE)" in ir_content

    chat_cell_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Cells", "ChatMessageCell.java"
    )
    with open(chat_cell_path, "r", encoding="utf-8") as f:
        cell_content = f.read()

    assert "messageObject.notoAnimatedEmojiUrl != null" in cell_content
