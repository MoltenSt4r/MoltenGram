package app.exteraless.components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

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

        public SpeedDialAction(String key, int iconRes, int titleRes) {
            this.key = key;
            this.iconRes = iconRes;
            this.titleRes = titleRes;
        }

        public String getTitle() {
            return LocaleController.getString(titleRes);
        }
    }

    public static final SpeedDialAction ACTION_CHAT = new SpeedDialAction("chat", R.drawable.filled_fab_compose_32, R.string.NewMessageTitle);
    public static final SpeedDialAction ACTION_CHANNEL = new SpeedDialAction("channel", R.drawable.msg_channel, R.string.NewChannel);
    public static final SpeedDialAction ACTION_GROUP = new SpeedDialAction("group", R.drawable.msg_groups, R.string.NewGroup);
    public static final SpeedDialAction ACTION_SECRET = new SpeedDialAction("secret", R.drawable.msg_secret, R.string.NewSecretChat);
    public static final SpeedDialAction ACTION_STORY = new SpeedDialAction("story", R.drawable.outline_fab_story_24, R.string.StoryPrivacyButtonPost);
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

        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        itemsContainer.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        itemsContainer.setClipChildren(false);
        itemsContainer.setClipToPadding(false);
        addView(itemsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 16, 0, 16, 70));

        setVisibility(GONE);
        rebuildItems();
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void rebuildItems() {
        itemsContainer.removeAllViews();
        itemViews.clear();

        String saved = AppearanceConfig.md3FabItems();
        if (TextUtils.isEmpty(saved)) {
            saved = "chat,channel,group,secret,story,qr,saved";
        }
        List<String> keys = Arrays.asList(saved.split(","));
        List<SpeedDialAction> active = new ArrayList<>();
        for (SpeedDialAction a : ALL_ACTIONS) {
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
                    LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT,
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
        }

        rebuildItems();

        int bottomMargin = dp(70);
        if (mainFab != null) {
            float fabY = mainFab.getY();
            float containerH = getHeight() > 0 ? getHeight() : (activity != null && activity.getFragmentView() != null ? activity.getFragmentView().getHeight() : 0);
            if (containerH > fabY && fabY > 0) {
                bottomMargin = (int) (containerH - fabY + dp(12));
            }
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) itemsContainer.getLayoutParams();
        if (lp != null) {
            lp.bottomMargin = bottomMargin;
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
            item.setPivotX(LocaleController.isRTL ? 0 : item.getWidth());
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

    public static void showCustomizeDialog(Context context, @Nullable Runnable onSaved) {
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.OEAppearanceMd3FabCustomize));

        String saved = AppearanceConfig.md3FabItems();
        if (TextUtils.isEmpty(saved)) {
            saved = "chat,channel,group,secret,story,qr,saved";
        }
        List<String> activeKeys = new ArrayList<>(Arrays.asList(saved.split(",")));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, dp(10), 0, dp(10));

        final CheckBoxCell[] cells = new CheckBoxCell[ALL_ACTIONS.length];
        for (int i = 0; i < ALL_ACTIONS.length; i++) {
            SpeedDialAction action = ALL_ACTIONS[i];
            boolean checked = activeKeys.contains(action.key);
            cells[i] = new CheckBoxCell(context, 1);
            cells[i].setBackground(Theme.getSelectorDrawable(false));
            cells[i].setText(action.getTitle(), "", checked, true);
            final int index = i;
            cells[i].setOnClickListener(v -> cells[index].setChecked(!cells[index].isChecked(), true));
            container.addView(cells[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        }

        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ALL_ACTIONS.length; i++) {
                if (cells[i].isChecked()) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(ALL_ACTIONS[i].key);
                }
            }
            String result = sb.length() > 0 ? sb.toString() : "chat";
            AppearanceConfig.md3FabItems.setConfigString(result);
            if (onSaved != null) {
                onSaved.run();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static String getItemsSummary() {
        String saved = AppearanceConfig.md3FabItems();
        if (TextUtils.isEmpty(saved)) return "";
        List<String> keys = Arrays.asList(saved.split(","));
        StringBuilder sb = new StringBuilder();
        for (SpeedDialAction action : ALL_ACTIONS) {
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

            setOnLongClickListener(v -> {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                close(true);
                showCustomizeDialog(getContext(), Md3SpeedDialView.this::rebuildItems);
                return true;
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
