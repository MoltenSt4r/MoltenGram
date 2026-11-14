package app.exteraless.chats;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Editable;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.List;

/**
 * Расширенное форматирование сообщений (таблицы, медиа-карточки, заголовки, маркеры, разделители, индексы, чек-листы).
 * Кодирует метаданные в {@code tg://fmt?...} на невидимом символе {@code \u200B}, оставляя читаемый текст для обычных клиентов Telegram.
 */
public class ExtendedFormattingHelper {

    public static final String PREFIX = "tg://fmt";

    public static boolean isExtendedFmtUrl(String url) {
        return url != null && url.startsWith(PREFIX);
    }

    public static void applyFormattingSpan(Spannable spannable, String url, int offset, TextStyleSpan.TextStyleRun run) {
        if (!app.exteraless.OpenExteraConfig.extendedFormatting()) {
            return;
        }
        if (spannable == null || url == null) {
            return;
        }
        try {
            Uri uri = Uri.parse(url);
            String type = uri.getQueryParameter("t");
            if (TextUtils.isEmpty(type)) {
                return;
            }
            int len = parseLen(uri);
            int spanStart = offset;
            if (offset >= 0 && offset < spannable.length() && spannable.charAt(offset) == '\u200B') {
                spanStart = offset + 1;
            }
            int spanEnd = resolveSpanEnd(spannable, spanStart, len);
            if (spanStart >= spanEnd || spanStart >= spannable.length()) {
                return;
            }

            switch (type) {
                case "table":
                    applyTableSpans(spannable, spanStart, spanEnd);
                    break;
                case "media":
                    applyMediaSpan(spannable, uri, spanStart, spanEnd, run);
                    break;
                case "h1":
                    spannable.setSpan(new RelativeSizeSpan(1.30f), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "h2":
                    spannable.setSpan(new RelativeSizeSpan(1.15f), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "hl": {
                    int color = parseHexColor(uri.getQueryParameter("c"), 0xFFFFD54F);
                    spannable.setSpan(new BackgroundColorSpan(ColorUtils.setAlphaComponent(color, 80)), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                }
                case "hr":
                    spannable.setSpan(new DividerSpan(), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "sup":
                    spannable.setSpan(new SuperscriptSpan(), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new RelativeSizeSpan(0.75f), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
                case "sub":
                    spannable.setSpan(new SubscriptSpan(), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    spannable.setSpan(new RelativeSizeSpan(0.75f), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    break;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static int parseLen(Uri uri) {
        String lenStr = uri.getQueryParameter("len");
        if (lenStr != null) {
            try {
                return Integer.parseInt(lenStr);
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private static int resolveSpanEnd(CharSequence text, int spanStart, int len) {
        if (len > 0) {
            return Math.min(text.length(), spanStart + len);
        }
        for (int i = spanStart; i < text.length(); i++) {
            if (text.charAt(i) == '\u200B') {
                return i;
            }
        }
        return text.length();
    }

    private static int parseHexColor(String hex, int fallback) {
        if (TextUtils.isEmpty(hex)) return fallback;
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            long val = Long.parseLong(hex, 16);
            return hex.length() <= 6 ? (0xFF000000 | (int) val) : (int) val;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static void applyMediaSpan(Spannable spannable, Uri uri, int spanStart, int spanEnd, TextStyleSpan.TextStyleRun run) {
        String kind = uri.getQueryParameter("k");
        if (TextUtils.isEmpty(kind)) kind = "img";
        String mediaUrl = uri.getQueryParameter("u");
        String caption = uri.getQueryParameter("cap");
        if (TextUtils.isEmpty(caption)) {
            caption = defaultMediaTitle(kind);
        }
        String host = "";
        if (!TextUtils.isEmpty(mediaUrl)) {
            try {
                String h = Uri.parse(mediaUrl).getHost();
                if (!TextUtils.isEmpty(h)) {
                    host = h.startsWith("www.") ? h.substring(4) : h;
                }
            } catch (Exception ignored) {}
            if (host.isEmpty()) {
                host = mediaUrl;
            }
            if (run != null) {
                spannable.setSpan(new URLSpanNoUnderline(mediaUrl, run), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                spannable.setSpan(new URLSpanNoUnderline(mediaUrl), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        spannable.setSpan(new InlineMediaSpan(kind, caption, host), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static String defaultMediaTitle(String kind) {
        switch (kind) {
            case "vid": return "Видео";
            case "gif": return "GIF-анимация";
            case "aud": return "Аудио";
            case "stk": return "Стикер";
            default: return "Фото";
        }
    }

    private static String mediaEmoji(String kind) {
        switch (kind) {
            case "vid": return "🎬";
            case "gif": return "🎞";
            case "aud": return "🎵";
            case "stk": return "✨";
            default: return "📷";
        }
    }

    private static int mediaBadgeColor(String kind) {
        switch (kind) {
            case "vid": return 0xFFE53935;
            case "gif": return 0xFF8E24AA;
            case "aud": return 0xFFFB8C00;
            case "stk": return 0xFFEC407A;
            default: return 0xFF1E88E5;
        }
    }

    // ==================== TABLE PARSING & SPANS ====================

    private static class LineSlice {
        final int start;
        final int end;
        final boolean isSeparator;
        final String[] cells;

        LineSlice(int start, int end, boolean isSeparator, String[] cells) {
            this.start = start;
            this.end = end;
            this.isSeparator = isSeparator;
            this.cells = cells;
        }
    }

    private static boolean isTableSeparatorLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return false;
        boolean hasDash = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '─' || c == '-' || c == '═') {
                hasDash = true;
            } else if (c != '┼' && c != '+' && c != '|' && c != ' ') {
                return false;
            }
        }
        return hasDash;
    }

    private static void applyTableSpans(Spannable spannable, int spanStart, int spanEnd) {
        String raw = spannable.subSequence(spanStart, spanEnd).toString();
        List<LineSlice> slices = new ArrayList<>();
        int maxCols = 1;
        int pos = 0;
        int len = raw.length();
        while (pos <= len) {
            int nl = raw.indexOf('\n', pos);
            int lineEnd = nl == -1 ? len : nl;
            String line = raw.substring(pos, lineEnd);
            if (!line.trim().isEmpty()) {
                boolean sep = isTableSeparatorLine(line);
                String[] cells = null;
                if (!sep) {
                    String[] split = line.split("\\|", -1);
                    cells = new String[split.length];
                    for (int i = 0; i < split.length; i++) {
                        cells[i] = split[i].trim();
                    }
                    if (cells.length > maxCols) {
                        maxCols = cells.length;
                    }
                }
                slices.add(new LineSlice(spanStart + pos, spanStart + lineEnd, sep, cells));
            }
            if (nl == -1) break;
            pos = nl + 1;
        }

        List<LineSlice> dataRows = new ArrayList<>();
        for (LineSlice s : slices) {
            if (!s.isSeparator) dataRows.add(s);
        }
        if (dataRows.isEmpty()) return;

        TextPaint measurePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        measurePaint.setTextSize(dp(13.5f));
        measurePaint.setTypeface(AndroidUtilities.bold());

        int[] colWidths = new int[maxCols];
        int hPad = dp(10);
        int minColW = dp(44);
        for (LineSlice row : dataRows) {
            for (int c = 0; c < row.cells.length; c++) {
                int w = (int) Math.ceil(measurePaint.measureText(row.cells[c])) + hPad * 2;
                if (w > colWidths[c]) colWidths[c] = w;
            }
        }
        int totalW = 0;
        for (int c = 0; c < maxCols; c++) {
            if (colWidths[c] < minColW) colWidths[c] = minColW;
            totalW += colWidths[c];
        }
        int maxAllowedW = Math.max(dp(160), AndroidUtilities.displaySize.x - dp(108));
        if (totalW > maxAllowedW && totalW > 0) {
            float scale = (float) maxAllowedW / totalW;
            totalW = 0;
            for (int c = 0; c < maxCols; c++) {
                colWidths[c] = Math.max(dp(36), (int) (colWidths[c] * scale));
                totalW += colWidths[c];
            }
        }

        for (LineSlice s : slices) {
            if (s.isSeparator && s.start < s.end) {
                spannable.setSpan(new ZeroHeightSpan(), s.start, s.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        for (int i = 0; i < dataRows.size(); i++) {
            LineSlice row = dataRows.get(i);
            if (row.start < row.end) {
                spannable.setSpan(
                        new TableRowSpan(i, dataRows.size(), colWidths, totalW, row.cells),
                        row.start,
                        row.end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }
    }

    private static class ZeroHeightSpan extends ReplacementSpan {
        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            if (fm != null) {
                fm.ascent = 0;
                fm.descent = 0;
                fm.top = 0;
                fm.bottom = 0;
            }
            return 0;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
        }
    }

    private static class TableRowSpan extends ReplacementSpan {
        private final int rowIndex;
        private final int totalRows;
        private final int[] colWidths;
        private final int totalWidth;
        private final String[] cells;
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final RectF rect = new RectF();

        TableRowSpan(int rowIndex, int totalRows, int[] colWidths, int totalWidth, String[] cells) {
            this.rowIndex = rowIndex;
            this.totalRows = totalRows;
            this.colWidths = colWidths;
            this.totalWidth = totalWidth;
            this.cells = cells;
        }

        private int rowHeight() {
            return dp(rowIndex == 0 ? 34 : 30);
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            int h = rowHeight();
            if (fm != null) {
                int extraTop = rowIndex == 0 ? dp(4) : 0;
                int extraBottom = rowIndex == totalRows - 1 ? dp(4) : 0;
                fm.ascent = -h + dp(6) - extraTop;
                fm.descent = dp(6) + extraBottom;
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return totalWidth;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            int h = rowHeight();
            float rowTop = y - h + dp(6);
            float rowBottom = rowTop + h;
            float r = dp(10);

            int baseTextColor = paint.getColor();
            int accentColor = Theme.getColor(Theme.key_chat_messageLinkIn);

            canvas.save();
            rect.set(x, rowTop, x + totalWidth, rowBottom);
            clipPath.reset();
            boolean isFirst = rowIndex == 0;
            boolean isLast = rowIndex == totalRows - 1;
            float[] radii = new float[]{
                    isFirst ? r : 0, isFirst ? r : 0,
                    isFirst ? r : 0, isFirst ? r : 0,
                    isLast ? r : 0, isLast ? r : 0,
                    isLast ? r : 0, isLast ? r : 0
            };
            clipPath.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.clipPath(clipPath);

            // Row background
            if (isFirst) {
                bgPaint.setColor(ColorUtils.setAlphaComponent(accentColor, 42));
                canvas.drawRect(rect, bgPaint);
            } else if (rowIndex % 2 == 1) {
                bgPaint.setColor(ColorUtils.setAlphaComponent(baseTextColor, 10));
                canvas.drawRect(rect, bgPaint);
            } else {
                bgPaint.setColor(ColorUtils.setAlphaComponent(baseTextColor, 20));
                canvas.drawRect(rect, bgPaint);
            }

            // Cell text and vertical dividers
            textPaint.set(paint);
            textPaint.setTextSize(dp(13));
            textPaint.setTypeface(isFirst ? AndroidUtilities.bold() : Typeface.DEFAULT);
            textPaint.setColor(baseTextColor);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(0.8f));
            borderPaint.setColor(ColorUtils.setAlphaComponent(baseTextColor, 45));

            float curX = x;
            int pad = dp(8);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = rowTop + (h - (fm.descent - fm.ascent)) / 2f - fm.ascent;

            for (int c = 0; c < colWidths.length; c++) {
                int cw = colWidths[c];
                String cell = c < cells.length ? cells[c] : "";
                if (!cell.isEmpty()) {
                    CharSequence ellipsized = TextUtils.ellipsize(cell, textPaint, Math.max(dp(12), cw - pad * 2), TextUtils.TruncateAt.END);
                    canvas.drawText(ellipsized, 0, ellipsized.length(), curX + pad, textY, textPaint);
                }
                if (c < colWidths.length - 1) {
                    float divX = curX + cw;
                    canvas.drawLine(divX, rowTop, divX, rowBottom, borderPaint);
                }
                curX += cw;
            }

            if (!isLast) {
                canvas.drawLine(x, rowBottom - dp(0.5f), x + totalWidth, rowBottom - dp(0.5f), borderPaint);
            }
            canvas.restore();

            // Outer border segments
            borderPaint.setColor(ColorUtils.setAlphaComponent(baseTextColor, 64));
            if (isFirst && isLast) {
                canvas.drawRoundRect(rect, r, r, borderPaint);
            } else if (isFirst) {
                canvas.drawPath(clipPath, borderPaint);
            } else if (isLast) {
                canvas.drawPath(clipPath, borderPaint);
            } else {
                canvas.drawLine(x, rowTop, x, rowBottom, borderPaint);
                canvas.drawLine(x + totalWidth, rowTop, x + totalWidth, rowBottom, borderPaint);
            }
        }
    }

    // ==================== INLINE MEDIA CARD SPAN ====================

    private static class InlineMediaSpan extends ReplacementSpan {
        private final String kind;
        private final String title;
        private final String subtitle;
        private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint subPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF cardRect = new RectF();
        private final RectF iconRect = new RectF();
        private int computedWidth;

        InlineMediaSpan(String kind, String title, String subtitle) {
            this.kind = kind;
            this.title = title;
            this.subtitle = subtitle;
        }

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            titlePaint.set(paint);
            titlePaint.setTextSize(dp(13.5f));
            titlePaint.setTypeface(AndroidUtilities.bold());
            subPaint.set(paint);
            subPaint.setTextSize(dp(11.5f));

            int textW = (int) Math.max(titlePaint.measureText(title), subPaint.measureText(subtitle));
            int maxW = Math.max(dp(180), AndroidUtilities.displaySize.x - dp(116));
            computedWidth = Math.min(maxW, Math.max(dp(170), textW + dp(78)));

            int cardH = dp(48);
            if (fm != null) {
                fm.ascent = -cardH + dp(10);
                fm.descent = dp(10);
                fm.top = fm.ascent - dp(2);
                fm.bottom = fm.descent + dp(2);
            }
            return computedWidth;
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            int cardH = dp(48);
            float cardTop = y - cardH + dp(10);
            float cardBottom = cardTop + cardH;
            int baseTextColor = paint.getColor();
            int badgeColor = mediaBadgeColor(kind);

            cardRect.set(x, cardTop, x + computedWidth, cardBottom);
            bgPaint.setColor(ColorUtils.setAlphaComponent(badgeColor, 26));
            canvas.drawRoundRect(cardRect, dp(12), dp(12), bgPaint);

            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(1));
            borderPaint.setColor(ColorUtils.setAlphaComponent(badgeColor, 90));
            canvas.drawRoundRect(cardRect, dp(12), dp(12), borderPaint);

            // Left icon pill
            float iconSize = dp(34);
            float iconLeft = x + dp(7);
            float iconTop = cardTop + (cardH - iconSize) / 2f;
            iconRect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
            bgPaint.setColor(ColorUtils.setAlphaComponent(badgeColor, 52));
            canvas.drawRoundRect(iconRect, dp(9), dp(9), bgPaint);

            titlePaint.set(paint);
            titlePaint.setTextSize(dp(15));
            String emoji = mediaEmoji(kind);
            float ew = titlePaint.measureText(emoji);
            Paint.FontMetrics efm = titlePaint.getFontMetrics();
            float ey = iconTop + (iconSize - (efm.descent - efm.ascent)) / 2f - efm.ascent;
            canvas.drawText(emoji, iconLeft + (iconSize - ew) / 2f, ey, titlePaint);

            // Title & subtitle
            float textLeft = iconLeft + iconSize + dp(10);
            float availTextW = Math.max(dp(40), computedWidth - (textLeft - x) - dp(24));

            titlePaint.setTextSize(dp(13.5f));
            titlePaint.setTypeface(AndroidUtilities.bold());
            titlePaint.setColor(baseTextColor);
            CharSequence ellTitle = TextUtils.ellipsize(title, titlePaint, availTextW, TextUtils.TruncateAt.END);
            canvas.drawText(ellTitle, 0, ellTitle.length(), textLeft, cardTop + dp(20), titlePaint);

            subPaint.set(paint);
            subPaint.setTextSize(dp(11.5f));
            subPaint.setColor(ColorUtils.setAlphaComponent(baseTextColor, 165));
            CharSequence ellSub = TextUtils.ellipsize(subtitle, subPaint, availTextW, TextUtils.TruncateAt.END);
            canvas.drawText(ellSub, 0, ellSub.length(), textLeft, cardTop + dp(37), subPaint);

            // Right chevron
            titlePaint.setTextSize(dp(16));
            titlePaint.setColor(ColorUtils.setAlphaComponent(badgeColor, 200));
            canvas.drawText("›", x + computedWidth - dp(16), cardTop + dp(29), titlePaint);
        }
    }

    // ==================== DIVIDER SPAN ====================

    private static class DividerSpan extends ReplacementSpan {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public int getSize(@NonNull Paint paint, CharSequence text, int start, int end, @Nullable Paint.FontMetricsInt fm) {
            if (fm != null) {
                fm.ascent = -dp(14);
                fm.descent = dp(6);
                fm.top = fm.ascent;
                fm.bottom = fm.descent;
            }
            return Math.min(dp(220), Math.max(dp(140), AndroidUtilities.displaySize.x - dp(140)));
        }

        @Override
        public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
            int w = Math.min(dp(220), Math.max(dp(140), AndroidUtilities.displaySize.x - dp(140)));
            float cy = y - dp(4);
            int color = paint.getColor();
            linePaint.setColor(ColorUtils.setAlphaComponent(color, 70));
            linePaint.setStrokeWidth(dp(1.2f));
            float cx = x + w / 2f;
            canvas.drawLine(x, cy, cx - dp(10), cy, linePaint);
            canvas.drawLine(cx + dp(10), cy, x + w, cy, linePaint);
            linePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_chat_messageLinkIn), 180));
            canvas.drawCircle(cx, cy, dp(2.5f), linePaint);
        }
    }

    // ==================== COMPOSER DIALOGS ====================

    public interface FormattingInsertCallback {
        void insert(String text, String fmtUrl, boolean replaceSelection);
    }

    public static void showFormattingMenu(Context context, Theme.ResourcesProvider resourcesProvider,
                                          String selectedText, FormattingInsertCallback callback) {
        if (context == null || callback == null) return;

        final String[] items = new String[]{
                "📊 Таблица",
                "🖼️ Медиа-карточка (фото / видео / GIF)",
                "H1  Крупный заголовок",
                "H2  Подзаголовок",
                "🖍️ Выделение маркером",
                "➖ Разделительная линия",
                "x²  Надстрочный индекс",
                "H₂O Подстрочный индекс"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle("Расширенное форматирование");
        builder.setItems(items, (dialog, which) -> {
            switch (which) {
                case 0:
                    showTableBuilderDialog(context, resourcesProvider, callback);
                    break;
                case 1:
                    showMediaInsertDialog(context, resourcesProvider, selectedText, callback);
                    break;
                case 2:
                    applySimpleFormat("h1", selectedText, "Заголовок", callback);
                    break;
                case 3:
                    applySimpleFormat("h2", selectedText, "Подзаголовок", callback);
                    break;
                case 4:
                    showHighlightColorDialog(context, resourcesProvider, selectedText, callback);
                    break;
                case 5: {
                    String hr = "──────────";
                    callback.insert(hr, PREFIX + "?t=hr&len=" + hr.length(), true);
                    break;
                }
                case 6:
                    applySimpleFormat("sup", selectedText, "2", callback);
                    break;
                case 7:
                    applySimpleFormat("sub", selectedText, "2", callback);
                    break;
            }
        });
        builder.show();
    }

    private static void applySimpleFormat(String type, String selectedText, String fallback, FormattingInsertCallback callback) {
        String text = TextUtils.isEmpty(selectedText) ? fallback : selectedText;
        callback.insert(text, PREFIX + "?t=" + type + "&len=" + text.length(), true);
    }

    private static void showHighlightColorDialog(Context context, Theme.ResourcesProvider resourcesProvider,
                                                 String selectedText, FormattingInsertCallback callback) {
        final String[] names = new String[]{"Жёлтый маркер", "Зелёный маркер", "Голубой маркер", "Розовый маркер", "Оранжевый маркер"};
        final String[] hexes = new String[]{"FFD54F", "81C784", "64B5F6", "F48FB1", "FFB74D"};
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle("Цвет маркера");
        builder.setItems(names, (d, which) -> {
            String text = TextUtils.isEmpty(selectedText) ? "Выделенный текст" : selectedText;
            callback.insert(text, PREFIX + "?t=hl&c=" + hexes[which] + "&len=" + text.length(), true);
        });
        builder.show();
    }

    private static void showTableBuilderDialog(Context context, Theme.ResourcesProvider resourcesProvider,
                                               FormattingInsertCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle("Вставить таблицу");

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(8));

        TextView hint = new TextView(context);
        hint.setText("Каждая строка — ряд таблицы, столбцы разделяются знаком «|». Первая строка станет шапкой.");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        root.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditText editor = new EditText(context);
        editor.setText("Параметр | Значение\nСтатус | Активно\nВерсия | 13.2.1");
        editor.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        editor.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        editor.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        editor.setMinLines(4);
        editor.setMaxLines(8);
        editor.setGravity(Gravity.TOP | Gravity.LEFT);
        root.addView(editor, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);
        builder.setView(scroll);

        builder.setPositiveButton("Вставить", (d, w) -> {
            String raw = editor.getText().toString().trim();
            if (raw.isEmpty()) return;
            String[] lines = raw.split("\n");
            StringBuilder fallback = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                if (fallback.length() > 0) fallback.append('\n');
                fallback.append(line);
                if (i == 0 && lines.length > 1) {
                    int cols = line.split("\\|", -1).length;
                    fallback.append('\n');
                    for (int c = 0; c < cols; c++) {
                        if (c > 0) fallback.append("┼");
                        fallback.append("─────────");
                    }
                }
            }
            String out = fallback.toString();
            callback.insert(out, PREFIX + "?t=table&len=" + out.length(), true);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private static void showMediaInsertDialog(Context context, Theme.ResourcesProvider resourcesProvider,
                                              String selectedText, FormattingInsertCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);
        builder.setTitle("Медиа-карточка");

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(8));

        final String[] kindLabels = new String[]{"📷 Фото", "🎬 Видео", "🎞 GIF", "🎵 Аудио", "✨ Стикер"};
        final String[] kindCodes = new String[]{"img", "vid", "gif", "aud", "stk"};

        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, kindLabels);
        spinner.setAdapter(adapter);
        root.addView(spinner, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditText captionEdit = new EditText(context);
        captionEdit.setHint("Подпись (необязательно)");
        if (!TextUtils.isEmpty(selectedText)) {
            captionEdit.setText(selectedText);
        }
        captionEdit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        captionEdit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        root.addView(captionEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditText urlEdit = new EditText(context);
        urlEdit.setHint("Ссылка (https://...)");
        urlEdit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        urlEdit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourcesProvider));
        root.addView(urlEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(root);
        builder.setPositiveButton("Вставить", (d, w) -> {
            String url = urlEdit.getText().toString().trim();
            String cap = captionEdit.getText().toString().trim();
            if (url.isEmpty() && cap.isEmpty()) return;
            int idx = Math.max(0, spinner.getSelectedItemPosition());
            String kind = kindCodes[idx];
            String label = cap.isEmpty() ? defaultMediaTitle(kind) : cap;
            String fallback = "[" + mediaEmoji(kind) + " " + label + "]" + (url.isEmpty() ? "" : " " + url);
            String fmtUrl = PREFIX + "?t=media&k=" + kind
                    + "&u=" + Uri.encode(url)
                    + "&cap=" + Uri.encode(label)
                    + "&len=" + fallback.length();
            callback.insert(fallback, fmtUrl, true);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }
}
