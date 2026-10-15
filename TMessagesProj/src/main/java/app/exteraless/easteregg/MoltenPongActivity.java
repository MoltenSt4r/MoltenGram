package app.exteraless.easteregg;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Пасхалка: Пинг-понг MoltenPong против бота на очки со звёздным логотипом MoltenGram.
 * Открывается при 10 тапах по версии в настройках.
 */
public class MoltenPongActivity extends BaseFragment {

    private PongView pongView;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("MoltenPong");
        actionBar.setSubtitle("MoltenGram vs Bot");

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.msg_retry);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    if (pongView != null) {
                        pongView.resetMatch();
                    }
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(0xFF0F141C); // Глубокий премиальный графит/тёмный космос

        pongView = new PongView(context);
        frameLayout.addView(pongView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        fragmentView = frameLayout;
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (pongView != null) {
            pongView.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (pongView != null) {
            pongView.pause();
        }
    }

    public static class PongView extends View {

        private static final int STATE_READY = 0;
        private static final int STATE_PLAYING = 1;
        private static final int STATE_POINT_SCORED = 2;

        private int state = STATE_READY;

        private final Paint tableLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint netPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint playerPaddlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint botPaddlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ballPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ballGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint scorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private Bitmap logoBitmap;
        private final Rect logoSrcRect = new Rect();
        private final RectF logoDstRect = new RectF();

        private float paddleWidth;
        private float paddleHeight;
        private float paddleRadius;
        private float ballRadius;

        private float playerX;
        private float playerY;
        private float botX;
        private float botY;

        private float ballX;
        private float ballY;
        private float ballVx;
        private float ballVy;
        private float ballBaseSpeed;

        private int playerScore = 0;
        private int botScore = 0;
        private int rallyCount = 0;
        private int highScore = 0;

        private long lastFrameTime = 0;
        private boolean isRunning = false;
        private String statusText = "Коснитесь, чтобы подать мяч";

        private final SharedPreferences prefs;

        public PongView(Context context) {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);

            prefs = context.getSharedPreferences("molten_pong", Context.MODE_PRIVATE);
            highScore = prefs.getInt("high_score", 0);

            initPaints();
            loadLogo(context);
        }

        private void initPaints() {
            tableLinePaint.setColor(0x33FFFFFF);
            tableLinePaint.setStyle(Paint.Style.STROKE);
            tableLinePaint.setStrokeWidth(AndroidUtilities.dp(2));

            netPaint.setColor(0x44FFFFFF);
            netPaint.setStyle(Paint.Style.STROKE);
            netPaint.setStrokeWidth(AndroidUtilities.dp(2));
            netPaint.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dp(8), AndroidUtilities.dp(8)}, 0));

            // Material 3 Expressive MoltenStar Orange / Sunset
            playerPaddlePaint.setColor(0xFFFF7A00);
            playerPaddlePaint.setStyle(Paint.Style.FILL);

            // Bot Cyan / Cool Slate
            botPaddlePaint.setColor(0xFF00B4D8);
            botPaddlePaint.setStyle(Paint.Style.FILL);

            ballPaint.setColor(0xFFFFFFFF);
            ballPaint.setStyle(Paint.Style.FILL);

            ballGlowPaint.setColor(0x44FF9100);
            ballGlowPaint.setStyle(Paint.Style.FILL);

            scorePaint.setColor(0x66FFFFFF);
            scorePaint.setTextSize(AndroidUtilities.dp(48));
            scorePaint.setTypeface(AndroidUtilities.bold());
            scorePaint.setTextAlign(Paint.Align.CENTER);

            hintPaint.setColor(0xAAFFFFFF);
            hintPaint.setTextSize(AndroidUtilities.dp(16));
            hintPaint.setTypeface(AndroidUtilities.bold());
            hintPaint.setTextAlign(Paint.Align.CENTER);

            logoPaint.setAlpha(55); // Элегантный водяной знак
        }

        private void loadLogo(Context context) {
            try {
                logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher_moltengram_star_round);
                if (logoBitmap == null) {
                    logoBitmap = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher_moltengram_round);
                }
                if (logoBitmap != null) {
                    logoSrcRect.set(0, 0, logoBitmap.getWidth(), logoBitmap.getHeight());
                }
            } catch (Throwable ignored) {
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            paddleWidth = AndroidUtilities.dp(96);
            paddleHeight = AndroidUtilities.dp(16);
            paddleRadius = AndroidUtilities.dp(8);
            ballRadius = AndroidUtilities.dp(10);
            ballBaseSpeed = AndroidUtilities.dp(7.5f);

            playerX = w / 2f;
            playerY = h - AndroidUtilities.dp(80);

            botX = w / 2f;
            botY = AndroidUtilities.dp(80);

            float logoSize = Math.min(w, h) * 0.42f;
            logoDstRect.set(w / 2f - logoSize / 2f, h / 2f - logoSize / 2f,
                    w / 2f + logoSize / 2f, h / 2f + logoSize / 2f);

            resetBall(true);
        }

        private void resetBall(boolean towardsPlayer) {
            int w = getWidth();
            int h = getHeight();
            ballX = w / 2f;
            ballY = h / 2f;
            rallyCount = 0;

            float angle = (float) ((Math.random() * 0.8f - 0.4f) * Math.PI); // Небольшой случайный угол
            float speed = ballBaseSpeed;
            ballVx = (float) (Math.sin(angle) * speed);
            ballVy = (float) (Math.cos(angle) * speed) * (towardsPlayer ? 1f : -1f);

            state = STATE_READY;
            statusText = "Коснитесь экрана для подачи";
            invalidate();
        }

        public void resetMatch() {
            playerScore = 0;
            botScore = 0;
            resetBall(true);
        }

        public void resume() {
            isRunning = true;
            lastFrameTime = System.nanoTime();
            invalidate();
        }

        public void pause() {
            isRunning = false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float touchX = event.getX();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (state == STATE_READY || state == STATE_POINT_SCORED) {
                        state = STATE_PLAYING;
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    playerX = Math.max(paddleWidth / 2f, Math.min(getWidth() - paddleWidth / 2f, touchX));
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    playerX = Math.max(paddleWidth / 2f, Math.min(getWidth() - paddleWidth / 2f, touchX));
                    invalidate();
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w == 0 || h == 0) return;

            // 1. Поле: Водяной знак логотипа MoltenStar по центру
            if (logoBitmap != null) {
                canvas.drawBitmap(logoBitmap, logoSrcRect, logoDstRect, logoPaint);
            }

            // 2. Сетка по центру и круг
            float centerY = h / 2f;
            canvas.drawLine(0, centerY, w, centerY, netPaint);
            canvas.drawCircle(w / 2f, centerY, AndroidUtilities.dp(56), tableLinePaint);

            // 3. Табло счёта
            canvas.drawText(String.valueOf(botScore), w / 2f, centerY - AndroidUtilities.dp(24), scorePaint);
            canvas.drawText(String.valueOf(playerScore), w / 2f, centerY + AndroidUtilities.dp(60), scorePaint);

            // 4. Ракетка бота (сверху)
            RectF botRect = new RectF(botX - paddleWidth / 2f, botY, botX + paddleWidth / 2f, botY + paddleHeight);
            canvas.drawRoundRect(botRect, paddleRadius, paddleRadius, botPaddlePaint);

            // 5. Ракетка игрока (снизу)
            RectF playerRect = new RectF(playerX - paddleWidth / 2f, playerY, playerX + paddleWidth / 2f, playerY + paddleHeight);
            canvas.drawRoundRect(playerRect, paddleRadius, paddleRadius, playerPaddlePaint);

            // 6. Мяч и свечение
            canvas.drawCircle(ballX, ballY, ballRadius + AndroidUtilities.dp(6), ballGlowPaint);
            canvas.drawCircle(ballX, ballY, ballRadius, ballPaint);

            // 7. Подсказка и рекорд
            if (state != STATE_PLAYING) {
                canvas.drawText(statusText, w / 2f, centerY + AndroidUtilities.dp(120), hintPaint);
            }
            if (highScore > 0) {
                String hsStr = "Рекорд: " + highScore + " • Ралли: " + rallyCount;
                Paint p = new Paint(hintPaint);
                p.setTextSize(AndroidUtilities.dp(13));
                p.setColor(0x88FFFFFF);
                canvas.drawText(hsStr, w / 2f, h - AndroidUtilities.dp(24), p);
            }

            // Игровой цикл физики
            if (isRunning && state == STATE_PLAYING) {
                updatePhysics(w, h, playerRect, botRect);
                postInvalidateOnAnimation();
            }
        }

        private void updatePhysics(int w, int h, RectF playerRect, RectF botRect) {
            long now = System.nanoTime();
            float dt = 1f;
            if (lastFrameTime > 0) {
                dt = Math.min(2.5f, (now - lastFrameTime) / 16_666_666f); // 60fps normalization
            }
            lastFrameTime = now;

            // Перемещение мяча
            ballX += ballVx * dt;
            ballY += ballVy * dt;

            // Отскок от боковых стен
            if (ballX - ballRadius <= 0) {
                ballX = ballRadius;
                ballVx = -ballVx;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            } else if (ballX + ballRadius >= w) {
                ballX = w - ballRadius;
                ballVx = -ballVx;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }

            // AI бота: плавное преследование мяча с реалистичной реакцией
            float botTargetX = ballX;
            float botDiff = botTargetX - botX;
            float botMaxSpeed = AndroidUtilities.dp(4.6f) * dt;
            if (Math.abs(botDiff) > botMaxSpeed) {
                botX += Math.signum(botDiff) * botMaxSpeed;
            } else {
                botX = botTargetX;
            }
            botX = Math.max(paddleWidth / 2f, Math.min(w - paddleWidth / 2f, botX));

            // Коллизия с ракеткой игрока
            if (ballVy > 0 && ballY + ballRadius >= playerY && ballY - ballRadius <= playerY + paddleHeight) {
                if (ballX >= playerX - paddleWidth / 2f - ballRadius && ballX <= playerX + paddleWidth / 2f + ballRadius) {
                    ballVy = -Math.abs(ballVy) * 1.03f; // Небольшое ускорение
                    float hitOffset = (ballX - playerX) / (paddleWidth / 2f); // от -1 до 1
                    ballVx += hitOffset * AndroidUtilities.dp(3.5f);
                    ballY = playerY - ballRadius - 1;
                    rallyCount++;
                    if (rallyCount > highScore) {
                        highScore = rallyCount;
                        prefs.edit().putInt("high_score", highScore).apply();
                    }
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }

            // Коллизия с ракеткой бота
            if (ballVy < 0 && ballY - ballRadius <= botY + paddleHeight && ballY + ballRadius >= botY) {
                if (ballX >= botX - paddleWidth / 2f - ballRadius && ballX <= botX + paddleWidth / 2f + ballRadius) {
                    ballVy = Math.abs(ballVy) * 1.03f;
                    float hitOffset = (ballX - botX) / (paddleWidth / 2f);
                    ballVx += hitOffset * AndroidUtilities.dp(3.2f);
                    ballY = botY + paddleHeight + ballRadius + 1;
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }

            // Гол игроку (бот забил)
            if (ballY - ballRadius > h) {
                botScore++;
                state = STATE_POINT_SCORED;
                statusText = "Очко боту! Нажмите для продолжения";
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                resetBall(true);
            }
            // Гол боту (игрок забил)
            else if (ballY + ballRadius < 0) {
                playerScore++;
                state = STATE_POINT_SCORED;
                statusText = "ГОООЛ! Вы забили! Нажмите для подачи";
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                resetBall(false);
            }
        }
    }
}
