package app.exteraless.plugins.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PluginStoreConfig {

    public static final int SORT_DATE_DESC = 0;
    public static final int SORT_DATE_ASC = 1;
    public static final int SORT_NAME_ASC = 2;
    public static final int SORT_NAME_DESC = 3;
    public static final int SORT_SIZE_DESC = 4;

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 100;

    public static final List<String> DEFAULT_CHANNELS = Collections.unmodifiableList(Arrays.asList(
            "PESSDES_Plugins",
            "PluginProject",
            "nonPlugins",
            "ApplePlugins",
            "ExerealPlugins",
            "MLPlugins",
            "CactusPlugins",
            "doctashare",
            "exteraPlugins",
            "TheDotted",
            "anivPlugins",
            "bleizixPlugins",
            "QuantaPlugins",
            "shareui"
    ));

    private static volatile PluginStoreConfig instance;
    private final SharedPreferences prefs;

    public static PluginStoreConfig getInstance() {
        if (instance == null) {
            synchronized (PluginStoreConfig.class) {
                if (instance == null) {
                    instance = new PluginStoreConfig();
                }
            }
        }
        return instance;
    }

    private PluginStoreConfig() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences("plugin_store_config", Context.MODE_PRIVATE);
    }

    public List<String> getSourceChannels() {
        Set<String> custom = prefs.getStringSet("custom_channels", null);
        Set<String> removed = prefs.getStringSet("removed_channels", null);
        if (removed == null) removed = Collections.emptySet();

        List<String> result = new ArrayList<>();
        for (String c : DEFAULT_CHANNELS) {
            if (!removed.contains(c.toLowerCase(Locale.ROOT))) {
                result.add(c);
            }
        }
        if (custom != null) {
            for (String c : custom) {
                if (!result.contains(c) && !removed.contains(c.toLowerCase(Locale.ROOT))) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    public boolean addSourceChannel(String rawUsername) {
        if (TextUtils.isEmpty(rawUsername)) return false;
        String clean = rawUsername.trim();
        if (clean.startsWith("https://t.me/")) {
            clean = clean.substring("https://t.me/".length());
        } else if (clean.startsWith("http://t.me/")) {
            clean = clean.substring("http://t.me/".length());
        } else if (clean.startsWith("t.me/")) {
            clean = clean.substring("t.me/".length());
        }
        if (clean.startsWith("@")) {
            clean = clean.substring(1);
        }
        if (clean.isEmpty() || clean.contains("/") || clean.contains("?")) return false;

        Set<String> custom = new HashSet<>(prefs.getStringSet("custom_channels", new HashSet<>()));
        custom.add(clean);
        prefs.edit().putStringSet("custom_channels", custom).apply();

        // Also remove from removed set if it was previously removed
        Set<String> removed = prefs.getStringSet("removed_channels", null);
        if (removed != null && removed.contains(clean.toLowerCase(Locale.ROOT))) {
            Set<String> newRemoved = new HashSet<>(removed);
            newRemoved.remove(clean.toLowerCase(Locale.ROOT));
            prefs.edit().putStringSet("removed_channels", newRemoved).apply();
        }
        return true;
    }

    public void removeSourceChannel(String channel) {
        if (TextUtils.isEmpty(channel)) return;
        String clean = channel.trim();
        if (clean.startsWith("@")) clean = clean.substring(1);

        Set<String> custom = prefs.getStringSet("custom_channels", null);
        if (custom != null && custom.contains(clean)) {
            Set<String> newCustom = new HashSet<>(custom);
            newCustom.remove(clean);
            prefs.edit().putStringSet("custom_channels", newCustom).apply();
        }

        Set<String> removed = new HashSet<>(prefs.getStringSet("removed_channels", new HashSet<>()));
        removed.add(clean.toLowerCase(Locale.ROOT));
        prefs.edit().putStringSet("removed_channels", removed).apply();
    }

    public boolean isCustomChannel(String channel) {
        if (TextUtils.isEmpty(channel)) return false;
        String clean = channel.trim();
        if (clean.startsWith("@")) clean = clean.substring(1);
        for (String def : DEFAULT_CHANNELS) {
            if (def.equalsIgnoreCase(clean)) return false;
        }
        return true;
    }

    public int getSortMode() {
        return prefs.getInt("sort_mode", SORT_DATE_DESC);
    }

    public void setSortMode(int mode) {
        prefs.edit().putInt("sort_mode", mode).apply();
    }

    public boolean isDeepSearchEnabled() {
        return prefs.getBoolean("deep_search", true);
    }

    public void setDeepSearchEnabled(boolean enabled) {
        prefs.edit().putBoolean("deep_search", enabled).apply();
    }

    public boolean isHideOldVersionsEnabled() {
        return prefs.getBoolean("hide_old_versions", true);
    }

    public void setHideOldVersionsEnabled(boolean enabled) {
        prefs.edit().putBoolean("hide_old_versions", enabled).apply();
    }

    public boolean isAutoUpdateEnabled() {
        return prefs.getBoolean("auto_update_plugins", true);
    }

    public void setAutoUpdateEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_update_plugins", enabled).apply();
    }

    public int getPageSize() {
        int size = prefs.getInt("page_size", DEFAULT_PAGE_SIZE);
        if (size < MIN_PAGE_SIZE) {
            return MIN_PAGE_SIZE;
        }
        if (size > MAX_PAGE_SIZE) {
            return MAX_PAGE_SIZE;
        }
        return size;
    }

    public void setPageSize(int size) {
        if (size < MIN_PAGE_SIZE) {
            size = MIN_PAGE_SIZE;
        } else if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }
        prefs.edit().putInt("page_size", size).apply();
    }

    public void clearCache() {
        try {
            File cache = new File(ApplicationLoader.applicationContext.getCacheDir(), "packit_plugins_cache.json");
            if (cache.exists()) {
                cache.delete();
            }
        } catch (Exception ignored) {}
    }
}
