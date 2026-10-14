package app.exteraless.appicons;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.LauncherIconController.LauncherIcon;

import app.exteraless.icons.IconShapeHelper;

public class IconPreviewView extends View {

    private final int inset;
    private final Path shape = new Path();
    private int shapeSize = -1;

    private LauncherIcon icon;
    private Drawable background;
    private Drawable foreground;
    private Drawable oldBackground;
    private Drawable oldForeground;
    private boolean hasBleed = true;
    private boolean oldHasBleed = true;
    private float progress = 1f;
    private ValueAnimator animator;
    private float scale = 1f;

    public IconPreviewView(Context context, int insetDp) {
        super(context);
        inset = AndroidUtilities.dp(insetDp);
    }

    public LauncherIcon getIcon() {
        return icon;
    }

    public void setIcon(LauncherIcon icon, boolean animated) {
        if (this.icon == icon) {
            return;
        }
        boolean crossfade = animated && this.icon != null;
        this.icon = icon;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (crossfade) {
            oldBackground = background;
            oldForeground = foreground;
            oldHasBleed = hasBleed;
        }
        hasBleed = icon == null || icon.hasBleed;
        background = icon == null || icon.background == 0 ? null : ContextCompat.getDrawable(getContext(), icon.background);
        foreground = icon == null || icon.foreground == 0 ? null : ContextCompat.getDrawable(getContext(), icon.foreground);
        if (crossfade) {
            progress = 0f;
            animator = ValueAnimator.ofFloat(0f, 1f).setDuration(260);
            animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                if (progress >= 1f) {
                    oldBackground = oldForeground = null;
                }
                invalidate();
            });
            animator.start();
        } else {
            progress = 1f;
            oldBackground = oldForeground = null;
        }
        invalidate();
    }

    public void setPreviewScale(float scale) {
        if (this.scale != scale) {
            this.scale = scale;
            invalidate();
        }
    }

    public void invalidateShape() {
        shapeSize = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int size = Math.min(getWidth(), getHeight()) - inset * 2;
        if (size <= 0 || background == null && foreground == null) {
            return;
        }
        if (shapeSize != size) {
            shapeSize = size;
            float dp = size / AndroidUtilities.density;
            shape.set(IconShapeHelper.getFinalIconShapePath(dp, dp, dp * 0.28f));
        }
        canvas.save();
        canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
        canvas.scale(scale, scale, size / 2f, size / 2f);
        canvas.clipPath(shape);
        if (progress < 1f && (oldBackground != null || oldForeground != null)) {
            AppIcons.draw(canvas, oldBackground, oldForeground, size, oldHasBleed);
            canvas.saveLayerAlpha(0, 0, size, size, (int) (255 * progress));
            AppIcons.draw(canvas, background, foreground, size, hasBleed);
            canvas.restore();
        } else {
            AppIcons.draw(canvas, background, foreground, size, hasBleed);
        }
        canvas.restore();
    }
}
