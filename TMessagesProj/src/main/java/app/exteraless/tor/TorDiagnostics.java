package app.exteraless.tor;

import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Диагностический модуль Tor для ExteraMS.
 * Собирает лог событий подключения (статусы, этапы bootstrap, транзиты, ошибки)
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

    private static final int MAX_ENTRIES = 250;

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

    private final LinkedList<DiagnosticEntry> entries = new LinkedList<>();

    private TorDiagnostics() {
        logEvent("INIT", "Tor diagnostic logger initialized (IP masking active)");
    }

    public synchronized void logEvent(String category, String rawMessage) {
        if (rawMessage == null) return;
        String sanitized = sanitize(rawMessage);
        entries.addLast(new DiagnosticEntry(category, sanitized));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeFirst();
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
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append("=== MoltenGram Tor Diagnostic Report ===\n");
        sb.append("Generated at: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append("\n");
        sb.append("Privacy constraint: NO IP addresses collected or retained.\n");
        sb.append("----------------------------------------\n\n");

        if (entries.isEmpty()) {
            sb.append("No diagnostic events recorded yet.\n");
        } else {
            for (DiagnosticEntry entry : entries) {
                sb.append(entry.format(sdf)).append("\n");
            }
        }
        sb.append("\n=== End of Diagnostic Report ===");
        return sb.toString();
    }

    public synchronized void clear() {
        entries.clear();
        logEvent("INIT", "Diagnostic log cleared (IP masking active)");
    }
}
