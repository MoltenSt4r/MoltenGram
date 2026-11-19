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
            "2349438816",
            "3490813925",
            "2329949330",
            "2443058177",
            "2467705421",
            "2475313399",
            "2521243181",
            "2554450010",
            "2618027830",
            "2672610568",
            "2782987927",
            "2813564336"
    ));

    public static final java.util.Map<String, String> KNOWN_CHANNEL_USERNAMES;
    public static final java.util.Map<String, String> KNOWN_CHANNEL_NAMES;
    static {
        java.util.Map<String, String> uMap = new java.util.HashMap<>();
        uMap.put("2349438816", "mishabotov");
        uMap.put("3490813925", "anivPlugins");
        uMap.put("2329949330", "ytilities");
        uMap.put("2443058177", "PESSDES_Plugins");
        uMap.put("2467705421", "CactusPlugins");
        uMap.put("2475313399", "shikaatuxplugins");
        uMap.put("2521243181", "zwyPlugins");
        uMap.put("2554450010", "TheDotted");
        uMap.put("2618027830", "bleizixPlugins");
        uMap.put("2672610568", "meeownnews");
        uMap.put("2782987927", "swagnonher");
        uMap.put("2813564336", "RnPlugins");
        KNOWN_CHANNEL_USERNAMES = Collections.unmodifiableMap(uMap);

        java.util.Map<String, String> tMap = new java.util.HashMap<>();
        tMap.put("2349438816", "ми ха ил котики и плагины");
        tMap.put("3490813925", "aniv Plugins");
        tMap.put("2329949330", "Ytilities");
        tMap.put("2443058177", "PESSDES Plugins");
        tMap.put("2467705421", "Колючие дополнения!");
        tMap.put("2475313399", "ШИШКИНЫ ЗАМЫСЛЫ");
        tMap.put("2521243181", "zwyPlugins");
        tMap.put("2554450010", "Dotted");
        tMap.put("2618027830", "bleizix's plugins");
        tMap.put("2672610568", "мяуньюс. / новости и плагины");
        tMap.put("2782987927", "SWAGA");
        tMap.put("2813564336", "RnDev");
        KNOWN_CHANNEL_NAMES = Collections.unmodifiableMap(tMap);
    }

    public static String getKnownUsernameForId(long id) {
        return KNOWN_CHANNEL_USERNAMES.get(String.valueOf(id));
    }

    public static String getKnownTitleForId(long id) {
        return KNOWN_CHANNEL_NAMES.get(String.valueOf(id));
    }

    public static String getKnownIdForUsername(String username) {
        if (TextUtils.isEmpty(username)) return null;
        String clean = username.trim();
        if (clean.startsWith("@")) clean = clean.substring(1);
        for (java.util.Map.Entry<String, String> entry : KNOWN_CHANNEL_USERNAMES.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(clean)) {
                return entry.getKey();
            }
        }
        return null;
    }

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
        checkMigration();
    }

    private void checkMigration() {
        if (prefs == null) return;
        if (prefs.getInt("channels_schema_version", 0) < 3) {
            Set<String> custom = getStringSetSafe("custom_channels");
            Set<String> newCustom = new HashSet<>();
            if (custom != null) {
                for (String c : custom) {
                    if (TextUtils.isEmpty(c)) continue;
                    String clean = c.trim();
                    if (clean.startsWith("@")) clean = clean.substring(1);
                    String mappedId = getKnownIdForUsername(clean);
                    if (mappedId == null && clean.matches("\\d+")) {
                        newCustom.add(clean);
                    }
                }
            }
            prefs.edit()
                    .putInt("channels_schema_version", 3)
                    .putStringSet("custom_channels", newCustom)
                    .remove("removed_channels")
                    .apply();
        }
    }

    private Set<String> getStringSetSafe(String key) {
        try {
            return prefs.getStringSet(key, null);
        } catch (ClassCastException e) {
            // Was previously saved as a plain String (e.g. from backup restore bug) — clear and migrate
            org.telegram.messenger.FileLog.e("PluginStoreConfig: key '" + key + "' was String, clearing", e);
            prefs.edit().remove(key).apply();
            return null;
        }
    }

    private Set<String> getStringSafeFallback(String key) {
        Set<String> val = getStringSetSafe(key);
        return val != null ? val : new HashSet<>();
    }

    public List<String> getSourceChannels() {
        Set<String> custom = getStringSetSafe("custom_channels");
        Set<String> removed = getStringSetSafe("removed_channels");
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

        String mappedId = getKnownIdForUsername(clean);
        if (mappedId != null) {
            clean = mappedId;
        }

        Set<String> custom = new HashSet<>(getStringSafeFallback("custom_channels"));
        custom.add(clean);
        prefs.edit().putStringSet("custom_channels", custom).apply();

        // Also remove from removed set if it was previously removed
        Set<String> removed = getStringSetSafe("removed_channels");
        if (removed != null) {
            String lower = clean.toLowerCase(Locale.ROOT);
            if (removed.contains(lower) || (mappedId != null && removed.contains(mappedId.toLowerCase(Locale.ROOT)))) {
                Set<String> newRemoved = new HashSet<>(removed);
                newRemoved.remove(lower);
                if (mappedId != null) newRemoved.remove(mappedId.toLowerCase(Locale.ROOT));
                prefs.edit().putStringSet("removed_channels", newRemoved).apply();
            }
        }
        return true;
    }

    public void removeSourceChannel(String channel) {
        if (TextUtils.isEmpty(channel)) return;
        String clean = channel.trim();
        if (clean.startsWith("@")) clean = clean.substring(1);
        String mappedId = getKnownIdForUsername(clean);

        Set<String> custom = getStringSetSafe("custom_channels");
        if (custom != null && (custom.contains(clean) || (mappedId != null && custom.contains(mappedId)))) {
            Set<String> newCustom = new HashSet<>(custom);
            newCustom.remove(clean);
            if (mappedId != null) newCustom.remove(mappedId);
            prefs.edit().putStringSet("custom_channels", newCustom).apply();
        }

        Set<String> removed = new HashSet<>(getStringSafeFallback("removed_channels"));
        removed.add(clean.toLowerCase(Locale.ROOT));
        if (mappedId != null) removed.add(mappedId.toLowerCase(Locale.ROOT));
        prefs.edit().putStringSet("removed_channels", removed).apply();
    }

    public boolean isCustomChannel(String channel) {
        if (TextUtils.isEmpty(channel)) return false;
        String clean = channel.trim();
        if (clean.startsWith("@")) clean = clean.substring(1);
        String mappedId = getKnownIdForUsername(clean);
        for (String def : DEFAULT_CHANNELS) {
            if (def.equalsIgnoreCase(clean) || (mappedId != null && def.equalsIgnoreCase(mappedId))) return false;
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
