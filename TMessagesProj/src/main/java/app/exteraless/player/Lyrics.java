package app.exteraless.player;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lyrics {

    public static final int SOURCE_FILE = 0;
    public static final int SOURCE_ONLINE = 1;

    public static final class Line {
        public final long time;
        public final String text;
        public final boolean isBacking;
        public final String role;
        public String romanized;
        public String translation;

        Line(long time, String text) {
            this(time, text, false, null);
        }

        Line(long time, String text, boolean isBacking, String role) {
            this.time = time;
            this.text = text;
            this.isBacking = isBacking;
            this.role = role;
        }
    }

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern META = Pattern.compile("^\\[[a-zA-Z#]+:.*]$");

    public final ArrayList<Line> lines;
    public final boolean synced;
    public final boolean instrumental;
    public final int source;
    public final String provider;
    public boolean isTranslated = false;
    public boolean showTranslation = true;

    private Lyrics(ArrayList<Line> lines, boolean synced, boolean instrumental, int source, String provider) {
        this.lines = lines;
        this.synced = synced;
        this.instrumental = instrumental;
        this.source = source;
        this.provider = provider;
    }

    public static Lyrics instrumental(int source, String provider) {
        return new Lyrics(new ArrayList<>(), false, true, source, provider);
    }

    static Lyrics synced(ArrayList<Line> lines, int source, String provider) {
        return new Lyrics(lines, true, false, source, provider);
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Line line : lines) {
            if (synced) {
                long t = Math.max(0, line.time);
                sb.append(String.format(Locale.US, "[%02d:%02d.%02d]", t / 60000, t / 1000 % 60, t % 1000 / 10));
            }
            if (line.role != null) {
                sb.append("{agent:").append(line.role).append("}");
            }
            if (line.isBacking) {
                sb.append("{bg}");
            }
            sb.append(line.text).append('\n');
        }
        return sb.toString();
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public static Lyrics parse(String text, int source) {
        return parse(text, source, null);
    }

    public static Lyrics parse(String text, int source, String provider) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = normalized.split("\n");
        long offset = 0;
        ArrayList<Line> timed = new ArrayList<>();
        for (String row : raw) {
            String line = row.trim();
            Matcher off = OFFSET.matcher(line);
            if (off.find()) {
                try {
                    offset = Long.parseLong(off.group(1));
                } catch (NumberFormatException ignore) {
                }
                continue;
            }
            Matcher m = TIME.matcher(line);
            ArrayList<Long> times = null;
            int end = 0;
            while (m.find() && m.start() == end) {
                if (times == null) {
                    times = new ArrayList<>();
                }
                times.add(toMillis(m.group(1), m.group(2), m.group(3)));
                end = m.end();
            }
            if (times == null) {
                continue;
            }
            String rawContent = WORD_TIME.matcher(line.substring(end)).replaceAll("").trim();
            boolean isBacking = false;
            String role = null;
            boolean matchedTag = true;
            while (matchedTag) {
                matchedTag = false;
                if (rawContent.startsWith("{bg}")) {
                    isBacking = true;
                    rawContent = rawContent.substring(4).trim();
                    matchedTag = true;
                } else if (rawContent.startsWith("[bg:") && rawContent.endsWith("]")) {
                    isBacking = true;
                    rawContent = rawContent.substring(4, rawContent.length() - 1).trim();
                    matchedTag = true;
                } else if (rawContent.matches("^\\{agent:[^}]+\\}.*")) {
                    int brace = rawContent.indexOf('}');
                    role = rawContent.substring(7, brace);
                    rawContent = rawContent.substring(brace + 1).trim();
                    matchedTag = true;
                } else if (rawContent.matches("^v\\d+:.*")) {
                    int col = rawContent.indexOf(':');
                    role = rawContent.substring(0, col);
                    rawContent = rawContent.substring(col + 1).trim();
                    matchedTag = true;
                }
            }
            if (rawContent.startsWith("(") && rawContent.endsWith(")") && rawContent.length() > 2) {
                // Bracketed backing vocals
                isBacking = true;
            }
            for (long t : times) {
                timed.add(new Line(Math.max(0, t - offset), rawContent, isBacking, role));
            }
        }
        if (!timed.isEmpty()) {
            Collections.sort(timed, (a, b) -> Long.compare(a.time, b.time));
            ArrayList<Line> out = new ArrayList<>(timed.size());
            for (int i = 0; i < timed.size(); i++) {
                Line l = timed.get(i);
                boolean blank = l.text.isEmpty();
                if (blank && (out.isEmpty() || out.get(out.size() - 1).text.isEmpty() || i == timed.size() - 1)) {
                    continue;
                }
                out.add(l);
            }
            return out.isEmpty() ? null : new Lyrics(out, true, false, source, provider);
        }
        ArrayList<Line> plain = new ArrayList<>();
        for (String row : raw) {
            String line = row.trim();
            if (META.matcher(line).matches()) {
                continue;
            }
            if (line.isEmpty() && (plain.isEmpty() || plain.get(plain.size() - 1).text.isEmpty())) {
                continue;
            }
            plain.add(new Line(-1, line));
        }
        while (!plain.isEmpty() && plain.get(plain.size() - 1).text.isEmpty()) {
            plain.remove(plain.size() - 1);
        }
        return plain.isEmpty() ? null : new Lyrics(plain, false, false, source, provider);
    }

    private static long toMillis(String min, String sec, String frac) {
        long ms = Long.parseLong(min) * 60_000L + Long.parseLong(sec) * 1000L;
        if (frac != null) {
            int f = Integer.parseInt(frac);
            if (frac.length() == 1) {
                ms += f * 100L;
            } else if (frac.length() == 2) {
                ms += f * 10L;
            } else {
                ms += f;
            }
        }
        return ms;
    }

    public int indexAt(long ms) {
        if (!synced) {
            return -1;
        }
        int lo = 0;
        int hi = lines.size() - 1;
        int found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).time <= ms) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }
}
