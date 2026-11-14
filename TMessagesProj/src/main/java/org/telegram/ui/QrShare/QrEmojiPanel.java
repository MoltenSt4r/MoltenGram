package org.telegram.ui.QrShare;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;

import app.exteraless.appearance.AppearanceConfig;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.remote.EmojiHelper;
import org.telegram.ui.ActionBar.Theme;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * The emoji keyboard of the QR screen's sheet: a glass strip of pack tabs over a grid of custom emoji (the presets, the
 * recent ones and the installed packs). Only animated (Lottie) custom emoji: the theme on the server keeps custom emoji
 * only and the backdrop pattern animates them. Without Premium only the presets are free; the packs show "Unlock".
 * <p>
 * The screen styles it from outside: the strip draws the glass under and the rim over itself
 * ({@link Tabs#underDraw} / {@link Tabs#overDraw}), the grid lets the screen draw under its cells and mask them
 * ({@link Grid#beforeChildrenDraw} / {@link Grid#afterChildrenDraw}), and the cells take an extra scale for the
 * open / close cascade and the picked state ({@link Cell#cascadeScale} / {@link Cell#selectScale}).
 */
@SuppressLint("ViewConstructor")
public class QrEmojiPanel extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public interface Delegate {
        void onEmojiPicked(long documentId, @Nullable TLRPC.Document document);

        default void onStandardEmojiPicked(String emoji) {}

        /** "Unlock" of a pack: the Premium screen. */
        void onUnlock();
    }

    /** Draws in the visible bounds of a view (left, top, right, bottom). */
    public interface BoundsDraw {
        void draw(Canvas canvas, float left, float top, float right, float bottom);
    }

    public static final int TABS_HEIGHT_DP = 36;
    private static final int TAB_SIZE_DP = 30, TAB_ICON_DP = 26, TABS_PAD_DP = 3;
    private static final int CELL_DP = 46;   // 7 columns in the sheet of a usual phone
    private static final int RECENT_ROWS = 3;
    private static final int TYPE_HEADER = 0, TYPE_EMOJI = 1;
    private static final int TAB_RECENT_COLOR = 0x99FFFFFF, TAB_ACTIVE_COLOR = 0x5CFFFFFF;
    private static final int PLACEHOLDER_COLOR = 0x2BFFFFFF; // the white loading silhouettes, 17%
    private static final int UNLOCK_COLOR = 0x24FFFFFF;      // the "Unlock" pill: white 14% on the glass
    private static final int KIND_PRESETS = 0, KIND_RECENT = 1, KIND_PACK = 2, KIND_STANDARD = 3;

    /**
     * The presets (3 rows × 7, row by row): ❤️🔥😎🤩❄️🌸🎉 / 🚀🗿🏆👻💸🎮💎 / 🦄🧸🐶🦊🐱🐵🐥, custom emoji of the animated emoji set
     * "RestrictedEmoji"; any not found there resolve through the custom emoji cache / fetcher. Free for everyone.
     */
    private static final long[] PRESETS = {
        5449505950283078474L, 5420315771991497307L, 5373141891321699086L, 5373026167722876724L, 5435957248314579621L, 5440354006335495210L, 5436040291507247633L,
        5445284980978621387L, 5442983582882601962L, 5409008750893734809L, 5371017798065592581L, 5472030678633684592L, 5467583879948803288L, 5471952986970267163L,
        5415696465945173620L, 5397915559037785261L, 5283020991981693429L, 5283051451889756068L, 5282816560128336499L, 5465449362807070392L, 5470012829983580233L
    };
    /** In the presets the ⭐ (slot 4) is replaced by the set's ❄️. */
    private static final int PRESET_SNOW_SLOT = 4;
    private static final String PRESETS_SET = "RestrictedEmoji";
    private static final String PREF_APPLIED = "qrAppliedEmoji";

    private final int account;
    private final Delegate delegate;
    private final Grid grid;
    private final GridLayoutManager layoutManager;
    private final Adapter adapter = new Adapter();
    private final RecyclerAnimationScrollHelper scrollHelper;
    private final Tabs tabs;

    private static final int[] CATEGORY_STRINGS = {
        R.string.Emoji1,
        R.string.Emoji2,
        R.string.Emoji3,
        R.string.Emoji4,
        R.string.Emoji5,
        R.string.Emoji6,
        R.string.Emoji7,
        R.string.Emoji8
    };

    private static final int[] CATEGORY_ICONS = {
        R.drawable.msg_emoji_smiles,
        R.drawable.msg_emoji_cat,
        R.drawable.msg_emoji_food,
        R.drawable.msg_emoji_activities,
        R.drawable.msg_emoji_travel,
        R.drawable.msg_emoji_objects,
        R.drawable.msg_emoji_other,
        R.drawable.msg_emoji_flags
    };

    /** A section of the grid: the presets, the recent emoji or a pack. */
    private static final class Section {
        final int kind;
        final @Nullable String title;
        final long setId;
        final ArrayList<TLRPC.Document> documents;
        final ArrayList<String> standardEmojis;
        final int iconRes;
        int start;   // adapter position of its header (or of its first emoji when it has no title)

        Section(int kind, @Nullable String title, long setId, ArrayList<TLRPC.Document> documents) {
            this(kind, title, setId, documents, null, 0);
        }

        Section(int kind, @Nullable String title, long setId, @Nullable ArrayList<TLRPC.Document> documents, @Nullable ArrayList<String> standardEmojis) {
            this(kind, title, setId, documents, standardEmojis, 0);
        }

        Section(int kind, @Nullable String title, long setId, @Nullable ArrayList<TLRPC.Document> documents, @Nullable ArrayList<String> standardEmojis, int iconRes) {
            this.kind = kind;
            this.title = title;
            this.setId = setId;
            this.documents = documents != null ? documents : new ArrayList<>();
            this.standardEmojis = standardEmojis != null ? standardEmojis : new ArrayList<>();
            this.iconRes = iconRes;
        }

        int count() {
            return !standardEmojis.isEmpty() ? standardEmojis.size() : documents.size();
        }
    }

    private final ArrayList<Section> sections = new ArrayList<>();
    private int spanCount = 7;
    private int itemCount;
    private boolean smoothScrolling;
    private boolean premium;
    private int headerColor = 0x99FFFFFF;
    private @Nullable TLRPC.TL_messages_stickerSet presetSet;
    private boolean presetsRequested, presetsFailed;
    private final java.util.HashSet<Long> presetsFetched = new java.util.HashSet<>();
    private final java.util.HashSet<Long> presetIds = new java.util.HashSet<>();
    /** The emoji applied to the QR (presets included), newest first: the start of Recent. */
    private @Nullable ArrayList<Long> applied;
    private boolean recentDirty;

    public QrEmojiPanel(Context context, int account, Delegate delegate) {
        super(context);
        this.account = account;
        this.delegate = delegate;
        premium = org.telegram.messenger.UserConfig.getInstance(account).isPremium() || AppearanceConfig.freeQrPremium();

        grid = new Grid(context);
        layoutManager = new GridLayoutManager(context, spanCount);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == TYPE_HEADER ? spanCount : 1;
            }
        });
        grid.setLayoutManager(layoutManager);
        scrollHelper = new RecyclerAnimationScrollHelper(grid, layoutManager);
        scrollHelper.setAnimationCallback(new RecyclerAnimationScrollHelper.AnimationCallback() {
            @Override
            public void onEndAnimation() {
                smoothScrolling = false;
            }
        });
        grid.setAdapter(adapter);
        // taps go through the list (it sets the pressed state on the cell, like the stock keyboard); no own selector
        grid.setSelectorDrawableColor(0);
        grid.setOnItemClickListener((view, position) -> {
            if (view instanceof Cell) {
                final Cell cell = (Cell) view;
                if (cell.document != null && cell.document.id != 0) {
                    delegate.onEmojiPicked(cell.document.id, cell.document);
                } else if (cell.standardEmoji != null) {
                    delegate.onStandardEmojiPicked(cell.standardEmoji);
                }
            }
        });
        grid.setItemAnimator(null);
        grid.setOverScrollMode(OVER_SCROLL_NEVER);
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!smoothScrolling) {
                    tabs.select(sectionAt(firstShownPosition()), true);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    smoothScrolling = false;
                }
            }
        });
        addView(grid, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        tabs = new Tabs(context);
        addView(tabs, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, TABS_HEIGHT_DP, Gravity.TOP));

        reload();
    }

    public Grid getGrid() {
        return grid;
    }

    public Tabs getTabs() {
        return tabs;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(account).addWeakObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(account).addWeakObserver(this, NotificationCenter.groupStickersDidLoad);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        reload();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.groupStickersDidLoad);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            grid.invalidateViews();
            return;
        }
        if (id == NotificationCenter.stickersDidLoad && args.length > 0 && args[0] instanceof Integer && (Integer) args[0] != MediaDataController.TYPE_EMOJIPACKS) {
            return;
        }
        reload();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        final int content = w - grid.getPaddingLeft() - grid.getPaddingRight();
        final int span = Math.max(5, content / dp(CELL_DP));
        if (span != spanCount) {
            spanCount = span;
            layoutManager.setSpanCount(span);
            // not inside this layout pass: the tabs are rebuilt and need a layout of their own
            post(this::reload);
        }
    }

    /* Text-colour emoji */

    private static final PorterDuffColorFilter TEXT_COLOR = new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);

    /** A custom emoji that takes the colour of the text (e.g. the plain icon packs): white on the QR screen. */
    static boolean isTextColor(@Nullable Drawable d) {
        return d instanceof AnimatedEmojiDrawable && org.telegram.messenger.MessageObject.isTextColorEmoji(((AnimatedEmojiDrawable) d).getDocument());
    }

    /**
     * Draws an emoji, white if it takes the text colour. The drawables are shared with the rest of the app (the stock
     * keyboard, statuses), so the filter is set only for this draw.
     */
    static void drawEmoji(Canvas canvas, Drawable d) {
        final boolean textColor = isTextColor(d);
        if (textColor) d.setColorFilter(TEXT_COLOR);
        d.draw(canvas);
        if (textColor) d.setColorFilter(null);
    }

    /* Data */

    private static boolean isLottie(@Nullable TLRPC.Document document) {
        return document != null && (
            "application/x-tgsticker".equals(document.mime_type) ||
            "image/webp".equals(document.mime_type) ||
            "video/webm".equals(document.mime_type) ||
            org.telegram.messenger.MessageObject.isAnimatedEmoji(document)
        );
    }

    /** Whether a custom emoji is one of the free presets (applies without Premium). */
    public boolean isPreset(long documentId) {
        return presetIds.contains(documentId);
    }

    /** An emoji has been applied to the QR: it goes to the start of Recent, shown from the next time the panel shows. */
    public void addRecent(long documentId) {
        final ArrayList<Long> l = applied();
        l.remove(documentId);
        l.add(0, documentId);
        while (l.size() > PRESETS.length) l.remove(l.size() - 1);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(l.get(i));
        }
        org.telegram.messenger.MessagesController.getMainSettings(account).edit().putString(PREF_APPLIED, sb.toString()).apply();
        recentDirty = true;
    }

    /** Before the panel shows again: Recent takes the emoji applied since (never while it is open). */
    public void refreshRecent() {
        if (recentDirty) {
            recentDirty = false;
            reload();
        }
    }

    /** One colour for every section header, tinted by the scene. */
    public void setHeaderColor(int color) {
        if (headerColor == color) return;
        headerColor = color;
        if (AppearanceConfig.md3QrShare()) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            final View v = grid.getChildAt(i);
            if (v instanceof Header) ((Header) v).title.setTextColor(color);
        }
    }

    private ArrayList<Long> applied() {
        if (applied == null) {
            applied = new ArrayList<>();
            final String saved = org.telegram.messenger.MessagesController.getMainSettings(account).getString(PREF_APPLIED, "");
            for (String t : saved.split(",")) {
                try {
                    if (!t.isEmpty()) applied.add(Long.parseLong(t));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return applied;
    }

    /** The presets; empty placeholders (id 0) until the animated emoji set has come. */
    private ArrayList<TLRPC.Document> presets() {
        if (presetSet == null && !presetsRequested) {
            presetsRequested = true;
            final TLRPC.TL_inputStickerSetShortName input = new TLRPC.TL_inputStickerSetShortName();
            input.short_name = PRESETS_SET;
            final TLRPC.TL_messages_stickerSet cached = MediaDataController.getInstance(account).getStickerSet(input, null, false, true, set -> {
                if (presetSet != null) return;
                if (set == null || set.documents == null) {
                    // no set (e.g. another server): the presets come one by one from the custom emoji, or not at all
                    presetsFailed = true;
                } else {
                    presetSet = set;
                }
                reload();
            });
            if (cached != null && cached.documents != null && !cached.documents.isEmpty()) presetSet = cached;
        }
        final ArrayList<TLRPC.Document> docs = new ArrayList<>(PRESETS.length);
        presetIds.clear();
        if (presetSet == null && !presetsFailed) {
            for (int i = 0; i < PRESETS.length; i++) docs.add(new TLRPC.TL_documentEmpty());
            return docs;
        }
        final java.util.HashMap<Long, TLRPC.Document> byId = new java.util.HashMap<>();
        if (presetSet != null && presetSet.documents != null) {
            for (TLRPC.Document d : presetSet.documents) {
                if (d != null) byId.put(d.id, d);
            }
        }
        long snowId = 0;
        if (presetSet != null && presetSet.packs != null) {
            for (TLRPC.TL_stickerPack pack : presetSet.packs) {
                if (pack != null && pack.emoticon != null && pack.emoticon.replace("\uFE0F", "").equals("\u2744") && pack.documents != null && !pack.documents.isEmpty()) {
                    snowId = pack.documents.get(0);
                    break;
                }
            }
        }
        for (int i = 0; i < PRESETS.length; i++) {
            final long id = i == PRESET_SNOW_SLOT && snowId != 0 && byId.containsKey(snowId) ? snowId : PRESETS[i];
            TLRPC.Document d = byId.get(id);
            if (d == null) d = AnimatedEmojiDrawable.findDocument(account, id);
            if (d != null) {
                docs.add(d);
                presetIds.add(d.id);
            } else if (presetsFetched.add(id)) {
                // one fetch per id; the section is rebuilt with it
                AnimatedEmojiDrawable.getDocumentFetcher(account).fetchDocument(id, doc -> AndroidUtilities.runOnUIThread(() -> {
                    if (doc != null) reload();
                }));
            }
        }
        return docs;
    }

    private void reload() {
        final MediaDataController data = MediaDataController.getInstance(account);
        sections.clear();
        // the packs: installed custom emoji packs, their animated (Lottie) emoji only
        final ArrayList<Section> packs = new ArrayList<>();
        for (TLRPC.TL_messages_stickerSet set : data.getStickerSets(MediaDataController.TYPE_EMOJIPACKS)) {
            if (set == null || set.set == null || set.documents == null) {
                continue;
            }
            final ArrayList<TLRPC.Document> docs = new ArrayList<>();
            for (TLRPC.Document d : set.documents) {
                if (isLottie(d)) {
                    docs.add(d);
                }
            }
            if (!docs.isEmpty()) {
                packs.add(new Section(KIND_PACK, set.set.title, set.set.id, docs));
            }
        }
        final ArrayList<TLRPC.Document> presets = presets();
        final ArrayList<TLRPC.Document> recent = new ArrayList<>();
        final java.util.HashSet<Long> inRecent = new java.util.HashSet<>();
        final int recentMax = spanCount * RECENT_ROWS;
        final ArrayList<Long> appliedIds = applied();
        for (int i = 0; i < appliedIds.size() && recent.size() < recentMax; i++) {
            final long id = appliedIds.get(i);
            TLRPC.Document d = null;
            for (TLRPC.Document p : presets) {
                if (p.id == id) {
                    d = p;
                    break;
                }
            }
            if (d == null) d = AnimatedEmojiDrawable.findDocument(account, id);
            if (isLottie(d) && inRecent.add(d.id)) recent.add(d);
        }
        for (int i = 0; i < Emoji.recentEmoji.size() && recent.size() < recentMax; i++) {
            final String e = Emoji.recentEmoji.get(i);
            if (e == null || !e.startsWith("animated_")) {
                continue;
            }
            try {
                final TLRPC.Document d = AnimatedEmojiDrawable.findDocument(account, Long.parseLong(e.substring(9)));
                if (isLottie(d) && inRecent.add(d.id)) {
                    recent.add(d);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        if (!recent.isEmpty()) {
            sections.add(new Section(KIND_RECENT, org.telegram.messenger.LocaleController.getString(R.string.RecentlyUsed), 0, recent, null, R.drawable.msg_emoji_recent));
        }

        // Standard / Configured System Emoji Categories from EmojiData.dataColored
        if (EmojiData.dataColored != null) {
            for (int c = 0; c < Math.min(EmojiData.dataColored.length, CATEGORY_STRINGS.length); c++) {
                final String[] emList = EmojiData.dataColored[c];
                if (emList != null && emList.length > 0) {
                    final ArrayList<String> emojis = new ArrayList<>(emList.length);
                    for (String em : emList) {
                        if (em != null) emojis.add(em);
                    }
                    if (!emojis.isEmpty()) {
                        sections.add(new Section(KIND_STANDARD, LocaleController.getString(CATEGORY_STRINGS[c]), 0, null, emojis, CATEGORY_ICONS[c]));
                    }
                }
            }
        }

        if (!presets.isEmpty()) {
            sections.add(new Section(KIND_PRESETS, org.telegram.messenger.LocaleController.getString(R.string.QrEmojiPresets), 0, presets, null, R.drawable.emoji_tabs_faves));
        }

        sections.addAll(packs);

        int position = 0;
        for (Section s : sections) {
            s.start = position;
            position += (s.title != null ? 1 : 0) + s.count();
        }
        itemCount = position;
        adapter.notifyDataSetChanged();
        tabs.setSections(sections);
        tabs.select(sectionAt(firstShownPosition()), false);
    }

    private int sectionAt(int position) {
        for (int i = sections.size() - 1; i >= 0; i--) {
            if (sections.get(i).start <= position) {
                return i;
            }
        }
        return 0;
    }

    /** The first row shown: the grid scrolls on under the tabs (no clip to padding), so the first one below them. */
    private int firstShownPosition() {
        final int top = grid.getPaddingTop();
        int position = RecyclerView.NO_POSITION, bestTop = Integer.MAX_VALUE;
        for (int i = 0; i < grid.getChildCount(); i++) {
            final View child = grid.getChildAt(i);
            final int childTop = layoutManager.getDecoratedTop(child);
            if (childTop >= top && childTop < bestTop) {
                final int p = grid.getChildAdapterPosition(child);
                if (p != RecyclerView.NO_POSITION) {
                    bestTop = childTop;
                    position = p;
                }
            }
        }
        return position != RecyclerView.NO_POSITION ? position : 0;
    }

    /** Like the stock emoji keyboard: a far pack is reached with a short jump animation, not by scrolling through all. */
    private void scrollToSection(int index) {
        if (index < 0 || index >= sections.size()) {
            return;
        }
        smoothScrolling = true;
        tabs.select(index, true);
        final int position = sections.get(index).start;
        final int first = layoutManager.findFirstVisibleItemPosition();
        if (layoutManager.findViewByPosition(position) == null && Math.abs(position - first) > spanCount * 9 || !SharedConfig.animationsEnabled()) {
            grid.stopScroll();
            scrollHelper.setScrollDirection(first < position ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            scrollHelper.scrollToPosition(position, 0, false, true);
            return;
        }
        final LinearSmoothScroller scroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int getVerticalSnapPreference() {
                return SNAP_TO_START; // the box starts below the grid's top padding: right under the tabs
            }
        };
        scroller.setTargetPosition(sections.get(index).start);
        layoutManager.startSmoothScroll(scroller);
    }

    /* Grid */

    /** The emoji grid; the screen draws under its cells and masks them (fades) through the two hooks. */
    public static class Grid extends RecyclerListView {

        public @Nullable Consumer<Canvas> beforeChildrenDraw;
        /** When set, the grid draws into its own layer and this masks it, e.g. DST_IN gradients. */
        public @Nullable Consumer<Canvas> afterChildrenDraw;

        Grid(Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            final int save = afterChildrenDraw != null ? canvas.saveLayer(0, 0, getWidth(), getHeight(), null) : -1;
            if (beforeChildrenDraw != null) {
                beforeChildrenDraw.accept(canvas);
            }
            super.dispatchDraw(canvas);
            if (afterChildrenDraw != null) {
                afterChildrenDraw.accept(canvas);
                canvas.restoreToCount(save);
            }
        }
    }

    /** One custom emoji of the grid. */
    public static class Cell extends View {

        private final int account;
        private @Nullable AnimatedEmojiDrawable drawable;
        private @Nullable Drawable standardDrawable;
        private @Nullable String standardEmoji;
        private final Paint placeholder = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long documentId;
        private @Nullable TLRPC.Document document;
        private @Nullable String key;
        private float pressK;

        /** The picked state's scale (the screen drives it). */
        public float selectScale = 1f;
        /** The open / close cascade's scale (the screen drives it). */
        public float cascadeScale = 1f;

        Cell(Context context, int account) {
            super(context);
            this.account = account;
            placeholder.setColor(PLACEHOLDER_COLOR);
        }

        /** The key the screen uses for a picked emoji: "c:" + document id or standard unicode. */
        @Nullable
        public String getKey() {
            return key;
        }

        void set(TLRPC.Document document) {
            standardDrawable = null;
            standardEmoji = null;
            if (document.id == 0) {
                // a preset still on its way: the placeholder only
                if (drawable != null && isAttachedToWindow()) drawable.removeView(this);
                drawable = null;
                documentId = 0;
                this.document = document;
                key = null;
                invalidate();
                return;
            }
            if (document.id == documentId && drawable != null) {
                return;
            }
            if (drawable != null && isAttachedToWindow()) {
                drawable.removeView(this);
            }
            documentId = document.id;
            this.document = document;
            key = "c:" + document.id;
            drawable = AnimatedEmojiDrawable.make(account, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, document);
            if (isAttachedToWindow()) {
                drawable.addView(this);
            }
            setContentDescription(document.id + "");
            invalidate();
        }

        void setStandardEmoji(String emoji) {
            if (drawable != null && isAttachedToWindow()) {
                drawable.removeView(this);
            }
            drawable = null;
            this.document = null;
            this.documentId = 0;
            this.standardEmoji = emoji;
            this.key = QrShareView.normKey(emoji);
            if (!Emoji.isSelectedCustomPack()) {
                Drawable d = Emoji.getEmojiDrawable(emoji);
                if (d == null) {
                    d = Emoji.getEmojiDrawable(emoji.replace("\uFE0F", ""));
                }
                this.standardDrawable = d;
            } else {
                this.standardDrawable = null;
            }
            setContentDescription(emoji);
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (drawable != null) {
                drawable.addView(this);
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (drawable != null) {
                drawable.removeView(this);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, w);
        }

        @Override
        public void setPressed(boolean pressed) {
            if (pressed != isPressed()) {
                super.setPressed(pressed);
                invalidate();
            }
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float target = isPressed() ? 1f : 0f;
            if (pressK != target) {
                pressK = Math.abs(pressK - target) < 0.02f ? target : pressK + (target - pressK) * 0.35f;
                invalidate();
            }
            final float scale = (1f - 0.2f * pressK) * selectScale * cascadeScale;
            if (scale <= 0.01f) {
                return;
            }
            final float size = getWidth() * 0.78f * scale;
            final float cx = getWidth() / 2f, cy = getHeight() / 2f;
            final boolean loaded = standardEmoji != null || (drawable != null && drawable.getImageReceiver() != null && drawable.getImageReceiver().hasBitmapImage());
            if (!loaded) {
                canvas.drawCircle(cx, cy, size * 0.42f, placeholder);
            }
            if (standardEmoji != null) {
                if (standardDrawable != null) {
                    standardDrawable.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
                    standardDrawable.draw(canvas);
                } else {
                    EmojiHelper.drawEmojiFont(canvas, (int) (cx - size / 2f), (int) (cy - size / 2f), EmojiHelper.getInstance().getCurrentTypeface(), standardEmoji, (int) size);
                }
            } else if (drawable != null) {
                drawable.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
                drawEmoji(canvas, drawable);
            }
        }
    }

    /** A section title (one line, the scene's header colour); without Premium a pack's title ends in "Unlock". */
    private final class Header extends FrameLayout {

        final TextView title;
        final TextView unlock;

        Header(Context context) {
            super(context);
            title = new TextView(context);
            if (AppearanceConfig.md3QrShare()) {
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            } else {
                title.setTextColor(headerColor);
            }
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            title.setTypeface(AndroidUtilities.bold());
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL);
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 15, 8, 15, 0));
            unlock = new TextView(context);
            unlock.setTextColor(0xFFFFFFFF);
            unlock.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            unlock.setTypeface(AndroidUtilities.bold());
            unlock.setGravity(Gravity.CENTER);
            unlock.setPadding(dp(10), 0, dp(10), 0);
            unlock.setText(org.telegram.messenger.LocaleController.getString(R.string.Unlock));
            final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(UNLOCK_COLOR);
            bg.setCornerRadius(dp(13));
            unlock.setBackground(bg);
            unlock.setOnClickListener(v -> delegate.onUnlock());
            addView(unlock, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 4, 12, 0));
        }

        void set(@Nullable String text, boolean locked) {
            title.setText(text != null ? text.replace('\n', ' ') : null);
            if (AppearanceConfig.md3QrShare()) {
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            } else {
                title.setTextColor(headerColor);
            }
            unlock.setVisibility(locked ? VISIBLE : GONE);
            // one line, cut before the pill
            final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) title.getLayoutParams();
            final int right = dp(15) + (locked ? dp(12) + (int) Math.ceil(unlock.getPaint().measureText(unlock.getText().toString())) + dp(20) + dp(6) : 0);
            if (lp.rightMargin != right) {
                lp.rightMargin = right;
                title.setLayoutParams(lp);
            }
        }
    }

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @Override
        public int getItemCount() {
            return itemCount;
        }

        @Override
        public int getItemViewType(int position) {
            final Section s = sections.get(sectionAt(position));
            return s.title != null && position == s.start ? TYPE_HEADER : TYPE_EMOJI;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view;
            if (viewType == TYPE_HEADER) {
                final Header header = new Header(parent.getContext());
                header.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
                view = header;
            } else {
                final Cell cell = new Cell(parent.getContext(), account);
                cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                view = cell;
            }
            return new RecyclerView.ViewHolder(view) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            bind(holder, position);
        }

        private void bind(@NonNull RecyclerView.ViewHolder holder, int position) {
            final Section s = sections.get(sectionAt(position));
            if (holder.getItemViewType() == TYPE_HEADER) {
                ((Header) holder.itemView).set(s.title, !premium && s.kind == KIND_PACK);
                return;
            }
            final Cell cell = (Cell) holder.itemView;
            final int offset = position - s.start - (s.title != null ? 1 : 0);
            if (s.kind == KIND_STANDARD) {
                if (offset >= 0 && offset < s.standardEmojis.size()) {
                    cell.setStandardEmoji(s.standardEmojis.get(offset));
                }
            } else {
                if (offset >= 0 && offset < s.documents.size()) {
                    cell.set(s.documents.get(offset));
                }
            }
        }
    }

    /* Tabs */

    /** The strip of pack tabs: Recent, then a tab per pack with its first emoji; the active one sits in a white circle. */
    public class Tabs extends HorizontalScrollView {

        /** Drawn under the strip in its visible bounds: the island's glass. */
        public @Nullable BoundsDraw underDraw;
        /** Drawn over the strip in its visible bounds: the glass rim. */
        public @Nullable BoundsDraw overDraw;

        private final LinearLayout content;
        private final Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int selected = -1;
        private float circleX = Float.NaN;

        Tabs(Context context) {
            super(context);
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            setFillViewport(true);
            setWillNotDraw(false);
            content = new LinearLayout(context) {
                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    drawCircle(canvas);
                    super.dispatchDraw(canvas);
                }
            };
            content.setOrientation(LinearLayout.HORIZONTAL);
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setPadding(dp(TABS_PAD_DP), 0, dp(TABS_PAD_DP), 0);
            addView(content, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (AppearanceConfig.md3QrShare()) {
                circle.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            } else {
                circle.setColor(TAB_ACTIVE_COLOR);
            }
        }

        void setSections(ArrayList<Section> sections) {
            content.removeAllViews();
            for (int i = 0; i < sections.size(); i++) {
                final Section s = sections.get(i);
                final Tab tab = new Tab(getContext(), s.kind, s.iconRes, s.kind == KIND_PACK && !s.documents.isEmpty() ? s.documents.get(0) : null, i);
                final int index = i;
                tab.setOnClickListener(v -> scrollToSection(index));
                tab.setContentDescription(s.title);
                content.addView(tab, new LinearLayout.LayoutParams(dp(TAB_SIZE_DP + 4), dp(TAB_SIZE_DP)));
            }
            selected = -1;
            circleX = Float.NaN;
        }

        void select(int index, boolean animated) {
            if (index == selected || index < 0 || index >= content.getChildCount()) {
                return;
            }
            selected = index;
            if (AppearanceConfig.md3QrShare()) {
                circle.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            } else {
                circle.setColor(TAB_ACTIVE_COLOR);
            }
            if (!animated) {
                circleX = Float.NaN;
            }
            for (int i = 0; i < content.getChildCount(); i++) {
                content.getChildAt(i).invalidate();
            }
            final View tab = content.getChildAt(index);
            // keep the active tab in view
            final int left = tab.getLeft() - dp(TAB_SIZE_DP), right = tab.getRight() + dp(TAB_SIZE_DP);
            if (left < getScrollX()) {
                if (animated) smoothScrollTo(left, 0);
                else scrollTo(left, 0);
            } else if (right > getScrollX() + getWidth()) {
                if (animated) smoothScrollTo(right - getWidth(), 0);
                else scrollTo(right - getWidth(), 0);
            }
            content.invalidate();
        }

        private void drawCircle(Canvas canvas) {
            if (selected < 0 || selected >= content.getChildCount()) {
                return;
            }
            final View tab = content.getChildAt(selected);
            final float target = tab.getLeft() + tab.getWidth() / 2f;
            if (tab.getWidth() == 0) {
                return;
            }
            if (Float.isNaN(circleX)) {
                circleX = target;
            } else if (circleX != target) {
                final float d = target - circleX;
                circleX = Math.abs(d) < 0.5f ? target : circleX + d * 0.3f;
                content.invalidate();
            }
            final float r = (getHeight() - dp(6)) / 2f;
            canvas.drawCircle(circleX, content.getHeight() / 2f, r, circle);
        }

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (AppearanceConfig.md3QrShare()) {
                bgPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                bgPaint.setAlpha(230);
                canvas.drawRect(getScrollX(), 0, getScrollX() + getWidth(), getHeight(), bgPaint);
            } else {
                if (underDraw != null) underDraw.draw(canvas, getScrollX(), 0, getScrollX() + getWidth(), getHeight());
            }
            super.draw(canvas);
            if (!AppearanceConfig.md3QrShare()) {
                if (overDraw != null) overDraw.draw(canvas, getScrollX(), 0, getScrollX() + getWidth(), getHeight());
            }
        }
    }

    /** A tab: the star of the presets, the clock of Recent, or a pack's first emoji. */
    private final class Tab extends View {

        private final int tabIndex;
        private final @Nullable AnimatedEmojiDrawable emoji;
        private final @Nullable Drawable icon;
        private float pressK;
        private boolean pressed;

        Tab(Context context, int kind, int iconRes, @Nullable TLRPC.Document document, int tabIndex) {
            super(context);
            this.tabIndex = tabIndex;
            if (document != null) {
                emoji = AnimatedEmojiDrawable.make(account, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_TAB_STRIP, document);
                icon = null;
            } else {
                emoji = null;
                int res = iconRes;
                if (res == 0) {
                    if (kind == KIND_PRESETS) {
                        res = R.drawable.emoji_tabs_faves;
                    } else if (kind == KIND_STANDARD) {
                        res = R.drawable.msg_emoji_smiles;
                    } else {
                        res = R.drawable.msg_emoji_recent;
                    }
                }
                icon = ContextCompat.getDrawable(context, res).mutate();
                icon.setColorFilter(new PorterDuffColorFilter(TAB_RECENT_COLOR, PorterDuff.Mode.SRC_IN));
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (emoji != null) emoji.addView(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (emoji != null) emoji.removeView(this);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            final int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                pressed = true;
                invalidate();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                pressed = false;
                invalidate();
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            final float target = pressed ? 1f : 0f;
            if (pressK != target) {
                pressK = Math.abs(pressK - target) < 0.02f ? target : pressK + (target - pressK) * 0.35f;
                invalidate();
            }
            final float s = dp(TAB_ICON_DP) * (1f - 0.15f * CubicBezierInterpolator.EASE_OUT.getInterpolation(pressK));
            final int l = (int) (getWidth() / 2f - s / 2f), t = (int) (getHeight() / 2f - s / 2f);
            final Drawable d = emoji != null ? emoji : icon;
            if (d != null) {
                if (icon != null && AppearanceConfig.md3QrShare()) {
                    boolean isSel = tabs != null && tabs.selected == tabIndex;
                    int tintColor = isSel ? 0xFFFFFFFF : Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.65f);
                    icon.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
                }
                d.setBounds(l, t, (int) (l + s), (int) (t + s));
                drawEmoji(canvas, d);
            }
        }
    }
}
