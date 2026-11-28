package app.exteraless.chats;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.ui.Components.EditTextBoldCursor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Pure-Java text typing, deletion particle, and fluid/liquid cursor animation engine
 * attached directly to {@link EditTextBoldCursor}. Replaces legacy Xposed DEX hooks
 * so animations work reliably across Android 8-17, Safe Mode, and Release R8 builds.
 */
public final class TextAnimationEngine {

    private static final int MAX_TRACKED_CHARS = 2500;
    private static final int MAX_BURST_ANIM_CHARS = 18;
    private static final int MAX_BURST_DELETE_CHARS = 6;
    private static final int MAX_PARTICLES_PER_VIEW = 90;

    private static final WeakHashMap<EditTextBoldCursor, ViewState> VIEW_STATES = new WeakHashMap<>();
    private static final Random RANDOM = new Random();
    private static final RectF TMP_RECT = new RectF();

    private static volatile boolean suppressAll;
    private static volatile long suppressUntilMs;
    private static volatile boolean globalSelectionActive;
    private static volatile long configVersion = 1L;

    private static volatile Config cachedConfig = Config.load();

    private static final class Config {
        boolean enabled;
        int durationMs;
        boolean blurEnabled;
        int blurDurationMs;
        int blurRadiusDp;
        int blurTextDelayMs;
        boolean slideEnabled;
        int slideDistDp;
        boolean scaleEnabled;
        float scaleStart;
        boolean rotateEnabled;
        int rotateAngle;
        boolean deleteEnabled;
        int particleStyle;
        int particleCount;
        int particleSpeed;
        int particleSpread;
        int particleSize;
        boolean cursorEnabled;
        int cursorSpeed;
        int cursorWidthSetting;
        boolean liquidCursorEnabled;
        int liquidStretch;
        boolean ignoreSpaces;
        boolean animateAllLines;

        static Config load() {
            Config c = new Config();
            c.enabled = ChatsConfig.textAnimationEnabled.Bool();
            c.durationMs = Math.max(40, ChatsConfig.textAnimationDuration.Int());
            c.blurEnabled = ChatsConfig.textAnimationBlurEnabled.Bool();
            c.blurDurationMs = Math.max(40, ChatsConfig.textAnimationBlurDuration.Int());
            c.blurRadiusDp = Math.max(0, ChatsConfig.textAnimationBlurRadius.Int());
            c.blurTextDelayMs = Math.max(0, ChatsConfig.textAnimationBlurTextDelay.Int());
            c.slideEnabled = ChatsConfig.textAnimationSlideEnabled.Bool();
            c.slideDistDp = ChatsConfig.textAnimationSlideDist.Int();
            c.scaleEnabled = ChatsConfig.textAnimationScaleEnabled.Bool();
            c.scaleStart = Math.max(0f, Math.min(2f, ChatsConfig.textAnimationScaleStart.Int() / 100f));
            c.rotateEnabled = ChatsConfig.textAnimationRotateEnabled.Bool();
            c.rotateAngle = ChatsConfig.textAnimationRotateAngle.Int();
            c.deleteEnabled = ChatsConfig.textAnimationDeleteEnabled.Bool();
            c.particleStyle = ChatsConfig.textAnimationParticleStyle.Int();
            c.particleCount = Math.max(1, Math.min(24, ChatsConfig.textAnimationParticleCount.Int()));
            c.particleSpeed = Math.max(5, ChatsConfig.textAnimationParticleSpeed.Int());
            c.particleSpread = Math.max(5, ChatsConfig.textAnimationParticleSpread.Int());
            c.particleSize = Math.max(10, ChatsConfig.textAnimationParticleSize.Int());
            c.cursorEnabled = ChatsConfig.textAnimationCursorEnabled.Bool();
            c.cursorSpeed = Math.max(1, ChatsConfig.textAnimationCursorSpeed.Int());
            c.cursorWidthSetting = Math.max(1, ChatsConfig.textAnimationCursorWidth.Int());
            c.liquidCursorEnabled = ChatsConfig.textAnimationLiquidCursorEnabled.Bool();
            c.liquidStretch = Math.max(0, ChatsConfig.textAnimationLiquidStretch.Int());
            c.ignoreSpaces = ChatsConfig.textAnimationIgnoreSpaces.Bool();
            c.animateAllLines = ChatsConfig.textAnimationAnimateAllLines.Bool();
            return c;
        }
    }

    private static final class CharAnim {
        int index;
        int length;
        long startTimeMs;
        int slideDurationMs;
        int blurDurationMs;
        float x;
        float top;
        float bottom;
        float width;
        float baseline;
    }

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        float rotation;
        float vRot;
        long startTimeMs;
        long lifeMs;
        int color;
        int style;
        String letter;
    }

    private static final class GlyphPos {
        float x;
        float top;
        float bottom;
        float baseline;
        float width;
    }

    private static final class ViewState {
        boolean initialized;
        String lastText = "";
        ArrayList<GlyphPos> prevGlyphs = new ArrayList<>();
        final ArrayList<CharAnim> charAnims = new ArrayList<>();
        final ArrayList<Particle> particles = new ArrayList<>();

        boolean cursorInitialized;
        float cursorX;
        float cursorTop;
        float cursorBottom;
        float cursorVelocityX;
        long lastFrameMs;

        final Paint charPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        MaskFilter lastBlurFilter;
        int lastBlurBucket = -1;
        boolean hasClippedRegion;
    }

    private TextAnimationEngine() {
    }

    public static void refreshConfig() {
        cachedConfig = Config.load();
        configVersion++;
    }

    public static void clearAll() {
        synchronized (VIEW_STATES) {
            VIEW_STATES.clear();
        }
    }

    public static void setSuppressed(boolean suppressed) {
        suppressAll = suppressed;
    }

    public static void suppressBriefly(long durationMs) {
        suppressUntilMs = SystemClock.uptimeMillis() + durationMs;
    }

    public static void setGlobalSelectionActive(boolean active) {
        globalSelectionActive = active;
    }

    private static boolean isEligible(EditTextBoldCursor view) {
        if (view == null || view.isInEditMode() || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        Config cfg = cachedConfig;
        if (!cfg.enabled || suppressAll || SystemClock.uptimeMillis() < suppressUntilMs) {
            return false;
        }
        int inputType = view.getInputType();
        int cls = inputType & InputType.TYPE_MASK_CLASS;
        int var = inputType & InputType.TYPE_MASK_VARIATION;
        if (cls == InputType.TYPE_CLASS_TEXT && (
                var == InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) {
            return false;
        }
        return !(cls == InputType.TYPE_CLASS_NUMBER && var == InputType.TYPE_NUMBER_VARIATION_PASSWORD);
    }

    private static ViewState getState(EditTextBoldCursor view) {
        synchronized (VIEW_STATES) {
            ViewState state = VIEW_STATES.get(view);
            if (state == null) {
                state = new ViewState();
                VIEW_STATES.put(view, state);
            }
            return state;
        }
    }

    public static void onFocusChanged(EditTextBoldCursor view, boolean focused) {
        if (view == null) return;
        ViewState state;
        synchronized (VIEW_STATES) {
            state = VIEW_STATES.get(view);
        }
        if (state != null) {
            state.cursorInitialized = false;
            state.cursorVelocityX = 0f;
            if (!focused) {
                state.charAnims.clear();
            }
        }
    }

    public static void onTouch(EditTextBoldCursor view, MotionEvent event) {
        if (view != null && event != null && event.getAction() == MotionEvent.ACTION_UP) {
            view.postInvalidateOnAnimation();
        }
    }

    public static boolean isCustomCursorActive(EditTextBoldCursor view) {
        if (!isEligible(view)) {
            return false;
        }
        Config cfg = cachedConfig;
        if (!cfg.cursorEnabled || !view.isFocused() || view.hasSelection() || globalSelectionActive) {
            return false;
        }
        return view.getLayout() != null;
    }

    /**
     * Updates text diff animations and clips out actively animating newly-typed glyphs
     * so {@code super.onDraw(canvas)} does not draw duplicate static characters beneath them.
     *
     * @return {@code true} if a canvas clip state was pushed and must be restored right after {@code super.onDraw}.
     */
    public static boolean beforeSuperDraw(EditTextBoldCursor view, Canvas canvas, int contentLeft, int contentTopOffset) {
        if (!isEligible(view) || canvas == null) {
            synchronized (VIEW_STATES) {
                ViewState state = VIEW_STATES.get(view);
                if (state != null) {
                    state.charAnims.clear();
                    state.particles.clear();
                    Editable text = view.getText();
                    state.lastText = text != null ? text.toString() : "";
                    state.hasClippedRegion = false;
                }
            }
            return false;
        }

        Layout layout = view.getLayout();
        Editable editable = view.getText();
        if (layout == null || editable == null) {
            return false;
        }

        ViewState state = getState(view);
        state.hasClippedRegion = false;
        long now = SystemClock.uptimeMillis();
        Config cfg = cachedConfig;

        String currentText = editable.toString();
        if (currentText.length() > MAX_TRACKED_CHARS) {
            state.initialized = true;
            state.lastText = "";
            state.charAnims.clear();
            return false;
        }

        if (!state.initialized) {
            state.initialized = true;
            state.lastText = currentText;
            captureGlyphPositions(layout, editable, state.prevGlyphs);
            return false;
        }

        if (!currentText.equals(state.lastText)) {
            detectDiff(view, state, layout, currentText, now, cfg);
            state.lastText = currentText;
        }
        captureGlyphPositions(layout, editable, state.prevGlyphs);

        pruneAndUpdateCharAnims(state, layout, currentText, now, cfg);
        if (state.charAnims.isEmpty()) {
            return false;
        }

        canvas.save();
        for (int i = 0, n = state.charAnims.size(); i < n; i++) {
            CharAnim a = state.charAnims.get(i);
            float pad = dpf2(1.5f);
            float left = contentLeft + a.x - pad;
            float right = contentLeft + a.x + Math.max(a.width, dpf2(2f)) + pad;
            float top = contentTopOffset + a.top;
            float bottom = contentTopOffset + a.bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutRect(left, top, right, bottom);
            } else {
                canvas.clipRect(left, top, right, bottom, Region.Op.DIFFERENCE);
            }
        }
        state.hasClippedRegion = true;
        return true;
    }

    /**
     * Draws animating newly-typed glyphs and backspace particles in text-layout coordinates.
     * Note: {@code canvas} is currently inside {@code EditTextBoldCursor}'s {@code translate(0, topPadding)} block.
     */
    public static void afterSuperDraw(EditTextBoldCursor view, Canvas canvas, int contentLeft, int contentTopOffset) {
        if (!isEligible(view) || canvas == null) {
            return;
        }
        Layout layout = view.getLayout();
        Editable editable = view.getText();
        if (layout == null || editable == null) {
            return;
        }
        ViewState state;
        synchronized (VIEW_STATES) {
            state = VIEW_STATES.get(view);
        }
        if (state == null || (state.charAnims.isEmpty() && state.particles.isEmpty())) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        Config cfg = cachedConfig;
        boolean needsMoreFrames = false;

        canvas.save();
        canvas.translate(contentLeft, contentTopOffset);

        // Draw newly typed animated characters
        if (!state.charAnims.isEmpty()) {
            TextPaint basePaint = view.getPaint();
            Paint p = state.charPaint;
            p.set(basePaint);
            int baseColor = view.getCurrentTextColor();
            int baseAlpha = Color.alpha(baseColor);
            String textStr = editable.toString();

            for (int i = 0, n = state.charAnims.size(); i < n; i++) {
                CharAnim a = state.charAnims.get(i);
                if (a.index < 0 || a.index + a.length > textStr.length()) {
                    continue;
                }
                long elapsed = now - a.startTimeMs;
                float tMotion = elapsed <= 0 ? 0f : Math.min(1f, elapsed / (float) Math.max(1, a.slideDurationMs));
                float tBlur = elapsed <= 0 ? 0f : Math.min(1f, elapsed / (float) Math.max(1, a.blurDurationMs));
                float eased = easeOutCubic(tMotion);
                float blurEased = easeOutQuad(tBlur);

                float alphaFactor = cfg.blurEnabled ? blurEased : eased;
                int finalAlpha = Math.max(0, Math.min(255, Math.round(baseAlpha * (0.12f + 0.88f * alphaFactor))));
                p.setColor(baseColor);
                p.setAlpha(finalAlpha);

                if (cfg.blurEnabled && cfg.blurRadiusDp > 0 && tBlur < 0.99f) {
                    float blurPx = dpf2(cfg.blurRadiusDp * 0.5f) * (1f - blurEased);
                    if (blurPx >= 0.6f) {
                        int bucket = Math.round(blurPx * 2f);
                        if (bucket != state.lastBlurBucket || state.lastBlurFilter == null) {
                            state.lastBlurFilter = new BlurMaskFilter(Math.max(0.5f, bucket / 2f), BlurMaskFilter.Blur.NORMAL);
                            state.lastBlurBucket = bucket;
                        }
                        p.setMaskFilter(state.lastBlurFilter);
                    } else {
                        p.setMaskFilter(null);
                    }
                } else {
                    p.setMaskFilter(null);
                }

                float offsetY = cfg.slideEnabled ? -dpf2(cfg.slideDistDp) * (1f - eased) : 0f;
                float scale = cfg.scaleEnabled ? (cfg.scaleStart + (1f - cfg.scaleStart) * eased) : 1f;
                float rotation = cfg.rotateEnabled ? (cfg.rotateAngle * (1f - eased)) : 0f;

                float pivotX = a.x + a.width * 0.5f;
                float pivotY = a.baseline - p.getTextSize() * 0.3f;

                canvas.save();
                canvas.translate(0f, offsetY);
                if (scale != 1f || rotation != 0f) {
                    canvas.translate(pivotX, pivotY);
                    if (scale != 1f) {
                        float safeScale = Math.max(0.05f, scale);
                        canvas.scale(safeScale, safeScale);
                    }
                    if (rotation != 0f) {
                        canvas.rotate(rotation);
                    }
                    canvas.translate(-pivotX, -pivotY);
                }
                canvas.drawText(textStr, a.index, a.index + a.length, a.x, a.baseline, p);
                canvas.restore();
                p.setMaskFilter(null);
                needsMoreFrames = true;
            }
        }

        // Draw delete burst particles
        if (!state.particles.isEmpty()) {
            needsMoreFrames |= drawParticles(canvas, state, now, view.getPaint());
        }

        canvas.restore();

        if (needsMoreFrames) {
            view.postInvalidateOnAnimation();
        }
    }

    /**
     * Draws the smooth interpolated and optionally liquid-stretched cursor inside {@code EditTextBoldCursor}'s
     * normal cursor coordinate block.
     */
    public static void drawCustomCursor(
            EditTextBoldCursor view,
            Canvas canvas,
            int cursorColor,
            float cursorWidthDp,
            int voffsetCursor,
            float cursorShiftX,
            float cursorShiftY
    ) {
        if (!isCustomCursorActive(view) || canvas == null) {
            return;
        }
        Layout layout = view.getLayout();
        Editable editable = view.getText();
        if (layout == null || editable == null) {
            return;
        }

        ViewState state = getState(view);
        Config cfg = cachedConfig;
        long now = SystemClock.uptimeMillis();

        int sel = Math.max(0, Math.min(view.getSelectionStart(), editable.length()));
        int line = layout.getLineForOffset(sel);
        int lineCount = Math.max(1, layout.getLineCount());

        float targetX = layout.getPrimaryHorizontal(sel);
        float lineTop = layout.getLineTop(line);
        float lineBottom = layout.getLineBottom(line);
        if (line < lineCount - 1) {
            lineBottom -= view.getLineSpacingExtra();
        }
        float vPad = Math.max(dpf2(1f), (lineBottom - lineTop) * 0.08f);
        float targetTop = lineTop + vPad;
        float targetBottom = Math.max(targetTop + dpf2(4f), lineBottom - vPad);

        float baseWidthPx = dpf2(Math.max(1.5f, cfg.cursorWidthSetting * 0.42f));
        if (baseWidthPx <= 0f) {
            baseWidthPx = dpf2(Math.max(1.5f, cursorWidthDp));
        }

        boolean needNextFrame = false;
        if (!state.cursorInitialized) {
            state.cursorInitialized = true;
            state.cursorX = targetX;
            state.cursorTop = targetTop;
            state.cursorBottom = targetBottom;
            state.cursorVelocityX = 0f;
            state.lastFrameMs = now;
        } else {
            long dtMs = Math.max(1L, Math.min(48L, now - state.lastFrameMs));
            state.lastFrameMs = now;
            float dt = dtMs / 1000f;
            float rate = 8f + cfg.cursorSpeed * 0.65f;
            float alpha = 1f - (float) Math.exp(-rate * dt);

            float prevX = state.cursorX;
            state.cursorX += (targetX - state.cursorX) * alpha;
            state.cursorTop += (targetTop - state.cursorTop) * alpha;
            state.cursorBottom += (targetBottom - state.cursorBottom) * alpha;
            state.cursorVelocityX = (state.cursorX - prevX) / Math.max(0.004f, dt);

            if (Math.abs(targetX - state.cursorX) > 0.25f
                    || Math.abs(targetTop - state.cursorTop) > 0.25f
                    || Math.abs(targetBottom - state.cursorBottom) > 0.25f) {
                needNextFrame = true;
            } else {
                state.cursorX = targetX;
                state.cursorTop = targetTop;
                state.cursorBottom = targetBottom;
                state.cursorVelocityX = 0f;
            }
        }

        float dxRemaining = targetX - state.cursorX;
        float stretch = 0f;
        float squeezeY = 0f;
        if (cfg.liquidCursorEnabled && cfg.liquidStretch > 0) {
            float rawStretch = dxRemaining * (cfg.liquidStretch / 100f) * 0.85f;
            float maxStretch = dp(28);
            stretch = Math.max(-maxStretch, Math.min(maxStretch, rawStretch));
            float mag = Math.min(1f, Math.abs(stretch) / Math.max(1f, dp(18)));
            squeezeY = (state.cursorBottom - state.cursorTop) * 0.14f * mag;
        }

        float left = state.cursorX - baseWidthPx * 0.5f + Math.min(0f, stretch);
        float right = state.cursorX + baseWidthPx * 0.5f + Math.max(0f, stretch);
        float top = state.cursorTop + squeezeY;
        float bottom = state.cursorBottom - squeezeY;

        // Soft breath blink only when stationary so moving cursor is always crisp
        float blinkAlpha = 1f;
        if (!needNextFrame) {
            long phase = now % 1060L;
            if (phase > 530L) {
                float p = (phase - 530L) / 530f;
                blinkAlpha = 1f - 0.85f * (float) Math.sin(p * Math.PI);
            }
        }

        int finalColor = cursorColor != 0 ? cursorColor : view.getCurrentTextColor();
        state.cursorPaint.setColor(finalColor);
        state.cursorPaint.setAlpha(Math.max(20, Math.round(Color.alpha(finalColor) * blinkAlpha)));

        canvas.save();
        int voffset = 0;
        if ((view.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            voffset = voffsetCursor;
        }
        canvas.translate(view.getPaddingLeft() + cursorShiftX, view.getExtendedPaddingTop() + voffset + cursorShiftY);
        TMP_RECT.set(left, top, right, bottom);
        float radius = Math.min(baseWidthPx, bottom - top) * 0.5f;
        canvas.drawRoundRect(TMP_RECT, radius, radius, state.cursorPaint);
        canvas.restore();

        // Keep cursor alive for smooth blink + motion
        view.postInvalidateOnAnimation();
    }

    private static void detectDiff(
            EditTextBoldCursor view,
            ViewState state,
            Layout layout,
            String currentText,
            long now,
            Config cfg
    ) {
        String oldText = state.lastText;
        int oldLen = oldText.length();
        int newLen = currentText.length();
        int minLen = Math.min(oldLen, newLen);

        int prefix = 0;
        while (prefix < minLen && oldText.charAt(prefix) == currentText.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < (minLen - prefix)
                && oldText.charAt(oldLen - 1 - suffix) == currentText.charAt(newLen - 1 - suffix)) {
            suffix++;
        }

        int added = newLen - prefix - suffix;
        int deleted = oldLen - prefix - suffix;

        // Shift existing animation indices cleanly after prefix
        int delta = added - deleted;
        for (int i = state.charAnims.size() - 1; i >= 0; i--) {
            CharAnim a = state.charAnims.get(i);
            if (a.index >= prefix && a.index < prefix + deleted) {
                state.charAnims.remove(i);
            } else if (a.index >= prefix + deleted) {
                a.index += delta;
            }
        }

        int activeLine = layout.getLineForOffset(Math.max(0, Math.min(view.getSelectionStart(), newLen)));

        // Newly added characters
        if (added > 0 && added <= TextAnimationController.BULK_CHANGE_THRESHOLD && added <= MAX_BURST_ANIM_CHARS) {
            int burstSlot = 0;
            int idx = prefix;
            int addedEnd = prefix + added;
            while (idx < addedEnd) {
                int charLen = Character.isHighSurrogate(currentText.charAt(idx)) && idx + 1 < addedEnd ? 2 : 1;
                char firstCh = currentText.charAt(idx);
                boolean skipSpace = cfg.ignoreSpaces && Character.isWhitespace(firstCh);
                int charLine = layout.getLineForOffset(Math.min(idx, newLen));
                boolean lineAllowed = cfg.animateAllLines || charLine == activeLine;

                if (!skipSpace && lineAllowed) {
                    CharAnim anim = new CharAnim();
                    anim.index = idx;
                    anim.length = charLen;
                    anim.startTimeMs = now + (long) burstSlot * cfg.blurTextDelayMs;
                    anim.slideDurationMs = cfg.durationMs;
                    anim.blurDurationMs = cfg.blurDurationMs;
                    fillAnimBounds(layout, anim, currentText);
                    if (anim.width > 0f) {
                        state.charAnims.add(anim);
                        burstSlot++;
                    }
                }
                idx += charLen;
            }
        }

        // Deleted characters -> particles
        if (cfg.deleteEnabled && deleted > 0 && deleted <= TextAnimationController.BULK_CHANGE_THRESHOLD) {
            int spawnEnd = Math.min(prefix + deleted, prefix + MAX_BURST_DELETE_CHARS);
            int textColor = view.getCurrentTextColor();
            float textSize = view.getTextSize();
            for (int i = prefix; i < spawnEnd; i++) {
                float gx = 0f;
                float gy = textSize;
                if (i < state.prevGlyphs.size()) {
                    GlyphPos pos = state.prevGlyphs.get(i);
                    gx = pos.x + pos.width * 0.5f;
                    gy = pos.baseline - textSize * 0.35f;
                } else {
                    int safeSel = Math.max(0, Math.min(prefix, newLen));
                    int l = layout.getLineForOffset(safeSel);
                    gx = layout.getPrimaryHorizontal(safeSel);
                    gy = layout.getLineBaseline(l) - textSize * 0.35f;
                }
                char delCh = oldText.charAt(i);
                if (!cfg.ignoreSpaces || !Character.isWhitespace(delCh)) {
                    spawnParticles(state, gx, gy, String.valueOf(delCh), textColor, textSize, now, cfg);
                }
            }
        }
    }

    private static void captureGlyphPositions(Layout layout, CharSequence text, ArrayList<GlyphPos> out) {
        out.clear();
        int len = Math.min(text.length(), MAX_TRACKED_CHARS);
        out.ensureCapacity(len);
        for (int i = 0; i < len; i++) {
            int line = layout.getLineForOffset(i);
            float h1 = layout.getPrimaryHorizontal(i);
            float h2 = (i + 1 <= len && layout.getLineForOffset(i + 1) == line)
                    ? layout.getPrimaryHorizontal(i + 1)
                    : h1 + dpf2(8f);
            GlyphPos p = new GlyphPos();
            p.x = Math.min(h1, h2);
            p.width = Math.max(dpf2(2f), Math.abs(h2 - h1));
            p.top = layout.getLineTop(line);
            p.bottom = layout.getLineBottom(line);
            p.baseline = layout.getLineBaseline(line);
            out.add(p);
        }
    }

    private static void pruneAndUpdateCharAnims(ViewState state, Layout layout, String text, long now, Config cfg) {
        Iterator<CharAnim> it = state.charAnims.iterator();
        while (it.hasNext()) {
            CharAnim a = it.next();
            int maxDur = Math.max(a.slideDurationMs, cfg.blurEnabled ? a.blurDurationMs : 0);
            if (a.index < 0 || a.index + a.length > text.length() || (now - a.startTimeMs) >= maxDur) {
                it.remove();
            } else {
                fillAnimBounds(layout, a, text);
            }
        }
    }

    private static void fillAnimBounds(Layout layout, CharAnim anim, String text) {
        int line = layout.getLineForOffset(anim.index);
        float h1 = layout.getPrimaryHorizontal(anim.index);
        float h2 = (anim.index + anim.length <= text.length() && layout.getLineForOffset(anim.index + anim.length) == line)
                ? layout.getPrimaryHorizontal(anim.index + anim.length)
                : h1 + dpf2(8f);
        anim.x = Math.min(h1, h2);
        anim.width = Math.max(0f, Math.abs(h2 - h1));
        anim.top = layout.getLineTop(line);
        anim.bottom = layout.getLineBottom(line);
        anim.baseline = layout.getLineBaseline(line);
    }

    private static void spawnParticles(
            ViewState state,
            float x,
            float y,
            String ch,
            int textColor,
            float textSize,
            long now,
            Config cfg
    ) {
        int count = cfg.particleCount;
        float speedScale = cfg.particleSpeed / 50f;
        float spreadScale = cfg.particleSpread / 50f;
        float sizeScale = cfg.particleSize / 50f;

        for (int i = 0; i < count; i++) {
            if (state.particles.size() >= MAX_PARTICLES_PER_VIEW) {
                state.particles.remove(0);
            }
            Particle pt = new Particle();
            pt.x = x + (RANDOM.nextFloat() - 0.5f) * dp(6) * spreadScale;
            pt.y = y + (RANDOM.nextFloat() - 0.5f) * dp(6) * spreadScale;
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            float baseVel = (dp(28) + RANDOM.nextFloat() * dp(44)) * speedScale * spreadScale;
            pt.vx = (float) (Math.cos(angle) * baseVel);
            pt.vy = (float) (Math.sin(angle) * baseVel) - dp(18) * speedScale;
            pt.size = Math.max(dpf2(1.4f), dpf2(2.6f) * sizeScale * (0.7f + RANDOM.nextFloat() * 0.6f));
            pt.rotation = RANDOM.nextFloat() * 360f;
            pt.vRot = (RANDOM.nextFloat() - 0.5f) * 320f * speedScale;
            pt.startTimeMs = now;
            pt.lifeMs = Math.max(240L, Math.round((420 + RANDOM.nextInt(240)) / Math.max(0.4f, speedScale * 0.7f)));
            pt.color = textColor;
            pt.style = cfg.particleStyle;
            pt.letter = ch;
            if (pt.style == 4) { // Letters scale relative to text size
                pt.size = Math.max(dpf2(8f), textSize * 0.68f * sizeScale);
            }
            state.particles.add(pt);
        }
    }

    private static boolean drawParticles(Canvas canvas, ViewState state, long now, TextPaint textPaint) {
        Paint p = state.particlePaint;
        boolean anyAlive = false;
        Iterator<Particle> it = state.particles.iterator();
        while (it.hasNext()) {
            Particle pt = it.next();
            long age = now - pt.startTimeMs;
            if (age >= pt.lifeMs) {
                it.remove();
                continue;
            }
            anyAlive = true;
            float t = age / (float) pt.lifeMs;
            float sec = age / 1000f;
            float fade = 1f - easeInQuad(t);
            int baseAlpha = Color.alpha(pt.color);
            p.setColor(pt.color);
            p.setAlpha(Math.max(0, Math.min(255, Math.round(baseAlpha * fade))));

            float px = pt.x + pt.vx * sec * (1f - 0.25f * t);
            float gravity = (pt.style == 1) ? dp(80) : (pt.style == 2 || pt.style == 3) ? dp(45) : dp(35);
            if (pt.style == 2) {
                px += (float) Math.sin(sec * 8f + pt.rotation) * dp(4);
            }
            float py = pt.y + pt.vy * sec + 0.5f * gravity * sec * sec;
            float rot = pt.rotation + pt.vRot * sec;

            switch (pt.style) {
                case 1: { // Sparks
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(Math.max(dpf2(1.2f), pt.size * 0.55f));
                    float len = pt.size * (2.2f - t);
                    float speedLen = (float) Math.hypot(pt.vx, pt.vy);
                    float nx = speedLen > 0.01f ? pt.vx / speedLen : 1f;
                    float ny = speedLen > 0.01f ? pt.vy / speedLen : 0f;
                    canvas.drawLine(px, py, px - nx * len, py - ny * len, p);
                    p.setStyle(Paint.Style.FILL);
                    break;
                }
                case 2: { // Snowflakes
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    p.setStrokeWidth(Math.max(dpf2(1f), pt.size * 0.35f));
                    float r = pt.size * 1.35f * (1f - 0.2f * t);
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.rotate(rot);
                    for (int k = 0; k < 3; k++) {
                        canvas.drawLine(-r, 0f, r, 0f, p);
                        canvas.rotate(60f);
                    }
                    canvas.restore();
                    p.setStyle(Paint.Style.FILL);
                    break;
                }
                case 3: { // Petals
                    p.setStyle(Paint.Style.FILL);
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.rotate(rot);
                    float rx = pt.size * 1.4f;
                    float ry = pt.size * 0.72f;
                    TMP_RECT.set(-rx, -ry, rx, ry);
                    canvas.drawOval(TMP_RECT, p);
                    canvas.restore();
                    break;
                }
                case 4: { // Letters
                    p.setStyle(Paint.Style.FILL);
                    p.setTypeface(textPaint.getTypeface());
                    p.setTextSize(pt.size * (1f - 0.35f * t));
                    canvas.save();
                    canvas.translate(px, py);
                    canvas.rotate(rot);
                    canvas.drawText(pt.letter, -pt.size * 0.25f, pt.size * 0.25f, p);
                    canvas.restore();
                    break;
                }
                case 0:
                default: { // Dust
                    p.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(px, py, pt.size * (1f - 0.35f * t), p);
                    break;
                }
            }
        }
        return anyAlive;
    }

    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static float easeOutQuad(float t) {
        float inv = 1f - t;
        return 1f - inv * inv;
    }

    private static float easeInQuad(float t) {
        return t * t;
    }
}
