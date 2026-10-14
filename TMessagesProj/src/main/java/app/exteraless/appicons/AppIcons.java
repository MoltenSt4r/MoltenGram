package app.exteraless.appicons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LauncherIconController;
import org.telegram.ui.LauncherIconController.LauncherIcon;

import java.util.ArrayList;
import java.util.HashMap;

public final class AppIcons {

    private static final HashMap<String, Integer> DESCRIPTIONS = new HashMap<>();
    private static final HashMap<LauncherIcon, Integer> accents = new HashMap<>();
    private static volatile LauncherIcon pending;

    static {
    }

    private AppIcons() {
    }

    public static CharSequence title(LauncherIcon icon) {
        return LocaleController.getString(icon.title);
    }

    public static CharSequence description(LauncherIcon icon) {
        Integer res = DESCRIPTIONS.get(icon.name());
        if (res != null) {
            String str = LocaleController.getString(res);
            if (!TextUtils.isEmpty(str)) {
                return str;
            }
        }
        return icon.description;
    }

    public static boolean hasDescriptions() {
        for (int res : DESCRIPTIONS.values()) {
            if (!TextUtils.isEmpty(LocaleController.getString(res))) {
                return true;
            }
        }
        return false;
    }

    public static ArrayList<LauncherIcon> available(int account) {
        boolean blocked = MessagesController.getInstance(account).premiumFeaturesBlocked();
        ArrayList<LauncherIcon> icons = new ArrayList<>();
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (!icon.premium || !blocked || LauncherIconController.isEnabled(icon)) {
                icons.add(icon);
            }
        }
        return icons;
    }

    public static boolean locked(LauncherIcon icon) {
        return icon.premium && !UserConfig.hasPremiumOnAccounts();
    }

    public static LauncherIcon current() {
        LauncherIcon applying = pending;
        if (applying != null) {
            return applying;
        }
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (LauncherIconController.isEnabled(icon)) {
                return icon;
            }
        }
        return LauncherIcon.MOLTENGRAM;
    }

    public static void apply(LauncherIcon icon) {
        pending = icon;
        Utilities.globalQueue.postRunnable(() -> {
            LauncherIconController.setIcon(icon);
            if (pending == icon) {
                pending = null;
            }
        });
    }

    // Для иконок с 1:1 фоном (полное покрытие, без safe-zone) рисуем без bleed точно в круг.
    // Для адаптивных иконок масштабируем безопасную зону (72dp -> 108dp) с отступом bleed = 0.25f.
    public static void draw(Canvas canvas, Drawable background, Drawable foreground, int size, boolean hasBleed) {
        if (!hasBleed) {
            if (background != null) {
                background.setBounds(0, 0, size, size);
                background.draw(canvas);
            }
            if (foreground != null) {
                foreground.setBounds(0, 0, size, size);
                foreground.draw(canvas);
            }
            return;
        }
        int bleed = Math.round(size * 0.25f);
        if (background != null) {
            background.setBounds(-bleed, -bleed, size + bleed, size + bleed);
            background.draw(canvas);
        }
        if (foreground != null) {
            foreground.setBounds(-bleed, -bleed, size + bleed, size + bleed);
            foreground.draw(canvas);
        }
    }

    public static void draw(Canvas canvas, Drawable background, Drawable foreground, int size) {
        draw(canvas, background, foreground, size, true);
    }

    static int accent(Context context, LauncherIcon icon) {
        if (icon == null) return 0;
        Integer cached = accents.get(icon);
        if (cached != null) {
            return cached;
        }
        int color = 0;
        try {
            int size = 24;
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            draw(new Canvas(bitmap),
                    icon.background != 0 ? ContextCompat.getDrawable(context, icon.background) : null,
                    icon.foreground != 0 ? ContextCompat.getDrawable(context, icon.foreground) : null,
                    size);
            int[] pixels = new int[size * size];
            bitmap.getPixels(pixels, 0, size, 0, 0, size, size);
            bitmap.recycle();
            color = dominant(pixels);
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (color == 0 && icon.glowColor != 0) {
            color = icon.glowColor;
        }
        accents.put(icon, color);
        return color;
    }

    private static int dominant(int[] pixels) {
        int bins = 12;
        float[] weight = new float[bins];
        float[] hue = new float[bins];
        float[] saturation = new float[bins];
        float[] lightness = new float[bins];
        float[] hsl = new float[3];
        int colored = 0;
        int extreme = 0;
        float extremeLightness = 0f;
        for (int pixel : pixels) {
            if (Color.alpha(pixel) < 200) {
                continue;
            }
            ColorUtils.colorToHSL(pixel, hsl);
            if (hsl[1] < 0.15f) {
                continue;
            }
            if (hsl[2] < 0.06f || hsl[2] > 0.94f) {
                extreme++;
                extremeLightness += hsl[2];
                continue;
            }
            int bin = Math.min(bins - 1, (int) (hsl[0] / 360f * bins));
            float w = hsl[1] * (1f - Math.abs(hsl[2] * 2f - 1f));
            weight[bin] += w;
            hue[bin] += hsl[0] * w;
            saturation[bin] += hsl[1] * w;
            lightness[bin] += hsl[2] * w;
            colored++;
        }
        int best = 0;
        for (int i = 1; i < bins; i++) {
            if (weight[i] > weight[best]) {
                best = i;
            }
        }
        if (weight[best] > 0f && colored >= extreme * 0.15f) {
            hsl[0] = hue[best] / weight[best];
            hsl[1] = saturation[best] / weight[best];
            hsl[2] = lightness[best] / weight[best];
            return ColorUtils.HSLToColor(hsl);
        }
        if (extreme == 0) {
            return 0;
        }
        hsl[0] = 0f;
        hsl[1] = 0f;
        hsl[2] = extremeLightness / extreme;
        return ColorUtils.HSLToColor(hsl);
    }

    static int tint(int accent) {
        if (accent == 0) {
            return 0;
        }
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(accent, hsl);
        hsl[1] = Math.min(hsl[1], 0.6f);
        hsl[2] = Theme.isCurrentThemeDark() ? 0.22f : 0.86f;
        return ColorUtils.HSLToColor(hsl);
    }
}
