package app.exteraless.spoof;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Data model for a device spoofing preset.
 */
public class DeviceSpoofPreset {
    public String id;
    public String title;
    public String manufacturer;
    public String model;
    public String systemVersion;
    public String group;
    public boolean isCustom;

    public DeviceSpoofPreset() {
    }

    public DeviceSpoofPreset(String id, String title, String manufacturer, String model, String systemVersion, String group, boolean isCustom) {
        this.id = id;
        this.title = title;
        this.manufacturer = manufacturer;
        this.model = model;
        this.systemVersion = systemVersion;
        this.group = group;
        this.isCustom = isCustom;
    }

    public String getFormattedDeviceModel() {
        if (model == null) {
            return manufacturer != null ? manufacturer : "Android unknown";
        }
        if (manufacturer != null && !manufacturer.isEmpty()) {
            if (model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
                return model;
            }
            return manufacturer + " " + model;
        }
        return model;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("title", title);
            json.put("manufacturer", manufacturer);
            json.put("model", model);
            json.put("systemVersion", systemVersion);
            json.put("group", group);
            json.put("isCustom", isCustom);
        } catch (JSONException ignore) {
        }
        return json;
    }

    public static DeviceSpoofPreset fromJson(JSONObject json) {
        if (json == null) {
            return null;
        }
        DeviceSpoofPreset preset = new DeviceSpoofPreset();
        preset.id = json.optString("id", "");
        preset.title = json.optString("title", "");
        preset.manufacturer = json.optString("manufacturer", "");
        preset.model = json.optString("model", "");
        preset.systemVersion = json.optString("systemVersion", "SDK 35");
        preset.group = json.optString("group", "Custom");
        preset.isCustom = json.optBoolean("isCustom", true);
        return preset;
    }
}
