package app.exteraless.feed;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.BulletinFactory;

import xyz.nextalone.nagram.helper.DoubleTap;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * Утилиты над сообщениями ленты.
 * Лента показывает посты из чужих каналов в одном синтетическом чате, поэтому её объекты
 * живут под сквозными id. Здесь собрано всё, что нужно для перехода между синтетическим
 * и настоящим представлением поста: копии сообщений, служебные строки (дата, разделитель
 * непрочитанного) и фильтр пунктов контекстного меню.
 */
public abstract class FeedMessageUtils {

    /**
     * Значение {@link MessageObject#searchType} у постов ленты.
     */
    public static final int SEARCH_TYPE_FEED = 4;

    private static final int CONTENT_TYPE_ACTION = 1;
    private static final int CONTENT_TYPE_UNREAD_DIVIDER = 2;

    private static final String PRIVATE_CHANNEL_LINK_MARKER = "/c/";

    private static final int DOUBLE_TAP_ACTION_TRANSLATE = DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE;
    private static final int DOUBLE_TAP_ACTION_REPLY = DoubleTap.DOUBLE_TAP_ACTION_REPLY;
    private static final int DOUBLE_TAP_ACTION_SAVE = DoubleTap.DOUBLE_TAP_ACTION_SAVE;
    private static final int DOUBLE_TAP_ACTION_TRANSLATE_LLM = DoubleTap.DOUBLE_TAP_ACTION_TRANSLATE_LLM;

    private static final int[] EXTRA_ALLOWED_FEED_OPTIONS = {36, 200, 203, 206};

    /**
     * Копирует ссылку на пост ленты: id берётся настоящий, канал — тот, из которого пост.
     */
    public static void copyFeedPostLink(final ChatActivity chatActivity, MessageObject message) {
        if (chatActivity == null || message == null) {
            return;
        }
        TLRPC.Chat chat = chatActivity.getMessagesController().getChat(-message.getDialogId());
        if (!ChatObject.isChannel(chat)) {
            return;
        }
        TLRPC.TL_channels_exportMessageLink request = new TLRPC.TL_channels_exportMessageLink();
        request.id = message.getRealId();
        request.channel = MessagesController.getInputChannel(chat);
        chatActivity.getConnectionsManager().sendRequest(request, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> onPostLinkExported(response, chatActivity)));
    }

    /**
     * Переносит результат перевода и пересказа на новый объект сообщения.
     */
    public static void copyTranslationState(MessageObject from, MessageObject to) {
        if (from == null || to == null || from == to) {
            return;
        }
        TLRPC.Message source = from.messageOwner;
        TLRPC.Message target = to.messageOwner;
        if (source == null || target == null) {
            return;
        }
        target.translatedText = source.translatedText;
        target.translatedToLanguage = source.translatedToLanguage;
        target.translatedVoiceTranscription = source.translatedVoiceTranscription;
        target.translatedPoll = source.translatedPoll;
        target.summaryText = source.summaryText;
        target.summarizedOpen = source.summarizedOpen;
        target.translatedSummaryText = source.translatedSummaryText;
        target.translatedSummaryLanguage = source.translatedSummaryLanguage;
    }

    /**
     * Служебная строка с датой над постом; дата округляется до начала суток.
     */
    public static MessageObject createDateHeader(int currentAccount, MessageObject message, int stableId) {
        TLRPC.TL_message header = new TLRPC.TL_message();
        header.message = LocaleController.formatDateChat(message.messageOwner.date);
        header.id = 0;

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(message.messageOwner.date * 1000L);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        header.date = (int) (calendar.getTimeInMillis() / 1000L);

        MessageObject result = new MessageObject(currentAccount, header, false, false);
        result.type = MessageObject.TYPE_DATE;
        result.contentType = CONTENT_TYPE_ACTION;
        result.isDateObject = true;
        result.stableId = stableId;
        return result;
    }

    /**
     * Строит копию поста в синтетической нумерации ленты и подменяет ею объект в хранилище.
     */
    public static MessageObject createReplacement(int currentAccount, long dialogId, MessageObject original) {
        if (original == null) {
            return null;
        }
        FeedController feedController = FeedController.getInstance(currentAccount);
        MessageObject stored = feedController.getMessage(dialogId, original.getRealId());
        if (stored == null) {
            return null;
        }
        TLRPC.TL_message copy = copyMessage(original.messageOwner);
        copy.id = stored.getId();
        copy.realId = stored.getRealId();
        copy.dialog_id = stored.getDialogId();

        MessageObject replacement = new MessageObject(currentAccount, copy, stored.replyMessageObject,
                null, null, null, null, true, true, 0L, false, false, false, SEARCH_TYPE_FEED);
        replacement.isPrimaryGroupMessage = stored.isPrimaryGroupMessage;
        replacement.localGroupId = stored.localGroupId;
        replacement.copyStableParams(stored);
        feedController.replaceMessage(stored, replacement);
        return replacement;
    }

    public static ArrayList<MessageObject> createReplacements(int currentAccount, long dialogId, ArrayList<MessageObject> originals) {
        ArrayList<MessageObject> result = new ArrayList<>();
        for (int i = 0; i < originals.size(); i++) {
            MessageObject replacement = createReplacement(currentAccount, dialogId, originals.get(i));
            if (replacement != null) {
                result.add(replacement);
            }
        }
        return result;
    }

    public static MessageObject createUnreadDivider(int currentAccount, int stableId) {
        TLRPC.TL_message divider = new TLRPC.TL_message();
        divider.message = "";
        divider.id = 0;

        MessageObject result = new MessageObject(currentAccount, divider, false, false);
        result.type = MessageObject.TYPE_LOADING;
        result.contentType = CONTENT_TYPE_UNREAD_DIVIDER;
        result.stableId = stableId;
        return result;
    }

    /**
     * Выкидывает из уже собранного контекстного меню пункты, неприменимые в ленте.
     * Три списка идут параллельно и режутся синхронно, начиная с конца.
     */
    public static void filterAllowedOptions(ArrayList<CharSequence> items, ArrayList<Integer> options, ArrayList<Integer> icons) {
        for (int i = options.size() - 1; i >= 0; i--) {
            if (!isAllowedFeedOption(options.get(i))) {
                icons.remove(i);
                items.remove(i);
                options.remove(i);
            }
        }
    }

    /**
     * Для пересылки и открытия обсуждений нужен пост в настоящей нумерации канала.
     */
    public static MessageObject getForwardingMessageObject(int currentAccount, boolean isFeedSearch, MessageObject message) {
        if (!isFeedSearch || message == null || message.getId() == message.getRealId()) {
            return message;
        }
        TLRPC.TL_message copy = copyMessage(message.messageOwner);
        copy.id = message.getRealId();
        copy.realId = 0;
        copy.dialog_id = message.getDialogId();

        MessageObject result = new MessageObject(currentAccount, copy, message.replyMessageObject,
                null, null, null, null, false, true, 0L, false, false, false);
        result.isPrimaryGroupMessage = message.isPrimaryGroupMessage;
        result.localGroupId = message.localGroupId;
        result.copyStableParams(message);
        return result;
    }

    /**
     * В ленте запрос по сообщению адресуется каналу поста, а не синтетическому диалогу.
     */
    public static TLRPC.InputPeer getInputPeerForMessageRequest(MessagesController messagesController, long dialogId, boolean isFeedSearch, MessageObject message) {
        if (isFeedSearch && message != null) {
            dialogId = message.getDialogId();
        }
        return messagesController.getInputPeer(dialogId);
    }

    /**
     * Куда скроллить обычный чат, когда играет трек, открытый из ленты.
     */
    public static int getPlaybackScrollMessageId(boolean isFeedSearch, long dialogId, MessageObject playingMessage) {
        if (playingMessage == null) {
            return 0;
        }
        if (!isFeedSearch && playingMessage.searchType == SEARCH_TYPE_FEED && playingMessage.getDialogId() == dialogId) {
            return playingMessage.getRealId();
        }
        return playingMessage.getId();
    }

    public static boolean isAllowedDoubleTapAction(int actionId) {
        return actionId == DOUBLE_TAP_ACTION_REPLY
                || actionId == DOUBLE_TAP_ACTION_SAVE
                || actionId == DOUBLE_TAP_ACTION_TRANSLATE
                || actionId == DOUBLE_TAP_ACTION_TRANSLATE_LLM;
    }

    public static boolean isAllowedFeedOption(int option) {
        switch (option) {
            case ChatActivity.OPTION_FORWARD:
            case ChatActivity.OPTION_COPY:
            case ChatActivity.OPTION_SAVE_TO_GALLERY:
            case ChatActivity.OPTION_SHARE:
            case ChatActivity.OPTION_SAVE_TO_GALLERY2:
            case ChatActivity.OPTION_REPLY:
            case ChatActivity.OPTION_SAVE_TO_DOWNLOADS_OR_MUSIC:
            case ChatActivity.OPTION_COPY_PHONE_NUMBER:
            case ChatActivity.OPTION_COPY_LINK:
            case ChatActivity.OPTION_TRANSLATE:
                return true;
            default:
                for (int allowed : EXTRA_ALLOWED_FEED_OPTIONS) {
                    if (allowed == option) {
                        return true;
                    }
                }
                return false;
        }
    }

    /**
     * Настоящий пост, а не служебная строка ленты и не реклама.
     */
    public static boolean isPostRow(MessageObject message) {
        return message != null
                && !message.isDateObject
                && message.type != MessageObject.TYPE_LOADING
                && !message.isSponsored();
    }

    public static TLRPC.Chat resolveDisplayChannel(MessageObject messageObject) {
        if (messageObject == null
                || messageObject.messageOwner == null
                || messageObject.searchType != SEARCH_TYPE_FEED
                || messageObject.preview
                || messageObject.isSponsored()
                || messageObject.type == MessageObject.TYPE_JOINED_CHANNEL
                || !messageObject.messageOwner.post
                || messageObject.messageOwner.action != null
                || messageObject.messageOwner.peer_id == null
                || messageObject.messageOwner.peer_id.channel_id == 0) {
            return null;
        }
        TLRPC.Chat channel = MessagesController.getInstance(messageObject.currentAccount)
                .getChat(messageObject.messageOwner.peer_id.channel_id);
        return ChatObject.isChannelAndNotMegaGroup(channel) ? channel : null;
    }

    public static boolean hasProfileSignature(MessageObject messageObject) {
        TLRPC.Chat channel = resolveDisplayChannel(messageObject);
        return channel != null && channel.signature_profiles;
    }

    public static boolean shouldMergePosts(
            MessageObject upper,
            TLRPC.Chat upperChannel,
            MessageObject lower,
            TLRPC.Chat lowerChannel
    ) {
        return upperChannel != null
                && lowerChannel != null
                && upperChannel.id == lowerChannel.id
                && upper.contentType == lower.contentType
                && !(upper.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup)
                && upper.isOutOwner() == lower.isOutOwner()
                && Math.abs(upper.messageOwner.date - lower.messageOwner.date) <= 5 * 60
                && lower.messageOwner.paid_message_stars <= 0
                && DialogObject.getPeerDialogId(upper.messageOwner.guestchat_via_from)
                    == DialogObject.getPeerDialogId(lower.messageOwner.guestchat_via_from);
    }

    /**
     * Совпадает ли играющее сообщение с уведомлением плеера, пришедшим по синтетическому id.
     */
    public static boolean matchesPlaybackNotification(int currentAccount, MessageObject message, int messageId) {
        if (message == null) {
            return false;
        }
        if (message.getId() == messageId) {
            return true;
        }
        FeedController feedController = FeedController.peekInstance(currentAccount);
        if (feedController == null) {
            return false;
        }
        long realDialogId = feedController.resolveRealDialogId(messageId);
        return realDialogId != 0
                && realDialogId == message.getDialogId()
                && feedController.resolveRealMessageId(realDialogId, messageId) == getFeedRealId(message);
    }

    private static int getFeedRealId(MessageObject message) {
        return message.searchType == SEARCH_TYPE_FEED ? message.getRealId() : message.getId();
    }

    private static void onPostLinkExported(TLObject response, ChatActivity chatActivity) {
        if (!(response instanceof TLRPC.TL_exportedMessageLink)) {
            return;
        }
        String link = ((TLRPC.TL_exportedMessageLink) response).link;
        if (AndroidUtilities.addToClipboard(link) && BulletinFactory.canShowBulletin(chatActivity)) {
            BulletinFactory.of(chatActivity).createCopyLinkBulletin(link.contains(PRIVATE_CHANNEL_LINK_MARKER)).show();
        }
    }

    private static TLRPC.TL_message copyMessage(TLRPC.Message message) {
        TLRPC.TL_message copy = new TLRPC.TL_message();
        copy.id = message.id;
        copy.from_id = message.from_id;
        copy.from_boosts_applied = message.from_boosts_applied;
        copy.peer_id = message.peer_id;
        copy.saved_peer_id = message.saved_peer_id;
        copy.date = message.date;
        copy.expire_date = message.expire_date;
        copy.action = message.action;
        copy.message = message.message;
        copy.media = message.media;
        copy.flags = message.flags;
        copy.flags2 = message.flags2;
        copy.mentioned = message.mentioned;
        copy.media_unread = message.media_unread;
        copy.out = message.out;
        copy.unread = message.unread;
        copy.entities = message.entities;
        copy.via_bot_name = message.via_bot_name;
        copy.reply_markup = message.reply_markup;
        copy.views = message.views;
        copy.forwards = message.forwards;
        copy.replies = message.replies;
        copy.edit_date = message.edit_date;
        copy.silent = message.silent;
        copy.post = message.post;
        copy.from_scheduled = message.from_scheduled;
        copy.legacy = message.legacy;
        copy.edit_hide = message.edit_hide;
        copy.pinned = message.pinned;
        copy.fwd_from = message.fwd_from;
        copy.via_bot_id = message.via_bot_id;
        copy.via_business_bot_id = message.via_business_bot_id;
        copy.reply_to = message.reply_to;
        copy.post_author = message.post_author;
        copy.grouped_id = message.grouped_id;
        copy.reactions = message.reactions;
        copy.restriction_reason = message.restriction_reason;
        copy.ttl_period = message.ttl_period;
        copy.quick_reply_shortcut_id = message.quick_reply_shortcut_id;
        copy.effect = message.effect;
        copy.noforwards = message.noforwards;
        copy.invert_media = message.invert_media;
        copy.offline = message.offline;
        copy.factcheck = message.factcheck;
        copy.send_state = message.send_state;
        copy.fwd_msg_id = message.fwd_msg_id;
        copy.params = message.params;
        copy.random_id = message.random_id;
        copy.local_id = message.local_id;
        copy.attachPath = message.attachPath;
        copy.dialog_id = message.dialog_id;
        copy.ttl = message.ttl;
        copy.destroyTime = message.destroyTime;
        copy.destroyTimeMillis = message.destroyTimeMillis;
        copy.layer = message.layer;
        copy.seq_in = message.seq_in;
        copy.seq_out = message.seq_out;
        copy.with_my_score = message.with_my_score;
        copy.replyMessage = message.replyMessage;
        copy.reqId = message.reqId;
        copy.realId = message.realId;
        copy.stickerVerified = message.stickerVerified;
        copy.isThreadMessage = message.isThreadMessage;
        copy.voiceTranscription = message.voiceTranscription;
        copy.voiceTranscriptionOpen = message.voiceTranscriptionOpen;
        copy.voiceTranscriptionRated = message.voiceTranscriptionRated;
        copy.voiceTranscriptionFinal = message.voiceTranscriptionFinal;
        copy.voiceTranscriptionForce = message.voiceTranscriptionForce;
        copy.voiceTranscriptionId = message.voiceTranscriptionId;
        copy.premiumEffectWasPlayed = message.premiumEffectWasPlayed;
        copy.originalLanguage = message.originalLanguage;
        copy.translatedToLanguage = message.translatedToLanguage;
        copy.translatedText = message.translatedText;
        copy.replyStory = message.replyStory;
        copy.quick_reply_shortcut = message.quick_reply_shortcut;
        return copy;
    }
}
