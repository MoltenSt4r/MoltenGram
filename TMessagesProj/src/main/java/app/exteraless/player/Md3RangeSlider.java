package app.exteraless.player;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Material 3 Expressive Dual Range Slider matching official MD3 spec:
 * - Rounded inactive pill tracks with stop-indicator dots.
 * - Solid active connecting track in accent color.
 * - Tall vertical rounded pill handles with 6dp track cutouts/gaps.
 * - Smooth touch & haptic feedback for independent Fade Out and Fade In values.
 */
public class Md3RangeSlider extends View {

    public interface OnRangeChangeListener {
        void onRangeChanged(int value1, int value2, boolean fromUser);
    }

    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stopDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF rect = new RectF();

    private int minValue = 0;
    private int maxValue = 12;
    private int value1 = 3; // Fade Out
    private int value2 = 3; // Fade In

    private int activeThumb = 0; // 0 = none, 1 = value1, 2 = value2
    private OnRangeChangeListener listener;

    private String label1Prefix = "Out";
    private String label2Prefix = "In";

    public Md3RangeSlider(Context context) {
        super(context);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        setFocusable(true);

        activePaint.setStyle(Paint.Style.FILL);
        inactivePaint.setStyle(Paint.Style.FILL);
        stopDotPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setStyle(Paint.Style.FILL);

        labelPaint.setTextSize(AndroidUtilities.dp(11));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setFakeBoldText(true);

        labelBgPaint.setStyle(Paint.Style.FILL);
    }

    public void setRange(int min, int max) {
        this.minValue = min;
        this.maxValue = max;
        invalidate();
    }

    public void setValues(int val1, int val2) {
        this.value1 = Math.max(minValue, Math.min(maxValue, val1));
        this.value2 = Math.max(minValue, Math.min(maxValue, val2));
        invalidate();
    }

    public void setLabels(String prefix1, String prefix2) {
        this.label1Prefix = prefix1;
        this.label2Prefix = prefix2;
        invalidate();
    }

    public int getValue1() {
        return value1;
    }

    public int getValue2() {
        return value2;
    }

    public void setOnRangeChangeListener(OnRangeChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = AndroidUtilities.dp(72);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        boolean isDark = Theme.isCurrentThemeDark();

        int inactiveColor = ColorUtils.blendARGB(accentColor, isDark ? 0xFF2A2B36 : 0xFFE5E7EB, isDark ? 0.82f : 0.78f);
        int stopDotColor = ColorUtils.blendARGB(accentColor, isDark ? 0xFFFFFFFF : 0xFF1E1E24, 0.45f);

        activePaint.setColor(accentColor);
        inactivePaint.setColor(inactiveColor);
        stopDotPaint.setColor(stopDotColor);
        thumbPaint.setColor(accentColor);

        float paddingLeft = AndroidUtilities.dp(24);
        float paddingRight = AndroidUtilities.dp(24);
        float trackLeft = paddingLeft;
        float trackRight = getWidth() - paddingRight;
        float trackWidth = trackRight - trackLeft;

        if (trackWidth <= 0) {
            return;
        }

        float trackCenterY = AndroidUtilities.dp(44);
        float trackHalfH = AndroidUtilities.dp(8);
        float trackTop = trackCenterY - trackHalfH;
        float trackBottom = trackCenterY + trackHalfH;
        float trackRadius = AndroidUtilities.dp(8);

        float gap = AndroidUtilities.dp(5);
        float thumbW = AndroidUtilities.dp(4.5f);
        float thumbH = AndroidUtilities.dp(42);
        float thumbHalfW = thumbW / 2f;
        float thumbHalfH = thumbH / 2f;
        float thumbRadius = AndroidUtilities.dp(2.25f);

        float norm1 = (value1 - minValue) / (float) (maxValue - minValue);
        float norm2 = (value2 - minValue) / (float) (maxValue - minValue);

        float x1 = trackLeft + norm1 * trackWidth;
        float x2 = trackLeft + norm2 * trackWidth;

        float leftX = Math.min(x1, x2);
        float rightX = Math.max(x1, x2);

        // 1. Left inactive track (from trackLeft to leftX - gap)
        float leftSegmentEnd = leftX - gap - thumbHalfW;
        if (leftSegmentEnd > trackLeft + AndroidUtilities.dp(2)) {
            rect.set(trackLeft, trackTop, leftSegmentEnd, trackBottom);
            canvas.drawRoundRect(rect, trackRadius, trackRadius, inactivePaint);
        }

        // 2. Center active track (between leftX + gap and rightX - gap)
        float centerSegmentStart = leftX + gap + thumbHalfW;
        float centerSegmentEnd = rightX - gap - thumbHalfW;
        if (centerSegmentEnd - centerSegmentStart >= AndroidUtilities.dp(4)) {
            rect.set(centerSegmentStart, trackTop, centerSegmentEnd, trackBottom);
            canvas.drawRoundRect(rect, trackRadius, trackRadius, activePaint);
        }

        // 3. Right inactive track (from rightX + gap to trackRight)
        float rightSegmentStart = rightX + gap + thumbHalfW;
        if (rightSegmentStart < trackRight - AndroidUtilities.dp(2)) {
            rect.set(rightSegmentStart, trackTop, trackRight, trackBottom);
            canvas.drawRoundRect(rect, trackRadius, trackRadius, inactivePaint);
        }

        // 4. Stop-indicator dots inside the inactive track ends
        float dotRadius = AndroidUtilities.dp(2.8f);
        float leftDotX = trackLeft + AndroidUtilities.dp(8);
        if (leftDotX < leftSegmentEnd - dotRadius) {
            canvas.drawCircle(leftDotX, trackCenterY, dotRadius, stopDotPaint);
        }
        float rightDotX = trackRight - AndroidUtilities.dp(8);
        if (rightDotX > rightSegmentStart + dotRadius) {
            canvas.drawCircle(rightDotX, trackCenterY, dotRadius, stopDotPaint);
        }

        // 5. Vertical pill thumbs
        // Draw Thumb 1 (Fade Out)
        rect.set(x1 - thumbHalfW, trackCenterY - thumbHalfH, x1 + thumbHalfW, trackCenterY + thumbHalfH);
        canvas.drawRoundRect(rect, thumbRadius, thumbRadius, thumbPaint);

        // Draw Thumb 2 (Fade In)
        rect.set(x2 - thumbHalfW, trackCenterY - thumbHalfH, x2 + thumbHalfW, trackCenterY + thumbHalfH);
        canvas.drawRoundRect(rect, thumbRadius, thumbRadius, thumbPaint);

        // 6. Draw clean badges/labels above handles
        drawThumbLabel(canvas, x1, label1Prefix + ": " + value1 + "s", accentColor, isDark);
        drawThumbLabel(canvas, x2, label2Prefix + ": " + value2 + "s", accentColor, isDark);
    }

    private void drawThumbLabel(Canvas canvas, float x, String text, int accentColor, boolean isDark) {
        float labelW = labelPaint.measureText(text) + AndroidUtilities.dp(10);
        float labelH = AndroidUtilities.dp(16);
        float labelTop = AndroidUtilities.dp(4);
        float labelLeft = Math.max(AndroidUtilities.dp(4), Math.min(getWidth() - AndroidUtilities.dp(4) - labelW, x - labelW / 2f));
        rect.set(labelLeft, labelTop, labelLeft + labelW, labelTop + labelH);

        labelBgPaint.setColor(ColorUtils.setAlphaComponent(accentColor, isDark ? 40 : 28));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), labelBgPaint);

        labelPaint.setColor(accentColor);
        canvas.drawText(text, rect.centerX(), rect.centerY() + AndroidUtilities.dp(4), labelPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float paddingLeft = AndroidUtilities.dp(24);
        float paddingRight = AndroidUtilities.dp(24);
        float trackLeft = paddingLeft;
        float trackRight = getWidth() - paddingRight;
        float trackWidth = trackRight - trackLeft;

        if (trackWidth <= 0) {
            return false;
        }

        float norm1 = (value1 - minValue) / (float) (maxValue - minValue);
        float norm2 = (value2 - minValue) / (float) (maxValue - minValue);
        float x1 = trackLeft + norm1 * trackWidth;
        float x2 = trackLeft + norm2 * trackWidth;

        float touchX = event.getX();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                float dist1 = Math.abs(touchX - x1);
                float dist2 = Math.abs(touchX - x2);

                if (dist1 <= dist2) {
                    activeThumb = 1;
                } else {
                    activeThumb = 2;
                }

                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                updateFromTouch(touchX, trackLeft, trackWidth);
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (activeThumb != 0) {
                    updateFromTouch(touchX, trackLeft, trackWidth);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                activeThumb = 0;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void updateFromTouch(float touchX, float trackLeft, float trackWidth) {
        float fraction = Math.max(0f, Math.min(1f, (touchX - trackLeft) / trackWidth));
        int val = Math.round(minValue + fraction * (maxValue - minValue));

        boolean changed = false;
        if (activeThumb == 1) {
            if (value1 != val) {
                value1 = val;
                changed = true;
            }
        } else if (activeThumb == 2) {
            if (value2 != val) {
                value2 = val;
                changed = true;
            }
        }

        if (changed) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) {
                listener.onRangeChanged(value1, value2, true);
            }
            invalidate();
        }
    }
}
