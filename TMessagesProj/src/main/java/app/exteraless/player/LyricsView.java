package app.exteraless.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.BreakIterator;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Locale;

import app.exteraless.appearance.AppearanceConfig;
import tw.nekomimi.nekogram.helpers.TypefaceHelper;

public class LyricsView extends FrameLayout {

    public static final int STATE_LYRICS = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_OFFER = 2;
    public static final int STATE_NOT_FOUND = 3;
    public static final int STATE_ERROR = 4;
    public static final int STATE_INSTRUMENTAL = 5;

    public interface Delegate {
        void onAction(int state);

        void onSeek(long ms);
    }

    private static final long FOLLOW_RESUME_MS = 750L;
    private static final int MAX_BLUR_DISTANCE = 4;
    private static final float BLUR_PER_LINE_DP = 1.25f;

    // Expressive Kinetic Wave constants (adapted from MD3 Expressive Google Sans Flex prototype)
    private static final String FLEX_FONT_ASSET = "fonts/google_sans_flex.ttf";
    private static final int QUANT_STEPS = 21;
    private static final float BASE_WGHT = 440f;
    private static final float PEAK_WGHT = 780f;
    private static final float BASE_WDTH = 100f;
    private static final float PEAK_WDTH = 116f;
    private static final float BASE_ROND = 15f;
    private static final float PEAK_ROND = 100f;
    private static final float PEAK_GRAD = 75f;
    private static final float OPTICAL_SIZE = 48f;
    private static final float ATTACK_LENGTH = 2.2f;
    private static final float TRAIL_LENGTH = 9.0f;
    private static final float DECAY_EXPONENT = 1.55f;
    private static final float WAVE_LIFT_DP = 2.0f;
    private static final float INACTIVE_CHAR_ALPHA = 0.28f;
    private static final float FALLBACK_PEAK_SCALE_BONUS = 0.055f;
    private static final int EXPRESSIVE_BLUR_BUCKETS = 3;
    private static final float[] EXPRESSIVE_BLUR_RADII_DP = {0.65f, 1.30f, 1.80f};

    private static final Object CACHE_LOCK = new Object();
    private static volatile boolean flexLoaded;
    private static volatile boolean flexVariableAxesSupported;
    private static final Typeface[] flexSteps = new Typeface[QUANT_STEPS];
    private static final Typeface[] fallbackSteps = new Typeface[QUANT_STEPS];
    private static Typeface flexBaseTypeface;
    private static float cachedBlurDensity = -1f;
    private static final BlurMaskFilter[] cachedBlurFilters = new BlurMaskFilter[EXPRESSIVE_BLUR_BUCKETS];

    private final FrameLayout listContainer;
    private final RecyclerListView list;
    private final LinearLayoutManager layoutManager;
    private final Adapter adapter = new Adapter();
    private final LinearLayout empty;
    private final FrameLayout tile;
    private final PlayerIcon tileIcon = PlayerIcon.stroke(PlayerIcon.NOTE, 28);
    private final TextView emptyTitle;
    private final TextView emptyText;
    private final TextView actionButton;
    private final RadialProgressView progress;
    private final GradientDrawable tileBg = new GradientDrawable();
    private final GradientDrawable buttonBg = new GradientDrawable();

    private final LinearLayout recenterBtn;
    private final View recenterArrow;
    private final TextView recenterLabel;
    private final GradientDrawable recenterBg = new GradientDrawable();
    private final Paint recenterArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path recenterArrowPath = new Path();
    private boolean recenterVisible;
    private boolean recenterPointUp;

    private final Paint fadePaint = new Paint();
    private LinearGradient topFade;
    private LinearGradient bottomFade;
    private Delegate delegate;
    private Lyrics lyrics;
    private int state = -1;
    private int active = -1;
    private long userScrollAt;
    private boolean userDragging;
    private boolean userScrolledAway;
    private int colorActive;
    private int colorAccent;
    private boolean lightMode;
    private long positionMs;
    private long positionAt;
    private boolean playingState = true;
    private boolean lyricsAreLatin = true;
    private boolean lastAppliedExpressive;
    private int lastAppliedTextSize = -1;
    private int lastAppliedAlignment = -1;
    private boolean lastAppliedBlur;
    private boolean lastAppliedSplitRoles;
    private boolean lastAppliedRomanize;

    private final Runnable resumeFollow = () -> {
        userScrollAt = 0;
        userScrolledAway = false;
        updateRecenterButton(true);
        updateBlur();
        scrollToActive(true);
    };

    public LyricsView(Context context) {
        super(context);
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        if (PlayerStyle.isExpressiveLyrics()) {
            Utilities.globalQueue.postRunnable(() -> ensureFlexCache(context));
        }

        listContainer = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                if (list.getVisibility() != VISIBLE || getWidth() == 0 || getHeight() == 0 || topFade == null) {
                    super.dispatchDraw(canvas);
                    return;
                }
                int w = getWidth();
                int h = getHeight();
                int save = canvas.saveLayer(0, 0, w, h, null);
                super.dispatchDraw(canvas);
                fadePaint.setShader(topFade);
                canvas.drawRect(0, 0, w, dp(32), fadePaint);
                fadePaint.setShader(bottomFade);
                canvas.drawRect(0, h - dp(40), w, h, fadePaint);
                canvas.restoreToCount(save);
            }

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (h > 0) {
                    topFade = new LinearGradient(0, 0, 0, dp(32), 0xff000000, 0x00000000, Shader.TileMode.CLAMP);
                    bottomFade = new LinearGradient(0, h - dp(40), 0, h, 0x00000000, 0xff000000, Shader.TileMode.CLAMP);
                }
            }
        };

        list = new RecyclerListView(context);
        layoutManager = new LinearLayoutManager(context);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);
        list.setItemAnimator(null);
        list.setClipToPadding(false);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(OVER_SCROLL_NEVER);
        list.setSelectorDrawableColor(0);
        list.setOnItemClickListener((view, position) -> {
            if (AppearanceConfig.playerLyricsTapToSeek() && lyrics != null && lyrics.synced && position >= 0 && position < lyrics.lines.size() && delegate != null) {
                userScrollAt = 0;
                userScrolledAway = false;
                removeCallbacks(resumeFollow);
                updateRecenterButton(true);
                updateBlur();
                delegate.onSeek(lyrics.lines.get(position).time);
            }
        });
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                userDragging = newState == RecyclerView.SCROLL_STATE_DRAGGING;
                if (userDragging && lyrics != null && lyrics.synced) {
                    userScrollAt = SystemClock.elapsedRealtime();
                    userScrolledAway = true;
                    removeCallbacks(resumeFollow);
                    updateBlur();
                    updateRecenterButton(true);
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE && userScrollAt != 0) {
                    userScrollAt = SystemClock.elapsedRealtime();
                    if (!isExpressiveActive()) {
                        removeCallbacks(resumeFollow);
                        postDelayed(resumeFollow, FOLLOW_RESUME_MS);
                    } else {
                        updateRecenterDirection();
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (userScrolledAway && recenterVisible) {
                    updateRecenterDirection();
                }
            }
        });
        listContainer.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(listContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        tile = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
                tileIcon.setBounds(0, 0, getWidth(), getHeight());
                tileIcon.draw(canvas);
            }
        };
        tile.setWillNotDraw(false);
        tileBg.setCornerRadius(dp(22));
        tile.setBackground(tileBg);
        empty.addView(tile, LayoutHelper.createLinear(64, 64, Gravity.CENTER_HORIZONTAL));
        progress = new RadialProgressView(context);
        progress.setSize(dp(36));
        empty.addView(progress, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL));
        emptyTitle = new TextView(context);
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        emptyTitle.setTypeface(AndroidUtilities.bold());
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
        emptyText = new TextView(context);
        emptyText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyText.setLineSpacing(0, 1.2f);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setMaxWidth(dp(260));
        empty.addView(emptyText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
        actionButton = new TextView(context);
        actionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        actionButton.setTypeface(AndroidUtilities.bold());
        actionButton.setGravity(Gravity.CENTER);
        actionButton.setPadding(dp(20), 0, dp(20), 0);
        buttonBg.setCornerRadius(dp(22));
        actionButton.setBackground(buttonBg);
        actionButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onAction(state);
            }
        });
        empty.addView(actionButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 44, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));
        addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 0, 16, 0));

        recenterBtn = new LinearLayout(context);
        recenterBtn.setOrientation(LinearLayout.HORIZONTAL);
        recenterBtn.setGravity(Gravity.CENTER_VERTICAL);
        recenterBtn.setPadding(dp(14), 0, dp(16), 0);
        recenterBtn.setElevation(dp(4));
        recenterBg.setCornerRadius(dp(18));
        recenterBtn.setBackground(recenterBg);
        recenterBtn.setVisibility(GONE);
        recenterBtn.setAlpha(0f);

        recenterArrowPaint.setStyle(Paint.Style.STROKE);
        recenterArrowPaint.setStrokeCap(Paint.Cap.ROUND);
        recenterArrowPaint.setStrokeJoin(Paint.Join.ROUND);
        recenterArrowPaint.setStrokeWidth(dp(1.8f));
        recenterArrow = new View(context) {
            @Override
            protected void onDraw(@NonNull Canvas canvas) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float d = dp(4.5f);
                recenterArrowPath.rewind();
                recenterArrowPath.moveTo(cx, cy - d);
                recenterArrowPath.lineTo(cx, cy + d);
                recenterArrowPath.moveTo(cx - d * 0.85f, cy + d * 0.15f);
                recenterArrowPath.lineTo(cx, cy + d);
                recenterArrowPath.lineTo(cx + d * 0.85f, cy + d * 0.15f);
                canvas.drawPath(recenterArrowPath, recenterArrowPaint);
            }
        };
        recenterBtn.addView(recenterArrow, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        recenterLabel = new TextView(context);
        recenterLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        recenterLabel.setTypeface(AndroidUtilities.bold());
        recenterLabel.setSingleLine(true);
        recenterLabel.setText(LocaleController.getString(R.string.OEPlayerLyricsRecenter));
        recenterBtn.addView(recenterLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        recenterBtn.setOnClickListener(v -> {
            userScrolledAway = false;
            userScrollAt = 0;
            removeCallbacks(resumeFollow);
            updateRecenterButton(true);
            updateBlur();
            scrollToActive(true);
        });
        addView(recenterBtn, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 16, 0, 16, 12));

        snapshotStyleConfig();
        playingState = !MediaController.getInstance().isMessagePaused();
    }

    private boolean isExpressiveActive() {
        return PlayerStyle.isExpressiveLyrics() && lyricsAreLatin;
    }

    private void snapshotStyleConfig() {
        lastAppliedExpressive = isExpressiveActive();
        lastAppliedTextSize = AppearanceConfig.playerLyricsTextSize();
        lastAppliedAlignment = AppearanceConfig.playerLyricsAlignment();
        lastAppliedBlur = AppearanceConfig.playerLyricsBlur();
        lastAppliedSplitRoles = AppearanceConfig.playerLyricsSplitRoles();
        lastAppliedRomanize = AppearanceConfig.playerLyricsRomanize();
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
    }

    public void setColors(PlayerColors c) {
        colorActive = c.onSurface;
        colorAccent = c.primary != 0 ? c.primary : c.onSurface;
        lightMode = !c.dark;
        tileBg.setColor(c.secondaryContainer);
        tileIcon.setColor(c.onSecondaryContainer);
        tile.invalidate();
        emptyTitle.setTextColor(c.onSurface);
        emptyText.setTextColor(c.onSurfaceVariant);
        actionButton.setTextColor(c.primary);
        buttonBg.setStroke(dp(1), c.outlineVariant);
        buttonBg.setColor(0);
        progress.setProgressColor(c.primary);
        recenterBg.setColor(c.primaryContainer);
        recenterBg.setStroke(dp(1), c.outlineVariant);
        recenterLabel.setTextColor(c.onPrimaryContainer);
        recenterArrowPaint.setColor(c.onPrimaryContainer);
        recenterArrow.invalidate();
        for (int i = 0; i < list.getChildCount(); i++) {
            list.getChildAt(i).invalidate();
        }
    }

    public void setPlaying(boolean playing) {
        if (playingState == playing) {
            return;
        }
        if (!playing) {
            positionMs = currentMs();
        }
        playingState = playing;
        positionAt = SystemClock.elapsedRealtime();
        for (int i = 0; i < list.getChildCount(); i++) {
            list.getChildAt(i).invalidate();
        }
    }

    public void refreshStyle() {
        lyricsAreLatin = lyrics == null || PlayerStyle.isLatinLyrics(lyrics);
        boolean exp = isExpressiveActive();
        int sz = AppearanceConfig.playerLyricsTextSize();
        int align = AppearanceConfig.playerLyricsAlignment();
        boolean blur = AppearanceConfig.playerLyricsBlur();
        boolean split = AppearanceConfig.playerLyricsSplitRoles();
        boolean rom = AppearanceConfig.playerLyricsRomanize();
        if (exp == lastAppliedExpressive && sz == lastAppliedTextSize && align == lastAppliedAlignment
                && blur == lastAppliedBlur && split == lastAppliedSplitRoles && rom == lastAppliedRomanize) {
            return;
        }
        snapshotStyleConfig();
        if (exp) {
            ensureFlexCache(getContext());
        } else if (userScrolledAway) {
            userScrolledAway = false;
            userScrollAt = 0;
            updateRecenterButton(false);
            updateBlur();
        }
        notifyLyricsChanged();
    }

    public boolean canScrollUp() {
        return list.getVisibility() == VISIBLE && list.canScrollVertically(-1);
    }

    public int getState() {
        return state;
    }

    public Lyrics getLyrics() {
        return lyrics;
    }

    public void showState(int newState) {
        if (state == newState && (newState != STATE_LYRICS || lyrics != null)) {
            if (newState == STATE_LYRICS) {
                list.setVisibility(VISIBLE);
                listContainer.setVisibility(VISIBLE);
                empty.setVisibility(GONE);
                updateRecenterButton(false);
            }
            return;
        }
        state = newState;
        if (newState == STATE_LYRICS) {
            list.setVisibility(VISIBLE);
            listContainer.setVisibility(VISIBLE);
            empty.setVisibility(GONE);
            updateRecenterButton(false);
            return;
        }
        lyrics = null;
        active = -1;
        userScrolledAway = false;
        userScrollAt = 0;
        removeCallbacks(resumeFollow);
        updateRecenterButton(false);
        adapter.notifyDataSetChanged();
        list.setVisibility(GONE);
        listContainer.setVisibility(GONE);
        empty.setVisibility(VISIBLE);
        boolean loading = newState == STATE_LOADING;
        progress.setVisibility(loading ? VISIBLE : GONE);
        tile.setVisibility(loading ? GONE : VISIBLE);
        String title;
        String text;
        String action = null;
        if (loading) {
            title = null;
            text = LocaleController.getString(R.string.OEPlayerLyricsSearching);
        } else if (newState == STATE_OFFER) {
            title = LocaleController.getString(R.string.OEPlayerNoLyricsTitle);
            text = LocaleController.getString(R.string.OEPlayerNoLyricsText);
            action = LocaleController.getString(R.string.OEPlayerFindLyrics);
        } else if (newState == STATE_NOT_FOUND) {
            title = LocaleController.getString(R.string.OEPlayerLyricsNotFound);
            text = LocaleController.getString(R.string.OEPlayerLyricsNotFoundText);
        } else if (newState == STATE_ERROR) {
            title = LocaleController.getString(R.string.OEPlayerLyricsError);
            text = LocaleController.getString(R.string.OEPlayerLyricsErrorText);
            action = LocaleController.getString(R.string.OEPlayerRetry);
        } else {
            title = LocaleController.getString(R.string.OEPlayerInstrumental);
            text = LocaleController.getString(R.string.OEPlayerInstrumentalText);
        }
        emptyTitle.setText(title);
        emptyTitle.setVisibility(title == null ? GONE : VISIBLE);
        emptyText.setText(text);
        actionButton.setText(action);
        actionButton.setVisibility(action == null ? GONE : VISIBLE);
    }

    public void setLyrics(Lyrics value, long positionMs) {
        if (state == STATE_LYRICS && lyrics == value && value != null) {
            setPosition(positionMs);
            return;
        }
        lyrics = value;
        lyricsAreLatin = value == null || PlayerStyle.isLatinLyrics(value);
        snapshotStyleConfig();
        if (isExpressiveActive()) {
            ensureFlexCache(getContext());
        }
        state = STATE_LYRICS;
        list.setVisibility(VISIBLE);
        listContainer.setVisibility(VISIBLE);
        empty.setVisibility(GONE);
        active = value != null ? value.indexAt(positionMs) : -1;
        this.positionMs = positionMs;
        positionAt = SystemClock.elapsedRealtime();
        playingState = !MediaController.getInstance().isMessagePaused();
        userScrollAt = 0;
        userScrolledAway = false;
        removeCallbacks(resumeFollow);
        updateRecenterButton(false);
        applyPadding();
        adapter.notifyDataSetChanged();
        list.post(() -> scrollToActive(false));
    }

    public void notifyLyricsChanged() {
        applyPadding();
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof LineView) {
                ((LineView) child).resetLayout();
                int pos = list.getChildAdapterPosition(child);
                if (pos >= 0) {
                    ((LineView) child).setRole(roleFor(pos), false);
                    ((LineView) child).setBlur(blurFor(pos));
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    public void setPosition(long ms) {
        if (lyrics == null || !lyrics.synced) {
            return;
        }
        long prevMs = currentMs();
        positionMs = ms;
        positionAt = SystemClock.elapsedRealtime();
        int index = lyrics.indexAt(ms);
        if (index == active) {
            if (isPlaying() || Math.abs(ms - prevMs) > 60L) {
                for (int i = 0; i < list.getChildCount(); i++) {
                    View child = list.getChildAt(i);
                    if (child instanceof LineView && ((LineView) child).role == LineView.ROLE_ACTIVE) {
                        child.invalidate();
                    }
                }
            }
            return;
        }
        int old = active;
        active = index;
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof LineView) {
                int pos = list.getChildAdapterPosition(child);
                ((LineView) child).setRole(roleFor(pos), true);
            }
        }
        boolean expressive = isExpressiveActive();
        boolean following = !userDragging && !userScrolledAway
                && (userScrollAt == 0 || (!expressive && SystemClock.elapsedRealtime() - userScrollAt > FOLLOW_RESUME_MS));
        if (following) {
            userScrollAt = 0;
            scrollToActive(old >= 0 && Math.abs(index - old) <= 3);
        } else if (userScrolledAway) {
            updateRecenterDirection();
        }
        updateBlur();
    }

    private long currentMs() {
        if (!isPlaying()) {
            return positionMs;
        }
        float speed = MediaController.getInstance().getPlaybackSpeed(true);
        if (speed <= 0f) {
            speed = 1f;
        }
        long elapsed = Math.max(0L, SystemClock.elapsedRealtime() - positionAt);
        return positionMs + Math.round(Math.min(2000L, elapsed) * speed);
    }

    private boolean isPlaying() {
        return playingState && !MediaController.getInstance().isMessagePaused();
    }

    private float blurFor(int position) {
        if (lyrics == null || !lyrics.synced || active < 0 || userDragging || userScrollAt != 0 || userScrolledAway) {
            return 0f;
        }
        if (isExpressiveActive()) {
            int dist = position - active;
            if (dist <= 1) {
                return 0f;
            }
            return Math.min(EXPRESSIVE_BLUR_BUCKETS, dist - 1);
        }
        int distance = Math.min(Math.abs(position - active), MAX_BLUR_DISTANCE);
        return dp(BLUR_PER_LINE_DP) * distance;
    }

    private void updateBlur() {
        for (int i = 0; i < list.getChildCount(); i++) {
            View child = list.getChildAt(i);
            if (child instanceof LineView) {
                ((LineView) child).setBlur(blurFor(list.getChildAdapterPosition(child)));
            }
        }
    }

    private void updateRecenterButton(boolean animated) {
        boolean show = state == STATE_LYRICS && lyrics != null && lyrics.synced && userScrolledAway && active >= 0;
        if (show) {
            updateRecenterDirection();
        }
        if (recenterVisible == show) {
            return;
        }
        recenterVisible = show;
        recenterBtn.animate().cancel();
        if (!animated) {
            recenterBtn.setVisibility(show ? VISIBLE : GONE);
            recenterBtn.setAlpha(show ? 1f : 0f);
            recenterBtn.setTranslationY(show ? 0f : dp(10));
            recenterBtn.setScaleX(show ? 1f : 0.9f);
            recenterBtn.setScaleY(show ? 1f : 0.9f);
            return;
        }
        if (show) {
            recenterBtn.setVisibility(VISIBLE);
            recenterBtn.setAlpha(0f);
            recenterBtn.setTranslationY(dp(10));
            recenterBtn.setScaleX(0.9f);
            recenterBtn.setScaleY(0.9f);
            recenterBtn.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .withEndAction(null)
                    .start();
        } else {
            recenterBtn.animate()
                    .alpha(0f)
                    .translationY(dp(10))
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(180)
                    .withEndAction(() -> {
                        if (!recenterVisible) {
                            recenterBtn.setVisibility(GONE);
                        }
                    })
                    .start();
        }
    }

    private void updateRecenterDirection() {
        if (active < 0) {
            return;
        }
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        boolean up = false;
        if (first != RecyclerView.NO_POSITION) {
            if (active < first) {
                up = true;
            } else if (active <= last) {
                View av = layoutManager.findViewByPosition(active);
                if (av != null && av.getTop() < list.getPaddingTop() - dp(12)) {
                    up = true;
                }
            }
        }
        if (recenterPointUp != up) {
            recenterPointUp = up;
            recenterArrow.animate().rotation(up ? 180f : 0f).setDuration(200).start();
        }
    }

    private int roleFor(int position) {
        if (lyrics == null || !lyrics.synced) {
            return LineView.ROLE_PLAIN;
        }
        if (position == active) {
            return LineView.ROLE_ACTIVE;
        }
        return position < active ? LineView.ROLE_PAST : LineView.ROLE_FUTURE;
    }

    private void scrollToActive(boolean smooth) {
        if (lyrics == null || list.getHeight() == 0) {
            return;
        }
        if (!lyrics.synced || !AppearanceConfig.playerLyricsAutoScroll()) {
            return;
        }
        int target = Math.max(active, 0);
        if (target >= adapter.getItemCount()) {
            return;
        }
        int first = layoutManager.findFirstVisibleItemPosition();
        boolean canSmooth = smooth && (layoutManager.findViewByPosition(target) != null
                || (first != RecyclerView.NO_POSITION && Math.abs(target - first) <= 16));
        if (canSmooth) {
            LinearSmoothScroller scroller = new LinearSmoothScroller(getContext()) {
                @Override
                public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                    return boxStart - viewStart;
                }

                @Override
                protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                    return 400f / displayMetrics.densityDpi;
                }

                @Override
                protected int calculateTimeForDeceleration(int dx) {
                    return Math.max(380, Math.min(700, super.calculateTimeForDeceleration(dx)));
                }
            };
            scroller.setTargetPosition(target);
            layoutManager.startSmoothScroll(scroller);
        } else {
            list.stopScroll();
            layoutManager.scrollToPositionWithOffset(target, 0);
        }
    }

    private void applyPadding() {
        int h = getHeight();
        if (lyrics != null && !lyrics.synced) {
            list.setPadding(0, dp(8), 0, dp(24));
        } else if (h > 0) {
            int offset = Math.min(dp(136), Math.round(h * 0.34f));
            list.setPadding(0, offset, 0, Math.max(0, h - offset - dp(72)));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h != oldh) {
            applyPadding();
            list.post(() -> scrollToActive(false));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(resumeFollow);
    }

    private static void ensureFlexCache(Context context) {
        if (flexLoaded) {
            return;
        }
        synchronized (CACHE_LOCK) {
            if (flexLoaded) {
                return;
            }
            AssetManager assets = context.getApplicationContext().getAssets();
            Typeface base = null;
            try {
                base = Typeface.createFromAsset(assets, FLEX_FONT_ASSET);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            if (base == null) {
                base = TypefaceHelper.lyricsTypeface();
            }
            flexBaseTypeface = base;
            Typeface fallbackBase = TypefaceHelper.lyricsTypeface();
            if (fallbackBase == null) {
                fallbackBase = Typeface.DEFAULT;
            }
            for (int i = 0; i < QUANT_STEPS; i++) {
                float intensity = i / (float) (QUANT_STEPS - 1);
                float wght = BASE_WGHT + (PEAK_WGHT - BASE_WGHT) * intensity;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    fallbackSteps[i] = Typeface.create(fallbackBase, Math.round(wght), false);
                } else {
                    fallbackSteps[i] = wght >= 600f ? Typeface.create(fallbackBase, Typeface.BOLD) : fallbackBase;
                }
            }
            boolean varSupported = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                int axesTier = 0; // 0 = all 5 axes, 1 = wght+wdth+opsz, 2 = wght only
                for (int i = 0; i < QUANT_STEPS; i++) {
                    float intensity = i / (float) (QUANT_STEPS - 1);
                    float wght = BASE_WGHT + (PEAK_WGHT - BASE_WGHT) * intensity;
                    float wdth = BASE_WDTH + (PEAK_WDTH - BASE_WDTH) * intensity;
                    float rond = BASE_ROND + (PEAK_ROND - BASE_ROND) * intensity;
                    float grad = PEAK_GRAD * intensity;
                    Typeface built = null;
                    while (axesTier <= 2 && built == null) {
                        try {
                            Typeface.Builder builder = new Typeface.Builder(assets, FLEX_FONT_ASSET);
                            builder.setWeight(Math.round(wght));
                            builder.setItalic(false);
                            String settings;
                            if (axesTier == 0) {
                                settings = String.format(Locale.US,
                                        "'wght' %.1f, 'wdth' %.2f, 'ROND' %.1f, 'GRAD' %.1f, 'opsz' %.0f",
                                        wght, wdth, rond, grad, OPTICAL_SIZE);
                            } else if (axesTier == 1) {
                                settings = String.format(Locale.US,
                                        "'wght' %.1f, 'wdth' %.2f, 'opsz' %.0f",
                                        wght, wdth, OPTICAL_SIZE);
                            } else {
                                settings = String.format(Locale.US, "'wght' %.1f", wght);
                            }
                            builder.setFontVariationSettings(settings);
                            built = builder.build();
                        } catch (Throwable ignored) {
                            axesTier++;
                        }
                    }
                    if (built != null) {
                        flexSteps[i] = built;
                        if (axesTier <= 1) {
                            varSupported = true;
                        }
                    } else {
                        flexSteps[i] = wght >= 600f ? Typeface.create(base, Typeface.BOLD) : base;
                    }
                }
            } else {
                for (int i = 0; i < QUANT_STEPS; i++) {
                    float intensity = i / (float) (QUANT_STEPS - 1);
                    float wght = BASE_WGHT + (PEAK_WGHT - BASE_WGHT) * intensity;
                    flexSteps[i] = wght >= 600f ? Typeface.create(base, Typeface.BOLD) : base;
                }
            }
            flexVariableAxesSupported = varSupported;
            flexLoaded = true;
        }
    }

    private static void ensureBlurFilters(float density) {
        if (Math.abs(cachedBlurDensity - density) < 0.01f && cachedBlurFilters[0] != null) {
            return;
        }
        synchronized (CACHE_LOCK) {
            if (Math.abs(cachedBlurDensity - density) < 0.01f && cachedBlurFilters[0] != null) {
                return;
            }
            cachedBlurDensity = density;
            for (int i = 0; i < EXPRESSIVE_BLUR_BUCKETS; i++) {
                float radiusPx = Math.max(0.5f, EXPRESSIVE_BLUR_RADII_DP[i] * density);
                cachedBlurFilters[i] = new BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL);
            }
        }
    }

    private static BlurMaskFilter expressiveBlurForBucket(int bucket, float density) {
        if (bucket <= 0) {
            return null;
        }
        ensureBlurFilters(density);
        int idx = Math.min(EXPRESSIVE_BLUR_BUCKETS - 1, bucket - 1);
        return cachedBlurFilters[idx];
    }

    private static float computeAxisIntensity(float delta) {
        if (delta < 0f) {
            if (delta <= -ATTACK_LENGTH) {
                return 0f;
            }
            float t = (delta + ATTACK_LENGTH) / ATTACK_LENGTH;
            return 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
        }
        if (delta >= TRAIL_LENGTH) {
            return 0f;
        }
        float normalized = 1f - delta / TRAIL_LENGTH;
        return (float) Math.pow(normalized, DECAY_EXPONENT);
    }

    private static float computeColorMix(float delta) {
        if (delta <= -1.1f) {
            return 0f;
        }
        if (delta >= 0.2f) {
            return 1f;
        }
        float t = (delta + 1.1f) / 1.3f;
        return 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
    }

    private static boolean containsComplexJoining(CharSequence s, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = s.charAt(i);
            if ((c >= 0x0600 && c <= 0x06FF)
                    || (c >= 0x0750 && c <= 0x077F)
                    || (c >= 0x08A0 && c <= 0x08FF)
                    || (c >= 0x0900 && c <= 0x0EFF)
                    || (c >= 0xFB50 && c <= 0xFDFF)
                    || (c >= 0xFE70 && c <= 0xFEFF)) {
                return true;
            }
        }
        return false;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return lyrics != null && lyrics.synced;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LineView view = new LineView(parent.getContext());
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            LineView view = (LineView) holder.itemView;
            Lyrics.Line line = lyrics.lines.get(position);
            String text = line.text;
            view.index = position;
            String romanized = null;
            if (AppearanceConfig.playerLyricsRomanize()) {
                if (!TextUtils.isEmpty(line.romanized)) {
                    romanized = line.romanized;
                } else if (!TextUtils.isEmpty(text)) {
                    String rom = RomanizeHelper.hasCyrillic(text)
                            ? RomanizeHelper.romanizeCyrillic(text)
                            : RomanizeHelper.romanize(text);
                    if (!TextUtils.isEmpty(rom) && !rom.equals(text)) {
                        romanized = rom;
                    }
                }
            }
            String translation = (lyrics != null && lyrics.showTranslation) ? line.translation : null;
            String subText;
            if (!TextUtils.isEmpty(romanized) && !TextUtils.isEmpty(translation)) {
                subText = romanized.trim() + "\n" + translation.trim();
            } else if (!TextUtils.isEmpty(romanized)) {
                subText = romanized.trim();
            } else if (!TextUtils.isEmpty(translation)) {
                subText = translation.trim();
            } else {
                subText = null;
            }
            view.setText(TextUtils.isEmpty(text) ? (lyrics.synced ? "" : "♪") : text, !lyrics.synced, line.isBacking, subText);
            view.setRole(roleFor(position), false);
            view.setBlur(blurFor(position));
        }

        @Override
        public int getItemCount() {
            return lyrics == null ? 0 : lyrics.lines.size();
        }
    }

    private static final class VisualUnit {
        int start;
        int end;
        int ctxStart;
        int ctxEnd;
        boolean isSpace;
        boolean isRtl;
        boolean isPrimaryGlyph;
        int logicalIndex;
        float restingWidth;
        float[] stepWidths;
        float frameIntensity;
        int frameStep;
        float frameColorMix;
    }

    private static final class VisualRow {
        String text;
        boolean hasComplexJoining;
        boolean hasNonPrimaryGlyph;
        final ArrayList<VisualUnit> units = new ArrayList<>();
        float restingWholeWidth;
        float height;
        float baselineOffset;
    }

    private class LineView extends View {

        static final int ROLE_PAST = 0;
        static final int ROLE_ACTIVE = 1;
        static final int ROLE_FUTURE = 2;
        static final int ROLE_PLAIN = 3;

        private static final float OPACITY_FUTURE = 0.51f;
        private static final float OPACITY_PAST = 0.5f;
        private static final float EXPRESSIVE_OPACITY_PAST = 0.52f;
        private static final float EXPRESSIVE_SCALE_PAST = 0.95f;
        private static final float EXPRESSIVE_SCALE_FUTURE = 0.96f;
        private static final float UNFILLED_ALPHA = 0.45f;
        private static final float ACTIVE_SCALE = 1.05f;

        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint measurePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint glyphCheckPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix fillMatrix = new Matrix();
        private final Spring opacity = new Spring(OPACITY_FUTURE, 260f, 1f, 0.004f);
        private final Spring scale = new Spring(1f, 320f, 0.75f, 0.0005f);
        private int index = -1;
        private String text;
        private String translationText;
        private boolean plain;
        private boolean isBacking;
        private StaticLayout layout;
        private StaticLayout translationLayout;
        private ArrayList<VisualRow> expressiveRows;
        private int totalLogicalUnits;
        private boolean expressiveStepWidthsReady;
        private boolean expressiveBaseRtl;
        private int layoutWidth;
        private int role = ROLE_FUTURE;
        private long lastFrame;
        private float blur = -1f;
        private int expressiveBlurBucket;
        private int lastTextSize = -1;
        private int lastAlignment = -1;
        private boolean lastExpressive;
        private boolean lastSplitRoles;

        void resetLayout() {
            layout = null;
            translationLayout = null;
            expressiveRows = null;
            expressiveStepWidthsReady = false;
            layoutWidth = -1;
            lastTextSize = -1;
            lastAlignment = -1;
            requestLayout();
        }

        LineView(Context context) {
            super(context);
            paint.setTypeface(TypefaceHelper.lyricsTypeface());
        }

        void setText(String value, boolean isPlain, boolean backing, String translation) {
            String displayText = value;
            boolean splitRoles = AppearanceConfig.playerLyricsSplitRoles();
            if (backing && splitRoles && !TextUtils.isEmpty(displayText)) {
                if (!displayText.startsWith("(") && !displayText.endsWith(")")) {
                    displayText = "(" + displayText + ")";
                }
            }
            String displayTranslation = translation != null ? translation.trim() : null;
            if (TextUtils.isEmpty(displayTranslation)) {
                displayTranslation = null;
            }
            int currentTextSize = AppearanceConfig.playerLyricsTextSize();
            int currentAlignment = AppearanceConfig.playerLyricsAlignment();
            boolean currentExpressive = isExpressiveActive();
            if (!TextUtils.equals(text, displayText) || !TextUtils.equals(translationText, displayTranslation)
                    || plain != isPlain || isBacking != backing || lastTextSize != currentTextSize
                    || lastAlignment != currentAlignment || lastExpressive != currentExpressive
                    || lastSplitRoles != splitRoles) {
                lastTextSize = currentTextSize;
                lastAlignment = currentAlignment;
                lastExpressive = currentExpressive;
                lastSplitRoles = splitRoles;
                text = displayText;
                translationText = displayTranslation;
                plain = isPlain;
                isBacking = backing;
                layout = null;
                translationLayout = null;
                expressiveRows = null;
                expressiveStepWidthsReady = false;
                layoutWidth = -1;
                requestLayout();
            }
        }

        void setBlur(float value) {
            if (isExpressiveActive()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0f) {
                    setRenderEffect(null);
                }
                blur = 0f;
                int bucket = AppearanceConfig.playerLyricsBlur() ? Math.round(value) : 0;
                if (expressiveBlurBucket != bucket) {
                    expressiveBlurBucket = bucket;
                    invalidate();
                }
                return;
            }
            expressiveBlurBucket = 0;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return;
            }
            if (!AppearanceConfig.playerLyricsBlur()) {
                value = 0f;
            }
            if (value == blur) {
                return;
            }
            blur = value;
            setRenderEffect(value > 0f ? RenderEffect.createBlurEffect(value, value, Shader.TileMode.DECAL) : null);
        }

        void setRole(int value, boolean animated) {
            role = value;
            boolean expressive = isExpressiveActive();
            float targetOpacity;
            float targetScale;
            if (expressive) {
                if (value == ROLE_PAST) {
                    targetOpacity = EXPRESSIVE_OPACITY_PAST;
                    targetScale = EXPRESSIVE_SCALE_PAST;
                } else if (value == ROLE_FUTURE) {
                    int dist = active >= 0 ? Math.max(1, index - active) : 1;
                    targetOpacity = Math.max(0.18f, 0.58f - dist * 0.14f);
                    targetScale = EXPRESSIVE_SCALE_FUTURE;
                } else {
                    targetOpacity = 1f;
                    targetScale = 1f;
                }
            } else {
                targetOpacity = value == ROLE_PAST ? OPACITY_PAST : value == ROLE_FUTURE ? OPACITY_FUTURE : 1f;
                targetScale = value == ROLE_ACTIVE ? ACTIVE_SCALE : 1f;
            }
            if (animated) {
                opacity.target = targetOpacity;
                scale.target = targetScale;
                lastFrame = 0;
            } else {
                opacity.snap(targetOpacity);
                scale.snap(targetScale);
            }
            invalidate();
        }

        private boolean useExpressiveForLine() {
            return isExpressiveActive() && (TextUtils.isEmpty(text) || PlayerStyle.isLatinText(text));
        }

        private boolean isInterlude() {
            return !plain && TextUtils.isEmpty(text);
        }

        private float textSize() {
            int sizeMode = AppearanceConfig.playerLyricsTextSize();
            int sp = sizeMode == 0 ? 24 : sizeMode == 2 ? 32 : 28;
            float size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, plain ? (sp - 8) : sp, getResources().getDisplayMetrics());
            if (isBacking && AppearanceConfig.playerLyricsSplitRoles()) {
                size *= 0.88f;
            }
            return size;
        }

        private void ensureLayout(int width) {
            boolean expressive = useExpressiveForLine();
            if (layoutWidth == width && ((expressive && expressiveRows != null) || (!expressive && layout != null))) {
                return;
            }
            layoutWidth = width;
            float sz = textSize();
            int glowPad = dp(8);
            int alignMode = AppearanceConfig.playerLyricsAlignment();
            Layout.Alignment alignment;
            if (alignMode == 0) {
                alignment = Layout.Alignment.ALIGN_CENTER;
            } else if (isBacking && AppearanceConfig.playerLyricsSplitRoles()) {
                alignment = Layout.Alignment.ALIGN_OPPOSITE;
            } else {
                alignment = Layout.Alignment.ALIGN_NORMAL;
            }

            if (expressive) {
                ensureFlexCache(getContext());
                int contentWidth = Math.max(1, width - glowPad * 2);
                buildExpressiveRows(contentWidth, sz);
                if (!TextUtils.isEmpty(translationText)) {
                    paint.setTypeface(PlayerStyle.isLatinText(translationText) ? flexSteps[0] : TypefaceHelper.lyricsTypeface());
                    paint.setFontFeatureSettings(null);
                    paint.setTextSize(sz * 0.68f);
                    translationLayout = StaticLayout.Builder.obtain(translationText, 0, translationText.length(), paint, contentWidth)
                            .setAlignment(alignment)
                            .setLineSpacing(0, 1.15f)
                            .setIncludePad(false)
                            .build();
                } else {
                    translationLayout = null;
                }
                layout = null;
                return;
            }

            expressiveRows = null;
            expressiveStepWidthsReady = false;
            paint.setTypeface(TypefaceHelper.lyricsTypeface());
            paint.setFontFeatureSettings(null);
            paint.setTextSize(sz);
            int textWidth = Math.max(1, (int) ((width - glowPad * 2) / ACTIVE_SCALE));
            layout = StaticLayout.Builder.obtain(text == null ? "" : text, 0, text == null ? 0 : text.length(), paint, textWidth)
                    .setAlignment(alignment)
                    .setLineSpacing(0, 1.18f)
                    .setIncludePad(false)
                    .build();

            if (!TextUtils.isEmpty(translationText)) {
                paint.setTextSize(sz * 0.68f);
                translationLayout = StaticLayout.Builder.obtain(translationText, 0, translationText.length(), paint, textWidth)
                        .setAlignment(alignment)
                        .setLineSpacing(0, 1.15f)
                        .setIncludePad(false)
                        .build();
                paint.setTextSize(sz);
            } else {
                translationLayout = null;
            }
        }

        private void buildExpressiveRows(int contentWidth, float sz) {
            expressiveRows = new ArrayList<>();
            expressiveStepWidthsReady = false;
            totalLogicalUnits = 0;
            String src = text == null ? "" : text;
            if (src.isEmpty()) {
                expressiveBaseRtl = LocaleController.isRTL;
                return;
            }

            Bidi paraBidi = new Bidi(src, Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
            expressiveBaseRtl = !paraBidi.baseIsLeftToRight();

            measurePaint.setTextSize(sz);
            measurePaint.setTypeface(flexSteps[QUANT_STEPS - 1]);
            measurePaint.setFontFeatureSettings("'kern' 0, 'liga' 0");
            Paint.FontMetrics fm = measurePaint.getFontMetrics();
            float rowHeight = Math.max(sz * 1.32f, (fm.descent - fm.ascent) * 1.16f);
            float baselineOffset = (rowHeight - (fm.ascent + fm.descent)) * 0.5f;

            glyphCheckPaint.setTextSize(sz);
            glyphCheckPaint.setTypeface(flexBaseTypeface != null ? flexBaseTypeface : flexSteps[0]);

            float spacePeakWidth = measurePaint.measureText(" ") * 1.04f;
            int[] logicalCounter = {0};
            String[] hardLines = src.split("\n", -1);
            for (String hardLine : hardLines) {
                ArrayList<String> tokens = tokenizeWords(hardLine);
                if (tokens.isEmpty()) {
                    continue;
                }
                ArrayList<String> expandedTokens = expandOverwideTokens(tokens, contentWidth);
                packTokensIntoRows(expandedTokens, contentWidth, spacePeakWidth, rowHeight, baselineOffset, sz, logicalCounter);
            }
        }

        private ArrayList<String> tokenizeWords(String line) {
            ArrayList<String> tokens = new ArrayList<>();
            int len = line.length();
            int i = 0;
            while (i < len) {
                while (i < len && Character.isWhitespace(line.charAt(i))) {
                    i++;
                }
                int start = i;
                while (i < len && !Character.isWhitespace(line.charAt(i))) {
                    i++;
                }
                if (i > start) {
                    tokens.add(line.substring(start, i));
                }
            }
            return tokens;
        }

        private float measureTokenPeak(String token) {
            boolean complex = containsComplexJoining(token, 0, token.length());
            if (complex) {
                measurePaint.setFontFeatureSettings(null);
                float w = measurePaint.measureText(token);
                measurePaint.setFontFeatureSettings("'kern' 0, 'liga' 0");
                return w * 1.04f * (1f + FALLBACK_PEAK_SCALE_BONUS);
            }
            float sum = 0f;
            BreakIterator bi = BreakIterator.getCharacterInstance();
            bi.setText(token);
            int prev = bi.first();
            for (int next = bi.next(); next != BreakIterator.DONE; prev = next, next = bi.next()) {
                boolean primary = flexVariableAxesSupported && isPrimaryCluster(token, prev, next);
                float w = measurePaint.measureText(token, prev, next);
                sum += w * 1.04f * (primary ? 1f : (1f + FALLBACK_PEAK_SCALE_BONUS));
            }
            return sum;
        }

        private boolean isPrimaryCluster(CharSequence seq, int start, int end) {
            if (end - start <= 0) {
                return true;
            }
            if (end - start == 1 && Character.isWhitespace(seq.charAt(start))) {
                return true;
            }
            String sub = seq.subSequence(start, end).toString();
            return glyphCheckPaint.hasGlyph(sub);
        }

        private ArrayList<String> expandOverwideTokens(ArrayList<String> tokens, int contentWidth) {
            ArrayList<String> result = new ArrayList<>(tokens.size());
            for (String token : tokens) {
                if (measureTokenPeak(token) <= contentWidth || containsComplexJoining(token, 0, token.length())) {
                    result.add(token);
                    continue;
                }
                BreakIterator bi = BreakIterator.getCharacterInstance();
                bi.setText(token);
                int chunkStart = bi.first();
                float chunkW = 0f;
                int prev = chunkStart;
                for (int next = bi.next(); next != BreakIterator.DONE; prev = next, next = bi.next()) {
                    boolean primary = flexVariableAxesSupported && isPrimaryCluster(token, prev, next);
                    float cw = measurePaint.measureText(token, prev, next) * 1.04f * (primary ? 1f : (1f + FALLBACK_PEAK_SCALE_BONUS));
                    if (prev > chunkStart && chunkW + cw > contentWidth) {
                        result.add(token.substring(chunkStart, prev));
                        chunkStart = prev;
                        chunkW = cw;
                    } else {
                        chunkW += cw;
                    }
                }
                if (chunkStart < token.length()) {
                    result.add(token.substring(chunkStart));
                }
            }
            return result;
        }

        private void packTokensIntoRows(ArrayList<String> tokens, int contentWidth, float spacePeakWidth,
                                        float rowHeight, float baselineOffset, float sz, int[] logicalCounter) {
            ArrayList<String> currentRow = new ArrayList<>();
            float currentWidth = 0f;
            for (String word : tokens) {
                float wordPeak = measureTokenPeak(word);
                float added = currentRow.isEmpty() ? wordPeak : (spacePeakWidth + wordPeak);
                if (!currentRow.isEmpty() && currentWidth + added > contentWidth) {
                    expressiveRows.add(finalizeVisualRow(currentRow, rowHeight, baselineOffset, sz, logicalCounter));
                    currentRow.clear();
                    currentRow.add(word);
                    currentWidth = wordPeak;
                } else {
                    currentRow.add(word);
                    currentWidth += added;
                }
            }
            if (!currentRow.isEmpty()) {
                expressiveRows.add(finalizeVisualRow(currentRow, rowHeight, baselineOffset, sz, logicalCounter));
            }
            totalLogicalUnits = logicalCounter[0];
        }

        private VisualRow finalizeVisualRow(ArrayList<String> words, float rowHeight, float baselineOffset,
                                            float sz, int[] logicalCounter) {
            VisualRow row = new VisualRow();
            row.height = rowHeight;
            row.baselineOffset = baselineOffset;
            StringBuilder sb = new StringBuilder();
            ArrayList<int[]> wordRanges = new ArrayList<>(words.size());
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                int ws = sb.length();
                sb.append(words.get(i));
                int we = sb.length();
                wordRanges.add(new int[]{ws, we});
            }
            row.text = sb.toString();
            row.hasComplexJoining = containsComplexJoining(row.text, 0, row.text.length());

            ArrayList<VisualUnit> logicalUnits = new ArrayList<>();
            BreakIterator bi = BreakIterator.getCharacterInstance();
            for (int wi = 0; wi < wordRanges.size(); wi++) {
                if (wi > 0) {
                    int spPos = wordRanges.get(wi)[0] - 1;
                    VisualUnit space = new VisualUnit();
                    space.start = spPos;
                    space.end = spPos + 1;
                    space.ctxStart = space.start;
                    space.ctxEnd = space.end;
                    space.isSpace = true;
                    space.isPrimaryGlyph = true;
                    space.logicalIndex = logicalCounter[0]++;
                    logicalUnits.add(space);
                }
                int ws = wordRanges.get(wi)[0];
                int we = wordRanges.get(wi)[1];
                if (containsComplexJoining(row.text, ws, we)) {
                    VisualUnit wordUnit = new VisualUnit();
                    wordUnit.start = ws;
                    wordUnit.end = we;
                    wordUnit.ctxStart = ws;
                    wordUnit.ctxEnd = we;
                    wordUnit.isSpace = false;
                    wordUnit.isPrimaryGlyph = false;
                    wordUnit.logicalIndex = logicalCounter[0]++;
                    logicalUnits.add(wordUnit);
                } else {
                    String wordSlice = row.text.substring(ws, we);
                    bi.setText(wordSlice);
                    int prev = bi.first();
                    for (int next = bi.next(); next != BreakIterator.DONE; prev = next, next = bi.next()) {
                        VisualUnit ch = new VisualUnit();
                        ch.start = ws + prev;
                        ch.end = ws + next;
                        ch.ctxStart = ws;
                        ch.ctxEnd = we;
                        ch.isSpace = false;
                        ch.isPrimaryGlyph = flexVariableAxesSupported && isPrimaryCluster(row.text, ch.start, ch.end);
                        ch.logicalIndex = logicalCounter[0]++;
                        logicalUnits.add(ch);
                    }
                }
            }

            Bidi bidi = new Bidi(row.text, expressiveBaseRtl ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_LEFT_TO_RIGHT);
            if (!bidi.isMixed() && bidi.isLeftToRight()) {
                row.units.addAll(logicalUnits);
            } else {
                int runCount = bidi.getRunCount();
                byte[] levels = new byte[runCount];
                Integer[] order = new Integer[runCount];
                for (int r = 0; r < runCount; r++) {
                    levels[r] = (byte) bidi.getRunLevel(r);
                    order[r] = r;
                }
                Bidi.reorderVisually(levels, 0, order, 0, runCount);
                for (int ri = 0; ri < runCount; ri++) {
                    int run = order[ri];
                    int rStart = bidi.getRunStart(run);
                    int rLimit = bidi.getRunLimit(run);
                    boolean runRtl = (bidi.getRunLevel(run) & 1) == 1;
                    if (!runRtl) {
                        for (int ui = 0; ui < logicalUnits.size(); ui++) {
                            VisualUnit u = logicalUnits.get(ui);
                            if (u.start >= rStart && u.start < rLimit) {
                                u.isRtl = false;
                                row.units.add(u);
                            }
                        }
                    } else {
                        for (int ui = logicalUnits.size() - 1; ui >= 0; ui--) {
                            VisualUnit u = logicalUnits.get(ui);
                            if (u.start >= rStart && u.start < rLimit) {
                                u.isRtl = true;
                                row.units.add(u);
                            }
                        }
                    }
                }
            }

            measurePaint.setTextSize(sz);
            measurePaint.setFontFeatureSettings(row.hasComplexJoining ? null : "'kern' 0, 'liga' 0");
            float sumResting = 0f;
            boolean hasNonPrimary = false;
            for (int ui = 0; ui < row.units.size(); ui++) {
                VisualUnit u = row.units.get(ui);
                if (!u.isSpace && !u.isPrimaryGlyph) {
                    hasNonPrimary = true;
                }
                measurePaint.setTypeface((u.isSpace || u.isPrimaryGlyph) ? flexSteps[0] : fallbackSteps[0]);
                u.restingWidth = measurePaint.measureText(row.text, u.start, u.end);
                sumResting += u.restingWidth;
            }
            row.hasNonPrimaryGlyph = hasNonPrimary;
            if (hasNonPrimary) {
                row.restingWholeWidth = sumResting;
            } else {
                measurePaint.setTypeface(flexSteps[0]);
                row.restingWholeWidth = measurePaint.measureText(row.text);
            }
            return row;
        }

        private void ensureActiveStepWidths() {
            if (expressiveStepWidthsReady || expressiveRows == null) {
                return;
            }
            float sz = textSize();
            measurePaint.setTextSize(sz);
            for (VisualRow row : expressiveRows) {
                for (int ui = 0; ui < row.units.size(); ui++) {
                    VisualUnit u = row.units.get(ui);
                    if (u.stepWidths == null || u.stepWidths.length != QUANT_STEPS) {
                        u.stepWidths = new float[QUANT_STEPS];
                    }
                }
            }
            for (int s = 0; s < QUANT_STEPS; s++) {
                float intensity = s / (float) (QUANT_STEPS - 1);
                float fallbackScale = 1f + FALLBACK_PEAK_SCALE_BONUS * intensity;
                for (VisualRow row : expressiveRows) {
                    measurePaint.setFontFeatureSettings(row.hasComplexJoining ? null : "'kern' 0, 'liga' 0");
                    for (int ui = 0; ui < row.units.size(); ui++) {
                        VisualUnit u = row.units.get(ui);
                        measurePaint.setTypeface((u.isSpace || u.isPrimaryGlyph) ? flexSteps[s] : fallbackSteps[s]);
                        float w = measurePaint.measureText(row.text, u.start, u.end);
                        if (!u.isSpace && !u.isPrimaryGlyph) {
                            w *= fallbackScale;
                        }
                        u.stepWidths[s] = w;
                    }
                }
            }
            expressiveStepWidthsReady = true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            ensureLayout(width);
            int pad = plain ? dp(6) : dp(10);
            int content;
            if (isInterlude()) {
                content = (int) (textSize() * 0.9f);
            } else if (useExpressiveForLine() && expressiveRows != null) {
                float hSum = 0f;
                for (int i = 0; i < expressiveRows.size(); i++) {
                    hSum += expressiveRows.get(i).height;
                }
                content = Math.round(hSum);
                if (translationLayout != null) {
                    content += dp(4) + translationLayout.getHeight();
                }
            } else {
                content = layout != null ? layout.getHeight() : 0;
                if (translationLayout != null) {
                    content += dp(4) + translationLayout.getHeight();
                }
            }
            setMeasuredDimension(width, content + pad * 2);
        }

        private float lineProgress() {
            if (lyrics == null || index < 0 || index >= lyrics.lines.size()) {
                return 0f;
            }
            long start = lyrics.lines.get(index).time;
            long end = index + 1 < lyrics.lines.size() ? lyrics.lines.get(index + 1).time : start + 4000;
            if (end <= start) {
                return 1f;
            }
            return Math.max(0f, Math.min(1f, (currentMs() - start) / (float) (end - start)));
        }

        private float expressiveCursorPos(int unitCount) {
            if (lyrics == null || index < 0 || index >= lyrics.lines.size() || unitCount <= 0) {
                return 0f;
            }
            long start = lyrics.lines.get(index).time;
            long nextTime = index + 1 < lyrics.lines.size() ? lyrics.lines.get(index + 1).time : start + 4500L;
            long rawDuration = Math.max(400L, nextTime - start);
            long maxPlausibleMs = Math.min(11000L, Math.max(3000L, unitCount * 220L + 1200L));
            float activeSpanMs = Math.max(350f, Math.min(rawDuration * 0.92f, (float) maxPlausibleMs));
            float elapsedMs = Math.max(0f, (float) (currentMs() - start));
            float rawProgress = elapsedMs / activeSpanMs;
            if (rawProgress <= 1f) {
                return rawProgress * unitCount;
            }
            float tailGraceMs = Math.max(350f, Math.min(900f, rawDuration - activeSpanMs));
            float tailFrac = Math.min(1f, (elapsedMs - activeSpanMs) / tailGraceMs);
            return unitCount + tailFrac * TRAIL_LENGTH;
        }

        private float glow(float t) {
            if (t < 0.15f) {
                return t / 0.15f;
            }
            if (t < 0.6f) {
                return 1f;
            }
            return Math.max(0f, 1f - (t - 0.6f) / 0.4f);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            long now = SystemClock.elapsedRealtime();
            float dt = lastFrame == 0 ? 0.016f : (now - lastFrame) / 1000f;
            lastFrame = now;
            boolean animating = opacity.step(dt);
            animating |= scale.step(dt);
            ensureLayout(getWidth());
            float alpha = Math.max(0f, Math.min(1f, opacity.value));
            int baseAlpha = colorActive >>> 24;
            int accentBaseAlpha = colorAccent >>> 24;
            boolean live = role == ROLE_ACTIVE && !plain;
            boolean trackExpressive = isExpressiveActive();
            boolean expressive = useExpressiveForLine();
            int pad = plain ? dp(6) : dp(10);
            int glowPad = dp(8);
            int textWidth = Math.max(1, getWidth() - glowPad * 2);
            int alignMode = AppearanceConfig.playerLyricsAlignment();
            boolean splitRoles = AppearanceConfig.playerLyricsSplitRoles();

            canvas.save();
            canvas.translate(glowPad, pad);
            float pivotY = (getHeight() - pad * 2) / 2f;
            float pivotX = 0f;
            if (trackExpressive) {
                if (alignMode == 0) {
                    pivotX = textWidth * 0.5f;
                } else {
                    boolean alignRight = expressiveBaseRtl ^ (isBacking && splitRoles);
                    pivotX = alignRight ? textWidth : 0f;
                }
            }
            canvas.scale(scale.value, scale.value, pivotX, pivotY);

            if (isInterlude()) {
                float progress = live ? lineProgress() : 0f;
                drawInterlude(canvas, live, progress, alpha, now, trackExpressive, textWidth, alignMode, splitRoles);
            } else if (expressive && expressiveRows != null) {
                drawExpressiveContent(canvas, live, alpha, baseAlpha, accentBaseAlpha, textWidth, alignMode, splitRoles, now);
            } else if (layout != null) {
                drawStandardContent(canvas, live, alpha, baseAlpha, trackExpressive);
            }

            if (!isInterlude() && translationLayout != null) {
                float mainHeight = (expressive && expressiveRows != null) ? totalExpressiveHeight() : (layout != null ? layout.getHeight() : 0);
                canvas.save();
                canvas.translate(0, mainHeight + dp(4));
                paint.setShader(null);
                paint.clearShadowLayer();
                paint.setFontFeatureSettings(null);
                if (trackExpressive && role == ROLE_FUTURE && expressiveBlurBucket > 0) {
                    paint.setMaskFilter(expressiveBlurForBucket(expressiveBlurBucket, getResources().getDisplayMetrics().density));
                } else {
                    paint.setMaskFilter(null);
                }
                if (trackExpressive && PlayerStyle.isLatinText(translationText)) {
                    ensureFlexCache(getContext());
                    paint.setTypeface(flexSteps[0]);
                } else {
                    paint.setTypeface(TypefaceHelper.lyricsTypeface());
                }
                paint.setTextSize(textSize() * 0.68f);
                int transAlpha = Math.round(alpha * baseAlpha * (trackExpressive && role == ROLE_ACTIVE ? 0.78f : 0.70f));
                paint.setColor(ColorUtils.setAlphaComponent(colorActive, transAlpha));
                translationLayout.draw(canvas);
                paint.setMaskFilter(null);
                canvas.restore();
            }
            canvas.restore();
            if (animating || (live && isPlaying())) {
                postInvalidateOnAnimation();
            } else {
                lastFrame = 0;
            }
        }

        private float totalExpressiveHeight() {
            if (expressiveRows == null) {
                return 0f;
            }
            float h = 0f;
            for (int i = 0; i < expressiveRows.size(); i++) {
                h += expressiveRows.get(i).height;
            }
            return h;
        }

        private void drawExpressiveContent(Canvas canvas, boolean live, float alpha, int baseAlpha, int accentBaseAlpha,
                                           int textWidth, int alignMode, boolean splitRoles, long now) {
            ensureFlexCache(getContext());
            paint.setMaskFilter(null);
            paint.setShader(null);
            paint.clearShadowLayer();
            paint.setTextSize(textSize());

            if (live) {
                ensureActiveStepWidths();
                float cursorPos = expressiveCursorPos(totalLogicalUnits);
                float inactiveAlphaRatio = lightMode ? 0.26f : INACTIVE_CHAR_ALPHA;
                int inactiveCol = ColorUtils.setAlphaComponent(colorActive, Math.round(baseAlpha * inactiveAlphaRatio));
                int accentCol = ColorUtils.setAlphaComponent(colorAccent, accentBaseAlpha);
                float liftDpPx = dp(WAVE_LIFT_DP);

                float rowY = 0f;
                for (int ri = 0; ri < expressiveRows.size(); ri++) {
                    VisualRow row = expressiveRows.get(ri);
                    paint.setFontFeatureSettings(row.hasComplexJoining ? null : "'kern' 0, 'liga' 0");
                    float rowWidthNow = 0f;
                    int unitCount = row.units.size();
                    for (int ui = 0; ui < unitCount; ui++) {
                        VisualUnit u = row.units.get(ui);
                        float delta = cursorPos - u.logicalIndex;
                        float intensity = computeAxisIntensity(delta);
                        int step = Math.max(0, Math.min(QUANT_STEPS - 1, Math.round(intensity * (QUANT_STEPS - 1))));
                        u.frameIntensity = intensity;
                        u.frameStep = step;
                        u.frameColorMix = computeColorMix(delta);
                        rowWidthNow += u.stepWidths[step];
                    }

                    float startX;
                    if (alignMode == 0) {
                        startX = (textWidth - rowWidthNow) * 0.5f;
                    } else {
                        boolean alignRight = expressiveBaseRtl ^ (isBacking && splitRoles);
                        startX = alignRight ? (textWidth - rowWidthNow) : 0f;
                    }

                    float x = startX;
                    float baseLineY = rowY + row.baselineOffset;
                    for (int ui = 0; ui < unitCount; ui++) {
                        VisualUnit u = row.units.get(ui);
                        float w = u.stepWidths[u.frameStep];
                        if (!u.isSpace) {
                            int blended = ColorUtils.blendARGB(inactiveCol, accentCol, u.frameColorMix);
                            if (alpha < 0.999f) {
                                blended = ColorUtils.setAlphaComponent(blended, Math.round(Color.alpha(blended) * alpha));
                            }
                            paint.setColor(blended);
                            paint.setTypeface(u.isPrimaryGlyph ? flexSteps[u.frameStep] : fallbackSteps[u.frameStep]);
                            float drawY = baseLineY - liftDpPx * u.frameIntensity;
                            if (!u.isPrimaryGlyph && u.frameIntensity > 0.005f) {
                                float s = 1f + FALLBACK_PEAK_SCALE_BONUS * u.frameIntensity;
                                float rawW = w / s;
                                float cx = x + w * 0.5f;
                                canvas.save();
                                canvas.scale(s, s, cx, drawY);
                                drawUnitText(canvas, row.text, u, x + (w - rawW) * 0.5f, drawY, paint);
                                canvas.restore();
                            } else {
                                drawUnitText(canvas, row.text, u, x, drawY, paint);
                            }
                        }
                        x += w;
                    }
                    rowY += row.height;
                }
                paint.setFontFeatureSettings(null);
                return;
            }

            // Past, future, or plain non-active expressive rows: resting typeface, zero per-char allocs
            if (role == ROLE_PAST) {
                paint.setColor(ColorUtils.setAlphaComponent(colorAccent, Math.round(alpha * accentBaseAlpha)));
                paint.setMaskFilter(null);
            } else if (role == ROLE_FUTURE) {
                paint.setColor(ColorUtils.setAlphaComponent(colorActive, Math.round(alpha * baseAlpha)));
                paint.setMaskFilter(expressiveBlurForBucket(expressiveBlurBucket, getResources().getDisplayMetrics().density));
            } else {
                paint.setColor(ColorUtils.setAlphaComponent(colorActive, Math.round(alpha * baseAlpha)));
                paint.setMaskFilter(null);
            }

            float rowY = 0f;
            for (int ri = 0; ri < expressiveRows.size(); ri++) {
                VisualRow row = expressiveRows.get(ri);
                paint.setFontFeatureSettings(row.hasComplexJoining ? null : "'kern' 0, 'liga' 0");
                float startX;
                if (alignMode == 0) {
                    startX = (textWidth - row.restingWholeWidth) * 0.5f;
                } else {
                    boolean alignRight = expressiveBaseRtl ^ (isBacking && splitRoles);
                    startX = alignRight ? (textWidth - row.restingWholeWidth) : 0f;
                }
                float baseLineY = rowY + row.baselineOffset;
                if (!row.hasNonPrimaryGlyph) {
                    paint.setTypeface(flexSteps[0]);
                    canvas.drawText(row.text, startX, baseLineY, paint);
                } else {
                    float x = startX;
                    int unitCount = row.units.size();
                    for (int ui = 0; ui < unitCount; ui++) {
                        VisualUnit u = row.units.get(ui);
                        if (!u.isSpace) {
                            paint.setTypeface(u.isPrimaryGlyph ? flexSteps[0] : fallbackSteps[0]);
                            drawUnitText(canvas, row.text, u, x, baseLineY, paint);
                        }
                        x += u.restingWidth;
                    }
                }
                rowY += row.height;
            }
            paint.setMaskFilter(null);
            paint.setFontFeatureSettings(null);
        }

        private void drawUnitText(Canvas canvas, String rowText, VisualUnit u, float x, float y, TextPaint p) {
            if (u.ctxStart != u.start || u.ctxEnd != u.end || u.isRtl) {
                canvas.drawTextRun(rowText, u.start, u.end, u.ctxStart, u.ctxEnd, x, y, u.isRtl, p);
            } else {
                canvas.drawText(rowText, u.start, u.end, x, y, p);
            }
        }

        private void drawStandardContent(Canvas canvas, boolean live, float alpha, int baseAlpha, boolean trackExpressive) {
            if (trackExpressive && role == ROLE_FUTURE && expressiveBlurBucket > 0) {
                paint.setMaskFilter(expressiveBlurForBucket(expressiveBlurBucket, getResources().getDisplayMetrics().density));
            } else {
                paint.setMaskFilter(null);
            }
            paint.setFontFeatureSettings(null);
            paint.setTypeface(TypefaceHelper.lyricsTypeface());
            paint.setTextSize(textSize());
            int highlightBase = trackExpressive ? colorAccent : colorActive;
            int highlightAlpha = highlightBase >>> 24;
            if (live) {
                float progress = lineProgress();
                int filled = ColorUtils.setAlphaComponent(highlightBase, (int) (alpha * highlightAlpha));
                float unfilledRatio = trackExpressive ? (lightMode ? 0.26f : INACTIVE_CHAR_ALPHA) : UNFILLED_ALPHA;
                int unfilled = ColorUtils.setAlphaComponent(colorActive, (int) (alpha * baseAlpha * unfilledRatio));
                float g = glow(progress);
                if (g > 0.01f) {
                    paint.setShadowLayer(dp(4 + 2 * g), 0, 0, ColorUtils.setAlphaComponent(highlightBase, (int) (g * 0.35f * highlightAlpha)));
                } else {
                    paint.clearShadowLayer();
                }
                int lineCount = layout.getLineCount();
                float totalWidth = 0f;
                for (int li = 0; li < lineCount; li++) {
                    totalWidth += Math.max(1f, layout.getLineWidth(li));
                }
                float targetX = progress * totalWidth;
                float accumulated = 0f;
                LinearGradient horizFill = new LinearGradient(0, 0, 1, 0, filled, unfilled, Shader.TileMode.CLAMP);
                for (int li = 0; li < lineCount; li++) {
                    float lineLeft = layout.getLineLeft(li);
                    float lineW = Math.max(1f, layout.getLineWidth(li));
                    int lineTop = layout.getLineTop(li);
                    int lineBottom = layout.getLineBottom(li);
                    float lineProg = Math.max(0f, Math.min(1f, (targetX - accumulated) / lineW));
                    accumulated += lineW;
                    canvas.save();
                    canvas.clipRect(-dp(8), lineTop - dp(4), getWidth() + dp(8), lineBottom + dp(4));
                    if (lineProg <= 0f) {
                        paint.setShader(null);
                        paint.setColor(unfilled);
                    } else if (lineProg >= 1f) {
                        paint.setShader(null);
                        paint.setColor(filled);
                    } else {
                        float feather = Math.max(dp(10), lineW * 0.14f);
                        fillMatrix.setScale(feather, 1f);
                        fillMatrix.postTranslate(lineLeft + lineProg * lineW - feather * 0.5f, 0f);
                        horizFill.setLocalMatrix(fillMatrix);
                        paint.setShader(horizFill);
                        paint.setColor(0xffffffff);
                    }
                    layout.draw(canvas);
                    canvas.restore();
                }
                paint.setShader(null);
                paint.clearShadowLayer();
            } else {
                paint.setShader(null);
                paint.clearShadowLayer();
                if (trackExpressive && role == ROLE_PAST) {
                    paint.setColor(ColorUtils.setAlphaComponent(colorAccent, (int) (alpha * highlightAlpha)));
                } else {
                    paint.setColor(ColorUtils.setAlphaComponent(colorActive, (int) (alpha * baseAlpha)));
                }
                layout.draw(canvas);
            }
            paint.setMaskFilter(null);
        }

        private void drawInterlude(Canvas canvas, boolean live, float progress, float alpha, long now,
                                   boolean expressive, int textWidth, int alignMode, boolean splitRoles) {
            float size = textSize() * 0.32f;
            float gap = size * 0.55f;
            float totalW = size * 3f + gap * 2f;
            float startX;
            if (expressive && alignMode == 0) {
                startX = (textWidth - totalW) * 0.5f;
            } else if (expressive && (expressiveBaseRtl ^ (isBacking && splitRoles))) {
                startX = textWidth - totalW;
            } else {
                startX = 0f;
            }
            float cy = textSize() * 0.45f;
            float breathe = live ? 1f + 0.06f * (float) Math.sin(now / 1600.0 * Math.PI * 2) : 1f;
            canvas.save();
            canvas.scale(breathe, breathe, startX + totalW * 0.5f, cy);
            int baseColor = expressive ? colorAccent : colorActive;
            int baseAlpha = baseColor >>> 24;
            for (int i = 0; i < 3; i++) {
                float lit = live ? Math.max(0f, Math.min(1f, progress * 3f - i)) : 0f;
                float dotAlpha = alpha * (0.35f + 0.65f * lit);
                dotPaint.setColor(ColorUtils.setAlphaComponent(baseColor, (int) (dotAlpha * baseAlpha)));
                if (lit > 0f) {
                    dotPaint.setShadowLayer(dp(4 + 2 * lit), 0, 0, ColorUtils.setAlphaComponent(baseColor, (int) (lit * 0.35f * baseAlpha)));
                } else {
                    dotPaint.clearShadowLayer();
                }
                float cx = startX + size / 2f + i * (size + gap);
                canvas.drawCircle(cx, cy, size / 2f * (0.9f + 0.1f * lit), dotPaint);
            }
            canvas.restore();
        }
    }
}
