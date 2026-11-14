/*
 * This is the source code of Solegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package app.exteraless.glass;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

import androidx.annotation.RequiresApi;



import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

/**
 * Liquid glass switches and sliders, after the "liquid-glass-demo" by winaviation (kube.io):
 * at rest the thumb is a white pill; while it is pressed or moving it springs up in size, turns
 * clear and works as a lens that magnifies the track under it, with a specular rim.
 */
public final class LiquidControls {

    public static boolean enabled() {
        return LiquidGlassConfig.glassOn && LiquidGlassConfig.liquidControls;
    }

    /** Damped spring parameterised by response time (s) and damping ratio, for fluid lenses. */
    public static final class GlassSpring {
        public float value, velocity, target;
        private float stiffness, damping;
        private boolean active;
        private float restDelta = 0.05f;

        public GlassSpring(float value) {
            this.value = this.target = value;
            params(0.42f, 0.82f);
        }

        public GlassSpring params(float response, float dampingFraction) {
            final float omega = (float) (2 * Math.PI / Math.max(0.05f, response));
            stiffness = omega * omega;
            damping = 2 * dampingFraction * omega;
            return this;
        }

        public GlassSpring rest(float delta) {
            restDelta = delta;
            return this;
        }

        public void to(float target) {
            if (this.target == target && !active && value == target) {
                return;
            }
            this.target = target;
            active = true;
        }

        public void set(float value) {
            this.value = this.target = value;
            velocity = 0;
            active = false;
        }

        public boolean step(float dt) {
            if (!active) {
                return false;
            }
            float left = dt;
            while (left > 1e-6f) {
                final float h = Math.min(left, 1f / 240f);
                final float a = -stiffness * (value - target) - damping * velocity;
                velocity += a * h;
                value += velocity * h;
                left -= h;
            }
            if (Math.abs(value - target) < restDelta && Math.abs(velocity) < restDelta * 20) {
                value = target;
                velocity = 0;
                active = false;
            }
            return active;
        }
    }

    /** Spring with the same integration as the demo (stiffness/damping, explicit time step). */
    public static final class Spring {
        public float value, target, velocity;
        private final float stiffness, damping;

        public Spring(float value, float stiffness, float damping) {
            this.value = this.target = value;
            this.stiffness = stiffness;
            this.damping = damping;
        }

        public float update(float dt) {
            // a few sub-steps keep stiff springs stable at low frame rates
            final int steps = Math.max(1, (int) Math.ceil(dt / (1f / 240f)));
            final float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                final float force = (target - value) * stiffness;
                final float dampingForce = velocity * damping;
                velocity += (force - dampingForce) * h;
                value += velocity * h;
            }
            return value;
        }

        public boolean settled() {
            return Math.abs(target - value) < 0.001f && Math.abs(velocity) < 0.001f;
        }

        public void snap(float v) {
            value = target = v;
            velocity = 0;
        }
    }

    public interface LensContent {
        /** Draws what lies under the thumb, in the same coordinates as the control. */
        void draw(Canvas canvas);
    }

    private static final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF rect = new RectF();
    private static final Path path = new Path();

    static {
        rimPaint.setStyle(Paint.Style.STROKE);
    }

    /**
     * The glass of one control: on Android 13+ the picture under the thumb is refracted by a shader
     * (solegram_lens_shader: magnified middle, bent rim, split colours, rim light). Keep one per view.
     */
    public static final class Lens {
        private Object impl;
        private boolean failed;

        /** @return false when the shader lens is not available here (old Android, software canvas) */
        boolean draw(Canvas canvas, int viewW, int viewH, float cx, float cy, float w, float h, float a, LensContent content) {
            if (failed || Build.VERSION.SDK_INT < 33 || !canvas.isHardwareAccelerated() || viewW <= 0 || viewH <= 0) {
                return false;
            }
            try {
                if (impl == null) {
                    impl = new ShaderLens();
                }
                ((ShaderLens) impl).draw(canvas, viewW, viewH, cx, cy, w, h, a, content);
                return true;
            } catch (Throwable e) {
                FileLog.e(e);
                failed = true;
                impl = null;
                return false;
            }
        }
    }

    @RequiresApi(33)
    private static final class ShaderLens {
        private final RenderNode node = new RenderNode("solegramLens");
        private final RuntimeShader shader = new RuntimeShader(AndroidUtilities.readRes(R.raw.solegram_lens_shader));

        void draw(Canvas canvas, int viewW, int viewH, float cx, float cy, float w, float h, float a, LensContent content) {
            node.setPosition(0, 0, viewW, viewH);
            final RecordingCanvas rc = node.beginRecording(viewW, viewH);
            try {
                content.draw(rc);
            } finally {
                node.endRecording();
            }
            shader.setFloatUniform("center", cx, cy);
            shader.setFloatUniform("halfSize", w / 2f, h / 2f);
            if (sliderLens) {
                // a slider's thumb is a thicker drop over a thin track: the middle magnifies it well, towards the
                // rim the glass curves it hard and splits its colours, so the distortion shows on a plain line too
                shader.setFloatUniform("magnify", 1f + 0.42f * a);
                shader.setFloatUniform("bend", h * 0.62f * a);
                shader.setFloatUniform("dispersion", 0.75f * a);
                shader.setFloatUniform("light", 0.95f * a);
            } else {
                shader.setFloatUniform("magnify", 1f + 0.2f * a);
                shader.setFloatUniform("bend", h * 0.42f * a);
                shader.setFloatUniform("dispersion", 0.4f * a);
                shader.setFloatUniform("light", 0.85f * a);
            }
            final float body = (sliderLens ? 0.04f : 0.06f) * a; // a faint white glass body, premultiplied
            shader.setFloatUniform("tint", body, body, body, body);
            node.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "img"));
            canvas.drawRenderNode(node);
        }
    }

    /** Leaves the thumb's shape out of what is drawn next (the track), so the glass alone shows it. */
    public static void clipOutThumb(Canvas canvas, float cx, float cy, float width, float height) {
        rect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
        path.rewind();
        path.addRoundRect(rect, height / 2f, height / 2f, Path.Direction.CW);
        if (Build.VERSION.SDK_INT >= 26) {
            canvas.clipOutPath(path);
        } else {
            canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE);
        }
    }

    /**
     * @param active 0 = resting white pill, 1 = clear glass lens (the demo's "active" state)
     * @param lens   content to magnify inside the thumb while it is glass, may be null
     */
    public static void drawThumb(Canvas canvas, float cx, float cy, float width, float height, float active, LensContent lens) {
        drawThumb(canvas, cx, cy, width, height, active, lens, null, 0, 0);
    }

    private static boolean sliderLens;

    /**
     * A slider's thumb: a real glass lens over the track (Android 13+): the track is magnified in the middle,
     * bent along the curved rim and split into colours at the bend, with light on the rim facing the top left.
     * Without the shader it is magnified a little and lit the same way.
     */
    public static void drawSliderThumb(Canvas canvas, float cx, float cy, float width, float height, float active, LensContent lens,
                                       Lens glass, int viewWidth, int viewHeight) {
        sliderLens = true;
        try {
            drawThumb(canvas, cx, cy, width, height, active, lens, glass, viewWidth, viewHeight);
        } finally {
            sliderLens = false;
        }
    }

    /**
     * @param glass      the view's {@link Lens} (shader refraction on Android 13+), may be null
     * @param viewWidth  size of the control: the shadow is kept inside it, so no edge ever cuts it off
     */
    public static void drawThumb(Canvas canvas, float cx, float cy, float width, float height, float active, LensContent lens,
                                 Lens glass, int viewWidth, int viewHeight) {
        final float a = Math.max(0f, Math.min(1f, active));
        rect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
        final float r = height / 2f;

        // soft drop shadow, deeper while lifted; only around the thumb, never seen through the glass
        if (Build.VERSION.SDK_INT >= 28) {
            float blur = dpf2(3 + 5 * a), dy = dpf2(1 + 2.5f * a);
            if (viewWidth > 0 && viewHeight > 0) {
                // the view clips what it draws: a shadow that would be cut off becomes a smaller one instead
                // (it reaches blur + dy below the thumb, blur - dy above and blur to the sides)
                final float k = dy / blur;
                final float side = Math.min(rect.left, viewWidth - rect.right);
                final float below = viewHeight - rect.bottom, above = rect.top;
                blur = Math.min(blur, Math.min(side, Math.min(below / (1f + k), above / (1f - k))));
                dy = k * blur;
            }
            if (blur > 0.5f) {
                canvas.save();
                path.rewind();
                path.addRoundRect(rect, r, r, Path.Direction.CW);
                canvas.clipOutPath(path);
                shadowPaint.setColor(0x06000000);
                shadowPaint.setShadowLayer(blur, 0, dy, (int) (0x22 + 0x14 * a) << 24);
                canvas.drawRoundRect(rect, r, r, shadowPaint);
                canvas.restore();
            }
        }

        // lens: the track under the thumb, magnified in the middle and bent at the curved rim
        boolean shaderLens = false;
        if (a > 0.01f && lens != null) {
            shaderLens = glass != null && glass.draw(canvas, viewWidth, viewHeight, cx, cy, width, height, a, lens);
            if (!shaderLens) {
                canvas.save();
                path.rewind();
                path.addRoundRect(rect, r, r, Path.Direction.CW);
                canvas.clipPath(path);
                final float magnify = 1f + (sliderLens ? 0.3f : 0.24f) * a;
                canvas.scale(magnify, magnify, cx, cy);
                lens.draw(canvas);
                canvas.restore();
            }
            rect.set(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f);
        }

        // white body fades to a faint tint (demo: background opacity 1 -> 0.1)
        fillPaint.setColor(((int) (255 * (1f - (sliderLens ? 0.94f : 0.9f) * a)) << 24) | 0xFFFFFF);
        canvas.drawRoundRect(rect, r, r, fillPaint);

        if (Build.VERSION.SDK_INT < 28) {
            // no shadow layers on hardware canvases before Android 9: a hairline keeps the white pill visible on white
            rimPaint.setShader(null);
            rimPaint.setStrokeWidth(Math.max(1f, dpf2(0.8f)));
            rimPaint.setColor(0x26000000);
            canvas.drawRoundRect(rect, r, r, rimPaint);
        }

        // specular rim: bright where light hits (top-left), soft at the opposite edge (the shader lens has its own)
        if (a > 0.01f && !shaderLens) {
            rimPaint.setStrokeWidth(Math.max(1f, dpf2(1.2f)));
            rimPaint.setColor(0xFFFFFFFF);
            final int light = (int) (210 * a);
            rimPaint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{(light << 24) | 0xFFFFFF, ((light / 6) << 24) | 0xFFFFFF, ((light / 2) << 24) | 0xFFFFFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            rect.inset(rimPaint.getStrokeWidth() / 2f, rimPaint.getStrokeWidth() / 2f);
            canvas.drawRoundRect(rect, r, r, rimPaint);
            rimPaint.setShader(null);
        }
    }

    /** Thumb size of a switch for a given track height, demo proportions (146x92 thumb on a 67 high track). */
    public static float switchThumbHeight(float trackHeight) {
        return trackHeight * 92f / 67f;
    }

    public static float switchThumbWidth(float trackHeight) {
        return trackHeight * 146f / 67f;
    }

    public static final float SWITCH_REST_SCALE = 0.65f;
    public static final float SWITCH_ACTIVE_SCALE = 0.9f;
    public static final float SLIDER_REST_SCALE = 0.6f;
    public static final float SLIDER_ACTIVE_SCALE = 1f;

    /** Centre of a switch thumb travelling along a track, xRatio 0..1 (may overshoot while springing). */
    public static float switchThumbCenter(float trackLeft, float trackWidth, float trackHeight, float xRatio) {
        final float thumbW = switchThumbWidth(trackHeight), thumbH = switchThumbHeight(trackHeight);
        final float restOffset = (1 - SWITCH_REST_SCALE) * thumbW / 2f;
        final float travel = trackWidth - trackHeight - (thumbW - thumbH) * SWITCH_REST_SCALE;
        final float marginLeft = -restOffset + (trackHeight - thumbH * SWITCH_REST_SCALE) / 2f;
        return trackLeft + marginLeft + thumbW / 2f + xRatio * travel;
    }

    /** Demo track colours: grey when off, green when on; a theme accent may replace the green. */
    public static int switchTrackColor(float onProgress, int onColor) {
        final float t = Math.max(0f, Math.min(1f, onProgress));
        final int offColor = 0x7794949F;
        return androidx.core.graphics.ColorUtils.blendARGB(offColor, onColor, t);
    }

    public static final int DEMO_GREEN = 0xEE3BBF4E;
    public static final int DEMO_BLUE = 0xFF0377F7;
    public static final int DEMO_SLIDER_TRACK = 0x6689898F;

    public static int dpi(float v) {
        return dp(v);
    }

    private LiquidControls() {
    }
}
