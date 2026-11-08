package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

public class AcceptDeclineView extends View {

    private final Paint trackBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    public static final int COLOR_ACCEPT = 0xFF40C749;
    public static final int COLOR_DECLINE = 0xFFF01D2C;

    private final Paint messageBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint messageTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final Paint retryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint retryTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final Drawable phoneDrawable;
    private final Drawable messageDrawable;
    private final Drawable retryDrawable;

    private final RectF trackRect = new RectF();
    private final RectF knobRect = new RectF();
    private final RectF messageRect = new RectF();
    private final RectF retryRect = new RectF();

    private final String declineText;
    private final String answerText;
    private final String messageText;
    private final String retryText;

    private float declineTextWidth;
    private float answerTextWidth;
    private float messageTextWidth;
    private float retryTextWidth;

    private float dragOffset = 0f;
    private float maxDragLeft = 0f;
    private float maxDragRight = 0f;

    private boolean touchingKnob = false;
    private boolean touchingMessage = false;
    private boolean touchingRetry = false;
    private float startTouchX = 0f;
    private float initialDragOffset = 0f;

    private boolean retryMod = false;
    private ValueAnimator resetAnimator;
    private Listener listener;

    private boolean triggered = false;

    public interface Listener {
        void onAccept();
        void onDecline();
        default void onMessage() {}
    }

    public AcceptDeclineView(@NonNull Context context) {
        super(context);

        trackBgPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, 32));

        knobPaint.setColor(0xFFFFFFFF); // Pure white draggable knob

        labelPaint.setTextSize(dp(15));
        labelPaint.setTypeface(AndroidUtilities.bold());
        labelPaint.setColor(0xD9FFFFFF);

        messageBgPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, 30));
        messageTextPaint.setTextSize(dp(14));
        messageTextPaint.setTypeface(AndroidUtilities.bold());
        messageTextPaint.setColor(0xFFFFFFFF);

        retryPaint.setColor(COLOR_ACCEPT);
        retryTextPaint.setTextSize(dp(15));
        retryTextPaint.setTypeface(AndroidUtilities.bold());
        retryTextPaint.setColor(0xFFFFFFFF);

        phoneDrawable = ContextCompat.getDrawable(context, R.drawable.round_call_24_white);
        messageDrawable = ContextCompat.getDrawable(context, R.drawable.msg_discussion);
        retryDrawable = ContextCompat.getDrawable(context, R.drawable.round_call_24_white);

        declineText = LocaleController.getString(R.string.VoipDeclineCall);
        answerText = LocaleController.getString(R.string.VoipAnswerCall);
        messageText = LocaleController.getString(R.string.Message);
        retryText = LocaleController.getString(R.string.RetryCall);

        measureTexts();
    }

    private void measureTexts() {
        declineTextWidth = labelPaint.measureText(declineText);
        answerTextWidth = labelPaint.measureText(answerText);
        messageTextWidth = messageTextPaint.measureText(messageText);
        retryTextWidth = retryTextPaint.measureText(retryText);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setRetryMod(boolean retryMod) {
        this.retryMod = retryMod;
        updateLayoutBounds(getMeasuredWidth(), getMeasuredHeight());
        stopAnimations();
        invalidate();
    }

    public void stopAnimations() {
        if (resetAnimator != null) {
            resetAnimator.cancel();
            resetAnimator = null;
        }
        dragOffset = 0f;
        updateKnobRect();
        touchingKnob = false;
        touchingMessage = false;
        touchingRetry = false;
        triggered = false;
        invalidate();
    }

    // Compatibility methods so other callers don't fail
    public void setShapeId(int shapeId) {}
    public void setShapes(int acceptShapeId, int declineShapeId) {}
    public int getAcceptShapeId() { return 0; }
    public int getDeclineShapeId() { return 0; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateLayoutBounds(getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateLayoutBounds(w, h);
    }

    private void updateLayoutBounds(int w, int h) {
        if (w <= 0 || h <= 0) return;

        float btnW = Math.max(dp(180), retryTextWidth + dp(64));
        float btnH = dp(56);
        float l = (w - btnW) / 2f;
        float t = h - btnH - dp(24);
        retryRect.set(l, t, l + btnW, t + btnH);

        // 1. Message Button ("💬 Сообщение")
        float msgBtnW = messageTextWidth + dp(46);
        float msgBtnH = dp(38);
        float msgLeft = (w - msgBtnW) / 2f;
        float msgTop = dp(10);
        messageRect.set(msgLeft, msgTop, msgLeft + msgBtnW, msgTop + msgBtnH);

        // 2. Call Slider Track ("Отклонить" — [Knob] — "Ответить")
        float trackMargin = dp(8);
        float trackH = dp(76);
        float trackTop = h - trackH - dp(18);
        float trackBottom = trackTop + trackH;
        trackRect.set(trackMargin, trackTop, w - trackMargin, trackBottom);

        // 3. Knob dimensions
        float knobW = dp(84);
        float knobH = dp(62);
        float knobPad = dp(7);
        maxDragLeft = Math.max(0f, (trackRect.width() - knobW) / 2f - knobPad);
        maxDragRight = Math.max(0f, (trackRect.width() - knobW) / 2f - knobPad);

        updateKnobRect();
    }

    private void updateKnobRect() {
        float knobW = dp(84);
        float knobH = dp(62);
        float cx = trackRect.centerX() + dragOffset;
        float cy = trackRect.centerY();
        knobRect.set(cx - knobW / 2f, cy - knobH / 2f, cx + knobW / 2f, cy + knobH / 2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (retryMod) {
            // Draw Retry Button
            retryPaint.setColor(touchingRetry ? ColorUtils.blendARGB(COLOR_ACCEPT, 0xFF000000, 0.15f) : COLOR_ACCEPT);
            canvas.drawRoundRect(retryRect, retryRect.height() / 2f, retryRect.height() / 2f, retryPaint);
            if (retryDrawable != null) {
                int iconSize = dp(24);
                int left = (int) (retryRect.left + dp(18));
                int top = (int) (retryRect.centerY() - iconSize / 2f);
                retryDrawable.setBounds(left, top, left + iconSize, top + iconSize);
                retryDrawable.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
                retryDrawable.draw(canvas);
            }
            float textX = retryRect.left + dp(48);
            Paint.FontMetrics fm = retryTextPaint.getFontMetrics();
            float textY = retryRect.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(retryText, textX, textY, retryTextPaint);
            return;
        }

        // 1. Draw "💬 Сообщение" Pill Button
        messageBgPaint.setColor(ColorUtils.setAlphaComponent(0xFFFFFFFF, touchingMessage ? 45 : 30));
        canvas.drawRoundRect(messageRect, messageRect.height() / 2f, messageRect.height() / 2f, messageBgPaint);

        int msgIconSize = dp(18);
        float totalMsgW = msgIconSize + dp(8) + messageTextWidth;
        float msgStartX = messageRect.left + (messageRect.width() - totalMsgW) / 2f;

        if (messageDrawable != null) {
            int left = (int) msgStartX;
            int top = (int) (messageRect.centerY() - msgIconSize / 2f);
            messageDrawable.setBounds(left, top, left + msgIconSize, top + msgIconSize);
            messageDrawable.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
            messageDrawable.draw(canvas);
        }

        float msgTextX = msgStartX + msgIconSize + dp(8);
        Paint.FontMetrics msgFm = messageTextPaint.getFontMetrics();
        float msgTextY = messageRect.centerY() - (msgFm.ascent + msgFm.descent) / 2f;
        canvas.drawText(messageText, msgTextX, msgTextY, messageTextPaint);

        // 2. Draw Call Slider Capsule Track
        canvas.drawRoundRect(trackRect, trackRect.height() / 2f, trackRect.height() / 2f, trackBgPaint);

        // 3. Draw Left Label ("Отклонить")
        float knobW = knobRect.width();
        float leftAreaWidth = (trackRect.width() - knobW) / 2f;
        float leftCenterX = trackRect.left + leftAreaWidth / 2f;
        float leftAlpha = 1f;
        if (dragOffset < 0 && maxDragLeft > 0) {
            leftAlpha = Math.max(0f, 1f - (-dragOffset / (maxDragLeft * 0.65f)));
        }
        labelPaint.setAlpha((int) (0xD9 * leftAlpha));
        float labelY = trackRect.centerY() - (labelPaint.ascent() + labelPaint.descent()) / 2f;
        canvas.drawText(declineText, leftCenterX - declineTextWidth / 2f, labelY, labelPaint);

        // 4. Draw Right Label ("Ответить")
        float rightCenterX = trackRect.right - leftAreaWidth / 2f;
        float rightAlpha = 1f;
        if (dragOffset > 0 && maxDragRight > 0) {
            rightAlpha = Math.max(0f, 1f - (dragOffset / (maxDragRight * 0.65f)));
        }
        labelPaint.setAlpha((int) (0xD9 * rightAlpha));
        canvas.drawText(answerText, rightCenterX - answerTextWidth / 2f, labelY, labelPaint);

        // 5. Draw Draggable White Knob
        canvas.drawRoundRect(knobRect, knobRect.height() / 2f, knobRect.height() / 2f, knobPaint);

        // 6. Draw Phone Icon inside Knob
        if (phoneDrawable != null) {
            int iconSize = dp(28);
            canvas.save();
            canvas.translate(knobRect.centerX(), knobRect.centerY());

            int iconColor;
            float rotation = 0f;
            if (dragOffset < 0 && maxDragLeft > 0) {
                float p = Math.min(1f, -dragOffset / (maxDragLeft * 0.75f));
                iconColor = ColorUtils.blendARGB(0xFF1C1B1F, COLOR_DECLINE, p);
                rotation = p * 135f; // rotate downwards into hangup phone receiver
            } else if (dragOffset > 0 && maxDragRight > 0) {
                float p = Math.min(1f, dragOffset / (maxDragRight * 0.75f));
                iconColor = ColorUtils.blendARGB(0xFF1C1B1F, COLOR_ACCEPT, p);
                rotation = 0f;
            } else {
                iconColor = 0xFF1C1B1F; // Resting dark neutral
            }

            canvas.rotate(rotation);
            phoneDrawable.setBounds(-iconSize / 2, -iconSize / 2, iconSize / 2, iconSize / 2);
            phoneDrawable.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
            phoneDrawable.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        float x = event.getX();
        float y = event.getY();

        if (retryMod) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (retryRect.contains(x, y)) {
                        touchingRetry = true;
                        invalidate();
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (touchingRetry && retryRect.contains(x, y)) {
                        touchingRetry = false;
                        invalidate();
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (listener != null) {
                            listener.onAccept();
                        }
                        return true;
                    }
                    touchingRetry = false;
                    invalidate();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    touchingRetry = false;
                    invalidate();
                    break;
            }
            return super.onTouchEvent(event);
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                triggered = false;
                if (messageRect.contains(x, y)) {
                    touchingMessage = true;
                    invalidate();
                    return true;
                }
                if (trackRect.contains(x, y) || knobRect.contains(x, y)) {
                    if (resetAnimator != null) {
                        resetAnimator.cancel();
                        resetAnimator = null;
                    }
                    touchingKnob = true;
                    startTouchX = x;
                    initialDragOffset = dragOffset;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (touchingKnob && !triggered) {
                    float deltaX = x - startTouchX;
                    dragOffset = Math.max(-maxDragLeft, Math.min(maxDragRight, initialDragOffset + deltaX));
                    updateKnobRect();

                    // Check Decline threshold (left)
                    if (dragOffset <= -maxDragLeft * 0.85f) {
                        triggered = true;
                        touchingKnob = false;
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        if (listener != null) {
                            listener.onDecline();
                        }
                    }
                    // Check Accept threshold (right)
                    else if (dragOffset >= maxDragRight * 0.85f) {
                        triggered = true;
                        touchingKnob = false;
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        if (listener != null) {
                            listener.onAccept();
                        }
                    }
                    invalidate();
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (touchingMessage) {
                    touchingMessage = false;
                    invalidate();
                    if (messageRect.contains(x, y)) {
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        if (listener != null) {
                            listener.onMessage();
                        }
                    }
                    return true;
                }
                if (touchingKnob) {
                    touchingKnob = false;
                    if (!triggered) {
                        if (dragOffset <= -maxDragLeft * 0.65f) {
                            triggered = true;
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            if (listener != null) {
                                listener.onDecline();
                            }
                        } else if (dragOffset >= maxDragRight * 0.65f) {
                            triggered = true;
                            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            if (listener != null) {
                                listener.onAccept();
                            }
                        } else {
                            animateKnobToCenter();
                        }
                    }
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                touchingMessage = false;
                if (touchingKnob) {
                    touchingKnob = false;
                    animateKnobToCenter();
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                break;
            }
        }
        return super.onTouchEvent(event);
    }

    private void animateKnobToCenter() {
        if (dragOffset == 0f) return;
        if (resetAnimator != null) {
            resetAnimator.cancel();
        }
        resetAnimator = ValueAnimator.ofFloat(dragOffset, 0f);
        resetAnimator.setDuration(260);
        resetAnimator.setInterpolator(new OvershootInterpolator(1.2f));
        resetAnimator.addUpdateListener(a -> {
            dragOffset = (float) a.getAnimatedValue();
            updateKnobRect();
            invalidate();
        });
        resetAnimator.start();
    }
}
