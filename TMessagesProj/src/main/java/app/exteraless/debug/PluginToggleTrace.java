package app.exteraless.debug;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import app.exteraless.plugins.PythonPluginsEngine;

public final class PluginToggleTrace {

    private static final long SLOW_MS = 250;
    private static final long STALL_MS = 700;
    private static final long PYTHON_STACKS_TIMEOUT_MS = 500;
    private static final int KEEP = 40;
    private static final int STACK_DEPTH = 18;
    private static final String FILE_NAME = "plugin-toggles.txt";

    private static final ThreadLocal<PluginToggleTrace> CURRENT = new ThreadLocal<>();
    private static final PluginToggleTrace OFF = new PluginToggleTrace(null, false);
    private static final ArrayDeque<String> records = new ArrayDeque<>();
    private static Handler watcher;

    private final String pluginId;
    private final boolean enabling;
    private final long wallStart = System.currentTimeMillis();
    private final ArrayList<String> labels = new ArrayList<>();
    private final ArrayList<Long> times = new ArrayList<>();
    private volatile Thread engineThread;
    private volatile boolean done;
    private volatile String stall;
    private String python;
    private long pythonEnteredNanos;
    private long pythonCallNanos;

    private PluginToggleTrace(String pluginId, boolean enabling) {
        this.pluginId = pluginId;
        this.enabling = enabling;
        mark("tap");
    }

    public static PluginToggleTrace start(String pluginId, boolean enabling) {
        if (!BuildConfig.DEBUG_TOOLS) {
            return OFF;
        }
        final PluginToggleTrace trace = new PluginToggleTrace(pluginId, enabling);
        watcher().postDelayed(() -> {
            if (!trace.done) {
                trace.stall = captureStall(trace);
            }
        }, STALL_MS);
        return trace;
    }

    public static PluginToggleTrace current() {
        return CURRENT.get();
    }

    public synchronized void mark(String label) {
        if (this == OFF) {
            return;
        }
        labels.add(label);
        times.add(System.nanoTime());
    }

    public void enterEngine() {
        if (this == OFF) {
            return;
        }
        engineThread = Thread.currentThread();
        CURRENT.set(this);
        mark("engine");
    }

    public void leaveEngine() {
        if (this == OFF) {
            return;
        }
        CURRENT.remove();
        mark("engine done");
    }

    public void beforePython() {
        if (this == OFF) {
            return;
        }
        pythonCallNanos = System.nanoTime();
        mark("java → python");
    }

    public void afterPython(String result) {
        if (this == OFF) {
            return;
        }
        mark("python → java");
        if (result == null) {
            return;
        }
        try {
            final JSONObject timings = new JSONObject(result).optJSONObject("timings");
            if (timings == null) {
                return;
            }
            pythonEnteredNanos = timings.optLong("entered_ns", 0);
            final StringBuilder out = new StringBuilder();
            final Iterator<String> keys = timings.keys();
            while (keys.hasNext()) {
                final String key = keys.next();
                if ("entered_ns".equals(key)) {
                    continue;
                }
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(key).append(' ').append(String.format(Locale.US, "%.1f", timings.optDouble(key)));
            }
            python = out.toString();
        } catch (Exception ignore) {
        }
    }

    public void finish(boolean ok) {
        if (this == OFF) {
            return;
        }
        mark("ui");
        done = true;
        final String record = format(ok);
        synchronized (records) {
            records.addLast(record);
            while (records.size() > KEEP) {
                records.removeFirst();
            }
        }
        if (totalMs() >= SLOW_MS) {
            FileLog.d("PluginToggleTrace: " + record);
        }
        watcher().post(PluginToggleTrace::save);
    }

    public static File getLog() {
        if (!BuildConfig.DEBUG_TOOLS) {
            return null;
        }
        final File file = new File(AndroidUtilities.getLogsDir(), FILE_NAME);
        return file.exists() ? file : null;
    }

    private synchronized long totalMs() {
        return (times.get(times.size() - 1) - times.get(0)) / 1_000_000;
    }

    private synchronized String format(boolean ok) {
        final StringBuilder out = new StringBuilder();
        final long total = totalMs();
        out.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(wallStart)))
                .append("  ").append(enabling ? "enable " : "disable").append("  ").append(pluginId)
                .append(String.format(Locale.US, "  %d ms", total));
        if (!ok) {
            out.append("  FAILED");
        }
        if (total >= SLOW_MS) {
            out.append("  SLOW");
        }
        out.append('\n');
        for (int i = 1; i < labels.size(); i++) {
            out.append(String.format(Locale.US, "  %-28s %8.1f ms\n",
                    labels.get(i - 1) + " → " + labels.get(i), (times.get(i) - times.get(i - 1)) / 1e6));
        }
        if (pythonEnteredNanos > 0 && pythonCallNanos > 0) {
            out.append(String.format(Locale.US, "  %-28s %8.1f ms\n", "GIL wait + bridge", (pythonEnteredNanos - pythonCallNanos) / 1e6));
        }
        if (python != null) {
            out.append("  python ms: ").append(python).append('\n');
        }
        if (stall != null) {
            out.append(stall);
        }
        return out.toString();
    }

    private static void save() {
        final StringBuilder out = new StringBuilder();
        synchronized (records) {
            for (String record : records) {
                out.append(record).append('\n');
            }
        }
        final File file = new File(AndroidUtilities.getLogsDir(), FILE_NAME);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(out.toString());
        } catch (Exception e) {
            FileLog.e("PluginToggleTrace: cannot write " + file, e);
        }
    }

    private static synchronized Handler watcher() {
        if (watcher == null) {
            final HandlerThread thread = new HandlerThread("plugin-toggle-watch");
            thread.start();
            watcher = new Handler(thread.getLooper());
        }
        return watcher;
    }

    private static String captureStall(PluginToggleTrace trace) {
        final StringBuilder out = new StringBuilder();
        out.append(String.format(Locale.US, "  stalled for %d ms, last step: %s\n", STALL_MS, trace.lastLabel()));
        final Thread engine = trace.engineThread;
        if (engine == null) {
            out.append("  engine thread has not picked the toggle up yet\n");
        } else {
            appendStack(out, "engine thread", engine, engine.getStackTrace());
        }
        final Thread main = Looper.getMainLooper().getThread();
        appendStack(out, "main thread", main, main.getStackTrace());
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            final Thread thread = entry.getKey();
            if (thread == engine || thread == main || !inPython(entry.getValue())) {
                continue;
            }
            appendStack(out, "in python", thread, entry.getValue());
        }
        out.append("  python stacks:\n").append(pythonStacks());
        return out.toString();
    }

    private synchronized String lastLabel() {
        return labels.get(labels.size() - 1);
    }

    private static boolean inPython(StackTraceElement[] stack) {
        for (StackTraceElement element : stack) {
            if (element.getClassName().startsWith("com.chaquo.python.")) {
                return true;
            }
        }
        return false;
    }

    private static void appendStack(StringBuilder out, String title, Thread thread, StackTraceElement[] stack) {
        out.append("  ").append(title).append(" \"").append(thread.getName()).append("\" ")
                .append(thread.getState()).append('\n');
        final int shown = Math.min(stack.length, STACK_DEPTH);
        for (int i = 0; i < shown; i++) {
            out.append("      at ").append(stack[i]).append('\n');
        }
        if (stack.length > shown) {
            out.append("      ... ").append(stack.length - shown).append(" more\n");
        }
    }

    private static String pythonStacks() {
        final FutureTask<String> task = new FutureTask<>(() -> PythonPluginsEngine.getInstance().debugPythonStacks());
        final Thread thread = new Thread(task, "plugin-toggle-pystacks");
        thread.setDaemon(true);
        thread.start();
        try {
            final String stacks = task.get(PYTHON_STACKS_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return stacks == null ? "      unavailable\n" : stacks;
        } catch (java.util.concurrent.TimeoutException e) {
            return String.format(Locale.US, "      no GIL within %d ms: some thread holds it without releasing\n", PYTHON_STACKS_TIMEOUT_MS);
        } catch (Exception e) {
            return "      failed: " + e + "\n";
        }
    }
}
