package app.exteraless.glyph;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

/**
 * Centered device model card for OpenExteraGlyphActivity.
 * Displays the current device model image centered (same clean presentation for Nothing and CMF devices)
 * with robust drawable caching preventing the phone image from disappearing.
 */
public class GlyphDevicePreviewCell extends View {

    private String currentModel;
    private Drawable phoneDrawable;
    private int currentResId = 0;

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cardRect = new RectF();

    private ValueAnimator pulseAnimator;
    private float pulseScale = 1.0f;

    public GlyphDevicePreviewCell(Context context) {
        super(context);
        currentModel = NothingPhoneEasterEgg.getDeviceDisplayName();

        cardPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(AndroidUtilities.dp(14));
        titlePaint.setFakeBoldText(true);

        subtitlePaint.setTextAlign(Paint.Align.CENTER);
        subtitlePaint.setTextSize(AndroidUtilities.dp(11.5f));

        ensureDrawableLoaded();
    }

    public void setModel(String modelName) {
        if (modelName != null && !modelName.equals(currentModel)) {
            currentModel = modelName;
            ensureDrawableLoaded();
            invalidate();
        }
    }

    public String getCurrentModel() {
        return currentModel;
    }

    private void ensureDrawableLoaded() {
        try {
            int[] drawables = NothingPhoneEasterEgg.getDrawablesForModelName(currentModel);
            if (drawables != null && drawables.length > 0) {
                int resId = drawables[0];
                if (resId != currentResId || phoneDrawable == null) {
                    currentResId = resId;
                    phoneDrawable = ContextCompat.getDrawable(getContext(), resId);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public void triggerPreviewAnimation(int type) {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        pulseAnimator = ValueAnimator.ofFloat(0.96f, 1.04f, 1.0f);
        pulseAnimator.setDuration(type == 1 ? 400L : 260L);
        pulseAnimator.setInterpolator(new OvershootInterpolator(2.0f));
        pulseAnimator.addUpdateListener(anim -> {
            pulseScale = (float) anim.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(224);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ensureDrawableLoaded();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        ensureDrawableLoaded();

        // 1. Draw subtle card background
        float marginH = AndroidUtilities.dp(16);
        float marginV = AndroidUtilities.dp(6);
        cardRect.set(marginH, marginV, width - marginH, height - marginV);

        int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        cardPaint.setColor(bgColor);
        canvas.drawRoundRect(cardRect, AndroidUtilities.dp(18), AndroidUtilities.dp(18), cardPaint);

        int strokeColor = Theme.getColor(Theme.key_windowBackgroundGrayShadow);
        strokePaint.setColor(strokeColor);
        canvas.drawRoundRect(cardRect, AndroidUtilities.dp(18), AndroidUtilities.dp(18), strokePaint);

        // 2. Draw centered phone model image
        if (phoneDrawable != null) {
            float phoneH = AndroidUtilities.dp(152);
            int intrinsicW = phoneDrawable.getIntrinsicWidth();
            int intrinsicH = phoneDrawable.getIntrinsicHeight();
            float aspect = (intrinsicW > 0 && intrinsicH > 0) ? (intrinsicW / (float) intrinsicH) : 0.48f;
            float phoneW = phoneH * aspect;

            float phoneLeft = (width - phoneW) / 2f;
            float phoneTop = marginV + AndroidUtilities.dp(14);

            canvas.save();
            if (Math.abs(pulseScale - 1.0f) > 0.001f) {
                canvas.scale(pulseScale, pulseScale, width / 2f, phoneTop + phoneH / 2f);
            }

            phoneDrawable.setBounds((int) phoneLeft, (int) phoneTop, (int) (phoneLeft + phoneW), (int) (phoneTop + phoneH));
            phoneDrawable.draw(canvas);
            canvas.restore();
        }

        // 3. Model title & hint label
        titlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        float textY = marginV + AndroidUtilities.dp(182);
        canvas.drawText(currentModel != null ? currentModel : "Nothing Phone", width / 2f, textY, titlePaint);
        canvas.drawText("Нажмите для эффекта • Удерживайте для смены модели", width / 2f, textY + AndroidUtilities.dp(16), subtitlePaint);
    }
}
