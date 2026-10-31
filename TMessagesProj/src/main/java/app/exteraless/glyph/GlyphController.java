package app.exteraless.glyph;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;

import androidx.annotation.OptIn;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;

import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphException;
import com.nothing.ketchum.GlyphFrame;
import com.nothing.ketchum.GlyphManager;
import com.nothing.ketchum.GlyphMatrixFrame;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphMatrixObject;
import com.nothing.ketchum.GlyphMatrixUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Управление Nothing Glyph: светодиодные зоны (Glyph Interface) и матрица Phone (3) / Phone (4a) Pro
 * (Glyph Matrix) через официальный Nothing Glyph Developer Kit (`com.nothing.ketchum`).
 *
 * Архитектура поддерживает все модели Nothing Phone с индивидуальной геометрией светодиодов:
 * - Nothing Phone (1) (20111, A063): 15 зон (A1, B1, C1..C4, E1, D1_1..D1_8)
 * - Nothing Phone (2) (22111, A065/AIN065): 33 зоны (A1, A2, B1, C1_1..C1_16, C2..C6, E1, D1_1..D1_8)
 * - Nothing Phone (2a) (23111, A142) / Phone (2a) Plus (23113, A142P): 26 зон (C_1..C_24, B, A)
 * - Nothing Phone (3a) / Phone (3a) Pro (24111, A059): 36 зон (C_1..C_20, A_1..A_11, B_1..B_5)
 * - Nothing Phone (3a) Lite (24111, A001T): одиночный глиф-индикатор (канал A)
 * - Nothing Phone (4a) / Phone (4b) (25111, A069/A009P): 6 сегментов Glyph Bar (A_1..A_6)
 * - Nothing Phone (3) (23112, A024): Glyph Matrix 25x25
 * - Nothing Phone (4a) Pro (25111p, A069P): Glyph Matrix 13x13
 */
public final class GlyphController {

    private static volatile GlyphController instance;

    public static GlyphController getInstance() {
        GlyphController local = instance;
        if (local == null) {
            synchronized (GlyphController.class) {
                local = instance;
                if (local == null) {
                    local = instance = new GlyphController();
                }
            }
        }
        return local;
    }

    /** Сколько логотип держится на Glyph Matrix при одиночном показе. */
    private static final long LOGO_SHOW_MS = 3000L;

    /** Верхняя граница циклов «дыхания» на записи — страховка, если стоп-ивент потеряется. */
    private static final int RECORDING_MAX_CYCLES = 600;

    /** Шаг и диапазон яркости «дыхания» матрицы (0-255, как в SDK). */
    private static final long BREATH_STEP_MS = 85L;
    private static final int BREATH_STEP = 14;
    private static final int BREATH_MIN = 15;
    private static final int BREATH_MAX = 255;

    /** Страховка от потерянного стоп-ивента записи: ~10 минут анимации матрицы. */
    private static final int RECORDING_MAX_BREATH_STEPS = 7000;

    /** Минимальный интервал между вспышками на сообщения — защита от стробоскопа в активном чате. */
    private static final long FLASH_COOLDOWN_MS = 2500L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean supported;

    private boolean initialized;
    private long lastInitAttemptAt;

    private GlyphManager stripManager;
    private volatile boolean stripSessionOpen;

    private GlyphMatrixManager matrixManager;
    private volatile boolean matrixRegistered;

    private final Runnable hideLogoRunnable = this::hideMatrixLogo;

    private Bitmap matrixLogoBitmap;
    private Bitmap matrixCallBitmap;
    private Bitmap matrixMicBitmap;

    private boolean recordingActive;
    private boolean videoRecordingActive;
    private boolean redIndicatorActive;
    private boolean callActive;
    /** Глифы заняты длительной анимацией (запись или звонок) — вспышки сообщений пропускаем. */
    private boolean glyphBusy;
    private boolean pendingMessageFlash;
    private boolean pendingBurstEffect;
    private int pendingPreviewType = -1;
    private long lastFlashAt;

    private int breathBrightness = BREATH_MAX;
    private int breathDirection = -1;
    private int breathSteps;

    private int callWaveStep = 0;
    private int matrixMessageStep = 0;
    private int matrixMessageStyle = GlyphConfig.MESSAGE_ANIM_DOUBLE_FLASH;

    /**
     * Пошаговая аппаратная анимация волны при звонке для всех полосочных моделей Nothing Phone.
     * Использует детерминированный `stripManager.toggle(frame)` на каждом шаге (90 мс),
     * чтобы сегменты дуг C1_1..C1_16, C_1..C_24, C_1..C_20, D1_1..D1_8 и квадраты A_1..A_6
     * плавно перетекающим бегущим огнём зажигались без обрыва потока `GlyphManager.animate()`.
     */
    private final Runnable callWaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!callActive || stripManager == null || !stripSessionOpen) {
                return;
            }
            try {
                GlyphFrame.Builder b = createStripFrameBuilder();
                if (b == null) {
                    return;
                }
                String targetDev = resolveStripDevice();
                buildWaveStepChannels(b, targetDev, callWaveStep);
                stripManager.toggle(b.build());
            } catch (Throwable t) {
                FileLog.e(t);
            }
            callWaveStep++;
            handler.postDelayed(this, 95L);
        }
    };

    /**
     * Пошаговая анимация входящего сообщения на Glyph Matrix (Phone 3 25x25 и Phone 4a Pro 13x13)
     * в соответствии с выбранным стилем: Double Flash, Strobe, Soft Pulse или Accent Ring.
     */
    private final Runnable matrixMessageAnimRunnable = new Runnable() {
        @Override
        public void run() {
            if (glyphBusy || matrixManager == null || !matrixRegistered) {
                return;
            }
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            int len = getMatrixGridLength();
            boolean showIcon = GlyphConfig.matrixShowIcon();
            Bitmap logo = showIcon ? getMatrixIconBitmap(context, R.drawable.moltengram_icon_monochrome) : null;
            int step = matrixMessageStep++;

            try {
                if (matrixMessageStyle == GlyphConfig.MESSAGE_ANIM_STROBE) {
                    // 3 резких стробоскопических импульса логотипа + угловых маркеров
                    if (step >= 6) {
                        hideMatrixLogo();
                        return;
                    }
                    if (step % 2 == 0) {
                        int[] frame = showIcon ? renderMatrixBitmap(logo, 255, len) : new int[len * len];
                        addMatrixPerimeterRing(frame, len, 1.0f, 3200);
                        if (!showIcon) {
                            addMatrixExpandingRing(frame, len, 0.52f, 3600);
                        }
                        pushMatrixFrame(frame);
                    } else {
                        pushMatrixFrame(new int[len * len]);
                    }
                    handler.postDelayed(this, 85L);
                } else if (matrixMessageStyle == GlyphConfig.MESSAGE_ANIM_SOFT_PULSE) {
                    // Плавное синусоидальное разгорание и затухание (14 кадров)
                    int totalSteps = 14;
                    if (step >= totalSteps) {
                        hideMatrixLogo();
                        return;
                    }
                    double phase = Math.sin(Math.PI * (step + 1) / (double) totalSteps);
                    int brightness = Math.max(18, Math.min(255, (int) Math.round(255.0 * phase)));
                    if (showIcon) {
                        showMatrixIcon(R.drawable.moltengram_icon_monochrome, brightness, false);
                    } else {
                        int[] frame = new int[len * len];
                        addMatrixExpandingRing(frame, len, (float) (0.35 + 0.55 * phase), brightness * 16);
                        pushMatrixFrame(frame);
                    }
                    handler.postDelayed(this, 70L);
                } else if (matrixMessageStyle == GlyphConfig.MESSAGE_ANIM_ACCENT_RING) {
                    // Расходящееся световое кольцо (с инверсией иконки при пересечении) (12 кадров)
                    int totalSteps = 12;
                    if (step >= totalSteps) {
                        hideMatrixLogo();
                        return;
                    }
                    float progress = (step + 1) / (float) totalSteps;
                    int logoBright = step < 9 ? 235 : Math.max(0, 235 - (step - 8) * 65);
                    int[] frame = showIcon ? renderMatrixBitmap(logo, logoBright, len) : new int[len * len];
                    addMatrixExpandingRing(frame, len, progress, 4095);
                    pushMatrixFrame(frame);
                    handler.postDelayed(this, 75L);
                } else {
                    // MESSAGE_ANIM_DOUBLE_FLASH: фирменная двойная вспышка + удержание
                    if (step == 0 || step == 2) {
                        if (showIcon) {
                            showMatrixIcon(R.drawable.moltengram_icon_monochrome, 255, false);
                        } else {
                            int[] frame = new int[len * len];
                            addMatrixPerimeterRing(frame, len, 0.92f, 4095);
                            addMatrixExpandingRing(frame, len, 0.45f, 3800);
                            pushMatrixFrame(frame);
                        }
                        handler.postDelayed(this, 130L);
                    } else if (step == 1) {
                        pushMatrixFrame(new int[len * len]);
                        handler.postDelayed(this, 90L);
                    } else if (step == 3) {
                        if (showIcon) {
                            showMatrixIcon(R.drawable.moltengram_icon_monochrome, 255, true);
                        } else {
                            int[] frame = new int[len * len];
                            addMatrixPerimeterRing(frame, len, 0.92f, 4095);
                            pushMatrixFrame(frame);
                            handler.removeCallbacks(hideLogoRunnable);
                            handler.postDelayed(hideLogoRunnable, 1200L);
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
    };

    /**
     * Длительная анимация Glyph Matrix (Phone 3 / Phone 4a Pro) для звонков и записи голосовых
     * с поддержкой выбранных стилей (Pulse, Wave, Breathing, Accent Ring, Heartbeat).
     */
    private final Runnable breathRunnable = new Runnable() {
        @Override
        public void run() {
            if (!glyphBusy || matrixManager == null || !matrixRegistered) {
                return;
            }
            if (breathSteps++ > RECORDING_MAX_BREATH_STEPS) {
                stopMatrixBreathing();
                return;
            }
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return;
            }
            int len = getMatrixGridLength();
            boolean showIcon = GlyphConfig.matrixShowIcon();
            int iconRes = callActive
                    ? R.drawable.baseline_call_24
                    : ((recordingActive || videoRecordingActive) ? R.drawable.baseline_mic_24 : R.drawable.moltengram_icon_monochrome);
            Bitmap iconBitmap = showIcon ? getMatrixIconBitmap(context, iconRes) : null;

            if (callActive) {
                int callStyle = GlyphConfig.callAnimationStyle();
                if (callStyle == GlyphConfig.CALL_ANIM_WAVE) {
                    // Вращающаяся радарная волна по периметру матрицы (+ иконка трубки в центре с инверсией)
                    int[] frame = showIcon ? renderMatrixBitmap(iconBitmap, 225, len) : new int[len * len];
                    float angle = (breathSteps * 24f) % 360f;
                    addMatrixRadarWave(frame, len, angle, 4095);
                    pushMatrixFrame(frame);
                    handler.postDelayed(this, 65L);
                    return;
                } else if (callStyle == GlyphConfig.CALL_ANIM_PULSE) {
                    // Пульсирующая трубка + расходящиеся звуковые кольца (с инверсией иконки при пересечении)
                    float ringPhase = (breathSteps % 10) / 9.0f;
                    int iconBright = (breathSteps % 10 < 5) ? 255 : 130;
                    int[] frame = showIcon ? renderMatrixBitmap(iconBitmap, iconBright, len) : new int[len * len];
                    addMatrixExpandingRing(frame, len, ringPhase, 3800);
                    pushMatrixFrame(frame);
                    handler.postDelayed(this, 75L);
                    return;
                }
            } else if (recordingActive || videoRecordingActive) {
                int recStyle = GlyphConfig.recordingAnimationStyle();
                if (recStyle == GlyphConfig.RECORDING_ANIM_ACCENT_RING) {
                    // Заполняющееся кольцо прогресса вокруг микрофона
                    int[] frame = showIcon ? renderMatrixBitmap(iconBitmap, 210, len) : new int[len * len];
                    float progress = ((breathSteps % 20) + 1) / 20.0f;
                    addMatrixPerimeterProgress(frame, len, progress, 4095);
                    pushMatrixFrame(frame);
                    handler.postDelayed(this, 80L);
                    return;
                } else if (recStyle == GlyphConfig.RECORDING_ANIM_HEARTBEAT) {
                    // Двойной удар сердца (lub-dub)
                    int phase = breathSteps % 12;
                    int hbBright;
                    if (phase == 0 || phase == 1) {
                        hbBright = 255;
                    } else if (phase == 2) {
                        hbBright = 60;
                    } else if (phase == 3 || phase == 4) {
                        hbBright = 220;
                    } else {
                        hbBright = Math.max(20, 90 - (phase - 5) * 12);
                    }
                    int[] frame = showIcon ? renderMatrixBitmap(iconBitmap, hbBright, len) : new int[len * len];
                    if (phase == 0 || phase == 3 || !showIcon) {
                        addMatrixPerimeterRing(frame, len, 0.88f, hbBright * 15);
                    }
                    pushMatrixFrame(frame);
                    handler.postDelayed(this, 80L);
                    return;
                }
            }

            // Плавное классическое дыхание (CALL_ANIM_BREATHING / RECORDING_ANIM_BREATHING)
            int step = callActive ? 20 : BREATH_STEP;
            breathBrightness += breathDirection * step;
            if (breathBrightness <= BREATH_MIN) {
                breathBrightness = BREATH_MIN;
                breathDirection = 1;
            } else if (breathBrightness >= BREATH_MAX) {
                breathBrightness = BREATH_MAX;
                breathDirection = -1;
            }
            if (showIcon) {
                showMatrixIcon(iconRes, breathBrightness, false);
            } else {
                int[] frame = new int[len * len];
                float breathFrac = 0.42f + 0.50f * (breathBrightness / 255.0f);
                addMatrixExpandingRing(frame, len, breathFrac, breathBrightness * 16);
                pushMatrixFrame(frame);
            }
            long delay = callActive ? 65L : BREATH_STEP_MS;
            handler.postDelayed(this, delay);
        }
    };

    private GlyphController() {
        supported = isNothingGlyphDevice();
    }

    public boolean isSupported() {
        return supported;
    }

    /** Идемпотентный биндинг к системному сервису глифов. */
    public synchronized void init() {
        if (!supported) {
            return;
        }
        if (initialized && (stripSessionOpen || matrixRegistered)) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (initialized && now - lastInitAttemptAt < 2500L) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        lastInitAttemptAt = now;
        try {
            if (isMatrixDevice()) {
                initMatrix(context);
            } else {
                initStrip(context);
            }
            initialized = true;
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /** Отбиндить сервис — вызывается при выключении фичи в настройках. */
    public synchronized void shutdown() {
        initialized = false;
        lastInitAttemptAt = 0;
        stripSessionOpen = false;
        matrixRegistered = false;
        previewPending = false;
        pendingPreviewType = -1;
        pendingMessageFlash = false;
        pendingBurstEffect = false;
        recordingActive = false;
        videoRecordingActive = false;
        if (redIndicatorActive) {
            redIndicatorActive = false;
            setRedRecordingIndicatorHardware(false);
        }
        callActive = false;
        glyphBusy = false;
        lastFlashAt = 0;
        handler.removeCallbacks(hideLogoRunnable);
        handler.removeCallbacks(breathRunnable);
        handler.removeCallbacks(matrixMessageAnimRunnable);
        handler.removeCallbacks(callWaveRunnable);
        try {
            if (stripManager != null) {
                try {
                    stripManager.turnOff();
                } catch (Throwable ignored) {}
                try {
                    stripManager.closeSession();
                } catch (Throwable ignored) {}
                stripManager.unInit();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        stripManager = null;
        try {
            if (matrixManager != null) {
                try {
                    matrixManager.closeAppMatrix();
                } catch (Throwable ignored) {}
                matrixManager.unInit();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        matrixManager = null;
    }

    public void onNewMessages(List<MessageObject> messages, int account) {
        if (!GlyphConfig.enabled() || !GlyphConfig.onNewMessage() || messages == null) {
            return;
        }
        // Хук сидит до фильтрации уведомлений, поэтому мьют проверяем сами:
        // вспышка без уведомления на экране вводит в заблуждение.
        MessagesController messagesController = MessagesController.getInstance(account);
        boolean hasIncoming = false;
        for (int i = 0; i < messages.size(); i++) {
            MessageObject message = messages.get(i);
            if (message != null && !message.isOutOwner()
                    && !messagesController.isDialogMuted(message.getDialogId())) {
                hasIncoming = true;
                break;
            }
        }
        if (!hasIncoming) {
            return;
        }
        handler.post(() -> {
            if (GlyphConfig.screenOffOnly() && isScreenOn()) {
                return;
            }
            if (glyphBusy) {
                return;
            }
            if (!ensureReady()) {
                pendingMessageFlash = true;
                return;
            }
            triggerMessageFlashNow();
        });
    }

    private void triggerMessageFlashNow() {
        if (glyphBusy) {
            return;
        }
        if (GlyphConfig.screenOffOnly() && isScreenOn()) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastFlashAt < FLASH_COOLDOWN_MS) {
            return;
        }
        lastFlashAt = now;
        if (matrixRegistered) {
            pulseMatrix(GlyphConfig.messageAnimationStyle());
        } else {
            pulseStrip(GlyphConfig.messageAnimationStyle());
        }
    }

    public void onRecordingStarted() {
        if (!GlyphConfig.enabled() || !GlyphConfig.onRecording()) {
            return;
        }
        handler.post(() -> {
            recordingActive = true;
            updateGlyphActivity();
        });
    }

    public void onRecordingStopped() {
        // Стоп не проверяет конфиг: гасить подсветку нужно всегда, даже если
        // тумблер выкрутили посреди записи.
        handler.post(() -> {
            recordingActive = false;
            updateGlyphActivity();
        });
    }

    /**
     * Запущена запись кружка (видеосообщения) или видео (`CameraView` / `RoundVideoRecorder` / `CameraController`).
     * Зажигает красный индикатор записи (Rec. Light) на поддерживаемых моделях Nothing Phone
     * и/или запускает свечение глифов при записи видео.
     */
    public void onVideoRecordingStarted() {
        if (!GlyphConfig.enabled()) {
            return;
        }
        final boolean wantGlyph = GlyphConfig.onVideoRecording();
        final boolean wantRedLed = GlyphConfig.redRecordingIndicator() && isRedIndicatorSupported();
        if (!wantGlyph && !wantRedLed) {
            return;
        }
        handler.post(() -> {
            if (wantRedLed) {
                redIndicatorActive = true;
                setRedRecordingIndicatorHardware(true);
            }
            if (wantGlyph) {
                videoRecordingActive = true;
                updateGlyphActivity();
            }
        });
    }

    /**
     * Остановлена запись кружка или видео — безусловно гасит красный индикатор записи и глифы.
     */
    public void onVideoRecordingStopped() {
        handler.post(() -> {
            videoRecordingActive = false;
            if (redIndicatorActive) {
                redIndicatorActive = false;
                setRedRecordingIndicatorHardware(false);
            }
            updateGlyphActivity();
        });
    }

    /**
     * Проверяет, поддерживает ли устройство Nothing Phone задний физический красный индикатор записи (Rec. Light).
     * Физический красный светодиод Rec. Light существует ТОЛЬКО на 6 моделях:
     * Nothing Phone (1), Phone (2), Phone (3), Phone (4a) Pro, Phone (4a) и Phone (4b).
     * На моделях Phone (2a), Phone (2a) Plus, Phone (3a), Phone (3a) Pro, Phone (3a) Lite и CMF его физически нет!
     */
    public static boolean isRedIndicatorSupported() {
        String name = NothingPhoneEasterEgg.getDeviceDisplayName();
        return isRedIndicatorSupportedForModel(name);
    }

    public static boolean isRedIndicatorSupportedForModel(String modelName) {
        if (modelName == null) {
            return false;
        }
        if ("Nothing Phone (1)".equals(modelName)
                || "Nothing Phone (2)".equals(modelName)
                || "Nothing Phone (3)".equals(modelName)
                || "Nothing Phone (4a) Pro".equals(modelName)
                || "Nothing Phone (4a)".equals(modelName)
                || "Nothing Phone (4b)".equals(modelName)) {
            return true;
        }
        String normalized = modelName.trim().replace("(", "").replace(")", "").replaceAll("\\s+", " ");
        return "Nothing Phone 1".equalsIgnoreCase(normalized)
                || "Nothing Phone 2".equalsIgnoreCase(normalized)
                || "Nothing Phone 3".equalsIgnoreCase(normalized)
                || "Nothing Phone 4a Pro".equalsIgnoreCase(normalized)
                || "Nothing Phone 4a".equalsIgnoreCase(normalized)
                || "Nothing Phone 4b".equalsIgnoreCase(normalized);
    }

    private static final String[] RED_LED_SYSFS_PATHS = new String[]{
            "/sys/class/leds/red/brightness",
            "/sys/class/leds/led:red/brightness",
            "/sys/class/leds/aw20036:led_red/brightness",
            "/sys/class/leds/aw210xx_led/red_led_br",
            "/sys/class/leds/vibrator_red/brightness",
            "/sys/class/leds/rec_led/brightness",
            "/sys/class/leds/front_cam_led_br/brightness"
    };

    /**
     * Управляет ТОЛЬКО физическим красным светодиодом записи (Rec. Light) на задней панели Nothing Phone
     * (без включения белых глифов!) через системные настройки Nothing OS, прямую запись в sysfs
     * или привилегированную команду Shizuku / su (если доступно).
     */
    public static void setRedRecordingIndicatorHardware(boolean active) {
        Context context = ApplicationLoader.applicationContext;
        if (context != null) {
            try {
                android.content.ContentResolver cr = context.getContentResolver();
                int val = active ? 1 : 0;
                try {
                    android.provider.Settings.System.putInt(cr, "led_effect_video_record_enable", val);
                } catch (Throwable ignored) {}
                try {
                    android.provider.Settings.System.putInt(cr, "nt_camera_rec_light", val);
                } catch (Throwable ignored) {}
                try {
                    android.provider.Settings.System.putInt(cr, "camera_rec_light", val);
                } catch (Throwable ignored) {}
                try {
                    android.provider.Settings.Global.putInt(cr, "nothing_camera_red_light_active", val);
                } catch (Throwable ignored) {}
                try {
                    android.provider.Settings.Global.putInt(cr, "nt_camera_rec_light", val);
                } catch (Throwable ignored) {}
                try {
                    android.provider.Settings.Global.putInt(cr, "led_effect_video_record_enable", val);
                } catch (Throwable ignored) {}
                try {
                    android.provider.Settings.Secure.putInt(cr, "nt_camera_rec_light", val);
                } catch (Throwable ignored) {}
                try {
                    android.provider.Settings.Secure.putInt(cr, "led_effect_video_record_enable", val);
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
        }
        final String payload = active ? "255" : "0";
        final String stripPayload = active ? "13 255" : "13 0";
        boolean writtenDirectly = false;
        // Phone (1) и Phone (2): Awinic контроллер через led_strips
        try {
            java.io.File opMode = new java.io.File("/sys/class/leds/led_strips/operating_mode");
            if (opMode.exists() && opMode.canWrite()) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(opMode)) {
                    fos.write("1".getBytes());
                    fos.flush();
                }
            }
            java.io.File singleB = new java.io.File("/sys/class/leds/led_strips/single_brightness");
            if (singleB.exists() && singleB.canWrite()) {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(singleB)) {
                    fos.write(stripPayload.getBytes());
                    fos.flush();
                    writtenDirectly = true;
                }
            }
        } catch (Throwable ignored) {}

        for (String path : RED_LED_SYSFS_PATHS) {
            try {
                java.io.File f = new java.io.File(path);
                if (f.exists() && f.canWrite()) {
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
                        fos.write(payload.getBytes());
                        fos.flush();
                        writtenDirectly = true;
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (!writtenDirectly) {
            new Thread(() -> {
                String shellCmd = "if [ -e /sys/class/leds/led_strips/operating_mode ]; then echo 1 > /sys/class/leds/led_strips/operating_mode 2>/dev/null; fi; "
                        + "if [ -e /sys/class/leds/led_strips/single_brightness ]; then echo \"" + stripPayload + "\" > /sys/class/leds/led_strips/single_brightness 2>/dev/null; fi; "
                        + "for p in /sys/class/leds/red/brightness /sys/class/leds/led:red/brightness /sys/class/leds/aw20036:led_red/brightness /sys/class/leds/rec_led/brightness; do "
                        + "if [ -e \"$p\" ]; then echo " + payload + " > \"$p\" 2>/dev/null; fi; done";
                try {
                    app.exteraless.shizuku.ShizukuController shizuku = app.exteraless.shizuku.ShizukuController.getInstance();
                    if (shizuku != null && shizuku.hasPermission()) {
                        shizuku.runCommand(shellCmd);
                        return;
                    }
                } catch (Throwable ignored) {}
                try {
                    Process su = Runtime.getRuntime().exec(new String[]{"su", "-c", shellCmd});
                    su.waitFor();
                } catch (Throwable ignored) {}
            }).start();
        }
    }

    /**
     * Применяет vendor-теги Nothing OS Camera2 (`CaptureRequest`) для включения/выключения
     * красного светодиода записи (`Rec. Light`) во время видеозаписи кружка или видео.
     */
    @SuppressWarnings("unchecked")
    public static void applyNothingCameraRedIndicator(
            android.hardware.camera2.CameraCharacteristics characteristics,
            android.hardware.camera2.CaptureRequest.Builder builder,
            boolean recordingVideo) {
        if (characteristics == null || builder == null) {
            return;
        }
        boolean enableRedLight = recordingVideo
                && GlyphConfig.enabled()
                && GlyphConfig.redRecordingIndicator()
                && isRedIndicatorSupported();
        try {
            List<android.hardware.camera2.CaptureRequest.Key<?>> keys = characteristics.getAvailableCaptureRequestKeys();
            if (keys == null) {
                return;
            }
            for (android.hardware.camera2.CaptureRequest.Key<?> key : keys) {
                if (key == null) continue;
                String name = key.getName();
                if (name == null) continue;
                String lower = name.toLowerCase();
                if (lower.contains("red_light") || lower.contains("reclight") || lower.contains("rec_light")
                        || lower.contains("recording_led") || lower.contains("record_led") || lower.contains("record_light")
                        || lower.contains("red_dot") || lower.contains("tally") || lower.contains("rec_indicator")
                        || lower.contains("recording_indicator")
                        || (lower.contains("nothing") && lower.contains("led"))) {
                    byte bVal = (byte) (enableRedLight ? 1 : 0);
                    int iVal = enableRedLight ? 1 : 0;
                    try {
                        ((android.hardware.camera2.CaptureRequest.Key<Byte>) key).getName();
                        builder.set((android.hardware.camera2.CaptureRequest.Key<Byte>) key, bVal);
                    } catch (Throwable ignored) {
                        try {
                            builder.set((android.hardware.camera2.CaptureRequest.Key<Integer>) key, iVal);
                        } catch (Throwable ignored2) {
                            try {
                                builder.set((android.hardware.camera2.CaptureRequest.Key<byte[]>) key, new byte[]{bVal});
                            } catch (Throwable ignored3) {
                                try {
                                    builder.set((android.hardware.camera2.CaptureRequest.Key<int[]>) key, new int[]{iVal});
                                } catch (Throwable ignored4) {}
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Применяет vendor-теги Nothing OS Camera2 через CameraX Camera2Interop.Extender
     * для включения/выключения красного светодиода записи (Rec. Light) во время видеозаписи кружка.
     */
    @OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
    @SuppressWarnings("unchecked")
    public static void applyNothingCameraRedIndicator(
            CameraCharacteristics characteristics,
            Camera2Interop.Extender<?> extender,
            boolean recordingVideo) {
        if (characteristics == null || extender == null) {
            return;
        }
        boolean enableRedLight = recordingVideo
                && GlyphConfig.enabled()
                && GlyphConfig.redRecordingIndicator()
                && isRedIndicatorSupported();
        try {
            List<CaptureRequest.Key<?>> keys = characteristics.getAvailableCaptureRequestKeys();
            if (keys == null) {
                return;
            }
            for (CaptureRequest.Key<?> key : keys) {
                if (key == null) continue;
                String name = key.getName();
                if (name == null) continue;
                String lower = name.toLowerCase();
                if (lower.contains("red_light") || lower.contains("reclight") || lower.contains("rec_light")
                        || lower.contains("recording_led") || lower.contains("record_led") || lower.contains("record_light")
                        || lower.contains("red_dot") || lower.contains("tally") || lower.contains("rec_indicator")
                        || lower.contains("recording_indicator")
                        || (lower.contains("nothing") && lower.contains("led"))) {
                    byte bVal = (byte) (enableRedLight ? 1 : 0);
                    int iVal = enableRedLight ? 1 : 0;
                    try {
                        extender.setCaptureRequestOption((CaptureRequest.Key<Byte>) key, bVal);
                    } catch (Throwable ignored) {
                        try {
                            extender.setCaptureRequestOption((CaptureRequest.Key<Integer>) key, iVal);
                        } catch (Throwable ignored2) {
                            try {
                                extender.setCaptureRequestOption((CaptureRequest.Key<byte[]>) key, new byte[]{bVal});
                            } catch (Throwable ignored3) {
                                try {
                                    extender.setCaptureRequestOption((CaptureRequest.Key<int[]>) key, new int[]{iVal});
                                } catch (Throwable ignored4) {}
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Входящий VoIP-звонок зазвонил — дышим, пока не ответят или не сбросят. */
    public void onIncomingCallStarted() {
        if (!GlyphConfig.enabled() || !GlyphConfig.onCall()) {
            return;
        }
        handler.post(() -> {
            callActive = true;
            updateGlyphActivity();
        });
    }

    /** Звонок приняли, сбросили или сервис умер — идемпотентно. */
    public void onIncomingCallStopped() {
        handler.post(() -> {
            callActive = false;
            updateGlyphActivity();
        });
    }

    /**
     * Единая точка входа для длительных анимаций: анимация живёт, пока активна
     * хотя бы одна причина (запись голосового или входящий звонок).
     */
    private void updateGlyphActivity() {
        boolean active = recordingActive || videoRecordingActive || callActive;
        if (!active) {
            glyphBusy = false;
            stopMatrixBreathing();
            handler.removeCallbacks(callWaveRunnable);
            if (stripManager != null && stripSessionOpen) {
                try {
                    stripManager.turnOff();
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
            return;
        }
        if (!ensureReady()) {
            return;
        }
        glyphBusy = true;
        if (matrixRegistered) {
            startMatrixBreathing();
        } else if (stripSessionOpen) {
            if (callActive) {
                startCallStrip();
            } else if (recordingActive || videoRecordingActive) {
                startRecordingStrip();
            }
        }
    }

    private boolean isScreenOn() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return true;
        }
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return powerManager == null || powerManager.isInteractive();
    }

    /** Превью, отложенное до подключения сервиса глифов (бинд асинхронный). */
    private boolean previewPending;

    /** Кнопка «Предпросмотр» в настройках: логотип на матрице и/или вспышка зон. */
    public void preview() {
        previewMessage();
    }

    public void previewMessage() {
        handler.post(() -> {
            if (!ensureReady()) {
                previewPending = supported;
                pendingPreviewType = 0;
                return;
            }
            // Если шло предыдущее превью звонка/записи, останавливаем его перед превью сообщения
            if (callActive || recordingActive || videoRecordingActive) {
                callActive = false;
                recordingActive = false;
                videoRecordingActive = false;
                updateGlyphActivity();
            }
            if (matrixRegistered) {
                pulseMatrix(GlyphConfig.messageAnimationStyle());
            } else {
                pulseStrip(GlyphConfig.messageAnimationStyle());
            }
        });
    }

    public void previewCall() {
        handler.post(() -> {
            if (!ensureReady()) {
                previewPending = supported;
                pendingPreviewType = 1;
                return;
            }
            recordingActive = false;
            callActive = true;
            updateGlyphActivity();
            handler.postDelayed(() -> {
                callActive = false;
                updateGlyphActivity();
            }, 2600L);
        });
    }

    public void previewRecording() {
        handler.post(() -> {
            if (!ensureReady()) {
                previewPending = supported;
                pendingPreviewType = 2;
                return;
            }
            callActive = false;
            recordingActive = true;
            updateGlyphActivity();
            handler.postDelayed(() -> {
                recordingActive = false;
                updateGlyphActivity();
            }, 2600L);
        });
    }

    /**
     * Изолированный предпросмотр ТОЛЬКО физического красного светодиода записи (Rec. Light)
     * без запуска белых глифов или матричной анимации.
     */
    public void previewRedIndicatorOnly() {
        handler.post(() -> {
            callActive = false;
            recordingActive = false;
            videoRecordingActive = false;
            previewPending = false;
            pendingPreviewType = -1;
            updateGlyphActivity();
            if (GlyphConfig.redRecordingIndicator() && isRedIndicatorSupported()) {
                redIndicatorActive = true;
                setRedRecordingIndicatorHardware(true);
                handler.postDelayed(() -> {
                    if (redIndicatorActive && !videoRecordingActive) {
                        redIndicatorActive = false;
                        setRedRecordingIndicatorHardware(false);
                    }
                }, 2200L);
            } else if (redIndicatorActive) {
                redIndicatorActive = false;
                setRedRecordingIndicatorHardware(false);
            }
        });
    }

    public static boolean isPhone1() {
        String name = NothingPhoneEasterEgg.getDeviceDisplayName();
        if ("Nothing Phone (1)".equals(name)) {
            return true;
        }
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        String product = Build.PRODUCT != null ? Build.PRODUCT.toUpperCase() : "";
        String board = Build.BOARD != null ? Build.BOARD.toUpperCase() : "";
        return model.contains("A063") || model.contains("PHONE (1)") || model.contains("PHONE 1")
                || device.contains("SPACEWAR") || product.contains("SPACEWAR") || board.contains("SPACEWAR");
    }

    public static boolean isNothingGlyphDevice() {
        if (isPhone1()) {
            return true;
        }
        String name = NothingPhoneEasterEgg.getDeviceDisplayName();
        if (name != null && name.startsWith("CMF")) {
            return false;
        }
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toUpperCase() : "";
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        if (model.contains("CMF") || brand.contains("CMF") || manufacturer.contains("CMF")
                || model.contains("A015") || (model.contains("A001") && !model.contains("A001T"))
                || device.contains("TETRIS") || device.contains("GALAGA")) {
            return false;
        }
        return NothingPhoneEasterEgg.isNothingOrCmfDevice();
    }

    /**
     * Возвращает канонический код устройства Nothing Glyph SDK (`Glyph.DEVICE_*`)
     * для полосочных (Strip) моделей.
     *
     * ВАЖНО: `GlyphFrame.Builder(device)` внутри `glyph-matrix-sdk-2.0.aar` проверяет
     * `Common.isTargetDevice*(device)` и принимает ТОЛЬКО константы `Glyph.DEVICE_*`:
     * - `"20111"` (Phone 1, 15 каналов)
     * - `"22111"` (Phone 2, 33 канала)
     * - `"23111"` / `"23113"` (Phone 2a / 2a Plus, 26 каналов)
     * - `"24111"` (Phone 3a / 3a Pro / 3a Lite, 36 каналов)
     * - `"25111"` (Phone 4a / 4b, 6 каналов)
     */
    public static String resolveStripDevice() {
        if (isPhone1()) {
            return Glyph.DEVICE_20111;
        }
        try {
            if (Common.is20111()) return Glyph.DEVICE_20111;
            if (Common.is22111()) return Glyph.DEVICE_22111;
            if (Common.is23113()) return Glyph.DEVICE_23113;
            if (Common.is23111()) return Glyph.DEVICE_23111;
            if (Common.is24111()) return Glyph.DEVICE_24111;
            if (Common.is25111()) return Glyph.DEVICE_25111;
        } catch (Throwable ignored) {}

        String name = NothingPhoneEasterEgg.getDeviceDisplayName();
        if ("Nothing Phone (1)".equals(name)) return Glyph.DEVICE_20111;
        if ("Nothing Phone (2)".equals(name)) return Glyph.DEVICE_22111;
        if ("Nothing Phone (2a) Plus".equals(name)) return Glyph.DEVICE_23113;
        if ("Nothing Phone (2a)".equals(name)) return Glyph.DEVICE_23111;
        if ("Nothing Phone (4a)".equals(name) || "Nothing Phone (4b)".equals(name)) return Glyph.DEVICE_25111;
        if ("Nothing Phone (3a) Pro".equals(name) || "Nothing Phone (3a)".equals(name) || "Nothing Phone (3a) Lite".equals(name)) {
            return Glyph.DEVICE_24111;
        }

        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";

        // Phone (4a) / Phone (4b) -> A069 / A009P (6 квадратных сегментов Glyph Bar)
        if (model.contains("A069") || model.contains("A009") || model.contains("4A") || model.contains("4B")
                || device.contains("FROGGER") || device.contains("BLASTOISE") || device.contains("SUPERCONTRA")) {
            return Glyph.DEVICE_25111;
        }
        // Phone (3a) Pro / Phone (3a) / Phone (3a) Lite -> A059 / A001T
        if (model.contains("A059") || model.contains("A001T") || model.contains("3A")
                || device.contains("GALAXIAN") || device.contains("ASTEROIDS")) {
            return Glyph.DEVICE_24111;
        }
        // Phone (2a) Plus -> A142P
        if (model.contains("A142P") || model.contains("2A PLUS") || model.contains("2A+") || device.contains("PACMANPRO")) {
            return Glyph.DEVICE_23113;
        }
        // Phone (2a) -> A142
        if (model.contains("A142") || model.contains("2A") || device.contains("PACMAN") || device.contains("AERODACTYL")) {
            return Glyph.DEVICE_23111;
        }
        // Phone (2) -> A065 / AIN065
        if (model.contains("A065") || model.contains("AIN065") || model.contains("PHONE (2)") || model.contains("PHONE 2") || device.contains("PONG")) {
            return Glyph.DEVICE_22111;
        }
        // Phone (1) -> A063
        if (model.contains("A063") || isPhone1()) {
            return Glyph.DEVICE_20111;
        }
        return Glyph.DEVICE_20111;
    }

    public static String getAppKey(Context context) {
        if (context == null) {
            return null;
        }
        try {
            android.content.pm.ApplicationInfo ai = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), android.content.pm.PackageManager.GET_META_DATA);
            if (ai != null && ai.metaData != null) {
                return ai.metaData.getString("NothingKey");
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return null;
    }

    /**
     * Nothing Phone glyph burst particle effect with haptic feedback.
     */
    public void playPhone1BurstEffect(Context context) {
        handler.post(() -> {
            if (!ensureReady()) {
                pendingBurstEffect = true;
                return;
            }
            runBurstHardwareLight();
            if (context != null) {
                try {
                    android.os.Vibrator vibrator = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            long[] timings = new long[]{0, 40, 30, 60, 30, 80};
                            int[] amplitudes = new int[]{0, 180, 0, 220, 0, 255};
                            vibrator.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1));
                        } else {
                            vibrator.vibrate(new long[]{0, 40, 30, 60, 30, 80}, -1);
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
        });
    }

    private void runBurstHardwareLight() {
        if (matrixManager != null && matrixRegistered) {
            pulseMatrix(GlyphConfig.MESSAGE_ANIM_ACCENT_RING);
        }
        if (stripManager != null && stripSessionOpen) {
            try {
                GlyphFrame.Builder builder = createStripFrameBuilder();
                if (builder != null) {
                    applyFullChannels(builder);
                    builder.buildPeriod(120)
                            .buildCycles(3)
                            .buildInterval(40);
                    stripManager.animate(builder.build());
                }
            } catch (Throwable t) {
                FileLog.e(t);
                flashDirectFallback();
            }
        } else if (!matrixRegistered) {
            flashDirectFallback();
        }
    }

    private void runPendingPreview() {
        handler.post(() -> {
            if (recordingActive || callActive) {
                updateGlyphActivity();
            }
            if (pendingBurstEffect) {
                pendingBurstEffect = false;
                runBurstHardwareLight();
            }
            if (pendingMessageFlash) {
                pendingMessageFlash = false;
                triggerMessageFlashNow();
            }
            if (previewPending) {
                previewPending = false;
                int type = pendingPreviewType;
                pendingPreviewType = -1;
                if (type == 1) {
                    previewCall();
                } else if (type == 2) {
                    previewRecording();
                } else {
                    previewMessage();
                }
            }
        });
    }

    private boolean ensureReady() {
        if (!supported) {
            return false;
        }
        if (!initialized || (!stripSessionOpen && !matrixRegistered)) {
            init();
        }
        return initialized && (stripSessionOpen || matrixRegistered);
    }

    public static boolean isMatrixDevice() {
        try {
            if (Common.is23112() || Common.is25111p()) {
                return true;
            }
        } catch (Throwable ignored) {}
        String name = NothingPhoneEasterEgg.getDeviceDisplayName();
        if ("Nothing Phone (3)".equals(name) || "Nothing Phone (4a) Pro".equals(name)) {
            return true;
        }
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        return model.contains("A024") || model.contains("A069P") || model.contains("PHONE (3)") || model.contains("PHONE 3")
                || (model.contains("4A") && model.contains("PRO"))
                || device.contains("METROID") || device.contains("FROGGERPRO");
    }

    public static String resolveMatrixDevice() {
        try {
            if (Common.is23112()) return Glyph.DEVICE_23112;
            if (Common.is25111p()) return Glyph.DEVICE_25111p;
        } catch (Throwable ignored) {}
        String name = NothingPhoneEasterEgg.getDeviceDisplayName();
        if ("Nothing Phone (4a) Pro".equals(name)) {
            return Glyph.DEVICE_25111p;
        }
        if ("Nothing Phone (3)".equals(name)) {
            return Glyph.DEVICE_23112;
        }
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        if (model.contains("A069P") || (model.contains("4A") && model.contains("PRO")) || device.contains("FROGGERPRO")) {
            return Glyph.DEVICE_25111p;
        }
        return Glyph.DEVICE_23112;
    }

    public static int getStripChannelCount(String device) {
        if (Glyph.DEVICE_20111.equals(device)) return 15;
        if (Glyph.DEVICE_22111.equals(device) || Glyph.DEVICE_22111I.equals(device)) return 33;
        if (Glyph.DEVICE_23111.equals(device) || Glyph.DEVICE_23113.equals(device)) return 26;
        if (Glyph.DEVICE_24111.equals(device)) return 36;
        if (Glyph.DEVICE_25111.equals(device)) return 6;
        return isPhone1() ? 15 : 33;
    }

    /**
     * Создаёт `GlyphFrame.Builder` с гарантированно валидным кодом устройства `Glyph.DEVICE_*`.
     * Если `stripManager.register()` перезаписал `mDevice = Build.MODEL` (например `"A063"`),
     * восстанавливает `mDevice = resolveStripDevice()`, чтобы `Common.getTargetDeviceGlyphChannelSize()`
     * вернул точное число каналов модели (15, 33, 26, 36 или 6).
     */
    private GlyphFrame.Builder createStripFrameBuilder() {
        String targetDevice = resolveStripDevice();
        if (stripManager != null) {
            try {
                Field devField = GlyphManager.class.getDeclaredField("mDevice");
                devField.setAccessible(true);
                Object cur = devField.get(stripManager);
                if (cur == null || !targetDevice.equals(cur)) {
                    devField.set(stripManager, targetDevice);
                }
                GlyphFrame.Builder b = stripManager.getGlyphFrameBuilder();
                if (b != null) {
                    return b;
                }
            } catch (Throwable ignored) {}
        }
        try {
            return new GlyphFrame.Builder(targetDevice);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    /**
     * Формирует один кадр бегущей волны (`CALL_ANIM_WAVE`) с учётом точной топологии зон каждой модели:
     * - Phone (1) (`20111`, 15 LED): волна снизу вверх `E1 -> D1_8..D1_1 -> C1..C4 -> A1 -> B1`
     * - Phone (2) (`22111`, 33 LED): волна по сегментам `E1 -> D1_8..D1_1 -> C2..C6 -> C1_1..C1_16 -> A2 -> A1 -> B1`
     * - Phone (2a / 2a Plus) (`23111`/`23113`, 26 LED): бегущая дуга по 24 сегментам `C_1..C_24 -> B -> A`
     * - Phone (3a / 3a Pro) (`24111`, 36 LED): бегущая волна по 3 полоскам `A_1..A_11 -> B_1..B_5 -> C_1..C_20`
     * - Phone (3a) Lite (`24111` Lite): пульсирующий одиночный глиф A
     * - Phone (4a / 4b) (`25111`, 6 LED): ping-pong бегущий огонь по 6 квадратам `A_1..A_6`
     */
    public static void buildWaveStepChannels(GlyphFrame.Builder b, String targetDev, int step) {
        if (b == null) {
            return;
        }
        if (Glyph.DEVICE_25111.equals(targetDev)) {
            // Nothing Phone (4a / 4b): ping-pong по 6 квадратам Glyph Bar (0..5..0)
            int cycle = step % 10;
            int head = cycle < 6 ? cycle : (10 - cycle);
            int tail = Math.max(0, Math.min(5, cycle < 6 ? head - 1 : head + 1));
            try {
                b.buildChannel(head, 4095);
                if (tail != head) {
                    b.buildChannel(tail, 1600);
                }
            } catch (Throwable ignored) {
                b.buildChannel(head);
            }
        } else if (Glyph.DEVICE_24111.equals(targetDev)) {
            if ("Nothing Phone (3a) Lite".equals(NothingPhoneEasterEgg.getDeviceDisplayName())) {
                int bright = (step % 2 == 0) ? 4095 : 900;
                for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11; i++) {
                    try { b.buildChannel(i, bright); } catch (Throwable ignored) {}
                }
            } else {
                // Nothing Phone (3a / 3a Pro): 9 фаз плавного перетекания по 36 светодиодам (A_1..A_11, B_1..B_5, C_1..C_20)
                int phase = step % 9;
                if (phase < 3) {
                    // Секция A (20..30)
                    int start = Glyph.Code_24111.A_1 + phase * 3;
                    int end = Math.min(Glyph.Code_24111.A_11, start + 4);
                    for (int i = start; i <= end; i++) {
                        try { b.buildChannel(i, 4095); } catch (Throwable ignored) {}
                    }
                } else if (phase < 5) {
                    // Секция B (31..35)
                    int start = Glyph.Code_24111.B_1 + (phase - 3) * 2;
                    int end = Math.min(Glyph.Code_24111.B_5, start + 2);
                    for (int i = start; i <= end; i++) {
                        try { b.buildChannel(i, 4095); } catch (Throwable ignored) {}
                    }
                } else {
                    // Секция C (0..19) — 20 сегментов разбиты на 4 окна по 5 LED
                    int start = Glyph.Code_24111.C_1 + (phase - 5) * 5;
                    int end = Math.min(Glyph.Code_24111.C_20, start + 5);
                    for (int i = start; i <= end; i++) {
                        try { b.buildChannel(i, 4095); } catch (Throwable ignored) {}
                    }
                }
            }
        } else if (Glyph.DEVICE_23111.equals(targetDev) || Glyph.DEVICE_23113.equals(targetDev)) {
            // Nothing Phone (2a / 2a Plus): 8 фаз по 24-сегментной дуге C_1..C_24 (0..23), затем B (24) и A (25)
            int phase = step % 8;
            if (phase < 6) {
                int start = Glyph.Code_23111.C_1 + phase * 4;
                int end = Math.min(Glyph.Code_23111.C_24, start + 4);
                for (int i = start; i <= end; i++) {
                    try { b.buildChannel(i, 4095); } catch (Throwable ignored) {}
                }
            } else if (phase == 6) {
                try { b.buildChannel(Glyph.Code_23111.B, 4095); } catch (Throwable ignored) {}
            } else {
                try { b.buildChannel(Glyph.Code_23111.A, 4095); } catch (Throwable ignored) {}
            }
        } else if (Glyph.DEVICE_20111.equals(targetDev) || isPhone1()) {
            // Nothing Phone (1): 7 фаз снизу вверх (E1 -> D1 нижняя половина -> D1 верхняя половина -> C2/C3 -> C1/C4 -> A1 -> B1)
            switch (step % 7) {
                case 0:
                    b.buildChannel(Glyph.Code_20111.E1, 4095);
                    b.buildChannel(Glyph.Code_20111.D1_8, 2200);
                    break;
                case 1:
                    for (int i = Glyph.Code_20111.D1_5; i <= Glyph.Code_20111.D1_8; i++) {
                        b.buildChannel(i, 4095);
                    }
                    break;
                case 2:
                    for (int i = Glyph.Code_20111.D1_1; i <= Glyph.Code_20111.D1_4; i++) {
                        b.buildChannel(i, 4095);
                    }
                    break;
                case 3:
                    b.buildChannel(Glyph.Code_20111.C2, 4095);
                    b.buildChannel(Glyph.Code_20111.C3, 4095);
                    break;
                case 4:
                    b.buildChannel(Glyph.Code_20111.C1, 4095);
                    b.buildChannel(Glyph.Code_20111.C4, 4095);
                    break;
                case 5:
                    b.buildChannel(Glyph.Code_20111.A1, 4095);
                    break;
                case 6:
                default:
                    b.buildChannel(Glyph.Code_20111.B1, 4095);
                    break;
            }
        } else {
            // Nothing Phone (2) (22111, 33 LED): 8 фаз снизу вверх через 16-сегментную дугу C1_1..C1_16
            switch (step % 8) {
                case 0:
                    b.buildChannel(Glyph.Code_22111.E1, 4095);
                    for (int i = Glyph.Code_22111.D1_5; i <= Glyph.Code_22111.D1_8; i++) {
                        b.buildChannel(i, 3600);
                    }
                    break;
                case 1:
                    for (int i = Glyph.Code_22111.D1_1; i <= Glyph.Code_22111.D1_4; i++) {
                        b.buildChannel(i, 4095);
                    }
                    break;
                case 2:
                    for (int i = Glyph.Code_22111.C2; i <= Glyph.Code_22111.C6; i++) {
                        b.buildChannel(i, 4095);
                    }
                    break;
                case 3:
                    for (int i = Glyph.Code_22111.C1_1; i <= Glyph.Code_22111.C1_6; i++) {
                        b.buildChannel(i, 4095);
                    }
                    break;
                case 4:
                    for (int i = Glyph.Code_22111.C1_6; i <= Glyph.Code_22111.C1_11; i++) {
                        b.buildChannel(i, 4095);
                    }
                    break;
                case 5:
                    for (int i = Glyph.Code_22111.C1_11; i <= Glyph.Code_22111.C1_16; i++) {
                        b.buildChannel(i, 4095);
                    }
                    break;
                case 6:
                    b.buildChannel(Glyph.Code_22111.A2, 4095);
                    b.buildChannel(Glyph.Code_22111.A1, 4095);
                    break;
                case 7:
                default:
                    b.buildChannel(Glyph.Code_22111.B1, 4095);
                    break;
            }
        }
    }

    public static void applyFullChannels(GlyphFrame.Builder builder) {
        if (builder == null) {
            return;
        }
        String device = resolveStripDevice();
        if (Glyph.DEVICE_25111.equals(device)) {
            // Nothing Phone (4a / 4b) - 6 квадратных зон A_1..A_6:
            for (int i = 0; i < 6; i++) {
                try { builder.buildChannel(i); } catch (Throwable ignored) {}
            }
            builder.buildChannelA();
        } else if (Glyph.DEVICE_24111.equals(device)) {
            if ("Nothing Phone (3a) Lite".equals(NothingPhoneEasterEgg.getDeviceDisplayName())) {
                for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11; i++) {
                    try { builder.buildChannel(i); } catch (Throwable ignored) {}
                }
                builder.buildChannelA();
            } else {
                for (int i = 0; i < 36; i++) {
                    try { builder.buildChannel(i); } catch (Throwable ignored) {}
                }
                builder.buildChannelA()
                        .buildChannelB()
                        .buildChannelC();
            }
        } else if (Glyph.DEVICE_23111.equals(device) || Glyph.DEVICE_23113.equals(device)) {
            // Phone (2a / 2a Plus) - 26 зон (C_1..C_24, B, A):
            for (int i = 0; i < 26; i++) {
                try { builder.buildChannel(i); } catch (Throwable ignored) {}
            }
            builder.buildChannelA()
                    .buildChannelB()
                    .buildChannelC();
        } else if (Glyph.DEVICE_20111.equals(device) || isPhone1()) {
            // Phone (1) - 15 адресных зон A1, B1, C1..C4, E1, D1_1..D1_8:
            for (int i = 0; i < 15; i++) {
                try { builder.buildChannel(i); } catch (Throwable ignored) {}
            }
            builder.buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .buildChannelD()
                    .buildChannelE();
        } else {
            // Phone (2) - 33 адресных зоны в 5 группах A, B, C, D, E:
            for (int i = 0; i < 33; i++) {
                try { builder.buildChannel(i); } catch (Throwable ignored) {}
            }
            builder.buildChannelA()
                    .buildChannelB()
                    .buildChannelC()
                    .buildChannelD()
                    .buildChannelE();
        }
    }

    /**
     * Акцентное кольцо / зона (`MESSAGE_ANIM_ACCENT_RING` и `RECORDING_ANIM_ACCENT_RING`):
     * - Phone (1): центральное кольцо катушки `C1..C4` + кольцо камеры `A1`
     * - Phone (2): центральное кольцо `C1_1..C6` + камера `A1, A2`
     * - Phone (2a / 2a Plus): главная верхняя дуга `C_1..C_24`
     * - Phone (3a / 3a Pro): верхняя и правая дуги `A_1..A_11` + `C_1..C_20`
     * - Phone (4a / 4b): центральные квадраты `A_3, A_4` (индексы 2, 3) и крайние `A_1, A_6` (0, 5)
     */
    public static void applyAccentChannels(GlyphFrame.Builder builder) {
        if (builder == null) {
            return;
        }
        String device = resolveStripDevice();
        if (Glyph.DEVICE_25111.equals(device)) {
            try {
                builder.buildChannel(Glyph.Code_25111.A_1)
                        .buildChannel(Glyph.Code_25111.A_3)
                        .buildChannel(Glyph.Code_25111.A_4)
                        .buildChannel(Glyph.Code_25111.A_6);
            } catch (Throwable ignored) {}
        } else if (Glyph.DEVICE_24111.equals(device)) {
            for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11; i++) {
                try { builder.buildChannel(i); } catch (Throwable ignored) {}
            }
            builder.buildChannelA();
        } else if (Glyph.DEVICE_23111.equals(device) || Glyph.DEVICE_23113.equals(device)) {
            for (int i = Glyph.Code_23111.C_1; i <= Glyph.Code_23111.C_24; i++) {
                try { builder.buildChannel(i); } catch (Throwable ignored) {}
            }
            builder.buildChannelC().buildChannelB();
        } else if (Glyph.DEVICE_20111.equals(device) || isPhone1()) {
            try {
                builder.buildChannel(Glyph.Code_20111.B1);
                builder.buildChannel(Glyph.Code_20111.A1)
                        .buildChannel(Glyph.Code_20111.C1)
                        .buildChannel(Glyph.Code_20111.C2)
                        .buildChannel(Glyph.Code_20111.C3)
                        .buildChannel(Glyph.Code_20111.C4);
            } catch (Throwable ignored) {}
            builder.buildChannelA().buildChannelB().buildChannelC();
        } else {
            try {
                builder.buildChannel(Glyph.Code_22111.A1)
                        .buildChannel(Glyph.Code_22111.A2);
                for (int i = Glyph.Code_22111.C1_1; i <= Glyph.Code_22111.C6; i++) {
                    builder.buildChannel(i);
                }
            } catch (Throwable ignored) {}
            builder.buildChannelA().buildChannelC();
        }
    }

    public void flashDirectFallback() {
        try {
            Field serviceField = GlyphManager.class.getDeclaredField("mService");
            serviceField.setAccessible(true);
            Object serviceObj = stripManager != null ? serviceField.get(stripManager) : null;
            if (serviceObj instanceof com.nothing.thirdparty.IGlyphService) {
                com.nothing.thirdparty.IGlyphService gs = (com.nothing.thirdparty.IGlyphService) serviceObj;
                try {
                    gs.openSession();
                } catch (Throwable ignored) {}
                String device = resolveStripDevice();
                int size = getStripChannelCount(device);
                int[] frame = new int[size];
                if (Glyph.DEVICE_24111.equals(device) && "Nothing Phone (3a) Lite".equals(NothingPhoneEasterEgg.getDeviceDisplayName())) {
                    for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11 && i < size; i++) {
                        frame[i] = 4000;
                    }
                } else {
                    for (int i = 0; i < size; i++) {
                        frame[i] = 4000;
                    }
                }
                gs.setFrameColors(frame);
                handler.postDelayed(() -> {
                    try {
                        gs.setFrameColors(new int[size]);
                    } catch (Throwable ignored) {}
                }, 220L);
            }
        } catch (Throwable ignored) {}
    }

    private void initStrip(Context context) {
        stripManager = GlyphManager.getInstance(context);
        stripManager.init(new GlyphManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName componentName) {
                String targetDevice = resolveStripDevice();
                boolean registered = false;
                try {
                    registered = stripManager.register(targetDevice);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
                if (!registered) {
                    try {
                        registered = stripManager.register();
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
                try {
                    Field serviceField = GlyphManager.class.getDeclaredField("mService");
                    serviceField.setAccessible(true);
                    Object serviceObj = serviceField.get(stripManager);
                    if (serviceObj instanceof com.nothing.thirdparty.IGlyphService) {
                        com.nothing.thirdparty.IGlyphService glyphService = (com.nothing.thirdparty.IGlyphService) serviceObj;
                        if (!registered) {
                            try { registered = glyphService.register(targetDevice); } catch (Throwable ignored) {}
                        }
                        if (!registered) {
                            String appKey = getAppKey(context);
                            if (appKey != null) {
                                try { registered = glyphService.registerSDK(appKey, targetDevice); } catch (Throwable ignored) {}
                            }
                        }
                        if (!registered) {
                            try { registered = glyphService.register("20111"); } catch (Throwable ignored) {}
                        }
                        if (!registered) {
                            try { registered = glyphService.register("A063"); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }

                // Всегда синхронизируем mHasAuthorized=true и канонический mDevice=targetDevice ("20111".."25111"),
                // чтобы stripManager.getGlyphFrameBuilder() создавал буфер правильной длины каналов!
                try {
                    Field authField = GlyphManager.class.getDeclaredField("mHasAuthorized");
                    authField.setAccessible(true);
                    authField.setBoolean(stripManager, true);

                    Field devField = GlyphManager.class.getDeclaredField("mDevice");
                    devField.setAccessible(true);
                    devField.set(stripManager, targetDevice);
                } catch (Throwable t) {
                    FileLog.e(t);
                }

                try {
                    stripManager.openSession();
                    stripSessionOpen = true;
                } catch (Throwable e) {
                    FileLog.e(e);
                    try {
                        Field serviceField = GlyphManager.class.getDeclaredField("mService");
                        serviceField.setAccessible(true);
                        Object serviceObj = serviceField.get(stripManager);
                        if (serviceObj instanceof com.nothing.thirdparty.IGlyphService) {
                            ((com.nothing.thirdparty.IGlyphService) serviceObj).openSession();
                        }
                    } catch (Throwable ignored) {}
                    stripSessionOpen = true;
                }
                runPendingPreview();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                stripSessionOpen = false;
            }
        });
    }

    private void initMatrix(Context context) {
        matrixManager = GlyphMatrixManager.getInstance(context);
        matrixManager.init(new GlyphMatrixManager.Callback() {
            @Override
            public void onServiceConnected(ComponentName componentName) {
                String targetMatrix = Common.is23112() ? Glyph.DEVICE_23112
                        : (Common.is25111p() ? Glyph.DEVICE_25111p : resolveMatrixDevice());
                boolean registered = false;
                try {
                    registered = matrixManager.register(targetMatrix);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
                if (!registered) {
                    String fallback = Glyph.DEVICE_23112.equals(targetMatrix) ? Glyph.DEVICE_25111p : Glyph.DEVICE_23112;
                    try {
                        registered = matrixManager.register(fallback);
                        if (registered) {
                            targetMatrix = fallback;
                        }
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
                if (!registered && Build.MODEL != null && !Build.MODEL.isEmpty()) {
                    try {
                        registered = matrixManager.register(Build.MODEL);
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
                if (!registered) {
                    try {
                        Field serviceField = GlyphMatrixManager.class.getDeclaredField("mService");
                        serviceField.setAccessible(true);
                        Object serviceObj = serviceField.get(matrixManager);
                        if (serviceObj instanceof com.nothing.thirdparty.IGlyphService) {
                            com.nothing.thirdparty.IGlyphService glyphService = (com.nothing.thirdparty.IGlyphService) serviceObj;
                            try { registered = glyphService.registerMatrixSDK(targetMatrix); } catch (Throwable ignored) {}
                            if (!registered && Build.MODEL != null) {
                                try { registered = glyphService.registerMatrixSDK(Build.MODEL); } catch (Throwable ignored) {}
                            }
                            if (!registered) {
                                String appKey = getAppKey(context);
                                if (appKey != null) {
                                    try { registered = glyphService.registerSDK(appKey, targetMatrix); } catch (Throwable ignored) {}
                                }
                            }
                            if (!registered) {
                                try { registered = glyphService.register(targetMatrix); } catch (Throwable ignored) {}
                            }
                        }
                        Field authField = GlyphMatrixManager.class.getDeclaredField("mHasAuthorized");
                        authField.setAccessible(true);
                        authField.setBoolean(matrixManager, true);

                        Field devField = GlyphMatrixManager.class.getDeclaredField("mDevice");
                        devField.setAccessible(true);
                        devField.set(matrixManager, targetMatrix);
                        registered = true;
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
                matrixRegistered = registered;
                if (matrixRegistered) {
                    runPendingPreview();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                matrixRegistered = false;
            }
        });
    }

    /** Короткая вспышка всех зон — реакция на входящее сообщение. */
    private void pulseStrip() {
        pulseStrip(GlyphConfig.messageAnimationStyle());
    }

    private void pulseStrip(int style) {
        if (stripManager == null || !stripSessionOpen) {
            return;
        }
        try {
            GlyphFrame.Builder builder = createStripFrameBuilder();
            if (builder == null) {
                return;
            }
            int totalDurationMs;
            switch (style) {
                case GlyphConfig.MESSAGE_ANIM_STROBE:
                    applyFullChannels(builder);
                    builder.buildPeriod(130)
                            .buildCycles(3)
                            .buildInterval(55);
                    totalDurationMs = 130 * 3 + 55 * 3 + 80;
                    break;
                case GlyphConfig.MESSAGE_ANIM_SOFT_PULSE:
                    applyFullChannels(builder);
                    builder.buildPeriod(600)
                            .buildCycles(1)
                            .buildInterval(50);
                    totalDurationMs = 700;
                    break;
                case GlyphConfig.MESSAGE_ANIM_ACCENT_RING:
                    applyAccentChannels(builder);
                    builder.buildPeriod(280)
                            .buildCycles(2)
                            .buildInterval(90);
                    totalDurationMs = 280 * 2 + 90 * 2 + 80;
                    break;
                case GlyphConfig.MESSAGE_ANIM_DOUBLE_FLASH:
                default:
                    applyFullChannels(builder);
                    builder.buildPeriod(240)
                            .buildCycles(2)
                            .buildInterval(80);
                    totalDurationMs = 240 * 2 + 80 * 2 + 80;
                    break;
            }
            stripManager.animate(builder.build());
            // Страховочное гашение по завершении циклов, чтобы ни один светодиод не остался полузажжённым
            handler.postDelayed(() -> {
                if (!glyphBusy && stripManager != null && stripSessionOpen) {
                    try {
                        stripManager.turnOff();
                    } catch (Throwable ignored) {}
                }
            }, totalDurationMs);
        } catch (Throwable t) {
            FileLog.e(t);
            flashDirectFallback();
        }
    }

    private void startCallStrip() {
        startCallStrip(GlyphConfig.callAnimationStyle());
    }

    private void startCallStrip(int style) {
        if (stripManager == null || !stripSessionOpen) {
            return;
        }
        handler.removeCallbacks(callWaveRunnable);
        try {
            switch (style) {
                case GlyphConfig.CALL_ANIM_WAVE:
                    callWaveStep = 0;
                    handler.post(callWaveRunnable);
                    break;
                case GlyphConfig.CALL_ANIM_BREATHING: {
                    GlyphFrame.Builder b = createStripFrameBuilder();
                    if (b != null) {
                        applyFullChannels(b);
                        GlyphFrame breathingFrame = b
                                .buildPeriod(1200)
                                .buildCycles(RECORDING_MAX_CYCLES)
                                .buildInterval(40)
                                .build();
                        stripManager.animate(breathingFrame);
                    }
                    break;
                }
                case GlyphConfig.CALL_ANIM_PULSE:
                default: {
                    GlyphFrame.Builder b = createStripFrameBuilder();
                    if (b != null) {
                        applyFullChannels(b);
                        GlyphFrame pulseFrame = b
                                .buildPeriod(560)
                                .buildCycles(RECORDING_MAX_CYCLES)
                                .buildInterval(140)
                                .build();
                        stripManager.animate(pulseFrame);
                    }
                    break;
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void startRecordingStrip() {
        startRecordingStrip(GlyphConfig.recordingAnimationStyle());
    }

    private void startRecordingStrip(int style) {
        if (stripManager == null || !stripSessionOpen) {
            return;
        }
        try {
            GlyphFrame.Builder builder = createStripFrameBuilder();
            if (builder == null) {
                return;
            }
            switch (style) {
                case GlyphConfig.RECORDING_ANIM_ACCENT_RING:
                    applyAccentChannels(builder);
                    builder.buildPeriod(1500)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(40);
                    break;
                case GlyphConfig.RECORDING_ANIM_HEARTBEAT: {
                    String device = resolveStripDevice();
                    if (Glyph.DEVICE_25111.equals(device)) {
                        try {
                            builder.buildChannel(Glyph.Code_25111.A_2)
                                    .buildChannel(Glyph.Code_25111.A_5);
                        } catch (Throwable ignored) {}
                    } else if (Glyph.DEVICE_24111.equals(device)) {
                        if ("Nothing Phone (3a) Lite".equals(NothingPhoneEasterEgg.getDeviceDisplayName())) {
                            for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11; i++) {
                                try { builder.buildChannel(i); } catch (Throwable ignored) {}
                            }
                            builder.buildChannelA();
                        } else {
                            for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11; i++) {
                                try { builder.buildChannel(i); } catch (Throwable ignored) {}
                            }
                            for (int i = Glyph.Code_24111.B_1; i <= Glyph.Code_24111.B_5; i++) {
                                try { builder.buildChannel(i); } catch (Throwable ignored) {}
                            }
                            builder.buildChannelA().buildChannelB();
                        }
                    } else if (Glyph.DEVICE_23111.equals(device) || Glyph.DEVICE_23113.equals(device)) {
                        try {
                            builder.buildChannel(Glyph.Code_23111.A)
                                    .buildChannel(Glyph.Code_23111.B);
                        } catch (Throwable ignored) {}
                        builder.buildChannelA().buildChannelB();
                    } else if (Glyph.DEVICE_20111.equals(device) || isPhone1()) {
                        try {
                            builder.buildChannel(Glyph.Code_20111.B1)
                                    .buildChannel(Glyph.Code_20111.E1);
                        } catch (Throwable ignored) {}
                        for (int i = Glyph.Code_20111.D1_1; i <= Glyph.Code_20111.D1_8; i++) {
                            try { builder.buildChannel(i); } catch (Throwable ignored) {}
                        }
                        builder.buildChannelB().buildChannelD().buildChannelE();
                    } else {
                        try {
                            builder.buildChannel(Glyph.Code_22111.B1)
                                    .buildChannel(Glyph.Code_22111.E1);
                        } catch (Throwable ignored) {}
                        for (int i = Glyph.Code_22111.D1_1; i <= Glyph.Code_22111.D1_8; i++) {
                            try { builder.buildChannel(i); } catch (Throwable ignored) {}
                        }
                        builder.buildChannelB().buildChannelD().buildChannelE();
                    }
                    builder.buildPeriod(720)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(180);
                    break;
                }
                case GlyphConfig.RECORDING_ANIM_BREATHING:
                default:
                    applyFullChannels(builder);
                    builder.buildPeriod(1800)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(40);
                    break;
            }
            stripManager.animate(builder.build());
        } catch (Throwable t) {
            FileLog.e(t);
            flashDirectFallback();
        }
    }

    private void pulseMatrix(int style) {
        if (matrixManager == null || !matrixRegistered) {
            return;
        }
        handler.removeCallbacks(hideLogoRunnable);
        handler.removeCallbacks(matrixMessageAnimRunnable);
        matrixMessageStyle = style;
        matrixMessageStep = 0;
        handler.post(matrixMessageAnimRunnable);
    }

    private void showMatrixLogo() {
        showMatrixIcon(R.drawable.moltengram_icon_monochrome, BREATH_MAX, true);
    }

    public static int getMatrixGridLength() {
        try {
            int sdkLen = Common.getDeviceMatrixLength();
            if (sdkLen > 0) {
                return sdkLen;
            }
        } catch (Throwable ignored) {}
        String target = resolveMatrixDevice();
        return Glyph.DEVICE_25111p.equals(target) ? 13 : 25;
    }

    public static int[] renderMatrixBitmap(Bitmap bitmap, int brightness, int matrixLength) {
        if (matrixLength <= 0) {
            return new int[0];
        }
        int[] out = new int[matrixLength * matrixLength];
        if (bitmap == null) {
            return out;
        }
        int clampedBrightness = Math.max(0, Math.min(255, brightness));
        int origW = bitmap.getWidth();
        int origH = bitmap.getHeight();
        if (origW <= 0 || origH <= 0) {
            return out;
        }
        Bitmap scaled = bitmap;
        try {
            Matrix m = new Matrix();
            m.postScale(1.05f, 1.05f);
            scaled = Bitmap.createBitmap(bitmap, 0, 0, origW, origH, m, true);
        } catch (Throwable ignored) {}
        int scaledW = scaled.getWidth();
        int scaledH = scaled.getHeight();
        int offsetX = scaledW >= origW ? (scaledW - origW) / 2 : 0;
        int offsetY = scaledH >= origH ? (scaledH - origH) / 2 : 0;
        int denom = Math.max(1, matrixLength - 1);
        for (int row = 0; row < matrixLength; row++) {
            int srcY = Math.min(scaledH - 1, Math.max(0, Math.round(row * (origH - 1) / (float) denom) + offsetY));
            for (int col = 0; col < matrixLength; col++) {
                int srcX = Math.min(scaledW - 1, Math.max(0, Math.round(col * (origW - 1) / (float) denom) + offsetX));
                int pixel = scaled.getPixel(srcX, srcY);
                int alpha = Color.alpha(pixel);
                int avgRgb = ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3) * alpha / 255;
                int ledVal = (avgRgb * 4095 / 255) * clampedBrightness / 255;
                out[row * matrixLength + col] = Math.max(0, Math.min(4095, ledVal));
            }
        }
        return out;
    }

    /**
     * Смешивает пиксель светового эффекта (кольца, радара, волны) с пикселем центральной иконки:
     * если пиксель иконки уже зажжён (`iconVal > 500`), то при прохождении поверх него яркого
     * светового эффекта (`effectVal > 700`) яркость пикселя контрастно инвертируется
     * (`effectVal - iconVal`), чтобы силуэт иконки чётко выделялся негативом на фоне кольца.
     */
    public static int blendMatrixEffectWithIconInversion(int existingIconVal, int effectVal) {
        if (effectVal <= 0) {
            return Math.max(0, Math.min(4095, existingIconVal));
        }
        if (existingIconVal > 500 && effectVal > 700) {
            return Math.max(0, Math.min(4095, effectVal - existingIconVal));
        }
        return Math.max(0, Math.min(4095, Math.max(existingIconVal, effectVal)));
    }

    /** Добавляет расходящееся круговое кольцо (радиус от 0 до R) поверх кадра матрицы с контрастной инверсией иконки. */
    public static void addMatrixExpandingRing(int[] frame, int len, float progress, int maxLed) {
        if (frame == null || len <= 0 || frame.length < len * len) return;
        float center = (len - 1) / 2.0f;
        float maxR = center;
        float targetR = Math.max(1.2f, progress * maxR);
        float thickness = len <= 13 ? 1.15f : 1.55f;
        for (int r = 0; r < len; r++) {
            for (int c = 0; c < len; c++) {
                float dist = (float) Math.hypot(r - center, c - center);
                float diff = Math.abs(dist - targetR);
                if (diff <= thickness) {
                    int val = Math.round(maxLed * (1.0f - diff / (thickness + 0.25f)));
                    int idx = r * len + c;
                    frame[idx] = blendMatrixEffectWithIconInversion(frame[idx], val);
                }
            }
        }
    }

    /** Добавляет статическое кольцо заданного относительного радиуса `radiusFraction` (0..1). */
    public static void addMatrixPerimeterRing(int[] frame, int len, float radiusFraction, int maxLed) {
        addMatrixExpandingRing(frame, len, radiusFraction, maxLed);
    }

    /** Добавляет вращающуюся радарную дугу по периметру круглой матрицы (`CALL_ANIM_WAVE`) с контрастной инверсией. */
    public static void addMatrixRadarWave(int[] frame, int len, float headAngleDeg, int maxLed) {
        if (frame == null || len <= 0 || frame.length < len * len) return;
        float center = (len - 1) / 2.0f;
        float ringR = center - 0.6f;
        float thickness = len <= 13 ? 1.25f : 1.75f;
        for (int r = 0; r < len; r++) {
            for (int c = 0; c < len; c++) {
                float dist = (float) Math.hypot(r - center, c - center);
                if (Math.abs(dist - ringR) <= thickness) {
                    float deg = (float) Math.toDegrees(Math.atan2(r - center, c - center));
                    if (deg < 0) deg += 360f;
                    float delta = (headAngleDeg - deg + 360f) % 360f;
                    if (delta <= 135f) {
                        int val = Math.round(maxLed * (1.0f - delta / 145f));
                        int idx = r * len + c;
                        frame[idx] = blendMatrixEffectWithIconInversion(frame[idx], val);
                    }
                }
            }
        }
    }

    /** Добавляет заполняющуюся дугу прогресса по периметру матрицы (`RECORDING_ANIM_ACCENT_RING`) с контрастной инверсией. */
    public static void addMatrixPerimeterProgress(int[] frame, int len, float progress, int maxLed) {
        if (frame == null || len <= 0 || frame.length < len * len) return;
        float center = (len - 1) / 2.0f;
        float ringR = center - 0.6f;
        float thickness = len <= 13 ? 1.2f : 1.6f;
        float maxDeg = Math.max(15f, Math.min(360f, progress * 360f));
        for (int r = 0; r < len; r++) {
            for (int c = 0; c < len; c++) {
                float dist = (float) Math.hypot(r - center, c - center);
                if (Math.abs(dist - ringR) <= thickness) {
                    // Отсчёт от верхней точки (-90 градусов) по часовой стрелке
                    float deg = (float) Math.toDegrees(Math.atan2(r - center, c - center)) + 90f;
                    if (deg < 0) deg += 360f;
                    if (deg <= maxDeg) {
                        int idx = r * len + c;
                        frame[idx] = blendMatrixEffectWithIconInversion(frame[idx], maxLed);
                    }
                }
            }
        }
    }

    private void pushMatrixFrame(int[] rendered) {
        if (matrixManager == null || !matrixRegistered || rendered == null || rendered.length == 0) {
            return;
        }
        try {
            matrixManager.setAppMatrixFrame(rendered);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        try {
            matrixManager.setMatrixFrame(rendered);
        } catch (Throwable ignored) {}
    }

    private void showMatrixIcon(int resId, int brightness, boolean autoHide) {
        if (matrixManager == null || !matrixRegistered) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        Bitmap bitmap = getMatrixIconBitmap(context, resId);
        if (bitmap == null) {
            return;
        }
        try {
            int[] rendered = null;
            try {
                if (Common.getDeviceMatrixLength() > 0) {
                    GlyphMatrixObject object = new GlyphMatrixObject.Builder()
                            .setImageSource(bitmap)
                            .setScale(105)
                            .setBrightness(brightness)
                            .build();
                    GlyphMatrixFrame frame = new GlyphMatrixFrame.Builder()
                            .addTop(object)
                            .build(context);
                    rendered = frame.render();
                }
            } catch (Throwable ignored) {}
            if (rendered == null || rendered.length == 0) {
                rendered = renderMatrixBitmap(bitmap, brightness, getMatrixGridLength());
            }
            pushMatrixFrame(rendered);
            handler.removeCallbacks(hideLogoRunnable);
            if (autoHide) {
                handler.postDelayed(hideLogoRunnable, LOGO_SHOW_MS);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private Bitmap getMatrixIconBitmap(Context context, int resId) {
        if ((resId == R.drawable.moltengram_icon_monochrome || resId == R.drawable.exteraless_icon_monochrome) && matrixLogoBitmap != null) {
            return matrixLogoBitmap;
        }
        if (resId == R.drawable.baseline_call_24 && matrixCallBitmap != null) {
            return matrixCallBitmap;
        }
        if (resId == R.drawable.baseline_mic_24 && matrixMicBitmap != null) {
            return matrixMicBitmap;
        }
        Drawable icon = context.getDrawable(resId);
        if (icon == null) {
            return null;
        }
        // SDK мапит яркость пикселя (R+G+B)/3 на яркость LED, поэтому чёрный
        // монохромный знак дал бы кадр из одних нулей — тонируем в белый.
        icon = icon.mutate();
        icon.setTint(Color.WHITE);
        Bitmap rawBitmap = GlyphMatrixUtils.drawableToBitmap(icon);
        Bitmap bitmap = cropTightMatrixBitmap(rawBitmap);
        if (resId == R.drawable.moltengram_icon_monochrome || resId == R.drawable.exteraless_icon_monochrome) {
            matrixLogoBitmap = bitmap;
        } else if (resId == R.drawable.baseline_call_24) {
            matrixCallBitmap = bitmap;
        } else if (resId == R.drawable.baseline_mic_24) {
            matrixMicBitmap = bitmap;
        }
        return bitmap;
    }

    private static Bitmap cropTightMatrixBitmap(Bitmap src) {
        if (src == null) return null;
        int width = src.getWidth();
        int height = src.getHeight();
        int minX = width, minY = height, maxX = -1, maxY = -1;
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            src.getPixels(row, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int alpha = (row[x] >>> 24);
                if (alpha > 30) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return src;
        }
        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        int maxDim = Math.max(cropW, cropH);
        int pad = Math.max(1, Math.round(maxDim * 0.04f));
        int targetDim = maxDim + pad * 2;
        Bitmap cropped = Bitmap.createBitmap(targetDim, targetDim, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cropped);
        Rect srcRect = new Rect(minX, minY, maxX + 1, maxY + 1);
        int left = pad + (maxDim - cropW) / 2;
        int top = pad + (maxDim - cropH) / 2;
        Rect dstRect = new Rect(left, top, left + cropW, top + cropH);
        canvas.drawBitmap(src, srcRect, dstRect, null);
        return cropped;
    }

    private Bitmap getMatrixLogoBitmap(Context context) {
        return getMatrixIconBitmap(context, R.drawable.moltengram_icon_monochrome);
    }

    private void startMatrixBreathing() {
        breathSteps = 0;
        breathBrightness = BREATH_MAX;
        breathDirection = -1;
        // Таймер автогашения от превью/уведомления не должен рвать дыхание.
        handler.removeCallbacks(hideLogoRunnable);
        handler.removeCallbacks(matrixMessageAnimRunnable);
        handler.removeCallbacks(breathRunnable);
        handler.post(breathRunnable);
    }

    private void stopMatrixBreathing() {
        handler.removeCallbacks(breathRunnable);
        handler.removeCallbacks(matrixMessageAnimRunnable);
        hideMatrixLogo();
    }

    private void hideMatrixLogo() {
        if (matrixManager == null || !matrixRegistered) {
            return;
        }
        try {
            int len = getMatrixGridLength();
            if (len > 0) {
                int[] empty = new int[len * len];
                try { matrixManager.setAppMatrixFrame(empty); } catch (Throwable ignored) {}
                try { matrixManager.setMatrixFrame(empty); } catch (Throwable ignored) {}
            }
            matrixManager.closeAppMatrix();
            try { matrixManager.turnOff(); } catch (Throwable ignored) {}
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
