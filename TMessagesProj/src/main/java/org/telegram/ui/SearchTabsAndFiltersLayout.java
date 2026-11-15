package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

import app.exteraless.appearance.Md3ChipTabHelper;

public class SearchTabsAndFiltersLayout extends FrameLayout implements Theme.Colorable {
    private final Path clipPath = new Path();
    private BlurredBackgroundDrawable blurredBackgroundDrawable;
    private float filtersBackgroundAlpha = 0f;

    public SearchTabsAndFiltersLayout(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clipPath.rewind();
        clipPath.addRoundRect(dp(9), dp(9), w - dp(9), h - dp(9),
                dp(16), dp(16), Path.Direction.CW);
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.setBounds(0, 0, w, h);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        boolean chipMode = Md3ChipTabHelper.isEnabled();
        if (!chipMode || filtersBackgroundAlpha > 0.01f) {
            if (blurredBackgroundDrawable != null && getBackground() != blurredBackgroundDrawable) {
                int oldAlpha = blurredBackgroundDrawable.getAlpha();
                float bgAlpha = chipMode ? filtersBackgroundAlpha : 1f;
                blurredBackgroundDrawable.setAlpha(Math.round(255 * bgAlpha));
                blurredBackgroundDrawable.setBounds(0, 0, getWidth(), getHeight());
                blurredBackgroundDrawable.draw(canvas);
                blurredBackgroundDrawable.setAlpha(oldAlpha);
            }
            canvas.save();
            if (!chipMode || filtersBackgroundAlpha >= 0.99f) {
                canvas.clipPath(clipPath);
            }
            super.dispatchDraw(canvas);
            canvas.restore();
        } else {
            super.dispatchDraw(canvas);
        }
    }

    public void setBlurredBackground(BlurredBackgroundDrawable drawable) {
        blurredBackgroundDrawable = drawable;
        setBackground(null);
        invalidate();
    }

    public void setFiltersBackgroundAlpha(float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        if (filtersBackgroundAlpha != clamped) {
            filtersBackgroundAlpha = clamped;
            invalidate();
        }
    }

    @Override
    public void updateColors() {
        if (blurredBackgroundDrawable != null) {
            blurredBackgroundDrawable.updateColors();
        }
        invalidate();
    }
}
