package app.exteraless.chats;

import android.net.Uri;
import android.text.TextUtils;

import com.radolyn.ayugram.AyuConstants;
import com.radolyn.ayugram.database.entities.DeletedMessage;
import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.proprietary.AyuHistoryHook;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import xyz.nextalone.nagram.NaConfig;

public final class DeletedReplyQuote {

    private static final class Author {

        final String name;
        final TLRPC.User user;
        final TLRPC.Chat chat;
        final int messageId;

        Author(String name, TLRPC.User user, TLRPC.Chat chat, int messageId) {
            this.name = name;
            this.user = user;
            this.chat = chat;
            this.messageId = messageId;
        }
    }

    public static final class Prefix {

        public final String text;
        private final ArrayList<TLRPC.MessageEntity> entities;

        Prefix(String text, ArrayList<TLRPC.MessageEntity> entities) {
            this.text = text;
            this.entities = entities;
        }

        public CharSequence prepend(CharSequence body) {
            return TextUtils.isEmpty(body) ? text : TextUtils.concat(text, "\n", body);
        }

        public ArrayList<TLRPC.MessageEntity> prependEntities(CharSequence body, ArrayList<TLRPC.MessageEntity> bodyEntities) {
            ArrayList<TLRPC.MessageEntity> result = new ArrayList<>(entities);
            if (bodyEntities != null && !TextUtils.isEmpty(body)) {
                int shift = text.length() + 1;
                for (TLRPC.MessageEntity entity : bodyEntities) {
                    entity.offset += shift;
                    result.add(entity);
                }
            }
            return result;
        }
    }

    private static final HashSet<String> quotedGroups = new HashSet<>();

    private DeletedReplyQuote() {
    }

    private static String groupId(SendMessagesHelper.SendMessageParams params) {
        if (params.params == null) {
            return null;
        }
        String groupId = params.params.get("groupId");
        return groupId == null || "0".equals(groupId) ? null : groupId;
    }

    private static synchronized boolean claimGroupQuote(String groupId, boolean last) {
        if (groupId == null) {
            return true;
        }
        boolean claimed = quotedGroups.add(groupId);
        if (last) {
            quotedGroups.remove(groupId);
        }
        return claimed;
    }

    public static void rewrite(int currentAccount, SendMessagesHelper.SendMessageParams params) {
        if (params == null || params.retryMessageObject != null
                || !NaConfig.INSTANCE.getReplyToDeletedAsQuote().Bool()) {
            return;
        }
        ChatActivity.ReplyQuote replyQuote = params.replyQuote;
        MessageObject source = params.replyToMsg != null ? params.replyToMsg
                : (replyQuote == null ? null : replyQuote.message);
        if (source == null || source.messageOwner == null) {
            return;
        }
        long selfId = UserConfig.getInstance(currentAccount).clientUserId;
        if (!AyuMessagesController.getInstance()
                .isAyuDeletedMessageId(selfId, source.getDialogId(), source.getId())) {
            return;
        }
        Author author = resolveAuthor(currentAccount, source);
        if (author == null || TextUtils.isEmpty(author.name)) {
            return;
        }
        String groupId = groupId(params);
        if (!claimGroupQuote(groupId, groupId != null && params.params.containsKey("final"))) {
            params.replyToMsg = params.replyToTopMsg;
            params.replyQuote = null;
            return;
        }
        CharSequence quoted = replyQuote != null ? replyQuote.getText() : source.messageText;
        String body = quoted == null ? "" : quoted.toString();
        String header = author.name;
        String quote = header + "\n" + body;
        String reply = params.message != null ? params.message : params.caption;
        String prefix = TextUtils.isEmpty(reply) ? quote : quote + "\n";

        if (params.entities == null) {
            params.entities = new ArrayList<>();
        }
        for (TLRPC.MessageEntity entity : params.entities) {
            entity.offset += prefix.length();
        }
        params.entities.add(blockquote(quote.length()));
        params.entities.add(bold(header.length()));
        TLRPC.MessageEntity authorEntity = authorEntity(author, header.length());
        if (authorEntity != null) {
            params.entities.add(authorEntity);
        }
        if (params.message == null) {
            params.caption = prefix + (params.caption == null ? "" : params.caption);
        } else {
            params.message = prefix + params.message;
        }
        params.replyToMsg = params.replyToTopMsg;
        params.replyQuote = null;
    }

    public static int quoteLength(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return 0;
        }
        String text = messageObject.messageOwner.message;
        ArrayList<TLRPC.MessageEntity> entities = messageObject.messageOwner.entities;
        if (TextUtils.isEmpty(text) || entities == null || entities.isEmpty()) {
            return 0;
        }
        int header = text.indexOf('\n');
        if (header <= 0) {
            return 0;
        }
        TLRPC.MessageEntity quote = null;
        boolean bold = false;
        for (TLRPC.MessageEntity entity : entities) {
            if (entity.offset != 0) {
                continue;
            }
            if (entity instanceof TLRPC.TL_messageEntityBlockquote && entity.collapsed) {
                quote = entity;
            } else if (entity instanceof TLRPC.TL_messageEntityBold && entity.length == header) {
                bold = true;
            }
        }
        if (quote == null || !bold || quote.length <= header || quote.length > text.length()) {
            return 0;
        }
        if (quote.length < text.length() && text.charAt(quote.length) != '\n') {
            return 0;
        }
        return quote.length;
    }

    public static int findOriginal(int currentAccount, MessageObject messageObject) {
        int length = quoteLength(messageObject);
        if (length <= 0) {
            return 0;
        }
        TLRPC.Message message = messageObject.messageOwner;
        int header = message.message.indexOf('\n');
        String body = message.message.substring(header + 1, length).trim();
        long author = 0;
        int postId = 0;
        MessagesController controller = MessagesController.getInstance(currentAccount);
        for (TLRPC.MessageEntity entity : message.entities) {
            if (entity.offset != 0 || entity.length != header) {
                continue;
            }
            if (entity instanceof TLRPC.TL_messageEntityMentionName) {
                author = ((TLRPC.TL_messageEntityMentionName) entity).user_id;
            } else if (entity instanceof TLRPC.TL_inputMessageEntityMentionName && ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id != null) {
                author = ((TLRPC.TL_inputMessageEntityMentionName) entity).user_id.user_id;
            } else if (entity instanceof TLRPC.TL_messageEntityTextUrl && entity.url != null) {
                Uri uri = Uri.parse(entity.url);
                if ("privatepost".equals(uri.getHost())) {
                    author = -parseLong(uri.getQueryParameter("channel"));
                    postId = (int) parseLong(uri.getQueryParameter("post"));
                } else if ("resolve".equals(uri.getHost())) {
                    TLObject peer = controller.getUserOrChat(uri.getQueryParameter("domain"));
                    if (peer instanceof TLRPC.User) {
                        author = ((TLRPC.User) peer).id;
                    } else if (peer instanceof TLRPC.Chat) {
                        author = -((TLRPC.Chat) peer).id;
                    }
                }
            }
        }
        long selfId = UserConfig.getInstance(currentAccount).clientUserId;
        long dialogId = messageObject.getDialogId();
        AyuMessagesController ayu = AyuMessagesController.getInstance();
        try {
            if (postId > 0 && ayu.getMessage(selfId, dialogId, postId) != null) {
                return postId;
            }
            if (body.isEmpty()) {
                return 0;
            }
            List<DeletedMessageFull> candidates = ayu.getOlderMessagesBefore(selfId, dialogId, message.id, 300);
            ArrayList<DeletedMessageFull> media = new ArrayList<>();
            for (DeletedMessageFull full : candidates) {
                DeletedMessage deleted = full.message;
                if (deleted == null || author != 0 && deleted.fromId != author) {
                    continue;
                }
                if (deleted.text != null && deleted.text.contains(body)) {
                    return deleted.messageId;
                }
                if (deleted.documentType != AyuConstants.DOCUMENT_TYPE_NONE) {
                    media.add(full);
                }
            }
            for (DeletedMessageFull full : media) {
                MessageObject restored = new MessageObject(currentAccount, AyuHistoryHook.map(full, currentAccount), false, false);
                if (restored.messageText != null && body.equals(restored.messageText.toString().trim())) {
                    return full.message.messageId;
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Prefix prefix(int currentAccount, MessageObject messageObject) {
        int length = quoteLength(messageObject);
        if (length <= 0) {
            return null;
        }
        MessagesController controller = MessagesController.getInstance(currentAccount);
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        for (TLRPC.MessageEntity entity : messageObject.messageOwner.entities) {
            if (entity.offset + entity.length <= length) {
                TLRPC.MessageEntity copy = copyForSend(controller, entity);
                if (copy != null) {
                    entities.add(copy);
                }
            }
        }
        return new Prefix(messageObject.messageOwner.message.substring(0, length), entities);
    }

    public static boolean canRemove(MessageObject messageObject) {
        int length = quoteLength(messageObject);
        if (length <= 0) {
            return false;
        }
        if (!messageObject.isMediaEmpty()) {
            return true;
        }
        String text = messageObject.messageOwner.message;
        return length + 1 < text.length() && !TextUtils.isEmpty(text.substring(length + 1).trim());
    }

    public static void remove(int currentAccount, MessageObject messageObject) {
        if (!canRemove(messageObject)) {
            return;
        }
        int length = quoteLength(messageObject);
        TLRPC.Message message = messageObject.messageOwner;
        int cut = length < message.message.length() ? length + 1 : length;
        MessagesController controller = MessagesController.getInstance(currentAccount);
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        for (TLRPC.MessageEntity entity : message.entities) {
            int end = entity.offset + entity.length;
            if (end <= cut) {
                continue;
            }
            TLRPC.MessageEntity copy = copyForSend(controller, entity);
            if (copy == null) {
                continue;
            }
            int start = Math.max(entity.offset, cut);
            copy.offset = start - cut;
            copy.length = end - start;
            entities.add(copy);
        }
        messageObject.editingMessage = message.message.substring(cut);
        messageObject.editingMessageEntities = entities;
        messageObject.editingMessageSearchWebPage = message.media instanceof TLRPC.TL_messageMediaWebPage;
        SendMessagesHelper.getInstance(currentAccount).editMessage(messageObject, null, null, null, null, null, null, false, messageObject.hasMediaSpoilers(), null);
    }

    private static TLRPC.MessageEntity copyForSend(MessagesController controller, TLRPC.MessageEntity entity) {
        if (entity instanceof TLRPC.TL_messageEntityMentionName) {
            TLRPC.InputUser inputUser = controller.getInputUser(((TLRPC.TL_messageEntityMentionName) entity).user_id);
            if (inputUser == null || inputUser instanceof TLRPC.TL_inputUserEmpty) {
                return null;
            }
            TLRPC.TL_inputMessageEntityMentionName mention = new TLRPC.TL_inputMessageEntityMentionName();
            mention.user_id = inputUser;
            mention.offset = entity.offset;
            mention.length = entity.length;
            return mention;
        }
        try {
            SerializedData out = new SerializedData(entity.getObjectSize());
            entity.serializeToStream(out);
            SerializedData in = new SerializedData(out.toByteArray());
            TLRPC.MessageEntity copy = TLRPC.MessageEntity.TLdeserialize(in, in.readInt32(false), false);
            out.cleanup();
            in.cleanup();
            return copy;
        } catch (Exception e) {
            return null;
        }
    }

    private static TLRPC.TL_messageEntityBlockquote blockquote(int length) {
        TLRPC.TL_messageEntityBlockquote quote = new TLRPC.TL_messageEntityBlockquote();
        quote.offset = 0;
        quote.length = length;
        quote.collapsed = true;
        return quote;
    }

    private static TLRPC.TL_messageEntityBold bold(int length) {
        TLRPC.TL_messageEntityBold bold = new TLRPC.TL_messageEntityBold();
        bold.offset = 0;
        bold.length = length;
        return bold;
    }

    private static TLRPC.MessageEntity authorEntity(Author author, int length) {
        if (author.user != null) {
            TLRPC.TL_inputMessageEntityMentionName mention = new TLRPC.TL_inputMessageEntityMentionName();
            TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
            inputUser.user_id = author.user.id;
            inputUser.access_hash = author.user.access_hash;
            mention.user_id = inputUser;
            mention.offset = 0;
            mention.length = length;
            return mention;
        }
        String url = chatUrl(author.chat, author.messageId);
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
        entity.offset = 0;
        entity.length = length;
        entity.url = url;
        return entity;
    }

    private static String chatUrl(TLRPC.Chat chat, int messageId) {
        if (chat == null) {
            return null;
        }
        String username = ChatObject.getPublicUsername(chat);
        if (!TextUtils.isEmpty(username)) {
            return "tg://resolve?domain=" + username;
        }
        if (!ChatObject.isChannel(chat) || chat.id == 0 || messageId <= 0) {
            return null;
        }
        return "tg://privatepost?channel=" + chat.id + "&post=" + messageId;
    }

    private static Author resolveAuthor(int currentAccount, MessageObject messageObject) {
        TLRPC.Message message = messageObject.messageOwner;
        MessagesController controller = MessagesController.getInstance(currentAccount);
        Author author = peerAuthor(controller, message.from_id, message.id);
        if (author != null) {
            return author;
        }
        if (message.post && message.peer_id instanceof TLRPC.TL_peerChannel) {
            author = chatAuthor(controller.getChat(message.peer_id.channel_id), message.id);
            if (author != null) {
                return author;
            }
        }
        long senderId = messageObject.getSenderId();
        if (senderId > 0) {
            author = userAuthor(controller.getUser(senderId), message.id);
        } else if (senderId < 0) {
            author = chatAuthor(controller.getChat(-senderId), message.id);
        }
        if (author != null) {
            return author;
        }
        if (message.fwd_from != null) {
            author = peerAuthor(controller, message.fwd_from.from_id, message.id);
            if (author != null) {
                return author;
            }
            author = peerAuthor(controller, message.fwd_from.saved_from_peer, message.id);
            if (author != null) {
                return author;
            }
            if (!TextUtils.isEmpty(message.fwd_from.from_name)) {
                return new Author(message.fwd_from.from_name, null, null, message.id);
            }
        }
        return userAuthor(controller.getUser(UserConfig.getInstance(currentAccount).clientUserId),
                message.id);
    }

    private static Author peerAuthor(MessagesController controller, TLRPC.Peer peer, int messageId) {
        if (peer instanceof TLRPC.TL_peerUser) {
            return userAuthor(controller.getUser(peer.user_id), messageId);
        }
        if (peer instanceof TLRPC.TL_peerChannel) {
            return chatAuthor(controller.getChat(peer.channel_id), messageId);
        }
        if (peer instanceof TLRPC.TL_peerChat) {
            return chatAuthor(controller.getChat(peer.chat_id), messageId);
        }
        return null;
    }

    private static Author userAuthor(TLRPC.User user, int messageId) {
        if (user == null) {
            return null;
        }
        String name = UserObject.getUserName(user);
        return TextUtils.isEmpty(name) ? null : new Author(name, user, null, messageId);
    }

    private static Author chatAuthor(TLRPC.Chat chat, int messageId) {
        if (chat == null || TextUtils.isEmpty(chat.title)) {
            return null;
        }
        return new Author(chat.title, null, chat, messageId);
    }
}
