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
            case FAB_COLOR_MONET_PRIMARY:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("primary", defaultColor);
            case FAB_COLOR_MONET_PRIMARY_CONTAINER:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("primary_container", defaultColor);
            case FAB_COLOR_MONET_SECONDARY:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("secondary", defaultColor);
            case FAB_COLOR_MONET_SECONDARY_CONTAINER:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("secondary_container", defaultColor);
            case FAB_COLOR_MONET_TERTIARY:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("tertiary", defaultColor);
            case FAB_COLOR_MONET_TERTIARY_CONTAINER:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("tertiary_container", defaultColor);
            case FAB_COLOR_MONET_SURFACE:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("surface_container_high", defaultColor);
            default:
                return code;
        }
    }

    public static int resolveFabIconColor(int bgColCode, int iconColCode, int defaultIconColor) {
        if (iconColCode != 0) {
            return resolveFabColor(iconColCode, defaultIconColor);
        }
        switch (bgColCode) {
            case FAB_COLOR_MONET_PRIMARY:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("on_primary", defaultIconColor);
            case FAB_COLOR_MONET_PRIMARY_CONTAINER:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("on_primary_container", defaultIconColor);
            case FAB_COLOR_MONET_SECONDARY:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("on_secondary", defaultIconColor);
            case FAB_COLOR_MONET_SECONDARY_CONTAINER:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("on_secondary_container", defaultIconColor);
            case FAB_COLOR_MONET_TERTIARY:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("on_tertiary", defaultIconColor);
            case FAB_COLOR_MONET_TERTIARY_CONTAINER:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("on_tertiary_container", defaultIconColor);
            case FAB_COLOR_MONET_SURFACE:
                return app.exteraless.appearance.AppearanceConfig.resolveThemeToken("on_surface", defaultIconColor);
            case FAB_COLOR_DEFAULT: {
                if (defaultIconColor != 0) {
                    return defaultIconColor;
                }
                int defaultBg = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton);
                return getOnAccentColor(defaultBg);
            }
            default: {
                if (bgColCode != 0) {
                    return getOnAccentColor(bgColCode);
                }
                if (defaultIconColor != 0) {
                    return defaultIconColor;
                }
                int defaultBg = org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton);
                return getOnAccentColor(defaultBg);
            }
        }
    }

    public static int getOnAccentColor(int color) {
        float[] hsl = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl);
        double lum = androidx.core.graphics.ColorUtils.calculateLuminance(color);
        boolean isLight = lum > 0.28 || hsl[2] >= 0.48f;

        if (isLight) {
            // Light / pastel container -> deep rich saturated tone of the same hue
            if (hsl[1] < 0.08f) {
                return 0xFF1C1B1F;
            }
            float outH = hsl[0];
            float outS = Math.min(1.0f, Math.max(0.78f, hsl[1] * 1.15f));
            float outL = 0.18f;
            int candidate = androidx.core.graphics.ColorUtils.HSLToColor(new float[]{outH, outS, outL});
            if (androidx.core.graphics.ColorUtils.calculateContrast(candidate, color) < 4.2) {
                candidate = androidx.core.graphics.ColorUtils.HSLToColor(new float[]{outH, outS, 0.12f});
            }
            return candidate;
        } else {
            // Dark background -> luminous soft pastel tint of the same hue
            if (hsl[1] < 0.08f) {
                return 0xFFF1F0F4;
            }
            float outH = hsl[0];
            float outS = Math.min(1.0f, Math.max(0.38f, hsl[1] * 0.85f));
            float outL = 0.88f;
            int candidate = androidx.core.graphics.ColorUtils.HSLToColor(new float[]{outH, outS, outL});
            if (androidx.core.graphics.ColorUtils.calculateContrast(candidate, color) < 4.2) {
                candidate = androidx.core.graphics.ColorUtils.HSLToColor(new float[]{outH, outS, 0.94f});
            }
            return candidate;
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
