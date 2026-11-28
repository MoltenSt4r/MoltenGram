package app.exteraless.chats;

import android.os.Handler;
import android.os.Looper;

import org.telegram.messenger.FileLog;

/**
 * Controls native text typing animations ({@link TextAnimationEngine}).
 * Previously depended on Xposed/LSPlant + external dex; now runs purely in-process
 * directly from {@link org.telegram.ui.Components.EditTextBoldCursor} so it works
 * across Android 8-17, Safe Mode, and Release R8 builds.
 */
public class TextAnimationController {

    private static final String TAG = "TextAnimationController";

    public static final int BULK_CHANGE_THRESHOLD = 30;

    private static volatile TextAnimationController instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean initialized = false;
    private boolean running = false;

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
        if (initialized) {
            return;
        }
        initialized = true;
        TextAnimationEngine.refreshConfig();
        if (isEnabled()) {
            start();
        }
    }

    public boolean isEnabled() {
        return ChatsConfig.textAnimationEnabled.Bool();
    }

    public synchronized boolean isRunning() {
        return running && isEnabled();
    }

    public synchronized void start() {
        try {
            ChatsConfig.textAnimationEnabled.setConfigBool(true);
            TextAnimationEngine.refreshConfig();
            running = true;
        } catch (Throwable e) {
            FileLog.e(TAG + ": start error", e);
        }
    }

    public synchronized void stop() {
        running = false;
        TextAnimationEngine.refreshConfig();
        TextAnimationEngine.clearAll();
    }

    public void setSelectionActive(boolean active) {
        TextAnimationEngine.setGlobalSelectionActive(active);
    }

    public void suppressBriefly() {
        TextAnimationEngine.suppressBriefly(350L);
    }

    public void updateSettings() {
        TextAnimationEngine.refreshConfig();
        if (isEnabled() && !running) {
            running = true;
        } else if (!isEnabled() && running) {
            running = false;
            TextAnimationEngine.clearAll();
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
        mainHandler.post(TextAnimationEngine::refreshConfig);
    }
}
