package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class VoIPButtonsLayout extends FrameLayout {

    private final RectF containerRect = new RectF();
    private final Paint containerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint containerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float containerRadius;
    private boolean drawPillDock = false;

    public VoIPButtonsLayout(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        containerStrokePaint.setStyle(Paint.Style.STROKE);
        containerStrokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        updateContainerColors();
    }

    public void updateContainerColors() {
        int surface = Theme.getColor(Theme.key_windowBackgroundWhite);
        int primary = Theme.getColor(Theme.key_chats_actionBackground);
        boolean isDark = Theme.isCurrentThemeDark();
        int fillColor = isDark
            ? ColorUtils.blendARGB(surface, primary, 0.14f)
            : ColorUtils.blendARGB(surface, primary, 0.08f);
        int strokeColor = ColorUtils.setAlphaComponent(
            Theme.getColor(Theme.key_windowBackgroundWhiteBlackText),
            isDark ? (int) (255 * 0.08f) : (int) (255 * 0.06f)
        );
        containerPaint.setColor(fillColor);
        containerStrokePaint.setColor(strokeColor);
        invalidate();
    }

    int visibleChildCount;
    int childWidth;
    int childPadding;

    private int childSize = 68;
    private boolean startPadding = true;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);

        visibleChildCount = 0;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != View.GONE) {
                visibleChildCount++;
            }
        }
        childWidth = AndroidUtilities.dp(childSize);
        int maxChildHeigth = 0;
        childPadding = (width / getChildCount() - childWidth) / 2;
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != View.GONE) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
                if (getChildAt(i).getMeasuredHeight() > maxChildHeigth) {
                    maxChildHeigth = getChildAt(i).getMeasuredHeight();
                }
            }
        }

        int h = Math.max(maxChildHeigth, AndroidUtilities.dp(58));
        setMeasuredDimension(width, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int minLeft = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE;
        int minTop = Integer.MAX_VALUE;
        int maxBottom = Integer.MIN_VALUE;
        if (startPadding) {
            int startFrom = (int) ((getChildCount() - visibleChildCount) / 2f * (childWidth + childPadding * 2));
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != View.GONE) {
                    int cLeft = startFrom + childPadding;
                    int cRight = cLeft + child.getMeasuredWidth();
                    int cTop = 0;
                    int cBottom = Math.max(child.getMeasuredHeight(), getMeasuredHeight());
                    child.layout(cLeft, cTop, cRight, cBottom);
                    minLeft = Math.min(minLeft, cLeft);
                    maxRight = Math.max(maxRight, cRight);
                    minTop = Math.min(minTop, cTop);
                    maxBottom = Math.max(maxBottom, cBottom);
                    startFrom += childPadding * 2 + child.getMeasuredWidth();
                }
            }
        } else  {
            int padding = visibleChildCount > 0 ? (getMeasuredWidth() - childWidth) / (visibleChildCount - 1) : 0;
            int k = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != View.GONE) {
                    int cLeft = k * padding;
                    int cRight = cLeft + child.getMeasuredWidth();
                    int cTop = 0;
                    int cBottom = Math.max(child.getMeasuredHeight(), getMeasuredHeight());
                    child.layout(cLeft, cTop, cRight, cBottom);
                    minLeft = Math.min(minLeft, cLeft);
                    maxRight = Math.max(maxRight, cRight);
                    minTop = Math.min(minTop, cTop);
                    maxBottom = Math.max(maxBottom, cBottom);
                    k++;
                }
            }
        }
        if (visibleChildCount > 0 && minLeft < maxRight) {
            float padH = AndroidUtilities.dp(12);
            float padV = AndroidUtilities.dp(6);
            float dockLeft = Math.max(AndroidUtilities.dp(12), minLeft - padH);
            float dockRight = Math.min(getMeasuredWidth() - AndroidUtilities.dp(12), maxRight + padH);
            float dockTop = Math.max(0, minTop - padV);
            float dockBottom = Math.min(getMeasuredHeight(), maxBottom + padV);
            containerRect.set(dockLeft, dockTop, dockRight, dockBottom);
            containerRadius = containerRect.height() / 2f;
        } else {
            containerRect.setEmpty();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (visibleChildCount > 0 && !containerRect.isEmpty() && drawPillDock) {
            canvas.drawRoundRect(containerRect, containerRadius, containerRadius, containerPaint);
            if (containerStrokePaint != null) {
                canvas.drawRoundRect(containerRect, containerRadius, containerRadius, containerStrokePaint);
            }
        }
    }

    public void setChildSize(int childSize) {
        this.childSize = childSize;
    }

    public void setUseStartPadding(boolean startPadding) {
        this.startPadding = startPadding;
    }

    public void setDrawPillDock(boolean draw) {
        this.drawPillDock = draw;
        invalidate();
    }

    public boolean isDrawPillDock() {
        return drawPillDock;
    }

    public void setPillDockColor(int fillColor, int strokeColor) {
        containerPaint.setColor(fillColor);
        containerStrokePaint.setColor(strokeColor);
        invalidate();
    }

    public RectF getContainerRect() {
        return containerRect;
    }
}
