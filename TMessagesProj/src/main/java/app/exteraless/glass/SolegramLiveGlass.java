/*
 * This is the source code of Solegram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 */

package app.exteraless.glass;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.inspector.WindowInspector;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;




import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.utils.RenderNodeEffects;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.PipRoundVideoView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.utils.glass.source.GlassSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The glass of popups (menus, dialogs, sheets, bulletins) as the glass in chats is: Telegram's liquid glass
 * (refraction, Solegram's dispersion and rim) over a live picture of what is under it, not a screenshot.
 * <p>
 * Screens that popups open over ({@link Host}: the activity, sheets, the photo viewer) draw their content
 * through a RenderNode of their own. All windows of the app are drawn by the one render thread of the process,
 * so a popup's window can draw those nodes too: the glass records them (where they are on screen, the dim of a
 * window between them included) into a small downscaled node with Telegram's blur, and that node is the image the
 * liquid glass shader refracts. When the screen under it changes, its window tells the glass to draw again.
 * <p>
 * Android 13+ with liquid glass on (it needs RuntimeShader). Elsewhere, or when the screen under a popup can't be
 * read (a window that isn't a host, a SurfaceView or WebView whose position or functor must stay in one window),
 * popups keep their plain look.
 */
public final class SolegramLiveGlass implements GlassSource, org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory.DrawableOwner, View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {

    /** The glass of the chat's floating controls: a light blur, the refraction shows. */
    public static final int CLEAR = 0;
    /** The glass of the chat's lists: a strong blur, for surfaces to read (dialogs, sheets). */
    public static final int FROSTED = 1;

    private static boolean failed;

    /** Live glass can be drawn: Android 13+, liquid glass and the chat blur on (power saving turns them off). */
    public static boolean available() {
        return Build.VERSION.SDK_INT >= 33 && !failed && SharedConfig.chatBlurEnabled() && LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS);
    }

    /** Something went wrong on this device: live glass is off until the app restarts, popups keep their plain look. */
    static void fail(Throwable e) {
        if (!failed) {
            failed = true;
            FileLog.e(e);
        }
    }

    // region Hosts: screens drawn through a node that popups can look into

    private static final ArrayList<Host> HOSTS = new ArrayList<>();
    private static final ArrayList<SolegramLiveGlass> ACTIVE = new ArrayList<>();
    private static int hostsVersion;

    public interface Drawer {
        void draw(@NonNull Canvas canvas);
    }

    /**
     * A screen's content recorded into a node of its own, drawn in its window as before and by the glass of popups
     * over it. A window host records the whole window ({@link #draw(Canvas)} in place of the view's own drawing);
     * a part host records one child (a screen's content without the bulletins over it) only while glass in the same
     * window uses it ({@link #beginPart}/{@link #endPart}).
     */
    public static final class Host implements View.OnAttachStateChangeListener, ViewTreeObserver.OnDrawListener {
        final ViewGroup view;
        @Nullable
        private final Drawer drawer;
        private final boolean part;
        private RenderNode node;
        private boolean attached, recording;
        private int users;
        @Nullable
        private ViewTreeObserver observer;
        /** Frame time of the window's last draw. */
        long drawnFrame = -1;

        public Host(@NonNull ViewGroup view, @NonNull Drawer drawer) {
            this(view, drawer, false);
        }

        /** A part host (see {@link #beginPart}). */
        public Host(@NonNull ViewGroup view) {
            this(view, null, true);
        }

        private Host(ViewGroup view, @Nullable Drawer drawer, boolean part) {
            this.view = view;
            this.drawer = drawer;
            this.part = part;
            view.addOnAttachStateChangeListener(this);
            if (view.isAttachedToWindow()) {
                onViewAttachedToWindow(view);
            }
        }

        /** Draws the content through the node; false when it wasn't drawn (draw it the usual way then). */
        public boolean draw(@NonNull Canvas canvas) {
            if (drawer == null || part) {
                return false;
            }
            final Canvas c = begin(canvas);
            if (c == null) {
                return false;
            }
            try {
                drawer.draw(c);
            } finally {
                endPart(canvas);
            }
            return true;
        }

        /**
         * A part host: the canvas to draw the part into while glass in this window uses it, null to draw it the
         * usual way. Each non-null result is followed by {@link #endPart}.
         */
        @Nullable
        public Canvas beginPart(@NonNull Canvas canvas) {
            return part && users > 0 ? begin(canvas) : null;
        }

        public void endPart(@NonNull Canvas canvas) {
            if (!recording || Build.VERSION.SDK_INT < 33) {
                return;
            }
            recording = false;
            node.endRecording();
            canvas.drawRenderNode(node);
        }

        @Nullable
        private Canvas begin(Canvas canvas) {
            if (Build.VERSION.SDK_INT < 33 || recording || !attached || !available() || !canvas.isHardwareAccelerated()) {
                return null;
            }
            final int w = view.getWidth(), h = view.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            try {
                if (node == null) {
                    node = new RenderNode("SolegramLiveSource");
                    node.setClipToBounds(false); // children of the host may draw outside it, as they did before
                }
                if (node.getWidth() != w || node.getHeight() != h) {
                    node.setPosition(0, 0, w, h);
                }
                final RecordingCanvas c = node.beginRecording(w, h);
                recording = true;
                return c;
            } catch (Throwable e) {
                fail(e);
                return null;
            }
        }

        boolean hasContent() {
            return node != null && node.hasDisplayList() && attached;
        }

        /** A part host glass can look into now (in a window, with nothing there that must stay in it). */
        public boolean canSample() {
            try {
                return available() && part && attached && !hasWindowBoundContent(view);
            } catch (Throwable e) {
                fail(e);
                return false;
            }
        }

        void addUser() {
            if (users++ == 0 && part) {
                view.invalidate(); // starts recording the part
            }
        }

        void removeUser() {
            if (users > 0 && --users == 0 && part) {
                view.invalidate();
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull View v) {
            if (attached) {
                return;
            }
            attached = true;
            HOSTS.add(this);
            hostsVersion++;
            if (!part && Build.VERSION.SDK_INT >= 33) {
                observer = view.getViewTreeObserver();
                observer.addOnDrawListener(this);
            }
        }

        @Override
        public void onViewDetachedFromWindow(@NonNull View v) {
            if (!attached) {
                return;
            }
            attached = false;
            HOSTS.remove(this);
            hostsVersion++;
            final ViewTreeObserver o = observer;
            observer = null;
            if (o != null && o.isAlive()) {
                try {
                    o.removeOnDrawListener(this);
                } catch (IllegalStateException e) {
                    // detached while its window dispatches onDraw: removed right after
                    AndroidUtilities.runOnUIThread(() -> {
                        if (o.isAlive()) {
                            o.removeOnDrawListener(this);
                        }
                    });
                }
            }
            if (node != null && Build.VERSION.SDK_INT >= 29) {
                node.discardDisplayList();
            }
        }

        /** The window draws (before it records): glass in other windows draws again with what it shows now. */
        @Override
        public void onDraw() {
            if (!attached) {
                return;
            }
            drawnFrame = AnimationUtils.currentAnimationTimeMillis();
            for (int i = 0; i < ACTIVE.size(); i++) {
                ACTIVE.get(i).onHostDrawn(this);
            }
        }

        int depth() {
            int depth = 0;
            for (View v = view; v.getParent() instanceof View; v = (View) v.getParent()) {
                depth++;
            }
            return depth;
        }
    }

    /** The window host of a window: the outermost one in it. */
    @Nullable
    private static Host hostFor(View root) {
        Host best = null;
        int bestDepth = Integer.MAX_VALUE;
        for (int i = 0; i < HOSTS.size(); i++) {
            final Host host = HOSTS.get(i);
            if (host.part || host.view.getRootView() != root) {
                continue;
            }
            final int depth = host.depth();
            if (depth < bestDepth) {
                best = host;
                bestDepth = depth;
            }
        }
        return best;
    }

    /**
     * Content that must stay in one window: a SurfaceView places its surface where its node is drawn, a WebView
     * draws through a functor. Glass doesn't look into a screen showing one.
     */
    private static boolean hasWindowBoundContent(View v) {
        if (v.getVisibility() != View.VISIBLE) {
            return false;
        }
        if (v instanceof SurfaceView || v instanceof WebView) {
            return true;
        }
        if (v instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) v;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (hasWindowBoundContent(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final int[] tmpLocation = new int[2];
    private static final Rect tmpRect = new Rect(), tmpRect2 = new Rect();

    /**
     * What is under the window of {@code root}: the hosts of the windows below it, bottom first, each with the dim
     * of its window ({@code dims}, 0 for none). Fills {@code out} when given.
     *
     * @return how many, or -1 when it can't be read (a window there that isn't a host, nothing to read)
     */
    private static int resolveLayers(View root, @Nullable ArrayList<Host> out, @Nullable float[][] dims) {
        if (Build.VERSION.SDK_INT < 33 || root == null) {
            return -1;
        }
        final List<View> roots = WindowInspector.getGlobalWindowViews();
        final int index = roots.indexOf(root);
        if (index <= 0) {
            return -1;
        }
        // a window just added (a popup being shown) has no place on screen yet
        final boolean placed = root.isAttachedToWindow() && root.getWidth() > 0 && root.getHeight() > 0;
        if (placed) {
            root.getLocationOnScreen(tmpLocation);
            tmpRect.set(tmpLocation[0], tmpLocation[1], tmpLocation[0] + root.getWidth(), tmpLocation[1] + root.getHeight());
        }
        final long screenArea = (long) AndroidUtilities.displaySize.x * (AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight);
        int count = 0, hosts = 0;
        for (int i = 0; i < index; i++) {
            final View r = roots.get(i);
            if (r == null || !r.isAttachedToWindow() || r.getVisibility() != View.VISIBLE || r.getWindowVisibility() != View.VISIBLE
                    || r.getWidth() <= 0 || r.getHeight() <= 0 || r.getAlpha() <= 0 || r instanceof PipRoundVideoView.PipFrameLayout) {
                continue;
            }
            float dim = 0;
            if (r.getLayoutParams() instanceof WindowManager.LayoutParams) {
                final WindowManager.LayoutParams params = (WindowManager.LayoutParams) r.getLayoutParams();
                if ((params.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                    dim = params.dimAmount;
                }
            }
            final Host host = hostFor(r);
            if (host == null || !host.hasContent()) {
                // a window glass can't look into: no matter if it isn't under this one (a small floating window);
                // for a window with no place yet, only a big one counts (checked again once it is placed)
                r.getLocationOnScreen(tmpLocation);
                tmpRect2.set(tmpLocation[0], tmpLocation[1], tmpLocation[0] + r.getWidth(), tmpLocation[1] + r.getHeight());
                if (placed ? Rect.intersects(tmpRect, tmpRect2) : (long) r.getWidth() * r.getHeight() * 4 > screenArea) {
                    return -1;
                }
                if (dim <= 0) {
                    continue;
                }
            } else if (hasWindowBoundContent(host.view)) {
                return -1;
            }
            if (out != null && dims != null) {
                if (dims[0].length <= count) {
                    final float[] grown = new float[count * 2 + 2];
                    System.arraycopy(dims[0], 0, grown, 0, dims[0].length);
                    dims[0] = grown;
                }
                out.add(host != null && host.hasContent() ? host : null); // null: only the dim of that window
                dims[0][count] = dim;
            }
            if (host != null && host.hasContent()) {
                hosts++;
            }
            count++;
        }
        return hosts > 0 ? count : -1;
    }

    /** Glass in the window of {@code windowRoot} can show what is under it live. */
    /**
     * A menu's window over a screen the live glass can't read (a web page, video): the system blurs what is behind
     * that window (Android 12+, where the device does window blur) and the panel lets it show through, so the menu
     * is still frosted glass over a live screen. Only for popup windows (sub windows), never a screen's own window.
     *
     * @return whether the blur is on
     */
    public static boolean frostBehind(@Nullable View windowRoot, @Nullable android.graphics.drawable.Drawable panel) {
        if (Build.VERSION.SDK_INT < 31 || windowRoot == null || !LiquidGlassConfig.glassMenus || !available()) {
            return false;
        }
        try {
            if (!(windowRoot.getLayoutParams() instanceof android.view.WindowManager.LayoutParams)) {
                return false;
            }
            final android.view.WindowManager.LayoutParams lp = (android.view.WindowManager.LayoutParams) windowRoot.getLayoutParams();
            if (lp.type < android.view.WindowManager.LayoutParams.FIRST_SUB_WINDOW || lp.type > android.view.WindowManager.LayoutParams.LAST_SUB_WINDOW) {
                return false;
            }
            final android.view.WindowManager wm = (android.view.WindowManager) windowRoot.getContext().getSystemService(android.content.Context.WINDOW_SERVICE);
            if (wm == null || !wm.isCrossWindowBlurEnabled()) {
                return false;
            }
            if ((lp.flags & android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND) == 0) {
                lp.flags |= android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
                lp.setBlurBehindRadius(AndroidUtilities.dp(24));
                wm.updateViewLayout(windowRoot, lp);
            }
            if (panel != null) {
                panel.mutate().setAlpha(0xC4);
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static boolean canSample(@Nullable View windowRoot) {
        if (!available() || windowRoot == null) {
            return false;
        }
        try {
            return resolveLayers(windowRoot, null, null) > 0;
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }
    // endregion

    // region Glass

    /**
     * Live glass for {@code owner} (the view that draws {@link #drawable}, in its own coordinates), or null when
     * there is none on this device now. It is live while the owner is in a window, and looks into the windows
     * under it.
     */
    @Nullable
    public static SolegramLiveGlass create(@NonNull View owner, @Nullable BlurredBackgroundColorProvider provider, int recipe) {
        return create(owner, provider, recipe, null);
    }

    /**
     * Live glass over the part of a screen in the same window ({@code host}, a part host), for glass drawn over
     * that screen (bulletins).
     */
    @Nullable
    public static SolegramLiveGlass create(@NonNull View owner, @Nullable BlurredBackgroundColorProvider provider, int recipe, @Nullable Host host) {
        if (!available() || host != null && !host.part) {
            return null;
        }
        if (recipe == FROSTED) {
            // the frosted recipe (a plain blur, no real liquid glass) is gone: those panels are Telegram's plain fill
            return null;
        }
        try {
            final SolegramLiveGlass glass = new SolegramLiveGlass(owner, recipe, host);
            glass.drawable.setColorProvider(provider);
            return glass;
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    public final BlurredBackgroundDrawable drawable;
    private final View owner;
    private final int recipe;
    @Nullable
    private final Host partHost;
    @Nullable
    private GlassSource fallback;
    @Nullable
    private View fallbackRef;

    private final ArrayList<Host> layers = new ArrayList<>();
    private final float[][] layerDims = {new float[4]};
    private int[] layerX = new int[4], layerY = new int[4];
    private boolean active, live;
    private int resolvedVersion = -1;
    @Nullable
    private ViewTreeObserver observer;

    private final RenderNode blurNode;
    private int effectScale;
    private float effectRadius = -1;
    private boolean effectVibrancy, effectDark;
    private final int[] location = new int[2];
    private int offsetX = Integer.MIN_VALUE, offsetY = Integer.MIN_VALUE;

    @RequiresApi(33)
    private SolegramLiveGlass(View owner, int recipe, @Nullable Host partHost) {
        this.owner = owner;
        this.recipe = recipe;
        this.partHost = partHost;
        blurNode = new RenderNode("SolegramLiveGlass");
        drawable = new BlurredBackgroundDrawable(this);
        drawable.setLiquidGlassEffectAllowed(); // Telegram's liquid glass shader, with the glass settings (SolegramGlass)
        owner.addOnAttachStateChangeListener(this);
        if (owner.isAttachedToWindow()) {
            onViewAttachedToWindow(owner);
        }
    }

    /**
     * Drawn in place of the live picture when there is none (Telegram's own blurred picture of its scrim menus):
     * {@code ref} is the view its coordinates are relative to.
     */
    public void setFallback(@Nullable GlassSource fallback, @Nullable View ref) {
        this.fallback = fallback;
        this.fallbackRef = ref;
    }

    /** Not used any more: lets go of its view and of the screens under it. */
    public void release() {
        owner.removeOnAttachStateChangeListener(this);
        onViewDetachedFromWindow(owner);
    }

    /** Releases the live glass of {@code drawable}, if it is one (a background replaced by something else). */
    public static void release(@Nullable Drawable drawable) {
        if (drawable instanceof BlurredBackgroundDrawable && ((BlurredBackgroundDrawable) drawable).getSource() instanceof SolegramLiveGlass) {
            ((SolegramLiveGlass) ((BlurredBackgroundDrawable) drawable).getSource()).release();
        }
    }

    /** The glass shows what is under it now (false: its plain look, see {@link #drawFallback}). */
    public boolean isLive() {
        if (active && resolvedVersion != hostsVersion) {
            resolve();
        }
        return active && live && !failed;
    }

    /** Where the owner is on screen: the glass shows what is under it there. Call before drawing it after a move. */
    public void updateOffset() {
        owner.getLocationOnScreen(location);
        if (location[0] != offsetX || location[1] != offsetY) {
            offsetX = location[0];
            offsetY = location[1];
            drawable.setSourceOffset(offsetX, offsetY);
            owner.invalidate();
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull View v) {
        if (active) {
            return;
        }
        active = true;
        ACTIVE.add(this);
        observer = owner.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        if (partHost != null) {
            partHost.addUser();
        }
        resolvedVersion = -1;
        offsetX = offsetY = Integer.MIN_VALUE;
        updateOffset();
        drawable.invalidateDisplayList();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull View v) {
        if (!active) {
            return;
        }
        active = false;
        live = false;
        ACTIVE.remove(this);
        if (observer != null && observer.isAlive()) {
            observer.removeOnPreDrawListener(this);
        }
        observer = null;
        if (partHost != null) {
            partHost.removeUser();
        }
        layers.clear();
        if (Build.VERSION.SDK_INT >= 29) {
            blurNode.discardDisplayList(); // lets go of the screens' nodes
        }
    }

    private void resolve() {
        resolvedVersion = hostsVersion;
        final boolean wasLive = live;
        layers.clear();
        try {
            if (partHost != null) {
                if (partHost.attached && partHost.view.getRootView() == owner.getRootView() && !hasWindowBoundContent(partHost.view)) {
                    layers.add(partHost);
                    layerDims[0][0] = 0;
                }
            } else if (resolveLayers(owner.getRootView(), layers, layerDims) <= 0) {
                layers.clear();
            }
        } catch (Throwable e) {
            fail(e);
            layers.clear();
        }
        live = !layers.isEmpty();
        if (layerX.length < layers.size()) {
            layerX = new int[layers.size() * 2];
            layerY = new int[layers.size() * 2];
        }
        if (wasLive != live || live) {
            drawable.invalidateDisplayList();
            owner.invalidate();
        }
    }

    @Override
    public boolean onPreDraw() {
        if (!active) {
            return true;
        }
        if (resolvedVersion != hostsVersion) {
            resolve();
        }
        updateOffset();
        if (live && partHost == null) {
            // Keeps every window's damage right. A node is synced (and its changes count as damage) by the first
            // window that draws it in a frame: if this window draws before a screen whose views changed, that
            // screen redraws whole, so it doesn't miss the change.
            final long frame = AnimationUtils.currentAnimationTimeMillis();
            for (int i = 0; i < layers.size(); i++) {
                final Host host = layers.get(i);
                if (host != null && host.drawnFrame != frame && host.view.isDirty()) {
                    host.view.invalidate();
                }
            }
        }
        return true;
    }

    /** A screen under the glass draws: the glass draws again with what it shows. */
    void onHostDrawn(Host host) {
        if (!active || !live || partHost != null || !layers.contains(host)) {
            return;
        }
        drawable.invalidateDisplayList();
        owner.invalidate();
    }

    @Override
    public BlurredBackgroundDrawable createDrawable() {
        return drawable;
    }

    /** Draws what is under the glass at {@code left..bottom} (screen coordinates), blurred as the recipe has it. */
    @Override
    public void draw(Canvas canvas, float left, float top, float right, float bottom) {
        if (Build.VERSION.SDK_INT < 33 || !canvas.isHardwareAccelerated()) {
            return;
        }
        if (!isLive()) {
            drawFallback(canvas, left, top, right, bottom);
            return;
        }
        try {
            drawLive(canvas, left, top, right, bottom);
        } catch (Throwable e) {
            fail(e);
        }
    }

    @RequiresApi(33)
    private void drawLive(Canvas canvas, float left, float top, float right, float bottom) {
        final int scale = recipe == FROSTED ? 8 : 4; // as the chat's glass downscales (DownscaleScrollableNoiseSuppressor)
        final float radius = (recipe == FROSTED ? dpf2(40 - 1.66f) : dpf2(6)) * SolegramGlass.blurScale();
        final boolean dark = Theme.isCurrentThemeDark();
        updateEffect(scale, radius, dark);

        // the screens under it, and the region to read: the glass and the blur's reach round it, on screen
        int ul = Integer.MAX_VALUE, ut = Integer.MAX_VALUE, ur = Integer.MIN_VALUE, ub = Integer.MIN_VALUE;
        for (int i = 0; i < layers.size(); i++) {
            final Host host = layers.get(i);
            if (host == null) {
                continue;
            }
            host.view.getLocationOnScreen(location);
            layerX[i] = location[0];
            layerY[i] = location[1];
            ul = Math.min(ul, location[0]);
            ut = Math.min(ut, location[1]);
            ur = Math.max(ur, location[0] + host.view.getWidth());
            ub = Math.max(ub, location[1] + host.view.getHeight());
        }
        if (ul >= ur || ut >= ub) {
            return;
        }
        final float margin = radius * 1.5f + scale;
        // snapped to the downscale grid, so the picture doesn't shimmer as the glass moves
        final int rl = Math.max(ul, (int) Math.floor((left - margin) / scale) * scale);
        final int rt = Math.max(ut, (int) Math.floor((top - margin) / scale) * scale);
        final int rr = Math.min(ur, (int) Math.ceil((right + margin) / scale) * scale);
        final int rb = Math.min(ub, (int) Math.ceil((bottom + margin) / scale) * scale);
        if (rl >= rr || rt >= rb) {
            return;
        }
        final int w = Math.max(1, (rr - rl + scale - 1) / scale), h = Math.max(1, (rb - rt + scale - 1) / scale);
        if (blurNode.getWidth() != w || blurNode.getHeight() != h) {
            blurNode.setPosition(0, 0, w, h);
        }
        final RecordingCanvas c = blurNode.beginRecording(w, h);
        try {
            c.scale(1f / scale, 1f / scale);
            c.translate(-rl, -rt);
            c.drawColor(Theme.getColor(Theme.key_windowBackgroundWhite) | 0xFF000000);
            for (int i = 0; i < layers.size(); i++) {
                final float dim = layerDims[0][i];
                if (dim > 0) {
                    c.drawColor(Color.argb(Math.round(255 * Math.min(1, dim)), 0, 0, 0)); // the dim of that window, over what is below it
                }
                final Host host = layers.get(i);
                if (host == null || host.node == null) {
                    continue;
                }
                c.save();
                c.translate(layerX[i], layerY[i]);
                c.drawRenderNode(host.node);
                c.restore();
            }
        } finally {
            blurNode.endRecording();
        }
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.translate(rl, rt);
        canvas.scale(scale, scale);
        canvas.drawRenderNode(blurNode);
        canvas.restore();
    }

    /** The blur (and colours) of the picture under the glass; made again only when they change. */
    @RequiresApi(33)
    private void updateEffect(int scale, float radius, boolean dark) {
        final boolean vibrancy = SolegramGlass.vibrancy();
        if (effectScale == scale && Math.abs(effectRadius - radius) < 0.01f && effectVibrancy == vibrancy && effectDark == dark) {
            return;
        }
        effectScale = scale;
        effectRadius = radius;
        effectVibrancy = vibrancy;
        effectDark = dark;
        final float r = DownscaleScrollableNoiseSuppressor.downscaleRadius(radius, scale);
        final RenderEffect blur = RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP);
        final RenderEffect colors;
        if (recipe == FROSTED) {
            // reading surfaces: the colours of the one menu glass (SolegramGlass, region Menus)
            colors = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(SolegramGlass.menuBackdropMatrix(dark)));
        } else {
            colors = vibrancy ? RenderNodeEffects.getSaturationX3RenderEffect() : null; // as the chat's glass
        }
        blurNode.setRenderEffect(colors != null ? RenderEffect.createChainEffect(blur, colors) : blur);
    }

    /** No live picture: Telegram's own picture where there is one, else a solid panel (the plain look). */
    private void drawFallback(Canvas canvas, float left, float top, float right, float bottom) {
        if (fallback != null && fallbackRef != null && fallbackRef.isAttachedToWindow()) {
            fallbackRef.getLocationOnScreen(location);
            canvas.save();
            canvas.translate(location[0], location[1]);
            fallback.draw(canvas, left - location[0], top - location[1], right - location[0], bottom - location[1]);
            canvas.restore();
            return;
        }
        final BlurredBackgroundColorProvider provider = drawable.solegramColorProvider();
        final int color = provider != null ? provider.getBackgroundColor() : 0;
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        canvas.drawColor(Color.alpha(color) > 0 ? color | 0xFF000000 : Theme.getColor(Theme.key_windowBackgroundWhite) | 0xFF000000);
        canvas.restore();
    }
    // endregion

    // region Colours

    /** No colour, outline or shadow of its own: for glass something else colours and outlines (the liquid menus). */
    public static BlurredBackgroundProvider glassOnly() {
        return new BlurredBackgroundProviderBuilder(null)
            .setBackgroundColor((r, dark) -> 0)
            .setStrokeColorTop(0, 0)
            .setStrokeColorBottom(0, 0)
            .setShadowColor(0, 0)
            .setShadowLayer(0, 0, 0)
            .build();
    }

    /**
     * A glass panel (dialogs, sheets) with the outline and shadow of the one menu glass (SolegramGlass, region
     * Menus) and its own fill.
     */
    public static BlurredBackgroundProvider panel(@Nullable Theme.ResourcesProvider resourcesProvider, BlurredBackgroundProviderBuilder.ColorProvider fill, boolean shadow) {
        return new BlurredBackgroundProviderBuilder(resourcesProvider)
            .setBackgroundColor(fill)
            .setStrokeColorTop(SolegramGlass.MENU_RIM_TOP_LIGHT, SolegramGlass.MENU_RIM_TOP_DARK)
            .setStrokeColorBottom(SolegramGlass.menuEdgeBottomRaw(false), SolegramGlass.menuEdgeBottomRaw(true))
            .setShadowColor(shadow ? SolegramGlass.MENU_SHADOW_LIGHT : 0, shadow ? SolegramGlass.MENU_SHADOW_DARK : 0)
            .setShadowLayer(dpf2(SolegramGlass.MENU_SHADOW_RADIUS_DP), 0, dpf2(SolegramGlass.MENU_SHADOW_DY_DP))
            .setStrokeWidth(dpf2(SolegramGlass.MENU_RIM_WIDTH_DP), dpf2(SolegramGlass.MENU_EDGE_BOTTOM_WIDTH_DP))
            .build();
    }
    // endregion

    // region Menus: the one glass of every popup menu

    /** Glass menus are on and live glass can be drawn: popup menus are live glass (their plain panel otherwise). */
    public static boolean menusOn() {
        return LiquidGlassConfig.glassMenus && available();
    }

    /**
     * The glass of a menu panel drawn by {@code owner}: the one menu glass (SolegramGlass, region Menus) at the menu
     * radius, {@code inset} inside its bounds as the plain panel it stands for has its shadow margin. Null without
     * glass menus or live glass.
     *
     * @param color the panel's colour when it isn't the theme's menu colour (a dark menu over media), 0 (or white) for that one
     * @param host a part host for a menu over a screen in its own window (else it looks into the windows under it)
     */
    @Nullable
    public static SolegramLiveGlass menu(@NonNull View owner, @Nullable Theme.ResourcesProvider resourcesProvider, int color, int recipe, int inset, @Nullable Host host) {
        if (!LiquidGlassConfig.glassMenus) {
            return null;
        }
        // white is what a custom panel leaves as "its colour" (ActionBarPopupWindowLayout): the theme's menu colour then
        final BlurredBackgroundProvider provider = color == 0 || color == Color.WHITE || color == Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground, resourcesProvider)
            ? BlurredBackgroundProviderImpl.solegramMenu(resourcesProvider, false) // follows the theme
            : panel(resourcesProvider, (r, dark) -> SolegramGlass.menuFill(color, dark), true);
        final SolegramLiveGlass glass = create(owner, provider, recipe, host);
        if (glass != null) {
            glass.drawable.setPadding(inset);
            glass.drawable.setHasPadding(inset > 0);
            glass.drawable.setRadius(SolegramGlass.menuRadius());
        }
        return glass;
    }

    /**
     * The background of a menu panel that isn't an ActionBarPopupWindowLayout (the boxes under the message menu,
     * a menu's top view, custom popups): live glass while glass menus are on and the screen under it can be read,
     * else {@code plain} (a popup_fixed_alert* panel, coloured as before). Padding and shadow margin are the plain
     * panel's, so the layout doesn't change.
     */
    public static Drawable menuPanel(@NonNull View owner, @NonNull Drawable plain, @Nullable Theme.ResourcesProvider resourcesProvider) {
        return menuPanel(owner, plain, resourcesProvider, 0);
    }

    /** {@link #menuPanel(View, Drawable, Theme.ResourcesProvider)} of a panel in its own colour ({@code color}, 0: the theme's menu colour). */
    public static Drawable menuPanel(@NonNull View owner, @NonNull Drawable plain, @Nullable Theme.ResourcesProvider resourcesProvider, int color) {
        if (!menusOn()) {
            return plain;
        }
        final Rect padding = new Rect();
        plain.getPadding(padding);
        final SolegramLiveGlass glass = menu(owner, resourcesProvider, color, CLEAR, padding.left, null);
        return glass != null ? new MenuDrawable(plain, glass) : plain;
    }

    /** A menu panel: its live glass where the screen under it can be read, its plain panel elsewhere. */
    public static final class MenuDrawable extends Drawable implements Drawable.Callback {
        private final Drawable plain;
        private final SolegramLiveGlass glass;
        private int alpha = 255;

        MenuDrawable(@NonNull Drawable plain, @NonNull SolegramLiveGlass glass) {
            this.plain = plain;
            this.glass = glass;
            plain.setCallback(this);
            glass.drawable.setCallback(this);
        }

        private boolean live() {
            return Build.VERSION.SDK_INT >= 33 && LiquidGlassConfig.glassMenus && glass.isLive();
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (live()) {
                glass.drawable.draw(canvas);
            } else {
                plain.draw(canvas);
            }
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            plain.setBounds(bounds);
            glass.drawable.setBounds(bounds);
        }

        @Override
        public boolean getPadding(@NonNull Rect padding) {
            return plain.getPadding(padding);
        }

        @Override
        public void getOutline(@NonNull android.graphics.Outline outline) {
            if (live()) {
                glass.drawable.getOutline(outline);
            } else {
                plain.getOutline(outline);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            if (this.alpha != alpha) {
                this.alpha = alpha;
                plain.setAlpha(alpha);
                glass.drawable.setAlpha(alpha); // the glass records itself again on a change: only real ones
            }
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            plain.setColorFilter(colorFilter); // the glass has the colours of the one menu glass
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public void invalidateDrawable(@NonNull Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
            unscheduleSelf(what);
        }
    }
    // endregion

    // region Telegram's blurred menus

    /**
     * Telegram's factory for its blurred menus (a picture of the screen taken when the menu opens) that gives live
     * glass to the views it makes backgrounds for (with glass menus on); Telegram's picture stays where there is no
     * live glass. Menus over Telegram's blurred backdrop take {@link #FROSTED}: frosted like the backdrop round
     * them, refracting at the rim.
     */
    public static class Factory extends BlurredBackgroundDrawableViewFactory {
        private final GlassSource fallbackSource;
        private final int recipe;
        @Nullable
        private View ref;

        public Factory(GlassSource source, int recipe) {
            super(source);
            this.fallbackSource = source;
            this.recipe = recipe;
        }

        @Override
        public void setSourceRootView(org.telegram.ui.Components.chat.ViewPositionWatcher watcher, ViewGroup parent) {
            super.setSourceRootView(watcher, parent);
            ref = parent;
        }

        @Override
        public BlurredBackgroundDrawable create(View view, BlurredBackgroundColorProvider provider, boolean multiwindow) {
            if (recipe == FROSTED) {
                // menus over Telegram's blurred backdrop: a plain fill of the theme's menu colour, as Telegram had them,
                // not a blur of the screen
                final org.telegram.utils.glass.source.GlassSourceColor plain = new org.telegram.utils.glass.source.GlassSourceColor();
                plain.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground) | 0xFF000000);
                return new BlurredBackgroundDrawableViewFactory(plain).create(view, provider, multiwindow);
            }
            if (view != null && menusOn()) {
                final SolegramLiveGlass glass = SolegramLiveGlass.create(view, provider, recipe);
                if (glass != null) {
                    glass.setFallback(fallbackSource, ref);
                    return glass.drawable;
                }
            }
            return super.create(view, provider, multiwindow);
        }
    }
    // endregion

    // region Sheets

    private static android.graphics.Bitmap probe;
    private static Canvas probeCanvas;
    private static final android.graphics.Paint probePaint = new android.graphics.Paint();

    /** The colour a filter gives white (a sheet's colour from its panel's MULTIPLY filter); made when it is set, not per frame. */
    public static int colorOf(@NonNull ColorFilter filter) {
        if (probe == null) {
            probe = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888);
            probeCanvas = new Canvas(probe);
        }
        probe.eraseColor(0);
        probePaint.setColor(0xFFFFFFFF);
        probePaint.setColorFilter(filter);
        probeCanvas.drawPaint(probePaint);
        probePaint.setColorFilter(null);
        return probe.getPixel(0, 0);
    }

    /**
     * The panel of a glass sheet: live glass where the sheet's own panel (a nine-patch) was, which keeps only its
     * shadow round the glass. Big panels refract only along their edge band (the glass thickness), inside they
     * are frosted for reading. Without live glass it is the sheet's own panel.
     */
    public static final class SheetDrawable extends Drawable {
        private final Drawable panel;
        private final float radius = dp(21); // the corners of sheet_shadow_round
        private final Rect padding = new Rect(), inner = new Rect(), glassBounds = new Rect();
        private final Path innerPath = new Path();
        private final RectF tmp = new RectF();
        private final float[] radii = new float[8];
        @Nullable
        private final Theme.ResourcesProvider resourcesProvider;
        @Nullable
        private SolegramLiveGlass glass;
        @Nullable
        private View owner;
        private int color;
        private float density;
        private int alpha = 255;

        public SheetDrawable(Drawable panel, int color, float density, @Nullable Theme.ResourcesProvider resourcesProvider) {
            this.panel = panel;
            this.color = color;
            this.density = density;
            this.resourcesProvider = resourcesProvider;
            radii[0] = radii[1] = radii[2] = radii[3] = radius;
        }

        /** The view that draws the sheet (its glass shows what is under that view). */
        public void setOwner(@Nullable View owner) {
            if (this.owner == owner) {
                return;
            }
            this.owner = owner;
            if (glass != null) {
                glass.release();
            }
            glass = owner == null ? null : SolegramLiveGlass.create(owner,
                panel(resourcesProvider, (r, dark) -> ColorUtils.setAlphaComponent(color, Math.round(255 * density)), false), FROSTED);
            if (glass != null) {
                glass.drawable.setRadius(radius, radius, 0, 0, false);
                glass.drawable.setAlpha(alpha);
                if (!inner.isEmpty()) {
                    glass.drawable.setBounds(glassBounds);
                }
            }
        }

        /** How dense the glass is (the tint layer's opacity). */
        public void setDensity(float density) {
            if (this.density != density) {
                this.density = density;
                updateColors();
            }
        }

        private void updateColors() {
            if (glass != null) {
                glass.drawable.updateColors();
                glass.drawable.invalidateDisplayList();
            }
            invalidateSelf();
            if (owner != null) {
                owner.invalidate();
            }
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (glass == null || Build.VERSION.SDK_INT < 33 || !glass.isLive()) {
                panel.draw(canvas);
                return;
            }
            canvas.save();
            canvas.clipOutPath(innerPath); // the panel's shadow round the glass
            panel.draw(canvas);
            canvas.restore();
            canvas.save();
            canvas.clipRect(inner.left, inner.top - dp(2), inner.right, inner.bottom); // the glass's own shadow and rim stay at the top
            glass.drawable.draw(canvas);
            canvas.restore();
        }

        @Override
        protected void onBoundsChange(@NonNull Rect bounds) {
            panel.setBounds(bounds);
            panel.getPadding(padding);
            inner.set(bounds.left + padding.left, bounds.top + padding.top, bounds.right - padding.right, bounds.bottom - padding.bottom);
            innerPath.rewind();
            tmp.set(inner);
            innerPath.addRoundRect(tmp, radii, Path.Direction.CW);
            // the glass goes on below the screen: no refracting edge along its bottom, and a size that stays the
            // same while the sheet scrolls its top (the shader's effect is made again only when the size changes)
            final int screen = AndroidUtilities.displaySize.y + AndroidUtilities.statusBarHeight + AndroidUtilities.navigationBarHeight;
            glassBounds.set(inner.left, inner.top, inner.right, inner.top + Math.max(inner.height(), screen) + dp(32));
            if (glass != null) {
                glass.drawable.setBounds(glassBounds);
            }
        }

        @Override
        public boolean getPadding(@NonNull Rect padding) {
            return panel.getPadding(padding);
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
            panel.setAlpha(alpha);
            if (glass != null && glass.drawable.getAlpha() != alpha) {
                glass.drawable.setAlpha(alpha);
            }
        }

        @Override
        public int getAlpha() {
            return alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            panel.setColorFilter(colorFilter);
            if (colorFilter != null) {
                setColor(colorOf(colorFilter));
            }
        }

        @Override
        public void setColorFilter(int color, @NonNull PorterDuff.Mode mode) {
            panel.setColorFilter(color, mode);
            setColor(color);
        }

        private void setColor(int color) {
            if (this.color != color) {
                this.color = color;
                updateColors();
            }
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @NonNull
        @Override
        public Drawable mutate() {
            return this;
        }
    }
    // endregion
}
