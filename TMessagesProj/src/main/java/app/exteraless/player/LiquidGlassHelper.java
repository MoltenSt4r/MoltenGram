package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.ApplicationLoader;

/**
 * Exact port of InterTune's Liquid Glass (com.kyant.backdrop + GlassBackdrop.kt).
 *
 * Implements:
 * - RoundedRectSDF (`radiusAt`, `sdRoundedRect`, `gradSdRoundedRect`, `circleMap`)
 * - `RoundedRectRefractionShaderString` and `RoundedRectRefractionWithDispersionShaderString` (7-band spectral chromatic aberration)
 * - `DefaultHighlightShaderString` (45° rim highlight with `BlendMode.PLUS` and `0.5dp` stroke)
 * - `VibrantColorFilter` (saturation = 1.5f)
 * - `GlassSpec` math (`refraction`, `frost = 1 - sqrt(intensity)`, `blur = lerp(6dp, 32dp, frost)`, `lensT = clamp(refraction, 0.15, 1.0)`, `tintAlpha`, `buttonTint`, `stillFill`)
 */
public final class LiquidGlassHelper {

    public static final float STILL_ALPHA = 0.98f;

    private static final String ROUNDED_RECT_SDF =
            "float radiusAt(float2 coord, float4 radii) {\n" +
            "    if (coord.x >= 0.0) {\n" +
            "        if (coord.y <= 0.0) return radii.y;\n" +
            "        else return radii.z;\n" +
            "    } else {\n" +
            "        if (coord.y <= 0.0) return radii.x;\n" +
            "        else return radii.w;\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
            "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
            "    float outside = length(max(cornerCoord, 0.0)) - radius;\n" +
            "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
            "    return outside + inside;\n" +
            "}\n" +
            "\n" +
            "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
            "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
            "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
            "        return sign(coord) * normalize(max(cornerCoord, 0.0));\n" +
            "    } else {\n" +
            "        float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
            "        return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
            "    }\n" +
            "}\n";

    public static final String ROUNDED_RECT_REFRACTION_SHADER =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float2 offset;\n" +
            "uniform float4 cornerRadii;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float depthEffect;\n" +
            ROUNDED_RECT_SDF +
            "float circleMap(float x) {\n" +
            "    return 1.0 - sqrt(1.0 - x * x);\n" +
            "}\n" +
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = (coord + offset) - halfSize;\n" +
            "    float radius = radiusAt(coord, cornerRadii);\n" +
            "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
            "    if (-sd >= refractionHeight) {\n" +
            "        return content.eval(coord);\n" +
            "    }\n" +
            "    sd = min(sd, 0.0);\n" +
            "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 centerNorm = length(centeredCoord) > 0.0001 ? normalize(centeredCoord) : float2(0.0, 0.0);\n" +
            "    float2 rawGrad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * centerNorm;\n" +
            "    float2 grad = length(rawGrad) > 0.0001 ? normalize(rawGrad) : float2(0.0, 0.0);\n" +
            "    float2 refractedCoord = coord + d * grad;\n" +
            "    return content.eval(refractedCoord);\n" +
            "}\n";

    public static final String ROUNDED_RECT_REFRACTION_DISPERSION_SHADER =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float2 offset;\n" +
            "uniform float4 cornerRadii;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float depthEffect;\n" +
            "uniform float chromaticAberration;\n" +
            ROUNDED_RECT_SDF +
            "float circleMap(float x) {\n" +
            "    return 1.0 - sqrt(1.0 - x * x);\n" +
            "}\n" +
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = (coord + offset) - halfSize;\n" +
            "    float radius = radiusAt(coord, cornerRadii);\n" +
            "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
            "    if (-sd >= refractionHeight) {\n" +
            "        return content.eval(coord);\n" +
            "    }\n" +
            "    sd = min(sd, 0.0);\n" +
            "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 centerNorm = length(centeredCoord) > 0.0001 ? normalize(centeredCoord) : float2(0.0, 0.0);\n" +
            "    float2 rawGrad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * centerNorm;\n" +
            "    float2 grad = length(rawGrad) > 0.0001 ? normalize(rawGrad) : float2(0.0, 0.0);\n" +
            "    float2 refractedCoord = coord + d * grad;\n" +
            "    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / max(1.0, halfSize.x * halfSize.y));\n" +
            "    float2 dispersedCoord = d * grad * dispersionIntensity;\n" +
            "    half4 color = half4(0.0);\n" +
            "    half4 red = content.eval(refractedCoord + dispersedCoord);\n" +
            "    color.r += red.r / 3.5;\n" +
            "    color.a += red.a / 7.0;\n" +
            "    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));\n" +
            "    color.r += orange.r / 3.5;\n" +
            "    color.g += orange.g / 7.0;\n" +
            "    color.a += orange.a / 7.0;\n" +
            "    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));\n" +
            "    color.r += yellow.r / 3.5;\n" +
            "    color.g += yellow.g / 3.5;\n" +
            "    color.a += yellow.a / 7.0;\n" +
            "    half4 green = content.eval(refractedCoord);\n" +
            "    color.g += green.g / 3.5;\n" +
            "    color.a += green.a / 7.0;\n" +
            "    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));\n" +
            "    color.g += cyan.g / 3.5;\n" +
            "    color.b += cyan.b / 3.0;\n" +
            "    color.a += cyan.a / 7.0;\n" +
            "    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));\n" +
            "    color.b += blue.b / 3.0;\n" +
            "    color.a += blue.a / 7.0;\n" +
            "    half4 purple = content.eval(refractedCoord - dispersedCoord);\n" +
            "    color.r += purple.r / 7.0;\n" +
            "    color.b += purple.b / 3.0;\n" +
            "    color.a += purple.a / 7.0;\n" +
            "    return color;\n" +
            "}\n";

    public static final String DEFAULT_HIGHLIGHT_SHADER =
            "uniform float2 size;\n" +
            "uniform float4 cornerRadii;\n" +
            "layout(color) uniform half4 color;\n" +
            "uniform float angle;\n" +
            "uniform float falloff;\n" +
            ROUNDED_RECT_SDF +
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = coord - halfSize;\n" +
            "    float radius = radiusAt(coord, cornerRadii);\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);\n" +
            "    float2 normal = float2(cos(angle), sin(angle));\n" +
            "    float d = dot(grad, normal);\n" +
            "    float intensity = pow(abs(d), falloff);\n" +
            "    return color * intensity;\n" +
            "}\n";

    private static ColorFilter vibrantColorFilter;

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 33;
    }

    public static boolean isBatterySaverStill() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && pm.isPowerSaveMode()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static ColorFilter getVibrantColorFilter() {
        if (vibrantColorFilter == null) {
            vibrantColorFilter = createColorControlsFilter(0f, 1f, 1.5f);
        }
        return vibrantColorFilter;
    }

    public static ColorFilter createColorControlsFilter(float brightness, float contrast, float saturation) {
        float invSat = 1f - saturation;
        float r = 0.213f * invSat;
        float g = 0.715f * invSat;
        float b = 0.072f * invSat;

        float c = contrast;
        float t = (0.5f - c * 0.5f + brightness) * 255f;
        float s = saturation;

        float cr = c * r;
        float cg = c * g;
        float cb = c * b;
        float cs = c * s;

        ColorMatrix cm = new ColorMatrix(new float[]{
                cr + cs, cg, cb, 0f, t,
                cr, cg + cs, cb, 0f, t,
                cr, cg, cb + cs, 0f, t,
                0f, 0f, 0f, 1f, 0f
        });
        return new ColorMatrixColorFilter(cm);
    }

    public static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Exact GlassSpec math from InterTune's GlassBackdrop.kt.
     */
    public static final class GlassSpec {
        public final float intensity;
        public final boolean still;
        public final float refraction;
        public final float frost;
        public final float blurPx;
        public final float lensT;

        public static GlassSpec of(float intensity) {
            return new GlassSpec(intensity, isBatterySaverStill());
        }

        public GlassSpec(float intensity, boolean still) {
            this.intensity = clamp01(intensity);
            this.still = still;
            this.refraction = this.intensity;
            this.frost = 1f - (float) Math.sqrt(this.intensity);
            this.blurPx = dpf2(lerp(6f, 32f, this.frost));
            this.lensT = Math.max(0.15f, Math.min(1f, this.refraction));
        }

        public float tintAlpha(float min, float max) {
            return lerp(min, max, frost);
        }

        public int tintColor(int surfaceColorAtElevation, float min, float max) {
            int alpha = Math.round(255f * clamp01(tintAlpha(min, max)));
            return ColorUtils.setAlphaComponent(surfaceColorAtElevation, alpha);
        }

        public int floatingTint(int surfaceColorAtElevation) {
            return tintColor(surfaceColorAtElevation, 0.66f, 0.98f);
        }

        public int buttonTint(int surfaceColor, int primaryContainerColor) {
            boolean dark = ColorUtils.calculateLuminance(surfaceColor) < 0.5;
            float floor = dark ? 0.75f : 0.55f;
            int alpha = Math.round(255f * clamp01(tintAlpha(floor, 0.95f)));
            return ColorUtils.setAlphaComponent(primaryContainerColor, alpha);
        }

        public int stillFill(int surfaceColorAtElevation) {
            return ColorUtils.setAlphaComponent(surfaceColorAtElevation, Math.round(255f * STILL_ALPHA));
        }
    }

    /**
     * Source that records a backdrop layer so multiple glass panes can refract it without recursion.
     */
    public interface BackdropDrawer {
        void drawBackdrop(@NonNull Canvas canvas, int width, int height);
    }

    /**
     * Reusable hardware-accelerated Liquid Glass renderer for a single pane/button/bar.
     * Uses Android 13+ RenderNode + RenderEffect + RuntimeShader.
     */
    public static final class GlassPane {
        private RenderNode contentNode;
        private RuntimeShader refractionShader;
        private RuntimeShader dispersionShader;
        private RuntimeShader highlightShader;

        private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clipPath = new Path();
        private final RectF rect = new RectF();
        private final float[] pathRadii = new float[8];

        private float lastW = -1;
        private float lastH = -1;
        private float lastR0 = -1, lastR1 = -1, lastR2 = -1, lastR3 = -1;
        private float lastBlur = -1;
        private float lastRefractionH = -1;
        private float lastRefractionAmt = -1;
        private boolean lastDepth = false;
        private boolean lastDispersion = false;
        private boolean lastVibrancy = false;

        public GlassPane() {
            highlightPaint.setStyle(Paint.Style.STROKE);
        }

        /**
         * Draws a Liquid Glass pane with symmetric corner radius {@code radiusPx}.
         */
        public void draw(
                @NonNull Canvas canvas,
                float left,
                float top,
                float right,
                float bottom,
                float radiusPx,
                @Nullable BackdropDrawer backdrop,
                float backdropOffsetX,
                float backdropOffsetY,
                int backdropWidth,
                int backdropHeight,
                boolean vibrancy,
                float blurRadiusPx,
                float refractionHeightPx,
                float refractionAmountPx,
                boolean depthEffect,
                boolean chromaticAberration,
                boolean drawHighlightAndShadow,
                int tintColor,
                int stillFillColor,
                boolean still
        ) {
            draw(
                    canvas,
                    left, top, right, bottom,
                    radiusPx, radiusPx, radiusPx, radiusPx,
                    backdrop, backdropOffsetX, backdropOffsetY, backdropWidth, backdropHeight,
                    vibrancy, blurRadiusPx, refractionHeightPx, refractionAmountPx,
                    depthEffect, chromaticAberration, drawHighlightAndShadow,
                    tintColor, stillFillColor, still
            );
        }

        /**
         * Draws a Liquid Glass pane with 4 independent corner radii (topLeft, topRight, bottomRight, bottomLeft).
         */
        public void draw(
                @NonNull Canvas canvas,
                float left,
                float top,
                float right,
                float bottom,
                float topLeftRadius,
                float topRightRadius,
                float bottomRightRadius,
                float bottomLeftRadius,
                @Nullable BackdropDrawer backdrop,
                float backdropOffsetX,
                float backdropOffsetY,
                int backdropWidth,
                int backdropHeight,
                boolean vibrancy,
                float blurRadiusPx,
                float refractionHeightPx,
                float refractionAmountPx,
                boolean depthEffect,
                boolean chromaticAberration,
                boolean drawHighlightAndShadow,
                int tintColor,
                int stillFillColor,
                boolean still
        ) {
            float w = right - left;
            float h = bottom - top;
            if (w <= 0f || h <= 0f) {
                return;
            }
            float maxR = Math.min(w, h) * 0.5f;
            float r0 = Math.min(maxR, Math.max(0f, topLeftRadius));
            float r1 = Math.min(maxR, Math.max(0f, topRightRadius));
            float r2 = Math.min(maxR, Math.max(0f, bottomRightRadius));
            float r3 = Math.min(maxR, Math.max(0f, bottomLeftRadius));

            rect.set(0f, 0f, w, h);
            pathRadii[0] = pathRadii[1] = r0;
            pathRadii[2] = pathRadii[3] = r1;
            pathRadii[4] = pathRadii[5] = r2;
            pathRadii[6] = pathRadii[7] = r3;
            clipPath.rewind();
            clipPath.addRoundRect(rect, pathRadii, Path.Direction.CW);

            canvas.save();
            canvas.translate(left, top);

            // 1. Subtle drop shadow (matches Kyant Shadow.Default: alpha 0.10, yOffset = 4dp)
            if (drawHighlightAndShadow) {
                shadowPaint.setColor(0x16000000);
                shadowPaint.setStyle(Paint.Style.FILL);
                canvas.save();
                canvas.translate(0f, dpf2(1.5f));
                canvas.drawPath(clipPath, shadowPaint);
                canvas.restore();
            }

            // 2. Backdrop refraction + blur + vibrancy (API 33+ Hardware Canvas & not still)
            boolean canRenderShader = Build.VERSION.SDK_INT >= 33 && !still && backdrop != null && canvas.isHardwareAccelerated();
            if (canRenderShader) {
                drawRefractedBackdropApi33(
                        canvas, w, h, r0, r1, r2, r3,
                        backdrop, backdropOffsetX + left, backdropOffsetY + top, backdropWidth, backdropHeight,
                        vibrancy, blurRadiusPx, refractionHeightPx, refractionAmountPx,
                        depthEffect, chromaticAberration
                );
            } else if (still && stillFillColor != 0) {
                tintPaint.setColor(stillFillColor);
                canvas.drawPath(clipPath, tintPaint);
            }

            // 3. Surface Tint fill (drawn over backdrop, before rim highlight)
            if (tintColor != 0 && Color.alpha(tintColor) > 0) {
                tintPaint.setColor(tintColor);
                canvas.drawPath(clipPath, tintPaint);
            }

            // 4. Specular 45° Rim Highlight (matches Kyant Highlight.Default + DefaultHighlightShaderString)
            if (drawHighlightAndShadow) {
                drawRimHighlight(canvas, w, h, r0, r1, r2, r3);
            }

            canvas.restore();
        }

        private void drawRefractedBackdropApi33(
                @NonNull Canvas canvas,
                float w, float h,
                float r0, float r1, float r2, float r3,
                @NonNull BackdropDrawer backdrop,
                float totalOffsetX, float totalOffsetY,
                int backdropWidth, int backdropHeight,
                boolean vibrancy,
                float blurRadiusPx,
                float refractionHeightPx,
                float refractionAmountPx,
                boolean depthEffect,
                boolean chromaticAberration
        ) {
            if (Build.VERSION.SDK_INT < 33) return;
            try {
                if (contentNode == null) {
                    contentNode = new RenderNode("LiquidGlassPane");
                }
                // Calculate padding like Kyant Blur.kt & Lens.kt
                float padding = 0f;
                if (blurRadiusPx > 0f) {
                    padding = Math.max(padding, blurRadiusPx);
                }
                if (refractionHeightPx > 0f && refractionAmountPx > 0f && padding > 0f) {
                    padding = Math.max(0f, padding - refractionHeightPx);
                }
                int padInt = Math.round(padding);
                int nodeW = Math.max(1, Math.round(w) + padInt * 2);
                int nodeH = Math.max(1, Math.round(h) + padInt * 2);

                boolean paramsChanged = w != lastW || h != lastH
                        || r0 != lastR0 || r1 != lastR1 || r2 != lastR2 || r3 != lastR3
                        || blurRadiusPx != lastBlur || refractionHeightPx != lastRefractionH
                        || refractionAmountPx != lastRefractionAmt
                        || depthEffect != lastDepth || chromaticAberration != lastDispersion
                        || vibrancy != lastVibrancy;

                if (paramsChanged) {
                    lastW = w;
                    lastH = h;
                    lastR0 = r0;
                    lastR1 = r1;
                    lastR2 = r2;
                    lastR3 = r3;
                    lastBlur = blurRadiusPx;
                    lastRefractionH = refractionHeightPx;
                    lastRefractionAmt = refractionAmountPx;
                    lastDepth = depthEffect;
                    lastDispersion = chromaticAberration;
                    lastVibrancy = vibrancy;

                    RenderEffect effect = null;
                    if (vibrancy) {
                        effect = RenderEffect.createColorFilterEffect(getVibrantColorFilter());
                    }
                    if (blurRadiusPx > 0.5f) {
                        RenderEffect blurEffect = RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP);
                        effect = effect != null ? RenderEffect.createChainEffect(blurEffect, effect) : blurEffect;
                    }
                    if (refractionHeightPx > 0f && refractionAmountPx > 0f) {
                        RuntimeShader shader;
                        if (chromaticAberration) {
                            if (dispersionShader == null) {
                                dispersionShader = new RuntimeShader(ROUNDED_RECT_REFRACTION_DISPERSION_SHADER);
                            }
                            shader = dispersionShader;
                        } else {
                            if (refractionShader == null) {
                                refractionShader = new RuntimeShader(ROUNDED_RECT_REFRACTION_SHADER);
                            }
                            shader = refractionShader;
                        }
                        shader.setFloatUniform("size", w, h);
                        shader.setFloatUniform("offset", (float) -padInt, (float) -padInt);
                        shader.setFloatUniform("cornerRadii", r0, r1, r2, r3);
                        shader.setFloatUniform("refractionHeight", refractionHeightPx);
                        // Exact Kyant Lens.kt sign convention: -refractionAmount
                        shader.setFloatUniform("refractionAmount", -refractionAmountPx);
                        shader.setFloatUniform("depthEffect", depthEffect ? 1f : 0f);
                        if (chromaticAberration) {
                            shader.setFloatUniform("chromaticAberration", 1f);
                        }
                        RenderEffect lensEffect = RenderEffect.createRuntimeShaderEffect(shader, "content");
                        effect = effect != null ? RenderEffect.createChainEffect(lensEffect, effect) : lensEffect;
                    }
                    contentNode.setRenderEffect(effect);
                }

                contentNode.setPosition(0, 0, nodeW, nodeH);
                Canvas rec = contentNode.beginRecording(nodeW, nodeH);
                try {
                    rec.translate(padInt - totalOffsetX, padInt - totalOffsetY);
                    backdrop.drawBackdrop(rec, backdropWidth, backdropHeight);
                } finally {
                    contentNode.endRecording();
                }

                canvas.save();
                canvas.clipPath(clipPath);
                canvas.translate(-padInt, -padInt);
                canvas.drawRenderNode(contentNode);
                canvas.restore();
            } catch (Throwable ignored) {
                // Fallback if device GPU driver rejects shader
            }
        }

        private void drawRimHighlight(
                @NonNull Canvas canvas,
                float w, float h,
                float r0, float r1, float r2, float r3
        ) {
            float strokeW = (float) Math.ceil(Math.min(dpf2(0.5f), Math.min(w, h) * 0.5f)) * 2f;
            highlightPaint.setStrokeWidth(strokeW);
            if (Build.VERSION.SDK_INT >= 33) {
                try {
                    if (highlightShader == null) {
                        highlightShader = new RuntimeShader(DEFAULT_HIGHLIGHT_SHADER);
                    }
                    highlightShader.setFloatUniform("size", w, h);
                    highlightShader.setFloatUniform("cornerRadii", r0, r1, r2, r3);
                    highlightShader.setColorUniform("color", Color.argb(128, 255, 255, 255));
                    highlightShader.setFloatUniform("angle", (float) (45.0 * Math.PI / 180.0));
                    highlightShader.setFloatUniform("falloff", 1f);
                    highlightPaint.setShader(highlightShader);
                    highlightPaint.setBlendMode(BlendMode.PLUS);
                } catch (Throwable ignored) {
                    highlightPaint.setShader(null);
                    highlightPaint.setColor(0x60ffffff);
                }
            } else {
                highlightPaint.setShader(null);
                highlightPaint.setColor(0x60ffffff);
                if (Build.VERSION.SDK_INT >= 29) {
                    highlightPaint.setBlendMode(BlendMode.PLUS);
                }
            }
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawPath(clipPath, highlightPaint);
            canvas.restore();
        }
    }
}
