package app.exteraless.shizuku;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public class ShizukuConfig {

    private static volatile ShizukuConfig instance;
    private final SharedPreferences prefs;

    public static ShizukuConfig getInstance() {
        if (instance == null) {
            synchronized (ShizukuConfig.class) {
                if (instance == null) {
                    instance = new ShizukuConfig();
                }
            }
        }
        return instance;
    }

    private ShizukuConfig() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences("shizuku_config", Context.MODE_PRIVATE);
    }

    public boolean isSilentUpdatesEnabled() {
        return prefs.getBoolean("silent_updates", true);
    }

    public void setSilentUpdatesEnabled(boolean enabled) {
        prefs.edit().putBoolean("silent_updates", enabled).apply();
    }

    public boolean isSilentApkEnabled() {
        return prefs.getBoolean("silent_apk", true);
    }

    public void setSilentApkEnabled(boolean enabled) {
        prefs.edit().putBoolean("silent_apk", enabled).apply();
    }

    public boolean isUnrestrictedBackgroundEnabled() {
        return prefs.getBoolean("unrestricted_background", true);
    }

    public void setUnrestrictedBackgroundEnabled(boolean enabled) {
        prefs.edit().putBoolean("unrestricted_background", enabled).apply();
    }

    public boolean isSilentPluginUpdatesEnabled() {
        return prefs.getBoolean("silent_plugin_updates", true);
    }

    public void setSilentPluginUpdatesEnabled(boolean enabled) {
        prefs.edit().putBoolean("silent_plugin_updates", enabled).apply();
    }

    public boolean isNotificationBoostEnabled() {
        return prefs.getBoolean("notification_boost", true);
    }

    public void setNotificationBoostEnabled(boolean enabled) {
        prefs.edit().putBoolean("notification_boost", enabled).apply();
    }
}
