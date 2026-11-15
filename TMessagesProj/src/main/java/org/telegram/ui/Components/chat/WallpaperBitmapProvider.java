package org.telegram.ui.Components.chat;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.Utilities;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.utils.glass.source.GlassSource;
import org.telegram.utils.glass.source.GlassSourceBitmap;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceWrapped;
import org.telegram.ui.Components.blur3.utils.BitmapMemoizedMetadata;

public class WallpaperBitmapProvider {

    private final GlassSourceColor sourceColor = new GlassSourceColor();
    private final GlassSourceBitmap sourceBitmap = new GlassSourceBitmap();

    private final GlassSourceColor liquidSourceColor = new GlassSourceColor();
    private final GlassSourceBitmap liquidSourceBitmap = new GlassSourceBitmap();
    private java.lang.ref.WeakReference<Bitmap> lastLiquidInputBitmap;
    private Bitmap cachedLiquidBitmap;
    private int lastLiquidBlurRadius = -1;

    private static final Rect tmpRect = new Rect();

    public GlassSource updateSourceFromBackgroundViewDrawable(
        Drawable drawable
    ) {
        if (drawable instanceof ColorDrawable) {
            final int color = ((ColorDrawable) drawable).getColor();
            sourceColor.setColor(color);
            return sourceColor;
        }

        if (drawable instanceof MotionBackgroundDrawable) {
            final MotionBackgroundDrawable motionDrawable = (MotionBackgroundDrawable) drawable;
            if (motionDrawable.getIntensity() >= 0 && motionDrawable.getBitmap() != null) {
                sourceBitmap.setBitmap(motionDrawable.getBitmap());
                return sourceBitmap;
            }
        }

        if (drawable instanceof BitmapDrawable) {
            final Bitmap bitmap = blurredFromBitmap.get(((BitmapDrawable) drawable).getBitmap());
            sourceBitmap.setBitmap(bitmap);
            return sourceBitmap;
        }

        if (drawable instanceof ChatBackgroundDrawable) {
            ChatBackgroundDrawable chatDrawable = (ChatBackgroundDrawable) drawable;
            Drawable inner = chatDrawable.getDrawable(false);
            if (inner != null && inner != drawable) {
                return updateSourceFromBackgroundViewDrawable(inner);
            }
        }

        if (drawable != null) {
            final int recW = 180;
            final int recH = 320;
            Canvas canvas = sourceBitmap.beginRecording(recW, recH);
            tmpRect.set(drawable.getBounds());
            drawable.setBounds(0, 0, recW, recH);
            drawable.draw(canvas);
            drawable.setBounds(tmpRect);
            sourceBitmap.endRecording();
            sourceBitmap.setBitmap(blurredFromBitmap.get(sourceBitmap.getBitmap()));
        }

        return sourceBitmap;
    }

    public GlassSource updateLiquidSourceFromBackgroundViewDrawable(
        Drawable drawable
    ) {
        if (drawable instanceof ColorDrawable) {
            final int color = ((ColorDrawable) drawable).getColor();
            liquidSourceColor.setColor(color);
            return liquidSourceColor;
        }

        if (drawable instanceof MotionBackgroundDrawable) {
            final MotionBackgroundDrawable motionDrawable = (MotionBackgroundDrawable) drawable;
            if (motionDrawable.getIntensity() >= 0 && motionDrawable.getBitmap() != null) {
                liquidSourceBitmap.setBitmap(motionDrawable.getBitmap());
                return liquidSourceBitmap;
            }
        }

        if (drawable instanceof BitmapDrawable) {
            final Bitmap bitmap = prepareLiquidBitmap(((BitmapDrawable) drawable).getBitmap());
            liquidSourceBitmap.setBitmap(bitmap);
            return liquidSourceBitmap;
        }

        if (drawable instanceof ChatBackgroundDrawable) {
            ChatBackgroundDrawable chatDrawable = (ChatBackgroundDrawable) drawable;
            Drawable inner = chatDrawable.getDrawable(false);
            if (inner != null && inner != drawable) {
                return updateLiquidSourceFromBackgroundViewDrawable(inner);
            }
        }

        if (drawable != null) {
            final int recW = 360;
            final int recH = 640;
            Canvas canvas = liquidSourceBitmap.beginRecording(recW, recH);
            tmpRect.set(drawable.getBounds());
            drawable.setBounds(0, 0, recW, recH);
            drawable.draw(canvas);
            drawable.setBounds(tmpRect);
            liquidSourceBitmap.endRecording();
            final Bitmap prepared = prepareLiquidBitmap(liquidSourceBitmap.getBitmap());
            if (prepared != null) {
                liquidSourceBitmap.setBitmap(prepared);
            }
        }

        return liquidSourceBitmap;
    }

    private static int getLiquidBlurRadius() {
        if (!app.exteraless.glass.LiquidGlassConfig.isLiquidGlassEnabled()) {
            return 4;
        }
        final float scale = app.exteraless.glass.SolegramGlass.blurScale();
        return scale <= 0.12f ? 0 : Math.max(1, Math.round(5f * scale));
    }

    private Bitmap prepareLiquidBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }
        final int radius = getLiquidBlurRadius();
        final Bitmap prevInput = lastLiquidInputBitmap != null ? lastLiquidInputBitmap.get() : null;
        if (prevInput == bitmap && cachedLiquidBitmap != null && !cachedLiquidBitmap.isRecycled() && lastLiquidBlurRadius == radius) {
            return cachedLiquidBitmap;
        }

        final int maxSide = 720;
        final int w = bitmap.getWidth();
        final int h = bitmap.getHeight();
        final float down = Math.max(1f, Math.max(w, h) / (float) maxSide);
        final int outW = Math.max(1, Math.round(w / down));
        final int outH = Math.max(1, Math.round(h / down));

        Bitmap out;
        if (outW != w || outH != h) {
            out = Bitmap.createScaledBitmap(bitmap, outW, outH, true);
        } else {
            out = bitmap.copy(Bitmap.Config.ARGB_8888, radius > 0);
        }
        if (out == null) {
            return null;
        }
        if (radius > 0) {
            if (!out.isMutable()) {
                final Bitmap mutable = out.copy(Bitmap.Config.ARGB_8888, true);
                if (mutable != null) {
                    out = mutable;
                }
            }
            if (out.isMutable()) {
                Utilities.stackBlurBitmap(out, radius);
            }
        }
        out.setHasAlpha(false);

        lastLiquidInputBitmap = new java.lang.ref.WeakReference<>(bitmap);
        cachedLiquidBitmap = out;
        lastLiquidBlurRadius = radius;
        return out;
    }

    public int getNavigationBarColor(GlassSource source) {
        if (source instanceof GlassSourceColor) {
            return ((GlassSourceColor) source).getColor();
        }

        if (source instanceof GlassSourceBitmap) {
            final Bitmap bitmap = ((GlassSourceBitmap) source).getBitmap();
            return navbarColorFromBitmap.get(bitmap);
        }

        if (source instanceof GlassSourceWrapped) {
            return getNavigationBarColor(((GlassSourceWrapped) source).getSource());
        }

        return 0;
    }

    public int getStatusBarColor(GlassSource source) {
        if (source instanceof GlassSourceColor) {
            return ((GlassSourceColor) source).getColor();
        }

        if (source instanceof GlassSourceBitmap) {
            final Bitmap bitmap = ((GlassSourceBitmap) source).getBitmap();
            return statusBarColorFromBitmap.get(bitmap);
        }

        if (source instanceof GlassSourceWrapped) {
            return getStatusBarColor(((GlassSourceWrapped) source).getSource());
        }

        return 0;
    }

    private final BitmapMemoizedMetadata<Bitmap> blurredFromBitmap = new BitmapMemoizedMetadata<>(WallpaperBitmapProvider::blurBitmap);
    private final BitmapMemoizedMetadata<Integer> navbarColorFromBitmap = new BitmapMemoizedMetadata<>(WallpaperBitmapProvider::averageBottomColor);
    private final BitmapMemoizedMetadata<Integer> statusBarColorFromBitmap = new BitmapMemoizedMetadata<>(WallpaperBitmapProvider::averageTopColor);


    private static Bitmap blurBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }

        final float scale = Math.max(bitmap.getWidth() / 90f, bitmap.getHeight() / 120f);
        final Bitmap result = Utilities.stackBlurBitmapWithScaleFactor(bitmap, scale);
        result.setHasAlpha(false);
        return result;
    }



    private static int averageTopColor(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0;
        }

        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        final int bottom = height / 10;
        return Utilities.averageBitmapColor(bitmap, 0, 0, width, bottom);
    }

    private static int averageBottomColor(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0;
        }

        final int height = bitmap.getHeight();
        final int width = bitmap.getWidth();
        final int top = height * 9 / 10;
        return Utilities.averageBitmapColor(bitmap, 0, top, width, height);
    }

}
