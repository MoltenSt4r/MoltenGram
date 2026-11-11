package app.exteraless.debug;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import app.exteraless.plugins.xposed.HookStats;
import app.exteraless.plugins.xposed.XposedHooks;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class EnergyProfiler {

    private static final String PREFS = "exteraless_energy_profiler";
    private static final String KEY_RUNNING = "running";
    private static final String KEY_WAKELOCK_PENDING = "wakelock_hooks_pending";
    private static final String KEY_WAKELOCK_BROKEN = "wakelock_hooks_broken";
    private static final String STATE_FILE = "energy-profiler-state.json";
    private static final String REPORT_PREFIX = "energy-";
    private static final long SAMPLE_INTERVAL_MS = 30_000;
    private static final long SAVE_INTERVAL_MS = 120_000;
    private static final int REPORT_TOP = 30;

    private static final int FOREGROUND = 0;
    private static final int BACKGROUND = 1;
    private static final int SCREEN_OFF = 2;
    private static final String[] STATE_NAMES = {"foreground", "background, screen on", "screen off"};

    private static EnergyProfiler instance;

    public static synchronized boolean isRunning() {
        return instance != null;
    }

    public static void start(Context context) {
        final Context app = context.getApplicationContext();
        synchronized (EnergyProfiler.class) {
            if (instance != null) {
                return;
            }
            prefs(app).edit().putBoolean(KEY_RUNNING, true).commit();
            stateFile(app).delete();
            instance = new EnergyProfiler(app, null);
        }
    }

    public static void resumeIfEnabled(Context context) {
        try {
            final Context app = context.getApplicationContext();
            final SharedPreferences prefs = prefs(app);
            if (!prefs.getBoolean(KEY_RUNNING, false)) {
                return;
            }
            if (prefs.getBoolean(KEY_WAKELOCK_PENDING, false)) {
                prefs.edit().remove(KEY_WAKELOCK_PENDING).putBoolean(KEY_WAKELOCK_BROKEN, true).commit();
            }
            synchronized (EnergyProfiler.class) {
                if (instance == null) {
                    instance = new EnergyProfiler(app, readState(app));
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void stop(Utilities.Callback<File> onReady) {
        final EnergyProfiler profiler;
        synchronized (EnergyProfiler.class) {
            profiler = instance;
            instance = null;
        }
        if (profiler == null) {
            onReady.run(getLastReport());
            return;
        }
        prefs(profiler.context).edit().putBoolean(KEY_RUNNING, false).commit();
        profiler.report(true, onReady);
    }

    public static void shareCurrent(Utilities.Callback<File> onReady) {
        final EnergyProfiler profiler;
        synchronized (EnergyProfiler.class) {
            profiler = instance;
        }
        if (profiler == null) {
            onReady.run(getLastReport());
            return;
        }
        profiler.report(false, onReady);
    }

    public static File getLastReport() {
        final File[] files = AndroidUtilities.getLogsDir().listFiles((dir, name) -> name.startsWith(REPORT_PREFIX) && name.endsWith(".txt"));
        File last = null;
        if (files != null) {
            for (File file : files) {
                if (last == null || file.lastModified() > last.lastModified()) {
                    last = file;
                }
            }
        }
        return last;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File stateFile(Context context) {
        return new File(context.getFilesDir(), STATE_FILE);
    }

    private static JSONObject readState(Context context) {
        final File file = stateFile(context);
        if (!file.exists()) {
            return null;
        }
        try {
            return new JSONObject(readSmall(file));
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private static final class Bucket {
        long wallMs;
        long awakeMs;
        long cpuTicks;
        long rxBytes;
        long txBytes;
        long dischargeUah;
        long dischargeMs;
        long currentSumUa;
        long currentSamples;
        final HashMap<String, Long> threads = new HashMap<>();
        final HashMap<String, long[]> wakelocks = new HashMap<>();

        JSONObject toJson() throws Exception {
            final JSONObject out = new JSONObject();
            out.put("wall", wallMs);
            out.put("awake", awakeMs);
            out.put("cpu", cpuTicks);
            out.put("rx", rxBytes);
            out.put("tx", txBytes);
            out.put("dischargeUah", dischargeUah);
            out.put("dischargeMs", dischargeMs);
            out.put("currentSum", currentSumUa);
            out.put("currentSamples", currentSamples);
            final JSONObject threadsJson = new JSONObject();
            for (Map.Entry<String, Long> entry : threads.entrySet()) {
                threadsJson.put(entry.getKey(), entry.getValue());
            }
            out.put("threads", threadsJson);
            final JSONObject locksJson = new JSONObject();
            for (Map.Entry<String, long[]> entry : wakelocks.entrySet()) {
                locksJson.put(entry.getKey(), new JSONArray().put(entry.getValue()[0]).put(entry.getValue()[1]));
            }
            out.put("wakelocks", locksJson);
            return out;
        }

        void fromJson(JSONObject in) {
            wallMs = in.optLong("wall");
            awakeMs = in.optLong("awake");
            cpuTicks = in.optLong("cpu");
            rxBytes = in.optLong("rx");
            txBytes = in.optLong("tx");
            dischargeUah = in.optLong("dischargeUah");
            dischargeMs = in.optLong("dischargeMs");
            currentSumUa = in.optLong("currentSum");
            currentSamples = in.optLong("currentSamples");
            final JSONObject threadsJson = in.optJSONObject("threads");
            if (threadsJson != null) {
                for (Iterator<String> it = threadsJson.keys(); it.hasNext(); ) {
                    final String key = it.next();
                    threads.put(key, threadsJson.optLong(key));
                }
            }
            final JSONObject locksJson = in.optJSONObject("wakelocks");
            if (locksJson != null) {
                for (Iterator<String> it = locksJson.keys(); it.hasNext(); ) {
                    final String key = it.next();
                    final JSONArray value = locksJson.optJSONArray(key);
                    if (value != null) {
                        wakelocks.put(key, new long[]{value.optLong(0), value.optLong(1)});
                    }
                }
            }
        }
    }

    private static final class HeldLock {
        final String tag;
        long accountedRealtime;

        HeldLock(String tag, long accountedRealtime) {
            this.tag = tag;
            this.accountedRealtime = accountedRealtime;
        }
    }

    private final Context context;
    private final HandlerThread thread;
    private final Handler handler;
    private final long clockTicks;
    private final int uid;
    private final Bucket[] buckets = {new Bucket(), new Bucket(), new Bucket()};
    private final long sessionStart;
    private final long previousWallMs;
    private long samples;
    private final HashMap<Integer, Long> threadTicks = new HashMap<>();
    private boolean haveBaseline;
    private long lastRealtime;
    private long lastUptime;
    private long lastProcessTicks;
    private long lastRx = TrafficStats.UNSUPPORTED;
    private long lastTx = TrafficStats.UNSUPPORTED;
    private long lastCharge = Long.MIN_VALUE;
    private boolean lastCharging;
    private long lastSave;
    private volatile int lastState;
    private volatile int resumedActivities;
    private volatile boolean stopped;
    private final BroadcastReceiver screenReceiver;
    private final Application.ActivityLifecycleCallbacks lifecycleCallbacks;
    private boolean ownHookStats;
    private String wakelockStatus = "not tracked";
    private boolean wakelocksWaiting;
    private final ArrayList<XC_MethodHook.Unhook> unhooks = new ArrayList<>();
    private final IdentityHashMap<Object, HeldLock> heldLocks = new IdentityHashMap<>();
    private final Runnable tick = this::tick;

    private EnergyProfiler(Context context, JSONObject saved) {
        this.context = context;
        long ticks = 100;
        try {
            ticks = Os.sysconf(OsConstants._SC_CLK_TCK);
        } catch (Throwable ignored) {
        }
        clockTicks = ticks > 0 ? ticks : 100;
        uid = Process.myUid();
        long start = System.currentTimeMillis();
        long previous = 0;
        if (saved != null) {
            start = saved.optLong("start", start);
            samples = saved.optLong("samples");
            final JSONArray savedBuckets = saved.optJSONArray("buckets");
            if (savedBuckets != null) {
                for (int i = 0; i < buckets.length && i < savedBuckets.length(); i++) {
                    final JSONObject bucket = savedBuckets.optJSONObject(i);
                    if (bucket != null) {
                        buckets[i].fromJson(bucket);
                        previous += buckets[i].wallMs;
                    }
                }
            }
        }
        sessionStart = start;
        previousWallMs = previous;
        resumedActivities = ApplicationLoader.mainInterfacePaused ? 0 : 1;
        lastState = currentState();

        thread = new HandlerThread("energy-profiler", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        handler = new Handler(thread.getLooper());

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                sampleSoon();
            }
        };
        final IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        try {
            context.registerReceiver(screenReceiver, filter);
        } catch (Throwable t) {
            FileLog.e(t);
        }

        lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (resumedActivities++ == 0) {
                    sampleSoon();
                }
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (resumedActivities > 0 && --resumedActivities == 0) {
                    sampleSoon();
                }
            }

            @Override
            public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
            }
        };
        if (context instanceof Application) {
            ((Application) context).registerActivityLifecycleCallbacks(lifecycleCallbacks);
        }

        if (!JankProfiler.isRunning() && !HookStats.isEnabled()) {
            HookStats.start();
            ownHookStats = true;
        }
        handler.post(() -> {
            installWakelockHooks();
            tick();
        });
    }

    private void sampleSoon() {
        if (stopped) {
            return;
        }
        handler.post(this::sample);
    }

    private void tick() {
        if (stopped) {
            return;
        }
        if (wakelocksWaiting) {
            installWakelockHooks();
        }
        if (ownHookStats) {
            HookStats.armPython();
        }
        sample();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, SAMPLE_INTERVAL_MS);
    }

    private int currentState() {
        boolean interactive = true;
        try {
            final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            interactive = pm == null || pm.isInteractive();
        } catch (Throwable ignored) {
        }
        if (!interactive) {
            return SCREEN_OFF;
        }
        return resumedActivities > 0 ? FOREGROUND : BACKGROUND;
    }

    private void sample() {
        try {
            sampleInternal();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void sampleInternal() {
        final long realtime = SystemClock.elapsedRealtime();
        final long uptime = SystemClock.uptimeMillis();
        final int state = currentState();
        final Bucket interval = buckets[lastState];
        if (haveBaseline) {
            interval.wallMs += Math.max(0, realtime - lastRealtime);
            interval.awakeMs += Math.max(0, uptime - lastUptime);
        }

        final long processTicks = readProcessTicks();
        if (haveBaseline && processTicks >= lastProcessTicks && lastProcessTicks > 0) {
            interval.cpuTicks += processTicks - lastProcessTicks;
        }
        if (processTicks > 0) {
            lastProcessTicks = processTicks;
        }
        readThreads(interval);

        final long rx = TrafficStats.getUidRxBytes(uid);
        final long tx = TrafficStats.getUidTxBytes(uid);
        if (haveBaseline && rx != TrafficStats.UNSUPPORTED && lastRx != TrafficStats.UNSUPPORTED && rx >= lastRx) {
            interval.rxBytes += rx - lastRx;
        }
        if (haveBaseline && tx != TrafficStats.UNSUPPORTED && lastTx != TrafficStats.UNSUPPORTED && tx >= lastTx) {
            interval.txBytes += tx - lastTx;
        }
        lastRx = rx;
        lastTx = tx;

        long charge = Long.MIN_VALUE;
        boolean charging = false;
        int current = Integer.MIN_VALUE;
        try {
            final BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                charge = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                charging = bm.isCharging();
                current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            }
        } catch (Throwable ignored) {
        }
        if (haveBaseline && !charging && !lastCharging && charge > 0 && lastCharge > 0 && lastCharge >= charge) {
            interval.dischargeUah += lastCharge - charge;
            interval.dischargeMs += Math.max(0, realtime - lastRealtime);
        }
        if (!charging && current != Integer.MIN_VALUE && current != 0) {
            buckets[state].currentSumUa += Math.abs((long) current);
            buckets[state].currentSamples++;
        }
        lastCharge = charge;
        lastCharging = charging;

        accountHeldLocks(realtime, interval);

        lastRealtime = realtime;
        lastUptime = uptime;
        lastState = state;
        haveBaseline = true;
        samples++;
        if (realtime - lastSave >= SAVE_INTERVAL_MS) {
            lastSave = realtime;
            save();
        }
    }

    private long readProcessTicks() {
        try {
            return parseTicks(readSmall(new File("/proc/self/stat")));
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long parseTicks(String stat) {
        final int close = stat.lastIndexOf(')');
        if (close < 0) {
            return -1;
        }
        final String[] fields = stat.substring(close + 2).split(" ");
        if (fields.length < 13) {
            return -1;
        }
        return Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
    }

    private static String parseComm(String stat) {
        final int open = stat.indexOf('(');
        final int close = stat.lastIndexOf(')');
        if (open < 0 || close <= open) {
            return "?";
        }
        return stat.substring(open + 1, close);
    }

    private void readThreads(Bucket interval) {
        final File[] tasks = new File("/proc/self/task").listFiles();
        if (tasks == null) {
            return;
        }
        final HashSet<Integer> seen = new HashSet<>();
        for (File task : tasks) {
            final int tid;
            try {
                tid = Integer.parseInt(task.getName());
            } catch (NumberFormatException e) {
                continue;
            }
            final String stat;
            try {
                stat = readSmall(new File(task, "stat"));
            } catch (Throwable t) {
                continue;
            }
            final long ticks = parseTicks(stat);
            if (ticks < 0) {
                continue;
            }
            seen.add(tid);
            final Long previous = threadTicks.put(tid, ticks);
            long delta;
            if (previous == null) {
                delta = haveBaseline ? ticks : 0;
            } else {
                delta = ticks >= previous ? ticks - previous : ticks;
            }
            if (delta > 0) {
                final String name = normalizeThreadName(parseComm(stat));
                final Long total = interval.threads.get(name);
                interval.threads.put(name, total == null ? delta : total + delta);
            }
        }
        threadTicks.keySet().retainAll(seen);
    }

    private static String normalizeThreadName(String name) {
        final StringBuilder out = new StringBuilder(name.length());
        boolean digits = false;
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c >= '0' && c <= '9') {
                if (!digits) {
                    out.append('#');
                    digits = true;
                }
            } else {
                out.append(c);
                digits = false;
            }
        }
        return out.toString().trim();
    }

    private void installWakelockHooks() {
        final SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean(KEY_WAKELOCK_BROKEN, false)) {
            wakelockStatus = "not tracked: hooking wake locks killed the process earlier";
            return;
        }
        if (!XposedHooks.isReady()) {
            wakelocksWaiting = true;
            wakelockStatus = "not tracked: native hooks are not active (no plugin uses hooks)";
            return;
        }
        wakelocksWaiting = false;
        prefs.edit().putBoolean(KEY_WAKELOCK_PENDING, true).commit();
        try {
            final XC_MethodHook acquire = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    onAcquired(param.thisObject);
                }
            };
            final XC_MethodHook release = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    onReleased(param.thisObject);
                }
            };
            unhooks.addAll(XposedBridge.hookAllMethods(PowerManager.WakeLock.class, "acquire", acquire));
            unhooks.addAll(XposedBridge.hookAllMethods(PowerManager.WakeLock.class, "release", release));
            wakelockStatus = "tracked";
        } catch (Throwable t) {
            wakelockStatus = "not tracked: " + t;
            FileLog.e(t);
        } finally {
            prefs.edit().remove(KEY_WAKELOCK_PENDING).commit();
        }
    }

    private static String wakelockTag(Object lock) {
        final String text = String.valueOf(lock);
        final int space = text.indexOf(' ');
        final int held = text.lastIndexOf(" held=");
        if (space >= 0 && held > space) {
            return text.substring(space + 1, held);
        }
        return text;
    }

    private static boolean isHeld(Object lock) {
        try {
            return ((PowerManager.WakeLock) lock).isHeld();
        } catch (Throwable t) {
            return false;
        }
    }

    private long[] wakelockEntry(Bucket bucket, String tag) {
        long[] entry = bucket.wakelocks.get(tag);
        if (entry == null) {
            entry = new long[2];
            bucket.wakelocks.put(tag, entry);
        }
        return entry;
    }

    private void onAcquired(Object lock) {
        if (stopped || lock == null) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        final String tag = wakelockTag(lock);
        synchronized (heldLocks) {
            wakelockEntry(buckets[lastState], tag)[0]++;
            if (!heldLocks.containsKey(lock) && isHeld(lock)) {
                heldLocks.put(lock, new HeldLock(tag, now));
            }
        }
    }

    private void onReleased(Object lock) {
        if (stopped || lock == null) {
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        synchronized (heldLocks) {
            final HeldLock held = heldLocks.get(lock);
            if (held != null && !isHeld(lock)) {
                wakelockEntry(buckets[lastState], held.tag)[1] += Math.max(0, now - held.accountedRealtime);
                heldLocks.remove(lock);
            }
        }
    }

    private void accountHeldLocks(long now, Bucket interval) {
        synchronized (heldLocks) {
            for (Iterator<Map.Entry<Object, HeldLock>> it = heldLocks.entrySet().iterator(); it.hasNext(); ) {
                final Map.Entry<Object, HeldLock> entry = it.next();
                final HeldLock held = entry.getValue();
                wakelockEntry(interval, held.tag)[1] += Math.max(0, now - held.accountedRealtime);
                held.accountedRealtime = now;
                if (!isHeld(entry.getKey())) {
                    it.remove();
                }
            }
        }
    }

    private JSONObject stateJson() throws Exception {
        final JSONObject out = new JSONObject();
        out.put("start", sessionStart);
        out.put("samples", samples);
        final JSONArray array = new JSONArray();
        synchronized (heldLocks) {
            for (Bucket bucket : buckets) {
                array.put(bucket.toJson());
            }
        }
        out.put("buckets", array);
        return out;
    }

    private void save() {
        try {
            final File file = stateFile(context);
            final File tmp = new File(file.getPath() + ".tmp");
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                writer.write(stateJson().toString());
            }
            if (!tmp.renameTo(file)) {
                FileLog.e("EnergyProfiler: cannot save state");
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void report(boolean finish, Utilities.Callback<File> onReady) {
        handler.post(() -> {
            sample();
            File file = null;
            try {
                file = writeReport();
            } catch (Throwable t) {
                FileLog.e(t);
            }
            if (finish) {
                shutdown();
            } else {
                save();
            }
            final File result = file;
            AndroidUtilities.runOnUIThread(() -> onReady.run(result));
        });
    }

    private void shutdown() {
        stopped = true;
        handler.removeCallbacks(tick);
        for (XC_MethodHook.Unhook unhook : unhooks) {
            try {
                unhook.unhook();
            } catch (Throwable ignored) {
            }
        }
        unhooks.clear();
        try {
            context.unregisterReceiver(screenReceiver);
        } catch (Throwable ignored) {
        }
        if (context instanceof Application) {
            ((Application) context).unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
        }
        if (ownHookStats) {
            HookStats.stop();
        }
        stateFile(context).delete();
        thread.quitSafely();
    }

    private File writeReport() throws Exception {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        final StringBuilder out = new StringBuilder();
        out.append("exteraless energy profile\n");
        out.append("app ").append(BuildVars.BUILD_VERSION_STRING).append(" (").append(BuildConfig.VERSION_CODE)
                .append(BuildVars.BUILD_COMMIT_ID.isEmpty() ? "" : ", " + BuildVars.BUILD_COMMIT_ID).append(")")
                .append(", ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        out.append("started ").append(format.format(new Date(sessionStart)))
                .append(", report ").append(format.format(new Date())).append('\n');
        long totalWall = 0;
        long totalCpu = 0;
        for (Bucket bucket : buckets) {
            totalWall += bucket.wallMs;
            totalCpu += bucket.cpuTicks;
        }
        out.append(String.format(Locale.US, "tracked %s in %d samples (%s before the last app restart), clock %d ticks/s\n",
                duration(totalWall), samples, duration(previousWallMs), clockTicks));
        out.append("wake locks: ").append(wakelockStatus).append('\n');

        out.append("\n== summary ==\n");
        out.append("state                    wall        awake     cpu s   cpu %   net rx MB  tx MB   battery mAh  mAh/h   avg mA\n");
        for (int i = 0; i < buckets.length; i++) {
            final Bucket b = buckets[i];
            final double cpuSeconds = b.cpuTicks / (double) clockTicks;
            final double cpuPercent = b.wallMs > 0 ? cpuSeconds * 100_000.0 / b.wallMs : 0;
            final double mah = b.dischargeUah / 1000.0;
            final double mahPerHour = b.dischargeMs > 0 ? mah * 3_600_000.0 / b.dischargeMs : 0;
            final double avgMa = b.currentSamples > 0 ? b.currentSumUa / 1000.0 / b.currentSamples : 0;
            out.append(String.format(Locale.US, "%-22s %10s %10s %9.1f %7.2f %10.2f %6.2f %12.1f %7.1f %8.1f\n",
                    STATE_NAMES[i], duration(b.wallMs), duration(b.awakeMs), cpuSeconds, cpuPercent,
                    b.rxBytes / 1048576.0, b.txBytes / 1048576.0, mah, mahPerHour, avgMa));
        }
        out.append(String.format(Locale.US, "total cpu %.1f s; battery numbers are for the whole phone while unplugged\n",
                totalCpu / (double) clockTicks));

        final HashMap<String, Long> overall = new HashMap<>();
        for (Bucket bucket : buckets) {
            for (Map.Entry<String, Long> entry : bucket.threads.entrySet()) {
                final Long value = overall.get(entry.getKey());
                overall.put(entry.getKey(), value == null ? entry.getValue() : value + entry.getValue());
            }
        }
        appendThreads(out, "all states", overall);
        for (int i = 0; i < buckets.length; i++) {
            appendThreads(out, STATE_NAMES[i], buckets[i].threads);
        }

        out.append("\n== wake locks ==\n");
        boolean anyLock = false;
        for (int i = 0; i < buckets.length; i++) {
            final ArrayList<Map.Entry<String, long[]>> locks = new ArrayList<>();
            synchronized (heldLocks) {
                for (Map.Entry<String, long[]> entry : buckets[i].wakelocks.entrySet()) {
                    locks.add(new java.util.AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue().clone()));
                }
            }
            if (locks.isEmpty()) {
                continue;
            }
            anyLock = true;
            locks.sort((a, b) -> Long.compare(b.getValue()[1], a.getValue()[1]));
            out.append("-- ").append(STATE_NAMES[i]).append('\n');
            out.append("   acquires    held s  tag\n");
            for (Map.Entry<String, long[]> entry : locks) {
                out.append(String.format(Locale.US, "%11d %9.1f  %s\n", entry.getValue()[0], entry.getValue()[1] / 1000.0, entry.getKey()));
            }
        }
        if (!anyLock) {
            out.append("none recorded\n");
        }

        if (ownHookStats) {
            HookStats.appendReport(out);
        }

        final File file = new File(AndroidUtilities.getLogsDir(), REPORT_PREFIX
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(out.toString());
        }
        return file;
    }

    private void appendThreads(StringBuilder out, String title, HashMap<String, Long> threads) {
        long total = 0;
        for (Long value : threads.values()) {
            total += value;
        }
        out.append("\n== threads, ").append(title).append(String.format(Locale.US, " (%.1f s cpu) ==\n", total / (double) clockTicks));
        if (total == 0) {
            out.append("no cpu time recorded\n");
            return;
        }
        final ArrayList<Map.Entry<String, Long>> entries = new ArrayList<>(threads.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        out.append("     cpu s      %  thread\n");
        int shown = 0;
        long rest = 0;
        for (Map.Entry<String, Long> entry : entries) {
            if (shown++ < REPORT_TOP) {
                out.append(String.format(Locale.US, "%10.1f %6.1f  %s\n",
                        entry.getValue() / (double) clockTicks, entry.getValue() * 100.0 / total, entry.getKey()));
            } else {
                rest += entry.getValue();
            }
        }
        if (rest > 0) {
            out.append(String.format(Locale.US, "%10.1f %6.1f  (%d more)\n", rest / (double) clockTicks, rest * 100.0 / total, entries.size() - REPORT_TOP));
        }
    }

    private static String duration(long ms) {
        final long minutes = ms / 60_000;
        if (minutes >= 60) {
            return String.format(Locale.US, "%dh %02dm", minutes / 60, minutes % 60);
        }
        return String.format(Locale.US, "%dm %02ds", minutes, (ms / 1000) % 60);
    }

    private static String readSmall(File file) throws Exception {
        try (FileInputStream in = new FileInputStream(file)) {
            final byte[] buffer = new byte[4096];
            final StringBuilder out = new StringBuilder();
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
            return out.toString();
        }
    }
}
