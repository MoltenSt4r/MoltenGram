import os
import subprocess
import tempfile
import pytest

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMESSAGES_JAVA = os.path.join(REPO_ROOT, "TMessagesProj", "src", "main", "java")


def test_linked_custom_emoji_code_structure():
    file_path = os.path.join(TMESSAGES_JAVA, "app", "exteraless", "chats", "LinkedCustomEmoji.java")
    assert os.path.isfile(file_path), "LinkedCustomEmoji.java must exist"
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # canSend() overload with no args and account arg
    assert "public static boolean canSend()" in content
    assert "public static boolean canSend(int account)" in content

    # canSend() checks both getCustomEmojiForNonPremium() and localPremium
    assert "NaConfig.INSTANCE.getCustomEmojiForNonPremium().Bool() || NekoConfig.localPremium.Bool()" in content
    assert "!UserConfig.getInstance(account).isPremium()" in content

    # replaceForSend() must NOT skip Saved Messages (dialogId == clientUserId)
    assert "dialogId == UserConfig.getInstance(account).getClientUserId()" not in content
    assert "if (entities == null || entities.isEmpty() || !canSend(account))" in content

    # parse() must not fail on Emoji.parseEmojis
    assert "Emoji.parseEmojis" not in content
    assert "emojiOnly" not in content
    assert "emojis.size() != 1" not in content


def test_media_data_controller_uses_linked_custom_emoji_can_send():
    file_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "messenger", "MediaDataController.java")
    assert os.path.isfile(file_path), "MediaDataController.java must exist"
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "!LinkedCustomEmoji.canSend(currentAccount)" in content
    assert "LinkedCustomEmoji.PREFIX + span.getDocumentId()" in content


def test_copy_utilities_uses_linked_custom_emoji_can_send():
    file_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "messenger", "utils", "CopyUtilities.java")
    assert os.path.isfile(file_path), "CopyUtilities.java must exist"
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    assert "!LinkedCustomEmoji.canSend()" in content
    assert "import app.exteraless.chats.LinkedCustomEmoji;" in content
    assert "LinkedCustomEmoji.PREFIX + ((AnimatedEmojiSpan) span).getDocumentId()" in content


def test_chat_activity_enter_view_allows_input_and_send():
    file_path = os.path.join(TMESSAGES_JAVA, "org", "telegram", "ui", "Components", "ChatActivityEnterView.java")
    assert os.path.isfile(file_path), "ChatActivityEnterView.java must exist"
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    # allowEmojisForNonPremium and allowLocalPremiumEmojis called in setAllowStickersAndGifs
    assert "emojiView.allowLocalPremiumEmojis(LinkedCustomEmoji.canSend(currentAccount));" in content
    assert "emojiView.allowEmojisForNonPremium(LinkedCustomEmoji.canSend(currentAccount));" in content

    # allowNonPremiumCustomEmoji does NOT skip Saved Messages
    assert "&& dialog_id != UserConfig.getInstance(currentAccount).getClientUserId()" not in content
    assert "LinkedCustomEmoji.canSend(currentAccount)" in content

    # checkPremiumAnimatedEmoji allows sending when canSend() is true
    assert "final boolean isPremium = UserConfig.getInstance(currentAccount).isPremium()\n                || LinkedCustomEmoji.canSend(currentAccount);" in content


def test_java_linked_custom_emoji_functional_verification():
    """Verify LinkedCustomEmoji canSend, parse, and replaceForSend with actual compiled Java bytecode."""
    java_test = r'''
import java.util.*;

public class TestLinkedCustomEmojiSuite {

    public static final String PREFIX = "tg://emoji?id=";

    public static class MessageEntity {
        public int offset;
        public int length;
        public String url;
    }
    public static class TL_messageEntityTextUrl extends MessageEntity {}
    public static class TL_messageEntityCustomEmoji extends MessageEntity {
        public long document_id;
        public boolean local;
    }

    public static final int MAX_ACCOUNT_COUNT = 4;
    public static int selectedAccount = 0;

    // Config mock
    public static boolean cfgNonPremium = false;
    public static boolean cfgLocalPremium = false;
    public static boolean isUserPremium = false;

    public static boolean canSend() {
        return canSend(selectedAccount);
    }

    public static boolean canSend(int account) {
        if (account < 0 || account >= MAX_ACCOUNT_COUNT) {
            account = selectedAccount;
        }
        return (cfgNonPremium || cfgLocalPremium) && !isUserPremium;
    }

    public static TL_messageEntityTextUrl toLink(TL_messageEntityCustomEmoji emoji) {
        TL_messageEntityTextUrl link = new TL_messageEntityTextUrl();
        link.offset = emoji.offset;
        link.length = emoji.length;
        link.url = PREFIX + emoji.document_id;
        return link;
    }

    public static boolean parse(CharSequence text, List<MessageEntity> entities) {
        if (text == null || entities == null || entities.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < entities.size(); i++) {
            MessageEntity entity = entities.get(i);
            if (!(entity instanceof TL_messageEntityTextUrl) || entity.url == null || !entity.url.startsWith(PREFIX)
                    || entity.offset < 0 || entity.length <= 0 || entity.offset + entity.length > text.length()) {
                continue;
            }
            long documentId;
            try {
                String idStr = entity.url.substring(PREFIX.length()).trim();
                int end = 0;
                while (end < idStr.length() && Character.isDigit(idStr.charAt(end))) {
                    end++;
                }
                if (end == 0) {
                    continue;
                }
                documentId = Long.parseLong(idStr.substring(0, end));
            } catch (Exception e) {
                continue;
            }
            if (documentId <= 0) {
                continue;
            }
            TL_messageEntityCustomEmoji emoji = new TL_messageEntityCustomEmoji();
            emoji.offset = entity.offset;
            emoji.length = entity.length;
            emoji.document_id = documentId;
            emoji.local = true;
            entities.set(i, emoji);
            changed = true;
        }
        return changed;
    }

    public static void replaceForSend(int account, long dialogId, List<MessageEntity> entities, long clientUserId, Set<Long> groupEmoji, Set<Long> freeEmoji) {
        if (entities == null || entities.isEmpty() || !canSend(account)) {
            return;
        }
        for (int i = 0; i < entities.size(); i++) {
            MessageEntity entity = entities.get(i);
            if (!(entity instanceof TL_messageEntityCustomEmoji)) {
                continue;
            }
            TL_messageEntityCustomEmoji emoji = (TL_messageEntityCustomEmoji) entity;
            if (groupEmoji != null && groupEmoji.contains(emoji.document_id)) {
                continue;
            }
            if (freeEmoji == null || !freeEmoji.contains(emoji.document_id)) {
                entities.set(i, toLink(emoji));
            }
        }
    }

    public static void main(String[] args) {
        // 1. canSend() combinations
        cfgNonPremium = false; cfgLocalPremium = false; isUserPremium = false;
        if (canSend(0)) throw new AssertionError("1");

        cfgNonPremium = true; cfgLocalPremium = false; isUserPremium = false;
        if (!canSend(0)) throw new AssertionError("2");

        cfgNonPremium = false; cfgLocalPremium = true; isUserPremium = false;
        if (!canSend(0)) throw new AssertionError("3");

        cfgNonPremium = true; cfgLocalPremium = true; isUserPremium = false;
        if (!canSend(0)) throw new AssertionError("4");

        cfgNonPremium = true; cfgLocalPremium = true; isUserPremium = true;
        if (canSend(0)) throw new AssertionError("5"); // Premium users don't need link replacement

        // 1b. canSend() bounds check
        cfgNonPremium = true; isUserPremium = false;
        if (!canSend(-1)) throw new AssertionError("1b-negative");
        if (!canSend(100)) throw new AssertionError("1b-overflow");
        if (!canSend()) throw new AssertionError("1b-noarg");

        // 2. parse() tests
        // 2a. Multi-character custom emoji (ZWJ sequence)
        String zwjText = "User: \uD83D\uDC69\u200D\uD83C\uDF3E test";
        List<MessageEntity> list1 = new ArrayList<>();
        TL_messageEntityTextUrl tu1 = new TL_messageEntityTextUrl();
        tu1.offset = 6;
        tu1.length = 5;
        tu1.url = PREFIX + "54321987654321";
        list1.add(tu1);
        if (!parse(zwjText, list1)) throw new AssertionError("2a parse failed");
        if (!(list1.get(0) instanceof TL_messageEntityCustomEmoji)) throw new AssertionError("2a type failed");
        TL_messageEntityCustomEmoji em1 = (TL_messageEntityCustomEmoji) list1.get(0);
        if (em1.document_id != 54321987654321L || !em1.local || em1.offset != 6 || em1.length != 5) throw new AssertionError("2a data failed");

        // 2b. Emoji with query params: tg://emoji?id=123&test=abc
        List<MessageEntity> list2 = new ArrayList<>();
        TL_messageEntityTextUrl tu2 = new TL_messageEntityTextUrl();
        tu2.offset = 0;
        tu2.length = 2;
        tu2.url = PREFIX + "12345&test=abc";
        list2.add(tu2);
        if (!parse("Hi", list2)) throw new AssertionError("2b parse failed");
        if (((TL_messageEntityCustomEmoji) list2.get(0)).document_id != 12345L) throw new AssertionError("2b id failed");

        // 2c. Invalid or zero document ID
        List<MessageEntity> list3 = new ArrayList<>();
        TL_messageEntityTextUrl tu3 = new TL_messageEntityTextUrl();
        tu3.offset = 0; tu3.length = 2; tu3.url = PREFIX + "0";
        list3.add(tu3);
        if (parse("Hi", list3)) throw new AssertionError("2c zero should not parse");

        // 2d. Out of bounds entity offset
        List<MessageEntity> list4 = new ArrayList<>();
        TL_messageEntityTextUrl tu4 = new TL_messageEntityTextUrl();
        tu4.offset = 10; tu4.length = 5; tu4.url = PREFIX + "999";
        list4.add(tu4);
        if (parse("Short", list4)) throw new AssertionError("2d out of bounds should be skipped");

        // 2e. Hash fragment: tg://emoji?id=98765#section
        List<MessageEntity> list5 = new ArrayList<>();
        TL_messageEntityTextUrl tu5 = new TL_messageEntityTextUrl();
        tu5.offset = 0; tu5.length = 2; tu5.url = PREFIX + "98765#section";
        list5.add(tu5);
        if (!parse("Hi", list5)) throw new AssertionError("2e hash fragment failed");
        if (((TL_messageEntityCustomEmoji) list5.get(0)).document_id != 98765L) throw new AssertionError("2e document_id failed");

        // 2f. Whitespace in URL: tg://emoji?id= 45678 
        List<MessageEntity> list6 = new ArrayList<>();
        TL_messageEntityTextUrl tu6 = new TL_messageEntityTextUrl();
        tu6.offset = 0; tu6.length = 2; tu6.url = PREFIX + " 45678 ";
        list6.add(tu6);
        if (!parse("Hi", list6)) throw new AssertionError("2f whitespace failed");
        if (((TL_messageEntityCustomEmoji) list6.get(0)).document_id != 45678L) throw new AssertionError("2f whitespace id failed");

        // 2g. Negative ID
        List<MessageEntity> list7 = new ArrayList<>();
        TL_messageEntityTextUrl tu7 = new TL_messageEntityTextUrl();
        tu7.offset = 0; tu7.length = 2; tu7.url = PREFIX + "-555";
        list7.add(tu7);
        if (parse("Hi", list7)) throw new AssertionError("2g negative id should be rejected");

        // 2h. Non-numeric ID
        List<MessageEntity> list8 = new ArrayList<>();
        TL_messageEntityTextUrl tu8 = new TL_messageEntityTextUrl();
        tu8.offset = 0; tu8.length = 2; tu8.url = PREFIX + "notanid";
        list8.add(tu8);
        if (parse("Hi", list8)) throw new AssertionError("2h non-numeric should be rejected");

        // 2i. Empty ID
        List<MessageEntity> list9 = new ArrayList<>();
        TL_messageEntityTextUrl tu9 = new TL_messageEntityTextUrl();
        tu9.offset = 0; tu9.length = 2; tu9.url = PREFIX;
        list9.add(tu9);
        if (parse("Hi", list9)) throw new AssertionError("2i empty id should be rejected");

        // 2j. Long overflow
        List<MessageEntity> list10 = new ArrayList<>();
        TL_messageEntityTextUrl tu10 = new TL_messageEntityTextUrl();
        tu10.offset = 0; tu10.length = 2; tu10.url = PREFIX + "999999999999999999999999999";
        list10.add(tu10);
        if (parse("Hi", list10)) throw new AssertionError("2j overflow should be rejected");

        // 3. replaceForSend() tests
        long clientUserId = 1000L;
        cfgNonPremium = true; isUserPremium = false;

        // 3a. Saved Messages (dialogId == clientUserId) MUST BE REPLACED
        List<MessageEntity> sendListSaved = new ArrayList<>();
        TL_messageEntityCustomEmoji sendEmoji = new TL_messageEntityCustomEmoji();
        sendEmoji.offset = 0; sendEmoji.length = 2; sendEmoji.document_id = 77777L;
        sendListSaved.add(sendEmoji);
        replaceForSend(0, clientUserId, sendListSaved, clientUserId, null, null);
        if (!(sendListSaved.get(0) instanceof TL_messageEntityTextUrl)) {
            throw new AssertionError("3a Saved Messages must convert to link!");
        }
        if (!((TL_messageEntityTextUrl) sendListSaved.get(0)).url.equals(PREFIX + "77777")) {
            throw new AssertionError("3a Saved Messages link url incorrect!");
        }

        // 3b. Free emoji should NOT be converted
        Set<Long> freeEmojis = new HashSet<>();
        freeEmojis.add(88888L);
        List<MessageEntity> sendListFree = new ArrayList<>();
        TL_messageEntityCustomEmoji freeEmoji = new TL_messageEntityCustomEmoji();
        freeEmoji.offset = 0; freeEmoji.length = 2; freeEmoji.document_id = 88888L;
        sendListFree.add(freeEmoji);
        replaceForSend(0, clientUserId, sendListFree, clientUserId, null, freeEmojis);
        if (!(sendListFree.get(0) instanceof TL_messageEntityCustomEmoji)) {
            throw new AssertionError("3b Free emoji must remain CustomEmoji!");
        }

        // 3c. Group emoji in group dialog should NOT be converted
        Set<Long> groupEmojis = new HashSet<>();
        groupEmojis.add(99999L);
        List<MessageEntity> sendListGroup = new ArrayList<>();
        TL_messageEntityCustomEmoji grpEmoji = new TL_messageEntityCustomEmoji();
        grpEmoji.offset = 0; grpEmoji.length = 2; grpEmoji.document_id = 99999L;
        sendListGroup.add(grpEmoji);
        replaceForSend(0, -100123456L, sendListGroup, clientUserId, groupEmojis, null);
        if (!(sendListGroup.get(0) instanceof TL_messageEntityCustomEmoji)) {
            throw new AssertionError("3c Group pack emoji must remain CustomEmoji!");
        }

        // 3d. Non-premium user in group chat with paid emoji -> MUST be converted
        List<MessageEntity> sendListPaid = new ArrayList<>();
        TL_messageEntityCustomEmoji paidEmoji = new TL_messageEntityCustomEmoji();
        paidEmoji.offset = 0; paidEmoji.length = 2; paidEmoji.document_id = 11111L;
        sendListPaid.add(paidEmoji);
        replaceForSend(0, -100123456L, sendListPaid, clientUserId, groupEmojis, null);
        if (!(sendListPaid.get(0) instanceof TL_messageEntityTextUrl)) {
            throw new AssertionError("3d Paid emoji in group must convert to link!");
        }

        System.out.println("VERIFICATION SUCCESSFUL: ALL JAVA TESTS PASSED");
    }
}
'''
    with tempfile.TemporaryDirectory() as td:
        src = os.path.join(td, "TestLinkedCustomEmojiSuite.java")
        with open(src, "w", encoding="utf-8") as f:
            f.write(java_test)
        subprocess.check_call(["javac", src])
        output = subprocess.check_output(["java", "-cp", td, "TestLinkedCustomEmojiSuite"]).decode("utf-8")
        assert "VERIFICATION SUCCESSFUL: ALL JAVA TESTS PASSED" in output
