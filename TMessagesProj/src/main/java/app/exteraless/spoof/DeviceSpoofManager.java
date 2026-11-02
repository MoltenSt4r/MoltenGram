package app.exteraless.spoof;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Manages user-space client-side device spoofing for MTProto connection parameters.
 * Does not require Root or Shizuku, as it overrides outgoing network metadata directly
 * without touching system-level android.os.Build fields.
 */
public class DeviceSpoofManager {

    private static final String PREFS_NAME = "device_spoof_prefs";
    private static final String KEY_ENABLED = "spoof_enabled";
    private static final String KEY_SELECTED_ID = "selected_preset_id";
    private static final String KEY_CUSTOM_PRESETS = "custom_presets_json";

    public static final String PRESET_MOLTENSTAR = "moltenstar_moltengram";

    private static volatile DeviceSpoofManager instance;

    private SharedPreferences preferences;
    private final List<DeviceSpoofPreset> builtInPresets = new ArrayList<>();
    private final List<DeviceSpoofPreset> customPresets = new ArrayList<>();

    public static DeviceSpoofManager getInstance() {
        DeviceSpoofManager localInstance = instance;
        if (localInstance == null) {
            synchronized (DeviceSpoofManager.class) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new DeviceSpoofManager();
                }
            }
        }
        return localInstance;
    }

    private SharedPreferences getPreferences() {
        if (preferences != null) {
            return preferences;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return preferences;
        }
        return null;
    }

    private DeviceSpoofManager() {
        getPreferences();
        initBuiltInPresets();
        loadCustomPresets();
    }

    private void initBuiltInPresets() {
        builtInPresets.clear();

        // 1. Futuristic Flagship: MoltenStar / MoltenGram branding
        builtInPresets.add(new DeviceSpoofPreset(
                PRESET_MOLTENSTAR,
                "MoltenStar",
                "MoltenStar",
                "MoltenGram",
                "SDK 36",
                "MoltenStar",
                false
        ));

        // 2. Google Pixel Lineup (from Pixel 11 down to original Pixel)
        builtInPresets.add(new DeviceSpoofPreset("pixel_11_pro_fold", "Google Pixel 11 Pro Fold", "Google", "Pixel 11 Pro Fold", "SDK 36", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_11_pro_xl", "Google Pixel 11 Pro XL", "Google", "Pixel 11 Pro XL", "SDK 36", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_11_pro", "Google Pixel 11 Pro", "Google", "Pixel 11 Pro", "SDK 36", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_11", "Google Pixel 11", "Google", "Pixel 11", "SDK 36", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_10_pro_fold", "Google Pixel 10 Pro Fold", "Google", "Pixel 10 Pro Fold", "SDK 36", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_10_pro_xl", "Google Pixel 10 Pro XL", "Google", "Pixel 10 Pro XL", "SDK 36", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_10_pro", "Google Pixel 10 Pro", "Google", "Pixel 10 Pro", "SDK 36", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_10", "Google Pixel 10", "Google", "Pixel 10", "SDK 36", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_9a", "Google Pixel 9a", "Google", "Pixel 9a", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_9_pro_fold", "Google Pixel 9 Pro Fold", "Google", "Pixel 9 Pro Fold", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_9_pro_xl", "Google Pixel 9 Pro XL", "Google", "Pixel 9 Pro XL", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_9_pro", "Google Pixel 9 Pro", "Google", "Pixel 9 Pro", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_9", "Google Pixel 9", "Google", "Pixel 9", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_8a", "Google Pixel 8a", "Google", "Pixel 8a", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_8_pro", "Google Pixel 8 Pro", "Google", "Pixel 8 Pro", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_8", "Google Pixel 8", "Google", "Pixel 8", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_fold", "Google Pixel Fold", "Google", "Pixel Fold", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_7a", "Google Pixel 7a", "Google", "Pixel 7a", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_7_pro", "Google Pixel 7 Pro", "Google", "Pixel 7 Pro", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_7", "Google Pixel 7", "Google", "Pixel 7", "SDK 35", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_6a", "Google Pixel 6a", "Google", "Pixel 6a", "SDK 34", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_6_pro", "Google Pixel 6 Pro", "Google", "Pixel 6 Pro", "SDK 34", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_6", "Google Pixel 6", "Google", "Pixel 6", "SDK 34", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_5a", "Google Pixel 5a", "Google", "Pixel 5a", "SDK 34", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_5", "Google Pixel 5", "Google", "Pixel 5", "SDK 34", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_4a_5g", "Google Pixel 4a (5G)", "Google", "Pixel 4a (5G)", "SDK 33", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_4a", "Google Pixel 4a", "Google", "Pixel 4a", "SDK 33", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_4_xl", "Google Pixel 4 XL", "Google", "Pixel 4 XL", "SDK 33", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_4", "Google Pixel 4", "Google", "Pixel 4", "SDK 33", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_3a_xl", "Google Pixel 3a XL", "Google", "Pixel 3a XL", "SDK 32", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_3a", "Google Pixel 3a", "Google", "Pixel 3a", "SDK 32", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_3_xl", "Google Pixel 3 XL", "Google", "Pixel 3 XL", "SDK 31", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_3", "Google Pixel 3", "Google", "Pixel 3", "SDK 31", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_2_xl", "Google Pixel 2 XL", "Google", "Pixel 2 XL", "SDK 30", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_2", "Google Pixel 2", "Google", "Pixel 2", "SDK 30", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_xl", "Google Pixel XL", "Google", "Pixel XL", "SDK 29", "Google Pixel", false));
        builtInPresets.add(new DeviceSpoofPreset("pixel_1", "Google Pixel", "Google", "Pixel", "SDK 29", "Google Pixel", false));

        // 3. Nothing Lineup (Реальные вышедшие модели: 4b, 4a Pro, 4a, 3, 2a Plus, 2a, 2, 1, CMF)
        builtInPresets.add(new DeviceSpoofPreset("nothing_phone_4b", "Nothing Phone (4b)", "Nothing", "Phone (4b)", "SDK 36", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_phone_4a_pro", "Nothing Phone (4a Pro)", "Nothing", "Phone (4a Pro)", "SDK 36", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_phone_4a", "Nothing Phone (4a)", "Nothing", "Phone (4a)", "SDK 36", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_phone_3", "Nothing Phone (3)", "Nothing", "Phone (3)", "SDK 35", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_phone_2a_plus", "Nothing Phone (2a) Plus", "Nothing", "Phone (2a) Plus", "SDK 34", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_phone_2a", "Nothing Phone (2a)", "Nothing", "Phone (2a)", "SDK 34", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_phone_2", "Nothing Phone (2)", "Nothing", "Phone (2)", "SDK 34", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_phone_1", "Nothing Phone (1)", "Nothing", "Phone (1)", "SDK 34", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_cmf_phone_2_pro", "Nothing CMF Phone 2 Pro", "Nothing", "CMF Phone 2 Pro", "SDK 35", "Nothing", false));
        builtInPresets.add(new DeviceSpoofPreset("nothing_cmf_phone_1", "Nothing CMF Phone 1", "Nothing", "CMF Phone 1", "SDK 34", "Nothing", false));
    }

    private void loadCustomPresets() {
        customPresets.clear();
        SharedPreferences prefs = getPreferences();
        if (prefs == null) {
            return;
        }
        String jsonStr = prefs.getString(KEY_CUSTOM_PRESETS, null);
        if (TextUtils.isEmpty(jsonStr)) {
            return;
        }
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                DeviceSpoofPreset preset = DeviceSpoofPreset.fromJson(array.getJSONObject(i));
                if (preset != null && !TextUtils.isEmpty(preset.id)) {
                    customPresets.add(preset);
                }
            }
        } catch (JSONException ignore) {
        }
    }

    private void saveCustomPresets() {
        SharedPreferences prefs = getPreferences();
        if (prefs == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (DeviceSpoofPreset preset : customPresets) {
            array.put(preset.toJson());
        }
        prefs.edit().putString(KEY_CUSTOM_PRESETS, array.toString()).apply();
    }

    public boolean isSpoofingEnabled() {
        SharedPreferences prefs = getPreferences();
        if (prefs == null) {
            return false;
        }
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    public void setSpoofingEnabled(boolean enabled) {
        SharedPreferences prefs = getPreferences();
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_ENABLED, enabled).apply();
        }
        applyToAllConnections();
    }

    public String getSelectedPresetId() {
        SharedPreferences prefs = getPreferences();
        if (prefs == null) {
            return PRESET_MOLTENSTAR;
        }
        return prefs.getString(KEY_SELECTED_ID, PRESET_MOLTENSTAR);
    }

    public void setSelectedPresetId(String id) {
        SharedPreferences prefs = getPreferences();
        if (prefs != null) {
            prefs.edit().putString(KEY_SELECTED_ID, id).apply();
        }
        applyToAllConnections();
    }

    public DeviceSpoofPreset getActivePreset() {
        String activeId = getSelectedPresetId();
        for (DeviceSpoofPreset p : customPresets) {
            if (p.id.equals(activeId)) {
                return p;
            }
        }
        for (DeviceSpoofPreset p : builtInPresets) {
            if (p.id.equals(activeId)) {
                return p;
            }
        }
        return !builtInPresets.isEmpty() ? builtInPresets.get(0) : null;
    }

    public DeviceSpoofPreset getCurrentPreset() {
        return getActivePreset();
    }

    public List<DeviceSpoofPreset> getBuiltInPresets() {
        if (builtInPresets.isEmpty()) {
            initBuiltInPresets();
        }
        return Collections.unmodifiableList(builtInPresets);
    }

    public List<DeviceSpoofPreset> getCustomPresets() {
        if (customPresets.isEmpty()) {
            loadCustomPresets();
        }
        return Collections.unmodifiableList(customPresets);
    }

    public List<DeviceSpoofPreset> getAllPresets() {
        List<DeviceSpoofPreset> all = new ArrayList<>();
        all.addAll(builtInPresets);
        all.addAll(customPresets);
        return all;
    }

    public DeviceSpoofPreset addCustomPreset(String title, String manufacturer, String model, String systemVersion) {
        String id = "custom_" + UUID.randomUUID().toString().substring(0, 8);
        if (TextUtils.isEmpty(title)) {
            title = (manufacturer != null ? manufacturer : "") + " " + (model != null ? model : "Device");
        }
        if (TextUtils.isEmpty(systemVersion)) {
            systemVersion = "SDK " + Build.VERSION.SDK_INT;
        }
        DeviceSpoofPreset preset = new DeviceSpoofPreset(id, title.trim(), manufacturer != null ? manufacturer.trim() : "", model != null ? model.trim() : "", systemVersion.trim(), "Custom", true);
        customPresets.add(preset);
        saveCustomPresets();
        return preset;
    }

    public boolean updateCustomPreset(String id, String title, String manufacturer, String model, String systemVersion) {
        for (DeviceSpoofPreset preset : customPresets) {
            if (preset.id.equals(id)) {
                if (!TextUtils.isEmpty(title)) {
                    preset.title = title.trim();
                }
                preset.manufacturer = manufacturer != null ? manufacturer.trim() : "";
                preset.model = model != null ? model.trim() : "";
                if (!TextUtils.isEmpty(systemVersion)) {
                    preset.systemVersion = systemVersion.trim();
                }
                saveCustomPresets();
                if (id.equals(getSelectedPresetId())) {
                    applyToAllConnections();
                }
                return true;
            }
        }
        return false;
    }

    public boolean deleteCustomPreset(String id) {
        for (int i = 0; i < customPresets.size(); i++) {
            if (customPresets.get(i).id.equals(id)) {
                customPresets.remove(i);
                saveCustomPresets();
                if (id.equals(getSelectedPresetId())) {
                    setSelectedPresetId(PRESET_MOLTENSTAR);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the device model string to be sent to MTProto servers in TL_initConnection.
     * When spoofing is disabled, returns actual hardware values (Build.MANUFACTURER + " " + Build.MODEL).
     */
    public String getDeviceModel() {
        if (!isSpoofingEnabled()) {
            return Build.MANUFACTURER + " " + Build.MODEL;
        }
        DeviceSpoofPreset preset = getActivePreset();
        if (preset != null) {
            return preset.getFormattedDeviceModel();
        }
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    /**
     * Returns the system version string to be sent to MTProto servers.
     * When spoofing is disabled, returns "SDK " + Build.VERSION.SDK_INT.
     */
    public String getSystemVersion() {
        if (!isSpoofingEnabled()) {
            return "SDK " + Build.VERSION.SDK_INT;
        }
        DeviceSpoofPreset preset = getActivePreset();
        if (preset != null && !TextUtils.isEmpty(preset.systemVersion)) {
            return preset.systemVersion;
        }
        return "SDK " + Build.VERSION.SDK_INT;
    }

    /**
     * Returns the manufacturer string (if needed for diagnostics/logs).
     */
    public String getManufacturer() {
        if (!isSpoofingEnabled()) {
            return Build.MANUFACTURER;
        }
        DeviceSpoofPreset preset = getActivePreset();
        if (preset != null && !TextUtils.isEmpty(preset.manufacturer)) {
            return preset.manufacturer;
        }
        return Build.MANUFACTURER;
    }

    /**
     * Propagates updated device spoofing parameters to all active MTProto ConnectionsManager instances.
     */
    public void applyToAllConnections() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            try {
                ConnectionsManager cm = ConnectionsManager.getInstance(a);
                if (cm != null) {
                    cm.updateDeviceModel();
                }
            } catch (Throwable ignore) {
            }
        }
    }
}
