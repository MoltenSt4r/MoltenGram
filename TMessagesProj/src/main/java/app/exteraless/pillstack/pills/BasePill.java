package app.exteraless.pillstack.pills;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingDrawable;

/** Общая база для пилюль: автообновление, шиммер загрузки, анимация смены размера. */
public abstract class BasePill extends FrameLayout {

    private static final SparseArray<Long> globalLastUpdateTimes = new SparseArray<>();

    protected Theme.ResourcesProvider resourcesProvider;
    protected boolean loading;
    protected LoadingDrawable loadingDrawable;
    protected View loadingTargetView;

    private final RectF rectF = new RectF();
    private boolean stackVisible = true;

    private final Runnable autoRefreshRunnable = () -> {
        onUpdateData(false);
        scheduleNextUpdate();
    };

    public BasePill(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setLayoutParams(new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL));
        setClipChildren(false);
        setClipToPadding(false);
    }

    public abstract int getPillId();

    /** 0 — не обновлять по таймеру. */
    public abstract long getRefreshInterval();

    public abstract void onUpdateData(boolean force);

    public abstract void onPillClicked();

    public abstract boolean onPillLongClicked();

    public abstract void updateColors();

    public void onPillSelected() {}

    public void onPillUnselected() {}

    public int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    public int getThemedColor(int key, float alpha) {
        return Theme.multAlpha(getThemedColor(key), alpha);
    }

    public boolean isRefreshDue() {
        long interval = getRefreshInterval();
        if (interval <= 0) {
            return true;
        }
        long last = globalLastUpdateTimes.get(getPillId(), 0L);
        return last == 0 || SystemClock.elapsedRealtime() - last >= interval;
    }

    public void markDataUpdated() {
        globalLastUpdateTimes.put(getPillId(), SystemClock.elapsedRealtime());
        scheduleNextUpdate();
    }

    private void scheduleNextUpdate() {
        removeCallbacks(autoRefreshRunnable);
        if (!stackVisible) {
            return;
        }
        long interval = getRefreshInterval();
        if (interval > 0) {
            postDelayed(autoRefreshRunnable, interval);
        }
    }

    public void onStackVisibilityChanged(boolean visible) {
        if (stackVisible == visible) {
            return;
        }
        stackVisible = visible;
        if (!visible) {
            removeCallbacks(autoRefreshRunnable);
        } else if (getRefreshInterval() > 0) {
            if (isRefreshDue()) {
                onUpdateData(false);
            }
            scheduleNextUpdate();
        }
    }

    public void animateSizeChange() {
        if (isLaidOut() && getVisibility() == VISIBLE && getParent() != null && getParent().getParent() instanceof ViewGroup) {
            TransitionManager.beginDelayedTransition((ViewGroup) getParent().getParent(),
                    new TransitionSet()
                            .addTransition(new ChangeBounds())
                            .setDuration(300)
                            .setInterpolator((TimeInterpolator) CubicBezierInterpolator.EASE_OUT_QUINT));
        }
    }

    public void setLoadingTargetView(View view) {
        this.loadingTargetView = view;
    }

    public void startLoading() {
        loading = true;
        if (loadingDrawable == null) {
            loadingDrawable = new LoadingDrawable(resourcesProvider);
            loadingDrawable.setCallback(this);
            loadingDrawable.setGradientScale(2f);
            loadingDrawable.setRadiiDp(14);
            updateLoadingColors();
        }
        loadingDrawable.reset();
        loadingDrawable.resetDisappear();
        loadingDrawable.setAlpha(255);
        invalidate();
    }

    public void stopLoading() {
        loading = false;
        if (loadingDrawable != null) {
            loadingDrawable.disappear();
        }
    }

    public void updateLoadingColors() {
        if (loadingDrawable != null) {
            int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
            loadingDrawable.setColors(Theme.multAlpha(color, 0.05f), Theme.multAlpha(color, 0.15f));
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        if (loadingDrawable == null) {
            return;
        }
        if (loading || loadingDrawable.isDisappearing()) {
            View target = loadingTargetView != null ? loadingTargetView : this;
            rectF.set(target.getLeft(), target.getTop(), target.getRight(), target.getBottom());
            loadingDrawable.setBounds(rectF);
            loadingDrawable.draw(canvas);
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!stackVisible) {
            return;
        }
        long interval = getRefreshInterval();
        if (interval <= 0) {
            return;
        }
        long last = globalLastUpdateTimes.get(getPillId(), 0L);
        if (last != 0) {
            long passed = SystemClock.elapsedRealtime() - last;
            if (passed < interval) {
                postDelayed(autoRefreshRunnable, interval - passed);
                return;
            }
        }
        autoRefreshRunnable.run();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(autoRefreshRunnable);
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == loadingDrawable || super.verifyDrawable(who);
    }
}
