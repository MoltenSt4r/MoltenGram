package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedLinearLayout;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.glass.GlassTabView;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.MainTabsUiHelper;

import java.util.HashSet;
import java.util.Set;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.ListAnimator;
import me.vkryl.android.util.ClickHelper;

import tw.nekomimi.nekogram.NekoConfig;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("ViewConstructor")
public class MainTabsLayout extends AnimatedLinearLayout {

    public static final int BOTTOM_NAVIGATION_MODE_SHOW = 0;
    public static final int BOTTOM_NAVIGATION_MODE_HIDE = 1;
    public static final int BOTTOM_NAVIGATION_MODE_FLOATING = 2;
    public static final int BOTTOM_NAVIGATION_MODE_EXPRESSIVE = 3;

    private static final String KEY_BOTTOM_NAVIGATION_FLOATING = "OEAppearanceBottomNavigationFloating";
    private static final String KEY_BOTTOM_NAVIGATION_EXPRESSIVE = "OEAppearanceBottomNavigationExpressive";

    private static Boolean bottomNavigationFloating;
    private static Boolean bottomNavigationExpressive;

    public static int getBottomNavigationMode() {
        if (isBottomNavigationHidden()) {
            return BOTTOM_NAVIGATION_MODE_HIDE;
        }
        if (isBottomNavigationExpressive()) {
            return BOTTOM_NAVIGATION_MODE_EXPRESSIVE;
        }
        return isBottomNavigationFloating() ? BOTTOM_NAVIGATION_MODE_FLOATING : BOTTOM_NAVIGATION_MODE_SHOW;
    }

    public static void setBottomNavigationMode(int mode) {
        NaConfig.INSTANCE.getHideBottomNavigationBar().setConfigBool(mode == BOTTOM_NAVIGATION_MODE_HIDE);
        final boolean floating = mode == BOTTOM_NAVIGATION_MODE_FLOATING;
        final boolean expressive = mode == BOTTOM_NAVIGATION_MODE_EXPRESSIVE;
        bottomNavigationFloating = floating;
        bottomNavigationExpressive = expressive;
        final SharedPreferences preferences = getBottomNavigationPreferences();
        if (preferences != null) {
            preferences.edit()
                    .putBoolean(KEY_BOTTOM_NAVIGATION_FLOATING, floating)
                    .putBoolean(KEY_BOTTOM_NAVIGATION_EXPRESSIVE, expressive)
                    .apply();
        }
    }

    public static boolean isBottomNavigationHidden() {
        return NaConfig.INSTANCE.getHideBottomNavigationBar().Bool();
    }

    public static boolean isBottomNavigationVisible() {
        return !isBottomNavigationHidden();
    }

    public static boolean isBottomNavigationFloating() {
        if (isBottomNavigationHidden()) {
            return false;
        }
        if (bottomNavigationFloating == null) {
            final SharedPreferences preferences = getBottomNavigationPreferences();
            if (preferences == null) {
                return false;
            }
            bottomNavigationFloating = preferences.getBoolean(KEY_BOTTOM_NAVIGATION_FLOATING, false);
        }
        return bottomNavigationFloating;
    }

    public static boolean isBottomNavigationExpressive() {
        if (isBottomNavigationHidden()) {
            return false;
        }
        if (bottomNavigationExpressive == null) {
            final SharedPreferences preferences = getBottomNavigationPreferences();
            if (preferences == null) {
                return false;
            }
            bottomNavigationExpressive = preferences.getBoolean(KEY_BOTTOM_NAVIGATION_EXPRESSIVE, false);
        }
        return bottomNavigationExpressive;
    }

    private static SharedPreferences getBottomNavigationPreferences() {
        return ApplicationLoader.applicationContext == null ? null : NekoConfig.getPreferences();
    }

    private final Theme.ResourcesProvider resourcesProvider;

    public MainTabsLayout(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    private static final float[] PASS_TEXT_SIZES_DP = {12f, 12f, 10f};
    private static final int[] PASS_PADDINGS_DP = {16, 8, 4};

    private int maxWidthPx;

    public void setMaxWidth(int maxWidthPx) {
        if (this.maxWidthPx != maxWidthPx) {
            this.maxWidthPx = maxWidthPx;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MainTabsUiHelper.isExpressiveToolbar()) {
            measureExpressiveToolbar(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = height - getPaddingTop() - getPaddingBottom();

        if (maxWidthPx > 0 && width > maxWidthPx) {
            width = maxWidthPx;
        }

        final boolean fillWidth = MainTabsUiHelper.isMaterial3NavigationBar() || MainTabsUiHelper.isIosNavigationBar();
        final int maxTotalWidthForTabs = width - getPaddingLeft() - getPaddingRight();
        final int minTotalWidthForTabs = fillWidth
                ? maxTotalWidthForTabs
                : Math.min(dp(320), maxTotalWidthForTabs);

        int chosenPass = PASS_TEXT_SIZES_DP.length - 1;
        float lastMeasuredTextSize = -1;
        for (int pass = 0; pass < PASS_TEXT_SIZES_DP.length; pass++) {
            if (PASS_TEXT_SIZES_DP[pass] != lastMeasuredTextSize) {
                measureTabTexts(PASS_TEXT_SIZES_DP[pass]);
                lastMeasuredTextSize = PASS_TEXT_SIZES_DP[pass];
            }
            final int padding = dp(PASS_PADDINGS_DP[pass]);
            float total = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                if (!isViewVisible(getChildAt(a))) continue;
                final float withMargin = tabsTextWidth[a] + padding * 2;
                total += withMargin;
            }
            final boolean fits = total <= maxTotalWidthForTabs;
            if (fits || pass == PASS_TEXT_SIZES_DP.length - 1) {
                chosenPass = pass;
                break;
            }
        }

        applyPassTextSize(chosenPass);

        final int tabPadding = dp(PASS_PADDINGS_DP[chosenPass]);
        final int maxTabTextWidthIfEq = (maxTotalWidthForTabs / Math.max(1, visibleChildCount)) - tabPadding * 2;

        if (fillWidth && visibleChildCount > 0) {
            int remWidth = maxTotalWidthForTabs;
            int remTabs = visibleChildCount;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);
                if (!isViewVisible(child)) {
                    tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                    tabsWeight[a] = 0;
                    continue;
                }
                int tabW = remWidth / remTabs;
                tabsTextWidthWithMargin[a] = tabW;
                tabsWeight[a] = 1;
                remWidth -= tabW;
                remTabs--;
            }
        } else {
            float totalWidth = 0;
            int totalWeight = 0;
            for (int a = 0, N = getChildCount(); a < N; a++) {
                final View child = getChildAt(a);
                if (!isViewVisible(child)) {
                    tabsTextWidth[a] = tabsTextWidthWithMargin[a] = 0;
                    tabsWeight[a] = 0;
                    continue;
                }

                tabsTextWidthWithMargin[a] = tabsTextWidth[a] + tabPadding * 2;
                tabsWeight[a] = tabsTextWidthWithMargin[a] > (maxTabTextWidthIfEq + tabPadding * 2) ? 0 : 1;

                totalWidth += tabsTextWidthWithMargin[a];
                totalWeight += tabsWeight[a];
            }

            if (totalWeight == 0) {
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    tabsWeight[a] = isViewVisible(getChildAt(a)) ? 1 : 0;
                }
                totalWeight = visibleChildCount;
            }

            if (totalWidth > maxTotalWidthForTabs) {
                final float m = maxTotalWidthForTabs / totalWidth;
                for (int a = 0, N = getChildCount(); a < N; a++) {
                    tabsTextWidthWithMargin[a] *= m;
                }
            } else if (totalWidth < minTotalWidthForTabs) {
                final float growW = minTotalWidthForTabs - totalWidth;
                final float growP = growW / totalWeight;

                for (int a = 0, N = getChildCount(); a < N; a++) {
                    tabsTextWidthWithMargin[a] += growP * tabsWeight[a];
                }
            }
        }

        int l = 0;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            if (!isViewVisible(getChildAt(a))) {
                continue;
            }

            tabsWidth[a] = Math.round(tabsTextWidthWithMargin[a]);
            tabsLeftPos[a] = l;
            l += tabsWidth[a];
        }
        setMeasuredDimension(l + getPaddingLeft() + getPaddingRight(), height);
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            child.measure(
                MeasureSpec.makeMeasureSpec(tabsWidth[a], MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        calculateTotalSizesAfterMeasure();
    }

    public interface Tab {
        float measureTextWidth();
        default float measureTextWidth(float textSizeDp) { return measureTextWidth(); }
        default void setTextSizeDp(float textSizeDp) {}
    }



    // fills tabsTextWidth[] and return visible child count;

    private float[] tabsTextWidth;
    private float[] tabsTextWidthWithMargin;
    private int[] tabsWeight;
    private int[] tabsWidth;

    private int[] tabsLeftPos;


    private int visibleChildCount;
    private int biggestTabTextWidth;

    private void measureTabTexts(float textSizeDp) {
        final int childCount = getChildCount();
        if (tabsTextWidth == null || tabsTextWidth.length < childCount) {
            tabsTextWidth = new float[childCount];
            tabsTextWidthWithMargin = new float[childCount];
            tabsWeight = new int[childCount];
            tabsLeftPos = new int[childCount];
            tabsWidth = new int[childCount];
        }

        float maxTabWidthF = 0;
        int index = 0;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                tabsTextWidth[a] = -1;
                continue;
            }

            final float tabWidth;
            if (child instanceof MainTabsLayout.Tab) {
                tabWidth = ((MainTabsLayout.Tab) child).measureTextWidth(textSizeDp);
            } else {
                tabWidth = 0;
            }

            tabsTextWidth[a] = tabWidth;
            maxTabWidthF = Math.max(maxTabWidthF, tabWidth);
            index++;
        }

        biggestTabTextWidth = (int) Math.ceil(maxTabWidthF);
        visibleChildCount = index;
    }

    private void applyPassTextSize(int pass) {
        final float textSizeDp = PASS_TEXT_SIZES_DP[pass];
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof MainTabsLayout.Tab) {
                ((MainTabsLayout.Tab) child).setTextSizeDp(textSizeDp);
            }
        }
    }

    @Override
    protected void setChildVisibilityFactor(View view, float factor) {
        final float s = lerp(0.7f, 1f, factor);
        view.setAlpha(factor);
        view.setScaleX(s);
        view.setScaleY(s);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (MainTabsUiHelper.isExpressiveToolbar()) {
            updateExpressivePositions(false);
            return;
        }
        checkVisualWidth();
    }

    @Override
    protected void onItemsChanged() {
        super.onItemsChanged();
        if (MainTabsUiHelper.isExpressiveToolbar()) {
            updateExpressivePositions(true);
            return;
        }
        checkVisualWidth();
    }

    private void checkVisualWidth() {
        for (int a = 0, N = getEntriesCount(); a < N; a++) {
            final ListAnimator.Entry<Holder> entry = getEntry(a);
            final float width = entry.getRectF().width();
            ((GlassTabView) entry.item.view).setVisualWidth(width);
        }
    }








    public void setTabSelected(View tab, boolean animated) {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSelected(child == tab, animated);
            }
        }
        if (MainTabsUiHelper.isExpressiveToolbar()) {
            updateExpressivePositions(animated);
            invalidate();
        }
    }

    private View findSelectedTab() {
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                if (((GlassTabView) child).isTabSelected()) {
                    return child;
                }
            }
        }
        return null;
    }

    private final Runnable restoreDrawSelector = () -> setSkipDrawSelector(false);

    private boolean drawCustomSelector;
    private void setSkipDrawSelector(boolean skipDrawSelector) {
        drawCustomSelector = skipDrawSelector;
        if (drawCustomSelector) {
            selectorPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider), 0.09f));
        }
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }

            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSkipDrawSelector(skipDrawSelector);
            }
        }
        invalidate();
    }







    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (MainTabsUiHelper.isExpressiveToolbar()) {
            drawExpressiveToolbar(canvas);
            return;
        }
        if (lensShown) {
            drawLens(canvas);
            return;
        }
        if (drawCustomSelector) {
            final float x = animatedLongSelectedViewCenterX + animatedLongSelectedViewOffsetX;
            final float sWidth = getInterpolatedWidthByX(x, this);
            if (MainTabsUiHelper.isMaterial3NavigationBar()) {
                MainTabsUiHelper.setMainTabSelectedIndicatorBounds(selectorRect, sWidth,
                        getHeight() - getPaddingTop() - getPaddingBottom());
                selectorRect.offset(x - sWidth / 2f, getPaddingTop());
                final float r = selectorRect.height() / 2f;
                canvas.drawRoundRect(selectorRect, r, r, selectorPaint);
            } else {
                final float sHeight = getHeight() - getPaddingTop() - getPaddingBottom();
                canvas.drawRoundRect(
                        x - sWidth / 2f, (getHeight() - sHeight) / 2f,
                        x + sWidth / 2f, (getHeight() + sHeight) / 2f,
                        sHeight / 2f, sHeight / 2f, selectorPaint);
            }
        }

        super.dispatchDraw(canvas);

        if (drawTopDivider && MainTabsUiHelper.isMaterial3NavigationBar() && !app.exteraless.glass.LiquidGlassConfig.isLiquidGlassEnabled()) {
            dividerPaint.setColor(Theme.getDividerColor(resourcesProvider));
            canvas.drawLine(0, 1, getMeasuredWidth(), 1, dividerPaint);
        }

        if (speedDialDimProgress > 0.001f) {
            speedDialDimPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(android.graphics.Color.BLACK, Math.round(140f * speedDialDimProgress)));
            if (MainTabsUiHelper.isMaterial3NavigationBar() && !MainTabsUiHelper.isMaterial3FloatingGlass()) {
                canvas.drawRect(0, 0, getWidth(), getHeight(), speedDialDimPaint);
            } else {
                final float r = dp(MainTabsUiHelper.getBackgroundRadius());
                final int inset = MainTabsUiHelper.getBackgroundInset();
                speedDialDimRect.set(inset, inset, getWidth() - inset, getHeight() - inset);
                canvas.drawRoundRect(speedDialDimRect, r, r, speedDialDimPaint);
            }
        }
    }

    private float speedDialDimProgress;
    private Runnable onSpeedDialDimClick;
    private final Paint speedDialDimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF speedDialDimRect = new RectF();

    public void setSpeedDialDim(float progress, Runnable onClick) {
        speedDialDimProgress = Math.max(0f, Math.min(1f, progress));
        onSpeedDialDimClick = onClick;
        invalidate();
    }

    private boolean drawTopDivider = true;

    public void setDrawTopDivider(boolean value) {
        if (drawTopDivider != value) {
            drawTopDivider = value;
            invalidate();
        }
    }

    final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint selectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF selectorRect = new android.graphics.RectF();
    final SpringAnimation scaleX = new SpringAnimation(this, DynamicAnimation.SCALE_X, 1f);
    final SpringAnimation scaleY = new SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f);

    final SpringAnimation selectedTabPositionOffsetX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionOffsetX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewOffsetX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewOffsetX = value;
            object.invalidate();
        }
    });
    final SpringAnimation selectedTabPositionX = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("selectedTabPositionX") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.animatedLongSelectedViewCenterX;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.animatedLongSelectedViewCenterX = value;
            object.invalidate();
        }
    });

    {
        selectedTabPositionOffsetX.setSpring(new SpringForce(1)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
        scaleX.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        scaleY.setSpring(new SpringForce(1f)
            .setStiffness(250)
            .setDampingRatio(0.25f));
        selectedTabPositionX.setSpring(new SpringForce(1f)
            .setStiffness(SpringForce.STIFFNESS_MEDIUM)
            .setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY));
    }

    private float animatedLongSelectedViewCenterX;
    private float animatedLongSelectedViewOffsetX;

    private boolean isInLongPress;
    private float lastLongSelectedViewCenterX;
    private float lastLongSelectedViewWidth;
    private View lastLongSelectedView;




    public static View findChildUnder(ViewGroup parent, float x, float y) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);

            if (child.getVisibility() != View.VISIBLE) continue;

            if (child instanceof GlassTabView && ((GlassTabView) child).isMainTabExpressive()) {
                final float left = child.getX();
                final float right = left + ((GlassTabView) child).getExpressiveSlotWidth();
                if (x >= left && x <= right && y >= child.getY() && y <= child.getY() + child.getHeight()) {
                    return child;
                }
                continue;
            }

            if (x >= child.getLeft() && x <= child.getRight()
                    && y >= child.getTop() && y <= child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    private void checkLongMove(float x_, float y, boolean start, boolean end) {
        final float x = clampXToChildrenCenters(x_, this);
        final View found = findNearestVisibleChildByX(x, this);
        if (start) {
            View selected = findSelectedTab();
            if (selected != null) {
                animatedLongSelectedViewCenterX = selected.getX() + selected.getWidth() / 2f;
                animatedLongSelectedViewOffsetX = animatedLongSelectedViewCenterX - x;
                selectedTabPositionOffsetX.animateToFinalPosition(0);
                if (selected != found && found != null) {
                    found.performClick();
                }
            }
            selectedTabPositionX.cancel();
        }

        if (!end) {
            animatedLongSelectedViewCenterX = x;
            invalidate();
        }

        if (found != null) {
            lastLongSelectedView = found;
            setTabSelected(found, true);

            if (end) {
                final float vw = found.getWidth();
                final float cx = found.getX() + vw / 2f;
                if (lastLongSelectedViewWidth != vw || lastLongSelectedViewCenterX != cx) {
                    selectedTabPositionX.animateToFinalPosition(cx);
                }
            }
        }
    }

    private final Set<View> tabsWithIgnoreClick = new HashSet<>();
    public void addTabToIgnoreClick(View v) {
        tabsWithIgnoreClick.add(v);
    }

    private final BoolAnimator animatorIsScaled = new BoolAnimator(0, (a, factor, c, g) -> {
        setScaleX(lerp(1, 1.019f, factor));
        setScaleY(lerp(1, 1.019f, factor));
    }, CubicBezierInterpolator.EASE_OUT_QUINT, 380);

    private final ClickHelper clickHelper = new ClickHelper(new ClickHelper.Delegate() {
        @Override
        public boolean needClickAt(View view, float x, float y) {
            lastLongSelectedView = null;
            final View found = findChildUnder(MainTabsLayout.this, x, y);
            return found != null && !tabsWithIgnoreClick.contains(found);
        }

        @Override
        public void onClickAt(View view, float x, float y) {
        }

        @Override
        public boolean needLongPress(float x, float y) {
            return true;
        }

        @Override
        public boolean needCancelTouchBySlopMove() {
            return false;
        }


        @Override
        public boolean onLongPressRequestedAt(View view, float x, float y) {
            checkPivot(view, x, y);
            isInLongPress = true;
            AndroidUtilities.cancelRunOnUIThread(restoreDrawSelector);
            setSkipDrawSelector(true);
            checkLongMove(x, y, true, false);
            invalidate();
            longTouchStart();
            return true;
        }

        @Override
        public void onLongPressMove(View view, MotionEvent e, float x, float y, float startX, float startY) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, false);
            invalidate();
        }

        @Override
        public long getLongPressDuration() {
            return ClickHelper.Delegate.super.getLongPressDuration() * 750 / 1000;
        }

        @Override
        public void onLongPressFinish(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            if (lastLongSelectedView != null) {
                lastLongSelectedView.performClick();
            }
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        @Override
        public void onLongPressCancelled(View view, float x, float y) {
            checkPivot(view, x, y);
            checkLongMove(x, y, false, true);
            isInLongPress = false;
            AndroidUtilities.runOnUIThread(restoreDrawSelector, 450);
            lastLongSelectedView = null;
            invalidate();
            longTouchEnd();
        }

        private void longTouchStart() {
            animatorIsScaled.setValue(true, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(-0.45f);
                scaleY.setStartVelocity(-0.45f);
            }
            scaleX.animateToFinalPosition(1.012f);
            scaleY.animateToFinalPosition(1.012f);
            */
        }

        private void longTouchEnd() {
            animatorIsScaled.setValue(false, true);

            /*
            if (!scaleX.isRunning()) {
                scaleX.setStartVelocity(0.25f);
                scaleY.setStartVelocity(0.25f);
            }
            scaleX.animateToFinalPosition(1f);
            scaleY.animateToFinalPosition(1f);
            */
        }
    });

    @Override
    public void setScaleY(float scaleY) {
        super.setScaleY(scaleY);
        checkLayerType();
    }

    @Override
    public void setScaleX(float scaleX) {
        super.setScaleX(scaleX);
        checkLayerType();
    }

    private void checkLayerType() {
        final int layerType = Math.abs(getScaleX() - 1f) < 0.0001f && Math.abs(getScaleY() - 1f) < 0.0001f ?
            View.LAYER_TYPE_NONE : View.LAYER_TYPE_HARDWARE;

        if (getLayerType() != layerType) {
            setLayerType(layerType, null);
            invalidate();
        }
    }


    private void checkPivot(View view, float x, float y) {
        float w = view.getWidth();
        float h = view.getHeight();

        if (w <= 0f || h <= 0f) {
            return;
        }

        float cx = w * 0.5f;
        float cy = h * 0.5f;

        float dx = x - cx;
        float dy = y - cy;

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;

        float nx = dx / halfW;
        float ny = dy / halfH;

        float r = (float) Math.sqrt(nx * nx + ny * ny);

        float pivotX;
        float pivotY;

        if (r > 1e-4f) {
            float mappedR = 1.5f * r / (r + 0.5f);

            float scale = mappedR / r;
            pivotX = cx + dx * scale;
            pivotY = cy + dy * scale;
        } else {
            pivotX = cx;
            pivotY = cy;
        }

        pivotX = lerp(cx, pivotX, 1f);
        pivotY = lerp(cy, pivotY, 3f);

        view.setPivotX(pivotX);
        view.setPivotY(pivotY);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (speedDialDimProgress > 0.05f) {
            if (ev.getActionMasked() == MotionEvent.ACTION_UP && onSpeedDialDimClick != null) {
                onSpeedDialDimClick.run();
            }
            return true;
        }
        if (MainTabsUiHelper.isExpressiveToolbar() && !expressiveCapsuleRect.isEmpty()) {
            if (ev.getActionMasked() == MotionEvent.ACTION_DOWN
                    && !expressiveCapsuleRect.contains(ev.getX(), ev.getY())) {
                return false;
            }
        }
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            lensMode = app.exteraless.glass.LiquidGlassConfig.isLiquidGlassEnabled()
                    && !MainTabsUiHelper.isMaterial3NavigationBar()
                    && !MainTabsUiHelper.isExpressiveToolbar();
        }
        if (lensMode) {
            lensTouch(ev);
            final boolean handled = super.dispatchTouchEvent(ev);
            return handled || lensTracking;
        }
        clickHelper.onTouchEvent(this, ev);
        final boolean handled = super.dispatchTouchEvent(ev);
        if (!handled && MainTabsUiHelper.isExpressiveToolbar()
                && getVisibility() == View.VISIBLE && getAlpha() > 0.05f
                && !expressiveCapsuleRect.isEmpty()
                && expressiveCapsuleRect.contains(ev.getX(), ev.getY())) {
            return true;
        }
        return handled;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return lensMode && lensDragging || super.onInterceptTouchEvent(ev);
    }

    private boolean lensMode;
    private boolean lensTracking, lensDragging;
    private boolean lensShown;
    private float lensDownX;
    @Nullable
    private View lensDownChild, lensFound, lensStartSelected;
    private final app.exteraless.glass.LiquidControls.GlassSpring lensX = new app.exteraless.glass.LiquidControls.GlassSpring(0).params(0.34f, 0.7f);
    private final app.exteraless.glass.LiquidControls.GlassSpring lensA = new app.exteraless.glass.LiquidControls.GlassSpring(0).rest(0.002f);
    private long lensLastFrame;
    private boolean lensRich;
    @Nullable
    private Object lensShader;
    private boolean lensShaderFailed;
    private final Path lensPath = new Path();
    private final RectF lensRect = new RectF();
    private final Paint lensPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lensRimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix lensRimMatrix = new Matrix();
    @Nullable
    public Utilities.Callback<Canvas> solegramLensBackdrop;

    private final Utilities.Callback<Canvas> lensContent = c -> {
        if (solegramLensBackdrop != null) {
            solegramLensBackdrop.run(c);
        }
        final android.graphics.drawable.Drawable bg = getBackground();
        if (bg != null) {
            bg.draw(c);
        }
        super.dispatchDraw(c);
    };
    private final Runnable lensHoldCheck = () -> {
        if (lensTracking && !lensDragging && lensDownChild != null && lensDownChild.isLongClickable()) {
            lensRelease(false);
        }
    };

    private void lensTouch(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                lensTracking = false;
                lensDragging = false;
                removeCallbacks(lensHoldCheck);
                final View child = findChildUnder(this, ev.getX(), ev.getY());
                if (child == null) {
                    return;
                }
                lensTracking = true;
                lensRich = !LiteMode.isPowerSaverApplied();
                lensDownX = ev.getX();
                lensDownChild = child;
                lensStartSelected = findSelectedTab();
                lensFound = lensStartSelected;
                if (!lensShown) {
                    final View from = lensStartSelected != null ? lensStartSelected : child;
                    lensX.set(from.getX() + from.getWidth() / 2f);
                    lensA.set(0);
                    setLensShown(true);
                }
                lensX.to(clampXToChildrenCenters(ev.getX(), this));
                lensA.params(0.3f, 0.66f);
                lensA.to(1);
                postDelayed(lensHoldCheck, ViewConfiguration.getLongPressTimeout());
                invalidate();
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!lensTracking) {
                    return;
                }
                if (!lensDragging && Math.abs(ev.getX() - lensDownX) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    lensDragging = true;
                    removeCallbacks(lensHoldCheck);
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (lensDragging) {
                    final float x = clampXToChildrenCenters(ev.getX(), this);
                    lensX.to(x);
                    final View found = findNearestVisibleChildByX(x, this);
                    if (found != null && found != lensFound) {
                        lensFound = found;
                        setTabSelected(found, true);
                        try {
                            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        } catch (Exception ignore) {
                        }
                    }
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                lensRelease(true);
                break;
            case MotionEvent.ACTION_CANCEL:
                lensRelease(false);
                break;
        }
    }

    private void lensRelease(boolean up) {
        removeCallbacks(lensHoldCheck);
        if (!lensTracking) {
            return;
        }
        if (lensDragging) {
            if (up && lensFound != null && lensFound != lensStartSelected) {
                lensFound.performClick();
            } else if (lensStartSelected != null && lensFound != lensStartSelected) {
                setTabSelected(lensStartSelected, true);
            }
        }
        lensTracking = false;
        lensDragging = false;
        lensDownChild = null;
        lensA.params(0.44f, 0.58f);
        lensA.to(0);
        invalidate();
    }

    private void setLensShown(boolean shown) {
        lensShown = shown;
        for (int a = 0, N = getChildCount(); a < N; a++) {
            final View child = getChildAt(a);
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setSkipDrawSelector(shown);
            }
        }
        invalidate();
    }

    private void drawLens(Canvas canvas) {
        final long now = System.nanoTime();
        final float dt = lensLastFrame == 0 ? 1f / 60f : Math.max(0, Math.min(1f / 30f, (now - lensLastFrame) / 1e9f));
        lensLastFrame = now;
        if (!lensTracking) {
            final View selected = findSelectedTab();
            if (selected != null) {
                lensX.to(selected.getX() + selected.getWidth() / 2f);
            }
        }
        boolean moving = lensX.step(dt);
        if (lensA.step(dt)) {
            moving = true;
        }
        if (!moving) {
            lensLastFrame = 0;
            if (!lensTracking && Math.abs(lensA.value) < 0.002f) {
                setLensShown(false);
                super.dispatchDraw(canvas);
                return;
            }
        }

        final float a = Math.max(-0.25f, Math.min(1.1f, lensA.value));
        final float on = Math.max(0, Math.min(1, a));
        final float tabH = getHeight() - getPaddingTop() - getPaddingBottom();
        final float cx = lensX.value, cy = getPaddingTop() + tabH / 2f;
        final float pillW = getInterpolatedWidthByX(cx, this);
        final float lensH = getHeight();
        final float lensW = Math.max(pillW * 1.24f + dp(10), lensH * 1.28f);
        final float stretch = lensRich ? Math.min(0.24f, Math.abs(lensX.velocity) / dp(2200)) : 0f;
        final float w = Math.max(dp(8), pillW + (lensW - pillW) * a) * (1f + stretch);
        final float h = Math.max(dp(8), tabH + (lensH - tabH) * a) * (1f - stretch * 0.25f);
        final float r = h / 2f;
        final float cxDraw = Math.max(w / 2f, Math.min(getWidth() - w / 2f, cx));
        lensFlow = lensRich ? Math.min(1f, Math.abs(lensX.velocity) / dp(1800)) : 0f;
        lensRect.set(cxDraw - w / 2f, cy - h / 2f, cxDraw + w / 2f, cy + h / 2f);
        lensPath.rewind();
        lensPath.addRoundRect(lensRect, r, r, Path.Direction.CW);

        canvas.save();
        if (Build.VERSION.SDK_INT >= 26) {
            canvas.clipOutPath(lensPath);
        } else {
            canvas.clipPath(lensPath, android.graphics.Region.Op.DIFFERENCE);
        }
        super.dispatchDraw(canvas);
        canvas.restore();

        if (Build.VERSION.SDK_INT >= 28 && on > 0.01f) {
            canvas.save();
            canvas.clipOutPath(lensPath);
            lensPaint.setColor(0x06000000);
            lensPaint.setShadowLayer(dp(6) * on, 0, dp(2) * on, (int) (0x26 * on) << 24);
            canvas.drawPath(lensPath, lensPaint);
            lensPaint.clearShadowLayer();
            canvas.restore();
        }
        final boolean shaderLens = drawShaderLens(canvas, cxDraw, cy, w, h, on);
        if (on < 1f) {
            lensPaint.setColor(Theme.multAlpha(Theme.getColor(Theme.key_glass_tabSelected, resourcesProvider), 0.09f * (1f - on)));
            canvas.drawPath(lensPath, lensPaint);
        }
        if (!shaderLens) {
            canvas.save();
            canvas.clipPath(lensPath);
            final float magnify = 1f + 0.3f * on;
            canvas.scale(magnify, magnify, cxDraw, cy);
            super.dispatchDraw(canvas);
            canvas.restore();
            if (on > 0.01f) {
                lensPaint.setColor((int) ((Theme.isCurrentThemeDark() ? 0x0B : 0x0F) * on) << 24 | 0xFFFFFF);
                canvas.drawPath(lensPath, lensPaint);
                lensPaint.setStyle(Paint.Style.STROKE);
                lensPaint.setStrokeWidth(AndroidUtilities.dpf2(3));
                lensPaint.setColor((int) (0x1A * on) << 24);
                canvas.save();
                canvas.clipPath(lensPath);
                canvas.drawPath(lensPath, lensPaint);
                canvas.restore();
                lensPaint.setStyle(Paint.Style.FILL);
                if (lensRimPaint.getShader() == null) {
                    lensRimPaint.setStyle(Paint.Style.STROKE);
                    lensRimPaint.setShader(new LinearGradient(0, 0, 1, 1,
                        new int[]{0xF0FFFFFF, 0x26FFFFFF, 0xC0FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                }
                lensRimMatrix.setScale(lensRect.width(), lensRect.height());
                lensRimMatrix.postTranslate(lensRect.left, lensRect.top);
                lensRimPaint.getShader().setLocalMatrix(lensRimMatrix);
                lensRimPaint.setStrokeWidth(Math.max(1f, AndroidUtilities.dpf2(1.4f)));
                lensRimPaint.setAlpha((int) (230 * on));
                final float inset = lensRimPaint.getStrokeWidth() / 2f;
                lensRect.inset(inset, inset);
                canvas.drawRoundRect(lensRect, r - inset, r - inset, lensRimPaint);
            }
        }
        if (moving) {
            postInvalidateOnAnimation();
        }
    }

    private final android.graphics.Rect lensBarPadding = new android.graphics.Rect();
    private final RectF lensBar = new RectF();
    private float lensFlow;

    private boolean drawShaderLens(Canvas canvas, float cx, float cy, float w, float h, float on) {
        if (lensShaderFailed || Build.VERSION.SDK_INT < 33 || !canvas.isHardwareAccelerated() || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        try {
            if (lensShader == null) {
                lensShader = new LensShader();
            }
            final android.graphics.drawable.Drawable bg = getBackground();
            lensBarPadding.setEmpty();
            if (bg != null) {
                bg.getPadding(lensBarPadding);
            }
            lensBar.set(lensBarPadding.left, lensBarPadding.top, getWidth() - lensBarPadding.right, getHeight() - lensBarPadding.bottom);
            if (lensBar.width() <= 0 || lensBar.height() <= 0 || solegramLensBackdrop != null) {
                lensBar.set(0, 0, getWidth(), getHeight());
            }
            ((LensShader) lensShader).draw(canvas, getWidth(), getHeight(), cx, cy, w, h, on, lensFlow, lensBar, lensContent);
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            lensShaderFailed = true;
            lensShader = null;
            return false;
        }
    }

    @RequiresApi(33)
    private static final class LensShader {
        private final RenderNode node = new RenderNode("solegramTabLens");
        private final RuntimeShader shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.solegram_tab_lens_shader));

        private RenderEffect effect;
        private final float[] last = new float[10];
        private boolean lastDark;
        private int lastDispersion = -1;
        private int lastVersion = -1;

        void draw(Canvas canvas, int viewW, int viewH, float cx, float cy, float w, float h, float a, float flow, RectF bar, Utilities.Callback<Canvas> content) {
            node.setPosition(0, 0, viewW, viewH);
            final RecordingCanvas rc = node.beginRecording(viewW, viewH);
            try {
                content.run(rc);
            } finally {
                node.endRecording();
            }
            final boolean dark = Theme.isCurrentThemeDark();
            final int dispersion = app.exteraless.glass.LiquidGlassConfig.glassDispersion;
            final int configVersion = app.exteraless.glass.LiquidGlassConfig.version;
            if (effect == null || last[0] != cx || last[1] != cy || last[2] != w || last[3] != h || last[4] != a || lastDark != dark
                || last[5] != bar.left || last[6] != bar.top || last[7] != bar.right || last[8] != bar.bottom
                || last[9] != flow || lastDispersion != dispersion || lastVersion != configVersion) {
                lastDark = dark;
                lastDispersion = dispersion;
                lastVersion = configVersion;
                last[0] = cx;
                last[1] = cy;
                last[2] = w;
                last[3] = h;
                last[4] = a;
                last[5] = bar.left;
                last[6] = bar.top;
                last[7] = bar.right;
                last[8] = bar.bottom;
                last[9] = flow;
                final float thickScale = app.exteraless.glass.LiquidGlassConfig.glassThickness / 100f;
                final float refractScale = app.exteraless.glass.LiquidGlassConfig.glassRefraction / 100f;
                final float rimScale = app.exteraless.glass.LiquidGlassConfig.glassRim / 100f;
                final float band = Math.min(w, h) / 2f * 0.45f * thickScale * (1f + 0.08f * flow);
                shader.setFloatUniform("center", cx, cy);
                shader.setFloatUniform("halfSize", w / 2f, h / 2f);
                shader.setFloatUniform("magnify", 1f + (0.15f + 0.03f * flow) * refractScale * a);
                shader.setFloatUniform("band", band);
                shader.setFloatUniform("bend", band * (0.6f + 0.06f * flow) * refractScale * a);
                shader.setFloatUniform("dispersion", (0.6f + 0.6f * dispersion / 100f) * (1f + 0.25f * flow) * a);
                shader.setFloatUniform("light", 0.9f * rimScale * a);
                shader.setFloatUniform("shade", 0.16f * a);
                shader.setFloatUniform("dp", AndroidUtilities.density);
                shader.setFloatUniform("barCenter", bar.centerX(), bar.centerY());
                shader.setFloatUniform("barHalf", bar.width() / 2f, bar.height() / 2f);
                shader.setFloatUniform("dark", dark ? 1f : 0f);
                final int customTint = app.exteraless.glass.LiquidGlassConfig.isLiquidGlassEnabled()
                    ? app.exteraless.glass.SolegramGlass.tintColor()
                    : 0;
                if (customTint != 0 && app.exteraless.glass.LiquidGlassConfig.glassTintAmount > 0) {
                    final float norm = Math.min(1f, app.exteraless.glass.LiquidGlassConfig.glassTintAmount / 60f);
                    final float tintA = (0.08f + 0.24f * norm) * a;
                    final float tr = android.graphics.Color.red(customTint) / 255f * tintA;
                    final float tg = android.graphics.Color.green(customTint) / 255f * tintA;
                    final float tb = android.graphics.Color.blue(customTint) / 255f * tintA;
                    shader.setFloatUniform("tint", tr, tg, tb, tintA);
                } else {
                    final float body = (dark ? 0.045f : 0.06f) * a;
                    shader.setFloatUniform("tint", body, body, body, body);
                }
                effect = RenderEffect.createRuntimeShaderEffect(shader, "img");
                node.setRenderEffect(effect);
            }
            canvas.drawRenderNode(node);
        }
    }


    private static float clampXToChildrenCenters(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return x;
        }

        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        boolean found = false;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = getCenterX(view);

            if (centerX < min) min = centerX;
            if (centerX > max) max = centerX;

            found = true;
        }

        if (!found) {
            return x;
        }

        if (x < min) return min;
        if (x > max) return max;
        return x;
    }

    @Nullable
    private static View findNearestVisibleChildByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return null;
        }

        View nearest = null;
        float nearestDistance = Float.MAX_VALUE;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = getCenterX(view);
            float distance = Math.abs(centerX - x);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = view;
            }
        }

        return nearest;
    }

    private static float getInterpolatedWidthByX(float x, ViewGroup parent) {
        if (parent == null || parent.getChildCount() == 0) {
            return 0f;
        }

        View left = null;
        View right = null;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == null || view.getVisibility() != View.VISIBLE) {
                continue;
            }

            float centerX = view.getX() + view.getWidth() * 0.5f;

            if (centerX <= x && (left == null || centerX > getCenterX(left))) {
                left = view;
            }

            if (centerX >= x && (right == null || centerX < getCenterX(right))) {
                right = view;
            }
        }

        if (left == null && right == null) {
            return 0f;
        }

        if (left == null) {
            return right.getWidth();
        }

        if (right == null) {
            return left.getWidth();
        }

        float leftX = getCenterX(left);
        float rightX = getCenterX(right);

        if (left == right || leftX == rightX) {
            return left.getWidth();
        }

        float ratio = (x - leftX) / (rightX - leftX);
        return lerp(left.getWidth(), right.getWidth(), ratio);
    }

    private static float getCenterX(View v) {
        if (v instanceof GlassTabView && ((GlassTabView) v).isMainTabExpressive()) {
            return v.getX() + ((GlassTabView) v).getExpressiveSlotWidth() * 0.5f;
        }
        return v.getX() + v.getWidth() * 0.5f;
    }

    // ---- Material 3 Expressive Floating Toolbar ----

    private int expressiveIconSlotPx;
    private int expressiveGapPx;
    private int expressiveMaxExtraPx;
    private float[] expressiveLabelWidths;

    private final Paint expressiveContainerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expressiveStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint expressivePillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF expressiveCapsuleRect = new RectF();
    private final RectF expressivePillRect = new RectF();
    private boolean expressiveColorsInitialized;
    private boolean expressiveHasInitialPill;
    private float expressivePillLeft;
    private float expressivePillRight;
    private float expressivePillTargetLeft = -1f;
    private float expressivePillTargetRight = -1f;

    private final SpringAnimation expressivePillLeftSpring = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("expressivePillLeft") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.expressivePillLeft;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.expressivePillLeft = value;
            object.invalidate();
        }
    });

    private final SpringAnimation expressivePillRightSpring = new SpringAnimation(this, new FloatPropertyCompat<MainTabsLayout>("expressivePillRight") {
        @Override
        public float getValue(MainTabsLayout object) {
            return object.expressivePillRight;
        }

        @Override
        public void setValue(MainTabsLayout object, float value) {
            object.expressivePillRight = value;
            object.invalidate();
        }
    });

    {
        expressivePillLeftSpring.setSpring(new SpringForce()
                .setStiffness(340f)
                .setDampingRatio(0.76f));
        expressivePillRightSpring.setSpring(new SpringForce()
                .setStiffness(340f)
                .setDampingRatio(0.76f));
        expressiveStrokePaint.setStyle(Paint.Style.STROKE);
    }

    public void updateExpressiveColors() {
        final int[] colors = new int[4];
        MainTabsUiHelper.resolveExpressiveColors(resourcesProvider, colors);
        expressiveContainerPaint.setColor(colors[0]);
        expressivePillPaint.setColor(colors[1]);
        expressiveStrokePaint.setStrokeWidth(AndroidUtilities.dpf2(0.6f));
        expressiveStrokePaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(colors[3], 28));
        final int shadowColor = Theme.isCurrentThemeDark() ? 0x55000000 : 0x26000000;
        if (Build.VERSION.SDK_INT >= 28) {
            expressiveContainerPaint.setShadowLayer(dp(8), 0, dp(2), shadowColor);
        }
        expressiveColorsInitialized = true;
        invalidate();
    }

    private float expressiveFabLeftInsetPx;
    private float expressiveFabRightInsetPx;

    public void setExpressiveFabInsets(float leftInsetPx, float rightInsetPx) {
        final float clampedLeft = Math.max(0f, leftInsetPx);
        final float clampedRight = Math.max(0f, rightInsetPx);
        if (Math.abs(expressiveFabLeftInsetPx - clampedLeft) > 0.25f
                || Math.abs(expressiveFabRightInsetPx - clampedRight) > 0.25f) {
            expressiveFabLeftInsetPx = clampedLeft;
            expressiveFabRightInsetPx = clampedRight;
            updateExpressivePositions(false);
            invalidate();
        }
    }

    public float getExpressiveCapsuleWidth() {
        return expressiveCapsuleRect.isEmpty() ? getMeasuredWidth() : expressiveCapsuleRect.width();
    }

    private void measureExpressiveToolbar(int widthMeasureSpec, int heightMeasureSpec) {
        final int fullW = MeasureSpec.getSize(widthMeasureSpec);
        final int fabReserve = dp(20 + AppearanceConfig.fabSize() + 8) - dp(12);
        final int availW = Math.max(dp(180), fullW - Math.max(0, fabReserve));
        final int height = MeasureSpec.getSize(heightMeasureSpec);
        final int tabHeight = Math.max(0, height - getPaddingTop() - getPaddingBottom());
        final int childCount = getChildCount();

        if (expressiveLabelWidths == null || expressiveLabelWidths.length < childCount) {
            expressiveLabelWidths = new float[childCount];
        }

        int visibleCount = 0;
        int maxTextPx = 0;
        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                expressiveLabelWidths[a] = 0f;
                continue;
            }
            float w = 0f;
            if (child instanceof GlassTabView) {
                w = ((GlassTabView) child).measureTextWidth(13f);
            }
            expressiveLabelWidths[a] = w;
            maxTextPx = Math.max(maxTextPx, (int) Math.ceil(w));
            visibleCount++;
        }
        visibleChildCount = visibleCount;

        if (visibleCount == 0) {
            setMeasuredDimension(0, height);
            calculateTotalSizesAfterMeasure();
            return;
        }

        final int padH = getPaddingLeft() + getPaddingRight();
        int iconSlot = visibleCount <= 3 ? dp(62) : (visibleCount == 4 ? dp(54) : dp(44));
        int gap = visibleCount <= 3 ? dp(8) : (visibleCount == 4 ? dp(6) : dp(3));
        int minSum = padH + visibleCount * iconSlot + Math.max(0, visibleCount - 1) * gap;
        final int pillPadDiff = Math.max(0, dp(66) - iconSlot);
        final int desiredMaxExtra = maxTextPx > 0 ? pillPadDiff + maxTextPx + dp(6) : 0;

        if (minSum + desiredMaxExtra > availW) {
            // Если не помещается целиком, аккуратно ужимаем слоты иконки перед тем как резать текст
            final int neededSlots = availW - padH - Math.max(0, visibleCount - 1) * gap - desiredMaxExtra;
            if (neededSlots > 0 && visibleCount > 0) {
                iconSlot = Math.max(dp(38), neededSlots / visibleCount);
            }
            gap = Math.min(gap, dp(3));
            minSum = padH + visibleCount * iconSlot + Math.max(0, visibleCount - 1) * gap;
        } else if (minSum + desiredMaxExtra < availW && visibleCount > 0) {
            // Expand icon slots (and gap if needed) so the toolbar fills availW and sits tightly next to the FAB
            final int textExtra = maxTextPx > 0 ? maxTextPx + dp(6) : 0;
            final int gapTotal = Math.max(0, visibleCount - 1) * gap;
            final int targetSlotsTotal = availW - padH - gapTotal - textExtra;
            int expandedSlot = targetSlotsTotal / visibleCount;
            if (expandedSlot < dp(66) && visibleCount > 1) {
                expandedSlot = (targetSlotsTotal - dp(66)) / (visibleCount - 1);
            }
            if (expandedSlot > iconSlot) {
                final int maxSlot = visibleCount > 1 ? dp(88) : expandedSlot;
                iconSlot = Math.min(expandedSlot, maxSlot);
                final int newPillPad = Math.max(0, dp(66) - iconSlot);
                final int used = padH + visibleCount * iconSlot + gapTotal + newPillPad + textExtra;
                if (used < availW && visibleCount > 1) {
                    gap += (availW - used) / (visibleCount - 1);
                }
                minSum = padH + visibleCount * iconSlot + Math.max(0, visibleCount - 1) * gap;
            }
        }
        expressiveIconSlotPx = iconSlot;
        expressiveGapPx = gap;

        final int newPillPadDiff = Math.max(0, dp(66) - iconSlot);
        final int newDesiredExtra = maxTextPx > 0 ? newPillPadDiff + maxTextPx + dp(6) : 0;
        final int allowedMaxExtra = Math.max(0, Math.min(newDesiredExtra, availW - minSum));
        expressiveMaxExtraPx = allowedMaxExtra;

        setMeasuredDimension(fullW, height);

        final int maxChildSlot = visibleCount > 0 ? Math.max(iconSlot, (fullW - padH) / visibleCount) : iconSlot;
        final boolean unconstrainedText = (minSum + newDesiredExtra) <= availW;
        final int maxTextWidthAllowed = unconstrainedText ? 0 : Math.max(dp(24), allowedMaxExtra - newPillPadDiff);
        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                child.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
                continue;
            }
            final int extra = expressiveLabelWidths[a] > 0
                    ? Math.min(allowedMaxExtra, newPillPadDiff + (int) Math.ceil(expressiveLabelWidths[a]) + dp(6))
                    : 0;
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setExpressiveTextMaxWidth(maxTextWidthAllowed);
            }
            child.measure(
                    MeasureSpec.makeMeasureSpec(maxChildSlot + extra, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(tabHeight, MeasureSpec.EXACTLY));
        }

        calculateTotalSizesAfterMeasure();
    }

    private void updateExpressivePositions(boolean animated) {
        final int w = getWidth();
        if (w <= 0) {
            return;
        }
        final int childCount = getChildCount();
        final int iconSlot = expressiveIconSlotPx > 0 ? expressiveIconSlotPx : dp(54);
        final int pillPadDiff = Math.max(0, dp(66) - iconSlot);
        final int padLeft = getPaddingLeft();
        final int padRight = getPaddingRight();

        float curExpandSum = 0f;
        float targetExpandSum = 0f;
        int visibleSeen = 0;
        int selectedIndex = -1;
        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                continue;
            }
            final boolean isSelected = child instanceof GlassTabView && ((GlassTabView) child).isTabSelected();
            final float expand = child instanceof GlassTabView ? ((GlassTabView) child).getExpressiveExpandFactor() : (isSelected ? 1f : 0f);
            final float labelW = (expressiveLabelWidths != null && a < expressiveLabelWidths.length) ? expressiveLabelWidths[a] : 0f;
            final float maxExtra = labelW > 0 ? Math.min(expressiveMaxExtraPx, pillPadDiff + (float) Math.ceil(labelW) + dp(6)) : 0f;
            curExpandSum += expand * maxExtra;
            targetExpandSum += isSelected ? maxExtra : 0f;
            if (isSelected) {
                selectedIndex = a;
            }
            visibleSeen++;
        }

        if (visibleSeen == 0) {
            expressiveCapsuleRect.setEmpty();
            return;
        }

        final float cLeft = Math.max(0f, expressiveFabLeftInsetPx);
        final float cRight = Math.max(cLeft + dp(120), w - Math.max(0f, expressiveFabRightInsetPx));
        expressiveCapsuleRect.set(cLeft, 0f, cRight, getHeight());

        final float innerCapsuleW = Math.max(0f, (cRight - cLeft) - padLeft - padRight);
        final float gap = visibleSeen > 1 ? dp(4) : 0f;
        final float gapsTotal = Math.max(0, visibleSeen - 1) * gap;
        final float curSlotW = Math.max(dp(34), (innerCapsuleW - gapsTotal - curExpandSum) / visibleSeen);
        final float targetSlotW = Math.max(dp(34), (innerCapsuleW - gapsTotal - targetExpandSum) / visibleSeen);

        float x = cLeft + padLeft;
        float xTarget = cLeft + padLeft;
        float newTargetPillLeft = -1f;
        float newTargetPillRight = -1f;

        for (int a = 0; a < childCount; a++) {
            final View child = getChildAt(a);
            if (!isViewVisible(child)) {
                continue;
            }
            final boolean isSelected = a == selectedIndex;
            final float expand = child instanceof GlassTabView ? ((GlassTabView) child).getExpressiveExpandFactor() : (isSelected ? 1f : 0f);
            final float labelW = (expressiveLabelWidths != null && a < expressiveLabelWidths.length) ? expressiveLabelWidths[a] : 0f;
            final float maxExtra = labelW > 0 ? Math.min(expressiveMaxExtraPx, pillPadDiff + (float) Math.ceil(labelW) + dp(6)) : 0f;
            final float curW = curSlotW + expand * maxExtra;
            final float targetW = targetSlotW + (isSelected ? maxExtra : 0f);

            child.setTranslationX(x - child.getLeft());
            if (child instanceof GlassTabView) {
                ((GlassTabView) child).setExpressiveLayoutState(curW, expand);
            }

            if (isSelected) {
                newTargetPillLeft = xTarget;
                newTargetPillRight = xTarget + targetW;
            }

            x += curW + gap;
            xTarget += targetW + gap;
        }

        if (newTargetPillLeft >= 0f && newTargetPillRight > newTargetPillLeft) {
            if (!expressiveHasInitialPill || (!animated && !expressivePillLeftSpring.isRunning() && !expressivePillRightSpring.isRunning())) {
                expressivePillLeftSpring.cancel();
                expressivePillRightSpring.cancel();
                expressivePillLeft = newTargetPillLeft;
                expressivePillRight = newTargetPillRight;
                expressivePillTargetLeft = newTargetPillLeft;
                expressivePillTargetRight = newTargetPillRight;
                expressiveHasInitialPill = true;
            } else if (Math.abs(expressivePillTargetLeft - newTargetPillLeft) > 0.5f
                    || Math.abs(expressivePillTargetRight - newTargetPillRight) > 0.5f) {
                expressivePillTargetLeft = newTargetPillLeft;
                expressivePillTargetRight = newTargetPillRight;
                expressivePillLeftSpring.animateToFinalPosition(newTargetPillLeft);
                expressivePillRightSpring.animateToFinalPosition(newTargetPillRight);
            }
        }
    }

    private void drawExpressiveToolbar(@NonNull Canvas canvas) {
        if (!expressiveColorsInitialized) {
            updateExpressiveColors();
        }
        updateExpressivePositions(true);

        if (!expressiveCapsuleRect.isEmpty()) {
            final float r = expressiveCapsuleRect.height() / 2f;
            canvas.drawRoundRect(expressiveCapsuleRect, r, r, expressiveContainerPaint);
            final float strokeInset = expressiveStrokePaint.getStrokeWidth() / 2f;
            expressiveCapsuleRect.inset(strokeInset, strokeInset);
            canvas.drawRoundRect(expressiveCapsuleRect, r - strokeInset, r - strokeInset, expressiveStrokePaint);
            expressiveCapsuleRect.inset(-strokeInset, -strokeInset);
        }

        if (expressiveHasInitialPill && expressivePillRight > expressivePillLeft) {
            final float top = getPaddingTop();
            final float bottom = getHeight() - getPaddingBottom();
            final float left = Math.max(expressiveCapsuleRect.left + getPaddingLeft(), expressivePillLeft);
            final float right = Math.min(expressiveCapsuleRect.right - getPaddingRight(), expressivePillRight);
            if (right > left && bottom > top) {
                expressivePillRect.set(left, top, right, bottom);
                final float r = expressivePillRect.height() / 2f;
                canvas.drawRoundRect(expressivePillRect, r, r, expressivePillPaint);
            }
        }

        super.dispatchDraw(canvas);

        if (speedDialDimProgress > 0.001f && !expressiveCapsuleRect.isEmpty()) {
            speedDialDimPaint.setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(android.graphics.Color.BLACK, Math.round(140f * speedDialDimProgress)));
            final float r = expressiveCapsuleRect.height() / 2f;
            canvas.drawRoundRect(expressiveCapsuleRect, r, r, speedDialDimPaint);
        }
    }
}
