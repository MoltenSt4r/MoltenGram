package org.telegram.ui.Components;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeColors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Human-readable catalog, categorization, HEX helpers, and contrast-safe editor palette
 * for {@link ThemeColors} keys.
 */
public final class ThemeKeyCatalog {

    /** Contrast-safe dynamic palette snapshot for the theme editor UI itself. */
    public static final class EditorPalette {
        public final boolean dark;
        public final int background;
        public final int surfaceElevated;
        public final int surfaceStrong;
        public final int textPrimary;
        public final int textSecondary;
        public final int textMuted;
        public final int accent;
        public final int onAccent;
        public final int chipBg;
        public final int chipSelectedBg;
        public final int chipText;
        public final int chipSelectedText;
        public final int divider;
        public final int handleColor;
        public final int selector;
        public final int glow;

        private EditorPalette(boolean dark, int background, int surfaceElevated, int surfaceStrong,
                              int textPrimary, int textSecondary, int textMuted, int accent, int onAccent,
                              int chipBg, int chipSelectedBg, int chipText, int chipSelectedText,
                              int divider, int handleColor, int selector, int glow) {
            this.dark = dark;
            this.background = background;
            this.surfaceElevated = surfaceElevated;
            this.surfaceStrong = surfaceStrong;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.textMuted = textMuted;
            this.accent = accent;
            this.onAccent = onAccent;
            this.chipBg = chipBg;
            this.chipSelectedBg = chipSelectedBg;
            this.chipText = chipText;
            this.chipSelectedText = chipSelectedText;
            this.divider = divider;
            this.handleColor = handleColor;
            this.selector = selector;
            this.glow = glow;
        }

        public static EditorPalette captureCurrentTheme() {
            int rawBg = Theme.getColor(Theme.key_dialogBackground);
            if (Color.alpha(rawBg) < 180) {
                rawBg = Theme.getColor(Theme.key_windowBackgroundWhite);
            }
            boolean isDark = Color.alpha(rawBg) >= 128 && AndroidUtilities.computePerceivedBrightness(rawBg) < 0.5f;
            int bg = Color.alpha(rawBg) >= 128 ? (0xFF000000 | rawBg) : (isDark ? 0xFF1A1D24 : 0xFFFFFFFF);

            int textPrimary = isDark ? 0xFFF2F4F8 : 0xFF1A1D22;
            int textSecondary = isDark ? 0xFF9BA4B5 : 0xFF666E7C;
            int textMuted = isDark ? 0xFF727C8E : 0xFF9098A6;

            int rawAccent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (Color.alpha(rawAccent) < 180) {
                rawAccent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
            }
            float bgLum = AndroidUtilities.computePerceivedBrightness(bg);
            float accLum = AndroidUtilities.computePerceivedBrightness(rawAccent);
            int accent = (Color.alpha(rawAccent) >= 180 && Math.abs(bgLum - accLum) >= 0.14f)
                    ? (0xFF000000 | rawAccent)
                    : (isDark ? 0xFF64B5F6 : 0xFF1E88E5);
            int onAccent = AndroidUtilities.computePerceivedBrightness(accent) < 0.58f ? 0xFFFFFFFF : 0xFF111418;

            int surfaceElevated = Theme.blendOver(bg, isDark ? 0x16FFFFFF : 0x0B000000);
            int surfaceStrong = Theme.blendOver(bg, isDark ? 0x24FFFFFF : 0x14000000);
            int chipSelectedBg = Theme.blendOver(bg, Theme.multAlpha(accent, isDark ? 0.25f : 0.16f));

            return new EditorPalette(
                    isDark,
                    bg,
                    surfaceElevated,
                    surfaceStrong,
                    textPrimary,
                    textSecondary,
                    textMuted,
                    accent,
                    onAccent,
                    surfaceElevated,
                    chipSelectedBg,
                    textSecondary,
                    accent,
                    isDark ? 0x1AFFFFFF : 0x14000000,
                    isDark ? 0x3CFFFFFF : 0x28000000,
                    isDark ? 0x18FFFFFF : 0x0D000000,
                    Theme.blendOver(bg, isDark ? 0x10FFFFFF : 0x08000000)
            );
        }
    }

    public static GradientDrawable createRoundedRect(int fillColor, int radiusDp, int strokeWidthDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        d.setColor(fillColor);
        if (strokeWidthDp > 0 && Color.alpha(strokeColor) > 0) {
            d.setStroke(AndroidUtilities.dp(strokeWidthDp), strokeColor);
        }
        return d;
    }

    public enum Category {
        ALL("Все", "All"),
        ESSENTIAL("Основное", "Essentials"),
        CHAT("Чат", "Chat"),
        CHATS("Чаты", "Chats"),
        CHROME("Фоны", "UI"),
        DIALOGS("Окна", "Dialogs"),
        CONTROLS("Кнопки", "Controls"),
        MEDIA("Медиа", "Media"),
        MODIFIED("Изм.", "Edited");

        public final String labelRu;
        public final String labelEn;

        Category(String labelRu, String labelEn) {
            this.labelRu = labelRu;
            this.labelEn = labelEn;
        }

        public String getLabel(boolean ru) {
            return ru ? labelRu : labelEn;
        }
    }

    public static final class EntryInfo {
        public final Category category;
        public final String title;
        public final String rawKey;
        public final boolean essential;

        public EntryInfo(Category category, String title, String rawKey, boolean essential) {
            this.category = category;
            this.title = title;
            this.rawKey = rawKey;
            this.essential = essential;
        }
    }

    private static final class Spec {
        final Category category;
        final String titleRu;
        final String titleEn;
        final boolean essential;

        Spec(Category category, String titleRu, String titleEn, boolean essential) {
            this.category = category;
            this.titleRu = titleRu;
            this.titleEn = titleEn;
            this.essential = essential;
        }
    }

    private static final Map<String, Spec> EXACT_SPECS = new HashMap<>();
    private static final Map<String, String> TOKEN_RU = new HashMap<>();
    private static final Map<String, String> TOKEN_EN = new HashMap<>();
    private static final ConcurrentHashMap<String, EntryInfo> LOOKUP_CACHE_RU = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, EntryInfo> LOOKUP_CACHE_EN = new ConcurrentHashMap<>();

    static {
        // Essential / Chrome & Window
        putSpec("windowBackgroundWhite", Category.CHROME, "Основной фон экранов", "Main Screen Background", true);
        putSpec("windowBackgroundGray", Category.CHROME, "Вторичный фон и разделители", "Secondary Gray Background", true);
        putSpec("windowBackgroundGrayShadow", Category.CHROME, "Тень между секциями", "Section Divider Shadow", false);
        putSpec("windowBackgroundWhiteBlackText", Category.CHROME, "Основной текст", "Primary Text", true);
        putSpec("windowBackgroundWhiteGrayText", Category.CHROME, "Вторичный текст", "Secondary Text", true);
        putSpec("windowBackgroundWhiteGrayText2", Category.CHROME, "Подписи и статус (2)", "Subtitles & Status (2)", false);
        putSpec("windowBackgroundWhiteGrayText3", Category.CHROME, "Мелкие подсказки (3)", "Small Captions (3)", false);
        putSpec("windowBackgroundWhiteGrayText4", Category.CHROME, "Пояснение под секцией (4)", "Section Footer Hint (4)", false);
        putSpec("windowBackgroundWhiteGrayText8", Category.CHROME, "Справочный текст (8)", "Help Text (8)", false);
        putSpec("windowBackgroundWhiteHintText", Category.CHROME, "Подсказка в поле ввода", "Input Placeholder Hint", false);
        putSpec("windowBackgroundWhiteInputField", Category.CONTROLS, "Линия поля ввода", "Input Field Underline", false);
        putSpec("windowBackgroundWhiteInputFieldActivated", Category.CONTROLS, "Активное поле ввода", "Focused Input Underline", false);
        putSpec("windowBackgroundWhiteBlueHeader", Category.CHROME, "Заголовок секции (акцент)", "Accent Section Header", true);
        putSpec("windowBackgroundWhiteBlueText", Category.CHROME, "Акцентный текст и действия", "Accent Action Text", true);
        putSpec("windowBackgroundWhiteBlueText2", Category.CHROME, "Акцентный текст (2)", "Accent Text (2)", false);
        putSpec("windowBackgroundWhiteBlueText3", Category.CHROME, "Ссылки и акценты (3)", "Accent Links (3)", false);
        putSpec("windowBackgroundWhiteBlueText4", Category.CHROME, "Выделенные пункты (4)", "Highlighted Items (4)", false);
        putSpec("windowBackgroundWhiteBlueText5", Category.CHROME, "Акцентный текст (5)", "Accent Text (5)", false);
        putSpec("windowBackgroundWhiteBlueText6", Category.CHROME, "Акцентный текст (6)", "Accent Text (6)", false);
        putSpec("windowBackgroundWhiteBlueText7", Category.CHROME, "Акцентный текст (7)", "Accent Text (7)", false);
        putSpec("windowBackgroundWhiteBlueButton", Category.CONTROLS, "Акцентная кнопка/иконка в списке", "List Accent Action", false);
        putSpec("windowBackgroundWhiteBlueIcon", Category.CONTROLS, "Акцентная иконка настройки", "Accent Setting Icon", false);
        putSpec("windowBackgroundWhiteGrayIcon", Category.CHROME, "Иконки в списках и настройках", "Settings & List Icons", true);
        putSpec("windowBackgroundWhiteGreenText", Category.CHROME, "Зелёный текст (успех)", "Positive Green Text", false);
        putSpec("windowBackgroundWhiteGreenText2", Category.CHROME, "Зелёный текст (2)", "Positive Green Text (2)", false);
        putSpec("windowBackgroundWhiteRedText", Category.CHROME, "Красный текст (удаление)", "Destructive Red Text", false);
        putSpec("windowBackgroundWhiteRedText2", Category.CHROME, "Текст ошибки (2)", "Error Text (2)", false);
        putSpec("windowBackgroundWhiteRedText3", Category.CHROME, "Красный текст (3)", "Destructive Text (3)", false);
        putSpec("windowBackgroundWhiteRedText4", Category.CHROME, "Предупреждение (4)", "Warning Text (4)", false);
        putSpec("windowBackgroundWhiteRedText5", Category.CHROME, "Опасное действие (5)", "Destructive Action (5)", false);
        putSpec("windowBackgroundWhiteValueText", Category.CHROME, "Значение параметра (справа)", "Setting Value Text", false);
        putSpec("windowBackgroundWhiteLinkText", Category.CHROME, "Цвет ссылок", "Hyperlink Text", false);
        putSpec("windowBackgroundWhiteLinkSelection", Category.CHROME, "Подсветка при нажатии ссылки", "Link Press Highlight", false);
        putSpec("windowBackgroundUnchecked", Category.CONTROLS, "Фон неактивной плашки", "Unchecked Banner Fill", false);
        putSpec("windowBackgroundChecked", Category.CONTROLS, "Фон активной плашки (тумблер)", "Checked Banner Fill", true);
        putSpec("windowBackgroundCheckText", Category.CONTROLS, "Текст активной плашки", "Checked Banner Text", false);
        putSpec("divider", Category.CHROME, "Тонкий разделитель строк", "Row Divider Line", true);
        putSpec("listSelectorSDK21", Category.CHROME, "Эффект нажатия (ripple)", "Touch Ripple Highlight", false);
        putSpec("graySection", Category.CHROME, "Фон подзаголовка списка", "List Subheader Background", false);
        putSpec("graySectionText", Category.CHROME, "Текст подзаголовка списка", "List Subheader Text", false);
        putSpec("progressCircle", Category.CONTROLS, "Индикатор загрузки", "Loading Spinner", false);
        putSpec("emptyListPlaceholder", Category.CHROME, "Текст пустого списка", "Empty State Placeholder", false);

        // ActionBar
        putSpec("actionBarDefault", Category.CHROME, "Верхняя панель (шапка)", "Top Action Bar", true);
        putSpec("actionBarDefaultIcon", Category.CHROME, "Иконки в верхней панели", "Action Bar Icons", true);
        putSpec("actionBarDefaultTitle", Category.CHROME, "Заголовок в верхней панели", "Action Bar Title", true);
        putSpec("actionBarDefaultSubtitle", Category.CHROME, "Подзаголовок в шапке", "Action Bar Subtitle", false);
        putSpec("actionBarDefaultSelector", Category.CHROME, "Нажатие кнопок в шапке", "Action Bar Press Ripple", false);
        putSpec("actionBarDefaultSearch", Category.CHROME, "Текст поиска в шапке", "Header Search Text", false);
        putSpec("actionBarDefaultSearchPlaceholder", Category.CHROME, "Подсказка поиска в шапке", "Header Search Hint", false);
        putSpec("actionBarActionModeDefault", Category.CHROME, "Панель выделения (фон)", "Selection Mode Bar", false);
        putSpec("actionBarActionModeDefaultIcon", Category.CHROME, "Иконки режима выделения", "Selection Bar Icons", false);
        putSpec("actionBarActionModeDefaultTop", Category.CHROME, "Статус-бар режима выделения", "Selection Status Bar", false);
        putSpec("actionBarActionModeDefaultSelector", Category.CHROME, "Нажатие в режиме выделения", "Selection Bar Ripple", false);
        putSpec("actionBarDefaultSubmenuBackground", Category.DIALOGS, "Фон выпадающего меню", "Dropdown Menu Background", true);
        putSpec("actionBarDefaultSubmenuItem", Category.DIALOGS, "Текст пунктов меню", "Dropdown Menu Text", false);
        putSpec("actionBarDefaultSubmenuItemIcon", Category.DIALOGS, "Иконки пунктов меню", "Dropdown Menu Icons", false);
        putSpec("actionBarDefaultSubmenuSeparator", Category.DIALOGS, "Разделитель в меню", "Dropdown Menu Divider", false);
        putSpec("actionBarTabActiveText", Category.CHROME, "Активная вкладка (текст)", "Active Tab Text", false);
        putSpec("actionBarTabUnactiveText", Category.CHROME, "Неактивная вкладка (текст)", "Inactive Tab Text", false);
        putSpec("actionBarTabLine", Category.CHROME, "Полоска под активной вкладкой", "Active Tab Indicator", false);
        putSpec("actionBarTabSelector", Category.CHROME, "Эффект нажатия вкладки", "Tab Touch Ripple", false);

        // Chats List & Drawer
        putSpec("chats_name", Category.CHATS, "Имя в списке чатов", "Chat Name", true);
        putSpec("chats_nameArchived", Category.CHATS, "Имя чата в архиве", "Archived Chat Name", false);
        putSpec("chats_nameIcon", Category.CHATS, "Иконка группы / канала", "Group / Channel Icon", false);
        putSpec("chats_secretName", Category.CHATS, "Имя секретного чата", "Secret Chat Name", false);
        putSpec("chats_secretIcon", Category.CHATS, "Замок секретного чата", "Secret Chat Lock Icon", false);
        putSpec("chats_message", Category.CHATS, "Текст последнего сообщения", "Last Message Preview", true);
        putSpec("chats_message_threeLines", Category.CHATS, "Превью сообщения (3 строки)", "3-Line Message Preview", false);
        putSpec("chats_nameMessage", Category.CHATS, "Имя отправителя в превью", "Preview Sender Name", false);
        putSpec("chats_nameMessage_threeLines", Category.CHATS, "Отправитель в превью (3 строки)", "3-Line Preview Sender", false);
        putSpec("chats_actionMessage", Category.CHATS, "Системное действие в превью", "Preview Service Action", false);
        putSpec("chats_attachMessage", Category.CHATS, "Подпись вложения (Фото/Файл)", "Preview Attachment Label", false);
        putSpec("chats_draft", Category.CHATS, "Слово «Черновик»", "Draft Label", false);
        putSpec("chats_date", Category.CHATS, "Время / дата в списке чатов", "Chat Timestamp", false);
        putSpec("chats_unreadCounter", Category.CHATS, "Счётчик непрочитанных", "Unread Badge Fill", true);
        putSpec("chats_unreadCounterMuted", Category.CHATS, "Счётчик без звука", "Muted Unread Badge", false);
        putSpec("chats_unreadCounterText", Category.CHATS, "Цифры в счётчике", "Unread Badge Count Text", false);
        putSpec("chats_pinnedOverlay", Category.CHATS, "Фон закреплённых чатов", "Pinned Chat Highlight", false);
        putSpec("chats_pinnedIcon", Category.CHATS, "Булавка закреплённого чата", "Pinned Chat Icon", false);
        putSpec("chats_muteIcon", Category.CHATS, "Иконка выключенного звука", "Muted Bell Icon", false);
        putSpec("chats_verifiedBackground", Category.CHATS, "Значок верификации (фон)", "Verified Badge Fill", false);
        putSpec("chats_verifiedCheck", Category.CHATS, "Галочка верификации", "Verified Checkmark", false);
        putSpec("chats_sentCheck", Category.CHATS, "Одна галочка (отправлено)", "Single Sent Checkmark", false);
        putSpec("chats_sentReadCheck", Category.CHATS, "Две галочки (прочитано)", "Double Read Checkmark", false);
        putSpec("chats_onlineCircle", Category.CHATS, "Индикатор «в сети»", "Online Status Dot", false);
        putSpec("chats_menuBackground", Category.CHROME, "Фон бокового меню", "Navigation Drawer Background", true);
        putSpec("chats_menuName", Category.CHROME, "Имя в боковом меню", "Drawer Profile Name", false);
        putSpec("chats_menuPhone", Category.CHROME, "Телефон в боковом меню", "Drawer Phone Number", false);
        putSpec("chats_menuItemText", Category.CHROME, "Пункты бокового меню", "Drawer Item Text", false);
        putSpec("chats_menuItemIcon", Category.CHROME, "Иконки бокового меню", "Drawer Item Icons", false);
        putSpec("chats_actionBackground", Category.CHATS, "Плавающая кнопка (карандаш)", "Floating Action Button", true);
        putSpec("chats_actionPressedBackground", Category.CHATS, "Нажатая FAB-кнопка", "Pressed FAB Fill", false);
        putSpec("chats_actionIcon", Category.CHATS, "Иконка на FAB-кнопке", "FAB Icon Color", false);
        putSpec("chats_tabUnreadActiveBackground", Category.CHATS, "Счётчик активной папки", "Active Folder Badge", false);
        putSpec("chats_tabUnreadUnactiveBackground", Category.CHATS, "Счётчик неактивной папки", "Inactive Folder Badge", false);
        putSpec("chats_archiveBackground", Category.CHATS, "Фон значка архива", "Archive Icon Fill", false);
        putSpec("chats_archivePinBackground", Category.CHATS, "Фон закреплённого архива", "Pinned Archive Fill", false);
        putSpec("chats_archiveIcon", Category.CHATS, "Иконка архива", "Archive Icon", false);

        // Chat Room
        putSpec("chat_wallpaper", Category.CHAT, "Фон / обои чата", "Chat Wallpaper", true);
        putSpec("chat_wallpaper_gradient_to1", Category.CHAT, "Градиент обоев (цвет 2)", "Wallpaper Gradient 2", false);
        putSpec("chat_wallpaper_gradient_to2", Category.CHAT, "Градиент обоев (цвет 3)", "Wallpaper Gradient 3", false);
        putSpec("chat_wallpaper_gradient_to3", Category.CHAT, "Градиент обоев (цвет 4)", "Wallpaper Gradient 4", false);
        putSpec("chat_inBubble", Category.CHAT, "Пузырь входящего сообщения", "Incoming Message Bubble", true);
        putSpec("chat_inBubbleSelected", Category.CHAT, "Выделенное входящее (пузырь)", "Selected Incoming Bubble", false);
        putSpec("chat_inBubbleShadow", Category.CHAT, "Тень входящего пузыря", "Incoming Bubble Shadow", false);
        putSpec("chat_outBubble", Category.CHAT, "Пузырь моего сообщения", "Outgoing Message Bubble", true);
        putSpec("chat_outBubbleSelected", Category.CHAT, "Выделенное моё (пузырь)", "Selected Outgoing Bubble", false);
        putSpec("chat_outBubbleShadow", Category.CHAT, "Тень моего пузыря", "Outgoing Bubble Shadow", false);
        putSpec("chat_outBubbleGradient", Category.CHAT, "Градиент моего пузыря (верх)", "Outgoing Bubble Gradient 1", false);
        putSpec("chat_outBubbleGradient2", Category.CHAT, "Градиент моего пузыря (низ)", "Outgoing Bubble Gradient 2", false);
        putSpec("chat_outBubbleGradient3", Category.CHAT, "Градиент моего пузыря (3)", "Outgoing Bubble Gradient 3", false);
        putSpec("chat_messageTextIn", Category.CHAT, "Текст входящего сообщения", "Incoming Message Text", true);
        putSpec("chat_messageTextOut", Category.CHAT, "Текст моего сообщения", "Outgoing Message Text", true);
        putSpec("chat_messageLinkIn", Category.CHAT, "Ссылки во входящем", "Incoming Message Link", false);
        putSpec("chat_messageLinkOut", Category.CHAT, "Ссылки в моём сообщении", "Outgoing Message Link", false);
        putSpec("chat_inTimeText", Category.CHAT, "Время во входящем сообщении", "Incoming Timestamp", false);
        putSpec("chat_outTimeText", Category.CHAT, "Время в моём сообщении", "Outgoing Timestamp", false);
        putSpec("chat_outSentCheck", Category.CHAT, "Галочки в моём сообщении", "Outgoing Status Checks", false);
        putSpec("chat_inReplyLine", Category.CHAT, "Полоска ответа (входящее)", "Incoming Reply Bar", false);
        putSpec("chat_outReplyLine", Category.CHAT, "Полоска ответа (моё)", "Outgoing Reply Bar", false);
        putSpec("chat_inReplyNameText", Category.CHAT, "Имя в ответе (входящее)", "Incoming Reply Name", false);
        putSpec("chat_outReplyNameText", Category.CHAT, "Имя в ответе (моё)", "Outgoing Reply Name", false);
        putSpec("chat_inReplyMessageText", Category.CHAT, "Цитата ответа (входящее)", "Incoming Reply Text", false);
        putSpec("chat_outReplyMessageText", Category.CHAT, "Цитата ответа (моё)", "Outgoing Reply Text", false);
        putSpec("chat_messagePanelBackground", Category.CHAT, "Панель ввода сообщения", "Message Input Bar", true);
        putSpec("chat_messagePanelText", Category.CHAT, "Текст в поле ввода", "Message Input Text", true);
        putSpec("chat_messagePanelHint", Category.CHAT, "Подсказка «Сообщение»", "Input Bar Hint", false);
        putSpec("chat_messagePanelIcons", Category.CHAT, "Иконки в панели ввода", "Input Bar Icons", false);
        putSpec("chat_messagePanelSend", Category.CHAT, "Кнопка отправки", "Send Button Color", true);
        putSpec("chat_messagePanelShadow", Category.CHAT, "Тень над полем ввода", "Input Bar Shadow", false);
        putSpec("chat_messagePanelCursor", Category.CHAT, "Курсор в поле ввода", "Input Text Cursor", false);
        putSpec("chat_topPanelBackground", Category.CHAT, "Верхняя панель закрепа/аудио", "Top Pinned Panel Background", false);
        putSpec("chat_topPanelTitle", Category.CHAT, "Заголовок верхней панели", "Top Pinned Panel Title", false);
        putSpec("chat_topPanelMessage", Category.CHAT, "Текст верхней панели", "Top Pinned Panel Subtitle", false);
        putSpec("chat_topPanelLine", Category.CHAT, "Полоска верхней панели", "Top Pinned Panel Line", false);
        putSpec("chat_topPanelClose", Category.CHAT, "Крестик верхней панели", "Top Pinned Close Icon", false);
        putSpec("chat_serviceBackground", Category.CHAT, "Подложка даты и уведомлений", "Date & Service Pill Fill", false);
        putSpec("chat_serviceText", Category.CHAT, "Текст даты и уведомлений", "Date & Service Pill Text", false);
        putSpec("chat_emojiPanelBackground", Category.CHAT, "Фон панели эмодзи и стикеров", "Emoji & Sticker Panel", false);
        putSpec("chat_emojiPanelIcon", Category.CHAT, "Иконки панели эмодзи", "Emoji Panel Icons", false);
        putSpec("chat_emojiPanelIconSelected", Category.CHAT, "Активная вкладка эмодзи", "Selected Emoji Tab", false);
        putSpec("chat_emojiSearchBackground", Category.CHAT, "Поле поиска эмодзи", "Emoji Search Field", false);
        putSpec("chat_inReactionButtonBackground", Category.CHAT, "Реакция (входящее)", "Incoming Reaction Pill", false);
        putSpec("chat_outReactionButtonBackground", Category.CHAT, "Реакция (моё)", "Outgoing Reaction Pill", false);

        // Dialogs & Bottom Sheets
        putSpec("dialogBackground", Category.DIALOGS, "Фон всплывающих окон и шторок", "Bottom Sheet Background", true);
        putSpec("dialogBackgroundGray", Category.DIALOGS, "Вторичный фон в окнах", "Dialog Secondary Background", false);
        putSpec("dialogTextBlack", Category.DIALOGS, "Основной текст в окнах", "Dialog Primary Text", true);
        putSpec("dialogTextGray", Category.DIALOGS, "Вторичный текст в окнах", "Dialog Secondary Text", false);
        putSpec("dialogTextGray2", Category.DIALOGS, "Подписи в окнах (2)", "Dialog Subtitles (2)", false);
        putSpec("dialogTextGray3", Category.DIALOGS, "Подсказки в окнах (3)", "Dialog Hints (3)", false);
        putSpec("dialogTextGray4", Category.DIALOGS, "Приглушённый текст (4)", "Dialog Muted Text (4)", false);
        putSpec("dialogTextBlue", Category.DIALOGS, "Акцентная кнопка диалога", "Dialog Accent Action", true);
        putSpec("dialogTextBlue2", Category.DIALOGS, "Ссылки в диалогах", "Dialog Accent Links", false);
        putSpec("dialogTextBlue4", Category.DIALOGS, "Заголовок секции в окне", "Dialog Accent Header", false);
        putSpec("dialogTextRed", Category.DIALOGS, "Опасное действие в окне", "Dialog Destructive Action", false);
        putSpec("dialogButton", Category.DIALOGS, "Кнопка в диалоге", "Dialog Button", false);
        putSpec("dialogButtonSelector", Category.DIALOGS, "Нажатие кнопки в диалоге", "Dialog Button Ripple", false);
        putSpec("dialogIcon", Category.DIALOGS, "Иконки в всплывающих окнах", "Dialog Icons", false);
        putSpec("dialogLineProgress", Category.DIALOGS, "Полоса загрузки", "Dialog Progress Fill", false);
        putSpec("dialogLineProgressBackground", Category.DIALOGS, "Фон полосы загрузки", "Dialog Progress Track", false);
        putSpec("dialogShadowLine", Category.DIALOGS, "Разделитель в окнах", "Dialog Divider", false);
        putSpec("sheet_scrollUp", Category.DIALOGS, "Ползунок (ручка) шторки", "Bottom Sheet Handle", false);
        putSpec("undo_background", Category.DIALOGS, "Фон плашки «Отменить»", "Undo Toast Background", false);
        putSpec("undo_cancelColor", Category.DIALOGS, "Кнопка «Отменить» в тосте", "Undo Toast Action", false);
        putSpec("undo_infoColor", Category.DIALOGS, "Текст в плашке «Отменить»", "Undo Toast Text", false);

        // Controls
        putSpec("switchTrack", Category.CONTROLS, "Переключатель: дорожка (выкл)", "Switch Track (Off)", true);
        putSpec("switchTrackChecked", Category.CONTROLS, "Переключатель: дорожка (вкл)", "Switch Track (On)", true);
        putSpec("switch2Track", Category.CONTROLS, "Переключатель настроек (выкл)", "Settings Switch Track (Off)", false);
        putSpec("switch2TrackChecked", Category.CONTROLS, "Переключатель настроек (вкл)", "Settings Switch Track (On)", false);
        putSpec("checkbox", Category.CONTROLS, "Круглая галочка (фон)", "Checkbox Fill", false);
        putSpec("checkboxCheck", Category.CONTROLS, "Значок галочки", "Checkbox Tick", false);
        putSpec("dialogRoundCheckBox", Category.CONTROLS, "Выбор в окнах (фон)", "Dialog Checkbox Fill", false);
        putSpec("dialogRoundCheckBoxCheck", Category.CONTROLS, "Выбор в окнах (галочка)", "Dialog Checkbox Tick", false);
        putSpec("dialogCheckboxSquareBackground", Category.CONTROLS, "Квадратный флажок (фон)", "Square Checkbox Fill", false);
        putSpec("radioBackground", Category.CONTROLS, "Радиокнопка (выкл)", "Radio Button (Off)", false);
        putSpec("radioBackgroundChecked", Category.CONTROLS, "Радиокнопка (вкл)", "Radio Button (On)", false);
        putSpec("featuredStickers_addButton", Category.CONTROLS, "Основная акцентная кнопка", "Primary Accent Button", true);
        putSpec("featuredStickers_addButtonPressed", Category.CONTROLS, "Нажатая акцентная кнопка", "Pressed Accent Button", false);
        putSpec("featuredStickers_buttonText", Category.CONTROLS, "Текст на акцентной кнопке", "Accent Button Text", false);
        putSpec("featuredStickers_removeButtonText", Category.CONTROLS, "Кнопка «Удалить»", "Remove Button Text", false);

        // Media, Profile & Avatars
        putSpec("avatar_backgroundBlue", Category.MEDIA, "Синий аватар (заглушка)", "Blue Avatar Fallback", false);
        putSpec("avatar_text", Category.MEDIA, "Инициалы на аватаре", "Avatar Initials", false);
        putSpec("profile_title", Category.MEDIA, "Имя в шапке профиля", "Profile Name", false);
        putSpec("profile_status", Category.MEDIA, "Статус в профиле", "Profile Status", false);
        putSpec("profile_actionBackground", Category.MEDIA, "Кнопка действия в профиле", "Profile Action FAB", false);
        putSpec("profile_actionIcon", Category.MEDIA, "Иконка действия в профиле", "Profile Action Icon", false);
        putSpec("player_background", Category.MEDIA, "Фон музыкального плеера", "Music Player Background", false);
        putSpec("player_actionBar", Category.MEDIA, "Шапка плеера", "Music Player Header", false);
        putSpec("player_progress", Category.MEDIA, "Полоса проигрывателя", "Player Progress Bar", false);

        initTokenMaps();
    }

    private static void putSpec(String rawKey, Category cat, String ru, String en, boolean essential) {
        EXACT_SPECS.put(rawKey, new Spec(cat, ru, en, essential));
    }

    private static void initTokenMaps() {
        putTok("background", "фон", "background");
        putTok("bg", "фон", "background");
        putTok("text", "текст", "text");
        putTok("title", "заголовок", "title");
        putTok("subtitle", "подзаголовок", "subtitle");
        putTok("name", "имя", "name");
        putTok("icon", "иконка", "icon");
        putTok("icons", "иконки", "icons");
        putTok("button", "кнопка", "button");
        putTok("btn", "кнопка", "button");
        putTok("pressed", "при нажатии", "pressed");
        putTok("selector", "эффект нажатия", "ripple");
        putTok("ripple", "эффект нажатия", "ripple");
        putTok("shadow", "тень", "shadow");
        putTok("line", "линия", "line");
        putTok("divider", "разделитель", "divider");
        putTok("separator", "разделитель", "separator");
        putTok("border", "рамка", "border");
        putTok("stroke", "обводка", "stroke");
        putTok("fill", "заливка", "fill");
        putTok("track", "дорожка", "track");
        putTok("thumb", "ползунок", "thumb");
        putTok("circle", "круг", "circle");
        putTok("dot", "точка", "dot");
        putTok("badge", "значок", "badge");
        putTok("counter", "счётчик", "counter");
        putTok("unread", "непрочитано", "unread");
        putTok("muted", "без звука", "muted");
        putTok("pinned", "закреп", "pinned");
        putTok("pin", "закреп", "pin");
        putTok("check", "галочка", "check");
        putTok("checked", "выбрано", "checked");
        putTok("unchecked", "не выбрано", "unchecked");
        putTok("selected", "выделено", "selected");
        putTok("active", "активный", "active");
        putTok("unactive", "неактивный", "inactive");
        putTok("inactive", "неактивный", "inactive");
        putTok("disabled", "отключено", "disabled");
        putTok("hint", "подсказка", "hint");
        putTok("placeholder", "заглушка", "placeholder");
        putTok("link", "ссылка", "link");
        putTok("links", "ссылки", "links");
        putTok("url", "ссылка", "URL");
        putTok("mention", "упоминание", "mention");
        putTok("reply", "ответ", "reply");
        putTok("forward", "пересылка", "forward");
        putTok("quote", "цитата", "quote");
        putTok("time", "время", "time");
        putTok("date", "дата", "date");
        putTok("sent", "отправлено", "sent");
        putTok("read", "прочитано", "read");
        putTok("failed", "ошибка", "failed");
        putTok("error", "ошибка", "error");
        putTok("warning", "предупреждение", "warning");
        putTok("bubble", "пузырь", "bubble");
        putTok("gradient", "градиент", "gradient");
        putTok("overlay", "наложение", "overlay");
        putTok("in", "входящее", "incoming");
        putTok("out", "исходящее", "outgoing");
        putTok("message", "сообщение", "message");
        putTok("messages", "сообщения", "messages");
        putTok("panel", "панель", "panel");
        putTok("header", "заголовок", "header");
        putTok("footer", "подвал", "footer");
        putTok("top", "верх", "top");
        putTok("bottom", "низ", "bottom");
        putTok("bar", "панель", "bar");
        putTok("tab", "вкладка", "tab");
        putTok("tabs", "вкладки", "tabs");
        putTok("menu", "меню", "menu");
        putTok("submenu", "подменю", "submenu");
        putTok("item", "пункт", "item");
        putTok("search", "поиск", "search");
        putTok("emoji", "эмодзи", "emoji");
        putTok("sticker", "стикер", "sticker");
        putTok("stickers", "стикеры", "stickers");
        putTok("gif", "GIF", "GIF");
        putTok("voice", "голос", "voice");
        putTok("video", "видео", "video");
        putTok("photo", "фото", "photo");
        putTok("media", "медиа", "media");
        putTok("audio", "аудио", "audio");
        putTok("music", "музыка", "music");
        putTok("player", "плеер", "player");
        putTok("seekbar", "полоса перемотки", "seekbar");
        putTok("progress", "прогресс", "progress");
        putTok("loader", "загрузка", "loader");
        putTok("wallpaper", "обои", "wallpaper");
        putTok("service", "сервисный", "service");
        putTok("admin", "админ", "admin");
        putTok("reaction", "реакция", "reaction");
        putTok("reactions", "реакции", "reactions");
        putTok("poll", "опрос", "poll");
        putTok("secret", "секретный", "secret");
        putTok("archive", "архив", "archive");
        putTok("online", "в сети", "online");
        putTok("status", "статус", "status");
        putTok("phone", "телефон", "phone");
        putTok("avatar", "аватар", "avatar");
        putTok("profile", "профиль", "profile");
        putTok("action", "действие", "action");
        putTok("close", "закрыть", "close");
        putTok("cancel", "отмена", "cancel");
        putTok("add", "добавить", "add");
        putTok("remove", "удалить", "remove");
        putTok("delete", "удалить", "delete");
        putTok("send", "отправка", "send");
        putTok("record", "запись", "record");
        putTok("attach", "вложение", "attachment");
        putTok("cursor", "курсор", "cursor");
        putTok("scroll", "прокрутка", "scroll");
        putTok("input", "поле ввода", "input");
        putTok("field", "поле", "field");
        putTok("verified", "верификация", "verified");
        putTok("scam", "мошенник", "scam");
        putTok("fake", "фейк", "fake");
        putTok("premium", "премиум", "premium");
        putTok("star", "звезда", "star");
        putTok("stars", "звёзды", "stars");
        putTok("stories", "истории", "stories");
        putTok("story", "история", "story");
        putTok("glass", "стекло", "glass");
        putTok("blur", "размытие", "blur");
        putTok("blue", "акцент", "accent");
        putTok("white", "светлый", "light");
        putTok("black", "основной", "primary");
        putTok("gray", "серый", "gray");
        putTok("grey", "серый", "gray");
        putTok("red", "красный", "red");
        putTok("green", "зелёный", "green");
        putTok("orange", "оранжевый", "orange");
        putTok("yellow", "жёлтый", "yellow");
        putTok("violet", "фиолетовый", "violet");
        putTok("purple", "пурпурный", "purple");
        putTok("pink", "розовый", "pink");
        putTok("cyan", "голубой", "cyan");
        putTok("round", "круглый", "round");
        putTok("square", "квадратный", "square");
        putTok("checkbox", "флажок", "checkbox");
        putTok("radio", "переключатель", "radio");
        putTok("switch", "тумблер", "switch");
        putTok("dialog", "окно", "dialog");
        putTok("sheet", "шторка", "sheet");
        putTok("chat", "чат", "chat");
        putTok("chats", "список чатов", "chat list");
        putTok("call", "звонок", "call");
        putTok("calls", "звонки", "calls");
        putTok("voip", "звонок", "call");
        putTok("bot", "бот", "bot");
        putTok("keyboard", "клавиатура", "keyboard");
        putTok("fast", "быстрая", "fast");
        putTok("empty", "пусто", "empty");
        putTok("default", "основной", "default");
        putTok("dark", "тёмный", "dark");
        putTok("light", "светлый", "light");
        putTok("code", "код", "code");
        putTok("block", "блок", "block");
        putTok("spoiler", "спойлер", "spoiler");
    }

    private static void putTok(String key, String ru, String en) {
        TOKEN_RU.put(key, ru);
        TOKEN_EN.put(key, en);
    }

    private ThemeKeyCatalog() {
    }

    public static boolean isRussianUi(Context context) {
        try {
            Locale appLocale = LocaleController.getInstance().getCurrentLocale();
            String lang = appLocale != null ? appLocale.getLanguage() : null;
            if (TextUtils.isEmpty(lang) && context != null) {
                Configuration cfg = context.getResources().getConfiguration();
                if (cfg != null && cfg.locale != null) {
                    lang = cfg.locale.getLanguage();
                }
            }
            if (TextUtils.isEmpty(lang)) {
                lang = Locale.getDefault().getLanguage();
            }
            if (lang == null) {
                return false;
            }
            String lower = lang.toLowerCase(Locale.US);
            return lower.startsWith("ru") || lower.startsWith("uk") || lower.startsWith("be");
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static EntryInfo lookup(int key, boolean ru) {
        String rawKey = ThemeColors.getStringName(key);
        if (rawKey == null) {
            rawKey = "";
        }
        return lookup(rawKey, ru);
    }

    public static EntryInfo lookup(String rawKey, boolean ru) {
        if (rawKey == null) {
            rawKey = "";
        }
        ConcurrentHashMap<String, EntryInfo> cache = ru ? LOOKUP_CACHE_RU : LOOKUP_CACHE_EN;
        EntryInfo cached = cache.get(rawKey);
        if (cached != null) {
            return cached;
        }
        Spec spec = EXACT_SPECS.get(rawKey);
        EntryInfo computed;
        if (spec != null) {
            computed = new EntryInfo(spec.category, ru ? spec.titleRu : spec.titleEn, rawKey, spec.essential);
        } else {
            Category cat = inferCategory(rawKey);
            String human = humanizeRawKey(rawKey, cat, ru);
            computed = new EntryInfo(cat, human, rawKey, false);
        }
        EntryInfo existing = cache.putIfAbsent(rawKey, computed);
        return existing != null ? existing : computed;
    }

    public static Category inferCategory(String rawKey) {
        if (TextUtils.isEmpty(rawKey)) {
            return Category.CHROME;
        }
        if (rawKey.startsWith("actionBarDefaultSubmenu")) {
            return Category.DIALOGS;
        }
        if (rawKey.startsWith("chat_")) {
            return Category.CHAT;
        }
        if (rawKey.startsWith("chats_menu")) {
            return Category.CHROME;
        }
        if (rawKey.startsWith("chats_")) {
            return Category.CHATS;
        }
        if (rawKey.startsWith("dialogRoundCheckBox") || rawKey.startsWith("dialogCheckbox")
                || rawKey.startsWith("switch") || rawKey.startsWith("checkbox")
                || rawKey.startsWith("radio") || rawKey.startsWith("featuredStickers_")
                || rawKey.startsWith("progress") || rawKey.startsWith("slider")) {
            return Category.CONTROLS;
        }
        if (rawKey.startsWith("dialog") || rawKey.startsWith("undo_")
                || rawKey.startsWith("sheet_") || rawKey.startsWith("contextProgress")
                || rawKey.startsWith("inappPlayer") || rawKey.startsWith("botKeyboard")
                || rawKey.startsWith("picker_") || rawKey.startsWith("login_")) {
            return Category.DIALOGS;
        }
        if (rawKey.startsWith("player_") || rawKey.startsWith("voip")
                || rawKey.startsWith("calls_") || rawKey.startsWith("avatar_")
                || rawKey.startsWith("profile_") || rawKey.startsWith("stories_")
                || rawKey.startsWith("premium") || rawKey.startsWith("glass_")
                || rawKey.startsWith("stars") || rawKey.startsWith("gift")
                || rawKey.startsWith("paint_")) {
            return Category.MEDIA;
        }
        return Category.CHROME;
    }

    private static String humanizeRawKey(String rawKey, Category cat, boolean ru) {
        if (TextUtils.isEmpty(rawKey)) {
            return ru ? "Неизвестный цвет" : "Unknown Color";
        }
        String working = rawKey;
        String prefixLabel = null;
        if (working.startsWith("windowBackgroundWhite")) {
            working = working.substring("windowBackgroundWhite".length());
            prefixLabel = ru ? "Экран" : "Screen";
        } else if (working.startsWith("windowBackground")) {
            working = working.substring("windowBackground".length());
            prefixLabel = ru ? "Фон экрана" : "Window";
        } else if (working.startsWith("actionBarDefaultSubmenu")) {
            working = working.substring("actionBarDefaultSubmenu".length());
            prefixLabel = ru ? "Меню" : "Menu";
        } else if (working.startsWith("actionBarActionModeDefault")) {
            working = working.substring("actionBarActionModeDefault".length());
            prefixLabel = ru ? "Выделение" : "Selection Bar";
        } else if (working.startsWith("actionBarDefault")) {
            working = working.substring("actionBarDefault".length());
            prefixLabel = ru ? "Шапка" : "Action Bar";
        } else if (working.startsWith("actionBar")) {
            working = working.substring("actionBar".length());
            prefixLabel = ru ? "Шапка" : "Action Bar";
        } else if (working.startsWith("featuredStickers_")) {
            working = working.substring("featuredStickers_".length());
            prefixLabel = ru ? "Кнопки стикеров" : "Featured";
        } else if (working.startsWith("chats_")) {
            working = working.substring("chats_".length());
            prefixLabel = ru ? "Список чатов" : "Chat List";
        } else if (working.startsWith("chat_")) {
            working = working.substring("chat_".length());
            prefixLabel = ru ? "Чат" : "Chat";
        } else if (working.startsWith("dialog")) {
            working = working.substring("dialog".length());
            prefixLabel = ru ? "Окно" : "Dialog";
        } else if (working.startsWith("profile_")) {
            working = working.substring("profile_".length());
            prefixLabel = ru ? "Профиль" : "Profile";
        } else if (working.startsWith("avatar_")) {
            working = working.substring("avatar_".length());
            prefixLabel = ru ? "Аватар" : "Avatar";
        } else if (working.startsWith("player_")) {
            working = working.substring("player_".length());
            prefixLabel = ru ? "Плеер" : "Player";
        } else if (working.startsWith("voip_")) {
            working = working.substring("voip_".length());
            prefixLabel = ru ? "Звонок" : "Call";
        } else if (working.startsWith("calls_")) {
            working = working.substring("calls_".length());
            prefixLabel = ru ? "Звонки" : "Calls";
        } else if (working.startsWith("stories_")) {
            working = working.substring("stories_".length());
            prefixLabel = ru ? "Истории" : "Stories";
        } else if (working.startsWith("glass_")) {
            working = working.substring("glass_".length());
            prefixLabel = ru ? "Стекло" : "Liquid Glass";
        }

        List<String> tokens = splitTokens(working);
        StringBuilder words = new StringBuilder();
        Map<String, String> dict = ru ? TOKEN_RU : TOKEN_EN;
        for (int i = 0; i < tokens.size(); i++) {
            String tok = tokens.get(i);
            if (TextUtils.isEmpty(tok)) {
                continue;
            }
            String mapped = dict.get(tok);
            if (mapped == null && tok.length() > 1) {
                char last = tok.charAt(tok.length() - 1);
                if (Character.isDigit(last)) {
                    String stem = tok.substring(0, tok.length() - 1);
                    String stemMapped = dict.get(stem);
                    if (stemMapped != null) {
                        mapped = stemMapped + " " + last;
                    }
                }
            }
            String piece = mapped != null ? mapped : tok;
            if (words.length() > 0) {
                words.append(' ');
            }
            words.append(piece);
        }

        String body = words.toString().trim();
        if (TextUtils.isEmpty(body)) {
            if (!TextUtils.isEmpty(prefixLabel)) {
                return prefixLabel;
            }
            return rawKey;
        }
        String capBody = capitalize(body);
        if (!TextUtils.isEmpty(prefixLabel)) {
            return prefixLabel + ": " + body;
        }
        return capBody;
    }

    private static List<String> splitTokens(String input) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(input)) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '_' || c == '-' || c == ' ') {
                if (cur.length() > 0) {
                    out.add(cur.toString().toLowerCase(Locale.US));
                    cur.setLength(0);
                }
                continue;
            }
            if (Character.isUpperCase(c) && cur.length() > 0) {
                char prev = cur.charAt(cur.length() - 1);
                boolean nextLower = (i + 1 < input.length()) && Character.isLowerCase(input.charAt(i + 1));
                if (Character.isLowerCase(prev) || Character.isDigit(prev) || nextLower) {
                    out.add(cur.toString().toLowerCase(Locale.US));
                    cur.setLength(0);
                }
            } else if (Character.isDigit(c) && cur.length() > 0 && !Character.isDigit(cur.charAt(cur.length() - 1))) {
                // Keep digits attached to short text tokens like Text2 -> "текст 2" handled above
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            out.add(cur.toString().toLowerCase(Locale.US));
        }
        return out;
    }

    private static String capitalize(String s) {
        if (TextUtils.isEmpty(s)) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static String formatHex(int color, boolean includeAlphaWhenOpaque) {
        int a = Color.alpha(color);
        if (a == 255 && !includeAlphaWhenOpaque) {
            return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
        }
        return String.format(Locale.US, "#%08X", color);
    }

    public static Integer parseHex(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.startsWith("0x") || s.startsWith("0X")) {
            s = s.substring(2);
        }
        if (s.length() == 3) {
            char r = s.charAt(0);
            char g = s.charAt(1);
            char b = s.charAt(2);
            s = "" + r + r + g + g + b + b;
        }
        if (s.length() == 6) {
            try {
                int rgb = Integer.parseInt(s, 16);
                return 0xFF000000 | rgb;
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        if (s.length() == 8) {
            try {
                long argb = Long.parseLong(s, 16);
                return (int) argb;
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    public static boolean isModifiedFromDefault(int key, int currentColor) {
        if (key < 0) {
            return false;
        }
        int def = Theme.getDefaultColor(key);
        return def != currentColor;
    }

    public static boolean isOpaqueRequiredKey(int key) {
        return key == Theme.key_chat_wallpaper
                || key == Theme.key_chat_wallpaper_gradient_to1
                || key == Theme.key_chat_wallpaper_gradient_to2
                || key == Theme.key_chat_wallpaper_gradient_to3
                || key == Theme.key_windowBackgroundWhite
                || key == Theme.key_windowBackgroundGray;
    }

    public static List<Integer> extractDistinctPalette(List<Integer> rawColors, int maxCount) {
        ArrayList<Integer> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        if (rawColors == null) {
            return result;
        }
        for (int i = 0; i < rawColors.size() && result.size() < maxCount; i++) {
            int c = rawColors.get(i);
            if (Color.alpha(c) < 32) {
                continue;
            }
            int opaque = 0xFF000000 | c;
            if (seen.add(opaque)) {
                result.add(opaque);
            }
        }
        return result;
    }
}
