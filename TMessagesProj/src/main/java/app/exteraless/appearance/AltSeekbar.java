package app.exteraless.appearance;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
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
 * Слайдер экстеры: заголовок с «пилюлей» текущего значения, подписи по краям
 * и вибро-отклик на упорах. Порт
 * {@code com.exteragram.messenger.preferences.components.AltSeekbar} (12.9.0).
 *
 * Работает в единицах настройки, а не в долях: {@link #setProgress(float)} принимает
 * значение из диапазона [min..max] — так же, как у exteraGram, и так его зовут превью
 * (SliderPreviewCell:52). Не путать с {@link AvatarCornersSeekBar}, у которого
 * setProgress принимает 0..1; тот появился раньше и остаётся ради экранов,
 * которые уже на него завязаны.
 *
 * Ветку exteraGram с {@code com.google.android.material.slider.Slider} перенести
 * нельзя — библиотеки Material Components в дереве нет; MD3-вид слайдера у нас
 * рисует сам {@link SeekBarView} (SeekBarView.java:469).
 */
@SuppressLint("ViewConstructor")
public class AltSeekbar extends FrameLayout {

    public interface OnDrag {
        void run(float value);
    }

    private final OnDrag onDrag;

    protected final AnimatedTextView headerValue;
    protected final TextView leftTextView;
    protected final TextView rightTextView;
    public final SeekBarView seekBarView;

    protected final int min;
    protected final int max;

    protected float currentValue;
    protected int roundedValue;

    private int vibro = -1;

    public AltSeekbar(Context context, OnDrag onDrag, int min, int max,
                      String title, String left, String right) {
        super(context);
        this.onDrag = onDrag;
        this.min = min;
        this.max = max;

        LinearLayout headerLayout = new LinearLayout(context);
        headerLayout.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

        TextView headerTextView = new TextView(context);
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        headerTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        headerTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        headerTextView.setText(title);
        headerLayout.addView(headerTextView, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

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
        headerValue.setAllowCancel(true);
        headerValue.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        headerValue.setPadding(dp(5.33f), dp(2), dp(5.33f), dp(2));
        headerValue.setTextSize(dp(12));
        headerValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        headerLayout.addView(headerValue, LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, 17, Gravity.CENTER_VERTICAL, 6, 1, 0, 0));

        addView(headerLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 17, 21, 0));

        FrameLayout valuesView = new FrameLayout(context);

        leftTextView = new TextView(context);
        leftTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        leftTextView.setGravity(Gravity.LEFT);
        leftTextView.setText(left);
        valuesView.addView(leftTextView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        rightTextView = new TextView(context);
        rightTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        rightTextView.setGravity(Gravity.RIGHT);
        rightTextView.setText(right);
        valuesView.addView(rightTextView, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        addView(valuesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.FILL_HORIZONTAL, 21, 48, 21, 0));

        seekBarView = new SeekBarView(context, true, null);
        seekBarView.setReportChanges(true);
        seekBarView.setDelegate((stop, progress) -> {
            float value = this.min + (this.max - this.min) * progress;
            if (this.onDrag != null) {
                this.onDrag.run(value);
            }
            if (Math.round(value) != roundedValue) {
                setProgress(value);
            }
        });
        addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44,
                Gravity.TOP, 16, 76, 16, 12));

        // Стартуем с min, а не с нуля: у exteraGram поле currentValue инициализировано нулём
        // и при min > 0 ползунок на миг уезжает за левый край. Реальное значение
        // владелец выставляет своим setProgress сразу после конструктора.
        setProgress(min);
    }

    /**
     * @param value значение настройки в диапазоне [min..max]
     */
    public void setProgress(float value) {
        currentValue = value;
        roundedValue = Math.round(value);
        seekBarView.setProgress(max == min ? 0f : (value - min) / (float) (max - min));
        headerValue.cancelAnimation();
        headerValue.setText(getTextForHeader(), true);
        checkEndpointHaptic(value);
        updateValues();
    }

    /** Обновить только подписи: сам ползунок уже стоит там, где надо (идёт перетаскивание). */
    public void updateHeader(float value) {
        currentValue = value;
        roundedValue = Math.round(value);
        CharSequence text = getTextForHeader();
        if (!TextUtils.equals(headerValue.getText(), text)) {
            headerValue.setText(text, true);
        }
        checkEndpointHaptic(value);
        updateValues();
    }

    /**
     * true — щёлкать по упору только при точном попадании в min/max, а не при
     * округлении к нему. Нужно слайдерам с дробным шагом.
     */
    public boolean useExactEndpointHaptic() {
        return false;
    }

    /** Текст «пилюли»: на краях — подпись края, между — само число. */
    public CharSequence getTextForHeader() {
        CharSequence text;
        if (roundedValue == min) {
            text = leftTextView.getText();
        } else if (roundedValue == max) {
            text = rightTextView.getText();
        } else {
            text = String.valueOf(roundedValue);
        }
        return text.toString().toUpperCase();
    }

    private void checkEndpointHaptic(float value) {
        int endpoint = -1;
        if (useExactEndpointHaptic()) {
            if (value <= min) {
                endpoint = min;
            } else if (value >= max) {
                endpoint = max;
            }
        } else if (roundedValue == min || roundedValue == max) {
            endpoint = roundedValue;
        }
        if (endpoint == -1) {
            vibro = -1;
            return;
        }
        if (endpoint != vibro) {
            vibro = endpoint;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }

    /** Подпись края подсвечивается акцентом по мере приближения ползунка к ней. */
    protected void updateValues() {
        int middle = (max - min) / 2 + min;
        float rightThreshold = middle * 1.5f - min * 0.5f;
        float leftThreshold = (middle + min) * 0.5f;
        if (currentValue >= rightThreshold) {
            rightTextView.setTextColor(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                    (currentValue - rightThreshold) / (max - rightThreshold)));
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else if (currentValue <= leftThreshold) {
            leftTextView.setTextColor(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText),
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                    (currentValue - leftThreshold) / (min - leftThreshold)));
            rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else {
            leftTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            rightTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (leftTextView == null || seekBarView == null) {
            return;
        }
        // Цвета подписей выставлены разово, поэтому смену темы доносим руками.
        updateValues();
        headerValue.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        seekBarView.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(134), MeasureSpec.EXACTLY));
    }
}
