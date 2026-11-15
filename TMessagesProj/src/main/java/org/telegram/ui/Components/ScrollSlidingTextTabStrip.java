/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpr;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.text.Layout;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import app.exteraless.appearance.Md3ChipTabHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.HashSet;

public class ScrollSlidingTextTabStrip extends HorizontalScrollView implements Theme.Colorable {

    public interface ScrollSlidingTabStripDelegate {
        void onPageSelected(int page, boolean forward);
        void onPageScrolled(float progress);
        default void onSamePageSelected() {

        }
        default boolean showOptions(int page, View view) {
            return false;
        }
        default boolean canReorder(int page) {
            return false;
        }
    }

    private final LinearLayout tabsContainer;
    private ScrollSlidingTabStripDelegate delegate;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean useSameWidth;
    private boolean useMinimalWidth;
    private boolean fitsItems;

    private int tabCount;
    private int currentPosition;
    private int selectedTabId = -1;
    private int allTextWidth;

    private float indicatorX;
    private float indicatorWidth;

    private int prevLayoutWidth;

    private float animateIndicatorStartX;
    private float animateIndicatorStartWidth;
    private float animateIndicatorToX;
    private float animateIndicatorToWidth;
    private boolean animatingIndicator;
    private float animationIdicatorProgress;

    private int scrollingToChild = -1;

    private final GradientDrawable selectorDrawable;
    private final GradientDrawable inactiveChipDrawable = new GradientDrawable();
    private final float[] chipRadiiScratch = new float[8];
    private final float[] chipRadiiFrom = new float[8];
    private final float[] chipRadiiTo = new float[8];
    private final float[] chipRadiiOut = new float[8];
    private int swipingToPosition = -1;
    private float swipingProgress;
    private float chipAnimRawProgress = 1f;

    private int tabLineColorKey = Theme.key_actionBarTabLine;
    private int activeTextColorKey = Theme.key_actionBarTabActiveText;
    private int unactiveTextColorKey = Theme.key_actionBarTabUnactiveText;
    private int selectorColorKey = Theme.key_actionBarTabSelector;

    private CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT_QUINT;

    private SparseIntArray positionToId = new SparseIntArray(5);
    private SparseIntArray idToPosition = new SparseIntArray(5);
    private SparseIntArray prevPositionToWidth = new SparseIntArray(5);
    private SparseIntArray positionToWidth = new SparseIntArray(5);

    private int previousPosition;

    private float animateFromIndicaxtorX;
    private float animateFromIndicatorWidth;

    private float indicatorXAnimationDx;
    private float indicatorWidthAnimationDx;

    public long animationDuration = 200;
    private View dragging;
    private ValueAnimator indicatorAnimator;

    protected int processColor(int color) {
        return color;
    }

    public ScrollSlidingTextTabStrip(Context context) {
        this(context, null);
    }

    public ScrollSlidingTextTabStrip(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        selectorDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null);
        float rad = AndroidUtilities.dpf2(10);
        selectorDrawable.setCornerRadii(new float[]{rad, rad, rad, rad, rad, rad, rad, rad});
//        selectorDrawable.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});

        setFillViewport(true);
        setWillNotDraw(false);

        setHorizontalScrollBarEnabled(false);
        tabsContainer = new LinearLayout(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                ScrollSlidingTextTabStrip.this.invalidate();
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);

                if (setInitialTab && idToPosition.indexOfKey(selectedTabId) >= 0 && tabsContainer.getChildAt(idToPosition.get(selectedTabId)) != null) {
                    scrollToChild(idToPosition.get(selectedTabId), false);
                    setInitialTab = false;
                }
            }

            private float sx, sy;
            private View find(float x, float y) {
                for (int i = 0; i < getChildCount(); i++) {
                    final View child = getChildAt(i);
                    if (
                        x >= child.getX() && x <= child.getX() + child.getWidth() &&
                        y >= child.getY() && y <= child.getY() + child.getHeight()
                    ) {
                        return child;
                    }
                }
                return null;
            }

            private int findPosition(float x, int w) {
                if (x < 0)
                    return 0;
                for (int i = 0; i < getChildCount(); i++) {
                    final View child = getChildAt(i);
                    final float cx = child.getLeft() + child.getWidth() / 2.0f;
                    if (
                        x >= cx - Math.min(w, child.getWidth()) / 2.0f &&
                        x <= cx + Math.min(w, child.getWidth()) / 2.0f
                    ) {
                        return i;
                    }
                }
                return getChildCount() - 1;
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (delegate == null || !reordering) return super.dispatchTouchEvent(ev);
                if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                    dragging = find(ev.getX(), ev.getY());
                    if (dragging != null && getParent() != null) {
                        final int page = positionToId.get(indexOfChild(dragging));
                        if (!delegate.canReorder(page)) {
                            dragging = null;
                            return super.dispatchTouchEvent(ev);
                        }
                        sx = ev.getX();
                        sy = ev.getY();
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
                    if (dragging != null) {
                        dragging.setTranslationX(ev.getX() - sx);
                        final int position = indexOfChild(dragging);
                        if (currentPosition == position) {
                            ScrollSlidingTextTabStrip.this.invalidate();
                        }
                        if (ev.getX() < dp(16)) {
                            ScrollSlidingTextTabStrip.this.scrollBy(-dp(1), 0);
                        } else if (ev.getX() >= getWidth() - dp(16)) {
                            ScrollSlidingTextTabStrip.this.scrollBy(dp(1), 0);
                        }
                        final int positionTo = findPosition(dragging.getX() + dragging.getWidth() / 2.0f, dragging.getWidth());
                        if (positionTo != position && delegate.canReorder(positionToId.get(positionTo))) {
                            final View replacing = getChildAt(positionTo);
                            if (replacing.getLeft() > dragging.getLeft()) {
                                sx += (replacing.getLeft() + replacing.getWidth() - dragging.getWidth() - dragging.getLeft());
                            } else {
                                sx += (replacing.getLeft() - dragging.getLeft());
                            }
                            dragging.setTranslationX(ev.getX() - sx);

                            final ViewGroup.LayoutParams draggingParams = dragging.getLayoutParams();
                            final ViewGroup.LayoutParams replacingParams = replacing.getLayoutParams();

                            final int fromX = dragging.getLeft();
                            final int toX = replacing.getLeft();
                            final int from = position;
                            final int to = positionTo;

                            if (currentPosition == from) {
                                currentPosition = to;
                            } else if (currentPosition == to) {
                                currentPosition = from;
                            }
                            if (previousPosition == from) {
                                previousPosition = to;
                            } else if (previousPosition == to) {
                                previousPosition = from;
                            }
                            prevLayoutWidth = -1;

                            final int fromId = positionToId.get(from);
                            final int toId = positionToId.get(to);

                            positionToId.put(from, toId);
                            idToPosition.put(toId, from);
                            positionToId.put(to, fromId);
                            idToPosition.put(fromId, to);

                            tabsContainer.removeViewAt(Math.max(from, to));
                            tabsContainer.removeViewAt(Math.min(from, to));

                            tabsContainer.addView(from < to ? replacing : dragging, Math.min(from, to), from < to ? replacingParams : draggingParams);
                            tabsContainer.addView(from < to ? dragging : replacing, Math.max(from, to), from < to ? draggingParams : replacingParams);

                            replacing.setTranslationX(toX - fromX);
                            replacing.animate().translationX(0)
                                .setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                                .setUpdateListener(a -> {
                                    invalidate();
                                    ScrollSlidingTextTabStrip.this.invalidate();
                                })
                                .start();
                        }
                    }
                } else if (ev.getAction() == MotionEvent.ACTION_UP) {
                    if (dragging != null) {
                        dragging.animate()
                            .translationX(0)
                            .translationY(0)
                            .setDuration(320)
                            .setUpdateListener(A -> {
                                invalidate();
                                ScrollSlidingTextTabStrip.this.invalidate();
                            })
                            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                            .start();
                    }
                    dragging = null;
                } else if (ev.getAction() == MotionEvent.ACTION_CANCEL) {

                    if (dragging != null) {
                        dragging.animate()
                            .translationX(0)
                            .translationY(0)
                            .setDuration(320)
                            .setUpdateListener(A -> {
                                invalidate();
                                ScrollSlidingTextTabStrip.this.invalidate();
                            })
                            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                            .start();
                    }
                    dragging = null;
                }
                return dragging != null || super.dispatchTouchEvent(ev);
            }
        };
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setPadding(dp(7), 0, dp(7), 0);
        tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(tabsContainer);

        updateColors();
    }

    public void setDelegate(ScrollSlidingTabStripDelegate scrollSlidingTabStripDelegate) {
        delegate = scrollSlidingTabStripDelegate;
    }

    public boolean isAnimatingIndicator() {
        return animatingIndicator;
    }

    private void setAnimationProgressInernal(TextView newTab, TextView prevTab, float value) {
        if (newTab == null || prevTab == null) {
            return;
        }
        int newColor = isChipFoldersEnabled() ? getChipTextColor(true) : processColor(Theme.getColor(activeTextColorKey, resourcesProvider));
        int prevColor = isChipFoldersEnabled() ? getChipTextColor(false) : processColor(Theme.getColor(unactiveTextColorKey, resourcesProvider));

        int r1 = Color.red(newColor);
        int g1 = Color.green(newColor);
        int b1 = Color.blue(newColor);
        int a1 = Color.alpha(newColor);
        int r2 = Color.red(prevColor);
        int g2 = Color.green(prevColor);
        int b2 = Color.blue(prevColor);
        int a2 = Color.alpha(prevColor);

        prevTab.setTextColor(Color.argb((int) (a1 + (a2 - a1) * value), (int) (r1 + (r2 - r1) * value), (int) (g1 + (g2 - g1) * value), (int) (b1 + (b2 - b1) * value)));
        newTab.setTextColor(Color.argb((int) (a2 + (a1 - a2) * value), (int) (r2 + (r1 - r2) * value), (int) (g2 + (g1 - g2) * value), (int) (b2 + (b1 - b2) * value)));

        indicatorX = animateIndicatorStartX + (animateIndicatorToX - animateIndicatorStartX) * value;
        indicatorWidth = animateIndicatorStartWidth + (animateIndicatorToWidth - animateIndicatorStartWidth) * value;
        invalidate();
    }

    @Keep
    public void setAnimationIdicatorProgress(float value) {
        animationIdicatorProgress = value;

        TextView newTab = (TextView) tabsContainer.getChildAt(currentPosition);
        TextView prevTab = (TextView) tabsContainer.getChildAt(previousPosition);
        if (prevTab == null || newTab == null) {
            return;
        }
        setAnimationProgressInernal(newTab, prevTab, value);

        if (value >= 1f) {
            prevTab.setTag(unactiveTextColorKey);
            newTab.setTag(activeTextColorKey);
        }

        if (delegate != null) {
            delegate.onPageScrolled(value);
        }
    }

    public void setUseSameWidth(boolean value) {
        useSameWidth = value;
    }
    public void setUseMinimalWidth(boolean value) {
        useMinimalWidth = value;
        tabsContainer.setLayoutParams(new LayoutParams(useMinimalWidth ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    public Drawable getSelectorDrawable() {
        return selectorDrawable;
    }

    public ViewGroup getTabsContainer() {
        return tabsContainer;
    }

    public ArrayList<Integer> getTabIds() {
        final ArrayList<Integer> ids = new ArrayList<>();
        for (int i = 0; i < tabsContainer.getChildCount(); ++i) {
            ids.add(positionToId.get(i));
        }
        return ids;
    }

    @Keep
    public float getAnimationIdicatorProgress() {
        return animationIdicatorProgress;
    }

    public int getNextPageId(boolean forward) {
        return positionToId.get(currentPosition + (forward ? 1 : -1), -1);
    }

    public SparseArray<View> removeTabs() {
        SparseArray<View> views = new SparseArray<>();
        for (int i = 0; i < tabsContainer.getChildCount(); i++) {
            View child = tabsContainer.getChildAt(i);
            views.put(positionToId.get(i), child);
        }
        positionToId.clear();
        idToPosition.clear();
        prevPositionToWidth.clear();
        positionToWidth.clear();
        tabsContainer.removeAllViews();
        allTextWidth = 0;
        tabCount = 0;

        return views;
    }

    BlurredBackgroundDrawable backgroundDrawable;
    public void setBlurredBackground(BlurredBackgroundDrawable background) {
        backgroundDrawable = background;
        if (backgroundDrawable != null) {
            backgroundDrawable.setCallback(this);
        }
    }

    public final Path clipPath = new Path();
    private final RectF prevRect = new RectF();
    private final RectF rect = new RectF();
    private final AnimatedFloat rectT = new AnimatedFloat(this, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat left = new AnimatedFloat(this, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat open = new AnimatedFloat(this, 420, CubicBezierInterpolator.EASE_OUT_QUINT);

    private void checkBoundsAndClipping() {
        final float rectT = this.rectT.set(1f);
        rect.set(getPaddingLeft(), 0, getMeasuredWidth() - getPaddingRight(), getMeasuredHeight());
        rect.inset(dp(7), dp(7));
        if (rectT >= 1) {
            prevRect.set(rect);
        } else {
            AndroidUtilities.lerp(prevRect, rect, rectT, rect);
        }

        final float r = rect.height() / 2;
        clipPath.rewind();
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW);

        if (backgroundDrawable != null) {
            backgroundDrawable.setAlpha(0xFF);
            backgroundDrawable.setBounds((int) rect.left - dp(7), 0, (int) rect.right + dp(7), getMeasuredHeight());
            backgroundDrawable.setRadius(r);
        }
    }

    public int getTabsCount() {
        return tabCount;
    }

    public boolean hasTab(int id) {
        return idToPosition.get(id, -1) != -1;
    }

    private class ChipTabTextView extends AnimatedEmojiSpan.TextViewEmojis implements Md3ChipTabHelper.ChipPressHost {
        protected final Md3ChipTabHelper.PressState md3PressState = new Md3ChipTabHelper.PressState();

        ChipTabTextView(Context context) {
            super(context);
        }

        @Override
        public void setPressed(boolean pressed) {
            super.setPressed(pressed);
            md3PressState.onPressedChanged(this, tabsContainer, ScrollSlidingTextTabStrip.this);
        }

        @Override
        public float getMd3PressProgress() {
            return md3PressState.getProgress();
        }
    }

    public void addTextTab(final int id, CharSequence text) {
        addTextTab(id, text, null, null);
    }
    public void addTextTab(final int id, CharSequence text, SparseArray<View> viewsCache) {
        addTextTab(id, text, viewsCache, null);
    }
    public void addTextTab(final int id, CharSequence text, SparseArray<View> viewsCache, OnLongClickListener listener) {
        int position = tabCount++;
        if (position == 0 && selectedTabId == -1) {
            selectedTabId = id;
        }
        positionToId.put(position, id);
        idToPosition.put(id, position);
        if (selectedTabId != -1 && selectedTabId == id) {
            currentPosition = position;
            prevLayoutWidth = 0;
        }
        TextView tab = null;
        if (viewsCache != null) {
            tab = (TextView) viewsCache.get(id);
            viewsCache.delete(id);
        }
        if (tab == null) {
            tab = new ChipTabTextView(getContext()) {
                @Override
                public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info);
                    info.setSelected(selectedTabId == id);
                }

                private Shaker shaker;
                private AnimatedFloat reorderingAlpha = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
                private boolean isReordering() {
                    return delegate != null && reordering && delegate.canReorder(id);
                }
                @Override
                protected void onDraw(Canvas canvas) {
                    final float pressP = md3PressState.step(this, tabsContainer, ScrollSlidingTextTabStrip.this);
                    final float pressSX = Md3ChipTabHelper.getPressScaleX(pressP);
                    final float pressSY = Md3ChipTabHelper.getPressScaleY(pressP);
                    final boolean pressScaled = Math.abs(pressSX - 1f) > 0.001f || Math.abs(pressSY - 1f) > 0.001f;
                    if (pressScaled) {
                        canvas.save();
                        canvas.scale(pressSX, pressSY, getWidth() * 0.5f, getHeight() * 0.5f);
                    }
                    final float reordering = reorderingAlpha.set(ScrollSlidingTextTabStrip.this.reordering);
                    if (delegate != null && delegate.canReorder(id)) {
                        if (reordering > 0) {
                            if (shaker == null) {
                                shaker = new Shaker(this);
                            }
                            canvas.save();
                            shaker.concat(canvas, reordering, getWidth() / 2.0f, getHeight() / 2.0f);
                        }
                        super.onDraw(canvas);
                        if (reordering > 0) {
                            canvas.restore();
                        }
                    } else {
                        if (reordering > 0) {
                            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), (int) (0xFF * lerp(1.0f, 0.5f, reordering)));
                        }
                        super.onDraw(canvas);
                        if (reordering > 0) {
                            canvas.restore();
                        }
                    }
                    if (pressScaled) {
                        canvas.restore();
                    }
                }
            };
            tab.setGravity(Gravity.CENTER);
            tab.setTextAlignment(TEXT_ALIGNMENT_CENTER);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            tab.setSingleLine();
            tab.setMaxLines(1);
//            tab.setMaxLines(2);
            tab.setTypeface(AndroidUtilities.bold());
            tab.setPadding(dp(16), 0, dp(16), 0);
            tab.setOnClickListener(v -> {
                scrollTo(id, tabsContainer.indexOfChild(v), v);
            });
            tab.setOnLongClickListener(v -> {
                if (reordering) return false;
                return delegate != null && delegate.showOptions(id, v);
            });
            NotificationCenter.listenEmojiLoading(tab);
        }
        text = Emoji.replaceEmoji(text, tab.getPaint().getFontMetricsInt(), false);
        tab.setText(text);
        int tabWidth = (int) Math.ceil(HintView2.measureCorrectly(text, tab.getPaint())) + dp(32);// + tab.getPaddingLeft() + tab.getPaddingRight();
        tabsContainer.addView(tab, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT));
        allTextWidth += tabWidth;
        positionToWidth.put(position, tabWidth);

        updateColors();
        if (listener != null) {
            tab.setOnLongClickListener(listener);
        }
    }

    public void scrollTo(int pageId, int position1, View v) {
        if (position1 < 0 || v == null && animatingIndicator) {
            return;
        }
        if (position1 == currentPosition && delegate != null) {
            delegate.onSamePageSelected();
            return;
        }
        boolean scrollingForward = currentPosition < position1;
        scrollingToChild = -1;
        previousPosition = currentPosition;
        currentPosition = position1;
        selectedTabId = pageId;
        swipingToPosition = -1;

        if (indicatorAnimator != null) {
            indicatorAnimator.cancel();
            indicatorAnimator = null;
        }
        animatingIndicator = true;
        animateIndicatorStartX = indicatorX;
        animateIndicatorStartWidth = indicatorWidth;

        if (v != null) {
            TextView nextChild = (TextView) v;
            animateIndicatorToWidth = getChildWidth(nextChild);
            animateIndicatorToX = nextChild.getLeft() + (nextChild.getMeasuredWidth() - animateIndicatorToWidth) / 2f;
        }
        final boolean md3 = isChipFoldersEnabled();
        if (md3 && Md3ChipTabHelper.getAnimMode() == Md3ChipTabHelper.ANIM_NONE) {
            animatingIndicator = false;
            chipAnimRawProgress = 1f;
            setEnabled(true);
            setAnimationIdicatorProgress(1.0f);
            refreshChipTabStyles();
            if (delegate != null) {
                delegate.onPageSelected(pageId, scrollingForward);
                delegate.onPageScrolled(1.0f);
            }
            scrollToChild(position1, false);
            return;
        }
        setEnabled(false);

        chipAnimRawProgress = 0f;
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(md3 ? Md3ChipTabHelper.getIndicatorDurationMs(animationDuration) : Math.max(1L, animationDuration));
        animator.setInterpolator(md3 ? new android.view.animation.LinearInterpolator() : interpolator);
        animator.addUpdateListener(anim -> {
            float raw = (float) anim.getAnimatedValue();
            if (md3) {
                chipAnimRawProgress = raw;
                setAnimationIdicatorProgress(Md3ChipTabHelper.getStandardProgress(raw));
            } else {
                setAnimationIdicatorProgress(raw);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (indicatorAnimator == animation) {
                    indicatorAnimator = null;
                }
                animatingIndicator = false;
                setEnabled(true);
                refreshChipTabStyles();
                if (!cancelled && delegate != null) {
                    delegate.onPageScrolled(1.0f);
                }
            }
        });
        indicatorAnimator = animator;
        animator.start();

        if (delegate != null) {
            delegate.onPageSelected(pageId, scrollingForward);
        }
        scrollToChild(position1, true);
    }

    public void scrollTo(int pageId) {
        scrollTo(pageId, idToPosition.get(pageId), null);
    }

    private boolean isChipFoldersEnabled() {
        return Md3ChipTabHelper.isEnabled();
    }

    private int getChipSpacingHalfPx() {
        return Md3ChipTabHelper.getSpacingHalfPx();
    }

    private int getChipVerticalInsetPx() {
        return getPaddingTop() >= dp(5) ? dp(1) : dp(5);
    }

    private float[] getChipCornerRadii(boolean active, int index, int count) {
        return Md3ChipTabHelper.getCornerRadii(active, index, count);
    }

    private int getChipTextColor(boolean active) {
        int activeTextColor = processColor(Theme.getColor(activeTextColorKey, resourcesProvider));
        int unactiveTextColor = processColor(Theme.getColor(unactiveTextColorKey, resourcesProvider));
        return Md3ChipTabHelper.getTextColor(active, activeTextColor, unactiveTextColor);
    }

    private void refreshChipTabStyles() {
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = tabsContainer.getChildAt(a);
            if (child instanceof TextView) {
                applyChipTabStyle((TextView) child, a, count, currentPosition == a);
            }
        }
        invalidate();
    }

    private void applyChipTabStyle(TextView tab, int index, int count, boolean active) {
        if (tab == null) {
            return;
        }
        int activeTextColor = processColor(Theme.getColor(activeTextColorKey, resourcesProvider));
        int unactiveTextColor = processColor(Theme.getColor(unactiveTextColorKey, resourcesProvider));
        if (!isChipFoldersEnabled()) {
            tab.setTextColor(active ? activeTextColor : unactiveTextColor);
            tab.setBackground(
                new InsetDrawable(
                    Theme.createSelectorDrawable(Theme.multAlpha(activeTextColor, .15f), Theme.RIPPLE_MASK_ROUNDRECT_6DP, dp(14)),
                    dp(4), dp(4), dp(4), dp(4)
                )
            );
            return;
        }
        tab.setTextColor(Md3ChipTabHelper.getTextColor(active, activeTextColor, unactiveTextColor));

        // Inactive chip containers are drawn behind selectorDrawable in drawChild() so animated transitions never clip or pop over the active pill.
        float[] radii = Md3ChipTabHelper.getCornerRadii(active, index, count);
        int gap = getChipSpacingHalfPx();
        int vInset = getChipVerticalInsetPx();
        tab.setBackground(Md3ChipTabHelper.createTransparentChipRipple(activeTextColor, radii, gap, vInset));
    }

    public void finishAddingTabs() {
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            TextView tab = (TextView) tabsContainer.getChildAt(a);
            tab.setTag(currentPosition == a ? activeTextColorKey : unactiveTextColorKey);
            applyChipTabStyle(tab, a, count, currentPosition == a);
            if (useMinimalWidth) {
                if (
                    prevPositionToWidth.size() != positionToWidth.size() ||
                    prevPositionToWidth.get(a) != positionToWidth.get(a)
                ) {
                    tab.requestLayout();
                }
            } else if (a == 0) {
                int prevWidth = tab.getLayoutParams().width;
                tab.getLayoutParams().width = count == 1 ? LayoutHelper.WRAP_CONTENT : 0;
                if (prevWidth != tab.getLayoutParams().width) {
                    tab.requestLayout();
                }
            }
        }
        prevPositionToWidth.clear();
        for (int i = 0; i < positionToWidth.size(); ++i)
            prevPositionToWidth.put(positionToWidth.keyAt(i), positionToWidth.valueAt(i));
    }

    public void setColors(int line, int active, int unactive, int selector) {
        tabLineColorKey = line;
        activeTextColorKey = active;
        unactiveTextColorKey = unactive;
        selectorColorKey = selector;
        updateColors();
    }

    @Override
    public void updateColors() {
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            TextView tab = (TextView) tabsContainer.getChildAt(a);
            applyChipTabStyle(tab, a, count, currentPosition == a);
        }
        int activeTextColor = processColor(Theme.getColor(activeTextColorKey, resourcesProvider));
        if (isChipFoldersEnabled()) {
            Md3ChipTabHelper.getCornerRadii(true, currentPosition, count, chipRadiiOut);
            Md3ChipTabHelper.configureSelectorDrawable(selectorDrawable, activeTextColor, chipRadiiOut);
        } else {
            float rad = AndroidUtilities.dpf2(10);
            selectorDrawable.setCornerRadii(new float[]{rad, rad, rad, rad, rad, rad, rad, rad});
            selectorDrawable.setStroke(0, 0);
            selectorDrawable.setColor(Theme.multAlpha(activeTextColor, .15f));
        }
        if (backgroundDrawable != null) {
            // glass background is drawn manually (not the view background), so the theme dispatcher does not reach it
            backgroundDrawable.updateColors();
        }
        invalidate();
    }
    @Override
    public void addColorKeys(HashSet<Integer> colors) {
        colors.add(activeTextColorKey);
    }

    public int getCurrentTabId() {
        return selectedTabId;
    }

    private boolean setInitialTab;
    public void setInitialTabId(int id) {
        setInitialTab = true;
        selectedTabId = id;
        int pos = idToPosition.get(id);
        TextView child = (TextView) tabsContainer.getChildAt(pos);
        if (child != null) {
            currentPosition = pos;
            prevLayoutWidth = 0;
            finishAddingTabs();
            requestLayout();
        }
    }

    public void resetTab() {
        selectedTabId = -1;
    }

    public int getFirstTabId() {
        return positionToId.get(0, 0);
    }


    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.save();
        if (backgroundDrawable != null && !isChipFoldersEnabled()) {
            if (rectT.set(1f) < 1) {
                checkBoundsAndClipping();
            }
            canvas.translate(getScrollX(), 0);
            backgroundDrawable.setShadowAlpha(this.open.set(isOpen));
            backgroundDrawable.draw(canvas);
            canvas.clipPath(clipPath);
            canvas.translate(-getScrollX(), 0);
            canvas.translate(left.set(0), 0);
        }
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    private boolean isOpen = true;
    public void setOpen(boolean open) {
        if (open == isOpen) return;

        isOpen = open;

        setPadding(dp(isOpen ? 0 : 6), getPaddingTop(), dp(isOpen ? 0 : 6), getPaddingBottom());
        invalidate();
        if (!fitsItems) {
            prevRect.set(rect);
            rectT.force(0);
            left.force(dp(isOpen ? 6 : -6));
        }
        checkBoundsAndClipping();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == backgroundDrawable || super.verifyDrawable(who);
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == tabsContainer) {
            final int height = getMeasuredHeight();
            float l = indicatorX + indicatorXAnimationDx;
            float r = l + indicatorWidth + indicatorWidthAnimationDx;

            final View current = tabsContainer.getChildAt(currentPosition);
            if (reordering && current != null) {
                l += current.getTranslationX();
                r += current.getTranslationX();
            }
            int wasAlpha = selectorDrawable.getAlpha();
            int chipInsetH = isChipFoldersEnabled() ? getChipSpacingHalfPx() : dp(4);
            int chipInsetV = isChipFoldersEnabled() ? getChipVerticalInsetPx() : dp(4);
            float pillLeft = getPaddingLeft() + l + chipInsetH;
            float pillTop = getPaddingTop() + chipInsetV;
            float pillRight = getPaddingLeft() + r - chipInsetH;
            float pillBottom = height - getPaddingBottom() - chipInsetV;
            if (isChipFoldersEnabled()) {
                int count = tabsContainer.getChildCount();
                int fromPos = animatingIndicator ? previousPosition : currentPosition;
                int toPos = animatingIndicator ? currentPosition : (swipingToPosition >= 0 ? swipingToPosition : currentPosition);
                float t = animatingIndicator ? Math.max(0f, Math.min(1f, animationIdicatorProgress)) : (swipingToPosition >= 0 ? swipingProgress : 1f);
                float transitionSX = 1f;
                float transitionSY = 1f;
                if (animatingIndicator) {
                    float moveT = Md3ChipTabHelper.getPillTravelProgress(chipAnimRawProgress);
                    transitionSX = Md3ChipTabHelper.getPillTransitionScaleX(chipAnimRawProgress);
                    transitionSY = Md3ChipTabHelper.getPillTransitionScaleY(chipAnimRawProgress);
                    float startW = animateIndicatorStartWidth + indicatorWidthAnimationDx;
                    float endW = animateIndicatorToWidth + indicatorWidthAnimationDx;
                    float curW = startW + (endW - startW) * t;
                    float startC = animateIndicatorStartX + indicatorXAnimationDx + startW * 0.5f;
                    float endC = animateIndicatorToX + indicatorXAnimationDx + endW * 0.5f;
                    float curC = startC + (endC - startC) * moveT;
                    l = curC - curW * 0.5f;
                    r = curC + curW * 0.5f;
                    if (reordering && current != null) {
                        l += current.getTranslationX();
                        r += current.getTranslationX();
                    }
                    pillLeft = getPaddingLeft() + l + chipInsetH;
                    pillRight = getPaddingLeft() + r - chipInsetH;
                }
                int activeTextColor = processColor(Theme.getColor(activeTextColorKey, resourcesProvider));
                int unactiveTextColor = processColor(Theme.getColor(unactiveTextColorKey, resourcesProvider));
                float containerAlpha = tabsContainer.getAlpha();
                int baseX = tabsContainer.getLeft();
                for (int i = 0; i < count; i++) {
                    View tab = tabsContainer.getChildAt(i);
                    if (tab == null || tab.getVisibility() != VISIBLE) {
                        continue;
                    }
                    float slotLeft = baseX + tab.getLeft() + tab.getTranslationX() + chipInsetH;
                    float slotRight = baseX + tab.getRight() + tab.getTranslationX() - chipInsetH;
                    float overlapAlpha = Md3ChipTabHelper.computeOverlapAlpha(slotLeft, slotRight, pillLeft, pillRight);
                    float childAlpha = overlapAlpha * containerAlpha * tab.getAlpha();
                    if (childAlpha <= 0.01f) {
                        continue;
                    }
                    float sx = tab.getScaleX() * Md3ChipTabHelper.getViewPressScaleX(tab);
                    float sy = tab.getScaleY() * Md3ChipTabHelper.getViewPressScaleY(tab);
                    boolean scaled = Math.abs(sx - 1f) > 0.001f || Math.abs(sy - 1f) > 0.001f;
                    if (scaled) {
                        canvas.save();
                        canvas.scale(sx, sy, (slotLeft + slotRight) * 0.5f, (pillTop + pillBottom) * 0.5f);
                    }
                    Md3ChipTabHelper.drawInactiveChip(
                            canvas, inactiveChipDrawable,
                            slotLeft, pillTop, slotRight, pillBottom,
                            i, count, unactiveTextColor,
                            childAlpha,
                            chipRadiiScratch
                    );
                    if (scaled) {
                        canvas.restore();
                    }
                }
                View activeTab = tabsContainer.getChildAt(toPos);
                float pillSX = transitionSX * Md3ChipTabHelper.getViewPressScaleX(activeTab);
                float pillSY = transitionSY * Md3ChipTabHelper.getViewPressScaleY(activeTab);
                Md3ChipTabHelper.drawActivePill(
                        canvas, selectorDrawable,
                        pillLeft, pillTop, pillRight, pillBottom,
                        pillSX, pillSY,
                        (pillLeft + pillRight) * 0.5f, (pillTop + pillBottom) * 0.5f,
                        activeTextColor, containerAlpha,
                        fromPos, toPos, count, t,
                        chipRadiiFrom, chipRadiiTo, chipRadiiOut
                );
                selectorDrawable.setAlpha(wasAlpha);
                return super.drawChild(canvas, child, drawingTime);
            } else {
                selectorDrawable.setAlpha((int) (wasAlpha * tabsContainer.getAlpha()));
                selectorDrawable.setBounds(
                    (int) pillLeft,
                    (int) pillTop,
                    (int) pillRight,
                    (int) pillBottom
                );
                final boolean result = super.drawChild(canvas, child, drawingTime);
                selectorDrawable.draw(canvas);
                selectorDrawable.setAlpha(wasAlpha);
                return result;
            }
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec) - dp(22);
        final int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            final View child = tabsContainer.getChildAt(a);
            final LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) child.getLayoutParams();
            float prevWeight = layoutParams.weight;
            int prevWidth = layoutParams.width;
            if (useMinimalWidth) {
                layoutParams.weight = 0;
                layoutParams.width = positionToWidth.get(a);
            } else if (allTextWidth > width) {
                layoutParams.weight = 0;
                layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            } else if (useSameWidth) {
                layoutParams.weight = 1.0f / count;
                layoutParams.width = 0;
            } else {
                if (a == 0 && count == 1) {
                    layoutParams.weight = 0.0f;
                    layoutParams.width = LayoutHelper.WRAP_CONTENT;
                } else {
                    layoutParams.weight = AndroidUtilities.lerp(1.0f / count, 1.0f / allTextWidth * positionToWidth.get(a), .5f);
                    layoutParams.width = LayoutHelper.WRAP_CONTENT;
                }
            }
            if (Math.abs(prevWeight - layoutParams.weight) > 0.001f || prevWidth != layoutParams.width) {
                child.setLayoutParams(layoutParams);
                child.requestLayout();
            }
        }
        float weightSum = tabsContainer.getWeightSum();
        if (count == 1 || allTextWidth > width) {
            tabsContainer.setWeightSum(0.0f);
        } else {
            tabsContainer.setWeightSum(1.0f);
        }
        if (Math.abs(weightSum - tabsContainer.getWeightSum()) > 0.1f) {
            tabsContainer.requestLayout();
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        fitsItems = getMeasuredWidth() < MeasureSpec.getSize(widthMeasureSpec);

        checkBoundsAndClipping();
    }

    private void scrollToChild(int position, boolean smooth) {
        if (tabCount == 0 || scrollingToChild == position) {
            return;
        }
        scrollingToChild = position;
        final TextView child = (TextView) tabsContainer.getChildAt(position);
        if (child == null) {
            return;
        }
        final int currentScrollX = getScrollX();
        final int left = child.getLeft();
        final int width = child.getMeasuredWidth();
        if (left - dp(50) < currentScrollX) {
            if (smooth) {
                smoothScrollTo(left - dp(50), 0);
            } else {
                scrollTo(left - dp(50), 0);
            }
        } else if (left + width + dp(21) > currentScrollX + getWidth()) {
            if (smooth) {
                smoothScrollTo(left + width, 0);
            } else {
                scrollTo(left + width, 0);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        if (prevLayoutWidth != r - l) {
            prevLayoutWidth = r - l;
            scrollingToChild = -1;
            if (animatingIndicator) {
                if (indicatorAnimator != null) {
                    indicatorAnimator.cancel();
                    indicatorAnimator = null;
                }
                animatingIndicator = false;
                setEnabled(true);
                refreshChipTabStyles();
                if (delegate != null) {
                    delegate.onPageScrolled(1.0f);
                }
            }
            final TextView child = (TextView) tabsContainer.getChildAt(currentPosition);
            if (child != null) {
                indicatorWidth = getChildWidth(child);
                indicatorX = child.getLeft() + (child.getMeasuredWidth() - indicatorWidth) / 2f;

                if (animateFromIndicaxtorX > 0 && animateFromIndicatorWidth > 0) {
                    if (animateFromIndicaxtorX != indicatorX || animateFromIndicatorWidth != indicatorWidth) {
                        final float dX = animateFromIndicaxtorX - indicatorX;
                        final float dW = animateFromIndicatorWidth - indicatorWidth;
                        final ValueAnimator valueAnimator = ValueAnimator.ofFloat(1f, 0);
                        valueAnimator.addUpdateListener(valueAnimator1 -> {
                            float v = (float) valueAnimator1.getAnimatedValue();
                            indicatorXAnimationDx = dX * v;
                            indicatorWidthAnimationDx = dW * v;
                            tabsContainer.invalidate();
                            invalidate();
                        });
                        valueAnimator.setDuration(200);
                        valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        valueAnimator.start();
                    }
                    animateFromIndicaxtorX = 0;
                    animateFromIndicatorWidth = 0;
                }
            }
        }

        checkBoundsAndClipping();
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = tabsContainer.getChildAt(a);
            child.setEnabled(enabled);
        }
    }

    public void selectTabWithId(int id, float progress) {
        selectTabWithId(id, progress, false);
    }

    public void selectTabWithId(int id, float progress, boolean force) {
        int position = idToPosition.get(id, -1);
        if (position < 0) {
            return;
        }
        if (currentPosition == position && !force) {
            return;
        }
        if (progress < 0) {
            progress = 0;
        } else if (progress > 1.0f) {
            progress = 1.0f;
        }
        TextView child = (TextView) tabsContainer.getChildAt(currentPosition);
        TextView nextChild = (TextView) tabsContainer.getChildAt(position);
        if (child != null && nextChild != null) {
            animateIndicatorStartWidth = getChildWidth(child);
            animateIndicatorStartX = child.getLeft() + (child.getMeasuredWidth() - animateIndicatorStartWidth) / 2f;
            animateIndicatorToWidth = getChildWidth(nextChild);
            animateIndicatorToX = nextChild.getLeft() + (nextChild.getMeasuredWidth() - animateIndicatorToWidth) / 2f;
            if (progress <= 0f) {
                swipingToPosition = -1;
                swipingProgress = 0f;
                setAnimationProgressInernal(nextChild, child, 0f);
                refreshChipTabStyles();
                return;
            }
            swipingToPosition = position;
            swipingProgress = progress;
            setAnimationProgressInernal(nextChild, child, progress);
            if (progress >= 1f) {
                child.setTag(unactiveTextColorKey);
                nextChild.setTag(activeTextColorKey);
            }
            scrollToChild(tabsContainer.indexOfChild(nextChild), true);
        }
        if (progress >= 1.0f) {
            swipingToPosition = -1;
            swipingProgress = 0f;
            currentPosition = position;
            selectedTabId = id;
            refreshChipTabStyles();
        }
    }

    private int getChildWidth(TextView child) {
        Layout layout = child.getLayout();
//        if (layout != null) {
//            return (int) Math.ceil(layout.getLineWidth(0)) + dp(2);
//        } else {
            return child.getMeasuredWidth();
//        }
    }

    public void onPageScrolled(int position, int first) {
        if (currentPosition == position) {
            return;
        }
        currentPosition = position;
        if (position >= tabsContainer.getChildCount()) {
            return;
        }
        for (int a = 0; a < tabsContainer.getChildCount(); a++) {
            tabsContainer.getChildAt(a).setSelected(a == position);
        }
        refreshChipTabStyles();
        if (first == position && position > 1) {
            scrollToChild(position - 1, true);
        } else {
            scrollToChild(position, true);
        }
        invalidate();
    }

    public void recordIndicatorParams() {
        animateFromIndicaxtorX = indicatorX;
        animateFromIndicatorWidth = indicatorWidth;
    }

    private boolean reordering;
    public boolean isReordering() {
        return reordering;
    }
    public void setReordering(boolean reordering) {
        if (this.reordering == reordering) return;
        this.reordering = reordering;
        AndroidUtilities.forEachViews(tabsContainer, View::invalidate);
    }
}
