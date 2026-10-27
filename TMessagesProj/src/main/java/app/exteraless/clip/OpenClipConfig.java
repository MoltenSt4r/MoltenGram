package app.exteraless.clip;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Persistent configuration store for OpenCLIP Multimodal Semantic Search.
 * Manages master toggle, similarity threshold, and automatic media indexing settings.
 */
public final class OpenClipConfig {

    public static final String PREF_NAME = "openClipConfig";

    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_THRESHOLD = "threshold";
    public static final String KEY_AUTO_INDEX_PHOTOS = "autoIndexPhotos";
    public static final String KEY_AUTO_INDEX_TEXT = "autoIndexText";

    public static final boolean DEFAULT_ENABLED = true;
    public static final float DEFAULT_THRESHOLD = 0.55f;
    public static final float MIN_THRESHOLD = 0.35f;
    public static final float MAX_THRESHOLD = 0.80f;
    public static final boolean DEFAULT_AUTO_INDEX_PHOTOS = true;
    public static final boolean DEFAULT_AUTO_INDEX_TEXT = true;

    private OpenClipConfig() {
    }

    public static SharedPreferences getPreferences() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences.Editor getEditor() {
        SharedPreferences prefs = getPreferences();
        return prefs != null ? prefs.edit() : null;
    }

    public static boolean isEnabled() {
        try {
            SharedPreferences prefs = getPreferences();
            return prefs == null ? DEFAULT_ENABLED : prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED);
        } catch (Throwable e) {
            return DEFAULT_ENABLED;
        }
    }

    public static void setEnabled(boolean value) {
        try {
            SharedPreferences.Editor editor = getEditor();
            if (editor != null) {
                editor.putBoolean(KEY_ENABLED, value).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public static float getThreshold() {
        try {
            SharedPreferences prefs = getPreferences();
            float val = prefs == null ? DEFAULT_THRESHOLD : prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD);
            return clampThreshold(val);
        } catch (Throwable e) {
            return DEFAULT_THRESHOLD;
        }
    }

    public static void setThreshold(float value) {
        try {
            float clamped = clampThreshold(value);
            SharedPreferences.Editor editor = getEditor();
            if (editor != null) {
                editor.putFloat(KEY_THRESHOLD, clamped).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isAutoIndexPhotos() {
        try {
            SharedPreferences prefs = getPreferences();
            return prefs == null ? DEFAULT_AUTO_INDEX_PHOTOS : prefs.getBoolean(KEY_AUTO_INDEX_PHOTOS, DEFAULT_AUTO_INDEX_PHOTOS);
        } catch (Throwable e) {
            return DEFAULT_AUTO_INDEX_PHOTOS;
        }
    }

    public static void setAutoIndexPhotos(boolean value) {
        try {
            SharedPreferences.Editor editor = getEditor();
            if (editor != null) {
                editor.putBoolean(KEY_AUTO_INDEX_PHOTOS, value).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isAutoIndexText() {
        try {
            SharedPreferences prefs = getPreferences();
            return prefs == null ? DEFAULT_AUTO_INDEX_TEXT : prefs.getBoolean(KEY_AUTO_INDEX_TEXT, DEFAULT_AUTO_INDEX_TEXT);
        } catch (Throwable e) {
            return DEFAULT_AUTO_INDEX_TEXT;
        }
    }

    public static void setAutoIndexText(boolean value) {
        try {
            SharedPreferences.Editor editor = getEditor();
            if (editor != null) {
                editor.putBoolean(KEY_AUTO_INDEX_TEXT, value).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public static float clampThreshold(float val) {
        if (Float.isNaN(val) || Float.isInfinite(val)) {
            return DEFAULT_THRESHOLD;
        }
        return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, val));
    }

    public static float getEffectiveThreshold() {
        float uiThreshold = getThreshold();
        float norm = (uiThreshold - MIN_THRESHOLD) / (MAX_THRESHOLD - MIN_THRESHOLD);
        return 0.15f + norm * (0.38f - 0.15f);
    }
}
