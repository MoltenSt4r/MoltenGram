/*
 * Copyright 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.messenger;

import android.content.Context;
import static org.telegram.messenger.AndroidUtilities.readRes;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.google.zxing.qrcode.encoder.QRCode;

import androidx.core.content.ContextCompat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.Md3AvatarShapes;
import org.telegram.ui.ActionBar.Theme;
import tw.nekomimi.nekogram.helpers.MonetHelper;

/**
 * This object renders a QR Code as a BitMatrix 2D array of greyscale values.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class TelegramQRCodeWriter {

    private static final int QUIET_ZONE_SIZE = 4;
    private ByteMatrix input;
    private float[] radii = new float[8];
    private int imageBloks;
    private int imageBlockX;
    private int sideQuadSize;

    private int imageSize;
    private Drawable centerDrawable;

    public void setCenterDrawable(Drawable drawable) {
        centerDrawable = drawable;
    }

    public Bitmap encode(String contents, BarcodeFormat format, int width, int height, Map<EncodeHintType, ?> hints, Bitmap bitmap, Context context) throws WriterException {
        return encode(contents, format, width, height, hints, bitmap, context, null);
    }

    public Bitmap encode(String contents, BarcodeFormat format, int width, int height, Map<EncodeHintType, ?> hints, Bitmap bitmap, Context context, Function<Integer, Bitmap> iconF) throws WriterException {

        if (contents.isEmpty()) {
            throw new IllegalArgumentException("Found empty contents");
        }

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Requested dimensions are too small: " + width + 'x' + height);
        }

        ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
        int quietZone = QUIET_ZONE_SIZE;
        if (hints != null) {
            if (hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
                errorCorrectionLevel = ErrorCorrectionLevel.valueOf(hints.get(EncodeHintType.ERROR_CORRECTION).toString());
            }
            if (hints.containsKey(EncodeHintType.MARGIN)) {
                quietZone = Integer.parseInt(hints.get(EncodeHintType.MARGIN).toString());
            }
        }

        QRCode code = Encoder.encode(contents, errorCorrectionLevel, hints);

        input = code.getMatrix();
        if (input == null) {
            throw new IllegalStateException();
        }
        int inputWidth = input.getWidth();
        int inputHeight = input.getHeight();

        for (int x = 0; x < inputWidth; x++) {
            if (has(x, 0)) {
                sideQuadSize++;
            } else {
                break;
            }
        }

        int qrWidth = inputWidth + (quietZone * 2);
        int qrHeight = inputHeight + (quietZone * 2);
        int outputWidth = Math.max(width, qrWidth);
        int outputHeight = Math.max(height, qrHeight);

        int multiple = Math.min(outputWidth / qrWidth, outputHeight / qrHeight);

        int padding = 16;

        int size = multiple * inputWidth + padding * 2;
        if (bitmap == null || bitmap.getWidth() != size) {
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xffffffff);
        Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(0xff000000);

        GradientDrawable rect = new GradientDrawable();
        rect.setShape(GradientDrawable.RECTANGLE);
        rect.setCornerRadii(radii);

        imageBloks = Math.round((size - 32) / 4.65f / multiple);
        if (imageBloks % 2 != inputWidth % 2) {
            imageBloks++;
        }
        imageBlockX = (inputWidth - imageBloks) / 2;
        imageSize = imageBloks * multiple - 24;
        int imageX = (size - imageSize) / 2;

        final boolean isMd3 = AppearanceConfig.md3QrCode();
        int[] md3Colors = null;
        int color1 = 0xff000000;
        if (isMd3) {
            int c1 = MonetHelper.getColor("a1_600");
            if (c1 == 0) c1 = Theme.getColor(Theme.key_featuredStickers_addButton);
            int c2 = MonetHelper.getColor("a2_600");
            if (c2 == 0) c2 = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
            int c3 = MonetHelper.getColor("a3_600");
            if (c3 == 0) c3 = MonetHelper.getColor("a1_400");
            if (c3 == 0) c3 = c1;
            c1 |= 0xFF000000;
            c2 |= 0xFF000000;
            c3 |= 0xFF000000;
            md3Colors = new int[]{c1, c2, c3};
            color1 = c1;
            blackPaint.setColor(c1);
        }
        int qrShape = isMd3 ? AppearanceConfig.QR_SHAPE_SQUIRCLE : (AppearanceConfig.md3QrCode() ? AppearanceConfig.md3QrPixelShape() : AppearanceConfig.QR_SHAPE_ROUNDED);
        float sideQuadPx = sideQuadSize * multiple;

        for (int a = 0; a < 3; a++) {
            int x, y;
            if (a == 0) {
                x = padding;
                y = padding;
            } else if (a == 1) {
                x = size - sideQuadSize * multiple - padding;
                y = padding;
            } else {
                x = padding;
                y = size - sideQuadSize * multiple - padding;
            }

            float r;
            if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                r = sideQuadPx / 2.0f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                r = sideQuadPx * 0.32f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                r = 0;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                r = sideQuadPx * 0.22f;
            } else {
                r = (sideQuadSize * multiple) / 3.0f;
            }
            Arrays.fill(radii, r);

            rect.setColor(color1);
            rect.setBounds(x, y, x + sideQuadSize * multiple, y + sideQuadSize * multiple);
            rect.draw(canvas);

            canvas.drawRect(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple, blackPaint);

            float rInner;
            if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                rInner = (sideQuadPx - multiple * 2) / 2.0f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                rInner = (sideQuadPx - multiple * 2) * 0.28f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                rInner = 0;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                rInner = (sideQuadPx - multiple * 2) * 0.2f;
            } else {
                rInner = (sideQuadSize * multiple) / 4.0f;
            }
            Arrays.fill(radii, rInner);
            rect.setColor(0xffffffff);
            rect.setBounds(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple);
            rect.draw(canvas);

            float rCenter;
            if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                rCenter = ((sideQuadSize - 4) * multiple) / 2.0f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                rCenter = ((sideQuadSize - 4) * multiple) * 0.32f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                rCenter = 0;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                rCenter = ((sideQuadSize - 4) * multiple) * 0.22f;
            } else {
                rCenter = ((sideQuadSize - 2) * multiple) / 4.0f;
            }
            if (isMd3) {
                int shapeId = (a == 1) ? AppearanceConfig.md3QrTopRightShape() : ((a == 2) ? AppearanceConfig.md3QrBottomLeftShape() : Md3AvatarShapes.SHAPE_COOKIE_4);
                float fSize = (sideQuadSize - 4) * multiple;
                Path finderShape = Md3AvatarShapes.INSTANCE.getShapePath(shapeId, fSize, fSize);
                canvas.save();
                canvas.translate(x + multiple * 2, y + multiple * 2);
                Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
                fp.setColor(color1);
                canvas.drawPath(finderShape, fp);
                canvas.restore();
            } else {
                Arrays.fill(radii, rCenter);
                rect.setColor(color1);
                rect.setBounds(x + multiple * 2, y + multiple * 2, x + (sideQuadSize - 2) * multiple, y + (sideQuadSize - 2) * multiple);
                rect.draw(canvas);
            }
        }
        float r = multiple / 2.0f;
        RectF sqRectF = new RectF();
        Path pathHelper = new Path();

        float modulePad1 = multiple * 0.08f;
        float moduleSize1 = multiple - modulePad1 * 2f;
        Path[] md3ShapesList1 = null;
        Path trShape1 = null;
        Path blShape1 = null;
        if (isMd3) {
            Path md3Cookie = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_COOKIE_4, moduleSize1, moduleSize1);
            Path md3Clover = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_CLOVER_4, moduleSize1, moduleSize1);
            Path md3Diamond = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_DIAMOND, moduleSize1, moduleSize1);
            Path md3Square = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_SQUARE, moduleSize1, moduleSize1);
            Path md3Circle = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_CIRCLE, moduleSize1, moduleSize1);
            trShape1 = Md3AvatarShapes.INSTANCE.getShapePath(AppearanceConfig.md3QrTopRightShape(), moduleSize1, moduleSize1);
            blShape1 = Md3AvatarShapes.INSTANCE.getShapePath(AppearanceConfig.md3QrBottomLeftShape(), moduleSize1, moduleSize1);
            md3ShapesList1 = new Path[]{md3Cookie, md3Clover, md3Diamond, md3Square, md3Circle};
        }
        Paint md3Paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);

        for (int y = 0, outputY = padding; y < inputHeight; y++, outputY += multiple) {
            for (int x = 0, outputX = padding; x < inputWidth; x++, outputX += multiple) {
                if (has(x, y)) {
                    if (isMd3 && md3ShapesList1 != null) {
                        int cIdx = Math.abs((x * 37 + y * 19 + (x ^ y) * 13)) % md3Colors.length;
                        Path shape;
                        if (x >= inputWidth / 2 && y < inputHeight / 2 && trShape1 != null) {
                            shape = trShape1;
                        } else if (x < inputWidth / 2 && y >= inputHeight / 2 && blShape1 != null) {
                            shape = blShape1;
                        } else {
                            int sIdx = Math.abs((x * 17 + y * 31 + (x * y) * 7)) % md3ShapesList1.length;
                            shape = md3ShapesList1[sIdx];
                        }
                        md3Paint1.setColor(md3Colors[cIdx]);
                        canvas.save();
                        canvas.translate(outputX + modulePad1, outputY + modulePad1);
                        canvas.drawPath(shape, md3Paint1);
                        canvas.restore();
                    } else if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                        canvas.drawCircle(outputX + multiple / 2.0f, outputY + multiple / 2.0f, multiple * 0.44f, blackPaint);
                    } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                        float pad = multiple * 0.06f;
                        sqRectF.set(outputX + pad, outputY + pad, outputX + multiple - pad, outputY + multiple - pad);
                        canvas.drawRoundRect(sqRectF, multiple * 0.35f, multiple * 0.35f, blackPaint);
                    } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                        pathHelper.reset();
                        pathHelper.moveTo(outputX + multiple / 2.0f, outputY + multiple * 0.08f);
                        pathHelper.lineTo(outputX + multiple * 0.92f, outputY + multiple / 2.0f);
                        pathHelper.lineTo(outputX + multiple / 2.0f, outputY + multiple * 0.92f);
                        pathHelper.lineTo(outputX + multiple * 0.08f, outputY + multiple / 2.0f);
                        pathHelper.close();
                        canvas.drawPath(pathHelper, blackPaint);
                    } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                        canvas.drawRect(outputX, outputY, outputX + multiple, outputY + multiple, blackPaint);
                    } else {
                        Arrays.fill(radii, r);
                        if (has(x, y - 1)) {
                            radii[0] = radii[1] = 0;
                            radii[2] = radii[3] = 0;
                        }
                        if (has(x, y + 1)) {
                            radii[6] = radii[7] = 0;
                            radii[4] = radii[5] = 0;
                        }
                        if (has(x - 1, y)) {
                            radii[0] = radii[1] = 0;
                            radii[6] = radii[7] = 0;
                        }
                        if (has(x + 1, y)) {
                            radii[2] = radii[3] = 0;
                            radii[4] = radii[5] = 0;
                        }
                        rect.setColor(0xff000000);
                        rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
                        rect.draw(canvas);
                    }
                } else {
                    if (qrShape == AppearanceConfig.QR_SHAPE_ROUNDED) {
                        boolean has = false;
                        Arrays.fill(radii, 0);
                        if (has(x - 1, y - 1) && has(x - 1, y) && has(x, y - 1)) {
                            radii[0] = radii[1] = r;
                            has = true;
                        }
                        if (has(x + 1, y - 1) && has(x + 1, y) && has(x, y - 1)) {
                            radii[2] = radii[3] = r;
                            has = true;
                        }
                        if (has(x - 1, y + 1) && has(x - 1, y) && has(x, y + 1)) {
                            radii[6] = radii[7] = r;
                            has = true;
                        }
                        if (has(x + 1, y + 1) && has(x + 1, y) && has(x, y + 1)) {
                            radii[4] = radii[5] = r;
                            has = true;
                        }
                        if (has) {
                            canvas.drawRect(outputX, outputY, outputX + multiple, outputY + multiple, blackPaint);
                            rect.setColor(0xffffffff);
                            rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
                            rect.draw(canvas);
                        }
                    }
                }
            }
        }

        if (isMd3) {
            Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            centerPaint.setColor(color1);
            float cx = imageX + imageSize / 2f;
            float cy = imageX + imageSize / 2f;
            float cr = imageSize / 2f;
            canvas.drawCircle(cx, cy, cr, centerPaint);
            try {
                Drawable icon = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.moltenstar_icon_monochrome);
                if (icon == null) {
                    icon = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.moltenstar_icon_foreground);
                }
                if (icon != null) {
                    icon = icon.mutate();
                    icon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                    int pad = (int) (cr * 0.28f);
                    icon.setBounds((int) (cx - cr + pad), (int) (cy - cr + pad), (int) (cx + cr - pad), (int) (cy + cr - pad));
                    icon.draw(canvas);
                }
            } catch (Exception ignore) {}
        } else if (centerDrawable != null) {
            centerDrawable.setBounds(imageX, imageX, imageX + imageSize, imageX + imageSize);
            centerDrawable.draw(canvas);
        } else if (iconF != null) {
            Bitmap icon = iconF.apply(imageSize);
            canvas.drawBitmap(icon, imageX, imageX, null);
            icon.recycle();
        } else {
            Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.exteraless_icon);
            drawable.setBounds(imageX, imageX, imageX + imageSize, imageX + imageSize);
            drawable.draw(canvas);
        }

        canvas.setBitmap(null);

        return bitmap;
    }

    private boolean has(int x, int y) {
        if (x >= imageBlockX && x < imageBlockX + imageBloks && y >= imageBlockX && y < imageBlockX + imageBloks) {
            return false;
        }
        if ((x < sideQuadSize || x >= input.getWidth() - sideQuadSize) && y < sideQuadSize) {
            return false;
        }
        if (x < sideQuadSize && y >= input.getHeight() - sideQuadSize) {
            return false;
        }
        return x >= 0 && y >= 0 && x < input.getWidth() && y < input.getHeight() && input.get(x, y) == 1;
    }

    public Bitmap encode(String contents, int width, int height, Map<EncodeHintType, ?> hints, Bitmap bitmap) throws WriterException {
        return encode(contents, width, height, hints, bitmap, 1.0f, 0xffffffff, 0xff000000);
    }

    public Bitmap encode(String contents, int width, int height, Map<EncodeHintType, ?> hints, Bitmap bitmap, float radiusFactor, int backgroundColor, int color) throws WriterException {

        if (contents.isEmpty()) {
            throw new IllegalArgumentException("Found empty contents");
        }

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Requested dimensions are too small: " + width + 'x' + height);
        }

        ErrorCorrectionLevel errorCorrectionLevel = ErrorCorrectionLevel.L;
        int quietZone = QUIET_ZONE_SIZE;
        if (hints != null) {
            if (hints.containsKey(EncodeHintType.ERROR_CORRECTION)) {
                errorCorrectionLevel = ErrorCorrectionLevel.valueOf(hints.get(EncodeHintType.ERROR_CORRECTION).toString());
            }
            if (hints.containsKey(EncodeHintType.MARGIN)) {
                quietZone = Integer.parseInt(hints.get(EncodeHintType.MARGIN).toString());
            }
        }

        QRCode code = Encoder.encode(contents, errorCorrectionLevel, hints);

        input = code.getMatrix();
        if (input == null) {
            throw new IllegalStateException();
        }
        int inputWidth = input.getWidth();
        int inputHeight = input.getHeight();

        for (int x = 0; x < inputWidth; x++) {
            if (has(x, 0)) {
                sideQuadSize++;
            } else {
                break;
            }
        }

        int qrWidth = inputWidth + (quietZone * 2);
        int qrHeight = inputHeight + (quietZone * 2);
        int outputWidth = Math.max(width, qrWidth);
        int outputHeight = Math.max(height, qrHeight);

        int multiple = Math.min(outputWidth / qrWidth, outputHeight / qrHeight);

        int padding = 16;

        int size = multiple * inputWidth + padding * 2;
        if (bitmap == null || bitmap.getWidth() != size) {
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        }
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(backgroundColor);
        Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        blackPaint.setColor(color);

        GradientDrawable rect = new GradientDrawable();
        rect.setShape(GradientDrawable.RECTANGLE);
        rect.setCornerRadii(radii);

        imageBloks = Math.round((size - 32) / 4.65f / multiple);
        if (imageBloks % 2 != inputWidth % 2) {
            imageBloks++;
        }
        imageBlockX = (inputWidth - imageBloks) / 2;
        imageSize = imageBloks * multiple - 24;
        int imageX = (size - imageSize) / 2;

        boolean isTransparentBackground = Color.alpha(backgroundColor) == 0;
        Path clipPath = new Path();
        RectF rectF = new RectF();
        final boolean isMd3 = AppearanceConfig.md3QrCode();
        int[] md3Colors = null;
        if (isMd3) {
            int c1 = MonetHelper.getColor("a1_600");
            if (c1 == 0) c1 = Theme.getColor(Theme.key_featuredStickers_addButton);
            int c2 = MonetHelper.getColor("a2_600");
            if (c2 == 0) c2 = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
            int c3 = MonetHelper.getColor("a3_600");
            if (c3 == 0) c3 = MonetHelper.getColor("a1_400");
            if (c3 == 0) c3 = c1;
            c1 |= 0xFF000000;
            c2 |= 0xFF000000;
            c3 |= 0xFF000000;
            md3Colors = new int[]{c1, c2, c3};
            color = c1;
            blackPaint.setColor(c1);
            rect.setColor(c1);
        }
        int qrShape = isMd3 ? AppearanceConfig.QR_SHAPE_SQUIRCLE : (AppearanceConfig.md3QrCode() ? AppearanceConfig.md3QrPixelShape() : AppearanceConfig.QR_SHAPE_ROUNDED);
        float sideQuadPx = sideQuadSize * multiple;

        for (int a = 0; a < 3; a++) {
            int x, y;
            if (a == 0) {
                x = padding;
                y = padding;
            } else if (a == 1) {
                x = size - sideQuadSize * multiple - padding;
                y = padding;
            } else {
                x = padding;
                y = size - sideQuadSize * multiple - padding;
            }

            float r;
            if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                r = sideQuadPx / 2.0f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                r = sideQuadPx * 0.32f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                r = 0;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                r = sideQuadPx * 0.22f;
            } else {
                r = (sideQuadSize * multiple) / 3.0f * radiusFactor;
            }

            if (isTransparentBackground) {
                rectF.set(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple);
                float rHole;
                if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                    rHole = (sideQuadPx - multiple * 2) / 2.0f;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                    rHole = (sideQuadPx - multiple * 2) * 0.28f;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                    rHole = 0;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                    rHole = (sideQuadPx - multiple * 2) * 0.2f;
                } else {
                    rHole = (sideQuadSize * multiple) / 4.0f * radiusFactor;
                }
                clipPath.reset();
                clipPath.addRoundRect(rectF, rHole, rHole, Path.Direction.CW);
                clipPath.close();
                canvas.save();
                canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
            }
            Arrays.fill(radii, r);
            rect.setColor(color);
            rect.setBounds(x, y, x + sideQuadSize * multiple, y + sideQuadSize * multiple);
            rect.draw(canvas);
            canvas.drawRect(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple, blackPaint);
            if (isTransparentBackground) {
                canvas.restore();
            }

            if (!isTransparentBackground) {
                float rInner;
                if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                    rInner = (sideQuadPx - multiple * 2) / 2.0f;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                    rInner = (sideQuadPx - multiple * 2) * 0.28f;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                    rInner = 0;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                    rInner = (sideQuadPx - multiple * 2) * 0.2f;
                } else {
                    rInner = (sideQuadSize * multiple) / 4.0f * radiusFactor;
                }
                Arrays.fill(radii, rInner);
                rect.setColor(backgroundColor);
                rect.setBounds(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple);
                rect.draw(canvas);
            }

            float rCenter;
            if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                rCenter = ((sideQuadSize - 4) * multiple) / 2.0f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                rCenter = ((sideQuadSize - 4) * multiple) * 0.32f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                rCenter = 0;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                rCenter = ((sideQuadSize - 4) * multiple) * 0.22f;
            } else {
                rCenter = ((sideQuadSize - 2) * multiple) / 4.0f * radiusFactor;
            }
            if (isMd3) {
                int shapeId = (a == 1) ? AppearanceConfig.md3QrTopRightShape() : ((a == 2) ? AppearanceConfig.md3QrBottomLeftShape() : Md3AvatarShapes.SHAPE_COOKIE_4);
                float fSize = (sideQuadSize - 4) * multiple;
                Path finderShape = Md3AvatarShapes.INSTANCE.getShapePath(shapeId, fSize, fSize);
                canvas.save();
                canvas.translate(x + multiple * 2, y + multiple * 2);
                Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
                fp.setColor(color);
                canvas.drawPath(finderShape, fp);
                canvas.restore();
            } else {
                Arrays.fill(radii, rCenter);
                rect.setColor(color);
                rect.setBounds(x + multiple * 2, y + multiple * 2, x + (sideQuadSize - 2) * multiple, y + (sideQuadSize - 2) * multiple);
                rect.draw(canvas);
            }
        }
        float r = multiple / 2.0f * radiusFactor;
        Path pathHelper = new Path();

        float modulePad2 = multiple * 0.08f;
        float moduleSize2 = multiple - modulePad2 * 2f;
        Path[] md3ShapesList2 = null;
        Path trShape2 = null;
        Path blShape2 = null;
        if (isMd3) {
            Path md3Cookie = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_COOKIE_4, moduleSize2, moduleSize2);
            Path md3Clover = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_CLOVER_4, moduleSize2, moduleSize2);
            Path md3Diamond = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_DIAMOND, moduleSize2, moduleSize2);
            Path md3Square = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_SQUARE, moduleSize2, moduleSize2);
            Path md3Circle = Md3AvatarShapes.INSTANCE.getShapePath(Md3AvatarShapes.SHAPE_CIRCLE, moduleSize2, moduleSize2);
            trShape2 = Md3AvatarShapes.INSTANCE.getShapePath(AppearanceConfig.md3QrTopRightShape(), moduleSize2, moduleSize2);
            blShape2 = Md3AvatarShapes.INSTANCE.getShapePath(AppearanceConfig.md3QrBottomLeftShape(), moduleSize2, moduleSize2);
            md3ShapesList2 = new Path[]{md3Cookie, md3Clover, md3Diamond, md3Square, md3Circle};
        }
        Paint md3Paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

        for (int y = 0, outputY = padding; y < inputHeight; y++, outputY += multiple) {
            for (int x = 0, outputX = padding; x < inputWidth; x++, outputX += multiple) {
                if (has(x, y)) {
                    if (isMd3 && md3ShapesList2 != null) {
                        int cIdx = Math.abs((x * 37 + y * 19 + (x ^ y) * 13)) % md3Colors.length;
                        Path shape;
                        if (x >= inputWidth / 2 && y < inputHeight / 2 && trShape2 != null) {
                            shape = trShape2;
                        } else if (x < inputWidth / 2 && y >= inputHeight / 2 && blShape2 != null) {
                            shape = blShape2;
                        } else {
                            int sIdx = Math.abs((x * 17 + y * 31 + (x * y) * 7)) % md3ShapesList2.length;
                            shape = md3ShapesList2[sIdx];
                        }
                        md3Paint2.setColor(md3Colors[cIdx]);
                        canvas.save();
                        canvas.translate(outputX + modulePad2, outputY + modulePad2);
                        canvas.drawPath(shape, md3Paint2);
                        canvas.restore();
                    } else if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                        canvas.drawCircle(outputX + multiple / 2.0f, outputY + multiple / 2.0f, multiple * 0.44f, blackPaint);
                    } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                        float pad = multiple * 0.06f;
                        rectF.set(outputX + pad, outputY + pad, outputX + multiple - pad, outputY + multiple - pad);
                        canvas.drawRoundRect(rectF, multiple * 0.35f, multiple * 0.35f, blackPaint);
                    } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                        pathHelper.reset();
                        pathHelper.moveTo(outputX + multiple / 2.0f, outputY + multiple * 0.08f);
                        pathHelper.lineTo(outputX + multiple * 0.92f, outputY + multiple / 2.0f);
                        pathHelper.lineTo(outputX + multiple / 2.0f, outputY + multiple * 0.92f);
                        pathHelper.lineTo(outputX + multiple * 0.08f, outputY + multiple / 2.0f);
                        pathHelper.close();
                        canvas.drawPath(pathHelper, blackPaint);
                    } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                        canvas.drawRect(outputX, outputY, outputX + multiple, outputY + multiple, blackPaint);
                    } else {
                        Arrays.fill(radii, r);
                        if (has(x, y - 1)) {
                            radii[0] = radii[1] = 0;
                            radii[2] = radii[3] = 0;
                        }
                        if (has(x, y + 1)) {
                            radii[6] = radii[7] = 0;
                            radii[4] = radii[5] = 0;
                        }
                        if (has(x - 1, y)) {
                            radii[0] = radii[1] = 0;
                            radii[6] = radii[7] = 0;
                        }
                        if (has(x + 1, y)) {
                            radii[2] = radii[3] = 0;
                            radii[4] = radii[5] = 0;
                        }
                        rect.setColor(color);
                        rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
                        rect.draw(canvas);
                    }
                } else {
                    if (qrShape == AppearanceConfig.QR_SHAPE_ROUNDED) {
                        boolean has = false;
                        Arrays.fill(radii, 0);
                        if (has(x - 1, y - 1) && has(x - 1, y) && has(x, y - 1)) {
                            radii[0] = radii[1] = r;
                            has = true;
                        }
                        if (has(x + 1, y - 1) && has(x + 1, y) && has(x, y - 1)) {
                            radii[2] = radii[3] = r;
                            has = true;
                        }
                        if (has(x - 1, y + 1) && has(x - 1, y) && has(x, y + 1)) {
                            radii[6] = radii[7] = r;
                            has = true;
                        }
                        if (has(x + 1, y + 1) && has(x + 1, y) && has(x, y + 1)) {
                            radii[4] = radii[5] = r;
                            has = true;
                        }
                        if (has && !isTransparentBackground) {
                            canvas.drawRect(outputX, outputY, outputX + multiple, outputY + multiple, blackPaint);
                            rect.setColor(backgroundColor);
                            rect.setBounds(outputX, outputY, outputX + multiple, outputY + multiple);
                            rect.draw(canvas);
                        }
                    }
                }
            }
        }

        if (isMd3) {
            Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            centerPaint.setColor(color);
            float cx = imageX + imageSize / 2f;
            float cy = imageX + imageSize / 2f;
            float cr = imageSize / 2f;
            canvas.drawCircle(cx, cy, cr, centerPaint);
            try {
                Drawable icon = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.moltenstar_icon_monochrome);
                if (icon == null) {
                    icon = ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.moltenstar_icon_foreground);
                }
                if (icon != null) {
                    icon = icon.mutate();
                    icon.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                    int pad = (int) (cr * 0.28f);
                    icon.setBounds((int) (cx - cr + pad), (int) (cy - cr + pad), (int) (cx + cr - pad), (int) (cy + cr - pad));
                    icon.draw(canvas);
                }
            } catch (Exception ignore) {}
        } else if (centerDrawable != null) {
            centerDrawable.setBounds(imageX, imageX, imageX + imageSize, imageX + imageSize);
            centerDrawable.draw(canvas);
        } else {
            Bitmap icon = SvgHelper.getBitmap(readRes(R.raw.qr_logo), imageSize, imageSize, false);
            canvas.drawBitmap(icon, imageX, imageX, null);
            icon.recycle();
        }

        canvas.setBitmap(null);

        return bitmap;
    }

    public static void drawSideQuads(Canvas canvas, float xOffset, float yOffset, Paint blackPaint, float sideQuadSize, float multiple, int padding, float size, float radiusFactor, float[] radii, boolean isTransparentBackground) {
        Path clipPath = new Path();
        int qrShape = AppearanceConfig.md3QrCode() ? AppearanceConfig.md3QrPixelShape() : AppearanceConfig.QR_SHAPE_ROUNDED;
        float sideQuadPx = sideQuadSize * multiple;
        for (int a = 0; a < 3; a++) {
            float x, y;
            if (a == 0) {
                x = padding;
                y = padding;
            } else if (a == 1) {
                x = size - sideQuadSize * multiple - padding;
                y = padding;
            } else {
                x = padding;
                y = size - sideQuadSize * multiple - padding;
            }

            x += xOffset;
            y += yOffset;

            float r;
            if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                r = sideQuadPx / 2.0f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                r = sideQuadPx * 0.32f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                r = 0;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                r = sideQuadPx * 0.22f;
            } else {
                r = (sideQuadSize * multiple) / 3.0f * radiusFactor;
            }

            if (isTransparentBackground) {
                AndroidUtilities.rectTmp.set(x + multiple, y + multiple, x + (sideQuadSize - 1) * multiple, y + (sideQuadSize - 1) * multiple);
                float rHole;
                if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                    rHole = (sideQuadPx - multiple * 2) / 2.0f;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                    rHole = (sideQuadPx - multiple * 2) * 0.28f;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                    rHole = 0;
                } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                    rHole = (sideQuadPx - multiple * 2) * 0.2f;
                } else {
                    rHole = (sideQuadSize * multiple) / 4.0f * radiusFactor;
                }
                clipPath.reset();
                clipPath.addRoundRect(AndroidUtilities.rectTmp, rHole, rHole, Path.Direction.CW);
                clipPath.close();
                canvas.save();
                canvas.clipPath(clipPath, Region.Op.DIFFERENCE);
            }
            AndroidUtilities.rectTmp.set(x, y, x + sideQuadSize * multiple, y + sideQuadSize * multiple);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, r, r, blackPaint);
            if (isTransparentBackground) {
                canvas.restore();
            }

            float rCenter;
            if (qrShape == AppearanceConfig.QR_SHAPE_DOTS) {
                rCenter = ((sideQuadSize - 4) * multiple) / 2.0f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUIRCLE) {
                rCenter = ((sideQuadSize - 4) * multiple) * 0.32f;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_SQUARE) {
                rCenter = 0;
            } else if (qrShape == AppearanceConfig.QR_SHAPE_DIAMOND) {
                rCenter = ((sideQuadSize - 4) * multiple) * 0.22f;
            } else {
                rCenter = ((sideQuadSize - 2) * multiple) / 4.0f * radiusFactor;
            }
            AndroidUtilities.rectTmp.set(x + multiple * 2, y + multiple * 2, x + (sideQuadSize - 2) * multiple, y + (sideQuadSize - 2) * multiple);
            canvas.drawRoundRect(AndroidUtilities.rectTmp, rCenter, rCenter, blackPaint);
        }
    }


    public int getImageSize() {
        return imageSize;
    }
}
