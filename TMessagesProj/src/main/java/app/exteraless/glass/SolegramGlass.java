/*
 * This is the source code of Solegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package app.exteraless.glass;

import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;

/**
 * Glass material tuning. Telegram's glass (blur3) asks this class for its parameters, so the
 * "Glass" settings page changes refraction, blur, tint and lighting across the whole app.
 * Every value at its default returns Telegram's original look.
 */
public final class SolegramGlass {

    /** Tint presets: none, theme accent, then Apple HIG system hues. */
    public static final int TINT_NONE = 0;
    public static final int TINT_ACCENT = 1;
    public static final int[] TINT_COLORS = {
        0, 0,
        0xFFFF2D55, // pink
        0xFFAF52DE, // purple
        0xFF5856D6, // indigo
        0xFF007AFF, // blue
        0xFF30B0C7, // teal
        0xFF34C759, // green
        0xFFFF9500, // orange
        0xFFFFCC00, // yellow
        0xFFFFFFFF, // white
        0xFF8E8E93, // grey
        0xFF000000, // black
    };

    /** Edge (bevel) width of liquid glass. */
    public static float thickness(float base) {
        return base * LiquidGlassConfig.glassThickness / 100f;
    }

    /** Upper bound for the bevel so it never eats the whole element. */
    public static float thicknessLimit(float minSide) {
        final float scale = Math.max(1f, LiquidGlassConfig.glassThickness / 100f);
        return Math.min(minSide / 5f * scale, minSide / 2.5f);
    }

    /** How far the content behind the glass is displaced. */
    public static float intensity(float base) {
        return base * LiquidGlassConfig.glassRefraction / 100f;
    }

    /** Refraction index: 150 keeps Telegram's value, 100 is flat glass, 250 is a strong lens. */
    public static float index(float base) {
        if (LiquidGlassConfig.glassIndex == 150) {
            return base;
        }
        return Math.max(1f, 1f + (base - 1f) * (LiquidGlassConfig.glassIndex - 100) / 50f);
    }

    /** Colour split at the edges (0 = off). */
    public static float dispersion() {
        return LiquidGlassConfig.glassDispersion / 200f;
    }

    /** Light along the inner edge of liquid glass (0 = off). */
    public static float rim() {
        return LiquidGlassConfig.glassRim / 100f * 0.35f;
    }

    /** Opacity of the glass tint layer. Telegram uses 0.85 for liquid and 0.76 for frosted glass. */
    public static float alpha(boolean liquid) {
        final float value = LiquidGlassConfig.glassOpacity / 100f;
        return liquid ? value : value * (0.76f / 0.85f);
    }

    /** Multiplier for the blur radius under the glass. */
    public static float blurScale() {
        return Math.max(0.05f, LiquidGlassConfig.glassBlur / 100f);
    }

    public static boolean vibrancy() {
        return LiquidGlassConfig.glassVibrancy;
    }

    /** Glass fill colour with the chosen tint mixed in; the alpha stays as Telegram computed it. */
    public static int tint(int color) {
        return tint(color, null);
    }

    public static int tint(int color, Theme.ResourcesProvider resourcesProvider) {
        final int tint = tintColor(resourcesProvider);
        if (tint == 0 || LiquidGlassConfig.glassTintAmount <= 0) {
            return color;
        }
        // Map the 0..60 tint slider to a vivid, unmistakable tint over both dark and light translucent glass
        final float norm = Math.min(1f, LiquidGlassConfig.glassTintAmount / 60f);
        final float amount = 0.20f + 0.70f * norm;
        final int mixed = ColorUtils.blendARGB(color | 0xFF000000, tint, amount);
        final int origAlpha = Color.alpha(color);
        if (origAlpha == 0 || origAlpha == 255) {
            return ColorUtils.setAlphaComponent(mixed, origAlpha);
        }
        final int minAlpha = Math.round(255f * (0.22f + 0.38f * norm));
        return ColorUtils.setAlphaComponent(mixed, Math.max(origAlpha, minAlpha));
    }

    public static int tintColor() {
        return tintColor(null);
    }

    public static int tintColor(Theme.ResourcesProvider resourcesProvider) {
        final int index = LiquidGlassConfig.glassTint;
        if (index == TINT_ACCENT) {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
            if (accent == 0) {
                accent = Theme.getColor(Theme.key_chats_actionBackground, resourcesProvider);
            }
            if (accent == 0) {
                accent = Theme.getColor(Theme.key_switchTrackChecked, resourcesProvider);
            }
            if (accent == 0) {
                accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourcesProvider);
            }
            return accent != 0 ? (accent | 0xFF000000) : 0;
        }
        if (index > TINT_ACCENT && index < TINT_COLORS.length) {
            return TINT_COLORS[index];
        }
        return 0;
    }

    /**
     * The glass outline. Telegram gives each element an edge of its own - a hairline you can hardly see on the
     * tab bar and in menus, a bright line in the chat - so a plain multiplier changed little where the edge is
     * faint. At 100% the original stays; below it fades out; above it every element goes towards the same clear
     * outline, so the setting looks alike everywhere: a white one for light edges (lighter on the bottom edge),
     * a firmer dark line for the dark edges of light themes (a white line would vanish on light glass).
     *
     * @param top the upper (lit) edge
     */
    public static int highlight(int color, boolean top) {
        final int setting = LiquidGlassConfig.glassHighlight;
        if (color == 0 || setting == 100) {
            return color; // no edge of its own (flat panels) stays without one
        }
        if (setting < 100) {
            return ColorUtils.setAlphaComponent(color, Math.round(Color.alpha(color) * setting / 100f));
        }
        final float boost = Math.min(1f, (setting - 100) / 200f);
        final int alpha = Color.alpha(color);
        final boolean light = ColorUtils.calculateLuminance(color | 0xFF000000) > 0.5;
        final int target = Math.max(alpha, light ? (top ? 0xB8 : 0x70) : (top ? 0x30 : 0x40));
        final int rgb = light ? ColorUtils.blendARGB(color | 0xFF000000, 0xFFFFFFFF, boost) : color;
        return ColorUtils.setAlphaComponent(rgb, Math.round(alpha + (target - alpha) * boost));
    }

    public static int highlight(int color) {
        return highlight(color, true);
    }

    /** The width of the outline: hairlines grow with the outline brightness above 100%, so it shows on every element. */
    public static float highlightWidth(float width, boolean top) {
        final int setting = LiquidGlassConfig.glassHighlight;
        if (setting <= 100 || width <= 0) {
            return width;
        }
        final float boost = Math.min(1f, (setting - 100) / 200f);
        final float target = Math.max(width, org.telegram.messenger.AndroidUtilities.dpf2(top ? 1.2f : 0.9f));
        return width + (target - width) * boost;
    }

    public static int shadow(int color) {
        return LiquidGlassConfig.glassShadow ? color : 0;
    }

    // region Soft floating shadow (the look of the glass preview) for the app's rounded glass

    /** Veil over the blur of frosted headers: lighter than Telegram's, follows the glass density slider. */
    public static int headerScrimAlpha(int telegramAlpha) {
        if (!LiquidGlassConfig.frostedHeaders) {
            return telegramAlpha;
        }
        final float alpha = Math.max(0.35f, Math.min(0.92f, 0.62f * LiquidGlassConfig.glassOpacity / 85f));
        return Math.round(255 * alpha);
    }

    public static boolean previewLook() {
        return LiquidGlassConfig.glassPreviewLook;
    }

    public static int previewShadowColor() {
        return LiquidGlassConfig.glassShadow ? (Theme.isCurrentThemeDark() ? 0x40000000 : 0x2E000000) : 0;
    }
    // endregion

    // region Menus: one glass for every popup menu and glass dialog
    // Telegram's blurred menus (message menu, chat and profile "⋮", ItemOptions such as the tab bar's), Solegram's
    // glass menus, the liquid menus and glass dialogs all take these, so they share density, blur, outline and shadow.
    // Raw colours (constants): the blur3 drawable passes them through tint() / highlight() / shadow() itself; the
    // getters return them processed, for code that draws on its own.

    /** Corners of every popup menu panel: glass, Telegram's blurred menus and the plain panels (popup_fixed_alert*). */
    public static final float MENU_RADIUS_DP = 20f;

    /** {@link #MENU_RADIUS_DP} in px. */
    public static int menuRadius() {
        return org.telegram.messenger.AndroidUtilities.dp(MENU_RADIUS_DP);
    }

    /** The picture under a menu is taken at 1/15 of the screen and blurred there (Telegram's own menu blur at 100%). */
    public static final float MENU_BLUR_DOWNSCALE = 15f;

    // the outline is kept soft (about 70% of Telegram's own strength): a hint of an edge, not a frame
    public static final int MENU_RIM_TOP_LIGHT = 0x8CFFFFFF, MENU_RIM_TOP_DARK = 0x36FFFFFF;
    public static final int MENU_RIM_BOTTOM_LIGHT = 0x24FFFFFF, MENU_RIM_BOTTOM_DARK = 0x0EFFFFFF;
    /** The inner lower edge that keeps light glass apart from light content under it. */
    public static final int MENU_LOW_EDGE_LIGHT = 0x12000000, MENU_LOW_EDGE_DARK = 0x24000000;
    public static final int MENU_SHADOW_LIGHT = 0x24000000, MENU_SHADOW_DARK = 0x4D000000;
    public static final float MENU_SHADOW_RADIUS_DP = 5f, MENU_SHADOW_DY_DP = 1.5f;
    public static final float MENU_RIM_WIDTH_DP = 1f, MENU_EDGE_BOTTOM_WIDTH_DP = 0.8f;

    /** Blur radius of the picture under menus (at {@link #MENU_BLUR_DOWNSCALE}), following the blur slider. */
    public static int menuBlurRadius() {
        return Math.max(2, Math.round(15 * blurScale()));
    }

    /** Colours of the blurred picture under menus: livelier (unless vibrancy is off) and a little deeper. */
    public static android.graphics.ColorMatrix menuBackdropMatrix(boolean dark) {
        final android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix();
        if (vibrancy()) {
            matrix.setSaturation(dark ? 2f : 3f);
        }
        org.telegram.messenger.AndroidUtilities.adjustBrightnessColorMatrix(matrix, dark ? -.2f : -.07f);
        return matrix;
    }

    /** Density of menu glass: Telegram's own (0.85 dark, 0.825 light) at the default density, then the slider's. */
    public static float menuAlpha(boolean dark) {
        return Math.max(0.3f, Math.min(1f, LiquidGlassConfig.glassOpacity / 100f * (dark ? 1f : 0.97f)));
    }

    /** The menu colour as glass (raw: the tint is not mixed in yet). */
    public static int menuFill(int menuColor, boolean dark) {
        return ColorUtils.setAlphaComponent(menuColor, Math.round(menuAlpha(dark) * Color.alpha(menuColor)));
    }

    /** Lower edge of an outline drawn in two parts: a dark line on light glass, a faint light one on dark glass. */
    public static int menuEdgeBottomRaw(boolean dark) {
        return dark ? MENU_RIM_BOTTOM_DARK : MENU_LOW_EDGE_LIGHT;
    }

    public static int menuRimTop(boolean dark) {
        return highlight(dark ? MENU_RIM_TOP_DARK : MENU_RIM_TOP_LIGHT, true);
    }

    public static int menuRimBottom(boolean dark) {
        return highlight(dark ? MENU_RIM_BOTTOM_DARK : MENU_RIM_BOTTOM_LIGHT, false);
    }

    public static int menuEdgeBottom(boolean dark) {
        return highlight(menuEdgeBottomRaw(dark), false);
    }

    public static int menuLowEdge(boolean dark) {
        return dark ? MENU_LOW_EDGE_DARK : MENU_LOW_EDGE_LIGHT;
    }

    public static int menuShadow(boolean dark) {
        return shadow(dark ? MENU_SHADOW_DARK : MENU_SHADOW_LIGHT);
    }

    /**
     * Density of a glass sheet meant for reading (update notes, a plugin's security report): medium, denser than
     * plain glass so the text reads over anything behind, yet clearly frosted. Follows the density slider above that floor.
     */
    public static float readingAlpha(boolean dark) {
        final float base = Math.max(0f, Math.min(1f, LiquidGlassConfig.glassOpacity / 100f));
        return Math.max(dark ? 0.875f : 0.86f, base + (1f - base) * 0.2f);
    }
    // endregion

    private SolegramGlass() {
    }
}
