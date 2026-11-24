package app.exteraless.glass;

import android.content.SharedPreferences;
import android.os.Build;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Components.blur3.GlassOutlineStyle;
import org.telegram.ui.LaunchActivity;
import org.telegram.utils.glass.GlassEngine;

import java.util.concurrent.CopyOnWriteArrayList;

import tw.nekomimi.nekogram.NekoConfig;

/**
 * Configuration for Solegram-ported Liquid Glass material, lens controls, live popups, and optional glass headers.
 * Stored in {@link NekoConfig#getPreferences()} with {@code MG_*} keys.
 */
public final class LiquidGlassConfig {

    private static final String PREFIX = "MG_";
    private static final String KEY_DEFAULTS_VERSION = PREFIX + "glassDefaultsVersion";
    private static final int GLASS_DEFAULTS_VERSION = 4;

    public static volatile int version = 1;

    public interface Listener {
        void onLiquidGlassConfigChanged();
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public static boolean isLiquidGlassEnabled() {
        return Build.VERSION.SDK_INT >= 33 && glassOn && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
    }

    public static boolean glassOn = false;
    public static int glassThickness = 75;
    public static int glassRefraction = 35;
    public static int glassIndex = 130;
    public static int glassDispersion = 40;
    public static int glassRim = 0;
    public static int glassOpacity = 35;
    public static int glassBlur = 0;
    public static int glassHighlight = 0;
    public static int glassTint = 0;
    public static int glassTintAmount = 25;
    public static boolean glassShadow = false;
    public static boolean glassVibrancy = false;
    public static boolean glassPreviewLook = true;
    public static boolean liquidControls = false;
    public static boolean glassHeaderButtons = false;
    public static boolean frostedHeaders = false;
    public static boolean glassMenus = false;
    public static boolean glassDialogs = false;
    public static boolean glassSheets = false;
    public static boolean glassFab = false;
    public static boolean glassMiniPlayer = false;
    public static boolean glassMessages = false;
    public static boolean glassMainHeader = false;
    public static boolean glassFolders = false;

    static {
        init();
    }

    private static SharedPreferences prefs() {
        return NekoConfig.getPreferences();
    }

    private static void init() {
        try {
            final SharedPreferences p = prefs();
            final int savedVer = p.getInt(KEY_DEFAULTS_VERSION, 0);
            if (savedVer < GLASS_DEFAULTS_VERSION) {
                final SharedPreferences.Editor editor = p.edit();
                if (savedVer == 0) {
                    writeGlassDefaults(editor);
                } else {
                    if (savedVer < 2) {
                        editor.putBoolean(PREFIX + "liquidControls", false);
                    }
                    if (savedVer < 3) {
                        editor.putBoolean(PREFIX + "glassOn", false);
                    }
                    if (savedVer < 4) {
                        editor.putInt(PREFIX + "glassRim", 0);
                        editor.putInt(PREFIX + "glassBlur", 0);
                        editor.putBoolean(PREFIX + "glassShadow", false);
                    }
                }
                editor.putInt(KEY_DEFAULTS_VERSION, GLASS_DEFAULTS_VERSION).commit();
                if (savedVer == 0) {
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(LiquidGlassConfig::enableTexturedGlass);
                }
            }
            reload();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void reload() {
        final SharedPreferences p = prefs();
        glassOn = p.getBoolean(PREFIX + "glassOn", false);
        glassThickness = p.getInt(PREFIX + "glassThickness", 75);
        glassRefraction = p.getInt(PREFIX + "glassRefraction", 35);
        glassIndex = p.getInt(PREFIX + "glassIndex", 130);
        glassDispersion = p.getInt(PREFIX + "glassDispersion", 40);
        glassRim = p.getInt(PREFIX + "glassRim", 0);
        glassOpacity = p.getInt(PREFIX + "glassOpacity", 35);
        glassBlur = p.getInt(PREFIX + "glassBlur", 0);
        glassHighlight = p.getInt(PREFIX + "glassHighlight", 0);
        glassTint = p.getInt(PREFIX + "glassTint", 0);
        glassTintAmount = p.getInt(PREFIX + "glassTintAmount", 25);
        glassShadow = p.getBoolean(PREFIX + "glassShadow", false);
        glassVibrancy = p.getBoolean(PREFIX + "glassVibrancy", false);
        glassPreviewLook = p.getBoolean(PREFIX + "glassPreviewLook", true);
        liquidControls = p.getBoolean(PREFIX + "liquidControls", false);
        glassHeaderButtons = p.getBoolean(PREFIX + "glassHeaderButtons", false);
        frostedHeaders = p.getBoolean(PREFIX + "frostedHeaders", false);
        glassMenus = p.getBoolean(PREFIX + "glassMenus", false);
        glassDialogs = p.getBoolean(PREFIX + "glassDialogs", false);
        glassSheets = p.getBoolean(PREFIX + "glassSheets", false);
        glassFab = p.getBoolean(PREFIX + "glassFab", false);
        glassMiniPlayer = p.getBoolean(PREFIX + "glassMiniPlayer", false);
        glassMessages = p.getBoolean(PREFIX + "glassMessages", false);
        glassMainHeader = p.getBoolean(PREFIX + "glassMainHeader", false);
        glassFolders = p.getBoolean(PREFIX + "glassFolders", false);
    }

    private static void writeGlassDefaults(SharedPreferences.Editor editor) {
        editor.putBoolean(PREFIX + "glassOn", false)
            .putInt(PREFIX + "glassThickness", 75)
            .putInt(PREFIX + "glassRefraction", 35)
            .putInt(PREFIX + "glassIndex", 130)
            .putInt(PREFIX + "glassDispersion", 40)
            .putInt(PREFIX + "glassRim", 0)
            .putInt(PREFIX + "glassOpacity", 35)
            .putInt(PREFIX + "glassBlur", 0)
            .putInt(PREFIX + "glassHighlight", 0)
            .putInt(PREFIX + "glassTint", 0)
            .putInt(PREFIX + "glassTintAmount", 25)
            .putBoolean(PREFIX + "glassShadow", false)
            .putBoolean(PREFIX + "glassVibrancy", false)
            .putBoolean(PREFIX + "glassPreviewLook", true)
            .putBoolean(PREFIX + "liquidControls", false)
            .putBoolean(PREFIX + "glassHeaderButtons", false)
            .putBoolean(PREFIX + "frostedHeaders", false)
            .putBoolean(PREFIX + "glassMenus", false)
            .putBoolean(PREFIX + "glassDialogs", false)
            .putBoolean(PREFIX + "glassSheets", false)
            .putBoolean(PREFIX + "glassFab", false)
            .putBoolean(PREFIX + "glassMiniPlayer", false)
            .putBoolean(PREFIX + "glassMessages", false)
            .putBoolean(PREFIX + "glassMainHeader", false)
            .putBoolean(PREFIX + "glassFolders", false);
    }

    private static final Runnable postThemeChangedRunnable = () -> {
        NotificationCenter.getGlobalInstance().postNotificationName(
                NotificationCenter.didSetNewTheme,
                Theme.isCurrentThemeDark(),
                false,
                true);
    };

    private static void scheduleThemeBroadcast(boolean immediate) {
        AndroidUtilities.cancelRunOnUIThread(postThemeChangedRunnable);
        if (immediate) {
            AndroidUtilities.runOnUIThread(postThemeChangedRunnable);
        } else {
            AndroidUtilities.runOnUIThread(postThemeChangedRunnable, 60);
        }
    }

    public static void putInt(String key, int value) {
        prefs().edit().putInt(PREFIX + key, value).apply();
        reload();
        notifyChanged();
        scheduleThemeBroadcast("glassTint".equals(key));
    }

    public static void putBoolean(String key, boolean value) {
        prefs().edit().putBoolean(PREFIX + key, value).apply();
        if ("glassMessages".equals(key)) {
            try {
                app.exteraless.appearance.AppearanceConfig.glassMessages.setConfigBool(value);
            } catch (Throwable ignore) {
            }
        } else if ("glassOn".equals(key) && value) {
            enableTexturedGlass();
        }
        reload();
        notifyChanged();
        scheduleThemeBroadcast(true);
    }

    public static void enableTexturedGlass() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                LiteMode.toggleFlag(LiteMode.FLAG_LIQUID_GLASS, true);
            }
            if (SharedConfig.canBlurChat()) {
                LiteMode.toggleFlag(LiteMode.FLAG_CHAT_BLUR, true);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static void resetGlass() {
        final SharedPreferences.Editor editor = prefs().edit();
        writeGlassDefaults(editor);
        editor.commit();
        reload();
        enableTexturedGlass();
        notifyChanged();
        scheduleThemeBroadcast(true);
    }

    public static void notifyChanged() {
        version++;
        ThemeBind.updateAll();
        Theme.reloadAllResources(ApplicationLoader.applicationContext);
        GlassOutlineStyle.dispatchChange();
        GlassEngine.bumpGeneration();
        app.exteraless.player.PlayerMiniView.styleChanged();
        for (Listener listener : listeners) {
            try {
                listener.onLiquidGlassConfigChanged();
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public static boolean canBlurWindows() {
        return Build.VERSION.SDK_INT >= 31 && LaunchActivity.systemBlurEnabled;
    }

    public static int blurBehindRadius() {
        return AndroidUtilities.dp(Math.max(4, Math.round(18 * SolegramGlass.blurScale())));
    }

    private LiquidGlassConfig() {
    }
}
