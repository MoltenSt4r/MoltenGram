package app.exteraless.clip;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Image preprocessor for MobileCLIP-S0 visual encoder on Android.
 * Performs aspect-ratio preserving center crop and bilinear resizing to 256x256,
 * extracts RGB float pixels into planar NCHW layout [1, 3, 256, 256], and scales pixels by 1.0f / 255.0f.
 */
public final class ClipImageProcessor {

    public static final int DEFAULT_WIDTH = 256;
    public static final int DEFAULT_HEIGHT = 256;
    public static final int CHANNELS = 3;
    public static final float DEFAULT_SCALE = 1.0f / 255.0f;

    private ClipImageProcessor() {
    }

    /**
     * Converts a Bitmap into a direct FloatBuffer in planar NCHW layout [1, 3, 256, 256]
     * using MobileCLIP-S0 standard scaling (1.0f / 255.0f).
     */
    public static FloatBuffer toPlanarFloatBuffer(Bitmap bitmap) {
        return toPlanarFloatBuffer(bitmap, DEFAULT_WIDTH, DEFAULT_HEIGHT, null, null);
    }

    /**
     * Converts a Bitmap into a direct FloatBuffer in planar NCHW layout [1, 3, targetWidth, targetHeight]
     * using MobileCLIP-S0 standard scaling (1.0f / 255.0f).
     */
    public static FloatBuffer toPlanarFloatBuffer(Bitmap bitmap, int targetWidth, int targetHeight) {
        return toPlanarFloatBuffer(bitmap, targetWidth, targetHeight, null, null);
    }

    /**
     * Converts a Bitmap into a direct FloatBuffer in planar NCHW layout with custom mean/std normalization.
     */
    public static FloatBuffer toPlanarFloatBuffer(Bitmap bitmap, int targetWidth, int targetHeight, float[] mean, float[] std) {
        if (bitmap == null || bitmap.isRecycled()) {
            return null;
        }

        Bitmap scaledBitmap = centerCropAndResize(bitmap, targetWidth, targetHeight);
        if (scaledBitmap == null) {
            return null;
        }

        int numPixels = targetWidth * targetHeight;
        int[] pixels = new int[numPixels];
        scaledBitmap.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);

        // Recycle intermediate bitmap if a new copy was created
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle();
        }

        // Direct ByteBuffer allocation for zero-copy native JNI passing to ONNX Runtime
        int totalFloats = CHANNELS * numPixels;
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(totalFloats * 4).order(ByteOrder.nativeOrder());
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();

        float[] planarArray = new float[totalFloats];
        int rOffset = 0;
        int gOffset = numPixels;
        int bOffset = 2 * numPixels;

        if (mean != null && std != null && mean.length >= 3 && std.length >= 3) {
            float meanR = mean[0], meanG = mean[1], meanB = mean[2];
            float invStdR = 1.0f / std[0], invStdG = 1.0f / std[1], invStdB = 1.0f / std[2];
            for (int i = 0; i < numPixels; i++) {
                int color = pixels[i];
                float rNorm = ((color >> 16) & 0xFF) * DEFAULT_SCALE;
                float gNorm = ((color >> 8) & 0xFF) * DEFAULT_SCALE;
                float bNorm = (color & 0xFF) * DEFAULT_SCALE;

                planarArray[rOffset + i] = (rNorm - meanR) * invStdR;
                planarArray[gOffset + i] = (gNorm - meanG) * invStdG;
                planarArray[bOffset + i] = (bNorm - meanB) * invStdB;
            }
        } else {
            for (int i = 0; i < numPixels; i++) {
                int color = pixels[i];
                planarArray[rOffset + i] = ((color >> 16) & 0xFF) * DEFAULT_SCALE;
                planarArray[gOffset + i] = ((color >> 8) & 0xFF) * DEFAULT_SCALE;
                planarArray[bOffset + i] = (color & 0xFF) * DEFAULT_SCALE;
            }
        }

        floatBuffer.put(planarArray);
        floatBuffer.rewind();
        return floatBuffer;
    }

    /**
     * Resizes a Bitmap to target dimensions using aspect-ratio preserving center crop
     * and high-quality bilinear filtering (Paint.FILTER_BITMAP_FLAG).
     */
    public static Bitmap centerCropAndResize(Bitmap src, int targetWidth, int targetHeight) {
        if (src == null || src.isRecycled()) {
            return null;
        }

        int srcWidth = src.getWidth();
        int srcHeight = src.getHeight();

        if (srcWidth <= 0 || srcHeight <= 0) {
            return null;
        }

        // Return unchanged if already matches target dimensions and software ARGB_8888 config
        if (srcWidth == targetWidth && srcHeight == targetHeight && src.getConfig() == Bitmap.Config.ARGB_8888) {
            return src;
        }

        float scale = Math.max((float) targetWidth / srcWidth, (float) targetHeight / srcHeight);
        float scaledWidth = scale * srcWidth;
        float scaledHeight = scale * srcHeight;
        float dx = (targetWidth - scaledWidth) * 0.5f;
        float dy = (targetHeight - scaledHeight) * 0.5f;

        Bitmap targetBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(targetBitmap);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);

        canvas.drawBitmap(src, matrix, paint);
        return targetBitmap;
    }
}
