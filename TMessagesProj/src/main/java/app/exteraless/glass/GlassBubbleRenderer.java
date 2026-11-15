package app.exteraless.glass;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatBackgroundDrawable;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.utils.glass.source.GlassSourceBitmap;

/**
 * Shared wallpaper snapshot cache + API 33+ AGSL RuntimeShader renderer
 * for Liquid Glass chat message bubbles.
 */
public final class GlassBubbleRenderer {

    private static final int MAX_SNAPSHOT_SIDE = 300;
    private static final LruCache<String, Bitmap> SNAPSHOT_CACHE = new LruCache<>(3);
    private static String cachedShaderSource;

    private GlassBubbleRenderer() {
    }

    public static final class SnapshotResult {
        public final Bitmap bitmap;
        public final Matrix baseMatrix;
        public final boolean matrixIsInContainerSpace;

        SnapshotResult(Bitmap bitmap, Matrix baseMatrix, boolean matrixIsInContainerSpace) {
            this.bitmap = bitmap;
            this.baseMatrix = baseMatrix;
            this.matrixIsInContainerSpace = matrixIsInContainerSpace;
        }
    }

    public static final class CellState {
        RuntimeShader shader;
        boolean shaderFailed;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap lastBitmap;
        BitmapShader bitmapShader;
        final Matrix matrix = new Matrix();
    }

    @Nullable
    public static SnapshotResult resolveWallpaperSnapshot(
            @Nullable Theme.ResourcesProvider resourcesProvider,
            @Nullable GlassSourceBitmap bitmapSource,
            int containerW,
            int containerH
    ) {
        Drawable wallpaperDrawable = null;
        if (resourcesProvider instanceof ChatActivity.ThemeDelegate) {
            wallpaperDrawable = ((ChatActivity.ThemeDelegate) resourcesProvider).getWallpaperDrawable();
        }
        int cW = containerW > 0 ? containerW : (AndroidUtilities.displaySize.x > 0 ? AndroidUtilities.displaySize.x : 1080);
        int cH = containerH > 0 ? containerH : (AndroidUtilities.displaySize.y > 0 ? AndroidUtilities.displaySize.y : 1920);

        float blurScale = SolegramGlass.blurScale();
        int blurRadius = Math.max(2, Math.min(26, Math.round(9f * blurScale)));

        if (wallpaperDrawable != null) {
            String key = buildWallpaperSignature(wallpaperDrawable, cW, cH, blurRadius);
            Bitmap snap = SNAPSHOT_CACHE.get(key);
            if (snap == null || snap.isRecycled()) {
                snap = captureAndBlurWallpaper(wallpaperDrawable, cW, cH, blurRadius);
                if (snap != null) {
                    SNAPSHOT_CACHE.put(key, snap);
                }
            }
            if (snap != null && !snap.isRecycled()) {
                Matrix m = new Matrix();
                float sx = cW / Math.max(1f, (float) snap.getWidth());
                float sy = cH / Math.max(1f, (float) snap.getHeight());
                float scale = Math.max(sx, sy);
                float dx = (cW - snap.getWidth() * scale) * 0.5f;
                float dy = (cH - snap.getHeight() * scale) * 0.5f;
                m.setScale(scale, scale);
                m.postTranslate(dx, dy);
                return new SnapshotResult(snap, m, true);
            }
        }

        if (bitmapSource != null) {
            Bitmap srcBmp = bitmapSource.getBitmap();
            if (srcBmp != null && !srcBmp.isRecycled()) {
                return new SnapshotResult(srcBmp, new Matrix(bitmapSource.getMatrix()), true);
            }
        }
        return null;
    }

    private static String buildWallpaperSignature(Drawable drawable, int cW, int cH, int blurRadius) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(System.identityHashCode(drawable))
                .append('|').append(Theme.isCurrentThemeDark() ? 1 : 0)
                .append('|').append(blurRadius)
                .append('|').append(cW / 64).append('x').append(cH / 64);
        if (drawable instanceof ChatBackgroundDrawable) {
            ChatBackgroundDrawable chatBg = (ChatBackgroundDrawable) drawable;
            Drawable inner = chatBg.getDrawable(false);
            sb.append("|cbg:").append(inner != null ? System.identityHashCode(inner) : 0);
            if (inner instanceof BitmapDrawable) {
                Bitmap b = ((BitmapDrawable) inner).getBitmap();
                sb.append(':').append(b != null ? System.identityHashCode(b) : 0);
            } else if (inner instanceof MotionBackgroundDrawable) {
                appendMotionSignature(sb, (MotionBackgroundDrawable) inner);
            }
        } else if (drawable instanceof MotionBackgroundDrawable) {
            appendMotionSignature(sb, (MotionBackgroundDrawable) drawable);
        }
        return sb.toString();
    }

    private static void appendMotionSignature(StringBuilder sb, MotionBackgroundDrawable mb) {
        int[] colors = mb.getColors();
        Bitmap pat = mb.getPatternBitmap();
        sb.append("|mb:")
                .append(mb.getIntensity())
                .append(':').append(pat != null ? System.identityHashCode(pat) : 0);
        if (colors != null) {
            for (int c : colors) {
                sb.append(':').append(c);
            }
        }
    }

    @Nullable
    private static Bitmap captureAndBlurWallpaper(Drawable drawable, int cW, int cH, int blurRadius) {
        try {
            float aspect = Math.max(0.25f, Math.min(4f, cW / (float) Math.max(1, cH)));
            int snapW;
            int snapH;
            if (aspect >= 1f) {
                snapW = MAX_SNAPSHOT_SIDE;
                snapH = Math.max(48, Math.round(MAX_SNAPSHOT_SIDE / aspect));
            } else {
                snapH = MAX_SNAPSHOT_SIDE;
                snapW = Math.max(48, Math.round(MAX_SNAPSHOT_SIDE * aspect));
            }

            Bitmap bmp = Bitmap.createBitmap(snapW, snapH, Bitmap.Config.ARGB_8888);
            Canvas snapCanvas = new Canvas(bmp);

            Rect savedBounds = new Rect(drawable.getBounds());
            int savedAlpha = drawable.getAlpha();
            try {
                drawable.setAlpha(255);
                drawable.setBounds(0, 0, snapW, snapH);
                drawable.draw(snapCanvas);
            } finally {
                drawable.setAlpha(savedAlpha);
                if (!savedBounds.isEmpty()) {
                    drawable.setBounds(savedBounds);
                }
            }

            if (blurRadius > 0) {
                Utilities.stackBlurBitmap(bmp, blurRadius);
            }
            return bmp;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    public static boolean drawRuntimeGlassBubble(
            Canvas canvas,
            CellState state,
            Path bubblePath,
            RectF rect,
            float[] radii,
            Bitmap bitmap,
            Matrix containerMatrix,
            float offsetX,
            float offsetY,
            float thicknessNorm,
            float refractionNorm,
            int foregroundVeilColor,
            float alphaInternal,
            boolean liquidGlassOn
    ) {
        if (Build.VERSION.SDK_INT < 33 || !canvas.isHardwareAccelerated() || state == null || bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        try {
            if (state.shader == null && !state.shaderFailed) {
                if (cachedShaderSource == null) {
                    cachedShaderSource = AndroidUtilities.readRes(R.raw.solegram_glass_shader);
                }
                if (cachedShaderSource != null && !cachedShaderSource.isEmpty()) {
                    state.shader = new RuntimeShader(cachedShaderSource);
                } else {
                    state.shaderFailed = true;
                    return false;
                }
            }
            if (state.shader == null) {
                return false;
            }

            if (state.lastBitmap != bitmap || state.bitmapShader == null) {
                state.lastBitmap = bitmap;
                state.bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                state.shader.setInputShader("img", state.bitmapShader);
            }

            state.matrix.set(containerMatrix);
            state.matrix.postTranslate(-offsetX, -offsetY);
            state.bitmapShader.setLocalMatrix(state.matrix);
            // Rebind input shader so HWUI picks up updated BitmapShader localMatrix deterministically.
            state.shader.setInputShader("img", state.bitmapShader);

            float bw = Math.max(2f, rect.width());
            float bh = Math.max(2f, rect.height());
            float cx = (rect.left + rect.right) * 0.5f;
            float cy = (rect.top + rect.bottom) * 0.5f;

            float topLeft = radii[0];
            float topRight = radii[2];
            float bottomRight = radii[4];
            float bottomLeft = radii[6];

            float baseThickness = AndroidUtilities.dpf2(11f) * thicknessNorm;
            float effectiveThickness = Math.max(
                    1.5f,
                    Math.min(SolegramGlass.thickness(baseThickness), SolegramGlass.thicknessLimit(Math.min(bw, bh)))
            );
            float effectiveIntensity = Math.max(0.01f, SolegramGlass.intensity(0.32f * Math.max(0.25f, refractionNorm)));
            float index = SolegramGlass.index(1.5f);
            float dispersion = liquidGlassOn ? SolegramGlass.dispersion() : 0f;
            float rim = liquidGlassOn ? SolegramGlass.rim() : 0f;

            float fa = Color.alpha(foregroundVeilColor) / 255f;
            float fr = (Color.red(foregroundVeilColor) / 255f) * fa;
            float fg = (Color.green(foregroundVeilColor) / 255f) * fa;
            float fb = (Color.blue(foregroundVeilColor) / 255f) * fa;

            state.shader.setFloatUniform("center", cx, cy);
            state.shader.setFloatUniform("size", bw * 0.5f, bh * 0.5f);
            state.shader.setFloatUniform("radius", bottomRight, topRight, bottomLeft, topLeft);
            state.shader.setFloatUniform("thickness", effectiveThickness);
            state.shader.setFloatUniform("refract_intensity", effectiveIntensity);
            state.shader.setFloatUniform("refract_index", index);
            state.shader.setFloatUniform("foreground_color_premultiplied", fr, fg, fb, fa);
            state.shader.setFloatUniform("dispersion", dispersion);
            state.shader.setFloatUniform("rim", rim);

            state.paint.setShader(state.shader);
            state.paint.setStyle(Paint.Style.FILL);
            state.paint.setAlpha(Math.max(0, Math.min(255, Math.round(255f * alphaInternal))));
            canvas.drawPath(bubblePath, state.paint);
            return true;
        } catch (Throwable t) {
            state.shaderFailed = true;
            FileLog.e(t);
            return false;
        }
    }
}
