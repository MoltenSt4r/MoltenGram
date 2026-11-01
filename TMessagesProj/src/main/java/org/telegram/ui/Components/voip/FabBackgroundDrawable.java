/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Grishka, 2013-2016.
 */

package org.telegram.ui.Components.voip;

import android.graphics.*;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;

import androidx.annotation.Keep;

import app.exteraless.appearance.Md3AvatarShapes;

public class FabBackgroundDrawable extends Drawable {

    private Paint bgPaint, shadowPaint;
    private Bitmap shadowBitmap;
    private int shapeId = Md3AvatarShapes.SHAPE_SCALLOP;

    public FabBackgroundDrawable() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint = new Paint();
        shadowPaint.setColor(0x4C000000);
    }

    public void setShapeId(int shapeId) {
        if (this.shapeId != shapeId) {
            this.shapeId = shapeId;
            shadowBitmap = null;
            invalidateSelf();
        }
    }

    public int getShapeId() {
        return shapeId;
    }

    @Override
    public void draw(Canvas canvas) {
        if (shadowBitmap == null)
            onBoundsChange(getBounds());
        Rect bounds = getBounds();
        if (shadowBitmap != null) {
            canvas.drawBitmap(shadowBitmap, bounds.centerX() - shadowBitmap.getWidth() / 2f, bounds.centerY() - shadowBitmap.getHeight() / 2f, shadowPaint);
        }
        int pad = AndroidUtilities.dp(4);
        float w = bounds.width() - pad * 2;
        float h = bounds.height() - pad * 2;
        if (w > 0 && h > 0) {
            if (shapeId > 0) {
                Path path = Md3AvatarShapes.getShapePath(shapeId, bounds.left + pad, bounds.top + pad, w, h);
                canvas.drawPath(path, bgPaint);
            } else {
                float size = Math.min(w, h);
                canvas.drawCircle(bounds.left + bounds.width() / 2f, bounds.top + bounds.height() / 2f, size / 2f, bgPaint);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {
        bgPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        bgPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0) {
            shadowBitmap = null;
            return;
        }
        shadowBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(shadowBitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int pad = AndroidUtilities.dp(4);
        float innerW = w - pad * 2;
        float innerH = h - pad * 2;
        if (innerW > 0 && innerH > 0) {
            if (shapeId > 0) {
                Path path = Md3AvatarShapes.getShapePath(shapeId, pad, pad, innerW, innerH);
                c.drawPath(path, p);
            } else {
                float size = Math.min(innerW, innerH);
                c.drawCircle(w / 2f, h / 2f, size / 2f, p);
            }
        }
    }

    @Keep
    public void setColor(int color) {
        bgPaint.setColor(color);
        invalidateSelf();
    }

    @Override
    public boolean getPadding(Rect padding) {
        int pad = AndroidUtilities.dp(4);
        padding.set(pad, pad, pad, pad);
        return true;
    }
}
