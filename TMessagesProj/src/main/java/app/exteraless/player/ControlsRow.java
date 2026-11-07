package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import app.exteraless.appearance.AppearanceConfig;

public class ControlsRow extends ViewGroup implements MorphButton.PressListener {

    public final MorphButton shuffle;
    public final MorphButton prev;
    public final MorphButton play;
    public final MorphButton next;
    public final MorphButton repeat;
    private final MorphButton[] buttons;
    private MorphButton pressed;
    private boolean playing;
    private boolean shuffleOn;
    private boolean repeatOn;
    private float scale = 1f;
    private long lastFrame;
    private boolean ticking;
    private final Runnable tick = this::tick;

    private LiquidGlassHelper.GlassPane glassBarPane;
    private LiquidGlassHelper.BackdropDrawer backdropDrawer;
    private int backdropWidth;
    private int backdropHeight;
    private PlayerColors colors;

    public ControlsRow(Context context) {
        super(context);
        setWillNotDraw(false);
        shuffle = new MorphButton(context, PlayerIcon.stroke(PlayerIcon.SHUFFLE, 22));
        prev = new MorphButton(context, PlayerIcon.fill(PlayerIcon.PREV, 28));
        play = new MorphButton(context, PlayerIcon.fill(PlayerIcon.PLAY, 36));
        play.setAltIcon(PlayerIcon.fill(PlayerIcon.PAUSE, 36));
        next = new MorphButton(context, PlayerIcon.fill(PlayerIcon.NEXT, 28));
        repeat = new MorphButton(context, PlayerIcon.stroke(PlayerIcon.REPEAT, 22));
        buttons = new MorphButton[]{shuffle, prev, play, next, repeat};
        float[] widths = {48, 64, 88, 64, 48};
        float[] heights = {48, 64, 88, 64, 48};
        for (int i = 0; i < buttons.length; i++) {
            MorphButton b = buttons[i];
            b.width.snap(dp(widths[i]));
            b.baseHeight = dp(heights[i]);
            b.setPressListener(this);
            addView(b);
        }
        shuffle.setRadius(dp(24), false);
        repeat.setRadius(dp(24), false);
        prev.setRadius(dp(32), false);
        next.setRadius(dp(32), false);
        play.setRadius(dp(44), false);
    }

    public void setBackdropSource(LiquidGlassHelper.BackdropDrawer drawer, int width, int height) {
        backdropDrawer = drawer;
        backdropWidth = width;
        backdropHeight = height;
        invalidate();
    }

    public void setColors(PlayerColors c) {
        colors = c;
        updateButtonColors();
    }

    public void updateButtonColors() {
        if (colors == null) return;
        boolean groupedGlass = AppearanceConfig.playerLiquidGlass() && AppearanceConfig.playerGroupedControls();
        int prevNextBg = groupedGlass ? 0 : colors.primaryContainer;
        int prevNextFg = groupedGlass ? colors.onSurface : colors.onPrimaryContainer;
        shuffle.setColors(0, colors.onSurfaceVariant, colors.secondaryContainer, colors.onSecondaryContainer);
        repeat.setColors(0, colors.onSurfaceVariant, colors.secondaryContainer, colors.onSecondaryContainer);
        repeat.setBadgeColors(colors.primary, colors.onPrimary);
        prev.setColors(prevNextBg, prevNextFg, colors.primaryContainer, colors.onPrimaryContainer);
        next.setColors(prevNextBg, prevNextFg, colors.primaryContainer, colors.onPrimaryContainer);
        play.setColors(colors.primary, colors.onPrimary, colors.primary, colors.onPrimary);
        invalidate();
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    public void setPlaying(boolean value, boolean animated) {
        playing = value;
        play.setAlt(value, animated);
        updateTargets(animated);
    }

    public void setModes(boolean shuffleActive, boolean repeatActive, boolean repeatOne, boolean animated) {
        shuffleOn = shuffleActive;
        repeatOn = repeatActive;
        shuffle.setActive(shuffleActive, animated);
        repeat.setActive(repeatActive, animated);
        repeat.setBadge(repeatOne ? "1" : null, animated);
        updateTargets(animated);
    }

    @Override
    public void onPressChanged(MorphButton button, boolean isPressed) {
        if (isPressed) {
            pressed = button;
        } else if (pressed == button) {
            pressed = null;
        }
        updateTargets(true);
    }

    private void updateTargets(boolean animated) {
        float prevW = pressed == prev ? 76 : pressed == play ? 58 : 64;
        float nextW = pressed == next ? 76 : pressed == play ? 58 : 64;
        float playW = pressed == play ? 100 : (pressed == prev || pressed == next) ? 80 : 88;
        setWidth(prev, dp(prevW), animated);
        setWidth(next, dp(nextW), animated);
        setWidth(play, dp(playW), animated);
        prev.setRadius(dp(pressed == prev ? 18 : 32), animated);
        next.setRadius(dp(pressed == next ? 18 : 32), animated);
        play.setRadius(dp(pressed == play ? 20 : playing ? 28 : 44), animated);
        shuffle.setRadius(dp(shuffleOn ? 14 : 24), animated);
        repeat.setRadius(dp(repeatOn ? 14 : 24), animated);
        if (animated) {
            startTick();
        } else if (getWidth() > 0) {
            measureButtons();
            layoutButtons(getWidth(), getHeight());
        }
    }

    private void setWidth(MorphButton b, float w, boolean animated) {
        if (animated) {
            b.width.target = w;
        } else {
            b.width.snap(w);
        }
    }

    private void startTick() {
        if (!ticking) {
            ticking = true;
            lastFrame = 0;
            postOnAnimation(tick);
        }
    }

    private void tick() {
        long now = SystemClock.elapsedRealtime();
        float dt = lastFrame == 0 ? 0.016f : (now - lastFrame) / 1000f;
        lastFrame = now;
        boolean animating = false;
        for (MorphButton b : buttons) {
            animating |= b.width.step(dt);
        }
        if (getWidth() > 0) {
            measureButtons();
            layoutButtons(getWidth(), getHeight());
        } else {
            requestLayout();
        }
        if (animating) {
            postOnAnimation(tick);
        } else {
            ticking = false;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
        ticking = false;
        for (MorphButton b : buttons) {
            b.width.snap(b.width.target);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        scale = Math.min(1f, (width - dp(16)) / (float) dp(312));
        int height = Math.round(dp(88) * scale);
        measureButtons();
        setMeasuredDimension(width, height);
    }

    private void measureButtons() {
        for (MorphButton b : buttons) {
            b.scale = scale;
            int w = Math.max(1, Math.round(b.width.value * scale));
            int h = Math.max(1, Math.round(b.baseHeight * scale));
            b.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutButtons(r - l, b - t);
    }

    private void layoutButtons(int width, int height) {
        float total = 0;
        for (MorphButton button : buttons) {
            total += button.getMeasuredWidth();
        }
        float gap = (width - total) / (buttons.length - 1f);
        float x = 0;
        for (MorphButton button : buttons) {
            int w = button.getMeasuredWidth();
            int h = button.getMeasuredHeight();
            int left = Math.round(x);
            int top = (height - h) / 2;
            button.layout(left, top, left + w, top + h);
            x += w + gap;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (AppearanceConfig.playerLiquidGlass() && AppearanceConfig.playerGroupedControls() && buttons.length >= 5 && getWidth() > 0) {
            if (glassBarPane == null) {
                glassBarPane = new LiquidGlassHelper.GlassPane();
            }
            float barLeft = buttons[0].getLeft() - dp(8) * scale;
            float barRight = buttons[4].getRight() + dp(8) * scale;
            float barH = dp(64) * scale;
            float barTop = (getHeight() - barH) * 0.5f;
            float barBottom = barTop + barH;
            float radius = dpf2(32f) * scale;

            float intensity = AppearanceConfig.playerGlassIntensityFloat();
            boolean still = LiquidGlassHelper.isBatterySaverStill();
            LiquidGlassHelper.GlassSpec glass = new LiquidGlassHelper.GlassSpec(intensity, still);

            int tint = colors != null ? glass.floatingTint(colors.surfaceContainer) : 0x40ffffff;
            int stillFill = colors != null ? glass.stillFill(colors.surfaceContainer) : 0xf0202020;

            int[] loc = new int[2];
            getLocationInWindow(loc);

            glassBarPane.draw(
                    canvas,
                    barLeft, barTop, barRight, barBottom,
                    radius,
                    backdropDrawer,
                    loc[0], loc[1],
                    backdropWidth > 0 ? backdropWidth : getWidth(),
                    backdropHeight > 0 ? backdropHeight : getHeight(),
                    true,
                    dpf2(4f),
                    dpf2(24f) * glass.lensT,
                    dpf2(32f) * glass.lensT,
                    true,
                    true,
                    true,
                    tint,
                    stillFill,
                    still
            );
        }
        super.dispatchDraw(canvas);
    }
}
