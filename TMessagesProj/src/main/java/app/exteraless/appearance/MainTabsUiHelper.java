package app.exteraless.appearance;

import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import app.exteraless.player.PlayerColors;
import me.vkryl.android.animator.BoolAnimator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.MainTabsLayout;

import tw.nekomimi.nekogram.helpers.MainTabsHelper;

/**
 * Material 3 для нижней панели вкладок.
 *
 * Размеры не-M3 варианта берутся из MainTabsHelper (в NagramX есть компактный режим
 * без подписей), поэтому «старая» ветка возвращает текущие значения форка, а не
 * зашитые 72/28/7.666 — при выключенном флаге поведение остаётся прежним.
 */
public final class MainTabsUiHelper {

    private MainTabsUiHelper() {
    }

    public static final int EXPRESSIVE_TOOLBAR_HEIGHT_DP = 62;
    public static final int EXPRESSIVE_TOOLBAR_BOTTOM_MARGIN_DP = 12;

    public static boolean isExpressiveToolbar() {
        return MainTabsLayout.isBottomNavigationExpressive();
    }

    public static boolean isMaterial3NavigationBar() {
        return AppearanceConfig.newNavigationBarStyle() && !MainTabsLayout.isBottomNavigationFloating() && !isExpressiveToolbar();
    }

    /** MD3-навигация при включённом Liquid Glass: плавающая стеклянная капсула с MD3-индикатором внутри. */
    public static boolean isMaterial3FloatingGlass() {
        return isMaterial3NavigationBar() && app.exteraless.glass.LiquidGlassConfig.isLiquidGlassEnabled();
    }

    /**
     * Панель как в Telegram iOS: капсула почти во всю ширину экрана, 60dp высотой,
     * вкладки растянуты по ширине. Это та же не-M3 ветка, только шире и выше,
     * поэтому все размеры считаются через MainTabsHelper. M3 приоритетнее.
     */
    public static boolean isIosNavigationBar() {
        return AppearanceConfig.iosNavigationBarStyle() && !isMaterial3NavigationBar() && !isExpressiveToolbar();
    }

    /** В iOS-стиле и MD3+LiquidGlass обёртка добавляет к системным инсетам 8dp по бокам. */
    private static int getWrapperSideInset() {
        return isIosNavigationBar() || isMaterial3FloatingGlass() ? AndroidUtilities.dp(8) : 0;
    }

    /** M3 — 64dp (при Liquid Glass — с плавающими отступами), иначе высота из MainTabsHelper. */
    public static int getTabsViewHeightDp() {
        if (isExpressiveToolbar()) {
            return EXPRESSIVE_TOOLBAR_HEIGHT_DP + EXPRESSIVE_TOOLBAR_BOTTOM_MARGIN_DP + 8;
        }
        if (isMaterial3FloatingGlass()) {
            return 64 + MainTabsHelper.getMainTabsMargin() * 2;
        }
        return isMaterial3NavigationBar() ? 64 : MainTabsHelper.getMainTabsHeightWithMargins();
    }

    /** M3 — dp(64) плюс системный отступ снизу, при Liquid Glass — плавающая капсула. */
    public static int getTabsViewHeight(int bottomInset) {
        if (isExpressiveToolbar()) {
            return AndroidUtilities.dp(EXPRESSIVE_TOOLBAR_HEIGHT_DP);
        }
        if (isMaterial3FloatingGlass()) {
            return AndroidUtilities.dp(64 + MainTabsHelper.getMainTabsMargin() * 2);
        }
        return isMaterial3NavigationBar()
                ? AndroidUtilities.dp(64) + bottomInset
                : AndroidUtilities.dp(MainTabsHelper.getMainTabsHeightWithMargins());
    }

    /** В iOS-стиле капсула на 4dp выше, но таб внутри остаётся 48dp — лишнее уходит в паддинг. */
    public static int getTabsInnerPaddingVertical() {
        if (isExpressiveToolbar()) {
            return AndroidUtilities.dp(7);
        }
        if (isMaterial3FloatingGlass()) {
            return AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin());
        }
        if (isMaterial3NavigationBar()) {
            return 0;
        }
        return AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin() + (isIosNavigationBar() ? 6 : 4));
    }

    public static int getTabsInnerPaddingHorizontal() {
        if (isExpressiveToolbar()) {
            return AndroidUtilities.dp(10);
        }
        if (isMaterial3FloatingGlass()) {
            return AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin());
        }
        return isMaterial3NavigationBar() ? 0 : AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin() + 4);
    }

    /** В M3 без Liquid Glass подложка без отступа от краёв; при Liquid Glass — плавающая капсула. */
    public static int getBackgroundInset() {
        if (isExpressiveToolbar()) {
            return 0;
        }
        if (isMaterial3FloatingGlass()) {
            return AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin() - 0.334f);
        }
        return isMaterial3NavigationBar() ? 0 : AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin() - 0.334f);
    }

    /** В M3 без Liquid Glass подложка прямоугольная, иначе скругление в половину высоты. */
    public static float getBackgroundRadius() {
        if (isExpressiveToolbar()) {
            return AndroidUtilities.dp(EXPRESSIVE_TOOLBAR_HEIGHT_DP / 2f);
        }
        if (isMaterial3FloatingGlass()) {
            return AndroidUtilities.dp(32f);
        }
        return isMaterial3NavigationBar() ? 0 : AndroidUtilities.dp(MainTabsHelper.getMainTabsHeight() / 2f);
    }

    /** В M3 и iOS-стиле панель растянута на всю ширину. */
    public static int getTabsViewWidth() {
        if (isExpressiveToolbar()) {
            return LayoutHelper.WRAP_CONTENT;
        }
        return isMaterial3NavigationBar() || isIosNavigationBar() ? LayoutHelper.MATCH_PARENT : MainTabsHelper.getTabsViewWidth();
    }

    /** Сдвиг кнопки «написать» над панелью: в M3 64 (или 64 + margin в плавающей капсуле). */
    public static int getTabsFabOffsetDp() {
        if (isExpressiveToolbar()) {
            // Капсула стоит в 12dp над navBar и имеет высоту 62dp (центр на 43dp).
            // FAB имеет default marginBottom 14dp и размер fabSize (центр на 14 + fabSize/2).
            return 29 - AppearanceConfig.fabSize() / 2;
        }
        if (isMaterial3FloatingGlass()) {
            return 64 + MainTabsHelper.getMainTabsMargin();
        }
        return isMaterial3NavigationBar() ? 64 : MainTabsHelper.getMainTabsHeight() + MainTabsHelper.getMainTabsMargin();
    }

    /** В M3 и iOS-стиле панель во всю ширину. */
    public static void applyTabsLayoutStyle(MainTabsLayout layout, int legacyMaxWidthPx) {
        final int paddingH = getTabsInnerPaddingHorizontal();
        final int paddingV = getTabsInnerPaddingVertical();
        layout.setPadding(paddingH, paddingV, paddingH, paddingV);
        if (isExpressiveToolbar()) {
            layout.setMaxWidth(0);
            return;
        }
        layout.setMaxWidth(isMaterial3NavigationBar() || isIosNavigationBar() ? 0 : legacyMaxWidthPx);
    }

    /**
     * В M3 (без плавающего стекла) нижний системный отступ уходит внутрь панели, а обёртка его не держит.
     * В плавающей капсуле (MD3 + Liquid Glass или iOS) нижний отступ держит обёртка.
     */
    public static void applyTabsBottomInset(MainTabsLayout layout, View wrapper, int bottomInset, int leftInset, int rightInset) {
        applyTabsBottomInset(layout, bottomInset);
        if (isExpressiveToolbar()) {
            final int sidePad = AndroidUtilities.dp(12);
            wrapper.setPadding(
                    leftInset + sidePad,
                    0,
                    rightInset + sidePad,
                    bottomInset + AndroidUtilities.dp(EXPRESSIVE_TOOLBAR_BOTTOM_MARGIN_DP)
            );
            if (layout.getLayoutParams() instanceof FrameLayout.LayoutParams flp) {
                final int desiredGravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                if (flp.gravity != desiredGravity || flp.width != LayoutHelper.WRAP_CONTENT) {
                    flp.gravity = desiredGravity;
                    flp.width = LayoutHelper.WRAP_CONTENT;
                    layout.setLayoutParams(flp);
                }
            }
            return;
        }
        final int side = getWrapperSideInset();
        final boolean insetInWrapper = !isMaterial3NavigationBar() || isMaterial3FloatingGlass();
        wrapper.setPadding(leftInset + side, 0, rightInset + side, insetInWrapper ? bottomInset : 0);
        if (layout.getLayoutParams() instanceof FrameLayout.LayoutParams flp) {
            final int desiredWidth = getTabsViewWidth();
            final int desiredGravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            if (flp.gravity != desiredGravity || flp.width != desiredWidth) {
                flp.gravity = desiredGravity;
                flp.width = desiredWidth;
                layout.setLayoutParams(flp);
            }
        }
    }

    public static void applyTabsBottomInset(MainTabsLayout layout, int bottomInset) {
        final int height = getTabsViewHeight(bottomInset);
        final ViewGroup.LayoutParams lp = layout.getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            layout.setLayoutParams(lp);
        }
        final int paddingBottom = getTabsInnerPaddingVertical() + (isMaterial3NavigationBar() && !isMaterial3FloatingGlass() && !isExpressiveToolbar() ? bottomInset : 0);
        if (layout.getPaddingBottom() != paddingBottom) {
            layout.setPadding(layout.getPaddingLeft(), layout.getPaddingTop(), layout.getPaddingRight(), paddingBottom);
        }
    }

    /** В M3 без Liquid Glass обводки нет; в плавающей стеклянной капсуле и не-M3 — стеклянный контур. */
    public static BlurredBackgroundProviderBuilder applyBackgroundStroke(BlurredBackgroundProviderBuilder builder) {
        if (isMaterial3NavigationBar() && !isMaterial3FloatingGlass()) {
            return builder
                    .setStrokeColorTop(0, 0)
                    .setStrokeColorBottom(0, 0)
                    .setStrokeWidth(0, 0);
        }
        return builder
                .setStrokeColorTop(0x11000000, 0x06FFFFFF)
                .setStrokeColorBottom(0x20000000, 0x11FFFFFF)
                .setStrokeWidth(AndroidUtilities.dpf2(0.4f), AndroidUtilities.dpf2(0.4f));
    }

    public static void setBlurBounds(RectF rectF, View view, int bottomInset) {
        final int bottom;
        final int top;
        if (isExpressiveToolbar()) {
            bottom = view.getMeasuredHeight() - bottomInset - AndroidUtilities.dp(EXPRESSIVE_TOOLBAR_BOTTOM_MARGIN_DP);
            top = bottom - AndroidUtilities.dp(EXPRESSIVE_TOOLBAR_HEIGHT_DP);
        } else if (isMaterial3FloatingGlass()) {
            bottom = view.getMeasuredHeight() - bottomInset - AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin());
            top = bottom - AndroidUtilities.dp(64);
        } else if (isMaterial3NavigationBar()) {
            bottom = view.getMeasuredHeight();
            top = bottom - AndroidUtilities.dp(64) - bottomInset;
        } else {
            bottom = view.getMeasuredHeight() - bottomInset - AndroidUtilities.dp(MainTabsHelper.getMainTabsMargin());
            top = bottom - AndroidUtilities.dp(MainTabsHelper.getMainTabsHeight());
        }
        rectF.set(0, top, view.getMeasuredWidth(), bottom);
    }

    /**
     * Токены Monet для Expressive Toolbar (как в Shevery / Material 3 Expressive Vibrant Toolbar):
     * [0] = containerColor (primaryContainer),
     * [1] = activePillColor (primary),
     * [2] = activeContentColor (onPrimary),
     * [3] = inactiveContentColor (onPrimaryContainer).
     */
    public static void resolveExpressiveColors(Theme.ResourcesProvider resourcesProvider, int[] out4) {
        final PlayerColors colors = PlayerColors.fromSeed(
                PlayerColors.noCoverSeed(resourcesProvider),
                PlayerColors.isDark(resourcesProvider));
        out4[0] = colors.primaryContainer;
        out4[1] = colors.primary;
        out4[2] = colors.onPrimary;
        out4[3] = colors.onPrimaryContainer;
    }

    public static void setMainTabSelectedIndicatorBounds(RectF rectF, float width, int height) {
        final float w = Math.min(AndroidUtilities.dp(56), Math.max(0, width - AndroidUtilities.dp(4) * 2));
        final float h = Math.min(AndroidUtilities.dp(32), height);
        final float x = (width - w) / 2f;
        final float y = MainTabsHelper.isMainTabsHideTitleStyle()
                ? (height - h) / 2f
                : AndroidUtilities.dp(6);
        rectF.set(x, y, w + x, h + y);
    }

    public static int getMainTabSelectedIndicatorColor(int color, float factor) {
        return Theme.multAlpha(color, factor * 0.125f);
    }

    public static float getMaterial3MainTabIconTopDp() {
        return 10.0f;
    }

    public static float getMaterial3MainTabAvatarTopDp() {
        return getMaterial3MainTabIconTopDp() + 1.0f;
    }

    public static float getMainTabCounterCenterY(boolean material3, int height) {
        if (!material3) {
            return AndroidUtilities.dpf2(10.0f);
        }
        return MainTabsHelper.isMainTabsHideTitleStyle()
                ? height / 2f - AndroidUtilities.dpf2(6.0f)
                : AndroidUtilities.dp(getMaterial3MainTabIconTopDp() + 6.0f);
    }

    public static float getSelectedBackgroundScaleX(boolean material3, float factor) {
        return AndroidUtilities.lerp(material3 ? 0.4f : 0.6f, 1.0f, factor);
    }

    public static float getSelectedBackgroundScaleY(boolean material3, float factor) {
        return material3 ? 1.0f : getSelectedBackgroundScaleX(false, factor);
    }

    public static void applyMaterial3MainTabStyle(TextView textView, BoolAnimator animator) {
        animator.setDuration(500L);
        animator.setInterpolator(CubicBezierInterpolator.Emphasized);
        textView.setIncludeFontPadding(false);
        textView.setLetterSpacing(0.04166667f);
        textView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16.0f, 49, 0.0f, 42.0f, 0.0f, 0.0f));
    }

    public static void setMaterial3MainTabSelected(BoolAnimator selected, BoolAnimator background, boolean value, boolean animated) {
        selected.setValue(value, animated);
        background.setDuration(value ? 100L : 200L);
        background.setInterpolator(value ? CubicBezierInterpolator.Emphasized : CubicBezierInterpolator.EmphasizedAccelerate);
        background.setValue(value, animated);
    }

    public static Drawable createMainTabsScrimBackground(Theme.ResourcesProvider resourcesProvider, boolean circle) {
        final int color = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        final ShapeDrawable drawable = circle
                ? Theme.createCircleDrawable(AndroidUtilities.dp(40), color)
                : Theme.createRoundRectDrawable(AndroidUtilities.dp(28), color);
        drawable.getPaint().setShadowLayer(AndroidUtilities.dp(6), 0, AndroidUtilities.dp(1), Theme.multAlpha(0xFF000000, 0.15f));
        if (!isMaterial3NavigationBar()) {
            return drawable;
        }
        return new InsetDrawable(drawable, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
    }
}
