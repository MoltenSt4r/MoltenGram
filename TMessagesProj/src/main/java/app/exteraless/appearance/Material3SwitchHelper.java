package app.exteraless.appearance;

import android.graphics.RectF;
import android.os.Build;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

public final class Material3SwitchHelper {

    private static final float[] ICON_CROSS = {6f, 6f, 18f, 18f, 6f, 18f, 18f, 6f};
    private static final float[] ICON_CHECK = {4.5f, 13.1f, 9f, 17.6f, 9f, 17.6f, 19.8f, 6.8f};
    private static final Interpolator THUMB_INTERPOLATOR = new AccelerateDecelerateInterpolator();
    private static final double[] lab = new double[3];

    private Material3SwitchHelper() {
    }

    private static float scaled(float value) {
        return AndroidUtilities.dpf2(value * 0.8125f);
    }

    public static int stateLayerRadius() {
        return (int) scaled(20f);
    }

    public static void setTrackBounds(RectF rect, int width, int height) {
        float halfWidth = scaled(26f);
        float halfHeight = scaled(16f);
        float cx = width / 2f;
        float cy = height / 2f;
        rect.set(cx - halfWidth, cy - halfHeight, cx + halfWidth, cy + halfHeight);
    }

    public static float trackOutlineWidth() {
        return scaled(2f);
    }

    public static int unselectedTrackColor(int trackKey, Theme.ResourcesProvider resourcesProvider) {
        return trackAtTone(trackKey, resourcesProvider, 90f, 22f);
    }

    public static int unselectedThumbColor(int trackKey, Theme.ResourcesProvider resourcesProvider) {
        int thumb = trackAtTone(trackKey, resourcesProvider, 50f, 60f);
        if (Theme.isCurrentThemeMonet()) {
            return thumb;
        }
        return ColorUtils.blendARGB(trackAtTone(trackKey, resourcesProvider, 90f, 22f), thumb,
                isLightSurface(resourcesProvider) ? 0.55f : 0.8f);
    }

    public static int selectedThumbColor(int thumbCheckedKey, Theme.ResourcesProvider resourcesProvider) {
        if (Build.VERSION.SDK_INT >= 31 && Theme.isCurrentThemeMonet()) {
            return Theme.getColor(Theme.key_switchTrackBlueThumbChecked, resourcesProvider);
        }
        return Theme.getColor(thumbCheckedKey, resourcesProvider);
    }

    private static boolean isLightSurface(Theme.ResourcesProvider resourcesProvider) {
        ColorUtils.colorToLAB(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), lab);
        return lab[0] > 50.0;
    }

    private static int trackAtTone(int key, Theme.ResourcesProvider resourcesProvider, float lightTone, float darkTone) {
        float tone = isLightSurface(resourcesProvider) ? lightTone : darkTone;
        ColorUtils.colorToLAB(Theme.getColor(key, resourcesProvider), lab);
        double chroma = Math.hypot(lab[1], lab[2]);
        if (chroma > 32.0) {
            double k = 32.0 / chroma;
            lab[1] *= k;
            lab[2] *= k;
        }
        return ColorUtils.LABToColor(tone, lab[1], lab[2]);
    }

    public static float thumbCenterX(int width, float progress) {
        return width / 2f + (progress * 2f - 1f) * scaled(10f);
    }

    public static void setThumbBounds(RectF rect, float cx, float cy, float progress, boolean checked, float iconFactor, float pressed) {
        float unselected = AndroidUtilities.lerp(8f, 12f, iconFactor);
        float phase = (float) (Math.acos(1f - Utilities.clamp01(progress) * 2f) / Math.PI);
        if (!checked) {
            phase = 1f - phase;
        }
        float split = checked ? 0.6f : 0.4f;
        float from = checked ? unselected : 12f;
        float to = checked ? 12f : unselected;
        float radius;
        float squash;
        if (phase < split) {
            squash = THUMB_INTERPOLATOR.getInterpolation(phase / split);
            radius = AndroidUtilities.lerp(from, 11f, squash);
        } else {
            float t = THUMB_INTERPOLATOR.getInterpolation((phase - split) / (1f - split));
            radius = AndroidUtilities.lerp(11f, to, t);
            squash = 1f - t;
        }
        float stretch = squash * 5f;
        if (pressed > 0f) {
            radius = AndroidUtilities.lerp(radius, 14f, pressed);
            stretch *= 1f - pressed;
        }
        float r = scaled(radius);
        float s = scaled(stretch);
        rect.set(cx - s - r, cy - r, cx + s + r, cy + r);
    }

    public static float thumbIconScale(int width, int height) {
        return scaled(13.333333f) / Math.max(width, height);
    }

    private static float iconGrid(float value) {
        return scaled(value * 16f / 24f);
    }

    public static float iconStrokeWidth() {
        return iconGrid(2f);
    }

    public static void setCheckIconLines(float[] lines, float cx, float cy, float progress) {
        float t = Utilities.clamp01(progress);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = iconGrid(AndroidUtilities.lerp(ICON_CROSS[i], ICON_CHECK[i], t) - 12f) + (i % 2 == 0 ? cx : cy);
        }
    }

    public static float clockHandLength(boolean longHand) {
        return iconGrid(longHand ? 8.5f : 6.8f);
    }
}
