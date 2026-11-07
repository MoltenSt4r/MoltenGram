package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.SystemClock;
import android.view.ViewGroup;

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

    public ControlsRow(Context context) {
        super(context);
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
}
