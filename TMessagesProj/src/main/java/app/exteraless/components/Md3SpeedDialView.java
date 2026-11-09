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
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
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
import android.text.InputType;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.ChannelCreateActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.AccountFrozenAlert;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.OutlineTextContainerView;
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
                if (a.isCustom() || !TextUtils.isEmpty(a.customTitle) || !TextUtils.isEmpty(a.iconName)) {
                    JSONObject obj = new JSONObject();
                    obj.put("id", a.key);
                    obj.put("title", a.customTitle != null ? a.customTitle : a.getTitle());
                    obj.put("type", a.actionType != null ? a.actionType : "");
                    obj.put("target", a.target != null ? a.target : "");
                    obj.put("icon", a.iconName != null ? a.iconName : "");
                    array.put(obj);
                }
            }
            AppearanceConfig.md3FabCustomItems.setConfigString(array.toString());
        } catch (Throwable ignored) {
        }
    }

    public static List<SpeedDialAction> getAllActions() {
        List<SpeedDialAction> custom = getCustomActions();
        List<SpeedDialAction> list = new ArrayList<>();
        for (SpeedDialAction builtIn : ALL_ACTIONS) {
            SpeedDialAction override = null;
            for (SpeedDialAction c : custom) {
                if (c.key.equals(builtIn.key)) {
                    override = c;
                    break;
                }
            }
            if (override != null) {
                list.add(new SpeedDialAction(
                        builtIn.key,
                        override.iconRes != 0 ? override.iconRes : builtIn.iconRes,
                        builtIn.titleRes,
                        override.customTitle,
                        null,
                        null,
                        override.iconName
                ));
            } else {
                list.add(builtIn);
            }
        }
        for (SpeedDialAction c : custom) {
            if (c.isCustom()) {
                list.add(c);
            }
        }
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
        for (String k : keys) {
            for (SpeedDialAction a : all) {
                if (a.key.equals(k)) {
                    active.add(a);
                    break;
                }
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

        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, null);
        builder.setApplyBottomPadding(false);
        builder.setApplyTopPadding(false);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(24));

        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int hintColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        int buttonTextColor = Theme.getColor(Theme.key_featuredStickers_buttonText);
        int inactiveBg = Theme.getColor(Theme.key_windowBackgroundGray);

        // Drag handle
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dp(3));
        handleBg.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
        handle.setBackground(handleBg);
        root.addView(handle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Header Title & Subtitle
        TextView titleView = new TextView(context);
        titleView.setText("Настройка пунктов меню FAB");
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTextColor(textColor);
        root.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView subtitleView = new TextView(context);
        subtitleView.setText("Стиль оформления, порядок, видимость и параметры действий кнопки FAB.");
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(hintColor);
        root.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Section 1: Item Style Selector
        TextView styleHeader = new TextView(context);
        styleHeader.setText("СТИЛЬ ПУНКТОВ МЕНЮ");
        styleHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        styleHeader.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        styleHeader.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        root.addView(styleHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        HorizontalScrollView styleScroll = new HorizontalScrollView(context);
        styleScroll.setHorizontalScrollBarEnabled(false);
        styleScroll.setClipToPadding(false);

        LinearLayout styleChips = new LinearLayout(context);
        styleChips.setOrientation(LinearLayout.HORIZONTAL);

        final String[] styleNames = new String[]{"Капсула (MD3)", "Карточка", "Тоновая", "Контур / Стекло"};
        final int[] styleIds = new int[]{
                AppearanceConfig.FAB_ITEM_STYLE_CAPSULE,
                AppearanceConfig.FAB_ITEM_STYLE_CARD,
                AppearanceConfig.FAB_ITEM_STYLE_FILLED_TONAL,
                AppearanceConfig.FAB_ITEM_STYLE_OUTLINE
        };
        final TextView[] styleChipViews = new TextView[styleNames.length];
        final int[] currentStyle = new int[]{AppearanceConfig.md3FabItemStyle()};

        Runnable updateStyleChips = () -> {
            for (int i = 0; i < styleNames.length; i++) {
                boolean active = (styleIds[i] == currentStyle[0]);
                styleChipViews[i].setTextColor(active ? buttonTextColor : textColor);
                styleChipViews[i].setBackground(Theme.createRoundRectDrawable(dp(16), active ? accentColor : inactiveBg));
            }
        };

        for (int i = 0; i < styleNames.length; i++) {
            final int idx = i;
            TextView chip = new TextView(context);
            chip.setText(styleNames[i]);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            styleChipViews[i] = chip;

            chip.setOnClickListener(v -> {
                currentStyle[0] = styleIds[idx];
                AppearanceConfig.md3FabItemStyle.setConfigInt(currentStyle[0]);
                updateStyleChips.run();
                if (onSaved != null) onSaved.run();
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            if (i < styleNames.length - 1) {
                lp.rightMargin = dp(8);
            }
            styleChips.addView(chip, lp);
        }
        updateStyleChips.run();
        styleScroll.addView(styleChips, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        root.addView(styleScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        // Section 2: Items List with Visibility, Reordering and Edit
        TextView itemsHeader = new TextView(context);
        itemsHeader.setText("ПУНКТЫ МЕНЮ И ПОРЯДОК");
        itemsHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        itemsHeader.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        itemsHeader.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        root.addView(itemsHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        String saved = AppearanceConfig.md3FabItems();
        if (TextUtils.isEmpty(saved)) {
            saved = "chat,channel,group,secret,story,qr,saved";
        }
        final List<String> activeKeys = new ArrayList<>(Arrays.asList(saved.split(",")));
        final List<SpeedDialAction> allActions = getAllActions();

        final List<SpeedDialAction> orderedActions = new ArrayList<>();
        for (String k : activeKeys) {
            for (SpeedDialAction a : allActions) {
                if (a.key.equals(k) && !orderedActions.contains(a)) {
                    orderedActions.add(a);
                    break;
                }
            }
        }
        for (SpeedDialAction a : allActions) {
            if (!orderedActions.contains(a)) {
                orderedActions.add(a);
            }
        }

        final LinearLayout itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);

        final BottomSheet[] sheetRef = new BottomSheet[1];

        final Runnable[] rebuildList = new Runnable[1];
        rebuildList[0] = () -> {
            itemsContainer.removeAllViews();

            for (int i = 0; i < orderedActions.size(); i++) {
                final int index = i;
                final SpeedDialAction action = orderedActions.get(i);
                final boolean checked = activeKeys.contains(action.key);

                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(10), dp(8), dp(10), dp(8));
                GradientDrawable rowBg = new GradientDrawable();
                rowBg.setCornerRadius(dp(12));
                rowBg.setColor(Theme.multAlpha(textColor, checked ? 0.05f : 0.02f));
                row.setBackground(rowBg);

                // Checkbox toggle
                ImageView checkIv = new ImageView(context);
                checkIv.setImageResource(checked ? R.drawable.msg_check : R.drawable.round_check2);
                checkIv.setColorFilter(checked ? accentColor : Theme.multAlpha(textColor, 0.3f), PorterDuff.Mode.SRC_IN);
                checkIv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                checkIv.setPadding(dp(4), dp(4), dp(4), dp(4));
                checkIv.setOnClickListener(v -> {
                    if (checked) {
                        activeKeys.remove(action.key);
                    } else {
                        if (!activeKeys.contains(action.key)) {
                            activeKeys.add(action.key);
                        }
                    }
                    rebuildList[0].run();
                });
                row.addView(checkIv, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

                // Action Icon inside rounded pill
                FrameLayout iconBox = new FrameLayout(context);
                GradientDrawable iconBoxBg = new GradientDrawable();
                iconBoxBg.setCornerRadius(dp(8));
                iconBoxBg.setColor(Theme.multAlpha(accentColor, checked ? 0.14f : 0.06f));
                iconBox.setBackground(iconBoxBg);

                ImageView iconIv = new ImageView(context);
                iconIv.setImageResource(action.iconRes);
                iconIv.setColorFilter(checked ? accentColor : Theme.multAlpha(textColor, 0.4f), PorterDuff.Mode.SRC_IN);
                iconIv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                iconBox.addView(iconIv, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
                row.addView(iconBox, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

                // Title & Subtitle
                LinearLayout textCol = new LinearLayout(context);
                textCol.setOrientation(LinearLayout.VERTICAL);

                TextView nameTv = new TextView(context);
                nameTv.setText(action.getTitle());
                nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
                nameTv.setTypeface(AndroidUtilities.bold());
                nameTv.setTextColor(checked ? textColor : Theme.multAlpha(textColor, 0.5f));
                nameTv.setSingleLine(true);
                nameTv.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(nameTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                String sub = action.isCustom() ? ("Пользовательский (" + action.actionType + ")") : "Встроенный";
                TextView subTv = new TextView(context);
                subTv.setText(sub);
                subTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
                subTv.setTextColor(hintColor);
                subTv.setSingleLine(true);
                textCol.addView(subTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

                row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

                // Move Up button
                ImageView upBtn = new ImageView(context);
                upBtn.setImageResource(R.drawable.baseline_keyboard_arrow_up_24);
                upBtn.setColorFilter(index > 0 ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText) : Theme.multAlpha(textColor, 0.2f), PorterDuff.Mode.SRC_IN);
                upBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                upBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
                upBtn.setOnClickListener(v -> {
                    if (index > 0) {
                        SpeedDialAction prev = orderedActions.remove(index - 1);
                        orderedActions.add(index, prev);
                        if (activeKeys.contains(action.key) && activeKeys.contains(prev.key)) {
                            int k1 = activeKeys.indexOf(prev.key);
                            int k2 = activeKeys.indexOf(action.key);
                            if (k1 != -1 && k2 != -1) {
                                activeKeys.set(k1, action.key);
                                activeKeys.set(k2, prev.key);
                            }
                        }
                        rebuildList[0].run();
                    }
                });
                row.addView(upBtn, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 2, 0, 2, 0));

                // Move Down button
                ImageView downBtn = new ImageView(context);
                downBtn.setImageResource(R.drawable.baseline_keyboard_arrow_down_24);
                downBtn.setColorFilter(index < orderedActions.size() - 1 ? Theme.getColor(Theme.key_windowBackgroundWhiteBlueText) : Theme.multAlpha(textColor, 0.2f), PorterDuff.Mode.SRC_IN);
                downBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                downBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
                downBtn.setOnClickListener(v -> {
                    if (index < orderedActions.size() - 1) {
                        SpeedDialAction next = orderedActions.remove(index + 1);
                        orderedActions.add(index, next);
                        if (activeKeys.contains(action.key) && activeKeys.contains(next.key)) {
                            int k1 = activeKeys.indexOf(action.key);
                            int k2 = activeKeys.indexOf(next.key);
                            if (k1 != -1 && k2 != -1) {
                                activeKeys.set(k1, next.key);
                                activeKeys.set(k2, action.key);
                            }
                        }
                        rebuildList[0].run();
                    }
                });
                row.addView(downBtn, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 2, 0, 4, 0));

                // Edit button (pencil)
                ImageView editBtn = new ImageView(context);
                editBtn.setImageResource(R.drawable.msg_edit);
                editBtn.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText), PorterDuff.Mode.SRC_IN);
                editBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                editBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
                editBtn.setOnClickListener(v -> {
                    if (sheetRef[0] != null) sheetRef[0].dismiss();
                    showAddOrEditCustomActionDialog(context, action, () -> showCustomizeDialog(context, onSaved));
                });
                row.addView(editBtn, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 2, 0, 4, 0));

                // Delete button for custom action
                if (action.isCustom()) {
                    ImageView delBtn = new ImageView(context);
                    delBtn.setImageResource(R.drawable.msg_delete);
                    delBtn.setColorFilter(Theme.getColor(Theme.key_dialogTextRed), PorterDuff.Mode.SRC_IN);
                    delBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    delBtn.setPadding(dp(6), dp(6), dp(6), dp(6));
                    delBtn.setOnClickListener(v -> {
                        List<SpeedDialAction> customList = getCustomActions();
                        for (int c = 0; c < customList.size(); c++) {
                            if (customList.get(c).key.equals(action.key)) {
                                customList.remove(c);
                                break;
                            }
                        }
                        saveCustomActions(customList);
                        activeKeys.remove(action.key);
                        orderedActions.remove(action);
                        rebuildList[0].run();
                    });
                    row.addView(delBtn, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));
                }

                itemsContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
            }
        };

        rebuildList[0].run();
        root.addView(itemsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Add Custom Action button (+ Добавить свой пункт...)
        TextView addCustomBtn = new TextView(context);
        addCustomBtn.setText("+ Добавить свой пункт FAB...");
        addCustomBtn.setGravity(Gravity.CENTER);
        addCustomBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        addCustomBtn.setTypeface(AndroidUtilities.bold());
        addCustomBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        GradientDrawable addBtnBg = new GradientDrawable();
        addBtnBg.setCornerRadius(dp(12));
        addBtnBg.setColor(Theme.multAlpha(accentColor, 0.10f));
        addCustomBtn.setBackground(addBtnBg);
        addCustomBtn.setPadding(0, dp(12), 0, dp(12));
        addCustomBtn.setOnClickListener(v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            showAddOrEditCustomActionDialog(context, null, () -> showCustomizeDialog(context, onSaved));
        });
        root.addView(addCustomBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Action Buttons: Done & Cancel
        TextView doneButton = new TextView(context);
        doneButton.setText(LocaleController.getString(R.string.Done));
        doneButton.setGravity(Gravity.CENTER);
        doneButton.setTypeface(AndroidUtilities.bold());
        doneButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        doneButton.setTextColor(buttonTextColor);
        doneButton.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 10));
        doneButton.setPadding(0, dp(14), 0, dp(14));
        doneButton.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (SpeedDialAction act : orderedActions) {
                if (activeKeys.contains(act.key)) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(act.key);
                }
            }
            String result = sb.length() > 0 ? sb.toString() : "chat";
            AppearanceConfig.md3FabItems.setConfigString(result);
            if (sheetRef[0] != null) sheetRef[0].dismiss();
            if (onSaved != null) onSaved.run();
        });
        root.addView(doneButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView cancelBtn = new TextView(context);
        cancelBtn.setText(LocaleController.getString(R.string.Cancel));
        cancelBtn.setGravity(Gravity.CENTER);
        cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        cancelBtn.setPadding(0, dp(8), 0, dp(8));
        cancelBtn.setOnClickListener(v -> {
            if (sheetRef[0] != null) sheetRef[0].dismiss();
        });
        root.addView(cancelBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scrollView.addView(root);
        builder.setCustomView(scrollView);
        sheetRef[0] = builder.create();
        sheetRef[0].show();
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
        showAddOrEditCustomActionDialog(context, null, onDone);
    }

    public static void showAddOrEditCustomActionDialog(Context context, @Nullable SpeedDialAction existing, @Nullable Runnable onDone) {
        if (context == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(context, true, null);
        builder.setApplyBottomPadding(false);
        builder.setApplyTopPadding(false);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(16), dp(22), dp(24));

        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        int hintColor = Theme.getColor(Theme.key_windowBackgroundWhiteHintText);
        if (hintColor == 0) {
            hintColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
        }
        int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        int buttonTextColor = Theme.getColor(Theme.key_featuredStickers_buttonText);
        int inactiveBg = Theme.getColor(Theme.key_windowBackgroundGray);

        // Drag handle pill
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dp(3));
        handleBg.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
        handle.setBackground(handleBg);
        layout.addView(handle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        TextView titleView = new TextView(context);
        titleView.setText(existing != null ? "Редактирование пункта FAB" : "Новый пункт меню FAB");
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        titleView.setTextColor(textColor);
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 16));

        final String[] types = new String[]{"deeplink", "chat", "setting", "plugin"};
        final String[] typeLabels = new String[]{"URL", "Чат", "Настройки", "Плагин"};
        final String[] typePlaceholders = new String[]{
                "https://... или tg://...",
                "@username или ID канала",
                "Раздел (appearance, general, chat...)",
                "Команда или функция плагина"
        };
        final int[] selectedType = new int[]{0};

        if (existing != null && existing.actionType != null) {
            for (int i = 0; i < types.length; i++) {
                if (types[i].equalsIgnoreCase(existing.actionType)) {
                    selectedType[0] = i;
                    break;
                }
            }
        }

        final TextView[] chipViews = new TextView[types.length];
        LinearLayout chipsLayout = new LinearLayout(context);
        chipsLayout.setOrientation(LinearLayout.HORIZONTAL);

        boolean isCustomAction = (existing == null || existing.isCustom());

        OutlineTextContainerView targetOutlineView = new OutlineTextContainerView(context);

        Runnable updateChips = () -> {
            for (int i = 0; i < types.length; i++) {
                boolean isSelected = (i == selectedType[0]);
                chipViews[i].setTextColor(isSelected ? buttonTextColor : textColor);
                chipViews[i].setBackground(Theme.createRoundRectDrawable(dp(18), isSelected ? accentColor : inactiveBg));
            }
        };

        if (isCustomAction) {
            for (int i = 0; i < types.length; i++) {
                final int idx = i;
                TextView chip = new TextView(context);
                chip.setText(typeLabels[i]);
                chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                chip.setTypeface(AndroidUtilities.bold());
                chip.setGravity(Gravity.CENTER);
                chipViews[i] = chip;
                chip.setOnClickListener(v -> {
                    selectedType[0] = idx;
                    updateChips.run();
                    targetOutlineView.setText("Значение: " + typePlaceholders[idx]);
                });
                chipsLayout.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, 0, 0, 8, 0));
                chip.setPadding(dp(14), 0, dp(14), 0);
            }
            updateChips.run();
            layout.addView(chipsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
        }

        int padH = dp(14);
        int padV = dp(14);

        // Title input (OutlineTextContainerView + EditTextBoldCursor)
        OutlineTextContainerView nameOutlineView = new OutlineTextContainerView(context);
        nameOutlineView.setText("Название пункта");

        EditTextBoldCursor nameEdit = new EditTextBoldCursor(context);
        nameEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameEdit.setTextColor(textColor);
        nameEdit.setCursorColor(accentColor);
        nameEdit.setBackground(null);
        nameEdit.setPadding(padH, padV, padH, padV);
        nameEdit.setSingleLine(true);
        nameEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);

        if (existing != null) {
            nameEdit.setText(existing.getTitle());
        }

        nameOutlineView.addView(nameEdit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        nameOutlineView.attachEditText(nameEdit);
        boolean hasName = !TextUtils.isEmpty(nameEdit.getText());
        nameOutlineView.animateSelection(false, hasName, false);

        nameEdit.setOnFocusChangeListener((v, hasFocus) -> {
            nameOutlineView.animateSelection(hasFocus, hasFocus || !TextUtils.isEmpty(nameEdit.getText()));
        });

        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                nameOutlineView.animateError(0f);
                nameOutlineView.animateSelection(nameEdit.hasFocus(), nameEdit.hasFocus() || (s != null && s.length() > 0));
            }
        });
        layout.addView(nameOutlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Target input (OutlineTextContainerView + EditTextBoldCursor)
        final EditTextBoldCursor targetEdit = new EditTextBoldCursor(context);
        if (isCustomAction) {
            targetOutlineView.setText("Значение: " + typePlaceholders[selectedType[0]]);

            targetEdit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            targetEdit.setTextColor(textColor);
            targetEdit.setCursorColor(accentColor);
            targetEdit.setBackground(null);
            targetEdit.setPadding(padH, padV, padH, padV);
            targetEdit.setSingleLine(true);
            targetEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);

            if (existing != null && existing.target != null) {
                targetEdit.setText(existing.target);
            }

            targetOutlineView.addView(targetEdit, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            targetOutlineView.attachEditText(targetEdit);
            boolean hasTarget = !TextUtils.isEmpty(targetEdit.getText());
            targetOutlineView.animateSelection(false, hasTarget, false);

            targetEdit.setOnFocusChangeListener((v, hasFocus) -> {
                targetOutlineView.animateSelection(hasFocus, hasFocus || !TextUtils.isEmpty(targetEdit.getText()));
            });

            targetEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void afterTextChanged(Editable s) {
                    targetOutlineView.animateError(0f);
                    targetOutlineView.animateSelection(targetEdit.hasFocus(), targetEdit.hasFocus() || (s != null && s.length() > 0));
                }
            });
            layout.addView(targetOutlineView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
        }

        // Icon picker row
        String initialIcon = (existing != null && existing.iconName != null) ? existing.iconName : "message";
        final String[] selectedIcon = new String[]{initialIcon};

        LinearLayout iconRow = new LinearLayout(context);
        iconRow.setOrientation(LinearLayout.HORIZONTAL);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable iconRowBg = new GradientDrawable();
        iconRowBg.setCornerRadius(dp(12));
        iconRowBg.setColor(Theme.multAlpha(textColor, 0.05f));
        iconRowBg.setStroke(dp(1), Theme.multAlpha(textColor, 0.10f));
        iconRow.setBackground(iconRowBg);
        iconRow.setPadding(dp(14), dp(10), dp(14), dp(10));

        final ImageView iconPreview = new ImageView(context);
        iconPreview.setImageResource(getIconResByName(selectedIcon[0]));
        iconPreview.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN);
        iconRow.addView(iconPreview, LayoutHelper.createLinear(30, 30, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout iconTextCol = new LinearLayout(context);
        iconTextCol.setOrientation(LinearLayout.VERTICAL);

        TextView iconTitle = new TextView(context);
        iconTitle.setText("Иконка кнопки");
        iconTitle.setTypeface(AndroidUtilities.bold());
        iconTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        iconTitle.setTextColor(textColor);
        iconTextCol.addView(iconTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView iconSubtitle = new TextView(context);
        iconSubtitle.setText("Нажмите, чтобы выбрать из каталога иконок...");
        iconSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        iconSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        iconTextCol.addView(iconSubtitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        iconRow.addView(iconTextCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        iconRow.setOnClickListener(v -> {
            showIconPickerGrid(context, selectedIcon[0], chosen -> {
                selectedIcon[0] = chosen;
                iconPreview.setImageResource(getIconResByName(chosen));
            });
        });
        layout.addView(iconRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        // Save & Cancel buttons
        TextView saveButton = new TextView(context);
        saveButton.setText(existing != null ? "Сохранить" : "Добавить");
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setTypeface(AndroidUtilities.bold());
        saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        saveButton.setTextColor(buttonTextColor);
        saveButton.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 10));
        saveButton.setPadding(0, dp(14), 0, dp(14));
        layout.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView cancelButton = new TextView(context);
        cancelButton.setText(LocaleController.getString(R.string.Cancel));
        cancelButton.setGravity(Gravity.CENTER);
        cancelButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        cancelButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        cancelButton.setPadding(0, dp(8), 0, dp(8));
        layout.addView(cancelButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scrollView.addView(layout);
        builder.setCustomView(scrollView);
        final BottomSheet sheet = builder.create();

        saveButton.setOnClickListener(v -> {
            String title = nameEdit.getText().toString().trim();
            if (title.isEmpty()) {
                nameOutlineView.animateError(1f);
                AndroidUtilities.shakeView(nameOutlineView);
                return;
            }

            String target = isCustomAction ? targetEdit.getText().toString().trim() : "";
            if (isCustomAction && target.isEmpty()) {
                targetOutlineView.animateError(1f);
                AndroidUtilities.shakeView(targetOutlineView);
                return;
            }

            String icon = selectedIcon[0];
            String type = isCustomAction ? types[selectedType[0]] : null;
            String id = (existing != null) ? existing.key : ("custom_" + System.currentTimeMillis());

            List<SpeedDialAction> customList = getCustomActions();
            for (int i = 0; i < customList.size(); i++) {
                if (customList.get(i).key.equals(id)) {
                    customList.remove(i);
                    break;
                }
            }
            customList.add(new SpeedDialAction(id, getIconResByName(icon), 0, title, type, target, icon));
            saveCustomActions(customList);

            if (existing == null) {
                String savedItems = AppearanceConfig.md3FabItems();
                if (TextUtils.isEmpty(savedItems)) {
                    savedItems = "chat,channel,group,secret,story,qr,saved";
                }
                AppearanceConfig.md3FabItems.setConfigString(savedItems + "," + id);
            }

            sheet.dismiss();
            if (onDone != null) {
                onDone.run();
            }
        });

        cancelButton.setOnClickListener(v -> sheet.dismiss());
        sheet.show();
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
            int itemStyle = AppearanceConfig.md3FabItemStyle();
            final int cornerRad = (itemStyle == AppearanceConfig.FAB_ITEM_STYLE_CARD) ? dp(12) :
                    (itemStyle == AppearanceConfig.FAB_ITEM_STYLE_FILLED_TONAL) ? dp(16) :
                    (itemStyle == AppearanceConfig.FAB_ITEM_STYLE_OUTLINE) ? dp(20) : rad;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setElevation(dp(3));
                setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRad);
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
            int itemStyle = AppearanceConfig.md3FabItemStyle();
            int themeBg = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
            int themeAccent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            int pressedColor = Theme.getColor(Theme.key_listSelector, resourcesProvider);

            if (itemStyle == AppearanceConfig.FAB_ITEM_STYLE_FILLED_TONAL) {
                int containerBg = ColorUtils.blendARGB(themeBg, themeAccent, 0.28f);
                int onContainer = app.exteraless.utils.UIUtil.getOnAccentColor(containerBg);
                setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16), containerBg, pressedColor));
                iconView.setColorFilter(new PorterDuffColorFilter(onContainer, PorterDuff.Mode.SRC_IN));
                textView.setTextColor(onContainer);
            } else if (itemStyle == AppearanceConfig.FAB_ITEM_STYLE_CARD) {
                int cardBg = ColorUtils.blendARGB(themeBg, themeAccent, 0.10f);
                int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
                setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12), cardBg, pressedColor));
                iconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
                textView.setTextColor(textColor);
            } else if (itemStyle == AppearanceConfig.FAB_ITEM_STYLE_OUTLINE) {
                int glassBg = Theme.multAlpha(themeBg, 0.90f);
                int strokeColor = Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.22f);
                GradientDrawable gd = new GradientDrawable();
                gd.setCornerRadius(dp(20));
                gd.setColor(glassBg);
                gd.setStroke(dp(1.5f), strokeColor);
                setBackground(gd);
                int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
                iconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
                textView.setTextColor(textColor);
            } else {
                // FAB_ITEM_STYLE_CAPSULE
                int bgColor = ColorUtils.blendARGB(themeBg, themeAccent, 0.14f);
                int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
                setBackground(Theme.createSimpleSelectorRoundRectDrawable(rad, bgColor, pressedColor));
                iconView.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
                textView.setTextColor(textColor);
            }
        }
    }
}
