package com.exteragram.messenger.utils.chats;

import android.text.SpannableStringBuilder;
import android.text.Spanned;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;

import java.util.concurrent.atomic.AtomicReferenceArray;

import tw.nekomimi.nekogram.helpers.MessageHelper;

/**
 * Шим {@code com.exteragram.messenger.utils.chats.ChatUtils}.
 *
 * Нужен настоящим плагинам: из семи новых каналов его зовут 13 штук, и без
 * него они не грузятся вовсе. Используют ровно три метода — путь к файлу
 * сообщения (46 вызовов), текст сообщения (3) и разбор ссылки на канал (1),
 * поэтому шим повторяет их, а не весь класс exteraGram.
 *
 * За каждым методом стоит наш собственный код (NagramX MessageHelper,
 * MessagesController), а не перенос: смысл шима в имени, которое плагины
 * ожидают увидеть.
 */
public final class ChatUtils {

    private static final AtomicReferenceArray<ChatUtils> instances =
            new AtomicReferenceArray<>(UserConfig.MAX_ACCOUNT_COUNT);
    private static SpannableStringBuilder channelIcon;

    private final int currentAccount;

    private ChatUtils(int account) {
        this.currentAccount = account;
    }

    public static ChatUtils getInstance() {
        return getInstance(UserConfig.selectedAccount);
    }

    public static ChatUtils getInstance(int account) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            account = UserConfig.selectedAccount;
        }
        ChatUtils local = instances.get(account);
        if (local == null) {
            local = new ChatUtils(account);
            if (!instances.compareAndSet(account, null, local)) {
                local = instances.get(account);
            }
        }
        return local;
    }

    public static CharSequence getChannelIcon() {
        if (channelIcon == null) {
            channelIcon = new SpannableStringBuilder("\u200D");
            channelIcon.setSpan(new ColoredImageSpan(Theme.chat_channelIconDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return channelIcon;
    }

    public static String getDCName(int dc) {
        switch (dc) {
            case 1:
            case 3:
                return "Miami FL, USA";
            case 2:
            case 4:
                return "Amsterdam, NL";
            case 5:
                return "Singapore, SG";
            default:
                return null;
        }
    }

    public static long extractOwnerId(long id) {
        long owner = id >> 32;
        if (((id >> 16) & 255) == 63) {
            owner |= 2147483648L;
        }
        return ((id >> 24) & 255) != 0 ? owner + 4294967296L : owner;
    }

    /** Путь к скачанному файлу сообщения или null, если файла нет. */
    public String getPathToMessage(MessageObject messageObject) {
        if (messageObject == null) {
            return null;
        }
        return MessageHelper.getPathToMessage(messageObject);
    }

    /** Текст сообщения без разметки; пустая строка вместо null — как ждут плагины. */
    public CharSequence getMessageText(MessageObject messageObject) {
        return getMessageText(messageObject, null);
    }

    public CharSequence getMessageText(MessageObject messageObject,
                                       MessageObject.GroupedMessages group) {
        if (messageObject == null) {
            return "";
        }
        String text = MessageHelper.getMessagePlainText(messageObject, group);
        return text == null ? "" : text;
    }

    /**
     * Канал по @username из кэша.
     *
     * Сетевого запроса здесь нет намеренно: плагины зовут это из отрисовки, а
     * поход на сервер оттуда означал бы подвисания. Нет в кэше — null.
     */
    public TLRPC.Chat resolveChannel(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        Object cached = MessagesController.getInstance(currentAccount)
                .getUserOrChat(stripAt(username));
        return cached instanceof TLRPC.Chat ? (TLRPC.Chat) cached : null;
    }

    public void resolveChannel(String username, Utilities.Callback<TLRPC.Chat> callback) {
        if (callback == null) {
            return;
        }
        TLRPC.Chat cached = resolveChannel(username);
        if (cached != null) {
            callback.run(cached);
            return;
        }
        if (username == null || username.isEmpty()) {
            callback.run(null);
            return;
        }
        MessagesController controller = MessagesController.getInstance(currentAccount);
        controller.getUserNameResolver().resolve(stripAt(username), peerId -> {
            if (peerId == null || peerId >= 0) {
                callback.run(null);
                return;
            }
            callback.run(controller.getChat(Long.valueOf(-peerId)));
        });
    }

    private static String stripAt(String username) {
        return username.startsWith("@") ? username.substring(1) : username;
    }
}
