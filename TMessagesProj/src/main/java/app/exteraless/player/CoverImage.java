package app.exteraless.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.Md3AvatarShapes;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

public class CoverImage extends FrameLayout {

    public interface Listener {
        void onSeed(MessageObject messageObject, int seed);
    }

    private final BackupImageView imageView;

    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final PlayerIcon note;
    private final int noteSizeDp;
    private int radius;
    private String currentKey;
    private MessageObject message;
    private int fallbackSeed;
    private Listener listener;

    private int shapeId = -1;
    private int fromShapeId = -1;
    private int toShapeId = -1;
    private float morphProgress = 1f;
    private ValueAnimator shapeMorphAnimator;
    private Path shapePath;
    private Path roundPath;
    private int lastW = -1;
    private int lastH = -1;
    private float defaultElevation = -1f;

    public CoverImage(Context context, int noteSizeDp) {
        super(context);
        this.noteSizeDp = noteSizeDp;
        setWillNotDraw(false);

        imageView = new BackupImageView(context);
        imageView.setFocusable(false);
        imageView.setClickable(false);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        note = PlayerIcon.stroke(PlayerIcon.NOTE, noteSizeDp);
        imageView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (set) {
                onBitmap(imageReceiver.getBitmap());
            }
        });
    }

    public void setShapeId(int shapeId) {
        setShapeId(shapeId, false);
    }

    public void setShapeId(int shapeId, boolean animated) {
        if (this.shapeId == shapeId && morphProgress >= 1f) {
            return;
        }
        if (shapeMorphAnimator != null) {
            shapeMorphAnimator.cancel();
            shapeMorphAnimator = null;
        }
        int oldShape = this.shapeId;
        this.shapeId = shapeId;
        this.shapePath = null;
        if (defaultElevation < 0) {
            defaultElevation = getElevation();
        }
        if (shapeId >= 0) {
            setRoundRadius(0);
            getImageReceiver().setRoundRadius(0);
            setElevation(0);
            setTranslationZ(0);
        } else {
            setRoundRadius(radius);
            getImageReceiver().setRoundRadius(radius);
            if (defaultElevation > 0) {
                setElevation(defaultElevation);
            }
        }
        if (animated && oldShape >= 0 && shapeId >= 0 && oldShape != shapeId) {
            fromShapeId = oldShape;
            toShapeId = shapeId;
            morphProgress = 0f;
            shapeMorphAnimator = ValueAnimator.ofFloat(0f, 1f);
            shapeMorphAnimator.setDuration(350);
            shapeMorphAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            shapeMorphAnimator.addUpdateListener(animation -> {
                morphProgress = (float) animation.getAnimatedValue();
                shapePath = null;
                invalidate();
            });
            shapeMorphAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    morphProgress = 1f;
                    shapeMorphAnimator = null;
                    shapePath = null;
                    invalidateOutline();
                    invalidate();
                }
            });
            shapeMorphAnimator.start();
        } else {
            morphProgress = 1f;
            fromShapeId = -1;
            toShapeId = -1;
        }
        invalidateOutline();
        invalidate();
    }

    public int getShapeId() {
        return shapeId;
    }

    public void setListener(int fallbackSeed, Listener listener) {
        this.fallbackSeed = fallbackSeed;
        this.listener = listener;
    }

    public void setRadius(int r) {
        radius = r;
        roundPath = null;
        if (shapeId < 0) {
            setRoundRadius(r);
        } else {
            setRoundRadius(0);
            getImageReceiver().setRoundRadius(0);
        }
        invalidate();
    }

    public void setColors(int background, int foreground) {
        placeholderPaint.setColor(background);
        note.setColor(foreground);
        invalidate();
    }

    public MessageObject getMessage() {
        return message;
    }

    public ImageReceiver getImageReceiver() {
        return imageView.getImageReceiver();
    }

    public Bitmap getBitmap() {
        return imageView.getImageReceiver().getBitmap();
    }

    public void setRoundRadius(int r) {
        imageView.setRoundRadius(r);
    }

    public void setImageDrawable(Drawable drawable) {
        imageView.setImageDrawable(drawable);
    }

    public void setImageBitmap(Bitmap bitmap) {
        imageView.setImageBitmap(bitmap);
    }

    public void setImage(ImageLocation imageLocation, String imageFilter, ImageLocation thumbLocation, String thumbFilter, String ext, int size, int cacheType, Object parentObject) {
        imageView.setImage(imageLocation, imageFilter, thumbLocation, thumbFilter, ext, size, cacheType, parentObject);
    }

    public void setImage(String path, String filter, Drawable thumb) {
        imageView.setImage(path, filter, thumb);
    }

    public void setMessage(MessageObject messageObject) {
        if (messageObject == null) {
            currentKey = null;
            message = null;
            imageView.setImageDrawable(null);
            return;
        }

        Bitmap file = PlayerArt.fileCover(messageObject);
        // Fallback for local files if not yet playing or AudioInfo not cached in MediaController
        if (file == null && messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
            try {
                File f = new File(messageObject.messageOwner.attachPath);
                if (f.exists()) {
                    AudioInfo info = AudioInfo.getAudioInfo(f);
                    if (info != null && info.getCover() != null) {
                        file = info.getCover();
                    }
                }
            } catch (Exception ignored) {
            }
        }

        String key = PlayerArt.key(messageObject) + (file != null ? ":f" : "");
        if (key != null && key.equals(currentKey)) {
            message = messageObject;
            return;
        }
        currentKey = key;
        message = messageObject;

        boolean hasCover = false;
        if (file != null) {
            imageView.setImageBitmap(file);
            onBitmap(file);
            hasCover = true;
        } else {
            ImageLocation full = PlayerArt.fullLocation(messageObject);
            ImageLocation thumb = PlayerArt.thumbLocation(messageObject);
            if (full != null) {
                imageView.setImage(full, null, thumb, null, null, 0, 1, messageObject);
                hasCover = true;
            } else if (thumb != null) {
                imageView.setImage(null, null, thumb, null, null, 0, 1, messageObject);
                hasCover = true;
            } else if (messageObject.messageOwner != null && !TextUtils.isEmpty(messageObject.messageOwner.message) &&
                    (messageObject.messageOwner.message.startsWith("http://") || messageObject.messageOwner.message.startsWith("https://"))) {
                imageView.setImage(messageObject.messageOwner.message, null, null);
                hasCover = true;
            } else {
                imageView.setImageDrawable(null);
            }
        }
    }

    public void release() {
        if (shapeMorphAnimator != null) {
            shapeMorphAnimator.cancel();
            shapeMorphAnimator = null;
        }
        morphProgress = 1f;
        fromShapeId = -1;
        toShapeId = -1;
        imageView.setImageDrawable(null);
    }

    private void onBitmap(Bitmap bitmap) {
        final MessageObject target = message;
        if (bitmap == null || target == null || listener == null) {
            return;
        }
        PlayerArt.requestSeed(target, bitmap, fallbackSeed, seed -> {
            if (target == message && listener != null) {
                listener.onSeed(target, seed);
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shapePath = null;
        roundPath = null;
        lastW = w;
        lastH = h;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.dispatchDraw(canvas);
            return;
        }

        final boolean hasShape = shapeId >= 0;
        final boolean hasClip = hasShape || radius > 0;
        if (hasClip) {
            canvas.save();
            if (hasShape) {
                if (shapePath == null || lastW != w || lastH != h) {
                    lastW = w;
                    lastH = h;
                    if (morphProgress < 1f && fromShapeId >= 0 && toShapeId >= 0) {
                        shapePath = Md3AvatarShapes.getMorphPath(fromShapeId, toShapeId, morphProgress, 0, 0, w, h);
                    } else {
                        shapePath = Md3AvatarShapes.getShapePath(shapeId, 0, 0, w, h);
                    }
                }
                if (shapePath != null) {
                    canvas.clipPath(shapePath);
                }
            } else {
                if (roundPath == null || lastW != w || lastH != h) {
                    lastW = w;
                    lastH = h;
                    roundPath = new Path();
                    rect.set(0, 0, w, h);
                    roundPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
                }
                if (roundPath != null) {
                    canvas.clipPath(roundPath);
                }
            }
        }

        if (!imageView.getImageReceiver().hasBitmapImage()) {
            if (hasShape && shapePath != null) {
                canvas.drawPath(shapePath, placeholderPaint);
            } else {
                rect.set(0, 0, w, h);
                canvas.drawRoundRect(rect, radius, radius, placeholderPaint);
            }
            note.setBounds(0, 0, w, h);
            note.draw(canvas);
        }

        super.dispatchDraw(canvas);

        if (hasClip) {
            canvas.restore();
        }
    }

    private float touchStartX, touchStartY;
    private boolean isSwiping;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!AppearanceConfig.playerSwipeTrack()) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                isSwiping = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                if (!isSwiping && Math.abs(dx) > AndroidUtilities.dp(16) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    isSwiping = true;
                }
                if (isSwiping) {
                    setTranslationX(dx * 0.45f);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isSwiping) {
                    float finalDx = event.getX() - touchStartX;
                    float slop = AndroidUtilities.dp(48);
                    if (finalDx < -slop) {
                        MediaController.getInstance().playNextMessage();
                    } else if (finalDx > slop) {
                        MediaController.getInstance().playPreviousMessage();
                    }
                    animate().translationX(0f).setDuration(220).setInterpolator(CubicBezierInterpolator.EASE_OUT).start();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }
        return super.onTouchEvent(event);
    }
}
