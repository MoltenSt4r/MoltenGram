package app.exteraless.chats;

import android.content.Context;
import android.os.Build;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import app.exteraless.plugins.xposed.XposedHooks;
import dalvik.system.DexClassLoader;
import dalvik.system.InMemoryDexClassLoader;

/**
 * Native controller for Text Typing Animations, loaded out of the box from
 * assets/dex/text_animation.dex.
 */
public class TextAnimationController {

    private static final String TAG = "TextAnimationController";
    private static final String ENTRY_CLASS = "com.textanimation.TextAnimationCore";
    private static final String ASSET_NAME = "dex/text_animation.dex";

    private static volatile TextAnimationController instance;

    private Class<?> coreClass;
    private Method startMethod;
    private Method unloadMethod;
    private Method updateSettingsMethod;
    private boolean isRunning;

    public static TextAnimationController getInstance() {
        if (instance == null) {
            synchronized (TextAnimationController.class) {
                if (instance == null) {
                    instance = new TextAnimationController();
                }
            }
        }
        return instance;
    }

    private TextAnimationController() {
    }

    public synchronized void init() {
        if (isEnabled()) {
            start();
        }
    }

    public boolean isEnabled() {
        return ChatsConfig.textAnimationEnabled.Bool();
    }

    public synchronized boolean isRunning() {
        return isRunning;
    }

    private boolean loadDexIfNeeded() {
        if (coreClass != null) {
            return true;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            FileLog.e(TAG + ": Application context is null");
            return false;
        }
        try {
            XposedHooks.ensureReady();

            ClassLoader classLoader;
            try (InputStream is = context.getAssets().open(ASSET_NAME)) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[8192];
                int nRead;
                while ((nRead = is.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                byte[] dexBytes = buffer.toByteArray();

                ClassLoader parent = context.getClassLoader();
                if (Build.VERSION.SDK_INT >= 26) {
                    classLoader = new InMemoryDexClassLoader(ByteBuffer.wrap(dexBytes), parent);
                } else {
                    File dexFile = new File(context.getCodeCacheDir(), "text_animation.dex");
                    try (FileOutputStream fos = new FileOutputStream(dexFile)) {
                        fos.write(dexBytes);
                    }
                    classLoader = new DexClassLoader(dexFile.getAbsolutePath(),
                            context.getCodeCacheDir().getAbsolutePath(), null, parent);
                }
            }

            coreClass = classLoader.loadClass(ENTRY_CLASS);
            startMethod = coreClass.getDeclaredMethod("start");
            startMethod.setAccessible(true);
            unloadMethod = coreClass.getDeclaredMethod("unload");
            unloadMethod.setAccessible(true);
            updateSettingsMethod = coreClass.getDeclaredMethod("updateSettings", String.class);
            updateSettingsMethod.setAccessible(true);

            return true;
        } catch (Throwable t) {
            FileLog.e(TAG + ": Failed to load DEX: " + t.getMessage(), t);
            return false;
        }
    }

    public synchronized void start() {
        if (isRunning) {
            updateSettings();
            return;
        }
        if (!loadDexIfNeeded()) {
            return;
        }
        try {
            startMethod.invoke(null);
            isRunning = true;
            updateSettings();
            FileLog.d(TAG + ": Started successfully");
        } catch (Throwable t) {
            FileLog.e(TAG + ": Failed to start: " + t.getMessage(), t);
        }
    }

    public synchronized void stop() {
        if (!isRunning || coreClass == null) {
            return;
        }
        try {
            if (unloadMethod != null) {
                unloadMethod.invoke(null);
            }
            isRunning = false;
            FileLog.d(TAG + ": Unloaded successfully");
        } catch (Throwable t) {
            FileLog.e(TAG + ": Failed to unload: " + t.getMessage(), t);
        }
    }

    public static final int BULK_CHANGE_THRESHOLD = 30;
    private static final long SUPPRESS_DURATION_MS = 600;
    private boolean suppressed;
    private boolean selectionActive;
    private final Runnable restoreRunnable = () -> {
        suppressed = false;
        updateSettings();
    };

    /**
     * Hides the animated cursor when text is selected in the input field.
     */
    public void setSelectionActive(boolean active) {
        if (selectionActive != active) {
            selectionActive = active;
            if (isRunning) {
                updateSettings();
            }
        }
    }

    /**
     * Temporarily disables the per-character animation for bulk text changes
     * (paste, select-all delete, sending a long message), then restores it.
     */
    public void suppressBriefly() {
        if (!isRunning || coreClass == null || updateSettingsMethod == null) {
            return;
        }
        org.telegram.messenger.AndroidUtilities.cancelRunOnUIThread(restoreRunnable);
        if (!suppressed) {
            suppressed = true;
            try {
                updateSettingsMethod.invoke(null, buildSettingsJson(false).toString());
            } catch (Throwable t) {
                FileLog.e(TAG + ": Failed to suppress: " + t.getMessage(), t);
            }
        }
        org.telegram.messenger.AndroidUtilities.runOnUIThread(restoreRunnable, SUPPRESS_DURATION_MS);
    }

    public void updateSettings() {
        if (!isRunning || coreClass == null || updateSettingsMethod == null || suppressed) {
            return;
        }
        try {
            updateSettingsMethod.invoke(null, buildSettingsJson(true).toString());
        } catch (Throwable t) {
            FileLog.e(TAG + ": Failed to update settings: " + t.getMessage(), t);
        }
    }

    private JSONObject buildSettingsJson(boolean enabled) throws org.json.JSONException {
        {
            JSONObject json = new JSONObject();
            json.put("enabled", enabled);
            json.put("duration", String.valueOf(ChatsConfig.textAnimationDuration.Int()));
            json.put("blur_enabled", ChatsConfig.textAnimationBlurEnabled.Bool());
            json.put("blur_duration", String.valueOf(ChatsConfig.textAnimationBlurDuration.Int()));
            json.put("blur_radius", String.valueOf(ChatsConfig.textAnimationBlurRadius.Int()));
            json.put("blur_text_delay", String.valueOf(ChatsConfig.textAnimationBlurTextDelay.Int()));
            json.put("slide_enabled", ChatsConfig.textAnimationSlideEnabled.Bool());
            json.put("slide_dist", String.valueOf(ChatsConfig.textAnimationSlideDist.Int()));
            json.put("scale_enabled", ChatsConfig.textAnimationScaleEnabled.Bool());
            float scaleStart = ChatsConfig.textAnimationScaleStart.Int() / 100.0f;
            json.put("scale_start", String.valueOf(scaleStart));
            json.put("rotate_enabled", ChatsConfig.textAnimationRotateEnabled.Bool());
            json.put("rotate_angle", String.valueOf(ChatsConfig.textAnimationRotateAngle.Int()));
            json.put("delete_anim_enabled", ChatsConfig.textAnimationDeleteEnabled.Bool());
            json.put("particle_style", ChatsConfig.textAnimationParticleStyle.Int());
            json.put("particle_count", String.valueOf(ChatsConfig.textAnimationParticleCount.Int()));
            json.put("particle_speed", String.valueOf(ChatsConfig.textAnimationParticleSpeed.Int()));
            json.put("particle_spread", String.valueOf(ChatsConfig.textAnimationParticleSpread.Int()));
            json.put("particle_size", String.valueOf(ChatsConfig.textAnimationParticleSize.Int()));
            boolean cursorEnabled = ChatsConfig.textAnimationCursorEnabled.Bool() && !selectionActive;
            json.put("cursor_enabled", cursorEnabled);
            json.put("cursor_speed", String.valueOf(ChatsConfig.textAnimationCursorSpeed.Int()));
            json.put("cursor_width", String.valueOf(ChatsConfig.textAnimationCursorWidth.Int()));
            json.put("liquid_cursor_enabled", ChatsConfig.textAnimationLiquidCursorEnabled.Bool());
            json.put("liquid_scale_factor", "15");
            json.put("selection_cursor_effect", 0);
            json.put("selection_liquid_stretch", String.valueOf(ChatsConfig.textAnimationLiquidStretch.Int()));
            json.put("selection_liquid_side", "50");
            json.put("ignore_spaces", ChatsConfig.textAnimationIgnoreSpaces.Bool());
            json.put("animate_all_lines", ChatsConfig.textAnimationAnimateAllLines.Bool());
            json.put("debug_mode", false);
            return json;
        }
    }

    public void resetToDefaults() {
        ChatsConfig.textAnimationDuration.setConfigInt(300);
        ChatsConfig.textAnimationBlurEnabled.setConfigBool(true);
        ChatsConfig.textAnimationBlurDuration.setConfigInt(300);
        ChatsConfig.textAnimationBlurRadius.setConfigInt(10);
        ChatsConfig.textAnimationBlurTextDelay.setConfigInt(20);
        ChatsConfig.textAnimationSlideEnabled.setConfigBool(true);
        ChatsConfig.textAnimationSlideDist.setConfigInt(20);
        ChatsConfig.textAnimationScaleEnabled.setConfigBool(false);
        ChatsConfig.textAnimationScaleStart.setConfigInt(0);
        ChatsConfig.textAnimationRotateEnabled.setConfigBool(false);
        ChatsConfig.textAnimationRotateAngle.setConfigInt(-15);
        ChatsConfig.textAnimationDeleteEnabled.setConfigBool(true);
        ChatsConfig.textAnimationParticleStyle.setConfigInt(0);
        ChatsConfig.textAnimationParticleCount.setConfigInt(5);
        ChatsConfig.textAnimationParticleSpeed.setConfigInt(50);
        ChatsConfig.textAnimationParticleSpread.setConfigInt(50);
        ChatsConfig.textAnimationParticleSize.setConfigInt(50);
        ChatsConfig.textAnimationCursorEnabled.setConfigBool(true);
        ChatsConfig.textAnimationCursorSpeed.setConfigInt(25);
        ChatsConfig.textAnimationCursorWidth.setConfigInt(5);
        ChatsConfig.textAnimationLiquidCursorEnabled.setConfigBool(false);
        ChatsConfig.textAnimationLiquidStretch.setConfigInt(60);
        ChatsConfig.textAnimationIgnoreSpaces.setConfigBool(true);
        ChatsConfig.textAnimationAnimateAllLines.setConfigBool(false);
        updateSettings();
    }
}
