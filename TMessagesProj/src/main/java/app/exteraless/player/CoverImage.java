package app.exteraless.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import app.exteraless.appearance.Md3AvatarShapes;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.Components.BackupImageView;

public class CoverImage extends BackupImageView {

    public interface Listener {
        void onSeed(MessageObject messageObject, int seed);
    }

    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final PlayerIcon note;
    private int radius;
    private String currentKey;
    private MessageObject message;
    private int fallbackSeed;
    private Listener listener;

    private int shapeId = -1;
    private Path shapePath;
    private int lastW = -1;
    private int lastH = -1;

    public CoverImage(Context context, int noteSizeDp) {
        super(context);
        note = PlayerIcon.stroke(PlayerIcon.NOTE, noteSizeDp);
        getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (set) {
                onBitmap(imageReceiver.getBitmap());
            }
        });
    }

    public void setShapeId(int shapeId) {
        if (this.shapeId != shapeId) {
            this.shapeId = shapeId;
            this.shapePath = null;
            if (shapeId >= 0) {
                setRoundRadius(0);
                getImageReceiver().setRoundRadius(0);
            } else {
                setRoundRadius(radius);
            }
            invalidate();
        }
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

    public void setMessage(MessageObject messageObject) {
        if (messageObject == null) {
            currentKey = null;
            message = null;
            setImageDrawable(null);
            return;
        }
        Bitmap file = PlayerArt.fileCover(messageObject);
        String key = PlayerArt.key(messageObject) + (file != null ? ":f" : "");
        if (key.equals(currentKey)) {
            return;
        }
        currentKey = key;
        message = messageObject;
        if (file != null) {
            setImageBitmap(file);
            onBitmap(file);
            return;
        }
        ImageLocation full = PlayerArt.fullLocation(messageObject);
        ImageLocation thumb = PlayerArt.thumbLocation(messageObject);
        if (full != null) {
            setImage(full, null, thumb, null, null, 0, 1, messageObject);
        } else if (thumb != null) {
            setImage(null, null, thumb, null, null, 0, 1, messageObject);
        } else {
            setImageDrawable(null);
        }
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

    public Bitmap getBitmap() {
        return getImageReceiver().getBitmap();
    }

    private float touchStartX, touchStartY;
    private boolean isSwiping;

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (!app.exteraless.appearance.AppearanceConfig.playerSwipeTrack()) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                touchStartX = event.getX();
                touchStartY = event.getY();
                isSwiping = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case android.view.MotionEvent.ACTION_MOVE:
                float dx = event.getX() - touchStartX;
                float dy = event.getY() - touchStartY;
                if (!isSwiping && Math.abs(dx) > org.telegram.messenger.AndroidUtilities.dp(16) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    isSwiping = true;
                }
                if (isSwiping) {
                    setTranslationX(dx * 0.45f);
                    return true;
                }
                break;
            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                if (isSwiping) {
                    float finalDx = event.getX() - touchStartX;
                    float slop = org.telegram.messenger.AndroidUtilities.dp(48);
                    if (finalDx < -slop) {
                        org.telegram.messenger.MediaController.getInstance().playNextMessage();
                    } else if (finalDx > slop) {
                        org.telegram.messenger.MediaController.getInstance().playPreviousMessage();
                    }
                    animate().translationX(0f).setDuration(220).setInterpolator(org.telegram.ui.Components.CubicBezierInterpolator.EASE_OUT).start();
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

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.onDraw(canvas);
            return;
        }

        final boolean hasShape = shapeId >= 0;
        if (hasShape) {
            getImageReceiver().setRoundRadius(0);
            if (shapePath == null || lastW != w || lastH != h) {
                lastW = w;
                lastH = h;
                shapePath = Md3AvatarShapes.getShapePath(shapeId, 0, 0, w, h);
            }
            canvas.save();
            canvas.clipPath(shapePath);
        }

        if (!getImageReceiver().hasBitmapImage()) {
            if (hasShape && shapePath != null) {
                canvas.drawPath(shapePath, placeholderPaint);
            } else {
                rect.set(0, 0, w, h);
                canvas.drawRoundRect(rect, radius, radius, placeholderPaint);
            }
            note.setBounds(0, 0, w, h);
            note.draw(canvas);
        }

        super.onDraw(canvas);

        if (hasShape) {
            canvas.restore();
        }
    }
}
