package app.exteraless.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.TextureView;
import android.widget.FrameLayout;

import app.exteraless.appearance.AppearanceConfig;
import app.exteraless.appearance.Md3AvatarShapes;
import app.exteraless.metrolist.MetrolistClient;
import app.exteraless.metrolist.SongRecMatcher;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;

public class CoverImage extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public interface Listener {
        void onSeed(MessageObject messageObject, int seed);
    }

    private final BackupImageView imageView;
    private final TextureView textureView;
    private VideoPlayer videoPlayer;
    private String currentVideoUrl;

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
    private Path shapePath;
    private Path roundPath;
    private int lastW = -1;
    private int lastH = -1;
    private float defaultElevation = -1f;

    private int videoWidth;
    private int videoHeight;

    public CoverImage(Context context, int noteSizeDp) {
        super(context);
        this.noteSizeDp = noteSizeDp;
        setWillNotDraw(false);

        imageView = new BackupImageView(context);
        imageView.setFocusable(false);
        imageView.setClickable(false);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        textureView = new TextureView(context);
        textureView.setFocusable(false);
        textureView.setClickable(false);
        textureView.setOpaque(false);
        textureView.setAlpha(0f);
        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        note = PlayerIcon.stroke(PlayerIcon.NOTE, noteSizeDp);
        imageView.getImageReceiver().setDelegate((imageReceiver, set, thumb, memCache) -> {
            if (set) {
                onBitmap(imageReceiver.getBitmap());
            }
        });
    }

    public void setShapeId(int shapeId) {
        if (this.shapeId != shapeId) {
            this.shapeId = shapeId;
            this.shapePath = null;
            if (defaultElevation < 0) {
                defaultElevation = getElevation();
            }
            if (shapeId >= 0) {
                setRoundRadius(0);
                getImageReceiver().setRoundRadius(0);
                setElevation(0);
            } else {
                setRoundRadius(radius);
                getImageReceiver().setRoundRadius(radius);
                if (defaultElevation > 0) {
                    setElevation(defaultElevation);
                }
            }
            invalidateOutline();
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
            releasePlayer();
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

        releasePlayer();

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

        // If video cover is enabled or static cover is missing, match via SongRec / Innertube
        if (noteSizeDp >= 60 && (AppearanceConfig.playerVideoCover() || !hasCover)) {
            final MessageObject reqMsg = messageObject;
            SongRecMatcher.getInstance().findVideoCover(reqMsg, new SongRecMatcher.MatchCallback() {
                @Override
                public void onMatched(String videoUrl, MetrolistClient.Track track) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (reqMsg != message) {
                            return;
                        }
                        if (!imageView.getImageReceiver().hasBitmapImage() && track != null && !TextUtils.isEmpty(track.thumbnailUrl)) {
                            imageView.setImage(track.thumbnailUrl, null, null);
                        }
                        if (AppearanceConfig.playerVideoCover() && !TextUtils.isEmpty(videoUrl)) {
                            playVideoCover(videoUrl);
                        }
                    });
                }

                @Override
                public void onNotFound() {
                }
            });
        }
    }

    private void playVideoCover(String videoUrl) {
        if (TextUtils.isEmpty(videoUrl)) {
            return;
        }
        if (videoPlayer != null && TextUtils.equals(currentVideoUrl, videoUrl)) {
            if (!MediaController.getInstance().isMessagePaused()) {
                videoPlayer.play();
            } else {
                videoPlayer.pause();
            }
            return;
        }

        releasePlayer();
        currentVideoUrl = videoUrl;

        try {
            videoPlayer = new VideoPlayer();
            videoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                }

                @Override
                public void onError(VideoPlayer player, Exception e) {
                    FileLog.e(e);
                    releasePlayer();
                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    videoWidth = width;
                    videoHeight = height;
                    updateTextureTransform();
                }

                @Override
                public void onRenderedFirstFrame() {
                    textureView.animate().alpha(1f).setDuration(250).start();
                }
            });
            videoPlayer.setTextureView(textureView);
            videoPlayer.setMute(true);
            videoPlayer.setVolume(0f);
            videoPlayer.setLooping(true);
            videoPlayer.preparePlayer(Uri.parse(videoUrl), "other");
            if (!MediaController.getInstance().isMessagePaused()) {
                videoPlayer.play();
            } else {
                videoPlayer.pause();
            }
        } catch (Exception e) {
            FileLog.e(e);
            releasePlayer();
        }
    }

    private void updateTextureTransform() {
        int viewW = getWidth();
        int viewH = getHeight();
        if (viewW <= 0 || viewH <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return;
        }
        float viewRatio = (float) viewW / (float) viewH;
        float videoRatio = (float) videoWidth / (float) videoHeight;
        float scaleX = 1f;
        float scaleY = 1f;

        if (videoRatio > viewRatio) {
            scaleX = videoRatio / viewRatio;
            scaleY = 1f;
        } else {
            scaleX = 1f;
            scaleY = viewRatio / videoRatio;
        }

        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f);
        textureView.setTransform(matrix);
    }

    public void release() {
        releasePlayer();
    }

    private void releasePlayer() {
        currentVideoUrl = null;
        if (videoPlayer != null) {
            videoPlayer.releasePlayer(true);
            videoPlayer = null;
        }
        if (textureView != null) {
            textureView.animate().cancel();
            textureView.setAlpha(0f);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        }
        releasePlayer();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingPlayStateChanged) {
            if (videoPlayer != null) {
                if (MediaController.getInstance().isMessagePaused()) {
                    videoPlayer.pause();
                } else {
                    videoPlayer.play();
                }
            }
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

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shapePath = null;
        roundPath = null;
        lastW = w;
        lastH = h;
        updateTextureTransform();
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
                    shapePath = Md3AvatarShapes.getShapePath(shapeId, 0, 0, w, h);
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

        if (!imageView.getImageReceiver().hasBitmapImage() && textureView.getAlpha() <= 0f) {
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
