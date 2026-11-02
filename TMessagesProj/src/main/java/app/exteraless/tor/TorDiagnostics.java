package app.exteraless.tor;

import android.content.Context;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Диагностический модуль Tor для ExteraMS.
 * Сохраняет лог событий подключения (статусы, этапы bootstrap, транзиты, ошибки)
 * на диск в файл tor_diagnostics.txt (сохраняется даже при сбоях/перезапусках приложения)
 * со СТРОГОЙ фильтрацией и маскированием любых IP-адресов.
 * Никакие реальные IP (клиента, мостов или узлов) не сохраняются.
 */
public class TorDiagnostics {

    private static volatile TorDiagnostics instance;

    public static TorDiagnostics getInstance() {
        if (instance == null) {
            synchronized (TorDiagnostics.class) {
                if (instance == null) {
                    instance = new TorDiagnostics();
                }
            }
        }
        return instance;
    }

    private static final String LOG_FILE = "tor_diagnostics.txt";
    private static final int MAX_FILE_SIZE = 256 * 1024; // 256 KB
    private static final int MAX_IN_MEMORY = 200;

    // Регулярные выражения для деперсонализации
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?::\\d{1,5})?\\b"
    );
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "\\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{1,4}(?::\\d{1,5})?\\b"
    );
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{40}\\b"
    );

    public static class DiagnosticEntry {
        public final long timestamp;
        public final String category;
        public final String message;

        public DiagnosticEntry(String category, String message) {
            this.timestamp = System.currentTimeMillis();
            this.category = category;
            this.message = message;
        }

        public String format(SimpleDateFormat sdf) {
            return String.format(Locale.US, "[%s] [%s] %s", sdf.format(new Date(timestamp)), category, message);
        }
    }

    private final LinkedList<DiagnosticEntry> inMemoryEntries = new LinkedList<>();

    private TorDiagnostics() {
        logEvent("INIT", "Tor diagnostic logger ready (disk persistence & IP masking active)");
    }

    private File getLogFile() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) return null;
        return new File(context.getFilesDir(), LOG_FILE);
    }

    public synchronized void logEvent(String category, String rawMessage) {
        if (rawMessage == null) return;
        String sanitized = sanitize(rawMessage);
        DiagnosticEntry entry = new DiagnosticEntry(category, sanitized);

        inMemoryEntries.addLast(entry);
        while (inMemoryEntries.size() > MAX_IN_MEMORY) {
            inMemoryEntries.removeFirst();
        }

        writeToFile(entry);
    }

    public synchronized void logThrowable(String category, Throwable t) {
        if (t == null) return;
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            logEvent(category, t.getClass().getSimpleName() + ": " + t.getMessage() + "\n" + sw.toString());
        } catch (Throwable ignored) {
            logEvent(category, t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private void writeToFile(DiagnosticEntry entry) {
        File file = getLogFile();
        if (file == null) return;

        try {
            if (file.exists() && file.length() > MAX_FILE_SIZE) {
                // Truncate older entries if log gets too large
                file.delete();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
            String line = entry.format(sdf) + "\n";

            try (FileOutputStream fos = new FileOutputStream(file, true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                osw.write(line);
                osw.flush();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static String sanitize(String input) {
        if (TextUtils.isEmpty(input)) return "";
        String res = IPV4_PATTERN.matcher(input).replaceAll("[REDACTED_IPV4]");
        res = IPV6_PATTERN.matcher(res).replaceAll("[REDACTED_IPV6]");
        res = FINGERPRINT_PATTERN.matcher(res).replaceAll("[REDACTED_FP]");
        return res;
    }

    public synchronized String getSanitizedLogReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MoltenGram Tor Diagnostic Report ===\n");
        sb.append("Generated at: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("\n");
        sb.append("Privacy constraint: NO IP addresses collected or retained.\n");
        sb.append("----------------------------------------\n\n");

        File file = getLogFile();
        boolean hasDiskContent = false;
        if (file != null && file.exists() && file.length() > 0) {
            try (FileInputStream fis = new FileInputStream(file);
                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                    hasDiskContent = true;
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }

        if (!hasDiskContent) {
            if (inMemoryEntries.isEmpty()) {
                sb.append("No diagnostic events recorded yet.\n");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
                for (DiagnosticEntry entry : inMemoryEntries) {
                    sb.append(entry.format(sdf)).append("\n");
                }
            }
        }

        sb.append("\n=== End of Diagnostic Report ===");
        return sb.toString();
    }

    public synchronized void clear() {
        inMemoryEntries.clear();
        File file = getLogFile();
        if (file != null && file.exists()) {
            file.delete();
        }
        logEvent("INIT", "Diagnostic log cleared (IP masking active)");
    }
}
