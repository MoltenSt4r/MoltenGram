package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.audioinfo.AudioInfo;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import app.exteraless.appearance.AppearanceConfig;

public class PlayerSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    public static PlayerSheet instance;

    private static final float[] SPEEDS = {1f, 1.2f, 1.5f, 1.7f, 2f, 0.5f};
    private static final CubicBezierInterpolator EMPHASIZED = new CubicBezierInterpolator(0.2, 0, 0, 1);
    private static final PathInterpolator BACK_GESTURE = new PathInterpolator(0.1f, 0.1f, 0f, 1f);

    private final LaunchActivity activity;
    private final int account;
    private final boolean dark;
    private final int fallbackSeed;
    private final FrameLayout root;
    private final PlayerLayout layout;
    private final FrameLayout header;
    private final ImageView collapseButton;
    private final ImageView moreButton;
    private final PlayerIcon collapseIcon = PlayerIcon.stroke(PlayerIcon.CHEVRON_DOWN, 24);
    private final PlayerIcon moreIcon = PlayerIcon.fill(PlayerIcon.MORE, 24);
    private final TextView headerLabel;
    private final TextView headerTitle;
    private final CoverImage cover;
    private final LinearLayout titleRow;
    private final TextView titleView;
    private final TextView artistView;
    private final MorphButton likeButton;
    private final PlayerIcon heartIcon = PlayerIcon.stroke(PlayerIcon.HEART, 24);
    private final LinearLayout lyricsPanel;
    private final CoverImage smallCover;
    private final TextView smallTitle;
    private final TextView smallArtist;
    private final LyricsView lyricsView;
    private final TextView sourceView;
    private ImageView translateButton;
    private Lyrics currentLyrics;
    private boolean isTranslatingLyrics;
    private final WavySeekBar seekBar;
    private final TextView bubble;
    private final GradientDrawable bubbleBg = new GradientDrawable();
    private final LinearLayout timeRow;
    private final TextView timeNow;
    private final TextView timeLeft;
    private final ControlsRow controls;
    private final LinearLayout group;
    private final MorphButton speedButton;
    private final MorphButton lyricsButton;
    private final MorphButton queueButton;
    private final Bulletin.Delegate bulletinDelegate = new Bulletin.Delegate() {
        @Override
        public int getBottomOffset(int tag) {
            return getBottomInset();
        }
    };

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF morphFrom = new RectF();
    private final RectF morphTo = new RectF();
    private final RectF morphCoverFrom = new RectF();
    private final RectF morphCoverTo = new RectF();
    private final RectF morphCoverBase = new RectF();
    private final RectF morphRect = new RectF();
    private final RectF morphCover = new RectF();
    private final Rect morphSrc = new Rect();
    private final Path morphPath = new Path();
    private final Paint morphPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private PlayerMiniView miniSource;
    private View morphCoverView;
    private float morphCoverBaseRadius;
    private boolean backPreview;
    private float backProgress;
    private int backDirection;
    private ValueAnimator backAnimator;
    private boolean skipMorph;
    private boolean closing;
    private boolean detached;
    private ValueAnimator morphAnimator;
    private float morphProgress = -1f;
    private float morphFromRadius;
    private float morphToRadius;
    private float morphCoverFromRadius;
    private float morphCoverToRadius;
    private int morphFromColor;
    private Bitmap morphSnapshot;
    private Bitmap morphCoverBitmap;
    private int morphBarsState = -1;
    private boolean underLightStatus;
    private boolean underLightNav;
    private final RectF backgroundRect = new RectF();
    private android.graphics.LinearGradient bgGradient;
    private int lastGradientSeed = -1;
    private Bitmap cachedBlurredArt;
    private String lastBlurredKey;
    private final Rect bgSrcRect = new Rect();
    private final Paint scrimPaint = new Paint();
    private final Path bgPath = new Path();
    private PlayerColors colors;
    private ValueAnimator colorAnimator;
    private MessageObject current;
    private String currentKey;
    private boolean lyricsMode;
    private float lyricsFraction;
    private ValueAnimator lyricsAnimator;
    private String lyricsKey;
    private boolean seeking;
    private float seekProgress;
    private Boolean lightBars;
    private boolean touchInLyrics;
    private float dragStartX;
    private float dragStartY;
    private boolean dragTracking;
    private boolean dragActive;
    private boolean coverAnimating;
    private final int touchSlop;

    public PlayerSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        Activity found = AndroidUtilities.findActivity(context);
        activity = found instanceof LaunchActivity ? (LaunchActivity) found : LaunchActivity.instance;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        occupyNavigationBar = true;
        drawNavigationBar = false;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        MessageObject playing = MediaController.getInstance().getPlayingMessageObject();
        account = playing != null ? playing.currentAccount : UserConfig.selectedAccount;
        currentAccount = account;
        dark = PlayerColors.isDark(resourcesProvider);
        fallbackSeed = PlayerColors.noCoverSeed(resourcesProvider);
        Integer seed = PlayerArt.cachedSeed(playing);
        if (AppearanceConfig.playerColorStyle() == 1) {
            seed = fallbackSeed;
        }
        colors = PlayerColors.fromSeed(seed != null ? seed : fallbackSeed, dark);

        root = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (morphProgress >= 0f || closing) {
                    return true;
                }
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    touchInLyrics = lyricsMode && lyricsView.getVisibility() == VISIBLE && hitInRoot(lyricsView, ev.getX(), ev.getY());
                }
                boolean result = super.dispatchTouchEvent(ev);
                if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) && touchInLyrics) {
                    touchInLyrics = false;
                    container.requestDisallowInterceptTouchEvent(false);
                }
                return result;
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                invalidate();
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (morphProgress < 0f) {
                    super.dispatchDraw(canvas);
                    return;
                }
                drawMorphBackground(canvas);
                super.dispatchDraw(canvas);
                drawMorphForeground(canvas);
            }

            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                if (morphProgress >= 0f) {
                    return;
                }
                float r = dp(28) * Math.max(0f, Math.min(1f, getTranslationY() / dp(56)));
                backgroundRect.set(0, 0, getWidth(), getHeight() + r);

                int bgStyle = AppearanceConfig.playerBackgroundStyle();
                if (bgStyle == 1 && cover != null && cover.getBitmap() != null) {
                    drawBlurredBackground(canvas, backgroundRect, r);
                } else if (bgStyle == 2) {
                    drawGradientBackground(canvas, backgroundRect, r);
                } else {
                    backgroundPaint.setShader(null);
                    backgroundPaint.setColor(colors.surface);
                    canvas.drawRoundRect(backgroundRect, r, r, backgroundPaint);
                }
            }

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                Bulletin.addDelegate(this, bulletinDelegate);
                if (AppearanceConfig.playerKeepScreenOn() && activity != null && activity.getWindow() != null) {
                    activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                Bulletin.removeDelegate(this);
                if (activity != null && activity.getWindow() != null) {
                    activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        };
        root.setWillNotDraw(false);
        containerView = root;

        layout = new PlayerLayout(context);
        root.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        header = new FrameLayout(context);
        collapseButton = new ImageView(context);
        collapseButton.setScaleType(ImageView.ScaleType.CENTER);
        collapseButton.setImageDrawable(collapseIcon);
        collapseButton.setContentDescription(getString(R.string.Close));
        collapseButton.setOnClickListener(v -> dismiss());
        header.addView(collapseButton, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        moreButton = new ImageView(context);
        moreButton.setScaleType(ImageView.ScaleType.CENTER);
        moreButton.setImageDrawable(moreIcon);
        moreButton.setContentDescription(getString(R.string.AccDescrMoreOptions));
        moreButton.setOnClickListener(this::showMenu);
        header.addView(moreButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        LinearLayout headerCenter = new LinearLayout(context);
        headerCenter.setOrientation(LinearLayout.VERTICAL);
        headerCenter.setGravity(Gravity.CENTER_HORIZONTAL);
        headerLabel = new TextView(context);
        headerLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        headerLabel.setLetterSpacing(0.08f);
        headerLabel.setAllCaps(true);
        headerLabel.setSingleLine(true);
        headerLabel.setEllipsize(TextUtils.TruncateAt.END);
        headerCenter.addView(headerLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        headerTitle = new TextView(context);
        headerTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        headerTitle.setTypeface(AndroidUtilities.bold());
        headerTitle.setSingleLine(true);
        headerTitle.setEllipsize(TextUtils.TruncateAt.END);
        headerCenter.addView(headerTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));
        header.addView(headerCenter, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 56, 0, 56, 0));
        layout.addView(header);

        cover = new CoverImage(context, 120);
        cover.setRadius(dp(28));
        cover.setListener(fallbackSeed, this::onSeed);
        cover.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                if (cover.getShapeId() >= 0) {
                    outline.setEmpty();
                } else {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(28));
                }
            }
        });
        cover.setElevation(cover.getShapeId() >= 0 ? 0 : dp(14));
        cover.setOnTouchListener(this::onCoverTouch);
        layout.addView(cover);

        titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(artistView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        titleRow.addView(titles, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        likeButton = new MorphButton(context, heartIcon);
        likeButton.setRadius(dp(24), false);
        likeButton.setOnClickListener(v -> toggleProfile());
        titleRow.addView(likeButton, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        layout.addView(titleRow);

        lyricsPanel = new LinearLayout(context);
        lyricsPanel.setOrientation(LinearLayout.VERTICAL);
        lyricsPanel.setVisibility(View.GONE);
        LinearLayout smallRow = new LinearLayout(context);
        smallRow.setOrientation(LinearLayout.HORIZONTAL);
        smallRow.setGravity(Gravity.CENTER_VERTICAL);
        smallCover = new CoverImage(context, 28);
        smallCover.setRadius(dp(16));
        smallRow.addView(smallCover, LayoutHelper.createLinear(56, 56));
        LinearLayout smallTitles = new LinearLayout(context);
        smallTitles.setOrientation(LinearLayout.VERTICAL);
        smallTitle = new TextView(context);
        smallTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        smallTitle.setTypeface(AndroidUtilities.bold());
        smallTitle.setSingleLine(true);
        smallTitle.setEllipsize(TextUtils.TruncateAt.END);
        smallTitles.addView(smallTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        smallArtist = new TextView(context);
        smallArtist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        smallArtist.setSingleLine(true);
        smallArtist.setEllipsize(TextUtils.TruncateAt.END);
        smallTitles.addView(smallArtist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        smallRow.addView(smallTitles, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 14, 0, 0, 0));
        translateButton = new ImageView(context);
        translateButton.setImageResource(R.drawable.ic_translate);
        translateButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        translateButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        translateButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextGray2), PorterDuff.Mode.SRC_IN));
        translateButton.setOnClickListener(v -> translateCurrentLyrics(true));
        smallRow.addView(translateButton, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        lyricsPanel.addView(smallRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        lyricsView = new LyricsView(context);
        lyricsView.setDelegate(new LyricsView.Delegate() {
            @Override
            public void onAction(int state) {
                if (state == LyricsView.STATE_OFFER) {
                    AppearanceConfig.lrclibAllowed.setConfigBool(true);
                }
                fetchLyrics();
            }

            @Override
            public void onSeek(long ms) {
                MessageObject mo = current;
                if (mo != null) {
                    MediaController.getInstance().seekToProgressMs(mo, ms);
                }
            }
        });
        lyricsPanel.addView(lyricsView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f, 0, 16, 0, 0));
        sourceView = new TextView(context);
        sourceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        sourceView.setSingleLine(true);
        lyricsPanel.addView(sourceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 16, 0, 8, 0, 0));
        layout.addView(lyricsPanel);

        seekBar = new WavySeekBar(context);
        seekBar.setContentDescription(getString(R.string.OEPlayerSeek));
        seekBar.setDelegate(new WavySeekBar.Delegate() {
            @Override
            public void onSeekStart() {
                seeking = true;
                bubble.animate().cancel();
                bubble.setVisibility(View.VISIBLE);
                bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
            }

            @Override
            public void onSeekMove(float progress) {
                seekProgress = progress;
                updateTimes();
                layoutBubble();
            }

            @Override
            public void onSeekEnd(float progress, boolean commit) {
                seeking = false;
                MessageObject mo = current;
                if (commit && mo != null) {
                    MediaController.getInstance().seekToProgress(mo, progress);
                    mo.audioProgress = progress;
                    mo.audioProgressSec = (int) (mo.getDuration() * progress);
                }
                bubble.animate().cancel();
                bubble.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(150).withEndAction(() -> bubble.setVisibility(View.GONE)).start();
                updateProgress();
            }
        });
        layout.addView(seekBar);

        timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeNow = new TextView(context);
        timeNow.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        timeNow.setFontFeatureSettings("tnum");
        timeRow.addView(timeNow, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        timeLeft = new TextView(context);
        timeLeft.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        timeLeft.setFontFeatureSettings("tnum");
        timeLeft.setGravity(Gravity.RIGHT);
        timeRow.addView(timeLeft, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        layout.addView(timeRow);

        controls = new ControlsRow(context);
        controls.shuffle.setOnClickListener(v -> {
            MediaController.getInstance().setPlaybackOrderType(SharedConfig.shuffleMusic ? 0 : 2);
            updateModes(true);
        });
        controls.repeat.setOnClickListener(v -> {
            int mode = SharedConfig.repeatMode;
            SharedConfig.setRepeatMode(mode == 0 ? 1 : mode == 1 ? 2 : 0);
            updateModes(true);
        });
        controls.prev.setOnClickListener(v -> MediaController.getInstance().playPreviousMessage());
        controls.next.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        controls.play.setOnClickListener(v -> togglePlay());
        controls.shuffle.setContentDescription(getString(R.string.ShuffleList));
        controls.prev.setContentDescription(getString(R.string.AccDescrPrevious));
        controls.next.setContentDescription(getString(R.string.Next));
        layout.addView(controls);

        group = new LinearLayout(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        speedButton = new MorphButton(context, PlayerIcon.stroke(PlayerIcon.SPEED, 20));
        speedButton.setRadius(dp(26), dp(8), false);
        speedButton.setOnClickListener(v -> showSpeedSliderDialog());
        group.addView(speedButton, LayoutHelper.createLinear(0, 52, 1f));
        lyricsButton = new MorphButton(context, PlayerIcon.stroke(PlayerIcon.LYRICS, 20));
        lyricsButton.setText(getString(R.string.OEPlayerLyrics));
        lyricsButton.setRadius(dp(8), false);
        lyricsButton.setOnClickListener(v -> setLyricsMode(!lyricsMode, true));
        group.addView(lyricsButton, LayoutHelper.createLinear(0, 52, 1f, 4, 0, 4, 0));
        queueButton = new MorphButton(context, PlayerIcon.stroke(PlayerIcon.QUEUE, 20));
        queueButton.setText(getString(R.string.OEPlayerQueue));
        queueButton.setRadius(dp(8), dp(26), false);
        queueButton.setOnClickListener(v -> openQueue());
        group.addView(queueButton, LayoutHelper.createLinear(0, 52, 1f));
        layout.addView(group);

        bubble = new TextView(context);
        bubble.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        bubble.setTypeface(AndroidUtilities.bold());
        bubble.setFontFeatureSettings("tnum");
        bubble.setPadding(dp(12), dp(6), dp(12), dp(6));
        bubbleBg.setCornerRadius(dp(16));
        bubble.setBackground(bubbleBg);
        bubble.setVisibility(View.GONE);
        bubble.setAlpha(0f);
        layout.addView(bubble);

        applyColors(colors);
        bind(playing, false);
    }

    private boolean hitInRoot(View view, float x, float y) {
        float left = 0;
        float top = 0;
        View v = view;
        while (v != null && v != root) {
            left += v.getLeft() + v.getTranslationX();
            top += v.getTop() + v.getTranslationY();
            if (!(v.getParent() instanceof View)) {
                return false;
            }
            v = (View) v.getParent();
        }
        return v == root && x >= left && x < left + view.getWidth() && y >= top && y < top + view.getHeight();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        if (morphProgress >= 0f || closing) {
            return false;
        }
        return !touchInLyrics || !lyricsView.canScrollUp();
    }

    @Override
    public void show() {
        super.show();
        instance = this;
        NotificationCenter nc = NotificationCenter.getInstance(account);
        nc.addObserver(this, NotificationCenter.messagePlayingDidReset);
        nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
        nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        nc.addObserver(this, NotificationCenter.fileLoaded);
        nc.addObserver(this, NotificationCenter.audioInfoLoaded);
        nc.addObserver(this, NotificationCenter.musicIdsLoaded);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.messagePlayingSpeedChanged);
        applySystemBars(true);
    }

    public void setTransitionSource(PlayerMiniView source) {
        miniSource = source;
    }

    public void dismissImmediately() {
        skipMorph = true;
        dismiss();
    }

    @Override
    public void dismiss() {
        if (closing) {
            return;
        }
        if (!skipMorph && miniSource != null && !isDismissed()) {
            if (morphProgress >= 0f) {
                boolean preview = backPreview;
                closing = true;
                detach();
                cancelBackAnimator();
                if (preview) {
                    backPreview = false;
                    morphCoverView.setVisibility(View.INVISIBLE);
                }
                startMorph(morphProgress, 0f, preview ? 380 : 300, this::finishMorphClose);
                return;
            }
            float ty = root.getTranslationY();
            root.setTranslationY(0);
            if (prepareMorph(true)) {
                closing = true;
                detach();
                cancelSheetAnimation();
                morphTo.set(0, ty, root.getWidth(), root.getHeight() + ty);
                morphToRadius = dp(28) * clamp01(ty / dp(56));
                morphCoverTo.offset(0, ty);
                startMorph(1f, 0f, 380, this::finishMorphClose);
                return;
            }
            root.setTranslationY(ty);
        }
        detach();
        if (morphAnimator != null) {
            morphAnimator.removeAllListeners();
            morphAnimator.cancel();
            morphAnimator = null;
        }
        if (morphProgress >= 0f) {
            cancelBackAnimator();
            backPreview = false;
            restoreMorphCover();
            clearMorph();
        }
        if (miniSource != null) {
            miniSource.setTransitionHidden(false);
        }
        super.dismiss();
    }

    private void finishMorphClose() {
        if (miniSource != null) {
            miniSource.setTransitionHidden(false);
        }
        root.setVisibility(View.INVISIBLE);
        backDrawable.setAlpha(0);
        skipDismissAnimation();
        super.dismiss();
    }

    @Override
    public void dismissInternal() {
        if (miniSource != null) {
            miniSource.setTransitionHidden(false);
        }
        super.dismissInternal();
    }

    @Override
    protected boolean onCustomOpenAnimation() {
        root.setTranslationY(0);
        if (!prepareMorph(true)) {
            return false;
        }
        morphTo.set(0, 0, root.getWidth(), root.getHeight());
        morphToRadius = 0;
        startMorph(0f, 1f, 420, () -> {
            clearMorph();
            restoreMorphCover();
            cover.setTranslationZ(-cover.getElevation());
            cover.animate().translationZ(0).setDuration(250).start();
            morphBarsState = -1;
            applySystemBars(true);
            onOpenAnimationEnd();
            if (delegate != null) {
                delegate.onOpenAnimationEnd();
            }
        });
        return true;
    }

    @Override
    protected boolean onCustomBackStarted(int swipeDirection) {
        if (miniSource == null || skipMorph) {
            return false;
        }
        if (closing || (morphProgress >= 0f && !backPreview)) {
            return true;
        }
        if (backPreview) {
            cancelBackAnimator();
        } else if (root.getTranslationY() != 0f || !prepareMorph(false)) {
            return false;
        } else {
            morphCoverBase.set(morphCoverTo);
            morphCoverBaseRadius = morphCoverToRadius;
            morphProgress = 1f;
            backProgress = 0f;
            backPreview = true;
        }
        backDirection = swipeDirection;
        applyBackPreview();
        return true;
    }

    @Override
    protected void onCustomBackProgressed(float progress) {
        if (!backPreview || closing) {
            return;
        }
        cancelBackAnimator();
        backProgress = progress;
        applyBackPreview();
    }

    @Override
    protected void onCustomBackCancelled() {
        if (!backPreview || closing) {
            return;
        }
        cancelBackAnimator();
        backAnimator = ValueAnimator.ofFloat(backProgress, 0f);
        backAnimator.addUpdateListener(a -> {
            backProgress = (float) a.getAnimatedValue();
            applyBackPreview();
        });
        backAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                backAnimator = null;
                backPreview = false;
                clearMorph();
                if (miniSource != null) {
                    miniSource.setTransitionHidden(false);
                }
                morphBarsState = -1;
                applySystemBars(true);
            }
        });
        backAnimator.setDuration((long) (250 * clamp01(backProgress)));
        backAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        backAnimator.start();
    }

    private void applyBackPreview() {
        float t = BACK_GESTURE.getInterpolation(clamp01(backProgress));
        float w = root.getWidth();
        float h = root.getHeight();
        float s = 1f - 0.1f * t;
        float inset = (w - w * s) / 2f;
        float left = inset + Math.max(0f, inset - dp(8)) * backDirection;
        float top = Math.max(h / 2f, Math.min(h, morphFrom.centerY())) * (1f - s);
        morphTo.set(left, top, left + w * s, top + h * s);
        morphToRadius = dp(28) * t;
        morphCoverTo.set(left + morphCoverBase.left * s, top + morphCoverBase.top * s, left + morphCoverBase.right * s, top + morphCoverBase.bottom * s);
        morphCoverToRadius = morphCoverBaseRadius * s;
        applyMorph();
    }

    private void cancelBackAnimator() {
        if (backAnimator != null) {
            backAnimator.removeAllListeners();
            backAnimator.cancel();
            backAnimator = null;
        }
    }

    private void restoreMorphCover() {
        if (morphCoverView == cover) {
            cover.setVisibility(lyricsFraction >= 1f ? View.INVISIBLE : View.VISIBLE);
        } else if (morphCoverView != null) {
            morphCoverView.setVisibility(View.VISIBLE);
        }
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static void lerpRect(RectF a, RectF b, float t, RectF out) {
        out.set(a.left + (b.left - a.left) * t, a.top + (b.top - a.top) * t, a.right + (b.right - a.right) * t, a.bottom + (b.bottom - a.bottom) * t);
    }

    private void rectInRoot(View view, RectF out) {
        float x = 0;
        float y = 0;
        View v = view;
        while (v != null && v != root) {
            x += v.getLeft() + v.getTranslationX();
            y += v.getTop() + v.getTranslationY();
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        float px = view.getPivotX();
        float py = view.getPivotY();
        float sx = view.getScaleX();
        float sy = view.getScaleY();
        out.set(x + px * (1f - sx), y + py * (1f - sy), x + px + (view.getWidth() - px) * sx, y + py + (view.getHeight() - py) * sy);
    }

    private boolean prepareMorph(boolean hideCover) {
        PlayerMiniView mini = miniSource;
        if (skipMorph || mini == null || !mini.canTransition() || root.getWidth() == 0 || !root.isAttachedToWindow()) {
            return false;
        }
        if (lyricsAnimator != null && lyricsAnimator.isRunning()) {
            lyricsAnimator.end();
        }
        View coverView = lyricsFraction >= 0.5f ? smallCover : cover;
        morphCoverView = coverView;
        int[] loc = new int[2];
        root.getLocationOnScreen(loc);
        mini.getCardRect(morphFrom);
        morphFrom.offset(-loc[0], -loc[1]);
        mini.getCoverRect(morphCoverFrom);
        morphCoverFrom.offset(-loc[0], -loc[1]);
        morphFromRadius = mini.getCardRadius();
        morphCoverFromRadius = mini.getCoverRadius();
        morphFromColor = mini.getCardColor();
        recycleSnapshot();
        morphSnapshot = mini.captureCard();
        Bitmap bitmap = coverView instanceof CoverImage ? ((CoverImage) coverView).getImageReceiver().getBitmap() : null;
        morphCoverBitmap = bitmap != null ? bitmap : mini.getCoverBitmap();
        rectInRoot(coverView, morphCoverTo);
        morphCoverToRadius = coverView == cover ? dp(28) * cover.getScaleX() : dp(16);
        if (activity != null && activity.getWindow() != null) {
            int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
            underLightStatus = (flags & View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) != 0;
            underLightNav = (flags & View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) != 0;
        }
        morphBarsState = -1;
        mini.setTransitionHidden(true);
        if (hideCover) {
            coverView.setVisibility(View.INVISIBLE);
        }
        layout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        return true;
    }

    private void startMorph(float from, float to, long duration, Runnable onEnd) {
        if (morphAnimator != null) {
            morphAnimator.removeAllListeners();
            morphAnimator.cancel();
        }
        morphProgress = from;
        applyMorph();
        morphAnimator = ValueAnimator.ofFloat(from, to);
        morphAnimator.addUpdateListener(a -> {
            morphProgress = (float) a.getAnimatedValue();
            applyMorph();
        });
        morphAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                morphAnimator = null;
                onEnd.run();
            }
        });
        morphAnimator.setDuration(duration);
        morphAnimator.setInterpolator(EMPHASIZED);
        morphAnimator.start();
    }

    private void applyMorph() {
        float p = morphProgress;
        lerpRect(morphFrom, morphTo, p, morphRect);
        float s = morphRect.width() / Math.max(1, root.getWidth());
        layout.setPivotX(0);
        layout.setPivotY(0);
        layout.setScaleX(s);
        layout.setScaleY(s);
        layout.setTranslationX(morphRect.left);
        layout.setTranslationY(morphRect.top);
        layout.setAlpha(clamp01((p - 0.35f) / 0.65f));
        backDrawable.setAlpha(dimBehind ? (int) (dimBehindAlpha * clamp01(p)) : 0);
        updateMorphBars();
        root.invalidate();
    }

    private void clearMorph() {
        morphProgress = -1f;
        layout.setScaleX(1f);
        layout.setScaleY(1f);
        layout.setTranslationX(0);
        layout.setTranslationY(0);
        layout.setAlpha(1f);
        layout.setLayerType(View.LAYER_TYPE_NONE, null);
        recycleSnapshot();
        morphCoverBitmap = null;
        root.invalidate();
    }

    private void recycleSnapshot() {
        if (morphSnapshot != null) {
            morphSnapshot.recycle();
            morphSnapshot = null;
        }
    }

    private void updateMorphBars() {
        int state = morphProgress > 0.5f ? 1 : 0;
        if (state == morphBarsState) {
            return;
        }
        morphBarsState = state;
        if (state == 1) {
            boolean light = colors.lightStatusBar();
            setBars(light, light);
        } else {
            setBars(underLightStatus, underLightNav);
        }
    }

    private void drawMorphBackground(Canvas canvas) {
        float p = morphProgress;
        lerpRect(morphFrom, morphTo, p, morphRect);
        float r = morphFromRadius + (morphToRadius - morphFromRadius) * p;
        morphPath.rewind();
        morphPath.addRoundRect(morphRect, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(morphPath);
        backgroundPaint.setColor(ColorUtils.blendARGB(morphFromColor, colors.surface, clamp01(p / 0.5f)));
        canvas.drawRect(morphRect, backgroundPaint);
    }

    private void drawMorphForeground(Canvas canvas) {
        float p = morphProgress;
        Bitmap snapshot = morphSnapshot;
        if (snapshot != null && !snapshot.isRecycled()) {
            float a = 1f - clamp01(p / 0.3f);
            if (a > 0f) {
                canvas.save();
                float s = morphRect.width() / snapshot.getWidth();
                canvas.translate(morphRect.left, morphRect.top);
                canvas.scale(s, s);
                morphPaint.setAlpha((int) (255 * a));
                canvas.drawBitmap(snapshot, 0, 0, morphPaint);
                canvas.restore();
            }
        }
        canvas.restore();
        if (backPreview) {
            return;
        }
        lerpRect(morphCoverFrom, morphCoverTo, p, morphCover);
        float r = morphCoverFromRadius + (morphCoverToRadius - morphCoverFromRadius) * p;
        morphPath.rewind();
        morphPath.addRoundRect(morphCover, r, r, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(morphPath);
        Bitmap bitmap = morphCoverBitmap;
        if (bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
            int bw = bitmap.getWidth();
            int bh = bitmap.getHeight();
            float ratio = morphCover.width() / Math.max(1f, morphCover.height());
            if (bw / (float) bh > ratio) {
                int w = (int) (bh * ratio);
                int x = (bw - w) / 2;
                morphSrc.set(x, 0, x + w, bh);
            } else {
                int h = (int) (bw / ratio);
                int y = (bh - h) / 2;
                morphSrc.set(0, y, bw, y + h);
            }
            morphPaint.setAlpha(255);
            canvas.drawBitmap(bitmap, morphSrc, morphCover, morphPaint);
        } else {
            backgroundPaint.setColor(colors.primaryContainer);
            canvas.drawRect(morphCover, backgroundPaint);
        }
        canvas.restore();
    }

    private void drawGradientBackground(Canvas canvas, RectF rect, float r) {
        if (bgGradient == null || lastGradientSeed != colors.surface) {
            lastGradientSeed = colors.surface;
            int topColor = colors.surface;
            int bottomColor = ColorUtils.blendARGB(colors.surface, 0xff000000, dark ? 0.45f : 0.25f);
            bgGradient = new android.graphics.LinearGradient(0, 0, 0, rect.bottom, topColor, bottomColor, android.graphics.Shader.TileMode.CLAMP);
        }
        backgroundPaint.setShader(bgGradient);
        canvas.drawRoundRect(rect, r, r, backgroundPaint);
    }

    private void drawBlurredBackground(Canvas canvas, RectF rect, float r) {
        Bitmap art = cover != null ? cover.getBitmap() : null;
        if (art != null && !art.isRecycled()) {
            if (cachedBlurredArt == null || !TextUtils.equals(currentKey, lastBlurredKey)) {
                lastBlurredKey = currentKey;
                try {
                    Bitmap small = Bitmap.createScaledBitmap(art, 80, 80, true);
                    org.telegram.messenger.Utilities.stackBlurBitmap(small, 24);
                    cachedBlurredArt = small;
                } catch (Throwable ignore) {
                }
            }
        }
        if (cachedBlurredArt != null && !cachedBlurredArt.isRecycled()) {
            canvas.save();
            bgPath.reset();
            bgPath.addRoundRect(rect, r, r, Path.Direction.CW);
            canvas.clipPath(bgPath);
            bgSrcRect.set(0, 0, cachedBlurredArt.getWidth(), cachedBlurredArt.getHeight());
            canvas.drawBitmap(cachedBlurredArt, bgSrcRect, rect, morphPaint);
            scrimPaint.setColor(dark ? 0x99000000 : 0x77000000);
            canvas.drawRect(rect, scrimPaint);
            canvas.restore();
        } else {
            drawGradientBackground(canvas, rect, r);
        }
    }

    private void detach() {
        if (detached) {
            return;
        }
        detached = true;
        if (getWindow() != null) {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        if (instance == this) {
            instance = null;
        }
        NotificationCenter nc = NotificationCenter.getInstance(account);
        nc.removeObserver(this, NotificationCenter.messagePlayingDidReset);
        nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
        nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
        nc.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
        nc.removeObserver(this, NotificationCenter.fileLoaded);
        nc.removeObserver(this, NotificationCenter.audioInfoLoaded);
        nc.removeObserver(this, NotificationCenter.musicIdsLoaded);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.messagePlayingSpeedChanged);
        if (colorAnimator != null) {
            colorAnimator.cancel();
        }
    }

    public static void onModesChanged() {
        if (instance != null) {
            instance.updateModes(true);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.messagePlayingDidStart) {
            SleepTimer.getInstance().onTrackFinished();
            MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
            if (mo == null || !mo.isMusic()) {
                dismissImmediately();
                return;
            }
            bind(mo, true);
        } else if (id == NotificationCenter.messagePlayingDidReset) {
            SleepTimer.getInstance().onTrackFinished();
            MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
            if (mo == null || !mo.isMusic()) {
                dismissImmediately();
            } else {
                bind(mo, true);
            }
        } else if (id == NotificationCenter.messagePlayingPlayStateChanged) {
            updatePlayState(true);
            updateProgress();
        } else if (id == NotificationCenter.messagePlayingProgressDidChanged) {
            updateProgress();
        } else if (id == NotificationCenter.audioInfoLoaded) {
            MessageObject mo = current;
            if (mo != null && PlayerArt.isPlaying(mo)) {
                int shapeId = PlayerArt.getOrCycleShape(mo);
                cover.setShapeId(shapeId);
                smallCover.setShapeId(shapeId);
                cover.setMessage(mo);
                smallCover.setMessage(mo);
                if (lyricsMode && lyricsView.getState() != LyricsView.STATE_LYRICS) {
                    lyricsKey = null;
                    loadLyrics();
                }
            }
        } else if (id == NotificationCenter.fileLoaded) {
            MessageObject mo = current;
            if (mo != null && mo.getDocument() != null && TextUtils.equals((String) args[0], FileLoader.getAttachFileName(mo.getDocument()))) {
                int shapeId = PlayerArt.getOrCycleShape(mo);
                cover.setShapeId(shapeId);
                smallCover.setShapeId(shapeId);
                cover.setMessage(mo);
                smallCover.setMessage(mo);
                if (lyricsMode && (lyricsView.getState() != LyricsView.STATE_LYRICS)) {
                    lyricsKey = null;
                    loadLyrics();
                }
            }
        } else if (id == NotificationCenter.musicIdsLoaded) {
            updateLike(true);
        } else if (id == NotificationCenter.messagePlayingSpeedChanged) {
            updateSpeed();
        }
    }

    private void bind(MessageObject mo, boolean animated) {
        if (mo == null) {
            return;
        }
        String key = PlayerArt.key(mo);
        boolean changed = !TextUtils.equals(key, currentKey);
        current = mo;
        currentKey = key;
        titleView.setText(mo.getMusicTitle());
        artistView.setText(mo.getMusicAuthor());
        smallTitle.setText(mo.getMusicTitle());
        smallArtist.setText(mo.getMusicAuthor());
        int shapeId = PlayerArt.getOrCycleShape(mo);
        cover.setShapeId(shapeId);
        smallCover.setShapeId(shapeId);
        cover.setMessage(mo);
        smallCover.setMessage(mo);
        if (changed) {
            Integer seed = PlayerArt.cachedSeed(mo);
            if (seed != null) {
                animateColors(PlayerColors.fromSeed(seed, dark), animated);
            } else if (PlayerArt.fileCover(mo) == null && PlayerArt.fullLocation(mo) == null && PlayerArt.thumbLocation(mo) == null) {
                animateColors(PlayerColors.fromSeed(fallbackSeed, dark), animated);
            }
            lyricsKey = null;
            if (lyricsMode) {
                loadLyrics();
            }
        }
        updateHeader();
        updateLike(animated);
        updateModes(animated);
        updateSpeed();
        updatePlayState(animated);
        updateProgress();
    }

    private void onSeed(MessageObject mo, int seed) {
        if (mo == current) {
            animateColors(PlayerColors.fromSeed(seed, dark), true);
        }
    }

    private void updateHeader() {
        MessageObject mo = current;
        if (mo == null) {
            return;
        }
        MessagesController.SavedMusicList saved = MediaController.getInstance().currentSavedMusicList;
        if (saved != null) {
            headerLabel.setText(getString(R.string.OEPlayerFromProfile));
            headerTitle.setText(DialogObject.getShortName(saved.currentAccount, saved.dialogId));
        } else if (MediaController.getInstance().currentPlaylistIsGlobalSearch()) {
            headerLabel.setText(getString(R.string.OEPlayerFromSearch));
            headerTitle.setText(getString(R.string.OEPlayerMusic));
        } else {
            long did = mo.getDialogId();
            headerLabel.setText(getString(R.string.OEPlayerFromChat));
            if (did == UserConfig.getInstance(account).getClientUserId()) {
                headerTitle.setText(getString(R.string.SavedMessages));
            } else {
                headerTitle.setText(DialogObject.getName(account, did));
            }
        }
    }

    private void updateLike(boolean animated) {
        MessageObject mo = current;
        boolean visible = mo != null && mo.getDocument() != null && !PlayerActions.noForwards(mo);
        likeButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            return;
        }
        boolean saved = PlayerActions.isSavedToProfile(mo);
        likeButton.setActive(saved, animated);
        likeButton.setRadius(dp(saved ? 14 : 24), animated);
        heartIcon.setFillStroke(saved);
        likeButton.setContentDescription(getString(saved ? R.string.ProfilePlaylistRemoveFromProfile : R.string.AudioSaveToMyProfile));
    }

    private void toggleProfile() {
        MessageObject mo = current;
        if (mo == null || PlayerActions.isProfileSavePending(mo)) {
            return;
        }
        boolean save = !PlayerActions.isSavedToProfile(mo);
        likeButton.setActive(save, true);
        likeButton.setRadius(dp(save ? 14 : 24), true);
        heartIcon.setFillStroke(save);
        PlayerActions.saveToProfile(mo, save, error -> {
            Theme.ResourcesProvider rp = colors.provider(resourcesProvider);
            if (error != null) {
                updateLike(true);
                BulletinFactory.of(root, rp).showForError(error);
                return;
            }
            MessagesController.SavedMusicList list = MediaController.getInstance().currentSavedMusicList;
            if (!save && list != null && list.dialogId == UserConfig.getInstance(mo.currentAccount).getClientUserId()) {
                list.remove(mo);
                if (list.list.isEmpty()) {
                    MediaController.getInstance().cleanup();
                    dismissImmediately();
                    return;
                }
                NotificationCenter.getInstance(mo.currentAccount).postNotificationName(NotificationCenter.musicListLoaded, list);
            }
            updateLike(true);
            BulletinFactory.of(root, rp).createSimpleBulletin(save ? R.raw.saved_messages : R.raw.ic_delete,
                    getString(save ? R.string.AudioSaveToMyProfileSaved : R.string.AudioSaveToMyProfileUnsaved)).show();
        });
    }

    private void updateModes(boolean animated) {
        int mode = SharedConfig.repeatMode;
        boolean repeatActive = mode == 1 || mode == 2;
        controls.setModes(SharedConfig.shuffleMusic, repeatActive, mode == 2, animated);
        controls.repeat.setContentDescription(getString(mode == 2 ? R.string.AccDescrRepeatOne : mode == 1 ? R.string.AccDescrRepeatList : R.string.AccDescrRepeatOff));
    }

    private void updateSpeed() {
        float speed = MediaController.getInstance().getPlaybackSpeed(true);
        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(currentLocale()));
        String label = format.format(speed) + "×";
        speedButton.setText(label);
        speedButton.setContentDescription(getString(R.string.OEPlayerSpeed) + " " + label);
    }

    private static Locale currentLocale() {
        Locale locale = LocaleController.getInstance().getCurrentLocale();
        return locale != null ? locale : Locale.getDefault();
    }

    private void cycleSpeed() {
        float speed = MediaController.getInstance().getPlaybackSpeed(true);
        int index = -1;
        for (int i = 0; i < SPEEDS.length; i++) {
            if (Math.abs(SPEEDS[i] - speed) < 0.01f) {
                index = i;
                break;
            }
        }
        float next = index < 0 ? 1f : SPEEDS[(index + 1) % SPEEDS.length];
        MediaController.getInstance().setPlaybackSpeed(true, next);
        updateSpeed();
    }

    private void showSpeedSliderDialog() {
        Context ctx = getContext();
        if (ctx == null) return;
        android.app.Dialog dialog = new android.app.Dialog(ctx);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(20), dp(22), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(24));
        bg.setColor(Theme.getColor(Theme.key_dialogBackground));
        root.setBackground(bg);

        // Header Row: Title on left, current speed badge on right
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(ctx);
        title.setText(getString(R.string.OEAppearancePlayerSpeedDialogTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        title.setTypeface(AndroidUtilities.bold());
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView speedBadge = new TextView(ctx);
        speedBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        speedBadge.setTypeface(AndroidUtilities.bold());
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton) != 0 ? Theme.getColor(Theme.key_featuredStickers_addButton) : 0xff00d2b4;
        speedBadge.setTextColor(accent);
        header.addView(speedBadge, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(header);

        // Slider limits: 0.25f to 3.0f
        final float minSpeed = 0.25f;
        final float maxSpeed = 3.0f;
        final int steps = 55; // (3.00 - 0.25) / 0.05 = 55 steps

        float currentSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        if (currentSpeed < minSpeed) currentSpeed = minSpeed;
        if (currentSpeed > maxSpeed) currentSpeed = maxSpeed;

        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(currentLocale()));
        speedBadge.setText(format.format(currentSpeed) + "×");

        Material3Slider bar = new Material3Slider(ctx, accent);
        bar.setValue(currentSpeed);
        root.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0, 10, 0, 10));

        // Quick Preset Chips: 0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x
        LinearLayout presets = new LinearLayout(ctx);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setGravity(Gravity.CENTER);
        presets.setPadding(0, dp(4), 0, dp(14));

        final float[] presetValues = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        final TextView[] chipViews = new TextView[presetValues.length];

        Runnable updateChips = () -> {
            float cur = bar.getValue();
            for (int i = 0; i < presetValues.length; i++) {
                boolean selected = Math.abs(cur - presetValues[i]) < 0.02f;
                GradientDrawable chipBg = new GradientDrawable();
                chipBg.setCornerRadius(dp(14));
                if (selected) {
                    chipBg.setColor(accent);
                    chipViews[i].setTextColor(0xffffffff);
                } else {
                    chipBg.setColor(Theme.getColor(Theme.key_dialogBackgroundGray));
                    chipViews[i].setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                }
                chipViews[i].setBackground(chipBg);
            }
        };

        bar.setCallback((val, fromUser) -> {
            speedBadge.setText(format.format(val) + "×");
            MediaController.getInstance().setPlaybackSpeed(true, val);
            updateSpeed();
            updateChips.run();
        });

        for (int i = 0; i < presetValues.length; i++) {
            final float pv = presetValues[i];
            TextView chip = new TextView(ctx);
            chip.setText(format.format(pv) + "×");
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(7), dp(8), dp(7));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.leftMargin = dp(2);
            lp.rightMargin = dp(2);
            chip.setOnClickListener(v -> {
                bar.setValue(pv);
                speedBadge.setText(format.format(pv) + "×");
                MediaController.getInstance().setPlaybackSpeed(true, pv);
                updateSpeed();
                updateChips.run();
            });
            chipViews[i] = chip;
            presets.addView(chip, lp);
        }
        updateChips.run();
        root.addView(presets);

        // Bottom action buttons: Reset (1.0x) on left, Done on right
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        TextView resetBtn = new TextView(ctx);
        resetBtn.setText(getString(R.string.OEAppearancePlayerSpeedReset));
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        resetBtn.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        resetBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
        resetBtn.setOnClickListener(v -> {
            bar.setValue(1.0f);
            speedBadge.setText("1×");
            MediaController.getInstance().setPlaybackSpeed(true, 1.0f);
            updateSpeed();
            updateChips.run();
        });
        buttons.addView(resetBtn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView doneBtn = new TextView(ctx);
        doneBtn.setText(getString(R.string.Done));
        doneBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneBtn.setTypeface(AndroidUtilities.bold());
        doneBtn.setTextColor(accent);
        doneBtn.setPadding(dp(12), dp(8), dp(8), dp(8));
        doneBtn.setOnClickListener(v -> dialog.dismiss());
        buttons.addView(doneBtn, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(buttons);

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int dialogWidth = Math.min(AndroidUtilities.displaySize.x - dp(40), dp(360));
            dialog.getWindow().setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    public static class Material3Slider extends View {
        private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rectF = new RectF();

        private final float min = 0.25f;
        private final float max = 3.0f;
        private final float step = 0.05f;
        private float value = 1.0f;
        private boolean isDragging = false;
        private ValueCallback callback;

        public interface ValueCallback {
            void onValueChanged(float value, boolean fromUser);
        }

        public Material3Slider(Context context, int accentColor) {
            super(context);
            activePaint.setColor(accentColor);
            thumbPaint.setColor(accentColor);
            inactivePaint.setColor(Theme.getColor(Theme.key_dialogBackgroundGray));
        }

        public void setValue(float v) {
            v = Math.max(min, Math.min(max, v));
            this.value = Math.round(v / step) * step;
            this.value = Math.round(this.value * 100f) / 100f;
            invalidate();
        }

        public float getValue() {
            return value;
        }

        public void setCallback(ValueCallback cb) {
            this.callback = cb;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int h = AndroidUtilities.dp(48);
            setMeasuredDimension(w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth() - getPaddingLeft() - getPaddingRight();
            int h = getHeight();
            float cy = h / 2f;
            float trackHeight = AndroidUtilities.dp(16);
            float trackRadius = trackHeight / 2f;
            float thumbWidth = AndroidUtilities.dp(4);
            float thumbHeight = AndroidUtilities.dp(28);
            float thumbRadius = thumbWidth / 2f;
            float gap = AndroidUtilities.dp(6);

            float left = getPaddingLeft() + thumbWidth / 2f;
            float right = left + w - thumbWidth;
            float availableW = right - left;
            float progress = (value - min) / (max - min);
            float thumbX = left + progress * availableW;

            // Inactive track (right of thumb with gap)
            float inactiveLeft = Math.min(right, thumbX + gap);
            if (inactiveLeft < right) {
                canvas.save();
                canvas.clipRect(inactiveLeft, cy - trackRadius, right, cy + trackRadius);
                rectF.set(left, cy - trackRadius, right, cy + trackRadius);
                canvas.drawRoundRect(rectF, trackRadius, trackRadius, inactivePaint);
                canvas.restore();
            }

            // Active track (left of thumb with gap)
            float activeRight = Math.max(left, thumbX - gap);
            if (activeRight > left) {
                canvas.save();
                canvas.clipRect(left, cy - trackRadius, activeRight, cy + trackRadius);
                rectF.set(left, cy - trackRadius, right, cy + trackRadius);
                canvas.drawRoundRect(rectF, trackRadius, trackRadius, activePaint);
                canvas.restore();
            }

            // MD3 thumb pill
            rectF.set(thumbX - thumbWidth / 2f, cy - thumbHeight / 2f, thumbX + thumbWidth / 2f, cy + thumbHeight / 2f);
            canvas.drawRoundRect(rectF, thumbRadius, thumbRadius, thumbPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float left = getPaddingLeft();
            float right = getWidth() - getPaddingRight();
            float availableW = right - left;
            if (availableW <= 0) return false;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isDragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                    updateFromX(event.getX());
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (isDragging) {
                        updateFromX(event.getX());
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private void updateFromX(float x) {
            float left = getPaddingLeft();
            float right = getWidth() - getPaddingRight();
            float progress = (x - left) / (right - left);
            progress = Math.max(0f, Math.min(1f, progress));
            float rawValue = min + progress * (max - min);
            float steppedValue = Math.round(rawValue / step) * step;
            steppedValue = Math.max(min, Math.min(max, Math.round(steppedValue * 100f) / 100f));
            if (Math.abs(steppedValue - value) > 0.001f) {
                value = steppedValue;
                if (Math.abs(value - 1.0f) < 0.01f || Math.abs(value - 2.0f) < 0.01f) {
                    AndroidUtilities.vibrateCursor(this);
                }
                invalidate();
                if (callback != null) {
                    callback.onValueChanged(value, true);
                }
            }
        }
    }

    private void togglePlay() {
        MediaController mc = MediaController.getInstance();
        if (mc.isDownloadingCurrentMessage()) {
            return;
        }
        MessageObject mo = mc.getPlayingMessageObject();
        if (mc.isMessagePaused()) {
            mc.playMessage(mo);
        } else {
            mc.pauseMessage(mo);
        }
    }

    private void updatePlayState(boolean animated) {
        boolean playing = !MediaController.getInstance().isMessagePaused();
        controls.setPlaying(playing, animated);
        controls.play.setContentDescription(getString(playing ? R.string.AccActionPause : R.string.AccActionPlay));
        seekBar.setPlaying(playing);
        float scale = playing ? 1f : 0.92f;
        if (cover.getScaleX() != scale) {
            if (animated) {
                cover.animate().scaleX(scale).scaleY(scale).setDuration(450).setInterpolator(EMPHASIZED).start();
            } else {
                cover.setScaleX(scale);
                cover.setScaleY(scale);
            }
        }
    }

    private float displayProgress() {
        MessageObject mo = current;
        if (seeking) {
            return seekProgress;
        }
        return mo != null ? mo.audioProgress : 0f;
    }

    private void updateProgress() {
        MessageObject mo = current;
        if (mo == null) {
            return;
        }
        if (!seeking) {
            seekBar.setProgress(mo.audioProgress);
        }
        updateTimes();
        if (lyricsMode) {
            lyricsView.setPosition((long) (mo.audioProgress * mo.getDuration() * 1000));
        }
    }

    private void updateTimes() {
        MessageObject mo = current;
        if (mo == null) {
            return;
        }
        int duration = (int) Math.round(mo.getDuration());
        int now = seeking ? (int) (duration * seekProgress) : Math.min(duration, mo.audioProgressSec);
        String a = formatTime(now);
        String b = "−" + formatTime(Math.max(0, duration - now));
        if (!TextUtils.equals(timeNow.getText(), a)) {
            timeNow.setText(a);
            bubble.setText(a);
        }
        if (!TextUtils.equals(timeLeft.getText(), b)) {
            timeLeft.setText(b);
        }
    }

    private static String formatTime(int seconds) {
        if (seconds >= 3600) {
            return String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
        return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void layoutBubble() {
        if (bubble.getWidth() == 0) {
            bubble.post(this::layoutBubble);
            return;
        }
        float left = dp(3);
        float right = seekBar.getWidth() - dp(3);
        float x = seekBar.getLeft() + left + displayProgress() * (right - left);
        float tx = x - bubble.getLeft() - bubble.getWidth() / 2f;
        float min = -bubble.getLeft() + dp(8);
        float max = layout.getWidth() - bubble.getLeft() - bubble.getWidth() - dp(8);
        bubble.setTranslationX(Math.max(min, Math.min(max, tx)));
    }

    private void setLyricsMode(boolean value, boolean animated) {
        if (lyricsMode == value) {
            return;
        }
        lyricsMode = value;
        if (getWindow() != null) {
            if (value && app.exteraless.appearance.AppearanceConfig.playerLyricsHideStatusBar()) {
                getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            } else {
                getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }
        lyricsButton.setActive(value, animated);
        lyricsButton.setRadius(dp(value ? 26 : 8), animated);
        if (value) {
            loadLyrics();
            MessageObject mo = current;
            if (mo != null) {
                lyricsView.setPosition((long) (mo.audioProgress * mo.getDuration() * 1000));
            }
        }
        if (lyricsAnimator != null) {
            lyricsAnimator.cancel();
        }
        float target = value ? 1f : 0f;
        if (!animated) {
            setLyricsFraction(target);
            return;
        }
        lyricsPanel.setVisibility(View.VISIBLE);
        cover.setVisibility(View.VISIBLE);
        titleRow.setVisibility(View.VISIBLE);
        lyricsAnimator = ValueAnimator.ofFloat(lyricsFraction, target);
        lyricsAnimator.addUpdateListener(a -> setLyricsFraction((float) a.getAnimatedValue()));
        lyricsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setLyricsFraction(target);
            }
        });
        lyricsAnimator.setDuration(360);
        lyricsAnimator.setInterpolator(EMPHASIZED);
        lyricsAnimator.start();
    }

    private void setLyricsFraction(float f) {
        lyricsFraction = f;
        float playerAlpha = Math.max(0f, 1f - f * 1.6f);
        float lyricsAlpha = Math.max(0f, (f - 0.35f) / 0.65f);
        cover.setAlpha(playerAlpha);
        titleRow.setAlpha(playerAlpha);
        cover.setTranslationY(-dp(24) * f);
        titleRow.setTranslationY(-dp(24) * f);
        lyricsPanel.setAlpha(lyricsAlpha);
        lyricsPanel.setTranslationY(dp(24) * (1f - f));
        cover.setVisibility(f >= 1f ? View.INVISIBLE : View.VISIBLE);
        titleRow.setVisibility(f >= 1f ? View.INVISIBLE : View.VISIBLE);
        lyricsPanel.setVisibility(f <= 0f ? View.GONE : View.VISIBLE);
    }

    private OnlineLyrics.Query query(MessageObject mo) {
        String album = null;
        if (PlayerArt.isPlaying(mo)) {
            AudioInfo info = MediaController.getInstance().getAudioInfo();
            if (info != null) {
                album = info.getAlbum();
            }
        }
        return new OnlineLyrics.Query(mo.getMusicAuthor(false), mo.getMusicTitle(false), album, (int) Math.round(mo.getDuration()));
    }

    private long positionMs() {
        MessageObject mo = current;
        return mo == null ? 0 : (long) (mo.audioProgress * mo.getDuration() * 1000);
    }

    private void loadLyrics() {
        MessageObject mo = current;
        if (mo == null) {
            return;
        }
        String key = currentKey;
        if (TextUtils.equals(lyricsKey, key)) {
            return;
        }
        lyricsKey = key;
        if (PlayerArt.isPlaying(mo)) {
            AudioInfo info = MediaController.getInstance().getAudioInfo();
            Lyrics embedded = info != null ? Lyrics.parse(info.getLyrics(), Lyrics.SOURCE_FILE) : null;
            if (embedded != null) {
                showLyrics(embedded);
                return;
            }
        }
        OnlineLyrics.Query q = query(mo);
        if (!q.valid()) {
            lyricsView.showState(LyricsView.STATE_NOT_FOUND);
            sourceView.setText(null);
            return;
        }
        Lyrics cached = OnlineLyrics.cached(q);
        if (cached != null) {
            showLyrics(cached);
            return;
        }
        lyricsView.showState(LyricsView.STATE_LOADING);
        sourceView.setText(null);
        OnlineLyrics.loadCached(q, (lyrics, status) -> {
            if (!TextUtils.equals(key, currentKey)) {
                return;
            }
            if (lyrics != null) {
                showLyrics(lyrics);
            } else if (AppearanceConfig.lrclibAllowed.Bool() || AppearanceConfig.hasOnlineLyricsProvider()) {
                fetchLyrics();
            } else {
                lyricsView.showState(LyricsView.STATE_OFFER);
            }
        });
    }

    private void fetchLyrics() {
        MessageObject mo = current;
        if (mo == null) {
            return;
        }
        String key = currentKey;
        OnlineLyrics.Query q = query(mo);
        if (!q.valid()) {
            lyricsView.showState(LyricsView.STATE_NOT_FOUND);
            return;
        }
        if (OnlineLyrics.knownMissing(q)) {
            lyricsView.showState(LyricsView.STATE_NOT_FOUND);
            return;
        }
        lyricsView.showState(LyricsView.STATE_LOADING);
        sourceView.setText(null);
        OnlineLyrics.fetch(q, (lyrics, status) -> {
            if (!TextUtils.equals(key, currentKey)) {
                return;
            }
            if (lyrics != null) {
                showLyrics(lyrics);
            } else {
                lyricsView.showState(status == OnlineLyrics.ERROR ? LyricsView.STATE_ERROR : LyricsView.STATE_NOT_FOUND);
            }
        });
    }

    private void showLyrics(Lyrics lyrics) {
        currentLyrics = lyrics;
        if (translateButton != null) {
            translateButton.setAlpha(1.0f);
        }
        if (lyrics == null) {
            return;
        }
        if (lyrics.instrumental) {
            lyricsView.showState(LyricsView.STATE_INSTRUMENTAL);
            sourceView.setText(lyrics.provider);
            return;
        }
        lyricsView.setLyrics(lyrics, positionMs());
        if (lyrics.source == Lyrics.SOURCE_ONLINE) {
            sourceView.setText(LocaleController.formatString(lyrics.synced ? R.string.OEPlayerSourceSynced : R.string.OEPlayerSourcePlain, lyrics.provider));
        } else {
            sourceView.setText(getString(lyrics.synced ? R.string.OEPlayerSourceSyncedFile : R.string.OEPlayerSourcePlainFile));
        }
        if (AppearanceConfig.playerLyricsTranslate() && !lyrics.lines.isEmpty()) {
            translateCurrentLyrics(false);
        }
    }

    private void translateCurrentLyrics(boolean showFeedback) {
        if (currentLyrics == null || currentLyrics.lines.isEmpty() || currentLyrics.instrumental) {
            return;
        }
        if (isTranslatingLyrics) {
            if (showFeedback && root != null) {
                BulletinFactory.of(root, colors.provider(resourcesProvider))
                        .createSimpleBulletin(R.drawable.ic_translate, getString(R.string.OEAppearancePlayerLyricsTranslating))
                        .show();
            }
            return;
        }
        if (currentLyrics.isTranslated) {
            if (showFeedback) {
                currentLyrics.showTranslation = !currentLyrics.showTranslation;
                lyricsView.notifyLyricsChanged();
                if (translateButton != null) {
                    translateButton.setAlpha(currentLyrics.showTranslation ? 1.0f : 0.5f);
                }
            }
            return;
        }

        if (showFeedback && root != null) {
            BulletinFactory.of(root, colors.provider(resourcesProvider))
                    .createSimpleBulletin(R.drawable.ic_translate, getString(R.string.OEAppearancePlayerLyricsTranslating))
                    .show();
        }

        isTranslatingLyrics = true;
        final Lyrics targetLyrics = currentLyrics;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < targetLyrics.lines.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            String t = targetLyrics.lines.get(i).text;
            if (t != null) {
                sb.append(t.replace('\n', ' ').trim());
            }
        }

        tw.nekomimi.nekogram.translate.Translator.translate(sb.toString(), new tw.nekomimi.nekogram.translate.Translator.Companion.TranslateCallBack() {
            @Override
            public void onSuccess(@org.jetbrains.annotations.NotNull String translation) {
                isTranslatingLyrics = false;
                if (currentLyrics != targetLyrics) {
                    return;
                }
                String[] split = translation.split("\n");
                for (int i = 0; i < targetLyrics.lines.size(); i++) {
                    if (i < split.length) {
                        String tr = split[i].trim();
                        targetLyrics.lines.get(i).translation = tr.isEmpty() ? null : tr;
                    }
                }
                targetLyrics.isTranslated = true;
                targetLyrics.showTranslation = true;
                lyricsView.notifyLyricsChanged();
                if (translateButton != null) {
                    translateButton.setAlpha(1.0f);
                }
                if (showFeedback && root != null) {
                    BulletinFactory.of(root, colors.provider(resourcesProvider))
                            .createSimpleBulletin(R.drawable.ic_translate, getString(R.string.OEAppearancePlayerLyricsTranslated))
                            .show();
                }
            }

            @Override
            public void onFailed(boolean unsupported, @org.jetbrains.annotations.NotNull String message) {
                isTranslatingLyrics = false;
                if (showFeedback && root != null) {
                    BulletinFactory.of(root, colors.provider(resourcesProvider))
                            .createSimpleBulletin(R.drawable.ic_translate, message)
                            .show();
                }
            }
        });
    }

    private void openQueue() {
        new PlayerQueueSheet(getContext(), colors, resourcesProvider).show();
    }

    private void openClassic() {
        dismissImmediately();
        if (activity != null) {
            new AudioPlayerAlert(activity, resourcesProvider).show();
        }
    }

    private void showMenu(View anchor) {
        MessageObject mo = current;
        if (mo == null || activity == null) {
            return;
        }
        boolean noForwards = PlayerActions.noForwards(mo);
        Theme.ResourcesProvider rp = colors.provider(resourcesProvider);
        ItemOptions o = ItemOptions.makeOptions(container, rp, anchor, true);
        if (!noForwards) {
            ItemOptions sub = o.makeSwipeback();
            sub.add(R.drawable.ic_ab_back, getString(R.string.Back), o::closeSwipeback);
            sub.addGap();
            sub.addIf(!PlayerActions.isSavedToProfile(mo), R.drawable.left_status_profile, getString(R.string.AudioSaveToMyProfile), () -> {
                o.dismiss();
                toggleProfile();
            });
            sub.add(R.drawable.msg_saved, getString(R.string.AudioSaveToSavedMessages), () -> {
                o.dismiss();
                PlayerActions.forwardTo(activity, mo, UserConfig.getInstance(mo.currentAccount).getClientUserId());
            });
            sub.add(R.drawable.menu_download_round, getString(R.string.AudioSaveToMusicFolder), () -> {
                o.dismiss();
                PlayerActions.saveToMusic(activity, mo, root, rp);
            });
            sub.addGap();
            sub.addText(getString(R.string.AudioSaveToInfo), 12, dp(200));
            o.add(R.drawable.msg_stories_save, getString(R.string.AudioSaveTo), () -> o.openSwipeback(sub));
            if (o.getLast() != null) {
                o.getLast().setRightIcon(R.drawable.msg_arrowright);
            }
            o.add(R.drawable.msg_forward, getString(R.string.Forward), () -> {
                o.dismiss();
                dismissImmediately();
                PlayerActions.forward(activity, mo);
            });
        } else {
            o.add(R.drawable.menu_download_round, getString(R.string.AudioSaveToMusicFolder), () -> {
                o.dismiss();
                PlayerActions.saveToMusic(activity, mo, root, rp);
            });
        }
        o.add(R.drawable.msg_shareout, getString(R.string.ShareFile), () -> {
            o.dismiss();
            PlayerActions.share(activity, mo);
        });
        o.addIf(mo.getId() > 0, R.drawable.msg_message, getString(R.string.ShowInChat), () -> {
            o.dismiss();
            dismissImmediately();
            PlayerActions.showInChat(activity, mo);
        });
        o.addGap();
        o.add(R.drawable.menu_night_mode_24, getString(R.string.OEAppearancePlayerSleepTimer), () -> {
            o.dismiss();
            SleepTimer.showDialog(getContext(), rp);
        });
        o.add(R.drawable.baseline_volume_up_24, getString(R.string.OEAppearancePlayerEqualizer), () -> {
            o.dismiss();
            try {
                Intent eqIntent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
                eqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getContext().getPackageName());
                eqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
                if (activity != null && eqIntent.resolveActivity(activity.getPackageManager()) != null) {
                    activity.startActivity(eqIntent);
                } else {
                    BulletinFactory.global().createSimpleBulletin(R.drawable.baseline_volume_up_24, getString(R.string.OEAppearancePlayerEqualizerNotFound)).show();
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
        o.add(R.drawable.msg_settings, getString(R.string.OEAppearancePlayerSettings), () -> {
            o.dismiss();
            dismissImmediately();
            if (activity != null) {
                activity.presentFragment(new PlayerSettingsActivity());
            }
        });
        o.add(R.drawable.msg_filled_data_music, getString(R.string.OEPlayerClassic), () -> {
            o.dismiss();
            openClassic();
        });
        o.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
        o.show();
    }

    private boolean onCoverTouch(View v, MotionEvent e) {
        if (!AppearanceConfig.playerSwipeTrack()) {
            return false;
        }
        if (coverAnimating) {
            return false;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartX = e.getRawX();
                dragStartY = e.getRawY();
                dragTracking = true;
                dragActive = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (!dragTracking) {
                    return false;
                }
                float dx = e.getRawX() - dragStartX;
                float dy = e.getRawY() - dragStartY;
                if (!dragActive) {
                    if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        dragActive = true;
                        dragStartX = e.getRawX();
                        dx = 0;
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    } else if (Math.abs(dy) > touchSlop) {
                        dragTracking = false;
                        return false;
                    }
                }
                if (dragActive) {
                    setCoverDrag(dx);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float dx = e.getRawX() - dragStartX;
                boolean wasActive = dragActive;
                dragTracking = false;
                dragActive = false;
                if (wasActive) {
                    finishCoverDrag(e.getActionMasked() == MotionEvent.ACTION_UP ? dx : 0);
                }
                return true;
            }
        }
        return false;
    }

    private void setCoverDrag(float dx) {
        cover.setTranslationX(dx);
        cover.setRotation(dx / dp(40));
        cover.setAlpha(1f - Math.min(Math.abs(dx) / dp(420), 0.4f));
    }

    private void finishCoverDrag(float dx) {
        if (Math.abs(dx) < dp(70)) {
            cover.animate().translationX(0).rotation(0).alpha(1f).setDuration(450).setInterpolator(EMPHASIZED).start();
            return;
        }
        boolean next = dx < 0;
        float out = (next ? -1 : 1) * layout.getWidth();
        coverAnimating = true;
        cover.animate().translationX(out).rotation(out / dp(40) / 4f).alpha(0f).setDuration(180).setInterpolator(CubicBezierInterpolator.EASE_IN).withEndAction(() -> {
            if (next) {
                MediaController.getInstance().playNextMessage();
            } else {
                MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
                if (mo != null) {
                    mo.audioProgressSec = 0;
                }
                MediaController.getInstance().playPreviousMessage();
            }
            cover.setTranslationX(-out * 0.35f);
            cover.setRotation(0);
            cover.animate().translationX(0).alpha(1f).setDuration(450).setInterpolator(EMPHASIZED).withEndAction(() -> coverAnimating = false).start();
        }).start();
    }

    private void animateColors(PlayerColors target, boolean animated) {
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        if (!animated) {
            applyColors(target);
            return;
        }
        PlayerColors from = colors;
        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.addUpdateListener(a -> applyColors(PlayerColors.lerp(from, target, (float) a.getAnimatedValue())));
        colorAnimator.setDuration(600);
        colorAnimator.setInterpolator(EMPHASIZED);
        colorAnimator.start();
    }

    private void applyColors(PlayerColors c) {
        colors = c;
        root.invalidate();
        collapseIcon.setColor(c.onSurface);
        moreIcon.setColor(c.onSurface);
        collapseButton.setBackground(Theme.createSelectorDrawable(c.ripple(), Theme.RIPPLE_MASK_CIRCLE_20DP));
        moreButton.setBackground(Theme.createSelectorDrawable(c.ripple(), Theme.RIPPLE_MASK_CIRCLE_20DP));
        headerLabel.setTextColor(c.onSurfaceVariant);
        headerTitle.setTextColor(c.onSurface);
        cover.setColors(c.primaryContainer, c.onPrimaryContainer);
        smallCover.setColors(c.primaryContainer, c.onPrimaryContainer);
        if (Build.VERSION.SDK_INT >= 28) {
            cover.setOutlineSpotShadowColor(c.shadow());
            cover.setOutlineAmbientShadowColor(c.shadow());
        }
        titleView.setTextColor(c.onSurface);
        artistView.setTextColor(c.onSurfaceVariant);
        smallTitle.setTextColor(c.onSurface);
        smallArtist.setTextColor(c.onSurfaceVariant);
        likeButton.setColors(0, c.onSurfaceVariant, c.primaryContainer, c.onPrimaryContainer);
        lyricsView.setColors(c);
        if (translateButton != null) {
            translateButton.setColorFilter(new PorterDuffColorFilter(c.onSurface, PorterDuff.Mode.SRC_IN));
            translateButton.setBackground(Theme.createSelectorDrawable(c.ripple(), Theme.RIPPLE_MASK_CIRCLE_20DP));
        }
        sourceView.setTextColor(c.onSurfaceVariant);
        seekBar.setColors(c.primary, c.secondaryContainer);
        timeNow.setTextColor(c.onSurfaceVariant);
        timeLeft.setTextColor(c.onSurfaceVariant);
        bubbleBg.setColor(c.onSurface);
        bubble.setTextColor(c.surface);
        controls.shuffle.setColors(0, c.onSurfaceVariant, c.secondaryContainer, c.onSecondaryContainer);
        controls.repeat.setColors(0, c.onSurfaceVariant, c.secondaryContainer, c.onSecondaryContainer);
        controls.repeat.setBadgeColors(c.primary, c.onPrimary);
        controls.prev.setColors(c.primaryContainer, c.onPrimaryContainer, c.primaryContainer, c.onPrimaryContainer);
        controls.next.setColors(c.primaryContainer, c.onPrimaryContainer, c.primaryContainer, c.onPrimaryContainer);
        controls.play.setColors(c.primary, c.onPrimary, c.primary, c.onPrimary);
        speedButton.setColors(c.surfaceHigh, c.onSurface, c.surfaceHigh, c.onSurface);
        lyricsButton.setColors(c.surfaceHigh, c.onSurface, c.primary, c.onPrimary);
        queueButton.setColors(c.surfaceHigh, c.onSurface, c.surfaceHigh, c.onSurface);
        applySystemBars(false);
    }

    private void applySystemBars(boolean force) {
        if (morphProgress >= 0f || closing) {
            return;
        }
        boolean light = colors.lightStatusBar();
        if (!force && lightBars != null && lightBars == light) {
            return;
        }
        lightBars = light;
        setBars(light, light);
    }

    private void setBars(boolean lightStatus, boolean lightNav) {
        if (getWindow() != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), lightStatus);
            AndroidUtilities.setLightNavigationBar(this, lightNav);
        }
        if (Build.VERSION.SDK_INT >= 26) {
            AndroidUtilities.setLightStatusBar(container, lightStatus);
            AndroidUtilities.setLightNavigationBar(container, lightNav);
        }
    }

    private class PlayerLayout extends ViewGroup {

        private int coverSize;
        private int stageHeight;

        PlayerLayout(Context context) {
            super(context);
            setClipChildren(false);
            setClipToPadding(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            int top = getStatusBarHeight();
            int bottom = getBottomInset();
            int side = dp(24);
            int contentW = Math.max(0, width - side * 2);
            int exactW = MeasureSpec.makeMeasureSpec(contentW, MeasureSpec.EXACTLY);
            header.measure(MeasureSpec.makeMeasureSpec(contentW + dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(56), MeasureSpec.EXACTLY));
            seekBar.measure(exactW, MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
            timeRow.measure(exactW, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            controls.measure(exactW, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            group.measure(exactW, MeasureSpec.makeMeasureSpec(dp(52), MeasureSpec.EXACTLY));
            titleRow.measure(exactW, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            bubble.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int fixed = dp(56) + dp(6) + dp(40) + timeRow.getMeasuredHeight() + dp(12) + controls.getMeasuredHeight() + dp(52) + dp(28) + dp(16);
            int available = height - top - bottom - fixed;
            int titleH = titleRow.getMeasuredHeight();
            coverSize = Math.max(dp(96), Math.min(contentW, available - dp(16) - dp(26) - titleH));
            stageHeight = dp(16) + coverSize + dp(26) + titleH;
            int coverSpec = MeasureSpec.makeMeasureSpec(coverSize, MeasureSpec.EXACTLY);
            cover.measure(coverSpec, coverSpec);
            lyricsPanel.measure(exactW, MeasureSpec.makeMeasureSpec(stageHeight, MeasureSpec.EXACTLY));
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int height = b - t;
            int top = getStatusBarHeight();
            int bottom = getBottomInset();
            int side = dp(24);
            int contentW = Math.max(0, width - side * 2);
            int y = top;
            header.layout(side - dp(12), y, side - dp(12) + header.getMeasuredWidth(), y + dp(56));
            y += dp(56);
            int stageTop = y;
            int coverLeft = (width - coverSize) / 2;
            cover.layout(coverLeft, y + dp(16), coverLeft + coverSize, y + dp(16) + coverSize);
            int titleTop = y + dp(16) + coverSize + dp(26);
            titleRow.layout(side, titleTop, side + contentW, titleTop + titleRow.getMeasuredHeight());
            lyricsPanel.layout(side, stageTop, side + contentW, stageTop + stageHeight);
            y = stageTop + stageHeight + dp(6);
            seekBar.layout(side, y, side + contentW, y + dp(40));
            int bubbleTop = y - dp(36);
            bubble.layout(side, bubbleTop, side + bubble.getMeasuredWidth(), bubbleTop + bubble.getMeasuredHeight());
            y += dp(40);
            timeRow.layout(side, y, side + contentW, y + timeRow.getMeasuredHeight());
            y += timeRow.getMeasuredHeight() + dp(12);
            controls.layout(side, y, side + contentW, y + controls.getMeasuredHeight());
            int groupTop = height - bottom - dp(28) - dp(52);
            group.layout(side, groupTop, side + contentW, groupTop + dp(52));
            if (seeking) {
                layoutBubble();
            }
        }
    }
}
