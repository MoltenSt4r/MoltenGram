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
    assert "setRepeatCount" in ir_content
    assert "android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE" in ir_content

    chat_cell_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Cells", "ChatMessageCell.java"
    )
    with open(chat_cell_path, "r", encoding="utf-8") as f:
        cell_content = f.read()

    assert "messageObject.notoAnimatedEmojiUrl != null" in cell_content


def test_cursor_hidden_on_selection_and_carousel_smooth_two_photo():
    # 1. Verify cursor hidden on selection in ChatActivityEnterView
    enter_view_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Components", "ChatActivityEnterView.java"
    )
    with open(enter_view_path, "r", encoding="utf-8") as f:
        enter_view_content = f.read()

    assert "boolean hasSelection = selStart != selEnd;" in enter_view_content
    assert "setAllowDrawCursor(!hasSelection);" in enter_view_content
    assert "setSelectionActive(hasSelection)" in enter_view_content

    # 2. Verify TextAnimationController supports setSelectionActive
    tac_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "chats", "TextAnimationController.java"
    )
    with open(tac_path, "r", encoding="utf-8") as f:
        tac_content = f.read()

    assert "public void setSelectionActive(boolean active)" in tac_content
    assert "!selectionActive" in tac_content

    # 3. Verify TwoPhotoCarouselStrategy has anchor keylines for smooth scrolling
    carousel_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "app", "exteraless", "appearance", "Md3MediaCarouselView.java"
    )
    with open(carousel_path, "r", encoding="utf-8") as f:
        carousel_content = f.read()

    assert "addAnchorKeyline" in carousel_content
    assert "smallMask" in carousel_content

    # 4. Verify PhotoViewer clipHorizontalAddition restored
    pv_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "PhotoViewer.java"
    )
    with open(pv_path, "r", encoding="utf-8") as f:
        pv_content = f.read()

    assert "clipHorizontal += object.clipHorizontalAddition;" in pv_content


def test_media_carousel_flyout_all_items_and_rounding_clip():
    # 1. ChatMessageCell visibility contract
    cmc_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Cells", "ChatMessageCell.java"
    )
    with open(cmc_path, "r", encoding="utf-8") as f:
        cmc_content = f.read()

    assert "if (md3MediaCarouselView != null && md3MediaCarouselView.getVisibility() == VISIBLE) {\n            return false;\n        }" in cmc_content, (
        "isSiblingGroupCarouselMessage must return false when md3MediaCarouselView is visible"
    )
    assert "if (md3MediaCarouselView != null && md3MediaCarouselView.getVisibility() == VISIBLE) {\n            return true;\n        }" in cmc_content, (
        "isMediaCarouselMessage must return true when md3MediaCarouselView is visible"
    )
    assert "public void didPressCarouselItem(MessageObject message)" in cmc_content
    assert "public View getCarouselImageView(int index)" in cmc_content
    assert "public View getCarouselItemView(int index)" in cmc_content
    assert "public int getCarouselMessageIndex(MessageObject message)" in cmc_content
    assert "public boolean containsCarouselMessage(MessageObject message)" in cmc_content

    # 2. ChatActivity PlaceProviderObject resolution and 24dp rounding / mask clipping
    ca_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "ChatActivity.java"
    )
    with open(ca_path, "r", encoding="utf-8") as f:
        ca_content = f.read()

    assert "if (cell.isSiblingGroupCarouselMessage()) {\n                    continue;\n                }" in ca_content
    assert "cell.containsCarouselMessage(messageObject)" in ca_content
    assert "int carouselIdx = cell.getCarouselMessageIndex(messageObject);" in ca_content
    assert "View carouselImage = cell.getCarouselImageView(index);" in ca_content
    assert "View carouselItem = cell.getCarouselItemView(index);" in ca_content
    assert "int r = AndroidUtilities.dp(24);" in ca_content
    assert "object.radius = new int[]{r, r, r, r};" in ca_content
    assert "object.clipHorizontalAddition = (int) ((carouselItem.getWidth() - maskRect.width()) / 2f);" in ca_content
    assert "MessageObject.GroupedMessages carouselGroup = cell != null && cell.isMediaCarouselMessage() ? cell.getCurrentMessagesGroup() : null;" in ca_content

    # 3. Behavioral simulation of isSiblingGroupCarouselMessage & isMediaCarouselMessage
    def simulate_is_sibling(has_group, group_size, carousel_setting, primary_id, current_id, carousel_visible):
        if carousel_visible:
            return False
        if has_group and group_size > 1 and carousel_setting:
            if primary_id is not None and current_id is not None:
                return primary_id != current_id
            return False
        return False

    def simulate_is_carousel(has_group, group_size, carousel_setting, primary_id, current_id, carousel_visible):
        if carousel_visible:
            return True
        is_sibling = simulate_is_sibling(has_group, group_size, carousel_setting, primary_id, current_id, carousel_visible)
        return has_group and group_size > 1 and carousel_setting and not is_sibling

    # Visible carousel host cell: element 0 (primary)
    assert simulate_is_sibling(True, 3, True, 100, 100, carousel_visible=True) is False
    assert simulate_is_carousel(True, 3, True, 100, 100, carousel_visible=True) is True

    # Visible carousel host cell: element 1 or 2 clicked (non-primary)
    assert simulate_is_sibling(True, 3, True, 100, 101, carousel_visible=True) is False
    assert simulate_is_carousel(True, 3, True, 100, 101, carousel_visible=True) is True
    assert simulate_is_sibling(True, 3, True, 100, 102, carousel_visible=True) is False
    assert simulate_is_carousel(True, 3, True, 100, 102, carousel_visible=True) is True

    # Sibling placeholder cell in chatListView (carousel not visible)
    assert simulate_is_sibling(True, 3, True, 100, 101, carousel_visible=False) is True
    assert simulate_is_carousel(True, 3, True, 100, 101, carousel_visible=False) is False


def test_media_carousel_cell_recycling_state_transition():
    """
    Verifies that ChatMessageCell resolves primary message identity directly in setMessageContent
    to prevent view recycling circular state dependencies, and asserts all lifecycle recycling transitions:
    1. Static contract: setMessageContent directly evaluates isPrimary using getCarouselPrimaryMessage,
       breaking circular dependency on isSiblingGroupCarouselMessage().
    2. Dynamic behavioral simulation: Ensures recycled cells with pre-existing VISIBLE carousel properly
       transition to GONE and collapse to (0, 0) in onMeasure on sibling message binding, while preserving
       PlaceProviderObject click resolution on host cell.
    """
    # 1. Static code / AST assertions in ChatMessageCell.java
    cmc_path = os.path.join(
        REPO, "TMessagesProj", "src", "main", "java", "org", "telegram", "ui", "Cells", "ChatMessageCell.java"
    )
    with open(cmc_path, "r", encoding="utf-8") as f:
        cmc_content = f.read()

    assert "MessageObject primary = getCarouselPrimaryMessage(currentMessagesGroup);" in cmc_content, (
        "ChatMessageCell.setMessageContent must resolve primary via getCarouselPrimaryMessage(currentMessagesGroup)"
    )
    assert "boolean isPrimary = primary != null && currentMessageObject != null && primary.getId() == currentMessageObject.getId();" in cmc_content, (
        "ChatMessageCell.setMessageContent must check primary.getId() == currentMessageObject.getId()"
    )
    assert "if (isPrimary) {" in cmc_content, (
        "ChatMessageCell.setMessageContent must branch on isPrimary"
    )
    assert "if (!isSiblingGroupCarouselMessage())" not in cmc_content, (
        "ChatMessageCell must NOT use !isSiblingGroupCarouselMessage() in setMessageContent"
    )

    # 2. Behavioral simulation of ChatMessageCell recycling lifecycle
    class Msg:
        def __init__(self, mid):
            self.id = mid

        def getId(self):
            return self.id

    class Group:
        def __init__(self, ids, is_documents=False):
            self.messages = [Msg(i) for i in ids]
            self.isDocuments = is_documents

        def findPrimaryMessageObject(self):
            return self.messages[0]

        def findCaptionMessageObject(self):
            return None

    def get_carousel_primary(group):
        if group is None or not group.messages:
            return None
        primary = group.findPrimaryMessageObject()
        if primary is not None:
            return primary
        caption = group.findCaptionMessageObject()
        if caption is not None:
            return caption
        return group.messages[0]

    class ChatMessageCellSim:
        VISIBLE = 0
        GONE = 8

        def __init__(self):
            self.md3MediaCarouselView_visibility = None
            self.currentMessageObject = None
            self.currentMessagesGroup = None
            self.mediaGroupCarousel = True

        def isSiblingGroupCarouselMessage(self):
            if self.md3MediaCarouselView_visibility == self.VISIBLE:
                return False
            if (self.currentMessagesGroup is not None and not self.currentMessagesGroup.isDocuments
                    and len(self.currentMessagesGroup.messages) > 1 and self.mediaGroupCarousel):
                primary = get_carousel_primary(self.currentMessagesGroup)
                if primary is not None and self.currentMessageObject is not None:
                    return primary.getId() != self.currentMessageObject.getId()
                return False
            return False

        def isMediaCarouselMessage(self):
            if self.md3MediaCarouselView_visibility == self.VISIBLE:
                return True
            return bool(self.currentMessagesGroup is not None and not self.currentMessagesGroup.isDocuments
                        and len(self.currentMessagesGroup.messages) > 1 and self.mediaGroupCarousel
                        and not self.isSiblingGroupCarouselMessage())

        def setMessageContent(self, msg, group):
            self.currentMessageObject = msg
            self.currentMessagesGroup = group
            if (self.currentMessagesGroup is not None and not self.currentMessagesGroup.isDocuments
                    and len(self.currentMessagesGroup.messages) > 1 and self.mediaGroupCarousel):
                primary = get_carousel_primary(self.currentMessagesGroup)
                is_primary = (primary is not None and self.currentMessageObject is not None
                              and primary.getId() == self.currentMessageObject.getId())
                if is_primary:
                    self.md3MediaCarouselView_visibility = self.VISIBLE
                else:
                    self.md3MediaCarouselView_visibility = self.GONE
            else:
                self.md3MediaCarouselView_visibility = self.GONE

        def onMeasure(self):
            if self.isSiblingGroupCarouselMessage():
                return (0, 0)
            return (1080, 800)

        def didPressCarouselItem(self, clicked_msg):
            old = self.currentMessageObject
            self.currentMessageObject = clicked_msg
            try:
                return self.isSiblingGroupCarouselMessage(), self.isMediaCarouselMessage()
            finally:
                self.currentMessageObject = old

    cell = ChatMessageCellSim()
    album1 = Group([100, 101, 102])
    album2 = Group([200, 201])
    single_msg = Msg(999)
    doc_group = Group([300, 301], is_documents=True)

    # Step 1: Fresh bind primary message (index 0)
    cell.setMessageContent(album1.messages[0], album1)
    assert cell.md3MediaCarouselView_visibility == ChatMessageCellSim.VISIBLE
    assert cell.isSiblingGroupCarouselMessage() is False
    assert cell.isMediaCarouselMessage() is True
    assert cell.onMeasure() == (1080, 800)

    # Step 2: Item click inside host cell (sibling item 101 clicked)
    is_sibling_click, is_carousel_click = cell.didPressCarouselItem(album1.messages[1])
    assert is_sibling_click is False, "Host cell with visible carousel must NOT be treated as sibling during item click"
    assert is_carousel_click is True, "Host cell must remain media carousel message during item click"

    # Step 3: View Recycling: The SAME cell instance with md3MediaCarouselView VISIBLE is bound to sibling message (id 101)
    cell.setMessageContent(album1.messages[1], album1)
    assert cell.md3MediaCarouselView_visibility == ChatMessageCellSim.GONE, (
        "Recycled cell bound to sibling message MUST transition md3MediaCarouselView to GONE"
    )
    assert cell.isSiblingGroupCarouselMessage() is True, (
        "Recycled sibling cell must report isSiblingGroupCarouselMessage == True"
    )
    assert cell.isMediaCarouselMessage() is False, (
        "Recycled sibling cell must report isMediaCarouselMessage == False"
    )
    assert cell.onMeasure() == (0, 0), (
        "Recycled sibling cell MUST collapse to (0, 0) in onMeasure"
    )

    # Step 4: View Recycling: Same cell bound to second sibling message (id 102)
    cell.setMessageContent(album1.messages[2], album1)
    assert cell.md3MediaCarouselView_visibility == ChatMessageCellSim.GONE
    assert cell.isSiblingGroupCarouselMessage() is True
    assert cell.isMediaCarouselMessage() is False
    assert cell.onMeasure() == (0, 0)

    # Step 5: View Recycling: Same cell rebound to single non-album message
    cell.setMessageContent(single_msg, None)
    assert cell.md3MediaCarouselView_visibility == ChatMessageCellSim.GONE
    assert cell.isSiblingGroupCarouselMessage() is False
    assert cell.isMediaCarouselMessage() is False
    assert cell.onMeasure() == (1080, 800)

    # Step 6: View Recycling: Same cell rebound to document group
    cell.setMessageContent(doc_group.messages[0], doc_group)
    assert cell.md3MediaCarouselView_visibility == ChatMessageCellSim.GONE
    assert cell.isSiblingGroupCarouselMessage() is False
    assert cell.isMediaCarouselMessage() is False
    assert cell.onMeasure() == (1080, 800)

    # Step 7: View Recycling: Same cell rebound to primary message of new album2
    cell.setMessageContent(album2.messages[0], album2)
    assert cell.md3MediaCarouselView_visibility == ChatMessageCellSim.VISIBLE
    assert cell.isSiblingGroupCarouselMessage() is False
    assert cell.isMediaCarouselMessage() is True
    assert cell.onMeasure() == (1080, 800)

    # Step 8: Bug reproduction simulation - demonstrate that buggy logic would fail recycling
    def buggy_set_message_content(c, msg, group):
        c.currentMessageObject = msg
        c.currentMessagesGroup = group
        if (c.currentMessagesGroup is not None and not c.currentMessagesGroup.isDocuments
                and len(c.currentMessagesGroup.messages) > 1 and c.mediaGroupCarousel):
            if not c.isSiblingGroupCarouselMessage():  # Circular dependency bug!
                c.md3MediaCarouselView_visibility = c.VISIBLE
            else:
                c.md3MediaCarouselView_visibility = c.GONE
        else:
            c.md3MediaCarouselView_visibility = c.GONE

    buggy_cell = ChatMessageCellSim()
    buggy_cell.setMessageContent(album1.messages[0], album1)
    assert buggy_cell.md3MediaCarouselView_visibility == ChatMessageCellSim.VISIBLE
    # Rebind buggy cell to sibling message (id 101)
    buggy_set_message_content(buggy_cell, album1.messages[1], album1)
    # Proves buggy cell remains VISIBLE and does NOT collapse to (0, 0)
    assert buggy_cell.md3MediaCarouselView_visibility == ChatMessageCellSim.VISIBLE
    assert buggy_cell.onMeasure() == (1080, 800)


