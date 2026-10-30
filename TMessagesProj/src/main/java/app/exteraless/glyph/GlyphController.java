package app.exteraless.glyph;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;

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
 * Управление Nothing Glyph: светодиодные зоны (Glyph Interface) и матрица Phone (3)
 * (Glyph Matrix) через единый AAR Nothing.
 *
 * SDK работает через системный прокси-сервис Nothing, поэтому весь жизненный цикл
 * сводится к init -> register -> openSession; на устройствах других вендоров
 * контроллер просто не инициализируется.
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

    /** Сколько логотип держится на Glyph Matrix. */
    private static final long LOGO_SHOW_MS = 3000L;

    /** Верхняя граница циклов «дыхания» на записи — страховка, если стоп-ивент потеряется. */
    private static final int RECORDING_MAX_CYCLES = 600;

    /** Шаг и диапазон яркости «дыхания» матрицы (0-255, как в SDK). */
    private static final long BREATH_STEP_MS = 120L;
    private static final int BREATH_STEP = 10;
    private static final int BREATH_MIN = 12;
    private static final int BREATH_MAX = 255;

    /** Страховка от потерянного стоп-ивента записи: ~10 минут дыхания матрицы. */
    private static final int RECORDING_MAX_BREATH_STEPS = 5000;

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
    private final Runnable callWaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!callActive || stripManager == null || !stripSessionOpen) {
                return;
            }
            try {
                GlyphFrame.Builder b = stripManager.getGlyphFrameBuilder();
                if (b == null) {
                    return;
                }
                b.buildPeriod(200)
                        .buildCycles(1)
                        .buildInterval(0);
                String targetDev = resolveStripDevice();
                if (Glyph.DEVICE_25111.equals(targetDev)) {
                    int sq = callWaveStep % 6;
                    b.buildChannel(sq);
                } else if (Glyph.DEVICE_24111.equals(targetDev)) {
                    if ("Nothing Phone (3a) Lite".equals(NothingPhoneEasterEgg.getDeviceDisplayName())) {
                        for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11; i++) {
                            try { b.buildChannel(i); } catch (Throwable ignored) {}
                        }
                        b.buildChannelA();
                    } else {
                        switch (callWaveStep % 3) {
                            case 0:
                                for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11; i++) {
                                    try { b.buildChannel(i); } catch (Throwable ignored) {}
                                }
                                b.buildChannelA();
                                break;
                            case 1:
                                for (int i = Glyph.Code_24111.B_1; i <= Glyph.Code_24111.B_5; i++) {
                                    try { b.buildChannel(i); } catch (Throwable ignored) {}
                                }
                                b.buildChannelB();
                                break;
                            case 2:
                                for (int i = Glyph.Code_24111.C_1; i <= Glyph.Code_24111.C_20; i++) {
                                    try { b.buildChannel(i); } catch (Throwable ignored) {}
                                }
                                b.buildChannelC();
                                break;
                        }
                    }
                } else if (Glyph.DEVICE_23111.equals(targetDev) || Glyph.DEVICE_23113.equals(targetDev)) {
                    switch (callWaveStep % 3) {
                        case 0:
                            try { b.buildChannel(Glyph.Code_23111.A); } catch (Throwable ignored) {}
                            b.buildChannelA();
                            break;
                        case 1:
                            try { b.buildChannel(Glyph.Code_23111.B); } catch (Throwable ignored) {}
                            b.buildChannelB();
                            break;
                        case 2:
                            for (int i = Glyph.Code_23111.C_1; i <= Glyph.Code_23111.C_24; i++) {
                                try { b.buildChannel(i); } catch (Throwable ignored) {}
                            }
                            b.buildChannelC();
                            break;
                    }
                } else if (Glyph.DEVICE_20111.equals(targetDev) || isPhone1()) {
                    switch (callWaveStep % 4) {
                        case 0:
                            try { b.buildChannel(Glyph.Code_20111.A1); } catch (Throwable ignored) {}
                            b.buildChannelA();
                            break;
                        case 1:
                            try { b.buildChannel(Glyph.Code_20111.B1); } catch (Throwable ignored) {}
                            b.buildChannelB();
                            break;
                        case 2:
                            for (int i = Glyph.Code_20111.D1_1; i <= Glyph.Code_20111.D1_8; i++) {
                                try { b.buildChannel(i); } catch (Throwable ignored) {}
                            }
                            b.buildChannelD();
                            break;
                        case 3:
                            try {
                                b.buildChannel(Glyph.Code_20111.C1)
                                        .buildChannel(Glyph.Code_20111.C2)
                                        .buildChannel(Glyph.Code_20111.C3)
                                        .buildChannel(Glyph.Code_20111.C4)
                                        .buildChannel(Glyph.Code_20111.E1);
                            } catch (Throwable ignored) {}
                            b.buildChannelC().buildChannelE();
                            break;
                    }
                } else {
                    switch (callWaveStep % 4) {
                        case 0:
                            try {
                                b.buildChannel(Glyph.Code_22111.A1)
                                        .buildChannel(Glyph.Code_22111.A2);
                            } catch (Throwable ignored) {}
                            b.buildChannelA();
                            break;
                        case 1:
                            try { b.buildChannel(Glyph.Code_22111.B1); } catch (Throwable ignored) {}
                            b.buildChannelB();
                            break;
                        case 2:
                            for (int i = Glyph.Code_22111.D1_1; i <= Glyph.Code_22111.D1_8; i++) {
                                try { b.buildChannel(i); } catch (Throwable ignored) {}
                            }
                            b.buildChannelD();
                            break;
                        case 3:
                            for (int i = Glyph.Code_22111.C1_1; i <= Glyph.Code_22111.C6; i++) {
                                try { b.buildChannel(i); } catch (Throwable ignored) {}
                            }
                            try { b.buildChannel(Glyph.Code_22111.E1); } catch (Throwable ignored) {}
                            b.buildChannelC().buildChannelE();
                            break;
                    }
                }
                stripManager.animate(b.build());
            } catch (Throwable t) {
                FileLog.e(t);
                flashDirectFallback();
            }
            callWaveStep++;
            handler.postDelayed(this, 180L);
        }
    };

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
            int step = callActive ? 22 : BREATH_STEP;
            breathBrightness += breathDirection * step;
            if (breathBrightness <= BREATH_MIN) {
                breathBrightness = BREATH_MIN;
                breathDirection = 1;
            } else if (breathBrightness >= BREATH_MAX) {
                breathBrightness = BREATH_MAX;
                breathDirection = -1;
            }
            int iconRes = callActive ? R.drawable.baseline_call_24 : (recordingActive ? R.drawable.baseline_mic_24 : R.drawable.moltengram_icon_monochrome);
            showMatrixIcon(iconRes, breathBrightness, false);
            long delay = callActive ? 70L : BREATH_STEP_MS;
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
        callActive = false;
        glyphBusy = false;
        lastFlashAt = 0;
        handler.removeCallbacks(hideLogoRunnable);
        handler.removeCallbacks(breathRunnable);
        handler.removeCallbacks(callWaveRunnable);
        try {
            if (stripManager != null) {
                stripManager.unInit();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        stripManager = null;
        try {
            if (matrixManager != null) {
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
            showMatrixLogo();
        } else {
            pulseStrip();
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
     * Единая точка входа для длительных анимаций: дыхание живёт, пока активна
     * хотя бы одна причина (запись голосового или входящий звонок).
     */
    private void updateGlyphActivity() {
        boolean active = recordingActive || callActive;
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
            } else if (recordingActive) {
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
            if (matrixRegistered) {
                showMatrixLogo();
            }
            pulseStrip();
        });
    }

    public void previewCall() {
        handler.post(() -> {
            if (!ensureReady()) {
                previewPending = supported;
                pendingPreviewType = 1;
                return;
            }
            callActive = true;
            updateGlyphActivity();
            handler.postDelayed(() -> {
                callActive = false;
                updateGlyphActivity();
            }, 2500L);
        });
    }

    public void previewRecording() {
        handler.post(() -> {
            if (!ensureReady()) {
                previewPending = supported;
                pendingPreviewType = 2;
                return;
            }
            recordingActive = true;
            updateGlyphActivity();
            handler.postDelayed(() -> {
                recordingActive = false;
                updateGlyphActivity();
            }, 2500L);
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
        if ("Nothing Phone (3a) Pro".equals(name) || "Nothing Phone (3a)".equals(name) || "Nothing Phone (3a) Lite".equals(name)) return Glyph.DEVICE_24111;

        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";

        // Phone (4a) / Phone (4b) -> A069 (квадраты)
        if (model.contains("A069") || model.contains("A009") || model.contains("4A") || model.contains("4B")
                || device.contains("FROGGER") || device.contains("BLASTOISE") || device.contains("SUPERCONTRA")) {
            return Glyph.DEVICE_25111;
        }
        // Phone (3a) Pro / Phone (3a) / Phone (3a) Lite -> A059 (полоски 3a/3a Pro или индикатор 3a Lite)
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
        // Phone (2) -> A065
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
            showMatrixLogo();
        }
        if (stripManager != null && stripSessionOpen) {
            try {
                GlyphFrame.Builder builder = stripManager.getGlyphFrameBuilder();
                if (builder != null) {
                    applyFullChannels(builder);
                    builder.buildPeriod(80)
                            .buildCycles(4)
                            .buildInterval(30);
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
        if (model.contains("A069P") || device.contains("FROGGERPRO")) {
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

    public static void applyFullChannels(GlyphFrame.Builder builder) {
        if (builder == null) {
            return;
        }
        String device = resolveStripDevice();
        if (Glyph.DEVICE_25111.equals(device)) {
            // Nothing Phone (4a / 4b) - 6 квадратных зон:
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
                try {
                    builder.buildChannel(i);
                } catch (Throwable ignored) {}
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

    public static void applyAccentChannels(GlyphFrame.Builder builder) {
        if (builder == null) {
            return;
        }
        String device = resolveStripDevice();
        if (Glyph.DEVICE_25111.equals(device)) {
            try { builder.buildChannel(0); } catch (Throwable ignored) {}
        } else if (Glyph.DEVICE_24111.equals(device)) {
            for (int i = Glyph.Code_24111.A_1; i <= Glyph.Code_24111.A_11; i++) {
                try { builder.buildChannel(i); } catch (Throwable ignored) {}
            }
            builder.buildChannelA();
        } else if (Glyph.DEVICE_23111.equals(device) || Glyph.DEVICE_23113.equals(device)) {
            try { builder.buildChannel(Glyph.Code_23111.B); } catch (Throwable ignored) {}
            builder.buildChannelB();
        } else if (Glyph.DEVICE_20111.equals(device) || isPhone1()) {
            try {
                builder.buildChannel(Glyph.Code_20111.B1);
            } catch (Throwable ignored) {}
            builder.buildChannelB();
        } else {
            try { builder.buildChannel(Glyph.Code_22111.B1); } catch (Throwable ignored) {}
            builder.buildChannelB();
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
                            try { registered = glyphService.register("20111"); } catch (Throwable ignored) {}
                        }
                        if (!registered) {
                            try { registered = glyphService.register("A063"); } catch (Throwable ignored) {}
                        }
                        if (!registered && context != null) {
                            try { registered = glyphService.register(context.getPackageName()); } catch (Throwable ignored) {}
                        }
                        if (!registered) {
                            try { registered = glyphService.register(""); } catch (Throwable ignored) {}
                        }
                        String appKey = getAppKey(context);
                        if (!registered && appKey != null) {
                            try { registered = glyphService.registerSDK(appKey, targetDevice); } catch (Throwable ignored) {}
                            if (!registered) {
                                try { registered = glyphService.registerSDK(appKey, "20111"); } catch (Throwable ignored) {}
                            }
                        }
                        if (!registered) {
                            try { registered = glyphService.registerSDK("test", targetDevice); } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }

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
                String targetMatrix = resolveMatrixDevice();
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
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
                try {
                    Field serviceField = GlyphMatrixManager.class.getDeclaredField("mService");
                    serviceField.setAccessible(true);
                    Object serviceObj = serviceField.get(matrixManager);
                    if (serviceObj instanceof com.nothing.thirdparty.IGlyphService) {
                        com.nothing.thirdparty.IGlyphService glyphService = (com.nothing.thirdparty.IGlyphService) serviceObj;
                        if (!registered) {
                            try { registered = glyphService.registerMatrixSDK(targetMatrix); } catch (Throwable ignored) {}
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
            GlyphFrame.Builder builder = stripManager.getGlyphFrameBuilder();
            if (builder == null) {
                return;
            }
            switch (style) {
                case GlyphConfig.MESSAGE_ANIM_STROBE:
                    applyFullChannels(builder);
                    builder.buildPeriod(140)
                            .buildCycles(3)
                            .buildInterval(60);
                    break;
                case GlyphConfig.MESSAGE_ANIM_SOFT_PULSE:
                    applyFullChannels(builder);
                    builder.buildPeriod(550)
                            .buildCycles(1)
                            .buildInterval(0);
                    break;
                case GlyphConfig.MESSAGE_ANIM_ACCENT_RING:
                    applyAccentChannels(builder);
                    builder.buildPeriod(300)
                            .buildCycles(2)
                            .buildInterval(100);
                    break;
                case GlyphConfig.MESSAGE_ANIM_DOUBLE_FLASH:
                default:
                    applyFullChannels(builder);
                    builder.buildPeriod(250)
                            .buildCycles(2)
                            .buildInterval(80);
                    break;
            }
            stripManager.animate(builder.build());
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
                    GlyphFrame.Builder b = stripManager.getGlyphFrameBuilder();
                    if (b != null) {
                        applyFullChannels(b);
                        GlyphFrame breathingFrame = b
                                .buildPeriod(1200)
                                .buildCycles(RECORDING_MAX_CYCLES)
                                .buildInterval(0)
                                .build();
                        stripManager.animate(breathingFrame);
                    }
                    break;
                }
                case GlyphConfig.CALL_ANIM_PULSE:
                default: {
                    GlyphFrame.Builder b = stripManager.getGlyphFrameBuilder();
                    if (b != null) {
                        applyFullChannels(b);
                        GlyphFrame pulseFrame = b
                                .buildPeriod(600)
                                .buildCycles(RECORDING_MAX_CYCLES)
                                .buildInterval(150)
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
            GlyphFrame.Builder builder = stripManager.getGlyphFrameBuilder();
            if (builder == null) {
                return;
            }
            switch (style) {
                case GlyphConfig.RECORDING_ANIM_ACCENT_RING:
                    applyAccentChannels(builder);
                    builder.buildPeriod(1600)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(0);
                    break;
                case GlyphConfig.RECORDING_ANIM_HEARTBEAT: {
                    String device = resolveStripDevice();
                    if (Glyph.DEVICE_25111.equals(device)) {
                        try { builder.buildChannel(0).buildChannel(2); } catch (Throwable ignored) {}
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
                        try { builder.buildChannel(Glyph.Code_20111.B1); } catch (Throwable ignored) {}
                        for (int i = Glyph.Code_20111.D1_1; i <= Glyph.Code_20111.D1_8; i++) {
                            try { builder.buildChannel(i); } catch (Throwable ignored) {}
                        }
                        builder.buildChannelB().buildChannelD();
                    } else {
                        try { builder.buildChannel(Glyph.Code_22111.B1); } catch (Throwable ignored) {}
                        for (int i = Glyph.Code_22111.D1_1; i <= Glyph.Code_22111.D1_8; i++) {
                            try { builder.buildChannel(i); } catch (Throwable ignored) {}
                        }
                        builder.buildChannelB().buildChannelD();
                    }
                    builder.buildPeriod(800)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(200);
                    break;
                }
                case GlyphConfig.RECORDING_ANIM_BREATHING:
                default:
                    applyFullChannels(builder);
                    builder.buildPeriod(1800)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(0);
                    break;
            }
            stripManager.animate(builder.build());
        } catch (Throwable t) {
            FileLog.e(t);
            flashDirectFallback();
        }
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
        if (bitmap == null || matrixLength <= 0) {
            return new int[0];
        }
        int clampedBrightness = Math.max(0, Math.min(255, brightness));
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] out = new int[matrixLength * matrixLength];
        if (w <= 0 || h <= 0) {
            return out;
        }
        int denom = Math.max(1, matrixLength - 1);
        for (int row = 0; row < matrixLength; row++) {
            int srcY = Math.min(h - 1, Math.max(0, Math.round(row * (h - 1) / (float) denom)));
            for (int col = 0; col < matrixLength; col++) {
                int srcX = Math.min(w - 1, Math.max(0, Math.round(col * (w - 1) / (float) denom)));
                int pixel = bitmap.getPixel(srcX, srcY);
                int alpha = Color.alpha(pixel);
                if (alpha <= 10) {
                    out[row * matrixLength + col] = 0;
                    continue;
                }
                int avgRgb = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3;
                int effectiveVal = (avgRgb * alpha) / 255;
                int ledVal = (effectiveVal * 4095 / 255) * clampedBrightness / 255;
                out[row * matrixLength + col] = Math.max(0, Math.min(4095, ledVal));
            }
        }
        return out;
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
            matrixManager.setAppMatrixFrame(rendered);
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
        handler.post(breathRunnable);
    }

    private void stopMatrixBreathing() {
        handler.removeCallbacks(breathRunnable);
        hideMatrixLogo();
    }

    private void hideMatrixLogo() {
        if (matrixManager == null || !matrixRegistered) {
            return;
        }
        try {
            matrixManager.closeAppMatrix();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
