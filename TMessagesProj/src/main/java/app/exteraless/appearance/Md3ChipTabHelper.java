package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;

import app.exteraless.utils.UIUtil;

/**
 * Shared styling, color resolution, corner radius morphing, and rendering helper
 * for Material Design 3 chip tab strips (Profile tabs & Search tabs).
 */
public final class Md3ChipTabHelper {

    private Md3ChipTabHelper() {
    }

    public static boolean isEnabled() {
        return AppearanceConfig.chipFoldersEnabled.Bool();
    }

    public static int getSpacingHalfPx() {
        int spacing = AppearanceConfig.chipFoldersSpacing.Int();
        switch (spacing) {
            case 0:
                return Math.max(1, dp(1.5f));
            case 2:
                return dp(5);
            case 3:
                return Math.max(dp(1), dp(AppearanceConfig.chipFoldersCustomSpacing.Int()) / 2);
            case 1:
            default:
                return dp(3);
        }
    }

    @NonNull
    public static float[] getCornerRadii(boolean active, int index, int count) {
        return getCornerRadii(active, index, count, new float[8]);
    }

    @NonNull
    public static float[] getCornerRadii(boolean active, int index, int count, @NonNull float[] out) {
        if (!isEnabled()) {
            fillUniform(out, dp(10));
            return out;
        }
        int shape = AppearanceConfig.chipFoldersShape.Int();
        switch (shape) {
            case 0:
                fillUniform(out, dp(20));
                break;
            case 1:
                fillUniform(out, dp(12));
                break;
            case 2:
                fillUniform(out, dp(8));
                break;
            case 3:
                fillUniform(out, dp(4));
                break;
            case 4:
                fillUniform(out, dp(active ? 20 : 10));
                break;
            case 5:
                if (active) {
                    fillUniform(out, dp(AppearanceConfig.chipFoldersRadiusActive.Int()));
                } else {
                    fillSegmented(out, index, count,
                            dp(AppearanceConfig.chipFoldersRadiusOuter.Int()),
                            dp(AppearanceConfig.chipFoldersRadiusInner.Int()));
                }
                break;
            case 6:
                if (active) {
                    fillUniform(out, dp(AppearanceConfig.chipFoldersRadiusAltActive.Int()));
                } else {
                    fillSegmented(out, index, count,
                            dp(AppearanceConfig.chipFoldersRadiusAltOuter.Int()),
                            dp(AppearanceConfig.chipFoldersRadiusInner.Int()));
                }
                break;
            case 7:
            default:
                fillUniform(out, dp(active
                        ? AppearanceConfig.chipFoldersRadiusCustomActive.Int()
                        : AppearanceConfig.chipFoldersRadiusInactive.Int()));
                break;
        }
        return out;
    }

    public static void lerpCornerRadii(@NonNull float[] from, @NonNull float[] to, float t, @NonNull float[] out) {
        float clamped = Math.max(0f, Math.min(1f, t));
        for (int i = 0; i < 8; i++) {
            out[i] = from[i] + (to[i] - from[i]) * clamped;
        }
    }

    private static void fillUniform(@NonNull float[] out, float r) {
        for (int i = 0; i < 8; i++) {
            out[i] = r;
        }
    }

    private static void fillSegmented(@NonNull float[] out, int index, int count, float outer, float inner) {
        if (count <= 1) {
            fillUniform(out, outer);
        } else if (index <= 0) {
            out[0] = outer; out[1] = outer;
            out[2] = inner; out[3] = inner;
            out[4] = inner; out[5] = inner;
            out[6] = outer; out[7] = outer;
        } else if (index >= count - 1) {
            out[0] = inner; out[1] = inner;
            out[2] = outer; out[3] = outer;
            out[4] = outer; out[5] = outer;
            out[6] = inner; out[7] = inner;
        } else {
            fillUniform(out, inner);
        }
    }

    public static int getActiveFillColor(int activeThemeColor) {
        boolean md3Colors = AppearanceConfig.chipFoldersMd3Colors.Bool();
        int style = AppearanceConfig.chipFoldersStyle.Int();
        if (!md3Colors && AppearanceConfig.chipFoldersColorActive.Int() != 0) {
            return AppearanceConfig.chipFoldersColorActive.Int();
        }
        if (style == 1) {
            return 0;
        }
        int accent = md3Colors ? AppearanceConfig.resolveThemeToken("primary", activeThemeColor) : activeThemeColor;
        if (style == 2) {
            return Theme.multAlpha(accent, 0.18f);
        }
        return accent;
    }

    public static int getInactiveFillColor(int unactiveThemeColor) {
        boolean md3Colors = AppearanceConfig.chipFoldersMd3Colors.Bool();
        int style = AppearanceConfig.chipFoldersStyle.Int();
        if (!md3Colors && AppearanceConfig.chipFoldersColorInactive.Int() != 0) {
            return AppearanceConfig.chipFoldersColorInactive.Int();
        }
        if (style == 1 || style == 2) {
            return 0;
        }
        return md3Colors
                ? AppearanceConfig.resolveThemeToken("surface_container_high", Theme.multAlpha(unactiveThemeColor, 0.14f))
                : Theme.multAlpha(unactiveThemeColor, 0.14f);
    }

    public static int getTextColor(boolean active, int activeThemeColor, int unactiveThemeColor) {
        boolean md3Colors = AppearanceConfig.chipFoldersMd3Colors.Bool();
        int style = AppearanceConfig.chipFoldersStyle.Int();
        if (active) {
            if (!md3Colors && AppearanceConfig.chipFoldersColorTextActive.Int() != 0) {
                return AppearanceConfig.chipFoldersColorTextActive.Int();
            }
            if (style == 0) {
                return UIUtil.getOnAccentColor(getActiveFillColor(activeThemeColor));
            }
            return md3Colors ? AppearanceConfig.resolveThemeToken("primary", activeThemeColor) : activeThemeColor;
        } else {
            if (!md3Colors && AppearanceConfig.chipFoldersColorTextInactive.Int() != 0) {
                return AppearanceConfig.chipFoldersColorTextInactive.Int();
            }
            if (md3Colors && style == 0) {
                return AppearanceConfig.resolveThemeToken("on_surface", unactiveThemeColor);
            }
            return unactiveThemeColor;
        }
    }

    public static void configureSelectorDrawable(
            @NonNull GradientDrawable selectorDrawable,
            int activeThemeColor,
            @NonNull float[] cornerRadii
    ) {
        boolean md3Colors = AppearanceConfig.chipFoldersMd3Colors.Bool();
        int style = AppearanceConfig.chipFoldersStyle.Int();
        selectorDrawable.setCornerRadii(cornerRadii);
        selectorDrawable.setColor(getActiveFillColor(activeThemeColor));
        int strokeColor = md3Colors
                ? AppearanceConfig.resolveThemeToken("primary", activeThemeColor)
                : activeThemeColor;
        selectorDrawable.setStroke(style == 1 ? dp(1.5f) : 0, style == 1 ? strokeColor : 0);
    }

    @NonNull
    public static InsetDrawable createTransparentChipRipple(
            int activeThemeColor,
            @NonNull float[] radii,
            int gapH,
            int vInset
    ) {
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadii(radii);
        mask.setColor(Color.WHITE);
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(Theme.multAlpha(activeThemeColor, 0.16f)),
                null,
                mask
        );
        return new InsetDrawable(ripple, gapH, vInset, gapH, vInset);
    }

    public static float computeOverlapAlpha(float chipLeft, float chipRight, float pillLeft, float pillRight) {
        if (pillRight <= pillLeft || chipRight <= chipLeft) {
            return 1f;
        }
        float overlap = Math.max(0f, Math.min(chipRight, pillRight) - Math.max(chipLeft, pillLeft));
        float width = chipRight - chipLeft;
        float coverage = Math.min(1f, overlap / width);
        return Math.max(0f, Math.min(1f, 1f - coverage));
    }

    public static void drawInactiveChip(
            @NonNull Canvas canvas,
            @NonNull GradientDrawable drawable,
            float left,
            float top,
            float right,
            float bottom,
            int index,
            int count,
            int unactiveThemeColor,
            float alpha,
            @NonNull float[] radiiScratch
    ) {
        if (alpha <= 0.01f || right <= left || bottom <= top) {
            return;
        }
        int style = AppearanceConfig.chipFoldersStyle.Int();
        int fill = getInactiveFillColor(unactiveThemeColor);
        boolean hasFill = Color.alpha(fill) > 0;
        boolean hasStroke = style == 1;
        if (!hasFill && !hasStroke) {
            return;
        }
        getCornerRadii(false, index, count, radiiScratch);
        drawable.setCornerRadii(radiiScratch);
        drawable.setColor(hasFill ? ColorUtils.setAlphaComponent(fill, Math.round(Color.alpha(fill) * alpha)) : 0);
        if (hasStroke) {
            int stroke = Theme.multAlpha(unactiveThemeColor, 0.35f * alpha);
            drawable.setStroke(dp(1.2f), stroke);
        } else {
            drawable.setStroke(0, 0);
        }
        drawable.setBounds(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
        drawable.draw(canvas);
    }
}
