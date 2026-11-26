/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ThemeKeyCatalog;

public class TextColorThemeCell extends FrameLayout {

    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView hexView;

    private int currentColor;
    private boolean hasColor;
    private boolean isModified;
    private boolean needDivider;

    private final Paint colorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkerLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkerDarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hexBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint modifiedDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint();
    private final Path circleClipPath = new Path();
    private final RectF hexRect = new RectF();

    private int textColor;
    private int subTextColor;
    private int surfaceColor;
    private int accentColor;
    private boolean darkTheme;

    public TextColorThemeCell(Context context) {
        super(context);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dpf2(1.2f));
        checkerLightPaint.setColor(0xFFFFFFFF);
        checkerDarkPaint.setColor(0xFFCBD2DA);

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setLines(1);
        titleView.setMaxLines(1);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textColumn.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        subtitleView.setLines(1);
        subtitleView.setMaxLines(1);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        subtitleView.setVisibility(GONE);
        textColumn.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        addView(textColumn, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL,
                LocaleController.isRTL ? 112 : 62,
                0,
                LocaleController.isRTL ? 62 : 112,
                0
        ));

        hexView = new TextView(context);
        hexView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        hexView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO));
        hexView.setLines(1);
        hexView.setSingleLine(true);
        hexView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3));
        hexView.setVisibility(GONE);
        addView(hexView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                LocaleController.isRTL ? 16 : 0,
                0,
                LocaleController.isRTL ? 0 : 16,
                0
        ));

        applyDefaultTheme();
    }

    private void applyDefaultTheme() {
        int bg = Theme.getColor(Theme.key_dialogBackground);
        boolean dark = AndroidUtilities.computePerceivedBrightness(bg) < 0.5f;
        int fg = dark ? 0xFFF2F4F8 : 0xFF1A1D21;
        int sub = dark ? 0xFFA2ABB8 : 0xFF6B7380;
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4);
        if (Color.alpha(accent) < 128) {
            accent = 0xFF3390EC;
        }
        setThemeColors(fg, sub, bg, accent, dark);
    }

    public void setThemeColors(int textColor, int subTextColor, int surfaceColor, int accentColor, boolean dark) {
        this.textColor = textColor;
        this.subTextColor = subTextColor;
        this.surfaceColor = surfaceColor;
        this.accentColor = accentColor;
        this.darkTheme = dark;

        titleView.setTextColor(textColor);
        subtitleView.setTextColor(subTextColor);
        hexView.setTextColor(dark ? Theme.multAlpha(textColor, 0.88f) : Theme.multAlpha(textColor, 0.78f));
        hexBgPaint.setColor(dark ? 0x16FFFFFF : 0x0E000000);
        strokePaint.setColor(dark ? 0x38FFFFFF : 0x26000000);
        modifiedDotPaint.setColor(accentColor);
        bgDotPaint.setColor(surfaceColor);
        dividerPaint.setColor(dark ? 0x18FFFFFF : 0x12000000);
        checkerLightPaint.setColor(dark ? 0xFF3A404A : 0xFFFFFFFF);
        checkerDarkPaint.setColor(dark ? 0xFF282D35 : 0xFFD8DEE6);
        invalidate();
    }

    public void setNeedDivider(boolean needDivider) {
        this.needDivider = needDivider;
        setWillNotDraw(!needDivider && !hasColor);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minHeight = subtitleView.getVisibility() == VISIBLE ? AndroidUtilities.dp(60) : AndroidUtilities.dp(50);
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(minHeight + (needDivider ? 1 : 0), MeasureSpec.EXACTLY)
        );
    }

    public void setTextAndColor(CharSequence text, int color) {
        setThemeEntry(text, null, color, false, false);
    }

    public void setThemeEntry(CharSequence title, CharSequence rawKey, int color, boolean modified, boolean divider) {
        titleView.setText(title);
        if (!TextUtils.isEmpty(rawKey)) {
            subtitleView.setText(rawKey);
            subtitleView.setVisibility(VISIBLE);
        } else {
            subtitleView.setText("");
            subtitleView.setVisibility(GONE);
        }
        currentColor = color;
        hasColor = true;
        isModified = modified;
        needDivider = divider;

        String hex = ThemeKeyCatalog.formatHex(color, false);
        hexView.setText(hex);
        hexView.setVisibility(VISIBLE);
        setWillNotDraw(false);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (hexView.getVisibility() == VISIBLE && hexView.getWidth() > 0) {
            hexRect.set(hexView.getLeft(), hexView.getTop(), hexView.getRight(), hexView.getBottom());
            float rad = AndroidUtilities.dpf2(8);
            canvas.drawRoundRect(hexRect, rad, rad, hexBgPaint);
        }

        if (hasColor) {
            float cy = getMeasuredHeight() / 2f;
            float cx = LocaleController.isRTL ? getMeasuredWidth() - AndroidUtilities.dp(30) : AndroidUtilities.dp(30);
            float radius = AndroidUtilities.dpf2(14);

            if (Color.alpha(currentColor) < 255) {
                canvas.save();
                circleClipPath.reset();
                circleClipPath.addCircle(cx, cy, radius, Path.Direction.CW);
                canvas.clipPath(circleClipPath);
                float cell = AndroidUtilities.dpf2(4.5f);
                float left = cx - radius;
                float top = cy - radius;
                int cols = (int) Math.ceil((radius * 2f) / cell);
                for (int r = 0; r < cols; r++) {
                    for (int c = 0; c < cols; c++) {
                        Paint p = ((r + c) & 1) == 0 ? checkerLightPaint : checkerDarkPaint;
                        canvas.drawRect(
                                left + c * cell,
                                top + r * cell,
                                left + (c + 1) * cell,
                                top + (r + 1) * cell,
                                p
                        );
                    }
                }
                canvas.restore();
            }

            colorPaint.setColor(currentColor);
            canvas.drawCircle(cx, cy, radius, colorPaint);
            canvas.drawCircle(cx, cy, radius - AndroidUtilities.dpf2(0.5f), strokePaint);

            if (isModified) {
                float dx = LocaleController.isRTL ? cx - radius * 0.72f : cx + radius * 0.72f;
                float dy = cy - radius * 0.72f;
                canvas.drawCircle(dx, dy, AndroidUtilities.dpf2(4.5f), bgDotPaint);
                canvas.drawCircle(dx, dy, AndroidUtilities.dpf2(3.0f), modifiedDotPaint);
            }
        }

        if (needDivider) {
            int start = LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(62);
            int end = LocaleController.isRTL ? getMeasuredWidth() - AndroidUtilities.dp(62) : getMeasuredWidth() - AndroidUtilities.dp(16);
            canvas.drawLine(start, getMeasuredHeight() - 1, end, getMeasuredHeight() - 1, dividerPaint);
        }
    }
}
