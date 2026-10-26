package app.exteraless.drawer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.RoundedCorner;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.math.MathUtils;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.utils.AppUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.MainTabsActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.ProxyListActivity;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.ui.ThemeActivity;

import xyz.nextalone.nagram.NaConfig;

/**
 * Собственная шторка бокового меню exteraGram: панель шириной min(300dp, экран − 56dp),
 * жесты от левого края, затемнение и сдвиг контента.
 *
 * exteraGram: {@code com/exteragram/messenger/drawer/DrawerContainer.java} (1655 строк).
 * Включается флагом {@link AppearanceConfig#navigationDrawer()}; при выключенном флаге
 * {@code LaunchActivity} даже не создаёт этот вид, а {@link #handleEdgeSwipeIntercept}
 * и {@link #openDrawer} выходят сразу — поведение остаётся стоковым.
 *
 * Что не перенесено (и почему):
 * — «бейдж exteraGram» ({@code showBadgeSelect}, {@code BadgesController}) — сервер exteraGram;
 * — превью аккаунта по долгому нажатию ({@code showAccountPreview}): у нашего
 *   {@code MainTabsActivity} нет конструктора с {@code Bundle} и режима {@code drawer_account_preview},
 *   а {@code DrawerLayoutContainer} не рисует превью поверх шторки. Долгое нажатие на чужой
 *   аккаунт переключает на него, как тап;
 * — наблюдатели {@code pluginMenuItemsUpdated} и {@code proxyPingUpdated}: таких уведомлений
 *   в форке нет.
 */
public class DrawerContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private static final int COLOR_KEY_DRAWER_BACKGROUND = Theme.key_windowBackgroundWhite;
    private static final int COLOR_KEY_POPUP_ACCENT = Theme.key_windowBackgroundWhiteBlueIcon;

    private static final FloatPropertyCompat<DrawerContainer> DRAWER_OFFSET =
            new FloatPropertyCompat<>("drawerOffset") {
                @Override
                public float getValue(DrawerContainer container) {
                    return container.getDrawerOffset();
                }

                @Override
                public void setValue(DrawerContainer container, float value) {
                    container.setDrawerOffset(value);
                }
            };

    private final FrameLayout drawerPanel;
    private final FrameLayout bulletinContainer;
    private final DrawerHeaderView headerView;
    private final DrawerAccountPickerView accountPickerView;
    private final DrawerMenuView menuView;

    private final Paint scrimPaint = new Paint();
    private final Rect rect = new Rect();
    private final Path clipPath = new Path();
    private final float[] radii = new float[8];

    private float cachedTopRightRadius = -1.0f;
    private float cachedBottomRightRadius = -1.0f;

    private int drawerWidth;
    private float progress;
    private boolean isOpen;
    private boolean isAnimating;
    private boolean tracking;
    private boolean startedEdgeSwipe;
    private boolean tapClosePending;
    private boolean notificationsRegistered;
    private boolean predictiveBackInProgress;
    private float predictiveBackStartProgress;
    private float startX;
    private float startY;
    private float startProgress;

    private View navigationTranslationTarget;
    private SpringAnimation springAnimation;
    private ValueAnimator standardAnimator;
    private VelocityTracker velocityTracker;
    private SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow selectAnimatedEmojiDialog;

    public DrawerContainer(Context context) {
        super(context);
        setVisibility(View.GONE);
        // Тег важен: DrawerLayoutContainer.dispatchApplyWindowInsetsInternal пропускает
        // только детей без тега, шторка вставки себе не двигает.
        setTag("drawer_container");

        drawerWidth = calculateDrawerWidth();

        drawerPanel = new FrameLayout(context);
        drawerPanel.setBackgroundColor(Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND));
        drawerPanel.setTranslationX(-drawerWidth);
        addView(drawerPanel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT));
        final FrameLayout.LayoutParams panelParams = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        panelParams.width = drawerWidth;
        drawerPanel.setLayoutParams(panelParams);

        final LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        bulletinContainer = new FrameLayout(context);
        drawerPanel.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        headerView = new DrawerHeaderView(context);
        content.addView(headerView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(160.0f)));

        accountPickerView = new DrawerAccountPickerView(context);
        content.addView(accountPickerView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        menuView = new DrawerMenuView(context);
        final LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0);
        menuParams.weight = 1.0f;
        content.addView(menuView, menuParams);

        setupCallbacks();
        headerView.setChevronExpanded(accountPickerView.isExpanded());
    }

    // ---- публичный API ----

    public boolean isDrawerOpen() {
        return progress > 0.001f || isAnimating || predictiveBackInProgress;
    }

    /** При выключенном флаге сразу закрывается. */
    public void openDrawer(boolean animated) {
        if (!AppearanceConfig.navigationDrawer()) {
            onCloseComplete();
            return;
        }
        if (progress >= 0.999f && !isAnimating) {
            isOpen = true;
            setProgress(1.0f);
            return;
        }
        isOpen = true;
        updateDrawerWidth();
        super.setVisibility(View.VISIBLE);
        applyDrawerPanelPadding();
        refreshContents();
        if (animated) {
            animateProgress(1.0f);
        } else {
            setProgress(1.0f);
        }
    }

    public void closeDrawer(boolean animated) {
        if (progress <= 0.001f && !isAnimating) {
            isOpen = false;
            onCloseComplete();
            return;
        }
        isOpen = false;
        if (animated) {
            animateProgress(0.0f);
        } else {
            setProgress(0.0f);
            onCloseComplete();
        }
    }

    public void toggleDrawer() {
        if (isDrawerOpen()) {
            closeDrawer(true);
        } else {
            openDrawer(true);
        }
    }

    public void onAccountChanged() {
        refreshContents();
    }

    /** Зовётся из {@code setDrawerContainer(null)}. */
    public void dispose() {
        cancelAnimations();
        onCloseComplete();
        recycleVelocityTracker();
        accountPickerView.dispose();
        unregisterNotifications();
        resetNavigationTranslationTarget();
    }

    // ---- предиктивное «назад» ----

    public boolean startPredictiveBack() {
        if (predictiveBackInProgress || tracking || startedEdgeSwipe || getVisibility() != View.VISIBLE) {
            return false;
        }
        if (isAnimating) {
            cancelAnimations();
        }
        if (progress <= 0.001f) {
            return false;
        }
        predictiveBackInProgress = true;
        predictiveBackStartProgress = progress;
        tapClosePending = false;
        super.setVisibility(View.VISIBLE);
        return true;
    }

    public void updatePredictiveBackProgress(float backProgress) {
        if (!predictiveBackInProgress) {
            return;
        }
        setProgress(predictiveBackStartProgress * (1.0f - (Math.max(0.0f, Math.min(1.0f, backProgress)) * 0.5f)));
    }

    public void cancelPredictiveBack() {
        if (!predictiveBackInProgress) {
            return;
        }
        predictiveBackInProgress = false;
        isOpen = predictiveBackStartProgress > 0.001f;
        animateProgress(predictiveBackStartProgress, true, 0.0f);
    }

    public void commitPredictiveBack() {
        if (!predictiveBackInProgress) {
            closeDrawer(true);
            return;
        }
        predictiveBackInProgress = false;
        isOpen = false;
        if (progress <= 0.001f) {
            onCloseComplete();
        } else {
            animateProgress(0.0f, true, 0.0f);
        }
    }

    // ---- жесты от края экрана (зовёт DrawerLayoutContainer) ----

    public boolean handleEdgeSwipeIntercept(MotionEvent ev) {
        if (!AppearanceConfig.navigationDrawer()) {
            return false;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
            startY = ev.getY();
            startProgress = progress;
            startedEdgeSwipe = false;
            tracking = false;
            if (canStartClosedDrawerSwipe(ev)) {
                startedEdgeSwipe = true;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.clear();
                velocityTracker.addMovement(ev);
            }
            return false;
        }
        if (startedEdgeSwipe) {
            if (velocityTracker != null) {
                velocityTracker.addMovement(ev);
            }
            if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                final float dx = ev.getX() - startX;
                final float dy = ev.getY() - startY;
                if (shouldBlockClosedDrawerSwipe(dx, dy)) {
                    startedEdgeSwipe = false;
                    return false;
                }
                if (shouldStartClosedDrawerTracking(dx, Math.abs(dy))) {
                    beginClosedDrawerTracking(ev, dx);
                    return true;
                }
            }
            if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
                startedEdgeSwipe = false;
            }
        }
        return false;
    }

    public boolean handleEdgeSwipeTouch(MotionEvent ev) {
        if (!AppearanceConfig.navigationDrawer()) {
            return false;
        }
        if (!startedEdgeSwipe && !tracking) {
            return false;
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);
        final int action = ev.getAction();
        if (action == MotionEvent.ACTION_MOVE) {
            if (tracking) {
                setProgress(Math.max(0.0f, Math.min(1.0f, startProgress + ((ev.getX() - startX) / drawerWidth))));
            }
            return true;
        }
        if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            return true;
        }
        if (tracking) {
            finishTracking();
        }
        startedEdgeSwipe = false;
        return true;
    }

    // ---- собственные касания ----

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (isClosingAnimationInProgress()) {
            return !shouldPassClosingTouchThrough(ev);
        }
        if (ev.getAction() != MotionEvent.ACTION_DOWN) {
            if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                final float dx = ev.getX() - startX;
                if (shouldStartVisibleDrawerTracking(dx, Math.abs(ev.getY() - startY))) {
                    beginVisibleDrawerTracking(ev, dx);
                    return true;
                }
            }
            return false;
        }
        if (isAnimating) {
            cancelAnimations();
        }
        startX = ev.getX();
        startY = ev.getY();
        startProgress = progress;
        tracking = false;
        final float panelRight = drawerPanel.getTranslationX() + drawerWidth;
        tapClosePending = ev.getX() > panelRight;
        return tapClosePending;
    }

    /**
     * Ветки ACTION_DOWN/MOVE/UP/CANCEL и {@code tapClosePending} — тап по затемнённой
     * области справа от панели закрывает её.
     */
    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent ev) {
        if (isClosingAnimationInProgress()) {
            return !shouldPassClosingTouchThrough(ev);
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        final int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            if (isAnimating) {
                cancelAnimations();
            }
            startX = ev.getX();
            startY = ev.getY();
            startProgress = progress;
            tracking = false;
            final float panelRight = drawerPanel.getTranslationX() + drawerWidth;
            tapClosePending = ev.getX() > panelRight;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            if (!tracking) {
                final float dx = ev.getX() - startX;
                if (shouldStartVisibleDrawerTracking(dx, Math.abs(ev.getY() - startY))) {
                    beginVisibleDrawerTracking(ev, dx);
                }
            }
            if (tracking) {
                setProgress(Math.max(0.0f, Math.min(1.0f, startProgress + ((ev.getX() - startX) / drawerWidth))));
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (tracking) {
                finishTracking();
                return true;
            }
            if (action == MotionEvent.ACTION_UP && tapClosePending) {
                tapClosePending = false;
                closeDrawer(true);
                return true;
            }
            tapClosePending = false;
        }
        return true;
    }

    // ---- отрисовка ----

    /** Скрим 102/255 или 160/255 в иммерсиве. */
    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (progress <= 0.0f) {
            super.dispatchDraw(canvas);
            return;
        }
        final float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        final int alpha;
        final int red;
        final int green;
        final int blue;
        if (!AppearanceConfig.immersiveDrawerAnimation() || AndroidUtilities.isTablet()) {
            alpha = (int) (clamped * 102.0f);
            red = 0;
            green = 0;
            blue = 0;
        } else {
            alpha = (int) (clamped * 160.0f);
            final int color = Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND);
            red = Color.red(color);
            green = Color.green(color);
            blue = Color.blue(color);
        }
        scrimPaint.setColor(Color.argb(alpha, red, green, blue));
        canvas.drawRect(0.0f, 0.0f, getWidth(), getHeight(), scrimPaint);
        super.dispatchDraw(canvas);
    }

    /** В неиммерсивном режиме правый край скруглён. */
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child != drawerPanel || AppearanceConfig.immersiveDrawerAnimation()) {
            return super.drawChild(canvas, child, drawingTime);
        }
        final float topRight = cachedTopRightRadius < 0.0f ? AndroidUtilities.dp(24.0f) : cachedTopRightRadius;
        final float bottomRight = cachedBottomRightRadius < 0.0f ? AndroidUtilities.dp(24.0f) : cachedBottomRightRadius;
        radii[0] = 0.0f;
        radii[1] = 0.0f;
        radii[2] = topRight;
        radii[3] = topRight;
        radii[4] = bottomRight;
        radii[5] = bottomRight;
        radii[6] = 0.0f;
        radii[7] = 0.0f;
        final RectF bounds = AndroidUtilities.rectTmp;
        bounds.set(child.getX(), child.getY(), child.getX() + child.getWidth(), child.getY() + child.getHeight());
        clipPath.rewind();
        clipPath.addRoundRect(bounds, radii, Path.Direction.CW);
        final int save = canvas.save();
        canvas.clipPath(clipPath);
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);
        return result;
    }

    /** Системные радиусы углов, API 31+. */
    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (!AppearanceConfig.immersiveDrawerAnimation()) {
            final float base = AndroidUtilities.dp(24.0f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                final RoundedCorner topRight = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT);
                final RoundedCorner bottomRight = insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT);
                cachedTopRightRadius = topRight != null ? Math.max(base, topRight.getRadius() / 2.0f) : base;
                cachedBottomRightRadius = bottomRight != null ? Math.max(base, bottomRight.getRadius() / 2.0f) : base;
            } else {
                cachedTopRightRadius = base;
                cachedBottomRightRadius = base;
            }
        }
        return super.onApplyWindowInsets(insets);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateDrawerWidth();
        setProgress(progress);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerNotifications();
        Bulletin.addDelegate(bulletinContainer, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return AndroidUtilities.navigationBarHeight;
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        Bulletin.removeDelegate(bulletinContainer);
        cancelAnimations();
        onCloseComplete();
        dismissSelectionPopup();
        recycleVelocityTracker();
        accountPickerView.dispose();
        resetNavigationTranslationTarget();
        unregisterNotifications();
    }

    // ---- анимация ----

    private void animateProgress(float target) {
        animateProgress(target, false, 0.0f);
    }

    private void animateProgress(float target, boolean fast, float startVelocity) {
        cancelAnimations();
        isAnimating = true;
        final float targetOffset = drawerWidth * target;
        if (useSpringAnimations()) {
            final SpringAnimation animation = new SpringAnimation(this, DRAWER_OFFSET);
            springAnimation = animation;
            animation.setSpring(new SpringForce(targetOffset)
                    .setStiffness(fast ? 1500.0f : 950.0f)
                    .setDampingRatio(1.0f));
            if (startVelocity != 0.0f) {
                animation.setStartVelocity(startVelocity);
            }
            animation.addEndListener((DynamicAnimation animation1, boolean canceled, float value, float velocity) -> {
                if (springAnimation == animation1) {
                    springAnimation = null;
                }
                if (canceled) {
                    return;
                }
                isAnimating = false;
                setDrawerOffset(targetOffset);
                if (target == 0.0f) {
                    onCloseComplete();
                }
            });
            animation.animateToFinalPosition(targetOffset);
            return;
        }
        final ValueAnimator animator = ValueAnimator.ofFloat(getDrawerOffset(), targetOffset);
        standardAnimator = animator;
        animator.setDuration(getAnimationDuration(targetOffset, fast));
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addUpdateListener(a -> setDrawerOffset((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (standardAnimator == animation) {
                    standardAnimator = null;
                }
                if (canceled) {
                    return;
                }
                isAnimating = false;
                setDrawerOffset(targetOffset);
                if (target == 0.0f) {
                    onCloseComplete();
                }
            }
        });
        animator.start();
    }

    /**
     * Аналог {@code ExteraConfig.getSpringAnimations()} (дефолт true) у нас живёт
     * в NagramX как стиль анимации «назад» (см. {@code UtilsConfig.applyMotionDefaults}).
     */
    private static boolean useSpringAnimations() {
        try {
            return NaConfig.INSTANCE.getBackAnimationStyle().Int() == ActionBarLayout.BACK_ANIMATION_SPRING;
        } catch (Exception e) {
            return false;
        }
    }

    private long getAnimationDuration(float target, boolean fast) {
        if (!fast) {
            return 300L;
        }
        float distance = getDrawerOffset();
        if (target > distance) {
            distance = drawerWidth - distance;
        }
        return Math.max((long) ((250.0f / Math.max(drawerWidth, 1)) * distance), 100L);
    }

    private void cancelAnimations() {
        if (springAnimation != null) {
            final SpringAnimation animation = springAnimation;
            springAnimation = null;
            animation.cancel();
        }
        if (standardAnimator != null) {
            final ValueAnimator animator = standardAnimator;
            standardAnimator = null;
            animator.cancel();
        }
        isAnimating = false;
        setProgress(progress);
        if (isOpen || progress > 0.001f || tracking || startedEdgeSwipe) {
            return;
        }
        onCloseComplete();
    }

    private float getDrawerOffset() {
        return drawerWidth * progress;
    }

    private void setDrawerOffset(float offset) {
        final float clamped = Math.max(0.0f, Math.min(drawerWidth, offset));
        setProgress(drawerWidth != 0 ? clamped / drawerWidth : 0.0f);
    }

    private void setProgress(float value) {
        progress = Math.max(0.0f, Math.min(1.0f, value));
        syncDrawerState();
        invalidate();
    }

    private void syncDrawerState() {
        if (progress <= 0.001f && !isAnimating && !tracking && !startedEdgeSwipe && !predictiveBackInProgress) {
            applyClosedState();
            return;
        }
        drawerPanel.setTranslationX((-drawerWidth) * (1.0f - progress));
        translateNavigationLayout(progress <= 0.001f ? 0.0f : getNavigationLayoutTranslation(progress));
        if (getVisibility() != View.VISIBLE) {
            super.setVisibility(View.VISIBLE);
        }
    }

    private void applyClosedState() {
        progress = 0.0f;
        drawerPanel.setTranslationX(-drawerWidth);
        translateNavigationLayout(0.0f);
        if (getVisibility() != View.GONE) {
            super.setVisibility(View.GONE);
        }
        tapClosePending = false;
    }

    private void onCloseComplete() {
        isOpen = false;
        tracking = false;
        startedEdgeSwipe = false;
        predictiveBackInProgress = false;
        predictiveBackStartProgress = 0.0f;
        setProgress(0.0f);
        tapClosePending = false;
        dismissSelectionPopup();
        menuView.clearMenu();
    }

    /** Иммерсивный режим уводит контент на всю ширину панели, обычный — на 30 %. */
    private float getNavigationLayoutTranslation(float progress) {
        if (AppearanceConfig.immersiveDrawerAnimation()) {
            return drawerWidth * progress;
        }
        return drawerWidth * progress * 0.3f;
    }

    private void translateNavigationLayout(float translationX) {
        final ViewParent parent = getParent();
        if (!(parent instanceof DrawerLayoutContainer)) {
            resetNavigationTranslationTarget();
            return;
        }
        final DrawerLayoutContainer layoutContainer = (DrawerLayoutContainer) parent;
        final INavigationLayout navigationLayout = layoutContainer.getParentActionBarLayout();
        if (navigationLayout == null) {
            resetNavigationTranslationTarget();
            return;
        }
        final View target = resolveNavigationTranslationTarget(layoutContainer, navigationLayout);
        if (navigationTranslationTarget != null && navigationTranslationTarget != target) {
            navigationTranslationTarget.setTranslationX(0.0f);
        }
        navigationTranslationTarget = target;
        if (target != null) {
            target.setTranslationX(translationX);
        }
    }

    private View resolveNavigationTranslationTarget(DrawerLayoutContainer layoutContainer, INavigationLayout navigationLayout) {
        final ViewGroup view = navigationLayout.getView();
        final ViewParent parent = view.getParent();
        if (parent instanceof View && ((View) parent).getParent() == layoutContainer) {
            return (View) parent;
        }
        return view;
    }

    private void resetNavigationTranslationTarget() {
        if (navigationTranslationTarget != null) {
            navigationTranslationTarget.setTranslationX(0.0f);
            navigationTranslationTarget = null;
        }
    }

    // ---- геометрия и жесты ----

    private int calculateDrawerWidth() {
        return Math.min(AndroidUtilities.dp(300.0f), AndroidUtilities.displaySize.x - AndroidUtilities.dp(56.0f));
    }

    private void updateDrawerWidth() {
        drawerWidth = calculateDrawerWidth();
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        lp.width = drawerWidth;
        drawerPanel.setLayoutParams(lp);
    }

    private void applyDrawerPanelPadding() {
        drawerPanel.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
    }

    private boolean isClosingAnimationInProgress() {
        return isAnimating && !isOpen;
    }

    private boolean shouldPassClosingTouchThrough(MotionEvent ev) {
        return ev != null && ev.getAction() == MotionEvent.ACTION_DOWN
                && ev.getX() > drawerPanel.getTranslationX() + drawerWidth;
    }

    private float getDrawerOpenTouchSlop() {
        return AndroidUtilities.getPixelsInCM(0.2f, true);
    }

    private float getDrawerCloseTouchSlop() {
        return AndroidUtilities.getPixelsInCM(0.4f, true);
    }

    private boolean shouldBlockClosedDrawerSwipe(float dx, float dy) {
        final float absDy = Math.abs(dy);
        float slop = AndroidUtilities.touchSlop;
        if (slop <= 0.0f) {
            slop = getDrawerOpenTouchSlop();
        }
        return absDy >= slop && absDy > Math.abs(dx);
    }

    private boolean shouldStartClosedDrawerTracking(float dx, float absDy) {
        return dx > 0.0f && dx / 3.0f > absDy && Math.abs(dx) >= getDrawerOpenTouchSlop();
    }

    private boolean shouldStartVisibleDrawerTracking(float dx, float absDy) {
        if (dx < 0.0f) {
            return Math.abs(dx) >= absDy && Math.abs(dx) >= getDrawerCloseTouchSlop();
        }
        return startProgress < 0.999f && dx > 0.0f && dx / 3.0f > absDy && dx >= getDrawerOpenTouchSlop();
    }

    private void beginClosedDrawerTracking(MotionEvent ev, float dx) {
        tracking = true;
        tapClosePending = false;
        if (isAnimating) {
            cancelAnimations();
        }
        super.setVisibility(View.VISIBLE);
        applyDrawerPanelPadding();
        refreshContents();
        offsetTrackingStart(ev, dx);
        resetTrackingVelocity(ev);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void beginVisibleDrawerTracking(MotionEvent ev, float dx) {
        tracking = true;
        tapClosePending = false;
        if (isAnimating) {
            cancelAnimations();
        }
        offsetTrackingStart(ev, dx);
        resetTrackingVelocity(ev);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    private void offsetTrackingStart(MotionEvent ev, float dx) {
        startX += Math.signum(dx) * (dx < 0.0f ? getDrawerCloseTouchSlop() : getDrawerOpenTouchSlop());
        startY = ev.getY();
        startProgress = progress;
    }

    private void resetTrackingVelocity(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(ev);
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    /** Порог 1/5 при открытии и 1/1.25 при закрытии. */
    private void finishTracking() {
        float xVelocity = 0.0f;
        float yVelocity = 0.0f;
        if (velocityTracker != null) {
            velocityTracker.computeCurrentVelocity(1000);
            xVelocity = velocityTracker.getXVelocity();
            yVelocity = velocityTracker.getYVelocity();
        }
        final int swipeVelocity = AppUtils.getSwipeVelocity();
        final boolean shouldOpen =
                (progress >= 1.0f / (isOpen ? 1.25f : 5.0f)
                        || (xVelocity >= swipeVelocity && Math.abs(xVelocity) >= Math.abs(yVelocity)))
                        && (xVelocity >= 0.0f || Math.abs(xVelocity) < swipeVelocity);
        if (shouldOpen) {
            final boolean fast = !isOpen && Math.abs(xVelocity) >= swipeVelocity;
            isOpen = true;
            animateProgress(1.0f, fast, xVelocity);
        } else {
            final boolean fast = isOpen && Math.abs(xVelocity) >= swipeVelocity;
            isOpen = false;
            animateProgress(0.0f, fast, xVelocity);
        }
        recycleVelocityTracker();
        tracking = false;
        startedEdgeSwipe = false;
        tapClosePending = false;
    }

    /** Свайп только с «чистого» экрана. */
    private boolean canStartClosedDrawerSwipe(MotionEvent ev) {
        if (!canOpen(ev)) {
            return false;
        }
        final ViewParent parent = getParent();
        if (!(parent instanceof DrawerLayoutContainer)) {
            return false;
        }
        final INavigationLayout navigationLayout = ((DrawerLayoutContainer) parent).getParentActionBarLayout();
        if (navigationLayout == null || navigationLayout.getFragmentStack().size() != 1 || !navigationLayout.allowSwipe()) {
            return false;
        }
        final BaseFragment lastFragment = navigationLayout.getLastFragment();
        if (lastFragment != null && lastFragment.getLastSheet() != null && lastFragment.getLastSheet().attachedToParent()) {
            return false;
        }
        final ViewGroup view = navigationLayout.getView();
        if (view == null) {
            return false;
        }
        view.getHitRect(rect);
        return rect.contains((int) ev.getX(), (int) ev.getY())
                && findScrollingChild(view, ev.getX() - rect.left, ev.getY() - rect.top) == null;
    }

    private boolean canOpen(MotionEvent ev) {
        final BaseFragment lastFragment = getLastFragment();
        if (lastFragment instanceof DialogsActivity) {
            return ((DialogsActivity) lastFragment).canOpenDrawerBySwipe(ev);
        }
        return false;
    }

    private View findScrollingChild(ViewGroup group, float x, float y) {
        for (int i = 0; i < group.getChildCount(); i++) {
            final View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (!rect.contains((int) x, (int) y)) {
                continue;
            }
            if (child.canScrollHorizontally(-1)) {
                return child;
            }
            if (child instanceof ViewGroup) {
                final View found = findScrollingChild((ViewGroup) child, x - rect.left, y - rect.top);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private BaseFragment getLastFragment() {
        final ViewParent parent = getParent();
        if (!(parent instanceof DrawerLayoutContainer)) {
            return null;
        }
        final INavigationLayout navigationLayout = ((DrawerLayoutContainer) parent).getParentActionBarLayout();
        if (navigationLayout == null) {
            return null;
        }
        final BaseFragment lastFragment = navigationLayout.getLastFragment();
        return lastFragment instanceof MainTabsActivity
                ? ((MainTabsActivity) lastFragment).getCurrentVisibleFragment() : lastFragment;
    }

    // ---- содержимое ----

    private void refreshContents() {
        headerView.updateUserInfo();
        accountPickerView.loadAccounts();
        final BaseFragment lastFragment = getLastFragment();
        if (lastFragment != null) {
            menuView.rebuildMenu(UserConfig.selectedAccount, lastFragment);
        } else {
            menuView.clearMenu();
        }
    }

    private void updateColors() {
        drawerPanel.setBackgroundColor(Theme.getColor(COLOR_KEY_DRAWER_BACKGROUND));
        headerView.updateColors();
        accountPickerView.updateColors();
        menuView.updateColors();
        invalidate();
    }

    private void setupCallbacks() {
        headerView.setOnChevronClick(() -> {
            accountPickerView.toggleExpand();
            headerView.setChevronExpanded(accountPickerView.isExpanded());
        });
        headerView.setOnThemeToggle(this::toggleTheme);
        headerView.setOnThemeToggleLongClick(() -> closeDrawerAndRun(() -> {
            final BaseFragment lastFragment = getLastFragment();
            if (lastFragment != null) {
                lastFragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
            }
        }));
        headerView.setOnNavigateToProfile(() -> closeDrawerAndRun(() -> {
            final BaseFragment lastFragment = getLastFragment();
            if (lastFragment == null) {
                return;
            }
            final Bundle args = new Bundle();
            args.putLong("user_id", UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
            args.putBoolean("my_profile", true);
            lastFragment.presentFragment(new ProfileActivity(args));
        }));
        headerView.setOnStatusClick(this::showStatusSelect);
        headerView.setOnProxyClick(() -> closeDrawerAndRun(() -> {
            final BaseFragment lastFragment = getLastFragment();
            if (lastFragment != null) {
                lastFragment.presentFragment(new ProxyListActivity());
            }
        }));
        accountPickerView.setOnAccountSelected(() -> closeDrawer(true));
        accountPickerView.setOnAccountLongClick(this::onAccountLongClick);
        menuView.setOnItemClick(() -> closeDrawer(true));
    }

    /** Сначала закрыть шторку, через 200 мс открыть экран. */
    private void closeDrawerAndRun(Runnable action) {
        closeDrawer(true);
        AndroidUtilities.runOnUIThread(action, 200L);
    }

    /**
     * exteraGram открывает превью {@code MainTabsActivity} чужого аккаунта
     * ({@code DrawerContainer.showAccountPreview} :855). У нас нет ни его конструктора
     * с {@code Bundle}, ни отрисовки превью поверх шторки, поэтому долгое нажатие
     * делает то же, что тап: закрывает шторку и переключает аккаунт.
     */
    private void onAccountLongClick(int account, View view) {
        if (account == UserConfig.selectedAccount) {
            return;
        }
        closeDrawer(true);
        final Context context = getContext();
        if (context instanceof LaunchActivity) {
            ((LaunchActivity) context).switchToAccount(account, true);
        }
    }

    /**
     * exteraGram: {@code lambda$setupCallbacks$2} — метод «не декомпилирован», но байткод читаемый
     * и восстановлен по нему: выбираются последняя дневная и последняя ночная темы, при
     * совпадении — разводятся к «Blue»/«Dark Blue», дальше уведомление
     * {@code needSetDayNightTheme} с центром круга на кнопке переключателя.
     */
    private void toggleTheme() {
        if (DialogsActivity.switchingTheme) {
            return;
        }
        final int[] pos = headerView.getThemeTogglePosition();
        final SharedPreferences preferences =
                ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Context.MODE_PRIVATE);

        String dayThemeName = preferences.getString("lastDayTheme", "Blue");
        if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
            dayThemeName = "Blue";
        }
        String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
        if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
            nightThemeName = "Dark Blue";
        }
        final Theme.ThemeInfo activeTheme = Theme.getActiveTheme();
        if (dayThemeName.equals(nightThemeName)) {
            if (activeTheme.isDark() || dayThemeName.equals("Dark Blue") || dayThemeName.equals("Night")) {
                dayThemeName = "Blue";
            } else {
                nightThemeName = "Dark Blue";
            }
        }

        final boolean toDark = dayThemeName.equals(activeTheme.getKey());
        final Theme.ThemeInfo themeInfo = Theme.getTheme(toDark ? nightThemeName : dayThemeName);
        if (themeInfo == null) {
            return;
        }
        DialogsActivity.switchingTheme = true;
        headerView.animateThemeToggle(toDark);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needSetDayNightTheme,
                themeInfo, false, pos, -1, toDark, headerView.getThemeToggleView(), null, null, false, null);
        final BaseFragment lastFragment = getLastFragment();
        if (lastFragment != null) {
            Theme.turnOffAutoNight(org.telegram.ui.Components.BulletinFactory.of(lastFragment),
                    () -> lastFragment.presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT)));
        }
    }

    // ---- выбор эмодзи-статуса ----

    private int getPopupWidth() {
        return (int) Math.min(AndroidUtilities.dp(324.0f), AndroidUtilities.displaySize.x * 0.95f);
    }

    private void dismissSelectionPopup() {
        if (selectAnimatedEmojiDialog != null) {
            selectAnimatedEmojiDialog.dismiss();
            selectAnimatedEmojiDialog = null;
        }
    }

    private void showStatusSelect() {
        if (selectAnimatedEmojiDialog != null) {
            return;
        }
        final BaseFragment lastFragment = getLastFragment();
        if (lastFragment == null) {
            return;
        }
        final int account = UserConfig.selectedAccount;
        final TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        if (user == null || (!MessagesController.getInstance(account).isPremiumUser(user) && !app.exteraless.chats.CustomEmojiStatusHelper.isEnabled())) {
            return;
        }
        final SimpleTextView nameView = headerView.getNameView();
        final int[] location = new int[2];
        nameView.getLocationOnScreen(location);
        final int drawableX = location[0] + nameView.getRightDrawableX();
        final int popupWidth = getPopupWidth();
        final int popupX = MathUtils.clamp(drawableX - (popupWidth / 2), 0, AndroidUtilities.displaySize.x - popupWidth);
        final int popupY = location[1] + nameView.getHeight();

        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[] popup =
                new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow[1];
        final SelectAnimatedEmojiDialog dialog = new SelectAnimatedEmojiDialog(lastFragment, getContext(), true,
                Math.max(0, drawableX - popupX), SelectAnimatedEmojiDialog.TYPE_EMOJI_STATUS, true, null, 16,
                Theme.getColor(COLOR_KEY_POPUP_ACCENT)) {
            @Override
            protected void onEmojiSelected(View view, Long documentId, TLRPC.Document document,
                                           TL_stars.TL_starGiftUnique gift, Integer until) {
                final TLRPC.EmojiStatus status;
                if (gift != null) {
                    final TLRPC.TL_inputEmojiStatusCollectible collectible = new TLRPC.TL_inputEmojiStatusCollectible();
                    collectible.collectible_id = gift.id;
                    if (until != null) {
                        collectible.flags |= 1;
                        collectible.until = until;
                    }
                    status = collectible;
                } else if (documentId == null) {
                    status = new TLRPC.TL_emojiStatusEmpty();
                } else {
                    final TLRPC.TL_emojiStatus emojiStatus = new TLRPC.TL_emojiStatus();
                    emojiStatus.document_id = documentId;
                    if (until != null) {
                        emojiStatus.flags |= 1;
                        emojiStatus.until = until;
                    }
                    status = emojiStatus;
                }
                MessagesController.getInstance(account).updateEmojiStatus(0L, status, gift);
                headerView.updateUserInfo();
                if (popup[0] != null) {
                    selectAnimatedEmojiDialog = null;
                    popup[0].dismiss();
                }
            }
        };
        dialog.setExpireDateHint(DialogObject.getEmojiStatusUntil(user.emoji_status));
        Long userDocId = org.telegram.messenger.UserObject.getEmojiStatusDocumentId(user);
        final long currentStatusId = userDocId != null ? userDocId : DialogObject.getEmojiStatusDocumentId(user.emoji_status);
        dialog.setSelected(currentStatusId != 0 ? Long.valueOf(currentStatusId) : null);
        dialog.setSaveState(3);

        final SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow window =
                new SelectAnimatedEmojiDialog.SelectAnimatedEmojiDialogWindow(dialog,
                        LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
                    @Override
                    public void dismiss() {
                        super.dismiss();
                        selectAnimatedEmojiDialog = null;
                    }
                };
        selectAnimatedEmojiDialog = window;
        popup[0] = window;
        final int[] selfLocation = new int[2];
        getLocationOnScreen(selfLocation);
        window.showAsDropDown(this, popupX, (popupY - selfLocation[1]) - AndroidUtilities.dp(16.0f),
                Gravity.LEFT | Gravity.TOP);
        window.dimBehind();
    }

    // ---- уведомления ----

    private void registerNotifications() {
        if (notificationsRegistered) {
            return;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final NotificationCenter center = NotificationCenter.getInstance(a);
            center.addObserver(this, NotificationCenter.mainUserInfoChanged);
            center.addObserver(this, NotificationCenter.userEmojiStatusUpdated);
            center.addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            center.addObserver(this, NotificationCenter.updateInterfaces);
            center.addObserver(this, NotificationCenter.appDidLogout);
            center.addObserver(this, NotificationCenter.attachMenuBotsDidLoad);
            center.addObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        final NotificationCenter global = NotificationCenter.getGlobalInstance();
        global.addObserver(this, NotificationCenter.didSetNewTheme);
        global.addObserver(this, NotificationCenter.themeAccentListUpdated);
        global.addObserver(this, NotificationCenter.notificationsCountUpdated);
        global.addObserver(this, NotificationCenter.reloadInterface);
        global.addObserver(this, NotificationCenter.proxySettingsChanged);
        notificationsRegistered = true;
    }

    private void unregisterNotifications() {
        if (!notificationsRegistered) {
            return;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            final NotificationCenter center = NotificationCenter.getInstance(a);
            center.removeObserver(this, NotificationCenter.mainUserInfoChanged);
            center.removeObserver(this, NotificationCenter.userEmojiStatusUpdated);
            center.removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            center.removeObserver(this, NotificationCenter.updateInterfaces);
            center.removeObserver(this, NotificationCenter.appDidLogout);
            center.removeObserver(this, NotificationCenter.attachMenuBotsDidLoad);
            center.removeObserver(this, NotificationCenter.didUpdateConnectionState);
        }
        final NotificationCenter global = NotificationCenter.getGlobalInstance();
        global.removeObserver(this, NotificationCenter.didSetNewTheme);
        global.removeObserver(this, NotificationCenter.themeAccentListUpdated);
        global.removeObserver(this, NotificationCenter.notificationsCountUpdated);
        global.removeObserver(this, NotificationCenter.reloadInterface);
        global.removeObserver(this, NotificationCenter.proxySettingsChanged);
        notificationsRegistered = false;
    }

    private void refreshAccountViews(int account, boolean reloadAccounts) {
        if (account == UserConfig.selectedAccount) {
            headerView.updateUserInfo();
        }
        if (reloadAccounts) {
            accountPickerView.loadAccounts();
        }
    }

    private void refreshAccountViews(int account, int mask) {
        final boolean updateHeader = (MessagesController.UPDATE_MASK_AVATAR & mask) != 0
                || (MessagesController.UPDATE_MASK_NAME & mask) != 0
                || (MessagesController.UPDATE_MASK_PHONE & mask) != 0
                || (MessagesController.UPDATE_MASK_EMOJI_STATUS & mask) != 0;
        final boolean updateAccounts = (MessagesController.UPDATE_MASK_AVATAR & mask) != 0
                || (MessagesController.UPDATE_MASK_NAME & mask) != 0
                || (MessagesController.UPDATE_MASK_EMOJI_STATUS & mask) != 0;
        if (updateHeader && account == UserConfig.selectedAccount) {
            headerView.updateUserInfo();
        }
        if (updateAccounts) {
            accountPickerView.loadAccounts();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.mainUserInfoChanged
                || id == NotificationCenter.userEmojiStatusUpdated
                || id == NotificationCenter.currentUserPremiumStatusChanged) {
            refreshAccountViews(account, true);
        } else if (id == NotificationCenter.updateInterfaces) {
            if (args.length > 0 && args[0] instanceof Integer) {
                refreshAccountViews(account, (Integer) args[0]);
            }
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
        } else if (id == NotificationCenter.didSetNewTheme) {
            updateColors();
        } else if (id == NotificationCenter.themeAccentListUpdated) {
            AndroidUtilities.runOnUIThread(this::updateColors);
        } else if (id == NotificationCenter.notificationsCountUpdated) {
            accountPickerView.updateUnreadCounters();
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
        } else if (id == NotificationCenter.reloadInterface) {
            headerView.updateUserInfo();
            accountPickerView.updateUnreadCounters();
            menuView.updateUnreadCounters(UserConfig.selectedAccount);
            updateColors();
        } else if (id == NotificationCenter.attachMenuBotsDidLoad) {
            if (account == UserConfig.selectedAccount && isOpen) {
                refreshContents();
            }
        } else if (id == NotificationCenter.proxySettingsChanged || id == NotificationCenter.didUpdateConnectionState) {
            headerView.updateProxyStatus();
        } else if (id == NotificationCenter.appDidLogout) {
            refreshAccountViews(account, true);
            if (isOpen) {
                closeDrawer(false);
            }
        }
    }
}
