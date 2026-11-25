package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import java.util.Calendar;
import java.util.Locale;

/**
 * Sleep timer bottom sheet: round dial (0..120 min), rolling minutes counter,
 * "stops at" clock, preset chips, finish-song / fade-out toggles.
 */
public class SleepTimerSheet extends BottomSheet {

    private static final int MAX_MIN = 120;
    private static final int[] PRESETS = {5, 10, 15, 30, 45, 60, 90, 120};

    private final int textColor;
    private final int subColor;
    private final int accent;
    private final int onAccent;
    private final int bgColor;

    private int minutes;
    private final AnimatedTextView valueText;
    private final AnimatedTextView stopsAtText;
    private final DialView dial;
    private final TextView[] chips = new TextView[PRESETS.length];

    public SleepTimerSheet(Context context, Theme.ResourcesProvider rp) {
        super(context, false, rp);
        fixNavigationBar();

        bgColor = Theme.getColor(Theme.key_dialogBackground, rp);
        textColor = Theme.getColor(Theme.key_dialogTextBlack, rp);
        subColor = Theme.getColor(Theme.key_dialogTextGray2, rp);
        int acc = Theme.getColor(Theme.key_featuredStickers_addButton, rp);
        accent = acc != 0 ? acc : textColor;
        onAccent = AndroidUtilities.computePerceivedBrightness(accent) > 0.6f ? 0xFF111111 : 0xFFFFFFFF;

        SleepTimer timer = SleepTimer.getInstance();
        if (timer.isRunning() && !timer.isEndOfTrack()) {
            minutes = Math.max(1, (int) Math.ceil(timer.getRemainingMs() / 60000.0));
        } else {
            minutes = timer.getLastMinutes();
        }
        minutes = Math.max(1, Math.min(MAX_MIN, minutes));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(12));

        // ---- Header ----
        FrameLayout header = new FrameLayout(context);
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView overline = new TextView(context);
        overline.setText(getString(R.string.OESleepPlayback).toUpperCase(Locale.getDefault()));
        overline.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        overline.setLetterSpacing(0.12f);
        overline.setTypeface(AndroidUtilities.bold());
        overline.setTextColor(subColor);
        titles.addView(overline);
        TextView title = new TextView(context);
        title.setText(getString(R.string.OEAppearancePlayerSleepTimer));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        title.setTextColor(textColor);
        titles.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        header.addView(titles, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        ImageView moon = new ImageView(context);
        PlayerIcon moonIcon = PlayerIcon.stroke(PlayerIcon.SLEEP, 20);
        moonIcon.setColor(textColor);
        moon.setImageDrawable(moonIcon);
        moon.setScaleType(ImageView.ScaleType.CENTER);
        moon.setBackground(Theme.createCircleDrawable(dp(40), Theme.multAlpha(textColor, 0.08f)));
        header.addView(moon, LayoutHelper.createFrame(40, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 10));

        // ---- Dial ----
        FrameLayout dialFrame = new FrameLayout(context);
        dial = new DialView(context);
        dialFrame.addView(dial, LayoutHelper.createFrame(200, 200, Gravity.CENTER));

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        valueText = new AnimatedTextView(context, false, true, true);
        valueText.setAnimationProperties(0.6f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        valueText.setTextSize(dp(44));
        valueText.setTypeface(AndroidUtilities.bold());
        valueText.setTextColor(textColor);
        valueText.setGravity(Gravity.CENTER);
        center.addView(valueText, LayoutHelper.createLinear(120, 52, Gravity.CENTER_HORIZONTAL));
        TextView minLabel = new TextView(context);
        minLabel.setText(getString(R.string.OESleepMin));
        minLabel.setTypeface(Typeface.MONOSPACE);
        minLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        minLabel.setTextColor(subColor);
        center.addView(minLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        dialFrame.addView(center, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        root.addView(dialFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 210, 0, 0, 0, 0));

        // ---- Stops at ----
        stopsAtText = new AnimatedTextView(context, false, true, false);
        stopsAtText.setAnimationProperties(0.4f, 0, 260, CubicBezierInterpolator.EASE_OUT_QUINT);
        stopsAtText.setTextSize(dp(11));
        stopsAtText.setTypeface(Typeface.MONOSPACE);
        stopsAtText.setTextColor(subColor);
        stopsAtText.setGravity(Gravity.CENTER);
        root.addView(stopsAtText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20, 0, 6, 0, 12));

        // ---- Chips ----
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout chipRow = new LinearLayout(context);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < PRESETS.length; i++) {
            final int value = PRESETS[i];
            TextView chip = new TextView(context);
            chip.setText(value >= 60 && value % 60 == 0 ? (value / 60) + "h" : value + "m");
            chip.setTypeface(Typeface.MONOSPACE);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), 0, dp(12), 0);
            chip.setOnClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                setMinutes(value, true);
            });
            chips[i] = chip;
            chipRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, 6, 0));
        }
        scroll.addView(chipRow);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 32, 0, 0, 0, 14));

        // ---- Toggles ----
        root.addView(toggleRow(context, rp, PlayerIcon.NOTE, getString(R.string.OESleepFinishSong), getString(R.string.OESleepFinishSongDesc),
                timer.isFinishSong(), timer::setFinishSong), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 0, 0, 4));
        root.addView(toggleRow(context, rp, "M11 5L6 9H2v6h4l5 4V5zM15.5 8.5a5 5 0 0 1 0 7M19 5a10 10 0 0 1 0 14",
                getString(R.string.OESleepFadeOut), getString(R.string.OESleepFadeOutDesc),
                timer.isFadeOut(), timer::setFadeOut), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 0, 0, 0, 14));

        // ---- Buttons ----
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = pill(context, timer.isRunning() ? getString(R.string.OEAppearancePlayerSleepTimerReset) : getString(R.string.Cancel),
                Theme.multAlpha(textColor, 0.08f), textColor);
        cancel.setOnClickListener(v -> {
            if (SleepTimer.getInstance().isRunning()) {
                SleepTimer.getInstance().cancel();
            }
            dismiss();
        });
        TextView start = pill(context, getString(R.string.OESleepStart), accent, onAccent);
        start.setOnClickListener(v -> {
            SleepTimer.getInstance().start(minutes);
            dismiss();
        });
        buttons.addView(cancel, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 6, 0));
        buttons.addView(start, LayoutHelper.createLinear(0, 44, 1f, 6, 0, 0, 0));
        root.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));

        setCustomView(root);
        setBackgroundColor(bgColor);
        setMinutes(minutes, false);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return dial == null || !dial.tracking;
    }

    private interface BoolSink {
        void set(boolean v);
    }

    private View toggleRow(Context context, Theme.ResourcesProvider rp, String iconPath, String titleText, String subText, boolean checked, BoolSink sink) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.multAlpha(textColor, 0.05f));
        bg.setCornerRadius(dp(16));
        row.setBackground(bg);

        ImageView icon = new ImageView(context);
        PlayerIcon d = PlayerIcon.stroke(iconPath, 16);
        d.setColor(textColor);
        icon.setImageDrawable(d);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setBackground(Theme.createCircleDrawable(dp(32), Theme.multAlpha(textColor, 0.08f)));
        row.addView(icon, LayoutHelper.createLinear(32, 32, 0, 0, 12, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(context);
        t.setText(titleText);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(textColor);
        texts.addView(t);
        TextView s = new TextView(context);
        s.setText(subText);
        s.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        s.setTextColor(subColor);
        texts.addView(s);
        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        Switch sw = new Switch(context, rp);
        sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        sw.setChecked(checked, false);
        row.addView(sw, LayoutHelper.createLinear(37, 40));
        row.setOnClickListener(v -> {
            boolean nv = !sw.isChecked();
            sw.setChecked(nv, true);
            sink.set(nv);
        });
        return row;
    }

    private TextView pill(Context context, String text, int bg, int fg) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(fg);
        tv.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(22), bg, Theme.multAlpha(fg, 0.15f)));
        return tv;
    }

    private void setMinutes(int value, boolean animated) {
        value = Math.max(1, Math.min(MAX_MIN, value));
        minutes = value;
        valueText.setText(String.valueOf(value), animated);
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MINUTE, value);
        String time = String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        String prefix = getString(R.string.OESleepStopsAt).toUpperCase(Locale.getDefault()) + "  ";
        SpannableStringBuilder sb = new SpannableStringBuilder(prefix + time);
        sb.setSpan(new StyleSpan(Typeface.BOLD), prefix.length(), sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        stopsAtText.setText(sb, animated);
        for (int i = 0; i < chips.length; i++) {
            boolean sel = PRESETS[i] == value;
            chips[i].setTextColor(sel ? onAccent : textColor);
            chips[i].setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16), sel ? accent : Theme.multAlpha(textColor, 0.06f), Theme.multAlpha(textColor, 0.12f)));
        }
        dial.setValue(value, animated);
    }

    private class DialView extends View {
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF arc = new RectF();
        private float shown = -1;
        private float target;
        boolean tracking;

        DialView(Context context) {
            super(context);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(dp(4));
            trackPaint.setColor(Theme.multAlpha(textColor, 0.12f));
            activePaint.setStyle(Paint.Style.STROKE);
            activePaint.setStrokeWidth(dp(4));
            activePaint.setStrokeCap(Paint.Cap.ROUND);
            activePaint.setColor(accent);
            tickPaint.setStrokeWidth(dp(1.2f));
            tickPaint.setStrokeCap(Paint.Cap.ROUND);
            labelPaint.setTextSize(dp(8));
            labelPaint.setTypeface(Typeface.MONOSPACE);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setColor(subColor);
            thumbPaint.setColor(accent);
            thumbDotPaint.setColor(bgColor);
        }

        void setValue(int v, boolean animated) {
            target = v;
            if (!animated || shown < 0 || tracking) {
                shown = v;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (Math.abs(shown - target) > 0.05f) {
                shown += (target - shown) * 0.25f;
                postInvalidateOnAnimation();
            } else {
                shown = target;
            }
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - dp(12);
            arc.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawCircle(cx, cy, r, trackPaint);

            // ticks every 5 minutes
            for (int m = 0; m < MAX_MIN; m += 5) {
                double a = Math.toRadians(m * 3 - 90);
                boolean major = m % 15 == 0;
                float r1 = r - dp(9), r2 = r - dp(major ? 16 : 13);
                tickPaint.setColor(Theme.multAlpha(textColor, major ? 0.45f : 0.22f));
                canvas.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                        cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, tickPaint);
            }
            drawLabel(canvas, cx, cy, r, 0, "2h");
            drawLabel(canvas, cx, cy, r, 5, "5");
            drawLabel(canvas, cx, cy, r, 15, "15");
            drawLabel(canvas, cx, cy, r, 30, "30");
            drawLabel(canvas, cx, cy, r, 45, "45");
            drawLabel(canvas, cx, cy, r, 60, "1h");
            drawLabel(canvas, cx, cy, r, 90, "90");

            float sweep = shown / MAX_MIN * 360f;
            canvas.drawArc(arc, -90, sweep, false, activePaint);
            double ta = Math.toRadians(sweep - 90);
            float tx = cx + (float) Math.cos(ta) * r, ty = cy + (float) Math.sin(ta) * r;
            canvas.drawCircle(tx, ty, dp(tracking ? 11 : 9), thumbPaint);
            canvas.drawCircle(tx, ty, dp(3), thumbDotPaint);
        }

        private void drawLabel(Canvas canvas, float cx, float cy, float r, int m, String text) {
            double a = Math.toRadians(m * 3 - 90);
            float lr = r - dp(m == 0 || m == 5 ? 24 : 26);
            float x = cx + (float) Math.cos(a) * lr;
            float y = cy + (float) Math.sin(a) * lr - (labelPaint.descent() + labelPaint.ascent()) / 2f;
            if (m == 0) x -= dp(6);
            if (m == 5) x += dp(4);
            canvas.drawText(text, x, y, labelPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    float d = (float) Math.hypot(e.getX() - cx, e.getY() - cy);
                    if (d < Math.min(cx, cy) * 0.45f) return false;
                    tracking = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    handle(e.getX() - cx, e.getY() - cy);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (tracking) handle(e.getX() - cx, e.getY() - cy);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    tracking = false;
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(e);
        }

        private void handle(float dx, float dy) {
            double deg = Math.toDegrees(Math.atan2(dy, dx)) + 90;
            if (deg < 0) deg += 360;
            int v = (int) Math.round(deg / 3.0);
            // prevent wrapping across the top
            if (minutes > 90 && v < 30) v = MAX_MIN;
            else if (minutes < 30 && v > 90) v = 1;
            if (v <= 0) v = minutes > 60 ? MAX_MIN : 1;
            v = Math.max(1, Math.min(MAX_MIN, v));
            if (v != minutes) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                setMinutes(v, true);
            }
        }
    }
}
