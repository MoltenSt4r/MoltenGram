package app.exteraless.plugins;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class PluginLog {

    private static final long MAX_BYTES = 1024 * 1024;
    private static final String DIR = "plugin_logs";
    private static final String CURRENT = "plugins.log";
    private static final String PREVIOUS = "plugins.1.log";

    private static final DispatchQueue queue = new DispatchQueue("pluginLogQueue");
    private static final SimpleDateFormat lineTime = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);
    private static final SimpleDateFormat fileTime = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss", Locale.US);

    private PluginLog() {
    }

    public static void append(String pluginId, String level, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        final long time = System.currentTimeMillis();
        final String thread = Thread.currentThread().getName();
        queue.postRunnable(() -> write(time, thread, pluginId, level, message));
    }

    private static File dir() {
        return ApplicationLoader.getFilesDirFixed(DIR);
    }

    private static void write(long time, String thread, String pluginId, String level, String message) {
        File dir = dir();
        if (dir == null) {
            return;
        }
        File current = new File(dir, CURRENT);
        if (current.length() > MAX_BYTES) {
            File previous = new File(dir, PREVIOUS);
            previous.delete();
            current.renameTo(previous);
            current = new File(dir, CURRENT);
        }
        String prefix = lineTime.format(new Date(time)) + " " + (level == null ? "I" : level) + "/"
                + (pluginId == null || pluginId.isEmpty() ? "engine" : pluginId) + " [" + thread + "] ";
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(current, true), StandardCharsets.UTF_8)) {
            String[] lines = message.split("\n", -1);
            int last = lines.length;
            while (last > 0 && lines[last - 1].trim().isEmpty()) {
                last--;
            }
            for (int i = 0; i < last; i++) {
                writer.write(prefix);
                writer.write(lines[i]);
                writer.write('\n');
            }
        } catch (IOException e) {
            FileLog.e(e);
        }
    }

    public static File[] files() {
        File dir = dir();
        if (dir == null) {
            return new File[0];
        }
        File previous = new File(dir, PREVIOUS);
        File current = new File(dir, CURRENT);
        if (previous.exists() && current.exists()) {
            return new File[]{previous, current};
        }
        if (current.exists()) {
            return new File[]{current};
        }
        if (previous.exists()) {
            return new File[]{previous};
        }
        return new File[0];
    }

    public static File export() {
        drain();
        File target = AndroidUtilities.getLogsDir();
        if (target == null) {
            return null;
        }
        File out = new File(target, "plugins-" + fileTime.format(new Date()) + ".log");
        try (OutputStream stream = new FileOutputStream(out)) {
            String header = "exteraless " + BuildConfig.BUILD_VERSION_STRING
                    + (BuildConfig.BUILD_COMMIT_ID.isEmpty() ? "" : " (" + BuildConfig.BUILD_COMMIT_ID + ")")
                    + ", Android " + android.os.Build.VERSION.RELEASE
                    + ", " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n\n";
            stream.write(header.getBytes(StandardCharsets.UTF_8));
            byte[] buffer = new byte[64 * 1024];
            for (File file : files()) {
                try (InputStream in = new FileInputStream(file)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        stream.write(buffer, 0, read);
                    }
                }
            }
        } catch (IOException e) {
            FileLog.e(e);
            return null;
        }
        return out;
    }

    public static void clear() {
        queue.postRunnable(() -> {
            for (File file : files()) {
                file.delete();
            }
        });
    }

    private static void drain() {
        CountDownLatch latch = new CountDownLatch(1);
        queue.postRunnable(latch::countDown);
        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
    }
}
