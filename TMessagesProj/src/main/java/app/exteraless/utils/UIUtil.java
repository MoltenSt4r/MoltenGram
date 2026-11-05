package app.exteraless.utils;

import android.animation.AnimatorSet;
import android.graphics.drawable.Drawable;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * UI-утилиты, перенесённые из exteraGram
 */
public final class UIUtil {

    private UIUtil() {
    }

    /**
     * «Вдавливание» вью при нажатии: 80 мс до {@code 1 - scale}, отпускание — 350 мс
     * с {@link OvershootInterpolator}.
     *
     * exteraGram дополнительно поджимает маску ripple ({@code Theme.RippleRadMaskDrawable}
     * с padding'ом), но у нас {@code RippleRadMaskDrawable} не умеет padding, а
     * {@code Theme.java} правит другой агент — поэтому здесь только масштаб.
     * Визуально основную часть эффекта даёт именно он.
     */
    public static void applyScaleStateListAnimator(View view, float scale, float tension) {
        if (view == null) {
            return;
        }

        final float pressedScale = 1f - scale;

        final AnimatorSet pressed = new AnimatorSet();
        pressed.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, pressedScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, pressedScale)
        );
        pressed.setDuration(80);

        final AnimatorSet released = new AnimatorSet();
        released.playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f)
        );
        released.setInterpolator(new OvershootInterpolator(tension));
        released.setDuration(350);

        final StateListAnimator animator = new StateListAnimator();
        animator.addState(new int[]{android.R.attr.state_pressed}, pressed);
        animator.addState(new int[0], released);

        view.setStateListAnimator(animator);
    }

    public static final int FAB_COLOR_DEFAULT = 0;
    public static final int FAB_COLOR_MONET_PRIMARY = 1;
    public static final int FAB_COLOR_MONET_PRIMARY_CONTAINER = 2;
    public static final int FAB_COLOR_MONET_SECONDARY = 3;
    public static final int FAB_COLOR_MONET_SECONDARY_CONTAINER = 4;
    public static final int FAB_COLOR_MONET_TERTIARY = 5;
    public static final int FAB_COLOR_MONET_TERTIARY_CONTAINER = 6;
    public static final int FAB_COLOR_MONET_SURFACE = 7;

    public static String getFabColorTitle(int col) {
        switch (col) {
            case FAB_COLOR_DEFAULT: return "По умолчанию";
            case FAB_COLOR_MONET_PRIMARY: return "Основной Monet (Primary)";
            case FAB_COLOR_MONET_PRIMARY_CONTAINER: return "Контейнер основного Monet";
            case FAB_COLOR_MONET_SECONDARY: return "Вторичный Monet (Secondary)";
            case FAB_COLOR_MONET_SECONDARY_CONTAINER: return "Контейнер вторичного Monet";
            case FAB_COLOR_MONET_TERTIARY: return "Третичный Monet (Tertiary)";
            case FAB_COLOR_MONET_TERTIARY_CONTAINER: return "Контейнер третичного Monet";
            case FAB_COLOR_MONET_SURFACE: return "Поверхность Monet (Surface)";
            default: return String.format("#%06X", (0xFFFFFF & col));
        }
    }

    public static int resolveFabColor(int code, int defaultColor) {
        switch (code) {
            case FAB_COLOR_DEFAULT:
                return defaultColor;
            case FAB_COLOR_MONET_PRIMARY: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("primary");
                return c != null ? c : defaultColor;
            }
            case FAB_COLOR_MONET_PRIMARY_CONTAINER: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("primary_container");
                return c != null ? c : defaultColor;
            }
            case FAB_COLOR_MONET_SECONDARY: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("secondary");
                return c != null ? c : defaultColor;
            }
            case FAB_COLOR_MONET_SECONDARY_CONTAINER: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("secondary_container");
                return c != null ? c : defaultColor;
            }
            case FAB_COLOR_MONET_TERTIARY: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("tertiary");
                return c != null ? c : defaultColor;
            }
            case FAB_COLOR_MONET_TERTIARY_CONTAINER: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("tertiary_container");
                return c != null ? c : defaultColor;
            }
            case FAB_COLOR_MONET_SURFACE: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("surface_container_high");
                if (c == null) c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("surface");
                return c != null ? c : defaultColor;
            }
            default:
                return code;
        }
    }

    public static int resolveFabIconColor(int bgColCode, int iconColCode, int defaultIconColor) {
        if (iconColCode != 0) {
            return resolveFabColor(iconColCode, defaultIconColor);
        }
        switch (bgColCode) {
            case FAB_COLOR_MONET_PRIMARY: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("on_primary");
                return c != null ? c : defaultIconColor;
            }
            case FAB_COLOR_MONET_PRIMARY_CONTAINER: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("on_primary_container");
                return c != null ? c : defaultIconColor;
            }
            case FAB_COLOR_MONET_SECONDARY: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("on_secondary");
                return c != null ? c : defaultIconColor;
            }
            case FAB_COLOR_MONET_SECONDARY_CONTAINER: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("on_secondary_container");
                return c != null ? c : defaultIconColor;
            }
            case FAB_COLOR_MONET_TERTIARY: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("on_tertiary");
                return c != null ? c : defaultIconColor;
            }
            case FAB_COLOR_MONET_TERTIARY_CONTAINER: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("on_tertiary_container");
                return c != null ? c : defaultIconColor;
            }
            case FAB_COLOR_MONET_SURFACE: {
                Integer c = tw.nekomimi.nekogram.helpers.MonetHelper.getColorOrNull("on_surface");
                return c != null ? c : defaultIconColor;
            }
            default: {
                if (bgColCode < 0) {
                    return getOnAccentColor(bgColCode);
                }
                int defaultBg = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton);
                return getOnAccentColor(defaultBg);
            }
        }
    }

    public static int getOnAccentColor(int color) {
        double contrastWhite = androidx.core.graphics.ColorUtils.calculateContrast(0xFFFFFFFF, color);
        double contrastBlack = androidx.core.graphics.ColorUtils.calculateContrast(0xFF000000, color);
        if (contrastBlack > contrastWhite) {
            return androidx.core.graphics.ColorUtils.blendARGB(color, 0xFF000000, 0.85f);
        } else {
            return 0xFFFFFFFF;
        }
    }

    /**
     * Фон плавающей кнопки. Апстрим всегда рисует круг
     * ({@code Theme.createSimpleSelectorCircleDrawable}); exteraGram при включённом
     * squareFab рисует «squircle» с радиусом ceil(size * 16 / 56) dp
     *
     * @param size сторона кнопки в dp
     */
    public static Drawable createFabSelectorDrawable(int size, int color, int pressedColor) {
        if (size == 40) {
            final int background = org.telegram.ui.ActionBar.Theme.getColor(
                    org.telegram.ui.ActionBar.Theme.key_windowBackgroundWhite);
            color = androidx.core.graphics.ColorUtils.blendARGB(background, 0xFFFFFFFF, 0.1f);
            pressedColor = org.telegram.ui.ActionBar.Theme.blendOver(background,
                    org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_listSelector));
        }
        return org.telegram.ui.ActionBar.Theme.createSimpleSelectorRoundRectDrawable(
                org.telegram.messenger.AndroidUtilities.dp(
                        app.exteraless.appearance.AppearanceConfig.fabCornerRadius(size)),
                color, pressedColor);
    }
}
