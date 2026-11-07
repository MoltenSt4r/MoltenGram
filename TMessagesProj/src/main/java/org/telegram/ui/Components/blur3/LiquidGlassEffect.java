package org.telegram.ui.Components.blur3;

import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.player.LiquidGlassHelper;

/**
 * InterTune Liquid Glass effect engine for Blur3 / RenderNode.
 * 
 * Features:
 * - 7-band chromatic aberration dispersion refraction
 * - 45° specular rim highlight with BlendMode.PLUS
 * - Vibrancy / 1.5x saturation color filter
 * - GlassSpec dynamic blur & lens math
 * - Battery saver fallback
 */
@RequiresApi(api = 33)
public class LiquidGlassEffect {

    private final RenderNode node;

    private RuntimeShader dispersionShader;
    private RuntimeShader standardShader;
    private RuntimeShader highlightShader;

    private RenderEffect effect;
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF highlightRect = new RectF();
    private final Path highlightPath = new Path();

    private float lastW, lastH;
    private float lastR1, lastR2, lastR3, lastR4;
    private float lastIntensity = -1f;
    private boolean lastDispersion;
    private boolean lastBatterySaver;

    public LiquidGlassEffect(RenderNode node) {
        this.node = node;
        try {
            dispersionShader = new RuntimeShader(LiquidGlassHelper.ROUNDED_RECT_REFRACTION_DISPERSION_SHADER);
            standardShader = new RuntimeShader(LiquidGlassHelper.ROUNDED_RECT_REFRACTION_SHADER);
            highlightShader = new RuntimeShader(LiquidGlassHelper.DEFAULT_HIGHLIGHT_SHADER);
        } catch (Throwable e) {
            FileLog.e("LiquidGlassEffect AGSL shader compile failed", e);
        }
    }

    public void update(
        float left, float top, float right, float bottom,
        float radiusLeftTop, float radiusRightTop, float radiusRightBottom, float radiusLeftBottom,
        float thickness,
        float intensity,
        float index,
        int foregroundColor
    ) {
        float width = Math.max(1f, right - left);
        float height = Math.max(1f, bottom - top);

        boolean dispersion = AppearanceConfig.liquidGlassDispersion();
        float globalIntensity = AppearanceConfig.liquidGlassIntensityFloat();
        float effectiveIntensity = globalIntensity * (intensity > 0 ? intensity : 1f);
        boolean batterySaver = LiquidGlassHelper.isBatterySaverStill();

        if (Math.abs(lastW - width) > 0.5f ||
            Math.abs(lastH - height) > 0.5f ||
            Math.abs(lastR1 - radiusLeftTop) > 0.5f ||
            Math.abs(lastR2 - radiusRightTop) > 0.5f ||
            Math.abs(lastR3 - radiusRightBottom) > 0.5f ||
            Math.abs(lastR4 - radiusLeftBottom) > 0.5f ||
            Math.abs(lastIntensity - effectiveIntensity) > 0.01f ||
            lastDispersion != dispersion ||
            lastBatterySaver != batterySaver) {

            lastW = width;
            lastH = height;
            lastR1 = radiusLeftTop;
            lastR2 = radiusRightTop;
            lastR3 = radiusRightBottom;
            lastR4 = radiusLeftBottom;
            lastIntensity = effectiveIntensity;
            lastDispersion = dispersion;
            lastBatterySaver = batterySaver;

            if (batterySaver || effectiveIntensity <= 0.001f) {
                node.setRenderEffect(null);
                return;
            }

            RuntimeShader activeShader = (dispersion && dispersionShader != null) ? dispersionShader : standardShader;
            if (activeShader == null) {
                return;
            }

            LiquidGlassHelper.GlassSpec spec = LiquidGlassHelper.GlassSpec.of(effectiveIntensity);

            activeShader.setFloatUniform("size", width, height);
            activeShader.setFloatUniform("offset", 0f, 0f);
            activeShader.setFloatUniform("cornerRadii", radiusLeftTop, radiusRightTop, radiusRightBottom, radiusLeftBottom);
            activeShader.setFloatUniform("refractionHeight", Math.max(1f, 32f * AndroidUtilities.density * spec.lensT));
            activeShader.setFloatUniform("refractionAmount", -24f * AndroidUtilities.density * spec.lensT);
            activeShader.setFloatUniform("depthEffect", 0.5f * effectiveIntensity);

            if (activeShader == dispersionShader) {
                activeShader.setFloatUniform("chromaticAberration", 0.45f);
            }

            try {
                RenderEffect blurEffect = RenderEffect.createBlurEffect(spec.blurPx, spec.blurPx, Shader.TileMode.CLAMP);
                RenderEffect vibrantEffect = RenderEffect.createColorFilterEffect(LiquidGlassHelper.getVibrantColorFilter(), blurEffect);
                RenderEffect shaderEffect = RenderEffect.createRuntimeShaderEffect(activeShader, "content");
                effect = RenderEffect.createChainEffect(shaderEffect, vibrantEffect);
                node.setRenderEffect(effect);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    public void drawHighlight(
        @NonNull Canvas canvas,
        float width, float height,
        float rLT, float rRT, float rRB, float rLB
    ) {
        if (highlightShader == null || lastBatterySaver || lastIntensity <= 0.001f) {
            return;
        }
        try {
            highlightShader.setFloatUniform("size", width, height);
            highlightShader.setFloatUniform("cornerRadii", rLT, rRT, rRB, rLB);
            float alpha = 0.45f * lastIntensity;
            highlightShader.setColorUniform("color", Color.argb((int) (alpha * 255), 255, 255, 255));
            highlightShader.setFloatUniform("angle", (float) (Math.PI / 4.0));
            highlightShader.setFloatUniform("falloff", 4.0f);

            highlightPaint.setShader(highlightShader);
            highlightPaint.setBlendMode(BlendMode.PLUS);
            highlightRect.set(0, 0, width, height);

            if (rLT == rRT && rRT == rRB && rRB == rLB) {
                canvas.drawRoundRect(highlightRect, rLT, rLT, highlightPaint);
            } else {
                highlightPath.rewind();
                highlightPath.addRoundRect(highlightRect, new float[]{rLT, rLT, rRT, rRT, rRB, rRB, rLB, rLB}, Path.Direction.CW);
                canvas.drawPath(highlightPath, highlightPaint);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
