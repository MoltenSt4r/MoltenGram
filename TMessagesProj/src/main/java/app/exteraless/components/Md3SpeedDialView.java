package app.exteraless.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.ChannelCreateActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import app.exteraless.appearance.AppearanceConfig;

@SuppressLint("ViewConstructor")
public class Md3SpeedDialView extends FrameLayout {

    public static class SpeedDialAction {
        public final String key;
        public final int iconRes;
        public final int titleRes;
        public final String customTitle;
        public final String actionType; // null for built-in, "deeplink", "chat", "plugin", "setting"
        public final String target;
        public final String iconName;

        public SpeedDialAction(String key, int iconRes, int titleRes) {
            this(key, iconRes, titleRes, null, null, null, null);
        }

        public SpeedDialAction(String key, int iconRes, int titleRes, String customTitle, String actionType, String target, String iconName) {
            this.key = key;
            this.iconRes = iconRes;
            this.titleRes = titleRes;
            this.customTitle = customTitle;
            this.actionType = actionType;
            this.target = target;
            this.iconName = iconName;
        }

        public String getTitle() {
            if (!TextUtils.isEmpty(customTitle)) {
                return customTitle;
            }
            return titleRes != 0 ? LocaleController.getString(titleRes) : key;
        }

        public boolean isCustom() {
            return !TextUtils.isEmpty(actionType);
        }
    }

    public static final SpeedDialAction ACTION_CHAT = new SpeedDialAction("chat", R.drawable.filled_fab_compose_32, R.string.NewMessageTitle);
    public static final SpeedDialAction ACTION_CHANNEL = new SpeedDialAction("channel", R.drawable.msg_channel, R.string.NewChannel);
    public static final SpeedDialAction ACTION_GROUP = new SpeedDialAction("group", R.drawable.msg_groups, R.string.NewGroup);
    public static final SpeedDialAction ACTION_SECRET = new SpeedDialAction("secret", R.drawable.msg_secret, R.string.NewSecretChat);
    public static final SpeedDialAction ACTION_STORY = new SpeedDialAction("story", R.drawable.camera, R.string.StoryPrivacyButtonPost);
    public static final SpeedDialAction ACTION_QR = new SpeedDialAction("qr", R.drawable.msg_qrcode, R.string.AuthAnotherClientScan);
    public static final SpeedDialAction ACTION_SAVED = new SpeedDialAction("saved", R.drawable.msg_saved, R.string.SavedMessages);

    public static final SpeedDialAction[] ALL_ACTIONS = new SpeedDialAction[]{
        ACTION_CHAT,
        ACTION_CHANNEL,
        ACTION_GROUP,
        ACTION_SECRET,
        ACTION_STORY,
        ACTION_QR,
        ACTION_SAVED
    };

    public static class IconChoice {
        public final String key;
        public final int resId;
        public final String titleRu;
        public final String keywords;

        public IconChoice(String key, int resId, String titleRu, String keywords) {
            this.key = key;
            this.resId = resId;
            this.titleRu = titleRu;
            this.keywords = keywords;
        }
    }

    public static final IconChoice[] ALL_ICON_CHOICES = new IconChoice[]{
        new IconChoice("message", R.drawable.msg_message, "Сообщение", "чат диалог sms message"),
        new IconChoice("channel", R.drawable.msg_channel, "Канал", "новости broadcast channel"),
        new IconChoice("group", R.drawable.msg_groups, "Группа", "чат сообщество group users"),
        new IconChoice("secret", R.drawable.msg_secret, "Секретный чат", "замок приватный lock secret"),
        new IconChoice("story", R.drawable.camera, "История", "сторис публикация camera story"),
        new IconChoice("qrcode", R.drawable.msg_qrcode, "QR-код", "скан qr code scan barcode"),
        new IconChoice("saved", R.drawable.msg_saved, "Избранное", "сохраненное закладка bookmark saved"),
        new IconChoice("settings", R.drawable.msg_settings, "Настройки", "параметры шестеренка gear options settings"),
        new IconChoice("customize", R.drawable.msg_customize, "Оформление", "вид тема кастомизация ui customize"),
        new IconChoice("theme", R.drawable.msg_theme, "Тема", "палитра цвет кисть brush theme"),
        new IconChoice("palette", R.drawable.msg_palette, "Палитра", "краски цвет design palette"),
        new IconChoice("link", R.drawable.msg_link, "Ссылка", "url сайт web link internet"),
        new IconChoice("bot", R.drawable.msg_bot, "Бот", "робот автоматизация robot bot"),
        new IconChoice("search", R.drawable.msg_search, "Поиск", "найти лупа find search query"),
        new IconChoice("download", R.drawable.msg_download, "Загрузки", "скачать файлы arrow download"),
        new IconChoice("camera", R.drawable.msg_camera, "Камера", "фото снимок photo picture camera"),
        new IconChoice("share", R.drawable.msg_share, "Поделиться", "переслать стрелка share forward"),
        new IconChoice("forward", R.drawable.msg_forward, "Переслать", "пересылка стрелка forward send"),
        new IconChoice("calls", R.drawable.msg_calls, "Звонки", "телефон трубка call phone ring"),
        new IconChoice("media", R.drawable.msg_media, "Медиа", "галерея фото видео gallery media"),
        new IconChoice("location", R.drawable.msg_location, "Геолокация", "карта местоположение pin map gps location"),
        new IconChoice("pin", R.drawable.msg_pin, "Закрепить", "булавка pin unpin"),
        new IconChoice("archive", R.drawable.msg_archive, "Архив", "скрыть коробка box archive"),
        new IconChoice("send", R.drawable.msg_send, "Отправить", "самолетик письмо send plane"),
        new IconChoice("contacts", R.drawable.msg_contacts, "Контакты", "люди друзья user person contacts"),
        new IconChoice("profile", R.drawable.msg_openprofile, "Профиль", "аккаунт юзер profile user account"),
        new IconChoice("help", R.drawable.msg_help, "Помощь", "вопрос справка faq question help"),
        new IconChoice("info", R.drawable.msg_info, "Информация", "описание о программе details info"),
        new IconChoice("stats", R.drawable.msg_stats, "Статистика", "график аналитика chart stats"),
        new IconChoice("copy", R.drawable.msg_copy, "Копировать", "буфер обмена copy clipboard"),
        new IconChoice("edit", R.drawable.msg_edit, "Редактировать", "карандаш править pen edit pencil"),
        new IconChoice("language", R.drawable.msg_language, "Язык", "глобус перевод language globe"),
        new IconChoice("notifications", R.drawable.msg_notifications, "Уведомления", "колокольчик звонок bell alert notifications"),
        new IconChoice("folders", R.drawable.msg_folders, "Папки", "каталоги вкладки folder directory"),
        new IconChoice("favorite", R.drawable.msg_fave, "Сердечко", "избранное любовь heart love fave"),
        new IconChoice("emoji", R.drawable.msg_emoji_smiles, "Эмодзи", "смайл стикер smile face emoji"),
        new IconChoice("gift", R.drawable.msg_gift_premium, "Подарок", "премиум сюрприз present gift premium"),
        new IconChoice("music", R.drawable.bot_music, "Музыка", "трек песня плеер audio music song"),
        new IconChoice("star", R.drawable.baseline_star_24, "Звезда", "рейтинг избранное star favorite"),
        new IconChoice("plugins", R.drawable.msg_plugins, "Плагины", "модули дополнения extensions plugins"),
        new IconChoice("mute", R.drawable.msg_mute, "Без звука", "тишина заглушить silent mute"),
        new IconChoice("block", R.drawable.msg_block, "Заблокировать", "бан черный список ban block"),
        new IconChoice("delete", R.drawable.msg_delete, "Удалить", "корзина стереть trash remove delete"),
        new IconChoice("leave", R.drawable.msg_leave, "Выйти", "покинуть exit logout leave"),
        new IconChoice("log", R.drawable.msg_log, "Журнал событий", "логи аудит audit history log"),
        new IconChoice("reactions", R.drawable.msg_reactions2, "Реакции", "лайки реакции heart react reactions"),
        new IconChoice("calendar", R.drawable.msg_calendar2, "Календарь", "дата число calendar schedule time"),
        new IconChoice("translate", R.drawable.msg_translate, "Переводчик", "язык слова перевод translator translate"),
        new IconChoice("file", R.drawable.msg_view_file, "Файл", "документ file document paper"),
        new IconChoice("discussion", R.drawable.msg_discussion, "Обсуждение", "чат комментарии forum discuss comments"),
        new IconChoice("voice", R.drawable.msg_voice_phone, "Голос", "звонок микрофон voice phone microphone"),
        new IconChoice("video", R.drawable.msg_video, "Видео", "камера ролик clip video movie"),
        new IconChoice("lightning", R.drawable.msg_instant, "Молния", "быстрый instant speed boost flash lightning"),
        new IconChoice("mention", R.drawable.msg_mention, "Упоминание", "собачка тег mention tag at"),
        new IconChoice("report", R.drawable.msg_report, "Пожаловаться", "жалоба warning report flag alert"),
        new IconChoice("speed", R.drawable.msg_speed, "Скорость", "спидометр быстро meter fast speed"),
        new IconChoice("retry", R.drawable.msg_retry, "Повторить", "обновить рестарт reload refresh retry"),
        new IconChoice("views", R.drawable.msg_views, "Просмотры", "глаз счетчик eye views watch"),
        new IconChoice("home", R.drawable.msg_home, "Главная", "дом главная home start house"),
        new IconChoice("clear", R.drawable.msg_clear, "Очистить", "очистка сброс brush clean clear"),
        new IconChoice("check", R.drawable.msg_check, "Галочка", "готово выполнено done ok check")
    };

    public static final String[] ICON_CHOICES;
    static {
        ICON_CHOICES = new String[ALL_ICON_CHOICES.length];
        for (int i = 0; i < ALL_ICON_CHOICES.length; i++) {
            ICON_CHOICES[i] = ALL_ICON_CHOICES[i].key;
        }
    }

    public static int getIconResByName(String iconName) {
        if (iconName == null) return R.drawable.msg_instant;
        for (IconChoice c : ALL_ICON_CHOICES) {
            if (c.key.equalsIgnoreCase(iconName)) {
                return c.resId;
            }
        }
        if ("chat".equalsIgnoreCase(iconName)) return R.drawable.msg_message;
        if ("code".equalsIgnoreCase(iconName) || "script".equalsIgnoreCase(iconName)) return R.drawable.msg_permissions;
        if ("brush".equalsIgnoreCase(iconName)) return R.drawable.msg_theme;
        if ("lock".equalsIgnoreCase(iconName)) return R.drawable.msg_secret;
        if ("call".equalsIgnoreCase(iconName) || "phone".equalsIgnoreCase(iconName)) return R.drawable.msg_calls;
        if ("gallery".equalsIgnoreCase(iconName)) return R.drawable.msg_media;
        if ("map".equalsIgnoreCase(iconName)) return R.drawable.msg_location;
        if ("user".equalsIgnoreCase(iconName)) return R.drawable.msg_contacts;
        if ("chart".equalsIgnoreCase(iconName)) return R.drawable.msg_stats;
        if ("pen".equalsIgnoreCase(iconName)) return R.drawable.msg_edit;
        if ("globe".equalsIgnoreCase(iconName)) return R.drawable.msg_language;
        if ("bell".equalsIgnoreCase(iconName)) return R.drawable.msg_notifications;
        if ("folder".equalsIgnoreCase(iconName)) return R.drawable.msg_folders;
        if ("heart".equalsIgnoreCase(iconName) || "fave".equalsIgnoreCase(iconName)) return R.drawable.msg_fave;
        if ("smile".equalsIgnoreCase(iconName)) return R.drawable.msg_emoji_smiles;
        return R.drawable.msg_instant;
    }

    public static List<SpeedDialAction> getCustomActions() {
        List<SpeedDialAction> list = new ArrayList<>();
        String json = AppearanceConfig.md3FabCustomItems();
        if (!TextUtils.isEmpty(json)) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String id = obj.optString("id");
                    String title = obj.optString("title");
                    String type = obj.optString("type");
                    String target = obj.optString("target");
                    String icon = obj.optString("icon");
                    int iconRes = getIconResByName(icon);
                    list.add(new SpeedDialAction(id, iconRes, 0, title, type, target, icon));
                }
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    public static void saveCustomActions(List<SpeedDialAction> customList) {
        try {
            JSONArray array = new JSONArray();
            for (SpeedDialAction a : customList) {
                if (a.isCustom()) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", a.key);
                    obj.put("title", a.customTitle);
                    obj.put("type", a.actionType);
                    obj.put("target", a.target);
                    obj.put("icon", a.iconName);
                    array.put(obj);
                }
            }
            AppearanceConfig.md3FabCustomItems.setConfigString(array.toString());
        } catch (Throwable ignored) {
        }
    }

    public static List<SpeedDialAction> getAllActions() {
        List<SpeedDialAction> list = new ArrayList<>(Arrays.asList(ALL_ACTIONS));
        list.addAll(getCustomActions());
        return list;
    }

    private final FrameLayout scrimView;
    private final LinearLayout itemsContainer;
    private final List<CapsuleItemView> itemViews = new ArrayList<>();
    private final DialogsActivity activity;
    private final FragmentFloatingButton mainFab;
    private final FragmentFloatingButton storiesFab;
    private final Theme.ResourcesProvider resourcesProvider;
    private boolean isOpen;

    public Md3SpeedDialView(@NonNull Context context,
                            DialogsActivity activity,
                            FragmentFloatingButton mainFab,
                            @Nullable FragmentFloatingButton storiesFab,
                            Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.activity = activity;
        this.mainFab = mainFab;
        this.storiesFab = storiesFab;
        this.resourcesProvider = resourcesProvider;

        scrimView = new FrameLayout(context);
        scrimView.setBackgroundColor(ColorUtils.setAlphaComponent(Color.BLACK, 140));
        scrimView.setOnClickListener(v -> close(true));
        addView(scrimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        boolean isLeft = AppearanceConfig.fabAlignment() == AppearanceConfig.FAB_ALIGN_LEFT;
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        itemsContainer.setGravity(isLeft ? Gravity.LEFT : Gravity.RIGHT);
        itemsContainer.setClipChildren(false);
        itemsContainer.setClipToPadding(false);
        addView(itemsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                (isLeft ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, isLeft ? 20 : 0, 0, isLeft ? 0 : 20, 70));

        setVisibility(GONE);
        rebuildItems();
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void rebuildItems() {
        itemsContainer.removeAllViews();
        itemViews.clear();

        boolean isLeft = AppearanceConfig.fabAlignment() == AppearanceConfig.FAB_ALIGN_LEFT;
        itemsContainer.setGravity(isLeft ? Gravity.LEFT : Gravity.RIGHT);
        FrameLayout.LayoutParams containerLp = (FrameLayout.LayoutParams) itemsContainer.getLayoutParams();
        if (containerLp != null) {
            containerLp.gravity = (isLeft ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM;
            containerLp.leftMargin = isLeft ? dp(20) : 0;
            containerLp.rightMargin = isLeft ? 0 : dp(20);
            itemsContainer.setLayoutParams(containerLp);
        }

        String saved = AppearanceConfig.md3FabItems();
        if (TextUtils.isEmpty(saved)) {
            saved = "chat,channel,group,secret,story,qr,saved";
        }
        List<String> keys = Arrays.asList(saved.split(","));
        List<SpeedDialAction> all = getAllActions();
        List<SpeedDialAction> active = new ArrayList<>();
        for (SpeedDialAction a : all) {
            if (keys.contains(a.key)) {
                active.add(a);
            }
        }
        if (active.isEmpty()) {
            active.add(ACTION_CHAT);
        }

        for (int i = 0; i < active.size(); i++) {
            SpeedDialAction action = active.get(i);
            CapsuleItemView itemView = new CapsuleItemView(getContext(), action);
            itemViews.add(itemView);
            boolean isLast = i == active.size() - 1;
            itemsContainer.addView(itemView, LayoutHelper.createLinear(
                    LayoutHelper.WRAP_CONTENT, 48,
                    isLeft ? Gravity.LEFT : Gravity.RIGHT,
                    0, 0, 0, isLast ? 0 : 10
            ));
        }
    }

    public void updateColors() {
        for (CapsuleItemView item : itemViews) {
            item.updateColors();
        }
    }

    public void open() {
        if (isOpen) return;
        isOpen = true;

        if (mainFab != null) {
            mainFab.bringToFront();
            mainFab.morphTo(AppearanceConfig.fabActiveShape(), AppearanceConfig.fabActiveSize(), AppearanceConfig.fabActiveColor(), true);
        }

        rebuildItems();

        final boolean isLeft = AppearanceConfig.fabAlignment() == AppearanceConfig.FAB_ALIGN_LEFT;
        int activeSize = AppearanceConfig.fabActiveSize();
        int restingSize = AppearanceConfig.fabSize();
        int topSize = Math.max(activeSize, restingSize);
        int bottomMargin = dp(14 + topSize + 10);
        if (mainFab != null) {
            // Нижний край FAB в покое (над нижней панелью и т.п.) + максимальная высота кнопки
            float restBottom = mainFab.getY() + mainFab.getHeight();
            float containerH = getHeight() > 0 ? getHeight()
                    : (activity != null && activity.getFragmentView() != null ? activity.getFragmentView().getHeight() : 0);
            if (containerH > 0 && restBottom > 0 && restBottom <= containerH) {
                float topEdge = restBottom - dp(topSize);
                bottomMargin = (int) (containerH - topEdge + dp(10));
            }
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) itemsContainer.getLayoutParams();
        if (lp != null) {
            lp.bottomMargin = bottomMargin;
            lp.leftMargin = isLeft ? dp(20) : 0;
            lp.rightMargin = isLeft ? 0 : dp(20);
            itemsContainer.setLayoutParams(lp);
        }

        setVisibility(VISIBLE);
        scrimView.setAlpha(0f);
        scrimView.animate().alpha(1f).setDuration(240).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();

        if (mainFab != null && mainFab.imageView != null) {
            mainFab.imageView.animate().rotation(45f).setDuration(240).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
        }

        if (storiesFab != null) {
            storiesFab.animate().alpha(0f).scaleX(0.4f).scaleY(0.4f).setDuration(180).start();
        }

        final int count = itemViews.size();
        for (int i = 0; i < count; i++) {
            final CapsuleItemView item = itemViews.get(i);
            item.setAlpha(0f);
            item.setScaleX(0.45f);
            item.setScaleY(0.45f);
            item.setTranslationY(dp(16));
            item.setPivotX(isLeft ? 0 : item.getWidth());
            item.setPivotY(item.getHeight());

            long delay = (count - 1 - i) * 32L;
            item.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(220)
                    .setStartDelay(delay)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .start();
        }
    }

    public void close(boolean animated) {
        if (!isOpen) return;
        isOpen = false;

        if (mainFab != null) {
            mainFab.morphTo(AppearanceConfig.fabShape(), AppearanceConfig.fabSize(), AppearanceConfig.fabColor(), animated);
        }

        if (animated) {
            scrimView.animate().alpha(0f).setDuration(180).start();
            if (mainFab != null && mainFab.imageView != null) {
                mainFab.imageView.animate().rotation(0f).setDuration(200).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
            }
            if (storiesFab != null) {
                storiesFab.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).start();
            }

            for (CapsuleItemView item : itemViews) {
                item.animate()
                        .alpha(0f)
                        .scaleX(0.5f)
                        .scaleY(0.5f)
                        .translationY(dp(12))
                        .setDuration(150)
                        .start();
            }

            postDelayed(() -> {
                if (!isOpen) {
                    setVisibility(GONE);
                }
            }, 190);
        } else {
            scrimView.setAlpha(0f);
            if (mainFab != null && mainFab.imageView != null) {
                mainFab.imageView.setRotation(0f);
            }
            if (storiesFab != null) {
                storiesFab.setAlpha(1f);
                storiesFab.setScaleX(1f);
                storiesFab.setScaleY(1f);
            }
            setVisibility(GONE);
        }
    }

    private void performAction(SpeedDialAction action) {
        if (activity == null) return;
        if (action.isCustom()) {
            performCustomAction(action);
            return;
        }
        switch (action.key) {
            case "chat":
                activity.openWriteContacts();
                break;
            case "channel":
                if (MessagesController.getInstance(activity.getCurrentAccount()).isFrozen()) {
                    AccountFrozenAlert.show(activity.getCurrentAccount());
                } else {
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    if (!BuildVars.DEBUG_VERSION && preferences.getBoolean("channel_intro", false)) {
                        Bundle args = new Bundle();
                        args.putInt("step", 0);
                        activity.presentFragment(new ChannelCreateActivity(args));
                    } else {
                        activity.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
                        preferences.edit().putBoolean("channel_intro", true).commit();
                    }
                }
                break;
            case "group":
                if (MessagesController.getInstance(activity.getCurrentAccount()).isFrozen()) {
                    AccountFrozenAlert.show(activity.getCurrentAccount());
                } else {
                    Bundle args = new Bundle();
                    activity.presentFragment(new GroupCreateActivity(args));
                }
                break;
            case "secret":
                if (MessagesController.getInstance(activity.getCurrentAccount()).isFrozen()) {
                    AccountFrozenAlert.show(activity.getCurrentAccount());
                } else {
                    Bundle args = new Bundle();
                    args.putBoolean("onlyUsers", true);
                    args.putBoolean("destroyAfterSelect", true);
                    args.putBoolean("createSecretChat", true);
                    args.putBoolean("selectAlertString", false);
                    activity.presentFragment(new ContactsActivity(args));
                }
                break;
            case "story":
                activity.openStoriesRecorder();
                break;
            case "qr":
                if (activity.getParentActivity() != null) {
                    CameraScanActivity.showAsSheet(activity, false, CameraScanActivity.TYPE_QR, new CameraScanActivity.CameraScanActivityDelegate() {
                        @Override
                        public boolean processQr(String text, Runnable onLoadEnd) {
                            AndroidUtilities.runOnUIThread(() -> {
                                onLoadEnd.run();
                                AndroidUtilities.runOnUIThread(() -> {
                                    BaseFragment last = LaunchActivity.getSafeLastFragment();
                                    if (last != null && last.getParentActivity() != null) {
                                        new QRCodeSheet(last, text).show();
                                    }
                                }, 150);
                            }, 600);
                            return true;
                        }
                    });
                }
                break;
            case "saved":
                Bundle args = new Bundle();
                args.putLong("user_id", UserConfig.getInstance(activity.getCurrentAccount()).getClientUserId());
                activity.presentFragment(new ChatActivity(args));
                break;
        }
    }

    private void performCustomAction(SpeedDialAction action) {
        if (activity == null || action == null) return;
        String type = action.actionType != null ? action.actionType : "deeplink";
        String target = action.target != null ? action.target.trim() : "";

        if ("chat".equals(type)) {
            if (target.startsWith("@")) {
                target = target.substring(1);
            }
            if (TextUtils.isDigitsOnly(target)) {
                try {
                    long dialogId = Long.parseLong(target);
                    Bundle args = new Bundle();
                    if (dialogId > 0) {
                        args.putLong("user_id", dialogId);
                    } else {
                        args.putLong("chat_id", -dialogId);
                    }
                    activity.presentFragment(new ChatActivity(args));
                    return;
                } catch (Exception ignored) {}
            }
            activity.getMessagesController().openByUserName(target, activity, 1);
        } else if ("setting".equals(type)) {
            if ("appearance".equalsIgnoreCase(target)) {
                activity.presentFragment(new app.exteraless.settings.OpenExteraAppearanceActivity());
            } else if ("chats".equalsIgnoreCase(target)) {
                activity.presentFragment(new app.exteraless.settings.OpenExteraChatsActivity());
            } else if ("updates".equalsIgnoreCase(target)) {
                activity.presentFragment(new app.exteraless.settings.OpenExteraUpdatesActivity());
            } else if ("plugins".equalsIgnoreCase(target)) {
                activity.presentFragment(new app.exteraless.plugins.ui.PluginsActivity());
            } else if ("navigation".equalsIgnoreCase(target) || "fab".equalsIgnoreCase(target)) {
                activity.presentFragment(new app.exteraless.settings.OpenExteraAppNavigationActivity());
            } else {
                activity.presentFragment(new app.exteraless.settings.OpenExteraSettingsActivity());
            }
        } else if ("plugin".equals(type)) {
            if (!TextUtils.isEmpty(target)) {
                try {
                    app.exteraless.plugins.PluginsController.getInstance().executeOnAppEvent("fab_action_" + target);
                    Toast.makeText(activity.getParentActivity(), "Команда: " + target, Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Toast.makeText(activity.getParentActivity(), "Ошибка: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        } else { // "deeplink" or URL
            if (!TextUtils.isEmpty(target)) {
                if (target.startsWith("http://") || target.startsWith("https://")) {
                    org.telegram.messenger.browser.Browser.openUrl(activity.getParentActivity(), target);
                } else if (target.startsWith("tg://")) {
                    activity.getMessagesController().openByUserName(target.replace("tg://resolve?domain=", "").replace("tg://", ""), activity, 1);
                } else {
                    org.telegram.messenger.browser.Browser.openUrl(activity.getParentActivity(), target);
                }
            }
        }
    }

    public static void showCustomizeDialog(Context context, @Nullable Runnable onSaved) {
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.OEAppearanceMd3FabCustomize));
        final AlertDialog[] dialogRef = new AlertDialog[1];

        String saved = AppearanceConfig.md3FabItems();
        if (TextUtils.isEmpty(saved)) {
            saved = "chat,channel,group,secret,story,qr,saved";
        }
        final List<String> activeKeys = new ArrayList<>(Arrays.asList(saved.split(",")));
        final List<SpeedDialAction> allActions = getAllActions();

        ScrollView scrollView = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(10), 0, dp(10));
        scrollView.addView(container);

        final CheckBoxCell[] cells = new CheckBoxCell[allActions.size()];
        for (int i = 0; i < allActions.size(); i++) {
            final SpeedDialAction action = allActions.get(i);
            boolean checked = activeKeys.contains(action.key);

            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);

            cells[i] = new CheckBoxCell(context, 1);
            cells[i].setBackground(Theme.getSelectorDrawable(false));
            cells[i].setText(action.getTitle(), action.isCustom() ? "(" + action.actionType + ")" : "", checked, false);
            final int index = i;
            cells[i].setOnClickListener(v -> cells[index].setChecked(!cells[index].isChecked(), true));

            rowLayout.addView(cells[i], LayoutHelper.createLinear(0, 48, 1f, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

            if (action.isCustom()) {
                ImageView deleteBtn = new ImageView(context);
                deleteBtn.setImageResource(R.drawable.msg_delete);
                deleteBtn.setColorFilter(Theme.getColor(Theme.key_dialogTextRed));
                deleteBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                deleteBtn.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector)));
                deleteBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
                deleteBtn.setOnClickListener(v -> {
                    List<SpeedDialAction> customList = getCustomActions();
                    for (int c = 0; c < customList.size(); c++) {
                        if (customList.get(c).key.equals(action.key)) {
                            customList.remove(c);
                            break;
                        }
                    }
                    saveCustomActions(customList);
                    activeKeys.remove(action.key);
                    String savedItems = AppearanceConfig.md3FabItems();
                    if (!TextUtils.isEmpty(savedItems)) {
                        List<String> list = new ArrayList<>(Arrays.asList(savedItems.split(",")));
                        list.remove(action.key);
                        AppearanceConfig.md3FabItems.setConfigString(TextUtils.join(",", list));
                    }
                    if (onSaved != null) {
                        onSaved.run();
                    }
                    if (dialogRef[0] != null) {
                        dialogRef[0].dismiss();
                    }
                    showCustomizeDialog(context, onSaved);
                });
                rowLayout.addView(deleteBtn, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
            }

            container.addView(rowLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        // Add Custom Action button
        TextView addCustomBtn = new TextView(context);
        addCustomBtn.setText("+ Добавить свой пункт...");
        addCustomBtn.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
        addCustomBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        addCustomBtn.setPadding(dp(16), dp(12), dp(16), dp(12));
        addCustomBtn.setBackground(Theme.getSelectorDrawable(false));
        addCustomBtn.setOnClickListener(v -> {
            if (dialogRef[0] != null) {
                dialogRef[0].dismiss();
            }
            showAddCustomActionDialog(context, () -> showCustomizeDialog(context, onSaved));
        });
        container.addView(addCustomBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(scrollView);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < allActions.size(); i++) {
                if (cells[i] != null && cells[i].isChecked()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(allActions.get(i).key);
                }
            }
            String result = sb.length() > 0 ? sb.toString() : "chat";
            AppearanceConfig.md3FabItems.setConfigString(result);
            if (onSaved != null) {
                onSaved.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        dialogRef[0] = builder.show();
    }

    public static void showIconPickerGrid(Context context, String selectedIconName, Consumer<String> onSelected) {
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Выберите иконку");

        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        // Search input bar
        LinearLayout searchContainer = new LinearLayout(context);
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), 0.06f));
        searchBg.setCornerRadius(dp(12));
        searchContainer.setBackground(searchBg);
        searchContainer.setPadding(dp(12), dp(2), dp(12), dp(2));

        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        searchIcon.setColorFilter(Theme.getColor(Theme.key_dialogTextGray2), PorterDuff.Mode.SRC_IN);
        searchContainer.addView(searchIcon, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        final EditText searchEdit = new EditText(context);
        searchEdit.setHint("Поиск иконки...");
        searchEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        searchEdit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        searchEdit.setBackground(null);
        searchEdit.setSingleLine(true);
        searchEdit.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchContainer.addView(searchEdit, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        final ImageView clearBtn = new ImageView(context);
        clearBtn.setImageResource(R.drawable.msg_clear);
        clearBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        clearBtn.setColorFilter(Theme.getColor(Theme.key_dialogTextGray2), PorterDuff.Mode.SRC_IN);
        clearBtn.setVisibility(View.GONE);
        clearBtn.setOnClickListener(v -> searchEdit.setText(""));
        searchContainer.addView(clearBtn, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));

        mainLayout.addView(searchContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 8));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout gridLayout = new LinearLayout(context);
        gridLayout.setOrientation(LinearLayout.VERTICAL);
        gridLayout.setPadding(dp(12), dp(4), dp(12), dp(8));
        scrollView.addView(gridLayout);

        mainLayout.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final int columns = 5;
        final AlertDialog[] dialogRef = new AlertDialog[1];

        final Runnable[] populate = new Runnable[1];
        final List<IconChoice> currentList = new ArrayList<>(Arrays.asList(ALL_ICON_CHOICES));

        populate[0] = () -> {
            gridLayout.removeAllViews();
            if (currentList.isEmpty()) {
                TextView emptyView = new TextView(context);
                emptyView.setText("Ничего не найдено");
                emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                emptyView.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
                emptyView.setGravity(Gravity.CENTER);
                emptyView.setPadding(0, dp(32), 0, dp(32));
                gridLayout.addView(emptyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return;
            }

            for (int i = 0; i < currentList.size(); i += columns) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setWeightSum(columns);

                for (int col = 0; col < columns; col++) {
                    int idx = i + col;
                    if (idx < currentList.size()) {
                        final IconChoice choice = currentList.get(idx);
                        final boolean isSelected = choice.key.equals(selectedIconName);

                        FrameLayout cell = new FrameLayout(context);
                        GradientDrawable cellBg = new GradientDrawable();
                        cellBg.setCornerRadius(dp(12));
                        if (isSelected) {
                            cellBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                        } else {
                            int textColor = Theme.getColor(Theme.key_dialogTextBlack);
                            cellBg.setColor(Theme.multAlpha(textColor, 0.07f));
                        }
                        cell.setBackground(cellBg);

                        ImageView iv = new ImageView(context);
                        iv.setImageResource(choice.resId);
                        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                        int onAccent = app.exteraless.utils.UIUtil.getOnAccentColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                        iv.setColorFilter(isSelected ? onAccent : Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.SRC_IN);
                        cell.addView(iv, LayoutHelper.createFrame(26, 26, Gravity.CENTER));

                        cell.setOnClickListener(v -> {
                            if (onSelected != null) {
                                onSelected.accept(choice.key);
                            }
                            if (dialogRef[0] != null) {
                                dialogRef[0].dismiss();
                            }
                        });

                        row.addView(cell, LayoutHelper.createLinear(0, 48, 1f, 3, 3, 3, 3));
                    } else {
                        View empty = new View(context);
                        row.addView(empty, LayoutHelper.createLinear(0, 48, 1f, 3, 3, 3, 3));
                    }
                }
                gridLayout.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));
            }
        };

        populate[0].run();

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String q = s != null ? s.toString().trim().toLowerCase(java.util.Locale.ROOT) : "";
                clearBtn.setVisibility(q.isEmpty() ? View.GONE : View.VISIBLE);
                currentList.clear();
                for (IconChoice choice : ALL_ICON_CHOICES) {
                    if (q.isEmpty() || choice.key.toLowerCase(java.util.Locale.ROOT).contains(q)
                            || choice.titleRu.toLowerCase(java.util.Locale.ROOT).contains(q)
                            || choice.keywords.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        currentList.add(choice);
                    }
                }
                populate[0].run();
            }
        });

        builder.setView(mainLayout);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        dialogRef[0] = builder.create();
        dialogRef[0].show();
    }

    public static void showAddCustomActionDialog(Context context, @Nullable Runnable onDone) {
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Новый пункт FAB");

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(20), dp(14), dp(20), dp(12));

        // 1. Title
        TextView titleLabel = new TextView(context);
        titleLabel.setText("Название пункта:");
        titleLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        titleLabel.setTypeface(AndroidUtilities.bold());
        container.addView(titleLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        final EditText titleEdit = new EditText(context);
        titleEdit.setHint("Например: Настройки, Поддержка");
        titleEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleEdit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        GradientDrawable titleBg = new GradientDrawable();
        titleBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        titleBg.setCornerRadius(dp(8));
        titleEdit.setBackground(titleBg);
        titleEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        container.addView(titleEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // 2. Type selector
        TextView typeLabel = new TextView(context);
        typeLabel.setText("Тип действия:");
        typeLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        typeLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        typeLabel.setTypeface(AndroidUtilities.bold());
        container.addView(typeLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        final String[] types = new String[]{"deeplink", "chat", "setting", "plugin"};
        final String[] typeLabels = new String[]{"Ссылка / URL (tg://, https://)", "Чат / Канал (@username или ID)", "Настройки MoltenGram", "Функция / Команда плагина"};
        final int[] selectedType = new int[]{0};

        final TextView typeSelector = new TextView(context);
        typeSelector.setText(typeLabels[0]);
        typeSelector.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        typeSelector.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        GradientDrawable typeBg = new GradientDrawable();
        typeBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        typeBg.setCornerRadius(dp(8));
        typeSelector.setBackground(typeBg);
        typeSelector.setPadding(dp(12), dp(10), dp(12), dp(10));
        typeSelector.setOnClickListener(v -> {
            AlertDialog.Builder typeBuilder = new AlertDialog.Builder(context);
            typeBuilder.setTitle("Выберите тип действия");
            typeBuilder.setItems(typeLabels, (d, which) -> {
                selectedType[0] = which;
                typeSelector.setText(typeLabels[which]);
            });
            typeBuilder.show();
        });
        container.addView(typeSelector, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // 3. Target value
        TextView targetLabel = new TextView(context);
        targetLabel.setText("Значение действия:");
        targetLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        targetLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        targetLabel.setTypeface(AndroidUtilities.bold());
        container.addView(targetLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        final EditText targetEdit = new EditText(context);
        targetEdit.setHint("URL, @username, id, раздел настроек");
        targetEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        targetEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        targetEdit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        GradientDrawable targetBg = new GradientDrawable();
        targetBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        targetBg.setCornerRadius(dp(8));
        targetEdit.setBackground(targetBg);
        targetEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        container.addView(targetEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // 4. Visual icon selector preview
        TextView iconLabel = new TextView(context);
        iconLabel.setText("Иконка кнопки:");
        iconLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        iconLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        iconLabel.setTypeface(AndroidUtilities.bold());
        container.addView(iconLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        final String[] selectedIcon = new String[]{ICON_CHOICES[0]};

        LinearLayout iconRow = new LinearLayout(context);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable iconRowBg = new GradientDrawable();
        iconRowBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        iconRowBg.setCornerRadius(dp(8));
        iconRow.setBackground(iconRowBg);
        iconRow.setPadding(dp(12), dp(8), dp(12), dp(8));

        final ImageView iconPreview = new ImageView(context);
        iconPreview.setImageResource(getIconResByName(selectedIcon[0]));
        iconPreview.setColorFilter(Theme.getColor(Theme.key_featuredStickers_addButton), PorterDuff.Mode.SRC_IN);
        iconRow.addView(iconPreview, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        TextView iconPrompt = new TextView(context);
        iconPrompt.setText("Выбрать иконку из галереи...");
        iconPrompt.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
        iconPrompt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        iconRow.addView(iconPrompt, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        iconRow.setOnClickListener(v -> {
            showIconPickerGrid(context, selectedIcon[0], chosen -> {
                selectedIcon[0] = chosen;
                iconPreview.setImageResource(getIconResByName(chosen));
            });
        });
        container.addView(iconRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        builder.setView(container);
        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String title = titleEdit.getText().toString().trim();
            String target = targetEdit.getText().toString().trim();
            if (TextUtils.isEmpty(title)) {
                title = "Действие";
            }
            String type = types[selectedType[0]];
            String icon = selectedIcon[0];
            String id = "custom_" + System.currentTimeMillis();

            List<SpeedDialAction> customList = getCustomActions();
            customList.add(new SpeedDialAction(id, getIconResByName(icon), 0, title, type, target, icon));
            saveCustomActions(customList);

            String saved = AppearanceConfig.md3FabItems();
            if (TextUtils.isEmpty(saved)) {
                saved = "chat,channel,group,secret,story,qr,saved";
            }
            AppearanceConfig.md3FabItems.setConfigString(saved + "," + id);

            if (onDone != null) {
                onDone.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static String getItemsSummary() {
        String saved = AppearanceConfig.md3FabItems();
        if (TextUtils.isEmpty(saved)) return "";
        List<String> keys = Arrays.asList(saved.split(","));
        List<SpeedDialAction> all = getAllActions();
        StringBuilder sb = new StringBuilder();
        for (SpeedDialAction action : all) {
            if (keys.contains(action.key)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(action.getTitle());
            }
        }
        return sb.toString();
    }

    private class CapsuleItemView extends LinearLayout {
        private final ImageView iconView;
        private final TextView textView;
        private final SpeedDialAction action;

        public CapsuleItemView(Context context, SpeedDialAction action) {
            super(context);
            this.action = action;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setClickable(true);
            setFocusable(true);

            final int h = dp(48);
            final int rad = h / 2;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setElevation(dp(3));
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), rad);
                    }
                });
                setClipToOutline(true);
            }

            iconView = new ImageView(context);
            iconView.setImageResource(action.iconRes);
            iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 16, 0, 10, 0));

            textView = new TextView(context);
            textView.setText(action.getTitle());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTypeface(AndroidUtilities.headerTypeface());
            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 20, 0));

            updateColors();

            setOnClickListener(v -> {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                close(true);
                postDelayed(() -> performAction(action), 150);
            });
        }

        public void updateColors() {
            final int h = dp(48);
            final int rad = h / 2;
            int themeBg = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int themeAccent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            int bgColor = ColorUtils.blendARGB(themeBg, themeAccent, 0.14f);
            int pressedColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);

            setBackground(Theme.createSimpleSelectorRoundRectDrawable(rad, bgColor, pressedColor));

            int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            iconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
            textView.setTextColor(textColor);
        }
    }
}
