package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class PlayerMiniView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int HEIGHT_DP = 64;

    private final BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private final boolean preview;
    private boolean dark;
    private int seed;
    private final LinearLayout card;
    private final GradientDrawable cardBg = new GradientDrawable();
    private final CoverImage cover;
    private final LinearLayout texts;
    private final TextView titleView;
    private final TextView artistView;
    private final RingPlayButton playButton;
    private final ImageView nextButton;
    private final ImageView closeButton;
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 24);
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 22);
    private PlayerColors colors;
    private org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable cardGlassDrawable;
    private static final java.util.concurrent.CopyOnWriteArrayList<java.lang.ref.WeakReference<PlayerMiniView>> instances = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final app.exteraless.glass.LiquidGlassConfig.Listener glassConfigListener = this::onGlassConfigChanged;

    public void setGlassFactory(org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory factory) {
        if (preview || factory == null) {
            return;
        }
        cardGlassDrawable = factory.create(card, org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl.topPanel(resourcesProvider));
        cardGlassDrawable.setRadius(dp(20));
        cardGlassDrawable.setShadowAlpha(0f);
        cardGlassDrawable.setLiquidGlassEffectAllowed(true);
        applyColors(colors);
    }

    private void onGlassConfigChanged() {
        applyStyle(false);
        notifyOffset();
    }

    public void invalidateGlass() {
        if (cardGlassDrawable != null) {
            cardGlassDrawable.invalidateDisplayList();
            cardGlassDrawable.invalidateSelf();
        }
        if (card != null) {
            card.invalidate();
        }
    }
    private ValueAnimator showAnimator;
    private MessageObject current;
    private String currentKey;
    private boolean shown;
    private float showProgress;
    private float hostFactor = 1f;
    private float baseTranslation;
    private Runnable offsetListener;

    public PlayerMiniView(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        this(context, fragment, resourcesProvider, false);
    }

    private PlayerMiniView(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider, boolean preview) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        this.preview = preview;
        dark = PlayerColors.isDark(resourcesProvider);
        seed = PlayerColors.fallbackSeed(resourcesProvider);
        colors = PlayerColors.fromSeed(seed, dark);
        setClipChildren(false);
        setClipToPadding(false);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), 0, dp(6), 0);
        cardBg.setCornerRadius(dp(20));
        card.setBackground(cardBg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });
        card.setClipToOutline(true);
        card.setElevation(dp(3));
        if (!preview) {
            card.setOnClickListener(v -> {
                if (speedDialMorphProgress >= 0.8f) {
                    if (this.playButton != null) {
                        this.playButton.performClick();
                    }
                } else {
                    Md3Player.open(fragment, this);
                }
            });
        } else {
            card.setClickable(false);
        }
        card.setContentDescription(getString(R.string.OEPlayerOpen));
        addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, HEIGHT_DP, Gravity.BOTTOM, 12, 0, 12, 12));

        cover = new CoverImage(context, 22);
        cover.setRadius(dp(12));
        card.addView(cover, LayoutHelper.createLinear(44, 44));

        texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.headerTypeface());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        artistView.setAlpha(0.8f);
        texts.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        card.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 12, 0, 4, 0));

        playButton = new RingPlayButton(context, 20, 3, 22, 1.3f, 12);
        playButton.setOnClickListener(v -> {
            MediaController mc = MediaController.getInstance();
            MessageObject mo = mc.getPlayingMessageObject();
            if (mo == null || mc.isDownloadingCurrentMessage()) {
                return;
            }
            if (mc.isMessagePaused()) {
                mc.playMessage(mo);
            } else {
                mc.pauseMessage(mo);
            }
        });
        card.addView(playButton, LayoutHelper.createLinear(48, 48));

        nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(nextIcon);
        nextButton.setContentDescription(getString(R.string.Next));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        card.addView(nextButton, LayoutHelper.createLinear(44, 48));

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(closeIcon);
        closeButton.setContentDescription(getString(R.string.AccDescrClosePlayer));
        closeButton.setOnClickListener(v -> MediaController.getInstance().cleanupPlayer(true, true));
        card.addView(closeButton, LayoutHelper.createLinear(44, 48));

        applyColors(colors);
        if (preview) {
            shown = true;
            showProgress = 1f;
            hostFactor = 1f;
            setVisibility(VISIBLE);
            update(false);
        } else {
            setVisibility(GONE);
        }
    }

    public boolean canTransition() {
        return isAttachedToWindow() && getVisibility() == VISIBLE && shown && showProgress >= 1f && hostFactor >= 0.99f && card.getWidth() > 0;
    }

    public void getCardRect(RectF out) {
        locate(card, out);
    }

    public void getCoverRect(RectF out) {
        locate(cover, out);
    }

    private static void locate(View view, RectF out) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        out.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
    }

    public float getCardRadius() {
        return dp(20);
    }

    public float getCoverRadius() {
        return dp(12);
    }

    public int getCardColor() {
        return colors.primaryContainer;
    }

    public Bitmap getCoverBitmap() {
        return cover.getImageReceiver().getBitmap();
    }

    public int getCoverShapeId() {
        return cover != null ? cover.getShapeId() : -1;
    }

    public Bitmap captureCard() {
        int w = card.getWidth();
        int h = card.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int visibility = cover.getVisibility();
        try {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            cover.setVisibility(INVISIBLE);
            card.setPressed(false);
            card.jumpDrawablesToCurrentState();
            card.draw(canvas);
            return bitmap;
        } catch (Throwable e) {
            return null;
        } finally {
            cover.setVisibility(visibility);
        }
    }

    public void setTransitionHidden(boolean hidden) {
        card.setAlpha(hidden ? 0f : 1f);
    }

    public void setOffsetListener(Runnable listener) {
        offsetListener = listener;
    }

    public static PlayerMiniView preview(Context context) {
        return new PlayerMiniView(context, null, null, true);
    }

    public static void styleChanged() {
        for (java.lang.ref.WeakReference<PlayerMiniView> ref : instances) {
            PlayerMiniView v = ref.get();
            if (v == null) {
                instances.remove(ref);
            } else {
                v.applyStyle(false);
                v.notifyOffset();
            }
        }
    }

    public void applyStyle(boolean animated) {
        checkThemeColors();
        if (colors != null) {
            applyColors(colors);
        } else {
            invalidateGlass();
        }
        cover.setShapeId(PlayerArt.getOrCycleMiniShape(current), animated);
        playButton.invalidate();
    }

    public void setTopState(float anchor, float zone, float shift, float visibility) {
    }

    public float getVisibleOffset(boolean chats) {
        return getVisibleOffset();
    }

    public int getTargetOffset(boolean chats) {
        return getTargetOffset();
    }

    public int getTargetTopOffset(boolean chats) {
        return 0;
    }

    public float getVisibleOffset() {
        return (dp(HEIGHT_DP) + dp(8)) * showProgress * hostFactor;
    }

    public int getTargetOffset() {
        return shown && hostFactor > 0.5f ? dp(HEIGHT_DP) + dp(8) : 0;
    }

    public void setHostPosition(float translation, float factor) {
        baseTranslation = translation;
        float old = hostFactor;
        hostFactor = factor;
        applyTransform();
        if (old != factor) {
            notifyOffset();
        }
    }

    private void applyTransform() {
        float p = showProgress * hostFactor;
        setTranslationY(baseTranslation + dp(24) * (1f - p));
        setAlpha(p);
        setVisibility(p > 0f ? VISIBLE : GONE);
    }

    private void notifyOffset() {
        if (offsetListener != null) {
            offsetListener.run();
        }
    }

    private float speedDialMorphProgress;
    private ValueAnimator speedDialMorphAnimator;

    public float getSpeedDialMorphProgress() {
        return speedDialMorphProgress;
    }

    public void setSpeedDialMorphProgress(float progress) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (Math.abs(speedDialMorphProgress - progress) < 0.001f) {
            return;
        }
        speedDialMorphProgress = progress;
        applySpeedDialMorph(progress);
    }

    public void setSpeedDialOpen(boolean open, boolean animated) {
        if (speedDialMorphAnimator != null) {
            speedDialMorphAnimator.cancel();
            speedDialMorphAnimator = null;
        }
        final float target = open ? 1f : 0f;
        if (!animated || !isAttachedToWindow()) {
            setSpeedDialMorphProgress(target);
            return;
        }
        speedDialMorphAnimator = ValueAnimator.ofFloat(speedDialMorphProgress, target);
        speedDialMorphAnimator.setDuration(open ? 240 : 180);
        speedDialMorphAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        speedDialMorphAnimator.addUpdateListener(a -> setSpeedDialMorphProgress((Float) a.getAnimatedValue()));
        speedDialMorphAnimator.start();
    }

    public static void notifySpeedDialMorph(boolean open, boolean animated) {
        for (java.lang.ref.WeakReference<PlayerMiniView> ref : instances) {
            PlayerMiniView v = ref.get();
            if (v != null) {
                v.setSpeedDialOpen(open, animated);
            }
        }
    }

    private void applySpeedDialMorph(float progress) {
        if (card == null) {
            return;
        }
        final int compactSize = dp(HEIGHT_DP);
        int parentW = getMeasuredWidth();
        if (parentW <= 0) {
            parentW = getWidth();
        }
        if (parentW <= 0 && getParent() instanceof View) {
            parentW = ((View) getParent()).getWidth();
        }
        if (parentW <= 0) {
            parentW = AndroidUtilities.displaySize.x;
        }
        final int fullW = Math.max(compactSize, parentW - dp(24));
        final int currentW = progress <= 0f ? FrameLayout.LayoutParams.MATCH_PARENT
                : Math.round(fullW - (fullW - compactSize) * progress);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        if (lp != null) {
            lp.gravity = Gravity.BOTTOM | Gravity.LEFT;
            lp.leftMargin = dp(12);
            lp.rightMargin = progress <= 0f ? dp(12) : 0;
            lp.width = currentW;
            card.setLayoutParams(lp);
        }

        final float contentAlpha = Math.max(0f, 1f - progress * 2f);
        if (cover != null) {
            cover.setAlpha(contentAlpha);
            cover.setScaleX(Math.max(0.01f, 1f - progress * 0.5f));
            cover.setScaleY(Math.max(0.01f, 1f - progress * 0.5f));
            cover.setVisibility(progress >= 0.99f ? GONE : VISIBLE);
        }
        if (texts != null) {
            texts.setAlpha(contentAlpha);
            texts.setVisibility(progress >= 0.99f ? GONE : VISIBLE);
        }
        if (nextButton != null) {
            nextButton.setAlpha(contentAlpha);
            nextButton.setScaleX(Math.max(0.01f, 1f - progress * 0.5f));
            nextButton.setScaleY(Math.max(0.01f, 1f - progress * 0.5f));
            nextButton.setVisibility(progress >= 0.99f ? GONE : VISIBLE);
        }
        if (closeButton != null) {
            closeButton.setAlpha(contentAlpha);
            closeButton.setScaleX(Math.max(0.01f, 1f - progress * 0.5f));
            closeButton.setScaleY(Math.max(0.01f, 1f - progress * 0.5f));
            closeButton.setVisibility(progress >= 0.99f ? GONE : VISIBLE);
        }

        final int padLeft = Math.round(dp(10) * (1f - progress) + dp(8) * progress);
        final int padRight = Math.round(dp(6) * (1f - progress) + dp(8) * progress);
        card.setPadding(padLeft, 0, padRight, 0);
        if (progress >= 0.9f) {
            card.setGravity(Gravity.CENTER);
        } else {
            card.setGravity(Gravity.CENTER_VERTICAL);
        }

        if (playButton != null) {
            playButton.setVisibility(VISIBLE);
            playButton.setAlpha(1f);
        }

        card.setContentDescription(progress >= 0.8f && playButton != null
                ? playButton.getContentDescription()
                : getString(R.string.OEPlayerOpen));

        card.invalidateOutline();
        invalidateGlass();
        card.requestLayout();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (preview) {
            update(false);
            return;
        }
        instances.addIfAbsent(new java.lang.ref.WeakReference<>(this));
        app.exteraless.glass.LiquidGlassConfig.addListener(glassConfigListener);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(this, NotificationCenter.messagePlayingDidReset);
            nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.addObserver(this, NotificationCenter.fileLoaded);
            nc.addObserver(this, NotificationCenter.audioInfoLoaded);
        }
        update(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (preview) {
            return;
        }
        app.exteraless.glass.LiquidGlassConfig.removeListener(glassConfigListener);
        for (java.lang.ref.WeakReference<PlayerMiniView> ref : instances) {
            PlayerMiniView v = ref.get();
            if (v == null || v == this) {
                instances.remove(ref);
            }
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidReset);
            nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.removeObserver(this, NotificationCenter.fileLoaded);
            nc.removeObserver(this, NotificationCenter.audioInfoLoaded);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            playButton.progressChanged();
        } else if (id == NotificationCenter.audioInfoLoaded) {
            MessageObject mo = current;
            if (mo != null && PlayerArt.isPlaying(mo)) {
                if (args != null && args.length > 0 && args[0] instanceof MessageObject
                        && !TextUtils.equals(PlayerArt.key((MessageObject) args[0]), currentKey)) {
                    return;
                }
                cover.setMessage(mo);
            }
        } else if (id == NotificationCenter.fileLoaded) {
            MessageObject mo = current;
            if (mo != null && mo.getDocument() != null && TextUtils.equals((String) args[0], FileLoader.getAttachFileName(mo.getDocument()))) {
                cover.setMessage(mo);
            }
        } else {
            update(true);
        }
    }

    private void checkThemeColors() {
        boolean themeDark = PlayerColors.isDark(resourcesProvider);
        int themeSeed = PlayerColors.fallbackSeed(resourcesProvider);
        if (themeDark != dark || themeSeed != seed) {
            dark = themeDark;
            seed = themeSeed;
            applyColors(PlayerColors.fromSeed(seed, dark));
        }
    }

    public void update(boolean animated) {
        checkThemeColors();
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        if (preview) {
            if (mo != null && mo.isMusic() && mo.getId() != 0) {
                bind(mo, animated);
            } else {
                current = null;
                currentKey = null;
                titleView.setText(getString(R.string.OEPlayerPreviewTitle));
                artistView.setText(getString(R.string.OEPlayerPreviewArtist));
                cover.setShapeId(PlayerArt.getOrCycleMiniShape(null), animated);
                cover.setMessage(null);
            }
            playButton.setPlaying(mo == null || !MediaController.getInstance().isMessagePaused(), animated);
            return;
        }
        boolean want = Md3Player.miniEnabled() && mo != null && mo.isMusic() && mo.getId() != 0;
        if (want) {
            bind(mo, animated && shown);
        }
        playButton.setPlaying(want && !MediaController.getInstance().isMessagePaused(), animated);
        setShown(want, animated);
        if (speedDialMorphProgress > 0f) {
            applySpeedDialMorph(speedDialMorphProgress);
        }
    }

    private void bind(MessageObject mo, boolean animated) {
        String key = PlayerArt.key(mo);
        boolean changed = !TextUtils.equals(key, currentKey);
        current = mo;
        currentKey = key;
        titleView.setText(mo.getMusicTitle());
        artistView.setText(mo.getMusicAuthor());
        cover.setShapeId(PlayerArt.getOrCycleMiniShape(mo), animated && changed);
        cover.setMessage(mo);
        playButton.setMessage(mo);
        playButton.setContentDescription(getString(MediaController.getInstance().isMessagePaused() ? R.string.AccActionPlay : R.string.AccActionPause));
    }

    private void setShown(boolean value, boolean animated) {
        if (shown == value && (showAnimator != null || showProgress == (value ? 1f : 0f))) {
            return;
        }
        shown = value;
        if (showAnimator != null) {
            showAnimator.cancel();
            showAnimator = null;
        }
        float target = value ? 1f : 0f;
        if (!animated || !isAttachedToWindow()) {
            showProgress = target;
            applyTransform();
            notifyOffset();
            return;
        }
        showAnimator = ValueAnimator.ofFloat(showProgress, target);
        showAnimator.addUpdateListener(a -> {
            showProgress = (float) a.getAnimatedValue();
            applyTransform();
            notifyOffset();
        });
        showAnimator.setDuration(value ? 450 : 300);
        showAnimator.setInterpolator(value ? new CubicBezierInterpolator(0.05, 0.7, 0.1, 1) : new CubicBezierInterpolator(0.3, 0, 0.8, 0.15));
        showAnimator.start();
    }

    private void applyColors(PlayerColors c) {
        if (c == null) {
            return;
        }
        colors = c;
        boolean glass = app.exteraless.glass.LiquidGlassConfig.isLiquidGlassEnabled()
                && app.exteraless.glass.LiquidGlassConfig.glassMiniPlayer;
        if (glass && cardGlassDrawable != null) {
            cardGlassDrawable.setRadius(dp(20));
            cardGlassDrawable.setShadowAlpha(0f);
            cardGlassDrawable.setLiquidGlassEffectAllowed(true);
            cardGlassDrawable.updateColors();
            cardGlassDrawable.invalidateDisplayList();
            card.setBackground(cardGlassDrawable);
            card.setClipToOutline(true);
            card.setElevation(0);
            card.invalidateOutline();
            card.invalidate();
        } else if (glass) {
            int fallbackBg = androidx.core.graphics.ColorUtils.setAlphaComponent(dark ? 0xFF1E2025 : 0xFFF2F4F8, 0xCC);
            cardBg.setColor(app.exteraless.glass.SolegramGlass.tint(fallbackBg, resourcesProvider));
            cardBg.setStroke(dp(1), dark ? 0x33FFFFFF : 0x55FFFFFF);
            card.setBackground(cardBg);
            card.setClipToOutline(true);
            card.setElevation(dp(3));
        } else {
            cardBg.setStroke(0, 0);
            cardBg.setColor(c.primaryContainer);
            card.setBackground(cardBg);
            card.setClipToOutline(true);
            card.setElevation(dp(3));
        }
        if (Build.VERSION.SDK_INT >= 28) {
            int neutralShadow = 0x22000000;
            card.setOutlineSpotShadowColor(neutralShadow);
            card.setOutlineAmbientShadowColor(neutralShadow);
        }
        int fg = glass ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider) : c.onPrimaryContainer;
        int subFg = glass ? ColorUtils.setAlphaComponent(fg, 0xBF) : c.onPrimaryContainer;
        cover.setColors(c.secondaryContainer, c.onSecondaryContainer);
        titleView.setTextColor(fg);
        artistView.setTextColor(subFg);
        nextIcon.setColor(fg);
        closeIcon.setColor(fg);
        int ripple = ColorUtils.setAlphaComponent(fg, 0x1f);
        nextButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        closeButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playButton.setBackground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_CIRCLE_20DP));
        playButton.setColors(fg, ColorUtils.setAlphaComponent(fg, 46), fg);
        card.setForeground(Theme.createSelectorDrawable(ripple, Theme.RIPPLE_MASK_ALL));
    }
}
