package app.exteraless.glyph;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

/**
 * Карточка интерактивного предпросмотра Nothing Phone в OpenExteraGlyphActivity.
 * Отрисовывает реальную топологию глифов (Glyph Interface / Glyph Matrix / Glyph Bar)
 * поверх корпуса выбранного смартфона Nothing и анимирует каждую световую зону
 * в точном соответствии с выбранным стилем анимации (Message, Call, Recording).
 */
public class GlyphDevicePreviewCell extends View {

    private String currentModel;
    private Drawable phoneDrawable;
    private int currentResId = 0;

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint matrixBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint matrixPixelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF cardRect = new RectF();
    private final RectF tempRect = new RectF();

    private ValueAnimator pulseAnimator;
    private float pulseScale = 1.0f;
    private float animProgress = 0.0f;
    private boolean animRunning = false;
    /** 0 = Новое сообщение, 1 = Звонок, 2 = Запись голосового */
    private int activePreviewType = 0;

    public GlyphDevicePreviewCell(Context context) {
        super(context);
        currentModel = NothingPhoneEasterEgg.getDeviceDisplayName();

        cardPaint.setStyle(Paint.Style.FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(1));

        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(AndroidUtilities.dp(14));
        titlePaint.setFakeBoldText(true);

        subtitlePaint.setTextAlign(Paint.Align.CENTER);
        subtitlePaint.setTextSize(AndroidUtilities.dp(11.5f));

        glyphCorePaint.setStyle(Paint.Style.STROKE);
        glyphCorePaint.setStrokeCap(Paint.Cap.ROUND);
        glyphCorePaint.setColor(Color.WHITE);

        glyphGlowPaint.setStyle(Paint.Style.STROKE);
        glyphGlowPaint.setStrokeCap(Paint.Cap.ROUND);
        glyphGlowPaint.setColor(0xFFFFF4E8);
        try {
            glyphGlowPaint.setMaskFilter(new BlurMaskFilter(AndroidUtilities.dp(5.5f), BlurMaskFilter.Blur.NORMAL));
        } catch (Throwable ignored) {}

        matrixBgPaint.setStyle(Paint.Style.FILL);
        matrixBgPaint.setColor(0xEE0B0C0E);

        matrixPixelPaint.setStyle(Paint.Style.FILL);
        matrixPixelPaint.setColor(Color.WHITE);

        ensureDrawableLoaded();
    }

    public void setModel(String modelName) {
        if (modelName != null && !modelName.equals(currentModel)) {
            currentModel = modelName;
            ensureDrawableLoaded();
            triggerPreviewAnimation(activePreviewType);
        }
    }

    public String getCurrentModel() {
        return currentModel;
    }

    private void ensureDrawableLoaded() {
        try {
            int[] drawables = NothingPhoneEasterEgg.getDrawablesForModelName(currentModel);
            if (drawables != null && drawables.length > 0) {
                int resId = drawables[0];
                if (resId != currentResId || phoneDrawable == null) {
                    currentResId = resId;
                    phoneDrawable = ContextCompat.getDrawable(getContext(), resId);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public void triggerPreviewAnimation(int type) {
        activePreviewType = type;
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        long duration = (type == 0) ? 1450L : 2500L;
        pulseAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
        pulseAnimator.setDuration(duration);
        pulseAnimator.setInterpolator(new LinearInterpolator());
        pulseAnimator.addUpdateListener(anim -> {
            animProgress = (float) anim.getAnimatedValue();
            if (animProgress < 0.16f) {
                float t = animProgress / 0.16f;
                pulseScale = 1.0f + 0.032f * (float) Math.sin(t * Math.PI);
            } else {
                pulseScale = 1.0f;
            }
            invalidate();
        });
        pulseAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) {
                animRunning = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                animRunning = false;
                pulseScale = 1.0f;
                invalidate();
            }
        });
        pulseAnimator.start();
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(224);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ensureDrawableLoaded();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        ensureDrawableLoaded();

        // 1. Фон карточки
        float marginH = AndroidUtilities.dp(16);
        float marginV = AndroidUtilities.dp(6);
        cardRect.set(marginH, marginV, width - marginH, height - marginV);

        int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite);
        cardPaint.setColor(bgColor);
        canvas.drawRoundRect(cardRect, AndroidUtilities.dp(18), AndroidUtilities.dp(18), cardPaint);

        int strokeColor = Theme.getColor(Theme.key_windowBackgroundGrayShadow);
        strokePaint.setColor(strokeColor);
        canvas.drawRoundRect(cardRect, AndroidUtilities.dp(18), AndroidUtilities.dp(18), strokePaint);

        // 2. Изображение смартфона и оверлей реальных глифов модели
        if (phoneDrawable != null) {
            float phoneH = AndroidUtilities.dp(152);
            int intrinsicW = phoneDrawable.getIntrinsicWidth();
            int intrinsicH = phoneDrawable.getIntrinsicHeight();
            float aspect = (intrinsicW > 0 && intrinsicH > 0) ? (intrinsicW / (float) intrinsicH) : 0.48f;
            float phoneW = phoneH * aspect;

            float phoneLeft = (width - phoneW) / 2f;
            float phoneTop = marginV + AndroidUtilities.dp(14);

            canvas.save();
            if (Math.abs(pulseScale - 1.0f) > 0.001f) {
                canvas.scale(pulseScale, pulseScale, width / 2f, phoneTop + phoneH / 2f);
            }

            phoneDrawable.setBounds((int) phoneLeft, (int) phoneTop, (int) (phoneLeft + phoneW), (int) (phoneTop + phoneH));
            phoneDrawable.draw(canvas);

            drawModelGlyphOverlay(canvas, phoneLeft, phoneTop, phoneW, phoneH);

            canvas.restore();
        }

        // 3. Подпись модели и подсказка управления
        titlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitlePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));

        float textY = marginV + AndroidUtilities.dp(182);
        canvas.drawText(currentModel != null ? currentModel : "Nothing Phone", width / 2f, textY, titlePaint);
        canvas.drawText("Нажмите для эффекта • Удерживайте для смены модели", width / 2f, textY + AndroidUtilities.dp(16), subtitlePaint);
    }

    /**
     * Вычисляет яркость конкретной зоны (0..1) в момент `animProgress` для выбранной модели и стиля анимации.
     * В режиме ожидания (`!animRunning`) возвращает лёгкое дежурное свечение (0.28f), чтобы геометрия
     * глифов выбранного телефона была отчётливо видна на карточке.
     */
    private float computeZoneIntensity(int zoneIndex, int totalZones) {
        if (!animRunning) {
            return 0.25f;
        }
        float p = animProgress;
        if (activePreviewType == 0) {
            int style = GlyphConfig.messageAnimationStyle();
            if (style == GlyphConfig.MESSAGE_ANIM_STROBE) {
                // 3 короткие вспышки
                float cycle = (p * 3.5f);
                if (cycle >= 3.0f) return 0f;
                float frac = cycle - (int) cycle;
                return frac < 0.48f ? 1.0f : 0.05f;
            } else if (style == GlyphConfig.MESSAGE_ANIM_SOFT_PULSE) {
                // Мягкий синусоидальный импульс
                return (float) Math.sin(p * Math.PI);
            } else if (style == GlyphConfig.MESSAGE_ANIM_ACCENT_RING) {
                // Акцентное кольцо (зона 0 или центральные зоны)
                boolean isAccent = (zoneIndex == 0 || zoneIndex == 2 || (totalZones == 6 && (zoneIndex == 2 || zoneIndex == 3)));
                if (!isAccent) return 0.08f;
                float cycle = p * 2.0f;
                float frac = cycle - (int) cycle;
                return (float) Math.sin(frac * Math.PI);
            } else {
                // Двойная вспышка (Double Flash)
                if (p < 0.35f) {
                    return (float) Math.sin((p / 0.35f) * Math.PI);
                } else if (p < 0.48f) {
                    return 0.05f;
                } else if (p < 0.85f) {
                    return (float) Math.sin(((p - 0.48f) / 0.37f) * Math.PI);
                }
                return 0.0f;
            }
        } else if (activePreviewType == 1) {
            int style = GlyphConfig.callAnimationStyle();
            if (style == GlyphConfig.CALL_ANIM_WAVE) {
                // Бегущая волна по зонам
                float wavePos = (p * 3.0f * totalZones) % totalZones;
                float dist = Math.abs(zoneIndex - wavePos);
                dist = Math.min(dist, totalZones - dist);
                return Math.max(0.08f, 1.0f - dist * 0.65f);
            } else if (style == GlyphConfig.CALL_ANIM_BREATHING) {
                return 0.15f + 0.85f * (float) Math.abs(Math.sin(p * 2.0f * Math.PI));
            } else {
                // Ритмичный пульс звонка
                float cycle = (p * 4.0f) % 1.0f;
                return cycle < 0.65f ? (float) Math.sin((cycle / 0.65f) * Math.PI) : 0.08f;
            }
        } else {
            int style = GlyphConfig.recordingAnimationStyle();
            if (style == GlyphConfig.RECORDING_ANIM_ACCENT_RING) {
                boolean isAccent = (zoneIndex == 0 || zoneIndex == 2 || (totalZones == 6 && (zoneIndex == 2 || zoneIndex == 3)));
                if (!isAccent) return 0.06f;
                return 0.2f + 0.8f * (float) Math.sin(p * 2.0f * Math.PI);
            } else if (style == GlyphConfig.RECORDING_ANIM_HEARTBEAT) {
                boolean isBeatZone = (zoneIndex == 1 || zoneIndex == 3 || zoneIndex == 0 || (totalZones == 6 && (zoneIndex == 1 || zoneIndex == 4)));
                if (!isBeatZone) return 0.08f;
                float cycle = (p * 2.5f) % 1.0f;
                if (cycle < 0.22f) return (float) Math.sin((cycle / 0.22f) * Math.PI);
                if (cycle >= 0.28f && cycle < 0.52f) return 0.85f * (float) Math.sin(((cycle - 0.28f) / 0.24f) * Math.PI);
                return 0.08f;
            } else {
                // Глубокое дыхание при записи
                return 0.12f + 0.88f * (float) Math.sin(p * Math.PI);
            }
        }
    }

    private void applyZoneAlpha(float intensity, float strokeDp) {
        int clampedAlpha = Math.max(0, Math.min(255, Math.round(intensity * 255f)));
        int glowAlpha = Math.max(0, Math.min(220, Math.round(intensity * 210f)));
        glyphCorePaint.setStyle(Paint.Style.STROKE);
        glyphCorePaint.setStrokeWidth(AndroidUtilities.dp(strokeDp));
        glyphCorePaint.setAlpha(clampedAlpha);

        glyphGlowPaint.setStyle(Paint.Style.STROKE);
        glyphGlowPaint.setStrokeWidth(AndroidUtilities.dp(strokeDp * 2.4f));
        glyphGlowPaint.setAlpha(glowAlpha);
    }

    private void drawGlowingLine(Canvas canvas, float x1, float y1, float x2, float y2, float intensity, float strokeDp) {
        if (intensity <= 0.02f) return;
        applyZoneAlpha(intensity, strokeDp);
        canvas.drawLine(x1, y1, x2, y2, glyphGlowPaint);
        canvas.drawLine(x1, y1, x2, y2, glyphCorePaint);
    }

    private void drawGlowingArc(Canvas canvas, RectF oval, float startAngle, float sweepAngle, float intensity, float strokeDp) {
        if (intensity <= 0.02f) return;
        applyZoneAlpha(intensity, strokeDp);
        canvas.drawArc(oval, startAngle, sweepAngle, false, glyphGlowPaint);
        canvas.drawArc(oval, startAngle, sweepAngle, false, glyphCorePaint);
    }

    private void drawGlowingDot(Canvas canvas, float cx, float cy, float radiusDp, float intensity) {
        if (intensity <= 0.02f) return;
        int clampedAlpha = Math.max(0, Math.min(255, Math.round(intensity * 255f)));
        glyphGlowPaint.setStyle(Paint.Style.FILL);
        glyphGlowPaint.setAlpha(Math.min(220, Math.round(intensity * 210f)));
        glyphCorePaint.setStyle(Paint.Style.FILL);
        glyphCorePaint.setAlpha(clampedAlpha);
        float r = AndroidUtilities.dp(radiusDp);
        canvas.drawCircle(cx, cy, r * 1.85f, glyphGlowPaint);
        canvas.drawCircle(cx, cy, r, glyphCorePaint);
    }

    private void drawModelGlyphOverlay(Canvas canvas, float left, float top, float w, float h) {
        String model = currentModel != null ? currentModel : "";
        if (model.startsWith("CMF")) {
            return;
        }
        if ("Nothing Phone (3)".equals(model)) {
            // Матрица 25x25 строго внутри чёрного круглого окна в правом верхнем углу корпуса Phone (3)
            drawMatrixOverlay(canvas, left + w * 0.791f, top + h * 0.0938f, w * 0.132f, 11);
        } else if ("Nothing Phone (4a) Pro".equals(model)) {
            // Матрица 13x13 строго внутри круглого окна справа от блока камер Phone (4a) Pro
            drawMatrixOverlay(canvas, left + w * 0.706f, top + h * 0.1406f, w * 0.155f, 9);
        } else if ("Nothing Phone (4a)".equals(model) || "Nothing Phone (4b)".equals(model)) {
            // Glyph Bar из 6 квадратных светодиодов справа от камеры
            boolean is4b = "Nothing Phone (4b)".equals(model);
            float barX = left + w * (is4b ? 0.798f : 0.886f);
            float barW = w * 0.036f;
            float startY = top + h * (is4b ? 0.125f : 0.104f);
            float totalH = h * (is4b ? 0.096f : 0.142f);
            float gap = totalH * 0.045f;
            float segH = (totalH - gap * 5f) / 6f;
            for (int i = 0; i < 6; i++) {
                float intensity = computeZoneIntensity(i, 6);
                if (intensity > 0.02f) {
                    float sy = startY + i * (segH + gap);
                    tempRect.set(barX, sy, barX + barW, sy + segH);
                    glyphGlowPaint.setStyle(Paint.Style.FILL);
                    glyphGlowPaint.setAlpha(Math.min(220, Math.round(intensity * 210f)));
                    glyphCorePaint.setStyle(Paint.Style.FILL);
                    glyphCorePaint.setAlpha(Math.min(255, Math.round(intensity * 255f)));
                    canvas.drawRoundRect(tempRect, AndroidUtilities.dp(1.2f), AndroidUtilities.dp(1.2f), glyphGlowPaint);
                    canvas.drawRoundRect(tempRect, AndroidUtilities.dp(1.0f), AndroidUtilities.dp(1.0f), glyphCorePaint);
                }
            }
        } else if ("Nothing Phone (3a) Lite".equals(model)) {
            // Одиночный круглый глиф-индикатор в правом нижнем углу Phone (3a) Lite
            float intensity = computeZoneIntensity(0, 1);
            drawGlowingDot(canvas, left + w * 0.881f, top + h * 0.908f, 2.5f, intensity);
        } else if ("Nothing Phone (3a)".equals(model) || "Nothing Phone (3a) Pro".equals(model)) {
            // 3 дуговых полоски вокруг круглого острова камер Phone (3a) / (3a) Pro
            float cx = left + w * 0.50f;
            float cy = top + h * 0.225f;
            float rx = w * 0.405f;
            float ry = h * 0.192f;
            tempRect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            // A: верхняя левая дуга
            drawGlowingArc(canvas, tempRect, 198f, 44f, computeZoneIntensity(0, 3), 2.5f);
            // B: нижняя левая полоска
            drawGlowingArc(canvas, tempRect, 132f, 24f, computeZoneIntensity(1, 3), 2.5f);
            // C: правая дуга
            drawGlowingArc(canvas, tempRect, -22f, 52f, computeZoneIntensity(2, 3), 2.5f);
        } else if ("Nothing Phone (2a)".equals(model) || "Nothing Phone (2a) Plus".equals(model)) {
            // 3 зоны вокруг верхнего круга Phone (2a) / (2a) Plus: C (верх-лево), B (право вертикально), A (низ-лево)
            float cx = left + w * 0.50f;
            float cy = top + h * 0.215f;
            float rx = w * 0.40f;
            float ry = h * 0.185f;
            tempRect.set(cx - rx, cy - ry, cx + rx, cy + ry);
            drawGlowingArc(canvas, tempRect, 196f, 48f, computeZoneIntensity(0, 3), 2.5f);
            drawGlowingLine(canvas, left + w * 0.895f, top + h * 0.150f, left + w * 0.895f, top + h * 0.288f, computeZoneIntensity(1, 3), 2.5f);
            drawGlowingArc(canvas, tempRect, 130f, 24f, computeZoneIntensity(2, 3), 2.5f);
        } else if ("Nothing Phone (2)".equals(model)) {
            // 11 сегментированных глиф-элементов Nothing Phone (2)
            // Зона 0 (A1, A2): капсула камеры
            tempRect.set(left + w * 0.088f, top + h * 0.036f, left + w * 0.292f, top + h * 0.236f);
            drawGlowingArc(canvas, tempRect, -150f, 180f, computeZoneIntensity(0, 5), 2.4f);
            drawGlowingArc(canvas, tempRect, 35f, 130f, computeZoneIntensity(0, 5), 2.4f);
            // Зона 1 (B1): правая верхняя диагональ
            drawGlowingLine(canvas, left + w * 0.862f, top + h * 0.062f, left + w * 0.665f, top + h * 0.178f, computeZoneIntensity(1, 5), 2.4f);
            // Зона 2 (C1..C6): прерывистое 6-сегментное центральное кольцо
            tempRect.set(left + w * 0.082f, top + h * 0.250f, left + w * 0.918f, top + h * 0.745f);
            float cInt = computeZoneIntensity(2, 5);
            drawGlowingArc(canvas, tempRect, -100f, 68f, cInt, 2.4f);
            drawGlowingArc(canvas, tempRect, -12f, 26f, cInt, 2.4f);
            drawGlowingArc(canvas, tempRect, 26f, 38f, cInt, 2.4f);
            drawGlowingArc(canvas, tempRect, 78f, 95f, cInt, 2.4f);
            drawGlowingArc(canvas, tempRect, 198f, 38f, cInt, 2.4f);
            // Зона 3 (D1_1..D1_8): нижняя вертикальная полоса
            drawGlowingLine(canvas, left + w * 0.497f, top + h * 0.800f, left + w * 0.497f, top + h * 0.910f, computeZoneIntensity(3, 5), 2.5f);
            // Зона 4 (E1): нижняя точка
            drawGlowingDot(canvas, left + w * 0.497f, top + h * 0.942f, 2.0f, computeZoneIntensity(4, 5));
        } else {
            // Nothing Phone (1) — классические 5 глиф-зон (A, B, C, D, E)
            // Зона 0 (A1): капсула камеры
            tempRect.set(left + w * 0.088f, top + h * 0.038f, left + w * 0.292f, top + h * 0.238f);
            drawGlowingArc(canvas, tempRect, 25f, 305f, computeZoneIntensity(0, 5), 2.5f);
            // Зона 1 (B1): правая верхняя диагональ
            drawGlowingLine(canvas, left + w * 0.862f, top + h * 0.062f, left + w * 0.665f, top + h * 0.178f, computeZoneIntensity(1, 5), 2.5f);
            // Зона 2 (C1..C4): центральная подкова вокруг катушки зарядки
            tempRect.set(left + w * 0.082f, top + h * 0.248f, left + w * 0.922f, top + h * 0.752f);
            drawGlowingArc(canvas, tempRect, -5f, 330f, computeZoneIntensity(2, 5), 2.5f);
            // Зона 3 (D1_1..D1_8): нижняя вертикальная полоса
            drawGlowingLine(canvas, left + w * 0.503f, top + h * 0.808f, left + w * 0.503f, top + h * 0.916f, computeZoneIntensity(3, 5), 2.5f);
            // Зона 4 (E1): нижняя точка
            drawGlowingDot(canvas, left + w * 0.503f, top + h * 0.950f, 2.0f, computeZoneIntensity(4, 5));
        }
    }

    /**
     * Отрисовывает круглый матричный экран Glyph Matrix (на Phone 3 и Phone 4a Pro)
     * с пиксельной сеткой и живой анимацией выбранного стиля.
     */
    private void drawMatrixOverlay(Canvas canvas, float cx, float cy, float radius, int gridN) {
        if (!animRunning) {
            return;
        }
        canvas.drawCircle(cx, cy, radius * 0.94f, matrixBgPaint);

        float cellStep = (radius * 1.62f) / gridN;
        float dotRadius = cellStep * 0.34f;
        float half = (gridN - 1) / 2.0f;
        float maxDist = half + 0.25f;

        for (int r = 0; r < gridN; r++) {
            for (int c = 0; c < gridN; c++) {
                float dr = r - half;
                float dc = c - half;
                float dist = (float) Math.hypot(dr, dc);
                if (dist > maxDist) continue;

                float normDist = dist / maxDist;
                float angleDeg = (float) Math.toDegrees(Math.atan2(dr, dc));
                if (angleDeg < 0) angleDeg += 360f;

                float pixelAlpha = computeMatrixPixelAlpha(dr, dc, normDist, angleDeg);
                if (pixelAlpha > 0.05f) {
                    float px = cx + dc * cellStep;
                    float py = cy + dr * cellStep;
                    matrixPixelPaint.setAlpha(Math.min(255, Math.round(pixelAlpha * 255f)));
                    canvas.drawCircle(px, py, dotRadius, matrixPixelPaint);
                }
            }
        }
    }

    private static float blendPreviewIconAndEffect(float iconVal, float effectVal) {
        if (effectVal <= 0.05f) {
            return iconVal;
        }
        if (iconVal > 0.25f && effectVal > 0.35f) {
            return Math.max(0.04f, effectVal - iconVal);
        }
        return Math.max(iconVal, effectVal);
    }

    private float computeMatrixPixelAlpha(float dr, float dc, float normDist, float angleDeg) {
        float p = animProgress;
        boolean showIcon = GlyphConfig.matrixShowIcon();
        boolean isCenterIcon = showIcon && normDist < 0.48f && (Math.abs(dr) + Math.abs(dc) < 3.2f);

        if (activePreviewType == 0) {
            int style = GlyphConfig.messageAnimationStyle();
            if (style == GlyphConfig.MESSAGE_ANIM_ACCENT_RING) {
                float ringPos = (p * 1.35f) % 1.0f;
                float ringDiff = Math.abs(normDist - ringPos);
                float ringVal = ringDiff < 0.22f ? (1.0f - ringDiff / 0.22f) : 0f;
                float iconVal = isCenterIcon ? 0.85f * (1.0f - p * 0.5f) : 0f;
                return blendPreviewIconAndEffect(iconVal, ringVal);
            } else if (style == GlyphConfig.MESSAGE_ANIM_STROBE) {
                float cycle = (p * 3.5f);
                if (cycle >= 3.0f) return 0f;
                boolean on = (cycle - (int) cycle) < 0.5f;
                boolean inRing = normDist > 0.78f || (!showIcon && Math.abs(normDist - 0.52f) < 0.16f);
                return on && (isCenterIcon || inRing) ? 1.0f : 0.06f;
            } else if (style == GlyphConfig.MESSAGE_ANIM_SOFT_PULSE) {
                float wave = (float) Math.sin(p * Math.PI);
                if (!showIcon) {
                    float ringTarget = 0.35f + 0.55f * wave;
                    float diff = Math.abs(normDist - ringTarget);
                    return diff < 0.22f ? wave * (1.0f - diff / 0.22f) : 0f;
                }
                return isCenterIcon ? wave : (normDist < 0.72f ? wave * 0.45f : 0f);
            } else {
                // Double Flash
                float flash = (p < 0.36f) ? (float) Math.sin((p / 0.36f) * Math.PI)
                        : (p >= 0.48f && p < 0.88f ? (float) Math.sin(((p - 0.48f) / 0.40f) * Math.PI) : 0f);
                if (!showIcon) {
                    return (normDist > 0.78f || Math.abs(normDist - 0.45f) < 0.16f) ? flash : 0f;
                }
                return isCenterIcon ? flash : (normDist > 0.78f ? flash * 0.6f : 0f);
            }
        } else if (activePreviewType == 1) {
            int style = GlyphConfig.callAnimationStyle();
            if (style == GlyphConfig.CALL_ANIM_WAVE) {
                // Вращающийся радар по периметру (+ иконка в центре)
                float effectVal = 0.05f;
                if (normDist > 0.68f) {
                    float head = (p * 720f) % 360f;
                    float diff = (head - angleDeg + 360f) % 360f;
                    if (diff < 140f) effectVal = 1.0f - diff / 140f;
                }
                return blendPreviewIconAndEffect(isCenterIcon ? 0.9f : 0f, effectVal);
            } else if (style == GlyphConfig.CALL_ANIM_PULSE) {
                float ringPos = (p * 3.0f) % 1.0f;
                float ringDiff = Math.abs(normDist - ringPos);
                float ringVal = ringDiff < 0.22f ? (1.0f - ringDiff / 0.22f) : 0f;
                return blendPreviewIconAndEffect(isCenterIcon ? 0.92f : 0f, ringVal);
            } else {
                float breath = 0.2f + 0.8f * (float) Math.abs(Math.sin(p * 2.0f * Math.PI));
                if (!showIcon) {
                    float ringTarget = 0.42f + 0.50f * breath;
                    float diff = Math.abs(normDist - ringTarget);
                    return diff < 0.22f ? breath * (1.0f - diff / 0.22f) : 0f;
                }
                return isCenterIcon ? breath : (normDist < 0.68f ? breath * 0.4f : 0f);
            }
        } else {
            int style = GlyphConfig.recordingAnimationStyle();
            if (style == GlyphConfig.RECORDING_ANIM_ACCENT_RING) {
                float effectVal = 0f;
                if (normDist > 0.68f) {
                    float sweep = ((p * 2.0f) % 1.0f) * 360f;
                    float cwAngle = (angleDeg + 90f) % 360f;
                    effectVal = cwAngle <= sweep ? 1.0f : 0.1f;
                }
                return blendPreviewIconAndEffect(isCenterIcon ? 0.85f : 0f, effectVal);
            } else if (style == GlyphConfig.RECORDING_ANIM_HEARTBEAT) {
                float cycle = (p * 2.5f) % 1.0f;
                float beat = 0.1f;
                if (cycle < 0.22f) beat = (float) Math.sin((cycle / 0.22f) * Math.PI);
                else if (cycle >= 0.28f && cycle < 0.52f) beat = 0.9f * (float) Math.sin(((cycle - 0.28f) / 0.24f) * Math.PI);
                return isCenterIcon ? beat : (normDist > 0.75f ? beat * (showIcon ? 0.75f : 1.0f) : 0f);
            } else {
                float breath = 0.15f + 0.85f * (float) Math.sin(p * Math.PI);
                if (!showIcon) {
                    float ringTarget = 0.42f + 0.50f * breath;
                    float diff = Math.abs(normDist - ringTarget);
                    return diff < 0.22f ? breath * (1.0f - diff / 0.22f) : 0f;
                }
                return isCenterIcon ? breath : (normDist < 0.65f ? breath * 0.35f : 0f);
            }
        }
    }
}
