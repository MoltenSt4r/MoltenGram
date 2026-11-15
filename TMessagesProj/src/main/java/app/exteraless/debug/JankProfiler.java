package app.exteraless.debug;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Printer;
import android.view.FrameMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import app.exteraless.plugins.Plugin;
import app.exteraless.plugins.PluginsController;
import app.exteraless.plugins.xposed.HookStats;

public final class JankProfiler {

    private static final long TRIGGER_NS = 8_000_000L;
    private static final long SLOW_NS = 16_000_000L;
    private static final long SAMPLE_INTERVAL_MS = 4;
    private static final long AUTOSAVE_INTERVAL_MS = 60_000;
    private static final int FOLDED_DEPTH = 64;
    private static final int SIGNATURE_DEPTH = 12;
    private static final int STACK_REPORT_DEPTH = 40;
    private static final int MAX_SLOWEST = 80;
    private static final int MAX_FOLDED = 30_000;
    private static final int REPORT_TOP = 40;
    private static final int REPORT_FOLDED = 4000;
    private static final String REPORT_PREFIX = "jank-";
    private static final String[] APP_PACKAGES = {
            "org.telegram.", "app.exteraless.", "tw.nekomimi.", "xyz.nextalone.",
            "com.exteragram.", "com.radolyn.", "me.vkryl."
    };

    private static JankProfiler instance;

    public static boolean isRunning() {
        return instance != null;
    }

    public static void start(Activity activity) {
        if (instance != null) {
            return;
        }
        instance = new JankProfiler(activity);
    }

    public static void stop(Utilities.Callback<File> onReady) {
        final JankProfiler profiler = instance;
        instance = null;
        if (profiler == null) {
            onReady.run(getLastReport());
            return;
        }
        profiler.finish(onReady);
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

    private static final class Sample {
        final long seq;
        final StackTraceElement[] stack;

        Sample(long seq, StackTraceElement[] stack) {
            this.seq = seq;
            this.stack = stack;
        }
    }

    private static final class SlowMessage {
        final long seq;
        final long durationNs;
        final String name;
        final Class<?> screen;

        SlowMessage(long seq, long durationNs, String name, Class<?> screen) {
            this.seq = seq;
            this.durationNs = durationNs;
            this.name = name;
            this.screen = screen;
        }
    }

    private static final class SlowEntry {
        final long durationNs;
        final String screen;
        final String name;
        final int samples;
        final String stack;

        SlowEntry(long durationNs, String screen, String name, int samples, String stack) {
            this.durationNs = durationNs;
            this.screen = screen;
            this.name = name;
            this.samples = samples;
            this.stack = stack;
        }
    }

    private static final class FrameStats {
        long frames;
        long janky;
        long over32;
        long over50;
        long over100;
        long over700;
    }

    private static final class SlowStats {
        long count;
        long ns;
    }

    private final Thread mainThread = Looper.getMainLooper().getThread();
    private final long startedAt = SystemClock.elapsedRealtime();
    private final File reportFile;
    private final ConcurrentLinkedQueue<SlowMessage> finishedMessages = new ConcurrentLinkedQueue<>();
    private final ArrayList<Sample> pendingSamples = new ArrayList<>();
    private final HashMap<String, Integer> selfAll = new HashMap<>();
    private final HashMap<String, Integer> selfApp = new HashMap<>();
    private final HashMap<String, Integer> inclusiveApp = new HashMap<>();
    private final HashMap<String, Integer> folded = new HashMap<>();
    private final HashMap<String, SlowStats> slowByScreen = new HashMap<>();
    private final PriorityQueue<SlowEntry> slowest = new PriorityQueue<>((a, b) -> Long.compare(a.durationNs, b.durationNs));
    private final Object frameLock = new Object();
    private final FrameStats frameTotals = new FrameStats();
    private final HashMap<String, FrameStats> frameByScreen = new HashMap<>();
    private final HashSet<Window> windows = new HashSet<>();
    private final HashMap<Window, RedrawTracker> redrawTrackers = new HashMap<>();
    private final HashMap<String, Integer> redrawn = new HashMap<>();
    private final ArrayList<View> dirtyLeaves = new ArrayList<>();
    private final Rect visibleRect = new Rect();
    private long checkedFrames;
    private long cleanFrames;
    private final Application.ActivityLifecycleCallbacks lifecycleCallbacks;
    private final HandlerThread frameThread = new HandlerThread("JankProfilerFrames");
    private final Handler frameHandler;
    private final FrameTracker frameTracker = new FrameTracker();
    private final long defaultFrameNs;
    private final float refreshRate;

    private volatile boolean running = true;
    private volatile long dispatchStart;
    private volatile long dispatchSeq;
    private volatile String dispatchName;
    private volatile Class<?> currentScreen;
    private volatile long messageCount;
    private volatile long busyNs;
    private volatile Utilities.Callback<File> onStopped;

    private long sampleCount;
    private long slowCount;
    private long slowTotalNs;
    private long foldedOverflow;

    private final Printer printer = this::onLooperMessage;

    private JankProfiler(Activity activity) {
        reportFile = new File(AndroidUtilities.getLogsDir(), REPORT_PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
        float rate = 60f;
        try {
            rate = activity.getWindowManager().getDefaultDisplay().getRefreshRate();
        } catch (Exception e) {
            FileLog.e(e);
        }
        refreshRate = rate > 1f ? rate : 60f;
        defaultFrameNs = (long) (1_000_000_000L / refreshRate);
        frameThread.start();
        frameHandler = new Handler(frameThread.getLooper());
        lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(@NonNull Activity resumed) {
                attachWindow(resumed.getWindow());
            }

            @Override
            public void onActivityPaused(@NonNull Activity paused) {
                detachWindow(paused.getWindow());
            }

            @Override
            public void onActivityCreated(@NonNull Activity created, Bundle state) {
            }

            @Override
            public void onActivityStarted(@NonNull Activity started) {
            }

            @Override
            public void onActivityStopped(@NonNull Activity stopped) {
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity saved, @NonNull Bundle state) {
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity destroyed) {
            }
        };
        ((Application) ApplicationLoader.applicationContext).registerActivityLifecycleCallbacks(lifecycleCallbacks);
        attachWindow(activity.getWindow());
        HookStats.start();
        Looper.getMainLooper().setMessageLogging(printer);
        new Thread(this::sample, "JankProfiler").start();
    }

    private void finish(Utilities.Callback<File> onReady) {
        Looper.getMainLooper().setMessageLogging(null);
        HookStats.stop();
        ((Application) ApplicationLoader.applicationContext).unregisterActivityLifecycleCallbacks(lifecycleCallbacks);
        for (Window window : new ArrayList<>(windows)) {
            detachWindow(window);
        }
        frameThread.quitSafely();
        onStopped = onReady;
        running = false;
    }

    private void attachWindow(Window window) {
        if (window == null || !windows.add(window)) {
            return;
        }
        try {
            window.addOnFrameMetricsAvailableListener(frameTracker, frameHandler);
        } catch (Exception e) {
            windows.remove(window);
            FileLog.e(e);
            return;
        }
        final View decor = window.peekDecorView();
        if (decor != null) {
            final RedrawTracker tracker = new RedrawTracker(decor);
            decor.getViewTreeObserver().addOnPreDrawListener(tracker);
            redrawTrackers.put(window, tracker);
        }
    }

    private void detachWindow(Window window) {
        if (window == null || !windows.remove(window)) {
            return;
        }
        try {
            window.removeOnFrameMetricsAvailableListener(frameTracker);
        } catch (Exception e) {
            FileLog.e(e);
        }
        final RedrawTracker tracker = redrawTrackers.remove(window);
        if (tracker != null) {
            final ViewTreeObserver observer = tracker.root.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(tracker);
            }
        }
    }

    private final class RedrawTracker implements ViewTreeObserver.OnPreDrawListener {
        final View root;

        RedrawTracker(View root) {
            this.root = root;
        }

        @Override
        public boolean onPreDraw() {
            recordRedraw(root);
            return true;
        }
    }

    private void recordRedraw(View root) {
        dirtyLeaves.clear();
        collectDirty(root, dirtyLeaves);
        for (int i = dirtyLeaves.size() - 1; i >= 0; i--) {
            if (!dirtyLeaves.get(i).getGlobalVisibleRect(visibleRect)) {
                dirtyLeaves.remove(i);
            }
        }
        final String screen = screenName(currentScreen);
        synchronized (redrawn) {
            checkedFrames++;
            if (dirtyLeaves.isEmpty()) {
                cleanFrames++;
                return;
            }
            for (View view : dirtyLeaves) {
                increment(redrawn, screen + "  " + describeView(view));
            }
        }
        dirtyLeaves.clear();
    }

    private static void collectDirty(View view, ArrayList<View> out) {
        if (!view.isDirty() || view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            final int before = out.size();
            for (int i = 0, count = group.getChildCount(); i < count; i++) {
                collectDirty(group.getChildAt(i), out);
            }
            if (out.size() > before) {
                return;
            }
        }
        out.add(view);
    }

    private static String describeView(View view) {
        final StringBuilder name = new StringBuilder(view.getClass().getName());
        if (view.getParent() != null) {
            name.append(" in ").append(view.getParent().getClass().getName());
        }
        float alpha = 1f;
        for (Object node = view; node instanceof View; node = ((View) node).getParent()) {
            alpha *= ((View) node).getAlpha();
        }
        if (alpha <= 0f) {
            name.append(" [alpha 0]");
        }
        return name.toString();
    }

    private final class FrameTracker implements Window.OnFrameMetricsAvailableListener {
        @Override
        public void onFrameMetricsAvailable(Window window, FrameMetrics metrics, int dropCount) {
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) {
                return;
            }
            final long total = metrics.getMetric(FrameMetrics.TOTAL_DURATION);
            long deadline = Build.VERSION.SDK_INT >= 31 ? metrics.getMetric(FrameMetrics.DEADLINE) : 0;
            if (deadline <= 0) {
                deadline = defaultFrameNs;
            }
            recordFrame(total, deadline);
        }
    }

    private void recordFrame(long totalNs, long deadlineNs) {
        final String screen = screenName(currentScreen);
        synchronized (frameLock) {
            FrameStats byScreen = frameByScreen.get(screen);
            if (byScreen == null) {
                byScreen = new FrameStats();
                frameByScreen.put(screen, byScreen);
            }
            countFrame(frameTotals, totalNs, deadlineNs);
            countFrame(byScreen, totalNs, deadlineNs);
        }
    }

    private static void countFrame(FrameStats stats, long totalNs, long deadlineNs) {
        stats.frames++;
        if (totalNs > deadlineNs) {
            stats.janky++;
        }
        if (totalNs > 32_000_000L) {
            stats.over32++;
        }
        if (totalNs > 50_000_000L) {
            stats.over50++;
        }
        if (totalNs > 100_000_000L) {
            stats.over100++;
        }
        if (totalNs > 700_000_000L) {
            stats.over700++;
        }
    }

    private void onLooperMessage(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        final char kind = line.charAt(0);
        if (kind == '>') {
            dispatchName = line;
            dispatchSeq = dispatchSeq + 1;
            dispatchStart = System.nanoTime();
        } else if (kind == '<') {
            final long start = dispatchStart;
            dispatchStart = 0;
            if (start == 0) {
                return;
            }
            final long duration = System.nanoTime() - start;
            final BaseFragment fragment = LaunchActivity.getLastFragmentIncludeMainTabs();
            final Class<?> screen = fragment != null ? fragment.getClass() : null;
            currentScreen = screen;
            messageCount = messageCount + 1;
            busyNs = busyNs + duration;
            if (duration >= SLOW_NS) {
                finishedMessages.add(new SlowMessage(dispatchSeq, duration, dispatchName, screen));
            }
        }
    }

    private void sample() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
        long lastAutosave = SystemClock.elapsedRealtime();
        while (running) {
            final long start = dispatchStart;
            if (start != 0 && System.nanoTime() - start >= TRIGGER_NS) {
                final long seq = dispatchSeq;
                final StackTraceElement[] stack = mainThread.getStackTrace();
                if (dispatchStart == start && dispatchSeq == seq && stack.length > 0) {
                    pendingSamples.add(new Sample(seq, stack));
                }
            }
            drain();
            final long now = SystemClock.elapsedRealtime();
            if (now - lastAutosave >= AUTOSAVE_INTERVAL_MS) {
                lastAutosave = now;
                writeReport();
            }
            try {
                Thread.sleep(SAMPLE_INTERVAL_MS);
            } catch (InterruptedException ignore) {
            }
        }
        drain();
        final File file = writeReport();
        final Utilities.Callback<File> callback = onStopped;
        if (callback != null) {
            AndroidUtilities.runOnUIThread(() -> callback.run(file));
        }
    }

    private void drain() {
        final long seqNow = dispatchSeq;
        SlowMessage message;
        while ((message = finishedMessages.poll()) != null) {
            aggregate(message);
        }
        final Iterator<Sample> iterator = pendingSamples.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().seq < seqNow) {
                iterator.remove();
            }
        }
    }

    private void aggregate(SlowMessage message) {
        final ArrayList<Sample> samples = new ArrayList<>();
        final Iterator<Sample> iterator = pendingSamples.iterator();
        while (iterator.hasNext()) {
            final Sample sample = iterator.next();
            if (sample.seq == message.seq) {
                samples.add(sample);
                iterator.remove();
            }
        }
        final String screen = screenName(message.screen);
        slowCount++;
        slowTotalNs += message.durationNs;
        SlowStats stats = slowByScreen.get(screen);
        if (stats == null) {
            stats = new SlowStats();
            slowByScreen.put(screen, stats);
        }
        stats.count++;
        stats.ns += message.durationNs;

        final HashMap<String, Integer> signatures = new HashMap<>();
        final HashMap<String, Sample> representatives = new HashMap<>();
        final HashSet<String> seen = new HashSet<>();
        for (Sample sample : samples) {
            final StackTraceElement[] stack = sample.stack;
            sampleCount++;
            increment(selfAll, frameName(stack[0]));
            for (StackTraceElement element : stack) {
                if (isAppFrame(element)) {
                    increment(selfApp, frameName(element));
                    break;
                }
            }
            seen.clear();
            for (StackTraceElement element : stack) {
                if (isAppFrame(element)) {
                    final String name = frameName(element);
                    if (seen.add(name)) {
                        increment(inclusiveApp, name);
                    }
                }
            }
            final String foldedStack = foldedStack(stack);
            if (folded.containsKey(foldedStack) || folded.size() < MAX_FOLDED) {
                increment(folded, foldedStack);
            } else {
                foldedOverflow++;
            }
            final String signature = signature(stack);
            increment(signatures, signature);
            if (!representatives.containsKey(signature)) {
                representatives.put(signature, sample);
            }
        }

        String dominant = null;
        int dominantCount = 0;
        for (Map.Entry<String, Integer> entry : signatures.entrySet()) {
            if (entry.getValue() > dominantCount) {
                dominantCount = entry.getValue();
                dominant = entry.getKey();
            }
        }
        final String stackText = dominant != null ? stackText(representatives.get(dominant).stack, dominantCount, samples.size()) : "";
        if (slowest.size() < MAX_SLOWEST || slowest.peek().durationNs < message.durationNs) {
            slowest.add(new SlowEntry(message.durationNs, screen, dispatchTarget(message.name), samples.size(), stackText));
            if (slowest.size() > MAX_SLOWEST) {
                slowest.poll();
            }
        }
    }

    private static void increment(HashMap<String, Integer> map, String key) {
        final Integer value = map.get(key);
        map.put(key, value == null ? 1 : value + 1);
    }

    private static boolean isAppFrame(StackTraceElement element) {
        final String className = element.getClassName();
        for (String prefix : APP_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String frameName(StackTraceElement element) {
        return element.getClassName() + "." + element.getMethodName();
    }

    private static String foldedStack(StackTraceElement[] stack) {
        final int depth = Math.min(stack.length, FOLDED_DEPTH);
        final StringBuilder builder = new StringBuilder();
        if (stack.length > depth) {
            builder.append("...");
        }
        for (int i = depth - 1; i >= 0; i--) {
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(frameName(stack[i]));
        }
        return builder.toString();
    }

    private static String signature(StackTraceElement[] stack) {
        final StringBuilder builder = new StringBuilder();
        final int depth = Math.min(stack.length, SIGNATURE_DEPTH);
        for (int i = 0; i < depth; i++) {
            builder.append(frameName(stack[i])).append('|');
        }
        return builder.toString();
    }

    private static String stackText(StackTraceElement[] stack, int matching, int total) {
        final StringBuilder builder = new StringBuilder();
        builder.append("   stack in ").append(matching).append(" of ").append(total).append(" samples:\n");
        final int depth = Math.min(stack.length, STACK_REPORT_DEPTH);
        for (int i = 0; i < depth; i++) {
            builder.append("     at ").append(stack[i]).append('\n');
        }
        if (stack.length > depth) {
            builder.append("     ... ").append(stack.length - depth).append(" more\n");
        }
        return builder.toString();
    }

    private static String dispatchTarget(String line) {
        if (line == null) {
            return "?";
        }
        final String prefix = ">>>>> Dispatching to ";
        return line.startsWith(prefix) ? line.substring(prefix.length()) : line;
    }

    private static String screenName(Class<?> screen) {
        return screen != null ? screen.getSimpleName() : "-";
    }

    private File writeReport() {
        final StringBuilder out = new StringBuilder(64 * 1024);
        final long sessionMs = SystemClock.elapsedRealtime() - startedAt;
        out.append("exteraless jank profile\n");
        out.append("app: ").append(BuildVars.BUILD_VERSION_STRING).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        out.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")")
                .append(", refresh ").append(Math.round(refreshRate)).append(" Hz\n");
        out.append("session: ").append(formatDuration(sessionMs)).append(running ? " (running)" : "").append('\n');
        out.append("sampling: trigger ").append(TRIGGER_NS / 1_000_000).append(" ms, interval ").append(SAMPLE_INTERVAL_MS)
                .append(" ms, slow message >= ").append(SLOW_NS / 1_000_000).append(" ms\n");
        out.append("debug logs: ").append(BuildVars.LOGS_ENABLED ? "on" : "off")
                .append(", liquid glass: ").append(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? "on" : "off")
                .append(", chat blur: ").append(LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR) ? "on" : "off").append('\n');
        appendPlugins(out);

        final long messages = messageCount;
        final long busy = busyNs;
        out.append("\n== main thread ==\n");
        out.append("messages: ").append(messages).append(", busy ").append(busy / 1_000_000).append(" ms");
        if (sessionMs > 0) {
            out.append(" (").append(String.format(Locale.US, "%.1f", busy / 1e6 * 100 / sessionMs)).append("% of session)");
        }
        out.append('\n');
        out.append("slow messages: ").append(slowCount).append(", total ").append(slowTotalNs / 1_000_000).append(" ms, samples ").append(sampleCount).append('\n');

        synchronized (frameLock) {
            out.append("\n== frames (activity windows) ==\n");
            appendFrameLine(out, "all", frameTotals);
            final ArrayList<Map.Entry<String, FrameStats>> screens = new ArrayList<>(frameByScreen.entrySet());
            screens.sort((a, b) -> Long.compare(b.getValue().janky, a.getValue().janky));
            for (Map.Entry<String, FrameStats> entry : screens) {
                appendFrameLine(out, entry.getKey(), entry.getValue());
            }
        }

        synchronized (redrawn) {
            out.append("\n== redrawn views (dirty before draw) ==\n");
            out.append("frames checked ").append(checkedFrames).append(", without dirty views ").append(cleanFrames).append('\n');
            for (Map.Entry<String, Integer> entry : sorted(redrawn, REPORT_TOP)) {
                out.append(String.format(Locale.US, "%7d  ", entry.getValue())).append(entry.getKey()).append('\n');
            }
        }

        out.append("\n== slow messages by screen ==\n");
        final ArrayList<Map.Entry<String, SlowStats>> slowScreens = new ArrayList<>(slowByScreen.entrySet());
        slowScreens.sort((a, b) -> Long.compare(b.getValue().ns, a.getValue().ns));
        for (Map.Entry<String, SlowStats> entry : slowScreens) {
            out.append("  ").append(entry.getKey()).append(": ").append(entry.getValue().count)
                    .append(" messages, ").append(entry.getValue().ns / 1_000_000).append(" ms\n");
        }

        try {
            HookStats.appendReport(out);
        } catch (Throwable t) {
            FileLog.e(t);
        }

        appendTop(out, "top self frames (all code)", selfAll);
        appendTop(out, "top self frames (app code)", selfApp);
        appendTop(out, "top inclusive frames (app code)", inclusiveApp);

        out.append("\n== slowest messages ==\n");
        final ArrayList<SlowEntry> entries = new ArrayList<>(slowest);
        entries.sort((a, b) -> Long.compare(b.durationNs, a.durationNs));
        int index = 1;
        for (SlowEntry entry : entries) {
            out.append('#').append(index++).append(' ').append(entry.durationNs / 1_000_000).append(" ms, screen ")
                    .append(entry.screen).append(", samples ").append(entry.samples).append('\n');
            out.append("   ").append(entry.name).append('\n');
            out.append(entry.stack);
        }

        out.append("\n== folded stacks (root;...;leaf samples) ==\n");
        if (foldedOverflow > 0) {
            out.append("# ").append(foldedOverflow).append(" samples dropped after ").append(MAX_FOLDED).append(" unique stacks\n");
        }
        final List<Map.Entry<String, Integer>> foldedEntries = sorted(folded, REPORT_FOLDED);
        for (Map.Entry<String, Integer> entry : foldedEntries) {
            out.append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(reportFile), StandardCharsets.UTF_8)) {
            writer.write(out.toString());
        } catch (Exception e) {
            FileLog.e(e);
        }
        return reportFile;
    }

    private void appendPlugins(StringBuilder out) {
        try {
            final StringBuilder enabled = new StringBuilder();
            for (Plugin plugin : PluginsController.getInstance().getPluginsSnapshot()) {
                if (plugin.isEnabled()) {
                    if (enabled.length() > 0) {
                        enabled.append(", ");
                    }
                    enabled.append(plugin.getId());
                }
            }
            out.append("enabled plugins: ").append(enabled.length() > 0 ? enabled : "-").append('\n');
        } catch (Throwable t) {
            out.append("enabled plugins: ?\n");
        }
    }

    private static void appendFrameLine(StringBuilder out, String name, FrameStats stats) {
        out.append("  ").append(name).append(": frames ").append(stats.frames)
                .append(", janky ").append(stats.janky);
        if (stats.frames > 0) {
            out.append(" (").append(String.format(Locale.US, "%.1f", stats.janky * 100.0 / stats.frames)).append("%)");
        }
        out.append(", >32ms ").append(stats.over32)
                .append(", >50ms ").append(stats.over50)
                .append(", >100ms ").append(stats.over100)
                .append(", >700ms ").append(stats.over700).append('\n');
    }

    private void appendTop(StringBuilder out, String title, HashMap<String, Integer> counts) {
        out.append("\n== ").append(title).append(" ==\n");
        for (Map.Entry<String, Integer> entry : sorted(counts, REPORT_TOP)) {
            out.append(String.format(Locale.US, "%7d  ~%6d ms  ", entry.getValue(), entry.getValue() * SAMPLE_INTERVAL_MS)).append(entry.getKey()).append('\n');
        }
    }

    private static List<Map.Entry<String, Integer>> sorted(HashMap<String, Integer> counts, int limit) {
        final ArrayList<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    private static String formatDuration(long ms) {
        final long seconds = ms / 1000;
        return String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, (seconds / 60) % 60, seconds % 60);
    }
}
