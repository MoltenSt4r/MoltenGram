package org.telegram.ui.Components.voip;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.Md3AvatarShapes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class ImageWithWavesView extends FrameLayout {
    private final AvatarWavesDrawable avatarWavesDrawable;
    private final BackupImageView backupImageView;
    private AnimatorSet animatorSet;
    private ValueAnimator shapeRotationAnimator;
    private float shapeRotationDeg = 0f;
    private final Matrix shapeRotateMatrix = new Matrix();
    private final Path rotatedShapePath = new Path();
    private boolean isConnectedCalled;
    private boolean isMuted;
    private final boolean allowAnimations;
    private int shapeId = Md3AvatarShapes.SHAPE_SCALLOP;
    private Path shapePath;
    private float lastShapeW = -1;
    private float lastShapeH = -1;
    private float lastLeft = -1;
    private float lastTop = -1;

    public ImageWithWavesView(Context context) {
        super(context);
        int userShape = AppearanceConfig.getAvatarShape(Md3AvatarShapes.PEER_TYPE_USER);
        shapeId = userShape > 0 ? userShape : Md3AvatarShapes.SHAPE_SCALLOP;
        avatarWavesDrawable = new AvatarWavesDrawable(AndroidUtilities.dp(104), AndroidUtilities.dp(111), AndroidUtilities.dp(12), 8);
        avatarWavesDrawable.setAmplitude(0f);
        avatarWavesDrawable.setShowWaves(false, this);
        backupImageView = new BackupImageView(context);
        if (shapeId >= 0) {
            backupImageView.setRoundRadius(0);
        }
        addView(backupImageView, LayoutHelper.createFrame(196, 196, Gravity.CENTER));
        setWillNotDraw(false);
        allowAnimations = LiteMode.isEnabled(LiteMode.FLAG_CALLS_ANIMATIONS);
        startShapeRotation();
        setClipChildren(false);
    }

    private void startShapeRotation() {
        if (!allowAnimations) {
            return;
        }
        if (shapeRotationAnimator != null) {
            shapeRotationAnimator.cancel();
        }
        shapeRotationAnimator = ValueAnimator.ofFloat(0f, 360f);
        shapeRotationAnimator.setDuration(18000);
        shapeRotationAnimator.setInterpolator(new LinearInterpolator());
        shapeRotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
        shapeRotationAnimator.setRepeatMode(ValueAnimator.RESTART);
        shapeRotationAnimator.addUpdateListener(anim -> {
            shapeRotationDeg = (Float) anim.getAnimatedValue();
            invalidate();
        });
        shapeRotationAnimator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (shapeRotationAnimator != null) {
            shapeRotationAnimator.cancel();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (allowAnimations && (shapeRotationAnimator == null || !shapeRotationAnimator.isRunning())) {
            startShapeRotation();
        }
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, String ext, Drawable thumb, Object parentObject) {
        backupImageView.setImage(imageLocation, imageFilter, ext, thumb, parentObject);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, Drawable thumb, Object parentObject) {
        backupImageView.setImage(imageLocation, imageFilter, thumb, parentObject);
    }

    public void setRoundRadius(int value) {
        if (shapeId >= 0) {
            backupImageView.setRoundRadius(0);
        } else {
            backupImageView.setRoundRadius(value);
        }
    }

    public void setShapeId(int shapeId) {
        if (this.shapeId != shapeId) {
            this.shapeId = shapeId;
            this.shapePath = null;
            if (shapeId >= 0 && backupImageView != null) {
                backupImageView.setRoundRadius(0);
            }
            invalidate();
        }
    }

    public int getShapeId() {
        return shapeId;
    }

    public void setPeer(TLRPC.User user) {
        if (user != null) {
            int peerType = UserObject.isUserSelf(user) ? Md3AvatarShapes.PEER_TYPE_SELF :
                    user.bot ? Md3AvatarShapes.PEER_TYPE_BOT : Md3AvatarShapes.PEER_TYPE_USER;
            int peerShape = AppearanceConfig.getAvatarShape(peerType);
            setShapeId(peerShape > 0 ? peerShape : Md3AvatarShapes.SHAPE_SCALLOP);
        }
    }

    public BackupImageView getBackupImageView() {
        return backupImageView;
    }

    public void setShowWaves(boolean showWaves) {
        avatarWavesDrawable.setShowWaves(false, this);
    }

    public void setMute(boolean isMuted, boolean isFast) {
        if (this.isMuted != isMuted) {
            this.isMuted = isMuted;
            avatarWavesDrawable.setMuteToStatic(isMuted, isFast, this);
        }
    }

    public void setAmplitude(double value) {
        avatarWavesDrawable.setAmplitude(0);
    }

    public void onConnected() {
        if (isConnectedCalled) {
            return;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        isConnectedCalled = true;
    }

    public void onNeedRating() {
        setShowWaves(false);
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        if (shapeRotationAnimator != null) {
            shapeRotationAnimator.cancel();
        }
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, View.ALPHA, this.getAlpha(), 1f),
                ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, this.getTranslationY(), -(float) AndroidUtilities.dp(24)),
                ObjectAnimator.ofFloat(this, View.SCALE_X, this.getScaleX(), 0.9f, 1f),
                ObjectAnimator.ofFloat(this, View.SCALE_Y, this.getScaleY(), 0.9f, 1f)
        );
        animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animatorSet.setDuration(300);
        animatorSet.setStartDelay(250);
        animatorSet.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (shapeId >= 0 && backupImageView != null && backupImageView.getMeasuredWidth() > 0 && backupImageView.getMeasuredHeight() > 0) {
            float left = backupImageView.getLeft();
            float top = backupImageView.getTop();
            float w = backupImageView.getMeasuredWidth();
            float h = backupImageView.getMeasuredHeight();
            if (shapePath == null || lastShapeW != w || lastShapeH != h || lastLeft != left || lastTop != top) {
                lastShapeW = w;
                lastShapeH = h;
                lastLeft = left;
                lastTop = top;
                shapePath = Md3AvatarShapes.getShapePath(shapeId, left, top, w, h);
            }
            if (shapePath != null) {
                canvas.save();
                if (shapeRotationDeg != 0f) {
                    float cx = left + w / 2f;
                    float cy = top + h / 2f;
                    shapeRotateMatrix.reset();
                    shapeRotateMatrix.setRotate(shapeRotationDeg, cx, cy);
                    rotatedShapePath.reset();
                    shapePath.transform(shapeRotateMatrix, rotatedShapePath);
                    canvas.clipPath(rotatedShapePath);
                } else {
                    canvas.clipPath(shapePath);
                }
                super.dispatchDraw(canvas);
                canvas.restore();
                return;
            }
        }
        super.dispatchDraw(canvas);
    }

    public static class AvatarWavesDrawable {

        float amplitude;
        float animateToAmplitude;
        float animateAmplitudeDiff;
        float wavesEnter = 0f;
        boolean showWaves;

        private final VoipBlobDrawable blobDrawable;
        private final VoipBlobDrawable blobDrawable2;

        public boolean muteToStatic = false;
        public float muteToStaticProgress = 1f;

        private ValueAnimator animator;
        private int muteToStaticInvalidationCount;

        public AvatarWavesDrawable(int minRadius, int maxRadius, int diff, int n) {
            blobDrawable = new VoipBlobDrawable(n - 1);
            blobDrawable2 = new VoipBlobDrawable(n);
            blobDrawable.minRadius = minRadius;
            blobDrawable.maxRadius = maxRadius;
            blobDrawable2.minRadius = minRadius - diff;
            blobDrawable2.maxRadius = maxRadius - diff;
            blobDrawable.generateBlob();
            blobDrawable2.generateBlob();
            int waveColor = Theme.getColor(Theme.key_chats_actionBackground);
            blobDrawable.paint.setColor(waveColor);
            blobDrawable.paint.setAlpha(30);
            blobDrawable2.paint.setColor(waveColor);
            blobDrawable2.paint.setAlpha(55);
        }

        public void update() {
            if (animateToAmplitude != amplitude) {
                amplitude += animateAmplitudeDiff * 16;
                if (animateAmplitudeDiff > 0) {
                    if (amplitude > animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                } else {
                    if (amplitude < animateToAmplitude) {
                        amplitude = animateToAmplitude;
                    }
                }
            }

            if (showWaves && wavesEnter != 1f) {
                wavesEnter += 16 / 350f;
                if (wavesEnter > 1f) {
                    wavesEnter = 1f;
                }
            } else if (!showWaves && wavesEnter != 0) {
                wavesEnter -= 16 / 350f;
                if (wavesEnter < 0f) {
                    wavesEnter = 0f;
                }
            }
        }

        public void draw(Canvas canvas, float cx, float cy, View parentView) {
            float scaleBlob = 0.8f + 0.4f * amplitude;
            if (showWaves || wavesEnter != 0) {
                canvas.save();
                float wavesEnter = CubicBezierInterpolator.DEFAULT.getInterpolation(this.wavesEnter);

                canvas.scale(scaleBlob * wavesEnter, scaleBlob * wavesEnter, cx, cy);

                blobDrawable.update(amplitude, 1f, muteToStaticProgress);
                blobDrawable.draw(cx, cy, canvas, blobDrawable.paint);

                blobDrawable2.update(amplitude, 1f, muteToStaticProgress);
                blobDrawable2.draw(cx, cy, canvas, blobDrawable.paint);
                canvas.restore();
            }

            if (muteToStatic && muteToStaticInvalidationCount == 0) {
                return;
            }

            if (muteToStaticInvalidationCount != 0) {
                muteToStaticInvalidationCount--;
            }

            if (wavesEnter != 0) {
                parentView.invalidate();
            }
        }

        public void setShowWaves(boolean show, View parentView) {
            if (showWaves != show) {
                parentView.invalidate();
            }
            showWaves = show;
        }

        public void setAmplitude(double value) {
            float amplitude = (float) value / 80f;
            if (!showWaves) {
                amplitude = 0;
            }
            if (amplitude > 1f) {
                amplitude = 1f;
            } else if (amplitude < 0) {
                amplitude = 0;
            }
            animateToAmplitude = amplitude;
            animateAmplitudeDiff = (animateToAmplitude - this.amplitude) / 200;
        }

        public void setMuteToStatic(boolean mute, boolean isFast, View parentView) {
            if (muteToStatic != mute) {
                muteToStatic = mute;
                if (animator != null) {
                    animator.removeAllUpdateListeners();
                    animator.cancel();
                }
                if (mute) {
                    animator = ValueAnimator.ofFloat(muteToStaticProgress, 0f);
                    muteToStaticInvalidationCount = (int) (2000 / AndroidUtilities.screenRefreshTime);
                } else {
                    muteToStaticInvalidationCount = 0;
                    animator = ValueAnimator.ofFloat(muteToStaticProgress, 1f);
                }
                animator.addUpdateListener(a -> muteToStaticProgress = (float) a.getAnimatedValue());
                if (isFast) {
                    animator.setDuration(150);
                } else {
                    animator.setDuration(1000);
                }
                animator.start();
                parentView.invalidate();
            }
        }
    }
}
