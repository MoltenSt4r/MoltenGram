package app.exteraless.drawer;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.Locale;

import app.exteraless.appearance.AppearanceConfig;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.DialogsActivity;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * Шапка бокового меню: аватар 72dp, переключатель темы, пилюля прокси, имя и подпись.
 *
 * Отличия от exteraGram:
 * — нет «бейджа exteraGram» ({@code BadgesController}/{@code BadgeDTO}): это его сервер,
 *   поэтому убраны {@code exteraBadgeDrawable}, {@code onBadgeClick} и второй правый drawable;
 * — иконки прокси взяты из NagramX ({@code proxy_on_solar}/{@code proxy_off_solar}) —
 *   {@code drawer_proxy_on/off} из exteraGram в ресурсах форка нет;
 * — подпись пинга собирается вручную: строки {@code NavigationDrawerProxyPingShort}
 *   («%1$d ms») в форке пока нет.
 */
public class DrawerHeaderView extends FrameLayout {

    private static final int COLOR_KEY_TEXT = Theme.key_windowBackgroundWhiteBlackText;
    private static final int COLOR_KEY_SUBTITLE = Theme.key_windowBackgroundWhiteGrayText2;
    private static final int COLOR_KEY_ICON = Theme.key_windowBackgroundWhiteGrayIcon;
    private static final int COLOR_KEY_STATUS = Theme.key_profile_verifiedBackground;

    private static final int PROXY_STATE_HIDDEN = 0;
    private static final int PROXY_STATE_ICON = 1;
    private static final int PROXY_STATE_PING = 2;

    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final BackupImageView avatarView;
    private final SimpleTextView nameView;
    private final SimpleTextView subtitleView;
    private final ImageView chevronView;
    private final FrameLayout themeToggleBg;
    private final RLottieImageView themeToggleView;
    private final RLottieDrawable sunDrawable;
    private final FrameLayout proxyButton;
    private final ImageView proxyIcon;
    private final AnimatedTextView proxyTextView;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable premiumStatusDrawable;

    private boolean chevronExpanded;
    private int lastProxyState = -1;
    private int lastProxyColor = -1;

    private Runnable onChevronClick;
    private Runnable onNavigateToProfile;
    private Runnable onProxyClick;
    private Runnable onStatusClick;
    private Runnable onThemeToggle;
    private Runnable onThemeToggleLongClick;

    public DrawerHeaderView(Context context) {
        super(context);

        avatarView = new BackupImageView(context);
        avatarView.getImageReceiver().setAvatarCornersApplied(true);
        avatarView.setRoundRadius(AppearanceConfig.getAvatarCorners(AndroidUtilities.dp(72)));
        addView(avatarView, LayoutHelper.createFrame(72, 72.0f, Gravity.LEFT | Gravity.TOP, 16.0f, 16.0f, 0.0f, 0.0f));
        avatarView.setOnClickListener(v -> {
            if (onNavigateToProfile != null) {
                onNavigateToProfile.run();
            }
        });

        themeToggleBg = new FrameLayout(context);
        ScaleStateListAnimator.apply(themeToggleBg);
        themeToggleBg.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18.0f), getThemeToggleBackgroundColor()));
        addView(themeToggleBg, LayoutHelper.createFrame(36, 36.0f, Gravity.RIGHT | Gravity.TOP, 0.0f, 16.0f, 16.0f, 0.0f));

        sunDrawable = new RLottieDrawable(R.raw.sun, String.valueOf(R.raw.sun),
                AndroidUtilities.dp(24.0f), AndroidUtilities.dp(24.0f), true, null);
        sunDrawable.setPlayInDirectionOfCustomEndFrame(true);
        themeToggleView = new RLottieImageView(context);
        themeToggleView.setAnimation(sunDrawable);
        themeToggleView.setScaleType(ImageView.ScaleType.CENTER);
        themeToggleBg.addView(themeToggleView, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        setThemeToggleStaticState(Theme.isCurrentThemeDark());
        themeToggleBg.setOnClickListener(v -> {
            resetThemeTogglePressAnimation();
            if (onThemeToggle != null) {
                onThemeToggle.run();
            }
        });
        themeToggleBg.setOnLongClickListener(v -> {
            if (onThemeToggleLongClick == null) {
                return false;
            }
            onThemeToggleLongClick.run();
            return true;
        });
        updateThemeToggleColors();

        proxyButton = new FrameLayout(context);
        ScaleStateListAnimator.apply(proxyButton);
        proxyButton.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18.0f), getThemeToggleBackgroundColor()));
        addView(proxyButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36.0f,
                Gravity.RIGHT | Gravity.TOP, 0.0f, 16.0f, 60.0f, 0.0f));
        proxyButton.setOnClickListener(v -> {
            if (onProxyClick != null) {
                onProxyClick.run();
            }
        });

        final LinearLayout proxyContent = new LinearLayout(context);
        proxyContent.setOrientation(LinearLayout.HORIZONTAL);
        proxyContent.setGravity(Gravity.CENTER);
        proxyContent.setPadding(AndroidUtilities.dp(6.0f), 0, AndroidUtilities.dp(6.0f), 0);
        proxyButton.addView(proxyContent, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        proxyIcon = new ImageView(context);
        proxyIcon.setScaleType(ImageView.ScaleType.CENTER);
        proxyContent.addView(proxyIcon, LayoutHelper.createLinear(24, 24));

        proxyTextView = new AnimatedTextView(context, true, true, true);
        proxyTextView.setTextSize(AndroidUtilities.dp(13.0f));
        proxyTextView.adaptWidth = true;
        proxyTextView.setTypeface(AndroidUtilities.bold());
        proxyTextView.setTextColor(Theme.getColor(COLOR_KEY_ICON));
        proxyTextView.setPadding(AndroidUtilities.dp(2.0f), 0, AndroidUtilities.dp(4.0f), 0);
        proxyTextView.setVisibility(View.GONE);
        proxyContent.addView(proxyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        final FrameLayout nameBlock = new FrameLayout(context);
        addView(nameBlock, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50.0f,
                Gravity.LEFT | Gravity.TOP, 0.0f, 100.0f, 0.0f, 0.0f));
        nameBlock.setOnClickListener(v -> {
            if (onChevronClick != null) {
                onChevronClick.run();
            }
        });

        nameView = new SimpleTextView(context);
        nameView.setTextSize(15);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        nameView.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        nameView.setEllipsizeByGradient(true);
        nameView.setCanHideRightDrawable(false);
        nameView.setRightDrawableOutside(true);
        nameView.setClickable(false);
        nameBlock.addView(nameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24.0f,
                Gravity.LEFT | Gravity.TOP, 16.0f, 0.0f, 64.0f, 0.0f));

        premiumStatusDrawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(nameView, AndroidUtilities.dp(22.0f));
        nameView.setRightDrawable(premiumStatusDrawable);
        nameView.setRightDrawableOnClick(v -> {
            if (onStatusClick != null) {
                onStatusClick.run();
            }
        });

        subtitleView = new SimpleTextView(context);
        subtitleView.setTextSize(12);
        subtitleView.setTextColor(Theme.getColor(COLOR_KEY_SUBTITLE));
        subtitleView.setMaxLines(1);
        subtitleView.setClickable(false);
        nameBlock.addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 16.0f, 26.0f, 64.0f, 0.0f));

        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.msg_expand);
        chevronView.setScaleType(ImageView.ScaleType.CENTER);
        chevronView.setColorFilter(createColorFilter(COLOR_KEY_SUBTITLE));
        nameBlock.addView(chevronView, LayoutHelper.createFrame(24, 24.0f,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0.0f, 0.0f, 22.0f, 0.0f));
    }

    public SimpleTextView getNameView() {
        return nameView;
    }

    public RLottieImageView getThemeToggleView() {
        return themeToggleView;
    }

    /** Центр кнопки в координатах окна. */
    public int[] getThemeTogglePosition() {
        final int[] pos = new int[2];
        themeToggleBg.getLocationInWindow(pos);
        pos[0] += themeToggleBg.getMeasuredWidth() / 2;
        pos[1] += themeToggleBg.getMeasuredHeight() / 2;
        return pos;
    }

    public void setOnChevronClick(Runnable onChevronClick) {
        this.onChevronClick = onChevronClick;
    }

    public void setOnNavigateToProfile(Runnable onNavigateToProfile) {
        this.onNavigateToProfile = onNavigateToProfile;
    }

    public void setOnProxyClick(Runnable onProxyClick) {
        this.onProxyClick = onProxyClick;
    }

    public void setOnStatusClick(Runnable onStatusClick) {
        this.onStatusClick = onStatusClick;
    }

    public void setOnThemeToggle(Runnable onThemeToggle) {
        this.onThemeToggle = onThemeToggle;
    }

    public void setOnThemeToggleLongClick(Runnable onThemeToggleLongClick) {
        this.onThemeToggleLongClick = onThemeToggleLongClick;
    }

    public void setChevronExpanded(boolean expanded) {
        if (chevronExpanded == expanded) {
            return;
        }
        chevronExpanded = expanded;
        chevronView.animate().cancel();
        chevronView.animate().rotation(expanded ? 180.0f : 0.0f)
                .setDuration(250L).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
    }

    public void animateThemeToggle(boolean toDark) {
        syncThemeToggle(toDark, true);
    }

    public void updateColors() {
        nameView.setTextColor(Theme.getColor(COLOR_KEY_TEXT));
        subtitleView.setTextColor(Theme.getColor(COLOR_KEY_SUBTITLE));
        chevronView.setColorFilter(createColorFilter(COLOR_KEY_SUBTITLE));
        themeToggleBg.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18.0f), getThemeToggleBackgroundColor()));
        lastProxyState = -1;
        lastProxyColor = -1;
        updateUserInfo();
        if (!themeToggleView.isPlaying() && !DialogsActivity.switchingTheme) {
            syncThemeToggle(false);
        }
        if (!DialogsActivity.switchingTheme || Theme.isCurrentThemeDark()) {
            updateThemeToggleColors();
        }
    }

    public void updateUserInfo() {
        final int account = UserConfig.selectedAccount;
        final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null) {
            return;
        }
        avatarDrawable.setInfo(account, user);
        avatarView.getImageReceiver().setAvatarCornersApplied(true);
        avatarView.setRoundRadius(AppearanceConfig.getAvatarCorners(AndroidUtilities.dp(72)));
        avatarView.getImageReceiver().setCurrentAccount(account);
        avatarView.setForUserOrChat(user, avatarDrawable);
        nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
        premiumStatusDrawable.setCurrentAccount(account);

        final String username = DialogObject.getPublicUsername(user);
        if (username != null && !username.isEmpty()) {
            subtitleView.setText("@".concat(username));
        } else if (user.phone == null || user.phone.isEmpty()) {
            subtitleView.setText(LocaleController.getString(R.string.NumberUnknown));
        } else if (NekoConfig.hidePhone.Bool()) {
            // Аналог ExteraConfig.getHidePhoneNumber() — у нас NekoConfig.hidePhone.
            subtitleView.setText(LocaleController.getString(R.string.MobileHidden));
        } else {
            subtitleView.setText(PhoneFormat.getInstance().format("+" + user.phone));
        }

        Long userDocId = org.telegram.messenger.UserObject.getEmojiStatusDocumentId(user);
        final long emojiStatusId = userDocId != null ? userDocId : DialogObject.getEmojiStatusDocumentId(user.emoji_status);
        final boolean premium = MessagesController.getInstance(account).isPremiumUser(user);
        final int statusColor = Theme.getColor(COLOR_KEY_STATUS);
        if (emojiStatusId != 0) {
            premiumStatusDrawable.set(emojiStatusId, true);
        } else if (premium) {
            premiumStatusDrawable.set(PremiumGradient.getInstance().premiumStarDrawableMini, true);
        } else if (app.exteraless.chats.CustomEmojiStatusHelper.isEnabled()) {
            try {
                Drawable addIcon = getContext().getResources().getDrawable(R.drawable.msg_mini_addemoji).mutate();
                premiumStatusDrawable.set(addIcon, true);
            } catch (Exception e) {
                premiumStatusDrawable.set((Drawable) null, true);
            }
        } else {
            premiumStatusDrawable.set((Drawable) null, true);
        }
        premiumStatusDrawable.setParticles(DialogObject.isEmojiStatusCollectible(user.emoji_status), true);
        premiumStatusDrawable.setColor(statusColor);
        nameView.setRightDrawable(emojiStatusId != 0 || premium || app.exteraless.chats.CustomEmojiStatusHelper.isEnabled() ? premiumStatusDrawable : null);

        updateProxyStatus();
    }

    /** Три состояния пилюли. */
    public void updateProxyStatus() {
        final boolean proxyEnabled = SharedConfig.isProxyEnabled();
        final int connectionState = ConnectionsManager.getInstance(UserConfig.selectedAccount).getConnectionState();
        final boolean connected = connectionState == ConnectionsManager.ConnectionStateConnected
                || connectionState == ConnectionsManager.ConnectionStateUpdating;

        long ping = 0;
        final int state;
        if (proxyEnabled && SharedConfig.currentProxy != null && connected) {
            ping = Utilities.clamp(SharedConfig.currentProxy.ping, 9999L, 0L);
            state = ping > 0 ? PROXY_STATE_PING : PROXY_STATE_ICON;
        } else if (SharedConfig.proxyList.isEmpty()) {
            state = PROXY_STATE_HIDDEN;
        } else {
            state = PROXY_STATE_ICON;
        }

        if (state != lastProxyState) {
            TransitionManager.beginDelayedTransition(this, new ChangeBounds().setDuration(150L));
            lastProxyState = state;
        }
        if (state == PROXY_STATE_HIDDEN) {
            proxyButton.setVisibility(View.GONE);
            return;
        }
        proxyButton.setVisibility(View.VISIBLE);
        if (state == PROXY_STATE_PING) {
            proxyTextView.setVisibility(View.VISIBLE);
            // exteraGram: LocaleController.formatString(R.string.NavigationDrawerProxyPingShort, ping) — «%1$d ms».
            proxyTextView.setText(String.format(Locale.US, "%d ms", ping), true);
        } else {
            proxyTextView.setVisibility(View.GONE);
        }

        final int color = Theme.getColor(proxyEnabled && connected
                ? Theme.key_windowBackgroundWhiteGreenText : COLOR_KEY_ICON);
        if (color != lastProxyColor) {
            lastProxyColor = color;
            proxyButton.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18.0f), Theme.multAlpha(color, 0.075f)));
            proxyIcon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            proxyTextView.setTextColor(color);
        }
        proxyIcon.setImageResource(proxyEnabled && connected ? R.drawable.proxy_on_solar : R.drawable.proxy_off_solar);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        premiumStatusDrawable.attach();
        if (themeToggleView.isPlaying() || DialogsActivity.switchingTheme) {
            return;
        }
        syncThemeToggle(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        premiumStatusDrawable.detach();
    }

    // ---- переключатель темы ----

    private void resetThemeTogglePressAnimation() {
        themeToggleBg.setPressed(false);
        themeToggleBg.setScaleX(1.0f);
        themeToggleBg.setScaleY(1.0f);
    }

    private int getThemeToggleCurrentFrame(boolean dark) {
        return dark ? sunDrawable.getFramesCount() - 1 : 0;
    }

    private int getThemeToggleEndFrame(boolean dark) {
        return dark ? sunDrawable.getFramesCount() : 0;
    }

    private void setThemeToggleStaticState(boolean dark) {
        sunDrawable.stop();
        sunDrawable.setCurrentFrame(getThemeToggleCurrentFrame(dark));
        sunDrawable.setCustomEndFrame(getThemeToggleEndFrame(dark));
        themeToggleView.invalidate();
    }

    private void syncThemeToggle(boolean animated) {
        syncThemeToggle(Theme.isCurrentThemeDark(), animated);
    }

    private void syncThemeToggle(boolean dark, boolean animated) {
        if (sunDrawable.getFramesCount() <= 0) {
            return;
        }
        final int currentFrame = getThemeToggleCurrentFrame(dark);
        final int endFrame = getThemeToggleEndFrame(dark);
        if (animated) {
            sunDrawable.setCustomEndFrame(endFrame);
            themeToggleView.playAnimation();
            return;
        }
        if (!isAttachedToWindow()) {
            setThemeToggleStaticState(dark);
            return;
        }
        sunDrawable.stop();
        sunDrawable.setCurrentFrame(currentFrame, false, true);
        sunDrawable.setCustomEndFrame(currentFrame);
        themeToggleView.invalidate();
    }

    private void updateThemeToggleColors() {
        applyThemeToggleColors(sunDrawable, Theme.getColor(COLOR_KEY_ICON));
        themeToggleView.setColorFilter(createColorFilter(COLOR_KEY_ICON));
        themeToggleView.invalidate();
    }

    /** Перекраска слоёв sun.json. */
    private void applyThemeToggleColors(RLottieDrawable drawable, int color) {
        drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        drawable.beginApplyLayerColors();
        drawable.setLayerColor("Sunny.**", color);
        drawable.setLayerColor("Path 6.**", color);
        drawable.setLayerColor("Path.**", color);
        drawable.setLayerColor("Path 5.**", color);
        drawable.commitApplyLayerColors();
    }

    private static PorterDuffColorFilter createColorFilter(int colorKey) {
        return new PorterDuffColorFilter(Theme.getColor(colorKey), PorterDuff.Mode.SRC_IN);
    }

    private static int getThemeToggleBackgroundColor() {
        return Theme.multAlpha(Theme.getColor(COLOR_KEY_ICON), 0.075f);
    }
}
