/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Pair;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Collections;

public class SeekBar {

    public interface SeekBarDelegate {
        void onSeekBarDrag(float progress);
        default void onSeekBarContinuousDrag(float progress) {

        }
        default void onSeekBarPressed() {}
        default void onSeekBarReleased() {}

        default boolean isSeekBarDragAllowed() {
            return true;
        }

        default boolean reverseWaveform() {
            return false;
        }
    }

    private static Paint paint;
    private static int thumbWidth;
    private float thumbProgress;
    private int thumbX = 0;
    private int draggingThumbX = 0;
    private int thumbDX = 0;
    private boolean pressed = false;
    private int width;
    private int height;
    private SeekBarDelegate delegate;
    private int backgroundColor;
    private int cacheColor;
    private int circleColor;
    private int progressColor;
    private int backgroundSelectedColor;
    private RectF rect = new RectF();
    private int lineHeight = AndroidUtilities.dp(2);
    private boolean selected;
    private float bufferedProgress;
    private float currentRadius;
    private long lastUpdateTime;
    private View parentView;
    private float alpha = 1f;
    private boolean playing;
    private float customWaveAmplitude;
    private long customWaveLastTime;
    private Path customWavePath;
    private Paint customWavePaint;

    public void setPlaying(boolean value) {
        if (playing != value) {
            playing = value;
            customWaveLastTime = SystemClock.uptimeMillis();
            if (parentView != null) {
                parentView.postInvalidateOnAnimation();
            }
        }
    }

    public SeekBar(View parent) {
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        parentView = parent;
        thumbWidth = AndroidUtilities.dp(24);
        currentRadius = AndroidUtilities.dp(6);
    }

    public void setParent(View parent) {
        parentView = parent;
    }

    public void setDelegate(SeekBarDelegate seekBarDelegate) {
        delegate = seekBarDelegate;
    }

    public boolean onTouch(int action, float x, float y) {
        if (action == MotionEvent.ACTION_DOWN) {
            int additionWidth = (height - thumbWidth) / 2;
            if (x >= -additionWidth && x <= width + additionWidth && y >= 0 && y <= height) {
                if (!(thumbX - additionWidth <= x && x <= thumbX + thumbWidth + additionWidth)) {
                    thumbX = (int) x - thumbWidth / 2;
                    if (thumbX < 0) {
                        thumbX = 0;
                    } else if (thumbX > width - thumbWidth) {
                        thumbX = width - thumbWidth;
                    }
                }
                pressed = true;
                draggingThumbX = thumbX;
                thumbDX = (int) (x - thumbX);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pressed) {
                thumbX = draggingThumbX;
                if (action == MotionEvent.ACTION_UP && delegate != null) {
                    delegate.onSeekBarDrag((float) thumbX / (float) (width - thumbWidth));
                }
                pressed = false;
                return true;
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            if (pressed) {
                draggingThumbX = (int) (x - thumbDX);
                if (draggingThumbX < 0) {
                    draggingThumbX = 0;
                } else if (draggingThumbX > width - thumbWidth) {
                    draggingThumbX = width - thumbWidth;
                }
                if (delegate != null) {
                    delegate.onSeekBarContinuousDrag((float) draggingThumbX / (float) (width - thumbWidth));
                }
                return true;
            }
        }
        return false;
    }

    public void setColors(int background, int cache, int progress, int circle, int selected) {
        backgroundColor = background;
        cacheColor = cache;
        circleColor = circle;
        progressColor = progress;
        backgroundSelectedColor = selected;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public void setProgress(float progress) {
        thumbProgress = progress;
        thumbX = (int) Math.ceil((width - thumbWidth) * thumbProgress);
        if (thumbX < 0) {
            thumbX = 0;
        } else if (thumbX > width - thumbWidth) {
            thumbX = width - thumbWidth;
        }
    }

    public void setBufferedProgress(float value) {
        bufferedProgress = value;
    }

    public float getProgress() {
        return (float) thumbX / (float) (width - thumbWidth);
    }

    public int getThumbX() {
        return (pressed ? draggingThumbX : thumbX) + thumbWidth / 2;
    }

    public boolean isDragging() {
        return pressed;
    }

    public void setSelected(boolean value) {
        selected = value;
    }

    public void setSize(int w, int h) {
        if (width == w && height == h) {
            return;
        }
        width = w;
        height = h;
        setProgress(thumbProgress);
    }

    public int getWidth() {
        return width - thumbWidth;
    }

    public void setLineHeight(int value) {
        lineHeight = value;
    }

    public void draw(Canvas canvas) {
        if (alpha <= 0) {
            return;
        }
        if (alpha < 1) {
            canvas.saveLayerAlpha(0, 0, width, height, (int) (255 * alpha), Canvas.ALL_SAVE_FLAG);
        }
        if (app.exteraless.appearance.AppearanceConfig.chatMediaSeekbarEnabled()) {
            drawCustomMediaSeekBar(canvas);
            if (alpha < 1) {
                canvas.restore();
            }
            updateTimestampAnimation();
            return;
        }
        rect.set(thumbWidth / 2, height / 2 - lineHeight / 2, width - thumbWidth / 2, height / 2 + lineHeight / 2);
        paint.setColor(selected ? backgroundSelectedColor : backgroundColor);
        drawProgressBar(canvas, rect, paint);
        if (bufferedProgress > 0) {
            paint.setColor(selected ? backgroundSelectedColor : cacheColor);
            rect.set(thumbWidth / 2, height / 2 - lineHeight / 2, thumbWidth / 2 + bufferedProgress * (width - thumbWidth), height / 2 + lineHeight / 2);
            drawProgressBar(canvas, rect, paint);
        }
        rect.set(thumbWidth / 2, height / 2 - lineHeight / 2, thumbWidth / 2 + (pressed ? draggingThumbX : thumbX), height / 2 + lineHeight / 2);
        paint.setColor(progressColor);
        drawProgressBar(canvas, rect, paint);
        paint.setColor(circleColor);

        int newRad = AndroidUtilities.dp(pressed ? 8 : 6);
        if (currentRadius != newRad) {
            long newUpdateTime = SystemClock.elapsedRealtime();
            long dt = newUpdateTime - lastUpdateTime;
            if (dt > 18) {
                dt = 16;
            }
            if (currentRadius < newRad) {
                currentRadius += AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius > newRad) {
                    currentRadius = newRad;
                }
            } else {
                currentRadius -= AndroidUtilities.dp(1) * (dt / 60.0f);
                if (currentRadius < newRad) {
                    currentRadius = newRad;
                }
            }
            if (parentView != null) {
                parentView.invalidate();
            }
        }

        canvas.drawCircle((pressed ? draggingThumbX : thumbX) + thumbWidth / 2, height / 2, currentRadius, paint);

        if (alpha < 1) {
            canvas.restore();
        }

        updateTimestampAnimation();
    }

    private void drawCustomMediaSeekBar(Canvas canvas) {
        float left = thumbWidth / 2f;
        float right = width - thumbWidth / 2f;
        float cy = height / 2f;
        if (right <= left) {
            return;
        }
        float thumbCx = Math.max(left, Math.min(right, left + (pressed ? draggingThumbX : thumbX)));
        int style = app.exteraless.appearance.AppearanceConfig.mediaSeekbarStyle(); // 0 = wavy, 1 = straight MD3
        int indicator = app.exteraless.appearance.AppearanceConfig.mediaSeekbarIndicator(); // 0 = bar, 1 = dot, 2 = none
        float thickness = Math.max(AndroidUtilities.dpf2(2.2f), Math.min(AndroidUtilities.dpf2(4.2f), AndroidUtilities.dpf2(app.exteraless.appearance.AppearanceConfig.playerSeekbarThickness() * 0.65f)));
        float indicatorHalf = indicator == 0
                ? AndroidUtilities.dpf2(2.8f) * 0.5f
                : (indicator == 1 ? AndroidUtilities.dpf2(pressed ? 6.5f : 5f) : 0f);
        float gap = indicator == 2 ? 0f : indicatorHalf + AndroidUtilities.dpf2(3f);

        float inactiveStart = Math.min(right, thumbCx + gap);
        if (right > inactiveStart) {
            rect.set(inactiveStart, cy - thickness * 0.5f, right, cy + thickness * 0.5f);
            paint.setColor(selected ? backgroundSelectedColor : backgroundColor);
            canvas.drawRoundRect(rect, thickness * 0.5f, thickness * 0.5f, paint);
            if (bufferedProgress > 0 && bufferedProgress < 1f) {
                float bufRight = Math.max(inactiveStart, Math.min(right, left + bufferedProgress * (right - left)));
                if (bufRight > inactiveStart) {
                    paint.setColor(selected ? backgroundSelectedColor : cacheColor);
                    rect.set(inactiveStart, cy - thickness * 0.5f, bufRight, cy + thickness * 0.5f);
                    canvas.drawRoundRect(rect, thickness * 0.5f, thickness * 0.5f, paint);
                }
            }
        }

        long now = SystemClock.uptimeMillis();
        float dt = customWaveLastTime > 0 ? Math.min(48L, Math.max(0L, now - customWaveLastTime)) : 16L;
        customWaveLastTime = now;
        float targetAmp = (style == 0 && playing) ? 1f : 0f;
        if (customWaveAmplitude != targetAmp) {
            float delta = dt / 220f;
            if (customWaveAmplitude < targetAmp) {
                customWaveAmplitude = Math.min(1f, customWaveAmplitude + delta);
            } else {
                customWaveAmplitude = Math.max(0f, customWaveAmplitude - delta);
            }
        }

        float activeEnd = Math.max(left, thumbCx - gap);
        if (activeEnd > left) {
            float halfStroke = thickness * 0.5f;
            float waveStart = left + halfStroke;
            float waveEnd = activeEnd - halfStroke;
            if (style == 0 && customWaveAmplitude > 0.001f && waveEnd - waveStart > AndroidUtilities.dpf2(2f)) {
                if (customWavePath == null) {
                    customWavePath = new Path();
                }
                if (customWavePaint == null) {
                    customWavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    customWavePaint.setStyle(Paint.Style.STROKE);
                    customWavePaint.setStrokeCap(Paint.Cap.ROUND);
                    customWavePaint.setStrokeJoin(Paint.Join.ROUND);
                }
                customWavePaint.setColor(progressColor);
                customWavePaint.setStrokeWidth(thickness);

                float amp = AndroidUtilities.dpf2(1.5f) * customWaveAmplitude;
                float wavelength = AndroidUtilities.dpf2(16f);
                float phase = ((now % 1200L) / 1200f) * (float) (2.0 * Math.PI);
                float step = AndroidUtilities.dpf2(1.6f);
                float taper = AndroidUtilities.dpf2(4.5f);
                customWavePath.rewind();
                boolean first = true;
                for (float x = waveStart; ; x += step) {
                    float sx = Math.min(x, waveEnd);
                    float u = Math.max(0f, Math.min(1f, Math.min((sx - waveStart) / taper, (waveEnd - sx) / taper)));
                    float smoothFade = u * u * (3f - 2f * u);
                    float y = cy + (float) Math.sin((sx - left) / wavelength * 2.0 * Math.PI - phase) * amp * smoothFade;
                    if (first) {
                        customWavePath.moveTo(sx, y);
                        first = false;
                    } else {
                        customWavePath.lineTo(sx, y);
                    }
                    if (sx >= waveEnd) {
                        break;
                    }
                }
                canvas.drawPath(customWavePath, customWavePaint);
            } else {
                paint.setColor(progressColor);
                rect.set(left, cy - thickness * 0.55f, activeEnd, cy + thickness * 0.55f);
                canvas.drawRoundRect(rect, thickness * 0.55f, thickness * 0.55f, paint);
            }
        }
        if (((style == 0 && playing) || Math.abs(customWaveAmplitude - targetAmp) > 0.001f) && parentView != null) {
            parentView.postInvalidateOnAnimation();
        }

        paint.setColor(circleColor);
        if (indicator == 0) {
            float barW = AndroidUtilities.dpf2(2.8f);
            float barH = AndroidUtilities.dpf2(pressed ? 14f : 11f);
            rect.set(thumbCx - barW / 2f, cy - barH / 2f, thumbCx + barW / 2f, cy + barH / 2f);
            canvas.drawRoundRect(rect, barW / 2f, barW / 2f, paint);
        } else if (indicator == 1) {
            float dotR = AndroidUtilities.dpf2(pressed ? 6.5f : 5f);
            canvas.drawCircle(thumbCx, cy, dotR, paint);
        }
    }

    protected void onTimestampUpdate(URLSpanNoUnderline link) {

    }

    private ArrayList<Pair<Float, URLSpanNoUnderline>> timestamps;
    private CharSequence lastCaption;
    private long lastVideoDuration;

    private float timestampsAppearing = 0;
    private long lastTimestampsAppearingUpdate;
    private final float TIMESTAMP_GAP = 1f;
    private static float[] tmpRadii;
    private static Path tmpPath;

    private int currentTimestamp = -1, lastTimestamp = -1;
    private StaticLayout[] timestampLabel;
    private TextPaint timestampLabelPaint;
    private float timestampChangeT = 1;
    private int timestampChangeDirection;
    private long lastTimestampUpdate;
    private float lastWidth = -1;

    public void clearTimestamps() {
        timestamps = null;
        currentTimestamp = -1;
        timestampsAppearing = 0;
        if (timestampLabel != null) {
            timestampLabel[0] = timestampLabel[1] = null;
        }
        lastCaption = null;
        lastVideoDuration = -1;
    }

    public void updateTimestamps(MessageObject messageObject, Long videoDuration) {
        if (messageObject == null) {
            clearTimestamps();
            return;
        }
        if (videoDuration == null) {
            videoDuration = (long) messageObject.getDuration() * 1000L;
        }
        if (videoDuration == null || videoDuration < 0) {
            clearTimestamps();
            return;
        }
        CharSequence text = messageObject.caption;
        if (messageObject.isYouTubeVideo()) {
            if (messageObject.youtubeDescription == null && messageObject.messageOwner.media.webpage.description != null) {
                messageObject.youtubeDescription = SpannableString.valueOf(messageObject.messageOwner.media.webpage.description);
                MessageObject.addUrlsByPattern(messageObject.isOut(), messageObject.youtubeDescription, false, 3, (int) (long) videoDuration, false);
            }
            text = messageObject.youtubeDescription;
        }
        if (text == lastCaption && lastVideoDuration == videoDuration) {
            return;
        }
        lastCaption = text;
        lastVideoDuration = videoDuration;
        if (!(text instanceof Spanned)) {
            timestamps = null;
            currentTimestamp = -1;
            timestampsAppearing = 0;
            if (timestampLabel != null) {
                timestampLabel[0] = timestampLabel[1] = null;
            }
            return;
        }
        Spanned spanned = (Spanned) text;
        URLSpanNoUnderline[] links;
        try {
            links = spanned.getSpans(0, spanned.length(), URLSpanNoUnderline.class);
        } catch (Exception e) {
            FileLog.e(e);
            timestamps = null;
            currentTimestamp = -1;
            timestampsAppearing = 0;
            if (timestampLabel != null) {
                timestampLabel[0] = timestampLabel[1] = null;
            }
            return;
        }
        timestamps = new ArrayList<>();
        timestampsAppearing = 0;
        if (timestampLabelPaint == null) {
            timestampLabelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            timestampLabelPaint.setTextSize(AndroidUtilities.dp(12));
            timestampLabelPaint.setColor(0xffffffff);
        }
        for (int i = 0; i < links.length; ++i) {
            try {
                URLSpanNoUnderline link = links[i];
                if (link != null && link.getURL() != null && link.label != null && link.getURL().startsWith("audio?")) {
                    Integer seconds = Utilities.parseInt(link.getURL().substring(6));
                    if (seconds != null && seconds >= 0) {
                        float position = seconds * 1000L / (float) videoDuration;
                        String label = link.label;
                        SpannableStringBuilder builder = new SpannableStringBuilder(label);
                        Emoji.replaceEmoji(builder, timestampLabelPaint.getFontMetricsInt(), false);
                        timestamps.add(new Pair<>(position, link));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        Collections.sort(timestamps, (a, b) -> {
            if (a.first > b.first) {
                return 1;
            } else if (b.first > a.first) {
                return -1;
            } else {
                return 0;
            }
        });
    }

    private void drawProgressBar(Canvas canvas, RectF rect, Paint paint) {
        float radius = thumbWidth / 2f;
        if (timestamps == null || timestamps.isEmpty()) {
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            float lineWidth = rect.bottom - rect.top;
            float left = thumbWidth / 2f;
            float right = width - thumbWidth / 2f;
            AndroidUtilities.rectTmp.set(rect);
            float halfGap = AndroidUtilities.dp(TIMESTAMP_GAP * timestampsAppearing) / 2f;
            if (tmpPath == null) {
                tmpPath = new Path();
            }
            tmpPath.reset();
            float minDur = AndroidUtilities.dp(4) / (right - left);
            int start = -1, end = -1;
            for (int i = 0; i < timestamps.size(); ++i) {
                if (timestamps.get(i).first >= minDur) {
                    start = i;
                    break;
                }
            }
            if (start < 0) {
                start = 0;
            }
            for (int i = timestamps.size() - 1; i >= 0; --i) {
                if (1f - timestamps.get(i).first >= minDur) {
                    end = i + 1;
                    break;
                }
            }
            if (end < 0) {
                end = timestamps.size();
            }
            boolean first = true;
            for (int i = start; i <= end; ++i) {
                float from = i == start ? 0 : timestamps.get(i - 1).first;
                float to = i == end ? 1 : timestamps.get(i).first;
                while (i != end && i != 0 && i < timestamps.size() - 1 && timestamps.get(i).first - from <= minDur) {
                    i++;
                    to = timestamps.get(i).first;
                }

                AndroidUtilities.rectTmp.left = AndroidUtilities.lerp(left, right, from) + (i > 0 ? halfGap : 0);
                AndroidUtilities.rectTmp.right = AndroidUtilities.lerp(left, right, to) - (i < end ? halfGap : 0);

                boolean last;
                if (last = AndroidUtilities.rectTmp.right > rect.right) {
                    AndroidUtilities.rectTmp.right = rect.right;
                }
                if (AndroidUtilities.rectTmp.right < rect.left) {
                    continue;
                }
                if (AndroidUtilities.rectTmp.left < rect.left) {
                    AndroidUtilities.rectTmp.left = rect.left;
                }

                if (tmpRadii == null) {
                    tmpRadii = new float[8];
                }
                if (i == start || last && AndroidUtilities.rectTmp.left >= rect.left) {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] = radius;
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius * 0.7f * timestampsAppearing;
                } else if (i >= end) {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] = radius * 0.7f * timestampsAppearing;
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius;
                } else {
                    tmpRadii[0] = tmpRadii[1] = tmpRadii[6] = tmpRadii[7] =
                    tmpRadii[2] = tmpRadii[3] = tmpRadii[4] = tmpRadii[5] = radius * 0.7f * timestampsAppearing;
                }
                tmpPath.addRoundRect(AndroidUtilities.rectTmp, tmpRadii, Path.Direction.CW);

                if (last) {
                    break;
                }
            }
            canvas.drawPath(tmpPath, paint);
        }
    }

    private void updateTimestampAnimation() {
        if (timestamps == null || timestamps.isEmpty()) {
            return;
        }

        float progress = (pressed ? draggingThumbX : thumbX) / (float) (width - thumbWidth);

        int timestampIndex = -1;
        for (int i = timestamps.size() - 1; i >= 0; --i) {
            if (timestamps.get(i).first - 0.001f <= progress) {
                timestampIndex = i;
                break;
            }
        }

        if (timestampLabel == null) {
            timestampLabel = new StaticLayout[2];
        }

        float left = thumbWidth / 2f;
        float right = width - thumbWidth / 2f;
        float rightPadded = right;
        float width = Math.abs(left - rightPadded) - AndroidUtilities.dp(16 + 50);

        lastWidth = width;

        if (timestampIndex != currentTimestamp) {
            if (pressed) {
                AndroidUtilities.vibrateCursor(parentView);
            }
            currentTimestamp = timestampIndex;
            if (currentTimestamp >= 0 && currentTimestamp < timestamps.size()) {
                onTimestampUpdate(timestamps.get(currentTimestamp).second);
            }
        }
        if (timestampChangeT < 1f) {
            long tx = Math.min(17, Math.abs(SystemClock.elapsedRealtime() - lastTimestampUpdate));
            float duration = timestamps.size() > 8 ? 160f : 220f;
            timestampChangeT = Math.min(timestampChangeT + tx / duration, 1);
            if (parentView != null) {
                parentView.invalidate();
            }
            lastTimestampUpdate = SystemClock.elapsedRealtime();
        }
        if (timestampsAppearing < 1f) {
            long tx = Math.min(17, Math.abs(SystemClock.elapsedRealtime() - lastTimestampUpdate));
            timestampsAppearing = Math.min(timestampsAppearing + tx / 200f, 1);
            if (parentView != null) {
                parentView.invalidate();
            }
            lastTimestampsAppearingUpdate = SystemClock.elapsedRealtime();
        }
    }
}
