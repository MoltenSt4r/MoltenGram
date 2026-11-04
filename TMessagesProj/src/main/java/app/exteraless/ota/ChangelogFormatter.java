package app.exteraless.ota;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Превращает markdown-тело GitHub-релиза в читаемый Spannable для OTA-экрана, диалога и уведомления.
 * Поддерживает то, что реально встречается в changelog: заголовки, списки, цитаты, разделители,
 * **жирный**, *курсив*, ~~зачёркнутый~~, `код` и [ссылки](url). Остальная разметка вычищается.
 */
public final class ChangelogFormatter {

    private ChangelogFormatter() {
    }

    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern ORDERED = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^\\s{0,3}>\\s?(.*)$");
    private static final Pattern RULE = Pattern.compile("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(```|~~~).*$");
    private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern HTML_TAG = Pattern.compile("</?[a-zA-Z][^>]*>");

    // Порядок важен: код раньше остального, жирный раньше курсива.
    private static final Pattern INLINE = Pattern.compile(
            "`([^`]+)`"                                   // 1 code
                    + "|\\*\\*(.+?)\\*\\*"                // 2 bold
                    + "|__(.+?)__"                        // 3 bold
                    + "|~~(.+?)~~"                        // 4 strike
                    + "|\\[([^\\]]+)]\\(([^)\\s]+)[^)]*\\)" // 5,6 link
                    + "|(?<![\\w*])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![\\w*])" // 7 italic
    );

    /** Отформатированный текст со стилями. */
    public static CharSequence format(String markdown) {
        if (TextUtils.isEmpty(markdown)) {
            return "";
        }
        String src = HTML_BREAK.matcher(markdown.replace("\r\n", "\n").replace('\r', '\n')).replaceAll("\n");
        src = HTML_TAG.matcher(src).replaceAll("");

        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = src.split("\n", -1);
        boolean inFence = false;
        boolean pendingBlank = false;

        for (String raw : lines) {
            if (FENCE.matcher(raw).matches()) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                appendLine(out, pendingBlank);
                pendingBlank = false;
                int start = out.length();
                out.append(raw);
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                continue;
            }
            String line = protectEscapes(raw);
            if (line.trim().isEmpty() || RULE.matcher(line).matches()) {
                if (out.length() > 0) {
                    pendingBlank = true;
                }
                continue;
            }

            Matcher m;
            if ((m = HEADING.matcher(line)).matches()) {
                // заголовок всегда отделяем пустой строкой от предыдущего блока
                appendLine(out, out.length() > 0);
                pendingBlank = false;
                int start = out.length();
                appendInline(out, m.group(2));
                int level = m.group(1).length();
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (level <= 3) {
                    out.setSpan(new RelativeSizeSpan(level == 1 ? 1.2f : level == 2 ? 1.12f : 1.06f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                continue;
            }

            appendLine(out, pendingBlank);
            pendingBlank = false;

            if ((m = BULLET.matcher(line)).matches()) {
                out.append(indent(m.group(1))).append("•  ");
                appendInline(out, m.group(2));
            } else if ((m = ORDERED.matcher(line)).matches()) {
                out.append(indent(m.group(1))).append(m.group(2)).append(".  ");
                appendInline(out, m.group(3));
            } else if ((m = QUOTE.matcher(line)).matches()) {
                int start = out.length();
                appendInline(out, m.group(1));
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                appendInline(out, line.trim());
            }
        }
        return out;
    }

    /** Тот же текст без стилей — для уведомлений и однострочных подписей. */
    public static String toPlain(String markdown) {
        return format(markdown).toString();
    }

    private static void appendLine(SpannableStringBuilder out, boolean blank) {
        if (out.length() == 0) {
            return;
        }
        out.append('\n');
        if (blank) {
            out.append('\n');
        }
    }

    private static String indent(String ws) {
        int depth = ws == null ? 0 : ws.replace("\t", "    ").length() / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(depth, 4); i++) {
            sb.append("    ");
        }
        return sb.toString();
    }

    private static void appendInline(SpannableStringBuilder out, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        Matcher m = INLINE.matcher(text);
        int last = 0;
        while (m.find()) {
            out.append(unescape(text.substring(last, m.start())));
            int start = out.length();
            if (m.group(1) != null) {
                out.append(restore(m.group(1)));
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(2) != null || m.group(3) != null) {
                appendInline(out, m.group(2) != null ? m.group(2) : m.group(3));
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(4) != null) {
                appendInline(out, m.group(4));
                out.setSpan(new StrikethroughSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(5) != null) {
                appendInline(out, m.group(5));
                out.setSpan(new URLSpan(m.group(6)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(7) != null) {
                appendInline(out, m.group(7));
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        out.append(unescape(text.substring(last)));
    }

    private static final char ESCAPE_BASE = '\uE000';

    /** \* \_ \# и т.п. прячем в private-use символы, чтобы инлайн-разметка их не видела. */
    private static String protectEscapes(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && "\\`*_{}[]()#+-.!>~|".indexOf(s.charAt(i + 1)) >= 0) {
                sb.append((char) (ESCAPE_BASE + s.charAt(++i)));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String restore(String s) {
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= ESCAPE_BASE && c < ESCAPE_BASE + 128) {
                if (sb == null) {
                    sb = new StringBuilder(s.substring(0, i));
                }
                sb.append((char) (c - ESCAPE_BASE));
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
    }

    private static String unescape(String s) {
        return restore(s);
    }
}
