/*
 * This is the source code of Solegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package app.exteraless.glass;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FragmentSearchField;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.ViewGroupPartRenderer;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSourceColor;
import org.telegram.utils.glass.source.GlassSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;

import java.util.ArrayList;

/**
 * Glass preview for the settings page: the top of a Telegram screen built from Telegram's own views —
 * a glass ActionBar (round back button, title, buttons) and the glass search field — over a chat
 * list that can be scrolled underneath. The glass is drawn by the same pipeline as in the app,
 * so every setting looks here exactly as it will look on the real screens.
 */
@SuppressLint("ViewConstructor")
public class SolegramGlassPreview extends FrameLayout {

    private static final int HEIGHT_DP = 200;

    private final RecyclerListView listView;
    private final LinearLayoutManager layoutManager;
    private final ActionBar actionBar;
    private final FragmentSearchField searchField;
    private final ViewPositionWatcher positionWatcher;
    private final GlassSourceColor sourceColor = new GlassSourceColor();
    private final GlassSourceRenderNode sourceGlass;
    private DownscaleScrollableNoiseSuppressor noiseSuppressor;
    private final IBlur3Capture capture;
    private final ArrayList<RectF> blurPositions = new ArrayList<>();
    private final RectF blurPositionHeader = new RectF();

    {
        blurPositions.add(blurPositionHeader);
    }

    public SolegramGlassPreview(Context context) {
        super(context);
        // the pills and the search field cast their shadow a little past their views; the settings row clips anyway
        setClipChildren(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            sourceGlass = new GlassSourceRenderNode(null);
            noiseSuppressor = new DownscaleScrollableNoiseSuppressor();
        } else {
            sourceGlass = null;
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setOverScrollMode(OVER_SCROLL_NEVER);
        listView.setClipToPadding(false);
        // the first rows already sit under the glass, like a chat list scrolled a little
        listView.setPadding(0, dp(28), 0, dp(8));
        listView.setAdapter(new RowsAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noiseSuppressor != null) {
                    noiseSuppressor.onScrolled(dx, dy);
                }
                invalidateGlass();
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        capture = new ViewGroupPartRenderer(listView, this, listView::drawChild);

        positionWatcher = new ViewPositionWatcher(this);

        actionBar = new ActionBar(context);
        actionBar.setOccupyStatusBar(false);
        actionBar.setAddToContainer(false);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setTitle("MoltenGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                // a preview: the buttons only show their press feedback
            }
        });
        final ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.outline_header_search);
        menu.addItem(2, R.drawable.ic_ab_other);
        addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 6, 0, 0));

        searchField = new FragmentSearchField(context, null);
        searchField.setPadding(dp(4), dp(4), dp(4), dp(4)); // as on the chats screen: a 40dp pill in a 48dp field
        searchField.setHintText(LocaleController.getString(R.string.Search), false);
        searchField.editText.setFocusable(false);
        searchField.editText.setFocusableInTouchMode(false);
        searchField.editText.setCursorVisible(false);
        addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.TOP, 7, 6 + 56 + 2, 7, 0));

        setupGlass();
        updateColors();
    }

    private BlurredBackgroundDrawableViewFactory factory() {
        final BlurredBackgroundDrawableViewFactory factory;
        if (sourceGlass != null) {
            factory = new BlurredBackgroundDrawableViewFactory(sourceGlass);
            factory.setLiquidGlassEffectAllowed(true);
        } else {
            factory = new BlurredBackgroundDrawableViewFactory(sourceColor);
        }
        factory.setSourceRootView(positionWatcher, this);
        return factory;
    }

    /** (Re)creates the glass of the header and the search field with the current glass settings. */
    private void setupGlass() {
        positionWatcher.unsubscribe(actionBar);
        positionWatcher.unsubscribe(searchField);
        final BlurredBackgroundDrawableViewFactory factory = factory();
        actionBar.setupGlass(factory, BlurredBackgroundProviderImpl.topPanelChatActivity(null));
        searchField.setupBlurredBackground(factory.create(searchField, BlurredBackgroundProviderImpl.topPanel(null)));
        searchField.setBlurredBackgroundVisibility(1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // the blur radius under the glass is fixed when the suppressor is made
            noiseSuppressor = new DownscaleScrollableNoiseSuppressor();
        }
        actionBar.invalidate();
        searchField.invalidate();
        invalidateGlass();
    }

    private final Runnable rebuild = this::setupGlass;

    /** The settings page calls this while sliders move; rebuilding is coalesced to one per frame. */
    public void refresh() {
        removeCallbacks(rebuild);
        postOnAnimation(rebuild);
        updateColors();
    }

    private void updateColors() {
        final int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
        setBackgroundColor(bg);
        sourceColor.setColor(bg);
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
        searchField.updateColors();
        listView.invalidateViews();
    }

    private void invalidateGlass() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && noiseSuppressor != null && getMeasuredWidth() > 0) {
            final int headerBottom = searchField.getBottom() > 0 ? searchField.getBottom() : dp(6 + 56 + 2 + 48);
            blurPositionHeader.set(0, -dp(48), getMeasuredWidth(), headerBottom + dp(48));
            noiseSuppressor.setupRenderNodes(blurPositions, 1);
            noiseSuppressor.invalidateResultRenderNodes(capture, getMeasuredWidth(), getMeasuredHeight());
        }
        invalidate();
        actionBar.invalidate();
        searchField.invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(HEIGHT_DP), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        invalidateGlass();
    }

    private int syncedVersion = Integer.MIN_VALUE, syncedBackground;

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        // presets, tint, toggles or a day/night switch changed the glass: rebuild it once
        final int background = Theme.getColor(Theme.key_windowBackgroundWhite);
        if (syncedVersion != LiquidGlassConfig.version || syncedBackground != background) {
            final boolean first = syncedVersion == Integer.MIN_VALUE;
            syncedVersion = LiquidGlassConfig.version;
            syncedBackground = background;
            if (!first) {
                refresh();
            }
        }
        // the same recording as on the real screens: the list under the glass, downscaled and blurred
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && sourceGlass != null && noiseSuppressor != null && !sourceGlass.inRecording()) {
            final Canvas c = sourceGlass.beginRecording(getMeasuredWidth(), getMeasuredHeight());
            c.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            if (SharedConfig.chatBlurEnabled()) {
                noiseSuppressor.draw(c, DownscaleScrollableNoiseSuppressor.DRAW_GLASS);
            }
            sourceGlass.endRecording();
        }
        super.dispatchDraw(canvas);
    }

    private float touchStartY, lastTouchY;
    private boolean ownsDrag;

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // drags on the chats scroll them under the glass; drags on the header, or past the end of the
        // chats, are left to the settings page
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchStartY = lastTouchY = ev.getY();
                ownsDrag = ev.getY() > searchField.getBottom();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(ownsDrag);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (ownsDrag && getParent() != null) {
                    final float dy = ev.getY() - lastTouchY;
                    if (dy != 0 && !listView.canScrollVertically(dy > 0 ? -1 : 1)) {
                        ownsDrag = false;
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
                lastTouchY = ev.getY();
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(rebuild);
    }

    // region Chats under the glass

    private static final int[][] AVATAR_COLORS = {
        {0xFFFF885E, 0xFFFF516A}, {0xFF64BF47, 0xFF4F9CD9}, {0xFF9B7CF4, 0xFF6E5BE0},
        {0xFFFFCD6A, 0xFFFFA85C}, {0xFF54CB68, 0xFF2EAF83}, {0xFF6EC9F7, 0xFF3D9DE8},
    };
    private static final int[] NAMES = {
        R.string.SG_PreviewChat1, R.string.SG_PreviewChat2, R.string.SG_PreviewChat3,
        R.string.SG_PreviewChat4, R.string.SG_PreviewChat5, R.string.SG_PreviewChat6,
    };
    private static final int[] MESSAGES = {
        R.string.SG_PreviewMessage1, R.string.SG_PreviewMessage2, R.string.SG_PreviewMessage3,
        R.string.SG_PreviewMessage4, R.string.SG_PreviewMessage5, R.string.SG_PreviewMessage6,
    };

    private class RowsAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final ChatRow row = new ChatRow(parent.getContext());
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((ChatRow) holder.itemView).set(position % NAMES.length);
        }

        @Override
        public int getItemCount() {
            return 48;
        }
    }

    /** A chat list row in Telegram's proportions: avatar, name, last message and time. */
    private static class ChatRow extends View {
        private final Paint avatarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint initialPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dividerPaint = new Paint();
        private int index;

        ChatRow(Context context) {
            super(context);
            initialPaint.setTextSize(dp(20));
            initialPaint.setTypeface(AndroidUtilities.bold());
            initialPaint.setColor(0xFFFFFFFF);
            initialPaint.setTextAlign(Paint.Align.CENTER);
            namePaint.setTextSize(dp(16));
            namePaint.setTypeface(AndroidUtilities.bold());
            textPaint.setTextSize(dp(15));
            timePaint.setTextSize(dp(13));
        }

        void set(int index) {
            this.index = index;
            invalidate();
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float cx = dp(10 + 27), cy = getHeight() / 2f, r = dp(27);
            final int[] colors = AVATAR_COLORS[index % AVATAR_COLORS.length];
            avatarPaint.setShader(new LinearGradient(cx, cy - r, cx, cy + r, colors[0], colors[1], Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, r, avatarPaint);
            final String name = LocaleController.getString(NAMES[index]);
            canvas.drawText(name.substring(0, 1).toUpperCase(), cx, cy - (initialPaint.ascent() + initialPaint.descent()) / 2f, initialPaint);

            namePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            timePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3));
            dividerPaint.setColor(Theme.getColor(Theme.key_divider));

            final float textLeft = dp(76);
            final String time = String.format(java.util.Locale.US, "%d:%02d", 9 + index, (index * 17) % 60);
            final float timeW = timePaint.measureText(time);
            canvas.drawText(time, getWidth() - dp(14) - timeW, dp(26), timePaint);
            final float nameMax = getWidth() - textLeft - timeW - dp(24);
            canvas.drawText(TextUtils.ellipsize(name, namePaint, nameMax, TextUtils.TruncateAt.END).toString(), textLeft, dp(26), namePaint);
            final String message = LocaleController.getString(MESSAGES[index]);
            canvas.drawText(TextUtils.ellipsize(message, textPaint, getWidth() - textLeft - dp(14), TextUtils.TruncateAt.END).toString(), textLeft, dp(48), textPaint);
            canvas.drawRect(textLeft, getHeight() - 1, getWidth(), getHeight(), dividerPaint);
        }
    }
    // endregion
}
