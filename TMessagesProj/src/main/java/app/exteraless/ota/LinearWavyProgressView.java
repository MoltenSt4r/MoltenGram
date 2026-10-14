package app.exteraless.ota;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Material 3 Linear Wavy Progress Indicator.
 * Draws an animated sinusoidal wave on the loaded portion and a straight track with dot on the remaining portion.
 */
public class LinearWavyProgressView extends View {

    private static final float TAU = (float) (Math.PI * 2);

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float progress = 0f;
    private float animatedProgress = 0f;
    private float phase = 0f;
    private long lastFrameTime = 0;
    private boolean isAnimating = true;

    private int activeColor = 0xFFF5A88E;
    private int trackColor = 0x33F5A88E;

    public LinearWavyProgressView(Context context) {
        super(context);
        init();
    }

    public LinearWavyProgressView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(dp(3.5f));
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeJoin(Paint.Join.ROUND);
        wavePaint.setColor(activeColor);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(3.5f));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(trackColor);
    }

    public void setColors(int active, int track) {
        this.activeColor = active;
        this.trackColor = track;
        wavePaint.setColor(active);
        trackPaint.setColor(track);
        invalidate();
    }

    public void setProgress(float prog) {
        this.progress = Math.max(0f, Math.min(1f, prog));
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    public void setAnimating(boolean animating) {
        if (this.isAnimating != animating) {
            this.isAnimating = animating;
            if (animating) {
                lastFrameTime = 0;
                invalidate();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrameTime = 0;
        if (isAnimating) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        long now = SystemClock.elapsedRealtime();
        float dt = lastFrameTime == 0 ? 0.016f : Math.min(0.05f, (now - lastFrameTime) / 1000f);
        lastFrameTime = now;

        // Smooth progress interpolation
        animatedProgress += (progress - animatedProgress) * Math.min(1f, dt * 10f);

        phase = (phase + 6f * dt) % TAU;

        float strokeW = dp(2.2f);
        wavePaint.setStrokeWidth(strokeW);
        trackPaint.setStrokeWidth(strokeW);

        float cy = getHeight() / 2f;
        float left = dp(4);
        float right = getWidth() - dp(4);
        float w = right - left;
        if (w <= 0) return;

        float x = left + animatedProgress * w;
        float amplitude = dp(2.0f);
        float lambda = dp(16.0f);
        float step = dp(1.5f);

        float waveEnd = Math.max(left, x);

        if (waveEnd > left + dp(1)) {
            path.rewind();
            boolean first = true;
            for (float px = left; ; px += step) {
                boolean last = px >= waveEnd;
                float cx = last ? waveEnd : px;
                float y = cy + amplitude * (float) Math.sin(TAU * cx / lambda - phase);
                if (first) {
                    path.moveTo(cx, y);
                    first = false;
                } else {
                    path.lineTo(cx, y);
                }
                if (last) break;
            }
            canvas.drawPath(path, wavePaint);
        }

        // Draw remaining inactive track
        float stopX = right;
        float restX = Math.min(stopX, waveEnd + dp(4));
        if (restX < stopX) {
            canvas.drawLine(restX, cy, stopX, cy, trackPaint);
            // End dot on inactive track
            canvas.drawCircle(stopX, cy, strokeW / 2f, trackPaint);
        }

        if (isAnimating && getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }
}
