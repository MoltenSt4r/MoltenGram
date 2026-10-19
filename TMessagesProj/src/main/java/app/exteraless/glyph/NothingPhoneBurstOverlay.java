package app.exteraless.glyph;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * On-screen particle explosion overlay for Nothing & CMF phone easter egg.
 * Spawns flying phone models of the user's detected device and glowing sparks.
 */
public class NothingPhoneBurstOverlay extends View {

    private static class Particle {
        Bitmap bitmap;
        float x;
        float y;
        float vx;
        float vy;
        float gravity;
        float rotation;
        float vRot;
        float scale;
        float alpha;
        boolean isSpark;
        int sparkColor;
        float sparkRadius;
    }

    private final List<Particle> particles = new ArrayList<>();
    private final List<Bitmap> loadedBitmaps = new ArrayList<>();
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix drawMatrix = new Matrix();
    private ValueAnimator animator;

    public NothingPhoneBurstOverlay(Context context) {
        super(context);
        sparkPaint.setStyle(Paint.Style.FILL);
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
            int width = getWidth();
            int height = getHeight();
            if (width <= 0 || height <= 0) {
                width = AndroidUtilities.displaySize.x;
                height = AndroidUtilities.displaySize.y;
            }

            // Load phone bitmaps
            if (drawableResIds != null && drawableResIds.length > 0) {
                for (int resId : drawableResIds) {
                    try {
                        Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId);
                        if (bmp != null) {
                            loadedBitmaps.add(bmp);
                        }
                    } catch (Throwable t) {
                        FileLog.e(t);
                    }
                }
            }

            Random rand = new Random();
            float originX = width * 0.5f;
            float originY = height * 0.45f;

            // 1. Phone model particles (35-40 items)
            if (!loadedBitmaps.isEmpty()) {
                int count = 36;
                for (int i = 0; i < count; i++) {
                    Particle p = new Particle();
                    p.isSpark = false;
                    p.bitmap = loadedBitmaps.get(rand.nextInt(loadedBitmaps.size()));
                    p.x = originX + (rand.nextFloat() - 0.5f) * AndroidUtilities.dp(60);
                    p.y = originY + (rand.nextFloat() - 0.5f) * AndroidUtilities.dp(60);

                    // Ejection angle: all 360 degrees, with upward bias
                    double angle = Math.toRadians((360.0 / count) * i + (rand.nextFloat() - 0.5f) * 20);
                    float speed = AndroidUtilities.dp(10 + rand.nextFloat() * 18);
                    p.vx = (float) (Math.cos(angle) * speed);
                    p.vy = (float) (Math.sin(angle) * speed) - AndroidUtilities.dp(8);
                    p.gravity = AndroidUtilities.dp(0.55f + rand.nextFloat() * 0.25f);

                    p.rotation = rand.nextFloat() * 360f;
                    p.vRot = (rand.nextFloat() - 0.5f) * 24f;
                    p.scale = 0.28f + rand.nextFloat() * 0.24f;
                    p.alpha = 1.0f;
                    particles.add(p);
                }
            }

            // 2. Glowing sparks / stardust particles (30 items)
            int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            int[] sparkColors = new int[]{0xFFFFFFFF, 0xFFFF0033, 0xFFFFCC00, accentColor};
            for (int i = 0; i < 30; i++) {
                Particle p = new Particle();
                p.isSpark = true;
                p.sparkColor = sparkColors[rand.nextInt(sparkColors.length)];
                p.sparkRadius = AndroidUtilities.dp(2f + rand.nextFloat() * 4f);
                p.x = originX + (rand.nextFloat() - 0.5f) * AndroidUtilities.dp(30);
                p.y = originY + (rand.nextFloat() - 0.5f) * AndroidUtilities.dp(30);

                double angle = rand.nextDouble() * Math.PI * 2;
                float speed = AndroidUtilities.dp(6 + rand.nextFloat() * 22);
                p.vx = (float) (Math.cos(angle) * speed);
                p.vy = (float) (Math.sin(angle) * speed) - AndroidUtilities.dp(5);
                p.gravity = AndroidUtilities.dp(0.35f + rand.nextFloat() * 0.2f);
                p.alpha = 1.0f;
                particles.add(p);
            }

            animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(2400L);
            animator.addUpdateListener(animation -> {
                float progress = animation.getAnimatedFraction();
                for (Particle p : particles) {
                    p.x += p.vx;
                    p.y += p.vy;
                    p.vy += p.gravity;
                    p.vx *= 0.985f;
                    p.rotation += p.vRot;

                    if (progress > 0.45f) {
                        p.alpha = Math.max(0f, 1f - (progress - 0.45f) / 0.55f);
                    } else {
                        p.alpha = 1.0f;
                    }
                }
                invalidate();
            });

            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void end(Animator animation) {
                    finishAndRemove();
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    finishAndRemove();
                }
            });

            animator.start();
        });
    }

    private void finishAndRemove() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        particles.clear();
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (particles.isEmpty()) {
            return;
        }

        for (Particle p : particles) {
            if (p.alpha <= 0.01f) {
                continue;
            }

            if (p.isSpark) {
                sparkPaint.setColor(p.sparkColor);
                sparkPaint.setAlpha((int) (255 * p.alpha));
                canvas.drawCircle(p.x, p.y, p.sparkRadius, sparkPaint);
            } else if (p.bitmap != null && !p.bitmap.isRecycled()) {
                bitmapPaint.setAlpha((int) (255 * p.alpha));
                drawMatrix.reset();
                int bw = p.bitmap.getWidth();
                int bh = p.bitmap.getHeight();
                drawMatrix.postTranslate(-bw / 2f, -bh / 2f);
                drawMatrix.postRotate(p.rotation);
                drawMatrix.postScale(p.scale, p.scale);
                drawMatrix.postTranslate(p.x, p.y);
                canvas.drawBitmap(p.bitmap, drawMatrix, bitmapPaint);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        particles.clear();
    }
}
