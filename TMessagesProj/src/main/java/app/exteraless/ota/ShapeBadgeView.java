package app.exteraless.ota;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import app.exteraless.appearance.Md3AvatarShapes;

/**
 * Custom View that draws an expressive Material 3 geometric shape badge
 * with a centered tinted icon.
 */
public class ShapeBadgeView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int shapeId;
    private Drawable iconDrawable;

    public ShapeBadgeView(Context context, int shapeId, int bgColor, int iconRes, int iconTint) {
        super(context);
        this.shapeId = shapeId;
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(bgColor);
        if (iconRes != 0) {
            iconDrawable = ContextCompat.getDrawable(context, iconRes);
            if (iconDrawable != null) {
                iconDrawable = iconDrawable.mutate();
                iconDrawable.setTint(iconTint);
            }
        }
    }

    public ShapeBadgeView(Context context, int shapeId, int bgColor, int iconRes) {
        this(context, shapeId, bgColor, iconRes, org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_featuredStickers_addButton));
    }

    public void setBadgeColor(int color) {
        bgPaint.setColor(color);
        invalidate();
    }

    public void setIconTint(int tint) {
        if (iconDrawable != null) {
            iconDrawable.setTint(tint);
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        Path shape = Md3AvatarShapes.getShapePath(shapeId, w, h);
        canvas.drawPath(shape, bgPaint);

        if (iconDrawable != null) {
            int iconSize = dp(20);
            int left = (int) ((w - iconSize) / 2f);
            int top = (int) ((h - iconSize) / 2f);
            iconDrawable.setBounds(left, top, left + iconSize, top + iconSize);
            iconDrawable.draw(canvas);
        }
    }
}
