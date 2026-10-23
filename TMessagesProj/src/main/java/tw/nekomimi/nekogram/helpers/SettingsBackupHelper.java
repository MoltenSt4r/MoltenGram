package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import com.radolyn.ayugram.utils.AyuGhostPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.PushListenerController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import kotlin.text.StringsKt;
import tw.nekomimi.nekogram.DialogConfig;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.config.ConfigItem;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.utils.FileUtil;
import tw.nekomimi.nekogram.utils.GsonUtil;
import tw.nekomimi.nekogram.utils.ShareUtil;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.helper.BookmarksHelper;
import xyz.nextalone.nagram.helper.LocalPeerColorHelper;
import xyz.nextalone.nagram.helper.LocalPremiumStatusHelper;

public final class SettingsBackupHelper {

    public static final String SECTION_GENERAL = "general";
    public static final String SECTION_TOR = "tor";
    public static final String SECTION_SHIZUKU = "shizuku";
    public static final String SECTION_PLUGINS = "plugins";
    public static final String SECTION_MEDIA = "media";
    public static final String SECTION_NOTIFICATIONS = "notifications";
    public static final String SECTION_AI = "ai";

    public static final String[] ALL_SECTIONS = {
            SECTION_GENERAL,
            SECTION_TOR,
            SECTION_SHIZUKU,
            SECTION_PLUGINS,
            SECTION_MEDIA,
            SECTION_NOTIFICATIONS,
            SECTION_AI
    };

    public static int getSectionTitleResId(String section) {
        if (SECTION_TOR.equals(section)) {
            return R.string.OEBackupSectionTor;
        } else if (SECTION_SHIZUKU.equals(section)) {
            return R.string.OEBackupSectionShizuku;
        } else if (SECTION_PLUGINS.equals(section)) {
            return R.string.OEBackupSectionPlugins;
        } else if (SECTION_MEDIA.equals(section)) {
            return R.string.OEBackupSectionMedia;
        } else if (SECTION_NOTIFICATIONS.equals(section)) {
            return R.string.OEBackupSectionNotifications;
        } else if (SECTION_AI.equals(section)) {
            return R.string.OEBackupSectionAi;
        } else {
            return R.string.OEBackupSectionGeneral;
        }
    }

    public static String getSectionForSp(String spName) {
        if ("tor_config".equals(spName)) {
            return SECTION_TOR;
        }
        if ("shizuku_config".equals(spName) || "moltengram_ota".equals(spName)) {
            return SECTION_SHIZUKU;
        }
        if ("plugin_store_config".equals(spName) || "exteraless_plugins".equals(spName)
                || "exteraless_plugins_metadata".equals(spName) || "exteraless_plugin_grants".equals(spName)
                || "exteraless_plugins_foreign".equals(spName) || (spName != null && spName.startsWith("exteraless_plugin_"))) {
            return SECTION_PLUGINS;
        }
        if ("media_saved_pos".equals(spName) || "metrolist_auth".equals(spName) || "video_subtitles".equals(spName)) {
            return SECTION_MEDIA;
        }
        if ("Notifications".equals(spName) || "exteraless_notifications".equals(spName)) {
            return SECTION_NOTIFICATIONS;
        }
        if ("aiConfig".equals(spName)) {
            return SECTION_AI;
        }
        return SECTION_GENERAL;
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces) throws JSONException {
        return backupSettingsJson(isCloud, indentSpaces, true, null);
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces, boolean includeApiKeys) throws JSONException {
        return backupSettingsJson(isCloud, indentSpaces, includeApiKeys, null);
    }

    private static final Set<String> SAFE_MAINCONFIG_KEYS = new HashSet<>(Arrays.asList(
            "saveIncomingPhotos", "noSoundHintShowed", "directShareHash",
            "useThreeLinesLayout", "archiveHidden", "distanceSystemType",
            "loopStickers", "keepMedia", "noStatusBar", "lastKeepMediaCheckTime",
            "searchMessagesAsListHintShows", "searchMessagesAsListUsed",
            "stickersReorderingHintUsed", "textSelectionHintShows",
            "scheduledOrNoSoundHintShows", "lockRecordAudioVideoHint",
            "disableVoiceAudioEffects", "chatSwipeAction", "ChatSwipeAction",
            "selectedAutoNightType", "lang_code", "web_restricted_domains2",
            "fons_size", "bubbleRadius", "iv_font_size", "allowBigEmoji",
            "useSystemEmoji", "useSystemBoldFont", "forceForumTabs",
            "suggestStickers", "suggestAnimatedEmoji", "repeatMode"
    ));

    private static boolean isKnownLongKey(String key) {
        return "scheduledOrNoSoundHintSeenAt".equals(key)
                || "scheduledHintSeenAt".equals(key)
                || "lastUpdateCheckTime".equals(key)
                || "appUpdateCheckTime".equals(key)
                || "pushLastReceivedTime".equals(key)
                || "passcodeRetryInMs".equals(key)
                || "lastUptimeMillis".equals(key);
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces, boolean includeApiKeys, Set<String> enabledSections) throws JSONException {

        JSONObject configJson = new JSONObject();

        if (enabledSections == null || enabledSections.contains(SECTION_GENERAL)) {
            ArrayList<String> userconfig = new ArrayList<>();
            userconfig.add("saveIncomingPhotos");
            userconfig.add("passcodeHash");
            userconfig.add("passcodeType");
            userconfig.add("autoLockIn");
            userconfig.add("useFingerprint");
            spToJSON("userconfing", configJson, userconfig::contains, isCloud);

            spToJSON("mainconfig", configJson, SAFE_MAINCONFIG_KEYS::contains, includeApiKeys);

            if (!isCloud) {
                spToJSON("themeconfig", configJson, null);
            }
            spToJSON("nkmrcfg", configJson, null, includeApiKeys);
            spToJSON("exteraconfig", configJson, null, includeApiKeys);
            spToJSON("custom_icons", configJson, null, includeApiKeys);
        }

        if (enabledSections == null || enabledSections.contains(SECTION_NOTIFICATIONS)) {
            spToJSON("Notifications", configJson, null);
            spToJSON("exteraless_notifications", configJson, null);
        }

        if (enabledSections == null || enabledSections.contains(SECTION_SHIZUKU)) {
            spToJSON("shizuku_config", configJson, null, includeApiKeys);
            spToJSON("moltengram_ota", configJson, null, includeApiKeys);
        }

        if (enabledSections == null || enabledSections.contains(SECTION_TOR)) {
            spToJSON("tor_config", configJson, null, includeApiKeys);
        }

        if (enabledSections == null || enabledSections.contains(SECTION_PLUGINS)) {
            spToJSON("plugin_store_config", configJson, null, includeApiKeys);
            spToJSON(app.exteraless.plugins.PluginsConstants.PREFS_NAME, configJson, null, includeApiKeys);
            spToJSON("exteraless_plugins_metadata", configJson, null, includeApiKeys);
            spToJSON("exteraless_plugin_grants", configJson, null, includeApiKeys);
            spToJSON("exteraless_plugins_foreign", configJson, null, includeApiKeys);
            try {
                for (app.exteraless.plugins.Plugin p : app.exteraless.plugins.PluginsController.getInstance().getPluginsSnapshot()) {
                    if (p != null && p.id != null) {
                        String pluginSp = app.exteraless.plugins.PluginsConstants.SETTINGS_PREFS_PREFIX + p.id;
                        spToJSON(pluginSp, configJson, null, includeApiKeys);
                    }
                }
            } catch (Throwable ignore) {
            }
        }

        if (enabledSections == null || enabledSections.contains(SECTION_MEDIA)) {
            spToJSON("media_saved_pos", configJson, null);
            spToJSON("metrolist_auth", configJson, null);
            spToJSON("video_subtitles", configJson, null);
        }

        if (enabledSections == null || enabledSections.contains(SECTION_AI)) {
            if (includeApiKeys) {
                spToJSON("aiConfig", configJson, key -> !"history".equals(key));
            } else {
                spToJSON("aiConfig", configJson, key -> !"history".equals(key) && !"services".equals(key));
            }
        }

        return configJson.toString(indentSpaces);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter) throws JSONException {
        spToJSON(sp, object, filter, true);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter, boolean includeApiKeys) throws JSONException {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(sp, Activity.MODE_PRIVATE);
        JSONObject jsonConfig = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if ("nkmrcfg".equals(sp) && isDeviceSpecificPushKey(key)) {
                continue;
            }
            if (!includeApiKeys && (key.endsWith("Key") || key.contains("Token") || key.contains("AccountID"))) {
                continue;
            }
            if (filter != null && !filter.apply(key)) {
                continue;
            }
            if (entry.getValue() instanceof Long) {
                key = key + "_long";
            } else if (entry.getValue() instanceof Float) {
                key = key + "_float";
            }
            jsonConfig.put(key, entry.getValue());
        }
        object.put(sp, jsonConfig);
    }

    public static void importSettings(Context context, File settingsFile) {
        if (context == null || settingsFile == null) return;
        try {
            JsonObject configJson = GsonUtil.toJsonObject(FileUtil.readUtf8String(settingsFile));
            showImportSectionsDialog(context, configJson);
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(context, e);
        }
    }

    public static void showImportSectionsDialog(Context context, JsonObject configJson) {
        if (context == null || configJson == null) return;

        Set<String> presentSections = new LinkedHashSet<>();
        for (String spName : configJson.keySet()) {
            presentSections.add(getSectionForSp(spName));
        }

        if (presentSections.isEmpty()) {
            AlertUtil.showSimpleAlert(context, getString(R.string.OEGeneralImportEtgEmpty));
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.OEBackupImportSectionsTitle));
        builder.setMessage(getString(R.string.OEBackupImportSectionsDesc));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, dp(8), 0, dp(8));
        scrollView.addView(linearLayout);

        Map<String, CheckBoxCell> sectionCells = new LinkedHashMap<>();
        for (String section : ALL_SECTIONS) {
            if (!presentSections.contains(section)) {
                continue;
            }
            CheckBoxCell cell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, null);
            cell.setBackground(Theme.getSelectorDrawable(false));
            cell.setText(getString(getSectionTitleResId(section)), "", true, false);
            cell.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
            cell.setChecked(true, false);
            cell.setOnClickListener(v -> {
                CheckBoxCell c = (CheckBoxCell) v;
                c.setChecked(!c.isChecked(), true);
            });
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            sectionCells.put(section, cell);
        }

        builder.setView(scrollView);
        builder.setPositiveButton(getString(R.string.Import), (dialog, which) -> {
            Set<String> selected = new HashSet<>();
            for (Map.Entry<String, CheckBoxCell> entry : sectionCells.entrySet()) {
                if (entry.getValue().isChecked()) {
                    selected.add(entry.getKey());
                }
            }
            if (selected.isEmpty()) {
                return;
            }

            try {
                JsonObject filtered = new JsonObject();
                for (Map.Entry<String, JsonElement> entry : configJson.entrySet()) {
                    String section = getSectionForSp(entry.getKey());
                    if (selected.contains(section)) {
                        filtered.add(entry.getKey(), entry.getValue());
                    }
                }

                importSettings(filtered);

                AlertDialog restart = new AlertDialog(context, 0);
                restart.setTitle(getString(R.string.AppName));
                restart.setMessage(getString(R.string.RestartAppToTakeEffect));
                restart.setPositiveButton(getString(R.string.OK), (__, ___) -> AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class)));
                restart.show();
            } catch (Exception e) {
                AlertUtil.showSimpleAlert(context, e);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    @SuppressLint("ApplySharedPref")
    public static void importSettings(JsonObject configJson) throws JSONException {
        Map<String, Integer> configTypes = new HashMap<>();
        try {
            configTypes.putAll(NekoConfig.getConfigTypes());
            configTypes.putAll(NaConfig.INSTANCE.getConfigTypes());
        } catch (Throwable ignore) {
        }
        try {
            configTypes.putAll(app.exteraless.OpenExteraConfig.getConfigTypes());
            configTypes.putAll(app.exteraless.chats.ChatsConfig.getConfigTypes());
            configTypes.putAll(app.exteraless.icons.IconPacksConfig.getConfigTypes());
            configTypes.putAll(app.exteraless.glyph.GlyphConfig.getConfigTypes());
            configTypes.putAll(app.exteraless.pillstack.PillStackConfig.getConfigTypes());
            configTypes.putAll(app.exteraless.utils.UtilsConfig.getConfigTypes());
            configTypes.putAll(app.exteraless.appearance.AppearanceConfig.getConfigTypes());
            configTypes.putAll(app.exteraless.general.GeneralConfig.getConfigTypes());
        } catch (Throwable ignore) {
        }
        String[] preservePrefixes = {
                AyuGhostPreferences.ghostReadExclusionPrefix,
                AyuGhostPreferences.ghostTypingExclusionPrefix,
                AyuSavePreferences.saveExclusionPrefix,
                LocalNameHelper.chatNameOverridePrefix,
                LocalNameHelper.userNameOverridePrefix,
                DialogConfig.customForumTabPrefix,
                LocalPeerColorHelper.KEY_PREFIX,
                LocalPremiumStatusHelper.KEY_PREFIX,
                BookmarksHelper.KEY_PREFIX
        };

        for (Map.Entry<String, JsonElement> element : configJson.entrySet()) {
            String spName = element.getKey();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(spName, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, JsonElement> config : ((JsonObject) element.getValue()).entrySet()) {
                String key = config.getKey();
                if ("nkmrcfg".equals(spName) && isDeviceSpecificPushKey(key)) {
                    continue;
                }
                if (!config.getValue().isJsonPrimitive()) {
                    // Handle StringSet stored as JsonArray
                    if (config.getValue().isJsonArray()) {
                        try {
                            com.google.gson.JsonArray arr = config.getValue().getAsJsonArray();
                            java.util.Set<String> set = new java.util.HashSet<>();
                            for (com.google.gson.JsonElement el : arr) {
                                if (el.isJsonPrimitive()) set.add(el.getAsString());
                            }
                            editor.putStringSet(key, set);
                        } catch (Throwable ignored) {}
                    }
                    continue;
                }
                JsonPrimitive value = config.getValue().getAsJsonPrimitive();
                if ("nkmrcfg".equals(spName)) {
                    boolean shouldSkip = true;
                    for (String prefix : preservePrefixes) {
                        if (key.startsWith(prefix)) {
                            shouldSkip = false;
                            break;
                        }
                    }
                    if (key.startsWith("OE") || key.startsWith("Neko") || key.startsWith("Na") || key.startsWith("pref_")) {
                        shouldSkip = false;
                    }
                    if (shouldSkip) {
                        String actualKey = key;
                        if (key.endsWith("_long")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_long", key);
                        } else if (key.endsWith("_float")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_float", key);
                        }
                        Integer type = configTypes.get(actualKey);
                        shouldSkip = type == null || !isCompatibleConfigValue(key, value, type);
                    }
                    if (shouldSkip) {
                        continue;
                    }
                }
                if (value.isBoolean()) {
                    editor.putBoolean(key, value.getAsBoolean());
                } else if (value.isNumber()) {
                    boolean isLong = false;
                    boolean isFloat = false;
                    if (key.endsWith("_long")) {
                        key = StringsKt.substringBeforeLast(key, "_long", key);
                        isLong = true;
                    } else if (key.endsWith("_float")) {
                        key = StringsKt.substringBeforeLast(key, "_float", key);
                        isFloat = true;
                    }
                    if (isLong || isKnownLongKey(key)) {
                        editor.putLong(key, value.getAsLong());
                    } else if (isFloat) {
                        editor.putFloat(key, value.getAsFloat());
                    } else {
                        editor.putInt(key, value.getAsInt());
                    }
                } else {
                    editor.putString(key, value.getAsString());
                }
            }
            editor.commit();
        }
        try {
            com.exteragram.messenger.ExteraConfig.reloadConfig();
        } catch (Throwable ignore) {
        }
        try {
            app.exteraless.tor.TorController.getInstance();
        } catch (Throwable ignore) {
        }
        try {
            app.exteraless.shizuku.ShizukuConfig.getInstance();
        } catch (Throwable ignore) {
        }
        try {
            app.exteraless.plugins.ui.PluginStoreConfig.getInstance();
        } catch (Throwable ignore) {
        }
        try {
            app.exteraless.plugins.PluginsController.getInstance().loadPluginSettings();
            app.exteraless.plugins.PluginsController.getInstance().notifyPluginsChanged();
        } catch (Throwable ignore) {
        }
        PushListenerController.reconcilePushRegistration();
    }

    private static boolean isCompatibleConfigValue(String key, JsonPrimitive value, int type) {
        if (key.equals(NaConfig.INSTANCE.getPushServiceType().getKey())) {
            return value.isNumber() && value.getAsInt() >= 0 && value.getAsInt() <= 3;
        }
        if (type == ConfigItem.configTypeBool || type == ConfigItem.configTypeBoolLinkInt) {
            return value.isBoolean();
        }
        if (type == ConfigItem.configTypeInt) {
            return value.isNumber() && !key.endsWith("_long") && !key.endsWith("_float");
        }
        if (type == ConfigItem.configTypeLong) {
            return value.isNumber() && key.endsWith("_long");
        }
        if (type == ConfigItem.configTypeFloat) {
            return value.isNumber() && key.endsWith("_float");
        }
        return value.isString();
    }

    public static void backupSettings(Context context, Theme.ResourcesProvider resourceProvider) {
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.OEBackupSectionsTitle));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setPadding(0, dp(8), 0, dp(8));
        scrollView.addView(linearLayout);

        Map<String, CheckBoxCell> sectionCells = new LinkedHashMap<>();
        for (String section : ALL_SECTIONS) {
            CheckBoxCell cell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, resourceProvider);
            cell.setBackground(Theme.getSelectorDrawable(false));
            cell.setText(getString(getSectionTitleResId(section)), "", true, false);
            cell.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
            cell.setChecked(true, false);
            cell.setOnClickListener(v -> {
                CheckBoxCell c = (CheckBoxCell) v;
                c.setChecked(!c.isChecked(), true);
            });
            linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
            sectionCells.put(section, cell);
        }

        android.view.View divider = new android.view.View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider, resourceProvider));
        linearLayout.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 16, 8, 16, 8));

        CheckBoxCell apiKeysCell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, resourceProvider);
        apiKeysCell.setBackground(Theme.getSelectorDrawable(false));
        apiKeysCell.setText(getString(R.string.ExportSettingsIncludeApiKeys), "", true, false);
        apiKeysCell.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
        apiKeysCell.setChecked(true, false);
        apiKeysCell.setOnClickListener(v -> {
            CheckBoxCell c = (CheckBoxCell) v;
            c.setChecked(!c.isChecked(), true);
        });
        linearLayout.addView(apiKeysCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        builder.setView(scrollView);
        builder.setPositiveButton(getString(R.string.ExportTheme), (dialog, which) -> {
            Set<String> selected = new HashSet<>();
            for (Map.Entry<String, CheckBoxCell> entry : sectionCells.entrySet()) {
                if (entry.getValue().isChecked()) {
                    selected.add(entry.getKey());
                }
            }
            if (selected.isEmpty()) {
                return;
            }
            boolean includeApiKeys = apiKeysCell.isChecked();
            try {
                File cacheFile = new File(AndroidUtilities.getCacheDir(), new Date() + ".nekox-settings.json");
                FileUtil.writeUtf8String(SettingsBackupHelper.backupSettingsJson(false, 4, includeApiKeys, selected), cacheFile);
                ShareUtil.shareFile(context, cacheFile);
            } catch (Exception e) {
                AlertUtil.showSimpleAlert(context, e);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private static boolean isDeviceSpecificPushKey(String key) {
        return key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedSimple().getKey())
                || key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPrivateKey().getKey())
                || key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushPublicKey().getKey())
                || key.equals(NaConfig.INSTANCE.getPushServiceTypeUnifiedWebPushAuthSecret().getKey());
    }
}
