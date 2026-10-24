package app.exteraless.glyph;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
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
                switch (callWaveStep % 4) {
                    case 0:
                        b.buildChannelA();
                        break;
                    case 1:
                        b.buildChannelB();
                        break;
                    case 2:
                        b.buildChannelD();
                        break;
                    case 3:
                        b.buildChannelC().buildChannelE();
                        break;
                }
                stripManager.animate(b.build());
            } catch (Throwable t) {
                FileLog.e(t);
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
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        supported = manufacturer.contains("nothing") || brand.contains("nothing") || isPhone1();
    }

    public boolean isSupported() {
        return supported;
    }

    /** Идемпотентный биндинг к системному сервису глифов. */
    public synchronized void init() {
        if (initialized || !supported || !GlyphConfig.enabled()) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
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
        stripSessionOpen = false;
        matrixRegistered = false;
        previewPending = false;
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
            if (!ensureReady() || glyphBusy) {
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
        });
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
                previewPending = supported && GlyphConfig.enabled();
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
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        String device = Build.DEVICE != null ? Build.DEVICE.toUpperCase() : "";
        String product = Build.PRODUCT != null ? Build.PRODUCT.toUpperCase() : "";
        String board = Build.BOARD != null ? Build.BOARD.toUpperCase() : "";
        return model.contains("A063") || model.contains("PHONE (1)") || model.contains("PHONE 1")
                || device.contains("SPACEWAR") || product.contains("SPACEWAR") || board.contains("SPACEWAR");
    }

    public static String resolveStripDevice() {
        if (isPhone1() || Common.is20111()) {
            return Glyph.DEVICE_20111;
        }
        if (Common.is22111()) {
            return Glyph.DEVICE_22111;
        }
        if (Common.is23111()) {
            return Glyph.DEVICE_23111;
        }
        if (Common.is23113()) {
            return Glyph.DEVICE_23113;
        }
        if (Common.is24111()) {
            return Glyph.DEVICE_24111;
        }
        if (Common.is25111()) {
            return Glyph.DEVICE_25111;
        }
        String model = Build.MODEL != null ? Build.MODEL.toUpperCase() : "";
        if (model.contains("A065") || model.contains("PHONE (2)")) {
            return Glyph.DEVICE_22111;
        }
        if (model.contains("A142") || model.contains("PHONE (2A)") || model.contains("PHONE 2A")) {
            return Glyph.DEVICE_23111;
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
     * Nothing Phone (1) glyph burst particle effect with haptic feedback.
     */
    public void playPhone1BurstEffect(Context context) {
        handler.post(() -> {
            if (!ensureReady()) {
                return;
            }
            if (stripManager != null && stripSessionOpen) {
                try {
                    GlyphFrame.Builder builder = stripManager.getGlyphFrameBuilder();
                    if (builder != null) {
                        builder.buildChannelB()
                                .buildChannelA()
                                .buildChannelE()
                                .buildChannelC()
                                .buildChannelD()
                                .buildPeriod(80)
                                .buildCycles(4)
                                .buildInterval(30);
                        stripManager.animate(builder.build());
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
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

    private void runPendingPreview() {
        if (!previewPending) {
            return;
        }
        previewPending = false;
        handler.post(this::preview);
    }

    private boolean ensureReady() {
        if (!supported || !GlyphConfig.enabled()) {
            return false;
        }
        if (!initialized) {
            init();
        }
        return initialized && (stripSessionOpen || matrixRegistered);
    }

    private boolean isMatrixDevice() {
        try {
            return Common.is23112() || Common.is25111p();
        } catch (Throwable t) {
            return false;
        }
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
                if (!registered) {
                    try {
                        Field serviceField = GlyphManager.class.getDeclaredField("mService");
                        serviceField.setAccessible(true);
                        Object serviceObj = serviceField.get(stripManager);
                        if (serviceObj instanceof com.nothing.thirdparty.IGlyphService) {
                            com.nothing.thirdparty.IGlyphService glyphService = (com.nothing.thirdparty.IGlyphService) serviceObj;
                            registered = glyphService.register(targetDevice);
                            if (!registered) {
                                String appKey = getAppKey(context);
                                registered = glyphService.registerSDK(appKey, targetDevice);
                            }
                            if (registered) {
                                Field authField = GlyphManager.class.getDeclaredField("mHasAuthorized");
                                authField.setAccessible(true);
                                authField.setBoolean(stripManager, true);

                                Field devField = GlyphManager.class.getDeclaredField("mDevice");
                                devField.setAccessible(true);
                                devField.set(stripManager, targetDevice);
                            }
                        }
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
                if (registered) {
                    try {
                        Field devField = GlyphManager.class.getDeclaredField("mDevice");
                        devField.setAccessible(true);
                        Object currentDev = devField.get(stripManager);
                        if (currentDev == null || !targetDevice.equals(currentDev)) {
                            devField.set(stripManager, targetDevice);
                        }
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
                if (!registered) {
                    return;
                }
                try {
                    stripManager.openSession();
                    stripSessionOpen = true;
                    runPendingPreview();
                } catch (GlyphException e) {
                    FileLog.e(e);
                }
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
                matrixRegistered = matrixManager.register(
                        Common.is23112() ? Glyph.DEVICE_23112 : Glyph.DEVICE_25111p);
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
                    builder.buildChannelA()
                            .buildChannelB()
                            .buildChannelC()
                            .buildChannelD()
                            .buildChannelE()
                            .buildPeriod(140)
                            .buildCycles(3)
                            .buildInterval(60);
                    break;
                case GlyphConfig.MESSAGE_ANIM_SOFT_PULSE:
                    builder.buildChannelA()
                            .buildChannelB()
                            .buildChannelC()
                            .buildChannelD()
                            .buildChannelE()
                            .buildPeriod(550)
                            .buildCycles(1)
                            .buildInterval(0);
                    break;
                case GlyphConfig.MESSAGE_ANIM_ACCENT_RING:
                    builder.buildChannelB()
                            .buildPeriod(300)
                            .buildCycles(2)
                            .buildInterval(100);
                    break;
                case GlyphConfig.MESSAGE_ANIM_DOUBLE_FLASH:
                default:
                    builder.buildChannelA()
                            .buildChannelB()
                            .buildChannelC()
                            .buildChannelD()
                            .buildChannelE()
                            .buildPeriod(250)
                            .buildCycles(2)
                            .buildInterval(80);
                    break;
            }
            stripManager.animate(builder.build());
        } catch (Throwable t) {
            FileLog.e(t);
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
                        GlyphFrame breathingFrame = b
                                .buildChannelA()
                                .buildChannelB()
                                .buildChannelC()
                                .buildChannelD()
                                .buildChannelE()
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
                        GlyphFrame pulseFrame = b
                                .buildChannelA()
                                .buildChannelB()
                                .buildChannelC()
                                .buildChannelD()
                                .buildChannelE()
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
                    builder.buildChannelB()
                            .buildPeriod(1600)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(0);
                    break;
                case GlyphConfig.RECORDING_ANIM_HEARTBEAT:
                    builder.buildChannelB()
                            .buildChannelD()
                            .buildPeriod(800)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(200);
                    break;
                case GlyphConfig.RECORDING_ANIM_BREATHING:
                default:
                    builder.buildChannelA()
                            .buildChannelB()
                            .buildChannelC()
                            .buildChannelD()
                            .buildChannelE()
                            .buildPeriod(1800)
                            .buildCycles(RECORDING_MAX_CYCLES)
                            .buildInterval(0);
                    break;
            }
            stripManager.animate(builder.build());
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void showMatrixLogo() {
        showMatrixIcon(R.drawable.moltengram_icon_monochrome, BREATH_MAX, true);
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
            GlyphMatrixObject object = new GlyphMatrixObject.Builder()
                    .setImageSource(bitmap)
                    .setScale(105)
                    .setBrightness(brightness)
                    .build();
            GlyphMatrixFrame frame = new GlyphMatrixFrame.Builder()
                    .addTop(object)
                    .build(context);
            matrixManager.setAppMatrixFrame(frame.render());
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
        Bitmap bitmap = GlyphMatrixUtils.drawableToBitmap(icon);
        if (resId == R.drawable.moltengram_icon_monochrome || resId == R.drawable.exteraless_icon_monochrome) {
            matrixLogoBitmap = bitmap;
        } else if (resId == R.drawable.baseline_call_24) {
            matrixCallBitmap = bitmap;
        } else if (resId == R.drawable.baseline_mic_24) {
            matrixMicBitmap = bitmap;
        }
        return bitmap;
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
