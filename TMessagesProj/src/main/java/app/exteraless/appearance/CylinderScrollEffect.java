package app.exteraless.appearance;

import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.view.View;

import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;

@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class CylinderScrollEffect {

    private static final String SHADER_CODE =
            "uniform shader img;\n" +
            "uniform float2 resolution;\n" +
            "uniform float2 boundsY;\n" +
            "uniform float edgeHeight;\n" +
            "uniform float maxSin;\n" +
            "uniform float maxTheta;\n" +
            "uniform float perspectiveK;\n" +
            "\n" +
            "float approxAsin(float x) {\n" +
            "    float x2 = x * x;\n" +
            "    return x * (1.0 + x2 * (0.16666667 + x2 * (0.075 + x2 * 0.04464286))) + 0.12 * x2 * x2 * x * (1.0 - sqrt(max(1.0 - x2, 0.001)));\n" +
            "}\n" +
            "\n" +
            "half4 main(in float2 fragCoord) {\n" +
            "    float topY = boundsY.x;\n" +
            "    float botY = boundsY.y;\n" +
            "    if (edgeHeight <= 1.0) {\n" +
            "        return img.eval(fragCoord);\n" +
            "    }\n" +
            "    if (fragCoord.y < topY || fragCoord.y > botY) {\n" +
            "        return half4(0.0);\n" +
            "    }\n" +
            "\n" +
            "    float u = 0.0;\n" +
            "    float dir = 0.0;\n" +
            "    float anchorY = fragCoord.y;\n" +
            "\n" +
            "    if (fragCoord.y < topY + edgeHeight) {\n" +
            "        u = (topY + edgeHeight - fragCoord.y) / edgeHeight;\n" +
            "        dir = -1.0;\n" +
            "        anchorY = topY + edgeHeight;\n" +
            "    } else if (fragCoord.y > botY - edgeHeight) {\n" +
            "        u = (fragCoord.y - (botY - edgeHeight)) / edgeHeight;\n" +
            "        dir = 1.0;\n" +
            "        anchorY = botY - edgeHeight;\n" +
            "    } else {\n" +
            "        return img.eval(fragCoord);\n" +
            "    }\n" +
            "\n" +
            "    u = clamp(u, 0.0, 1.0);\n" +
            "    float sinT = clamp(u * maxSin, 0.0, 0.985);\n" +
            "    float cosT = sqrt(max(1.0 - sinT * sinT, 0.015));\n" +
            "    float theta = approxAsin(sinT);\n" +
            "    float g = theta / max(maxTheta, 0.01);\n" +
            "    float w = smoothstep(0.0, 0.28, u);\n" +
            "    float v = clamp(mix(u, g, w), 0.0, 1.0);\n" +
            "\n" +
            "    float srcY = clamp(anchorY + dir * v * edgeHeight, topY, botY);\n" +
            "\n" +
            "    float depth = 1.0 - cosT;\n" +
            "    float cx = resolution.x * 0.5;\n" +
            "    float scaleX = 1.0 + perspectiveK * depth;\n" +
            "    float srcX = cx + (fragCoord.x - cx) * scaleX;\n" +
            "    if (srcX < 0.0 || srcX > resolution.x) {\n" +
            "        return half4(0.0);\n" +
            "    }\n" +
            "\n" +
            "    half4 col = img.eval(float2(srcX, srcY));\n" +
            "    float horizonFade = 1.0 - smoothstep(0.95, 1.0, u);\n" +
            "    float shade = 1.0 - 0.10 * depth;\n" +
            "    return half4(col.rgb * half(shade * horizonFade), col.a * half(horizonFade));\n" +
            "}\n";

    private final View targetView;
    private final RuntimeShader shader;
    private boolean applied;

    private float lastWidth = -1f;
    private float lastHeight = -1f;
    private float lastTopY = -1f;
    private float lastBotY = -1f;
    private float lastEdgeHeight = -1f;
    private float lastMaxSin = -1f;
    private float lastPerspectiveK = -1f;

    public CylinderScrollEffect(View targetView) {
        this.targetView = targetView;
        this.shader = new RuntimeShader(SHADER_CODE);
    }

    private static float approxAsin(float x) {
        float x2 = x * x;
        return x * (1.0f + x2 * (0.16666667f + x2 * (0.075f + x2 * 0.04464286f)))
                + 0.12f * x2 * x2 * x * (1.0f - (float) Math.sqrt(Math.max(1.0f - x2, 0.001f)));
    }

    public void update(int width, int height, int paddingTop, int paddingBottom, float angleDeg) {
        if (width <= 0 || height <= 0) {
            clear();
            return;
        }

        float visTop = (targetView.getTop() <= AndroidUtilities.dp(12) && paddingTop > AndroidUtilities.dp(28))
                ? paddingTop : 0f;
        float visBottom = (paddingBottom > AndroidUtilities.dp(40))
                ? Math.max(visTop + AndroidUtilities.dp(120), height - paddingBottom + AndroidUtilities.dp(6))
                : (float) height;
        if (visBottom > height) {
            visBottom = height;
        }

        float visSpan = visBottom - visTop;
        if (visSpan < AndroidUtilities.dp(160)) {
            clear();
            return;
        }

        float clampedAngle = Math.max(5f, Math.min(60f, angleDeg));
        float norm = (clampedAngle - 5f) / 55f; // 0..1

        float desiredEdgeDp = 64f + norm * 44f; // 64dp..108dp
        float edgeHeight = Math.min(AndroidUtilities.dpf2(desiredEdgeDp), visSpan * 0.28f);

        float maxSin = 0.72f + norm * 0.23f; // 0.72..0.95 (at default 25deg -> ~0.805)
        float maxTheta = approxAsin(maxSin);
        float perspectiveK = 0.14f + norm * 0.18f; // 0.14..0.32 (at default 25deg -> ~0.205)

        if (!applied
                || Math.abs(lastWidth - width) > 0.5f
                || Math.abs(lastHeight - height) > 0.5f
                || Math.abs(lastTopY - visTop) > 0.5f
                || Math.abs(lastBotY - visBottom) > 0.5f
                || Math.abs(lastEdgeHeight - edgeHeight) > 0.5f
                || Math.abs(lastMaxSin - maxSin) > 0.002f
                || Math.abs(lastPerspectiveK - perspectiveK) > 0.002f) {

            lastWidth = width;
            lastHeight = height;
            lastTopY = visTop;
            lastBotY = visBottom;
            lastEdgeHeight = edgeHeight;
            lastMaxSin = maxSin;
            lastPerspectiveK = perspectiveK;

            shader.setFloatUniform("resolution", (float) width, (float) height);
            shader.setFloatUniform("boundsY", visTop, visBottom);
            shader.setFloatUniform("edgeHeight", edgeHeight);
            shader.setFloatUniform("maxSin", maxSin);
            shader.setFloatUniform("maxTheta", maxTheta);
            shader.setFloatUniform("perspectiveK", perspectiveK);

            RenderEffect effect = RenderEffect.createRuntimeShaderEffect(shader, "img");
            targetView.setRenderEffect(effect);
            applied = true;
        }
    }

    public void clear() {
        if (applied) {
            targetView.setRenderEffect(null);
            applied = false;
        }
    }
}
