package app.exteraless.plugins.xposed;

import android.os.Looper;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import app.exteraless.plugins.PluginsWatchdog;

public final class HookStats {

    static volatile boolean enabled;
    private static volatile boolean pythonArmed;

    private static final Set<HookStats> ALL = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final Thread MAIN_THREAD = Looper.getMainLooper().getThread();

    private final int id;
    private final String pluginId;
    private final String side;
    private final AtomicInteger targets = new AtomicInteger();
    private volatile String target = "?";
    private final LongAdder calls = new LongAdder();
    private final LongAdder totalNs = new LongAdder();
    private final AtomicLong maxNs = new AtomicLong();
    private final LongAdder mainCalls = new LongAdder();
    private final LongAdder mainNs = new LongAdder();
    private final AtomicLong mainMaxNs = new AtomicLong();
    private final LongAdder contendedCalls = new LongAdder();
    private final LongAdder contendedNs = new LongAdder();
    private final ConcurrentHashMap<String, LongAdder> blockers = new ConcurrentHashMap<>();

    static int nextId() {
        return NEXT_ID.incrementAndGet();
    }

    HookStats(int id, String pluginId, String side) {
        this.id = id;
        this.pluginId = pluginId;
        this.side = side;
        ALL.add(this);
    }

    void addTarget(Member member) {
        if (member == null) {
            return;
        }
        final String className = member.getDeclaringClass().getName();
        final String name = className.substring(className.lastIndexOf('.') + 1) + "."
                + (member instanceof Constructor ? "<init>" : member.getName());
        final int count = targets.incrementAndGet();
        target = count == 1 ? name : name + " x" + count;
    }

    static boolean isMainThread() {
        return Thread.currentThread() == MAIN_THREAD;
    }

    static String findBlocker(PluginsWatchdog watchdog) {
        if (watchdog == null) {
            return null;
        }
        try {
            final Thread self = Thread.currentThread();
            for (Map.Entry<Thread, PluginsWatchdog.ExecutionInfo> entry : watchdog.getExecutingPlugins().entrySet()) {
                if (entry.getKey() != self) {
                    return entry.getValue().getPluginId();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    void record(long elapsedNs, boolean main, String blocker) {
        calls.increment();
        totalNs.add(elapsedNs);
        updateMax(maxNs, elapsedNs);
        if (!main) {
            return;
        }
        mainCalls.increment();
        mainNs.add(elapsedNs);
        updateMax(mainMaxNs, elapsedNs);
        if (blocker != null) {
            contendedCalls.increment();
            contendedNs.add(elapsedNs);
            blockers.computeIfAbsent(blocker, key -> new LongAdder()).increment();
        }
    }

    private static void updateMax(AtomicLong max, long value) {
        long current;
        while (value > (current = max.get()) && !max.compareAndSet(current, value)) {
        }
    }

    private void reset() {
        calls.reset();
        totalNs.reset();
        maxNs.set(0);
        mainCalls.reset();
        mainNs.reset();
        mainMaxNs.set(0);
        contendedCalls.reset();
        contendedNs.reset();
        blockers.clear();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void start() {
        synchronized (ALL) {
            for (HookStats stats : ALL) {
                stats.reset();
            }
        }
        enabled = true;
        pythonArmed = Python.isStarted();
        callProfile("start");
    }

    public static void armPython() {
        if (enabled && !pythonArmed && Python.isStarted()) {
            pythonArmed = true;
            callProfile("start");
        }
    }

    public static void stop() {
        enabled = false;
        pythonArmed = false;
        callProfile("stop");
    }

    private static PyObject callProfile(String function) {
        if (!Python.isStarted()) {
            return null;
        }
        try {
            return Python.getInstance().getModule("extera_utils.hook_profile").callAttr(function);
        } catch (Throwable t) {
            FileLog.e("HookStats: hook_profile." + function + " failed", t);
            return null;
        }
    }

    public static void appendReport(StringBuilder out) {
        out.append("\n== plugin hooks ==\n");
        JSONObject python = null;
        final PyObject snapshot = callProfile("snapshot");
        if (snapshot != null) {
            try {
                python = new JSONObject(snapshot.toString());
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        appendGc(out, python != null ? python.optJSONArray("gc") : null);

        final JSONObject bodies = python != null ? python.optJSONObject("hooks") : null;
        final ArrayList<HookStats> active = new ArrayList<>();
        synchronized (ALL) {
            for (HookStats stats : ALL) {
                if (stats.calls.sum() > 0) {
                    active.add(stats);
                }
            }
        }
        if (active.isEmpty()) {
            out.append("no plugin hook calls\n");
            return;
        }
        active.sort((a, b) -> {
            final int byMain = Long.compare(b.mainNs.sum(), a.mainNs.sum());
            return byMain != 0 ? byMain : Long.compare(b.totalNs.sum(), a.totalNs.sum());
        });
        out.append("     calls  total ms    avg us   max ms  python ms  main calls   main ms  main max  contended  hook\n");
        for (HookStats stats : active) {
            final long calls = stats.calls.sum();
            final long total = stats.totalNs.sum();
            final JSONArray body = bodies != null ? bodies.optJSONArray(String.valueOf(stats.id)) : null;
            final String pythonMs = body != null ? String.format(Locale.US, "%.1f", body.optLong(1) / 1e6) : "-";
            out.append(String.format(Locale.US, "%10d %9.1f %9.1f %8.2f %10s %11d %9.1f %9.2f %4d/%-5.0f  %s %s %s\n",
                    calls, total / 1e6, total / 1e3 / calls, stats.maxNs.get() / 1e6, pythonMs,
                    stats.mainCalls.sum(), stats.mainNs.sum() / 1e6, stats.mainMaxNs.get() / 1e6,
                    stats.contendedCalls.sum(), stats.contendedNs.sum() / 1e6,
                    stats.pluginId, stats.side, stats.target));
            if (!stats.blockers.isEmpty()) {
                out.append("            main thread waited while another thread ran:");
                for (Map.Entry<String, LongAdder> entry : stats.blockers.entrySet()) {
                    out.append(' ').append(entry.getKey()).append(' ').append(entry.getValue().sum());
                }
                out.append('\n');
            }
        }
    }

    private static void appendGc(StringBuilder out, JSONArray collections) {
        out.append("python gc:");
        if (collections == null || collections.length() == 0) {
            out.append(" no collections\n");
            return;
        }
        for (int i = 0; i < collections.length(); i++) {
            final JSONArray entry = collections.optJSONArray(i);
            if (entry == null) {
                continue;
            }
            out.append(String.format(Locale.US, " gen%d%s %d runs %.1f ms (max %.2f ms);",
                    entry.optInt(0), entry.optBoolean(1) ? " main" : "", entry.optLong(2),
                    entry.optLong(3) / 1e6, entry.optLong(4) / 1e6));
        }
        out.append('\n');
    }
}
