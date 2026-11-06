package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;

@SuppressLint("ViewConstructor")
public class VoIpGradientLayout extends FrameLayout {

    public enum GradientState {
        CALLING,
        CONNECTED,
        BAD_CONNECTION
    }

    private int alphaOrangeRed = 0;
    private float clipRadius = 0f;
    private boolean showClip = false;
    private final Path clipPath = new Path();
    private int clipCx = 0;
    private int clipCy = 0;
    private ValueAnimator callingAnimator;
    private ValueAnimator badConnectionAnimator;
    private GradientState state;
    private boolean isPaused = false;
    public volatile boolean lockDrawing = false;
    private final VoIPBackgroundProvider backgroundProvider;
    private boolean allowAnimations;

    private float callingPulseProgress = 0f;
    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private LinearGradient gradientShader;
    private int lastW = -1;
    private int lastH = -1;
    private int lastTopColor = 0;
    private int lastBottomColor = 0;

    public VoIpGradientLayout(@NonNull Context context, boolean black, VoIPBackgroundProvider backgroundProvider) {
        super(context);
        this.backgroundProvider = backgroundProvider;
        allowAnimations = LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS);

        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        switchToCalling();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (callingAnimator != null) {
            callingAnimator.cancel();
        }
        if (badConnectionAnimator != null) {
            badConnectionAnimator.cancel();
        }
    }

    public void switchToCalling() {
        if (state == GradientState.CALLING) {
            return;
        }
        state = GradientState.CALLING;
        if (callingAnimator != null) {
            callingAnimator.cancel();
        }
        callingAnimator = ValueAnimator.ofFloat(0f, 1f);
        callingAnimator.addUpdateListener(animation -> {
            callingPulseProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        callingAnimator.setRepeatCount(ValueAnimator.INFINITE);
        callingAnimator.setRepeatMode(ValueAnimator.REVERSE);
        callingAnimator.setInterpolator(new LinearInterpolator());
        callingAnimator.setDuration(2200);
        if (allowAnimations) {
            callingAnimator.start();
        }
    }

    public boolean isConnectedCalled() {
        return state == GradientState.CONNECTED || state == GradientState.BAD_CONNECTION;
    }

    public void switchToCallConnected(int x, int y) {
        switchToCallConnected(x, y, true);
    }

    public void switchToCallConnected(int x, int y, boolean animated) {
        if (state == GradientState.CONNECTED || state == GradientState.BAD_CONNECTION) {
            return;
        }
        state = GradientState.CONNECTED;
        if (callingAnimator != null) {
            callingAnimator.removeAllUpdateListeners();
            callingAnimator.cancel();
            callingAnimator = null;
        }
        callingPulseProgress = 0f;
        clipCx = x;
        clipCy = y;
        int w = AndroidUtilities.displaySize.x;
        int h = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight + AndroidUtilities.navigationBarHeight;
        double d1 = Math.sqrt((w - x) * (w - x) + (h - y) * (h - y));
        double d2 = Math.sqrt(x * x + (h - y) * (h - y));
        double d3 = Math.sqrt(x * x + y * y);
        double d4 = Math.sqrt((w - x) * (w - x) + y * y);
        double revealMaxRadius = Math.max(Math.max(Math.max(d1, d2), d3), d4);

        showClip = true;
        backgroundProvider.setReveal(true);
        ValueAnimator revealAnimator = ValueAnimator.ofFloat(0f, (float) revealMaxRadius);
        revealAnimator.addUpdateListener(animation -> {
            clipRadius = (float) animation.getAnimatedValue();
            invalidate();
            backgroundProvider.invalidateViews();
        });
        revealAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                showClip = false;
                backgroundProvider.setReveal(false);
            }
        });
        revealAnimator.setDuration(animated ? 400 : 0);
        revealAnimator.start();
    }

    public void showToBadConnection() {
        if (state == GradientState.BAD_CONNECTION) {
            return;
        }
        state = GradientState.BAD_CONNECTION;
        if (badConnectionAnimator != null) {
            badConnectionAnimator.cancel();
        }
        badConnectionAnimator = ValueAnimator.ofInt(alphaOrangeRed, 255);
        badConnectionAnimator.addUpdateListener(animation -> {
            alphaOrangeRed = (int) animation.getAnimatedValue();
            invalidate();
            backgroundProvider.invalidateViews();
        });
        badConnectionAnimator.setDuration(500);
        badConnectionAnimator.start();
    }

    public void hideBadConnection() {
        if (state == GradientState.CONNECTED) {
            return;
        }
        state = GradientState.CONNECTED;
        if (badConnectionAnimator != null) {
            badConnectionAnimator.removeAllUpdateListeners();
            badConnectionAnimator.cancel();
        }
        badConnectionAnimator = ValueAnimator.ofInt(alphaOrangeRed, 0);
        badConnectionAnimator.addUpdateListener(animation -> {
            alphaOrangeRed = (int) animation.getAnimatedValue();
            invalidate();
            backgroundProvider.invalidateViews();
        });
        badConnectionAnimator.setDuration(500);
        badConnectionAnimator.start();
    }

    public void pause() {
        if (isPaused) {
            return;
        }
        isPaused = true;
        if (callingAnimator != null && callingAnimator.isRunning()) {
            callingAnimator.pause();
        }
    }

    public void resume() {
        if (!isPaused) {
            return;
        }
        isPaused = false;
        if (callingAnimator != null && callingAnimator.isPaused()) {
            callingAnimator.resume();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        backgroundProvider.setTotalSize(getWidth(), getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (lockDrawing) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        int surface = Theme.getColor(Theme.key_windowBackgroundWhite);
        int primary = Theme.getColor(Theme.key_chats_actionBackground);
        boolean isDark = Theme.isCurrentThemeDark();

        // 1. Flat MD3 tonal surface fill (no gradient bloom)
        int flatBgColor = ColorUtils.blendARGB(surface, primary, isDark ? 0.14f : 0.08f);
        if (alphaOrangeRed > 0) {
            int warmAlert = ColorUtils.blendARGB(flatBgColor, 0xFFE28B29, isDark ? 0.18f : 0.12f);
            flatBgColor = ColorUtils.blendARGB(flatBgColor, warmAlert, alphaOrangeRed / 255f);
        }
        canvas.drawColor(flatBgColor);

        // 2. Circular reveal on call connected transition
        if (showClip && clipRadius > 0) {
            clipPath.rewind();
            clipPath.addCircle(clipCx, clipCy, clipRadius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawColor(flatBgColor);
            canvas.restore();
        }

        // 3. Update VoIPBackgroundProvider canvases for dock buttons and chips
        int buttonTonalColor = isDark
            ? ColorUtils.blendARGB(flatBgColor, Color.BLACK, 0.38f)
            : ColorUtils.blendARGB(flatBgColor, primary, 0.14f);
        int darkContainerColor = ColorUtils.setAlphaComponent(
            isDark ? Color.BLACK : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
            (int) (255 * (isDark ? 0.35f : 0.10f))
        );

        Canvas lightCanvas = backgroundProvider.getLightCanvas();
        if (lightCanvas != null) {
            lightCanvas.drawColor(buttonTonalColor);
        }
        Canvas darkCanvas = backgroundProvider.getDarkCanvas();
        if (darkCanvas != null) {
            darkCanvas.drawColor(darkContainerColor);
        }
        if (backgroundProvider.isReveal()) {
            Canvas revealCanvas = backgroundProvider.getRevealCanvas();
            if (revealCanvas != null) {
                revealCanvas.drawColor(buttonTonalColor);
            }
            Canvas revealDarkCanvas = backgroundProvider.getRevealDrakCanvas();
            if (revealDarkCanvas != null) {
                revealDarkCanvas.drawColor(darkContainerColor);
            }
        }

        super.onDraw(canvas);
    }
}
