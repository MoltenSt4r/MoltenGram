package app.exteraless.chats;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

public final class LinkedCustomEmoji {

    public static final String PREFIX = "tg://emoji?id=";

    private LinkedCustomEmoji() {
    }

    public static boolean canSend(int account) {
        return NaConfig.INSTANCE.getCustomEmojiForNonPremium().Bool()
                && !UserConfig.getInstance(account).isPremium();
    }

    public static boolean isLink(TLRPC.MessageEntity entity) {
        return entity instanceof TLRPC.TL_messageEntityTextUrl && entity.url != null && entity.url.startsWith(PREFIX);
    }

    public static TLRPC.TL_messageEntityTextUrl toLink(TLRPC.TL_messageEntityCustomEmoji emoji) {
        TLRPC.TL_messageEntityTextUrl link = new TLRPC.TL_messageEntityTextUrl();
        link.offset = emoji.offset;
        link.length = emoji.length;
        link.url = PREFIX + emoji.document_id;
        return link;
    }

    public static boolean parse(CharSequence text, List<TLRPC.MessageEntity> entities) {
        if (text == null || entities == null || entities.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < entities.size(); i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            if (!isLink(entity) || entity.offset < 0 || entity.length <= 0 || entity.offset + entity.length > text.length()) {
                continue;
            }
            long documentId;
            try {
                documentId = Long.parseLong(entity.url.substring(PREFIX.length()));
            } catch (NumberFormatException e) {
                FileLog.e(e);
                continue;
            }
            int[] emojiOnly = new int[1];
            ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(text.subSequence(entity.offset, entity.offset + entity.length).toString(), emojiOnly);
            if (documentId == 0 || emojiOnly[0] <= 0 || emojis.size() != 1) {
                continue;
            }
            TLRPC.TL_messageEntityCustomEmoji emoji = new TLRPC.TL_messageEntityCustomEmoji();
            emoji.offset = entity.offset;
            emoji.length = entity.length;
            emoji.document_id = documentId;
            emoji.local = true;
            entities.set(i, emoji);
            changed = true;
        }
        return changed;
    }

    public static ArrayList<TLRPC.MessageEntity> parsedCopy(CharSequence text, List<TLRPC.MessageEntity> entities) {
        if (entities == null) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> copy = new ArrayList<>(entities);
        parse(text, copy);
        return copy;
    }

    public static void replaceForSend(int account, long dialogId, List<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty() || !canSend(account) || dialogId == UserConfig.getInstance(account).getClientUserId()) {
            return;
        }
        HashSet<Long> groupEmoji = null;
        if (dialogId < 0) {
            TLRPC.ChatFull chatFull = MessagesController.getInstance(account).getChatFull(-dialogId);
            if (chatFull != null && chatFull.emojiset != null) {
                TLRPC.TL_messages_stickerSet set = MediaDataController.getInstance(account).getGroupStickerSetById(chatFull.emojiset);
                if (set != null && set.documents != null) {
                    groupEmoji = new HashSet<>();
                    for (TLRPC.Document document : set.documents) {
                        groupEmoji.add(document.id);
                    }
                }
            }
        }
        for (int i = 0; i < entities.size(); i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            if (!(entity instanceof TLRPC.TL_messageEntityCustomEmoji)) {
                continue;
            }
            TLRPC.TL_messageEntityCustomEmoji emoji = (TLRPC.TL_messageEntityCustomEmoji) entity;
            if (groupEmoji != null && groupEmoji.contains(emoji.document_id)) {
                continue;
            }
            TLRPC.Document document = emoji.document != null ? emoji.document : AnimatedEmojiDrawable.findDocument(account, emoji.document_id);
            if (!MessageObject.isFreeEmoji(document)) {
                entities.set(i, toLink(emoji));
            }
        }
    }

    public static boolean isLinkOnlyMessage(TLRPC.Message message) {
        if (message == null || message.entities == null || message.entities.isEmpty() || !MessageObject.isMediaEmptyWebpage(message)) {
            return false;
        }
        boolean found = false;
        for (TLRPC.MessageEntity entity : message.entities) {
            if (entity instanceof TLRPC.TL_messageEntityTextUrl) {
                if (!isLink(entity)) {
                    return false;
                }
                found = true;
            } else if (entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityEmail) {
                return false;
            } else if (entity instanceof TLRPC.TL_messageEntityCustomEmoji && ((TLRPC.TL_messageEntityCustomEmoji) entity).local) {
                found = true;
            }
        }
        return found;
    }
}
