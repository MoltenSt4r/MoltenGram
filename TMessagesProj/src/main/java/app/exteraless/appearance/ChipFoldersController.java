package app.exteraless.appearance;

import android.content.Context;
import android.os.Build;
import android.view.View;

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
 * Native controller for Material 3 Chip Folders, loaded out of the box from
 * assets/dex/chip_folders.dex.
 */
public class ChipFoldersController {

    private static final String TAG = "ChipFoldersController";
    private static final String ENTRY_CLASS = "dev.rooni.chipfolders.ChipFolders";
    private static final String ASSET_NAME = "dex/chip_folders.dex";

    private static volatile ChipFoldersController instance;

    private Class<?> coreClass;
    private Method startMethod;
    private Method stopMethod;
    private Method updateSettingsMethod;
    private Method refreshPreviewMethod;
    private boolean isRunning;

    public static ChipFoldersController getInstance() {
        if (instance == null) {
            synchronized (ChipFoldersController.class) {
                if (instance == null) {
                    instance = new ChipFoldersController();
                }
            }
        }
        return instance;
    }

    private ChipFoldersController() {
    }

    public synchronized void init() {
        if (isEnabled()) {
            start();
        }
    }

    public boolean isEnabled() {
        return AppearanceConfig.chipFoldersEnabled.Bool();
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
                    File dexFile = new File(context.getCodeCacheDir(), "chip_folders.dex");
                    try (FileOutputStream fos = new FileOutputStream(dexFile)) {
                        fos.write(dexBytes);
                    }
                    classLoader = new DexClassLoader(dexFile.getAbsolutePath(),
                            context.getCodeCacheDir().getAbsolutePath(), null, parent);
                }
            }

            coreClass = classLoader.loadClass(ENTRY_CLASS);
            for (Method method : coreClass.getDeclaredMethods()) {
                if ("start".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    startMethod = method;
                    startMethod.setAccessible(true);
                } else if ("stop".equals(method.getName()) && method.getParameterTypes().length == 0) {
                    stopMethod = method;
                    stopMethod.setAccessible(true);
                } else if ("updateSettings".equals(method.getName()) && method.getParameterTypes().length == 1) {
                    updateSettingsMethod = method;
                    updateSettingsMethod.setAccessible(true);
                } else if ("refreshPreview".equals(method.getName()) && method.getParameterTypes().length == 1) {
                    refreshPreviewMethod = method;
                    refreshPreviewMethod.setAccessible(true);
                }
            }

            return true;
        } catch (Throwable t) {
            FileLog.e(TAG + ": Failed to load DEX: " + t.getMessage(), t);
            return false;
        }
    }

    public synchronized void start() {
        isRunning = true;
    }

    public synchronized void stop() {
        isRunning = false;
    }

    public String buildConfig() {
        return "style:" + AppearanceConfig.chipFoldersStyle.Int()
                + ";shape:" + AppearanceConfig.chipFoldersShape.Int()
                + ";size:" + AppearanceConfig.chipFoldersSize.Int()
                + ";spacing:" + AppearanceConfig.chipFoldersSpacing.Int()
                + ";ra:" + AppearanceConfig.chipFoldersRadiusActive.Int()
                + ";ri:" + AppearanceConfig.chipFoldersRadiusInactive.Int()
                + ";rca:" + AppearanceConfig.chipFoldersRadiusCustomActive.Int()
                + ";ro:" + AppearanceConfig.chipFoldersRadiusOuter.Int()
                + ";ri_in:" + AppearanceConfig.chipFoldersRadiusInner.Int()
                + ";raa:" + AppearanceConfig.chipFoldersRadiusAltActive.Int()
                + ";rao:" + AppearanceConfig.chipFoldersRadiusAltOuter.Int()
                + ";md3:" + (AppearanceConfig.chipFoldersMd3Colors.Bool() ? 1 : 0)
                + ";sdiv:" + (AppearanceConfig.chipFoldersScrollDivider.Bool() ? 1 : 0)
                + ";h:" + AppearanceConfig.chipFoldersCustomHeight.Int()
                + ";sp:" + AppearanceConfig.chipFoldersCustomSpacing.Int()
                + ";lpad:" + AppearanceConfig.chipFoldersListTopPadding.Int()
                + ";bpad:" + AppearanceConfig.chipFoldersBarBottomPadding.Int()
                + ";ca:" + AppearanceConfig.chipFoldersColorActive.Int()
                + ";ci:" + AppearanceConfig.chipFoldersColorInactive.Int()
                + ";ta:" + AppearanceConfig.chipFoldersColorTextActive.Int()
                + ";ti:" + AppearanceConfig.chipFoldersColorTextInactive.Int()
                + ";anim:" + AppearanceConfig.chipFoldersAnim.Int()
                + ";aspd:" + AppearanceConfig.chipFoldersAnimSpeed.Int();
    }

    public void updateSettings() {
    }

    public void refreshPreview(View cell) {
        if (cell == null) {
            return;
        }
        if (coreClass == null && !loadDexIfNeeded()) {
            return;
        }
        try {
            if (refreshPreviewMethod != null) {
                refreshPreviewMethod.invoke(null, cell);
            }
        } catch (Throwable t) {
            FileLog.e(TAG + ": Failed to refresh preview: " + t.getMessage(), t);
        }
    }
}
