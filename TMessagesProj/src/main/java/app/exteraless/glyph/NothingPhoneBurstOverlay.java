package app.exteraless.glyph;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import app.exteraless.appearance.Md3AvatarShapes;

/**
 * Interactive Material Design 3 physics overlay for Nothing Phone easter egg.
 * Spawns 100% opaque falling MD3 Expressive shapes and device silhouettes,
 * runs stiff inter-item collision separation (no penetrating/overlapping),
 * applies smooth angular damping (no wild spinning), guarantees screen filling to the top,
 * presents a bouncy 2/3-screen MD3 cookie with a prominent MoltenGram vector logo,
 * and drops the floor on dismissal with reverse-spinning cookie.
 */
public class NothingPhoneBurstOverlay extends View {

    public static final int SHAPE_CIRCLE = Md3AvatarShapes.SHAPE_CIRCLE;
    public static final int SHAPE_PILL = Md3AvatarShapes.SHAPE_PILL;
    public static final int SHAPE_CLOVER = Md3AvatarShapes.SHAPE_CLOVER_4;
    public static final int SHAPE_SCALLOP = Md3AvatarShapes.SHAPE_SCALLOP;

    private static final int[] MD3_FALLING_SHAPES = new int[]{
            Md3AvatarShapes.SHAPE_SQUARE,
            Md3AvatarShapes.SHAPE_PILL,
            Md3AvatarShapes.SHAPE_CLOVER_4,
            Md3AvatarShapes.SHAPE_CLOVER_8,
            Md3AvatarShapes.SHAPE_COOKIE_4,
            Md3AvatarShapes.SHAPE_COOKIE_9,
            Md3AvatarShapes.SHAPE_COOKIE_12,
            Md3AvatarShapes.SHAPE_FLOWER,
            Md3AvatarShapes.SHAPE_PUFFY,
            Md3AvatarShapes.SHAPE_HEART,
            Md3AvatarShapes.SHAPE_STAR_4,
            Md3AvatarShapes.SHAPE_HEXAGON,
            Md3AvatarShapes.SHAPE_SCALLOP,
            Md3AvatarShapes.SHAPE_CLAMSHELL,
            Md3AvatarShapes.SHAPE_SUNNY
    };

    private static class Silhouette {
        float halfWidth;
        float halfHeight;
        float effectiveRadius;
    }

    private static class PhysicsItem {
        boolean isDevice;
        Bitmap bitmap;
        Silhouette silhouette;

        int shapeId;
        int color;
        float baseWidth;
        float baseHeight;

        float x;
        float y;
        float vx;
        float vy;
        float rotation;
        float vRot;
        float scale;
        boolean settled;
    }

    private final List<PhysicsItem> items = new ArrayList<>();
    private final List<Bitmap> deviceBitmaps = new ArrayList<>();
    private final List<Silhouette> deviceSilhouettes = new ArrayList<>();

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cookiePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix drawMatrix = new Matrix();

    private Drawable logoDrawable;
    private Vibrator vibrator;

    private ValueAnimator simulationAnimator;
    private ValueAnimator cookieAnimator;
    private ValueAnimator dismissAnimator;

    private float cookieProgress = 0f;
    private float dismissCookieProgress = 0f;
    private boolean isScreenFilled = false;
    private boolean isDismissing = false;
    private boolean floorDisabled = false;

    private float[] columnFloors;
    private static final int COLUMNS_COUNT = 9;

    private long lastHapticTime = 0;
    private int spawnedCount = 0;
    private static final int TARGET_SPAWN_ITEMS = 160;

    private android.window.OnBackInvokedCallback backCallback;

    public NothingPhoneBurstOverlay(Context context) {
        super(context);
        shapePaint.setStyle(Paint.Style.FILL);
        cookiePaint.setStyle(Paint.Style.FILL);

        setFocusable(true);
        setFocusableInTouchMode(true);

        try {
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable ignored) {}

        try {
            logoDrawable = ContextCompat.getDrawable(context, R.drawable.moltengram_icon_monochrome);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static void show(Activity activity, int[] drawableResIds) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            NothingPhoneBurstOverlay overlay = new NothingPhoneBurstOverlay(activity);
            decorView.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            overlay.start(drawableResIds);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void start(int[] drawableResIds) {
        post(() -> {
            requestFocus();
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                width = AndroidUtilities.displaySize.x;
                height = AndroidUtilities.displaySize.y;
            }

            columnFloors = new float[COLUMNS_COUNT];
            for (int i = 0; i < COLUMNS_COUNT; i++) {
                columnFloors[i] = height - AndroidUtilities.dp(16);
            }

            if (drawableResIds != null && drawableResIds.length > 0) {
                for (int resId : drawableResIds) {
                    try {
                        Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId);
                        if (bmp != null) {
                            deviceBitmaps.add(bmp);
                            deviceSilhouettes.add(computeSilhouette(bmp));
                        }
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
            }

            startSimulationLoop();
        });
    }

    /**
     * Calculates authentic silhouette bounds ignoring transparent padding (alpha <= 128 / 50%).
     */
    private Silhouette computeSilhouette(Bitmap bmp) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        Silhouette s = new Silhouette();
        if (w <= 0 || h <= 0) {
            s.halfWidth = AndroidUtilities.dp(30);
            s.halfHeight = AndroidUtilities.dp(60);
            s.effectiveRadius = AndroidUtilities.dp(40);
            return s;
        }

        int step = Math.max(2, Math.min(w, h) / 36);
        int minX = w, maxX = 0, minY = h, maxY = 0;
        int opaqueCount = 0;

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int pixel = bmp.getPixel(x, y);
                int alpha = (pixel >>> 24) & 0xFF;
                if (alpha > 128) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    opaqueCount++;
                }
            }
        }

        if (opaqueCount > 4 && maxX > minX && maxY > minY) {
            s.halfWidth = Math.max(AndroidUtilities.dp(14), (maxX - minX) / 2f);
            s.halfHeight = Math.max(AndroidUtilities.dp(24), (maxY - minY) / 2f);
            // Effective collision radius covers the phone body nicely
            s.effectiveRadius = Math.max(s.halfWidth, s.halfHeight * 0.72f);
        } else {
            s.halfWidth = w / 2f;
            s.halfHeight = h / 2f;
            s.effectiveRadius = Math.min(w, h) / 2f;
        }
        return s;
    }

    private void startSimulationLoop() {
        Random rand = new Random();
        int[] themeColors = getThemeColors();

        simulationAnimator = ValueAnimator.ofFloat(0f, 1f);
        simulationAnimator.setDuration(16000L);
        simulationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        simulationAnimator.addUpdateListener(animation -> {
            int width = getWidth() > 0 ? getWidth() : AndroidUtilities.displaySize.x;
            int height = getHeight() > 0 ? getHeight() : AndroidUtilities.displaySize.y;

            // 1. Spawning items continuously until screen is properly filled
            if (!isDismissing && !isScreenFilled && spawnedCount < TARGET_SPAWN_ITEMS) {
                int spawnBatch = 2;
                for (int b = 0; b < spawnBatch && spawnedCount < TARGET_SPAWN_ITEMS; b++) {
                    PhysicsItem item = new PhysicsItem();
                    item.isDevice = !deviceBitmaps.isEmpty() && (rand.nextInt(3) == 0 || spawnedCount % 3 == 0);
                    if (item.isDevice) {
                        int idx = rand.nextInt(deviceBitmaps.size());
                        item.bitmap = deviceBitmaps.get(idx);
                        item.silhouette = deviceSilhouettes.get(idx);
                        item.scale = 0.28f + rand.nextFloat() * 0.12f;
                    } else {
                        item.shapeId = MD3_FALLING_SHAPES[rand.nextInt(MD3_FALLING_SHAPES.length)];
                        item.color = themeColors[rand.nextInt(themeColors.length)];
                        item.baseWidth = AndroidUtilities.dp(34 + rand.nextInt(26));
                        item.baseHeight = item.shapeId == Md3AvatarShapes.SHAPE_PILL ? item.baseWidth * 1.75f : item.baseWidth;
                        item.scale = 1.0f;
                    }

                    item.x = AndroidUtilities.dp(24) + rand.nextFloat() * (width - AndroidUtilities.dp(48));
                    item.y = -AndroidUtilities.dp(40) - rand.nextFloat() * AndroidUtilities.dp(70);
                    item.vx = (rand.nextFloat() - 0.5f) * AndroidUtilities.dp(2.0f);
                    item.vy = AndroidUtilities.dp(4.5f + rand.nextFloat() * 4.5f);
                    item.rotation = rand.nextFloat() * 360f;
                    item.vRot = (rand.nextFloat() - 0.5f) * 3f; // gentle initial rotation
                    item.settled = false;
                    items.add(item);
                    spawnedCount++;
                }
            }

            // 2. Physics simulation step
            float gravity = isDismissing ? AndroidUtilities.dp(1.5f) : AndroidUtilities.dp(0.52f);
            float colWidth = width / (float) COLUMNS_COUNT;
            boolean anyImpact = false;

            for (PhysicsItem item : items) {
                if (!item.settled) {
                    item.x += item.vx;
                    item.y += item.vy;
                    item.vy += gravity;
                    item.rotation += item.vRot;

                    // Strong rotational and horizontal damping to avoid spinning wildly
                    item.vRot *= 0.92f;
                    item.vx *= 0.96f;

                    float r = getItemRadius(item);

                    // Left/Right screen wall boundaries
                    if (item.x - r < 0) {
                        item.x = r;
                        item.vx = -item.vx * 0.35f;
                    } else if (item.x + r > width) {
                        item.x = width - r;
                        item.vx = -item.vx * 0.35f;
                    }

                    // Floor collision (when floor is active)
                    if (!floorDisabled && columnFloors != null) {
                        int col = Math.max(0, Math.min(COLUMNS_COUNT - 1, (int) (item.x / colWidth)));
                        float floorY = columnFloors[col];

                        if (item.y + r >= floorY) {
                            item.y = floorY - r;
                            item.vy = -item.vy * 0.22f;
                            item.vx *= 0.55f;
                            item.vRot *= 0.50f;
                            anyImpact = true;

                            if (Math.abs(item.vy) < AndroidUtilities.dp(1.2f)) {
                                item.settled = true;
                                item.vx = 0f;
                                item.vy = 0f;
                                item.vRot = 0f;

                                float addedHeight = r * 1.30f;
                                columnFloors[col] = Math.max(AndroidUtilities.dp(30), columnFloors[col] - addedHeight);
                                if (col > 0) {
                                    columnFloors[col - 1] = Math.max(AndroidUtilities.dp(30), columnFloors[col - 1] - addedHeight * 0.40f);
                                }
                                if (col < COLUMNS_COUNT - 1) {
                                    columnFloors[col + 1] = Math.max(AndroidUtilities.dp(30), columnFloors[col + 1] - addedHeight * 0.40f);
                                }
                            }
                        }
                    }
                } else {
                    // Settled items have strictly zero angular velocity
                    item.vRot = 0f;
                    item.vx = 0f;
                    item.vy = 0f;
                }
            }

            // 3. Stiff pairwise inter-item collision separation (resolves overlaps completely)
            if (!floorDisabled) {
                int count = items.size();
                for (int iter = 0; iter < 3; iter++) {
                    for (int i = 0; i < count; i++) {
                        PhysicsItem a = items.get(i);
                        float rA = getItemRadius(a);

                        for (int j = i + 1; j < count; j++) {
                            PhysicsItem b = items.get(j);
                            float rB = getItemRadius(b);

                            float dx = b.x - a.x;
                            float dy = b.y - a.y;
                            float distSq = dx * dx + dy * dy;
                            float minDist = rA + rB;

                            if (distSq < minDist * minDist && distSq > 0.0001f) {
                                float dist = (float) Math.sqrt(distSq);
                                float overlap = minDist - dist;
                                float nx = dx / dist;
                                float ny = dy / dist;

                                // Positional separation so items NEVER stay overlapping
                                float pushA = a.settled ? (b.settled ? 0.5f : 0.0f) : (b.settled ? 1.0f : 0.5f);
                                float pushB = b.settled ? (a.settled ? 0.5f : 0.0f) : (a.settled ? 1.0f : 0.5f);

                                a.x -= nx * overlap * pushA;
                                a.y -= ny * overlap * pushA;
                                b.x += nx * overlap * pushB;
                                b.y += ny * overlap * pushB;

                                // Gentle velocity exchange without excessive torque
                                float rvx = b.vx - a.vx;
                                float rvy = b.vy - a.vy;
                                float velAlongNormal = rvx * nx + rvy * ny;
                                if (velAlongNormal < 0) {
                                    float restitution = 0.22f;
                                    float impulse = -(1f + restitution) * velAlongNormal / 2f;
                                    if (!a.settled) {
                                        a.vx -= impulse * nx;
                                        a.vy -= impulse * ny;
                                    }
                                    if (!b.settled) {
                                        b.vx += impulse * nx;
                                        b.vy += impulse * ny;
                                    }
                                    anyImpact = true;
                                }
                            }
                        }
                    }
                }
            }

            if (anyImpact && !isDismissing) {
                playImpactHaptic();
            }

            // 4. Reliable 100% screen fill condition: trigger cookie when pile reaches the top
            if (!isScreenFilled && !isDismissing && columnFloors != null) {
                float avgFloor = 0;
                for (float f : columnFloors) {
                    avgFloor += f;
                }
                avgFloor /= COLUMNS_COUNT;
                // Guaranteed trigger when pile fills up towards top or enough items stacked
                if (avgFloor <= height * 0.26f || (spawnedCount >= 110 && allItemsSettled()) || spawnedCount >= TARGET_SPAWN_ITEMS) {
                    triggerCookieReveal();
                }
            }

            invalidate();
        });
        simulationAnimator.start();
    }

    private boolean allItemsSettled() {
        for (PhysicsItem item : items) {
            if (!item.settled) return false;
        }
        return true;
    }

    private float getItemRadius(PhysicsItem item) {
        if (item.isDevice && item.silhouette != null) {
            return item.silhouette.effectiveRadius * item.scale;
        }
        return (item.baseWidth / 2f) * item.scale;
    }

    private void triggerCookieReveal() {
        if (isScreenFilled) {
            return;
        }
        isScreenFilled = true;
        playCookieCelebrationHaptic();

        cookieAnimator = ValueAnimator.ofFloat(0f, 1f);
        cookieAnimator.setDuration(950L);
        cookieAnimator.setInterpolator(new OvershootInterpolator(2.4f));
        cookieAnimator.addUpdateListener(anim -> {
            cookieProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        cookieAnimator.start();
    }

    private int[] getThemeColors() {
        int primary = Theme.getColor(Theme.key_featuredStickers_addButton);
        int secondary = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
        int outBubble = Theme.getColor(Theme.key_chat_outBubble);
        int inBubble = Theme.getColor(Theme.key_chat_inBubble);
        int switchTrack = Theme.getColor(Theme.key_switchTrackChecked);
        return new int[]{
                primary,
                secondary,
                outBubble,
                inBubble,
                switchTrack,
                blendColor(primary, Color.WHITE, 0.25f),
                blendColor(primary, Color.BLACK, 0.2f),
                blendColor(switchTrack, Color.WHITE, 0.35f)
        };
    }

    private int blendColor(int color1, int color2, float ratio) {
        float iRatio = 1f - ratio;
        int a = (int) (Color.alpha(color1) * iRatio + Color.alpha(color2) * ratio);
        int r = (int) (Color.red(color1) * iRatio + Color.red(color2) * ratio);
        int g = (int) (Color.green(color1) * iRatio + Color.green(color2) * ratio);
        int b = (int) (Color.blue(color1) * iRatio + Color.blue(color2) * ratio);
        return Color.argb(a, r, g, b);
    }

    // --- Tactile Haptic Feedback ---

    private void playImpactHaptic() {
        long now = SystemClock.uptimeMillis();
        if (now - lastHapticTime < 42L) {
            return;
        }
        lastHapticTime = now;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(10, 100));
            } else {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        } catch (Throwable ignored) {}
    }

    private void playCookieCelebrationHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vibrator != null && vibrator.hasVibrator()) {
                long[] timings = new long[]{0, 35, 35, 55, 35, 90};
                int[] amplitudes = new int[]{0, 140, 0, 200, 0, 255};
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
            } else {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        } catch (Throwable ignored) {}
    }

    // --- Dismissal & Back Gesture Handling ---

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            startDismissAnimation();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isDismissing) {
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            startDismissAnimation();
            return true;
        }
        return true;
    }

    public void startDismissAnimation() {
        if (isDismissing) {
            return;
        }
        isDismissing = true;
        floorDisabled = true;

        // Release settled items so they fall freely through bottom
        for (PhysicsItem item : items) {
            item.settled = false;
            item.vy = Math.max(item.vy, AndroidUtilities.dp(6.0f));
        }

        // Animate cookie reverse-spin and scale-down
        dismissAnimator = ValueAnimator.ofFloat(0f, 1f);
        dismissAnimator.setDuration(460L);
        dismissAnimator.setInterpolator(new AccelerateInterpolator(1.6f));
        dismissAnimator.addUpdateListener(anim -> {
            dismissCookieProgress = (float) anim.getAnimatedValue();
            invalidate();
        });
        dismissAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                finishAndRemove();
            }
        });
        dismissAnimator.start();
    }

    private void finishAndRemove() {
        if (simulationAnimator != null) {
            simulationAnimator.cancel();
            simulationAnimator = null;
        }
        if (cookieAnimator != null) {
            cookieAnimator.cancel();
            cookieAnimator = null;
        }
        if (dismissAnimator != null) {
            dismissAnimator.cancel();
            dismissAnimator = null;
        }
        items.clear();
        deviceBitmaps.clear();
        deviceSilhouettes.clear();
        try {
            ViewGroup parent = (ViewGroup) getParent();
            if (parent != null) {
                parent.removeView(NothingPhoneBurstOverlay.this);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && getContext() instanceof Activity) {
            try {
                backCallback = this::startDismissAnimation;
                ((Activity) getContext()).getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                        backCallback
                );
            } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && backCallback != null && getContext() instanceof Activity) {
            try {
                ((Activity) getContext()).getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
            } catch (Throwable ignored) {}
            backCallback = null;
        }
        if (simulationAnimator != null) {
            simulationAnimator.cancel();
            simulationAnimator = null;
        }
        if (cookieAnimator != null) {
            cookieAnimator.cancel();
            cookieAnimator = null;
        }
        if (dismissAnimator != null) {
            dismissAnimator.cancel();
            dismissAnimator = null;
        }
        items.clear();
        deviceBitmaps.clear();
        deviceSilhouettes.clear();
    }

    // --- Drawing ---

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        // 1. Draw 100% opaque falling and settled MD3 shapes and devices
        for (PhysicsItem item : items) {
            if (item.y - getItemRadius(item) > height) {
                continue;
            }

            drawMatrix.reset();
            drawMatrix.postRotate(item.rotation);
            drawMatrix.postScale(item.scale, item.scale);
            drawMatrix.postTranslate(item.x, item.y);

            if (item.isDevice && item.bitmap != null && !item.bitmap.isRecycled()) {
                bitmapPaint.setAlpha(255);
                drawMatrix.preTranslate(-item.bitmap.getWidth() / 2f, -item.bitmap.getHeight() / 2f);
                canvas.drawBitmap(item.bitmap, drawMatrix, bitmapPaint);
            } else {
                shapePaint.setColor(item.color);
                shapePaint.setAlpha(255); // 100% opaque
                canvas.save();
                canvas.concat(drawMatrix);
                drawMd3Shape(canvas, item);
                canvas.restore();
            }
        }

        // 2. Draw animated 2/3-screen MD3 cookie with prominent MoltenGram logo
        if (isScreenFilled && (cookieProgress > 0.01f || isDismissing)) {
            drawMd3Cookie(canvas, width, height);
        }
    }

    private void drawMd3Shape(Canvas canvas, PhysicsItem item) {
        Path path = Md3AvatarShapes.getShapePath(item.shapeId, item.baseWidth, item.baseHeight);
        if (path != null) {
            canvas.save();
            canvas.translate(-item.baseWidth / 2f, -item.baseHeight / 2f);
            canvas.drawPath(path, shapePaint);
            canvas.restore();
        }
    }

    private void drawMd3Cookie(Canvas canvas, int width, int height) {
        float cx = width / 2f;
        float cy = height / 2f;

        // Exactly 2/3 of screen width
        float cookieDiameter = width * (2f / 3f);
        float cookieRadius = cookieDiameter / 2f;

        float scale;
        float rotation;

        if (isDismissing) {
            // Reverse spin and shrink down to 0
            scale = Math.max(0f, 1f - dismissCookieProgress);
            rotation = -280f * dismissCookieProgress;
        } else {
            // Bouncy entrance spin
            scale = cookieProgress;
            rotation = -180f * (1f - cookieProgress);
        }

        if (scale <= 0.001f) {
            return;
        }

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation);
        canvas.scale(scale, scale);

        // Official MD3 12-sided scalloped cookie shape with NO border/frame
        Path cookiePath = Md3AvatarShapes.getShapePath(Md3AvatarShapes.SHAPE_COOKIE_12, cookieDiameter, cookieDiameter);

        int primaryColor = Theme.getColor(Theme.key_featuredStickers_addButton);
        int surfaceColor = Theme.getColor(Theme.key_windowBackgroundWhite);

        cookiePaint.setColor(primaryColor);
        cookiePaint.setAlpha(255);

        canvas.save();
        canvas.translate(-cookieRadius, -cookieRadius);
        canvas.drawPath(cookiePath, cookiePaint);
        canvas.restore();

        // Large prominent vector MoltenGram logo inside cookie
        if (logoDrawable != null) {
            int logoSize = (int) (cookieDiameter * 0.58f);
            logoDrawable.setBounds(-logoSize / 2, -logoSize / 2, logoSize / 2, logoSize / 2);
            logoDrawable.setTint(surfaceColor != 0 ? surfaceColor : Color.WHITE);
            logoDrawable.setAlpha(255);
            logoDrawable.draw(canvas);
        }

        canvas.restore();
    }
}
