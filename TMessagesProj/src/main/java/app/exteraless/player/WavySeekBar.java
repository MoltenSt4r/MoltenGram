package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.CubicBezierInterpolator;

public class WavySeekBar extends View {

    public interface Delegate {
        void onSeekStart();

        void onSeekMove(float progress);

        void onSeekEnd(float progress, boolean commit);
    }

    private static final float TAU = (float) (Math.PI * 2);

    private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private Delegate delegate;
    private float progress;
    private float dragProgress;
    private boolean dragging;
    private boolean playing;
    private float amplitude;
    private float phase;
    private long lastFrame;
    private float maxAmplitude = dp(4);

    private ValueAnimator returnAnimator;
    private float returnProgress;
    private boolean animatingReturn;

    private ValueAnimator resetAnimator;
    private float resetProgress;
    private boolean animatingReset;

    public WavySeekBar(Context context) {
        super(context);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(dp(4));
        wavePaint.setStrokeCap(Paint.Cap.ROUND);
        wavePaint.setStrokeJoin(Paint.Join.ROUND);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(dp(4));
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(int active, int inactive) {
        wavePaint.setColor(active);
        fillPaint.setColor(active);
        trackPaint.setColor(inactive);
        invalidate();
    }

    public void setMaxAmplitude(float px) {
        maxAmplitude = px;
        invalidate();
    }

    private void cancelAnimations() {
        if (returnAnimator != null) {
            returnAnimator.cancel();
            returnAnimator = null;
        }
        animatingReturn = false;
        if (resetAnimator != null) {
            resetAnimator.cancel();
            resetAnimator = null;
        }
        animatingReset = false;
    }

    public void startBounceReturn(float from, float to) {
        cancelAnimations();
        animatingReturn = true;
        returnProgress = from;
        returnAnimator = ValueAnimator.ofFloat(from, to);
        returnAnimator.setDuration(320);
        returnAnimator.setInterpolator(new OvershootInterpolator(1.4f));
        returnAnimator.addUpdateListener(anim -> {
            returnProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        returnAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatingReturn = false;
                returnAnimator = null;
                invalidate();
            }
        });
        returnAnimator.start();
    }

    public void animateResetToZero() {
        if (progress <= 0.02f || dragging) {
            progress = 0f;
            invalidate();
            return;
        }
        cancelAnimations();
        animatingReset = true;
        resetProgress = progress;
        resetAnimator = ValueAnimator.ofFloat(progress, 0f);
        resetAnimator.setDuration(300);
        resetAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        resetAnimator.addUpdateListener(anim -> {
            resetProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        resetAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animatingReset = false;
                resetAnimator = null;
                progress = 0f;
                invalidate();
            }
        });
        resetAnimator.start();
    }

    public void cancelSeek() {
        if (dragging) {
            dragging = false;
            float from = dragProgress;
            float to = progress;
            if (delegate != null) {
                delegate.onSeekEnd(progress, false);
            }
            startBounceReturn(from, to);
        }
    }

    public void setProgress(float value) {
        if (dragging) {
            return;
        }
        if (animatingReturn) {
            progress = value;
            return;
        }
        if (animatingReset) {
            if (value > 0.05f) {
                cancelAnimations();
                progress = value;
                invalidate();
            }
            return;
        }
        if (progress > 0.15f && value < 0.03f) {
            animateResetToZero();
            return;
        }
        if (progress != value) {
            progress = value;
            invalidate();
        }
    }

    public float getProgress() {
        return dragging ? dragProgress : progress;
    }

    public void setPlaying(boolean value) {
        if (playing != value) {
            playing = value;
            lastFrame = 0;
            invalidate();
        }
    }

    public boolean isDragging() {
        return dragging;
    }

    private float left() {
        return dp(3);
    }

    private float right() {
        return getWidth() - dp(3);
    }

    private float progressAt(float x) {
        float w = right() - left();
        if (w <= 0) {
            return 0;
        }
        return Math.max(0f, Math.min(1f, (x - left()) / w));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelAnimations();
                dragging = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                dragProgress = progressAt(event.getX());
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (delegate != null) {
                    delegate.onSeekStart();
                    delegate.onSeekMove(dragProgress);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    dragProgress = progressAt(event.getX());
                    if (delegate != null) {
                        delegate.onSeekMove(dragProgress);
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    dragging = false;
                    progress = dragProgress;
                    if (delegate != null) {
                        delegate.onSeekEnd(progress, true);
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    float from = dragProgress;
                    float to = progress;
                    if (delegate != null) {
                        delegate.onSeekEnd(progress, false);
                    }
                    startBounceReturn(from, to);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        long now = SystemClock.elapsedRealtime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        int style = app.exteraless.appearance.AppearanceConfig.playerSeekbarStyle();
        // 0: Default (Thick track + pill thumb)
        // 1: Wavy (Wavy track + round dot thumb)
        // 2: Slim (Thin track + no thumb)
        // 3: Squiggly (Wavy track + vertical bar thumb)
        boolean isWavy = style == 1 || style == 3;
        float targetAmp = (playing && isWavy) ? maxAmplitude : 0f;
        amplitude += (targetAmp - amplitude) * (1f - (float) Math.pow(0.85, dt / 0.04f));
        if (Math.abs(amplitude - targetAmp) < dpf2(0.05f)) {
            amplitude = targetAmp;
        }
        if (amplitude > 0f) {
            phase = (phase + 6f * dt) % TAU;
        }

        float strokeW = style == 0 ? dp(8) : dp(3.5f);
        wavePaint.setStrokeWidth(strokeW);
        trackPaint.setStrokeWidth(strokeW);

        float cy = getHeight() / 2f;
        float left = left();
        float right = right();
        float p;
        if (dragging) {
            p = dragProgress;
        } else if (animatingReturn) {
            p = returnProgress;
        } else if (animatingReset) {
            p = resetProgress;
        } else {
            p = progress;
        }

        float thumbW = dragging ? dp(3) : dp(5);
        float x = left + p * (right - left);
        float gap = (style == 2) ? 0 : dp(5);
        float thumbRadius = (style == 1) ? dp(7) : (thumbW / 2f);
        float waveEnd = x - gap - thumbRadius;
        float lambda = dp(34);
        float step = dp(2);

        if (waveEnd > left) {
            if (amplitude > 0f && isWavy) {
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
                    if (last) {
                        break;
                    }
                }
                canvas.drawPath(path, wavePaint);
            } else {
                canvas.drawLine(left, cy, waveEnd, cy, wavePaint);
            }
        }

        float stopX = right - dp(1);
        float restX = Math.min(stopX, x + thumbRadius + gap);
        if (restX < stopX) {
            canvas.drawLine(restX, cy, stopX, cy, trackPaint);
        }

        if (style == 0) {
            // Default: thick track, vertical pill thumb, dot at end
            canvas.drawCircle(stopX, cy, dp(2.5f), fillPaint);
            rect.set(x - thumbW / 2f, cy - dp(12), x + thumbW / 2f, cy + dp(12));
            canvas.drawRoundRect(rect, dpf2(2f), dpf2(2f), fillPaint);
        } else if (style == 1) {
            // Wavy: circular round dot thumb, dot at end
            canvas.drawCircle(stopX, cy, dp(2), fillPaint);
            canvas.drawCircle(x, cy, dp(7), fillPaint);
        } else if (style == 2) {
            // Slim: smooth line, no thumb
            if (dragging) {
                canvas.drawCircle(x, cy, dp(3.5f), fillPaint);
            }
        } else {
            // Squiggly (style 3): wavy line with vertical bar thumb
            canvas.drawCircle(stopX, cy, dp(2), fillPaint);
            rect.set(x - thumbW / 2f, cy - dp(9), x + thumbW / 2f, cy + dp(9));
            canvas.drawRoundRect(rect, dpf2(1.5f), dpf2(1.5f), fillPaint);
        }

        if (amplitude > 0f || targetAmp != amplitude) {
            postInvalidateOnAnimation();
        } else {
            lastFrame = 0;
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0, 100, getProgress() * 100f));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            float value = Math.max(0f, Math.min(1f, progress + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 0.05f : -0.05f)));
            progress = value;
            if (delegate != null) {
                delegate.onSeekEnd(value, true);
            }
            invalidate();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }
}
