package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

/**
 * Слайдер с заголовком и подписями по краям — как в exteraGram (AltSeekbar).
 * Используется для закругления аватарок: слева «Квадрат», справа «Круг».
 */
@SuppressLint("ViewConstructor")
public class AvatarCornersSeekBar extends FrameLayout {

    public interface OnDrag {
        void run(int value);
    }

    private final AnimatedTextView headerValue;
    private final TextView leftTextView;
    private final TextView rightTextView;
    public final SeekBarView seekBarView;

    private final int min, max;
    private float currentValue;
    private int roundedValue;
    private int vibro = -1;

    public AvatarCornersSeekBar(Context context, OnDrag onDrag, int min, int max,
                                String title, String left, String right) {
        super(context);

        this.max = max;
        this.min = min;

        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        TextView headerTextView = new TextView(context);
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        headerTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        headerTextView.setText(title);
        headerLayout.addView(headerTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        headerValue = new AnimatedTextView(context, false, true, true) {
            final Drawable backgroundDrawable = Theme.createRoundRectDrawable(dp(4),
                    Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), 0.15f));

            @Override
            protected void onDraw(Canvas canvas) {
                backgroundDrawable.setBounds(0, 0,
                        (int) (getPaddingLeft() + getDrawable().getCurrentWidth() + getPaddingRight()),
                        getMeasuredHeight());
                backgroundDrawable.draw(canvas);
                super.onDraw(canvas);
            }
        };
        headerValue.setAnimationProperties(.45f, 0, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
        headerValue.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        headerValue.setPadding(dp(5.33f), dp(2), dp(5.33f), dp(2));
        headerValue.setTextSize(dp(12));
        headerValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        headerLayout.addView(headerValue, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 17, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

        addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 17, 21, 0));

        seekBarView = new SeekBarView(context, true, null);
        seekBarView.setReportChanges(true);
        seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                float newValue = min + (max - min) * progress;
                if (Math.round(newValue) != roundedValue) {
                    setProgress(progress);
                    onDrag.run(roundedValue);
                }
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }
        });
        addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP, 16, 68, 16, 12));

        FrameLayout valuesView = new FrameLayout(context);

        leftTextView = new TextView(context);
        leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        leftTextView.setGravity(Gravity.LEFT);
        leftTextView.setText(left);
        valuesView.addView(leftTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        rightTextView = new TextView(context);
        rightTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        rightTextView.setGravity(Gravity.RIGHT);
        rightTextView.setText(right);
        valuesView.addView(rightTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        addView(valuesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 52, 21, 0));
    }

    private void updateValues() {
        int middle = (max - min) / 2 + min;
        if (currentValue >= middle * 1.5f - min * 0.5f) {
            rightTextView.setTextColor(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                    (currentValue - (middle * 1.5f - min * 0.5f)) / (max - (middle * 1.5f - min * 0.5f))
            ));
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else if (currentValue <= (middle + min) * 0.5f) {
            leftTextView.setTextColor(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                    (currentValue - (middle + min) * 0.5f) / (min - (middle + min) * 0.5f)
            ));
            rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else {
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
    }

    public void setValue(int value) {
        setProgress(max == min ? 0f : (value - min) / (float) (max - min));
    }

    public void setProgress(float progress) {
        currentValue = min + (max - min) * progress;
        roundedValue = Math.round(currentValue);
        seekBarView.setProgress(progress);
        headerValue.cancelAnimation();
        headerValue.setText(getTextForHeader(), true);
        if ((roundedValue == min || roundedValue == max) && roundedValue != vibro) {
            vibro = (int) currentValue;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } else if (roundedValue > min && roundedValue < max) {
            vibro = -1;
        }
        updateValues();
    }

    /**
     * Единица измерения для промежуточных значений, например «dp» у радиуса секций
     * (exteraGram: formatSectionRadius — 0 «Off», 28 «Max», иначе «N dp»).
     * Пусто у закругления аватарок, где число выводится без единиц.
     */
    private String valueSuffix = "";
    private float valueScale = 1f;

    public void setValueSuffix(String suffix) {
        valueSuffix = suffix == null ? "" : suffix;
    }

    public void setValueScale(float scale) {
        valueScale = scale > 0f ? scale : 1f;
    }

    public CharSequence getTextForHeader() {
        CharSequence text;
        if (roundedValue == min) {
            text = leftTextView.getText();
        } else if (roundedValue == max) {
            text = rightTextView.getText();
        } else if (valueScale != 1f) {
            String formatted = String.format(java.util.Locale.US, "%.1f", roundedValue * valueScale);
            text = valueSuffix.isEmpty() ? formatted : formatted + " " + valueSuffix;
        } else {
            text = valueSuffix.isEmpty() ? String.valueOf(roundedValue) : roundedValue + " " + valueSuffix;
        }
        // Верхний регистр нужен закруглению аватарок («CIRCLE»), но не радиусу секций:
        // у экстеры там «20 dp», «Off», «Max» как есть.
        return valueSuffix.isEmpty() && valueScale == 1f ? text.toString().toUpperCase() : text;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(126), MeasureSpec.EXACTLY)
        );
    }
}
